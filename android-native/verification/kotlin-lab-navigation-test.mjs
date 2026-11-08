import assert from 'node:assert/strict';
import fs from 'node:fs';

/**
 * Isolated Kotlin Lab launcher / navigation contract. This does not claim
 * phone accessibility or screen acceptance; it prevents an APK that opens
 * the unrelated MainActivity or makes basic screens unreachable.
 */
const read=path=>fs.readFileSync(path,'utf8');
const main=read('android-native/app/src/main/AndroidManifest.xml');
const debug=read('android-native/app/src/debug/AndroidManifest.xml');
const release=read('android-native/app/src/release/AndroidManifest.xml');
const build=read('android-native/app/build.gradle.kts');
const journey=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt');

assert.match(main,/android:name="\.MainActivity"/,
  'Live MainActivity must remain registered, even when hidden from the lab launcher');
assert.doesNotMatch(main,/android.intent.category.LAUNCHER/,
  'Base manifest must not duplicate the Kotlin Lab launcher');
assert.match(release,/android:name="\.MainActivity"/);
assert.equal((release.match(/android.intent.category.LAUNCHER/g)||[]).length,1,
  'Release variant must keep the original native MainActivity launcher');
assert.match(debug,/android:name="\.KotlinEnginePreviewActivity"/);
assert.equal((debug.match(/android.intent.category.LAUNCHER/g)||[]).length,1,
  'Debug variant must have only the Kotlin Lab launcher');
assert.ok(debug.includes('android:label="Pursuit Iron Kotlin Lab"') &&
  debug.includes('tools:replace="android:label"'),
  'The installed Kotlin Lab icon must have its own explicit label');
assert.ok(build.includes('applicationIdSuffix = ".kotlinlab"'),
  'Kotlin Lab must not overwrite production or another developer\'s .preview app');
assert.ok(journey.includes('Scaffold(') && journey.includes('NavigationBar {') &&
  journey.includes('NavigationBarItem('),
  'Ordinary training screens must be reachable through a persistent native bar');
assert.ok(!journey.includes('KotlinCandidateDestination.entries.forEach'),
  'Do not reintroduce nine raw debugging chips as the primary navigation');
for(const required of [
  'KotlinCandidateDestination.PROGRAM',
  'KotlinCandidateDestination.TRAIN',
  'KotlinCandidateDestination.HISTORY',
  'KotlinCandidateDestination.COACH',
  'KotlinCandidateDestination.ONBOARD',
  'KotlinCandidateDestination.GYM',
  'KotlinCandidateDestination.STARTER',
  'KotlinCandidateDestination.FUTURE',
  'KotlinCandidateDestination.SETTINGS'
])assert.ok(journey.includes(required),'Missing navigation destination '+required);
assert.ok(journey.includes('if(!navigationTouched &&') &&
  journey.includes('host.reviewResumableCustomWorkouts()') &&
  journey.includes('host.listOwnedCustomCycleDrafts()'),
  'Cold-start routing must be based on verified native state without stealing a manual navigation');
console.log('PASS isolated single-launcher Kotlin Lab; original release launcher preserved; four-tab training UI and advanced menu reachable');
