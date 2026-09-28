const INTERPRETATIONS = {
    phul: { publicName: 'Power / Hypertrophy Upper-Lower', structure: 'heavy upper/lower sessions followed by higher-rep upper/lower sessions' },
    phat: { publicName: 'Power / Hypertrophy 5-Day', structure: 'two strength-biased sessions followed by three hypertrophy-biased sessions' },
    arnold: { publicName: 'Chest + Back / Shoulders + Arms / Legs', structure: 'a six-day chest-and-back, shoulders-and-arms, and legs rotation' },
    five_three_one: { publicName: 'Main-Lift Waves', structure: 'one primary barbell lift anchoring each training day' },
    five31_beginner: { publicName: 'Main-Lift Waves · Novice', structure: 'three full-body sessions with two prioritized main-lift exposures per day' },
    texas: { publicName: 'Volume / Recovery / Intensity', structure: 'a three-day high-volume, lower-fatigue, and intensity undulation' },
    gzclp: { publicName: 'Tiered Linear Progression', structure: 'a four-day heavy-primary, secondary-volume, and accessory tier structure' },
    rippler: { publicName: 'Tiered Wave', structure: 'a four-day tiered strength structure with a changing intensity emphasis' },
    jt: { publicName: 'Tiered Powerbuilding', structure: 'a four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary work' }
};
export function methodPolicyForSplit(family, fallbackName) {
    const policy = INTERPRETATIONS[family];
    if (!policy)
        return {
            mode: 'pursuit_native',
            publicName: fallbackName,
            statement: 'This is a Pursuit-designed structure; Pursuit Engine owns exercise selection, dose, reps, RIR, loading and progression.'
        };
    return {
        mode: 'pursuit_interpretation',
        publicName: policy.publicName,
        compatibilityId: family,
        statement: `Pursuit interpretation: preserves ${policy.structure}, while Pursuit Engine owns exercise selection, dose, reps, RIR, loading and progression. It is not a formula-exact reproduction of a third-party routine.`
    };
}
export function publicSplitName(family, fallbackName) {
    return methodPolicyForSplit(family, fallbackName).publicName;
}
