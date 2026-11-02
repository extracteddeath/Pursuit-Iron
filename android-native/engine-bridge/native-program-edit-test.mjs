import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { sha256Utf8 } from './json-identity.mjs';
import { nativeProgramOptions,prepareNativeProgram,inspectNativeProgram } from './native-onboarding.mjs';
import { prepareNativeProgramEdit } from './native-program-edit.mjs';
import { prepareNativeWorkout } from './native-workout.mjs';
import { dispatchWorkoutStoreJson as sourceDispatch } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';

let dispatchWorkoutStoreJson=sourceDispatch;
if(process.argv[2]) {
  const context=vm.createContext({});
  vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context,{timeout:10000});
  dispatchWorkoutStoreJson=context.PursuitIronBridge.dispatchWorkoutStoreJson;
}
const proposal=prepareNativeProgram(JSON.stringify({creationId:'creation-edit',programId:'program-edit',
  createdAt:1000,seed:223,profile:{name:'Original plan',goal:'both',experience:'advanced',unit:'lb',
    split:'full_body',days:3,session:'s90',equipment:nativeProgramOptions().equipment,
    allowBodyweight:true,allowSupersets:true,barbellCap:3}}));
const raw=JSON.parse(proposal.snapshot),p=raw.saved[0],day=p.days[0];
assert.ok(day.exercises.length>=3);
// Repeated IDs must carry the original slot's override, not the first matching ID.
day.exercises[2]=day.exercises[0];day.primaryIndex=0;
p.cycleId='cycle-preserved';p.custom=true;
raw.cycles=[{id:'cycle-preserved',name:'Preserved cycle',blockIds:['program-edit','sibling-block'],adaptExercises:true,adaptSplit:true,updatedAt:900}];
raw.saved.push({...structuredClone(p),id:'sibling-block',name:'Other block'});
raw.history=[{id:'preserved-history',programId:p.id,dayId:day.id,date:900,perf:{}}];
raw.perf={privateRecord:{w:123,r:4}};raw.tombs={'old-record':500};
raw.prefs={theme:'dark',barbellWeight:45};
const fields=['overrides','nextWeekPrescriptions','progStyle','slotBias','autoBias','rounds','weekOff','pairs'];
for(const f of fields) p[f]={...(p[f]||{}),[`${day.id}:0`]:{sentinel:f+'-first',myo:{mini:3}},
  [`${day.id}:2`]:{sentinel:f+'-repeat',drop:{percent:20}},[`${day.id}:0:week-4`]:f+'-week'};
p.ss={};
const snapshot=JSON.stringify(raw);
const original=inspectNativeProgram(snapshot).program;
const request={format:1,operationId:'save-edit',reference:original.reference,editedAt:2000,name:'Organized plan',
  days:p.days.map(d=>({id:d.id,label:d.label,slots:d.exercises.map((_,i)=>i)}))};
[request.days[0].slots[0],request.days[0].slots[2]]=[2,0];
request.days[0].label='Renamed day';
[request.days[0],request.days[1]]=[request.days[1],request.days[0]];
const edit=JSON.stringify(request),prepared=prepareNativeProgramEdit(snapshot,edit);
assert.equal(prepared.source,'modules/engine-api.js:programSavePlan');
assert.equal(prepared.sourceSnapshotSha256,sha256Utf8(snapshot));
assert.equal(prepared.candidateSnapshotSha256,sha256Utf8(prepared.candidateSnapshot));
assert.equal(prepared.alreadyPresent,false);
const saved=JSON.parse(prepared.candidateSnapshot),edited=saved.saved[0];
assert.equal(edited.name,request.name);assert.equal(edited.updatedAt,2000);
assert.equal(edited.days[1].label,'Renamed day');assert.equal(edited.days[1].primaryIndex,2);
assert.deepEqual(edited.days[1].exercises,day.exercises,
  'Repeated IDs look the same; slot identities still move');
