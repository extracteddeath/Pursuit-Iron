import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,inspectNativeWorkoutMuscles } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';
const snapshot=prepareNativeProgram(JSON.stringify({
  creationId:'adjust-create',programId:'adjust-program',createdAt:1000,seed:223,
  profile:{name:'Audited native adjustment',goal:'hypertrophy',experience:'beginner',
    unit:'kg',split:'full_body',days:3,session:'s60',
    equipment:nativeProgramOptions().equipment,allowBodyweight:true,allowSupersets:true,barbellCap:3}
})).snapshot;
const issued=prepareNativeWorkout(snapshot,JSON.stringify({programId:'adjust-program',
  workoutId:'adjust-workout',startedAt:2000}));
const review=inspectNativeWorkoutMuscles(snapshot,JSON.stringify(issued));
const affected=review.rows.find(row=>issued.rows.some(r=>r.slot===row.exerciseIndex && r.sets.filter(s=>!s.warm).length>=2));
assert.ok(affected);
const part=affected.primaryPart,slots=issued.rows.flatMap(row=>row.sets.filter(s=>!s.warm).map((set,i)=>({
  exerciseIndex:row.slot,setIndex:i,exerciseId:row.id
})));
assert.ok(slots.length>2);
const skipped=slots.find(s=>s.exerciseIndex===affected.exerciseIndex && s.setIndex===1);
const events=slots.map((slot,i)=>({
  operationId:`adjust-set-${i}`,slot,
  disposition:slot===skipped?'SKIPPED':'COMPLETED',
  values:slot===skipped?null:{load:'40',unit:'kg',reps:6,rir:2},
  fromRevision:i,toRevision:i+1,replacesOperationId:null,
  adjustment:slot===skipped?{action:'reduce',part}:null
}));
assert.equal(events.filter(e=>e.adjustment!==null).length,0,'slots are not identity-equal');
for(const event of events)if(event.slot.exerciseIndex===skipped.exerciseIndex &&
  event.slot.setIndex===skipped.setIndex) {
  event.disposition='SKIPPED';event.values=null;event.adjustment={action:'reduce',part};
}
const finished={format:4,session:issued.session,slots,startRevision:0,
  events,finish:{operationId:'adjust-finish',fromRevision:events.length,toRevision:events.length+1,
    completedAt:62000,durationMs:60000}};
const handoff=JSON.stringify(finished),issuedRaw=JSON.stringify(issued);
const result=prepareCanonicalWorkoutHistory(snapshot,snapshot,handoff,issuedRaw);
assert.deepEqual(result.historyEntry.nativeSetAdjustments,{format:1,events:[
  {slot:skipped,action:'reduce',part}
]});
assert.equal(result.historyEntry.nativeSetRevisions.events.length,events.length);
assert.equal(result.historyEntry.nativeSetObservations.events.filter(e=>e.disposition==='SKIPPED').length,1);
assert.equal(prepareCanonicalWorkoutHistory(snapshot,result.candidateSnapshot,handoff,issuedRaw).alreadyPresent,true);
assert.equal(inspectNativeHistory(result.candidateSnapshot,issued.session.workoutId).historyId,issued.session.workoutId);
const badAction=structuredClone(finished);badAction.events.find(e=>e.adjustment).adjustment.action='diagnosed';
assert.throws(()=>prepareCanonicalWorkoutHistory(snapshot,snapshot,JSON.stringify(badAction),issuedRaw));
const badPart=structuredClone(finished);badPart.events.find(e=>e.adjustment).adjustment.part='unrelated_fake';
assert.throws(()=>prepareCanonicalWorkoutHistory(snapshot,snapshot,JSON.stringify(badPart),issuedRaw));
const badOwner=structuredClone(finished);badOwner.events[0].adjustment={action:'reduce',part};
assert.throws(()=>prepareCanonicalWorkoutHistory(snapshot,snapshot,JSON.stringify(badOwner),issuedRaw));
const missing=structuredClone(finished);delete missing.events[0].adjustment;
assert.throws(()=>prepareCanonicalWorkoutHistory(snapshot,snapshot,JSON.stringify(missing),issuedRaw));
console.log('PASS v4 native adjusted skip provenance, legacy observation compatibility, exact replay, canonical primary muscle matching and malformed/forged adjustment rejection');
