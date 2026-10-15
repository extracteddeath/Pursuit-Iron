// Build 880 — Play Store and accounts foundation.
//   * syncStoreOnce: the one merge-then-publish loop a future cloud backend plugs into.
//   * privacy.html: the Play-required policy, reachable from Settings and consistent with the code.
//   * Screen wake lock: a per-device preference, deliberately outside the synced store.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import * as records from '../modules/training-domain/records.js';

const root = new URL('../', import.meta.url);
const read = file => fs.readFileSync(new URL(file, root), 'utf8');
const { mergeStores, STORE_VERSION } = records;
// Soft-bound so a pre-880 control fails on behaviour, not on a missing export.
const syncStoreOnce = records.syncStoreOnce ?? (async () => ({ status: 'missing', data: null }));
const createMemorySyncProvider = records.createMemorySyncProvider ?? (() => ({ pull: async () => null, push: async () => ({ ok: true }), snapshot: () => null, publish() {}, pushes: 0, conflicts: 0 }));

const session = (id, date) => ({ id, programId: 'p1', dayId: 'd1', date, unit: 'lb', updatedAt: date,
    perf: { 'back-squat': { weight: 200, reps: 5, sets: [{ w: 200, r: 5, done: true }] } } });
const program = (id, updatedAt) => ({ id, name: id, updatedAt, days: [], config: {} });
const store = (over) => ({ v: STORE_VERSION, savedAt: 1, tombs: {}, history: [], saved: [], cycles: [], custom: [], gyms: [], ...over });
const ids = rows => (rows || []).map(r => r.id).sort();

// 1. Two devices with divergent edits converge, lose nothing, and a deletion travels.
{
    const remote = createMemorySyncProvider(null);
    let phone = store({ savedAt: 10, history: [session('h-phone', 10)], saved: [program('keep', 1), program('doomed', 1)] });
    let tablet = store({ savedAt: 5, history: [session('h-tablet', 5)], saved: [program('keep', 1), program('doomed', 1)] });
    let r = await syncStoreOnce(phone, remote); phone = r.data;
    assert.equal(r.status, 'synced'); assert.equal(r.pushed, true);
    // Tablet deletes a program (tombstone newer than the record) and syncs.
    tablet = { ...tablet, savedAt: 20, saved: tablet.saved.filter(p => p.id !== 'doomed'), tombs: { doomed: 20 } };
    r = await syncStoreOnce(tablet, remote); tablet = r.data;
    r = await syncStoreOnce(phone, remote); phone = r.data;
    assert.deepEqual(ids(phone.history), ['h-phone', 'h-tablet'], 'no workout lost in either direction');
    assert.deepEqual(ids(phone.saved), ['keep'], 'a deletion on one device removes the program on the other');
    assert.deepEqual(ids(tablet.history), ids(phone.history));
    assert.equal(r.localChanged, true, 'the phone is told its local store changed and must be saved');
    console.log('PASS 1 two devices converge; no workout lost; a tombstone travels.');
}

// 2. Another device publishes between pull and push: the write is refused, re-merged, and both survive.
{
    let raced = false;
    const remote = createMemorySyncProvider(store({ savedAt: 1, history: [session('h-base', 1)] }), {
        beforePush(p) {
            if (raced) return;
            raced = true;
            p.publish(store({ savedAt: 30, history: [session('h-base', 1), session('h-other', 30)] }));
        }
    });
    const r = await syncStoreOnce(store({ savedAt: 40, history: [session('h-mine', 40)] }), remote);
    assert.equal(r.status, 'synced');
    assert.equal(remote.conflicts, 1, 'the stale write was refused once');
    assert.ok(r.attempts >= 2);
    assert.deepEqual(ids(remote.snapshot().history), ['h-base', 'h-mine', 'h-other'], 'the racing device\'s workout survived');
    console.log('PASS 2 a concurrent publish is refused, re-merged and retried without loss.');
}

