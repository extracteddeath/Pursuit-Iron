import assert from 'node:assert/strict';
import fs from 'node:fs';

// Fast UI source contract; real Android activity tests and physical-device
// acceptance remain separate beta gates.
const source=fs.readFileSync(
  'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt','utf8'
);
for(const required of [
  'var priorLevelingSlotId by rememberSaveable(workoutId)',
  'referenceMode==WorkoutReferenceMode.LAST ||',
  'priorLevelingSlotId!=null',
  '"Leveling · review previous workout"',
  'DoubleProgressionLevelingDomain',
  '.reviewLastForCurrentWorkout(',
  'WorkoutReferenceKey(set.setKey) to',
  'previous=physicalMachineLastReferences(',
  'machineInstanceId=selectedMachineId',
  'goalRir=requireNotNull(first.targetRir)',
  'verifiedStock=stock',
  'unfinished.forEach { set->',
  'currentEditors[levelEditorKey(',
  'levelVersion++',
  'PursuitPreviousWorkoutLevelingDialog(',
  'onApply={',
  'observed=observed',
  'onDismiss={priorLevelingSlotId=null}'
]) assert.ok(source.includes(required),
  'Custom prior-session Leveling must be a source-verified draft-only action: '+required);
assert.ok(source.includes('CustomWorkoutVisitPart.MAIN) !in current.effectiveVisits'),
  'Only unrecorded main working visits are eligible for prefill');
assert.ok(source.includes('!requiresNewMachineChoice'),
  'An inactive or unselected physical machine cannot grant Leveling authority');
assert.ok(!source.includes('controller.finish()'),
  'Custom Leveling UI may not bypass the ordinary custom journal finish authority');
console.log('PASS custom previous-workout Leveling is opt-in, station-safe, and draft-only');
