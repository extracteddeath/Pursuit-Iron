import assert from 'node:assert/strict';
import { defaultLoadingInventoryFor, loadingInventoryInUnit, nextAvailableLoad } from '../modules/next-engine/loading.js';
import { evaluateWorkoutProgression } from '../modules/next-engine/performance.js';

// Reproduces the saved-unit-switch path from the user's machine workout.
const kg = defaultLoadingInventoryFor('kg');
const lb = loadingInventoryInUnit(kg, 'lb');
assert.equal(lb.machine.increment, 5, 'serialized factory machine stock has native lb notches');
assert.equal(lb.cable.increment, 5);
assert.equal(lb.smith.increment, 5);
assert.equal(nextAvailableLoad('machine_press', 155, lb, ['machine']), 160,
    '155 lb must not advance by a converted 5.51 lb pin increment');

const exercise = { exerciseId: 'machine_press', name: 'Machine Chest Press', sets: 4,
    progressionStyle: 'dynamic', prescription: { reps: [8, 12], rir: [2, 2] } };
const performed = [12, 12, 8, 8].map((reps, setIndex) => ({ exerciseId: 'machine_press', setIndex,
    load: 155, reps, rir: 2 }));
const decision = evaluateWorkoutProgression({ exercises: [exercise] }, performed,
    { loadingInventory: lb, equipmentAvailable: ['machine'] })[0];
assert.deepEqual(decision.setTargets.map(({ weight, reps }) => [weight, reps]),
    [[160, 8], [160, 8], [155, 9], [155, 9]],
    'set-specific advancement honors achievable native stock and preserves later sets');

// Already-saved versions of the old conversion are repaired WITHOUT requiring another unit switch.
const oldLbProfile = defaultLoadingInventoryFor('lb');
oldLbProfile.machine = { minimum: 5.51, increment: 5.51, maximum: 551.16 };
oldLbProfile.cable = { minimum: 5.51, increment: 5.51, maximum: 330.69 };
const repaired = loadingInventoryInUnit(oldLbProfile, 'lb');
assert.equal(repaired.machine.increment, 5);
assert.equal(repaired.cable.increment, 5);
assert.equal(nextAvailableLoad('machine_press', 155, repaired, ['machine']), 160);
// An actual custom 5.51 lb machine with a different maximum is not a factory conversion.
const trueCustom = structuredClone(oldLbProfile);
trueCustom.machine.maximum = 500;
assert.equal(loadingInventoryInUnit(trueCustom, 'lb').machine.increment, 5.51);

// Changing a different implement cannot force the stock machine back to 5.51 lb steps.
const partial = structuredClone(kg);
partial.dumbbells.availablePerHand = [5, 12.5, 22.5];
const partialLb = loadingInventoryInUnit(partial, 'lb');
assert.equal(partialLb.machine.increment, 5);
assert.deepEqual(partialLb.dumbbells.availablePerHand, [11.02, 27.56, 49.6]);

// Explicitly customized equipment (including valid fractional increments) remains authoritative.
const custom = structuredClone(kg);
custom.machine.increment = 3;
custom.exerciseOverrides.machine_press = { increment: 1.25, minimum: 0 };
const customLb = loadingInventoryInUnit(custom, 'lb');
assert.equal(customLb.machine.increment, 6.61);
assert.equal(customLb.exerciseOverrides.machine_press.increment, 2.76);
assert.equal(kg.machine.increment, 2.5, 'conversion never mutates the source');
const back = loadingInventoryInUnit(defaultLoadingInventoryFor('lb'), 'kg');
assert.equal(back.machine.increment, 2.5);
assert.equal(back.barbell.barWeight, 20);
console.log('PASS B883 native equipment steps after unit switch and per-set dynamic progression');
