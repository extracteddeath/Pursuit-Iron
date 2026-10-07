import { productionSource } from './production-source.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { generateProgram } from '../modules/next-engine/generate.js';
import { transitionProgramPhase } from '../modules/next-engine/phase-transition.js';

const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading = {
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500}, cable:{minimum:5,increment:5,maximum:300}, smith:{minimum:5,increment:5,maximum:500}, exerciseOverrides:{}
};
const request = {
  athlete:{experience:'advanced',trainingAgeMonths:72},
  goal:{type:'strength',musclePriorities:{},liftPriorities:{bench_press:'high',back_squat:'high',deadlift:'high'}},
  schedule:{days:['monday','wednesday','friday','sunday'].map(day=>({day,minMinutes:60,maxMinutes:90,targetExercises:6}))},
  equipment:{available:equipment,bodyweight:'allow',loading},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
  preferences:{preferredSplit:'upper_lower',lockedSplit:'upper_lower',avoidedExercises:[],progressionStyle:'auto'},
  customExercises:[], seed:19201
};

const previous = generateProgram(request,{phase:'strength_accumulation',blockWeeks:6}).program;
assert.equal(previous.audit.result,'pass','M192 baseline must pass audit');
// Make the before-state explicit so the test exercises a real from/to transition instead of depending
// on whichever Auto default future engine versions choose for strength accumulation.
const forcedPrevious = {
  ...previous,
  sessions: previous.sessions.map(session => ({
    ...session,
    exercises: session.exercises.map(ex => ex.role === 'primary_strength'
      ? {...ex,progressionStyle:'double',progression:'Double progression test baseline'}
      : ex)
  }))
};
const ids = [...new Set(forcedPrevious.sessions.flatMap(s=>s.exercises.map(e=>e.exerciseId)))];
const primaryIds = forcedPrevious.sessions.flatMap(s=>s.exercises.filter(e=>e.role==='primary_strength').map(e=>e.exerciseId));
const transitioned = transitionProgramPhase(forcedPrevious,request,'peak',{
  successfulExerciseIds:ids,
  protectedExerciseIds:primaryIds,
  replaceExerciseIds:[], techniqueLimitedExerciseIds:[], fatigueLimitedExerciseIds:[],
  progressionEvidenceByExercise:Object.fromEntries(ids.map(id=>[id,{comparableExposures:4,styleExposures:4,rirCoverage:1,e1rmSamples:4,successful:true}])),
  nextBlockWeeks:4
});
assert.equal(transitioned.program.audit.result,'pass','M192 target block must pass audit');

const reviewed = transitioned.program.sessions.flatMap(s=>s.exercises).filter(ex=>ex.progressionSelection?.previousStyle);
assert.ok(reviewed.length>0,'retained exercises should carry progression before/after metadata');
for (const ex of reviewed) {
  assert.equal(typeof ex.progressionSelection.changed,'boolean',`${ex.name} must carry an engine-owned changed flag`);
  assert.equal(ex.progressionSelection.changed,ex.progressionStyle!==ex.progressionSelection.previousStyle,`${ex.name} changed flag must match the actual before/after styles`);
}
const changed = reviewed.filter(ex=>ex.progressionSelection.changed);
assert.ok(changed.length>0,'peak transition should include at least one real method change from the forced double baseline');
assert.ok(changed.some(ex=>ex.role==='primary_strength'),'a retained primary lift should expose its changed progression method');
assert.ok(transitioned.continuity.progressionMethodChanges.length>0,'continuity explainability should still record progression method changes');

const adapter = fs.readFileSync(new URL('../modules/next-engine/app-shell-adapter.js',import.meta.url),'utf8');
assert.match(adapter,/function progressionPlanItem\(/,'shell adapter should centralize progression-plan serialization');
assert.match(adapter,/previousStyle,/,'shell progression plan should carry the previous method');
assert.match(adapter,/changed: previousStyle !== null \? previousStyle !== style : false/,'shell plan should expose a normalized before/after changed flag');

const app = productionSource();
assert.match(app,/data-progression-delta/,'progression UI should expose per-lift change/keep status');
assert.ok(app.includes('Changed: '),'changed methods should be labeled explicitly');
assert.ok(app.includes('Kept: '),'retained methods should be labeled explicitly');
assert.ok(app.includes('changed · '),'adaptive summary should separate changed from kept methods');
assert.ok(!/reselectProgressionStyle|selectProgressionStyle/.test(app),'UI must display transition deltas without rerunning engine policy');

console.log(`PASS M192: ${reviewed.length} retained progression decisions carried before/after metadata and ${changed.length} real method change(s) are now explainable in the UI.`);
