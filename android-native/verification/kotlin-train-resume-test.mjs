import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const candidate=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt','utf8');
const controller=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinSessionController.kt','utf8');

// The minimized shortcut belongs to the current host/athlete and is NOT an
// access grant. Resume must cold recheck signed current program approval.
assert.match(candidate,/var minimizedGeneratedId by rememberSaveable\(host\)/);
assert.match(candidate,/var selectedId by rememberSaveable\(host\)/);
assert.match(candidate,/PursuitPrimaryAction\("Resume workout"/);
assert.match(candidate,/host\.currentlyApprovedKotlinWorkoutId\(\)/);
assert.match(candidate,/minimizedGeneratedId==awaitingWorkoutId/);
assert.match(candidate,/selectedId==null && customWorkoutId==null/);
assert.match(candidate,/if\(approved==awaitingWorkoutId\) \{/);
assert.match(candidate,/selectedId=awaitingWorkoutId\s*minimizedGeneratedId=null/);
assert.match(candidate,/if\(generatedWorkoutId!=null && selectedId!=generatedWorkoutId\)/);
assert.doesNotMatch(candidate,/if\(generatedWorkoutId!=null\) \{\s*if\(selectedId/);

// Minimize only when journal writes are not in progress. Clearing a controller
// is in-memory only and cannot invent a Finish or delete durable set receipts.
assert.match(candidate,/val sessionState by controller\.state\.collectAsStateWithLifecycle\(\)/);
assert.match(candidate,/TextButton\(enabled=!sessionState\.busy,\s*onClick=::minimizeGeneratedWorkout/);
assert.match(candidate,/BackHandler\(enabled=customWorkoutId==null && selectedId!=null\)/);
assert.match(candidate,/fun minimizeGeneratedWorkout\(\)/);
assert.match(candidate,/if\(!controller\.state\.value\.busy\) \{/);
assert.match(candidate,/minimizedGeneratedId=openId\.takeIf\s*\{\s*controller\.state\.value\.presentation\?\.finished!=true\s*\}/);
assert.match(candidate,/minimizedGeneratedId=openId\.takeIf[\s\S]*?controller\.clear\(\)/);
assert.match(candidate,/workoutScreens\.SaveableStateProvider\("composer:\$selectedId"\)/);
const clearBody=controller.slice(controller.indexOf('fun clear()'),controller.indexOf('override fun close()'));
assert.match(clearBody,/mutable\.value=NativeKotlinSessionState\(\)/);
assert.doesNotMatch(clearBody,/recordWorkoutOperation|delete|finish\(/);
assert.match(candidate,/val completedId=selectedId\s*controller\.clear\(\)\s*minimizedGeneratedId=null\s*selectedId=null\s*if\(completedId!=null\)onViewFinishedHistory\(completedId\)/);
assert.match(candidate,/minimizedGeneratedId=null\s*selectedId=null\s*onReviewNext\(exerciseId\)/);

// No old program may inherit state. No verified custom session is stranded
// by a hard-coded 20-record limit, including after a refresh.
assert.match(candidate,/result\.workouts\.take\(visibleUnfinishedCount\)/);
assert.match(candidate,/\(visibleUnfinishedCount\+20\)\.coerceAtMost\(result\.workouts\.size\)/);
assert.match(candidate,/visibleUnfinishedCount=20\s*resumable=null/);
assert.doesNotMatch(candidate,/more navigation is still needed/);
assert.match(candidate,/onOpenCustomWorkout\(item\.workoutId\)/);
// Shortened-session review must use exact Kotlin-staged set evidence and
// readable exercise identities, not raw source IDs or inferred counts.
const shortDomain=readFileSync(
 'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinShorterSessionReviewDomain.kt','utf8');
assert.match(shortDomain,/data class KotlinShortSessionExerciseImpact/);
assert.match(shortDomain,/Collections\.unmodifiableList\(impacts\)/);
assert.match(candidate,/val changedImpacts=review\.exerciseImpacts\.filter/);
assert.match(candidate,/kotlinExerciseLabel\(impact\.exerciseId\)/);
assert.match(candidate,/remainingWorking of \$originalWorking working sets retained/);
assert.match(candidate,/!available\.alreadyIssued &&\s*changedImpacts\.isNotEmpty\(\)/);
assert.doesNotMatch(candidate,/review\.omittedExerciseIds\.joinToString/);
console.log('PASS native Train owner-scoped minimize, reverified resume, save-state retention, and complete unfinished-workout pagination');
console.log('PASS native shorter-session impact evidence, exact counts and readable Train review');

// The custom signed-cycle path must expose equally exact changes to authored
// slots, without importing raw internal IDs into the athlete-facing summary.
const customCycle=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleCandidateScreen.kt','utf8');
const customDomain=readFileSync(
 'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomShorterSessionReviewDomain.kt','utf8');
assert.match(customDomain,/data class CustomShortSessionSlotImpact/);
assert.match(customDomain,/Collections\.unmodifiableList\(impacts\)/);
assert.match(customCycle,/val changedSlots=timeReview\.slotImpacts\.filter/);
assert.match(customCycle,/kotlinExerciseLabel\(impact\.exerciseId\)/);
assert.match(customCycle,/impact\.omittedWarmupSets>0/);
assert.match(customCycle,/impact\.supersetGroup!=null/);
assert.doesNotMatch(customCycle,/timeReview\.omittedSlotIds\.joinToString/);
console.log('PASS exact custom shorter-session slot review, warmups and linked supersets');

// Expose the already existing owner-checked Full restore as a real two-stage
// custom Plan action; never suggest that a read-only Full preview is issued.
const restore=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomTimeRestorePanel.kt','utf8');
const customController=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleController.kt','utf8');
assert.match(customCycle,/onClick=controller::reviewCustomTimeUndo/);
assert.match(customCycle,/NativeKotlinCustomTimeRestorePanel\(/);
assert.match(customCycle,/onConfirm=controller::acceptCustomTimeUndo/);
assert.match(customCycle,/onCancel=controller::dismissCustomTimeUndoReview/);
assert.match(customCycle,/prepared\.snapshot\.absoluteWeek==weekNumber/);
assert.match(restore,/NativeKotlinCustomRestorePresentation\.inspect\(shorter,full\)/);
assert.match(restore,/shorter\.shortSessionOmittedSetKeys/);
assert.match(restore,/Confirm restore Full/);
assert.match(restore,/Inspect every original set target/);
assert.match(customController,/fun dismissCustomTimeUndoReview\(\)/);
console.log('PASS custom Full-restoration preview, explicit confirmation and stale-view fences');
