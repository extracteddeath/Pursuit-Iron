// Shared interaction motion. Presentation only: this module never reads or writes training data.
// Spatial springs can be interrupted without losing velocity; effects use critical damping.
const states = new WeakMap();
const active = new Set();
let frame = 0, previousAt = 0;
const reduced = () => globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;

export function motionValue(el, key, fallback = 0) {
    return states.get(el)?.get(key)?.value ?? fallback;
}
export function stopMotion(el, key) {
    const state = states.get(el)?.get(key);
    if (state) { active.delete(state); state.complete = null; }
}
export function setMotionValue(el, key, value, write) {
    stopMotion(el, key);
    let map = states.get(el);
    if (!map) states.set(el, map = new Map());
    const state = map.get(key) || { el, key, velocity: 0 };
    Object.assign(state, { value, target: value, velocity: 0, write });
    map.set(key, state);
    write(value);
}
export function spring(el, key, target, options = {}) {
    if (!el) { options.complete?.(); return () => {}; }
    let map = states.get(el);
    if (!map) states.set(el, map = new Map());
    let state = map.get(key);
    if (!state) {
        state = { el, key, value: options.from ?? target, velocity: 0 };
        map.set(key, state);
    }
    Object.assign(state, {
        target, stiffness: options.stiffness ?? 700, damping: options.damping ?? .9,
        write: options.write || (v => el.style.setProperty(key, String(v))),
        complete: options.complete, precision: options.precision ?? .02
    });
    if (options.restart !== undefined) { state.value = options.restart; state.velocity = 0; }
    if (options.velocity !== undefined) state.velocity = options.velocity;
    state.write(state.value);
    if (reduced() || globalThis.document?.visibilityState === 'hidden') {
        settle(state);
        return () => {};
    }
    active.add(state);
    if (!frame) { previousAt = performance.now(); frame = requestAnimationFrame(tick); }
    const callback = state.complete;
    return () => { if (state.complete === callback) stopMotion(el, key); };
}
function settle(state) {
    state.value = state.target; state.velocity = 0;
    state.write(state.target); active.delete(state);
    const complete = state.complete; state.complete = null; complete?.();
}
function tick(now) {
    const dt = Math.min(Math.max(0, (now - previousAt) / 1000), .032) / 3;
    previousAt = now;
    active.forEach(state => {
        if (!state.el.isConnected) { active.delete(state); state.complete = null; return; }
        for (let step = 0; step < 3; step++) {
            state.velocity += (state.stiffness * (state.target - state.value)
                - 2 * state.damping * Math.sqrt(state.stiffness) * state.velocity) * dt;
            state.value += state.velocity * dt;
        }
        state.write(state.value);
        if (Math.abs(state.target - state.value) < state.precision && Math.abs(state.velocity) < state.precision)
            settle(state);
    });
    frame = active.size ? requestAnimationFrame(tick) : 0;
}
const pixels = (el, name) => v => el.style.setProperty(name, `${v}px`);
const surfaceSelector = '[data-view-frame], [data-pi-workout-page], [data-sheet-drag], .wpb-pop, .wpb-dialog, .wpb-slideL, .wpb-slideR, .wpb-state-enter, .wpb-float-in, .wpb-notice-in';
const groupSelector = '.wpb-segmented, .wpb-premium-tabs, .wpb-exercise-tabs, .wpb-library-flag-filter, .wpb-progress-grouping, .wpb-plan-panes, .wpb-exercise-window-tabs, .wpb-exercise-metric-tabs, .wpb-effort-scale, .wpb-tabbar, [role="tablist"]';
const surfaceOwners = new WeakMap();

