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
const clickText = async (page, text) => { await page.bringToFront(); return page.$$eval('button', (buttons, label) => {
    const button = buttons.find(b => b.textContent.trim() === label);
    if (!button) throw new Error('Missing button: ' + label);
    button.click();
}, text); };
const openWorkout = async page => {
    await page.bringToFront();
    await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.waitForSelector('.wpb-workout input[aria-label="reps"]');
};
// A PWA window and browser tab share both training and the live recovery key.
// A warning alone cannot stop the older tab's next autosave erasing a workout.
async function verifyStorageOwner(browser) {
    const context = await browser.createBrowserContext();
    const first = await context.newPage(), second = await context.newPage();
    try {
        await configure(first); await configure(second);
        await first.goto('http://127.0.0.1:8772/', { waitUntil: 'networkidle0' });
        await openWorkout(first);
        const liveBefore = await snapshot(first);
        await second.bringToFront();
        await second.goto('http://127.0.0.1:8772/', { waitUntil: 'networkidle0' });
        await second.waitForFunction(() => !!document.querySelector('.wpb-home, [data-storage-busy]'));
        if (await second.$('.wpb-home')) {
            await first.bringToFront();
            await first.click('button[aria-label="Finish workout"]');
            await first.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === 'Save & finish'));
            await clickText(first, 'Save & finish');
            await first.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.length === 1);
            await second.bringToFront();
            await second.click('[data-tab="settings"]');
            await second.locator('[data-theme-summary]').click();
            await second.locator('[data-theme-option="rosewater"]').click();
            await second.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).theme === 'rosewater');
            assert.equal((await training(first)).history.length, 1,
                'A stale second tab must never erase a workout completed in the first tab');
        }
        assert.ok(await second.$('[data-storage-busy]'), 'A second app is held before hydration or autosave');
        assert.equal(await second.$('.wpb-live-dock'), null, 'The blocked tab cannot resume or discard another window’s workout');
        assert.equal(await second.$('input[type="file"]'), null, 'The blocked tab cannot restore over the active owner');
        await second.evaluate(() => document.documentElement.style.fontSize = '200%');
        await second.screenshot({ path: path.join(root, 'verification/m375-storage-gate-phone.png') });
        assert.deepEqual(await snapshot(first), liveBefore, 'Opening a second window cannot change live recovery');
        await clickText(second, 'Try again');
        await second.waitForSelector('[data-storage-busy]');
        assert.deepEqual(await snapshot(first), liveBefore, 'Retry cannot bypass an active owner');
        await first.bringToFront();
        await first.click('button[aria-label="Finish workout"]');
        await first.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === 'Save & finish'));
        await clickText(first, 'Save & finish');
        await first.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.length === 1);
        await first.close();
        await second.waitForFunction(async () => !(await navigator.locks.query()).held.some(lock => lock.name === 'pursuit-iron:wpb-storage'));
        await clickText(second, 'Try again');
        await second.waitForSelector('.wpb-home');
        assert.equal((await training(second)).history.length, 1, 'Released ownership loads the current completed history');
        assert.equal(await snapshot(second), null, 'The completed workout is not resurrected after ownership changes');
        const completedId = (await training(second)).history[0].id;
        // A previous app version cannot honor a lock it never implemented. Preserve
        // this window's own export and stop its writes on any unexpected external edit.
        const external = await context.newPage();
        await external.setBypassServiceWorker(true);
        await external.goto('http://127.0.0.1:8772/privacy.html');
        const externalText = await external.evaluate(() => {
            const data = JSON.parse(localStorage.getItem('wpb:v1'));
            data.history = [{ ...data.history[0], id: 'legacy-window-workout' }];
            const text = JSON.stringify(data); localStorage.setItem('wpb:v1', text); return text;
        });
        await second.bringToFront();
        await second.waitForSelector('[data-storage-changed="true"]');
        await second.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        assert.equal(await second.evaluate(() => localStorage.getItem('wpb:v1')), externalText, 'A stale window pauses instead of overwriting external edits');
        await second.evaluate(() => {
            URL.createObjectURL = blob => { blob.text().then(text => { window.__m375Export = text; }); return 'blob:backup-probe'; };
            URL.revokeObjectURL = () => {};
            HTMLAnchorElement.prototype.click = () => {};
            document.documentElement.style.fontSize = '200%';
        });
        const backup = await second.waitForSelector('xpath/.//button[normalize-space(.)="Download this window’s backup"]');
        await backup.evaluate(node => node.scrollIntoView({ block: 'center' })); await backup.click();
        await second.waitForFunction(() => !!window.__m375Export);
        assert.equal(await second.evaluate(() => JSON.parse(window.__m375Export).history[0].id), completedId,
            'Emergency export keeps the original window history after an external overwrite');
        await second.reload({ waitUntil: 'networkidle0' }); await second.waitForSelector('.wpb-home');
        assert.equal((await training(second)).history[0].id, 'legacy-window-workout', 'A fresh document hydrates the latest external data');
        await external.close();
        await second.setOfflineMode(true);
        await second.goto('http://127.0.0.1:8772/privacy.html');
        assert.match(await second.title(), /^Privacy Policy/, 'The service worker must serve the real privacy page');
        const returned = await context.newPage(); await configure(returned);
        await returned.goto('http://127.0.0.1:8772/', { waitUntil: 'networkidle0' }); await returned.waitForSelector('.wpb-home');
        await second.bringToFront();
        await second.goBack({ waitUntil: 'networkidle0' }); await second.waitForSelector('[data-storage-busy="true"]');
        assert.equal((await training(returned)).history[0].id, 'legacy-window-workout', 'Back navigation cannot revive a stale document over the current owner');
        console.log('PASS M375: competing app windows cannot overwrite training/live state; retry waits for ownership and then loads the completed workout.');
    } finally { await context.close(); }
}
try {
    browser = await launch();
    await verifyStorageOwner(browser);
    let page = await browser.newPage(); await configure(page);
    await page.goto('http://127.0.0.1:8772/', { waitUntil: 'networkidle0' }); await openWorkout(page);
    assert.deepEqual(await page.$eval('[data-testid="set-0-0"]', row => {
        const w = row.querySelector('input[aria-label="weight"]');
        const r = row.querySelector('input[aria-label="reps"]');
        return [!!row.querySelector('.wpb-set-controls'), !!w?.readOnly, !!r?.readOnly];
    }), [true, true, true], 'resumed completed set retains the full faded row with read-only weight and reps');
    const pendingWeight = await page.waitForSelector('[data-testid="set-0-1"] input[aria-label="weight"]');
    await pendingWeight.click(); await page.keyboard.down('Control'); await page.keyboard.press('KeyA'); await page.keyboard.up('Control'); await page.keyboard.press('Backspace'); await page.keyboard.type('155'); await page.keyboard.press('Enter');
    // Enter/IME Next is sometimes scheduled on the next React/animation frame.
    // Wait for the real app to move focus; never fake the transition in the test.
    await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === 'reps', {timeout:3000});
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
    assert.ok(await page.$('[data-view-frame="home"]'), 'a workout resumed from Home minimizes back to Home');
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
    assert.ok(await page.$('[data-view-frame="home"]'), 'a workout recovered offline finishes back at its launch destination');
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
        const waiting = { state: 'installed', postMessage: msg => { window.__m217SentUpdate = msg; } };
        navigator.serviceWorker.getRegistration = async () => ({ waiting });
        const stale = { postMessage: () => { window.__m217UsedStaleUpdate = true; } };
        window.dispatchEvent(new CustomEvent('wpb:update-ready', { detail: { waiting: stale } }));
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
    assert.equal(await page.evaluate(() => !!window.__m217UsedStaleUpdate), false, 'Restart re-resolves the waiting worker instead of messaging the stale hint');
    assert.equal(await page.$eval('button.wpb-notice-in', b => b.disabled && b.getAttribute('aria-busy')==='true'), true);
    await page.waitForFunction(() => document.body.textContent.includes('Restart did not finish'), {timeout:12000});
    assert.equal(await page.$eval('button.wpb-notice-in', b => b.disabled), false, 'a stalled update restores an actionable retry');
    assert.equal(await page.evaluate(() => !!window.__pursuitUpdateRequested), false, 'a timed-out attempt cannot reload later without consent');
    await page.evaluate(() => {
        window.__m217SentUpdate = null;
        navigator.serviceWorker.getRegistration = async () => ({waiting:{postMessage: msg => {window.__m217SentUpdate={...msg,retry:true};}}});
    });
    await page.locator('button.wpb-notice-in').click();
    await page.waitForFunction(() => window.__m217SentUpdate?.retry);

    assert.equal((await training(page)).history.length, 1);
    assert.deepEqual(errors, []);
    console.log('PASS M217 production Android browser: numeric entry, nested Back, failed-save retention, stable workout id, paused timers, compact/landscape geometry, renderer-crash offline restore, atomic history and interrupted cleanup, and save-before-update with fresh-worker resolution, stalled-activation feedback and retry.');
}
finally {
    if (browser?.connected) await browser.close();
    await new Promise(resolve => server.close(resolve));
    fs.rmSync(userDataDir, { recursive: true, force: true });
}
