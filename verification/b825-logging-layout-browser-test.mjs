import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const program = { id: 'b825-logging', custom: true, weeks: 6,
    config: { unit: 'lb', goal: 'both', experience: 'intermediate', progression: 'double', deload: false },
    days: [{ id: 'lower', label: 'Lower', primaryIndex: 0, exercises: ['back-squat', 'inc-curl'] }],
    overrides: { 'lower:0': { sets: 4, reps: '5-8', rir: '2', rest: 120 }, 'lower:1': { sets: 2, reps: '10-15', rir: '2', rest: 90 } } };
const history = [{ id: 'last', programId: program.id, dayId: 'lower', date: 1, unit: 'lb',
    perf: { 'back-squat': { weight: 215, reps: 3, sets: [205, 210, 215].map((w, i) => ({ w, r: i === 1 ? 2 : 3, done: true, rir: i === 2 ? 0 : 2, rirReported: i === 2 })) } } }];
const live = { schemaVersion: 2, programId: program.id, dayId: 'lower', weekIndex: 1,
    dayExSig: 'back-squat|inc-curl', exIdx: 0, elapsedMs: 120000, readiness: { label: 'Normal', factor: 1 },
    restPaused: false, restRemain: 0, restMax: 0, runPaused: true,
    data: [{ id: 'back-squat', slot: 0, sets: Array.from({ length: 4 }, () => ({ weight: '190', reps: '5', done: false, auto: false, valueOwner: 'user', target: { w: '190', reps: '5-8', rir: '2' } })) },
        { id: 'inc-curl', slot: 1, sets: Array.from({ length: 2 }, () => ({ weight: '30', reps: '10', done: false, auto: false, valueOwner: 'user', target: { w: '30', reps: '10-15', rir: '2' } })) }] };
