import assert from 'node:assert/strict';
import fs from 'node:fs';
import { generateNextProgramForShell, getNextShellCell } from '../modules/next-engine/app-shell-adapter.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { ENGINE_VERSION } from '../modules/next-engine/config.js';
import { buildRuntimeSetTargets } from '../modules/next-engine/workout-runtime.js';
import { nextWorkoutSuggestionForShell } from '../modules/next-engine/workout-history-adapter.js';

const legacy=[...EXERCISE_MAP.values()].map(d=>({id:d.id,name:d.name,part:d.legacyPart||'chest',type:'compound',equip:[]}));
const equipment=['barbell','dumbbell','bench','cable','machine','smith','ezbar','pullup','dip','kettlebell','bands','legpress','hacksquat','legext','legcurl','calfmachine'];
const config={name:'Authority audit',unit:'lb',goal:'both',experience:'intermediate',split:'full_body',days:3,session:'s60',weeks:4,equipment,focus:{},reduce:[],barbellCap:3,noBodyweight:false,noSupersets:false,deload:false,progression:'auto'};
const built=generateNextProgramForShell({config,legacyExercises:legacy,seed:42,makeId:()=> 'authority-program'});
assert.equal(built.nextProgram.audit.result,'pass');
assert.equal(built.program.engineSource,'pursuit-next');
assert.equal(built.program.engineSourceVersion,ENGINE_VERSION);
assert.equal(built.program.nextEngine.program.engineVersion,ENGINE_VERSION);
assert.ok(built.program.nextEngine.request && built.program.nextEngine.baseRequest,'immutable engine request snapshots must be stored');
assert.ok(built.program.days.length===3 && built.program.days.every(d=>d.exercises.length>0));
const day=built.program.days[0];
const cell=getNextShellCell(built.program,day,0,1);
assert.ok(cell?.nextEngine);
assert.ok(Number(cell.sets)>0);
// Persisted shell state is not allowed to multiply the set count. If an old backup or interrupted
// migration leaves an array here, the shared boundary must recover the engine-authored scalar.
const corrupted=structuredClone(built.program);
const cKey=`${corrupted.days[0].id}:0`;
corrupted.nextWeekPrescriptions[cKey][1].sets=[Number(cell.sets), Number(cell.sets)+1];
assert.equal(getNextShellCell(corrupted,corrupted.days[0],0,1).sets,Number(cell.sets),'ambiguous persisted set shape must replay the immutable engine prescription');
corrupted.overrides[cKey]={...(corrupted.overrides[cKey]||{}),sets:[Number(cell.sets),Number(cell.sets)]};
assert.equal(getNextShellCell(corrupted,corrupted.days[0],0,1).sets,Number(cell.sets),'repeated override array must collapse to one scalar set count');
const runtime=buildRuntimeSetTargets({exerciseId:built.program.nextEngine.program.sessions[0].exercises[0].exerciseId,cell,loadingInventory:built.program.nextEngine.request.equipment.loading,equipmentAvailable:built.program.nextEngine.request.equipment.available,includeWarmups:false,workingLoad:null});
assert.equal(runtime.filter(x=>x.kind==='work').length,Number(cell.sets),'workout runtime must realize the engine-owned set count exactly');

// The same engine-owned cell must also drive the cross-session decision. Bottom-of-range work is the
// starting point for double progression, not permission to add load; top-of-range work across every
// prescribed set earns the next load step. This proves generation -> shell cell -> performed history ->
// Pursuit Engine evaluator is one coherent route rather than a UI-only label.
const reps=String(cell.reps).split('-').map(Number);
const repLo=reps[0], repHi=reps[1] ?? reps[0];
const rirLo=Number(String(cell.rir).split('-')[0]) || 2;
const legacyId=day.exercises[0];
const mkHistory=(rep,date)=>[{id:`h-${rep}`,date,programId:built.program.id,programName:built.program.name,dayId:day.id,dayLabel:day.label,weekIndex:1,unit:'lb',perf:{[legacyId]:{weight:100,reps:rep,sets:Array.from({length:Number(cell.sets)},()=>({w:100,r:rep,rir:rirLo,tr:rirLo,done:true}))}}}];
const baselineDecision=nextWorkoutSuggestionForShell(built.program,mkHistory(repLo,1000),legacy,day,0,1);
assert.equal(baselineDecision?.action,'add_reps','bottom of a rep range must build reps before load');
assert.equal(baselineDecision?.weight,100);
const topDecision=nextWorkoutSuggestionForShell(built.program,mkHistory(repHi,2000),legacy,day,0,1);
assert.equal(topDecision?.action,'increase_load','top of range across every prescribed set must earn load progression');
assert.ok(Number(topDecision?.weight)>100);
const app=fs.readFileSync(new URL('../modules/App.js',import.meta.url),'utf8');
for(const marker of [
 'const GENERATION_ROUTE = "pursuit-next-only"',
 'generateNextProgramForShell({ config',
 'generateNextCycleForShell({',
 'nextWorkoutSuggestionForShell(program, history',
 'buildRuntimeSetTargets({',
 'M46: no scheme-specific legacy state mutation. Completed-set history is the only progression input.'
]) assert.ok(app.includes(marker),`missing authority boundary marker: ${marker}`);
console.log(`PASS engine authority: generated/audited by Pursuit Engine ${ENGINE_VERSION}, shell snapshot preserved, runtime realizes the engine cell, bottom-range work builds reps, top-range work earns load, and creation/cycle/progression routes remain Next-owned.`);
