// Build 882 — the workout screen gives ONE consistent reason for a target, and the evidence is one tap away.
// A real session is logged through the UI and repeated immediately, which is precisely the state in
// which v661's fatigue override used to relabel the lift "e1RM autoregulation · temporary" over loads
// that were really dynamic double progression, while Coach claimed "Today's target is already reduced".
import assert from 'node:assert/strict';
import fs from 'node:fs'; import http from 'node:http'; import path from 'node:path'; import puppeteer from 'puppeteer-core';
import { generateNextProgramForShell, markUserPrescriptionOverride } from '../modules/next-engine/app-shell-adapter.js';
import { EXERCISES, EX_BY_ID, computeCell } from '../modules/engine-api.js';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const EQ=['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
const program = generateNextProgramForShell({config:{name:'Demo',unit:'lb',goal:'hypertrophy',experience:'intermediate',split:'upper_lower',days:4,session:'s60',weeks:6,progression:'auto',deload:true,equipment:EQ,focus:{},reduce:[],barbellCap:3,noBodyweight:false,noSupersets:false},legacyExercises:EXERCISES,seed:3,makeId:()=> 'demo'}).program;
const initial = { v: 13, savedAt: 1, saved: [program], cycles: [], history: [], perf: {}, drafts: {}, tombs: {}, seenIntro: 999, seenWhatsNew: 999, pinnedId: program.id, restAutoStart: false, warmupCard: false, unit: 'lb', unitChosen: true, experience: 'intermediate', bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.png': 'image/png' };
const server = http.createServer((req, res) => { const file = path.resolve(root, '.' + (new URL(req.url, 'http://x').pathname.replace(/^\/$/, '/index.html'))); if (!fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; } res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file)); });
await new Promise(r => server.listen(0, '127.0.0.1', r)); const url = `http://127.0.0.1:${server.address().port}/`;
let browser;
try {
browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
const page = await browser.newPage(); const errors = []; page.on('pageerror', e => errors.push(e.message));
await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
await page.evaluateOnNewDocument(s => { if (!localStorage.getItem('seed')) { localStorage.setItem('seed', '1'); localStorage.setItem('wpb:v1', JSON.stringify(s)); } }, initial);
await page.goto(url, { waitUntil: 'networkidle0' });
const start = await page.waitForSelector('xpath/.//button[contains(., "Start")]'); await start.click();
await page.waitForSelector('.wpb-workout');
const strong = await page.waitForSelector('xpath/.//button[contains(., "Feeling strong")]'); await strong.click();
await new Promise(r => setTimeout(r, 500));
// Session 1, logged through the real controls.
for (let ex = 0; ex < 6; ex++) {
    for (let guard = 0; guard < 8; guard++) {
        for (const inp of await page.$$('.wpb-workout input[aria-label="weight"]'))
            if (!(await inp.evaluate(el => el.value))) { await inp.click({ clickCount: 3 }); await inp.type('100'); }
        const done = await page.$('.wpb-workout button[aria-label="Mark set done"]');
        if (!done) break;
        await done.evaluate(el => el.scrollIntoView({ block: 'center' })); await done.click(); await new Promise(r => setTimeout(r, 100));
    }
    const next = await page.$('.wpb-workout button[aria-label="Next exercise"]');
    if (next && ex < 5) { await next.click(); await new Promise(r => setTimeout(r, 150)); }
}
(await page.waitForSelector('xpath/.//button[contains(., "All sets done")]')).click();
(await page.waitForSelector('xpath/.//div[contains(@class,"wpb-backdrop")]//button[normalize-space(.)="Save & finish"]')).click();
await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.length === 1);
// Repeat it at once: the chest is now "unrecovered", which is exactly when v661's fatigue override fires.
await page.click('[data-tab="progress"]');
const card = await page.waitForSelector('[data-history-id]'); await card.evaluate(el => (el.querySelector('button') || el).click());
(await page.waitForSelector('button[aria-label="Repeat this workout"]')).click();
await page.waitForSelector('.wpb-workout');
const strong2 = await page.waitForSelector('xpath/.//button[contains(., "Feeling strong")]'); await strong2.click();
await page.waitForSelector('.wpb-progression-label');
await page.waitForFunction(() => /prior sets/.test(document.querySelector('.wpb-workout').textContent));
const label = await page.$eval('.wpb-progression-label', el => el.textContent.trim());
// The reference card is located by its content, not by the B882 button, so a pre-fix control fails on behaviour.
const cardText = await page.evaluate(() => [...document.querySelectorAll('.wpb-workout div')].filter(d => /prior sets/.test(d.textContent) && d.textContent.length < 400).sort((a, b) => a.textContent.length - b.textContent.length).pop()?.textContent || '');
assert.doesNotMatch(label, /temporary/i, 'the method line must not announce an override the load engine does not apply: ' + label);
assert.match(label, /Dynamic double/, 'the method line names the style that set these loads: ' + label);
assert.match(cardText, /Dynamic double progression/, 'the reference card and the method line agree');
const disclosure = await page.waitForSelector('.wpb-load-explanation');
assert.equal(await disclosure.evaluate(el => el.open), false, 'target reason starts compact');
await page.click('.wpb-load-explanation summary');
assert.equal(await disclosure.evaluate(el => el.open), true, 'target reason expands without changing the prescription');
console.log('PASS 1 method line and reference card name the same, load-owning method.');
const coachBtn = await page.$('button[aria-label="Expand Coach guidance"]');
if (coachBtn) { await coachBtn.click(); await new Promise(r => setTimeout(r, 300)); }
const coachText = await page.evaluate(() => document.querySelector('.wpb-workout').textContent);
assert.doesNotMatch(coachText, /already reduced|use the lighter target/i, 'Coach must not claim a reduction recovery did not cause');
assert.match(coachText, /still recovering|Recovery is still low/i, 'Coach still flags the unrecovered muscle');
console.log('PASS 2 Coach flags recovery without claiming a load reduction that did not happen.');
await page.click('[data-why-target]');
const sheet = await page.waitForSelector('xpath/.//div[contains(@class,"wpb-backdrop")][.//*[contains(., "Why this prescription?")]]');
const sheetText = await sheet.evaluate(el => el.textContent);
assert.match(sheetText, /Dynamic double progression/, 'the evidence sheet explains the same method');
assert.match(sheetText, /100 lb/, 'the sheet shows today\'s load');
assert.deepEqual(errors, [], 'no runtime errors');
console.log('PASS 3 "Why" opens the explanation sheet with the same method and load.');

