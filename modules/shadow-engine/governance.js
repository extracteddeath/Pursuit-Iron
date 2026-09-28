import { CANARY_BEHAVIORS, CANARY_MANUAL_REVIEW_MIN_PER_ARM } from './canary.js';
export const CANARY_GOVERNANCE_SCHEMA_VERSION = 1;
export const CANARY_GOVERNANCE_CONFIDENCE_Z = 1.96;
export const CANARY_GOVERNANCE_MIN_HIGH_PRECISION_PER_ARM = 50;
const METRICS = [
    { key: 'completion_rate', label: 'Completion rate', direction: 'higher_better', margin: .01 },
    { key: 'duration_accuracy', label: 'Session-time accuracy', direction: 'lower_better', margin: .01 },
    { key: 'target_rep_hit_rate', label: 'Target-rep hit rate', direction: 'higher_better', margin: .01 },
    { key: 'rir_accuracy', label: 'RIR target accuracy', direction: 'lower_better', margin: .05 },
    { key: 'harm_session_rate', label: 'Monitored-harm session rate', direction: 'lower_better', margin: .01 },
];
const round = (v, d = 4) => Math.round(v * 10 ** d) / 10 ** d;
const finite = (v) => Number.isFinite(v);
const mean = (xs) => xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : NaN;
const variance = (xs) => { if (xs.length < 2)
    return 0; const m = mean(xs); return xs.reduce((n, x) => n + (x - m) ** 2, 0) / (xs.length - 1); };
