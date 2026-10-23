import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { CUSTOM_CYCLE_PHASES, customCycleSpecs, generateNextCycleForShell, cycleSplitCandidates, canRetargetStrengthEntryRoster } from '../modules/next-engine/cycle-runtime-adapter.js';
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
assert.match(app,/final\.cycleAdaptSplit = cycleAdaptSplit/);
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

let lockedIds=0;
const retainedRoster=generateNextCycleForShell({
 templateId:'custom', config:{...config,days:4,split:'upper_lower'},
 blocks:[{phase:'mixed_accumulation',split:'upper_lower',weeks:2},{phase:'mixed_accumulation',split:'upper_lower',weeks:3}],
 adaptBetweenBlocks:false, adaptTrainingSplit:true, legacyExercises:EXERCISES,seed:893,
 makeId:()=>`b893-locked-${++lockedIds}`
});
const exerciseOrder=b=>b.nextEngine.program.sessions.map(day=>day.exercises.map(ex=>ex.exerciseId));
assert.deepEqual(exerciseOrder(retainedRoster.blocks[1]),exerciseOrder(retainedRoster.blocks[0]),
 'automatic split adaptation must not replace exercises when the weekly split is explicitly unchanged and exercise adaptation is off');
assert.ok(retainedRoster.blocks.every(b=>b.nextEngine.program.audit.result==='pass'));
console.log('PASS B893 independence: disabling exercise adaptation preserves roster across same-split blocks.');

// M298: creation alone cannot prove a custom cycle is executable. Walk the
// authored short blocks, a schedule change, evidence-driven recovery and repeat.
const { config: lifecycleConfig, completedHistory, ids } = await import('./m265-generation-fixture.mjs');
const { advanceNextCycleForShell, repeatCycleOptions } = await import('../modules/next-engine/cycle-runtime-adapter.js');
const { analyzeShellHistoryForNextEngine } = await import('../modules/next-engine/workout-history-adapter.js');
const lifecycle = generateNextCycleForShell({
 templateId:'custom',config:lifecycleConfig,
 blocks:[{phase:'mixed_accumulation',weeks:1},
  {phase:'strength_accumulation',weeks:2,split:'full_body',days:3,priorityLift:'bench_press',frequency:2},
  {phase:'peak',weeks:1,split:'full_body',days:3}],
 legacyExercises:EXERCISES,seed:298,makeId:ids('b893-lifecycle'),adaptTrainingSplit:true,adaptBetweenBlocks:false
});
const lifecycleBefore=JSON.stringify(lifecycle);
const first=lifecycle.blocks[0],firstHistory=completedHistory(first);
assert.equal(analyzeShellHistoryForNextEngine(first,firstHistory.slice(0,-1),EXERCISES).readyForNextBlock,false,'a partial one-week block cannot skip its required work');
assert.equal(analyzeShellHistoryForNextEngine(first,firstHistory,EXERCISES).readyForNextBlock,true,'a completed one-week custom block can advance with favorable evidence');
const entered=advanceNextCycleForShell({cycle:lifecycle.cycle,activeProgram:first,history:firstHistory,legacyExercises:EXERCISES,makeId:ids('b893-enter')});
assert.equal(entered.nextProgram.id,lifecycle.blocks[1].id,'advancement replaces the reserved preview, not its identity');
assert.equal(entered.nextProgram.days.length,3);
assert.equal(entered.nextProgram.nextEngine.cycleState.minimumWorkouts,6,'review uses the actual three-day block, not the four-day cycle baseline');
assert.equal(entered.nextProgram.nextEngine.request.goal.liftFrequency.bench_press,2);
const secondHistory=completedHistory(entered.nextProgram);
const stale=structuredClone(entered.nextProgram);
stale.nextEngine.cycleState.minimumWorkouts=99;stale.nextEngine.cycleState.reviewAfterWorkouts=99;
assert.equal(analyzeShellHistoryForNextEngine(stale,secondHistory,EXERCISES).readyForNextBlock,true,'existing saved previews receive the schedule correction when read');
const final=advanceNextCycleForShell({cycle:entered.cycle,activeProgram:entered.nextProgram,history:[...firstHistory,...secondHistory],legacyExercises:EXERCISES,makeId:ids('b893-final')});
assert.equal(final.nextProgram.id,lifecycle.blocks[2].id);
const finished=advanceNextCycleForShell({cycle:final.cycle,activeProgram:final.nextProgram,history:[...firstHistory,...secondHistory,...completedHistory(final.nextProgram)],legacyExercises:EXERCISES,makeId:ids('b893-finished')});
assert.equal(finished.done,true,'the complete custom sequence can finish without inventing extra weeks');
const standalone={...first,cycleId:undefined};
assert.equal(analyzeShellHistoryForNextEngine(standalone,firstHistory,EXERCISES).readyForNextBlock,false,'standalone evidence thresholds retain their separate owner');

