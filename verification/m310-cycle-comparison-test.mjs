
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { captureShellVolumeSnapshot } from '../modules/next-engine/volume-repair.js';

// M310: old fixed-split behavior and new goal-optimized adaptation are tested
// with exactly the same athlete, gym, training weeks and generation seed.
// This does not claim to execute an obsolete legacy engine binary.
const fixtures=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url),'utf8')).rows;
const cases=[
 {label:'4-day Upper/Lower size-to-strength',row:2,seed:30442,goal:'hypertrophy',focus:{}},
 {label:'5-day ULPPL size-to-strength',row:3,seed:30453,goal:'hypertrophy',focus:{}},
 {label:'5-day ULPPL back focus',row:3,seed:30713,goal:'both',focus:{lats:2,upper_back:2}}
];
const weeks=[{phase:'hypertrophy_accumulation',weeks:5},{phase:'strength_accumulation',weeks:5}];
const sum=sessions=>sessions.reduce((n,s)=>n+s.exercises.reduce((v,e)=>v+e.sets,0),0);
const round=v=>Math.round(v*10)/10;
function generate(sample,adapt){
 let id=0;
 const config={...fixtures[sample.row].config,name:'M310 actual cycle comparison',
   goal:sample.goal,focus:sample.focus,reduce:[],weeks:5};
 return generateNextCycleForShell({
  templateId:'custom',config,blocks:weeks,legacyExercises:EXERCISES,seed:sample.seed,
  makeId:()=> 'm310-'+sample.seed+'-'+(adapt?'adaptive':'fixed')+'-'+(++id),
  adaptBetweenBlocks:true,adaptTrainingSplit:adapt
 });
}
function describe(result){
 const [h,s]=result.blocks;
 const b=captureShellVolumeSnapshot(h,5,EXERCISES);
 const a=captureShellVolumeSnapshot(s,1,EXERCISES);
 const end=captureShellVolumeSnapshot(s,5,EXERCISES);
 const d=result.cycle.blockMeta[1].splitDecision;
 if(d.mode==='goal_optimized'){
  assert.equal(d.realizedSelection?.mode,'finalized_strength_top_two',
    'Adaptive strength ranking must compare finalized executable finalists');
  assert.ok(d.realizedSelection.evaluatedFinalists>=1 && d.realizedSelection.evaluatedFinalists<=2);
  assert.equal(d.realizedSelection.firstWeekSets,
    sum(captureShellVolumeSnapshot(s,1,EXERCISES).sessions));
  assert.equal(d.realizedSelection.lastWeekSets,
    sum(captureShellVolumeSnapshot(s,s.config.weeks,EXERCISES).sessions));
  assert.equal(d.realizedSelection.executableCoverage,
    Math.min(...[0,d.realizedQuality.weeks.length-1].map(i=>
      d.realizedQuality.weeks[i].muscleTargetCoveragePercent)));
  assert.equal(d.realizedSelection.executableLiftPractice,
    Math.min(...[0,d.realizedQuality.weeks.length-1].map(i=>
      d.realizedQuality.weeks[i].liftPracticePercent)));
  assert.ok(Number.isFinite(d.realizedSelection.score)
    && Number.isFinite(d.realizedSelection.baseScore));
  assert.deepEqual(s.nextEngine.cycleTemplate.splitDecision.realizedSelection,d.realizedSelection);
 } else assert.equal(d.realizedSelection,undefined,
   'Fixed split must not execute or display adaptive finalist selection');
 assert.equal(h.nextEngine.program.audit.result,'pass');
 assert.equal(s.nextEngine.program.audit.result,'pass');
 for(const snapshot of [b,a,end]){
  assert.ok(snapshot&&!snapshot.missing);
  assert.ok(snapshot.sessions.every(x=>x.exercises.every(e=>Number.isInteger(e.sets)&&e.sets>=1)));
  assert.ok(snapshot.sessions.every(x=>x.estimatedMinutes<=x.maxMinutes+.01),
   'The executable cycle must obey day-specific clock limits');
 }
 assert.equal(d.workingWeekVolumeChange.previousWeeklySets,sum(b.sessions));
 assert.equal(d.workingWeekVolumeChange.currentWeeklySets,sum(a.sessions));
 assert.ok(sum(a.sessions)>=Math.ceil(sum(b.sessions)*.5));
 return {
  splits:[h.config.split,s.config.split],boundarySets:[sum(b.sessions),sum(a.sessions)],
  lastStrengthWeekSets:sum(end.sessions),
  perDaySets:a.sessions.map(x=>x.exercises.reduce((n,e)=>n+e.sets,0)),
  perDayMinutes:a.sessions.map(x=>Math.round(x.estimatedMinutes)),
  latDose:[round(b.regions.lats),round(a.regions.lats)],
  upperBackDose:[round(b.regions.upper_back),round(a.regions.upper_back)],
  liftPractice:d.liftFrequencies,
  focusedDoseStatus:s.nextEngine.focusedStrengthDose?.status??'none',
  focusSetChanges:s.nextEngine.focusedStrengthDose?.changes?.length??0,
  finalizedSelectionMode:d.realizedSelection?.mode??null,
  strengthDays:a.sessions.map(x=>({day:x.day,
    exercises:x.exercises.slice(0,4).map(e=>({
      name:e.name,sets:e.sets,reps:e.prescription?.reps,
      rir:e.prescription?.rir,role:e.role
    }))
  }))
 };
}
const matrix=[];
for(const sample of cases){
 const old=describe(generate(sample,false));
 const current=describe(generate(sample,true));
 assert.equal(old.splits[0],fixtures[sample.row].config.split);
 assert.equal(old.splits[1],fixtures[sample.row].config.split);
 assert.equal(old.perDaySets.length,fixtures[sample.row].config.days);
 assert.equal(current.perDaySets.length,fixtures[sample.row].config.days);
 if(sample.label==='4-day Upper/Lower size-to-strength'){
  assert.ok(current.liftPractice.back_squat>old.liftPractice.back_squat,
   'A feasible adaptive strength structure should provide additional squat practice for this seeded case');
  assert.notEqual(current.splits[1],old.splits[1],
   'The four-day goal-optimized seed must test a real training-split change');
 }
 if(sample.label==='5-day ULPPL size-to-strength'){
  assert.ok(current.latDose[1]>=old.latDose[1]+2,
   'Adaptive split must protect meaningfully more actual lat work than fixed ULPPL in this case');
  assert.ok(current.liftPractice.deadlift>old.liftPractice.deadlift,
   'The five-day strength rotation should offer more deadlift practice');
  assert.notEqual(current.splits[1],old.splits[1],
   'The five-day goal-optimized seed must test a real training-split change');
 }
 if(sample.label==='5-day ULPPL back focus'){
  assert.ok(current.latDose[1]>=current.latDose[0]*.5,
   'The chosen executable strength block must preserve at least half the focused lat dose');
  assert.ok(current.focusSetChanges>0||current.finalizedSelectionMode==='finalized_strength_top_two',
   'Lat preservation must come from audited set repair or a genuinely finalized split');
 }
 matrix.push({scenario:sample.label,seed:sample.seed,
  oldMode:'fixed-split cycle',newMode:'adaptive-split cycle',old,current});
}
console.log('M310 actual fixed-versus-adaptive cycle comparison: '+JSON.stringify(matrix,null,2));
console.log('PASS M310: full executable cycle split comparison preserves week doses, rep/RIR, session clock and practice.');
