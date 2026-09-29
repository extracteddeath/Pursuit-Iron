import { bestEstimated1RM, estimate1RM } from './history.js';
import { availableLoadAtOrBelow, formatExerciseLoad, loadingRecommendation } from './loading.js';
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
function meanTargetRir(exercise) {
    const low = Number(exercise?.prescription?.rir?.[0]);
    const high = Number(exercise?.prescription?.rir?.[1]);
    if (Number.isFinite(low) && Number.isFinite(high))
        return Math.max(0, (low + high) / 2);
    if (Number.isFinite(low))
        return Math.max(0, low);
    return 2;
}
/**
 * A missed rep floor is not a normal double-progression "hold" when the load itself made the
 * prescribed range impossible. The previous evaluator anchored `currentLoad` to the heaviest logged
 * set, so an accidental ramp such as 200x3, 205x3, 210x2, 215x2 against a 5-8 target became three
 * straight sets at 215 on the next exposure. Recalibrate from the hard/unknown miss evidence instead.
 *
 * Explicit high-RIR short sets are different: the athlete stopped early despite having reps available,
 * so the load is held and they are asked to execute the prescription rather than being auto-deloaded.
 */
function belowRangeLoadCorrection(exercise, actual, currentLoad, context) {
    const repFloor = Number(exercise?.prescription?.reps?.[0]);
    if (!(repFloor > 0) || !(currentLoad > 0))
        return null;
    const misses = actual.filter(set => Number(set.reps) < repFloor);
    if (!misses.length)
        return null;
    const targetRirLow = Math.max(0, Number(exercise?.prescription?.rir?.[0]) || 0);
    const hardOrUnknown = misses.filter(set => set.rir === null || Number(set.rir) < targetRirLow);
    const explicitHard = misses.filter(set => set.rir !== null && Number(set.rir) <= Math.max(1, targetRirLow - 1));
    const maxDeficit = Math.max(...misses.map(set => repFloor - Number(set.reps)));
    const widespreadHardMiss = hardOrUnknown.length >= Math.ceil(misses.length / 2)
        && misses.length >= Math.ceil(actual.length / 2);
    if (!explicitHard.length && !widespreadHardMiss && maxDeficit < 2)
        return null;
    // If every miss was explicitly easy enough to have completed the rep floor, this is execution
    // evidence rather than an excessive-load signal. Keep the load and ask for the prescribed reps.
    if (hardOrUnknown.length === 0)
        return null;
    // Prefer explicitly hard misses as the calibration basis. When effort was not logged, treat the
    // set as failure (estimate1RM's conservative default) and use the strongest such observation.
    const basis = explicitHard.length ? explicitHard : hardOrUnknown;
    const estimates = basis
        .map(set => estimate1RM(set.load, set.reps, set.rir))
        .filter(value => value !== null && Number.isFinite(value));
    if (!estimates.length)
        return null;
    const calibrationE1RM = explicitHard.length ? Math.min(...estimates) : Math.max(...estimates);
    const targetRir = meanTargetRir(exercise);
    const desired = calibrationE1RM / (1 + (repFloor + targetRir) / 30);
    // A correction must actually move down. Snapping to the equipment inventory prevents a theoretical
    // 187.8 lb recommendation when the lifter can only load 185/190, and never rounds the miss upward.
    const ceiling = Math.min(desired, currentLoad - 1e-6);
    const suggestedLoad = availableLoadAtOrBelow(exercise.exerciseId, ceiling, context.loadingInventory, context.equipmentAvailable);
    if (!(suggestedLoad > 0) || suggestedLoad >= currentLoad - 1e-6)
        return null;
    const targetLabel = formatExerciseLoad(exercise.exerciseId, suggestedLoad, context.equipmentAvailable, context.loadingInventory);
    const hardest = explicitHard.length
        ? [...explicitHard].sort((a, b) => (Number(a.rir) - Number(b.rir)) || (Number(b.load) - Number(a.load)))[0]
        : null;
    const effortDetail = hardest
        ? `, including ${hardest.load}×${hardest.reps}${hardest.rir !== null ? ` at ${hardest.rir} RIR` : ''}`
        : '';
    const range = exercise.prescription.reps[0] === exercise.prescription.reps[1]
        ? `${exercise.prescription.reps[0]}`
        : `${exercise.prescription.reps[0]}–${exercise.prescription.reps[1]}`;
    return {
        suggestedLoad,
        suggestedReps: repFloor,
        calibrationE1RM,
        confidence: explicitHard.length ? 'high' : 'moderate',
        reason: `${misses.length}/${actual.length} logged set${actual.length === 1 ? '' : 's'} fell below the ${repFloor}-rep floor${effortDetail}. Holding the heaviest logged weight would repeat an off-target load. Reduce to ${targetLabel} and rebuild from ${repFloor} reps inside the ${range} range at the planned effort.`
    };
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
        if (!allAtLeastBottom) {
            const correction = belowRangeLoadCorrection(ex, actual, currentLoad, context);
            if (correction) {
                return {
                    exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'decrease_load', confidence: correction.confidence,
                    reason: correction.reason, currentLoad, suggestedLoad: correction.suggestedLoad, suggestedReps: correction.suggestedReps,
                    estimated1RM, calibrationEstimated1RM: correction.calibrationE1RM
                };
            }
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
