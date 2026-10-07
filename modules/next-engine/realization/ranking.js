/** Session realization: ranking. Maintained production source. */
import { MUSCLE_MOVEMENTS, equipmentEligible, maxBarbells } from './rules.js';
import { armCoverageBias } from '../arm-coverage.js';
import { avoidableExerciseOverlap } from '../exercise-economy.js';
import { exerciseSetupInefficiencyPenalty } from '../exercise-selection-intelligence.js';
import { functionalCoverageBiases } from '../functional-coverage.js';

function strengthCandidate(a, session, request, chosen, catalog) {
    const barbells = chosen.filter(e => e.flags.barbell).length;
    const candidates = catalog.filter(e => equipmentEligible(e, session, request))
        .filter(e => !request.preferences.avoidedExercises?.includes(e.id))
        .filter(e => (e.liftSpecificity?.[a.lift] ?? 0) > .45)
        .filter(e => !e.flags.barbell || barbells < maxBarbells(session, request));
    return candidates.sort((x, y) => {
        const xs = x.liftSpecificity?.[a.lift] ?? 0, ys = y.liftSpecificity?.[a.lift] ?? 0;
        if (a.role === 'secondary_strength') {
            // Volume/secondary exposures should retain transfer without blindly repeating the highest-fatigue
            // competition lift. This makes paused bench / RDL-style secondary work viable when specificity
            // remains meaningful, while exact competition lifts still dominate primary-strength slots.
            const fatigue = (e) => e.fatigue.systemic + e.fatigue.axial * .8 + e.fatigue.lowerBack;
            const xScore = xs * 5 + x.loadability * .12 + x.suitability.strength * .1 - fatigue(x) * .13;
            const yScore = ys * 5 + y.loadability * .12 + y.suitability.strength * .1 - fatigue(y) * .13;
            if (Math.abs(yScore - xScore) > .02)
                return yScore - xScore;
        }
        const specificity = ys - xs;
        if (Math.abs(specificity) > .02)
            return specificity;
        const confidence = (x.source === 'legacy_v661' ? 1 : 0) - (y.source === 'legacy_v661' ? 1 : 0);
        return confidence || y.loadability - x.loadability || x.id.localeCompare(y.id);
    })[0];
}

function muscleScore(ex, muscle, role, session, chosen, weeklyMovementUse, request) {
    const credit = ex.muscles[muscle]?.credit ?? 0;
    if (!credit)
        return -999;
    const sameSession = chosen.filter(c => c.movementFamily === ex.movementFamily).length;
    const weeklyRepeat = weeklyMovementUse.get(ex.movementFamily) ?? 0;
    const exactRepeat = weeklyMovementUse.get(`@${ex.id}`) ?? 0;
    const fatigue = ex.fatigue.systemic + ex.fatigue.axial * .7 + ex.fatigue.lowerBack * .8;
    const compoundFit = role === 'hypertrophy_isolation' ? (ex.flags.compound ? -1.4 : 1.3) : (ex.flags.compound ? .8 : 0);
    let intent = 0;
    if (session.intent === 'pull' && ex.movementFamily === 'vertical_pull')
        intent += .65;
    if (session.intent === 'upper' && ex.movementFamily === 'horizontal_pull')
        intent += .45;
    if (session.intent === 'push' && ['horizontal_press', 'chest_adduction', 'lateral_raise'].includes(ex.movementFamily))
        intent += .55;
    if (session.intent === 'lower' && ['squat', 'hip_hinge'].includes(ex.movementFamily))
        intent += .35;
    if (session.intent === 'legs' && ['leg_press', 'knee_extension', 'knee_flexion', 'hip_extension'].includes(ex.movementFamily))
        intent += .55;
    const confidencePenalty = ex.source === 'custom' && ex.metadataConfidence === 'low' ? (ex.athleteFitConfidence === 'high' ? .25 : ex.athleteFitConfidence === 'moderate' ? .4 : .65) : ex.source === 'legacy_v661' ? .35 : 0;
    const preferredBonus = request.preferences.preferredExercises?.includes(ex.id) ? 0.9 : 0;
    // Arm-function diversity is a scoring input, not a second volume ledger. Once direct curl/forearm
    // work is being allocated, reward the missing anatomical bias so two curl slots do not both become
    // supinated biceps variants and direct wrist work does not train only one direction.
    const armBias = armCoverageBias(ex);
    let armCoverageBonus = 0;
    if (muscle === 'biceps') {
        if (armBias === 'biceps_bias' && !(weeklyMovementUse.get('#arm:biceps_bias') ?? 0))
            armCoverageBonus += 1.6;
        if (armBias === 'brachialis_bias' && !(weeklyMovementUse.get('#arm:brachialis_bias') ?? 0))
            armCoverageBonus += 2.5;
    }
    else if (muscle === 'forearms') {
        if (armBias === 'wrist_flexion' && !(weeklyMovementUse.get('#arm:wrist_flexion') ?? 0))
            armCoverageBonus += 2.7;
        if (armBias === 'wrist_extension' && !(weeklyMovementUse.get('#arm:wrist_extension') ?? 0))
            armCoverageBonus += 2.7;
        // Static grip remains useful, but it should not crowd out the first flexion/extension exposure
        // when the user explicitly gives forearms enough dose to earn direct work.
        if (armBias === null && ex.movementFamily === 'grip' && (!(weeklyMovementUse.get('#arm:wrist_flexion') ?? 0) || !(weeklyMovementUse.get('#arm:wrist_extension') ?? 0)))
            armCoverageBonus -= 1.2;
    }
    // Functional coverage is a selection tiebreaker, never a new dose target. The first exercise for a
    // muscle remains driven by stimulus/fatigue quality; later slots get a strong preference for the
    // complementary function instead of repeating the same motion just because its base score is high.
    let functionalCoverageBonus = 0;
    const functionalBiases = functionalCoverageBiases(ex);
    const missing = (bias) => !(weeklyMovementUse.get(`#function:${bias}`) ?? 0);
    if (muscle === 'hamstrings') {
        if (functionalBiases.includes('hamstrings_hip_extension') && missing('hamstrings_hip_extension'))
            functionalCoverageBonus += 2.2;
        if (functionalBiases.includes('hamstrings_knee_flexion') && missing('hamstrings_knee_flexion'))
            functionalCoverageBonus += 2.6;
    }
    else if (muscle === 'calves') {
        if (functionalBiases.includes('calves_straight_knee') && missing('calves_straight_knee'))
            functionalCoverageBonus += 2.2;
        if (functionalBiases.includes('calves_bent_knee') && missing('calves_bent_knee'))
            functionalCoverageBonus += 2.6;
    }
    else if (muscle === 'triceps') {
        if (functionalBiases.includes('triceps_lengthened') && missing('triceps_lengthened'))
            functionalCoverageBonus += 2.5;
        if (functionalBiases.includes('triceps_non_overhead') && missing('triceps_non_overhead'))
            functionalCoverageBonus += 2.0;
    }
    else if (muscle === 'back') {
        if (functionalBiases.includes('back_vertical_pull') && missing('back_vertical_pull'))
            functionalCoverageBonus += 2.25;
        if (functionalBiases.includes('back_horizontal_row') && missing('back_horizontal_row'))
            functionalCoverageBonus += 2.25;
    }
    // M182 uses station affinity only as a modest candidate tiebreaker. Redundancy itself is
    // enforced by the semantic/knowledge-graph overlap gate below; setup economy must never overpower
    // distinct stimulus, functional coverage, user priority, or strength specificity.
    const setupInefficiency = exerciseSetupInefficiencyPenalty(ex, chosen);
    return credit * 4 + ex.suitability.hypertrophy * .35 + ex.stability * .15 + compoundFit + intent + preferredBonus + armCoverageBonus + functionalCoverageBonus
        - fatigue * .22 - sameSession * 1.65 - weeklyRepeat * .42 - exactRepeat * 1.6 - ex.setupCost * .08 - confidencePenalty - setupInefficiency;
}

