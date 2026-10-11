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
// R59: exercise complete journeys using the production router and Android Back bridge.
// The active program must survive browsing, and a launch must retain its caller and selected week.
async function assertResumeClear(page){
  const selector='.wpb-live-dock button[aria-label="Resume workout"]';
  await page.waitForSelector(selector,{visible:true});
  const target=await page.$eval(selector,button=>{
    const box=button.getBoundingClientRect();
    const top=document.elementFromPoint(box.left+box.width/2,box.top+box.height/2);
    const toast=document.querySelector('.wpb-toast');
    return {width:box.width,height:box.height,clear:button.contains(top),
      covering:top?.closest('.wpb-toast')?.textContent||top?.getAttribute('aria-label'),
      toastBottom:toast?getComputedStyle(toast).bottom:null,
      toastTarget:toast?.style.bottom};
  });
  assert.ok(target.width>=44&&target.height>=44,'Resume retains its real 44px pointer target');
  assert.ok(target.clear,'Resume is immediately tappable when its dock appears: '+JSON.stringify(target));
}
async function verifyNavigationFlow(browser,theme,width){
  const alternate={...program,id:'r59-alternate',name:'R59 Alternate',
    config:{...program.config,name:'R59 Alternate'}};
  const initial={...store(theme),saved:[program,alternate]};
  const context=await browser.createBrowserContext();
  const page=await context.newPage();
  const errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  try{
    await page.setViewport({width,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument(value=>localStorage.setItem('wpb:v1',JSON.stringify(value)),initial);
    await page.goto('http://127.0.0.1:8796/',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-tabbar');
    const route=async name=>page.waitForSelector('[data-view-frame="'+name+'"]');
    const back=async()=>page.$eval('[data-view-frame] button[aria-label="Back"]',button=>button.click());
    const clickText=async text=>page.$$eval('button',(buttons,label)=>{
      const button=buttons.find(node=>node.textContent.trim()===label);
      if(!button)throw new Error('Missing flow action: '+label);
      button.click();
    },text);
    const settle=async()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    const selectedWeek=()=>page.$eval('.wpb-plan-week-chip[aria-pressed="true"]',button=>button.getAttribute('aria-label'));
    const assertPlan=async week=>{
      await route('plan');
      assert.equal(await page.$eval('.wpb-plan-title',node=>node.textContent),program.name,
        'Plan follows the active program rather than the last browsed program');
      assert.equal(await selectedWeek(),'Week '+week,'Plan retains its selected browsing week');
    };

    // On a fresh load the program editor has never been opened. Preview must initialize it itself.
    await page.click('[data-tab="plan"]');
    await page.click('[data-plan-week-picker] [aria-label="Week 3"]');
    await settle();
    const beforeScroll=await page.$eval('.wpb-plan-view',node=>node.scrollTop);
    assert.ok(beforeScroll>50,'The selected week has a real scroll position to restore');
    await page.click('[data-plan-week="3"] .wpb-plan-session-link');
    await route('nextWorkout');
    await page.$$eval('button',buttons=>buttons.find(node=>node.textContent.includes('View full program')).click());
    await page.waitForSelector('.wpb-program');
    assert.ok(await page.$eval('.wpb-program',node=>node.textContent.includes('R54 Manual')),
      'Preview opens the correct full program without bouncing to Home');
    assert.equal(await page.$eval('.wpb-program .wpb-week-rail>button[aria-pressed="true"]',node=>node.textContent.trim().startsWith('Week 3')),true,
      'Full program opens on the previewed week');
    await back();await route('nextWorkout');
    assert.ok(await page.$eval('.wpb-next-workout-view',node=>node.textContent.includes('Week 3 of 4')),
      'Full program Back returns to the same day and week preview');
    await back();await assertPlan(3);await settle();
    const returnedScroll=await page.$eval('.wpb-plan-view',node=>node.scrollTop);
    assert.ok(Math.abs(returnedScroll-beforeScroll)<=2,'Back restores the selected week scroll position: '+JSON.stringify({beforeScroll,returnedScroll}));

    // Browsing a second program is not a request to change the active training program.
    await page.click('[data-tab="home"]');
    await page.click('button[aria-label="Open R59 Alternate"]');await page.waitForSelector('.wpb-program');
    await back();await route('home');await page.click('[data-tab="plan"]');await assertPlan(3);
    await page.click('[data-plan-pane="volume"]');
    await page.click('[data-tab="profile"]');await page.click('[data-tab="plan"]');
    assert.equal(await page.$eval('[data-plan-pane="volume"]',node=>node.getAttribute('aria-pressed')),'true',
      'Switching tabs retains the selected Plan section');

    // Each actual program switch gets its own browsing context, never another program's week.
    await page.click('[data-tab="home"]');await page.click('button[aria-label="Switch to R59 Alternate"]');
    await page.click('[data-tab="plan"]');await route('plan');
    assert.equal(await page.$eval('.wpb-plan-title',node=>node.textContent),'R59 Alternate');
    assert.equal(await selectedWeek(),'Week 1','A newly active program starts at its own current week');
    await page.click('[data-plan-week-picker] [aria-label="Week 2"]');
    await page.click('[data-tab="home"]');await page.click('button[aria-label="Switch to R54 Manual"]');
    await page.click('[data-tab="plan"]');
    assert.equal(await page.$eval('[data-plan-pane="volume"]',node=>node.getAttribute('aria-pressed')),'true');
    await page.click('[data-plan-pane="schedule"]');await assertPlan(3);

    await page.click('[data-plan-week="3"] .wpb-plan-session-link');await route('nextWorkout');
    await clickText('Start workout');await page.waitForSelector('.wpb-workout');
    const checkIn=await page.$('.wpb-backdrop button[data-wpb-system-back]');
    if(checkIn){await checkIn.click();await page.waitForSelector('.wpb-backdrop',{hidden:true});}
    const weight=await page.$('[data-testid="set-0-0"] input[aria-label="weight"]');
    await weight.click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');
    await page.keyboard.type('185');await page.keyboard.press('Tab');
    await page.evaluate(()=>history.back());await page.waitForSelector('.wpb-workout',{hidden:true});await assertPlan(3);
    await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
    const minimized=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
    assert.equal(minimized.weekIndex,3,'Minimizing does not replace the selected workout week');
    assert.equal(minimized.data[0].sets[0].weight,'185','Minimizing retains typed set values');

    // Resuming from a different tab should return to that tab, keeping the same live workout.
    await page.click('[data-tab="progress"]');
    await assertResumeClear(page);
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');await page.waitForSelector('.wpb-workout');
    await page.evaluate(()=>history.back());await page.waitForSelector('.wpb-workout',{hidden:true});await route('progress');
    const resumed=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
    assert.equal(resumed.sessionId,minimized.sessionId,'Resume and minimize retain the same workout identity');
    assert.deepEqual(resumed.data,minimized.data,'Resume and minimize retain every entered set');
    const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
    assert.deepEqual(saved.saved,initial.saved,'Navigation never edits saved program content');
    assert.deepEqual(saved.history,[],'Navigation never logs an unfinished workout');
    assert.equal(saved.pinnedId,program.id,'Only an explicit Switch changes the active program');
    // An active Undo notification must never cover the newly returned dock, even for one frame.
    for(let retry=0;retry<3;retry++){
      await assertResumeClear(page);
      await page.click('.wpb-live-dock button[aria-label="Resume workout"]');await page.waitForSelector('.wpb-workout');
      await page.evaluate(()=>history.back());await page.waitForSelector('.wpb-workout',{hidden:true});await route('progress');
      const recovered=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
      assert.equal(recovered.sessionId,minimized.sessionId,'Repeated resume retains the same workout identity');
      assert.deepEqual(recovered.data,minimized.data,'Repeated resume retains every entered set');
    }
    await assertResumeClear(page);
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');await page.waitForSelector('.wpb-workout');
    const reps=await page.$('[data-testid="set-0-0"] input[aria-label="reps"]');
    await reps.click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');
    await page.keyboard.type('6');await page.keyboard.press('Tab');
    await page.click('[data-testid="set-0-0"] .wpb-set-complete');
    await page.click('button[aria-label="Finish workout"]');await clickText('Save & finish');
    await page.waitForSelector('.wpb-workout',{hidden:true});await route('progress');
    const completed=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
    assert.equal(completed.history.length,1,'Finishing logs the workout exactly once');
    assert.equal(completed.history[0].id,minimized.sessionId,'The completed record retains the resumed session identity');
    assert.equal(completed.history[0].weekIndex,3,'The completed record retains the selected workout week');
    assert.ok(completed.history[0].perf['bb-bench'].sets.some(set=>set.w===185&&set.r===6),
      'Finishing retains the real entered weight and reps');
    assert.deepEqual(completed.saved,initial.saved,'Finishing does not edit either saved program');
    assert.equal(await page.evaluate(()=>localStorage.getItem('wpb:live')),null,'Successful finish clears live recovery state');
    await page.click('[data-tab="plan"]');await assertPlan(3);
    await page.$eval('.wpb-plan-open-program',button=>button.click());await page.waitForSelector('.wpb-program');
    assert.equal(await page.$eval('.wpb-program .wpb-week-rail>button[aria-pressed="true"]',node=>node.textContent.trim().startsWith('Week 3')),true,
      'Plan full-program action retains the selected week');
    await page.$eval('.wpb-program button[title="Start workout"]',button=>button.click());await page.waitForSelector('.wpb-workout');
    const secondCheckIn=await page.$('.wpb-backdrop button[data-wpb-system-back]');
    if(secondCheckIn){await secondCheckIn.click();await page.waitForSelector('.wpb-backdrop',{hidden:true});}
    await page.evaluate(()=>history.back());await page.waitForSelector('.wpb-workout',{hidden:true});await page.waitForSelector('.wpb-program');
    await back();await assertPlan(3);
    assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history.length),1,
      'Minimizing from full Program preserves its Back path and does not add another completed workout');
    assert.equal(errors.length,0,'No flow errors in '+theme+'/'+width+': '+errors.join('; '));
    return {theme,width,selectedWeek:3,beforeScroll,returnedScroll,sessionId:resumed.sessionId};
  }finally{await context.close();}
}
async function verifyNextSessionFlow(browser,theme,width){
  const context=await browser.createBrowserContext();
  const page=await context.newPage();
  const errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  try{
    await page.setViewport({width,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument(value=>localStorage.setItem('wpb:v1',JSON.stringify(value)),store(theme));
    await page.goto('http://127.0.0.1:8796/',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-tabbar');await page.click('[data-tab="plan"]');
    await page.$eval('.wpb-plan-view',node=>{node.scrollTop=180;});
    await page.click('[data-plan-week="1"] .wpb-plan-session-link');
    await page.$$eval('button',buttons=>buttons.find(button=>button.textContent.trim()==='Start workout').click());
    await page.waitForSelector('.wpb-workout');
    const checkIn=await page.$('.wpb-backdrop button[data-wpb-system-back]');
    if(checkIn){await checkIn.click();await page.waitForSelector('.wpb-backdrop',{hidden:true});}
    for(const [label,value] of [['weight','185'],['reps','6']]){
      const input=await page.$('[data-testid="set-0-0"] input[aria-label="'+label+'"]');
      await input.click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');
      await page.keyboard.type(value);await page.keyboard.press('Tab');
    }
    await page.click('[data-testid="set-0-0"] .wpb-set-complete');
    await page.click('button[aria-label="Finish workout"]');
    await page.$$eval('button',buttons=>buttons.find(button=>button.textContent.trim()==='Save & finish').click());
    await page.waitForSelector('[data-view-frame="plan"]');
    await page.waitForFunction(()=>document.querySelector('.wpb-plan-week-chip[aria-pressed="true"]')?.getAttribute('aria-label')==='Week 2');
    const next=await page.evaluate(()=>({
      selected:document.querySelector('.wpb-plan-week-chip[aria-pressed="true"]').getAttribute('aria-label'),
      action:document.querySelector('.wpb-plan-up-next').textContent,
      scroll:document.querySelector('.wpb-plan-view').scrollTop,
      records:JSON.parse(localStorage.getItem('wpb:v1')).history.length
    }));
    assert.equal(next.selected,'Week 2','Completing the current week follows the new due week');
    assert.ok(next.action.includes('Week 2'),'The visible next action agrees with the completed history');
    assert.equal(next.scroll,0,'A newly due week returns to the next-workout action at the top');
    assert.equal(next.records,1,'Revealing the next session does not duplicate the completed workout');
    assert.deepEqual(errors,[],'No errors while advancing the next-session flow');
    return {theme,width,...next};
  }finally{await context.close();}
}
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
  const flows=[];
  const nextSessions=[];
  for(const theme of ['rose','rosewater'])
    for(const width of [320,393]){
      flows.push(await verifyNavigationFlow(browser,theme,width));
      nextSessions.push(await verifyNextSessionFlow(browser,theme,width));
    }
  console.log('PASS R54/R59: paired Library/Detail/Program/Workout layouts; active Plan, preview/full-program Back, remembered week/section/scroll, per-program context, safe workout returns and next due week at 320/393px; '+JSON.stringify({diagnostics,flows,nextSessions}));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
