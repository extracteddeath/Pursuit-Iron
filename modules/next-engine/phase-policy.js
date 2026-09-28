const POLICIES = {
    foundation: {
        phase: 'foundation', volumeMultiplier: .82, strengthVolumeMultiplier: .82,
        hypertrophyRirShift: 1, strengthRirShift: 1, strengthRepBias: 'normal', advancedTechniqueBudget: 0, specificityBias: .85, sessionCapacityMultiplier: .8
    },
    hypertrophy_accumulation: {
        phase: 'hypertrophy_accumulation', volumeMultiplier: 1, strengthVolumeMultiplier: .9,
        hypertrophyRirShift: 0, strengthRirShift: 0, strengthRepBias: 'hypertrophy', advancedTechniqueBudget: 2, specificityBias: .9, sessionCapacityMultiplier: 1
    },
    mixed_accumulation: {
        phase: 'mixed_accumulation', volumeMultiplier: .96, strengthVolumeMultiplier: 1,
        hypertrophyRirShift: 0, strengthRirShift: 0, strengthRepBias: 'normal', advancedTechniqueBudget: 1, specificityBias: 1, sessionCapacityMultiplier: .95
    },
    strength_accumulation: {
        phase: 'strength_accumulation', volumeMultiplier: .8, strengthVolumeMultiplier: .96,
        hypertrophyRirShift: 1, strengthRirShift: 0, strengthRepBias: 'lower', advancedTechniqueBudget: 0, specificityBias: 1.14, sessionCapacityMultiplier: .78
    },
    intensification: {
        phase: 'intensification', volumeMultiplier: .72, strengthVolumeMultiplier: .86,
        hypertrophyRirShift: 1, strengthRirShift: 1, strengthRepBias: 'lower', advancedTechniqueBudget: 0, specificityBias: 1.3, sessionCapacityMultiplier: .76
    },
    peak: {
        phase: 'peak', volumeMultiplier: .30, strengthVolumeMultiplier: .78,
        hypertrophyRirShift: 2, strengthRirShift: -1, strengthRepBias: 'peak', advancedTechniqueBudget: 0, specificityBias: 1.65, sessionCapacityMultiplier: .42
    },
    maintenance: {
        phase: 'maintenance', volumeMultiplier: .62, strengthVolumeMultiplier: .66,
        hypertrophyRirShift: 1, strengthRirShift: 1, strengthRepBias: 'normal', advancedTechniqueBudget: 0, specificityBias: .95, sessionCapacityMultiplier: .68
    },
    recovery: {
        phase: 'recovery', volumeMultiplier: .55, strengthVolumeMultiplier: .58,
        hypertrophyRirShift: 2, strengthRirShift: 2, strengthRepBias: 'normal', advancedTechniqueBudget: 0, specificityBias: .78, sessionCapacityMultiplier: .55
    }
};
export function initialPhaseForGoal(goal) {
    if (goal === 'hypertrophy')
        return 'hypertrophy_accumulation';
    if (goal === 'strength')
        return 'strength_accumulation';
    return 'mixed_accumulation';
}
export function phasePolicyFor(phase) {
    return POLICIES[phase];
}
export function phaseLabel(phase) {
    return phase.split('_').map(x => x[0].toUpperCase() + x.slice(1)).join(' ');
}
