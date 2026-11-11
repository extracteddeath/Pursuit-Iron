/**
 * Gym-scoped physical machine identities for one logical exercise.
 * Movement identity drives programming and volume; physical identity drives load
 * progression, load PRs, strength trends and Last/Target comparisons.
 *
 * Unlabeled historical sets remain LEGACY, never reassigned to a newly added A.
 */
export const LEGACY_MACHINE_ID = '__legacy_machine__';
export const UNKNOWN_GYM_ID = '__unknown_gym__';
const idRule = /^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$/;
const object = value => value && typeof value === 'object' && !Array.isArray(value) ? value : {};
const validId = value => typeof value === 'string' && idRule.test(value);
const requiredId = (value, field) => {
    if (!validId(value)) throw new TypeError(`Invalid ${field}`);
    return value;
};
const variantList = (gym, exerciseId) => {
    const items = object(gym?.machineVariants)[exerciseId]?.instances;
    return Array.isArray(items) ? items : [];
};
const configFor = (gym, exerciseId) => object(object(gym?.machineVariants)[exerciseId]);
const editable = value => String(value ?? '').trim().slice(0, 80);
const ensureInstance = (instance, allowInactive = false) => {
    requiredId(instance?.id, 'machine instance ID');
    if (!editable(instance?.label)) throw new TypeError('Machine label required');
    if (instance.id === LEGACY_MACHINE_ID || instance.id === UNKNOWN_GYM_ID)
        throw new TypeError('Reserved machine instance ID');
    if (!allowInactive && instance.active === false) throw new TypeError('Inactive machine cannot be selected as default');
    const loading = instance.loading === undefined ? undefined : object(instance.loading);
    if (loading) {
        if (loading.unit !== undefined && !['lb','kg'].includes(loading.unit))
            throw new TypeError('Machine loading unit must be lb or kg');
        for (const field of ['minimum','increment','maximum'])
            if (loading[field] !== undefined && !(Number.isFinite(loading[field]) &&
                loading[field] >= 0 && (field !== 'increment' || loading[field] > 0)))
                throw new TypeError(`Invalid machine ${field}`);
        if (loading.minimum !== undefined && loading.maximum !== undefined &&
            loading.minimum > loading.maximum) throw new TypeError('Invalid machine load range');
    }
    return {
        id: instance.id,
        label: editable(instance.label),
        active: instance.active !== false,
        ...(editable(instance.description) ? { description: editable(instance.description) } : {}),
        ...(loading ? { loading: { ...loading } } : {})
    };
};
function updatedGym(gym, exerciseId, next) {
    requiredId(gym?.id, 'gym ID');
    requiredId(exerciseId, 'logical exercise ID');
    return { ...gym, machineVariants: {
        ...object(gym.machineVariants),
        [exerciseId]: { defaultId: next.defaultId ?? null, instances: next.instances.map(v => ({ ...v, ...(v.loading ? {loading:{...v.loading}} : {}) })) }
    }};
}
export function addMachineVariant(gym, exerciseId, instance, makeDefault = false) {
    const created = ensureInstance(instance);
    const current = configFor(gym, exerciseId);
    const instances = variantList(gym, exerciseId);
    if (instances.some(v => v.id === created.id)) throw new TypeError('Duplicate machine instance ID');
    return updatedGym(gym, exerciseId, {
        defaultId: makeDefault || !current.defaultId ? created.id : current.defaultId,
        instances: [...instances, created]
    });
}
export function editMachineVariant(gym, exerciseId, instanceId, patch) {
    const current = configFor(gym, exerciseId);
    const instances = variantList(gym, exerciseId);
    if (!instances.some(v => v.id === instanceId)) throw new TypeError('Unknown machine');
    const next = instances.map(v => v.id === instanceId
        ? ensureInstance({ ...v, ...patch, id: instanceId, loading: patch?.loading === undefined ? v.loading : patch.loading }, true) : v);
    if (current.defaultId === instanceId && next.find(v => v.id === instanceId)?.active === false)
        throw new TypeError('Choose a different default before deactivating this machine');
    return updatedGym(gym, exerciseId, {defaultId:current.defaultId, instances:next});
}
export function setDefaultMachineVariant(gym, exerciseId, instanceId) {
    const current = configFor(gym, exerciseId);
    const found = variantList(gym, exerciseId).find(v => v.id === instanceId && v.active !== false);
    if (!found) throw new TypeError('Default must be an active machine in this gym');
    return updatedGym(gym, exerciseId, {defaultId:instanceId, instances:current.instances});
}
/** Existing saved selections take priority even if that machine is deactivated. */
export function resolveMachineVariant(gym, exerciseId, selectedId = null, { forNewWorkout = true } = {}) {
    const current = configFor(gym, exerciseId);
    const instances = variantList(gym, exerciseId);
    if (selectedId != null) {
        if (selectedId === LEGACY_MACHINE_ID)
            return { id: LEGACY_MACHINE_ID, label: 'Legacy machine', active: false, legacy: true };
        const selected = instances.find(v => v.id === selectedId);
        if (selected) return { ...selected, legacy: false };
        if (!forNewWorkout) return { id: String(selectedId), label: `Former machine (${selectedId})`, active: false, missing: true };
        throw new TypeError('Selected machine is unknown in this gym');
    }
    const fallback = instances.find(v => v.id === current.defaultId && v.active !== false)
        ?? instances.find(v => v.active !== false);
    return fallback ? { ...fallback, legacy:false }
        : { id: LEGACY_MACHINE_ID, label: 'Legacy machine', active:true, legacy:true };
}
/** The same movement on distinct machines or gyms has incompatible loads. */
export function comparableMachineScope(a, b, {sameTrainingDay = true} = {}) {
    if (!a || !b) return false;
    const gymA = a.gymId ?? UNKNOWN_GYM_ID, gymB = b.gymId ?? UNKNOWN_GYM_ID;
    const machineA = a.machineInstanceId ?? LEGACY_MACHINE_ID;
    const machineB = b.machineInstanceId ?? LEGACY_MACHINE_ID;
    return gymA === gymB && a.exerciseId === b.exerciseId && machineA === machineB &&
        (!sameTrainingDay || a.dayId === b.dayId);
}
/** New set receipts own their chosen machine; targets and movement IDs do not change. */
export function stampMachineSet(set, {gymId, exerciseId, machineInstanceId}) {
    requiredId(gymId, 'gym ID');
    requiredId(exerciseId, 'exercise ID');
    if (machineInstanceId !== LEGACY_MACHINE_ID) requiredId(machineInstanceId, 'machine instance ID');
    return { ...set, gymId, exerciseId, machineInstanceId };
}
/** A legacy row cannot be guessed to be A simply because A is the gym default now. */
export function machineIdOfSet(set, performance) {
    return set?.machineInstanceId ?? performance?.machineInstanceId ?? LEGACY_MACHINE_ID;
}
/**
 * Returns only observed completed working sets in one truly comparable scope.
 * Intra-workout A/B switches survive as separate rows, without dropping history.
 */
