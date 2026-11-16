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
const backupPath = path.join(tmp, 'backup.json');
fs.writeFileSync(backupPath, JSON.stringify({ ...initial, history: [existing,
    { ...existing, id: 'restored-2', date: existing.date + 86400000, updatedAt: existing.date + 86400000 }] }));

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
        const reviewLayout = async kind => {
            const selectors = ['[aria-label="Backup merge impact"]', '[data-import-switch-hint]',
                ...(kind === 'csv' ? ['[data-csv-import-summary]', '[data-csv-unit="kg"]'] : [])];
            const fonts = () => page.evaluate(selectors => selectors.map(selector =>
                parseFloat(getComputedStyle(document.querySelector(selector)).fontSize)), selectors);
            const normal = await fonts();
            if (process.env.PI_SCREENSHOTS) {
                await page.$eval('[aria-label="Backup merge impact"]', el => el.scrollIntoView({ block: 'center' }));
                await page.screenshot({ path: path.join(process.env.PI_SCREENSHOTS, `backup-review-${kind}-${width}-${theme}-100.png`) });
            }
            const impact = await page.$$eval('[data-backup-row]', rows => rows.map(row => ({
                key: row.dataset.backupRow, label: row.querySelector('th').textContent,
                counts: [...row.querySelectorAll('td')].map(cell => cell.lastElementChild?.textContent || cell.textContent)
            })));
            await page.evaluate(() => document.documentElement.style.fontSize = '200%');
            const enlarged = await fonts();
            await page.$eval('[aria-label="Backup merge impact"]', el => el.scrollIntoView({ block: 'center' }));
            if (process.env.PI_SCREENSHOTS)
                await page.screenshot({ path: path.join(process.env.PI_SCREENSHOTS, `backup-review-${kind}-${width}-${theme}-200.png`) });
            assert.ok(enlarged.every((font, index) => font >= normal[index] * 1.9),
                'Restore review and unit choice must follow 200% text: ' + JSON.stringify({ normal, enlarged }));
            await layout();
            const overflow = await page.$$eval('.wpb-backup table, .wpb-backup tbody th, .wpb-backup td, [data-csv-import-summary]', nodes => nodes.flatMap(node => {
                const r = node.getBoundingClientRect();
                return r.left < -1 || r.right > innerWidth + 1 || node.scrollWidth > node.clientWidth + 1
                    ? [{ text: node.textContent, width: r.width, scroll: node.scrollWidth }] : [];
            }));
            assert.deepEqual(overflow, [], 'Restore impact and CSV summary stay readable inside the viewport');
            const client = await page.createCDPSession();
            const { nodes } = await client.send('Accessibility.getFullAXTree');
            const table = nodes.find(node => node.role?.value === 'table' && node.name?.value === 'Backup merge impact');
            assert.ok(table, 'Reflow retains the accessible table');
            const children = node => (node.childIds || []).flatMap(id => {
                const child = nodes.find(candidate => candidate.nodeId === id);
                return child ? [child, ...children(child)] : [];
            });
            const headers = children(table).filter(node => node.role?.value === 'columnheader').map(node => node.name?.value);
            assert.deepEqual(headers, ['Records', 'Add', 'Update', 'Remove'], 'Responsive impact keeps named column associations');
            await client.detach();
            assert.deepEqual(await page.$$eval('[data-backup-row]', rows => rows.map(row => ({
                key: row.dataset.backupRow, label: row.querySelector('th').textContent,
                counts: [...row.querySelectorAll('td')].map(cell => cell.lastElementChild?.textContent || cell.textContent)
            }))), impact, 'Reflow preserves every record/count mapping');
            await page.evaluate(() => document.documentElement.style.fontSize = '');
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
        const reviewInput = await page.$('.wpb-backup input[type="file"]');
        await reviewInput.uploadFile(csvPath); await page.waitForSelector('[data-csv-import-summary]');
        await reviewLayout('csv');
        await reviewInput.uploadFile(backupPath); await page.waitForSelector('[data-backup-row="saved"]');
        await reviewLayout('json');
        await page.evaluate(() => document.documentElement.style.fontSize = '200%'); await layout();
        await action('Confirm restore');
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.length === 2);
        assert.ok(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).history.some(entry => entry.id === 'restored-2')),
            'Reviewed JSON backup adds the intended record at enlarged text');
        await action('Undo this restore');
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.length === 1);
        assert.equal((await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).history)).length, 1, 'Transfer/preview failures never mutate records');
        assert.deepEqual(errors, []);
        console.log('PASS backup safety ' + width + 'px ' + theme + ': grants, transfer failures/cancellation, stale reads, enlarged JSON/CSV previews, accessible counts and JSON restore/Undo.');
    } finally { await context.close(); }
}

