import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { EXERCISES, EX_BY_ID, computeCell, exerciseAt, effortLabel, plannedWeek } from '../modules/engine-api.js';

import { generateNextProgramForShell, markUserPrescriptionOverride } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine } from '../modules/next-engine/workout-history-adapter.js';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const fixture = JSON.parse(fs.readFileSync(path.join(root, 'verification/m205-generation-parity-results.json'))).rows[3];
const matrixProgram = generateNextProgramForShell({config:{...fixture.config,weeks:3,deload:true},legacyExercises:EXERCISES,
    seed:fixture.seed,makeId:()=> 'm370-progression'}).program;
matrixProgram.days=[{...matrixProgram.days[0],exercises:matrixProgram.days[0].exercises.slice(0,1)}];
const program = generateNextProgramForShell({ config: fixture.config, legacyExercises: EXERCISES,
    seed: fixture.seed, makeId: () => 'm214-browser' }).program;
const day = program.days[0], id = day.exercises[1], sourceKey = `${day.id}:1`, key = `${day.id}:0`;
program.days = [{ ...day, primaryIndex: -1, exercises: [id] }];
program.overrides = { [key]: program.overrides[sourceKey] };
program.nextWeekPrescriptions = { [key]: program.nextWeekPrescriptions[sourceKey] };
for (const [field, value] of [['sets', 2], ['reps', '10-15'], ['rir', '2-3'], ['rest', 0], ['tech', null]])
    program.overrides[key] = markUserPrescriptionOverride(program.overrides[key], field, value);
