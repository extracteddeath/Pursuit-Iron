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
  warmupCard:false,exNotes:{'bb-bench':'J-hooks 9.5'},
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
      toastTarget:toast?.style.bottom,
      notice:toast?.getBoundingClientRect().toJSON(),noticeParent:toast?.offsetParent?.getBoundingClientRect().toJSON(),
      barriers:[...document.querySelectorAll('.wpb-live-dock,.wpb-tabbar,.wpb-workout-footer')].map(el=>({
        class:el.className,box:el.getBoundingClientRect().toJSON(),visibility:getComputedStyle(el).visibility,
        display:getComputedStyle(el).display,transform:getComputedStyle(el).transform}))};
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
    await page.$eval('.wpb-program button[title="Start workout"]',button=>button.click());
    await page.waitForSelector('[data-testid="session-length-control"]');
    assert.equal(await page.$eval('[aria-label="Session length"] button[aria-pressed="true"]',node=>node.textContent),'Full',
      'Program-day starts offer a one-session time choice, defaulting to the complete prescription');
    await clickText('Start workout');await page.waitForSelector('.wpb-workout');
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
// R60: browsing a different destination must retain filters, content and its own scroll position.
async function verifyBrowseMemory(browser,theme,width){
  const context=await browser.createBrowserContext();
  const page=await context.newPage();
  const errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const entries=Array.from({length:64},(_,i)=>({
    id:'r60-history-'+i,programId:program.id,programName:program.name,dayId:'upper',dayLabel:'Upper',
    weekIndex:1,date:Date.now()-(i+1)*86400000,unit:'lb',duration:1800,totalSets:5,tonnage:5400,
    perf:{'bb-bench':{weight:185,reps:6,sets:[{w:185,r:6,done:true,rir:2,rirReported:true}]},
      'inc-curl':{weight:30,reps:12,sets:[{w:30,r:12,done:true,rir:2,rirReported:true}]},
      'back-squat':{weight:245,reps:6,sets:[{w:245,r:6,done:true,rir:2,rirReported:true}]},
      'deadlift':{weight:315,reps:6,sets:[{w:315,r:6,done:true,rir:2,rirReported:true}]},
      'ohp':{weight:95,reps:6,sets:[{w:95,r:6,done:true,rir:2,rirReported:true}]}}
  }));
  try{
    await page.setViewport({width,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument(initial=>{
      if(localStorage.getItem('r60-seeded'))return;
      localStorage.setItem('r60-seeded','1');
      localStorage.setItem('wpb:v1',JSON.stringify(initial));
    },{...store(theme),history:entries});
    await page.goto('http://127.0.0.1:8796/',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-tabbar');
    const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    const clickText=async(label,selector='button')=>{
      const handle=await page.evaluateHandle((label,selector)=>[...document.querySelectorAll(selector)]
        .find(button=>button.textContent.trim()===label),label,selector);
      assert.ok(handle.asElement(),'The real '+label+' control exists');
      try{await handle.asElement().click();}finally{await handle.dispose();}
    };
    const training=()=>page.evaluate(()=>{
      const s=JSON.parse(localStorage.getItem('wpb:v1'));
      return {saved:s.saved,cycles:s.cycles,history:s.history,perf:s.perf,drafts:s.drafts};
    });
    const before=await training();
    const scroll=()=>page.$eval('[data-view-frame] .wpb-page-scroll',node=>node.scrollTop);
    const setScroll=async value=>{await page.$eval('[data-view-frame] .wpb-page-scroll',(node,value)=>{node.scrollTop=value;},value);await settle();return scroll();};
    const assertScroll=async(expected,label)=>{await settle();const actual=await scroll();
      assert.ok(Math.abs(actual-expected)<=2,label+' '+JSON.stringify({theme,width,expected,actual}));};

    await page.click('button[aria-label="Exercise library"]');await page.waitForSelector('.wpb-library');
    const libraryScroll=await setScroll(180);assert.ok(libraryScroll>50,'Library has a real browsing position');
    await clickText('Compare','.wpb-library-header button');await page.waitForSelector('[data-view-frame="compare"]');
    await page.evaluate(()=>history.back());await page.waitForSelector('.wpb-library');
    await assertScroll(libraryScroll,'Compare Back restores the Library position');
    await page.type('input[aria-label="Search exercises"]','press');
    await clickText('Chest','.wpb-filter-chip--primary');await clickText('Barbell','.wpb-filter-chip--secondary');
    await clickText('Recent','.wpb-library-flag');
    const libraryState=()=>page.evaluate(()=>({
      query:document.querySelector('.wpb-library-search input').value,
      muscle:document.querySelector('.wpb-filter-chip--primary[aria-pressed="true"]').textContent,
      equipment:document.querySelector('.wpb-filter-chip--secondary[aria-pressed="true"]').textContent,
      flag:document.querySelector('.wpb-library-flag[aria-pressed="true"]').textContent,
      results:document.querySelectorAll('.wpb-library-row').length
    }));
    const filtered=await libraryState();assert.ok(filtered.results>0,'The combined filters have real results');
    await clickText('Compare','.wpb-library-header button');await page.waitForSelector('[data-view-frame="compare"]');
    await page.evaluate(()=>history.back());await page.waitForSelector('.wpb-library');
    assert.deepEqual(await libraryState(),filtered,'Compare Back retains query, muscle, equipment, Recent and matching results');
    assert.equal(await page.$$eval('.wpb-library-header>.wpb-hscroll',groups=>groups.every(group=>{
      const box=group.querySelector('[aria-pressed="true"]').getBoundingClientRect(),bounds=group.getBoundingClientRect();
      return box.left>=bounds.left-1&&box.right<=bounds.right+1;
    })),true,'Restored selected muscle and equipment chips are fully visible');
    const clear=await page.$eval('button[aria-label="Clear exercise search"]',button=>{
      const box=button.getBoundingClientRect(),top=document.elementFromPoint(box.x+box.width/2,box.y+box.height/2);
      return {width:box.width,height:box.height,clear:button.contains(top)};
    });
    assert.ok(clear.width>=48&&clear.height>=48&&clear.clear,'Clear search is a real unobstructed 48px target: '+JSON.stringify(clear));
    if(process.env.R60_CAPTURE_DIR){fs.mkdirSync(process.env.R60_CAPTURE_DIR,{recursive:true});
      await page.screenshot({path:path.join(process.env.R60_CAPTURE_DIR,`library-${theme}-${width}.png`)});}
    await page.click('button[aria-label="Clear exercise search"]');
    assert.equal(await page.$eval('.wpb-library-search input',input=>input.value),'');
    assert.equal(await page.$eval('.wpb-library-search input',input=>document.activeElement===input),true,'Clear returns focus to the search field');
    const cleared=await libraryState();assert.equal(cleared.muscle,'Chest');assert.equal(cleared.equipment,'Barbell');assert.equal(cleared.flag,'Recent');
    await page.type('.wpb-library-search input','no matching exercise');await clickText('Clear filters');
    const reset=await libraryState();assert.equal(reset.query,'');assert.equal(reset.muscle,'All muscles');
    assert.equal(reset.equipment,'Any equipment');assert.equal(reset.flag,'All');
    await page.evaluate(()=>history.back());await page.waitForSelector('[data-view-frame="home"]');

    await page.click('[data-tab="progress"]');await page.waitForSelector('#history-search');
    await page.type('#history-search','Upper');await page.select('select[aria-label="Filter history by program"]',program.id);
    await page.select('select[aria-label="Filter history by time"]','90');await clickText('By month');
    await clickText('Show 14 more workouts');
    await page.$eval('[data-history-id="r60-history-0"] button[aria-expanded]',button=>button.scrollIntoView({block:'center'}));
    await settle();
    await page.click('[data-history-id="r60-history-0"] button[aria-expanded]');
    await page.waitForFunction(()=>document.querySelector('[data-history-id="r60-history-0"]')?.dataset.open==='1');
    const sessionsScroll=await setScroll(150);assert.ok(sessionsScroll>50);
    await page.click('[data-tab="profile"]');await page.click('[data-tab="progress"]');
    assert.equal(await page.$eval('#history-search',input=>input.value),'Upper','History search survives a destination change');
    assert.equal(await page.$eval('select[aria-label="Filter history by program"]',input=>input.value),program.id);
    assert.equal(await page.$eval('select[aria-label="Filter history by time"]',input=>input.value),'90');
    assert.equal(await page.$$eval('.wpb-history-session',rows=>rows.length),64,'The expanded history page is retained');
    assert.equal(await page.$eval('[data-history-id="r60-history-0"]',row=>row.dataset.open),'1','The expanded session is retained');
    assert.equal(await page.$$eval('.wpb-progress button',buttons=>buttons.find(b=>b.textContent==='By month').getAttribute('aria-pressed')),'true');
    await assertScroll(sessionsScroll,'Sessions returns to its own exact scroll position');
    await clickText('Strength','.wpb-premium-tabs button');
    await assertScroll(0,'The new Strength section starts at its own top');
    const strengthScroll=await setScroll(120);assert.ok(strengthScroll>50);
    await clickText('Lifts','.wpb-premium-tabs button');await clickText('Records','[data-lift-pane]');
    await assertScroll(0,'A newly selected Lifts pane starts at its own top');
    const recordsScroll=await setScroll(120);assert.ok(recordsScroll>=0); // Records can fit now that Strength has its own section.
    await clickText('Exercises','[data-lift-pane]');await assertScroll(0,'Exercises does not inherit the Records offset');
    await page.type('input[aria-label="Search your lifts"]','curl');
    await page.click('[data-tab="settings"]');await page.click('[data-tab="progress"]');
    assert.equal(await page.$eval('.wpb-premium-tabs [aria-selected="true"]',button=>button.textContent),'Lifts');
    assert.equal(await page.$eval('[data-lift-pane][aria-current="true"]',button=>button.dataset.liftPane),'exercises');
    assert.equal(await page.$eval('input[aria-label="Search your lifts"]',input=>input.value),'curl');
    assert.equal(await page.$$eval('.wpb-progress button.opt',buttons=>buttons.length),1,'The restored lift query retains its matching row');
    await clickText('Records','[data-lift-pane]');await assertScroll(recordsScroll,'Records returns to its own scroll position');
    await clickText('Strength','.wpb-premium-tabs button');await assertScroll(strengthScroll,'Strength restores its own exact scroll position');
    await clickText('Body','.wpb-premium-tabs button');await assertScroll(0,'Body does not inherit the Strength offset');
    await clickText('Sessions','.wpb-premium-tabs button');await assertScroll(sessionsScroll,'Switching sections restores Sessions exactly');
    await clickText('Lifts','.wpb-premium-tabs button');
    await page.click('[data-tab="home"]');await page.click('button[aria-label="Workout history"]');
    await page.waitForSelector('#history-search');await assertScroll(0,'The explicit Home history shortcut opens Sessions at the top');
    assert.equal(await page.$eval('.wpb-premium-tabs [aria-selected="true"]',button=>button.textContent),'Sessions');
    assert.deepEqual(await training(),before,'Browsing never changes saved programs, history, performance or drafts');
    assert.equal(await page.evaluate(()=>localStorage.getItem('wpb:live')),null,'Browsing never starts a workout');

    await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-tabbar');
    await page.click('[data-tab="progress"]');await page.waitForSelector('#history-search');
    assert.equal(await page.$eval('#history-search',input=>input.value),'','A new app visit starts with fresh browsing choices');
    assert.equal(await page.$eval('select[aria-label="Filter history by program"]',input=>input.value),'all');
    assert.equal(await page.$eval('select[aria-label="Filter history by time"]',input=>input.value),'all');
    assert.equal(await page.$$eval('.wpb-history-session',rows=>rows.length),50,'A new visit uses the standard first history page');
    // The existing startup normalizer refreshes history updatedAt through the stamped setter.
    // Every record identity and training field must still match; in-visit checks above include stamps.
    const withoutLoadStamp=s=>({...s,history:s.history.map(({updatedAt,...record})=>record)});
    assert.deepEqual(withoutLoadStamp(await training()),withoutLoadStamp(before),'Reload retains every training record');
    assert.deepEqual(errors,[],'No browsing or reload errors');
    return {theme,width,libraryScroll,sessionsScroll,recordsScroll,clear};
  }finally{await context.close();}
}
// Cohesion 13: real manual-program choices retain selection and deload semantics
// while labels grow with text settings. Inactive weeks remain usable choices.
async function verifyWeekChoices(browser,theme){
  const context=await browser.createBrowserContext();
  const page=await context.newPage(),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const fixture={...program,config:{...program.config,deload:true}};
  try{
    await page.setViewport({width:320,height:1000,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.evaluateOnNewDocument(value=>localStorage.setItem('wpb:v1',JSON.stringify(value)),
      {...store(theme),saved:[fixture]});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.goto('http://127.0.0.1:8796/',{waitUntil:'networkidle0'});
    // Native auto-scroll collapses Home context; wait for the real card target to stop moving.
    await page.locator('.hp-open').click({timeout:12000});
    await page.waitForSelector('.wpb-week-rail');
    for(const width of [320,393,834,1280])for(const font of [16,32]){
      await page.setViewport({width,height:1000,deviceScaleFactor:1,isMobile:true,hasTouch:true});
      await page.evaluate(px=>{document.documentElement.style.fontSize=px+'px';return new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));},font);
      const geometry=await page.evaluate(()=>{
        const root=document.querySelector('.wpb-program');
        const choices=[...root.querySelectorAll('.wpb-week-rail>button')];
        return {overflow:root.scrollWidth>root.clientWidth+1,choices:choices.map(b=>{
          const range=document.createRange();range.selectNodeContents(b);
          const box=b.getBoundingClientRect();
          return {height:b.offsetHeight,opacity:Number(getComputedStyle(b).opacity),
            label:parseFloat(getComputedStyle(b.querySelector('.wpb-ui-choice-label')).fontSize),
            caption:parseFloat(getComputedStyle(b.querySelector('.wpb-ui-choice-caption')).fontSize),
            clipped:[...range.getClientRects()].some(r=>r.width>1&&r.height>1&&
              (r.left<box.left-1||r.right>box.right+1||r.top<box.top-1||r.bottom>box.bottom+1))};
        })};
      });
      assert.ok(!geometry.overflow&&geometry.choices.length===5&&geometry.choices.every(b=>
        b.height>=51&&b.opacity>=.9&&!b.clipped&&b.label>=font*.8125-.1&&b.caption>=font*.6875-.1),
        'Whole, readable Program choices at '+JSON.stringify({theme,width,font,geometry}));
      await page.$eval('.wpb-program-week-chip[data-deload="false"]:nth-child(2)',b=>b.click());
      assert.equal(await page.$eval('.wpb-week-rail>button:nth-child(2)',b=>b.getAttribute('aria-pressed')),'true');
      await page.$eval('.wpb-program-week-chip[data-deload="true"]',b=>{b.scrollIntoView({block:'nearest',inline:'nearest'});b.focus();});
      await page.keyboard.press('Enter');
      assert.equal(await page.$eval('.wpb-program-week-chip[data-deload="true"]',b=>b.getAttribute('aria-pressed')),'true',
        'Keyboard can select the actual deload week');
      assert.equal(await page.$$eval('.wpb-week-rail>button[aria-pressed="true"]',bs=>bs.length),1);
    }
    const cdp=await page.createCDPSession();
    await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'},{name:'forced-colors',value:'active'}]});
    const forced=await page.evaluate(()=>{
      const selected=document.querySelector('.wpb-week-rail>[aria-pressed="true"]'),idle=document.querySelector('.wpb-week-rail>[aria-pressed="false"]');
      return {active:matchMedia('(forced-colors:active)').matches,selected:getComputedStyle(selected).backgroundColor,idle:getComputedStyle(idle).backgroundColor,
        outline:parseFloat(getComputedStyle(selected).outlineWidth),transition:getComputedStyle(selected).transitionDuration};
    });
    assert.ok(forced.active&&forced.selected!==forced.idle&&forced.outline>=2&&forced.transition.split(',').every(x=>parseFloat(x)===0),
      'Shared week choice retains forced-color selection, keyboard focus and reduced motion: '+JSON.stringify(forced));
    assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[fixture], 'Browsing weeks never edits the program');
    assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history),[], 'Browsing weeks never logs a session');
    assert.deepEqual(errors,[]);
    console.log('PASS Cohesion 13 Program choices '+theme+': 320/393/834/1280px, 100/200% text, manual/deload keyboard selection, readable inactive weeks, forced colors and unchanged data.');
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
    assert.equal(detail.background,library.background,'Exercise detail retains the Library theme canvas');
    await page.screenshot({path:path.join(root,'verification/r54-detail-'+theme+'-phone.png')});

    // The manual progression program uses the week picker too. Earlier R53
    // ignored its active week and left all four buttons visually and semantically off.
    await page.evaluate(()=>localStorage.removeItem('wpb:live'));
    await page.reload({waitUntil:'networkidle0'});
    // A reload can restore the previous detail route. Open Home explicitly rather
    // than clicking a background program card that is still mounted but obscured.
    await page.waitForSelector('[data-tab="home"]');
    await page.click('[data-tab="home"]');
    await page.waitForSelector('[data-view-frame="home"] .hp-open[aria-label="Open R54 Manual"]', {visible:true});
    await page.click('[data-view-frame="home"] .hp-open[aria-label="Open R54 Manual"]');
    await page.waitForSelector('.wpb-program .wpb-week-rail', {visible:true});
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
    // R63: the utility row remains quiet while notes retain the chosen tint.
    // Exercise the note editor and info overlay with a live user-owned set.
    const beforeInputs=await page.$$eval('[data-testid="set-0-0"] input',els=>els.map(el=>el.value));
    const surrounding=await page.evaluate(()=>{
      const root=document.querySelector('.wpb-workout');
      const note=root.querySelector('button[aria-label^="Edit pinned note for "]');
      const coach=root.querySelector('button[aria-label="Expand Coach guidance"]');
      const tools=[...root.querySelectorAll('.wpb-workout-tool')];
      return {note:note&&{height:note.offsetHeight,background:getComputedStyle(note).backgroundColor},
        coach:coach&&{height:coach.offsetHeight,background:getComputedStyle(coach).backgroundColor},
        tools:tools.map(el=>({height:el.offsetHeight,background:getComputedStyle(el).backgroundColor,
          border:getComputedStyle(el).borderTopColor}))};
    });
    assert.ok(surrounding.note?.height>=44&&surrounding.coach?.height>=40&&surrounding.coach.height<=48&&
      surrounding.note.background!==surrounding.coach.background&&surrounding.tools.length>=3&&
      surrounding.tools.every(x=>x.height>=48&&x.background==='rgba(0, 0, 0, 0)'&&x.border==='rgba(0, 0, 0, 0)'),
      'Theme-tinted note and compact Coach coexist with unfilled full-size tool targets: '+JSON.stringify(surrounding));
    await page.click('button[aria-label^="Edit pinned note for "]');
    await page.waitForSelector('.wpb-workout-scroll textarea');
    await page.$eval('.wpb-workout-scroll textarea',el=>el.select());
    await page.keyboard.type('J-hooks 10');
    await page.$$eval('.wpb-workout-scroll button',buttons=>buttons.find(b=>b.textContent.trim()==='Save note').click());
    await page.waitForFunction(()=>document.querySelector('button[aria-label^="Edit pinned note for "]')?.textContent.includes('J-hooks 10'));
    // Keep a composited, scrolled workout layer while opening the overlay.
    // A sheet must remain anchored to the screen even while its opener animates.
    await page.$eval('.wpb-workout-scroll',el=>{
      el.style.setProperty('transform','translateZ(0)','important');
      el.scrollTop=el.scrollHeight;
    });
    await page.click('button[aria-label^="Exercise info for "]');
    await page.waitForSelector('.wpb-backdrop [data-sheet-drag][data-pi-surface]');
    await page.waitForFunction(()=>document.querySelector('.wpb-backdrop [data-sheet-drag]')?.style.getPropertyValue('--pi-surface-y')==='0px');
    for(const width of [320,393]){
      await page.setViewport({width,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
      const close=await page.$eval('.wpb-backdrop button[aria-label="Close"]',button=>{
        const r=button.getBoundingClientRect(),hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
        return {height:r.height,top:r.top,bottom:r.bottom,hit:button.contains(hit)};
      });
      assert.ok(close.height>=48&&close.top>=0&&close.bottom<=852&&close.hit,
        'Info sheet Close is visible and tappable after editing a scrolled note: '+JSON.stringify({width,close}));
    }
    await page.screenshot({path:path.join(root,'verification/r63-workout-info-'+theme+'-phone.png')});
    await page.click('.wpb-backdrop button[aria-label="Close"]');
    await page.waitForSelector('.wpb-backdrop',{hidden:true});
    assert.deepEqual(await page.$$eval('[data-testid="set-0-0"] input',els=>els.map(el=>el.value)),beforeInputs,
      'Note editing and info menus preserve live weight/reps');
    assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[program],
      'Supporting controls do not rewrite the program');
    diagnostics[theme]={surrounding};
    assert.equal(errors.length,0,'No route errors in '+theme+': '+errors.join('; '));
    diagnostics[theme]={...diagnostics[theme],library,detail,weeks,workout,inputs};
    await context.close();
  }
  for(const theme of ['rose','rosewater'])await verifyWeekChoices(browser,theme);
  const flows=[];
  const nextSessions=[];
  const browsing=[];
  for(const theme of ['rose','rosewater'])
    for(const width of [320,393]){
      flows.push(await verifyNavigationFlow(browser,theme,width));
      nextSessions.push(await verifyNextSessionFlow(browser,theme,width));
      browsing.push(await verifyBrowseMemory(browser,theme,width));
    }
  // M284: the R76 wide-screen fixtures are empty. Certify the real populated
  // Sessions grid using the same recorded-set schema as the R60 64-session test.
  // Screenshots capture both the initial overview and the scrolled history.
  const wideHistory=[];
  for(const theme of ['rose','rosewater']) for(const width of [900,1280]){
    const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    try{
      await page.setViewport({width,height:850,deviceScaleFactor:1,isMobile:false,hasTouch:false});
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
      const entries=Array.from({length:16},(_,i)=>({
        id:'m284-tablet-'+i,updatedAt:i%2===0?0:1700000000000+i,programId:i%2===0?program.id:'m285-secondary',
        programName:i%2===0?program.name:'M285 Second Program',
        dayId:'upper',dayLabel:'Upper',weekIndex:1,date:Date.now()-(i+1)*8*86400000,
        unit:'lb',duration:1800,totalSets:2,tonnage:1470,
        perf:{'bb-bench':{weight:185,reps:6,sets:[{w:185,r:6,done:true,rir:2,rirReported:true}]},
          'inc-curl':{weight:30,reps:12,sets:[{w:30,r:12,done:true,rir:2,rirReported:true}]}}
      }));
      const state={...store(theme),saved:[program,{...program,id:'m285-secondary',name:'M285 Second Program',config:{...program.config,name:'M285 Second Program'}}],history:entries};
      await page.evaluateOnNewDocument(data=>localStorage.setItem('wpb:v1',JSON.stringify(data)),state);
      await page.goto('http://127.0.0.1:8796/',{waitUntil:'networkidle0'});
      await page.waitForSelector('.wpb-tabbar');
      await page.click('[data-tab="progress"]');
      await page.waitForSelector('.wpb-history-session');
      await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
      const sample=async()=>page.evaluate(()=>{
        const pane=document.querySelector('.wpb-progress .wpb-page-scroll[data-scroll-key="sessions"]');
        const chrome=document.querySelector('.wpb-progress-chrome');
        const history=[...pane.querySelectorAll('.wpb-history-session')];
        const filters=pane.querySelector('.wpb-history-filters');
        const grouping=pane.querySelector('.wpb-progress-grouping');
        const calendar=pane.querySelector('.wpb-training-history');
        const p=pane.getBoundingClientRect(),ch=chrome.getBoundingClientRect();
        const rect=el=>{const b=el?.getBoundingClientRect();return b?{x:b.x,y:b.y,right:b.right,bottom:b.bottom,width:b.width,height:b.height}:null;};
        const css=getComputedStyle(pane);
        return {viewport:innerWidth,columns:css.gridTemplateColumns,
          display:css.display,scrollTop:pane.scrollTop,
          count:history.length,headerOpaque:getComputedStyle(chrome).backgroundColor!=='rgba(0, 0, 0, 0)',
          overflow:document.documentElement.scrollWidth>innerWidth+2,
          viewportRect:rect(pane),chrome:rect(chrome),filters:rect(filters),
          grouping:rect(grouping),calendar:rect(calendar),firstSession:rect(history[0]),
          sessionInPane:history.every(node=>{const b=node.getBoundingClientRect();
            return b.left>=p.left-2&&b.right<=p.right+2;})};
      });
      const initial=await sample();
      await page.screenshot({path:path.join(root,`verification/m284-history-${theme}-${width}-top-phone.png`)});
      assert.ok(initial.count===16&&!initial.overflow&&initial.headerOpaque&&initial.sessionInPane,
        'Populated Sessions history remains bounded beneath wide header: '+JSON.stringify({theme,width,initial}));
      assert.ok(width===1280?initial.display==='grid':initial.display!=='grid',
        'History uses responsive grid only at wide layouts: '+JSON.stringify({theme,width,initial}));
      if(width===1280)
        assert.ok(initial.calendar&&initial.firstSession&&initial.grouping&&
          initial.firstSession.y<initial.calendar.bottom-60&&
          initial.firstSession.y<initial.viewportRect.bottom,
          'First logged session begins beside the calendar, not after its full-height grid row: '+
            JSON.stringify({theme,width,initial}));
      const scrolled=await page.$eval('.wpb-progress .wpb-page-scroll[data-scroll-key="sessions"]',el=>{
        el.scrollTop=Math.min(280,el.scrollHeight-el.clientHeight);return el.scrollTop;});
      await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
      const after=await sample();
      await page.screenshot({path:path.join(root,`verification/m284-history-${theme}-${width}-scrolled-phone.png`)});
      assert.ok(scrolled>0&&after.scrollTop>0&&after.headerOpaque&&
        Math.abs(after.chrome.y-initial.chrome.y)<1&&!after.overflow,
        'Populated history scrolls below a stable, opaque Progress header: '+JSON.stringify({theme,width,initial,after}));
      // M285: multi-program and multi-month histories can expose layout
      // holes that single-program history never exercises. Rotate grouping
      // views without changing the saved sessions or leaving the route.
      const grouped={};
      for(const mode of ['By month','By program']){
        await page.evaluate(label=>{
          const button=[...document.querySelectorAll('.wpb-progress-grouping button')]
            .find(node=>node.textContent.trim()===label);
          if(!button)throw new Error('Missing Sessions grouping '+label);
          button.click();
        },mode);
        await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
        const layout=await page.$eval('.wpb-progress .wpb-page-scroll[data-scroll-key="sessions"]',pane=>{
          const p=pane.getBoundingClientRect();
          const groups=[...pane.querySelectorAll('.wpb-history-group-header')].map(node=>{
            const r=node.getBoundingClientRect(),parent=node.parentElement?.getBoundingClientRect();
            return {title:node.textContent.trim().slice(0,80),y:r.y,x:r.x,width:r.width,
              parentLeft:parent?.left,parentRight:parent?.right,
              parentTop:parent?.top,parentBottom:parent?.bottom};
          });
          const button=[...pane.querySelectorAll('.wpb-progress-grouping button')]
            .find(node=>node.getAttribute('aria-pressed')==='true');
          return {groups,selected:button?.textContent.trim(),
            sessions:pane.querySelectorAll('.wpb-history-session').length,
            overflow:document.documentElement.scrollWidth>innerWidth+2,
            pane:{left:p.left,right:p.right},width:innerWidth};
        });
        await page.screenshot({path:path.join(root,
          `verification/m285-history-${theme}-${width}-${mode==='By month'?'month':'program'}-phone.png`)});
        assert.ok(layout.selected===mode&&layout.sessions===16&&
          layout.groups.length>=2&&!layout.overflow&&
          layout.groups.every(g=>g.parentLeft>=layout.pane.left-2&&
            g.parentRight<=layout.pane.right+2),
          'Multi-program/month grouping keeps full history in its reading lane: '+
            JSON.stringify({theme,width,mode,layout}));
        if(width===1280&&mode==='By month'){
          const gaps=layout.groups.slice(1).map((g,i)=>g.parentTop-layout.groups[i].parentBottom);
          assert.ok(gaps.every(gap=>gap>=-1&&gap<=64),
            'Short month groups must not inherit a tall calendar grid row: '+
              JSON.stringify({theme,width,gaps,layout}));
        }
        grouped[mode]=layout;
      }
      if(width===1280)
        assert.ok(grouped['By month'].groups.length>=3&&
          grouped['By program'].groups.length===2,
          'Wide history exposes multiple month groups and two training programs');
      const actualHistory=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history);
      assert.deepEqual(actualHistory,entries,
        'Wide history inspection preserves logged sets and both migrated and edited record clocks');
      assert.deepEqual(errors,[],'No wide history render errors');
      wideHistory.push({theme,width,initial,after});
    }finally{await context.close();}
  }
  console.log('PASS R54/R59/R60: paired Library/Detail/Program/Workout layouts; active Plan, safe workout returns, next due week, retained Library/history/lift filters and independent Progress scroll at 320/393px; '+JSON.stringify({diagnostics,flows,nextSessions,browsing}));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
