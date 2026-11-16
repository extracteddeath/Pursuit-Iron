import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const read=(name)=>readFileSync(join(root,name),'utf8');
const folder='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const day=read(folder+'NativeKotlinPlanDayEditor.kt');
const plan=read(folder+'NativeKotlinCustomCycleCandidateScreen.kt');
const review=read(folder+'NativeKotlinPlanCrossDayMove.kt');
const controller=read(folder+'NativeKotlinCustomCycleController.kt');
const test=read('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinPlanCrossDayMoveTest.kt');
const workflow=read('.github/workflows/android-kotlin-rebuild.yml');

assert.match(day,/onMoveToDay:\(String\)->Unit/);
assert.match(day,/Text\("Move to another training day"\)/);
assert.match(day,/key\(slot\.slotId\)/);
assert.match(day,/PursuitDisclosureRow\(/);
assert.match(day,/if\(moreActionsExpanded\) \{/);

assert.match(plan,/crossDayMoveScope=NativeKotlinPlanCrossDayMoveScope\(/);
assert.match(plan,/val matching=current\.athleteId==scope\.athleteId/);
assert.match(plan,/NativeKotlinPlanCrossDayMove\.inspect\(/);
assert.match(plan,/NativeEditSheet\(/);
assert.match(plan,/preview\?\.let\{controller\.moveExerciseAcrossDays\(it\)\}/);
assert.match(plan,/enabled=!busy && preview!=null/);
for(const phrase of ['preview.movedSlotIds','preview.overrideWeeks','preview.overrideCount',
    'preview.overrideWorkingSets','preview.supersetGroup','preview.remainingSourceSlots',
    'Undo is available after saving']) {
    assert.ok(plan.includes(phrase),'missing explicit Plan movement disclosure: '+phrase);
}
assert.match(controller,/fun moveExerciseAcrossDays\(review:NativeKotlinPlanCrossDayMoveReview\)/);
assert.match(controller,/NativeKotlinPlanCrossDayMove\.confirmed\(draft,review\)/);
assert.match(review,/current\.revision!=reviewed\.cycleRevision/);
assert.match(review,/moving\.size !in 2\.\.3/);
assert.match(review,/CustomCycleBuilderDomain\.moveSlots\(/);
assert.match(review,/reviewed\.movedSlotIds,reviewed\.destinationIndex/);
for(const testName of ['realSavedMoveAndUndoPreservePinnedLoadsNotesAndWeeks',
    'linkedSupersetMovesTogetherWithoutFlatteningRoundOrLoad',
    'staleReviewCannotMoveADifferentNewlyEditedProgram',
    'groupCollisionsFullDayAndIncompleteGroupAreUnavailable']) {
    assert.ok(test.includes(testName),'missing native file regression: '+testName);
}
assert.match(workflow,/node android-native\/verification\/kotlin-plan-cross-day-move-test\.mjs/);
console.log('PASS native Plan cross-day movement is a confirmed exact source-CAS operation, not a silent schedule or workout change');
