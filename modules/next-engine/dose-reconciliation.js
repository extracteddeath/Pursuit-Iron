import { createExerciseMap } from './exercise-db.js';
import { createMusclePrescriptions } from './prescription.js';
import { INTENT_MUSCLES } from './topology.js';
import { PUBLIC_MEV_REGIONS, publicMevBaseTarget, publicMevContractApplies, publicMevLedger, publicMevRequired } from './public-mev.js';

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
    return `${candidate.sessionIndex}:${candidate.exerciseIndex}:${candidate.removeExercise ? 1 : 0}`;
}

function primaryMuscle(def) {
    const primary = Object.entries(def?.muscles ?? {}).find(([, contribution]) => contribution?.role === 'primary')?.[0];
    if (primary)
        return primary;
    return Object.entries(def?.muscles ?? {})
        .filter(([, contribution]) => (contribution?.credit ?? 0) > 0)
        .sort((a, b) => (b[1]?.credit ?? 0) - (a[1]?.credit ?? 0))[0]?.[0];
}

function fragmentedSession(session, phase) {
    const oneSet = session.exercises.filter(ex => ex.sets === 1).length;
    const peakMaintenanceTopOff = phase === 'peak' && session.exercises.length <= 5;
    return oneSet > 2 && !peakMaintenanceTopOff && (session.maxMinutes <= 45 || oneSet * 2 >= session.exercises.length);
}

function sessionAcceptsMuscle(session, muscle) {
    return (INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full ?? []).includes(muscle);
}

function sourceStructurePreserved(intent, exercises, exerciseMap) {
    if (intent !== 'full' && intent !== 'strength_full')
        return true;
    const defs = exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter(Boolean);
    const hasPush = defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily));
    const hasPull = defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily));
    const hasLower = defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily));
    return hasPush && hasPull && hasLower;
}

function addedSetMinutes(exercise) {
    return .75 + Math.max(0, Number(exercise.prescription?.restSeconds) || 0) / 60;
}

/**
 * M188 session-economy repair.
 *
 * A weekly program can be dose-correct yet still look coach-poor if one session contains several isolated
 * one-set fragments while another compatible training day has substantial unused capacity. This repair is
 * deliberately redistribution-only: it moves a one-set non-strength movement to a compatible session, or
 * merges it into the same movement there, so weekly muscle dose is exactly unchanged.
 *
 * It runs after recoverable-dose trimming because that is the final point at which weekly set shape is known.
 * It never moves strength work, never breaks an existing superset pair, never sacrifices the source
 * session's required movement identity, never lets the destination become fragmented, respects the
 * destination exercise-count budget, and uses conservative clock headroom. The ordinary final program
 * audit still remains authoritative after this pass.
 */