function enterSurface(el, initial = false) {
    if (el.closest('.wpb-closing')) return;
    const key = el.getAttribute('data-view-frame') ?? el.getAttribute('data-motion-key') ?? 'mounted';
    if (surfaceOwners.get(el) === key) return;
    const first = !surfaceOwners.has(el);
    surfaceOwners.set(el, key); el.dataset.piSurface = '1';
    const sheet = el.matches('[data-sheet-drag]');
    const dialog = el.matches('.wpb-pop,.wpb-dialog');
    const root = el.closest('#root');
    const routeView = el.getAttribute('data-view-frame');
    const routeDirection = !initial && routeView && root?.dataset.piRouteTarget === routeView
        ? root.dataset.piRouteDirection : null;
    const dir = el.matches('.wpb-slideR') || el.getAttribute('data-motion-direction') === 'r' ? -1
        : el.matches('.wpb-slideL') || el.getAttribute('data-motion-direction') === 'l' ? 1
        : routeDirection === 'forward' ? 1 : routeDirection === 'backward' ? -1 : 0;
    const y = sheet ? 52 : dialog ? 8 : el.matches('.wpb-notice-in') ? -6 : dir ? 0 : 4;
    const quiet = initial || reduced();
    const restart = (name, value) => !first && !quiet && !active.has(states.get(el)?.get(name)) ? value : undefined;
    spring(el, '--pi-surface-x', 0, { from: quiet ? 0 : dir * (routeDirection ? 34 : 18),
        restart: restart('--pi-surface-x', dir * (routeDirection ? 34 : 18)),
        stiffness: routeDirection ? 520 : 700, damping: routeDirection ? .9 : .93,
        write: pixels(el, '--pi-surface-x') });
    spring(el, '--pi-surface-y', 0, { from: quiet ? 0 : y, restart: restart('--pi-surface-y', y), write: pixels(el, '--pi-surface-y'), damping: sheet ? .94 : 1 });
    spring(el, '--pi-surface-opacity', 1, { from: quiet ? 1 : .55, restart: restart('--pi-surface-opacity', .55), stiffness: 1200, damping: 1, precision: .001 });
    if (dialog) spring(el, '--pi-surface-scale', 1, { from: quiet ? 1 : .97, damping: .9, precision: .001 });
}
export function exitMotion(wrapper, complete) {
    let cancelled = false;
    const panels = [...wrapper.querySelectorAll('[data-sheet-drag],.wpb-pop,.wpb-dialog')];
    const backs = [...wrapper.querySelectorAll('.wpb-backdrop')];
    let pending = panels.length + backs.length;
    const stops = [];
    const done = () => { if (--pending === 0 && !cancelled) complete(); };
    if (!pending) { complete(); return () => {}; }
    panels.forEach(el => {
        el.dataset.piSurface = '1';
        spring(el, '--pi-surface-opacity', 0, { stiffness: 1400, damping: 1, precision: .001 });
        stops.push(spring(el, '--pi-surface-y', el.matches('[data-sheet-drag]') ? 64 : 8,
            { write: pixels(el, '--pi-surface-y'), stiffness: 1100, damping: 1, complete: done }));
    });
    backs.forEach(el => {
        el.dataset.piBackdrop = '1';
        stops.push(spring(el, '--pi-backdrop-opacity', 0, { from: 1, stiffness: 1400, damping: 1, precision: .001, complete: done }));
    });
    return () => { cancelled = true; stops.forEach(stop => stop()); };
}
export function resumeMotion(wrapper) {
    if (!wrapper) return;
    wrapper.querySelectorAll('[data-sheet-drag],.wpb-pop,.wpb-dialog').forEach(el => {
        if (!surfaceOwners.has(el)) { enterSurface(el); return; }
        spring(el, '--pi-surface-y', 0, { write: pixels(el, '--pi-surface-y'), damping: .94 });
        spring(el, '--pi-surface-opacity', 1, { stiffness: 1200, damping: 1, precision: .001 });
    });
    wrapper.querySelectorAll('.wpb-backdrop').forEach(el => {
        el.dataset.piBackdrop = '1';
        spring(el, '--pi-backdrop-opacity', 1, { from: 0, stiffness: 1200, damping: 1, precision: .001 });
    });
}
export function settleSheet(el, velocity = 0) {
    return spring(el, '--pi-surface-y', 0, { velocity, write: pixels(el, '--pi-surface-y'), damping: .94 });
}
export function moveSheet(el, value) {
    el.dataset.piSurface = '1';
    setMotionValue(el, '--pi-surface-y', value, pixels(el, '--pi-surface-y'));
}

