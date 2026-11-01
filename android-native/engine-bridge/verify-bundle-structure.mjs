import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const [bundleFile, metaFile] = process.argv.slice(2);
assert.ok(bundleFile && metaFile && process.argv.length === 4,
  'Usage: node verify-bundle-structure.mjs <bundle-file> <esbuild-metafile>');
const raw = fs.readFileSync(bundleFile);
assert.ok(raw.length >= 1000 && raw.length <= 12 * 1024 * 1024,
  'Canonical offline bundle exceeds expected byte limits');
const bundle = raw.toString('utf8');
const metadata = JSON.parse(fs.readFileSync(metaFile,'utf8'));
assert.ok(typeof metadata.inputs === 'object' && typeof metadata.outputs === 'object');
const sources = Object.keys(metadata.inputs).map(p => p.replaceAll('\\','/'));
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
  entry.entryPoint.endsWith('protocol.mjs'), 'Incorrect JS bundle entry');
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
  entry: 'android-native/engine-bridge/protocol.mjs',
  bytes: raw.length,
  inputModules: sources.length,
  bundleSha256: sha256
}));
