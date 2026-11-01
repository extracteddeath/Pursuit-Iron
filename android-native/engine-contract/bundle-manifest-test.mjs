import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { createBundleManifest, verifyBundleManifest } from './create-bundle-manifest.mjs';

const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'pi-engine-manifest-'));
const source='modules/engine-api.js';
const root=path.join(tmp,'modules');
const bundleFile=path.join(tmp,'engine.js');
const fixture=path.join(tmp,'fixtures.json');
try {
  fs.mkdirSync(root);
  const js=Buffer.from('export const source = "canonical";\n');
  fs.writeFileSync(path.join(tmp,source),js);
  fs.writeFileSync(path.join(tmp,'coverage.json'),JSON.stringify({
    schemaVersion:2,entryRoot:source,canonicalRuntimeFiles:[source]
  }));
  fs.writeFileSync(path.join(tmp,'snapshot.json'),JSON.stringify({
    schemaVersion:2,files:{[source]:sha(js)}
  }));
  fs.writeFileSync(fixture,JSON.stringify({
    schemaVersion:1,feature:'progressionPreview',cases:[{},{},{}]
  }));
  const bundle=Buffer.from('var PursuitIronBridge = '+ 'x'.repeat(4000)+';');
  fs.writeFileSync(bundleFile,bundle);
  const manifest=createBundleManifest(tmp,bundleFile,fixture);
  assert.equal(manifest.approvedForWorkoutExecution,false);
  assert.equal(manifest.androidSandboxParityVerified,false);
  assert.ok(verifyBundleManifest(bundle,manifest));
  assert.deepEqual(manifest,createBundleManifest(tmp,bundleFile,fixture));
  const tampered=Buffer.from(bundle);
  tampered[10]=tampered[10]===65?66:65;
  assert.equal(verifyBundleManifest(tampered,manifest),false);
  assert.equal(verifyBundleManifest(bundle,{...manifest,bundleSha256:'0'.repeat(64)}),false);
  assert.equal(verifyBundleManifest(bundle,{...manifest,canonicalSourceDigest:'unverified'}),false);
  assert.equal(verifyBundleManifest(bundle,{...manifest,approvedForWorkoutExecution:true}),false);
  assert.equal(verifyBundleManifest(bundle,{...manifest,androidSandboxParityVerified:true}),false);
  assert.equal(verifyBundleManifest(bundle,{}),false);
  console.log('PASS 3-part Android engine provenance manifest, tamper detection and noncertified guard');
} finally {
  fs.rmSync(tmp,{recursive:true,force:true});
}
