import { armCoverageBias } from './arm-coverage.js';
import { functionalCoverageBiases } from './functional-coverage.js';
import { exerciseSelectionRelationship } from './exercise-selection-intelligence.js';
const STRENGTH_ROLES = new Set(['primary_strength', 'secondary_strength', 'strength_support']);
const SPECIALIZATION_PRIORITIES = new Set(['high', 'specialization', 'primary']);
function economyText(def) {
    return `${def.id} ${def.name} ${def.legacyPattern ?? ''} ${def.legacySubregion ?? ''} ${def.legacyPart ?? ''}`.toLowerCase();
}
function inferredUnilateral(def) {
    if (def.flags.unilateral)
        return true;
    return /\b(single[- ]?(?:arm|leg)|one[- ]arm|unilateral|b[- ]stance)\b/.test(economyText(def));
}
export function primaryDirectMuscles(def) {
    if (!def)
        return [];
    return Object.entries(def.muscles)
        .filter(([, c]) => !!c && c.credit >= 1)
        .map(([muscle]) => muscle)
        .sort();
}
function weightedMuscleSimilarity(a, b) {
    const muscles = new Set([
        ...Object.keys(a.muscles),
        ...Object.keys(b.muscles)
    ]);
    let intersection = 0, union = 0;
    for (const muscle of muscles) {
        const av = (a.muscles[muscle]?.credit ?? 0) >= .5 ? (a.muscles[muscle]?.credit ?? 0) : 0;
        const bv = (b.muscles[muscle]?.credit ?? 0) >= .5 ? (b.muscles[muscle]?.credit ?? 0) : 0;
        intersection += Math.min(av, bv);
        union += Math.max(av, bv);
    }
    return union > 0 ? intersection / union : 0;
}
/** A competition/transfer-oriented anchor may legitimately precede a lower-fatigue hypertrophy movement. */
export function isStrengthSpecificAnchor(def) {
    if (!def)
        return false;
    const specificity = Math.max(0, ...Object.values(def.liftSpecificity ?? {}));
    return specificity >= .7 && def.suitability.strength >= 5;
}
function primaryBias(def) {
    const primaries = primaryDirectMuscles(def);
    if (primaries.length)
        return primaries.join('+');
    const direct = Object.entries(def.muscles)
        .filter(([, c]) => !!c && c.credit >= .5).sort((a, b) => (b[1]?.credit ?? 0) - (a[1]?.credit ?? 0));
    return direct[0]?.[0] ?? 'generic';
}
/**
 * Semantic same-slot key used for exercise economy.
 *
 * It is deliberately more specific than movementFamily. Complementary functions keep separate keys:
 * row vs pulldown, hamstring hinge vs curl, overhead vs non-overhead triceps, straight- vs bent-knee
 * calves, biceps-biased vs brachialis-biased curls, and wrist flexion vs extension. Near-equivalent
 * variants (Incline Smith vs Incline DB, Machine Row vs Neutral-Grip Machine Row, two lateral raises)
 * share a key so the realizer can consolidate them instead of paying another setup transition.
 */
