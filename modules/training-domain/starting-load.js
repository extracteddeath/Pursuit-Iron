// Starting estimate for a lift with no history of its own.
//
// Optional first-use heuristic; logged performance remains the progression authority.
// The estimate comes from, in order:
//   1. a related lift the lifter HAS logged (same movement pattern), its best e1RM moved
//      across implements using approximate load ratios;
//   2. bodyweight × a per-pattern ratio for the lifter's experience.
// It is deliberately a little light (STARTING_MARGIN) and always labelled as an estimate:
// actual logged sets establish the real progression.
import { EX_BY_ID } from './catalog.js';
import { e1rmRIR } from './programs.js';
import { historyNumber, convertHistoryLoad, completedHistorySets, observedHistoryRIR, normalizeHistoryRevisions } from '../next-engine/history-contract.js';

const STARTING_MARGIN = 0.9;

// Approximate seed ratios, not strength standards or measured maximums.
const PATTERN_BW_RATIO = {
    'horizontal-push': 1.0, 'incline-push': 0.85, 'vertical-push': 0.65,
    'horizontal-pull': 0.9, 'vertical-pull': 0.9, 'pullover': 0.4,
    'squat': 1.4, 'hip-hinge': 1.7, 'hip-extension': 1.6, 'lunge': 0.7,
    'knee-extension': 0.8, 'knee-flexion': 0.55,
    'elbow-flexion': 0.45, 'elbow-flexion-neutral': 0.45, 'elbow-extension-neutral': 0.45, 'elbow-extension-overhead': 0.4,
    'shoulder-abduction': 0.25, 'shoulder-flexion': 0.25, 'shoulder-horizontal-adduction': 0.5, 'shoulder-horizontal-abduction': 0.25,
    'scapular-upward-rotation': 0.2, 'shrug': 1.3,
    'plantarflexion-straight-knee': 1.3, 'plantarflexion-bent-knee': 1.0,
    'hip-abduction': 0.6, 'hip-adduction': 0.6, 'wrist-flexion': 0.25, 'wrist-extension': 0.15, 'spinal-flexion': 0.4
};
// Patterns close enough to carry strength across (scaled by their bodyweight ratios).
const PATTERN_FAMILY = { 'horizontal-push': 'press', 'incline-push': 'press', 'vertical-push': 'press',
    'horizontal-pull': 'pull', 'vertical-pull': 'pull', 'squat': 'squat', 'lunge': 'squat', 'hip-hinge': 'hinge', 'hip-extension': 'hinge',
    'elbow-flexion': 'curl', 'elbow-flexion-neutral': 'curl', 'elbow-extension-neutral': 'triceps', 'elbow-extension-overhead': 'triceps' };
const EXPERIENCE_SCALE = { beginner: 0.55, novice: 0.75, intermediate: 1, advanced: 1.25, elite: 1.4 };
const SEX_SCALE = { male: 1, female: 0.72 };

let PROFILE = { bodyweight: null, bwUnit: 'kg', sex: null, experience: null };
// The app owns the lifter's bodyweight, sex and experience; it hands them in once per change.
export function setLifterProfile(p) {
    const bodyweight = historyNumber(p?.bodyweight);
    PROFILE = { bodyweight: bodyweight > 0 && bodyweight <= 1500 ? bodyweight : null,
        bwUnit: p?.bwUnit === 'lb' ? 'lb' : 'kg', sex: p?.sex || null, experience: p?.experience || null };
}

// What one logged unit of this exercise carries relative to a barbell. null = not load-estimable
// (bodyweight, assisted, bands).
export function implementFactor(ex) {
    const eq = ex?.equip || [];
    if (/^assisted-/.test(ex?.id || '') || eq.includes('band') || eq.includes('bands')) return null;
    const single = /^(single|one-arm)/.test(ex?.id || '') || /single-arm|one-arm/i.test(ex?.name || '');
    if (eq.includes('dumbbell') || eq.includes('kettlebell')) return 0.42;
    if (eq.some(q => q === 'barbell' || q === 'smith' || q === 'ezbar' || q === 'trapbar')) return 1;
    if (eq.includes('landmine')) return 0.6;
    if (eq.includes('cable')) return single ? 0.36 : 0.65;
    if (eq.some(q => q === 'legpress' || q === 'hacksquat' || q === 'beltsquat')) return 1.5;
    if (eq.some(q => /^machine|pecdeck|legext|legcurl|calfmachine|hipthrustmachine|abduction|adduction|reversehyper|kickback/.test(q))) return single ? 0.5 : 1;
    return null;
}

