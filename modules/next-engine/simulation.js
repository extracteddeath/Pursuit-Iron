import { generateProgram } from './generate.js';
import { normalizeRequest } from './prescription.js';
import { phasePolicyFor } from './phase-policy.js';
import { createExerciseMap, EXERCISE_MAP } from './exercise-db.js';
import { progressionInstruction, selectProgressionStyle } from './progression-style.js';
import { createTrainingSetEvents } from './events.js';
import { deriveMuscleLedger } from './ledgers.js';
import { auditProgram } from './arbiter.js';
import { estimateSessionMinutes, repsForPhase, rirForPhase, restForExercise } from './realizer.js';
import { transitionProgramPhase } from './phase-transition.js';
import { evaluateWorkoutProgression } from './performance.js';
import { resolveLoadingMode } from './loading.js';
const CYCLE_TEMPLATES = [
    { id: 'powerbuilding', name: 'Powerbuilding', goal: 'mixed', blocks: [
            { phase: 'hypertrophy_accumulation', label: 'Hypertrophy', weeks: 6 },
            { phase: 'strength_accumulation', label: 'Strength', weeks: 5 },
            { phase: 'peak', label: 'Peak', weeks: 3 }
        ] },
    { id: 'strength_peak', name: 'Strength Peak', goal: 'strength', blocks: [
            { phase: 'mixed_accumulation', label: 'Base', weeks: 4 },
            { phase: 'intensification', label: 'Intensify', weeks: 4 },
            { phase: 'peak', label: 'Realize', weeks: 2 }
        ] },
    { id: 'foundation', name: 'Beginner Foundation', goal: 'mixed', blocks: [
            { phase: 'foundation', label: 'Foundation', weeks: 4 },
            { phase: 'hypertrophy_accumulation', label: 'Grow', weeks: 5 },
            { phase: 'mixed_accumulation', label: 'Strengthen', weeks: 4 }
        ] },
    { id: 'hypertrophy_spec', name: 'Hypertrophy Specialization', goal: 'hypertrophy', blocks: [
            { phase: 'hypertrophy_accumulation', label: 'Volume Wave 1', weeks: 5 },
            { phase: 'hypertrophy_accumulation', label: 'Volume Wave 2', weeks: 5 }
        ] }
];
const DEFAULT_BLOCKS = CYCLE_TEMPLATES[0].blocks;
function hashString(value) {
    let hash = 2166136261;
    for (let i = 0; i < value.length; i++) {
        hash ^= value.charCodeAt(i);
        hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
}
function compactHash(value) {
    return hashString(value).toString(16).padStart(8, '0');
}
function initialLoad(exercise, request) {
    const mode = resolveLoadingMode(exercise.exerciseId, request.equipment.available, request.equipment.loading);
    const h = hashString(exercise.exerciseId + exercise.role);
    const primary = exercise.role === 'primary_strength';
    const secondary = exercise.role === 'secondary_strength';
    switch (mode) {
        case 'barbell_total': return 45 + 5 * ((primary ? 24 : secondary ? 16 : 10) + (h % 8));
        case 'smith_total': return 45 + 5 * ((primary ? 20 : secondary ? 14 : 9) + (h % 8));
        case 'dumbbell_per_hand': return 5 * ((primary ? 10 : secondary ? 8 : 5) + (h % 7));
        case 'machine_stack': return 10 * ((primary ? 10 : secondary ? 8 : 5) + (h % 8));
        case 'cable_stack': return 5 * ((primary ? 12 : secondary ? 10 : 5) + (h % 8));
        case 'bodyweight': return 0;
        case 'external_load': return 5 * (5 + (h % 8));
    }
}
function stateFor(exercise, request, states) {
    let state = states.get(exercise.exerciseId);
    if (!state) {
        state = {
            load: initialLoad(exercise, request),
            repTarget: exercise.prescription.reps[0],
            exposures: 0,
            positive: 0,
            holds: 0,
            reviews: 0,
            successfulExposures: 0,
            failedExposures: 0,
            constrainedReviews: 0,
            loadingBlockedExposures: 0,
            e1rmSamples: 0,
            firstE1rm: null,
            lastE1rm: null,
            name: exercise.name,
            role: exercise.role
        };
        states.set(exercise.exerciseId, state);
    }
    state.role = exercise.role;
    state.name = exercise.name;
    state.repTarget = Math.max(exercise.prescription.reps[0], Math.min(exercise.prescription.reps[1], state.repTarget));
    return state;
}
function isStallCandidate(exercise) {
    return exercise.role !== 'primary_strength' && exercise.role !== 'secondary_strength';
}
function chooseSyntheticStall(program, profile, blockIndex) {
    if (profile === 'steady' || profile === 'fast_responder')
        return new Set();
    const persistencePriority = ['cable_lateral_raise', 'preacher_curl', 'seated_leg_curl', 'cable_triceps', 'rear_delt_fly', 'neutral_pulldown', 'leg_press', 'cable_curl'];
    const candidates = program.sessions.flatMap(s => s.exercises)
        .filter(isStallCandidate)
        .filter((ex, i, arr) => arr.findIndex(x => x.exerciseId === ex.exerciseId) === i)
        .sort((a, b) => {
        const ai = persistencePriority.indexOf(a.exerciseId), bi = persistencePriority.indexOf(b.exerciseId);
        const ap = ai < 0 ? 99 : ai, bp = bi < 0 ? 99 : bi;
        return ap - bp || (hashString(`${blockIndex}:${a.exerciseId}`) - hashString(`${blockIndex}:${b.exerciseId}`)) || a.exerciseId.localeCompare(b.exerciseId);
    });
    const count = profile === 'fatigue_prone' ? Math.min(2, candidates.length) : Math.min(1, candidates.length);
    return new Set(candidates.slice(0, count).map(x => x.exerciseId));
}
function performedSetsFor(exercise, state, profile, forcedStall) {
    const [low, high] = exercise.prescription.reps;
    const [rirLow, rirHigh] = exercise.prescription.rir;
    const cycle = state.exposures;
    const naturallySlow = profile === 'mixed' && hashString(exercise.exerciseId) % 5 === 0;
    const fatigueHit = profile === 'fatigue_prone' && cycle > 0 && cycle % 4 === 3;
    const stall = forcedStall || naturallySlow && cycle % 3 === 2 || fatigueHit;
    let reps;
    let rir;
    if (stall) {
        reps = Math.max(1, low - (low > 1 ? 1 : 0));
        rir = Math.max(0, rirLow - 1);
    }
    else if (profile === 'fast_responder') {
        reps = high;
        rir = Math.max(rirLow, Math.min(rirHigh, rirLow));
    }
    else {
        reps = Math.max(low, Math.min(high, state.repTarget));
        rir = Math.max(rirLow, Math.min(rirHigh, rirLow + (reps < high ? 1 : 0)));
    }
    return Array.from({ length: exercise.sets }, (_, setIndex) => ({
        exerciseId: exercise.exerciseId,
        setIndex,
        load: state.load,
        reps,
        rir,
        techniqueQuality: 'good',
        painFlag: false,
        advancedTechnique: setIndex === exercise.sets - 1 ? exercise.advancedTechnique?.type : undefined
    }));
}
function simulateSession(session, request, states, profile, forcedStalls) {
    const performed = [];
    const performedByExercise = new Map();
    for (const exercise of session.exercises) {
        const state = stateFor(exercise, request, states);
        const sets = performedSetsFor(exercise, state, profile, forcedStalls.has(exercise.exerciseId));
        performedByExercise.set(exercise.exerciseId, sets);
        performed.push(...sets);
    }
    const decisions = evaluateWorkoutProgression(session, performed, {
        loadingInventory: request.equipment.loading,
        equipmentAvailable: request.equipment.available
    });
    for (const decision of decisions) {
        const exercise = session.exercises.find(x => x.exerciseId === decision.exerciseId);
        const state = stateFor(exercise, request, states);
        const actual = performedByExercise.get(exercise.exerciseId) ?? [];
        const completion = actual.length / Math.max(1, exercise.sets);
        const repsMet = actual.length > 0 && actual.every(set => set.reps >= exercise.prescription.reps[0]);
        const effortMet = actual.every(set => set.rir === null || set.rir >= exercise.prescription.rir[0]);
        // Longitudinal response classification must be based on what the athlete actually did, not on
        // whether a machine/bodyweight loading inventory happens to offer another increment. A top-of-
        // range bodyweight set can legitimately produce a UI `review` while still being a successful
        // training exposure. Counting those reviews as fatigue previously made fast responders look stalled.
        const successfulExposure = completion >= .75 && repsMet && effortMet;
        const failedExposure = completion < .75 || !repsMet || !effortMet;
        if (successfulExposure)
            state.successfulExposures++;
        if (failedExposure)
            state.failedExposures++;
        state.exposures++;
        if (decision.action === 'increase_load') {
            state.positive++;
            if (decision.suggestedLoad !== undefined && decision.suggestedLoad !== null)
                state.load = decision.suggestedLoad;
            state.repTarget = exercise.prescription.reps[0];
        }
        else if (decision.action === 'add_reps') {
            state.positive++;
            state.repTarget = Math.min(exercise.prescription.reps[1], Math.max(state.repTarget + 1, decision.suggestedReps ?? state.repTarget + 1));
        }
        else if (decision.action === 'decrease_load') {
            state.holds++;
            if (decision.suggestedLoad !== undefined && decision.suggestedLoad !== null)
                state.load = decision.suggestedLoad;
            state.repTarget = exercise.prescription.reps[0];
        }
        else if (decision.action === 'hold')
            state.holds++;
        else {
            state.reviews++;
            if (successfulExposure)
                state.constrainedReviews++;
        }
        if (decision.outcome === 'success_blocked')
            state.loadingBlockedExposures++;
        if (decision.estimated1RM !== undefined && decision.estimated1RM !== null) {
            state.e1rmSamples++;
            if (state.firstE1rm === null)
                state.firstE1rm = decision.estimated1RM;
            state.lastE1rm = decision.estimated1RM;
        }
    }
    return decisions;
}
function actionCounter() {
    return { increase_load: 0, add_reps: 0, decrease_load: 0, hold: 0, review: 0 };
}
function metrics(program) {
    const exercises = program.sessions.flatMap(s => s.exercises);
    const weeklySets = exercises.reduce((sum, ex) => sum + ex.sets, 0);
    const strength = exercises.filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength');
    const accessory = exercises.filter(ex => ex.role !== 'primary_strength' && ex.role !== 'secondary_strength');
    const strengthSets = strength.reduce((sum, ex) => sum + ex.sets, 0);
    const accessorySets = weeklySets - strengthSets;
    const weightedRep = exercises.reduce((sum, ex) => sum + ex.sets * ((ex.prescription.reps[0] + ex.prescription.reps[1]) / 2), 0);
    const weightedStrengthRep = strength.reduce((sum, ex) => sum + ex.sets * ((ex.prescription.reps[0] + ex.prescription.reps[1]) / 2), 0);
    const weightedAccessoryRep = accessory.reduce((sum, ex) => sum + ex.sets * ((ex.prescription.reps[0] + ex.prescription.reps[1]) / 2), 0);
    const weightedRir = exercises.reduce((sum, ex) => sum + ex.sets * ((ex.prescription.rir[0] + ex.prescription.rir[1]) / 2), 0);
    const minutes = program.sessions.map(s => s.estimatedMinutes);
    return {
        weeklySets,
        strengthSets,
        accessorySets,
        exerciseSlots: exercises.length,
        avgExercisesPerSession: program.sessions.length ? Number((exercises.length / program.sessions.length).toFixed(1)) : 0,
        avgRepTarget: weeklySets ? Number((weightedRep / weeklySets).toFixed(1)) : 0,
        avgStrengthRepTarget: strengthSets ? Number((weightedStrengthRep / strengthSets).toFixed(1)) : 0,
        avgAccessoryRepTarget: accessorySets ? Number((weightedAccessoryRep / accessorySets).toFixed(1)) : 0,
        avgRirTarget: weeklySets ? Number((weightedRir / weeklySets).toFixed(1)) : 0,
        avgSessionMinutes: minutes.length ? Number((minutes.reduce((a, b) => a + b, 0) / minutes.length).toFixed(1)) : 0,
        peakSessionMinutes: minutes.length ? Math.max(...minutes) : 0,
        advancedTechniqueCount: exercises.filter(ex => !!ex.advancedTechnique).length,
        strengthSetShare: weeklySets ? Number((strengthSets / weeklySets).toFixed(2)) : 0
    };
}
function exerciseNames(program) {
    return new Map(program.sessions.flatMap(s => s.exercises.map(ex => [ex.exerciseId, ex.name])));
}
function programDiff(previous, next) {
    const priorNames = exerciseNames(previous);
    const nextNames = exerciseNames(next);
    const priorIds = new Set(priorNames.keys());
    const nextIds = new Set(nextNames.keys());
    const retained = [...priorIds].filter(id => nextIds.has(id)).map(id => priorNames.get(id)).sort();
    const added = [...nextIds].filter(id => !priorIds.has(id)).map(id => nextNames.get(id)).sort();
    const removed = [...priorIds].filter(id => !nextIds.has(id)).map(id => priorNames.get(id)).sort();
    const a = metrics(previous), b = metrics(next);
    return {
        retained,
        added,
        removed,
        weeklySetDelta: b.weeklySets - a.weeklySets,
        strengthSetDelta: b.strengthSets - a.strengthSets,
        avgRepDelta: Number((b.avgRepTarget - a.avgRepTarget).toFixed(1)),
        avgRirDelta: Number((b.avgRirTarget - a.avgRirTarget).toFixed(1))
    };
}
function counterSnapshot(states) {
    return new Map([...states.entries()].map(([id, state]) => [id, {
        exposures: state.exposures, positive: state.positive, holds: state.holds, reviews: state.reviews,
        successfulExposures: state.successfulExposures, failedExposures: state.failedExposures, constrainedReviews: state.constrainedReviews,
        loadingBlockedExposures: state.loadingBlockedExposures ?? 0, e1rmSamples: state.e1rmSamples ?? 0
    }]));
}
function blockResponse(program, states, actionCounts, before) {
    const names = exerciseNames(program);
    const successful = [];
    const stalled = [];
    const progressionEvidenceByExercise = {};
    let totalPerformanceFailures = 0, totalPerformanceExposures = 0;
    for (const [id, state] of states) {
        if (!names.has(id))
            continue;
        const start = before.get(id) ?? { exposures: 0, positive: 0, holds: 0, reviews: 0, successfulExposures: 0, failedExposures: 0, constrainedReviews: 0, loadingBlockedExposures: 0, e1rmSamples: 0 };
        const exposures = state.exposures - start.exposures;
        if (exposures <= 0)
            continue;
        const successfulExposures = state.successfulExposures - start.successfulExposures;
        const failedExposures = state.failedExposures - start.failedExposures;
        const successRate = successfulExposures / exposures;
        const failureRate = failedExposures / exposures;
        totalPerformanceFailures += failedExposures;
        totalPerformanceExposures += exposures;
        const successfulTrend = successRate >= .65 && failureRate < .25;
        const stalledTrend = failureRate >= .35;
        progressionEvidenceByExercise[id] = {
            comparableExposures: exposures,
            styleExposures: exposures,
            failureCount: failedExposures,
            stallCount: stalledTrend ? failedExposures : 0,
            loadingBlockedCount: Math.max(0, (state.loadingBlockedExposures ?? 0) - (start.loadingBlockedExposures ?? 0)),
            // Synthetic performed sets always contain an observed RIR, so the simulator can make the
            // same evidence-quality decision as production instead of relying on selector defaults.
            rirCoverage: 1,
            e1rmSamples: Math.max(0, (state.e1rmSamples ?? 0) - (start.e1rmSamples ?? 0)),
            successful: successfulTrend
        };
        if (successfulTrend)
            successful.push(id);
        if (isStallCandidate({ role: state.role }) && stalledTrend)
            stalled.push(id);
    }
    // Primary strength exercises remain protected across blocks even if a noisy synthetic exposure occurred.
    for (const ex of program.sessions.flatMap(s => s.exercises).filter(ex => ex.role === 'primary_strength')) {
        if (!successful.includes(ex.exerciseId))
            successful.push(ex.exerciseId);
    }
    const actionTotal = Object.values(actionCounts).reduce((sum, value) => sum + value, 0);
    const progressionTotal = actionCounts.increase_load + actionCounts.add_reps;
    const progressionRate = actionTotal ? progressionTotal / actionTotal : 0;
    const holdRate = actionTotal ? (actionCounts.hold + actionCounts.decrease_load) / actionTotal : 0;
    const reviewRate = actionTotal ? actionCounts.review / actionTotal : 0;
    // A fatigue-limited block should be detectable even when the progression layer correctly
    // chooses conservative holds rather than escalating every poor exposure to a review.
    // Requiring repeated stalled exercises prevents an isolated hard session from shrinking
    // the next block, while the hold-rate branch makes the synthetic fatigue-prone profile
    // materially different from an ordinary mixed responder.
    const performanceFailureRate = totalPerformanceExposures ? totalPerformanceFailures / totalPerformanceExposures : 0;
    const fatiguePattern = performanceFailureRate >= .18 && stalled.length >= 2;
    const classification = fatiguePattern
        ? 'fatigue_limited'
        : stalled.length >= Math.max(2, Math.ceil(names.size * .2))
            ? 'stalled'
            : performanceFailureRate <= .08 && progressionRate >= .45
                ? 'productive'
                : 'mixed';
    const fatigueLimitedExerciseIds = classification === 'fatigue_limited'
        ? Object.entries(progressionEvidenceByExercise).filter(([, row]) => Number(row.failureCount) > 0).map(([id]) => id).sort()
        : [];
    for (const id of fatigueLimitedExerciseIds)
        progressionEvidenceByExercise[id] = { ...progressionEvidenceByExercise[id], fatigueLimited: true };
    return {
        successfulExerciseIds: [...new Set(successful)].sort(),
        stalledExerciseIds: [...new Set(stalled)].sort(),
        fatigueLimitedExerciseIds,
        techniqueLimitedExerciseIds: [],
        progressionEvidenceByExercise,
        successfulExerciseNames: [...new Set(successful)].map(id => names.get(id) ?? id).sort(),
        stalledExerciseNames: [...new Set(stalled)].map(id => names.get(id) ?? id).sort(),
        progressionActions: actionCounts,
        progressionRate: Number(progressionRate.toFixed(2)),
        holdRate: Number(holdRate.toFixed(2)),
        reviewRate: Number(reviewRate.toFixed(2)),
        classification
    };
}
function requestWithAvoided(request, avoided) {
    return {
        ...request,
        preferences: {
            ...(request.preferences ?? {}),
            avoidedExercises: [...new Set([...(request.preferences?.avoidedExercises ?? []), ...avoided])]
        }
    };
}
function requestWithResponseAdaptation(request, response) {
    const avoided = [...(request.preferences?.avoidedExercises ?? []), ...response.stalledExerciseIds];
    const reduceCapacity = response.classification === 'fatigue_limited';
    return {
        ...request,
        schedule: { days: request.schedule.days.map(day => ({
                ...day,
                // Apply a meaningful capacity reduction before the target phase applies its own
                // session-capacity multiplier. A flat -1 was frequently rounded away (e.g. 7→6
                // then both becoming 5 in strength accumulation), so fatigue history could claim
                // to adapt without changing the realized program.
                targetExercises: day.targetExercises === undefined ? undefined : Math.max(2, day.targetExercises - (reduceCapacity ? Math.max(1, Math.ceil(day.targetExercises * .2)) : 0))
            })) },
        preferences: {
            ...(request.preferences ?? {}),
            avoidedExercises: [...new Set(avoided)],
            responseCapacityScale: reduceCapacity ? .82 : (request.preferences?.responseCapacityScale ?? 1)
        }
    };
}
function designForPhase(phase, adaptBetweenBlocks) {
    const policy = phasePolicyFor(phase);
    const adaptive = (adaptiveText, staticText) => adaptBetweenBlocks ? adaptiveText : staticText;
    switch (phase) {
        case 'foundation': return {
            focus: 'Build movement skill, repeatable training tolerance, and a stable base without chasing fatigue.',
            volumeIntent: 'moderate', exerciseVarietyIntent: 'moderate', mainLiftRepIntent: '4–8 reps', accessoryRepIntent: '8–20 reps',
            specificityIntent: 'moderate', advancedTechniqueBudget: policy.advancedTechniqueBudget,
            structuralAdaptation: adaptive('Re-solve conservatively around movement quality, tolerance, and successful exercise fit.', 'Keep the exercise skeleton stable while gradually building dose and skill.')
        };
        case 'hypertrophy_accumulation': return {
            focus: 'Accumulate muscle-building volume while keeping the competition-pattern lifts practiced.',
            volumeIntent: 'high', exerciseVarietyIntent: 'high', mainLiftRepIntent: '4–8 reps', accessoryRepIntent: '6–20+ reps',
            specificityIntent: 'moderate', advancedTechniqueBudget: policy.advancedTechniqueBudget,
            structuralAdaptation: adaptive('Full re-optimization: exercise count, set allocation, and exercise selection may change from prior history.', 'Structure locked after Block 1; phase prescription changes without re-solving exercise identity.')
        };
        case 'mixed_accumulation': return {
            focus: 'Develop strength and hypertrophy together with enough specificity to progress the main lifts without crowding out productive accessory work.',
            volumeIntent: 'moderate', exerciseVarietyIntent: 'moderate', mainLiftRepIntent: '3–8 reps', accessoryRepIntent: '6–20 reps',
            specificityIntent: 'high', advancedTechniqueBudget: policy.advancedTechniqueBudget,
            structuralAdaptation: adaptive('Re-balance strength and hypertrophy resources around recent response while protecting successful main lifts.', 'Keep the prior skeleton and retarget the mixed strength/hypertrophy prescription.')
        };
        case 'strength_accumulation': return {
            focus: 'Shift resources toward heavier, more specific strength work while preserving enough hypertrophy work to maintain muscle.',
            volumeIntent: 'moderate', exerciseVarietyIntent: 'moderate', mainLiftRepIntent: '2–6 reps', accessoryRepIntent: '6–15 reps',
            specificityIntent: 'high', advancedTechniqueBudget: policy.advancedTechniqueBudget,
            structuralAdaptation: adaptive('Re-solve the block around strength specificity, successful movements, and prior-block response.', 'Keep the prior exercise skeleton and reduce/retarget its dose.')
        };
        case 'intensification': return {
            focus: 'Increase main-lift specificity and loading while deliberately trimming accessory fatigue and redundant volume.',
            volumeIntent: 'low', exerciseVarietyIntent: 'low', mainLiftRepIntent: '2–5 reps', accessoryRepIntent: '6–15 reps',
            specificityIntent: 'very_high', advancedTechniqueBudget: policy.advancedTechniqueBudget,
            structuralAdaptation: adaptive('Re-solve around high-value strength work, continuity, and a tighter fatigue budget.', 'Keep the existing skeleton but lower accessory dose and main-lift rep targets.')
        };
        case 'peak': return {
            focus: 'Express strength with high specificity and sharply reduced fatigue; only assistance that earns its place remains.',
            volumeIntent: 'very_low', exerciseVarietyIntent: 'low', mainLiftRepIntent: '1–4 reps', accessoryRepIntent: '6–15 reps',
            specificityIntent: 'very_high', advancedTechniqueBudget: policy.advancedTechniqueBudget,
            structuralAdaptation: adaptive('Re-solve aggressively for specificity and fatigue reduction while retaining successful main lifts.', 'Keep the prior skeleton but cut sets and move primary lifts into peak rep ranges.')
        };
        case 'maintenance': return {
            focus: 'Preserve strength and muscle with the minimum practical training cost while keeping successful movement patterns familiar.',
            volumeIntent: 'low', exerciseVarietyIntent: 'low', mainLiftRepIntent: '3–8 reps', accessoryRepIntent: '6–15 reps',
            specificityIntent: 'moderate', advancedTechniqueBudget: policy.advancedTechniqueBudget,
            structuralAdaptation: adaptive('Keep only movements that continue to justify their recovery cost.', 'Preserve the current skeleton and reduce dose.')
        };
        case 'recovery': return {
            focus: 'Reduce local and systemic fatigue while maintaining enough movement practice to make the return to development smooth.',
            volumeIntent: 'very_low', exerciseVarietyIntent: 'low', mainLiftRepIntent: '4–8 reps', accessoryRepIntent: '8–20 reps',
            specificityIntent: 'moderate', advancedTechniqueBudget: policy.advancedTechniqueBudget,
            structuralAdaptation: adaptive('Remove avoidable fatigue sources while preferentially retaining well-tolerated movement patterns.', 'Preserve familiar movements at a sharply reduced dose.')
        };
    }
}
function retargetProgramWithoutStructuralAdaptation(previous, request, target, blockWeeks = 6) {
    const sourcePolicy = phasePolicyFor(previous.phase);
    const targetPolicy = phasePolicyFor(target);
    const exerciseMap = createExerciseMap(request.customExercises);
    const sessions = previous.sessions.map(session => {
        const exercises = session.exercises.map(ex => {
            const def = exerciseMap.get(ex.exerciseId);
            if (!def)
                throw new Error(`Cannot retarget unknown exercise ${ex.exerciseId}.`);
            const strength = ex.role === 'primary_strength' || ex.role === 'secondary_strength';
            const sourceScale = strength ? sourcePolicy.strengthVolumeMultiplier : sourcePolicy.volumeMultiplier;
            const targetScale = strength ? targetPolicy.strengthVolumeMultiplier : targetPolicy.volumeMultiplier;
            const ratio = sourceScale > 0 ? targetScale / sourceScale : 1;
            const minimumSets = strength ? 1 : 1;
            const workingSetCap = request.preferences.volumeApproach === 'minimalist' ? 3 : ex.workingSetCap;
            const sets = Math.min(workingSetCap ?? Infinity, Math.max(minimumSets, Math.round(ex.sets * ratio)));
            const prescription = {
                reps: repsForPhase(def, ex.role, targetPolicy),
                rir: rirForPhase(ex.role, targetPolicy),
                restSeconds: restForExercise(ex.role, def)
            };
            const progressionSelection = selectProgressionStyle(def, ex.role, {
                phase: target,
                experience: request.athlete.experience,
                blockWeeks: Math.max(1, Number(blockWeeks) || 6),
                requestedStyle: request.preferences?.progressionStyle,
                prescription
            });
            const previousStyle = ex.progressionStyle ?? null;
            return {
                ...ex,
                sets,
                ...(workingSetCap !== undefined ? { workingSetCap } : {}),
                prescription,
                progressionStyle: progressionSelection.style,
                progression: progressionInstruction(progressionSelection.style),
                progressionSelection: {
                    source: progressionSelection.source,
                    confidence: progressionSelection.confidence,
                    reason: progressionSelection.reason,
                    previousStyle,
                    changed: previousStyle !== null ? previousStyle !== progressionSelection.style : false
                },
                advancedTechnique: targetPolicy.advancedTechniqueBudget > 0 ? ex.advancedTechnique : undefined
            };
        });
        return { ...session, exercises, estimatedMinutes: estimateSessionMinutes(exercises) };
    });
    const events = createTrainingSetEvents(sessions, request.customExercises);
    const muscleLedger = deriveMuscleLedger(events);
    const base = { ...previous, phase: target, sessions, events, muscleLedger, rationale: [...previous.rationale, `Adapt Between Blocks OFF: preserved the existing exercise skeleton while applying the ${target.replaceAll('_', ' ')} dose and rep policy.`] };
    const { audit: _audit, ...auditable } = base;
    void _audit;
    const audit = auditProgram(auditable, request);
    if (audit.result === 'reject')
        throw new Error(`Static block retarget failed audit: ${audit.findings.map(f => f.code).join(', ')}`);
    return { ...base, audit };
}
function historyCarryover(program, states) {
    const ids = new Set(program.sessions.flatMap(s => s.exercises.map(ex => ex.exerciseId)));
    const returning = [...states.entries()].filter(([id, state]) => ids.has(id) && state.exposures > 0);
    const strengthIds = new Set(program.sessions.flatMap(s => s.exercises.filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength').map(ex => ex.exerciseId)));
    return {
        returningExercises: returning.length,
        returningStrengthExercises: returning.filter(([id]) => strengthIds.has(id)).length,
        carriedLoads: returning.filter(([id]) => strengthIds.has(id)).map(([exerciseId, state]) => ({ exerciseId, exerciseName: state.name, load: state.load })).sort((a, b) => a.exerciseName.localeCompare(b.exerciseName))
    };
}
function clampRange(range, delta) {
    const low = Math.max(1, range[0] + delta);
    const high = Math.max(low, range[1] + delta);
    return [low, high];
}
function weekModifier(phase, week, totalWeeks, role) {
    const p = totalWeeks <= 1 ? 1 : (week - 1) / (totalWeeks - 1);
    const strength = role === 'primary_strength' || role === 'secondary_strength';
    switch (phase) {
        case 'hypertrophy_accumulation': {
            // Accumulate useful work, then consolidate before the strength block rather than adding sets forever.
            const final = totalWeeks >= 5 && week === totalWeeks;
            return { setFactor: final ? .82 : (.9 + .18 * p), repShift: strength && p > .65 ? -1 : 0, rirShift: week <= 2 ? 1 : 0 };
        }
        case 'strength_accumulation':
            return { setFactor: strength ? (1 - .08 * p) : (.92 - .2 * p), repShift: strength ? -Math.round(2 * p) : 0, rirShift: week === 1 ? 1 : 0 };
        case 'intensification':
            return { setFactor: strength ? (.95 - .12 * p) : (.78 - .28 * p), repShift: strength ? -Math.round(p) : 0, rirShift: strength ? 0 : 1 };
        case 'peak':
            return { setFactor: strength ? (1 - .28 * p) : (.65 - .35 * p), repShift: strength ? -Math.round(p) : 0, rirShift: strength ? 0 : 1 };
        case 'foundation':
            return { setFactor: .82 + .18 * p, repShift: 0, rirShift: p < .5 ? 1 : 0 };
        case 'mixed_accumulation':
            return { setFactor: strength ? (1 - .04 * p) : (.92 - .08 * p), repShift: strength && p > .6 ? -1 : 0, rirShift: week === 1 ? 1 : 0 };
        case 'maintenance': return { setFactor: .9, repShift: 0, rirShift: 1 };
        case 'recovery': return { setFactor: .7, repShift: 0, rirShift: 2 };
    }
}
/** Exact prescription presented to the simulator for a specific week. This is a view over the block program, not a mutation of it. */
/* ⚠ THE PROGRESSION STYLE MOVES THROUGH THE BLOCK, AS IN v661. The weekly modifier already makes a strength block
   heavier week by week (reps shift down, effort up), but the style stayed fixed: a 6-week strength block's main lift was
   `double` from week 1 to week 6. v661 (autoStyleDetail, position = (week-1)/(weeks-1), 0.5 for a single week):
     strength PRIMARY compound — block <= 4 weeks: accumulate, then e1RM from halfway (too short to complete a hi/mid/lo
                                 wave, which needs ~3 exposures); longer: accumulate < 0.35, WAVE 0.35-0.75, e1RM >= 0.75;
     strength SECONDARY compound — accumulate, then e1RM from 0.65.
   Only inside accumulation-type blocks: in a cycle's intensification or peak block the block-level style (resolved from the
   phase) already encodes position, so a week schedule on top would count it twice. Beginners keep linear throughout, and
   everything that is not a strength-role compound keeps its block style. */
const ACCUMULATION_PHASES = new Set(['foundation', 'hypertrophy_accumulation', 'mixed_accumulation', 'strength_accumulation', 'maintenance']);
export function weeklyProgressionStyle(ex, phase, week, totalWeeks) {
    const base = ex.progressionStyle;
    // Manual means manual for the actual weekly shell prescription too. The legacy within-block
    // schedule may evolve Auto strength work, but it must never silently replace an explicit method.
    if (ex.progressionSelection?.source === 'manual')
        return base;
    if (!base || base === 'linear' || !ACCUMULATION_PHASES.has(phase))
        return base;
    if (ex.role !== 'primary_strength' && ex.role !== 'secondary_strength')
        return base;
    const def = EXERCISE_MAP.get(ex.exerciseId);
    if (!def || !def.flags.compound)
        return base;
    const position = totalWeeks <= 1 ? 0.5 : Math.min(1, Math.max(0, (week - 1) / (totalWeeks - 1)));
    if (ex.role === 'secondary_strength')
        return position >= 0.65 ? 'e1rm' : base;
    if (totalWeeks <= 4)
        return position >= 0.5 ? 'e1rm' : base;
    return position >= 0.75 ? 'e1rm' : position >= 0.35 ? 'wave' : base;
}
/* ⚠ THE WEEK'S SET FACTOR APPLIES TO THE SESSION, NOT TO EACH EXERCISE. It was `round(ex.sets * factor)` per exercise; with
   the usual 3-4 sets an exercise, any factor from ~0.88 to ~1.12 rounds back to the same count — so the designed hypertrophy
   ramp (0.90 -> 1.08 of base volume) never reached the lifter (measured: 86 sets every week, weeks 1-5), and cuts were
   exaggerated (the 0.82 consolidation week became -25%). Now the session's intended total (sum of ex.sets x factor) is rounded
   once and the sets are shared out by largest remainder (ties to the earlier exercise), minimums (2 for strength roles, else 1)
   still winning. The week's volume is what the phase policy says. */
function allocateWeekSets(exercises, factors) {
    const raw = exercises.map((ex, i) => ex.sets * Math.max(.25, factors[i]));
    const mins = exercises.map(ex => (ex.role === 'primary_strength' || ex.role === 'secondary_strength') ? 2 : 1);
    const caps = exercises.map((ex, i) => Math.max(mins[i], ex.workingSetCap ?? Infinity));
    const out = raw.map((r, i) => Math.min(caps[i], Math.max(mins[i], Math.floor(r))));
    let left = Math.round(raw.reduce((a, b) => a + b, 0)) - out.reduce((a, b) => a + b, 0);
    const order = raw.map((r, i) => ({ i, frac: r - Math.floor(r) })).sort((a, b) => b.frac - a.frac || a.i - b.i);
    while (left > 0) {
        const eligible = order.filter(({ i }) => out[i] < caps[i]);
        if (!eligible.length) break;
        for (const { i } of eligible) {
            if (left <= 0) break;
            out[i]++; left--;
        }
    }
    return out;
}
export function prescriptionForSimulationWeek(session, phase, week, totalWeeks) {
    const modifiers = session.exercises.map(ex => weekModifier(phase, week, totalWeeks, ex.role));
    const weekSets = allocateWeekSets(session.exercises, modifiers.map(m => m.setFactor));
    const exercises = session.exercises.map((ex, idx) => {
        const modifier = modifiers[idx];
        const sets = weekSets[idx];
        const reps = clampRange(ex.prescription.reps, modifier.repShift);
        const rir = [
            Math.max(0, Math.min(6, ex.prescription.rir[0] + modifier.rirShift)),
            Math.max(0, Math.min(6, ex.prescription.rir[1] + modifier.rirShift))
        ];
        if (rir[1] < rir[0])
            rir[1] = rir[0];
        const progressionStyle = weeklyProgressionStyle(ex, phase, week, totalWeeks);
        const progression = progressionStyle && progressionStyle !== ex.progressionStyle && progressionStyle !== 'auto' ? progressionInstruction(progressionStyle) : ex.progression;
        return { ...ex, sets, prescription: { ...ex.prescription, reps, rir }, progressionStyle, progression };
    });
    return { ...session, exercises, estimatedMinutes: estimateSessionMinutes(exercises) };
}
function summarizeSimulationWeek(sessions) {
    const all = sessions.flatMap(s => s.exercises);
    const sets = all.reduce((n, e) => n + e.sets, 0);
    const strength = all.filter(e => e.role === 'primary_strength' || e.role === 'secondary_strength');
    const strengthSets = strength.reduce((n, e) => n + e.sets, 0);
    const weighted = (items, selector) => {
        const denom = items.reduce((n, e) => n + e.sets, 0) || 1;
        return Math.round(items.reduce((n, e) => n + selector(e) * e.sets, 0) / denom * 10) / 10;
    };
    const minutes = sessions.map(session => session.estimatedMinutes);
    return { weeklySets: sets, strengthSets, accessorySets: sets - strengthSets, avgRepTarget: weighted(all, e => (e.prescription.reps[0] + e.prescription.reps[1]) / 2), avgStrengthRepTarget: weighted(strength, e => (e.prescription.reps[0] + e.prescription.reps[1]) / 2), avgRirTarget: weighted(all, e => (e.prescription.rir[0] + e.prescription.rir[1]) / 2), totalMinutes: minutes.reduce((n, value) => n + value, 0), peakSessionMinutes: minutes.length ? Math.max(...minutes) : 0 };
}
function simulateBlock(index, spec, program, request, profile, states) {
    const before = counterSnapshot(states);
    const weeks = [];
    const totalActions = actionCounter();
    const forcedStalls = chooseSyntheticStall(program, profile, index);
    for (let week = 1; week <= spec.weeks; week++) {
        const workouts = [];
        const weekActions = actionCounter();
        const weeklySessions = program.sessions.map(baseSession => prescriptionForSimulationWeek(baseSession, spec.phase, week, spec.weeks));
        for (let sessionIndex = 0; sessionIndex < weeklySessions.length; sessionIndex++) {
            const session = weeklySessions[sessionIndex];
            const decisions = simulateSession(session, request, states, profile, forcedStalls);
            for (const decision of decisions) {
                weekActions[decision.action]++;
                totalActions[decision.action]++;
            }
            workouts.push({ week, sessionIndex, day: session.day, sessionName: session.name, estimatedMinutes: session.estimatedMinutes, prescription: session.exercises.map(ex => ({ exerciseId: ex.exerciseId, exerciseName: ex.name, role: ex.role, sets: ex.sets, reps: [...ex.prescription.reps], rir: [...ex.prescription.rir] })), decisions });
        }
        const topStrengthEstimates = [...states.entries()]
            .filter(([, state]) => (state.role === 'primary_strength' || state.role === 'secondary_strength') && state.lastE1rm !== null)
            .map(([exerciseId, state]) => ({ exerciseId, exerciseName: state.name, estimated1RM: state.lastE1rm }))
            .sort((a, b) => b.estimated1RM - a.estimated1RM)
            .slice(0, 6);
        weeks.push({ week, workouts, prescription: summarizeSimulationWeek(weeklySessions), actionCounts: weekActions, topStrengthEstimates });
    }
    return { weeks, response: blockResponse(program, states, totalActions, before) };
}
export function runPowerbuildingSimulation(options) {
    const specs = (options.blocks?.length ? options.blocks : DEFAULT_BLOCKS).map(block => ({ ...block, weeks: Math.max(1, Math.round(block.weeks)) }));
    const profile = options.responseProfile ?? 'mixed';
    const blocks = [];
    const states = new Map();
    let priorProgram;
    let priorResponse;
    let cumulativeAvoided = [];
    for (let index = 0; index < specs.length; index++) {
        const spec = specs[index];
        let baseRequest = options.request;
        if (options.adaptBetweenBlocks && priorResponse) {
            cumulativeAvoided = [...new Set([...cumulativeAvoided, ...priorResponse.stalledExerciseIds])];
            baseRequest = requestWithResponseAdaptation(requestWithAvoided(options.request, cumulativeAvoided), priorResponse);
        }
        const normalized = normalizeRequest(baseRequest);
        let program;
        let continuity;
        const notes = [];
        const design = designForPhase(spec.phase, options.adaptBetweenBlocks);
        if (!priorProgram) {
            program = generateProgram(baseRequest, {
                phase: spec.phase,
                blockWeeks: spec.weeks,
                progressionStyle: baseRequest.preferences?.progressionStyle
            }).program;
            notes.push(`Started ${spec.label} from the athlete's cycle contract.`);
            notes.push(design.focus);
        }
        else if (options.adaptBetweenBlocks) {
            const result = transitionProgramPhase(priorProgram, normalized, spec.phase, {
                successfulExerciseIds: priorResponse?.successfulExerciseIds ?? [],
                protectedExerciseIds: priorProgram.sessions.flatMap(s => s.exercises).filter(ex => ex.role === 'primary_strength').map(ex => ex.exerciseId),
                fatigueLimitedExerciseIds: priorResponse?.fatigueLimitedExerciseIds ?? [],
                techniqueLimitedExerciseIds: priorResponse?.techniqueLimitedExerciseIds ?? [],
                progressionEvidenceByExercise: priorResponse?.progressionEvidenceByExercise ?? {},
                nextBlockWeeks: spec.weeks
            });
            program = result.program;
            continuity = result.continuity;
            notes.push(`Block goal re-solved for ${spec.label}: sets, exercise count, exercise selection, reps, RIR, and fatigue budget are allowed to change.`);
            if (priorResponse?.stalledExerciseNames.length)
                notes.push(`Prior response excluded ${priorResponse.stalledExerciseNames.join(', ')} after repeated simulated stalls.`);
            if (priorResponse?.classification === 'fatigue_limited')
                notes.push('Prior block was fatigue-limited, so next-block session-capacity targets were reduced by roughly 20% before phase-specific scaling.');
            else if (priorResponse?.classification === 'productive')
                notes.push('Prior block was productive, so no extra volume was added automatically; successful movements received continuity preference.');
            notes.push(result.continuity.rationale);
        }
        else {
            program = retargetProgramWithoutStructuralAdaptation(priorProgram, normalized, spec.phase, spec.weeks);
            notes.push(`Adapt Between Blocks is off: ${spec.label} keeps the prior block's exercise skeleton.`);
            notes.push('Only phase-appropriate set dose, reps, RIR, rest, and technique eligibility are retargeted; exercise identity is intentionally held stable.');
        }
        if (program.audit.result === 'reject')
            throw new Error(`${spec.label} program was rejected: ${program.audit.findings.map(f => f.code).join(', ')}`);
        const carryover = historyCarryover(program, states);
        if (index > 0 && carryover.returningExercises)
            notes.push(`${carryover.returningExercises} exercises entered this block with simulated training history; ${carryover.returningStrengthExercises} were strength-role movements with carried load state.`);
        const simulated = simulateBlock(index, spec, program, normalized, profile, states);
        blocks.push({
            index,
            spec,
            program,
            metrics: metrics(program),
            weeks: simulated.weeks,
            response: simulated.response,
            design,
            historyCarryover: carryover,
            continuity,
            diffFromPrevious: priorProgram ? programDiff(priorProgram, program) : undefined,
            adaptationNotes: notes
        });
        priorProgram = program;
        priorResponse = simulated.response;
    }
    const totalWeeks = specs.reduce((sum, block) => sum + block.weeks, 0);
    const signature = JSON.stringify({
        request: options.request,
        adaptBetweenBlocks: options.adaptBetweenBlocks,
        responseProfile: profile,
        blocks: blocks.map(block => ({
            phase: block.spec.phase,
            sessions: block.program.sessions.map(s => s.exercises.map(ex => [ex.exerciseId, ex.sets, ex.prescription.reps, ex.prescription.rir])),
            response: block.response.progressionActions,
            carryover: block.historyCarryover.carriedLoads
        }))
    });
    const fingerprint = compactHash(signature);
    return {
        id: `sim-${fingerprint}`,
        fingerprint,
        adaptBetweenBlocks: options.adaptBetweenBlocks,
        responseProfile: profile,
        totalWeeks,
        request: options.request,
        blocks
    };
}
export function cycleTemplates() {
    return CYCLE_TEMPLATES.map(template => ({ ...template, blocks: template.blocks.map(block => ({ ...block })) }));
}
export function goalForCycleTemplate(id) {
    return (CYCLE_TEMPLATES.find(template => template.id === id) ?? CYCLE_TEMPLATES[0]).goal;
}
export function blocksForCycleTemplate(id) {
    return (CYCLE_TEMPLATES.find(template => template.id === id) ?? CYCLE_TEMPLATES[0]).blocks.map(block => ({ ...block }));
}
