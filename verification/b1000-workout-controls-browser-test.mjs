// B1000: actual responsive workout exercise rail and working-set controls.
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
"import {WorkoutExerciseNav,WorkingSetCountControl} from '/modules/workout-session-controls.js';",
"function Demo(){const [index,setIndex]=React.useState(0);const [count,setCount]=React.useState('12');",
"window.chooseLast=()=>setIndex(17);",
"const rows=Array.from({length:18},()=>({id:'bb-bench',sets:[]}));",
"return React.createElement('main',{className:'wpb wpb-workout'},",
"React.createElement(WorkoutExerciseNav,{data:rows,exIdx:index,onSelect:setIndex}),",
"React.createElement(WorkingSetCountControl,{label:'Working sets',value:count,onChange:setCount}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">'
    +'<link rel="stylesheet" href="/app.css">'+map+'</head><body><div id="root"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
    const name=new URL(req.url,'http://localhost').pathname;
    if(name==='/b1000.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
    const file=path.resolve(root,'.'+name);
    if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
        res.writeHead(404);res.end();return;
    }
    res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
    res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try {
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
        headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page=await browser.newPage(),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.setViewport({width:393,height:740,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.goto('http://127.0.0.1:'+server.address().port+'/b1000.html',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-ex-nav-step');
    assert.equal(await page.$eval('.wpb-ex-nav-step',el=>Math.round(el.getBoundingClientRect().height)),48,
        'every workout rail button must have an actual 48px hit target');
    await page.evaluate(()=>window.chooseLast());
    await page.waitForFunction(()=>document.querySelector('.wpb-ex-nav')?.scrollLeft>0);
    const rail=await page.evaluate(()=>{
        const nav=document.querySelector('.wpb-ex-nav').getBoundingClientRect();
        const active=document.querySelector('.wpb-ex-nav-step[aria-current="step"]').getBoundingClientRect();
        return {left:active.left,right:active.right,railLeft:nav.left,railRight:nav.right};
    });
    assert.ok(rail.left>=rail.railLeft-1&&rail.right<=rail.railRight+1,
        'current exercise is completely revealed inside its own rail: '+JSON.stringify(rail));
    await page.setViewport({width:320,height:550,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.evaluate(()=>document.documentElement.style.fontSize='32px');
    const scaled=await page.evaluate(()=>{
        const input=document.querySelector('.wpb-working-set-stepper input');
        const buttons=[...document.querySelectorAll('.wpb-working-set-stepper button')];
        const nav=document.querySelector('.wpb-ex-nav-step').getBoundingClientRect();
        const inputRect=input.getBoundingClientRect();
        return {inputWidth:inputRect.width,fontSize:parseFloat(getComputedStyle(input).fontSize),
            buttonSizes:buttons.map(el=>el.getBoundingClientRect().height),
            navHeight:nav.height,pageWidth:document.documentElement.scrollWidth,viewport:innerWidth};
    });
    assert.ok(scaled.navHeight>=48&&scaled.buttonSizes.every(size=>size>=48),
        'scaled workout navigation and steppers retain touch targets: '+JSON.stringify(scaled));
    assert.ok(scaled.inputWidth>=56&&scaled.fontSize>=38,
        'two digit input stays legible at 200% text: '+JSON.stringify(scaled));
    assert.ok(scaled.pageWidth<=321,'only navigation rail scrolls, not the whole screen: '+JSON.stringify(scaled));
    assert.deepEqual(errors,[],'no browser runtime exceptions');
    console.log('PASS B1000 browser: 48px exercise rail, active reveal, 320px/200% legible stepper and no page overflow.');
} finally {await browser?.close();await new Promise(resolve=>server.close(resolve));}
