import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = new URL('../', import.meta.url).pathname;
const importMap = fs.readFileSync(path.join(root, 'index.html'), 'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)?.[0];
assert.ok(importMap, 'browser probes must use the production import map');
const fixture = { id: 'm201-browser-custom', name: 'Custom workout', custom: true,
    config: { goal: 'both', experience: 'intermediate', progression: 'auto', weeks: 10, unit: 'lb', deload: true },
    overrides: { 'd:0': { sets: 5, reps: '8-12', rir: '2' } },
    days: [{ id: 'd', label: 'Upper', primaryIndex: -1, exercises: ['inc-curl'] }] };
const template = `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js'; import {createRoot} from '/vendor/react-dom-client.js';
import {WorkoutSession, StyleTag, EX_BY_ID, AttachmentGlyph, ExerciseAnimation} from '/modules/App.js';
const params=new URL(location.href).searchParams, program=${JSON.stringify(fixture)}, week=Number(params.get('week')||4), exercise=params.get('exercise')||'inc-curl';
program.days[0].exercises=[exercise]; program.id+='-'+exercise; if(exercise!=='inc-curl')program.overrides['d:0'].reps='12-20';
const history=[{id:'previous',programId:program.id,dayId:'d',date:1,unit:'lb',perf:{[exercise]:{weight:30,reps:9,sets:[{w:30,r:10,rir:2},{w:30,r:8,rir:2},{w:30,r:6,rir:2},{w:25,r:11,rir:2}]}}}];
if(!localStorage.getItem('m201-seeded') && exercise==='inc-curl' && week===4){
localStorage.setItem('m201-seeded','1');localStorage.setItem('wpb:live',JSON.stringify({schemaVersion:2,programId:program.id,dayId:'d',weekIndex:4,dayExSig:'inc-curl',exIdx:0,elapsedMs:120000,data:[{id:'inc-curl',slot:0,note:'',sets:[
{weight:'30',reps:'11',done:true,auto:true,target:{w:'30',reps:'8-12'}},
{weight:'25',reps:'12',done:false,auto:true,target:{w:'25',reps:'8-12'}},
{weight:'25',reps:'12',done:false,auto:true,target:{w:'25',reps:'8-12'}},
{weight:'25',reps:'14',done:false,auto:true,target:{w:'25',reps:'12-20'}},
{weight:'25',reps:'13',done:false,auto:true,added:true,target:{w:'25',reps:'8-12'}}]}]}));}
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day:program.days[0],weekIndex:week,unit:'lb',perf:{},history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{},setUnit:()=>{}})));
window.__m201Ready=true;
</script></body></html>`;
const variants = ['seated-db-lat-raise','single-db-lat-raise','chest-supported-lat-raise','seated-cable-lat-raise','cuff-cable-lat-raise','seated-rear-fly','single-cable-rear-fly','chest-supported-rear-fly'];
const gallery = `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/app.css"><style>html,body,#root{height:auto!important;overflow:visible!important;}</style>${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import {EX_BY_ID,AttachmentGlyph,ExerciseAnimation} from '/modules/App.js';
createRoot(document.getElementById('root')).render(React.createElement('div',{style:{padding:16,display:'grid',gap:16}},${JSON.stringify(variants)}.map(id=>React.createElement('section',{key:id,'data-variant':id,style:{padding:16,border:'1px solid #604077',borderRadius:16}},React.createElement('h3',{},EX_BY_ID[id].name),React.createElement(AttachmentGlyph,{ex:EX_BY_ID[id]}),React.createElement(ExerciseAnimation,{ex:EX_BY_ID[id]})))));
</script></body></html>`;
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    if (pathname === '/probe.html' || pathname === '/variants.html') { res.setHeader('Content-Type','text/html');res.end(pathname === '/probe.html' ? template : gallery);return; }
    const file = path.resolve(root, '.' + pathname);
    if (!file.startsWith(root) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8768, '127.0.0.1', resolve));
