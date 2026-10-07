import { productionSource } from './production-source.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { canonicalShellSetCount, getNextShellCell, markUserPrescriptionOverride } from '../modules/next-engine/app-shell-adapter.js';

let passed=0;
const check=(name,fn)=>{fn(); console.log('PASS '+name); passed++;};

check('scalar set counts stay scalar',()=>assert.equal(canonicalShellSetCount(3),3));
check('repeated persisted set arrays collapse instead of concatenating',()=>assert.equal(canonicalShellSetCount([3,3]),3));
check('ambiguous set arrays prefer engine fallback',()=>assert.equal(canonicalShellSetCount([3,4],5),5));
check('malformed set counts cannot create a dose explosion',()=>assert.equal(canonicalShellSetCount(999),20));

const program={
  id:'set-shape',engineSource:'pursuit-next',engineSourceVersion:'0.62.5',config:{weeks:4,deload:false},
  days:[{id:'d1',label:'Day 1',exercises:['x']}],
  overrides:{},
  nextWeekPrescriptions:{'d1:0':{1:{sets:[3,3],reps:'8-12',rir:'2',rest:120}}}
};
let cell=getNextShellCell(program,program.days[0],0,1);
check('shared Next shell cell repairs repeated-array set count',()=>assert.equal(cell.sets,3));
program.overrides['d1:0']={sets:[4,4]};
cell=getNextShellCell(program,program.days[0],0,1);
check('unowned stale shell set mirror cannot replace Engine prescription',()=>assert.equal(cell.sets,3));
program.overrides['d1:0']=markUserPrescriptionOverride({},'sets',[4,4]);
cell=getNextShellCell(program,program.days[0],0,1);
check('explicit user-owned shell override is normalized at the same boundary',()=>assert.equal(cell.sets,4));
program.overrides['d1:0']=markUserPrescriptionOverride({},'sets',[4,5]);
cell=getNextShellCell(program,program.days[0],0,1);
check('ambiguous user override stays one scalar and falls back to Engine, never 45 or 9',()=>assert.equal(cell.sets,3));

const app=productionSource();
check('all generated program surfaces still enter through getNextShellCell',()=>assert.ok(app.includes('const nextCell = getNextShellCell(program, day, slotIndex, weekIndex);')));
check('legacy custom programs restore authored schedules and saved dose deltas before display',()=>assert.ok(app.includes('sets: legacyCustomSetCount(program, day, ex, slotIndex, weekIndex, o.sets, base.sets)')));
check('phone set row keeps protected 520px breakpoint',()=>assert.ok(app.includes('const narrowSet = useNarrow(520);')));
check('SET/LAST header has breathing room',()=>{
  assert.ok(app.includes('gap: narrowSet ? 5 : 8'));
  assert.ok(app.includes('width: narrowSet ? 24 : 26 }, children: "SET"'));
  assert.ok(app.includes('width: narrowSet ? 60 : 82'));
});
const sharedStart=app.indexOf('// ── Shared TARGET / LAST column');
const sharedEnd=app.indexOf('// TARGET mode:',sharedStart);
const lastBlock=app.slice(sharedStart,sharedEnd);
check('LAST reference does not duplicate RIR',()=>{
  assert.ok(sharedStart>0 && sharedEnd>sharedStart);
  assert.ok(!lastBlock.includes('shownRir'));
  assert.ok(!lastBlock.includes('effortLabel('));
  assert.ok(lastBlock.includes('Keep the compact LAST reference to load × reps only'));
});
check('cycle opening-dose preview reads 1-based week one',()=>assert.ok(app.includes('map(weeks => weeks?.[1] ?? weeks?.["1"])')));

console.log(`${passed} set-display-integrity checks passed.`);
