import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { inspectEngineSnapshot } from './snapshot-identity.mjs';

const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');

export function createBundleManifest(snapshotRoot, bundleFile, fixtureFile) {
  const canonical = inspectEngineSnapshot(snapshotRoot, fixtureFile);
  const bundle = fs.readFileSync(bundleFile);
  assert.ok(bundle.length > 1000 && bundle.length <= 12 * 1024 * 1024,
    'Invalid Android engine bundle size');
  const fixtureCount = JSON.parse(fs.readFileSync(fixtureFile,'utf8')).cases.length;
  return Object.freeze({
    schemaVersion: 1,
    protocolVersion: canonical.protocolVersion,
    canonicalSource: canonical.source,
    canonicalSourceDigest: canonical.sourceDigest,
    fixtureDigest: canonical.fixtureDigest,
    goldenFixtureCases: fixtureCount,
    bundleSha256: digest(bundle),
    bundleSizeBytes: bundle.length,
    canonicalRuntimeFiles: canonical.canonicalRuntimeFiles,
    bundler: {name:'esbuild',version:'0.28.2',format:'iife',globalName:'PursuitIronBridge'},
    approvedForWorkoutExecution: false,
    androidSandboxParityVerified: false
  });
}

export function verifyBundleManifest(bundleBytes, manifest) {
  const sha = /^[0-9a-f]{64}$/;
  return bundleBytes instanceof Uint8Array &&
    bundleBytes.length > 1000 && bundleBytes.length <= 12 * 1024 * 1024 &&
    manifest?.schemaVersion === 1 &&
    manifest.protocolVersion === 1 &&
    manifest.canonicalSource === 'modules/engine-api.js' &&
    sha.test(manifest.canonicalSourceDigest) &&
    sha.test(manifest.fixtureDigest) &&
    sha.test(manifest.bundleSha256) &&
    manifest.bundleSizeBytes === bundleBytes.length &&
    manifest.approvedForWorkoutExecution === false &&
    manifest.androidSandboxParityVerified === false &&
    digest(bundleBytes) === manifest.bundleSha256;
}

if(process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  assert.equal(process.argv.length, 5, 'Usage: node create-bundle-manifest.mjs <snapshot-dir> <bundle-file> <out-file>');
  const fixtures=fileURLToPath(new URL('./progression-golden.json',import.meta.url));
  const manifest=createBundleManifest(process.argv[2],process.argv[3],fixtures);
  assert.ok(verifyBundleManifest(fs.readFileSync(process.argv[3]),manifest));
  fs.writeFileSync(process.argv[4],JSON.stringify(manifest,null,2)+'\n');
  console.log('PASS Android canonical bundle identities: '+manifest.bundleSha256+
    ', canonical '+manifest.canonicalSourceDigest+
    ', fixtures '+manifest.fixtureDigest+
    '; Android execution remains disabled');
}
