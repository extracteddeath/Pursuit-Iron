import { initialPhaseForGoal, phaseLabel } from './phase-policy.js';
import { recoverySignalForDecision } from './recovery.js';
function normalizedDaysPerWeek(value) {
    if (typeof value === 'boolean' || typeof value === 'object' || value === null || value === '')
        throw new TypeError('Days per week must be a whole number between 1 and 7.');
    const n = Number(value);
    if (!Number.isInteger(n) || n < 1 || n > 7)
        throw new RangeError('Days per week must be a whole number between 1 and 7.');
    return n;
}
function validCyclePhase(phase) {
    phaseLabel(phase);
    return phase;
}
export function createInitialCycleState(goal, daysPerWeek) {
    const days = normalizedDaysPerWeek(daysPerWeek);
    const minimumWorkouts = Math.max(4, days * 2);
    const reviewAfterWorkouts = Math.max(minimumWorkouts + 2, days * 4);
    const phase = initialPhaseForGoal(goal);
    return {
        phase, phaseLabel: phaseLabel(phase), workoutsInPhase: 0, minimumWorkouts, reviewAfterWorkouts, status: 'building',
        rationale: goal === 'hypertrophy'
            ? 'Build productive hypertrophy work while preserving successful exercises. Phase review is evidence-triggered rather than calendar-automatic.'
            : goal === 'strength'
                ? 'Accumulate specific strength practice and supporting work before considering a higher-specificity phase.'
                : 'Develop strength and hypertrophy together while preserving room to bias the next phase from actual response.'
    };
}
function nextDevelopmentPhase(goal, current) {
    if (current === 'recovery')
        return initialPhaseForGoal(goal);
    if (goal === 'strength') {
        if (current === 'strength_accumulation')
            return 'intensification';
        if (current === 'intensification')
            return 'peak';
        return 'strength_accumulation';
    }
    if (goal === 'mixed')
        return current === 'mixed_accumulation' ? 'intensification' : 'mixed_accumulation';
    return 'hypertrophy_accumulation';
}
export function recommendNextPhase(goal, state, recovery) {
    initialPhaseForGoal(goal);
    if (!state || typeof state !== 'object')
        throw new TypeError('Cycle state must be an object.');
    validCyclePhase(state.phase);
    if (state.phase === 'recovery') {
        if (state.status !== 'review_eligible' || (state.recoveryExitEvidence ?? 0) < 2)
            return undefined;
        return state.recoveryEntryPhase && state.recoveryEntryPhase !== 'recovery'
            ? state.recoveryEntryPhase
            : initialPhaseForGoal(goal);
    }
    if (recovery?.status === 'deload_recommended')
        return 'recovery';
    if (state.status !== 'review_eligible')
        return undefined;
    return nextDevelopmentPhase(goal, state.phase);
}
function causalCycleEvidence(decisions) {
    const signals = (decisions ?? []).map(recoverySignalForDecision);
    const negative = signals.filter(v => v < 0);
    const positive = signals.filter(v => v > 0);
    return {
        negativeCount: negative.length,
        negativeScore: negative.reduce((sum, value) => sum + Math.abs(value), 0),
        positiveCount: positive.length,
        positiveScore: positive.reduce((sum, value) => sum + value, 0)
    };
}
function advanceRecoveryPhase(state, decisions, recovery, goal, workoutsInPhase) {
    const { negativeCount: concerning, negativeScore, positiveCount: positive } = causalCycleEvidence(decisions);
    let exitEvidence = state.recoveryExitEvidence ?? 0;
    let status = 'building';
    let rationale = 'Recovery phase remains active while the engine looks for repeated evidence that performance and fatigue have normalized.';
    if (recovery?.status === 'deload_recommended') {
        exitEvidence = 0;
        status = 'recovery_review';
        rationale = 'Broad negative performance signals remain present during recovery. Continue reducing stress and review sleep, soreness, exercise fit, and non-training recovery before returning to development work.';
    }
    else if (recovery?.status === 'watch') {
        exitEvidence = Math.max(0, exitEvidence - 1);
        rationale = 'Recovery is improving but still carries repeated caution signals. Stay in recovery and collect another exposure before considering an exit.';
    }
    else if (recovery?.status === 'normal') {
        const acceptableConcern = Math.max(1, Math.floor(decisions.length * .25));
        const favorable = decisions.length > 0 && positive > 0 && concerning <= acceptableConcern && negativeScore < .75;
        exitEvidence = favorable ? exitEvidence + 1 : Math.max(0, exitEvidence - 1);
        rationale = favorable
            ? `Recovery evidence is normalizing (${exitEvidence}/2 exit confirmations). Return to development only after repeated favorable exposures and the minimum recovery dose is complete.`
            : 'Recovery assessment is broadly normal, but the current workout did not provide a strong enough favorable performance signal to count toward recovery exit.';
    }
    if (workoutsInPhase >= state.minimumWorkouts && exitEvidence >= 2) {
        status = 'review_eligible';
        rationale = 'Recovery exit criteria are satisfied: the minimum recovery exposure is complete and repeated favorable performance signals have returned without broad fatigue evidence. A return to the pre-recovery development phase is ready for review.';
    }
    else if (workoutsInPhase >= state.reviewAfterWorkouts && exitEvidence < 2 && recovery?.status !== 'deload_recommended') {
        status = 'recovery_review';
        rationale = 'Recovery has lasted beyond the normal review window without repeated clear exit evidence. Review exercise fit, life stress, and program demands instead of automatically resuming harder training.';
    }
    const interim = {
        ...state,
        workoutsInPhase,
        status,
        rationale,
        recovery,
        recoveryExitEvidence: exitEvidence,
        recommendedNextPhase: undefined
    };
    return { ...interim, recommendedNextPhase: goal ? recommendNextPhase(goal, interim, recovery) : undefined };
}
export function advanceCycleState(state, decisions, structuralAdaptation, recovery, goal) {
    if (!state || typeof state !== 'object')
        throw new TypeError('Cycle state must be an object.');
    validCyclePhase(state.phase);
    if (!Number.isInteger(state.workoutsInPhase) || state.workoutsInPhase < 0)
        throw new RangeError('Cycle workouts-in-phase must be a non-negative whole number.');
    if (!Number.isInteger(state.minimumWorkouts) || state.minimumWorkouts < 1)
        throw new RangeError('Cycle minimum workouts must be a positive whole number.');
    if (!Number.isInteger(state.reviewAfterWorkouts) || state.reviewAfterWorkouts < state.minimumWorkouts)
        throw new RangeError('Cycle review threshold must be a whole number at or above the minimum workouts.');
    if (state.recoveryExitEvidence !== undefined && (!Number.isInteger(state.recoveryExitEvidence) || state.recoveryExitEvidence < 0))
        throw new RangeError('Recovery exit evidence must be a non-negative whole number.');
    const workoutsInPhase = state.workoutsInPhase + 1;
    if (state.phase === 'recovery')
        return advanceRecoveryPhase(state, decisions, recovery, goal, workoutsInPhase);
    const evidence = causalCycleEvidence(decisions);
    const concerning = evidence.negativeCount;
    const negativeScore = evidence.negativeScore;
    const progress = evidence.positiveCount;
    let status = 'building';
    let rationale = state.rationale;
    if (recovery?.status === 'deload_recommended') {
        status = 'recovery_review';
        rationale = recovery.rationale;
    }
    else if (workoutsInPhase >= state.minimumWorkouts && concerning >= Math.max(2, Math.ceil(decisions.length * .4)) && negativeScore >= 1.5) {
        status = 'recovery_review';
        rationale = 'Several exercises show causal hard-effort, load, rep-floor, or incomplete-session concerns after sufficient exposure. Evaluate fatigue, exercise fit, and recovery before changing phase or adding work.';
    }
    else if (workoutsInPhase >= state.reviewAfterWorkouts) {
        status = 'review_eligible';
        rationale = progress > 0 && !structuralAdaptation
            ? 'The phase has enough exposure for review, but performance is still progressing. Continuing the current phase remains a valid option.'
            : 'The phase has enough exposure for an evidence-based review. A transition is optional, not automatic.';
    }
    const interim = { ...state, workoutsInPhase, status, rationale, recovery };
    const recommendedNextPhase = goal ? recommendNextPhase(goal, interim, recovery) : undefined;
    return { ...interim, recommendedNextPhase };
}
export function startPhase(state, phase, goal, daysPerWeek) {
    const days = normalizedDaysPerWeek(daysPerWeek);
    validCyclePhase(phase);
    if (!state || typeof state !== 'object')
        throw new TypeError('Cycle state must be an object.');
    validCyclePhase(state.phase);
    const base = createInitialCycleState(goal, days);
    const enteringRecovery = phase === 'recovery';
    const minimumWorkouts = enteringRecovery ? Math.max(2, Math.ceil(days * .6)) : base.minimumWorkouts;
    const reviewAfterWorkouts = enteringRecovery ? Math.max(minimumWorkouts + 2, days) : base.reviewAfterWorkouts;
    const recoveryEntryPhase = enteringRecovery
        ? (state.phase === 'recovery' ? state.recoveryEntryPhase : state.phase)
        : undefined;
    return {
        ...base,
        phase,
        phaseLabel: phaseLabel(phase),
        rationale: enteringRecovery
            ? 'Reduce training stress temporarily while preserving movement practice. Recovery exit requires repeated favorable evidence; it is not triggered by a fixed number of days alone.'
            : phase === 'peak'
                ? 'Peak strength expression with low total volume, very specific low-rep work, and only enough accessory training to preserve muscle and movement quality.'
                : phase === 'intensification'
                    ? 'Shift resources toward more specific, lower-rep strength work while reducing accessory volume and avoiding unnecessary fatigue.'
                    : `Begin ${phaseLabel(phase).toLowerCase()} with a fresh evidence window while preserving successful exercise continuity where it remains compatible.`,
        recommendedNextPhase: undefined,
        recovery: undefined,
        workoutsInPhase: 0,
        minimumWorkouts,
        reviewAfterWorkouts,
        recoveryEntryPhase,
        recoveryExitEvidence: enteringRecovery ? 0 : undefined
    };
}
