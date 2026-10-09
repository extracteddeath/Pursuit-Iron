import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// Keep the original compact layout; completed rows retain muted, locked logged values.

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
    page.on('pageerror', error => { errors.push(error.message); console.error(error.message); });
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
        return { scrollTop: scroller.scrollTop, rows: Array.from(document.querySelectorAll('[data-testid^="set-0-"][data-done="0"]')).map(row => ({
            box: rect(row), controls: rect(row.querySelector('.wpb-set-controls')), weight: rect(row.querySelector('input[aria-label="weight"]')), reps: rect(row.querySelector('input[aria-label="reps"]')), check: rect(row.querySelector('.wpb-set-actions>button'))
        })), next: rect(document.querySelector('.wpb-workout-footer button[aria-label="Next exercise"]')) };
    });
    const usableRows = async label => {
        const layout = await geometry();
        for (const [i, row] of layout.rows.entries()) {
            assert.ok(row.weight.width >= 20 && row.reps.width >= 20, `${label}: set ${i + 1} has usable fields`);
            assert.ok(row.weight.x + row.weight.width <= row.reps.x, `${label}: fields do not overlap`);
            // The compact check keeps its tap target inside its own horizontal track.
            assert.ok(row.reps.x + row.reps.width <= row.check.x, `${label}: check stays to the right`);
            assert.ok(row.weight.x >= 0 && row.check.x + row.check.width <= await page.evaluate(() => innerWidth) + 1, `${label}: controls fit the phone`);
        }
    };
    const visibleFooter = () => page.$eval('.wpb-workout-footer', footer => {
        const buttons = Array.from(footer.querySelectorAll('button'));
        return buttons.every(button => { const r = button.getBoundingClientRect(); return r.width > 0 && r.left >= 0 && r.right <= innerWidth + 1 && r.top >= 0 && r.bottom <= innerHeight + 1; });
    });
    for (const width of [320, 360, 390, 430, 520]) {
        await page.setViewport({ width, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
        await page.goto('http://127.0.0.1:8784/index.html', { waitUntil: 'networkidle0' });
        await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.waitForSelector('[data-testid="set-0-3"]');
        await page.waitForFunction(() => !document.body.innerText.includes('Resumed your in-progress workout'));
        await settle();
        const initial = await geometry();
        assert.ok(initial.rows.slice(1).every(row => row.box.height - row.controls.height <= 8), 'pending rows contain only their controls and normal padding');
        assert.equal(await page.$$eval('.wpb-set-detail--work', nodes => nodes.length), 0, 'reserved-height detail wrappers are removed');
        await usableRows(`TARGET at ${width}px`);
        const targets = await page.$eval('[data-testid="set-0-0"]', row => {
            const buttons = [...row.querySelectorAll('.wpb-set-stepper, .wpb-set-complete')];
            return buttons.map(n => { const r = n.getBoundingClientRect(); const hit = document.elementFromPoint(r.x + r.width - 1, r.y + r.height / 2); return { width: r.width, owned: hit === n || n.contains(hit) }; });
        });
        assert.ok(targets.every(t => t.width === 30 && t.owned), 'each 30px button owns its whole horizontal track');
        for (const [label, field, delta] of [['weight up', 'weight', 5], ['weight down', 'weight', -5], ['reps up', 'reps', 1], ['reps down', 'reps', -1]]) {
            const input = `[data-testid="set-0-0"] input[aria-label="${field}"]`;
            const before = Number(await page.$eval(input, n => n.value));
            await page.tap(`[data-testid="set-0-0"] button[aria-label="${label}"]`);
            assert.equal(Number(await page.$eval(input, n => n.value)), before + delta, 'a tap changes exactly one increment');
        }
        await page.click('button[aria-label="Show previous workout values"]'); await settle();
        await usableRows(`LAST at ${width}px`);
        assert.match(await page.$eval('[data-testid="set-0-0"]', n => n.innerText), /205×3/);
        assert.match(await page.$eval('[data-testid="set-0-0"] button[aria-label="Use last time\'s 205 by 3"]', n => n.innerText), /Last time\s+205×3/);
        assert.match(await page.$eval('[data-testid="set-0-3"]', n => n.innerText), /new/);
        await page.click('button[aria-label="Show prescribed targets"]'); await settle();
        await usableRows(`back to TARGET at ${width}px`);
        assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="weight"]', n => n.value), '190', 'reference toggles cannot rewrite typed values');
        const weightField = '[data-testid="set-0-0"] input[aria-label="weight"]';
        const repsField = '[data-testid="set-0-0"] input[aria-label="reps"]';
        const selected = selector => page.$eval(selector, input => [input.selectionStart, input.selectionEnd]);
        for (const [selector, replacement] of [[weightField, '205.5'], [repsField, '6']]) {
            await page.tap(selector);
            const length = await page.$eval(selector, input => input.value.length);
            assert.deepEqual(await selected(selector), [0, length], `tap selects the whole value at ${width}px`);
            await page.keyboard.type(replacement);
            assert.equal(await page.$eval(selector, input => input.value), replacement, 'typing replaces the value without Select All');
        }
        await page.click('[data-testid="set-0-0"] button[aria-label="weight up"]');
        assert.ok(Number(await page.$eval(weightField, input => input.value)) > 205.5, 'steppers continue from the typed decimal');
        await page.focus('[data-testid="set-0-0"] button[aria-label="weight down"]');
        await page.keyboard.press('Tab');
        assert.deepEqual(await selected(weightField), [0, await page.$eval(weightField, input => input.value.length)], 'keyboard focus selects the value');
        await page.keyboard.press('Backspace');
        assert.equal(await page.$eval(weightField, input => input.value), '', 'a selected value can still be cleared');
        await page.keyboard.type('190');
        await page.tap(repsField); await page.keyboard.type('5');
        await page.waitForFunction(() => {
            const set = JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0];
            return set.weight === '190' && set.reps === '5';
        });
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b829-before-log-phone.png') });

        await page.click('[data-testid="set-0-0"] button[aria-label="Mark set done"]');
        await page.waitForSelector('[data-rest-card]'); await settle();
        assert.ok(await page.$('.wpb-workout-footer button[aria-label="Next exercise"]'), 'rest must retain exercise navigation');
        assert.ok(await visibleFooter(), `timer and navigation fit at ${width}px`);
        const logged = await geometry();
        await usableRows(`logged at ${width}px`);
        assert.equal(await page.$$eval('[data-effort-picker]', nodes => nodes.length), 1, 'one reps-left prompt opens after completing a set');
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b829-effort-picker-phone.png') });
        assert.ok(Math.abs(initial.next.y - logged.next.y) < 1, 'Next exercise stays in its bottom slot');
        const picker = '[data-testid="set-0-0"] button[aria-label="3 reps left"]';
        await page.waitForSelector(picker); await page.click(picker); await settle();
        await usableRows(`effort recorded at ${width}px`);
        const snapshot = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));
        assert.equal(snapshot.data[0].sets[0].actualRIR, 3); assert.equal(snapshot.data[0].sets[0].weight, '190'); assert.equal(snapshot.data[0].sets[0].reps, '5');
        await page.waitForFunction(() => getComputedStyle(document.querySelector('[data-testid="set-0-0"]')).backgroundColor === 'rgba(0, 0, 0, 0)');
        const completed = await page.$eval('[data-testid="set-0-0"]', n => ({
            values: [...n.querySelectorAll('input')].map(i => [i.value, i.readOnly]),
            steppers: n.querySelectorAll('.wpb-set-stepper').length,
            background: getComputedStyle(n).backgroundColor,
            undoShadow: getComputedStyle(n.querySelector('button[aria-label="Mark set not done"]')).boxShadow,
            textColor: getComputedStyle(n.querySelector('input')).color,
            number: n.querySelector('.wpb-set-controls>span').textContent,
            weightCenter: (() => { const r=n.querySelector('input[aria-label="weight"]').getBoundingClientRect();return r.x+r.width/2; })()
        }));
        assert.deepEqual(completed.values, [['190', true], ['5', true]], 'logged values remain visible and locked');
        assert.equal(completed.number, '1', 'completed set stays identifiable');
        assert.equal(completed.steppers, 0, 'logged values cannot change through a stepper');
        assert.equal(completed.background, 'rgba(0, 0, 0, 0)', 'completed row has no bright background');
        assert.equal(completed.undoShadow, 'none', 'completed button has no accent glow');
        assert.ok(Math.abs(completed.weightCenter - (initial.rows[0].weight.x + initial.rows[0].weight.width / 2)) <= 5, 'logging keeps the load aligned');
        await page.click('button[aria-label="Show previous workout values"]');
        assert.equal(await page.$$eval('[data-testid="set-0-0"] .wpb-set-controls button', ns => ns.length), 1, 'completed references are read-only and Undo remains available');
        await page.click('button[aria-label="Show prescribed targets"]');
        await page.tap('[data-testid="set-0-0"] input[aria-label="weight"]');
        await page.keyboard.type('999');
        assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="weight"]', n => n.value), '190', 'typing cannot rewrite a completed set');
        // Completing another set retires the older picker, while its effort and Undo stay reachable.
        await page.click('[data-testid="set-0-1"] button[aria-label="Mark set done"]'); await settle();
        assert.equal(await page.$$eval('[data-testid="set-0-0"] [data-effort-picker]', nodes => nodes.length), 0);
        assert.equal(await page.$$eval('[data-testid="set-0-1"] [data-effort-picker]', nodes => nodes.length), 1);
        assert.equal(await page.$$eval('[data-testid="set-0-0"] [data-effort-tag]', nodes => nodes.length), 1);
        await page.click('[data-testid="set-0-0"] [data-effort-tag]'); await settle();
        await page.click('[data-testid="set-0-0"] button[aria-label="2 reps left"]'); await settle();
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].actualRIR), 2, 'older effort stays editable');
        await page.click('[data-testid="set-0-1"] button[aria-label="Mark set not done"]'); await settle();
        assert.equal(await page.$eval('[data-testid="set-0-1"] input[aria-label="weight"]', n => n.value), '190', 'Undo preserves the logged weight');
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b829-after-log-phone.png') });
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
        await page.click('button[aria-label="Focused mode"]');
        await page.waitForSelector('.wpb-focus-value-input');
        for (const [label, replacement] of [['weight', '195'], ['reps', '6']]) {
            const selector = `.wpb-focus-value-input[aria-label="${label}"]`;
            await page.tap(selector);
            assert.deepEqual(await selected(selector), [0, await page.$eval(selector, input => input.value.length)], 'Focus mode selects on tap');
            await page.keyboard.type(replacement);
            assert.equal(await page.$eval(selector, input => input.value), replacement);
        }
        await page.click('button[aria-label="Exit focused mode"]');
        await page.waitForSelector('[data-testid="set-0-0"]');
        assert.equal(await page.$eval(weightField, input => input.value), '195');
        assert.equal(await page.$eval(repsField, input => input.value), '6');
        console.log(`PASS restored compact rows, tap-to-select, Focus entry, LAST/TARGET, RIR, navigation during rest, and undo at ${width}px.`);
    }
    await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await page.goto('http://127.0.0.1:8784/index.html', { waitUntil: 'networkidle0' });
    await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.waitForSelector('[data-testid="set-0-3"]');
    await page.waitForFunction(() => !document.body.innerText.includes('Resumed your in-progress workout'));
    for (let i = 0; i < 4; i++) await page.click(`[data-testid="set-0-${i}"] button[aria-label="Mark set done"]`);
    const loggedRows = await page.$$eval('[data-testid^="set-0-"][data-done="1"]', ns => ns.map(n => ({
        number: n.querySelector('.wpb-set-controls>span').textContent,
        values: [...n.querySelectorAll('input')].map(i => [i.value, i.readOnly]),
        undo: !!n.querySelector('button[aria-label="Mark set not done"]'),
        effort: !!n.querySelector('.wpb-completed-effort')
    })));
    assert.deepEqual(loggedRows.map(r => r.number), ['1', '2', '3', '4']);
    assert.ok(loggedRows.every(r => JSON.stringify(r.values) === JSON.stringify([['190', true], ['5', true]]) && r.undo && r.effort));
    assert.ok(await visibleFooter(), 'all sets complete still retains rest and navigation');
    await settle();
    await page.screenshot({ path: path.join(root, 'verification/b829-all-completed-phone.png') });
    await page.click('[data-testid="set-0-2"] button[aria-label="Mark set not done"]');
    assert.equal(await page.$eval('[data-testid="set-0-2"] input[aria-label="weight"]', n => n.readOnly), false);
    assert.equal(await page.$eval('[data-testid="set-0-2"] input[aria-label="weight"]', n => n.value), '190');
    assert.equal(await page.$eval('[data-testid="set-0-2"]', n => n.getAttribute('data-active')), '1');
    console.log('PASS screenshot regression: all four completed rows keep their set number, load/reps, effort and Undo; reopening restores editing.');
    assert.deepEqual(errors, []);
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
