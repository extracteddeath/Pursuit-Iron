import { historyNumber, progressionExposureContext } from './history-contract.js';

const automatic = row => row && row.auto === true && row.valueOwner === 'prescription' && !row.added && !row.warm && !row.sub;
const untouched = row => automatic(row) && !row.done && String(row.weight) === String(row.target?.w)
    && row.target?.prefillReps !== undefined && String(row.reps) === String(row.target.prefillReps);
// A session-local set type keeps automatic numeric ownership. It still represents
// explicit intent, so failure/AMRAP/percentage work is not ordinary miss evidence.
const authoredIntent = row => row?.typeOverride || row?.target?.failure || row?.target?.amrap
    || row?.target?.nextAction === 'percentage';
const hold = (sets, reasonCode) => ({ sets, decision: { schemaVersion: 1, action: 'hold', reasonCode, earnedProgression: false } });

const restorePendingAdjustment = row => {
    const { liveAdjustment, retuneFrom, retuneSrc, autoTuned, hint, recoveryLimited, suggested, ...rest } = row;
    return { ...rest, weight: retuneFrom.weight, reps: retuneFrom.reps, target: retuneFrom.target,
        ...(retuneFrom.recoveryLimited !== undefined ? { recoveryLimited: retuneFrom.recoveryLimited } : {}) };
};

/** A restored session must still contain the observations that authorized a live change.
 * Missing/unlogged sources cannot justify a lower opening load or rep target. */
export function reconcileRestoredLiveAdjustments(sets) {
    if (!Array.isArray(sets)) return sets;
    let changed = false;
    const out = sets.map((row, index) => {
        if (!untouched(row) || authoredIntent(row) || !row.retuneFrom || !row.liveAdjustment) return row;
        const adjustment = row.liveAdjustment;
        const count = adjustment.action === 'reduce_pending_load' ? 2
            : adjustment.action === 'cap_pending_reps' ? 1 : null;
        if (count === null) return row; // Unknown protocols are not ours to rewrite.
        const sources = adjustment.sourceIndices;
        const valid = Array.isArray(sources) && sources.length === count && new Set(sources).size === count
            && sources.every(sourceIndex => {
                const source = sets[sourceIndex];
                const reps = historyNumber(source?.reps), effort = historyNumber(source?.actualRIR);
                return Number.isInteger(sourceIndex) && sourceIndex >= 0 && sourceIndex < index
                    && source?.done && !source.warm && !source.sub && !source.added && !authoredIntent(source)
                    && historyNumber(source.weight) > 0 && Number.isInteger(reps) && reps > 0
                    && effort !== null && effort >= 0 && effort <= 10;
            });
        if (valid) return row;
        changed = true;
        return restorePendingAdjustment(row);
    });
    return changed ? out : sets;
}

/** Undo only app-owned pending changes that still equal their adjusted target. */
export function rollbackLiveAutoregulation(sets, sourceIndex, action) {
    if (!Array.isArray(sets)) return sets;
    let changed = false;
    const result = sets.map(row => {
        if (!untouched(row) || !row.liveAdjustment?.sourceIndices?.includes(sourceIndex) || !row.retuneFrom
            || action && row.liveAdjustment.action !== action) return row;
        changed = true;
        return restorePendingAdjustment(row);
    });
    return changed ? result : sets;
}

/** Two observed misses may ease one pending automatic accessory row; this never earns progression. */
function applyLiveLoadReduction({ sets, sourceIndex, cell, context = {}, snapLoad }) {
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
            && !authoredIntent(row) && !limited.nonComparable && !limited.interrupted && row.liveAdjustment?.action !== 'reduce_pending_load'
            && ((r < reps[0] && effort <= rir[0]) || (effort < rir[0] && r <= reps[1]));
    });
    if (!failed) {
        const restored = rollbackLiveAutoregulation(sets, sourceIndex);
        return hold(restored, 'retain_target_without_repeated_observed_miss');
    }
    const indices = observed.map(x => x.index), last = Math.max(...indices), pendingIndex = sets.findIndex((row, index) => index > last && !row?.done && !row?.warm && !row?.sub);
    if (pendingIndex < 0) return hold(sets, 'no_pending_work');
    const pending = sets[pendingIndex];
    if (!untouched(pending) || authoredIntent(pending)) return hold(sets, 'protect_manual_or_changed_row');
    if (pending.liveAdjustment && pending.liveAdjustment.action !== 'cap_pending_reps') return hold(sets, 'already_adjusted_pending_row');
    const load = historyNumber(pending.weight);
    if (!(load > 0) || typeof snapLoad !== 'function') return hold(sets, 'unknown_loadability');
    const next = snapLoad(load * .95);
    if (!Number.isFinite(next) || next <= 0 || next >= load || next < load * .9) return hold(sets, 'no_small_loadable_reduction');
    const decision = { schemaVersion: 1, action: 'reduce_pending_load', reasonCode: 'two_observed_accessory_misses', sourceIndices: indices,
        pendingIndex, fromLoad: load, toLoad: next, earnedProgression: false, confidence: 'moderate' };
    const result = [...sets];
    result[pendingIndex] = { ...pending, weight: String(next), target: { ...pending.target, w: next }, autoTuned: true, hint: 'down',
        recoveryLimited: true, liveAdjustment: decision, retuneSrc: indices.at(-1),
        retuneFrom: pending.retuneFrom ?? { weight: pending.weight, reps: pending.reps, target: pending.target, recoveryLimited: pending.recoveryLimited } };
    return { sets: result, decision };
}

