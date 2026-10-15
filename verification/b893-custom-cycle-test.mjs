import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { CUSTOM_CYCLE_PHASES, customCycleSpecs, generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
const app=fs.readFileSync(new URL('../modules/App.js',import.meta.url),'utf8');
const css=fs.readFileSync(new URL('../app.css',import.meta.url),'utf8');
const phases=new Set(CUSTOM_CYCLE_PHASES.map(x=>x.phase));
assert.ok(phases.has('hypertrophy_accumulation')&&phases.has('strength_accumulation')&&phases.has('peak'));
const chosen=[
  {phase:'peak',weeks:2},
  {phase:'hypertrophy_accumulation',weeks:'8'},
  {phase:'strength_accumulation',weeks:5},
  {phase:'hypertrophy_accumulation',weeks:3}
];
const original=JSON.stringify(chosen);
const result=customCycleSpecs(chosen);
assert.deepEqual(result.map(x=>x.phase),chosen.map(x=>x.phase),'Custom block order remains exact, including repeats');
assert.deepEqual(result.map(x=>x.weeks),[2,8,5,3],'Per-block duration is numeric and exact');
assert.equal(JSON.stringify(chosen),original,'Engine validation must not mutate source');
assert.throws(()=>customCycleSpecs([{phase:'peak',weeks:4}]),/2 and 8/);
assert.throws(()=>customCycleSpecs([{phase:'peak',weeks:0},{phase:'peak',weeks:3}]),/1–24/);
assert.throws(()=>customCycleSpecs([{phase:'peak',weeks:1.5},{phase:'peak',weeks:2}]),/1–24/);
assert.throws(()=>customCycleSpecs([{phase:'unsupported',weeks:4},{phase:'peak',weeks:2}]),/unsupported/);
assert.throws(()=>customCycleSpecs(Array.from({length:8},()=>({phase:'peak',weeks:24}))),/104/);
assert.match(app,/function CustomCycleComposer\(\{ blocks, onChange \}\)/);
assert.match(app,/customCycleBlocks\.map\(b => \(\{ phase: b\.phase, weeks: Number\(b\.weeks\) \}\)\)/);
assert.match(app,/templateId: tmplId, blocks: tmplId === "custom" \? config\.cycleBlocks : undefined/);
assert.match(app,/cycleTemplate !== "custom" \|\| customCycleValid/);
assert.match(app,/cycleTemplate === "custom"[\s\S]*?\["cycletype", "goal", \.\.\.CYCLE_STEP_KEYS\.slice\(1\)\]/, 'A custom cycle must ask for its goal rather than defaulting every cycle to hypertrophy');
assert.match(css,/\.wpb-custom-cycle-block/);
assert.match(css,/@media\(max-width:345px\)/);
console.log('PASS B893: engine custom-phase order, lengths, invalid-input gates and complete wizard→worker wiring.');

// Exercise generated prescriptions, not only the authoring validator.
const config=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url))).rows[3].config;
const blocks=[{phase:'peak',weeks:1},{phase:'hypertrophy_accumulation',weeks:7},{phase:'hypertrophy_accumulation',weeks:3},{phase:'foundation',weeks:2}];
for(const adaptBetweenBlocks of [false,true]){
 let id=0;
 const before=JSON.stringify({config,blocks});
 const built=generateNextCycleForShell({templateId:'custom',blocks,config,legacyExercises:EXERCISES,seed:893,makeId:()=>`b893-${++id}`,adaptBetweenBlocks});
 assert.equal(JSON.stringify({config,blocks}),before,'Cycle generation preserves caller data');
 assert.deepEqual(built.cycle.blockMeta.map(b=>({phase:b.phase,weeks:b.weeks})),blocks);
 assert.deepEqual(built.blocks.map(b=>({phase:b.nextEngine.program.phase,weeks:b.config.weeks})),blocks);
 assert.deepEqual(built.cycle.blockIds,built.blocks.map(b=>b.id));
 assert.ok(built.blocks.every((b,i)=>b.cycleId===built.cycle.id&&b.nextEngine.program.audit.result==='pass'&&b.weeks===blocks[i].weeks));
}
console.log('PASS B893 generated cycles: exact unusual phase order, repeats, 1/7/3/2-week blocks, ownership, audit and input preservation in static/adaptive modes.');