let browser;
try {
    browser = await puppeteer.launch({executablePath:process.env.CHROME_BIN || '/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page = await browser.newPage();
    await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    const errors=[];page.on('pageerror',e=>{errors.push(e.message);console.error('Browser page error:',e.message);});
    const open = async query => {
        await page.goto('http://127.0.0.1:8768/probe.html'+query,{waitUntil:'networkidle0'});
        await page.waitForSelector('input[aria-label="reps"]');
        const checkIn = await page.$('.wpb-backdrop button[data-wpb-system-back]');
        if (checkIn) {
            await checkIn.click();
            await page.waitForFunction(()=>!!JSON.parse(localStorage.getItem('wpb:live')).readiness);
            await page.waitForSelector('.wpb-backdrop',{hidden:true});
        }
    };
    const rows = () => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets);
    await open('');
    let restored=await rows();
    assert.deepEqual(restored.map(s=>s.reps),['11','12','12','12','12']);
    assert.deepEqual(restored.map(s=>s.weight),['30','25','25','25','25']);
    assert.equal(restored[0].done,true);
    assert.equal(await page.evaluate(()=>document.body.textContent.includes('toward 12 reps')),true);
    const repFieldsFit = () => page.$$eval('input[aria-label="reps"]',nodes=>nodes.every(input=>{
        const style=getComputedStyle(input),canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');
        ctx.font=style.font;
        return input.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight)>=ctx.measureText(input.value).width;
    }));
    assert.equal(await repFieldsFit(),true,'all rep digits, including the added set, fit at 390px');
    await page.screenshot({path:path.join(root,'verification/m201-reps-phone.png'),fullPage:true});
    await page.setViewport({width:360,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.waitForFunction(()=>window.innerWidth===360);
    assert.equal(await repFieldsFit(),true,'all rep digits fit in the compact 360px layout');
    await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    // A manually typed number remains user-owned even outside the planned range, after deletion,
    // further edits, and an actual browser reload. The repair cannot corrupt a workout log.
    const reps=await page.$$('input[aria-label="reps"]');
    await reps[1].click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');await page.keyboard.type('14');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].reps==='14');
    assert.equal((await rows())[1].auto,false);
    await reps[2].click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');await page.keyboard.press('Backspace');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[2].reps==='');
    await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('input[aria-label="reps"]');
    restored=await rows();assert.equal(restored[1].reps,'14');assert.equal(restored[2].reps,'');assert.equal(restored[0].reps,'11');
    await open('?week=8');
    assert.equal(await page.evaluate(()=>/lengthened partial/i.test(document.body.textContent)),true,'markerless custom technique must render in the workout');
    assert.equal(await page.evaluate(()=>document.body.textContent.includes('+ partials')),true,'the final working set must visibly carry the intensifier tag');
    await open('?week=8&exercise=lat-raise');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets.some(s=>s.sub&&s.kind==='myo'));
    assert.equal((await rows()).filter(s=>s.sub&&s.kind==='myo'&&s.prescribed).length,3);
    await page.screenshot({path:path.join(root,'verification/m201-intensifier-phone.png'),fullPage:true});
    await page.goto('http://127.0.0.1:8768/variants.html',{waitUntil:'networkidle0'});
    await page.waitForSelector('[data-variant-visual="lateral-seated-db"]');
    assert.equal(await page.$$eval('[data-variant]',nodes=>nodes.length),8);
    assert.equal(await page.$$eval('[data-variant] svg[data-variant-visual]',nodes=>nodes.length),8);
    assert.equal(await page.$$eval('[data-variant] button[aria-label="Pause movement preview"]',nodes=>nodes.length),8);
    await page.screenshot({path:path.join(root,'verification/m201-variants-phone.png'),fullPage:true});
    for (const id of variants) {
        const preview=await page.$('[data-variant="'+id+'"]');
        await preview.screenshot({path:path.join(root,'verification/m201-variant-'+id+'-phone.png')});
    }
    assert.deepEqual(errors,[],'workout restore, typing, techniques, and all variant previews must render without browser errors');
    console.log('PASS M201 mobile browser: restored 14/13 auto reps -> 12, manual 14/blank and completed sets preserved through reload, custom partials/myo visible, eight variant previews.');
}
finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
