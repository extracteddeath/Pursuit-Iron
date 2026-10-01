// Data-only compatibility for backups made during the retired M75/M76 experiments.
// No trial assignment, candidate search, rollout, or workout decision runs here.
export function emptyRetiredTrialData() {
    return { schemaVersion: 1, enabled: false, consentVersion: 1, consentAt: null, enrollmentId: null, trials: [] };
}
export function preserveRetiredTrialData(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return emptyRetiredTrialData();
    return { ...emptyRetiredTrialData(), ...value,
        trials: Array.isArray(value.trials) ? value.trials.filter(x => x && typeof x === 'object').slice(-200) : [] };
}
export function emptyRetiredRolloutData() {
    return { schemaVersion: 1, releaseId: 'm82-build706', rollbackLatched: false, rollbackReason: null, rollbackAt: null, programs: [] };
}
export function preserveRetiredRolloutData(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return emptyRetiredRolloutData();
    return { ...emptyRetiredRolloutData(), ...value,
        programs: Array.isArray(value.programs) ? value.programs.filter(x => x && typeof x === 'object').slice(-80) : [] };
}
