import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const importMap = fs.readFileSync(path.join(root, 'index.html'), 'utf8')
    .match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture = [
"import React from '/vendor/react.js';",
"import {createRoot} from '/vendor/react-dom-client.js';",
"import {WeeklyScheduleSheet} from '/modules/program-schedule-sheet.js';",
"function Demo(){",
"const [open,setOpen]=React.useState(false);",
"const [schedule,setSchedule]=React.useState({1:'upper',2:'retired',4:'lower'});",
"const program={days:[{id:'upper',label:'Upper A'},{id:'lower',label:'Lower B'}],schedule};",
"const cycle=weekday=>setSchedule(prev=>({...prev,[weekday]:prev[weekday]==='upper'?'lower':prev[weekday]==='lower'?null:'upper'}));",
"return React.createElement('main',{className:'wpb'},",
"React.createElement('button',{id:'open',onClick:()=>setOpen(true)},'Edit weekly schedule'),",
"open&&React.createElement(WeeklyScheduleSheet,{program,onCycle:cycle,onClear:()=>setSchedule({}),onClose:()=>setOpen(false)}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html = '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'
    + importMap + '</head><body><div id="root"></div><script type="module">' + fixture + '</script></body></html>';
const mime = {'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server = http.createServer((req,res)=>{
    const name = new URL(req.url,'http://localhost').pathname;
    if(name==='/program-schedule-test.html'){
        res.setHeader('Content-Type','text/html');res.end(html);return;
    }
    const file = path.resolve(root,'.'+name);
    if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
        res.writeHead(404);res.end();return;
    }
    res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
    res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try {
    browser = await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
        headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page = await browser.newPage(), errors = [];
    page.on('pageerror',error=>errors.push(error.message));
    await page.setViewport({width:393,height:740,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.goto('http://127.0.0.1:'+server.address().port+'/program-schedule-test.html',{waitUntil:'networkidle0'});
    await page.click('#open');
    await page.waitForSelector('[data-testid="weekly-schedule-sheet"]');
    const summary = ()=>page.$eval('.wpb-program-schedule-caption [role="status"]',el=>el.textContent);
    assert.match(await summary(),/2 training days, 5 rest/,'orphaned assignment is not counted');
    const rows = await page.$$eval('.wpb-program-schedule-row',items=>items.map(el=>({
        label:el.getAttribute('aria-label'),assigned:el.getAttribute('data-assigned')
    })));
    assert.equal(rows.length,7);
    assert.match(rows[0].label,/Mon.*Upper A.*Change assignment/);
    assert.match(rows[1].label,/Tue.*Rest day.*Change assignment/);
    assert.equal(rows[1].assigned,'false');
    await page.evaluate(()=>document.querySelectorAll('.wpb-program-schedule-row')[1].click());
    await page.waitForFunction(()=>document.querySelector('.wpb-program-schedule-caption [role="status"]')?.textContent.includes('3 training days'));
    assert.match(await page.$eval('.wpb-program-schedule-row:nth-of-type(2)',el=>el.getAttribute('aria-label')),/Upper A/);
    await page.click('[aria-label="Clear all scheduled workouts"]');
    await page.waitForFunction(()=>document.querySelector('.wpb-program-schedule-caption [role="status"]')?.textContent.includes('0 training days'));
    assert.equal(await page.$('[aria-label="Clear all scheduled workouts"]'),null);
    assert.equal(await page.$eval('.wpb-program-schedule-close', el => el === document.activeElement), true,
        'clearing focuses a persistent action before removing the Clear button');
    await page.keyboard.press('Escape');
    await page.waitForFunction(()=>!document.querySelector('[data-testid="weekly-schedule-sheet"]'));
    assert.equal(await page.$eval('#open',el=>el===document.activeElement),true,'closing restores launcher focus');
    await page.setViewport({width:320,height:550,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.evaluate(()=>document.documentElement.style.fontSize='32px');
    await page.click('#open');
    const geometry = await page.$eval('[data-testid="weekly-schedule-sheet"] .wpb-program-schedule-sheet',el=>{
        const r=el.getBoundingClientRect();
        return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,height:innerHeight};
    });
    assert.ok(geometry.left>=-1&&geometry.right<=321&&geometry.top>=-1
        &&geometry.bottom<=geometry.height+1,'320px/200% geometry stays in viewport '+JSON.stringify(geometry));
    assert.deepEqual(errors,[],'no runtime exceptions');
    console.log('PASS B995 browser: valid schedule counts, day assignment announcements, live updates, clear, Escape, 320px/200% layout.');
} finally {
    await browser?.close();
    await new Promise(resolve=>server.close(resolve));
}