for(const f of fields) {
  assert.deepEqual(edited[f][`${day.id}:2`],p[f][`${day.id}:0`],f+' follows the original first slot');
  assert.deepEqual(edited[f][`${day.id}:0`],p[f][`${day.id}:2`],f+' follows the repeated slot');
  assert.deepEqual(edited[f][`${day.id}:2:week-4`],p[f][`${day.id}:0:week-4`]);
}
for(const key of Object.keys(raw).filter(k=>!['saved','savedAt'].includes(k)))
  assert.deepEqual(saved[key],raw[key],key+' is preserved');
assert.deepEqual(saved.saved[1],raw.saved[1],'Cycle siblings are never implicitly edited');
assert.equal(inspectNativeProgram(prepared.candidateSnapshot).program.program.name,request.name);
assert.deepEqual(prepareNativeProgramEdit(prepared.candidateSnapshot,edit),{
  ...prepared,alreadyPresent:true,sourceSnapshotSha256:sha256Utf8(prepared.candidateSnapshot)
},'An exact acknowledged/lost-ack retry writes nothing');
assert.equal(snapshot,JSON.stringify(raw),'Preparation is pure');

const send=(params,method='prepareNativeProgramEdit')=>JSON.stringify({protocolVersion:1,id:'edit-wire',method,params});
const params={currentSnapshot:snapshot,editJson:edit};
assert.equal(JSON.parse(dispatchWorkoutStoreJson(send(params))).ok,true);
for(const diagnostic of [dispatchEngineJson,dispatchBackupSchemaJson])
  assert.equal(JSON.parse(diagnostic(send(params))).ok,false,'Read-only diagnostics cannot prepare saved edits');
for(const change of [
  r=>r.name='',r=>r.name='bad\nname',r=>r.name='x'.repeat(121),
  r=>r.editedAt=1000,r=>r.extra=true,r=>r.reference.snapshotSha256='a'.repeat(64),
  r=>r.reference.programUpdatedAt=999,r=>r.reference.storeSavedAt=999,
  r=>r.days.pop(),r=>r.days.push(r.days[0]),r=>r.days[0].id='missing-day',
  r=>r.days[0].slots.push(0),r=>r.days[0].slots[0]=99,
  r=>r.days[0].slots[0]=-1,r=>r.days[0].slots[0]=0.5
]) {
  const altered=structuredClone(request);change(altered);
  const wire=send({...params,editJson:JSON.stringify(altered)});
  const result=JSON.parse(dispatchWorkoutStoreJson(wire));assert.equal(result.ok,false);
  assert.ok(!JSON.stringify(result).includes('privateRecord'),'Errors never echo a saved record');
}
assert.throws(()=>prepareNativeProgramEdit(prepared.candidateSnapshot,JSON.stringify({...request,name:'replayed-other-name'})));
const stale=JSON.stringify({...raw,savedAt:1001});assert.throws(()=>prepareNativeProgramEdit(stale,edit));
const legacy=structuredClone(raw);delete legacy.history[0].dayId;
const legacySnapshot=JSON.stringify(legacy),legacyEdit=structuredClone(request);
legacyEdit.reference=inspectNativeProgram(legacySnapshot).program.reference;
assert.throws(()=>prepareNativeProgramEdit(legacySnapshot,JSON.stringify(legacyEdit)));

// Superset links and round counts travel with a complete, ordered group.
const paired=JSON.parse(proposal.snapshot),pd=paired.saved[0].days[0];
paired.saved[0].ss={[`${pd.id}:0`]:true};paired.saved[0].rounds={[`${pd.id}:0`]:4};
const pairedSnapshot=JSON.stringify(paired),pr=inspectNativeProgram(pairedSnapshot).program;
const pairedRequest={format:1,operationId:'save-paired',reference:pr.reference,editedAt:3000,name:'Paired plan',
  days:paired.saved[0].days.map(d=>({id:d.id,label:d.label,slots:d.exercises.map((_,i)=>i)}))};
