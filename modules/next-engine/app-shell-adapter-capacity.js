/*
 * Capacity-band compatibility layer.
 *
 * The established 60–90 minute programs remain the first attempt, unchanged. If the engine rejects
 * only that exact exercise-slot target, progressively smaller targets are tried while retaining the
 * SAME time band, split, equipment, goal, priorities, and restrictions. A fallback is accepted only
 * when the normal engine audit returns pass, so genuine equipment/design refusals remain refusals.
 */
import * as base from './app-shell-adapter.js?capacity-base=1';
import { generateProgram } from './generate.js';
import { buildGenerationRecoveryPlan } from './generation-recovery.js';
import { createInitialCycleState } from './cycles.js';
import {
    capacityTargetCandidates,
    requestWithExerciseTarget,
    requestedExerciseTarget
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

function firstPassingCapacityRequest(request, config) {
    const initial = runProgram(request, config);
    if (initial.program?.audit?.result === 'pass')
        return { request, result: initial, adjusted: false };

    const wanted = requestedExerciseTarget(request);
    for (const target of capacityTargetCandidates(request, config?.session)) {
        const candidateRequest = requestWithExerciseTarget(request, target);
        const candidate = runProgram(candidateRequest, config);
        if (candidate.program?.audit?.result === 'pass') {
            return {
                request: candidateRequest,
                result: candidate,
                adjusted: true,
                requestedTarget: wanted,
                effectiveTarget: target
            };
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
                policy: 'soft-exercise-target',
                session: options.config?.session ?? 's60',
                requestedTargetExercises: attempt.requestedTarget,
                effectiveTargetExercises: attempt.effectiveTarget
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
        // If relaxing the optional slot target still cannot produce an audited program, defer to the
        // existing classifier so real equipment, lift-contract, and structural errors stay visible.
        return base.splitBuildability(config, legacyExercises);
    }
}
