function coverageText(def) {
    return `${def.id} ${def.name} ${def.legacyPattern ?? ''} ${def.legacySubregion ?? ''}`.toLowerCase();
}
function meaningfulDirectCredit(def, muscle) {
    const c = def.muscles[muscle]?.credit ?? 0;
    // Function coverage is about deliberate stimulus selection, not incidental quarter-credit overlap.
    return c >= .5 ? c : 0;
}
export function functionalCoverageBiases(def) {
    const biases = [];
    const text = coverageText(def);
    if (meaningfulDirectCredit(def, 'hamstrings')) {
        if (def.movementFamily === 'knee_flexion')
            biases.push('hamstrings_knee_flexion');
        if (def.movementFamily === 'hip_hinge' || def.movementFamily === 'hip_extension')
            biases.push('hamstrings_hip_extension');
    }
    if (meaningfulDirectCredit(def, 'calves') && def.movementFamily === 'calf') {
        if (/seated|bent[ -]?knee|soleus/.test(text))
            biases.push('calves_bent_knee');
        else
            biases.push('calves_straight_knee');
    }
    if (meaningfulDirectCredit(def, 'triceps') && def.movementFamily === 'elbow_extension') {
        // Overhead work is the clearest long-head lengthened option. Skullcrusher/lying/PJR-style
        // extensions also deliberately train the long head at longer muscle lengths than pushdowns.
        if (/overhead|\boh\b|skull|lying|pjr|skullover/.test(text))
            biases.push('triceps_lengthened');
        else
            biases.push('triceps_non_overhead');
    }
    if (meaningfulDirectCredit(def, 'back')) {
        if (def.movementFamily === 'vertical_pull')
            biases.push('back_vertical_pull');
        if (def.movementFamily === 'horizontal_pull')
            biases.push('back_horizontal_row');
    }
    return biases;
}
export function functionalCoverageUseKeys(def) {
    return functionalCoverageBiases(def).map(bias => `#function:${bias}`);
}
export function deriveFunctionalCoverage(sessions, exerciseMap) {
    const ledger = {
        hamstrings: { totalDirectSets: 0, hipExtensionSets: 0, kneeFlexionSets: 0 },
        calves: { totalDirectSets: 0, straightKneeSets: 0, bentKneeSets: 0 },
        triceps: { totalDirectSets: 0, lengthenedSets: 0, nonOverheadSets: 0 },
        back: { totalDirectSets: 0, verticalPullSets: 0, horizontalRowSets: 0, shoulderExtensionSets: 0 }
    };
    for (const session of sessions)
        for (const exercise of session.exercises) {
            const def = exerciseMap.get(exercise.exerciseId);
            if (!def)
                continue;
            const sets = Math.max(0, Number(exercise.sets) || 0);
            const hamCredit = meaningfulDirectCredit(def, 'hamstrings');
            const calfCredit = meaningfulDirectCredit(def, 'calves');
            const triCredit = meaningfulDirectCredit(def, 'triceps');
            const backCredit = meaningfulDirectCredit(def, 'back');
            const biases = functionalCoverageBiases(def);
            if (hamCredit) {
                ledger.hamstrings.totalDirectSets += sets * hamCredit;
                if (biases.includes('hamstrings_hip_extension'))
                    ledger.hamstrings.hipExtensionSets += sets * hamCredit;
                if (biases.includes('hamstrings_knee_flexion'))
                    ledger.hamstrings.kneeFlexionSets += sets * hamCredit;
            }
            if (calfCredit) {
                ledger.calves.totalDirectSets += sets * calfCredit;
                if (biases.includes('calves_straight_knee'))
                    ledger.calves.straightKneeSets += sets * calfCredit;
                if (biases.includes('calves_bent_knee'))
                    ledger.calves.bentKneeSets += sets * calfCredit;
            }
            if (triCredit && def.movementFamily === 'elbow_extension') {
                ledger.triceps.totalDirectSets += sets * triCredit;
                if (biases.includes('triceps_lengthened'))
                    ledger.triceps.lengthenedSets += sets * triCredit;
                if (biases.includes('triceps_non_overhead'))
                    ledger.triceps.nonOverheadSets += sets * triCredit;
            }
            if (backCredit && ['vertical_pull', 'horizontal_pull', 'shoulder_extension'].includes(def.movementFamily)) {
                ledger.back.totalDirectSets += sets * backCredit;
                if (biases.includes('back_vertical_pull'))
                    ledger.back.verticalPullSets += sets * backCredit;
                if (biases.includes('back_horizontal_row'))
                    ledger.back.horizontalRowSets += sets * backCredit;
                if (def.movementFamily === 'shoulder_extension')
                    ledger.back.shoulderExtensionSets += sets * backCredit;
            }
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
export function evaluateFunctionalCoverage(sessions, request, exerciseMap) {
    const ledger = deriveFunctionalCoverage(sessions, exerciseMap);
    const findings = [];
    const defs = [...exerciseMap.values()];
    const available = (bias) => defs.some(def => functionalCoverageBiases(def).includes(bias) && setupAvailable(def, request) && !request.preferences.avoidedExercises?.includes(def.id));
    const severityFor = (muscle) => priorityRank(request.goal.musclePriorities[muscle]) >= 2 ? 'major' : 'warning';
    // These contracts deliberately begin only after the muscle has enough direct work to support two
    // distinct functions. Sparse plans are allowed to choose the single highest-value pattern instead
    // of paying an exercise-diversity tax they cannot afford.
    if (ledger.hamstrings.totalDirectSets >= 5) {
        const severity = severityFor('hamstrings');
        if (available('hamstrings_hip_extension') && ledger.hamstrings.hipExtensionSets < 1)
            findings.push({
                code: 'HAMSTRINGS_HIP_EXTENSION_MISSING', severity,
                message: `Hamstrings receive ${ledger.hamstrings.totalDirectSets.toFixed(1)} meaningful direct sets but no hip-extension/hinge exposure.`
            });
        if (available('hamstrings_knee_flexion') && ledger.hamstrings.kneeFlexionSets < 1)
            findings.push({
                code: 'HAMSTRINGS_KNEE_FLEXION_MISSING', severity,
                message: `Hamstrings receive ${ledger.hamstrings.totalDirectSets.toFixed(1)} meaningful direct sets but no knee-flexion exposure.`
            });
    }
    if (ledger.calves.totalDirectSets >= 5) {
        const severity = severityFor('calves');
        if (available('calves_straight_knee') && ledger.calves.straightKneeSets < 1)
            findings.push({
                code: 'CALVES_STRAIGHT_KNEE_MISSING', severity,
                message: `Calves receive ${ledger.calves.totalDirectSets.toFixed(1)} direct sets but no straight-knee calf pattern.`
            });
        if (available('calves_bent_knee') && ledger.calves.bentKneeSets < 1)
            findings.push({
                code: 'CALVES_BENT_KNEE_MISSING', severity,
                message: `Calves receive ${ledger.calves.totalDirectSets.toFixed(1)} direct sets but no bent-knee calf pattern for deliberate soleus coverage.`
            });
    }
    if (ledger.triceps.totalDirectSets >= 5) {
        const severity = severityFor('triceps');
        if (available('triceps_lengthened') && ledger.triceps.lengthenedSets < 1)
            findings.push({
                code: 'TRICEPS_LENGTHENED_EXTENSION_MISSING', severity,
                message: `Direct triceps extension totals ${ledger.triceps.totalDirectSets.toFixed(1)} sets but has no overhead/lengthened extension pattern.`
            });
        if (available('triceps_non_overhead') && ledger.triceps.nonOverheadSets < 1)
            findings.push({
                code: 'TRICEPS_NON_OVERHEAD_EXTENSION_MISSING', severity,
                message: `Direct triceps extension totals ${ledger.triceps.totalDirectSets.toFixed(1)} sets but has no non-overhead extension pattern.`
            });
    }
    if (ledger.back.totalDirectSets >= 6) {
        const severity = severityFor('back');
        if (available('back_vertical_pull') && ledger.back.verticalPullSets < 1)
            findings.push({
                code: 'BACK_VERTICAL_PULL_MISSING', severity,
                message: `Back receives ${ledger.back.totalDirectSets.toFixed(1)} meaningful direct sets but no vertical-pull exposure.`
            });
        if (available('back_horizontal_row') && ledger.back.horizontalRowSets < 1)
            findings.push({
                code: 'BACK_HORIZONTAL_ROW_MISSING', severity,
                message: `Back receives ${ledger.back.totalDirectSets.toFixed(1)} meaningful direct sets but no horizontal-row exposure.`
            });
    }
    return { ledger, findings };
}
