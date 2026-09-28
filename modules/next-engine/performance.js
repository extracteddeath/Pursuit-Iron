import { bestEstimated1RM } from './history.js';
import { loadingRecommendation } from './loading.js';
function byExercise(sets) {
    const map = new Map();
    for (const set of sets) {
        const list = map.get(set.exerciseId) ?? [];
        list.push(set);
        map.set(set.exerciseId, list);
    }
    return map;
}
function representativeLoad(sets) {
    const loads = sets.map(s => s.load).filter((x) => x !== null && Number.isFinite(x));
    return loads.length ? Math.max(...loads) : null;
}
export function evaluateWorkoutProgression(session, performedSets, context = {}) {
    const grouped = byExercise(performedSets);
    return session.exercises.map(ex => {
        const actual = (grouped.get(ex.exerciseId) ?? []).sort((a, b) => a.setIndex - b.setIndex);
        if (!actual.length) {
            return { exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'review', confidence: 'low', reason: 'No completed sets were logged for this prescribed exercise.', currentLoad: null, suggestedLoad: null, estimated1RM: null };
        }
        const currentLoad = representativeLoad(actual);
        const estimated1RM = bestEstimated1RM(actual);
        const completion = actual.length / ex.sets;
        const allAtTop = actual.length >= ex.sets && actual.every(s => s.reps >= ex.prescription.reps[1]);
        const allAtLeastBottom = actual.every(s => s.reps >= ex.prescription.reps[0]);
        const rirReported = actual.filter(s => s.rir !== null);
        const effortInRange = rirReported.length === 0 || rirReported.every(s => s.rir >= ex.prescription.rir[0]);
        const clearOvershoot = rirReported.length > 0 && rirReported.filter(s => s.rir < Math.max(0, ex.prescription.rir[0] - 1)).length >= Math.ceil(rirReported.length / 2);
        const style = ex.progressionStyle ?? 'double';
        if (completion < .75) {
            return { exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'review', confidence: 'moderate', reason: 'Less than 75% of prescribed sets were completed; diagnose time, fatigue, exercise fit, or interruption before progressing.', currentLoad, suggestedLoad: currentLoad, estimated1RM };
        }
        if ((style === 'linear' || style === 'e1rm') && completion >= 1 && allAtLeastBottom && effortInRange && !clearOvershoot) {
            const canAdvance = style === 'linear' || allAtTop || (estimated1RM !== null && actual.every(s => s.reps >= Math.max(ex.prescription.reps[0], ex.prescription.reps[1] - 1)));
            if (canAdvance) {
                const loading = loadingRecommendation(ex.exerciseId, currentLoad, context.loadingInventory, context.equipmentAvailable);
                if (loading.suggestedLoad !== null)
                    return {
                        exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'increase_load', confidence: rirReported.length === actual.length ? 'high' : 'moderate',
                        reason: style === 'linear'
                            ? `Linear progression exposure completed at target effort. Advance from ${loading.label} to ${loading.suggestedLabel}.`
                            : `e1RM autoregulation supports another practical load step while the ${ex.prescription.reps[0]}–${ex.prescription.reps[1]} rep target remains controlled.`,
                        currentLoad, suggestedLoad: loading.suggestedLoad, loadMode: loading.mode, suggestedLoadLabel: loading.suggestedLabel ?? undefined, estimated1RM
                    };
            }
        }
        if (allAtTop && effortInRange) {
            const loading = loadingRecommendation(ex.exerciseId, currentLoad, context.loadingInventory, context.equipmentAvailable);
            const suggestedLoad = loading.suggestedLoad;
            if (suggestedLoad === null) {
                return {
                    exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'review', confidence: rirReported.length === actual.length ? 'high' : 'moderate',
                    reason: `All prescribed sets reached the top of the rep range, but ${loading.rationale.toLowerCase()} Review the loading inventory, rep range, or exercise choice instead of recommending an impossible increase.`,
                    currentLoad, suggestedLoad: null, loadMode: loading.mode, suggestedLoadLabel: undefined, estimated1RM
                };
            }
            return {
                exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'increase_load', confidence: rirReported.length === actual.length ? 'high' : 'moderate',
                reason: suggestedLoad !== null && currentLoad !== null
                    ? `All prescribed sets reached the top of the rep range within target effort. Increase from ${loading.label} to ${loading.suggestedLabel} next exposure.`
                    : 'All prescribed sets reached the top of the rep range without exceeding the target effort. Increase load by the smallest available increment next exposure.',
                currentLoad, suggestedLoad, loadMode: loading.mode, suggestedLoadLabel: loading.suggestedLabel ?? undefined, estimated1RM
            };
        }
        if (clearOvershoot || !allAtLeastBottom) {
            return { exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'hold', confidence: 'moderate', reason: 'Performance or effort fell outside the target range; hold load and collect another comparable exposure before changing the program.', currentLoad, suggestedLoad: currentLoad, estimated1RM };
        }
        const bestReps = Math.max(...actual.map(s => s.reps));
        const suggestedReps = Math.min(ex.prescription.reps[1], bestReps + 1);
        /* Every logged set reached the top, but fewer sets than prescribed: the missing SET is what blocks the load increase.
           The generic message ("work toward 5 reps") was wrong for a lifter who had already done 5. */
        const shortOnSets = actual.length < ex.sets && actual.every(s => s.reps >= ex.prescription.reps[1]);
        const reason = shortOnSets
            ? `Every logged set reached ${ex.prescription.reps[1]} reps, but ${actual.length} of ${ex.sets} prescribed sets were done. Complete all ${ex.sets} at the top of the range to earn the load increase.`
            : style === 'wave'
                ? `Wave-loading exposure is controlled but has not earned a heavier wave yet; add a rep where practical while preserving the planned effort.`
                : style === 'dynamic'
                    ? `Dynamic double progression: advance the lowest-performing sets inside the range toward ${suggestedReps} reps before changing load.`
                    : style === 'ladder'
                        ? `Rep ladder: continue climbing toward ${suggestedReps} reps inside the current rung before adding load.`
                        /* e1RM and linear used to fall through to double progression's wording, so an e1RM week's advice described a
                           different progression from the one named on the lift (measured: 24 of 542 same-prescription suggestions). */
                        : style === 'e1rm'
                            ? `e1RM autoregulation: hold the load and work toward ${suggestedReps} rep${suggestedReps === 1 ? '' : 's'} at the planned effort; the load moves once your estimated max does.`
                            : style === 'linear'
                                ? `Linear progression: complete every prescribed set in the ${ex.prescription.reps[0]}–${ex.prescription.reps[1]} range at the planned effort to add load next session.`
                                : `The exercise is within the prescribed range; keep the load and work toward ${suggestedReps} rep${suggestedReps === 1 ? '' : 's'} where practical before increasing load.`;
        return { exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'add_reps', confidence: 'moderate', reason, currentLoad, suggestedLoad: currentLoad, suggestedReps, estimated1RM };
    });
}
