from pathlib import Path
import json, hashlib

def read(p): return Path(p).read_text()
def write(p,s): Path(p).write_text(s)
def once(s, old, new, label):
    n=s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly 1 occurrence, found {n}')
    return s.replace(old,new,1)

app=read('modules/App.js')
app=once(app,"const __APP_VERSION__='3.226.0'; const __BUILD__='782';","const __APP_VERSION__='3.227.0'; const __BUILD__='783';",'app version/build')
app=once(app,'const STORE_VERSION = 12;','const STORE_VERSION = 13;','store version 12 -> 13')

migration='''    // 12 -> 13: completed workout history became editable in M164, so a same-id session can now have
    // two legitimate versions. Give history the same per-record conflict clock used by other mutable
    // syncable lists. Legacy records deliberately start at zero so any real M177+ edit wins.
    13: (d) => {
        if (!Array.isArray(d.history))
            return d;
        const history = d.history.map(x => (x && typeof x === "object" && x.updatedAt == null) ? { ...x, updatedAt: 0 } : x);
        return { ...d, history };
    },
'''
app=once(app,'const STORE_MIGRATIONS = {\n','const STORE_MIGRATIONS = {\n'+migration,'store v13 history migration')

old_comment=''' *   history      union by id. A session is an immutable record of something that happened; two devices
 *                can only ever hold different subsets of the truth, never conflicting versions of it.
 *                Union is not a compromise here — it is the correct answer.'''
new_comment=''' *   history      union by id, newest `updatedAt` wins. Completed sessions were once immutable, but the
 *                correction editor makes load/reps/RIR/date/duration mutable evidence. Pre-M177 records
 *                have clock 0; equal-clock conflicts fall back to the newer store snapshot.'''
app=once(app,old_comment,new_comment,'history merge policy comment')

old_byid='''    const byId = (a = [], b = [], count) => {
        const m = new Map();
        [...(Array.isArray(a) ? a : []), ...(Array.isArray(b) ? b : [])].forEach(r => {
            if (!r || r.id == null)
                return;
            const prev = m.get(r.id);
            if (!prev) {
                m.set(r.id, r);
                return;
            }
            if (prev === r)
                return;
            const pu = prev.updatedAt || 0, ru = r.updatedAt || 0;
            if (ru > pu) {
                m.set(r.id, r);
                stats.conflicts++;
            }
            else if (ru < pu)
                stats.conflicts++;
        });'''
new_byid='''    const byId = (a = [], b = [], count) => {
        const m = new Map(), sourceClock = new Map();
        const add = (rows, storeClock) => (Array.isArray(rows) ? rows : []).forEach(r => {
            if (!r || r.id == null)
                return;
            const prev = m.get(r.id);
            if (!prev) {
                m.set(r.id, r);
                sourceClock.set(r.id, storeClock);
                return;
            }
            if (prev === r)
                return;
            const prevSig = JSON.stringify(prev), nextSig = JSON.stringify(r);
            if (prevSig === nextSig)
                return;
            const pu = prev.updatedAt || 0, ru = r.updatedAt || 0;
            const pc = sourceClock.get(r.id) || 0;
            // Per-record clocks are authoritative. Store recency only breaks legacy/equal-clock ties;
            // the serialised signature is the final stable tie-breaker when both clocks are identical.
            if (ru > pu || (ru === pu && (storeClock > pc || (storeClock === pc && nextSig > prevSig)))) {
                m.set(r.id, r);
                sourceClock.set(r.id, storeClock);
            }
            stats.conflicts++;
        });
        add(a, A.savedAt || 0);
        add(b, B.savedAt || 0);'''
app=once(app,old_byid,new_byid,'deterministic record merge')

old_map='''    const mapMerge = (key) => {
        const oa = older[key] || {}, nb = newer[key] || {};
        return { ...oa, ...nb }; // keys only the older side has survive; shared keys take the newer blob
    };'''
new_map='''    const mapMerge = (key) => {
        const oa = older[key] || {}, nb = newer[key] || {};
        return { ...oa, ...nb }; // keys only the older side has survive; shared keys take the newer blob
    };
    // `perf` is a derived latest-performance mirror. A per-session correction may beat a globally newer
    // store snapshot, so rebuild exercises represented in retained history from their newest session.
    const mergedPerf = mapMerge("perf"), perfSeen = new Set();
    hist.slice().sort((x, y) => (Number(y?.date) || 0) - (Number(x?.date) || 0)).forEach(h => {
        Object.entries(h?.perf || {}).forEach(([exId, p]) => {
            if (!perfSeen.has(exId)) {
                mergedPerf[exId] = p;
                perfSeen.add(exId);
            }
        });
    });'''
app=once(app,old_map,new_map,'perf rebase from merged history')
app=once(app,'        perf: mapMerge("perf"),','        perf: mergedPerf,','merged perf output')

app=once(app,'    const [history, setHistory] = useState([]);','    const [history, setHistoryRaw] = useState([]);','history raw setter')
app=once(app,'    const setCustom = useMemo(() => stampedSetter(setCustomRaw), []);','    const setCustom = useMemo(() => stampedSetter(setCustomRaw), []);\n    const setHistory = useMemo(() => stampedSetter(setHistoryRaw), []);','history stamped setter')
app=once(app,'        setHistory(normalizeHistoryDayIds(d.history || [], runtimeSaved));','        setHistoryRaw(normalizeHistoryDayIds(d.history || [], runtimeSaved));','history hydration must not restamp')
write('modules/App.js',app)

