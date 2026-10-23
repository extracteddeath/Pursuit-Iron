/**
 * M311. A saved cycle can opt into the new optimizer without being recreated.
 * This changes the future generation policy only; no preview, training log,
 * active block, manual edit, or split explicitly authored for a later block is
 * rewritten. The existing advance-cycle worker applies these flags when it
 * builds the next block from completed training evidence.
 */
export function upgradeExistingCycleToAdaptive(cycle, saved = [], history = [], now = Date.now()) {
    if (!cycle || cycle.engineSource !== 'pursuit-next' || !cycle.nextEngineCycle
        || !Array.isArray(cycle.nextEngineCycle.plannedBlocks)
        || !cycle.nextEngineCycle.baseRequest || !cycle.nextEngineCycle.baseConfig)
        throw new Error('This older cycle does not have the engine data needed for a safe adaptive upgrade.');
    if (cycle.done)
        throw new Error('Completed cycles cannot be changed. Start a new adaptive cycle instead.');
    const ids = cycle.blockIds || [], meta = cycle.blockMeta || [];
    const active = Number(cycle.activeBlock);
    if (!Number.isInteger(active) || active < 0 || active >= ids.length - 1
        || meta.length !== ids.length || meta.some((m, i) => m?.id !== ids[i]))
        throw new Error('This cycle has no valid upcoming block to adapt.');
    const byId = new Map(saved.filter(Boolean).map(p => [p.id, p]));
    if (!ids.every(id => byId.has(id)))
        throw new Error('A cycle block is missing. The existing program was not modified.');
    const futureIds = new Set(ids.slice(active + 1));
    if (history.some(entry => futureIds.has(entry?.programId)))
        throw new Error('A future block already has recorded workouts, so it cannot be upgraded safely.');
    if (cycle.adaptExercises === true && cycle.adaptTrainingSplit === true
        && cycle.nextEngineCycle.adaptBetweenBlocks === true
        && cycle.nextEngineCycle.adaptTrainingSplit === true)
        return { cycle, changed: false };
    const legacyBridge = !!cycle.nextEngineCycle.legacyFirstBlockId && active === 0;
    return {
        changed: true,
        cycle: {
            ...cycle,
            adaptExercises: true,
            adaptTrainingSplit: true,
            adaptiveUpgrade: {
                enabledAt: now,
                activeBlockAtUpgrade: active,
                awaitingBoundary: true,
                firstFixedLegacyBridge: legacyBridge
            },
            nextEngineCycle: {
                ...cycle.nextEngineCycle,
                adaptBetweenBlocks: true,
                adaptTrainingSplit: true,
                baseConfig: {
                    ...cycle.nextEngineCycle.baseConfig,
                    cycleAdapt: true,
                    cycleAdaptSplit: true
                }
            }
        }
    };
}
