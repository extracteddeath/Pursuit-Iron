import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { EXERCISES } from '../modules/App.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { upgradeExistingCycleToAdaptive } from '../modules/next-engine/adaptive-cycle-upgrade.js';
import { completedHistory, ids } from './m265-generation-fixture.mjs';

// M318 measures synchronous *complete* cycle creation and block advancement
// on a 320px mobile Chrome viewport, with a simulated 4x CPU slowdown.
// Chrome CPU emulation is NOT physical-device profiling. Environment-relative
// measurements are diagnostic; auditable results are the acceptance contract.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const rows=JSON.parse(fs.readFileSync(path.join(root,'verification/m205-generation-parity-results.json'),'utf8')).rows;
const config={...rows[2].config,name:'M318 Chrome mobile adaptive cycles',goal:'hypertrophy',
  focus:{},reduce:[],weeks:5};
const blocks=[{phase:'hypertrophy_accumulation',weeks:5},
  {phase:'strength_accumulation',weeks:5}];
const advanceConfig={...rows[3].config,name:'M318 existing custom cycle',
  goal:'both',focus:{lats:2,upper_back:2}};
const fixed=generateNextCycleForShell({
  templateId:'custom',config:advanceConfig,
  blocks:[{phase:'hypertrophy_accumulation',weeks:5},
    {phase:'strength_accumulation',weeks:5},{phase:'peak',weeks:2}],
  legacyExercises:EXERCISES,seed:31101,makeId:ids('m318-fixed'),
  adaptTrainingSplit:false,adaptBetweenBlocks:false
});
const upgraded=upgradeExistingCycleToAdaptive(fixed.cycle,fixed.blocks,[],318001);
assert.equal(upgraded.changed,true);
const history=completedHistory(fixed.blocks[0]);
const baseline=JSON.stringify({cycle:upgraded.cycle,active:fixed.blocks[0],history});
const mime={'.js':'text/javascript','.mjs':'text/javascript','.json':'application/json'};
const server=http.createServer((req,res)=>{
 const uri=new URL(req.url,'http://localhost');
 if(uri.pathname==='/__m318_benchmark'){
  res.setHeader('Content-Type','text/html; charset=utf-8');
  res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>M318 engine timing harness</body></html>');
  return;
 }
 const file=path.resolve(root,'.'+decodeURIComponent(uri.pathname));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
  res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
 res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
  headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage();
 const pageErrors=[];
 page.on('pageerror',e=>pageErrors.push(e.message));
 await page.setViewport({width:320,height:844,isMobile:true,hasTouch:true,deviceScaleFactor:2});
 await page.setBypassServiceWorker(true);
 await page.goto(`http://127.0.0.1:${server.address().port}/__m318_benchmark`,{waitUntil:'load'});
 const client=await page.createCDPSession();
 // Preload modules at native speed; track engine operation latency, not download/parse.
 await page.evaluate(async()=>import('/modules/next-engine/cycle-runtime-adapter.js'));
 const payload={legacyExercises:EXERCISES,config,blocks,
   converted:{cycle:upgraded.cycle,activeProgram:fixed.blocks[0],history}};
 const records=[];
 for(const rate of [1,4]){
  await client.send('Emulation.setCPUThrottlingRate',{rate});
  const result=await page.evaluate(async ({legacyExercises,config,blocks,converted,rate})=>{
   const {generateNextCycleForShell,advanceNextCycleForShell}=await import('/modules/next-engine/cycle-runtime-adapter.js');
   const begin=performance.now();
   let id=0;
   const made=generateNextCycleForShell({
    templateId:'custom',config,blocks,legacyExercises,seed:30442,
    makeId:()=>`m318-browser-${rate}-${++id}`,
    adaptTrainingSplit:true,adaptBetweenBlocks:true
   });
   const creationMs=performance.now()-begin;
   const strength=made.blocks[1];
   if(made.blocks.length!==2||strength.nextEngine.program.audit.result!=='pass'
      ||made.cycle.blockMeta[1].splitDecision.realizedSelection?.mode!=='finalized_strength_top_two')
    throw new Error('M318: real browser cycle creation did not generate a verified adaptive strength finalist');
   const strengthDecision=made.cycle.blockMeta[1].splitDecision;
   const startAdvance=performance.now();
   const next=advanceNextCycleForShell({
    ...converted,legacyExercises,
    makeId:()=>`m318-advance-${rate}`
   });
   const advanceMs=performance.now()-startAdvance;
   if(next.done||next.insertedRecovery||next.nextProgram?.nextEngine?.program?.audit?.result!=='pass'
      ||next.cycle.blockMeta[1].splitDecision.realizedSelection?.mode!=='finalized_strength_top_two')
    throw new Error('M318: real browser block advancement did not create an audited adaptive strength block');
   if(next.nextProgram.id!==converted.cycle.blockIds[1]
      ||next.nextProgram.nextEngine.priorBlock.workouts!==converted.history.length)
    throw new Error('M318: timing worker did not preserve history or target block identity');
   return {rate,creationMs,advanceMs,createdSplit:strength.config.split,
    advancedSplit:next.nextProgram.config.split,
    selectedFinalists:strengthDecision.realizedSelection.evaluatedFinalists,
    advancedFinalists:next.cycle.blockMeta[1].splitDecision.realizedSelection.evaluatedFinalists,
    days:strength.days.length,advancedDays:next.nextProgram.days.length,
    creationWeekSets:strengthDecision.realizedQuality.weeks[0].weeklySets,
    advancementWeekSets:next.cycle.blockMeta[1].splitDecision.realizedQuality.weeks[0].weeklySets};
  },{...payload,rate});
  assert.ok(result.creationMs>0&&result.advanceMs>0);
  assert.ok(result.selectedFinalists>=1&&result.selectedFinalists<=2);
  assert.ok(result.advancedFinalists>=1&&result.advancedFinalists<=2);
  assert.equal(result.days,config.days);
  assert.equal(result.advancedDays,advanceConfig.days);
  records.push(result);
 }
 await client.send('Emulation.setCPUThrottlingRate',{rate:1});
 assert.deepEqual(pageErrors,[]);
 assert.equal(JSON.stringify({cycle:upgraded.cycle,active:fixed.blocks[0],history}),baseline,
  'Benchmarking must not mutate saved cycle, active workout plan, or training logs');
 assert.deepEqual(records.map(x=>x.creationWeekSets),[records[0].creationWeekSets,records[0].creationWeekSets],
  'Identical seeds and athlete constraints must preserve executable sets under CPU emulation');
 assert.deepEqual(records.map(x=>x.advancementWeekSets),[records[0].advancementWeekSets,records[0].advancementWeekSets]);
 const round=n=>Math.round(n*10)/10;
 console.log('M318 Chrome 320px CPU throttling (simulated, NOT physical Android) '+
  JSON.stringify({chromeCpuRates:records.map(x=>x.rate),
   cases:records.map(r=>({...r,creationMs:round(r.creationMs),advanceMs:round(r.advanceMs)})),
   creationSlowdown:round(records[1].creationMs/records[0].creationMs),
   advancementSlowdown:round(records[1].advanceMs/records[0].advanceMs)}));
 console.log('PASS M318: real mobile Chromium, native/4x CPU: complete cycle creation + saved-history block advancement keep audited executable identity, doses, and finalist choices.');
 await page.close();
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
