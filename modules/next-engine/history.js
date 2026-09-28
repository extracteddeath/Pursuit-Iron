/**
 * Epley-style estimate using reported RIR as reps-in-reserve context. This is a
 * training trend estimate, not a measured 1RM. We intentionally ignore very
 * high-rep sets where the estimate becomes too noisy.
 */
export function estimate1RM(load, reps, rir) {
    if (load === null || !Number.isFinite(load) || load <= 0 || reps <= 0)
        return null;
    const repsToFailure = reps + Math.max(0, rir ?? 0);
    if (repsToFailure > 15)
        return null;
    return Math.round((load * (1 + repsToFailure / 30)) * 10) / 10;
}
export function bestEstimated1RM(sets) {
    let best = null;
    for (const set of sets) {
        const estimate = estimate1RM(set.load, set.reps, set.rir);
        if (estimate !== null && (best === null || estimate > best))
            best = estimate;
    }
    return best;
}
export function buildStrengthTrend(exposures) {
    const points = [];
    for (const exposure of exposures) {
        let best = null;
        for (const set of exposure.sets) {
            const estimated1RM = estimate1RM(set.load, set.reps, set.rir);
            if (estimated1RM !== null && (!best || estimated1RM > best.estimated1RM))
                best = { estimated1RM, set };
        }
        if (best && best.set.load !== null)
            points.push({ completedAt: exposure.completedAt, estimated1RM: best.estimated1RM, load: best.set.load, reps: best.set.reps, rir: best.set.rir });
    }
    return points.sort((a, b) => a.completedAt.localeCompare(b.completedAt));
}
