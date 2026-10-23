import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
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
console.log('PASS M304/M305: executable week prescriptions and phase-target coverage remain separate from actual regional changes.');
