import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { upgradeExistingCycleToAdaptive } from '../modules/next-engine/adaptive-cycle-upgrade.js';
import { repeatCycleOptions } from '../modules/engine-api.js';

const config=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url))).rows[3].config;
let n=0;
const built=generateNextCycleForShell({templateId:'custom',config:{...config,goal:'both',focus:{lats:2,upper_back:2}},
 blocks:[{phase:'hypertrophy_accumulation',weeks:5},{phase:'strength_accumulation',weeks:5},{phase:'peak',weeks:2}],
 legacyExercises:EXERCISES,seed:31101,makeId:()=>`m311-${++n}`,adaptTrainingSplit:false,adaptBetweenBlocks:false});
const cycle=built.cycle, saved=built.blocks;
const training=[{programId:saved[0].id,id:'m311-workout-1',sets:[{weight:175,reps:5,rir:2}]}];
const original=JSON.stringify({cycle,saved,training});
const result=upgradeExistingCycleToAdaptive(cycle,saved,training,311000);
assert.equal(result.changed,true);
assert.equal(JSON.stringify({cycle,saved,training}),original,'No prior state may be mutated');
assert.deepEqual(result.cycle.blockIds,cycle.blockIds);
assert.deepEqual(result.cycle.blockMeta,cycle.blockMeta);
assert.deepEqual(result.cycle.nextEngineCycle.plannedBlocks,cycle.nextEngineCycle.plannedBlocks);
assert.equal(result.cycle.nextEngineCycle.adaptTrainingSplit,true);
assert.equal(result.cycle.nextEngineCycle.adaptBetweenBlocks,true);
assert.equal(result.cycle.adaptTrainingSplit,true);
assert.equal(result.cycle.adaptExercises,true);
assert.equal(result.cycle.adaptiveUpgrade.activeBlockAtUpgrade,0);
const again=upgradeExistingCycleToAdaptive(result.cycle,saved,training,311001);
assert.equal(again.changed,false);
assert.equal(again.cycle,result.cycle,'Second upgrade is idempotent');
const repeated=repeatCycleOptions(result.cycle);
assert.equal(repeated.adaptTrainingSplit,true,'Cycle repeat retains upgraded split optimization');
assert.equal(repeated.adaptBetweenBlocks,true,'Cycle repeat retains exercise adaptation');
for(const [label,corrupt] of [
 ['missing',saved.slice(1)],
 ['future history',saved],
]){
 assert.throws(()=>upgradeExistingCycleToAdaptive(cycle,corrupt,
    label==='future history'?[...training,{programId:saved[2].id}]:training),/missing|recorded workouts/);
}
assert.throws(()=>upgradeExistingCycleToAdaptive({...cycle,done:true},saved,training),/Completed cycles/);
assert.throws(()=>upgradeExistingCycleToAdaptive({...cycle,nextEngineCycle:undefined},saved,training),/older cycle/);
assert.throws(()=>upgradeExistingCycleToAdaptive({...cycle,blockIds:[saved[0].id],blockMeta:cycle.blockMeta.slice(0,1)},saved,training),/upcoming block/);
const bridged={...cycle,nextEngineCycle:{...cycle.nextEngineCycle,legacyFirstBlockId:saved[0].id}};
const bridgeUpgrade=upgradeExistingCycleToAdaptive(bridged,saved,training);
assert.equal(bridgeUpgrade.cycle.adaptiveUpgrade.firstFixedLegacyBridge,true);
console.log('PASS M311: existing custom/converted cycles enable adaptation at future boundaries without altering active exercise roster, logged work, authored phase blocks, preview IDs or ownership.');
