import { createExerciseCatalog, createExerciseMap } from './exercise-db.js';
import { estimateSessionMinutes, progressionForExercise, progressionStyleForExercise, repsForPhase, restForExercise, rirForPhase } from './realizer.js';
import { phasePolicyFor } from './phase-policy.js';
const FOCUS_LIFT = {
    bench_focus: 'bench_press',
    squat_focus: 'back_squat',
    deadlift_focus: 'deadlift',
    press_focus: 'overhead_press'
};
const REPLACEMENT_FAMILIES = {
    bench_press: new Set(['horizontal_press', 'chest_adduction']),
    back_squat: new Set(['squat', 'leg_press', 'knee_extension', 'hip_extension', 'knee_flexion', 'hip_hinge']),
    deadlift: new Set(['hip_hinge', 'hip_extension', 'knee_flexion']),
    overhead_press: new Set(['vertical_press', 'lateral_raise', 'horizontal_press'])
};
function equipmentEligible(ex, session, request) {
    const day = request.schedule.days.find(item => item.day === session.day);
    if (!day)
        return false;
    const equipment = day.equipmentOverride ?? request.equipment.available;
    if ((ex.flags.bodyweight || ex.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
        return false;
    return [ex.equipment, ...(ex.equipmentAlternatives ?? [])].some(setup => setup.every(required => required === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : equipment.includes(required)));
}
function maxBarbells(session, request) {
    return request.schedule.days.find(item => item.day === session.day)?.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay;
}
function planned(ex, role, sets, phase, request) {
    const policy = phasePolicyFor(phase);
    const realizedRole = role === 'hypertrophy_isolation' ? 'hypertrophy_compound' : role;
    const progressionStyle = progressionStyleForExercise(ex, realizedRole, policy, request.athlete.experience);
    return {
        exerciseId: ex.id,
        name: ex.name,
        role: realizedRole,
        sets,
        prescription: {
            reps: repsForPhase(ex, realizedRole, policy),
            rir: rirForPhase(realizedRole, policy),
            restSeconds: restForExercise(realizedRole, ex)
        },
        progression: progressionForExercise(ex, realizedRole, policy, request.athlete.experience),
        progressionStyle
    };
}
/**
 * Explicit focus intents are a session contract. If equipment permits the named lift, a Squat/Bench/
 * Deadlift/Press focus day must contain a meaningfully lift-specific movement even when the overall goal
 * is hypertrophy. The repair swaps a compatible compound instead of adding volume, preserving the user's
 * time cap and the phase-appropriate rep/RIR policy.
 */
export function proposeExplicitFocusRepair(session, request, phase) {
    const catalog = createExerciseCatalog(request.customExercises);
    const defs = createExerciseMap(request.customExercises);
    const lift = FOCUS_LIFT[session.intent];
    if (!lift)
        return session;
    const hasSpecific = session.exercises.some(ex => (defs.get(ex.exerciseId)?.liftSpecificity?.[lift] ?? 0) > .45);
    if (hasSpecific)
        return session;
    const currentBarbells = session.exercises.filter(ex => defs.get(ex.exerciseId)?.flags.barbell).length;
    const candidate = catalog
        .filter(ex => equipmentEligible(ex, session, request))
        .filter(ex => !request.preferences.avoidedExercises?.includes(ex.id))
        .filter(ex => (ex.liftSpecificity?.[lift] ?? 0) > .45)
        .filter(ex => !session.exercises.some(existing => existing.exerciseId === ex.id))
        .filter(ex => !ex.flags.barbell || currentBarbells < maxBarbells(session, request) || session.exercises.some(existing => defs.get(existing.exerciseId)?.flags.barbell))
        .sort((a, b) => (b.liftSpecificity?.[lift] ?? 0) - (a.liftSpecificity?.[lift] ?? 0) || b.suitability.hypertrophy - a.suitability.hypertrophy || b.loadability - a.loadability || a.id.localeCompare(b.id))[0];
    if (!candidate)
        return session;
    const families = REPLACEMENT_FAMILIES[lift];
    const replacements = session.exercises
        .map((exercise, index) => ({ exercise, index, def: defs.get(exercise.exerciseId) }))
        .filter((item) => !!item.def)
        .filter(item => item.exercise.role !== 'primary_strength' && item.exercise.role !== 'secondary_strength')
        .filter(item => families.has(item.def.movementFamily))
        .sort((a, b) => Number(b.def.flags.compound) - Number(a.def.flags.compound) || b.exercise.sets - a.exercise.sets || a.index - b.index);
    const compoundReplacement = replacements.find(item => item.def.flags.compound);
    if (compoundReplacement) {
        for (let sets = Math.max(1, compoundReplacement.exercise.sets); sets >= 1; sets--) {
            const next = session.exercises.map((exercise, index) => index === compoundReplacement.index ? planned(candidate, compoundReplacement.exercise.role, sets, phase, request) : exercise);
            const minutes = estimateSessionMinutes(next);
            if (minutes <= session.maxMinutes)
                return { ...session, exercises: next, estimatedMinutes: minutes };
        }
    }
    // If the focus day contains only isolation/accessory work from the lift region, replacing that work
    // can erase a different muscle floor (for example, swapping the only hamstring curl for Back Squat).
    // Prefer adding a small two-set focus anchor when the session still has room. The outer generator
    // transaction accepts this only when the complete independent audit remains green.
    const densityCeiling = session.maxMinutes <= 30 ? 6 : session.maxMinutes <= 45 ? 7 : session.maxMinutes <= 60 ? 9 : session.maxMinutes <= 75 ? 10 : session.maxMinutes <= 90 ? 11 : 12;
    // A named focus lift is part of the session contract, not filler. In roomy sessions we may exceed the
    // soft target-exercise count when the independent time/density guards still say the session is sane.
    // This matters for focused strength structures whose hypertrophy realization can otherwise consume the
    // nominal exercise slots with useful accessory work while omitting the lift named by the session.
    const exerciseCeiling = Math.max((session.targetExercises ?? session.exercises.length) + 1, densityCeiling);
    if (session.exercises.length < exerciseCeiling) {
        for (const sets of [2, 1]) {
            const next = [planned(candidate, 'hypertrophy_compound', sets, phase, request), ...session.exercises];
            const minutes = estimateSessionMinutes(next);
            if (minutes <= session.maxMinutes)
                return { ...session, exercises: next, estimatedMinutes: minutes };
        }
    }
    const replacement = replacements[0];
    if (replacement) {
        for (let sets = Math.max(1, replacement.exercise.sets); sets >= 1; sets--) {
            const next = session.exercises.map((exercise, index) => index === replacement.index ? planned(candidate, replacement.exercise.role, sets, phase, request) : exercise);
            const minutes = estimateSessionMinutes(next);
            if (minutes <= session.maxMinutes)
                return { ...session, exercises: next, estimatedMinutes: minutes };
        }
    }
    return session;
}
export function enforceExplicitFocusIntents(sessions, request, phase) {
    return sessions.map(session => proposeExplicitFocusRepair(session, request, phase));
}
