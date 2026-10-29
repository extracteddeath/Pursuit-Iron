import assert from 'node:assert/strict';
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = new URL('../', import.meta.url).pathname;
const swPath = new URL('../sw.js', import.meta.url);
const originalSw = fs.readFileSync(swPath, 'utf8');
const baseMatch = originalSw.match(/const CACHE="([^"]+)"/);
assert.ok(baseMatch, 'service-worker cache id must be discoverable');
const oldCache = baseMatch[1];
const runtimePath = new URL('../modules/training-domain/analytics.js', import.meta.url);
const revisionProbe = '// m178 runtime content revision probe';
const originalRuntime = fs.readFileSync(runtimePath, 'utf8');
const originalBuild = JSON.parse(fs.readFileSync(new URL('../RELEASE_MANIFEST.json', import.meta.url))).build;
const originalReleaseFiles = ['sw.js', 'RELEASE_MANIFEST.json', 'BUILD_PROFILE.json', 'modules/App.js', 'index.html'].map(file => {
  const url = new URL('../' + file, import.meta.url);
  return [url, fs.readFileSync(url)];
});
let newCache;

const chrome = process.env.CHROME_BIN || '/usr/bin/google-chrome';
assert.ok(fs.existsSync(chrome), `Chrome not found at ${chrome}`);

