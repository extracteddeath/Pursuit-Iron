import { productionSource } from './production-source.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { mergeStores, migrateStore, STORE_VERSION } from '../modules/App.js';

const perf = (weight, reps=8, date=1000) => ({ bench:{ weight, reps, date, sets:[{w:weight,r:reps,rir:2}] } });
const hist = (weight, updatedAt, date=1000) => ({ id:'h1', date, updatedAt, programId:'p1', dayId:'d1', perf:perf(weight,8,date), setsDone:1, volume:weight*8 });
const store = (savedAt, history, perfMap, extra={}) => ({ v:STORE_VERSION, savedAt, history, perf:perfMap, saved:[], cycles:[], custom:[], gyms:[], tombs:{}, ...extra });

assert.equal(STORE_VERSION,13,'M177 must extend the current store schema without replacing migrations 8-12');
const migrated=migrateStore({v:12,savedAt:5,history:[{id:'legacy',date:1}],saved:[],cycles:[],custom:[],gyms:[]});
assert.equal(migrated.v,13);
assert.equal(migrated.history[0].updatedAt,0,'legacy history must receive an intentionally-old edit clock');

// A corrected session wins even when its whole-store snapshot is older for unrelated reasons.
const stale=store(500,[hist(200,10)],perf(200));
const corrected=store(400,[hist(185,20)],perf(185));
for (const [a,b] of [[stale,corrected],[corrected,stale]]) {
  const merged=mergeStores(a,b).data;
  assert.equal(merged.history.length,1);
  assert.equal(merged.history[0].perf.bench.weight,185,'newer per-session edit must beat stale same-id history');
  assert.equal(merged.perf.bench.weight,185,'derived perf mirror must rebase from winning merged history');
}

// Pre-M177 same-id variants have no usable per-record clock. Newer store savedAt is the compatibility fallback.
const legacyOld=store(100,[hist(200,0)],perf(200));
const legacyCorrected=store(200,[hist(190,0)],perf(190));
assert.equal(mergeStores(legacyOld,legacyCorrected).data.history[0].perf.bench.weight,190);
assert.equal(mergeStores(legacyCorrected,legacyOld).data.history[0].perf.bench.weight,190,'merge order must not change an equal-clock conflict');

// A deletion beats an older edit, while a later correction deliberately resurrects the record.
const deleted=store(300,[],{}, {tombs:{h1:30}});
assert.equal(mergeStores(deleted,store(250,[hist(185,20)],perf(185))).data.history.length,0);
assert.equal(mergeStores(deleted,store(250,[hist(180,40)],perf(180))).data.history.length,1);

// Rebuilding perf must follow chronological evidence, not whichever session happened to be edited last.
const recent=hist(205,15,2000); recent.id='h2';
const oldCorrected=hist(180,30,1000);
const mergedChron=mergeStores(store(500,[recent,hist(200,10,1000)],perf(205,8,2000)),store(400,[oldCorrected],perf(180,8,1000))).data;
assert.equal(mergedChron.perf.bench.weight,205,'an older corrected session must not replace a newer workout as the current perf mirror');

const src=productionSource();
for (const marker of [
  'const STORE_VERSION = 13;',
  '13: (d) => {',
  'const [history, setHistoryRaw] = useState([]);',
  'const setHistory = useMemo(() => stampedSetter(setHistoryRaw), []);',
  'setHistoryRaw(normalizeHistoryDayIds(d.history || [], runtimeSaved));',
  'const mergedPerf = mapMerge("perf"), perfSeen = new Set();'
]) assert.ok(src.includes(marker),`missing M177 persistence marker: ${marker}`);
for (const legacyMigration of ['12: (d) =>','11: (d) =>','10: (d) =>','9: (d) =>','8: (d) =>'])
  assert.ok(src.includes(legacyMigration),`M177 must preserve prior migration ${legacyMigration}`);

console.log('M177 history merge integrity OK: schema 13 migration, edit clocks, deterministic conflicts, tombstones, chronology-safe perf rebasing, and prior migrations are release-gated.');
