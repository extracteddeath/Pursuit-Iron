import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {prepareNativeProgram,inspectNativeProgram,nativeProgramOptions} from './native-onboarding.mjs';
import {prepareNativeGymInventoryEdit} from './native-gym-inventory-edit.mjs';
import {prepareNativeWorkout} from './native-workout.mjs';
import {inspectNativeEquipmentLoad} from './native-equipment-loading.mjs';
import {dispatchWorkoutStoreJson} from './workout-store-protocol.mjs';
import {dispatchEngineJson,dispatchBackupSchemaJson} from './protocol.mjs';
import {sha256Utf8} from './json-identity.mjs';

let packagedDispatch=dispatchWorkoutStoreJson;
if(process.argv[2]) {
  const context=vm.createContext({});
  vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  packagedDispatch=context.PursuitIronBridge.dispatchWorkoutStoreJson;
}
const profile={name:'Own gym',goal:'both',experience:'intermediate',unit:'lb',
  split:'full_body',days:3,session:'s60',equipment:nativeProgramOptions().equipment,
  allowBodyweight:true,allowSupersets:false,barbellCap:3,focus:{},reduced:[]};
const first=prepareNativeProgram(JSON.stringify({
  creationId:'gym-create',programId:'gym-program',createdAt:1000,seed:223,profile})).snapshot;
const original=JSON.parse(first);
original.history=[{id:'prior-workout',programId:'gym-program',dayId:original.saved[0].days[0].id,
  date:901,perf:{bench:{weight:155}}}];
original.perf={historic:{weight:155}};
original.prefs={theme:'dark'};
const exactSource=JSON.stringify(original);
const reference=inspectNativeProgram(exactSource).program.reference;
const stock={unit:'lb',barbell:{barWeight:45,platePairs:[
  {weight:2.5,pairs:2},{weight:5,pairs:2},{weight:10,pairs:2},{weight:25,pairs:2}]},
  dumbbells:{availablePerHand:[20,25,30]},cable:{minimum:5,increment:5,maximum:200},
  smith:{availableLoads:[15,25,35]}};
const request={version:1,operationId:'gym-stock-1',reference,editedAt:2000,loadingInventory:stock};
const editJson=JSON.stringify(request);
const prepared=prepareNativeGymInventoryEdit(exactSource,editJson);
assert.equal(prepared.sourceSnapshotSha256,sha256Utf8(exactSource));
assert.equal(prepared.candidateSnapshotSha256,sha256Utf8(prepared.candidateSnapshot));
assert.equal(prepared.preparedOnly,true);assert.equal(prepared.approvedForWorkoutExecution,false);
assert.equal(prepared.alreadyPresent,false);
const saved=JSON.parse(prepared.candidateSnapshot);
assert.deepEqual(saved.saved[0].config.loadingInventory,stock);
assert.deepEqual(saved.history,original.history);
assert.deepEqual(saved.perf,original.perf);
assert.deepEqual(saved.prefs,original.prefs);
assert.deepEqual(saved.saved[0].days,original.saved[0].days);
assert.deepEqual(saved.saved[0].nextEngine,original.saved[0].nextEngine);
assert.equal(saved.saved[0].updatedAt,2000);
assert.equal(saved.savedAt,2000);
assert.equal(saved.nativeGymInventoryEdit.sourceSnapshotSha256,sha256Utf8(exactSource));
assert.deepEqual(prepareNativeGymInventoryEdit(prepared.candidateSnapshot,editJson),{
 ...prepared,alreadyPresent:true,sourceSnapshotSha256:sha256Utf8(prepared.candidateSnapshot)
},'A repeat request must be an exact nonwriting acknowledged retry');
assert.equal(inspectNativeProgram(prepared.candidateSnapshot).loadingInventory.unit,'lb');
assert.equal(inspectNativeProgram(exactSource).loadingInventory,null);
const plan=prepareNativeWorkout(prepared.candidateSnapshot,
  JSON.stringify({programId:'gym-program',workoutId:'next-workout',startedAt:3000}));