export function exerciseEconomyCluster(def) {
    if (!def)
        return null;
    const text = economyText(def);
    const laterality = inferredUnilateral(def) ? 'unilateral' : 'bilateral';
    const kind = def.flags.compound ? 'compound' : 'isolation';
    const pattern = (def.legacyPattern ?? '').toLowerCase();
    const sub = (def.legacySubregion ?? '').toLowerCase();
    const part = (def.legacyPart ?? '').toLowerCase();
    switch (def.movementFamily) {
        case 'squat':
        case 'leg_press':
            return `knee_dominant_${laterality}_${kind}`;
        case 'horizontal_press': {
            const angle = pattern.includes('incline') || /\bincline\b/.test(text) ? 'incline'
                : /\bdecline\b/.test(text) ? 'decline'
                    : /\bdip\b/.test(text) ? 'dip'
                        : /\bfloor\b/.test(text) ? 'floor'
                            : 'flat';
            return `chest_press_${angle}_${laterality}_${kind}`;
        }
        case 'vertical_press': {
            const fn = pattern.includes('shoulder-flexion') || /front raise/.test(text) ? 'front_raise'
                : pattern.includes('scapular-upward') || /\by[- ]raise\b/.test(text) ? 'scapular_raise'
                    : /landmine/.test(text) ? 'landmine_press'
                        : 'overhead_press';
            return `${fn}_${laterality}_${kind}`;
        }
        case 'chest_adduction':
            return `chest_adduction_${laterality}_${kind}`;
        case 'horizontal_pull': {
            // Bodyweight row mechanics can be materially different loading solutions in equipment-scarce
            // programs (for example an inverted row versus an upright doorway row). Keep those separate;
            // machine/cable/dumbbell grip variants still consolidate by regional job.
            const mechanic = /doorway row/.test(text) ? 'doorway_bodyweight' : /inverted row/.test(text) ? 'inverted_bodyweight' : null;
            const bias = mechanic ?? (part === 'lats' || sub.includes('lat') ? 'lat_bias' : part === 'upper_back' || sub.includes('upper_back') ? 'upper_back_bias' : 'row');
            return `horizontal_row_${bias}_${laterality}_${kind}`;
        }
        case 'vertical_pull':
            return `vertical_pull_${laterality}_${kind}`;
        case 'shoulder_extension':
            return `shoulder_extension_${laterality}_${kind}`;
        case 'lateral_raise':
            return `lateral_raise_${kind}`;
        case 'rear_delt': {
            const fn = /face pull/.test(text) ? 'face_pull' : /\brow\b/.test(text) ? 'rear_delt_row' : 'rear_delt_fly';
            return `${fn}_${kind}`;
        }
        case 'hip_hinge':
            return `hip_hinge_${primaryBias(def)}_${laterality}_${kind}`;
        case 'hip_extension':
            return `hip_extension_${primaryBias(def)}_${laterality}_${kind}`;
        case 'knee_flexion':
            return `knee_flexion_${kind}`;
        case 'knee_extension':
            return `knee_extension_${kind}`;
        case 'elbow_flexion': {
            const armBias = armCoverageBias(def);
            const bias = armBias === 'brachialis_bias' ? 'brachialis_bias' : armBias === 'biceps_bias' ? 'biceps_bias'
                : /hammer|reverse[- ]?grip|reverse curl|pronated/.test(text) ? 'brachialis_bias' : 'biceps_bias';
            return `elbow_flexion_${bias}_${kind}`;
        }
        case 'elbow_extension': {
            const biases = functionalCoverageBiases(def);
            const fn = biases.includes('triceps_lengthened') ? 'lengthened' : 'non_overhead';
            return `elbow_extension_${fn}_${kind}`;
        }
        case 'calf': {
            const biases = functionalCoverageBiases(def);
            const fn = biases.includes('calves_bent_knee') ? 'bent_knee' : 'straight_knee';
            return `calf_${fn}_${kind}`;
        }
        case 'core':
            return `core_${pattern || (/side bend/.test(text) ? 'lateral-flexion' : 'generic')}_${kind}`;
        case 'shrug':
            return `shrug_${primaryBias(def)}_${kind}`;
        case 'grip':
            return `grip_${sub || pattern || 'static'}_${kind}`;
        case 'wrist_flexion':
            return `wrist_flexion_${kind}`;
        case 'wrist_extension':
            return `wrist_extension_${kind}`;
        case 'wrist_deviation': {
            const fn = /radial/.test(text) ? 'radial' : /ulnar/.test(text) ? 'ulnar' : 'generic';
            return `wrist_deviation_${fn}_${kind}`;
        }
        case 'hip_abduction':
        case 'hip_adduction':
            return `${def.movementFamily}_${primaryBias(def)}_${kind}`;
        case 'neck': {
            const fn = /lateral/.test(text) ? 'lateral' : /extension|harness/.test(text) ? 'extension' : /curl|flexion/.test(text) ? 'flexion' : 'generic';
            return `neck_${fn}_${kind}`;
        }
        case 'dorsiflexion':
            return `dorsiflexion_${kind}`;
        default:
            return `${def.movementFamily}_${primaryBias(def)}_${kind}`;
    }
}
/** M81 compatibility helper: the original reviewed quad compound cluster. */
export function compoundEconomyCluster(def) {
    if (!def?.flags.compound)
        return null;
    if (def.movementFamily !== 'squat' && def.movementFamily !== 'leg_press')
        return null;
    return inferredUnilateral(def) ? 'knee_dominant_unilateral' : 'knee_dominant_bilateral';
}
function sameSemanticSlot(a, b) {
    const clusterA = exerciseEconomyCluster(a), clusterB = exerciseEconomyCluster(b);
    if (clusterA && clusterA === clusterB && weightedMuscleSimilarity(a, b) >= .67)
        return true;
    // M182 knowledge-graph fallback catches near-equivalent variants whose legacy family/subregion
    // metadata differs even though their stimulus and functional slot are effectively the same. The
    // relationship model keeps complementary functions (row/pulldown, triceps positions, calf knee
    // angles, biceps/brachialis) in separate subslots, so broader intelligence does not erase variety.
    const relationship = exerciseSelectionRelationship(a, b);
    return relationship.nearDuplicate && relationship.stimulusSimilarity >= .72;
}
function specializationAllowsSecond(priority, role) {
    return role === 'specialization' || (priority !== undefined && SPECIALIZATION_PRIORITIES.has(priority));
}
/**
 * True when adding candidate would create an avoidable same-session setup. Strength-specific work and
 * complementary functional slots are preserved. High-priority/specialization work may use two variants in one slot,
 * but never duplicate the exact same exercise or grow to three same-slot variants.
 */
