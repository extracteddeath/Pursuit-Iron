import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=path=>fs.readFileSync(path,'utf8');
const flow=read('.github/workflows/android-kotlin-lab-disposable.yml');
const app=read('android-native/app/build.gradle.kts');
const debug=read('android-native/app/src/debug/AndroidManifest.xml');
const stable=read('.github/workflows/android-kotlin-lab-device.yml');
for(const required of [
  "'android-native/KOTLIN_LAB_DISPOSABLE_TEST.md'",
  "'milestone(kotlin): disposable lab '",
  '-PkotlinLabDisposableRun=$GITHUB_RUN_NUMBER',
  ':app:assembleDebug :app:bundleDebug',
  'kotlin-lab-stable-signing-test.mjs',
  'native-core-journey-test.mjs',
  ':native-coach-store:testDebugUnitTest',
  'apksigner','aapt','Signer #1 certificate SHA-256 digest:',
  'sourceCommit=$GITHUB_SHA','deviceAcceptance=NOT_TESTED',
  'signature=ephemeral-debug-not-updateable','if-no-files-found: error'
])assert.ok(flow.includes(required),'Missing disposable QA gate '+required);
assert.ok(app.includes('applicationIdSuffix = ".kotlinlabstable"') &&
  app.includes('applicationIdSuffix = ".kotlinlabtrial$kotlinLabDisposableRun"') &&
  app.includes('if(kotlinLabDisposableRun!=null)') &&
  debug.includes('android:label="${kotlinLabDisplayName}"'),
  'Trial must get a per-run identity and leave stable Lab untouched');
assert.ok(flow.includes('test -z "${KOTLIN_LAB_SIGNING_STORE_FILE:-}"') &&
  !flow.includes('secrets.PI_KEYSTORE') &&
  !flow.includes('secrets.KOTLIN_LAB_KEYSTORE') &&
  stable.includes('Require stable private Kotlin Lab signing'),
  'Trial never accesses production or stable signing keys');
assert.ok(!flow.includes('bundleRelease') && flow.includes('app-debug.aab'),
  'Trial cannot masquerade as a Play-ready AAB');
console.log('PASS per-run disposable Kotlin Lab APK and debug AAB are isolated and milestone gated');
