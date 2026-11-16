import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {join,resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const read=(name)=>readFileSync(join(root,name),'utf8');
const base='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const editor=read(base+'NativeKotlinPlanDayEditor.kt');
const screen=read(base+'NativeKotlinCustomCycleCandidateScreen.kt');
const controller=read(base+'NativeKotlinCustomCycleController.kt');
const removal=read(base+'NativeKotlinPlanExerciseRemoval.kt');
const regression=read('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinPlanExerciseRemovalTest.kt');
const build=read('.github/workflows/android-kotlin-rebuild.yml');

assert.match(editor,/onRemoveExercise:\(String\)->Unit/);
assert.match(editor,/Text\("Remove exercise from program"\)/);
assert.match(editor,/enabled=!busy/);
assert.match(editor,/remove=slot\.supersetGroup==null/);
assert.match(screen,/removeExerciseReview=NativeKotlinPlanExerciseRemoval\.inspect\(/);
assert.match(screen,/val stillCurrent=NativeKotlinPlanExerciseRemoval\.inspect\(/);
assert.match(screen,/controller\.removeExercise\(review\)/);
assert.match(screen,/enabled=!busy && stillCurrent/);
for(const fragment of ['review.overriddenWeeks','review.overrideWorkingSets',
    'review.pinned','review.baseWorkingSets','review.blockWeeks',
    'review.remainingExercises','Undo immediately','Keep exercise']) {
    assert.ok(screen.includes(fragment),'Explicit removal review is missing '+fragment);
}
assert.match(controller,/fun removeExercise\(review:NativeKotlinPlanExerciseRemovalReview\) = edit/);
assert.match(controller,/NativeKotlinPlanExerciseRemoval\.confirmed\(draft,review\)/);
assert.match(removal,/cycleRevision:Long/);
assert.match(removal,/current\.revision!=reviewed\.cycleRevision/);
assert.match(removal,/slot\.supersetGroup!=null/);
assert.match(removal,/CustomCycleBuilderDomain\.removeSlot\(/);
assert.match(removal,/confirmPinned=true/);
assert.match(regression,/removalAndUndoColdReplayKeepOtherDaysAndTheirPrescriptions/);
assert.match(regression,/staleDialogAndChangedAuthoredWeekCannotEraseDifferentSlot/);
assert.match(regression,/linkedSupersetsAndAmbiguousSlotIdentityCannotBeRemovedByThisRoute/);
assert.match(regression,/previewNeverTouchesOwnerPrivateTrainingStore/);
assert.match(build,/node android-native\/verification\/kotlin-plan-exercise-removal-test\.mjs/);
console.log('PASS confirmed source-scoped custom Plan removal, pinned/override disclosure, CAS, undo and cold-recovery test wiring');
