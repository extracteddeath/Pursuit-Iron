// M230 canonical prescriptions stage. Stage context is local to one realization.
import { phasePolicyFor } from './phase-policy.js';
import { progressionInstruction, selectProgressionStyle } from './progression-style.js';

export /* M230:PRESERVE:top.repsForPhase:BEGIN */
function repsForPhase(ex, role, policy) {
    if (role === 'primary_strength' || role === 'secondary_strength') {
        const base = ex.preferredReps.strength ?? [4, 8];
        if (policy.strengthRepBias === 'hypertrophy') {
            const hypertrophy = ex.preferredReps.hypertrophy ?? [5, 10];
            const low = Math.max(ex.practicalReps[0], Math.min(hypertrophy[0], role === 'primary_strength' ? 5 : 6));
            const cap = role === 'primary_strength' ? (ex.fatigue.systemic >= 6 ? 6 : 8) : 10;
            const high = Math.max(low, Math.min(ex.practicalReps[1], hypertrophy[1], cap));
            return [low, high];
        }
        if (policy.strengthRepBias === 'peak') {
            const low = Math.max(ex.practicalReps[0], role === 'primary_strength' ? 1 : 2);
            const high = Math.max(low, Math.min(base[1], role === 'primary_strength' ? 3 : 4));
            return [low, high];
        }
        if (policy.strengthRepBias === 'lower') {
            const low = Math.max(ex.practicalReps[0], role === 'primary_strength' ? 2 : 3);
            const high = Math.max(low, Math.min(base[1], role === 'primary_strength' ? 5 : 6));
            return [low, high];
        }
        return base;
    }
    return ex.preferredReps.hypertrophy ?? (ex.flags.compound ? [6, 12] : [10, 20]);
}
/* M230:PRESERVE:top.repsForPhase:END */

export /* M230:PRESERVE:top.rirForPhase:BEGIN */
function rirForPhase(role, policy) {
    const base = role === 'primary_strength' || role === 'secondary_strength' ? [2, 3] : role === 'hypertrophy_compound' ? [1, 2] : [0, 2];
    const shift = role === 'primary_strength' || role === 'secondary_strength' ? policy.strengthRirShift : policy.hypertrophyRirShift;
    return [Math.min(5, base[0] + shift), Math.min(6, base[1] + shift)];
}
/* M230:PRESERVE:top.rirForPhase:END */

export /* M230:PRESERVE:top.restForExercise:BEGIN */
function restForExercise(role, ex) { return role === 'primary_strength' ? 240 : role === 'secondary_strength' ? 180 : ex.flags.compound ? 150 : 90; }
/* M230:PRESERVE:top.restForExercise:END */

export /* M230:PRESERVE:top.progressionStyleForExercise:BEGIN */
function progressionStyleForExercise(ex, role, policy, experience = 'intermediate') {
    return selectProgressionStyle(ex, role, {
        phase: policy.phase,
        experience,
        blockWeeks: policy.blockWeeks,
        requestedStyle: policy.requestedProgressionStyle
    }).style;
}
/* M230:PRESERVE:top.progressionStyleForExercise:END */

export /* M230:PRESERVE:top.progressionForExercise:BEGIN */
function progressionForExercise(ex, role, policy = phasePolicyFor('mixed_accumulation'), experience = 'intermediate') {
    return progressionInstruction(progressionStyleForExercise(ex, role, policy, experience));
}
/* M230:PRESERVE:top.progressionForExercise:END */

export /* M230:PRESERVE:top.makePlanned:BEGIN */
function makePlanned(ex, role, sets, policy, experience = 'intermediate') {
    // The allocation expresses why work exists; the final exercise role should describe the movement that
    // was actually selected. Large-muscle claims may legitimately resolve to a fly/extension, and labeling
    // that isolation as a compound distorts RIR semantics and coach-facing output.
    const realizedRole = (role === 'hypertrophy_compound' || role === 'hypertrophy_isolation')
        ? (ex.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation')
        : role;
    const prescription = { reps: repsForPhase(ex, realizedRole, policy), rir: rirForPhase(realizedRole, policy), restSeconds: restForExercise(realizedRole, ex) };
    const progressionSelection = selectProgressionStyle(ex, realizedRole, {
        phase: policy.phase,
        experience,
        blockWeeks: policy.blockWeeks,
        requestedStyle: policy.requestedProgressionStyle,
        prescription
    });
    const progressionStyle = progressionSelection.style;
    return {
        exerciseId: ex.id, name: ex.name, role: realizedRole, sets, prescription,
        progression: progressionInstruction(progressionStyle), progressionStyle,
        progressionSelection: {
            source: progressionSelection.source,
            confidence: progressionSelection.confidence,
            reason: progressionSelection.reason
        }
    };
}
/* M230:PRESERVE:top.makePlanned:END */
