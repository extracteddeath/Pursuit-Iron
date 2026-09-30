import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { EXERCISES, STORE_VERSION } from '../modules/App.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter-capacity.js';
import { nextProgramToShellProgram } from '../modules/next-engine/app-shell-adapter.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { createTrainingSetEvents } from '../modules/next-engine/events.js';
import { deriveMuscleLedger } from '../modules/next-engine/ledgers.js';
import { estimateSessionMinutes } from '../modules/next-engine/realizer.js';
import { auditProgram } from '../modules/next-engine/arbiter.js';

const config = { name: 'M199 volume repair', split: 'ulppl', days: 5, session: 's90', goal: 'both', experience: 'intermediate', weeks: 6,
    equipment: ['barbell', 'rack', 'dumbbell', 'bench', 'cable', 'machine', 'smith', 'pullup', 'legpress'], barbellCap: 3 };
const built = generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 199 });
const engine = structuredClone(built.nextProgram), map = createExerciseMap([]);
const rows = engine.sessions.flatMap(s => s.exercises.map(e => ({ s, e }))).filter(x => map.get(x.e.exerciseId).movementFamily === 'horizontal_pull');
if (rows.length === 1) {
    const source = rows[0], destination = engine.sessions.find(s => s !== source.s && ['upper', 'full', 'strength_full', 'pull'].includes(s.intent));
    const extra = structuredClone(source.e), at = destination.exercises.findIndex(e => !map.get(e.exerciseId).flags.compound);
    destination.exercises.splice(at < 0 ? destination.exercises.length : at, 0, extra); rows.push({ s: destination, e: extra });
}
for (const { e } of rows.slice(0, 2)) { e.exerciseId = 'chest_supported_row'; e.name = 'Chest-Supported Row'; e.sets = 3; }
engine.sessions.forEach(s => { s.estimatedMinutes = estimateSessionMinutes(s.exercises); });
engine.events = createTrainingSetEvents(engine.sessions, built.request.customExercises);
engine.muscleLedger = deriveMuscleLedger(engine.events); engine.audit = auditProgram(engine, built.request);
assert.equal(engine.audit.result, 'pass');
const fixture = nextProgramToShellProgram(engine, config, EXERCISES, () => 'm199-browser-volume');
fixture.nextEngine = { ...built.program.nextEngine, ...fixture.nextEngine, program: engine, request: built.request };

const root = new URL('../', import.meta.url).pathname;
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const file = path.join(root, pathname === '/' ? 'index.html' : pathname);
    if (!file.startsWith(root) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] ?? 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8767, '127.0.0.1', resolve));
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    const page = await browser.newPage();
    await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    const errors = []; page.on('pageerror', e => errors.push(e.message));
    await page.goto('http://127.0.0.1:8767/', { waitUntil: 'networkidle0' });
    await page.evaluate((saved, v) => localStorage.setItem('wpb:v1', JSON.stringify({ v, savedAt: Date.now(), saved: [saved], pinnedId: saved.id,
        history: [], perf: {}, drafts: {}, cycles: [], custom: [], banned: [], equipDefault: saved.config.equipment, unit: 'lb', unitChosen: true,
        experience: 'intermediate', seenIntro: 999, seenWhatsNew: 999, theme: 'purple' })), fixture, STORE_VERSION);
    await page.reload({ waitUntil: 'networkidle0' });
    await page.waitForFunction(() => !!window.__pursuitMounted);
    const openPlan = async () => {
        await page.waitForSelector('.hp-open'); await page.click('.hp-open');
        await page.waitForSelector('button[title="Program details"]'); await page.click('button[title="Program details"]');
        await page.waitForFunction(() => [...document.querySelectorAll('[data-infocard] > button')].some(b => /Weekly volume/.test(b.textContent)));
        await page.evaluate(() => [...document.querySelectorAll('[data-infocard] > button')].find(b => /Weekly volume/.test(b.textContent)).click());
    };
    await openPlan();
    await page.waitForSelector('[data-volume-auto-fix]');
    const before = await page.evaluate(async id => {
        const { volumeAudit, plannedWeek } = await import('./modules/App.js');
        const p = JSON.parse(localStorage.getItem('wpb:v1')).saved.find(p => p.id === id);
        return { issues: volumeAudit(p).issues, counts: Array.from({ length: 6 }, (_, i) => plannedWeek(p, i + 1).sets) };
    }, fixture.id);
    assert.ok(before.issues.some(i => i.region === 'upper_back'));
    await page.click('[data-volume-auto-fix]');
    await page.waitForSelector('[data-volume-repair-status="success"]', { timeout: 30000 });
    await page.waitForFunction(id => JSON.parse(localStorage.getItem('wpb:v1'))?.saved?.find(p => p.id === id)?.nextEngine?.volumeRepair?.status === 'success', {}, fixture.id);
    const after = await page.evaluate(async id => {
        const { volumeAudit, plannedWeek } = await import('./modules/App.js');
        const p = JSON.parse(localStorage.getItem('wpb:v1')).saved.find(p => p.id === id);
        return { issues: volumeAudit(p).issues, counts: Array.from({ length: 6 }, (_, i) => plannedWeek(p, i + 1).sets), slots: p.days.reduce((n, d) => n + d.exercises.length, 0) };
    }, fixture.id);
    assert.equal(after.issues.length, 0); assert.equal(after.slots, fixture.days.reduce((n, d) => n + d.exercises.length, 0));
    assert.notDeepEqual(after.counts, before.counts);
    await page.screenshot({ path: path.join(root, 'verification/m199-volume-repair-phone.png'), fullPage: true });
    await page.reload({ waitUntil: 'networkidle0' }); await openPlan();
    assert.equal(await page.$('[data-volume-auto-fix]'), null, 'resolved warning must stay resolved after reload');
    const reloadCounts = await page.evaluate(async id => {
        const { plannedWeek } = await import('./modules/App.js');
        const p = JSON.parse(localStorage.getItem('wpb:v1')).saved.find(p => p.id === id);
        return Array.from({ length: 6 }, (_, i) => plannedWeek(p, i + 1).sets);
    }, fixture.id);
    assert.deepEqual(reloadCounts, after.counts);
    assert.deepEqual(errors, [], 'real UI must remain free of render/runtime errors');
    console.log(`PASS M199 browser Auto-fix: click -> canonical saved state -> audit -> reload; week counts ${before.counts.join('/')} -> ${after.counts.join('/')}; ${after.slots} stable exercise slots.`);
}
finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
