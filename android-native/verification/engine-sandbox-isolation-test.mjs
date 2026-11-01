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
assert.ok(!core.includes('inspectSyntheticFailure'),
  'Test diagnostic access must never be packaged in the production runtime library');
assert.doesNotMatch(core, /\bSharedPreferences\b|\bContentResolver\b|\bWebView\b|\bsetJavaScriptEnabled\b/,
  'Read-only engine probe must not access persistence, web surfaces or arbitrary Android storage');
assert.match(device, /on:\s*\n  workflow_dispatch:/);
assert.ok(device.includes("'android-native/engine-runtime/DEVICE_PROOF_MILESTONE.md'"),
  'Emulator automation must run only when the explicit milestone file changes');
assert.ok(!device.includes("'android-native/engine-runtime/**'"),
  'Routine sandbox source commits cannot trigger an emulator');
assert.match(apk, /android-native\/PREVIEW_MILESTONE\.md/);
assert.doesNotMatch(apk, /'android-native\/\*\*'/, 'Routine native commits must not generate APKs');
assert.match(bundle, /canonical-export\.mjs/);
assert.match(bundle, /verify-snapshot\.mjs/);
assert.match(bundle, /verify-parity\.mjs/);
assert.match(manifest, /approvedForWorkoutExecution: false/);
assert.match(manifest, /androidSandboxParityVerified: false/);
console.log('PASS Android sandbox is separate, read-only, digest-guarded; milestone emulator and preview-APK pipelines');

const kotlinBackend = read('engine-runtime/src/main/java/io/github/extracteddeath/pursuitiron/engine/SandboxProgressionBackend.kt');
assert.ok(kotlinBackend.includes('certification: FeatureCertification? = null'),
  'Uncertified Kotlin bridge must never be routed to the workout UI');
assert.ok(kotlinBackend.includes('EngineJsonCodec.decodeProgressionPreview'),
  'Kotlin bridge must decode strictly typed protocol results');
assert.ok(!kotlinBackend.includes('suggestedLoad =') && !kotlinBackend.includes('calculateProgression('),
  'Kotlin transport may not silently implement training calculations');
