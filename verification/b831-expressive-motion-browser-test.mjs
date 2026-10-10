import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root = path.resolve(new URL('../', import.meta.url).pathname);
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
        const page = await browser.newPage(), errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setViewport({ width, height: 844, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
        await page.evaluateOnNewDocument((store, snapshot) => {
            if (localStorage.getItem('b831-seeded')) return;
            localStorage.setItem('b831-seeded', '1'); localStorage.setItem('wpb:v1', JSON.stringify(store)); localStorage.setItem('wpb:live', JSON.stringify(snapshot));
        }, initial, live);
        await page.goto('http://127.0.0.1:8794/', { waitUntil: 'networkidle0' });
        await page.waitForSelector('#root[data-pi-motion="expressive"] .wpb-tabbar');
        const clickText = text => page.$$eval('button', (buttons, text) => {
            const button = buttons.find(b => b.textContent.includes(text));
            if (!button) throw new Error('Missing button: ' + text);
            button.click();
        }, text);
        // Root tabs keep their navigation semantics, respond immediately, and settle without layout growth.
        for (const tab of ['plan','progress','profile','settings','home']) {
            await page.click(`[data-tab="${tab}"]`);
            await page.waitForSelector(`[data-view-frame="${tab}"][data-pi-surface]`);
            await page.waitForFunction(tab => document.querySelector(`[data-view-frame="${tab}"]`)?.style.getPropertyValue('--pi-surface-opacity') === '1', {}, tab);
            assert.equal(await page.$eval(`[data-tab="${tab}"]`, b => b.getAttribute('aria-current')), 'page');
            assert.ok(await page.$eval('.wpb', el => el.scrollWidth <= innerWidth + 1), `${tab} fits at ${width}px`);
            // page.click produces a real touch ripple. Check idle icon styling only AFTER
            // animationend clears the transient layer, not while the active ::after paints.
            await page.waitForFunction(tab => !document.querySelector(`[data-tab="${tab}"]`)?.hasAttribute('data-pi-ink'),
                { timeout: 5000 }, tab);
            const navPill = await page.$eval(`[data-tab="${tab}"]`, el => {
                const style = getComputedStyle(el, '::before');
                return { height: parseFloat(style.height), radius: style.borderRadius,
                    opacity: parseFloat(style.opacity), width: style.width,
                    display: style.display, oldUnderline: getComputedStyle(el, '::after').display };
            });
            assert.ok(navPill.height >= 27 && navPill.height <= 32 &&
                (parseFloat(navPill.width) >= 30 || navPill.width.startsWith('min(')),
                'selected navigation has a compact expressive pill: ' + JSON.stringify(navPill));
            assert.ok(navPill.opacity > .9 && navPill.radius.includes('px') && navPill.display !== 'none' &&
                navPill.oldUnderline === 'none', 'selected navigation paints only the rounded tonal pill: ' + JSON.stringify(navPill));
            if (tab === 'home') {
                assert.ok(await page.$eval('.wpb-home-hero', el =>
                    parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 18 &&
                    parseFloat(getComputedStyle(el).marginBottom) <= 14),
                    'home hero has a tighter spacious-card hierarchy');
            }
            if (tab === 'progress') {
                assert.ok(await page.$eval('.wpb-progress .wpb-premium-tabs', el =>
                    parseFloat(getComputedStyle(el).borderTopLeftRadius) >= 14),
                    'progress uses a unified pill-tab surface');
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
                        logoHeight: logo.getBoundingClientRect().height };
                });
                assert.ok(homeType.size >= 26 && homeType.size <= 31 && homeType.weight >= 700,
                    'Home title follows the expressive type scale');
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
            }
            if (width === 430) await page.screenshot({ path: path.join(root, `verification/b831-${tab}-phone.png`) });
        }
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
                Math.abs(Number.parseFloat(group.style.getPropertyValue('--pi-m3-track-x')) - selected.offsetLeft) < .15;
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
                minItem: item.getBoundingClientRect().height, items: el.querySelectorAll('[role="menuitem"]').length };
        });
        assert.ok(menuGeometry.radius >= 16 && menuGeometry.minItem >= 44 &&
            menuGeometry.left >= 0 && menuGeometry.right <= width + 1,
            'M3 contextual menus are rounded, scroll-safe and touch accessible: ' + JSON.stringify({ width, menuGeometry }));
        assert.ok(menuGeometry.items >= 2, 'contextual actions keep their original features');
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
        await page.click('.wpb-live-dock button[aria-label="Resume workout"]'); await page.waitForSelector('.wpb-workout');
        await page.waitForFunction(() => !document.body.innerText.includes('Resumed your in-progress workout'));
        const row = '[data-testid="set-0-0"]';
        await page.click(`${row} input[aria-label="weight"]`); await page.keyboard.type('195.5');
        const before = await page.$$eval(`${row} input`, els => els.map(el => el.value));
        for (let tap = 0; tap < 8; tap++) await page.click(tap % 2 ? 'button[aria-label="Show prescribed targets"]' : 'button[aria-label="Show previous workout values"]');
        assert.deepEqual(await page.$$eval(`${row} input`, els => els.map(el => el.value)), before, 'reference transitions never overwrite typed values');
        await page.waitForFunction(() => [...document.querySelectorAll('[data-pi-reference][data-pi-surface]')].every(el => el.style.getPropertyValue('--pi-surface-y') === '0px'));
        await page.click(`${row} button[aria-label="Mark set done"]`);
        await page.waitForSelector(`${row} [data-effort-picker]`);
        await page.waitForFunction(row => {
            const button = document.querySelector(row + ' button[aria-label="3 reps left"]');
            if (!button) return false;
            const r = button.getBoundingClientRect(), hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
            return hit === button || button.contains(hit);
        }, {}, row);
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
        // The completed workout interaction test intentionally leaves a live
        // session snapshot. Start the secondary-screen check from a normal
        // seeded Home state; an active session legitimately reopens Workout.
        // This is test fixture cleanup, after assertions of live persistence.
        await page.evaluate(store => {
            localStorage.removeItem('wpb:live');
            localStorage.setItem('wpb:v1', JSON.stringify(store));
        }, initial);
        await page.goto('http://127.0.0.1:8794/', { waitUntil: 'networkidle0' });
        await page.waitForSelector('#root[data-pi-motion="expressive"] .wpb-home-create');
        await page.click('.wpb-home-create');
        await page.waitForSelector('.wpb-wizard .wpb-wizard-progress');
        const wizardGeometry = await page.$eval('.wpb-wizard', el => {
            const bar = el.querySelector('.wpb-wizard-progress').getBoundingClientRect();
            const footer = el.querySelector('.wpb-wizard-footer').getBoundingClientRect();
            const screen = el.getBoundingClientRect();
            return { barHeight: bar.height, footer: footer.bottom, screen: screen.bottom,
                width: el.scrollWidth, viewport: innerWidth };
        });
        assert.ok(wizardGeometry.barHeight >= 4 && wizardGeometry.width <= width + 1 &&
            wizardGeometry.footer <= innerHeight + 2, 'M3 program wizard is compact, visible and never horizontally clipped: ' + JSON.stringify(wizardGeometry));
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
                Math.abs(parseFloat(el.style.getPropertyValue('--pi-m3-track-x')) - chosen.offsetLeft) < .2;
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
        assert.deepEqual(errors, []);
        await page.close();
        console.log(`PASS expressive app motion, sheet drag, rapid navigation, reference/RIR persistence and reduced motion at ${width}px.`);
    }
} finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
