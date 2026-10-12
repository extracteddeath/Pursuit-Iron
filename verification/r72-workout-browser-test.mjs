import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// R72: same live workout and set controls at all window sizes.
// Only the optional, read-only lineup becomes a supporting pane on expanded
// tall windows; buttons call the original exercise navigation.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'r72-adaptive',name:'R72 Adaptive Workout',custom:true,weeks:6,
  config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2',rest:120},
    'upper:1':{sets:2,reps:'10-15',rir:'2',rest:90}}
};
const store=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,restAutoStart:false,
  warmupCard:false,unit:'lb',unitChosen:true,experience:'intermediate',
  bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()
});
const live={
  schemaVersion:2,programId:program.id,dayId:'upper',weekIndex:1,
  dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:120000,
  readiness:{label:'Normal',factor:1},restPaused:false,restRemain:0,
  restMax:0,runPaused:true,
  data:[
    {id:'bb-bench',slot:0,sets:Array.from({length:3},
      ()=>({weight:'185',reps:'6',done:false,auto:false,valueOwner:'user',
        target:{w:'185',reps:'6-8',rir:'2'}}))},
    {id:'inc-curl',slot:1,sets:Array.from({length:2},
      ()=>({weight:'30',reps:'10',done:false,auto:false,valueOwner:'user',
        target:{w:'30',reps:'10-15',rir:'2'}}))}
  ]
};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.png':'image/png','.svg':'image/svg+xml',
  '.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8799,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const results=[];
  for(const theme of ['rose','rosewater']){
    for(const [width,height] of [[393,852],[900,480],[900,1000],[1280,850]]){
      const context=await browser.createBrowserContext();
      const page=await context.newPage();const errors=[];
      page.on('pageerror',e=>errors.push(e.message));
      try{
        await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument((state,session)=>{
          localStorage.setItem('wpb:v1',JSON.stringify(state));
          localStorage.setItem('wpb:live',JSON.stringify(session));
        },store(theme),live);
        await page.goto('http://127.0.0.1:8799/',{waitUntil:'networkidle0'});
        await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.waitForSelector('[data-testid="set-0-0"] input[aria-label="weight"]');
        const layout=()=>page.evaluate(()=>{
          const scroll=document.querySelector('.wpb-workout-scroll');
          const side=scroll.querySelector(':scope > .wpb-workout-side');
          const rect=el=>{const r=el.getBoundingClientRect();
            return {x:r.x,right:r.right,y:r.y,bottom:r.bottom,width:r.width,height:r.height};};
          const row=document.querySelector('[data-testid^="set-"][data-done="0"]');
          const controls=row?.querySelector('.wpb-set-controls');
          const weight=row?.querySelector('input[aria-label="weight"]');
          const reps=row?.querySelector('input[aria-label="reps"]');
          const complete=row?.querySelector('.wpb-set-actions>button');
          return {grid:getComputedStyle(scroll).display==='grid',
            sideVisible:getComputedStyle(side).display!=='none',
            scroll:rect(scroll),side:rect(side),row:rect(row),
            controls:rect(controls),weight:rect(weight),reps:rect(reps),
            complete:rect(complete),sideItems:side.querySelectorAll('button').length,
            current:side.querySelectorAll('[aria-current="step"]').length,
            overflow:document.documentElement.scrollWidth>innerWidth+2};
        });
        let state=await layout();
        const expanded=width>=1120&&height>=720;
        assert.equal(state.grid,expanded,'Workout pane follows available window size');
        assert.equal(state.sideVisible,expanded,'Support lineup is hidden on compact windows');
        assert.equal(state.overflow,false,'Workout must not cause document overflow');
        assert.equal(state.sideItems,2,'Lineup reflects both real session exercises');
        assert.equal(state.current,1,'Lineup identifies exactly one active exercise');
        assert.ok(state.weight.width>=20 && state.reps.width>=20 && state.complete.width>=20 &&
          state.weight.right<=state.reps.x+2 && state.reps.right<=state.complete.x+2,
          'Original input and completion controls remain distinct: '+JSON.stringify({theme,width,height,state}));
        if(expanded){
          assert.ok(state.scroll.width>780,'Secondary workout route grows past phone width');
          assert.ok(state.side.x>=state.row.right-2,
            'Lineup is beside the original set editor, not on top of it');
          await page.click('.wpb-workout-side-item:nth-child(2)');
          await page.waitForSelector('[data-testid="set-1-0"]');
          assert.ok(await page.$eval('.wpb-workout-side-item:nth-child(2)',
            n=>n.getAttribute('aria-current')==='step'),
            'Lineup uses original goEx navigation and current selection');
          await page.setViewport({width:900,height:1000,deviceScaleFactor:1});
          state=await layout();
          assert.equal(state.sideVisible,false,'Medium layout hides the support pane');
          assert.ok(await page.$('[data-testid="set-1-0"]'),
            'Resizing does not lose the selected exercise');
          await page.setViewport({width,height,deviceScaleFactor:1});
          state=await layout();
          assert.equal(state.sideVisible,true,'Re-expansion restores the same lineup');
          await page.click('button[aria-label="Focused mode"]');
          await page.waitForFunction(()=>!document.querySelector('.wpb-workout-side'));
          assert.ok(await page.$('.wpb-focus-hero .wpb-focus-value-input'),
            'Focused mode retains its dedicated editable set fields');
          assert.ok(await page.$eval('.wpb-exercise-heading',n=>n.textContent.toLowerCase().includes('curl')),
            'Focused mode retains the selected second exercise');
          await page.click('button[aria-label="Exit focused mode"]');
          await page.waitForSelector('.wpb-workout-side');
          assert.ok(await page.$('[data-testid="set-1-0"]'),
            'Returning from focus restores the original second-exercise set rows');
        }
        assert.equal((await page.$eval('[data-testid^="set-"] input[aria-label="weight"]',
          input=>input.value)).length>0,true,'Editable weight remains present');
        assert.equal((await layout()).overflow,false,'No overflow after interaction and resize');
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),
          [program],'Adaptive presentation never mutates the saved program');
        assert.deepEqual(errors,[],'Workout has no page exceptions: '+errors.join('; '));
        results.push({theme,width,height,expanded});
      }finally{await context.close();}
    }
  }
  console.log('PASS R72 responsive live workout: '+JSON.stringify(results));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
