import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { spawnSync } from 'node:child_process';
import { EXERCISES, computeCell } from '../modules/engine-api.js';

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
// M372: observe the public suggestion boundary only inside the browser harness.
// No production module, debug export, or training algorithm is replaced on disk.
const loggerProgram=fixed.blocks[0],loggerDay=loggerProgram.days[0],loggerNow=Date.now();
const loggerHistory=Array.from({length:240},(_,index)=>{
 const day=loggerProgram.days[index%loggerProgram.days.length],week=1+index%loggerProgram.config.weeks;
 return {id:`m372-history-${index}`,programId:loggerProgram.id,dayId:day.id,dayLabel:day.label,
  date:loggerNow-(index+1)*86400000,weekIndex:week,unit:'lb',perf:Object.fromEntries(day.exercises.map((id,slot)=>{
   const cell=computeCell(loggerProgram,day,id,slot,week),reps=Number(String(cell.reps).split('-')[0]);
   return [id,{weight:100,reps,sets:Array.from({length:cell.sets},()=>({w:100,r:reps,rir:2,tr:2,rirReported:true,done:true}))}];
  }))};
});
const loggerData=loggerDay.exercises.map((id,slot)=>{
 const cell=computeCell(loggerProgram,loggerDay,id,slot,1);
 return {id,slot,note:'',sets:Array.from({length:cell.sets},()=>({weight:'100',reps:String(cell.reps).split('-')[0],
  done:false,auto:false,valueOwner:'user',target:{w:'100',reps:cell.reps,rir:cell.rir}}))};
});
const loggerLive={schemaVersion:2,sessionId:'m372-live',programId:loggerProgram.id,dayId:loggerDay.id,weekIndex:1,
 dayExSig:loggerDay.exercises.join('|'),exIdx:0,elapsedMs:120000,readiness:{label:'Normal',factor:1},
 restPaused:true,restRemain:0,restMax:0,runPaused:false,data:loggerData};
