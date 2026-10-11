import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const program = { id: 'm219-ui', name: 'UI integration', custom: true, weeks: 4,
    config: { name: 'UI integration', goal: 'both', experience: 'intermediate', progression: 'manual', split: 'custom', weeks: 4, days: 1, session: 's60', unit: 'lb', deload: false },
    days: [{ id: 'm219-upper', label: 'Upper', primaryIndex: 0, exercises: ['bb-bench', 'inc-curl'] }],
    overrides: { 'm219-upper:0': { sets: 4, reps: '6-8', rir: '2' }, 'm219-upper:1': { sets: 3, reps: '10-15', rir: '2' } } };
const initial = { v: 13, savedAt: 1, saved: [program], cycles: [], history: [{ id: 'past', programId: 'other', date: '2026-09-25', unit: 'lb', exercises: [{ id: 'bb-bench', sets: [{ weight: 100, reps: 8, done: true }] }] }], perf: {}, drafts: {}, tombs: {},
    seenIntro: 999, seenWhatsNew: 999, pinnedId: program.id, theme: 'amethyst', restScale: 1, restAutoStart: false, warmupCard: false, unit: 'lb', unitChosen: true, experience: 'intermediate', bodyweight: 185, sex: 'male', age: 30,
    installDismissedAt: Date.now(), lastBackup: Date.now() };
