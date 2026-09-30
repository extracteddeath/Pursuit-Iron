/*
 * Capacity bands describe how much time the athlete is willing to make available, not an obligation
 * to fill an exact number of exercise slots or the lower edge of a clock range. Keep the requested
 * band as the first attempt, then allow longer bands to inherit a proven shorter-band floor when the
 * extra work is not productive. The selected band's MAXIMUM is never reduced.
 */
const SESSION_TARGET_FLOOR = Object.freeze({
    s20: 2,
    s40: 3,
    s60: 5,
    s90: 5,
    s120: 5,
    s120p: 5
});

/*
 * Longer choices must be monotonic: if a sound 40–60 minute program exists, offering the athlete
 * more time cannot make that same split impossible. These are fallback lower edges only. The normal
 * requested lower edge is always attempted first, so productive longer sessions remain unchanged.
 */
const SESSION_MINIMUM_STEPS = Object.freeze({
    s20: [],
    s40: [],
    s60: [],
    s90: [40],
    s120: [60, 40],
    s120p: [90, 60, 40]
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

export function requestedMinimumMinutes(request) {
    const mins = (request?.schedule?.days ?? [])
        .map(day => Number(day?.minMinutes))
        .filter(Number.isFinite)
        .map(value => Math.max(0, value));
    return mins.length ? Math.max(...mins) : 0;
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

export function capacityMinimumCandidates(request, session) {
    const wanted = requestedMinimumMinutes(request);
    return (SESSION_MINIMUM_STEPS[String(session || 's60')] ?? [])
        .filter(value => value < wanted);
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

export function requestWithMinimumMinutes(request, minimumMinutes) {
    const normalized = Math.max(0, Number(minimumMinutes) || 0);
    return {
        ...request,
        schedule: {
            ...request.schedule,
            days: (request.schedule?.days ?? []).map(day => ({
                ...day,
                minMinutes: Number.isFinite(Number(day.minMinutes))
                    ? Math.min(Math.max(0, Number(day.minMinutes)), normalized)
                    : normalized
            }))
        }
    };
}
