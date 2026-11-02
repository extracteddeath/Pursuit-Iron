import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout } from './native-workout.mjs';
import { inspectNativeEquipmentLoad } from './native-equipment-loading.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchEngineJson, dispatchBackupSchemaJson } from './protocol.mjs';

const profile={name:'Loading accuracy',goal:'both',experience:'intermediate',
 unit:'lb',split:'full_body',days:3,session:'s60',
 equipment:nativeProgramOptions().equipment,allowBodyweight:true,allowSupersets:false,barbellCap:3};
const original=prepareNativeProgram(JSON.stringify({creationId:'equipment-create',programId:'equipment-program',
 createdAt:1000,seed:223,profile})).snapshot;
function inspect(snapshot,choice,load) {
 const plan=prepareNativeWorkout(snapshot,JSON.stringify({programId:'equipment-program',
   workoutId:'equipment-workout',startedAt:2000}));
 const row=choice?.(plan.rows)??plan.rows[0],slot={exerciseIndex:row.slot,exerciseId:row.id,setIndex:0};
 return {plan,slot,issued:JSON.stringify(plan),
   review:inspectNativeEquipmentLoad(snapshot,JSON.stringify(plan),slot,load)};
}
const base=inspect(original,null,'159.5');
assert.equal(base.review.confirmed,false,'Never label assumptions as verified gym loading');
assert.equal(base.review.next,null);
const catalog=base.plan.rows;
const types=catalog.map(row=>({id:row.id,name:row.name}));
const inventory={unit:'lb',
 barbell:{barWeight:45,platePairs:[{weight:2.5,pairs:2},{weight:5,pairs:2},{weight:10,pairs:2},{weight:25,pairs:2}]},
 dumbbells:{availablePerHand:[20,25,30,35,40]},
 machine:{minimum:5,increment:5,maximum:250},
 cable:{minimum:5,increment:5,maximum:150},
 smith:{availableLoads:[15,20,25,30]},
 exerciseOverrides:{}};
const modified=JSON.parse(original);modified.saved[0].config.loadingInventory=inventory;
const explicit=JSON.stringify(modified);
const row=base.plan.rows[0],override={...inventory,exerciseOverrides:{[row.id]:{minimum:0,increment:5,maximum:250}}};
const withOverride=JSON.parse(original);withOverride.saved[0].config.loadingInventory=override;
const result=inspect(JSON.stringify(withOverride),rows=>rows.find(r=>r.id===row.id),'159.5');
assert.equal(result.review.confirmed,true);
assert.equal(result.review.family,'exercise_override');
assert.equal(result.review.previous,155);
assert.equal(result.review.next,160);
assert.equal(result.review.exact,false);
const wire=JSON.stringify({protocolVersion:1,id:'equipment-test',method:'inspectNativeEquipmentLoad',
 params:{rawSnapshot:JSON.stringify(withOverride),issuedWorkout:result.issued,slot:result.slot,inputLoad:'159.5'}});
assert.deepEqual(JSON.parse(dispatchWorkoutStoreJson(wire)).result,result.review);
assert.equal(JSON.parse(dispatchEngineJson(wire)).ok,false);
assert.equal(JSON.parse(dispatchBackupSchemaJson(wire)).ok,false);
const altered=JSON.parse(result.issued);altered.rows[0].id='bogus';
assert.equal(JSON.parse(dispatchWorkoutStoreJson(JSON.stringify({protocolVersion:1,id:'bad',method:'inspectNativeEquipmentLoad',
 params:{rawSnapshot:JSON.stringify(withOverride),issuedWorkout:JSON.stringify(altered),slot:result.slot,inputLoad:'159.5'}}))).ok,false);
assert.equal(JSON.parse(dispatchWorkoutStoreJson(JSON.stringify({protocolVersion:1,id:'bad2',method:'inspectNativeEquipmentLoad',
 params:{rawSnapshot:JSON.stringify(withOverride),issuedWorkout:result.issued,slot:result.slot,inputLoad:'-5'}}))).ok,false);
const kgJson=JSON.parse(original);kgJson.saved[0].config.loadingInventory={...inventory,unit:'kg'};
const noSilentConversion=inspect(JSON.stringify(kgJson),null,'155');
assert.equal(noSilentConversion.review.confirmed,false,'Do not convert unknown gym hardware into 5.51 lb machine jumps');
const empty=JSON.parse(original);empty.saved[0].config.loadingInventory={
 ...inventory,dumbbells:{availablePerHand:[]},barbell:{barWeight:45,platePairs:[]}};
const noPlates=inspect(JSON.stringify(empty),null,'160');
if(noPlates.review.mode==='barbell_total')assert.equal(noPlates.review.next,null);
console.log('PASS confirmed equipment only, 159.5->160, no 5.51 conversion, empty inventory, source binding, channel isolation; candidate exercises: '+JSON.stringify(types.slice(0,3)));
