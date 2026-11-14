import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=(path)=>readFileSync('android-native/'+path,'utf8');
const store=read('native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinPinnedExerciseNoteFileStore.kt');
const host=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
const sheet=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinPinnedExerciseNoteControls.kt');
const custom=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt');
const generated=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutCandidateScreen.kt');
const test=read('native-coach-store/src/test/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinPinnedExerciseNoteFileStoreTest.kt');

assert.match(store,/class NativeKotlinPinnedExerciseNoteFileStore/);
assert.match(store,/fence\.withOwnerTransaction\(key\)/);
assert.match(store,/NativePinnedNoteRefusal\.REVISION_CONFLICT/);
assert.match(store,/NativePinnedNoteRefusal\.EVENT_ID_COLLISION/);
assert.match(store,/NativePinnedNoteRefusal\.CORRUPT_OR_PENDING/);
assert.match(store,/StandardCopyOption\.ATOMIC_MOVE/);
assert.match(store,/MessageDigest\.isEqual\(sha\(body\),digest\)/);
assert.match(store,/lastEventId==eventId/);
assert.match(store,/it\.exerciseId!=exercise/);
assert.match(store,/setupKeys=setOf\("attachment","seat","grip","other"\)/);
assert.match(host,/customIssuedStore\.open\(owner\.ownerKey,workoutId\)/);
assert.match(host,/trackedWorkout\.open\(/);
assert.match(host,/current\.issued\.athleteId==owner\.athleteId/);
assert.match(host,/journal\.snapshot\.slots\.any\{it\.exerciseId==exerciseId\}/);
assert.match(host,/current\.journal\.snapshot\.targets\.any\{it\.exerciseId==exerciseId\}/);
assert.match(host,/if\(!canAccessPinnedExercise\(owner,workoutId,exerciseId,custom,true\)\)/);
assert.match(host,/pinnedExerciseNotes\.save\(owner\.ownerKey,exerciseId/);
assert.match(custom,/NativeKotlinPinnedExerciseControl\(/);
assert.match(generated,/NativeKotlinPinnedExerciseControl\(/);
assert.match(sheet,/rememberSaveable\(workoutId,exerciseId\)/);
assert.match(sheet,/NativePinnedExerciseNotePolicy\.validContent\(draft,setup\)/);
assert.match(sheet,/host\.savePinnedExerciseNote\(/);
assert.match(sheet,/persisted\.lastEventId==request/);
assert.match(sheet,/if\(persisted!=null && persisted\.lastEventId==request/);
assert.match(sheet,/Note not confirmed saved\. Your edits remain here/);
assert.match(sheet,/if\(dirty\)confirmDiscard=true/);
assert.match(sheet,/enabled=!busy && dirty && valid/);
assert.match(test,/@Test fun noteSurvivesColdReopenAndCarriesAcrossWorkoutsByExactExercise/);
assert.match(test,/@Test fun ownerFenceAndPerExerciseCasPermitOnlyOneConcurrentEdit/);
assert.match(test,/@Test fun wrongOwnerAndCorruptOrPendingBytesFailClosed/);
assert.doesNotMatch(store,/KotlinJournalOperation\.RecordSet|CustomWorkoutJournalOperation\.Record/);

// The compact Focus toolbar must enter the existing owner-verified note
// sheet for exactly the selected exercise/slot. Duplicate custom exercise
// IDs in separate slots cannot open two sheets from one toolbar tap.
const tools=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinWorkoutTools.kt');
for(const label of ['View warm-up targets','Swap exercise','Session note',
    'Pinned exercise note and setup']) assert.ok(tools.includes('"'+label+'"'));
assert.match(tools,/TooltipBox\(/);
assert.match(tools,/state=rememberTooltipState\(\)/);
assert.match(tools,/Icon\(icon,contentDescription=label\)/);
assert.match(tools,/Modifier\.size\(48\.dp\)/);
assert.match(tools,/horizontalScroll\(rememberScrollState\(\)\)/);
assert.doesNotMatch(tools,/\.record\(|\.save\(|\.finish\(|NativeKotlinCandidateHost/,
    'presentation tools must not create a second journal or note owner');
assert.match(generated,/NativeKotlinWorkoutTools\(/);
assert.match(generated,/externalOpen=pinnedExerciseId==selected\.target\.exerciseId/);
assert.match(generated,/showEditAction=showAll \|\| presentation\.finished/);
assert.match(generated,/toolTargets\.none\{it\.setKey in journal\.effectiveSets\}/);
assert.match(custom,/NativeKotlinWorkoutTools\(/);
assert.match(custom,/pinnedSlotId=slot\.slotId/);
assert.match(custom,/externalOpen=pinnedSlotId==slot\.slotId/);
assert.match(custom,/showEditAction=activeFocus==null \|\| finished/);
assert.match(sheet,/val editorOpen=opened \|\| externalOpen/);
assert.match(sheet,/if\(editorOpen\)NativeKotlinPinnedExerciseNoteSheet\(/);
assert.match(sheet,/custom,editorOpen\)/,
    'closing either note entry point must refresh the saved inline preview');
assert.match(sheet,/custom=custom,onDismiss=\{opened=false;onExternalDismiss\(\)\}/);
console.log('PASS owner-verified pinned notes, scoped Focus tools, durable CAS and shared sheet');