export function avoidableExerciseOverlap(candidate, candidateRole, chosen, context = {}) {
    if (STRENGTH_ROLES.has(candidateRole) || isStrengthSpecificAnchor(candidate))
        return false;
    const comparable = chosen.filter(({ def, role }) => {
        if (role && STRENGTH_ROLES.has(role))
            return false;
        if (isStrengthSpecificAnchor(def))
            return false;
        return sameSemanticSlot(candidate, def);
    });
    if (comparable.some(({ def }) => def.id === candidate.id))
        return true;
    if (!comparable.length)
        return false;
    if (specializationAllowsSecond(context.priority, candidateRole))
        return comparable.length >= 2;
    return true;
}
/** M81 compatibility helper retained for targeted compound tests. */
export function avoidableCompoundOverlap(candidate, candidateRole, chosen) {
    if (!candidate.flags.compound || (candidate.movementFamily !== 'squat' && candidate.movementFamily !== 'leg_press'))
        return false;
    if (STRENGTH_ROLES.has(candidateRole) || isStrengthSpecificAnchor(candidate))
        return false;
    const cluster = compoundEconomyCluster(candidate);
    return chosen.some(({ def, role }) => {
        if (role && STRENGTH_ROLES.has(role))
            return false;
        if (isStrengthSpecificAnchor(def))
            return false;
        return cluster !== null && compoundEconomyCluster(def) === cluster && weightedMuscleSimilarity(candidate, def) >= .67;
    });
}
export function redundantExercisePairs(exercises, defs, priorityForMuscle) {
    const out = [];
    for (let i = 0; i < exercises.length; i++) {
        const a = exercises[i], aDef = defs.get(a.exerciseId);
        if (!aDef || STRENGTH_ROLES.has(a.role) || isStrengthSpecificAnchor(aDef))
            continue;
        const cluster = exerciseEconomyCluster(aDef);
        if (!cluster)
            continue;
        for (let j = i + 1; j < exercises.length; j++) {
            const b = exercises[j], bDef = defs.get(b.exerciseId);
            if (!bDef || STRENGTH_ROLES.has(b.role) || isStrengthSpecificAnchor(bDef))
                continue;
            if (!sameSemanticSlot(aDef, bDef))
                continue;
            const exact = a.exerciseId === b.exerciseId;
            const primary = primaryDirectMuscles(aDef)[0] ?? primaryDirectMuscles(bDef)[0];
            const specialized = !exact && primary && SPECIALIZATION_PRIORITIES.has(priorityForMuscle?.(primary) ?? 'normal');
            if (specialized) {
                const sameSlotCount = exercises.filter(ex => {
                    const def = defs.get(ex.exerciseId);
                    return !!def && !STRENGTH_ROLES.has(ex.role) && !isStrengthSpecificAnchor(def) && sameSemanticSlot(aDef, def);
                }).length;
                if (sameSlotCount <= 2)
                    continue;
            }
            out.push({ a, b, cluster, kind: aDef.flags.compound && bDef.flags.compound ? 'compound' : 'accessory' });
        }
    }
    return out;
}
export function redundantCompoundPairs(exercises, defs) {
    const out = [];
    for (let i = 0; i < exercises.length; i++) {
        const a = exercises[i], aDef = defs.get(a.exerciseId), cluster = compoundEconomyCluster(aDef);
        if (!cluster || STRENGTH_ROLES.has(a.role) || isStrengthSpecificAnchor(aDef))
            continue;
        for (let j = i + 1; j < exercises.length; j++) {
            const b = exercises[j], bDef = defs.get(b.exerciseId);
            if (!bDef || STRENGTH_ROLES.has(b.role) || isStrengthSpecificAnchor(bDef))
                continue;
            if (compoundEconomyCluster(bDef) !== cluster || weightedMuscleSimilarity(aDef, bDef) < .67)
                continue;
            out.push({ a, b, cluster });
        }
    }
    return out;
}