const bounds = value => {
    const raw = Array.isArray(value) ? value : String(value ?? '').split(/[-–]/);
    const pair = raw.map(historyNumber);
    if (pair.length === 1) pair.push(pair[0]);
    return pair.length === 2 && pair.every(n => n !== null) && pair[0] <= pair[1] ? pair : null;
};

/** Keep an untouched same-load rep suggestion within today's explicitly observed capacity.
 * This can only lower reps, never add load/reps or extrapolate across different weights.
 * The upper end of a reserve range is conservative; 8 @ 2 RIR cannot justify 11 @ 1–2 RIR.
 * Weekly progression remains history-owned; this is only a session-local guard. */
function capPendingReps({ sets, sourceIndex, cell, context = {} }) {
    if (!Array.isArray(sets) || !Number.isInteger(sourceIndex)) return hold(sets, 'invalid_live_scope');
    const baseline = rollbackLiveAutoregulation(sets, sourceIndex, 'cap_pending_reps');
    const source = baseline[sourceIndex];
    if (!source?.done || source.warm || source.sub || source.added || authoredIntent(source)) return hold(baseline, 'no_new_working_observation');
    const exposure = progressionExposureContext([source], context);
    if (exposure.interrupted || exposure.nonComparable) return hold(baseline, 'non_comparable_live_context');
    if (!cell || !['auto', 'double', 'dynamic', 'ladder', 'e1rm'].includes(cell.progressionStyle ?? 'auto')
        || cell.setTargets || cell.percentageProtocol) return hold(baseline, 'protect_authored_strength_protocol');
    const completed = baseline.map((row, index) => ({ row, index })).filter(x => x.row?.done && !x.row.warm && !x.row.sub);
    if (completed.at(-1)?.index !== sourceIndex) return hold(baseline, 'protect_later_observations');
    // Never skip over a completed/manual next working row to change one farther ahead.
    const pendingIndex = baseline.findIndex((row, index) => index > sourceIndex && row && !row.warm && !row.sub);
    const pending = baseline[pendingIndex];
    if (!untouched(pending) || pending.liveAdjustment) return hold(baseline, 'protect_manual_or_changed_row');
    const cellReps = bounds(cell.range ?? cell.reps);
    if (!cellReps || cellReps[0] === cellReps[1] || authoredIntent(pending)) return hold(baseline, 'protect_authored_strength_protocol');
    const reps = bounds(pending.target?.reps ?? cell.reps), rir = bounds(pending.target?.rir ?? cell.rir);
    const load = historyNumber(source.weight), nextLoad = historyNumber(pending.weight);
    const r = historyNumber(source.reps), effort = historyNumber(source.actualRIR), planned = historyNumber(pending.reps);
    if (!reps || reps[0] < 1 || reps[1] <= reps[0] || !rir || rir[0] < 0 || rir[1] > 10
        || !(load > 0 && nextLoad > 0) || Math.abs(load - nextLoad) > .01
        || !Number.isInteger(r) || r <= 0 || effort === null || effort < 0 || effort > 10 || !(planned > 0))
        return hold(baseline, 'unknown_or_different_live_intent');
    const cap = Math.max(Math.ceil(reps[0]), Math.min(Math.floor(reps[1]), Math.floor(r + effort - rir[1])));
    if (planned <= cap) return hold(baseline, 'retain_observed_rep_target');
    const decision = { schemaVersion: 1, action: 'cap_pending_reps', reasonCode: 'observed_effort_rep_cap',
        sourceIndices: [sourceIndex], pendingIndex, fromReps: planned, toReps: cap,
        earnedProgression: false, confidence: 'moderate' };
    const result = [...baseline];
    result[pendingIndex] = { ...pending, reps: String(cap), target: { ...pending.target, prefillReps: String(cap) },
        autoTuned: true, liveAdjustment: decision, retuneSrc: sourceIndex,
        retuneFrom: { weight: pending.weight, reps: pending.reps, target: pending.target } };
    return { sets: result, decision };
}

export function applyLiveAutoregulation(args) {
    const reduced = applyLiveLoadReduction(args);
    if (reduced.decision.action === 'reduce_pending_load') return reduced;
    return capPendingReps({ ...args, sets: reduced.sets });
}

/** UI copy reports what happened, rather than promising every rating changes the next set. */
export function liveEffortFeedback(sets, sourceIndex) {
    if (sets?.[sourceIndex]?.actualRIR == null) return 'Optional · guides suggestions';
    const pending = sets.slice(sourceIndex + 1).find(row => row && !row.warm && !row.sub && !row.done);
    if (!pending) return 'Saved for future workouts';
    const adjustment = pending.liveAdjustment;
    if (untouched(pending) && adjustment?.sourceIndices?.includes(sourceIndex)) {
        if (adjustment.action === 'cap_pending_reps') return 'Next set reps adjusted';
        if (adjustment.action === 'reduce_pending_load') return 'Next set load eased';
    }
    return 'Saved · next target unchanged';
}
