import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES, EX_BY_ID, computeCell, prescribeSets, sessionSuggestion, loggedWorkoutPerformance, propagateCycleEditsPure, normalizeEditedHistoryEntry, styleFor, programChangeLabels } from '../modules/engine-api.js';

import { generateNextProgramForShell, getNextShellCell, markUserPrescriptionOverride,
    nextExerciseIdForShellExercise, swapNextSlotPrescriptions, snapshotNextShellPrescription } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine, nextWorkoutSuggestionForShell,
    nextWorkoutSuggestionFromPerformedShell } from '../modules/next-engine/workout-history-adapter.js';
import { captureShellVolumeSnapshot, repairShellVolume } from '../modules/next-engine/volume-repair.js';
import { convertProgramToNextCycleForShell, advanceNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { withProgressionChoices } from '../modules/session-tools.js';

const fixture = JSON.parse(fs.readFileSync('verification/m205-generation-parity-results.json')).rows[3];
const original = generateNextProgramForShell({ config: fixture.config, legacyExercises: EXERCISES,
    seed: fixture.seed, makeId: () => 'm214-executable' }).program;
const clone = value => structuredClone(value);
const keyOf = (day, slot) => `${day.id}:${slot}`;
const historyFor = (program, day, id, reps, extra = []) => ({ id: 'm214-log', programId: program.id,
    dayId: day.id, weekIndex: 1, date: 1, unit: 'lb', perf: { [id]: { weight: 100, reps,
        sets: [{ w: 100, r: reps, rir: 2 }, { w: 100, r: reps, rir: 2 }, ...extra] } } });

// Reproduce the actual mismatch: UI says 2 x 10-15, while the prior bridge evaluated 5 x 8-12
// and called a fully completed edited workout incomplete. All runtime/history surfaces agree now.
const edited = clone(original), day = edited.days[0], slot = 1, id = day.exercises[slot], key = keyOf(day, slot);
for (const [field, value] of [['sets', 2], ['reps', '10-15'], ['rir', '2-3'], ['rest', 0], ['tech', 'off']])
    edited.overrides[key] = markUserPrescriptionOverride(edited.overrides[key], field, value);
edited.progStyle[key] = 'double';
const input = JSON.stringify(edited);
const log = historyFor(edited, day, id, 10, [
    { w: 80, r: 5, sub: true, kind: 'drop' }, { w: 50, r: 15, warm: true },
    { w: 100, r: 15, done: false }
]);
const logInput = JSON.stringify(log);
// Program editor and in-workout choices use the exercise identity. That explicit
// choice must reach the executable cell, not merely its selected-state label.
for (const method of ['double', 'dynamic', 'ladder', 'linear', 'wave', 'e1rm']) {
    const chosen = withProgressionChoices(original, { [id]: method });
    for (const week of [1, original.config.weeks]) {
        const cell = getNextShellCell(chosen, day, slot, week);
        assert.equal(cell.progressionStyle, method, 'An exercise choice must own the generated prescription');
        assert.equal(cell.ownership.progressionStyle, 'user');
    }
    const replay = analyzeShellHistoryForNextEngine(chosen, [log], EXERCISES);
    assert.equal(replay.workouts[0].session.exercises[slot].progressionStyle, method,
        'The progression evaluator must execute the method selected in the UI');
}
const methodSources = JSON.stringify(original);
const slotMethod = clone(original);
slotMethod.progStyle = { [key]: 'double' };
const exerciseChoice = withProgressionChoices(slotMethod, { [id]: 'dynamic' });
assert.equal(getNextShellCell(exerciseChoice, day, slot, 1).progressionStyle, 'dynamic',
    'An exercise-wide choice explicitly overrides its saved slot method');
assert.equal(getNextShellCell(withProgressionChoices(exerciseChoice, { [id]: 'auto' }), day, slot, 1).progressionStyle, 'double',
    'Auto reveals the original slot schedule without rewriting it');
assert.deepEqual(exerciseChoice.nextWeekPrescriptions, original.nextWeekPrescriptions);
assert.equal(exerciseChoice.overrides, slotMethod.overrides);
const mirrored = { ...original, progStyle: { [id]: getNextShellCell(original, day, slot, 1).progressionStyle } };
assert.equal(styleFor(mirrored, id), 'auto', 'Old engine-written style mirrors do not become user choices');
const locked = { ...mirrored, progStyleLifter: { [id]: true } };
assert.equal(getNextShellCell(locked, day, slot, original.config.weeks).progressionStyle, locked.progStyle[id]);
assert.equal(getNextShellCell(locked, day, slot, original.config.weeks).ownership.progressionStyle, 'user');
// The method changes the actual numbers, not just metadata: DDP can advance the
// qualifying first set while classic double keeps a shared load until both earn it.
const choiceHistory = historyFor(edited, day, id, 10);
choiceHistory.perf[id].sets = [{ w: 100, r: 15, rir: 2, tr: 2 }, { w: 100, r: 10, rir: 2, tr: 2 }];
const classic = withProgressionChoices(edited, { [id]: 'double' });
const dynamicChoice = withProgressionChoices(edited, { [id]: 'dynamic' });
const classicTargets = sessionSuggestion(classic, day, slot, {}, 'lb', 1, [choiceHistory]);
const dynamicTargets = sessionSuggestion(dynamicChoice, day, slot, {}, 'lb', 1, [choiceHistory]);
assert.equal(classicTargets.weight, 100);
assert.ok(dynamicTargets.setTargets[0].weight > 100);
assert.equal(dynamicTargets.setTargets[1].weight, 100);
assert.equal(snapshotNextShellPrescription(dynamicChoice, day, slot, EX_BY_ID[id], 1).progressionStyle, 'dynamic');
const factualHistory = clone(choiceHistory);
factualHistory.perf[id].prescription = snapshotNextShellPrescription(classic, day, slot, EX_BY_ID[id], 1);
const historyBeforeChoice = JSON.stringify(factualHistory);
const factualReplay = analyzeShellHistoryForNextEngine(dynamicChoice, [factualHistory], EXERCISES);
assert.equal(factualReplay.workouts[0].session.exercises[slot].progressionStyle, 'double',
    'A later method choice cannot rewrite the method actually logged');
assert.equal(nextWorkoutSuggestionForShell(dynamicChoice, [factualHistory], EXERCISES, day, slot, 1).action, 'represcribe');
assert.equal(JSON.stringify(factualHistory), historyBeforeChoice);
const pairedChoice = clone(original);
pairedChoice.pairs = { [key]: 'db-bench' };
const alternatingChoice = withProgressionChoices(pairedChoice, { 'db-bench': 'linear' });
assert.equal(getNextShellCell(alternatingChoice, day, slot, 1).progressionStyle, getNextShellCell(original, day, slot, 1).progressionStyle);
assert.equal(getNextShellCell(alternatingChoice, day, slot, 2).progressionStyle, 'linear',
    'An alternating lift owns its choice only on weeks when it is prescribed');
assert.equal(JSON.stringify(original), methodSources, 'Choosing and evaluating methods does not rewrite the generated artifact');
const methodLabels = programChangeLabels(original, withProgressionChoices(original, { [id]: 'dynamic' }));
assert.deepEqual(methodLabels, ['Progression method'], 'A method-only save must not claim changed sets or reps');
const analysis = analyzeShellHistoryForNextEngine(edited, [log], EXERCISES);
const planned = analysis.workouts[0].session.exercises[slot];
assert.equal(planned.sets, 2);
assert.deepEqual(planned.prescription, { reps: [10, 15], rir: [2, 3], restSeconds: 0 });
assert.equal(planned.progressionStyle, 'double');
assert.equal(planned.advancedTechnique, undefined);
assert.equal(analysis.performedSetCount, 2, 'unfinished rows, warmups and extensions cannot count as work');
assert.equal(getNextShellCell(edited, day, slot, 1).ownership.progressionStyle, 'user');
const immediate = nextWorkoutSuggestionFromPerformedShell(edited, EXERCISES, day, slot, 1, log.perf, 'lb');
const next = nextWorkoutSuggestionForShell(edited, [log], EXERCISES, day, slot, 1);
for (const suggestion of [immediate, next]) {
    assert.equal(suggestion.reps, '10-15');
    assert.equal(suggestion.action, 'add_reps');
    assert.ok(suggestion.target >= 10 && suggestion.target <= 15);
    assert.doesNotMatch(suggestion.reason, /of 5|incomplete/);
}
const rows = prescribeSets(edited, day, EX_BY_ID[id], slot, 1, 'lb', immediate, null, {}, [log], false);
assert.equal(rows.filter(row => !row.warm && !row.sub).length, 2);
assert.ok(rows.every(row => row.target.reps === '10-15' && Number(row.reps) <= 15));
const top = historyFor(edited, day, id, 15);
assert.equal(nextWorkoutSuggestionFromPerformedShell(edited, EXERCISES, day, slot, 1, top.perf, 'lb').action, 'increase_load');
top.perf[id].sets[1].done = false;
assert.notEqual(nextWorkoutSuggestionFromPerformedShell(edited, EXERCISES, day, slot, 1, top.perf, 'lb').action, 'increase_load');
assert.equal(JSON.stringify(edited), input, 'evaluation never changes saved prescriptions');
assert.equal(JSON.stringify(log), logInput, 'evaluation never changes performed history');

// Duplicate exercise occurrences are not independent map keys in legacy history.
// Preserve both groups of performed sets exactly once and block earned progression
// because two separate slot prescriptions cannot be compared as a single lift.
const repeatedProgram = clone(original), repeatedDay = repeatedProgram.days[0];
const repeatedId = repeatedDay.exercises[0];
repeatedDay.exercises[1] = repeatedId;
const rowFor = (slot, weight, reps) => ({ id: repeatedId, slot, note: 'Slot ' + slot,
    sets: [{ weight: String(weight), reps: String(reps), done: true,
        target: { w: String(weight), reps: '5-8', rir: '2' } }] });
const repeatedPerf = loggedWorkoutPerformance(repeatedProgram, repeatedDay,
    [rowFor(0, 100, 5), rowFor(1, 90, 6)], 1, 'lb', {}, []);
assert.deepEqual(repeatedPerf[repeatedId].sets.map(s => s.w), [100, 90],
    'duplicate ID serialization must preserve both actual loads, not overwrite the first');
assert.deepEqual(repeatedPerf[repeatedId].duplicateExerciseSlots, [0, 1]);
assert.equal(repeatedPerf[repeatedId].prescriptionEdited, true,
    'combined duplicate slots are non-comparable for automatic progression');
const repeatedEntry = { id: 'm267-repeated', programId: repeatedProgram.id, dayId: repeatedDay.id,
    weekIndex: 1, date: 1700000000000, unit: 'lb', perf: repeatedPerf };
const repeatedAnalysis = analyzeShellHistoryForNextEngine(repeatedProgram, [repeatedEntry], EXERCISES);
assert.equal(repeatedAnalysis.performedSetCount, 2,
    'two logged sets must not become four when a program day repeats an exercise');
assert.equal(repeatedAnalysis.workouts[0].progression.find(x =>
    x.exerciseId === repeatedAnalysis.workouts[0].session.exercises[0].exerciseId).outcome, 'non_comparable',
    'repeated-slot evidence must never earn extra load progression');

// New logs retain their actual prescription when the lifter subsequently edits the plan. The
// current target can be recalibrated from that history without retroactively changing its rating.
const historical = clone(log);
historical.perf[id].prescription = snapshotNextShellPrescription(edited, day, slot, EX_BY_ID[id], 1);
for (const st of historical.perf[id].sets) { st.tr = 2; st.rirReported = true; }
const laterEdit = clone(edited);
laterEdit.overrides[key] = markUserPrescriptionOverride(laterEdit.overrides[key], 'sets', 4);
laterEdit.overrides[key] = markUserPrescriptionOverride(laterEdit.overrides[key], 'reps', '15-20');
const historicalInput = JSON.stringify(historical);
const replayed = analyzeShellHistoryForNextEngine(laterEdit, [historical], EXERCISES);
assert.equal(replayed.workouts[0].session.exercises[slot].sets, 2);
assert.deepEqual(replayed.workouts[0].session.exercises[slot].prescription.reps, [10, 15]);
assert.ok(replayed.workouts[0].performedSets.every(s => s.rir === 2), 'explicit observed effort at the target is still observed');
const shifted = nextWorkoutSuggestionForShell(laterEdit, [historical], EXERCISES, laterEdit.days[0], slot, 1);
assert.equal(shifted.reps, '15-20'); assert.equal(shifted.action, 'represcribe');
assert.equal(JSON.stringify(historical), historicalInput);
const badSnapshot = clone(historical);
badSnapshot.perf[id].prescription.sets = 200;
assert.equal(analyzeShellHistoryForNextEngine(laterEdit, [badSnapshot], EXERCISES).workouts[0].session.exercises[slot].sets, 4,
    'malformed imported history snapshots fall back to the canonical prescription');
const correctedDraft = clone(historical);
correctedDraft.perf[id].sets[0].rir = 3;
correctedDraft.perf[id].sets[0].rirReported = false;
const corrected = normalizeEditedHistoryEntry(historical, correctedDraft);
assert.deepEqual(corrected.perf[id].prescription, historical.perf[id].prescription);
assert.equal(corrected.perf[id].sets[0].rirReported, true, 'an edited effort value becomes explicit observed evidence');

// Explicit techniques share Workout's decoder rather than resurrecting the immutable base cue.
for (const [cue, type] of [['drop set', 'drop_set'], ['myo-reps', 'myo_reps'],
    ['lengthened partials', 'lengthened_partials'], [null, undefined], ['off', undefined]]) {
    const program = clone(edited);
    program.overrides[key] = markUserPrescriptionOverride(program.overrides[key], 'tech', cue);
    const history = analyzeShellHistoryForNextEngine(program, [log], EXERCISES);
    assert.equal(history.workouts[0].session.exercises[slot].advancedTechnique?.type, type);
    assert.equal(captureShellVolumeSnapshot(program, 1, EXERCISES).sessions[0].exercises[slot].advancedTechnique?.type, type);
}

// A cycle-wide swap updates bound identity in both adaptive and locked future phases, retaining
// each phase's own cells, explicit user edits, final-set cues, base engine snapshot and untouched days.
const swapped = clone(original), sibling = clone(original);
sibling.id = 'm214-later-phase';
sibling.nextEngine.program.phase = 'intensification';
const replacement = 'lat-pulldown';
assert.notEqual(swapped.days[0].exercises[slot], replacement);
swapped.days[0].exercises[slot] = replacement;
swapped.overrides[key] = { ...swapped.overrides[key], legacyExerciseId: replacement,
    nextExerciseId: nextExerciseIdForShellExercise(EX_BY_ID[replacement]) };
sibling.nextWeekPrescriptions[key][1] = { ...sibling.nextWeekPrescriptions[key][1], reps: '4-6', tech: 'drop set' };
sibling.overrides[key] = markUserPrescriptionOverride(sibling.overrides[key], 'sets', 2);
const siblingInput = JSON.stringify(sibling);
for (const adapt of [false, true]) {
    const propagated = propagateCycleEditsPure(swapped, original, [sibling], adapt)[0];
    assert.equal(propagated.days[0].exercises[slot], replacement);
    assert.equal(propagated.overrides[key].legacyExerciseId, replacement);
    assert.equal(propagated.overrides[key].nextExerciseId, nextExerciseIdForShellExercise(EX_BY_ID[replacement]));
    assert.equal(propagated.overrides[key].prescriptionOwners.sets, 'user');
    assert.deepEqual(propagated.nextWeekPrescriptions, sibling.nextWeekPrescriptions);
    assert.deepEqual(propagated.nextEngine.program, sibling.nextEngine.program, 'immutable source is not another writer');
    assert.deepEqual(propagated.days.slice(1), sibling.days.slice(1));
    const snapshot = captureShellVolumeSnapshot(propagated, 1, EXERCISES);
    assert.equal(snapshot.sessions[0].exercises[slot].exerciseId, nextExerciseIdForShellExercise(EX_BY_ID[replacement]));
    assert.equal(snapshot.sessions[0].exercises[slot].sets, 2);
    const history = analyzeShellHistoryForNextEngine(propagated,
        [historyFor(propagated, propagated.days[0], replacement, 5)], EXERCISES);
    assert.equal(history.workouts[0].session.exercises[slot].exerciseId, snapshot.sessions[0].exercises[slot].exerciseId);
    assert.equal(history.workouts[0].session.exercises[slot].name, EX_BY_ID[replacement].name);
    assert.ok(history.diagnoses.some(d => d.exerciseId === snapshot.sessions[0].exercises[slot].exerciseId));
    assert.equal(propagateCycleEditsPure(swapped, original, [propagated], adapt)[0].overrides[key].nextExerciseId,
        propagated.overrides[key].nextExerciseId, 'repeated propagation cannot revert movement identity');
}
assert.equal(JSON.stringify(sibling), siblingInput);
assert.equal(propagateCycleEditsPure(original, original, [sibling], false)[0], sibling);

// Reorder two existing lifts without copying the edited phase's dose or pinning old slot metadata.
const reordered = clone(original), reorderDay = reordered.days[0], a = keyOf(reorderDay, 0), b = keyOf(reorderDay, 1);
[reorderDay.exercises[0], reorderDay.exercises[1]] = [reorderDay.exercises[1], reorderDay.exercises[0]];
reorderDay.primaryIndex = reorderDay.primaryIndex === 0 ? 1 : reorderDay.primaryIndex === 1 ? 0 : reorderDay.primaryIndex;
reordered.nextWeekPrescriptions = swapNextSlotPrescriptions(reordered.nextWeekPrescriptions, reorderDay.id, 0, 1);
const reorderSibling = clone(sibling);
reorderSibling.progStyle[a] = 'e1rm'; reorderSibling.progStyle[b] = 'double';
reorderSibling.slotBias = { [a]: 1, [b]: -1 };
reorderSibling.weekOff = { [`${a}:2`]: true };
const reorderedPhase = propagateCycleEditsPure(reordered, original, [reorderSibling], false)[0];
assert.deepEqual(reorderedPhase.nextWeekPrescriptions[a], reorderSibling.nextWeekPrescriptions[b]);
assert.deepEqual(reorderedPhase.nextWeekPrescriptions[b], reorderSibling.nextWeekPrescriptions[a]);
assert.equal(reorderedPhase.overrides[a].legacyExerciseId, reorderDay.exercises[0]);
assert.equal(reorderedPhase.overrides[a].sets, 2, 'manual prescription stays attached to its lift');
assert.equal(reorderedPhase.progStyle[a], 'double'); assert.equal(reorderedPhase.progStyle[b], 'e1rm');
assert.equal(reorderedPhase.slotBias[a], -1); assert.equal(reorderedPhase.slotBias[b], 1);
assert.equal(reorderedPhase.weekOff[`${b}:2`], true);
assert.equal(computeCell(reorderedPhase, reorderedPhase.days[0], reorderDay.exercises[0], 0, 1).sets, 2);
const repaired = repairShellVolume(reorderedPhase, EXERCISES);
const manual = Object.values(repaired.program.overrides).find(o => o.legacyExerciseId === reorderDay.exercises[0] && o.prescriptionOwners?.sets === 'user');
assert.equal(manual?.sets, 2, 'Auto-fix cannot rewrite a remapped explicit set count');

// Repair previously saved stale propagation at read time too, without rewriting backups.
const stale = clone(sibling);
stale.days[0].exercises[slot] = replacement;
const staleInput = JSON.stringify(stale);
assert.equal(captureShellVolumeSnapshot(stale, 1, EXERCISES).sessions[0].exercises[slot].exerciseId,
    nextExerciseIdForShellExercise(EX_BY_ID[replacement]));
const staleHistory = analyzeShellHistoryForNextEngine(stale,
    [historyFor(stale, stale.days[0], replacement, 5)], EXERCISES);
assert.equal(staleHistory.workouts[0].session.exercises[slot].exerciseId,
    nextExerciseIdForShellExercise(EX_BY_ID[replacement]));
assert.equal(JSON.stringify(stale), staleInput);

// Completing a real block or converting a standalone plan must not revive the stale base roster.
const swapInput = JSON.stringify(swapped);
const manualSwap = clone(swapped);
manualSwap.progStyle[key] = 'e1rm';
const converted = convertProgramToNextCycleForShell({ program: swapped, templateId: 'powerbuilding',
    legacyExercises: EXERCISES, seed: fixture.seed, adaptBetweenBlocks: false });
assert.ok(converted.blocks.length > 1);
for (const future of converted.allBlocks.slice(1)) {
    assert.ok(future.days[0].exercises.includes(replacement), 'locked future blocks retain the lifter\'s actual swap');
    assert.ok(!future.days[0].exercises.includes(original.days[0].exercises[slot]), 'the removed base identity cannot return');
    assert.equal(future.nextEngine.audit.result, 'pass');
}
assert.equal(JSON.stringify(swapped), swapInput, 'conversion projection never rewrites the current block');
for (const adaptBetweenBlocks of [false, true]) {
    const manualCycle = convertProgramToNextCycleForShell({ program: manualSwap, templateId: 'powerbuilding',
        legacyExercises: EXERCISES, seed: fixture.seed, adaptBetweenBlocks });
    for (const future of manualCycle.allBlocks.slice(1)) {
        const retained = future.nextEngine.program.sessions.flatMap(s => s.exercises)
            .find(e => e.exerciseId === nextExerciseIdForShellExercise(EX_BY_ID[replacement]));
        assert.ok(retained, 'viable retained manual movement remains in later phases');
        assert.equal(retained.progressionStyle, 'e1rm', 'per-lift manual methods survive locked and adaptive continuation');
        assert.equal(retained.progressionSelection.source, 'manual');
    }
    const active = manualCycle.currentProgram, history = [];
    for (let week = 1; week <= active.config.weeks; week++) for (const d of active.days) {
        const perf = {};
        d.exercises.forEach((legacyId, slot) => {
            const cell = getNextShellCell(active, d, slot, week);
            const reps = Math.max(...String(cell.reps).match(/\d+/g).map(Number));
            const rir = Math.min(10, Math.min(...String(cell.rir).match(/\d+/g).map(Number)) + 1);
            perf[legacyId] = { weight: 100 + week * 5, reps,
                prescription: snapshotNextShellPrescription(active, d, slot, EX_BY_ID[legacyId], week),
                sets: Array.from({ length: cell.sets }, () => ({ w: 100 + week * 5, r: reps, rir, rirReported: true })) };
        });
        history.push({ id: `m214-${adaptBetweenBlocks}-${history.length}`, programId: active.id, dayId: d.id,
            weekIndex: week, date: 1000000000 + history.length * 86400000, unit: 'lb', perf });
    }
    assert.equal(analyzeShellHistoryForNextEngine(active, history, EXERCISES).readyForNextBlock, true);
    const advanced = advanceNextCycleForShell({ cycle: manualCycle.cycle, activeProgram: active,
        history, legacyExercises: EXERCISES });
    assert.equal(advanced.nextProgram.nextEngine.audit.result, 'pass');
    const retained = advanced.nextProgram.nextEngine.program.sessions.flatMap(s => s.exercises)
        .find(e => e.exerciseId === nextExerciseIdForShellExercise(EX_BY_ID[replacement]));
    assert.ok(retained, 'actual history-driven block completion keeps the swapped movement');
    assert.equal(retained.progressionStyle, 'e1rm', 'actual block completion keeps its user-selected method');
}

console.log('PASS M214 executable synchronization: user prescription/method ownership, honest completed-set evidence, shared techniques, cycle swaps/reorders, live diagnoses, stale saved identity, immutable history/base and Auto-fix protection.');
