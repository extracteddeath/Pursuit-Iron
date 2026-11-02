import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mergeStores, parseStoredData } from '../../modules/engine-api.js';
import { previewCanonicalBackupMerge } from './backup-merge-preview.mjs';
import { prepareCanonicalBackupMerge } from './backup-merge-preparation.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchBackupSchemaJson,dispatchEngineJson } from './protocol.mjs';

const sha=text=>createHash('sha256').update(text,'utf8').digest('hex');
const history=(id,w,clock)=>({
  id,date:clock+1000,updatedAt:clock,
  programId:'synthetic',dayId:'a',perf:{bench:{weight:w,reps:8,sets:[{w,r:8}]}},
  setsDone:1,volume:8*w
});
const raw=(savedAt,historyRows=[],tombs={})=>JSON.stringify({
  v:13,savedAt,saved:[],history:historyRows,cycles:[],custom:[],gyms:[],
  perf:{},tombs
});
const current=raw(100,[history('old',100,10)]);
const incoming=raw(110,[history('new',120,20)]);
const before=[current,incoming];
const preview=previewCanonicalBackupMerge(current,incoming);
assert.deepEqual(preview.flags,[]);
const params={currentSnapshot:current,incomingSnapshot:incoming,
  expectedCandidateSha256:preview.hypotheticalMergedSha256};
const prepared=prepareCanonicalBackupMerge(params);
assert.deepEqual(Object.keys(prepared).sort(),[
  'source','validation','preparedOnly','persisted','approvedForImport',
  'approvedForWorkoutExecution','requiresAtomicUndo','storeVersion',
  'currentSha256','incomingSha256','candidateSnapshotSha256',
  'candidateSnapshotBytes','candidateSnapshot'
].sort());
assert.equal(prepared.preparedOnly,true);
assert.equal(prepared.persisted,false);
assert.equal(prepared.approvedForImport,false);
assert.equal(prepared.approvedForWorkoutExecution,false);
assert.equal(prepared.requiresAtomicUndo,true);
assert.equal(prepared.currentSha256,sha(current));
assert.equal(prepared.incomingSha256,sha(incoming));
assert.equal(prepared.candidateSnapshotSha256,sha(prepared.candidateSnapshot));
assert.equal(prepared.candidateSnapshotBytes,Buffer.byteLength(prepared.candidateSnapshot,'utf8'));
assert.deepEqual(parseStoredData(prepared.candidateSnapshot),
  JSON.parse(JSON.stringify(mergeStores(parseStoredData(current),
    parseStoredData(incoming)).data)), 'canonical candidate round-trips serialized store');
assert.equal(parseStoredData(prepared.candidateSnapshot).history.length,2);
assert.deepEqual([current,incoming],before);
assert.deepEqual(prepareCanonicalBackupMerge(params),prepared,
  'Exact read-only preparation is deterministic');

const wire=(method,p)=>JSON.stringify({
  protocolVersion:1,id:'backup-prepare-1',method,params:p
});
const response=JSON.parse(dispatchWorkoutStoreJson(wire('prepareBackupMerge',params)));
assert.equal(response.ok,true,JSON.stringify(response.error));
assert.deepEqual(response.result,prepared);
for(const dispatcher of [dispatchBackupSchemaJson,dispatchEngineJson]) {
  assert.equal(JSON.parse(dispatcher(wire('prepareBackupMerge',params))).ok,false);
}
const reject=p=>{
  assert.throws(()=>prepareCanonicalBackupMerge(p));
  const result=JSON.parse(dispatchWorkoutStoreJson(wire('prepareBackupMerge',p)));
  assert.equal(result.ok,false, 'malformed/risky preparation must be rejected');
  assert.ok(!JSON.stringify(result).includes('synthetic'));
};
reject({...params,expectedCandidateSha256:'f'.repeat(64)});
reject({...params,currentSnapshot:raw(101,[history('old',101,10)])});
reject({...params,incomingSnapshot:'{"v":14}'});
reject({...params,extra:true});
reject({...params,incomingSnapshot:'é'.repeat(600000)});
const conflicting=raw(120,[history('old',150,30)]);
const conflicts=previewCanonicalBackupMerge(current,conflicting);
assert.ok(conflicts.flags.includes('RECORD_CONFLICTS_REQUIRE_REVIEW'));
reject({currentSnapshot:current,incomingSnapshot:conflicting,
  expectedCandidateSha256:conflicts.hypotheticalMergedSha256});
const deleting=raw(130,[],{old:40});
const deletion=previewCanonicalBackupMerge(current,deleting);
assert.ok(deletion.flags.includes('OWNED_RECORDS_REMOVED'), 'canonical tombstone must report removed owned records');
// Preflight conflict counts alone do NOT certify a non-destructive merge.
reject({currentSnapshot:current,incomingSnapshot:deleting,
  expectedCandidateSha256:deletion.hypotheticalMergedSha256});
// Non-record settings use the original device's own approval policy, too.
const currentWithSettings=JSON.stringify({...JSON.parse(current),unit:'lb'});
const changedPreferences=JSON.stringify({...JSON.parse(incoming),unit:'kg'});
const preferencesPreview=previewCanonicalBackupMerge(currentWithSettings,changedPreferences);
reject({currentSnapshot:currentWithSettings,incomingSnapshot:changedPreferences,
  expectedCandidateSha256:preferencesPreview.hypotheticalMergedSha256});

const crowdedA=raw(100,Array.from({length:1150},(_,i)=>history('a-'+i,100,11+i)));
const crowdedB=raw(200,Array.from({length:1150},(_,i)=>history('b-'+i,100,21+i)));
const loss=previewCanonicalBackupMerge(crowdedA,crowdedB);
assert.ok(loss.historyDropped>0);
reject({currentSnapshot:crowdedA,incomingSnapshot:crowdedB,
  expectedCandidateSha256:loss.hypotheticalMergedSha256});
assert.equal(JSON.parse(dispatchWorkoutStoreJson(wire('publishBackupMerge',params))).ok,false);
console.log('PASS exact canonical backup preparation, candidate digest, corrections/deletions, risky capacity/loss refusal and no public import/write method');
