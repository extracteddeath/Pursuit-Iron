import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { nativeProgramOptions,prepareNativeProgram } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';
import { dispatchWorkoutStoreJson as sourceDispatch } from './workout-store-protocol.mjs';

let dispatch=sourceDispatch;
if(process.argv[2]) {
  const context=vm.createContext({});vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  dispatch=context.PursuitIronBridge.dispatchWorkoutStoreJson;
}
const clone=v=>JSON.parse(JSON.stringify(v));
function journalFor(plan) {
  const slots=issuedNativeWorkoutFrom(current,JSON.stringify(plan)).slots;
  return {format:2,session:plan.session,slots,startRevision:0,events:slots.map((slot,i)=>({operationId:`work-${i}`,slot,
    disposition:i===1?'SKIPPED':'COMPLETED',values:i===1?null:{load:'100',unit:plan.session.unit,reps:8,rir:2},fromRevision:i,toRevision:i+1})),
    finish:{operationId:'finish-warmups',fromRevision:slots.length,toRevision:slots.length+1,
      completedAt:plan.session.startedAt+60000,durationMs:60000}};
}
let current;
for(const [unit,seedHistory,repeated] of [['lb',true,false],['kg',false,false],['lb',false,true]]) {
  current=prepareNativeProgram(JSON.stringify({creationId:'warm-create',programId:'warm-program',createdAt:1000,seed:223,
    profile:{name:'Warm-up facts',goal:'both',experience:'intermediate',unit,split:'full_body',days:3,session:'s60',
      equipment:nativeProgramOptions().equipment,allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot;
  if(repeated) {
    const store=JSON.parse(current),p=store.saved[0],day=p.days[0];
    day.exercises[1]=day.exercises[0];p.overrides[`${day.id}:1`]=clone(p.overrides[`${day.id}:0`]);
    p.nextWeekPrescriptions[`${day.id}:1`]=clone(p.nextWeekPrescriptions[`${day.id}:0`]);
    current=JSON.stringify(store);
  }
  if(seedHistory)for(let rotation=0;rotation<3;rotation++) {
    const plan=prepareNativeWorkout(current,JSON.stringify({programId:'warm-program',workoutId:`seed-${rotation}`,startedAt:2000+rotation*70000}));
    current=prepareCanonicalWorkoutHistory(current,current,JSON.stringify(journalFor(plan)),JSON.stringify(plan)).candidateSnapshot;
  }
  const plan=prepareNativeWorkout(current,JSON.stringify({programId:'warm-program',workoutId:'warm-workout',startedAt:300000}));
  const issued=JSON.stringify(plan),base=journalFor(plan),without=prepareCanonicalWorkoutHistory(current,current,JSON.stringify(base),issued);
  const row=plan.rows[0],warmCount=row.sets.filter(s=>s.warm).length;
  if(seedHistory)assert.ok(warmCount>=2,'Real completed history must produce canonical warm-up targets');
  const location=index=>({exerciseIndex:row.slot,warmupIndex:index,exerciseId:row.id});
  const warmEvent=(operationId,index,values,at,replacesOperationId=null)=>({operationId,slot:location(index),
    disposition:values===null?'SKIPPED':'COMPLETED',values,recordedAt:at,replacesOperationId});
  const actual=(load,reps)=>({load,unit,reps,rir:null});
  const warmups=[warmEvent('warm-first',0,actual('40',8),301000),warmEvent('warm-skipped',1,null,302000),
    warmEvent('warm-added',Math.max(2,warmCount),actual('0',10),303000),
    warmEvent('warm-attempt',0,actual('80',0),304000,'warm-first')];
  if(repeated)warmups.push({...warmEvent('warm-repeated',0,actual('25',5),305000),
    slot:{exerciseIndex:plan.rows[1].slot,warmupIndex:0,exerciseId:row.id}});
  const journal={...clone(base),format:6,note:{operationId:'warm-note',text:'Keep the session note',updatedAt:306000,replacesOperationId:null},
    events:base.events.map(e=>({...e,replacesOperationId:null,adjustment:null})),warmups};
  const params=j=>({originSnapshot:current,currentSnapshot:current,finishedJournal:JSON.stringify(j),issuedWorkout:issued});
  const send=j=>JSON.stringify({protocolVersion:1,id:'warmup-proof',method:'prepareIssuedWorkoutHistory',params:params(j)});
  const result=JSON.parse(dispatch(send(journal)));assert.equal(result.ok,true);
  const saved=result.result,entry=saved.historyEntry,baseline=without.historyEntry;
  for(const key of ['setsDone','totalSets','volume','perf','nativeSetObservations','nativeSetLocations','nativeHistoryBasis'])
    assert.deepEqual(entry[key],baseline[key],key+' cannot gain training credit from warm-ups');
  assert.deepEqual(entry.nativeWarmups.events,warmups);
  assert.deepEqual(entry.nativeWarmups.exercises[0].targets,row.sets.filter(s=>s.warm),'Original targets survive verbatim');
  assert.equal(entry.nativeSessionNote.text,'Keep the session note');
  const unlogged=JSON.parse(dispatch(send({...journal,warmups:[],note:null})));
  assert.equal(unlogged.ok,true);
  assert.equal(unlogged.result.historyEntry.nativeWarmups.events.length,0);
  assert.ok(inspectNativeHistory(unlogged.result.candidateSnapshot,'warm-workout').warmups.every(row=>row.disposition===null));
  assert.deepEqual(unlogged.result.historyEntry.perf,baseline.perf);
  let history=inspectNativeHistory(saved.candidateSnapshot,'warm-workout');
  const first=history.warmups.find(w=>w.slot.exerciseIndex===row.slot && w.slot.warmupIndex===0);
  assert.equal(first.values.reps,0);assert.equal(first.values.load,'80');
  assert.ok(history.warmups.some(w=>w.disposition==='SKIPPED'));
  assert.ok(history.warmups.some(w=>w.values?.load==='0'));
  if(seedHistory)assert.ok(history.warmups.some(w=>w.disposition===null),'Unlogged issued warm-ups remain unlogged');
  if(repeated)assert.equal(history.warmups.filter(w=>w.slot.exerciseId===row.id && w.slot.warmupIndex===0).length,2);
  const correction=JSON.stringify({format:3,operationId:'correct-warmup',snapshotSha256:history.snapshotSha256,
    historyId:history.historyId,entrySha256:history.entrySha256,correctedAt:370000,
    changes:[{slot:first.slot,disposition:'COMPLETED',values:actual('500',12)}]});
  const corrected=prepareNativeHistoryCorrection(saved.candidateSnapshot,correction),beforeStore=JSON.parse(saved.candidateSnapshot),afterStore=JSON.parse(corrected.candidateSnapshot);
  assert.deepEqual(afterStore.perf,beforeStore.perf,'A false high warm-up never becomes progression evidence');
  assert.deepEqual(corrected.history.exercises,history.exercises,'Working records and original set positions are unchanged');
  const nextStart=JSON.stringify({programId:'warm-program',workoutId:'next-after-warmups',startedAt:390000});
  assert.deepEqual(prepareNativeWorkout(saved.candidateSnapshot,nextStart).rows,
    prepareNativeWorkout(corrected.candidateSnapshot,nextStart).rows,'A warm-up correction cannot raise the next working target');

  assert.deepEqual(afterStore.history.filter(h=>h.id!=='warm-workout'),beforeStore.history.filter(h=>h.id!=='warm-workout'));
  for(const key of Object.keys(entry).filter(k=>!['nativeWarmups','updatedAt','nativeCorrection'].includes(k)))
    assert.deepEqual(afterStore.history.find(h=>h.id==='warm-workout')[key],entry[key]);
  assert.equal(corrected.history.warmups.find(w=>JSON.stringify(w.slot)===JSON.stringify(first.slot)).values.load,'500');
  assert.equal(prepareNativeHistoryCorrection(corrected.candidateSnapshot,correction).alreadyPresent,true);
  assert.throws(()=>prepareNativeHistoryCorrection(corrected.candidateSnapshot,correction.replace('"500"','"501"')));
  history=corrected.history;
  const working=history.editRows.find(r=>r.retained);
  const workEdit=JSON.stringify({format:2,operationId:'work-after-warmup',snapshotSha256:history.snapshotSha256,
    historyId:history.historyId,entrySha256:history.entrySha256,correctedAt:380000,
    changes:[{slot:working.slot,disposition:'COMPLETED',values:{...working.values,load:'90'}}]});
  const workResult=prepareNativeHistoryCorrection(corrected.candidateSnapshot,workEdit);
  assert.deepEqual(JSON.parse(workResult.candidateSnapshot).history.find(h=>h.id==='warm-workout').nativeWarmups,
    afterStore.history.find(h=>h.id==='warm-workout').nativeWarmups,'A later working correction retains warm-up evidence');
  for(const mutate of [j=>j.warmups[0].slot.exerciseId='foreign',j=>j.warmups[0].slot.exerciseIndex=47,
    j=>j.warmups[0].slot.warmupIndex=19,j=>j.warmups[0].values.unit=unit==='lb'?'kg':'lb',
    j=>j.warmups[0].values.reps=-1,j=>j.warmups[0].values={...actual('0',0)},
    j=>j.warmups[0].recordedAt=299999,j=>j.warmups[0].recordedAt=400000,
    j=>j.warmups[0].operationId=j.events[0].operationId,j=>j.warmups[3].replacesOperationId='stale',
    j=>j.warmups.push(clone(j.warmups[0])),j=>delete j.warmups]) {
    const altered=clone(journal);mutate(altered);
    assert.equal(JSON.parse(dispatch(send(altered))).ok,false);
  }
}
console.log('PASS native warm-ups: real issued targets, added/zero-load/skipped/failed observations, repeated slots, retained audits/corrections, and unchanged working counts/volume/progression');
