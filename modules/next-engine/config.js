export const ENGINE_VERSION = '0.63.2';
export const MUSCLE_DOSE_PRIOR = {
    novice: { minimum: 4, preferred: 6, upper: 10 },
    intermediate: { minimum: 6, preferred: 9, upper: 14 },
    advanced: { minimum: 6, preferred: 10, upper: 16 }
};
export const PRIORITY_MULTIPLIER = {
    maintenance: 0.65,
    normal: 1,
    high: 1.18,
    specialization: 1.34,
    primary: 1.45
};
export const PRIORITY_UTILITY = {
    maintenance: 0.68,
    normal: 1,
    high: 1.45,
    specialization: 1.82,
    primary: 2.05
};
export const ALLOCATION_CONFIG = {
    minimumUsefulUtility: 0.028,
    floorUtilityBoost: 2.25,
    minuteSoftPressureStart: 0.72,
    maxMarginalIterations: 80
};
export const CORE_MUSCLES = ['chest', 'back', 'side_delts', 'rear_delts', 'front_delts', 'biceps', 'triceps', 'quads', 'hamstrings', 'glutes', 'calves', 'core'];
/**
 * Targets v661 could program directly but the early rewrite collapsed or omitted. They stay
 * separate from CORE_MUSCLES so adding the compatibility catalog does not suddenly force six new
 * normal-priority volume floors into every existing program. A user can still prioritize them and
 * the allocator can satisfy them with direct work.
 */
export const OPTIONAL_MUSCLES = ['traps', 'forearms', 'adductors', 'abductors', 'neck', 'lower_back'];
export const ALL_MUSCLES = [...CORE_MUSCLES, ...OPTIONAL_MUSCLES];
