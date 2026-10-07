import { selectProgressionStyle } from './progression-style.js';
import { availableLoadAtOrBelow, resolveLoadingMode } from './loading.js';
const numberPair = (value, fallback, minimum = 0) => {
    const valid = nums => nums.every(n => Number.isFinite(n) && n >= minimum);
    if (Array.isArray(value) && value.length >= 2) {
        const a = value[0] == null || String(value[0]).trim() === '' ? NaN : Number(value[0]);
        const b = value[1] == null || String(value[1]).trim() === '' ? NaN : Number(value[1]);
        if (valid([a, b]))
            return [Math.min(a, b), Math.max(a, b)];
    }
    if (typeof value === 'number' && valid([value]))
        return [value, value];
    const raw = String(value ?? '').trim();
    if (!raw) return fallback;
    const parts = raw.split(/[-–]/);
    if (parts.some(x => !x.trim()) || parts.length > 2) return fallback;
    const nums = parts.map(x => Number(x.trim()));
    if (!valid(nums)) return fallback;
    if (nums.length === 2)
        return [Math.min(nums[0], nums[1]), Math.max(nums[0], nums[1])];
    if (nums.length === 1)
        return [nums[0], nums[0]];
    return fallback;
};
export function freestyleCellForRepRange(repRange) {
    const lo = Number(repRange?.[0]), hi = Number(repRange?.[1]);
    const range = Number.isFinite(lo) && Number.isFinite(hi) ? [Math.max(1, Math.round(Math.min(lo, hi))), Math.max(1, Math.round(Math.max(lo, hi)))] : [8, 12];
    return {
        sets: 3,
        reps: range,
        rir: [2, 2],
        rest: 90,
        role: 'accessory',
        progressionStyle: 'freestyle',
        tech: null
    };
}
export function buildUserAddedSlotPrescriptions(args) {
    const base = freestyleCellForRepRange(args.repRange);
    const role = args.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation';
    const out = {};
    for (let week = 1; week <= Math.max(1, Math.round(args.weeks || 1)); week++)
        out[week] = { ...base, role, progressionStyle: 'auto' };
    return out;
}
export function techniqueProtocolFromCell(cell) {
    const cue = String(cell.tech ?? '').toLowerCase();
    if (cue.includes('myo'))
        return { type: 'myo_reps', miniSets: { minimum: 3, maximum: 5, targetReps: 5, restSeconds: 15 } };
    if (cue.includes('drop'))
        return { type: 'drop_set', drops: [{ fraction: .8, restSeconds: 15 }, { fraction: .6, restSeconds: 15 }] };
    if (cue.includes('partial'))
        return { type: 'lengthened_partials', partials: { onLastSet: true, position: 'lengthened' } };
    return { type: null };
}
// Audits and history evaluation must see the same final-set protocol as Workout, including Off.
export function advancedTechniqueFromCell(cell, original) {
    const protocol = techniqueProtocolFromCell(cell);
    if (!protocol.type) return undefined;
    return { ...(original?.type === protocol.type ? original : {}), ...protocol,
        appliesTo: 'last_set', note: String(cell.tech) };
}
export function buildRuntimeSetTargets(args) {
    const { exerciseId, cell, loadingInventory, equipmentAvailable } = args;
    const reps = numberPair(cell.reps, [8, 12], 1);
    const rir = numberPair(cell.rir, [2, 2]);
    const workReps = args.suggestedReps && args.suggestedReps >= reps[0] && args.suggestedReps <= reps[1] ? Math.round(args.suggestedReps) : Math.round(reps[0]);
    const count = Math.max(1, Math.round(Number(cell.sets) || 1));
    const workingLoad = Number.isFinite(args.workingLoad) && Number(args.workingLoad) > 0 ? Number(args.workingLoad) : null;
    const targets = [];
    const role = String(cell.role ?? '');
    const strengthRole = role === 'primary_strength' || role === 'secondary_strength';
    const mode = resolveLoadingMode(exerciseId, equipmentAvailable, loadingInventory);
    if (args.includeWarmups !== false && strengthRole && workingLoad && mode !== 'bodyweight') {
        const ramps = role === 'primary_strength' ? [[.5, 5], [.7, 3], [.85, 1]] : [[.6, 5], [.8, 3]];
        let last = null;
        for (const [fraction, warmReps] of ramps) {
            const snapped = args.snapLoad ? args.snapLoad(workingLoad * fraction)
                : availableLoadAtOrBelow(exerciseId, workingLoad * fraction, loadingInventory, equipmentAvailable);
            if (snapped === null || snapped <= 0 || snapped >= workingLoad || snapped === last)
                continue;
            targets.push({ kind: 'warmup', weight: snapped, reps: warmReps, repRange: [warmReps, warmReps], rir: null });
            last = snapped;
        }
    }
    for (let i = 0; i < count; i++) {
        const target = args.setTargets?.[i];
        const weight = Number.isFinite(target?.weight) ? target.weight : workingLoad;
        const repTarget = Number.isFinite(target?.reps) ? Math.max(reps[0], Math.min(reps[1], Math.round(target.reps))) : workReps;
        targets.push({ kind: 'work', weight, reps: repTarget, repRange: reps, rir });
    }
    return targets;
}

