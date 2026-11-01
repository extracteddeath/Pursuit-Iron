import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { parseStoredData,loadableAtOrBelow,EX_BY_ID } from '../../modules/engine-api.js';

const profile={name:'Native deload',goal:'both',experience:'intermediate',unit:'kg',split:'full_body',
  days:3,session:'s60',equipment:nativeProgramOptions().equipment,allowBodyweight:true,allowSupersets:true,barbellCap:3};
const created=prepareNativeProgram(JSON.stringify({creationId:'create-deload',programId:'program-deload',
  createdAt:1000,seed:223,profile})).snapshot;
// Retained observations from a completed first week make the next issued
// loads measurable; a blank first-ever target is not evidence of a reduction.
const withHistory=JSON.parse(created),saved=withHistory.saved[0];
withHistory.history=saved.days.map((day,index)=>({id:`prior-${index}`,programId:saved.id,
  dayId:day.id,dayLabel:day.label,date:1100+index,updatedAt:1100+index,weekIndex:1,unit:'kg',
  perf:Object.fromEntries(day.exercises.map(id=>[id,{weight:100,reps:6,
    sets:[{w:100,r:6,rir:2,rirReported:true,workingSetIndex:0}]}]))}));
const initial=JSON.stringify(withHistory);
const start=JSON.stringify({programId:'program-deload',workoutId:'workout-deload',startedAt:2000});
const baseline=prepareNativeWorkout(initial,start);
const [trained,skipped]=baseline.rows;
assert.ok(trained && skipped && trained.id!==skipped.id);
assert.ok(trained.sets.some(set=>!set.warm && Number(set.weight)>0),'test requires a real positive issued target');
const source=JSON.parse(initial);
source.saved[0].deloadNext={[trained.id]:.9,[skipped.id]:.9};
// Same-clock completion must still advance the program revision for merge.
source.saved[0].updatedAt=62000;
const origin=JSON.stringify(source),plan=prepareNativeWorkout(origin,start),issued=JSON.stringify(plan);
assert.deepEqual(issuedNativeWorkoutFrom(origin,issued).plan,plan);
for(const row of plan.rows) {
  if(!Object.hasOwn(source.saved[0].deloadNext,row.id)) continue;
  const original=baseline.rows.find(other=>other.slot===row.slot);
  for(const [index,set] of row.sets.entries()) {
    const load=Number(original.sets[index].weight);
    if(load>0)assert.equal(Number(set.weight),loadableAtOrBelow(EX_BY_ID[row.id],load*.9,'kg'));
    if(!set.warm)assert.equal(set.target.decisionTrace.evaluation.reasonCode,'user_one_shot_deload');
  }
}
const slots=issuedNativeWorkoutFrom(origin,issued).slots;
const journal={format:2,session:plan.session,slots,startRevision:0,
  events:slots.map((slot,index)=>({operationId:`deload-set-${index}`,slot,
    disposition:slot.exerciseId===skipped.id?'SKIPPED':'COMPLETED',
    values:slot.exerciseId===skipped.id?null:{load:'40',unit:'kg',reps:6,rir:null},
    fromRevision:index,toRevision:index+1})),
  finish:{operationId:'deload-finish',fromRevision:slots.length,toRevision:slots.length+1,
    completedAt:62000,durationMs:60000}};
const wire=JSON.stringify(journal),prepared=prepareCanonicalWorkoutHistory(origin,origin,wire,issued);
const result=parseStoredData(prepared.candidateSnapshot),program=result.saved[0];
assert.deepEqual(program.deloadNext,{[skipped.id]:.9});
assert.equal(program.updatedAt,62001);
assert.equal(result.savedAt,62001);
assert.equal(result.history.length,withHistory.history.length+1);
const completed=result.history.find(entry=>entry.id==='workout-deload');
assert.ok(completed.perf[trained.id]);
assert.equal(completed.perf[skipped.id],undefined);
assert.equal(JSON.parse(origin).saved[0].deloadNext[trained.id],.9,'preparation has no storage side effects');
assert.equal(prepareCanonicalWorkoutHistory(origin,prepared.candidateSnapshot,wire,issued).alreadyPresent,true);
const next=prepareNativeWorkout(prepared.candidateSnapshot,JSON.stringify({programId:'program-deload',
  workoutId:'workout-next-deload',startedAt:63000}));
assert.notEqual(next.session.dayId,plan.session.dayId);
assert.equal(JSON.parse(prepared.candidateSnapshot).saved[0].deloadNext[trained.id],undefined);

const allSkipped=structuredClone(journal);
for(const event of allSkipped.events){event.disposition='SKIPPED';event.values=null;}
const skippedResult=prepareCanonicalWorkoutHistory(origin,origin,JSON.stringify(allSkipped),issued);
assert.deepEqual(parseStoredData(skippedResult.candidateSnapshot).saved[0].deloadNext,source.saved[0].deloadNext);
assert.equal(parseStoredData(skippedResult.candidateSnapshot).saved[0].updatedAt,62000);

// An old completion retry cannot consume an explicitly rearmed later choice.
const rearmed=JSON.parse(prepared.candidateSnapshot);
rearmed.saved[0].deloadNext[trained.id]=.9;
rearmed.saved[0].updatedAt=70000;rearmed.savedAt=70000;
const rearmedRaw=JSON.stringify(rearmed);
assert.equal(prepareCanonicalWorkoutHistory(origin,rearmedRaw,wire,issued).candidateSnapshot,rearmedRaw);
for(const invalid of [true,null,'0.9',0,1,-1,[], {'unknown-exercise':.9}]) {
  const bad=JSON.parse(initial);
  bad.saved[0].deloadNext=typeof invalid==='object' && invalid!==null ? invalid : {[trained.id]:invalid};
  assert.throws(()=>prepareNativeWorkout(JSON.stringify(bad),start),'unsupported deload choices must fail closed');
}
const changed=JSON.parse(origin);changed.saved[0].deloadNext[trained.id]=.8;
assert.throws(()=>prepareCanonicalWorkoutHistory(origin,JSON.stringify(changed),wire,issued),
  'completion cannot consume a concurrent deload edit');
assert.throws(()=>prepareCanonicalWorkoutHistory(origin,origin,wire),
  'legacy journals cannot claim a deload prescription was actually issued');
console.log('PASS native deload: issued equipment targets, atomic history/choice candidate, skipped choices, monotonic revisions, next day and exact/rearmed retries');
