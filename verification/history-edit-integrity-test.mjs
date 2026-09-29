import assert from 'node:assert/strict';
import { normalizeEditedHistoryEntry, perfAfterHistoryReplace, lifterModelKey } from '../modules/App.js';
import { nextWorkoutSuggestionForShell } from '../modules/next-engine/workout-history-adapter.js';

const original={
 id:'h1',date:1000,programId:'p1',programName:'Plan',dayId:'d1',dayLabel:'Lower',weekIndex:1,unit:'lb',durationMin:55,
 perf:{'back-squat':{weight:207.5,reps:3,date:1000,sets:[
  {w:200,r:3,tr:2},{w:205,r:3,tr:2},{w:210,r:2,tr:2},{w:215,r:2,rir:0,tr:2}
 ]}},volume:1960,setsDone:4
};
const draft=structuredClone(original);
draft.programId='evil'; draft.dayId='other'; draft.weekIndex=99; // structural provenance must stay locked
// Correct the last set and a RIR value. The editor should preserve target provenance.
draft.perf['back-squat'].sets[3].w='185';
draft.perf['back-squat'].sets[3].r='5';
draft.perf['back-squat'].sets[3].rir='2';
draft.durationMin='60';
const corrected=normalizeEditedHistoryEntry(original,draft);
assert.equal(corrected.programId,'p1');
assert.equal(corrected.dayId,'d1');
assert.equal(corrected.weekIndex,1);
assert.equal(corrected.durationMin,60);
assert.equal(corrected.perf['back-squat'].sets[3].w,185);
assert.equal(corrected.perf['back-squat'].sets[3].r,5);
assert.equal(corrected.perf['back-squat'].sets[3].rir,2);
assert.equal(corrected.perf['back-squat'].sets[3].tr,2);
assert.equal(corrected.setsDone,4);

const mirror=perfAfterHistoryReplace([original],{'back-squat':original.perf['back-squat']},'h1',corrected);
assert.deepEqual(mirror['back-squat'].sets,corrected.perf['back-squat'].sets);

const rirOnly=structuredClone(original);
rirOnly.perf['back-squat'].sets[0].rir=4;
const rirCorrected=normalizeEditedHistoryEntry(original,rirOnly);
assert.notEqual(lifterModelKey([original]),lifterModelKey([rirCorrected]),'RIR-only edits must invalidate the lifter-model cache');

const program={
 id:'p1',engineSource:'pursuit-next',engineSourceVersion:'0.62.5',config:{unit:'lb'},
 days:[{id:'d1',label:'Lower',exercises:['back-squat']}],
 nextWeekPrescriptions:{'d1:0':{1:{sets:3,reps:'5-8',rir:'2',rest:180,progressionStyle:'double',role:'hypertrophy_compound'}}},
 nextEngine:{
  request:{goal:{type:'hypertrophy'},schedule:{days:['monday']},equipment:{available:['barbell','rack'],loading:{unit:'lb',barbell:{barWeight:45,platePairs:[{weight:2.5,pairs:4},{weight:5,pairs:4},{weight:10,pairs:4},{weight:25,pairs:4},{weight:45,pairs:10}]},dumbbells:{availablePerHand:[5]},machine:{minimum:5,increment:5,maximum:500},cable:{minimum:5,increment:5,maximum:300},smith:{minimum:5,increment:5,maximum:500},exerciseOverrides:{}}}},
  program:{sessions:[{id:'s1',name:'Lower',intent:'Lower',exercises:[{exerciseId:'back_squat',name:'Back Squat',role:'hypertrophy_compound',sets:3,prescription:{reps:[5,8],rir:[2,2],restSeconds:180},progression:'Double progression',progressionStyle:'double'}]}]},
  cycleState:{phase:'hypertrophy_accumulation',phaseLabel:'Hypertrophy',workoutsInPhase:0,minimumWorkouts:4,reviewAfterWorkouts:6,status:'building'}
 }
};
const legacy=[{id:'back-squat',name:'Back Squat'}];
const before=nextWorkoutSuggestionForShell(program,[original],legacy,program.days[0],0,1);
assert.equal(before?.action,'decrease_load');
const after=nextWorkoutSuggestionForShell(program,[corrected],legacy,program.days[0],0,1);
assert.ok(after);
assert.notDeepEqual({action:after.action,weight:after.weight},{action:before.action,weight:before.weight},'corrected history must materially change the engine decision when evidence changes');
console.log('PASS history edit integrity: raw sets are editable, ownership is locked, mirrors/cache refresh, and Pursuit Engine re-reads corrected evidence.');
