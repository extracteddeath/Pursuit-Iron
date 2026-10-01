import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { EXERCISES } from '../modules/App.js';
import { generateNextProgramForShell, markUserPrescriptionOverride } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine } from '../modules/next-engine/workout-history-adapter.js';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const fixture = JSON.parse(fs.readFileSync(path.join(root, 'verification/m205-generation-parity-results.json'))).rows[3];
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
    console.log('PASS M214 mobile browser: edited 2 x 10-15 workout actually saved with immutable prescription, typed-row rep targets and explicit RIR; history replay and reload agree.');
}
finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
