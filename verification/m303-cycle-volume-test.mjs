import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/App.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { publicMevLedger, PUBLIC_MEV_REGIONS } from '../modules/next-engine/public-mev.js';

// M303: check executed weekly sets and each public muscle region, not only
// prescription metadata. These are stable seeded gym configurations previously
// represented in the generation parity oracle.
const fixtures=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url),'utf8')).rows;
const cases=[
  {row:2,seed:30342,label:'Upper/Lower 4 days'},
  {row:3,seed:30353,label:'ULPPL 5 days'},
  {row:12,seed:30362,label:'Prioritized 5-day muscles'}
];
const sets=p=>p.sessions.reduce((sum,day)=>sum+day.exercises.reduce((n,ex)=>n+ex.sets,0),0);
const round=n=>Math.round(n*10)/10;
const matrix=[];
for(const probe of cases){
  const config={...fixtures[probe.row].config,name:'M303 phase-dose audit',goal:'hypertrophy'};
  const authored=[
    {phase:'hypertrophy_accumulation',weeks:5},
    {phase:'strength_accumulation',weeks:5}
  ];
  let id=0;
  const baseline=JSON.stringify({config,authored});
  const result=generateNextCycleForShell({
    templateId:'custom',config,blocks:authored,legacyExercises:EXERCISES,
    seed:probe.seed,makeId:()=>`m303-${probe.row}-${++id}`,
    adaptBetweenBlocks:true,adaptTrainingSplit:true
  });
  assert.equal(JSON.stringify({config,authored}),baseline,'Generation preserves authored data');
  const previous=result.blocks[0].nextEngine.program;
  const current=result.blocks[1].nextEngine.program;
  const before=sets(previous),after=sets(current);
  const decision=result.cycle.blockMeta[1].splitDecision;
  const exerciseMap=createExerciseMap(result.blocks[1].nextEngine.request.customExercises);
  const prior=publicMevLedger(previous.sessions,exerciseMap);
  const next=publicMevLedger(current.sessions,exerciseMap);
  assert.equal(previous.audit.result,'pass');
  assert.equal(current.audit.result,'pass');
  assert.equal(current.sessions.length,config.days,'Cycle preserves the athlete\'s weekly frequency');
  assert.equal(result.blocks[1].config.split,decision.selectedSplit);
  if(before>=16) assert.ok(after>=Math.ceil(before*.5),
    `${probe.label}: strength must not cliff from ${before} to ${after} sets`);
  assert.ok(Number.isInteger(decision.regionalCarryoverPercent));
  assert.ok(decision.regionalCarryoverPercent>=0&&decision.regionalCarryoverPercent<=100);
  assert.ok(Array.isArray(decision.regionalRiskRegions));
  assert.ok(decision.regionalRiskRegions.every(r=>PUBLIC_MEV_REGIONS.includes(r)));
  const losses=PUBLIC_MEV_REGIONS.filter(region=>prior[region]>=2&&next[region]<prior[region]*.4)
    .map(region=>`${region}:${round(prior[region])}→${round(next[region])}`);
  matrix.push({
    case:probe.label,hypertrophySets:before,strengthSets:after,
    retainedPct:Math.round(after/before*100),
    hypertrophySplit:previous.split.family,strengthSplit:current.split.family,
    regionalCarryoverPct:decision.regionalCarryoverPercent,
    atRisk:decision.regionalRiskRegions,largeRegionalReductions:losses,
    liftPractice:decision.liftFrequencies
  });
}
console.log('M303 generated cycle dose matrix: '+JSON.stringify(matrix,null,2));
console.log('PASS M303: longitudinal split adaptation respects working-set safety and exposes actual regional doses.');
