import fs from 'node:fs';
import { performance } from 'node:perf_hooks';
import * as base from '../modules/next-engine/app-shell-adapter.js';
import * as capacity from '../modules/next-engine/app-shell-adapter-capacity.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { runShadowProgramForShell } from '../modules/shadow-engine/shell-adapter.js';

const app = fs.readFileSync('modules/App.js', 'utf8');
const legacyExercises = [...EXERCISE_MAP.values()].map(def => ({
  id: def.id,
  name: def.name,
  equip: Array.isArray(def.equipment) ? [...def.equipment] : [],
  part: Object.entries(def.muscles ?? {}).sort((a,b)=>(b[1]?.credit ?? 0)-(a[1]?.credit ?? 0))[0]?.[0] ?? 'other',
  type: def.flags?.compound ? 'compound' : 'isolation',
  pattern: def.movementFamily
}));
const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const cfg = (patch={}) => ({
  name:'M199 perf', experience:'intermediate', goal:'both', split:'full_body', days:3, session:'s90', weeks:6,
  equipment, focus:{}, focusList:[], reduce:[], progression:'auto', progressionStyle:'auto', deload:true,
  barbellCap:3, percentScheme:null, volumeApproach:'standard', noSupersets:false, noBodyweight:false, unit:'lb',
  ...patch
});

async function bench(label, fn, rounds=5) {
  const values=[];
  for (let i=0;i<rounds;i++) {
    const t0=performance.now();
    await fn(i);
    values.push(performance.now()-t0);
  }
  values.sort((a,b)=>a-b);
  const mean=values.reduce((a,b)=>a+b,0)/values.length;
  const p50=values[Math.floor(values.length*0.5)];
  const p95=values[Math.min(values.length-1, Math.floor(values.length*0.95))];
  return { label, rounds, min:+values[0].toFixed(1), p50:+p50.toFixed(1), mean:+mean.toFixed(1), p95:+p95.toFixed(1), max:+values.at(-1).toFixed(1) };
}

function section(source, startToken, endToken, max=18000) {
  const a=source.indexOf(startToken);
  if (a<0) return '';
  const b=source.indexOf(endToken,a+startToken.length);
  return source.slice(a,b<0?Math.min(source.length,a+max):Math.min(b,a+max));
}
function occurrences(source, needle) {
  const out=[]; let at=0;
  while ((at=source.indexOf(needle,at))>=0) { out.push(at); at+=needle.length; }
  return out;
}
function lineAt(index) { return app.slice(0,index).split('\n').length; }
function contexts(needle, radius=180) {
  return occurrences(app,needle).map(i=>({line:lineAt(i), text:app.slice(Math.max(0,i-radius),Math.min(app.length,i+needle.length+radius)).replace(/\s+/g,' ')}));
}

const wizard=section(app,'function Wizard(', 'function Heading(');
const finish=section(wizard,'const finish = async () =>', 'const next =');
const firstBuild=Math.min(...['generateNextWithShadow(','generateNextCycleForShell('].map(x=>{const i=finish.indexOf(x);return i<0?Infinity:i;}));
const firstAwait=finish.indexOf('await ');
const importCapacity=/from ["']\.\/next-engine\/app-shell-adapter-capacity\.js["']/.test(app.slice(0,5000));
const importBase=/from ["']\.\/next-engine\/app-shell-adapter\.js["']/.test(app.slice(0,5000));

const staticAudit={
  appBytes:Buffer.byteLength(app),
  appLines:app.split('\n').length,
  importsCapacityAdapter:importCapacity,
  importsBaseAdapter:importBase,
  wizardHasBuildingState:/\[building, setBuilding\]/.test(wizard),
  wizardPaintsBeforeBuild:firstAwait>=0 && firstAwait<firstBuild,
  wizardFirstAwaitOffset:firstAwait,
  wizardFirstBuildOffset:Number.isFinite(firstBuild)?firstBuild:null,
  splitChecksAreChunked:/setTimeout\(tick, 0\)/.test(wizard),
  splitBuildabilityCalls:contexts('splitBuildability('),
  programGenerationCalls:contexts('generateNextProgramForShell('),
  cycleGenerationCalls:contexts('generateNextCycleForShell('),
  localStorageReads:occurrences(app,'localStorage.getItem(').length,
  localStorageWrites:occurrences(app,'localStorage.setItem(').length,
  jsonStringifyCalls:occurrences(app,'JSON.stringify(').length,
  intervalCalls:contexts('setInterval('),
  timeoutCalls:occurrences(app,'setTimeout(').length
};

const results=[];
for (const [name,patch] of [
  ['FB3 s90',{split:'full_body',days:3,session:'s90'}],
  ['FB5 s90',{split:'full_body',days:5,session:'s90'}],
  ['ULPPL5 s90',{split:'ulppl',days:5,session:'s90'}],
  ['PPL6 s90',{split:'ppl',days:6,session:'s90'}]
]) {
  const c=cfg(patch);
  results.push(await bench(`base generate ${name}`, i=>base.generateNextProgramForShell({config:c,banned:[],legacyExercises,seed:7000+i}),3));
  results.push(await bench(`capacity generate ${name}`, i=>capacity.generateNextProgramForShell({config:c,banned:[],legacyExercises,seed:7100+i}),3));
  results.push(await bench(`base split check ${name}`, ()=>base.splitBuildability(c,legacyExercises),3));
  results.push(await bench(`capacity split check ${name}`, ()=>capacity.splitBuildability(c,legacyExercises),3));
}
const shadowCfg=cfg({split:'ulppl',days:5,session:'s90'});
const live=capacity.generateNextProgramForShell({config:shadowCfg,banned:[],legacyExercises,seed:7200});
results.push(await bench('shadow evaluation ULPPL5', i=>runShadowProgramForShell({config:shadowCfg,banned:[],legacyExercises,seed:7200+i,liveProgram:live.nextProgram}),3));
for (const adaptBetweenBlocks of [false,true]) {
  results.push(await bench(`powerbuilding FB5 cycle ${adaptBetweenBlocks?'adaptive':'locked'}`, i=>generateNextCycleForShell({templateId:'powerbuilding',config:cfg({split:'full_body',days:5,session:'s90'}),banned:[],legacyExercises,seed:7300+i,adaptBetweenBlocks,makeId:(()=>{let n=0;return()=>`perf-${adaptBetweenBlocks}-${++n}`;})()}),2));
}

const report={generatedAt:new Date().toISOString(),staticAudit,benchmarks:results};
fs.writeFileSync('m199-performance-audit.json',JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));

const warnings=[];
if (!staticAudit.importsCapacityAdapter) warnings.push('UI imports the base app-shell adapter instead of the M198 capacity-aware adapter.');
if (!staticAudit.wizardPaintsBeforeBuild) warnings.push('Wizard does not yield to a paint before synchronous generation begins.');
const slow=results.filter(x=>x.p95>=100).map(x=>`${x.label}: ${x.p95}ms p95`);
if (slow.length) warnings.push(`Main-thread operations >=100ms: ${slow.join('; ')}`);
console.log('\nM199 WARNINGS');
for (const warning of warnings) console.log('- '+warning);
