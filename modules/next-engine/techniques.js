const LENGTHENED_IDS = new Set(['cable_fly', 'preacher_curl', 'overhead_cable_triceps', 'seated_leg_curl', 'calf_raise', 'cable_lateral_raise']);
function eligibleTypes(ex) {
    const out = [];
    if (!ex.flags.barbell && ex.stability >= 5 && !ex.flags.compound)
        out.push('myo_reps', 'drop_set');
    else if (!ex.flags.barbell && ex.stability >= 6 && ex.flags.compound)
        out.push('drop_set');
    if (LENGTHENED_IDS.has(ex.id))
        out.push('lengthened_partials');
    return [...new Set(out)];
}
export function chooseAdvancedTechnique(exercise, planned, experience, priority, alreadyUsed, weeklyBudget) {
    if (weeklyBudget <= alreadyUsed || experience === 'novice')
        return undefined;
    if (planned.role === 'primary_strength' || planned.role === 'secondary_strength')
        return undefined;
    if (priority !== 'high' && priority !== 'specialization' && priority !== 'primary')
        return undefined;
    if (planned.sets < 2)
        return undefined;
    const types = eligibleTypes(exercise);
    if (!types.length)
        return undefined;
    const type = types.includes('lengthened_partials') ? 'lengthened_partials' : types[0];
    const note = type === 'myo_reps'
        ? 'On the final set only, use a brief myo-rep extension after the prescribed hard set; stop when rep quality or target effort deteriorates.'
        : type === 'drop_set'
            ? 'On the final set only, reduce load once and continue with controlled reps near the prescribed effort; do not repeat additional drops.'
            : 'On the final set only, after full-ROM reps are complete, add a small number of controlled partials in the lengthened portion while technique remains stable.';
    return { type, appliesTo: 'last_set', note };
}
export function techniqueExtraSeconds(technique) {
    if (!technique)
        return 0;
    if (technique.type === 'myo_reps')
        return 75;
    if (technique.type === 'drop_set')
        return 60;
    return 45;
}
