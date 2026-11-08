import assert from 'node:assert/strict';
import fs from 'node:fs';

// Routine branch changes may never produce an APK. A single explicitly
// approved M5 development-device milestone has a narrowly pinned push path,
// an explicit commit prefix and source/JVM checks on the same revision.
// This is NOT release certification or permanent CI APK assembly.
const read=path=>fs.readFileSync(path,'utf8');
const preview=read('.github/workflows/android-native-preview.yml');
const kotlinLab=read('.github/workflows/android-kotlin-lab-device.yml');
const kotlinMarker=read('android-native/KOTLIN_LAB_DEVICE_TEST.md');
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
// Separate, explicitly named Kotlin-only source milestone. Ordinary source
// pushes never package an APK; this candidate cannot overwrite the other
// developer's .preview app or the production app.
assert.match(kotlinLab,/^on:\n  push:\n    branches:\n      - architecture\/kotlin-engine-rebuild\n    paths:\n      - 'android-native\/KOTLIN_LAB_DEVICE_TEST\.md'/m,
  'Kotlin device candidate must be gated by its single branch marker');
assert.ok(kotlinLab.includes("github.event.head_commit.message, 'milestone(kotlin): device lab '"),
  'Kotlin Lab APK requires the explicit one-time milestone commit prefix');
for(const script of [
  'native-core-journey-test.mjs',
  'native-onboarding-interaction-test.mjs',
  'native-original-theme-parity-test.mjs',
  'no-apk-before-end-to-end-test.mjs'
])assert.ok(kotlinLab.includes('node android-native/verification/'+script),
  'Kotlin device candidate must recheck '+script);
assert.ok(kotlinLab.includes(':kotlin-engine:testDebugUnitTest') &&
  kotlinLab.includes(':native-coach-store:testDebugUnitTest') &&
  kotlinLab.includes(':app:assembleDebug'),
  'Kotlin milestone must test the independent engine and native history before assembling');
assert.ok(kotlinMarker.includes('one-time') &&
  kotlinMarker.includes('development') &&
  kotlinMarker.includes('io.github.extracteddeath.pursuitiron.kotlinlab'),
  'Kotlin milestone must disclose its testing scope and isolated package ID');
assert.ok(read('android-native/app/build.gradle.kts')
  .includes('applicationIdSuffix = ".kotlinlab"'),
  'Kotlin device candidate must not overwrite the native preview install');
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
console.log('PASS guarded M5 and isolated Kotlin Lab device milestones; all ordinary APK builds remain frozen');