/** Saved automatic entries must obey the current prescription. This only repairs pending app-owned
 * working rows; actual performance, manual entries, warmups, and technique extensions are immutable.
 * Loads are deliberately retained because the lifter may have adjusted them during the workout. */
export function reconcilePendingRepTargets(sets, cell) {
    if (!Array.isArray(sets) || !cell || cell.missing)
        return sets;
    const [lo, hi] = numberPair(cell.range ?? cell.reps, [8, 12], 1);
    if (!(lo > 0 && hi >= lo))
        return sets;
    const range = lo === hi ? String(lo) : `${lo}-${hi}`;
    let changed = false;
    const out = sets.map(s => {
        const prescriptionOwned = s?.valueOwner != null ? s.valueOwner === 'prescription' : s?.auto === true;
        if (!s || s.done || s.warm || s.sub || (s.target?.nextAction === 'percentage' && cell.ownership?.reps !== 'user') || !prescriptionOwned)
            return s;
        const r = Number(s.reps);
        const reps = String(s.reps ?? '').trim() && Number.isFinite(r)
            ? String(Math.max(Math.ceil(lo), Math.min(Math.floor(hi), Math.round(r)))) : s.reps;
        const suggested = s.suggested && Number.isFinite(Number(s.suggested.reps))
            ? { ...s.suggested, reps: String(Math.max(Math.ceil(lo), Math.min(Math.floor(hi), Math.round(Number(s.suggested.reps))))) }
            : s.suggested;
        if (String(reps) === String(s.reps) && s.target?.reps === range
            && (!s.suggested || String(suggested?.reps) === String(s.suggested.reps)))
            return s;
        changed = true;
        return { ...s, reps, ...(suggested ? { suggested } : {}), target: { ...s.target, reps: range } };
    });
    return changed ? out : sets;
}

// Refresh a resumed prescription only while BOTH values still equal the saved automatic prefill.
// Old snapshots without ownership metadata, manual/added rows and logged work are preserved.
export function refreshPendingSetTargets(sets, fresh) {
    if (!Array.isArray(sets) || !Array.isArray(fresh)) return sets;
    const work = fresh.filter(s => !s.warm && !s.sub && !s.added);
    let ordinal = 0, changed = false;
    const out = sets.map(s => {
        if (!s || s.warm || s.sub || s.added) return s;
        const target = work[ordinal++];
        if (!target || s.done || s.valueOwner !== 'prescription' || s.auto !== true
            || String(s.weight) !== String(s.target?.w)
            || (s.target?.prefillReps != null && String(s.reps) !== String(s.target.prefillReps))) return s;
        // Pre-M222 automatic rows only stored the range; auto+ownership are still required.
        if (s.weight === target.weight && s.reps === target.reps
            && s.target?.prefillReps === target.target?.prefillReps) return s;
        changed = true;
        return { ...s, weight: target.weight, reps: target.reps, target: target.target };
    });
    return changed ? out : sets;
}

export function customProgramProgressionStyle(exercise, cell, requestedStyle, config = {}) {
    return selectProgressionStyle(exercise, cell?.role || 'accessory', {
        requestedStyle, experience: config.experience,
        blockWeeks: config.weeks, prescription: { reps: numberPair(cell?.reps, [8,12], 1) }
    }).style;
}
