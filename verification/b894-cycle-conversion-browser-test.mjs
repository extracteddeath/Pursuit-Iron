import assert from 'node:assert/strict';
import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import puppeteer from 'puppeteer-core';
import { EXERCISES } from '../modules/App.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const cfg=JSON.parse(fs.readFileSync(path.join(root,'verification/m205-generation-parity-results.json'))).rows[3].config;
let n=0;const generated=generateNextProgramForShell({config:{...cfg,name:'My existing program',weeks:10},legacyExercises:EXERCISES,seed:894,makeId:()=>`b894-${++n}`}).program;
const mime={'.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const errors=[];
 for(const [kind,width,theme] of [['native',320,'amethyst'],['legacy',390,'amethyst'],['native',900,'rosewater']]){
  const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));await page.setViewport({width,height:844,isMobile:width<600,hasTouch:width<600});await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  const program=structuredClone(generated);if(kind==='legacy'){delete program.nextEngine;delete program.engineSource;program.custom=true;program.config.split='custom';program.overrides={...program.overrides,[program.days[0].id+':0']:{rir:2}};}
  const history=[{id:'old-history',programId:program.id,date:'2026-10-14',dayId:program.days[0].id,sets:{}}];
  const store={v:13,savedAt:1,saved:[program],cycles:[],history,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};
  await page.evaluateOnNewDocument(store=>{if(!sessionStorage.getItem('b894-seeded')){localStorage.setItem('wpb:v1',JSON.stringify(store));sessionStorage.setItem('b894-seeded','1');}},store);
  const open=async()=>{await page.goto(`http://127.0.0.1:${server.address().port}/index.html`,{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');await page.$eval(`button[aria-label="More options for ${program.name}"]`,n=>n.click());await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.includes('adaptive training cycle')));await page.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent.includes('adaptive training cycle')).click());await page.waitForSelector('[aria-label="Turn program into training cycle"]');};
  await open();
  const dialog='[aria-label="Turn program into training cycle"]';
  await page.$eval(dialog,n=>[...n.querySelectorAll('button')].find(b=>b.textContent.includes('Custom cycle')).click());
  await page.waitForSelector('.wpb-custom-cycle');
  await page.select('[aria-label="Block 2 training phase"]','peak');
  const weeks='[aria-label="Block 2 weeks"]';
  const replace=async value=>{await page.click(weeks);await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');await page.keyboard.press('Backspace');if(value)await page.type(weeks,value);};
  await replace('');
  assert.equal(await page.$eval(dialog,n=>[...n.querySelectorAll('button')].find(b=>b.textContent==='Create training cycle').disabled),true);
  // Closing and a page reload retain incomplete inputs without changing the program.
  await page.click(dialog+' button[aria-label="Close"]');await page.reload({waitUntil:'networkidle0'});await open();
  await page.waitForSelector('.wpb-custom-cycle');assert.equal(await page.$eval(weeks,n=>n.value),'');
  assert.equal(await page.$eval('[aria-label="Block 2 training phase"]',n=>n.value),'peak');
  await replace('2');await page.click('[aria-label="Remove block 3"]');
  assert.equal(await page.$$eval('.wpb-custom-cycle-block',ns=>ns.length),1);
  const bounds=await page.$$eval('.wpb-custom-cycle input,.wpb-custom-cycle select,.wpb-custom-cycle-block-head button',ns=>ns.every(n=>{const r=n.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth&&r.height>=44;}));assert.equal(bounds,true,kind+' controls fit '+width);
  assert.equal(await page.$eval(dialog,n=>{const r=n.getBoundingClientRect();const create=[...n.querySelectorAll('button')].find(b=>b.textContent==='Create training cycle').getBoundingClientRect();const close=n.querySelector('[aria-label="Close"]').getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight+1&&create.bottom<=innerHeight&&close.top>=0;}),true,'header and creation action remain within viewport');
  if(kind==='native'&&width===320)await page.screenshot({path:path.join(root,'verification/b894-conversion-phone.png')});
  const baseline=await page.evaluate(()=>{const s=JSON.parse(localStorage.getItem('wpb:v1'));return {saved:s.saved,history:s.history};});
  assert.deepEqual(baseline.saved,[program]);
  await page.$eval(dialog,n=>[...n.querySelectorAll('button')].find(b=>b.textContent==='Create training cycle').click());
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')||'{}').cycles?.length===1,{timeout:60000});
  const after=await page.evaluate(()=>({store:JSON.parse(localStorage.getItem('wpb:v1')),drafts:JSON.parse(localStorage.getItem('wpb:cycle-setup:v1')||'{}')}));
  assert.deepEqual(after.store.history,baseline.history,'conversion leaves actual workout history unchanged');
  const cycle=after.store.cycles[0],current=after.store.saved.find(p=>p.id===program.id),next=after.store.saved.find(p=>p.id===cycle.blockIds[1]);
  assert.equal(cycle.blockIds.length,2);assert.equal(cycle.blockIds[0],program.id);assert.equal(cycle.blockMeta[0].weeks,10);assert.equal(cycle.blockMeta[1].weeks,2);assert.equal(cycle.blockMeta[1].phase,'peak');
  assert.deepEqual(current.days,program.days);assert.deepEqual(current.overrides,program.overrides);assert.equal(next.nextEngine.cycleTemplate.plannedIndex,1);assert.equal(after.drafts['convert:'+program.id],undefined);
  // Older converted cycles may have the adaptive switches disabled. Their
  // real Cycle options must offer an in-place upgrade instead of forcing a new
  // cycle or rewriting training records.
  await page.evaluate(()=>{
    const s=JSON.parse(localStorage.getItem('wpb:v1'));
    const c=s.cycles[0];
    c.adaptExercises=false;c.adaptTrainingSplit=false;
    c.nextEngineCycle.adaptBetweenBlocks=false;c.nextEngineCycle.adaptTrainingSplit=false;
    localStorage.setItem('wpb:v1',JSON.stringify(s));
  });
  await page.reload({waitUntil:'networkidle0'});
  await page.waitForSelector('.wpb-home');
  await page.click('[aria-label="Open cycle My existing program Cycle"]');
  await page.waitForSelector('.wpb-cycle-detail');
  await page.click('[aria-label="Cycle options"]');
  await page.waitForSelector('.wpb-cycle-option-actions');
  await page.$eval('.wpb-cycle-option-actions',el=>{
    const b=[...el.querySelectorAll('button')].find(b=>b.textContent.trim()==='Upgrade future blocks to adaptive');
    if(!b)throw new Error('In-place cycle upgrade is missing from Cycle options');
    b.click();
  });
  await page.waitForFunction(()=>{
    const c=JSON.parse(localStorage.getItem('wpb:v1')||'{}').cycles?.[0];
    return c?.adaptExercises&&c?.adaptTrainingSplit&&c?.nextEngineCycle?.adaptBetweenBlocks&&c?.nextEngineCycle?.adaptTrainingSplit;
  });
  const upgraded=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.deepEqual(upgraded.history,baseline.history,'Adaptive opt-in preserves every workout log');
  assert.deepEqual(upgraded.saved,after.store.saved,'Adaptive opt-in cannot rewrite current or preview blocks');
  assert.deepEqual(upgraded.cycles[0].blockIds,cycle.blockIds,'Adaptation retains cycle block identity and all references');
  assert.deepEqual(upgraded.cycles[0].nextEngineCycle.plannedBlocks,cycle.nextEngineCycle.plannedBlocks);
  assert.equal(upgraded.cycles[0].activeBlock,0);
  assert.equal(upgraded.cycles[0].adaptiveUpgrade.awaitingBoundary,true);
  await page.reload({waitUntil:'networkidle0'});
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).cycles[0].adaptTrainingSplit),true);
  await page.close();
 }
 assert.deepEqual(errors,[]);
 console.log('PASS B894 real app: native/legacy custom conversion, one future block, close/reload draft recovery, protected history/roster/overrides, correct ownership and responsive controls at 320/390/900px in dark/light themes.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
