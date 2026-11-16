import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const picker=readFileSync(root+'NativeKotlinWorkoutMachineSelector.kt','utf8');
const generated=readFileSync(root+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(root+'NativeKotlinCustomJournalScreen.kt','utf8');
const host=readFileSync(root+'NativeKotlinCandidateHost.kt','utf8');
// Both Kotlin journals must share the same compact presentation and keep the
// existing owner-verified, revision-fenced set-selection authority.
for(const call of [generated,custom])
  assert.match(call,/NativeKotlinWorkoutMachineSelector\(/);
assert.match(picker,/OutlinedButton\([\s\S]*?Physical machine · \$display/);
assert.match(picker,/min=48\.dp/);
assert.match(picker,/if\(open\)ModalBottomSheet\(onDismissRequest=\{open=false\}\)/);
assert.match(picker,/verticalScroll\(rememberScrollState\(\)\)/);
assert.match(picker,/min=52\.dp/);
assert.match(picker,/config\.machines\.filter \{it\.active \|\| it\.id==selectedId\}/);
assert.match(picker,/enabled=enabled && machine\.active && !current/);
assert.match(picker,/onSelect\(machine\.id\)/);
const selectingOption=picker.slice(picker.indexOf('active.forEach {machine->'),
    picker.indexOf('if(onCreate!=null)'));
assert.doesNotMatch(selectingOption,/open=false/,
  'never dismiss picker before the machine choice is verified by the writer');
const creatingOption=picker.slice(picker.indexOf('onCreate(newMachineName.trim()'),
    picker.indexOf('enabled=enabled && config.machines.size<32'));
assert.doesNotMatch(creatingOption,/newMachineName=""|makeDefault=false/,
  'retain unsaved input when roster or workout-choice save fails');
assert.match(picker,/Different machine not saved/);
assert.doesNotMatch(picker,/FilterChip\(selected=/);
assert.doesNotMatch(picker,/journalStore\.save\(|machineRosters\.save\(|KotlinJournalOperation\./);
assert.match(host,/fun selectWorkoutPhysicalMachine\(/);
assert.match(host,/scope\.finished \|\| exerciseId !in scope\.exerciseIds/);
assert.match(host,/prior\.snapshot\.revision!=expectedRevision/);
assert.match(picker,/initialMachineScopedLoad\(/);
assert.match(picker,/saved\.machineInstanceId==selectedMachineId/);
assert.match(picker,/physicalMachineLastReferences\(/);
assert.match(picker,/filterKeys \{ it\.machineId==requested \}/);
// Creating a surprise physical machine must remain on an actual workout +
// exact source gym, never act as a new program prescription or a JS fallback.
assert.match(picker,/onCreate:\(String,String,Boolean\)->Unit/);
assert.match(picker,/host\.addWorkoutPhysicalMachine\(/);
assert.match(picker,/NativeWorkoutMachineCreationResult\.AddedNeedsSelection/);
assert.match(picker,/onRosterChanged\(result\.roster\)/);
assert.match(picker,/Add machine & use now/);
assert.match(picker,/error\?\.let \{Text\(it/);
for(const call of [generated,custom])assert.match(call,/error=machineChoices\.error/);
assert.match(picker,/makeDefault/);
for(const call of [generated,custom]) {
  assert.match(call,/onRosterChanged=\{machineRoster=it\}/);
  assert.match(call,/machineChoices\.onCreate\(/);
}
assert.match(host,/fun addWorkoutPhysicalMachine\(/);
assert.match(host,/expectedRosterRevision:Long/);
assert.match(host,/expectedChoiceRevision:Long/);
assert.match(host,/reviewedGym==null \|\| reviewedGym\.gymId!=scope\.gymId/);
assert.match(host,/scope\.finished \|\| exerciseId !in scope\.exerciseIds/);
assert.match(host,/source\.catalog\.none\{it\.exerciseId==exerciseId\}/);
assert.match(host,/previous\.machines\.size>=32/);
assert.match(host,/it\.label\.equals\(name,ignoreCase=true\)/);
assert.match(host,/machineRosters\.save\(signed\.ownerKey/);
assert.match(host,/workoutMachineChoices\.save\(signed\.ownerKey,workoutId/);
assert.match(host,/NativeWorkoutMachineCreationResult\.AddedNeedsSelection\(/);
const createSection=host.slice(host.indexOf('fun addWorkoutPhysicalMachine('),
  host.indexOf('Read-only profile/settings summary',host.indexOf('fun addWorkoutPhysicalMachine(')));
assert.ok(createSection.length>1000);
assert.doesNotMatch(createSection,/journalStore\.save\(|customIssuedStore\.save\(|\.finish\(/);
assert.match(picker,/config==null \|\| \(config\.machines\.isEmpty\(\) && onCreate==null\)/);
assert.match(host,/fun workoutMachineEligibleExerciseIds\(/);
assert.match(host,/LoadBasis\.MACHINE_STACK,LoadBasis\.CABLE_STACK/);
assert.match(host,/EXERCISE_HAS_NO_MACHINE_STACK/);
for(const screen of [generated,custom]){
  assert.match(screen,/eligibleMachineExercises/);
  assert.match(screen,/workoutMachineEligibleExerciseIds\(/);
  assert.match(screen,/KotlinGymMachineAlternatives\(/);
}
console.log('PASS native Machine A/B: compact touch-safe picker, keyboard/scroll sheet, disabled inactive options, same engine CAS and isolated set/Last scope.');