export function installAppMotion(root) {
    if (!root) return () => {};
    root.dataset.piMotion = 'expressive';
    const pressed = new Map(), controls = new WeakMap(), choiceStates = new WeakMap(), referenceKeys = new WeakMap();
    const media = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)');
    let menuOpener = null;
    // Anchored program menus must clear the live workout dock as well as the
    // bottom tab bar. The old viewport-only flip can hide Delete underneath
    // a resumed-workout dock even though the menu fits within window.innerHeight.
    // Individual CSS translate composes with the existing popIn animation.
    function positionContextMenu(el) {
        if (!el || !el.isConnected) return;
        el.dataset.piM3Menu = '1';
        requestAnimationFrame(() => {
            if (!el.isConnected) return;
            const existing = Number.parseFloat(el.style.getPropertyValue('--pi-menu-shift-y')) || 0;
            const box = el.getBoundingClientRect();
            let bottomLimit = Math.min(window.innerHeight - 8,
                globalThis.visualViewport ? visualViewport.height + visualViewport.offsetTop - 8 : Infinity);
            for (const barrier of root.querySelectorAll('.wpb-live-dock,.wpb-tabbar')) {
                const r = barrier.getBoundingClientRect();
                if (r.width && r.height && r.bottom > 0 && r.top < window.innerHeight &&
                    getComputedStyle(barrier).visibility !== 'hidden') {
                    bottomLimit = Math.min(bottomLimit, r.top - 8);
                }
            }
            // Undo any previous translate so repeated resizes cannot accumulate a drift.
            const rawBottom = box.bottom - existing, rawTop = box.top - existing;
            const shift = Math.max(8 - rawTop, Math.min(0, bottomLimit - rawBottom));
            el.style.setProperty('--pi-menu-shift-y', `${Math.round(shift * 100) / 100}px`);
        });
    }
    const positionOpenMenus = () => root.querySelectorAll('.wpb-context-menu')
        .forEach(positionContextMenu);

    // Material 3 selection tracks sit behind the actual React-owned buttons. Geometry is
    // measured, never guessed, so four-column and two-column selectors share one behavior.
    const selectionTracks = new WeakMap();
    const trackSelector = '.wpb-progress .wpb-premium-tabs, .wpb-settings .wpb-segmented, .wpb-exercise-tabs, .wpb-library-flag-filter, .wpb-progress-grouping, .wpb-plan-panes, .wpb-exercise-window-tabs, .wpb-tabbar';
// Delayed visual entrances are bounded to five high-level cards per new page.
// No workout logger controls, engine values or React-owned children participate.
const entranceKeys = new WeakMap();
const entranceSelector = '.wpb-home-hero, .wpb-home .wpb-action-tile, .wpb-program .wpb-day-card, .wpb-progress .wpb-progress-glance-item, .wpb-profile .wpb-profile-strength-cell, .wpb-settings .wpb-settings-card, .wpb-plan-view .wpb-plan-glance-tile, .wpb-plan-view .wpb-plan-up-next';
function stagePage(el, initial = false) {
    if (!el.matches('[data-view-frame]')) return;
    const key = el.getAttribute('data-view-frame');
    if (entranceKeys.get(el) === key) return;
    entranceKeys.set(el, key);
    if (initial || reduced() || !key || key === 'workout' || el.closest('[data-pi-workout-page]')) return;
    el.dataset.piCascade = '1';
    [...el.querySelectorAll(entranceSelector)].slice(0, 5).forEach((item, index) => {
        if (item.closest('.wpb-workout,[data-pi-workout-page],[data-sheet-drag]')) return;
        delete item.dataset.piReveal;
        item.style.setProperty('--pi-reveal-delay', `${index * 26}ms`);
        item.dataset.piReveal = '1';
    });
}
const navSurface = button =>
    `color-mix(in srgb, ${getComputedStyle(button).color} 14%, transparent)`;

/* Shared disclosure orchestration.
   The content is still mounted/removed by React: this layer observes actual
   semantic state and animates only the card shell/arriving content. Never
   clone a live form or delay a program/logging state change to finish a tween. */
