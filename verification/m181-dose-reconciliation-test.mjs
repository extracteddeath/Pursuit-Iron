import assert from 'node:assert/strict';
import { reconcileRecoverableDose } from '../modules/next-engine/dose-reconciliation.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { createMusclePrescriptions, normalizeRequest } from '../modules/next-engine/prescription.js';

const request = normalizeRequest({
  athlete:{experience:'intermediate'},
  goal:{type:'mixed',musclePriorities:{},liftPriorities:{}},
  schedule:{days:[
    {day:'monday',maxMinutes:90,targetExercises:7},
    {day:'wednesday',maxMinutes:90,targetExercises:7},
    {day:'friday',maxMinutes:90,targetExercises:7}
  ]},
  equipment:{available:['machine','cable','dumbbell','bench'],bodyweight:'exclude',loading:{}},
  restrictions:{allowSupersets:true,maxBarbellMovementsPerDay:0},
  preferences:{},customExercises:[],seed:18191
});
const phase='mixed_accumulation';
const map=createExerciseMap(request.customExercises);
const prescriptions=createMusclePrescriptions(request,phase).filter(p=>p.muscle!=='front_delts'&&p.priority!=='maintenance'&&p.upper>0);
const exercise=(exerciseId,name,role,sets)=>({exerciseId,name,role,sets});
const sessions=[
  {id:'m',day:'monday',name:'Upper A',maxMinutes:90,estimatedMinutes:70,intent:'upper',exercises:[
    exercise('machine_press','Machine Chest Press','primary_strength',5),
    exercise('chest_supported_row','Chest-Supported Row','hypertrophy_compound',8),
    exercise('preacher_curl','Preacher Curl','hypertrophy_isolation',8),
    exercise('rear_delt_fly','Reverse Pec Deck','hypertrophy_isolation',8)
  ]},
  {id:'w',day:'wednesday',name:'Pull',maxMinutes:90,estimatedMinutes:70,intent:'pull',exercises:[
    exercise('neutral_pulldown','Neutral-Grip Pulldown','hypertrophy_compound',8),
    exercise('cable_curl','Cable Curl','hypertrophy_isolation',8),
    exercise('rear_delt_fly','Reverse Pec Deck','hypertrophy_isolation',8)
  ]},
  {id:'f',day:'friday',name:'Lower',maxMinutes:90,estimatedMinutes:45,intent:'lower',exercises:[]}
];
const snap=s=>{
  const fractional=Object.fromEntries(prescriptions.map(p=>[p.muscle,0]));
  const direct=Object.fromEntries(prescriptions.map(p=>[p.muscle,0]));
  for(const session of s)for(const ex of session.exercises){const def=map.get(ex.exerciseId);for(const [m,c] of Object.entries(def?.muscles??{})){if(!(m in fractional))continue;fractional[m]+=c.credit*ex.sets;if(c.credit>=.999)direct[m]+=ex.sets;}}
  return {fractional,direct};
};
const ceiling=p=>p.upper+Math.max(1,p.upper*.08);
const overflow=snap=>prescriptions.reduce((n,p)=>n+Math.max(0,snap.fractional[p.muscle]-ceiling(p)),0);
const before=snap(sessions);
assert.ok(overflow(before)>3,'fixture must begin materially over recoverable dose');
assert.equal(before.fractional.calves,0,'fixture must include an existing calf shortfall');
const first=reconcileRecoverableDose(sessions,request,phase);
const second=reconcileRecoverableDose(sessions,request,phase);
const after=snap(first.sessions);
assert.ok(first.adjustments.length>0,'reconciliation must trim optional work');
assert.ok(overflow(after)<overflow(before),'reconciliation must reduce overflow');
assert.equal(JSON.stringify(first),JSON.stringify(second),'reconciliation must be deterministic');
assert.equal(first.sessions[0].exercises[0].sets,5,'strength-specific work must not be trimmed');
for(const session of first.sessions)for(const ex of session.exercises)assert.ok(ex.sets>=2,`${ex.name} fell below two sets`);
for(const p of prescriptions){
  const totalFloor=before.fractional[p.muscle]>=p.preferred?p.preferred:before.fractional[p.muscle]>=p.minimum?p.minimum:before.fractional[p.muscle];
  assert.ok(after.fractional[p.muscle]+.001>=totalFloor,`${p.muscle} total-dose floor worsened`);
  const directFloor=before.direct[p.muscle]>=(p.directPreferred??0)?(p.directPreferred??0):before.direct[p.muscle]>=(p.directMinimum??0)?(p.directMinimum??0):before.direct[p.muscle];
  assert.ok(after.direct[p.muscle]+.001>=directFloor,`${p.muscle} direct-dose floor worsened`);
}
assert.equal(after.fractional.calves,0,'unrelated shortfall must not worsen');
console.log(`PASS M181 dose reconciliation: ${first.adjustments.length} trims, overflow ${overflow(before).toFixed(1)} -> ${overflow(after).toFixed(1)}.`);
