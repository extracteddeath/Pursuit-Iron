import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES, EX_BY_ID } from '../modules/training-domain/catalog.js';
import { generateNextCycleForShell, convertProgramToNextCycleForShell, reviewConvertedStrengthExerciseCoverage } from '../modules/next-engine/cycle-runtime-adapter.js';
import { nextExerciseIdForShellExercise } from '../modules/next-engine/shell-projection.js';
import { refreshAdaptiveCyclePreviews, authoredCycleExitSnapshot, upgradeExistingCycleToAdaptive } from '../modules/next-engine/adaptive-cycle-upgrade.js';
import { captureShellVolumeSnapshot } from '../modules/next-engine/volume-repair.js';
import { shellExercisePerformableFor } from '../modules/next-engine/shell-equipment.js';
import { advanceLegacyFirstCycleBlock } from '../modules/training-domain/programs.js';
import { config as cfg } from './m265-generation-fixture.mjs';
export const equipment=['barbell','dumbbell','bench','rack','pullup','ezbar','bands','dip'];
let serial=0;const id=()=>`m326-${++serial}`;
const built=generateNextCycleForShell({templateId:'custom',config:{...cfg,days:3,split:'full_body',goal:'both'},
 blocks:[{phase:'hypertrophy_accumulation',weeks:4},{phase:'strength_accumulation',weeks:4},{phase:'peak',weeks:2}],
 legacyExercises:EXERCISES,seed:326,makeId:id});
export const legacy={id:'m326-custom',custom:true,engineV:70,name:'Custom hypertrophy',weeks:4,
 config:{...cfg,days:3,split:'custom',goal:'hypertrophy',weeks:4,progression:'manual',deload:false},
 days:Array.from({length:3},(_,i)=>({id:`authored-${i}`,label:`Full Body ${i+1}`,primaryIndex:0,
 exercises:['bb-bench','bb-row','back-squat','db-rdl','lat-raise','inc-curl']})),
 overrides:Object.fromEntries(Array.from({length:3},(_,i)=>Array.from({length:6},(_,slot)=>[ `authored-${i}:${slot}`,{sets:5,reps:'8-12',rir:'2',rest:90}])).flat())};
const originalIds=[legacy.id,...built.cycle.blockIds.slice(1)];
export const cycle={...built.cycle,blockIds:originalIds,blockMeta:built.cycle.blockMeta.map((m,i)=>({...m,id:originalIds[i]})),
 nextEngineCycle:{...built.cycle.nextEngineCycle,legacyFirstBlockId:legacy.id,legacyFirstBlock:true}};
