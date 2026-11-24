import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const candidate=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt','utf8');
const controller=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinSessionController.kt','utf8');

// The minimized shortcut belongs to the current host/athlete and is NOT an
// access grant. Resume must recheck BOTH the current signed approval and the
// unfinished owner-verified disk journal, not just a matching workout ID.
assert.match(candidate,/var minimizedGeneratedId by rememberSaveable\(host\)/);
assert.match(candidate,/var selectedId by rememberSaveable\(host\)/);
assert.match(candidate,/PursuitPrimaryAction\("Resume workout"/);
assert.match(candidate,/host\.currentlyApprovedKotlinWorkoutId\(\)/);
assert.match(candidate,/minimizedGeneratedId==awaitingWorkoutId/);
const minimizeShortcut=candidate.slice(
  candidate.indexOf('minimizedGeneratedId?.let { awaitingWorkoutId ->'),
  candidate.indexOf('// This is the COLD-RESTART case:'));
assert.match(minimizeShortcut,/currentUnfinishedGeneratedWorkoutId\(host\)==\s*awaitingWorkoutId/);
assert.match(minimizeShortcut,/runCatching\s*\{/);
assert.match(minimizeShortcut,/withContext\(Dispatchers\.IO\)/);
assert.doesNotMatch(minimizeShortcut,/host\.currentlyApprovedKotlinWorkoutId\(\)/,
  'A matching approval without an unfinished journal must not reopen a completed workout');
assert.match(minimizeShortcut,/The minimized workout is no longer verifiably unfinished/);
// Generated Resume has its own signed-journal read and must stay reachable
// while optional custom/measured inventory or short-session previews load.
// Both minimized and cold-restarted paths share one navigation gate,
// not a new authorization or a bypass of disk verification.
const resumeGate=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinTrainResumeGate.kt','utf8');
const coldResume=candidate.slice(
  candidate.indexOf('resumableGeneratedId?.takeIf {'),
  candidate.indexOf('selectionMessage?.takeIf{minimizedGeneratedId==null}'));
assert.match(candidate,/fun canVerifyGeneratedResume\(\)=NativeKotlinTrainResumeGate\.mayVerifyGenerated\(/);
assert.match(resumeGate,/!resumeChecking && !manualLookupBusy && !anotherWorkoutOpen/);
assert.doesNotMatch(resumeGate,/reviewingResume|timePreviewBusy|timePreview|customInventory/);
assert.match(minimizeShortcut,/PursuitPrimaryAction\("Resume workout",\s*enabled=canVerifyGeneratedResume\(\)/);
assert.match(minimizeShortcut,/if\(canVerifyGeneratedResume\(\)\)/);
assert.match(coldResume,/PursuitPrimaryAction\("Resume saved workout",\s*enabled=canVerifyGeneratedResume\(\)/);
assert.match(coldResume,/if\(canVerifyGeneratedResume\(\)\)/);
assert.doesNotMatch(minimizeShortcut,/!reviewingResume|!timePreviewBusy/);
assert.doesNotMatch(coldResume,/!reviewingResume|!timePreviewBusy/);
assert.match(coldResume,/currentUnfinishedGeneratedWorkoutId\(host\)==\s*issuedId/);
assert.match(coldResume,/resumableGeneratedId==issuedId/);

assert.match(candidate,/fun noCompetingWorkout\(\)=selectedId==null && currentCustomId==null/);
assert.match(candidate,/currentGeneratedId==null && !measuredOpen/);
assert.match(candidate,/val currentCustomId by rememberUpdatedState\(customWorkoutId\)/);
assert.match(candidate,/val currentGeneratedId by rememberUpdatedState\(generatedWorkoutId\)/);
assert.match(candidate,/timeAcceptanceBusy=timeAcceptanceBusy/);
assert.match(minimizeShortcut,/noCompetingWorkout\(\)/);
assert.match(coldResume,/noCompetingWorkout\(\)/);
assert.match(candidate,/if\(noCompetingWorkout\(\)\)selectedId=available\.workoutId/);
assert.match(candidate,/if\(noCompetingWorkout\(\)\)selectedId=found/);
assert.match(candidate,/"Open workout by ID",enabled=valid && canVerifyGeneratedResume\(\)/);
assert.match(candidate,/if\(verifiedUnfinished\) \{/);
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
assert.match(candidate,/visibleUnfinishedCount=20\s*visibleMeasuredCount=20\s*visibleApprovedMeasuredCount=20\s*resumable=null/);
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
// Primary training actions must come before optional hold/carry creation.
// Verified custom and generated resume still precede both, while measured
// recovery counts remain visible even when advanced tools are collapsed.
const trainEntry=candidate.indexOf('} else if(selectedId == null) {');
const customResume=candidate.indexOf('Text(if(resumableGeneratedId!=null)"Other unfinished workouts"',trainEntry);
const primaryProgram=candidate.indexOf('Text("Your training program"',trainEntry);
const timeChoice=candidate.indexOf('PursuitSectionHeading("Time available"',trainEntry);
const measuredTools=candidate.indexOf('PursuitSectionHeading("Measured workouts"',trainEntry);
const otherTools=candidate.indexOf('TextButton(onClick={showOtherWorkouts=!showOtherWorkouts})',trainEntry);
assert.ok(trainEntry>0 && customResume>trainEntry && customResume<primaryProgram &&
  primaryProgram<timeChoice && timeChoice<measuredTools &&
  measuredTools<otherTools,'Main workout and its time choice must not be buried behind measured workout tools');
// Plan is a separately owner-verified destination. Optional unfinished
// workout inventory must not block program review or first-session entry.
// Competing workout navigation and in-flight SHORTEN acceptance still gate
// this action so it cannot race a durable session-choice write.
const programSection=candidate.slice(primaryProgram,timeChoice);
assert.match(programSection,/PursuitPrimaryAction\("Open my program",\s*enabled=!searching && !resumeChecking && !timeAcceptanceBusy\)/);
assert.match(programSection,/if\(!searching && !resumeChecking && !timeAcceptanceBusy\)\s*onReturnToProgram\(\)/);
assert.doesNotMatch(programSection,/!reviewingResume|!timePreviewBusy/);
assert.match(candidate,/var showMeasuredWorkouts by rememberSaveable/);
const measuredSection=candidate.slice(measuredTools,otherTools);
assert.match(measuredSection,/val unfinishedMeasured=/);
assert.match(measuredSection,/val awaitingMeasured=/);
assert.match(measuredSection,/if\(unfinishedMeasured>0 \|\| awaitingMeasured>0\)/);
assert.match(measuredSection,/Saved measured workout verification is unavailable/);
assert.match(measuredSection,/onClick=\{showMeasuredWorkouts=!showMeasuredWorkouts\}/);
assert.match(measuredSection,/if\(showMeasuredWorkouts\) \{\s*when\(val review=resumableMeasured\)/);
assert.match(measuredSection,/host\.openMeasuredWorkout\(item\.workoutId\)/);
assert.match(measuredSection,/host\.issueMeasuredWorkout\(item\.workoutId\)/);
assert.match(measuredSection,/PursuitPrimaryAction\("Create measured workout"/);
const disclosure=measuredSection.slice(measuredSection.indexOf('OutlinedButton('),
  measuredSection.indexOf('if(showMeasuredWorkouts)'));
assert.doesNotMatch(disclosure,/host\.|selectedId=|measuredOpen=true|\.finish\(/,
  'opening secondary tools is presentation-only, not a workout command');

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

for(const needle of ['visibleMeasuredCount','visibleApprovedMeasuredCount']) {
  assert.ok(candidate.includes('var '+needle+' by rememberSaveable(host)'));
  assert.ok(candidate.includes(needle+'=20'));
}
assert.match(candidate,/review\.workouts\.take\(visibleMeasuredCount\)/);
assert.match(candidate,/confirmed\.confirmed\.take\(visibleApprovedMeasuredCount\)/);
assert.match(candidate,/visibleMeasuredCount\+=minOf\(20,remaining\)/);
assert.match(candidate,/visibleApprovedMeasuredCount\+=minOf\(20,remaining\)/);
assert.doesNotMatch(candidate,/review\.workouts\.take\(20\)|confirmed\.confirmed\.take\(20\)/);
assert.match(candidate,/host\.openMeasuredWorkout\(item\.workoutId\)/);
assert.match(candidate,/host\.issueMeasuredWorkout\(item\.workoutId\)/);
console.log('PASS all verified unfinished and approved measured sessions are independently pageable; resume and issuance owner gates preserved.');

const timeHost=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt','utf8');
const timeReview=timeHost.slice(timeHost.indexOf('fun reviewCurrentKotlinShortSession('),
  timeHost.indexOf('fun acceptCurrentKotlinShortSession('));
assert.match(timeReview,/existing is NativeJournalFileResult\.Durable &&\s*existing\.journal\.finishedByEventId!=null\)\s*return@withOwnerTransaction null/);
const directTimeStart=candidate.slice(candidate.indexOf('if(available.alreadyIssued ||\n'),
  candidate.indexOf('val updated=withContext(Dispatchers.IO)'));
assert.match(directTimeStart,/host\.currentlyApprovedKotlinWorkoutId\(\)==available\.workoutId/);
assert.match(directTimeStart,/host\.reviewCurrentKotlinShortSession\(/);
assert.match(directTimeStart,/currentUnfinishedGeneratedWorkoutId\(host\)==available\.workoutId/);
assert.match(directTimeStart,/selectedTimeBudget==selection && timePreview==available/);
assert.match(directTimeStart,/finally \{resumeChecking=false\}/);
console.log('PASS generated time-choice shortcut revalidates the exact approved review and refuses finished journals.');
