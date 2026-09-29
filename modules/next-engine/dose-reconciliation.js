import { createExerciseMap } from './exercise-db.js';
import { createMusclePrescriptions } from './prescription.js';

const STRENGTH_ROLES = new Set(['primary_strength', 'secondary_strength', 'strength_support']);
const EPS = .001;

function cloneSessions(sessions) {
    return sessions.map(session => ({
        ...session,
        exercises: session.exercises.map(exercise => ({
            ...exercise,
            prescription: exercise.prescription ? { ...exercise.prescription } : exercise.prescription,
            progression: exercise.progression ? { ...exercise.progression } : exercise.progression
        }))
    }));
}

function doseSnapshot(sessions, exerciseMap, prescriptions) {
    const fractional = Object.fromEntries(prescriptions.map(p => [p.muscle, 0]));
    const direct = Object.fromEntries(prescriptions.map(p => [p.muscle, 0]));
    for (const session of sessions) {
        for (const exercise of session.exercises) {
            const def = exerciseMap.get(exercise.exerciseId);
            if (!def)
                continue;
            for (const [muscle, contribution] of Object.entries(def.muscles ?? {})) {
                if (!(muscle in fractional) || contribution.credit <= 0)
                    continue;
                fractional[muscle] += contribution.credit * exercise.sets;
                if (contribution.credit >= .999)
                    direct[muscle] += exercise.sets;
            }
        }
    }
    return { fractional, direct };
}

function materialCeiling(prescription) {
    // Upper landmarks are modeled useful-region boundaries, not exact integer-set quotas. Allow one
    // set-equivalent of discrete realization slack, but not the 1.5–2.0x overflow that M181 exposed.
    return prescription.upper + Math.max(1, prescription.upper * .08);
}

function preservationFloor(before, modeledMinimum, modeledPreferred) {
    if (before + EPS >= modeledPreferred)
        return modeledPreferred;
    if (before + EPS >= modeledMinimum)
        return modeledMinimum;
    // Never make a pre-existing shortfall worse during overflow cleanup.
    return before;
}

function candidateKey(candidate) {
    return `${candidate.sessionIndex}:${candidate.exerciseIndex}`;
}

/**
 * Reconcile final realized dose against modeled recoverable upper regions.
 *
 * Allocation already caps each muscle claim, but realization can create additional secondary-credit
 * volume: e.g. rows/pulldowns add biceps and rear-delt dose while direct curls/rear-delt work still gets
 * fully realized. Long time bands made that mismatch large enough to produce 1.5–2x modeled upper dose.
 *
 * This pass is deliberately subtractive and conservative:
 * - strength-specific work is never reduced;
 * - no movement is reduced below two working sets, avoiding one-set fragmentation;
 * - a muscle that reached its preferred dose keeps at least preferred dose;
 * - direct preferred dose is preserved when it was already achieved;
 * - pre-existing minimum/direct-minimum shortfalls are never worsened;
 * - only reductions that materially shrink current over-ceiling dose are accepted.
 *
 * The final program is still rebuilt and run through the normal arbiter after this pass. This is not a
 * second volume ledger or an audit bypass; it reconciles collateral realization with the same prescription
 * landmarks the allocator and arbiter already use.
 */
export function reconcileRecoverableDose(inputSessions, request, phase) {
    const sessions = cloneSessions(inputSessions);
    const exerciseMap = createExerciseMap(request.customExercises);
    const prescriptions = createMusclePrescriptions(request, phase)
        .filter(p => p.muscle !== 'front_delts' && p.priority !== 'maintenance' && p.upper > 0);
    const prescriptionMap = new Map(prescriptions.map(p => [p.muscle, p]));
    const adjustments = [];

    for (let guard = 0; guard < 160; guard++) {
        const before = doseSnapshot(sessions, exerciseMap, prescriptions);
        const overflowing = prescriptions.filter(p => before.fractional[p.muscle] > materialCeiling(p) + EPS);
        if (!overflowing.length)
            break;

        const beforeOverflow = overflowing.reduce((sum, p) => sum + Math.max(0, before.fractional[p.muscle] - materialCeiling(p)), 0);
        let best = null;

        sessions.forEach((session, sessionIndex) => {
            session.exercises.forEach((exercise, exerciseIndex) => {
                if (STRENGTH_ROLES.has(exercise.role) || exercise.sets <= 2)
                    return;
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    return;
                const touchesOverflow = overflowing.some(p => (def.muscles?.[p.muscle]?.credit ?? 0) > 0);
                if (!touchesOverflow)
                    return;

                const proposed = cloneSessions(sessions);
                proposed[sessionIndex].exercises[exerciseIndex].sets -= 1;
                const after = doseSnapshot(proposed, exerciseMap, prescriptions);

                for (const p of prescriptions) {
                    const muscle = p.muscle;
                    const totalFloor = preservationFloor(before.fractional[muscle], p.minimum, p.preferred);
                    const directFloor = preservationFloor(before.direct[muscle], p.directMinimum ?? 0, p.directPreferred ?? 0);
                    if (after.fractional[muscle] + EPS < totalFloor || after.direct[muscle] + EPS < directFloor)
                        return;
                }

                const afterOverflow = prescriptions.reduce((sum, p) => sum + Math.max(0, after.fractional[p.muscle] - materialCeiling(p)), 0);
                const benefit = beforeOverflow - afterOverflow;
                if (benefit <= EPS)
                    return;

                const rolePreference = exercise.role === 'hypertrophy_isolation' ? .20 : exercise.role === 'hypertrophy_compound' ? .08 : 0;
                const broadCollateralBenefit = overflowing.reduce((sum, p) => sum + Math.min(1, def.muscles?.[p.muscle]?.credit ?? 0), 0) * .03;
                const score = benefit + rolePreference + broadCollateralBenefit;
                const candidate = { sessionIndex, exerciseIndex, score, benefit, before, after, proposed, def };
                if (!best || candidate.score > best.score + EPS || (Math.abs(candidate.score - best.score) <= EPS && candidateKey(candidate) < candidateKey(best)))
                    best = candidate;
            });
        });

        if (!best)
            break;

        const exercise = sessions[best.sessionIndex].exercises[best.exerciseIndex];
        const affected = prescriptions
            .map(p => ({ muscle: p.muscle, before: best.before.fractional[p.muscle], after: best.after.fractional[p.muscle], upper: p.upper }))
            .filter(row => row.after < row.before - EPS)
            .map(row => ({ ...row, before: Math.round(row.before * 10) / 10, after: Math.round(row.after * 10) / 10 }));
        adjustments.push({
            day: sessions[best.sessionIndex].day,
            exerciseId: exercise.exerciseId,
            exercise: exercise.name,
            fromSets: exercise.sets,
            toSets: exercise.sets - 1,
            affected
        });
        sessions[best.sessionIndex].exercises[best.exerciseIndex].sets -= 1;
    }

    const finalSnapshot = doseSnapshot(sessions, exerciseMap, prescriptions);
    const remainingOverflow = prescriptions
        .map(p => ({ muscle: p.muscle, actual: finalSnapshot.fractional[p.muscle], upper: p.upper, materialCeiling: materialCeiling(p) }))
        .filter(row => row.actual > row.materialCeiling + EPS)
        .map(row => ({ ...row, actual: Math.round(row.actual * 10) / 10, materialCeiling: Math.round(row.materialCeiling * 10) / 10 }));

    return { sessions, adjustments, remainingOverflow };
}
