import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const picker=readFileSync(root+'NativeKotlinWorkoutMachineSelector.kt','utf8');
const generated=readFileSync(root+'NativeKotlinWorkoutCandidateScreen.kt','utf8');
const custom=readFileSync(root+'NativeKotlinCustomJournalScreen.kt','utf8');
const host=readFileSync(root+'NativeKotlinCandidateHost.kt','utf8');
const gym=readFileSync(root+'NativeKotlinMachineAlternativesPanel.kt','utf8');
// Both Kotlin journals must share the same compact presentation and keep the
// existing owner-verified, revision-fenced set-selection authority.
for(const call of [generated,custom])
  assert.match(call,/NativeKotlinWorkoutMachineSelector\(/);
assert.match(picker,/OutlinedButton\([\s\S]*?Physical machine · \$display/);
assert.match(picker,/min=48\.dp/);
assert.match(picker,/if\(open\)ModalBottomSheet\(onDismissRequest=\{open=false\}\)/);
assert.match(picker,/verticalScroll\(rememberScrollState\(\)\)/);
assert.match(picker,/import androidx\.compose\.foundation\.verticalScroll/);
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
const admission=host.slice(host.indexOf('fun addWorkoutPhysicalMachine('),
  host.indexOf('MACHINE_NAME_ALREADY_USED_OR_LIMIT_REACHED'));
assert.match(admission,/if\(source\.catalog\.singleOrNull \{it\.exerciseId==exerciseId\}/);
assert.doesNotMatch(admission,/previous\.machines\.isEmpty\(\) && source\.catalog\.none/,
  'an existing legacy machine group must not bypass current machine-stack eligibility');
for(const screen of [generated,custom]){
  assert.match(screen,/eligibleMachineExercises/);
  assert.match(screen,/workoutMachineEligibleExerciseIds\(/);
  assert.match(screen,/KotlinGymMachineAlternatives\(/);
}
// Saved physical stations must be visible at their exact gym without first
// searching for a hidden logical movement. Source choices stay owner/revision
// verified by the existing controller, not an ad-hoc UI roster writer.
assert.match(gym,/Saved Machine A\/B groups at this gym/);
// Only catalog-qualified stacks are selectable; older non-stack physical\n// groups remain visible as read-only historical identity, never reclassified.\nassert.match(gym,/it\.gymId==gymId && it\.exerciseId in verifiedMachineIds &&/);\nassert.match(gym,/it\.machines\.isNotEmpty\(\)/);\nassert.match(gym,/it\.exerciseId !in verifiedMachineIds/);\nassert.match(gym,/History only/);
assert.match(gym,/savedGroups\.take\(if\(showAllSaved\)savedGroups\.size else 5\)/);
assert.match(gym,/exerciseId=saved\.exerciseId/);
assert.match(gym,/Default: \$it/);
assert.match(gym,/val usedNames=config\?\.machines\.orEmpty\(\)/);
assert.match(gym,/firstOrNull\{it\.lowercase\(\) !in usedNames\}/);
assert.doesNotMatch(gym,/'B'\.code\+\(config\?\.machines\?\.size/);
console.log('PASS native Machine A/B: compact touch-safe picker, keyboard/scroll sheet, disabled inactive options, same engine CAS and isolated set/Last scope.');
