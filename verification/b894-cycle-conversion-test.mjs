import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES, computeCell, cycleConfigForStandaloneProgram, mergeStandaloneIntoGeneratedCycle, advanceLegacyFirstCycleBlock } from '../modules/engine-api.js';

import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { generateNextCycleForShell, convertProgramToNextCycleForShell, customFutureCycleSpecs } from '../modules/next-engine/cycle-runtime-adapter.js';
import { readCycleSetupDraft, writeCycleSetupDraft, clearCycleSetupDraft, CYCLE_SETUP_DRAFT_KEY } from '../modules/cycle-setup-drafts.js';
const config=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url))).rows[3].config;
let id=0;const makeId=()=>`b894-${++id}`;
const source=generateNextProgramForShell({config:{...config,weeks:10},legacyExercises:EXERCISES,seed:894,makeId}).program;
const future=[{phase:source.nextEngine.program.phase,weeks:3},{phase:'peak',weeks:1},{phase:'hypertrophy_accumulation',weeks:7}];
for(const adaptBetweenBlocks of [false,true]){
 const before=JSON.stringify(source);
 const built=convertProgramToNextCycleForShell({program:source,templateId:'custom',blocks:future,legacyExercises:EXERCISES,seed:894,makeId,adaptBetweenBlocks});
 assert.equal(JSON.stringify(source),before,'conversion never mutates original');
 assert.equal(built.currentProgram.id,source.id);
 assert.deepEqual(built.currentProgram.days,source.days,'existing exercise roster is preserved');
 assert.deepEqual(built.currentProgram.overrides,source.overrides);
 assert.equal(built.currentProgram.config.weeks,10);
 assert.deepEqual(built.cycle.nextEngineCycle.plannedBlocks.slice(1).map(b=>({phase:b.phase,weeks:b.weeks})),future,'a matching first future phase must not be dropped');
 assert.deepEqual(built.blocks.map(b=>({phase:b.nextEngine.program.phase,weeks:b.config.weeks})),future);
 assert.ok(built.blocks.every((b,i)=>b.cycleIndex===i+1&&b.nextEngine.cycleTemplate.plannedIndex===i+1&&b.nextEngine.program.audit.result==='pass'));
}
const legacy=structuredClone(source);delete legacy.nextEngine;delete legacy.engineSource;legacy.custom=true;legacy.config={...legacy.config,split:'custom'};
legacy.overrides={...legacy.overrides,[legacy.days[0].id+':0']:{rir:2}};
const snapshot=JSON.stringify(legacy),cfg=cycleConfigForStandaloneProgram(legacy,'Legacy custom cycle');
const generated=generateNextCycleForShell({templateId:'custom',blocks:[{phase:'peak',weeks:2}],existingBlockWeeks:10,config:cfg,legacyExercises:EXERCISES,seed:894,makeId,adaptBetweenBlocks:true});
const generatedBefore=JSON.stringify(generated);
const merged=mergeStandaloneIntoGeneratedCycle(legacy,generated,{appendAll:true});
assert.equal(JSON.stringify(legacy),snapshot);assert.equal(JSON.stringify(generated),generatedBefore);
assert.equal(merged.allBlocks.length,2,'one future block is allowed without throwing away the generated block');
assert.equal(merged.currentProgram.id,legacy.id);assert.deepEqual(merged.currentProgram.days,legacy.days);
assert.equal(merged.currentProgram.engineSource,undefined,'authored block is never relabelled as engine generated');
for(const week of [1,5,10])for(const day of legacy.days)for(const [slot,ex]of day.exercises.entries()){ const a=computeCell(merged.currentProgram,day,ex,slot,week),b=computeCell(legacy,day,ex,slot,week); for(const key of ['sets','reps','rest','tech'])assert.deepEqual(a[key],b[key],'legacy dose remains exact'); if(day.id===legacy.days[0].id&&slot===0)assert.equal(a.rir,b.rir,'explicit authored effort remains exact'); }
assert.deepEqual(merged.cycle.nextEngineCycle.plannedBlocks.map(b=>b.weeks),[10,2]);
assert.equal(merged.blocks[0].nextEngine.cycleTemplate.plannedIndex,1);
assert.equal(merged.cycle.blockMeta[1].plannedIndex,1);
assert.equal(advanceLegacyFirstCycleBlock(merged.cycle,merged.allBlocks).nextProgram.id,merged.blocks[0].id);
assert.throws(()=>customFutureCycleSpecs([],10),/1 and 7/);
assert.throws(()=>customFutureCycleSpecs(Array.from({length:8},()=>({phase:'peak',weeks:1})),10),/1 and 7/);
assert.throws(()=>customFutureCycleSpecs([{phase:'peak',weeks:7}],98),/104/);
assert.throws(()=>customFutureCycleSpecs([{phase:'peak',weeks:1}],104),/104/);
assert.deepEqual(customFutureCycleSpecs([{phase:'peak',weeks:6}],98).map(b=>b.weeks),[6]);
const data=new Map(),storage={getItem:k=>data.get(k)||null,setItem:(k,v)=>data.set(k,v)};
const draft={name:'My cycle',blocks:[{phase:'peak',weeks:''}],templateId:'custom',adapt:true,step:3,config:{name:'My cycle',days:4,focus:{chest:1},goal:'strength'},cycleMode:true,choseMode:true};
assert.equal(writeCycleSetupDraft('wizard','gym-a',draft,storage),true);
assert.equal(readCycleSetupDraft('wizard','gym-a',storage).blocks[0].weeks,'','incomplete text is recoverable, never coerced into a valid duration');
assert.equal(readCycleSetupDraft('wizard','gym-b',storage),null,'different equipment/unit context cannot inherit a stale draft');
writeCycleSetupDraft('convert:p1','program-a',draft,storage);clearCycleSetupDraft('wizard',storage);
assert.equal(readCycleSetupDraft('wizard','gym-a',storage),null);assert.equal(readCycleSetupDraft('convert:p1','program-a',storage).name,'My cycle');
data.set(CYCLE_SETUP_DRAFT_KEY,'{broken');assert.equal(readCycleSetupDraft('wizard','gym-a',storage),null);
assert.equal(writeCycleSetupDraft('wizard','gym-a',draft,{getItem(){throw Error('blocked')},setItem(){throw Error('blocked')}}),false);
console.log('PASS B894: generated and authored conversions preserve Block 1, exact future order/duration, ownership, first advancement and isolated recoverable setup drafts.');

