export const PUBLIC_MEV = Object.freeze({
    chest: 8,
    lats: 8,
    upper_back: 8,
    side_delts: 8,
    rear_delts: 8,
    biceps: 8,
    triceps: 6,
    quads: 8,
    hamstrings: 6,
    glutes: 4,
    calves: 8,
    core: 6
});
export const PUBLIC_MEV_REGIONS = Object.freeze(Object.keys(PUBLIC_MEV));
/** Mirrors the shell's training-age landmark scaling after beginner/none are normalized to novice. */
export const PUBLIC_MEV_EXPERIENCE_SCALE = Object.freeze({
    novice: .75,
    intermediate: 1,
    advanced: 1.25
});
export function publicMevForExperience(region, experience) {
    const base = PUBLIC_MEV[region];
    const scale = PUBLIC_MEV_EXPERIENCE_SCALE[experience] ?? 1;
    return base ? Math.max(1, Math.round(base * scale)) : 0;
}
export const PUBLIC_REGION_MUSCLE = Object.freeze({
    chest: 'chest',
    lats: 'back',
    upper_back: 'back',
    side_delts: 'side_delts',
    rear_delts: 'rear_delts',
    biceps: 'biceps',
    triceps: 'triceps',
    quads: 'quads',
    hamstrings: 'hamstrings',
    glutes: 'glutes',
    calves: 'calves',
    core: 'core'
});
const ACCUMULATION_PHASES = new Set(['hypertrophy_accumulation', 'mixed_accumulation']);
export function publicMevContractApplies(request, phase) {
    if (!ACCUMULATION_PHASES.has(phase))
        return false;
    if (request.schedule.days.length < 5)
        return false;
    const averageMinimum = request.schedule.days.reduce((sum, day) => sum + (day.minMinutes ?? 0), 0) / Math.max(1, request.schedule.days.length);
    return averageMinimum >= 60;
}
export function publicRegionPriority(request, region) {
    return request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[region]] ?? 'normal';
}
/** Reduced/maintenance muscles are an explicit request to train below the normal floor. */
export function publicMevRequired(request, region) {
    return publicRegionPriority(request, region) !== 'maintenance';
}
/**
 * Base-program reserve required for the shell's week-level set modulation.
 * Hypertrophy accumulation can consolidate the final week to 82% of base sets; mixed accumulation
 * can reach 84%. Using the worst accumulation factor keeps an ordinary 5+ week block at/above the
 * displayed MEV without changing the later-week consolidation policy itself.
 */
export function publicMevBaseTarget(request, region) {
    const base = Math.ceil(publicMevForExperience(region, request.athlete.experience) / .82);
    // Fractional cross-credit (especially row/pulldown and arm carryover) loses more than a simple
    // 18% when each exercise is rounded independently at the low-volume week. One additional base
    // set of reserve prevents a nominal 8.0 target from becoming 7.6–7.9 in the shell.
    const crossCreditSensitive = ['lats', 'upper_back', 'biceps', 'triceps'].includes(region);
    const reserve = (crossCreditSensitive ? 1 : 0) + (request.athlete.experience === 'advanced' ? 1 : 0);
    return base + reserve;
}
/**
 * Contract-aware ceiling for the coarse internal ledger. This does not increase allocator targets;
 * it only prevents the audit from calling required public-MEV carryover an upper-region violation.
 */
export function publicMevInternalSafetyCeiling(request, phase, muscle, modeledUpper) {
    if (!publicMevContractApplies(request, phase))
        return modeledUpper;
    let floor = 0;
    for (const region of PUBLIC_MEV_REGIONS) {
        if (PUBLIC_REGION_MUSCLE[region] !== muscle || !publicMevRequired(request, region))
            continue;
        const target = publicMevBaseTarget(request, region);
        // The composite back ledger carries both public regions. Other regions may include compound
        // secondary credit on top of their direct MEV reserve, so give that carryover bounded headroom.
        if (muscle === 'back')
            floor += target;
        else
            floor = Math.max(floor, target * 1.5);
    }
    return Math.max(modeledUpper, floor);
}
export function publicRegionContribution(def, region) {
    const family = def.movementFamily;
    const legacyPart = def.legacyPart;
    const legacySub = def.legacySubregion;
    // The shell reports side/rear delt MEV as direct-set guidance. Pressing/pulling secondary credit
    // must not satisfy these floors.
    if (region === 'side_delts') {
        return family === 'lateral_raise' || legacySub === 'side_delts' ? 1 : 0;
    }
    if (region === 'rear_delts') {
        return family === 'rear_delt' || legacySub === 'rear_delts' ? 1 : 0;
    }
    // `back` is deliberately coarse inside Next, while the shell exposes width and thickness as two
    // independent regions. Mirror the legacy volume view: vertical/extension work is direct lat work;
    // rows are direct upper-back work, with modest cross-credit in the opposite region.
    if (region === 'lats') {
        if (legacyPart === 'lats' || family === 'vertical_pull' || family === 'shoulder_extension')
            return 1;
        if (legacyPart === 'upper_back' || family === 'horizontal_pull')
            return .4;
        return 0;
    }
    if (region === 'upper_back') {
        if (legacyPart === 'upper_back' || family === 'horizontal_pull')
            return 1;
        // The legacy shell gives vertical pulls modest thickness carryover, but pullovers/shoulder
        // extension are lat work and do not satisfy upper-back MEV.
        if (family === 'vertical_pull')
            return .4;
        return 0;
    }
    const muscle = PUBLIC_REGION_MUSCLE[region];
    return Math.max(0, def.muscles[muscle]?.credit ?? 0);
}
export function directlyTargetsPublicRegion(def, region) {
    if (region === 'side_delts')
        return def.movementFamily === 'lateral_raise' || def.legacySubregion === 'side_delts';
    if (region === 'rear_delts')
        return def.movementFamily === 'rear_delt' || def.legacySubregion === 'rear_delts';
    if (region === 'lats')
        return def.legacyPart === 'lats' || def.movementFamily === 'vertical_pull' || def.movementFamily === 'shoulder_extension';
    if (region === 'upper_back')
        return def.legacyPart === 'upper_back' || def.movementFamily === 'horizontal_pull';
    const muscle = PUBLIC_REGION_MUSCLE[region];
    return (def.muscles[muscle]?.role === 'primary') || (def.muscles[muscle]?.credit ?? 0) >= 1;
}
export function publicMevLedger(sessions, exerciseMap) {
    const out = Object.fromEntries(PUBLIC_MEV_REGIONS.map(region => [region, 0]));
    for (const session of sessions) {
        for (const exercise of session.exercises) {
            const def = exerciseMap.get(exercise.exerciseId);
            if (!def)
                continue;
            for (const region of PUBLIC_MEV_REGIONS) {
                const credit = publicRegionContribution(def, region);
                if (credit > 0)
                    out[region] += exercise.sets * credit;
            }
        }
    }
    return out;
}
