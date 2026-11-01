import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { nativeProgramOptions,prepareNativeProgram,inspectNativeProgram } from './native-onboarding.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchBackupSchemaJson,dispatchEngineJson } from './protocol.mjs';
import { parseStoredData,splitSupportsTrainingDays,EX_BY_ID } from '../../modules/engine-api.js';
const hash=raw=>createHash('sha256').update(raw,'utf8').digest('hex');
const options=nativeProgramOptions();
for(const split of options.splits)
  assert.deepEqual(split.days,[3,4,5,6].filter(days=>splitSupportsTrainingDays(split.id,days)));
const base={creationId:'create-native-test',programId:'program-native-test',createdAt:1000,seed:223,
  profile:{name:'My native plan',goal:'both',experience:'intermediate',unit:'lb',split:'full_body',
    days:3,session:'s60',equipment:options.equipment,allowBodyweight:true,allowSupersets:false,barbellCap:3}};
let first;
for(const changes of [{},{split:'upper_lower',days:4,goal:'strength',unit:'kg'},
  {split:'ppl',days:6,goal:'hypertrophy',session:'s90',experience:'advanced',allowBodyweight:false},
  {split:'ulppl',days:5,session:'s120p',experience:'advanced',barbellCap:null}]) {
  const setup=JSON.stringify({...base,profile:{...base.profile,...changes}});
  const proposal=prepareNativeProgram(setup);first??=proposal;
  assert.equal(proposal.preparedOnly,true);assert.equal(proposal.approvedForWorkoutExecution,false);
  assert.equal(proposal.setupSha256,hash(setup));assert.equal(proposal.snapshotSha256,hash(proposal.snapshot));
  const store=parseStoredData(proposal.snapshot),program=store.saved[0];
  assert.equal(store.history.length,0);assert.equal(store.nativeOnboarding.setupJson,setup);
  assert.equal(program.nextEngine.program.audit.result,'pass');
  assert.equal(program.nextEngine.program.split.family,JSON.parse(setup).profile.split);
  assert.equal(program.config.unit,JSON.parse(setup).profile.unit);
  assert.equal(program.config.session,JSON.parse(setup).profile.session);
  assert.equal(program.config.noSupersets,true);
  assert.ok(program.days.every(day=>day.exercises.every(id=>EX_BY_ID[id])));
  const restored=inspectNativeProgram(proposal.snapshot);
  assert.equal(restored.program.program.id,base.programId);
  assert.equal(restored.creationId,base.creationId);
  assert.equal(restored.setupJson,setup);
  const current=JSON.parse(proposal.snapshot);current.savedAt=2000;
  assert.equal(inspectNativeProgram(JSON.stringify(current)).program.reference.storeSavedAt,2000,
    'owned program restore uses the current store revision');
}
const original=Date.now;Date.now=()=>999999;
try { assert.deepEqual(prepareNativeProgram(JSON.stringify(base)),first); }
finally { Date.now=original; }
for(const changes of [{split:'upper_lower',days:3},{split:'ulppl',days:6},{goal:'invented'},
  {equipment:['unknown']},{equipment:[]},{equipment:['barbell','barbell']},{barbellCap:9},
  {name:'private-profile\ninvalid'},{allowSupersets:1}])
  assert.throws(()=>prepareNativeProgram(JSON.stringify({...base,profile:{...base.profile,...changes}})));
const send=(method,params)=>JSON.stringify({protocolVersion:1,id:'onboarding-test',method,params});
for(const method of ['nativeProgramOptions','prepareNativeProgram','inspectNativeProgram']) {
  const params=method==='nativeProgramOptions'?{}:method==='prepareNativeProgram'
    ?{setupJson:JSON.stringify(base)}:{rawSnapshot:first.snapshot};
  const request=send(method,params);
  assert.equal(JSON.parse(dispatchWorkoutStoreJson(request)).ok,true);
  assert.equal(JSON.parse(dispatchBackupSchemaJson(request)).ok,false);
  assert.equal(JSON.parse(dispatchEngineJson(request)).ok,false);
}
const deleted=JSON.parse(first.snapshot);deleted.tombs[base.programId]=1000;
assert.throws(()=>inspectNativeProgram(JSON.stringify(deleted)));
const forged=JSON.parse(first.snapshot);forged.nativeOnboarding.creationId='another-creation';
assert.throws(()=>inspectNativeProgram(JSON.stringify(forged)));
assert.equal(JSON.parse(dispatchWorkoutStoreJson(send('prepareNativeProgram',
  {setupJson:JSON.stringify({...base,private:'do-not-echo'})}))).ok,false);
console.log('PASS native setup: four audited splits, goal/unit/time/equipment preservation, deterministic creation, restore, tombstones and channel separation');
