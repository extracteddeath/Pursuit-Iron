import assert from 'node:assert/strict';
import fs from 'node:fs';

// Release guard: this must fail if somebody re-enables installable or test APK
// production before the whole native user flow has passed acceptance.
const read=path=>fs.readFileSync(path,'utf8');
const preview=read('.github/workflows/android-native-preview.yml');
const device=read('.github/workflows/android-engine-device-proof.yml');
const sandbox=read('.github/workflows/android-engine-runtime.yml');
const doc=read('android-native/PREVIEW_MILESTONE.md');
for (const [name,source] of [['preview',preview],['device proof',device]]) {
  assert.match(source,/^on:\n  workflow_dispatch:/m,
    name+' may be manually inspected but not push-triggered');
  assert.doesNotMatch(source,/^  push:/m,
    name+' must not start on source or milestone changes');
  assert.match(source,/^    if: \$\{\{ false \}\}$/m,
    name+' job must remain unconditionally disabled');
}
assert.ok(!sandbox.includes(':engine-runtime:assembleDebugAndroidTest'),
  'Routine engine-runtime checks cannot build an instrumentation APK');
assert.ok(sandbox.includes(':engine-runtime:compileDebugAndroidTestJavaWithJavac'),
  'Keep Java instrumented source compilation without APK assembly');
const sourceWorkflows=[
  'android-compose-ui-compile.yml','android-domain-contract.yml',
  'android-compose-foundation.yml','android-engine-bundle.yml',
  'android-engine-runtime.yml'
].map(filename=>read('.github/workflows/'+filename));
for(const flow of sourceWorkflows)
  assert.doesNotMatch(flow,/gradle\s+[^\n]*:(?:app:assemble|engine-runtime:assembleDebugAndroidTest)/,
    'No routine validation workflow may assemble a native APK');
assert.match(doc,/APK BUILD FREEZE — active/);
console.log('PASS no APK builds until canonical onboarding-to-completion acceptance; source-only CI remains active');
