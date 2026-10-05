import assert from 'node:assert/strict';
import { normalizeLoadingInventory, nextAvailableLoad, availableLoadAtOrBelow, DEFAULT_LOADING_INVENTORY } from '../modules/next-engine/loading.js';
import { estimate1RM, bestEstimated1RM } from '../modules/next-engine/history.js';
import { buildRuntimeSetTargets, reconcilePendingRepTargets } from '../modules/next-engine/workout-runtime.js';

// Persisted/imported equipment must not throw, and an explicit empty list is not a default gym.
for (const input of [{barbell:{platePairs:[null,{}, {weight:5,pairs:1}]}}, {dumbbells:{availablePerHand:'5,10'}}, {machine:{availableLoads:'5,10'}}])
    assert.doesNotThrow(()=>normalizeLoadingInventory(input));
for (const loads of [[], '5,10', [null, '10', -5]]) {
    const inventory={dumbbells:{availablePerHand:loads}, machine:{availableLoads:loads}, exerciseOverrides:{barbell_bench:{availableLoads:loads}}};
    assert.equal(nextAvailableLoad('db-bench',10,inventory,['dumbbell']),null);
    assert.equal(nextAvailableLoad('machine_press',10,inventory,['machine']),null);
    assert.equal(nextAvailableLoad('barbell_bench',100,inventory,['barbell','rack']),null);
    assert.equal(availableLoadAtOrBelow('barbell_bench',95,inventory,['barbell','rack']),null);
}
assert.equal(nextAvailableLoad('barbell_bench',100,{exerciseOverrides:{barbell_bench:{increment:Infinity}}},['barbell','rack']),null);
assert.equal(nextAvailableLoad('barbell_bench',100,{exerciseOverrides:{barbell_bench:{increment:'5'}}},['barbell','rack']),null);
assert.equal(nextAvailableLoad('barbell_bench',100,undefined,['barbell','rack']),105);
assert.equal(availableLoadAtOrBelow('barbell_bench',104,undefined,['barbell','rack']),100);
const ordinary=structuredClone(DEFAULT_LOADING_INVENTORY), before=structuredClone(ordinary);
assert.deepEqual(normalizeLoadingInventory(normalizeLoadingInventory(ordinary)),normalizeLoadingInventory(ordinary));
nextAvailableLoad('barbell_bench',185,ordinary,['barbell','rack']);assert.deepEqual(ordinary,before);
assert.equal(nextAvailableLoad('db-bench',20,{dumbbells:{availablePerHand:[25,20,25,NaN,null]}},['dumbbell']),25);
assert.equal(nextAvailableLoad('barbell_bench',100,{exerciseOverrides:{barbell_bench:{minimum:0,increment:2.5,maximum:105}}},['barbell','rack']),102.5);
console.log('PASS M221 equipment: malformed lists do not crash; explicit empty/invalid inventories block impossible load advice; valid inventories and overrides remain exact and immutable.');

// Huge plate quantities used to enumerate the entire stock and hang. Queries stay target-bounded.
assert.equal(nextAvailableLoad('barbell_bench',185,{barbell:{barWeight:45,platePairs:[{weight:2.5,pairs:100000000}]}},['barbell','bench','rack']),190);
assert.equal(availableLoadAtOrBelow('barbell_bench',184,{barbell:{barWeight:45,platePairs:[{weight:2.5,pairs:100000000}]}},['barbell','bench','rack']),180);
assert.equal(nextAvailableLoad('barbell_bench',Number.MAX_VALUE,{exerciseOverrides:{barbell_bench:{increment:Number.MAX_VALUE}}},['barbell','bench','rack']),null);
assert.equal(nextAvailableLoad('barbell_bench',100,{barbell:{barWeight:45,platePairs:[{weight:0.01,pairs:100000000},{weight:0.03,pairs:100000000}]}},['barbell','bench','rack']),null,'resource limit blocks advice rather than guessing');
// Independent exhaustive combinations are affordable on ordinary finite inventories.
let comparisons=0;
for(const barWeight of [20,45]) for(const denomination of [1.25,2.5,5]) {
    const pairs=[{weight:denomination,pairs:2},{weight:denomination*3,pairs:3},{weight:denomination*7,pairs:1}];
    const all=new Set();for(let a=0;a<=2;a++)for(let b=0;b<=3;b++)for(let c=0;c<=1;c++)all.add(barWeight+2*denomination*(a+3*b+7*c));
    const exact=[...all].sort((a,b)=>a-b),inventory={barbell:{barWeight,platePairs:pairs}};
    for(let current=0;current<=barWeight+40*denomination;current+=0.75) {
        assert.equal(nextAvailableLoad('barbell_bench',current,inventory,['barbell','bench','rack']),exact.find(n=>n>current+1e-6)??null);
        assert.equal(availableLoadAtOrBelow('barbell_bench',current,inventory,['barbell','bench','rack']),current>0?(exact.filter(n=>n<=current+1e-6).at(-1)??null):null);
        comparisons+=2;
    }
}
console.log(`PASS M221 bounded loading: huge stock completes, pathological expansion fails closed, overflow is blocked and ${comparisons} exact finite-inventory comparisons agree.`);

for(const reps of [NaN,Infinity,-1,0,null,undefined,'8']) assert.equal(estimate1RM(100,reps,2),null);
for(const rir of [NaN,Infinity,'2',{},-1]) assert.equal(estimate1RM(100,8,rir),null);
assert.equal(estimate1RM(100,8,null),126.7);
assert.equal(estimate1RM(100,8,2),133.3);
assert.equal(estimate1RM(100,16,0),null);
assert.equal(bestEstimated1RM([null,{load:100,reps:NaN,rir:2},{load:100,reps:8,rir:2}]),133.3);
assert.equal(bestEstimated1RM(null),null);
assert.equal(estimate1RM(Number.MAX_VALUE,8,2),null);
console.log('PASS M221 history: invalid entries cannot poison the strongest valid estimate; missing effort and high-rep safeguards remain intact.');

const targets=(reps,rir)=>buildRuntimeSetTargets({exerciseId:'barbell_bench',cell:{sets:3,reps,rir},workingLoad:100,equipmentAvailable:['barbell','rack'],includeWarmups:false});
for(const range of ['', ' ', null, [null,null], '8-', '-12', [0,12]]) {
    const rows=targets(range,'');assert.ok(rows.every(s=>s.reps===8&&s.repRange[0]===8&&s.repRange[1]===12&&s.rir[0]===2));
}
assert.ok(targets('10–15','0').every(s=>s.reps===10&&s.repRange[1]===15&&s.rir[0]===0));
const pending=[{auto:true,reps:'14',target:{reps:'1-20'},weight:'205'},{auto:false,valueOwner:'user',reps:'14',target:{reps:'1-20'},weight:'212.5'},{auto:true,done:true,reps:'14',target:{reps:'1-20'}}];
const repaired=reconcilePendingRepTargets(pending,{reps:''});assert.equal(repaired[0].target.reps,'8-12');assert.equal(repaired[0].reps,'12');assert.equal(repaired[0].weight,'205');assert.equal(repaired[1],pending[1]);assert.equal(repaired[2],pending[2]);
console.log('PASS M221 runtime: blank or partial ranges use safe defaults; valid rep/RIR ranges remain literal; restoration preserves completed/manual rows and loads.');
