import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root = path.resolve(new URL('../', import.meta.url).pathname);
const importMap = fs.readFileSync(path.join(root, 'index.html'), 'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const program = { id: 'm232-phone', custom: true, weeks: 6, config: { unit: 'lb', goal: 'hypertrophy', experience: 'intermediate', progression: 'double', equipment: ['cable'] },
    days: [{ id: 'push', label: 'Accessory', primaryIndex: -1, exercises: ['cable-fly'] }], overrides: { 'push:0': { sets: 3, reps: '8-12', rir: '1-2', progressionStyle: 'double' } } };
const history = [{ id: 'prior', programId: program.id, dayId: 'push', date: 1, unit: 'lb', perf: { 'cable-fly': { weight: 100, reps: 8,
    sets: Array.from({ length: 3 }, () => ({ w: 100, r: 8, rir: 2, rirReported: true, done: true })) } } }];
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import {WorkoutSession,StyleTag} from '/modules/App.js';
const program=${JSON.stringify(program)},history=${JSON.stringify(history)};
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day:program.days[0],weekIndex:1,unit:'lb',perf:{},history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{}})));
</script></body></html>`;
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = http.createServer((req, res) => { const pathname = new URL(req.url, 'http://localhost').pathname;
    if (pathname === '/probe.html') { res.setHeader('Content-Type', 'text/html'); res.end(html); return; }
    const file = path.resolve(root, '.' + pathname);
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] ?? 'application/octet-stream'); res.end(fs.readFileSync(file)); });
await new Promise(resolve => server.listen(8782, '127.0.0.1', resolve)); let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    const page = await browser.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message));
    await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
    await page.goto('http://127.0.0.1:8782/probe.html', { waitUntil: 'networkidle0' });
    await page.waitForSelector('[data-testid="set-0-2"]');
    const check = await page.$('.wpb-backdrop button[data-wpb-system-back]'); if (check) await check.click();
    const values = () => page.$$eval('input[aria-label="weight"]', nodes => nodes.map(n => n.value));
    assert.deepEqual(await values(), ['100', '100', '100']);
    for (let i = 0; i < 2; i++) {
        const row = `[data-testid="set-0-${i}"]`, reps = await page.$(row + ' input[aria-label="reps"]');
        await reps.click(); await page.keyboard.down('Control'); await page.keyboard.press('KeyA'); await page.keyboard.up('Control'); await page.keyboard.press('Backspace'); await reps.type('6');
        await page.click(row + ' button[aria-label="Mark set done"]');
        await page.waitForSelector(row + ' button[aria-label="Failure, no reps left"]');
        await page.click(row + ' button[aria-label="Failure, no reps left"]');
    }
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live'))?.data[0].sets[2].weight === '95');
    assert.deepEqual(await values(), ['100', '100', '95']);
    const snapshot = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));
    assert.equal(snapshot.data[0].sets[2].recoveryLimited, true);
    await page.reload({ waitUntil: 'networkidle0' }); await page.waitForSelector('[data-testid="set-0-2"]');
    assert.deepEqual(await values(), ['100', '100', '95'], 'live adaptation survives real component recovery');
    await page.click('[data-testid="set-0-0"] button[aria-label="Mark set not done"]');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live'))?.data[0].sets[2].weight === '100');
    assert.deepEqual(await values(), ['100', '100', '100']);
    assert.deepEqual(errors, []);
    console.log('PASS M232 phone: report two misses → one 5% accessory reduction → autosave/reload preserves it → undo either source restores it; no runtime errors.');
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
