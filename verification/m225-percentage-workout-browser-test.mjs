import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
import { EXERCISES, EX_BY_ID, TEMPLATES, templateConfig, percentagePlanFor, prescribeSets } from '../modules/App.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
const root = path.resolve(new URL('../', import.meta.url).pathname);
const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
const fixtures = Object.fromEntries(['p531','gzclp'].map(templateId => {
    const config = { ...templateConfig(TEMPLATES.find(t => t.id === templateId), equipment), unit: 'lb', noSupersets: true, barbellCap: 3 };
    const p = generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 225, makeId: () => templateId }).program;
    const d = p.days[0], slot = d.primaryIndex, id = d.exercises[slot];
    p.days = [{ ...d, exercises: [id], primaryIndex: 0, t2Index: null }];
    p.overrides = { [`${d.id}:0`]: p.overrides[`${d.id}:${slot}`] };
    p.nextWeekPrescriptions = { [`${d.id}:0`]: p.nextWeekPrescriptions[`${d.id}:${slot}`] };
    p.trainingMax = { [id]: 200 }; p.trainingMaxUnit = 'lb'; p.ss = {};
    return [templateId, p];
}));
const importMap = fs.readFileSync(path.join(root, 'index.html'), 'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import {WorkoutSession,StyleTag,EX_BY_ID,prescribeSets} from '/modules/App.js';
const q=new URL(location.href).searchParams,p=${JSON.stringify(fixtures)}[q.get('template')||'p531'],d=p.days[0],id=d.exercises[0],unit=q.get('unit')||'lb',week=Number(q.get('week')||1);
localStorage.removeItem('wpb:live');localStorage.removeItem('m225-finished');
if(q.has('resume')) {const rows=prescribeSets(p,d,EX_BY_ID[id],0,week,unit,null,null,{},[],false).filter(r=>!r.sub).map((r,i)=>({...r,done:true,actualRIR:2,...(i===0?{recoveryLimited:true}:{})}));localStorage.setItem('wpb:live',JSON.stringify({schemaVersion:2,programId:p.id,dayId:d.id,weekIndex:week,dayExSig:id,exIdx:0,elapsedMs:120000,data:[{id,slot:0,note:'',sets:rows}]}));}
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program:p,day:d,weekIndex:week,unit,perf:{},history:[],restAutoStart:false,warmupCard:false,setUnit:()=>{},onExit:()=>{},onFinish:entry=>localStorage.setItem('m225-finished',JSON.stringify(entry))})));
</script></body></html>`;
const mime = { '.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml' };
const server = http.createServer((req,res) => {
    const pathname = new URL(req.url,'http://localhost').pathname;
    if (pathname === '/probe.html') { res.setHeader('Content-Type','text/html'); res.end(html); return; }
    const file = path.resolve(root,'.'+pathname);
    if (!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()) { res.writeHead(404);res.end();return; }
    res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8777,'127.0.0.1',resolve));
let browser;
try {
    browser = await puppeteer.launch({ executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage'] });
    const page = await browser.newPage(), errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    const open = async query => {
        await page.goto('http://127.0.0.1:8777/probe.html?'+query,{waitUntil:'networkidle0'});
        await page.waitForSelector('[data-testid="set-0-0"]');
        const check=await page.$('.wpb-backdrop button[data-wpb-system-back]');if(check)await check.click();
    };
    for (const [template,unit,week] of [['p531','lb',1],['p531','kg',1],['p531','lb',5],['gzclp','lb',1]]) {
        await open(`template=${template}&unit=${unit}&week=${week}`);
        const p=fixtures[template],d=p.days[0],ex=EX_BY_ID[d.exercises[0]],preview=percentagePlanFor(p,d,ex,0,week,unit);
        const weights=await page.$$eval('input[aria-label="weight"]',ns=>ns.map(n=>n.value));
        const reps=await page.$$eval('input[aria-label="reps"]',ns=>ns.map(n=>n.value));
        const runtime=prescribeSets(p,d,ex,0,week,unit,null,null,{},[],true).filter(r=>!r.sub);
        assert.deepEqual(weights,runtime.map(s=>s.weight));assert.deepEqual(reps,runtime.map(s=>s.reps));
        assert.deepEqual(weights.slice(-preview.sets.length),preview.sets.map(s=>String(s.weight)));
        assert.deepEqual(reps.slice(-preview.sets.length),preview.sets.map(s=>s.reps.replace('+','')));
        if(template==='p531'&&unit==='lb'&&week===1) assert.ok(weights.includes('170'),'literal heaviest 85% target survives adaptation');
    }
    await open('template=gzclp&resume');
    const clickText=label=>page.$$eval('button',(buttons,text)=>{const b=buttons.find(b=>b.textContent.trim()===text);if(!b)throw Error('Missing button '+text);b.click();},label);
    await clickText('Finish workout');
    await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='Save & finish'));
    await clickText('Save & finish');await page.waitForFunction(()=>!!localStorage.getItem('m225-finished'));
    const log=await page.evaluate(()=>JSON.parse(localStorage.getItem('m225-finished')));
    const id=fixtures.gzclp.days[0].exercises[0];
    assert.equal(log.perf[id].sets[0].recoveryLimited,true);assert.equal(log.perf[id].prescription.protocol.scheme,'gzclp');
    assert.ok(log.perf[id].prescription.setTargets.length>0);assert.deepEqual(errors,[]);
    await page.screenshot({path:path.join(root,'verification/m225-percentage-phone.png')});
    console.log('PASS M225 mobile browser: literal percentage peak, lb/kg rows, deload, tiered targets, protected resume and actual finish serialization.');
} finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}

