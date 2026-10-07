import assert from 'node:assert/strict';
import fs from 'node:fs';
import { execFileSync, spawn } from 'node:child_process';
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
const originalReleaseFiles = ['sw.js', 'RELEASE_MANIFEST.json', 'BUILD_PROFILE.json'].map(file => {
  const url = new URL('../' + file, import.meta.url);
  return [url, fs.readFileSync(url)];
});
let newCache;

const chrome = process.env.CHROME_BIN || '/usr/bin/google-chrome';
assert.ok(fs.existsSync(chrome), `Chrome not found at ${chrome}`);

const server = spawn('python3', ['-m', 'http.server', '8765', '--bind', '127.0.0.1'], { cwd: root, stdio: 'ignore' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
await sleep(800);
let browser;
try {
  browser = await puppeteer.launch({
    executablePath: chrome,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-timer-throttling']
  });
  const page = await browser.newPage();
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

  // A same-build runtime repair must produce a waiting update through the actual release writer.
  // It must keep the active tab on its old cached source until the user accepts Restart.
  fs.writeFileSync(runtimePath, originalRuntime + '\n' + revisionProbe + '\n');
  execFileSync(process.execPath, ['scripts/finalize-release.mjs'], { cwd: root, stdio: 'pipe' });
  newCache = fs.readFileSync(swPath, 'utf8').match(/const CACHE="([^"]+)"/)[1];
  assert.notEqual(newCache, oldCache, 'runtime-only changes must create a distinct offline release');
  await page.evaluate(async () => {
    const reg = await navigator.serviceWorker.getRegistration();
    if (!reg) throw new Error('registration missing');
    await reg.update();
  });

  await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => /Update ready.*Restart/i.test(b.textContent || '')), { timeout: 30000 });
  const waitingBeforeClick = await page.evaluate(async () => {
    const reg = await navigator.serviceWorker.getRegistration();
    return !!reg?.waiting;
  });
  assert.equal(waitingBeforeClick, true, 'new worker must wait until the in-app Restart action');
  const activeRuntime = await page.evaluate(async () => (await fetch('./modules/training-domain/analytics.js')).text());
  assert.equal(activeRuntime.includes(revisionProbe), false, 'installing a repair cannot overwrite the active release cache');

  const nav = page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 30000 });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find(x => /Update ready.*Restart/i.test(x.textContent || ''));
    if (!b) throw new Error('restart button missing');
    b.click();
  });
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

  console.log('M178 PWA browser lifecycle OK: same-build runtime repair surfaced a waiting update, old source stayed isolated, Restart loaded the new cache, and local training storage survived.');
} finally {
  fs.writeFileSync(runtimePath, originalRuntime);
  for (const [url, bytes] of originalReleaseFiles) fs.writeFileSync(url, bytes);
  if (browser) await browser.close();
  server.kill('SIGTERM');
}