const group=pairedRequest.days[0].slots.splice(0,2);pairedRequest.days[0].slots.push(...group);
const pairedResult=JSON.parse(prepareNativeProgramEdit(pairedSnapshot,JSON.stringify(pairedRequest)).candidateSnapshot);
const anchor=pd.exercises.length-2;
assert.deepEqual(pairedResult.saved[0].ss,{[`${pd.id}:${anchor}`]:true});
assert.deepEqual(pairedResult.saved[0].rounds,{[`${pd.id}:${anchor}`]:4});
[pairedRequest.days[0].slots[anchor],pairedRequest.days[0].slots[anchor+1]]=[1,0];
assert.throws(()=>prepareNativeProgramEdit(pairedSnapshot,JSON.stringify(pairedRequest)),
  'A save cannot silently reverse or split an authored pairing');

// Exercise settings are not synthesized by native code; actual canonical
// issuance must observe a saved ordinary reorder after a cold restore.
const plain=inspectNativeProgram(proposal.snapshot).program;
const ordinary={format:1,operationId:'ordinary-edit',reference:plain.reference,editedAt:4000,name:'Next workout plan',
  days:plain.program.days.map(d=>({id:d.id,label:d.label,slots:d.exerciseIds.map((_,i)=>i)}))};
[ordinary.days[0].slots[0],ordinary.days[0].slots[1]]=[1,0];
const issuedSnapshot=prepareNativeProgramEdit(proposal.snapshot,JSON.stringify(ordinary)).candidateSnapshot;
const workout=prepareNativeWorkout(issuedSnapshot,JSON.stringify({programId:'program-edit',
  workoutId:'after-edit',startedAt:5000}));
assert.equal(workout.rows[0].id,plain.program.days[0].exerciseIds[1]);
assert.equal(workout.rows[1].id,plain.program.days[0].exerciseIds[0]);
// Native edit v2 deletes a selected *slot*, without changing older format-1 saves.
const removedRaw=structuredClone(raw),removedDay=removedRaw.saved[0].days[0];
const removedSnapshot=JSON.stringify(removedRaw),removedRef=inspectNativeProgram(removedSnapshot).program.reference;
const removal={format:2,operationId:'remove-slot',reference:removedRef,editedAt:6000,
  name:removedRaw.saved[0].name,days:removedRaw.saved[0].days.map(d=>
    ({id:d.id,label:d.label,slots:d.exercises.map((_,i)=>i)}))};
removal.days[0].slots.splice(2,1);
const removalWire=JSON.stringify(removal);
const removalPrepared=prepareNativeProgramEdit(removedSnapshot,removalWire);
const removedStore=JSON.parse(removalPrepared.candidateSnapshot);
assert.equal(removedStore.saved[0].days[0].exercises.length,removedDay.exercises.length-1);
assert.deepEqual(removedStore.saved[0].overrides[`${removedDay.id}:2`],
  removedRaw.saved[0].overrides[`${removedDay.id}:3`],
  'The next original slot shifts forward without inheriting the removed slot override');
assert.notDeepEqual(removedStore.saved[0].overrides[`${removedDay.id}:2`],
  removedRaw.saved[0].overrides[`${removedDay.id}:2`]);
assert.deepEqual(removedStore.saved[0].overrides[`${removedDay.id}:0`],
  removedRaw.saved[0].overrides[`${removedDay.id}:0`]);
assert.deepEqual(removedStore.cycles,removedRaw.cycles);
assert.deepEqual(removedStore.history,removedRaw.history);
assert.deepEqual(removedStore.saved[1],removedRaw.saved[1]);
assert.equal(prepareNativeProgramEdit(removalPrepared.candidateSnapshot,removalWire).alreadyPresent,true);
const badPrimary=structuredClone(removal);badPrimary.days[0].slots=[1,2];
assert.throws(()=>prepareNativeProgramEdit(removedSnapshot,JSON.stringify(badPrimary)));
const lastGone=structuredClone(removal);lastGone.days[0].slots=[];
assert.throws(()=>prepareNativeProgramEdit(removedSnapshot,JSON.stringify(lastGone)));
const oldFormat=structuredClone(removal);oldFormat.format=1;
assert.throws(()=>prepareNativeProgramEdit(removedSnapshot,JSON.stringify(oldFormat)));
const pairedRemoval=structuredClone(removedRaw);
pairedRemoval.saved[0].ss={[`${removedDay.id}:1`]:true};
const pairedRemovalSnapshot=JSON.stringify(pairedRemoval);
const pairedRemovalRequest=structuredClone(removal);
pairedRemovalRequest.reference=inspectNativeProgram(pairedRemovalSnapshot).program.reference;
assert.throws(()=>prepareNativeProgramEdit(pairedRemovalSnapshot,JSON.stringify(pairedRemovalRequest)),
  'Removing a member from an authored pair must be refused');
