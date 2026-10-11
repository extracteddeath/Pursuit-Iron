import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// R54: screenshot and semantic-state contract for routes R53's paired primary
// destination test does not exercise. No writes to workout production storage.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'r54-manual',name:'R54 Manual',custom:true,weeks:4,
  config:{name:'R54 Manual',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:4,reps:'6-8',rir:'2'},'upper:1':{sets:3,reps:'10-15',rir:'2'}}
};
const store=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',
  unitChosen:true,experience:'intermediate',bodyweight:185,
  installDismissedAt:Date.now(),lastBackup:Date.now()
});
const snapshot={
  schemaVersion:2,programId:program.id,dayId:'upper',weekIndex:1,
  dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:120000,
  readiness:{label:'Normal',factor:1},restPaused:true,restRemain:47,
  restMax:90,runPaused:true,
  data:['bb-bench','inc-curl'].map((id,slot)=>({id,slot,sets:[
    {weight:slot?'30':'190',reps:slot?'10':'5',done:false,auto:false,
     valueOwner:'user',target:{w:slot?'30':'190',reps:slot?'10-15':'6-8',rir:'2'}}
  ]}))
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
await new Promise(resolve=>server.listen(8796,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const diagnostics={};
  for(const theme of ['rose','rosewater']){
    const context=await browser.createBrowserContext();
    const page=await context.newPage();
    const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument(value=>{
      localStorage.setItem('wpb:v1',JSON.stringify(value));
    },store(theme));
    await page.goto('http://127.0.0.1:8796/',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-tabbar');
    const audit=async(selector)=>{
      await page.waitForSelector(selector);
      return page.evaluate(sel=>{
        const node=document.querySelector(sel);
        const root=document.querySelector('.wpb');
        const css=getComputedStyle(node);
        return {background:css.backgroundColor,image:css.backgroundImage,
          overflow:root.scrollWidth>root.clientWidth+1,
          width:node.getBoundingClientRect().width};
      },selector);
    };
    // Library and exercise detail have historically been separate palettes.
    await page.click('[data-tab="settings"]');
    await page.waitForSelector('.wpb-settings');
    await page.$$eval('button',buttons=>{
      const b=buttons.find(n=>n.textContent.includes('Exercise library'));
      if(!b)throw new Error('Exercise library navigation missing');
      b.click();
    });
    const library=await audit('.wpb-library');
    assert.ok(!library.overflow&&library.width>300,'Library fits phone: '+JSON.stringify(library));
    await page.waitForSelector('.wpb-library-row');
    await page.screenshot({path:path.join(root,'verification/r54-library-'+theme+'-phone.png')});
    await page.click('.wpb-library-row');
    const detail=await audit('.wpb-exercise-detail');
    assert.ok(!detail.overflow&&detail.width>300,
      'Exercise detail fits and retains semantic overlay: '+JSON.stringify(detail));
    await page.screenshot({path:path.join(root,'verification/r54-detail-'+theme+'-phone.png')});

    // The manual progression program uses the week picker too. Earlier R53
    // ignored its active week and left all four buttons visually and semantically off.
    await page.evaluate(()=>localStorage.removeItem('wpb:live'));
    await page.reload({waitUntil:'networkidle0'});
    await page.waitForSelector('.hp-open');
    await page.click('.hp-open');
    await page.waitForSelector('.wpb-program .wpb-week-rail');
    const weeks=await page.evaluate(()=>{
      const buttons=[...document.querySelectorAll('.wpb-program .wpb-week-rail>button')];
      return buttons.map(b=>({name:b.textContent.trim().split('\n')[0],
        pressed:b.getAttribute('aria-pressed'),
        opacity:Number(getComputedStyle(b).opacity),
        height:b.getBoundingClientRect().height}));
    });
    assert.equal(weeks.filter(x=>x.pressed==='true').length,1,
      'Manual program has exactly one selected week: '+JSON.stringify(weeks));
    assert.ok(weeks.filter(x=>x.pressed==='true').every(x=>x.opacity>=.9&&x.height>=44),
      'Selected manual week stays visually distinct and tappable: '+JSON.stringify(weeks));
    await page.click('.wpb-program .wpb-week-rail>button:nth-child(2)');
    const after=await page.$$eval('.wpb-program .wpb-week-rail>button',
      buttons=>buttons.map(b=>b.getAttribute('aria-pressed')));
    assert.deepEqual(after.slice(0,2),['false','true'],
      'Changing manual week updates the real pressed button');
    await page.screenshot({path:path.join(root,'verification/r54-manual-week-'+theme+'-phone.png')});

    // Re-enter using a real persisted in-progress session. The logger remains
    // geometry-protected by B825, R49 and the independent workout shard.
    await page.evaluate(live=>localStorage.setItem('wpb:live',JSON.stringify(live)),snapshot);
    await page.reload({waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
    const workout=await audit('.wpb-workout');
    const inputs=await page.evaluate(()=>[...document.querySelectorAll(
      '[data-testid="set-0-0"] input[aria-label="weight"],[data-testid="set-0-0"] input[aria-label="reps"]')]
      .map(n=>({width:n.getBoundingClientRect().width,
        foreground:getComputedStyle(n).color,background:getComputedStyle(n).backgroundColor})));
    assert.ok(!workout.overflow&&inputs.length===2&&inputs.every(n=>n.width>=20),
      'The resumed logger remains readable without horizontal overflow: '+
      JSON.stringify({workout,inputs}));
    await page.screenshot({path:path.join(root,'verification/r54-workout-'+theme+'-phone.png')});
    assert.equal(errors.length,0,'No route errors in '+theme+': '+errors.join('; '));
    diagnostics[theme]={library,detail,weeks,workout,inputs};
    await context.close();
  }
  console.log('PASS R54: Rose dark/Rosewater light Library, Exercise Detail, manual Program week picker and resumed Workout at 393px; '+JSON.stringify(diagnostics));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
