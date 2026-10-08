import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { generateNextProgramForShell, generateNextCycleForShell } from '../modules/engine-api.js';
import { generationInput, completedHistory, equipment, ids } from './m265-generation-fixture.mjs';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const input = generationInput();
const program = generateNextProgramForShell({ ...input, makeId: ids('m265-app-program') }).program;
const builtCycle = generateNextCycleForShell({ ...input, templateId: 'powerbuilding', adaptBetweenBlocks: true, makeId: ids('m265-app-cycle') });
const storeFor = (saved = [program], cycles = [], history = []) => ({
    v: 13, savedAt: 1, saved, cycles, history, perf: {}, drafts: {}, tombs: {}, seenIntro: 999, seenWhatsNew: 999,
    pinnedId: saved[0]?.id, theme: 'amethyst', restScale: 1, unit: 'lb', unitChosen: true, experience: 'intermediate',
    bodyweight: 185, sex: 'male', age: 30, equipDefault: equipment,
    gyms: [{ id: 'm265-gym', name: 'Test gym', equipment, limits: {}, inventory: {}, limitUnit: 'lb' }], activeGymId: 'm265-gym',
    installDismissedAt: Date.now(), lastBackup: Date.now()
});
let workerFault = false;
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const name = new URL(req.url, 'http://localhost').pathname;
    const file = path.resolve(root, '.' + (name === '/' ? '/index.html' : name));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    if (name === '/modules/generation-worker.js' && workerFault) { res.writeHead(503); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
    // A test-only busy loop in the REAL worker simulates a slower phone. The engine and app
    // are unchanged: animation/input must continue, and Cancel must terminate this computation.
    let body = fs.readFileSync(file);
    if (name === '/modules/generation-worker.js') body = Buffer.from(body + '\nconst slowUntil = performance.now() + 900; while (performance.now() < slowUntil) {}\n');
    res.end(body);
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
let browser;
const errors = [];
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    const open = async (store, width = 320, bypass = true) => {
        const context = await browser.createBrowserContext();
        const page = await context.newPage();
        page.on('pageerror', error => errors.push(error.message));
        await page.setViewport({ width, height: 780, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        await page.setBypassServiceWorker(bypass);
        await page.evaluateOnNewDocument(initial => {
            if (!localStorage.getItem('m265-seeded')) { localStorage.setItem('m265-seeded', '1'); localStorage.setItem('wpb:v1', JSON.stringify(initial)); }
            window.__m265 = { jobs: [], terminated: 0, ticks: 0 };
            setInterval(() => window.__m265.ticks++, 10);
            const NativeWorker = window.Worker;
            window.Worker = new Proxy(NativeWorker, { construct(Target, args) {
                const worker = new Target(...args);
                const post = worker.postMessage.bind(worker), terminate = worker.terminate.bind(worker);
                worker.postMessage = payload => { window.__m265.jobs.push({ operation: payload.operation, id: payload.id }); return post(payload); };
                worker.terminate = () => { window.__m265.terminated++; return terminate(); };
                return worker;
            } });
        }, store);
        await page.goto(origin, { waitUntil: 'networkidle0' });
        await page.waitForSelector('.wpb-tabbar');
        return { page, context };
    };
    const readStore = page => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')));
    const textButton = (page, label, prefix = false) => page.$$eval('button', (buttons, label, prefix) => {
        const button = buttons.find(n => prefix ? n.textContent.trim().startsWith(label) : n.textContent.trim() === label);
        if (!button) throw new Error(`Missing button: ${label}; available: ${buttons.map(n => n.textContent.trim()).filter(Boolean).join(' | ')}`);
        button.click();
    }, label, prefix);
    const plan = async page => { await page.click('[data-tab="plan"]'); await page.waitForSelector('.wpb-plan-view'); };
    const done = async page => { await page.waitForSelector('[data-generation-busy]', { hidden: true }); };
    const template = async (page, waitForStatus = true) => {
        await page.click('[data-tab="home"]');
        if (!(await page.$('.wpb-carousel-card'))) await textButton(page, 'Browse', true);
        await page.waitForSelector('.wpb-carousel-card');
        await page.$eval('.wpb-carousel-card', button => button.click());
        if (waitForStatus) await page.waitForSelector('[data-generation-busy]');
    };

    // Actual template button; double tap cannot produce two jobs or a competing plan.
    {
        const { page, context } = await open(storeFor());
        await page.click('[data-tab="home"]');
        if (!(await page.$('.wpb-carousel-card'))) await textButton(page, 'Browse', true);
        await page.waitForSelector('.wpb-carousel-card');
        const before = await page.evaluate(() => window.__m265.ticks);
        await page.$eval('.wpb-carousel-card', b => { b.click(); b.click(); });
        await page.waitForSelector('[data-generation-busy]');
        await page.evaluate(() => new Promise(resolve => setTimeout(resolve, 300)));
        assert.ok((await page.evaluate(() => window.__m265.ticks)) - before >= 12, 'the main thread continues to process input/timers during heavy worker computation');
        const bounds = await page.$eval('[data-generation-busy] > div', n => ({ left: n.getBoundingClientRect().left, right: n.getBoundingClientRect().right }));
        assert.ok(bounds.left >= 0 && bounds.right <= 321, 'build controls fit a narrow phone');
        await page.screenshot({ path: path.join(root, 'verification/m265-build-phone.png') });
        await done(page); await page.waitForSelector('.wpb-program');
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['program']);
        assert.ok(!(await page.evaluate(() => document.body.textContent)).includes("This combination couldn't be built safely"), 'a duplicate tap cannot surface a false capacity refusal');
        assert.deepEqual((await readStore(page)).saved, [program], 'template creation preserves existing saved plans');
        await page.click('button[aria-label="Reroll exercises or enter a seed"]'); await textButton(page, 'Shuffle');
        if (await page.$$eval('button', nodes => nodes.some(n => n.textContent.trim() === 'Regenerate'))) await textButton(page, 'Regenerate');
        await page.waitForFunction(() => window.__m265.jobs.length === 2); await done(page);
        await page.click('button[aria-label="Reroll exercises or enter a seed"]');
        await page.click('button[aria-label="Start this program again from week 1"]');
        await page.click('button[aria-label="Start over at week 1 with the original exercises"]');
        await page.waitForFunction(() => window.__m265.jobs.length === 3); await done(page);
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).saved.length === 2);
        assert.deepEqual((await readStore(page)).saved.find(p => p.id === program.id), program, 'reset preserves the original saved plan');
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['program', 'program', 'program']);
        await context.close();
    }
    // Saved-plan settings wait for the worker and retain identity and completed history.
    {
        const { page, context } = await open(storeFor([program], [], completedHistory(program)), 390);
        await plan(page); const beforeHistory = (await readStore(page)).history;
        await textButton(page, 'Open full program'); await page.waitForSelector('.wpb-program');
        await page.click('button[aria-label="Program settings"]');
        await page.waitForSelector('input[aria-label="Block length in weeks"]');
        await page.click('button[aria-label="Decrease block length"]');
        await page.click('button[aria-label="Save program settings"]');
        await page.waitForSelector('[data-generation-busy]'); await done(page);
        await page.waitForFunction(id => JSON.parse(localStorage.getItem('wpb:v1')).saved.find(p => p.id === id)?.config.weeks === 5, {}, program.id);
        const current = await readStore(page);
        assert.equal(current.saved.length, 1); assert.equal(current.pinnedId, program.id);
        assert.deepEqual(current.history, beforeHistory);
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['program']);
        await context.close();
    }
    // Cancel and navigation invalidate the result even after the old worker would have finished.
    for (const action of ['cancel', 'escape', 'back', 'navigate']) {
        const { page, context } = await open(storeFor());
        await template(page);
        if (action === 'cancel') await page.click('[data-cancel-generation]');
        else if (action === 'escape') await page.keyboard.press('Escape');
        else if (action === 'back') await page.evaluate(() => history.back());
        else await page.$eval('[data-tab="settings"]', button => button.click());
        await done(page);
        await page.evaluate(() => new Promise(resolve => setTimeout(resolve, 1300)));
        assert.ok(await page.evaluate(() => window.__m265.terminated >= 1));
        assert.deepEqual((await readStore(page)).saved, [program]);
        await plan(page); assert.ok((await page.$eval('.wpb-plan-view', n => n.textContent)).includes(program.name));
        await context.close();
    }
    // A failed native worker cannot fall back to synchronous generation or overwrite a plan; retry starts cleanly.
    {
        const { page, context } = await open(storeFor());
        workerFault = true; await template(page, false);
        await page.waitForFunction(() => window.__m265.terminated >= 1); await done(page); workerFault = false;
        assert.deepEqual((await readStore(page)).saved, [program]);
        assert.ok(await page.evaluate(() => window.__m265.terminated >= 1));
        assert.ok((await page.evaluate(() => document.body.textContent)).includes('The plan builder could not start'), 'transport failure gives an actionable builder message');
        await template(page); await done(page); await page.waitForSelector('.wpb-program');
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['program', 'program']);
        await context.close();
    }
    // Standalone block button uses completed OWN history and preserves the historical program and records.
    {
        const history = completedHistory(program), { page, context } = await open(storeFor([program], [], history));
        await plan(page); const beforeHistory = (await readStore(page)).history;
        await textButton(page, 'Open full program'); await page.waitForSelector('.wpb-program');
        await textButton(page, 'Build next block'); await textButton(page, 'Create next block');
        await page.waitForSelector('[data-generation-busy]'); await done(page);
        await page.waitForFunction(id => JSON.parse(localStorage.getItem('wpb:v1')).saved.some(p => p.id !== id), {}, program.id);
        const current = await readStore(page);
        assert.equal(current.saved.length, 2); assert.deepEqual(current.saved.find(p => p.id === program.id), program);
        assert.deepEqual(current.history, beforeHistory); assert.notEqual(current.pinnedId, program.id);
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['next_block']);
        await context.close();
    }
    // Actual Complete Block confirmation advances an attached cycle through the worker.
    {
        const { cycle, blocks } = builtCycle, history = completedHistory(blocks[0]);
        const { page, context } = await open(storeFor(blocks, [cycle], history));
        await plan(page); const beforeHistory = (await readStore(page)).history;
        await textButton(page, 'Open full program'); await page.waitForSelector('.wpb-program');
        await page.click('button[aria-label^="Part of cycle"]'); await page.waitForSelector('.wpb-cycle-detail');
        await page.$eval('.wpb-cycle-detail details', n => { n.open = true; });
        await textButton(page, 'Complete block · advance', true); await textButton(page, 'Complete block');
        await page.waitForSelector('[data-generation-busy]'); await done(page);
        await page.waitForFunction(id => JSON.parse(localStorage.getItem('wpb:v1')).cycles.find(c => c.id === id)?.activeBlock === 1, {}, cycle.id);
        const current = await readStore(page);
        assert.deepEqual(current.history, beforeHistory); assert.deepEqual(current.saved.find(p => p.id === blocks[0].id), blocks[0]);
        assert.equal(current.pinnedId, cycle.blockIds[1]);
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['advance_cycle']);
        await context.close();
    }
    // Standalone conversion keeps the exact first block and adds audited future blocks.
    {
        const { page, context } = await open(storeFor(), 390);
        await page.click(`button[aria-label="More options for ${program.name}"]`);
        await textButton(page, 'Turn into training cycle');
        await page.waitForSelector('[aria-label="Turn program into training cycle"]');
        await textButton(page, 'Create training cycle');
        await page.waitForSelector('[data-generation-busy]'); await done(page);
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).cycles.length === 1);
        const current = await readStore(page), converted = current.saved.find(p => p.id === program.id);
        assert.deepEqual(converted.days, program.days); assert.deepEqual(converted.nextWeekPrescriptions, program.nextWeekPrescriptions);
        assert.deepEqual(converted.overrides, program.overrides);
        for (const [key, value] of Object.entries(program.config)) assert.deepEqual(converted.config[key], value, `conversion preserves ${key}`);
        assert.equal(converted.nextEngine.program.audit.result, 'pass');
        assert.equal(current.cycles[0].blockIds[0], program.id);
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['convert_cycle']);
        await context.close();
    }
    // Imported looping cycles retain their completed cycle and old blocks when a fresh cycle is rolled.
    {
        const { cycle, blocks } = structuredClone(builtCycle);
        cycle.activeBlock = blocks.length - 1; cycle.onComplete = 'loop';
        const { page, context } = await open(storeFor(blocks, [cycle], completedHistory(blocks.at(-1))), 390);
        await plan(page); const beforeHistory = (await readStore(page)).history;
        await textButton(page, 'Open full program'); await page.waitForSelector('.wpb-program');
        await page.click('button[aria-label^="Part of cycle"]'); await page.waitForSelector('.wpb-cycle-detail');
        await page.$eval('.wpb-cycle-detail details', n => { n.open = true; });
        await textButton(page, 'Complete final block'); await textButton(page, 'Roll new cycle');
        await page.waitForSelector('[data-generation-busy]'); await done(page);
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).cycles.length === 2);
        const current = await readStore(page);
        assert.equal(current.cycles.find(c => c.id === cycle.id).done, true);
        const rolled = current.cycles.find(c => c.id !== cycle.id);
        assert.equal(rolled.continuedFrom, cycle.id); assert.equal(rolled.activeBlock, 0);
        assert.deepEqual(current.saved.filter(p => cycle.blockIds.includes(p.id)), blocks);
        assert.deepEqual(current.history, beforeHistory);
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['advance_cycle', 'cycle']);
        await context.close();
    }
    // The installed shell must precache the worker AND its full dependency closure for an offline build.
    {
        const { page, context } = await open(storeFor(), 390, false);
        await page.evaluate(async () => { const reg = await navigator.serviceWorker.ready; if (!reg.active) throw new Error('No active service worker'); });
        await page.reload({ waitUntil: 'networkidle0' });
        await page.waitForFunction(() => !!navigator.serviceWorker.controller);
        const cache = await page.evaluate(async () => {
            const reg = await navigator.serviceWorker.getRegistration();
            const names = await caches.keys();
            return { active: !!reg?.active, worker: (await caches.match('/modules/generation-worker.js'))?.status,
                transport: (await caches.match('/modules/generation-runtime.js'))?.status, names };
        });
        assert.equal(cache.worker, 200); assert.equal(cache.transport, 200);
        await page.setOfflineMode(true);
        await template(page); await done(page); await page.waitForSelector('.wpb-program');
        assert.deepEqual(await page.evaluate(() => window.__m265.jobs.map(j => j.operation)), ['program']);
        assert.deepEqual((await readStore(page)).saved, [program]);
        await page.setOfflineMode(false); await context.close();
    }
    assert.deepEqual(errors, []);
    console.log('PASS M265 browser: real template/shuffle/reset/settings/next-block/cycle-advance/conversion/loop buttons, responsive build status at 320/390px, duplicate-tap exclusion, Cancel/Escape/Back, stale navigation rejection, worker failure/retry, preserved programs/history and installed offline worker generation.');
} finally { workerFault = false; if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
