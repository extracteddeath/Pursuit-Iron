import assert from 'node:assert/strict';
import fs from 'node:fs'; import http from 'node:http'; import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { planShorterSession } from '../modules/engine-api.js';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'short-browser',name:'Short session probe',custom:true,weeks:6,engineV:70,
 config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',focus:{},deload:false},
 days:[{id:'day',label:'Full body',primaryIndex:0,exercises:['bb-bench','back-squat','inc-curl','cable-fly','lat-raise','pushdown']}],
 overrides:Object.fromEntries(Array.from({length:6},(_,i)=>[`day:${i}`,{sets:5,reps:i<2?'5-8':'10-15',rir:'2',rest:i<2?180:90,tech:i>1?'Myo-reps':null}]))};
const plan=planShorterSession(program,program.days[0],1,30),expected=Object.entries(plan.slots).filter(([,r])=>r.sets>0);
assert.equal(plan.fits,true);
const store={v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,
 pinnedId:program.id,theme:'amethyst',restAutoStart:false,warmupCard:false,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
 installDismissedAt:Date.now(),lastBackup:Date.now()};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml','.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const url=`http://127.0.0.1:${server.address().port}/index.html`;
const clickText=async(page,label)=>{await page.waitForFunction(label=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()===label),{},label);await page.evaluate(label=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===label).click(),label);};
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--disable-gpu']});
 for(const width of [320,430,900]){
  const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:880,deviceScaleFactor:1,isMobile:width<600,hasTouch:true});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(store=>{if(!localStorage.getItem('short-test-seeded')){localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('short-test-seeded','1');}},store);
  await page.goto(url,{waitUntil:'networkidle0'});await clickText(page,'Preview · Time available');
  await page.waitForSelector('[data-testid="session-length-control"]');
  for(const label of ['45 min','30 min','20 min','Full']){await clickText(page,label);assert.equal(await page.$eval(`[aria-label="Session length"] button[aria-pressed="true"]`,n=>n.textContent),label);}
  assert.equal(await page.$$eval('[data-nextex]',ns=>ns.length),6,'Full restores full roster');
  await clickText(page,'20 min');assert.match(await page.$eval('[data-testid="session-length-summary"]',n=>n.textContent),/Protected work needs/);
  assert.equal(await page.$$eval('button',ns=>ns.find(n=>n.textContent.trim()==='Start workout').disabled),true,'cannot start an infeasible budget');
  await clickText(page,'30 min');assert.equal(await page.$$eval('[data-nextex]',ns=>ns.length),expected.length);
  const optionsFit=await page.$$eval('[aria-label="Session length"] button',ns=>ns.every(n=>{const r=n.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1&&r.height>=44;}));assert.equal(optionsFit,true,'touch targets fit at '+width);
  if(process.env.SHORT_SESSION_SCREENSHOTS) await page.screenshot({path:`${process.env.SHORT_SESSION_SCREENSHOTS}/short-session-${width}-phone.png`,fullPage:true});
  await clickText(page,'Start workout');await page.waitForSelector('.wpb-workout');
  await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.startsWith('Feeling strong')));
  await page.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent.startsWith('Feeling strong')).click());
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')||'null')?.sessionLengthPlan?.minutes===30);
  const snap=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
  assert.deepEqual(snap.data.map(r=>[String(r.slot),r.sets.filter(s=>!s.warm&&!s.sub).length]),expected.map(([slot,r])=>[slot,r.sets]),'engine cuts realized at original slots');
  assert.deepEqual((await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved))[0].overrides,program.overrides,'saved program unaffected');
  // Real load entry, log, minimize and reload: reduced rows and user data must survive.
  await page.$eval('[data-testid="set-0-0"] input[aria-label="weight"]',n=>{const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(n,'135');n.dispatchEvent(new Event('input',{bubbles:true}));});
  await page.evaluate(()=>document.querySelector('[data-testid="set-0-0"] [aria-label="Mark set done"]').click());
  await page.evaluate(()=>window.dispatchEvent(new Event('wpb:system-back')));
  await page.waitForSelector('.wpb-live-dock');await page.reload({waitUntil:'networkidle0'});
  await page.click('.wpb-live-dock button[aria-label="Resume workout"]');await page.waitForSelector('.wpb-workout');
  const resumed=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
  assert.equal(resumed.sessionLengthPlan.minutes,30);assert.equal(resumed.data.length,snap.data.length);
  assert.equal(resumed.data[0].sets[0].weight,'135');assert.equal(resumed.data[0].sets[0].done,true);
  assert.deepEqual(resumed.data.map(r=>r.sets.length),snap.data.map(r=>r.sets.length),'omitted sets stay omitted on reload');
  assert.match(await page.$eval('.wpb-workout-summary',n=>n.textContent),/30 min plan/);
  await page.click('[aria-label="Finish workout"]');
  await clickText(page,'Save & finish');
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')||'{}').history?.length===1);
  const entry=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history[0]);
  assert.equal(entry.sessionLengthPlan.minutes,30);assert.equal(entry.prescriptionEdited,true);
  assert.equal(entry.totalSets,expected.reduce((n,[,r])=>n+r.sets,0),'completion denominator uses reduced session');
  assert.equal(entry.perf['bb-bench'].prescription.sets,plan.slots[0].sets);
  assert.deepEqual(errors,[]);console.log(`PASS shorter-session browser ${width}: preview, infeasible limit, start, log, minimize/reload, finish and immutable saved program`);
  await context.close();
 }
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
