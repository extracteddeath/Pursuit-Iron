import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { nativeProgramOptions,prepareNativeProgram } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';
import { validNativeNoteText } from './native-session-note.mjs';
import { dispatchWorkoutStoreJson as sourceDispatch } from './workout-store-protocol.mjs';

let dispatch=sourceDispatch;
if(process.argv[2]) {
  const context=vm.createContext({});vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  dispatch=context.PursuitIronBridge.dispatchWorkoutStoreJson;
}
const origin=prepareNativeProgram(JSON.stringify({creationId:'create-note',programId:'program-note',
  createdAt:1000,seed:223,profile:{name:'Session note proof',goal:'both',experience:'intermediate',unit:'lb',
    split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
    allowBodyweight:true,allowSupersets:true,barbellCap:3}})).snapshot;
const plan=prepareNativeWorkout(origin,JSON.stringify({programId:'program-note',workoutId:'workout-note',startedAt:2000}));
const issued=JSON.stringify(plan),slots=issuedNativeWorkoutFrom(origin,issued).slots;
const base={format:2,session:plan.session,slots,startRevision:0,
  events:slots.map((slot,i)=>({operationId:`note-set-${i}`,slot,
    disposition:i===1?'SKIPPED':'COMPLETED',values:i===1?null:{load:'40',unit:'lb',reps:6,rir:2},
    fromRevision:i,toRevision:i+1})),
  finish:{operationId:'finish-note',fromRevision:slots.length,toRevision:slots.length+1,completedAt:62000,durationMs:60000}};
const without=prepareCanonicalWorkoutHistory(origin,origin,JSON.stringify(base),issued);
const withNote=text=>({...structuredClone(base),format:5,
  events:base.events.map(e=>({...e,replacesOperationId:null,adjustment:null})),
  note:{operationId:'session-note-one',text,updatedAt:3000,replacesOperationId:null}});
const params=journal=>({originSnapshot:origin,currentSnapshot:origin,issuedWorkout:issued,finishedJournal:JSON.stringify(journal)});
const send=journal=>JSON.stringify({protocolVersion:1,id:'note-wire',method:'prepareIssuedWorkoutHistory',params:params(journal)});
for(const text of ['Seat position 4\nFelt strong today. 🏋️','']) {
  const journal=withNote(text);
  const response=JSON.parse(dispatch(send(journal)));assert.equal(response.ok,true);
  const saved=response.result,entry=saved.historyEntry,baseline=without.historyEntry;
  assert.equal(entry.nativeSessionNote.text,text);
  for(const key of ['setsDone','totalSets','volume','perf','nativeSetObservations','nativeSetLocations','nativeHistoryBasis'])
    assert.deepEqual(entry[key],baseline[key],key+' cannot change because a note was saved');
  assert.ok(!Object.prototype.hasOwnProperty.call(entry,'nativeSetRevisions'));
  assert.ok(!Object.prototype.hasOwnProperty.call(entry,'nativeSetAdjustments'));
  const history=inspectNativeHistory(saved.candidateSnapshot,'workout-note');
  assert.equal(history.sessionNote,text);
  const first=history.editRows.find(r=>r.retained);
  const correction=JSON.stringify({format:2,operationId:'correct-with-note',snapshotSha256:history.snapshotSha256,
    historyId:history.historyId,entrySha256:history.entrySha256,correctedAt:63000,
    changes:[{slot:first.slot,disposition:'COMPLETED',values:{...first.values,load:'45'}}]});
  const corrected=prepareNativeHistoryCorrection(saved.candidateSnapshot,correction);
  assert.equal(corrected.history.sessionNote,text);
  assert.deepEqual(JSON.parse(corrected.candidateSnapshot).history[0].nativeSessionNote,entry.nativeSessionNote);
  const next=prepareNativeWorkout(corrected.candidateSnapshot,JSON.stringify({programId:'program-note',
    workoutId:'workout-after-note',startedAt:64000}));
  assert.ok(!Object.prototype.hasOwnProperty.call(next,'note'),'A session note does not become the next session note');
}
for(const mutate of [j=>j.note.text='x'.repeat(2001),j=>j.note.text='bad\u0000text',j=>j.note.text='\uD800',
  j=>j.note.updatedAt=1999,j=>j.note.updatedAt=62001,j=>j.note.operationId='finish-note',
  j=>j.note.operationId=j.events[0].operationId,j=>j.note.replacesOperationId=j.note.operationId,
  j=>j.note.extra=true,j=>delete j.note,j=>j.format=4]) {
  const journal=withNote('private-note-text');mutate(journal);
  const result=JSON.parse(dispatch(send(journal)));assert.equal(result.ok,false);
  assert.ok(!JSON.stringify(result).includes('private-note-text'));
}
assert.equal(validNativeNoteText('line\nnext\t🙂'),true);
console.log('PASS durable session-note handoff: bounded text, empty clearing, unchanged sets/records, retained corrections, next-session isolation and private errors');
