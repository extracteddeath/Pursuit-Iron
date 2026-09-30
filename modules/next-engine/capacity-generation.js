import { generateProgram } from './generate.js';
import {
    capacityMinimumCandidates,
    capacityTargetCandidates,
    requestWithExerciseTarget,
    requestWithMinimumMinutes,
    requestedExerciseTarget,
    requestedMinimumMinutes
} from './capacity-policy.js';

function canonicalSeed(raw) {
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? Math.max(1, Math.floor(n)) : 1;
}

function requestWithSeed(request, seed) {
    return canonicalSeed(request?.seed) === seed ? request : { ...request, seed };
}

// A rejected random layout is not proof that the athlete's choices are impossible. Keep the exact
// requested seed first for reproducibility, then use a small deterministic family only after it
// fails. The successful seed is persisted in the returned request/program, so rebuild/share remains
// exact rather than depending on hidden randomness.
export function alternateSeedCandidates(request, count = 5) {
    const base = canonicalSeed(request?.seed);
    const out = [];
    let state = base >>> 0;
    for (let i = 0; i < count; i++) {
        state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
        const seed = (state % 2147483646) + 1;
        if (seed !== base && !out.includes(seed)) out.push(seed);
    }
    return out;
}

function attemptResult(candidateRequest, originalRequest, generationOptions) {
    const candidate = generateProgram(candidateRequest, generationOptions);
    if (candidate.program?.audit?.result !== 'pass') return null;
    return {
        request: candidateRequest,
        result: candidate,
        adjusted: candidateRequest !== originalRequest,
        requestedTarget: requestedExerciseTarget(originalRequest),
        effectiveTarget: requestedExerciseTarget(candidateRequest),
        requestedMinimumMinutes: requestedMinimumMinutes(originalRequest),
        effectiveMinimumMinutes: requestedMinimumMinutes(candidateRequest),
        requestedSeed: canonicalSeed(originalRequest?.seed),
        effectiveSeed: canonicalSeed(candidateRequest?.seed)
    };
}

function passingSeedFamily(baseRequest, originalRequest, generationOptions, includeRequestedSeed = true) {
    const requested = canonicalSeed(baseRequest?.seed);
    const seeds = includeRequestedSeed
        ? [requested, ...alternateSeedCandidates(baseRequest)]
        : alternateSeedCandidates(baseRequest);
    for (const seed of seeds) {
        const hit = attemptResult(requestWithSeed(baseRequest, seed), originalRequest, generationOptions);
        if (hit) return hit;
    }
    return null;
}

/**
 * Generate against a session TIME CAPACITY rather than treating the selected band's lower edge and
 * exercise count as quotas. The exact requested request is always tried first. If that particular
 * random layout rejects, deterministic alternate seeds are tried before changing the athlete's time
 * contract. Only then is optional density relaxed; on longer bands the lower clock edge may inherit
 * a shorter proven floor while the selected maximum stays unchanged. Every candidate still passes
 * the normal engine audit, so equipment, recovery, coverage and safety constraints remain hard.
 */
export function firstPassingCapacityProgram(request, config, generationOptions = {}) {
    const initial = generateProgram(request, generationOptions);
    if (initial.program?.audit?.result === 'pass') {
        return {
            request,
            result: initial,
            adjusted: false,
            requestedTarget: requestedExerciseTarget(request),
            effectiveTarget: requestedExerciseTarget(request),
            requestedMinimumMinutes: requestedMinimumMinutes(request),
            effectiveMinimumMinutes: requestedMinimumMinutes(request),
            requestedSeed: canonicalSeed(request?.seed),
            effectiveSeed: canonicalSeed(request?.seed)
        };
    }

    // Selection/topology contains intentional randomness. A single structural miss (for example the
    // last upper-pull slot disappearing on one Full Body roll) must not become a user-facing refusal
    // when another deterministic roll of the same request passes unchanged.
    const sameCapacity = passingSeedFamily(request, request, generationOptions, false);
    if (sameCapacity) return sameCapacity;

    const wantedTarget = requestedExerciseTarget(request);
    const lowerTargets = capacityTargetCandidates(request, config?.session);

    for (const target of lowerTargets) {
        const candidateRequest = requestWithExerciseTarget(request, target);
        const hit = passingSeedFamily(candidateRequest, request, generationOptions, true);
        if (hit) return hit;
    }

    for (const minimumMinutes of capacityMinimumCandidates(request, config?.session)) {
        const targetOrder = wantedTarget ? [wantedTarget, ...lowerTargets] : [0];
        for (const target of targetOrder) {
            let candidateRequest = requestWithMinimumMinutes(request, minimumMinutes);
            if (target) candidateRequest = requestWithExerciseTarget(candidateRequest, target);
            const hit = passingSeedFamily(candidateRequest, request, generationOptions, true);
            if (hit) return hit;
        }
    }

    return {
        request,
        result: initial,
        adjusted: false,
        requestedTarget: requestedExerciseTarget(request),
        effectiveTarget: requestedExerciseTarget(request),
        requestedMinimumMinutes: requestedMinimumMinutes(request),
        effectiveMinimumMinutes: requestedMinimumMinutes(request),
        requestedSeed: canonicalSeed(request?.seed),
        effectiveSeed: canonicalSeed(request?.seed)
    };
}
