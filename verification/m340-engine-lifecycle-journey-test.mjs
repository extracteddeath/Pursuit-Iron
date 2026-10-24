import assert from 'node:assert/strict';
import {
    EXERCISES, EX_BY_ID, exerciseAt, computeCell, getNextShellCell,
    prescribeSets, sessionSuggestion, loggedWorkoutPerformance, summarizeSession,
    normalizeEditedHistoryEntry
} from '../modules/engine-api.js';
import { generateNextCycleForShell, advanceNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { analyzeShellHistoryForNextEngine, nextWorkoutSuggestionForShell } from '../modules/next-engine/workout-history-adapter.js';
import { refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
import { config, ids } from './m265-generation-fixture.mjs';

// M340: one continuous, serialized program/cycle -> prescription -> live set ->
// completion/history -> recommendation -> adaptation -> next cycle journey.
// Other gates deliberately remain owners of their deeper edge-case matrices.
const clone = value => JSON.parse(JSON.stringify(value));
const countWorking = perf => Object.values(perf).reduce((sum, lift) =>
    sum + lift.sets.filter(set => !set.warm && !set.sub).length, 0);
const maxRep = cell => Number(String(cell.reps).match(/[0-9]+/g)?.at(-1)) || 8;
const targetRir = cell => Number(String(cell.rir).match(/[0-9]+/g)?.[0] ?? 2);
const trace = { scenario: 'M340 continuous engine lifecycle', seed: 34011, stages: [],
    sessions: 0, workingSets: 0, generatedBlocks: 0, cycleTransitions: 0 };
function stage(name, detail = {}) {
    trace.stages.push({ name, ...detail });
    console.log('LIFECYCLE_STAGE ' + JSON.stringify(trace.stages.at(-1)));
}
const original = generateNextCycleForShell({
    templateId: 'custom',
    config: { ...config, name: 'M340 real logging journey', days: 4, split: 'upper_lower', weeks: 1 },
    blocks: [
        { phase: 'mixed_accumulation', weeks: 1 },
        { phase: 'strength_accumulation', weeks: 2 }
    ],
    legacyExercises: EXERCISES, seed: trace.seed,
    makeId: ids('m340-cycle'), adaptBetweenBlocks: true, adaptTrainingSplit: true
});
assert.equal(original.blocks.length, 2);
assert.ok(original.blocks.every(p => p.nextEngine?.program?.audit?.result === 'pass'));
let { cycle } = original;
let program = original.blocks[0];
let history = [];
let serial = 0;
trace.generatedBlocks = original.blocks.length;
const previewIdentity = original.blocks[1].id;
stage('creation-and-preview', { blocks: original.blocks.length, previewId: previewIdentity,
    days: program.days.length });

function completeRealWorkout(p, day, week, prior) {
    const before = JSON.stringify({ program: p, history: prior });
    const submitted = [];
    for (let slot = 0; slot < day.exercises.length; slot++) {
        const id = exerciseAt(p, day, slot, week);
        const ex = EX_BY_ID[id];
        const cell = computeCell(p, day, id, slot, week);
        if (!ex || !cell || cell.missing || !(cell.sets > 0)) continue;
        const canonical = getNextShellCell(p, day, slot, week);
        assert.equal(Number(canonical.sets), Number(cell.sets),
            'Preview and workout disagree about set count: ' + day.id + ':' + slot);
        assert.equal(String(canonical.reps), String(cell.reps),
            'Preview and workout disagree about rep range: ' + day.id + ':' + slot);
        // Only the leading lift needs a prior-history decision in this bounded run;
        // all other slots still use the exact production prescription builder.
        const suggestion = slot === 0
            ? sessionSuggestion(p, day, slot, null, 'lb', week, prior) : null;
        const rows = prescribeSets(p, day, ex, slot, week, 'lb', suggestion, null, {}, prior, true);
        const works = rows.filter(set => !set.warm && !set.sub);
        assert.equal(works.length, Number(cell.sets),
            'Wrong loggable working-set count: ' + day.id + ':' + slot);
        assert.ok(works.every(set => set.target?.reps && set.valueOwner),
            'Every automatically created working row must explain its target and owner');
        // Rendering the same untouched workout twice must never drift a second-set load.
        const again = prescribeSets(p, day, ex, slot, week, 'lb', suggestion, null, {}, prior, true);
        assert.deepEqual(again, rows, 'A read recomputed live targets without new history');
        if (works.length >= 2) {
            const paused = clone(rows);
            const indices = paused.map((set, i) => !set.warm && !set.sub ? i : -1).filter(i => i >= 0);
            paused[indices[0]].done = true;
            paused[indices[1]].valueOwner = 'user';
            paused[indices[1]].auto = false;
            paused[indices[1]].weight = '37';
            const restored = refreshPendingSetTargets(paused, again);
            assert.equal(restored[indices[0]].weight, paused[indices[0]].weight,
                'Resume rewrote a completed load');
            assert.equal(restored[indices[1]].weight, '37',
                'Resume rewrote a manually edited later-set load');
            assert.deepEqual(paused, clone(paused), 'Resume must leave its source rows usable');
        }
        // This is a completed workout, not a synthetic perf map. The real logger
        // serializes the rows, their targets, RIR provenance, techniques and identity.
        const workReps = maxRep(cell);
        const rir = targetRir(cell);
        const completed = rows.map(set => ({
            ...set, done: true,
            weight: set.weight && Number(set.weight) > 0 ? set.weight
                : (ex.equip?.includes('dumbbell') ? '25' : '95'),
            reps: set.sub && !Number(set.reps) ? '4' : set.warm ? set.reps : String(workReps),
            ...(!set.warm && !set.sub ? { actualRIR: rir } : {})
        }));
        submitted.push({ id, slot, sets: completed });
    }
    assert.ok(submitted.length > 0, 'Executable training day has no loggable exercises');
    const perf = loggedWorkoutPerformance(p, day, submitted, week, 'lb', {}, prior);
    const expected = submitted.reduce((sum, lift) =>
        sum + lift.sets.filter(set => !set.warm && !set.sub).length, 0);
    assert.equal(countWorking(perf), expected,
        'Actual completed working sets were lost or duplicated during persistence');
    const summary = summarizeSession(p, day, week);
    assert.equal(summary.sets, expected,
        'Home/Plan/Preview planned dose disagrees with logged working sets');
    for (const [id, result] of Object.entries(perf)) {
        assert.ok(result.prescription?.schemaVersion === 1 && result.prescription.exerciseId === id,
            'Log lacks a valid original prescription snapshot: ' + id);
        assert.ok(result.sets.every(set => Number.isFinite(set.w) && Number.isFinite(set.r)
            && set.r > 0), 'Log contains an invalid completed numeric set');
    }
    const date = Date.UTC(2026, 9, 1 + serial++);
    const entry = { id: 'm340-log-' + serial, programId: p.id, programName: p.name,
        dayId: day.id, dayLabel: day.label, weekIndex: week, unit: 'lb',
        date, updatedAt: date, durationMin: 65, perf };
    assert.deepEqual(clone(entry), entry, 'Completed history is not JSON-persistable');
    assert.equal(JSON.stringify({ program: p, history: prior }), before,
        'Reading/persisting a workout mutated the saved program or history');
    trace.sessions++;
    trace.workingSets += expected;
    return entry;
}

for (let block = 0; block < 2; block++) {
    const blockStart = history.length;
    const firstDay = program.days[0];
    for (let week = 1; week <= program.config.weeks; week++) {
        for (const day of program.days) {
            const entry = completeRealWorkout(program, day, week, history);
            history = clone([...history, entry]); // exercise the actual storage boundary
            const analysis = analyzeShellHistoryForNextEngine(program, history, EXERCISES);
            assert.equal(analysis.workoutCount, history.length - blockStart,
                'A persisted workout disappeared or was counted twice in its active block');
            if (week === 1 && day.id === firstDay.id) {
                const suggestion = nextWorkoutSuggestionForShell(program, history, EXERCISES,
                    firstDay, 0, week, 'lb');
                if (suggestion) {
                    const firstId = exerciseAt(program, firstDay, 0, week);
                    assert.deepEqual(suggestion.last, entry.perf[firstId],
                        'Progression LAST is not the exact set log just saved');
                    assert.ok(suggestion.reason && suggestion.action,
                        'Logged exposure has an unexplained next-workout decision');
                }
            }
        }
    }
    const stable = JSON.stringify({ cycle, program, history });
    const analysis = analyzeShellHistoryForNextEngine(program, history, EXERCISES);
    assert.equal(analysis.workoutCount, history.length - blockStart);
    assert.equal(analysis.performedSetCount,
        history.slice(blockStart).reduce((sum, entry) => sum + countWorking(entry.perf), 0),
        'Completion analytics disagrees with the exact serialized set log');
    assert.equal(analysis.plannedDayCoverage.observedDays, program.days.length);
    assert.equal(analysis.readyForNextBlock, true,
        'Every authored day was fully logged but the cycle cannot advance');
    stage('completion-and-adaptation-evidence', { block, workouts: analysis.workoutCount,
        workingSets: analysis.performedSetCount, days: analysis.plannedDayCoverage.observedDays });

    // A corrected history record supersedes its earlier revision rather than
    // creating another workout, another training day, or extra volume.
    const prior = history[blockStart];
    const draft = clone(prior);
    const liftId = Object.keys(draft.perf)[0];
    draft.perf[liftId].sets[0].r += 1;
    const revision = normalizeEditedHistoryEntry(prior, draft);
    const revisions = [prior, ...history, revision];
    const editedAnalysis = analyzeShellHistoryForNextEngine(program, revisions, EXERCISES);
    assert.equal(editedAnalysis.workoutCount, analysis.workoutCount,
        'History correction manufactured an additional completed workout');
    assert.equal(editedAnalysis.performedSetCount, analysis.performedSetCount,
        'History correction manufactured extra completed working sets');
    assert.equal(JSON.stringify({ cycle, program, history }), stable,
        'History correction changed the original persisted plan/log data');
    stage('revised-history-replay', { block, count: editedAnalysis.workoutCount });

    const result = advanceNextCycleForShell({
        cycle: clone(cycle), activeProgram: clone(program), history: clone(history),
        legacyExercises: EXERCISES, makeId: ids('m340-advance-' + block)
    });
    assert.equal(JSON.stringify({ cycle, program, history }), stable,
        'Cycle advancement mutated its input objects');
    assert.equal(result.insertedRecovery, false, 'Healthy completed work inserted unexpected recovery');
    trace.cycleTransitions++;
    if (block === 0) {
        assert.equal(result.nextProgram?.id, previewIdentity,
            'Adaptation abandoned the reserved, user-visible next-block identity');
        assert.equal(result.nextProgram.nextEngine?.program?.audit?.result, 'pass');
        assert.equal(result.cycle.activeBlock, 1);
        cycle = clone(result.cycle);
        program = clone(result.nextProgram);
        stage('adapted-preview-to-active', { block: 1, id: program.id, days: program.days.length });
    } else {
        assert.equal(result.done, true, 'Completed final phase did not finish the cycle');
        assert.equal(result.cycle.done, true);
        stage('cycle-completed', { blocks: 2, totalLogs: history.length });
    }
}
stage('audit-finished', { sessions: trace.sessions, workingSets: trace.workingSets });
console.log('ENGINE_LIFECYCLE_AUDIT ' + JSON.stringify({ status: 'pass', ...trace }));
