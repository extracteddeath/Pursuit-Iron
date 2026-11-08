import assert from 'node:assert/strict';
import fs from 'node:fs';

const gradle = fs.readFileSync('android-native/app/build.gradle.kts', 'utf8');
const root = 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const theme = fs.readFileSync(root + 'MainActivity.kt', 'utf8');
const shell = fs.readFileSync(root + 'NativePreviewApp.kt', 'utf8');
const rootGradle = fs.readFileSync('android-native/build.gradle.kts', 'utf8');
const domainGradle = fs.readFileSync('android-native/domain-contract/build.gradle.kts', 'utf8');
const buildWorkflow = fs.readFileSync('.github/workflows/android-native-preview.yml', 'utf8');
assert.match(rootGradle, /com\.android\.application"\) version "9\.1\.1"/);
assert.match(rootGradle, /org\.jetbrains\.kotlin\.plugin\.compose"\) version "2\.4\.21"/);
assert.doesNotMatch(rootGradle + gradle + domainGradle, /org\.jetbrains\.kotlin\.android/);
assert.match(gradle, /compileSdk = 37/);
assert.match(domainGradle, /compileSdk = 37/);
assert.match(buildWorkflow, /gradle-version: '9\.3\.1'/);
assert.ok(buildWorkflow.includes('platforms;android-37.0'), 'SDK manager must use the published 37.0 package identifier');
assert.ok(buildWorkflow.includes("'android-native/M5_DEVICE_TEST_APK.md'") &&
  buildWorkflow.includes('if: "${{') &&
  buildWorkflow.includes("github.event_name == 'push'") &&
  buildWorkflow.includes("startsWith(github.event.head_commit.message, 'milestone(android): M5 ')"),
  'Routine preview APK builds must remain frozen except for the one guarded M5 development milestone');
assert.match(gradle, /compose-bom:2026\.09\.00/);
assert.match(gradle, /androidx\.compose\.material3:material3:1\.5\.0-rc01/);
assert.match(theme, /motionScheme = MotionScheme\.expressive\(\)/);
assert.match(shell, /MaterialTheme\.motionScheme\.defaultEffectsSpec\(\)/);
assert.doesNotMatch(shell, /tween\(220\)/);
console.log('PASS pinned native preview Material 3 Expressive motion API and BOM contract');
