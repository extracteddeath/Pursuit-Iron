import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { nativeProgramOptions,prepareNativeProgram } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { pinnedExerciseContext } from './native-exercise-notes.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';
import { dispatchWorkoutStoreJson as sourceDispatch } from './workout-store-protocol.mjs';

let dispatch=sourceDispatch;
if(process.argv[2]) {
  const context=vm.createContext({});vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  dispatch=context.PursuitIronBridge.dispatchWorkoutStoreJson;
}
const source=JSON.parse(prepareNativeProgram(JSON.stringify({creationId:'create-pinned',programId:'program-pinned',
  createdAt:1000,seed:223,profile:{name:'Pinned facts proof',goal:'both',experience:'intermediate',unit:'lb',
    split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
    allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot);
const exerciseId=source.saved[0].days[0].exercises[0];
// Repeated slots and a recurring exercise retain one pinned context, with independent set locations.
source.saved[0].days.forEach(day=>{day.exercises[0]=exerciseId;day.exercises[1]=exerciseId;});
source.exNotes={[exerciseId]:'Old cue',unrelated:'Keep this note'};
source.exSetup={[exerciseId]:{legacySetting:'7'},unrelated:{seat:'9'}};
const origin=JSON.stringify(source);
const plan=prepareNativeWorkout(origin,JSON.stringify({programId:'program-pinned',workoutId:'workout-pinned',startedAt:2000}));
const issued=JSON.stringify(plan),slots=issuedNativeWorkoutFrom(origin,issued).slots;
assert.ok(plan.rows.filter(r=>r.id===exerciseId).length>=2);
const context=plan.rows[0].pinnedNote;
assert.equal(context.text,'Old cue');assert.deepEqual(context.setup,{legacySetting:'7'});
assert.ok(context.fields.some(f=>f.key==='legacySetting' && f.orphan));
const base={format:6,session:plan.session,slots,startRevision:0,note:null,warmups:[],
  events:slots.map((slot,i)=>({operationId:`pinned-set-${i}`,slot,disposition:'COMPLETED',
    values:{load:'40',unit:'lb',reps:6,rir:2},fromRevision:i,toRevision:i+1,replacesOperationId:null,adjustment:null})),
  finish:{operationId:'finish-pinned',fromRevision:slots.length,toRevision:slots.length+1,completedAt:62000,durationMs:60000}};
const baseline=prepareCanonicalWorkoutHistory(origin,origin,JSON.stringify(base),issued);
const first={operationId:'pinned-one',exerciseId,text:'Pause on chest 🏋️',setup:{legacySetting:'8'},updatedAt:3000,replacesOperationId:null};
const final={...first,operationId:'pinned-two',text:'Final cue\nKeep wrists stacked.',updatedAt:4000,replacesOperationId:first.operationId};
const journal={...base,format:7,exerciseNotes:[first,final]};
const params=j=>({originSnapshot:origin,currentSnapshot:origin,issuedWorkout:issued,finishedJournal:JSON.stringify(j)});
const send=(j,overrides={})=>JSON.parse(dispatch(JSON.stringify({protocolVersion:1,id:'pinned-wire',method:'prepareIssuedWorkoutHistory',
  params:{...params(j),...overrides}})));
for(const edits of [[first,final],[first,{...final,text:'',setup:{}}]]) {
  const j={...journal,exerciseNotes:edits},result=send(j);assert.equal(result.ok,true);
  const saved=result.result,store=JSON.parse(saved.candidateSnapshot),last=edits.at(-1);
  assert.equal(store.exNotes[exerciseId],last.text);assert.deepEqual(store.exSetup[exerciseId],last.setup);
  assert.equal(store.exNotes.unrelated,'Keep this note');assert.deepEqual(store.exSetup.unrelated,{seat:'9'});
  assert.deepEqual(saved.historyEntry.nativeExerciseNotes,{format:1,edits});
  for(const key of ['setsDone','totalSets','volume','perf','nativeSetObservations','nativeSetLocations','nativeHistoryBasis','nativeWarmups'])
    assert.deepEqual(saved.historyEntry[key],baseline.historyEntry[key],key+' is independent of pinned facts');
  const retry=send(j,{currentSnapshot:saved.candidateSnapshot});assert.equal(retry.ok,true);
  assert.equal(retry.result.alreadyPresent,true);assert.equal(retry.result.candidateSnapshot,saved.candidateSnapshot);
  const next=prepareNativeWorkout(saved.candidateSnapshot,JSON.stringify({programId:'program-pinned',workoutId:'pinned-next',startedAt:63000}));
  assert.equal(next.rows[0].pinnedNote.text,last.text);assert.deepEqual(next.rows[0].pinnedNote.setup,last.setup);
  const history=inspectNativeHistory(saved.candidateSnapshot,'workout-pinned'),row=history.editRows.find(r=>r.retained);
  const corrected=prepareNativeHistoryCorrection(saved.candidateSnapshot,JSON.stringify({format:2,operationId:'pinned-correction',
    snapshotSha256:history.snapshotSha256,historyId:history.historyId,entrySha256:history.entrySha256,correctedAt:64000,
    changes:[{slot:row.slot,disposition:'COMPLETED',values:{...row.values,load:'45'}}]}));
  const correctedStore=JSON.parse(corrected.candidateSnapshot);
  assert.deepEqual(correctedStore.exNotes,store.exNotes);assert.deepEqual(correctedStore.exSetup,store.exSetup);
  assert.deepEqual(correctedStore.history[0].nativeExerciseNotes,{format:1,edits});
}
for(const mutate of [j=>j.exerciseNotes[1].replacesOperationId=null,j=>j.exerciseNotes[1].updatedAt=3000,
  j=>j.exerciseNotes[0].operationId='finish-pinned',j=>j.exerciseNotes[0].operationId=j.events[0].operationId,
  j=>j.exerciseNotes[0].text='x'.repeat(2001),j=>j.exerciseNotes[0].text='\uD800',
  j=>j.exerciseNotes[0].setup={invented:'3'},j=>j.exerciseNotes[0].setup={legacySetting:'x'.repeat(201)},
  j=>j.exerciseNotes[0].updatedAt=1999,j=>j.exerciseNotes[1].updatedAt=62001,
  j=>j.exerciseNotes[0].exerciseId='not-issued',j=>j.exerciseNotes=[],j=>j.exerciseNotes[0].extra=true,
  j=>j.note={operationId:first.operationId,text:'collision',updatedAt:3000,replacesOperationId:null}]) {
  const changed=structuredClone(journal);mutate(changed);assert.equal(send(changed).ok,false);
}
const forged=structuredClone(plan);forged.rows[0].pinnedNote.text='Forged base';
assert.equal(send(journal,{issuedWorkout:JSON.stringify(forged)}).ok,false);
const legacy=structuredClone(plan);legacy.rows.forEach(row=>delete row.pinnedNote);
assert.doesNotThrow(()=>issuedNativeWorkoutFrom(origin,JSON.stringify(legacy)));
assert.equal(send(journal,{issuedWorkout:JSON.stringify(legacy)}).ok,false);
assert.equal(pinnedExerciseContext({...source,exNotes:{[exerciseId]:'x'.repeat(2001)}},exerciseId),null);
assert.equal(pinnedExerciseContext({...source,exNotes:{[exerciseId]:null}},exerciseId),null);
console.log('PASS pinned exercise notes/setup: verified bases, repeated slots, exact retries, explicit clears, retained history corrections, next-workout carryover and unchanged training facts');
