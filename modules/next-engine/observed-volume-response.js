// M348: detect a *within-program observational association* following a
// controlled, logged set-count change. This is not a dose-response experiment,
// causal effect estimate or authority to change future volume automatically.
const DAY = 86400000;
const average = numbers => numbers.reduce((a, b) => a + b, 0) / numbers.length;
const near = (a, b, tolerance = .01) => Math.abs(a - b) <= tolerance;
const fail = (reason, extra = {}) => ({
    status: 'insufficient', reason, autoApply: false, ...extra
});

/**
 * Rows are already validated, deduplicated, scoped to one program and
 * restricted to comparable workouts by the canonical history adapter.
 * Never infer an increase from merely flat performance at unchanged dose.
 */
export function evaluateObservedVolumeResponse(rowsInput = []) {
    const days = new Map();
    for (const row of rowsInput) {
        const key = String(row.historyId);
        const list = days.get(key) ?? [];
        list.push(row);
        days.set(key, list);
    }
    const recent = [...days.values()].sort((a, b) => a[0].at - b[0].at
        || String(a[0].historyId).localeCompare(String(b[0].historyId))).slice(-6);
    if (recent.length < 6)
        return fail('need_six_comparable_workouts', { observations: recent.length });

    // Multi-exercise muscle days and shifting rosters cannot isolate a set
    // change from exercise selection. Those require a separate matched panel.
    if (recent.some(group => group.length !== 1))
        return fail('multiple_exercises_or_changed_roster', { observations: recent.length });
    const rows = recent.map(group => group[0]);
    const ids = new Set(rows.map(row => JSON.stringify([
        row.sessionId, row.exerciseId, row.protocolKey
    ])));
    if (ids.size !== 1)
        return fail('changed_exercise_or_prescription', { observations: rows.length });
    if (rows.some(row => row.pain || row.poorTechnique || row.failure ||
        row.effortCoverage < .9 || !(row.score > 0) ||
        !(row.dose > 0) || row.complete !== true || row.effortOvershoot > 0))
        return fail('incomplete_or_unreliable_effort', { observations: rows.length });
    const gaps = rows.slice(1).map((row, i) => (row.at - rows[i].at) / DAY);
    if (gaps.some(gap => gap < 5 || gap > 9))
        return fail('changed_or_irregular_training_frequency', { observations: rows.length });

    const before = rows.slice(0, 3), after = rows.slice(3);
    const beforeDose = average(before.map(row => row.dose));
    const afterDose = average(after.map(row => row.dose));
    if (before.some(row => !near(row.dose, beforeDose)) ||
        after.some(row => !near(row.dose, afterDose)))
        return fail('unstable_dose_windows', { observations: rows.length });
    if (near(beforeDose, afterDose))
        return fail('no_observed_volume_change', { observations: rows.length });
    const doseChange = afterDose / beforeDose - 1;
    if (Math.abs(doseChange) < .15 || Math.abs(doseChange) > .4 ||
        Math.abs(afterDose - beforeDose) < .99)
        return fail('volume_change_outside_comparable_trial_range', { observations: rows.length });

    // A previously improving exercise cannot attribute its continuing
    // improvement to a later increase in sets. Require a genuinely flat
    // baseline, then a stable directional trend in the followup.
    const baselineDrift = before.at(-1).score / before[0].score - 1;
    if (Math.abs(baselineDrift) > .01)
        return fail('baseline_already_changing', { observations: rows.length });
    const followupGain = after.at(-1).score / after[0].score - 1;
    const stableFollowup = after[1].score >= after[0].score * .995
        && after[2].score >= after[1].score * .995;
    const signal = followupGain >= .015 && stableFollowup
        ? 'improvement_associated' : followupGain <= -.015
            ? 'decline_associated' : 'no_clear_benefit';

    return {
        status: 'observed',
        reason: 'single_exercise_stable_frequency_before_after',
        signal,
        direction: doseChange > 0 ? 'higher_volume' : 'lower_volume',
        confidence: 'limited_observational',
        interpretation: 'association_not_causation',
        autoApply: false,
        observations: rows.length,
        before: { weeklySets: beforeDose, sessions: before.length,
            performanceChange: baselineDrift },
        after: { weeklySets: afterDose, sessions: after.length,
            performanceChange: followupGain },
        relativeSetChange: doseChange
    };
}
