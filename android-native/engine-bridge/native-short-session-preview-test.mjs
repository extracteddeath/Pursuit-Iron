import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { previewNativeShorterSession } from './native-workout.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';
import { parseStoredData,nextSessionCursor,planShorterSession } from '../../modules/engine-api.js';
const setup=JSON.stringify({creationId:'create-short',programId:'program-short',createdAt:1000,seed:223,
  profile:{name:'Short session preview',goal:'both',experience:'intermediate',
    unit:'lb',split:'full_body',days:3,session:'s90',
    equipment:nativeProgramOptions().equipment,allowBodyweight:true,allowSupersets:true,barbellCap:3}});
const source=prepareNativeProgram(setup).snapshot,stored=parseStoredData(source);
const program=stored.saved[0],cursor=nextSessionCursor(program,stored.history);
for(const minutes of [45,30,20]) {
  const preview=previewNativeShorterSession(source,program.id,minutes);
  const expected=planShorterSession(program,program.days[cursor.dayIndex],cursor.weekIndex,minutes);
  assert.equal(preview.source,'modules/engine-api.js:planShorterSession');
  assert.equal(preview.readOnly,true);assert.equal(preview.applied,false);
  assert.equal(preview.programId,program.id);assert.equal(preview.minutes,minutes);
  assert.equal(preview.fits,expected.fits);assert.equal(preview.changed,expected.changed);
  assert.deepEqual(preview.cuts,expected.cuts);
  assert.equal(preview.estimatedMinutes,expected.estimatedMinutes);
  const request=JSON.stringify({protocolVersion:1,id:'short-preview',method:'previewNativeShorterSession',
    params:{rawSnapshot:source,programId:program.id,minutes}});
  const response=JSON.parse(dispatchWorkoutStoreJson(request));
  assert.equal(response.ok,true);assert.deepEqual(response.result,preview);
  assert.equal(JSON.parse(dispatchEngineJson(request)).ok,false);
  assert.equal(JSON.parse(dispatchBackupSchemaJson(request)).ok,false);
}
assert.equal(JSON.stringify(stored),JSON.stringify(parseStoredData(source)),
  'projections cannot change a saved program');
for(const minutes of [0,19,35,60,null])assert.throws(()=>previewNativeShorterSession(source,program.id,minutes));
assert.throws(()=>previewNativeShorterSession(source,'unknown-program',30));
const malformed=JSON.stringify({protocolVersion:1,id:'reject',method:'previewNativeShorterSession',
  params:{rawSnapshot:source,programId:program.id,minutes:30,apply:true}});
assert.equal(JSON.parse(dispatchWorkoutStoreJson(malformed)).ok,false);
console.log('PASS canonical short-session previews: real next-session cursor, 45/30/20 engine parity, no writes, strict method and channel isolation');
