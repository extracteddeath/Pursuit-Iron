import assert from 'node:assert/strict';
import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import puppeteer from 'puppeteer-core';
import { generateNextProgramForShell } from '../modules/engine-api.js';
import { generationInput, completedHistory, equipment, ids } from './m265-generation-fixture.mjs';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program=generateNextProgramForShell({...generationInput(),makeId:ids('b905-browser')}).program;
const history=completedHistory(program);
const initial={v:13,savedAt:1,saved:[program],cycles:[],history,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,
 pinnedId:program.id,theme:'amethyst',unit:'lb',unitChosen:true,experience:'intermediate',restAutoStart:false,warmupCard:false,
 gyms:[{id:'test',name:'Test gym',equipment,inventory:{},limits:{},limitUnit:'lb'}],activeGymId:'test',bodyweight:185,
 installDismissedAt:Date.now(),lastBackup:Date.now()};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.webmanifest':'application/manifest+json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const f=path.resolve(root,'.'+(new URL(req.url,'http://localhost').pathname==='/'?'/index.html':new URL(req.url,'http://localhost').pathname));if(!f.startsWith(root+path.sep)||!fs.existsSync(f)||!fs.statSync(f).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(f)]||'application/octet-stream');res.end(fs.readFileSync(f));});
await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const errors=[];
 for(const width of [320,834]){
  const context=await browser.createBrowserContext(),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:width===320?844:1194,isMobile:width<600,hasTouch:true});await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);await page.setBypassServiceWorker(true);
  await page.evaluateOnNewDocument(state=>{if(!localStorage.getItem('b905-seeded')){localStorage.setItem('wpb:v1',JSON.stringify(state));localStorage.setItem('b905-seeded','1');}},{...initial,theme:width===834?'rosewater':'amethyst'});
  await page.goto(`http://127.0.0.1:${server.address().port}`,{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-tabbar');
  const store=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  const clickText=text=>page.$$eval('button',(buttons,text)=>{const b=buttons.find(n=>n.textContent.trim()===text);if(!b)throw Error('Missing '+text);b.click();},text);
  const openPlan=async()=>{await page.click('[data-tab="plan"]');await page.waitForSelector('.wpb-plan-view');await clickText('Open full program');await page.waitForSelector('.wpb-program');};
  const before=(await store()).saved[0],beforeHistory=(await store()).history;
  await openPlan();
  const rebuild=async()=>{await page.locator('[aria-label="Program settings"]').click();await page.waitForSelector('[aria-label="Decrease block length"]');await page.locator('[aria-label="Decrease block length"]').click();await page.waitForFunction(()=>document.querySelector('[aria-label="Save program settings"]')?.disabled===false);await page.locator('[aria-label="Save program settings"]').click();await page.waitForSelector('[data-program-review]',{timeout:20000}).catch(async error=>{console.error(await page.evaluate(()=>({text:document.body.innerText.slice(-2200),saved:JSON.parse(localStorage.getItem("wpb:v1")).saved.map(p=>({id:p.id,weeks:p.weeks,configWeeks:p.config?.weeks})),inputs:[...document.querySelectorAll("input")].map(n=>({label:n.getAttribute("aria-label"),value:n.value}))})));throw error;});};
  await rebuild();assert.deepEqual((await store()).saved[0],before,'generation is staged until Apply');
  const modal=await page.$eval('.wpb-program-revision',el=>{
   const rect=el.getBoundingClientRect(),backdrop=el.closest('.wpb-session-dialog-backdrop');
   return {motion:el.dataset.piSurface,backdropMotion:backdrop?.dataset.piBackdrop,
    width:rect.width,left:rect.left,bottom:rect.bottom,viewport:innerWidth,viewportHeight:innerHeight,
    radius:parseFloat(getComputedStyle(el).borderTopLeftRadius),bg:getComputedStyle(el).backgroundColor,
    overflow:el.scrollWidth>el.clientWidth+1};
  });
  assert.equal(modal.motion,'1','session review participates in shared surface springs');
  assert.equal(modal.backdropMotion,'1','session review scrim participates in shared backdrop springs');
  assert.ok(modal.radius>=25&&!modal.overflow&&modal.width<=modal.viewport,'modal stays rounded and scroll-contained');
  if(width===834)assert.ok(modal.left>30&&modal.bottom<modal.viewportHeight-20,'portrait tablet review is centered');
  else assert.ok(Math.abs(modal.bottom-modal.viewportHeight)<3,'phone review stays bottom attached');
  assert.equal(await page.$eval('.wpb-program-revision',n=>n.scrollWidth<=n.clientWidth+1),true);
  assert.match(await page.$eval('[data-program-review]',n=>n.textContent),/Block length/);
  await page.screenshot({path:`/tmp/b905-review-${width}.png`});
  await clickText('Keep current plan');await page.waitForSelector('[data-program-review]',{hidden:true});assert.deepEqual((await store()).saved[0],before,'cancel leaves the stored program intact');
  await rebuild();await page.locator('[data-apply-program-review]').click();await page.waitForFunction(id=>JSON.parse(localStorage.getItem('wpb:v1')).saved.find(p=>p.id===id)?.layoutHistory?.length===1,{},program.id);
  const applied=(await store()).saved[0];assert.equal(applied.config.weeks,before.config.weeks-1);assert.deepEqual(applied.layoutHistory[0].program,before);assert.deepEqual((await store()).history,beforeHistory);
  await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-tabbar');await openPlan();await page.click('[title="Program details"]');await page.waitForSelector('[data-restore-program-layout="0"]');
  await page.click('[data-restore-program-layout="0"]');await page.waitForSelector('[aria-label="Restore earlier layout"]');assert.deepEqual((await store()).saved[0],applied,'restore is also preview-only');
  await page.locator('[data-apply-program-review]').click();await page.waitForFunction(id=>JSON.parse(localStorage.getItem('wpb:v1')).saved.find(p=>p.id===id)?.layoutHistory?.length===2,{},program.id);
  const restored=(await store()).saved[0];for(const key of Object.keys(before))assert.deepEqual(restored[key],before[key],key);const payload=rows=>rows.map(({updatedAt,...row})=>row);assert.deepEqual(payload((await store()).history),payload(beforeHistory),'all logged evidence survives restore; hydration may refresh revision clocks');
  await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-tabbar');assert.equal((await store()).saved[0].layoutHistory.length,2,'exact restore history persists');
  await context.close();
 }
 // A real resumed workout uses the existing detector, preserves logged/manual work, and persists the optional lighter choice.
 {
  const p={id:'b905-flat',name:'Plateau',custom:true,weeks:6,config:{unit:'kg',goal:'both',experience:'intermediate',progression:'double',deload:false},days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench']}],overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2',rest:90}},progStyle:{'bb-bench':'double'}};
  const logs=Array.from({length:5},(_,i)=>({id:'flat-'+i,date:Date.now()-(i+1)*604800000,updatedAt:1,programId:p.id,dayId:'upper',dayLabel:'Upper',weekIndex:1,unit:'kg',perf:{'bb-bench':{weight:80,reps:8,sets:[1,2,3].map(()=>({w:80,r:8,rir:2,rirReported:true}))}}}));
  const live={schemaVersion:2,programId:p.id,dayId:'upper',weekIndex:1,dayExSig:'bb-bench',exIdx:0,elapsedMs:1000,readiness:{label:'Normal',factor:1},restPaused:false,restRemain:0,restMax:0,runPaused:true,data:[{id:'bb-bench',slot:0,sets:[
   {weight:'80',reps:'8',done:true,auto:false,valueOwner:'user',actualRIR:2,target:{w:'80',reps:'6-8',rir:'2'}},
   {weight:'80',reps:'8',done:false,auto:true,valueOwner:'engine',target:{w:'80',reps:'6-8',rir:'2'}},
   {weight:'82.5',reps:'7',done:false,auto:false,valueOwner:'user',target:{w:'82.5',reps:'6-8',rir:'2'}}]}]};
  const context=await browser.createBrowserContext(),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument((state,session)=>{if(!localStorage.getItem('b905-flat-seeded')){localStorage.setItem('wpb:v1',JSON.stringify(state));localStorage.setItem('wpb:live',JSON.stringify(session));localStorage.setItem('b905-flat-seeded','1');}},{...initial,unit:'kg',saved:[p],pinnedId:p.id,history:logs},live);
  await page.goto(`http://127.0.0.1:${server.address().port}`,{waitUntil:'networkidle0'});
  await page.locator('[aria-label="Resume workout"]').click();await page.waitForSelector('.wpb-workout');
  const snapshot=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0]);
  const before=await snapshot();
  const open=async()=>{await page.$$eval('button',buttons=>buttons.find(b=>b.getAttribute('aria-label')==='Plateau options').click());await page.waitForSelector('[aria-label="Plateau options"]');};
  await open();await page.screenshot({path:'/tmp/b905-plateau.png'});await page.locator('[data-plateau-lighter]').click();
  await page.waitForFunction(w=>Number(JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].weight)<Number(w),{},before.sets[1].weight);
  let after=await snapshot();assert.deepEqual(after.sets[0],before.sets[0]);assert.deepEqual(after.sets[2],before.sets[2]);assert.equal(after.sets[1].valueOwner,'user');
  await page.$$eval('button',buttons=>buttons.find(b=>b.textContent.trim()==='Undo').click());await page.waitForFunction(w=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].weight===w,{},before.sets[1].weight);
  await open();await page.locator('[data-plateau-lighter]').click();await page.waitForFunction(w=>Number(JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].weight)<Number(w),{},before.sets[1].weight);
  after=await snapshot();await page.reload({waitUntil:'networkidle0'});await page.locator('[aria-label="Resume workout"]').click();await page.waitForSelector('.wpb-workout');assert.deepEqual((await snapshot()).sets,after.sets);
  assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved[0]),p,'optional load reduction never rewrites the saved program');
  await context.close();
 }
 assert.deepEqual(errors,[]);console.log('PASS B905 browser: real worker rebuild preview/cancel/apply, exact restore preview and reload persistence, unchanged history, narrow dark and portrait-tablet light dialogs; plateau choice/undo/reload with completed/manual protection.');
}finally{if(browser)await browser.close();await new Promise(r=>server.close(r));}
