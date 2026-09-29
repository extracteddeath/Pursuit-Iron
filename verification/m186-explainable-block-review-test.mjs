import assert from 'node:assert/strict';
import { generateProgram } from '../modules/next-engine/generate.js';
import { transitionProgramPhase } from '../modules/next-engine/phase-transition.js';
import { buildBlockReviewSummary } from '../modules/next-engine/explainability.js';

const equipment=['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading={
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500}, cable:{minimum:5,increment:5,maximum:300}, smith:{minimum:5,increment:5,maximum:500}, exerciseOverrides:{}
};
const request={
  athlete:{experience:'intermediate',trainingAgeMonths:36},
  goal:{type:'mixed',musclePriorities:{chest:'high',back:'high'},liftPriorities:{bench_press:'high',back_squat:'high',deadlift:'high'}},
  schedule:{days:['monday','tuesday','thursday','friday','sunday'].map(day=>({day,minMinutes:60,maxMinutes:90,targetExercises:7}))},
  equipment:{available:equipment,bodyweight:'allow',loading},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
  preferences:{preferredSplit:'ulppl',lockedSplit:'ulppl',avoidedExercises:[]},
  customExercises:[], seed:18601
};

const previous=generateProgram(request,{phase:'hypertrophy_accumulation'}).program;
assert.equal(previous.audit.result,'pass','M186 baseline block must pass the production audit');
const priorIds=[...new Set(previous.sessions.flatMap(session=>session.exercises.map(exercise=>exercise.exerciseId)))];
const evidence={
  successfulExerciseIds:priorIds.slice(0,Math.max(6,Math.floor(priorIds.length*.7))),
  protectedExerciseIds:priorIds.slice(0,3),
  replaceExerciseIds:[]
};
const transitioned=transitionProgramPhase(previous,request,'strength_accumulation',evidence);
const next=transitioned.program;
assert.equal(next.audit.result,'pass','M186 target block must still pass the production audit');
assert.ok(next.blockReview,'phase transition must publish a Block Review summary');
assert.deepEqual(next.blockReview,next.explainability.blockReview,'Block Review must use the same explainability contract exposed to the UI');
assert.equal(next.blockReview.schemaVersion,1);
assert.equal(next.blockReview.status,'changed');
assert.equal(next.blockReview.fromPhase,'hypertrophy_accumulation');
assert.equal(next.blockReview.toPhase,'strength_accumulation');
assert.ok(next.blockReview.headline.length>10);
assert.ok(next.blockReview.summary.length>10);
assert.ok(next.blockReview.changes.some(change=>change.key==='phase'),'review must make the phase change explicit');
assert.ok(next.blockReview.changes.some(change=>change.key==='weekly-work'),'review must quantify the weekly work change');
assert.ok(next.blockReview.changes.some(change=>change.key==='exercise-continuity'),'review must quantify exercise continuity');
assert.ok(next.blockReview.reasons.some(reason=>reason.title==='Why the block changed'),'review must explain why the new block exists');
assert.ok(next.blockReview.reasons.some(reason=>reason.title==='Why exercises changed'),'review must explain continuity/replacement decisions');
assert.ok(next.blockReview.changes.every(change=>Array.isArray(change.reasonCodes) && change.reasonCodes.length),'every visible change needs machine-readable provenance');
assert.ok(next.blockReview.changes.every(change=>!String(change.detail).match(/undefined|null|NaN/)),'user-facing review text cannot leak missing values');
assert.ok(next.blockReview.reasons.every(reason=>!String(reason.detail).match(/undefined|null|NaN/)),'user-facing rationale cannot leak missing values');
assert.ok(transitioned.continuity.capacityAdjustedRetentionRate>=0 && transitioned.continuity.capacityAdjustedRetentionRate<=1,'continuity result must remain bounded');

// A review of an unchanged prescription should not manufacture a change narrative.
const steady=buildBlockReviewSummary(next,next,{});
assert.equal(steady.status,'steady');
assert.equal(steady.headline,'The prescription is staying on course');
assert.equal(steady.changes.filter(change=>change.direction!=='steady').length,0);

// The summary layer must remain deterministic and presentation-only.
assert.deepEqual(buildBlockReviewSummary(previous,next,{continuity:transitioned.continuity,transitionReason:'Scheduled cycle progression.',reasonCodes:['test:scheduled']}),buildBlockReviewSummary(previous,next,{continuity:transitioned.continuity,transitionReason:'Scheduled cycle progression.',reasonCodes:['test:scheduled']}));
assert.equal(previous.audit.result,'pass','building the review must not mutate the prior program');
assert.equal(next.audit.result,'pass','building the review must not mutate the target program');

console.log('PASS M186 explainable Block Review: audited phase transition, quantified changes, continuity rationale, provenance, deterministic steady-state behavior.');
