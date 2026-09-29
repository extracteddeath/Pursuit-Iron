import assert from 'node:assert/strict';
import { CURATED_EXERCISES } from '../modules/next-engine/exercise-db.js';
import { selectProgressionStyle, reselectProgressionStyle } from '../modules/next-engine/progression-style.js';
import { generateProgram } from '../modules/next-engine/generate.js';
import { transitionProgramPhase } from '../modules/next-engine/phase-transition.js';

const byId = new Map(CURATED_EXERCISES.map(ex => [ex.id, ex]));
const ex = id => {
  const value = byId.get(id);
  assert.ok(value, `missing curated exercise ${id}`);
  return value;
};
const style = (id, role, context) => selectProgressionStyle(ex(id), role, context).style;

// Creation-time selection: progression belongs to the exercise + phase, not just the program goal.
assert.equal(style('back_squat', 'primary_strength', { phase:'strength_accumulation', experience:'novice', blockWeeks:6 }), 'linear',
  'novice barbell compounds should still use linear progression');
assert.equal(style('pullup', 'hypertrophy_compound', { phase:'hypertrophy_accumulation', experience:'novice', blockWeeks:6 }), 'ladder',
  'novice bodyweight movements with a broad rep range should build reps before external load');
assert.equal(style('machine_press', 'hypertrophy_compound', { phase:'hypertrophy_accumulation', experience:'intermediate', blockWeeks:6 }), 'dynamic',
  'quick-change compound loading should use per-set dynamic double progression');
assert.equal(style('barbell_bench', 'primary_strength', { phase:'hypertrophy_accumulation', experience:'intermediate', blockWeeks:6 }), 'double',
  'barbell accumulation should keep one shared working load');
assert.equal(style('cable_fly', 'hypertrophy_isolation', { phase:'hypertrophy_accumulation', experience:'advanced', blockWeeks:6 }), 'double',
  'isolation work should not be over-engineered');
assert.equal(style('barbell_bench', 'primary_strength', { phase:'intensification', experience:'intermediate', blockWeeks:6 }), 'wave',
  'a sufficiently long intensification block should allow a wave on a primary competition-style lift');
assert.notEqual(style('barbell_bench', 'primary_strength', { phase:'intensification', experience:'intermediate', blockWeeks:4 }), 'wave',
  'short blocks should not start a wave they cannot complete');
assert.equal(style('barbell_bench', 'primary_strength', { phase:'peak', experience:'intermediate', blockWeeks:3 }), 'e1rm',
  'peak competition-style barbell strength work should use e1RM autoregulation');
assert.equal(selectProgressionStyle(ex('machine_press'), 'hypertrophy_compound', {
  phase:'hypertrophy_accumulation', experience:'intermediate', requestedStyle:'ladder'
}).style, 'ladder', 'manual progression choices must remain authoritative');

