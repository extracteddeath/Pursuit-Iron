import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const profile=readFileSync(dir+'NativeKotlinProfileScreen.kt','utf8');
const panel=readFileSync(dir+'NativeKotlinProfileSharePanel.kt','utf8');
const model=readFileSync(dir+'NativeKotlinProfileShareSummary.kt','utf8');
const workflow=readFileSync('.github/workflows/android-kotlin-rebuild.yml','utf8');

assert.match(profile,/timeline\?\.athleteId==current\.athleteProfileId/);
assert.match(profile,/NativeKotlinProfileSharePanel\(/);
assert.match(profile,/profileProgress,displayNameForCurrentAthlete/);
assert.match(profile,/shareFavoriteLabels/);
assert.match(profile,/favoriteExercisesOwner==current\.athleteProfileId/);
assert.match(panel,/rememberSaveable\(athleteId\)/);
assert.match(panel,/var includeName by rememberSaveable\(athleteId\) \{ mutableStateOf\(false\) \}/);
assert.match(panel,/var includeFavorites by rememberSaveable\(athleteId\) \{ mutableStateOf\(false\) \}/);
assert.match(panel,/AlertDialog\(/);
assert.match(panel,/Text\(summary/);
assert.match(panel,/Intent\(Intent.ACTION_SEND\)/);
assert.match(panel,/Intent.createChooser/);
assert.match(panel,/type="text\/plain"/);
assert.match(model,/if \(includeName\)/);
assert.match(model,/if \(includeFavoriteExercises\)/);
assert.match(model,/MAX_FAVORITES = 5/);
assert.match(model,/not a verified competition record or workout recommendation/);
assert.doesNotMatch(model+panel,/\.publish\(|\.issue\(|\.approve\(|\.record\(|\.saveWorkout\(|webView|WebView/);
assert.match(workflow,/node android-native\/verification\/kotlin-profile-share-test\.mjs/);
console.log('PASS profile sharing is an explicit privacy-scoped text preview of verified activity');
