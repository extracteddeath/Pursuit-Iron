import { shellConfigToNextRequest } from '../next-engine/app-shell-adapter.js';
import { optimizeShadowProgram } from './optimizer.js';
import { SHADOW_ENGINE_VERSION } from './config.js';
import { shadowPriorityMetrics } from './scoring.js';
export const CANARY_SCHEMA_VERSION = 1;
export const CANARY_CONSENT_VERSION = 1;
export const CANARY_MIN_SESSIONS = 4;
export const CANARY_MAX_SESSIONS = 8;
export const CANARY_BEHAVIORS = ['time_reallocation', 'specialization_frequency', 'specialization_order', 'phase_transition'];
export function defaultCanaryResearchState() { return { schemaVersion: 1, enabled: false, consentVersion: 1, consentAt: null, enrollmentId: null, trials: [] }; }
export function normalizeCanaryResearchState(value) {
    const base = defaultCanaryResearchState();
    if (!value || typeof value !== 'object')
        return base;
    const trials = Array.isArray(value.trials) ? value.trials.filter((x) => x && typeof x === 'object' && CANARY_BEHAVIORS.includes(x.behavior) && ['control', 'treatment'].includes(x.arm)).slice(-200) : [];
    return { ...base, ...value, schemaVersion: 1, consentVersion: 1, enabled: !!value.enabled, enrollmentId: typeof value.enrollmentId === 'string' ? value.enrollmentId : null, consentAt: Number.isFinite(value.consentAt) ? value.consentAt : null, trials };
}
function hash32(input) { let h = 2166136261 >>> 0; for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 16777619);
} h ^= h >>> 16; h = Math.imul(h, 0x85ebca6b); h ^= h >>> 13; h = Math.imul(h, 0xc2b2ae35); h ^= h >>> 16; return h >>> 0; }
export function assignCanaryTrial(enrollmentId, programId) {
    const behavior = CANARY_BEHAVIORS[hash32(`${enrollmentId}|${programId}|behavior`) % CANARY_BEHAVIORS.length];
    const arm = (hash32(`${enrollmentId}|${programId}|arm`) & 1) ? 'treatment' : 'control';
    return { behavior, arm, trialId: `canary-${hash32(`${enrollmentId}|${programId}|trial`).toString(36)}` };
}
const TYPE_MAP = {
    time_reallocation: ['time_reallocation'], specialization_frequency: ['specialization_frequency'], specialization_order: ['specialization_order'], phase_transition: ['phase_transition']
};
function weeklyMinutes(program) { return program.sessions.reduce((n, s) => n + s.estimatedMinutes, 0); }
function behaviorComparison(liveProgram, request, behavior, continuityIds = [], previousProgram) {
    const result = optimizeShadowProgram(request, liveProgram, { continuityIds, previousProgram, allowedChangeTypes: TYPE_MAP[behavior] });
    const base = result.baseline, cand = result.candidate;
    const b = shadowPriorityMetrics(base.program, request), c = shadowPriorityMetrics(cand.program, request);
    const delta = cand.score.total - base.score.total;
    return { schemaVersion: 3, shadowEngineVersion: SHADOW_ENGINE_VERSION, liveEngineVersion: liveProgram.engineVersion, evaluatedAt: Date.now(), baselineScore: base.score, shadowScore: cand.score, scoreDelta: Math.round(delta * 1000) / 1000, accepted: delta > .15, candidateCount: result.candidateCount, changes: cand.changes, metrics: { baselineWeeklyMinutes: weeklyMinutes(base.program), shadowWeeklyMinutes: weeklyMinutes(cand.program), baselinePriorityFrequency: b.frequency, shadowPriorityFrequency: c.frequency, baselinePriorityEarlySlots: b.early, shadowPriorityEarlySlots: c.early }, research: { evidenceClass: 'controlled_canary_candidate', productionPromotionAllowed: false, note: `M75 behavior-isolated ${behavior} candidate. Assignment and outcome evidence remain local; no automatic promotion is permitted.` }, experimentalProgram: delta > .15 ? cand.program : undefined };
}
export function runCanaryBehaviorForShell(options) {
    const request = shellConfigToNextRequest(options.config, options.banned ?? [], options.legacyExercises, options.seed ?? options.liveProgram.seed);
    return behaviorComparison(options.liveProgram, request, options.behavior, options.continuityIds ?? [], options.previousProgram);
}
function parseTarget(v) { if (v == null)
    return null; const m = String(v).match(/(\d+)(?:\s*[-–]\s*(\d+))?/); if (!m)
    return null; const a = Number(m[1]), b = Number(m[2] ?? m[1]); return Number.isFinite(a) && Number.isFinite(b) ? [Math.min(a, b), Math.max(a, b)] : null; }