const loggerStore={v:13,savedAt:1,saved:[loggerProgram],history:loggerHistory,cycles:[],perf:{},drafts:{},tombs:{},
 seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,theme:'amethyst',restAutoStart:false,warmupCard:false,
 lastBackup:loggerNow,installDismissedAt:loggerNow};
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const loggerHtml=`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import {WorkoutSession,StyleTag} from '/modules/App.js';
const program=${JSON.stringify(loggerProgram)},history=${JSON.stringify(loggerHistory)},live=${JSON.stringify(loggerLive)},store=${JSON.stringify(loggerStore)};
localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('wpb:live',JSON.stringify(live));
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day:program.days[0],weekIndex:1,unit:'lb',perf:{},history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:entry=>window.__m372Finished=entry})));
</script></body></html>`;
const mime={'.html':'text/html','.webmanifest':'application/manifest+json','.js':'text/javascript','.mjs':'text/javascript','.json':'application/json','.css':'text/css','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{
 const uri=new URL(req.url,'http://localhost');
 if(uri.pathname==='/__m372_logger'){
  res.setHeader('Content-Type','text/html; charset=utf-8');res.end(loggerHtml);return;
 }
 if(uri.pathname==='/__m318_benchmark'){
  res.setHeader('Content-Type','text/html; charset=utf-8');
  res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>M318 engine timing harness</body></html>');
  return;
 }
 const file=path.resolve(root,'.'+decodeURIComponent(uri.pathname==='/'?'/index.html':uri.pathname));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
  res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
 let contents=fs.readFileSync(file);
 if(uri.pathname==='/modules/training-domain/prescriptions.js'){
  const marker=/function sessionSuggestion\([^)]*\) \{/;
  assert.ok(marker.test(contents.toString()),'Suggestion boundary must be observable by the logging guard');
  contents=contents.toString().replace(marker,match=>match+'globalThis.__m372SuggestionCalls=(globalThis.__m372SuggestionCalls||0)+1;');
 }
 // A harness-only negative control proves the counter detects a removed cache.
 if(process.argv.includes('--logger-disable-cache')&&uri.pathname==='/modules/App.js'){
  const marker=/const suggestions = useMemo\(\(\) => (day\.exercises\.map\([^\n]+?\)), \[day, program, dayPerf, unit, weekIndex\]\);/;
  assert.ok(marker.test(contents.toString()),'Negative control must reach the memoized suggestion owner');
  contents=contents.toString().replace(marker,'const suggestions = $1;');
 }
 // M373 reference timing keeps the projection formula and bypasses only reuse.
 if(process.argv.includes('--exercise-knowledge-uncached')&&uri.pathname==='/modules/next-engine/exercise-selection-intelligence.js'){
  const read='  const cached = canonicalKnowledge.get(def);\n  if (cached) return cached;\n';
  const write='  if (canonicalDefinitions.has(def)) canonicalKnowledge.set(def, node);\n';
  assert.ok(contents.toString().includes(read)&&contents.toString().includes(write),'Reference control must bypass the exact cache boundary');
  contents=contents.toString().replace(read,'').replace(write,'');
 }
 if(uri.pathname==='/modules/next-engine/workout-history-adapter.js'){
  for(const [marker,key] of [
   ['function legacyExerciseById(legacyExercises) {','maps'],
   ['export function analyzeShellHistoryForNextEngine(program, history, legacyExercises, options = {}) {','analyses']
  ]){
   assert.ok(contents.toString().includes(marker),'History work counter must reach '+key);
   contents=contents.toString().replace(marker,marker+`globalThis.__m376Work ||= {maps:0,analyses:0};globalThis.__m376Work.${key}++;`);
  }
  if(process.argv.includes('--history-rebuild-reference')){
   const marker='    const context = { snap, planSnap: sourceSnapshot(program), legacyMap: legacyExerciseById(legacyExercises) };';
   assert.ok(contents.toString().includes(marker),'Reference must bypass per-analysis reuse');
   contents=contents.toString().replace(marker,'    const context = undefined;');
  }
 }
 if(process.argv.includes('--history-rebuild-reference')&&uri.pathname==='/modules/next-engine/loading.js'){
  for(const marker of [
   "normalizeLoadingInventory(defaultInventoryDefinition(unit === 'lb' ? 'kg' : 'lb'))",
   'normalizeLoadingInventory(defaultInventoryDefinition(inventory.unit))',
   'const defaults = defaultInventoryDefinition(input.unit)'
  ]){
   assert.ok(contents.toString().includes(marker),'Reference must restore the original default clone boundary');
   contents=contents.toString().replace(marker,marker.replace('defaultInventoryDefinition','defaultLoadingInventoryFor'));
  }
 }
 if(process.argv.includes('--history-rebuild-reference')&&uri.pathname==='/modules/session-tools.js'){
  const marker='const suggestion = hasChoices ? sessionSuggestion(program, day, slot, perf, unit, weekIndex, history) : proposedSuggestion;';
  assert.ok(contents.toString().includes(marker),'Reference must restore duplicate proposal/effective work');
  contents=contents.toString().replace(marker,'const suggestion = sessionSuggestion(program, day, slot, perf, unit, weekIndex, history);');
 }
 res.end(contents);
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
  headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 if(!process.argv.includes('--logger-only')&&!process.argv.includes('--startup-only')){
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
  JSON.stringify({exerciseKnowledgeReuse:!process.argv.includes('--exercise-knowledge-uncached'),chromeCpuRates:records.map(x=>x.rate),
   cases:records.map(r=>({...r,creationMs:round(r.creationMs),advanceMs:round(r.advanceMs)})),
   creationSlowdown:round(records[1].creationMs/records[0].creationMs),
   advancementSlowdown:round(records[1].advanceMs/records[0].advanceMs)}));
 console.log('PASS M318: real mobile Chromium, native/4x CPU: complete cycle creation + saved-history block advancement keep audited executable identity, doses, and finalist choices.');
 await page.close();
 }
 if(!process.argv.includes('--startup-only'))await verifyLoggerPerformance(browser,server.address().port);
 if(!process.argv.includes('--logger-only'))await verifyAppPerformance(browser,server.address().port);
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}

