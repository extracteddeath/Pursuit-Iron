import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection,inspectNativeHistoryPage } from './native-history.mjs';
import { dispatchWorkoutStoreJson as sourceDispatch } from './workout-store-protocol.mjs';
import { attemptedHistorySets,exRecords,percentagePlanFor,parseStoredData,EX_BY_ID } from '../../modules/engine-api.js';

let dispatch=sourceDispatch;
if(process.argv[2]) {
  const context=vm.createContext({});vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  dispatch=context.PursuitIronBridge.dispatchWorkoutStoreJson;
}
const send=(method,params)=>JSON.parse(dispatch(JSON.stringify({protocolVersion:1,id:'failed-proof',method,params})));
const slotKey=slot=>`${slot.exerciseIndex}:${slot.setIndex}`;
let operation=0;
function fixture(unit='kg',mixed=false,tech=null,duplicate=false) {
  const source=JSON.parse(prepareNativeProgram(JSON.stringify({creationId:'create-failed',programId:'program-failed',
    createdAt:1000,seed:223,profile:{name:'Failed-attempt lifecycle',goal:'strength',experience:'beginner',unit,
      split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
      allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot);
  const program=source.saved[0],day=program.days[0];program.config.percentScheme='gzclp';
  // Use actual percentage-tier ownership and an actual training max.
  day.primaryIndex=0;program.trainingMax={[day.exercises[0]]:80};program.trainingMaxUnit=unit;
  if(duplicate) {
    day.exercises[1]=day.exercises[0];
    program.nextWeekPrescriptions[`${day.id}:1`]=structuredClone(program.nextWeekPrescriptions[`${day.id}:0`]);
  }
  if(tech)program.nextWeekPrescriptions[`${day.id}:0`][1].tech=tech;
  const origin=JSON.stringify(source),start=JSON.stringify({programId:program.id,workoutId:'workout-failed',startedAt:2000});
  const plan=prepareNativeWorkout(origin,start),issued=JSON.stringify(plan),slots=issuedNativeWorkoutFrom(origin,issued).slots;
  const events=slots.map((slot,i)=>{
    const set=plan.rows.find(row=>row.slot===slot.exerciseIndex).sets.filter(s=>!s.warm)[slot.setIndex];
    const fail=slot.exerciseIndex===0 && !set.sub && (!mixed || slot.setIndex===0);
    return {operationId:`original-${i}`,slot,disposition:'COMPLETED',
      values:{load:fail?'80':'40',unit,reps:fail?0:6,rir:null},fromRevision:i,toRevision:i+1};
  });
  const journal={format:2,session:plan.session,slots,startRevision:0,events,
    finish:{operationId:'finish-failed',fromRevision:slots.length,toRevision:slots.length+1,completedAt:62000,durationMs:60000}};
  const result=prepareCanonicalWorkoutHistory(origin,origin,JSON.stringify(journal),issued);
  const packaged=send('prepareIssuedWorkoutHistory',{originSnapshot:origin,currentSnapshot:origin,finishedJournal:JSON.stringify(journal),issuedWorkout:issued});
  assert.equal(packaged.ok,true);assert.deepEqual(packaged.result,result);
  return {origin,plan,issued,start,journal,result};
}
function edit(raw,changes) {
  const before=inspectNativeHistory(raw,'workout-failed');
  const request=JSON.stringify({format:2,operationId:`failure-edit-${++operation}`,correctedAt:70000+operation,
    snapshotSha256:before.snapshotSha256,entrySha256:before.entrySha256,historyId:before.historyId,changes});
  const out=prepareNativeHistoryCorrection(raw,request),wire=send('prepareNativeHistoryCorrection',{currentSnapshot:raw,correctionJson:request});
  assert.equal(wire.ok,true);assert.deepEqual(wire.result,out);
  const retry=prepareNativeHistoryCorrection(out.candidateSnapshot,request);
  assert.equal(retry.alreadyPresent,true);assert.equal(retry.candidateSnapshot,out.candidateSnapshot);
  const conflict=JSON.parse(request);conflict.correctedAt++;
  assert.throws(()=>prepareNativeHistoryCorrection(out.candidateSnapshot,JSON.stringify(conflict)));
  const a=JSON.parse(raw).history[0],b=JSON.parse(out.candidateSnapshot).history[0];
  assert.deepEqual(b.nativeCompletion,a.nativeCompletion);assert.equal(b.date,a.date);assert.equal(b.totalSets,a.totalSets);
  assert.deepEqual(JSON.parse(out.candidateSnapshot).saved,JSON.parse(raw).saved);
  return out;
}
const completed=(row,unit,load,reps,rir=null)=>({slot:row.slot,disposition:'COMPLETED',values:{load,unit,reps,rir}});
const skip=row=>({slot:row.slot,disposition:'SKIPPED',values:null});
for(const [unit,mixed,tech,duplicate] of [['kg',false,null,false],['lb',true,null,false],['kg',false,'drop set',true]]) {
  const f=fixture(unit,mixed,tech,duplicate),id=f.plan.rows[0].id;
  assert.equal(f.plan.allowFailedAttempts,true);assert.equal(f.result.historyEntry.nativeHistoryBasis.allowFailedAttempts,true);
  let raw=f.result.candidateSnapshot,view=inspectNativeHistory(raw,'workout-failed');
  const failed=view.editRows.find(row=>row.values?.reps===0);assert.ok(failed && failed.allowFailedAttempt);
  const before=JSON.parse(raw).history[0],originalFailures=before.perf[id].sets.filter(set=>set.failedAttempt);
  assert.ok(originalFailures.length>=1);
  assert.equal(inspectNativeHistoryPage(raw,JSON.stringify({offset:0,snapshotSha256:null})).entries[0].detailsAvailable,true);
  assert.deepEqual(send('inspectNativeHistory',{rawSnapshot:raw,historyId:'workout-failed'}).result,view);
  const revised=edit(raw,[completed(failed,unit,'85',0,0)]);raw=revised.candidateSnapshot;view=revised.history;
  const entry=JSON.parse(raw).history[0],row=entry.perf[id].sets.find(set=>set.decisionTrace.slot===failed.slot.exerciseIndex && set.workingSetIndex===failed.slot.setIndex);
  assert.equal(row.r,0);assert.equal(row.w,85);assert.equal(row.failedAttempt,true);assert.equal(row.decisionTrace.applied.reps,0);
  assert.equal(entry.volume,before.volume,'a failed attempt contributes no invented repetition or tonnage');
  assert.equal(attemptedHistorySets(entry.perf[id]).filter(set=>set.r===0).length,originalFailures.length);
  if(!mixed && !duplicate) {
    assert.equal(entry.perf[id].reps,0);assert.equal(entry.perf[id].weight,85);
    assert.equal(exRecords(parseStoredData(raw).history,id,unit).maxWeight,null,'failed attempts cannot become a working-set PR');
  }
  const target=f.plan.rows[0].sets.filter(set=>!set.warm)[failed.slot.setIndex].target.decisionTrace;
  const {applied,...trace}=row.decisionTrace;assert.deepEqual(trace,target);
  const successful=edit(raw,[completed(view.editRows.find(r=>slotKey(r.slot)===slotKey(failed.slot)),unit,'45',4)]);
  raw=successful.candidateSnapshot;view=successful.history;
  const positive=JSON.parse(raw).history[0].perf[id].sets.find(set=>set.decisionTrace.slot===failed.slot.exerciseIndex && set.workingSetIndex===failed.slot.setIndex);
  assert.equal(positive.r,4);assert.ok(!positive.failedAttempt);
  assert.equal(JSON.parse(raw).history[0].volume,entry.volume+45*4);
  const back=edit(raw,[completed(view.editRows.find(r=>slotKey(r.slot)===slotKey(failed.slot)),unit,'90',0)]);
  raw=back.candidateSnapshot;view=back.history;
  assert.equal(JSON.parse(raw).history[0].volume,entry.volume);
  const skipped=edit(raw,[skip(view.editRows.find(r=>slotKey(r.slot)===slotKey(failed.slot)))]);
  assert.equal(skipped.history.setsDone,view.setsDone-1);
  const restored=edit(skipped.candidateSnapshot,[completed(skipped.history.editRows.find(r=>slotKey(r.slot)===slotKey(failed.slot)),unit,'95',0)]);
  assert.equal(restored.history.setsDone,view.setsDone);
  if(tech) {
    const extension=restored.history.editRows.find(r=>r.kind!=='working');assert.ok(extension && !extension.allowFailedAttempt);
    assert.throws(()=>edit(restored.candidateSnapshot,[completed(extension,unit,'50',0)]));
  }
  assert.throws(()=>edit(restored.candidateSnapshot,[completed(failed,unit,'0',0)]));
  assert.throws(()=>edit(restored.candidateSnapshot,[completed(failed,unit,'95',-1)]));
  // Legacy positive-only correction commands must preserve other failed rows.
  const current=restored.history,positiveExercise=current.exercises.find(ex=>ex.sets.some(set=>set.reps>0));
  const set=positiveExercise.sets.find(set=>set.reps>0);
  const v1=JSON.stringify({operationId:`legacy-${++operation}`,correctedAt:80000,snapshotSha256:current.snapshotSha256,
    entrySha256:current.entrySha256,historyId:current.historyId,changes:[{exerciseId:positiveExercise.id,setIndex:set.setIndex,load:'35',reps:5,rir:null}]});
  const legacy=prepareNativeHistoryCorrection(restored.candidateSnapshot,v1);
  assert.ok(JSON.parse(legacy.candidateSnapshot).history[0].perf[id].sets.some(s=>s.r===0 && s.failedAttempt));
  // Existing format-1 bases keep their exact completion acknowledgement.
  const old=JSON.parse(f.result.candidateSnapshot);old.history[0].nativeHistoryBasis.format=1;
  delete old.history[0].nativeHistoryBasis.allowFailedAttempts;
  const oldRaw=JSON.stringify(old),ack=prepareCanonicalWorkoutHistory(f.origin,oldRaw,JSON.stringify(f.journal),f.issued);
  assert.equal(ack.alreadyPresent,true);assert.equal(ack.candidateSnapshot,oldRaw);
  const oldView=inspectNativeHistory(oldRaw,'workout-failed');assert.ok(oldView.exercises.some(ex=>ex.sets.some(set=>set.failedAttempt)));
  assert.ok(oldView.editRows.every(r=>!r.allowFailedAttempt));
}
// Ordinary source programs and their original targets cannot acquire the capability.
const f=fixture(),ordinary=JSON.parse(f.origin);delete ordinary.saved[0].config.percentScheme;
const normal=prepareNativeWorkout(JSON.stringify(ordinary),f.start);assert.ok(!normal.allowFailedAttempts);
const forged=structuredClone(normal);forged.allowFailedAttempts=true;
assert.throws(()=>issuedNativeWorkoutFrom(JSON.stringify(ordinary),JSON.stringify(forged)));
const slots=issuedNativeWorkoutFrom(JSON.stringify(ordinary),JSON.stringify(normal)).slots;
const journal={...f.journal,session:normal.session,slots,events:slots.map((slot,i)=>({operationId:`normal-${i}`,slot,
  disposition:'COMPLETED',values:{load:'40',unit:'kg',reps:6,rir:null},fromRevision:i,toRevision:i+1})),
  finish:{...f.journal.finish,fromRevision:slots.length,toRevision:slots.length+1}};
const initial=prepareCanonicalWorkoutHistory(JSON.stringify(ordinary),JSON.stringify(ordinary),JSON.stringify(journal),JSON.stringify(normal));
const normalView=inspectNativeHistory(initial.candidateSnapshot,'workout-failed');
assert.ok(normalView.editRows.every(row=>!row.allowFailedAttempt));
assert.throws(()=>edit(initial.candidateSnapshot,[completed(normalView.editRows[0],'kg','40',0)]));
// Check the real next percentage decision reads failures without earning a load increase.
const initialHistory=f.result.historyEntry,program=JSON.parse(f.origin).saved[0],day=program.days[0],id=f.plan.rows[0].id;
const next=percentagePlanFor(program,day,EX_BY_ID[id],0,1,'kg',[initialHistory]);
assert.ok(next && next.sets.every(set=>set.weight<=80));
console.log('PASS native failed-attempt lifecycle'+(process.argv[2]?' through actual Android IIFE':'')+
  ': exact zero reps, no invented volume/PR, original positions/targets, skip/positive/failed corrections, protocol-scoped permission, legacy acknowledgement and next-session replay');
