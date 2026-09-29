from pathlib import Path
import json, hashlib

def read(p): return Path(p).read_text()
def write(p,s): Path(p).write_text(s)
def once(s, old, new, label):
    n=s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly 1 occurrence, found {n}')
    return s.replace(old,new,1)

cycles=read('modules/next-engine/cycles.js')
cycles=once(cycles,
    "import { initialPhaseForGoal, phaseLabel } from './phase-policy.js';",
    "import { initialPhaseForGoal, phaseLabel } from './phase-policy.js';\nimport { recoverySignalForDecision } from './recovery.js';",
    'cycles recovery import')
anchor="""export function recommendNextPhase(goal, state, recovery) {
    if (state.phase === 'recovery') {
        if (state.status !== 'review_eligible' || (state.recoveryExitEvidence ?? 0) < 2)
            return undefined;
        return state.recoveryEntryPhase && state.recoveryEntryPhase !== 'recovery'
            ? state.recoveryEntryPhase
            : initialPhaseForGoal(goal);
    }
    if (recovery?.status === 'deload_recommended')
        return 'recovery';
    if (state.status !== 'review_eligible')
        return undefined;
    return nextDevelopmentPhase(goal, state.phase);
}
"""
helper=anchor+"""function causalCycleEvidence(decisions) {
    const signals = (decisions ?? []).map(recoverySignalForDecision);
    const negative = signals.filter(v => v < 0);
    const positive = signals.filter(v => v > 0);
    return {
        negativeCount: negative.length,
        negativeScore: negative.reduce((sum, value) => sum + Math.abs(value), 0),
        positiveCount: positive.length,
        positiveScore: positive.reduce((sum, value) => sum + value, 0)
    };
}
"""
cycles=once(cycles,anchor,helper,'causal cycle helper')
cycles=once(cycles,
    "    const concerning = decisions.filter(d => d.action === 'review' || d.action === 'hold' || d.action === 'decrease_load').length;\n    const positive = decisions.filter(d => d.action === 'increase_load' || d.action === 'add_reps').length;",
    "    const { negativeCount: concerning, negativeScore, positiveCount: positive } = causalCycleEvidence(decisions);",
    'recovery action-label evidence')
cycles=once(cycles,
    "        const favorable = decisions.length > 0 && concerning <= acceptableConcern && (positive > 0 || concerning === 0);",
    "        const favorable = decisions.length > 0 && positive > 0 && concerning <= acceptableConcern && negativeScore < .75;",
    'recovery favorable evidence')
cycles=once(cycles,
    "    const reviews = decisions.filter(d => d.action === 'review').length;\n    const progress = decisions.filter(d => d.action === 'increase_load' || d.action === 'add_reps').length;",
    "    const evidence = causalCycleEvidence(decisions);\n    const concerning = evidence.negativeCount;\n    const negativeScore = evidence.negativeScore;\n    const progress = evidence.positiveCount;",
    'development action-label evidence')
cycles=once(cycles,
    "    else if (workoutsInPhase >= state.minimumWorkouts && reviews >= Math.max(2, Math.ceil(decisions.length * .4))) {\n        status = 'recovery_review';\n        rationale = 'Several exercises need review after sufficient exposure. Evaluate fatigue, exercise fit, and recovery before changing phase or adding work.';\n    }",
    "    else if (workoutsInPhase >= state.minimumWorkouts && concerning >= Math.max(2, Math.ceil(decisions.length * .4)) && negativeScore >= 1.5) {\n        status = 'recovery_review';\n        rationale = 'Several exercises show causal hard-effort, load, rep-floor, or incomplete-session concerns after sufficient exposure. Evaluate fatigue, exercise fit, and recovery before changing phase or adding work.';\n    }",
    'development recovery-review threshold')
write('modules/next-engine/cycles.js',cycles)

