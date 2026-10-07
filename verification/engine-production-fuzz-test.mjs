import assert from 'node:assert/strict';
import { EXERCISES, EX_BY_ID } from '../modules/App.js';
import { generateNextProgramForShell, getNextShellCell, NextShellAdapterError } from '../modules/next-engine/app-shell-adapter.js';

const fullGym=['barbell','rack','bench','dumbbell','cable','machine','smith','ezbar','pullup','dip','kettlebell','bands','legpress','hacksquat','legext','legcurl','calfmachine'];
const profiles=[
  {name:'full',equipment:fullGym,noBodyweight:false},
  {name:'db-home',equipment:['bench','dumbbell','bands'],noBodyweight:false},
  {name:'machine',equipment:['bench','cable','machine','legpress','legext','legcurl','calfmachine'],noBodyweight:true},
  {name:'barbell-basic',equipment:['barbell','rack','bench'],noBodyweight:true},
  {name:'bodyweight-bands',equipment:['bands','pullup'],noBodyweight:false}
];
const core=[
  ['full_body',3],['upper_lower',4],['ppl',5],['ulppl',5]
];
const goals=['hypertrophy','both','strength'];
const experiences=['beginner','intermediate','advanced'];
const sessions=['s40','s60','s90'];
const progressions=['auto','double','dynamic','ladder','linear','wave','e1rm'];
const units=['lb','kg'];
let state=0x2265a11;
const rand=()=>{state=(Math.imul(state,1664525)+1013904223)>>>0;return state/4294967296};
const pick=a=>a[Math.floor(rand()*a.length)];
const seed=()=>1+Math.floor(rand()*2147483646);

function configFor({split,days,profile=profiles[0],goal=pick(goals),experience=pick(experiences),session=pick(sessions),progressionStyle=pick(progressions),unit=pick(units),noSupersets=rand()<.35,barbellCap=rand()<.25?1:3,volumeApproach=rand()<.2?'minimalist':'standard'}){
  return {
    name:'Fuzz',unit,goal,experience,split,days,session,weeks:3+Math.floor(rand()*8),
    progression:'auto',progressionStyle,deload:rand()<.5,equipment:[...profile.equipment],
    focus:{},focusList:[],reduce:[],barbellCap,noBodyweight:profile.noBodyweight,noSupersets,volumeApproach
  };
}

function checkProgram(result,config,label){
  const p=result.program;
  assert.equal(result.nextProgram.audit.result,'pass',`${label}: engine audit did not pass`);
  assert.equal(p.days.length,config.days,`${label}: day count drift`);
  assert.equal(p.engineSource,'pursuit-next',`${label}: source drift`);
  assert.ok(p.nextEngine?.request&&p.nextEngine?.program,`${label}: missing engine snapshot`);
  assert.equal(p.nextEngine.program.sessions.length,config.days,`${label}: engine session count drift`);
  for(const day of p.days){
    assert.ok(day.exercises.length>0,`${label}: empty day ${day.id}`);
    assert.equal(new Set(day.exercises).size,day.exercises.length,`${label}: duplicate visible exercise in ${day.id}`);
    for(let slot=0;slot<day.exercises.length;slot++){
      const id=day.exercises[slot];
      assert.ok(EX_BY_ID[id],`${label}: unmapped shell exercise ${id}`);
      for(const week of [1,Math.max(1,Number(p.weeks||p.config?.weeks)||1)]){
        const cell=getNextShellCell(p,day,slot,week);
        assert.ok(cell,`${label}: missing cell ${day.id}/${slot}/w${week}`);
        assert.ok(Number.isFinite(Number(cell.sets))&&Number(cell.sets)>=1&&Number(cell.sets)<=20,`${label}: invalid set count`);
        assert.ok(String(cell.reps??cell.range??'').trim(),`${label}: blank reps`);
        assert.ok(String(cell.rir??'').trim(),`${label}: blank RIR`);
      }
    }
  }
  for(const s of result.nextProgram.sessions){
    assert.ok(Number.isFinite(s.estimatedMinutes)&&s.estimatedMinutes<=s.maxMinutes+.001,`${label}: time overflow ${s.estimatedMinutes}/${s.maxMinutes}`);
    for(const ex of s.exercises){
      assert.ok(Number.isInteger(ex.sets)&&ex.sets>=1&&ex.sets<=20,`${label}: invalid engine sets`);
      assert.ok(Array.isArray(ex.prescription?.reps)&&ex.prescription.reps.length===2&&ex.prescription.reps[0]>0&&ex.prescription.reps[1]>=ex.prescription.reps[0],`${label}: invalid rep prescription`);
      assert.ok(Array.isArray(ex.prescription?.rir)&&ex.prescription.rir.length===2&&ex.prescription.rir[0]>=0&&ex.prescription.rir[1]>=ex.prescription.rir[0],`${label}: invalid RIR prescription`);
    }
  }
}

