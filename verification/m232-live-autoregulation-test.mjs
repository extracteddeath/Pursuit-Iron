import assert from 'node:assert/strict';
import { applyLiveAutoregulation, rollbackLiveAutoregulation } from '../modules/next-engine/live-autoregulation.js';
import { loggedWorkoutPerformance, EX_BY_ID, sessionSuggestion } from '../modules/training-domain.js';
import { progressionExposureContext } from '../modules/next-engine/history-contract.js';

const cell = { sets: 3, reps: [8, 12], rir: [1, 2], role: 'hypertrophy_isolation', progressionStyle: 'double' };
const pending = () => ({ weight: '100', reps: '8', done: false, auto: true, valueOwner: 'prescription', target: { w: 100, reps: [8, 12], rir: 1, prefillReps: '8' } });
const sets = [{ ...pending(), reps: '6', actualRIR: 0, done: true }, { ...pending(), reps: '7', actualRIR: 0, done: true }, pending()];
const snapLoad = x => Math.floor(x / 5) * 5, input = { sets, sourceIndex: 1, cell, snapLoad };
const before = structuredClone(sets), adjusted = applyLiveAutoregulation(input);
assert.deepEqual(sets, before);
assert.equal(adjusted.decision.action, 'reduce_pending_load');
assert.equal(adjusted.decision.earnedProgression, false);
assert.equal(adjusted.sets[0], sets[0]); assert.equal(adjusted.sets[1], sets[1]);
assert.equal(adjusted.sets[2].weight, '95'); assert.equal(adjusted.sets[2].reps, sets[2].reps);
assert.deepEqual(adjusted.sets[2].target.reps, sets[2].target.reps);
assert.equal(adjusted.sets[2].recoveryLimited, true);
assert.equal(applyLiveAutoregulation({ ...input, sets: adjusted.sets }).sets, adjusted.sets, 'same event never compounds');
for (const source of [0, 1]) assert.deepEqual(rollbackLiveAutoregulation(adjusted.sets, source), sets);
const restored = JSON.parse(JSON.stringify(adjusted.sets));
assert.deepEqual(applyLiveAutoregulation({ ...input, sets: restored }).sets, restored, 'interruption/reload does not compound');
assert.deepEqual(rollbackLiveAutoregulation(restored, 0), sets);
const cleared = adjusted.sets.map((s, i) => i === 0 ? { ...s, actualRIR: null } : s);
assert.equal(applyLiveAutoregulation({ ...input, sets: cleared, sourceIndex: 0 }).sets[2].weight, '100', 'removing evidence rolls back its pending change');
for (const field of [{ valueOwner: 'user' }, { auto: false }, { added: true }, { weight: '103' }, { reps: '9' }, { done: true }]) {
    const protectedRows = sets.map((s, i) => i === 2 ? { ...s, ...field } : s);
    assert.equal(applyLiveAutoregulation({ ...input, sets: protectedRows }).sets, protectedRows);
}
for (const context of [{ interrupted: true }, { prescriptionEdited: true }, { manualOverride: true }])
    assert.equal(applyLiveAutoregulation({ ...input, context }).sets, sets);
for (const c of [{ ...cell, role: 'primary_strength' }, { ...cell, role: 'secondary_strength' }, { ...cell, progressionStyle: 'wave' }, { ...cell, setTargets: [] }])
    assert.equal(applyLiveAutoregulation({ ...input, cell: c }).sets, sets);
for (const mutate of [s => ({ ...s, actualRIR: null }), s => ({ ...s, actualRIR: undefined }), s => ({ ...s, actualRIR: 3 }), s => ({ ...s, reps: '12', actualRIR: 2 }),
    s => ({ ...s, weight: '-100' }), s => ({ ...s, actualRIR: 'bad' }), s => ({ ...s, prescriptionEdited: true })]) {
    const rows = sets.map((s, i) => i < 2 ? mutate(s) : s);
    assert.equal(applyLiveAutoregulation({ ...input, sets: rows }).sets, rows);
}
assert.equal(applyLiveAutoregulation({ ...input, snapLoad: () => 80 }).sets, sets, 'large/coarse steps are refused');
assert.equal(applyLiveAutoregulation({ ...input, snapLoad: () => NaN }).sets, sets);
const ex = EX_BY_ID['bb-bench'], program = { id: 'live-232', custom: true, weeks: 6, config: { unit: 'lb', progression: 'double' },
    days: [{ id: 'd', label: 'Accessory', primaryIndex: -1, exercises: [ex.id] }], overrides: { 'd:0': { sets: 3, reps: '8-12', rir: '1-2', progressionStyle: 'double' } } };
const finished = adjusted.sets.map(s => ({ ...s, done: true, reps: '12', actualRIR: 2 }));
const perf = loggedWorkoutPerformance(program, program.days[0], [{ id: ex.id, slot: 0, note: '', sets: finished }], 1, 'lb', {}, []);
assert.equal(perf[ex.id].sets[2].recoveryLimited, true, 'live limitation survives actual production serialization');
assert.equal(progressionExposureContext(perf[ex.id].sets).badDay, true);
const history = [{ id: 'completed', programId: program.id, dayId: 'd', date: 1, unit: 'lb', perf }];
assert.notEqual(sessionSuggestion(program, program.days[0], 0, null, 'lb', 1, history).action, 'increase_load', 'eased work cannot earn future progression');
console.log('PASS M232: two observed misses, one bounded loadable pending reduction, completed/manual/protocol protection, idempotent resume/undo and no earned progression from eased work.');
