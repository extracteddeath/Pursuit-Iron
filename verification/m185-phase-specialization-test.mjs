import assert from 'node:assert/strict';
import { generateProgram } from '../modules/next-engine/generate.js';
import { normalizeRequest, createMusclePrescriptions } from '../modules/next-engine/prescription.js';

const equipment=['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading={
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500}, cable:{minimum:5,increment:5,maximum:300}, smith:{minimum:5,increment:5,maximum:500}, exerciseOverrides:{}
};
const request={
  athlete:{experience:'intermediate',trainingAgeMonths:36},
  goal:{type:'mixed',musclePriorities:{},liftPriorities:{}},
  schedule:{days:['monday','tuesday','thursday','friday','sunday'].map(day=>({day,minMinutes:60,maxMinutes:90,targetExercises:7}))},
  equipment:{available:equipment,bodyweight:'allow',loading},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
  preferences:{preferredSplit:'ulppl',lockedSplit:'ulppl',avoidedExercises:[]},
  customExercises:[], seed:18501
};
const normalized=normalizeRequest(request);
const phases=['hypertrophy_accumulation','strength_accumulation','intensification','peak'];
function avg(values){ return values.length ? values.reduce((a,b)=>a+b,0)/values.length : 0; }
function summarize(phase){
  const {program}=generateProgram(request,{phase});
  assert.equal(program.audit.result,'pass',`${phase} must pass the engine audit`);
  const exercises=program.sessions.flatMap(s=>s.exercises);
  const strength=exercises.filter(ex=>ex.role==='primary_strength'||ex.role==='secondary_strength');
  const accessories=exercises.filter(ex=>ex.role==='hypertrophy_compound'||ex.role==='hypertrophy_isolation'||ex.role==='specialization');
  const totalSets=exercises.reduce((sum,ex)=>sum+ex.sets,0);
  const accessorySets=accessories.reduce((sum,ex)=>sum+ex.sets,0);
  const strengthSets=strength.reduce((sum,ex)=>sum+ex.sets,0);
  const minutes=program.sessions.reduce((sum,s)=>sum+s.estimatedMinutes,0);
  const strengthRepMid=avg(strength.map(ex=>(ex.prescription.reps[0]+ex.prescription.reps[1])/2));
  const strengthRirMid=avg(strength.map(ex=>(ex.prescription.rir[0]+ex.prescription.rir[1])/2));
  const advancedTechniques=exercises.filter(ex=>ex.advancedTechnique).length;
  const prescriptions=createMusclePrescriptions(normalized,phase);
  const preferredDose=prescriptions.reduce((sum,p)=>sum+p.preferred,0);
  const upperDose=prescriptions.reduce((sum,p)=>sum+p.upper,0);
  return {phase,totalSets,accessorySets,strengthSets,minutes,strengthRepMid,strengthRirMid,advancedTechniques,preferredDose,upperDose,strengthExerciseCount:strength.length};
}
const rows=Object.fromEntries(phases.map(phase=>[phase,summarize(phase)]));
const h=rows.hypertrophy_accumulation, s=rows.strength_accumulation, i=rows.intensification, p=rows.peak;

// Phase identity must change actual programming, not just labels.
assert.ok(h.preferredDose > s.preferredDose,`hypertrophy preferred dose ${h.preferredDose} should exceed strength ${s.preferredDose}`);
assert.ok(s.preferredDose > p.preferredDose,`strength preferred dose ${s.preferredDose} should exceed peak ${p.preferredDose}`);
assert.ok(h.accessorySets > p.accessorySets,`hypertrophy accessory sets ${h.accessorySets} should exceed peak ${p.accessorySets}`);
assert.ok(h.totalSets > p.totalSets,`hypertrophy total sets ${h.totalSets} should exceed peak ${p.totalSets}`);
assert.ok(h.minutes > p.minutes,`hypertrophy weekly minutes ${h.minutes} should exceed peak ${p.minutes}`);

// The strength spine gets progressively heavier/lower-rep through the cycle.
assert.ok(h.strengthExerciseCount >= 3,'powerbuilding hypertrophy block should retain an S/B/D strength spine');
assert.ok(s.strengthExerciseCount >= 3,'strength block should retain an S/B/D strength spine');
assert.ok(p.strengthExerciseCount >= 3,'peak block should retain an S/B/D strength spine');
assert.ok(h.strengthRepMid > s.strengthRepMid,`hypertrophy strength reps ${h.strengthRepMid} should exceed strength ${s.strengthRepMid}`);
assert.ok(s.strengthRepMid >= i.strengthRepMid,`strength reps ${s.strengthRepMid} should not be lower than intensification ${i.strengthRepMid}`);
assert.ok(i.strengthRepMid > p.strengthRepMid,`intensification reps ${i.strengthRepMid} should exceed peak ${p.strengthRepMid}`);
assert.ok(p.strengthRirMid < h.strengthRirMid,`peak strength RIR ${p.strengthRirMid} should be lower than hypertrophy ${h.strengthRirMid}`);

// Peak is a taper, not a renamed strength block: less total work while specific strength practice remains.
assert.ok(p.totalSets < s.totalSets,`peak total sets ${p.totalSets} should taper below strength ${s.totalSets}`);
assert.ok(p.accessorySets < s.accessorySets,`peak accessory sets ${p.accessorySets} should taper below strength ${s.accessorySets}`);
assert.ok(p.minutes < s.minutes,`peak minutes ${p.minutes} should taper below strength ${s.minutes}`);

console.log('M185 phase specialization');
console.table(Object.values(rows));
console.log('PASS M185: hypertrophy -> strength -> intensification -> peak changes dose, accessory volume, strength reps/RIR, and taper behavior.');