const disclosureSelector = '.wpb-program .wpb-day-card, .hp-card, .wpb-profile-achievement-toggle, .wpb-progress-disclosure, .wpb-settings details.wpb-advanced';
const disclosureStates = new WeakMap();
function disclosure(el, initial = false) {
    const day = el.matches('.wpb-program .wpb-day-card');
    const history = el.matches('.hp-card');
    const native = el.matches('details');
    const trigger = day ? el.querySelector('.wpb-day-toggle') :
        history ? el.querySelector('.hp-action[aria-expanded]') :
        el.matches('.wpb-profile-achievement-toggle') ? el :
        native ? el.querySelector('summary') : null;
    if (!trigger) return;
    const panel = day ? el.querySelector(':scope > .wpb-expand') :
        history ? el.querySelector(':scope > .hp-blocks') :
        el.matches('.wpb-profile-achievement-toggle')
            ? (el.nextElementSibling?.matches('.wpb-profile-achievement-board') ? el.nextElementSibling : null) :
        native ? el.querySelector(':scope > .wpb-advanced-body') :
        el.querySelector(':scope > *:not(summary)');
    const open = native ? el.open : day ? !!panel : trigger.getAttribute('aria-expanded') === 'true';
    const previous = disclosureStates.get(el);
    if (day) {
        // The day header currently omits expanded semantics. Reflect the real
        // mounted panel without changing its React click/scroll behavior.
        if (trigger.getAttribute('aria-expanded') !== (open ? 'true' : 'false'))
            trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
        if (panel && el.id) {
            const id = el.id + '-content';
            panel.id = id;
            trigger.setAttribute('aria-controls', id);
        } else trigger.removeAttribute('aria-controls');
    }
    el.dataset.piDisclosure = open ? 'open' : 'closed';
    if (previous === open && !initial) return;
    disclosureStates.set(el, open);
    if (day || history) {
        const target = open ? 21 : 16;
        if (previous === undefined || initial)
            setMotionValue(el, '--pi-disclosure-radius', target, pixels(el, '--pi-disclosure-radius'));
        else
            spring(el, '--pi-disclosure-radius', target, { write: pixels(el, '--pi-disclosure-radius'), stiffness: 760, damping: .84, precision: .015 });
    }
    if (open && panel && previous === false && !reduced()) {
        panel.dataset.piExpandEnter = '1';
    }
}

    const trackResize = typeof ResizeObserver === 'function' ? new ResizeObserver(entries => {
        for (const entry of entries) {
            const group = entry.target, selected = choiceStates.get(group);
            if (root.contains(group) && Number.isInteger(selected) && selected >= 0)
                positionTrack(group, [...group.children].filter(el => el.matches('button')), selected, true);
        }
    }) : null;
    function selectedSurface(group, button) {
        // The original theme uses contrast-tested selected fills that may NOT be
        // its bright accent color (e.g. lime uses a dark surface with light text).
        // Temporarily resolve the original CSS cascade before enabling the moving
        // overlay; all attributes are restored synchronously before the next paint.
        const tracked = group.hasAttribute('data-pi-m3-track');
        if (tracked) delete group.dataset.piM3Track;
        const css = getComputedStyle(button);
        const color = css.backgroundColor;
        // Exercise detail uses a theme-tonal gradient instead of a solid fill.
        const fill = (color === 'transparent' || color === 'rgba(0, 0, 0, 0)') && css.backgroundImage !== 'none'
            ? css.backgroundImage : color;
        if (tracked) group.dataset.piM3Track = '1';
        return fill;
    }
    function positionTrack(group, buttons, selected, instant = false) {
        if (!group.matches(trackSelector) || selected < 0 || !buttons[selected]) return;
        const button = buttons[selected];
        let state = selectionTracks.get(group);
        if (!state) {
            // Sample the real selected surface BEFORE overriding it with the moving track.
            // Settings derives its active fill from the saved theme; Progress uses its card tone.
            const fill = group.matches('.wpb-tabbar') ? navSurface(button) : selectedSurface(group, button);
            state = { fill }; selectionTracks.set(group, state);
            group.style.setProperty('--pi-m3-track-fill', fill);
            group.dataset.piM3Track = '1';
            trackResize?.observe(group);
        }
        const computedFill = group.matches('.wpb-tabbar') ? navSurface(button) : selectedSurface(group, button);
        if (computedFill && computedFill !== 'transparent' && computedFill !== state.fill) {
            state.fill = computedFill;
            group.style.setProperty('--pi-m3-track-fill', state.fill);
        }
        const nav = group.matches('.wpb-tabbar');
        // Expressive selection grows into the surrounding whitespace while
        // retaining all five original tab widths and labels.
        const width = nav ? Math.min(60, button.offsetWidth * .78) : button.offsetWidth;
        const target = {
            '--pi-m3-track-x': button.offsetLeft + (nav ? (button.offsetWidth - width) / 2 : 0),
            '--pi-m3-track-y': button.offsetTop + (nav ? 3 : 0),
            '--pi-m3-track-width': width,
            '--pi-m3-track-height': nav ? 33 : button.offsetHeight
        };
        for (const [key, value] of Object.entries(target)) {
            if (instant || state[key] === undefined) setMotionValue(group, key, value, pixels(group, key));
            else if (Math.abs(state[key] - value) > .05)
                spring(group, key, value, { write: pixels(group, key), stiffness: 800, damping: .89, precision: .01 });
            state[key] = value;
        }
    }
    // A brief touch-origin state layer on navigation and choices, not the dense workout set grid.
    // It never inserts a DOM child, changes a hit target, or consumes a click.
    const inkTargets = '.wpb-tab, .wpb-segmented > button, .wpb-premium-tabs > button, .wpb-exercise-tabs > button, .wpb-exercise-window-tabs > button, .wpb-exercise-metric-tabs > button, .wpb-progress-grouping > button, .wpb-plan-panes > button, .wpb-wizard-option, .wpb-wizard-choice, .wpb-wizard-preset, .wpb-filter-chip, .wpb-library-flag, .wpb-onboarding-primary, .wpb-onboarding-secondary, [role="tablist"] > button, button.wpb-toggle, .wpb-context-menu-item, .wpb-sheet-close';
    function ink(el, x, y) {
        if (reduced() || !el.matches(inkTargets)) return;
        const r = el.getBoundingClientRect();
        if (!r.width || !r.height) return;
        const px = Number.isFinite(x) ? Math.max(0, Math.min(r.width, x - r.left)) : r.width / 2;
        const py = Number.isFinite(y) ? Math.max(0, Math.min(r.height, y - r.top)) : r.height / 2;
        el.style.setProperty('--pi-ink-x', `${px}px`);
        el.style.setProperty('--pi-ink-y', `${py}px`);
        if (el.hasAttribute('data-pi-ink')) {
            delete el.dataset.piInk;
            void el.offsetWidth; // restart a repeated tap without replacing a React-owned element
        }
        el.dataset.piInk = '1';
    }
    const inkEnd = e => {
        if (e.animationName === 'piInkBurst' && e.target?.dataset) delete e.target.dataset.piInk;
        if (e.animationName === 'piContentCascade' && e.target?.dataset) delete e.target.dataset.piReveal;
        if (e.animationName === 'piDisclosureContent' && e.target?.dataset) delete e.target.dataset.piExpandEnter;
        if (e.animationName === 'piExpressiveStepIn' && e.target?.dataset) delete e.target.dataset.piStepEnter;
        if (e.target?.matches?.('.wpb-context-menu')) positionContextMenu(e.target);
    };
    const clearInk = () => root.querySelectorAll('[data-pi-ink]').forEach(el => delete el.dataset.piInk);
    function control(el) {
        if (controls.has(el)) return;
        const css = getComputedStyle(el), box = el.getBoundingClientRect();
        ['TopLeft','TopRight','BottomRight','BottomLeft'].forEach((corner, i) => {
            const radius = parseFloat(css[`border${corner}Radius`]) || 0;
            el.style.setProperty(`--pi-corner-${i}`, `${Math.min(radius, box.height / 2, box.width / 2)}px`);
        });
        el.dataset.piControl = '1'; controls.set(el, true);
    }
    const disabled = el => !el || el.disabled || el.getAttribute('aria-disabled') === 'true' || el.closest('[inert],.wpb-closing');
    function shape(el, held = false) {
        const selected = el.dataset.piChoice === 'selected';
        const expressive = el.matches('.wpb-wizard-option,.wpb-wizard-choice,.wpb-wizard-preset,.wpb-filter-chip,.wpb-library-flag');
        const change = selected ? (expressive ? 11 : 7) : 0;
        spring(el, '--pi-control-shape', change - (held ? expressive ? 5 : 3 : 0),
            { from: 0, write: pixels(el, '--pi-control-shape'),
                stiffness: expressive ? 680 : 850, damping: expressive ? .81 : .9 });
    }
    // Utilitarian logging controls own their pre-existing compact feedback.
    // Do not morph/compress the set-complete check, numeric steppers, RIR row,
    // or Target/Last selector. Expressive feedback continues elsewhere.
    const protectedWorkoutPress = el => !!el && (
        !!el.closest('[data-testid^="set-"],.wpb-set-controls') ||
        el.matches('.wpb-set-complete,.wpb-set-stepper,.wpb-target-toggle')
    );
    function press(el, id, x = null, y = null) {
        if (disabled(el) || protectedWorkoutPress(el)) return;
        // A second pointer/keyboard press may supersede an interrupted first one.
        if (pressed.has(id)) release(id);
        control(el); pressed.set(id, { el, x, y });
        ink(el, x, y);
        const expressiveAction = el.matches('.wpb-home .wpb-action-tile,.wpb-home .wpb-primary-action,.wpb-wizard .wpb-wizard-option,.wpb-wizard .wpb-wizard-next-action');
        spring(el, '--pi-control-scale', expressiveAction ? .956 : .97,
            { from: 1, stiffness: expressiveAction ? 930 : 1300,
                damping: expressiveAction ? .83 : 1, precision: .001 });
        shape(el, true);
    }
    function release(id) {
        const held = pressed.get(id); if (!held) return;
        pressed.delete(id);
        const expressiveAction = held.el.matches('.wpb-home .wpb-action-tile,.wpb-home .wpb-primary-action,.wpb-wizard .wpb-wizard-option,.wpb-wizard .wpb-wizard-next-action');
        spring(held.el, '--pi-control-scale', 1,
            { stiffness: expressiveAction ? 640 : 850,
                damping: expressiveAction ? .72 : .78, precision: .001 });
        shape(held.el);
    }
    const button = e => e.target.closest?.('button');
    const down = e => { if (e.button === 0) press(button(e), e.pointerId, e.clientX, e.clientY); };
    const up = e => release(e.pointerId);
    const move = e => { const h = pressed.get(e.pointerId); if (h && Math.hypot(e.clientX - h.x, e.clientY - h.y) > 12) release(e.pointerId); };
    const keydown = e => {
        // Contextual program actions are a real role=menu. Keep keyboard focus
        // within its existing items for arrows/Home/End; Escape delegates to the
        // original React dismissal layer and returns to the initiating control.
        // No new menu elements, event handlers on items or backdrop gestures.
        const menu = e.target.closest?.('.wpb-context-menu[role="menu"]');
        if (menu && !e.altKey && !e.ctrlKey && !e.metaKey &&
            !e.isComposing && !e.target.matches('input,textarea,select,[contenteditable]')) {
            if (e.key === 'Escape') {
                const dismiss = root.querySelector('.wpb-dismiss-layer');
                if (dismiss) {
                    e.preventDefault(); e.stopPropagation();
                    dismiss.click();
                    if (menuOpener?.isConnected) menuOpener.focus({ preventScroll: true });
                    return;
                }
            }
            if (['ArrowDown','ArrowUp','Home','End'].includes(e.key)) {
                const items = [...menu.querySelectorAll('[role="menuitem"]')]
                    .filter(el => !disabled(el));
                if (items.length) {
                    const at = items.indexOf(e.target.closest('[role="menuitem"]'));
                    const next = e.key === 'Home' ? 0 : e.key === 'End' ? items.length - 1 :
                        (at + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
                    e.preventDefault(); e.stopPropagation();
                    items[next].focus({ preventScroll: true });
                    return;
                }
            }
        }
        // Progress is a real tablist: arrow/Home/End keys move focus AND activate the
        // selected panel. Settings' independent toggle buttons retain native semantics.
        const tab = e.target.closest?.('.wpb-progress .wpb-premium-tabs [role="tab"], .wpb-exercise-tabs button[aria-selected], .wpb-exercise-window-tabs button[role="tab"], .wpb-exercise-metric-tabs button[role="tab"]');
        if (tab && !e.altKey && !e.ctrlKey && !e.metaKey &&
            ['ArrowRight','ArrowLeft','Home','End'].includes(e.key)) {
            const tabs = [...tab.parentElement.children].filter(el => el.matches('[role="tab"]') && !disabled(el));
            if (tabs.length > 1) {
                const at = tabs.indexOf(tab);
                const next = e.key === 'Home' ? 0 : e.key === 'End' ? tabs.length - 1 :
                    (at + (e.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
                e.preventDefault();
                tabs[next].focus({ preventScroll: true });
                tabs[next].click();
            }
            return;
        }
        if (!e.repeat && !e.isComposing && (e.key === ' ' || e.key === 'Enter'))
            press(button(e), 'keyboard');
    };
    const keyup = e => { if (e.key === ' ' || e.key === 'Enter') release('keyboard'); };
    function selection(group, initial = false) {
        const buttons = [...group.children].filter(el => el.matches('button'));
        const selected = buttons.findIndex(b => b.getAttribute('aria-pressed') === 'true' || b.getAttribute('aria-selected') === 'true' || b.getAttribute('aria-current') === 'page');
        const previous = choiceStates.get(group); choiceStates.set(group, selected);
        if (previous === selected && !initial) return;
        positionTrack(group, buttons, selected, initial);
        const rir = group.matches('.wpb-effort-scale');
        // Material's expressive button group: the active option takes more
        // space, adjacent options yield gently, and the painted selection
        // follows their measured bounds. The underlying React controls stay
        // present and in the same order, with identical keyboard semantics.
        const fluid = group.matches('.wpb-progress .wpb-premium-tabs,.wpb-library .wpb-library-flag-filter,.wpb-plan-panes');
        if (fluid) group.dataset.piM3Fluid = '1';
        let trackQueued = false;
        const followFluid = () => {
            if (!fluid || trackQueued) return;
            trackQueued = true;
            requestAnimationFrame(() => {
                trackQueued = false;
                if (root.contains(group) && choiceStates.get(group) === selected)
                    positionTrack(group, buttons, selected, true);
            });
        };
        buttons.forEach((b, i) => {
            if (!rir && !b.hasAttribute('aria-selected') && !b.hasAttribute('aria-pressed') && !b.hasAttribute('aria-current') && !group.matches('.wpb-tabbar')) return;
            control(b); b.dataset.piChoice = i === selected ? 'selected' : 'unselected';
            const neighbor = selected >= 0 && Math.abs(i - selected) === 1;
            const grow = rir ? i === selected ? 1.18 : neighbor ? .91 : 1 :
                fluid ? i === selected ? 1.20 : neighbor ? .95 : 1 : 1;
            spring(b, '--pi-choice-grow', grow, {
                from: initial ? grow : 1,
                damping: fluid ? .83 : .9, stiffness: fluid ? 620 : 850,
                precision: .001,
                ...(fluid ? { write: value => {
                    b.style.setProperty('--pi-choice-grow', String(value));
                    followFluid();
                } } : {})
            });
            shape(b);
            if (group.matches('.wpb-tabbar')) {
                const icon = b.querySelector('svg');
                if (icon && !initial && previous !== undefined && i === selected)
                    spring(icon, '--pi-nav-scale', 1, { restart: .88, damping: .8, stiffness: 800, precision: .001 });
            }
        });
    }
    function refreshTrackTheme() {
        // ThemePicker updates the same React buttons in place. The active ink fill
        // must follow its new inline token even if the selected index did not change.
        root.querySelectorAll(trackSelector).forEach(group => {
            const state = selectionTracks.get(group);
            const chosen = group.querySelector('button[aria-selected="true"],button[aria-pressed="true"]');
            const fill = chosen ? (group.matches('.wpb-tabbar') ? navSurface(chosen) : selectedSurface(group, chosen)) : null;
            if (state && fill && fill !== 'transparent' && fill !== state.fill) {
                state.fill = fill;
                group.style.setProperty('--pi-m3-track-fill', fill);
            }
        });
    }
    // A native-feeling directional handoff between the *existing* five
    // destinations. Direction is captured before React switches the frame.
    // A sheet, a workout or a nested Library route never inherits a stale
    // direction because the destination must match the actual next frame.
    const rootTabs = ['home', 'plan', 'progress', 'profile', 'settings'];
    const navigationIntent = e => {
        const target = e.target.closest?.('.wpb-tabbar [data-tab]');
        if (!target) return;
        const current = root.querySelector('.wpb-tabbar [aria-current="page"][data-tab]');
        const from = rootTabs.indexOf(current?.dataset.tab);
        const to = rootTabs.indexOf(target.dataset.tab);
        if (from < 0 || to < 0 || from === to) return;
        root.dataset.piRouteTarget = target.dataset.tab;
        root.dataset.piRouteDirection = to > from ? 'forward' : 'backward';
    };
    const themeSelection = e => {
        const opener = e.target.closest?.('button[aria-haspopup="menu"]');
        if (opener) menuOpener = opener;
        if (e.target.closest?.('[data-theme-option]'))
            requestAnimationFrame(refreshTrackTheme);
    };
    function reference(el, initial = false) {
        const key = el.getAttribute('data-motion-key'), before = referenceKeys.get(el); referenceKeys.set(el, key);
        if (initial || before === undefined || before === key) return;
        el.dataset.piSurface = '1';
        const moving = active.has(states.get(el)?.get('--pi-surface-y'));
        spring(el, '--pi-surface-y', 0, { restart: moving ? undefined : key === 'last' ? 5 : -5, write: pixels(el, '--pi-surface-y'), damping: 1, stiffness: 900 });
        spring(el, '--pi-surface-opacity', 1, { restart: active.has(states.get(el)?.get('--pi-surface-opacity')) ? undefined : .55, stiffness: 1200, damping: 1, precision: .001 });
    }
    function toggle(el, initial = false) {
        const thumb = el.firstElementChild;
        if (!thumb) return;
        const on = el.getAttribute('aria-checked') === 'true';
        const travel = Math.max(0, el.clientWidth - thumb.clientWidth - 2 * (parseFloat(getComputedStyle(thumb).left) || 3));
        thumb.dataset.piThumb = '1';
        spring(thumb, '--pi-switch-x', on ? travel : 0, { from: initial ? on ? travel : 0 : on ? 0 : travel,
            write: pixels(thumb, '--pi-switch-x'), stiffness: 850, damping: .9 });
    }
    function option(el) {
        if (el.closest(groupSelector) || el.matches('.wpb-set-complete,[role="switch"]')) return;
        control(el); el.dataset.piChoice = el.getAttribute('aria-pressed') === 'true' ? 'selected' : 'unselected'; shape(el);
    }
    const enteredWizard = new WeakSet();
    function wizardStep(el, initial = false) {
        if (enteredWizard.has(el)) return;
        enteredWizard.add(el);
        if (initial || reduced()) return;
        // A keyed fieldset is mounted for each real step; animate the entire
        // authored step once. No control is cloned or delayed.
        el.dataset.piStepEnter = '1';
    }
    function scan(node, initial = false) {
        if (node.nodeType !== 1 || !root.contains(node)) return;
        const each = (selector, fn) => { if (node.matches(selector)) fn(node, initial); node.querySelectorAll(selector).forEach(el => fn(el, initial)); };
        each(surfaceSelector, enterSurface);
        each('[data-view-frame]', stagePage);
        each('.wpb-wizard-step', wizardStep);
        each(disclosureSelector, disclosure);
        each(groupSelector, selection);
        each('.wpb-toggle[aria-checked]', toggle);
        each('.wpb-context-menu', positionContextMenu);
        each('button[aria-pressed]:not(.wpb-set-complete)', option);
        each('[data-pi-reference]', reference);
        each('.wpb-backdrop', el => {
            if (el.dataset.piBackdrop || el.closest('.wpb-closing')) return;
            el.dataset.piBackdrop = '1';
            spring(el, '--pi-backdrop-opacity', 1, { from: initial ? 1 : 0, damping: 1, stiffness: 1200, precision: .001 });
        });
    }
    scan(root, true);
    const observer = new MutationObserver(records => {
        const groups = new Set(), disclosures = new Set();
        records.forEach(record => {
            if (record.type === 'childList') {
                record.addedNodes.forEach(node => scan(node));
                const host = record.target.nodeType === 1 ? record.target.closest(disclosureSelector) : null;
                if (host) disclosures.add(host);
            } else {
                const el = record.target;
                if (record.attributeName === 'data-motion-key') { if (el.matches('[data-pi-reference]')) reference(el); else enterSurface(el); }
                if (record.attributeName === 'data-view-frame') { enterSurface(el); stagePage(el); }
                if (record.attributeName === 'aria-checked' && el.matches('.wpb-toggle')) toggle(el);
                if (record.attributeName === 'aria-pressed' && el.matches('button')) option(el);
                if (record.attributeName === 'aria-expanded' || record.attributeName === 'open') {
                    const host = el.closest(disclosureSelector);
                    if (host) disclosures.add(host);
                }
                const group = el.closest(groupSelector); if (group) groups.add(group);
            }
        });
        groups.forEach(group => selection(group));
        disclosures.forEach(el => disclosure(el));
    });
    observer.observe(root, { subtree: true, childList: true, attributes: true,
        attributeFilter: ['data-view-frame','data-motion-key','aria-pressed','aria-selected','aria-current','aria-checked','aria-expanded','open'] });
    const settleAll = () => {
        [...pressed.keys()].forEach(release);
        [...active].filter(state => root.contains(state.el)).forEach(settle);
        clearInk();
    };
    const visibility = () => { if (document.visibilityState === 'hidden') settleAll(); };
    const preference = () => { if (media.matches) settleAll(); };
    root.addEventListener('animationend', inkEnd);
    root.addEventListener('pointerdown', down, { passive: true });
    document.addEventListener('pointerup', up, { passive: true });
    document.addEventListener('pointercancel', up, { passive: true });
    document.addEventListener('pointermove', move, { passive: true });
    root.addEventListener('click', navigationIntent, true);
    root.addEventListener('click', themeSelection);
    root.addEventListener('keydown', keydown); document.addEventListener('keyup', keyup);
    document.addEventListener('visibilitychange', visibility);
    window.addEventListener('blur', settleAll);
    window.addEventListener('resize', positionOpenMenus);
    globalThis.visualViewport?.addEventListener('resize', positionOpenMenus);
    media?.addEventListener('change', preference);
    return () => {
        observer.disconnect(); trackResize?.disconnect(); menuOpener = null;
        settleAll(); delete root.dataset.piMotion;
        root.removeEventListener('animationend', inkEnd);
        root.removeEventListener('pointerdown', down); document.removeEventListener('pointerup', up);
        document.removeEventListener('pointercancel', up); document.removeEventListener('pointermove', move);
        root.removeEventListener('click', navigationIntent, true);
        delete root.dataset.piRouteTarget; delete root.dataset.piRouteDirection;
        root.removeEventListener('click', themeSelection);
        root.removeEventListener('keydown', keydown); document.removeEventListener('keyup', keyup);
        document.removeEventListener('visibilitychange', visibility);
        window.removeEventListener('blur', settleAll);
        window.removeEventListener('resize', positionOpenMenus);
        globalThis.visualViewport?.removeEventListener('resize', positionOpenMenus);
        media?.removeEventListener('change', preference);
    };
}
