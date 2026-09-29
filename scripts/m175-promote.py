from pathlib import Path
import json, hashlib

def read(p): return Path(p).read_text()
def write(p,s): Path(p).write_text(s)
def once(s, old, new, label):
    n=s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly 1 occurrence, found {n}')
    return s.replace(old,new,1)

# Shared longitudinal avoidance memory: immutable base settings remain authoritative, while
# user/learned exercise avoidances persist across every adapted block.
history=read('modules/next-engine/workout-history-adapter.js')
old="""function requestAdaptedFromHistory(request, analysis) {
    const replace = new Set(analysis.replaceExerciseIds ?? []);
    let adapted = replace.size ? {
        ...request,
        preferences: { ...request.preferences, avoidedExercises: [...new Set([...(request.preferences?.avoidedExercises ?? []), ...replace])] }
    } : request;
    if (analysis.classification !== 'fatigue_limited' && analysis.recovery.status !== 'deload_recommended')
        return adapted;
    return {
        ...adapted,
        schedule: { days: adapted.schedule.days.map(day => ({
                ...day,
                targetExercises: day.targetExercises === undefined ? undefined : Math.max(2, day.targetExercises - Math.max(1, Math.ceil(day.targetExercises * .2)))
            })) }
    };
}
"""
new="""export function carryForwardAvoidedExercises(baseRequest, currentRequest, analysis) {
    const avoided = [...new Set([
        ...(baseRequest?.preferences?.avoidedExercises ?? []),
        ...(currentRequest?.preferences?.avoidedExercises ?? []),
        ...(analysis?.replaceExerciseIds ?? [])
    ])];
    if (!avoided.length)
        return baseRequest;
    return {
        ...baseRequest,
        preferences: { ...(baseRequest.preferences ?? {}), avoidedExercises: avoided }
    };
}
function requestAdaptedFromHistory(request, currentRequest, analysis) {
    const adapted = carryForwardAvoidedExercises(request, currentRequest, analysis);
    if (analysis.classification !== 'fatigue_limited' && analysis.recovery.status !== 'deload_recommended')
        return adapted;
    return {
        ...adapted,
        schedule: { days: adapted.schedule.days.map(day => ({
                ...day,
                targetExercises: day.targetExercises === undefined ? undefined : Math.max(2, day.targetExercises - Math.max(1, Math.ceil(day.targetExercises * .2)))
            })) }
    };
}
"""
history=once(history,old,new,'shared longitudinal avoidance helper')
history=once(history,
    "    const adaptedRequest = requestAdaptedFromHistory(baseRequest, analysis);",
    "    const adaptedRequest = requestAdaptedFromHistory(baseRequest, snap.request, analysis);",
    'standalone carry-forward call')
write('modules/next-engine/workout-history-adapter.js',history)

cycle=read('modules/next-engine/cycle-runtime-adapter.js')
cycle=once(cycle,
    "import { analyzeShellHistoryForNextEngine } from './workout-history-adapter.js';",
    "import { analyzeShellHistoryForNextEngine, carryForwardAvoidedExercises } from './workout-history-adapter.js';",
    'cycle carry-forward import')
cycle=once(cycle,
    "    const request = clone(base);\n    if (target === 'recovery' || analysis.classification === 'fatigue_limited' || analysis.recovery.status === 'deload_recommended') {",
    "    let request = carryForwardAvoidedExercises(clone(base), current?.nextEngine?.request, analysis);\n    if (target === 'recovery' || analysis.classification === 'fatigue_limited' || analysis.recovery.status === 'deload_recommended') {",
    'cycle request carry-forward')
cycle=once(cycle,
    "        next = transitionProgramPhase(source, normalized, target, { successfulExerciseIds: analysis.successfulExerciseIds, protectedExerciseIds: analysis.protectedExerciseIds }).program;",
    "        next = transitionProgramPhase(source, normalized, target, { successfulExerciseIds: analysis.successfulExerciseIds, protectedExerciseIds: analysis.protectedExerciseIds, replaceExerciseIds: analysis.replaceExerciseIds }).program;",
    'cycle replace evidence')
