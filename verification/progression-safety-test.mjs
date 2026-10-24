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
const incline = {exercises:[{exerciseId:'low-inc-bb-bench',name:'Low-Incline Barbell Press',
  sets:4,prescription:{reps:[6,8],rir:[2,2]},progressionStyle:'double'}]};
const ramp = [195,200,205,210].map((load,setIndex)=>({
  exerciseId:'low-inc-bb-bench',setIndex,load,reps:[10,8,6,5][setIndex],rir:null}));
const originalRamp=structuredClone(ramp);
for(const style of ['dynamic','linear','wave','e1rm','ladder']){
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

// M339: classic double may deliberately normalize to a supported straight-set load.
const calibrated=evaluateWorkoutProgression(incline,ramp,ctx)[0];
assert.equal(calibrated.reasonCode,'straight_set_calibration');
assert.equal(calibrated.action,'decrease_load');
assert.equal(calibrated.confidence,'low','Unreported effort is not confident capacity evidence');
assert.deepEqual(calibrated.setTargets.map(s=>[s.weight,s.reps]),Array.from({length:4},()=>[190,6]));
assert.match(calibrated.reason,/later sets under fatigue/);
const observed=ramp.map(s=>({...s,rir:2}));
const normalized=evaluateWorkoutProgression(incline,observed,ctx)[0];
assert.equal(normalized.suggestedLoad,200,'Reported reserve supports a different rounded common load');
const sparseInventory={...ctx,loadingInventory:{unit:'lb',exerciseOverrides:{'low-inc-bb-bench':
  {availableLoads:[185,195,205,215]}}}};
assert.equal(evaluateWorkoutProgression(incline,ramp,sparseInventory)[0].suggestedLoad,185,
  'The common load rounds down to a weight the selected equipment actually supports');
const noisy=ramp.map((s,i)=>({...s,reps:i===0?16:s.reps}));
assert.notEqual(evaluateWorkoutProgression(incline,noisy,ctx)[0].reasonCode,'straight_set_calibration',
  'An excluded high-rep estimate cannot establish support for all set positions');
const healthy=ramp.map((s,i)=>({...s,reps:[10,8,6,6][i],rir:2}));
const fatigued=healthy.map((s,i)=>({...s,rir:i===3?0:2}));
assert.ok(evaluateWorkoutProgression(incline,fatigued,ctx)[0].suggestedLoad
  < evaluateWorkoutProgression(incline,healthy,ctx)[0].suggestedLoad,'Later-set fatigue constrains the common load');
const easy=ramp.map(s=>({...s,rir:5}));
assert.notEqual(evaluateWorkoutProgression(incline,easy,ctx)[0].reasonCode,'straight_set_calibration',
  'Explicitly easy early stopping cannot trigger this correction');
for(const flags of [{badDay:true},{prescriptionEdited:true},{percentageLoading:true}])
  assert.notEqual(evaluateWorkoutProgression(incline,ramp,{...ctx,...flags})[0].reasonCode,'straight_set_calibration');
assert.notEqual(evaluateWorkoutProgression(incline,ramp.slice(0,3),ctx)[0].reasonCode,'straight_set_calibration');
const repeated=ramp.map(s=>({...s,load:190,reps:6,rir:2}));
assert.equal(evaluateWorkoutProgression(incline,repeated,ctx)[0].action,'add_reps',
  'A successful normalized exposure resumes ordinary double progression');
assert.equal(evaluateWorkoutProgression(incline,repeated.map(s=>({...s,reps:8})),ctx)[0].suggestedLoad,195,
  'All sets at the ceiling still earn the next load');
assert.deepEqual(ramp,originalRamp);

console.log('PASS progression safety: reported rep-floor bug -> 185 lb; normal double progression preserved.');
