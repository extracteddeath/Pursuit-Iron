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
assert.match(save,/source\.catalog\.groupBy \{it\.exerciseId\}/);
assert.match(save,/records\.size==1|entries\.size==1/);
assert.match(save,/LoadBasis\.MACHINE_STACK,LoadBasis\.CABLE_STACK/);
assert.match(save,/EXERCISE_HAS_NO_MACHINE_STACK/);
assert.match(save,/machineRosters\.save\(identity\.ownerKey/);
assert.ok(save.indexOf('nativeMachineRosterSourceEligible(') <
    save.indexOf('machineRosters.save(identity.ownerKey'),
    'source admission must precede any physical roster mutation');

const adding=controller.slice(controller.indexOf('fun addPhysicalMachine('),
    controller.indexOf('/** Edits gym stock only'));
assert.match(adding,/source\.catalog\.singleOrNull\{it\.exerciseId==exerciseId\}/);
assert.match(adding,/LoadBasis\.MACHINE_STACK,LoadBasis\.CABLE_STACK/);
assert.doesNotMatch(adding,/require\(source\.catalog\.any/);

assert.match(policy,/previous\.filter \{/);
assert.match(policy,/proposed\.filter \{/);
assert.match(policy,/it\.gymId==gymId && it\.exerciseId !in eligibleExerciseIds/);
assert.match(policy,/return historical==after/);
assert.doesNotMatch(policy,/\.save\(|Journal|WorkoutMachineChoice/);

assert.match(panel,/val verifiedMachineIds=catalog\.groupBy/);
assert.match(panel,/records\.size==1/);
assert.match(panel,/ex\.exerciseId in verifiedMachineIds/);
assert.match(panel,/it\.exerciseId in verifiedMachineIds/);
assert.match(panel,/it\.exerciseId !in verifiedMachineIds/);
assert.match(panel,/historicalGroups\.take\(5\)/);
assert.match(panel,/History only/);
assert.match(panel,/exerciseId in verifiedMachineIds && label\.isNotBlank\(\)/);
console.log('PASS Machine A/B gym admission: live owner source, exact stack identities, no legacy mutation, visible historical groups and safe add controls.');