write('modules/next-engine/cycle-runtime-adapter.js',cycle)

config=read('modules/next-engine/config.js')
config=once(config,"export const ENGINE_VERSION = '0.63.1';","export const ENGINE_VERSION = '0.63.2';",'engine version')
write('modules/next-engine/config.js',config)

app=read('modules/App.js')
app=once(app,"const __APP_VERSION__='3.224.0'; const __BUILD__='780';","const __APP_VERSION__='3.225.0'; const __BUILD__='781';",'app version/build')
write('modules/App.js',app)

index=read('index.html')
index=index.replace("build='780'","build='781'")
index=index.replace("build:'780'","build:'781'")
if "build='780'" in index or "build:'780'" in index:
    raise SystemExit('stale build 780 remained in bootstrap diagnostics')
write('index.html',index)

sw=read('sw.js')
sw=once(sw,'/* M174 causal cycle-state hardening — Engine 0.63.1. */','/* M175 longitudinal adaptation memory — Engine 0.63.2. */','sw release comment')
sw=once(sw,'const CACHE="pursuit-iron-production-m174-causal-cycle-state";','const CACHE="pursuit-iron-production-m175-longitudinal-adaptation-memory";','sw cache')
write('sw.js',sw)

profile=json.loads(read('BUILD_PROFILE.json'))
profile['milestone']='M175'
profile['source']='M174 + persistent poor-fit/user avoidance memory across adapted blocks'
profile['engine']='0.63.2 longitudinal avoidance memory parity for standalone and adaptive cycles'
profile['cache']='pursuit-iron-production-m175-longitudinal-adaptation-memory'
write('BUILD_PROFILE.json',json.dumps(profile,indent=2)+'\n')

test=r'''import assert from 'node:assert/strict';
import fs from 'node:fs';
import { carryForwardAvoidedExercises } from '../modules/next-engine/workout-history-adapter.js';

const base={
  goal:{type:'mixed'},
  preferences:{preferredSplit:'full_body',avoidedExercises:['user-ban']},
  schedule:{days:[{day:'monday',maxMinutes:60}]}
};
const prior={
  ...base,
  preferences:{...base.preferences,avoidedExercises:['user-ban','poor-fit-block-1'],responseCapacityScale:.72}
};
const afterBlock2=carryForwardAvoidedExercises(base,prior,{replaceExerciseIds:['poor-fit-block-2']});
assert.deepEqual(afterBlock2.preferences.avoidedExercises,['user-ban','poor-fit-block-1','poor-fit-block-2']);
assert.equal(afterBlock2.preferences.responseCapacityScale,undefined,'temporary fatigue capacity must not become permanent adaptation memory');
assert.deepEqual(base.preferences.avoidedExercises,['user-ban'],'immutable base request must not be mutated');

// Prove memory survives another block even when the old poor-fit exercise is no longer present and
// therefore cannot be re-diagnosed in the new block.
const afterBlock3=carryForwardAvoidedExercises(base,afterBlock2,{replaceExerciseIds:['poor-fit-block-3']});
assert.deepEqual(afterBlock3.preferences.avoidedExercises,['user-ban','poor-fit-block-1','poor-fit-block-2','poor-fit-block-3']);

const cycle=fs.readFileSync(new URL('../modules/next-engine/cycle-runtime-adapter.js',import.meta.url),'utf8');
assert.ok(cycle.includes("carryForwardAvoidedExercises(clone(base), current?.nextEngine?.request, analysis)"),'adaptive cycle request must inherit accumulated avoidances');
assert.ok(cycle.includes('replaceExerciseIds: analysis.replaceExerciseIds'),'adaptive cycle phase transition must receive explicit poor-fit replacement evidence');
const history=fs.readFileSync(new URL('../modules/next-engine/workout-history-adapter.js',import.meta.url),'utf8');
assert.ok(history.includes('requestAdaptedFromHistory(baseRequest, snap.request, analysis)'),'standalone next-block adaptation must inherit accumulated avoidances');
console.log('M175 longitudinal adaptation memory OK: user bans and poor-fit replacements persist across standalone and adaptive-cycle blocks without persisting temporary fatigue capacity.');
'''
write('verification/m175-longitudinal-adaptation-memory-test.mjs',test)

