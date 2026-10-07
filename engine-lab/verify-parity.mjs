import assert from 'node:assert/strict';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { register } from 'node:module';
register('../verification/import-loader.mjs', import.meta.url);
const production=await import('../modules/App.js');
const productionEngine=await import('../modules/next-engine/app-shell-adapter.js');
const standalone=await import(pathToFileURL(path.resolve(process.argv[2] || '../pursuit-iron-engine','index.js')));
const isolated=standalone.shell;
assert.deepEqual(isolated.EXERCISES,production.EXERCISES);
assert.deepEqual(isolated.TEMPLATES,production.TEMPLATES);
const equip=['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
let cells=0;
// Program metadata uses Date.now(); hold only the test clock so sequential exports are comparable.
const originalNow=Date.now;
Date.now=()=>1791348518000;
try {
for (const [split,days] of [['full_body',3],['upper_lower',4],['ulppl',5]]) {
  const config={name:'Export parity',unit:'lb',goal:'both',experience:'intermediate',split,days,session:'s60',weeks:6,progression:'auto',deload:false,equipment:equip,focus:{},reduce:[],barbellCap:3,noBodyweight:false,noSupersets:false};
  const p=standalone.generateNextProgramForShell({config,legacyExercises:production.EXERCISES,seed:223,makeId:()=>split}).program;
  const q=productionEngine.generateNextProgramForShell({config,legacyExercises:production.EXERCISES,seed:223,makeId:()=>split}).program;
  assert.deepEqual(q,p);
  for (const day of p.days) for(let slot=0;slot<day.exercises.length;slot++) for(const week of [1,3,6]) {
    const id=day.exercises[slot];
    assert.deepEqual(isolated.computeCell(p,day,id,slot,week),production.computeCell(p,day,id,slot,week));cells++;
  }
  assert.deepEqual(isolated.weeklyVolume(p),production.weeklyVolume(p));
  assert.deepEqual(isolated.buildWeekPlan(p),production.buildWeekPlan(p));
}
} finally { Date.now=originalNow; }
console.log(`PASS export/application parity: exact catalogs/templates, three representative splits, ${cells} prescription cells, weekly volume and week plans.`);