legacy.cycleId=cycle.id;
export const saved=[legacy,...built.blocks.slice(1)];
saved[1].icon="bolt";saved[1].iconColor="purple";saved[1].folder="My cycle";
const history=[{id:'past',programId:legacy.id,dayId:legacy.days[0].id,date:1700000000000,updatedAt:1700000000000,weekIndex:1,perf:{}}];
const before=JSON.stringify({cycle,saved,history});
assert.equal(authoredCycleExitSnapshot(legacy,EXERCISES).sessions.reduce((n,d)=>n+d.exercises.reduce((s,e)=>s+e.sets,0),0),90);
const options={cycle,saved,history,legacyExercises:EXERCISES,equipment,makeId:id,now:326000};
const result=refreshAdaptiveCyclePreviews(options);
assert.equal(JSON.stringify({cycle,saved,history}),before);
assert.deepEqual(result.cycle.blockIds,cycle.blockIds);
for(const key of ["icon","iconColor","folder","createdAt"]) assert.equal(result.blocks[0][key],saved[1][key],`Refresh preserves block ${key}`);
assert.deepEqual(result.cycle.nextEngineCycle.plannedBlocks,cycle.nextEngineCycle.plannedBlocks);
assert.equal(result.cycle.activeBlock,0);
assert.equal(result.cycle.adaptiveUpgrade.awaitingBoundary,false);
assert.deepEqual(result.cycle.nextEngineCycle.baseConfig.equipment,equipment);
const performable=shellExercisePerformableFor({equipment});
for(const p of result.blocks){
 assert.equal(p.cycleId,cycle.id);assert.equal(p.id,cycle.blockIds[p.cycleIndex]);
 assert.ok(p.days.flatMap(d=>d.exercises).every(id=>performable(EX_BY_ID[id])),'Every preview exercise is available in the selected gym');
 assert.equal(p.nextEngine.program.audit.result,'pass');
 if(p.nextEngine.program.phase==='strength_accumulation')for(let week=1;week<=4;week++){
  const view=captureShellVolumeSnapshot(p,week,EXERCISES);
  const sets=view.sessions.reduce((n,d)=>n+d.exercises.reduce((s,e)=>s+e.sets,0),0);
  assert.ok(sets>=45,`Strength week ${week} must preserve the actual 90-set custom exit continuity floor, got ${sets}`);
  assert.ok(view.sessions.every(d=>d.estimatedMinutes<=d.maxMinutes+.01));
 }
}
const refreshedSaved=saved.map(p=>result.blocks.find(b=>b.id===p.id)||p);
assert.equal(refreshedSaved[0],legacy);
const activated=advanceLegacyFirstCycleBlock(result.cycle,refreshedSaved);
assert.deepEqual(activated.nextProgram,result.blocks[0],'Legacy bridge activates the refreshed saved strength block');
assert.throws(()=>refreshAdaptiveCyclePreviews({...options,history:[...history,{programId:saved[1].id}]}),/recorded workouts/);
assert.throws(()=>refreshAdaptiveCyclePreviews({...options,saved:saved.map((p,i)=>i===1?{...p,edited:true}:p)}),/manual edits/);
const flagged=upgradeExistingCycleToAdaptive(cycle,saved,history).cycle;
const repeated=refreshAdaptiveCyclePreviews({...options,cycle:flagged});
assert.equal(repeated.blocks.length,2,'Already-upgraded cycles can replace stale previews');
// The athlete's concrete bug: an authored eight-movement custom day was
// converted into a cycle; its OLD strength preview had three movements.
const priorSession={exercises:Array.from({length:8},(_,i)=>({
    exerciseId:'fixture-'+i,sets:3
}))};
const threeSession={intent:'full_body',exercises:priorSession.exercises.slice(0,3)};
const fourSession={intent:'full_body',exercises:priorSession.exercises.slice(0,4)};
const sourceProof={sessions:[priorSession,priorSession,priorSession]};
const oldStrength={sessions:[threeSession,threeSession,threeSession]};
const inputProof=JSON.stringify({sourceProof,oldStrength});
assert.equal(reviewConvertedStrengthExerciseCoverage(sourceProof,oldStrength,
    Array.from({length:3},(_,i)=>({label:'Full Body '+(i+1)}))).code,
    'SOURCE_EXERCISE_WEEKLY_COLLAPSE',
    'Three movements must not be disguised as a healthy custom→strength entry');
assert.equal(reviewConvertedStrengthExerciseCoverage(sourceProof,
    {sessions:[fourSession,fourSession,fourSession]},
    Array.from({length:3},(_,i)=>({label:'Full Body '+(i+1)}))),null,
    'Four meaningful movements may fit a stronger lift/rest program; eight is not a fixed quota');
assert.equal(JSON.stringify({sourceProof,oldStrength}),inputProof,
    'The source-based check must remain pure and preserve saved programs');

// Full end-to-end refresh of a real eight-exercise authored program.
// Distinct mapped movements must still be available in the chosen gym.
const canPerform=shellExercisePerformableFor({equipment});
const sourceExerciseIds=new Set(legacy.days[0].exercises);
const extras=EXERCISES.filter(ex=>!sourceExerciseIds.has(ex.id) && canPerform(ex)
    && nextExerciseIdForShellExercise(ex)).slice(0,2).map(ex=>ex.id);
assert.equal(extras.length,2,'Fixture needs two additional available mapped movements');
const highVariety=structuredClone(legacy);
for(const day of highVariety.days){
    day.exercises.push(...extras);
    for(let slot=0;slot<day.exercises.length;slot++)
        highVariety.overrides[`${day.id}:${slot}`]={
            ...(highVariety.overrides[`${day.id}:${slot}`]??{}),
            sets:3,reps:'8-12',rir:'2',rest:90
        };
    assert.equal(new Set(day.exercises).size,8);
}
const highSaved=[highVariety,...saved.slice(1)];
const highFrozen=JSON.stringify({highSaved,cycle,history});
const highRefresh=refreshAdaptiveCyclePreviews({
    ...options,saved:highSaved,now:326001
});
const highStrength=highRefresh.blocks[0];
const highCounts=highStrength.days.map(day=>new Set(day.exercises).size);
console.log('M326 converted eight-exercise custom→strength refreshed exercise counts:',
    JSON.stringify({prior:highVariety.days.map(day=>day.exercises.length),
        generated:highCounts,split:highStrength.config.split}));
