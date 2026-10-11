import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
// M3E 2026 design contract: colored surfaces stay tonal, never decorative
// gradients. The one radial-gradient is a transient finger-origin state layer.
const expressiveCss = fs.readFileSync(path.join(root, 'app.css'), 'utf8');
const gradientCount = [...expressiveCss.matchAll(/(?:linear|radial|conic)-gradient\(/g)].length;
assert.equal(gradientCount, 1, 'only the transient touch ink may use a radial gradient');
assert.match(expressiveCss, /--pi-m3e-primary-container:/, 'theme-derived tonal hierarchy required');
assert.match(expressiveCss, /--pi-m3e-surface-container-high:/, 'high-emphasis tonal container required');
assert.match(expressiveCss, /M3 EXPRESSIVE R26 — CROSS-SCREEN INTERACTION CONTRACT/,
    'cross-screen touch and keyboard interaction contract must be present');
assert.doesNotMatch(expressiveCss,
    /\.wpb-workout \.wpb-rest-actions>button\{\s*min-height:40px;/,
    'R26 eliminates superseded 40px rest control rules');
assert.match(expressiveCss, /\.wpb-home \.wpb-home-hero\{[\s\S]*?background-image:none!important;/, 'Home hero must not render a gradient');
const program = { id: 'b831-motion', name: 'Motion check', custom: true, weeks: 6,
    config: { unit: 'lb', goal: 'both', experience: 'intermediate', progression: 'double', split: 'custom', deload: false },
    days: [{ id: 'lower', label: 'Lower', primaryIndex: 0, exercises: ['back-squat', 'inc-curl'] }],
    overrides: { 'lower:0': { sets: 4, reps: '5-8', rir: '2', rest: 120 }, 'lower:1': { sets: 2, reps: '10-15', rir: '2', rest: 90 } } };
const history = [{ id: 'last', programId: program.id, dayId: 'lower', date: 1, unit: 'lb',
    perf: { 'back-squat': { weight: 205, reps: 7, sets: [{ w: 205, r: 7, done: true, rir: 2, rirReported: true }] } } }];
const initial = { v: 13, savedAt: 1, saved: [program], cycles: [], history, perf: history[0].perf,
    drafts: {}, tombs: {}, seenIntro: 999, seenWhatsNew: 999, pinnedId: program.id, theme: 'amethyst',
    restAutoStart: false, warmupCard: false, unit: 'lb', unitChosen: true, experience: 'intermediate',
    bodyweight: 185, installDismissedAt: Date.now(), lastBackup: Date.now() };
const live = { schemaVersion: 2, programId: program.id, dayId: 'lower', weekIndex: 1,
    dayExSig: 'back-squat|inc-curl', exIdx: 0, elapsedMs: 120000, readiness: { label: 'Normal', factor: 1 },
    restPaused: true, restRemain: 47, restMax: 90, runPaused: true,
    data: ['back-squat','inc-curl'].map((id, slot) => ({ id, slot,
        sets: Array.from({ length: slot ? 2 : 4 }, () => ({ weight: slot ? '30' : '190', reps: slot ? '10' : '5',
            done: false, auto: false, valueOwner: 'user', target: { w: slot ? '30' : '190', reps: slot ? '10-15' : '5-8', rir: '2' } })) })) };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
    const name = new URL(req.url, 'http://localhost').pathname;
    const file = path.resolve(root, '.' + (name === '/' ? '/index.html' : name));
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(8794, '127.0.0.1', resolve));
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox','--disable-dev-shm-usage'] });
    for (const width of [320, 430]) {
        // Every phone width has its own storage namespace. The secondary-screen
        // smoke deliberately clears the workout fixture, which must not leak
        // into the next width's dock/menu regression.
        const context = await browser.createBrowserContext();
        let page = await context.newPage(); const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setViewport({ width, height: 844, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
        await page.evaluateOnNewDocument((store, snapshot) => {
            if (localStorage.getItem('b831-seeded')) return;
            localStorage.setItem('b831-seeded', '1'); localStorage.setItem('wpb:v1', JSON.stringify(store)); localStorage.setItem('wpb:live', JSON.stringify(snapshot));
        }, initial, live);
        await page.goto('http://127.0.0.1:8794/', { waitUntil: 'networkidle0' });
        await page.waitForSelector('#root[data-pi-motion="expressive"] .wpb-tabbar');
        // R28 frame efficiency: a settled spring must not request any frames
        // and MutationObserver leaf inserts must not run subtree selectors.
        // Synthetic nodes are entirely outside the trainer's persisted state.
        const idleR28 = await page.evaluate(async () => {
            const host=document.querySelector('.wpb');
            const leaf=document.createElement('div');
            leaf.className='wpb-wizard-step';
            let subtreeScans=0;
            leaf.querySelectorAll=() => { subtreeScans++; return []; };
            host.append(leaf);
            await new Promise(resolve=>setTimeout(resolve,0));
            const stepEntered=leaf.dataset.piStepEnter==='1';
            const probe=document.createElement('div');
            host.append(probe);
            await new Promise(resolve=>setTimeout(resolve,0));
            const {spring}=await import('/modules/ui-motion.js');
            const frame=window.requestAnimationFrame;
            let queued=0,paints=0;
            window.requestAnimationFrame=(...args)=>{ queued++; return frame.apply(window,args); };
            try{
                spring(probe,'--pi-r28-idle',1,{from:1,write:v=>{paints++;probe.style.setProperty('--pi-r28-idle',String(v));}});
            }finally{window.requestAnimationFrame=frame;}
            leaf.remove();probe.remove();
            return {subtreeScans,stepEntered,queued,paints};
        });
        assert.ok(idleR28.subtreeScans===0 && idleR28.stepEntered &&
            idleR28.queued===0 && idleR28.paints<=2,
            'R28 idle motion and leaf updates avoid unnecessary frame/selector work: '+JSON.stringify(idleR28));
        // R29: detached tablists must leave ResizeObserver immediately.
        // React can also move a group within the same mutation batch: that
        // must remain observed and must not duplicate an observation.
        const resizeLifecycleR29 = await page.evaluate(async () => {
            const root=document.querySelector('.wpb');
            const wrapper=document.createElement('section');
            wrapper.className='wpb-progress';
            const group=document.createElement('div');
            group.className='wpb-premium-tabs';
            const button=document.createElement('button');
            button.type='button'; button.setAttribute('aria-selected','true');
            button.textContent='Test tab';
            group.append(button); wrapper.append(group);
            const oldObserve=ResizeObserver.prototype.observe;
            const oldUnobserve=ResizeObserver.prototype.unobserve;
            let observes=0,unobserves=0;
            ResizeObserver.prototype.observe=function(el,...rest){
                if(el===group)observes++;
                return oldObserve.call(this,el,...rest);
            };
            ResizeObserver.prototype.unobserve=function(el,...rest){
                if(el===group)unobserves++;
                return oldUnobserve.call(this,el,...rest);
            };
            const flush=()=>new Promise(resolve=>setTimeout(resolve,20));
            try {
                root.append(wrapper); await flush();
                const firstTracked=group.dataset.piM3Track==='1';
                wrapper.remove(); root.append(wrapper); await flush();
                const movesDontUnobserve=observes===1 && unobserves===0;
                wrapper.remove(); await flush();
                const released=unobserves===1;
                root.append(wrapper); await flush();
                const resumed=observes===2 && group.dataset.piM3Track==='1';
                wrapper.remove(); await flush();
                return {firstTracked,movesDontUnobserve,released,resumed,
                    observes,unobserves};
            } finally {
                wrapper.remove();
                ResizeObserver.prototype.observe=oldObserve;
                ResizeObserver.prototype.unobserve=oldUnobserve;
            }
        });
        assert.ok(resizeLifecycleR29.firstTracked &&
            resizeLifecycleR29.movesDontUnobserve &&
            resizeLifecycleR29.released && resizeLifecycleR29.resumed &&
            resizeLifecycleR29.observes===2 && resizeLifecycleR29.unobserves===2,
            'R29 ResizeObserver groups release on unmount and safely rejoin after move: '+
                JSON.stringify(resizeLifecycleR29));
        // R30: resizing an observed selection group must resolve its latest
        // button bounds even when multiple widths arrive before the next paint.
        // The indicator remains behind the real buttons (not a second hitbox).
        const trackResizeR30=await page.evaluate(async()=>{
            const root=document.querySelector('.wpb');
            const host=document.createElement('section');
            host.className='wpb-progress';
            const group=document.createElement('div');
            group.className='wpb-premium-tabs';
            group.style.cssText='width:148px;display:flex;max-width:none';
            const first=document.createElement('button');
            const second=document.createElement('button');
            for(const button of [first,second]){
                button.type='button';
                button.textContent='Resize test';
                button.style.cssText='flex:1 1 0;min-width:0';
            }
            first.setAttribute('aria-selected','true');
            group.append(first,second);host.append(group);root.append(host);
            const frames=()=>new Promise(resolve=>
                requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
            try{
                await frames();
                const before=Number.parseFloat(group.style.getPropertyValue('--pi-m3-track-width'));
                group.style.width='178px';
                group.style.width='198px';
                group.style.width='218px';
                // Wait for the ResizeObserver callback and its scheduled
                // presentation frame; three rAFs can sample one intermediate
                // button width on a loaded CI phone browser.
                await frames();
                await frames();
                const after=Number.parseFloat(group.style.getPropertyValue('--pi-m3-track-width'));
                const expected=first.offsetWidth;
                const selectedX=first.offsetLeft;
                const targetX=Number.parseFloat(group.style.getPropertyValue('--pi-m3-track-x'));
                const selectedIsReal=first.getAttribute('aria-selected')==='true' &&
                    second.getAttribute('aria-selected')!=='true';
                return {before,after,expected,targetX,selectedX,selectedIsReal,
                    groupWidth:group.offsetWidth};
            }finally{host.remove();}
        });
        assert.ok(Number.isFinite(trackResizeR30.before) &&
            Number.isFinite(trackResizeR30.after) &&
            trackResizeR30.after > trackResizeR30.before + 8 &&
            Math.abs(trackResizeR30.after-trackResizeR30.expected)<2 &&
            Math.abs(trackResizeR30.targetX-trackResizeR30.selectedX)<2 &&
            trackResizeR30.selectedIsReal,
            'R30 coalesced ResizeObserver still tracks final selected button geometry: '+
                JSON.stringify(trackResizeR30));

        // R31: interruption must preserve the newest animation and ownership.
        // Use a detached-from-React but connected fixture to exercise the
        // same exported sheet/dialog transition helpers as the application.
        const interruptR31=await page.evaluate(async()=>{
            const {spring,exitMotion,resumeMotion,setMotionValue}=await import('/modules/ui-motion.js');
            const host=document.createElement('div');
            const dialog=document.createElement('section');
            dialog.className='wpb-dialog';
            const backdrop=document.createElement('div');
            backdrop.className='wpb-backdrop';
            host.append(dialog,backdrop);
            document.body.append(host);
            const move=(el,key)=>v=>el.style.setProperty(key,String(v));
            let retargetFinished=0,firstExit=0,secondExit=0;
            try{
                // A disposer for the first (no-callback) animation must not
                // stop the second spring even though both have null complete.
                const test=document.createElement('div');
                host.append(test);
                const oldStop=spring(test,'--r31-interrupt',80,{
                    from:0,write:move(test,'--r31-interrupt'),stiffness:1700
                });
                spring(test,'--r31-interrupt',20,{
                    write:move(test,'--r31-interrupt'),stiffness:1000,damping:1,
                    precision:.02,complete:()=>retargetFinished++
                });
                oldStop();
                // A direct drag value is also a new owner of the property.
                const dragStop=spring(test,'--r31-drag',75,{
                    from:0,write:move(test,'--r31-drag'),stiffness:1500
                });
                setMotionValue(test,'--r31-drag',13,move(test,'--r31-drag'));
                dragStop();
                const dragPreserved=Number.parseFloat(
                    test.style.getPropertyValue('--r31-drag'))===13;
                const firstStop=exitMotion(host,()=>firstExit++);
                resumeMotion(host);
                firstStop(); // previous disposer after direction reversal
                await new Promise(resolve=>setTimeout(resolve,100));
                exitMotion(host,()=>secondExit++);
                firstStop(); // stale close must never stop a new close
                await new Promise(resolve=>setTimeout(resolve,850));
                const retarget=Number.parseFloat(test.style.getPropertyValue('--r31-interrupt'));
                const y=Number.parseFloat(dialog.style.getPropertyValue('--pi-surface-y'));
                const opacity=Number.parseFloat(dialog.style.getPropertyValue('--pi-surface-opacity'));
                return {retargetFinished,retarget,dragPreserved,firstExit,secondExit,y,opacity};
            } finally{host.remove();}
        });
        assert.ok(interruptR31.retargetFinished===1 &&
            Math.abs(interruptR31.retarget-20)<.1 &&
            interruptR31.dragPreserved &&
            interruptR31.firstExit===0 && interruptR31.secondExit===1 &&
            interruptR31.y>=7.8 && interruptR31.opacity<.05,
            'R31 interrupted and resumed transitions retain only current ownership: '+
                JSON.stringify(interruptR31));




        // R16: a named solid surface must not be a 3%-alpha color wash.
        // Probe all five theme-derived roles in both browser color schemes
        // without touching saved theme state or exercise/workout data.
        const surfaceRoles = await page.evaluate(() => {
            const host = document.querySelector('#root[data-pi-motion="expressive"] .wpb');
            const probe = document.createElement('div');
            probe.style.cssText = 'position:absolute;top:-9999px;left:-9999px;width:1px;height:1px;pointer-events:none';
            host.appendChild(probe);
            const roles = ['--pi-m3e-surface-container-low','--pi-m3e-surface-container',
                '--pi-m3e-surface-container-high','--pi-m3e-primary-container',
                '--pi-m3e-secondary-container'];
            const ctx = document.createElement('canvas').getContext('2d', {willReadFrequently:true});
            const result = {};
            for (const scheme of ['dark','light']) {
                probe.style.colorScheme = scheme;
                result[scheme] = {};
                for (const role of roles) {
                    probe.style.backgroundColor = 'var(' + role + ')';
                    const computed = getComputedStyle(probe).backgroundColor;
                    ctx.clearRect(0,0,1,1);
                    ctx.fillStyle = computed;
                    ctx.fillRect(0,0,1,1);
                    result[scheme][role] = [...ctx.getImageData(0,0,1,1).data];
                }
            }
            probe.remove();
            return result;
        });
        for (const [scheme, colors] of Object.entries(surfaceRoles)) {
            for (const [role, rgba] of Object.entries(colors)) {
                assert.equal(rgba[3],255,'R16 ' + scheme + ' ' + role +
                    ' must be a solid, rendered M3 surface: ' + JSON.stringify(surfaceRoles));
            }
            assert.notDeepEqual(colors['--pi-m3e-surface-container-low'].slice(0,3),
                colors['--pi-m3e-surface-container-high'].slice(0,3),
                'R16 low and high elevations must remain visually distinct in ' + scheme);
        }

        const clickText = text => page.$$eval('button', (buttons, text) => {
            const button = buttons.find(b => b.textContent.includes(text));
            if (!button) throw new Error('Missing button: ' + text);
            button.click();
        }, text);
        // Normalized resolved colors: Chrome returns modern color(srgb ...)
        // for light-dark()/color-mix() but classic rgb(...) for most text.
        const homePalette = async () => page.evaluate(() => {
            const luminance = color => {
                if (!color) return NaN;
                const srgb = color.match(/^color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)/);
                const rgb = color.match(/^rgba?\(\s*([\d.]+)[, ]+([\d.]+)[, ]+([\d.]+)/);
                const numbers = srgb ? srgb.slice(1,4).map(Number) :
                    rgb ? rgb.slice(1,4).map(n => Number(n)/255) : null;
                if (!numbers) return NaN;
                const linear = numbers.map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4);
                return .2126*linear[0]+.7152*linear[1]+.0722*linear[2];
            };
            const paint = element => {
                if (!element) return null;
                const c = getComputedStyle(element);
                const background = luminance(c.backgroundColor), foreground = luminance(c.color);
                return { background, foreground,
                    contrast: (Math.max(background,foreground)+.05)/(Math.min(background,foreground)+.05),
                    backgroundImage:c.backgroundImage, height:element.getBoundingClientRect().height,
                    bg:c.backgroundColor, fg:c.color };
            };
            return { scheme:getComputedStyle(document.documentElement).colorScheme,
                primary:paint(document.querySelector('.wpb-home .wpb-home-hero .wpb-primary-action')),
                secondary:paint(document.querySelector('.wpb-home-create')),
                hero:paint(document.querySelector('.wpb-home-hero')) };
        });
        // Read real resting containers, not source hex colors. The 20-theme
        // UI test already establishes light-theme selection contrast separately.
        const tonalSurfaceReport = async (names) => page.evaluate(names => {
            const output = {};
            for (const [key,selector] of Object.entries(names)) {
                const el = document.querySelector(selector);
                if (!el) { output[key]=null; continue; }
                const c = getComputedStyle(el);
                output[key] = {
                    bg:c.backgroundColor, image:c.backgroundImage,
                    width:el.getBoundingClientRect().width,
                    height:el.getBoundingClientRect().height
                };
            }
            return { dark:getComputedStyle(document.documentElement).colorScheme.includes('dark'), output };
        }, names);
        // Root tabs keep their navigation semantics, respond immediately, and settle without layout growth.
        for (const tab of ['plan','progress','profile','settings','home']) {
            await page.click(`[data-tab="${tab}"]`);
            await page.waitForSelector(`[data-view-frame="${tab}"][data-pi-surface]`);
            // The mounted destination receives a linked entrance sourced from
            // the actual tapped navigation control. No duplicate views, no
            // animation on workout rows, and no change to navigation state.
            if (tab === 'plan' || tab === 'progress') {
                const linked = await page.$eval(`[data-view-frame="${tab}"]`, el => {
                    const lead = el.querySelector('[data-pi-connected="source"]');
                    return { count:el.querySelectorAll('[data-pi-connected="source"]').length,
                        x:lead?.style.getPropertyValue('--pi-connected-x'),
                        y:lead?.style.getPropertyValue('--pi-connected-y') };
                });
                assert.ok(linked.count === 1 && Number.isFinite(parseFloat(linked.x)) &&
                    Number.isFinite(parseFloat(linked.y)) &&
                    Math.abs(parseFloat(linked.x)) <= 24.01 &&
                    Math.abs(parseFloat(linked.y)) <= 18.01,
                    'R9 connected route bounds one actual lead card: '+JSON.stringify(linked));
            }
            await page.waitForFunction(tab => document.querySelector(`[data-view-frame="${tab}"]`)?.style.getPropertyValue('--pi-surface-opacity') === '1', {}, tab);
            assert.equal(await page.$eval(`[data-tab="${tab}"]`, b => b.getAttribute('aria-current')), 'page');
            const navDir = await page.$eval(`[data-view-frame="${tab}"]`, el => ({
                direction:el.dataset.piNavDirection,
                scale:Number.parseFloat(el.style.getPropertyValue('--pi-surface-scale')),
                opacity:Number.parseFloat(el.style.getPropertyValue('--pi-surface-opacity'))
            }));
            assert.equal(navDir.direction, tab==='home' ? 'back' : 'forward',
                'route entrance direction follows actual tab positions: '+JSON.stringify(navDir));
            assert.ok(navDir.scale>=.98 && navDir.scale<=1.001 && navDir.opacity>.95,
                'connected navigation settles to fully interactive page geometry');
            assert.ok(await page.$eval('.wpb', el => el.scrollWidth <= innerWidth + 1), `${tab} fits at ${width}px`);
            // page.click produces a real touch ripple. Check idle icon styling only AFTER
            // animationend clears the transient layer, not while the active ::after paints.
            await page.waitForFunction(tab => !document.querySelector(`[data-tab="${tab}"]`)?.hasAttribute('data-pi-ink'),
                { timeout: 5000 }, tab);
            await page.waitForFunction(() => {
                const el=document.querySelector('.wpb-tabbar[data-pi-m3-track]');
                const b=el?.querySelector('[aria-current="page"]');
                return b && Math.abs(parseFloat(el.style.getPropertyValue('--pi-m3-track-x')) -
                    (b.offsetLeft+(b.offsetWidth-Math.min(60,b.offsetWidth*.78))/2)) < .8;
            }, { timeout: 5000 });
            const navPill = await page.$eval('.wpb-tabbar[data-pi-m3-track]', el => {
                const selected = el.querySelector('[aria-current="page"]');
                const indicator = getComputedStyle(el, '::before');
                const width = Math.min(60, selected.offsetWidth * .78);
                return { height: parseFloat(indicator.height), radius: indicator.borderRadius,
                    width: parseFloat(indicator.width), expectedWidth: width,
                    x: parseFloat(el.style.getPropertyValue('--pi-m3-track-x')),
                    expected: selected.offsetLeft + (selected.offsetWidth-width)/2,
                    fill: el.style.getPropertyValue('--pi-m3-track-fill'),
                    oldPill: getComputedStyle(selected, '::before').display,
                    oldUnderline: getComputedStyle(selected, '::after').display };
            });
            assert.ok(navPill.height >= 32 && navPill.height <= 34 &&
                Math.abs(navPill.width-navPill.expectedWidth)<.8 &&
                Math.abs(navPill.x-navPill.expected)<.8, 'spatial navigation indicator stays centered: ' + JSON.stringify(navPill));
            assert.ok(navPill.radius.includes('px') && navPill.fill.includes('color-mix') &&
                navPill.oldPill === 'none' && navPill.oldUnderline === 'none',
                'moving navigation paints one theme-native pill instead of separate underlines: ' + JSON.stringify(navPill));
            if (tab === 'progress') {
                // Selected group indicator springs independently from actual
                // button hit targets, and settles to an unscaled resting pill.
                await page.waitForFunction(() => {
                    const bar=document.querySelector('.wpb-tabbar[data-pi-m3-track]');
                    return bar?.style.getPropertyValue('--pi-m3-track-stretch') === '1';
                }, { timeout: 5000 });
                assert.ok(await page.$eval('.wpb-tabbar', bar => {
                    const item = bar.querySelector('[aria-current="page"]');
                    return item.getBoundingClientRect().height >= 44 &&
                        parseFloat(bar.style.getPropertyValue('--pi-m3-track-corner')) >= 17.9;
                }), 'R9 selection flex retains real navigation tap geometry');
            }
            if (tab === 'plan') {
                const tonal = await tonalSurfaceReport({
                    featured:'.wpb-plan-glance-tile[data-featured="true"]',
                    secondary:'.wpb-plan-glance-tile:not([data-featured="true"])',
                    next:'.wpb-plan-up-next'
                });
                const {featured,secondary,next}=tonal.output;
                assert.ok(featured && secondary && next,
                    'R11 Plan retains measured real summary and next-day cards: '+JSON.stringify(tonal));
                if (tonal.dark) assert.ok(featured.bg !== secondary.bg &&
                    featured.image === 'none' && secondary.image === 'none' &&
                    next.image === 'none' && featured.width > 0 &&
                    next.height >= 70,
                    'R11 Plan features a single flat tonal week without extra space: '+JSON.stringify(tonal));
            }
            if (tab === 'progress') {
                const tonal = await tonalSurfaceReport({
                    featured:'.wpb-progress-glance-item:first-child',
                    secondary:'.wpb-progress-glance-item:nth-child(2)',
                    history:'.wpb-progress .wpb-history-session'
                });
                const {featured,secondary}=tonal.output;
                assert.ok(featured && secondary,'R11 Progress retains its metric tiles: '+JSON.stringify(tonal));
                if (tonal.dark) assert.ok(featured.bg !== secondary.bg &&
                    featured.image === 'none' && secondary.image === 'none',
                    'R11 Progress maintains a quieter secondary fact: '+JSON.stringify(tonal));
                // R24 history is the real saved-session list. Every visible
                // action must remain touch-safe; never activate edit/delete.
                const historyR24=await page.evaluate(() => {
                    const card=document.querySelector('.wpb-progress .wpb-history-session');
                    if(!card) return null;
                    const style=getComputedStyle(card),r=card.getBoundingClientRect();
                    const buttons=[...card.querySelectorAll('.wpb-history-actions>button')]
                        .filter(el=>el.getClientRects().length)
                        .map(el=>({w:el.getBoundingClientRect().width,h:el.getBoundingClientRect().height}));
                    return {image:style.backgroundImage,w:r.width,
                        overflows:card.scrollWidth>card.clientWidth+1,buttons};
                });
                if(historyR24) assert.ok(historyR24.image==='none' &&
                    historyR24.w>0 && !historyR24.overflows &&
                    historyR24.buttons.every(b=>b.w>=44&&b.h>=44),
                    'R24 history surfaces and real actions fit the phone: '+JSON.stringify(historyR24));
            }
            if (tab === 'profile') {
                const tonal = await tonalSurfaceReport({
                    featured:'.wpb-profile-strength-cell:first-child',
                    secondary:'.wpb-profile-strength-cell:nth-child(2)',
                    identity:'.wpb-profile-identity'
                });
                const {featured,secondary,identity}=tonal.output;
                // A one-lift/one-cell profile is valid, and does not display a
                // second strength tile. Compare the featured cell against the
                // always-present identity surface in that real fixture.
                assert.ok(featured && identity,
                    'R11 Profile retains strength summary and identity: '+JSON.stringify(tonal));
                if (tonal.dark) assert.ok(featured.bg !== (secondary?.bg ?? identity.bg) &&
                    featured.image === 'none' && (secondary?.image ?? 'none') === 'none' &&
                    identity.image === 'none',
                    'R11 Profile emphasis remains tonal for one or multiple cells: '+JSON.stringify(tonal));
            }
            if (tab === 'home') {
                // In the active-workout fixture, the optional Create Program
                // dock is hidden by the resumed-workout bar. Only assert what
                // actually exists; the no-live scenario below checks both.
                const homeAccent = await homePalette();
                assert.ok(homeAccent.primary && homeAccent.primary.height >= 44,
                    'R10 Start Workout retains its accessible 44px+ target: '+JSON.stringify(homeAccent));
                if (homeAccent.scheme.includes('dark')) {
                    assert.ok(homeAccent.primary.contrast >= 4.5 &&
                        homeAccent.primary.background < .30 &&
                        homeAccent.primary.backgroundImage === 'none',
                        'R10 dark Start Workout is subdued, filled and legible: '+JSON.stringify(homeAccent));
                }
            }
            if (tab === 'home') {
                assert.ok(await page.$eval('.wpb-home-hero', el =>
                    parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 30 &&
                    parseFloat(getComputedStyle(el).marginBottom) <= 14),
                    'home hero has a tighter spacious-card hierarchy');
            }
            if (tab === 'home') {
                const bento = await page.$eval('.wpb-home .wpb-action-grid', grid => {
                    const tiles=[...grid.querySelectorAll(':scope > .wpb-action-tile')];
                    const first=tiles[0]?.getBoundingClientRect();
                    return {count:tiles.length, firstWidth:first?.width,
                        gridWidth:grid.getBoundingClientRect().width,
                        heroRadius:parseFloat(getComputedStyle(document.querySelector('.wpb-home-hero')).borderTopLeftRadius)};
                });
                assert.ok(bento.count>=2 && bento.firstWidth >= bento.gridWidth-2 &&
                    bento.heroRadius>=30,
                    'M3 bento uses a full-width feature without extra layout rows: '+JSON.stringify(bento));
            }
            if (tab === 'home') {
                const shape = await page.$eval('.wpb-home', shell => {
                    const header=shell.querySelector('.wpb-home-header'), hero=shell.querySelector('.wpb-home-hero');
                    const stats=[...shell.querySelectorAll('.wpb-metric-strip > .wpb-metric')];
                    const first=stats[0]?.getBoundingClientRect();
                    return {radius:parseFloat(getComputedStyle(header).borderBottomLeftRadius),
                        heroRadius:parseFloat(getComputedStyle(hero).borderTopLeftRadius),
                        headerTint:getComputedStyle(header).backgroundImage,
                        headerSurface:getComputedStyle(header).backgroundColor,
                        statCount:stats.length,statRadius:stats[0]&&parseFloat(getComputedStyle(stats[0]).borderTopLeftRadius),
                        statWidth:first?.width,scrollWidth:shell.scrollWidth,viewport:innerWidth};
                });
                assert.ok(shape.radius>=26 && shape.heroRadius>=35 &&
                    shape.headerTint==='none' && shape.headerSurface!=='transparent' &&
                    shape.headerSurface!=='rgba(0, 0, 0, 0)' && shape.statCount===2 &&
                    shape.statRadius>=20 && shape.statWidth>80 &&
                    shape.scrollWidth<=shape.viewport+1,
                    'R4 connected color-tinted dashboard has true tonal hierarchy without overflow: '+JSON.stringify(shape));
            }
            if (tab === 'plan') {
                const nextTitle = await page.$eval('.wpb-plan-up-next>div:first-child>div:nth-child(2)', el => {
                    const cs=getComputedStyle(el);
                    return {whiteSpace:cs.whiteSpace,overflow:cs.overflow,
                        textOverflow:cs.textOverflow,wrap:cs.overflowWrap};
                });
                assert.ok(nextTitle.whiteSpace==='normal' && nextTitle.overflow!=='hidden' &&
                    nextTitle.textOverflow!=='ellipsis',
                    'R18 Plan next-session name should wrap instead of truncating: '+JSON.stringify(nextTitle));
            }
            if (tab === 'plan') {
                const state = await page.$eval('.wpb-plan-view', el => {
                    const metrics = [...el.querySelectorAll('.wpb-plan-glance-tile')];
                    const first = metrics[0];
                    const group = el.querySelector('.wpb-plan-panes[data-pi-m3-track]');
                    const title = el.querySelector('.wpb-plan-title');
                    return {
                        count:metrics.length,
                        size:parseFloat(getComputedStyle(title).fontSize),
                        firstRadius:first ? parseFloat(getComputedStyle(first).borderTopLeftRadius) : 0,
                        firstSize:first ? parseFloat(getComputedStyle(first.lastElementChild).fontSize) : 0,
                        buttons:group?.querySelectorAll('button').length,
                        selected:group?.querySelectorAll('[aria-pressed="true"]').length,
                        trackFill:group?.style.getPropertyValue('--pi-m3-track-fill'),
                        overflow:el.scrollWidth>el.clientWidth+1
                    };
                });
                assert.ok(state.count===3 && state.size>=27 && state.firstRadius>=26 &&
                    state.firstSize>=24 && state.buttons===4 && state.selected===1 &&
                    state.trackFill?.length>3 && !state.overflow,
                    'Plan has a real featured week and elastic four-pane navigator: '+JSON.stringify(state));
            }
            if (tab === 'progress') {
                const metrics = await page.$eval('.wpb-progress [data-progress-glance]', grid => {
                    const cards = [...grid.querySelectorAll('.wpb-progress-glance-item')];
                    const first = cards[0]?.getBoundingClientRect(),second = cards[1]?.getBoundingClientRect();
                    return {count:cards.length, firstHeight:first?.height, secondHeight:second?.height,
                        firstFont:parseFloat(getComputedStyle(cards[0].querySelector('.mono')).fontSize),
                        overflow:grid.scrollWidth>grid.clientWidth+1};
                });
                assert.ok(metrics.count===3 && metrics.firstFont>=22 && !metrics.overflow &&
                    Math.abs(metrics.firstHeight-metrics.secondHeight)<3,
                    'R18 Progress has a compact, equal-height metric row at phone widths: '+JSON.stringify(metrics));
            }
            if (tab === 'progress') {
                assert.ok(await page.$eval('.wpb-progress .wpb-premium-tabs', el =>
                    parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 14),
                    'progress uses a unified pill-tab surface');
            }
            if (tab === 'settings') {
                const heading = await page.$eval('.wpb-settings', el => {
                    const header=el.querySelector(':scope > .wpb-page-header');
                    const picker=el.querySelector('.wpb-settings-jump-control');
                    const select=picker?.querySelector('select');
                    const title=header?.querySelector('h1');
                    return { headerHeight:header?.getBoundingClientRect().height,
                        pickerHeight:picker?.getBoundingClientRect().height,
                        selectHeight:select?.getBoundingClientRect().height,
                        titleSize:title ? parseFloat(getComputedStyle(title).fontSize) : 0,
                        pickerBackground:picker ? getComputedStyle(picker).backgroundImage : '',
                        overflow:el.scrollWidth>el.clientWidth+1 };
                });
                assert.ok(heading.titleSize>=27 && heading.pickerHeight>=44 &&
                    heading.selectHeight>=44 && heading.headerHeight<115 &&
                    heading.pickerBackground==='none' && !heading.overflow,
                    'R18 Settings keeps a compact header and functional section jump: '+JSON.stringify(heading));
            }
            if (tab === 'settings') {
                assert.ok(await page.$eval('.wpb-settings-card', el =>
                    parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 15),
                    'settings sections share the card geometry');
            }
            // Layout and typography: meaningful hierarchy without overflow at 320 or 430px.
            if (tab === 'home') {
                const homeType = await page.$eval('.wpb-home-header h1', el => {
                    const title = getComputedStyle(el);
                    const logo = el.closest('.wpb-home-header').querySelector('div[aria-hidden="true"]');
                    return { size: parseFloat(title.fontSize), weight: Number(title.fontWeight),
                        logoHeight: logo.getBoundingClientRect().height,
                        scrolled: el.closest('.wpb-home')?.dataset.scrolled==='1' };
                });
                const expectedSize=homeType.scrolled
                    ? homeType.size>=20 && homeType.size<=25
                    : homeType.size>=29 && homeType.size<=37;
                assert.ok(expectedSize && homeType.weight >= 700,
                    'Home title follows expanded/compact expressive type scale: '+JSON.stringify(homeType));
                assert.ok(homeType.logoHeight >= 24, 'Home header spacing does not collapse its logo');
            }
            if (tab === 'progress') {
                const progressType = await page.$eval('.wpb-progress', el => {
                    const title = getComputedStyle(el.querySelector('.wpb-progress-title'));
                    const metric = el.querySelector('.wpb-progress-glance-item > .mono');
                    return { size: parseFloat(title.fontSize),
                        metric: metric ? parseFloat(getComputedStyle(metric).fontSize) : null };
                });
                assert.ok(progressType.size >= 26 && progressType.metric >= 21,
                    'Progress keeps dominant title and readable numerical summaries');
            }
            if (tab === 'settings') {
                const settingsType = await page.$eval('.wpb-settings', el => {
                    const title = getComputedStyle(el.querySelector('.wpb-settings-title'));
                    const row = el.querySelector('.wpb-settings-row');
                    return { title: parseFloat(title.fontSize),
                        rowHeight: row?.getBoundingClientRect().height };
                });
                assert.ok(settingsType.title >= 12 && settingsType.title <= 15,
                    'Settings section headings are legible and distinct');
                assert.ok(settingsType.rowHeight == null || settingsType.rowHeight >= 44,
                    'compact Settings rows retain adequate height');
            }
            if (tab === 'profile') {
                const profileType = await page.$eval('.wpb-profile', el => {
                    const title = getComputedStyle(el.querySelector('.wpb-profile-title'));
                    const stat = el.querySelector('.wpb-profile-strength-cell');
                    return { title: parseFloat(title.fontSize),
                        radius: stat ? parseFloat(getComputedStyle(stat).borderTopLeftRadius) : null };
                });
                assert.ok(profileType.title >= 26 && profileType.radius >= 11,
                    'Profile uses the shared title scale and consistent stat geometry');
                // R17: actual profile dashboard geometry, not the old
                // R5 full-width Push tile with its orphaned last row.
                const profileR17 = await page.$eval('.wpb-profile', el => {
                    const rect = x => x?.getBoundingClientRect();
                    const header=el.querySelector('.wpb-profile-head');
                    const identity=el.querySelector('.wpb-profile-identity');
                    const edit=el.querySelector('.wpb-profile-edit');
                    const stats=[...el.querySelectorAll('.wpb-profile-stat')].map(rect);
                    const grid=el.querySelector('.wpb-profile-strength-grid');
                    const cells=grid ? [...grid.querySelectorAll('.wpb-profile-strength-cell')].map(rect) : [];
                    const identityPaint=identity ? getComputedStyle(identity) : null;
                    return {
                        title:parseFloat(getComputedStyle(el.querySelector('.wpb-profile-title')).fontSize),
                        headerHeight:rect(header)?.height ?? 0,
                        identityHeight:rect(identity)?.height ?? 0,
                        identityBorder:identityPaint?.borderTopWidth,
                        identityBackground:identityPaint?.backgroundColor,
                        editHeight:rect(edit)?.height ?? 0,
                        columns:grid ? getComputedStyle(grid).gridTemplateColumns.split(' ').length : 0,
                        cells:cells.map(b=>({x:b.x,y:b.y,width:b.width,height:b.height})),
                        stats:stats.map(b=>({y:b.y,width:b.width,height:b.height})),
                        overflow:el.scrollWidth>el.clientWidth+1
                    };
                });
                assert.ok(profileR17.title>=27 && profileR17.headerHeight<=112 &&
                    profileR17.identityHeight<=40 && profileR17.identityBorder==='0px' &&
                    profileR17.identityBackground==='rgba(0, 0, 0, 0)' &&
                    profileR17.editHeight>=44 && !profileR17.overflow,
                    'R17 Profile is compact, readable and editable: '+JSON.stringify(profileR17));
                assert.ok(profileR17.stats.length===3 &&
                    profileR17.stats.every(s=>Math.abs(s.y-profileR17.stats[0].y)<=1 && s.width>=60),
                    'R17 three lifetime summary stats share a stable row: '+JSON.stringify(profileR17));
                if (profileR17.cells.length>=2) {
                    assert.equal(profileR17.columns,2,
                        'R17 strength metrics use a balanced two-column grid');
                    const [a,b]=profileR17.cells;
                    assert.ok(Math.abs(a.y-b.y)<2 && Math.abs(a.width-b.width)<3 &&
                        a.height>=60 && b.height>=60,
                        'R17 Push/Pull are equally sized peers: '+JSON.stringify(profileR17));
                }
                if (profileR17.cells.length===4) {
                    const [,,c,d]=profileR17.cells;
                    assert.ok(Math.abs(c.y-d.y)<2 && Math.abs(c.width-d.width)<3,
                        'R17 Squat/Hinge have no orphaned row: '+JSON.stringify(profileR17));
                }
            }
            if (width === 430) await page.screenshot({ path: path.join(root, `verification/b831-${tab}-phone.png`) });
        }
        // The real page scroll controller already marks scrolled chrome.
        // Exercise the adaptive top bar without introducing an independent scroll handler.
        await page.click('[data-tab="home"]');
        const homeScroller = await page.$eval('.wpb-home .wpb-page-scroll', el => ({
            max:el.scrollHeight-el.clientHeight
        }));
        if (homeScroller.max>40) {
            await page.$eval('.wpb-home .wpb-page-scroll', el => {
                el.scrollTop=90;
                el.dispatchEvent(new Event('scroll',{bubbles:true}));
            });
            await page.waitForFunction(() => document.querySelector('.wpb-home')?.dataset.scrolled==='1');
            await page.waitForFunction(() => {
                const header=document.querySelector('.wpb-home .wpb-home-header');
                return header && parseFloat(getComputedStyle(header).borderBottomLeftRadius)<=17;
            }, { timeout: 5000 });
            assert.equal(await page.$eval('.wpb-home', el => el.dataset.scrolled), '1',
                'adaptive M3 app bar compresses after real content scroll');
            await page.$eval('.wpb-home .wpb-page-scroll', el => {
                el.scrollTop=0;
                el.dispatchEvent(new Event('scroll',{bubbles:true}));
            });
            await page.waitForFunction(() => document.querySelector('.wpb-home')?.dataset.scrolled==='0');
        }
        // Native navigation semantics and DOM children remain constant while a
        // single measured indicator travels between nonadjacent destinations.
        await page.click('[data-tab="plan"]');
        await page.waitForFunction(() => {
            const el=document.querySelector('.wpb-tabbar');
            const b=el?.querySelector('[aria-current="page"]');
            return b?.dataset.tab==='plan' &&
                Math.abs(parseFloat(el.style.getPropertyValue('--pi-m3-track-x')) -
                  (b.offsetLeft+(b.offsetWidth-Math.min(60,b.offsetWidth*.78))/2))<.8;
        });
        const navStart=await page.$eval('.wpb-tabbar',x=>parseFloat(x.style.getPropertyValue('--pi-m3-track-x')));
        await page.click('[data-tab="profile"]');
        await page.waitForFunction(() => {
            const el=document.querySelector('.wpb-tabbar');
            const b=el?.querySelector('[aria-current="page"]');
            return b?.dataset.tab==='profile' &&
                Math.abs(parseFloat(el.style.getPropertyValue('--pi-m3-track-x')) -
                  (b.offsetLeft+(b.offsetWidth-Math.min(60,b.offsetWidth*.78))/2))<.8;
        });
        const navEnd=await page.$eval('.wpb-tabbar',x=>parseFloat(x.style.getPropertyValue('--pi-m3-track-x')));
        assert.ok(Math.abs(navEnd-navStart)>20,'navigation spring moves between real tabs');
        assert.ok(await page.$eval('[data-view-frame="profile"]',el=>el.dataset.piCascade==='1'),
            'visible profile components are coordinated by the page-level motion system');
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),initial.saved,
            'global navigation motion never modifies saved program data');
        // R24: test the finish-sheet CSS even though this fixture deliberately
        // keeps its unfinished workout alive. Do not finish or mutate the log.
        const finishThemeR24=await page.evaluate(() => {
            const root=document.querySelector('.wpb');
            const sheet=document.createElement('div');
            sheet.className='wpb-finish-sheet';
            const metrics=document.createElement('div');
            metrics.className='wpb-finish-metrics';
            const metric=document.createElement('div');
            metric.textContent='Summary metric';
            metrics.append(metric);sheet.append(metrics);root.append(sheet);
            const panel=getComputedStyle(sheet),tile=getComputedStyle(metric);
            const c=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
            const alpha=value=>{c.clearRect(0,0,1,1);c.fillStyle=value;c.fillRect(0,0,1,1);return c.getImageData(0,0,1,1).data[3];};
            const out={panelAlpha:alpha(panel.backgroundColor),
                panelImage:panel.backgroundImage,tileAlpha:alpha(tile.backgroundColor),
                tileImage:tile.backgroundImage,
                tileRadius:parseFloat(tile.borderTopLeftRadius)};
            sheet.remove();
            return out;
        });
        assert.ok(finishThemeR24.panelAlpha===255 && finishThemeR24.tileAlpha===255 &&
            finishThemeR24.panelImage==='none' && finishThemeR24.tileImage==='none' &&
            finishThemeR24.tileRadius>=18,
            'R24 finished-workout summary uses solid rounded theme-native surfaces: '+JSON.stringify(finishThemeR24));
        // The Material 3 tab indicator actually travels with the semantic selection,
        // is theme-derived, and changes paint only (not target geometry or DOM children).
        await page.click('[data-tab="progress"]');
        await page.waitForSelector('.wpb-progress .wpb-premium-tabs[data-pi-m3-track]');
        const beforeTrack = await page.$eval('.wpb-progress .wpb-premium-tabs', el => ({
            x: Number.parseFloat(el.style.getPropertyValue('--pi-m3-track-x')),
            fill: el.style.getPropertyValue('--pi-m3-track-fill'),
            children: el.childElementCount,
            widths: [...el.children].map(b => b.offsetWidth)
        }));
        assert.ok(beforeTrack.fill.length > 3 && beforeTrack.fill !== 'transparent',
            'M3 track borrows the active Pursuit theme surface');
        await page.click('.wpb-progress .wpb-premium-tabs>button:nth-child(2)');
        await page.waitForFunction(() => {
            const group = document.querySelector('.wpb-progress .wpb-premium-tabs');
            const selected = group?.querySelector('[aria-selected="true"]');
            return selected?.textContent?.trim() === 'Lifts' &&
                Math.abs(Number.parseFloat(group.style.getPropertyValue('--pi-m3-track-x')) - selected.offsetLeft) < .3 &&
                Math.abs(Number.parseFloat(group.style.getPropertyValue('--pi-m3-track-width')) - selected.offsetWidth) < .3 &&
                Number.parseFloat(selected.style.getPropertyValue('--pi-choice-grow')) > 1.18;
        }, { timeout: 5000 });
        const afterTrack = await page.$eval('.wpb-progress .wpb-premium-tabs', el => {
            const selected = el.querySelector('[aria-selected="true"]');
            return { x: Number.parseFloat(el.style.getPropertyValue('--pi-m3-track-x')),
                w: Number.parseFloat(el.style.getPropertyValue('--pi-m3-track-width')),
                targetW: selected.offsetWidth, childCount: el.childElementCount,
                indicator: getComputedStyle(el, '::before').content,
                activeBackground: getComputedStyle(selected).backgroundColor };
        });
        assert.ok(afterTrack.x > beforeTrack.x && Math.abs(afterTrack.w - afterTrack.targetW) < .15,
            'shared M3 selection slides and resizes to the exact chosen segment');
        assert.ok(afterTrack.w > beforeTrack.widths[1] + 4,
            'selected Progress segment physically expands while adjacent segments yield');
        assert.equal(afterTrack.childCount, beforeTrack.children,
            'selection motion never adds a DOM element or reduces tap targets');
        assert.ok(afterTrack.indicator !== 'none' && afterTrack.activeBackground === 'rgba(0, 0, 0, 0)',
            'moving indicator paints the selection, not the old static button fill');
        if (width === 430)
            await page.screenshot({ path: path.join(root, 'verification/b831-m3-selected-progress-phone.png') });
        // True tablist keyboard navigation (including screen-reader-friendly selection)
        // must update the panel and follow the very same animated track.
        await page.focus('.wpb-progress .wpb-premium-tabs>button:nth-child(2)');
        await page.keyboard.press('ArrowRight');
        await page.waitForFunction(() => {
            const group = document.querySelector('.wpb-progress .wpb-premium-tabs');
            const selected = group?.querySelector('[aria-selected="true"]');
            return selected?.textContent?.trim() === 'Volume' &&
                Math.abs(Number.parseFloat(group.style.getPropertyValue('--pi-m3-track-x')) - selected.offsetLeft) < .15;
        }, { timeout: 5000 });
        await page.keyboard.press('Home');
        await page.waitForFunction(() => {
            const group = document.querySelector('.wpb-progress .wpb-premium-tabs');
            return group?.querySelector('[aria-selected="true"]')?.textContent?.trim() === 'Sessions';
        }, { timeout: 5000 });
        assert.equal(await page.$eval('.wpb-progress .wpb-premium-tabs>button:first-child',
            b => document.activeElement === b), true,
            'M3 keyboard Home moves focus to the first Progress tab');
        // Training-history grouping is a real two-option control; the existing
        // React state updates a painted indicator without resizing either button.
        await page.waitForSelector('.wpb-progress .wpb-progress-grouping[data-pi-m3-track]');
        const historyGrouping = await page.$eval('.wpb-progress-grouping', el => ({
            selected: el.querySelector('[aria-pressed="true"]')?.textContent?.trim(),
            count: el.childElementCount, x: Number.parseFloat(el.style.getPropertyValue('--pi-m3-track-x')),
            widths: [...el.children].map(b => b.offsetWidth),
            fill: el.style.getPropertyValue('--pi-m3-track-fill')
        }));
        assert.equal(historyGrouping.count, 2, 'both history group choices remain present');
        assert.ok(historyGrouping.fill.length > 3 && historyGrouping.fill !== 'transparent',
            'history grouping borrows the theme selection surface');
        await page.click('.wpb-progress-grouping > button:nth-child(2)');
        await page.waitForFunction(() => {
            const g = document.querySelector('.wpb-progress-grouping');
            const selected = g?.querySelector('[aria-pressed="true"]');
            return selected?.textContent?.includes('month') &&
                Math.abs(Number.parseFloat(g.style.getPropertyValue('--pi-m3-track-x')) - selected.offsetLeft) < .2;
        },{timeout:5000});
        assert.deepEqual(await page.$eval('.wpb-progress-grouping', g =>
            [...g.children].map(b => b.offsetWidth)), historyGrouping.widths,
            'history grouping indicator does not alter tap target widths');
        // A real semantic range tablist (same class as Exercise > Charts)
        // uses the existing keyboard and moving selection controller.
        await page.evaluate(() => {
            const g = document.createElement('div');
            g.className = 'wpb-exercise-window-tabs';
            g.setAttribute('role','tablist');
            g.dataset.piRangeTest = '1';
            g.style.display = 'flex';
            for (const [i,label] of ['Month','Quarter','All'].entries()) {
                const b = document.createElement('button');
                b.setAttribute('role','tab');
                b.setAttribute('aria-selected',String(i===0));
                b.textContent=label;
                b.style.flex='1';
                b.addEventListener('click',()=>{
                    for (const child of g.children) child.setAttribute('aria-selected',String(child===b));
                });
                g.append(b);
            }
            document.querySelector('.wpb-progress').append(g);
        });
        await page.waitForSelector('[data-pi-range-test][data-pi-m3-track]');
        await page.focus('[data-pi-range-test]>button:first-child');
        await page.keyboard.press('End');
        await page.waitForFunction(() => {
            const g = document.querySelector('[data-pi-range-test]');
            const b = g?.querySelector('[aria-selected="true"]');
            return b?.textContent==='All' && document.activeElement===b &&
                Math.abs(parseFloat(g.style.getPropertyValue('--pi-m3-track-x'))-b.offsetLeft)<.2;
        },{timeout:5000});
        assert.ok(await page.$eval('[data-pi-range-test]', g =>
            g.childElementCount===3 && getComputedStyle(g,'::before').content!=='none'),
            'exercise chart range keeps real accessible tabs behind the animated indicator');
        await page.evaluate(() => document.querySelector('[data-pi-range-test]').remove());
        // Exercise-detail tabs must share the actual moving selection track.
        // Use the real page observer without mutating React-owned training data.
        await page.evaluate(() => {
            const group = document.createElement('div');
            group.className = 'wpb-exercise-tabs'; group.setAttribute('role','tablist');
            group.dataset.piExerciseTest = '1';
            for (const [i,name] of ['Overview','History','Notes'].entries()) {
                const b = document.createElement('button');
                b.textContent = name; b.setAttribute('role','tab');
                b.setAttribute('aria-selected',String(i===0));
                b.addEventListener('click',()=>{
                    for (const peer of group.children) peer.setAttribute('aria-selected',String(peer===b));
                });
                group.append(b);
            }
            document.querySelector('.wpb-progress').append(group);
        });
        await page.waitForSelector('[data-pi-exercise-test][data-pi-m3-track]');
        const exerciseTrack = await page.$eval('[data-pi-exercise-test]', el => ({
            fill: el.style.getPropertyValue('--pi-m3-track-fill'), count: el.childElementCount,
            paint: getComputedStyle(el,'::before').content
        }));
        assert.ok(exerciseTrack.fill && exerciseTrack.fill !== 'transparent' &&
            exerciseTrack.count === 3 && exerciseTrack.paint !== 'none',
            'exercise details preserve three real controls and a painted active track');
        await page.focus('[data-pi-exercise-test]>button:first-child');
        await page.keyboard.press('End');
        await page.waitForFunction(() => {
            const group = document.querySelector('[data-pi-exercise-test]');
            const chosen = group?.querySelector('[aria-selected="true"]');
            return chosen?.textContent === 'Notes' && document.activeElement === chosen &&
                Math.abs(parseFloat(group.style.getPropertyValue('--pi-m3-track-x')) - chosen.offsetLeft) < .2;
        },{timeout:5000});
        await page.evaluate(() => document.querySelector('[data-pi-exercise-test]').remove());
        await page.click('[data-tab="settings"]'); await page.waitForSelector('.wpb-settings');
        const settingsTrack = await page.$eval('.wpb-settings .wpb-segmented[data-pi-m3-track]', el => ({
            x: Number.parseFloat(el.style.getPropertyValue('--pi-m3-track-x')),
            width: Number.parseFloat(el.style.getPropertyValue('--pi-m3-track-width')),
            selected: el.querySelector('[aria-pressed="true"]')?.textContent?.trim(),
            fill: el.style.getPropertyValue('--pi-m3-track-fill'),
            count: el.childElementCount
        }));
        assert.ok(settingsTrack.width >= 20 && settingsTrack.fill.length > 3 &&
            settingsTrack.selected?.length > 0 && settingsTrack.count >= 2,
            'Settings uses the same M3 track with live unit and effort choices');
        if (width === 430)
            await page.screenshot({ path: path.join(root, 'verification/b831-m3-selected-settings-phone.png') });

        // Choice controls show a quiet touch-origin state layer, even for rapid repeated taps.
        // This must not add nodes or modify the actual workout logging grid.
        const ink = await page.$eval('[data-tab="settings"]', el => {
            const r = el.getBoundingClientRect(), children = el.childElementCount;
            const x = r.left + r.width * .25, y = r.top + r.height * .35;
            el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, button: 0, pointerId: 95, clientX: x, clientY: y }));
            const state = { ink: el.dataset.piInk, x: parseFloat(el.style.getPropertyValue('--pi-ink-x')),
                y: parseFloat(el.style.getPropertyValue('--pi-ink-y')),
                animation: getComputedStyle(el, '::after').animationName,
                rippleVisible: getComputedStyle(el, '::after').display !== 'none',
                childrenUnchanged: el.childElementCount === children };
            document.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 95 }));
            return state;
        });
        assert.equal(ink.ink, '1', 'the press state layer activates on root navigation');
        assert.ok(ink.x > 0 && ink.y > 0 && ink.childrenUnchanged, 'tap origin is tracked without DOM or layout additions');
        assert.ok(ink.animation.includes('piInkBurst') && ink.rippleVisible,
            'touch feedback is painted and transient even on the selected navigation tab');
        await page.waitForFunction(() => !document.querySelector('[data-tab="settings"]')?.hasAttribute('data-pi-ink'));
        // Losing window focus cancels a held button instead of leaving a scaled control.
        await page.$eval('[data-tab="settings"]', el => {
            const r = el.getBoundingClientRect();
            el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, button: 0, pointerId: 96,
                clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 }));
            window.dispatchEvent(new Event('blur'));
        });
        await page.waitForFunction(() => document.querySelector('[data-tab="settings"]')?.style.getPropertyValue('--pi-control-scale') === '1');
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        const reducedInk = await page.$eval('[data-tab="settings"]', el => {
            const r = el.getBoundingClientRect();
            el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, button: 0, pointerId: 97,
                clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 }));
            document.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 97 }));
            return el.hasAttribute('data-pi-ink');
        });
        assert.equal(reducedInk, false, 'reduced motion never starts the state-layer burst');
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }]);
        await page.focus('[data-tab="settings"]'); await page.keyboard.down(' ');
        await page.waitForFunction(() => Number.parseFloat(document.querySelector('[data-tab="settings"]').style.getPropertyValue('--pi-control-scale')) < .999);
        await page.keyboard.up(' ');
        await page.waitForFunction(() => document.querySelector('[data-tab="settings"]').style.getPropertyValue('--pi-control-scale') === '1');
        await clickText('Your gyms'); await page.waitForSelector('[data-sheet-drag][data-pi-surface]');
        await page.waitForFunction(() => document.querySelector('[data-sheet-drag]')?.style.getPropertyValue('--pi-surface-y') === '0px');
        // Shared M3 sheets retain the existing native edge-attachment, detent and
        // scroll ownership; the new handle is visual and has no extra DOM children.
        const sheetStyle = await page.$eval('.wpb-backdrop>[data-sheet-drag]', el => {
            const rect = el.getBoundingClientRect(), face = getComputedStyle(el);
            const handle = getComputedStyle(el, '::before');
            return { topRadius: parseFloat(face.borderTopLeftRadius),
                width: rect.width, right: rect.right, handleWidth: parseFloat(handle.width),
                handleHeight: parseFloat(handle.height), handleDisplay: handle.display,
                children: el.childElementCount, translate: el.style.getPropertyValue('--pi-surface-y') };
        });
        assert.ok(sheetStyle.topRadius >= 25 && sheetStyle.handleWidth >= 34 &&
            sheetStyle.handleHeight === 4 && sheetStyle.handleDisplay !== 'none',
            'M3 sheet paints the compact centered drag handle and generous top corners');
        assert.ok(sheetStyle.right <= width + 1 && sheetStyle.width <= width + 1 &&
            sheetStyle.translate === '0px', 'M3 sheet does not overflow or move its resting detent');
        // R20: measure the actual sheet, not an illustration of a modal.
        // A solid background is necessary because underlying Settings content
        // must not show through. All working gym selection rows remain present.
        const gymR20 = await page.$eval('.wpb-gym-sheet', sheet => {
            const done=sheet.querySelector('.wpb-gym-done');
            const card=sheet.querySelector('.wpb-gym-card');
            const c=getComputedStyle(sheet);
            const ctx=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
            ctx.fillStyle=c.backgroundColor;ctx.fillRect(0,0,1,1);
            return {alpha:ctx.getImageData(0,0,1,1).data[3],
                image:c.backgroundImage,doneHeight:done?.getBoundingClientRect().height ?? 0,
                doneWidth:done?.getBoundingClientRect().width ?? 0,
                cardRadius:card?parseFloat(getComputedStyle(card).borderTopLeftRadius):null,
                cardCount:sheet.querySelectorAll('.wpb-gym-card').length,
                overflow:sheet.scrollWidth>sheet.clientWidth+1};
        });
        assert.ok(gymR20.alpha===255 && gymR20.image==='none' &&
            gymR20.doneHeight>=44 && gymR20.doneWidth>=44 &&
            gymR20.cardCount>=1 && gymR20.cardRadius>=18 && !gymR20.overflow,
            'R20 solid gym sheet preserves selection cards and full-size Done: '+JSON.stringify(gymR20));
        if (width === 430)
            await page.screenshot({ path: path.join(root, 'verification/b831-m3-gym-sheet-phone.png') });

        // A partial drag belongs to the sheet and returns to rest; it cannot leave an offset behind.
        await page.$eval('[data-sheet-drag]', el => {
            const r = el.getBoundingClientRect();
            window.__b831Touch = { identifier: 1, target: el, clientX: r.left + 20, clientY: r.top + 20 };
            el.dispatchEvent(new TouchEvent('touchstart', { touches: [new Touch(window.__b831Touch)], bubbles: true }));
        });
        await page.$eval('[data-sheet-drag]', el => {
            el.dispatchEvent(new TouchEvent('touchmove', { touches: [new Touch({ ...window.__b831Touch, clientY: window.__b831Touch.clientY + 30 })], bubbles: true }));
            el.dispatchEvent(new TouchEvent('touchend', { touches: [], bubbles: true }));
        });
        await page.waitForFunction(() => document.querySelector('[data-sheet-drag]')?.style.getPropertyValue('--pi-surface-y') === '0px');
        await page.keyboard.press('Escape'); await page.waitForFunction(() => !document.querySelector('.wpb-backdrop'));
        assert.equal(await page.$$eval('[data-wpb-lock]', els => els.length), 0, 'sheet exit releases scrolling');
        await page.click('[data-tab="home"]'); await page.waitForSelector('.hp-open');
        // The saved-program menu is a real, anchored three-action surface.
        // Keyboard navigation and Escape must preserve the authored program
        // instead of navigating or calling the destructive Delete item.
        await page.click('button[aria-haspopup="menu"]');
        await page.waitForSelector('.wpb-context-menu[role="menu"]');
        // popIn starts slightly scaled, so boundingClientRect is temporarily
        // smaller than the real 44px target. Measure resting layout instead.
        await page.waitForFunction(() => {
            const menu = document.querySelector('.wpb-context-menu');
            return menu && menu.getAnimations({ subtree: false }).every(a =>
                a.playState === 'finished' || a.playState === 'idle');
        }, { timeout: 5000 });
        await page.waitForFunction(() => {
            const menu = document.querySelector('.wpb-context-menu');
            const dock = document.querySelector('.wpb-live-dock');
            if (!menu || !dock) return false;
            return menu.getBoundingClientRect().bottom <= dock.getBoundingClientRect().top - 3;
        }, { timeout: 5000 });
        const menuGeometry = await page.$eval('.wpb-context-menu', el => {
            const r = el.getBoundingClientRect();
            const item = el.querySelector('[role="menuitem"]');
            const dock = document.querySelector('.wpb-live-dock')?.getBoundingClientRect();
            const bar = document.querySelector('.wpb-tabbar')?.getBoundingClientRect();
            return { left: r.left, right: r.right, bottom: r.bottom,
                dockTop: dock?.top, navTop: bar?.top,
                offset: parseFloat(el.style.getPropertyValue('--pi-menu-shift-y')) || 0,
                radius: parseFloat(getComputedStyle(el).borderTopLeftRadius),
                minItem: item.getBoundingClientRect().height, layoutItem: item.offsetHeight,
                items: el.querySelectorAll('[role="menuitem"]').length };
        });
        assert.ok(menuGeometry.radius >= 16 && menuGeometry.layoutItem >= 44 &&
            menuGeometry.minItem >= 43.8 &&
            menuGeometry.left >= 0 && menuGeometry.right <= width + 1,
            'M3 contextual menus are rounded, scroll-safe and touch accessible: ' + JSON.stringify({ width, menuGeometry }));
        assert.ok(menuGeometry.items >= 2, 'contextual actions keep their original features');
        // Repeated resize signals in a single turn must trigger at most one
        // remeasurement of the open anchored menu; the menu still clears dock.
        const resizeR28=await page.evaluate(async()=>{
            const menu=document.querySelector('.wpb-context-menu');
            const original=menu.getBoundingClientRect, dock=document.querySelector('.wpb-live-dock');
            let reads=0;
            menu.getBoundingClientRect=function(...args){reads++;return original.apply(this,args);};
            try{
                for(let i=0;i<6;i++) window.dispatchEvent(new Event('resize'));
                await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
            }finally{menu.getBoundingClientRect=original;}
            const box=menu.getBoundingClientRect(),dockBox=dock.getBoundingClientRect();
            return {reads,clearsDock:box.bottom<=dockBox.top-3,overflow:box.right>innerWidth+1};
        });
        assert.ok(resizeR28.reads>=1 && resizeR28.reads<=3 &&
            resizeR28.clearsDock && !resizeR28.overflow,
            'R28 coalesces open-menu reposition reads on burst resize: '+JSON.stringify(resizeR28));

        // 2026 expressive menu group: rounded outside corners without a
        // wider popup, extra item height, lost keyboard order or new React state.
        const menuGroupShape = await page.$eval('.wpb-context-menu', menu => {
            const items = [...menu.querySelectorAll(':scope > .wpb-context-menu-item')];
            return { count: items.length,
                first: items[0] ? parseFloat(getComputedStyle(items[0]).borderTopLeftRadius) : 0,
                last: items.at(-1) ? parseFloat(getComputedStyle(items.at(-1)).borderBottomRightRadius) : 0 };
        });
        const menuSurfaceR21 = await page.$eval('.wpb-context-menu', menu => {
            const style=getComputedStyle(menu);
            const ctx=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
            ctx.fillStyle=style.backgroundColor;ctx.fillRect(0,0,1,1);
            return {alpha:ctx.getImageData(0,0,1,1).data[3],
                image:style.backgroundImage,
                width:menu.getBoundingClientRect().width,
                overflow:menu.scrollWidth>menu.clientWidth+1};
        });
        assert.ok(menuSurfaceR21.alpha===255 && menuSurfaceR21.image==='none' &&
            menuSurfaceR21.width<=width-16 && !menuSurfaceR21.overflow,
            'R21 context menu uses a solid, viewport-safe theme surface: '+JSON.stringify(menuSurfaceR21));
        assert.ok(menuGroupShape.count >= 2 && menuGroupShape.first >= 20 &&
            menuGroupShape.last >= 20, 'connected expressive menu outer shapes: ' + JSON.stringify(menuGroupShape));
        assert.ok(menuGeometry.bottom < menuGeometry.dockTop &&
            menuGeometry.bottom < menuGeometry.navTop,
            'M3 program actions remain above the active workout dock and tab bar: ' +
            JSON.stringify(menuGeometry));
        if (width === 430)
            await page.screenshot({ path: path.join(root, 'verification/b831-m3-program-menu-phone.png') });
        await page.focus('.wpb-context-menu [role="menuitem"]:first-child');
        await page.keyboard.press('ArrowDown');
        assert.ok(await page.$eval('.wpb-context-menu', el =>
            document.activeElement === el.querySelectorAll('[role="menuitem"]')[1]),
            'M3 ArrowDown advances focus to the next available contextual action');
        await page.keyboard.press('End');
        assert.ok(await page.$eval('.wpb-context-menu', el =>
            document.activeElement === [...el.querySelectorAll('[role="menuitem"]')].at(-1)),
            'M3 End moves to last menu action without triggering it');
        await page.keyboard.press('Escape');
        await page.waitForFunction(() => !document.querySelector('.wpb-context-menu'));
        assert.ok(await page.$eval('button[aria-haspopup="menu"]', el =>
            document.activeElement === el), 'Escape dismisses menu and restores its opener focus');
        assert.equal(await page.$eval('.hp-open', el => el.isConnected), true,
            'menu dismiss preserves the program card and its original action');
        await page.click('.hp-open');
        await page.waitForSelector('.wpb-program');
        // Info sections preserve content while closing and survive an immediate reopen.
        const card = await page.$('[data-infocard][data-collapsible="1"]');
        if (card) {
            await card.$eval('button[aria-expanded]', b => b.click());
            await page.waitForSelector('[data-pi-reveal]');
            await card.$eval('button[aria-expanded]', b => b.click());
            await card.$eval('button[aria-expanded]', b => b.click());
            await page.waitForFunction(() => [...document.querySelectorAll('[data-pi-reveal]')].some(el => el.style.height === 'auto'));
        }
        assert.ok(await page.$eval('.wpb-program .wpb-day-card', el =>
            parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 15), 'program day cards share the expressive radius');
        assert.ok(await page.$eval('.wpb-program .wpb-day-title', el => {
            const style = getComputedStyle(el);
            return parseFloat(style.fontSize) >= 15 && parseFloat(style.lineHeight) >= 18;
        }), 'Plan day titles are legible without expanding or rearranging the day cards');
        // A real plan-day accordion must morph its surface and expose the
        // actual mounted panel to assistive technology. No extra rows,
        // reserved height or program-data mutation is permitted.
        // Plan opens the next training day by default. Normalize to a
        // collapsed card so we exercise a genuine closed -> open transition.
        const initiallyOpen = await page.$eval('.wpb-program .wpb-day-card', card =>
            !!card.querySelector(':scope > .wpb-expand'));
        if (initiallyOpen) {
            await page.click('.wpb-program .wpb-day-toggle');
            await page.waitForFunction(() =>
                !document.querySelector('.wpb-program .wpb-day-card > .wpb-expand'));
        }
        await page.click('.wpb-program .wpb-day-toggle');
        await page.waitForFunction(() => {
            const card=document.querySelector('.wpb-program .wpb-day-card');
            const panel=card?.querySelector(':scope > .wpb-expand');
            const toggle=card?.querySelector('.wpb-day-toggle');
            return card?.dataset.piDisclosure==='open' && panel &&
                toggle?.getAttribute('aria-expanded')==='true' &&
                toggle?.getAttribute('aria-controls')===panel.id &&
                parseFloat(getComputedStyle(card).borderTopLeftRadius)>=20;
        }, { timeout: 5000 });
        // React mounts the disclosure immediately but its measured content
        // height settles after the shape/height spring. Check the *settled*
        // 44px target instead of racing the first animation frame (the prior
        // post-merge 430px failure). Do not relax the actual hit-size contract.
        await page.waitForFunction(() => {
            const card=document.querySelector('.wpb-program .wpb-day-card');
            const button=card?.querySelector('.wpb-day-toggle');
            const panel=card?.querySelector(':scope > .wpb-expand');
            return button?.getBoundingClientRect().height >= 44 &&
                panel?.getBoundingClientRect().height > 40 &&
                card.children.length >= 2;
        }, { timeout: 5000 });
        // R22: the actual More options button in the expanded day needs a
        // 44px hit box, and the sheet must not clip its title or action hints.
        await page.waitForSelector('.wpb-program button[aria-label^="More options for "]');
        const programBeforeOptions=await page.evaluate(() =>
            JSON.stringify(JSON.parse(localStorage.getItem('wpb:v1')||'{}').saved));
        const triggerR22=await page.$eval('.wpb-program button[aria-label^="More options for "]',
            button=>({height:button.getBoundingClientRect().height,
                width:button.getBoundingClientRect().width}));
        assert.ok(triggerR22.height>=44 && triggerR22.width>=44,
            'R22 program exercise options opener is accessible: '+JSON.stringify(triggerR22));
        // Match an actual exercise, not the day-level More options control.
        const actionableR22='.wpb-program button[aria-label="More options for Back Squat"]';
        await page.waitForSelector(actionableR22);
        await page.click(actionableR22);
        await page.waitForSelector('.wpb-exercise-options-sheet .wpb-sheet-action');
        const optionsR22=await page.$eval('.wpb-exercise-options-sheet', sheet=>{
            const title=sheet.querySelector(':scope > div:first-child > div:first-child > div:first-child');
            const close=sheet.querySelector('.wpb-sheet-close');
            const actions=[...sheet.querySelectorAll(':scope > .wpb-sheet-action')];
            const firstDescription=actions[0]?.querySelector(':scope > span:last-child > span:last-child');
            const c=getComputedStyle(sheet);
            const ctx=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
            ctx.fillStyle=c.backgroundColor;ctx.fillRect(0,0,1,1);
            return {alpha:ctx.getImageData(0,0,1,1).data[3],
                image:c.backgroundImage,
                titleWrap:title?getComputedStyle(title).whiteSpace:null,
                titleOverflow:title?getComputedStyle(title).overflow:null,
                closeHeight:close?.getBoundingClientRect().height||0,
                actions:actions.length,
                minActionHeight:Math.min(...actions.map(a=>a.getBoundingClientRect().height)),
                descriptionWrap:firstDescription?getComputedStyle(firstDescription).whiteSpace:null,
                overflow:sheet.scrollWidth>sheet.clientWidth+1};
        });
        assert.ok(optionsR22.alpha===255 && optionsR22.image==='none' &&
            optionsR22.titleWrap==='normal' && optionsR22.titleOverflow!=='hidden' &&
            optionsR22.closeHeight>=44 && optionsR22.actions>=4 &&
            optionsR22.minActionHeight>=54 && optionsR22.descriptionWrap==='normal' &&
            !optionsR22.overflow,
            'R22 exercise options maintain readable names/descriptions and touch targets: '+JSON.stringify(optionsR22));
        if(width===430) await page.screenshot({path:path.join(root,'verification/b831-m3-exercise-options-phone.png')});
        await page.click('.wpb-exercise-options-sheet .wpb-sheet-close');
        await page.waitForSelector('.wpb-exercise-options-sheet',{hidden:true});
        assert.equal(await page.evaluate(() =>
            JSON.stringify(JSON.parse(localStorage.getItem('wpb:v1')||'{}').saved)),programBeforeOptions,
            'R22 opening and dismissing exercise options preserves the entire program');
        const disclosureOpen=await page.$eval('.wpb-program .wpb-day-card', el => ({
            touch:el.querySelector('.wpb-day-toggle').getBoundingClientRect().height,
            panel:el.querySelector(':scope > .wpb-expand')?.getBoundingClientRect().height,
            radius:parseFloat(getComputedStyle(el).borderTopLeftRadius),
            children:el.children.length
        }));
        assert.ok(disclosureOpen.touch>=44 && disclosureOpen.panel>40 &&
            disclosureOpen.children>=2,'M3 day disclosure is accessible without smaller hit targets');
        await page.click('.wpb-program .wpb-day-toggle');
        await page.waitForFunction(() => {
            const card=document.querySelector('.wpb-program .wpb-day-card');
            return card?.dataset.piDisclosure==='closed' &&
                !card.querySelector(':scope > .wpb-expand') &&
                card.querySelector('.wpb-day-toggle')?.getAttribute('aria-expanded')==='false' &&
                parseFloat(getComputedStyle(card).borderTopLeftRadius)<=16.5;
        }, { timeout: 5000 });
        assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).saved), initial.saved,
            'disclosure motion preserves the complete saved prescription');
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]'); await page.waitForSelector('.wpb-workout');
        await page.waitForFunction(() => !document.body.innerText.includes('Resumed your in-progress workout'));
        // R23: actual on-screen tool controls stay touch-safe; do not touch
        // the compact weight/reps grid, navigation ownership or log state.
        const liveToolsR23=await page.$$eval('.wpb-workout-tools .wpb-workout-tool', controls =>
            controls.filter(el => el.getClientRects().length).map(el => {
                const s=getComputedStyle(el),r=el.getBoundingClientRect();
                return {w:r.width,h:r.height,image:s.backgroundImage,touch:s.touchAction};
            }));
        assert.ok(liveToolsR23.length>0 && liveToolsR23.every(x=>
            x.w>=44 && x.h>=44 && x.image==='none' && x.touch==='manipulation'),
            'R23 live tool controls use stable 44px opaque tonal targets: '+JSON.stringify(liveToolsR23));
        const row = '[data-testid="set-0-0"]';
        await page.click(`${row} input[aria-label="weight"]`); await page.keyboard.type('195.5');
        const before = await page.$$eval(`${row} input`, els => els.map(el => el.value));
        for (let tap = 0; tap < 8; tap++) await page.click(tap % 2 ? 'button[aria-label="Show prescribed targets"]' : 'button[aria-label="Show previous workout values"]');
        assert.deepEqual(await page.$$eval(`${row} input`, els => els.map(el => el.value)), before, 'reference transitions never overwrite typed values');
        assert.ok(await page.$eval('.wpb-target-toggle', button => !button.hasAttribute('data-pi-control')),
            'Target/Last control retains its own compact feedback and never takes global shape morph');

        await page.waitForFunction(() => [...document.querySelectorAll('[data-pi-reference][data-pi-surface]')].every(el => el.style.getPropertyValue('--pi-surface-y') === '0px'));
        await page.click(`${row} button[aria-label="Mark set done"]`);
        await page.waitForSelector(`${row} [data-effort-picker]`);
        assert.ok(await page.$eval(`${row} .wpb-set-complete`, button => !button.hasAttribute('data-pi-control')),
            'completing a set never installs expressive press scaling on the protected row');
        await page.waitForFunction(row => {
            const button = document.querySelector(row + ' button[aria-label="3 reps left"]');
            if (!button) return false;
            const r = button.getBoundingClientRect(), hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
            return hit === button || button.contains(hit);
        }, {}, row);
        // Rest can be collapsed or auto-dismissed based on the active session.
        // When rendered, its action buttons and surface must remain readable.
        const restR23=await page.evaluate(() => {
            const card=document.querySelector('.wpb-workout .wpb-rest-card');
            if(!card || !card.getClientRects().length) return null;
            const s=getComputedStyle(card);
            const controls=[...card.querySelectorAll('.wpb-rest-actions>button')]
                .filter(el=>el.getClientRects().length)
                .map(el=>({w:el.getBoundingClientRect().width,h:el.getBoundingClientRect().height}));
            const ctx=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
            ctx.fillStyle=s.backgroundColor;ctx.fillRect(0,0,1,1);
            return {alpha:ctx.getImageData(0,0,1,1).data[3],
                image:s.backgroundImage,controls,
                overflow:card.scrollWidth>card.clientWidth+1};
        });
        if(restR23) assert.ok(restR23.alpha===255 && restR23.image==='none' &&
            !restR23.overflow && restR23.controls.every(x=>x.w>=44 && x.h>=44),
            'R23 rest controls fit within a solid accessible container: '+JSON.stringify(restR23));
        const groupWidth = await page.$eval(`${row} .wpb-effort-scale`, el => el.offsetWidth);
        await page.click(`${row} button[aria-label="3 reps left"]`);
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].actualRIR === 3);
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].actualRIR), 3, 'effort commits immediately');
        assert.equal(await page.$eval(`${row} .wpb-effort-scale`, el => el.offsetWidth), groupWidth, 'RIR feedback occupies the same space');
        await page.waitForFunction(row => !document.querySelector(row + ' [data-effort-picker]'), {}, row);
        assert.equal(await page.$eval(`${row} [data-effort-tag]`, el => el.textContent), '3 RIR');
        for (const n of [2,1,2,1]) await page.click(`.wpb-ex-nav-step:nth-child(${n})`);
        await page.waitForSelector(row);
        await page.waitForFunction(() => document.querySelector('[data-pi-workout-page]')?.style.getPropertyValue('--pi-surface-x') === '0px');
        assert.equal(await page.$eval(`${row} input[aria-label="weight"]`, el => el.value), '195.5');
        assert.equal(await page.$eval(`${row} [data-effort-tag]`, el => el.textContent), '3 RIR');
        assert.ok(await page.$eval('.wpb-workout-footer', el => el.getBoundingClientRect().bottom <= innerHeight + 1), 'footer remains available during rest');
        // Runtime preference changes finish existing motion and keep every control functional.
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        await page.click(`${row} [data-effort-tag]`); await page.click(`${row} button[aria-label="2 reps left"]`);
        await page.waitForFunction(row => !document.querySelector(row + ' [data-effort-picker]'), {}, row);
        assert.equal(await page.$eval(`${row} [data-effort-tag]`, el => el.textContent), '2 RIR');
        await page.click(`${row} button[aria-label="Mark set not done"]`);
        assert.equal(await page.$eval(`${row} input[aria-label="weight"]`, el => el.value), '195.5', 'Undo preserves the logged load');
        assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).saved), initial.saved, 'motion cannot change program prescriptions');
        assert.deepEqual(errors, []);
        await page.screenshot({ path: path.join(root, `verification/b831-motion-${width}-phone.png`) });
        // Continue into real program-authoring and exercise-library screens,
        // verifying the new compact M3 hierarchy with the saved program intact.
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }]);
        // Isolate authoring/library QA from the live-workout fixture. A separate
        // fresh page starts with only the same persisted programs (not the
        // deliberately unfinished training session tested above).
        await page.close();
        page = await context.newPage();
        page.on('pageerror', error => errors.push(error.message));
        await page.setViewport({ width, height: 844, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
        await page.evaluateOnNewDocument(store => {
            // Seed this independent secondary browser page exactly once.
            // A subsequent reload must retain its newly added 80-session chart
            // fixture, not silently overwrite it with the original one-session
            // state. The previous unconditional clear caused the dense-chart
            // test to fail before any scrub interaction was exercised.
            if (localStorage.getItem('b831-secondary-seeded')) return;
            localStorage.clear();
            localStorage.setItem('b831-secondary-seeded', '1');
            localStorage.setItem('b831-seeded', '1');
            localStorage.setItem('wpb:v1', JSON.stringify(store));
        }, initial);
        await page.goto('http://127.0.0.1:8794/', { waitUntil: 'networkidle0' });
        await page.waitForSelector('#root[data-pi-motion="expressive"] .wpb-home-create');
        // The separate no-live Home fixture displays both screenshot CTAs.
        // Require differentiated emphasis, text contrast and unchanged targets.
        const homeWithoutDock = await homePalette();
        assert.ok(homeWithoutDock.primary && homeWithoutDock.secondary &&
            homeWithoutDock.primary.height >= 44 &&
            homeWithoutDock.secondary.height >= 44,
            'R10 Home actions exist and remain tappable without live workout: '+JSON.stringify(homeWithoutDock));
        if (homeWithoutDock.scheme.includes('dark')) {
            assert.ok(homeWithoutDock.primary.contrast >= 4.5 &&
                homeWithoutDock.secondary.contrast >= 4.5 &&
                homeWithoutDock.primary.background < .30 &&
                homeWithoutDock.secondary.background < .12 &&
                homeWithoutDock.primary.background > homeWithoutDock.secondary.background * 1.3 &&
                homeWithoutDock.primary.backgroundImage === 'none' &&
                homeWithoutDock.secondary.backgroundImage === 'none',
                'R10 dark Home has one tonal primary and quiet secondary: '+JSON.stringify(homeWithoutDock));
        }
        // R13: the real Start Workout CTA must retain a centered, monochrome
        // glyph instead of an outlined dark triangle on the muted accent.
        const startIcon = await page.$eval('.wpb-home-hero .wpb-primary-action', button => {
            const icon = button.querySelector('svg');
            if (!icon) return null;
            const glyph = icon.querySelector('polygon');
            const ic = getComputedStyle(icon);
            const ib = icon.getBoundingClientRect();
            const bb = button.getBoundingClientRect();
            return {
                width: ib.width, height: ib.height, stroke: ic.stroke, color: ic.color,
                polygonFill: glyph ? getComputedStyle(glyph).fill : null,
                verticalOffset: Math.abs((ib.top + ib.height / 2) - (bb.top + bb.height / 2)),
                buttonHeight: bb.height
            };
        });
        assert.ok(startIcon && startIcon.width >= 18 && startIcon.height >= 18 &&
            startIcon.verticalOffset <= 7 && startIcon.stroke === startIcon.color &&
            startIcon.buttonHeight >= 44 &&
            (!startIcon.polygonFill || startIcon.polygonFill === startIcon.color),
            'R13 Start Workout has a centered, on-color play glyph: ' + JSON.stringify(startIcon));
        // R15: background-color can be rgba(...,.03) and still pass a
        // "not transparent" check. Measure the rendered alpha channel for
        // both surfaces; reject anything less than fully opaque on phones.
        const dockPaint = await page.$eval('.wpb-home-compose-bar', bar => {
            const c = getComputedStyle(bar);
            const quickEl = bar.querySelector('.wpb-home-quick');
            const primary = bar.querySelector('.wpb-home-create')?.getBoundingClientRect();
            const quick = quickEl?.getBoundingClientRect();
            const alpha = el => {
                const canvas = document.createElement('canvas');
                canvas.width = canvas.height = 1;
                const ctx = canvas.getContext('2d', {willReadFrequently:true});
                ctx.clearRect(0,0,1,1);
                ctx.fillStyle = getComputedStyle(el).backgroundColor;
                ctx.fillRect(0,0,1,1);
                return ctx.getImageData(0,0,1,1).data[3];
            };
            return {
                image: c.backgroundImage, background: c.backgroundColor,
                dockAlpha: alpha(bar), quickAlpha: quickEl ? alpha(quickEl) : 0,
                quickImage: quickEl ? getComputedStyle(quickEl).backgroundImage : null,
                pointerEvents: c.pointerEvents, opacity: c.opacity,
                primaryHeight: primary?.height ?? 0, quickHeight: quick?.height ?? 0,
                primaryWidth: primary?.width ?? 0, quickWidth: quick?.width ?? 0,
                separated: !!(primary && quick && quick.left >= primary.right-1)
            };
        });
        assert.ok(dockPaint.image === 'none' && dockPaint.quickImage === 'none' &&
            dockPaint.dockAlpha === 255 && dockPaint.quickAlpha === 255 &&
            dockPaint.pointerEvents === 'auto' && dockPaint.opacity === '1' &&
            dockPaint.primaryHeight >= 44 && dockPaint.quickHeight >= 44 &&
            dockPaint.primaryWidth >= dockPaint.quickWidth && dockPaint.separated,
            'R15 home dock and Quick are truly opaque and intercept underlying taps: ' + JSON.stringify(dockPaint));
        await page.click('.wpb-home-create');
        await page.waitForSelector('.wpb-wizard .wpb-wizard-progress');
        const optionShapes = await page.$$eval('.wpb-wizard .wpb-wizard-option, .wpb-wizard .wpb-wizard-step>div>button[aria-pressed]', controls =>
            controls.map(el => ({radius:parseFloat(getComputedStyle(el).borderTopLeftRadius),
                height:el.getBoundingClientRect().height})));
        assert.ok(optionShapes.length>=2 && optionShapes.every(el=>el.radius>=18&&el.height>=58),
            'M3 wizard options use large asymmetric shapes without smaller touch targets: '+JSON.stringify(optionShapes));
        const wizardGeometry = await page.$eval('.wpb-wizard', el => {
            const bar = el.querySelector('.wpb-wizard-progress').getBoundingClientRect();
            const footer = el.querySelector('.wpb-wizard-footer').getBoundingClientRect();
            const screen = el.getBoundingClientRect();
            return { barHeight: bar.height, footer: footer.bottom, screen: screen.bottom,
                width: el.scrollWidth, viewport: innerWidth };
        });
        assert.ok(wizardGeometry.barHeight >= 4 && wizardGeometry.width <= width + 1 &&
            wizardGeometry.footer <= wizardGeometry.screen + 2, 'M3 program wizard is compact, visible and never horizontally clipped: ' + JSON.stringify(wizardGeometry));
        // R19: guided creation uses solid footer chrome and consistent,
        // accessible hit sizes instead of a translucent bottom veil.
        const wizardR19 = await page.$eval('.wpb-wizard', shell => {
            const footer=shell.querySelector('.wpb-wizard-footer');
            const back=shell.querySelector('.wpb-wizard-header button[aria-label="Back"]');
            const actions=[...shell.querySelectorAll('.wpb-wizard-back-action,.wpb-wizard-next-action')];
            const option=shell.querySelector('.wpb-wizard-option, .wpb-wizard-step>div>button[aria-pressed]');
            const bg=getComputedStyle(footer).backgroundColor;
            const ctx=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
            ctx.fillStyle=bg;ctx.fillRect(0,0,1,1);
            return {footerAlpha:ctx.getImageData(0,0,1,1).data[3],
                footerImage:getComputedStyle(footer).backgroundImage,
                backHeight:back.getBoundingClientRect().height,
                actionHeights:actions.map(x=>x.getBoundingClientRect().height),
                optionHeight:option?.getBoundingClientRect().height ?? 0,
                optionTouch:option ? getComputedStyle(option).touchAction : '',
                footerActionTouches:actions.map(el=>getComputedStyle(el).touchAction),
                overflow:shell.scrollWidth>shell.clientWidth+1};
        });
        assert.ok(wizardR19.footerAlpha===255 && wizardR19.footerImage==='none' &&
            wizardR19.backHeight>=44 &&
            wizardR19.actionHeights.length===2 &&
            wizardR19.actionHeights.every(x=>x>=48) &&
            wizardR19.optionHeight>=58 && !wizardR19.overflow &&
            wizardR19.optionTouch==='manipulation' &&
            wizardR19.footerActionTouches.every(v=>v==='manipulation'),
            'R19 creation footer is solid and actions/options remain tap safe: '+JSON.stringify(wizardR19));
        const wizardR5 = await page.$eval('.wpb-wizard', el => ({
            title:parseFloat(getComputedStyle(el.querySelector('.wpb-wizard-heading-title')).fontSize),
            progressHeight:el.querySelector('.wpb-wizard-progress').getBoundingClientRect().height,
            clipped:el.scrollWidth>innerWidth+1
        }));
        assert.ok(wizardR5.title>=28 && wizardR5.progressHeight>=6 && !wizardR5.clipped,
            'R5 wizard retains a large guided headline and slim responsive progress track: '+JSON.stringify(wizardR5));
        if (width === 430) await page.screenshot({ path: path.join(root, 'verification/b831-m3-wizard-phone.png') });
        await page.click('.wpb-wizard-header button[aria-label="Back"]');
        await page.waitForSelector('.wpb-home-create');
        await page.click('[data-tab="settings"]');
        await page.waitForSelector('.wpb-settings');
        await clickText('Exercise library');
        await page.waitForSelector('.wpb-library-flag-filter[data-pi-m3-track]');
        const libraryStart = await page.$eval('.wpb-library-flag-filter', el => ({
            x: parseFloat(el.style.getPropertyValue('--pi-m3-track-x')),
            fill: el.style.getPropertyValue('--pi-m3-track-fill'),
            children: el.childElementCount,
            w: el.getBoundingClientRect().width
        }));
        assert.equal(libraryStart.children, 4, 'library retains all four working filter modes');
        assert.ok(libraryStart.fill && libraryStart.fill !== 'transparent',
            'library moving selection uses the chosen theme');
        await page.click('.wpb-library-flag:nth-child(2)');
        await page.waitForFunction(() => {
            const el = document.querySelector('.wpb-library-flag-filter');
            const chosen = el?.querySelector('[aria-pressed="true"]');
            return chosen?.textContent?.includes('Recent') &&
                Math.abs(parseFloat(el.style.getPropertyValue('--pi-m3-track-x')) - chosen.offsetLeft) < .3 &&
                Math.abs(parseFloat(el.style.getPropertyValue('--pi-m3-track-width')) - chosen.offsetWidth) < .3 &&
                parseFloat(chosen.style.getPropertyValue('--pi-choice-grow')) > 1.18;
        }, { timeout: 5000 });
        await page.$eval('.wpb-library-search input', input => input.focus());
        const libraryFocus = await page.$eval('.wpb-library-search input', el =>
            parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 12 &&
            getComputedStyle(el).outlineStyle === 'solid');
        assert.ok(libraryFocus, 'library search keeps a visible, comfortably rounded keyboard focus');
        assert.ok(await page.$eval('.wpb-library', el => el.scrollWidth <= innerWidth + 1),
            'library filters and list do not overflow phone width');
        assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).saved), initial.saved,
            'secondary screen M3 styling cannot mutate programs');
        if (width === 430) await page.screenshot({ path: path.join(root, 'verification/b831-m3-library-phone.png') });
        // Navigate an actual exercise from Library into its full-screen detail.
        // This extends verification beyond synthetic tab fixtures; it also
        // captures the real history and charts screens for review.
        await page.click('.wpb-library-flag:first-child');
        // An earlier keyboard-focus contract leaves this search focused.
        // Explicitly leave focus, then observe the actual resting shape.
        await page.$eval('.wpb-library-search input', el => el.blur());
        await page.waitForFunction(() => {
            const el=document.querySelector('.wpb-library-search');
            return el && parseFloat(getComputedStyle(el).borderTopLeftRadius)>=18;
        }, { timeout: 5000 });
        const librarySearchRestRadius = await page.$eval('.wpb-library-search', el =>
            parseFloat(getComputedStyle(el).borderTopLeftRadius));
        assert.ok(librarySearchRestRadius >= 18,
            'R19 Library resting search is softly rounded without oversized chrome');
        await page.click('.wpb-library-search input');
        await page.keyboard.type('Back Squat');
        await page.waitForSelector('.wpb-library-row');
        const libraryR5 = await page.$eval('.wpb-library', el => {
            const row=el.querySelector('.wpb-library-row');
            const search=el.querySelector('.wpb-library-search');
            return { radius:row ? parseFloat(getComputedStyle(row).borderTopLeftRadius) : 0,
                height:row?.getBoundingClientRect().height ?? 0,
                searchRadius:search ? parseFloat(getComputedStyle(search).borderTopLeftRadius) : 0,
                overflow:el.scrollWidth>el.clientWidth+1 };
        });
        assert.ok(libraryR5.radius>=18 && libraryR5.height>=59 &&
            libraryR5.searchRadius>=15 && libraryR5.searchRadius<librarySearchRestRadius && !libraryR5.overflow,
            'R5 Library uses sculpted responsive rows and prominent search: '+JSON.stringify(libraryR5));
        // The one filtered match uses a content-sized 2026 expressive list
        // surface, not a gradient or a rigid one-line clipped control.
        const rowShape = await page.$eval('.wpb-library-row', row => ({
            radius: parseFloat(getComputedStyle(row).borderTopLeftRadius),
            image: getComputedStyle(row).backgroundImage,
            height: row.getBoundingClientRect().height
        }));
        assert.ok(rowShape.radius >= 18 && rowShape.image === 'none' &&
            rowShape.height >= 59, 'M3E list remains flat, responsive and tappable: ' + JSON.stringify(rowShape));
        const libraryR19 = await page.$eval('.wpb-library', shell => {
            const search=shell.querySelector('.wpb-library-search input');
            const row=shell.querySelector('.wpb-library-row');
            const title=row?.querySelector(':scope > div:nth-child(2) > div:first-child');
            const statusButtons=[...shell.querySelectorAll('.wpb-library-flag')];
            const headerBack=shell.querySelector('.wpb-library-header button[aria-label="Back"]');
            return {searchHeight:search?.getBoundingClientRect().height,
                inputBackground:getComputedStyle(search).backgroundColor,
                inputBorder:getComputedStyle(search).borderTopWidth,
                titleWrap:title ? getComputedStyle(title).whiteSpace : null,
                titleOverflow:title ? getComputedStyle(title).overflow : null,
                rowHeight:row?.getBoundingClientRect().height,
                flagHeights:statusButtons.map(x=>x.getBoundingClientRect().height),
                backHeight:headerBack?.getBoundingClientRect().height ?? 0,
                overflow:shell.scrollWidth>shell.clientWidth+1};
        });
        assert.ok(libraryR19.searchHeight>=44 &&
            libraryR19.inputBorder==='0px' &&
            libraryR19.titleWrap==='normal' && libraryR19.titleOverflow!=='hidden' &&
            libraryR19.rowHeight>=60 &&
            libraryR19.flagHeights.length===4 &&
            libraryR19.flagHeights.every(x=>x>=44) &&
            libraryR19.backHeight>=44 && !libraryR19.overflow,
            'R19 Library search, wrapped titles and four filters stay accessible: '+JSON.stringify(libraryR19));
        const libraryTone = await tonalSurfaceReport({
            header:'.wpb-library-header',
            search:'.wpb-library-search',
            results:'.wpb-library-results>.wpb-library-row',
            filters:'.wpb-library-flag-filter'
        });
        const {header,search,results,filters}=libraryTone.output;
        assert.ok(header && search && results && filters,
            'R11 Library retains all actual search/filter/list controls: '+JSON.stringify(libraryTone));
        if (libraryTone.dark) assert.ok(search.bg !== results.bg &&
            header.image === 'none' && search.image === 'none' &&
            results.image === 'none' && filters.image === 'none' &&
            results.height >= 59 && search.height >= 44,
            'R11 Library search stays elevated over quiet flat results: '+JSON.stringify(libraryTone));
        await page.click('.wpb-library-row');
        await page.waitForSelector('[data-exercisedetail] .wpb-exercise-tabs[data-pi-m3-track]');
        // R12: the actual Library row supplied the bounded direction for the
        // detail's first title/art entrance. The opaque backdrop and 44px Back
        // control remain untouched throughout the transition.
        const linkedDetail = await page.$eval('.wpb-exercise-detail', el => {
            const title=el.querySelector('.wpb-exercise-detail-header>div:last-child');
            const back=el.querySelector('.wpb-exercise-detail-header button[aria-label="Back to library"]');
            const figure=el.querySelector('.wpb-exercise-figure-wrap');
            const bound=key=>parseFloat(el.style.getPropertyValue(key));
            return { connected:el.dataset.piDetailLinked,
                x:bound('--pi-detail-arrive-x'),y:bound('--pi-detail-arrive-y'),
                titleAnimation:title ? getComputedStyle(title).animationName : null,
                figureAnimation:figure ? getComputedStyle(figure).animationName : null,
                opacity:parseFloat(getComputedStyle(el).opacity),
                backWidth:back?.getBoundingClientRect().width };
        });
        assert.ok(linkedDetail.connected === '1' &&
            Number.isFinite(linkedDetail.x) && Number.isFinite(linkedDetail.y) &&
            Math.abs(linkedDetail.x)<=22.01 && Math.abs(linkedDetail.y)<=15.01 &&
            linkedDetail.titleAnimation === 'piR12DetailTitleArrival' &&
            linkedDetail.opacity>=.999 && linkedDetail.backWidth>=44,
            'R12 uses true row-origin motion without ghosting detail or shrinking Back: '+JSON.stringify(linkedDetail));
        if (linkedDetail.figureAnimation)
            assert.equal(linkedDetail.figureAnimation,'piR12DetailFigureArrival',
                'art follows same detail entrance rather than competing animation');
        await page.emulateMediaFeatures([{ name:'prefers-reduced-motion',value:'reduce' }]);
        assert.ok(await page.$eval('.wpb-exercise-detail', el => {
            const title=el.querySelector('.wpb-exercise-detail-header>div:last-child');
            return title && getComputedStyle(title).animationName === 'none' &&
                getComputedStyle(el).opacity >= .999;
        }), 'R12 respects a mid-detail reduced-motion preference with a fully opaque page');
        await page.emulateMediaFeatures([{ name:'prefers-reduced-motion',value:'no-preference' }]);
        const overlayPaint = await page.$eval('.wpb-exercise-detail', el=>({
            opacity:Number.parseFloat(getComputedStyle(el).opacity),
            background:getComputedStyle(el).backgroundColor,
            rect:el.getBoundingClientRect().toJSON(),
            closed:getComputedStyle(el).visibility==='hidden'
        }));
        assert.ok(overlayPaint.opacity>=0.999 && !overlayPaint.closed &&
            overlayPaint.rect.width>=width-3,
            'full-screen exercise detail never ghosts the underlying Library during motion: '+
              JSON.stringify(overlayPaint));
        const detailGeometry = await page.$eval('.wpb-exercise-detail', el => {
            const h = el.querySelector('.wpb-exercise-detail-header button[aria-label="Back to library"]');
            const tabs = [...el.querySelectorAll('.wpb-exercise-tabs>button')];
            const body = el.querySelector('[data-exercisedetail]');
            return { back: h?.getBoundingClientRect().width,
                count: tabs.length, minTabHeight: Math.min(...tabs.map(b=>b.getBoundingClientRect().height)),
                overflow: body?.scrollWidth > body?.clientWidth + 1,
                selection: tabs.filter(b=>b.getAttribute('aria-selected')==='true').length };
        });
        assert.ok(detailGeometry.back>=44 && detailGeometry.count===4 &&
            detailGeometry.minTabHeight>=40 && !detailGeometry.overflow &&
            detailGeometry.selection===1, 'exercise details keep compact touch-safe tabs: '+JSON.stringify(detailGeometry));
        const detailR20 = await page.$eval('.wpb-exercise-detail', sheet => {
            const header=sheet.querySelector('.wpb-exercise-detail-header');
            const title=header?.querySelector(':scope > div:last-child');
            const figure=sheet.querySelector('.wpb-exercise-figure-wrap');
            const tabs=[...sheet.querySelectorAll('.wpb-exercise-tabs>button')];
            const headerStyle=title ? getComputedStyle(title) : null;
            return {wrap:headerStyle?.whiteSpace,overflow:headerStyle?.overflow,
                textOverflow:headerStyle?.textOverflow,
                headerHeight:header?.getBoundingClientRect().height ?? 0,
                figureRadius:figure?parseFloat(getComputedStyle(figure).borderTopLeftRadius):0,
                tabs:tabs.length,tabHeights:tabs.map(x=>x.getBoundingClientRect().height),
                width:sheet.getBoundingClientRect().width};
        });
        assert.ok(detailR20.wrap==='normal' && detailR20.overflow!=='hidden' &&
            detailR20.textOverflow!=='ellipsis' && detailR20.headerHeight>=64 &&
            detailR20.figureRadius>=22 && detailR20.tabs===4 &&
            detailR20.tabHeights.every(h=>h>=44) && detailR20.width<=width+2,
            'R20 Exercise Detail title, illustration and real tabs are readable: '+JSON.stringify(detailR20));
        const formLink=await page.$eval('.wpb-exercise-detail a[data-testid="form-video-link"]', link=>{
            const box=link.getBoundingClientRect(),css=getComputedStyle(link);
            const details=link.querySelector(':scope > div:last-child');
            return {height:box.height,width:box.width,direction:css.flexDirection,
                href:link.getAttribute('href'),align:getComputedStyle(details).textAlign};
        });
        assert.ok(formLink.height>=70&&formLink.height<=96&&
            formLink.width<=width&&formLink.direction==='row'&&
            formLink.align==='left'&&formLink.href?.startsWith('https://'),
            'video guidance becomes a compact real link, not a large chart-blocking hero: '+JSON.stringify(formLink));
        if (width===430) await page.screenshot({ path: path.join(root, 'verification/b831-m3-exercise-history-phone.png') });
        await page.click('.wpb-exercise-tabs>button:nth-child(2)');
        await page.waitForSelector('[data-exercisecharts]');
        assert.equal(await page.$eval('.wpb-exercise-tabs>button:nth-child(2)',
            b=>b.getAttribute('aria-selected')), 'true', 'exercise charts tab selects the real panel');
        // The actual range choices (when rendered) remain real, touch-sized
        // tabs. Do not inject or replace the chart/range React controls.
        const rangesR20=await page.$$eval('.wpb-exercise-window-tabs>button', buttons =>
            buttons.map(b=>b.getBoundingClientRect().height));
        assert.ok(rangesR20.every(h=>h>=44),
            'R20 rendered exercise chart ranges use accessible hit heights: '+JSON.stringify(rangesR20));

        assert.ok(await page.$eval('[data-exercisedetail]',el=>el.scrollWidth<=el.clientWidth+1),
            'exercise chart metric choices scroll rather than clipping screen width');
        // Capture the selected Charts tab, not a transient keyboard focus ring
        // on the next tab. Keyboard focus remains separately tested above.
        if (width===430) await page.screenshot({ path: path.join(root, 'verification/b831-m3-exercise-charts-phone.png') });
        // An 80-session, multi-year chronology needs one scrub target, not 80
        // overlapping 12px circles. Use a separate synthetic history fixture,
        // preserving the user's simulated saved-program fields exactly.
        await page.evaluate(store => {
            const denseHistory = Array.from({length:80},(_,i)=>{
                const w=175+i;
                return {id:'b831-dense-'+i, programId:store.saved[0].id,dayId:'lower',
                    date:Date.UTC(2024,0,1+i*7),unit:'lb',
                    perf:{'back-squat':{weight:w,reps:5,sets:[{w,r:5,done:true,rir:2}]}}};
            });
            localStorage.setItem('wpb:v1',JSON.stringify({...store,history:denseHistory}));
        },initial);
        await page.reload({waitUntil:'networkidle0'});
        await page.waitForSelector('.wpb-tabbar');
        await page.click('[data-tab="settings"]');
        await page.waitForSelector('.wpb-settings');
        await clickText('Exercise library');
        await page.waitForSelector('.wpb-library-search input');
        await page.click('.wpb-library-search input');
        await page.keyboard.type('Back Squat');
        await page.waitForSelector('.wpb-library-row');
        await page.click('.wpb-library-row');
        await page.waitForSelector('[data-exercisedetail] .wpb-exercise-tabs');
        await page.click('.wpb-exercise-tabs>button:nth-child(2)');
        await page.waitForSelector('[data-exercisecharts]');
        const denseDiagnostic = await page.evaluate(() => {
            const saved = JSON.parse(localStorage.getItem('wpb:v1') || '{}');
            const svg = document.querySelector('.wpb-chart[data-metricchart]');
            return {
                savedHistory: saved.history?.length,
                savedFirst: saved.history?.[0]?.date,
                savedLast: saved.history?.at(-1)?.date,
                visibleHistory: document.querySelectorAll('.wpb-exercise-history-session').length,
                chartPoints: svg?.dataset.points,
                chartMetric: svg?.dataset.metricchart,
                emptyRange: !!document.querySelector('[data-empty-window]'),
                detailTabs: [...document.querySelectorAll('.wpb-exercise-tabs>button')].map(b => ({
                    label: b.textContent, selected: b.getAttribute('aria-selected')
                })),
                surfaceExcerpt: document.querySelector('[data-exercisecharts]')?.textContent?.slice(0, 180)
            };
        });
        console.log('B831 DENSE CHART DIAGNOSTIC ' + JSON.stringify(denseDiagnostic));
        assert.ok(Number(denseDiagnostic.chartPoints) > 36,
            'dense seeded lift must appear on the real chart before scrubbing: ' + JSON.stringify(denseDiagnostic));
        await page.waitForSelector('[data-chart-scrubber]');
        const denseSummary=await page.$eval('.wpb-chart[data-metricchart]',svg=>({
            n:Number(svg.dataset.points),touches:svg.querySelectorAll('[role="button"]').length,
            max:Number(svg.querySelector('[data-chart-scrubber]').getAttribute('aria-valuemax'))}));
        assert.ok(denseSummary.n>=60 && denseSummary.touches===0 &&
            denseSummary.max===denseSummary.n, 'dense history preserves all records and uses one scrubber: '+JSON.stringify(denseSummary));
        // Puppeteer's page.focus only accepts HTMLElement; the accessible
        // chart inspector is an SVG element with a native focus() method.
        await page.$eval('[data-chart-scrubber]', element => {
            element.focus();
            assertFocus(element);
            function assertFocus(el) {
                if (document.activeElement !== el) throw new Error('SVG inspector is not keyboard-focusable');
            }
        });
        await page.keyboard.press('Home');
        assert.equal(await page.$eval('[data-chart-scrubber]',x=>x.getAttribute('aria-valuenow')),'1');
        await page.keyboard.press('End');
        assert.equal(Number(await page.$eval('[data-chart-scrubber]',x=>x.getAttribute('aria-valuenow'))),denseSummary.n);
        await page.keyboard.press('ArrowLeft');
        assert.equal(Number(await page.$eval('[data-chart-scrubber]',x=>x.getAttribute('aria-valuenow'))),denseSummary.n-1);
        assert.ok(await page.$eval('.wpb-chart-axis',x=>x.textContent.includes('24')&&x.textContent.includes('25')),
            'date labels distinguish multiple years');
        if(width===430)await page.screenshot({path:path.join(root,'verification/b831-m3-dense-chart-phone.png')});
        await page.click('.wpb-exercise-detail-header button[aria-label="Back to library"]');
        await page.waitForSelector('[data-exercisedetail]',{hidden:true});
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),initial.saved,
            'exercise detail view and charts do not change saved training programs');
        // R21: temporarily introduce a second *valid* program to exercise
        // the real Home switcher. Never tap Switch; check selection/disabled
        // semantics, close without altering ownership, then restore the
        // fixture before closing the browser context.
        await page.evaluate(seed => {
            const copy={...seed.saved[0],id:'r21-secondary',name:
                'Secondary customized strength and hypertrophy development program'};
            localStorage.setItem('wpb:v1',JSON.stringify({
                ...seed,saved:[seed.saved[0],copy]
            }));
            localStorage.removeItem('wpb:live');
        },initial);
        await page.reload({waitUntil:'networkidle0'});
        await page.waitForSelector('button[aria-label="Switch program"]');
        await page.click('button[aria-label="Switch program"]');
        await page.waitForSelector('.wpb-switcher-sheet .wpb-switcher-row');
        const switcherR21 = await page.$eval('.wpb-switcher-sheet', sheet => {
            const rows=[...sheet.querySelectorAll('.wpb-switcher-row')];
            const active=rows.find(row=>row.dataset.state==='active');
            const available=rows.find(row=>row.dataset.state==='available');
            const longTitle=available?.querySelector(':scope > div:first-child > div:first-child > span:first-child');
            const done=sheet.querySelector(':scope > div:first-child > button');
            const ctx=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
            const paint=getComputedStyle(sheet);ctx.fillStyle=paint.backgroundColor;
            ctx.fillRect(0,0,1,1);
            return {alpha:ctx.getImageData(0,0,1,1).data[3],
                image:paint.backgroundImage,
                doneHeight:done?.getBoundingClientRect().height||0,
                rows:rows.length,activeDisabled:active?.disabled,
                availableDisabled:available?.disabled,
                minRowHeight:Math.min(...rows.map(x=>x.getBoundingClientRect().height)),
                titleWhiteSpace:longTitle?getComputedStyle(longTitle).whiteSpace:null,
                titleTextOverflow:longTitle?getComputedStyle(longTitle).textOverflow:null,
                clipped:sheet.scrollWidth>sheet.clientWidth+1};
        });
        assert.ok(switcherR21.alpha===255 && switcherR21.image==='none' &&
            switcherR21.doneHeight>=44 && switcherR21.rows===2 &&
            switcherR21.activeDisabled===true && switcherR21.availableDisabled===false &&
            switcherR21.minRowHeight>=70 && switcherR21.titleWhiteSpace==='normal' &&
            switcherR21.titleTextOverflow!=='ellipsis' && !switcherR21.clipped,
            'R21 switcher retains working program-state semantics with readable names: '+JSON.stringify(switcherR21));
        if (width===430) await page.screenshot({path:path.join(root,'verification/b831-m3-program-switcher-phone.png')});
        await page.click('.wpb-switcher-sheet>div:first-child>button');
        await page.waitForSelector('.wpb-switcher-sheet',{hidden:true});
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).saved.length),2,
            'closing the switcher must not change saved programs');
        await page.evaluate(seed => {
            localStorage.setItem('wpb:v1',JSON.stringify(seed));
        },initial);
        await page.reload({waitUntil:'networkidle0'});
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),initial.saved,
            'R21 switcher test restores the source program unchanged');
        assert.deepEqual(errors, []);
        await page.close();
        await context.close();
        console.log(`PASS expressive app motion, sheet drag, rapid navigation, reference/RIR persistence and reduced motion at ${width}px.`);
    }
    // R25: true first-run storage, isolated from every saved-program and
    // resumed-workout fixture. Inspect welcome paint and hitboxes only.
    for (const width of [320,430]) {
        const cleanContext=await browser.createBrowserContext();
        const welcome=await cleanContext.newPage();
        await welcome.setViewport({width,height:844,deviceScaleFactor:1,isMobile:true,hasTouch:true});
        const welcomeErrors=[];
        welcome.on('pageerror',error=>welcomeErrors.push(error.message));
        await welcome.goto('http://127.0.0.1:8794/',{waitUntil:'networkidle0'});
        await welcome.waitForSelector('.wpb-onboarding',{timeout:12000});
        const startR25=await welcome.$eval('.wpb-onboarding',screen=>{
            const footer=screen.querySelector('.wpb-onboarding-footer');
            const primary=screen.querySelector('.wpb-onboarding-primary');
            const secondary=screen.querySelector('.wpb-onboarding-secondary');
            const features=[...screen.querySelectorAll('.wpb-onboarding-feature')];
            const rgba=el=>{
                const style=getComputedStyle(el),canvas=document.createElement('canvas').getContext('2d',{willReadFrequently:true});
                canvas.fillStyle=style.backgroundColor;canvas.fillRect(0,0,1,1);
                return {alpha:canvas.getImageData(0,0,1,1).data[3],image:style.backgroundImage};
            };
            return {paint:rgba(screen),footer:rgba(footer),
                featureCount:features.length,
                featurePaint:features[0]?rgba(features[0]):null,
                featureOverflow:features.some(el=>el.scrollWidth>el.clientWidth+1),
                primaryHeight:primary?.getBoundingClientRect().height||0,
                secondaryHeight:secondary?.getBoundingClientRect().height||0,
                primaryTouch:primary?getComputedStyle(primary).touchAction:'',
                secondaryTouch:secondary?getComputedStyle(secondary).touchAction:'',
                overflow:screen.scrollWidth>screen.clientWidth+1};
        });
        assert.ok(startR25.paint.alpha===255 && startR25.paint.image==='none' &&
            startR25.footer.alpha===255 && startR25.footer.image==='none' &&
            startR25.featureCount>=2 && startR25.featurePaint?.alpha===255 &&
            startR25.featurePaint?.image==='none' && !startR25.featureOverflow &&
            startR25.primaryHeight>=50 && startR25.primaryTouch==='manipulation' &&
            (startR25.secondaryHeight===0 || (startR25.secondaryHeight>=50 &&
                startR25.secondaryTouch==='manipulation')) &&
            !startR25.overflow,
            'R25 cold-start welcome is solid and touch-safe: '+JSON.stringify(startR25));
        if(width===430) await welcome.screenshot({path:path.join(root,'verification/b831-m3-onboarding-phone.png')});
        assert.deepEqual(welcomeErrors,[]);
        await cleanContext.close();
    }
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
