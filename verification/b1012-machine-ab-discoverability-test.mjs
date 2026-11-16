import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const read = file => readFileSync(new URL('../'+file,import.meta.url),'utf8');
const gym = read('modules/gym-machine-variants.js');
const loader = read('modules/gym-load-fields.js');
const session = read('modules/workout-session-controls.js');
const program = read('modules/program-exercise-options.js');
const app = read('modules/App.js');

// The original A/B creation, gym default and workout set switching remain the
// only owners. Discoverability must never redefine progression or merge stacks.
assert.match(gym,/open:true,children:/);
assert.match(gym,/Machine A\/B · Add physical machines/);
assert.match(gym,/No Machine A\/B alternatives saved in this gym yet/);
assert.match(gym,/No physical variants for this exercise/);
assert.match(gym,/setDefaultMachineVariant\(/);
assert.match(gym,/addMachineVariant\(/);
assert.match(loader,/GymMachineVariantFields,\{gym,onChange\}/);
assert.match(program,/isMachineLike\(exercise\.equip\)/);
assert.match(program,/Machine A\/B is selected during a workout/);
assert.match(program,/Settings → Your gyms/);
assert.match(session,/if \(!gym\) return null/);
assert.match(session,/if \(!isMachineLike\(EX_BY_ID\[exerciseId\]\?\.equip\)\) return null/);
assert.match(session,/Machine A\/B · Set up alternatives/);
assert.match(session,/Machine A\/B · Choose physical machine/);
assert.match(session,/Physical machine used for this exercise/);
assert.match(session,/onChange:event=>onSelect\(event\.target\.value\)/);
assert.match(session,/Changing machines|Switching machines/);
assert.match(app,/onSelect:id=>switchPhysicalMachine\(ei,id\)/);
assert.match(app,/if\(set\.done\)return set/);
assert.match(app,/set\.machineInstanceId:nextId|machineInstanceId:nextId/);
assert.match(app,/lastMachine=machineId \? latestMachineSet\(history/);
assert.doesNotMatch(gym,/setProgram\(|history\.push\(/);
assert.doesNotMatch(session,/localStorage\.setItem\(|history\.push\(/);
assert.doesNotMatch(program,/setProgram\(|history\.push\(/);
console.log('PASS A/B setup and workout switching are discoverable, while program drafts, logged sets, and machine histories remain independently owned');
