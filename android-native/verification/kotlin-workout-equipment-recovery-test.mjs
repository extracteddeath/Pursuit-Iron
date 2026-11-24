import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const recovery=readFileSync(dir+'NativeKotlinEquipmentRecovery.kt','utf8');
const machine=readFileSync(dir+'NativeKotlinWorkoutMachineSelector.kt','utf8');
const generated=readFileSync(dir+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(dir+'NativeKotlinCustomJournalScreen.kt','utf8');
const info=readFileSync(dir+'NativeKotlinWorkoutExerciseInfoSheet.kt','utf8');
const customSheet=readFileSync(dir+'NativeKotlinCustomExerciseSheet.kt','utf8');
const generatedSheet=readFileSync(dir+'NativeKotlinComposerSwapSheet.kt','utf8');
const tests=readFileSync(
    'android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinEquipmentRecoveryPolicyTest.kt','utf8');

// These actions now live in Exercise Info. Never resurrect the permanent
// recovery banner above the actual work/last/target editor.
assert.doesNotMatch(recovery,/NativeKotlinEquipmentRecoveryControl|@Composable|PursuitCard/);
assert.doesNotMatch(generated,/NativeKotlinEquipmentRecoveryControl\(/);
assert.doesNotMatch(custom,/NativeKotlinEquipmentRecoveryControl\(/);
assert.match(info,/Text\("Equipment and exercise"/);
assert.match(info,/onChooseMachine\?\.let/);
assert.match(info,/onSwapExercise\?\.let/);
assert.match(info,/unavailableReason\?\.takeIf/);
assert.match(info,/Planned working sets/);
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
assert.match(generated,/hasPhysicalMachineConfig=infoConfig!=null/);
assert.match(generated,/hasRecordedExerciseWork=targets\.any\{/);
assert.match(generated,/onChooseMachine=if\(!infoWarmupsOnly && infoOptions\.canChangeStation\)/);
// A generated workout may contain more than one occurrence of the same lift.
 // In that case an exerciseId-wide swap or skip is ambiguous and the Info
 // sheet must hide it, while retaining exact single-set actions.
assert.match(generated,/val multipleOccurrences=presentation\?\.exercises\.orEmpty\(\)\.count/);
assert.match(generated,/onSwapExercise=if\(!infoWarmupsOnly && !multipleOccurrences &&\s*infoOptions\.canReplaceExercise\)/);
assert.match(generated,/!multipleOccurrences &&\s*infoActions\.mayRequestSkipRemaining/);
assert.match(generated,/unavailableReason=if\(multipleOccurrences && !infoWarmupsOnly\)/);
assert.match(generated,/bulk skip and workout-only/);
assert.match(generated,/openPhysicalMachineFor=exerciseId/);
assert.match(generated,/reviewSwap\(exerciseId\)/);
// Opening a physical station is not an instruction to abandon the user's
// edited working set (or navigate back to the first warm-up).
const generatedMachineInfo=generated.slice(
  generated.indexOf('onChooseMachine=if(!infoWarmupsOnly'),
  generated.indexOf('onSwapExercise=if(!infoWarmupsOnly'));
assert.ok(generatedMachineInfo.includes('openPhysicalMachineFor=exerciseId'));
assert.doesNotMatch(generatedMachineInfo,/selectedKey=|controller\.record\(/);
assert.match(generated,/openPhysicalMachineFor==selected\.target\.exerciseId/);
assert.match(generated,/onExternalOpenConsumed=\{openPhysicalMachineFor=null\}/);
assert.match(generated,/host\.reviewComposerWorkoutSwapChoices\(workoutId,exerciseId\)/);
assert.match(generated,/machineChoices\.onSelect\(exerciseId,machineId\)/);

assert.match(custom,/NativeKotlinEquipmentRecoveryPolicy\.review\(/);
assert.match(custom,/linkedSuperset=slot\.supersetGroup!=null/);
assert.match(custom,/hasRecordedExerciseWork=infoRecorded/);
assert.match(custom,/onChooseMachine=if\(!infoWarmupsOnly && infoOptions\.canChangeStation\)/);
assert.match(custom,/onSwapExercise=if\(!infoWarmupsOnly && infoOptions\.canReplaceExercise\)/);
assert.match(custom,/unavailableReason=if\(!infoWarmupsOnly/);
assert.match(custom,/openPhysicalMachineForSlot=slot\.slotId/);
assert.match(custom,/reviewSwap\(slot\)/);
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
const copyStart=customSheet.indexOf('forms=forms.mapIndexed {otherIndex,other->');
const copyEnd=customSheet.indexOf('Use these NEW targets for other working sets',copyStart);
assert.ok(copyStart>=0 && copyEnd>copyStart);
const newTargetCopy=customSheet.slice(copyStart,copyEnd);
assert.match(newTargetCopy,/rest=fields\.rest\)/);
assert.doesNotMatch(newTargetCopy,/amplifier=null|extraReps=""|extraLoad=""/);
assert.match(customSheet,/Each set keeps its own amplifier and follow-up edits/);
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
