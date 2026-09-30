/*
 * Capacity bands describe how much time the athlete is willing to make available, not an obligation
 * to fill an exact number of exercise slots. These helpers keep the preferred target while providing
 * a monotonic fallback for longer bands when an optional slot makes an otherwise valid plan fail.
 */
const SESSION_TARGET_FLOOR = Object.freeze({
    s20: 2,
    s40: 3,
    s60: 5,
    s90: 5,
    s120: 5,
    s120p: 5
});

export function capacityTargetFloor(session) {
    return SESSION_TARGET_FLOOR[String(session || 's60')] ?? 5;
}

export function requestedExerciseTarget(request) {
    const targets = (request?.schedule?.days ?? [])
        .map(day => Number(day?.targetExercises))
        .filter(Number.isFinite)
        .map(Math.round);
    return targets.length ? Math.max(...targets) : 0;
}

export function capacityTargetCandidates(request, session) {
    const wanted = requestedExerciseTarget(request);
    const floor = capacityTargetFloor(session);
    if (!wanted || wanted <= floor)
        return [];
    const targets = [];
    for (let target = wanted - 1; target >= floor; target--)
        targets.push(target);
    return targets;
}

export function requestWithExerciseTarget(request, target) {
    const normalized = Math.max(1, Math.round(Number(target) || 1));
    return {
        ...request,
        schedule: {
            ...request.schedule,
            days: (request.schedule?.days ?? []).map(day => ({
                ...day,
                targetExercises: Number.isFinite(Number(day.targetExercises))
                    ? Math.min(Math.max(1, Math.round(Number(day.targetExercises))), normalized)
                    : normalized
            }))
        }
    };
}
