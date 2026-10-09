import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// Restore the original full set grid: completed rows stay visible and dimmed, never collapsed.
// Logged effort sits under reps inside the grid; completed rows have no extra effort line.

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
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b827-before-log-phone.png') });

        await page.click('[data-testid="set-0-0"] button[aria-label="Mark set done"]');
        await page.waitForSelector('[data-rest-card]'); await settle();
        assert.ok(await page.$('.wpb-workout-footer button[aria-label="Next exercise"]'), 'rest must retain exercise navigation');
        assert.ok(await visibleFooter(), `timer and navigation fit at ${width}px`);
        const logged = await geometry();
        await usableRows(`logged at ${width}px`);
        assert.equal(await page.$$eval('[data-effort-picker]', nodes => nodes.length), 1, 'one reps-left prompt opens after completing a set');
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b827-effort-picker-phone.png') });
        assert.ok(Math.abs(initial.next.y - logged.next.y) < 1, 'Next exercise stays in its bottom slot');
        const picker = '[data-testid="set-0-0"] button[aria-label="3 reps left"]';
        await page.waitForSelector(picker); await page.click(picker); await settle();
        await usableRows(`effort recorded at ${width}px`);
        const snapshot = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));
        assert.equal(snapshot.data[0].sets[0].actualRIR, 3); assert.equal(snapshot.data[0].sets[0].weight, '190'); assert.equal(snapshot.data[0].sets[0].reps, '5');
        // Completed rows retain the same control grid with read-only logged inputs.
        const completedFirst = await page.$eval('[data-testid="set-0-0"]', row => {
            const weight = row.querySelector('input[aria-label="weight"]');
            const reps = row.querySelector('input[aria-label="reps"]');
            const check = row.querySelector('button[aria-label="Mark set not done"]');
            const controls = row.querySelector('.wpb-set-controls');
            return { weight: weight?.value, reps: reps?.value, readonly: weight?.readOnly && reps?.readOnly,
                hasControls: !!controls, opacity: controls && Number(getComputedStyle(controls).opacity),
                checkFill: check && getComputedStyle(check).backgroundColor,
                oldCollapse: row.hasAttribute('data-completed-bar') };
        });
        assert.equal(completedFirst.weight, '190');
        assert.equal(completedFirst.reps, '5');
        assert.equal(completedFirst.readonly, true, 'completed values are protected until Undo');
        assert.equal(completedFirst.hasControls, true, 'original row structure remains');
        assert.ok(completedFirst.opacity < 0.85, 'finished values are visibly faded');
        assert.equal(completedFirst.oldCollapse, false, 'no summary-only completed-set bar');
        assert.notEqual(completedFirst.checkFill, 'rgb(255, 103, 145)', 'completed check must not retain the former bright pink fill');
        assert.equal(await page.$$eval('[data-testid="set-0-0"] .wpb-set-stepper', nodes => nodes.length), 0,
            'completed set steppers retire while their weight/reps tracks stay put');
        const effortLayout = await page.$eval('[data-testid="set-0-0"]', row => {
            const tag = row.querySelector('[data-effort-tag]'), reps = row.querySelector('input[aria-label="reps"]');
            const t = tag.getBoundingClientRect(), r = reps.getBoundingClientRect();
            return { text: tag.textContent, inReps: tag.parentElement.classList.contains('wpb-set-reps'),
                below: t.top >= r.bottom, centered: Math.abs((t.left + t.width / 2) - (r.left + r.width / 2)) < 1,
                height: row.getBoundingClientRect().height, picker: !!row.querySelector('[data-effort-picker]') };
        });
        assert.equal(effortLayout.text, '3 RIR', 'actual effort is distinct from the 2 RIR target');
        assert.ok(effortLayout.inReps && effortLayout.below && effortLayout.centered, 'effort sits under the reps value');
        assert.ok(effortLayout.height <= 64 && !effortLayout.picker, `recorded completed set has no extra full-width effort line: ${JSON.stringify(effortLayout)}`);
        assert.ok(!await page.$eval('[data-testid="set-0-0"]', n => n.innerText.includes('+ effort')));
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
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b827-after-log-phone.png') });
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
        // Regression: four finished sets must look like the ORIGINAL faded set rows,
        // not collapsed to summaries, even when the exercise itself is complete.
        for (let si = 0; si < 4; si++) {
            await page.click(`[data-testid="set-0-${si}"] button[aria-label="Mark set done"]`);
            await settle();
        }
        const completed = await page.$$eval('[data-testid^="set-0-"][data-done="1"]', rows => rows.map(row => {
            const weight = row.querySelector('input[aria-label="weight"]');
            const reps = row.querySelector('input[aria-label="reps"]');
            const number = row.querySelector('.wpb-set-controls>span')?.textContent;
            const controls = row.querySelector('.wpb-set-controls');
            return { number, weight: weight?.value, reps: reps?.value,
                readonly: weight?.readOnly && reps?.readOnly,
                opacity: controls ? Number(getComputedStyle(controls).opacity) : 1,
                fit: !!weight && !!reps && weight.getBoundingClientRect().left >= 0 &&
                     reps.getBoundingClientRect().right <= innerWidth + 1,
                hasOldControls: !!controls, notCollapsed: !row.hasAttribute('data-completed-bar'),
                hasUndo: !!row.querySelector('button[aria-label="Mark set not done"]') };
        }));
        assert.equal(completed.length, 4, 'all four Back Squat rows remain present');
        completed.forEach((row, index) => {
            assert.equal(row.number, String(index + 1), 'set number remains in original column');
            assert.match(row.weight, /^(?:190|195)$/, 'actual logged weight remains readable');
            assert.match(row.reps, /^[56]$/, 'actual logged reps remain readable');
            assert.ok(row.readonly && row.hasOldControls && row.notCollapsed && row.hasUndo,
                'completed set keeps the original grid and edit-via-Undo path');
            assert.ok(row.opacity < .85 && row.fit, `set ${index + 1} is muted and fits at ${width}px`);
        });
        const compact = await page.$$eval('[data-testid^="set-0-"]', rows => rows.map(row => ({
            effort: row.querySelector('[data-effort-tag]')?.textContent,
            height: row.getBoundingClientRect().height,
            picker: !!row.querySelector('[data-effort-picker]'), text: row.innerText
        })));
        assert.equal(compact[0].effort, '— RIR', 'Undo clears prior effort before re-completion');
        assert.equal(compact[1].effort, '— RIR', 'unreported effort never copies the target');
        assert.ok(compact.filter(row => !row.picker).every(row => row.height <= 64), 'older completed rows stay compact');
        assert.ok(compact.every(row => !row.text.includes('+ effort')), 'no separate add-effort line');
        await page.click('[data-testid="set-0-0"] [data-effort-tag]'); await settle();
        await page.click('[data-testid="set-0-0"] button[aria-label="2 reps left"]'); await settle();
        assert.equal(await page.$eval('[data-testid="set-0-0"] [data-effort-tag]', n => n.textContent), '2 RIR');
        await page.click('[data-testid="set-0-0"] [data-effort-tag]'); await settle();
        await page.click('[data-testid="set-0-0"] button[aria-label="Failure, no reps left"]'); await settle();
        assert.equal(await page.$eval('[data-testid="set-0-0"] [data-effort-tag]', n => n.textContent), '0 RIR', 'zero effort is a logged value');
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].actualRIR === 0);
        assert.equal(await page.$$eval('[data-testid^="set-0-"] [data-effort-picker]', nodes => nodes.length), 1,
            'one newest unanswered effort editor remains available');
        await page.click('[data-testid="set-0-3"] [data-effort-tag]'); await settle();
        await page.click('[data-testid="set-0-3"] button[aria-label="2 reps left"]'); await settle();
        assert.equal(await page.$$eval('[data-effort-picker]', nodes => nodes.length), 0, 'recording latest effort closes even after tapping its inline label');
        if (width === 390) await page.screenshot({ path: path.join(root, 'verification/b830-rir-under-reps-phone.png') });
        console.log(`PASS original faded row grid, legible logged fields, controls, typing, RIR and Undo at ${width}px.`);
    }
    assert.deepEqual(errors, []);
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