assert.ok(highCounts.reduce((n,v)=>n+v,0)>=12,
    'An eight-exercise custom source cannot regenerate a three-exercise-per-day strength future');
// The old weekly-total check could pass 3/5/4 after an 8/8/8
// custom Full Body source. When the same three Full Body days survive,
// require each actual generated day to retain >= half the old menu.
const sameFullBody=highStrength.nextEngine.program.sessions.length===highVariety.days.length &&
    highStrength.config.split==='full_body';
assert.equal(sameFullBody,true,
    'This regression deliberately uses the same Full Body split and scheduled day count; engine session intents may use internal names');
assert.ok(highCounts.every((count,index)=>count>=Math.ceil(
    new Set(highVariety.days[index].exercises).size/2)),
    'Each unchanged Full Body day must retain >= half the authored movement menu: 8→3 cannot hide in the weekly total: '+JSON.stringify(highCounts));
assert.equal(reviewConvertedStrengthExerciseCoverage(
    authoredCycleExitSnapshot(highVariety,EXERCISES),
    highStrength.nextEngine.program,highVariety.days),null,
    'The actual saved custom→Strength preview must satisfy the same day-by-day exercise guard as initial cycle conversion');
assert.ok(highStrength.days.every((day,i)=>day.exercises.every(id=>canPerform(EX_BY_ID[id]))),
    'Refreshed strength roster must remain compatible with selected equipment');
assert.equal(JSON.stringify({highSaved,cycle,history}),highFrozen,
    'Refreshing may not mutate source custom program, future saved plans or workout history');
assert.equal(highRefresh.blocks[0].id,cycle.blockIds[1]);
assert.equal(highRefresh.blocks[1].id,cycle.blockIds[2]);
assert.equal(highRefresh.cycle.blockIds[0],highVariety.id);
assert.equal(highRefresh.blocks[0].nextEngine.program.audit.result,'pass');

// The upgrade must remain reachable from the existing saved-cycle UI,
// not become a headless generator change with no way to refresh real data.
const webUi=fs.readFileSync(new URL('../modules/App.js',import.meta.url),'utf8');
const transport=fs.readFileSync(new URL('../modules/generation-worker.js',import.meta.url),'utf8');
assert.match(webUi,/Refresh future blocks for this gym/,
    'Adaptive saved cycles expose their manual refresh action');
assert.match(webUi,/Upgrade future blocks to adaptive/,
    'Converted fixed cycles expose their first-time upgrade action');
assert.match(webUi,/onUpgradeAdaptive\(cycle\.id\)/,
    'The saved cycle ID is sent to the existing guarded upgrade operation');
assert.match(transport,/refresh_cycle:\s*refreshAdaptiveCyclePreviews/,
    'Refresh requests continue to reach the canonical engine through the worker');
console.log('PASS M326: actual custom exit continuity across all strength weeks, selected-gym previews, saved-ID/phase preservation, safe legacy activation and repeat refresh.');

// Specific machines remain unavailable even when another machine makes the
// engine's broad machine category available during native custom conversion.
const native={...built.blocks[0],cycleId:undefined,custom:true};
const partialGym=[...equipment,'machine','legpress','legcurl'];
const converted=convertProgramToNextCycleForShell({program:native,templateId:'custom',blocks:[{phase:'strength_accumulation',weeks:4}],
 equipment:partialGym,legacyExercises:EXERCISES,adaptBetweenBlocks:true,adaptTrainingSplit:true,makeId:id,seed:3261});
const allowedInPartialGym=shellExercisePerformableFor({equipment:partialGym});
assert.ok(converted.blocks.flatMap(p=>p.days.flatMap(d=>d.exercises)).every(id=>allowedInPartialGym(EX_BY_ID[id])),
 'Specific unavailable machines are filtered during native conversion, not only refresh');
assert.deepEqual(converted.currentProgram.days,native.days);
console.log('PASS M326 native conversion: selected gym filters specific unavailable machines while preserving current training.');