async function verifyLoggerPerformance(browser,port){
 const quantiles=values=>{const sorted=[...values].sort((a,b)=>a-b),at=p=>Math.round(sorted[Math.ceil(sorted.length*p)-1]*10)/10;
  return {samples:values.length,p50:at(.5),p95:at(.95),p99:at(.99)};};
 const records=[];
 for(const rate of [1,4]){
  const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  try{
   await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
   await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
   await page.goto(`http://127.0.0.1:${port}/__m372_logger`,{waitUntil:'networkidle0'});
   await page.waitForSelector('[data-testid="set-0-0"]');
   const check=await page.$('.wpb-backdrop button[data-wpb-system-back]');if(check)await check.click();
   const client=await page.createCDPSession();await client.send('Emulation.setCPUThrottlingRate',{rate});
   const result=await page.evaluate(async()=>{
    const frames=()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
    await frames();const mountedCalls=globalThis.__m372SuggestionCalls;globalThis.__m372SuggestionCalls=0;
    const before=localStorage.getItem('wpb:v1'),inputTimes=[],completeTimes=[],undoTimes=[];
    const input=document.querySelector('[data-testid="set-0-0"] input[aria-label="weight"]'),setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
    for(let i=0;i<40;i++){
     const value=String(100+i%6*5),start=performance.now();setter.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));await frames();
     if(JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].weight!==value)throw Error('Typed value was not persisted');
     inputTimes.push(performance.now()-start);
    }
    for(let i=0;i<10;i++){
     let start=performance.now();document.querySelector('[data-testid="set-0-0"] .wpb-set-complete').click();await frames();
     if(!JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].done)throw Error('Completed set was not persisted');completeTimes.push(performance.now()-start);
     start=performance.now();document.querySelector('[data-testid="set-0-0"] .wpb-set-complete').click();await frames();
     if(JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].done)throw Error('Undo was not persisted');undoTimes.push(performance.now()-start);
    }
    if(localStorage.getItem('wpb:v1')!==before)throw Error('Logging probe changed saved program/history');
    return {mountedCalls,callsDuringLogging:globalThis.__m372SuggestionCalls,inputTimes,completeTimes,undoTimes,historyWork:globalThis.__m376Work};
   });
   assert.ok(result.mountedCalls>0,'The real engine suggestion boundary must be exercised on mount');
   assert.ok(result.historyWork?.analyses>0&&result.historyWork.maps<=result.historyWork.analyses*3+result.mountedCalls*2+8,'Each history analysis must share its catalog lookup across historical workouts: '+JSON.stringify(result.historyWork));
   assert.equal(result.callsDuringLogging,0,'Typing, completing and undoing sets must reuse unchanged engine suggestions');
   assert.deepEqual(errors,[],'Logging probe must not produce runtime errors');
   records.push({rate,mountedSuggestionCalls:result.mountedCalls,callsDuringLogging:result.callsDuringLogging,historyWork:result.historyWork,
    input:quantiles(result.inputTimes),complete:quantiles(result.completeTimes),undo:quantiles(result.undoTimes)});
  }finally{await context.close();}
 }
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'RELEASE_MANIFEST.json'),'utf8'));
 const gitRevision=spawnSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'});
 const revision=process.env.GITHUB_SHA||(gitRevision.status===0?gitRevision.stdout.trim():'unknown');
 const report={schemaVersion:1,sourceRevision:revision,build:manifest.build,runtimeAggregate:manifest.runtimeAggregate,
  fixture:{programId:loggerProgram.id,historyWorkouts:loggerHistory.length,rows:loggerData.length,
   workingSets:loggerData.reduce((n,row)=>n+row.sets.length,0),viewport:'393x852',reducedMotion:true},
  browser:await browser.version(),unit:'ms',
  method:'Synthetic input/click to two animation frames; persisted live state checked per action. Local preloaded assets; CPU slowdown emulated. Not physical Android, field INP, cold startup, or TalkBack certification.',
  timingPolicy:'Environment-relative diagnostics; correctness and zero duplicate suggestion calls are enforced, not a flaky machine-speed threshold.',records};
 const reportAt=process.argv.indexOf('--logger-report');
 if(reportAt>=0){const target=process.argv[reportAt+1];if(!target||target.startsWith('--'))throw Error('Use --logger-report path/to/report.json');
  const filename=path.resolve(root,target);fs.mkdirSync(path.dirname(filename),{recursive:true});
  fs.writeFileSync(filename,JSON.stringify(report,null,2)+'\n');}
 console.log('M372_LOGGING_PERFORMANCE '+JSON.stringify(report));
 console.log('PASS M372: 240-workout history, native/4x CPU; 40 typed values and 10 completion/undo pairs per profile persist exactly without repeating engine suggestions or changing saved training.');
}


