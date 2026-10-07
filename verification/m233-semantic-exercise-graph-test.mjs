import assert from 'node:assert/strict';
import { createSemanticExerciseGraph, evaluateExerciseTransfer, validateCustomExerciseSemantics, transferExerciseStartingReference } from '../modules/next-engine/semantic-exercise-graph.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { semanticStartingReferenceForShell } from '../modules/next-engine/workout-history-adapter.js';
const graph = createSemanticExerciseGraph(), bench = EXERCISE_MAP.get('barbell_bench'), paused = EXERCISE_MAP.get('paused_bench');
assert.ok(graph.size > 100);
assert.equal(graph.node('unknown'), null);
assert.ok(Object.isFrozen(graph.node(bench.id)) && Object.isFrozen(graph.relations(bench.id)));
assert.equal(graph.relations(bench.id), graph.relations(bench.id), 'lazy immutable edge cache');
const request = normalizeRequest({ athlete: { experience: 'intermediate' }, goal: { type: 'strength' }, schedule: { days: ['monday', 'thursday'].map(day => ({ day, maxMinutes: 60 })) },
    equipment: { available: ['barbell', 'rack', 'bench', 'cable', 'machine'], loading: { unit: 'lb' } } });
const context = { request, day: 'monday', role: 'primary_strength' }, transfer = graph.transfer(bench.id, paused.id, context);
assert.equal(transfer.allowed, true); assert.equal(transfer.loadReferenceAllowed, true); assert.equal(transfer.referenceFraction, .8);
assert.equal(transfer.earnedProgression, false); assert.equal(transfer.referenceOnly, true);
const sets = [{ load: 200, reps: 8, rir: 2, done: true }, { load: 200, reps: 8, rir: 2, done: true }];
const reference = transferExerciseStartingReference({ graph, fromId: bench.id, toId: paused.id, sets, sourceUnit: 'lb', targetUnit: 'lb', context, snapLoad: n => Math.floor(n / 5) * 5 });
assert.equal(reference.load, 160); assert.equal(reference.earnedProgression, false);
for (const ctx of [{ ...context, interrupted: true }, { ...context, recoveryLimited: true }, { ...context, prescriptionEdited: true }])
    assert.equal(transferExerciseStartingReference({ graph, fromId: bench.id, toId: paused.id, sets, sourceUnit: 'lb', targetUnit: 'lb', context: ctx, snapLoad: x => x }).load, null);
assert.equal(graph.transfer(bench.id, paused.id, { ...context, request: { ...request, preferences: { ...request.preferences, avoidedExercises: [paused.id] } } }).allowed, false);
assert.equal(graph.transfer(bench.id, paused.id, { ...context, request: { ...request, schedule: { days: [{ day: 'monday', maxMinutes: 60, equipmentOverride: ['cable'] }] } } }).allowed, false);
for (const [a, b] of [['chest_supported_row', 'neutral_pulldown'], ['rdl', 'seated_leg_curl'], ['cable_curl', 'hammer'], ['standing_calf', 'seated-calf']]) {
    assert.ok(EXERCISE_MAP.has(a) && EXERCISE_MAP.has(b)); assert.equal(evaluateExerciseTransfer(EXERCISE_MAP.get(a), EXERCISE_MAP.get(b)).allowed, false, 'complementary functions are distinct');
}
assert.equal(graph.transfer('machine_press', 'incline_smith').loadReferenceAllowed, false, 'machine numbers have no universal load equivalence');
assert.equal(graph.transfer('cable_fly', bench.id, context).allowed, false, 'isolation stimulus cannot replace a strength anchor');
const metadata = { schemaVersion: 1, movementSubslot: graph.node(bench.id).movementSubslot, jointActions: [...graph.node(bench.id).jointActions], functionalBiases: [...graph.node(bench.id).functionalBiases],
    loadConvention: 'barbell_total', rangeOfMotion: 'full', laterality: 'bilateral' };
const custom = { ...structuredClone(bench), id: 'custom-bench', name: 'Named Custom Bench', metadataConfidence: 'high', semanticMetadata: metadata };
const customGraph = createSemanticExerciseGraph([custom]);
assert.equal(customGraph.transfer(custom.id, bench.id, context).allowed, true);
custom.muscles.chest.credit = 0;
assert.equal(customGraph.transfer(custom.id, bench.id, context).allowed, true, 'graph snapshots cannot drift with caller mutation');
custom.muscles.chest.credit = 1;
assert.equal(createSemanticExerciseGraph([{ ...custom, semanticMetadata: { ...metadata, jointActions: ['hip_extension'] } }]).transfer(custom.id, bench.id, context).allowed, false);
assert.equal(createSemanticExerciseGraph([{ ...custom, semanticMetadata: undefined }]).transfer(custom.id, bench.id, context).allowed, false);
assert.equal(createSemanticExerciseGraph([{ ...custom, metadataConfidence: 'low' }]).transfer(custom.id, bench.id, context).allowed, false);
assert.deepEqual(normalizeRequest({ ...request, customExercises: [custom] }).customExercises[0].semanticMetadata, validateCustomExerciseSemantics(metadata));
assert.throws(() => normalizeRequest({ ...request, customExercises: [{ ...custom, semanticMetadata: { ...metadata, schemaVersion: 2 } }] }), /Custom semantics/);
assert.throws(() => validateCustomExerciseSemantics({ ...metadata, loadConvention: 'whatever' }), /Custom semantics/);
const day = { id: 'd' }, shell = { id: 'scope', config: { unit: 'lb' }, days: [day], nextEngine: { request, program: { sessions: [{ day: 'monday' }] } } };
const asOf = Date.parse('2026-10-07T12:00:00Z');
const history = [{ id: 'h', programId: shell.id, dayId: 'd', date: asOf - 7 * 86400000, unit: 'lb', perf: { previous: { prescription: { schemaVersion: 1, exerciseId: bench.id },
    sets: sets.map(s => ({ w: s.load, r: s.reps, rir: s.rir, rirReported: true })) } } }];
const advice = semanticStartingReferenceForShell(shell, history, day, paused.id, { reps: '6-10', role: 'primary_strength' }, { asOf });
assert.equal(advice.weight, 160); assert.equal(advice.referenceOnly, true); assert.equal(advice.action, 'initial');
assert.equal(semanticStartingReferenceForShell(shell, [{ ...history[0], interrupted: true, perf: { previous: { ...history[0].perf.previous, interrupted: false } } }], day, paused.id, { role: 'primary_strength' }, { asOf }), null, 'narrow false cannot clear enclosing limitation');
assert.equal(semanticStartingReferenceForShell(shell, [{ ...history[0], programId: 'foreign' }], day, paused.id, { role: 'primary_strength' }, { asOf }), null);
assert.equal(semanticStartingReferenceForShell(shell, [{ ...history[0], dayId: 'other' }], day, paused.id, { role: 'primary_strength' }, { asOf }), null);
console.log('PASS M233: canonical immutable semantic graph, explicit custom metadata, complementary-function/strength/equipment/avoidance safeguards, conservative unit-aware reference transfer and no borrowed progression.');