function repairOneSetFragmentation(sessions, exerciseMap, phase) {
    const repairs = [];
    for (let guard = 0; guard < 24; guard++) {
        const sourceIndex = sessions.findIndex(session => fragmentedSession(session, phase));
        if (sourceIndex < 0)
            break;
        const source = sessions[sourceIndex];
        if (source.exercises.length <= 3)
            break;

        const candidates = source.exercises
            .map((exercise, exerciseIndex) => ({ exercise, exerciseIndex, def: exerciseMap.get(exercise.exerciseId) }))
            .filter(item => item.def && item.exercise.sets === 1 && !STRENGTH_ROLES.has(item.exercise.role) && !item.exercise.supersetGroup)
            .map(item => ({ ...item, muscle: primaryMuscle(item.def) }))
            .filter(item => !!item.muscle)
            .sort((a, b) => {
                // True accessories are the cheapest fragments to relocate. A one-set compound may be the
                // only push/pull/lower identity movement on a full-body day, so consider compounds last.
                const roleA = a.exercise.role === 'hypertrophy_isolation' ? 0 : 1;
                const roleB = b.exercise.role === 'hypertrophy_isolation' ? 0 : 1;
                return roleA - roleB || (b.def.setupCost ?? 0) - (a.def.setupCost ?? 0) || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
            });

        let best = null;
        for (const candidate of candidates) {
            const sourceProposal = source.exercises.filter((_, index) => index !== candidate.exerciseIndex);
            if (sourceProposal.length < 3 || !sourceStructurePreserved(source.intent, sourceProposal, exerciseMap))
                continue;
            for (let destinationIndex = 0; destinationIndex < sessions.length; destinationIndex++) {
                if (destinationIndex === sourceIndex)
                    continue;
                const destination = sessions[destinationIndex];
                if (!sessionAcceptsMuscle(destination, candidate.muscle))
                    continue;

                const existingIndex = destination.exercises.findIndex(ex => ex.exerciseId === candidate.exercise.exerciseId && ex.role === candidate.exercise.role);
                const merge = existingIndex >= 0;
                if (!merge && destination.targetExercises !== undefined && destination.exercises.length >= destination.targetExercises + 1)
                    continue;
                if (merge && destination.exercises[existingIndex].sets >= 5)
                    continue;

                let destinationProposal;
                if (merge) {
                    destinationProposal = destination.exercises.map((ex, index) => index === existingIndex ? { ...ex, sets: ex.sets + 1 } : ex);
                }
                else {
                    destinationProposal = [...destination.exercises, { ...candidate.exercise, supersetGroup: undefined }];
                }
                const projected = { ...destination, exercises: destinationProposal };
                if (fragmentedSession(projected, phase))
                    continue;

                const extraMinutes = addedSetMinutes(candidate.exercise);
                const slackAfter = destination.maxMinutes - ((destination.estimatedMinutes ?? 0) + extraMinutes);
                if (slackAfter < -EPS)
                    continue;

                const score = (merge ? 100 : 0) + slackAfter + (candidate.exercise.role === 'hypertrophy_isolation' ? 2 : 0);
                if (!best || score > best.score + EPS) {
                    best = { candidate, sourceIndex, destinationIndex, sourceProposal, destinationProposal, merge, score };
                }
            }
        }

        if (!best)
            break;

        const sourceSession = sessions[best.sourceIndex];
        const destinationSession = sessions[best.destinationIndex];
        sourceSession.exercises = best.sourceProposal;
        destinationSession.exercises = best.destinationProposal;
        repairs.push({
            exerciseId: best.candidate.exercise.exerciseId,
            exercise: best.candidate.exercise.name,
            fromDay: sourceSession.day,
            toDay: destinationSession.day,
            merged: best.merge || undefined,
            weeklyDoseChanged: false
        });
    }
    return repairs;
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
 * - ordinary reductions never leave a one-set fragment;
 * - an optional two-set movement may be removed entirely when all dose floors remain protected and the
 *   session still has at least three other movements, avoiding redundant setup just to preserve two sets;
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
    const adjustments = [];
    // Accumulation reserve is an explicit regional contract. Overflow cleanup must preserve
    // achieved lat/upper-back and direct-delt floors before reducing collateral volume.
    const protectedRegions = publicMevContractApplies(request, phase)
        ? PUBLIC_MEV_REGIONS.filter(region => publicMevRequired(request, region)) : [];

    for (let guard = 0; guard < 160; guard++) {
        const before = doseSnapshot(sessions, exerciseMap, prescriptions);
        const beforeRegions = protectedRegions.length ? publicMevLedger(sessions, exerciseMap) : null;
        const overflowing = prescriptions.filter(p => before.fractional[p.muscle] > materialCeiling(p) + EPS);
        if (!overflowing.length)
            break;

        const beforeOverflow = overflowing.reduce((sum, p) => sum + Math.max(0, before.fractional[p.muscle] - materialCeiling(p)), 0);
        let best = null;

        sessions.forEach((session, sessionIndex) => {
            session.exercises.forEach((exercise, exerciseIndex) => {
                if (STRENGTH_ROLES.has(exercise.role) || exercise.sets < 2)
                    return;
                const removeExercise = exercise.sets === 2;
                if (removeExercise && session.exercises.length <= 3)
                    return;
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    return;
                const touchesOverflow = overflowing.some(p => (def.muscles?.[p.muscle]?.credit ?? 0) > 0);
                if (!touchesOverflow)
                    return;

                const proposed = cloneSessions(sessions);
                if (removeExercise)
                    proposed[sessionIndex].exercises.splice(exerciseIndex, 1);
                else
                    proposed[sessionIndex].exercises[exerciseIndex].sets -= 1;

                // Dose reconciliation is subtractive, but it may not invalidate the named session
                // contract that generation already satisfied. In particular, removing a two-set row/
                // pulldown from a Full Body day can leave weekly back dose acceptable while turning the
                // actual session into lower + push only. Preserve push + pull + lower identity before
                // considering the candidate's volume benefit.
                if (!sourceStructurePreserved(session.intent, proposed[sessionIndex].exercises, exerciseMap))
                    return;

                const after = doseSnapshot(proposed, exerciseMap, prescriptions);
                if (beforeRegions) {
                    const afterRegions = publicMevLedger(proposed, exerciseMap);
                    if (protectedRegions.some(region => afterRegions[region] + EPS <
                        Math.min(beforeRegions[region], publicMevBaseTarget(request, region))))
                        return;
                }

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
                const setupRemovalBonus = removeExercise ? .05 : 0;
                const score = benefit + rolePreference + broadCollateralBenefit + setupRemovalBonus;
                const candidate = { sessionIndex, exerciseIndex, removeExercise, score, benefit, before, after, proposed, def };
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
            toSets: best.removeExercise ? 0 : exercise.sets - 1,
            removedExercise: best.removeExercise || undefined,
            affected
        });
        if (best.removeExercise)
            sessions[best.sessionIndex].exercises.splice(best.exerciseIndex, 1);
        else
            sessions[best.sessionIndex].exercises[best.exerciseIndex].sets -= 1;
    }

    const fragmentationRepairs = repairOneSetFragmentation(sessions, exerciseMap, phase);
    const finalSnapshot = doseSnapshot(sessions, exerciseMap, prescriptions);
    const remainingOverflow = prescriptions
        .map(p => ({ muscle: p.muscle, actual: finalSnapshot.fractional[p.muscle], upper: p.upper, materialCeiling: materialCeiling(p) }))
        .filter(row => row.actual > row.materialCeiling + EPS)
        .map(row => ({ ...row, actual: Math.round(row.actual * 10) / 10, materialCeiling: Math.round(row.materialCeiling * 10) / 10 }));

    return { sessions, adjustments, remainingOverflow, fragmentationRepairs };
}
