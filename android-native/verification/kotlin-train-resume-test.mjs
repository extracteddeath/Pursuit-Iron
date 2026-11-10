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
assert.match(candidate,/TextButton\(enabled=!sessionState\.busy,onClick=/);
assert.match(candidate,/if\(!controller\.state\.value\.busy\) \{/);
assert.match(candidate,/minimizedGeneratedId=selectedId\s*controller\.clear\(\)/);
assert.match(candidate,/workoutScreens\.SaveableStateProvider\("composer:\$selectedId"\)/);
const clearBody=controller.slice(controller.indexOf('fun clear()'),controller.indexOf('override fun close()'));
assert.match(clearBody,/mutable\.value=NativeKotlinSessionState\(\)/);
assert.doesNotMatch(clearBody,/recordWorkoutOperation|delete|finish\(/);
assert.match(candidate,/minimizedGeneratedId=null\s*selectedId=null\s*onViewFinishedHistory\(\)/);
assert.match(candidate,/minimizedGeneratedId=null\s*selectedId=null\s*onReviewNext\(exerciseId\)/);

// No old program may inherit state. No verified custom session is stranded
// by a hard-coded 20-record limit, including after a refresh.
assert.match(candidate,/result\.workouts\.take\(visibleUnfinishedCount\)/);
assert.match(candidate,/\(visibleUnfinishedCount\+20\)\.coerceAtMost\(result\.workouts\.size\)/);
assert.match(candidate,/visibleUnfinishedCount=20\s*resumable=null/);
assert.doesNotMatch(candidate,/more navigation is still needed/);
assert.match(candidate,/onOpenCustomWorkout\(item\.workoutId\)/);
console.log('PASS native Train owner-scoped minimize, reverified resume, save-state retention, and complete unfinished-workout pagination');
