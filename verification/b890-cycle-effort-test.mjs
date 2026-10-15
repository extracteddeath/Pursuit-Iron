import assert from 'node:assert/strict';
import { computeCell, mergeStandaloneIntoGeneratedCycle } from '../modules/App.js';
import { currentProgram, generatedCycle } from './m215-cycle-duration-fixture.mjs';
import { effortForSimulationWeek, prescriptionForSimulationWeek } from '../modules/next-engine/simulation.js';
import { rirForPhase } from '../modules/next-engine/realizer-prescriptions.js';
import { phasePolicyFor } from '../modules/next-engine/phase-policy.js';
const before = structuredClone(currentProgram);
const p = mergeStandaloneIntoGeneratedCycle(currentProgram, structuredClone(generatedCycle)).currentProgram;
const day = p.days[0], cell = (program,slot,week) => computeCell(program,day,day.exercises[slot],slot,week);
assert.equal(cell(p,0,1).rir,'2-3');
assert.equal(cell(p,0,3).rir,'1-2');
assert.equal(cell(p,0,10).rir,'1-2');
assert.equal(cell(p,1,1).rir,'1-3');
assert.equal(cell(p,1,3).rir,'0-2');
assert.equal(cell(p,0,11).rir,'3-4','deload adds recovery reserve to the parent block base, like generated shell cells');
assert.equal(cell(p,1,11).rir,'2-4');
const recoveryEx={exerciseId:'bb-bench',role:'hypertrophy_compound',sets:3,prescription:{reps:[6,10],rir:rirForPhase('hypertrophy_compound',phasePolicyFor('hypertrophy_accumulation')),restSeconds:120}};
const generatedDeload=prescriptionForSimulationWeek({exercises:[recoveryEx]},'recovery',1,1).exercises[0].prescription.rir;
assert.equal(cell(p,0,11).rir,generatedDeload.join('-'),'converted deload matches actual generated-shell materialization');
for (const rir of [0,2,'2-3',[1,2]]) {
 const edited={...p,overrides:{[day.id+':0']:{rir}}};
 assert.equal(cell(edited,0,1).rir,cell(edited,0,10).rir,'explicit target stays fixed');
 assert.equal(cell(edited,0,11).rir,cell(edited,0,1).rir,'explicit target wins on deload too');
}
const manual={...p,config:{...p.config,progression:'manual'}};
assert.equal(cell(manual,0,1).rir,'2');assert.equal(cell(manual,0,10).rir,'2');
assert.equal(cell({...p,progStyle:{'bb-bench':'manual'}},0,1).rir,'2');
assert.equal(cell({...p,progStyle:{[day.id+':0']:'manual'}},0,1).rir,'2');
assert.equal(cell(currentProgram,0,1).rir,'2','standalone defaults are unchanged');
assert.equal(cell(currentProgram,0,10).rir,'2');
const novice={...p,config:{...p.config,experience:'novice'}};
assert.equal(cell(novice,0,10).rir,'2-3','novice compound floor survives conversion');
for(let week=1;week<=11;week++)for(let slot=0;slot<day.exercises.length;slot++){
 const a=cell(p,slot,week),b=cell(currentProgram,slot,week);
 for(const key of ['sets','reps','rest','tech'])assert.deepEqual(a[key],b[key],key+' retained');
}
for (const phase of ['hypertrophy_accumulation','strength_accumulation','mixed_accumulation','foundation','peak','recovery'])
for (const role of ['primary_strength','hypertrophy_compound','hypertrophy_isolation']) {
 const base=rirForPhase(role,phasePolicyFor(phase));
 for(let week=1;week<=6;week++){
  const ex={exerciseId:'bb-bench',role,sets:3,prescription:{reps:[6,10],rir:base,restSeconds:120}};
  const generated=prescriptionForSimulationWeek({exercises:[ex]},phase,week,6).exercises[0].prescription.rir;
  assert.deepEqual(effortForSimulationWeek(base,role,phase,week,6),generated,'custom effort matches generated weekly policy');
 }
}
assert.deepEqual(currentProgram,before,'reads and conversion leave source unchanged');
console.log('PASS B890 custom cycle effort: weekly canonical targets, deload, authored/manual targets, novice floor and unchanged dose.');
