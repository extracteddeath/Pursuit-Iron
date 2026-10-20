import assert from 'node:assert/strict';
import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import puppeteer from 'puppeteer-core';
import { program, history } from './b904-session-review-test.mjs';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const store={v:13,savedAt:1,saved:[program],cycles:[],history,perf:history[0].perf,drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:program.id,
 theme:'amethyst',unit:'kg',unitChosen:true,restAutoStart:false,warmupCard:false,experience:'intermediate',bodyweight:84,installDismissedAt:Date.now(),lastBackup:Date.now()};
const mime={'.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8804,'127.0.0.1',resolve));let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const errors=[];
 for(const width of [320,393,900]){
  const context=await browser.createBrowserContext(),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:850,isMobile:width<600,hasTouch:width<600});await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(store=>{if(!sessionStorage.getItem('review-seed')){localStorage.setItem('wpb:v1',JSON.stringify(store));sessionStorage.setItem('review-seed','1');}},{...store,theme:width===900?'rosewater':'amethyst',restScale:width===900?1.5:1,saved:[width===900?{...program,overrides:{...program.overrides,'upper:1':{...program.overrides['upper:1'],techOverride:'Last set: myo-reps'}}}:program]});
  await page.goto('http://127.0.0.1:8804/index.html',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');
  await page.waitForSelector('[aria-label="Review plan changes"]');
  const before=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.match(await page.$eval('.wpb-plan-change-banner',n=>n.innerText),/2 plan changes/);
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
  assert.match(await page.$eval('[data-plan-change="bb-bench"]',n=>n.innerText),/80 kg × 8/);
  assert.match(await page.$eval('[data-plan-change="bb-bench"]',n=>n.innerText),/top of the rep range/);
  assert.equal(await page.$eval('[data-plan-change="bb-bench"]',n=>!!n.querySelector('.wpb-game-plan-method,.wpb-plan-held-target')),false,
   'Changes does not repeat target RIR/sets or a second held prescription');
  await page.click('[aria-label="Undo change for Barbell Bench Press"]');
  await page.waitForSelector('[aria-label="Restore recommendation for Barbell Bench Press"]');
  assert.match(await page.$eval('[data-plan-change="bb-bench"]',n=>n.innerText),/undone/i);
  assert.match(await page.$eval('[data-plan-change="bb-bench"]',n=>n.innerText),/top of the rep range/,'the original engine reason remains visible after undo');
  assert.equal(await page.$eval('.wpb-session-dialog',n=>n.scrollWidth<=n.clientWidth+1),true);
  await page.waitForFunction(()=>document.querySelector('.wpb-plan-undo')?.getBoundingClientRect().height>=44);
  assert.ok(await page.$eval('.wpb-plan-undo',n=>parseFloat(getComputedStyle(n).minHeight)>=48));
  await page.screenshot({path:`/tmp/b904-changes-${width}.png`});
  await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('[aria-label="Plan changes"]'));
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/80 kg × 8/);
  const held=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.deepEqual(held.history,before.history);assert.deepEqual(held.perf,before.perf);assert.deepEqual(held.saved[0].progStyle,program.progStyle);assert.deepEqual(held.saved[0].overrides,before.saved[0].overrides);
  await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[data-game-plan-exercise="bb-bench"]');
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/80 kg × 8/);
  const clickText=async text=>assert.ok(await page.evaluate(text=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text);b?.click();return !!b;},text),text);
  await clickText('Preview · Time available');await page.waitForSelector('.wpb-next-workout-view');
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/80 kg × 8/);
  await page.click('[aria-label="Review plan changes"]');await page.waitForSelector('[aria-label="Restore recommendation for Barbell Bench Press"]');
  await page.click('[aria-label="Restore recommendation for Barbell Bench Press"]');await page.waitForSelector('[aria-label="Undo change for Barbell Bench Press"]');
  await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('[aria-label="Plan changes"]'));
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/82.5 kg × 6/);
  if(width===393){
   await page.click('[aria-label="Review plan changes"]');await page.click('[aria-label="Undo change for Barbell Bench Press"]');await page.waitForSelector('[aria-label="Restore recommendation for Barbell Bench Press"]');await page.keyboard.press('Escape');
   await clickText('20 min');await page.waitForSelector('[data-testid="session-length-summary"]');
   await page.click('[aria-label="Review plan changes"]');await page.waitForSelector('[aria-label="Undo change for Barbell Row"]');
   await page.click('[aria-label="Undo change for Barbell Row"]');await page.waitForSelector('[aria-label="Restore recommendation for Barbell Row"]');
   await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('[aria-label="Plan changes"]'));
   assert.match(await page.$eval('[data-game-plan-exercise="bb-row"]',n=>n.innerText),/90 kg × 8/,'undo applies in the shorter-session context');
   await page.click('[aria-label="Review plan changes"]');await page.click('[aria-label="Restore recommendation for Barbell Row"]');await page.waitForSelector('[aria-label="Undo change for Barbell Row"]');await page.keyboard.press('Escape');
   await clickText('Full');
   await clickText('Start workout');await page.waitForSelector('.wpb-workout');await clickText('Skip');
   await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.data?.some(r=>r.id==='bb-bench'));
   let live=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
   const work=live.data.find(r=>r.id==='bb-bench').sets.filter(s=>!s.warm&&!s.sub);
   assert.equal(work.length,3);assert.ok(work.every(s=>s.weight==='80'&&s.reps==='8'&&s.target.rir==='2'&&s.valueOwner==='user'));
   assert.ok(live.data.find(r=>r.id==='bb-row').sets.filter(s=>!s.warm&&!s.sub).every(s=>s.weight==='90'&&s.reps==='9'));
   await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[aria-label="Resume workout"]');await page.click('[aria-label="Resume workout"]');await page.waitForSelector('.wpb-workout');
   live=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));assert.ok(live.data[0].sets.filter(s=>!s.warm&&!s.sub).every(s=>s.weight==='80'&&s.reps==='8'),'resume keeps the chosen prescription');
  }
  await context.close();
 }
 assert.deepEqual(errors,[]);console.log('PASS B904 browser: Home review/game plan, truthful PRs, undo/restore, durable reload, preview/logger/resume parity and 320/393/900px themed layouts.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
