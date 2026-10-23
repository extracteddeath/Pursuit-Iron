import { generateNextCycleForShell } from './cycle-runtime-adapter.js';
import { computeCell, exerciseAt, weeksOf, effortBounds } from '../training-domain/records.js';
import { nextExerciseIdForShellExercise, shellPrescriptionFieldOwner } from './shell-projection.js';
import { createExerciseMap } from './exercise-db.js';
import { publicMevLedger } from './public-mev.js';

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

// Refresh forecasts through the existing generator, retaining the cycle's identities
// and authored phase sequence. No completed/current training is regeneration input.
export function refreshAdaptiveCyclePreviews(options) {
    const { saved = [], history = [], legacyExercises = [], equipment, makeId } = options;
    const upgraded = upgradeExistingCycleToAdaptive(options.cycle, saved, history, options.now);
    const cycle = upgraded.cycle, active = Number(cycle.activeBlock), ids = cycle.blockIds;
    const current = saved.find(p => p.id === ids[active]);
    const future = ids.slice(active + 1).map(id => saved.find(p => p.id === id));
    if (!Array.isArray(equipment) || !equipment.length) throw new Error('Choose the gym equipment before refreshing future blocks.');
    if (future.some(p => p.edited || Object.values(p.overrides || {}).some(o => ['sets','reps','rir','rest','tech'].some(field => shellPrescriptionFieldOwner(p,o,field) === 'user'))))
        throw new Error('A future block has manual edits. Keep or remove those edits before rebuilding its preview.');
    const specs = future.map((p, offset) => {
        const meta = cycle.blockMeta[active + 1 + offset];
        const authored = cycle.nextEngineCycle.plannedBlocks[meta.plannedIndex];
        if (!authored) throw new Error('A future block is missing its authored phase. Nothing was changed.');
        return { ...authored, phase: meta.phase || authored.phase, weeks: meta.weeks || authored.weeks, label: meta.label || authored.label };
    });
    const config = { ...cycle.nextEngineCycle.baseConfig, equipment: [...equipment], name: cycle.name };
    // Native snapshots already have the canonical executable projection. Legacy
    // custom blocks read their exact cells, then share the same event/region owners.
    const previousVolumeSnapshot = authoredCycleExitSnapshot(current, legacyExercises);
    const generated = generateNextCycleForShell({ templateId: 'custom', config, blocks: specs,
        existingBlockWeeks: weeksOf(current), seed: cycle.seed, legacyExercises, makeId,
        banned: options.banned || [], adaptBetweenBlocks: true, adaptTrainingSplit: true,
        previousShell: current, previousVolumeSnapshot });
    const blocks = generated.blocks.map((p, offset) => {
        const index = active + 1 + offset, meta = cycle.blockMeta[index];
        return { ...p, id: ids[index], cycleId: cycle.id, cycleIndex: index,
            name: future[offset].name, blockLabel: meta.label,
            nextEngine: { ...p.nextEngine, cycleTemplate: { ...p.nextEngine.cycleTemplate,
                templateId: cycle.templateId, plannedIndex: meta.plannedIndex, blockIndex: index, preview: true } } };
    });
    return { changed: true, blocks, cycle: { ...cycle,
        adaptiveUpgrade: { ...cycle.adaptiveUpgrade, awaitingBoundary: false, previewsRefreshedAt: options.now ?? Date.now(), equipment: [...equipment] },
        blockMeta: cycle.blockMeta.map((m,i) => i <= active ? m : { ...m, preview: true,
            splitDecision: blocks[i-active-1].nextEngine.cycleTemplate.splitDecision,
            split: blocks[i-active-1].config.split, days: blocks[i-active-1].days.length }),
        nextEngineCycle: { ...cycle.nextEngineCycle, baseConfig: { ...config, cycleAdapt: true, cycleAdaptSplit: true },
            baseRequest: generated.baseRequest, previewRevision: 1 } } };
}

export function authoredCycleExitSnapshot(current, legacyExercises = []) {
    let previousVolumeSnapshot = null;
    if (!current.nextEngine?.program && current.config?.goal !== 'strength') {
        const week = weeksOf(current), map = new Map(legacyExercises.map(ex => [ex.id, ex]));
        const sessions = current.days.map(day => ({ day: day.id, exercises: day.exercises.map((_, slot) => {
            const id = exerciseAt(current, day, slot, week), ex = map.get(id), cell = computeCell(current, day, id, slot, week);
            const exerciseId = nextExerciseIdForShellExercise(ex);
            if (!exerciseId || cell.missing) throw new Error('The custom program has an unresolved exercise. Nothing was changed.');
            return { exerciseId, sets: Number(cell.sets) || 0, role: cell.role || 'hypertrophy_isolation',
                prescription: { reps: effortBounds(cell.reps) || [8,12], rir: effortBounds(cell.rir) || [2,2] } };
        }).filter(ex => ex.sets > 0) }));
        previousVolumeSnapshot = { sessions, regions: publicMevLedger(sessions, createExerciseMap()), missing: false };
    }
    return previousVolumeSnapshot;
}
