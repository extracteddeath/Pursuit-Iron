import { createExerciseCatalog, createExerciseMap } from './exercise-db.js';
import { ALL_MUSCLES } from './config.js';
import { phasePolicyFor } from './phase-policy.js';
import { progressionInstruction, resolveProgressionStyle } from './progression-style.js';
import { createMusclePrescriptions, productiveTimeBandDoseTarget } from './prescription.js';
import { chooseAdvancedTechnique, techniqueExtraSeconds } from './techniques.js';
import { INTENT_MUSCLES } from './topology.js';
import { PUBLIC_MEV_REGIONS, PUBLIC_REGION_MUSCLE, directlyTargetsPublicRegion, publicMevBaseTarget, publicMevContractApplies, publicMevLedger, publicMevRequired, publicRegionContribution } from './public-mev.js';
import { armCoverageBias, armCoverageUseKeys, evaluateArmCoverage } from './arm-coverage.js';
import { functionalCoverageBiases, functionalCoverageUseKeys } from './functional-coverage.js';
import { avoidableExerciseOverlap, exerciseEconomyCluster } from './exercise-economy.js';
import { setupTransitionCost } from './setup-economy.js';
import { exerciseSetupInefficiencyPenalty } from './exercise-selection-intelligence.js';
const MUSCLE_MOVEMENTS = {
    chest: ['horizontal_press', 'chest_adduction'],
    back: ['horizontal_pull', 'vertical_pull', 'shoulder_extension'],
    side_delts: ['lateral_raise'], rear_delts: ['rear_delt'], front_delts: ['vertical_press'],
    biceps: ['elbow_flexion'], triceps: ['elbow_extension'],
    quads: ['squat', 'leg_press', 'knee_extension'],
    hamstrings: ['hip_hinge', 'knee_flexion'],
    glutes: ['hip_extension', 'squat', 'hip_hinge'],
    calves: ['calf'], core: ['core'],
    traps: ['shrug', 'horizontal_pull', 'vertical_pull'],
    forearms: ['grip', 'wrist_flexion', 'wrist_extension', 'wrist_deviation', 'elbow_flexion'],
    adductors: ['hip_adduction', 'squat'], abductors: ['hip_abduction', 'squat'],
    neck: ['neck'], lower_back: ['hip_hinge', 'core']
};
const LARGE = ['chest', 'back', 'quads', 'hamstrings', 'glutes', 'lower_back'];
const SMALL = ['side_delts', 'rear_delts', 'front_delts', 'biceps', 'triceps', 'calves', 'core', 'traps', 'forearms', 'adductors', 'abductors', 'neck'];
function equipmentEligible(ex, session, request) {
    const day = request.schedule.days.find(d => d.day === session.day);
    const equipment = day.equipmentOverride ?? request.equipment.available;
    if ((ex.flags.bodyweight || ex.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
        return false;
    const setups = [ex.equipment, ...(ex.equipmentAlternatives ?? [])];
    return setups.some(setup => setup.every(req => req === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : equipment.includes(req)));
}
function maxBarbells(session, request) {
    return request.schedule.days.find(d => d.day === session.day)?.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay;
}
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
export function repsForPhase(ex, role, policy) {
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
export function rirForPhase(role, policy) {
    const base = role === 'primary_strength' || role === 'secondary_strength' ? [2, 3] : role === 'hypertrophy_compound' ? [1, 2] : [0, 2];
    const shift = role === 'primary_strength' || role === 'secondary_strength' ? policy.strengthRirShift : policy.hypertrophyRirShift;
    return [Math.min(5, base[0] + shift), Math.min(6, base[1] + shift)];
}
export function restForExercise(role, ex) { return role === 'primary_strength' ? 240 : role === 'secondary_strength' ? 180 : ex.flags.compound ? 150 : 90; }
export function progressionStyleForExercise(ex, role, policy, experience = 'intermediate') {
    return resolveProgressionStyle(ex, role, { phase: policy.phase, experience });
}
export function progressionForExercise(ex, role, policy = phasePolicyFor('mixed_accumulation'), experience = 'intermediate') {
    return progressionInstruction(progressionStyleForExercise(ex, role, policy, experience));
}
function compactRestCompression(exercises) {
    return exercises.map(ex => {
        // Time-constrained programming should first recover clock time from low-cost hypertrophy rest,
        // never from the primary/secondary strength work that depends most on full recovery.
        if (ex.role === 'primary_strength' || ex.role === 'secondary_strength')
            return ex;
        const floor = ex.role === 'hypertrophy_compound' ? 120 : 75;
        if (ex.prescription.restSeconds <= floor)
            return ex;
        return { ...ex, prescription: { ...ex.prescription, restSeconds: floor } };
    });
}
function fitCompactRestToSession(session, exercises) {
    if (session.maxMinutes > 45)
        return null;
    const compressed = compactRestCompression(exercises);
    return estimateSessionMinutes(compressed) <= session.maxMinutes ? compressed : null;
}
export function estimateSessionMinutes(exercises) {
    let seconds = 7 * 60;
    for (const ex of exercises) {
        seconds += 45 + ex.sets * 35 + Math.max(0, ex.sets - 1) * ex.prescription.restSeconds + techniqueExtraSeconds(ex.advancedTechnique);
        if (ex.role === 'primary_strength')
            seconds += 6 * 60;
        else if (ex.role === 'secondary_strength' || ex.role === 'hypertrophy_compound')
            seconds += 2 * 60;
    }
    // Low-interference accessory supersets overlap part of the between-set rest. We still charge
    // transition/setup time and 25% of the shorter rest period rather than pretending supersets are free.
    // For genuinely low-interference accessory pairings, ~75% of the shorter between-set rest can be
    // overlapped by alternating movements while still preserving a meaningful recovery window.
    const groups = new Map();
    for (const ex of exercises)
        if (ex.supersetGroup) {
            const list = groups.get(ex.supersetGroup) ?? [];
            list.push(ex);
            groups.set(ex.supersetGroup, list);
        }
    for (const pair of groups.values())
        if (pair.length === 2) {
            const overlap = Math.min(Math.max(0, pair[0].sets - 1) * pair[0].prescription.restSeconds, Math.max(0, pair[1].sets - 1) * pair[1].prescription.restSeconds);
            seconds -= Math.round(overlap * .75);
        }
    return Math.round(seconds / 60);
}
const estimateMinutes = estimateSessionMinutes;
function addToLedger(ledger, ex, sets) {
    for (const [m, c] of Object.entries(ex.muscles)) {
        ledger.fractional[m] = (ledger.fractional[m] ?? 0) + c.credit * sets;
        if (c.credit >= 1)
            ledger.direct[m] = (ledger.direct[m] ?? 0) + sets;
    }
}
function makePlanned(ex, role, sets, policy, experience = 'intermediate') {
    // The allocation expresses why work exists; the final exercise role should describe the movement that
    // was actually selected. Large-muscle claims may legitimately resolve to a fly/extension, and labeling
    // that isolation as a compound distorts RIR semantics and coach-facing output.
    const realizedRole = (role === 'hypertrophy_compound' || role === 'hypertrophy_isolation')
        ? (ex.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation')
        : role;
    const progressionStyle = progressionStyleForExercise(ex, realizedRole, policy, experience);
    return { exerciseId: ex.id, name: ex.name, role: realizedRole, sets, prescription: { reps: repsForPhase(ex, realizedRole, policy), rir: rirForPhase(realizedRole, policy), restSeconds: restForExercise(realizedRole, ex) }, progression: progressionInstruction(progressionStyle), progressionStyle };
}
function integerSetPlan(entries, desiredTotal) {
    const result = new Map();
    if (!entries.length || desiredTotal <= 0)
        return result;
    const weights = entries.map(entry => Math.max(0, entry.allocation.dose));
    const weightSum = weights.reduce((a, b) => a + b, 0) || entries.length;
    const raw = entries.map((entry, index) => ({ entry, value: desiredTotal * (weights[index] || 1) / weightSum }));
    let assigned = 0;
    for (const item of raw) {
        const base = Math.min(4, Math.floor(item.value));
        result.set(item.entry, base);
        assigned += base;
    }
    const ranked = [...raw].sort((a, b) => (b.value - Math.floor(b.value)) - (a.value - Math.floor(a.value)) || a.entry.allocation.id.localeCompare(b.entry.allocation.id));
    let cursor = 0;
    while (assigned < desiredTotal && ranked.length) {
        const item = ranked[cursor % ranked.length];
        const current = result.get(item.entry) ?? 0;
        if (current < 4) {
            result.set(item.entry, current + 1);
            assigned++;
        }
        cursor++;
        if (cursor > ranked.length * 6)
            break;
    }
    return result;
}
function desiredDirect(muscle, target, directTargetDose) {
    const modeled = directTargetDose[muscle];
    if (modeled !== undefined)
        return modeled;
    if (muscle === 'side_delts' || muscle === 'rear_delts' || muscle === 'calves')
        return Math.min(6, Math.max(3, Math.round(target * .7)));
    if (muscle === 'biceps' || muscle === 'triceps')
        return Math.min(5, Math.max(2, Math.round(target * .5)));
    if (muscle === 'core')
        return Math.min(4, Math.max(2, Math.round(target * .5)));
    return 0;
}
function primaryMuscle(def) {
    return Object.entries(def.muscles).find(([, c]) => c.role === 'primary')?.[0];
}
function intentAcceptsMuscle(intent, muscle) {
    return (INTENT_MUSCLES[intent] ?? INTENT_MUSCLES.full).includes(muscle);
}
function floorSpilloverAllowed(intent, muscle, request) {
    // Short 3–4 day weeks sometimes have only one native home for a muscle. When that home is truly
    // clock-saturated, a coach will often place a small, low-interference accessory on another day
    // rather than knowingly leave the weekly floor unmet. Keep this narrow: no strength-focused days,
    // no compounds across split boundaries, and no spillover at all in higher-frequency weeks.
    // Core/forearm/neck accessories are genuinely low-interference and may need to spill out of a
    // single saturated native day even in 5–6 day named splits (notably a Bro split with only one Legs
    // day). This only runs for an unmet floor; it does not create optional extra volume.
    if (['core', 'forearms', 'neck'].includes(muscle))
        return true;
    if (request.schedule.days.length > 4)
        return false;
    if (['bench_focus', 'press_focus', 'deadlift_focus', 'squat_focus', 'chest', 'back_day'].includes(intent))
        return false;
    if (['calves', 'abductors', 'adductors'].includes(muscle))
        return true;
    if (['quads', 'hamstrings', 'glutes'].includes(muscle))
        return ['upper', 'push', 'pull', 'arms', 'shoulders_arms', 'torso'].includes(intent);
    return false;
}
function assignAccessorySupersets(session, exerciseMap, allowSupersets = true) {
    const exercises = session.exercises.map(ex => { const clone = { ...ex }; delete clone.supersetGroup; return clone; });
    const rawMinutes = estimateMinutes(exercises);
    if (!allowSupersets)
        return { ...session, exercises, estimatedMinutes: rawMinutes };
    // Normal sessions only use isolation supersets once there are enough movements to justify the
    // transition complexity. In genuinely short sessions, also permit one stable chest/back antagonist
    // compound pairing (e.g. machine press + chest-supported row). This is a deliberate density tool,
    // never a strength-anchor shortcut and never a lower-body compound circuit.
    const constrained = session.maxMinutes <= 35 || rawMinutes > session.maxMinutes;
    if (exercises.length < 5 && !constrained)
        return { ...session, exercises, estimatedMinutes: rawMinutes };
    const eligible = exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
        .filter((x) => !!x.def)
        .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength')
        .filter(x => {
        if (!x.def.flags.compound)
            return x.def.fatigue.systemic <= 2 && x.def.fatigue.lowerBack <= 1;
        const primary = primaryMuscle(x.def);
        return constrained && (primary === 'chest' || primary === 'back') && x.def.fatigue.systemic <= 3 && x.def.fatigue.axial <= 2 && x.def.fatigue.lowerBack <= 1 && x.def.stability >= 4;
    });
    let group = 1;
    const used = new Set();
    for (const a of eligible) {
        if (used.has(a.index) || group > 2)
            continue;
        const am = primaryMuscle(a.def);
        const b = eligible.find(candidate => {
            if (candidate.index <= a.index || used.has(candidate.index))
                return false;
            const bm = primaryMuscle(candidate.def);
            if (!am || !bm || am === bm)
                return false;
            // Compound/compound supersets are restricted to the stable chest/back antagonist case above.
            if (a.def.flags.compound && candidate.def.flags.compound && !((am === 'chest' && bm === 'back') || (am === 'back' && bm === 'chest')))
                return false;
            // Do not pair two movements that compete for the same joint-local target family.
            if (a.def.movementFamily === candidate.def.movementFamily)
                return false;
            return Math.abs(a.exercise.sets - candidate.exercise.sets) <= 2;
        });
        if (!b)
            continue;
        const id = `SS${group++}`;
        exercises[a.index].supersetGroup = id;
        exercises[b.index].supersetGroup = id;
        used.add(a.index);
        used.add(b.index);
    }
    return { ...session, exercises, estimatedMinutes: estimateMinutes(exercises) };
}
function sequenceSessionExercises(session, exerciseMap) {
    const indexed = session.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }));
    const used = new Set();
    const blocks = [];
    const familyClass = (def) => {
        if (!def)
            return 'other';
        if (['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily))
            return 'push';
        if (['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily))
            return 'pull';
        if (['squat', 'leg_press', 'knee_extension'].includes(def.movementFamily))
            return 'knee';
        if (['hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
            return 'hinge';
        return def.movementFamily;
    };
    const makeBlock = (items) => {
        // A mixed compound/accessory density pair always presents the compound first. Pure accessory
        // pairs preserve their original order. Strength work is never supersetted by construction.
        items = [...items].sort((a, b) => {
            const ac = !!a.def?.flags.compound, bc = !!b.def?.flags.compound;
            if (ac !== bc)
                return ac ? -1 : 1;
            return a.index - b.index;
        });
        const lead = items.find(x => x.def?.flags.compound) ?? items[0];
        const defs = items.map(x => x.def).filter((x) => !!x);
        return {
            items, first: Math.min(...items.map(x => x.index)),
            compound: defs.some(def => def.flags.compound),
            strength: items.some(x => x.exercise.role === 'primary_strength' || x.exercise.role === 'secondary_strength' || x.exercise.role === 'strength_support'),
            familyClass: familyClass(lead?.def),
            joint: [
                Math.max(0, ...defs.map(d => d.fatigue.shoulder)), Math.max(0, ...defs.map(d => d.fatigue.elbow)),
                Math.max(0, ...defs.map(d => d.fatigue.knee)), Math.max(0, ...defs.map(d => d.fatigue.grip)),
                Math.max(0, ...defs.map(d => d.fatigue.lowerBack))
            ]
        };
    };
    for (const item of indexed) {
        if (used.has(item.index))
            continue;
        if (item.exercise.supersetGroup) {
            const pair = indexed.filter(x => x.exercise.supersetGroup === item.exercise.supersetGroup);
            pair.forEach(x => used.add(x.index));
            blocks.push(makeBlock(pair));
        }
        else {
            used.add(item.index);
            blocks.push(makeBlock([item]));
        }
    }
    const strength = blocks.filter(b => b.strength).sort((a, b) => a.first - b.first);
    const compoundPool = blocks.filter(b => !b.strength && b.compound).sort((a, b) => a.first - b.first);
    const accessories = blocks.filter(b => !b.strength && !b.compound).sort((a, b) => a.first - b.first);
    // Mixed density pairs (compound + non-interfering accessory) can be useful under a real time cap,
    // but the accessory must not pre-fatigue another compound that is still waiting. Build explicit
    // dependencies between compound blocks: if block A contains an accessory for a muscle materially
    // used by compound block B, B has to happen first. This preserves the superset while protecting
    // later performance.
    const preFatigues = (a, b) => {
        const accessoryPrimaries = a.items.filter(x => x.def && !x.def.flags.compound).map(x => Object.entries(x.def.muscles).find(([, c]) => c.role === 'primary')?.[0]).filter(Boolean);
        if (!accessoryPrimaries.length)
            return false;
        return b.items.some(x => x.def?.flags.compound && accessoryPrimaries.some(m => (x.def.muscles[m]?.credit ?? 0) >= .5));
    };
    let compounds = [];
    const remaining = [...compoundPool];
    while (remaining.length) {
        const available = remaining.filter(candidate => !remaining.some(other => other !== candidate && preFatigues(candidate, other)));
        const pool = available.length ? available : remaining; // deterministic fail-safe for an unlikely dependency cycle
        let chosen = pool[0];
        if ((session.intent === 'full' || session.intent === 'strength_full') && compounds.length && pool.length > 1) {
            const prev = compounds[compounds.length - 1];
            chosen = [...pool].sort((a, b) => {
                const score = (cand) => {
                    const classPenalty = cand.familyClass === prev.familyClass ? 5 : 0;
                    const jointOverlap = cand.joint.reduce((sum, v, j) => sum + Math.min(v, prev.joint[j]), 0);
                    return classPenalty + jointOverlap * .35 + cand.first * .01;
                };
                return score(a) - score(b) || a.first - b.first;
            })[0];
        }
        else
            chosen = [...pool].sort((a, b) => a.first - b.first)[0];
        compounds.push(chosen);
        remaining.splice(remaining.indexOf(chosen), 1);
    }
    const exercises = [...strength, ...compounds, ...accessories].flatMap(block => block.items.map(item => item.exercise));
    return { ...session, exercises, estimatedMinutes: estimateMinutes(exercises) };
}
export function optimizeSetupAwareSessionSequence(session, exerciseMap, request) {
    const indexed = session.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }));
    const used = new Set();
    const blocks = [];
    const familyClass = (def) => {
        if (!def)
            return 'other';
        if (['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily))
            return 'push';
        if (['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily))
            return 'pull';
        if (['squat', 'leg_press', 'knee_extension'].includes(def.movementFamily))
            return 'knee';
        if (['hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
            return 'hinge';
        return def.movementFamily;
    };
    const makeBlock = (items) => {
        // A mixed compound/accessory density pair always presents the compound first. Pure accessory
        // pairs preserve their original order. Strength work is never supersetted by construction.
        items = [...items].sort((a, b) => {
            const ac = !!a.def?.flags.compound, bc = !!b.def?.flags.compound;
            if (ac !== bc)
                return ac ? -1 : 1;
            return a.index - b.index;
        });
        const lead = items.find(x => x.def?.flags.compound) ?? items[0];
        const defs = items.map(x => x.def).filter((x) => !!x);
        const primaryMuscles = new Set();
        for (const def of defs)
            for (const [muscle, contribution] of Object.entries(def.muscles)) {
                if (contribution?.role === 'primary' || contribution?.credit === 1)
                    primaryMuscles.add(muscle);
            }
        return {
            items, first: Math.min(...items.map(x => x.index)),
            compound: defs.some(def => def.flags.compound),
            strength: items.some(x => x.exercise.role === 'primary_strength' || x.exercise.role === 'secondary_strength' || x.exercise.role === 'strength_support'),
            familyClass: familyClass(lead?.def), primaryMuscles,
            joint: [
                Math.max(0, ...defs.map(d => d.fatigue.shoulder)), Math.max(0, ...defs.map(d => d.fatigue.elbow)),
                Math.max(0, ...defs.map(d => d.fatigue.knee)), Math.max(0, ...defs.map(d => d.fatigue.grip)),
                Math.max(0, ...defs.map(d => d.fatigue.lowerBack))
            ]
        };
    };
    const firstDef = (block) => block.items.find(x => x.def)?.def;
    const lastDef = (block) => [...block.items].reverse().find(x => x.def)?.def;
    for (const item of indexed) {
        if (used.has(item.index))
            continue;
        if (item.exercise.supersetGroup) {
            const pair = indexed.filter(x => x.exercise.supersetGroup === item.exercise.supersetGroup);
            pair.forEach(x => used.add(x.index));
            blocks.push(makeBlock(pair));
        }
        else {
            used.add(item.index);
            blocks.push(makeBlock([item]));
        }
    }
    const strength = blocks.filter(b => b.strength).sort((a, b) => a.first - b.first);
    const compoundPool = blocks.filter(b => !b.strength && b.compound).sort((a, b) => a.first - b.first);
    const accessories = blocks.filter(b => !b.strength && !b.compound).sort((a, b) => a.first - b.first);
    // Mixed density pairs (compound + non-interfering accessory) can be useful under a real time cap,
    // but the accessory must not pre-fatigue another compound that is still waiting. Build explicit
    // dependencies between compound blocks: if block A contains an accessory for a muscle materially
    // used by compound block B, B has to happen first. This preserves the superset while protecting
    // later performance.
    const preFatigues = (a, b) => {
        const accessoryPrimaries = a.items.filter(x => x.def && !x.def.flags.compound).map(x => Object.entries(x.def.muscles).find(([, c]) => c.role === 'primary')?.[0]).filter(Boolean);
        if (!accessoryPrimaries.length)
            return false;
        return b.items.some(x => x.def?.flags.compound && accessoryPrimaries.some(m => (x.def.muscles[m]?.credit ?? 0) >= .5));
    };
    let compounds = [];
    const remaining = [...compoundPool];
    while (remaining.length) {
        const available = remaining.filter(candidate => !remaining.some(other => other !== candidate && preFatigues(candidate, other)));
        const pool = available.length ? available : remaining; // deterministic fail-safe for an unlikely dependency cycle
        let chosen = pool[0];
        if ((session.intent === 'full' || session.intent === 'strength_full') && compounds.length && pool.length > 1) {
            const prev = compounds[compounds.length - 1];
            chosen = [...pool].sort((a, b) => {
                const score = (cand) => {
                    const classPenalty = cand.familyClass === prev.familyClass ? 5 : 0;
                    const jointOverlap = cand.joint.reduce((sum, v, j) => sum + Math.min(v, prev.joint[j]), 0);
                    // Setup affinity is only a low-weight tie-break here. Full-body interference rotation remains
                    // far more important than staying at the same station.
                    const setup = setupTransitionCost(lastDef(prev), firstDef(cand));
                    return classPenalty + jointOverlap * .35 + setup * .08 + cand.first * .01;
                };
                return score(a) - score(b) || a.first - b.first;
            })[0];
        }
        else
            chosen = [...pool].sort((a, b) => a.first - b.first)[0];
        compounds.push(chosen);
        remaining.splice(remaining.indexOf(chosen), 1);
    }
    const priorityValue = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
    const accessoryTier = (block) => {
        if (block.items.some(x => x.exercise.role === 'specialization'))
            return 0;
        let best = 0;
        for (const muscle of block.primaryMuscles)
            best = Math.max(best, priorityValue[request.goal.musclePriorities[muscle] ?? 'normal']);
        return best >= 2 ? 1 : 2;
    };
    const adjacencyPenalty = (a, b) => {
        if (!a)
            return b.first * .0001;
        const setup = setupTransitionCost(lastDef(a), firstDef(b));
        const shared = [...a.primaryMuscles].some(m => b.primaryMuscles.has(m));
        const sameFamily = a.familyClass === b.familyClass;
        const jointOverlap = b.joint.reduce((sum, v, j) => sum + Math.min(v, a.joint[j]), 0);
        // Setup is the dominant objective only after higher-value sequencing has already been protected.
        // Small local-fatigue penalties prevent pathological grouping when two options save the same setup.
        return setup + (shared ? .22 : 0) + (sameFamily ? .08 : 0) + jointOverlap * .012;
    };
    const optimizeBand = (band, preceding) => {
        if (band.length <= 1)
            return band;
        // Accessory tails are normally short. Solve the station path exactly for <=9 blocks; use a
        // deterministic nearest-neighbor fallback for unusual custom programs with larger tails.
        if (band.length > 9) {
            const left = [...band], out = [];
            let prev = preceding;
            while (left.length) {
                const next = [...left].sort((a, b) => adjacencyPenalty(prev, a) - adjacencyPenalty(prev, b) || a.first - b.first)[0];
                out.push(next);
                left.splice(left.indexOf(next), 1);
                prev = next;
            }
            return out;
        }
        const n = band.length, dp = new Map();
        const better = (candidate, current) => {
            if (!current)
                return true;
            if (Math.abs(candidate.cost - current.cost) > .000001)
                return candidate.cost < current.cost;
            const ca = candidate.path.map(i => band[i].first), cb = current.path.map(i => band[i].first);
            for (let i = 0; i < Math.min(ca.length, cb.length); i++)
                if (ca[i] !== cb[i])
                    return ca[i] < cb[i];
            return ca.length < cb.length;
        };
        for (let i = 0; i < n; i++)
            dp.set(`${1 << i}:${i}`, { cost: adjacencyPenalty(preceding, band[i]), path: [i] });
        for (let mask = 1; mask < (1 << n); mask++)
            for (let last = 0; last < n; last++) {
                const state = dp.get(`${mask}:${last}`);
                if (!state)
                    continue;
                for (let next = 0; next < n; next++) {
                    if (mask & (1 << next))
                        continue;
                    const key = `${mask | (1 << next)}:${next}`;
                    const candidate = { cost: state.cost + adjacencyPenalty(band[last], band[next]), path: [...state.path, next] };
                    if (better(candidate, dp.get(key)))
                        dp.set(key, candidate);
                }
            }
        let best;
        const full = (1 << n) - 1;
        for (let last = 0; last < n; last++) {
            const state = dp.get(`${full}:${last}`);
            if (state && better(state, best))
                best = state;
        }
        return best ? best.path.map(i => band[i]) : band;
    };
    // Priority boundaries are hard. Specialization and user-prioritized accessories keep their place
    // ahead of ordinary cleanup work; equipment economy only optimizes within each equally safe band.
    const orderedAccessories = [];
    let preceding = compounds[compounds.length - 1] ?? strength[strength.length - 1];
    for (const tier of [0, 1, 2]) {
        const band = accessories.filter(block => accessoryTier(block) === tier);
        const optimized = optimizeBand(band, preceding);
        orderedAccessories.push(...optimized);
        if (optimized.length)
            preceding = optimized[optimized.length - 1];
    }
    const exercises = [...strength, ...compounds, ...orderedAccessories].flatMap(block => block.items.map(item => item.exercise));
    return { ...session, exercises, estimatedMinutes: estimateMinutes(exercises) };
}
/** Re-applies density pairing, time estimation and coach-like sequencing after a later engine stage moves work between sessions. */
export function finalizePlannedSession(session, request) {
    const exerciseMap = createExerciseMap(request.customExercises);
    return sequenceSessionExercises(assignAccessorySupersets({ ...session, exercises: session.exercises.map(ex => ({ ...ex })), estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets), exerciseMap);
}
function strengthSetsForAllocation(allocation, session, request, policy) {
    const shortSession = session.maxMinutes <= 35;
    const denseThreeDayStrength = request.goal.type === 'strength' && request.schedule.days.length <= 3;
    const baseSets = allocation.role === 'primary_strength'
        ? (request.athlete.experience === 'novice' ? 3 : (shortSession ? 3 : 4))
        : (shortSession || denseThreeDayStrength || request.athlete.experience === 'novice' ? 2 : 3);
    return Math.max(2, Math.round(baseSets * policy.strengthVolumeMultiplier));
}
/**
 * Deterministically selects only protected strength anchors for a provisional topology. The returned
 * fractional ledger and time are the actual baseline used by the second allocator pass; the anchor map
 * is later pinned so the final program cannot silently switch to a different variant after budgeting.
 */
export function realizeStrengthAnchors(plans, request, phase) {
    const policy = phasePolicyFor(phase);
    const exerciseCatalog = createExerciseCatalog(request.customExercises);
    const ledger = { fractional: {}, direct: {} };
    const anchors = {};
    const sessionMinutes = {};
    const missingAllocationIds = [];
    let realizedCount = 0;
    for (const plan of plans) {
        const chosen = [];
        const planned = [];
        for (const allocation of plan.allocations.filter(x => x.kind === 'lift')) {
            const def = strengthCandidate(allocation, plan, request, chosen, exerciseCatalog);
            if (!def) {
                missingAllocationIds.push(allocation.id);
                continue;
            }
            const sets = strengthSetsForAllocation(allocation, plan, request, policy);
            const exercise = makePlanned(def, allocation.role, sets, policy, request.athlete.experience);
            chosen.push(def);
            planned.push(exercise);
            addToLedger(ledger, def, sets);
            anchors[allocation.id] = {
                allocationId: allocation.id, exerciseId: def.id, exerciseName: def.name,
                sets, sessionId: plan.id, day: plan.day, role: allocation.role, lift: allocation.lift
            };
            realizedCount++;
        }
        if (planned.length)
            sessionMinutes[plan.id] = estimateSessionMinutes(planned);
    }
    return {
        anchors,
        fractional: ledger.fractional,
        estimatedMinutes: Object.values(sessionMinutes).reduce((sum, value) => sum + value, 0),
        sessionMinutes,
        realizedCount,
        expectedCount: plans.reduce((sum, plan) => sum + plan.allocations.filter(x => x.kind === 'lift').length, 0),
        missingAllocationIds
    };
}
export function realizeSessions(plans, request, targetDose = {}, directTargetDose = {}, phase, options = {}) {
    const policy = phasePolicyFor(phase);
    const exerciseCatalog = createExerciseCatalog(request.customExercises);
    const exerciseMap = createExerciseMap(request.customExercises);
    const sessions = plans.map(plan => ({ plan, defs: [], exercises: [], importance: [] }));
    const ledger = { fractional: {}, direct: {} };
    const pinnedStrengthAnchors = options.strengthAnchors ?? {};
    const weeklyMovementUse = new Map();
    const trackExerciseUse = (def) => {
        weeklyMovementUse.set(def.movementFamily, (weeklyMovementUse.get(def.movementFamily) ?? 0) + 1);
        weeklyMovementUse.set(`@${def.id}`, (weeklyMovementUse.get(`@${def.id}`) ?? 0) + 1);
        for (const key of armCoverageUseKeys(def))
            weeklyMovementUse.set(key, (weeklyMovementUse.get(key) ?? 0) + 1);
        for (const key of functionalCoverageUseKeys(def))
            weeklyMovementUse.set(key, (weeklyMovementUse.get(key) ?? 0) + 1);
    };
    const add = (s, allocation, def, sets) => {
        if (sets <= 0)
            return;
        s.defs.push(def);
        s.exercises.push(makePlanned(def, allocation.role, sets, policy, request.athlete.experience));
        s.importance.push(allocation.importance);
        trackExerciseUse(def);
        addToLedger(ledger, def, sets);
    };
    // 1) Required/specific lift work first.
    for (const s of sessions)
        for (const a of s.plan.allocations.filter(x => x.kind === 'lift')) {
            const pinned = pinnedStrengthAnchors[a.id];
            const def = pinned ? exerciseMap.get(pinned.exerciseId) : strengthCandidate(a, s.plan, request, s.defs, exerciseCatalog);
            if (!def)
                continue;
            if (pinned) {
                const eligible = equipmentEligible(def, s.plan, request)
                    && !request.preferences.avoidedExercises?.includes(def.id)
                    && (!pinned.sessionId || pinned.sessionId === s.plan.id)
                    && (def.liftSpecificity?.[a.lift] ?? 0) > .45;
                if (!eligible)
                    throw new Error('Pinned strength anchor ' + pinned.exerciseId + ' is no longer eligible for ' + a.id + ' on ' + s.plan.day + '.');
            }
            const phaseSets = pinned?.sets ?? strengthSetsForAllocation(a, s.plan, request, policy);
            add(s, a, def, phaseSets);
        }
    // 2) Major-muscle work. Convert fractional topology allocations into an integer weekly set plan
    // before selecting exercises so distributing 4 target sets across 3 sessions cannot silently become 3.
    for (const muscle of LARGE) {
        const allEntries = sessions.flatMap(s => s.plan.allocations
            .filter(a => a.kind === 'muscle' && a.muscle === muscle)
            .map(allocation => ({ session: s, allocation })));
        if (!allEntries.length)
            continue;
        const fullBodyCapacityContract = plans.every(p => p.intent === 'full') && (plans.some(p => p.targetExercises !== undefined) || request.preferences.lockedSplit === 'full_body');
        const mandatoryFullBodyMuscle = fullBodyCapacityContract && ['chest', 'back'].includes(muscle);
        const entries = mandatoryFullBodyMuscle
            ? allEntries.filter(entry => !entry.session.defs.some(def => (def.muscles[muscle]?.credit ?? 0) >= 1))
            : allEntries;
        const activeEntries = entries.length ? entries : allEntries;
        const modeledTarget = targetDose[muscle] ?? allEntries.reduce((sum, e) => sum + e.allocation.dose, 0);
        const perUncoveredSession = request.athlete.experience === 'novice' ? 1 : 2;
        const current = ledger.fractional[muscle] ?? 0;
        const target = mandatoryFullBodyMuscle
            ? Math.max(modeledTarget, current + perUncoveredSession * activeEntries.length)
            : modeledTarget;
        const residual = Math.max(0, Math.round(target - current));
        if (residual <= 0)
            continue;
        const setPlan = integerSetPlan(activeEntries, residual);
        for (const entry of activeEntries) {
            const sets = setPlan.get(entry) ?? 0;
            if (sets <= 0)
                continue;
            const def = muscleCandidate(entry.allocation, entry.session.plan, request, entry.session.defs, weeklyMovementUse, exerciseCatalog);
            if (!def)
                continue;
            add(entry.session, entry.allocation, def, sets);
        }
    }
    // 3) Direct isolation after compound carryover is known.
    const smallIntentPreference = {
        biceps: ['arms', 'shoulders_arms', 'pull', 'upper', 'strength_full', 'full'], triceps: ['arms', 'shoulders_arms', 'push', 'bench_focus', 'press_focus', 'upper', 'strength_full', 'full'], rear_delts: ['pull', 'upper', 'bench_focus', 'press_focus', 'strength_full', 'full'],
        side_delts: ['shoulders', 'shoulders_arms', 'push', 'press_focus', 'upper', 'strength_full', 'full'], calves: ['legs', 'lower', 'squat_focus', 'deadlift_focus', 'strength_full', 'limbs', 'full'], core: ['legs', 'lower', 'squat_focus', 'deadlift_focus', 'strength_full', 'limbs', 'full'], front_delts: ['push', 'press_focus', 'bench_focus', 'upper', 'strength_full', 'full']
    };
    for (const muscle of SMALL) {
        if (muscle === 'front_delts' && request.goal.musclePriorities.front_delts === 'normal')
            continue;
        const rankedSessions = [...sessions].sort((a, b) => {
            // Topology may deliberately spill a low-interference accessory onto a Lower/Leg day to balance
            // session density. Honor that explicit destination before the generic intent preference; otherwise
            // earlier Upper allocations can satisfy the global direct-volume target and silently erase the move.
            const aSpill = a.plan.allocations.some(x => x.kind === 'muscle' && x.muscle === muscle && x.id.includes('-spill-')) ? 1 : 0;
            const bSpill = b.plan.allocations.some(x => x.kind === 'muscle' && x.muscle === muscle && x.id.includes('-spill-')) ? 1 : 0;
            if (aSpill !== bSpill)
                return bSpill - aSpill;
            const pref = smallIntentPreference[muscle] ?? [];
            const ai = pref.indexOf(a.plan.intent);
            const bi = pref.indexOf(b.plan.intent);
            return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
        });
        for (const s of rankedSessions)
            for (const a of s.plan.allocations.filter(x => x.kind === 'muscle' && x.muscle === muscle)) {
                const target = targetDose[muscle] ?? a.dose;
                const totalResidual = Math.max(0, target - (ledger.fractional[muscle] ?? 0));
                const directResidual = Math.max(0, desiredDirect(muscle, target, directTargetDose) - (ledger.direct[muscle] ?? 0));
                const need = Math.max(totalResidual, directResidual);
                if (need < .75)
                    continue;
                const def = muscleCandidate(a, s.plan, request, s.defs, weeklyMovementUse, exerciseCatalog);
                if (!def)
                    continue;
                const sets = Math.max(1, Math.min(4, Math.round(Math.min(a.dose, need))));
                add(s, a, def, sets);
            }
    }
    const realized = sessions.map(s => {
        // Time repair must be importance-aware. Tail trimming recreates legacy list-order starvation: a B/A
        // priority claim added later can disappear before earlier C filler. Protect A, then B, then C exactly
        // as the allocator intended; strength anchors are last-resort removals only.
        const rank = { C: 0, B: 1, A: 2 };
        while (s.exercises.length > 3 && estimateMinutes(s.exercises) > s.plan.maxMinutes) {
            const removable = s.exercises.map((exercise, index) => ({ exercise, index, importance: s.importance[index] ?? 'C', def: s.defs[index] }))
                .sort((a, b) => {
                const strengthA = a.exercise.role === 'primary_strength' || a.exercise.role === 'secondary_strength' ? 1 : 0;
                const strengthB = b.exercise.role === 'primary_strength' || b.exercise.role === 'secondary_strength' ? 1 : 0;
                if (strengthA !== strengthB)
                    return strengthA - strengthB;
                if (rank[a.importance] !== rank[b.importance])
                    return rank[a.importance] - rank[b.importance];
                const aPriority = primaryMuscle(a.def) ? request.goal.musclePriorities[primaryMuscle(a.def)] : 'normal';
                const bPriority = primaryMuscle(b.def) ? request.goal.musclePriorities[primaryMuscle(b.def)] : 'normal';
                const pr = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
                if (pr[aPriority] !== pr[bPriority])
                    return pr[aPriority] - pr[bPriority];
                return a.exercise.sets - b.exercise.sets || b.index - a.index;
            })[0];
            if (!removable)
                break;
            s.exercises.splice(removable.index, 1);
            s.defs.splice(removable.index, 1);
            s.importance.splice(removable.index, 1);
        }
        return { ...s.plan, estimatedMinutes: estimateMinutes(s.exercises), exercises: s.exercises };
    });
    // A full-body capacity contract should not create two bloated days and two sparse ones. Move whole
    // accessory prescriptions between full-body sessions first; weekly volume and progression stay intact.
    if (realized.every(s => s.intent === 'full') && (realized.some(s => s.targetExercises !== undefined) || request.preferences.lockedSplit === 'full_body')) {
        for (let pass = 0; pass < 50; pass++) {
            const under = [...realized]
                .filter(s => s.targetExercises !== undefined && s.exercises.length < Math.max(3, (s.targetExercises ?? 3) - 1))
                .sort((a, b) => (a.exercises.length - (a.targetExercises ?? 3)) - (b.exercises.length - (b.targetExercises ?? 3)) || a.id.localeCompare(b.id))[0];
            if (!under)
                break;
            const donor = [...realized]
                .filter(s => s.id !== under.id && s.exercises.length > Math.max(3, (s.targetExercises ?? 3) - 1))
                .sort((a, b) => (b.exercises.length - (b.targetExercises ?? 3)) - (a.exercises.length - (a.targetExercises ?? 3)) || b.id.localeCompare(a.id))[0];
            if (!donor)
                break;
            const chosenIds = new Set(under.exercises.map(e => e.exerciseId));
            const candidate = [...donor.exercises]
                .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter(x => x.def && x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && !chosenIds.has(x.exercise.exerciseId))
                .filter(x => {
                const remaining = donor.exercises.filter((_, i) => i !== x.index).map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                const hasPush = remaining.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily));
                const hasPull = remaining.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily));
                const hasLower = remaining.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily));
                return hasPush && hasPull && hasLower;
            })
                .sort((a, b) => {
                const fatigueA = (a.def.fatigue.systemic + a.def.fatigue.axial + a.def.fatigue.lowerBack);
                const fatigueB = (b.def.fatigue.systemic + b.def.fatigue.axial + b.def.fatigue.lowerBack);
                return fatigueA - fatigueB || a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
            })[0];
            if (!candidate)
                break;
            const proposed = [...under.exercises, candidate.exercise];
            if (estimateMinutes(proposed) > under.maxMinutes)
                break;
            donor.exercises.splice(candidate.index, 1);
            donor.estimatedMinutes = estimateMinutes(donor.exercises);
            under.exercises = proposed;
            under.estimatedMinutes = estimateMinutes(under.exercises);
        }
    }
    // If an explicit time card still leaves a session sparse, split an existing multi-set large-muscle
    // prescription across a complementary movement. This changes exercise variety, not weekly set count.
    for (const session of realized) {
        const target = session.targetExercises;
        if (target === undefined)
            continue;
        const floor = Math.max(2, target - 1);
        for (let pass = 0; pass < 8 && session.exercises.length < floor; pass++) {
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((x) => !!x);
            const sourceCandidates = session.exercises
                .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter(x => x.def && x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && x.exercise.sets >= 4)
                .map(x => {
                const primary = Object.entries(x.def.muscles).find(([, c]) => c.role === 'primary')?.[0];
                return { ...x, primary };
            })
                .filter(x => x.primary && (LARGE.includes(x.primary) || (target >= 7 && SMALL.includes(x.primary))))
                .sort((a, b) => b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
            let changed = false;
            for (const source of sourceCandidates) {
                const allocation = {
                    id: `capacity-${session.id}-${source.primary}`,
                    kind: 'muscle', muscle: source.primary, role: source.exercise.role, dose: 1, importance: 'C'
                };
                const alternate = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog);
                if (!alternate)
                    continue;
                if (alternate.movementFamily === source.def.movementFamily && LARGE.includes(source.primary))
                    continue;
                const movedSets = 2;
                if (source.exercise.sets - movedSets < 2)
                    continue;
                const reduced = { ...source.exercise, sets: source.exercise.sets - movedSets };
                const added = makePlanned(alternate, source.exercise.role, movedSets, policy, request.athlete.experience);
                const proposal = session.exercises.map((ex, i) => i === source.index ? reduced : ex).concat(added);
                const minutes = estimateMinutes(proposal);
                if (minutes > session.maxMinutes)
                    continue;
                session.exercises = proposal;
                session.estimatedMinutes = minutes;
                trackExerciseUse(alternate);
                changed = true;
                break;
            }
            if (!changed)
                break;
        }
    }
    // Last resort for an explicit time-tier contract: add useful assistance volume rather than returning a
    // technically valid but obviously underbuilt session. Additions are capped by modeled upper regions,
    // prefer muscles still below their preferred region, avoid normal front-delt filler, and must fit time.
    const capacityPrescriptions = createMusclePrescriptions(request, phase);
    const prescriptionMap = new Map(capacityPrescriptions.map(p => [p.muscle, p]));
    const intentMuscles = {
        upper: ['chest', 'back', 'side_delts', 'rear_delts', 'biceps', 'triceps'],
        lower: ['quads', 'hamstrings', 'glutes', 'calves', 'core'],
        push: ['chest', 'side_delts', 'triceps'],
        pull: ['back', 'rear_delts', 'biceps'],
        legs: ['quads', 'hamstrings', 'glutes', 'calves', 'core'],
        // Torso/Limbs is a strict named split rather than a generic fallback. Torso keeps chest/back/delts;
        // Limbs owns legs plus direct arm work. This prevents capacity filler from adding quads/glutes to Torso.
        torso: ['chest', 'back', 'side_delts', 'rear_delts'],
        limbs: ['quads', 'hamstrings', 'glutes', 'calves', 'biceps', 'triceps', 'forearms', 'core'],
        full: ['chest', 'back', 'quads', 'hamstrings', 'glutes', 'side_delts', 'rear_delts', 'biceps', 'triceps', 'calves', 'core']
    };
    const currentFractional = () => {
        const totals = Object.fromEntries(ALL_MUSCLES.map(m => [m, 0]));
        for (const session of realized)
            for (const exercise of session.exercises) {
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    continue;
                for (const [muscle, contribution] of Object.entries(def.muscles))
                    totals[muscle] += contribution.credit * exercise.sets;
            }
        return totals;
    };
    const totalsWithSessionProposal = (sessionId, proposal) => {
        const totals = Object.fromEntries(ALL_MUSCLES.map(m => [m, 0]));
        for (const session of realized) {
            const exercises = session.id === sessionId ? proposal : session.exercises;
            for (const exercise of exercises) {
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    continue;
                for (const [muscle, contribution] of Object.entries(def.muscles))
                    totals[muscle] += contribution.credit * exercise.sets;
            }
        }
        return totals;
    };
    const directTotalsWithSessionProposal = (sessionId, proposal) => {
        const totals = Object.fromEntries(ALL_MUSCLES.map(m => [m, 0]));
        for (const session of realized) {
            const exercises = session.id === sessionId ? proposal : session.exercises;
            for (const exercise of exercises) {
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    continue;
                for (const [muscle, contribution] of Object.entries(def.muscles))
                    if (contribution.credit >= 1)
                        totals[muscle] += exercise.sets;
            }
        }
        return totals;
    };
    const fitProposalBySafeDosage = (session, initial, protectedIndex) => {
        const optimizeSupersets = (exercises) => assignAccessorySupersets({ ...session, exercises, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
        let proposal = optimizeSupersets(initial.map(ex => ({ ...ex })));
        let minutes = estimateMinutes(proposal);
        if (minutes <= session.maxMinutes)
            return proposal;
        const baseline = currentFractional();
        const pr = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
        for (let guard = 0; guard < 24 && minutes > session.maxMinutes; guard++) {
            const candidates = proposal.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter(x => x.index !== protectedIndex && x.def)
                .filter(x => x.exercise.sets > (x.exercise.role === 'primary_strength' || x.exercise.role === 'secondary_strength' ? 2 : 1))
                .map(x => {
                const next = optimizeSupersets(proposal.map((ex, i) => i === x.index ? { ...ex, sets: ex.sets - 1 } : ex));
                const totals = totalsWithSessionProposal(session.id, next);
                const safe = capacityPrescriptions.filter(p => p.muscle !== 'front_delts' && p.minimum > 0).every(p => {
                    const required = Math.min(baseline[p.muscle], p.minimum * .85);
                    return totals[p.muscle] + .001 >= required;
                });
                const primary = primaryMuscle(x.def);
                const priority = primary ? (request.goal.musclePriorities[primary] ?? 'normal') : 'normal';
                const strength = x.exercise.role === 'primary_strength' || x.exercise.role === 'secondary_strength';
                const roleRank = x.exercise.role === 'primary_strength' ? 2 : x.exercise.role === 'secondary_strength' ? 1 : 0;
                const surplus = primary ? Math.max(0, baseline[primary] - (prescriptionMap.get(primary)?.minimum ?? 0)) : 0;
                return { ...x, next, safe, priority, roleRank, strength, surplus };
            })
                .filter(x => x.safe)
                .sort((a, b) => {
                if (a.strength !== b.strength)
                    return a.strength ? 1 : -1;
                if (pr[a.priority] !== pr[b.priority])
                    return pr[a.priority] - pr[b.priority];
                if (a.roleRank !== b.roleRank)
                    return a.roleRank - b.roleRank;
                return b.surplus - a.surplus || b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
            });
            const chosen = candidates[0];
            if (!chosen)
                break;
            proposal = chosen.next;
            minutes = estimateMinutes(proposal);
        }
        return minutes <= session.maxMinutes ? proposal : null;
    };
    const priorityWeight = { maintenance: .5, normal: 1, high: 1.35, specialization: 1.7, primary: 1.9 };
    for (const session of realized) {
        const target = session.targetExercises;
        if (target === undefined)
            continue;
        const floor = Math.max(2, target - 1);
        for (let pass = 0; pass < 10 && session.exercises.length < floor; pass++) {
            const totals = currentFractional();
            const muscles = (INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full)
                .filter(m => m !== 'front_delts')
                .map(m => {
                const p = prescriptionMap.get(m);
                const target = phase === 'hypertrophy_accumulation' || phase === 'mixed_accumulation'
                    ? productiveTimeBandDoseTarget(request, p, phase)
                    : p.preferred;
                return { m, p, target, deficit: p.preferred - totals[m], room: p.upper - totals[m] };
            })
                // targetExercises is a complexity/capacity reference, not an instruction to manufacture
                // optional maintenance-region filler. For modeled muscles, spare slots may still buy marginal
                // adaptive work above preferred when useful upper-region room remains, matching M50 behavior.
                .filter(x => x.target > 0 && x.p.priority !== 'maintenance' && x.room >= 2)
                .sort((a, b) => {
                const av = (Math.max(0, a.deficit) + .35 * Math.max(0, a.room)) * priorityWeight[a.p.priority];
                const bv = (Math.max(0, b.deficit) + .35 * Math.max(0, b.room)) * priorityWeight[b.p.priority];
                return bv - av || a.m.localeCompare(b.m);
            });
            let added = false;
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((x) => !!x);
            for (const option of muscles) {
                const role = SMALL.includes(option.m) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
                const allocation = { id: `capacity-add-${session.id}-${option.m}`, kind: 'muscle', muscle: option.m, role, dose: 2, importance: option.p.priority === 'specialization' || option.p.priority === 'primary' ? 'A' : option.p.priority === 'high' ? 'B' : 'C' };
                const familyCap = option.p.priority === 'high' || option.p.priority === 'specialization' || option.p.priority === 'primary' ? 2 : 1;
                const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 0, familyCap);
                if (!def)
                    continue;
                const setOptions = [2];
                for (const sets of setOptions) {
                    const exercise = makePlanned(def, role, sets, policy, request.athlete.experience);
                    const proposal = [...session.exercises, exercise];
                    const minutes = estimateMinutes(proposal);
                    if (minutes > session.maxMinutes)
                        continue;
                    session.exercises = proposal;
                    session.estimatedMinutes = minutes;
                    trackExerciseUse(def);
                    added = true;
                    break;
                }
                if (added)
                    break;
            }
            if (!added)
                break;
        }
    }
    // Priority truth check. A high/specialization target must survive realization and time repair even when
    // its allocation was inserted after normal work. This specifically prevents list-order starvation.
    const currentDirect = () => {
        const totals = Object.fromEntries(ALL_MUSCLES.map(m => [m, 0]));
        for (const session of realized)
            for (const exercise of session.exercises) {
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    continue;
                for (const [muscle, contribution] of Object.entries(def.muscles))
                    if (contribution.credit >= 1)
                        totals[muscle] += exercise.sets;
            }
        return totals;
    };
    for (const p of capacityPrescriptions.filter(p => p.priority === 'high' || p.priority === 'specialization' || p.priority === 'primary')) {
        const totals = currentFractional(), direct = currentDirect();
        const need = Math.max(0, p.minimum - totals[p.muscle], p.directMinimum - direct[p.muscle]);
        if (need < .75)
            continue;
        const sessionCandidates = [...realized].filter(session => intentAcceptsMuscle(session.intent, p.muscle)).sort((a, b) => {
            const prefs = smallIntentPreference[p.muscle] ?? [];
            const ai = prefs.indexOf(a.intent), bi = prefs.indexOf(b.intent);
            return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi) || a.estimatedMinutes - b.estimatedMinutes;
        });
        for (const session of sessionCandidates) {
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const role = SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
            const allocation = { id: `priority-repair-${session.id}-${p.muscle}`, kind: 'muscle', muscle: p.muscle, role, dose: need, importance: p.priority === 'high' ? 'B' : 'A' };
            const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog);
            if (!def)
                continue;
            const sets = Math.max(1, Math.min(4, Math.ceil(need)));
            const added = makePlanned(def, role, sets, policy, request.athlete.experience);
            const proposal = [...session.exercises, added];
            if (estimateMinutes(proposal) > session.maxMinutes)
                continue;
            session.exercises = proposal;
            session.estimatedMinutes = estimateMinutes(proposal);
            trackExerciseUse(def);
            break;
        }
    }
    // Final minimum-dose repair. If the structural phase solver left a modeled core-muscle floor just
    // short while there is still time available, spend that slack on the missing claim rather than returning
    // a knowingly incomplete program. This is intentionally floor-only: it never chases preferred volume.
    for (const p of capacityPrescriptions.filter(p => p.minimum > 0 && p.muscle !== 'front_delts')) {
        const totals = currentFractional();
        const auditFloor = p.minimum * .85;
        if (totals[p.muscle] + .001 >= auditFloor)
            continue;
        const need = Math.max(1, Math.ceil(p.minimum - totals[p.muscle]));
        const candidates = [...realized].filter(session => intentAcceptsMuscle(session.intent, p.muscle)).sort((a, b) => {
            const prefs = smallIntentPreference[p.muscle] ?? [];
            const ai = prefs.indexOf(a.intent), bi = prefs.indexOf(b.intent);
            const pref = (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
            return pref || a.estimatedMinutes - b.estimatedMinutes;
        });
        for (const session of candidates) {
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const role = SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
            const allocation = { id: `floor-repair-${session.id}-${p.muscle}`, kind: 'muscle', muscle: p.muscle, role, dose: need, importance: 'B' };
            const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 1);
            if (!def)
                continue;
            // Floor repair should actually target the missing muscle, not count a minor .25 carryover as direct work.
            if ((def.muscles[p.muscle]?.credit ?? 0) < 1)
                continue;
            const sets = Math.min(3, need);
            const added = makePlanned(def, role, sets, policy, request.athlete.experience);
            const proposal = [...session.exercises, added];
            if (estimateMinutes(proposal) > session.maxMinutes)
                continue;
            session.exercises = proposal;
            session.estimatedMinutes = estimateMinutes(proposal);
            trackExerciseUse(def);
            break;
        }
    }
    // Final structural truth check for locked/full-body cycles. Strength-specific pressing can consume the
    // modeled chest target before non-bench days receive any push slot, so repair missing regions directly
    // instead of allowing a label-only "Full Body" session.
    const repairFullBodyStructure = () => {
        if (realized.some(s => s.intent === 'full' || s.intent === 'strength_full')) {
            const regionStatus = (session) => {
                const defs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                return {
                    push: defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily)),
                    pull: defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily)),
                    lower: defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
                };
            };
            for (const session of realized.filter(s => s.intent === 'full' || s.intent === 'strength_full')) {
                for (let guard = 0; guard < 3; guard++) {
                    const status = regionStatus(session);
                    const missing = !status.push ? 'push' : !status.pull ? 'pull' : !status.lower ? 'lower' : null;
                    if (!missing)
                        break;
                    const totals = currentFractional();
                    const targetMuscles = missing === 'push' ? ['chest'] : missing === 'pull' ? ['back'] : ['quads', 'hamstrings', 'glutes'];
                    let repaired = false;
                    for (const muscle of targetMuscles.sort((a, b) => {
                        const pa = prescriptionMap.get(a), pb = prescriptionMap.get(b);
                        return (pb.preferred - totals[b]) - (pa.preferred - totals[a]);
                    })) {
                        const allocation = { id: `full-body-region-${session.id}-${muscle}`, kind: 'muscle', muscle, role: 'hypertrophy_compound', dose: 2, importance: 'B' };
                        const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                        const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog);
                        if (!def)
                            continue;
                        const structuralSets = session.maxMinutes <= 35 ? 1 : 2;
                        const added = makePlanned(def, 'hypertrophy_compound', structuralSets, policy, request.athlete.experience);
                        let proposal = [...session.exercises, added];
                        const fitted = fitProposalBySafeDosage(session, proposal, proposal.length - 1);
                        if (!fitted)
                            continue;
                        proposal = fitted;
                        const minutes = estimateMinutes(proposal);
                        session.exercises = proposal;
                        session.estimatedMinutes = minutes;
                        trackExerciseUse(def);
                        repaired = true;
                        break;
                    }
                    if (!repaired)
                        break;
                }
            }
        }
    };
    repairFullBodyStructure();
    // Structural repair can free time by reducing surplus strength dosage. Re-run the true minimum
    // floors afterwards so rear delts/core/etc. are not permanently skipped merely because the earlier
    // floor pass occurred before that clock space existed. This remains floor-only and never chases
    // preferred volume.
    const repairMinimumFloors = () => {
        const maxSetsPerExercise = request.preferences.volumeApproach === 'minimalist' ? 3 : 4;
        for (const p of capacityPrescriptions.filter(p => p.minimum > 0 && p.muscle !== 'front_delts')) {
            const totals = currentFractional();
            const auditFloor = p.minimum * .85;
            if (totals[p.muscle] + .001 >= auditFloor)
                continue;
            const need = Math.max(1, Math.ceil(p.minimum - totals[p.muscle]));
            const candidates = [...realized]
                .filter(session => intentAcceptsMuscle(session.intent, p.muscle) || floorSpilloverAllowed(session.intent, p.muscle, request))
                .sort((a, b) => {
                const an = intentAcceptsMuscle(a.intent, p.muscle), bn = intentAcceptsMuscle(b.intent, p.muscle);
                if (an !== bn)
                    return an ? -1 : 1;
                const prefs = smallIntentPreference[p.muscle] ?? [];
                const ai = prefs.indexOf(a.intent), bi = prefs.indexOf(b.intent);
                const pref = (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
                return pref || a.exercises.length - b.exercises.length || (b.maxMinutes - b.estimatedMinutes) - (a.maxMinutes - a.estimatedMinutes) || a.id.localeCompare(b.id);
            });
            for (const session of candidates) {
                const spillover = !intentAcceptsMuscle(session.intent, p.muscle);
                // Prefer extending an already-selected direct movement. It avoids another setup transition and is
                // usually the most coach-like way to repair a one-set floor miss late in realization.
                const existing = session.exercises
                    .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def)
                    .filter(x => (x.def.muscles[p.muscle]?.credit ?? 0) >= (p.directMinimum > 0 ? 1 : .25) && x.exercise.sets < maxSetsPerExercise)
                    .filter(x => !spillover || !x.def.flags.compound)
                    .sort((a, b) => a.def.setupCost - b.def.setupCost || a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId))[0];
                if (existing) {
                    const credit = Math.max(.25, existing.def.muscles[p.muscle]?.credit ?? 0);
                    const creditNeed = Math.max(1, Math.ceil((auditFloor - currentFractional()[p.muscle]) / credit));
                    const maxAdd = Math.min(creditNeed, maxSetsPerExercise - existing.exercise.sets);
                    for (let addSets = maxAdd; addSets >= 1; addSets--) {
                        const proposal = session.exercises.map((ex, i) => i === existing.index ? { ...ex, sets: ex.sets + addSets } : ex);
                        const compact = fitCompactRestToSession(session, proposal);
                        const fitted = compact ?? fitProposalBySafeDosage(session, proposal, existing.index);
                        if (!fitted)
                            continue;
                        session.exercises = fitted;
                        session.estimatedMinutes = estimateMinutes(fitted);
                        break;
                    }
                    if (currentFractional()[p.muscle] + .001 >= auditFloor)
                        break;
                }
                const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                const role = spillover || SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
                const allocation = { id: `post-structure-floor-${session.id}-${p.muscle}`, kind: 'muscle', muscle: p.muscle, role, dose: need, importance: 'B' };
                const minimumCredit = p.directMinimum > 0 ? 1 : .25;
                const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, minimumCredit, minimumCredit < 1 ? 2 : undefined);
                if (!def || (spillover && def.flags.compound))
                    continue;
                // A new cross-split movement should be a real accessory dose rather than a one-set fragment.
                // Existing direct movements may still absorb a single missing set above.
                const credit = Math.max(.25, def.muscles[p.muscle]?.credit ?? 0);
                const creditNeed = Math.max(1, Math.ceil((auditFloor - currentFractional()[p.muscle]) / credit));
                const upperRoom = Math.max(1, Math.floor((p.upper - currentFractional()[p.muscle]) / credit));
                const usefulNewSets = Math.min(maxSetsPerExercise, upperRoom, Math.max(spillover ? 2 : 1, Math.min(maxSetsPerExercise, creditNeed)));
                for (const sets of [usefulNewSets, 1]) {
                    if (spillover && sets < 2)
                        continue;
                    if (sets <= 0)
                        continue;
                    const added = makePlanned(def, role, sets, policy, request.athlete.experience);
                    let proposal = [...session.exercises, added];
                    const compact = fitCompactRestToSession(session, proposal);
                    const fitted = compact ?? fitProposalBySafeDosage(session, proposal, proposal.length - 1);
                    if (!fitted)
                        continue;
                    proposal = fitted;
                    const minutes = estimateMinutes(proposal);
                    session.exercises = proposal;
                    session.estimatedMinutes = minutes;
                    trackExerciseUse(def);
                    break;
                }
                if (currentFractional()[p.muscle] + .001 >= auditFloor)
                    break;
            }
        }
    };
    // Provisional supersets can create legitimate clock room for minimum-dose repairs. They are
    // recomputed later, so this is a planning transform rather than a permanent pairing decision.
    for (let i = 0; i < realized.length; i++)
        realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    repairMinimumFloors();
    // Minimum-dose repairs can change which low-fatigue work exists in each session. Re-run the
    // full-body structural truth pass once more so the final program—not an intermediate state—must
    // satisfy push + pull + lower on every locked Full Body day.
    repairFullBodyStructure();
    for (let i = 0; i < realized.length; i++)
        realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    repairMinimumFloors();
    // Dedicated body-part intents need enough local structure to deserve their label. Strength carryover
    // can otherwise satisfy a weekly ledger before a Chest/Shoulders/Arms day receives any assistance.
    // Add the smallest useful local prescription (normally 2 sets) and keep it under modeled upper dose.
    const dedicatedIntentMuscles = {
        upper: ['chest', 'back', 'side_delts'], lower: ['quads', 'hamstrings', 'glutes'],
        push: ['chest', 'side_delts', 'triceps'], pull: ['back', 'rear_delts', 'biceps'],
        legs: ['quads', 'hamstrings', 'glutes', 'calves'], glute: ['glutes', 'hamstrings', 'quads'],
        chest: ['chest'], back_day: ['back', 'rear_delts'], shoulders: ['side_delts', 'rear_delts'],
        arms: ['biceps', 'triceps'], chest_back: ['chest', 'back'], shoulders_arms: ['side_delts', 'biceps', 'triceps'],
        torso: ['chest', 'back', 'side_delts'], limbs: ['quads', 'hamstrings', 'biceps', 'triceps'],
        core_day: ['core', 'abductors', 'adductors'], strength_full: ['chest', 'back', 'quads', 'hamstrings']
    };
    for (const session of realized) {
        const targets = dedicatedIntentMuscles[session.intent];
        if (!targets || session.exercises.length >= 2)
            continue;
        const totals = currentFractional();
        const ordered = [...targets].sort((a, b) => {
            const pa = prescriptionMap.get(a), pb = prescriptionMap.get(b);
            const ar = (pa.preferred - totals[a]) + Math.max(0, pa.upper - totals[a]) * .25;
            const br = (pb.preferred - totals[b]) + Math.max(0, pb.upper - totals[b]) * .25;
            return br - ar || a.localeCompare(b);
        });
        for (const muscle of ordered) {
            const p = prescriptionMap.get(muscle);
            if (totals[muscle] >= p.upper)
                continue;
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const role = SMALL.includes(muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
            const allocation = { id: `intent-repair-${session.id}-${muscle}`, kind: 'muscle', muscle, role, dose: 2, importance: 'B' };
            const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 1);
            if (!def)
                continue;
            const room = Math.max(1, Math.floor(p.upper - totals[muscle]));
            const sets = Math.max(1, Math.min(2, room));
            const added = makePlanned(def, role, sets, policy, request.athlete.experience);
            const fitted = fitProposalBySafeDosage(session, [...session.exercises, added], session.exercises.length);
            if (!fitted)
                continue;
            session.exercises = fitted;
            session.estimatedMinutes = estimateMinutes(fitted);
            trackExerciseUse(def);
            break;
        }
    }
    // Repeated split intents should distribute accessories rather than leaving one day skeletal while
    // sister sessions carry all optional work. Move whole non-strength prescriptions within the same
    // intent; weekly dose is unchanged, duplicates are prohibited, and the receiving session must fit.
    const rebalanceRepeatedIntents = () => {
        const structureValid = (intent, exercises) => {
            const defs = exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const push = defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily));
            const pull = defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily));
            const lower = defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily));
            if (intent === 'full' || intent === 'strength_full')
                return push && pull && lower;
            if (intent === 'upper')
                return push && pull;
            if (intent === 'lower' || intent === 'legs' || intent === 'glute')
                return lower;
            if (intent === 'push' || intent === 'chest' || intent === 'bench_focus' || intent === 'press_focus')
                return push;
            if (intent === 'pull' || intent === 'back_day' || intent === 'deadlift_focus')
                return pull || lower;
            return true;
        };
        const groups = new Map();
        for (const session of realized) {
            const list = groups.get(session.intent) ?? [];
            list.push(session);
            groups.set(session.intent, list);
        }
        for (const group of groups.values()) {
            if (group.length < 2)
                continue;
            for (let pass = 0; pass < 40; pass++) {
                const ordered = [...group].sort((a, b) => a.exercises.length - b.exercises.length || a.id.localeCompare(b.id));
                const receiver = ordered[0], donor = ordered[ordered.length - 1];
                if (donor.exercises.length - receiver.exercises.length <= 1)
                    break;
                const receiverIds = new Set(receiver.exercises.map(ex => ex.exerciseId));
                const candidates = donor.exercises
                    .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def)
                    .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && x.exercise.role !== 'strength_support')
                    .filter(x => equipmentEligible(x.def, receiver, request))
                    .filter(x => !x.def.flags.barbell || receiver.exercises.filter(ex => exerciseMap.get(ex.exerciseId)?.flags.barbell).length < maxBarbells(receiver, request))
                    .filter(x => !receiverIds.has(x.exercise.exerciseId))
                    .filter(x => {
                    const muscle = primaryMuscle(x.def);
                    const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal';
                    const chosen = receiver.exercises.map(ex => ({ def: exerciseMap.get(ex.exerciseId), role: ex.role })).filter((x) => !!x.def);
                    return !avoidableExerciseOverlap(x.def, x.exercise.role, chosen, { priority });
                })
                    .filter(x => {
                    if (x.def.flags.compound)
                        return true;
                    const muscle = primaryMuscle(x.def);
                    const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal';
                    if (priority === 'high' || priority === 'specialization' || priority === 'primary')
                        return true;
                    const signature = (def) => Object.entries(def.muscles).sort(([a], [b]) => a.localeCompare(b)).map(([m, c]) => `${m}:${c?.credit ?? 0}`).join('|');
                    const sameFamily = receiver.exercises.map((ex, index) => ({ ex, index, def: exerciseMap.get(ex.exerciseId) })).find(y => !!y.def && !y.def.flags.compound && exerciseEconomyCluster(y.def) === exerciseEconomyCluster(x.def) && primaryMuscle(y.def) === muscle);
                    if (!sameFamily)
                        return true;
                    const donorKeepsFamily = donor.exercises.some((ex, i) => i !== x.index && exerciseEconomyCluster(exerciseMap.get(ex.exerciseId)) === exerciseEconomyCluster(x.def));
                    return donorKeepsFamily && !!sameFamily.def && signature(sameFamily.def) === signature(x.def) && sameFamily.ex.sets + x.exercise.sets <= 5;
                })
                    .sort((a, b) => {
                    const ap = primaryMuscle(a.def), bp = primaryMuscle(b.def);
                    const pr = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
                    const aPriority = ap ? (request.goal.musclePriorities[ap] ?? 'normal') : 'normal';
                    const bPriority = bp ? (request.goal.musclePriorities[bp] ?? 'normal') : 'normal';
                    if (pr[aPriority] !== pr[bPriority])
                        return pr[aPriority] - pr[bPriority];
                    const af = a.def.fatigue.systemic + a.def.fatigue.axial + a.def.fatigue.lowerBack;
                    const bf = b.def.fatigue.systemic + b.def.fatigue.axial + b.def.fatigue.lowerBack;
                    return af - bf || a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
                });
                let moved = false;
                for (const candidate of candidates) {
                    const donorProposal = donor.exercises.filter((_, i) => i !== candidate.index);
                    if (donorProposal.length < 2 || !structureValid(donor.intent, donorProposal))
                        continue;
                    const signature = (def) => Object.entries(def.muscles).sort(([a], [b]) => a.localeCompare(b)).map(([m, c]) => `${m}:${c?.credit ?? 0}`).join('|');
                    let receiverProposal;
                    const mergeIndex = !candidate.def.flags.compound ? receiver.exercises.findIndex(ex => {
                        const def = exerciseMap.get(ex.exerciseId);
                        return !!def && !def.flags.compound && exerciseEconomyCluster(def) === exerciseEconomyCluster(candidate.def) && signature(def) === signature(candidate.def) && ex.sets + candidate.exercise.sets <= 5;
                    }) : -1;
                    const donorKeepsFamily = donor.exercises.some((ex, i) => i !== candidate.index && exerciseEconomyCluster(exerciseMap.get(ex.exerciseId)) === exerciseEconomyCluster(candidate.def));
                    if (mergeIndex >= 0 && donorKeepsFamily)
                        receiverProposal = receiver.exercises.map((ex, i) => i === mergeIndex ? { ...ex, sets: ex.sets + candidate.exercise.sets } : ex);
                    else
                        receiverProposal = [...receiver.exercises, candidate.exercise];
                    const receiverOptimized = assignAccessorySupersets({ ...receiver, exercises: receiverProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (receiverOptimized.estimatedMinutes > receiver.maxMinutes)
                        continue;
                    donor.exercises = assignAccessorySupersets({ ...donor, exercises: donorProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                    donor.estimatedMinutes = estimateMinutes(donor.exercises);
                    receiver.exercises = receiverOptimized.exercises;
                    receiver.estimatedMinutes = receiverOptimized.estimatedMinutes;
                    moved = true;
                    break;
                }
                if (!moved)
                    break;
            }
        }
    };
    rebalanceRepeatedIntents();
    // Compact-session complexity reconciliation. A 30–45 minute plan should not become a circuit of
    // one-set accessories merely because weekly minima were repaired independently.
    // Remove whole low-priority assistance prescriptions when all modeled floors and the session's
    // structural promise remain intact. Priority work and strength anchors are protected.
    const finalStructureValid = (session, exercises) => {
        const defs = exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
        const push = defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily));
        const pull = defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily));
        const lower = defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily));
        if (session.intent === 'full' || session.intent === 'strength_full')
            return push && pull && lower;
        if (session.intent === 'upper')
            return push && pull;
        if (session.intent === 'lower' || session.intent === 'legs' || session.intent === 'glute')
            return lower;
        if (session.intent === 'push' || session.intent === 'chest' || session.intent === 'bench_focus' || session.intent === 'press_focus')
            return push;
        if (session.intent === 'pull' || session.intent === 'back_day' || session.intent === 'deadlift_focus')
            return pull || lower;
        return true;
    };
    // One-set duplicate consolidation. Late floor repairs can leave the same accessory as a one-set
    // fragment on several sister sessions. When one session is fragment-heavy, move an exact duplicate
    // set onto an existing copy elsewhere (up to four sets) instead of paying another setup transition.
    // Weekly dose, exercise identity, and progression policy stay unchanged; the source session must keep
    // its structural promise and the receiving session must remain inside its hard time cap.
    const consolidateOneSetDuplicates = () => {
        for (let pass = 0; pass < 80; pass++) {
            const sources = [...realized].sort((a, b) => {
                const af = a.exercises.filter(ex => ex.sets === 1).length, bf = b.exercises.filter(ex => ex.sets === 1).length;
                return bf - af || b.exercises.length - a.exercises.length || a.id.localeCompare(b.id);
            });
            let moved = false;
            for (const source of sources) {
                const oneSetCount = source.exercises.filter(ex => ex.sets === 1).length;
                const target = source.targetExercises;
                const crowded = target !== undefined && source.exercises.length > Math.max(3, target + 1);
                const extremeLongFragmentation = source.maxMinutes > 45 && oneSetCount >= 4 && source.exercises.length >= 9;
                const hasConsolidatableCompound = source.exercises.some(fragment => {
                    if (fragment.sets !== 1 || fragment.role === 'primary_strength' || fragment.role === 'secondary_strength')
                        return false;
                    const def = exerciseMap.get(fragment.exerciseId);
                    if (!def?.flags.compound)
                        return false;
                    const muscle = primaryMuscle(def);
                    return realized.some(session => session.id !== source.id && (!muscle || intentAcceptsMuscle(session.intent, muscle)) && session.exercises.some(ex => ex.exerciseId === fragment.exerciseId && ex.role === fragment.role && ex.sets < 4));
                });
                // Normal long sessions keep their planned distribution unless the engine produced an exact
                // one-set compound duplicate, which is usually better consolidated into a useful 2+ set dose.
                if (source.maxMinutes > 45 && !crowded && !extremeLongFragmentation && !hasConsolidatableCompound)
                    continue;
                if (oneSetCount <= 2 && !crowded && !hasConsolidatableCompound)
                    continue;
                for (let index = source.exercises.length - 1; index >= 0; index--) {
                    const fragment = source.exercises[index];
                    if (fragment.sets !== 1 || fragment.role === 'primary_strength' || fragment.role === 'secondary_strength')
                        continue;
                    const fragmentDef = exerciseMap.get(fragment.exerciseId);
                    const fragmentMuscle = fragmentDef ? primaryMuscle(fragmentDef) : undefined;
                    if (source.maxMinutes > 45 && !crowded && !extremeLongFragmentation && !fragmentDef?.flags.compound)
                        continue;
                    const sourceProposal = source.exercises.filter((_, i) => i !== index);
                    if (sourceProposal.length < 2 || !finalStructureValid(source, sourceProposal))
                        continue;
                    const destinations = realized
                        .filter(session => session.id !== source.id && (!fragmentMuscle || intentAcceptsMuscle(session.intent, fragmentMuscle)))
                        .map(session => {
                        const matchIndex = session.exercises.findIndex(ex => ex.exerciseId === fragment.exerciseId && ex.role === fragment.role && ex.sets < 4);
                        if (matchIndex < 0)
                            return null;
                        const proposal = session.exercises.map((ex, i) => i === matchIndex ? { ...ex, sets: ex.sets + 1 } : ex);
                        const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        return optimized.estimatedMinutes <= session.maxMinutes ? { session, matchIndex, optimized } : null;
                    })
                        .filter(Boolean);
                    destinations.sort((a, b) => a.optimized.estimatedMinutes - b.optimized.estimatedMinutes || a.session.id.localeCompare(b.session.id));
                    const chosen = destinations[0];
                    if (!chosen)
                        continue;
                    source.exercises = assignAccessorySupersets({ ...source, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                    source.estimatedMinutes = estimateMinutes(source.exercises);
                    chosen.session.exercises = chosen.optimized.exercises;
                    chosen.session.estimatedMinutes = chosen.optimized.estimatedMinutes;
                    moved = true;
                    break;
                }
                if (moved)
                    break;
            }
            if (!moved)
                break;
        }
    };
    consolidateOneSetDuplicates();
    // Compact repeated-intent balancing. A weekly plan can be perfectly valid in total yet leave one
    // 40-minute session with three one-set fragments while a sister session holds a 2+ set movement for
    // the same primary muscle. Swap those prescriptions instead of deleting work or adding time: weekly
    // exercise identity, total sets, muscle ledger, and progression cells stay unchanged, while each
    // session gets a more intentional dose shape. Strength anchors/specialization work are never moved,
    // and heterogeneous day-equipment contracts remain authoritative.
    const rebalanceOneSetFragmentsBySwap = () => {
        const movable = (ex) => !['primary_strength', 'secondary_strength', 'strength_support', 'specialization'].includes(ex.role);
        for (let pass = 0; pass < 24; pass++) {
            const source = [...realized]
                .filter(session => session.maxMinutes <= 45 && session.exercises.filter(ex => ex.sets === 1).length > 2)
                .sort((a, b) => b.exercises.filter(ex => ex.sets === 1).length - a.exercises.filter(ex => ex.sets === 1).length || a.id.localeCompare(b.id))[0];
            if (!source)
                break;
            let swapped = false;
            const fragments = source.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter((x) => !!x.def && x.exercise.sets === 1 && movable(x.exercise))
                .filter(x => { const muscle = primaryMuscle(x.def); const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal'; return priority === 'normal' || priority === 'maintenance'; });
            for (const fragment of fragments) {
                const muscle = primaryMuscle(fragment.def);
                if (!muscle)
                    continue;
                const destinations = [...realized]
                    .filter(session => session.id !== source.id && session.exercises.filter(ex => ex.sets === 1).length <= 1)
                    .sort((a, b) => a.estimatedMinutes - b.estimatedMinutes || a.id.localeCompare(b.id));
                for (const destination of destinations) {
                    if (!equipmentEligible(fragment.def, destination, request))
                        continue;
                    const receivers = destination.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def && x.exercise.sets >= 2 && movable(x.exercise))
                        .filter(x => primaryMuscle(x.def) === muscle && equipmentEligible(x.def, source, request))
                        .sort((a, b) => b.exercise.sets - a.exercise.sets || a.def.setupCost - b.def.setupCost || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                    for (const receiver of receivers) {
                        if (source.exercises.some((ex, i) => i !== fragment.index && ex.exerciseId === receiver.exercise.exerciseId))
                            continue;
                        if (destination.exercises.some((ex, i) => i !== receiver.index && ex.exerciseId === fragment.exercise.exerciseId))
                            continue;
                        const sourceProposal = source.exercises.map((ex, i) => i === fragment.index ? { ...receiver.exercise } : ex);
                        const destinationProposal = destination.exercises.map((ex, i) => i === receiver.index ? { ...fragment.exercise } : ex);
                        if (!finalStructureValid(source, sourceProposal) || !finalStructureValid(destination, destinationProposal))
                            continue;
                        const sourceOptimized = assignAccessorySupersets({ ...source, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        const destinationOptimized = assignAccessorySupersets({ ...destination, exercises: destinationProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (sourceOptimized.estimatedMinutes > source.maxMinutes || destinationOptimized.estimatedMinutes > destination.maxMinutes)
                            continue;
                        if (sourceOptimized.exercises.filter(ex => ex.sets === 1).length >= source.exercises.filter(ex => ex.sets === 1).length)
                            continue;
                        if (destinationOptimized.exercises.filter(ex => ex.sets === 1).length > 2)
                            continue;
                        source.exercises = sourceOptimized.exercises;
                        source.estimatedMinutes = sourceOptimized.estimatedMinutes;
                        destination.exercises = destinationOptimized.exercises;
                        destination.estimatedMinutes = destinationOptimized.estimatedMinutes;
                        swapped = true;
                        break;
                    }
                    if (swapped)
                        break;
                }
                if (swapped)
                    break;
            }
            if (!swapped)
                break;
        }
    };
    rebalanceOneSetFragmentsBySwap();
    // Compact-strength polish. A short session with three or four unrelated one-set accessories is
    // usually an accounting artifact, not a prescription a coach would intentionally write. Remove only
    // normal/maintenance one-set assistance that is genuinely surplus: the session must retain its split
    // identity, every modeled fractional/direct floor must be no worse than before, and high/specialized
    // priorities plus all strength-specific work stay protected. This intentionally permits a compact
    // strength session to leave some optional small-muscle work on the table rather than creating a
    // five-exercise "sampler" of single sets.
    const pruneCompactOneSetFragments = () => {
        const priorityRank = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
        const contributionSignature = (def) => Object.entries(def.muscles)
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([muscle, c]) => `${muscle}:${c?.credit ?? 0}`)
            .join('|');
        for (const source of realized) {
            for (let guard = 0; guard < 8; guard++) {
                const oneSetCount = source.exercises.filter(ex => ex.sets === 1).length;
                const fragmented = oneSetCount > 2 && (source.maxMinutes <= 45 || oneSetCount * 2 >= source.exercises.length);
                if (!fragmented)
                    break;
                // Exact-ledger consolidation comes before deletion. If a one-set assistance movement has the
                // same muscle-credit vector as an existing non-strength movement, fold that set into the
                // established prescription. This removes setup fragmentation while preserving weekly dose
                // exactly (e.g. 1 set Leg Press -> an existing Hack Squat set when both credit quads/glutes
                // identically). Cap the receiver at five sets so cleanup never creates a local mega-set block.
                let exactMerge = false;
                for (let fragmentIndex = source.exercises.length - 1; fragmentIndex >= 0 && !exactMerge; fragmentIndex--) {
                    const fragment = source.exercises[fragmentIndex];
                    if (fragment.sets !== 1 || fragment.role === 'primary_strength' || fragment.role === 'secondary_strength' || fragment.role === 'strength_support' || fragment.role === 'specialization')
                        continue;
                    const fragmentDef = exerciseMap.get(fragment.exerciseId);
                    if (!fragmentDef)
                        continue;
                    const fragmentMuscle = primaryMuscle(fragmentDef);
                    const fragmentPriority = fragmentMuscle ? (request.goal.musclePriorities[fragmentMuscle] ?? 'normal') : 'normal';
                    if (fragmentPriority === 'high' || fragmentPriority === 'specialization' || fragmentPriority === 'primary')
                        continue;
                    const signature = contributionSignature(fragmentDef);
                    for (let receiverIndex = 0; receiverIndex < source.exercises.length; receiverIndex++) {
                        if (receiverIndex === fragmentIndex)
                            continue;
                        const receiver = source.exercises[receiverIndex];
                        if (receiver.role === 'primary_strength' || receiver.role === 'secondary_strength' || receiver.role === 'strength_support' || receiver.role === 'specialization' || receiver.sets >= 5)
                            continue;
                        const receiverDef = exerciseMap.get(receiver.exerciseId);
                        if (!receiverDef || receiverDef.flags.compound !== fragmentDef.flags.compound || contributionSignature(receiverDef) !== signature)
                            continue;
                        const proposal = source.exercises
                            .filter((_, i) => i !== fragmentIndex)
                            .map(ex => ({ ...ex }));
                        const adjustedReceiverIndex = receiverIndex - (fragmentIndex < receiverIndex ? 1 : 0);
                        proposal[adjustedReceiverIndex] = { ...proposal[adjustedReceiverIndex], sets: proposal[adjustedReceiverIndex].sets + 1 };
                        const optimized = assignAccessorySupersets({ ...source, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimized.estimatedMinutes > source.maxMinutes)
                            continue;
                        source.exercises = optimized.exercises;
                        source.estimatedMinutes = optimized.estimatedMinutes;
                        exactMerge = true;
                        break;
                    }
                }
                if (exactMerge)
                    continue;
                const baseline = currentFractional(), baselineDirect = currentDirect();
                const candidates = source.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def && x.exercise.sets === 1)
                    .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && x.exercise.role !== 'strength_support')
                    .map(x => {
                    const muscle = primaryMuscle(x.def);
                    const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal';
                    if (priority === 'high' || priority === 'specialization' || priority === 'primary')
                        return null;
                    const proposal = source.exercises.filter((_, i) => i !== x.index);
                    if (proposal.length < 2)
                        return null;
                    // Some constrained split weeks intentionally distribute an Upper/Lower identity across
                    // sister sessions (for example one Upper is bench-dominant and the other pull-dominant).
                    // Fragment cleanup may not worsen that existing pattern coverage, but it should not be
                    // blocked merely because the source session was already intentionally asymmetric.
                    if (!finalStructureValid(source, proposal)) {
                        const coverage = (items) => {
                            const defs = items.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                            return {
                                push: defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily)),
                                pull: defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily)),
                                lower: defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
                            };
                        };
                        const before = coverage(source.exercises), after = coverage(proposal);
                        if ((before.push && !after.push) || (before.pull && !after.pull) || (before.lower && !after.lower))
                            return null;
                    }
                    const totals = totalsWithSessionProposal(source.id, proposal), direct = directTotalsWithSessionProposal(source.id, proposal);
                    const safe = capacityPrescriptions.filter(p => p.muscle !== 'front_delts').every(p => {
                        const requiredFractional = Math.min(baseline[p.muscle], p.minimum * .85);
                        const requiredDirect = Math.min(baselineDirect[p.muscle], p.directMinimum);
                        return totals[p.muscle] + .001 >= requiredFractional && direct[p.muscle] + .001 >= requiredDirect;
                    });
                    if (!safe)
                        return null;
                    const prescription = muscle ? prescriptionMap.get(muscle) : undefined;
                    const surplus = muscle ? Math.max(0, baseline[muscle] - (prescription?.minimum ?? 0)) : 0;
                    return { ...x, muscle, priority, proposal, surplus };
                })
                    .filter(Boolean);
                candidates.sort((a, b) => priorityRank[a.priority] - priorityRank[b.priority]
                    || (a.def.flags.compound ? 1 : 0) - (b.def.flags.compound ? 1 : 0)
                    || b.surplus - a.surplus
                    || b.def.setupCost - a.def.setupCost
                    || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                const chosen = candidates[0];
                if (!chosen)
                    break;
                const optimized = assignAccessorySupersets({ ...source, exercises: chosen.proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                source.exercises = optimized.exercises;
                source.estimatedMinutes = optimized.estimatedMinutes;
            }
        }
    };
    pruneCompactOneSetFragments();
    // Coach-like accessory consolidation. Use the same semantic economy slot as candidate selection
    // instead of raw movementFamily labels: complementary functions (curl biases, triceps positions,
    // calf knee angles, core patterns, etc.) stay distinct while interchangeable variants share a slot.
    // High-priority/specialization work may deliberately use a second variant when local dose requires
    // it; ordinary work should earn more dose before it pays another setup transition.
    const consolidateRedundantAccessoryFamilies = () => {
        for (const session of realized) {
            const groups = new Map();
            for (const [index, exercise] of session.exercises.entries()) {
                if (exercise.role === 'primary_strength' || exercise.role === 'secondary_strength' || exercise.role === 'strength_support')
                    continue;
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def || def.flags.compound)
                    continue;
                const muscle = primaryMuscle(def);
                const cluster = exerciseEconomyCluster(def);
                if (!cluster)
                    continue;
                const key = `${cluster}|${muscle ?? 'generic'}`;
                const list = groups.get(key) ?? [];
                list.push({ exercise, index, def, muscle });
                groups.set(key, list);
            }
            for (const entries of groups.values()) {
                if (entries.length < 2)
                    continue;
                const muscles = new Set(entries.map(x => x.muscle).filter(Boolean));
                if (muscles.size !== 1)
                    continue;
                const muscle = [...muscles][0];
                const priority = request.goal.musclePriorities[muscle] ?? 'normal';
                if (priority === 'high' || priority === 'specialization' || priority === 'primary')
                    continue;
                const combined = entries.reduce((sum, x) => sum + x.exercise.sets, 0);
                if (combined < 2)
                    continue;
                const ranked = [...entries].sort((a, b) => {
                    const score = (x) => x.def.suitability.hypertrophy * .5 + x.def.stability * .15 - x.def.setupCost * .12 - x.def.fatigue.systemic * .08;
                    return score(b) - score(a) || a.index - b.index;
                });
                const keeper = ranked[0];
                if (combined <= 5) {
                    const remove = new Set(entries.filter(x => x !== keeper).map(x => x.index));
                    session.exercises = session.exercises.filter((_, index) => !remove.has(index));
                    // Filtering changes indexes before the keeper when a duplicate happened to precede it, so
                    // resolve by stable exercise id after removal.
                    const kept = session.exercises.findIndex(ex => ex.exerciseId === keeper.exercise.exerciseId);
                    if (kept >= 0)
                        session.exercises[kept] = { ...session.exercises[kept], sets: combined };
                    session.estimatedMinutes = estimateMinutes(session.exercises);
                    continue;
                }
                // If one useful movement cannot hold the full normal-priority dose, move one whole duplicate
                // to another compatible weekly exposure instead of leaving two interchangeable variants in
                // the same session. This preserves weekly sets and avoids creating a one-set fragment.
                const mover = ranked.slice(1).sort((a, b) => b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId))[0];
                if (!mover)
                    continue;
                const destinations = realized
                    .filter(target => target.id !== session.id && intentAcceptsMuscle(target.intent, muscle))
                    .filter(target => !target.exercises.some(ex => exerciseEconomyCluster(exerciseMap.get(ex.exerciseId)) === exerciseEconomyCluster(mover.def)))
                    .map(target => {
                    if (target.targetExercises !== undefined && target.exercises.length >= target.targetExercises + 2)
                        return null;
                    const optimized = assignAccessorySupersets({ ...target, exercises: [...target.exercises, { ...mover.exercise }], estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    return optimized.estimatedMinutes <= target.maxMinutes ? { target, optimized } : null;
                })
                    .filter(Boolean);
                destinations.sort((a, b) => a.optimized.estimatedMinutes - b.optimized.estimatedMinutes || a.target.id.localeCompare(b.target.id));
                const destination = destinations[0];
                if (!destination)
                    continue;
                session.exercises = session.exercises.filter((_, index) => index !== mover.index);
                session.estimatedMinutes = estimateMinutes(session.exercises);
                destination.target.exercises = destination.optimized.exercises;
                destination.target.estimatedMinutes = destination.optimized.estimatedMinutes;
            }
        }
    };
    consolidateRedundantAccessoryFamilies();
    for (const session of realized) {
        if (session.targetExercises === undefined)
            continue;
        // An exercise-count selection is a complexity budget at every time tier, not only on short days.
        // Long sessions can use more sets/rest; they should not fill spare clock time with setup-heavy
        // accessory sprawl. Permit one exercise of practical slack for floor/identity repair.
        const maxExercises = Math.max(3, session.targetExercises + 1);
        for (let guard = 0; guard < 12 && session.exercises.length > maxExercises; guard++) {
            const baseline = currentFractional();
            const candidates = session.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter((x) => !!x.def)
                .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength')
                .map(x => {
                const primary = primaryMuscle(x.def);
                const priority = primary ? (request.goal.musclePriorities[primary] ?? 'normal') : 'normal';
                const proposal = session.exercises.filter((_, i) => i !== x.index);
                const totals = totalsWithSessionProposal(session.id, proposal);
                const safe = finalStructureValid(session, proposal) && capacityPrescriptions.filter(p => p.muscle !== 'front_delts' && p.minimum > 0).every(p => {
                    const required = Math.min(baseline[p.muscle], p.minimum * .85);
                    return totals[p.muscle] + .001 >= required;
                });
                const fatigue = x.def.fatigue.systemic + x.def.fatigue.axial + x.def.fatigue.lowerBack;
                return { ...x, primary, priority, proposal, safe, fatigue };
            })
                .filter(x => x.safe && x.priority !== 'primary' && x.priority !== 'specialization' && x.priority !== 'high')
                .sort((a, b) => {
                const roleA = a.exercise.role === 'hypertrophy_isolation' ? 0 : 1, roleB = b.exercise.role === 'hypertrophy_isolation' ? 0 : 1;
                return roleA - roleB || a.exercise.sets - b.exercise.sets || b.fatigue - a.fatigue || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
            });
            const chosen = candidates[0];
            if (!chosen)
                break;
            session.exercises = chosen.proposal;
            session.estimatedMinutes = estimateMinutes(session.exercises);
        }
    }
    // Realized spillover sanity repair. Topology can validly move a small arm/delt claim onto a Lower
    // day, but later global-ledger satisfaction may erase a planned hamstring/glute exercise and leave
    // the final session as "Squat + curls." When that happens, move the upper accessory back to the
    // least-loaded upper-compatible session instead of diluting the Lower/Leg identity. Weekly sets and
    // the exact exercise prescription are preserved.
    const upperAccessoryFamilies = new Set(['lateral_raise', 'rear_delt', 'elbow_flexion', 'elbow_extension', 'grip', 'wrist_flexion', 'wrist_extension', 'wrist_deviation']);
    const upperAccessoryIntents = new Set(['upper', 'push', 'pull', 'arms', 'shoulders', 'shoulders_arms', 'chest', 'back_day', 'chest_back', 'torso', 'bench_focus', 'press_focus']);
    const lowerMovementFamilies = new Set(['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion', 'calf', 'hip_abduction', 'hip_adduction', 'dorsiflexion']);
    for (const source of realized.filter(session => session.intent === 'lower' || session.intent === 'legs')) {
        for (let guard = 0; guard < 4; guard++) {
            const lowerCount = source.exercises.filter(ex => lowerMovementFamilies.has(exerciseMap.get(ex.exerciseId)?.movementFamily ?? '')).length;
            const accessoryEntries = source.exercises
                .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter((x) => !!x.def)
                .filter(x => upperAccessoryFamilies.has(x.def.movementFamily) && !x.def.flags.compound);
            if (lowerCount >= 2 || !accessoryEntries.length)
                break;
            let moved = false;
            for (const item of accessoryEntries.sort((a, b) => a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId))) {
                const sourceProposal = source.exercises.filter((_, i) => i !== item.index);
                if (!sourceProposal.some(ex => lowerMovementFamilies.has(exerciseMap.get(ex.exerciseId)?.movementFamily ?? '')))
                    continue;
                const destinations = realized
                    .filter(session => session.id !== source.id && upperAccessoryIntents.has(session.intent))
                    .map(session => {
                    const existing = session.exercises.findIndex(ex => ex.exerciseId === item.exercise.exerciseId && ex.role === item.exercise.role);
                    let proposal;
                    if (existing >= 0) {
                        if (session.exercises[existing].sets + item.exercise.sets > 5)
                            return null;
                        proposal = session.exercises.map((ex, i) => i === existing ? { ...ex, sets: ex.sets + item.exercise.sets } : ex);
                    }
                    else {
                        const muscle = primaryMuscle(item.def);
                        const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal';
                        const chosen = session.exercises.map(ex => ({ def: exerciseMap.get(ex.exerciseId), role: ex.role }))
                            .filter((x) => !!x.def);
                        if (avoidableExerciseOverlap(item.def, item.exercise.role, chosen, { priority })) {
                            const cluster = exerciseEconomyCluster(item.def);
                            const mergeIndex = session.exercises.findIndex(ex => {
                                const def = exerciseMap.get(ex.exerciseId);
                                return !!def && !def.flags.compound && exerciseEconomyCluster(def) === cluster && primaryMuscle(def) === muscle && ex.sets + item.exercise.sets <= 5;
                            });
                            if (mergeIndex < 0)
                                return null;
                            proposal = session.exercises.map((ex, i) => i === mergeIndex ? { ...ex, sets: ex.sets + item.exercise.sets } : ex);
                        }
                        else
                            proposal = [...session.exercises, { ...item.exercise }];
                    }
                    const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    return optimized.estimatedMinutes <= session.maxMinutes ? { session, optimized } : null;
                })
                    .filter(Boolean);
                destinations.sort((a, b) => a.optimized.estimatedMinutes - b.optimized.estimatedMinutes || a.session.id.localeCompare(b.session.id));
                const target = destinations[0];
                if (!target)
                    continue;
                source.exercises = assignAccessorySupersets({ ...source, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                source.estimatedMinutes = estimateMinutes(source.exercises);
                target.session.exercises = target.optimized.exercises;
                target.session.estimatedMinutes = target.optimized.estimatedMinutes;
                moved = true;
                break;
            }
            if (!moved)
                break;
        }
    }
    // Supersets are a time-management transform, never a substitute for strength rest.
    for (let i = 0; i < realized.length; i++)
        realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    // Advanced techniques are scarce transforms, not extra exercises or automatic failure work.
    // Only high-priority hypertrophy work can consume the phase budget, and only when session time still fits.
    let techniquesUsed = 0;
    const priorityRank = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
    const candidates = realized.flatMap((session, sessionIndex) => session.exercises.map((exercise, exerciseIndex) => {
        const def = exerciseMap.get(exercise.exerciseId);
        const primaryMuscle = def ? Object.entries(def.muscles).find(([, c]) => c.role === 'primary')?.[0] : undefined;
        const priority = primaryMuscle ? request.goal.musclePriorities[primaryMuscle] : 'normal';
        return { sessionIndex, exerciseIndex, def, priority };
    })).filter(x => x.def).sort((a, b) => priorityRank[b.priority] - priorityRank[a.priority] || a.sessionIndex - b.sessionIndex || a.exerciseIndex - b.exerciseIndex);
    for (const candidate of candidates) {
        if (techniquesUsed >= policy.advancedTechniqueBudget)
            break;
        const session = realized[candidate.sessionIndex];
        const exercise = session.exercises[candidate.exerciseIndex];
        const technique = chooseAdvancedTechnique(candidate.def, exercise, request.athlete.experience, candidate.priority, techniquesUsed, policy.advancedTechniqueBudget);
        if (!technique)
            continue;
        const proposed = { ...exercise, advancedTechnique: technique };
        const exercises = session.exercises.map((x, i) => i === candidate.exerciseIndex ? proposed : x);
        const minutes = estimateMinutes(exercises);
        if (minutes > session.maxMinutes)
            continue;
        session.exercises = exercises;
        session.estimatedMinutes = minutes;
        techniquesUsed++;
    }
    // One-set hypertrophy compounds are usually setup-heavy fragments rather than useful prescriptions.
    // Prefer a second set when the primary muscle is still below its preferred region; otherwise remove
    // the fragment when floors and session identity remain intact. Strength-specific single sets are
    // intentionally exempt because low-volume peak work can be legitimate.
    for (const session of realized) {
        for (let index = session.exercises.length - 1; index >= 0; index--) {
            const exercise = session.exercises[index];
            if (exercise.sets !== 1 || exercise.role !== 'hypertrophy_compound')
                continue;
            const def = exerciseMap.get(exercise.exerciseId);
            const muscle = def ? primaryMuscle(def) : undefined;
            if (!def || !muscle)
                continue;
            const prescription = prescriptionMap.get(muscle);
            const totals = currentFractional();
            if (prescription && totals[muscle] + .001 < prescription.preferred) {
                const proposal = session.exercises.map((ex, i) => i === index ? { ...ex, sets: 2 } : ex);
                const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                if (proposedTotals[muscle] <= prescription.upper + 0.001) {
                    const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (optimized.estimatedMinutes <= session.maxMinutes) {
                        session.exercises = optimized.exercises;
                        session.estimatedMinutes = optimized.estimatedMinutes;
                        continue;
                    }
                }
            }
            const proposal = session.exercises.filter((_, i) => i !== index);
            if (proposal.length < 2 || !finalStructureValid(session, proposal))
                continue;
            const proposedTotals = totalsWithSessionProposal(session.id, proposal);
            const safe = capacityPrescriptions.filter(p => p.muscle !== 'front_delts' && p.minimum > 0).every(p => proposedTotals[p.muscle] + .001 >= p.minimum * .85);
            if (!safe)
                continue;
            const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
            session.exercises = optimized.exercises;
            session.estimatedMinutes = optimized.estimatedMinutes;
        }
    }
    // Time-bucket floor reconciliation. A bucket such as 40–60 minutes is not merely a 60-minute cap:
    // its lower edge is the amount of training time the athlete said they can reliably devote. M48
    // collapsed that contract to maxMinutes only, so a technically valid 28–35 minute session could
    // escape from the 40–60 bucket with productive volume still available.
    //
    // Move toward 95% of the lower edge (the old engine's practical tolerance), but only with useful
    // work. Accumulation phases may use some of the adaptive region above `preferred` when the athlete
    // explicitly selected a longer time bucket; later phases progressively stop doing that so tapering
    // remains real. Never cross the modeled upper region or the user's upper time edge.
    const averageMinimumMinutes = request.schedule.days.reduce((sum, day) => sum + (day.minMinutes ?? 0), 0) / Math.max(1, request.schedule.days.length);
    const accumulationFloorExpansion = phase === 'hypertrophy_accumulation'
        ? (averageMinimumMinutes >= 120 ? .72 : averageMinimumMinutes >= 90 ? .62 : averageMinimumMinutes >= 60 ? .52 : .45)
        : phase === 'mixed_accumulation'
            ? (averageMinimumMinutes >= 120 ? .54 : averageMinimumMinutes >= 90 ? .47 : averageMinimumMinutes >= 60 ? .40 : .32)
            : 0;
    const latePhaseExpansion = phase === 'strength_accumulation' ? .15 : 0;
    const usefulTarget = (p) => {
        if (phase === 'hypertrophy_accumulation' || phase === 'mixed_accumulation') {
            if (p.priority === 'maintenance' && p.minimum === 0 && p.preferred === 0)
                return 0;
            return Math.min(p.upper, Math.max(productiveTimeBandDoseTarget(request, p, phase), p.preferred + Math.round(Math.max(0, p.upper - p.preferred) * accumulationFloorExpansion)));
        }
        return Math.min(p.upper, p.preferred + Math.round(Math.max(0, p.upper - p.preferred) * latePhaseExpansion));
    };
    for (const session of realized) {
        const lower = session.minMinutes ?? 0;
        const productiveFloor = lower > 0 ? lower * .95 : 0;
        if (productiveFloor <= 0)
            continue;
        session.estimatedMinutes = estimateMinutes(session.exercises);
        for (let guard = 0; guard < 24 && session.estimatedMinutes + 0.001 < productiveFloor; guard++) {
            const totals = currentFractional();
            const options = session.exercises.map((exercise, index) => {
                const depthCap = exercise.role === 'hypertrophy_isolation' && (session.minMinutes ?? 0) >= 90 ? 5 : 4;
                if (exercise.role === 'primary_strength' || exercise.role === 'secondary_strength' || exercise.sets >= depthCap)
                    return null;
                const def = exerciseMap.get(exercise.exerciseId);
                const muscle = def ? primaryMuscle(def) : undefined;
                if (!def || !muscle)
                    return null;
                if (muscle === 'front_delts' && request.goal.musclePriorities.front_delts === 'normal')
                    return null;
                const prescription = prescriptionMap.get(muscle);
                if (!prescription)
                    return null;
                const target = usefulTarget(prescription);
                if (target <= 0 || totals[muscle] + .001 >= target)
                    return null;
                const proposal = session.exercises.map((ex, i) => i === index ? { ...ex, sets: ex.sets + 1 } : ex);
                const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                if (proposedTotals[muscle] > prescription.upper + .001)
                    return null;
                const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                if (optimized.estimatedMinutes > session.maxMinutes)
                    return null;
                const deficit = (target - totals[muscle]) / Math.max(1, target);
                const priority = priorityRank[request.goal.musclePriorities[muscle] ?? 'normal'];
                return { index, muscle, deficit, priority, optimized, currentSets: exercise.sets };
            }).filter(Boolean);
            options.sort((a, b) => b.priority - a.priority || b.deficit - a.deficit || a.currentSets - b.currentSets || a.index - b.index);
            const best = options[0];
            if (best) {
                session.exercises = best.optimized.exercises;
                session.estimatedMinutes = best.optimized.estimatedMinutes;
                continue;
            }
            // If useful set depth is exhausted but the session is still below the bucket floor, spend one
            // of the session's intended movement slots on the most under-served compatible muscle. This is
            // how a 40–60 card can actually produce ~5 useful movements instead of accepting four at 31m.
            const targetExercises = session.targetExercises;
            if (targetExercises === undefined || session.exercises.length >= targetExercises)
                break;
            const intentPool = (INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full)
                .map(muscle => {
                if (muscle === 'front_delts' && request.goal.musclePriorities.front_delts === 'normal')
                    return null;
                const prescription = prescriptionMap.get(muscle);
                if (!prescription)
                    return null;
                const target = usefulTarget(prescription);
                if (target <= 0 || totals[muscle] + .001 >= target)
                    return null;
                const deficit = (target - totals[muscle]) / Math.max(1, target);
                const priority = priorityRank[request.goal.musclePriorities[muscle] ?? 'normal'];
                return { muscle, prescription, target, deficit, priority };
            }).filter(Boolean);
            intentPool.sort((a, b) => b.priority - a.priority || b.deficit - a.deficit || a.muscle.localeCompare(b.muscle));
            let appended = false;
            for (const need of intentPool) {
                const allocation = {
                    id: `time-band-${session.id}-${need.muscle}-${guard}`, kind: 'muscle', muscle: need.muscle,
                    role: SMALL.includes(need.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound', dose: 2,
                    importance: (request.goal.musclePriorities[need.muscle] === 'primary' || request.goal.musclePriorities[need.muscle] === 'specialization') ? 'A' : request.goal.musclePriorities[need.muscle] === 'high' ? 'B' : 'C'
                };
                const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((x) => !!x);
                const familyCap = need.prescription.priority === 'high' || need.prescription.priority === 'specialization' || need.prescription.priority === 'primary' ? 2 : 1;
                const alternate = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 0, familyCap);
                if (!alternate)
                    continue;
                const remaining = Math.max(1, Math.floor(need.prescription.upper - totals[need.muscle]));
                const sets = Math.min(2, remaining);
                const added = makePlanned(alternate, allocation.role, sets, policy, request.athlete.experience);
                const proposal = [...session.exercises, added];
                const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                if (optimized.estimatedMinutes > session.maxMinutes)
                    continue;
                session.exercises = optimized.exercises;
                session.estimatedMinutes = optimized.estimatedMinutes;
                trackExerciseUse(alternate);
                appended = true;
                break;
            }
            if (!appended)
                break;
        }
    }
    // Allocation-target reconciliation. Time/structure repairs may trim a muscle back to its bare
    // minimum even though the allocator deliberately reserved a higher productive target for the chosen
    // time band. Restore that target when there is still compatible session room. This is the missing
    // link between M50's capacity-aware allocator and the final coach-facing program: minimum volume is
    // safety, while targetDose is the productive dose the engine actually decided was worth spending.
    if (phase === 'hypertrophy_accumulation' || phase === 'mixed_accumulation') {
        const targetPriority = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
        const targets = capacityPrescriptions
            .filter(p => p.muscle !== 'front_delts' && p.minimum > 0 && (targetDose[p.muscle] ?? 0) > 0)
            .map(p => ({ p, target: Math.min(p.upper, targetDose[p.muscle] ?? p.minimum) }))
            .sort((a, b) => targetPriority[b.p.priority] - targetPriority[a.p.priority] ||
            (LARGE.includes(b.p.muscle) ? 1 : 0) - (LARGE.includes(a.p.muscle) ? 1 : 0) ||
            b.target - a.target || a.p.muscle.localeCompare(b.p.muscle));
        for (const { p, target } of targets) {
            for (let guard = 0; guard < 12; guard++) {
                const totals = currentFractional();
                if (totals[p.muscle] + .49 >= target)
                    break;
                const sessionsByRoom = [...realized]
                    .filter(session => intentAcceptsMuscle(session.intent, p.muscle))
                    .sort((a, b) => (b.maxMinutes - b.estimatedMinutes) - (a.maxMinutes - a.estimatedMinutes) || a.id.localeCompare(b.id));
                let repaired = false;
                // Prefer deepening an existing direct movement: no new setup cost and the weekly exercise
                // selection stays stable. One set at a time keeps the repair inside the modeled upper region.
                for (const session of sessionsByRoom) {
                    const existing = session.exercises
                        .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def)
                        .filter(x => {
                        const depthCap = x.exercise.role === 'hypertrophy_isolation' && (session.minMinutes ?? 0) >= 90 ? 5 : 4;
                        return (x.def.muscles[p.muscle]?.credit ?? 0) >= 1 && x.exercise.sets < depthCap && x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength';
                    })
                        .sort((a, b) => a.exercise.sets - b.exercise.sets || a.def.setupCost - b.def.setupCost || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                    for (const x of existing) {
                        const proposal = session.exercises.map((ex, i) => i === x.index ? { ...ex, sets: ex.sets + 1 } : ex);
                        const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                        if (proposedTotals[p.muscle] > p.upper + .001)
                            continue;
                        const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimized.estimatedMinutes > session.maxMinutes)
                            continue;
                        session.exercises = optimized.exercises;
                        session.estimatedMinutes = optimized.estimatedMinutes;
                        repaired = true;
                        break;
                    }
                    if (repaired)
                        break;
                }
                if (repaired)
                    continue;
                // Before adding another setup, rebalance a locally redundant accessory. Late time repair can
                // leave one intended muscle below its productive target while another muscle in the same
                // session still carries marginal work above the band-specific target. Swap that surplus first:
                // this preserves coach-like session density and restores the weekly dose mix without clock fill.
                const needsSubfloorRebalance = sessionsByRoom.some(session => {
                    const lower = session.minMinutes ?? 0;
                    return lower > 0 && session.estimatedMinutes + 0.001 < lower * .95;
                });
                for (const session of needsSubfloorRebalance ? sessionsByRoom : []) {
                    const desiredSets = Math.max(1, Math.min(2, Math.ceil(target - totals[p.muscle])));
                    const donors = session.exercises
                        .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def)
                        .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength')
                        .map(x => {
                        const muscle = primaryMuscle(x.def);
                        const donorPrescription = muscle ? prescriptionMap.get(muscle) : undefined;
                        const donorTarget = donorPrescription ? productiveTimeBandDoseTarget(request, donorPrescription, phase) : 0;
                        const surplus = muscle ? totals[muscle] - donorTarget : 0;
                        return { ...x, muscle, donorTarget, surplus };
                    })
                        .filter(x => x.muscle && x.muscle !== p.muscle && x.surplus >= .75)
                        .sort((a, b) => b.surplus - a.surplus || a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                    for (const donor of donors) {
                        const chosenDefs = session.exercises
                            .filter((_, i) => i !== donor.index)
                            .map(ex => exerciseMap.get(ex.exerciseId))
                            .filter((d) => !!d);
                        const role = SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
                        const allocation = { id: `target-swap-${session.id}-${p.muscle}-${guard}`, kind: 'muscle', muscle: p.muscle, role, dose: desiredSets, importance: p.priority === 'primary' || p.priority === 'specialization' ? 'A' : p.priority === 'high' ? 'B' : 'C' };
                        const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 1);
                        if (!def)
                            continue;
                        const replacement = makePlanned(def, role, desiredSets, policy, request.athlete.experience);
                        const proposal = session.exercises.map((ex, i) => i === donor.index ? replacement : ex);
                        if (!finalStructureValid(session, proposal))
                            continue;
                        const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                        if (proposedTotals[p.muscle] > p.upper + .001 || proposedTotals[p.muscle] <= totals[p.muscle] + .001)
                            continue;
                        const stealsUsefulDose = capacityPrescriptions
                            .filter(q => q.muscle !== p.muscle && q.muscle !== 'front_delts' && q.minimum > 0)
                            .some(q => {
                            const qTarget = Math.max(q.preferred, productiveTimeBandDoseTarget(request, q, phase));
                            return totals[q.muscle] + .49 >= qTarget && proposedTotals[q.muscle] + .49 < qTarget;
                        });
                        if (stealsUsefulDose)
                            continue;
                        const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimized.estimatedMinutes > session.maxMinutes)
                            continue;
                        session.exercises = optimized.exercises;
                        session.estimatedMinutes = optimized.estimatedMinutes;
                        trackExerciseUse(def);
                        repaired = true;
                        break;
                    }
                    if (repaired)
                        break;
                }
                if (repaired)
                    continue;
                // If no existing movement can absorb another productive set, use an intended slot only when
                // the session still has movement capacity. Do not create a new setup merely to chase a number.
                for (const session of sessionsByRoom) {
                    if (session.targetExercises !== undefined && session.exercises.length >= session.targetExercises + (needsSubfloorRebalance ? 2 : 1))
                        continue;
                    const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                    const role = SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
                    const allocation = { id: `target-reconcile-${session.id}-${p.muscle}-${guard}`, kind: 'muscle', muscle: p.muscle, role, dose: 1, importance: p.priority === 'primary' || p.priority === 'specialization' ? 'A' : p.priority === 'high' ? 'B' : 'C' };
                    const familyCap = p.priority === 'high' || p.priority === 'specialization' || p.priority === 'primary' ? 2 : 1;
                    const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 1, familyCap);
                    if (!def)
                        continue;
                    const added = makePlanned(def, role, 1, policy, request.athlete.experience);
                    const proposal = [...session.exercises, added];
                    const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                    if (proposedTotals[p.muscle] > p.upper + .001)
                        continue;
                    const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (optimized.estimatedMinutes > session.maxMinutes)
                        continue;
                    session.exercises = optimized.exercises;
                    session.estimatedMinutes = optimized.estimatedMinutes;
                    trackExerciseUse(def);
                    repaired = true;
                    break;
                }
                if (!repaired)
                    break;
            }
        }
    }
    // User-facing MEV reconciliation. The internal allocator uses a compact muscle model, but the
    // shell exposes stricter landmarks (including separate lat/upper-back floors and direct-set
    // side/rear-delt guidance). On genuinely high-capacity accumulation plans, those visible MEVs are
    // part of the program contract: satisfy them before spending additional volume above preferred
    // elsewhere. A small reserve is built into the base program so planned late-block consolidation
    // cannot make an otherwise normal 5+ week plan fall below the displayed MEV.
    if (publicMevContractApplies(request, phase)) {
        const flexRegions = new Set(['side_delts', 'rear_delts', 'biceps', 'triceps', 'calves', 'core']);
        const roleForRegion = (region) => ['side_delts', 'rear_delts', 'biceps', 'triceps', 'calves', 'core'].includes(region) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
        const sessionAcceptsRegion = (session, region) => {
            const muscle = PUBLIC_REGION_MUSCLE[region];
            if (intentAcceptsMuscle(session.intent, muscle))
                return true;
            // Low-interference accessories may move across the nominal split boundary when that is the only
            // way to distribute a real weekly floor cleanly. This is preferable to 8–10 local sets on one
            // day and matches the engine's existing accessory-spillover philosophy.
            if (flexRegions.has(region))
                return !['chest', 'back_day', 'bench_focus', 'press_focus'].includes(session.intent) || ['side_delts', 'rear_delts', 'biceps', 'triceps'].includes(region);
            return false;
        };
        const sessionRegionPenalty = (session, region) => {
            // Cross-split accessory placement is a fallback, not a first choice. In particular, do not
            // spend the generous 60–90 minute capacity by turning Lower/Legs into an arms/delts day while
            // compatible upper sessions still have room.
            if (['side_delts', 'rear_delts', 'biceps', 'triceps'].includes(region) && ['lower', 'legs', 'limbs'].includes(session.intent))
                return 3;
            if (region === 'calves' && ['upper', 'push', 'pull', 'torso'].includes(session.intent))
                return 2;
            return 0;
        };
        const publicCandidate = (region, session) => {
            const muscle = PUBLIC_REGION_MUSCLE[region];
            const chosen = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const chosenWithRoles = session.exercises.map(ex => ({ def: exerciseMap.get(ex.exerciseId), role: ex.role })).filter((x) => !!x.def);
            const families = new Map();
            for (const def of chosen)
                families.set(def.movementFamily, (families.get(def.movementFamily) ?? 0) + 1);
            const exactUses = (id) => realized.reduce((n, s) => n + s.exercises.filter(ex => ex.exerciseId === id).length, 0);
            const barbells = chosen.filter(def => def.flags.barbell).length;
            const role = roleForRegion(region);
            return exerciseCatalog
                .filter(def => equipmentEligible(def, session, request))
                .filter(def => !request.preferences.avoidedExercises?.includes(def.id))
                .filter(def => directlyTargetsPublicRegion(def, region))
                .filter(def => !chosen.some(x => x.id === def.id))
                .filter(def => !avoidableExerciseOverlap(def, role, chosenWithRoles, { priority: request.goal.musclePriorities[muscle] ?? 'normal' }))
                .filter(def => (families.get(def.movementFamily) ?? 0) < ((region === 'lats' || region === 'upper_back') ? 2 : 1))
                .filter(def => !def.flags.barbell || barbells < maxBarbells(session, request))
                .map(def => {
                const exact = exactUses(def.id), familyUses = realized.reduce((n, s) => n + s.exercises.filter(ex => exerciseMap.get(ex.exerciseId)?.movementFamily === def.movementFamily).length, 0);
                const fatigue = def.fatigue.systemic + def.fatigue.axial * .7 + def.fatigue.lowerBack * .8;
                const base = muscleScore(def, muscle, role, session, chosen, weeklyMovementUse, request);
                const direct = publicRegionContribution(def, region);
                return { def, score: base + direct * 4 - exact * 3.5 - familyUses * .12 - fatigue * .04 };
            })
                .sort((a, b) => b.score - a.score || a.def.id.localeCompare(b.def.id))[0]?.def;
        };
        const prescriptionByMuscle = new Map(capacityPrescriptions.map(p => [p.muscle, p]));
        const priorityRankPublic = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
        const orderedRegions = [...PUBLIC_MEV_REGIONS]
            .filter(region => publicMevRequired(request, region))
            .sort((a, b) => priorityRankPublic[request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[b]] ?? 'normal'] - priorityRankPublic[request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[a]] ?? 'normal']
            || publicMevBaseTarget(request, b) - publicMevBaseTarget(request, a) || a.localeCompare(b));
        for (const region of orderedRegions) {
            const muscle = PUBLIC_REGION_MUSCLE[region];
            const internal = prescriptionByMuscle.get(muscle);
            if (!internal)
                continue;
            const target = publicMevBaseTarget(request, region);
            for (let guard = 0; guard < 24; guard++) {
                const publicLedger = publicMevLedger(realized, exerciseMap);
                if (publicLedger[region] + .001 >= target)
                    break;
                const sessionsByRoom = [...realized]
                    .filter(session => sessionAcceptsRegion(session, region))
                    .sort((a, b) => sessionRegionPenalty(a, region) - sessionRegionPenalty(b, region) || (b.maxMinutes - b.estimatedMinutes) - (a.maxMinutes - a.estimatedMinutes) || a.id.localeCompare(b.id));
                let repaired = false;
                // First deepen a direct movement already earning this public region's floor. This is usually
                // the cleanest fix because it adds no setup or duplicate movement family.
                for (const session of sessionsByRoom) {
                    const existing = session.exercises
                        .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def)
                        .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && directlyTargetsPublicRegion(x.def, region))
                        .filter(x => x.exercise.sets < 5)
                        .sort((a, b) => a.exercise.sets - b.exercise.sets || a.def.setupCost - b.def.setupCost || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                    for (const item of existing) {
                        const proposal = session.exercises.map((ex, i) => i === item.index ? { ...ex, sets: ex.sets + 1 } : ex);
                        // Public MEV is a stricter visible floor than the coarse internal upper model for some
                        // composite/secondary-heavy muscles. The public target itself bounds this repair, so do
                        // not let the private upper estimate veto a still-unmet user-facing floor.
                        const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimized.estimatedMinutes > session.maxMinutes)
                            continue;
                        session.exercises = optimized.exercises;
                        session.estimatedMinutes = optimized.estimatedMinutes;
                        repaired = true;
                        break;
                    }
                    if (repaired)
                        break;
                }
                if (repaired)
                    continue;
                // Add a complementary direct movement only when deepening is exhausted. Exact weekly repeats
                // and same-session family duplicates are strongly avoided; small accessory floors may use a
                // cross-split destination to keep per-session dosage coach-like.
                for (const session of sessionsByRoom) {
                    const def = publicCandidate(region, session);
                    if (!def)
                        continue;
                    const remaining = target - publicLedger[region];
                    const sets = Math.max(1, Math.min(def.flags.compound ? 3 : 4, Math.ceil(remaining / Math.max(.001, publicRegionContribution(def, region)))));
                    const added = makePlanned(def, roleForRegion(region), sets, policy, request.athlete.experience);
                    const proposal = [...session.exercises, added];
                    const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (optimized.estimatedMinutes > session.maxMinutes)
                        continue;
                    session.exercises = optimized.exercises;
                    session.estimatedMinutes = optimized.estimatedMinutes;
                    trackExerciseUse(def);
                    repaired = true;
                    break;
                }
                if (!repaired)
                    break;
            }
        }
        // Coach-like weekly distribution: when a muscle already has multiple direct exposures, avoid
        // concentrating nearly all movable hypertrophy work in one of them. Strength anchors are never
        // moved. This is deliberately conservative: a heavy/light 70/30-ish split is fine; the repair
        // only acts on extreme direct-set concentration that can be improved without changing weekly dose.
        const directRegionDose = (session, region) => session.exercises.reduce((sum, exercise) => {
            const def = exerciseMap.get(exercise.exerciseId);
            return sum + (def && directlyTargetsPublicRegion(def, region) ? exercise.sets * publicRegionContribution(def, region) : 0);
        }, 0);
        const concentrationLimit = request.athlete.experience === 'novice' ? .7 : request.athlete.experience === 'advanced' ? .8 : .75;
        for (const region of orderedRegions) {
            if (!publicMevRequired(request, region))
                continue;
            for (let guard = 0; guard < 16; guard++) {
                const doses = realized.map((session, index) => ({ session, index, dose: directRegionDose(session, region) }));
                const exposed = doses.filter(x => x.dose > .001);
                const total = exposed.reduce((sum, x) => sum + x.dose, 0);
                if (total < 6 || (exposed.length < 2 && !flexRegions.has(region)))
                    break;
                const source = [...exposed].sort((a, b) => b.dose - a.dose || a.index - b.index)[0];
                const publicPriority = request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[region]] ?? 'normal';
                const specialized = publicPriority === 'high' || publicPriority === 'specialization' || publicPriority === 'primary';
                const sourceFamilyCounts = new Map();
                for (const exercise of source.session.exercises) {
                    const def = exerciseMap.get(exercise.exerciseId);
                    if (!def || def.flags.compound || exercise.role === 'primary_strength' || exercise.role === 'secondary_strength' || !directlyTargetsPublicRegion(def, region))
                        continue;
                    sourceFamilyCounts.set(def.movementFamily, (sourceFamilyCounts.get(def.movementFamily) ?? 0) + 1);
                }
                const duplicateAccessoryFamily = [...sourceFamilyCounts.values()].some(count => count > 1);
                let allowed = Math.max(4, Math.floor(total * concentrationLimit));
                // Normal-priority work should fit in one useful accessory family per exposure whenever the
                // week already offers another compatible exposure. If a sixth+ direct set would force two
                // same-family isolations, move that dose across the week instead. Specialization can earn it.
                if (!specialized && (flexRegions.has(region) || duplicateAccessoryFamily))
                    allowed = Math.min(allowed, 5);
                if (source.dose <= allowed + .001)
                    break;
                const sourceMovable = source.session.exercises
                    .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def)
                    .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && directlyTargetsPublicRegion(x.def, region))
                    .filter(x => x.exercise.sets > 1)
                    .sort((a, b) => b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                if (!sourceMovable.length)
                    break;
                const targetCandidates = doses
                    .filter(x => x.index !== source.index && sessionAcceptsRegion(x.session, region))
                    .sort((a, b) => sessionRegionPenalty(a.session, region) - sessionRegionPenalty(b.session, region) || a.dose - b.dose || (b.session.maxMinutes - b.session.estimatedMinutes) - (a.session.maxMinutes - a.session.estimatedMinutes) || a.index - b.index);
                let moved = false;
                for (const target of targetCandidates) {
                    const existing = target.session.exercises
                        .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def)
                        .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && directlyTargetsPublicRegion(x.def, region) && x.exercise.sets < 5)
                        .sort((a, b) => a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId))[0];
                    const sourceItem = sourceMovable[0];
                    if (existing) {
                        const sourceProposal = source.session.exercises.map((ex, i) => i === sourceItem.index ? { ...ex, sets: ex.sets - 1 } : ex);
                        const targetProposal = target.session.exercises.map((ex, i) => i === existing.index ? { ...ex, sets: ex.sets + 1 } : ex);
                        const optimizedTarget = assignAccessorySupersets({ ...target.session, exercises: targetProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimizedTarget.estimatedMinutes > target.session.maxMinutes)
                            continue;
                        source.session.exercises = assignAccessorySupersets({ ...source.session, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                        source.session.estimatedMinutes = estimateMinutes(source.session.exercises);
                        target.session.exercises = optimizedTarget.exercises;
                        target.session.estimatedMinutes = optimizedTarget.estimatedMinutes;
                        moved = true;
                        break;
                    }
                    if (target.session.targetExercises !== undefined && target.session.exercises.length >= target.session.targetExercises + 2)
                        continue;
                    const def = publicCandidate(region, target.session);
                    if (!def)
                        continue;
                    const added = makePlanned(def, roleForRegion(region), 1, policy, request.athlete.experience);
                    const optimizedTarget = assignAccessorySupersets({ ...target.session, exercises: [...target.session.exercises, added], estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (optimizedTarget.estimatedMinutes > target.session.maxMinutes)
                        continue;
                    const sourceProposal = source.session.exercises.map((ex, i) => i === sourceItem.index ? { ...ex, sets: ex.sets - 1 } : ex);
                    source.session.exercises = assignAccessorySupersets({ ...source.session, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                    source.session.estimatedMinutes = estimateMinutes(source.session.exercises);
                    target.session.exercises = optimizedTarget.exercises;
                    target.session.estimatedMinutes = optimizedTarget.estimatedMinutes;
                    trackExerciseUse(def);
                    moved = true;
                    break;
                }
                if (!moved)
                    break;
            }
        }
        // Public-floor and distribution repairs can add a new accessory after the earlier M52 cleanup.
        // Re-run the same consolidation rule so a repair cannot regress into mergeable 1–2 set variants.
        consolidateRedundantAccessoryFamilies();
        for (let i = 0; i < realized.length; i++)
            realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    }
    // Late floor/public-volume work can re-create a lopsided repeated-intent week after the earlier
    // balancing pass. Re-run the dose-neutral whole-exercise balancer now that all useful work exists.
    // The balancer is destination-equipment/barbell aware, so heterogeneous M58 day contracts remain
    // authoritative while full-body/upper/lower sister sessions avoid needless complexity spikes.
    rebalanceRepeatedIntents();
    // Final hard-limit reconciliation. Late floor/structure repairs can legitimately change a session
    // after the initial time trim, so re-run the same floor-aware dosage fitter on the finished session.
    // No movement is protected here, but an exercise can never be reduced below one set and the fitter
    // refuses any decrement that creates a new modeled muscle-floor deficit.
    for (const session of realized) {
        session.estimatedMinutes = estimateMinutes(session.exercises);
        if (session.estimatedMinutes <= session.maxMinutes)
            continue;
        const compact = fitCompactRestToSession(session, session.exercises);
        if (compact) {
            session.exercises = compact;
            session.estimatedMinutes = estimateMinutes(compact);
            continue;
        }
        const fitted = fitProposalBySafeDosage(session, session.exercises, -1);
        if (!fitted)
            continue;
        session.exercises = fitted;
        session.estimatedMinutes = estimateMinutes(fitted);
    }
    // Late time/public-floor reconciliation can create fresh one-set assistance after the earlier
    // complexity pass. Compact plans outside the high-capacity public-MEV contract get one final
    // surplus-fragment cleanup at the true end of realization; high-capacity plans keep their visible
    // MEV reserve untouched.
    if (!publicMevContractApplies(request, phase))
        pruneCompactOneSetFragments();
    if (request.preferences.volumeApproach === 'minimalist') {
        // Same movement plan, lower dose, harder work. Keep heavy strength anchors just shy of true
        // failure while hypertrophy work lives at the 0–1 RIR target promised by the UI.
        // IMPORTANT: this cap used to run after all floor repairs and could therefore undo them (for
        // example 4 squat sets repaired to the quad floor were silently cut back to 3). Keep the
        // per-exercise <=3 contract, then re-run the floor allocator with that same cap so any remaining
        // deficit is distributed to another useful movement rather than invalidating the finished block.
        for (let i = 0; i < realized.length; i++) {
            const exercises = realized[i].exercises.map(ex => {
                const strength = ex.role === 'primary_strength' || ex.role === 'secondary_strength';
                return { ...ex, sets: Math.min(3, ex.sets), prescription: { ...ex.prescription, rir: strength ? [1, 2] : [0, 1] } };
            });
            realized[i] = { ...realized[i], exercises, estimatedMinutes: estimateMinutes(exercises) };
        }
        for (let i = 0; i < realized.length; i++)
            realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
        repairMinimumFloors();
        for (let i = 0; i < realized.length; i++)
            realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    }
    // Final anatomical arm-function reconciliation. Initial selection already prefers complementary
    // curl/wrist functions, but later phase retargeting, time trimming, minimalist caps and public-floor
    // repairs can legitimately change the realized menu after those tiebreakers ran. If that leaves a
    // *major* arm-coverage contract unmet, repair it transactionally here rather than letting a valid
    // priority request fail at the final arbiter. This remains one volume ledger: the repair adds only
    // the missing movement exposure and the existing safe-dosage fitter may reclaim a lower-priority
    // set when clock time is tight.
    const repairMajorArmCoverage = () => {
        const biasForCode = (code) => code === 'ARM_BICEPS_BIAS_MISSING' ? 'biceps_bias' :
            code === 'ARM_BRACHIALIS_BIAS_MISSING' || code === 'ARM_BRACHIORADIALIS_UNDERSERVED' ? 'brachialis_bias' :
                code === 'WRIST_FLEXION_MISSING' ? 'wrist_flexion' :
                    code === 'WRIST_EXTENSION_MISSING' ? 'wrist_extension' : null;
        const muscleForBias = (bias) => bias === 'biceps_bias' || bias === 'brachialis_bias' ? 'biceps' : 'forearms';
        const intentRank = (intent, muscle) => {
            const order = muscle === 'forearms'
                ? ['arms', 'shoulders_arms', 'limbs', 'pull', 'upper', 'back_day', 'full', 'strength_full']
                : ['arms', 'shoulders_arms', 'pull', 'back_day', 'upper', 'limbs', 'full', 'strength_full'];
            const i = order.indexOf(intent);
            return i < 0 ? 99 : i;
        };
        for (let guard = 0; guard < 8; guard++) {
            const finding = evaluateArmCoverage(realized, request, exerciseMap).findings.find(f => f.severity === 'major');
            if (!finding)
                break;
            const bias = biasForCode(finding.code);
            if (!bias)
                break;
            const muscle = muscleForBias(bias);
            const defs = exerciseCatalog
                .filter(def => armCoverageBias(def) === bias)
                .filter(def => !request.preferences.avoidedExercises?.includes(def.id))
                .sort((a, b) => b.suitability.hypertrophy - a.suitability.hypertrophy || a.setupCost - b.setupCost || a.id.localeCompare(b.id));
            const destinations = realized
                .filter(session => intentAcceptsMuscle(session.intent, muscle))
                .sort((a, b) => intentRank(a.intent, muscle) - intentRank(b.intent, muscle) || (b.maxMinutes - b.estimatedMinutes) - (a.maxMinutes - a.estimatedMinutes) || a.id.localeCompare(b.id));
            let repaired = false;
            for (const session of destinations) {
                if (session.targetExercises !== undefined && session.exercises.length >= session.targetExercises + 2)
                    continue;
                for (const def of defs) {
                    if (!equipmentEligible(def, session, request))
                        continue;
                    if (session.exercises.some(ex => ex.exerciseId === def.id))
                        continue;
                    const chosen = session.exercises.map(ex => ({ def: exerciseMap.get(ex.exerciseId), role: ex.role })).filter((x) => !!x.def);
                    if (avoidableExerciseOverlap(def, 'hypertrophy_isolation', chosen, { priority: request.goal.musclePriorities[muscle] ?? 'normal' }))
                        continue;
                    const added = makePlanned(def, 'hypertrophy_isolation', 1, policy, request.athlete.experience);
                    const initial = [...session.exercises, added];
                    const fitted = fitProposalBySafeDosage(session, initial, initial.length - 1);
                    if (!fitted)
                        continue;
                    session.exercises = fitted;
                    session.estimatedMinutes = estimateMinutes(fitted);
                    trackExerciseUse(def);
                    repaired = true;
                    break;
                }
                if (repaired)
                    break;
            }
            if (!repaired)
                break;
        }
    };
    repairMajorArmCoverage();
    // One last semantic-economy reconciliation sits after every floor, distribution, spillover and
    // anatomical repair. This is the invariant boundary: no late pass may silently reintroduce an
    // avoidable same-slot accessory pairing that initial candidate selection correctly filtered.
    consolidateRedundantAccessoryFamilies();
    for (let i = 0; i < realized.length; i++)
        realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    // Final coach-like sequencing pass. Repairs above may add or move exercises late in realization,
    // so ordering belongs at the true end of the pipeline. It changes no dose, prescription, or time
    // budget: it only protects high-value work from avoidable local pre-fatigue and makes supersets
    // contiguous in the rendered workout.
    for (let i = 0; i < realized.length; i++)
        realized[i] = sequenceSessionExercises(realized[i], exerciseMap);
    return realized;
}
