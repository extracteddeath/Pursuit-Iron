import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/engine-api.js';

import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { generateNextCycleForShell, convertProgramToNextCycleForShell, describeRegionalWorkingWeekChange } from '../modules/next-engine/cycle-runtime-adapter.js';
import { captureShellVolumeSnapshot } from '../modules/next-engine/volume-repair.js';

// The executable Week 1 / later-week cells can differ from the engine's
// base-session set count. Protect the actual Program/Home/Workout projection.
const fixtures=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url),'utf8')).rows;
const total=sessions=>sessions.reduce((n,day)=>n+day.exercises.reduce((v,ex)=>v+ex.sets,0),0);
const view=(p,week)=>{
 const snapshot=captureShellVolumeSnapshot(p,week,EXERCISES);
 assert.ok(snapshot&&!snapshot.missing,`Week ${week} must have every executable cell`);
 assert.equal(snapshot.sessions.length,p.days.length,'Week projection preserves training days');
 assert.ok(snapshot.sessions.every(s=>s.exercises.every(ex=>ex.sets>0&&Number.isInteger(ex.sets))));
 return {weeklySets:total(snapshot.sessions),daySets:snapshot.sessions.map(s=>total([s])),regions:snapshot.regions};
};
// M315: use the *saved* week cells, not the engine's ranked base-session
// ledger, when verifying the optimizer's reported coverage and lift frequency.
const { createExerciseMap } = await import('../modules/next-engine/exercise-db.js');
const { normalizeRequest, createMusclePrescriptions } = await import('../modules/next-engine/prescription.js');
const { PUBLIC_MEV_REGIONS } = await import('../modules/next-engine/public-mev.js');
const verifyRealized=(program,decision)=>{
 const report=decision?.realizedQuality;
 assert.equal(report?.comparison,'ranked_engine_base_to_saved_executable_weeks');
 assert.equal(report.basis,'creation_or_adaptation_snapshot');
 assert.equal(report.weeks.length,program.config.weeks);
 assert.equal(report.rankedBase.weeklySets,decision.weeklySets);
 assert.equal(report.rankedBase.muscleTargetCoveragePercent,decision.muscleTargetCoverage);
 assert.deepEqual(report.rankedBase.liftFrequencies,decision.liftFrequencies);
 assert.equal(report.rankedBase.liftPracticePercent,decision.liftPracticePercent);
 const request=normalizeRequest(program.nextEngine.request);
 const prescriptions=createMusclePrescriptions(request,program.nextEngine.program.phase);
 const catalog=createExerciseMap(request.customExercises);
 const priorities=request.goal.musclePriorities;
 const weights={primary:3,specialization:2.6,high:1.9,normal:1,maintenance:.12};
 for(const [index,realized] of report.weeks.entries()){
  const snapshot=captureShellVolumeSnapshot(program,index+1,EXERCISES);
  assert.ok(snapshot&&!snapshot.missing);
  assert.equal(realized.week,index+1);
  assert.equal(realized.weeklySets,total(snapshot.sessions));
  assert.deepEqual(realized.overCapacityDays,[]);
  assert.deepEqual(Object.keys(realized.regionalSets).sort(),[...PUBLIC_MEV_REGIONS].sort());
  for(const region of PUBLIC_MEV_REGIONS)
   assert.equal(realized.regionalSets[region],Math.round((snapshot.regions[region]||0)*10)/10,
     'Credited regional sets must come from exact workout week');
  const freq={};
  for(const lift of ['bench_press','back_squat','deadlift','overhead_press']){
   freq[lift]=snapshot.sessions.filter(day=>day.exercises.some(ex=>
     ['primary_strength','secondary_strength'].includes(ex.role)
     && (catalog.get(ex.exerciseId)?.liftSpecificity?.[lift]||0)>.45)).length;
  }
  assert.deepEqual(realized.liftFrequencies,freq,'Lift practice must match exact saved exercise sessions');
  let earned=0,possible=0;
  for(const p of prescriptions){
   const w=weights[priorities[p.muscle]];
   const target=Math.max(.25,Number(p.preferred)||0);
   earned+=w*Math.min(1,(snapshot.muscleLedger?.[p.muscle]?.fractionalSets||0)/target);
   possible+=w;
  }
  assert.equal(realized.muscleTargetCoveragePercent,Math.round(100*(possible?earned/possible:0)),
   'Ranked muscle coverage and saved workout coverage must share target math');
 }
 assert.equal(report.firstWeekSetDelta,report.weeks[0].weeklySets-report.rankedBase.weeklySets);
 assert.equal(report.firstWeekCoverageDelta,
  report.weeks[0].muscleTargetCoveragePercent-report.rankedBase.muscleTargetCoveragePercent);
 assert.deepEqual(program.nextEngine.cycleTemplate.splitDecision.realizedQuality,report);
};
const decisions=[];
for(const [row,seed] of [[2,30442],[3,30453]]){
 const config={...fixtures[row].config,name:'M304 exact-week projection',goal:'hypertrophy'};
 let id=0;
 const created=generateNextCycleForShell({
   templateId:'custom',config,
   blocks:[{phase:'hypertrophy_accumulation',weeks:5},{phase:'strength_accumulation',weeks:5}],
   legacyExercises:EXERCISES,seed,
   makeId:()=>`m304-${row}-${++id}`,
   adaptBetweenBlocks:true,adaptTrainingSplit:true
 });
 const before=created.blocks[0],after=created.blocks[1];
 const hypertrophyStart=view(before,1),hypertrophyEnd=view(before,5);
 const strengthStart=view(after,1),strengthEnd=view(after,5);
 const baseBefore=total(before.nextEngine.program.sessions);
 const baseAfter=total(after.nextEngine.program.sessions);

 // M305: the selected split records executable volume at the *boundary*
 // (last hypertrophy week -> first strength week), not its internal base.
 const decision=created.cycle.blockMeta[1].splitDecision;
 verifyRealized(before,created.cycle.blockMeta[0].splitDecision);
 verifyRealized(after,decision);
 const transition=decision.workingWeekVolumeChange;
 assert.ok(transition,'Generated strength entry must explain actual week-to-week volume');
 assert.equal(transition.comparison,'previous_final_week_to_next_first_week');
 assert.equal(transition.previousWeek,5);
 assert.equal(transition.previousWeeklySets,hypertrophyEnd.weeklySets);
 assert.equal(transition.currentWeeklySets,strengthStart.weeklySets);
 assert.equal(transition.weeklyPercentChange,Math.round((strengthStart.weeklySets-hypertrophyEnd.weeklySets)/hypertrophyEnd.weeklySets*100));
 assert.deepEqual(after.nextEngine.cycleTemplate.splitDecision.workingWeekVolumeChange,transition,
   'Program preview and cycle metadata must agree about actual regional volume');
 for(const region of transition.regions){
   assert.equal(region.previousSets,Math.round((hypertrophyEnd.regions[region.region]||0)*10)/10);
   assert.equal(region.currentSets,Math.round((strengthStart.regions[region.region]||0)*10)/10);
   assert.equal(region.changeSets,Math.round((region.currentSets-region.previousSets)*10)/10);
   assert.equal(region.percentChange,region.previousSets>0
     ? Math.round((region.currentSets-region.previousSets)/region.previousSets*100):null);
 }
 const measured=describeRegionalWorkingWeekChange(before,after,EXERCISES);
 assert.deepEqual(measured,transition,'Canonical diagnostic can independently reconstruct the saved block transition');
 // M357: a saved exercise edit after generation must change the comparison
 // displayed by Cycle Detail and Preview without mutating the original report.
 const edited=structuredClone(after);
 const editable=edited.days.find(day=>day.exercises.length>1);
 assert.ok(editable,'Transition fixture needs an editable saved exercise slot');
 editable.exercises.pop();
 const live=describeRegionalWorkingWeekChange(before,edited,EXERCISES);
 assert.ok(live,'An edited saved phase still has a valid executable transition');
 assert.ok(live.currentWeeklySets<transition.currentWeeklySets,
   'The current saved block must not report a stale generation-time set total');
 assert.equal(live.previousWeeklySets,transition.previousWeeklySets,
   'An untouched preceding block retains its true final-week volume');
 assert.deepEqual(after.nextEngine.cycleTemplate.splitDecision.workingWeekVolumeChange,transition,
   'Browsing and comparing must never rewrite the historical generation snapshot');

 if(row===3){
   const core=transition.regions.find(r=>r.region==='core');
   assert.ok(core&&core.percentChange<0,
     'The explicit regional decrease remains visible even when new strength targets are met');
   assert.equal(decision.regionalCarryoverPercent,100,
     'Target coverage is not an absolute-volume retention percentage');
 }
 decisions.push({config:fixtures[row].label,base:[baseBefore,baseAfter],
   firstWeek:[hypertrophyStart.weeklySets,strengthStart.weeklySets],
   finalWeek:[hypertrophyEnd.weeklySets,strengthEnd.weeklySets],
   firstDaySets:[hypertrophyStart.daySets,strengthStart.daySets],
   lastDaySets:[hypertrophyEnd.daySets,strengthEnd.daySets],
   split:[before.config.split,after.config.split]});
}
{
 const config={...fixtures[3].config,name:'M304 converted live week',goal:'hypertrophy',weeks:8};
 let id=0;
 const source=generateNextProgramForShell({
   config,legacyExercises:EXERCISES,seed:30473,makeId:()=>`m304-source-${++id}`
 }).program;
 const beforeString=JSON.stringify(source);
 const converted=convertProgramToNextCycleForShell({
   program:source,templateId:'custom',blocks:[{phase:'strength_accumulation',weeks:5}],
   legacyExercises:EXERCISES,seed:30474,makeId:()=>`m304-convert-${++id}`,
   adaptBetweenBlocks:true,adaptTrainingSplit:true
 });
 const h=converted.currentProgram,s=converted.blocks[0];
 const firstH=view(h,1),lastH=view(h,8),firstS=view(s,1),lastS=view(s,5);
 assert.equal(JSON.stringify(source),beforeString,'Conversion cannot mutate the existing program');

 verifyRealized(s,converted.cycle.blockMeta[1].splitDecision);
 const transition=converted.cycle.blockMeta[1].splitDecision.workingWeekVolumeChange;
 assert.ok(transition,'Converted existing program must include exact regional volume-change context');
 assert.equal(transition.previousWeek,8);
 assert.equal(transition.previousWeeklySets,lastH.weeklySets);
 assert.equal(transition.currentWeeklySets,firstS.weeklySets);
 assert.deepEqual(s.nextEngine.cycleTemplate.splitDecision.workingWeekVolumeChange,transition);
 assert.deepEqual(describeRegionalWorkingWeekChange(h,s,EXERCISES),transition);
 decisions.push({config:'Converted standalone ULPPL',base:[
   total(h.nextEngine.program.sessions),total(s.nextEngine.program.sessions)
 ],firstWeek:[firstH.weeklySets,firstS.weeklySets],
 finalWeek:[lastH.weeklySets,lastS.weeklySets],
 firstDaySets:[firstH.daySets,firstS.daySets],
 lastDaySets:[lastH.daySets,lastS.daySets],split:[h.config.split,s.config.split]});
}
for(const row of decisions){
 assert.ok(row.firstWeek.every(n=>n>=1),'Week 1 must not be empty');
 assert.ok(row.finalWeek.every(n=>n>=1),'Final work week must not be empty');
 assert.ok(row.firstWeek[1]>=Math.ceil(row.firstWeek[0]*.5),
   `${row.config}: executable first-week strength sets fell from ${row.firstWeek[0]} to ${row.firstWeek[1]}`);
 assert.ok(row.finalWeek[1]>=Math.ceil(row.finalWeek[0]*.5),
   `${row.config}: executable final-week strength sets fell from ${row.finalWeek[0]} to ${row.finalWeek[1]}`);
 assert.ok(row.firstDaySets.every(days=>days.every(n=>n>=1)));
}
console.log('M304 executable working-week transition matrix: '+JSON.stringify(decisions,null,2));

