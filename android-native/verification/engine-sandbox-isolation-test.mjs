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
assert.match(app, /implementation\(project\(":engine-runtime"\)\)/,
  'App needs the independently gated read-only metadata runtime');
const shell = read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativePreviewApp.kt');
const activity = read('app/src/main/java/io/github/extracteddeath/pursuitiron/MainActivity.kt');
const packaged = read('app/src/main/java/io/github/extracteddeath/pursuitiron/PackagedReadOnlySchemaVerifier.kt');
assert.match(shell, /UnconnectedEngineGateway\(\)\.status/,
  'Training navigation must stay disconnected from the read-only metadata service');
assert.match(packaged, /CanonicalJsSandboxProbe.verifyBundleBytes\(binary, expected\)/);
assert.match(activity, /readOnlyVerifier\?\.close\(\)/);
assert.ok(!packaged.includes('generateNextProgramForShell') &&
  !packaged.includes('updateHistory') &&
  !packaged.includes('deleteHistory') &&
  !packaged.includes('lookupExercisesAsync'),
  'App may only connect the canonical backup metadata checker');
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

/**
 * A large selected file must never bypass smaller Kotlin/Java/JS bounds or
 * receive a truncated source accepted as the original. Keep the three
 * transport owners locked to the same bounded 1 MiB source window.
 */
const limits = read('domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ReadOnlyBackupLimits.kt');
const intake = read('domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ReadOnlyBackupCandidateReader.kt');
const sourceGate = read('domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ReadOnlyBackupSourceGate.kt');
const codec = read('domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ReadOnlyBackupSchemaCodec.kt');
const worker = read('engine-runtime/src/main/java/io/github/extracteddeath/pursuitiron/engine/CanonicalJsReadOnlyService.java');
const jsProbe = read('engine-bridge/backup-schema-check.mjs');
const jsProtocol = read('engine-bridge/protocol.mjs');
assert.match(limits, /MAX_SOURCE_BYTES = 1024 \* 1024/);
assert.match(limits, /MAX_SOURCE_CHARACTERS = 1024 \* 1024/);
assert.match(limits, /MAX_WIRE_BYTES = 4 \* 1024 \* 1024/);
assert.match(intake, /MAX_CANDIDATE_BYTES = ReadOnlyBackupLimits\.MAX_SOURCE_BYTES/);
assert.match(sourceGate, /MAX_PROBE_BYTES = ReadOnlyBackupLimits\.MAX_SOURCE_BYTES/);
assert.match(sourceGate, /MAX_PROBE_CHARACTERS = ReadOnlyBackupLimits\.MAX_SOURCE_CHARACTERS/);
assert.match(codec, /ReadOnlyBackupLimits\.MAX_SOURCE_BYTES/);
assert.match(codec, /ReadOnlyBackupLimits\.MAX_WIRE_BYTES/);
assert.match(worker, /ReadOnlyBackupLimits\.MAX_SOURCE_BYTES/);
assert.match(worker, /ReadOnlyBackupLimits\.MAX_SOURCE_CHARACTERS/);
assert.match(worker, /ReadOnlyBackupLimits\.MAX_WIRE_BYTES/);
assert.match(jsProbe, /MAX_BACKUP_PROBE_CHARS = 1024 \* 1024/);
assert.match(jsProtocol, /json\.length > 4 \* 1024 \* 1024/);
assert.match(intake, /candidate\.fill\(0\)/);
assert.match(intake, /FingerprintOnly\(fingerprint\)/);
console.log('PASS consistent 1 MiB source and 4 MiB wire caps across Kotlin, Android and JS; oversized sources fingerprint only');
