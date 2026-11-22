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
assert.match(picker,/TextButton\([\s\S]*?Machine · \$display/);
const tools=readFileSync(root+'NativeKotlinWorkoutTools.kt','utf8');
assert.match(tools,/onMachine:\(\(\)->Unit\)\?=null/);
assert.match(tools,/machineLabel:String\?=null/);
assert.match(tools,/Icons\.Outlined\.Tune/);
assert.match(tools,/WorkoutTool\("Choose physical machine:/);
assert.match(picker,/showInlineTrigger:Boolean=true/);
assert.match(picker,/if\(showInlineTrigger\) \{/);
assert.match(picker,/LaunchedEffect\(externalOpen\)/);
assert.match(picker,/onExternalOpenConsumed\?\.invoke\(\)/);
// Focus no longer carries rare machine/swap icons in its tiny tool rail;
// both verified actions are available through the existing Exercise Info.
const info=readFileSync(root+'NativeKotlinWorkoutExerciseInfoSheet.kt','utf8');
assert.match(generated,/onMachine=null/);
assert.match(custom,/onMachine=null/);
assert.match(generated,/onSwap=null/);
assert.match(custom,/onSwap=null/);
assert.match(info,/onChooseMachine:\(\(\)->Unit\)\?=null/);
assert.match(info,/onSwapExercise:\(\(\)->Unit\)\?=null/);
assert.match(info,/Equipment and exercise/);
assert.match(info,/Planned working sets/);
assert.match(generated,/openPhysicalMachineFor=exerciseId/);
assert.match(custom,/openPhysicalMachineForSlot=slot\.slotId/);
assert.match(generated,/showInlineTrigger=showAll \|\| presentation\.finished/);
assert.match(custom,/showInlineTrigger=!focusOnNext \|\| finished/);
// Focus opens exactly the same sheet as Equipment unavailable; neither
// tool tap nor exercise navigation issues a load or alters logged sets.
for(const screen of [generated,custom]) {
  assert.match(screen,/NativeKotlinWorkoutTools\(/);
  assert.match(screen,/NativeKotlinWorkoutMachineSelector\(/);
}

const action=picker.slice(picker.indexOf('    TextButton('),
    picker.indexOf('    if(open)ModalBottomSheet('));
assert.doesNotMatch(action,/fillMaxWidth\(\)/,
  'physical machine choice must not occupy a full-width banner in Train');
assert.match(action,/heightIn\(min=48\.dp\)/);
assert.match(action,/widthIn\(max=260\.dp\)/);
assert.match(action,/contentDescription="Current physical machine: \$display\. Choose or add machine"/);
assert.match(action,/TextOverflow\.Ellipsis/);
assert.match(action,/enabled=true/);
assert.match(picker,/min=48\.dp/);
assert.match(picker,/if\(open\)ModalBottomSheet\(onDismissRequest=\{open=false\}\)/);
assert.match(picker,/verticalScroll\(rememberScrollState\(\)\)/);
assert.match(picker,/import androidx\.compose\.foundation\.verticalScroll/);
assert.match(picker,/min=52\.dp/);
assert.match(picker,/config\.machines\.filter \{it\.active \|\| it\.id==selectedId\}/);
assert.match(picker,/enabled=enabled && machine\.active && !current/);
assert.match(picker,/onSelect\(machine\.id\)/);
// Selection and add-now remain visible when the CAS save rejects or partially
// commits; only a newly published, verified station choice dismisses the sheet.
const savedAcknowledgement=picker.slice(
    picker.indexOf('LaunchedEffect(selectedId,config.machines,acknowledgedCreatedMachineId)'),
    picker.indexOf('    TextButton(',picker.indexOf('LaunchedEffect(selectedId,config.machines,acknowledgedCreatedMachineId)')));
assert.match(savedAcknowledgement,/pendingChoice!=null && selectedId==pendingChoice/);
assert.match(savedAcknowledgement,/config\.machines\.size>creation\.second/);
assert.match(savedAcknowledgement,/selectedId!=creation\.first/);
assert.match(savedAcknowledgement,/acknowledgedCreatedMachineId==selectedId/);
assert.match(picker,/createdMachineId=result\.choice\.machines\[exerciseId\]/);
for(const screen of [generated,custom])
  assert.match(screen,/acknowledgedCreatedMachineId=machineChoices\.createdMachineId/);
assert.match(savedAcknowledgement,/config\.machines\.any\{it\.id==selectedId\}/);
assert.match(savedAcknowledgement,/open=false/);
assert.match(savedAcknowledgement,/pendingCreation=null/);
assert.match(picker,/pendingChoice=machine\.id/);
assert.match(picker,/pendingCreation=selectedId to config\.machines\.size/);

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
assert.match(picker,/WorkoutLastReferenceDomain\.physicalStation\(previous,selectedMachineId\)/);
const lastAuthority=read('android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/WorkoutLastReferenceDomain.kt');
assert.match(lastAuthority,/history\.machineSets\.filter/);
assert.match(lastAuthority,/selectedMachineId==null && history\.machineSets\.isEmpty\(\)/);
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
assert.match(host,/CatalogPhysicalMachineEligibility\.eligibleExerciseIds\(source\.catalog\)/);
assert.match(host,/CatalogPhysicalMachineEligibility::eligible/);
assert.match(host,/EXERCISE_HAS_NO_MACHINE_STACK/);
const admission=host.slice(host.indexOf('fun addWorkoutPhysicalMachine('),
  host.indexOf('MACHINE_NAME_ALREADY_USED_OR_LIMIT_REACHED'));
assert.match(admission,/if\(source\.catalog\.singleOrNull\s*\{it\.exerciseId==exerciseId\}/);
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
// Live gym changes and storage failures must never trap the shared picker
// in a permanent busy state or guess that an unacknowledged selection saved.
// A retry is an explicit READ, not an implicit replay of the rejected write.
assert.match(picker,/val canRetry:Boolean/);
assert.match(picker,/val onRetry:\(\)->Unit/);
assert.match(picker,/suspend fun readVerifiedChoices\(refreshRoster:Boolean\)/);
assert.match(picker,/host\.recoverWorkoutMachineChoices\(workoutId,custom\)/);
assert.match(picker,/host\.readMachineRoster\(\)/);
assert.match(picker,/if\(latestRoster==null\) \{\s*saved=null/);
assert.match(picker,/catch\(cancelled:CancellationException\) \{\s*throw cancelled/);
assert.ok((picker.match(/finally \{\s*busy=false/g)||[]).length>=4,
  'initial read, manual retry, selection and creation must always unlock');
assert.match(picker,/onRetry=\{/);
assert.match(picker,/if\(!readVerifiedChoices\(refreshRoster=true\)\)/);
const selectionRecovery=picker.slice(
  picker.indexOf('onSelect={exerciseId,machineId->'),
  picker.indexOf('onCreate={exerciseId,label,makeDefault->'));
assert.match(selectionRecovery,/readVerifiedChoices\(refreshRoster=true\)/);
assert.match(selectionRecovery,/saved=null/);
assert.doesNotMatch(selectionRecovery,/onSelect\(machineId\)|recordSet\(|KotlinJournalOperation/);
const creationRecovery=picker.slice(
  picker.indexOf('onCreate={exerciseId,label,makeDefault->'),
  picker.indexOf('/** Stable compact saveable per-exercise choice;'));
assert.match(creationRecovery,/NativeWorkoutMachineCreationResult\.AddedNeedsSelection/);
assert.match(creationRecovery,/if\(current==null \|\| confirmedRoster==null\) \{/);
assert.match(creationRecovery,/Refresh verification before adding a physical machine/);
assert.match(creationRecovery,/readVerifiedChoices\(refreshRoster=false\)/);
assert.match(creationRecovery,/readVerifiedChoices\(refreshRoster=true\)/);
assert.match(creationRecovery,/createdMachineId=result\.choice\.machines\[exerciseId\]/);
assert.match(picker,/onRetry:\(\(\)->Unit\)\?=null/);
assert.match(picker,/retryEnabled:Boolean=false/);
assert.match(picker,/Refresh verified machine choices/);
for(const screen of [generated,custom]) {
  assert.match(screen,/onRetry=machineChoices\.onRetry/);
  assert.match(screen,/retryEnabled=machineChoices\.canRetry/);
  assert.match(screen,/Retry machine verification/);
}
const storeProof=readFileSync('android-native/native-coach-store/src/test/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinWorkoutMachineChoiceFileStoreTest.kt','utf8');
assert.match(storeProof,/staleMachineChoiceCanBeReopenedAndExplicitlyRetriedWithoutLosingOtherStations/);
assert.match(storeProof,/listOf\(false,true\)/);
assert.match(storeProof,/assertEquals\(otherWriter,reopened\)/);
assert.match(storeProof,/first\.machines\+\("press" to "B"\)/);
assert.match(storeProof,/reopened\.machines\+\("press" to "B"\)/);

console.log('PASS native Machine A/B: compact touch-safe picker, keyboard/scroll sheet, disabled inactive options, same engine CAS and isolated set/Last scope.');
