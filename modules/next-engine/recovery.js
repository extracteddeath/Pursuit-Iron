/**
 * Signed causal recovery evidence from the performance evaluator.
 * Positive = productive evidence, negative = meaningful fatigue/load stress, zero = neutral.
 */
export function recoverySignalForDecision(decision) {
    const strengthMultiplier = decision?.role === 'primary_strength' ? 1.25 : decision?.role === 'secondary_strength' ? 1.1 : 1;
    switch (decision?.reasonCode) {
        case 'progression_success': return 1;
        case 'effort_overshoot': return -1 * strengthMultiplier;
        case 'effort_below_target': return -.55 * strengthMultiplier;
        case 'load_too_heavy': return -.9 * strengthMultiplier;
        case 'rep_floor_miss': return -.65 * strengthMultiplier;
        case 'incomplete_session': return -.3;
        default: return 0;
    }
}

/**
 * Recovery assessment intentionally requires repeated multi-exercise evidence.
 * Normal progression holds, loading-inventory limits, and missing data are not fatigue evidence.
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
        const signals = decisions.map(recoverySignalForDecision);
        const negatives = signals.filter(v => v < 0);
        const negativeScore = negatives.reduce((sum, v) => sum + Math.abs(v), 0);
        const positiveScore = signals.filter(v => v > 0).reduce((sum, v) => sum + v, 0);
        if (negativeScore >= .75) negativeWorkouts++;
        if (negatives.length >= 2 && negativeScore >= 1.5) broadNegativeWorkouts++;
        if (positiveScore > negativeScore) positiveWorkouts++;
        evidenceCount += negatives.length;
    }
    if (usable.length >= 3 && broadNegativeWorkouts >= 3 && positiveWorkouts === 0) {
        return { status: 'deload_recommended', confidence: usable.length >= 4 ? 'high' : 'moderate', evidenceCount,
            rationale: 'Repeated broad hard-effort/load-miss signals across at least three workouts suggest accumulated fatigue. A short recovery phase is preferable to adding work or changing multiple exercises.' };
    }
    if (usable.length >= 2 && (broadNegativeWorkouts >= 2 || negativeWorkouts >= 3)) {
        return { status: 'watch', confidence: 'moderate', evidenceCount,
            rationale: 'Recovery is worth watching because meaningful negative performance causes have repeated, but the evidence is not broad or persistent enough to justify a deload yet.' };
    }
    return { status: 'normal', confidence: usable.length >= 3 ? 'moderate' : 'low', evidenceCount,
        rationale: 'Recent performance does not show repeated broad evidence of accumulated fatigue.' };
}