let structuralId=0;
const structural=convertProgramToNextCycleForShell({
 program:source,templateId:'custom',
 blocks:[{phase:'strength_accumulation',weeks:5,split:'upper_lower',days:4,priorityLift:'bench_press',frequency:2}],
 legacyExercises:EXERCISES,seed:894,makeId:()=>`b894-structure-${++structuralId}`,adaptBetweenBlocks:false
});
assert.deepEqual(structural.currentProgram.days,source.days,'converted working block and history keep their original week');
assert.equal(structural.blocks[0].config.split,'upper_lower','the upcoming strength block can restructure the split');
assert.equal(structural.blocks[0].config.days,4,'the upcoming phase uses its chosen training frequency');
assert.equal(structural.cycle.nextEngineCycle.plannedBlocks[1].frequency,2,'the future practice target survives in cycle metadata');
assert.equal(structural.blocks[0].nextEngine.request.goal.liftFrequency.bench_press,2,'the executable future block owns its lift-frequency contract');
console.log('PASS B894 structure: converted block is immutable; future block can independently rebuild split and bench frequency.');

const oversized=structuredClone(legacy);
oversized.config={...oversized.config,goal:'hypertrophy'};
for(const day of oversized.days)for(const [slot] of day.exercises.entries())
 oversized.overrides[`${day.id}:${slot}`]={sets:6};
const undersized=structuredClone(generated);
undersized.blocks[0].nextEngine.program.phase='strength_accumulation';
for(const session of undersized.blocks[0].nextEngine.program.sessions)for(const ex of session.exercises)ex.sets=1;
assert.throws(()=>mergeStandaloneIntoGeneratedCycle(oversized,undersized,{appendAll:true}),
 /drops weekly working sets/,'authored custom working sets must be compared to the first strength block');
assert.equal(oversized.config.goal,'hypertrophy','a rejected conversion must leave the authored program unchanged');
console.log('PASS B894 volume: high-volume authored plans cannot silently transition to a drastically underdosed strength accumulation block.');

let optimizeId=0;
const automaticConversion=convertProgramToNextCycleForShell({
 program:source,templateId:'custom',seed:894,
 blocks:[{phase:'strength_accumulation',weeks:4}],
 legacyExercises:EXERCISES,adaptTrainingSplit:true,adaptBetweenBlocks:true,
 makeId:()=>`b894-auto-${++optimizeId}`
});
assert.deepEqual(automaticConversion.currentProgram.days,source.days,
 'split adaptation must not rewrite the converted existing program');
assert.equal(automaticConversion.cycle.adaptTrainingSplit,true);
const selected=automaticConversion.cycle.blockMeta[1].splitDecision;
assert.equal(selected.mode,'goal_optimized');
assert.ok(selected.passingCandidates>=1,'converted future strength block must choose an audited structure');
assert.equal(automaticConversion.blocks[0].config.split,selected.selectedSplit);
assert.equal(automaticConversion.blocks[0].nextEngine.program.audit.result,'pass');
assert.deepEqual(automaticConversion.cycle.nextEngineCycle.plannedBlocks[1],
 {phase:'strength_accumulation',weeks:4,label:'Strength'},
 'preview optimizer must not rewrite the authored future phase specification');
console.log('PASS B894 automatic conversion: current program unchanged; future strength block uses goal-optimized split.');