const tired=structuredClone(secondHistory);
tired.forEach((entry,i)=>Object.values(entry.perf).forEach(perf=>{
 perf.weight=140-i*10;perf.reps=1;
 perf.sets.forEach(set=>{set.w=perf.weight;set.r=1;set.rir=0;});
}));
const recovery=advanceNextCycleForShell({cycle:entered.cycle,activeProgram:entered.nextProgram,history:tired,legacyExercises:EXERCISES,makeId:ids('b893-recovery')});
assert.equal(recovery.insertedRecovery,true,'repeated broad failures still insert recovery rather than forcing a planned phase');
assert.equal(recovery.nextProgram.days.length,3);
assert.equal(recovery.nextProgram.config.days,3);
assert.equal(recovery.nextProgram.nextEngine.request.schedule.days.length,3,'recovery preserves the active block schedule rather than reverting to the cycle baseline');
assert.equal(recovery.nextProgram.config.split,'full_body');
assert.equal(recovery.nextProgram.nextEngine.program.audit.result,'pass');
const recoveredHistory=completedHistory(recovery.nextProgram);
assert.equal(analyzeShellHistoryForNextEngine(recovery.nextProgram,recoveredHistory.slice(0,1),EXERCISES).readyForNextBlock,false,'short fixed blocks do not bypass recovery exit evidence');
recoveredHistory.forEach((entry,i)=>Object.values(entry.perf).forEach(perf=>{perf.weight=100+i*5;perf.sets.forEach(set=>set.w=perf.weight);}));
const resumed=advanceNextCycleForShell({cycle:recovery.cycle,activeProgram:recovery.nextProgram,history:[...tired,...recoveredHistory],legacyExercises:EXERCISES,makeId:ids('b893-resumed')});
assert.equal(resumed.nextProgram.id,lifecycle.blocks[2].id,'recovery resumes the correct reserved phase despite its shifted position');
assert.equal(resumed.nextProgram.config.days,3);
const repeatInput=repeatCycleOptions(JSON.parse(JSON.stringify(recovery.cycle)));
const repeated=generateNextCycleForShell({...repeatInput,legacyExercises:EXERCISES,seed:298,makeId:ids('b893-repeated')});
assert.deepEqual(repeated.cycle.nextEngineCycle.plannedBlocks,lifecycle.cycle.nextEngineCycle.plannedBlocks,'repeat retains custom durations, order, split and lift-frequency contracts, excluding inserted recovery');
assert.equal(repeated.cycle.adaptTrainingSplit,true);
assert.equal(repeated.cycle.adaptExercises,false,'repeat retains both independent adaptation choices');
assert.notEqual(repeated.cycle.id,lifecycle.cycle.id);
assert.equal(JSON.stringify(lifecycle),lifecycleBefore,'advancing, recovery and repeat cannot mutate completed or preview training artifacts');
assert.match(app,/\.\.\.repeatCycleOptions\(cycle\)/,'the app loop submits the same tested repeat options to its worker');
console.log('PASS B893 lifecycle: short-block completion, schedule changes, persisted-state repair, recovery insertion/resume, custom repeat and immutable prior records.');


