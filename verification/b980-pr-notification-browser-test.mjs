import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

// B980: exercise actual WorkoutSession record notifications. The B951 pure gate
// does not prove that editing a field, re-rendering or resuming cannot celebrate.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const im=fs.readFileSync(path.join(root,'index.html'),'utf8')
  .match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const program={id:'b980-pr',name:'Record replay regression',custom:true,weeks:6,
  config:{unit:'lb',goal:'both',experience:'intermediate',progression:'manual',split:'custom',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench']}],
  overrides:{'upper:0':{sets:2,reps:'5-12',rir:'2',rest:90}}};
const previous={id:'b980-prior',programId:program.id,dayId:'upper',date:Date.now()-86400000,
  unit:'lb',perf:{'bb-bench':{weight:100,reps:8,
    sets:[{w:100,r:8,done:true,rir:2,rirReported:true}]}}};
const live={schemaVersion:2,programId:program.id,dayId:'upper',weekIndex:2,
  dayExSig:'bb-bench',exIdx:0,elapsedMs:60000,readiness:{label:'Normal',factor:1},
  restPaused:true,restRemain:0,restMax:0,runPaused:true,
  data:[{id:'bb-bench',slot:0,note:'',sets:[0,1].map(()=>({
    weight:'100',reps:'8',done:false,auto:false,valueOwner:'user',
    target:{w:'100',reps:'5-12',rir:'2'}}))}]};
const fixture=[
  "import React from '/vendor/react.js';",
  "import {createRoot} from '/vendor/react-dom-client.js';",
  "import {WorkoutSession,StyleTag} from '/modules/App.js';",
  'const program='+JSON.stringify(program)+';',
  'const prior='+JSON.stringify(previous)+';',
  "const history=new URL(location.href).searchParams.has('prior')?[prior]:[];",
  "createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day:program.days[0],weekIndex:2,unit:'lb',perf:history[0]?.perf||{},history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{},setUnit:()=>{}})));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">'+
  '<link rel="stylesheet" href="/app.css">'+im+'</head><body><div id="root"></div>'+
  '<script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{
  const pathname=new URL(req.url,'http://localhost').pathname;
  if(pathname==='/pr-test.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
  const file=path.resolve(root,'.'+pathname);
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const url='http://127.0.0.1:'+server.address().port+'/pr-test.html';
const toast='.wpb-b965-layout-22';
let browser;
try {
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const open=async(withPrior)=>{
    const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.setViewport({width:393,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument(snapshot=>{
      if(!localStorage.getItem('wpb:live'))localStorage.setItem('wpb:live',JSON.stringify(snapshot));
    },live);
    await page.goto(url+(withPrior?'?prior=1':''),{waitUntil:'networkidle0'});
    await page.waitForSelector('[data-testid="set-0-0"] input[aria-label="reps"]');
    return {context,page,errors};
  };
  const editReps=async(page,value)=>{
    const selector='[data-testid="set-0-0"] input[aria-label="reps"]';
    await page.click(selector);
    await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');
    await page.keyboard.type(String(value));
    assert.equal(await page.$eval(selector,el=>el.value),String(value));
  };
  const clickSet=(page,done)=>page.click('[data-testid="set-0-0"] button[aria-label="Mark set '+(done?'done':'not done')+'"]');
  const waitDone=(page,done)=>page.waitForFunction(done=>
    JSON.parse(localStorage.getItem('wpb:live')||'{}')?.data?.[0]?.sets?.[0]?.done===done,{},done);
  const isToast=page=>page.$eval('body',el=>!!el.querySelector('.wpb-b965-layout-22'));
  // A first exposure is not a PR. Merely typing a huge number cannot notify.
  {
    const {context,page,errors}=await open(false);
    assert.equal(await isToast(page),false,'initial mount cannot show a record');
    await editReps(page,10);
    assert.equal(await isToast(page),false,'typing, not logging, cannot announce a record');
    await clickSet(page,true);await waitDone(page,true);
    assert.equal(await isToast(page),false,'first-ever lift has no historical best to beat');
    assert.deepEqual(errors,[],'fresh workout has no browser exceptions');
    await context.close();
  }
  // Historical tie, actual new PR, undo/re-log, and exact session restoration.
  {
    const {context,page,errors}=await open(true);
    assert.equal(await isToast(page),false,'restoring a pending workout cannot show a PR');
    await clickSet(page,true);await waitDone(page,true);
    assert.equal(await isToast(page),false,'tying the real recorded best is not a PR');
    await clickSet(page,false);await waitDone(page,false);
    await editReps(page,10);
    assert.equal(await isToast(page),false,'increasing reps in the input is not an actual PR');
    await clickSet(page,true);await waitDone(page,true);
    await page.waitForSelector(toast);
    assert.match(await page.$eval(toast,el=>el.textContent),/New estimated 1RM best/,
      'completed working-set record shows one truthful celebration');
    await page.click(toast);await page.waitForFunction(sel=>!document.querySelector(sel),{},toast);
    await clickSet(page,false);await waitDone(page,false);
    await clickSet(page,true);await waitDone(page,true);
    assert.equal(await isToast(page),false,'undo/re-log cannot replay the same exercise PR');
    await page.reload({waitUntil:'networkidle0'});
    await page.waitForSelector('[data-testid="set-0-0"][data-done="1"]');
    assert.equal(await isToast(page),false,'resuming a completed PR cannot re-announce it');
    await clickSet(page,false);await waitDone(page,false);
    await clickSet(page,true);await waitDone(page,true);
    assert.equal(await isToast(page),false,'undo after resume cannot manufacture a repeat notification');
    assert.deepEqual(errors,[],'PR lifecycle has no browser exceptions');
    await context.close();
  }
  console.log('PASS B980 browser: no edit/tie/first-exposure/reload PR, genuine completed PR only, no undo replays.');
} finally {
  if(browser)await browser.close();
  await new Promise(done=>server.close(done));
}