export function machineHistory(history, { gymId, exerciseId, machineInstanceId, dayId = null }) {
    if (!validId(exerciseId) || !gymId ||
        !machineInstanceId || (dayId !== null && !String(dayId))) return [];
    const records = [];
    for (const entry of Array.isArray(history) ? history : []) {
        if ((entry.gymId ?? UNKNOWN_GYM_ID) !== gymId ||
            dayId !== null && entry.dayId !== dayId) continue;
        const performance = entry.perf?.[exerciseId];
        if (!performance) continue;
        for (const [index, set] of (Array.isArray(performance.sets) ? performance.sets : []).entries()) {
            const weight = Number(set?.w), reps = Number(set?.r);
            if (!set || set.warm || set.sub || set.done === false ||
                !(reps > 0) || !(weight >= 0) || !Number.isFinite(weight) ||
                machineIdOfSet(set, performance) !== machineInstanceId) continue;
            records.push({ date:entry.date, workoutId:entry.id ?? null, dayId:entry.dayId,
                gymId, exerciseId, machineInstanceId,
                setIndex:Number.isInteger(set.workingSetIndex) ? set.workingSetIndex : index,
                weight, reps, rir:set.rir ?? null, unit: entry.unit ?? null });
        }
    }
    return records.sort((a,b) => Number(a.date || 0)-Number(b.date || 0));
}
export function latestMachineSet(history, scope) {
    return machineHistory(history, scope).at(-1) ?? null;
}
/** Filter a targeted exercise without stripping other exercises or their muscle dose. */
export function historyForMachineRecommendation(history, exerciseId, machineInstanceId, gymId) {
    return (Array.isArray(history) ? history : []).map(entry => {
        const performance = entry?.perf?.[exerciseId];
        if (!performance?.sets || (entry.gymId ?? UNKNOWN_GYM_ID) !== gymId)
            return entry?.perf?.[exerciseId]
                ? { ...entry, perf: { ...entry.perf, [exerciseId]: { ...performance, sets: [] } } }
                : entry;
        const sets = performance.sets.filter(set =>
            machineIdOfSet(set, performance) === machineInstanceId);
        return { ...entry, perf: { ...entry.perf,
            [exerciseId]: { ...performance, sets,
                weight: sets.length ? Math.max(...sets.map(s => Number(s.w)).filter(Number.isFinite)) : null,
                machineInstanceId } } };
    });
}

/** Persist only validated gym-scoped machine definitions; never flatten A/B into stock. */
export function sanitizeMachineVariants(raw) {
    const out = {};
    for (const [exerciseId, record] of Object.entries(object(raw)).slice(0,256)) {
        if (!validId(exerciseId)) continue;
        const instances = [];
        for (const item of (Array.isArray(record?.instances) ? record.instances : []).slice(0,32)) {
            try {
                const clean = ensureInstance(item, true);
                if (!instances.some(v => v.id === clean.id)) instances.push(clean);
            } catch { /* corrupt entries cannot become a progression authority */ }
        }
        if (!instances.length) continue;
        const preferred = instances.find(v => v.id === record.defaultId && v.active)
            ?? instances.find(v => v.active);
        out[exerciseId] = { defaultId: preferred?.id ?? null, instances };
    }
    return out;
}
