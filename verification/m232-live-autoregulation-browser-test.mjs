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
    sets: [8, 10, 8].map(r => ({ w: 100, r, rir: 2, rirReported: true, done: true })) } } }];
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
    const values = () => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets.map(s => s.weight));
    assert.deepEqual(await values(), ['100', '100', '100']);
    const rows = () => page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets);
    const typeReps = async (index, value) => {
        const input=await page.$(`[data-testid="set-0-${index}"] input[aria-label="reps"]`);
        await input.click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');await input.type(String(value));
    };
    assert.deepEqual((await rows()).map(s=>s.reps), ['9','11','9'], 'positional history produces independent rep targets');
    await typeReps(0,8);
    await page.click('[data-testid="set-0-0"] button[aria-label="Mark set done"]');
    await page.waitForSelector('[data-testid="set-0-0"] [data-effort-picker] button[aria-label="2 reps left"]');
    const targetColors=await page.evaluate(()=>[0,1].map(i=>{
        const el=document.querySelector(`[data-testid="set-0-${i}"] [data-target-effort]`);
        return {text:el.textContent,color:getComputedStyle(el).color,visible:el.getBoundingClientRect().height>0};
    }));
    assert.ok(targetColors.every(t=>t.visible&&/1–2 RIR/.test(t.text)));
    assert.notEqual(targetColors[0].color,targetColors[1].color,'previous target remains visible but loses active theme color');
    assert.match(await page.$eval('[data-testid="set-0-0"] [data-effort-feedback]',el=>el.textContent),/guides suggestions/);
    await page.click('[data-testid="set-0-0"] button[aria-label="2 reps left"]');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].reps==='8');
    assert.equal((await rows())[0].actualRIR,2);
    assert.match(await page.$eval('[data-testid="set-0-0"] [data-target-effort]',el=>el.textContent),/1–2 RIR/);
    assert.match(await page.$eval('[data-testid="set-0-0"] [data-effort-tag]',el=>el.textContent),/2 RIR/);
    await page.click('[data-testid="set-0-0"] [data-effort-tag]');
    await page.waitForSelector('[data-testid="set-0-0"] [data-effort-feedback]');
    assert.match(await page.$eval('[data-testid="set-0-0"] [data-effort-feedback]',el=>el.textContent),/Next set reps adjusted/);
    if (process.env.B901_SCREENSHOT) await page.screenshot({path:process.env.B901_SCREENSHOT});
    await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[data-testid="set-0-2"]');
    assert.equal((await rows())[1].reps,'8','rep cap survives component recovery');
    await page.click('[data-testid="set-0-0"] [data-effort-tag]');
    await page.click('[data-testid="set-0-0"] button[aria-label="3 reps left"]');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].reps==='9');
    await page.click('[data-testid="set-0-0"] [data-effort-tag]');
    await page.click('[data-testid="set-0-0"] button[aria-label="3 reps left"]');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].reps==='11');
    await page.click('[aria-label="Focused mode"]');
    await page.waitForSelector('[data-focus-effort-target]');
    assert.match(await page.$eval('[data-focus-effort-target]',el=>el.textContent),/Target 1–2 RIR/,'Focus retains the owed set target');
    assert.match(await page.$eval('[data-focus-hero]',el=>el.textContent),/1–2 RIR/,'Focus header uses target.rir');
    await page.click('[aria-label="Exit focused mode"]');
    await page.click('[data-testid="set-0-0"] button[aria-label="Mark set not done"]');
    assert.equal((await rows())[1].reps,'11');
    console.log('PASS B901 phone: 9/11/9 → log 8 @ 2 → next 8, muted retained target vs actual, truthful copy, reload/rerate/clear/undo and Focus target parity.');
    for (let i = 0; i < 2; i++) {
        const row = `[data-testid="set-0-${i}"]`, reps = await page.$(row + ' input[aria-label="reps"]');
        await reps.click(); await page.keyboard.down('Control'); await page.keyboard.press('KeyA'); await page.keyboard.up('Control'); await page.keyboard.press('Backspace'); await reps.type('6');
        await page.click(row + ' button[aria-label="Mark set done"]');
        await page.waitForSelector(row + ' button[aria-label="Failure, no reps left"]');
        await page.locator(row + ' button[aria-label="Failure, no reps left"]').click();
        await page.waitForFunction(i => JSON.parse(localStorage.getItem('wpb:live'))?.data[0].sets[i].actualRIR === 0, {}, i);
    }
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live'))?.data[0].sets[2].loadAdvice?.toLoad === 95);
    assert.deepEqual(await values(), ['100', '100', '100']);
    assert.equal(await page.$eval('[data-testid="set-0-2"] input[aria-label="weight"]', n => n.value), '100', 'pending row remains unchanged while advice is displayed');
    const snapshot = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));
    assert.equal(snapshot.data[0].sets[2].loadAdvice.toLoad, 95);
    await page.reload({ waitUntil: 'networkidle0' }); await page.waitForSelector('[data-testid="set-0-2"]');
    assert.deepEqual(await values(), ['100', '100', '100'], 'live adaptation survives real component recovery');
    assert.equal(await page.$eval('[data-testid="set-0-2"] input[aria-label="weight"]', n => n.value), '100', 'recovered row retains unchanged load');
    await page.click('[data-testid="set-0-0"] button[aria-label="Mark set not done"]');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live'))?.data[0].sets[2].weight === '100');
    assert.deepEqual(await values(), ['100', '100', '100']);
    assert.equal(await page.$eval('[data-testid="set-0-2"] input[aria-label="weight"]', n => n.value), '100', 'undo restores the rendered pending load');
    // M296: restore an untouched opening session with an orphaned second-set
    // reduction. No tap/log/rating should be needed to get its original load back.
    const restoreSnapshot = async saved => {
        const script = await page.evaluateOnNewDocument(s => {
            localStorage.setItem('wpb:live', JSON.stringify(s));
            window.__m296SeededLoads = s.data[0].sets.map(row => row.weight);
        }, saved);
        await page.reload({ waitUntil: 'networkidle0' });await page.waitForSelector('[data-testid="set-0-2"]');
        await page.removeScriptToEvaluateOnNewDocument(script.identifier);
    };
    const orphan = structuredClone(snapshot);
    for (const row of orphan.data[0].sets) { row.done = false; row.actualRIR = null; }
    orphan.data[0].sets[1] = { ...orphan.data[0].sets[2],
        liveAdjustment: { ...orphan.data[0].sets[2].liveAdjustment, pendingIndex: 1, sourceIndices: [0, 1] } };
    await restoreSnapshot(orphan);
    assert.equal(await page.evaluate(() => window.__m296SeededLoads[1]), '100', 'Opening load must remain unchanged');
    assert.deepEqual(await values(), ['100', '100', '100'], 'Opening removes automatic reductions without logged sources');
    assert.equal(await page.$eval('[data-testid="set-0-1"] input[aria-label="weight"]', n => n.value), '100');
    await page.reload({ waitUntil: 'networkidle0' });await page.waitForSelector('[data-testid="set-0-2"]');
    assert.deepEqual(await values(), ['100', '100', '100'], 'Repaired opening targets remain stable on a second reload');
    const manualOrphan = structuredClone(orphan);
    Object.assign(manualOrphan.data[0].sets[1], { weight: '92.5', auto: false, valueOwner: 'user' });
    await restoreSnapshot(manualOrphan);
    assert.equal((await values())[1], '92.5', 'Restore preserves a deliberately edited second-set load');
    console.log('PASS M296 phone opening: orphaned set-2 reduction repaired before interaction, reload-stable, manual loads protected.');
    // M359: use the actual set-number menu; selecting a type retains numeric
    // ownership, so this must be protected by intent rather than input editing.
    const freshSession = async () => {
        // Clear after the old page's unload autosave and before the next mount.
        const reset = await page.evaluateOnNewDocument(() => localStorage.removeItem('wpb:live'));
        await page.reload({ waitUntil: 'networkidle0' });
        await page.waitForSelector('[data-testid="set-0-2"]');
        await page.removeScriptToEvaluateOnNewDocument(reset.identifier);
        const check = await page.$('.wpb-backdrop button[data-wpb-system-back]');
        if (check) await check.click();
        assert.deepEqual(await values(), ['100', '100', '100'], 'Each intent case starts from a fresh prescription');
        assert.ok((await rows()).every(row => !row.done));
    };
    const chooseFailure = async index => {
        await page.click(`[data-testid="set-0-${index}"] [data-set-options-trigger]`);
        await page.waitForSelector('[data-testid="workout-set-options"]');
        await page.click('[data-testid="workout-set-options"] [data-set-type="failure"]');
    };
    const logFailure = async index => {
        await typeReps(index, 8);
        await page.click(`[data-testid="set-0-${index}"] button[aria-label="Mark set done"]`);
        await page.waitForSelector(`[data-testid="set-0-${index}"] button[aria-label="Failure, no reps left"]`);
        await page.locator(`[data-testid="set-0-${index}"] button[aria-label="Failure, no reps left"]`).click();
        await page.waitForFunction(i => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[i].actualRIR === 0, {}, index);
    };
    await freshSession();
    for (const index of [0, 1]) { await chooseFailure(index); await logFailure(index); }
    assert.deepEqual(await values(), ['100', '100', '100'], 'Two deliberate failure sets cannot ease the next load');
    const authored = await rows();
    assert.ok(authored.slice(0, 2).every(row => row.typeOverride === 'failure' && row.target.rir === 0));
    assert.equal(authored[2].liveAdjustment, undefined);
    await page.reload({ waitUntil: 'networkidle0' });await page.waitForSelector('[data-testid="set-0-2"]');
    assert.deepEqual(await rows(), authored, 'Reload preserves deliberate effort and actual completed sets');
    await freshSession();
    await chooseFailure(2);
    for (const index of [0, 1]) await logFailure(index);
    assert.deepEqual(await values(), ['100', '100', '100'], 'Ordinary misses cannot rewrite the authored final set');
    assert.equal((await rows())[2].target.failure, true);
    assert.equal((await rows())[2].liveAdjustment, undefined);
    await freshSession();
    await typeReps(0, 8);
    await page.click('[data-testid="set-0-0"] button[aria-label="Mark set done"]');
    await page.waitForSelector('[data-testid="set-0-0"] button[aria-label="2 reps left"]');
    await page.click('[data-testid="set-0-0"] button[aria-label="2 reps left"]');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[1].reps === '8');
    await chooseFailure(1);
    await page.click('[data-testid="set-0-0"] [data-effort-tag]');
    await page.click('[data-testid="set-0-0"] button[aria-label="3 reps left"]');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].actualRIR === 3);
    assert.equal((await rows())[1].target.failure, true, 'Rerating cannot undo a later explicit set type');
    assert.equal((await rows())[1].target.rir, 0);
    await page.click('[data-testid="set-0-0"] button[aria-label="Mark set not done"]');
    await page.waitForFunction(() => !JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].done);
    assert.equal((await rows())[1].target.failure, true, 'Unticking delegates live Undo to its canonical owner');
    assert.equal((await rows())[1].target.rir, 0);
    console.log('PASS M359 phone: deliberate failure sources and pending set intent survive logging and reload without spurious load reductions.');
    assert.deepEqual(errors, []);
    console.log('PASS M232 phone: report two misses → one 5% accessory reduction → autosave/reload preserves it → undo either source restores it; no runtime errors.');
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
