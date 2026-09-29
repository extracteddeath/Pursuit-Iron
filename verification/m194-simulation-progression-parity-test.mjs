import assert from 'node:assert/strict';
import fs from 'node:fs';
import { generateProgram } from '../modules/next-engine/generate.js';
import { blocksForCycleTemplate, prescriptionForSimulationWeek, runPowerbuildingSimulation } from '../modules/next-engine/simulation.js';

const equipment=['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const loading={
  unit:'lb',
  barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},
  dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},
  machine:{minimum:5,increment:5,maximum:500}, cable:{minimum:5,increment:5,maximum:300}, smith:{minimum:5,increment:5,maximum:500}, exerciseOverrides:{}
};
const request={
  athlete:{experience:'advanced',trainingAgeMonths:72},
  goal:{type:'strength',musclePriorities:{},liftPriorities:{bench_press:'high',back_squat:'high',deadlift:'high'}},
  schedule:{days:['monday','wednesday','friday','sunday'].map(day=>({day,minMinutes:60,maxMinutes:90,targetExercises:6}))},
  equipment:{available:equipment,bodyweight:'allow',loading},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},
  preferences:{preferredSplit:'upper_lower',lockedSplit:'upper_lower',avoidedExercises:[],progressionStyle:'auto'},
  customExercises:[],seed:19401
};
const strengthPeak=blocksForCycleTemplate('strength_peak');

// The torture simulator must now make the same block-length decision as the real M193 cycle runtime.
const adaptive=runPowerbuildingSimulation({request,blocks:strengthPeak,adaptBetweenBlocks:true,responseProfile:'mixed'});
assert.equal(adaptive.blocks.length,3,'strength-peak simulation should contain all three blocks');
assert.deepEqual(adaptive.blocks.map(b=>b.spec.weeks),[4,4,2],'simulation should retain the real cycle durations');
const intensify=adaptive.blocks[1];
assert.equal(intensify.program.phase,'intensification');
const intensifyPrimary=intensify.program.sessions.flatMap(s=>s.exercises).filter(ex=>ex.role==='primary_strength');
assert.ok(intensifyPrimary.length>0,'intensification simulation needs primary strength work');
for (const ex of intensifyPrimary)
  assert.notEqual(ex.progressionStyle,'wave',`4-week simulated intensification must not choose a 5+ week wave for ${ex.name}`);

// Synthetic workout history must be explicit enough to exercise M189's adaptive selector rather than
// relying on progression-style defaults. Every trained lift should have comparable exposure data.
const firstEvidence=adaptive.blocks[0].response.progressionEvidenceByExercise;
assert.ok(firstEvidence && Object.keys(firstEvidence).length>0,'simulated block response must expose per-exercise progression evidence');
for (const [id,row] of Object.entries(firstEvidence)) {
  assert.ok(row.comparableExposures>0,`${id} should have comparable synthetic exposures`);
  assert.equal(row.styleExposures,row.comparableExposures,`${id} style exposure count should match comparable block exposures`);
  assert.equal(row.rirCoverage,1,`${id} synthetic RIR coverage should reflect the actual performed-set generator`);
  assert.ok(Number.isFinite(row.e1rmSamples),`${id} should report explicit e1RM sample count`);
}
const retainedAdaptive=intensify.program.sessions.flatMap(s=>s.exercises).filter(ex=>ex.progressionSelection?.previousStyle);
assert.ok(retainedAdaptive.length>0,'adaptive simulated transition should retain before/after progression metadata');
assert.ok(retainedAdaptive.some(ex=>['adaptive','adaptive_hold','manual'].includes(ex.progressionSelection.source)),'retained simulated lifts should be re-reviewed through the adaptive selector');

