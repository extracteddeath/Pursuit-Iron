import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import vm from 'node:vm';

const [bundleFile, metaFile, snapshotRoot] = process.argv.slice(2);
assert.ok(bundleFile && metaFile && snapshotRoot && process.argv.length === 5,
  'Usage: node verify-bundle-structure.mjs <bundle-file> <esbuild-metafile> <verified-snapshot>');
const raw = fs.readFileSync(bundleFile);
assert.ok(raw.length >= 1000 && raw.length <= 12 * 1024 * 1024,
  'Canonical offline bundle exceeds expected byte limits');
const bundle = raw.toString('utf8');
const metadata = JSON.parse(fs.readFileSync(metaFile,'utf8'));
assert.ok(typeof metadata.inputs === 'object' && typeof metadata.outputs === 'object');
const sources = Object.keys(metadata.inputs).map(p => p.replaceAll('\\','/'));
const coverage = JSON.parse(fs.readFileSync(path.join(snapshotRoot, 'coverage.json'),'utf8'));
assert.equal(coverage.entryRoot, 'modules/engine-api.js');
const allowedCanonical = new Set(coverage.canonicalRuntimeFiles);
const allowedBridge = new Set(JSON.parse(fs.readFileSync(
  'android-native/engine-bridge/bundle-sources.json','utf8'))
  .map(name=>'android-native/engine-bridge/'+name));
for (const source of sources) {
  const modulePos = source.lastIndexOf('/modules/');
  const bridgePos = source.lastIndexOf('/android-native/engine-bridge/');
  const normalized = modulePos >= 0 ? source.slice(modulePos + 1)
    : bridgePos >= 0 ? source.slice(bridgePos + 1) : source;
  assert.ok(allowedCanonical.has(normalized) || allowedBridge.has(normalized),
    'Bundler imported a source outside the verified canonical runtime: ' + normalized);
}
assert.ok(sources.some(p => p.endsWith('/modules/engine-api.js') ||
  p === 'modules/engine-api.js'), 'Canonical engine entry not in bundled imports');
assert.ok(sources.some(p => p.endsWith('/modules/next-engine/performance.js') ||
  p === 'modules/next-engine/performance.js'), 'Actual progression policy absent');
assert.ok(sources.some(p => p.endsWith('/android-native/engine-bridge/protocol.mjs') ||
  p === 'android-native/engine-bridge/protocol.mjs'), 'Strict bridge entry missing');
const artifacts = Object.entries(metadata.outputs);
assert.equal(artifacts.length,1,'Unexpected output artifacts');
const [,entry] = artifacts[0];
assert.ok(Array.isArray(entry.entryPoint) === false &&
  typeof entry.entryPoint === 'string' &&
  entry.entryPoint.endsWith('android-entry.mjs'), 'Incorrect JS bundle entry');
assert.ok(Object.keys(entry.imports ?? {}).length === 0,
  'Bundle may not import unbundled code');
assert.ok(bundle.includes('PursuitIronBridge'),
  'Bundler did not provide an expected global bridge name');