// M306: a converted working program may have user-owned set overrides. The
// phase comparison must use those real executable cells (not the originally
// generated low-volume baseline) and never silently lose a large authored day.
{
 const config={...fixtures[3].config,name:'M306 edited conversion',goal:'hypertrophy',weeks:8};
 let id=0;
 const source=generateNextProgramForShell({
   config,legacyExercises:EXERCISES,seed:30673,
   makeId:()=>`m306-source-${++id}`
 }).program;
 const edited=structuredClone(source);
 const day=edited.days[0];
 edited.overrides={...edited.overrides};
 const editedSetsPerExercise=Math.max(6,Math.ceil(32/day.exercises.length));
 day.exercises.forEach((exercise,slot)=>{
   const key=`${day.id}:${slot}`;
   edited.overrides[key]={...(edited.overrides[key]??{}),sets:editedSetsPerExercise,
     prescriptionOwners:{...(edited.overrides[key]?.prescriptionOwners??{}),sets:'user'}};
 });
 const original=JSON.stringify(edited);
 const prior=view(edited,8);
 assert.ok(prior.daySets[0]>=30,'User-entered higher-set day is represented by executable cells');
 let built=null,refused=null;
 try{
   built=convertProgramToNextCycleForShell({
     program:edited,templateId:'custom',blocks:[{phase:'strength_accumulation',weeks:5}],
     legacyExercises:EXERCISES,seed:30674,
     makeId:()=>`m306-cycle-${++id}`,adaptTrainingSplit:true,adaptBetweenBlocks:true
   });
 }catch(error){refused=error;}
 assert.equal(JSON.stringify(edited),original,'Conversion and rejection both leave manual set prescriptions untouched');
 if(built){
   const strength=built.blocks[0],now=view(strength,1);
   const decision=built.cycle.blockMeta[1].splitDecision;
   assert.equal(strength.nextEngine.program.audit.result,'pass');
   assert.ok(now.weeklySets>=Math.ceil(prior.weeklySets*.5),
     'A converted highly edited program cannot lose more than half its executable weekly sets');
   assert.equal(decision.workingWeekVolumeChange.previousWeeklySets,prior.weeklySets,
     'Real manual set edits, rather than the original base, own the transition comparison');
   assert.equal(decision.workingWeekVolumeChange.currentWeeklySets,now.weeklySets);
   assert.deepEqual(built.currentProgram.overrides,edited.overrides);
   console.log('M306 edited conversion accepted: '+JSON.stringify({
     previousDaySets:prior.daySets,nextDaySets:now.daySets,
     previousWeeklySets:prior.weeklySets,nextWeeklySets:now.weeklySets
   }));
 }else{
   assert.ok(refused?.code||refused?.message,'Unsafe edited program must have an explicit error');
   assert.match(String(refused.message),/strength|volume|safe|cycle|constraint|time|training/i,
     'Edited cycle refusal must communicate a training constraint');
   console.log('M306 edited conversion explicitly refused: '+String(refused.message));
 }
}
console.log('PASS M306: 30+ set edited conversion is either audited and transparently retained or rejected without mutation.');

console.log('PASS M315: every saved cycle work-week certifies region sets, weighted muscle coverage and lift practice against executable prescriptions.');
console.log('PASS M304/M305: executable week prescriptions and phase-target coverage remain separate from actual regional changes.');
