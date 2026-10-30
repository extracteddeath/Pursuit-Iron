import { bestEstimated1RM, estimate1RM } from './history.js';
import { availableLoadAtOrBelow, formatExerciseLoad, loadingRecommendation, resolveLoadingMode } from './loading.js';
import { progressionExposureContext } from './history-contract.js';
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
function decision(exercise, fields) {
    return { exerciseId: exercise.exerciseId, exerciseName: exercise.name, role: exercise.role, ...fields };
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
// Classic double progression can consolidate a mixed-load exposure into straight sets.
// Estimate capacity at each observed position, including the fatigued later sets, rather
// than borrowing the fresh/best set or treating an arithmetic load average as capacity.
function straightSetCalibration(exercise, actual, context) {
    if ((exercise.progressionStyle ?? 'double') !== 'double' || context.percentageLoading
        || exercise.prescription.setTargets?.length || actual.length !== exercise.sets || actual.length < 2)
        return null;
    // Added load alone cannot establish total-force capacity for bodyweight movements.
    if (resolveLoadingMode(exercise.exerciseId, context.equipmentAvailable, context.loadingInventory) === 'bodyweight')
        return null;
    const loads = actual.map(s => s.load);
    if (!loads.every(w => Number.isFinite(w) && w > 0) || Math.min(...loads) === Math.max(...loads))
        return null;
    const floor = exercise.prescription.reps[0];
    const knownEffort = actual.every(s => Number.isFinite(s.rir));
    // Unknown effort alone must not turn productive in-range work into an automatic reduction.
    if (!knownEffort && actual.every(s => s.reps >= floor))
        return null;
    // Explicitly easy short sets indicate early stopping, not demonstrated load failure.
    if (actual.some(s => s.reps < floor)
        && actual.filter(s => s.reps < floor).every(s => Number.isFinite(s.rir)
            && s.reps + s.rir >= floor + meanTargetRir(exercise)))
        return null;
    const capacities = actual.map(s => estimate1RM(s.load, s.reps, s.rir));
    // High-rep/invalid observations cannot establish an all-set load through this estimator.
    if (capacities.some(value => value === null))
        return null;
    const observedMean = loads.reduce((sum, w) => sum + w, 0) / loads.length;
    const supported = Math.min(...capacities) / (1 + (floor + meanTargetRir(exercise)) / 30);
    const weight = availableLoadAtOrBelow(exercise.exerciseId, Math.min(observedMean, supported),
        context.loadingInventory, context.equipmentAvailable);
    if (!(weight > 0) || weight >= Math.max(...loads))
        return null;
    const label = formatExerciseLoad(exercise.exerciseId, weight, context.equipmentAvailable, context.loadingInventory);
    return { suggestedLoad: weight, suggestedReps: floor, confidence: knownEffort ? 'moderate' : 'low',
        reason: `Use ${label} for all ${exercise.sets} working sets, starting at ${floor} reps. This conservative estimate uses each set's performance, including later sets under fatigue, and rounds down to available equipment. ${knownEffort ? 'Reported reps in reserve are included.' : 'Effort was not reported, so this is a cautious estimate.'} Build all sets through the rep range before increasing load.` };
}
function evaluateProgression(session, performedSets, context = {}) {
    const grouped = byExercise(performedSets);
    return session.exercises.map(ex => {
        const actual = (grouped.get(ex.exerciseId) ?? []).sort((a, b) => a.setIndex - b.setIndex);
        if (!actual.length) {
            return decision(ex, { outcome: 'unobserved', reasonCode: 'no_completed_sets', action: 'review', confidence: 'low', reason: 'No completed sets were logged for this prescribed exercise.', currentLoad: null, suggestedLoad: null, estimated1RM: null });
        }
        const currentLoad = representativeLoad(actual);
        const estimated1RM = bestEstimated1RM(actual);
        // Completing an edited/extraneous/duplicate row is not the same as
        // completing every prescribed set position. This also protects plans
        // after the user skips a middle row but logs a later one.
        const positions = new Set(actual.map(row => row.setIndex));
        const completePositions = positions.size === ex.sets && actual.length === ex.sets
            && Array.from({ length: ex.sets }, (_, i) => i).every(i => positions.has(i));
        const completion = Array.from({ length: ex.sets }, (_, i) => i)
            .filter(i => positions.has(i)).length / Math.max(1, ex.sets);
        const completedPrescription = completePositions;
        const targets = ex.prescription.setTargets;
        const targetFor = s => targets?.[s.setIndex];
        const allAtTop = completedPrescription && actual.every(s => targetFor(s)
            ? s.reps >= targetFor(s).reps && s.load != null && Math.abs(s.load - targetFor(s).weight) < .01
            : s.reps >= ex.prescription.reps[1] && s.load === currentLoad);
        const allAtLeastBottom = actual.every(s => s.reps >= (targetFor(s)?.reps ?? ex.prescription.reps[0]));
        const rirReported = actual.filter(s => s.rir !== null);
        const targetRirFloor = Math.max(0, Number(ex.prescription.rir[0]) || 0);
        const belowTargetRir = rirReported.filter(s => Number(s.rir) < (targetFor(s)?.rir ?? targetRirFloor));
        const effortInRange = rirReported.length === 0 || belowTargetRir.length === 0;
        const effortBelowTarget = belowTargetRir.length > 0;
        const repeatedEffortOvershoot = rirReported.length > 0 && belowTargetRir.length >= Math.ceil(rirReported.length / 2);
        const severeEffortOvershoot = rirReported.length > 0 && rirReported.filter(s => Number(s.rir) < Math.max(0, targetRirFloor - 1)).length >= Math.ceil(rirReported.length / 2);
        const style = ex.progressionStyle ?? 'double';
        const exposure = progressionExposureContext(actual, context);

        // A user edit/substitution changes the question being measured. Never translate that exposure into
        // an automatic load prescription for the original exercise; collect one comparable exposure first.
        if (exposure.nonComparable) {
            return decision(ex, { outcome: 'non_comparable', reasonCode: context.timeLimited ? 'time_limited_session' : 'non_comparable_exposure', action: context.timeLimited ? 'hold' : 'review', confidence: 'high', reason: context.timeLimited ? 'This session was intentionally shortened. Keep the current reference load; the planned cuts do not count as failed sets or a stall.' : 'This exposure included an exercise or prescription edit/substitution, so it is not comparable enough to drive automatic progression. Keep the current reference load until a comparable exposure is logged.', currentLoad, suggestedLoad: currentLoad, estimated1RM });
        }
        // Explicit bad-day/readiness context should prevent one anomalous session from becoming a permanent
        // progression decision. This is intentionally checked before load-failure correction.
        if (exposure.badDay) {
            const detail = exposure.readiness ? ` (${exposure.readiness.replace(/_/g, ' ')})` : '';
            return decision(ex, { outcome: 'context_limited', reasonCode: 'readiness_limited_exposure', action: 'hold', confidence: 'moderate', reason: `This exposure was flagged as readiness/recovery limited${detail}. Hold the prior prescription and use the next comparable exposure before increasing or automatically reducing load.`, currentLoad, suggestedLoad: currentLoad, estimated1RM });
        }
        if (exposure.interrupted && !completedPrescription) {
            return decision(ex, { outcome: 'interrupted', reasonCode: 'interrupted_exposure', action: 'review', confidence: 'high', reason: 'The workout was interrupted before the prescribed sets were completed. Do not interpret missing work as either progression success or a load failure.', currentLoad, suggestedLoad: currentLoad, estimated1RM });
        }
        // Missing work blocks earned progression, but completed rep misses still describe the load.
        // Evaluate those observations before the completion hold, without treating an unlogged set
        // as a failure. Explicit edit/readiness/interruption protections above retain precedence.
        if (!allAtLeastBottom) {
            const correction = belowRangeLoadCorrection(ex, actual, currentLoad, context);
            if (correction) {
                return decision(ex, {
                    outcome: 'failure', reasonCode: 'load_too_heavy', action: 'decrease_load', confidence: correction.confidence,
                    reason: correction.reason, currentLoad, suggestedLoad: correction.suggestedLoad, suggestedReps: correction.suggestedReps,
                    estimated1RM, calibrationEstimated1RM: correction.calibrationE1RM
                });
            }
        }
        // A prior full, in-range exposure cannot certify today's shorter session.
        // Below-range failure recalibration above has its own separate safety policy.
        if (actual.length > ex.sets && allAtLeastBottom) {
            return decision(ex, {
                outcome: 'non_comparable', reasonCode: 'prescription_set_count_changed',
                action: 'hold', confidence: 'high',
                reason: `The prior exposure logged ${actual.length} working sets, while the current prescription calls for ${ex.sets}. Different set counts cannot earn an automatic load change. Keep the prior working load until a comparable session is completed.`,
                currentLoad, suggestedLoad: currentLoad, estimated1RM
            });
        }
        // Missing sets are a hard block on load progression even if every logged set hit the top of the range.
        // Previously 3/4 top-end sets (75% completion) could fall through to the allAtTop branch and advance.
        if (!completedPrescription) {
            const topLogged = actual.every(s => s.reps >= ex.prescription.reps[1]);
            const reason = topLogged
                ? `Every logged set reached ${ex.prescription.reps[1]} reps, but only ${actual.length} of ${ex.sets} prescribed sets were completed. Complete all prescribed sets at the top of the range before increasing load.`
                : `${actual.length} of ${ex.sets} prescribed sets were completed. Finish a comparable full exposure before using it to increase load.`;
            return decision(ex, { outcome: 'incomplete', reasonCode: completion < .75 ? 'incomplete_session' : 'incomplete_prescription', action: completion < .75 ? 'review' : 'hold', confidence: 'moderate', reason, currentLoad, suggestedLoad: currentLoad, estimated1RM });
        }

        const calibration = straightSetCalibration(ex, actual, context);
        if (calibration)
            return decision(ex, { outcome: 'recalibrated', reasonCode: 'straight_set_calibration', action: 'decrease_load',
                ...calibration, currentLoad, estimated1RM });

        // Dynamic double is genuinely set-specific: a completed, comparable session can
        // earn heavier weight on set 1 while set 2 continues building its own reps.
        // The exercise-wide rules above still block interrupted/edited/bad-day work.
        if (style === 'dynamic' && !context.percentageLoading && allAtLeastBottom) {
            const [repFloor, repCeiling] = ex.prescription.reps;
            let advanced = 0, blocked = 0;
            const priorByPosition = new Map(actual.map(set => [set.setIndex, set]));
            const setTargets = Array.from({ length: ex.sets }, (_, index) => {
                const previous = priorByPosition.get(index);
                if (!previous) return { weight: currentLoad, reps: repFloor };
                const plannedRir = targetFor(previous)?.rir ?? targetRirFloor;
                const effortOkay = previous.rir === null || Number(previous.rir) >= plannedRir;
                const earned = previous.load > 0 && previous.reps >= repCeiling && effortOkay;
                const next = earned
                    ? loadingRecommendation(ex.exerciseId, previous.load, context.loadingInventory, context.equipmentAvailable).suggestedLoad
                    : null;
                const increased = Number.isFinite(next) && next > previous.load + 1e-6;
                if (increased) advanced++;
                else if (earned) blocked++;
                return {
                    weight: increased ? next : previous.load,
                    reps: increased ? repFloor : Math.max(repFloor, Math.min(repCeiling, previous.reps + (effortOkay ? 1 : 0))),
                    previousLoad: previous.load, earnedLoadIncrease: increased
                };
            });
            const suggestedLoad = Math.max(...setTargets.map(t => t.weight).filter(Number.isFinite));
            if (advanced) return decision(ex, {
                outcome: 'success', reasonCode: 'per_set_progression_success', action: 'increase_load',
                confidence: rirReported.length === actual.length ? 'high' : 'moderate',
                reason: `Dynamic double progression: ${advanced} of ${ex.sets} completed sets reached ${repCeiling} reps at acceptable effort and earned the next available weight. Other sets keep their own loads and rep targets.`,
                currentLoad, suggestedLoad, setTargets, estimated1RM
            });
            if (blocked === ex.sets) return decision(ex, {
                outcome: 'success_blocked', reasonCode: 'loading_inventory_blocked', action: 'review',
                confidence: 'moderate', reason: 'All sets reached their rep ceiling, but no heavier selectable weight is available. Review the equipment inventory.',
                currentLoad, suggestedLoad: currentLoad, setTargets, estimated1RM
            });
            if (!effortBelowTarget) return decision(ex, {
                outcome: 'productive', reasonCode: 'normal_progression', action: 'add_reps',
                confidence: 'moderate',
                reason: blocked ? 'One set has reached the highest available load; the remaining sets continue progressing independently.'
                    : 'Dynamic double progression: hold each set’s own weight and build its reps independently.',
                currentLoad, suggestedLoad: currentLoad, setTargets,
                suggestedReps: Math.min(repCeiling, Math.max(...actual.map(s => s.reps)) + 1), estimated1RM
            });
        }

        // M183 progression invariant: automatic load increases require a complete top-of-range exposure at
        // acceptable effort. Progression styles may change how work is accumulated, but no style can use a
        // merely in-range or partial exposure as proof that a heavier prescription was earned.
        if (allAtTop && effortInRange) {
            const loading = loadingRecommendation(ex.exerciseId, currentLoad, context.loadingInventory, context.equipmentAvailable);
            const suggestedLoad = loading.suggestedLoad;
            if (suggestedLoad === null) {
                return decision(ex, {
                    outcome: 'success_blocked', reasonCode: 'loading_inventory_blocked', action: 'review', confidence: rirReported.length === actual.length ? 'high' : 'moderate',
                    reason: `All prescribed sets reached the top of the rep range, but ${loading.rationale.toLowerCase()} Review the loading inventory, rep range, or exercise choice instead of recommending an impossible increase.`,
                    currentLoad, suggestedLoad: null, loadMode: loading.mode, suggestedLoadLabel: undefined, estimated1RM
                });
            }
            const styleDetail = style === 'e1rm' ? ' The e1RM progression remains bounded by the phase rep/RIR target.'
                : style === 'linear' ? ' The complete top-range exposure satisfies the linear load-step safety gate.'
                    : style === 'wave' ? ' The heavier wave is earned only after this complete controlled exposure.' : '';
            return decision(ex, {
                outcome: 'success', reasonCode: 'progression_success', action: 'increase_load', confidence: rirReported.length === actual.length ? 'high' : 'moderate',
                reason: suggestedLoad !== null && currentLoad !== null
                    ? `All prescribed sets reached the top of the rep range within target effort. Increase from ${loading.label} to ${loading.suggestedLabel} next exposure.${styleDetail}`
                    : `All prescribed sets reached the top of the rep range without exceeding target effort. Increase load by the smallest available increment next exposure.${styleDetail}`,
                currentLoad, suggestedLoad, loadMode: loading.mode, suggestedLoadLabel: loading.suggestedLabel ?? undefined, estimated1RM
            });
        }
        if (effortBelowTarget) {
            const code = repeatedEffortOvershoot ? 'effort_overshoot' : 'effort_below_target';
            const detail = severeEffortOvershoot
                ? 'Repeated effort was materially harder than prescribed.'
                : 'Reported effort fell below the prescribed RIR floor.';
            return decision(ex, { outcome: 'failure', reasonCode: code, action: 'hold', confidence: repeatedEffortOvershoot ? 'high' : 'moderate', reason: `${detail} Hold the load and re-enter the prescribed effort range before progressing.`, currentLoad, suggestedLoad: currentLoad, estimated1RM });
        }
        if (!allAtLeastBottom) {
            return decision(ex, { outcome: 'failure', reasonCode: 'rep_floor_miss', action: 'hold', confidence: 'moderate', reason: 'Repetitions fell below the prescribed floor without enough evidence for an automatic load correction. Keep each set at its own last weight and retry within the prescribed range; do not apply the heaviest set to every row.', currentLoad, suggestedLoad: currentLoad, estimated1RM });
        }
        const bestReps = Math.max(...actual.map(s => s.reps));
        const suggestedReps = Math.min(ex.prescription.reps[1], bestReps + 1);
        const reason = style === 'wave'
            ? `Wave-loading exposure is controlled but has not earned a heavier wave yet; add a rep where practical while preserving the planned effort.`
            : style === 'dynamic'
                ? `Dynamic double progression: advance the lowest-performing sets inside the range toward ${suggestedReps} reps before changing load.`
                : style === 'ladder'
                    ? `Rep ladder: continue climbing toward ${suggestedReps} reps inside the current rung before adding load.`
                    : style === 'e1rm'
                        ? `e1RM autoregulation: hold the load and work toward ${suggestedReps} rep${suggestedReps === 1 ? '' : 's'} at the planned effort; a heavier load requires a complete top-range exposure.`
                        : style === 'linear'
                            ? `Linear progression: build the complete prescription to the top of the ${ex.prescription.reps[0]}–${ex.prescription.reps[1]} range at planned effort before adding load.`
                            : `Keep the load and build each set toward ${ex.prescription.reps[1]} reps at the planned effort. Next rep targets follow each set’s own last performance.`;
        return decision(ex, { outcome: 'productive', reasonCode: 'normal_progression', action: 'add_reps', confidence: 'moderate', reason, currentLoad, suggestedLoad: currentLoad, suggestedReps, estimated1RM });
    });
}

// Realize the evaluator's decision at each working-set position. Never copy the best set's reps
// to every row, and never estimate extra reps from a load reduction (especially high-rep cables).
export function progressionSetTargets(exercise, actual, result) {
    const [lo, hi] = exercise.prescription.reps;
    const dynamic = exercise.progressionStyle === 'dynamic';
    if (dynamic && Array.isArray(result.setTargets)) return result.setTargets;
    const increasing = result.action === 'increase_load';
    const decreasing = result.action === 'decrease_load';
    // A session's representative load is its maximum, not evidence that every set used it.
    // Holds and rep-only progression retain each observed set's load for every method.
    // Explicit increases/corrections still own their new load, including dynamic progression.
    const retainingLoads = !increasing && !decreasing && result.suggestedLoad === result.currentLoad;
    const priorByPosition = new Map(actual.map(set => [set.setIndex, set]));
    return Array.from({ length: Math.max(1, exercise.sets) }, (_, index) => {
        const previous = priorByPosition.get(index);
        const load = retainingLoads && previous?.load != null
            ? previous.load : result.suggestedLoad ?? result.currentLoad;
        const sameLoad = previous?.load != null && load != null && Math.abs(previous.load - load) < 1e-6;
        const advance = result.action === 'add_reps' ? 1 : 0;
        const reps = !increasing && !decreasing && sameLoad && Number.isFinite(previous?.reps)
            ? Math.max(lo, Math.min(hi, Math.round(previous.reps) + advance)) : lo;
        return { weight: load, reps };
    });
}
export function evaluateWorkoutProgression(session, performedSets, context = {}) {
    const results = evaluateProgression(session, performedSets, context);
    return results.map((result, index) => {
        const exercise = session.exercises[index];
        const actual = performedSets.filter(s => s.exerciseId === exercise.exerciseId).slice().sort((a,b) => a.setIndex-b.setIndex);
        return { ...result, setTargets: progressionSetTargets(exercise, actual, result) };
    });
}
