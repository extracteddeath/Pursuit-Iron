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
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

let browser;
try {
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 for (const [width,height,theme] of [[320,740,'amethyst'],[393,850,'amethyst'],[393,850,'rosewater'],[900,1000,'rosewater'],[1280,850,'amethyst']]) {
  const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument((store,live,theme)=>{
   localStorage.setItem('wpb:v1',JSON.stringify({...store,theme}));
   localStorage.setItem('wpb:live',JSON.stringify({...live,restPaused:true,restRemain:60,restMax:90}));
  },store,live,theme);
  await page.goto(url,{waitUntil:'networkidle0'});
  await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
  await page.waitForSelector('.wpb-workout');
  const nav=await page.$$eval('.wpb-ex-nav-step',els=>els.map(el=>({name:el.querySelector('.wpb-ex-nav-name')?.textContent,count:el.querySelector('.wpb-ex-nav-count')?.textContent,label:el.getAttribute('aria-label')})));
  assert.equal(nav[0].count,'1/3');assert.equal(nav[1].count,'0/2');assert.ok(nav.every(x=>x.name&&x.label.includes(x.name)));
  const navigate=async i=>page.evaluate(i=>document.querySelectorAll('.wpb-ex-nav-step')[i].click(),i);
  await navigate(1);await page.waitForSelector('[data-testid="set-1-0"]');
  const visible=await page.$eval('.wpb-ex-nav',rail=>{const r=rail.getBoundingClientRect(),a=rail.querySelector('[aria-current="step"]').getBoundingClientRect();return !r.width||a.left>=r.left-1&&a.right<=r.right+1;});
  assert.ok(visible,'selected chip is revealed without scrolling the workout');
  await navigate(0);await page.waitForSelector('[data-testid="set-0-1"]');
  assert.equal(await page.$eval('[data-testid="set-0-1"] input[aria-label="weight"]',el=>el.value),'175','manual load survives navigation');
  assert.equal(await page.$eval('[data-testid="set-0-1"] input[aria-label="reps"]',el=>el.value),'7','manual reps survive navigation');
  assert.ok(await page.$('.wpb-workout-plate-strip'),'plate calculator retained');
  assert.ok(await page.$('.wpb-target-toggle'),'Target/Last retained');
  assert.ok(await page.$('.wpb-set-index'),'per-set options retained');
  const hierarchy=await page.$eval('.wpb-progression-label',button=>{
   const name=document.querySelector('.wpb-exercise-name'),b=getComputedStyle(button),n=getComputedStyle(name);
   return {height:button.getBoundingClientRect().height,font:parseFloat(b.fontSize),nameFont:parseFloat(n.fontSize),background:b.backgroundColor};
  });
  assert.ok(hierarchy.height<=32,'progression control stays compact at '+width);
  assert.ok(hierarchy.font<hierarchy.nameFont,'exercise name leads progression');
  assert.equal(hierarchy.background,'rgba(0, 0, 0, 0)','progression is a quiet text action');
  await page.locator('.wpb-progression-label').click();
  await page.waitForSelector('[aria-label="Exercise progression"]');
  await page.locator('[aria-label="Close Exercise progression"]').click();
  assert.equal(await page.$eval('[data-testid="set-0-1"] input[aria-label="weight"]',el=>el.value),'175','opening progression preserves entered load');
  if(process.env.B896_SCREENSHOTS) {
   await page.$eval('.wpb-workout-scroll',el=>el.scrollTop=0);
   await page.screenshot({path:`${process.env.B896_SCREENSHOTS}/b898-heading-${width}-${theme}.png`});
  }

  // Finish the final accessory set through the real log action: chip counts are live.
  await navigate(1);await page.waitForSelector('[data-testid="set-1-0"]');
  await page.evaluate(()=>document.querySelector('[data-testid="set-1-0"] [aria-label="Mark set done"]').click());
  await page.waitForFunction(()=>document.querySelector('.wpb-ex-nav-step:nth-child(2) .wpb-ex-nav-count').textContent==='1/2');
  await page.evaluate(()=>document.querySelector('[data-testid="set-1-1"] [aria-label="Mark set done"]').click());
  await page.waitForFunction(()=>document.querySelector('.wpb-ex-nav-step:nth-child(2)').dataset.complete==='1');
  await navigate(0);await page.waitForSelector('[data-testid="set-0-1"]');
  if(width>=600) await page.click('[aria-label="Focused mode"]');
  const rest=await page.$eval('[data-rest-card]',el=>({label:el.getAttribute('aria-label'),ring:el.querySelector('.wpb-rest-ring circle[pathLength]')?.getAttribute('stroke-dashoffset'),value:el.querySelector('[role="progressbar"]').getAttribute('aria-valuenow')}));
  const fits=await page.$eval('[data-rest-card]',el=>{const time=el.querySelector('.mono'),actions=el.querySelector('.wpb-rest-actions'),t=time.getBoundingClientRect(),a=actions.getBoundingClientRect();return time.scrollWidth<=time.clientWidth+1&&(t.bottom<=a.top+1||t.right<=a.left+1)&&el.scrollHeight<=el.clientHeight+1;});
  assert.ok(fits,'rest countdown and actions fit without overlap or clipping');
  assert.match(rest.label,/paused/);assert.equal(Number(rest.value),60);assert.ok(Math.abs(Number(rest.ring)-100/3)<.1,'ring and progressbar read the same remaining time');
  await page.waitForFunction(()=>{const el=document.querySelector('[data-rest-card] [aria-label="Add 15 seconds to rest"]'),r=el.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return el===hit||el.contains(hit);});
  await page.click('[data-rest-card] [aria-label="Add 15 seconds to rest"]');
  await page.waitForFunction(()=>document.querySelector('[data-rest-card] [role="progressbar"]').getAttribute('aria-valuenow')==='75');
  await page.click('[data-rest-card] [aria-label="Resume rest"]');await page.waitForSelector('[aria-label="Pause rest"]');
  await page.click('[data-rest-card] [aria-label="Pause rest"]');
  assert.ok(await page.$('[aria-label="Next exercise"]'),'Next remains available during rest');
  assert.ok(await page.$('[aria-label="Restart rest"]'));assert.ok(await page.$('[aria-label="Skip rest"]'));
  if(width>=600) await page.click('[aria-label="Exit focused mode"]');
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);assert.equal(overflow,false,'no page overflow '+width);
  if(process.env.B896_SCREENSHOTS) await page.screenshot({path:`${process.env.B896_SCREENSHOTS}/b896-${width}-${theme}.png`});
  assert.deepEqual(errors,[]);console.log(`PASS B896 ${width} ${theme}: named live progress, navigation ownership, preserved controls and shared rest state`);
  await context.close();
 }
} finally {await browser?.close();await new Promise(resolve=>server.close(resolve));}
