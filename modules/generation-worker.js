import {
    EXERCISES, EX_BY_ID, NextShellAdapterError,
    generateNextProgramForShell, generateNextCycleForShell, convertProgramToNextCycleForShell,
    advanceNextCycleForShell, generateNextBlockFromShellHistory,
    setLoadInc, setExLoadInc, setAvailPlates, setGymLimits, setRestScaleGlobal
} from './engine-api.js';

const operations = Object.freeze({
    program: generateNextProgramForShell,
    cycle: generateNextCycleForShell,
    convert_cycle: convertProgramToNextCycleForShell,
    advance_cycle: advanceNextCycleForShell,
    next_block: generateNextBlockFromShellHistory
});

/** Transport only: every training decision still belongs to the canonical engine API. */
export function executeGenerationTask({ operation, options, idPrefix, settings = {} }) {
    if (!Object.hasOwn(operations, operation) || !Array.isArray(options?.legacyExercises) || typeof idPrefix !== 'string' || !idPrefix)
        throw new NextShellAdapterError('NEXT_GENERATION_INVALID_REQUEST', 'The plan-building request is incomplete.');
    // The UI can add, edit or remove custom exercises between jobs. Replace the worker-local
    // registry from this request's catalog so dosage/time helpers see exactly the same definitions.
    for (const id of Object.keys(EX_BY_ID)) delete EX_BY_ID[id];
    EXERCISES.splice(0, EXERCISES.length, ...options.legacyExercises);
    for (const exercise of EXERCISES) EX_BY_ID[exercise.id] = exercise;
    setLoadInc(settings.loadInc?.v, settings.loadInc?.unit);
    setExLoadInc(settings.exLoadInc);
    setAvailPlates(settings.plates);
    setRestScaleGlobal(settings.restScale);
    setGymLimits(settings.gym ? { ...settings.gym, limitUnit: settings.gym.unit } : null);
    let nextId = 0;
    const makeId = () => ++nextId === 1 ? idPrefix : `${idPrefix}-${nextId}`;
    return operations[operation]({ ...options, makeId });
}

export function generationReply(request) {
    try {
        return { id: request?.id, ok: true, result: executeGenerationTask(request) };
    } catch (error) {
        return { id: request?.id, ok: false, error: {
            code: error?.code || 'NEXT_ENGINE_ERROR',
            message: String(error?.message || error),
            recovery: error?.recovery ?? null
        } };
    }
}

if (typeof WorkerGlobalScope !== 'undefined' && globalThis instanceof WorkerGlobalScope) {
    globalThis.addEventListener('message', event => globalThis.postMessage(generationReply(event.data)));
}
