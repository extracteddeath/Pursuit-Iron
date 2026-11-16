import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const source=(name)=>readFileSync(root+name+'.kt','utf8');
const host=source('NativeKotlinCandidateHost');
const plan=source('NativeKotlinPlanDayEditor');
const cycle=source('NativeKotlinCustomCycleCandidateScreen');
const journey=source('NativeKotlinCandidateJourney');
const gym=source('NativeKotlinGymSourceCandidateScreen');
const machines=source('NativeKotlinMachineAlternativesPanel');

assert.match(host,/fun reviewPlanPhysicalMachines\(/);
const review=host.slice(host.indexOf('fun reviewPlanPhysicalMachines('),
    host.indexOf('fun readMachineRoster('));
assert.match(review,/identity\.athleteId!=athleteId/);
assert.match(review,/revisionFence\.withOwnerTransaction\(identity\.ownerKey\)/);
assert.match(review,/source\.ownerKey!=identity\.ownerKey/);
assert.match(review,/source\.setup\.reviewed\?\.gym\?\.gymId!=gymId/);
assert.match(review,/NativeMachineRosterResult\.Saved/);
assert.match(review,/LoadBasis\.MACHINE_STACK,LoadBasis\.CABLE_STACK/);
assert.doesNotMatch(review,/machineRosters\.save\(|journalStore\.save\(|workoutMachineChoices\.save\(/);

assert.match(plan,/fun nativePlanMachineDescription\(/);
assert.match(plan,/exerciseId !in review\.eligibleExerciseIds/);
assert.match(plan,/it\.gymId==review\.gymId && it\.exerciseId==exerciseId/);
assert.match(plan,/val gymDefault=active\.singleOrNull/);
assert.match(plan,/Default not verified/);
assert.match(plan,/Manage Machine A\/B at my gym/);
assert.match(plan,/onManageMachine\(slot\.exerciseId\)/);
assert.match(plan,/Use the workout/);

assert.match(cycle,/reviewPlanPhysicalMachines\(athleteId,gymId\)/);
assert.match(cycle,/machineReview=planMachineReview/);
assert.match(cycle,/onManageMachine=onManagePhysicalMachine/);
assert.match(cycle,/planMachineReview=null/);
assert.match(journey,/var pendingMachineExerciseId by rememberSaveable/);
assert.match(journey,/onManagePhysicalMachine=\{exerciseId->/);
assert.match(journey,/navigate\(KotlinCandidateDestination\.GYM\)/);
assert.match(journey,/focusedMachineExerciseId=pendingMachineExerciseId/);
assert.match(journey,/onMachineFocusConsumed=\{pendingMachineExerciseId=null\}/);

const machinePosition=gym.indexOf('NativeKotlinMachineAlternativesPanel(');
const libraryPosition=gym.indexOf('NativeKotlinExerciseLibraryAccess(');
assert.ok(machinePosition>0 && libraryPosition>machinePosition,
    'machine group must be immediately discoverable above long exercise library');
assert.match(machines,/LaunchedEffect\(gymId,focusedMachineExerciseId,catalog\)/);
assert.match(machines,/catalog\.singleOrNull/);
assert.match(machines,/LoadBasis\.MACHINE_STACK/);
assert.match(machines,/LoadBasis\.CABLE_STACK/);
assert.match(machines,/stalePlanFocus=option==null/);
assert.match(machines,/exerciseId=""\s*query=""/);
assert.match(machines,/onMachineFocusConsumed\(\)/);
assert.doesNotMatch(machines.slice(machines.indexOf('LaunchedEffect('),
    machines.indexOf('var label by rememberSaveable')),
    /controller\.addPhysicalMachine\(|controller\.defaultPhysicalMachine\(/);
console.log('PASS Plan to Gym Machine A/B: source-backed review, honest defaults, no cross-gym or workout mutation, explicit focused navigation.');
