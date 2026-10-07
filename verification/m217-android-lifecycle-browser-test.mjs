import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// Exercise the complete production app, including its real store, Back bridge and service worker.
const root = process.env.APP_ROOT || path.resolve(new URL('../', import.meta.url).pathname);
const program = {
    id: 'm217-android', name: 'Android recovery', custom: true, weeks: 10,
    config: { name: 'Android recovery', goal: 'both', experience: 'intermediate', progression: 'manual',
        split: 'custom', weeks: 10, days: 1, session: 's60', unit: 'lb', deload: false },
    days: [{ id: 'm217-upper', label: 'Upper', primaryIndex: -1, exercises: ['inc-curl'] }],
    overrides: { 'm217-upper:0': { sets: 2, reps: '10-15', rir: '2', rest: 90, tech: null } }
};
const store = { v: 13, savedAt: 1, saved: [program], cycles: [], history: [], perf: {}, drafts: {}, tombs: {},
    seenIntro: 999, seenWhatsNew: 999, pinnedId: program.id, restAutoStart: false, warmupCard: false,
    unit: 'lb', unitChosen: true, experience: 'intermediate', bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const live = { schemaVersion: 2, programId: program.id, dayId: program.days[0].id, weekIndex: 1,
    dayExSig: 'inc-curl', exIdx: 0, readiness: { label: 'Normal', factor: 1 }, elapsedMs: 120000,
    restPaused: true, restRemain: 47, restMax: 90, runPaused: true,
    data: [{ id: 'inc-curl', slot: 0, note: 'Keep this note', sets: [
        { weight: '100', reps: '11', done: true, auto: false, valueOwner: 'user', target: { w: '100', reps: '10-15', rir: '2' } },
        { weight: '95', reps: '12', done: false, auto: false, valueOwner: 'user', target: { w: '95', reps: '10-15', rir: '2' } }
    ] }] };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const file = path.resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
    res.setHeader('Cache-Control', 'no-store'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8772, '127.0.0.1', resolve));
const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pursuit-m217-'));
let browser;
const launch = () => puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome',
    userDataDir, headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
