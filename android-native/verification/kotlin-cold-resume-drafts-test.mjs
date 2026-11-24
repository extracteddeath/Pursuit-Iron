import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const store=readFileSync(dir+'NativeKotlinPendingWorkoutDrafts.kt','utf8');
const generated=readFileSync(dir+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(dir+'NativeKotlinCustomJournalScreen.kt','utf8');
const tests=readFileSync(
  'android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinPendingWorkoutDraftsTest.kt','utf8');

assert.match(store,/getSharedPreferences\("kotlin-unsaved-workout-entry-v1",Context.MODE_PRIVATE\)/);
assert.match(store,/MessageDigest\.getInstance\("SHA-256"\)/);
assert.match(store,/athleteId,workoutId,gymId,exerciseId/);
assert.match(store,/setKey,visitPart,machineId/);
assert.match(store,/load\.value\.stripTrailingZeros\(\)\.toPlainString\(\),load\.unit\.name,load\.basis\.name/);
assert.match(store,/reps\.minimum\.toString\(\),reps\.maximum\.toString\(\)/);
assert.match(store,/if\(committed\)null else store\.read\(identity\)/);
assert.match(store,/if\(committed\)store\.discard\(identity\)/);
assert.match(store,/if\(!committed\)store\.save\(/);
assert.match(store,/NativePendingWorkoutDraftCodec\.decode\(/);
assert.match(store,/NativePendingWorkoutDraftCodec\.encode\(/);
assert.match(store,/if\(parts\.any\{it\.length>MAX_FIELD \|\| !utf8RoundTrips\(it\)\}\)return null/);
assert.match(store,/return encoded\.takeIf\{it\.length<=MAX_RECORD\}/);
assert.match(store,/it\.toByteArray\(Charsets\.UTF_8\)\.contentEquals\(bytes\)/);
assert.match(store,/rememberSaveable\(identity\.value,receiptRevision,committed,stateSaver=fieldSaver\)/);
assert.match(store,/it\[0\]==identity\.value/);
assert.match(store,/it\[1\]==receiptRevision\.orEmpty\(\) && it\[2\]==committed\.toString\(\)/);
assert.match(tests,/savedEditorFieldsCannotOverrideNewerDurableReceiptsOrMachineScope/);
assert.match(tests,/draftEncodingMustAlwaysBeRecoverableWithoutTextReplacement/);
assert.doesNotMatch(store,/KotlinPerformedSet\(|RecordSet\(|recordIssuedCustomSet\(|issueWorkout\(/);

assert.match(generated,/journal\?\.snapshot\?\.source\?\.athleteId\.orEmpty\(\)/);
assert.match(generated,/val draftIdentity=NativePendingWorkoutDraftIdentity\.make\(/);
assert.match(generated,/draftIdentity,set\.isResolved,set\.currentReceipt\?\.appliedRevision/);
assert.match(generated,/draftEditor\.update\(load=it\)/);
assert.match(generated,/draftEditor\.update\(reps=it\)/);
assert.match(generated,/draftEditor\.update\(rir=it\)/);
assert.match(generated,/Recovered an unfinished entry/);
assert.match(generated,/Discard recovered entry/);

assert.match(custom,/athleteId=current\.originalSnapshot\.stamp\.athleteId/);
assert.ok(custom.includes('val visitDraftIdentity=part.name+":stage:"+stageOrdinal') &&
  custom.includes('":activation:"+(activationReceiptRevision?.toString() ?: "none")') &&
  custom.includes('set.setKey,visitDraftIdentity,machineInstanceId,target,expectedReps') &&
  custom.includes('activationReceiptRevision=observed?.appliedRevision') &&
  tests.includes('myoDraftFromPriorActivationRevisionCannotOverrideCorrectedLastSet'),
  'Each mini-set and exact logged activation revision must have distinct cold-recovered drafts');
assert.match(custom,/draftIdentity,existing!=null,existing\?\.appliedRevision/);
assert.match(custom,/draftEditor\.update\(load=it\)/);
assert.match(custom,/draftEditor\.update\(reps=it\)/);
assert.match(custom,/draftEditor\.update\(rir=it\)/);
assert.match(custom,/if\(part==CustomWorkoutVisitPart\.FOLLOW_UP\)/);
assert.match(custom,/Recovered an unfinished entry/);
assert.match(custom,/Discard recovered entry/);

assert.match(tests,/exactIssuedIdentityIsStableAndMachineChangesNeverAlias/);
assert.match(tests,/swappedExerciseDifferentVisitOrChangedPrescriptionCannotResurrectDraft/);
assert.match(tests,/codecRoundTripsPartialTypingAndUnknownActualRir/);
assert.match(tests,/corruptOversizeAndForeignVersionDraftsFailClosed/);
console.log('PASS native cold-resume drafts: generated/custom main/follow-up scope; raw UI only; no journal changes.');