const store = { v: 13, savedAt: 1, saved: [program], cycles: [], history, perf: history[0].perf,
    drafts: {}, tombs: {}, seenIntro: 999, seenWhatsNew: 999, pinnedId: program.id, theme: 'amethyst',
    restAutoStart: true, warmupCard: false, unit: 'lb', unitChosen: true, experience: 'intermediate',
    bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const file = path.resolve(root, '.' + pathname);
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8784, '127.0.0.1', resolve));
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    const page = await browser.newPage(), errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.evaluateOnNewDocument((store, live) => {
        if (location.origin !== 'http://127.0.0.1:8784' || window !== window.top) return;
        localStorage.setItem('wpb:v1', JSON.stringify(store));
        localStorage.setItem('wpb:live', JSON.stringify(live));
    }, store, live);
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
    const settle = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const geometry = () => page.evaluate(() => {
        const rect = n => { const r = n.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; };
        const scroller = document.querySelector('.wpb-workout-scroll');
        return { scrollTop: scroller.scrollTop, rows: Array.from(document.querySelectorAll('[data-testid^="set-0-"]')).map(row => ({
            box: rect(row), weight: rect(row.querySelector('input[aria-label="weight"]')), reps: rect(row.querySelector('input[aria-label="reps"]')), check: rect(row.querySelector('.wpb-set-actions>button'))
        })), next: rect(document.querySelector('.wpb-workout-footer button[aria-label="Next exercise"]')) };
    });
    const stable = (before, after, label, vertical = true) => {
        for (let i = 0; i < before.rows.length; i++) for (const part of ['box', 'weight', 'reps', 'check']) {
            for (const key of vertical ? ['x', 'y', 'width', 'height'] : ['x', 'width', 'height'])
                assert.ok(Math.abs(before.rows[i][part][key] - after.rows[i][part][key]) < 1, `${label}: set ${i + 1} ${part}.${key} shifted`);
        }
    };
    const visibleFooter = () => page.$eval('.wpb-workout-footer', footer => {
        const buttons = Array.from(footer.querySelectorAll('button'));
        return buttons.every(button => { const r = button.getBoundingClientRect(); return r.width > 0 && r.left >= 0 && r.right <= innerWidth + 1 && r.top >= 0 && r.bottom <= innerHeight + 1; });
    });
    for (const width of [320, 360, 390, 430]) {
        await page.setViewport({ width, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
        await page.goto('http://127.0.0.1:8784/index.html', { waitUntil: 'networkidle0' });
        await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.waitForSelector('[data-testid="set-0-3"]');
        await page.waitForFunction(() => !document.body.innerText.includes('Resumed your in-progress workout'));
        assert.equal(await page.$eval('.wpb-workout-suggestion', n => n.open), false, 'long advice starts collapsed');
        await page.click('.wpb-workout-suggestion summary');
        assert.match(await page.$eval('.wpb-workout-suggestion', n => n.innerText), /rep floor|rep-floor|below|range/);
        await page.click('.wpb-workout-suggestion summary');
        await settle();
        const initial = await geometry();
        await page.click('button[aria-label="Show previous workout values"]'); await settle();
        const last = await geometry();
        stable(initial, last, `TARGET to LAST at ${width}px`);
        assert.match(await page.$eval('[data-testid="set-0-0"]', n => n.innerText), /205×3/);
        await page.click('button[aria-label="Show prescribed targets"]'); await settle();
        stable(last, await geometry(), `LAST to TARGET at ${width}px`);
        assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="weight"]', n => n.value), '190', 'reference toggles cannot rewrite typed values');
        assert.ok(await page.$$eval('.wpb-set-reference-value', nodes => nodes.every(n => n.scrollWidth <= n.clientWidth + 1)), 'target summaries fit the reference track');
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b825-before-log-phone.png') });

        await page.click('[data-testid="set-0-0"] button[aria-label="Mark set done"]');
        await page.waitForSelector('[data-rest-card]'); await settle();
        assert.ok(await page.$('.wpb-workout-footer button[aria-label="Next exercise"]'), 'rest must retain exercise navigation');
        assert.ok(await visibleFooter(), `timer and navigation fit at ${width}px`);
        const logged = await geometry();
        stable(initial, logged, `log set at ${width}px`, false);
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b825-effort-picker-phone.png') });
        assert.ok(Math.abs(initial.next.y - logged.next.y) < 1, 'Next exercise stays in its bottom slot');
        const picker = '[data-testid="set-0-0"] button[aria-label="3 reps left"]';
        await page.waitForSelector(picker); await page.click(picker); await settle();
        const effort = await geometry();
        stable(logged, effort, `log effort at ${width}px`);
        const snapshot = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));
        assert.equal(snapshot.data[0].sets[0].actualRIR, 3); assert.equal(snapshot.data[0].sets[0].weight, '190'); assert.equal(snapshot.data[0].sets[0].reps, '5');
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b825-after-log-phone.png') });
        await page.click('.wpb-workout-footer button[aria-label="Next exercise"]');
        await page.waitForSelector('[data-testid="set-1-0"]');
        assert.match(await page.$eval('.wpb-workout-footer-primary', n => n.innerText), /Finish workout/);
        assert.ok(await visibleFooter(), 'Finish workout remains reachable during rest');
        assert.ok(await page.$('[data-rest-card]'), 'moving to the next exercise preserves running rest');
        await page.click('.wpb-workout-footer button[aria-label="Previous exercise"]');
        await page.waitForSelector('[data-testid="set-0-0"]');
        await page.click('[data-testid="set-0-0"] button[aria-label="Mark set not done"]'); await settle();
        assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="weight"]', n => n.value), '190');
        await page.click('button[aria-label="Skip rest"]'); await settle();
        assert.ok(await visibleFooter());
        console.log(`PASS stable logging, LAST/TARGET, RIR, navigation during rest, and undo at ${width}px.`);
    }
    assert.deepEqual(errors, []);
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