// M299: an explicit per-block schedule applies to that block alone. Once the
// override ends, the next unspecified block returns to the authored baseline,
// even with exercise adaptation turned off. Its config, engine and request must
// agree; a locked retarget must never advertise a different weekly schedule.
{
  const baseline={...config,split:'upper_lower',days:4,session:'s90'};
  const changing=[
    {phase:'hypertrophy_accumulation',weeks:2},
    {phase:'mixed_accumulation',weeks:2,split:'full_body',days:3},
    {phase:'hypertrophy_accumulation',weeks:2}
  ];
  const saved=JSON.stringify({baseline,changing});
  let serial=0;
  const built=generateNextCycleForShell({templateId:'custom',blocks:changing,config:baseline,
    legacyExercises:EXERCISES,seed:299299,makeId:()=>`m299-topology-${++serial}`,
    adaptBetweenBlocks:false,adaptTrainingSplit:false});
  assert.equal(JSON.stringify({baseline,changing}),saved,'Topological adaptation cannot mutate user inputs');
  for (const [i,expected] of [[0,{split:'upper_lower',days:4}],
    [1,{split:'full_body',days:3}],[2,{split:'upper_lower',days:4}]]) {
    const block=built.blocks[i];
    assert.equal(block.config.split,expected.split,`Block ${i+1} config split`);
    assert.equal(block.config.days,expected.days,`Block ${i+1} config days`);
    assert.equal(block.nextEngine.program.split.family,expected.split,`Block ${i+1} executable split`);
    assert.equal(block.nextEngine.program.sessions.length,expected.days,`Block ${i+1} executable days`);
    assert.equal(block.nextEngine.request.schedule.days.length,expected.days,`Block ${i+1} request days`);
    assert.equal(block.nextEngine.program.audit.result,'pass',`Block ${i+1} remains audited`);
  }
}
console.log('PASS M299: per-block topology overrides expire cleanly; config, generated sessions and request agree.');


// M300: automatic split optimization explicitly annotates even the unchanged
// split. Keep the verified same-roster volume fallback eligible in that case,
// but do not bypass requested lift practice, a different split or different days.
{
  const original={phase:'hypertrophy_accumulation',split:{family:'upper_lower'},
    sessions:Array.from({length:4},(_,i)=>({id:`prior-${i}`,exercises:[]}))};
  const request={preferences:{lockedSplit:'upper_lower'},
    schedule:{days:Array.from({length:4},(_,i)=>({id:`day-${i}`}))}};
  const candidate=cycleSplitCandidates({phase:'strength_accumulation',weeks:6},
    {split:'upper_lower',days:4},true)[0];
  assert.equal(candidate.split,'upper_lower','automatic search begins with the original split');
  assert.equal(canRetargetStrengthEntryRoster(original,request,candidate),true,
    'an explicit but unchanged automatic split does not disqualify volume rescue');
  assert.equal(canRetargetStrengthEntryRoster(original,request,{phase:'strength_accumulation',weeks:6}),true,
    'the normal unchanged path remains eligible');
  assert.equal(canRetargetStrengthEntryRoster(original,{...request,preferences:{lockedSplit:'full_body'}},candidate),false,
    'a changed weekly split requires reconstruction, not static volume rescue');
  assert.equal(canRetargetStrengthEntryRoster(original,{...request,schedule:{days:request.schedule.days.slice(1)}},candidate),false,
    'different weekly frequency requires rebuilding the schedule');
  assert.equal(canRetargetStrengthEntryRoster(original,request,{...candidate,priorityLift:'bench_press',frequency:2}),false,
    'requested new lift practice cannot be rescued by keeping the old roster');
}
console.log('PASS M300: automatic same-split strength-entry rescue is eligible without bypassing structural or lift-practice changes.');