config=read('modules/next-engine/config.js')
config=once(config,"export const ENGINE_VERSION = '0.63.0';","export const ENGINE_VERSION = '0.63.1';",'engine version')
write('modules/next-engine/config.js',config)

app=read('modules/App.js')
app=once(app,"const __APP_VERSION__='3.223.0'; const __BUILD__='779';","const __APP_VERSION__='3.224.0'; const __BUILD__='780';",'app version/build')
write('modules/App.js',app)

index=read('index.html')
index=index.replace("build='779'","build='780'")
index=index.replace("build:'779'","build:'780'")
if "build='779'" in index or "build:'779'" in index:
    raise SystemExit('stale build 779 remained in bootstrap diagnostics')
write('index.html',index)

sw=read('sw.js')
sw=once(sw,'/* M173 integration + bootstrap resilience hardening — Engine 0.63.0. */','/* M174 causal cycle-state hardening — Engine 0.63.1. */','sw release comment')
sw=once(sw,'const CACHE="pursuit-iron-production-m173-integration-resilience";','const CACHE="pursuit-iron-production-m174-causal-cycle-state";','sw cache')
write('sw.js',sw)

profile=json.loads(read('BUILD_PROFILE.json'))
profile['milestone']='M174'
profile['source']='M173 + causal cycle-state recovery/review hardening'
profile['engine']='0.63.1 causal recovery evidence through cycle-state transitions'
profile['cache']='pursuit-iron-production-m174-causal-cycle-state'
write('BUILD_PROFILE.json',json.dumps(profile,indent=2)+'\n')

test=r'''import assert from 'node:assert/strict';
import { advanceCycleState, createInitialCycleState, startPhase } from '../modules/next-engine/cycles.js';

const normalRecovery={status:'normal',confidence:'moderate',evidenceCount:0,rationale:'No broad causal fatigue evidence.'};
const neutralReview=()=>({action:'review',reasonCode:'loading_inventory_blocked',role:'primary_strength'});
const neutralHold=()=>({action:'hold',reasonCode:'normal_progression',role:'hypertrophy_compound'});
const hard=()=>({action:'hold',reasonCode:'effort_overshoot',role:'hypertrophy_compound'});
const success=()=>({action:'increase_load',reasonCode:'progression_success',role:'hypertrophy_compound'});

let development=createInitialCycleState('mixed',3);
development={...development,workoutsInPhase:development.minimumWorkouts-1};
const neutralDevelopment=advanceCycleState(development,[neutralReview(),neutralReview(),neutralReview(),neutralReview()],false,normalRecovery,'mixed');
assert.equal(neutralDevelopment.status,'building','neutral review labels must not masquerade as fatigue');
const hardDevelopment=advanceCycleState(development,[hard(),hard(),hard(),hard()],false,normalRecovery,'mixed');
assert.equal(hardDevelopment.status,'recovery_review');

const prior={phase:'mixed_accumulation'};
let recovery=startPhase(prior,'recovery','mixed',3);
recovery=advanceCycleState(recovery,[neutralHold(),neutralReview()],false,normalRecovery,'mixed');
assert.equal(recovery.recoveryExitEvidence,0);
assert.equal(recovery.status,'building');
recovery=advanceCycleState(recovery,[neutralHold(),neutralReview()],false,normalRecovery,'mixed');
assert.equal(recovery.recoveryExitEvidence,0);
assert.notEqual(recovery.status,'review_eligible');

let recovered=startPhase(prior,'recovery','mixed',3);
recovered=advanceCycleState(recovered,[success(),neutralReview()],false,normalRecovery,'mixed');
assert.equal(recovered.recoveryExitEvidence,1);
recovered=advanceCycleState(recovered,[success(),neutralHold()],false,normalRecovery,'mixed');
assert.equal(recovered.recoveryExitEvidence,2);
assert.equal(recovered.status,'review_eligible');
assert.equal(recovered.recommendedNextPhase,'mixed_accumulation');

let setback=startPhase(prior,'recovery','mixed',3);
setback={...setback,recoveryExitEvidence:1};
setback=advanceCycleState(setback,[hard(),neutralHold()],false,normalRecovery,'mixed');
assert.equal(setback.recoveryExitEvidence,0);
assert.notEqual(setback.status,'review_eligible');

console.log('M174 causal cycle-state tests OK: neutral labels stay neutral, causal negatives review, and causal success earns recovery exit.');
'''
write('verification/m174-causal-cycle-state-test.mjs',test)

