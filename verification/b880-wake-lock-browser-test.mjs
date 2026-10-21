// Build 880 — the screen stays on while a workout is open.
// A stubbed navigator.wakeLock records every request/release so the real app wiring is exercised:
// acquire on open, re-acquire after the OS drops the lock while hidden, release on leaving, and never
// request when the lifter has turned the preference off. Settings writes the per-device preference.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const program = { id: 'b880-awake', name: 'Wake fixture', custom: true, weeks: 6,
    config: { unit: 'lb', goal: 'both', experience: 'intermediate', progression: 'manual', split: 'custom', deload: false },
    days: [{ id: 'lower', label: 'Lower', primaryIndex: 0, exercises: ['back-squat'] }],
    overrides: { 'lower:0': { sets: 2, reps: '5-8', rir: '2', rest: 120 } } };
const initial = { v: 13, savedAt: 1, saved: [program], cycles: [], history: [], perf: {},
    drafts: {}, tombs: {}, seenIntro: 999, seenWhatsNew: 999, pinnedId: program.id,
    restAutoStart: false, warmupCard: false, unit: 'lb', unitChosen: true, experience: 'intermediate',
    bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const live = { schemaVersion: 2, programId: program.id, dayId: 'lower', weekIndex: 1,
    dayExSig: 'back-squat', exIdx: 0, elapsedMs: 60000, readiness: { label: 'Normal', factor: 1 },
    restPaused: true, restRemain: 0, restMax: 90, runPaused: true,
    data: [{ id: 'back-squat', slot: 0, note: '', sets: [
        { weight: '185', reps: '5', done: false, auto: false, valueOwner: 'user', target: { w: '185', reps: '5-8', rir: '2' } },
        { weight: '185', reps: '5', done: false, auto: false, valueOwner: 'user', target: { w: '185', reps: '5-8', rir: '2' } }] }] };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const file = path.resolve(root, '.' + (new URL(req.url, 'http://localhost').pathname.replace(/^\/$/, '/index.html')));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/`;

// Installed before the app loads. visibilityState is made controllable so the test can play the OS.
const stub = () => {
    const log = window.__wake = { requests: 0, releases: 0, active: 0, sentinels: [] };
    let hidden = false;
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => hidden ? 'hidden' : 'visible' });
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
    window.__setHidden = value => { hidden = value; document.dispatchEvent(new Event('visibilitychange')); };
    const makeSentinel = () => {
        const target = new EventTarget();
        const sentinel = Object.assign(target, { released: false, type: 'screen',
            release: async () => { if (sentinel.released) return; sentinel.released = true; log.releases++; log.active--; target.dispatchEvent(new Event('release')); } });
        return sentinel;
    };
    Object.defineProperty(navigator, 'wakeLock', { configurable: true, value: {
        request: async type => { if (type !== 'screen') throw new Error('bad type'); log.requests++; log.active++; const s = makeSentinel(); log.sentinels.push(s); return s; }
    } });
    // What Android does when the page is hidden: drop the lock without asking.
    window.__osDrop = () => log.sentinels.filter(s => !s.released).forEach(s => s.release());
};

let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    const open = async (pref) => {
        const context = await browser.createBrowserContext();
        const page = await context.newPage(); const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setViewport({ width: 393, height: 852, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
        await page.evaluateOnNewDocument(stub);
        await page.evaluateOnNewDocument((store, snapshot, pref) => {
            if (localStorage.getItem('b880-seeded')) return;
            localStorage.setItem('b880-seeded', '1'); localStorage.setItem('wpb:v1', JSON.stringify(store)); localStorage.setItem('wpb:live', JSON.stringify(snapshot));
            if (pref !== null) localStorage.setItem('wpb:keep-awake', pref);
        }, initial, live, pref);
        await page.goto(url, { waitUntil: 'networkidle0' });
        return { context, page, errors };
    };
    const wake = page => page.evaluate(() => ({ requests: window.__wake.requests, releases: window.__wake.releases, active: window.__wake.active }));

    // 1–3. Default preference: acquire on open, re-acquire after an OS drop, release on leaving.
    {
        const { context, page, errors } = await open(null);
        assert.deepEqual(await wake(page), { requests: 0, releases: 0, active: 0 }, 'no lock outside a workout');
        await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.waitForSelector('.wpb-workout');
        await page.waitForFunction(() => window.__wake.active === 1);
        console.log('PASS 1 opening a workout holds one screen wake lock.');

        await page.evaluate(() => { window.__setHidden(true); window.__osDrop(); });
        assert.equal((await wake(page)).active, 0);
        await page.evaluate(() => window.__setHidden(false));
        await page.waitForFunction(() => window.__wake.active === 1 && window.__wake.requests === 2);
        console.log('PASS 2 returning to the app re-acquires the lock the OS dropped.');

        await page.click('button[aria-label="Workout options"]');
        const minimize = await page.waitForSelector('xpath/.//button[contains(., "Minimize workout")]');
        await minimize.click();
        await page.waitForFunction(() => !document.querySelector('.wpb-workout'));
        await page.waitForFunction(() => window.__wake.active === 0);
        assert.deepEqual(errors, [], 'no runtime errors');
        console.log('PASS 3 leaving the workout releases the lock.');
        await context.close();
    }

    // 4. Preference off: no request at all.
    {
        const { context, page, errors } = await open('0');
        await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.waitForSelector('.wpb-workout');
        await new Promise(resolve => setTimeout(resolve, 400));
        assert.equal((await wake(page)).requests, 0, 'the lifter turned keep-screen-on off');
        assert.deepEqual(errors, []);
        console.log('PASS 4 the off preference is honoured.');
        await context.close();
    }

    // 5. Settings toggle writes the device preference and reflects it.
    {
        const { context, page, errors } = await open(null);
        await page.click('[data-tab="settings"]');
        const sel = 'button[role="switch"][aria-label="Keep screen on during workouts"]';
        await page.waitForSelector(sel);
        assert.equal(await page.$eval(sel, el => el.getAttribute('aria-checked')), 'true');
        const switchGeometry=()=>page.$eval(sel,button=>{
            const track=button.querySelector('.wpb-settings-switch-track').getBoundingClientRect();
            const thumb=button.querySelector('.wpb-settings-switch-thumb').getBoundingClientRect();
            return {hit:button.getBoundingClientRect().height,width:track.width,height:track.height,
                contained:thumb.left>=track.left&&thumb.right<=track.right&&thumb.top>=track.top&&thumb.bottom<=track.bottom};
        });
        const assertSwitch=async()=>{
            const box=await switchGeometry();
            assert.ok(box.hit>=48&&box.width===44&&box.height===26&&box.contained,
                'Switch track, thumb and hit target retain independent geometry: '+JSON.stringify(box));
        };
        await assertSwitch();
        await page.$eval(sel, el => el.scrollIntoView({ block: 'center' }));
        await page.click(sel);
        await page.waitForFunction(s => document.querySelector(s).getAttribute('aria-checked') === 'false', {}, sel);
        assert.equal(await page.evaluate(() => localStorage.getItem('wpb:keep-awake')), '0');
        await assertSwitch();
        const stored = await page.evaluate(() => localStorage.getItem('wpb:v1'));
        assert.ok(!/keepAwake|keep-awake/.test(stored), 'device preference stays out of the synced store');
        assert.deepEqual(errors, []);
        console.log('PASS 5 Settings toggles the per-device keep-screen-on preference.');
        await context.close();
    }
} finally {
    await browser?.close();
    server.close();
}
