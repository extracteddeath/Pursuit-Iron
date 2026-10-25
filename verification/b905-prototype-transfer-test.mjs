import assert from 'node:assert/strict';
import { reviewProgramChanges, withProgramRevision, programReviewKey } from '../modules/program-review.js';
import { lighterPendingWorkout, workoutPlateau } from '../modules/workout-context.js';
import { userOwnsRuntimeSet, EX_BY_ID, loadableAtOrBelow, computeCell } from '../modules/engine-api.js';
const before={id:'review-905',name:'Review',custom:true,weeks:4,seed:42,config:{unit:'kg',goal:'both',experience:'intermediate',progression:'manual',weeks:4,session:'s60',deload:false},
 days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','bb-row'],note:'Keep this cue'}],schedule:{1:'upper'},
 overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2',rest:120},'upper:1':{sets:2,reps:'8-12',rir:'2',rest:90}},progStyle:{'bb-row':'dynamic'}};
const next=structuredClone(before);next.overrides['upper:1'].sets=3;next.overrides['upper:1'].rir='1';next.schedule={2:'upper'};
const original=programReviewKey(before),review=reviewProgramChanges(before,next);
assert.match(review.days[0].rows[0].after.join(' '),/3 × 8-12 · 1 RIR/);
assert.match(review.config.find(x=>x.label==='Schedule').before,/Mon/);assert.match(review.config.find(x=>x.label==='Schedule').after,/Tue/);
assert.equal(programReviewKey(before),original,'review is read-only');
const committed=withProgramRevision(before,next,'Before repair',1234);
assert.deepEqual(committed.layoutHistory[0].program,before,'exact authored settings and overrides survive');
const restored=withProgramRevision(committed,JSON.parse(JSON.stringify(committed)).layoutHistory[0].program,'Before restore',1235);
for(const key of Object.keys(before))assert.deepEqual(restored[key],before[key]);
assert.deepEqual(computeCell(restored,restored.days[0],'bb-row',1,4),computeCell(before,before.days[0],'bb-row',1,4));
let history=committed;for(let n=0;n<8;n++)history=withProgramRevision(history,{...history,seed:50+n},'Earlier',2000+n);
assert.equal(history.layoutHistory.length,3);assert.ok(history.layoutHistory.every(x=>!x.program.layoutHistory),'snapshots do not recurse');
assert.throws(()=>withProgramRevision(before,{...next,id:'another'},'bad'));
const row={id:'bb-bench',sets:[{weight:'80',reps:'8',done:true,valueOwner:'user'},
 {weight:'80',reps:'8',valueOwner:'engine',target:{rir:'2',w:'80'}},
 {weight:'82.5',reps:'7',valueOwner:'user'}, {weight:'40',reps:'5',warm:true,valueOwner:'engine'},
 {weight:'50',reps:'8',sub:true,kind:'drop'}, {weight:'45',reps:'6',sub:true,kind:'drop',done:true}]};
const lighter=lighterPendingWorkout(row,w=>loadableAtOrBelow(EX_BY_ID['bb-bench'],w,'kg'),userOwnsRuntimeSet);
assert.equal(lighter.sets[1].weight,'70');assert.equal(lighter.sets[1].valueOwner,'user');assert.equal(lighter.sets[1].reps,'8');assert.equal(lighter.sets[1].target.rir,'2');
assert.deepEqual(lighter.sets[0],row.sets[0]);assert.deepEqual(lighter.sets[2],row.sets[2]);assert.equal(lighter.sets[3].weight,'35','pending automatic warmups follow the lighter load');
assert.ok(lighter.sets.some(x=>x.sub&&x.done));assert.ok(!lighter.sets.some(x=>x.sub&&!x.done));
assert.equal(lighterPendingWorkout({...row,injuryAdjusted:'chest'},w=>w*.9,userOwnsRuntimeSet).sets,row.sets);
const logs=Array.from({length:5},(_,i)=>({id:'log-'+i,date:Date.now()-i*604800000,programId:before.id,dayId:'upper',dayLabel:'Upper',unit:'kg',
 perf:{'bb-bench':{weight:80,reps:8,sets:Array.from({length:3},()=>({w:80,r:8}))}}}));
assert.ok(workoutPlateau(before,before.days[0],'bb-bench',logs));
assert.equal(workoutPlateau(before,before.days[0],'bb-bench',logs.map(h=>({...h,programId:'different'}))),null);
assert.equal(workoutPlateau({...before,days:[...before.days,{id:'lower',label:'Lower',exercises:['bb-bench']}]},before.days[0],'bb-bench',logs.map(h=>({...h,dayId:'lower',dayLabel:'Lower'}))),null);
assert.equal(workoutPlateau(before,before.days[0],'bb-bench',[]),null);
console.log('PASS B905: read-only diffs, exact restore after serialization, bounded nonrecursive revisions, actual week targets, protected lighter-session edits, program/day-scoped plateau evidence.');
