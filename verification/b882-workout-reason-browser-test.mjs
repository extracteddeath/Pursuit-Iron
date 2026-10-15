// Build 882 — the workout screen gives ONE consistent reason for a target, and the evidence is one tap away.
// A real session is logged through the UI and repeated immediately, which is precisely the state in
// which v661's fatigue override used to relabel the lift "e1RM autoregulation · temporary" over loads
// that were really dynamic double progression, while Coach claimed "Today's target is already reduced".
import assert from 'node:assert/strict';
import fs from 'node:fs'; import http from 'node:http'; import path from 'node:path'; import puppeteer from 'puppeteer-core';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { EXERCISES } from '../modules/App.js';
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
} finally {
    await browser?.close();
    server.close();
}
