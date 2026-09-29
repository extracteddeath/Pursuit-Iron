import assert from 'node:assert/strict';
import { allocateTraining } from '../modules/next-engine/allocator.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { createMusclePrescriptions, createStrengthClaims, normalizeRequest } from '../modules/next-engine/prescription.js';
import { realizeSessions, realizeStrengthAnchors, estimateSessionMinutes } from '../modules/next-engine/realizer.js';
import { solveTopology } from '../modules/next-engine/topology.js';

const request=normalizeRequest({
  athlete:{experience:'intermediate'}, goal:{type:'strength',musclePriorities:{},liftPriorities:{bench_press:'high',back_squat:'high',deadlift:'high'}},
  schedule:{days:[{day:'monday',minMinutes:60,maxMinutes:90,targetExercises:7},{day:'wednesday',minMinutes:60,maxMinutes:90,targetExercises:7},{day:'friday',minMinutes:60,maxMinutes:90,targetExercises:7}]},
  equipment:{available:['barbell','rack','bench','dumbbell','cable','machine','leg_press','pullup_bar','bodyweight'],bodyweight:'allow',loading:{unit:'lb',barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,60,70,80,90,100]},machine:{minimum:5,increment:5,maximum:500},cable:{minimum:5,increment:5,maximum:300},smith:{minimum:5,increment:5,maximum:500},exerciseOverrides:{}}},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true}, preferences:{preferredSplit:'strength_fb',lockedSplit:'strength_fb',avoidedExercises:[]}, customExercises:[], seed:172
});
const phase='strength_accumulation';
const muscles=createMusclePrescriptions(request,phase); const claims=createStrengthClaims(request,phase);
const projected=allocateTraining(request,muscles,claims,phase);
const provisionalTopology=solveTopology(request,projected.allocations);
const baseline=realizeStrengthAnchors(provisionalTopology.sessions,request,phase);
assert.ok(baseline.expectedCount>0); assert.equal(baseline.realizedCount,baseline.expectedCount); assert.equal(baseline.missingAllocationIds.length,0);
assert.ok(baseline.estimatedMinutes>0);
const actual=allocateTraining(request,muscles,claims,phase,{strengthBaseline:baseline});
assert.equal(actual.metrics.strengthBaselineSource,'realized_anchors');
assert.equal(actual.metrics.strengthMinutes,Math.round(baseline.estimatedMinutes*10)/10);
for(const [muscle,credit] of Object.entries(baseline.fractional)) assert.equal(actual.projectedFromStrength[muscle],credit);
const differs=Object.keys({...projected.projectedFromStrength,...actual.projectedFromStrength}).some(m=>Math.abs((projected.projectedFromStrength[m]??0)-(actual.projectedFromStrength[m]??0))>.05);
assert.equal(differs,true,'actual anchor ledger should replace at least one generic lift projection');
const topology=solveTopology(request,actual.allocations,{seed:provisionalTopology.seed});
assert.equal(topology.family,provisionalTopology.family);
assert.deepEqual(topology.sessions.map(s=>[s.id,s.day,s.intent]),provisionalTopology.sessions.map(s=>[s.id,s.day,s.intent]));
const final=realizeSessions(topology.sessions,request,actual.targetDose,actual.directTargetDose,phase,{strengthAnchors:baseline.anchors});
const map=createExerciseMap(request.customExercises);
const finalFractional={}; let finalStrengthMinutes=0; let finalStrengthCount=0;
for(const session of final){
  const strengthExercises=session.exercises.filter(ex=>ex.role==='primary_strength'||ex.role==='secondary_strength');
  if(strengthExercises.length) finalStrengthMinutes+=estimateSessionMinutes(strengthExercises);
  for(const ex of strengthExercises){
    const def=map.get(ex.exerciseId); assert.ok(def); finalStrengthCount++;
    for(const [muscle,c] of Object.entries(def.muscles)) finalFractional[muscle]=(finalFractional[muscle]??0)+c.credit*ex.sets;
    const pins=Object.values(baseline.anchors).filter(pin=>pin.sessionId===session.id&&pin.exerciseId===ex.exerciseId&&pin.sets===ex.sets);
    assert.ok(pins.length>=1,`final strength exercise ${ex.exerciseId} was not pinned from preview`);
  }
}
assert.equal(finalStrengthCount,baseline.realizedCount);
for(const muscle of new Set([...Object.keys(finalFractional),...Object.keys(baseline.fractional)])) assert.ok(Math.abs((finalFractional[muscle]??0)-(baseline.fractional[muscle]??0))<1e-9,`strength credit mismatch for ${muscle}`);
assert.equal(finalStrengthMinutes,baseline.estimatedMinutes);
console.log('M172 actual strength baseline tests OK.');
