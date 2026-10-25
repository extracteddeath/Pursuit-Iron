import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
const root = path.resolve(new URL('../', import.meta.url).pathname);
const program = { id:'b860-preview',name:'Full Body Review — Strength and Hypertrophy with a deliberately long program name',custom:true,weeks:4,
 config:{name:'Full Body Review',goal:'both',experience:'intermediate',progression:'double',split:'custom',weeks:4,days:2,session:'s60',unit:'lb',deload:true},
 days:[{id:'bench',label:'Full Body — Bench focus',primaryIndex:0,exercises:['bb-bench','inc-curl']},{id:'squat',label:'Full Body — Squat focus',primaryIndex:0,exercises:['back-squat','leg-press']}],
 overrides:{'bench:0':{sets:2,reps:'6-8',rir:'2'},'bench:1':{sets:3,reps:'10-15',rir:'2'},'squat:0':{sets:2,reps:'6-8',rir:'2'},'squat:1':{sets:3,reps:'10-15',rir:'2'}} };
const initial = theme => ({v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.webmanifest':'application/manifest+json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const name=new URL(req.url,'http://localhost').pathname;const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8806,'127.0.0.1',resolve));
let browser;
try {
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 for(const theme of ['rose','rosewater','amethyst']) for(const [width,height] of [[320,740],[393,852],[900,1000],[1280,850]]) {
  const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  try {
   await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
   await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
   await page.evaluateOnNewDocument(seed=>{if(!localStorage.getItem('b860-seeded')){localStorage.setItem('b860-seeded','1');localStorage.setItem('wpb:v1',JSON.stringify(seed));}},initial(theme));
   await page.goto('http://127.0.0.1:8806/',{waitUntil:'networkidle0'});
   await page.waitForSelector('.wpb-tabbar');
   if(theme!=='rosewater') assert.equal(await page.$eval('.wpb-home-hero',el=>getComputedStyle(el).backgroundColor),await page.evaluate(()=>{
     const home=document.querySelector('.wpb-home');const c=document.createElement('div');c.style.background='var(--pi-color-feature)';home.append(c);const value=getComputedStyle(c).backgroundColor;c.remove();return value;
   }),'Home uses the subdued derived feature role without a separate red treatment');
   await page.click('[data-tab="plan"]');await page.waitForSelector('.wpb-plan-session-link');
   const first=await page.$('.wpb-plan-session-link');await first.evaluate(el=>el.scrollIntoView({block:'center'}));await first.click();
   await page.waitForSelector('.wpb-next-workout-view');
   assert.equal(await page.$eval('.wpb-next-workout-view h1',el=>el.textContent),'Bench focus');
   assert.equal(await page.$eval('.wpb-next-workout-view h1',el=>el.getAttribute('aria-label')),program.days[0].label);
   assert.match(await page.$eval('.wpb-next-workout-view',el=>el.textContent),/5 working sets/);
   assert.match(await page.$eval('.wpb-next-workout-view',el=>el.textContent),/Week 1 of 5/);
   const startReachable=()=>page.$eval('.wpb-preview-start',button=>{
    const r=button.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);
    return r.y>=0&&r.bottom<=innerHeight&&r.height>=48&&!!hit&&button.contains(hit);
   });
   assert.equal(await startReachable(),true,'Start is immediately reachable without scrolling');
   assert.equal(await page.evaluate(()=>document.querySelector('[data-nextex]')
    .compareDocumentPosition(document.querySelector('.wpb-session-game-plan'))&Node.DOCUMENT_POSITION_FOLLOWING),4,
    'Saved exercise prescriptions precede optional coaching');
   assert.equal(await page.$eval('.wpb-preview-program-name',el=>el.textContent),program.name);
   assert.equal(await page.$eval('.wpb-preview-program-name',el=>getComputedStyle(el).textOverflow),'clip',
    'Program identity stays readable rather than ellipsized');
   await page.$eval('.wpb-next-workout-view',el=>{el.scrollTop=el.scrollHeight;});
   assert.equal(await startReachable(),true,'Start stays reachable after scrolling to supporting detail');
   await page.$eval('.wpb-next-workout-view',el=>{el.scrollTop=0;});
   const prescriptions=await page.$$eval('[data-nextex]',rows=>rows.map(el=>el.textContent));
   assert.ok(prescriptions[0].includes('2×6-8'));assert.ok(prescriptions[1].includes('3×10-15'));
   await page.screenshot({path:path.join(root,`verification/b860-preview-${theme}-${width}-phone.png`)});
   // Text-only enlargement keeps identity, prescriptions and complete exercise names accessible.
   await page.evaluate(()=>{window.__b860Text=[...document.querySelectorAll('.wpb-next-workout-shell,.wpb-next-workout-shell *')].map(el=>({el,value:el.style.getPropertyValue('font-size'),priority:el.style.getPropertyPriority('font-size')}));window.__b860Text.forEach(({el})=>{const px=parseFloat(getComputedStyle(el).fontSize);el.dataset.b860Px=px;});window.__b860Text.forEach(({el})=>el.style.setProperty('font-size',Number(el.dataset.b860Px)*2+'px','important'));});
   assert.deepEqual(await page.$$eval('[data-nextex]',rows=>rows.map(el=>el.textContent)),prescriptions);
   assert.ok(await page.$eval('.wpb-next-workout-view',el=>el.scrollWidth<=el.clientWidth+2),'200% preview stays within its scroller');
   assert.equal(await startReachable(),true,'200% text keeps Start reachable and clear of scrolling content');
   if(width===320)await page.screenshot({path:path.join(root,`verification/b860-preview-large-${theme}-${width}-phone.png`)});
   await page.evaluate(()=>window.__b860Text.forEach(({el,value,priority})=>value?el.style.setProperty('font-size',value,priority):el.style.removeProperty('font-size')));
   await page.click('.wpb-next-workout-view button[aria-label="Back"]');await page.waitForSelector('.wpb-plan-session-link');
   assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[program],'Preview and reflow preserve saved prescriptions');
   assert.deepEqual(errors,[]);
   console.log(`PASS B860 preview ${theme} ${width}x${height}: full accessible name, useful heading, shared prescription/count, theme feature, 200% reflow, Back and unchanged saved plan`);
  } finally {await context.close();}
 }
} finally {await browser?.close();await new Promise(resolve=>server.close(resolve));}
