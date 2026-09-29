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
app=once(app,"const __APP_VERSION__='3.225.0'; const __BUILD__='781';","const __APP_VERSION__='3.226.0'; const __BUILD__='782';",'app version/build')
write('modules/App.js',app)

index=read('index.html')
index=index.replace("build='781'","build='782'")
index=index.replace("build:'781'","build:'782'")
if "build='781'" in index or "build:'781'" in index:
    raise SystemExit('stale build 781 remained in bootstrap diagnostics')
write('index.html',index)

sw=read('sw.js')
sw=once(sw,'/* M175 longitudinal adaptation memory — Engine 0.63.2. */','/* M176 interrupted-workout restore regression hardening — Engine 0.63.2. */','sw release comment')
sw=once(sw,'const CACHE="pursuit-iron-production-m175-longitudinal-adaptation-memory";','const CACHE="pursuit-iron-production-m176-workout-restore-hardening";','sw cache')
write('sw.js',sw)

profile=json.loads(read('BUILD_PROFILE.json'))
profile['milestone']='M176'
profile['source']='M175 + interrupted-workout resume/lifecycle persistence regression hardening'
profile['cache']='pursuit-iron-production-m176-workout-restore-hardening'
write('BUILD_PROFILE.json',json.dumps(profile,indent=2)+'\n')

test=r'''import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES, sessionSnapshotStatus, mergeSessionData } from '../modules/App.js';

const ids=EXERCISES.slice(0,3).map(ex=>ex.id);
assert.equal(ids.length,3);
const [a,b,c]=ids;
const snap={
  schemaVersion:2,programId:'p1',dayId:'d1',weekIndex:2,dayExSig:`${a}|${b}`,
  data:[
    {id:a,slot:0,note:'keep A',sets:[{w:100,r:8,done:true}]},
    {id:b,slot:1,note:'keep B',sets:[{w:50,r:12,done:true}]}
  ]
};
const ctx={programId:'p1',dayId:'d1',weekIndex:2,dayExSig:`${a}|${b}`};
assert.equal(sessionSnapshotStatus(snap,ctx),'resume','same program/day/week/signature must resume exactly');
assert.equal(sessionSnapshotStatus(snap,{...ctx,dayExSig:`${b}|${a}|${c}`}),'partial','program edits must trigger partial resume instead of discarding work');
assert.equal(sessionSnapshotStatus(snap,{...ctx,programId:'other'}),'none');
assert.equal(sessionSnapshotStatus(snap,{...ctx,dayId:'other'}),'none');
assert.equal(sessionSnapshotStatus(snap,{...ctx,weekIndex:3}),'none');
assert.equal(sessionSnapshotStatus({...snap,data:[{id:a,sets:[null]}]},ctx),'none','malformed persisted rows must be rejected instead of crashing resume');

const fresh=[
  {id:b,slot:0,note:'fresh B',sets:[{w:0,r:0}]},
  {id:c,slot:1,note:'fresh C',sets:[{w:0,r:0}]},
  {id:a,slot:2,note:'fresh A',sets:[{w:0,r:0}]}
];
const merged=mergeSessionData(snap.data,fresh);
assert.deepEqual(merged.map(x=>x.id),[b,c,a],'current program owns exercise order');
assert.deepEqual(merged[0].sets,snap.data[1].sets,'logged sets must follow the matching exercise across a reorder');
assert.equal(merged[0].note,'keep B');
assert.deepEqual(merged[1].sets,fresh[1].sets,'new/replacement exercise must use the current prescription');
assert.deepEqual(merged[2].sets,snap.data[0].sets);
assert.equal(merged[2].note,'keep A');

const src=fs.readFileSync(new URL('../modules/App.js',import.meta.url),'utf8');
for(const marker of [
  'schemaVersion: 2, programId: program.id, dayId: day.id, weekIndex, dayExSig',
  'elapsedMs: runElapsedMs()',
  'restEndMs: restEndRef.current',
  'const minimizeWorkout = () => { persistLive();',
  'useLayoutEffect(() => {\n        persistLive();',
  'document.addEventListener("visibilitychange", onVis);',
  'window.addEventListener("pagehide", save);',
  'document.addEventListener("freeze", save);',
  'return () => { document.removeEventListener("visibilitychange", onVis); window.removeEventListener("pagehide", save); document.removeEventListener("freeze", save); };'
]) assert.ok(src.includes(marker),`missing live-session persistence marker: ${marker}`);
assert.ok(src.includes('const startRef = useRef((liveMatch && Number.isFinite(liveMatch.elapsedMs))'),'workout clock must resume from actual training time, not wall time away');
assert.ok(src.includes('if (m.restPaused && m.restRemain > 0)'),'paused rest timer must restore remaining seconds');
assert.ok(src.includes('if (m.restEndMs && m.restEndMs > Date.now())'),'running rest timer must restore by absolute end time');
console.log('M176 workout restore hardening OK: exact/partial resume, malformed-data rejection, merge preservation, timer state, and mobile lifecycle flush hooks are release-gated.');
'''
write('verification/m176-workout-restore-hardening-test.mjs',test)

