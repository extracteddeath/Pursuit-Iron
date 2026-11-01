import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

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
const allowedBridge = new Set([
  'android-native/engine-bridge/protocol.mjs',
  'android-native/engine-bridge/progression-preview.mjs',
  'android-native/engine-bridge/exercise-lookup.mjs',
  'android-native/engine-bridge/android-entry.mjs',
  'android-native/engine-bridge/sandbox-compat.mjs'
]);
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
