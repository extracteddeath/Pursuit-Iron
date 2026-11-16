import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const read=(f)=>readFileSync(join(root,f),'utf8');
const app='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const engine=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomCycleBuilderDomain.kt');
const preview=read(app+'NativeKotlinPlanStructureRemoval.kt');
const sheets=read(app+'NativeKotlinPlanStructureRemovalSheets.kt');
const ctrl=read(app+'NativeKotlinCustomCycleController.kt');
const screen=read(app+'NativeKotlinCustomCycleCandidateScreen.kt');
const navigator=read(app+'NativeKotlinPlanEditorNavigator.kt');
const day=read(app+'NativeKotlinPlanDayEditor.kt');
const test=read('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinPlanStructureRemovalTest.kt');
const ci=read('.github/workflows/android-kotlin-rebuild.yml');

for(const fragment of ['fun removeDay(','fun removeBlock(',
    'remove-training-day','remove-training-block',
    'current.weekOverrides.filterKeys{it!=blockId}',
    'overrides.filterNot{it.weekday==weekday}']) {
    assert.ok(engine.includes(fragment),'Missing canonical Kotlin domain operation '+fragment);
}
for(const fragment of ['fun day(','fun block(','fun confirmedDay(',
    'fun confirmedBlock(','current.revision!=review.cycleRevision',
    'CustomCycleBuilderDomain.removeDay(',
    'CustomCycleBuilderDomain.removeBlock(',
    'overrides.map{Triple(it.weekInBlock,it.weekday,it.slotId)}']) {
    assert.ok(preview.includes(fragment),'Missing revision-gated exact Plan review '+fragment);
}
for(const fragment of ['Remove training day?','Remove entire training block?',
    'review.overrideWeeks','review.overrideWorkingSets',
    'review.shiftsLaterBlockWeeks','stillCurrent',
    'Modifier.fillMaxWidth().heightIn(min=48.dp)',
    'Undo is available immediately']) {
    assert.ok(sheets.includes(fragment),'Missing disclosed, accessible confirmation '+fragment);
}
assert.match(ctrl,/fun removeReviewedDay\(review:NativePlanDayRemovalReview\) = edit/);
assert.match(ctrl,/fun removeReviewedBlock\(review:NativePlanBlockRemovalReview\) = edit/);
assert.match(day,/onRemoveDay:\(\)->Unit/);
assert.match(day,/Text\("Remove training day"\)/);
assert.match(navigator,/onRemoveBlock:\(String\)->Unit/);
assert.match(navigator,/Text\("Remove block"\)/);
for(const fragment of ['removeDayReview=NativeKotlinPlanStructureRemoval.day(',
    'removeBlockReview=NativeKotlinPlanStructureRemoval.block(',
    'val stillCurrent=NativeKotlinPlanStructureRemoval.day(',
    'val stillCurrent=NativeKotlinPlanStructureRemoval.block(',
    'controller.removeReviewedDay(review)',
    'controller.removeReviewedBlock(review)',
    'NativeKotlinPlanDayRemovalSheet(',
    'NativeKotlinPlanBlockRemovalSheet(']) {
    assert.ok(screen.includes(fragment),'Missing real Plan UI/write path '+fragment);
}
for(const name of [
    'deletingDayColdSavesOnlyItsFutureOverridesAndUndoRestoresEverything',
    'removingOnlyBlockFailsButSelectedBlockAndOverridesCanBeRemoved',
    'fullBlockDeleteOnDiskAndUndoRemainOwnedAndAtomic',
    'staleOrRetargetedDialogsCannotDeleteADifferentDayOrBlock',
    'orphanedAmbiguousOverrideCannotBecomeDeletionConsent',
    'reviewingDeleteOptionsDoesNotTouchPrivateStore'
]) {
    assert.ok(test.includes(name),'Missing real owner-store regression '+name);
}
assert.match(ci,/node android-native\/verification\/kotlin-plan-structure-removal-test\.mjs/);
console.log('PASS native custom Plan day/block removals: exact preview, guarded consent, canonical Kotlin writer, cold recovery and undo');
