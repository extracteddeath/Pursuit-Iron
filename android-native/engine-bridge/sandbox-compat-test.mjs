import assert from 'node:assert/strict';
import { clonePlainSandboxData, installCanonicalSandboxCompat } from './sandbox-compat.mjs';

const isolate={};
assert.equal(installCanonicalSandboxCompat(isolate),'plain-data-only');
assert.equal(installCanonicalSandboxCompat(isolate),'native');
const input={
  unit:'lb',barbell:{barWeight:45,platePairs:[{weight:45,pairs:2},{weight:2.5,pairs:2}]},
  machine:{increment:5,availableLoads:[95,100,105]},exerciseOverrides:{},extra:undefined
};
input.alias=input.machine;
const expected=structuredClone(input);
const cloned=isolate.structuredClone(input);
assert.deepEqual(cloned,expected,'factory inventories must match native structuredClone');
assert.notStrictEqual(cloned,input);
assert.strictEqual(cloned.alias,cloned.machine,'shared references');
cloned.machine.increment=10;
assert.equal(input.machine.increment,5,'must not mutate canonical inventory defaults');
const circular={label:'cycle'};
circular.ref=circular;
assert.strictEqual(clonePlainSandboxData(circular).ref.ref.label,'cycle');
for(const unsupported of [new Date(),new Map(),new Uint8Array(2),()=>1,{get x(){return 1;}}])
  assert.throws(()=>clonePlainSandboxData(unsupported),/Sandbox clone/);
assert.throws(()=>clonePlainSandboxData({item:Symbol('x')}),/supports data only/);
assert.deepEqual(clonePlainSandboxData({nested:[null,{value:1}]}),{nested:[null,{value:1}]});
assert.equal(installCanonicalSandboxCompat({structuredClone}),'native');
assert.throws(()=>installCanonicalSandboxCompat({structuredClone:null}),/Untrusted/);
console.log('PASS scoped plain-data structuredClone compatibility without altering the canonical engine');