verify=read('scripts/verify-release.mjs')
anchor="execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m175-longitudinal-adaptation-memory-test.mjs'],{stdio:'inherit',cwd:root});"
insert=anchor+"\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m176-workout-restore-hardening-test.mjs'],{stdio:'inherit',cwd:root});"
verify=once(verify,anchor,insert,'M176 verification hook')
write('scripts/verify-release.mjs',verify)

changelog=read('CHANGELOG.md')
entry='''## M176 — Interrupted Workout Restore Hardening (3.226.0 / build 782 / Engine 0.63.2)\n\n- Audited the live-workout persistence path rather than rewriting it: current code already autosaves the complete session, flushes on mobile/PWA lifecycle events, restores workout/rest timers, and partially merges logged work after a program edit.\n- Adds release-gated tests for exact resume, partial resume, malformed snapshot rejection, exercise-reorder merging, newly added/replaced exercises, and preservation of logged notes/sets.\n- Adds source-level release guards for `visibilitychange`, `pagehide`, Page Lifecycle `freeze`, minimize-before-exit persistence, actual-training-time restoration, and paused/running rest-timer restoration.\n- No workout UI or engine behavior was changed; this milestone prevents the already-correct recovery path from silently regressing.\n\n'''
if not changelog.startswith('## M176 — Interrupted Workout Restore Hardening'):
    changelog=entry+changelog
write('CHANGELOG.md',changelog)

report='''# M176 interrupted workout restore hardening\n\nApp **3.226.0**, build **782**, Pursuit Engine **0.63.2**.\n\nThe current live-session persistence implementation already contains the right mobile/PWA behavior, so M176 protects it rather than replacing it.\n\n## Release-gated invariants\n\n- Same program/day/week resumes the exact saved session.\n- A changed exercise list performs a partial resume and keeps logged work for unchanged exercises.\n- Malformed stored rows are rejected instead of crashing workout restore.\n- The current program owns exercise order/structure while the snapshot owns logged sets and notes.\n- Workout elapsed time resumes from actual training time, not wall time spent away from the app.\n- Paused and running rest timers restore correctly.\n- `visibilitychange`, `pagehide`, and `freeze` all flush the current snapshot before mobile teardown/background kill.\n\nPhysical Android interaction certification is still separate because source/Node tests cannot simulate the OS killing an installed PWA process.\n'''
write('M176_REPORT.md',report)

manifest=json.loads(read('RELEASE_MANIFEST.json'))
manifest['milestone']='M176'
manifest['appVersion']='3.226.0'
manifest['build']=782
manifest['localCandidate']={
    'name':'M176 Interrupted Workout Restore Hardening',
    'base':'M175 / app 3.225.0 build 781 / Engine 0.63.2',
    'validation':'Focused live-session restore/lifecycle regression plus the complete M175 production suite.'
}
def sha_file(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
for f in list(manifest.get('runtimeFiles',{})):
    manifest['runtimeFiles'][f]=sha_file(f)
agg=''.join(f"{f}:{manifest['runtimeFiles'][f]}\n" for f in sorted(manifest.get('runtimeFiles',{})))
manifest['runtimeAggregate']=hashlib.sha256(agg.encode()).hexdigest()
for f in list(manifest.get('uiFiles',{})):
    manifest['uiFiles'][f]=sha_file(f)
write('RELEASE_MANIFEST.json',json.dumps(manifest,indent=2)+'\n')