// Whole-app startup and repeated recovery, using the same large-history fixture.
// Timings are diagnostic; actual persisted identity and bounded work are gates.
async function verifyAppPerformance(browser,port){
 const records=[],reference=process.argv.includes('--history-rebuild-reference');
 for(const rate of [1,4]){
  const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  try{
   await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
   await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
   await page.evaluateOnNewDocument((store,live)=>{
    localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('wpb:live',JSON.stringify(live));
    globalThis.__m376Startup={longTasks:[]};
    new PerformanceObserver(list=>globalThis.__m376Startup.longTasks.push(...list.getEntries().map(entry=>({start:entry.startTime,duration:entry.duration})))).observe({type:'longtask',buffered:true});
    const observer=new MutationObserver(()=>{
     if(document.querySelector('.wpb-home')&&!globalThis.__m376Startup.ready){
      globalThis.__m376Startup.ready=true;observer.disconnect();
      requestAnimationFrame(()=>requestAnimationFrame(()=>{globalThis.__m376Startup.homeMs=performance.now();}));
     }
    });observer.observe(document,{subtree:true,childList:true});
   },loggerStore,{...loggerLive,runPaused:true,elapsedMs:125000});
   const client=await page.createCDPSession();await client.send('Emulation.setCPUThrottlingRate',{rate});
   await page.goto(`http://127.0.0.1:${port}/`,{waitUntil:'networkidle0'});
   await page.waitForFunction(()=>globalThis.__m376Startup?.homeMs>0);
   const startup=await page.evaluate(()=>globalThis.__m376Startup);
   const training=()=>page.evaluate(()=>{const state=JSON.parse(localStorage.getItem('wpb:v1'));return JSON.stringify({saved:state.saved,history:state.history,cycles:state.cycles,drafts:state.drafts});});
   const before=await training(),returns=[];
   for(let index=0;index<5;index++){
    await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.evaluate(()=>{
     let start;
     const observer=new MutationObserver(()=>{
      if(start!==undefined&&document.querySelector('[data-testid="set-0-0"]')){
       observer.disconnect();requestAnimationFrame(()=>requestAnimationFrame(()=>{globalThis.__m376Return={ms:performance.now()-start,live:JSON.parse(localStorage.getItem('wpb:live'))};}));
      }
     });globalThis.__m376Return=null;observer.observe(document,{subtree:true,childList:true});
     document.querySelector('.wpb-live-dock button[aria-label="Resume workout"]').addEventListener('click',()=>{start=performance.now();},{once:true,capture:true});
    });
    await page.locator('.wpb-live-dock button[aria-label="Resume workout"]').click();
    await page.waitForFunction(()=>!!globalThis.__m376Return);
    const result=await page.evaluate(()=>globalThis.__m376Return);
    assert.equal(result.live.sessionId,loggerLive.sessionId);assert.equal(result.live.elapsedMs,125000);
    assert.equal(result.live.data[0].sets[0].weight,'100');
    returns.push(Math.round(result.ms*10)/10);
    await page.evaluate(()=>history.back());await page.waitForSelector('.wpb-workout',{hidden:true});
   }
   assert.equal(await training(),before,'Startup/resume cannot change saved training/history');
   const work=await page.evaluate(()=>globalThis.__m376Work);
   if(reference)assert.ok(work.maps>loggerHistory.length,'Control must restore repeated historical work');
   else {
    assert.ok(work.analyses>0&&work.maps<=work.analyses*3+64,'Full app reuses one history lookup per analysis: '+JSON.stringify(work));
    assert.ok(work.analyses<=(returns.length*2+3)*loggerData.length,
     'A review without saved choices must not repeat proposal/effective history analysis: '+JSON.stringify(work));
   }
   assert.deepEqual(errors,[]);
   records.push({rate,homeMs:Math.round(startup.homeMs*10)/10,longTasks:startup.longTasks,warmReturnsMs:returns,historyWork:work});
  }finally{await context.close();}
 }
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'RELEASE_MANIFEST.json'),'utf8'));
 const report={schemaVersion:1,build:manifest.build,runtimeAggregate:manifest.runtimeAggregate,browser:await browser.version(),reference,
  fixture:{historyWorkouts:loggerHistory.length,serializedStoreCharacters:JSON.stringify(loggerStore).length,viewport:'393x852',reducedMotion:true},
  method:'Full local app navigation to two-frame Home readiness; real pointer Resume to two-frame set entry, with exact persisted recovery and unchanged training content. Native/4x CPU emulation. Not field CWV, network timing or physical Android certification.',records};
 const at=process.argv.indexOf('--app-report');
 if(at>=0){const target=process.argv[at+1];if(!target||target.startsWith('--'))throw Error('Use --app-report path/to/report.json');fs.mkdirSync(path.dirname(path.resolve(root,target)),{recursive:true});fs.writeFileSync(path.resolve(root,target),JSON.stringify(report,null,2)+'\n');}
 console.log('M376_APP_PERFORMANCE '+JSON.stringify(report));
 console.log(reference?'PASS M376 reference: original repeated lookups are reproduced while recovery identity and saved training remain unchanged.':'PASS M376: full-app 240-workout startup and five pointer resume/minimize cycles at native/4x CPU preserve recovery identity and saved training with bounded history lookup work.');
}
