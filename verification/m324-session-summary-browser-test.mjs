import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { summaryProgram as program } from './m324-session-summary-fixture.mjs';
import { summarizeSession } from '../modules/engine-api.js';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const day = program.days[0], expected = summarizeSession(program, day, 2);
const history = [{ id: 'm324-week-one', programId: program.id, dayId: day.id,
    date: Date.now() - 86400000, weekIndex: 1, unit: 'lb',
    perf: Object.fromEntries(day.exercises.map((id, slot) => [id, { weight: 100, reps: 8,
        sets: Array.from({ length: slot ? 2 : 3 }, () => ({ w: 100, r: slot ? 15 : 8, rir: 2, rirReported: true, done: true })) }])) }];
const store = { v: 13, savedAt: 1, saved: [program], cycles: [], history, perf: {}, drafts: {}, tombs: {},
    pinnedId: program.id, seenIntro: 999, seenWhatsNew: 999, unit: 'lb', unitChosen: true,
    experience: 'intermediate', bodyweight: 185, restAutoStart: false, warmupCard: false,
    installDismissedAt: Date.now(), lastBackup: Date.now() };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json',
    '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
    const file = path.resolve(root, '.' + new URL(req.url, 'http://localhost').pathname);
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/index.html`;
const clickText = async (page, text) => {
    await page.waitForFunction(text => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === text), {}, text);
    await page.evaluate(text => [...document.querySelectorAll('button')].find(b => b.textContent.trim() === text).click(), text);
};
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome',
        headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    for (const [width, theme] of [[320, 'amethyst'], [900, 'rosewater']]) {
        const context = await browser.createBrowserContext(), page = await context.newPage(), errors = [];
        page.on('pageerror', e => errors.push(e.message));
        await page.setViewport({ width, height: 880, deviceScaleFactor: 1, isMobile: width < 600, hasTouch: true });
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        await page.evaluateOnNewDocument(initial => {
            if (!sessionStorage.getItem('m324-seeded')) {
                localStorage.setItem('wpb:v1', JSON.stringify(initial)); sessionStorage.setItem('m324-seeded', '1');
            }
        }, { ...store, theme });
        await page.goto(url, { waitUntil: 'networkidle0' });
        await page.waitForSelector('.wpb-home');
        const loadedHistory = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).history);
        const outline = await page.$eval('.wpb-home-session-outline', node => node.textContent);
        assert.match(outline, /Dumbbell Bench Press/);
        assert.match(outline, /Back Squat/);
        assert.doesNotMatch(outline, /Barbell Bench Press/);
        await page.click('[data-tab="plan"]');
        await page.waitForSelector('[data-plan-week="2"] .wpb-plan-session-link');
        const row = await page.$eval('[data-plan-week="2"] .wpb-plan-session-link', node => node.innerText);
        assert.match(row, /Dumbbell Bench Press/);
        assert.match(row, new RegExp(expected.sets + ' sets'));
        assert.match(row, new RegExp(expected.minutes + 'm'));
        await page.screenshot({ path: `verification/m324-plan-${width}-phone.png` });
        await page.click('[aria-label="Open full program"]');
        await page.waitForSelector('[data-daycard]');
        // Collapse the selected day to inspect its summary rather than the
        // already-correct expanded exercise rows.
        if (!await page.$('.wpb-day-exercise-preview')) await page.click('[data-daycard] .wpb-day-title');
        await page.waitForSelector('.wpb-day-exercise-preview');
        const names = await page.$eval('.wpb-day-exercise-preview', node => node.textContent);
        assert.match(names, /Dumbbell Bench Press/); assert.match(names, /Back Squat/);
        assert.doesNotMatch(names, /Barbell Bench Press/);
        await page.screenshot({ path: `verification/m324-program-${width}-phone.png` });
        await page.reload({ waitUntil: 'networkidle0' });
        await clickText(page, 'Preview · Time available');
        await page.waitForSelector('[data-nextex]');
        const previewNames = await page.$$eval('[data-nextex]', nodes => nodes.map(n => n.textContent).join(' '));
        assert.match(previewNames, /Dumbbell Bench Press/); assert.match(previewNames, /Back Squat/);
        const preview = await page.$eval('.wpb-next-workout-view', n => n.innerText);
        assert.match(preview, new RegExp(expected.sets + ' working sets'));
        assert.match(preview, new RegExp('~' + expected.minutes + ' min'));
        await page.screenshot({ path: `verification/m324-preview-${width}-phone.png` });
        await clickText(page, 'Start workout');
        await page.waitForSelector('.wpb-workout');
        await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.startsWith('Feeling strong')));
        await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.startsWith('Feeling strong')).click());
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live') || '{}').data?.length === 2);
        const live = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));
        assert.equal(live.weekIndex, 2);
        assert.deepEqual(live.data.map(row => row.id), expected.exercises.map(row => row.id));
        assert.equal(live.data.reduce((n, row) => n + row.sets.filter(set => !set.warm && !set.sub).length, 0), expected.sets);
        assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).history), loadedHistory,
            'Browsing and starting a workout preserve the normalized loaded history, including import metadata');
        assert.deepEqual(errors, []);
        await context.close();
    }
    console.log('PASS M324 browser: real Home → Plan → full Program → Preview → Workout uses week-two exercises, sets and clock, preserving history at 320/900px dark/light.');
} finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
}
