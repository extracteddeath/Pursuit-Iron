import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const recovery=readFileSync(dir+'NativeKotlinEquipmentRecovery.kt','utf8');
const machine=readFileSync(dir+'NativeKotlinWorkoutMachineSelector.kt','utf8');
const generated=readFileSync(dir+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(dir+'NativeKotlinCustomJournalScreen.kt','utf8');
const customSheet=readFileSync(dir+'NativeKotlinCustomExerciseSheet.kt','utf8');
const generatedSheet=readFileSync(dir+'NativeKotlinComposerSwapSheet.kt','utf8');
const tests=readFileSync(
    'android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinEquipmentRecoveryPolicyTest.kt','utf8');

assert.match(recovery,/Equipment unavailable\? Change machine or exercise/);
assert.match(recovery,/Same lift · different physical machine/);
assert.match(recovery,/Different exercise · workout only/);
assert.match(recovery,/if\(expanded\)PursuitCard/);
assert.match(recovery,/activeWorkout && hasPhysicalMachineConfig/);
assert.match(recovery,/activeWorkout && !hasRecordedExerciseWork && !linkedSuperset/);
assert.doesNotMatch(recovery,/onRecord\(|onSave\(|RecordSet\(|issueWorkout\(|KotlinPerformedSet\(/);
assert.match(machine,/externalOpen:Boolean=false/);
assert.match(machine,/onExternalOpenConsumed:\(\(\)->Unit\)\?=null/);
assert.match(machine,/LaunchedEffect\(externalOpen\)/);
assert.match(machine,/if\(externalOpen\) \{/);
assert.match(machine,/onRequestHistory\?\.invoke\(\)/);
assert.match(machine,/onExternalOpenConsumed\?\.invoke\(\)/);
assert.match(machine,/pendingChoice=machine\.id/);
assert.match(machine,/onSelect\(machine\.id\)/);
assert.match(machine,/onCreate\(newMachineName\.trim\(\)\.ifEmpty\{suggestedName\},makeDefault\)/);

assert.match(generated,/NativeKotlinEquipmentRecoveryPolicy\.review\(/);
assert.match(generated,/NativeKotlinEquipmentRecoveryControl\(/);
assert.match(generated,/hasPhysicalMachineConfig=activeMachineConfig!=null/);
assert.match(generated,/hasRecordedExerciseWork=recorded/);
assert.match(generated,/onChangeStation=\{openPhysicalMachineFor=exercise\}/);
assert.match(generated,/onReplaceExercise=\{reviewSwap\(exercise\)\}/);
assert.match(generated,/openPhysicalMachineFor==selected\.target\.exerciseId/);
assert.match(generated,/onExternalOpenConsumed=\{openPhysicalMachineFor=null\}/);
assert.match(generated,/host\.reviewComposerWorkoutSwapChoices\(workoutId,exerciseId\)/);
assert.match(generated,/machineChoices\.onSelect\(exerciseId,machineId\)/);

assert.match(custom,/NativeKotlinEquipmentRecoveryPolicy\.review\(/);
assert.match(custom,/NativeKotlinEquipmentRecoveryControl\(/);
assert.match(custom,/linkedSuperset=slot\.supersetGroup!=null/);
assert.match(custom,/hasRecordedExerciseWork=recorded/);
assert.match(custom,/onChangeStation=\{openPhysicalMachineForSlot=slot\.slotId\}/);
assert.match(custom,/onReplaceExercise=\{reviewSwap\(slot\)\}/);
assert.match(custom,/externalOpen=openPhysicalMachineForSlot==slot\.slotId/);
assert.match(custom,/onExternalOpenConsumed=\{openPhysicalMachineForSlot=null\}/);
assert.match(custom,/machineChoices\.onSelect\(slot\.exerciseId,machineId\)/);
assert.match(custom,/host\.readCustomAuthoringSource\(\)/);
assert.match(custom,/onSave=\{exerciseId,sets,note->/);
assert.match(custom,/confirmSwap\(slotId,exerciseId,sets,note\)/);

assert.match(customSheet,/fun freshReplacementStructure\(\):List<CustomSetFields>/);
assert.match(customSheet,/replacing\?\.sets\?\.takeIf\{it\.size in 1\.\.20\}/);
assert.match(customSheet,/\.map\{CustomSetFields\(type=it\.type\)\}/);
assert.match(customSheet,/forms=freshReplacementStructure\(\)/);
assert.match(customSheet,/Copy original timing ONLY \(optional\)/);
assert.match(customSheet,/Use these NEW targets for other working sets/);
assert.match(customSheet,/if\(replacing!=null && !swapConfirmation\)/);
assert.match(customSheet,/swapConfirmation=true/);
assert.match(customSheet,/if\(sets!=null\)onSave\(exerciseId,sets,note\)/);
assert.doesNotMatch(customSheet,/forms=replacing\.sets\.map\{it\.toFields\(\)\}/);
assert.match(generatedSheet,/Use this exercise's reviewed rep range/);
assert.match(generatedSheet,/sourceTargets\.forEachIndexed \{i,target->/);
assert.match(generatedSheet,/if\(target\.type==DraftSetType\.WORKING\)/);
assert.match(generatedSheet,/min=range\.minimum\.toString\(\)/);
assert.match(generatedSheet,/max=range\.maximum\.toString\(\)/);
assert.match(generatedSheet,/sourceTargets\.map \{ComposerSwapDraftRow\(\)\}/);

for(const t of [
 'sameExercisePhysicalMachineSwitchIsSeparateFromMovementReplacement',
 'noMachineConfigStillAllowsVerifiedExerciseReplacement',
 'actualRecordedWorkBlocksReplacementWithoutDisablingStationSelection',
 'linkedSupersetRequiresReviewOfWholeGroup',
 'finishedOrUnavailableWorkoutCannotOpenEitherMutation'])
  assert.ok(tests.includes(t),'Missing Kotlin equipment recovery policy test: '+t);
console.log('PASS: both Train routes expose owner-verified station vs workout-only exercise changes; copied targets remain opt-in and new.');
