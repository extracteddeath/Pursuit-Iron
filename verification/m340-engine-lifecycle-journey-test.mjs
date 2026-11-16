import assert from 'node:assert/strict';
import {
    EXERCISES, EX_BY_ID, exerciseAt, computeCell, getNextShellCell,
    prescribeSets, sessionSuggestion, loggedWorkoutPerformance, summarizeSession,
    normalizeEditedHistoryEntry, resolveNextShellExerciseId, previousWorkoutSet
} from '../modules/engine-api.js';
import { generateNextCycleForShell, advanceNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { analyzeShellHistoryForNextEngine, nextWorkoutSuggestionForShell } from '../modules/next-engine/workout-history-adapter.js';
import { refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
import { auditSetDecisionTraces, loadableAtOrBelow } from '../modules/training-domain/prescriptions.js';
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
    sessions: 0, workingSets: 0, protectedRefreshCases: 0, provenanceCases: 0, persistedTraceCases: 0,
    loadGridCases: 0, generatedBlocks: 0, cycleTransitions: 0 };
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
        // Every slot must traverse the real history-to-next-prescription adapter.
        // Earlier audits only requested history advice for slot zero, so a broken
        // rear-delt/late-session target could pass an otherwise green lifecycle audit.
        const suggestion = sessionSuggestion(p, day, slot, null, 'lb', week, prior);
        const rows = prescribeSets(p, day, ex, slot, week, 'lb', suggestion, null, {}, prior, true);
        assert.deepEqual(auditSetDecisionTraces(rows),[],
            'An actual Workout target diverged from recorded engine evidence: ' + day.id + ':' + slot);
        // M418: audit the actual displayed target, its exact prior position,
        // and the loading grid used by this Workout. These checks use the real
        // sessionSuggestion + prescribeSets path for *every* exercise slot.
        const auditRows = rows.filter(set => !set.warm && !set.sub && !set.added);
        for (const [setIndex, row] of auditRows.entries()) {
            const evidence = row.target?.decisionTrace;
            assert.ok(evidence && evidence.schemaVersion === 1,
                'Fresh working target lacks diagnostic provenance: ' + day.id + ':' + slot + ':' + setIndex);
            assert.deepEqual([evidence.programId, evidence.dayId, evidence.exerciseId,
                evidence.slot, evidence.weekIndex, evidence.workingSetIndex],
                [String(p.id), String(day.id), ex.id, slot, week, setIndex],
                'Trace identity was borrowed from a different plan/day/exercise/set');
            assert.equal(evidence.target.unit, 'lb');
            assert.equal(evidence.target.load, Number(row.weight) || null,
                'Displayed set load is not its recorded prescription decision');
            assert.equal(evidence.target.reps, Number(row.reps),
                'Displayed set reps are not their recorded prescription decision');
            const previous = previousWorkoutSet(suggestion?.last, setIndex);
            assert.equal(evidence.source?.originalLoad ?? null, previous?.w ?? null,
                'Trace history source does not match this set position');
            assert.equal(evidence.source?.reps ?? null, previous?.r ?? null,
                'Trace historical reps do not match this set position');
            if (previous && suggestion?.sourceEntryId != null)
                assert.equal(evidence.source.historyEntryId, String(suggestion.sourceEntryId),
                    'Trace points at the wrong prior workout entry');
            if (Number(row.weight) > 0) {
                const prescribed = Number(row.weight);
                assert.ok(Math.abs(loadableAtOrBelow(ex, prescribed, 'lb') - prescribed) < 1e-6,
                    'Prescribed weight is not on the exercise equipment grid: ' +
                    JSON.stringify({ day: day.id, slot, setIndex, prescribed }));
                trace.loadGridCases++;
            }
            trace.provenanceCases++;
        }
        // The existing trace auditor must catch tampering, not just bless
        // correct rows; a migrated/persisted record cannot rewrite one target.
        if (auditRows.length) {
            const corrupted = clone(rows);
            const tampered = corrupted.find(row => !row.warm && !row.sub && !row.added);
            tampered.target.decisionTrace.target.reps += 1;
            assert.ok(auditSetDecisionTraces(corrupted).some(v =>
                v.code === 'display_target_diverges_from_trace'),
                'Target/trace corruption escaped the existing audit');
        }
        const works = rows.filter(set => !set.warm && !set.sub);
        if (suggestion?.action === 'add_reps' && cell.progressionStyle === 'double'
            && Array.isArray(suggestion.setTargets) && suggestion.setTargets.length === works.length) {
            assert.deepEqual(works.map(set => Number(set.weight)),
                suggestion.setTargets.map(set => Number(set.weight)),
                'Late workout slot ignored the history-derived double-progression loads: '
                    + day.id + ':' + slot);
        }
        assert.equal(works.length, Number(cell.sets),
            'Wrong loggable working-set count: ' + day.id + ':' + slot);
        assert.ok(works.every(set => set.target?.reps && set.valueOwner),
            'Every automatically created working row must explain its target and owner');
        // Rendering the same untouched workout twice must never drift a second-set load.
        const again = prescribeSets(p, day, ex, slot, week, 'lb', suggestion, null, {}, prior, true);
        assert.deepEqual(again, rows, 'A read recomputed live targets without new history');
        // M418: protected rows must survive a CHANGED prescription, not merely
        // a read of identical targets. Replaying every slot/set position exposes
        // late-exercise ownership defects a first-exercise-only test would miss.
        const newer = clone(again).map(set => {
            if (set.warm || set.sub || set.added) return set;
            const weight = String((Number(set.weight) || 95) + 5);
            const reps = String((Number(set.reps) || 8) + 1);
            return { ...set, weight, reps, target: {
                ...set.target, w: weight, prefillReps: reps
            } };
        });
        const workingIndices = rows.map((set, i) =>
            !set.warm && !set.sub && !set.added ? i : -1).filter(i => i >= 0);
        for (const index of workingIndices) {
            for (const state of ['manual-prefill', 'manual-edited', 'completed']) {
                const paused = clone(rows), edited = paused[index];
                // A user can own the row by editing RIR alone, leaving weight
                // and reps equal to their original automatic prefills.
                edited.actualRIR = 1;
                if (state.startsWith('manual')) {
                    edited.valueOwner = 'user';
                    edited.auto = false;
                }
                if (state !== 'manual-prefill') {
                    edited.weight = String(37 + index);
                    edited.reps = '11';
                }
                if (state === 'completed') edited.done = true;
                const saved = clone(paused);
                const restored = refreshPendingSetTargets(paused, newer);
                for (const field of ['weight', 'reps', 'actualRIR', 'valueOwner',
                    'auto', 'done', 'target']) {
                    assert.deepEqual(restored[index][field], saved[index][field],
                        'M418 refresh overwrote ' + state + ' ' + field + ' in ' +
                        JSON.stringify({ day: day.id, slot, set: index, week }));
                }
                assert.deepEqual(paused, saved,
                    'M418 refresh mutated its saved input at ' +
                    JSON.stringify({ day: day.id, slot, set: index, state, week }));
                trace.protectedRefreshCases++;
            }
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
        const slot = submitted.find(row => row.id === id)?.slot;
        const engineId = resolveNextShellExerciseId(p, day, slot, EX_BY_ID[id]);
        assert.ok(result.prescription?.schemaVersion === 1 && result.prescription.exerciseId === engineId,
            'Log prescription identity/schema mismatch: ' + JSON.stringify({ id, expected: engineId, actual: result.prescription, day: day.id, slot: submitted.find(e => e.id === id)?.slot, authored: day.exercises }));
        assert.ok(result.sets.every(set => Number.isFinite(set.w) && Number.isFinite(set.r)
            && set.r > 0), 'Log contains an invalid completed numeric set');
        // The logged set must retain the original prescription *and* the
        // applied, user-observed values so later history replay is explainable.
        const workoutRows = submitted.find(row => row.id === id)?.sets
            .filter(row => !row.warm && !row.sub && !row.added) || [];
        for (const [setIndex, sourceRow] of workoutRows.entries()) {
            const storedRow = result.sets.find(row => row.workingSetIndex === setIndex);
            assert.ok(storedRow?.decisionTrace?.schemaVersion === 1,
                'Logged working set lost its decision trace: ' +
                JSON.stringify({ day: day.id, slot, setIndex }));
            const expectedTrace = sourceRow.target.decisionTrace;
            const { applied, ...originalTrace } = storedRow.decisionTrace;
            assert.deepEqual(originalTrace, expectedTrace,
                'Logger changed the original prescribed decision: ' +
                JSON.stringify({ day: day.id, slot, setIndex }));
            assert.deepEqual([applied.load, applied.reps, applied.owner],
                [storedRow.w, storedRow.r, sourceRow.valueOwner],
                'Logger failed to distinguish actual performance from the prefill');
            trace.persistedTraceCases++;
        }
    }
    const date = Date.UTC(2026, 9, 1 + serial++);
    const entry = { id: 'm340-log-' + serial, programId: p.id, programName: p.name,
        dayId: day.id, dayLabel: day.label, weekIndex: week, unit: 'lb',
        date, updatedAt: date, durationMin: 65, perf };
    const stored = clone(entry);
    assert.equal(stored.id, entry.id);
    assert.equal(stored.programId, entry.programId);
    assert.equal(stored.dayId, entry.dayId);
    assert.equal(countWorking(stored.perf), expected,
        'JSON persistence changed completed working-set evidence');
    for (const [id, lift] of Object.entries(perf))
        assert.deepEqual(stored.perf[id].prescription, lift.prescription,
            'JSON persistence changed the historical prescription snapshot');
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
                    assert.deepEqual(suggestion.last, clone(entry.perf[firstId]),
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
stage('audit-finished', { sessions: trace.sessions, workingSets: trace.workingSets,
    protectedRefreshCases: trace.protectedRefreshCases,
    provenanceCases: trace.provenanceCases, persistedTraceCases: trace.persistedTraceCases,
    loadGridCases: trace.loadGridCases });
assert.ok(trace.protectedRefreshCases > trace.workingSets,
    'Every working set position must be audited in multiple protected states');
assert.equal(trace.provenanceCases, trace.workingSets,
    'Every completed working-set position must have prior-source and load evidence');
assert.equal(trace.persistedTraceCases, trace.workingSets,
    'Every completed working-set position must preserve its trace during logging');
assert.ok(trace.loadGridCases > 0, 'At least one weighted set must validate its equipment grid');
console.log('ENGINE_LIFECYCLE_AUDIT ' + JSON.stringify({ status: 'pass', ...trace }));
