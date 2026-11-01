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
assert.equal(JSON.parse(call('x'.repeat(1048577))).error.code,'INVALID_REQUEST');
assert.equal(JSON.parse(dispatchBackupSchemaJson('x'.repeat(4194305))).error.code,'INVALID_JSON');
assert.equal(JSON.parse(dispatchBackupSchemaJson('{')).error.code,'INVALID_JSON');
assert.equal(JSON.parse(dispatchEngineJson(JSON.stringify({
  protocolVersion:1,id:'x',method:'backupSchemaCheck',params:{rawSnapshot:'x'.repeat(16385)}
}))).error.code,'INVALID_JSON',
  '16KiB generic engine channel remains bounded');
assert.equal(dispatchEngineRequest({protocolVersion:1,id:'schema-1',
  method:'backupSchemaCheck',params:{rawSnapshot:snapshot}}).ok,true);
console.log('PASS isolated canonical v13 schema probe, read-only/redacted output, strict bounds and forbidden methods');

const inventory = raw => JSON.parse(call(raw,'backupProgramInventory'));
const listed = inventory(snapshot);
assert.equal(listed.ok,true,JSON.stringify(listed));
assert.deepEqual(Object.keys(listed.result).sort(),[
  'source','readOnly','validation','snapshotOnly','imported',
  'approvedForWorkoutExecution','storeVersion','storeSavedAt','programs'
].sort());
assert.equal(listed.result.validation,'canonical-v13-backup-program-inventory');
assert.equal(listed.result.snapshotOnly,true);
assert.equal(listed.result.readOnly,true);
assert.equal(listed.result.imported,false);
assert.equal(listed.result.approvedForWorkoutExecution,false);
assert.equal(listed.result.storeSavedAt,100);
assert.deepEqual(listed.result.programs,[{
  id:'synthetic',name:'synthetic-sensitive-name',updatedAt:42,trainingDays:1,exerciseSlots:0,
  days:[{label:'Demo',exerciseSlots:0}]
}]);
assert.ok(!JSON.stringify(listed).includes('private-synthetic-history'),
  'Inventory cannot project historical athlete records');
assert.ok(!JSON.stringify(listed).includes('strength'),
  'Inventory cannot project goal or training prescriptions');
const withTomb = JSON.stringify({...JSON.parse(snapshot),tombs:{synthetic:42}});
assert.deepEqual(inventory(withTomb).result.programs,[], 'equal deletion clock wins');
assert.deepEqual(inventory(JSON.stringify({...JSON.parse(snapshot),
  tombs:{synthetic:43}})).result.programs,[], 'newer deletion clock wins');
assert.equal(inventory(JSON.stringify({...JSON.parse(snapshot),
  tombs:{synthetic:41}})).result.programs.length,1,
  'newer revision is visible only in the backup snapshot');
assert.equal(inventory(JSON.stringify({...JSON.parse(snapshot),
  saved:[...JSON.parse(snapshot).saved,JSON.parse(snapshot).saved]})).error.code,
  'SCHEMA_REJECTED','duplicate revision must fail closed');
assert.equal(inventory(JSON.stringify({...JSON.parse(snapshot),
  saved:[{...JSON.parse(snapshot).saved[0],updatedAt:-1}]})).error.code,
  'SCHEMA_REJECTED');
assert.equal(inventory(JSON.stringify({...JSON.parse(snapshot),
  tombs:{synthetic:'42'}})).error.code,'SCHEMA_REJECTED');
assert.equal(inventory(JSON.stringify({...JSON.parse(snapshot),
  saved:Array.from({length:25},(_,i)=>({
    ...JSON.parse(snapshot).saved[0],id:'synthetic-'+i
  }))})).error.code,'SCHEMA_REJECTED','cannot silently truncate program list');
assert.equal(JSON.parse(call(snapshot,'deleteHistory')).ok,false);
console.log('PASS canonical v13 backup-only saved-program inventory, tombstones, duplicate revisions and redaction');

const daySnapshot = JSON.stringify({
  ...JSON.parse(snapshot),
  saved:[{...JSON.parse(snapshot).saved[0],
    days:[{id:'day-a',label:'Push',exercises:['bb-bench','db-press']},
          {id:'day-b',exercises:[]}]
  }]
});
assert.deepEqual(inventory(daySnapshot).result.programs[0].days,[
  {label:'Push',exerciseSlots:2},{label:null,exerciseSlots:0}
]);
assert.equal(inventory(daySnapshot).result.programs[0].exerciseSlots,2);
assert.ok(!JSON.stringify(inventory(daySnapshot)).includes('bb-bench'));
assert.ok(!JSON.stringify(inventory(daySnapshot)).includes('db-press'));
const invalidLabel = JSON.stringify({...JSON.parse(daySnapshot),saved:[{
  ...JSON.parse(daySnapshot).saved[0],
  days:[{id:'day-a',label:'Bad'+String.fromCharCode(0)+'Label',exercises:[]}]
}]});
assert.equal(inventory(invalidLabel).error.code,'SCHEMA_REJECTED');
assert.equal(inventory(JSON.stringify({...JSON.parse(daySnapshot),saved:[{
  ...JSON.parse(daySnapshot).saved[0],days:[{
    id:'day-a',label:'Z'.repeat(121),exercises:[]
  }]
}]})).error.code,'SCHEMA_REJECTED');
console.log('PASS backup-only day labels/counts without exposing exercise identities');

const largerThanLegacy = JSON.stringify({
  ...JSON.parse(snapshot),
  history: Array.from({length:120}, (_,i) => ({
    id:'synthetic-history-'+i,
    note:'x'.repeat(1800)
  }))
});
assert.ok(largerThanLegacy.length > 128*1024 && largerThanLegacy.length < 1024*1024);
assert.equal(inventory(largerThanLegacy).ok,true,
  'Complete v13 JSON >128 KiB should use the canonical read-only inventory');
assert.equal(JSON.parse(call(largerThanLegacy)).ok,true,
  'Larger source may be counted without exposing history records');
assert.ok(!JSON.stringify(inventory(largerThanLegacy)).includes('synthetic-history-'),
  'Program inventory must remain redacted after widening source bounds');
console.log('PASS 1 MiB bounded source window and 4 MiB metadata channel');
