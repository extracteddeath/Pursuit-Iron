import assert from 'node:assert/strict';
import fs from 'node:fs';
import { performance } from 'node:perf_hooks';
import { EXERCISES } from '../modules/engine-api.js';

import { generateNextProgramForShell, nextProgramToShellProgram } from '../modules/next-engine/app-shell-adapter.js';
import { canonicalShellSetCount, range } from '../modules/next-engine/shell-projection.js';
import { prescriptionForSimulationWeek } from '../modules/next-engine/simulation.js';

// M317: deterministic old-loop vs shared-session weekly projection coverage.
// Device speed varies, so only call-count/parity is a hard gate; timing is a
// diagnostic, not a fabricated phone benchmark or a flaky speed threshold.
const config=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url))).rows[3].config;
const built=generateNextProgramForShell({
 config:{...config,name:'M317 weekly projection cost',weeks:5},
 legacyExercises:EXERCISES,seed:31731,makeId:()=> 'm317-source'
});
const engine=built.nextProgram;
assert.equal(engine.audit.result,'pass');
const experience=config.experience==='none'||config.experience==='beginner'?'novice'
 :config.experience==='advanced'?'advanced':'intermediate';
const projection={experience};
const totalSlots=engine.sessions.reduce((n,s)=>n+s.exercises.length,0);
assert.ok(totalSlots>engine.sessions.length*2,'Measure real multi-exercise sessions');
const summaries=[];
for(const {weeks,deload,manual} of [
 {weeks:5,deload:false,manual:false},
 {weeks:10,deload:true,manual:false},
 {weeks:24,deload:true,manual:true}
]){
 const cfg={...built.program.config,weeks,deload,progression:manual?'manual':'auto'};
 const t0=performance.now();
 const result=nextProgramToShellProgram(engine,cfg,EXERCISES,()=> 'm317-'+weeks);
 const realMs=performance.now()-t0;
 let oldCalls=0, sharedCalls=0, oldChecksum=0, sharedChecksum=0;
 const oldStart=performance.now();
 for(const session of engine.sessions)for(let slot=0;slot<session.exercises.length;slot++){
  for(let week=1;week<=weeks;week++){
   const cell=prescriptionForSimulationWeek(session,engine.phase,week,weeks,projection).exercises[slot];
   oldCalls++;oldChecksum+=canonicalShellSetCount(cell.sets);
  }
  if(deload){
   const cell=prescriptionForSimulationWeek(session,'recovery',1,1,projection).exercises[slot];
   oldCalls++;oldChecksum+=canonicalShellSetCount(cell.sets);
  }
 }
 const oldMs=performance.now()-oldStart;
 const batchedStart=performance.now();
 for(const [dayIndex,session] of engine.sessions.entries()){
  const dayId=`next-${dayIndex+1}-${session.day}`;
  for(let week=1;week<=weeks+(deload?1:0);week++){
   const cellList=week>weeks
    ?prescriptionForSimulationWeek(session,'recovery',1,1,projection).exercises
    :prescriptionForSimulationWeek(session,engine.phase,week,weeks,projection).exercises;
   sharedCalls++;
   assert.equal(cellList.length,session.exercises.length);
   for(let slot=0;slot<cellList.length;slot++){
    const cell=cellList[slot],key=`${dayId}:${slot}`,saved=result.nextWeekPrescriptions[key][week];
    assert.ok(saved,`Missing executable week ${week}, day ${dayId}, slot ${slot}`);
    assert.equal(saved.sets,canonicalShellSetCount(cell.sets));
    assert.equal(saved.reps,range(cell.prescription.reps));
    assert.equal(saved.rir,range(cell.prescription.rir));
    assert.equal(saved.rest,cell.prescription.restSeconds);
    assert.equal(saved.role,cell.role);
    assert.equal(saved.tech,week>weeks?null:saved.tech,'Recovery must not retain intensity techniques');
    sharedChecksum+=saved.sets;
   }
  }
 }
 const sharedMs=performance.now()-batchedStart;
 assert.equal(sharedChecksum,oldChecksum,'Batched and per-exercise simulation must produce exactly the same sets');
 assert.equal(oldCalls,totalSlots*(weeks+(deload?1:0)));
 assert.equal(sharedCalls,engine.sessions.length*(weeks+(deload?1:0)));
 assert.ok(oldCalls>sharedCalls*2,'M317 projection must eliminate duplicate session-level simulation calls');
 assert.equal(Object.keys(result.nextWeekPrescriptions).length,totalSlots);
 if(manual)assert.ok(Object.values(result.overrides).every(o=>o.prescriptionOwners?.sets==='user'),
  'Manual set/reps/RIR ownership stays intact');
 else assert.ok(Object.values(result.overrides).every(o=>!o.prescriptionOwners),
  'Automatic prescription ownership stays with the weekly engine');
 summaries.push({weeks,deload,manual,days:engine.sessions.length,slots:totalSlots,
  naiveSimulationCalls:oldCalls,sharedSimulationCalls:sharedCalls,
  fewerSimulationsPercent:Math.round((1-sharedCalls/oldCalls)*1000)/10,
  oldLoopMs:Math.round(oldMs*10)/10,sharedLoopMs:Math.round(sharedMs*10)/10,
  fullShellProjectionMs:Math.round(realMs*10)/10});
}
console.log('M317 projection benchmark, Node CI reference (NOT actual phone): '+JSON.stringify(summaries));
console.log('PASS M317: 5-, 10- and 24-week, recovery and manual/auto projection parity with fewer simulations.');
