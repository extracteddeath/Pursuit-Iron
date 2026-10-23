import assert from 'node:assert/strict';
import { EXERCISES, EX_BY_ID } from '../modules/training-domain/catalog.js';
import { generateNextCycleForShell, convertProgramToNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
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
const history=[{id:'past',programId:legacy.id,dayId:legacy.days[0].id,date:1700000000000,updatedAt:1700000000000,weekIndex:1,perf:{}}];
const before=JSON.stringify({cycle,saved,history});
assert.equal(authoredCycleExitSnapshot(legacy,EXERCISES).sessions.reduce((n,d)=>n+d.exercises.reduce((s,e)=>s+e.sets,0),0),90);
const options={cycle,saved,history,legacyExercises:EXERCISES,equipment,makeId:id,now:326000};
const result=refreshAdaptiveCyclePreviews(options);
assert.equal(JSON.stringify({cycle,saved,history}),before);
assert.deepEqual(result.cycle.blockIds,cycle.blockIds);
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
