import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/engine-api.js';

import { generateNextCycleForShell, scoreRegionalCarryover, scoreLiftPractice } from '../modules/next-engine/cycle-runtime-adapter.js';
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
 // M314 ranking must disclose the actual requested-priority lift frequencies
 // and a supporting-region floor distinct from specialized-region continuity.
 assert.ok(Number.isInteger(decision.liftPracticePercent) && decision.liftPracticePercent>=0
    && decision.liftPracticePercent<=100);
 assert.ok(Number.isInteger(decision.weakestPriorityLiftPracticePercent)
    && decision.weakestPriorityLiftPracticePercent>=0);
 assert.ok(Number.isInteger(decision.weakestSupportingRegionCoveragePercent)
    && decision.weakestSupportingRegionCoveragePercent>=0);
 assert.ok(Array.isArray(decision.liftPracticeShortfalls));
 assert.ok(Object.entries(decision.liftPracticeTargets).every(([lift,target])=>
    target>=1 && Number.isInteger(target) && lift in decision.liftFrequencies));
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
   // M316 may select a better fully materialized split that already carries
   // the needed lat dose. Do not require an unnecessary set change simply to
   // satisfy an old seeded intervention count. Verify executable continuity.
   assert.ok(current.nextEngine.focusedStrengthDose.changes.length>0
     || (decision.realizedSelection?.mode==='finalized_strength_top_two'
       && lats.currentSets>=lats.previousSets*.5),
     'Focused-lat continuity must come from an audited set repair or a genuinely finalized split');
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

// M314: athlete priority must matter to the phase's strength-specific ranking.
{
 const priorities={bench_press:'primary',back_squat:'high',deadlift:'high',overhead_press:'normal'};
 const requested={bench_press:3};
 const full={bench_press:3,back_squat:2,deadlift:2,overhead_press:2};
 const ideal=scoreLiftPractice(full,priorities,requested,'strength_accumulation');
 assert.equal(ideal.percent,100);
 assert.equal(ideal.targets.bench_press,3,'Explicit three-day bench practice cannot be replaced by a generic two-day target');
 const lostBench=scoreLiftPractice({...full,bench_press:2},priorities,requested,'strength_accumulation');
 const lostPress=scoreLiftPractice({...full,overhead_press:1},priorities,requested,'strength_accumulation');
 assert.ok(lostBench.quality<lostPress.quality,
   'Losing one primary bench practice must cost more than one optional press practice');
 assert.equal(lostBench.weakestPriorityPercent,67);
 assert.ok(lostBench.shortfalls.includes('bench_press') && !lostBench.shortfalls.includes('overhead_press'));
 assert.equal(lostPress.weakestPriorityPercent,100);
 const reduced=scoreLiftPractice({...full,overhead_press:0},
   {...priorities,overhead_press:'maintenance'},requested,'strength_accumulation');
 assert.equal(reduced.percent,100,'User-reduced overhead practice may not be penalized');
 const peak=scoreLiftPractice({bench_press:1},{bench_press:'primary'}, {},'peak');
 assert.equal(peak.percent,100,'Peaking uses one exposure by default, not accumulation volume');
 assert.deepEqual(full,{bench_press:3,back_squat:2,deadlift:2,overhead_press:2});
}
{
 const prescription=[{muscle:'chest',preferred:10},{muscle:'quads',preferred:10},{muscle:'hamstrings',preferred:10}];
 const before={chest:10,quads:10,hamstrings:10};
 const neglected=scoreRegionalCarryover(before,{chest:0,quads:10,hamstrings:10},
   {chest:'normal',quads:'normal',hamstrings:'normal'},prescription);
 assert.equal(neglected.weakestSupportingPercent,0,
   'High total dose cannot conceal complete loss of an ordinary productive chest region');
 assert.equal(neglected.weakestFocusedPercent,100,'Ordinary chest is not an explicit specialization');
 const recovered=scoreRegionalCarryover(before,{chest:4.5,quads:10,hamstrings:10},
   {chest:'normal',quads:'normal',hamstrings:'normal'},prescription);
 assert.equal(recovered.weakestSupportingPercent,100,
   'Beneath-strength-target retention is rewarded until its phase-bounded useful target');
 const reduced=scoreRegionalCarryover(before,{chest:0,quads:10,hamstrings:10},
   {chest:'maintenance',quads:'normal',hamstrings:'normal'},prescription);
 assert.equal(reduced.weakestSupportingPercent,100,
   'An explicitly reduced muscle cannot impose a hidden continuity quota');
 const focused=scoreRegionalCarryover(before,{chest:0,quads:10,hamstrings:10},
   {chest:'specialization',quads:'normal',hamstrings:'normal'},prescription);
 assert.equal(focused.weakestFocusedPercent,0);
 assert.equal(focused.weakestSupportingPercent,100,
   'Explicit focus is separately protected, not double-counted as ordinary muscle');
 const tiny=scoreRegionalCarryover({...before,chest:1},{chest:0,quads:10,hamstrings:10},
   {chest:'normal',quads:'normal',hamstrings:'normal'},prescription);
 assert.equal(tiny.weakestSupportingPercent,100,'Trivial prior dose cannot become a continuity obligation');
}
console.log('PASS M314: weighted primary-lift practice and separately protected ordinary muscle dose remain soft, phase-bounded ranking signals.');

console.log('PASS M307: focused region doses measured independently of target-coverage score.');
