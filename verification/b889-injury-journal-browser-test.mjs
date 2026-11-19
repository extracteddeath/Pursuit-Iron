import assert from 'node:assert/strict';
import fs from 'node:fs'; import http from 'node:http'; import path from 'node:path';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'b888',name:'Session choices',custom:true,weeks:4,config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',deload:false},
 days:[{id:'test',label:'Push',primaryIndex:0,exercises:['bb-bench','inc-curl']}],overrides:{'test:0':{sets:3,reps:'5-8',rir:'2',rest:90},'test:1':{sets:2,reps:'8-12',rir:'2',rest:90}}};
const data=[{id:'bb-bench',slot:0,note:'',sets:[{weight:'170',reps:'5',done:true,auto:true,valueOwner:'prescription'},
 {weight:'175',reps:'7',done:false,auto:false,valueOwner:'user'}, {weight:'170',reps:'5',done:false,auto:true,valueOwner:'prescription'}].map(s=>({...s,target:{w:'170',reps:'5-8',rir:2}}))},
 {id:'inc-curl',slot:1,note:'',sets:[1,2].map(()=>({weight:'25',reps:'8',done:false,auto:true,valueOwner:'prescription',target:{w:'25',reps:'8-12',rir:2}}))}];
const store={v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,
 pinnedId:program.id,theme:'amethyst',restAutoStart:false,warmupCard:false,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
 installDismissedAt:Date.now(),lastBackup:Date.now()};
const live={schemaVersion:2,sessionId:'b888-live',programId:program.id,dayId:'test',weekIndex:1,dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:20000,
 readiness:{label:'Normal',factor:1},restPaused:false,restRemain:0,restMax:0,runPaused:true,data};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8799,'127.0.0.1',resolve));