function muscleCandidate(a, session, request, chosen, weeklyMovementUse, catalog, minCredit = 0, maxSameMovementFamily) {
    const muscle = a.muscle;
    const movementAllowed = MUSCLE_MOVEMENTS[muscle] ?? [];
    const barbells = chosen.filter(e => e.flags.barbell).length;
    const horizontalPresses = chosen.filter(e => e.movementFamily === 'horizontal_press').length;
    const sameFamilyCount = (family) => chosen.filter(e => e.movementFamily === family).length;
    const priority = request.goal.musclePriorities[muscle] ?? 'normal';
    const dedicatedArmFamily = (session.intent === 'arms' || session.intent === 'shoulders_arms') && ['biceps', 'triceps', 'forearms', 'side_delts'].includes(muscle);
    const specialization = priority === 'high' || priority === 'specialization' || priority === 'primary';
    const defaultFamilyCap = a.role === 'hypertrophy_isolation' ? (dedicatedArmFamily || specialization ? 2 : 1) : 2;
    const familyCap = maxSameMovementFamily ?? defaultFamilyCap;
    return catalog.filter(e => equipmentEligible(e, session, request))
        .filter(e => !request.preferences.avoidedExercises?.includes(e.id))
        // movementFamily is intentionally broad. Add a semantic economy guard for reviewed near-equivalent
        // compounds so `squat` vs `leg_press` labels cannot sneak Hack Squat + Leg Press into two ordinary
        // hypertrophy slots. True strength anchors remain exempt (e.g. Back Squat -> Leg Press).
        .filter(e => !avoidableExerciseOverlap(e, a.role, chosen.map(def => ({ def })), { priority }))
        .filter(e => movementAllowed.includes(e.movementFamily) || (minCredit > 0 && minCredit < 1 && (e.muscles[muscle]?.credit ?? 0) >= minCredit))
        .filter(e => (e.muscles[muscle]?.credit ?? 0) >= minCredit)
        .filter(e => e.movementFamily !== 'horizontal_press' || horizontalPresses < 2)
        .filter(e => sameFamilyCount(e.movementFamily) < familyCap)
        .filter(e => !chosen.some(c => c.id === e.id))
        .filter(e => !e.flags.barbell || barbells < maxBarbells(session, request))
        .map(e => ({ e, score: muscleScore(e, muscle, a.role, session, chosen, weeklyMovementUse, request) }))
        .sort((x, y) => y.score - x.score)[0]?.e;
}

export { strengthCandidate, muscleScore, muscleCandidate };
