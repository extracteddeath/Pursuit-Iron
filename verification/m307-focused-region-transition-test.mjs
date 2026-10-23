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
 assert.ok(actual,'Transition must report exact executable doses');
 assert.equal(actual.regions.length>0,true);
 assert.deepEqual(current.nextEngine.cycleTemplate.splitDecision.workingWeekVolumeChange,actual);
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
 matrix.push({label,split:[previous.config.split,current.config.split],
   weekly:[actual.previousWeeklySets,actual.currentWeeklySets],targetCoverage:decision.regionalCarryoverPercent,
   focusedDose:dose,regionalAtRisk:decision.regionalRiskRegions});
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
 assert.ok(prioritized.percent<100&&prioritized.atRiskRegions.includes('lats'),
   'Specialized back flags lost lat work independently of retained upper-back work');
 assert.ok(!prioritized.atRiskRegions.includes('upper_back'),'Covered upper back is not falsely flagged');
 const recovered=scoreRegionalCarryover(prior,{lats:6,upper_back:6.3},{back:'specialization'},backPrescription);
 assert.equal(recovered.percent,100,'A modest, feasible focused-width recovery satisfies soft carryover');
 const extreme=scoreRegionalCarryover({lats:100,upper_back:100},{lats:6,upper_back:6},
   {back:'specialization'},backPrescription);
 assert.equal(extreme.percent,100,'A very large hypertrophy prior cannot become a strength-volume quota');
 const reduced=scoreRegionalCarryover(prior,current,{back:'maintenance'},backPrescription);
 assert.equal(reduced.percent,100,'Explicitly reduced back work is exempt from soft carryover preferences');
 assert.deepEqual(prior,{lats:12,upper_back:10},'Scoring preserves the input ledger');
}
console.log('PASS M307 prioritized back: independent width/thickness scoring without strength-volume quotas.');

console.log('PASS M307: focused region doses measured independently of target-coverage score.');
