import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { inspectEngineSnapshot } from './snapshot-identity.mjs';

const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'pi-native-identity-'));
const source = 'modules/engine-api.js';
const fixtureFile = path.join(root, 'golden.json');
const manifestFile = path.join(root, 'snapshot.json');
const writeManifest = digest => fs.writeFileSync(manifestFile,
  JSON.stringify({ schemaVersion:2, files:{[source]:digest} }));
try {
  fs.mkdirSync(path.join(root, 'modules'));
  const js = 'export const fixture = 1;\n';
  fs.writeFileSync(path.join(root, source), js);
  fs.writeFileSync(path.join(root, 'coverage.json'), JSON.stringify({
    schemaVersion:2, entryRoot:source, canonicalRuntimeFiles:[source]
  }));
  const fixtures = {schemaVersion:1,feature:'progressionPreview',cases:[{},{},{}]};
  fs.writeFileSync(fixtureFile, JSON.stringify(fixtures));
  writeManifest(hash(js));
  const identity = inspectEngineSnapshot(root,fixtureFile);
  assert.equal(identity.parityCertified, false);
  assert.match(identity.sourceDigest, /^[0-9a-f]{64}$/);
  assert.match(identity.fixtureDigest, /^[0-9a-f]{64}$/);
  assert.deepEqual(identity, inspectEngineSnapshot(root,fixtureFile), 'deterministic identity');
  fs.writeFileSync(path.join(root,source), 'export const fixture = 2;\n');
  assert.throws(()=>inspectEngineSnapshot(root,fixtureFile), /byte mismatch/);
  writeManifest(hash('export const fixture = 2;\n'));
  assert.notEqual(inspectEngineSnapshot(root,fixtureFile).sourceDigest,identity.sourceDigest,
    'changing source must change identity');
  fs.writeFileSync(fixtureFile,JSON.stringify({...fixtures,cases:[{},{},{},{}]}));
  assert.notEqual(inspectEngineSnapshot(root,fixtureFile).fixtureDigest,identity.fixtureDigest);
  fs.writeFileSync(path.join(root,'coverage.json'),JSON.stringify({
    schemaVersion:2,entryRoot:source,canonicalRuntimeFiles:[source,'modules/../unsafe.js']
  }));
  assert.throws(()=>inspectEngineSnapshot(root,fixtureFile),/unsafe source path/);
  console.log('PASS canonical engine runtime + golden-fixture identity, mutation detection and fail-closed source paths');
} finally {
  fs.rmSync(root,{recursive:true,force:true});
}
