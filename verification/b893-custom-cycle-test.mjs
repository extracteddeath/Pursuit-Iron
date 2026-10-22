import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { CUSTOM_CYCLE_PHASES, customCycleSpecs, generateNextCycleForShell, cycleSplitCandidates } from '../modules/next-engine/cycle-runtime-adapter.js';
import { createStrengthClaims, normalizeRequest } from '../modules/next-engine/prescription.js';
import { shellConfigToNextRequest } from '../modules/next-engine/app-shell-adapter.js';
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
assert.match(app,/function CustomCycleComposer\(\{ blocks, onChange/);
assert.match(app,/final\.cycleBlocks = customCycleBlocks\.map\(b => \(\{ \.\.\.b, weeks: Number\(b\.weeks\)/);
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

const structured=customCycleSpecs([
 {phase:'hypertrophy_accumulation',weeks:6},
 {phase:'strength_accumulation',weeks:5,split:'upper_lower',days:4,priorityLift:'bench_press',frequency:2}
]);
assert.deepEqual(structured[1],{phase:'strength_accumulation',weeks:5,label:'Strength',split:'upper_lower',days:4,priorityLift:'bench_press',frequency:2});
assert.throws(()=>customCycleSpecs([{phase:'peak',weeks:2},{phase:'strength_accumulation',weeks:5,split:'unreal_split'}]),/split/);
assert.throws(()=>customCycleSpecs([{phase:'peak',weeks:2},{phase:'strength_accumulation',weeks:5,days:2,priorityLift:'bench_press',frequency:3}]),/exposures/);
const req=shellConfigToNextRequest(config,[],EXERCISES,893);
req.goal={...req.goal,liftPriorities:{bench_press:'primary'},liftFrequency:{bench_press:3}};
const normalized=normalizeRequest(req);
assert.equal(normalized.goal.liftFrequency.bench_press,3);
assert.equal(createStrengthClaims(normalized,'strength_accumulation').filter(c=>c.lift==='bench_press'&&c.required).length,3);
assert.throws(()=>normalizeRequest({...req,goal:{...req.goal,liftFrequency:{bench_press:6}}}),/frequency/);
assert.match(app,/Block \$\{i\+1\+\(existingWeeks \? 1:0\)\} weekly split/);
assert.match(app,/Weekly exposures/);
let structuredId=0;
const structuredResult=generateNextCycleForShell({
 templateId:'custom',blocks:structured,config,legacyExercises:EXERCISES,seed:893,
 makeId:()=>`b893-structure-${++structuredId}`,adaptBetweenBlocks:false
});
assert.equal(structuredResult.blocks[1].config.split,'upper_lower','strength block changes weekly split');
assert.equal(structuredResult.blocks[1].config.days,4,'strength block owns weekly schedule');
assert.equal(structuredResult.cycle.nextEngineCycle.plannedBlocks[1].frequency,2);
const practiceDays=structuredResult.blocks[1].nextEngine.program.sessions.filter(session=>session.exercises.some(ex=>
 ['primary_strength','secondary_strength'].includes(ex.role)
 && (ex.exerciseId==='bb-bench' || /bench/i.test(ex.name)))).length;
assert.ok(practiceDays>=2,'bench receives repeated weekly strength exposures');
console.log('PASS B893 structure: explicit split/day change, 3-exposure request policy and two-day bench contract.');

const automaticCandidates=cycleSplitCandidates({phase:'strength_accumulation',weeks:4},{split:'ulppl',days:5},true);
assert.ok(automaticCandidates.length>=2,'automatic split mode considers multiple compatible structures');
assert.ok(automaticCandidates.every(c=>c.split&&c.phase==='strength_accumulation'),'candidate enumeration preserves the phase');
assert.deepEqual(cycleSplitCandidates({phase:'strength_accumulation',split:'ulppl',weeks:4},{split:'full_body',days:5},true),
 [{phase:'strength_accumulation',split:'ulppl',weeks:4}],'manual block split overrides automation');
assert.deepEqual(cycleSplitCandidates({phase:'strength_accumulation',weeks:4},{split:'ulppl',days:5},false),
 [{phase:'strength_accumulation',weeks:4}],'disabling split adaptation keeps the original plan');
assert.match(app,/function CycleAdaptSplitChoice\(/);
assert.match(app,/cycleAdaptSplit: !!config\.cycleAdaptSplit/);
assert.match(app,/adaptTrainingSplit: !!config\.cycleAdaptSplit/);
let optId=0;
const optimized=generateNextCycleForShell({
 templateId:'custom',config:{...config,days:5,split:'ulppl'},legacyExercises:EXERCISES,seed:893,
 blocks:[{phase:'mixed_accumulation',weeks:3},{phase:'strength_accumulation',weeks:4}],
 adaptTrainingSplit:true,adaptBetweenBlocks:true,makeId:()=>`b893-opt-${++optId}`
});
assert.equal(optimized.cycle.adaptTrainingSplit,true);
assert.equal(optimized.cycle.nextEngineCycle.adaptTrainingSplit,true);
for(const [index,block] of optimized.blocks.entries()){
 const decision=optimized.cycle.blockMeta[index].splitDecision;
 assert.equal(decision.mode,'goal_optimized');
 assert.ok(decision.candidateCount>=2&&decision.passingCandidates>=1);
 assert.equal(block.config.split,decision.selectedSplit,'selected split propagates into the executable block');
 assert.equal(block.nextEngine.program.split.family,decision.selectedSplit,'generator and app display the same structure');
 assert.equal(block.nextEngine.program.audit.result,'pass','the winning structure passes every hard safety audit');
 assert.equal(block.nextEngine.cycleTemplate.splitDecision.selectedSplit,decision.selectedSplit);
 assert.equal(block.config.days,5,'adaptation never changes requested weekly training days');
}
console.log('PASS B893 automatic topology: separate split/exercise switches, audited phase-aware structure selection and exact preview metadata.');
