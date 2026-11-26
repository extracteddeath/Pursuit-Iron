import assert from 'node:assert/strict';
import { addMachineVariant, editMachineVariant, setDefaultMachineVariant, resolveMachineVariant,
    stampMachineSet, machineHistory, latestMachineSet, machineStartingReference, comparableMachineScope,
    historyForMachineRecommendation, assignLegacyMachineHistory, sanitizeMachineVariants,
    LEGACY_MACHINE_ID } from '../modules/next-engine/machine-instances.js';

const gymX = { id:'gymX' }, gymY={ id:'gymY' };
const A={id:'A',label:'Machine A',loading:{unit:'lb',minimum:10,increment:5,maximum:300}};
const B={id:'B',label:'Machine B',loading:{unit:'lb',minimum:5,increment:10,maximum:250}};
let x=addMachineVariant(gymX,'chest_press',A,true);
x=addMachineVariant(x,'chest_press',B);
let y=addMachineVariant(gymY,'chest_press',B,true);
y=addMachineVariant(y,'chest_press',A);
assert.equal(resolveMachineVariant(x,'chest_press').id,'A');
assert.equal(resolveMachineVariant(y,'chest_press').id,'B');
assert.equal(resolveMachineVariant(x,'chest_press','B').loading.increment,10);
assert.throws(()=>addMachineVariant(x,'chest_press',A),/Duplicate/);
assert.throws(()=>setDefaultMachineVariant(x,'chest_press','missing'),/active/);

const row=(id,date,gymId,dayId,sets)=>({id,programId:'p1',date,gymId,dayId,unit:'lb',
    perf:{chest_press:{sets}}});
const set=(w,r,id)=>stampMachineSet({w,r,done:true},{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:id});
const history=[
    row('a1',1,'gymX','upper',[set(200,8,'A')]),
    row('b1',2,'gymX','upper',[set(140,8,'B')]),
    row('mixed',3,'gymX','upper',[set(145,9,'B'),set(205,8,'A')]),
    row('a-push',4,'gymX','push',[set(215,7,'A')]),
    row('gymY-b',5,'gymY','upper',[set(100,8,'B')]),
    row('legacy',6,'gymX','upper',[{w:190,r:8,done:true}])
];
assert.deepEqual(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'upper'}).map(s=>s.weight),[200,205]);
assert.deepEqual(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'B',dayId:'upper'}).map(s=>s.weight),[140,145]);
assert.equal(latestMachineSet(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'upper'}).weight,205);
assert.equal(latestMachineSet(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:LEGACY_MACHINE_ID,dayId:'upper'}).weight,190);
assert.equal(machineHistory(history,{gymId:'gymY',exerciseId:'chest_press',machineInstanceId:'B'}).length,1);
assert.equal(comparableMachineScope({gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'upper'},
    {gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'B',dayId:'upper'}),false);
assert.equal(comparableMachineScope({gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'upper'},
    {gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'push'}),false);
const scoped=historyForMachineRecommendation(history,'chest_press','A','gymX');
assert.deepEqual(scoped.map(h=>h.perf.chest_press?.sets.length),[1,0,1,1,0,0]);
assert.equal(scoped[2].perf.chest_press.sets[0].w,205);
assert.equal(history[2].perf.chest_press.sets.length,2,'filter never mutates saved sets');
x=setDefaultMachineVariant(x,'chest_press','B');
x=editMachineVariant(x,'chest_press','A',{active:false});
assert.equal(resolveMachineVariant(x,'chest_press').id,'B');
assert.equal(resolveMachineVariant(x,'chest_press','A',{forNewWorkout:false}).active,false);
assert.equal(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A'}).length,3);

/* Older untagged sets belong to the machine that was physically used,
   never to whichever variant was created second or made default later.
   Attaching history changes a gym-scoped view, NOT the original receipts. */
const rawHistory=JSON.stringify(history);
assert.equal(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',
    machineInstanceId:'B',gym:x}).length,2);
assert.equal(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',
    machineInstanceId:'A',gym:x}).length,3);
x=assignLegacyMachineHistory(x,'chest_press','A');
assert.equal(x.machineVariants.chest_press.legacyHistoryMachineId,'A');
const aWithOld=machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',
    machineInstanceId:'A',dayId:'upper',gym:x});
assert.deepEqual(aWithOld.map(v=>v.weight),[200,205,190]);
assert.equal(aWithOld.at(-1).recordedMachineInstanceId,LEGACY_MACHINE_ID,
    'Audit preserves the original unlabelled receipt identity');
assert.deepEqual(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',
    machineInstanceId:'B',dayId:'upper',gym:x}).map(v=>v.weight),[140,145]);
assert.equal(latestMachineSet(history,{gymId:'gymX',exerciseId:'chest_press',
    machineInstanceId:'A',dayId:'upper',gym:x}).weight,190);
assert.equal(historyForMachineRecommendation(history,'chest_press','A','gymX',x)
    .at(-1).perf.chest_press.sets.length,1);
assert.equal(historyForMachineRecommendation(history,'chest_press','B','gymX',x)
    .at(-1).perf.chest_press.sets.length,0);
assert.equal(machineHistory(history,{gymId:'gymY',exerciseId:'chest_press',
    machineInstanceId:'A',gym:x}).length,0,
    'GymX assignment never leaks to GymY');
assert.equal(sanitizeMachineVariants(x.machineVariants).chest_press.legacyHistoryMachineId,'A');
const xRenamed=editMachineVariant(x,'chest_press','A',{label:'Original Press'});
assert.equal(xRenamed.machineVariants.chest_press.legacyHistoryMachineId,'A');
const withThird=addMachineVariant(xRenamed,'chest_press',{id:'C',label:'New Stack'});
assert.equal(withThird.machineVariants.chest_press.legacyHistoryMachineId,'A');
assert.deepEqual(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',
    machineInstanceId:'C',gym:withThird}),[]);
assert.throws(()=>assignLegacyMachineHistory(x,'chest_press','unknown'),/existing physical machine/);
const restored=assignLegacyMachineHistory(x,'chest_press',null);
assert.equal(restored.machineVariants.chest_press.legacyHistoryMachineId,undefined);
assert.equal(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',
    machineInstanceId:'A',dayId:'upper',gym:restored}).length,2);
assert.equal(JSON.stringify(history),rawHistory,'A/B assignment cannot mutate saved training history');
console.log('PASS original-machine history assignment: no A/B load transfer, cross-gym leak or old receipt rewrite.');

console.log('Machine-instance web engine: 18 invariants pass.');

// The workout may use another day on the same station as a starting reference,
// without granting it progression authority or borrowing a different machine.
const referenceScope={gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',gym:x};
assert.equal(machineStartingReference(history,{...referenceScope,dayId:'upper'}).weight,190);
assert.equal(machineStartingReference(history,{...referenceScope,dayId:'upper'}).referenceOnly,false);
const otherDayReference=machineStartingReference(history,{...referenceScope,dayId:'new-day'});
assert.equal(otherDayReference.weight,190);
assert.equal(otherDayReference.referenceOnly,true);
assert.equal(machineStartingReference(history,{...referenceScope,machineInstanceId:'C',dayId:'new-day'}),null);
assert.equal(machineStartingReference(history,{...referenceScope,gymId:'gymY',dayId:'new-day'}),null);
const foreignSummary={...history[4],perf:{chest_press:{weight:100,reps:8,sets:[set(100,8,'B')]}}};
assert.equal(historyForMachineRecommendation([foreignSummary],'chest_press','A','gymX',x)[0].perf.chest_press.weight,null);
console.log('PASS same-machine starting references and cross-gym summary isolation');
