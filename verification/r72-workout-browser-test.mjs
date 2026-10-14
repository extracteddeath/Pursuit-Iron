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
    {id:'bb-bench',slot:0,note:'',sets:Array.from({length:3},
      ()=>({weight:'185',reps:'6',done:false,auto:false,valueOwner:'user',
        target:{w:'185',reps:'6-8',rir:'2'}}))},
    {id:'inc-curl',slot:1,note:'',sets:Array.from({length:2},
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
  for(const theme of ['rose','rosewater','violet']){
    for(const [width,height] of [[320,740],[393,852],[600,800],[900,480],[900,1000],[1280,850],[1600,900]]
      .filter(([w,h])=>!process.argv[2]||process.argv[2]===w+'x'+h)){
      const context=await browser.createBrowserContext();
      const page=await context.newPage();const errors=[];
      page.on('pageerror',e=>errors.push(e.message));
      page.on('console',msg=>{if(msg.type()==='error')console.log('R75 browser error: '+msg.text());});
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
        const clickSettled = async selector => {
          // Resizing can move the toolbar between pointer coordinates and
          // delivery. Require a stable, unobscured target for the native click.
          await page.waitForFunction(async selector => {
            const button = document.querySelector(selector); if (!button) return false;
            const before = button.getBoundingClientRect();
            await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
            const after = button.getBoundingClientRect();
            const hit = document.elementFromPoint(after.left + after.width / 2, after.top + after.height / 2);
            return after.width > 0 && after.height > 0 && Math.abs(after.left - before.left) < .1
              && Math.abs(after.top - before.top) < .1 && (hit === button || button.contains(hit));
          }, {}, selector);
          await page.click(selector);
        };
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

          // R73: the selected companion item must be distinguishable by real
          // themed fill/stroke, while reducing motion removes only animation.
          const tones=await page.evaluate(()=>{
            const items=document.querySelectorAll('.wpb-workout-side-item');
            const selected=getComputedStyle(items[0]),idle=getComputedStyle(items[1]);
            return {selectedFill:selected.backgroundColor,idleFill:idle.backgroundColor,
              selectedStroke:selected.borderColor,idleStroke:idle.borderColor,
              reducedTransition:selected.transitionDuration};
          });
          assert.notEqual(tones.selectedFill,tones.idleFill,
            'Selected companion has a distinct theme-native fill');
          assert.notEqual(tones.selectedStroke,tones.idleStroke,
            'Selected companion has a distinct theme-native border');
          assert.ok(tones.reducedTransition.split(',').every(v=>parseFloat(v)===0),
            'Reduced motion suppresses selection animation');
          await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'no-preference'}]);
          const motion=await page.$eval('.wpb-workout-side-item',
            node=>getComputedStyle(node).transitionDuration.split(',')
              .map(v=>parseFloat(v)));
          assert.ok(motion.some(v=>v>0&&v<=.3),
            'Standard motion adds a bounded color-only transition');
          await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
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
          await clickSettled('button[aria-label="Focused mode"]');
          await page.waitForFunction(()=>!document.querySelector('.wpb-workout-side'));
          assert.ok(await page.$('.wpb-focus-hero .wpb-focus-value-input'),
            'Focused mode retains its dedicated editable set fields');
          assert.ok(await page.$eval('.wpb-exercise-heading',n=>n.textContent.toLowerCase().includes('curl')),
            'Focused mode retains the selected second exercise');
          await clickSettled('button[aria-label="Exit focused mode"]');
          await page.waitForSelector('.wpb-workout-side');
          assert.ok(await page.$('[data-testid="set-1-0"]'),
            'Returning from focus restores the original second-exercise set rows');
        }
        assert.equal((await page.$eval('[data-testid^="set-"] input[aria-label="weight"]',
          input=>input.value)).length>0,true,'Editable weight remains present');
        assert.equal((await layout()).overflow,false,'No overflow after interaction and resize');
        // R75: exercise the original logger through a complete real session
        // journey at every supported size. A pane screenshot alone cannot
        // certify typed values, effort, undo or durable completion.
        if(expanded)await page.click('.wpb-workout-side-item:first-child');
        await page.waitForSelector('[data-testid="set-0-0"] input[aria-label="weight"]');
        const fill=async(selector,value)=>{
          await page.click(selector);
          await page.$eval(selector,input=>input.select());
          await page.keyboard.type(value);
          await page.keyboard.press('Tab');
        };
        const clickEffort=async(value)=>{
          const selector='[data-testid="set-0-0"] button[aria-label="'+value+' reps left"]';
          await page.waitForFunction(sel=>{
            const el=document.querySelector(sel);if(!el||el.closest('[inert],[aria-hidden="true"]'))return false;
            const r=el.getBoundingClientRect();
            return r.height>0&&el.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));
          },{},selector);
          await page.click(selector);
        };
        await fill('[data-testid="set-0-0"] input[aria-label="weight"]','205');
        await fill('[data-testid="set-0-0"] input[aria-label="reps"]','8');
        await page.click('[data-testid="set-0-0"] button[aria-label="Mark set done"]');
        await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
        await clickEffort(3);
        await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.data[0].sets[0].actualRIR===3);
        await page.click('[data-testid="set-0-0"] button[aria-label="Mark set not done"]');
        assert.deepEqual(await page.$$eval('[data-testid="set-0-0"] input',els=>els.map(el=>el.value)),
          ['205','8'],'Undo retains both authored values');
        await page.click('[data-testid="set-0-0"] button[aria-label="Mark set done"]');
        await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
        await clickEffort(2);
        const plateButton=(await page.$$('.wpb-workout-tool'));
        let platesOpened=false;
        for(const button of plateButton){
          if(await button.evaluate(el=>el.textContent.trim()==='Plates')){
            await button.click();platesOpened=true;break;
          }
        }
        assert.equal(platesOpened,true,'Plate calculator remains reachable');
        await page.waitForSelector('.wpb-backdrop [data-sheet-drag]');
        assert.ok(await page.$eval('.wpb-backdrop',el=>el.textContent.includes('Calculators')&&
          el.textContent.includes('Barbell')&&!!el.querySelector('input')),
          'Plates opens the actual calculation sheet');
        await page.keyboard.press('Escape');
        await page.waitForSelector('.wpb-backdrop',{hidden:true});
        await page.click('.wpb-workout-footer button[aria-label="Next exercise"]');
        await page.waitForSelector('[data-testid="set-1-0"]');
        await fill('[data-testid="set-1-0"] input[aria-label="weight"]','20');
        await fill('[data-testid="set-1-0"] input[aria-label="reps"]','12');
        await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.data[1].sets[0].weight==='20');
        const beforeResume=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
        await page.evaluate(()=>history.back());
        await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.waitForSelector('[data-testid="set-1-0"]');
        const afterResume=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
        assert.equal(afterResume.sessionId,beforeResume.sessionId,'Resume retains the same session identity');
        assert.deepEqual(afterResume.data,beforeResume.data,'Resume retains every typed/logged set and effort');
        await page.click('button[aria-label="Finish workout"]');
        const save=await page.evaluateHandle(()=>[...document.querySelectorAll('.wpb-backdrop button')]
          .find(el=>el.textContent.trim()==='Save & finish'));
        assert.ok(save.asElement(),'Partial-session review offers Save & finish');
        await save.asElement().click();
        try{
          await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).history.length===1&&
            localStorage.getItem('wpb:live')===null);
        }catch(error){
          console.log('R75 completion diagnostic '+JSON.stringify(await page.evaluate(()=>({
            history:JSON.parse(localStorage.getItem('wpb:v1')).history,
            live:JSON.parse(localStorage.getItem('wpb:live')),
            dialog:[...document.querySelectorAll('.wpb-backdrop')].map(el=>el.textContent),
            workout:!!document.querySelector('.wpb-workout')}))));
          await page.screenshot({path:path.join(root,'verification/r75-completion-failure-phone.png')});
          throw error;
        }
        const record=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history[0]);
        assert.equal(record.id,beforeResume.sessionId,'Completion stores the resumed identity exactly once');
        assert.ok(record.perf['bb-bench'].sets.some(set=>set.w===205&&set.r===8&&set.rir===2&&set.rirReported),
          'History retains the entered weight, reps and reported effort');
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),
          [program],'Adaptive presentation never mutates the saved program');
        assert.deepEqual(errors,[],'Workout has no page exceptions: '+errors.join('; '));
        results.push({theme,width,height,expanded});
        console.log('PASS R75 workout journey '+theme+' '+width+'x'+height);
      }catch(error){
        console.log('R75 failed case '+JSON.stringify({theme,width,height,
          state:await page.evaluate(()=>({live:JSON.parse(localStorage.getItem('wpb:live')),
            effort:[...document.querySelectorAll('[data-effort-picker] button')].map(el=>{
              const r=el.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);
              return {label:el.getAttribute('aria-label'),y:r.y,bottom:r.bottom,height:r.height,
                hit:el.contains(hit),hitClass:hit?.className};
            })}))}));
        await page.screenshot({path:path.join(root,'verification/r75-workflow-failure-'+theme+'-'+width+'-phone.png')});
        throw error;
      }finally{await context.close();}
    }
  }
  assert.equal(results.length,process.argv[2]?3:21,'Every requested R75 scenario ran');
  console.log('PASS R72/R75 responsive live workout and durable logging: '+JSON.stringify(results));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
