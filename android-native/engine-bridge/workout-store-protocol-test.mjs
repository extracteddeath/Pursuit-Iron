import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createHash } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import { sha256Utf8,utf8ByteLength,equalJson } from './json-identity.mjs';
import { dispatchWorkoutStoreJson,WORKOUT_STORE_LIMITS as limits } from './workout-store-protocol.mjs';
import { dispatchBackupSchemaJson,dispatchEngineJson } from './protocol.mjs';

const digest=v=>createHash('sha256').update(v,'utf8').digest('hex');
assert.equal(sha256Utf8(''),'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
assert.equal(sha256Utf8('abc'),'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
assert.equal(sha256Utf8('a'.repeat(1000000)),
  'cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0');
let seed=223;
const next=()=>seed=(Math.imul(seed,1664525)+1013904223)>>>0;
const strings=['Español — 日本語 🏋️','\ud800','\udc00','x\ud800y','\u0000\u2028\u2029',
  'é'.repeat(600000),'😀'.repeat(1000)];
for (let length=0;length<145;length++) strings.push('x'.repeat(length));
for (let n=0;n<100;n++) strings.push(String.fromCharCode(
  ...Array.from({length:next()%1024},()=>next()%65536)));
for (const text of strings) {
  assert.equal(utf8ByteLength(text),Buffer.byteLength(text,'utf8'));
  assert.equal(sha256Utf8(text),digest(text),'portable UTF-8 SHA must match native crypto');
}
for (const [a,b] of [[{a:1,b:[null,true]},{b:[null,true],a:1}],
  [{a:1},{a:'1'}],[[-0],[0]],[[1,2],[2,1]], [{a:null},{}],
  [JSON.parse('{"__proto__":{"x":1}}'),{}]])
  assert.equal(equalJson(a,b),isDeepStrictEqual(a,b));

const f=JSON.parse(fs.readFileSync('android-native/domain-contract/src/test/resources/finished-journal-golden.json','utf8'));
const call=(method,params)=>({protocolVersion:1,id:'owned-store-test',method,params});
const send=r=>JSON.parse(dispatchWorkoutStoreJson(JSON.stringify(r)));
const source={rawSnapshot:f.rawSnapshot};
const validation=send(call('validateOwnedStore',source));
assert.equal(validation.ok,true);
assert.equal(validation.result.validated,true);
assert.equal(validation.result.snapshotSha256,digest(f.rawSnapshot));
assert.equal(validation.result.approvedForWorkoutExecution,false);
const params={originSnapshot:f.rawSnapshot,currentSnapshot:f.rawSnapshot,finishedJournal:JSON.stringify(f.handoff)};
const preparation=send(call('prepareWorkoutHistory',params));
assert.deepEqual(preparation.result,f.preparation,'portable runtime matches original native-crypto canonical golden result');
const retry=send(call('prepareWorkoutHistory',{...params,currentSnapshot:preparation.result.candidateSnapshot}));
assert.equal(retry.ok,true);assert.equal(retry.result.alreadyPresent,true);
assert.equal(retry.result.candidateSnapshot,preparation.result.candidateSnapshot);
for (const input of [
  call('deleteHistory',{}),call('prepareWorkoutHistory',{...params,extra:true}),
  call('validateOwnedStore',{rawSnapshot:'{"v":99,"secret":"private-source"}'}),
  call('validateOwnedStore',{rawSnapshot:'x'.repeat(limits.snapshotBytes+1)}),
  call('validateOwnedStore',{rawSnapshot:'é'.repeat(limits.snapshotBytes)}),
  call('prepareWorkoutHistory',{...params,finishedJournal:'x'.repeat(limits.journalBytes+1)}),
  {...call('validateOwnedStore',source),protocolVersion:2},
  {...call('validateOwnedStore',source),id:''},
  {...call('validateOwnedStore',source),hiddenWrite:true}
]) {
  const rejected=send(input);
  assert.equal(rejected.ok,false);
  assert.ok(!JSON.stringify(rejected).includes('private-source'));
}
for (const dispatcher of [dispatchEngineJson,dispatchBackupSchemaJson]) {
  assert.equal(JSON.parse(dispatcher(JSON.stringify(call('prepareWorkoutHistory',params)))).ok,false,
    'normal/backup channels must never inherit private workout-store capability');
  assert.equal(JSON.parse(dispatcher(JSON.stringify(call('validateOwnedStore',source)))).ok,false);
}
assert.equal(JSON.parse(dispatchWorkoutStoreJson('invalid')).ok,false);
console.log('PASS portable SHA-256/UTF-8 against native crypto, canonical completion parity, bounded store protocol, retries and backup-channel separation');
