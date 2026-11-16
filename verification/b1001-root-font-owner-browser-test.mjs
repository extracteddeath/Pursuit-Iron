// B1001: Browser-computed typography (not regex) at normal and 200% Android text size.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const map=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture=[
"import React from '/vendor/react.js';",
"import {createRoot} from '/vendor/react-dom-client.js';",
"import {WorkingSetCountControl} from '/modules/workout-session-controls.js';",
"import {PlanChangeBanner} from '/modules/plan-session-surfaces.js';",
"function Demo(){const [value,setValue]=React.useState('12');",
"const review={changes:[{change:{undone:false}},{change:{undone:true}}]};",
"return React.createElement('main',{className:'wpb wpb-workout'},",
"React.createElement('label',null,'Plain entry',React.createElement('input',{id:'plain'})),",
"React.createElement(WorkingSetCountControl,{label:'Working sets',value,onChange:setValue}),",
"React.createElement(PlanChangeBanner,{review,onClick:()=>{}}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">'
    +'<link rel="stylesheet" href="/app.css">'+map+'</head><body><div id="root"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
    const name=new URL(req.url,'http://localhost').pathname;
    if(name==='/b1001.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
    const filename=path.resolve(root,'.'+name);
    if(!filename.startsWith(root+path.sep)||!fs.existsSync(filename)||!fs.statSync(filename).isFile()){
        res.writeHead(404);res.end();return;
    }
    res.setHeader('Content-Type',mime[path.extname(filename)]||'application/octet-stream');
    res.end(fs.readFileSync(filename));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
        headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page=await browser.newPage(),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.setViewport({width:320,height:600,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.goto('http://127.0.0.1:'+server.address().port+'/b1001.html',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-plan-change-banner');
    const sample=()=>page.evaluate(()=>{
        const style=selector=>parseFloat(getComputedStyle(document.querySelector(selector)).fontSize);
        const input=document.querySelector('.wpb-working-set-stepper input').getBoundingClientRect();
        return {plain:style('#plain'),stepper:style('.wpb-working-set-stepper input'),
            plan:style('.wpb-plan-change-banner strong'),inputWidth:input.width,
            pageWidth:document.documentElement.scrollWidth,viewport:innerWidth};
    });
    const normal=await sample();
    assert.ok(normal.plain>=16,'unclassified input remains safe from mobile browser autozoom '+JSON.stringify(normal));
    assert.ok(normal.stepper>=20&&normal.plan>=14,'component fonts win against global reset '+JSON.stringify(normal));
    await page.evaluate(()=>document.documentElement.style.fontSize='32px');
    const large=await sample();
    assert.ok(large.stepper>=39.5&&large.plan>=27.5,
        'component font-size scales to 200% instead of inheriting 13px '+JSON.stringify(large));
    assert.ok(large.inputWidth>=56&&large.pageWidth<=321,
        'large text fits the numeric control and the 320px app viewport '+JSON.stringify(large));
    assert.deepEqual(errors,[],'no browser runtime errors');
    console.log('PASS B1001 browser: actual 16px plain input, 200%-scaled Workout and Plan text, 320px width.');
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
