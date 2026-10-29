import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'b993',name:'Information layout test',custom:true,weeks:4,
 config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',deload:false},
 days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
 overrides:{'upper:0':{sets:3,reps:'5-8',rir:'2',rest:90},'upper:1':{sets:2,reps:'8-12',rir:'2',rest:90}}};
const store={v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
 seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme:'amethyst',restAutoStart:false,
 warmupCard:false,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
 installDismissedAt:Date.now(),lastBackup:Date.now()};
const data=[{id:'bb-bench',slot:0,note:'',sets:[1,2,3].map(()=>({weight:'170',reps:'5',done:false,auto:false,valueOwner:'user',target:{w:'170',reps:'5-8',rir:2}}))},
 {id:'inc-curl',slot:1,note:'',sets:[1,2].map(()=>({weight:'25',reps:'8',done:false,auto:false,valueOwner:'user',target:{w:'25',reps:'8-12',rir:2}}))}];
const live={schemaVersion:2,sessionId:'b993-live',programId:program.id,dayId:'upper',weekIndex:1,
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
 const open=async()=>{
  await page.click('button[aria-label^="Exercise info for"]');
  await page.waitForSelector('.wpb-exinfo-sheet');
 };
 await open();
 assert.equal(await page.$eval('.wpb-exinfo-sheet',e=>e.getAttribute('role')),'dialog');
 assert.equal(await page.$eval('.wpb-exinfo-sheet',e=>e.getAttribute('aria-modal')),'true');
 assert.ok(await page.$eval('.wpb-exinfo-close',e=>document.activeElement===e),'opening places focus on Close');
 const summary=await page.$('.wpb-exinfo-why-disclosure');
 if(summary)assert.ok(await summary.evaluate(e=>e.getBoundingClientRect().height>=44),'Explanation disclosure must be 44px tall');
 await page.keyboard.down('Shift');await page.keyboard.press('Tab');await page.keyboard.up('Shift');
 const trapped=await page.$eval('.wpb-exinfo-sheet',sheet=>{
  const elements=[...sheet.querySelectorAll('button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled),summary,a[href]')]
    .filter(el=>el.getClientRects().length&&el.getAttribute('aria-disabled')!=='true'&&el.getAttribute('tabindex')!=='-1'&&!el.closest('[inert],[aria-hidden="true"]')&&(el.tagName==='SUMMARY'||!el.closest('details:not([open])'))&&getComputedStyle(el).visibility!=='hidden');
  return document.activeElement===elements.at(-1);
 });
 assert.ok(trapped,'Shift+Tab from Close wraps to the last eligible info-sheet control');
 await page.keyboard.press('Tab');
 assert.ok(await page.$eval('.wpb-exinfo-close',e=>document.activeElement===e),'Tab cycles back to Close');
 for(const [width,height,rootFont] of [[320,650,16],[320,650,32],[393,850,16],[834,900,24]]){
  await page.setViewport({width,height,isMobile:width<600,hasTouch:width<600,deviceScaleFactor:2});
  await page.evaluate(font=>document.documentElement.style.fontSize=font+'px',rootFont);
  const m=await page.$eval('.wpb-exinfo-sheet',panel=>{
   const r=panel.getBoundingClientRect(),close=panel.querySelector('.wpb-exinfo-close').getBoundingClientRect();
   const rest=[...panel.querySelectorAll('.wpb-exinfo-rest-step')].map(b=>b.getBoundingClientRect());
   const weight=[...panel.querySelectorAll('.wpb-exinfo-weight-choice')].map(b=>b.getBoundingClientRect());
   return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,height:innerHeight,width:innerWidth,
    clientWidth:panel.clientWidth,scrollWidth:panel.scrollWidth,close:[close.width,close.height],
    rest:rest.map(x=>[x.width,x.height]),weight:weight.map(x=>[x.width,x.height])};
  });
  assert.ok(m.left>=-1&&m.right<=width+1&&m.top>=-1&&m.bottom<=height+1&&m.scrollWidth<=m.clientWidth+2,
   'Info fits visible viewport without horizontal clipping: '+JSON.stringify({width,height,rootFont,m}));
  assert.ok(m.close[0]>=48&&m.close[1]>=48&&m.rest.length===2&&m.rest.every(([w,h])=>w>=48&&h>=48),
   'Info close/rest actions meet touch size: '+JSON.stringify(m));
  assert.ok(m.weight.every(([w,h])=>w>=44&&h>=44),'Load step choices retain touch targets');
 }
 await page.setViewport({width:393,height:850,isMobile:true,hasTouch:true,deviceScaleFactor:2});
 await page.evaluate(()=>document.documentElement.style.fontSize='16px');
 await page.click('.wpb-exinfo-rest-step:last-of-type').catch(()=>{});
 // Real value change must use the Workout state writer, not just change CSS.
 await page.$eval('.wpb-exinfo-rest-step',el=>el.click());
 await page.waitForFunction(()=>document.querySelector('.wpb-exinfo-rest-caption')?.textContent.includes('Custom'));
 await page.keyboard.press('Escape');
 await page.waitForFunction(()=>!document.querySelector('.wpb-exinfo-sheet'));
 assert.ok(await page.$eval('button[aria-label^="Exercise info for"]',el=>document.activeElement===el),
  'Escape restores focus to the original exercise Info action');
 assert.deepEqual(errors,[],'No page exceptions during info-sheet navigation');
 console.log('PASS B993 browser: responsive Workout Info, modality, keyboard cycle, touch targets, real rest changes and focus return.');
}finally{await browser?.close();await new Promise(done=>server.close(done));}
