import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const read = file => readFileSync(new URL('../'+file,import.meta.url),'utf8');
const gym = read('modules/gym-machine-variants.js');
const loader = read('modules/gym-load-fields.js');
const session = read('modules/workout-session-controls.js');
const program = read('modules/program-exercise-options.js');
const app = read('modules/App.js');
const runtime = read('modules/workout-machine-runtime.js');

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
assert.match(session,/const isMachine=isMachineLike\(EX_BY_ID\[exerciseId\]\?\.equip\)/);
assert.match(session,/if\(!isMachine\)return null/);
assert.match(session,/!gym && _jsx\('p'/);
assert.match(session,/wpb-machine-switch-trigger/);
assert.match(session,/wpb-machine-switch-sheet/);
assert.match(session,/onClick:\(\)=>\{setOpen\(true\);setMessage\(''\);\}/);
assert.match(session,/onClick:\(\)=>choose\(machine\.id\)/);
assert.match(session,/Add .*& use now/);
assert.match(session,/onCreate\(label,makeDefault\)/);
assert.match(session,/onSwap\(\)/);
assert.match(session,/createPortal\(/);
assert.match(session,/role:'alert'/);
assert.match(session,/Same exercise, different physical machine/);
assert.match(app,/onSelect:id=>switchPhysicalMachine\(ei,id\)/);
assert.match(runtime,/if\(set\.done\)return set/);
assert.match(app,/chooseWorkoutPhysicalMachine\(row,scope\)/);
assert.match(app,/createWorkoutPhysicalMachine\(gymProfile,row\?\.id,name,makeDefault\)/);
assert.match(runtime,/machineInstanceId:nextId/);
assert.match(runtime,/withGymMachineConfig/);
assert.match(app,/lastMachine=machineId \? machineStartingReference\(history/);
assert.match(runtime,/const last=machineStartingReference\(history/);
assert.match(app,/!row.machineInstanceId && workoutRowMatchesPrescription/);
assert.doesNotMatch(gym,/setProgram\(|history\.push\(/);
assert.doesNotMatch(session,/localStorage\.setItem\(|history\.push\(/);
assert.doesNotMatch(program,/setProgram\(|history\.push\(/);
console.log('PASS A/B setup and workout switching are discoverable, while program drafts, logged sets, and machine histories remain independently owned');

// App-only projection: the independent engine gate owns historical selection;
// this integration gate owns seeding the selected reference into live rows.
{
    const reference={weight:75,unit:'lb',workingSets:[90,85,80,75].map((weight,setIndex)=>({weight,unit:'lb',setIndex}))};
    const {seedMachineReferenceRows}=await import('../modules/workout-machine-runtime.js');
    const original=[{done:true,weight:'95',reps:'20',machineInstanceId:'B'},
        ...[1,2,3].map(()=>({weight:'999',reps:'18',target:{w:'999',decisionTrace:{},estimate:{},nextAction:'increase_load'},loadAdvice:{},hint:'up'}))];
    const seeded=seedMachineReferenceRows(original,reference,'A','lb');
    assert.equal(seeded[0],original[0],'completed set stays on its original machine');
    assert.deepEqual(seeded.slice(1).map(s=>s.weight),['85','80','75']);
    assert.ok(seeded.slice(1).every(s=>s.valueOwner==='user'&&s.machineInstanceId==='A'&&!s.target.decisionTrace&&!s.loadAdvice));
    assert.equal(original[1].weight,'999','reference projection never mutates source rows');
    console.log('PASS A/B runtime: independent positional references and immutable completed rows');
}
