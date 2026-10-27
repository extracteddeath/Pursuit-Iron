import assert from 'node:assert/strict';
import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import puppeteer from 'puppeteer-core';
import { program, history, alternate, alternateHistory } from './b904-session-review-test.mjs';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const store={v:13,savedAt:1,saved:[program],cycles:[],history,perf:history[0].perf,drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:program.id,
 theme:'amethyst',unit:'kg',unitChosen:true,restAutoStart:false,warmupCard:false,experience:'intermediate',bodyweight:84,installDismissedAt:Date.now(),lastBackup:Date.now()};
const mime={'.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8804,'127.0.0.1',resolve));let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const errors=[];
 const reveal=async (page,id)=>{const key=`[data-plan-change="${id}"] .wpb-plan-change-more`;if(!await page.$eval(key,node=>node.open))await page.click(key+' summary');};
 for(const width of [320,393,900]){
  const context=await browser.createBrowserContext(),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:850,isMobile:width<600,hasTouch:width<600});await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(store=>{if(!sessionStorage.getItem('review-seed')){localStorage.setItem('wpb:v1',JSON.stringify(store));sessionStorage.setItem('review-seed','1');}},{...store,theme:width===900?'rosewater':'amethyst',restScale:width===900?1.5:1,saved:[width===900?{...program,overrides:{...program.overrides,'upper:1':{...program.overrides['upper:1'],techOverride:'Last set: myo-reps'}}}:program]});
  await page.goto('http://127.0.0.1:8804/index.html',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');
  await page.waitForSelector('[aria-label="Review plan changes"]');
  const before=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.match(await page.$eval('.wpb-plan-change-banner',n=>n.innerText),/1 upcoming adjustment/);
  const homeCoach=await page.evaluate(()=>{
   const hero=document.querySelector('.wpb-home-hero'),game=document.querySelector('.wpb-home-game-plan');
   const outline=hero?.querySelector('.wpb-home-session-outline');
   const rows=[...document.querySelectorAll('.wpb-home-game-plan [data-game-plan-exercise]')];
   return {separate:!!game&&!hero.contains(game)&&!!(hero.compareDocumentPosition(game)&Node.DOCUMENT_POSITION_FOLLOWING),
    outline:!!outline&&!!outline.querySelector('.wpb-home-outline-list'),count:rows.length,
    isShort:!game?.querySelector('.wpb-game-plan-target,.wpb-game-plan-method,.wpb-game-plan-technique'),
    overflow:document.querySelector('.wpb-home').scrollWidth>document.querySelector('.wpb-home').clientWidth+1};
  });
  assert.ok(homeCoach.separate&&homeCoach.outline&&homeCoach.count>0&&homeCoach.count<=3&&homeCoach.isShort&&!homeCoach.overflow,
   'Home has its own concise coaching card, not a second workout outline: '+JSON.stringify(homeCoach));
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/82.5 kg × 6/);
  assert.match(await page.$eval('[data-game-plan-exercise="bb-row"]',n=>n.innerText),/90 kg × 9/);
  if(width===900)assert.match(await page.$eval('[data-game-plan-exercise="bb-row"]',n=>n.innerText),/23-second rests/,'game plan honors the logger rest scale');
  assert.ok(await page.$('[data-pr-exercise="bb-row"]'));assert.equal(await page.$('[data-pr-exercise="bb-bench"]'),null,'no fabricated RIR-adjusted PR');
  assert.equal(await page.$eval('.wpb-home',n=>n.scrollWidth<=n.clientWidth+1),true);
  await page.screenshot({path:`/tmp/b904-home-${width}.png`});
  await page.click('[aria-label="Review plan changes"]');await page.waitForSelector('[aria-label="Plan changes"]');
  assert.equal(await page.$('[data-plan-change="bb-row"]'),null,'ordinary row rep progress has no undo');
  assert.match(await page.$eval('[data-plan-change="bb-bench"]',n=>n.innerText),/80 → 82.5 kg/);
  await reveal(page,'bb-bench');
  assert.match(await page.$eval('[data-plan-change="bb-bench"]',n=>n.innerText),/top of the rep range/);
  assert.equal(await page.$eval('[data-plan-change="bb-bench"]',n=>!!n.querySelector('.wpb-game-plan-method,.wpb-plan-held-target')),false,
   'Changes does not repeat target RIR/sets or a second held prescription');
  await page.click('[aria-label="Keep previous load for Barbell Bench Press"]');
  await page.waitForSelector('[aria-label="Use recommended load for Barbell Bench Press"]');
  assert.match(await page.$eval('[data-plan-change="bb-bench"]',n=>n.innerText),/Prior load kept/i);
  await reveal(page,'bb-bench');
  assert.match(await page.$eval('[data-plan-change="bb-bench"]',n=>n.innerText),/top of the rep range/,'engine reason remains on demand after holding the prior load');
  assert.equal(await page.$eval('.wpb-session-dialog',n=>n.scrollWidth<=n.clientWidth+1),true);
  await page.waitForFunction(()=>document.querySelector('.wpb-plan-undo')?.getBoundingClientRect().height>=44);
  assert.ok(await page.$eval('.wpb-plan-undo',n=>parseFloat(getComputedStyle(n).minHeight)>=48));
  await page.screenshot({path:`/tmp/b904-changes-${width}.png`});
  await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('[aria-label="Plan changes"]'));
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/80 kg × 6/);
  const held=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.deepEqual(held.history,before.history);assert.deepEqual(held.perf,before.perf);assert.deepEqual(held.saved[0].progStyle,program.progStyle);assert.deepEqual(held.saved[0].overrides,before.saved[0].overrides);
  await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[data-game-plan-exercise="bb-bench"]');
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/80 kg × 6/);
  const clickText=async text=>assert.ok(await page.evaluate(text=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text);b?.click();return !!b;},text),text);
  await clickText('Preview · Time available');await page.waitForSelector('.wpb-next-workout-view');
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/80 kg × 6/);
  await page.click('[aria-label="Review plan changes"]');await page.waitForSelector('[aria-label="Use recommended load for Barbell Bench Press"]');
  await page.click('[aria-label="Use recommended load for Barbell Bench Press"]');await page.waitForSelector('[aria-label="Keep previous load for Barbell Bench Press"]');
  await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('[aria-label="Plan changes"]'));
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/82.5 kg × 6/);
  if(width===393){
   await page.click('[aria-label="Review plan changes"]');await page.click('[aria-label="Keep previous load for Barbell Bench Press"]');await page.waitForSelector('[aria-label="Use recommended load for Barbell Bench Press"]');await page.keyboard.press('Escape');
   await clickText('20 min');await page.waitForSelector('[data-testid="session-length-summary"]');
   await page.click('[aria-label="Review plan changes"]');
   assert.equal(await page.$('[data-plan-change="bb-row"]'),null,'rep-only changes cannot be undone even in shortened workouts');
   await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('[aria-label="Plan changes"]'));
   assert.match(await page.$eval('[data-game-plan-exercise="bb-row"]',n=>n.innerText),/90 kg × 9/,'shortening retains normal row progression');
   await clickText('Full');
   await clickText('Start workout');await page.waitForSelector('.wpb-workout');await clickText('Skip');
   await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.data?.some(r=>r.id==='bb-bench'));
   let live=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
   const work=live.data.find(r=>r.id==='bb-bench').sets.filter(s=>!s.warm&&!s.sub);
   assert.equal(work.length,3);assert.ok(work.every(s=>s.weight==='80'&&s.reps==='6'&&s.target.rir==='2'&&s.valueOwner==='user'));
   assert.ok(live.data.find(r=>r.id==='bb-row').sets.filter(s=>!s.warm&&!s.sub).every(s=>s.weight==='90'&&s.reps==='9'));
   await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[aria-label="Resume workout"]');await page.click('[aria-label="Resume workout"]');await page.waitForSelector('.wpb-workout');
   live=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));assert.ok(live.data[0].sets.filter(s=>!s.warm&&!s.sub).every(s=>s.weight==='80'&&s.reps==='6'),'resume keeps the chosen prescription');
  }
  await context.close();
 }
 // M325: real alternate-week choice survives reload and reaches the workout logger.
 for(const width of [320,900]){
  const context=await browser.createBrowserContext(),page=await context.newPage();
  page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:850,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  const source=alternateHistory[0];
  const seededHistory=[{...source,id:'base-first',weekIndex:1,date:source.date-86400000,perf:{'bb-bench':source.perf['bb-bench']}},
   source,{...source,id:'base-third',weekIndex:3,date:source.date+86400000,perf:{'bb-bench':source.perf['bb-bench']}}];
  await page.evaluateOnNewDocument(data=>{if(!sessionStorage.getItem('m325-seed')){
   localStorage.setItem('wpb:v1',JSON.stringify(data));sessionStorage.setItem('m325-seed','1');}},
   {...store,saved:[alternate],history:seededHistory,perf:{},pinnedId:alternate.id,unit:'lb',theme:width===900?'rosewater':'amethyst'});
  await page.goto('http://127.0.0.1:8804/index.html',{waitUntil:'networkidle0'});
  await page.waitForSelector('[data-game-plan-exercise="db-bench"]');
  const baseline=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  await page.click('[aria-label="Review plan changes"]');
  await page.waitForSelector('[aria-label="Keep previous load for Dumbbell Bench Press"]');
  assert.match(await page.$eval('.wpb-plan-review-intro',n=>n.innerText),/Week 4.*upcoming workout/);
  assert.match(await page.$eval('[data-plan-change="db-bench"]',n=>n.innerText),/50 → 55 lb/);
  await reveal(page,'db-bench');
  const card=await page.$eval('[data-plan-change="db-bench"]',n=>n.innerText);
  assert.match(card,/50 lb × 8/);assert.match(card,/55 lb × 6/);assert.match(card,/same workout day/);
  await page.click('[aria-label="Keep previous load for Dumbbell Bench Press"]');
  await page.waitForSelector('[aria-label="Use recommended load for Dumbbell Bench Press"]');
  await reveal(page,'db-bench');
  assert.equal(await page.$eval('[data-plan-change="db-bench"] .wpb-plan-target-diff strong',n=>n.innerText),'50 lb × 6');
  assert.equal(await page.$eval('[data-plan-change="db-bench"] .wpb-plan-before',n=>n.innerText),'50 lb × 8');
  assert.match(await page.$eval('[data-plan-change="db-bench"] .wpb-plan-change-reason',n=>n.innerText),/Original recommendation:/);
  await page.screenshot({path:path.join(root,`verification/m325-review-${width}-phone.png`)});
  await page.keyboard.press('Escape');
  await page.reload({waitUntil:'networkidle0'});
  await page.waitForSelector('[data-game-plan-exercise="db-bench"]');
  assert.match(await page.$eval('[data-game-plan-exercise="db-bench"]',n=>n.innerText),/50 lb × 6/);
  await page.click('[aria-label="Review plan changes"]');
  await page.click('[aria-label="Use recommended load for Dumbbell Bench Press"]');
  await page.waitForSelector('[aria-label="Keep previous load for Dumbbell Bench Press"]');
  await page.keyboard.press('Escape');
  assert.match(await page.$eval('[data-game-plan-exercise="db-bench"]',n=>n.innerText),/55 lb × 6/);
  await page.click('[aria-label="Review plan changes"]');
  await page.click('[aria-label="Keep previous load for Dumbbell Bench Press"]');
  await page.waitForSelector('[aria-label="Use recommended load for Dumbbell Bench Press"]');
  await page.keyboard.press('Escape');
  await page.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Preview · Time available').click());
  await page.waitForSelector('.wpb-next-workout-view');
  await page.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Start workout').click());
  await page.waitForSelector('.wpb-workout');
  await page.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Skip').click());
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.data?.some(r=>r.id==='db-bench'));
  const live=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
  const sets=live.data.find(r=>r.id==='db-bench').sets.filter(s=>!s.warm&&!s.sub);
  assert.equal(live.weekIndex,4);assert.equal(sets.length,3);
  assert.ok(sets.every(s=>s.weight==='50'&&s.reps==='6'&&s.valueOwner==='user'));
  const after=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.deepEqual(after.history,baseline.history);assert.deepEqual(after.perf,baseline.perf);
  assert.deepEqual(after.saved[0].pairs,baseline.saved[0].pairs);
  assert.deepEqual(after.saved[0].overrides,baseline.saved[0].overrides);
  await context.close();
 }
 assert.deepEqual(errors,[]);console.log('PASS B904 browser: Home review/game plan, truthful PRs, undo/restore, durable reload, preview/logger/resume parity and 320/393/900px themed layouts.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
