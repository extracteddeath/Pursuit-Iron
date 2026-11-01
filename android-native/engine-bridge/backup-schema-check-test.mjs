import assert from 'node:assert/strict';
import { dispatchBackupSchemaJson, dispatchEngineRequest, dispatchEngineJson } from './protocol.mjs';
import { parseStoredData } from '../../modules/engine-api.js';

// Invented canonical source only. No personal backup enters CI.
const snapshot = JSON.stringify({
  v:13,savedAt:100,
  saved:[{id:'synthetic',updatedAt:42,name:'synthetic-sensitive-name',
    config:{goal:'strength'},weeks:4,days:[{id:'one',label:'Demo',exercises:[]}]}],
  history:[{id:'private-synthetic-history',date:1}],cycles:[],custom:[],gyms:[],
  tombs:{},perf:{}
});
const call = (rawSnapshot,method='backupSchemaCheck',extras={}) =>
  dispatchBackupSchemaJson(JSON.stringify({
    protocolVersion:1,id:'schema-1',method,params:{rawSnapshot,...extras}
  }));
const ok = JSON.parse(call(snapshot));
assert.equal(ok.ok,true,JSON.stringify(ok));
assert.deepEqual(Object.keys(ok.result).sort(),[
  'source','readOnly','validation','imported','approvedForWorkoutExecution',
  'storeVersion','counts'
].sort());
assert.equal(ok.result.source,'modules/engine-api.js:parseStoredData');
assert.equal(ok.result.validation,'canonical-v13-structure-only');
assert.equal(ok.result.readOnly,true);
assert.equal(ok.result.imported,false);
assert.equal(ok.result.approvedForWorkoutExecution,false);
assert.deepEqual(ok.result.counts,{saved:1,history:1,cycles:0,custom:0,gyms:0});
assert.equal(parseStoredData(snapshot).saved.length,ok.result.counts.saved);
assert.ok(!JSON.stringify(ok).includes('synthetic-sensitive-name'));
assert.ok(!JSON.stringify(ok).includes('private-synthetic-history'));
assert.ok(!JSON.stringify(ok).includes('exercises'));
assert.equal(JSON.stringify(JSON.parse(snapshot)),snapshot,'Must not mutate the caller bytes');
assert.equal(JSON.parse(call(snapshot,'deleteHistory')).error.code,'INVALID_REQUEST');
assert.equal(JSON.parse(call(snapshot,'backupSchemaCheck',{replaceHistory:[]})).error.code,'INVALID_REQUEST');
assert.equal(JSON.parse(call(snapshot.replace('"v":13','"v":12'))).error.code,'SCHEMA_REJECTED');
assert.equal(JSON.parse(call(snapshot.replace('"v":13','"v":14'))).error.code,'SCHEMA_REJECTED');
assert.equal(JSON.parse(call(snapshot.replace('"history":[','"history":"not an array", "unused":['))).error.code,'SCHEMA_REJECTED');
assert.equal(JSON.parse(call('not-json')).error.code,'SCHEMA_REJECTED');
assert.equal(JSON.parse(call('{"v":13}')).error.code,'SCHEMA_REJECTED');
assert.equal(JSON.parse(call('x'.repeat(65537))).error.code,'INVALID_REQUEST');
assert.equal(JSON.parse(dispatchBackupSchemaJson('x'.repeat(196609))).error.code,'INVALID_JSON');
assert.equal(JSON.parse(dispatchBackupSchemaJson('{')).error.code,'INVALID_JSON');
assert.equal(JSON.parse(dispatchEngineJson(JSON.stringify({
  protocolVersion:1,id:'x',method:'backupSchemaCheck',params:{rawSnapshot:'x'.repeat(16385)}
}))).error.code,'INVALID_JSON',
  '16KiB generic engine channel remains bounded');
assert.equal(dispatchEngineRequest({protocolVersion:1,id:'schema-1',
  method:'backupSchemaCheck',params:{rawSnapshot:snapshot}}).ok,true);
console.log('PASS isolated canonical v13 schema probe, read-only/redacted output, strict bounds and forbidden methods');
