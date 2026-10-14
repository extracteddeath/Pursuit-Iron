import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const program = { id: 'b875-dock', name: 'Context fixture', custom: true, weeks: 6,
    config: { unit: 'lb', goal: 'both', experience: 'intermediate', progression: 'manual', split: 'custom', deload: false },
    days: [{ id: 'lower', label: 'Lower', primaryIndex: 0, exercises: ['back-squat'] }],
    overrides: { 'lower:0': { sets: 2, reps: '5-8', rir: '2', rest: 120,
        tech: 'On the final set only, continue with lengthened partials after the last full rep.' } } };
const history = [{ id: 'last', programId: program.id, dayId: 'lower', date: 1, unit: 'lb',
    perf: { 'back-squat': { weight: 205, reps: 7, sets: [{ w: 205, r: 7, done: true, rir: 2, rirReported: true }] } } }];
const initial = { v: 13, savedAt: 1, saved: [program], cycles: [], history, perf: history[0].perf,
    drafts: {}, tombs: {}, seenIntro: 999, seenWhatsNew: 999, pinnedId: program.id,
    restAutoStart: false, warmupCard: false, unit: 'lb', unitChosen: true, experience: 'intermediate',
    bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const live = { schemaVersion: 2, programId: program.id, dayId: 'lower', weekIndex: 1,
    dayExSig: 'back-squat', exIdx: 0, elapsedMs: 120000, readiness: { label: 'Normal', factor: 1 },
    restPaused: true, restRemain: 47, restMax: 90, runPaused: true,
    data: [{ id: 'back-squat', slot: 0, note: '', sets: [
        { warm: true, weight: '95', reps: '5', done: false, target: { w: '95', reps: '5' } },
        { weight: '190', reps: '5', done: false, auto: false, valueOwner: 'user', target: { w: '195', reps: '5-8', prefillReps: '6', rir: '2' } },
        { weight: '187.5', reps: '7', done: false, auto: false, valueOwner: 'user', target: { w: '195', reps: '5-8', prefillReps: '6', rir: '2' } },
        { sub: true, kind: 'drop', weight: '150', reps: '8', done: false, target: { w: '150', reps: 'to failure' } },
        { sub: true, kind: 'drop', weight: '112.5', reps: '8', done: false, target: { w: '112.5', reps: 'to failure' } }
    ] }] };
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
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox','--disable-dev-shm-usage'] });
    for (const [width, theme, reduced] of [[320,'amethyst',false],[430,'rosewater',true],[800,'amethyst',false],[1280,'rosewater',false]]) {
        const context = await browser.createBrowserContext();
        const page = await context.newPage(); const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setViewport({ width, height: 844, deviceScaleFactor: 1, isMobile: width < 800, hasTouch: true });
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: reduced ? 'reduce' : 'no-preference' }]);
        await page.evaluateOnNewDocument((store, snapshot) => {
            if (localStorage.getItem('b875-seeded')) return;
            localStorage.setItem('b875-seeded','1'); localStorage.setItem('wpb:v1',JSON.stringify(store)); localStorage.setItem('wpb:live',JSON.stringify(snapshot));
        }, { ...initial, theme }, live);
        await page.goto(url, { waitUntil: 'networkidle0' });
        await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.waitForSelector('.wpb-workout');
        const option = async label => {
            await page.click('button[aria-label="Workout options"]');
            const button = await page.waitForSelector(`xpath/.//button[contains(., '${label}')]`);
            await button.click();
        };
        await option('Preview set-aware dock');
        const clickStable = async selector => {
            await page.waitForFunction(async selector => {
                const el=document.querySelector(selector); if(!el)return false;
                const before=el.getBoundingClientRect();
                await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
                const r=el.getBoundingClientRect(), hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
                return Math.abs(r.top-before.top)<.1 && Math.abs(r.left-before.left)<.1
                    && Math.abs(r.height-before.height)<.1 && (hit===el||el.contains(hit));
            }, {}, selector);
            await page.click(selector);
        };
        const dock = '[data-set-dock-preview]';
        await page.waitForSelector(`${dock}[data-mode="rest"]`);
        const identity = await page.$eval(dock, el => { el.dataset.testIdentity='same'; return el.textContent; });
        assert.match(identity, /0:47/);
        await page.waitForFunction(()=>{const b=document.querySelector('button[aria-label="Skip rest"]');const r=b.getBoundingClientRect();const hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);return hit===b||b.contains(hit);});
        await clickStable('button[aria-label="Skip rest"]');
        await page.waitForSelector(`${dock}[data-mode="set"][data-set-kind="warmup"]`);
        assert.equal(await page.$eval(dock, el => el.dataset.testIdentity), 'same', 'timer morph reuses the same container');
        assert.doesNotMatch(await page.$eval(dock, el => el.textContent), /LAST TIME|This set/);
        const geometry = await page.$eval(dock, el => {
            const root=el.closest('.wpb-workout'), scroll=root.querySelector('.wpb-workout-scroll'), footer=root.querySelector('.wpb-workout-footer');
            return { bottom:el.getBoundingClientRect().bottom, footer:footer.getBoundingClientRect().top,
                reserved:parseFloat(getComputedStyle(scroll).paddingBottom), height:el.offsetHeight,
                overflow:el.scrollWidth>el.clientWidth+1 };
        });
        assert.ok(geometry.bottom<=geometry.footer-6 && geometry.reserved>=geometry.height && !geometry.overflow,
            'measured dock clears footer, reserves scroll room and fits: '+JSON.stringify(geometry));
        const log = async index => {
            const selector=`[data-testid="set-0-${index}"] button[aria-label="Mark set done"]`;
            await page.$eval(selector, el=>el.scrollIntoView({block:'center'}));
            await page.waitForFunction(selector=>{ const el=document.querySelector(selector); if(!el)return false;const r=el.getBoundingClientRect();const hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);return hit===el||el.contains(hit); }, {}, selector);
            await clickStable(selector);
        };
        await log(0);
        await page.waitForSelector(`${dock}[data-set-kind="work"]`);
        assert.match(await page.$eval(dock,el=>el.textContent), /195 lb × 6/);
        assert.doesNotMatch(await page.$eval(dock,el=>el.textContent), /This set/);
        await clickStable(`${dock} .wpb-set-dock-plates`);
        await page.waitForSelector('.wpb-backdrop input[placeholder="Target weight (lb)"]');
        assert.equal(await page.$eval('.wpb-backdrop input[placeholder="Target weight (lb)"]', el=>el.value), '190', 'plate action uses actual typed set, not suggested load');
        await page.click('.wpb-backdrop button[aria-label="Close"]');
        await log(1);
        await page.waitForFunction(()=>document.querySelector('[data-set-dock-preview]')?.textContent.includes('This set'));
        await page.waitForFunction(()=>{
            const button=document.querySelector('[data-testid="set-0-2"] button[aria-label="Mark set done"]');
            const r=button.getBoundingClientRect(), hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
            const controls=button.closest('.wpb-set-controls').getBoundingClientRect();
            const panel=document.querySelector('[data-set-dock-preview]').getBoundingClientRect();
            return (hit===button||button.contains(hit)) && controls.bottom<=panel.top-8;
        });
        await page.screenshot({path:path.join(root,`verification/b875-dock-${width}-phone.png`)});
        // Preview must step aside for the existing keyboard/Focus owners.
        await page.$eval('[data-testid="set-0-2"] input[aria-label="weight"]',el=>el.focus());
        await page.evaluate(()=>{Object.defineProperty(window.visualViewport,'height',{configurable:true,get:()=>400});window.visualViewport.dispatchEvent(new Event('resize'));});
        await page.waitForFunction(()=>!document.querySelector('[data-set-dock-preview]'));
        await page.evaluate(()=>{delete window.visualViewport.height;window.visualViewport.dispatchEvent(new Event('resize'));});
        await page.waitForSelector(dock);
        // Injury reduction preserves typed/logged values and removes pending drop extensions.
        const before = await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data);
        await option('Report an injury');
        await page.waitForSelector('.wpb-injury-reduce'); await page.click('.wpb-injury-reduce');
        await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).injuryReports?.length===1);
        const adjusted=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
        assert.deepEqual(adjusted.data[0].sets,before[0].sets.slice(0,3));
        assert.doesNotMatch(await page.$eval(dock,el=>el.textContent), /This set/);
        assert.equal(await page.$('.wpb-tech-cue'), null, 'affected lift no longer offers the prescribed failure technique');
        await page.waitForSelector('xpath/.//button[normalize-space(.)="Undo"]');
        await (await page.$('xpath/.//button[normalize-space(.)="Undo"]')).click();
        await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).injuryReports?.length===0);
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data),before);
        await option('Report an injury'); await page.waitForSelector('.wpb-injury-reduce'); await page.click('.wpb-injury-reduce');
        await page.reload({waitUntil:'networkidle0'});
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
        await page.waitForSelector('.wpb-workout');
        assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).injuryReports.length),1);
        assert.equal(await page.$('.wpb-tech-cue'),null);
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved), initial.saved);
        assert.deepEqual(errors, []);
        await context.close(); console.log(`B875 dock and injury journeys pass: ${width}px, ${theme}, reduced motion=${reduced}`);
    }
} finally { if(browser) await browser.close();await new Promise(resolve=>server.close(resolve)); }
