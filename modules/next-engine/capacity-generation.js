import { generateProgram } from './generate.js';
import {
    capacityMinimumCandidates,
    capacityTargetCandidates,
    requestWithExerciseTarget,
    requestWithMinimumMinutes,
    requestedExerciseTarget,
    requestedMinimumMinutes
} from './capacity-policy.js';

function passingAttempt(candidateRequest, originalRequest, generationOptions) {
    const candidate = generateProgram(candidateRequest, generationOptions);
    if (candidate.program?.audit?.result !== 'pass')
        return null;
    return {
        request: candidateRequest,
        result: candidate,
        adjusted: candidateRequest !== originalRequest,
        requestedTarget: requestedExerciseTarget(originalRequest),
        effectiveTarget: requestedExerciseTarget(candidateRequest),
        requestedMinimumMinutes: requestedMinimumMinutes(originalRequest),
        effectiveMinimumMinutes: requestedMinimumMinutes(candidateRequest)
    };
}

/**
 * Generate against a session TIME CAPACITY rather than treating the selected band's lower edge and
 * exercise count as quotas. The exact requested request is always tried first. If it rejects, only
 * optional density is relaxed; on longer bands the lower clock edge may then inherit a shorter
 * proven floor while the selected maximum stays unchanged. Every candidate still passes the normal
 * engine audit, so equipment, recovery, coverage and safety constraints remain hard requirements.
 */
export function firstPassingCapacityProgram(request, config, generationOptions = {}) {
    const initial = generateProgram(request, generationOptions);
    if (initial.program?.audit?.result === 'pass')
        return { request, result: initial, adjusted: false };

    const wantedTarget = requestedExerciseTarget(request);
    const lowerTargets = capacityTargetCandidates(request, config?.session);

    for (const target of lowerTargets) {
        const hit = passingAttempt(requestWithExerciseTarget(request, target), request, generationOptions);
        if (hit)
            return hit;
    }

    for (const minimumMinutes of capacityMinimumCandidates(request, config?.session)) {
        const targetOrder = wantedTarget ? [wantedTarget, ...lowerTargets] : [0];
        for (const target of targetOrder) {
            let candidateRequest = requestWithMinimumMinutes(request, minimumMinutes);
            if (target)
                candidateRequest = requestWithExerciseTarget(candidateRequest, target);
            const hit = passingAttempt(candidateRequest, request, generationOptions);
            if (hit)
                return hit;
        }
    }

    return { request, result: initial, adjusted: false };
}
