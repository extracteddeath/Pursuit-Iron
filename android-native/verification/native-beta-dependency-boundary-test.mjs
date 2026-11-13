import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

// This checks the *declared* beta module boundary. It is NOT a substitute for
// Gradle's resolved releaseRuntimeClasspath or a compiled APK inspection.
const read=path=>readFileSync(path,'utf8');
const app=read('android-native/app/build.gradle.kts');
const settings=read('android-native/settings.gradle.kts');
const engine=read('android-native/engine-runtime/build.gradle.kts');
const scope=read('android-native/LAUNCH_BETA_SCOPE.md');
const readme=read('android-native/README.md');

const dependencies=app.slice(app.indexOf('\ndependencies {'),
  app.indexOf('\n// Beta release has TWO independent gates:'));
assert.ok(dependencies.startsWith('\ndependencies {'),
  'Cannot locate beta app dependency block');
for(const project of [':domain-contract',':kotlin-engine',':native-coach-store']) {
  assert.ok(dependencies.includes('implementation(project("'+project+'"))'),
    'Missing canonical Kotlin beta dependency: '+project);
}
for(const forbidden of [':engine-runtime',':engine-bridge',':legacy-engine',':web-engine']) {
  assert.ok(!dependencies.includes('project("'+forbidden+'")'),
    'Legacy runtime must not be a direct beta app dependency: '+forbidden);
}
assert.match(settings,/include\([^\n]*":kotlin-engine"[^\n]*":native-coach-store"/,
  'Kotlin modules must be included in Gradle settings');
assert.match(engine,/androidx\.javascriptengine:javascriptengine/,
  'Historical JS runtime boundary changed: review cleanup policy');
assert.ok(app.includes('verifyEarlyBetaRuntime') &&
  app.includes('releaseRuntimeClasspath'),
  'Resolved release classpath must remain a mandatory packaging gate');
assert.ok(scope.includes('100% functional parity') &&
  scope.includes('No release/100%/superiority claims'),
  'Cleanup must not weaken engine parity or evidence policy');
assert.ok(readme.includes('Gradle project inclusion') &&
  readme.includes('Historical development notes'),
  'README must distinguish historic module inclusion from beta runtime');

console.log('PASS native beta declared dependency boundary; resolved Gradle classpath still requires execution');
