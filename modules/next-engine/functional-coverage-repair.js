import { createExerciseCatalog, createExerciseMap } from './exercise-db.js';
import { functionalCoverageBiases } from './functional-coverage.js';
import { estimateSessionMinutes, progressionForExercise, progressionStyleForExercise, repsForPhase, restForExercise, rirForPhase } from './realizer.js';
import { phasePolicyFor } from './phase-policy.js';
const FINDING_BIAS = {
    HAMSTRINGS_HIP_EXTENSION_MISSING: 'hamstrings_hip_extension',
    HAMSTRINGS_KNEE_FLEXION_MISSING: 'hamstrings_knee_flexion',
    CALVES_STRAIGHT_KNEE_MISSING: 'calves_straight_knee',
    CALVES_BENT_KNEE_MISSING: 'calves_bent_knee',
    TRICEPS_LENGTHENED_EXTENSION_MISSING: 'triceps_lengthened',
    TRICEPS_NON_OVERHEAD_EXTENSION_MISSING: 'triceps_non_overhead',
    BACK_VERTICAL_PULL_MISSING: 'back_vertical_pull',
    BACK_HORIZONTAL_ROW_MISSING: 'back_horizontal_row'
};
const BIAS_MUSCLE = {
    hamstrings_hip_extension: 'hamstrings', hamstrings_knee_flexion: 'hamstrings',
    calves_straight_knee: 'calves', calves_bent_knee: 'calves',
    triceps_lengthened: 'triceps', triceps_non_overhead: 'triceps',
    back_vertical_pull: 'back', back_horizontal_row: 'back'
};
const MUSCLE_INTENTS = {
    hamstrings: new Set(['lower', 'legs', 'glute', 'deadlift_focus', 'strength_full', 'full']),
    calves: new Set(['lower', 'legs', 'glute', 'strength_full', 'full']),
    triceps: new Set(['push', 'upper', 'bench_focus', 'press_focus', 'strength_full', 'full']),
    back: new Set(['pull', 'upper', 'deadlift_focus', 'strength_full', 'full'])
};
function dayFor(session, request) { return request.schedule.days.find(day => day.day === session.day); }
function equipmentEligible(def, session, request) {
    const day = dayFor(session, request);
    if (!day)
        return false;
    const equipment = day.equipmentOverride ?? request.equipment.available;
    if ((def.flags.bodyweight || def.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
        return false;
    return [def.equipment, ...(def.equipmentAlternatives ?? [])].some(setup => setup.every(required => required === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : equipment.includes(required)));
}
function barbellCount(session, defs) { return session.exercises.filter(ex => defs.get(ex.exerciseId)?.flags.barbell).length; }
function maxBarbells(session, request) { return dayFor(session, request)?.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay; }
function candidateEligible(def, session, request, defs) {
    if (!equipmentEligible(def, session, request) || request.preferences.avoidedExercises?.includes(def.id) || session.exercises.some(ex => ex.exerciseId === def.id))
        return false;
    if (def.flags.barbell && barbellCount(session, defs) >= maxBarbells(session, request))
        return false;
    return true;
}
function planned(def, role, sets, phase, request) {
    const policy = phasePolicyFor(phase);
    const realizedRole = role === 'hypertrophy_isolation' && def.flags.compound ? 'hypertrophy_compound' : role;
    const progressionStyle = progressionStyleForExercise(def, realizedRole, policy, request.athlete.experience);
    return { exerciseId: def.id, name: def.name, role: realizedRole, sets, prescription: { reps: repsForPhase(def, realizedRole, policy), rir: rirForPhase(realizedRole, policy), restSeconds: restForExercise(realizedRole, def) }, progression: progressionForExercise(def, realizedRole, policy, request.athlete.experience), progressionStyle };
}
function roleFor(def) { return def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation'; }
function densityCeiling(maxMinutes) { return maxMinutes <= 30 ? 6 : maxMinutes <= 45 ? 7 : maxMinutes <= 60 ? 9 : maxMinutes <= 75 ? 10 : maxMinutes <= 90 ? 11 : 12; }
function sessionPreference(session, muscle) {
    if (MUSCLE_INTENTS[muscle].has(session.intent))
        return 3;
    if (session.intent === 'full')
        return 2;
    return 0;
}
function candidatesFor(bias, session, request, defs) {
    const catalog = createExerciseCatalog(request.customExercises);
    return catalog.filter(def => functionalCoverageBiases(def).includes(bias) && candidateEligible(def, session, request, defs))
        .sort((a, b) => b.suitability.hypertrophy - a.suitability.hypertrophy || Number(!b.flags.compound) - Number(!a.flags.compound) || (a.fatigue.systemic ?? 0) - (b.fatigue.systemic ?? 0) || a.id.localeCompare(b.id));
}
/**
 * Produce conservative, dose-aware alternatives for a missing weekly function. The generator decides
 * whether to accept any proposal by re-running its complete independent audit; this helper never bypasses
 * time, MEV/MRV, equipment, strength-anchor, recovery, or split contracts.
 *
 * Order of operations:
 * 1) split an existing non-strength exercise for the same muscle into two function variants, preserving
 *    weekly set count (ideal for triceps/calves/back/hamstring accessory work);
 * 2) add a tiny 1-2 set accessory when the session has real time/density headroom (needed when all of the
 *    existing stimulus comes from protected strength work such as deadlifts);
 * 3) replace a non-strength accessory only as a last resort. The outer audit rejects harmful swaps.
 */
export function proposeFunctionalCoverageRepairs(sessions, finding, request, phase) {
    const bias = FINDING_BIAS[finding.code];
    if (!bias)
        return [];
    const muscle = BIAS_MUSCLE[bias];
    const defs = createExerciseMap(request.customExercises);
    const ordered = sessions.map((session, index) => ({ session, index, preference: sessionPreference(session, muscle) }))
        .filter(item => item.preference > 0)
        .sort((a, b) => b.preference - a.preference || (b.session.maxMinutes - b.session.estimatedMinutes) - (a.session.maxMinutes - a.session.estimatedMinutes) || a.index - b.index);
    const proposals = [];
    const push = (index, nextSession) => { const next = sessions.map(session => ({ ...session, exercises: [...session.exercises] })); next[index] = nextSession; proposals.push(next); };
    // Dose-neutral split of an existing direct accessory: e.g. 5 pushdown sets -> 3 pushdown + 2 overhead.
    for (const { session, index } of ordered) {
        const choices = candidatesFor(bias, session, request, defs);
        if (!choices.length)
            continue;
        const donors = session.exercises.map((exercise, exerciseIndex) => ({ exercise, exerciseIndex, def: defs.get(exercise.exerciseId) }))
            .filter((item) => !!item.def)
            .filter(item => item.exercise.role !== 'primary_strength' && item.exercise.role !== 'secondary_strength')
            .filter(item => (item.def.muscles[muscle]?.credit ?? 0) >= .5 && item.exercise.sets >= 2)
            .filter(item => functionalCoverageBiases(item.def).some(existingBias => BIAS_MUSCLE[existingBias] === muscle))
            .filter(item => !functionalCoverageBiases(item.def).includes(bias))
            .sort((a, b) => b.exercise.sets - a.exercise.sets || a.exerciseIndex - b.exerciseIndex);
        for (const donor of donors)
            for (const candidate of choices.slice(0, 4)) {
                const moved = Math.min(2, Math.max(1, donor.exercise.sets - 1));
                const existing = { ...donor.exercise, sets: donor.exercise.sets - moved };
                const nextExercises = session.exercises.map((exercise, i) => i === donor.exerciseIndex ? existing : exercise);
                nextExercises.splice(donor.exerciseIndex + 1, 0, planned(candidate, roleFor(candidate), moved, phase, request));
                const minutes = estimateSessionMinutes(nextExercises);
                if (minutes <= session.maxMinutes && nextExercises.length <= densityCeiling(session.maxMinutes))
                    push(index, { ...session, exercises: nextExercises, estimatedMinutes: minutes });
            }
    }
    // Add the minimum useful accessory dose when the existing function is protected strength work.
    for (const { session, index } of ordered) {
        if (session.exercises.length >= densityCeiling(session.maxMinutes))
            continue;
        for (const candidate of candidatesFor(bias, session, request, defs).slice(0, 5))
            for (const sets of [2, 1]) {
                const nextExercises = [...session.exercises, planned(candidate, roleFor(candidate), sets, phase, request)];
                const minutes = estimateSessionMinutes(nextExercises);
                if (minutes <= session.maxMinutes)
                    push(index, { ...session, exercises: nextExercises, estimatedMinutes: minutes });
            }
    }
    // Last resort: swap a low-value non-strength accessory. Full-program audit must approve the trade.
    for (const { session, index } of ordered) {
        const choices = candidatesFor(bias, session, request, defs);
        if (!choices.length)
            continue;
        const replaceable = session.exercises.map((exercise, exerciseIndex) => ({ exercise, exerciseIndex, def: defs.get(exercise.exerciseId) }))
            .filter((item) => !!item.def)
            .filter(item => item.exercise.role !== 'primary_strength' && item.exercise.role !== 'secondary_strength')
            .filter(item => (item.def.muscles[muscle]?.credit ?? 0) < .5)
            .sort((a, b) => a.exercise.sets - b.exercise.sets || (a.def.suitability.hypertrophy - b.def.suitability.hypertrophy) || b.exerciseIndex - a.exerciseIndex);
        for (const victim of replaceable.slice(0, 4))
            for (const candidate of choices.slice(0, 4)) {
                const sets = Math.max(1, Math.min(2, victim.exercise.sets));
                const nextExercises = session.exercises.map((exercise, i) => i === victim.exerciseIndex ? planned(candidate, roleFor(candidate), sets, phase, request) : exercise);
                const minutes = estimateSessionMinutes(nextExercises);
                if (minutes <= session.maxMinutes)
                    push(index, { ...session, exercises: nextExercises, estimatedMinutes: minutes });
            }
    }
    return proposals;
}