verify=read('scripts/verify-release.mjs')
anchor="execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m174-causal-cycle-state-test.mjs'],{stdio:'inherit',cwd:root});"
insert=anchor+"\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m175-longitudinal-adaptation-memory-test.mjs'],{stdio:'inherit',cwd:root});"
verify=once(verify,anchor,insert,'M175 verification hook')
write('scripts/verify-release.mjs',verify)

changelog=read('CHANGELOG.md')
entry='''## M175 — Longitudinal Adaptation Memory (3.225.0 / build 781 / Engine 0.63.2)\n\n- Fixes a multi-block memory gap: an exercise diagnosed as poor fit could be avoided for the immediately following block and then become eligible again because later adaptation rebuilt from the original immutable request.\n- Adds one shared carry-forward boundary for exercise avoidances. Original user bans, previously learned poor-fit replacements, and newly diagnosed poor-fit exercises now accumulate across adapted blocks.\n- Applies the same behavior to standalone next-block adaptation and adaptive training-cycle advancement.\n- Adaptive cycles now also pass `replaceExerciseIds` into phase-transition continuity, matching the standalone path.\n- Temporary fatigue capacity reductions remain temporary; only exercise-avoidance memory is carried forward.\n- Adds focused regression coverage and reruns the complete M174 production suite.\n\n'''
if not changelog.startswith('## M175 — Longitudinal Adaptation Memory'):
    changelog=entry+changelog
write('CHANGELOG.md',changelog)

report='''# M175 longitudinal adaptation memory\n\nApp **3.225.0**, build **781**, Pursuit Engine **0.63.2**.\n\nM175 closes a multi-block adaptation-memory gap shared by standalone blocks and adaptive cycles. The immutable base request remains the source of stable program settings, but exercise avoidances now carry forward as longitudinal memory.\n\n## Behavior\n\n- User-banned exercises remain banned across later adapted blocks.\n- Exercises diagnosed as poor fit remain avoided beyond only the next block.\n- Newly diagnosed poor-fit exercises are added cumulatively.\n- Adaptive cycle transitions receive explicit `replaceExerciseIds`, so continuity cannot preserve a diagnosed poor-fit movement.\n- Temporary fatigue-driven capacity scaling is not persisted as permanent memory.\n\n## Validation\n\nFocused M175 persistence/parity tests plus the full production release suite must pass before publication. Physical Android interaction certification remains separate.\n'''
write('M175_REPORT.md',report)

manifest=json.loads(read('RELEASE_MANIFEST.json'))
manifest['milestone']='M175'
manifest['appVersion']='3.225.0'
manifest['build']=781
manifest['engineVersion']='0.63.2'
manifest['localCandidate']={
    'name':'M175 Longitudinal Adaptation Memory',
    'base':'M174 / app 3.224.0 build 780 / Engine 0.63.1',
    'validation':'Focused multi-block avoidance-memory/parity regression plus the complete M174 production suite.'
}
def sha_file(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
for f in list(manifest.get('runtimeFiles',{})):
    manifest['runtimeFiles'][f]=sha_file(f)
agg=''.join(f"{f}:{manifest['runtimeFiles'][f]}\n" for f in sorted(manifest.get('runtimeFiles',{})))
manifest['runtimeAggregate']=hashlib.sha256(agg.encode()).hexdigest()
for f in list(manifest.get('uiFiles',{})):
    manifest['uiFiles'][f]=sha_file(f)
write('RELEASE_MANIFEST.json',json.dumps(manifest,indent=2)+'\n')
