/*
 * Capacity-band compatibility layer.
 *
 * The requested program remains the first attempt, unchanged. If the engine rejects it, optional
 * exercise count is relaxed first. For LONGER bands only, the lower clock edge may then relax toward
 * a proven shorter-band floor while the selected band's MAXIMUM stays unchanged. This makes time
 * availability monotonic: giving Pursuit more time can never make a split impossible merely because
 * the engine cannot justify enough extra work to fill the lower edge.
 *
 * Every fallback must still pass the normal engine audit. Equipment, split contracts, recovery,
 * coverage and safety rules are never suppressed.
 */
import * as base from './app-shell-adapter.js?capacity-base=1';
import { generateProgram } from './generate.js';
import { buildGenerationRecoveryPlan } from './generation-recovery.js';
import { createInitialCycleState } from './cycles.js';
import {
    capacityMinimumCandidates,
    capacityTargetCandidates,
    requestWithExerciseTarget,
    requestWithMinimumMinutes,
    requestedExerciseTarget,
    requestedMinimumMinutes
} from './capacity-policy.js';

export * from './app-shell-adapter.js?capacity-base=1';

function generationOptions(config) {
    return {
        blockWeeks: Math.max(1, Math.round(Number(config?.weeks) || 4)),
        progressionStyle: config?.progressionStyle
    };
}

function runProgram(request, config) {
    return generateProgram(request, generationOptions(config));
}

function passingAttempt(candidateRequest, config, originalRequest) {
    const candidate = runProgram(candidateRequest, config);
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

function firstPassingCapacityRequest(request, config) {
    const initial = runProgram(request, config);
    if (initial.program?.audit?.result === 'pass')
        return { request, result: initial, adjusted: false };

    const wantedTarget = requestedExerciseTarget(request);
    const lowerTargets = capacityTargetCandidates(request, config?.session);

    // Preserve the selected clock band first: only optional exercise density relaxes here.
    for (const target of lowerTargets) {
        const hit = passingAttempt(requestWithExerciseTarget(request, target), config, request);
        if (hit)
            return hit;
    }

    // If the lower CLOCK edge itself is what makes the longer choice fail, progressively inherit
    // the floor of shorter proven bands while retaining the user's selected maxMinutes. At each step
    // try the requested exercise count first, then only as much density reduction as needed.
    for (const minimumMinutes of capacityMinimumCandidates(request, config?.session)) {
        const targetOrder = wantedTarget ? [wantedTarget, ...lowerTargets] : [0];
        for (const target of targetOrder) {
            let candidateRequest = requestWithMinimumMinutes(request, minimumMinutes);
            if (target)
                candidateRequest = requestWithExerciseTarget(candidateRequest, target);
            const hit = passingAttempt(candidateRequest, config, request);
            if (hit)
                return hit;
        }
    }

    return { request, result: initial, adjusted: false };
}

function rejectionRecovery(result, request) {
    try {
        return buildGenerationRecoveryPlan(result.program.audit, request);
    }
    catch {
        return undefined;
    }
}

export function generateNextProgramForShell(options) {
    const originalRequest = base.shellConfigToNextRequest(
        options.config,
        options.banned ?? [],
        options.legacyExercises,
        options.seed
    );
    const attempt = firstPassingCapacityRequest(originalRequest, options.config);
    const { request, result } = attempt;

    if (result.program.audit.result !== 'pass') {
        throw new base.NextShellAdapterError(
            'NEXT_ENGINE_REJECTED',
            `Pursuit Engine ${result.program.engineVersion} could not safely satisfy this request.`,
            rejectionRecovery(result, originalRequest)
        );
    }

    const legacyProgram = base.nextProgramToShellProgram(
        result.program,
        options.config,
        options.legacyExercises,
        options.makeId
    );

    legacyProgram.nextEngine = {
        ...legacyProgram.nextEngine,
        request: JSON.parse(JSON.stringify(request)),
        baseRequest: JSON.parse(JSON.stringify(request)),
        program: JSON.parse(JSON.stringify(result.program)),
        cycleState: JSON.parse(JSON.stringify(createInitialCycleState(request.goal.type, request.schedule.days.length))),
        historySchemaVersion: 1,
        ...(attempt.adjusted ? {
            capacityAdjustment: {
                policy: 'soft-capacity-band',
                session: options.config?.session ?? 's60',
                requestedTargetExercises: attempt.requestedTarget,
                effectiveTargetExercises: attempt.effectiveTarget,
                requestedMinimumMinutes: attempt.requestedMinimumMinutes,
                effectiveMinimumMinutes: attempt.effectiveMinimumMinutes,
                maxMinutes: request.schedule?.days?.[0]?.maxMinutes
            }
        } : {})
    };

    return {
        program: legacyProgram,
        nextProgram: result.program,
        request,
        diagnostics: result.diagnostics
    };
}

export function splitBuildability(config, legacyExercises = []) {
    const gaps = base.splitContractGaps(config, legacyExercises);
    if (gaps.length)
        return { ok: false, kind: 'lifts', items: gaps.map(g => g.replace(/_/g, ' ')) };

    try {
        generateNextProgramForShell({ config, banned: [], legacyExercises, seed: 1 });
        return { ok: true };
    }
    catch {
        // A genuine refusal after every audited capacity fallback still belongs to the established
        // classifier so equipment, lift-contract and structural errors remain visible to the wizard.
        return base.splitBuildability(config, legacyExercises);
    }
}
