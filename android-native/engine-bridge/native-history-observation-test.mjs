import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';
import { dispatchWorkoutStoreJson as sourceDispatch } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';
import { normalizeEditedHistoryEntry,nextSessionCursor,EX_BY_ID } from '../../modules/engine-api.js';

let dispatch=sourceDispatch;
if(process.argv[2]) {
  const context=vm.createContext({});vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  dispatch=context.PursuitIronBridge.dispatchWorkoutStoreJson;
}
const wire=(method,params)=>JSON.stringify({protocolVersion:1,id:'observation-proof',method,params});
const sameSlot=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
let operation=0;
const command=(view,changes)=>JSON.stringify({format:2,operationId:`observe-${++operation}`,
  snapshotSha256:view.snapshotSha256,entrySha256:view.entrySha256,historyId:view.historyId,
  correctedAt:70000+operation,changes});
const completed=(row,load='45',reps=9,rir=3)=>({slot:row.slot,disposition:'COMPLETED',values:{load,unit:'kg',reps,rir}});
const skipped=row=>({slot:row.slot,disposition:'SKIPPED',values:null});
function fixture(tech=null,duplicate=false,allSkipped=false,annotated=false) {
  const source=JSON.parse(prepareNativeProgram(JSON.stringify({creationId:'create-observations',programId:'program-observations',
    createdAt:1000,seed:223,profile:{name:'History observations',goal:'both',experience:'intermediate',unit:'kg',
      split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
      allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot);
  const program=source.saved[0],day=program.days[0];
  if(duplicate) {
    day.exercises[1]=day.exercises[0];
    program.nextWeekPrescriptions[`${day.id}:1`]=structuredClone(program.nextWeekPrescriptions[`${day.id}:0`]);
  }
  if(tech)for(const slot of duplicate?[0,1]:[0])program.nextWeekPrescriptions[`${day.id}:${slot}`][1].tech=tech;
  const origin=JSON.stringify(source),plan=prepareNativeWorkout(origin,
    JSON.stringify({programId:program.id,workoutId:'workout-observations',startedAt:2000})),issued=JSON.stringify(plan);
  const slots=issuedNativeWorkoutFrom(origin,issued).slots;
  const events=slots.map((slot,i)=>({operationId:`original-${i}`,slot,
    disposition:allSkipped || slot.exerciseIndex===0 && slot.setIndex===1?'SKIPPED':'COMPLETED',
    values:allSkipped || slot.exerciseIndex===0 && slot.setIndex===1?null:
      {load:slot.exerciseIndex===0 && slot.setIndex===0?'0':'50',unit:'kg',reps:8,rir:i%2?2:null},
    fromRevision:i,toRevision:i+1})).reverse().map((e,i)=>({...e,fromRevision:i,toRevision:i+1}));
  const journal={format:2,session:plan.session,slots,startRevision:0,events,
    finish:{operationId:'finish-observations',fromRevision:slots.length,toRevision:slots.length+1,completedAt:62000,durationMs:60000}};
  if(annotated) {
    journal.format=4;journal.events=events.map(event=>({...event,replacesOperationId:null,
      adjustment:event.disposition==='SKIPPED'?{action:'reduce',part:EX_BY_ID[event.slot.exerciseId].part}:null}));
  }
  const result=prepareCanonicalWorkoutHistory(origin,origin,JSON.stringify(journal),issued);
  return {origin,plan,issued,journal,result};
}
function edit(raw,changes) {
  const before=inspectNativeHistory(raw,'workout-observations'),request=command(before,changes);
  const result=prepareNativeHistoryCorrection(raw,request),response=JSON.parse(dispatch(wire('prepareNativeHistoryCorrection',
    {currentSnapshot:raw,correctionJson:request})));
  assert.equal(response.ok,true);assert.deepEqual(response.result,result);
  const replay=prepareNativeHistoryCorrection(result.candidateSnapshot,request);
  assert.equal(replay.alreadyPresent,true);assert.equal(replay.candidateSnapshot,result.candidateSnapshot);
  const conflict=JSON.parse(request);conflict.correctedAt++;
  assert.throws(()=>prepareNativeHistoryCorrection(result.candidateSnapshot,JSON.stringify(conflict)),
    'same operation cannot change its exact request');
  const after=JSON.parse(result.candidateSnapshot).history[0],original=JSON.parse(raw).history[0];
  assert.deepEqual(after.nativeCompletion,original.nativeCompletion);
  assert.equal(after.date,original.date);assert.equal(after.totalSets,original.totalSets);
  assert.equal(after.nativeHistoryRevisions.events.length,(original.nativeHistoryRevisions?.events.length || 0)+changes.length);
  assert.deepEqual(JSON.parse(result.candidateSnapshot).saved,JSON.parse(raw).saved);
  assert.deepEqual(nextSessionCursor(JSON.parse(raw).saved[0],JSON.parse(raw).history),
    nextSessionCursor(JSON.parse(result.candidateSnapshot).saved[0],JSON.parse(result.candidateSnapshot).history));
  return {result,request,after};
}
function compareCanonical(fixture,raw) {
  const entry=JSON.parse(raw).history[0],journal=structuredClone(fixture.journal);
  journal.events=journal.events.map(event=>({...event,...entry.nativeSetObservations.events.find(e=>sameSlot(e.slot,event.slot))}));
  const reference=prepareCanonicalWorkoutHistory(fixture.origin,fixture.origin,JSON.stringify(journal),fixture.issued).historyEntry;
  const normalized=normalizeEditedHistoryEntry(reference,reference);
  assert.deepEqual(entry.perf,JSON.parse(JSON.stringify(normalized.perf)));
  assert.equal(entry.volume,normalized.volume);assert.equal(entry.setsDone,normalized.setsDone);
}
for(const [tech,duplicate] of [[null,false],['myo-reps',false],['drop set',true]]) {
  const f=fixture(tech,duplicate),initial=f.result.historyEntry;
  let raw=f.result.candidateSnapshot,view=inspectNativeHistory(raw,initial.id);
  assert.equal(view.editRows.length,f.journal.slots.length);
  for(const row of initial.nativeHistoryBasis.rows) {
    for(const key of ['weight','reps','date','sets'])assert.ok(!Object.hasOwn(row.performance,key));
    for(const missing of row.omitted) {
      for(const key of ['w','r','rir','rirReported'])assert.ok(!Object.hasOwn(missing.template,key));
      assert.ok(!missing.template.decisionTrace?.applied);
    }
  }
  const restore=view.editRows.find(row=>row.disposition==='SKIPPED');assert.ok(restore);
  let changed=edit(raw,[completed(restore)]);raw=changed.result.candidateSnapshot;
  assert.equal(changed.after.setsDone,initial.setsDone+1);compareCanonical(f,raw);
  view=changed.result.history;
  const zero=view.editRows.find(row=>!row.retained && row.values?.load==='0');assert.ok(zero);
  changed=edit(raw,[completed(zero)]);raw=changed.result.candidateSnapshot;compareCanonical(f,raw);
  assert.equal(changed.result.history.editRows.find(row=>sameSlot(row.slot,zero.slot)).retained,true);
  view=changed.result.history;
  const work=view.editRows.find(row=>row.retained && row.kind==='working' && row.values.load==='45');
  changed=edit(raw,[skipped(work)]);raw=changed.result.candidateSnapshot;compareCanonical(f,raw);
  const moved=changed.after.nativeHistoryBasis.rows.find(row=>row.exerciseIndex===work.slot.exerciseIndex)
    .omitted.find(set=>set.setIndex===work.slot.setIndex);
  assert.ok(moved.template.decisionTrace);assert.ok(!moved.template.decisionTrace.applied);
  const restoreAgain=changed.result.history.editRows.find(row=>sameSlot(row.slot,work.slot));
  changed=edit(raw,[completed(restoreAgain)]);raw=changed.result.candidateSnapshot;compareCanonical(f,raw);
  if(tech) {
    const extension=changed.result.history.editRows.find(row=>row.kind!=='working' && row.retained);
    changed=edit(raw,[skipped(extension)]);raw=changed.result.candidateSnapshot;compareCanonical(f,raw);
    changed=edit(raw,[completed(changed.result.history.editRows.find(row=>sameSlot(row.slot,extension.slot)))]);
    raw=changed.result.candidateSnapshot;compareCanonical(f,raw);
  }
  view=inspectNativeHistory(raw,initial.id);
  for(const mutate of [
    r=>r.changes[0].values.reps=0,r=>r.changes[0].values.load='',r=>r.changes[0].values.unit='lb',
    r=>r.changes[0].slot.exerciseIndex=47,r=>r.changes[0].slot.exerciseId='other-lift',
    r=>r.changes[0].values.rir=11,r=>r.changes[0].extra=true,r=>r.format=3,
    r=>r.changes.push(r.changes[0]),r=>r.changes[0].disposition='TARGET',r=>r.extra=true
  ]) {
    const bad=JSON.parse(command(view,[completed(view.editRows[0])]));mutate(bad);
    assert.throws(()=>prepareNativeHistoryCorrection(raw,JSON.stringify(bad)));
  }
  const legacy=JSON.parse(f.result.candidateSnapshot);delete legacy.history[0].nativeHistoryBasis;
  const old=JSON.stringify(legacy),legacyView=inspectNativeHistory(old,initial.id);
  assert.equal(legacyView.editRows,undefined);
  assert.throws(()=>prepareNativeHistoryCorrection(old,command(legacyView,[completed(restore)])));
  const retry=prepareCanonicalWorkoutHistory(f.origin,old,JSON.stringify(f.journal),f.issued);
  assert.equal(retry.alreadyPresent,true);assert.equal(retry.candidateSnapshot,old);
  const request=wire('prepareNativeHistoryCorrection',{currentSnapshot:raw,correctionJson:command(view,[completed(view.editRows[0])])});
  assert.equal(JSON.parse(dispatchEngineJson(request)).ok,false);assert.equal(JSON.parse(dispatchBackupSchemaJson(request)).ok,false);
}
// A wholly skipped workout can be restored without inventing targets or actuals.
const empty=fixture(null,false,true),emptyView=inspectNativeHistory(empty.result.candidateSnapshot,'workout-observations');
assert.equal(emptyView.exercises.length,0);assert.ok(emptyView.editRows.every(row=>row.values===null && !row.retained));
let restored=edit(empty.result.candidateSnapshot,[completed(emptyView.editRows[0])]);compareCanonical(empty,restored.result.candidateSnapshot);
restored=edit(restored.result.candidateSnapshot,[skipped(restored.result.history.editRows[0])]);
assert.deepEqual(restored.after.perf,{});assert.equal(restored.after.volume,0);assert.equal(restored.after.setsDone,0);
assert.ok(restored.result.history.editRows.every(row=>row.disposition==='SKIPPED'));
// Removing the last working set must not silently drop completed extensions.
const extended=fixture('drop set',false,false),exView=inspectNativeHistory(extended.result.candidateSnapshot,'workout-observations');
const working=exView.editRows.filter(row=>row.slot.exerciseIndex===0 && row.kind==='working' && row.retained);
assert.throws(()=>prepareNativeHistoryCorrection(extended.result.candidateSnapshot,command(exView,working.map(skipped))));
// Correcting an affected-muscle skip removes only its current effective reason.
// The original durable adjustment audit remains, and a later plain skip does not
// invent or reactivate an old injury-related reason.
const annotated=fixture(null,false,false,true),annotatedView=inspectNativeHistory(annotated.result.candidateSnapshot,'workout-observations');
const tagged=annotatedView.editRows.find(row=>row.disposition==='SKIPPED');
const repaired=edit(annotated.result.candidateSnapshot,[completed(tagged)]);
assert.deepEqual(repaired.after.nativeSetAdjustments,{format:1,events:[]});
assert.deepEqual(repaired.after.nativeSetRevisions,annotated.result.historyEntry.nativeSetRevisions);
const plain=edit(repaired.result.candidateSnapshot,[skipped(repaired.result.history.editRows.find(row=>sameSlot(row.slot,tagged.slot)))]);
assert.deepEqual(plain.after.nativeSetAdjustments,{format:1,events:[]});
assert.deepEqual(plain.after.nativeSetRevisions,annotated.result.historyEntry.nativeSetRevisions);
// Restoring an older observation must preserve a newer Last result and PR.
const older=fixture(),withNewer=JSON.parse(older.result.candidateSnapshot),oldEntry=older.result.historyEntry;
const liftId=older.plan.rows[0].id,newPerf={weight:90,reps:8,date:63000,sets:[{w:90,r:8,workingSetIndex:0}]};
withNewer.history.unshift({id:'newer-observation',date:63000,updatedAt:63000,programId:oldEntry.programId,
  dayId:oldEntry.dayId,unit:'kg',perf:{[liftId]:newPerf}});withNewer.perf[liftId]=newPerf;
const olderRaw=JSON.stringify(withNewer),olderView=inspectNativeHistory(olderRaw,oldEntry.id);
const olderRestore=prepareNativeHistoryCorrection(olderRaw,command(olderView,
  [completed(olderView.editRows.find(row=>row.disposition==='SKIPPED'))]));
assert.deepEqual(JSON.parse(olderRestore.candidateSnapshot).perf[liftId],newPerf);
assert.equal(olderRestore.history.exercises.find(ex=>ex.id===liftId).record.w,90);
assert.equal(olderRestore.history.date,oldEntry.date);
console.log('PASS saved observation edits'+(process.argv[2]?' through actual Android IIFE':'')+
  ': skipped/zero/working/myo/drop/repeated slots, complete exercise removal/restoration, canonical summaries, original targets, append-only audits and exact retries');
