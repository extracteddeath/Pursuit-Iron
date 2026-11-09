import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const candidate=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt',
  'utf8');
const controller=readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinSessionController.kt',
  'utf8');

// Minimize must never imply a completed set or wipe the signed history.
assert.match(candidate,/var minimizedGeneratedId by rememberSaveable\(host\)/);
assert.match(candidate,/Text\("Minimize"\)/);
assert.match(candidate,/TextButton\(enabled=!sessionState\.busy,onClick=/);
assert.match(candidate,/minimizedGeneratedId=selectedId\s*controller\.clear\(\)/);
assert.doesNotMatch(candidate,/Text\("Other workout"\)/);
assert.match(candidate,/workoutScreens\.SaveableStateProvider\("composer:\$selectedId"\)/);
assert.match(controller,/fun clear\(\) \{[\s\S]*?mutable\.value=NativeKotlinSessionState\(\)/);
const clearBody=controller.slice(controller.indexOf('fun clear()'),controller.indexOf('override fun close()'));
assert.doesNotMatch(clearBody,/recordWorkoutOperation|delete|finish\(/);

// A saved Compose workout ID is a presentation shortcut, never authorization.
assert.match(candidate,/PursuitPrimaryAction\("Resume workout"/);
assert.match(candidate,/host\.currentlyApprovedKotlinWorkoutId\(\)/);
assert.match(candidate,/if\(approved==awaitingWorkoutId\)\s*selectedId=awaitingWorkoutId/);
assert.match(candidate,/selectedId==null && customWorkoutId==null/);
assert.match(candidate,/if\(generatedWorkoutId!=null && selectedId!=generatedWorkoutId\)/);
assert.match(candidate,/controller,checkNotNull\(selectedId\),host/);

// Explicit Finish -> History or Coach clears the shortcut.
assert.match(candidate,/minimizedGeneratedId=null\s*selectedId=null\s*onViewFinishedHistory\(\)/);
assert.match(candidate,/minimizedGeneratedId=null\s*selectedId=null\s*onReviewNext\(exerciseId\)/);

// Every verified unfinished workout remains accessible with bounded paging.
assert.match(candidate,/result\.workouts\.take\(visibleUnfinishedCount\)/);
assert.match(candidate,/\(visibleUnfinishedCount\+20\)\.coerceAtMost\(/);
assert.match(candidate,/visibleUnfinishedCount=20\s*resumable=null/);
assert.doesNotMatch(candidate,/more navigation is still needed/);

console.log('PASS native Train: minimize/resume is revalidated, preserves journal and exposes every verified unfinished workout');