export function scoreCanaryWorkout(log) {
    const total = Math.max(0, Number(log?.totalSets) || 0), done = Math.max(0, Number(log?.setsDone) || 0);
    let prescribed = 0, hit = 0, rirN = 0, rirDelta = 0;
    for (const p of Object.values(log?.perf ?? {})) {
        for (const set of p?.sets ?? []) {
            if (set?.sub)
                continue;
            const target = parseTarget(set?.pt);
            if (target) {
                prescribed++;
                if (Number(set?.r) >= target[0] && Number(set?.r) <= target[1])
                    hit++;
            }
            if (Number.isFinite(set?.rir) && Number.isFinite(set?.tr)) {
                rirN++;
                rirDelta += Number(set.rir) - Number(set.tr);
            }
        }
    }
    const durationRatio = Number(log?.durationMin) > 0 && Number(log?.estMin) > 0 ? Number(log.durationMin) / Number(log.estMin) : null;
    const completionRate = total ? Math.max(0, Math.min(1, done / total)) : 1;
    const harms = [];
    if (completionRate < .8)
        harms.push('low_completion');
    if (durationRatio != null && durationRatio > 1.35)
        harms.push('session_overrun');
    if (prescribed >= 3 && hit / prescribed < .55)
        harms.push('rep_target_miss');
    if (rirN >= 3 && rirDelta / rirN < -.9)
        harms.push('effort_overshoot');
    return { historyId: String(log?.id ?? ''), completedAt: Number(log?.date) || Date.now(), completionRate: Math.round(completionRate * 1000) / 1000, durationRatio: durationRatio == null ? null : Math.round(durationRatio * 1000) / 1000, prescribedSetCount: prescribed, targetRepHitRate: prescribed ? Math.round(hit / prescribed * 1000) / 1000 : null, meanRirDelta: rirN ? Math.round(rirDelta / rirN * 1000) / 1000 : null, harmSignals: harms };
}
export function recordCanaryWorkout(state, programId, log) {
    const now = Date.now();
    const trials = state.trials.map(t => { if (t.programId !== programId || t.status !== 'active')
        return t; const ev = scoreCanaryWorkout(log); if (t.sessions.some(s => s.historyId && s.historyId === ev.historyId))
        return t; const sessions = [...t.sessions, ev].slice(-CANARY_MAX_SESSIONS); const complete = sessions.length >= CANARY_MIN_SESSIONS; return { ...t, sessions, status: complete ? 'complete' : 'active', ...(complete ? { completedAt: now } : {}) }; });
    return { ...state, trials };
}
export const CANARY_MANUAL_REVIEW_MIN_PER_ARM = 25;
const meanFinite = (values) => { const xs = values.filter((v) => Number.isFinite(v)); return xs.length ? Math.round((xs.reduce((a, b) => a + b, 0) / xs.length) * 1000) / 1000 : null; };
function summarizeCanaryArm(trials, arm) {
    const ts = trials.filter(t => t.status === 'complete' && t.arm === arm);
    const sessions = ts.flatMap(t => t.sessions || []);
    return { trials: ts.length, sessions: sessions.length, completionRate: meanFinite(sessions.map(s => s.completionRate)), durationRatio: meanFinite(sessions.map(s => s.durationRatio)), targetRepHitRate: meanFinite(sessions.map(s => s.targetRepHitRate)), meanAbsRirDelta: meanFinite(sessions.map(s => s.meanRirDelta == null ? null : Math.abs(s.meanRirDelta))), harmSessionRate: sessions.length ? Math.round((sessions.filter(s => s.harmSignals.length > 0).length / sessions.length) * 1000) / 1000 : null };
}
export function analyzeCanaryEvidence(state) {
    const trials = state.trials || [];
    return CANARY_BEHAVIORS.map(behavior => {
        const bt = trials.filter(t => t.behavior === behavior);
        const control = summarizeCanaryArm(bt, 'control'), treatment = summarizeCanaryArm(bt, 'treatment');
        const ready = control.trials >= CANARY_MANUAL_REVIEW_MIN_PER_ARM && treatment.trials >= CANARY_MANUAL_REVIEW_MIN_PER_ARM;
        let direction = 'insufficient';
        if (ready) {
            let t = 0, c = 0;
            const higher = (a, b) => { if (a == null || b == null)
                return; if (a > b + .015)
                t++;
            else if (b > a + .015)
                c++; };
            const lower = (a, b) => { if (a == null || b == null)
                return; if (a + .015 < b)
                t++;
            else if (b + .015 < a)
                c++; };
            higher(treatment.completionRate, control.completionRate);
            higher(treatment.targetRepHitRate, control.targetRepHitRate);
            lower(treatment.durationRatio, control.durationRatio);
            lower(treatment.meanAbsRirDelta, control.meanAbsRirDelta);
            lower(treatment.harmSessionRate, control.harmSessionRate);
            direction = t >= 3 && c <= 1 ? 'treatment_favorable' : c >= 3 && t <= 1 ? 'control_favorable' : 'mixed';
        }
        return { behavior, control, treatment, evidenceReadyForManualReview: ready, descriptiveDirection: direction, productionPromotionAllowed: false };
    });
}
export function canarySummary(state) { const trials = state.trials || []; const active = trials.filter(t => t.status === 'active').length, complete = trials.filter(t => t.status === 'complete').length, treatment = trials.filter(t => t.arm === 'treatment').length, control = trials.filter(t => t.arm === 'control').length, harms = trials.reduce((n, t) => n + t.sessions.reduce((m, s) => m + s.harmSignals.length, 0), 0); return { trials: trials.length, active, complete, treatment, control, harms }; }
