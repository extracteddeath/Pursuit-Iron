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

// M338: holding a mixed-load exposure cannot promote its heaviest set to every row.
const incline = {exercises:[{exerciseId:'low_inc_bb_bench',name:'Low-Incline Barbell Press',
  sets:4,prescription:{reps:[6,8],rir:[2,2]},progressionStyle:'double'}]};
const ramp = [195,200,205,210].map((load,setIndex)=>({
  exerciseId:'low_inc_bb_bench',setIndex,load,reps:[10,8,6,5][setIndex],rir:null}));
const originalRamp=structuredClone(ramp);
for(const style of ['double','linear','wave','e1rm','ladder']){
  const result=evaluateWorkoutProgression({exercises:[{...incline.exercises[0],progressionStyle:style}]},ramp,ctx)[0];
  assert.equal(result.action,'hold',style+' does not earn a heavier exposure');
  assert.deepEqual(result.setTargets.map(s=>[s.weight,s.reps]),[[195,8],[200,8],[205,6],[210,6]],
    style+' preserves each observed load while retrying inside the range');
}
const productiveRamp=ramp.map((s,i)=>({...s,reps:[7,7,6,6][i]}));
const productive=evaluateWorkoutProgression(incline,productiveRamp,ctx)[0];
assert.equal(productive.action,'add_reps');
assert.deepEqual(productive.setTargets.map(s=>[s.weight,s.reps]),[[195,8],[200,8],[205,7],[210,7]]);
const limited=evaluateWorkoutProgression(incline,ramp,{...ctx,badDay:true})[0];
assert.deepEqual(limited.setTargets.map(s=>s.weight),[195,200,205,210]);
assert.deepEqual(ramp,originalRamp,'prescription decisions never rewrite historical sets');

console.log('PASS progression safety: reported rep-floor bug -> 185 lb; normal double progression preserved.');
