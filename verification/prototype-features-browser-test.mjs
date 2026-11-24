import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// The prototype features, driven the way a lifter would: Home week strip and 7-day recap, the
// mid-workout pain check, starting a saved routine, and a program share code pasted back in.
const root = path.resolve(new URL('../', import.meta.url).pathname);
const D = 86400000, now = Date.now();
const EQ = ['barbell', 'dumbbell', 'bench', 'cable', 'machine', 'smith', 'ezbar', 'pullup', 'dip', 'kettlebell', 'bands'];
const program = { id: 'proto-main', name: 'Upper Lower', custom: true, weeks: 6, schedule: { 1: 'upper', 3: 'lower', 5: 'upper' },
  config: { unit: 'lb', goal: 'both', experience: 'intermediate', progression: 'double', weeks: 6, session: 's60', deload: false },
  days: [{ id: 'upper', label: 'Upper', primaryIndex: 0, exercises: ['bb-bench', 'ohp', 'lat-raise', 'inc-curl'] },
    { id: 'lower', label: 'Lower', primaryIndex: 0, exercises: ['back-squat', 'leg-press'] }],
  overrides: { 'upper:0': { sets: 4, reps: '5-7', rir: '2' } } };
const routine = { id: 'proto-routine', name: 'Hotel gym push', quick: true, artifactType: 'freestyle', engineV: 1, createdAt: now - 20 * D,
  config: { name: 'Hotel gym push', goal: 'both', progression: 'manual', split: null, days: 1, session: 's60', experience: 'intermediate', weeks: 1,
    equipment: EQ, focus: {}, reduce: [], barbellCap: null, noBodyweight: false, deload: false, percentScheme: null },
  days: [{ id: 'rday', label: 'Hotel gym push', exercises: ['ohp', 'lat-raise'], primaryIndex: 0 }], overrides: {} };
const session = (i, ago, w) => ({ id: 'h' + i, programId: program.id, programName: program.name, dayId: 'upper', dayLabel: 'Upper', weekIndex: 1,
  date: now - ago * D - 3600000, unit: 'lb', durationMin: 50, setsDone: 3, totalSets: 3,
  perf: { 'bb-bench': { weight: w, reps: 6, sets: [1, 2, 3].map(() => ({ w, r: 6, done: true, rir: 2, rirReported: true })) } } });
const state = { v: 13, savedAt: 1, saved: [program, routine], pinnedId: program.id, cycles: [], perf: {}, drafts: {}, tombs: {},
  history: [session(1, 15, 175), session(2, 8, 180), session(3, 1, 185)],
  seenIntro: 999, seenWhatsNew: 999, unit: 'lb', unitChosen: true, experience: 'intermediate', bodyweight: 185,
  installDismissedAt: now, lastBackup: now, theme: 'amethyst' };

const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json',
  '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  const name = new URL(req.url, 'http://localhost').pathname;
  const file = path.resolve(root, '.' + (name === '/' ? '/index.html' : name));
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
  res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const settle = page => page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(r, 150)))));
