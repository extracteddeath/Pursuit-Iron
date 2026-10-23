import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const current={
 id:'m328-current',cycleId:'m328-cycle',name:'Swap Undo workout',custom:true,weeks:4,
 config:{unit:'lb',goal:'both',split:'custom',experience:'intermediate',progression:'double',
     equipment:['barbell','dumbbell','bench','rack','cable','machine','smith','ezbar','pullup'],deload:false},
 days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
 overrides:{'upper:0':{sets:3,reps:'5-8',rir:'2',rest:90},'upper:1':{sets:2,reps:'8-12',rir:'2',rest:90}}
};
const next={...structuredClone(current),id:'m328-next',name:'Future phase'};
const unrelated={...structuredClone(current),id:'m328-other',cycleId:'different',name:'Unrelated'};
const store={v:13,savedAt:1,saved:[current,next,unrelated],cycles:[],history:[],perf:{},
 drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:current.id,theme:'amethyst',
 restAutoStart:false,warmupCard:false,unit:'lb',unitChosen:true,experience:'intermediate',
 bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};
const live={schemaVersion:2,sessionId:'m328-workout',programId:current.id,dayId:'upper',
 weekIndex:1,dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:10000,
 readiness:{label:'Normal',factor:1},restPaused:false,restRemain:0,restMax:0,runPaused:true,
 data:[{id:'bb-bench',slot:0,note:'',sets:[1,2,3].map(()=>({weight:'170',reps:'5',done:false,
     auto:true,valueOwner:'prescription',target:{w:'170',reps:'5-8',rir:2}}))},
 {id:'inc-curl',slot:1,note:'',sets:[1,2].map(()=>({weight:'25',reps:'8',done:false,
     auto:true,valueOwner:'prescription',target:{w:'25',reps:'8-12',rir:2}}))}]};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
 const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
     res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
 res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
     headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:393,height:850,isMobile:true,hasTouch:true});
 await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 await page.evaluateOnNewDocument((store,live)=>{
     if(!sessionStorage.getItem('m328-seeded')){
         localStorage.setItem('wpb:v1',JSON.stringify(store));
         localStorage.setItem('wpb:live',JSON.stringify(live));
         sessionStorage.setItem('m328-seeded','1');
     }
 },store,live);
 const loc=`http://127.0.0.1:${server.address().port}/index.html`;
 const read=()=>page.evaluate(()=>({store:JSON.parse(localStorage.getItem('wpb:v1')),
     live:JSON.parse(localStorage.getItem('wpb:live'))}));
 const button=async label=>{
     const found=await page.evaluate(label=>{const b=[...document.querySelectorAll('button')]
         .find(b=>b.textContent.trim()===label);if(b)b.click();return !!b;},label);
     assert.ok(found,'expected button '+label);
 };
 const openSwap=async()=>{
     await page.waitForSelector('.wpb-workout-tool');
     await button('Swap');
     await page.waitForSelector('.wpb-swap-list button.opt');
     await page.$eval('.wpb-swap-list button.opt',b=>b.click());
     await page.waitForFunction(()=>document.body.textContent.includes('Apply this swap'));
     await button('Update my program');
 };
 await page.goto(loc,{waitUntil:'networkidle0'});
 await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.waitForSelector('[data-testid="set-0-1"]');
 await openSwap();
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1'))?.saved?.[0]?.days?.[0]?.exercises?.[0]!=='bb-bench');
 let snap=await read();
 const newId=snap.live.data[0].id;
 assert.notEqual(newId,'bb-bench');
 assert.equal(snap.store.saved[0].days[0].exercises[0],newId);
 assert.equal(snap.store.saved[1].days[0].exercises[0],newId,'sibling block reflects program scope');
 assert.equal(snap.store.saved[2].days[0].exercises[0],'bb-bench','unrelated program untouched');
 assert.ok(await page.$('.wpb-notice button'));
 await button('Undo');
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1'))?.saved?.[0]?.days?.[0]?.exercises?.[0]==='bb-bench');
 snap=await read();
 assert.equal(snap.live.data[0].id,'bb-bench','Undo restores the visible exercise and working sets');
 assert.equal(snap.store.saved[1].days[0].exercises[0],'bb-bench','cycle sibling restored');
 assert.equal(snap.store.saved[2].days[0].exercises[0],'bb-bench');
 assert.equal(snap.store.saved[0].edited,false,'saved plan is no longer marked edited for undone swap');
 assert.equal(snap.store.history.length,0);
 await page.reload({waitUntil:'networkidle0'});
 await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
 await page.waitForSelector('[data-testid="set-0-1"]');
 assert.equal((await read()).live.data[0].id,'bb-bench','Undo persists through resume');
 await openSwap();
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1'))?.saved?.[0]?.days?.[0]?.exercises?.[0]!=='bb-bench');
 // Manual set entry after the swap makes rollback unsafe; do not silently
 // delete what the user just typed or roll back a program independently.
 const weight='[data-testid="set-0-0"] input[aria-label="weight"]';
 await page.$eval(weight,input=>{
     input.value='135';input.dispatchEvent(new Event('input',{bubbles:true}));
 });
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].weight==='135');
 await button('Undo');
 snap=await read();
 assert.equal(snap.store.saved[0].days[0].exercises[0],snap.live.data[0].id);
 assert.notEqual(snap.live.data[0].id,'bb-bench','edited replacement remains in workout');
 assert.equal(snap.live.data[0].sets[0].weight,'135','manual weight survives rejected Undo');
 assert.equal(snap.store.saved[1].days[0].exercises[0],snap.live.data[0].id);
 assert.deepEqual(errors,[]);
 console.log('PASS M328 browser: program-and-cycle Undo, persisted resume, manual-entry conflict protection.');
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