const meanNullable = (xs) => xs.length ? round(mean(xs)) : null;
function sessionMetric(session, key) {
    if (key === 'completion_rate')
        return finite(session.completionRate) ? session.completionRate : null;
    if (key === 'duration_accuracy')
        return finite(session.durationRatio) ? Math.abs(session.durationRatio - 1) : null;
    if (key === 'target_rep_hit_rate')
        return finite(session.targetRepHitRate) ? session.targetRepHitRate : null;
    if (key === 'rir_accuracy')
        return finite(session.meanRirDelta) ? Math.abs(session.meanRirDelta) : null;
    if (key === 'harm_session_rate')
        return session.harmSignals?.length ? 1 : 0;
    return null;
}
function trialMetric(trial, key) {
    const xs = (trial.sessions || []).map(s => sessionMetric(s, key)).filter((x) => finite(x));
    return xs.length ? mean(xs) : null;
}
function armTrials(state, behavior, arm) {
    return (state.trials || []).filter(t => t.behavior === behavior && t.arm === arm && t.status === 'complete');
}
function metricCompare(def, control, treatment) {
    const c = control.map(t => trialMetric(t, def.key)).filter((x) => finite(x));
    const t = treatment.map(t => trialMetric(t, def.key)).filter((x) => finite(x));
    if (!c.length || !t.length)
        return { ...def, controlMean: meanNullable(c), treatmentMean: meanNullable(t), treatmentMinusControl: null, ci95Low: null, ci95High: null, signal: 'uncertain' };
    const delta = mean(t) - mean(c);
    const se = Math.sqrt(variance(t) / Math.max(1, t.length) + variance(c) / Math.max(1, c.length));
    const lo = delta - CANARY_GOVERNANCE_CONFIDENCE_Z * se, hi = delta + CANARY_GOVERNANCE_CONFIDENCE_Z * se;
    let signal = 'uncertain';
    if (def.direction === 'higher_better') {
        if (lo > def.margin)
            signal = 'favorable';
        else if (hi < -def.margin)
            signal = 'adverse';
    }
    else {
        if (hi < -def.margin)
            signal = 'favorable';
        else if (lo > def.margin)
            signal = 'adverse';
    }
    return { ...def, controlMean: round(mean(c)), treatmentMean: round(mean(t)), treatmentMinusControl: round(delta), ci95Low: round(lo), ci95High: round(hi), signal };
}
function confidence(controlN, treatmentN) {
    const n = Math.min(controlN, treatmentN);
    if (n < CANARY_MANUAL_REVIEW_MIN_PER_ARM)
        return 'insufficient';
    if (n < CANARY_GOVERNANCE_MIN_HIGH_PRECISION_PER_ARM)
        return 'moderate';
    return 'higher';
}
export function assessCanaryBehaviorGovernance(state, behavior) {
    const control = armTrials(state, behavior, 'control'), treatment = armTrials(state, behavior, 'treatment');
    const metrics = METRICS.map(def => metricCompare(def, control, treatment));
    const ready = control.length >= CANARY_MANUAL_REVIEW_MIN_PER_ARM && treatment.length >= CANARY_MANUAL_REVIEW_MIN_PER_ARM;
    const favorableMetrics = metrics.filter(m => m.signal === 'favorable').map(m => m.key), adverseMetrics = metrics.filter(m => m.signal === 'adverse').map(m => m.key);
    const harm = metrics.find(m => m.key === 'harm_session_rate');
    const completion = metrics.find(m => m.key === 'completion_rate');
    const safetySignals = [];
    if (ready && harm.signal === 'adverse')
        safetySignals.push('Treatment has a confidently higher monitored-harm session rate.');
    if (ready && harm.treatmentMinusControl != null && harm.treatmentMinusControl >= .05)
        safetySignals.push('Treatment harm-session rate is at least 5 percentage points above control.');
    if (ready && completion.signal === 'adverse' && completion.treatmentMinusControl != null && completion.treatmentMinusControl <= -.03)
        safetySignals.push('Treatment completion rate is materially lower than control.');
    let decision = 'insufficient';
    if (ready) {
        if (safetySignals.length)
            decision = 'safety_blocked';
        else if (adverseMetrics.length >= 2 && favorableMetrics.length <= 1)
            decision = 'evidence_unfavorable';
        else if (favorableMetrics.length >= 3 && adverseMetrics.length === 0)
            decision = 'evidence_favorable';
        else if (favorableMetrics.length === 0 && adverseMetrics.length === 0)
            decision = 'manual_review';
        else
            decision = 'mixed';
    }
    const eligibleForManualPromotionReview = ready && decision === 'evidence_favorable' && safetySignals.length === 0;
    const notes = [];
    if (!ready)
        notes.push(`Needs at least ${CANARY_MANUAL_REVIEW_MIN_PER_ARM} completed randomized program trials in each arm.`);
    else
        notes.push('95% intervals are computed over trial-level outcomes; sessions within one randomized program are not treated as independent trials.');
    if (eligibleForManualPromotionReview)
        notes.push('Evidence may enter a human release-review dossier, but the app cannot promote the behavior.');
    if (decision === 'safety_blocked')
        notes.push('Treatment expansion should pause for this behavior until the safety signal is investigated.');
    return { behavior, controlTrials: control.length, treatmentTrials: treatment.length, controlSessions: control.reduce((n, t) => n + (t.sessions?.length || 0), 0), treatmentSessions: treatment.reduce((n, t) => n + (t.sessions?.length || 0), 0), evidenceReadyForManualReview: ready, evidenceConfidence: confidence(control.length, treatment.length), decision, favorableMetrics, adverseMetrics, safetySignals, eligibleForManualPromotionReview, automaticPromotionAllowed: false, productionPromotionAllowed: false, metrics, notes };
}
export function buildCanaryGovernanceDossier(state, researchEngineVersion = '0.62.3-shadow') {
    const assessments = CANARY_BEHAVIORS.map(b => assessCanaryBehaviorGovernance(state, b));
    return { schemaVersion: 1, generatedAt: Date.now(), policy: 'Controlled canary evidence can qualify a behavior for human release review but can never authorize production promotion from the app. Safety blocks override favorable efficacy signals.', liveEngineVersion: '0.62.0', researchEngineVersion, trialUnit: 'program', confidenceInterval: 'two-sided normal approximation over trial-level means (95%)', automaticPromotionAllowed: false, productionPromotionAllowed: false, summary: { behaviors: assessments.length, reviewReady: assessments.filter(x => x.evidenceReadyForManualReview).length, favorable: assessments.filter(x => x.decision === 'evidence_favorable').length, unfavorable: assessments.filter(x => x.decision === 'evidence_unfavorable').length, safetyBlocked: assessments.filter(x => x.decision === 'safety_blocked').length, insufficient: assessments.filter(x => x.decision === 'insufficient').length, manualPromotionReviewCandidates: assessments.filter(x => x.eligibleForManualPromotionReview).length }, assessments };
}
