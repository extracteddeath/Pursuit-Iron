import assert from 'node:assert/strict';
import fs from 'node:fs';

// Routine branch changes may never produce an APK. A single explicitly
// approved M5 development-device milestone has a narrowly pinned push path,
// an explicit commit prefix and source/JVM checks on the same revision.
// This is NOT release certification or permanent CI APK assembly.
const read=path=>fs.readFileSync(path,'utf8');
const preview=read('.github/workflows/android-native-preview.yml');
const device=read('.github/workflows/android-engine-device-proof.yml');
const sandbox=read('.github/workflows/android-engine-runtime.yml');
const doc=read('android-native/PREVIEW_MILESTONE.md');
const marker=read('android-native/M5_DEVICE_TEST_APK.md');
assert.match(preview,/^on:\n  workflow_dispatch:\n  push:\n    branches:\n      - architecture\/android-compose-foundation\n    paths:\n      - 'android-native\/M5_DEVICE_TEST_APK\.md'/m,
  'APK source pushes must be scoped to the single M5 milestone marker');
assert.ok(preview.includes('    if: "') &&
  preview.includes("github.event_name == 'push' && startsWith(github.event.head_commit.message, 'milestone(android): M5 ')"),
  'Manual runs and ordinary pushes must remain unable to assemble a device APK');
for(const script of [
  'native-core-journey-test.mjs',
  'native-onboarding-interaction-test.mjs',
  'native-original-theme-parity-test.mjs',
  'no-apk-before-end-to-end-test.mjs'
])assert.ok(preview.includes('node android-native/verification/'+script),
  'Development APK must independently re-run the latest core contract '+script);
assert.match(preview,/gradle :domain-contract:testDebugUnitTest --no-daemon --stacktrace/);
assert.match(preview,/gradle :app:assembleDebug --no-daemon --stacktrace/);
assert.ok(marker.includes('M5 development APK') && doc.includes('M5 development APK'),
  'Candidate marker and milestone must explicitly identify testing scope');
assert.match(doc,/APK BUILD FREEZE — active/);
assert.match(device,/^on:\n  workflow_dispatch:/m);
assert.doesNotMatch(device,/^  push:/m);
assert.match(device,/^    if: \$\{\{ false \}\}$/m,
  'Unreviewed emulator/device-proof APK remains disabled');
assert.ok(!sandbox.includes(':engine-runtime:assembleDebugAndroidTest'));
assert.ok(sandbox.includes(':engine-runtime:compileDebugAndroidTestJavaWithJavac'));
const sourceWorkflows=[
  'android-compose-ui-compile.yml','android-domain-contract.yml',
  'android-compose-foundation.yml','android-engine-bundle.yml',
  'android-engine-runtime.yml'
].map(filename=>read('.github/workflows/'+filename));
for(const flow of sourceWorkflows)
  assert.doesNotMatch(flow,/gradle\s+[^\n]*:(?:app:assemble|engine-runtime:assembleDebugAndroidTest)/,
    'Routine validation workflows may not assemble APKs');
console.log('PASS source-verified M5 device APK is a one-marker, one-prefix exception; all ordinary APK builds remain frozen');
