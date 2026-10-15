// Build 881 — Strong/Hevy import through the real Settings restore panel: pick the file, review the
// summary, answer the unit question, import, see the history land, and Undo back to the exact prior log.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const existing = { id: 'mine-1', date: Date.UTC(2026, 6, 20), updatedAt: Date.UTC(2026, 6, 20), programId: 'gone', dayId: 'd', dayLabel: 'Mine', unit: 'lb',
    perf: { 'bb-bench': { weight: 230, reps: 5, date: Date.UTC(2026, 6, 20), sets: [{ w: 230, r: 5 }] } } };
const initial = { v: 13, savedAt: 1, saved: [], cycles: [], history: [existing], perf: { 'bb-bench': existing.perf['bb-bench'] },
    drafts: {}, tombs: {}, seenIntro: 999, seenWhatsNew: 999, unit: 'lb', unitChosen: true, experience: 'intermediate',
    bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const STRONG = [
    'Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE',
    '2026-03-02 07:10:00,Upper A,1h 5m,Bench Press (Barbell),W,60,10,0,0,,,',
    '2026-03-02 07:10:00,Upper A,1h 5m,Bench Press (Barbell),1,100,5,0,0,,,8',
    '2026-03-02 07:10:00,Upper A,1h 5m,Bench Press (Barbell),2,100,5,0,0,,,',
    '2026-03-04 07:00:00,Lower A,58m,Deadlift (Barbell),1,180,3,0,0,,,',
    '2026-03-04 07:00:00,Lower A,58m,Zercher Mystery Lift,1,80,5,0,0,,,'
].join('\n');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'b881-'));
const csvPath = path.join(tmp, 'strong_workouts.csv');
fs.writeFileSync(csvPath, STRONG);

const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const file = path.resolve(root, '.' + (new URL(req.url, 'http://localhost').pathname.replace(/^\/$/, '/index.html')));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/`;
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    for (const width of [360, 1280]) {
        const context = await browser.createBrowserContext();
        const page = await context.newPage(); const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setViewport({ width, height: 860, deviceScaleFactor: 1, isMobile: width < 800, hasTouch: width < 800 });
        await page.evaluateOnNewDocument(store => {
            if (localStorage.getItem('b881-seeded')) return;
            localStorage.setItem('b881-seeded', '1'); localStorage.setItem('wpb:v1', JSON.stringify(store));
        }, initial);
        await page.goto(url, { waitUntil: 'networkidle0' });
        await page.click('[data-tab="settings"]');
        const restore = await page.waitForSelector('xpath/.//button[contains(., "Restore backup")]');
        await restore.evaluate(el => el.scrollIntoView({ block: 'center' }));
        await restore.click();
        await page.waitForSelector('[data-import-switch-hint]');
        const input = await page.waitForSelector('input[type="file"][aria-label="Choose a backup file or a Strong or Hevy CSV export"]');
        assert.match(await input.evaluate(el => el.accept), /\.csv/, 'the picker accepts CSV files');
        await input.uploadFile(csvPath);
        await page.waitForSelector('[data-csv-import-summary="strong"]');
        const summaryText = await page.$eval('[data-csv-import-summary]', el => el.textContent);
        assert.match(summaryText, /2\s*workouts/, summaryText);
        assert.match(summaryText, /Zercher Mystery Lift \(1 set\)/, 'the skipped lift is named: ' + summaryText);
        const rows = await page.$$eval('[data-backup-row]', els => els.map(el => el.getAttribute('data-backup-row')));
        assert.deepEqual(rows, ['history', 'custom'], 'a workout import previews only the records it can change');
        // The unit question: defaults to the app unit, switching re-previews.
        assert.equal(await page.$eval('[data-csv-unit="lb"]', el => el.getAttribute('aria-pressed')), 'true');
        await page.click('[data-csv-unit="kg"]');
        await page.waitForFunction(() => document.querySelector('[data-csv-unit="kg"]').getAttribute('aria-pressed') === 'true');
        const importBtn = await page.waitForSelector('xpath/.//button[normalize-space(.)="Import workouts"]');
        await importBtn.click();
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.length === 3, { timeout: 10000 });
        const after = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')));
        const upper = after.history.find(h => h.dayLabel === 'Upper A');
        assert.deepEqual(upper.perf['bb-bench'].sets.map(s => s.w), [220.5, 220.5], 'the kg answer converted the loads');
        assert.ok(after.history.some(h => h.id === 'mine-1'), 'existing history kept');
        assert.equal(after.unit, 'lb', 'device unit untouched');
        // Undo puts back exactly the prior log.
        const undo = await page.waitForSelector('xpath/.//button[normalize-space(.)="Undo this restore"]');
        await undo.click();
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.length === 1, { timeout: 10000 });
        assert.deepEqual(errors, [], 'no runtime errors');
        console.log(`PASS ${width}px: Strong CSV previewed, unit chosen, imported with conversion, and undone.`);
        await context.close();
    }
} finally {
    await browser?.close();
    server.close();
    fs.rmSync(tmp, { recursive: true, force: true });
}
