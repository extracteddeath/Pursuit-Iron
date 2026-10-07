/** Session realization: rules. Maintained production source. */
import { INTENT_MUSCLES } from '../topology.js';

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

const smallIntentPreference = {
        biceps: ['arms', 'shoulders_arms', 'pull', 'upper', 'strength_full', 'full'], triceps: ['arms', 'shoulders_arms', 'push', 'bench_focus', 'press_focus', 'upper', 'strength_full', 'full'], rear_delts: ['pull', 'upper', 'bench_focus', 'press_focus', 'strength_full', 'full'],
        side_delts: ['shoulders', 'shoulders_arms', 'push', 'press_focus', 'upper', 'strength_full', 'full'], calves: ['legs', 'lower', 'squat_focus', 'deadlift_focus', 'strength_full', 'limbs', 'full'], core: ['legs', 'lower', 'squat_focus', 'deadlift_focus', 'strength_full', 'limbs', 'full'], front_delts: ['push', 'press_focus', 'bench_focus', 'upper', 'strength_full', 'full']
    };

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

function addToLedger(ledger, ex, sets) {
    for (const [m, c] of Object.entries(ex.muscles)) {
        ledger.fractional[m] = (ledger.fractional[m] ?? 0) + c.credit * sets;
        if (c.credit >= 1)
            ledger.direct[m] = (ledger.direct[m] ?? 0) + sets;
    }
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

export { MUSCLE_MOVEMENTS, LARGE, SMALL, smallIntentPreference, equipmentEligible, maxBarbells, addToLedger, integerSetPlan, desiredDirect, primaryMuscle, intentAcceptsMuscle, floorSpilloverAllowed };
