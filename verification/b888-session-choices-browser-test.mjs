import assert from 'node:assert/strict';
import fs from 'node:fs'; import http from 'node:http'; import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { workoutProgressionOrigin, workoutProgramChanges } from '../modules/session-tools.js';
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
await new Promise(resolve=>server.listen(8798,'127.0.0.1',resolve));
let browser;
try {
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:393,height:850,isMobile:true,hasTouch:true});
 await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 await page.evaluateOnNewDocument((store,live)=>{if(!sessionStorage.getItem('seeded')){localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('wpb:live',JSON.stringify(live));sessionStorage.setItem('seeded','1');}},store,live);
 const clickText=async text=>{const found=await page.evaluate(t=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===t);b?.click();return !!b;},text);assert.ok(found,'button '+text);};
 const getLive=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
 const getStore=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
 await page.goto('http://127.0.0.1:8798/index.html',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');
 await clickText('Log injury');await page.waitForSelector('[aria-label="Injury muscle"]');
 await page.select('[aria-label="Injury muscle"]','biceps'); await page.type('[aria-label="Injury details"]','Elbow discomfort on curls');
 await clickText('Save injury'); await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).injuryLog?.length===1);
 assert.equal((await getStore()).history.length,0,'injury logging does not advance the workout rotation');
 await page.click('[aria-label="Close Injury log"]');
 await page.click('.wpb-live-dock button[aria-label="Resume workout"]');await page.waitForSelector('[data-testid="set-0-2"]');
 assert.ok(await page.$eval('[data-exercise-tags]',n=>n.textContent.includes('Key lift')));
 await page.click('[aria-label="Change exercise progression"]');await page.click('[data-progression-choice="dynamic"]');
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).sessionProgressions?.['bb-bench']==='dynamic');
 let snap=await getLive();const firstOrigin=structuredClone(snap.sessionProgressionOrigins['bb-bench']);assert.equal(firstOrigin.schema,1);assert.equal(firstOrigin.style,null);assert.equal(snap.data[0].sets[0].weight,'170');assert.equal(snap.data[0].sets[0].done,true);
 assert.equal(snap.data[0].sets[1].weight,'175');assert.equal(snap.data[0].sets[1].reps,'7');assert.equal((await getStore()).saved[0].progStyle,undefined);
 assert.equal(await page.$$eval('.wpb-workout-scroll .wpb-session-context button',els=>els.some(b=>b.textContent.includes('Session notes'))),false,'session notes does not precede the exercise heading');
 await page.click('[aria-label="Workout options"]');await page.click('[aria-label="Session notes"]');await page.waitForSelector('[aria-label="Whole session note repetition"]');
 await page.type('.wpb-session-note-editor:nth-child(1) textarea','Try an earlier session');
 await page.select('[aria-label="Whole session note repetition"]','once');
 await page.type('.wpb-session-note-editor:nth-child(2) textarea','Pause on chest');
 await page.select('[aria-label="Barbell bench press note repetition"]','once').catch(async()=>{await page.$$eval('.wpb-session-note-editor select',els=>{els[1].value='once';els[1].dispatchEvent(new Event('change',{bubbles:true}));});});
 await page.click('[aria-label="Close Session notes"]');
 await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.waitForSelector('[data-testid="set-0-2"]');snap=await getLive();assert.deepEqual(snap.sessionProgressionOrigins['bb-bench'],firstOrigin);assert.equal(snap.sessionProgressions['bb-bench'],'dynamic');assert.equal(snap.sessionNote,'Try an earlier session');assert.equal(snap.data[0].note,'Pause on chest');
  // B970: the compact icon is the sole visible per-exercise note action.
  assert.equal(await page.$$eval('.wpb-exercise-heading .wpb-session-choice',els=>els.length),0,
    'Exercise heading does not insert an extra note row');
  const noteAction='[aria-label="Edit session note"][data-has-note="true"]';
  await page.waitForSelector(noteAction);
  const noteMarker=await page.$eval(noteAction,el=>({
    marker:getComputedStyle(el,'::after').content,
    width:el.getBoundingClientRect().width,height:el.getBoundingClientRect().height
  }));
  assert.equal(noteMarker.marker,'""','Saved note exposes a visible indicator');
  assert.ok(noteMarker.width>=48&&noteMarker.height>=48,'Note action retains its 48px hit target');
  await page.click(noteAction);
  await page.waitForSelector('[aria-label="Session notes"].wpb-session-dialog');
  assert.equal(await page.$eval('.wpb-session-note-editor:nth-child(2) textarea',n=>n.value),
    'Pause on chest','Toolbar opens the persisted exercise note');
  await page.click('[aria-label="Close Session notes"]');
 await page.click('[aria-label="Finish workout"]');await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Save & finish'));
 await clickText('Save & finish');
 await page.waitForSelector('[aria-label="Update program progression?"]');
 assert.deepEqual((await getStore()).history[0].progressionOrigins['bb-bench'],firstOrigin);
 assert.equal((await getStore()).history[0].sessionNoteRepeat,'once');assert.equal((await getStore()).history[0].perf['bb-bench'].noteRepeat,'once');
 await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[aria-label="Update program progression?"]');
 await clickText('Update program');await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved[0].progStyle?.['bb-bench']==='dynamic');
 assert.equal((await getStore()).history[0].programUpdateStatus,'applied');
 // Start the next matching workout through Home, rather than fabricating a recovery snapshot.
 await page.waitForSelector('.wpb-home .wpb-primary-action');await page.click('.wpb-home .wpb-primary-action');
 await page.waitForSelector('.wpb-workout');
 await clickText('Skip');
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.sessionNote==='Try an earlier session');
 snap=await getLive();assert.equal(snap.sessionNoteRepeat,'none','repeated session note is now one-use');
 assert.equal(snap.data[0].note,'Pause on chest');assert.equal(snap.data[0].noteRepeat,'none','repeated exercise note is now one-use');
 // Verify role coverage for the accessory and the focused logger.
 await page.click('.wpb-ex-nav-step:nth-child(2)');await page.waitForFunction(()=>document.querySelector('[data-exercise-tags]')?.textContent.includes('Accessory lift'));
 await page.click('.wpb-ex-nav-step:nth-child(1)');
 await page.screenshot({path:'/tmp/b888-workout.png'});
 // Completing the repeated session consumes its reminder without erasing the archived note.
 await page.evaluate(()=>document.querySelector('[data-testid^="set-0-"][data-warm="0"] [aria-label="Mark set done"]').click());
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets.some(s=>s.done&&!s.warm));
 await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.waitForSelector('.wpb-workout');await page.click('[aria-label="Finish workout"]');
 await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Save & finish'));await clickText('Save & finish');
 await page.waitForSelector('.wpb-home .wpb-primary-action');await page.click('.wpb-home .wpb-primary-action');await page.waitForSelector('.wpb-workout');await clickText('Skip');
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.sessionNote==='');snap=await getLive();
 assert.equal(snap.data[0].note,'','exercise reminder expires after one repetition');
 await page.close();
 for (const width of [320,900]) {
  const light=await browser.newPage();light.on('pageerror',e=>errors.push(e.message));
  await light.setViewport({width,height:width===900?480:850,isMobile:width<600,hasTouch:width<600});
  await light.evaluateOnNewDocument((store,live)=>{localStorage.setItem('wpb:v1',JSON.stringify({...store,theme:'rosewater'}));localStorage.setItem('wpb:live',JSON.stringify(live));},store,live);
  await light.goto('http://127.0.0.1:8798/index.html',{waitUntil:'networkidle0'});
  await light.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');await light.click('.wpb-live-dock button[aria-label="Resume workout"]');
  await light.waitForSelector('.wpb-workout');if(await light.$('[aria-label="Focused mode"]'))await light.click('[aria-label="Focused mode"]');
  assert.equal(await light.$$eval('.wpb-session-context button',els=>els.some(b=>b.textContent.includes('Session notes'))),false,'Focus keeps session notes out of the exercise heading');
  await light.click('[aria-label="Workout options"]');
  await light.waitForSelector('.wpb-workout-options-sheet');
  const menu=await light.$eval('.wpb-workout-options-sheet',el=>({height:el.getBoundingClientRect().height,viewport:innerHeight,overflow:getComputedStyle(el).overflowY}));
  assert.ok(menu.height<=menu.viewport&&menu.overflow==='auto','workout options scroll within the viewport');
  await light.click('[aria-label="Session notes"]');await light.waitForSelector('[aria-label="Session notes"].wpb-session-dialog');
  const box=await light.$eval('.wpb-session-dialog',el=>({w:el.clientWidth,scroll:el.scrollWidth,bg:getComputedStyle(el).backgroundColor}));
  assert.ok(box.scroll<=box.w+1,'note sheet fits '+width);
  assert.ok(/^rgb\(/.test(box.bg)||/,[ ]*1\)$/.test(box.bg),'note sheet must be opaque: '+box.bg);
  assert.equal(await light.$$eval('.wpb-session-note-editor',els=>els.length),2,'session and exercise editors in Focus mode');
  await light.close();
 }
 // Exercise added through the real picker: session count and future count are independent.
 for (const apply of [true,false]) {
  const added=await browser.newPage();added.on('pageerror',e=>errors.push(e.message));
  await added.setViewport({width:320,height:850,isMobile:true,hasTouch:true});
  await added.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await added.evaluateOnNewDocument((store,live)=>{if(!sessionStorage.getItem('b903-seeded')){localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('wpb:live',JSON.stringify(live));sessionStorage.setItem('b903-seeded','1');}},store,live);
  const click=async text=>assert.ok(await added.evaluate(t=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===t);b?.click();return !!b;},text),text);
  await added.goto('http://127.0.0.1:8798/index.html',{waitUntil:'networkidle0'});
  await added.click('.wpb-live-dock button[aria-label="Resume workout"]');await added.waitForSelector('.wpb-workout');
  await added.click('[aria-label="Workout options"]');await added.waitForSelector('.wpb-workout-options-sheet');await added.evaluate(()=>[...document.querySelectorAll('.wpb-workout-options-sheet button')].find(b=>b.textContent.includes('Add exercise')).click());
  await added.type('input[placeholder^="Search "]','Reverse EZ bar curl');
  await added.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.includes('Reverse EZ-Bar Curl')));
  await added.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent.includes('Reverse EZ-Bar Curl')).click());
  await added.waitForSelector('[aria-label="Adjust working sets for Reverse EZ-Bar Curl"]');
  await added.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data.at(-1)?.sets.length===3);
  await added.click('[aria-label="Adjust working sets for Reverse EZ-Bar Curl"]');await added.waitForSelector('[aria-label="Working sets"]');
  await added.click('[aria-label="Decrease Working sets"]');await click('Apply set count');
  await added.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data.at(-1)?.sets.length===2);
  await added.reload({waitUntil:'networkidle0'});await added.click('.wpb-live-dock button[aria-label="Resume workout"]');await added.waitForSelector('.wpb-workout');
  assert.equal(await added.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data.at(-1).sets.length),2);
  await added.click('[aria-label="Finish workout"]');await added.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Save & finish'));await click('Save & finish');
  await added.waitForSelector('[aria-label="Update program?"]');
  const input='[aria-label="Future sets for Reverse EZ-Bar Curl"]';
  assert.equal(await added.$eval(input,n=>n.value),'2');
  await added.click('[aria-label="Increase Future sets for Reverse EZ-Bar Curl"]');await added.click('[aria-label="Increase Future sets for Reverse EZ-Bar Curl"]');
  await added.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).history[0].programExerciseChanges?.[0].sets===4);
  await added.reload({waitUntil:'networkidle0'});await added.waitForSelector('[aria-label="Update program?"]');assert.equal(await added.$eval(input,n=>n.value),'4');
  assert.ok(await added.$eval('.wpb-session-dialog',n=>n.scrollWidth<=n.clientWidth+1));
  await added.screenshot({path:'/tmp/b903-program-set-review.png'});
  const before=await added.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history[0].perf);
  await click(apply?'Update program':'Keep this session only');
  await added.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).history[0].programUpdateStatus!=='pending');
  const after=await added.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.deepEqual(after.history[0].perf,before,'review never rewrites logged performance');
  assert.equal(after.saved[0].days[0].exercises.includes('reverse-curl'),apply);
  if(apply){
   assert.equal(after.saved[0].overrides['test:2'].sets,4);
   await added.waitForSelector('.wpb-home .wpb-primary-action');await added.click('.wpb-home .wpb-primary-action');await added.waitForSelector('.wpb-workout');await click('Skip');
   await added.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.data.some(r=>r.id==='reverse-curl'));
   assert.equal(await added.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data.find(r=>r.id==='reverse-curl').sets.filter(s=>!s.warm&&!s.sub).length),4);
  }else assert.deepEqual(after.saved,[program]);
  await added.close();
 }
 // M330: adjust the alternate week's set count through the logger and finish review.
 for(const [width,apply] of [[320,true],[900,false]]) {
  const context=await browser.createBrowserContext();const paired=await context.newPage();
  paired.on('pageerror',e=>errors.push(e.message));
  await paired.setViewport({width,height:850,isMobile:width<600,hasTouch:true});
  await paired.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  const pp={...structuredClone(program),id:'m330-paired',name:'Paired workout',pairs:{'test:0':'db-bench'},
   days:[program.days[0],{id:'other',label:'Other day',primaryIndex:0,exercises:['bb-bench']}],
   overrides:{...program.overrides,'other:0':{sets:2,reps:'6-8',rir:2}}};
  const originalHistory=[];
  const pl={...structuredClone(live),sessionId:'m330-live',programId:pp.id,weekIndex:2,
   data:[{id:'db-bench',slot:0,note:'',sets:[{weight:'50',reps:'8',done:true,auto:true,valueOwner:'prescription'},
    ...[1,2].map(()=>({weight:'50',reps:'6',done:false,auto:true,valueOwner:'prescription'}))]
    .map(set=>({...set,target:{w:'50',reps:'5-8',rir:2}}))},structuredClone(live.data[1])]};
  await paired.evaluateOnNewDocument((store,live)=>{
   if(sessionStorage.getItem('m330-seeded'))return;
   sessionStorage.setItem('m330-seeded','1');localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('wpb:live',JSON.stringify(live));
  },{...store,saved:[pp,program],pinnedId:pp.id,history:originalHistory,theme:width===320?'amethyst':'rosewater'},pl);
  const click=async text=>assert.ok(await paired.evaluate(t=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===t);b?.click();return !!b;},text),text);
  await paired.goto('http://127.0.0.1:8798/index.html',{waitUntil:'networkidle0'});
  await paired.click('.wpb-live-dock button[aria-label="Resume workout"]');
  await paired.waitForSelector('[data-testid="set-0-2"]');
  await paired.$eval('.wpb-add-set',button=>button.click());
  await paired.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets.filter(s=>!s.warm&&!s.sub).length===4);
  await paired.click('[aria-label="Finish workout"]');await paired.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Save & finish'));await click('Save & finish');
  await paired.waitForSelector('[aria-label="Update program?"]');
  const read=()=>paired.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  let stored=await read();const log=structuredClone(stored.history[0]);
  assert.equal(log.programExerciseChanges[0].slot,0);assert.equal(log.programExerciseChanges[0].weekIndex,2);
  assert.deepEqual(log.programExerciseChanges[0].slotExerciseIds,['bb-bench','db-bench']);
  assert.equal(log.perf['db-bench'].sets.filter(s=>s.done!==false).length,1,'only performed work enters history');
  await paired.reload({waitUntil:'networkidle0'});await paired.waitForSelector('[aria-label="Update program?"]');
  const scope=await paired.$eval('[aria-label="Update program?"]',n=>n.textContent);
  assert.match(scope,/Applies every week to this workout slot/);assert.match(scope,/Barbell/);assert.match(scope,/Dumbbell/);
  const geometry=await paired.$eval('[aria-label="Update program?"]',n=>({width:n.clientWidth,scroll:n.scrollWidth}));
  assert.ok(geometry.scroll<=geometry.width+1,'scope fits narrow review');
  await paired.screenshot({path:`/tmp/m330-review-${width}.png`});
  await click(apply?'Update program':'Keep this session only');
  await paired.waitForFunction(status=>JSON.parse(localStorage.getItem('wpb:v1')).history[0].programUpdateStatus===status,{},apply?'applied':'session-only');
  stored=await read();const after=stored.saved.find(p=>p.id===pp.id);
  assert.equal(after.overrides['test:0'].sets,apply?4:3);
  assert.deepEqual(after.days,pp.days,'review does not append a duplicate alternate lift');
  assert.deepEqual(after.pairs,pp.pairs);assert.deepEqual(after.overrides['other:0'],pp.overrides['other:0']);
  assert.deepEqual(stored.saved.find(p=>p.id===program.id),program,'unrelated saved program remains exact');
  assert.deepEqual(stored.history[0].perf,log.perf,'program choice never changes completed training');
  await paired.reload({waitUntil:'networkidle0'});
  stored=await read();assert.equal(stored.history[0].programUpdateStatus,apply?'applied':'session-only');
  assert.equal(stored.saved.find(p=>p.id===pp.id).overrides['test:0'].sets,apply?4:3);
  await context.close();
 }
 // M331: saved/draft conflicts and a failed write must keep the whole review pending.
 for(const [mode,width] of [['saved-method',320],['draft-method',900],['draft-count',320],['legacy-count',320],['write-failure',320],['legacy',900],['deleted',320]]) {
  const context=await browser.createBrowserContext();const reviewPage=await context.newPage();
  reviewPage.on('pageerror',e=>errors.push(e.message));
  await reviewPage.setViewport({width,height:850,isMobile:width<600,hasTouch:true});
  await reviewPage.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  const base={...structuredClone(program),id:'m331-review',progStyle:{'bb-bench':'double'},progStyleLifter:{'bb-bench':true}};
  const entry={id:'m331-finished',date:1700000000000,updatedAt:1700000000000,programId:base.id,dayId:'test',weekIndex:1,
   unit:'lb',programUpdateStatus:'pending',progressionChanges:{'bb-bench':'dynamic'},
   progressionOrigins:{'bb-bench':workoutProgressionOrigin(base,'bb-bench')},
   perf:{'bb-bench':{weight:170,reps:5,sets:[{w:170,r:5,done:true,rir:2,rirReported:true}]}}};
  const later=structuredClone(base);let saved=[base],drafts={};
  if(mode==='saved-method'){later.progStyle['bb-bench']='linear';saved=[later];}
  if(mode==='draft-method'){later.progStyle['bb-bench']='linear';drafts[base.id]=later;}
  if(mode==='draft-count'||mode==='legacy-count'){
   entry.programExerciseChanges=workoutProgramChanges([{id:'bb-bench',slot:0,setCountEdited:true,sets:[{},{},{},{}]}],base.days[0],()=>({sets:3}),base,1);
   if(mode==='legacy-count')entry.programExerciseChanges=entry.programExerciseChanges.map(({exerciseId,sets,previousSets,added})=>({exerciseId,sets,previousSets,added}));
   later.overrides['test:0'].sets=5;drafts[base.id]=later;
  }
  if(mode==='legacy')delete entry.progressionOrigins;
  if(mode==='deleted')saved=[];
  await reviewPage.evaluateOnNewDocument(seed=>{
   if(!sessionStorage.getItem('m331-seeded')){
    sessionStorage.setItem('m331-seeded','1');localStorage.setItem('wpb:v1',JSON.stringify(seed));localStorage.removeItem('wpb:live');
   }
   const set=Storage.prototype.setItem;
   window.__m331Fail=false;window.__m331Attempts=0;
   Storage.prototype.setItem=function(key,value){
    if(key==='wpb:v1'&&window.__m331Fail){window.__m331Attempts++;throw new DOMException('Full','QuotaExceededError');}
    return set.call(this,key,value);
   };
  },{...store,saved,drafts,history:[entry],perf:entry.perf,pinnedId:base.id,theme:width===900?'rosewater':'amethyst'});
  const read=()=>reviewPage.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  const click=async label=>{const button=await reviewPage.waitForSelector(`xpath/.//section[contains(@class, 'wpb-session-dialog')]//button[normalize-space(.)="${label}"]`);await button.click();};
  await reviewPage.goto('http://127.0.0.1:8798/index.html',{waitUntil:'networkidle0'});
  await reviewPage.waitForSelector('.wpb-session-dialog');
  const before=await read();
  if(mode==='write-failure')await reviewPage.evaluate(()=>{window.__m331Fail=true;window.storage={set:async()=>{throw new Error('Unavailable');}};});
  // Two synchronous clicks exercise the write guard before the async save settles.
  await reviewPage.evaluate(()=>{const b=[...document.querySelectorAll('.wpb-session-dialog button')].find(b=>b.textContent.trim()==='Update program');b.click();b.click();});
  await reviewPage.waitForSelector('.wpb-session-dialog [role="alert"]');
  const message=await reviewPage.$eval('.wpb-session-dialog [role="alert"]',n=>n.textContent);
  assert.match(message,mode==='write-failure'?/Could not save/:mode==='legacy'?/older review/:mode==='deleted'?/no longer saved/:mode.endsWith('count')?/set count changed/:/progression or workout slots changed/);
  let after=await read();
  assert.deepEqual(after.saved,before.saved,mode+' saved program must not partially change');
  assert.deepEqual(after.drafts,before.drafts,mode+' draft must not change');
  assert.deepEqual(after.history,before.history,mode+' history stays exact and pending');
  const fit=await reviewPage.$eval('.wpb-session-dialog',n=>({width:n.clientWidth,scroll:n.scrollWidth}));
  assert.ok(fit.scroll<=fit.width+1,'visible conflict message fits '+width);
  await reviewPage.waitForFunction(()=>document.querySelector('.wpb-session-dialog').getBoundingClientRect().bottom<=innerHeight+1);
  if(mode==='saved-method'||mode==='draft-method'||mode==='write-failure')await reviewPage.screenshot({path:`/tmp/m331-${mode}-${width}.png`});
  if(mode==='write-failure'){
   assert.equal(await reviewPage.evaluate(()=>window.__m331Attempts),1,'only one write attempt before retry');
   await reviewPage.reload({waitUntil:'networkidle0'});await reviewPage.waitForSelector('.wpb-session-dialog');
   assert.equal((await read()).history[0].programUpdateStatus,'pending','failed save survives reload as pending');
   await click('Update program');
   await reviewPage.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).history[0].programUpdateStatus==='applied');
   after=await read();assert.equal(after.saved[0].progStyle['bb-bench'],'dynamic');
  }else{
   await click('Keep this session only');
   await reviewPage.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).history[0].programUpdateStatus==='session-only');
   after=await read();assert.deepEqual(after.saved,before.saved);assert.deepEqual(after.drafts,before.drafts);
  }
  assert.deepEqual(after.history[0].perf,before.history[0].perf,'completed performance remains unchanged');
  assert.equal(after.history.length,1,'duplicate clicks never duplicate the workout');
  await context.close();
 }
 console.log('PASS M331 browser: source origins survive resume/finish; saved/draft conflicts, legacy/deleted review, duplicate clicks and failed-write/reload/retry preserve training.');
 assert.deepEqual(errors,[],'no runtime errors');
 console.log('PASS B888 browser: Home injury persistence, tags, progression isolation, protected sets, note recovery, durable post-save program choice');
} finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
