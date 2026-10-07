/** Session realization: time. Maintained production source. */
import { techniqueExtraSeconds } from '../techniques.js';

function compactRestCompression(exercises) {
    return exercises.map(ex => {
        // Time-constrained programming should first recover clock time from low-cost hypertrophy rest,
        // never from the primary/secondary strength work that depends most on full recovery.
        if (ex.role === 'primary_strength' || ex.role === 'secondary_strength')
            return ex;
        const floor = ex.role === 'hypertrophy_compound' ? 120 : 75;
        if (ex.prescription.restSeconds <= floor)
            return ex;
        return { ...ex, prescription: { ...ex.prescription, restSeconds: floor } };
    });
}

function fitCompactRestToSession(session, exercises) {
    if (session.maxMinutes > 45)
        return null;
    const compressed = compactRestCompression(exercises);
    return estimateSessionMinutes(compressed) <= session.maxMinutes ? compressed : null;
}

function estimateSessionMinutes(exercises) {
    let seconds = 7 * 60;
    for (const ex of exercises) {
        seconds += 45 + ex.sets * 35 + Math.max(0, ex.sets - 1) * ex.prescription.restSeconds + techniqueExtraSeconds(ex.advancedTechnique);
        if (ex.role === 'primary_strength')
            seconds += 6 * 60;
        else if (ex.role === 'secondary_strength' || ex.role === 'hypertrophy_compound')
            seconds += 2 * 60;
    }
    // Low-interference accessory supersets overlap part of the between-set rest. We still charge
    // transition/setup time and 25% of the shorter rest period rather than pretending supersets are free.
    // For genuinely low-interference accessory pairings, ~75% of the shorter between-set rest can be
    // overlapped by alternating movements while still preserving a meaningful recovery window.
    const groups = new Map();
    for (const ex of exercises)
        if (ex.supersetGroup) {
            const list = groups.get(ex.supersetGroup) ?? [];
            list.push(ex);
            groups.set(ex.supersetGroup, list);
        }
    for (const pair of groups.values())
        if (pair.length === 2) {
            const overlap = Math.min(Math.max(0, pair[0].sets - 1) * pair[0].prescription.restSeconds, Math.max(0, pair[1].sets - 1) * pair[1].prescription.restSeconds);
            seconds -= Math.round(overlap * .75);
        }
    return Math.round(seconds / 60);
}

const estimateMinutes = estimateSessionMinutes;

export { compactRestCompression, fitCompactRestToSession, estimateSessionMinutes, estimateMinutes };