const errors = [];
const configure = async page => {
    page.on('pageerror', error => errors.push(error.message));
    await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await page.evaluateOnNewDocument((initial, snapshot) => {
        const originalSet = Storage.prototype.setItem, originalRemove = Storage.prototype.removeItem;
        if (!localStorage.getItem('m217-seeded')) {
            originalSet.call(localStorage, 'm217-seeded', '1');
            originalSet.call(localStorage, 'wpb:v1', JSON.stringify(initial));
            originalSet.call(localStorage, 'wpb:live', JSON.stringify(snapshot));
        }
        window.__m217FailKeys = [];
        Storage.prototype.setItem = function (key, value) {
            if (window.__m217FailKeys.includes(key)) throw new DOMException('Device full', 'QuotaExceededError');
            return originalSet.call(this, key, value);
        };
        Storage.prototype.removeItem = function (key) {
            if (key === 'wpb:live' && window.__m217BlockLiveDelete) throw new Error('Interrupted cleanup');
            return originalRemove.call(this, key);
        };
    }, store, live);
};
const snapshot = page => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));
const training = page => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')));
const clickText = (page, text) => page.$$eval('button', (buttons, label) => {
    const button = buttons.find(b => b.textContent.trim() === label);
    if (!button) throw new Error('Missing button: ' + label);
    button.click();
}, text);
const openWorkout = async page => {
    await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.waitForSelector('.wpb-workout input[aria-label="reps"]');
};
try {
    browser = await launch(); let page = await browser.newPage(); await configure(page);
    await page.goto('http://127.0.0.1:8772/', { waitUntil: 'networkidle0' }); await openWorkout(page);
    const fields = await page.$$('input[aria-label="weight"]');
    await fields[1].click(); await page.keyboard.down('Control'); await page.keyboard.press('KeyA'); await page.keyboard.up('Control'); await page.keyboard.press('Backspace'); await page.keyboard.type('155'); await page.keyboard.press('Enter');
    assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-label')), 'reps', 'numeric Next advances load to reps');
    await page.keyboard.down('Control'); await page.keyboard.press('KeyA'); await page.keyboard.up('Control');
    await page.keyboard.type('14'); await page.keyboard.press('Enter');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].reps === '14');
    const typed = await snapshot(page);
    assert.equal(typed.elapsedMs, live.elapsedMs, 'a restored paused workout retains its exact elapsed duration');
    assert.equal(typed.data[0].sets[1].weight, '155');
    assert.equal(typed.data[0].sets[1].done, false, 'keyboard Next/Done cannot log a set');

    // Reproduce the M216 loss path first, so the same gate can prove the regression fails there.
    await page.evaluate(() => { window.__m217FailKeys = ['wpb:live']; history.back(); });
    await page.waitForFunction(() => !document.querySelector('.wpb-workout') || !!document.querySelector('.wpb-workout [role="alert"]'));
    assert.equal(await page.$('.wpb-workout') !== null, true, 'system Back must keep an unsaved workout open');
    assert.equal((await snapshot(page)).data[0].sets[1].weight, '155');
    await page.evaluate(() => { window.__m217FailKeys = []; }); await clickText(page, 'Retry save');
    await page.waitForSelector('.wpb-workout [role="alert"]', { hidden: true });
    await page.click('button[aria-label="Workout options"]');
    await page.waitForSelector('.wpb-backdrop');
    await page.evaluate(() => history.back()); await page.waitForSelector('.wpb-backdrop', { hidden: true });
    assert.ok(await page.$('.wpb-workout'), 'Back closes the top sheet before minimizing');
    await page.evaluate(() => history.back()); await page.waitForSelector('.wpb-workout', { hidden: true });
    await openWorkout(page); const resumed = await snapshot(page);
    assert.ok(resumed.sessionId, 'live workouts carry a stable completion id');
    assert.equal(resumed.sessionId, typed.sessionId);
    assert.equal(resumed.data[0].sets[1].weight, '155'); assert.equal(resumed.data[0].sets[1].reps, '14');
    assert.equal(resumed.restPaused, true); assert.equal(resumed.restRemain, 47);
    assert.equal(resumed.runPaused, true); assert.equal(resumed.elapsedMs, typed.elapsedMs);
    await page.evaluate(() => history.back()); await page.waitForSelector('.wpb-workout', { hidden: true });
    await openWorkout(page);
    assert.equal((await snapshot(page)).elapsedMs, live.elapsedMs, 'repeated minimize/resume cannot drift a paused clock');

    // Production save failure must retain BOTH the live recovery copy and the current screen.
    await page.evaluate(() => { window.__m217FailKeys = ['wpb:v1']; });
    await page.click('button[aria-label="Finish workout"]');
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === 'Save & finish'));
    await clickText(page, 'Save & finish');
    await page.waitForSelector('.wpb-workout [role="alert"]');
    assert.equal((await training(page)).history.length, 0, 'failed persistence cannot claim a logged session');
    assert.equal((await snapshot(page)).sessionId, resumed.sessionId);
    assert.equal((await snapshot(page)).data[0].note, 'Keep this note');
    await page.evaluate(() => { window.__m217FailKeys = []; }); await clickText(page, 'Retry save');
    await page.waitForSelector('.wpb-workout [role="alert"]', { hidden: true });

    for (const [width, height] of [[320, 740], [390, 844], [844, 390]]) {
        await page.setViewport({ width, height, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
        await page.waitForFunction(w => window.innerWidth === w, {}, width);
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        // Entry and sheet-close animations can still translate the shell after two frames.
        // Measure the settled layout while keeping the viewport bounds assertion unchanged.
        await page.evaluate(() => Promise.allSettled(document.getAnimations().filter(a => a.effect?.getComputedTiming().iterations !== Infinity).map(a => a.finished)));
        const bounds = await page.$eval('.wpb-workout', el => { const r = el.getBoundingClientRect(); return { left: r.left, right: r.right, bottom: r.bottom }; });
        if (bounds.left < -1 || bounds.right > width + 1 || bounds.bottom > height + 1)
            await page.screenshot({ path: path.join(root, 'verification/m217-geometry-phone.png'), fullPage: true });
        assert.ok(bounds.left >= -1 && bounds.right <= width + 1 && bounds.bottom <= height + 1, 'workout shell stays within the phone/split-screen viewport: ' + JSON.stringify({ width, height, bounds }));
    }
    await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await page.screenshot({ path: path.join(root, 'verification/m217-recovery-phone.png'), fullPage: true });

    // Installed Android PWAs can lose their renderer while Chrome's storage service survives.
    // Crash the real renderer and open a fresh page offline, with no in-memory React state.
    await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' }); document.dispatchEvent(new Event('visibilitychange')); });
    const beforeKill = await snapshot(page);
    await page.evaluate(async () => { await navigator.serviceWorker.ready; });
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    const crashed = new Promise(resolve => page.once('error', resolve));
    const cdp = await page.createCDPSession();
    cdp.send('Page.crash').catch(() => {}); await crashed; await page.close();
    page = await browser.newPage(); await configure(page); await page.setOfflineMode(true);
    await page.goto('http://127.0.0.1:8772/', { waitUntil: 'networkidle0' }); await openWorkout(page);
    const afterKill = await snapshot(page);
    assert.equal(afterKill.sessionId, beforeKill.sessionId, 'a new offline renderer restores the same workout');
    assert.equal((await snapshot(page)).data[0].sets[1].weight, '155');
    assert.equal((await snapshot(page)).data[0].sets[1].reps, '14');
    assert.equal((await snapshot(page)).restRemain, 47);

    // Model a kill after durable history but before recovery cleanup: no resume or duplicate log.
    await page.evaluate(() => { window.__m217BlockLiveDelete = true; });
    await page.click('button[aria-label="Finish workout"]');
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === 'Save & finish'));
    await clickText(page, 'Save & finish'); await page.waitForSelector('.wpb-workout', { hidden: true });
    const logged = await training(page);
    assert.equal(logged.history.length, 1); assert.equal(logged.history[0].id, beforeKill.sessionId);
    assert.ok(logged.history[0].perf['inc-curl'].sets.length > 0);
    assert.ok(await snapshot(page), 'the recovery file deliberately survives interrupted cleanup');
    await page.reload({ waitUntil: 'networkidle0' });
    await page.waitForFunction(() => window.__pursuitMounted && !!document.querySelector('.wpb-home'));
    assert.equal((await training(page)).history.length, 1); assert.equal(await page.$('.wpb-live-dock'), null);
    assert.equal(await snapshot(page), null, 'cold startup recognizes the already-committed workout');
    await page.setOfflineMode(false);

    // Explicit update restarts must wait for the current store writer and stop on failed saves.
    await page.evaluate(() => {
        window.__m217SentUpdate = null;
        window.dispatchEvent(new CustomEvent('wpb:update-ready', { detail: { waiting: { postMessage: msg => { window.__m217SentUpdate = msg; } } } }));
        window.__m217FailKeys = ['wpb:v1'];
    });
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => /Update ready.*Restart/.test(b.textContent)));
    await page.$$eval('button', buttons => buttons.find(b => /Update ready.*Restart/.test(b.textContent)).click());
    await page.waitForFunction(() => document.body.textContent.includes('Could not save before restarting'));
    assert.equal(await page.evaluate(() => !!window.__pursuitUpdateRequested), false);
    assert.equal(await page.evaluate(() => window.__m217SentUpdate), null);
    await page.evaluate(() => { window.__m217FailKeys = []; });
    await page.$$eval('button', buttons => buttons.find(b => /Update ready.*Restart/.test(b.textContent)).click());
    await page.waitForFunction(() => !!window.__m217SentUpdate);
    assert.deepEqual(await page.evaluate(() => window.__m217SentUpdate), { type: 'SKIP_WAITING' });
    assert.equal((await training(page)).history.length, 1);
    assert.deepEqual(errors, []);
    console.log('PASS M217 production Android browser: numeric entry, nested Back, failed-save retention, stable workout id, paused timers, compact/landscape geometry, renderer-crash offline restore, atomic history and interrupted cleanup, and save-before-update.');
}
finally {
    if (browser?.connected) await browser.close();
    await new Promise(resolve => server.close(resolve));
    fs.rmSync(userDataDir, { recursive: true, force: true });
}
