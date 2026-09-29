import assert from 'node:assert/strict';
import { generateProgram } from '../modules/next-engine/generate.js';
import { deriveProgressionSelectionEvidence } from '../modules/next-engine/workout-history-adapter.js';

const loading={
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500}, cable:{minimum:5,increment:5,maximum:300}, smith:{minimum:5,increment:5,maximum:500}, exerciseOverrides:{}
};
const request={
  athlete:{experience:'intermediate',trainingAgeMonths:36},
  goal:{type:'strength',musclePriorities:{},liftPriorities:{bench_press:'high',back_squat:'high',deadlift:'high'}},
  schedule:{days:['monday','tuesday','thursday','friday','sunday'].map(day=>({day,minMinutes:60,maxMinutes:90,targetExercises:6}))},
  equipment:{available:['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'],bodyweight:'allow',loading},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
  preferences:{preferredSplit:'ulppl',lockedSplit:'ulppl',avoidedExercises:[]},
  customExercises:[], seed:18902
};

const shortBlock=generateProgram(request,{phase:'intensification',blockWeeks:4}).program;
const longBlock=generateProgram(request,{phase:'intensification',blockWeeks:6}).program;
assert.equal(shortBlock.audit.result,'pass');
assert.equal(longBlock.audit.result,'pass');
const shortPrimary=shortBlock.sessions.flatMap(s=>s.exercises).filter(e=>e.role==='primary_strength');
const longPrimary=longBlock.sessions.flatMap(s=>s.exercises).filter(e=>e.role==='primary_strength');
assert.ok(shortPrimary.length>0 && longPrimary.length>0,'test needs primary strength work');
assert.ok(shortPrimary.every(e=>e.progressionStyle!=='wave'),'four-week blocks must not start a five-plus-week loading wave');
assert.ok(longPrimary.some(e=>e.progressionStyle==='wave'),'a long enough intensification block should allow wave loading on an eligible primary lift');

const evidence=deriveProgressionSelectionEvidence([
  {
    performedSets:[
      {exerciseId:'barbell_bench',load:200,reps:5,rir:2},
      {exerciseId:'barbell_bench',load:200,reps:5,rir:null}
    ],
    progression:[{exerciseId:'barbell_bench',outcome:'success',reasonCode:'progression_success',action:'increase_load',estimated1RM:245}]
  },
  {
    performedSets:[
      {exerciseId:'barbell_bench',load:205,reps:4,rir:0},
      {exerciseId:'barbell_bench',load:205,reps:4,rir:0}
    ],
    progression:[{exerciseId:'barbell_bench',outcome:'failure',reasonCode:'rep_floor_miss',action:'hold',estimated1RM:232}]
  },
  {
    performedSets:[
      {exerciseId:'barbell_bench',load:205,reps:5,rir:2},
      {exerciseId:'barbell_bench',load:205,reps:5,rir:2}
    ],
    progression:[{exerciseId:'barbell_bench',outcome:'success_blocked',reasonCode:'loading_inventory_blocked',action:'review',estimated1RM:251}]
  },
  {
    performedSets:[{exerciseId:'barbell_bench',load:205,reps:3,rir:0}],
    progression:[{exerciseId:'barbell_bench',outcome:'incomplete',reasonCode:'incomplete_prescription',action:'hold',estimated1RM:226}]
  }
]);
const bench=evidence.barbell_bench;
assert.equal(bench.comparableExposures,3,'incomplete exposures must not count toward method reselection');
assert.equal(bench.styleExposures,3);
assert.equal(bench.failureCount,1);
assert.equal(bench.stallCount,1,'ordinary successful or blocked exposures must not be mislabeled stalls');
assert.equal(bench.loadingBlockedCount,1);
assert.equal(bench.e1rmSamples,3);
assert.equal(bench.rirCoverage,5/6,'RIR coverage must reflect actual reported effort, not target RIR');

console.log('PASS M189 integration: actual block duration reaches Auto selection and longitudinal history produces conservative per-lift progression evidence.');
