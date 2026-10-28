import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
import { program, history } from './b904-session-review-test.mjs';
const root = path.resolve(new URL('../', import.meta.url).pathname);
const importMap = fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import {WorkoutSession,StyleTag} from '/modules/App.js';
const program=${JSON.stringify(program)},history=${JSON.stringify(history)};
const root=createRoot(document.getElementById('root'));
root.render(React.createElement('div',{className:'wpb',style:{height:'100%'}},
    React.createElement(StyleTag),React.createElement(WorkoutSession,{
        program,day:program.days[0],weekIndex:2,unit:'kg',perf:history[0].perf,history,
        restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{},setUnit:()=>{}
    })));
</script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
    const pathname=new URL(req.url,'http://localhost').pathname;
    if(pathname==='/b944.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
    const file=path.resolve(root,'.'+pathname);
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
 await page.setViewport({width:393,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 const url='http://127.0.0.1:'+server.address().port+'/b944.html';
 await page.goto(url,{waitUntil:'networkidle0'});
 await page.waitForSelector('.wpb-readiness-dialog');
 const weightInputs='[data-testid^="set-0-"][data-warm="0"][data-sub="0"] input[aria-label="weight"]';
 const weights=()=>page.$$eval(weightInputs,nodes=>nodes.map(n=>Number(n.value)));
 const baseline=await weights();
 assert.ok(baseline.length>=3&&baseline.every(n=>n>0),'real working prescription available');
 const repsBefore=await page.$$eval('[data-testid^="set-0-"][data-warm="0"][data-sub="0"] input[aria-label="reps"]',nodes=>nodes.map(n=>n.value));
 await page.click('[data-readiness-factor="0.95"]');
 await page.waitForFunction(()=>!document.querySelector('.wpb-readiness-dialog'));
 const off=await weights();
 assert.ok(off.every((n,i)=>n<=baseline[i]),'readiness never increases the working load');
 assert.ok(off.some((n,i)=>n<baseline[i]),'readiness actually reduces load');
 const open=async()=>{await page.click('button[aria-label="Workout options"]');
     await page.click('button[aria-label="Adjust workout readiness"]');
     await page.waitForSelector('.wpb-readiness-dialog');};
 await open();await page.click('[data-readiness-factor="0.88"]');
 await page.waitForFunction(()=>!document.querySelector('.wpb-readiness-dialog'));
 const low=await weights();
 assert.ok(low.some((n,i)=>n<off[i]),'second readiness choice reduces from the ORIGINAL load');
 await open();await page.click('[data-readiness-factor="1"]');
 await page.waitForFunction(()=>!document.querySelector('.wpb-readiness-dialog'));
 assert.deepEqual(await weights(),baseline,'strong restores initial suggested working loads exactly');
 assert.deepEqual(await page.$$eval('[data-testid^="set-0-"][data-warm="0"][data-sub="0"] input[aria-label="reps"]',nodes=>nodes.map(n=>n.value)),
    repsBefore,'reps remain untouched');
 await open();await page.click('[data-readiness-factor="0.95"]');
 await page.waitForFunction(()=>!document.querySelector('.wpb-readiness-dialog'));
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')||'{}').readiness?.factor===.95);
 await page.reload({waitUntil:'networkidle0'});
 await page.waitForSelector('.wpb-workout');
 assert.equal(await page.$('.wpb-readiness-dialog'),null,'resume does not repeat readiness check-in');
 assert.deepEqual(await weights(),off,'resume reapplies readiness after canonical prescription refresh');
 assert.deepEqual(errors,[]);
 console.log('PASS B944 browser: repeatable check-in, no compounded load scaling, full baseline restore and durable resume.');
}finally{if(browser)await browser.close();await new Promise(done=>server.close(done));}
