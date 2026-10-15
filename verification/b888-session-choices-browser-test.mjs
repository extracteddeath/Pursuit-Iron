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
 let snap=await getLive();assert.equal(snap.data[0].sets[0].weight,'170');assert.equal(snap.data[0].sets[0].done,true);
 assert.equal(snap.data[0].sets[1].weight,'175');assert.equal(snap.data[0].sets[1].reps,'7');assert.equal((await getStore()).saved[0].progStyle,undefined);
 await clickText('Session notes');await page.waitForSelector('[aria-label="Whole session note repetition"]');
 await page.type('.wpb-session-note-editor:nth-child(1) textarea','Try an earlier session');
 await page.select('[aria-label="Whole session note repetition"]','once');
 await page.type('.wpb-session-note-editor:nth-child(2) textarea','Pause on chest');
 await page.select('[aria-label="Barbell bench press note repetition"]','once').catch(async()=>{await page.$$eval('.wpb-session-note-editor select',els=>{els[1].value='once';els[1].dispatchEvent(new Event('change',{bubbles:true}));});});
 await page.click('[aria-label="Close Session notes"]');
 await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.waitForSelector('[data-testid="set-0-2"]');snap=await getLive();assert.equal(snap.sessionProgressions['bb-bench'],'dynamic');assert.equal(snap.sessionNote,'Try an earlier session');assert.equal(snap.data[0].note,'Pause on chest');
 await page.click('[aria-label="Finish workout"]');await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Save & finish'));
 await clickText('Save & finish');
 await page.waitForSelector('[aria-label="Update program progression?"]');
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
  await light.setViewport({width,height:850,isMobile:width<600,hasTouch:width<600});
  await light.evaluateOnNewDocument((store,live)=>{localStorage.setItem('wpb:v1',JSON.stringify({...store,theme:'rosewater'}));localStorage.setItem('wpb:live',JSON.stringify(live));},store,live);
  await light.goto('http://127.0.0.1:8798/index.html',{waitUntil:'networkidle0'});
  await light.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');await light.click('.wpb-live-dock button[aria-label="Resume workout"]');
  await light.waitForSelector('.wpb-workout');if(await light.$('[aria-label="Focused mode"]'))await light.click('[aria-label="Focused mode"]');
  await light.evaluate(()=>document.querySelector('.wpb-session-context button').click());await light.waitForSelector('[aria-label="Session notes"]');
  const box=await light.$eval('.wpb-session-dialog',el=>({w:el.clientWidth,scroll:el.scrollWidth,bg:getComputedStyle(el).backgroundColor}));
  assert.ok(box.scroll<=box.w+1,'note sheet fits '+width);
  assert.ok(/^rgb\(/.test(box.bg)||/,[ ]*1\)$/.test(box.bg),'note sheet must be opaque: '+box.bg);
  assert.equal(await light.$$eval('.wpb-session-note-editor',els=>els.length),2,'session and exercise editors in Focus mode');
  await light.close();
 }
 assert.deepEqual(errors,[],'no runtime errors');
 console.log('PASS B888 browser: Home injury persistence, tags, progression isolation, protected sets, note recovery, durable post-save program choice');
} finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
