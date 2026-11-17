import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const host=readFileSync(root+'NativeKotlinCandidateHost.kt','utf8');
const controller=readFileSync(root+'NativeKotlinGymSourceController.kt','utf8');
const panel=readFileSync(root+'NativeKotlinMachineAlternativesPanel.kt','utf8');
const policy=readFileSync(root+'NativeKotlinMachineSourceScope.kt','utf8');

// This is a REAL mutable-boundary requirement, not only a visual filter.
// A malicious/stale UI request cannot bypass source eligibility.
const save=host.slice(host.indexOf('fun saveMachineRoster('),
    host.indexOf('private data class IssuedMachineWorkoutScope'));
assert.match(save,/revisionFence\.withOwnerTransaction\(identity\.ownerKey\)/);
assert.match(save,/source\.ownerKey!=identity\.ownerKey/);
assert.match(save,/KotlinMachineAlternativesDomain\.preservesOtherGyms/);
assert.match(save,/nativeMachineRosterSourceEligible\(/);
assert.match(save,/CatalogPhysicalMachineEligibility\.eligibleExerciseIds\(source\.catalog\)/);
assert.match(save,/EXERCISE_HAS_NO_MACHINE_STACK/);
assert.match(save,/machineRosters\.save\(identity\.ownerKey/);
assert.ok(save.indexOf('nativeMachineRosterSourceEligible(') <
    save.indexOf('machineRosters.save(identity.ownerKey'),
    'source admission must precede any physical roster mutation');

const adding=controller.slice(controller.indexOf('fun addPhysicalMachine('),
    controller.indexOf('/** Edits gym stock only'));
assert.match(adding,/source\.catalog\.singleOrNull\{it\.exerciseId==exerciseId\}/);
assert.match(adding,/CatalogPhysicalMachineEligibility::eligible/);
assert.doesNotMatch(adding,/require\(source\.catalog\.any/);

assert.match(policy,/previous\.filter \{/);
assert.match(policy,/proposed\.filter \{/);
assert.match(policy,/it\.gymId==gymId && it\.exerciseId !in eligibleExerciseIds/);
assert.match(policy,/return historical==after/);
assert.doesNotMatch(policy,/\.save\(|Journal|WorkoutMachineChoice/);

const generated=readFileSync(root+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(root+'NativeKotlinCustomJournalScreen.kt','utf8');
const selection=host.slice(host.indexOf('fun selectWorkoutPhysicalMachine('),
    host.indexOf('fun workoutMachineEligibleExerciseIds('));
assert.match(selection,/source\.catalog\.singleOrNull\{it\.exerciseId==exerciseId\}/);
assert.match(selection,/EXERCISE_HAS_NO_MACHINE_STACK/);
const addNow=host.slice(host.indexOf('fun addWorkoutPhysicalMachine('),
    host.indexOf('Read-only profile/settings summary',host.indexOf('fun addWorkoutPhysicalMachine(')));
assert.match(addNow,/source\.catalog\.singleOrNull\s*\{it\.exerciseId==exerciseId\}/);
assert.match(addNow,/EXERCISE_HAS_NO_MACHINE_STACK/);
assert.ok(addNow.indexOf('source.catalog.singleOrNull') <
    addNow.indexOf('machineRosters.save(signed.ownerKey'),
    'a duplicate or ambiguous catalog identity can never create a station');

assert.ok(selection.indexOf('source.catalog.singleOrNull') <
    selection.indexOf('workoutMachineChoices.save('));
const recovery=host.slice(host.indexOf('fun recoverWorkoutMachineChoices('),
    host.indexOf('fun selectWorkoutPhysicalMachine('));
assert.match(recovery,/val eligible=if\(source!=null/);
assert.match(recovery,/val machine=bound\[exercise\] \?: if\(exercise in eligible\)/);
assert.match(recovery,/scope\.observedMachines\.forEach/);
assert.match(custom,/val config=if\(slot\.exerciseId in eligibleMachineExercises\)/);
assert.match(generated,/if\(exerciseId in eligibleMachineExercises\)/);
// The same live-source qualification must guard EACH working-set gate;
// otherwise a historical fake barbell Machine A/B can make set completion
// impossible when its picker has (correctly) been hidden.
assert.match(generated,/val perSetMachineConfig=if\(set\.target\.exerciseId in eligibleMachineExercises\)/);
assert.match(generated,/else null\s+val perSetMachineId=/);
assert.match(panel,/val verifiedMachineIds=CatalogPhysicalMachineEligibility/);
assert.match(panel,/\.eligibleExerciseIds\(catalog\)/);
assert.match(panel,/ex\.exerciseId in verifiedMachineIds/);
assert.match(panel,/it\.exerciseId in verifiedMachineIds/);
assert.match(panel,/it\.exerciseId !in verifiedMachineIds/);
assert.match(panel,/historicalGroups\.take\(5\)/);
assert.match(panel,/History only/);
assert.match(panel,/exerciseId in verifiedMachineIds && label\.isNotBlank\(\)/);
console.log('PASS Machine A/B gym admission: live owner source, exact stack identities, no legacy mutation, visible historical groups and safe add controls.');
