/**
 * Recovery assessment intentionally requires repeated multi-exercise evidence.
 * A single poor workout can create a watch signal but cannot recommend a deload.
 */
export function assessRecovery(recentWorkouts) {
    const usable = recentWorkouts.filter(x => x.length > 0).slice(-5);
    if (!usable.length)
        return { status: 'normal', confidence: 'low', evidenceCount: 0, rationale: 'Not enough completed workout evidence to assess recovery.' };
    let negativeWorkouts = 0;
    let broadNegativeWorkouts = 0;
    let positiveWorkouts = 0;
    let evidenceCount = 0;
    for (const decisions of usable) {
        const negative = decisions.filter(d => d.action === 'review' || d.action === 'hold' || d.action === 'decrease_load').length;
        const positive = decisions.filter(d => d.action === 'increase_load' || d.action === 'add_reps').length;
        const ratio = negative / Math.max(1, decisions.length);
        if (negative > 0)
            negativeWorkouts++;
        if (ratio >= .5 && negative >= 2)
            broadNegativeWorkouts++;
        if (positive > negative)
            positiveWorkouts++;
        evidenceCount += negative;
    }
    if (usable.length >= 3 && broadNegativeWorkouts >= 3 && positiveWorkouts === 0) {
        return {
            status: 'deload_recommended', confidence: usable.length >= 4 ? 'high' : 'moderate', evidenceCount,
            rationale: 'Repeated broad underperformance/review signals across at least three workouts suggest accumulated fatigue. A short recovery phase is preferable to adding work or changing multiple exercises.'
        };
    }
    if (usable.length >= 2 && (broadNegativeWorkouts >= 2 || negativeWorkouts >= 3)) {
        return {
            status: 'watch', confidence: 'moderate', evidenceCount,
            rationale: 'Recovery is worth watching because negative performance signals have repeated, but the evidence is not broad or persistent enough to justify a deload yet.'
        };
    }
    return {
        status: 'normal', confidence: usable.length >= 3 ? 'moderate' : 'low', evidenceCount,
        rationale: 'Recent performance does not show repeated broad evidence of accumulated fatigue.'
    };
}
