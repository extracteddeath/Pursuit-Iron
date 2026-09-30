import assert from 'node:assert/strict';
import fs from 'node:fs';
import { spawn } from 'node:child_process';
import puppeteer from 'puppeteer-core';

const root = new URL('../', import.meta.url).pathname;
const chrome = process.env.CHROME_BIN || '/usr/bin/google-chrome';
assert.ok(fs.existsSync(chrome), `Chrome not found at ${chrome}`);

const server = spawn('python3', ['-m', 'http.server', '8766', '--bind', '127.0.0.1'], { cwd: root, stdio: 'ignore' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
await sleep(700);
let browser;
try {
  browser = await puppeteer.launch({
    executablePath: chrome,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-timer-throttling']
  });
  const page = await browser.newPage();
  const cdp = await page.createCDPSession();
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  await page.evaluateOnNewDocument(() => {
    window.__m199LongTasks = [];
    try {
      new PerformanceObserver(list => {
        for (const e of list.getEntries()) window.__m199LongTasks.push(Math.round(e.duration));
      }).observe({ entryTypes: ['longtask'] });
    } catch {}
  });

  await page.setRequestInterception(true);
  page.on('request', request => {
    if (/\/modules\/App\.js(?:\?|$)/.test(request.url())) {
      setTimeout(() => request.continue().catch(() => {}), 650);
    } else request.continue().catch(() => {});
  });

  const started = Date.now();
  const nav = page.goto('http://127.0.0.1:8766/', { waitUntil: 'networkidle0', timeout: 30000 });
  await page.waitForSelector('#pursuit-startup-status', { timeout: 5000 });
  const bootText = await page.$eval('#pursuit-startup-status', el => el.textContent?.trim());
  assert.equal(bootText, 'Loading Pursuit Iron…', 'cold start must visibly identify loading while App.js is unavailable');
  const recoveryBeforeMount = await page.$('[data-boot-recovery]');
  assert.equal(recoveryBeforeMount, null, 'normal delayed startup must not enter recovery before first render');

  await nav;
  await page.waitForFunction(() => window.__pursuitMounted === true, { timeout: 15000 });
  const result = await page.evaluate(() => ({
    mounted: !!window.__pursuitMounted,
    startupStillVisible: !!document.getElementById('pursuit-startup'),
    recoveryVisible: !!document.querySelector('[data-boot-recovery]'),
    longTasks: window.__m199LongTasks || [],
    nav: performance.getEntriesByType('navigation')[0] ? {
      domContentLoaded: Math.round(performance.getEntriesByType('navigation')[0].domContentLoadedEventEnd),
      load: Math.round(performance.getEntriesByType('navigation')[0].loadEventEnd)
    } : null
  }));
  const wallMs = Date.now() - started;
  assert.equal(result.mounted, true, 'app must complete first render under a throttled cold start');
  assert.equal(result.recoveryVisible, false, 'cold start must not fall into boot recovery controls');
  assert.equal(result.startupStillVisible, false, 'boot shell must be removed after React commits');
  assert.ok(wallMs < 15000, `4x-CPU cold start exceeded 15s (${wallMs}ms)`);
  const maxLongTask = result.longTasks.length ? Math.max(...result.longTasks) : 0;
  console.log(JSON.stringify({ wallMs, maxLongTask, longTaskCount: result.longTasks.length, navigation: result.nav }, null, 2));
  console.log('M199 browser loading test: pass');
} finally {
  if (browser) await browser.close();
  server.kill('SIGTERM');
}