function bestRecentE1rm(history, id, unit) {
    // Use the latest completed exposure, never an all-time maximum or an obsolete revision.
    for (const h of history.slice().reverse()) {
        let best = 0;
        const p = h?.perf?.[id];
        if (!p) continue;
        for (const s of completedHistorySets(p)) {
            const w = convertHistoryLoad(historyNumber(s.w), h.unit, unit), r = historyNumber(s.r);
            if (!(w > 0) || !(r > 0)) continue;
            const rir = observedHistoryRIR(s);
            const e = e1rmRIR(w, r, Number.isFinite(rir) ? rir : 0);
            if (e > best) best = e;
        }
        if (Number.isFinite(best) && best > 0) return { e1rm: best, at: Number(h.date) };
    }
    return null;
}

// → { e1rm, from: { kind:'own'|'related', id, name, e1rm } | { kind:'bodyweight' } } in `unit`, or null.
export function startingEstimate(exId, { history = [], unit = 'kg', experience = null } = {}) {
    const ex = EX_BY_ID[exId], f = implementFactor(ex);
    if (!ex || !(f > 0) || !ex.pattern) return null;
    const now = Date.now();
    history = normalizeHistoryRevisions(history).entries.filter(entry =>
        ['lb','kg'].includes(String(entry.unit || unit).trim().toLowerCase())
        && Number(entry.date) <= now && now - Number(entry.date) <= 90 * 86400000);
    // Logged before but with no suggestion here (another program, an imported history): start from its own best.
    const own = bestRecentE1rm(history, exId, unit);
    if (own) return { e1rm: own.e1rm, from: { kind: 'own', id: ex.id, name: ex.name, e1rm: Math.round(own.e1rm) } };
    const seen = new Set(), related = [];
    for (const h of history || []) for (const id of Object.keys(h?.perf || {})) {
        if (seen.has(id) || id === exId) continue; seen.add(id);
        const rel = EX_BY_ID[id], rf = implementFactor(rel);
        const samePattern = rel?.pattern === ex.pattern;
        if (!rel || !(rf > 0) || !(samePattern || (PATTERN_FAMILY[rel.pattern] && PATTERN_FAMILY[rel.pattern] === PATTERN_FAMILY[ex.pattern]))) continue;
        const best = bestRecentE1rm(history, id, unit);
        const scale = samePattern ? 1 : (PATTERN_BW_RATIO[ex.pattern] || 1) / (PATTERN_BW_RATIO[rel.pattern] || 1);
        if (best) related.push({ rel, rf, scale, ...best, rank: (samePattern ? 2 : 0) + (rel.equip?.[0] === ex.equip?.[0] ? 1 : 0) });
    }
    related.sort((a, b) => (b.rank - a.rank) || (b.at - a.at));
    if (related[0]) {
        const r = related[0];
        return { e1rm: r.e1rm * r.scale * f / r.rf, from: { kind: 'related', id: r.rel.id, name: r.rel.name, e1rm: Math.round(r.e1rm) } };
    }
    const ratio = PATTERN_BW_RATIO[ex.pattern], bw = PROFILE.bodyweight;
    if (!(ratio > 0) || !(bw > 0)) return null;
    const bwInUnit = convertHistoryLoad(bw, PROFILE.bwUnit, unit);
    const xp = EXPERIENCE_SCALE[experience || PROFILE.experience] ?? EXPERIENCE_SCALE.novice;
    return { e1rm: bwInUnit * ratio * xp * (SEX_SCALE[PROFILE.sex] ?? 1) * f, from: { kind: 'bodyweight' } };
}

// Working load for `reps` at `rir`, before equipment snapping, already on the light side.
export function startingWorkLoad(estimate, reps, rir) {
    if (!(estimate?.e1rm > 0) || !Number.isFinite(estimate.e1rm)) return null;
    const r = Math.max(1, historyNumber(reps) ?? 8), k = Math.max(0, historyNumber(rir) ?? 2);
    return estimate.e1rm * STARTING_MARGIN / (1 + (r + k) / 30);
}

export function startingEstimateText(estimate, unit) {
    if (!estimate?.from) return '';
    if (estimate.from.kind === 'own') return `Estimated from your recent ${estimate.from.name} sets (${estimate.from.e1rm} ${unit} estimated 1RM), with a lighter starting load.`;
    return estimate.from.kind === 'related'
        ? `Estimated from your ${estimate.from.name} (${estimate.from.e1rm} ${unit} estimated 1RM), with a lighter starting load.`
        : 'Approximate starting load from your bodyweight and experience.';
}
