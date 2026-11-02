import assert from 'node:assert/strict';
import fs from 'node:fs';

// The user explicitly authorized one DEBUG onboarding-to-Train milestone.
// Routine source commits and on-device instrumentation remain APK-free.
const read=path=>fs.readFileSync(path,'utf8');
const preview=read('.github/workflows/android-native-preview.yml');
const device=read('.github/workflows/android-engine-device-proof.yml');
const sandbox=read('.github/workflows/android-engine-runtime.yml');
const doc=read('android-native/PREVIEW_MILESTONE.md');

assert.ok(preview.includes("      - '.github/workflows/android-native-preview.yml'") &&
  preview.includes('      - architecture/android-compose-foundation') &&
  preview.includes("github.ref == 'refs/heads/architecture/android-compose-foundation'"),
  'Only an explicit preview workflow edit on the isolated branch can build this debug APK');
assert.match(preview,/^  push:\n    branches:\n      - architecture\/android-compose-foundation\n    paths:/m);
assert.match(preview,/^  workflow_dispatch:/m);
assert.match(preview,/Gate onboarding to real workout screen/);
for(const file of [
  'native-onboarding-test.mjs','native-workout-test.mjs',
  'workout-store-protocol-test.mjs','native-onboarding-interaction-test.mjs',
  'native-preview-interaction-test.mjs','no-apk-before-end-to-end-test.mjs'
])assert.ok(preview.includes(file),'Milestone missing required gate: '+file);
assert.match(preview,/gradle :app:assembleDebug --no-daemon --stacktrace/);
assert.ok(preview.includes("pursuit-iron-onboarding-to-train-preview"),
  'Preview must have its own artifact identity');
assert.match(device,/^on:\n  workflow_dispatch:/m);
assert.doesNotMatch(device,/^  push:/m);
assert.match(device,/^    if: \$\{\{ false \}\}$/m,
  'On-device instrumentation remains explicitly disabled');
assert.ok(!sandbox.includes(':engine-runtime:assembleDebugAndroidTest'));
assert.ok(sandbox.includes(':engine-runtime:compileDebugAndroidTestJavaWithJavac'));
for(const name of [
  'android-compose-ui-compile.yml','android-domain-contract.yml',
  'android-compose-foundation.yml','android-engine-bundle.yml',
  'android-engine-runtime.yml'
]) {
  const workflow=read('.github/workflows/'+name);
  assert.doesNotMatch(workflow,/gradle\s+[^\n]*:(?:app:assemble|engine-runtime:assembleDebugAndroidTest)/,
    'Routine validation workflow must never assemble an APK: '+name);
}
assert.match(doc,/ONBOARDING-TO-TRAIN DEBUG APK MILESTONE — authorized/);
console.log('PASS isolated one-off debug preview; routine source CI and device-APK freeze preserved');
