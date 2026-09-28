import { availableLoadAtOrBelow, resolveLoadingMode } from './loading.js';
const numberPair = (value, fallback) => {
    if (Array.isArray(value) && value.length >= 2) {
        const a = Number(value[0]), b = Number(value[1]);
        if (Number.isFinite(a) && Number.isFinite(b))
            return [Math.min(a, b), Math.max(a, b)];
    }
    if (typeof value === 'number' && Number.isFinite(value))
        return [value, value];
    const raw = String(value ?? '').trim();
    const nums = raw.split(/[-–]/).map(x => Number(x.trim())).filter(Number.isFinite);
    if (nums.length >= 2)
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
export function buildRuntimeSetTargets(args) {
    const { exerciseId, cell, loadingInventory, equipmentAvailable } = args;
    const reps = numberPair(cell.reps, [8, 12]);
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
            const snapped = availableLoadAtOrBelow(exerciseId, workingLoad * fraction, loadingInventory, equipmentAvailable);
            if (snapped === null || snapped <= 0 || snapped >= workingLoad || snapped === last)
                continue;
            targets.push({ kind: 'warmup', weight: snapped, reps: warmReps, repRange: [warmReps, warmReps], rir: null });
            last = snapped;
        }
    }
    for (let i = 0; i < count; i++)
        targets.push({ kind: 'work', weight: workingLoad, reps: workReps, repRange: reps, rir });
    return targets;
}