// Adapt Between Blocks OFF uses the same selector contract now; it must not merely change the instruction
// string while leaving a stale progressionStyle from the previous block.
const locked=runPowerbuildingSimulation({request:{...request,seed:19402},blocks:strengthPeak,adaptBetweenBlocks:false,responseProfile:'steady'});
const lockedIntensify=locked.blocks[1];
const lockedReviewed=lockedIntensify.program.sessions.flatMap(s=>s.exercises).filter(ex=>ex.progressionSelection?.previousStyle);
assert.ok(lockedReviewed.length>0,'static simulated retarget should carry real progression review metadata');
for (const ex of lockedReviewed) {
  assert.equal(ex.progressionSelection.changed,ex.progressionSelection.previousStyle!==ex.progressionStyle,`${ex.name} static changed flag must match the actual styles`);
  if (ex.role==='primary_strength') assert.notEqual(ex.progressionStyle,'wave',`static 4-week intensification must also reject a too-long wave for ${ex.name}`);
}

// Manual method ownership must survive BOTH block generation and the weekly prescription function used
// by the actual shell. Previously a manual double choice could silently turn into wave/e1RM mid-block.
const manualRequest={...request,seed:19403,preferences:{...request.preferences,progressionStyle:'double'}};
const manualProgram=generateProgram(manualRequest,{phase:'strength_accumulation',blockWeeks:6,progressionStyle:'double'}).program;
assert.equal(manualProgram.audit.result,'pass','manual progression parity fixture must generate');
const manualPrimarySession=manualProgram.sessions.find(s=>s.exercises.some(ex=>ex.role==='primary_strength'));
assert.ok(manualPrimarySession,'manual fixture should contain a primary strength session');
for (let week=1;week<=6;week++) {
  const weekly=prescriptionForSimulationWeek(manualPrimarySession,'strength_accumulation',week,6);
  for (const ex of weekly.exercises.filter(ex=>ex.role==='primary_strength'))
    assert.equal(ex.progressionStyle,'double',`manual double must remain double in week ${week} for ${ex.name}`);
}
const manualSim=runPowerbuildingSimulation({request:manualRequest,blocks:strengthPeak,adaptBetweenBlocks:false,responseProfile:'steady'});
for (const [blockIndex,block] of manualSim.blocks.entries())
  for (const ex of block.program.sessions.flatMap(s=>s.exercises)) {
    assert.equal(ex.progressionStyle,'double',`manual double must own simulated block ${blockIndex+1}: ${ex.name}`);
    assert.equal(ex.progressionSelection?.source,'manual',`manual source must remain explicit in simulated block ${blockIndex+1}: ${ex.name}`);
  }

// Auto's established within-accumulation schedule remains intentional and separately tested. M194 only
// prevents that schedule from overriding explicit manual ownership.
const autoProgram=generateProgram({...request,seed:19404},{phase:'strength_accumulation',blockWeeks:6}).program;
const autoPrimarySession=autoProgram.sessions.find(s=>s.exercises.some(ex=>ex.role==='primary_strength' && ex.progressionStyle!=='linear'));
assert.ok(autoPrimarySession,'auto fixture should contain eligible primary strength work');
const autoWeek3=prescriptionForSimulationWeek(autoPrimarySession,'strength_accumulation',3,6);
assert.ok(autoWeek3.exercises.filter(ex=>ex.role==='primary_strength').some(ex=>ex.progressionStyle==='wave'),'Auto should retain the tested within-block strength schedule when it was not manually overridden');

const simulationSource=fs.readFileSync(new URL('../modules/next-engine/simulation.js',import.meta.url),'utf8');
assert.match(simulationSource,/nextBlockWeeks: spec\.weeks/,'adaptive simulation transitions must receive the actual target block duration');
assert.match(simulationSource,/progressionEvidenceByExercise: priorResponse\?\.progressionEvidenceByExercise/,'adaptive simulator must feed its per-lift evidence into phase transition');
assert.match(simulationSource,/retargetProgramWithoutStructuralAdaptation\(priorProgram, normalized, spec\.phase, spec\.weeks\)/,'static simulation transitions must receive target block duration');

console.log(`PASS M194: simulator progression now mirrors production block context, carries per-lift evidence, preserves manual methods through weekly prescriptions, and keeps Auto's tested within-block schedule.`);