// Ongoing Auto: change only with enough evidence or a legitimate phase change.
assert.equal(reselectProgressionStyle(ex('back_squat'), 'primary_strength', {
  phase:'strength_accumulation', experience:'novice', currentStyle:'linear',
  evidence:{ comparableExposures:3, styleExposures:3, failureCount:2, stallCount:2 }
}).style, 'double', 'repeated stalls should graduate novice linear progression');
assert.equal(reselectProgressionStyle(ex('barbell_bench'), 'primary_strength', {
  previousPhase:'strength_accumulation', phase:'peak', experience:'intermediate', currentStyle:'double', blockWeeks:3,
  prescription:{ reps:[1,3], rir:[0,1] }, evidence:{ comparableExposures:4, styleExposures:4, rirCoverage:.9, e1rmSamples:4 }
}).style, 'e1rm', 'phase changes should re-select progression for the target prescription');
assert.equal(reselectProgressionStyle(ex('barbell_bench'), 'primary_strength', {
  previousPhase:'strength_accumulation', phase:'peak', experience:'intermediate', currentStyle:'double', blockWeeks:3,
  prescription:{ reps:[1,3], rir:[0,1] }, evidence:{ comparableExposures:4, styleExposures:4, fatigueLimited:true, rirCoverage:.9, e1rmSamples:4 }
}).style, 'double', 'fatigue-limited evidence should prevent escalation to an advanced method');
assert.equal(reselectProgressionStyle(ex('barbell_bench'), 'primary_strength', {
  phase:'intensification', experience:'intermediate', currentStyle:'wave', blockWeeks:6,
  prescription:{ reps:[2,5], rir:[1,2] }, evidence:{ comparableExposures:4, styleExposures:4, rirCoverage:.25, e1rmSamples:1 }
}).style, 'double', 'advanced methods should fall back when effort/e1RM evidence is too sparse');
assert.equal(reselectProgressionStyle(ex('machine_press'), 'hypertrophy_compound', {
  phase:'hypertrophy_accumulation', experience:'intermediate', currentStyle:'dynamic',
  evidence:{ comparableExposures:4, styleExposures:4, successful:true }
}).style, 'dynamic', 'successful methods should not be changed just for novelty');
assert.equal(reselectProgressionStyle(ex('pullup'), 'hypertrophy_compound', {
  phase:'hypertrophy_accumulation', experience:'intermediate', currentStyle:'double',
  evidence:{ comparableExposures:4, styleExposures:4, loadingBlockedCount:2 }
}).style, 'ladder', 'repeated bodyweight loading walls should move Auto toward rep-ladder progression');
assert.equal(reselectProgressionStyle(ex('machine_press'), 'hypertrophy_compound', {
  phase:'hypertrophy_accumulation', experience:'intermediate', currentStyle:'double', requestedStyle:'double',
  evidence:{ comparableExposures:12, failureCount:6, stallCount:6 }
}).style, 'double', 'manual choices must not be replaced by adaptive Auto');

// Full engine integration: generation stores the selector's choice, and block transition reviews it.
const equipment=['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading={
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500}, cable:{minimum:5,increment:5,maximum:300}, smith:{minimum:5,increment:5,maximum:500}, exerciseOverrides:{}
};
const request={
  athlete:{experience:'intermediate',trainingAgeMonths:36},
  goal:{type:'mixed',musclePriorities:{},liftPriorities:{bench_press:'high',back_squat:'high',deadlift:'high'}},
  schedule:{days:['monday','tuesday','thursday','friday','sunday'].map(day=>({day,minMinutes:60,maxMinutes:90,targetExercises:6}))},
  equipment:{available:equipment,bodyweight:'allow',loading},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
  preferences:{preferredSplit:'ulppl',lockedSplit:'ulppl',avoidedExercises:[]},
  customExercises:[], seed:18901
};
const previous=generateProgram(request,{phase:'strength_accumulation'}).program;
assert.equal(previous.audit.result,'pass','M189 integration baseline must pass audit');
const previousExercises=previous.sessions.flatMap(s=>s.exercises);
const successfulExerciseIds=[...new Set(previousExercises.map(e=>e.exerciseId))];
const protectedExerciseIds=[...new Set(previousExercises.filter(e=>e.role==='primary_strength').map(e=>e.exerciseId))];
const transitioned=transitionProgramPhase(previous,request,'peak',{
  successfulExerciseIds, protectedExerciseIds, replaceExerciseIds:[], techniqueLimitedExerciseIds:[], fatigueLimitedExerciseIds:[], nextBlockWeeks:3
});
assert.equal(transitioned.program.audit.result,'pass','adaptive progression transition must preserve engine audit');
const retainedPrimary=transitioned.program.sessions.flatMap(s=>s.exercises)
  .filter(e=>e.role==='primary_strength' && previousExercises.some(p=>p.exerciseId===e.exerciseId));
assert.ok(retainedPrimary.length>0,'peak transition should retain at least one successful primary strength exercise');
for (const lift of retainedPrimary) {
  const def=byId.get(lift.exerciseId);
  if (def?.flags?.barbell && Number(def.loadability)>=6) {
    assert.equal(lift.progressionStyle,'e1rm',`${lift.name} should be re-selected to peak e1RM progression`);
    assert.ok(lift.progressionSelection?.reason,'adaptive transition should preserve progression-selection explainability');
  }
}
assert.ok(Array.isArray(transitioned.continuity.progressionMethodChanges),'block review must report progression method changes');

console.log('PASS M189: Auto progression selection is exercise/phase aware at creation, conservative with sparse evidence, adapts after stalls/fatigue/loading constraints, respects manual overrides, and re-selects methods at block transitions.');
