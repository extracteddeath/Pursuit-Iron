import assert from 'node:assert/strict';
import fs from 'node:fs';
import { generateProgram } from '../modules/next-engine/generate.js';
import { transitionProgramPhase } from '../modules/next-engine/phase-transition.js';
import { shellConfigToNextRequest } from '../modules/next-engine/app-shell-adapter.js';

const equipment=['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading={
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500}, cable:{minimum:5,increment:5,maximum:300}, smith:{minimum:5,increment:5,maximum:500}, exerciseOverrides:{}
};
const baseRequest={
  athlete:{experience:'intermediate',trainingAgeMonths:36},
  goal:{type:'mixed',musclePriorities:{},liftPriorities:{bench_press:'high',back_squat:'high',deadlift:'high'}},
  schedule:{days:['monday','tuesday','thursday','friday','sunday'].map(day=>({day,minMinutes:60,maxMinutes:90,targetExercises:6}))},
  equipment:{available:equipment,bodyweight:'allow',loading},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
  preferences:{preferredSplit:'ulppl',lockedSplit:'ulppl',avoidedExercises:[]},
  customExercises:[], seed:19001
};

// Creation explainability must travel with the actual exercise prescription, not live only in a selector test.
const initial=generateProgram(baseRequest,{phase:'strength_accumulation',blockWeeks:4}).program;
assert.equal(initial.audit.result,'pass','M190 initial program must pass audit');
const initialExercises=initial.sessions.flatMap(s=>s.exercises);
assert.ok(initialExercises.length>0,'M190 needs realized exercises');
for (const exercise of initialExercises) {
  assert.ok(exercise.progressionSelection,'every generated exercise should carry progression selection metadata');
  assert.ok(['auto','manual'].includes(exercise.progressionSelection.source),'selection source should be user-facing metadata');
  assert.ok(exercise.progressionSelection.confidence,'selection confidence should be retained');
  assert.ok(exercise.progressionSelection.reason?.length>12,'selection reason should explain why the method was chosen');
}

// The shell request is the immutable lifecycle contract. A manual global method must survive creation -> history -> next block.
const shellRequest=shellConfigToNextRequest({
  experience:'advanced', goal:'strength', split:'upper_lower', days:4, session:'s90', weeks:4,
  progressionStyle:'double', equipment:['barbell','bench','rack','dumbbell','cable','machine','smith','legpress','pullup'],
  noBodyweight:false, noSupersets:false, unit:'lb'
},[],[],19002);
assert.equal(shellRequest.preferences.progressionStyle,'double','shell request must persist a global manual progression choice');

const manualRequest={
  ...baseRequest,
  athlete:{experience:'advanced',trainingAgeMonths:60},
  preferences:{...baseRequest.preferences,progressionStyle:'double'},
  seed:19003
};
const manualPrevious=generateProgram(manualRequest,{phase:'strength_accumulation',blockWeeks:4,progressionStyle:'double'}).program;
assert.equal(manualPrevious.audit.result,'pass','manual-style baseline must pass');
for (const exercise of manualPrevious.sessions.flatMap(s=>s.exercises)) {
  assert.equal(exercise.progressionStyle,'double','manual double should own creation-time selection');
  assert.equal(exercise.progressionSelection?.source,'manual','manual source should be visible at creation');
}
const manualIds=[...new Set(manualPrevious.sessions.flatMap(s=>s.exercises.map(e=>e.exerciseId)))];
const manualTransition=transitionProgramPhase(manualPrevious,manualRequest,'peak',{
  successfulExerciseIds:manualIds, protectedExerciseIds:manualPrevious.sessions.flatMap(s=>s.exercises.filter(e=>e.role==='primary_strength').map(e=>e.exerciseId)),
  replaceExerciseIds:[], techniqueLimitedExerciseIds:[], fatigueLimitedExerciseIds:[], progressionEvidenceByExercise:{}, nextBlockWeeks:3
});
assert.equal(manualTransition.program.audit.result,'pass','manual-style transition must remain auditable');
for (const exercise of manualTransition.program.sessions.flatMap(s=>s.exercises)) {
  assert.equal(exercise.progressionStyle,'double',`manual method must persist into the next block for ${exercise.name}`);
  assert.equal(exercise.progressionSelection?.source,'manual',`manual source must remain explicit for ${exercise.name}`);
}

// Auto must use the actual NEXT block length even for brand-new target-phase exercises.
const autoPrevious=generateProgram(baseRequest,{phase:'strength_accumulation',blockWeeks:6}).program;
const autoIds=[...new Set(autoPrevious.sessions.flatMap(s=>s.exercises.map(e=>e.exerciseId)))];
const shortTransition=transitionProgramPhase(autoPrevious,baseRequest,'intensification',{
  successfulExerciseIds:autoIds, protectedExerciseIds:autoPrevious.sessions.flatMap(s=>s.exercises.filter(e=>e.role==='primary_strength').map(e=>e.exerciseId)),
  replaceExerciseIds:[], techniqueLimitedExerciseIds:[], fatigueLimitedExerciseIds:[], progressionEvidenceByExercise:{}, nextBlockWeeks:4
});
assert.equal(shortTransition.program.audit.result,'pass','short-block transition must pass audit');
const targetPrimary=shortTransition.program.sessions.flatMap(s=>s.exercises).filter(e=>e.role==='primary_strength');
assert.ok(targetPrimary.length>0,'intensification should contain primary strength work');
for (const exercise of targetPrimary)
  assert.notEqual(exercise.progressionStyle,'wave',`four-week next block must not start a long loading wave for ${exercise.name}`);

// Shell adapter should expose a compact progression plan so UI can explain Auto without recomputing policy.
const adapter=fs.readFileSync(new URL('../modules/next-engine/app-shell-adapter.js',import.meta.url),'utf8');
assert.match(adapter,/progressionPlan:/,'shell program metadata should expose the selected progression plan');
assert.match(adapter,/progressionSelection:/,'slot metadata should carry the engine selection explanation');

console.log('PASS M190: progression selection now survives the full creation/block lifecycle, uses the real next-block duration for new exercises, preserves manual choices, and exposes selection explanations for the UI.');
