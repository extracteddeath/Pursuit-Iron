import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const panel=readFileSync(root+'NativeKotlinMachineAlternativesPanel.kt','utf8');
const ui=readFileSync(root+'NativeKotlinMachineRenameControl.kt','utf8');
const controller=readFileSync(root+'NativeKotlinGymSourceController.kt','utf8');
const host=readFileSync(root+'NativeKotlinCandidateHost.kt','utf8');

assert.match(panel,/NativeKotlinMachineRenameControl\(/);
assert.match(panel,/controller\.renamePhysicalMachine\(exerciseId,machine\.id,newLabel\)/);
assert.match(ui,/rememberSaveable\(gymId,machine\.id,machine\.label\)/);
assert.match(ui,/pendingName!=null && machine\.label==pendingName/);
assert.match(ui,/it\.id!=machine\.id && it\.label\.trim\(\)\.equals\(candidate,ignoreCase=true\)/);
assert.match(ui,/enabled=!busy && changed && candidate\.isNotBlank\(\) && !duplicate/);
assert.match(ui,/Text\("Save machine name"\)/);

const adding=controller.slice(controller.indexOf('fun addPhysicalMachine('),
  controller.indexOf('/** Edits gym stock only'));
assert.match(adding,/current\.machines\.none \{/);
assert.match(adding,/it\.label\.trim\(\)\.equals\(label\.trim\(\),ignoreCase=true\)/);
const renaming=controller.slice(controller.indexOf('fun renamePhysicalMachine('),
  controller.indexOf('override fun close()'));
assert.match(renaming,/it\.id!=machineId/);
assert.match(renaming,/KotlinMachineAlternativesDomain\.edit\(config,previous\.copy\(label=label\.trim\(\)\)\)/);
assert.match(controller,/host\.saveMachineRoster\(roster\.revision,configs\)/);
// Rename is a source-roster metadata change, never a direct workout/history
// write or a new independent progression/load decision path.
assert.doesNotMatch(renaming,/journalStore\.save\(|workoutMachineChoices\.save\(|KotlinJournalOperation|progression/);
assert.match(host,/fun saveMachineRoster\(/);
console.log('PASS native gym Machine A/B rename: stable physical IDs, distinct labels and owner-fenced metadata write.');