// M389: the same history records can be corrected after import or on-device logging.
// Exercise the real persistence boundary, not an isolated form renderer.
async function historyCorrectionContracts(browser, url, width, height, theme, enlarged, historyUnit) {
    const date = Date.now() - 86400000;
    const entry = { id: 'm389-correct', programId: 'earlier-program', dayId: 'upper', weekIndex: 3,
        programName: 'Earlier authored program', dayLabel: 'Upper strength and muscle training', date, updatedAt: 10,
        unit: historyUnit, durationMin: 55, setsDone: 4, volume: 1695,
        perf: { 'bb-bench': { unit: historyUnit, weight: 92.5, reps: 6, date, sets: [
            { w: 90, r: 6, tr: 2 }, { w: 92.5, r: 6, tr: 2 }, { w: 95, r: 5, rir: 1, tr: 2 }, { w: 50, r: 3, sub: true, tr: 2 }
        ] }, 'inc-curl': { unit: historyUnit, weight: 12.5, reps: 10, date, sets: [{ w: 12.5, r: 10, rir: 2 }] } } };
    const other = { ...entry, id: 'm389-other', dayId: 'other-day', date: date - 86400000 };
    const context = await browser.createBrowserContext(), page = await context.newPage(), errors = [];
    page.on('pageerror', error => errors.push(error.message));
    try {
        await page.setViewport({ width, height, isMobile: width < 600, hasTouch: width < 600 });
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        await page.evaluateOnNewDocument(seed => {
            if (localStorage.getItem('m389-seeded')) return;
            localStorage.setItem('m389-seeded', '1');localStorage.setItem('wpb:v1', JSON.stringify(seed));
        }, { ...initial, theme, unit: 'lb', history: [entry, other], perf: entry.perf });
        await page.goto(url, { waitUntil: 'networkidle0' });
        await page.click('[data-tab="progress"]');await page.waitForSelector('[data-history-id="m389-correct"]');
        await page.click('[data-history-id="m389-correct"] .wpb-history-session-toggle');
        const read = () => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')));
        const before = await read();
        const open = async () => {
            await page.click('[data-history-id="m389-correct"] .wpb-history-edit');
            await page.waitForSelector('.wpb-history-editor-sheet', { visible: true });
            if (enlarged) await page.evaluate(() => document.documentElement.style.fontSize = '200%');
            await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
        };
        const action = async label => {
            const button = await page.waitForSelector('xpath/.//button[normalize-space(.)="' + label + '"]');
            await button.click();await button.dispose();
        };
        const type = async (selector, value) => {
            await page.$eval(selector, input => { input.focus();input.select(); });
            await page.keyboard.press('Backspace');if (value) await page.keyboard.type(value);
        };
        const original = before.history.find(item => item.id === entry.id);
        const facts = item => { const copy = structuredClone(item);delete copy.updatedAt;return copy; };
        const layout = async visibleHeight => {
            const result = await page.$eval('.wpb-history-editor-sheet', sheet => {
                const rect = element => element.getBoundingClientRect().toJSON();
                const footer = sheet.querySelector('.wpb-history-editor-footer');
                const buttons = [sheet.querySelector('[aria-label="Close history editor"]'), ...footer.querySelectorAll('button')];
                return { sheet: rect(sheet), parent: rect(sheet.parentElement), insideRow: !!sheet.closest('[data-history-id]'),
                    body: rect(sheet.querySelector('.wpb-history-editor-body')),
                    overflow: sheet.scrollWidth > sheet.clientWidth + 1,
                    named: sheet.parentElement.getAttribute('aria-labelledby') === sheet.querySelector('h2').id,
                    buttons: buttons.map(button => { const r = rect(button), hit = document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);
                        return { ...r, hit: !!hit && (hit === button || button.contains(hit)) }; }) };
            });
            assert.ok(!result.insideRow && result.named && !result.overflow && result.sheet.x >= -1 && result.sheet.right <= width + 1,
                'named history modal escapes virtualized rows and fits the window: ' + JSON.stringify(result));
            assert.ok(result.sheet.y >= -1 && result.sheet.bottom <= visibleHeight + 1 && result.body.height >= 40 &&
                result.buttons.every(button => button.hit && button.width >= 44 && button.height >= 44 && button.y >= -1 && button.bottom <= visibleHeight + 1),
                'Close/Cancel/Save remain visible and pointer-reachable above navigation/keyboard: ' + JSON.stringify(result));
        };
        await open();await layout(height);
        await page.screenshot({ path: path.join(root, `verification/m389-history-${width}-${theme}-phone.png`) });
        const footerTop = await page.$eval('.wpb-history-editor-footer', element => element.getBoundingClientRect().top);
        await page.$eval('.wpb-history-editor-body', element => element.scrollTop = element.scrollHeight);
        assert.ok(Math.abs(await page.$eval('.wpb-history-editor-footer', element => element.getBoundingClientRect().top) - footerTop) < 1,
            'scrolling set fields never displaces the correction actions');
        const load = '#history-editor-bb-bench-0-load', reps = '#history-editor-bb-bench-0-reps', rir = '#history-editor-bb-bench-0-rir';
        assert.match(await page.$eval('.wpb-history-editor-load', label => label.textContent), new RegExp('Load ' + historyUnit));
        for (const [selector, invalid, valid] of [[load, '92..5', '212.5'], [load, '100kg', '212.5'], [load, '1e308', '212.5'],
            [reps, '', '7'], [reps, '7.5', '7'], [rir, '2..5', '2.5'], [rir, '11', '2.5'], ['#history-editor-minutes', '5.5', '60']]) {
            await type(selector, invalid);await action('Save correction');await page.waitForSelector('#history-editor-error');
            assert.deepEqual((await read()).history, before.history, 'invalid/incomplete values never silently remove, round or rewrite stored sets');
            assert.equal(await page.$eval(selector, input => input.value), invalid, 'the typed value remains available to fix');
            await type(selector, valid);
        }
        const glyphFits = await page.$eval(load, input => {
            const style = getComputedStyle(input), canvas = document.createElement('canvas'), ctx = canvas.getContext('2d');
            ctx.font = style.font;
            return ctx.measureText(input.value).width <= input.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight) + 1;
        });
        assert.ok(glyphFits, 'the entire decimal load is visible at normal/enlarged text');
        const previousViewport = await page.evaluate(() => ({ value: document.documentElement.style.getPropertyValue('--visual-h'),
            priority: document.documentElement.style.getPropertyPriority('--visual-h') }));
        const visibleHeight = Math.max(300, Math.round(height * .55));
        await page.evaluate(height => document.documentElement.style.setProperty('--visual-h', height + 'px', 'important'), visibleHeight);
        await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
        await layout(visibleHeight);
        await page.evaluate(previous => previous.value ? document.documentElement.style.setProperty('--visual-h', previous.value, previous.priority)
            : document.documentElement.style.removeProperty('--visual-h'), previousViewport);
        await action('Cancel');await page.waitForSelector('.wpb-history-editor-backdrop', { hidden: true });
        assert.deepEqual((await read()).history, before.history, 'Cancel discards all staged valid and invalid edits');
        await page.waitForFunction(() => document.activeElement === document.querySelector('[data-history-id="m389-correct"] .wpb-history-edit'));
        await open();
        // Exit retains the closing child for motion. A rapid new opening must
        // start from persisted history rather than revive that discarded draft.
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }]);
        await type(load, '999');
        await page.$$eval('.wpb-history-editor-actions button', buttons => buttons.find(button => button.textContent === 'Cancel').click());
        await page.$eval('[data-history-id="m389-correct"] .wpb-history-edit', button => button.click());
        await page.waitForFunction(() => document.querySelector('#history-editor-bb-bench-0-load')?.value === '90');
        assert.equal(await page.$eval(load, input => input.value), '90', 'rapid reopening discards the canceled draft during ordinary exit motion');
        assert.deepEqual((await read()).history, before.history, 'interrupted closing never writes the canceled draft');
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        await type(load, '212.5');await type(reps, '7');await type(rir, '2.5');await type('#history-editor-minutes', '60');
        const changedDate = await page.$eval('#history-editor-date', input => {
            const date = new Date(new Date(input.value).getTime() - 3600000);
            const value = new Date(date.getTime() - date.getTimezoneOffset()*60000).toISOString().slice(0,16);
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
            input.dispatchEvent(new Event('input', { bubbles: true }));return new Date(value).getTime();
        });
        await action('Save correction');await page.waitForSelector('.wpb-history-editor-backdrop', { hidden: true });
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.find(item => item.id === 'm389-correct').perf['bb-bench'].sets[0].w === 212.5);
        const after = await read(), corrected = after.history.find(item => item.id === entry.id);
        assert.deepEqual([corrected.programId,corrected.dayId,corrected.weekIndex,corrected.unit],
            [original.programId,original.dayId,original.weekIndex,original.unit], 'correction preserves structural ownership and the historical unit');
        assert.equal(corrected.perf['bb-bench'].sets.length, 4);assert.equal(corrected.perf['bb-bench'].sets[3].sub, true);
        assert.equal(corrected.perf['bb-bench'].sets[0].tr, 2);assert.equal(corrected.perf['bb-bench'].sets[0].rir, 2.5);
        assert.equal(corrected.setsDone, 4);assert.equal(corrected.volume, 2643);assert.equal(corrected.durationMin, 60);assert.equal(corrected.date, changedDate);
        assert.ok(corrected.updatedAt > original.updatedAt, 'the existing shared stamped setter revisions the correction');
        assert.deepEqual(after.perf['bb-bench'].sets, corrected.perf['bb-bench'].sets, 'the derived performance mirror follows the corrected raw sets');
        assert.deepEqual(after.history.find(item => item.id === other.id), before.history.find(item => item.id === other.id));
        assert.equal(after.unit, 'lb');
        await action('Undo');
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.find(item => item.id === 'm389-correct').perf['bb-bench'].sets[0].w === 90);
        assert.deepEqual(facts((await read()).history.find(item => item.id === entry.id)), facts(original), 'Undo restores the original facts using the shared revision owner');
        await open();
        await page.click('.wpb-history-editor-exercise .wpb-history-editor-remove-set');
        await action('Save correction');await page.waitForSelector('.wpb-history-editor-backdrop', { hidden: true });
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.find(item => item.id === 'm389-correct').perf['bb-bench'].sets.length === 3);
        assert.equal((await read()).history.find(item => item.id === entry.id).setsDone, 3, 'an explicit set removal remains supported');
        await action('Undo');await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.find(item => item.id === 'm389-correct').perf['bb-bench'].sets.length === 4);
        await open();
        await page.click('.wpb-history-editor-add-set');
        assert.equal(await page.$$eval('.wpb-history-editor-set', rows => rows.length), 6, 'set additions remain staged');
        await page.$$eval('.wpb-history-editor-remove-lift', buttons => buttons.forEach(button => button.click()));
        await page.waitForSelector('.wpb-history-editor-empty');
        assert.equal(await page.$eval('.wpb-history-editor-actions button:last-child', button => button.disabled), true, 'the correction form cannot erase a whole workout');
        await page.keyboard.press('Escape');await page.waitForSelector('.wpb-history-editor-backdrop', { hidden: true });
        assert.deepEqual(facts((await read()).history.find(item => item.id === entry.id)), facts(original), 'Escape discards lift removals and added sets');
        await open();await type(load, '200');
        await page.goBack();await page.waitForSelector('.wpb-history-editor-backdrop', { hidden: true });
        assert.ok(await page.$('.wpb-progress'), 'browser Back closes only the correction form');
        assert.deepEqual(facts((await read()).history.find(item => item.id === entry.id)), facts(original), 'browser Back discards the staged correction');
        await page.reload({ waitUntil: 'networkidle0' });
        assert.deepEqual(facts((await read()).history.find(item => item.id === entry.id)), facts(original), 'reload retains restored historical facts');
        assert.deepEqual(errors, []);
        console.log('PASS M389 history correction ' + JSON.stringify({width,height,theme,enlarged,historyUnit}));
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
    for (const args of [[320,740,'rose',true,'kg'],[393,852,'rosewater',false,'lb'],[900,480,'rosewater',true,'kg'],[1280,850,'rose',true,'lb']]) await historyCorrectionContracts(browser, url, ...args);
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
        await page.evaluate(() => document.documentElement.style.fontSize = '200%');
        // The unit question: defaults to the app unit, switching re-previews.
        assert.equal(await page.$eval('[data-csv-unit="lb"]', el => el.getAttribute('aria-pressed')), 'true');
        const unitChoice = await page.$('[data-csv-unit="kg"]');
        await unitChoice.evaluate(el => el.scrollIntoView({ block: 'center' })); await unitChoice.click();
        await page.waitForFunction(() => document.querySelector('[data-csv-unit="kg"]').getAttribute('aria-pressed') === 'true');
        const importBtn = await page.waitForSelector('xpath/.//button[normalize-space(.)="Import workouts"]');
        await importBtn.evaluate(el => el.scrollIntoView({ block: 'center' }));
        await importBtn.click();
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).history.length === 3, { timeout: 10000 });
        const after = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')));
        const upper = after.history.find(h => h.dayLabel === 'Upper A');
        assert.deepEqual(upper.perf['bb-bench'].sets.map(s => s.w), [220.5, 220.5], 'the kg answer converted the loads');
        assert.ok(after.history.some(h => h.id === 'mine-1'), 'existing history kept');
        assert.equal(after.unit, 'lb', 'device unit untouched');
        // Undo puts back exactly the prior log.
        const undo = await page.waitForSelector('xpath/.//button[normalize-space(.)="Undo this restore"]');
        await undo.evaluate(el => el.scrollIntoView({ block: 'center' }));
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
