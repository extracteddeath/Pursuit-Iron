/** Pure estimated-1RM PR gate: only a newly completed ordinary working set
 * can beat a recorded historical best. Never run notification effects in React
 * state updaters, which can be replayed. */
export function completedSetPR(row, previousBest, estimateOneRepMax) {
    if (!row || row.done || row.warm || row.sub || row.typeOverride === 'warmup'
        || !Number.isFinite(previousBest) || previousBest <= 0 || typeof estimateOneRepMax !== 'function') return null;
    const weight = Number(row.weight), reps = Number(row.reps);
    if (!Number.isFinite(weight) || weight <= 0 || !Number.isSafeInteger(reps) || reps < 1 || reps > 30) return null;
    const est = estimateOneRepMax(weight, reps);
    return Number.isFinite(est) && est > previousBest + 0.01 ? { w: weight, r: reps, est } : null;
}