const mime = { '.js':'text/javascript', '.css':'text/css', '.html':'text/html', '.json':'application/json', '.webmanifest':'application/manifest+json', '.svg':'image/svg+xml', '.png':'image/png' };
const server = http.createServer((req,res) => {
  const pathname = new URL(req.url,'http://localhost').pathname;
  const file = path.resolve(root, '.' + (pathname==='/'?'/index.html':pathname));
  if (!file.startsWith(path.resolve(root)+path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.setHeader('Cache-Control','no-store'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8765,'127.0.0.1',resolve));
let browser;
try {
  browser = await puppeteer.launch({
    executablePath: chrome,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-timer-throttling']
  });
  const page = await browser.newPage();
  await page.evaluateOnNewDocument(() => {
    if (!localStorage.getItem('wpb:v1')) localStorage.setItem('wpb:v1',JSON.stringify({v:13,saved:[],cycles:[],history:[],perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,installDismissedAt:Date.now(),lastBackup:Date.now()}));
  });
  await page.goto('http://127.0.0.1:8765/', { waitUntil: 'networkidle0', timeout: 60000 });

  await page.waitForFunction(async () => {
    if (!('serviceWorker' in navigator)) return false;
    await navigator.serviceWorker.ready;
    return !!navigator.serviceWorker.controller;
  }, { timeout: 30000 });

  await page.evaluate(() => localStorage.setItem('wpb:m178-update-sentinel', JSON.stringify({keep:'logged-work', at:12345})));
  const before = await page.evaluate(async () => ({
    controller: !!navigator.serviceWorker.controller,
    caches: await caches.keys(),
    sentinel: localStorage.getItem('wpb:m178-update-sentinel')
  }));
  assert.ok(before.controller, 'baseline page must be controlled by the installed worker');
  assert.ok(before.caches.includes(oldCache), 'baseline release cache must be active');

  // A numbered runtime release must produce a waiting update through the actual release writer.
  // It must keep the active tab on its old cached source until the user accepts Restart.
  fs.writeFileSync(runtimePath, originalRuntime + '\n' + revisionProbe + '\n');
  execFileSync(process.execPath, ['scripts/finalize-release.mjs', '--next-build'], { cwd: root, stdio: 'pipe' });
  newCache = fs.readFileSync(swPath, 'utf8').match(/const CACHE="([^"]+)"/)[1];
  assert.notEqual(newCache, oldCache, 'runtime-only changes must create a distinct offline release');
  await page.evaluate(async () => {
    const reg = await navigator.serviceWorker.getRegistration();
    if (!reg) throw new Error('registration missing');
    await reg.update();
  });

  await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => /Update ready.*Restart/i.test(b.textContent || '')), { timeout: 30000 });
  // A waiting release is a user decision, not a toast. Keep the action visible
  // across phone viewport changes, foreground checks, and tab navigation.
  for (const [width, height] of [[320, 680], [393, 852]]) {
    await page.setViewport({ width, height, deviceScaleFactor: 1 });
    const prompt = await page.evaluate(() => {
      const button = document.querySelector('button.wpb-notice-in');
      if (!button) return null;
      const rect = button.getBoundingClientRect(), style = getComputedStyle(button);
      return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom,
        width: rect.width, height: rect.height, display: style.display,
        visibility: style.visibility, pointerEvents: style.pointerEvents,
        visibleAtCenter: button.contains(document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2)),
        text: button.textContent || '' };
    });
    assert.ok(prompt && prompt.width > 0 && prompt.height > 0,
      `waiting update action must remain laid out at ${width}px`);
    assert.ok(prompt.left >= 0 && prompt.right <= width && prompt.top >= 0 && prompt.bottom <= height,
      `update action must fit within ${width}px viewport: ${JSON.stringify(prompt)}`);
    assert.notEqual(prompt.visibility, 'hidden');
    assert.notEqual(prompt.display, 'none');
    assert.ok(prompt.height >= 44, `Restart button must remain a real mobile touch target at ${width}px`);
    assert.equal(prompt.pointerEvents, 'auto', 'Restart must remain clickable above page chrome');
    assert.equal(prompt.visibleAtCenter, true, 'Another surface cannot cover the update action');
    assert.match(prompt.text, /Update ready.*Restart/i);
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    assert.ok(await page.$('button.wpb-notice-in'),
      'foreground update rechecks must not dismiss the pending Restart choice');
  }
  await page.click('[data-tab="settings"]');
  await page.waitForFunction(() => !!document.querySelector('button.wpb-notice-in'));
  assert.equal(await page.evaluate(() => !!window.__pursuitUpdateRequested), false,
    'routing and viewport changes must never accept an update implicitly');
  await page.setViewport({ width: 800, height: 600, deviceScaleFactor: 1 });
  console.log('PASS pending update: Restart remains visible and user-gated across narrow phones, foreground checks and Settings navigation.');

  const waitingBeforeClick = await page.evaluate(async () => {
    const reg = await navigator.serviceWorker.getRegistration();
    return !!reg?.waiting;
  });
  assert.equal(waitingBeforeClick, true, 'new worker must wait until the in-app Restart action');
  const activeRuntime = await page.evaluate(async () => (await fetch('./modules/training-domain/analytics.js')).text());
  assert.equal(activeRuntime.includes(revisionProbe), false, 'installing a repair cannot overwrite the active release cache');
  assert.ok(await page.evaluate(async b => (await (await fetch('./modules/App.js')).text()).includes(`const __BUILD__='${b}'`), originalBuild),
    'The running installed app must retain its old build until Restart');

  const nav = page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 });
  await page.locator('button.wpb-notice-in').click();
  await nav;
  await page.waitForFunction(() => !!navigator.serviceWorker.controller, { timeout: 15000 });

  const after = await page.evaluate(async () => ({
    caches: await caches.keys(),
    sentinel: localStorage.getItem('wpb:m178-update-sentinel'),
    requested: !!window.__pursuitUpdateRequested,
    mounted: !!window.__pursuitMounted,
    runtime: await (await fetch('./modules/training-domain/analytics.js')).text()
  }));
  assert.equal(after.sentinel, before.sentinel, 'service-worker takeover/reload must preserve local training storage');
  assert.ok(after.caches.includes(newCache), 'new release cache must become active');
  assert.ok(!after.caches.includes(oldCache), 'old production cache must be removed after activation');
  assert.equal(after.mounted, true, 'app must complete first render after update-driven restart');
  assert.ok(after.runtime.includes(revisionProbe), 'restart must load the repaired runtime from its new offline cache');
  await page.click('[data-tab="settings"]');
  await page.waitForFunction(b => document.body.textContent.includes('build ' + b), {}, originalBuild + 1);
  await page.setOfflineMode(true);
  await page.reload({ waitUntil: 'networkidle0' });
  await page.waitForFunction(() => window.__pursuitMounted);
  await page.click('[data-tab="settings"]');
  await page.waitForFunction(b => document.body.textContent.includes('build ' + b), {}, originalBuild + 1);
  assert.equal(await page.evaluate(() => localStorage.getItem('wpb:m178-update-sentinel')), before.sentinel);
  await page.setOfflineMode(false);
  console.log(`PASS numbered update: installed build ${originalBuild} → ${originalBuild + 1}; Settings and offline reload agree, storage preserved.`);

  // Another window can activate the update while this renderer still shows its old prompt.
  // Merely posting SKIP_WAITING to that now-active worker cannot trigger controllerchange again.
  const raceProbe = revisionProbe + ' activated-by-companion';
  fs.writeFileSync(runtimePath, originalRuntime + '\n' + raceProbe + '\n');
  execFileSync(process.execPath, ['scripts/finalize-release.mjs'], { cwd: root, stdio: 'pipe' });
  await page.evaluate(async () => (await navigator.serviceWorker.getRegistration()).update());
  await page.waitForSelector('button.wpb-notice-in');
  const companion = await browser.newPage();
  await companion.goto('http://127.0.0.1:8765/', { waitUntil: 'networkidle0' });
  await companion.evaluate(async () => {
    const reg = await navigator.serviceWorker.getRegistration();
    if (!reg.waiting) throw new Error('companion has no waiting update');
    reg.waiting.postMessage({ type: 'SKIP_WAITING' });
  });
  await page.bringToFront();
  await page.waitForFunction(async () => {const r=await navigator.serviceWorker.getRegistration();return !r.waiting && r.active===navigator.serviceWorker.controller;}, {polling:100});

  assert.equal(await page.evaluate(() => !!window.__pursuitUpdateRequested), false,
    'activation elsewhere must not reload this page without its own Restart consent');
  const staleNav = page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 10000 });
  await page.locator('button.wpb-notice-in').click();
  await staleNav;
  await page.waitForFunction(() => window.__pursuitMounted);
  assert.equal(await page.evaluate(() => localStorage.getItem('wpb:m178-update-sentinel')), before.sentinel);
  assert.ok(await page.evaluate(async probe => (await (await fetch('./modules/training-domain/analytics.js')).text()).includes(probe), raceProbe));
  await companion.close();
  console.log('PASS stale update prompt: companion activation stays user-gated; real Restart click reloads and retains training storage.');

  console.log('M178 PWA browser lifecycle OK: numbered release surfaced a waiting update, old source stayed isolated, Settings/offline loaded the new build, and local training storage survived.');
} finally {
  fs.writeFileSync(runtimePath, originalRuntime);
  for (const [url, bytes] of originalReleaseFiles) fs.writeFileSync(url, bytes);
  if (browser) await browser.close();
  await new Promise(resolve => server.close(resolve));
}