index=read('index.html')
index=index.replace("build='782'","build='783'").replace("build:'782'","build:'783'")
if "build='782'" in index or "build:'782'" in index:
    raise SystemExit('stale build 782 remained in bootstrap diagnostics')
write('index.html',index)

sw=read('sw.js')
sw=once(sw,'/* M176 interrupted-workout restore regression hardening — Engine 0.63.2. */','/* M177 editable-history merge integrity — Engine 0.63.2. */','sw release comment')
sw=once(sw,'const CACHE="pursuit-iron-production-m176-workout-restore-hardening";','const CACHE="pursuit-iron-production-m177-history-merge-integrity";','sw cache')
write('sw.js',sw)

profile=json.loads(read('BUILD_PROFILE.json'))
profile['milestone']='M177'
profile['source']='M176 + editable workout-history conflict clocks, tombstones, deterministic merge, and perf rebase'
profile['cache']='pursuit-iron-production-m177-history-merge-integrity'
write('BUILD_PROFILE.json',json.dumps(profile,indent=2)+'\n')

test=r'''import assert from 'node:assert/strict';
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

const src=fs.readFileSync(new URL('../modules/App.js',import.meta.url),'utf8');
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
'''
write('verification/m177-history-merge-integrity-test.mjs',test)

verify=read('scripts/verify-release.mjs')
verify=once(verify,"  'Correct workout log','normalizeEditedHistoryEntry','perfAfterHistoryReplace',","  'Correct workout log','normalizeEditedHistoryEntry','perfAfterHistoryReplace','const setHistory = useMemo(() => stampedSetter(setHistoryRaw), [])',",'M177 source marker')
anchor="execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m176-workout-restore-hardening-test.mjs'],{stdio:'inherit',cwd:root});"
insert=anchor+"\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m177-history-merge-integrity-test.mjs'],{stdio:'inherit',cwd:root});"
verify=once(verify,anchor,insert,'M177 verification hook')
write('scripts/verify-release.mjs',verify)

changelog=read('CHANGELOG.md')
entry='''## M177 — Editable History Merge Integrity (3.227.0 / build 783 / Engine 0.63.2)\n\n- Treats corrected workout history as mutable evidence during backup/device reconciliation instead of the old immutable-session assumption.\n- Adds per-session `updatedAt` clocks and deletion tombstones to every history mutation while keeping hydration raw so opening the app does not manufacture edits.\n- Extends the existing persistence chain to schema 13 (preserving migrations 8–12) and gives legacy history an intentionally-old conflict clock.\n- Makes equal-clock legacy conflicts deterministic with store recency plus a stable final tie-breaker.\n- Rebases the derived `perf` mirror from the newest retained chronological history so progression/readiness cannot keep stale corrected evidence or let an older edited workout replace a newer session.\n- Adds release-gated regression coverage for both merge orders, legacy ties, delete/edit resurrection, migration preservation, and perf consistency.\n\n'''
if not changelog.startswith('## M177 — Editable History Merge Integrity'):
    changelog=entry+changelog
write('CHANGELOG.md',changelog)

report='''# M177 editable history merge integrity\n\nApp **3.227.0**, build **783**, Pursuit Engine **0.63.2**, store schema **13**.\n\nM164 made completed workout history editable, but the persistence layer still carried the older invariant that a logged session was immutable. History therefore bypassed the shared `updatedAt`/tombstone setter and same-id reconciliation could not reliably distinguish a corrected session from its stale copy. M177 closes that cross-layer gap without replacing the newer store migrations already present in M176.\n\n## What changed\n\n- History mutations now use the same centrally stamped setter as other mutable syncable records.\n- Store hydration uses the raw history setter, preventing boot/load from changing edit clocks.\n- Schema 13 gives legacy history an `updatedAt` baseline of zero while preserving schema migrations 8–12.\n- Merge conflicts prefer per-record `updatedAt`; legacy ties fall back to the newer store snapshot and then a deterministic signature tie-breaker.\n- The `perf` cache is rebuilt from the newest retained chronological session for each exercise, preserving corrected evidence without allowing an older corrected workout to masquerade as the latest workout.\n- History deletions now produce tombstones; undo/re-edit resurrects only when its edit clock is newer than the deletion.\n\n## Validation\n\nFocused M177 tests cover schema migration, prior-migration preservation, two-way merge ordering, legacy ties, deletion-vs-edit ordering, and chronology-safe perf rebasing. The complete production release-integrity suite also runs before publication. Physical installed-PWA behavior remains a separate device certification step.\n'''
write('M177_REPORT.md',report)

manifest=json.loads(read('RELEASE_MANIFEST.json'))
manifest['milestone']='M177'
manifest['appVersion']='3.227.0'
manifest['build']=783
manifest['localCandidate']={
    'name':'M177 Editable History Merge Integrity',
    'base':'M176 / app 3.226.0 build 782 / Engine 0.63.2',
    'validation':'Focused editable-history persistence/merge regression plus the complete M176 production suite.'
}
def sha_file(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
for f in list(manifest.get('runtimeFiles',{})):
    manifest['runtimeFiles'][f]=sha_file(f)
agg=''.join(f"{f}:{manifest['runtimeFiles'][f]}\n" for f in sorted(manifest.get('runtimeFiles',{})))
manifest['runtimeAggregate']=hashlib.sha256(agg.encode()).hexdigest()
for f in list(manifest.get('uiFiles',{})):
    manifest['uiFiles'][f]=sha_file(f)
write('RELEASE_MANIFEST.json',json.dumps(manifest,indent=2)+'\n')
