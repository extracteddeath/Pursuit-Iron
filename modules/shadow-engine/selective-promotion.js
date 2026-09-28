import { CANARY_BEHAVIORS, runCanaryBehaviorForShell, scoreCanaryWorkout } from './canary.js';
import { SHADOW_ENGINE_VERSION } from './config.js';
export const SELECTIVE_PROMOTION_SCHEMA_VERSION = 1;
export const SELECTIVE_PROMOTION_MIN_PROGRAMS_PER_ARM = 8;
export const SELECTIVE_PROMOTION_MAX_PROGRAMS = 80;
export const SELECTIVE_PROMOTION_MAX_SESSIONS_PER_PROGRAM = 8;
export const SELECTIVE_PROMOTION_ALLOWED_ROLLOUTS = [0, 1, 5, 10, 25, 50, 100];
// M76 ships the bridge with zero enabled promotions. A future reviewed release may change this
// manifest, but the app/canary evidence itself has no API that can mutate it at runtime.
export const M76_SELECTIVE_PROMOTION_MANIFEST = {
    schemaVersion: 1,
    releaseId: 'm82-build706',
    liveFallbackEngineVersion: '0.62.0',
    researchEngineVersion: '0.62.5-shadow',
    entries: []
};
const finite = (v) => Number.isFinite(v);
const mean = (xs) => xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : NaN;
function hash32(input) { let h = 2166136261 >>> 0; for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 16777619);
} h ^= h >>> 16; h = Math.imul(h, 0x85ebca6b); h ^= h >>> 13; h = Math.imul(h, 0xc2b2ae35); h ^= h >>> 16; return h >>> 0; }
function isSha256(value) { return /^[a-f0-9]{64}$/i.test(value || ''); }
export function validateSelectivePromotionManifest(manifest) {
    const errors = [];
    if (!manifest || manifest.schemaVersion !== 1)
        errors.push('Unsupported selective-promotion manifest schema.');
    if (manifest?.liveFallbackEngineVersion !== '0.62.0')
        errors.push('Fallback engine must remain current Engine 0.62.0.');
    const entries = Array.isArray(manifest?.entries) ? manifest.entries : [];
    const active = entries.filter(e => e.status === 'approved' && Number(e.rolloutPercent) > 0);
    if (active.length > 1)
        errors.push('Only one behavior may have a non-zero production rollout in a release.');
    for (const e of entries) {
        if (!CANARY_BEHAVIORS.includes(e.behavior))
            errors.push(`Unsupported behavior ${String(e.behavior)}.`);
        if (!SELECTIVE_PROMOTION_ALLOWED_ROLLOUTS.includes(Number(e.rolloutPercent)))
            errors.push(`Rollout ${e.rolloutPercent}% is not an approved ramp step.`);
        if (e.status === 'approved' && e.rolloutPercent > 0) {
            if (!String(e.approvalId || '').trim())
                errors.push(`${e.behavior} is missing a human approval id.`);
            if (!isSha256(e.dossierSha256))
                errors.push(`${e.behavior} is missing a valid reviewed dossier SHA-256.`);
            if (!finite(e.reviewedAt) || e.reviewedAt <= 0)
                errors.push(`${e.behavior} is missing a review timestamp.`);
        }
    }
    return { ok: errors.length === 0, errors, active: active[0] || null };
}
export function defaultSelectivePromotionRuntimeState(manifest = M76_SELECTIVE_PROMOTION_MANIFEST) {
    return { schemaVersion: 1, releaseId: manifest.releaseId, rollbackLatched: false, rollbackReason: null, rollbackAt: null, programs: [] };
}
export function normalizeSelectivePromotionRuntimeState(value, manifest = M76_SELECTIVE_PROMOTION_MANIFEST) {
    // A rollback is sticky for one release. Only a new releaseId creates a fresh rollout state.
    if (!value || value.schemaVersion !== 1 || value.releaseId !== manifest.releaseId)
        return defaultSelectivePromotionRuntimeState(manifest);
    const programs = Array.isArray(value.programs) ? value.programs.filter((p) => p && typeof p.programId === 'string' && CANARY_BEHAVIORS.includes(p.behavior) && ['fallback', 'promoted'].includes(p.arm)).slice(-SELECTIVE_PROMOTION_MAX_PROGRAMS) : [];
    return { ...defaultSelectivePromotionRuntimeState(manifest), ...value, programs, rollbackLatched: !!value.rollbackLatched, rollbackReason: value.rollbackReason ? String(value.rollbackReason) : null, rollbackAt: finite(value.rollbackAt) ? value.rollbackAt : null };
}
export function selectivePromotionBucket(releaseId, behavior, programId) { return hash32(`${releaseId}|${behavior}|${programId}|selective-rollout`) % 10000; }
export function assignSelectivePromotion(manifest, state, programId) {
    const v = validateSelectivePromotionManifest(manifest);
    if (!v.ok || !v.active || state.rollbackLatched || state.releaseId !== manifest.releaseId)
        return null;
    const e = v.active, bucket = selectivePromotionBucket(manifest.releaseId, e.behavior, programId), promoted = bucket < Math.round(e.rolloutPercent * 100);
    return { releaseId: manifest.releaseId, behavior: e.behavior, arm: promoted ? 'promoted' : 'fallback', rolloutPercent: e.rolloutPercent, bucket, approvalId: e.approvalId, dossierSha256: e.dossierSha256, fallbackEngineVersion: '0.62.0' };
}
export function runSelectivePromotionForShell(options) {
    const manifest = options.manifest || M76_SELECTIVE_PROMOTION_MANIFEST, state = normalizeSelectivePromotionRuntimeState(options.runtimeState, manifest), assignment = assignSelectivePromotion(manifest, state, options.programId);
    if (!assignment)
        return { assignment: null, comparison: null, reason: state.rollbackLatched ? 'rollback_latched' : 'no_active_rollout' };
    if (assignment.arm === 'fallback')
        return { assignment, comparison: null, reason: 'rollout_fallback' };
    const comparison = runCanaryBehaviorForShell({ ...options, behavior: assignment.behavior });
    if (!comparison.accepted || !comparison.experimentalProgram)
        return { assignment: { ...assignment, arm: 'fallback' }, comparison, reason: 'candidate_not_audit_safe_or_better' };
    return { assignment, comparison, experimentalProgram: comparison.experimentalProgram, reason: 'promoted_candidate' };
}
function programMetric(p, key) {
    if (!p.sessions?.length)
        return null;
    if (key === 'harm')
        return mean(p.sessions.map(s => s.harmSignals?.length ? 1 : 0));
    return mean(p.sessions.map(s => s.completionRate).filter(finite));
}
export function assessSelectivePromotionSafety(state) {
    const complete = state.programs.filter(p => p.complete), fallback = complete.filter(p => p.arm === 'fallback'), promoted = complete.filter(p => p.arm === 'promoted');
    const fH = fallback.map(p => programMetric(p, 'harm')).filter(finite), pH = promoted.map(p => programMetric(p, 'harm')).filter(finite), fC = fallback.map(p => programMetric(p, 'completion')).filter(finite), pC = promoted.map(p => programMetric(p, 'completion')).filter(finite);
    const fallbackHarmRate = fH.length ? mean(fH) : null, promotedHarmRate = pH.length ? mean(pH) : null, fallbackCompletionRate = fC.length ? mean(fC) : null, promotedCompletionRate = pC.length ? mean(pC) : null;
    const harmRateDelta = fallbackHarmRate != null && promotedHarmRate != null ? promotedHarmRate - fallbackHarmRate : null, completionRateDelta = fallbackCompletionRate != null && promotedCompletionRate != null ? promotedCompletionRate - fallbackCompletionRate : null;
    const reasons = [];
    // Conservative local emergency stop: repeated absolute harm in promoted programs can stop a rollout
    // before enough local controls accumulate. Comparative stops require both arms to have >=8 programs.
    if (promoted.length >= 4 && promotedHarmRate != null && promotedHarmRate >= .25)
        reasons.push('Promoted programs show an excessive monitored-harm rate.');
    if (promoted.length >= SELECTIVE_PROMOTION_MIN_PROGRAMS_PER_ARM && fallback.length >= SELECTIVE_PROMOTION_MIN_PROGRAMS_PER_ARM) {
        if (harmRateDelta != null && harmRateDelta >= .05)
            reasons.push('Promoted harm rate is at least 5 percentage points above fallback.');
        if (completionRateDelta != null && completionRateDelta <= -.05)
            reasons.push('Promoted completion rate is at least 5 percentage points below fallback.');
    }
    return { rollbackRequired: reasons.length > 0, reasons, fallbackPrograms: fallback.length, promotedPrograms: promoted.length, fallbackHarmRate, promotedHarmRate, harmRateDelta, fallbackCompletionRate, promotedCompletionRate, completionRateDelta };
}
export function recordSelectivePromotionWorkout(state, assignment, programId, log) {
    if (!assignment || assignment.releaseId !== state.releaseId)
        return state;
    const ev = scoreCanaryWorkout(log);
    let found = false;
    let programs = state.programs.map(p => { if (p.programId !== programId)
        return p; found = true; if (p.sessions.some(s => s.historyId && s.historyId === ev.historyId))
        return p; const sessions = [...p.sessions, ev].slice(-SELECTIVE_PROMOTION_MAX_SESSIONS_PER_PROGRAM); return { ...p, sessions, complete: sessions.length >= 4 }; });
    if (!found)
        programs.push({ programId, arm: assignment.arm, behavior: assignment.behavior, sessions: [ev], complete: false });
    programs = programs.slice(-SELECTIVE_PROMOTION_MAX_PROGRAMS);
    const next = { ...state, programs };
    const safety = assessSelectivePromotionSafety(next);
    if (state.rollbackLatched || !safety.rollbackRequired)
        return next;
    return { ...next, rollbackLatched: true, rollbackReason: safety.reasons.join(' '), rollbackAt: Date.now() };
}
export function selectivePromotionStatus(manifest = M76_SELECTIVE_PROMOTION_MANIFEST, state = defaultSelectivePromotionRuntimeState(manifest)) {
    const v = validateSelectivePromotionManifest(manifest), active = v.ok ? v.active : null;
    return { releaseId: manifest.releaseId, valid: v.ok, errors: v.errors, activeBehavior: active?.behavior || null, configuredRolloutPercent: active?.rolloutPercent || 0, effectiveRolloutPercent: state.rollbackLatched ? 0 : (active?.rolloutPercent || 0), rollbackLatched: !!state.rollbackLatched, rollbackReason: state.rollbackReason || null, fallbackEngineVersion: '0.62.0', researchEngineVersion: SHADOW_ENGINE_VERSION, automaticPromotionAllowed: false };
}
