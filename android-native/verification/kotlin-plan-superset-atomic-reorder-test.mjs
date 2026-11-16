import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {join,dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const read=name=>readFileSync(join(root,name),'utf8');
const app='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const engine=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomCycleBuilderDomain.kt');
const policy=read(app+'NativeKotlinPlanSupersetOrdering.kt');
const plan=read(app+'NativeKotlinPlanDayEditor.kt');
const controller=read(app+'NativeKotlinCustomCycleController.kt');
const supersetEditor=read(app+'NativeKotlinSupersetEditorSheet.kt');
const test=read('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinPlanSupersetOrderingTest.kt');
const workflow=read('.github/workflows/android-kotlin-rebuild.yml');

for(const fragment of ['fun proposed(','direction !in listOf(-1,1)',
    'members.size !in 2..3','members.last().index-members.first().index+1!=members.size',
    'selectedSlotId','next.add(destination,moved)','next.flatMap']) {
    assert.ok(policy.includes(fragment),'Missing non-mutating atomic round reorder: '+fragment);
}
assert.match(plan,/earlier=NativeKotlinPlanSupersetOrdering\.proposed\(/);
assert.match(plan,/later=NativeKotlinPlanSupersetOrdering\.proposed\(/);
assert.match(controller,/NativeKotlinPlanSupersetOrdering\.proposed\(it,slotId,direction\)/);
assert.match(controller,/CustomCycleBuilderDomain\.reorderSlots\(/);
assert.match(engine,/val grouped=proposed\.withIndex\(\)\.filter\{it\.value\.supersetGroup!=null\}/);
assert.match(engine,/val noncontiguous=selected\.isNotEmpty\(\)/);
assert.match(engine,/noncontiguous \|\|/);
assert.match(supersetEditor,/val selectionContiguous=selected\.size in 2\.\.3/);
assert.match(supersetEditor,/enabled=!busy && !stale && allowSupersets && selectionContiguous/);
assert.match(supersetEditor,/Superset partners must be adjacent/);
assert.match(engine,/CustomCycleBuilderRefusal\.SLOT_ORDER_CONFLICT/);
for(const name of [
    'movingOneLinkedMemberMovesEntireGroupWithStableInnerOrder',
    'canonicalBuilderRejectsMaliciousScatteredRoundButAcceptsAtomicMove',
    'invalidOrNoncontiguousSupersetRemainsImmutableUntilUnlinked',
    'authorCannotCreateSeparatedSupersetButCanUnlinkOldMembership',
    'realNativeFileMoveAndUndoPreserveEachProgramSetAndOverride',
    'inspectingMovementOrderNeverWritesOwnerTrainingFiles'
])assert.ok(test.includes(name),'Missing native source-backed test '+name);
assert.match(workflow,/node android-native\/verification\/kotlin-plan-superset-atomic-reorder-test\.mjs/);
console.log('PASS Kotlin authored superset movement and canonical reorder checks retain whole linked rounds');
