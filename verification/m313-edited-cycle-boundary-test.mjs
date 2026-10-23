import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { getNextShellCell } from '../modules/engine-api.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { convertProgramToNextCycleForShell, advanceNextCycleForShell, describeRegionalWorkingWeekChange } from '../modules/next-engine/cycle-runtime-adapter.js';
import { upgradeExistingCycleToAdaptive } from '../modules/next-engine/adaptive-cycle-upgrade.js';
import { captureShellVolumeSnapshot } from '../modules/next-engine/volume-repair.js';
import { completedHistory, ids } from './m265-generation-fixture.mjs';
import { analyzeShellHistoryForNextEngine } from '../modules/next-engine/workout-history-adapter.js';

// M313: previously tested edits happened BEFORE conversion. These edits happen
// AFTER the standalone program is converted and its strength preview exists.
// The next-block worker must read the actual saved owner-edited program.
const config=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url))).rows[3].config;
const program=generateNextProgramForShell({
  config:{...config,name:'M313 edited custom conversion',goal:'hypertrophy',weeks:4},
  legacyExercises:EXERCISES,seed:31351,makeId:ids('m313-source')
}).program;
program.custom=true;
const baseline=JSON.stringify(program);
const created=convertProgramToNextCycleForShell({
  program,templateId:'custom',blocks:[{phase:'strength_accumulation',weeks:4}],
  legacyExercises:EXERCISES,seed:31352,makeId:ids('m313-preview'),
  adaptBetweenBlocks:true,adaptTrainingSplit:false
});
const upgraded=upgradeExistingCycleToAdaptive(created.cycle,created.allBlocks,[],313000).cycle;
assert.equal(upgraded.adaptTrainingSplit,true);
assert.equal(JSON.stringify(program),baseline,'Neither conversion nor opt-in may edit the source');
assert.equal(created.currentProgram.id,program.id);
assert.equal(created.blocks[0].id,upgraded.blockIds[1]);

const total=view=>view.sessions.reduce((n,s)=>n+s.exercises.reduce((v,e)=>v+e.sets,0),0);
const snapshot=(p,week)=>{
  const view=captureShellVolumeSnapshot(p,week,EXERCISES);
  assert.ok(view&&!view.missing,'Every user-edited and adapted workout cell must be readable');
  return view;
};
const withManualSets=(source,extreme)=>{
  const edited=structuredClone(source), day=edited.days[0];
  assert.ok(day.exercises.length>=2);
  edited.overrides={...(edited.overrides||{})};
  for(const [slot] of day.exercises.entries()){
    if(!extreme && slot>1)continue;
    const key=`${day.id}:${slot}`;
    const current=Number(getNextShellCell(source,day,slot,source.config.weeks).sets);
    const selected=extreme?Math.max(6,Math.ceil(32/day.exercises.length)):current+1;
    edited.overrides[key]={
      ...(edited.overrides[key]||{}),sets:selected,
      prescriptionOwners:{...(edited.overrides[key]?.prescriptionOwners||{}),sets:'user'}
    };
  }
  return edited;
};

for(const extreme of [false,true]){
  const label=extreme?'30+ set custom day':'moderate custom set edits';
  const active=withManualSets(created.currentProgram,extreme);
  const first=snapshot(active,1),last=snapshot(active,active.config.weeks);
  assert.ok(total(last)>total(snapshot(created.currentProgram,active.config.weeks)),
    'Manual set edits must increase the actual executable work rather than metadata alone');
  if(extreme)assert.ok(last.sessions[0].exercises.reduce((n,e)=>n+e.sets,0)>=30);
  const history=completedHistory(active);
  assert.equal(analyzeShellHistoryForNextEngine(active,history,EXERCISES).readyForNextBlock,true,
    'Completed edited-program workouts must be eligible for the actual next-block worker');
  const before=JSON.stringify({active,history,upgraded,created});
  let advanced,refused;
  try{
    advanced=advanceNextCycleForShell({
      cycle:upgraded,activeProgram:active,history,
      legacyExercises:EXERCISES,makeId:ids('m313-next-'+(extreme?'high':'normal'))
    });
  }catch(error){refused=error;}
  assert.equal(JSON.stringify({active,history,upgraded,created}),before,
    'Successful and refused advancement must never mutate user-owned sets, saved cycle, history or preview');
  if(!extreme)assert.ifError(refused);
  if(advanced){
    assert.equal(advanced.insertedRecovery,false,'Successful edited work should not invent a recovery phase');
    assert.equal(advanced.nextProgram.id,created.blocks[0].id,'Adaptation must retain preview identity');
    assert.equal(advanced.nextProgram.nextEngine.program.audit.result,'pass');
    assert.equal(advanced.nextProgram.nextEngine.cycleTemplate.splitDecision.mode,'goal_optimized');
    assert.equal(advanced.nextProgram.nextEngine.priorBlock.programId,active.id);
    assert.equal(advanced.nextProgram.nextEngine.priorBlock.workouts,history.length);
    const after=snapshot(advanced.nextProgram,1);
    assert.ok(total(after)>=Math.ceil(total(last)*.5),
      `${label}: executable strength week fell from ${total(last)} to ${total(after)} sets`);
    assert.ok(after.sessions.every(s=>s.estimatedMinutes<=s.maxMinutes+.01),
      'Every strength workout must fit its own clock');
    const described=describeRegionalWorkingWeekChange(active,advanced.nextProgram,EXERCISES);
    assert.equal(described.previousWeeklySets,total(last));
    assert.equal(described.currentWeeklySets,total(after));
    assert.deepEqual(advanced.cycle.blockMeta[1].splitDecision.workingWeekVolumeChange,described);
    console.log(`M313 ${label} accepted: ${total(last)} -> ${total(after)} executable weekly sets`);
  }else{
    assert.ok(extreme,'Feasible moderate manual set edits should remain convertible and adaptive');
    assert.ok(refused?.code?.startsWith('NEXT_'),
      'Unsafe user-edited programs must fail with an explicit engine error, not silently fall back or crash');
    assert.notEqual(refused.code,'NEXT_CYCLE_NOT_READY');
    assert.match(String(refused.message),/strength|time|capacity|volume|session|training|split|cycle|weekly|recovery|schedule/i);
    console.log('M313 extreme custom day safely refused: '+refused.code+' / '+refused.message);
  }
}
console.log('PASS M313: saved post-conversion manual sets are read at advancement, with audited continuity or explicit non-mutating refusal.');