import assert from 'node:assert/strict';
import { EXERCISES } from '../modules/engine-api.js';
import { generateNextCycleForShell, advanceNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { analyzeShellHistoryForNextEngine } from '../modules/next-engine/workout-history-adapter.js';
import { captureShellVolumeSnapshot } from '../modules/next-engine/volume-repair.js';
import { createEngineContext } from '../modules/next-engine/engine-context.js';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { diagnoseExerciseResponse } from '../modules/next-engine/response.js';
import { config, completedHistory, ids } from './m265-generation-fixture.mjs';

const roundtrip = value => JSON.parse(JSON.stringify(value));
const count = view => view.sessions.reduce((n, day) => n + day.exercises.reduce((m, ex) => m + ex.sets, 0), 0);
const advance = (cycle, program, history, name) => advanceNextCycleForShell({
    cycle, activeProgram: program, history, legacyExercises: EXERCISES, makeId: ids(name)
});
const build = (configuration, blocks, seed) => generateNextCycleForShell({
    templateId: 'custom', config: configuration, blocks, legacyExercises: EXERCISES,
    seed, makeId: ids(`m323-${seed}`), adaptBetweenBlocks: true, adaptTrainingSplit: true
});

// Successful low-rep/intense weeks must not be judged against week one's
// higher rep/effort targets. Missing snapshots retain the legacy fallback;
// genuine misses of the historical target must still be diagnosed.
const opening = { exerciseId: 'fixture-lift', name: 'Fixture lift', role: 'hypertrophy_isolation',
    sets: 3, prescription: { reps: [8, 12], rir: [3, 4] } };
const later = { ...opening, sets: 2, prescription: { reps: [3, 5], rir: [1, 2] } };
const normalRecovery = { status: 'normal' };
const exposures = Array.from({ length: 3 }, (_, i) => ({
    completedAt: new Date(Date.UTC(2026, 8, 1 + i * 7)).toISOString(), plannedExercise: later,
    sets: Array.from({ length: 2 }, () => ({ load: 100, reps: 3, rir: 1 }))
}));
const successfulDiagnosis = diagnoseExerciseResponse(opening, exposures, normalRecovery);
assert.notEqual(successfulDiagnosis.state, 'fatigue_limited');
assert.equal(successfulDiagnosis.state, 'possibly_understimulated',
    'Completion and effort comparisons also use each exposure target');
const failed = exposures.map(x => ({ ...x, sets: x.sets.map(s => ({ ...s, reps: 1, rir: 0 })) }));
assert.equal(diagnoseExerciseResponse(opening, failed, normalRecovery).state, 'fatigue_limited');
assert.equal(diagnoseExerciseResponse(opening, exposures.map(({ plannedExercise, ...x }) => x), normalRecovery).state,
    'fatigue_limited', 'History without an exposure snapshot retains its existing fallback');
const underloaded = exposures.map(x => ({ ...x, sets: x.sets.map(s => ({ ...s, reps: 5, rir: 3 })) }));
assert.equal(diagnoseExerciseResponse(opening, underloaded, normalRecovery).state, 'underloaded');
assert.notEqual(diagnoseExerciseResponse(opening, exposures.map(x => ({ ...x,
    sets: x.sets.map(s => ({ ...s, rir: null })) })), normalRecovery).state, 'underloaded',
    'Missing effort must not become confident underloading evidence');

// A fixed one-week block used to accept four unique workouts of the same
// authored day as completion evidence for its four different training days.
const short = build(config, [{ phase: 'mixed_accumulation', weeks: 1 },
    { phase: 'strength_accumulation', weeks: 2 }], 32301);
const program = short.blocks[0], complete = completedHistory(program);
const repeats = complete.map((row, i) => ({ ...structuredClone(complete[0]),
    id: `repeat-${i}`, date: row.date, updatedAt: row.updatedAt }));
const untouched = JSON.stringify({ short, repeats });
const repeatAnalysis = analyzeShellHistoryForNextEngine(program, repeats, EXERCISES);
assert.equal(repeatAnalysis.workoutCount, complete.length, 'Repeated days remain genuine performance evidence');
assert.deepEqual(repeatAnalysis.plannedDayCoverage, { requiredDays: 4, observedDays: 1,
    missingDayIds: program.days.slice(1).map(day => day.id) });
assert.equal(repeatAnalysis.readyForNextBlock, false, 'Repeats cannot substitute for three untrained days');
assert.equal(repeatAnalysis.cycleState.recommendedNextPhase, undefined);
assert.match(repeatAnalysis.cycleState.rationale, /1 of 4 planned training days/);
assert.throws(() => advance(short.cycle, program, repeats, 'm323-refused'),
    error => error.code === 'NEXT_CYCLE_NOT_READY' && /1 of 4 planned training days/.test(error.message));
assert.equal(JSON.stringify({ short, repeats }), untouched, 'Refusal cannot alter saved state or history');

const resumed = roundtrip([...repeats, ...complete.slice(1)]);
assert.equal(analyzeShellHistoryForNextEngine(program, resumed, EXERCISES).readyForNextBlock, true,
    'Completing the missing days unlocks normal advancement after a reload');
assert.equal(advance(short.cycle, program, resumed, 'm323-resumed').nextProgram.id, short.blocks[1].id);

// A corrected record replaces its old day evidence; foreign, empty and
// unresolved sessions cannot manufacture coverage for the omitted days.
const revisions = complete.map((row, i) => ({ ...repeats[i], id: row.id, updatedAt: row.date + 1000 }));
const corrected = analyzeShellHistoryForNextEngine(program, [...complete, ...revisions], EXERCISES);
assert.equal(corrected.plannedDayCoverage.observedDays, 1);
assert.equal(corrected.readyForNextBlock, false);
const unrelated = complete.slice(1).map(row => ({ ...row, programId: 'another-cycle' }));
const unresolved = complete.slice(1).map(row => ({ ...row, id: `unresolved-${row.id}`,
    dayId: 'missing', dayLabel: 'Missing day' }));
const empty = complete.slice(1).map(row => ({ ...row, id: `empty-${row.id}`, perf: {} }));
const noisy = analyzeShellHistoryForNextEngine(program, [...repeats, ...unrelated, ...unresolved, ...empty], EXERCISES);
assert.equal(noisy.plannedDayCoverage.observedDays, 1);
assert.equal(noisy.workoutCount, repeats.length);
assert.equal(noisy.readyForNextBlock, false);

// Broad repeated misses may still warrant early recovery even when the whole
// authored week has not been trained. Day coverage must never suppress that.
const tired = structuredClone(repeats);
tired.forEach((entry, i) => Object.values(entry.perf).forEach(perf => {
    perf.weight = 140 - i * 10; perf.reps = 1;
    perf.sets.forEach(set => { set.w = perf.weight; set.r = 1; set.rir = 0; set.rirReported = true; });
}));
const fatigueAnalysis = analyzeShellHistoryForNextEngine(program, tired, EXERCISES);
assert.equal(fatigueAnalysis.plannedDayCoverage.observedDays, 1);
assert.equal(fatigueAnalysis.recovery.status, 'deload_recommended');
assert.equal(fatigueAnalysis.readyForNextBlock, true);
assert.equal(advance(short.cycle, program, tired, 'm323-recovery').insertedRecovery, true);

const cases = [
    { days: 3, split: 'full_body', equipment: ['dumbbell', 'bench', 'band'], label: 'limited equipment' },
    { days: 4, split: 'upper_lower', equipment: config.equipment, label: 'isolated bad day', badDay: true },
    { days: 5, split: 'ulppl', equipment: config.equipment, label: 'manual set edit', edit: true }
];
for (const sample of cases) {
    const generated = build({ ...config, ...sample, goal: 'both', name: `Replay ${sample.days}d` },
        [{ phase: 'hypertrophy_accumulation', weeks: 3 },
            { phase: 'strength_accumulation', weeks: 3 }, { phase: 'peak', weeks: 2 }], 32300 + sample.days);
    let cycle = generated.cycle, current = generated.blocks[0], history = [];
    if (sample.edit) {
        current = structuredClone(current);
        const key = `${current.days[0].id}:0`;
        const last = captureShellVolumeSnapshot(current, current.config.weeks, EXERCISES).sessions[0].exercises[0];
        current.overrides = { ...current.overrides, [key]: { ...current.overrides?.[key],
            sets: last.sets + 1, prescriptionOwners: { sets: 'user' } } };
    }
    const authored = JSON.stringify(cycle.nextEngineCycle.plannedBlocks);
    const totals = [];
    for (let block = 0; block < 3; block++) {
        const logs = completedHistory(current);
        if (sample.badDay) {
            const disrupted = logs[Math.floor(logs.length / 2)];
            disrupted.badDay = true;
            Object.values(disrupted.perf).forEach(perf => perf.sets.forEach(set => { set.r = 1; set.rir = 0; }));
        }
        history = [...history, ...logs];
        const input = roundtrip({ cycle, current, history });
        const before = JSON.stringify(input);
        const next = advance(input.cycle, input.current, input.history, `m323-next-${sample.days}-${block}`);
        assert.equal(JSON.stringify(input), before, 'Every transition preserves user edits and completed history');
        assert.equal(JSON.stringify(next.cycle.nextEngineCycle.plannedBlocks), authored);
        assert.equal(next.insertedRecovery, false, 'Expected healthy replay: '+JSON.stringify({scenario:sample.label,block,classification:next.analysis.classification,recovery:next.analysis.recovery,decisions:next.analysis.workouts.at(-1)?.progression.map(d=>({name:d.exerciseName,reason:d.reasonCode}))}));
        const weekSets = Array.from({ length: current.config.weeks }, (_, i) =>
            count(captureShellVolumeSnapshot(current, i + 1, EXERCISES)));
        totals.push({ phase: current.nextEngine.program.phase, sets: weekSets });
        if (block === 2) {
            assert.equal(next.done, true);
            assert.equal(next.cycle.done, true);
            break;
        }
        assert.equal(next.nextProgram.id, cycle.blockIds[block + 1], 'Keep the reserved preview identity');
        assert.equal(next.nextProgram.days.length, sample.days);
        assert.equal(next.nextProgram.nextEngine.program.audit.result, 'pass');
        const context = createEngineContext(normalizeRequest(next.nextProgram.nextEngine.request));
        for (let week = 1; week <= next.nextProgram.config.weeks; week++) {
            const view = captureShellVolumeSnapshot(next.nextProgram, week, EXERCISES);
            assert.ok(view && !view.missing);
            assert.ok(view.sessions.every(day => day.estimatedMinutes <= day.maxMinutes + .001));
            for (const day of view.sessions) for (const exercise of day.exercises)
                assert.ok(context.equipmentEligible(context.exerciseById(exercise.exerciseId), day.day),
                    'Adaptation must keep every executable exercise compatible with the authored equipment');
            if (block === 0) assert.ok(count(view) >= Math.ceil(weekSets.at(-1) * .5));
        }
        cycle = next.cycle; current = next.nextProgram;
    }
    console.log('M323 replay ' + JSON.stringify({ days: sample.days, scenario: sample.label,
        workouts: history.length, totals }));
}
console.log('PASS M323: fixed-cycle day coverage, corrected/missed history, early fatigue recovery and full constrained/uneven/edited multi-block replay.');
