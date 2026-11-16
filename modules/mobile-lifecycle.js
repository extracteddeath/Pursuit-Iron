// Restore both timer anchors from one instant. While paused, elapsed time depends only on the
// pause anchor; sampling the moving wall clock twice can otherwise lose milliseconds on save.
export function restoreWorkoutClock(snapshot, now = Date.now()) {
    return {
        startedAt: Number.isFinite(snapshot?.elapsedMs) ? now - snapshot.elapsedMs : (snapshot?.startedAt || now),
        pausedAt: snapshot?.runPaused ? now : 0
    };
}

export function workoutElapsedMs(startedAt, pausedAccum = 0, pausedAt = 0, now = Date.now()) {
    return (pausedAt || now) - startedAt - pausedAccum;
}

// A screen-lock request can finish after Android backgrounds the app or the workout unmounts.
// Keep one request in flight and release late results instead of leaking a lock into Home.
export function holdWorkoutScreenAwake(nav = globalThis.navigator, doc = globalThis.document, win = globalThis.window) {
    if (!nav?.wakeLock?.request || !doc?.addEventListener)
        return () => {};
    let disposed = false;
    let pageHidden = false;
    let pending = false;
    let lock = null;
    const release = sentinel => {
        try { Promise.resolve(sentinel?.release()).catch(() => {}); }
        catch {}
    };
    const acquire = async () => {
        if (disposed || pageHidden || pending || doc.visibilityState !== 'visible' || (lock && !lock.released))
            return;
        pending = true;
        try {
            const sentinel = await nav.wakeLock.request('screen');
            if (disposed || pageHidden || doc.visibilityState !== 'visible') {
                release(sentinel);
                return;
            }
            lock = sentinel;
            sentinel.addEventListener?.('release', () => { if (lock === sentinel) lock = null; }, { once: true });
        }
        catch {} // Unsupported, battery-saving, and denied requests must not interrupt a workout.
        finally { pending = false; }
    };
    const releaseCurrent = () => {
        if (!lock) return;
        const previous = lock;
        lock = null;
        release(previous);
    };
    const onVisibility = () => {
        if (doc.visibilityState === 'visible') acquire();
        else releaseCurrent();
    };
    // A page can enter the back/forward cache without a visibilitychange. Do not
    // keep its screen lock, or accept a late request, until pageshow resumes it.
    const onPageHide = () => { pageHidden = true; releaseCurrent(); };
    const onPageShow = () => { pageHidden = false; acquire(); };
    doc.addEventListener('visibilitychange', onVisibility);
    win?.addEventListener('pagehide', onPageHide);
    win?.addEventListener('pageshow', onPageShow);
    acquire();
    return () => {
        disposed = true;
        doc.removeEventListener('visibilitychange', onVisibility);
        win?.removeEventListener('pagehide', onPageHide);
        win?.removeEventListener('pageshow', onPageShow);
        releaseCurrent();
    };
}