verify=read('scripts/verify-release.mjs')
anchor="execFileSync(process.execPath,['verification/m173-pwa-resilience-test.mjs'],{stdio:'inherit',cwd:root});"
insert=anchor+"\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m174-causal-cycle-state-test.mjs'],{stdio:'inherit',cwd:root});"
verify=once(verify,anchor,insert,'M174 verification hook')
write('scripts/verify-release.mjs',verify)

changelog=read('CHANGELOG.md')
entry='''## M174 — Causal Cycle-State Hardening (3.224.0 / build 780 / Engine 0.63.1)\n\n- Closes the remaining M170 recovery-causality gap inside `cycles.js`. Generic action labels such as `hold` and `review` no longer count as fatigue by themselves.\n- Development blocks now enter recovery review only from sufficiently broad, weighted negative causes such as effort overshoot, load-too-heavy, rep-floor misses, or incomplete sessions. Loading-inventory limitations remain neutral.\n- Recovery-phase exit now requires repeated positive causal evidence (`progression_success`) after the minimum recovery dose. Neutral holds/reviews neither delay recovery as fake fatigue nor count as exit confirmation.\n- Adds focused regression coverage and reruns the full M173 production suite.\n\n'''
if not changelog.startswith('## M174 — Causal Cycle-State Hardening'):
    changelog=entry+changelog
write('CHANGELOG.md',changelog)

report='''# M174 causal cycle-state hardening\n\nApp **3.224.0**, build **780**, Pursuit Engine **0.63.1**.\n\nM170 made recovery assessment causal, but the cycle-state layer still classified raw `hold`, `review`, and `decrease_load` action labels. M174 removes that last semantic mismatch.\n\n## Behavior\n\n- Neutral action labels no longer become fatigue evidence.\n- Broad causal negative evidence can still trigger recovery review after sufficient exposure.\n- Recovery exit requires two positive causal confirmations after the minimum recovery dose.\n- A neutral-only recovery workout does not count as an exit confirmation.\n- A real negative cause still resets recovery-exit evidence even when its UI action is the generic `hold`.\n\n## Validation\n\nFocused M174 cycle-state tests plus the complete release-integrity suite must pass before publication. Physical Android interaction certification remains separate.\n'''
write('M174_REPORT.md',report)

manifest=json.loads(read('RELEASE_MANIFEST.json'))
manifest['milestone']='M174'
manifest['appVersion']='3.224.0'
manifest['build']=780
manifest['engineVersion']='0.63.1'
manifest['localCandidate']={
    'name':'M174 Causal Cycle-State Hardening',
    'base':'M173 / app 3.223.0 build 779 / Engine 0.63.0',
    'validation':'Focused causal cycle-state regression plus the complete M173 production regression suite.'
}
def sha_file(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
for f in list(manifest.get('runtimeFiles',{})):
    manifest['runtimeFiles'][f]=sha_file(f)
agg=''.join(f"{f}:{manifest['runtimeFiles'][f]}\n" for f in sorted(manifest.get('runtimeFiles',{})))
manifest['runtimeAggregate']=hashlib.sha256(agg.encode()).hexdigest()
for f in list(manifest.get('uiFiles',{})):
    manifest['uiFiles'][f]=sha_file(f)
write('RELEASE_MANIFEST.json',json.dumps(manifest,indent=2)+'\n')
