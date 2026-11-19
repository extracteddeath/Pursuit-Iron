import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { phasePolicyFor } from '../modules/next-engine/phase-policy.js';
import { strengthEntryMinimumSets, findStrengthVolumeCliff } from '../modules/next-engine/cycle-runtime-adapter.js';
import { addMachineVariant, assignLegacyMachineHistory, machineHistory,
    sanitizeMachineVariants, latestMachineSet } from '../modules/next-engine/machine-instances.js';

const read=path=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
const app=read('modules/App.js');
const machine=read('modules/workout-session-controls.js');
const gymUI=read('modules/gym-machine-variants.js');
const css=read('modules/workout-machine-switch.css');

// The live workout owns completed/pending set rows, not a stale week cell.
// Every visible "Why" set count must read the exact same working rows as
// "Set 1 of 4" and should not re-count warm-ups or myo/drop extensions.
assert.match(app,/wpb-exinfo-why-chip", children: \[wTotal, " sets"\]/);
assert.match(app,/const liveRow=data\.find\(row=>row\.slot===why\.slot && row\.id===why\.ex\.id\)/);
assert.match(app,/const liveWorkSetCount=liveRow \? workOnly\(liveRow\)\.length : t\.decided\.sets/);
const why=app.slice(app.indexOf('_jsx(Exit, { when: !!why'),
    app.indexOf('_jsx(Exit, { when: histSheet'));
assert.ok(why.includes('children: [liveWorkSetCount, " sets'));
assert.ok(why.includes('k: "Sets", v: liveWorkSetCount'));
assert.doesNotMatch(why,/children: \[t\.decided\.sets, " sets"/);

// Strength accumulation is not a peaking taper: 50% was previously
// considered fine, causing large long-cycle gaps despite unchanged days.
assert.equal(phasePolicyFor('strength_accumulation').volumeMultiplier,.8,
    'Standalone strength generator policy remains stable');
assert.equal(phasePolicyFor('strength_accumulation').sessionCapacityMultiplier,.78,
    'Strength still reserves longer rests and avoids one-set fragmentation');
assert.match(read('modules/next-engine/cycle-runtime-adapter.js'),
    /target === 'strength_accumulation' \? Math\.max\(\.9, ratio\) : ratio/);
assert.equal(strengthEntryMinimumSets(60),42);
assert.equal(findStrengthVolumeCliff(60,[{week:1,weeklySets:50},{week:2,weeklySets:39}]).week,2);
assert.equal(findStrengthVolumeCliff(60,[{week:1,weeklySets:42}]),null);
assert.ok(phasePolicyFor('peak').volumeMultiplier<.5,
    'Peak retains deliberately distinct volume taper');

// Machine and exercise swap have a readable, full-width home in Exercise Info.
// The tiny workout tool row cannot truncate the new machine button.
const toolRail=app.slice(app.indexOf('className: "wpb-hscroll wpb-workout-tools'),
    app.indexOf('className: "wpb-set-head"'));
assert.doesNotMatch(toolRail,/WorkoutMachineVariantControl|\{ k: "swap"/);
assert.match(machine,/wpb-exinfo-machine-tools/);
assert.match(machine,/wpb-exinfo-swap-action/);
assert.match(machine,/onAssignLegacy/);
assert.match(gymUI,/Original physical machine for older unlabelled history/);
assert.match(css,/wpb-exinfo-machine-tools/);
assert.match(css,/wpb-machine-switch-add span/);
assert.match(css,/overflow-wrap:anywhere/);

// New machines do not automatically receive old, unlabelled history. The
// athlete can explicitly assign the old source to the original machine A.
let gym={id:'gym1'};
gym=addMachineVariant(gym,'chest_press',{id:'A',label:'Original'});
gym=addMachineVariant(gym,'chest_press',{id:'B',label:'New stack'});
const history=[{id:'historical',gymId:'gym1',dayId:'upper',date:1,unit:'lb',
    perf:{chest_press:{sets:[{w:180,r:10,done:true}]}}},
  {id:'b',gymId:'gym1',dayId:'upper',date:2,unit:'lb',
    perf:{chest_press:{sets:[{w:90,r:10,done:true,machineInstanceId:'B'}]}}}];
assert.deepEqual(machineHistory(history,{gymId:'gym1',exerciseId:'chest_press',
    machineInstanceId:'A',gym}),[]);
gym=assignLegacyMachineHistory(gym,'chest_press','A');
assert.equal(latestMachineSet(history,{gymId:'gym1',exerciseId:'chest_press',
    machineInstanceId:'A',gym}).weight,180);
assert.equal(latestMachineSet(history,{gymId:'gym1',exerciseId:'chest_press',
    machineInstanceId:'B',gym}).weight,90);
assert.equal(sanitizeMachineVariants(gym.machineVariants).chest_press.legacyHistoryMachineId,'A');
console.log('PASS strength continuity, actual live-note counts, full-width machine actions and original-history isolation');
