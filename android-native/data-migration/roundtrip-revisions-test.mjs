import assert from 'node:assert/strict';
import { mergeStores, parseStoredData, migrateStore, STORE_VERSION } from '../../modules/engine-api.js';

// ALL fixtures are invented; never read or display a user's backup in CI.
assert.equal(STORE_VERSION, 13);
const history = (id, load, date, updatedAt) => ({
  id, date, updatedAt, programId:'synthetic-program', dayId:'synthetic-day',
  perf:{ bench: {weight:load, reps:8, date, sets:[{w:load,r:8,rir:2}]} },
  setsDone:1, volume:load*8
});
const store = (savedAt, entries, tombs = {}) => ({
  v:STORE_VERSION, savedAt, history:entries, perf:{},
  saved:[], cycles:[], custom:[], gyms:[], tombs
});
const original = store(50, [history('session-a', 200, 1000, 10)]);
const snapshot = JSON.stringify(original);
const round = parseStoredData(snapshot);
assert.equal(round.v, STORE_VERSION);
assert.equal(round.history[0].id, 'session-a');
assert.equal(round.history[0].updatedAt, 10);
assert.equal(round.history[0].perf.bench.weight, 200);
assert.equal(JSON.stringify(original), snapshot, 'read-only parse must not mutate input');

const corrected = store(30, [history('session-a', 185, 1000, 20)]);
const merged = mergeStores(original, corrected).data;
assert.equal(merged.history.length, 1, 'same session ID may never duplicate');
assert.equal(merged.history[0].perf.bench.weight, 185,
  'newer per-session edit takes precedence over older store timestamp');
assert.equal(mergeStores(corrected, original).data.history[0].perf.bench.weight, 185,
  'merge ordering must not roll back corrections');

const deleted = store(60, [], {'session-a':30});
assert.equal(mergeStores(deleted, corrected).data.history.length, 0,
  'a newer tombstone defeats an earlier edit');
const resurrected = store(70, [history('session-a', 190, 1000, 40)]);
assert.equal(mergeStores(deleted, resurrected).data.history[0].perf.bench.weight, 190,
  'a later explicit correction may resurrect a tombstoned session');

const mostRecent = history('session-b', 210, 3000, 15);
const olderCorrection = history('session-a', 180, 1000, 45);
const chronological = mergeStores(store(70,[mostRecent,history('session-a',200,1000,10)]),
                                  store(80,[olderCorrection])).data;
assert.equal(chronological.perf.bench.weight, 210,
  'editing an older workout may not replace the latest chronological performance');
const migrated = migrateStore({v:12,savedAt:5,history:[{id:'old-session',date:1}],
    saved:[],cycles:[],custom:[],gyms:[]});
assert.equal(migrated.v,13);
assert.equal(migrated.history[0].updatedAt,0);
assert.equal(JSON.stringify(original),snapshot,'merge must not mutate source');
console.log('PASS synthetic canonical v13 roundtrip, corrections, tombstones, chronological history and v12 migration');
