import assert from 'node:assert/strict';
import { evaluateWorkoutProgression } from '../modules/next-engine/performance.js';

const session = { exercises: [{
  exerciseId: 'back_squat', name: 'Back Squat', sets: 3,
  prescription: { reps: [5,8], rir: [2,2] }, progressionStyle: 'double'
}]};
const ctx = { equipmentAvailable: ['barbell','rack'] };
const decide = sets => evaluateWorkoutProgression(session, sets.map((s,i)=>({exerciseId:'back_squat',setIndex:i,...s})), ctx)[0];

const reported = decide([
  {load:200,reps:3,rir:null}, {load:205,reps:3,rir:null},
  {load:210,reps:2,rir:null}, {load:215,reps:2,rir:0}
]);
assert.equal(reported.action, 'decrease_load');
assert.equal(reported.suggestedLoad, 185);
assert.equal(reported.suggestedReps, 5);
assert.ok(reported.suggestedLoad < reported.currentLoad);
assert.match(reported.reason, /below the 5-rep floor/);

const noRir = decide([
  {load:200,reps:3,rir:null}, {load:205,reps:3,rir:null},
  {load:210,reps:2,rir:null}, {load:215,reps:2,rir:null}
]);
assert.equal(noRir.action, 'decrease_load');
assert.equal(noRir.suggestedLoad, 185);

const easyEarlyStop = decide([
  {load:185,reps:4,rir:5}, {load:185,reps:4,rir:5}, {load:185,reps:4,rir:5}
]);
assert.equal(easyEarlyStop.action, 'hold');
assert.equal(easyEarlyStop.suggestedLoad, 185);

const owned = decide([
  {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}
]);
assert.equal(owned.action, 'increase_load');
assert.equal(owned.suggestedLoad, 190);

const inRange = decide([
  {load:185,reps:5,rir:2}, {load:185,reps:5,rir:2}, {load:185,reps:5,rir:2}
]);
assert.equal(inRange.action, 'add_reps');
assert.equal(inRange.suggestedLoad, 185);

console.log('PASS progression safety: reported rep-floor bug -> 185 lb; normal double progression preserved.');