const required=[];
for(let i=0;i<36;i++){
  const [split,days]=core[i%core.length];
  required.push(configFor({split,days,profile:profiles[0],session:i%2?'s60':'s90'}));
}
let built=0;
for(let i=0;i<required.length;i++){
  const config=required[i], s=226000+i;
  const result=generateNextProgramForShell({config,legacyExercises:EXERCISES,seed:s,makeId:()=>`required-${i}`});
  checkProgram(result,config,`required-${i}/${config.split}/${config.goal}/${config.experience}`);
  built++;
}
console.log(`PASS production fuzz required: ${built}/${required.length} ordinary full-gym configurations build and remain executable.`);

const stress=[];
const stressSplits=[...core,['hybrid',4],['phul',4],['full_body_patterns',4],['upper_lower_alt',4]];
for(let i=0;i<48;i++){
  const [split,days]=stressSplits[i%stressSplits.length];
  const profile=profiles[1+(i%(profiles.length-1))];
  stress.push(configFor({
    split,days,profile,
    session:sessions[i%sessions.length],
    noSupersets:i%2===0,
    barbellCap:i%3===0?0:i%3===1?1:3,
    volumeApproach:i%4===0?'minimalist':'standard'
  }));
}
let stressBuilt=0,safeRefusals=0;
for(let i=0;i<stress.length;i++){
  const config=stress[i], s=227000+i, label=`stress-${i}/${config.split}/${profiles[1+(i%(profiles.length-1))].name}`;
  try{
    const result=generateNextProgramForShell({config,legacyExercises:EXERCISES,seed:s,makeId:()=>`stress-${i}`});
    checkProgram(result,config,label);
    stressBuilt++;
  }catch(error){
    assert.ok(error instanceof NextShellAdapterError,`${label}: unexpected error type ${error?.stack??error}`);
    assert.equal(error.code,'NEXT_ENGINE_REJECTED',`${label}: unsafe/unexpected refusal ${error.code}`);
    assert.ok(error.recovery?.blockingCodes?.length||error.recovery?.suggestions?.length,`${label}: rejection lacks recovery diagnostics`);
    safeRefusals++;
  }
}
assert.ok(stressBuilt>0,'stress matrix should contain successful restricted configurations');
assert.ok(stressBuilt+safeRefusals===stress.length);
console.log(`PASS production fuzz stress: ${stressBuilt} restricted configurations built; ${safeRefusals} impossible combinations failed closed with recovery diagnostics.`);

const deterministic=configFor({split:'upper_lower',days:4,profile:profiles[0],goal:'both',experience:'intermediate',session:'s60',progressionStyle:'auto',unit:'lb',noSupersets:false,barbellCap:3,volumeApproach:'standard'});
const a=generateNextProgramForShell({config:deterministic,legacyExercises:EXERCISES,seed:228228,makeId:()=> 'deterministic'});
const b=generateNextProgramForShell({config:structuredClone(deterministic),legacyExercises:EXERCISES,seed:228228,makeId:()=> 'deterministic'});
assert.deepEqual(a.nextProgram,b.nextProgram,'same config+seed must generate identical engine programs');
assert.deepEqual(a.program.days,b.program.days,'same config+seed must generate identical shell rosters');
assert.deepEqual(a.program.nextWeekPrescriptions,b.program.nextWeekPrescriptions,'same config+seed must generate identical executable cells');
console.log('PASS production fuzz determinism: engine program, visible roster and executable cells are stable for identical inputs.');
