import assert from 'node:assert/strict';
import { represcribeForWeek } from '../modules/next-engine/workout-history-adapter.js';

// Regression: Hack Squat previously logged as 50 lb for all working sets must
// not acquire an artificial 49.5 lb working weight solely because a new week's
// rep/RIR prescription happens to land just below 50 on a 0.5 lb grid.
const exerciseId = 'hack_squat';
const logging = { equipment: { available: ['machine'], loading: {
    unit: 'lb', exerciseOverrides: { [exerciseId]: { minimum: 0, increment: 0.5 } }
} } };
const last = { weight: 50, reps: 9, sets: [
    { w: 50, r: 9, rir: 3, rirReported: true, done: true },
    { w: 50, r: 9, rir: 3, rirReported: true, done: true },
    { w: 50, r: 9, rir: 3, rirReported: true, done: true }
] };
const nextWeek = { reps: '8-12', rir: '2.25-2.25' };
const previousWeek = { reps: '8-10', rir: '3-3' };
const suggest = (old = last, current = nextWeek, request = logging) =>
    represcribeForWeek(exerciseId, current, previousWeek, old, request, 'lb', 'lb');

const held = suggest();
assert.equal(held.weight, 50, 'ignore sub-pound estimated regression after fully completed 50 lb work');
assert.equal(held.target, 10);
assert.match(held.reason, /keep the last successfully completed load/);

const meaningful = suggest(last, { ...nextWeek, rir: '3-3' });
assert.equal(meaningful.weight, 48.5, 'genuine larger re-prescriptions still lower the load');

const incomplete = suggest({ ...last, sets: [last.sets[0], { ...last.sets[1], r: 7 }] });
assert.equal(incomplete.weight, 49.5, 'below-floor work never receives a hold exemption');

const mixed = suggest({ ...last, sets: [last.sets[0], { ...last.sets[1], w: 45 }] });
assert.equal(mixed.weight, 49.5, 'mixed loads are not automatically treated as a proven straight-set hold');

const coarse = suggest(last, nextWeek, { equipment: { available: ['machine'], loading: {
    unit: 'lb', exerciseOverrides: { [exerciseId]: { minimum: 0, increment: 5 } }
} } });
assert.equal(coarse.weight, 45, 'a meaningful whole-notch reduction is not converted into a hold');

// The hold is expressed in the prescription's display unit: real 0.25 kg
// microloading is valid, but the absolute 0.5 lb bound must not bypass the
// stricter two-percent cap for lighter exercises.
const kgLog = { equipment: { available: ['machine'], loading: {
    unit: 'kg', exerciseOverrides: { [exerciseId]: { minimum: 0, increment: 0.25 } }
} } };
const kgSets = { ...last, weight: 25, sets: last.sets.map(set => ({ ...set, w: 25 })) };
const kgHold = represcribeForWeek(exerciseId, nextWeek, previousWeek, kgSets, kgLog, 'kg', 'kg');
assert.equal(kgHold.weight, 25, 'a supported 0.25 kg weekly-grid artifact retains completed 25 kg work');
assert.match(kgHold.reason, /keep the last successfully completed load/);

const lightSets = { ...last, weight: 10, sets: last.sets.map(set => ({ ...set, w: 10 })) };
const lightRecalibration = suggest(lightSets);
assert.equal(lightRecalibration.weight, 9.5, 'a 0.5 lb decline is meaningful at a 10 lb working weight');
assert.doesNotMatch(lightRecalibration.reason, /keep the last successfully completed load/);

const unfinished = suggest({ ...last, sets: last.sets.map(set => ({ ...set, done: false })) });
assert.doesNotMatch(unfinished.reason, /keep the last successfully completed load/,
    'a fallback summary without completed set evidence does not earn a hold');

console.log('PASS B936: prevent near-grid phantom reductions while preserving unit boundaries, incomplete evidence and meaningful decreases.');
