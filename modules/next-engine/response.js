import { estimate1RM } from './history.js';
function completionRate(exercise, sets) {
    return sets.length / Math.max(1, exercise.sets);
}
function lowRepOrEffortFailure(exercise, sets) {
    if (!sets.length)
        return false;
    const below = sets.filter(s => s.reps < exercise.prescription.reps[0]).length;
    const rir = sets.filter(s => s.rir !== null);
    const overshot = rir.filter(s => s.rir < Math.max(0, exercise.prescription.rir[0] - 1)).length;
    return below >= Math.ceil(sets.length / 2) || (rir.length > 0 && overshot >= Math.ceil(rir.length / 2));
}
function clearlyUnderloaded(exercise, sets) {
    if (completionRate(exercise, sets) < .9 || !sets.length)
        return false;
    if (!sets.every(s => s.reps >= exercise.prescription.reps[1]))
        return false;
    const rir = sets.filter(s => s.rir !== null);
    if (rir.length < Math.ceil(sets.length / 2))
        return false;
    return rir.filter(s => s.rir > exercise.prescription.rir[1]).length >= Math.ceil(rir.length / 2);
}
function discomfortExposure(sets) {
    return sets.some(s => s.painFlag === true);
}
function poorTechniqueExposure(sets) {
    if (!sets.length)
        return false;
    const poor = sets.filter(s => s.techniqueQuality === 'poor').length;
    return poor >= Math.ceil(sets.length / 2);
}
function questionableTechniqueExposure(sets) {
    if (!sets.length)
        return false;
    const questionable = sets.filter(s => s.techniqueQuality === 'questionable' || s.techniqueQuality === 'poor').length;
    return questionable >= Math.ceil(sets.length / 2);
}
function exposurePerformanceScore(sets) {
    let bestEstimate = null;
    for (const set of sets) {
        const estimated = estimate1RM(set.load, set.reps, set.rir);
        if (estimated !== null && (bestEstimate === null || estimated > bestEstimate))
            bestEstimate = estimated;
    }
    if (bestEstimate !== null)
        return bestEstimate;
    if (!sets.length)
        return null;
    return Math.max(...sets.map(s => s.reps));
}
function averageRir(sets) {
    const values = sets.map(s => s.rir).filter((x) => x !== null);
    return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
}
function makeDiagnosis(exercise, state, confidence, evidenceCount, rationale, action, structural, reason, expectedBenefit, changeCost, blastRadius) {
    return {
        exerciseId: exercise.exerciseId,
        exerciseName: exercise.name,
        state, confidence, evidenceCount, rationale,
        intervention: { action, structural, reason, expectedBenefit, changeCost, blastRadius }
    };
}
/**
 * Diagnose an exercise from comparable completed exposures. The diagnosis is
 * intentionally conservative: structural interventions require repeated
 * evidence, while one-off signals default to collecting more data.
 */
