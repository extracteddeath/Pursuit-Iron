const ZERO_WEIGHTED = {
    biceps_brachii: 0, brachialis: 0, brachioradialis: 0, wrist_flexors: 0, wrist_extensors: 0
};
function armText(def) {
    return `${def.id} ${def.name} ${def.legacyPattern ?? ''} ${def.legacySubregion ?? ''}`.toLowerCase();
}
/**
 * Direct elbow-flexion work is split into two programming biases instead of pretending every curl is
 * anatomically identical. Neutral/pronated/hammer/reverse-style curls bias brachialis and
 * brachioradialis; ordinary supinated curls bias biceps brachii. Both still receive fractional credit
 * for the other elbow flexors because those tissues cannot be fully isolated in a curl.
 */
export function armCoverageBias(def) {
    if (def.movementFamily === 'wrist_flexion')
        return 'wrist_flexion';
    if (def.movementFamily === 'wrist_extension')
        return 'wrist_extension';
    if (def.movementFamily !== 'elbow_flexion')
        return null;
    const text = armText(def);
    const brachialisBiased = /neutral|hammer|reverse|pronat|brachialis|brachioradialis|pinwheel/.test(text);
    return brachialisBiased ? 'brachialis_bias' : 'biceps_bias';
}
export function armCoverageContribution(def) {
    const bias = armCoverageBias(def);
    if (bias === 'wrist_flexion')
        return { wrist_flexors: 1 };
    if (bias === 'wrist_extension')
        return { wrist_extensors: 1 };
    if (bias === 'brachialis_bias')
        return { biceps_brachii: .5, brachialis: 1, brachioradialis: .85 };
    if (bias === 'biceps_bias')
        return { biceps_brachii: 1, brachialis: .5, brachioradialis: .25 };
    return {};
}
export function armCoverageUseKeys(def) {
    const bias = armCoverageBias(def);
    if (!bias)
        return [];
    const contribution = armCoverageContribution(def);
    return [`#arm:${bias}`, ...Object.keys(contribution).map(region => `#arm-region:${region}`)];
}
export function deriveArmCoverage(sessions, exerciseMap) {
    const ledger = {
        weightedSets: { ...ZERO_WEIGHTED }, directElbowFlexionSets: 0, directWristFlexionSets: 0, directWristExtensionSets: 0,
        bicepsBiasedSets: 0, brachialisBiasedSets: 0
    };
    for (const session of sessions)
        for (const exercise of session.exercises) {
            const def = exerciseMap.get(exercise.exerciseId);
            if (!def)
                continue;
            const sets = Math.max(0, Number(exercise.sets) || 0);
            const bias = armCoverageBias(def);
            if (bias === 'biceps_bias') {
                ledger.directElbowFlexionSets += sets;
                ledger.bicepsBiasedSets += sets;
            }
            else if (bias === 'brachialis_bias') {
                ledger.directElbowFlexionSets += sets;
                ledger.brachialisBiasedSets += sets;
            }
            else if (bias === 'wrist_flexion')
                ledger.directWristFlexionSets += sets;
            else if (bias === 'wrist_extension')
                ledger.directWristExtensionSets += sets;
            for (const [region, credit] of Object.entries(armCoverageContribution(def)))
                ledger.weightedSets[region] += sets * credit;
        }
    return ledger;
}
function priorityRank(priority) {
    return priority === 'primary' ? 4 : priority === 'specialization' ? 3 : priority === 'high' ? 2 : priority === 'normal' || priority === undefined ? 1 : 0;
}
function setupAvailable(def, request) {
    if ((def.flags.bodyweight || def.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
        return false;
    const setups = [def.equipment, ...(def.equipmentAlternatives ?? [])];
    return request.schedule.days.some(day => {
        const equipment = day.equipmentOverride ?? request.equipment.available;
        return setups.some(setup => setup.every(item => item === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : equipment.includes(item)));
    });
}
export function evaluateArmCoverage(sessions, request, exerciseMap) {
    const ledger = deriveArmCoverage(sessions, exerciseMap);
    const findings = [];
    const defs = [...exerciseMap.values()];
    const available = (bias) => defs.some(def => armCoverageBias(def) === bias && setupAvailable(def, request) && !request.preferences.avoidedExercises?.includes(def.id));
    const bicepsPriority = priorityRank(request.goal.musclePriorities.biceps);
    const forearmPriority = priorityRank(request.goal.musclePriorities.forearms);
    // A meaningful direct-curl allocation should not collapse to one grip orientation. Low-dose plans
    // (1-4 direct sets/week) may intentionally use one efficient curl, so the diversity contract starts
    // at five direct sets unless biceps itself is explicitly prioritized.
    const elbowCoverageRequired = ledger.directElbowFlexionSets >= 5;
    if (elbowCoverageRequired) {
        const severity = bicepsPriority >= 2 ? 'major' : 'warning';
        if (available('biceps_bias') && ledger.bicepsBiasedSets < 1)
            findings.push({
                code: 'ARM_BICEPS_BIAS_MISSING', severity,
                message: `Direct elbow-flexion work totals ${ledger.directElbowFlexionSets.toFixed(1)} sets but has no biceps-brachii-biased curl pattern.`
            });
        if (available('brachialis_bias') && ledger.brachialisBiasedSets < 1)
            findings.push({
                code: 'ARM_BRACHIALIS_BIAS_MISSING', severity,
                message: `Direct elbow-flexion work totals ${ledger.directElbowFlexionSets.toFixed(1)} sets but has no neutral/pronated curl pattern to deliberately cover brachialis and brachioradialis.`
            });
        if (available('brachialis_bias') && ledger.weightedSets.brachioradialis < 1)
            findings.push({
                code: 'ARM_BRACHIORADIALIS_UNDERSERVED', severity,
                message: `Brachioradialis receives only ${ledger.weightedSets.brachioradialis.toFixed(1)} weighted direct sets despite meaningful elbow-flexor volume.`
            });
    }
    // Wrist flexion and extension are a paired function contract whenever direct wrist training is
    // deliberately present, and become a hard repair condition when forearms are explicitly prioritized.
    const directWrist = ledger.directWristFlexionSets + ledger.directWristExtensionSets;
    const wristCoverageRequired = forearmPriority >= 2 || directWrist > 0;
    if (wristCoverageRequired) {
        const severity = forearmPriority >= 2 ? 'major' : 'warning';
        if (available('wrist_flexion') && ledger.directWristFlexionSets < 1)
            findings.push({
                code: 'WRIST_FLEXION_MISSING', severity,
                message: 'Forearm programming includes/prioritizes direct wrist work but has no wrist-flexion exercise.'
            });
        if (available('wrist_extension') && ledger.directWristExtensionSets < 1)
            findings.push({
                code: 'WRIST_EXTENSION_MISSING', severity,
                message: 'Forearm programming includes/prioritizes direct wrist work but has no wrist-extension exercise.'
            });
    }
    return { ledger, findings };
}
