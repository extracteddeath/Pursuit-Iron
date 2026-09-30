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
import { buildGenerationRecoveryPlan } from './generation-recovery.js';
import { createInitialCycleState } from './cycles.js';
import { firstPassingCapacityProgram } from './capacity-generation.js';

export * from './app-shell-adapter.js?capacity-base=1';

function generationOptions(config) {
    return {
        blockWeeks: Math.max(1, Math.round(Number(config?.weeks) || 4)),
        progressionStyle: config?.progressionStyle
    };
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
    const attempt = firstPassingCapacityProgram(
        originalRequest,
        options.config,
        generationOptions(options.config)
    );
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
