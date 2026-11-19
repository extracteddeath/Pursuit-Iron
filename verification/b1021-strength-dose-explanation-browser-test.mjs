import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { saved } from './m326-cycle-refresh-test.mjs';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const fixture=structuredClone(saved.find(p=>p.nextEngine?.program?.phase==='strength_accumulation'));
assert.ok(fixture,'A real generated Strength preview must exist for this test');
const original=JSON.stringify(fixture);
const limited={
    comparison:'last_hypertrophy_week_to_first_strength_week',
    retention:.6,status:'limited',
    requested:{upper_back:6,side_delts:5},
    target:{upper_back:4,side_delts:5},
    actual:{upper_back:3.5,side_delts:4.5},
    unresolved:['upper_back','side_delts'],
    limits:[
        {region:'upper_back',requested:6,feasible:4,actual:3.5,reasonCode:'SHARED_MUSCLE_CEILING'},
        {region:'side_delts',requested:5,feasible:5,actual:4.5,reasonCode:'NO_SAFE_ADDITION'}
    ]
};
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)?.[0];
assert.ok(importMap);
const html=`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import {CycleBlockPreview,StyleTag} from '/modules/App.js';
const root=createRoot(document.getElementById('root'));
window.renderStrengthDose=(program)=>root.render(React.createElement('div',{className:'wpb',style:{height:'100vh'}},
    React.createElement(StyleTag,{theme:'amethyst'}),
    React.createElement(CycleBlockPreview,{program,onClose:()=>{}})));
</script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.mjs':'text/javascript'};
const server=http.createServer((req,res)=>{
    const url=new URL(req.url,'http://localhost').pathname;
    if(url==='/dose-fixture.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
    const file=path.resolve(root,'.'+url);
    if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
    res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    for(const width of [320,393,900]){
        const page=await browser.newPage(),errors=[];
        page.on('pageerror',e=>errors.push(e.message));
        await page.setViewport({width,height:850,isMobile:width<600,hasTouch:width<600});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.goto('http://127.0.0.1:'+server.address().port+'/dose-fixture.html',{waitUntil:'networkidle0'});
        const configured={...fixture,nextEngine:{...fixture.nextEngine,strengthEntryDose:limited}};
        await page.waitForFunction(()=>typeof window.renderStrengthDose==='function');
        await page.evaluate(program=>window.renderStrengthDose(program),configured);
        await page.waitForSelector('.wpb-strength-dose-review');
        assert.equal(await page.$eval('.wpb-strength-dose-review',node=>node.dataset.strengthDoseStatus),'limited');
        assert.match(await page.$eval('.wpb-strength-dose-review>summary',node=>node.textContent),/2 areas below goal/);
        assert.equal(await page.$eval('.wpb-strength-dose-review',node=>node.open),false,'The report is discoverable but not visually dominant');
        assert.ok(await page.$('.wpb-cycle-preview-exercise'),'Exercise list remains available before the report');
        const order=await page.$eval('.wpb-cycle-block-preview',node=>{
            const workouts=node.querySelector('.wpb-cycle-preview-workouts'),report=node.querySelector('.wpb-strength-dose-review');
            return !!(workouts.compareDocumentPosition(report)&Node.DOCUMENT_POSITION_FOLLOWING);
        });
        assert.ok(order,'The report follows, rather than displaces, primary workout content');
        await page.click('.wpb-strength-dose-review>summary');
        const copy=await page.$eval('.wpb-strength-dose-review',node=>node.innerText);
        for(const expected of ['Upper back','3.5 of 6 requested','feasible 4','Shared muscle recovery limit',
            'Side delts','4.5 of 5 requested','No safe addition fits this block','Original generated first-week estimate'])
            assert.ok(copy.includes(expected),`${width}px original-dose proof missing: ${expected}`);
        await page.evaluate(()=>{document.documentElement.style.fontSize='32px';});
        const bounds=await page.$eval('.wpb-strength-dose-review',node=>{
            const summary=node.querySelector('summary'),box=node.getBoundingClientRect(),panel=node.closest('.wpb-cycle-block-preview');
            return {width:node.scrollWidth,client:node.clientWidth,summary:summary.getBoundingClientRect().height,
                left:box.left,right:box.right,panelScroll:panel.scrollWidth,panelClient:panel.clientWidth};
        });
        assert.ok(bounds.width<=bounds.client+1&&bounds.panelScroll<=bounds.panelClient+1&&bounds.left>=0&&bounds.right<=width+1,
            `No 200% font overflow at ${width}px: ${JSON.stringify(bounds)}`);
        assert.ok(bounds.summary>=44,'The explanation summary has a touch-sized target');
        const met={...limited,status:'met',requested:{lats:4},target:{lats:4},actual:{lats:4},unresolved:[],limits:[]};
        await page.evaluate(program=>window.renderStrengthDose(program),{...fixture,nextEngine:{...fixture.nextEngine,strengthEntryDose:met}});
        await page.waitForFunction(()=>document.querySelector('.wpb-strength-dose-review')?.dataset.strengthDoseStatus==='met');
        assert.match(await page.$eval('.wpb-strength-dose-review summary',node=>node.textContent),/planned goal covered/);
        const withoutReport={...fixture,nextEngine:{...fixture.nextEngine,strengthEntryDose:undefined}};
        await page.evaluate(program=>window.renderStrengthDose(program),withoutReport);
        await page.waitForFunction(()=>!document.querySelector('.wpb-strength-dose-review'),
            {timeout:5000});
        assert.ok(await page.$('.wpb-cycle-preview-exercise'),
            'An imported/legacy block without a retained-dose report still shows its real workouts');
        assert.deepEqual(errors,[],`No browser exceptions at ${width}px`);
        await page.close();
    }
    assert.equal(JSON.stringify(fixture),original,'Viewing a Strength report never modifies a saved cycle or set target');
    console.log('PASS B1021 cycle Strength-dose disclosure: read-only actual/requested/feasible values, accurate constraints, phone sizing and missing-report fallback.');
}finally{await browser?.close();await new Promise(done=>server.close(done));}
