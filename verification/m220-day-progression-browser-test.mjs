import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
import { program, history, squatProgram, squatDay, squatHistory, inclineProgram, inclineHistory, rearProgram, rearHistory } from './m220-day-progression-test.mjs';
const root = path.resolve(new URL('../', import.meta.url).pathname);
const importMap = fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const snapshot = {schemaVersion:2,programId:program.id,dayId:'lower',weekIndex:1,dayExSig:'seated-calf',exIdx:0,elapsedMs:3800000,restPaused:true,restRemain:50,restMax:90,data:[{id:'seated-calf',slot:0,sets:Array.from({length:5},(_,i)=>({weight:i===2?'212.5':'210',reps:'20',done:i===0,auto:i!==2,valueOwner:i===2?'user':'prescription',target:{w:'210',reps:'12-20',rir:'2',prefillReps:'20'}}))}]};
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import RootApp,{WorkoutSession,StyleTag} from '/modules/App.js';import {installAppMotion} from '/modules/ui-motion.js';
const params=new URL(location.href).searchParams,squat=params.has('squat'),incline=params.has('incline'),rear=params.has('rear');
const program=rear?${JSON.stringify(rearProgram)}:incline?${JSON.stringify(inclineProgram)}:squat?${JSON.stringify(squatProgram)}:${JSON.stringify(program)},history=rear?${JSON.stringify(rearHistory)}:incline?${JSON.stringify(inclineHistory)}:squat?${JSON.stringify(squatHistory)}:${JSON.stringify(history)},day=squat?program.days.find(d=>d.id===${JSON.stringify(squatDay.id)}):program.days[0],resume=params.has('resume');
if(params.has('custom')){program.custom=true;delete program.engineSource;}
const owner=createRoot(document.getElementById('root'));localStorage.setItem('wpb:v1',JSON.stringify({v:13,saved:[],history:[],cycles:[],perf:{},drafts:{},tombs:{},theme:'ice',unit:'lb',unitChosen:true,experience:'intermediate',seenIntro:999,seenWhatsNew:999,installDismissedAt:Date.now(),lastBackup:Date.now()}));owner.render(React.createElement(RootApp));installAppMotion(document.getElementById('root'));window.mountProgressionWorkout=()=>{localStorage.removeItem('wpb:live');if(resume){const saved=squat?{schemaVersion:2,programId:program.id,dayId:day.id,weekIndex:1,dayExSig:day.exercises.join('|'),exIdx:0,elapsedMs:3800000,restPaused:true,restRemain:50,restMax:90,data:[{id:'back-squat',slot:0,note:'',sets:Array.from({length:4},(_,i)=>({weight:i===2?'200':'215',reps:i===0?'3':i===2?'6':'5',done:i===0,auto:i!==2,valueOwner:i===2?'user':'prescription',target:{w:'215',reps:'5-8',rir:'2',prefillReps:'5'}}))}]}:${JSON.stringify(snapshot)};if(incline){saved.programId=program.id;saved.dayId=day.id;saved.dayExSig=day.exercises.join('|');saved.data=[{id:'low-inc-bb-bench',slot:0,note:'J-hooks 7.5',sets:Array.from({length:4},(_,i)=>({weight:i===2?'197.5':'210',reps:i===0?'5':'6',done:i===0,auto:i!==2,valueOwner:i===2?'user':'prescription',target:{w:'210',reps:'6-8',rir:'2',prefillReps:i===0?'5':'6'}}))}];}if(rear){saved.programId=program.id;saved.dayId=day.id;saved.dayExSig=day.exercises.join('|');saved.data=[{id:'rear-fly',slot:0,sets:Array.from({length:3},(_,i)=>({weight:i===2?'55':'60',reps:'21',done:false,auto:true,valueOwner:'prescription',target:{w:i===2?'55':'60',reps:'15-25',rir:'2',prefillReps:'21'}}))}];}localStorage.setItem('wpb:live',JSON.stringify(saved));}
owner.render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day,weekIndex:1,unit:'lb',perf:history[0].perf,history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{},setUnit:()=>{}})));};
</script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{const pathname=new URL(req.url,'http://localhost').pathname;if(pathname==='/probe.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}const file=path.resolve(root,'.'+pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8775,'127.0.0.1',resolve));let browser;
try {
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    const open=async query=>{await page.goto('http://127.0.0.1:8775/probe.html'+query,{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');await page.evaluate(()=>window.mountProgressionWorkout());await page.waitForSelector('[data-testid="set-0-1"]');const check=await page.$('.wpb-backdrop button[data-wpb-system-back]');if(check)await check.click();};
    await open('');
    const values=()=>page.$$eval('[data-testid^="set-0-"][data-done="0"] input[aria-label="weight"]',ns=>ns.map(n=>n.value));
    const savedWorkSets=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets.filter(s=>!s.warm&&!s.sub));
    assert.deepEqual(await values(),['205','205','205','205','205'],'fresh five-set Lower workout holds at its own 205 load');
    const text=await page.$eval('.wpb-workout',n=>n.innerText);
    assert.match(text,/5 prior sets/);assert.doesNotMatch(text,/last 210×15/);
    await page.click('button[aria-label="Show previous workout values"]');
    const lastText=await page.$eval('.wpb-workout',n=>n.innerText);
    assert.match(lastText,/205×16/);assert.match(lastText,/205×12/);
    // B1002 screenshot: a later isolation exercise must preserve the 60 lb
    // working load across all three rows; the UI must show the matching raw LAST.
    await open('?rear');
    const rearWork='[data-testid^="set-0-"][data-warm="0"][data-sub="0"]';
    const rearWeights=await page.$$eval(`${rearWork} input[aria-label="weight"]`, nodes=>nodes.map(node=>node.value));
    const rearReps=await page.$$eval(`${rearWork} input[aria-label="reps"]`, nodes=>nodes.map(node=>node.value));
    assert.deepEqual(rearWeights,['60','60','60'],'B1002: last 60x20 cannot prefill 55x21 on the third set');
    assert.deepEqual(rearReps,['21','21','21'],'B1002: rep-only advance remains inside planned rep range');
    await page.click('button[aria-label="Show previous workout values"]');
    const rearLast=await page.$$eval(rearWork, nodes=>nodes.map(node=>node.textContent));
    assert.equal(rearLast.length,3);
    for(const row of rearLast)assert.match(row,/60×20/,'visible LAST must match the same three history rows');
    await page.screenshot({path:path.join(root,'verification/b1002-rear-fly-phone.png')});
    await open('?rear&resume');
    assert.deepEqual(await page.$$eval(`${rearWork} input[aria-label="weight"]`,nodes=>nodes.map(node=>node.value)),
        ['60','60','60'],'B1002: resume repairs a stale automatic third-set 55 lb without touching manual work');
    assert.deepEqual((await savedWorkSets()).map(row=>row.weight),['60','60','60'],
        'Reconciled automatic loads survive persistence and no longer revive the old 55 lb');

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
        assert.deepEqual(setWeights,['205','210','215','205']);assert.deepEqual(setReps,['5','5','5','5']);
        assert.equal(await page.$eval('[data-load-advice]',el=>el.querySelector('button')?.textContent?.trim()),'Use 190 lb',
            'A lighter squat recommendation must require an explicit Use action');
        for(const warm of await page.$$('[data-testid^="set-0-"][data-warm="1"] button[aria-label="Mark set done"]'))await warm.click();
        const first=workRows[0];
        await page.click('button[aria-label="Show previous workout values"]');
        const shortcut=await page.$(`${first} [data-reference-action="last"]`);
        assert.ok(shortcut,'LAST reference uses latest 205 x 3, never older 200 x 3');
        const shortcutText=await shortcut.evaluate(n=>n.innerText);
        assert.match(shortcutText,/205×3/);assert.doesNotMatch(shortcutText,/@/,'planned effort must not be presented as reported');
        for(const [index,text] of [[0,'205×3'],[1,'210×2'],[2,'215×3'],[3,'— new']])
            assert.ok((await page.$eval(workRows[index],n=>n.innerText)).includes(text),`LAST set ${index+1} matches the latest raw workout`);
        await page.click('button[aria-label="Focused mode"]');
        const focusText=await page.$eval('[data-focus-hero]',n=>n.innerText);
        assert.match(focusText,/last 205\s*×\s*3/);assert.doesNotMatch(focusText,/last 200/);
        await page.click('button[aria-label="Exit focused mode"]');
        await page.screenshot({path:path.join(root,'verification/m220-squat-phone.png')});
        await page.click(`${first} [data-reference-action="last"]`);
        assert.equal(await page.$eval(`${first} input[aria-label="weight"]`,n=>n.value),'205');
        assert.equal(await page.$eval(`${first} input[aria-label="reps"]`,n=>n.value),'5', 'applying previous load must not overwrite the current rep target');
        for(const row of workRows.slice(0,3))await page.click(`${row} button[aria-label="Mark set done"]`);
        assert.equal(await page.$(`${workRows[3]} [data-reference-action="last"]`),null,'the new fourth row has no invented prior set reference');
    }
    for(const variant of ['?squat&resume','?squat&resume&custom']){
        await open(variant);
        assert.deepEqual(await page.$$eval('[data-testid^="set-0-"][data-done="0"] input[aria-label="weight"]',ns=>ns.map(n=>n.value)),['210','200','205'],
            'Double progression retains each prior set-position load and completed/manual edits');
        assert.deepEqual(await page.$$eval('[data-testid^="set-0-"][data-done="0"] input[aria-label="reps"]',ns=>ns.map(n=>n.value)),['5','6','5']);
        const resumedSquat=await savedWorkSets();
        assert.deepEqual(resumedSquat.map(s=>s.weight),['215','210','200','205']);
        assert.deepEqual(resumedSquat.map(s=>s.reps),['3','5','6','5']);
        assert.equal(resumedSquat[0].done,true,'completed squat load and reps stay persisted after collapse');
    }
    for(const width of [320,393,430,460]){
        await page.setViewport({width,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
        await open('?incline');
        const work='[data-testid^="set-0-"][data-warm="0"][data-sub="0"]';
        assert.deepEqual(await page.$$eval(`${work} input[aria-label="weight"]`,ns=>ns.map(n=>n.value)),['195','200','205','210']);
        assert.deepEqual(await page.$$eval(`${work} input[aria-label="reps"]`,ns=>ns.map(n=>n.value)),['6','6','6','6']);
        assert.match(await page.$eval('.wpb-load-reference summary',n=>n.textContent),/195–210 lb/,
            'The target summary describes the full retained per-set load range; lower advice is separate');
        await page.click('button[aria-label="Show previous workout values"]');
        const lastRows=await page.$$eval(work,ns=>ns.map(n=>n.textContent));
        for(const [index,text] of ['195×10','200×8','205×6','210×5'].entries())
            assert.ok(lastRows[index].includes(text),'LAST retains the exact reported set '+(index+1));
        if(width===460)await page.screenshot({path:path.join(root,'verification/m338-incline-phone.png')});
        await open('?incline&resume');
        const restored=await savedWorkSets();
        assert.deepEqual(restored.map(s=>s.weight),['210','200','197.5','210'],
            'Resume holds automatic double-progression loads and preserves completed/manual loads');
        assert.equal(restored[0].done,true);
        assert.deepEqual(restored.map(s=>s.reps),['5','6','6','6']);
    }
    assert.deepEqual(errors,[]);
    console.log('PASS M220 browser: rep-first double progression, opt-in fatigue advice, protected resume, honest LAST and tap-to-fill.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
