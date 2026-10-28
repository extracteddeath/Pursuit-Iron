// One session-only readiness policy. The prescription stays authoritative; this adjusts
// unlogged, app-owned working loads without fabricating a new progression decision.
export const READINESS_CHOICES = Object.freeze([
    Object.freeze({ label: 'Feeling strong', factor: 1, detail: 'Keep the planned loads' }),
    Object.freeze({ label: 'A little off', factor: .95, detail: 'Easier loads where equipment allows' }),
    Object.freeze({ label: 'Run down', factor: .88, detail: 'Lighter loads, focus on good form' })
]);
export const readinessLabel = readiness => readiness?.label && readiness.label !== '—'
    ? readiness.label : 'Not set';

export function adjustWorkoutReadiness(rows, factor, { exerciseFor, unit, snapLoad, ownsSet }) {
    if (!READINESS_CHOICES.some(choice => choice.factor === factor))
        throw new Error('Unsupported readiness level.');
    if (!Array.isArray(rows)) return rows;
    let anyChanged = false;
    const result = rows.map(row => {
        const ex = exerciseFor(row?.id);
        if (!ex || !Array.isArray(row.sets)) return row;
        let rowChanged = false;
        const sets = row.sets.map(set => {
            // Warm-up, technique extension, logged and user-entered sets are never re-prescribed.
            // Live autoregulation wins if this particular set already reacted to observed effort.
            if (!set || set.warm || set.sub || set.done || ownsSet(set)
                || set.liveAdjustment || set.retuneSrc != null || set.autoTuned
                || !(Number(set.weight) > 0)) return set;
            const baseline = Number(set.readinessBaseWeight ?? set.weight);
            if (!(baseline > 0) || !Number.isFinite(baseline)) return set;
            // Canonical equipment snapping: no phantom half-pound weights on coarse stacks.
            const proposed = factor === 1 ? baseline : snapLoad(ex, baseline * factor, unit);
            const weight = Number(proposed);
            if (!(weight > 0) || !Number.isFinite(weight) || weight > baseline + 1e-6) return set;
            const targetWeight = String(Math.round(weight * 1000) / 1000);
            const oldWeight = String(set.weight);
            const unchanged = targetWeight === oldWeight;
            // Clear metadata at baseline so a subsequent edit starts with fresh evidence.
            if (factor === 1 && set.readinessBaseWeight == null) return set;
            if (factor !== 1 && unchanged && Number(set.readinessBaseWeight) === baseline
                && set.readinessFactor === factor) return set;
            const { readinessBaseWeight, readinessFactor, ...plain } = set;
            const next = factor === 1 ? plain : { ...plain, readinessBaseWeight: baseline, readinessFactor: factor };
            rowChanged = true;
            // Retain reps, RIR, protocol and value ownership; only suggested load changes.
            return { ...next, weight: targetWeight, target: set.target
                ? { ...set.target, w: targetWeight } : set.target };
        });
        if (!rowChanged) return row;
        anyChanged = true;
        return { ...row, sets };
    });
    return anyChanged ? result : rows;
}
