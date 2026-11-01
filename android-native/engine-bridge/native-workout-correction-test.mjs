import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';

for(const tech of [null,'myo-reps','drop set']) {
  const source=JSON.parse(prepareNativeProgram(JSON.stringify({creationId:'create-edits',programId:'program-edits',
    createdAt:1000,seed:223,profile:{name:'In-workout correction proof',goal:'both',experience:'intermediate',unit:'kg',
      split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
      allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot);
  const start=JSON.stringify({programId:'program-edits',workoutId:'workout-edits',startedAt:2000});
  let plan=prepareNativeWorkout(JSON.stringify(source),start);
  if(tech) {
    source.saved[0].nextWeekPrescriptions[`${plan.session.dayId}:0`][1].tech=tech;
    plan=prepareNativeWorkout(JSON.stringify(source),start);
  }
  const origin=JSON.stringify(source),issued=JSON.stringify(plan),slots=issuedNativeWorkoutFrom(origin,issued).slots;
  const events=slots.map((slot,i)=>({operationId:`original-${i}`,slot,
    disposition:i===1?'SKIPPED':'COMPLETED',values:i===1?null:{load:i===0?'999':'40',unit:'kg',reps:6,rir:null},
    fromRevision:i,toRevision:i+1,replacesOperationId:null}));
  const edit=(index,op,disposition,values)=>{
    const slot=slots[index],prior=events.findLast(e=>e.slot.exerciseIndex===slot.exerciseIndex && e.slot.setIndex===slot.setIndex);
    events.push({operationId:op,slot,disposition,values,fromRevision:events.length,toRevision:events.length+1,
      replacesOperationId:prior.operationId});
  };
  edit(0,'mark-skipped','SKIPPED',null);
  edit(0,'correct-zero','COMPLETED',{load:'0',unit:'kg',reps:8,rir:2});
  edit(1,'restore-skipped','COMPLETED',{load:'45',unit:'kg',reps:8,rir:0});
  if(tech) {
    const extension=slots.findIndex(slot=>plan.rows.find(row=>row.slot===slot.exerciseIndex)
      .sets.filter(s=>!s.warm)[slot.setIndex].sub);
    assert.ok(extension>=0);
    edit(extension,'correct-extension','COMPLETED',{load:'25',unit:'kg',reps:5,rir:null});
  }
  const journal={format:3,session:plan.session,slots,startRevision:0,events,
    finish:{operationId:'finish',fromRevision:events.length,toRevision:events.length+1,completedAt:62000,durationMs:60000}};
  const handoff=JSON.stringify(journal),result=prepareCanonicalWorkoutHistory(origin,origin,handoff,issued);
  const entry=result.historyEntry;
  assert.deepEqual(entry.nativeSetRevisions,{format:1,startRevision:0,events,finish:journal.finish});
  assert.equal(entry.nativeCompletion.journalRevision,events.length+1);
  assert.equal(entry.nativeSetObservations.events.length,slots.length);
  assert.equal(entry.nativeSetObservations.events.find(e=>e.slot.exerciseIndex===0 && e.slot.setIndex===0).values.load,'0');
  assert.ok(!entry.perf[plan.rows[0].id].sets.some(s=>s.workingSetIndex===0),'mixed-zero omission remains canonical policy');
  assert.equal(entry.setsDone,slots.length,'corrections are not additional completed sets');
  // Compare the real canonical logger on the final values, without an edit log.
  const effective=new Map();for(const event of events)effective.set(`${event.slot.exerciseIndex}:${event.slot.setIndex}`,event);
  const materialized={...journal,format:2,events:[...effective.values()].map(({replacesOperationId,...e},i)=>
    ({...e,fromRevision:i,toRevision:i+1})),finish:{...journal.finish,fromRevision:slots.length,toRevision:slots.length+1}};
  const reference=prepareCanonicalWorkoutHistory(origin,origin,JSON.stringify(materialized),issued).historyEntry;
  assert.deepEqual(entry.perf,reference.perf);assert.equal(entry.volume,reference.volume);
  assert.deepEqual(entry.nativeSetObservations,reference.nativeSetObservations);
  assert.equal(prepareCanonicalWorkoutHistory(origin,result.candidateSnapshot,handoff,issued).alreadyPresent,true);
  const changedPast=structuredClone(journal);changedPast.events[0].values.load='35';
  assert.throws(()=>prepareCanonicalWorkoutHistory(origin,result.candidateSnapshot,JSON.stringify(changedPast),issued),
    'the original evidence participates in completion identity even when final values agree');
  for(const mutate of [
    j=>j.events[slots.length].replacesOperationId='wrong-prior',
    j=>j.events[slots.length+1].replacesOperationId='original-0',
    j=>j.events[slots.length].replacesOperationId=null,
    j=>delete j.events[0].replacesOperationId,
    j=>j.events[slots.length].operationId='original-0',
    j=>j.events[slots.length+1].values.unit='lb',
    j=>j.finish.fromRevision--,
    j=>j.format=2
  ]) {
    const bad=structuredClone(journal);mutate(bad);
    assert.throws(()=>prepareCanonicalWorkoutHistory(origin,origin,JSON.stringify(bad),issued));
  }
  const request=JSON.stringify({protocolVersion:1,id:'edited-workout',method:'prepareIssuedWorkoutHistory',
    params:{originSnapshot:origin,currentSnapshot:origin,finishedJournal:handoff,issuedWorkout:issued}});
  assert.equal(JSON.parse(dispatchWorkoutStoreJson(request)).ok,true);
  assert.equal(JSON.parse(dispatchEngineJson(request)).ok,false);assert.equal(JSON.parse(dispatchBackupSchemaJson(request)).ok,false);
  // Subsequent saved-history correction must retain the original in-workout audit.
  const view=inspectNativeHistory(result.candidateSnapshot,entry.id),lift=view.exercises[0],set=lift.sets[0];
  assert.equal(lift.id,plan.rows[0].id);assert.equal(lift.record.w,45,'a corrected false high must not remain a strength record');
  const corrected=prepareNativeHistoryCorrection(result.candidateSnapshot,JSON.stringify({operationId:'history-edit',
    snapshotSha256:view.snapshotSha256,historyId:view.historyId,entrySha256:view.entrySha256,correctedAt:70000,
    changes:[{exerciseId:lift.id,setIndex:set.setIndex,load:'50',reps:9,rir:null}]}));
  assert.deepEqual(JSON.parse(corrected.candidateSnapshot).history[0].nativeSetRevisions,entry.nativeSetRevisions);
}
console.log('PASS active-workout corrections: exact receipt chains, original audit, working/myo/drop and skipped/zero observations, canonical parity, retry identity and later history edits');
