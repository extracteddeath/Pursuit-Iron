import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
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
console.log('PASS M307: focused region doses measured independently of target-coverage score.');
