import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { prepareNativeProgram, inspectNativeProgram, nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeGymInventoryEdit } from './native-gym-inventory-edit.mjs';
import { prepareNativeWorkout } from './native-workout.mjs';
import { inspectNativeEquipmentLoad } from './native-equipment-loading.mjs';
import { nativeIssuedLoadMode, nativeStackCandidates } from './native-loading-catalog.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';

let worker=dispatchWorkoutStoreJson;
if(process.argv[2]) {
  const context=vm.createContext({});
  vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  worker=context.PursuitIronBridge.dispatchWorkoutStoreJson;
}
const equipment=nativeProgramOptions().equipment;
assert.equal(nativeIssuedLoadMode('lat-pulldown',equipment),'cable_stack');
assert.equal(nativeIssuedLoadMode('machine-press',equipment),null,
  'Dedicated selectorized machines cannot inherit a generic machine carrier');
assert.equal(nativeIssuedLoadMode('back-squat',equipment),'barbell_total');
assert.equal(nativeIssuedLoadMode('lat-pulldown',[]),null);
assert.deepEqual(nativeStackCandidates({days:[{exercises:['lat-pulldown','lat-pulldown','back-squat']}],
 config:{equipment}}),[{id:'lat-pulldown',name:'Lat Pulldown',mode:'cable_stack'}]);

const profile={name:'Verified machine setup',goal:'hypertrophy',experience:'advanced',
 unit:'lb',split:'ppl',days:6,session:'s90',equipment,allowBodyweight:true,
 allowSupersets:false,barbellCap:3,focus:{},reduced:[]};
let entry=null;
for(const seed of [223,7,71,1122,2026,4929,99,1201,805,1782]) {
  const setup=JSON.stringify({creationId:'stack-seed',programId:'stack-program',createdAt:1000,seed,profile});
  const snapshot=prepareNativeProgram(setup).snapshot;
  const saved=inspectNativeProgram(snapshot);
  const workout=prepareNativeWorkout(snapshot,JSON.stringify({
    programId:'stack-program',workoutId:'stack-before',startedAt:2000}));
  const candidate=saved.loadingCandidates.find(c=>workout.rows.some(r=>r.id===c.id));
  if(candidate) {entry={snapshot,saved,workout,candidate};break;}
}
assert.ok(entry,'An actual issued cable exercise must be available for source-pinned physical testing');
const {snapshot,saved,candidate}=entry;
assert.ok(saved.program.program.days.some(day=>day.exerciseIds.includes(candidate.id)));
assert.equal(candidate.mode,'cable_stack');
assert.equal(inspectNativeProgram(snapshot).loadingInventory,null,
  'Unconfigured gym cannot claim a factory load');

const override={unit:'lb',exerciseOverrides:{[candidate.id]:{
  minimum:5,increment:7.5,maximum:200}}};
const edit={version:1,operationId:'stack-edit',reference:saved.program.reference,editedAt:3000,
 loadingInventory:override};
const candidatePrep=prepareNativeGymInventoryEdit(snapshot,JSON.stringify(edit));
const newSnapshot=candidatePrep.candidateSnapshot;
assert.deepEqual(JSON.parse(newSnapshot).saved[0].config.loadingInventory,override);
assert.deepEqual(inspectNativeProgram(newSnapshot).loadingInventory,override);
const newWorkout=prepareNativeWorkout(newSnapshot,JSON.stringify({
 programId:'stack-program',workoutId:'stack-after',startedAt:3100}));
const row=newWorkout.rows.find(r=>r.id===candidate.id);
assert.ok(row);
const slot={exerciseIndex:row.slot,setIndex:0,exerciseId:row.id};
const view=inspectNativeEquipmentLoad(newSnapshot,JSON.stringify(newWorkout),slot,'159.5');
assert.equal(view.confirmed,true);
assert.equal(view.family,'exercise_override');
assert.equal(view.mode,'cable_stack');
assert.equal(view.exact,false);
assert.equal(view.previous,155);
assert.equal(view.next,162.5);
const wire=(current,request)=>JSON.stringify({protocolVersion:1,id:'stock-protocol',
 method:'prepareNativeGymInventoryEdit',params:{currentSnapshot:current,editJson:JSON.stringify(request)}});
assert.equal(JSON.parse(worker(wire(snapshot,edit))).ok,true);

const exact={unit:'lb',exerciseOverrides:{[candidate.id]:{availableLoads:[17.5,22.5,27.5]}}};
const second={version:1,operationId:'stack-exact',reference:candidatePrep.program.reference,
 editedAt:4000,loadingInventory:exact};
const exactResult=prepareNativeGymInventoryEdit(newSnapshot,JSON.stringify(second));
const lastSnapshot=exactResult.candidateSnapshot;
const exactWorkout=prepareNativeWorkout(lastSnapshot,JSON.stringify({
 programId:'stack-program',workoutId:'stack-exact-workout',startedAt:4100}));
const exactRow=exactWorkout.rows.find(r=>r.id===candidate.id);
assert.ok(exactRow);
const exactView=inspectNativeEquipmentLoad(lastSnapshot,JSON.stringify(exactWorkout),
 {exerciseIndex:exactRow.slot,setIndex:0,exerciseId:exactRow.id},'21');
assert.equal(exactView.confirmed,true);
assert.equal(exactView.previous,17.5);assert.equal(exactView.next,22.5);
assert.equal(JSON.parse(worker(wire(newSnapshot,second))).ok,true);

for(const bad of [
 {unit:'lb',exerciseOverrides:{'back-squat':{minimum:5,increment:5,maximum:100}}},
 {unit:'lb',exerciseOverrides:{'nonexistent-exercise':{availableLoads:[10,15]}}},
 {unit:'kg',exerciseOverrides:{[candidate.id]:{availableLoads:[10,15]}}},
 {unit:'lb',exerciseOverrides:{[candidate.id]:{availableLoads:[20,20]}}},
 {unit:'lb',exerciseOverrides:{[candidate.id]:{availableLoads:[20,20.001]}}},
 {unit:'lb',exerciseOverrides:{[candidate.id]:{minimum:5,increment:0,maximum:100}}},
 {unit:'lb',exerciseOverrides:{[candidate.id]:{minimum:5,increment:5}}},
 {unit:'lb',exerciseOverrides:{[candidate.id]:{mode:'cable_stack',minimum:5,increment:5,maximum:100}}},
 {unit:'lb',exerciseOverrides:{[candidate.id]:{minimum:5,increment:5,maximum:100},'back-squat':{availableLoads:[45,50]}}}
]) {
  const request={...edit,loadingInventory:bad};
  assert.throws(()=>prepareNativeGymInventoryEdit(snapshot,JSON.stringify(request)),
    'Unsupported or unverified exercise loading stock must fail closed');
  assert.equal(JSON.parse(worker(wire(snapshot,request))).ok,false);
}
assert.throws(()=>prepareNativeGymInventoryEdit(newSnapshot,JSON.stringify({
 ...edit,loadingInventory:exact})), 'Stale stock edits never change another revision');
assert.equal(prepareNativeGymInventoryEdit(newSnapshot,JSON.stringify(edit)).alreadyPresent,true,
  'Exact same operation acknowledges without a second write');
console.log('PASS physical per-exercise machine/cable: source ownership, exact/range modes, bad stock isolation, no defaults');
