import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';
import { dispatchWorkoutStoreJson as sourceDispatch } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';
import { parseStoredData,exRecords,lifterModelKey,nextSessionCursor } from '../../modules/engine-api.js';

// The source suite can also exercise the actual isolated Android IIFE. Keep
// source-produced expected values independent of the packaged dispatcher.
let dispatchWorkoutStoreJson=sourceDispatch;
if(process.argv[2]) {
  const context=vm.createContext({});
  vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  dispatchWorkoutStoreJson=context.PursuitIronBridge.dispatchWorkoutStoreJson;
  assert.equal(typeof dispatchWorkoutStoreJson,'function');
}

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
  const wireResult=JSON.parse(dispatchWorkoutStoreJson(wire));
  assert.equal(wireResult.ok,true);assert.deepEqual(wireResult.result,result);
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
// Repeated exercise IDs retain separate issued locations, even when extensions
// have identical observed values and observations arrive in reverse order.
for(const [tech,count,mixed] of [[null,4,false],['myo-reps',4,false],['drop set',4,true],[null,12,false]]) {
  const source=JSON.parse(prepareNativeProgram(JSON.stringify({creationId:'create-duplicate',programId:'program-duplicate',
    createdAt:1000,seed:223,profile:{name:'Repeated lift proof',goal:'both',experience:'intermediate',unit:'kg',
      split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
      allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot);
  const program=source.saved[0],day=program.days[0],exerciseId=day.exercises[0];
  day.exercises[1]=exerciseId;
  program.nextWeekPrescriptions[`${day.id}:1`]=structuredClone(program.nextWeekPrescriptions[`${day.id}:0`]);
  for(const slot of [0,1])Object.assign(program.nextWeekPrescriptions[`${day.id}:${slot}`][1],{sets:count,tech});
  const origin=JSON.stringify(source),plan=prepareNativeWorkout(origin,
    JSON.stringify({programId:program.id,workoutId:'workout-duplicate',startedAt:2000}));
  const issued=JSON.stringify(plan),slots=issuedNativeWorkoutFrom(origin,issued).slots;
  assert.equal(plan.rows[0].id,plan.rows[1].id);
  const events=slots.map((slot,i)=>({operationId:`duplicate-${i}`,slot,
    disposition:slot.exerciseIndex===0 && slot.setIndex===1?'SKIPPED':'COMPLETED',
    values:slot.exerciseIndex===0 && slot.setIndex===1?null:
      {load:mixed && slot.setIndex===0?'0':'75',unit:'kg',reps:8,rir:null},fromRevision:i,toRevision:i+1}))
    .reverse().map((event,i)=>({...event,fromRevision:i,toRevision:i+1}));
  const journal=JSON.stringify({format:2,session:plan.session,slots,startRevision:0,events,
    finish:{operationId:'finish-duplicate',fromRevision:slots.length,toRevision:slots.length+1,completedAt:62000,durationMs:60000}});
  const completion=prepareCanonicalWorkoutHistory(origin,origin,journal,issued);
  const completionWire=JSON.parse(dispatchWorkoutStoreJson(JSON.stringify({protocolVersion:1,id:'duplicate-finish',
    method:'prepareIssuedWorkoutHistory',params:{originSnapshot:origin,currentSnapshot:origin,
      finishedJournal:journal,issuedWorkout:issued}})));
  assert.equal(completionWire.ok,true);assert.deepEqual(completionWire.result,completion);
  const raw=completion.candidateSnapshot,entry=completion.historyEntry,view=inspectNativeHistory(raw,entry.id);
  const lift=view.exercises.find(ex=>ex.id===exerciseId);
  assert.deepEqual([...new Set(lift.sets.map(set=>set.exerciseIndex))],[0,1]);
  assert.deepEqual(entry.perf[exerciseId].duplicateExerciseSlots,[0,1]);
  assert.equal(entry.nativeSetLocations.sets.length,Object.values(entry.perf).reduce((n,p)=>n+p.sets.length,0));
  if(count===12)assert.ok(lift.sets.at(-1).setIndex>19,'merged indices must not be truncated to one occurrence');
  const target=lift.sets.find(set=>set.exerciseIndex===1 && (tech?set.kind!=='working':true));
  assert.ok(target);
  const originalMap=structuredClone(entry.nativeSetLocations);
  const correction=command(view,[change(exerciseId,target,{load:'35',reps:9})]);
  const corrected=prepareNativeHistoryCorrection(raw,correction);
  const correctionWire=JSON.parse(dispatchWorkoutStoreJson(JSON.stringify({protocolVersion:1,id:'duplicate-correct',
    method:'prepareNativeHistoryCorrection',params:{currentSnapshot:raw,correctionJson:correction}})));
  assert.equal(correctionWire.ok,true);assert.deepEqual(correctionWire.result,corrected);
  const next=JSON.parse(corrected.candidateSnapshot).history[0];
  assert.deepEqual(next.nativeSetLocations,originalMap);
  assert.deepEqual(next.perf[exerciseId].duplicateExerciseSlots,[0,1]);
  assert.equal(next.perf[exerciseId].prescriptionEdited,true);
  const location=originalMap.sets.find(s=>s.exerciseId===exerciseId && s.setIndex===target.setIndex).slot;
  for(const [i,event] of next.nativeSetObservations.events.entries()) {
    if(event.slot.exerciseIndex===location.exerciseIndex && event.slot.setIndex===location.setIndex) {
      assert.deepEqual(event.values,{load:'35',unit:'kg',reps:9,rir:null});
    } else assert.deepEqual(event,entry.nativeSetObservations.events[i],'the other occurrence and skipped/omitted rows are untouched');
  }
  assert.equal(prepareNativeHistoryCorrection(corrected.candidateSnapshot,
    command(view,[change(exerciseId,target,{load:'35',reps:9})])).alreadyPresent,true);
  assert.equal(JSON.parse(dispatchWorkoutStoreJson(JSON.stringify({protocolVersion:1,id:'duplicate-inspect',
    method:'inspectNativeHistory',params:{rawSnapshot:raw,historyId:entry.id}}))).ok,true);
  // Replaying a completion committed before locations existed must leave its
  // bytes intact. Ambiguous legacy repeats stay summary-only, never guessed.
  const legacy=JSON.parse(raw);delete legacy.history.find(h=>h.id===entry.id).nativeSetLocations;
  const legacyRaw=JSON.stringify(legacy);
  const retry=prepareCanonicalWorkoutHistory(origin,legacyRaw,journal,issued);
  assert.equal(retry.alreadyPresent,true);assert.equal(retry.candidateSnapshot,legacyRaw);
  assert.ok(!Object.hasOwn(retry.historyEntry,'nativeSetLocations'));
  assert.throws(()=>inspectNativeHistory(legacyRaw,entry.id));
  for(const mutate of [
    e=>e.nativeSetLocations.format=2,
    e=>e.nativeSetLocations.sets.pop(),
    e=>e.nativeSetLocations.sets.push(e.nativeSetLocations.sets[0]),
    e=>e.nativeSetLocations.sets[0].slot.exerciseIndex=47,
    e=>e.nativeSetLocations.sets[0].slot.setIndex=1,
    e=>e.nativeSetLocations.sets[0].setIndex=480,
    e=>e.perf[exerciseId].duplicateExerciseSlots.reverse(),
    e=>{
      const a=e.nativeSetLocations.sets.filter(s=>s.exerciseId===exerciseId);
      [a[0].slot,a.at(-1).slot]=[a.at(-1).slot,a[0].slot];
    }
  ]) {
    const bad=JSON.parse(raw);mutate(bad.history.find(h=>h.id===entry.id));
    assert.throws(()=>inspectNativeHistory(JSON.stringify(bad),entry.id));
  }
  if(tech) {
    const bad=JSON.parse(raw),p=bad.history[0].perf[exerciseId];
    const locations=bad.history[0].nativeSetLocations.sets.filter(s=>s.exerciseId===exerciseId && p.sets[s.setIndex].sub);
    const first=locations.find(s=>s.slot.exerciseIndex===0),second=locations.find(s=>s.slot.exerciseIndex===1);
    [first.slot,second.slot]=[second.slot,first.slot];
    assert.throws(()=>inspectNativeHistory(JSON.stringify(bad),entry.id),'identical extensions cannot be reassigned to another occurrence');
  }
  // Rotate through the remaining real days. Last must keep skipped/omitted
  // positions and select the matching occurrence, including >20 merged rows.
  let rotation=corrected.candidateSnapshot,returned=false;
  for(let i=0;i<program.days.length;i++) {
    const start=JSON.stringify({programId:program.id,workoutId:`next-duplicate-${i}`,startedAt:100000+i*70000});
    const nextPlan=prepareNativeWorkout(rotation,start);
    const issuedWire=JSON.parse(dispatchWorkoutStoreJson(JSON.stringify({protocolVersion:1,id:'duplicate-next',
      method:'prepareNativeWorkout',params:{rawSnapshot:rotation,startJson:start}})));
    assert.equal(issuedWire.ok,true);assert.deepEqual(issuedWire.result,nextPlan);
    if(nextPlan.session.dayId===day.id) {
      for(const row of nextPlan.rows.filter(row=>row.id===exerciseId)) {
        assert.deepEqual(row.previous,next.perf[exerciseId].sets
          .filter(set=>!set.sub && set.decisionTrace.slot===row.slot)
          .map(set=>({workingSetIndex:set.workingSetIndex,load:String(set.w),reps:set.r,unit:'kg'})));
      }
      assert.ok(!nextPlan.rows[0].previous.some(set=>set.workingSetIndex===1),'a skipped set cannot borrow its successor');
      returned=true;break;
    }
    const retained=JSON.stringify(nextPlan),nextSlots=issuedNativeWorkoutFrom(rotation,retained).slots;
    const nextJournal={format:2,session:nextPlan.session,slots:nextSlots,startRevision:0,
      events:nextSlots.map((slot,j)=>({operationId:`next-${i}-${j}`,slot,disposition:'COMPLETED',
        values:{load:'40',unit:'kg',reps:8,rir:null},fromRevision:j,toRevision:j+1})),
      finish:{operationId:`finish-next-${i}`,fromRevision:nextSlots.length,toRevision:nextSlots.length+1,
        completedAt:nextPlan.session.startedAt+60000,durationMs:60000}};
    rotation=prepareCanonicalWorkoutHistory(rotation,rotation,JSON.stringify(nextJournal),retained).candidateSnapshot;
  }
  assert.ok(returned,'the canonical cursor must rotate back to the repeated-lift day');
}
console.log('PASS native history corrections'+(process.argv[2]?' through actual Android IIFE':'')+
  ': exact repeated-lift locations, retained targets/extensions/skips, canonical mirrors/cache/records, legacy completion retries, stale conflicts and exact correction retries');
