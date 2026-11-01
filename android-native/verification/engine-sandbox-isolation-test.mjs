import assert from 'node:assert/strict';
import fs from 'node:fs';

const root = 'android-native/';
const read = name => fs.readFileSync(root+name,'utf8');
const app = read('app/build.gradle.kts');
const core = read('engine-runtime/src/main/java/io/github/extracteddeath/pursuitiron/engine/CanonicalJsSandboxProbe.java');
const runtime = read('engine-runtime/build.gradle.kts');
const device = fs.readFileSync('.github/workflows/android-engine-device-proof.yml','utf8');
const apk = fs.readFileSync('.github/workflows/android-native-preview.yml','utf8');
const bundle = fs.readFileSync('.github/workflows/android-engine-bundle.yml','utf8');
const manifest = read('engine-contract/create-bundle-manifest.mjs');
const settings = read('settings.gradle.kts');

assert.match(settings, /include\(":app", ":domain-contract", ":engine-runtime"\)/);
assert.doesNotMatch(app, /implementation\(project\(":engine-runtime"\)\)/,
  'Production-like demo cannot silently import an uncertified engine');
assert.match(runtime, /androidx\.javascriptengine:javascriptengine:1\.1\.1/);
for (const guard of [
  'JavaScriptSandbox.isSupported()', 'JavaScriptSandbox.createConnectedInstanceAsync',
  'JSONObject.quote(input)', 'verifyBundleBytes(bundle, expectedSha256)',
  'getJSONObject("result")','engineCertified', 'Looper.getMainLooper()',
  'isolate.evaluateJavaScriptAsync'
]) assert.ok(core.includes(guard), 'Android runtime missing guard: '+guard);
assert.doesNotMatch(core, /\bSharedPreferences\b|\bContentResolver\b|\bWebView\b|\bsetJavaScriptEnabled\b/,
  'Read-only engine probe must not access persistence, web surfaces or arbitrary Android storage');
assert.match(device, /on:\s*\n  workflow_dispatch:/);
assert.doesNotMatch(device, /\n\s+push:/, 'Emulator workflow must stay manual-only');
assert.match(apk, /android-native\/PREVIEW_MILESTONE\.md/);
assert.doesNotMatch(apk, /'android-native\/\*\*'/, 'Routine native commits must not generate APKs');
assert.match(bundle, /canonical-export\.mjs/);
assert.match(bundle, /verify-snapshot\.mjs/);
assert.match(bundle, /verify-parity\.mjs/);
assert.match(manifest, /approvedForWorkoutExecution: false/);
assert.match(manifest, /androidSandboxParityVerified: false/);
console.log('PASS Android sandbox is separate, read-only, digest-guarded; manual emulator and milestone-only APK pipelines');
