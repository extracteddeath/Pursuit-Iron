import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
import { programToText } from '../modules/program-share-sheet.js';
import { EXERCISES, EX_BY_ID, TEMPLATES, templateConfig, percentagePlanFor, prescribeSets, withTrainingMax, roundTo, loadStep } from '../modules/engine-api.js';

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
async function verifyTrainingMaxEditing(browser) {
 for(const theme of ['rose','rosewater'])for(const unit of ['lb','kg']) {
    const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
    const p={...fixtures.p531,name:'M225 percentage editor',trainingMax:{...fixtures.p531.trainingMax,'legacy-max':151.25}},id=p.days[0].exercises[0],ex=EX_BY_ID[id];
    const entered=unit==='lb'?'212.5':'97.5';
    const live={schemaVersion:2,programId:p.id,dayId:p.days[0].id,weekIndex:1,dayExSig:id,exIdx:0,elapsedMs:120000,
        runPaused:true,restPaused:true,restRemain:47,restMax:90,data:[{id,slot:0,sets:[{weight:'187.5',reps:'4',done:false,
            auto:false,valueOwner:'user',target:{w:'185',reps:'5',rir:'2'}}]}]};
    const history=[{id:'m225-existing-log',date:Date.now()-86400000,programId:p.id,programName:p.name,dayId:p.days[0].id,
        dayLabel:p.days[0].label,week:1,unit:'lb',perf:{[id]:{weight:190,reps:5,sets:[{w:190,r:5,rir:2,tr:2}]}}}];
    page.on('pageerror',e=>errors.push(e.message));
    try {
        await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument((p,theme,unit,live,history)=>{
            if(localStorage.getItem('m225-max-seeded'))return;
            localStorage.setItem('m225-max-seeded','1');
            localStorage.setItem('wpb:v1',JSON.stringify({v:13,savedAt:1,saved:[p],cycles:[],history,perf:{},drafts:{},tombs:{},
                seenIntro:999,seenWhatsNew:999,pinnedId:p.id,theme,unit,unitChosen:true,experience:'intermediate',warmupCard:false,restAutoStart:false,
                bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()}));
            localStorage.setItem('wpb:live',JSON.stringify(live));
        },p,theme,unit,live,history);
        await page.goto('http://127.0.0.1:8777/index.html',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');
        const state=()=>page.evaluate(()=>{const d=JSON.parse(localStorage.getItem('wpb:v1'));return {saved:d.saved,history:d.history,cycles:d.cycles,drafts:d.drafts,
            live:JSON.parse(localStorage.getItem('wpb:live'))?.data};});
        const initial=await state();
        const openProgram=async()=>{await page.$eval('[aria-label="Open M225 percentage editor"]',b=>b.click());await page.waitForSelector('.wpb-program');};
        await openProgram();
        const open=async()=>{await page.$eval('[aria-label="Edit training maxes"]',b=>{b.scrollIntoView({block:'center'});b.focus();b.click();});await page.waitForSelector('.wpb-training-max-sheet',{visible:true});};
        const input=()=>page.$('.wpb-training-max-controls input');
        const type=async(handle,value)=>{await handle.click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');if(value)await page.keyboard.type(value);else await page.keyboard.press('Backspace');};
        const click=label=>page.$$eval('.wpb-training-max-sheet button',(buttons,label)=>buttons.find(b=>b.textContent.trim()===label).click(),label);
        const applyDisabled=()=>page.$$eval('.wpb-training-max-footer button',buttons=>buttons.find(b=>b.textContent.trim()==='Apply maxes').disabled);
        const close=async()=>{await click('Cancel');await page.waitForSelector('.wpb-training-max-sheet',{hidden:true});};
        await open();await type(await input(),entered);
        assert.equal(await page.$eval('.wpb-training-max-controls input',el=>el.value),entered,'Training-max typing preserves the decimal point and intended value');
        await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
        assert.deepEqual(await state(),initial,'Typing a max changes neither the program draft nor saved/live/history data');
        const expected=withTrainingMax(p,id,Number(entered),unit);
        const preview=percentagePlanFor(expected,expected.days[0],ex,0,1,unit);
        for(const set of preview.sets)assert.ok((await page.$eval('.wpb-training-max-preview',el=>el.textContent)).includes(`${set.weight} ${unit} × ${set.reps}`),'Staged preview uses canonical percentage targets');
        for(const bad of ['12..5','-10','1e309']) {
            await type(await input(),bad);assert.equal(await page.$eval('.wpb-training-max-controls input',el=>el.getAttribute('aria-invalid')),'true');
            assert.ok(await applyDisabled(),'Invalid text cannot be applied');
        }
        await type(await input(),'');assert.ok(!await applyDisabled(),'Clearing a saved max is an explicit staged change');
        assert.equal(await page.$('.wpb-training-max-preview'),null,'A cleared max has no misleading load preview');
        await type(await input(),entered);
        if(unit==='lb')for(const width of [320,393,834,1280])for(const text of [16,32]) {
            await page.setViewport({width,height:852,isMobile:true,hasTouch:true});
            await page.evaluate(text=>document.documentElement.style.fontSize=text+'px',text);
            const layout=await page.$eval('.wpb-training-max-sheet',el=>{
                el.querySelector('.wpb-training-max-body').scrollTop=100000;
                const close=el.querySelector('.wpb-sheet-close'),r=close.getBoundingClientRect();
                return {width:el.clientWidth,scroll:el.scrollWidth,height:el.getBoundingClientRect().height,
                    title:parseFloat(getComputedStyle(el.querySelector('h2')).fontSize),font:parseFloat(getComputedStyle(el.querySelector('input')).fontSize),
                    inputs:[...el.querySelectorAll('input')].map(n=>({label:n.getAttribute('aria-label'),height:n.getBoundingClientRect().height})),
                    actions:[...el.querySelectorAll('.wpb-training-max-footer button')].map(n=>{const b=n.getBoundingClientRect();return {top:b.top,bottom:b.bottom,height:b.height,clear:n.contains(document.elementFromPoint(b.left+b.width/2,b.top+b.height/2))};}),
                    close:close.contains(document.elementFromPoint(r.left+r.width/2,r.top+r.height/2))};
            });
            assert.ok(layout.scroll<=layout.width+1&&layout.height<=852&&layout.title>=text*1.125-.1&&layout.font>=text*.9375-.1&&layout.close
                &&layout.inputs.every(i=>i.label&&i.height>=48)&&layout.actions.every(a=>a.top>=0&&a.bottom<=852&&a.height>=44&&a.clear),
                'Training-max editor fits enlarged text and keeps its actions visible: '+JSON.stringify({theme,width,text,layout}));
            if(width===320)await page.screenshot({path:path.join(root,`verification/m225-training-max-${theme}-${text}-phone.png`)});
        }
        await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});await page.evaluate(()=>document.documentElement.style.fontSize='16px');
        const cdp=await page.createCDPSession();
        await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'},{name:'forced-colors',value:'active'}]});
        await page.focus('.wpb-training-max-input');await page.keyboard.press('Tab');
        const forced=await page.$eval('.wpb-training-max-sheet',el=>({active:matchMedia('(forced-colors:active)').matches,
            border:parseFloat(getComputedStyle(el.querySelector('input')).borderTopWidth),outline:parseFloat(getComputedStyle(document.activeElement).outlineWidth),animation:getComputedStyle(el).animationName}));
        assert.ok(forced.active&&forced.border>=1&&forced.outline>=2&&forced.animation==='none','Forced colors and reduced-motion controls remain usable');
        await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});await cdp.detach();
        await close();assert.deepEqual(await state(),initial,'Cancel discards the complete max draft');
        await open();await type(await input(),entered);await page.keyboard.press('Escape');await page.waitForSelector('.wpb-training-max-sheet',{hidden:true});
        await page.waitForFunction(()=>document.activeElement?.getAttribute('aria-label')==='Edit training maxes');
        assert.deepEqual(await state(),initial,'Escape discards changes and restores the entry action');
        await open();await click('Use 1RM');
        await type(await page.$('.wpb-training-max-conversion input'),'12..5');
        assert.ok(await page.$$eval('.wpb-training-max-conversion button',buttons=>buttons.find(b=>b.textContent==='Use this max').disabled));
        const rm=unit==='lb'?300.5:121.5,converted=roundTo(rm*.9,loadStep(ex,unit));
        await type(await page.$('.wpb-training-max-conversion input'),String(rm));
        assert.ok((await page.$eval('.wpb-training-max-conversion',el=>el.textContent)).includes(`training max ${converted} ${unit}`));
        await page.keyboard.press('Escape');await page.waitForSelector('.wpb-training-max-conversion',{hidden:true});
        assert.ok(await page.$('.wpb-training-max-sheet'),'Escape closes conversion before the parent editor');
        assert.equal(await page.evaluate(()=>document.activeElement?.dataset?.['1rmFor']),id,'Conversion returns focus to its lift');
        await click('Use 1RM');await type(await page.$('.wpb-training-max-conversion input'),String(rm));
        assert.ok(await applyDisabled(),'Finish or cancel conversion before applying maxes');
        await page.keyboard.press('Enter');await page.waitForSelector('.wpb-training-max-conversion',{hidden:true});
        assert.equal(await page.$eval('.wpb-training-max-controls input',el=>el.value),String(converted));
        assert.deepEqual(await state(),initial,'Using a 1RM only stages the rounded max');
        await close();assert.deepEqual(await state(),initial,'Outer Cancel also discards a staged 1RM conversion');
        await open();await type(await input(),entered);await click('Apply maxes');await page.waitForSelector('.wpb-training-max-sheet',{hidden:true});
        await page.waitForFunction((id,value)=>JSON.parse(localStorage.getItem('wpb:v1')).drafts?.p531?.trainingMax?.[id]===value,{},id,expected.trainingMax[id]);
        const staged=await state();assert.deepEqual(staged.saved,initial.saved,'Apply updates the program draft before explicit reviewed Save');
        assert.equal(staged.drafts.p531.trainingMax['legacy-max'],151.25,'Untouched stored fields keep their precision');
        await page.$eval('[aria-label="Review program changes"]',b=>b.click());await page.waitForSelector('.wpb-save-review-panel').catch(async error=>{
            throw new Error('Training-max save review did not open: '+JSON.stringify({errors,body:await page.evaluate(()=>document.body.innerText.slice(-5000))}),{cause:error});
        });
        assert.ok((await page.$eval('.wpb-save-review-summary',el=>el.textContent)).includes('Training maxes'));
        await page.$eval('.wpb-save-review-details summary',el=>el.click());
        const setting=await page.$$eval('.wpb-program-review-setting',(rows,name)=>rows.find(row=>row.textContent.includes(name+' training max'))?.textContent,ex.name);
        assert.ok(setting?.includes(`200 lb → ${expected.trainingMax[id]} lb`),'Save review shows exact before/after maxes with stored units');
        await page.$eval('.wpb-save-review-confirm',b=>b.click());
        await page.waitForFunction((id,value)=>JSON.parse(localStorage.getItem('wpb:v1')).saved.find(p=>p.id==='p531').trainingMax[id]===value,{},id,expected.trainingMax[id]);
        const after=await state();assert.deepEqual(after.history,initial.history);assert.deepEqual(after.cycles,initial.cycles);assert.deepEqual(after.live,initial.live,'Saved max changes protect every minimized set');
        assert.deepEqual(after.saved[0].overrides,initial.saved[0].overrides);assert.deepEqual(after.saved[0].nextWeekPrescriptions,initial.saved[0].nextWeekPrescriptions);
        assert.equal(after.saved[0].trainingMax['legacy-max'],151.25);
        await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[data-tab="home"]');await page.click('[data-tab="home"]');await openProgram();await open();
        assert.ok(Math.abs(Number(await page.$eval('.wpb-training-max-controls input',el=>el.value))-Number(entered))<1e-9,'Decimal max survives save, unit conversion and reload');
        await close();
        if(theme==='rose') {
            // A new workout consumes the saved max; the earlier minimized one was compared intact above.
            await page.evaluate(()=>localStorage.removeItem('wpb:live'));await page.reload({waitUntil:'networkidle0'});
            await page.waitForSelector('[data-tab="home"]');await page.click('[data-tab="home"]');await page.waitForSelector('.wpb-home .wpb-primary-action');
            await page.$eval('.wpb-home .wpb-primary-action',b=>b.click());await page.waitForSelector('.wpb-workout');
            await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live'))?.data?.length);
            const session=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
            const targets=prescribeSets(after.saved[0],after.saved[0].days[0],ex,0,session.weekIndex,unit,null,null,{},after.history,true).filter(r=>!r.sub);
            assert.deepEqual(await page.$$eval('input[aria-label="weight"]',ns=>ns.map(n=>n.value)),targets.map(r=>r.weight),'Actual new workout loads use the saved decimal max');
        }
        assert.deepEqual(errors,[]);
        console.log('PASS M225 training-max case '+JSON.stringify({theme,unit,entered,stored:after.saved[0].trainingMax[id]}));
    } finally {await context.close();}
 }
 console.log('PASS M225 training maxes: decimal/lb/kg editing, staged Apply/Cancel/Escape, 1RM conversion, canonical previews, exact save review, reload, enlarged controls and protected history/live sets.');
}
const importMap = fs.readFileSync(path.join(root, 'index.html'), 'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import { WorkoutSession, StyleTag } from '/modules/App.js';
import { EX_BY_ID, prescribeSets } from '/modules/engine-api.js';

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
    await verifyTrainingMaxEditing(browser);
    if(!process.argv.includes('--maxes-only')) {
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
        const shared=programToText(p,'rir',unit,week);
        assert.ok(shared.includes('Planned loads: '+preview.sets.map(set=>`${set.weight} ${unit} × ${set.reps}`).join(' · ')),
            'Shared percentage loads, rep-out sets, units and deload match the canonical selected week');
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
    }
} finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}

