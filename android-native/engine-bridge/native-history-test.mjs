import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';
import { parseStoredData,exRecords,lifterModelKey,nextSessionCursor } from '../../modules/engine-api.js';

function fixture(tech=null,mixed=false) {
  const source=JSON.parse(prepareNativeProgram(JSON.stringify({creationId:'create-correct',programId:'program-correct',
    createdAt:1000,seed:223,profile:{name:'Correction proof',goal:'both',experience:'intermediate',unit:'kg',
      split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
      allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot);
  const start=JSON.stringify({programId:'program-correct',workoutId:'workout-correct',startedAt:2000});
  let plan=prepareNativeWorkout(JSON.stringify(source),start);
  if(tech) {
    const row=plan.rows[0],key=`${plan.session.dayId}:${row.slot}`;
    source.saved[0].nextWeekPrescriptions[key][plan.session.weekIndex].tech=tech;
    plan=prepareNativeWorkout(JSON.stringify(source),start);
  }
  const origin=JSON.stringify(source),issued=JSON.stringify(plan),slots=issuedNativeWorkoutFrom(origin,issued).slots;
  const journal={format:2,session:plan.session,slots,startRevision:0,events:slots.map((slot,i)=>({
    operationId:`set-${i}`,slot,disposition:i===1?'SKIPPED':'COMPLETED',
    values:i===1?null:{load:mixed && i===0?'0':'200',unit:'kg',reps:6,rir:i%2===0?2:null},
    fromRevision:i,toRevision:i+1})),finish:{operationId:'finish-correct',fromRevision:slots.length,
      toRevision:slots.length+1,completedAt:62000,durationMs:60000}};
  // Observation order is logging order, not workout order.
  journal.events=journal.events.reverse().map((e,i)=>({...e,fromRevision:i,toRevision:i+1}));
  return {snapshot:prepareCanonicalWorkoutHistory(origin,origin,JSON.stringify(journal),issued).candidateSnapshot,plan};
}
const command=(history,changes,operationId='correct-once')=>JSON.stringify({operationId,
  snapshotSha256:history.snapshotSha256,historyId:history.historyId,entrySha256:history.entrySha256,
  correctedAt:70000,changes});
const change=(exerciseId,set,values={})=>({exerciseId,setIndex:set.setIndex,load:set.load,reps:set.reps,rir:set.rir,...values});
for(const tech of [null,'myo-reps','drop set']) {
  const {snapshot,plan}=fixture(tech),before=inspectNativeHistory(snapshot,'workout-correct');
  const lift=before.exercises[0],set=lift.sets[0];
  const request=command(before,[change(lift.id,set,{load:'50',reps:8,rir:4})]);
  const result=prepareNativeHistoryCorrection(snapshot,request),after=JSON.parse(result.candidateSnapshot);
  const original=JSON.parse(snapshot).history[0],corrected=after.history[0];
  assert.equal(after.history.length,1);assert.equal(corrected.date,original.date);
  assert.deepEqual(corrected.nativeCompletion,original.nativeCompletion);
  assert.equal(corrected.totalSets,original.totalSets);assert.equal(corrected.setsDone,original.setsDone);
  assert.equal(corrected.volume,original.volume-200*6+50*8,'all extension volume must survive a working-set edit');
  assert.equal(corrected.perf[lift.id].sets[0].w,50);assert.equal(corrected.perf[lift.id].sets[0].r,8);
  assert.equal(corrected.perf[lift.id].sets[0].rir,4);assert.equal(corrected.perf[lift.id].sets[0].rirReported,true);
  const {applied:oldApplied,...oldTrace}=original.perf[lift.id].sets[0].decisionTrace;
  const {applied,...trace}=corrected.perf[lift.id].sets[0].decisionTrace;
  assert.deepEqual(trace,oldTrace);assert.deepEqual(applied,{...oldApplied,load:50,reps:8,owner:'user'});
  assert.deepEqual(after.perf[lift.id],corrected.perf[lift.id]);
  assert.notEqual(lifterModelKey([original]),lifterModelKey([corrected]));
  assert.deepEqual(nextSessionCursor(after.saved[0],after.history),nextSessionCursor(after.saved[0],[original]));
  assert.deepEqual(after.saved,JSON.parse(snapshot).saved,'correction cannot rearm deloads or change the program');
  assert.equal(result.history.snapshotSha256,result.candidateSnapshotSha256);
  assert.equal(prepareNativeHistoryCorrection(result.candidateSnapshot,request).candidateSnapshot,result.candidateSnapshot);
  assert.equal(prepareNativeHistoryCorrection(result.candidateSnapshot,request).alreadyPresent,true);
  assert.throws(()=>prepareNativeHistoryCorrection(result.candidateSnapshot,command(before,[change(lift.id,set,{load:'55'})],'stale')));
  assert.throws(()=>prepareNativeHistoryCorrection(result.candidateSnapshot,request.replace('"50"','"55"')));
  if(tech) {
    const extension=lift.sets.find(s=>s.kind!=='working');assert.ok(extension);
    const ext=prepareNativeHistoryCorrection(result.candidateSnapshot,command(result.history,
      [change(lift.id,extension,{load:'999',reps:5,rir:null})],'extension-correct'));
    assert.equal(ext.history.exercises[0].record.w,200,'extensions cannot become strength records');
    const entry=JSON.parse(ext.candidateSnapshot).history[0];
    assert.equal(entry.perf[lift.id].sets[extension.setIndex].sub,true);
    assert.equal(entry.volume,corrected.volume-200*6+999*5);
  }
  for(const invalid of [
    {load:''},{load:'1e2'},{load:'-1'},{reps:0},{reps:1.5},{rir:11},{setIndex:19},{exerciseId:'unknown'}
  ])assert.throws(()=>prepareNativeHistoryCorrection(snapshot,command(before,[change(lift.id,set,invalid)])));
  assert.throws(()=>prepareNativeHistoryCorrection(snapshot,command(before,[change(lift.id,set),change(lift.id,set)])));
  for(const field of ['dayId','unit','date','programId']) {
    const bad=JSON.parse(request);bad[field]='changed';
    assert.throws(()=>prepareNativeHistoryCorrection(snapshot,JSON.stringify(bad)));
  }
  const wire=JSON.stringify({protocolVersion:1,id:'history-test',method:'prepareNativeHistoryCorrection',
    params:{currentSnapshot:snapshot,correctionJson:request}});
  assert.equal(JSON.parse(dispatchWorkoutStoreJson(wire)).ok,true);
  assert.equal(JSON.parse(dispatchEngineJson(wire)).ok,false);
  assert.equal(JSON.parse(dispatchBackupSchemaJson(wire)).ok,false);
  for(const mutate of [s=>s.tombs['workout-correct']=99999,s=>s.history.push(s.history[0]),
    s=>delete s.history[0].nativeSetObservations,s=>s.savedAt++]) {
    const bad=JSON.parse(snapshot);mutate(bad);
    assert.throws(()=>prepareNativeHistoryCorrection(JSON.stringify(bad),request));
  }
}
// Mixed zero rows and skips have no editable canonical row, but must survive.
const mixed=fixture('drop set',true),mixedBefore=inspectNativeHistory(mixed.snapshot,'workout-correct');
const mixedLift=mixedBefore.exercises.find(e=>e.sets.length),mixedSet=mixedLift.sets[0];
const mixedResult=prepareNativeHistoryCorrection(mixed.snapshot,command(mixedBefore,[change(mixedLift.id,mixedSet,{load:'0',rir:null})]));
assert.equal(JSON.parse(mixedResult.candidateSnapshot).history[0].nativeSetObservations.events
  .filter(e=>e.disposition==='SKIPPED').length,1);
assert.equal(JSON.parse(mixedResult.candidateSnapshot).history[0].nativeSetObservations.events
  .find(e=>e.slot.exerciseIndex===0 && e.slot.setIndex===0).values.load,'0');
// Reducing a false high entry must restore the earlier genuine working-set PR.
const {snapshot}=fixture(),store=JSON.parse(snapshot),entry=store.history[0];
const previous=structuredClone(entry);previous.id='earlier';previous.date=61000;
for(const perf of Object.values(previous.perf))for(const set of perf.sets)set.w=100;
store.history.push(previous);
const raw=JSON.stringify(store),view=inspectNativeHistory(raw,entry.id),lift=view.exercises[0];
const lowered=prepareNativeHistoryCorrection(raw,command(view,lift.sets.map(s=>change(lift.id,s,{load:'50'}))));
assert.equal(lowered.history.exercises[0].record.w,100);
assert.deepEqual(lowered.history.exercises[0].record,exRecords(parseStoredData(lowered.candidateSnapshot).history,lift.id,'kg').maxWeight);
// Editing an older workout must not replace a newer Last result in the mirror.
const withNewer=JSON.parse(snapshot),later={id:'newer-result',date:63000,updatedAt:63000,unit:'kg',
  programId:entry.programId,dayId:entry.dayId,weekIndex:1,
  perf:{[lift.id]:{weight:90,reps:7,date:63000,sets:[{w:90,r:7,workingSetIndex:0}]}}};
withNewer.history.push(later);withNewer.perf[lift.id]=later.perf[lift.id];
const newerRaw=JSON.stringify(withNewer),olderView=inspectNativeHistory(newerRaw,entry.id);
const olderEdit=prepareNativeHistoryCorrection(newerRaw,command(olderView,
  olderView.exercises[0].sets.map(s=>change(lift.id,s,{load:'50'}))));
assert.deepEqual(JSON.parse(olderEdit.candidateSnapshot).perf[lift.id],later.perf[lift.id]);
assert.equal(olderEdit.history.exercises[0].record.w,90);
// Reporting the same RIR as a former target is still a new actual observation.
const effortView=inspectNativeHistory(snapshot,entry.id);
const effortLift=effortView.exercises.find(ex=>ex.sets.some(s=>s.rir===null));
const effortSet=effortLift.sets.find(s=>s.rir===null);
const effortResult=prepareNativeHistoryCorrection(snapshot,command(effortView,
  [change(effortLift.id,effortSet,{rir:2})],'report-actual-rir'));
assert.equal(JSON.parse(effortResult.candidateSnapshot).history[0].perf[effortLift.id].sets[effortSet.setIndex].rirReported,true);
const clearEffort=prepareNativeHistoryCorrection(effortResult.candidateSnapshot,command(effortResult.history,
  [change(effortLift.id,effortSet,{rir:null})],'clear-actual-rir'));
assert.equal(clearEffort.history.exercises.find(ex=>ex.id===effortLift.id).sets
  .find(s=>s.setIndex===effortSet.setIndex).rir,null);
assert.equal(JSON.parse(clearEffort.candidateSnapshot).history[0].perf[effortLift.id].sets[effortSet.setIndex].rirReported,undefined);
console.log('PASS native history corrections: exact observation mapping, retained targets/extensions/skips, canonical mirrors/cache/records, stale conflicts and exact retries');