const clickText = (page, scope, text) => page.evaluate((scope, text) => {
  const el = [...document.querySelectorAll(scope)].find(b => b.textContent.trim().startsWith(text));
  if (!el) throw new Error('No ' + scope + ' starting with ' + text);
  el.click();
}, scope, text);
let browser;
try {
  browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const open = async (width) => {
    const ctx = await browser.createBrowserContext(), page = await ctx.newPage(), errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.setViewport({ width, height: 800, isMobile: true, hasTouch: true });
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
    await page.evaluateOnNewDocument(s => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.setItem('wpb:v1', JSON.stringify(s)); } }, state);
    await page.goto('http://127.0.0.1:' + server.address().port + '/', { waitUntil: 'networkidle0' });
    await page.waitForSelector('.wpb-home .wpb-home-hero');
    return { ctx, page, errors };
  };

  // 1. Home: strip and recap fit a 320px phone and say what happened.
  {
    const { ctx, page, errors } = await open(320);
    const home = await page.evaluate(() => {
      const strip = document.querySelector('.wpb-home-strip'), recap = document.querySelector('.wpb-home-week');
      const scroller = document.querySelector('.wpb-home-content');
      const days = [...strip.querySelectorAll('.wpb-home-strip-day')];
      const clipped = [...document.querySelectorAll('.wpb-home-strip *, .wpb-home-week *')].filter(el => {
        const r = el.getBoundingClientRect(), p = el.closest('.wpb-ui-card').getBoundingClientRect();
        return r.width > 0 && (r.left < p.left - 1 || r.right > p.right + 1);
      }).map(el => el.className);
      return { days: days.length, today: days.filter(d => d.dataset.today === 'true').length,
        labels: days.map(d => d.getAttribute('aria-label')), streak: strip.querySelector('.wpb-home-strip-streak').textContent,
        title: strip.querySelector('.wpb-home-strip-title')?.textContent, recap: recap.innerText, overflow: scroller.scrollWidth - scroller.clientWidth, clipped,
        stripBeforeHero: !!(strip.compareDocumentPosition(document.querySelector('.wpb-home-hero')) & Node.DOCUMENT_POSITION_FOLLOWING) };
    });
    assert.equal(home.days, 7); assert.equal(home.today, 1);
    assert.ok(home.labels.every(l => /: (trained|scheduled|scheduled, not logged|rest)$/.test(l)), 'Every day announces its state: ' + home.labels);
    assert.match(home.title, /^Week 1 of 6$/);
    assert.match(home.streak, /week streak/);
    assert.match(home.recap, /Last 7 days/); assert.match(home.recap, /1 of 3 sessions/); assert.match(home.recap, /Best: Barbell Bench Press 185 lb × 6/);
    assert.ok(home.stripBeforeHero, 'The week strip leads Home');
    assert.equal(home.overflow, 0); assert.deepEqual(home.clipped, [], 'Nothing in the strip or recap spills out of its card');
    await page.evaluate(() => { Object.defineProperty(navigator, 'share', { value: undefined, configurable: true });
      window.__copied = null; navigator.clipboard.writeText = t => { window.__copied = t; return Promise.resolve(); }; });
    await page.click('.wpb-home-recap-share');
    await page.waitForFunction(() => window.__copied);
    assert.match(await page.evaluate(() => window.__copied), /^My last 7 days on Pursuit Iron: 1 session of 3 · 3 sets/);
    assert.match(await page.$eval('.wpb-home-recap-share', b => b.textContent), /Copied/);

    // 3. Routines: Freestyle offers the saved routine; one tap starts it.
    assert.match(await page.$eval('.wpb-home-freestyle', b => b.textContent), /1 routine/);
    await page.click('.wpb-home-freestyle');
    await page.waitForSelector('[data-routine-picker]');
    const sheet = await page.evaluate(() => {
      const panel = document.querySelector('[data-routine-picker] .wpb-ui-modal-sheet-panel').getBoundingClientRect();
      const hit = document.elementFromPoint(panel.left + panel.width / 2, Math.min(panel.bottom, innerHeight) - 8);
      return { onTop: !!hit?.closest('[data-routine-picker]'), text: document.querySelector('[data-routine-picker]').innerText };
    });
    assert.ok(sheet.onTop, 'The routine sheet sits above the tab bar');
    assert.match(sheet.text, /Hotel gym push/); assert.match(sheet.text, /Not trained yet/);
    // The picker slides up; press its Start action once it has a box on screen.
    await page.waitForSelector('[aria-label="Start Hotel gym push"]', { visible: true });
    await page.waitForFunction(() => { const r = document.querySelector('[aria-label="Start Hotel gym push"]').getBoundingClientRect(); return r.width > 0 && r.height > 0; }, { timeout: 5000 });
    await page.click('[aria-label="Start Hotel gym push"]');
    await page.waitForSelector('.wpb-workout');
    assert.match(await page.$eval('.wpb-workout', w => w.innerText), /Exercise 1 of 2/);
    assert.deepEqual(errors, []);
    await ctx.close();
  }

  // 2. Pain check during the planned workout: knee → sore halves the squat's remaining sets.
  {
    const { ctx, page, errors } = await open(360);
    await page.click('.wpb-primary-action');
    await page.waitForSelector('.wpb-workout');
    await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.includes('Feeling strong'))?.click());
    await settle(page);
    const before = await page.$eval('.wpb-workout', w => (w.innerText.match(/(\d+) \/ (\d+) sets/) || [])[2]);
    await page.click('.wpb-workout-tool[aria-label="Pain check"]');
    await page.waitForSelector('[data-pain-check]');
    const next = await page.$('.wpb-pain-next');
    assert.equal(await next.evaluate(b => b.disabled), false, 'The area of the current lift is preselected');
    const area = await page.$eval('[data-pain-check] .wpb-ui-choice[aria-pressed="true"]', b => b.textContent);
    await clickText(page, '[data-pain-check] button', 'Next');
    assert.equal(await page.$eval('.wpb-pain-next', b => b.disabled), true, 'Severity must be chosen');
    await clickText(page, '[data-pain-check] button', 'Sore');
    await clickText(page, '[data-pain-check] button', 'Next');
    const plan = await page.$eval('.wpb-pain-plan', p => p.innerText);
    assert.match(plan, /half of/);
    const footer = await page.evaluate(() => { const b = document.querySelector('.wpb-pain-next').getBoundingClientRect();
      return document.elementFromPoint(b.left + b.width / 2, b.top + b.height / 2)?.closest('.wpb-pain-next') != null; });
    assert.ok(footer, 'The apply button is not covered by the workout footer');
    await page.click('.wpb-pain-next');
    await page.waitForSelector('.wpb-pain-banner');
    assert.match(await page.$eval('.wpb-pain-banner', b => b.innerText), new RegExp('Protecting your ' + area.toLowerCase().split(' ')[0]));
    const after = await page.$eval('.wpb-workout', w => (w.innerText.match(/(\d+) \/ (\d+) sets/) || [])[2]);
    assert.ok(Number(after) < Number(before), `Sets dropped from ${before} to ${after}`);
    await clickText(page, 'button', 'Undo');
    await settle(page);
    assert.equal(await page.$('.wpb-pain-banner'), null, 'Undo restores the workout');
    assert.deepEqual(errors, []);
    await ctx.close();
  }

  // 4. Share code: the program's sheet mints a PI2 code that Plan → Import brings in as an editable copy.
  {
    const { ctx, page, errors } = await open(393);
    await page.click('[data-tab="plan"]');
    await page.waitForSelector('.wpb-plan-programs [aria-label="Open Upper Lower"]');
    await page.evaluate(() => document.querySelector('.wpb-plan-programs [aria-label="Open Upper Lower"]').click());
    await page.waitForSelector('[aria-label="Share program"]');
    await page.evaluate(() => document.querySelector('[aria-label="Share program"]').click());
    await page.waitForFunction(() => /^PI2-/.test(document.querySelector('#program-share-code')?.value || ''));
    const code = await page.$eval('#program-share-code', t => t.value);
    assert.ok(code.length < 400, 'Code length ' + code.length);
    const before = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).saved.length);
    await page.evaluate(() => document.querySelector('.wpb-program-share-close').click());
    await page.reload({ waitUntil: 'networkidle0' });
    await page.waitForSelector('.wpb-tabbar .wpb-tab');
    // B1031: bringing a program in is program management — Plan → Import.
    await page.click('[data-tab="plan"]');
    await page.waitForSelector('.wpb-plan-programs .wpb-home-import');
    await page.evaluate(() => document.querySelector('.wpb-plan-programs .wpb-home-import').click());
    await page.waitForSelector('textarea[aria-label="Program code or text"]');
    await page.type('textarea[aria-label="Program code or text"]', 'PI2-nonsense-0000');
    await clickText(page, 'button', 'Import program');
    await page.waitForSelector('.wpb-settings-import-result[data-outcome="error"]');
    await page.$eval('textarea[aria-label="Program code or text"]', t => { t.value = ''; });
    await page.evaluate(() => { const t = document.querySelector('textarea[aria-label="Program code or text"]');
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(t, ''); t.dispatchEvent(new Event('input', { bubbles: true })); });
    await page.type('textarea[aria-label="Program code or text"]', code);
    await clickText(page, 'button', 'Import program');
    await page.waitForFunction(n => JSON.parse(localStorage.getItem('wpb:v1')).saved.length === n + 1, { timeout: 5000 }, before);
    const imported = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).saved.at(-1));
    assert.equal(imported.name, 'Upper Lower'); assert.equal(imported.custom, true);
    assert.deepEqual(imported.days.map(d => d.exercises), program.days.map(d => d.exercises));
    assert.equal(imported.overrides[imported.days[0].id + ':0'].sets, 4);
    assert.deepEqual(errors, []);
    await ctx.close();
  }
  console.log('PASS prototype features in the browser: week strip + recap share, pain check with undo, routine start, PI2 share code import.');
} finally {
  if (browser) await browser.close();
  await new Promise(resolve => server.close(resolve));
}
