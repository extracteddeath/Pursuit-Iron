import assert from 'node:assert/strict';
import { dispatchWorkoutStoreJson } from '../engine-bridge/workout-store-protocol.mjs';
import { issuedNativeWorkoutFrom } from '../engine-bridge/native-workout.mjs';

/**
 * One real canonical bridge journey, not UI fixture numbers:
 * onboarding -> snapshot restore -> issued targets -> confirmed journal
 * -> exactly one history publication -> exact correction -> next session.
 *
 * The bridge prepares immutable snapshots only. Android's atomic store,
 * durable journal, process lifecycle, keyboard and visual presentation need
 * their own Kotlin and device acceptance; this is NOT an APK release gate
 * or a training-rule correctness certificate.
 */
let serial=0;
const run=(method,params)=>{
  const id=`journey-${++serial}`;
  const response=JSON.parse(dispatchWorkoutStoreJson(JSON.stringify({
    protocolVersion:1,id,method,params
  })));
  assert.equal(response.id,id,`${method} preserves request identity`);
  return response;
};
const accept=(method,params)=>{
  const response=run(method,params);
  assert.equal(response.ok,true,`${method}: ${response.error?.code??'unknown'}`);
  return response.result;
};
const reject=(method,params)=>{
  const response=run(method,params);
  assert.equal(response.ok,false,`${method} must fail closed`);
  return response.error.code;
};

