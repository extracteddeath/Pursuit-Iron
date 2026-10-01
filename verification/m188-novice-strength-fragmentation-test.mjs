import assert from 'node:assert/strict';
import { generateProgram } from '../modules/next-engine/generate.js';
import { evaluateProgramCoachQuality } from './coach-quality-oracle.mjs';

const loading={
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500},
  cable:{minimum:5,increment:5,maximum:300},
  smith:{minimum:5,increment:5,maximum:500},exerciseOverrides:{}
};
const req={
  athlete:{experience:'novice'},
  goal:{type:'strength',musclePriorities:{},liftPriorities:{}},
  schedule:{days:['monday','tuesday','wednesday'].map(day=>({day,maxMinutes:60}))},
  equipment:{available:['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'],bodyweight:'allow',loading},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
  preferences:{preferredSplit:'full_body',lockedSplit:'full_body',avoidedExercises:[]},
  customExercises:[],seed:188006
};
const generated=generateProgram(req);
const quality=evaluateProgramCoachQuality(generated.program,req,{audit:generated.program.audit});
for(const session of generated.program.sessions){
  console.log(`${session.day} ${session.name} ${session.estimatedMinutes}/${session.maxMinutes}m`);
  for(const ex of session.exercises) console.log(`  ${ex.sets}x ${ex.name} [${ex.role}]`);
}
for(const finding of [...quality.hardFailures,...quality.warnings]) console.log(`${finding.code}: ${finding.detail}`);
assert.equal(quality.result,'pass',`novice strength full-body remains fragmented: ${[...quality.hardFailures,...quality.warnings].map(x=>x.code).join(', ')}`);
console.log('PASS M188 novice-strength fragmentation regression.');
