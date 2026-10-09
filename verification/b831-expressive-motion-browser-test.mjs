import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const program = { id: 'b831-motion', name: 'Motion check', custom: true, weeks: 6,
    config: { unit: 'lb', goal: 'both', experience: 'intermediate', progression: 'double', split: 'custom', deload: false },
    days: [{ id: 'lower', label: 'Lower', primaryIndex: 0, exercises: ['back-squat', 'inc-curl'] }],
    overrides: { 'lower:0': { sets: 4, reps: '5-8', rir: '2', rest: 120 }, 'lower:1': { sets: 2, reps: '10-15', rir: '2', rest: 90 } } };
const history = [{ id: 'last', programId: program.id, dayId: 'lower', date: 1, unit: 'lb',
    perf: { 'back-squat': { weight: 205, reps: 7, sets: [{ w: 205, r: 7, done: true, rir: 2, rirReported: true }] } } }];
const initial = { v: 13, savedAt: 1, saved: [program], cycles: [], history, perf: history[0].perf,
    drafts: {}, tombs: {}, seenIntro: 999, seenWhatsNew: 999, pinnedId: program.id, theme: 'amethyst',
    restAutoStart: false, warmupCard: false, unit: 'lb', unitChosen: true, experience: 'intermediate',
    bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const live = { schemaVersion: 2, programId: program.id, dayId: 'lower', weekIndex: 1,
    dayExSig: 'back-squat|inc-curl', exIdx: 0, elapsedMs: 120000, readiness: { label: 'Normal', factor: 1 },
    restPaused: true, restRemain: 47, restMax: 90, runPaused: true,
    data: ['back-squat','inc-curl'].map((id, slot) => ({ id, slot,
        sets: Array.from({ length: slot ? 2 : 4 }, () => ({ weight: slot ? '30' : '190', reps: slot ? '10' : '5',
            done: false, auto: false, valueOwner: 'user', target: { w: slot ? '30' : '190', reps: slot ? '10-15' : '5-8', rir: '2' } })) })) };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const name = new URL(req.url, 'http://localhost').pathname;
    const file = path.resolve(root, '.' + (name === '/' ? '/index.html' : name));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8794, '127.0.0.1', resolve));
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox','--disable-dev-shm-usage'] });
    for (const width of [320, 430]) {
        const page = await browser.newPage(), errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setViewport({ width, height: 844, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
        await page.evaluateOnNewDocument((store, snapshot) => {
            if (localStorage.getItem('b831-seeded')) return;
            localStorage.setItem('b831-seeded', '1'); localStorage.setItem('wpb:v1', JSON.stringify(store)); localStorage.setItem('wpb:live', JSON.stringify(snapshot));
        }, initial, live);
        await page.goto('http://127.0.0.1:8794/', { waitUntil: 'networkidle0' });
        await page.waitForSelector('#root[data-pi-motion="expressive"] .wpb-tabbar');
        const clickText = text => page.$$eval('button', (buttons, text) => {
            const button = buttons.find(b => b.textContent.includes(text));
            if (!button) throw new Error('Missing button: ' + text);
            button.click();
        }, text);
        // Root tabs keep their navigation semantics, respond immediately, and settle without layout growth.
        for (const tab of ['plan','progress','profile','settings','home']) {
            await page.click(`[data-tab="${tab}"]`);
            await page.waitForSelector(`[data-view-frame="${tab}"][data-pi-surface]`);
            await page.waitForFunction(tab => document.querySelector(`[data-view-frame="${tab}"]`)?.style.getPropertyValue('--pi-surface-opacity') === '1', {}, tab);
            assert.equal(await page.$eval(`[data-tab="${tab}"]`, b => b.getAttribute('aria-current')), 'page');
            assert.ok(await page.$eval('.wpb', el => el.scrollWidth <= innerWidth + 1), `${tab} fits at ${width}px`);
            const navPill = await page.$eval(`[data-tab="${tab}"]`, el => {
                const style = getComputedStyle(el, '::before');
                return { height: parseFloat(style.height), radius: style.borderRadius,
                    opacity: parseFloat(style.opacity), width: parseFloat(style.width) };
            });
            assert.ok(navPill.height >= 27 && navPill.height <= 32 && navPill.width >= 30,
                'selected navigation has a compact expressive pill: ' + JSON.stringify(navPill));
            assert.ok(navPill.opacity > .9 && navPill.radius.includes('px'),
                'selected navigation uses a visible rounded tonal selection');
            if (tab === 'home') {
                assert.ok(await page.$eval('.wpb-home-hero', el =>
                    parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 18 &&
                    parseFloat(getComputedStyle(el).marginBottom) <= 14),
                    'home hero has a tighter spacious-card hierarchy');
            }
            if (tab === 'progress') {
                assert.ok(await page.$eval('.wpb-progress .wpb-premium-tabs', el =>
                    parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 14),
                    'progress uses a unified pill-tab surface');
            }
            if (tab === 'settings') {
                assert.ok(await page.$eval('.wpb-settings-card', el =>
                    parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 15),
                    'settings sections share the card geometry');
            }
            if (width === 430) await page.screenshot({ path: path.join(root, `verification/b831-${tab}-phone.png`) });
        }
        await page.click('[data-tab="settings"]'); await page.waitForSelector('.wpb-settings');
        // Choice controls show a quiet touch-origin state layer, even for rapid repeated taps.
        // This must not add nodes or modify the actual workout logging grid.
        const ink = await page.$eval('[data-tab="settings"]', el => {
            const r = el.getBoundingClientRect(), children = el.childElementCount;
            const x = r.left + r.width * .25, y = r.top + r.height * .35;
            el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, button: 0, pointerId: 95, clientX: x, clientY: y }));
            const state = { ink: el.dataset.piInk, x: parseFloat(el.style.getPropertyValue('--pi-ink-x')),
                y: parseFloat(el.style.getPropertyValue('--pi-ink-y')),
                animation: getComputedStyle(el, '::after').animationName, childrenUnchanged: el.childElementCount === children };
            document.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 95 }));
            return state;
        });
        assert.equal(ink.ink, '1', 'the press state layer activates on root navigation');
        assert.ok(ink.x > 0 && ink.y > 0 && ink.childrenUnchanged, 'tap origin is tracked without DOM or layout additions');
        assert.ok(ink.animation.includes('piInkBurst'), 'touch feedback is transient');
        await page.waitForFunction(() => !document.querySelector('[data-tab="settings"]')?.hasAttribute('data-pi-ink'));
        // Losing window focus cancels a held button instead of leaving a scaled control.
        await page.$eval('[data-tab="settings"]', el => {
            const r = el.getBoundingClientRect();
            el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, button: 0, pointerId: 96,
                clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 }));
            window.dispatchEvent(new Event('blur'));
        });
        await page.waitForFunction(() => document.querySelector('[data-tab="settings"]')?.style.getPropertyValue('--pi-control-scale') === '1');
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        const reducedInk = await page.$eval('[data-tab="settings"]', el => {
            const r = el.getBoundingClientRect();
            el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, button: 0, pointerId: 97,
                clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 }));
            document.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 97 }));
            return el.hasAttribute('data-pi-ink');
        });
        assert.equal(reducedInk, false, 'reduced motion never starts the state-layer burst');
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }]);
        await page.focus('[data-tab="settings"]'); await page.keyboard.down(' ');
        await page.waitForFunction(() => Number.parseFloat(document.querySelector('[data-tab="settings"]').style.getPropertyValue('--pi-control-scale')) < .999);
        await page.keyboard.up(' ');
        await page.waitForFunction(() => document.querySelector('[data-tab="settings"]').style.getPropertyValue('--pi-control-scale') === '1');
        await clickText('Your gyms'); await page.waitForSelector('[data-sheet-drag][data-pi-surface]');
        await page.waitForFunction(() => document.querySelector('[data-sheet-drag]')?.style.getPropertyValue('--pi-surface-y') === '0px');
        // A partial drag belongs to the sheet and returns to rest; it cannot leave an offset behind.
        await page.$eval('[data-sheet-drag]', el => {
            const r = el.getBoundingClientRect();
            window.__b831Touch = { identifier: 1, target: el, clientX: r.left + 20, clientY: r.top + 20 };
            el.dispatchEvent(new TouchEvent('touchstart', { touches: [new Touch(window.__b831Touch)], bubbles: true }));
        });
        await page.$eval('[data-sheet-drag]', el => {
            el.dispatchEvent(new TouchEvent('touchmove', { touches: [new Touch({ ...window.__b831Touch, clientY: window.__b831Touch.clientY + 30 })], bubbles: true }));
            el.dispatchEvent(new TouchEvent('touchend', { touches: [], bubbles: true }));
        });
        await page.waitForFunction(() => document.querySelector('[data-sheet-drag]')?.style.getPropertyValue('--pi-surface-y') === '0px');
        await page.keyboard.press('Escape'); await page.waitForFunction(() => !document.querySelector('.wpb-backdrop'));
        assert.equal(await page.$$eval('[data-wpb-lock]', els => els.length), 0, 'sheet exit releases scrolling');
        await page.click('[data-tab="home"]'); await page.waitForSelector('.hp-open'); await page.click('.hp-open');
        await page.waitForSelector('.wpb-program');
        // Info sections preserve content while closing and survive an immediate reopen.
        const card = await page.$('[data-infocard][data-collapsible="1"]');
        if (card) {
            await card.$eval('button[aria-expanded]', b => b.click());
            await page.waitForSelector('[data-pi-reveal]');
            await card.$eval('button[aria-expanded]', b => b.click());
            await card.$eval('button[aria-expanded]', b => b.click());
            await page.waitForFunction(() => [...document.querySelectorAll('[data-pi-reveal]')].some(el => el.style.height === 'auto'));
        }
        assert.ok(await page.$eval('.wpb-program .wpb-day-card', el =>
            parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 15), 'program day cards share the expressive radius');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]'); await page.waitForSelector('.wpb-workout');
        await page.waitForFunction(() => !document.body.innerText.includes('Resumed your in-progress workout'));
        const row = '[data-testid="set-0-0"]';
        await page.click(`${row} input[aria-label="weight"]`); await page.keyboard.type('195.5');
        const before = await page.$$eval(`${row} input`, els => els.map(el => el.value));
        for (let tap = 0; tap < 8; tap++) await page.click(tap % 2 ? 'button[aria-label="Show prescribed targets"]' : 'button[aria-label="Show previous workout values"]');
        assert.deepEqual(await page.$$eval(`${row} input`, els => els.map(el => el.value)), before, 'reference transitions never overwrite typed values');
        await page.waitForFunction(() => [...document.querySelectorAll('[data-pi-reference][data-pi-surface]')].every(el => el.style.getPropertyValue('--pi-surface-y') === '0px'));
        await page.click(`${row} button[aria-label="Mark set done"]`);
        await page.waitForSelector(`${row} [data-effort-picker]`);
        await page.waitForFunction(row => {
            const button = document.querySelector(row + ' button[aria-label="3 reps left"]');
            if (!button) return false;
            const r = button.getBoundingClientRect(), hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
            return hit === button || button.contains(hit);
        }, {}, row);
        const groupWidth = await page.$eval(`${row} .wpb-effort-scale`, el => el.offsetWidth);
        await page.click(`${row} button[aria-label="3 reps left"]`);
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].actualRIR === 3);
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].actualRIR), 3, 'effort commits immediately');
        assert.equal(await page.$eval(`${row} .wpb-effort-scale`, el => el.offsetWidth), groupWidth, 'RIR feedback occupies the same space');
        await page.waitForFunction(row => !document.querySelector(row + ' [data-effort-picker]'), {}, row);
        assert.equal(await page.$eval(`${row} [data-effort-tag]`, el => el.textContent), '3 RIR');
        for (const n of [2,1,2,1]) await page.click(`.wpb-ex-nav-step:nth-child(${n})`);
        await page.waitForSelector(row);
        await page.waitForFunction(() => document.querySelector('[data-pi-workout-page]')?.style.getPropertyValue('--pi-surface-x') === '0px');
        assert.equal(await page.$eval(`${row} input[aria-label="weight"]`, el => el.value), '195.5');
        assert.equal(await page.$eval(`${row} [data-effort-tag]`, el => el.textContent), '3 RIR');
        assert.ok(await page.$eval('.wpb-workout-footer', el => el.getBoundingClientRect().bottom <= innerHeight + 1), 'footer remains available during rest');
        // Runtime preference changes finish existing motion and keep every control functional.
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        await page.click(`${row} [data-effort-tag]`); await page.click(`${row} button[aria-label="2 reps left"]`);
        await page.waitForFunction(row => !document.querySelector(row + ' [data-effort-picker]'), {}, row);
        assert.equal(await page.$eval(`${row} [data-effort-tag]`, el => el.textContent), '2 RIR');
        await page.click(`${row} button[aria-label="Mark set not done"]`);
        assert.equal(await page.$eval(`${row} input[aria-label="weight"]`, el => el.value), '195.5', 'Undo preserves the logged load');
        assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).saved), initial.saved, 'motion cannot change program prescriptions');
        assert.deepEqual(errors, []);
        await page.screenshot({ path: path.join(root, `verification/b831-motion-${width}-phone.png`) });
        await page.close();
        console.log(`PASS expressive app motion, sheet drag, rapid navigation, reference/RIR persistence and reduced motion at ${width}px.`);
    }
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
