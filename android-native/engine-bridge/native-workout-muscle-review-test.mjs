import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,inspectNativeWorkoutMuscles } from './native-workout.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';
import { EX_BY_ID,secondaryOf } from '../../modules/engine-api.js';
const p={name:'Native muscle review',goal:'both',experience:'intermediate',
  unit:'lb',split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
  allowBodyweight:true,allowSupersets:true,barbellCap:3};
let examined=0;
for(const split of ['full_body','upper_lower','ppl','ulppl']) {
  const snapshot=prepareNativeProgram(JSON.stringify({
    creationId:'create-muscles',programId:'program-muscles',createdAt:1000,seed:223,
    profile:{...p,split,days:split==='full_body'?3:split==='ppl'?3:split==='upper_lower'?4:5}
  })).snapshot;
  const issued=JSON.stringify(prepareNativeWorkout(snapshot,JSON.stringify({
    programId:'program-muscles',workoutId:'muscle-review',startedAt:2000})));
  const reviewed=inspectNativeWorkoutMuscles(snapshot,issued);
  assert.equal(reviewed.readOnly,true);assert.equal(reviewed.applied,false);
  assert.equal(reviewed.workoutId,'muscle-review');
  const rows=JSON.parse(issued).rows;
  assert.equal(reviewed.rows.length,rows.length);
  for(const row of reviewed.rows) {
    const ex=EX_BY_ID[row.exerciseId];
    assert.equal(row.primaryPart,ex.part);
    const expected=[...new Set([ex.part,...secondaryOf(ex,JSON.parse(snapshot).saved[0].engineV).map(([part])=>part)])].sort();
    assert.deepEqual(row.involvedParts,expected);
    assert.equal(row.exerciseIndex,rows.find(r=>r.id===row.exerciseId && r.slot===row.exerciseIndex).slot);
  }
  assert.deepEqual(reviewed.parts,[...new Set(reviewed.rows.flatMap(r=>r.involvedParts))].sort());
  const wire=JSON.stringify({protocolVersion:1,id:'muscle-review',method:'inspectNativeWorkoutMuscles',
    params:{rawSnapshot:snapshot,issuedWorkout:issued}});
  assert.deepEqual(JSON.parse(dispatchWorkoutStoreJson(wire)).result,reviewed);
  assert.equal(JSON.parse(dispatchEngineJson(wire)).ok,false);
  assert.equal(JSON.parse(dispatchBackupSchemaJson(wire)).ok,false);
  const wrong=JSON.parse(issued);wrong.rows[0].id='forged';
  assert.throws(()=>inspectNativeWorkoutMuscles(snapshot,JSON.stringify(wrong)));
  const fake=JSON.stringify({protocolVersion:1,id:'muscle-review',method:'inspectNativeWorkoutMuscles',
    params:{rawSnapshot:snapshot,issuedWorkout:issued,skipSets:true}});
  assert.equal(JSON.parse(dispatchWorkoutStoreJson(fake)).ok,false);
  examined++;
}
console.log(`PASS non-mutating canonical muscle involvement: ${examined} split sessions, primary/secondary sources, forged issued inputs and channel isolation`);
