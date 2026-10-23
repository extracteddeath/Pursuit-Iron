import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { generateNextCycleForShell, scoreRegionalCarryover } from '../modules/next-engine/cycle-runtime-adapter.js';
import { PUBLIC_MEV_REGIONS, PUBLIC_REGION_MUSCLE } from '../modules/next-engine/public-mev.js';
import { shellVolumeTargets } from '../modules/next-engine/volume-repair.js';

// M307: independent stress matrix for selected muscle focus. Compare the
// executable hypertrophy final week with strength Week 1; aggregate totals or
// a 100% target coverage score cannot replace regional dose measurements.
const fixtures=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url),'utf8')).rows;
const scenarios=[
  {label:'5-day back focus',row:3,focus:{lats:2,upper_back:2},seed:30713},
  {label:'5-day side/rear delt focus',row:3,focus:{shoulders:2,rear_delts:2},seed:30723},
  {label:'5-day chest focus',row:3,focus:{chest:2},seed:30733},
  {label:'4-day posterior focus',row:2,focus:{hamstrings:2,glutes:1},seed:30742}
];
const matrix=[];
for(const {label,row,focus,seed} of scenarios){
 let id=0;
 const config={...fixtures[row].config,name:'M307 regional dose focus',goal:'both',focus,reduce:[],deload:false};
 const result=generateNextCycleForShell({
  templateId:'custom',config,
  blocks:[{phase:'hypertrophy_accumulation',weeks:5},{phase:'strength_accumulation',weeks:5}],
  legacyExercises:EXERCISES,seed,makeId:()=>`m307-${seed}-${++id}`,
  adaptTrainingSplit:true,adaptBetweenBlocks:true
 });
 const decision=result.cycle.blockMeta[1].splitDecision;
 const current=result.blocks[1],previous=result.blocks[0];
 const actual=decision.workingWeekVolumeChange;
 assert.equal(current.nextEngine.program.audit.result,'pass');
 assert.ok(current.nextEngine.focusedStrengthDose,
   'Every focused hypertrophy-to-strength transition reports its feasibility-limited dose allocation');
 assert.ok(['unchanged','improved','limited'].includes(current.nextEngine.focusedStrengthDose.status));
 assert.deepEqual(current.nextEngine.focusedStrengthDose.comparison,
   'last_hypertrophy_week_to_first_strength_week');
 assert.ok(actual,'Transition must report exact executable doses');
 assert.equal(actual.regions.length>0,true);
 assert.deepEqual(current.nextEngine.cycleTemplate.splitDecision.workingWeekVolumeChange,actual);
 const expectedFocusedReductions=actual.regions.filter(region=>{
   const priority=current.nextEngine.request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[region.region]];
   return ['high','specialization','primary'].includes(priority)
     && region.previousSets>=4&&region.currentSets<region.previousSets*.5;
 }).map(region=>region.region);
 assert.deepEqual(actual.focusedVolumeReductions,expectedFocusedReductions,
   'Warnings come only from prioritized executable muscle doses, not modeled phase coverage');
 if(label==='5-day back focus'){
   const lats=actual.regions.find(r=>r.region==='lats');
   const upperBack=actual.regions.find(r=>r.region==='upper_back');
   assert.ok(lats&&upperBack,'Back width and thickness retain independent executable dose credit');
   assert.equal(actual.focusedVolumeReductions.includes('lats'),
     lats.previousSets>=4&&lats.currentSets<lats.previousSets*.5,
     'A recovered lat dose must clear its warning; a genuine loss must remain visible');
   assert.equal(actual.focusedVolumeReductions.includes('upper_back'),
     upperBack.previousSets>=4&&upperBack.currentSets<upperBack.previousSets*.5,
     'Upper-back warnings must independently match its measured volume');
   // This real five-day gym fixture was proven feasible at 6 credited lat
   // sets without stripping a strength anchor or another focused region.
   // Do not silently regress to M308's 5/11.8 working-set shortfall.
   assert.ok(lats.currentSets>=lats.previousSets*.5,
     'The feasible focused-lat continuity repair must protect at least half the prior productive dose');
   assert.ok(current.nextEngine.focusedStrengthDose.changes.length>0,
     'This seeded transition must make a real audited set change, not just alter a ranking score');
 }

 const focused=actual.regions.filter(x=>{
   const m=PUBLIC_REGION_MUSCLE[x.region];
   return current.nextEngine.request.goal.musclePriorities[m]==='specialization';
 });
 const targets=shellVolumeTargets(current).filter(x=>focused.some(r=>r.region===x.region));
 const dose=focused.map(x=>({
   region:x.region,prior:x.previousSets,next:x.currentSets,
   retentionPct:x.previousSets>0?Math.round(x.currentSets/x.previousSets*100):null,
   preferred:Math.round((targets.find(t=>t.region===x.region)?.mav||0)*10)/10
 }));
 assert.ok(focused.length>=1,'Focus input reaches strength engine as specialization');
 assert.ok(focused.every(x=>PUBLIC_MEV_REGIONS.includes(x.region)));
 assert.ok(dose.every(x=>Number.isFinite(x.prior)&&Number.isFinite(x.next)&&x.next>=0));
 for(const change of current.nextEngine.focusedStrengthDose.changes){
   assert.ok(change.creditedSetsGained>0 && change.weeks===current.config.weeks,
     'Feasible focus changes must improve credited sets consistently across work weeks');
   assert.ok(change.operations.some(o=>o.delta===1),'Every reallocation raises a focused accessory');
   assert.ok(change.operations.every(o=>Math.abs(o.delta)===1),'No bulk or fractional working-set edits');
 }
 matrix.push({label,split:[previous.config.split,current.config.split],
   weekly:[actual.previousWeeklySets,actual.currentWeeklySets],targetCoverage:decision.regionalCarryoverPercent,
   focusedDose:dose,regionalAtRisk:decision.regionalRiskRegions,
   focusedVolumeReductions:actual.focusedVolumeReductions});
}
console.log('M307 focused executable region matrix: '+JSON.stringify(matrix,null,2));
// M307 regression: an aggregate back prescription should not treat losing
// focused lat work as perfect retention merely because the *strength* phase
// targets lower back volume. Keep normal intent and intentional maintenance
// on their existing, softer paths.
{
 const prior={lats:12,upper_back:10},current={lats:5,upper_back:6.3};
 const backPrescription=[{muscle:'back',preferred:10}];
 const ordinary=scoreRegionalCarryover(prior,current,{back:'normal'},backPrescription);
 assert.equal(ordinary.percent,100,'Ordinary back volume remains on the original half-prescription scoring path');
 const prioritized=scoreRegionalCarryover(prior,current,{back:'specialization'},backPrescription);
 assert.equal(prioritized.weakestFocusedPercent,83,
   'The optimizer cannot average away an under-covered focused lat region behind strong upper-back coverage');
 assert.ok(prioritized.percent>prioritized.weakestFocusedPercent,
   'The separate weakest-focus signal is stricter than aggregate regional continuity');
 assert.ok(prioritized.percent<100&&prioritized.atRiskRegions.includes('lats'),
   'Specialized back flags lost lat work independently of retained upper-back work');
 assert.ok(!prioritized.atRiskRegions.includes('upper_back'),'Covered upper back is not falsely flagged');
 const recovered=scoreRegionalCarryover(prior,{lats:6,upper_back:6.3},{back:'specialization'},backPrescription);
 assert.equal(recovered.percent,100,'A modest, feasible focused-width recovery satisfies soft carryover');
 assert.equal(recovered.weakestFocusedPercent,100,'The weakest-focused signal recovers when lat dose is restored');
 const extreme=scoreRegionalCarryover({lats:100,upper_back:100},{lats:6,upper_back:6},
   {back:'specialization'},backPrescription);
 assert.equal(extreme.percent,100,'A very large hypertrophy prior cannot become a strength-volume quota');
 assert.equal(extreme.weakestFocusedPercent,100,'Extreme prior dose cannot impose a hidden weakest-region quota');
 const reduced=scoreRegionalCarryover(prior,current,{back:'maintenance'},backPrescription);
 assert.equal(reduced.percent,100,'Explicitly reduced back work is exempt from soft carryover preferences');
 assert.equal(reduced.weakestFocusedPercent,100,'Reduced muscles never receive a focus continuity penalty');
 assert.deepEqual(prior,{lats:12,upper_back:10},'Scoring preserves the input ledger');
}
console.log('PASS M307 prioritized back: independent width/thickness scoring without strength-volume quotas.');

console.log('PASS M307: focused region doses measured independently of target-coverage score.');
