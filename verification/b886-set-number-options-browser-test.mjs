import assert from 'node:assert/strict';
import fs from 'node:fs'; import http from 'node:http'; import path from 'node:path';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'b886-logger',custom:true,weeks:4,config:{unit:'lb',goal:'hypertrophy',experience:'intermediate',progression:'double',deload:false},
 days:[{id:'test',label:'Push',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
 overrides:{'test:0':{sets:3,reps:'5-8',rir:'2',rest:90},'test:1':{sets:2,reps:'8-12',rir:'2',rest:90}}};
const data=[{id:'bb-bench',slot:0,sets:[1,2,3].map(()=>({weight:'170',reps:'5',done:false,auto:false,valueOwner:'user',target:{w:'170',reps:'5-8',rir:2}}))},
 {id:'inc-curl',slot:1,sets:[1,2].map(()=>({weight:'25',reps:'8',done:false,auto:false,valueOwner:'user',target:{w:'25',reps:'8-12',rir:2}}))}];
const store={v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,
 pinnedId:program.id,theme:'amethyst',restAutoStart:false,warmupCard:false,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
 installDismissedAt:Date.now(),lastBackup:Date.now()};
const live={schemaVersion:2,programId:program.id,dayId:'test',weekIndex:1,dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:20000,
 readiness:{label:'Normal',factor:1},restPaused:false,restRemain:0,restMax:0,runPaused:true,data};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8789,'127.0.0.1',resolve));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(), errors=[]; page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 await page.evaluateOnNewDocument((store,live)=>{if(location.origin==='http://127.0.0.1:8789'){localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('wpb:live',JSON.stringify(live));}},store,live);
 await page.goto('http://127.0.0.1:8789/index.html',{waitUntil:'networkidle0'});
 await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.waitForSelector('[data-testid="set-0-2"]');
 assert.equal(await page.$('.wpb-complete-remaining'),null,'bulk complete action removed');
 const titleSize=await page.$eval('.wpb-exercise-heading .wpb-exercise-name',el=>parseFloat(getComputedStyle(el).fontSize));
 assert.ok(titleSize>=20,`exercise name reads as a headline, ${titleSize}px`);
 const open=()=>page.click('[data-testid="set-0-0"] [data-set-options-trigger]');
 const choose=type=>page.click(`[data-testid="workout-set-options"] button[data-set-type="${type}"]`);
 await open(); await page.waitForSelector('[data-testid="workout-set-options"]');
 const sheetGeometry=await page.$eval('[data-testid="workout-set-options"]',modal=>{
  const close=modal.querySelector('.wpb-b973-set-close');
  const r=close?.getBoundingClientRect();
  return {close:r?{w:r.width,h:r.height,l:r.left,right:r.right}:null,
   panel:modal.getBoundingClientRect().width,overflow:modal.scrollWidth>modal.clientWidth+1};
 });
 assert.ok(sheetGeometry.close?.w>=48&&sheetGeometry.close?.h>=48&&
  sheetGeometry.close.l>=-1&&sheetGeometry.close.right<=innerWidth+1&&!sheetGeometry.overflow,
  'Workout set options keeps Close touch target reachable on the phone: '+JSON.stringify(sheetGeometry));
 assert.equal(await page.$eval('[data-testid="workout-set-options"]',n=>n.getAttribute('aria-modal')),'true');
 assert.equal(await page.$$eval('[data-testid="workout-set-options"] [data-set-type]',a=>a.length),3);
 await choose('warmup');
 assert.equal(await page.$eval('[data-testid="set-0-0"]',n=>n.dataset.warm),'1');
 assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="weight"]',n=>n.value),'170','warm-up keeps manually chosen load');
 await open(); await choose('failure');
 assert.equal(await page.$eval('[data-testid="set-0-0"]',n=>n.dataset.warm),'0');
 await open(); assert.equal(await page.$eval('[data-set-type="failure"]',n=>n.getAttribute('aria-pressed')),'true');
 await choose('standard');
 assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="reps"]',n=>n.value),'5');
 assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="weight"]',n=>n.value),'170');
 await page.click('[data-testid="set-0-0"] .wpb-set-complete');
 await open(); assert.equal(await page.$$eval('[data-testid="workout-set-options"] [data-set-type]',a=>a.length),0,'completed set type is locked');
 await page.click('button[aria-label="Close set options"]');
 await page.click('.wpb-add-set'); await page.waitForSelector('[data-testid="set-0-3"]');
 await page.click('[data-testid="set-0-3"] [data-set-options-trigger]');
 assert.ok(await page.$('[data-set-remove]'),'only added rows offer removal');
 await page.click('[data-set-remove]'); assert.ok(await page.$('[data-testid="set-0-3"]'),'first removal click leaves row intact');
 await page.click('[data-set-remove]'); assert.equal(await page.$('[data-testid="set-0-3"]'),null,'confirmed removal only');
 assert.equal(await page.$eval('[data-testid="set-0-0"]',n=>n.dataset.done),'1','previously logged set unaffected');
 assert.deepEqual(errors,[],'No render errors');
 console.log('PASS B886 browser: tap set number → type selection, completed guard, confirmed removal, readable heading');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
