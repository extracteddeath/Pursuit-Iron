import assert from 'node:assert/strict';
import { holdWorkoutScreenAwake, restoreWorkoutClock, workoutElapsedMs } from '../modules/mobile-lifecycle.js';

const pausedClock = restoreWorkoutClock({ elapsedMs: 120000, runPaused: true }, 1000000);
assert.deepEqual(pausedClock, { startedAt: 880000, pausedAt: 1000000 });
for (const now of [1000000, 1000001, 1001000, 2000000]) {
    assert.equal(workoutElapsedMs(pausedClock.startedAt, 0, pausedClock.pausedAt, now), 120000,
        'a paused timer is invariant under later wall-clock reads');
    const resumed = restoreWorkoutClock({ elapsedMs: 120000, runPaused: true }, now);
    assert.equal(workoutElapsedMs(resumed.startedAt, 0, resumed.pausedAt, now + 1), 120000,
        'restoring a paused snapshot cannot add or lose a millisecond');
}
assert.equal(workoutElapsedMs(1000, 200, 0, 1700), 500, 'running elapsed time excludes accumulated pauses');
assert.equal(workoutElapsedMs(1000, 200, 1600, 1700), 400, 'paused elapsed time ends at its pause anchor');
assert.equal(workoutElapsedMs(1700, 0, 1700, 1701), 0, 'a paused reset remains exactly zero');
assert.deepEqual(restoreWorkoutClock({ elapsedMs: 0, runPaused: true }, 1700), { startedAt: 1700, pausedAt: 1700 });
assert.deepEqual(restoreWorkoutClock({ startedAt: 1000 }, 1700), { startedAt: 1000, pausedAt: 0 },
    'legacy snapshots retain their wall-clock start');
assert.deepEqual(restoreWorkoutClock(null, 1700), { startedAt: 1700, pausedAt: 0 });

const flush = () => new Promise(resolve => setImmediate(resolve));
const deferred = () => { let resolve, reject; const promise = new Promise((ok, no) => { resolve = ok; reject = no; }); return { promise, resolve, reject }; };
const sentinel = () => {
    const value = new EventTarget();
    value.released = false;
    value.releases = 0;
    value.release = async () => { value.releases++; value.released = true; value.dispatchEvent(new Event('release')); };
    return value;
};
const environment = () => {
    const doc = new EventTarget(), win = new EventTarget(), requests = [];
    doc.visibilityState = 'visible';
    const nav = { wakeLock: { request: kind => { assert.equal(kind, 'screen'); const task = deferred(); requests.push(task); return task.promise; } } };
    const visibility = state => { doc.visibilityState = state; doc.dispatchEvent(new Event('visibilitychange')); };
    return { doc, win, nav, requests, visibility };
};

// A delayed platform response used to outlive the workout cleanup and keep Home awake.
{
    const e = environment(), stop = holdWorkoutScreenAwake(e.nav, e.doc, e.win), lock = sentinel();
    e.visibility('visible'); e.win.dispatchEvent(new Event('pageshow'));
    assert.equal(e.requests.length, 1, 'one platform request may be in flight');
    stop(); e.requests[0].resolve(lock); await flush();
    assert.equal(lock.releases, 1, 'a lock granted after the workout closes must be released');
    e.visibility('visible'); e.win.dispatchEvent(new Event('pageshow'));
    assert.equal(e.requests.length, 1, 'an unmounted workout cannot request a new lock');
}
{
    const e = environment(), stop = holdWorkoutScreenAwake(e.nav, e.doc, e.win), first = sentinel();
    e.requests[0].resolve(first); await flush();
    e.visibility('visible'); assert.equal(e.requests.length, 1, 'an active lock is reused');
    e.visibility('hidden'); assert.equal(first.releases, 1);
    e.visibility('visible'); assert.equal(e.requests.length, 2, 'foreground return reacquires');
    const second = sentinel(); e.requests[1].resolve(second); await flush();
    await second.release(); e.win.dispatchEvent(new Event('pageshow'));
    assert.equal(e.requests.length, 3, 'page restoration recovers a platform-released lock');
    e.requests[2].reject(new Error('Battery saver')); await flush();
    e.visibility('visible'); assert.equal(e.requests.length, 4, 'a later return can retry a denied request');
    stop(); const late = sentinel(); e.requests[3].resolve(late); await flush();
    assert.equal(late.releases, 1);
}
{
    const e = environment(), stop = holdWorkoutScreenAwake(e.nav, e.doc, e.win), lock = sentinel();
    e.visibility('hidden'); e.requests[0].resolve(lock); await flush();
    assert.equal(lock.releases, 1, 'a request granted in the background cannot stay held');
    stop();
}
assert.doesNotThrow(() => holdWorkoutScreenAwake({}, new EventTarget(), new EventTarget())());
console.log('PASS M217 screen-awake lifecycle: one request, background release, foreground/page restoration, denied requests, and release after unmount.');
console.log('PASS exact workout-clock recovery: shared restore instant, paused invariance, running pause accounting, zero reset and legacy fallback.');
