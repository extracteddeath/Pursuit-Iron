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
console.log('PASS native Machine A/B: compact touch-safe picker, keyboard/scroll sheet, disabled inactive options, same engine CAS and isolated set/Last scope.');
