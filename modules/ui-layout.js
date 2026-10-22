// Shared shell/notice geometry. Reads rendered layout only; never training state.
export function installNoticeLayout(root) {
    if (!root) return () => {};
    const noticeSelector = '.wpb-notice';
    const barrierSelector = '.wpb-tabbar,.wpb-live-dock,.wpb-workout-footer,.wpb-set-dock-prototype';
    let frame = 0;
    const observed = new Set();
    const update = () => {
        frame = 0;
        const notices = [...root.querySelectorAll(noticeSelector)];
        const barriers = [...root.querySelectorAll(barrierSelector)];
        const navigation = barriers.filter(el => el.matches('.wpb-tabbar'));
        const targets = new Set([root, ...navigation, ...notices, ...(notices.length ? barriers : [])]);
        for (const el of observed) if (!targets.has(el)) { resize?.unobserve(el); observed.delete(el); }
        for (const el of targets) if (!observed.has(el)) { resize?.observe(el); observed.add(el); }
        for (const bar of navigation) {
            const box = bar.getBoundingClientRect();
            // The live dock shares the rail's real inset, including enlarged text.
            bar.parentElement.style.setProperty('--pi-navigation-rail-width',
                `${box.height > box.width * 2 ? box.width : 0}px`);
        }
        const viewportTop = globalThis.visualViewport?.offsetTop || 0;
        const viewportBottom = Math.min(innerHeight,
            globalThis.visualViewport ? visualViewport.offsetTop + visualViewport.height : Infinity);
        const placed = [];
        for (const notice of notices) {
            const parent = notice.offsetParent || root;
            const bounds = parent.getBoundingClientRect();
            const own = notice.getBoundingClientRect();
            let limit = Math.min(bounds.bottom, viewportBottom);
            for (const el of barriers) {
                const box = el.getBoundingClientRect();
                if (!box.width || !box.height || box.bottom <= bounds.top || box.top >= viewportBottom ||
                    box.right <= own.left || box.left >= own.right ||
                    (el.matches('.wpb-tabbar') && box.height > box.width * 2) ||
                    getComputedStyle(el).visibility === 'hidden') continue;
                limit = Math.min(limit, box.top);
            }
            for (const box of placed) if (box.right > own.left && box.left < own.right)
                limit = Math.min(limit, box.top);
            notice.style.setProperty('--pi-notice-bottom', `${Math.max(8, bounds.bottom - limit + 8)}px`);
            notice.style.setProperty('--pi-notice-max-height', `${Math.max(0, limit - Math.max(bounds.top, viewportTop) - 16)}px`);
            notice.dataset.positioned = 'true';
            placed.push(notice.getBoundingClientRect());
        }
    };
    const schedule = () => { if (!frame) frame = requestAnimationFrame(update); };
    // A retained Undo notice must clear a newly returned live dock before its
    // first paint/hit test, not one scheduled frame after the route changed.
    const refresh = () => { if (frame) cancelAnimationFrame(frame); update(); };
    const resize = typeof ResizeObserver === 'function' ? new ResizeObserver(refresh) : null;
    const relevant = `${noticeSelector},${barrierSelector}`;
    const changes = new MutationObserver(records => {
        if (records.some(record => [...record.addedNodes, ...record.removedNodes].some(node =>
            node.nodeType === 1 && (node.matches(relevant) || node.querySelector(relevant))))) refresh();
    });
    changes.observe(root, { childList: true, subtree: true });
    window.addEventListener('resize', schedule);
    globalThis.visualViewport?.addEventListener('resize', schedule);
    globalThis.visualViewport?.addEventListener('scroll', schedule);
    update();
    return () => {
        changes.disconnect(); resize?.disconnect(); observed.clear();
        if (frame) cancelAnimationFrame(frame);
        window.removeEventListener('resize', schedule);
        globalThis.visualViewport?.removeEventListener('resize', schedule);
        globalThis.visualViewport?.removeEventListener('scroll', schedule);
    };
}
