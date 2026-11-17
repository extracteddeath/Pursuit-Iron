import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const prefix='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const journey=readFileSync(prefix+'NativeKotlinCandidateJourney.kt','utf8');
const history=readFileSync(prefix+'NativeKotlinCandidateHistoryScreen.kt','utf8');
const policy=readFileSync(prefix+'NativeKotlinHistoryFocusPolicy.kt','utf8');
const tests=readFileSync('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinHistoryFocusPolicyTest.kt','utf8');
const workflow=readFileSync('.github/workflows/android-kotlin-rebuild.yml','utf8');

assert.match(journey,/finishedHistoryWorkoutId by remember\(host\)/);
assert.match(journey,/if\(destination!=KotlinCandidateDestination\.HISTORY\)/);
assert.match(journey,/preferredWorkoutId=finishedHistoryWorkoutId/);
assert.match(journey,/onClearWorkoutFocus=\{finishedHistoryWorkoutId=null\}/);
assert.match(journey,/onViewFinishedHistory:\(String\)->Unit/);
assert.match(journey,/onViewHistory=\{onViewFinishedHistory\(customWorkoutId\)\}/);
assert.match(journey,/val completedId=selectedId/);
assert.match(journey,/onViewFinishedHistory\(completedId\)/);
assert.match(history,/NativeKotlinHistoryFocusPolicy\.admit\(/);
assert.match(history,/verified\?\.completions\?\.map\{it\.manifest\.workoutId\}/);
assert.match(history,/verified\?\.completions\?\.singleOrNull/);
assert.match(history,/if\(preferredWorkoutId!=null\)visibleSessions/);
assert.match(history,/if\(entry\.manifest\.workoutId==focusedWorkoutId\)\s*expanded=true/);
assert.match(history,/Show all workout history/);
assert.match(policy,/verifiedWorkoutIds\.count \{ verified->verified==it \}==1/);
assert.match(workflow,/node android-native\/verification\/kotlin-finished-history-deeplink-test\.mjs/);
for(const name of [
  'verifiedFinishedWorkoutReceivesExactFocus',
  'staleOrUnknownWorkoutCannotSelectAnOlderCompletion',
  'invalidOrDuplicateIdentifiersFailClosed'
])assert.ok(tests.includes(name));
console.log('PASS native completed workout to exact corrected History: generated/custom, source-checked focus and return to full log.');
