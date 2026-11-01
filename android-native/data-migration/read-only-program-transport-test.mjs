import assert from 'node:assert/strict';
import fs from 'node:fs';
import { deriveReadOnlyProgramReference } from './read-only-program-reference.mjs';
import { projectReadOnlySavedProgram } from './read-only-program-transport.mjs';

// Shared with Kotlin unit tests; all data in this file is invented.
const fixture = JSON.parse(fs.readFileSync(
  'android-native/domain-contract/src/test/resources/read-only-transport-golden.json','utf8'
));
const ref = deriveReadOnlyProgramReference(fixture.rawSnapshot,fixture.selectedProgramId);
assert.deepEqual(ref,fixture.wire.reference,'SHA is bound to exact canonical source bytes');
const result = projectReadOnlySavedProgram(fixture.rawSnapshot,ref);
assert.deepEqual(result,fixture.wire,'Node producer must match Kotlin golden wire fixture');
assert.equal(JSON.stringify(result).includes('"weight"'),false);
assert.equal(JSON.stringify(result).includes('"history"'),false);
assert.equal(JSON.stringify(result).includes('"reps"'),false);
assert.equal(result.program.days[0].exerciseIds[0],'bb-bench');

function mutateProgram(changer) {
  const data=JSON.parse(fixture.rawSnapshot);
  changer(data.saved[0],data);
  return JSON.stringify(data);
}
const rejects=(raw,reference=ref)=>assert.throws(
  ()=>projectReadOnlySavedProgram(raw,reference),
  /unavailable|Unprojectable|Oversized/
);
rejects(mutateProgram(p=>{p.name='Edited same-clock name'}));
rejects(mutateProgram((p,s)=>{s.tombs[p.id]=p.updatedAt}));
rejects(mutateProgram(p=>{p.updatedAt++}));
rejects(fixture.rawSnapshot,{...ref,snapshotSha256:'b'.repeat(64)});
assert.throws(()=>projectReadOnlySavedProgram(fixture.rawSnapshot,null));
const cases=[
  p=>{delete p.name},
  p=>{p.config.goal='\nunsafe'},
  p=>{p.days[0].exercises[0]={id:'bb-bench',weight:999}},
  p=>{p.days[0].exercises=['wrong id']},
  p=>{p.days[1].id=p.days[0].id},
  p=>{p.days=[...Array(8)].map((_,i)=>({id:'day-'+i,label:'Day',exercises:[]}))},
  p=>{p.weeks=105},
  p=>{p.days[0].label=''},
  p=>{p.name='Control\u0085inside'},
  p=>{p.custom='true'}
];
for(const alter of cases) {
  const updated=mutateProgram(alter);
  const fresh=deriveReadOnlyProgramReference(updated,fixture.selectedProgramId);
  rejects(updated,fresh);
}
assert.equal(JSON.stringify(JSON.parse(fixture.rawSnapshot)),fixture.rawSnapshot,
  'No canonical snapshot content may be modified during projection');
console.log('PASS synthetic v13 saved-program read-only wire parity, exact digest, tombstones, bounded days and no prescription data');
