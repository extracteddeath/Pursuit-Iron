import assert from 'node:assert/strict';
import { deriveAthleteResponse } from '../modules/next-engine/athlete-response.js';
const exercise = { exerciseId:'db_curl', sets:3, prescriptionSource:'logged_snapshot' };
const workout = (i,dayId='a',reasonCode='progression_success') => ({ programId:'p', historyId:`w${i}`, dayId,
    completedAt:new Date(Date.UTC(2026,8,i+1)).toISOString(), session:{ exercises:[exercise] },
    performedSets:Array.from({length:3},(_,setIndex)=>({ exerciseId:'db_curl',setIndex,load:25+i,reps:12,rir:2 })),
    progression:[{exerciseId:'db_curl',outcome:reasonCode==='progression_success'?'success':'failure',reasonCode}] });
const options={programId:'p',dayIds:['a','b'],recovery:{status:'normal'}};
const input=Array.from({length:8},(_,i)=>workout(i,i%2?'a':'b'));
const before=structuredClone(input), memory=deriveAthleteResponse(input,options);
assert.equal(memory.confidence,'high');assert.equal(memory.action,'maintain');
assert.equal(memory.achievedDoseByMuscle.biceps,24);
assert.equal(memory.bySlot['a:db_curl'].comparableExposures,4);
assert(memory.bySlot['a:db_curl'].progressionVelocityPerWeek>0);
assert.deepEqual(memory,deriveAthleteResponse([...input].reverse(),options));
assert.deepEqual(memory,deriveAthleteResponse(JSON.parse(JSON.stringify(input)),options));
assert.deepEqual(input,before);
const unrelated={...workout(9),programId:'other'};
assert.equal(deriveAthleteResponse([unrelated],options).comparableExposures,0);
assert.equal(deriveAthleteResponse([{...workout(9),dayId:'other'}],options).comparableExposures,0);
assert.equal(deriveAthleteResponse([...input,...input],options).comparableExposures,8);
for(const outcome of ['incomplete','interrupted','context_limited','non_comparable','unobserved']) {
    const rows=input.map(w=>({...w,progression:[{...w.progression[0],outcome}]}));
    const result=deriveAthleteResponse(rows,options);
    assert.equal(result.comparableExposures,0);assert.equal(result.capacityScale,1);
    assert.equal(result.achievedDoseByMuscle.biceps,24,'actual completed dose remains an independent fact');
}
assert.equal(deriveAthleteResponse([workout(1)],options).bySlot['a:db_curl'].progressionVelocityPerWeek,null);
const failing=Array.from({length:6},(_,i)=>workout(i,i%2?'a':'b','rep_floor_miss'));
const fatigue=deriveAthleteResponse(failing,options);
assert.equal(fatigue.capacityScale,.95);assert.equal(fatigue.action,'reduce_capacity');
assert.equal(deriveAthleteResponse(failing.map(w=>({...w,dayId:'a'})),options).capacityScale,1,'one slot cannot create a global recovery conclusion');
assert.equal(deriveAthleteResponse(failing.map(w=>({...w,session:{exercises:[{...exercise,prescriptionSource:'current_projection'}]}})),options).capacityScale,1);
assert.equal(deriveAthleteResponse(input,{...options,previousCapacityScale:.8}).capacityScale,.85);
assert.equal(deriveAthleteResponse(failing,{...options,previousCapacityScale:.6}).capacityScale,.6);
const missingEffort=input.map(w=>({...w,performedSets:w.performedSets.map(s=>({...s,rir:null}))}));
assert.equal(deriveAthleteResponse(missingEffort,options).observedRirCoverage,0);
assert.notEqual(deriveAthleteResponse(missingEffort,options).confidence,'high');
console.log('PASS M231: owned day/program dose, separate progression velocity, sparse/limited restraint, replay order/deduplication, bounded capacity response and missing-effort confidence.');
