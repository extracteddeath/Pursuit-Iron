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

// Exercise transfer failures and file-read races through the production Settings component.
const slowPath = path.join(tmp, 'slow.txt'), badPath = path.join(tmp, 'unreadable.txt');
const oversizedPath = path.join(tmp, 'oversized.json');
fs.writeFileSync(slowPath, 'delayed old file'); fs.writeFileSync(badPath, 'unreadable');
fs.closeSync(fs.openSync(oversizedPath, 'w')); fs.truncateSync(oversizedPath, 21 * 1024 * 1024);
async function backupContracts(browser, url, width, theme) {
    const context = await browser.createBrowserContext(), page = await context.newPage();
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    try {
        await page.setViewport({ width, height: 900, deviceScaleFactor: 1, isMobile: width < 800, hasTouch: width < 800 });
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        await page.evaluateOnNewDocument(store => {
            localStorage.setItem('wpb:v1', JSON.stringify(store));
            window.backupProbe = { copy: 'fail', share: 'cancel', download: 'fail', copies: 0, shares: 0, downloads: 0, persisted: false, grant: false };
            const probe = window.backupProbe;
            Object.defineProperty(navigator, 'clipboard', { value: { writeText: async text => {
                probe.copies++; probe.clipboardText = text;
                if (probe.copy === 'fail') throw new Error('clipboard denied');
                if (probe.copy === 'hold') await new Promise(resolve => { probe.resolveCopy = resolve; });
            } } });
            Object.defineProperty(navigator, 'canShare', { value: () => true });
            Object.defineProperty(navigator, 'share', { value: async data => {
                probe.shares++; probe.sharedText = await data.files[0].text();
                if (probe.share === 'cancel') throw new DOMException('cancelled', 'AbortError');
                if (probe.share === 'fail') throw new Error('share failed');
            } });
            Object.defineProperty(navigator, 'storage', { value: {
                persisted: async () => probe.persisted,
                estimate: async () => { throw new Error('usage estimate unavailable'); },
                persist: async () => { probe.persisted = probe.grant; return probe.grant; }
            } });
            const createURL = URL.createObjectURL.bind(URL), revokeURL = URL.revokeObjectURL.bind(URL);
            URL.createObjectURL = blob => { if (probe.download === 'fail') throw new Error('download blocked'); return createURL(blob); };
            URL.revokeObjectURL = value => { probe.revoked = (probe.revoked || 0) + 1; revokeURL(value); };
            const click = HTMLAnchorElement.prototype.click;
            HTMLAnchorElement.prototype.click = function () { if (this.download) { probe.downloads++; probe.downloadName = this.download; return; } return click.call(this); };
            const read = File.prototype.text;
            File.prototype.text = function () {
                if (this.name === 'slow.txt') return new Promise(resolve => { probe.resolveFile = resolve; });
                if (this.name === 'unreadable.txt') return Promise.reject(new Error('read failed'));
                return read.call(this);
            };
        }, { ...initial, theme, lastBackup: 1 });
        const action = async label => {
            const handle = await page.waitForSelector('xpath/.//button[normalize-space(.)="' + label + '"]');
            await handle.evaluate(el => el.scrollIntoView({ block: 'center' })); await handle.click(); await handle.dispose();
        };
        const stamp = () => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).lastBackup);
        const status = pattern => page.waitForFunction(source => {
            return new RegExp(source).test(document.querySelector('.wpb-backup [role="status"]')?.textContent || '');
        }, {}, pattern.source);
        const layout = async () => {
            const bad = await page.$$eval('.wpb-backup button', elements => elements.flatMap(el => {
                const rect = el.getBoundingClientRect(), range = document.createRange(); range.selectNodeContents(el);
                const label = range.getBoundingClientRect();
                return rect.height < 47.9 || rect.width < 47.9 || rect.left < -1 || rect.right > innerWidth + 1 ||
                    label.left < rect.left - 1 || label.right > rect.right + 1 || label.bottom > rect.bottom + 1 ?
                    [{ text: el.textContent, rect: rect.toJSON(), label: label.toJSON() }] : [];
            }));
            assert.deepEqual(bad, [], 'Full labels and shared 48px targets at ' + width + 'px');
        };
        await page.goto(url, { waitUntil: 'networkidle0' }); await page.click('[data-tab="settings"]');
        await page.waitForSelector('.wpb-storage-status');
        await action('Protect');
        await page.waitForFunction(() => document.querySelector('.wpb-storage-status [role="status"]')?.textContent.includes("hasn't granted"));
        assert.match(await page.$eval('.wpb-storage-status', el => el.textContent), /Best effort/);
        await page.evaluate(() => { window.backupProbe.grant = true; }); await action('Protect');
        await page.waitForFunction(() => document.querySelector('.wpb-storage-status')?.textContent.includes('Data protection: Protected'));
        assert.match(await page.$eval('.wpb-storage-status', el => el.textContent), /Usage unavailable/,
            'Rejected estimates do not hide a confirmed persistence grant');
        await layout();
        await action('Export workout log (CSV)'); await action('Copy text'); await status(/Couldn't copy/);
        assert.equal(await stamp(), 1, 'Failed copy never stamps a backup');
        await page.evaluate(() => { window.backupProbe.copy = 'ok'; }); await action('Copy text'); await status(/CSV copied/);
        assert.equal(await stamp(), 1, 'Partial CSV never stamps a full backup'); await action('Done');
        await action('Save backup'); await action('Share backup'); await status(/cancelled/);
        assert.equal(await stamp(), 1, 'Cancelling the OS share sheet never stamps a backup');
        await page.evaluate(() => { window.backupProbe.share = 'fail'; });
        await action('Share backup'); await status(/Couldn't download/);
        assert.equal(await stamp(), 1, 'Failed share and failed fallback never stamp a backup');
        await page.evaluate(() => { window.backupProbe.copy = 'hold'; });
        await action('Copy text');
        assert.equal(await page.$eval('.wpb-backup', el => el.getAttribute('aria-busy')), 'true');
        assert.ok(await page.$$eval('.wpb-backup button', els => els.every(el => el.disabled)), 'All competing transfers disable while an operation is pending');
        const copies = await page.evaluate(() => window.backupProbe.copies);
        await page.evaluate(() => [...document.querySelectorAll('.wpb-backup button')].find(el => el.textContent === 'Copy text').click());
        assert.equal(await page.evaluate(() => window.backupProbe.copies), copies, 'Repeated activation cannot start a second copy');
        await page.evaluate(() => window.backupProbe.resolveCopy()); await status(/Backup copied/);
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).lastBackup > 1);
        const snapshot = await page.$eval('.wpb-backup textarea', el => el.value);
        assert.equal(await page.evaluate(() => window.backupProbe.clipboardText), snapshot, 'Transfer uses the exact visible preview');
        assert.equal(JSON.parse(snapshot).history[0].id, existing.id);
        await page.evaluate(() => { window.backupProbe.download = 'ok'; });
        await action('Share backup'); await status(/Download requested instead/);
        assert.equal(await page.evaluate(() => window.backupProbe.downloads), 1);
        assert.match(await page.evaluate(() => window.backupProbe.downloadName), /pursuit-iron-backup-.*\.json$/);
        await layout();
        await page.evaluate(() => document.documentElement.style.fontSize = '200%'); await layout();
        if (process.env.PI_SCREENSHOTS) {
            await page.$eval('.wpb-backup', el => el.scrollIntoView({ block: 'start' }));
            await page.screenshot({ path: path.join(process.env.PI_SCREENSHOTS, 'backup-' + width + '-' + theme + '.png') });
        }
        await page.evaluate(() => document.documentElement.style.fontSize = ''); await action('Done');
        await action('Restore backup');
        const input = await page.$('.wpb-backup input[type="file"]');
        await input.uploadFile(csvPath); await page.waitForSelector('[data-csv-import-summary]');
        await input.uploadFile(oversizedPath); await status(/20 MB/);
        assert.equal(await page.$('[data-csv-import-summary]'), null, 'Oversized selection clears the prior executable preview');
        assert.equal(await page.$eval('.wpb-backup textarea', el => el.value), '');
        await input.uploadFile(badPath); await status(/Couldn't read/);
        await input.uploadFile(slowPath); await status(/Reading file/);
        await input.uploadFile(csvPath); await page.waitForSelector('[data-csv-import-summary]');
        await page.evaluate(() => window.backupProbe.resolveFile('stale file must not win'));
        assert.equal(await page.$eval('.wpb-backup textarea', el => el.value), STRONG, 'An earlier slow file cannot replace the most recent selection');
        await input.uploadFile(slowPath); await status(/Reading file/);
        await page.type('.wpb-backup textarea', 'manual draft');
        await page.evaluate(() => window.backupProbe.resolveFile('stale after paste'));
        assert.equal(await page.$eval('.wpb-backup textarea', el => el.value), 'manual draft', 'Typing supersedes pending file reads');
        await input.uploadFile(slowPath); await status(/Reading file/); await action('Cancel');
        await page.evaluate(() => window.backupProbe.resolveFile('stale after close'));
        await action('Restore backup');
        assert.equal(await page.$eval('.wpb-backup textarea', el => el.value), '', 'Closing invalidates pending reads');
        await page.evaluate(() => document.documentElement.style.fontSize = '200%'); await layout();
        assert.equal((await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).history)).length, 1, 'Transfer/preview failures never mutate records');
        assert.deepEqual(errors, []);
        console.log('PASS backup safety ' + width + 'px ' + theme + ': grants, failures, cancellation, CSV distinction, single transfer, stale reads and 200% labels.');
    } finally { await context.close(); }
}

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
    for (const [width, theme] of [[320, 'rosewater'], [393, 'rose'], [834, 'rosewater']]) await backupContracts(browser, url, width, theme);
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
