import assert from 'node:assert/strict';
import { nextWorkoutSuggestionFromPerformedShell } from '../modules/next-engine/workout-history-adapter.js';

const program={
 id:'p1', engineSource:'pursuit-next', config:{unit:'lb'},
 days:[{id:'d1',label:'Lower',exercises:['back-squat']}],
 nextWeekPrescriptions:{'d1:0':{1:{sets:3,reps:'5-8',rir:'2-2',rest:180,progressionStyle:'double',role:'hypertrophy_compound'}}},
 nextEngine:{
  request:{goal:{type:'hypertrophy'},schedule:{days:['monday']},equipment:{available:['barbell','rack'],loading:{unit:'lb'}}},
  program:{sessions:[{id:'s1',name:'Lower',intent:'Lower',exercises:[{exerciseId:'back_squat',name:'Back Squat',role:'hypertrophy_compound',sets:3,prescription:{reps:[5,8],rir:[2,2],restSeconds:180},progression:'Double progression',progressionStyle:'double'}]}]},
  cycleState:{phase:'hypertrophy_accumulation',phaseLabel:'Hypertrophy',workoutsInPhase:0,minimumWorkouts:4,reviewAfterWorkouts:6,status:'building'}
 }
};
const legacy=[{id:'back-squat',name:'Back Squat'}];
const perf={'back-squat':{weight:215,reps:2,sets:[
 {w:200,r:3,rir:null,tr:2,done:true},
 {w:205,r:3,rir:null,tr:2,done:true},
 {w:210,r:2,rir:null,tr:2,done:true},
 {w:215,r:2,rir:0,tr:2,done:true}
]}};
const s=nextWorkoutSuggestionFromPerformedShell(program,legacy,program.days[0],0,1,perf,'lb');
assert.ok(s);
assert.equal(s.action,'decrease_load');
assert.equal(s.dir,'down');
assert.equal(s.weight,185);
assert.equal(s.target,5);
assert.match(s.reason,/215×2 at 0 RIR/);
console.log('PASS shell progression safety: null RIR stays missing; exact user case -> 185 lb/down.');
