import assert from 'node:assert/strict';
import { evaluateWorkoutProgression } from '../modules/next-engine/performance.js';

const ctx = { equipmentAvailable: ['barbell','rack'] };
function exercise(style = 'double', sets = 3) {
  return {
    exerciseId: 'back_squat', name: 'Back Squat', role: 'primary_strength', sets,
    prescription: { reps: [5,8], rir: [2,2] }, progressionStyle: style
  };
}
function decide({ style='double', prescribedSets=3, sets, context={} }) {
  const session = { exercises: [exercise(style, prescribedSets)] };
  return evaluateWorkoutProgression(session, sets.map((s,i)=>({ exerciseId:'back_squat', setIndex:i, ...s })), { ...ctx, ...context })[0];
}

// Reported production failure: an accidental/heavy 3/3/2/2 ramp against 5-8 must reset, never turn 215 into straight doubles.
const reported = decide({ sets:[
  {load:200,reps:3,rir:null}, {load:205,reps:3,rir:null},
  {load:210,reps:2,rir:null}, {load:215,reps:2,rir:0}
]});
assert.equal(reported.outcome, 'failure');
assert.equal(reported.action, 'decrease_load');
assert.equal(reported.suggestedLoad, 185);
assert.equal(reported.suggestedReps, 5);
assert.ok(reported.suggestedLoad < reported.currentLoad);

// 75% completion used to be enough to reach the generic all-at-top branch. Missing sets are now a hard progression block.
const partialTop = decide({ prescribedSets:4, sets:[
  {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}
]});
assert.equal(partialTop.outcome, 'incomplete');
assert.equal(partialTop.reasonCode, 'incomplete_prescription');
assert.notEqual(partialTop.action, 'increase_load');
assert.equal(partialTop.suggestedLoad, 185);

// Every progression family shares the M183 load-step gate: complete + top of range + acceptable effort.
for (const style of ['double','dynamic','ladder','linear','wave','e1rm']) {
  const merelyInRange = decide({ style, sets:[
    {load:185,reps:6,rir:2}, {load:185,reps:6,rir:2}, {load:185,reps:6,rir:2}
  ]});
  assert.equal(merelyInRange.outcome, 'productive', `${style} should remain productive in-range`);
  assert.notEqual(merelyInRange.action, 'increase_load', `${style} must not increase from merely in-range work`);
  assert.equal(merelyInRange.suggestedLoad, 185);

  const owned = decide({ style, sets:[
    {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}
  ]});
  assert.equal(owned.outcome, 'success', `${style} top-range exposure should succeed`);
  assert.equal(owned.action, 'increase_load', `${style} should increase only after owning the range`);
  assert.equal(owned.suggestedLoad, 190);
}

// The old linear/e1RM branch referenced an undefined clearOvershoot variable. These cases prove both paths return safely.
for (const style of ['linear','e1rm']) {
  const controlled = decide({ style, sets:[
    {load:185,reps:7,rir:2}, {load:185,reps:7,rir:2}, {load:185,reps:7,rir:2}
  ]});
  assert.equal(controlled.action, 'add_reps');
}

// Edits/substitutions are not comparable evidence for the original prescription.
const edited = decide({ sets:[
  {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}
], context:{ prescriptionEdited:true } });
assert.equal(edited.outcome, 'non_comparable');
assert.equal(edited.action, 'review');
assert.equal(edited.suggestedLoad, 185);

// A declared bad/readiness-limited day should not permanently ratchet load up or down from one anomalous exposure.
const badDay = decide({ sets:[
  {load:185,reps:3,rir:0}, {load:185,reps:3,rir:0}, {load:185,reps:2,rir:0}
], context:{ readiness:{ status:'low' } } });
assert.equal(badDay.outcome, 'context_limited');
assert.equal(badDay.action, 'hold');
assert.equal(badDay.suggestedLoad, 185);

// Interrupted work is classified separately from performance failure.
const interrupted = decide({ prescribedSets:4, sets:[
  {load:185,reps:8,rir:2}, {load:185,reps:8,rir:2}
], context:{ sessionInterrupted:true } });
assert.equal(interrupted.outcome, 'interrupted');
assert.equal(interrupted.action, 'review');

console.log('PASS M183 progression safeguards: failure recalibration, completion gate, style gate, edit/readiness/interruption classification.');
