import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
import { program, history } from './m222-custom-program-parity-test.mjs';
const root = path.resolve(new URL('../', import.meta.url).pathname);
const importMap = fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const snapshot = {schemaVersion:2,programId:program.id,dayId:'push',weekIndex:1,dayExSig:'cable-fly',exIdx:0,elapsedMs:3800000,data:[{id:'cable-fly',slot:0,superset:true,sets:Array.from({length:4},(_,i)=>({weight:i===2?'17.5':'20',reps:'20',done:i===0,auto:i!==1,valueOwner:i===1?'user':'prescription',target:{w:i===2?'25':'20',reps:'10-20',rir:'2'}}))}]};
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import {WorkoutSession,StyleTag} from '/modules/App.js';
const program=${JSON.stringify(program)},history=${JSON.stringify(history)},params=new URL(location.href).searchParams,resume=params.has('resume'); if(params.has('dynamic'))program.progStyle={'cable-fly':'dynamic'};if(params.has('down'))history[0].perf['cable-fly'].sets.forEach(s=>{s.w=25;s.r=8;s.rir=0;});if(params.has('nosuperset'))program.config.noSupersets=true;
localStorage.removeItem('wpb:live');if(resume)localStorage.setItem('wpb:live',JSON.stringify(${JSON.stringify(snapshot)}));
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day:program.days[0],weekIndex:1,unit:'lb',perf:{'seated-calf':history[0].perf['seated-calf']},history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{},setUnit:()=>{}})));
</script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{const pathname=new URL(req.url,'http://localhost').pathname;if(pathname==='/probe.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}const file=path.resolve(root,'.'+pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8776,'127.0.0.1',resolve));let browser;
try {
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    const open=async query=>{await page.goto('http://127.0.0.1:8776/probe.html'+query,{waitUntil:'networkidle0'});await page.waitForSelector('[data-testid="set-0-1"]');const check=await page.$('.wpb-backdrop button[data-wpb-system-back]');if(check)await check.click();};
    const values=()=>page.$$eval('[data-done="0"] input[aria-label="weight"]',ns=>ns.map(n=>n.value));
    const reps=()=>page.$$eval('[data-done="0"] input[aria-label="reps"]',ns=>ns.map(n=>n.value));
    await open('');
    assert.deepEqual(await values(),['25','25','25','25']);assert.deepEqual(await reps(),['20','11','10','11']);
    assert.match(await page.$eval('.wpb-workout',n=>n.innerText),/4 prior sets/);
    await page.click('button[aria-label="Show previous workout values"]');
    const lastText=await page.$eval('.wpb-workout',n=>n.innerText);assert.match(lastText,/25×19/);assert.match(lastText,/20×11/);
    await page.screenshot({path:path.join(root,'verification/m222-progression-phone.png')});
    await open('?dynamic');assert.deepEqual(await values(),['25','25','20','25']);assert.deepEqual(await reps(),['20','11','12','11']);
    assert.match(await page.$eval('.wpb-workout',n=>n.innerText),/20–25 lb/);
    await open('?down');assert.deepEqual(await values(),['20','20','20','20']);assert.deepEqual(await reps(),['10','10','10','10']);
    await open('?resume&nosuperset');assert.deepEqual(await values(),['20','17.5','25']);assert.deepEqual(await reps(),['20','20','11']);
    const resumed=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets);
    assert.deepEqual(resumed.map(s=>s.weight),['20','20','17.5','25']);assert.deepEqual(resumed.map(s=>s.reps),['20','20','20','11']);
    assert.equal(resumed[0].done,true,'completed set keeps its logged values');
    assert.equal(await page.$$eval('[data-testid="set-0-0"] input', ns=>ns.every(n=>n.readOnly) && ns.length===2),true,'completed logged values remain visible and locked');
    assert.ok(!(await page.$eval('.wpb-workout',n=>n.innerText)).includes('Unlink superset'));assert.equal(await page.$eval('.wpb-workout-tools',n=>n.innerText.includes('Superset')),false);
    // Typed/manual values survive state writes and a reload.
    const input=await page.$('[data-testid="set-0-3"] input[aria-label="reps"]');await input.click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');await page.keyboard.press('Backspace');await input.type('13');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[3].reps==='13');
    const state=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));assert.equal(state.data[0].sets[3].valueOwner,'user');
    assert.deepEqual(errors,[]);
    console.log('PASS M222 browser: screenshot Cable Fly targets, LAST evidence, mixed DDP loads/reps, reduced-load floor, protected resume/manual typing, no-superset setting and no runtime exceptions.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
