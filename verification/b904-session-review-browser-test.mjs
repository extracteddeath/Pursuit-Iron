import assert from 'node:assert/strict';
import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import puppeteer from 'puppeteer-core';
import { program, history, alternate, alternateHistory } from './b904-session-review-test.mjs';
const root=path.resolve(new URL('../',import.meta.url).pathname);

// B994: the standalone diagnostic CLI is part of the full web distribution.
// Run it here, not in the isolated engine snapshot which intentionally omits scripts/.
{
 const {spawnSync}=await import('node:child_process');
 const {mkdtempSync,writeFileSync,rmSync}=await import('node:fs');
 const {tmpdir}=await import('node:os');
 const {join}=await import('node:path');
 const temp=mkdtempSync(join(tmpdir(),'pursuit-replay-'));
 const fixture=thirdLoad=>({
  schemaVersion:1,kind:'pursuit-iron-progression-replay',exerciseId:'rear-fly',
  slot:0,weekIndex:1,unit:'lb',progressionStyle:'double',
  suggestion:{action:'add_reps',setTargets:[0,1,2].map(i=>({load:i===2?thirdLoad:60,reps:21}))},
  history:[{weekIndex:1,unit:'lb',sets:[0,1,2].map(setIndex=>({setIndex,load:60,reps:20,reportedRir:2}))}],
  rows:[0,1,2].map(i=>({rowIndex:i,workingSetIndex:i,kind:'work',done:false,
   owner:'prescription',load:i===2?thirdLoad:60,reps:21,
   target:{load:i===2?thirdLoad:60,reps:21},trace:null,liveAdjustment:null,
   readinessFactor:null}))
 });
 try {
  const file=join(temp,'diagnostics.json');
  const run=payload=>{
   writeFileSync(file,JSON.stringify(payload));
   return spawnSync(process.execPath,[path.join(root,'scripts/audit-workout-replay.mjs'),file],
    {cwd:root,encoding:'utf8'});
  };
  const valid=run(fixture(60));
  assert.equal(valid.status,0,'Unchanged three-position double loads pass replay: '+valid.stdout+valid.stderr);
  const unsafe=run(fixture(55));
  assert.equal(unsafe.status,1,'An unearned third-set 55 lb load must fail replay');
  assert.match(unsafe.stdout,/unearned_double_progression_history_load/);
  assert.match(unsafe.stdout,/unearned_double_progression_suggestion_load/);
 } finally { rmSync(temp,{recursive:true,force:true}); }
}
console.log('PASS B994 independent replay: same-position 60×20 history cannot prescribe 55×21.');

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
  await page.waitForSelector('.wpb-plan-change-banner[aria-label^="Review "]');
  const before=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.match(await page.$eval('.wpb-plan-change-banner',n=>n.innerText),/1 plan adjustment/);
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
  if(width===900){
   assert.match(await page.$eval('[data-game-plan-exercise="bb-row"]',n=>n.innerText),/Myo-reps/,
    'Collapsed Game Plan identifies the technique without full instructions');
   await page.click('[data-game-plan-exercise="bb-row"] summary');
   assert.match(await page.$eval('[data-game-plan-exercise="bb-row"] details>p',n=>n.innerText),/23-second rests/,
    'Expanded Game Plan retains the logger-scaled Myo-reps rest interval');
   await page.click('[data-game-plan-exercise="bb-row"] summary');
   assert.equal(await page.$eval('[data-game-plan-exercise="bb-row"] details',n=>n.open),false,
    'Leave the Home Game Plan collapsed before continuing the workout journey');
  }
  assert.ok(await page.$('[data-pr-exercise="bb-row"]'));assert.equal(await page.$('[data-pr-exercise="bb-bench"]'),null,'no fabricated RIR-adjusted PR');
  assert.equal(await page.$eval('.wpb-home',n=>n.scrollWidth<=n.clientWidth+1),true);
  await page.screenshot({path:`/tmp/b904-home-${width}.png`});
  await page.click('.wpb-plan-change-banner[aria-label^="Review "]');await page.waitForSelector('[aria-label="Plan changes"]');
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
  const previewChange=await page.$('.wpb-plan-change-banner[aria-label^="Review "]');
  if(!previewChange){
   const evidence=await page.evaluate(()=>{
      const text=document.querySelector('.wpb-next-workout-view')?.innerText||'';
      const saved=JSON.parse(localStorage.getItem('wpb:v1')||'null');
      return {heading:text.slice(0,550),choices:saved?.saved?.[0]?.sessionPlanChoices,
        hasReview:!!document.querySelector('.wpb-plan-change-banner'),
        homeGamePlan:document.querySelector('[data-game-plan-exercise="bb-bench"]')?.innerText};
   });
   throw new Error('Preview hid a previously held load adjustment: '+JSON.stringify(evidence));
  }
  await page.click('.wpb-plan-change-banner[aria-label^="Review "]');await page.waitForSelector('[aria-label="Use recommended load for Barbell Bench Press"]');
  await page.click('[aria-label="Use recommended load for Barbell Bench Press"]');await page.waitForSelector('[aria-label="Keep previous load for Barbell Bench Press"]');
  await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('[aria-label="Plan changes"]'));
  assert.match(await page.$eval('[data-game-plan-exercise="bb-bench"]',n=>n.innerText),/82.5 kg × 6/);
  if(width===393){
   await page.click('.wpb-plan-change-banner[aria-label^="Review "]');await page.click('[aria-label="Keep previous load for Barbell Bench Press"]');await page.waitForSelector('[aria-label="Use recommended load for Barbell Bench Press"]');await page.keyboard.press('Escape');
   await clickText('20 min');await page.waitForSelector('[data-testid="session-length-summary"]');
   const shortenedBanner=await page.$('.wpb-plan-change-banner[aria-label^="Review "]');
   const shortState=await page.evaluate(()=>{
    const saved=JSON.parse(localStorage.getItem('wpb:v1')||'null');
    return { choice:saved?.saved?.[0]?.sessionPlanChoices?.['upper:0:bb-bench'],
      bench:document.querySelector('[data-game-plan-exercise="bb-bench"]')?.innerText,
      row:document.querySelector('[data-game-plan-exercise="bb-row"]')?.innerText };
   });
   assert.ok(shortState.choice?.key,
    'Shortening must preserve the previously approved load decision in saved program state');
   assert.equal(shortenedBanner,null,
    'A 3-set history cannot earn a new 2-set shortened-session load; no pending change means no review');
   assert.match(shortState.bench,/80 kg × 8/,
    'The shorter plan holds the previously trained load without inventing a new increase');
   assert.match(shortState.row,/90 kg × 8/,
    'The shortened 2-set prescription holds the last comparable 90-kg working load');
   assert.doesNotMatch(shortState.row,/Different set counts cannot earn an automatic load change/,
    'Collapsed Game Plan stays concise until the athlete asks why');
   await page.click('[data-game-plan-exercise="bb-row"] summary');
   assert.match(await page.$eval('[data-game-plan-exercise="bb-row"] details>p',n=>n.innerText),
    /Different set counts cannot earn an automatic load change/,
    'Full progression safety explanation remains available on demand');
   // Return the disclosure to its original collapsed state before testing
   // Full/Short session target previews; open details intentionally hides them.
   await page.click('[data-game-plan-exercise="bb-row"] summary');
   assert.equal(await page.$eval('[data-game-plan-exercise="bb-row"] details',n=>n.open),false);
   await clickText('Full');
   await page.waitForFunction(()=>{
    const row=document.querySelector('[data-game-plan-exercise="bb-row"]')?.innerText||'';
    const bench=document.querySelector('[data-game-plan-exercise="bb-bench"]')?.innerText||'';
    return row.includes('90 kg × 9') && bench.includes('80 kg × 6');
   });
   assert.match(await page.$eval('[data-game-plan-exercise="bb-row"]',n=>n.innerText),/90 kg × 9/,
    'Restoring Full re-enables ordinary rep progression without changing logged history');
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
  await page.click('.wpb-plan-change-banner[aria-label^="Review "]');
  await page.waitForSelector('[aria-label="Keep previous load for Dumbbell Bench Press"]');
  assert.match(await page.$eval('.wpb-plan-review-intro',n=>n.innerText),/Upper · Week 4/,
   'Plan review heading identifies the exact week without crowding its scope');
  assert.match(await page.$eval('.wpb-plan-changes-body>.wpb-plan-review-basis',n=>n.innerText),
   /Load choices apply to this workout.*Completed history stays intact/,
   'Separate scope explanation remains visible in the improved review hierarchy');
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
  await page.click('.wpb-plan-change-banner[aria-label^="Review "]');
  await page.click('[aria-label="Use recommended load for Dumbbell Bench Press"]');
  await page.waitForSelector('[aria-label="Keep previous load for Dumbbell Bench Press"]');
  await page.keyboard.press('Escape');
  assert.match(await page.$eval('[data-game-plan-exercise="db-bench"]',n=>n.innerText),/55 lb × 6/);
  await page.click('.wpb-plan-change-banner[aria-label^="Review "]');
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