// M361: use the actual saved increment, Repeat flow, logger and explanation surfaces.
const roundedProgram=structuredClone(program);roundedProgram.id='m361-rounded';
const selected=roundedProgram.days.flatMap(day=>day.exercises.map((id,slot)=>({day,id,slot})))
    .find(({day,id,slot})=>EX_BY_ID[id]?.equip.includes('cable')&&computeCell(roundedProgram,day,id,slot,1).role==='hypertrophy_isolation');
assert.ok(selected,'The generated fixture needs a cable isolation exercise');
const {day:roundedDay,id:roundedId,slot:roundedSlot}=selected;
const roundedKey=roundedDay.id+':'+roundedSlot;
for(const [field,value] of [['sets',4],['reps','8-12'],['rir','2'],['progressionStyle','double']])
    roundedProgram.overrides[roundedKey]=markUserPrescriptionOverride(roundedProgram.overrides[roundedKey]||{},field,value);
const roundedEntry={id:'m361-history',programId:roundedProgram.id,dayId:roundedDay.id,weekIndex:1,date:1700000000000,unit:'lb',
    perf:{[roundedId]:{weight:115,reps:8,sets:[100,105,110,115].map((w,i)=>({w,r:[12,10,8,8][i],rir:2,rirReported:true,done:true}))}}};
for(const [unit,step,expected] of [['lb',10,[100,100,110,110]],['kg',5,[45,45,45,50]]]){
    const context=await browser.createBrowserContext(), probe=await context.newPage();
    try{
        await probe.setViewport({width:390,height:844,isMobile:true,hasTouch:true});
        probe.on('pageerror',e=>errors.push(e.message));
        const savedProgram={...roundedProgram,exLoadInc:{[roundedId]:{v:step,unit}}};
        const store={...initial,saved:[savedProgram],pinnedId:savedProgram.id,unit,history:[roundedEntry]};
        await probe.evaluateOnNewDocument(s=>{if(!localStorage.getItem('m361-seeded')){localStorage.setItem('m361-seeded','1');localStorage.setItem('wpb:v1',JSON.stringify(s));}},store);
        await probe.goto(url,{waitUntil:'networkidle0'});
        await probe.click('[data-tab="progress"]');
        const record=await probe.waitForSelector('[data-history-id]');await record.evaluate(el=>(el.querySelector('button')||el).click());
        await probe.click('button[aria-label="Repeat this workout"]');
        await probe.waitForSelector('.wpb-workout');
        await (await probe.waitForSelector('xpath/.//button[contains(., "Feeling strong")]')).click();
        await probe.waitForSelector('.wpb-backdrop',{hidden:true});
        await probe.click(`.wpb-ex-nav-step:nth-child(${roundedSlot+1})`);
        const weights=()=>probe.$$eval(`[data-testid^="set-${roundedSlot}-"][data-warm="0"][data-sub="0"] input[aria-label="weight"]`,ns=>ns.map(n=>Number(n.value)));
        assert.deepEqual(await weights(),expected);
        await probe.click('.wpb-load-explanation summary');
        assert.match(await probe.$eval('.wpb-load-explanation',el=>el.textContent),/Keep your previous working-set loads and build reps/);
        await probe.click('[data-why-target]');
        const explanation=await probe.waitForSelector('xpath/.//div[contains(@class,"wpb-backdrop")][.//*[contains(., "Why this prescription?")]]');
        assert.match(await explanation.evaluate(el=>el.textContent),/Keep your previous working-set loads and build reps/);
        if(unit==='kg')assert.match(await explanation.evaluate(el=>el.textContent),/52\.16 kg × 8/,'Historical evidence converts once without long decimal tails');
        await (await explanation.$('button[aria-label="Close"]')).click();
        await probe.waitForSelector('.wpb-backdrop',{hidden:true});
        await probe.click('button[aria-label="Focused mode"]');
        assert.equal(await probe.$eval('[data-focus-hero] input[aria-label="weight"]',el=>Number(el.value)),expected[0]);
        assert.match(await probe.$eval('[data-focus-hero]',el=>el.innerText),new RegExp(`\\b${unit}\\b`));
        await probe.click('button[aria-label="Exit focused mode"]');
        const historyBefore=await probe.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history);
        await probe.reload({waitUntil:'networkidle0'});
        await (await probe.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]')).click();
        await probe.waitForSelector('.wpb-workout');
        assert.deepEqual(await weights(),expected);
        await probe.click('.wpb-load-explanation summary');
        assert.match(await probe.$eval('.wpb-load-explanation',el=>el.textContent),/Keep your previous working-set loads and build reps/);
        assert.deepEqual(await probe.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history),historyBefore);
        console.log(`PASS M361 browser ${unit}: equipment target, all four prefills, expanded reason, Why, Focus and reload retain set-position loads ${expected.join(',')}; history unchanged.`);
    }finally{await context.close();}
}
assert.deepEqual(errors,[],'No runtime errors in rounded-target journeys');
} finally {
    await browser?.close();
    server.close();
}
