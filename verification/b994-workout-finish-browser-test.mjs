import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'b994',name:'Workout completion layout test',custom:true,weeks:4,
 config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',deload:false},
 days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
 overrides:{'upper:0':{sets:3,reps:'5-8',rir:'2',rest:90},'upper:1':{sets:2,reps:'8-12',rir:'2',rest:90}}};
const store={v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
 seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme:'amethyst',restAutoStart:false,
 warmupCard:false,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
 installDismissedAt:Date.now(),lastBackup:Date.now()};
const data=[{id:'bb-bench',slot:0,note:'',sets:[1,2,3].map((_,i)=>({weight:'170',reps:'5',done:i===0,auto:false,valueOwner:'user',target:{w:'170',reps:'5-8',rir:2}}))},
 {id:'inc-curl',slot:1,note:'',sets:[1,2].map(()=>({weight:'25',reps:'8',done:false,auto:false,valueOwner:'user',target:{w:'25',reps:'8-12',rir:2}}))}];
const live={schemaVersion:2,sessionId:'b994-live',programId:program.id,dayId:'upper',weekIndex:1,
 dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:20000,readiness:{label:'Normal',factor:1},
 restPaused:true,restRemain:0,restMax:0,runPaused:true,data};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json',
 '.png':'image/png','.svg':'image/svg+xml','.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
 const name=new URL(req.url,'http://localhost').pathname;
 const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
  res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
 res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 await page.setViewport({width:393,height:850,isMobile:true,hasTouch:true,deviceScaleFactor:2});
 await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 await page.evaluateOnNewDocument((state,session)=>{
  localStorage.setItem('wpb:v1',JSON.stringify(state));localStorage.setItem('wpb:live',JSON.stringify(session));
 },store,live);
 await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'networkidle0'});
 await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.waitForSelector('.wpb-workout');
 const openFinish=async()=>{
  await page.click('button[aria-label="Finish workout"]');
  await page.waitForSelector('.wpb-finish-sheet');
 };
 await openFinish();
 const initial=await page.$eval('.wpb-finish-metrics',el=>({
  tiles:el.querySelectorAll('.wpb-finish-metric').length,
  values:[...el.querySelectorAll('.wpb-finish-metric-value')].map(x=>x.textContent.trim()),
  labels:[...el.querySelectorAll('.wpb-finish-metric-label')].map(x=>x.textContent.trim())}));
 assert.deepEqual(initial.labels,['Sets','Volume','Time'],'live workout metrics are preserved');
 assert.equal(initial.tiles,3,'finish metrics keep three distinct tiles');
 assert.ok(await page.$('.wpb-finish-estimate'),'completed working set still yields real e1RM summary');
 assert.ok(await page.$('.wpb-finish-feedback-toggle'),'logged muscle opens optional recovery feedback');
 const readMetrics=()=>page.$eval('.wpb-finish-sheet',panel=>{
  const r=panel.getBoundingClientRect(),items=[...panel.querySelectorAll('.wpb-finish-metric')];
  const rect=el=>{const x=el.getBoundingClientRect();return {left:x.left,right:x.right,width:x.width,height:x.height}};
  return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,
   width:innerWidth,height:innerHeight,clientWidth:panel.clientWidth,scrollWidth:panel.scrollWidth,
   metricBounds:items.map(rect),metricOverflow:items.some(x=>x.scrollWidth>x.clientWidth+3),
   entryWhiteSpace:[...panel.querySelectorAll('.wpb-finish-entry-name')].map(el=>getComputedStyle(el).whiteSpace),
   actions:[...panel.querySelectorAll('.wpb-finish-save,.wpb-finish-share-action,.wpb-finish-feedback-toggle')].map(rect)};
 });
 for(const [width,height,font] of [[320,650,16],[320,650,32],[393,850,16],[834,900,24]]){
  await page.setViewport({width,height,isMobile:width<600,hasTouch:width<600,deviceScaleFactor:2});
  await page.evaluate(value=>{document.documentElement.style.fontSize=value+'px'},font);
  if(!(await page.$('.wpb-workout'))){
   await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
   await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
  }
  if(!(await page.$('.wpb-finish-sheet')))await openFinish();
  const m=await readMetrics();
  assert.ok(m.left>=-1&&m.right<=width+1&&m.top>=-1&&m.bottom<=height+1,
    'finish sheet stays in visible viewport: '+JSON.stringify({width,height,font,m}));
  assert.ok(m.scrollWidth<=m.clientWidth+2,'finish sheet does not overflow horizontally: '+JSON.stringify({width,height,font,m}));
  assert.ok(m.metricBounds.length===3&&m.metricBounds.every(x=>x.width>0&&x.left>=-1&&x.right<=width+1),
    'all finish metrics remain visible at small widths: '+JSON.stringify({width,height,font,m}));
  assert.ok(m.entryWhiteSpace.every(value=>value!=='nowrap'),'long movement names may wrap');
  assert.ok(m.actions.every(x=>x.height>=44),'completion actions meet touch size');
 }
 await page.setViewport({width:393,height:850,isMobile:true,hasTouch:true,deviceScaleFactor:2});
 await page.evaluate(()=>{document.documentElement.style.fontSize='16px'});
 if(!(await page.$('.wpb-workout'))){
  await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
  await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 }
 if(!(await page.$('.wpb-finish-sheet')))await openFinish();
 await page.click('.wpb-finish-feedback-toggle');
 await page.waitForSelector('.wpb-finish-rating-choice');
 const rating=await page.$('.wpb-finish-rating-choice');
 assert.equal(await rating.evaluate(el=>el.getAttribute('aria-pressed')),'false','recovery choice starts unselected');
 await rating.click();
 assert.equal(await rating.evaluate(el=>el.getAttribute('aria-pressed')),'true','recovery choice exposes selected state');
 const selected=await rating.evaluate(el=>({
  color:getComputedStyle(el).color,bg:getComputedStyle(el).backgroundColor,
  box:el.getBoundingClientRect().height}));
 assert.ok(selected.box>=44,'recovery choice has 44px minimum target');
 await page.click('.wpb-finish-feedback-toggle');
 assert.equal(await page.$eval('.wpb-finish-feedback-toggle',e=>e.getAttribute('aria-expanded')),'false','feedback disclosure closes');
 await page.click('.wpb-finish-keep-training');
 await page.waitForFunction(()=>!document.querySelector('.wpb-finish-sheet'));
 assert.ok(await page.$('[data-testid="set-0-0"]'),'Keep training returns to the same live workout');
 assert.deepEqual(errors,[],'no page errors in Workout completion layout and interaction');
 console.log('PASS B994 browser: completion stats, 320px scaled layout, accessible recovery selections and Keep training.');
}finally{await browser?.close();await new Promise(done=>server.close(done));}
