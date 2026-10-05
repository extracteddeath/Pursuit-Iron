/**
 * Epley-style estimate using reported RIR as reps-in-reserve context. This is a
 * training trend estimate, not a measured 1RM. We intentionally ignore very
 * high-rep sets where the estimate becomes too noisy.
 */
export function estimate1RM(load, reps, rir) {
    if (!Number.isFinite(load) || load <= 0 || !Number.isFinite(reps) || reps <= 0
        || (rir != null && (!Number.isFinite(rir) || rir < 0)))
        return null;
    const repsToFailure = reps + Math.max(0, rir ?? 0);
    if (repsToFailure > 15)
        return null;
    const estimate = Math.round((load * (1 + repsToFailure / 30)) * 10) / 10;
    return Number.isFinite(estimate) ? estimate : null;
}
export function bestEstimated1RM(sets) {
    let best = null;
    for (const set of Array.isArray(sets) ? sets : []) {
        if (!set) continue;
        const estimate = estimate1RM(set.load, set.reps, set.rir);
        if (estimate !== null && (best === null || estimate > best))
            best = estimate;
    }
    return best;
}
