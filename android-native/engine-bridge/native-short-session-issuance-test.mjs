import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { parseStoredData,nextSessionCursor,planShorterSession,computeCell } from '../../modules/engine-api.js';
import { finishedIssuedJournal } from './native-workout-test.mjs';

const settings={creationId:'short-creation',programId:'short-program',createdAt:1000,seed:223,
  profile:{name:'Short issued training',goal:'hypertrophy',experience:'beginner',unit:'lb',
    split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
    allowBodyweight:true,allowSupersets:true,barbellCap:3}};
let checked=0,refused=0;
for(const seed of [21,223,782,947]) {
  const snapshot=prepareNativeProgram(JSON.stringify({...settings,seed})).snapshot;
  const store=parseStoredData(snapshot),program=store.saved[0];
  const next=nextSessionCursor(program,store.history),day=program.days[next.dayIndex];
  const full=prepareNativeWorkout(snapshot,JSON.stringify({programId:program.id,
    workoutId:`full-${seed}`,startedAt:2000}));
  assert.equal(full.format,1);
  for(const minutes of [45,30,20]) {
    const expected=planShorterSession(program,day,next.weekIndex,minutes);
    const start=JSON.stringify({programId:program.id,workoutId:`short-${seed}-${minutes}`,
      startedAt:2000,minutes});
    if(!expected.fits) {
      assert.throws(()=>prepareNativeWorkout(snapshot,start));
      refused++;continue;
    }
    const issued=prepareNativeWorkout(snapshot,start);
    assert.equal(issued.format,2);
    assert.deepEqual(issued.sessionLengthPlan,expected);
    assert.deepEqual(issuedNativeWorkoutFrom(snapshot,JSON.stringify(issued)).plan,issued);
    const selected=issued.rows.map(row=>({id:row.id,slot:row.slot}));
    assert.deepEqual(selected,day.exercises.flatMap((_,slot)=>
      computeCell(program,{...day,sessionLengthPlan:expected},day.exercises[slot],slot,next.weekIndex).sets>0
        ? [{id:issued.rows.find(row=>row.slot===slot)?.id,slot}] : []));
    const finished=JSON.stringify(finishedIssuedJournal(issued,{skipSecond:false}));
    const prepared=prepareCanonicalWorkoutHistory(snapshot,snapshot,finished,JSON.stringify(issued));
    assert.deepEqual(prepared.historyEntry.sessionLengthPlan,expected);
    assert.equal(prepared.historyEntry.timeLimited,expected.changed);
    assert.equal(prepared.historyEntry.prescriptionEdited,expected.changed);
    assert.equal(prepareCanonicalWorkoutHistory(snapshot,prepared.candidateSnapshot,
      finished,JSON.stringify(issued)).alreadyPresent,true);
    const forged=structuredClone(issued);
    forged.sessionLengthPlan.slots[Object.keys(forged.sessionLengthPlan.slots)[0]].sets=0;
    assert.throws(()=>issuedNativeWorkoutFrom(snapshot,JSON.stringify(forged)));
    const corrupted=structuredClone(issued);
    corrupted.rows[0].sets.pop();
    assert.throws(()=>prepareCanonicalWorkoutHistory(snapshot,snapshot,finished,JSON.stringify(corrupted)));
    const request=JSON.stringify({protocolVersion:1,id:'short-issue',method:'prepareNativeWorkout',
      params:{rawSnapshot:snapshot,startJson:start}});
    assert.deepEqual(JSON.parse(dispatchWorkoutStoreJson(request)).result,issued);
    checked++;
  }
}
assert.ok(checked>0,'at least one genuinely shortened training day must finish');
assert.ok(refused>0,'unfittable time requests must fail closed');
console.log(`PASS native shortened issuance and canonical completion: ${checked} issued, ${refused} refused`);
