import { historyNumber, progressionExposureContext } from './history-contract.js';

const automatic = row => row && row.auto === true && row.valueOwner === 'prescription' && !row.added && !row.warm && !row.sub;
const untouched = row => automatic(row) && !row.done && String(row.weight) === String(row.target?.w)
    && row.target?.prefillReps !== undefined && String(row.reps) === String(row.target.prefillReps);
const hold = (sets, reasonCode) => ({ sets, decision: { schemaVersion: 1, action: 'hold', reasonCode, earnedProgression: false } });

/** Undo only app-owned pending changes that still equal their adjusted target. */
export function rollbackLiveAutoregulation(sets, sourceIndex) {
    if (!Array.isArray(sets)) return sets;
    let changed = false;
    const result = sets.map(row => {
        if (!untouched(row) || !row.liveAdjustment?.sourceIndices?.includes(sourceIndex) || !row.retuneFrom) return row;
        changed = true;
        const { liveAdjustment, retuneFrom, retuneSrc, autoTuned, hint, recoveryLimited, ...rest } = row;
        return { ...rest, weight: retuneFrom.weight, reps: retuneFrom.reps, target: retuneFrom.target,
            ...(retuneFrom.recoveryLimited !== undefined ? { recoveryLimited: retuneFrom.recoveryLimited } : {}) };
    });
    return changed ? result : sets;
}

/** Two observed misses may ease one pending automatic accessory row; this never earns progression. */
export function applyLiveAutoregulation({ sets, sourceIndex, cell, context = {}, snapLoad }) {
    if (!Array.isArray(sets) || !Number.isInteger(sourceIndex) || !sets[sourceIndex]) return hold(sets, 'invalid_live_scope');
    if (!sets[sourceIndex].done || sets[sourceIndex].warm || sets[sourceIndex].sub) return hold(sets, 'no_new_working_observation');
    if (!cell || !['hypertrophy_compound', 'hypertrophy_isolation', 'accessory'].includes(cell.role)
        || !['auto', 'double', 'dynamic', 'ladder', 'e1rm'].includes(cell.progressionStyle ?? 'auto') || cell.setTargets || cell.percentageProtocol)
        return hold(sets, 'protect_authored_strength_protocol');
    const flags = progressionExposureContext([], context);
    if (flags.interrupted || flags.nonComparable) return hold(sets, 'non_comparable_live_context');
    const pair = value => Array.isArray(value) ? [...value] : typeof value === 'string' ? value.split(/[-–]/).map(historyNumber) : [historyNumber(value), historyNumber(value)];
    const reps = pair(cell.reps), rir = pair(cell.rir);
    if (reps.length === 1) reps.push(reps[0]); if (rir.length === 1) rir.push(rir[0]);
    if (reps.length !== 2 || rir.length !== 2 || !reps.every(Number.isFinite) || !rir.every(Number.isFinite)
        || reps[0] < 1 || reps[1] < reps[0] || rir[0] < 0 || rir[1] < rir[0] || rir[1] > 10) return hold(sets, 'unknown_live_intent');
    const completed = sets.map((row, index) => ({ row, index })).filter(({ row }) => row?.done && !row.warm && !row.sub && !row.added);
    const observed = completed.slice(-2);
    if (observed.length < 2) return hold(sets, 'collect_second_observation');
    const failed = observed.every(({ row }) => {
        const load = historyNumber(row.weight), r = historyNumber(row.reps), effort = historyNumber(row.actualRIR);
        const limited = progressionExposureContext([row]);
        return load > 0 && Number.isInteger(r) && r > 0 && effort !== null && effort >= 0 && effort <= 10
            && !limited.nonComparable && !limited.interrupted && !row.liveAdjustment
            && ((r < reps[0] && effort <= rir[0]) || (effort < rir[0] && r <= reps[1]));
    });
    if (!failed) {
        const restored = rollbackLiveAutoregulation(sets, sourceIndex);
        return hold(restored, 'retain_target_without_repeated_observed_miss');
    }
    const indices = observed.map(x => x.index), last = Math.max(...indices), pendingIndex = sets.findIndex((row, index) => index > last && !row?.done && !row?.warm && !row?.sub);
    if (pendingIndex < 0) return hold(sets, 'no_pending_work');
    const pending = sets[pendingIndex];
    if (!untouched(pending)) return hold(sets, 'protect_manual_or_changed_row');
    if (pending.liveAdjustment) return hold(sets, 'already_adjusted_pending_row');
    const load = historyNumber(pending.weight);
    if (!(load > 0) || typeof snapLoad !== 'function') return hold(sets, 'unknown_loadability');
    const next = snapLoad(load * .95);
    if (!Number.isFinite(next) || next <= 0 || next >= load || next < load * .9) return hold(sets, 'no_small_loadable_reduction');
    const decision = { schemaVersion: 1, action: 'reduce_pending_load', reasonCode: 'two_observed_accessory_misses', sourceIndices: indices,
        pendingIndex, fromLoad: load, toLoad: next, earnedProgression: false, confidence: 'moderate' };
    const result = [...sets];
    result[pendingIndex] = { ...pending, weight: String(next), target: { ...pending.target, w: next }, autoTuned: true, hint: 'down',
        recoveryLimited: true, liveAdjustment: decision, retuneSrc: indices.at(-1),
        retuneFrom: { weight: pending.weight, reps: pending.reps, target: pending.target, recoveryLimited: pending.recoveryLimited } };
    return { sets: result, decision };
}