const row=plan.rows.find(x=>x.id && x.sets?.some(set=>!set.warm));
const slot={exerciseIndex:row.slot,exerciseId:row.id,setIndex:0};
const review=inspectNativeEquipmentLoad(prepared.candidateSnapshot,JSON.stringify(plan),slot,'160');
assert.equal(review.confirmed,true,'Actual saved stock must be used by native loading inspection');
assert.equal(review.mode,'barbell_total');assert.equal(review.next,165);
assert.equal(inspectNativeEquipmentLoad(exactSource,
  JSON.stringify(prepareNativeWorkout(exactSource,JSON.stringify({
    programId:'gym-program',workoutId:'old-workout',startedAt:3001}))),
  slot,'160').confirmed,false,'Unconfigured source is always manual');
// Clear stock is an explicit, authenticated new revision.
const clearRequest={version:1,operationId:'gym-stock-clear',reference:prepared.program.reference,
  editedAt:4000,loadingInventory:null};
const cleared=prepareNativeGymInventoryEdit(prepared.candidateSnapshot,JSON.stringify(clearRequest));
const clean=JSON.parse(cleared.candidateSnapshot);
assert.ok(!Object.hasOwn(clean.saved[0].config,'loadingInventory'));
assert.deepEqual(clean.history,original.history);
assert.equal(inspectNativeProgram(cleared.candidateSnapshot).loadingInventory,null);
assert.equal(JSON.stringify(original),exactSource);
const wire=(params,method='prepareNativeGymInventoryEdit')=>JSON.stringify({
 protocolVersion:1,id:'gym-edit-wire',method,params});
const params={currentSnapshot:exactSource,editJson};
assert.equal(JSON.parse(packagedDispatch(wire(params))).ok,true);
assert.equal(JSON.parse(dispatchEngineJson(wire(params))).ok,false);
assert.equal(JSON.parse(dispatchBackupSchemaJson(wire(params))).ok,false);
for(const change of [
 r=>r.reference.snapshotSha256='a'.repeat(64),
 r=>r.reference.programId='wrong-program',r=>r.editedAt=1000,
 r=>r.extra=true,r=>r.loadingInventory={...stock,unit:'kg'},
 r=>r.loadingInventory={...stock,dumbbells:{availablePerHand:[20,20]}},
 r=>r.loadingInventory={...stock,barbell:{barWeight:45,platePairs:[]}},
 r=>r.loadingInventory={...stock,smith:{availableLoads:[10,-20]}},
 r=>r.loadingInventory={...stock,exerciseOverrides:{'back-squat':{increment:5}}},
 r=>r.loadingInventory={unit:'lb'}
]) {
 const forged=structuredClone(request);change(forged);
 assert.throws(()=>prepareNativeGymInventoryEdit(exactSource,JSON.stringify(forged)));
 assert.equal(JSON.parse(packagedDispatch(wire({
  currentSnapshot:exactSource,editJson:JSON.stringify(forged)}))).ok,false);
}
assert.throws(()=>prepareNativeGymInventoryEdit(prepared.candidateSnapshot,
 JSON.stringify({...request,loadingInventory:{unit:'kg'}})));
assert.throws(()=>prepareNativeGymInventoryEdit(cleared.candidateSnapshot,editJson),
 'Stale original revision cannot overwrite a later gym update');
const duplicated=JSON.parse(exactSource);
duplicated.saved.push({...structuredClone(duplicated.saved[0]),id:'other-program'});
duplicated.saved[1].config.loadingInventory={unit:'lb',dumbbells:{availablePerHand:[10]}};
const multi=JSON.stringify(duplicated);
const multiRef=inspectNativeProgram(multi).program.reference;
const multiEdited=prepareNativeGymInventoryEdit(multi,JSON.stringify({
 ...request,reference:multiRef}));
const multiSaved=JSON.parse(multiEdited.candidateSnapshot);
assert.deepEqual(multiSaved.saved[1],duplicated.saved[1]);
console.log('PASS native gym edit: historical preservation, exact revision, typed stock, clear, retry, next workout and channel isolation');
