import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const program={id:'m232',custom:true,weeks:4,config:{unit:'lb',weeks:4,goal:'hypertrophy',experience:'intermediate',progression:'auto',equipment:['cable']},
    days:[{id:'d',label:'Pull',primaryIndex:-1,exercises:['cable-fly']}],overrides:{'d:0':{sets:4,reps:'10-15',rir:'2'}},progStyle:{'cable-fly':'double'}};
const rows=Array.from({length:4},(_,i)=>({weight:i===3?'103':'100',reps:i===0?'8':i===1?'9':i===3?'13':'10',
    done:i===0,auto:i===2,valueOwner:i===2?'prescription':'user',target:{w:'100',prefillReps:'10',reps:'10-15',rir:'2'}}));
const snapshot={schemaVersion:2,programId:program.id,dayId:'d',weekIndex:1,dayExSig:'cable-fly',exIdx:0,elapsedMs:2000,
    data:[{id:'cable-fly',slot:0,note:'',sets:rows}]};
const history=[{id:'past',programId:program.id,dayId:'d',date:2000,weekIndex:1,unit:'lb',perf:{'cable-fly':{weight:100,reps:10,sets:Array.from({length:4},()=>({w:100,r:10,rir:2}))}}}];
const html=`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import {WorkoutSession,StyleTag} from '/modules/App.js';
if(!localStorage.getItem('wpb:live'))localStorage.setItem('wpb:live',JSON.stringify(${JSON.stringify(snapshot)}));
const program=${JSON.stringify(program)};createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb'},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day:program.days[0],weekIndex:1,unit:'lb',perf:{},history:${JSON.stringify(history)},restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{}})));
</script></body></html>`;
const server=http.createServer((req,res)=>{const uri=new URL(req.url,'http://localhost').pathname;
    if(uri==='/probe.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
    const file=path.resolve(root,'.'+uri);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8785,'127.0.0.1',resolve));let browser;
try{
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.setViewport({width:390,height:844,isMobile:true,hasTouch:true});
    const open=async()=>{await page.goto('http://127.0.0.1:8785/probe.html',{waitUntil:'networkidle0'});await page.waitForSelector('[data-testid="set-0-1"]');const close=await page.$('.wpb-backdrop button[data-wpb-system-back]');if(close)await close.click();};
    const waitWeight=weight=>page.waitForFunction(w=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[2].weight===w,{},weight);
    await open();await page.click('[data-testid="set-0-1"] button[aria-label="Mark set done"]');await waitWeight('95');
    let saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets);
    assert.equal(saved[0].weight,'100');assert.equal(saved[2].reps,'10');assert.equal(saved[2].recoveryLimited,true);assert.equal(saved[3].weight,'103');assert.equal(saved[3].reps,'13');
    await page.click('[data-testid="set-0-1"] button[aria-label="Mark set not done"]');await waitWeight('100');
    await page.click('[data-testid="set-0-1"] button[aria-label="Mark set done"]');await waitWeight('95');
    await open();await waitWeight('95');assert.equal(await page.$eval('[data-testid="set-0-3"] input[aria-label="weight"]',n=>n.value),'103');
    assert.deepEqual(errors,[]);console.log('PASS M232 browser: actual completion handler reduces only automatic pending work, protects typing, reverses undo and survives reload.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