assert.ok(!/\bimport\s*\(/.test(bundle),
  'Dynamic imports must not escape the standalone JS bundle');
assert.ok(!/\brequire\s*\(/.test(bundle),
  'Node CommonJS module loading cannot be used in the Android isolate');
// The actual minified IIFE must expose every claimed Android method.
// Source-level protocol tests did not catch a missing android-entry export.
const sandbox = vm.createContext({});
vm.runInContext(bundle, sandbox, { timeout: 10000 });
const bridge = sandbox.PursuitIronBridge;
assert.ok(bridge && typeof bridge.dispatchEngineJson === 'function',
  'Android global bridge is missing its canonical request handler');
assert.equal(typeof bridge.dispatchBackupSchemaJson, 'function',
  'Android bundle must export the dedicated canonical schema probe');
const checkedSource = JSON.stringify({
  v: 13, savedAt: 100, saved: [], history: [], cycles: [],
  custom: [], gyms: [], tombs: {}, perf: {}
});
const schemaWire = JSON.stringify({
  protocolVersion: 1, id: 'bundle-schema-proof', method: 'backupSchemaCheck',
  params: { rawSnapshot: checkedSource }
});
const checked = JSON.parse(bridge.dispatchBackupSchemaJson(schemaWire));
assert.equal(checked.ok, true, 'Canonical parser must work through the actual IIFE');
assert.equal(checked.result.source, 'modules/engine-api.js:parseStoredData');
assert.equal(checked.result.readOnly, true);
assert.equal(checked.result.imported, false);
assert.equal(checked.result.approvedForWorkoutExecution, false);
assert.equal(checked.result.counts.saved, 0);
const forbidden = JSON.parse(bridge.dispatchBackupSchemaJson(JSON.stringify({
  protocolVersion: 1, id: 'bundle-no-writes', method: 'deleteHistory', params: {}
})));
assert.equal(forbidden.ok, false, 'Wider schema channel must refuse write methods');

const backupSnapshot = JSON.stringify({
  ...JSON.parse(checkedSource),
  saved: [
    { id:'backup-active',name:'Synthetic backup view',updatedAt:42,
      config:{goal:'strength'},days:[{id:'day-a',exercises:['bb-bench']}] },
    { id:'backup-deleted',name:'Deleted backup entry',updatedAt:19,
      config:{goal:'strength'},days:[{id:'day-b',exercises:[]}] }
  ],
  history: [{id:'synthetic-private-history'}],
  tombs: {'backup-deleted':19}
});
const invWire = JSON.stringify({
  protocolVersion:1, id:'bundle-inventory-proof',
  method:'backupProgramInventory',params:{rawSnapshot:backupSnapshot}
});
const inventory = JSON.parse(bridge.dispatchBackupSchemaJson(invWire));
assert.equal(inventory.ok,true,'Actual Android IIFE must expose the backup-only inventory');
assert.equal(inventory.result.snapshotOnly,true);
assert.equal(inventory.result.approvedForWorkoutExecution,false);
assert.equal(inventory.result.programs.length,1);
assert.equal(inventory.result.programs[0].id,'backup-active');
assert.equal(inventory.result.programs[0].trainingDays,1);
assert.equal(inventory.result.programs[0].exerciseSlots,1);
assert.deepEqual(JSON.parse(JSON.stringify(inventory.result.programs[0].days)),[
  {label:null,exerciseSlots:1}
]);
assert.ok(!JSON.stringify(inventory).includes('synthetic-private-history'));
assert.ok(!JSON.stringify(inventory).includes('Deleted backup entry'));
const inventoryWrite = JSON.parse(bridge.dispatchBackupSchemaJson(JSON.stringify({
  protocolVersion:1,id:'bundle-inventory-write',method:'deleteHistory',
  params:{rawSnapshot:backupSnapshot}
})));
assert.equal(inventoryWrite.ok,false,'Inventory channel must not expose write operations');

const currentFixture = JSON.parse(fs.readFileSync(
  'android-native/domain-contract/src/test/resources/read-only-transport-golden.json','utf8'
));
const currentWire = JSON.stringify({
  protocolVersion:1,id:'bundle-current-program',
  method:'currentProgramProjection',
  params:{
    rawSnapshot:currentFixture.rawSnapshot,
    programId:currentFixture.selectedProgramId,
    snapshotSha256:currentFixture.wire.reference.snapshotSha256
  }
});
const currentRes = JSON.parse(bridge.dispatchBackupSchemaJson(currentWire));
assert.equal(currentRes.ok,true,'Android IIFE must project canonical current program');
assert.deepEqual(JSON.parse(JSON.stringify(currentRes.result)),currentFixture.wire);
assert.equal(JSON.stringify(currentRes).includes('"history"'),false);

// Execute actual packaged completion code, not a source-level stand-in.
assert.equal(typeof bridge.dispatchWorkoutStoreJson,'function');
const completionFixture=JSON.parse(fs.readFileSync(
  'android-native/domain-contract/src/test/resources/finished-journal-golden.json','utf8'));
const completeWire=JSON.stringify({protocolVersion:1,id:'bundle-completion',method:'prepareWorkoutHistory',
  params:{originSnapshot:completionFixture.rawSnapshot,currentSnapshot:completionFixture.rawSnapshot,
    finishedJournal:JSON.stringify(completionFixture.handoff)}});
const completed=JSON.parse(bridge.dispatchWorkoutStoreJson(completeWire));
assert.equal(completed.ok,true);
assert.deepEqual(completed.result,completionFixture.preparation);
assert.equal(JSON.parse(bridge.dispatchBackupSchemaJson(completeWire)).ok,false);
assert.equal(JSON.parse(bridge.dispatchEngineJson(completeWire)).ok,false);

const sha256 = crypto.createHash('sha256').update(raw).digest('hex');
console.log(JSON.stringify({
  bundleVerified: true,
  apkBuilt: false,
  androidExecutionCertified: false,
  entry: 'android-native/engine-bridge/android-entry.mjs',
  bytes: raw.length,
  inputModules: sources.length,
  bundleSha256: sha256
}));