const live = { schemaVersion: 2, programId: program.id, dayId: program.days[0].id, weekIndex: 1, dayExSig: 'bb-bench|inc-curl', exIdx: 0, readiness: { label: 'Normal', factor: 1 }, elapsedMs: 120000, restPaused: true, restRemain: 47, restMax: 90, runPaused: true,
    data: ['bb-bench', 'inc-curl'].map((id, slot) => ({ id, slot, sets: [{ weight: slot ? '40' : '200', reps: slot ? '12' : '8', done: false, auto: false, valueOwner: 'user', target: { w: slot ? '40' : '200', reps: slot ? '10-15' : '6-8', rir: '2' } }] })) };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const name = new URL(req.url, 'http://localhost').pathname;
    const file = path.resolve(root, '.' + (name === '/' ? '/index.html' : name));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8774, '127.0.0.1', resolve));
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    const page = await browser.newPage(), errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.setViewport({ width: 320, height: 740, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
    await page.evaluateOnNewDocument((store, snapshot) => {
        if (!localStorage.getItem('m219-seeded')) { localStorage.setItem('m219-seeded', '1'); localStorage.setItem('wpb:v1', JSON.stringify(store)); localStorage.setItem('wpb:live', JSON.stringify(snapshot)); }
        const Native = window.AudioContext;
        window.__m219Audio = { contexts: 0, closed: 0, starts: 0 };
        if (Native) window.AudioContext = new Proxy(Native, { construct(Target, args) {
            const ac = new Target(...args); window.__m219Audio.contexts++;
            const close = ac.close.bind(ac); ac.close = () => { window.__m219Audio.closed++; return close(); };
            const create = ac.createOscillator.bind(ac); ac.createOscillator = () => {
                const o = create(), start = o.start.bind(o); o.start = (...a) => { window.__m219Audio.starts++; return start(...a); }; return o;
            }; return ac;
        } });
    }, initial, live);
    await page.goto('http://127.0.0.1:8774/', { waitUntil: 'networkidle0' });
    await page.waitForSelector('.wpb-tabbar');
    const settled = () => page.evaluate(() => Promise.allSettled(document.getAnimations().filter(a => a.effect?.getComputedTiming().iterations !== Infinity).map(a => a.finished)));
    const clickText = text => page.$$eval('button', (buttons, label) => { const b = buttons.find(n => n.textContent.trim() === label); if (!b) throw new Error('Missing button: ' + label); b.click(); }, text);
    await page.click('[data-tab="plan"]'); await page.waitForSelector('.wpb-plan-view');
    const standard = await page.$eval('[data-plan-week="1"]', n => n.textContent);
    await page.click('[data-tab="settings"]'); await clickText('Long');
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:v1')).restScale === 1.3);
    await page.click('[data-tab="plan"]'); await page.waitForSelector('.wpb-plan-view');
    const longer = await page.$eval('[data-plan-week="1"]', n => n.textContent);
    assert.notEqual(longer, standard, 'Plan refreshes time after changing rest length');
    assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).saved), initial.saved, 'changing rest duration cannot rewrite training prescriptions');

    const themes = await page.evaluate(async () => Object.keys((await import('/modules/App.js')).THEMES));
    const r36Observations = [];
    for (const theme of themes) {
        await page.click('[data-tab="settings"]'); await page.waitForSelector('[data-theme-summary]');
        await page.$eval('.wpb-theme-picker', n => { n.open = true; });
        await page.click('[data-theme-option="' + theme + '"]');
        await page.waitForFunction(id => JSON.parse(localStorage.getItem('wpb:v1')).theme === id, {}, theme);
        for (const tab of ['home', 'plan', 'progress', 'profile', 'settings']) {
            await page.click('[data-tab="' + tab + '"]'); await settled();
            const bounds = await page.$eval('.wpb', n => ({ width: n.getBoundingClientRect().width, scroll: n.scrollWidth }));
            assert.ok(bounds.width <= 321 && bounds.scroll <= 321, JSON.stringify({ theme, tab, bounds }));

            // R36 deep audit: resolve rendered text against the nearest opaque
            // ancestor surface (including alpha-composited painted containers).
            // Capture cases before deciding which theme roles to adjust. This
            // stage deliberately audits, rather than overriding, native data.
            const nestedContrast = await page.evaluate(tab => {
                const selectors = {
                    home:['.wpb-home .wpb-home-hero','.wpb-home .wpb-action-tile'],
                    plan:['.wpb-plan-up-next','.wpb-plan-glance-tile'],
                    progress:['.wpb-progress-glance-item','.wpb-progress .wpb-history-filter-help'],
                    profile:['.wpb-profile-stat','.wpb-profile-strength-cell'],
                    settings:['.wpb-settings-card','.wpb-settings-row']
                }[tab] || [];
                const ctx=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
                const rgba=color=>{
                    ctx.clearRect(0,0,1,1);ctx.fillStyle='rgba(0,0,0,0)';
                    try{ctx.fillStyle=color;ctx.fillRect(0,0,1,1);}catch{return [0,0,0,0]}
                    return [...ctx.getImageData(0,0,1,1).data].map((x,i)=>i===3?x/255:x/255);
                };
                const blend=(front,back)=>front.slice(0,3).map((x,i)=>x*front[3]+back[i]*(1-front[3]));
                const lum=rgb=>{const v=rgb.map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);
                    return .2126*v[0]+.7152*v[1]+.0722*v[2]};
                const contrast=(x,y)=>{const a=lum(x),b=lum(y);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05)};
                const background=el=>{
                    let bg=[1,1,1],stack=[];
                    for(let n=el;n&&n.nodeType===1;n=n.parentElement)stack.push(n);
                    for(const n of stack.reverse()){const c=rgba(getComputedStyle(n).backgroundColor);bg=blend(c,bg)}
                    return bg;
                };
                return selectors.map(selector=>{
                    const container=[...document.querySelectorAll(selector)].find(el=>el.getClientRects().length);
                    if(!container)return {selector,missing:true};
                    const candidates=[container,...container.querySelectorAll('span,small,p,h1,h2,h3,button,.mono,div')];
                    const label=candidates.find(el=>el.getClientRects().length &&
                        [...el.childNodes].some(n=>n.nodeType===3&&n.textContent.trim().length>=2));
                    if(!label)return {selector,noLabel:true};
                    const c=getComputedStyle(label),paint=background(label),fg=rgba(c.color);
                    const ratio=contrast(blend(fg,[...paint,1]),paint);
                    return {selector,label:label.textContent.trim().slice(0,48),
                        ratio:+ratio.toFixed(2),font:c.fontSize,paint:c.color};
                });
            }, tab);
            r36Observations.push({theme,tab,nestedContrast});
            // R34/R35: verify actual responsive tab targets and rendered color
            // roles under each of the 20 saved themes, not just source tokens.
            const accessibility = await page.evaluate(tab => {
                const nav=document.querySelector('.wpb-tabbar[data-pi-m3-track]');
                const selected=nav?.querySelector('button[aria-current="page"]');
                const primary=tab==='home' ? document.querySelector('.wpb-home .wpb-home-hero .wpb-primary-action') : null;
                const ctx=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
                const channels=value=>{
                    ctx.clearRect(0,0,1,1);
                    ctx.fillStyle='#000';ctx.fillStyle=value;
                    ctx.fillRect(0,0,1,1);
                    return [...ctx.getImageData(0,0,1,1).data];
                };
                const lum=value=>{
                    const rgb=channels(value).slice(0,3).map(x=>x/255);
                    const l=rgb.map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);
                    return .2126*l[0]+.7152*l[1]+.0722*l[2];
                };
                const ratio=(a,b)=>{
                    const x=lum(a),y=lum(b);
                    return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
                };
                const navStyle=getComputedStyle(nav),selectedStyle=getComputedStyle(selected);
                const ringStyle=getComputedStyle(nav,'::before');
                const labels=[...nav.querySelectorAll('button[data-tab]')].map(button=>{
                    const box=button.getBoundingClientRect(),label=button.querySelector('span');
                    return {tab:button.dataset.tab,w:box.width,h:box.height,
                        labelOverflows:!!label&&label.scrollWidth>label.clientWidth+1};
                });
                return {
                    labels,
                    navLabelRatio:ratio(selectedStyle.color,navStyle.backgroundColor),
                    navOutlineRatio:ratio(ringStyle.outlineColor,navStyle.backgroundColor),
                    primaryRatio:primary ? ratio(getComputedStyle(primary).color,getComputedStyle(primary).backgroundColor) : null,
                    primaryHeight:primary?.getBoundingClientRect().height ?? null,
                    outlineWidth:parseFloat(ringStyle.outlineWidth)
                };
            }, tab);
            assert.ok(accessibility.labels.length===5 &&
                accessibility.labels.every(b=>b.w>=44 && b.h>=44 && !b.labelOverflows),
                `R34 ${theme}/${tab} bottom navigation remains touch-safe and legible: ${JSON.stringify(accessibility)}`);
            assert.ok(accessibility.navLabelRatio>=4.5 &&
                accessibility.navOutlineRatio>=3 && accessibility.outlineWidth>=2,
                `R35 ${theme}/${tab} semantic navigation contrast: ${JSON.stringify(accessibility)}`);
            if(tab==='home') assert.ok(accessibility.primaryRatio>=4.5 && accessibility.primaryHeight>=44,
                `R35 ${theme} filled primary action must have 4.5:1 text contrast: ${JSON.stringify(accessibility)}`);
        }
        const contrast = await page.$$eval('.wpb-settings .wpb-segmented>button[aria-pressed="true"]', buttons => {
            const luminance = color => {
                const rgb = color.match(/[\d.]+/g).slice(0, 3).map(Number).map(v => v / 255);
                const linear = rgb.map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4);
                return .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2];
            };
            return buttons.map(button => {
                const css = getComputedStyle(button), text = luminance(css.color);
                // The M3 indicator is painted by the tab group's ::before. Contrast
                // must use the visible selected surface, not the transparent button.
                const group = button.closest('[data-pi-m3-track]');
                const surface = group ? group.style.getPropertyValue('--pi-m3-track-fill') : css.backgroundColor;
                // The original theme can intentionally use a contrast-safe dark
                // selection fill with a bright accent elsewhere. Check that the M3
                // moving track reproduces that visible fill, not the inline accent.
                if (group) delete group.dataset.piM3Track;
                const originalSelectedFill = getComputedStyle(button).backgroundColor;
                if (group) group.dataset.piM3Track = '1';
                const background = luminance(surface);
                const ratio = (Math.max(text, background) + .05) / (Math.min(text, background) + .05);
                return { label: button.textContent.trim(), ratio,
                    textColor: css.color, originalSelectedFill, surfaceFill: surface,
                    fresh: !group || originalSelectedFill === surface };
            });
        });
        assert.ok(contrast.length >= 4 && contrast.every(item => item.ratio >= 4.5 && item.fresh), `${theme}: selected settings remain legible: ${JSON.stringify(contrast)}`);
    }
    const r36Fails=r36Observations.flatMap(({theme,tab,nestedContrast})=>
        nestedContrast.filter(x=>Number.isFinite(x.ratio)&&x.ratio<4.5)
            .map(x=>({theme,tab,...x})));
    console.log('R36 NESTED COLOR AUDIT '+JSON.stringify({
        themeCount:themes.length,samples:r36Observations.reduce((n,x)=>n+x.nestedContrast.filter(y=>Number.isFinite(y.ratio)).length,0),
        failures:r36Fails.length,examples:r36Fails.slice(0,35)
    }));
    assert.ok(themes.length===20 && r36Observations.length===themes.length*5 &&
        r36Observations.reduce((n,x)=>n+x.nestedContrast.filter(y=>Number.isFinite(y.ratio)).length,0)>=170 &&
        r36Fails.length===0,
        'R36 rendered secondary labels meet 4.5:1 on all 20 saved themes: '+JSON.stringify(r36Fails));
    await page.$eval('.wpb-theme-picker', n => { n.open = true; });
    await page.click('[data-theme-option="amethyst"]');
    await page.click('[data-tab="plan"]'); await settled();
    await page.screenshot({ path: path.join(root, 'verification/m219-plan-phone.png') });

    // Test the final CSS cascade with the same closing-sheet structure used by Exit.
    const duration = () => page.evaluate(() => {
        const host = document.createElement('div'); host.className = 'wpb-closing'; host.innerHTML = '<div class="wpb-backdrop"><div data-sheet-drag="1"></div></div>';
        document.querySelector('.wpb').append(host);
        const seconds = parseFloat(getComputedStyle(host.querySelector('[data-sheet-drag]')).animationDuration);
        host.remove(); return seconds;
    });
    assert.ok(await duration() < .001, 'closing sheets honor reduced motion');
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }]);
    assert.ok(await duration() >= .2, 'normal sheet exits still animate');
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);

    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.waitForSelector('.wpb-workout');
    await page.waitForFunction(() => window.__m219Audio.contexts === 1);
    assert.ok(await page.$eval('.wpb-workout-summary', n => n.scrollHeight <= n.clientHeight + 1), 'workout set/exercise summary cannot be clipped by its touch target');
    await page.click('[data-testid="set-0-0"] button[aria-label="Mark set done"]');
    await page.waitForFunction(() => window.__m219Audio.starts >= 1);
    const scale = await page.$$eval('.wpb-effort-scale button', nodes => nodes.map(n => { const r = n.getBoundingClientRect(); return { top: r.top, left: r.left, right: r.right }; }));
    assert.equal(scale.length, 5, 'all five effort choices are available');
    assert.ok(scale.every(r => Math.abs(r.top - scale[0].top) < 1 && r.left >= 0 && r.right <= 320), 'effort choices share one row within the phone');
    await page.click('.wpb-ex-nav-step:nth-child(2)');
    await page.waitForSelector('[data-testid="set-1-0"] button[aria-label="Mark set done"]');
    await page.click('[data-testid="set-1-0"] button[aria-label="Mark set done"]');
    await page.waitForFunction(() => window.__m219Audio.starts >= 2);
    assert.equal(await page.evaluate(() => window.__m219Audio.contexts), 1, 'two PR tones reuse the warmed native context');
    await new Promise(resolve => setTimeout(resolve, 2600));
    await page.screenshot({ path: path.join(root, 'verification/m219-workout-phone.png') });
    await page.evaluate(() => history.back()); await page.waitForSelector('.wpb-workout', { hidden: true });
    await page.waitForFunction(() => window.__m219Audio.closed === 1);
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]'); await page.waitForSelector('.wpb-workout');
    await page.waitForFunction(() => window.__m219Audio.contexts === 2);
    assert.deepEqual(errors, []);
    console.log(`PASS M219 browser: ${themes.length} themes across five tabs at 320px, preserved prescriptions after rest changes, refreshed Plan durations, reduced-motion cascade, two PR beeps sharing a native context, context release on Back and recreation on resume.`);
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