// 3. Nothing new for the remote: no write.
{
    const base = store({ savedAt: 5, history: [session('h1', 5)] });
    const remote = createMemorySyncProvider(mergeStores(base, base).data);
    const before = remote.pushes;
    const r = await syncStoreOnce(base, remote);
    assert.equal(r.status, 'synced'); assert.equal(r.pushed, false); assert.equal(remote.pushes, before);
    console.log('PASS 3 an unchanged store does not re-publish.');
}

// 4. A provider that always loses the race ends in retry_exhausted, still carrying merged data.
{
    const remote = createMemorySyncProvider(store({ savedAt: 1, history: [session('h-a', 1)] }), {
        beforePush(p) { p.publish(p.snapshot()); }
    });
    const r = await syncStoreOnce(store({ savedAt: 9, history: [session('h-b', 9)] }), remote, { maxAttempts: 3 });
    assert.equal(r.status, 'retry_exhausted');
    assert.deepEqual(ids(r.data.history), ['h-a', 'h-b'], 'exhaustion still returns the safe merged store');
    console.log('PASS 4 retry exhaustion is reported and never discards merged records.');
}

// 5. The contract is enforced: a malformed provider fails loudly.
{
    let threw = false;
    try { await syncStoreOnce(store({}), { pull: async () => null }); } catch { threw = true; }
    assert.ok(threw, 'a provider without push() must be rejected');
    threw = false;
    try { await syncStoreOnce(store({}), { pull: async () => null, push: async () => ({ ok: false }) }); } catch { threw = true; }
    assert.ok(threw, 'a failed push that is not a conflict must not be reported as synced');
    console.log('PASS 5 malformed providers and silent push failures are rejected.');
}

// 6. Privacy policy exists, is linked from Settings, names the real contact and data-reset path.
{
    const policy = read('privacy.html');
    const app = read('modules/App.js');
    assert.match(app, /href: "\.\/privacy\.html"/, 'Settings links the privacy policy');
    assert.match(policy, /pursuitiron@gmail\.com/);
    assert.ok(app.includes('const BUG_EMAIL = "pursuitiron@gmail.com"'), 'policy contact matches the app support address');
    assert.match(policy, /Reset everything/); assert.ok(app.includes('label: "Reset everything"'), 'policy names the real reset control');
    // "No analytics / nothing sent": the only fetch in the UI must be same-origin (the gallery).
    const fetches = [...app.matchAll(/\bfetch\(([^,)]+)/g)].map(m => m[1].trim());
    assert.ok(fetches.length >= 1 && fetches.every(arg => !/https?:/.test(arg)), 'no cross-origin fetch exists: ' + fetches.join(', '));
    assert.doesNotMatch(app, /sendBeacon|google-analytics|gtag\(|firebase/i, 'no telemetry or backend SDK is bundled');
    console.log('PASS 6 privacy policy is linked and every factual claim checked against the code.');
}

// 7. The wake-lock preference is per device and is never written into the synced/exported store.
{
    const app = read('modules/App.js');
    assert.ok(app.includes('const KEEP_AWAKE_KEY = "wpb:keep-awake"'));
    assert.match(app, /useEffect\(\(\) => \(keepAwake \? holdWorkoutScreenAwake\(\) : undefined\), \[keepAwake\]\)/, 'the one wake-lock owner is gated by the preference');
    assert.equal((app.match(/navigator\.wakeLock\.request\(/g) || []).length, 0, 'App.js must not grow a second wake-lock owner');
    assert.ok(app.includes('"aria-label": "Keep screen on during workouts"'), 'Settings exposes the preference');
    for (const line of app.split('\n').filter(l => /saveStore\(\{|const exportData =/.test(l)))
        assert.doesNotMatch(line, /keepAwake/, 'device preference leaked into the synced store');
    console.log('PASS 7 keep-screen-on is a device preference outside the synced store.');
}

// 8. Play kit: asset-links template is valid and the README points at the domain-root location.
{
    const links = JSON.parse(read('play/assetlinks.template.json'));
    assert.equal(links[0].relation[0], 'delegate_permission/common.handle_all_urls');
    assert.match(read('play/README.md'), /extracteddeath\.github\.io\/\.well-known\/assetlinks\.json/);
    console.log('PASS 8 Play release kit is well-formed.');
}
