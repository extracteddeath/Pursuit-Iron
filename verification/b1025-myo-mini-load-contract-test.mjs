import assert from 'node:assert/strict';
import { EX_BY_ID, growMyoSets, syncSubSets } from '../modules/engine-api.js';

// Regression: editing only a myo mini's reps used to mark its entire row
// user-owned. Subsequent activation-load changes left stale 30/25 lb
// minis beside a 47.5 lb activation set, visually acting like drops.
const ex = EX_BY_ID['lat-raise'];
assert.ok(ex, 'test uses a real loadable exercise');
const activation = { weight: '47.5', reps: '13', done: true, warm: false };
const mini = (weight, targetWeight = weight) => ({
    weight, reps: '5', warm: false, sub: true, kind: 'myo',
    myo: true, done: false, auto: false, valueOwner: 'user',
    target: { w: targetWeight, reps: '3-5', myo: true }
});

const stale = [activation, mini('47.5'), mini('30'), mini('25')];
const fixed = syncSubSets(stale, ex, 'lb');
assert.deepEqual(fixed.slice(1).map(s => s.weight), ['47.5', '47.5', '47.5'],
    'rep-only user ownership may not turn myo-reps into descending drop loads');
assert.deepEqual(fixed.slice(1).map(s => s.target.w), ['47.5', '47.5', '47.5'],
    'displayed targets and editable weights must agree');
assert.deepEqual(fixed.slice(1).map(s => s.reps), ['5', '5', '5'],
    'repair must not rewrite the athlete\'s reps');
assert.equal(syncSubSets(fixed, ex, 'lb'), fixed,
    'a synchronized session must remain referentially stable on re-renders');
assert.deepEqual(stale.slice(1).map(s => s.weight), ['47.5', '30', '25'],
    'repair must be immutable');

// A user deliberately changing the mini's LOAD is not a rep-only edit.
// Explicit load choices and logged facts always beat generated targets.
const manual = mini('30', '47.5');
const logged = { ...mini('25'), done: true };
const protectedSets = syncSubSets([activation, manual, logged, mini('25')], ex, 'lb');
assert.equal(protectedSets[1], manual, 'manual load stays untouched');
assert.equal(protectedSets[2], logged, 'logged actual load stays untouched');
assert.equal(protectedSets[3].weight, '47.5', 'remaining automatic mini follows activation');
const sameValueManual = { ...mini('47.5'), weightOwner: 'user' };
const repOnly = mini('47.5');
const adjusted = syncSubSets([{ ...activation, weight: '50' }, sameValueManual, repOnly], ex, 'lb');
assert.equal(adjusted[1], sameValueManual,
    'explicit weight edits remain owned even when they originally matched the target');
assert.equal(adjusted[2].weight, '50',
    'a reps-only edit must continue following the activation load');

// The common sequence starts with unchanged minis; editing the activation
// set later must carry every unlogged mini even when reps were manually set.
const later = syncSubSets([
    { ...activation, weight: '50' },
    mini('47.5'), mini('47.5'), mini('47.5')
], ex, 'lb');
assert.deepEqual(later.slice(1).map(s => s.weight), ['50', '50', '50']);

// Growth is still 3-5 minis, inherits activation load and uses the 3-rep
// floor. It is not a new independently progressing/drop-loaded set.
const completedThird = { ...fixed[3], done: true, reps: '5' };
const extended = syncSubSets(growMyoSets([...fixed.slice(0, 3), completedThird]), ex, 'lb');
assert.equal(extended.length, 5, 'successful third mini may add a fourth');
assert.equal(extended[4].weight, '47.5', 'grown mini uses activation weight');
assert.equal(growMyoSets([...fixed.slice(0, 3), { ...completedThird, reps: '2' }]).length, 4,
    'failed mini stops growth');

// Drop sets remain separate, preserving their explicit fractions.
const drops = syncSubSets([activation,
    { weight: '0', sub: true, kind: 'drop', dropF: 0.8, auto: true, target: { w: '0' } },
    { weight: '0', sub: true, kind: 'drop', dropF: 0.6, auto: true, target: { w: '0' } }
], ex, 'lb');
assert.ok(Number(drops[1].weight) < 47.5 && Number(drops[1].weight) > Number(drops[2].weight),
    'only drop sets should reduce load across extension rows');
console.log('PASS B1025: myo activation-load invariant, rep-only ownership, manual/logged preservation, growth, and drops');
