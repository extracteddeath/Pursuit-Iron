import assert from 'node:assert/strict';
import { sessionSuggestion, computeCell } from '../modules/App.js';
const program={id:'custom1',custom:true,config:{unit:'lb'},overrides:{'d1:0':{sets:3,reps:'5-8',rir:'2',progressionStyle:'double'}},days:[{id:'d1',exercises:['back-squat']}]};
const history=[{programId:'custom1',dayId:'d1',date:Date.now(),unit:'lb',perf:{'back-squat':{weight:215,reps:2,sets:[
 {w:200,r:3,rir:null,done:true},{w:205,r:3,rir:null,done:true},{w:210,r:2,rir:null,done:true},{w:215,r:2,rir:0,done:true}
]}}}];
const s=sessionSuggestion(program,program.days[0],0,null,'lb',1,history);
assert.ok(s);
assert.equal(s.action,'decrease_load');
assert.equal(s.dir,'down');
assert.equal(s.weight,185);
assert.equal(s.target,5);
console.log('PASS custom double progression safety: rep-floor miss -> 185 lb/down.');

// Regression from the screenshot: custom-plan ranges persisted as arrays must not collapse [10,15] to 10.
// Ten reps across all prescribed sets is the BASELINE of a 10-15 range; load only increases once every
// prescribed set reaches 15 at the planned effort.
const arrayProgram={id:'custom-array',custom:true,config:{unit:'lb'},overrides:{'d2:0':{sets:3,reps:[10,15],rir:[2,2],progressionStyle:'double'}},days:[{id:'d2',primaryIndex:0,exercises:['back-squat']}]};
const baselineHistory=[{programId:'custom-array',dayId:'d2',date:Date.now(),unit:'lb',perf:{'back-squat':{weight:100,reps:10,sets:[
 {w:100,r:10,rir:2,done:true},{w:100,r:10,rir:2,done:true},{w:100,r:10,rir:2,done:true}
]}}}];
const arrayCell=computeCell(arrayProgram,arrayProgram.days[0],'back-squat',0,1);
assert.equal(arrayCell.reps,'10-15');
assert.equal(arrayCell.range,'10-15');
assert.equal(arrayCell.rir,'2');
const baseline=sessionSuggestion(arrayProgram,arrayProgram.days[0],0,null,'lb',1,baselineHistory);
assert.ok(baseline);
assert.equal(baseline.action,'add_reps');
assert.equal(baseline.dir,'hold');
assert.equal(baseline.weight,100);
assert.equal(baseline.target,15);
assert.match(baseline.reason,/toward 15 reps/);
assert.doesNotMatch(baseline.reason,/reached 10 reps/);

const topHistory=[{programId:'custom-array',dayId:'d2',date:Date.now()+1,unit:'lb',perf:{'back-squat':{weight:100,reps:15,sets:[
 {w:100,r:15,rir:2,done:true},{w:100,r:15,rir:2,done:true},{w:100,r:15,rir:2,done:true}
]}}}];
const top=sessionSuggestion(arrayProgram,arrayProgram.days[0],0,null,'lb',1,topHistory);
assert.ok(top);
assert.equal(top.action,'increase_load');
assert.equal(top.dir,'up');
assert.equal(top.weight,105);
assert.equal(top.target,10);
assert.match(top.reason,/reached 15 reps/);
console.log('PASS custom 10-15 array range: 10s hold/build; only 15s across all sets earn a load increase.');


// M166 regression: array-shaped custom sets can intentionally encode one set count per week.
const setScheduleProgram={id:'custom-set-schedule',custom:true,config:{unit:'lb',weeks:4},overrides:{'d3:0':{sets:[5,3,3,4],reps:'8-12',rir:'2'}},days:[{id:'d3',primaryIndex:0,exercises:['back-squat']}]};
assert.deepEqual([1,2,3,4].map(week=>computeCell(setScheduleProgram,setScheduleProgram.days[0],'back-squat',0,week).sets),[5,3,3,4]);
console.log('PASS custom set schedule: computeCell preserves 5/3/3/4 across weeks 1–4.');
