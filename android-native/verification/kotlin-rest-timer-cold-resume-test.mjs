import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const rest=readFileSync(dir+'NativeRestTimer.kt','utf8');
const policy=readFileSync(dir+'NativeKotlinRestCountdownStore.kt','utf8');
const bridge=readFileSync(dir+'NativeKotlinWorkoutRestTimer.kt','utf8');
const generated=readFileSync(dir+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(dir+'NativeKotlinCustomJournalScreen.kt','utf8');
const tests=readFileSync('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinRestCountdownPolicyTest.kt','utf8');

assert.match(policy,/getSharedPreferences\("kotlin-workout-rest-v1",Context.MODE_PRIVATE\)/);
assert.match(policy,/athleteId,workoutId/);
assert.match(policy,/MessageDigest\.getInstance\("SHA-256"\)/);
assert.match(policy,/receiptStamp\(receiptEventId\)/);
assert.match(policy,/raw\?\.takeIf\{it\.startsWith\("\$stamp:"\)\}/);
assert.doesNotMatch(policy,/SystemClock|System\.currentTimeMillis/); // Pure policy receives clock samples
assert.match(policy,/remaining\(checkpoint,elapsed,wall\)/);
assert.match(policy,/if\(elapsedAge>MAX_SECONDS\*1000L\+CLOCK_TOLERANCE_MILLIS\)return false/);
assert.match(policy,/abs\(wallAge-elapsedAge\)<=CLOCK_TOLERANCE_MILLIS/);
assert.match(policy,/fun adjust\(/);
assert.match(policy,/fun decode\(raw:String\?\)/);
assert.doesNotMatch(policy,/RecordSet\(|recordIssuedCustomSet\(|approve\(|issueWorkout\(|KotlinPerformedSet\(/);

assert.match(rest,/ownerId: String\? = null/);
assert.match(rest,/scopeKey=remember\(workoutId,ownerId\)/);
// An Activity bundle may be older than the acknowledged journal receipt.
const scoped=rest.slice(rest.indexOf('var active by if (scopeKey != null)'),rest.indexOf('stateSaver=restCheckpointSaver'));
assert.match(scoped,/remember\(scopeKey\)/);
assert.match(scoped,/restStore\.read\(scopeKey,latestReceiptOperationId,/);
assert.doesNotMatch(scoped.split('} else {')[0],/rememberSaveable/);
assert.match(rest,/var observedEvent by remember\(workoutId,ownerId\).*mutableStateOf\(latestReceiptOperationId\)/);
assert.match(rest,/restStore\.save\(scopeKey,latestReceiptOperationId,checkpoint\)/);
assert.match(rest,/NativeRestCountdownPolicy\.valid\(/);
assert.match(rest,/NativeRestCountdownPolicy\.adjust\(/);
assert.match(rest,/onLongClick = \{acknowledgeManualAction\(\);publish\(null\)\}/);
assert.match(rest,/onClick = \{ acknowledgeManualAction\(\);begin\(prescribedSeconds\) \}/);
assert.match(rest,/fun acknowledgeManualAction\(\) \{[\s\S]*?observedEvent=latestReceiptOperationId/);
assert.match(rest,/NativeRestReceiptCuePolicy\.needsPendingClear\(/);
assert.match(rest,/TextButton\(onClick=\{acknowledgeManualAction\(\);publish\(null\)\}/);
assert.match(rest,/Text\("−30 s"\)/);
assert.match(rest,/Text\("\+30 s"\)/);
assert.match(rest,/Text\("Clear"\)/);
assert.match(rest,/observedEvent=latestReceiptOperationId/);
assert.match(rest,/if\(confirmedCue.seconds==0\)publish\(null\)/);
assert.match(rest,/LaunchedEffect\(workoutId,ownerId,latestReceiptOperationId,autoCue,enabled\)/);
assert.match(rest,/NativeRestReceiptCuePolicy.newCue\(/);
assert.match(rest,/if\(confirmedCue==null\)/);

assert.match(bridge,/ownerId=athleteId/);
assert.match(bridge,/if\(!rest.hasRemainingWork\)return/);
assert.match(bridge,/rest\.manualRestSeconds/);
assert.match(bridge,/latestReceiptOperationId=rest\.latestReceiptEventId/);
assert.match(generated,/athleteId=journal\.snapshot\.source\.athleteId/);
assert.match(custom,/athleteId=current\.originalSnapshot\.stamp\.athleteId/);
assert.match(generated,/KotlinWorkoutRestDomain\.inspect\(journal/);
assert.match(custom,/KotlinWorkoutRestDomain\.inspect\(current/);

assert.match(tests,/normalCountdownRecoversAfterWorkoutMinimizeAndColdReopen/);
assert.match(tests,/quickAdjustChangesRemainingRestWithoutChangingAuthorisedTargets/);
assert.match(tests,/clockChangesRebootsAndHistoricalRestAlwaysFailClosed/);
assert.match(tests,/rejectsCorruptAndImpossiblePersistedSnapshots/);
console.log('PASS native rest timer: scoped cold recovery, real clock guard, receipt-only auto cue, accessible adjustments; no coach writes.');
