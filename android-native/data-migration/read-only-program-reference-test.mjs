import assert from 'node:assert/strict';
import { STORE_VERSION, mergeStores, parseStoredData } from '../../modules/engine-api.js';
import {
  deriveReadOnlyProgramReference, matchesCurrentReadOnlyProgram
} from './read-only-program-reference.mjs';

// Only invented data is used. This test must never read a personal backup.
assert.equal(STORE_VERSION, 13);
const program = (updatedAt, name = 'Synthetic plan') => ({
  id:'synthetic-program', updatedAt, name,
  config:{goal:'both'}, days:[{id:'synthetic-day', exercises:[]}]
});
const store = (savedAt, saved, tombs = {}) => ({
  v:13, savedAt, saved, tombs, history:[], cycles:[], custom:[],
  gyms:[], perf:{}
});
const raw = JSON.stringify(store(100, [program(42)]));
const ref = deriveReadOnlyProgramReference(raw, 'synthetic-program');
assert.deepEqual(Object.keys(ref), [
  'programId','programUpdatedAt','storeSavedAt','storeVersion','snapshotSha256'
]);
assert.equal(ref.programUpdatedAt, 42);
assert.match(ref.snapshotSha256, /^[0-9a-f]{64}$/);
assert.ok(Object.isFrozen(ref));
assert.ok(matchesCurrentReadOnlyProgram(raw,ref));
assert.equal(deriveReadOnlyProgramReference(raw,'other-program'),null);
assert.equal(JSON.stringify(parseStoredData(raw)),raw,
  'canonical inspection may not rewrite the input');

const modified = JSON.stringify(store(101,[program(43)]));
assert.ok(!matchesCurrentReadOnlyProgram(modified,ref),
  'a newer program revision invalidates the reference');
assert.ok(!matchesCurrentReadOnlyProgram(JSON.stringify(store(101,[program(42)])),ref),
  'a different exact snapshot invalidates the reference even for the same program');
assert.ok(!matchesCurrentReadOnlyProgram(JSON.stringify(store(100,[program(42,'Other plan')])),ref),
  'same-clock changed program content cannot reuse a reference');
assert.equal(deriveReadOnlyProgramReference(JSON.stringify(store(100,[program(42)],{
  'synthetic-program':42
})), 'synthetic-program'),null, 'equal-time deletion wins');
assert.equal(deriveReadOnlyProgramReference(JSON.stringify(store(100,[program(42)],{
  'synthetic-program':43
})), 'synthetic-program'),null, 'newer deletion wins');
assert.ok(deriveReadOnlyProgramReference(JSON.stringify(store(100,[program(42)],{
  'synthetic-program':41
})), 'synthetic-program'), 'a newer edit may resurrect a deleted program');

const newer = store(90,[program(50)]);
const deleted = store(110,[],{'synthetic-program':55});
const merged = mergeStores(newer,deleted).data;
assert.equal(deriveReadOnlyProgramReference(JSON.stringify(merged),'synthetic-program'),
  null,'the reference gate must agree with canonical merge tombstones');
assert.equal(deriveReadOnlyProgramReference(JSON.stringify(
  mergeStores(deleted,store(120,[program(60)])).data
),'synthetic-program').programUpdatedAt,60,'later explicit edit wins');

for (const invalid of [
  JSON.stringify(store(100,[program(42),program(43)])),
  JSON.stringify(store(100,[{...program(42),updatedAt:-1}])),
  JSON.stringify(store(100,[{...program(42),updatedAt:'42'}])),
  JSON.stringify(store(100,[{...program(42),updatedAt:undefined}])),
  JSON.stringify(store(100,[program(42)],{'synthetic-program':'44'})),
  JSON.stringify(store(-1,[program(42)])),
  JSON.stringify({...store(100,[program(42)]),v:12}),
  JSON.stringify({...store(100,[program(42)]),v:14})
]) assert.throws(()=>deriveReadOnlyProgramReference(invalid,'synthetic-program'));
assert.throws(()=>deriveReadOnlyProgramReference(raw,'__proto__'));
assert.ok(!matchesCurrentReadOnlyProgram(raw,{...ref,snapshotSha256:'f'.repeat(64)}));
assert.ok(!matchesCurrentReadOnlyProgram(raw,{...ref,authorizedForLogging:true}));
assert.ok(!matchesCurrentReadOnlyProgram('{',ref));
assert.equal(JSON.stringify(store(100,[program(42)])),raw,
  'identity checks cannot mutate the original snapshot');
console.log('PASS source-backed read-only v13 saved-program identity, digest, clocks, tombstones, duplicates and rejection gates');
