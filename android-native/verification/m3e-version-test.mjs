import assert from 'node:assert/strict';
import fs from 'node:fs';

const gradle = fs.readFileSync('android-native/app/build.gradle.kts', 'utf8');
const root = 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const theme = fs.readFileSync(root + 'MainActivity.kt', 'utf8');
const shell = fs.readFileSync(root + 'NativePreviewApp.kt', 'utf8');
assert.match(gradle, /compose-bom:2026\.09\.00/);
assert.match(gradle, /androidx\.compose\.material3:material3:1\.5\.0-beta02/);
assert.match(theme, /motionScheme = MotionScheme\.expressive\(\)/);
assert.match(shell, /MaterialTheme\.motionScheme\.defaultEffectsSpec\(\)/);
assert.doesNotMatch(shell, /tween\(220\)/);
console.log('PASS pinned native preview Material 3 Expressive motion API and BOM contract');
