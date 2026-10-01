import assert from 'node:assert/strict';
import { holdWorkoutScreenAwake } from '../modules/mobile-lifecycle.js';

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