program.progStyle = { [key]: 'double' };
program.ss = {};
const importMap = fs.readFileSync(path.join(root, 'index.html'), 'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)?.[0];
assert.ok(importMap);
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import {WorkoutSession,StyleTag} from '/modules/App.js';
const program=${JSON.stringify(program)},day=program.days[0];
if (!localStorage.getItem('m214-seeded')) {
 localStorage.setItem('m214-seeded','1');
 localStorage.setItem('wpb:live',JSON.stringify({schemaVersion:2,programId:program.id,dayId:day.id,weekIndex:1,dayExSig:${JSON.stringify(id)},exIdx:0,elapsedMs:120000,
 data:[{id:${JSON.stringify(id)},slot:0,note:'',sets:[0,1].map(()=>({weight:'100',reps:'10',done:true,auto:false,valueOwner:'user',actualRIR:2,target:{w:'100',reps:'10-15',rir:'2-3'}}))}]}));
}
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day,weekIndex:1,unit:'lb',perf:{},history:[],restAutoStart:false,warmupCard:false,setUnit:()=>{},onExit:()=>{},onFinish:entry=>{entry.id='m214-browser-log';entry.date=Date.now();localStorage.setItem('m214-finished',JSON.stringify(entry));}})));
</script></body></html>`;
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    if (pathname === '/probe.html') { res.setHeader('Content-Type', 'text/html'); res.end(html); return; }
    const file = path.resolve(root, '.' + pathname);
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] ?? 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8769, '127.0.0.1', resolve));
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true,
        args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    const page = await browser.newPage(), errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    // Read the production screen through its router, including the actual last/deload week.
    const alternating={id:'m370-alternating',name:'Alternating authored plan',custom:true,weeks:3,
        config:{unit:'lb',weeks:3,goal:'both',experience:'intermediate',progression:'manual',deload:true,split:'custom',days:1,session:'s60'},
        days:[{id:'upper',label:'Upper — complete program name',primaryIndex:0,exercises:['bb-bench']}],
        pairs:{'upper:0':'db-bench'},overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'}}};
    const oneWeek={...alternating,id:'m370-single',weeks:1,config:{...alternating.config,weeks:1,deload:false}};
    const cases=[...([matrixProgram,alternating].flatMap(p=>[[p,320,'amethyst'],[p,393,'rosewater'],[p,900,'amethyst']])),[oneWeek,393,'rosewater']];
    for(const [p,width,theme] of cases){
        const context=await browser.createBrowserContext(),matrixPage=await context.newPage();
        matrixPage.on('pageerror',error=>errors.push(error.message));
        await matrixPage.setViewport({width,height:852,isMobile:width<600,hasTouch:true});
        await matrixPage.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await matrixPage.evaluateOnNewDocument((saved,theme)=>{if(localStorage.getItem('m214-matrix-seeded'))return;
            localStorage.setItem('wpb:v1',JSON.stringify({v:13,savedAt:1,saved:[saved],history:[],cycles:[],perf:{},drafts:{},tombs:{},pinnedId:saved.id,
                unit:'lb',unitChosen:true,restAutoStart:false,warmupCard:false,seenIntro:999,seenWhatsNew:999,theme,lastBackup:Date.now(),installDismissedAt:Date.now()}));
            localStorage.setItem('m214-matrix-seeded','1');},p,theme);
        await matrixPage.goto('http://127.0.0.1:8769/index.html',{waitUntil:'networkidle0'});
        const state=()=>matrixPage.evaluate(()=>{const d=JSON.parse(localStorage.getItem('wpb:v1'));return {saved:d.saved,history:d.history,cycles:d.cycles,drafts:d.drafts};});
        const before=await state();
        if(p.id===matrixProgram.id){
            await matrixPage.locator('[data-tab="plan"]').click();
            await matrixPage.locator('[data-plan-week-picker] [aria-label="Week 4"]').click();
            await matrixPage.locator('[data-plan-pane="volume"]').click();
            const volumePane=await matrixPage.$eval('.wpb-plan-view',n=>n.textContent);
            assert.ok(volumePane.includes('Week 4 volume')&&volumePane.includes(`${plannedWeek(p,4).sets} working sets`),'Plan Volume must keep the selected deload week and its executable set count');
            assert.ok(await matrixPage.$('[data-plan-week-picker] [aria-label="Week 4"][aria-pressed="true"]'),'Volume keeps its selected week control');
            await matrixPage.locator('[data-plan-week-picker] [aria-label="Week 2"]').click();
            assert.match(await matrixPage.$eval('.wpb-plan-view',n=>n.textContent),/Week 2 volume/,'Volume changes week without returning to Schedule');
            await matrixPage.locator('[data-plan-week-picker] [aria-label="Week 4"]').click();
            await matrixPage.locator('[data-plan-week-picker] [aria-label="Week 4"]').click();
            assert.ok(await matrixPage.$('[data-plan-week-picker] [aria-label="Week 4"][aria-pressed="true"]'),'Re-selecting a volume week retains it');
            await matrixPage.locator('[data-tab="home"]').click();
            await matrixPage.locator('[data-tab="plan"]').click();
            assert.match(await matrixPage.$eval('.wpb-plan-view',n=>n.textContent),/Week 4 volume/,'Tab navigation restores the browsed volume week');
            await matrixPage.locator('[data-tab="home"]').click();
        }
        await matrixPage.locator('.hp-open').click();await matrixPage.locator('button[title="Program details"]').click();
        await matrixPage.evaluate(()=>(()=>{const b=[...document.querySelectorAll('[data-infocard]>button')].find(b=>b.textContent.includes('Lift progression'));if(b.getAttribute('aria-expanded')!=='true')b.click();})());
        await matrixPage.waitForSelector('[data-progression-row]');
        const volumeSummary=await matrixPage.evaluate(()=>[...document.querySelectorAll('[data-infocard]>button')].find(n=>n.textContent.includes('Weekly volume')).textContent);
        assert.ok(volumeSummary.includes(`${plannedWeek(p,1).sets} working sets`),'Volume header counts executable working sets rather than adding muscle credits: '+volumeSummary);

        const compare=async expectedWeeks=>{
            const actual=await matrixPage.$$eval('[data-progression-row] .wpb-progression-cell',nodes=>nodes.map(n=>({week:Number(n.dataset.progressionWeek),id:n.dataset.progressionExercise,
                prescription:n.querySelector('.wpb-progression-prescription')?.textContent,effort:n.querySelector('.wpb-progression-effort')?.textContent})));
            const expected=p.days.flatMap(day=>day.exercises.flatMap((_,slot)=>expectedWeeks.map(week=>{
                const id=exerciseAt(p,day,slot,week),c=computeCell(p,day,id,slot,week);
                return {week,id,prescription:`${c.sets}×${c.reps}`,effort:effortLabel(c.rir)};
            })));
            assert.deepEqual(actual,expected,`Progression uses executable weeks and actual slot identity: ${p.id}/${expectedWeeks}`);
        };
        const total=p.config.weeks+(p.config.deload?1:0);
        await compare(total===1?[1]:[1,2]);
        assert.equal(await matrixPage.$eval('[aria-label="Earlier weeks"]',n=>n.disabled),true);
        for(let first=2;first<=total-1;first++){
            await matrixPage.locator('[aria-label="Later weeks"]').click();await compare([first,first+1]);
        }
        assert.equal(await matrixPage.$eval('[aria-label="Later weeks"]',n=>n.disabled),true,'The real last/deload week is reachable');
        if(p.config.deload)assert.match(await matrixPage.$eval('.wpb-progression',n=>n.textContent),/Deload/);
        if(p.pairs&&total>1){
            await matrixPage.type('[aria-label="Search a lift"]','Dumbbell');
            assert.equal(await matrixPage.$$eval('[data-progression-row]',n=>n.length),1,'Search includes the actual alternating lift');
            await matrixPage.$eval('[aria-label="Search a lift"]',n=>{const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;set.call(n,'');n.dispatchEvent(new Event('input',{bubbles:true}));});
        }
        for(const large of [false,true]){
            if(large)await matrixPage.evaluate(()=>document.documentElement.style.fontSize='32px');
            const geometry=await matrixPage.evaluate(()=>{
                const sheet=document.querySelector('.wpb-program-reference-panel'),close=sheet.querySelector('[aria-label="Close program details"]'),r=close.getBoundingClientRect();
                const toggle=[...sheet.querySelectorAll('[data-infocard]>button')].find(n=>n.textContent.includes('Lift progression'));
                return {overflow:sheet.scrollWidth-sheet.clientWidth,viewportOverflow:document.documentElement.scrollWidth-innerWidth,
                    closeVisible:r.top>=0&&r.bottom<=innerHeight&&close.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)),
                    controls:[...sheet.querySelectorAll('.wpb-progression button')].map(n=>n.getBoundingClientRect().height),
                    controlled:!!document.getElementById(toggle.getAttribute('aria-controls'))};
            });
            assert.ok(geometry.overflow<=1&&geometry.viewportOverflow<=1&&geometry.controls.every(h=>h>=48),`Readable progression at ${width}/${large}: ${JSON.stringify(geometry)}`);
            assert.ok(geometry.closeVisible&&geometry.controlled,'Reference has a reachable Close action and connected disclosures');
            await matrixPage.$eval('.wpb-program-reference-body',n=>n.scrollTop=n.scrollHeight);
            await matrixPage.screenshot({path:`verification/m370-progression-${p.id}-${width}-${large?'large':'normal'}-phone.png`});
        }
        await matrixPage.locator('[aria-label="Close program details"]').click();await matrixPage.waitForSelector('.wpb-program-reference-backdrop',{hidden:true});
        assert.deepEqual(await state(),before,'Inspecting every week/filter leaves saved training and drafts untouched');
        const liftId=p.days[0].exercises[0],liftName=EX_BY_ID[liftId].name;
        const openMethod=async()=>{
            await matrixPage.evaluate(()=>document.documentElement.style.fontSize='');
            await matrixPage.locator(`[aria-label="How ${liftName} progresses"]`).click();
            await matrixPage.waitForSelector('.wpb-progression-choice-panel');
        };
        await openMethod();
        assert.equal(await matrixPage.$eval('[data-program-progression-choice="auto"]',n=>n.getAttribute('aria-pressed')),'true');
        for(const large of [false,true]){
            await matrixPage.evaluate(large=>document.documentElement.style.fontSize=large?'32px':'',large);
            const g=await matrixPage.evaluate(()=>{
                const panel=document.querySelector('.wpb-progression-choice-panel'),close=panel.querySelector('[aria-label="Close progression methods"]'),r=close.getBoundingClientRect();
                return {overflow:panel.scrollWidth-panel.clientWidth,viewport:document.documentElement.scrollWidth-innerWidth,
                    close:r.top>=0&&r.bottom<=innerHeight&&close.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)),
                    heights:[...panel.querySelectorAll('button')].map(n=>n.getBoundingClientRect().height),
                    title:panel.querySelector('.wpb-progression-choice-exercise').textContent};
            });
            assert.ok(g.overflow<=1&&g.viewport<=1&&g.close&&g.heights.every(h=>h>=48)&&g.title===liftName,`Readable method sheet ${width}/${large}: ${JSON.stringify(g)}`);
            await matrixPage.screenshot({path:`verification/m371-method-${p.id}-${width}-${large?'large':'normal'}-phone.png`});
        }
        await matrixPage.keyboard.press('Escape');await matrixPage.waitForSelector('.wpb-progression-choice-backdrop',{hidden:true});
        assert.equal(await matrixPage.evaluate(()=>document.activeElement?.getAttribute('aria-label')),`How ${liftName} progresses`,'Method inspection restores focus to its row');
        assert.deepEqual(await state(),before,'Reading and closing progression methods never changes training');
        if(p.id===matrixProgram.id&&width===393){
            await openMethod();await matrixPage.locator('[data-program-progression-choice="dynamic"]').click();
            await matrixPage.waitForSelector('.wpb-progression-choice-backdrop',{hidden:true});
            assert.deepEqual((await state()).saved,before.saved,'A method remains staged until the program is reviewed and saved');
            await matrixPage.locator('.wpb-program-save').click();await matrixPage.waitForSelector('.wpb-save-review-confirm');
            assert.match(await matrixPage.$eval('.wpb-save-review-summary',n=>n.textContent),/Progression method/,'A method-only edit is labeled accurately');
            await matrixPage.locator('.wpb-save-review-details>summary').click();
            assert.match(await matrixPage.$eval('[aria-labelledby="program-save-title"]',n=>n.textContent),/Dynamic double/i,'Review names the executable method change');
            assert.match(await matrixPage.$eval('.wpb-program-review-columns',n=>n.innerText),/Before[\s\S]*Double progression[\s\S]*After[\s\S]*Dynamic double/i,'Review exposes the actual before/after prescription');
            for(const large of [false,true]){
                await matrixPage.evaluate(large=>document.documentElement.style.fontSize=large?'32px':'',large);
                const g=await matrixPage.evaluate(()=>{
                    const panel=document.querySelector('.wpb-save-review-panel'),close=panel.querySelector('[aria-label="Close change review"]'),r=close.getBoundingClientRect();
                    return {overflow:panel.scrollWidth-panel.clientWidth,close:r.top>=0&&r.bottom<=innerHeight,
                        actions:[...panel.querySelectorAll('button')].every(n=>n.getBoundingClientRect().height>=48),
                        footer:panel.querySelector('.wpb-save-review-footer').getBoundingClientRect().bottom<=innerHeight,
                        body:panel.querySelector('.wpb-save-review-scroll').clientHeight};
                });
                assert.ok(g.overflow<=1&&g.close&&g.actions&&g.footer&&g.body>0,`Usable before/after review at enlarged text: ${JSON.stringify(g)}`);
                await matrixPage.screenshot({path:`verification/m371-save-review-${large?'large':'normal'}-phone.png`});
            }
            await matrixPage.evaluate(()=>document.documentElement.style.fontSize='');
            await matrixPage.locator('.wpb-save-review-confirm').click();
            await matrixPage.waitForFunction(id=>JSON.parse(localStorage.getItem('wpb:v1')).saved[0]?.progStyle?.[id]==='dynamic',{},liftId);
            const committed=(await state()).saved[0];
            assert.equal(computeCell(committed,committed.days[0],liftId,0,1).progressionStyle,'dynamic','Saved editor choice owns the executable cell');
            assert.deepEqual(committed.nextWeekPrescriptions,p.nextWeekPrescriptions,'A choice cannot rewrite the generated weekly schedule');
            await matrixPage.locator('button[title="Program details"]').click();
            await matrixPage.evaluate(()=>(()=>{const b=[...document.querySelectorAll('[data-infocard]>button')].find(b=>b.textContent.includes('Lift progression'));if(b.getAttribute('aria-expanded')!=='true')b.click();})());
            await matrixPage.waitForSelector('[data-progression-method]');
            assert.ok((await matrixPage.$$eval('[data-progression-method]',ns=>ns.map(n=>n.dataset.progressionMethod))).every(style=>style==='dynamic'),'Plan info shows current methods rather than only the original generation choices');
            await matrixPage.locator('[aria-label="Close program details"]').click();await matrixPage.waitForSelector('.wpb-program-reference-backdrop',{hidden:true});
            await matrixPage.locator('.wpb-day-start').click();await matrixPage.waitForSelector('.wpb-next-workout-view');
            await matrixPage.locator('.wpb-preview-start').click();await matrixPage.waitForSelector('.wpb-workout');
            const checkIn=await matrixPage.$('.wpb-backdrop button[data-wpb-system-back]');if(checkIn)await checkIn.click();
            assert.match(await matrixPage.$eval('[aria-label="Change exercise progression"]',n=>n.textContent),/Dynamic double/i,'Workout names the method selected in Program');
            for(const [label,value] of [['weight','100'],['reps','5']]){
                await matrixPage.focus(`[data-testid="set-0-0"] input[aria-label="${label}"]`);await matrixPage.keyboard.down('Control');await matrixPage.keyboard.press('KeyA');await matrixPage.keyboard.up('Control');await matrixPage.keyboard.type(value);await matrixPage.keyboard.press('Tab');
            }
            await matrixPage.locator('[aria-label="Change exercise progression"]').click();await matrixPage.locator('[data-progression-choice="double"]').click();
            assert.deepEqual(await matrixPage.$$eval('[data-testid="set-0-0"] input',ns=>ns.map(n=>n.value)),['100','5'],'A session method change preserves manually entered values');
            await matrixPage.evaluate(()=>window.dispatchEvent(new Event('wpb:system-back')));await matrixPage.waitForSelector('.wpb-live-dock');
            await matrixPage.reload({waitUntil:'networkidle0'});await matrixPage.locator('[aria-label="Resume workout"]').click();await matrixPage.waitForSelector('.wpb-workout');
            assert.match(await matrixPage.$eval('[aria-label="Change exercise progression"]',n=>n.textContent),/Double progression/i,'Generated session choice survives reload and resume');
            await matrixPage.locator('[data-testid="set-0-0"] .wpb-set-complete').click();await matrixPage.locator('button[aria-label="Finish workout"]').click();
            await matrixPage.evaluate(()=>[...document.querySelectorAll('button')].find(n=>n.textContent.trim()==='Save & finish').click());
            await matrixPage.waitForSelector('[aria-label="Update program progression?"]');
            const logged=(await state()).history[0];
            assert.equal(logged.perf[liftId].prescription.progressionStyle,'double','Actual serialized history records the method used in the session');
            assert.equal(logged.perf[liftId].sets[0].w,100);assert.equal(logged.perf[liftId].sets[0].r,5);
            await matrixPage.evaluate(()=>[...document.querySelectorAll('button')].find(n=>n.textContent.trim()==='Update program').click());
            try { await matrixPage.waitForFunction(id=>JSON.parse(localStorage.getItem('wpb:v1')).saved[0]?.progStyle?.[id]==='double',{},liftId); }
            catch(error) { console.error('METHOD_REVIEW_DIAGNOSTIC',await matrixPage.evaluate(()=>{const d=JSON.parse(localStorage.getItem('wpb:v1'));return {styles:d.saved.map(p=>({id:p.id,style:p.progStyle,lifter:p.progStyleLifter})),drafts:Object.keys(d.drafts||{}),history:d.history.map(h=>({changes:h.progressionChanges,origin:h.progressionOrigins,status:h.programUpdateStatus})),dialog:document.querySelector('.wpb-session-dialog')?.textContent,notices:[...document.querySelectorAll('.wpb-notice')].map(n=>n.textContent)};}));throw error; }
            const updated=(await state()).saved[0];assert.equal(computeCell(updated,updated.days[0],liftId,0,1).progressionStyle,'double','Post-workout review changes the executable program');
            await matrixPage.reload({waitUntil:'networkidle0'});assert.deepEqual((await state()).history[0].perf,logged.perf,'Reload retains the completed facts and method snapshot');
        }
        await context.close();
    }
    await page.goto('http://127.0.0.1:8769/probe.html', { waitUntil: 'networkidle0' });
    await page.waitForSelector('.wpb-workout');
    const checkIn = await page.$('.wpb-backdrop button[data-wpb-system-back]');
    if (checkIn) { await checkIn.click(); await page.waitForSelector('.wpb-backdrop', { hidden: true }); }
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets.length), 2);
    const clickText = text => page.$$eval('button', (buttons, label) => {
        const button = buttons.find(b => b.textContent.trim() === label);
        if (!button) throw new Error('Missing button: ' + label);
        button.click();
    }, text);
    await clickText('Finish workout');
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === 'Save & finish'));
    await clickText('Save & finish');
    await page.waitForFunction(() => !!localStorage.getItem('m214-finished'));
    const log = await page.evaluate(() => JSON.parse(localStorage.getItem('m214-finished')));
    assert.equal(log.perf[id].prescription.sets, 2);
    assert.equal(log.perf[id].prescription.reps, '10-15');
    assert.equal(log.perf[id].prescription.progressionStyle, 'double');
    assert.equal(log.perf[id].prescription.tech, null);
    assert.ok(log.perf[id].sets.every(s => s.rirReported === true && s.pt === '10-15'),
        'typed working rows retain target and explicit effort provenance when actually saved');
    const analysis = analyzeShellHistoryForNextEngine(program, [log], EXERCISES);
    assert.equal(analysis.performedSetCount, 2);
    assert.equal(analysis.workouts[0].progression[0].action, 'add_reps');
    await page.reload({ waitUntil: 'networkidle0' });
    assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('m214-finished'))), log);
    assert.deepEqual(errors, []);
    await page.screenshot({ path: path.join(root, 'verification/m214-snapshot-phone.png'), fullPage: true });
    console.log('PASS M214 browser: canonical weeks/deload/volume; accessible method choices at 320/393/900px and 200% text; actual editor save → Preview → Workout method change → resume → logged method snapshot → reviewed program update; immutable history.');
}
finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
