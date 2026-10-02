import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
import { program, history } from './m220-day-progression-test.mjs';
const root = path.resolve(new URL('../', import.meta.url).pathname);
const importMap = fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const snapshot = {schemaVersion:2,programId:program.id,dayId:'lower',weekIndex:1,dayExSig:'seated-calf',exIdx:0,elapsedMs:3800000,restPaused:true,restRemain:50,restMax:90,data:[{id:'seated-calf',slot:0,sets:Array.from({length:5},(_,i)=>({weight:i===2?'212.5':'210',reps:'20',done:i===0,auto:i!==2,valueOwner:i===2?'user':'prescription',target:{w:'210',reps:'12-20',rir:'2'}}))}]};
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import {WorkoutSession,StyleTag} from '/modules/App.js';
const program=${JSON.stringify(program)},history=${JSON.stringify(history)},resume=new URL(location.href).searchParams.has('resume');
localStorage.removeItem('wpb:live');if(resume)localStorage.setItem('wpb:live',JSON.stringify(${JSON.stringify(snapshot)}));
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day:program.days[0],weekIndex:1,unit:'lb',perf:{'seated-calf':history[0].perf['seated-calf']},history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{},setUnit:()=>{}})));
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
    const values=()=>page.$$eval('input[aria-label="weight"]',ns=>ns.map(n=>n.value));
    assert.deepEqual(await values(),['205','205','205','205','205'],'fresh five-set Lower workout holds at its own 205 load');
    const text=await page.$eval('.wpb-workout',n=>n.innerText);
    assert.match(text,/Last time: 205 lb/);assert.doesNotMatch(text,/last 210×15/);
    await page.click('button[aria-label="Show previous workout values"]');
    const lastText=await page.$eval('.wpb-workout',n=>n.innerText);
    assert.match(lastText,/205×16/);assert.match(lastText,/205×12/);
    await open('?resume');
    assert.deepEqual(await values(),['210','205','212.5','205','205'],'untouched pending automatic loads refresh while the manual load survives');
    assert.ok(await page.$eval('[data-testid="set-0-0"]',n=>n.innerText.includes('210')),'completed 210 lb set survives');
    await page.screenshot({path:path.join(root,'verification/m220-progression-phone.png')});
    assert.deepEqual(errors,[]);
    console.log('PASS M220 browser: fresh Lower uses 205, LAST/advice agree, resumed automatic rows repair, completed 210 and manual 212.5 remain intact.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