const beforeRemovalWorkout=prepareNativeWorkout(removedSnapshot,JSON.stringify({
  programId:'program-edit',workoutId:'pre-removal',startedAt:7000}));
const afterRemovalWorkout=prepareNativeWorkout(removalPrepared.candidateSnapshot,JSON.stringify({
  programId:'program-edit',workoutId:'post-removal',startedAt:8000}));
assert.equal(afterRemovalWorkout.session.dayId,beforeRemovalWorkout.session.dayId);
if(beforeRemovalWorkout.session.dayId===removedDay.id) {
  assert.ok(afterRemovalWorkout.rows.length<beforeRemovalWorkout.rows.length);
  assert.equal(afterRemovalWorkout.rows[0].id,beforeRemovalWorkout.rows[0].id);
} else {
  assert.deepEqual(afterRemovalWorkout.rows.map(x=>x.id),beforeRemovalWorkout.rows.map(x=>x.id),
    'A changed future day must not alter the canonical next-day roster');
}
// The single-slot substitution route never transfers numeric prescriptions.
// Use the exact generated program as a fixture: choose a same-part/type
// replacement from the canonical catalog with matching equipment and pattern.
const replacementBase=JSON.parse(proposal.snapshot),replacementDay=replacementBase.saved[0].days[0];
const existingId=replacementDay.exercises[1];
const {EX_BY_ID:exerciseMap, EXERCISES:exerciseCatalog, expandEquipment:expand, nextExerciseIdForShellExercise:engineIdFor, isBarLike:barLike, canonicalRepRange:repPair}=await import('../../modules/engine-api.js');
const originalExercise=exerciseMap[existingId],allow=expand(replacementBase.saved[0].config.equipment);
const engineWeeks=replacementBase.saved[0].nextWeekPrescriptions[`${replacementDay.id}:1`];
const replacementId=exerciseCatalog.find(ex=>ex.id!==existingId &&
  ex.part===originalExercise.part && ex.type===originalExercise.type &&
  (!ex.pattern || !originalExercise.pattern || ex.pattern===originalExercise.pattern) &&
  Object.values(engineWeeks).every(cell=>cell.tech==null && cell.sets>0 &&
    repPair(cell.reps)?.[0]>=ex.rep[0] && repPair(cell.reps)?.[1]<=ex.rep[1]) &&
  ex.equip.every(item=>allow.has(item)) &&
  (ex.equip.length>0 || !replacementBase.saved[0].config.noBodyweight) &&
  !replacementDay.exercises.includes(ex.id) && engineIdFor(ex) &&
  (!Number.isInteger(replacementBase.saved[0].config.barbellCap) ||
    replacementDay.exercises.reduce((sum,id,i)=>sum+(barLike(exerciseMap[i===1?ex.id:id]?.equip)?1:0),0)
    <=replacementBase.saved[0].config.barbellCap))?.id;
assert.ok(replacementId,'Generated program must offer a compatible alternate');
replacementBase.saved[0].overrides={...replacementBase.saved[0].overrides,
  [`${replacementDay.id}:1`]:{
    nextEngine:true,legacyExerciseId:existingId,
    nextExerciseId:engineIdFor(originalExercise)
  }};