export function diagnoseExerciseResponse(exercise, exposuresInput, recovery) {
    const exposures = [...exposuresInput]
        .filter(x => x.sets.length > 0)
        .sort((a, b) => a.completedAt.localeCompare(b.completedAt))
        .slice(-4);
    if (!exposures.length) {
        return makeDiagnosis(exercise, 'uncertain', 'low', 0, 'No completed comparable exposures are available yet.', 'collect_more_data', false, 'Collect at least one complete exposure before changing the exercise or its dose.', .1, 0, 0);
    }
    const discomfort = exposures.filter(x => discomfortExposure(x.sets)).length;
    const poorTechnique = exposures.filter(x => poorTechniqueExposure(x.sets)).length;
    const questionableTechnique = exposures.filter(x => questionableTechniqueExposure(x.sets)).length;
    const negative = exposures.filter(x => lowRepOrEffortFailure(exercise, x.sets)).length;
    const complete = exposures.filter(x => completionRate(exercise, x.sets) >= .9).length;
    const underloaded = exposures.filter(x => clearlyUnderloaded(exercise, x.sets)).length;
    const scores = exposures.map(x => exposurePerformanceScore(x.sets)).filter((x) => x !== null && Number.isFinite(x));
    const latest = exposures.at(-1);
    if (discomfort >= 2) {
        return makeDiagnosis(exercise, 'poor_fit', discomfort >= 3 ? 'high' : 'moderate', discomfort, `Discomfort was reported in ${discomfort} recent comparable exposures, which is stronger evidence of poor exercise fit than a normal performance fluctuation.`, 'replace_exercise', true, 'Review an intent-preserving replacement instead of adding volume or pushing load through repeated discomfort.', .9, .55, .35);
    }
    if (poorTechnique >= 2 || questionableTechnique >= 3) {
        return makeDiagnosis(exercise, 'technique_limited', poorTechnique >= 3 ? 'high' : 'moderate', Math.max(poorTechnique, questionableTechnique), 'Technique quality has repeatedly degraded, so performance data should not be interpreted as a simple volume or loading problem.', 'review_technique', false, 'Keep program structure stable and address execution, load selection, or exercise setup before changing dose.', .75, .15, .05);
    }
    if (negative >= 2) {
        const systemic = recovery?.status === 'watch' || recovery?.status === 'deload_recommended';
        const reducible = exercise.role !== 'primary_strength' && exercise.role !== 'secondary_strength' && exercise.sets > 2;
        return makeDiagnosis(exercise, 'fatigue_limited', negative >= 3 || systemic ? 'high' : 'moderate', negative, systemic
            ? 'Repeated local underperformance is occurring alongside broader recovery concerns, increasing confidence that fatigue is limiting the exercise.'
            : 'The exercise has missed its prescribed rep/effort target in at least two comparable exposures, which is enough to reduce stress before adding work.', reducible ? 'reduce_set' : 'increase_rest', reducible, reducible
            ? 'Remove one set from this exercise first and preserve the rest of the program.'
            : 'Preserve the strength exposure and increase recovery/rest before considering a structural reduction.', .82, reducible ? .25 : .12, reducible ? .18 : .05);
    }
    if (underloaded >= 1 && clearlyUnderloaded(exercise, latest.sets)) {
        return makeDiagnosis(exercise, 'underloaded', underloaded >= 2 ? 'high' : 'moderate', underloaded, 'The prescribed top-end reps were completed with more repetitions in reserve than the target, indicating that load—not extra volume—is the first variable to change.', 'increase_load', false, 'Increase load by the smallest practical increment and keep the program structure unchanged.', .9, .08, .02);
    }
    if (scores.length >= 2) {
        const first = scores[0];
        const last = scores.at(-1);
        const relative = first > 0 ? (last - first) / first : 0;
        const positiveActions = exposures.filter(x => x.progression?.action === 'increase_load').length;
        if (relative >= .015 || positiveActions >= 2) {
            return makeDiagnosis(exercise, 'progressing', relative >= .03 || positiveActions >= 3 ? 'high' : 'moderate', exposures.length, 'Comparable performance is improving while the exercise remains executable, so changing the exercise or adding sets would create unnecessary churn.', 'maintain', false, 'Maintain the exercise and let load/rep progression continue.', 1, 0, 0);
        }
    }
    if (exposures.length >= 3 && complete >= 3 && discomfort === 0 && questionableTechnique === 0 && negative === 0) {
        const first = scores[0] ?? null;
        const last = scores.at(-1) ?? null;
        const relative = first !== null && last !== null && first > 0 ? Math.abs(last - first) / first : null;
        const rirs = exposures.map(x => averageRir(x.sets)).filter((x) => x !== null);
        const targetRir = rirs.length ? rirs.every(v => v >= exercise.prescription.rir[0] && v <= exercise.prescription.rir[1] + 1) : true;
        if ((relative === null || relative < .01) && targetRir) {
            const eligible = exercise.role !== 'primary_strength' && exercise.role !== 'secondary_strength' && exercise.sets < 4 && recovery?.status !== 'watch' && recovery?.status !== 'deload_recommended';
            return makeDiagnosis(exercise, 'possibly_understimulated', 'moderate', exposures.length, 'At least three complete exposures are stable, well tolerated, and close to the intended effort without a meaningful performance trend. This is only suggestive—not proof—of insufficient dose.', eligible ? 'add_set' : 'collect_more_data', eligible, eligible
                ? 'If the program audit remains clean, add only one set to this exercise and evaluate the response before making another change.'
                : 'Keep collecting evidence; do not add volume while recovery is questionable or the exercise already has substantial per-session dose.', eligible ? .55 : .2, eligible ? .35 : 0, eligible ? .18 : 0);
        }
    }
    return makeDiagnosis(exercise, 'uncertain', exposures.length >= 3 ? 'moderate' : 'low', exposures.length, 'The recent exposures do not support a confident explanation that would justify a structural change.', 'collect_more_data', false, 'Keep the current structure and collect another comparable exposure.', .15, 0, 0);
}
export function diagnoseSessionResponses(exercises, exposures, recovery) {
    return exercises.map(ex => diagnoseExerciseResponse(ex, exposures.get(ex.exerciseId) ?? [], recovery));
}
