/**
 * Program-view last-set reference. The archived workout owns observed loads and
 * the unit used on that day. The mutable perf cache is only a fallback because
 * switching units rounds its bare weights to 0.5-unit increments.
 *
 * Converted values are DISPLAY approximations, never an input to progression,
 * plate math, or saved workout history. Native-unit values stay exact.
 */
import { lastTopSet } from './training-domain.js';
import { convertHistoryLoad, historyNumber, normalizeHistoryRevisions } from './next-engine/history-contract.js';

const normalizedUnit = value => String(value || '').toLowerCase().trim();
const compact = value => String(Math.round(value * 100) / 100);

function isDumbbell(exercise) {
    const details = [exercise?.name, exercise?.id, ...(Array.isArray(exercise?.equip) ? exercise.equip : [])];
    return details.some(value => /dumbbell|\bdb\b/i.test(String(value || '')));
}

function referenceFromSet(set, sourceUnit, targetUnit, exercise) {
    const raw = historyNumber(set?.w);
    if (!(raw > 0)) return null;
    const from = normalizedUnit(sourceUnit) || normalizedUnit(targetUnit);
    const to = normalizedUnit(targetUnit) || from;
    const converted = from !== to && (from === 'kg' || from === 'lb') && (to === 'kg' || to === 'lb');
    const convertedLoad = converted ? convertHistoryLoad(raw, from, to) : raw;
    if (!Number.isFinite(convertedLoad) || convertedLoad <= 0) return null;
    // Converted loads are displayed at a useful training increment, not as
    // false precision. Preserve half-unit dumbbell loads in either unit.
    const step = to === 'lb' ? (isDumbbell(exercise) ? 0.5 : 2.5)
        : (isDumbbell(exercise) ? 0.5 : 1);
    const shown = converted ? Math.round(convertedLoad / step) * step : raw;
    const reps = historyNumber(set?.r);
    return { weight: compact(shown), reps: reps !== null && reps > 0 ? reps : null,
        approximate: converted, unit: to };
}

export function programLastPerformance(history, fallbackPerf, exerciseId, unit, exercise) {
    // Revision-aware, newest first. An edited or deleted session must not leave
    // an obsolete cache value on the Program card.
    const entries = normalizeHistoryRevisions(history).entries;
    for (let i = entries.length - 1; i >= 0; i--) {
        const entry = entries[i];
        const top = lastTopSet(entry?.perf?.[exerciseId]);
        if (!top) continue;
        const observed = referenceFromSet(top, entry.unit || unit, unit, exercise);
        if (observed) return observed;
    }
    const top = lastTopSet(fallbackPerf);
    return top ? referenceFromSet(top, unit, unit, exercise) : null;
}
