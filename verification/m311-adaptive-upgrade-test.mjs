import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { generateNextCycleForShell, convertProgramToNextCycleForShell, advanceNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { analyzeShellHistoryForNextEngine } from '../modules/next-engine/workout-history-adapter.js';
import { completedHistory, ids } from './m265-generation-fixture.mjs';
import { captureShellVolumeSnapshot, preserveStrengthWeekContinuity } from '../modules/next-engine/volume-repair.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
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

// M312: enabling the switches is not enough. Walk the real advancement worker
// with completed executable workouts, using the saved fixed-split preview as
// the replacement target. It must generate an audited adaptive block under the
// same ID without rewriting past logs, active work or author-owned phase specs.
const fullHistory=completedHistory(saved[0]);
assert.equal(analyzeShellHistoryForNextEngine(saved[0],fullHistory,EXERCISES).readyForNextBlock,true,
 'The completed first block must provide genuine advancement evidence');
const beforeAdvance=JSON.stringify({result,saved,fullHistory});
const advanced=advanceNextCycleForShell({
 cycle:result.cycle,activeProgram:saved[0],history:fullHistory,
 legacyExercises:EXERCISES,makeId:ids('m312-next')
});
assert.equal(advanced.done,false);
assert.equal(advanced.insertedRecovery,false,'Favorable training evidence must not trigger recovery');
assert.equal(advanced.replaceProgramId,saved[1].id,'The preview must be replaced in place');
assert.equal(advanced.nextProgram.id,saved[1].id,'History and Plan references must retain their program ID');
assert.equal(advanced.nextProgram.nextEngine.program.phase,'strength_accumulation');
assert.equal(advanced.nextProgram.nextEngine.program.audit.result,'pass');
// M315: on a history-adapted block, the split ranking is a base forecast;
// the finalized work-week breakdown must reflect the saved executable plan.
const advancedQuality=advanced.cycle.blockMeta[1].splitDecision.realizedQuality;
assert.equal(advancedQuality.comparison,'ranked_engine_base_to_saved_executable_weeks');
assert.equal(advancedQuality.weeks.length,advanced.nextProgram.config.weeks);
assert.deepEqual(advanced.nextProgram.nextEngine.cycleTemplate.splitDecision.realizedQuality,advancedQuality);
for(const week of advancedQuality.weeks){
 const snap=captureShellVolumeSnapshot(advanced.nextProgram,week.week,EXERCISES);
 assert.ok(snap&&!snap.missing);
 assert.equal(week.weeklySets,snap.sessions.reduce((n,s)=>
  n+s.exercises.reduce((m,e)=>m+e.sets,0),0));
 assert.deepEqual(week.overCapacityDays,[]);
}
assert.equal(advanced.nextProgram.nextEngine.cycleTemplate.preview,false);
assert.equal(advanced.nextProgram.nextEngine.cycleTemplate.splitDecision.mode,'goal_optimized',
 'An upgraded cycle must actually run split optimization at advancement');
assert.equal(advanced.cycle.blockMeta[1].splitDecision.mode,'goal_optimized');
assert.equal(advanced.cycle.activeBlock,1);
assert.equal(advanced.nextProgram.nextEngine.priorBlock.programId,saved[0].id,
 'The rebuilt future block must be tied to actual preceding workout history');
assert.equal(advanced.nextProgram.nextEngine.priorBlock.workouts,fullHistory.length);
assert.equal(advanced.nextProgram.config.split,advanced.nextProgram.nextEngine.program.split.family);
assert.equal(advanced.nextProgram.days.length,advanced.nextProgram.nextEngine.program.sessions.length);
assert.deepEqual(advanced.cycle.nextEngineCycle.plannedBlocks,cycle.nextEngineCycle.plannedBlocks,
 'User-authored phases are immutable even after the worker generates a new split');
assert.equal(JSON.stringify({result,saved,fullHistory}),beforeAdvance,
 'Advance must not mutate the upgrade, saved previews, active plan or workout logs');
const prevWeek=captureShellVolumeSnapshot(saved[0],5,EXERCISES);
const nextWeek=captureShellVolumeSnapshot(advanced.nextProgram,1,EXERCISES);
assert.ok(prevWeek&&!prevWeek.missing&&nextWeek&&!nextWeek.missing,
 'Evaluate real executable weeks, not merely the base exercise prescriptions');
const weeklySets=snapshot=>snapshot.sessions.reduce((total,day)=>total+
 day.exercises.reduce((n,ex)=>n+ex.sets,0),0);
const actualBefore=weeklySets(prevWeek),actualAfter=weeklySets(nextWeek);
assert.ok(actualAfter>=Math.ceil(actualBefore*.5),
 `Adaptive strength entry must not silently cliff from ${actualBefore} to ${actualAfter} executable sets`);
assert.ok(nextWeek.sessions.every(day=>day.exercises.every(ex=>Number.isInteger(ex.sets)&&ex.sets>=1)));
assert.ok(nextWeek.sessions.every(day=>day.estimatedMinutes<=day.maxMinutes+.01),
 'Every generated training session must fit the actual available time');
console.log(`PASS M312: genuine upgrade advancement replaced the fixed preview under its original ID, ${actualBefore}→${actualAfter} executable weekly sets, with audited goal-optimized structure and unchanged training history.`);


// Also certify the actual user migration: a saved, current Next standalone
// custom program becomes a fixed cycle, opts into adaptation, then advances
// from *its original program* using new workout evidence. This is different
// from upgrading an already generated multi-block cycle above.
const standalone=generateNextProgramForShell({
 config:{...config,name:'M312 converted custom program',goal:'hypertrophy',weeks:4},
 legacyExercises:EXERCISES,seed:31122,makeId:ids('m312-standalone')
}).program;
standalone.custom=true;
const originalStandalone=JSON.stringify(standalone);
const conversion=convertProgramToNextCycleForShell({
 program:standalone,templateId:'custom',
 blocks:[{phase:'strength_accumulation',weeks:4}],
 legacyExercises:EXERCISES,seed:31123,makeId:ids('m312-converted'),
 adaptBetweenBlocks:true,adaptTrainingSplit:false
});
const convertedSaved=conversion.allBlocks;
const convertedUpgrade=upgradeExistingCycleToAdaptive(conversion.cycle,convertedSaved,[],311240);
assert.equal(convertedUpgrade.changed,true);
assert.equal(convertedUpgrade.cycle.nextEngineCycle.convertedFromProgramId,standalone.id);
assert.equal(convertedUpgrade.cycle.adaptiveUpgrade.firstFixedLegacyBridge,false,
 'A current Next-engine standalone program is not the old no-snapshot legacy bridge');
assert.equal(conversion.currentProgram.id,standalone.id);
assert.equal(JSON.stringify(standalone),originalStandalone);
const convertedHistory=completedHistory(conversion.currentProgram);
assert.equal(analyzeShellHistoryForNextEngine(conversion.currentProgram,convertedHistory,EXERCISES).readyForNextBlock,true);
const migratedBefore=JSON.stringify({conversion,convertedHistory});
const migrated=advanceNextCycleForShell({
 cycle:convertedUpgrade.cycle,activeProgram:conversion.currentProgram,history:convertedHistory,
 legacyExercises:EXERCISES,makeId:ids('m312-migrated')
});
assert.equal(migrated.insertedRecovery,false);
assert.equal(migrated.replaceProgramId,convertedSaved[1].id);
assert.equal(migrated.nextProgram.id,convertedSaved[1].id);
assert.equal(migrated.cycle.activeBlock,1);
assert.equal(migrated.nextProgram.nextEngine.cycleTemplate.splitDecision.mode,'goal_optimized',
 'Upgraded standalone programs must engage the adaptive split search on real advancement');
assert.equal(migrated.nextProgram.nextEngine.priorBlock.programId,standalone.id);
assert.equal(migrated.nextProgram.nextEngine.priorBlock.workouts,convertedHistory.length);
assert.equal(migrated.nextProgram.nextEngine.program.audit.result,'pass');
const migratedQuality=migrated.cycle.blockMeta[1].splitDecision.realizedQuality;
const migratedPreviousSets=weeklySets(captureShellVolumeSnapshot(conversion.currentProgram,conversion.currentProgram.config.weeks,EXERCISES));
const continuity=migrated.nextProgram.nextEngine.strengthWeekContinuity;
assert.ok(continuity?.changes.length>0,'The formerly rejected conversion must repair its deficient later week');
assert.equal(continuity.minimumWeeklySets,Math.ceil(migratedPreviousSets*.5));
assert.equal(migratedQuality.comparison,'ranked_engine_base_to_saved_executable_weeks');
assert.equal(migratedQuality.weeks.length,migrated.nextProgram.config.weeks);
assert.deepEqual(migrated.nextProgram.nextEngine.cycleTemplate.splitDecision.realizedQuality,migratedQuality);
for(const week of migratedQuality.weeks){
 const snap=captureShellVolumeSnapshot(migrated.nextProgram,week.week,EXERCISES);
 assert.equal(week.weeklySets,snap.sessions.reduce((n,s)=>
  n+s.exercises.reduce((m,e)=>m+e.sets,0),0));
 assert.deepEqual(week.overCapacityDays,[]);
 assert.ok(week.weeklySets>=Math.ceil(migratedPreviousSets*.5),
  'Every saved strength work week must retain the continuity floor, including the formerly failing final week');
}
// Removing the repair recreates the actual generation regression. Verify the
// transaction changes only generated set counts and is deterministic/idempotent.
const unrepaired=structuredClone(migrated.nextProgram);
for(const change of continuity.changes)unrepaired.nextWeekPrescriptions[change.key][change.week].sets-=change.delta;
delete unrepaired.nextEngine.strengthWeekContinuity;
const frozenBefore=JSON.stringify(unrepaired);
const repaired=preserveStrengthWeekContinuity(conversion.currentProgram,unrepaired,EXERCISES);
assert.equal(JSON.stringify(unrepaired),frozenBefore,'Repair must not mutate the saved candidate');
assert.deepEqual(repaired.nextWeekPrescriptions,migrated.nextProgram.nextWeekPrescriptions);
assert.equal(preserveStrengthWeekContinuity(conversion.currentProgram,repaired,EXERCISES),repaired,
 'Already safe executable weeks must not grow on repeated calls');
const protectedCandidate=structuredClone(unrepaired);
for(const day of protectedCandidate.days)for(const slot of day.exercises.keys()){
 const key=`${day.id}:${slot}`;
 protectedCandidate.overrides??={};
 protectedCandidate.overrides[key]={...protectedCandidate.overrides[key],prescriptionOwners:{sets:'user'}};
}
assert.equal(preserveStrengthWeekContinuity(conversion.currentProgram,protectedCandidate,EXERCISES),protectedCandidate,
 'A continuity target must never overwrite user-owned set counts');
assert.equal(preserveStrengthWeekContinuity(conversion.currentProgram,{...unrepaired,nextEngine:{...unrepaired.nextEngine,
 program:{...unrepaired.nextEngine.program,phase:'peak'}}},EXERCISES).nextEngine.program.phase,'peak');
assert.deepEqual(migrated.cycle.nextEngineCycle.plannedBlocks,conversion.cycle.nextEngineCycle.plannedBlocks);
assert.equal(JSON.stringify({conversion,convertedHistory}),migratedBefore);
console.log('PASS M312: saved standalone custom program converts, upgrades, and advances into audited goal-adaptive strength with no history/identity loss.');

console.log('PASS M311: existing custom/converted cycles enable adaptation at future boundaries without altering active exercise roster, logged work, authored phase blocks, preview IDs or ownership.');