let browser;
try {
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:393,height:850,isMobile:true,hasTouch:true});await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 await page.evaluateOnNewDocument((store,live)=>{if(!sessionStorage.getItem('seeded')){localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('wpb:live',JSON.stringify(live));sessionStorage.setItem('seeded','1');}},store,live);
 const clickText=async text=>{const ok=await page.evaluate(t=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===t);b?.click();return !!b;},text);assert.ok(ok,text);};
 const getStore=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
 await page.goto('http://127.0.0.1:8799/index.html',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');
 await clickText('Log injury');await page.waitForSelector('[data-injury-editor]');
 await clickText('Save injury');assert.equal((await getStore()).injuryLog?.length||0,0,'blank area cannot create an entry');
 await page.click('[data-injury-area="elbow"]');await clickText('Left');await page.click('[data-injury-level="sharp"]');
 assert.equal(await page.$eval('[data-injury-impact="skip"]',node=>node.getAttribute('aria-pressed')),'true','severity seeds the review preference');
 assert.ok(await page.$eval('.wpb-injury-preview',node=>node.textContent.includes('If applied: skip')),'severity previews the effect before applying any workout changes');
 await page.type('[aria-label="Injury details"]','Curls irritate my elbow');
 assert.ok(await page.$eval('.wpb-injury-preview',el=>el.textContent.includes('2 related lifts')),'preview includes primary and supporting involvement');
 await page.evaluate(()=>{const b=document.querySelector('.wpb-injury-save');b.click();b.click();});
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).injuryLog?.length===1);
 let entry=(await getStore()).injuryLog[0];const id=entry.id,created=entry.at;
 assert.equal(entry.side,'left');assert.equal(entry.region,'elbow');assert.equal(entry.level,'sharp');assert.equal(entry.impact,'skip');assert.match(entry.onset,/^\d{4}-\d{2}-\d{2}$/);
 await page.waitForSelector('[data-injury-id]');await page.click('[aria-label="Edit Left elbow"]');await page.waitForFunction(()=>document.querySelector('.wpb-injury-save')?.textContent==='Save changes');
 await clickText('Right');await page.click('[data-injury-impact="reduce"]');await clickText('Save changes');
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).injuryLog[0].side==='right');
 entry=(await getStore()).injuryLog[0];assert.equal(entry.id,id);assert.equal(entry.at,created);assert.equal(entry.level,'sharp','training preferences do not rewrite the reported severity');assert.equal(entry.impact,'reduce');assert.equal((await getStore()).injuryLog.length,1);
 await page.waitForFunction(()=>[...document.querySelectorAll('.wpb-injury-record-actions button')].some(b=>b.textContent==='Mark resolved'));await clickText('Mark resolved');await page.waitForFunction(()=>!!JSON.parse(localStorage.getItem('wpb:v1')).injuryLog[0].resolvedAt);
 await page.waitForSelector('.wpb-injury-resolved summary');await page.focus('.wpb-injury-resolved summary');await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>!!document.activeElement.closest('.wpb-injury-journal')),true,'keyboard focus stays inside the journal');await page.click('.wpb-injury-resolved summary');await clickText('Mark active');await page.waitForFunction(()=>!JSON.parse(localStorage.getItem('wpb:v1')).injuryLog[0].resolvedAt);
 await page.click('[aria-label="Close Injury log"]');assert.ok(await page.$eval('.wpb-home-health',el=>el.textContent.includes('Right elbow')));
 await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.waitForSelector('.wpb-workout');await page.evaluate(()=>[...document.querySelectorAll('.wpb-session-context button')].find(b=>b.textContent.includes('active injury')).click());
 await page.waitForSelector('[data-injury-sheet]');assert.equal(await page.$eval('[aria-label="Choose logged injury"]',el=>el.value),id);
 assert.equal(await page.$$eval('[data-injury-sheet] input[type="checkbox"]',els=>els.length),2);
 assert.notEqual(await page.$eval('.wpb-injury-reduce',el=>getComputedStyle(el).backgroundColor),await page.$eval('.wpb-injury-skip',el=>getComputedStyle(el).backgroundColor),'preferred reduction has a distinct primary action');
 await page.click('[data-injury-sheet] input[type="checkbox"]');await page.click('.wpb-injury-reduce');
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).injuryReports?.length===1);
 const snap=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));assert.equal(snap.injuryReports[0].injuryId,id);
 assert.equal(snap.data[0].sets[0].done,true);assert.equal(snap.data[0].sets[1].weight,'175');
 await page.click('[aria-label="Finish workout"]');await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Save & finish'));await clickText('Save & finish');
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).injuryLog[0].lastAdjustment?.action==='reduce');
 entry=(await getStore()).injuryLog[0];assert.equal((await getStore()).injuryLog.length,1,'workout review updates the source injury rather than duplicating it');assert.equal(entry.adjustments.length,1);assert.equal(entry.side,'right');
 await page.close();
 for(const [width,theme] of [[320,'rosewater'],[900,'amethyst']]) {
  const p=await browser.newPage();p.on('pageerror',e=>errors.push(e.message));await p.setViewport({width,height:850,isMobile:width<600,hasTouch:width<600});
  await p.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await p.evaluateOnNewDocument(store=>localStorage.setItem('wpb:v1',JSON.stringify({...store,injuryLog:[]})),{...store,theme});
  await p.goto('http://127.0.0.1:8799/index.html',{waitUntil:'networkidle0'});await p.waitForSelector('.wpb-home-health');
  await p.evaluate(()=>[...document.querySelectorAll('.wpb-home-health button')].find(b=>b.textContent==='Log injury').click());await p.waitForSelector('[data-injury-editor]');
  await p.click('[data-injury-area="shoulder"]');
  const box=await p.$eval('.wpb-injury-journal',el=>({w:el.clientWidth,scroll:el.scrollWidth,bg:getComputedStyle(el).backgroundColor}));assert.ok(box.scroll<=box.w+1);assert.ok(/^rgb\(/.test(box.bg));
  await p.screenshot({path:`/tmp/b889-injury-${width}.png`});
  await p.type('[aria-label="Injury details"]','Unsaved detail');await p.click('[aria-label="Close Injury log"]');await p.waitForSelector('.wpb-injury-discard');
  await p.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent==='Keep editing').click());assert.equal(await p.$eval('[aria-label="Injury details"]',el=>el.value),'Unsaved detail');
  await p.close();
 }
 assert.deepEqual(errors,[]);console.log('PASS B889 browser: required area, duplicate-save guard, side and preference, edit/resolve/reopen, workout source linking, protected sets and 320/900 layouts');
} finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
