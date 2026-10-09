import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
import { program, history, squatProgram, squatDay, squatHistory } from './m220-day-progression-test.mjs';
const root = path.resolve(new URL('../', import.meta.url).pathname);
const importMap = fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const snapshot = {schemaVersion:2,programId:program.id,dayId:'lower',weekIndex:1,dayExSig:'seated-calf',exIdx:0,elapsedMs:3800000,restPaused:true,restRemain:50,restMax:90,data:[{id:'seated-calf',slot:0,sets:Array.from({length:5},(_,i)=>({weight:i===2?'212.5':'210',reps:'20',done:i===0,auto:i!==2,valueOwner:i===2?'user':'prescription',target:{w:'210',reps:'12-20',rir:'2'}}))}]};
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import {WorkoutSession,StyleTag} from '/modules/App.js';
const params=new URL(location.href).searchParams,squat=params.has('squat');
const program=squat?${JSON.stringify(squatProgram)}:${JSON.stringify(program)},history=squat?${JSON.stringify(squatHistory)}:${JSON.stringify(history)},day=squat?program.days.find(d=>d.id===${JSON.stringify(squatDay.id)}):program.days[0],resume=params.has('resume');
if(params.has('custom')){program.custom=true;delete program.engineSource;}
localStorage.removeItem('wpb:live');if(resume){const saved=squat?{schemaVersion:2,programId:program.id,dayId:day.id,weekIndex:1,dayExSig:day.exercises.join('|'),exIdx:0,elapsedMs:3800000,restPaused:true,restRemain:50,restMax:90,data:[{id:'back-squat',slot:0,note:'',sets:Array.from({length:4},(_,i)=>({weight:i===2?'200':'215',reps:i===0?'3':i===2?'6':'5',done:i===0,auto:i!==2,valueOwner:i===2?'user':'prescription',target:{w:'215',reps:'5-8',rir:'2',prefillReps:'5'}}))}]}:${JSON.stringify(snapshot)};localStorage.setItem('wpb:live',JSON.stringify(saved));}
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day,weekIndex:1,unit:'lb',perf:history[0].perf,history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{},setUnit:()=>{}})));
</script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{const pathname=new URL(req.url,'http://localhost').pathname;if(pathname==='/probe.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}const file=path.resolve(root,'.'+pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8775,'127.0.0.1',resolve));let browser;
try {
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    const open=async query=>{await page.goto('http://127.0.0.1:8775/probe.html'+query,{waitUntil:'networkidle0'});await page.waitForSelector('[data-testid="set-0-1"]');const check=await page.$('.wpb-backdrop button[data-wpb-system-back]');if(check)await check.click();};
    await open('');
    const values=()=>page.$$eval('[data-testid^="set-0-"][data-done="0"] input[aria-label="weight"]',ns=>ns.map(n=>n.value));
    const savedWorkSets=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets.filter(s=>!s.warm&&!s.sub));
    assert.deepEqual(await values(),['205','205','205','205','205'],'fresh five-set Lower workout holds at its own 205 load');
    const text=await page.$eval('.wpb-workout',n=>n.innerText);
    assert.match(text,/5 prior sets/);assert.doesNotMatch(text,/last 210×15/);
    await page.click('button[aria-label="Show previous workout values"]');
    const lastText=await page.$eval('.wpb-workout',n=>n.innerText);
    assert.match(lastText,/205×16/);assert.match(lastText,/205×12/);
    await open('?resume');
    assert.deepEqual(await values(),['205','212.5','205','205'],'untouched pending automatic loads refresh while the manual load survives');
    const resumedCalf=await savedWorkSets();
    assert.deepEqual(resumedCalf.map(s=>s.weight),['210','205','212.5','205','205']);
    assert.equal(resumedCalf[0].done,true,'completed 210 lb set survives');
    assert.deepEqual(await page.$eval('[data-testid="set-0-0"]',row=>[row.querySelector('input[aria-label="weight"]')?.value,row.querySelector('input[aria-label="reps"]')?.readOnly]),['210',true], 'completed set retains faded full-row weight/reps');
    await page.screenshot({path:path.join(root,'verification/m220-progression-phone.png')});
    for(const variant of ['?squat','?squat&custom']){
        await open(variant);
        const workSelector='[data-testid^="set-0-"][data-warm="0"][data-sub="0"]';
        const workRows=await page.$$eval(workSelector,ns=>ns.map(n=>`[data-testid="${n.dataset.testid}"]`));
        const setWeights=await page.$$eval(`${workSelector} input[aria-label="weight"]`,ns=>ns.map(n=>n.value));
        const setReps=await page.$$eval(`${workSelector} input[aria-label="reps"]`,ns=>ns.map(n=>n.value));
        assert.deepEqual(setWeights,['190','190','190','190']);assert.deepEqual(setReps,['5','5','5','5']);
        for(const warm of await page.$$('[data-testid^="set-0-"][data-warm="1"] button[aria-label="Mark set done"]'))await warm.click();
        const first=workRows[0];
        const shortcut=await page.$(`${first} button[aria-label="Use last time's 205 by 3"]`);
        assert.ok(shortcut,'Last time shortcut uses latest 205 x 3, never older 200 x 3');
        const shortcutText=await shortcut.evaluate(n=>n.innerText);
        assert.match(shortcutText,/205×3/);assert.doesNotMatch(shortcutText,/@/,'planned effort must not be presented as reported');
        await page.click('button[aria-label="Show previous workout values"]');
        for(const [index,text] of [[0,'205×3'],[1,'210×2'],[2,'215×3'],[3,'— new']])
            assert.ok((await page.$eval(workRows[index],n=>n.innerText)).includes(text),`LAST set ${index+1} matches the latest raw workout`);
        await page.click('button[aria-label="Focused mode"]');
        const focusText=await page.$eval('[data-focus-hero]',n=>n.innerText);
        assert.match(focusText,/last 205\s*×\s*3/);assert.doesNotMatch(focusText,/last 200/);
        await page.click('button[aria-label="Exit focused mode"]');
        await page.screenshot({path:path.join(root,'verification/m220-squat-phone.png')});
        await page.click(`${first} button[aria-label="Use last time's 205 by 3"]`);
        assert.equal(await page.$eval(`${first} input[aria-label="weight"]`,n=>n.value),'205');
        assert.equal(await page.$eval(`${first} input[aria-label="reps"]`,n=>n.value),'3');
        for(const row of workRows.slice(0,3))await page.click(`${row} button[aria-label="Mark set done"]`);
        assert.equal(await page.$(`${workRows[3]} button[aria-label^="Use last time's"]`),null,'the new fourth row has no invented prior set shortcut');
    }
    for(const variant of ['?squat&resume','?squat&resume&custom']){
        await open(variant);
        assert.deepEqual(await page.$$eval('[data-testid^="set-0-"][data-done="0"] input[aria-label="weight"]',ns=>ns.map(n=>n.value)),['190','200','190'],
            'stale automatic 215 targets refresh, while completed 215 and manually typed 200 survive');
        assert.deepEqual(await page.$$eval('[data-testid^="set-0-"][data-done="0"] input[aria-label="reps"]',ns=>ns.map(n=>n.value)),['5','6','5']);
        const resumedSquat=await savedWorkSets();
        assert.deepEqual(resumedSquat.map(s=>s.weight),['215','190','200','190']);
        assert.deepEqual(resumedSquat.map(s=>s.reps),['3','5','6','5']);
        assert.equal(resumedSquat[0].done,true,'completed squat load and reps stay persisted after collapse');
    }
    assert.deepEqual(errors,[]);
    console.log('PASS M220 browser: scoped Lower history, protected resume, generated/custom incomplete squat recalibration, latest LAST/shortcut agreement and tap-to-fill.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
