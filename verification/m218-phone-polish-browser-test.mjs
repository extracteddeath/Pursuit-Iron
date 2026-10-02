import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// Use the complete production app: reading notes, choosing themes, jumping weeks, and editing a resumed workout
// must preserve the user's training data as well as fit the phone.
const root = path.resolve(new URL('../', import.meta.url).pathname);
const releaseMilestone = Number(JSON.parse(fs.readFileSync(path.join(root, 'BUILD_PROFILE.json'), 'utf8')).milestone.replace(/^M/, ''));
const program = {
    id: 'm218-phone', name: 'Ten-week upper training', custom: true, weeks: 10,
    config: { name: 'Ten-week upper training', goal: 'both', experience: 'intermediate', progression: 'manual',
        split: 'custom', weeks: 10, days: 1, session: 's60', unit: 'lb', deload: false },
    days: [{ id: 'm218-upper', label: 'Upper', primaryIndex: -1, exercises: ['inc-curl'] }],
    overrides: { 'm218-upper:0': { sets: 2, reps: '10-15', rir: '2', rest: 90,
        tech: 'On the final set only, continue with lengthened partials after the last full rep.' } }
};
const initial = { v: 13, savedAt: 1, saved: [program], cycles: [], history: [], perf: {}, drafts: {}, tombs: {},
    seenIntro: 999, seenWhatsNew: 217, pinnedId: program.id, theme: 'amethyst', restAutoStart: false, warmupCard: false,
    unit: 'lb', unitChosen: true, experience: 'intermediate', bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const live = { schemaVersion: 2, programId: program.id, dayId: program.days[0].id, weekIndex: 1,
    dayExSig: 'inc-curl', exIdx: 0, readiness: { label: 'Normal', factor: 1 }, elapsedMs: 120000,
    restPaused: true, restRemain: 47, restMax: 90, runPaused: true,
    data: [{ id: 'inc-curl', slot: 0, note: 'Keep the typed values', sets: [
        { weight: '100', reps: '11', done: true, auto: false, valueOwner: 'user', target: { w: '100', reps: '10-15', rir: '2' } },
        { weight: '155', reps: '14', done: false, auto: false, valueOwner: 'user', target: { w: '95', reps: '10-15', rir: '2' } }
    ] }] };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const file = path.resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
    res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8773, '127.0.0.1', resolve));
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true,
        args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    const page = await browser.newPage(), errors = [];
    page.on('pageerror', e => errors.push(e.message));
    const resize = async (width, height = 844) => {
        await page.setViewport({ width, height, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    };
    await resize(390);
    await page.evaluateOnNewDocument(store => {
        if (!localStorage.getItem('m218-seeded')) {
            localStorage.setItem('m218-seeded', '1');
            localStorage.setItem('wpb:v1', JSON.stringify(store));
        }
    }, initial);
    await page.goto('http://127.0.0.1:8773/', { waitUntil: 'networkidle0' });
    await page.waitForSelector('.wpb-tabbar');
    const saved = () => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')));
    const snapshot = () => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));

    await page.waitForFunction(() => document.querySelector('[data-wn-item]') || /What's new couldn.t load/.test(document.body.textContent));
    const notes = await page.$$eval('[data-wn-item]', nodes => nodes.map(n => ({ text: n.textContent.trim(), icon: !!n.querySelector('svg') })));
    assert.equal(notes.length, 5, 'Home must render the current release instead of its error boundary');
    assert.ok(notes.every(n => n.icon && n.text), 'plain-text release notes receive readable text and a valid icon');
    assert.ok(notes.every(n => !n.text.includes('Completed history is saved')), 'Home shows the current release only');
    await page.screenshot({ path: path.join(root, 'verification/m218-whats-new-phone.png') });
    const clickText = async text => {
        await page.waitForFunction(label => Array.from(document.querySelectorAll('button')).some(n => n.textContent.trim() === label && n.getBoundingClientRect().width > 0), {}, text);
        const handle = await page.evaluateHandle(label => Array.from(document.querySelectorAll('button')).find(n => n.textContent.trim() === label && n.getBoundingClientRect().width > 0), text);
        assert.ok(handle.asElement(), 'button is available: ' + text);
        await handle.asElement().evaluate(n => n.scrollIntoView({ block: 'center', inline: 'nearest' }));
        await page.evaluate(() => Promise.allSettled(document.getAnimations().filter(a => a.effect?.getComputedTiming().iterations !== Infinity).map(a => a.finished)));
        await handle.asElement().click();
        await handle.dispose();
    };
    await clickText('Got it');
    await page.waitForFunction(version => JSON.parse(localStorage.getItem('wpb:v1')).seenWhatsNew === version, {}, releaseMilestone);
    await page.reload({ waitUntil: 'networkidle0' }); await page.waitForSelector('.wpb-tabbar');
    assert.equal(await page.$$eval('[data-wn-item]', nodes => nodes.length), 0, 'dismissal survives reload');
    const beforeNotes = await saved();
    await page.click('[data-tab="settings"]');
    await clickText("What's new in this version");
    await page.waitForSelector('.wpb-backdrop [data-wn-item]', { visible: true });
    assert.equal(await page.$$eval('.wpb-backdrop [data-wn-item]', nodes => nodes.length), 5, 'Settings replays the dismissed release');
    await page.evaluate(() => history.back());
    await page.waitForSelector('.wpb-backdrop', { hidden: true });
    await clickText("What's new in this version");
    await page.waitForSelector('.wpb-backdrop [data-wn-item]', { visible: true });
    await clickText('Full changelog');
    await page.waitForSelector('.wpb-backdrop button[aria-label="Close"]');
    await page.waitForFunction(() => !document.querySelector('[data-wn-item]'));
    assert.equal(await page.$$eval('[data-wn-item]', nodes => nodes.length), 0, 'the archive replaces the release card');
    const archive = await page.$eval('.wpb-backdrop', n => n.textContent);
    assert.match(archive, /build 808/); assert.match(archive, /build 807/);
    assert.ok(await page.$$eval('.wpb-backdrop svg', nodes => nodes.length) > 5, 'mixed current and archived note formats render their icons');
    await page.click('.wpb-backdrop button[aria-label="Close"]');
    await page.waitForSelector('.wpb-backdrop', { hidden: true });
    assert.deepEqual((await saved()).saved, beforeNotes.saved, 'reading notes cannot rewrite the program');
    assert.deepEqual((await saved()).history, beforeNotes.history);
    assert.equal((await saved()).seenWhatsNew, releaseMilestone, 'replaying and closing notes preserves dismissal');
    await page.evaluate(snapshot => localStorage.setItem('wpb:live', JSON.stringify(snapshot)), live);
    await page.reload({ waitUntil: 'networkidle0' }); await page.waitForSelector('.wpb-live-dock');

    await page.click('[data-tab="settings"]');
    await page.waitForSelector('[data-theme-summary]');
    assert.equal(await page.$eval('.wpb-theme-picker', n => n.open), false);
    assert.match(await page.$eval('[data-theme-summary]', n => n.textContent), /Amethyst/);
    const beforeTheme = await saved();
    await page.focus('[data-theme-summary]'); await page.keyboard.press('Enter');
    await page.waitForFunction(() => document.querySelector('.wpb-theme-picker').open);
    await page.click('[data-theme-option="light"]');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).theme === 'light');
    assert.match(await page.$eval('[data-theme-summary]', n => n.textContent), /Mint · Light/);
    assert.deepEqual((await saved()).saved, beforeTheme.saved, 'changing appearance cannot rewrite the program');
    assert.deepEqual((await saved()).history, beforeTheme.history);
    await page.reload({ waitUntil: 'networkidle0' });
    await page.click('[data-tab="settings"]'); await page.waitForSelector('[data-theme-summary]');
    assert.equal(await page.$eval('.wpb-theme-picker', n => n.open), false);
    assert.match(await page.$eval('[data-theme-summary]', n => n.textContent), /Mint · Light/);
    await page.click('[data-theme-summary]'); await page.click('[data-theme-option="amethyst"]');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).theme === 'amethyst');
    for (const width of [320, 360, 390]) {
        await resize(width);
        assert.ok(await page.$$eval('.wpb-theme-option', nodes => nodes.every(n => {
            const r = n.getBoundingClientRect(), name = n.querySelector('span:last-child'); return r.width >= 44 && r.height >= 44 && r.left >= 0 && r.right <= innerWidth && name.scrollWidth <= name.clientWidth + 1;
        })), 'every expanded theme is reachable at ' + width + 'px');
    }
    await page.screenshot({ path: path.join(root, 'verification/m218-theme-picker-phone.png') });
    await page.focus('[data-theme-summary]'); await page.keyboard.press('Space');
    await page.waitForFunction(() => !document.querySelector('.wpb-theme-picker').open);
    await page.$eval('.wpb-settings .wpb-page-scroll', n => { n.scrollTop = 0; });
    await page.screenshot({ path: path.join(root, 'verification/m218-settings-phone.png') });

    await page.click('[data-tab="plan"]'); await page.waitForSelector('.wpb-plan-view');
    assert.match(await page.$eval('.wpb-plan-glance', n => n.textContent), /1 \/ 10/);
    assert.ok(await page.$eval('.wpb-plan-up-next', n => n.getBoundingClientRect().bottom < innerHeight * .6), 'Up Next is visible near the top');
    await page.screenshot({ path: path.join(root, 'verification/m218-plan-phone.png') });
    for (const width of [320, 390]) {
        await resize(width);
        await page.click('button[aria-label="Week 10"]');
        await page.waitForFunction(() => {
            const picker = document.querySelector('[data-plan-week-picker]');
            const row = document.querySelector('[data-plan-week="10"]');
            const day = row?.querySelector('button[aria-label="Upper, week 10"]');
            if (!day) return false;
            const r = day.getBoundingClientRect(), p = picker.getBoundingClientRect();
            return p.top >= -1 && r.top >= p.bottom - 1 && r.bottom < document.querySelector('.wpb-tabbar').getBoundingClientRect().top;
        });
        assert.equal(await page.$eval('button[aria-label="Week 10"]', n => n.getAttribute('aria-pressed')), 'true');
        await page.click('button[aria-label="Week 5"]');
        await page.waitForFunction(() => {
            const day = document.querySelector('[data-plan-week="5"] button[aria-label="Upper, week 5"]');
            return day && day.getBoundingClientRect().bottom < document.querySelector('.wpb-tabbar').getBoundingClientRect().top;
        });
    }
    await page.screenshot({ path: path.join(root, 'verification/m218-plan-week-jump-phone.png') });
    for (const pane of ['volume', 'progression', 'blueprint', 'schedule']) {
        await page.click('[data-plan-pane="' + pane + '"]');
        await page.waitForFunction(id => document.querySelector('[data-plan-pane="' + id + '"]').getAttribute('aria-pressed') === 'true', {}, pane);
    }
    assert.equal(await page.$eval('button[aria-label="Week 5"]', n => n.getAttribute('aria-pressed')), 'true', 'week selection survives view changes');

    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.waitForSelector('.wpb-workout');
    await resize(390);
    await page.click('.wpb-tech-cue');
    await page.waitForFunction(() => document.querySelector('.wpb-tech-cue').getAttribute('aria-expanded') === 'true');
    assert.match(await page.$eval('.wpb-workout', n => n.textContent), /continue with lengthened partials/);
    await page.click('.wpb-tech-cue');
    const weight = '[data-testid="set-0-1"] input[aria-label="weight"]';
    await page.click(weight, { clickCount: 3 }); await page.keyboard.type('125.5'); await page.keyboard.press('Enter');
    assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-label')), 'reps');
    await page.keyboard.down('Control'); await page.keyboard.press('KeyA'); await page.keyboard.up('Control');
    await page.keyboard.type('14'); await page.keyboard.press('Enter');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].weight === '125.5');
    await page.click(weight); assert.equal(await page.$eval(weight, n => n.value), '125.5', 'tapping the field cannot hit a neighboring stepper');
    await page.keyboard.press('Tab');
    for (const [width, height] of [[320, 740], [360, 844], [390, 844], [844, 390]]) {
        await resize(width, height);
        const geometry = await page.$$eval('.wpb-workout .wpb-num-input', nodes => nodes.map(n => {
            const r = n.getBoundingClientRect(), style = getComputedStyle(n), context = document.createElement('canvas').getContext('2d');
            context.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
            return { value: n.value, left: r.left, right: r.right, space: n.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight), text: context.measureText(n.value).width };
        }));
        assert.ok(geometry.every(n => n.left >= 0 && n.right <= width && n.space + 1 >= n.text), 'typed values fit without clipping: ' + JSON.stringify({ width, geometry }));
        assert.ok(await page.$$eval('.wpb-target-reps, .wpb-set-tech-tag', nodes => nodes.every(n => n.scrollWidth <= n.clientWidth + 1)), 'target ranges and technique labels stay together at ' + width);
        const values = (await snapshot()).data[0].sets[1];
        assert.equal(values.weight, '125.5'); assert.equal(values.reps, '14'); assert.equal(values.done, false);
    }
    await resize(390); await page.screenshot({ path: path.join(root, 'verification/m218-workout-phone.png') });
    await page.click('button[aria-label="Focused mode"]'); await page.waitForSelector('.wpb-focus-hero');
    await page.click('button[aria-label="Exit focused mode"]'); await page.waitForSelector('.wpb-focus-hero', { hidden: true });
    await page.evaluate(() => history.back()); await page.waitForSelector('.wpb-workout', { hidden: true });
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]'); await page.waitForSelector(weight);
    assert.equal(await page.$eval(weight, n => n.value), '125.5');
    assert.equal((await snapshot()).data[0].sets[1].reps, '14');
    assert.deepEqual(errors, []);
    console.log('PASS M218 full-app phone polish: current release renders on Home and Settings, dismissal survives reload, Back and the mixed-format archive preserve data; native theme disclosure and persisted light/dark selection; unchanged programs; ten-week navigation scrolls the selected days below the sticky picker; Plan views retain selection; technique details, numeric focus, unclipped decimal values, target ranges, focused mode and Back/resume at 320–390px and landscape.');
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