for(const field of ['progStyle','slotBias','autoBias','rounds','weekOff','pairs','ss']) {
  const map=replacementBase.saved[0][field]||{};
  for(const key of Object.keys(map)) if(key===`${replacementDay.id}:1` ||
    key.startsWith(`${replacementDay.id}:1:`)) delete map[key];
  replacementBase.saved[0][field]=map;
}
const replacementSnapshot=JSON.stringify(replacementBase);
const replacementRef=inspectNativeProgram(replacementSnapshot).program.reference;
const substitution={format:3,operationId:'substitution-one',
  reference:replacementRef,editedAt:7000,name:replacementBase.saved[0].name,
  days:replacementBase.saved[0].days.map(d=>({id:d.id,label:d.label,
    slots:d.exercises.map((_,i)=>i)})),
  replacement:{dayId:replacementDay.id,sourceSlot:1,exerciseId:replacementId}};
const replacementWire=JSON.stringify(substitution);
const replacementPrepared=prepareNativeProgramEdit(replacementSnapshot,replacementWire);
const substitutionStore=JSON.parse(replacementPrepared.candidateSnapshot);
assert.equal(substitutionStore.saved[0].days[0].exercises[1],replacementId);
assert.deepEqual(substitutionStore.saved[0].overrides[`${replacementDay.id}:1`],{
  nextEngine:true,legacyExerciseId:replacementId,
  nextExerciseId:engineIdFor(exerciseMap[replacementId])
});
assert.deepEqual(substitutionStore.history,replacementBase.history);
assert.equal(prepareNativeProgramEdit(replacementPrepared.candidateSnapshot,replacementWire).alreadyPresent,true);
const illegal=structuredClone(substitution);illegal.replacement.sourceSlot=replacementDay.primaryIndex;
assert.throws(()=>prepareNativeProgramEdit(replacementSnapshot,JSON.stringify(illegal)));
illegal.replacement.sourceSlot=1;illegal.replacement.exerciseId='non-existent';
assert.throws(()=>prepareNativeProgramEdit(replacementSnapshot,JSON.stringify(illegal)));
const withMetadata=structuredClone(replacementBase);
withMetadata.saved[0].nextWeekPrescriptions[`${replacementDay.id}:1`]={userOwned:true};
const metaSnapshot=JSON.stringify(withMetadata);
const metaReq=structuredClone(substitution);metaReq.reference=inspectNativeProgram(metaSnapshot).program.reference;
const cleanedMeta=JSON.parse(prepareNativeProgramEdit(metaSnapshot,JSON.stringify(metaReq)).candidateSnapshot);
assert.deepEqual(cleanedMeta.saved[0].nextWeekPrescriptions[`${replacementDay.id}:1`],
  withMetadata.saved[0].nextWeekPrescriptions[`${replacementDay.id}:1`],
  'Only the verified engine-owned weekly role template can survive replacement');

const forgedReplacement=structuredClone(substitution);forgedReplacement.replacement.exerciseId='not-in-catalog';
assert.throws(()=>prepareNativeProgramEdit(replacementSnapshot,JSON.stringify(forgedReplacement)));
const fullMeta=structuredClone(replacementBase);
for(const field of ['overrides','slotBias','progStyle','autoBias','weekOff','rounds','pairs'])
  fullMeta.saved[0][field][`${replacementDay.id}:1:week-4`]={oldExerciseOwned:field};
const fullMetaSnapshot=JSON.stringify(fullMeta);
const fullMetaReq=structuredClone(substitution);
fullMetaReq.reference=inspectNativeProgram(fullMetaSnapshot).program.reference;
const clean=JSON.parse(prepareNativeProgramEdit(fullMetaSnapshot,JSON.stringify(fullMetaReq)).candidateSnapshot);
for(const field of ['overrides','nextWeekPrescriptions','slotBias','progStyle','autoBias','weekOff','rounds','pairs'])
  assert.equal(clean.saved[0][field][`${replacementDay.id}:1:week-4`],undefined,
    'Suffix-specific custom metadata must not survive a physical exercise replacement');
console.log('PASS native program edit: revision save, rich slot metadata, duplicate identity, pairings, retries, retained records and canonical issuance');