for(const unit of ['lb','kg']) {
  const suffix=unit;
  const programId=`journey-program-${suffix}`;
  const workoutId=`journey-workout-${suffix}`;
  const options=accept('nativeProgramOptions',{});
  const setup=JSON.stringify({
    creationId:`journey-create-${suffix}`,programId,createdAt:1000,seed:223,
    profile:{name:'Integrated native journey',goal:'both',experience:'intermediate',
      unit,split:'full_body',days:3,session:'s60',equipment:options.equipment,
      allowBodyweight:true,allowSupersets:false,barbellCap:3}
  });

  // The user's reviewed program has one exact canonical source, including
  // stable identity, weight units, chosen equipment and empty history.
  const proposal=accept('prepareNativeProgram',{setupJson:setup});
  const origin=proposal.snapshot;
  assert.equal(proposal.preparedOnly,true);
  assert.equal(proposal.approvedForWorkoutExecution,false);
  assert.equal(JSON.parse(origin).history.length,0);
  const restored=accept('inspectNativeProgram',{rawSnapshot:origin});
  assert.equal(restored.program.program.id,programId);
  assert.equal(restored.setupJson,setup);
  assert.equal(accept('validateOwnedStore',{rawSnapshot:origin}).validated,true);

  const startJson=JSON.stringify({programId,workoutId,startedAt:2000});
  const plan=accept('prepareNativeWorkout',{rawSnapshot:origin,startJson});
  assert.equal(plan.session.workoutId,workoutId);
  assert.equal(plan.session.unit,unit);
  assert.ok(plan.rows.length>0);
  const issuedWorkout=JSON.stringify(plan);
  const issued=issuedNativeWorkoutFrom(origin,issuedWorkout);
  assert.deepEqual(issued.plan,plan);
  const slots=issued.slots;
  assert.ok(slots.length>1);

  // A partial journal has not been explicitly finished and cannot be
  // published. Values are user observations, never target RIR copied by UI.
  const events=slots.map((slot,i)=>({
    operationId:`journey-set-${suffix}-${i}`,slot,
    disposition:i===1?'SKIPPED':'COMPLETED',
    values:i===1?null:{load:'40',unit,reps:6,rir:i%2===0?2:null},
    fromRevision:i,toRevision:i+1
  }));
  const unfinished={
    format:2,session:plan.session,slots,startRevision:0,
    events:events.slice(0,1),finish:null
  };
  const params=finishedJournal=>({
    originSnapshot:origin,currentSnapshot:origin,issuedWorkout,finishedJournal
  });
  assert.equal(reject('prepareIssuedWorkoutHistory',
    params(JSON.stringify(unfinished))),'PREPARATION_REJECTED');

  const journal={format:2,session:plan.session,slots,startRevision:0,events,
    finish:{operationId:`journey-finish-${suffix}`,fromRevision:slots.length,
      toRevision:slots.length+1,completedAt:62000,durationMs:60000}};
  const finishedJournal=JSON.stringify(journal);
  const result=accept('prepareIssuedWorkoutHistory',params(finishedJournal));
  assert.equal(result.alreadyPresent,false);
  const completed=JSON.parse(result.candidateSnapshot);
  assert.equal(completed.history.length,1);
  const entry=completed.history[0];
  assert.equal(entry.id,workoutId);
  assert.equal(entry.totalSets,slots.length);
  assert.equal(entry.setsDone,slots.length-1);
  assert.equal(entry.nativeSetObservations.events.length,events.length);
  assert.deepEqual(entry.nativeSetObservations.events[1].values,null);
  assert.equal(entry.nativeSetObservations.events[0].values.rir,2);

  // After a lost completion acknowledgement, the same durable journal
  // must not publish a duplicate history entry.
  const retry=accept('prepareIssuedWorkoutHistory',{
    ...params(finishedJournal),currentSnapshot:result.candidateSnapshot
  });
  assert.equal(retry.alreadyPresent,true);
  assert.equal(retry.candidateSnapshot,result.candidateSnapshot);
  assert.equal(reject('prepareIssuedWorkoutHistory',{
    ...params(finishedJournal),issuedWorkout:JSON.stringify({
      ...plan,session:{...plan.session,workoutId:'forged-session'}
    })
  }),'PREPARATION_REJECTED');

  const history=accept('inspectNativeHistory',{
    rawSnapshot:result.candidateSnapshot,historyId:workoutId
  });
  assert.equal(history.historyId,workoutId);
  assert.equal(history.setsDone,slots.length-1);
  const exercise=history.exercises.find(x=>x.sets.length>0);
  assert.ok(exercise);
  const prior=exercise.sets[0];
  const change={exerciseId:exercise.id,setIndex:prior.setIndex,
    load:'42',reps:7,rir:3};
  const correctionJson=JSON.stringify({
    operationId:`journey-correction-${suffix}`,
    snapshotSha256:history.snapshotSha256,historyId:history.historyId,
    entrySha256:history.entrySha256,correctedAt:70000,changes:[change]
  });
  const edited=accept('prepareNativeHistoryCorrection',{
    currentSnapshot:result.candidateSnapshot,correctionJson
  });
  const corrected=JSON.parse(edited.candidateSnapshot);
  assert.equal(corrected.history.length,1);
  assert.equal(corrected.history[0].setsDone,entry.setsDone);
  assert.equal(corrected.history[0].totalSets,entry.totalSets);
  assert.deepEqual(corrected.saved,completed.saved,
    'editing actual performance must not silently regenerate the plan');

  const correctedHistory=accept('inspectNativeHistory',{
    rawSnapshot:edited.candidateSnapshot,historyId:workoutId
  });
  const correctedSet=correctedHistory.exercises.find(x=>x.id===exercise.id)
    ?.sets.find(x=>x.setIndex===prior.setIndex);
  assert.equal(correctedSet?.load,'42');
  assert.equal(correctedSet?.reps,7);
  assert.equal(correctedSet?.rir,3);
  assert.equal(accept('prepareNativeHistoryCorrection',{
    currentSnapshot:edited.candidateSnapshot,correctionJson
  }).alreadyPresent,true,'retrying the exact saved correction is idempotent');

  // A second, conflicting correction from the old entry revision fails,
  // rather than overwriting the first edit or a later workout.
  const stale=JSON.stringify({
    ...JSON.parse(correctionJson),
    operationId:`journey-stale-${suffix}`,
    changes:[{...change,load:'70'}]
  });
  assert.equal(reject('prepareNativeHistoryCorrection',{
    currentSnapshot:edited.candidateSnapshot,correctionJson:stale
  }),'PREPARATION_REJECTED');

  const next=accept('prepareNativeWorkout',{
    rawSnapshot:edited.candidateSnapshot,
    startJson:JSON.stringify({
      programId,workoutId:`journey-next-${suffix}`,startedAt:90000
    })
  });
  assert.notEqual(next.session.workoutId,workoutId);
  assert.notEqual(next.session.dayId,plan.session.dayId,
    'published training moves the next workout to the canonical next day');
}
console.log('PASS two-unit canonical native core journeys: program restore, issued workout, unfinished rejection, skipped/actual RIR, completion retry, history correction, stale conflict, and next day (preparation only; Android device acceptance pending)');
