import assert from 'node:assert/strict';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { generateNextProgramForShell, splitBuildability } from '../modules/next-engine/app-shell-adapter.js';
import { generateNextCycleForShell, nextCycleTemplatesForShell } from '../modules/next-engine/cycle-runtime-adapter.js';

const EQ = {
  full: ['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'],
  db: ['bench','dumbbell','pullup_bar','bodyweight'],
  machine: ['bench','cable','machine','leg_press','bodyweight'],
  barbell: ['barbell','rack','bench','pullup_bar','bodyweight']
};
const legacyExercises = [...EXERCISE_MAP.values()].map(def => ({
  id:def.id,name:def.name,equip:Array.isArray(def.equipment)?[...def.equipment]:[],
  part:Object.entries(def.muscles??{}).sort((a,b)=>(b[1]?.credit??0)-(a[1]?.credit??0))[0]?.[0]??'other',
  type:def.flags?.compound?'compound':'isolation',pattern:def.movementFamily
}));
const base = {
  name:'M198 creation audit',experience:'intermediate',goal:'both',split:'full_body',days:3,session:'s90',weeks:6,
  equipment:EQ.full,focus:{},focusList:[],reduce:[],progression:'auto',progressionStyle:'auto',deload:true,
  barbellCap:3,percentScheme:null,volumeApproach:'standard',noSupersets:false,noBodyweight:false,unit:'lb'
};
const templateGoal=new Map(nextCycleTemplatesForShell().map(t=>[t.id,t.goal]));
const programCases=[
 ['FB2 s60',{split:'full_body',days:2,session:'s60'}],['FB3 s60',{split:'full_body',days:3,session:'s60'}],
 ['FB3 s90',{split:'full_body',days:3,session:'s90'}],['FB3 s120',{split:'full_body',days:3,session:'s120'}],
 ['FB4 s90',{split:'full_body',days:4,session:'s90'}],['FB5 s90',{split:'full_body',days:5,session:'s90'}],
 ['FB6 s90',{split:'full_body',days:6,session:'s90'}],['UL2 s60',{split:'upper_lower',days:2,session:'s60'}],
 ['UL4 s90',{split:'upper_lower',days:4,session:'s90'}],['UL6 s90',{split:'upper_lower',days:6,session:'s90'}],
 ['PPL3 s60',{split:'ppl',days:3,session:'s60'}],['PPL5 s90',{split:'ppl',days:5,session:'s90'}],
 ['PPL6 s90',{split:'ppl',days:6,session:'s90'}],['ULPPL5 s90',{split:'ulppl',days:5,session:'s90'}],
 ['PPLUL5 s90',{split:'pplul',days:5,session:'s90'}],['Hybrid5 s90',{split:'hybrid',days:5,session:'s90'}],
 ['PHUL4 s90',{split:'phul',days:4,session:'s90'}],['PHAT5 s90',{split:'phat',days:5,session:'s90'}],
 ['Bro5 s90',{split:'bro',days:5,session:'s90'}],['Arnold6 s90',{split:'arnold',days:6,session:'s90'}],
 ['Patterns4 s90',{split:'full_body_patterns',days:4,session:'s90'}],['TorsoLimbs4 s90',{split:'torso_limbs',days:4,session:'s90'}],
 ['PPLA4 s90',{split:'ppla',days:4,session:'s90'}],['ULA5 s90',{split:'ula',days:5,session:'s90'}],
 ['Glute4 s90',{split:'glute_focus',days:4,session:'s90'}],['StrengthFB3 s90',{split:'strength_fb',days:3,session:'s90',goal:'strength'}],
 ['ULPPL hypertrophy',{split:'ulppl',days:5,session:'s90',goal:'hypertrophy'}],['ULPPL strength',{split:'ulppl',days:5,session:'s90',goal:'strength'}],
 ['FB beginner',{split:'full_body',days:3,session:'s90',experience:'beginner'}],['FB advanced',{split:'full_body',days:3,session:'s90',experience:'advanced'}],
 ['FB no supersets',{split:'full_body',days:3,session:'s90',noSupersets:true}],['UL no bodyweight',{split:'upper_lower',days:4,session:'s90',noBodyweight:true}],
 ['ULPPL barbell cap 1',{split:'ulppl',days:5,session:'s90',barbellCap:1}],['PPL minimalist',{split:'ppl',days:5,session:'s90',volumeApproach:'minimalist'}],
 ['FB dumbbell gym',{split:'full_body',days:3,session:'s60',equipment:EQ.db,barbellCap:null}],['FB machine gym',{split:'full_body',days:3,session:'s60',equipment:EQ.machine,barbellCap:null}],
 ['StrengthFB barbell gym',{split:'strength_fb',days:3,session:'s90',goal:'strength',equipment:EQ.barbell,barbellCap:3}]
];
const cycleCases=[
 ['Powerbuilding FB3 s60','powerbuilding',{split:'full_body',days:3,session:'s60'}],['Powerbuilding FB3 s90','powerbuilding',{split:'full_body',days:3,session:'s90'}],
 ['Powerbuilding FB5 s90','powerbuilding',{split:'full_body',days:5,session:'s90'}],['Powerbuilding FB3 s120','powerbuilding',{split:'full_body',days:3,session:'s120'}],
 ['Powerbuilding ULPPL5','powerbuilding',{split:'ulppl',days:5,session:'s90'}],['Powerbuilding UL4','powerbuilding',{split:'upper_lower',days:4,session:'s90'}],
 ['Powerbuilding PPL5','powerbuilding',{split:'ppl',days:5,session:'s90'}],['Powerbuilding minimalist','powerbuilding',{split:'ulppl',days:5,session:'s90',volumeApproach:'minimalist'}],
 ['Powerbuilding no supersets','powerbuilding',{split:'upper_lower',days:4,session:'s90',noSupersets:true}],
 ['Strength Peak FB3','strength_peak',{split:'full_body',days:3,session:'s90'}],['Strength Peak UL4','strength_peak',{split:'upper_lower',days:4,session:'s90'}],
 ['Strength Peak PPL5','strength_peak',{split:'ppl',days:5,session:'s90'}],
 ['Hypertrophy ULPPL5','hypertrophy_spec',{split:'ulppl',days:5,session:'s90'}],['Hypertrophy PPL5','hypertrophy_spec',{split:'ppl',days:5,session:'s90'}],
 ['Hypertrophy FB3','hypertrophy_spec',{split:'full_body',days:3,session:'s90'}],
 ['Foundation FB3 beginner','foundation',{split:'full_body',days:3,session:'s90',experience:'beginner'}],['Foundation UL4 beginner','foundation',{split:'upper_lower',days:4,session:'s90',experience:'beginner'}]
];
const rows=[],failures=[];
const summarizeError=err=>({code:err?.code??err?.name??'Error',message:String(err?.message??err),recovery:err?.recovery?.findings?.slice?.(0,4)?.map?.(f=>`${f.severity}:${f.code}`)});
for(let i=0;i<programCases.length;i++){
 const [label,patch]=programCases[i],cfg={...base,...patch,name:`Audit ${label}`},verdict=splitBuildability(cfg,legacyExercises);let built=null,error=null;
 try{built=generateNextProgramForShell({config:cfg,banned:[],legacyExercises,seed:19800+i});}catch(err){error=summarizeError(err);}
 const pass=!!built&&built.nextProgram?.audit?.result==='pass';rows.push({kind:'program',label,wizard:verdict.ok,built:pass,error});
 if(verdict.ok&&!pass)failures.push(`PROGRAM CONTRACT MISMATCH: ${label} enabled→reject ${error?.code}: ${error?.message}`);
}
for(let i=0;i<cycleCases.length;i++){
 const [label,templateId,patch]=cycleCases[i],goal=templateGoal.get(templateId)??'both',cfg={...base,...patch,goal,name:`Audit ${label}`},verdict=splitBuildability(cfg,legacyExercises);
 for(const adaptBetweenBlocks of [false,true]){let built=null,error=null;try{built=generateNextCycleForShell({templateId,config:cfg,banned:[],legacyExercises,seed:19900+i*2+(adaptBetweenBlocks?1:0),adaptBetweenBlocks,makeId:(()=>{let n=0;return()=>`audit-${i}-${adaptBetweenBlocks?'a':'l'}-${++n}`;})()});}catch(err){error=summarizeError(err);}
  const blocks=built?.blocks??[],pass=blocks.length>0&&blocks.every(b=>b?.nextEngine?.program?.audit?.result==='pass');rows.push({kind:'cycle',label,adapt:adaptBetweenBlocks,wizard:verdict.ok,built:pass,blocks:blocks.length,error});
  if(verdict.ok&&!pass)failures.push(`CYCLE CONTRACT MISMATCH: ${label} ${adaptBetweenBlocks?'adaptive':'locked'} enabled→reject ${error?.code}: ${error?.message}`);
 }
}
const monotonic=[['FB3','full_body',3],['FB5','full_body',5],['UL4','upper_lower',4],['PPL5','ppl',5],['ULPPL5','ulppl',5]];
for(const [label,split,days] of monotonic){let seenBuildable=false;for(const session of ['s60','s90','s120']){const verdict=splitBuildability({...base,split,days,session},legacyExercises);if(seenBuildable&&!verdict.ok)failures.push(`NON-MONOTONIC TIME CAPACITY: ${label} builds shorter but ${session} is blocked (${verdict.kind??'unknown'})`);seenBuildable||=verdict.ok;}}
console.table(rows.map(r=>({kind:r.kind,case:r.label+(r.kind==='cycle'?` ${r.adapt?'adaptive':'locked'}`:''),wizard:r.wizard?'enabled':'blocked',final:r.built?'PASS':'FAIL',code:r.error?.code??''})));
const enabled=rows.filter(r=>r.wizard).length,mismatches=rows.filter(r=>r.wizard&&!r.built).length;
console.log(`M198 creation audit: ${rows.length} routes; ${enabled} wizard-enabled; ${mismatches} enabled→build mismatches; ${monotonic.length} time-capacity ladders.`);
if(failures.length){console.error('\n'+failures.join('\n'));throw new Error(`M198 creation audit found ${failures.length} contract failures.`);}
assert.ok(rows.some(r=>r.kind==='program'&&r.label==='ULPPL5 s90'&&r.built),'ULPPL 60–90 must build');
assert.ok(rows.some(r=>r.kind==='cycle'&&r.label==='Powerbuilding FB5 s90'&&r.adapt&&r.built),'Adaptive Full Body 5-day 60–90 powerbuilding must build');
console.log('M198 creation audit: pass');
// Post-structural-repair verification trigger.
