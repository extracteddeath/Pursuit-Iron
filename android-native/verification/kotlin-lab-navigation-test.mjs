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
const history=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHistoryScreen.kt');

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
assert.ok(debug.includes('android:label="Pursuit Iron Lab Stable"') &&
  debug.includes('tools:replace="android:label"'),
  'The installed Kotlin Lab icon must have its own explicit label');
assert.ok(build.includes('applicationIdSuffix = ".kotlinlabstable"'),
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
for(const required of [
  'KotlinHistoryPerformanceSummaryDomain.fromComposer(entry.evidence.sets)',
  'KotlinHistoryPerformanceSummaryDomain.fromCustom(entry.evidence.visits)',
  'fact.issuedLoad,fact.issuedReps,fact.issuedRir',
  'fact.prescribedLoad,fact.prescribedReps,fact.prescribedRir',
  'Actual RIR not logged',
  'Amplifier follow-ups:',
  'NativeKotlinSavedComposerHistorySetRow(',
  'host.correctSavedKotlinWorkout(',
  'KotlinSavedSetCorrection(',
  'NativeSavedCorrectionFileResult.Durable',
  'currentSet.effectiveEventId!=fact.effectiveEventId',
  'current?.manifest?.savedCorrectionRevision!=',
  'kotlinHistoryCorrectionActual('
])assert.ok(history.includes(required),'Kotlin History must show source-backed target/actual facts: '+required);
const coach=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateCoachScreen.kt');
const host=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
assert.ok(journey.includes('onOpenCoach={exerciseId->') &&
  journey.includes('coachRequestedExerciseId=exerciseId') &&
  journey.includes('onReviewNext={exerciseId->') &&
  journey.includes('preferredExerciseId=coachRequestedExerciseId') &&
  history.includes('onClick={onOpenCoach(exercise)}') &&
  history.includes('Review next workout with Coach'),
  'History and Finish must carry explicit source-backed exercise focus to Coach');
assert.ok(coach.includes('preferredExerciseId?.takeIf{it in choices}') &&
  coach.includes('host.currentVerifiedCoachExerciseChoices()') &&
  coach.includes('onOpenTrain(approvedId)') &&
  journey.includes('selectedGeneratedWorkoutId=id') &&
  journey.includes('if(generatedWorkoutId!=null && selectedId!=generatedWorkoutId)'),
  'Coach focus requires current owner-verified program, and accepted grant must open the exact Train entry');
assert.ok((host.match(/enableRepeatedFatigueReview=assistance.offersFatigueAdvice/g)||[]).length===2,
  'Both next-rep preview and final acceptance must preserve the athlete fatigue-advice switch');
console.log('PASS Kotlin Lab navigation, corrected History and explicit History-to-Coach handoff');
