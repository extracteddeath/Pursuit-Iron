import { EXERCISE_MAP } from './exercise-db.js';
const round = (value) => Number(value.toFixed(2));
const clean = (values) => [...new Set((Array.isArray(values) ? values : []).filter(v => Number.isFinite(v) && v >= 0).map(round))].sort((a, b) => a - b);
const record = value => value && typeof value === 'object' && !Array.isArray(value) ? value : {};
// Defaults describe unspecified equipment. Explicit empty/malformed lists must stay empty rather
// than inventing selectable loads. Invalid numeric limits likewise cannot authorize a load step.
const limit = (value, fallback) => value === undefined ? fallback : Number.isFinite(value) ? Math.max(0, value) : 0;
const exactLoads = value => value === undefined ? undefined : clean(value);
export const DEFAULT_LOADING_INVENTORY = {
    unit: 'lb',
    barbell: {
        barWeight: 45,
        platePairs: [
            { weight: 2.5, pairs: 2 },
            { weight: 5, pairs: 2 },
            { weight: 10, pairs: 2 },
            { weight: 25, pairs: 2 },
            { weight: 45, pairs: 10 }
        ]
    },
    dumbbells: { availablePerHand: Array.from({ length: 20 }, (_, i) => (i + 1) * 5) },
    machine: { minimum: 10, increment: 10, maximum: 500 },
    cable: { minimum: 5, increment: 5, maximum: 300 },
    smith: { minimum: 5, increment: 5, maximum: 500 },
    exerciseOverrides: {}
};
export function normalizeLoadingInventory(input) {
    if (!input)
        return structuredClone(DEFAULT_LOADING_INVENTORY);
    input = record(input);
    const rawPlates = input.barbell?.platePairs === undefined ? DEFAULT_LOADING_INVENTORY.barbell.platePairs : input.barbell?.platePairs;
    const platePairs = (Array.isArray(rawPlates) ? rawPlates : [])
        .filter(p => p && Number.isFinite(p.weight) && p.weight > 0 && Number.isFinite(p.pairs) && p.pairs > 0)
        .map(p => ({ weight: round(p.weight), pairs: Math.max(1, Math.floor(p.pairs)) }))
        .sort((a, b) => a.weight - b.weight);
    const normalizeIncrement = (candidate, fallback) => ({
        minimum: limit(candidate?.minimum, fallback.minimum),
        increment: limit(candidate?.increment, fallback.increment),
        maximum: limit(candidate?.maximum, fallback.maximum),
        availableLoads: exactLoads(candidate?.availableLoads)
    });
    const exerciseOverrides = Object.fromEntries(Object.entries(record(input.exerciseOverrides)).map(([id, raw]) => {
        const override = { ...record(raw) };
        for (const key of ['minimum', 'increment', 'maximum'])
            if (override[key] !== undefined) override[key] = limit(override[key], 0);
        if (override.availableLoads !== undefined) override.availableLoads = clean(override.availableLoads);
        return [id, override];
    }));
    const unitToken = typeof input.unit === 'string' ? input.unit.trim().toLowerCase() : '';
    return {
        unit: unitToken === 'kg' ? 'kg' : 'lb',
        barbell: { barWeight: limit(input.barbell?.barWeight, 45), platePairs },
        dumbbells: { availablePerHand: clean(input.dumbbells?.availablePerHand === undefined ? DEFAULT_LOADING_INVENTORY.dumbbells.availablePerHand : input.dumbbells.availablePerHand) },
        machine: normalizeIncrement(input.machine, DEFAULT_LOADING_INVENTORY.machine),
        cable: normalizeIncrement(input.cable, DEFAULT_LOADING_INVENTORY.cable),
        smith: normalizeIncrement(input.smith, DEFAULT_LOADING_INVENTORY.smith),
        exerciseOverrides
    };
}
function availableSetup(exerciseId, equipment) {
    const def = EXERCISE_MAP.get(exerciseId);
    if (!def)
        return [];
    const setups = [def.equipment, ...(def.equipmentAlternatives ?? [])];
    if (!equipment?.length)
        return def.equipment;
    return setups.find(setup => setup.every(item => equipment.includes(item))) ?? def.equipment;
}
function modeFromSetup(exerciseId, equipment) {
    const def = EXERCISE_MAP.get(exerciseId);
    const setup = availableSetup(exerciseId, equipment);
    if (def?.flags.bodyweight)
        return 'bodyweight';
    if (setup.includes('barbell'))
        return 'barbell_total';
    if (setup.includes('dumbbell'))
        return 'dumbbell_per_hand';
    if (setup.includes('smith'))
        return 'smith_total';
    if (setup.includes('cable'))
        return 'cable_stack';
    if (setup.includes('machine') || setup.includes('leg_press'))
        return 'machine_stack';
    return 'external_load';
}
function achievableBarbellLoads(barWeight, plates, ceiling) {
    if (!Number.isFinite(ceiling) || barWeight > ceiling) return [];
    // A load query needs only reachable totals near its target, never the full stock of plates.
    // Bound pathological imported inventories without substituting imaginary plate combinations.
    const operationBudget = 100000;
    let operations = 0;
    let totals = new Set([round(barWeight)]);
    for (const plate of plates) {
        const before = [...totals];
        for (const current of before) {
            const step = plate.weight * 2;
            const usable = Math.min(plate.pairs, Math.floor((ceiling - current + 1e-6) / step));
            if (!Number.isFinite(step) || !(step > 0)) continue;
            for (let count = 1; count <= usable; count++) {
                if (++operations > operationBudget) return null;
                const load = round(current + step * count);
                if (Number.isFinite(load) && load <= ceiling + 1e-6) totals.add(load);
            }
        }
    }
    // Combining denominations requires repeated expansion after each denomination; the loop above does that.
    return [...totals].sort((a, b) => a - b);
}
function nextFromExact(values, current) {
    return clean(values).find(v => v > current + 1e-6) ?? null;
}
function nextFromIncrement(profile, current) {
    if (Array.isArray(profile.availableLoads))
        return nextFromExact(profile.availableLoads, current);
    const minimum = profile.minimum ?? 0;
    const increment = profile.increment;
    if (!(increment > 0))
        return null;
    const steps = Math.max(0, Math.floor((current - minimum) / increment) + 1);
    let next = round(minimum + steps * increment);
    if (next <= current + 1e-6)
        next = round(next + increment);
    if (profile.maximum !== undefined && next > profile.maximum + 1e-6)
        return null;
    return Number.isFinite(next) && next > current + 1e-6 ? next : null;
}
function overrideNext(override, current) {
    if (Array.isArray(override.availableLoads))
        return nextFromExact(override.availableLoads, current);
    if (override.increment !== undefined)
        return nextFromIncrement({ minimum: override.minimum ?? 0, increment: override.increment, maximum: override.maximum }, current);
    return undefined;
}
export function resolveLoadingMode(exerciseId, equipment, inventory) {
    const override = inventory?.exerciseOverrides?.[exerciseId];
    return override?.mode ?? modeFromSetup(exerciseId, equipment);
}
export function formatExerciseLoad(exerciseId, load, equipment, inventory) {
    if (load === null)
        return '—';
    const mode = resolveLoadingMode(exerciseId, equipment, inventory);
    const unit = inventory?.unit ?? 'lb';
    if (mode === 'dumbbell_per_hand' || inventory?.exerciseOverrides?.[exerciseId]?.perHand)
        return `${load} ${unit}/hand`;
    if (mode === 'bodyweight')
        return load > 0 ? `BW + ${load} ${unit}` : 'Bodyweight';
    return `${load} ${unit}`;
}
export function availableLoadAtOrBelow(exerciseId, desiredLoad, inventoryInput, equipment) {
    if (!Number.isFinite(desiredLoad) || desiredLoad <= 0)
        return null;
    const inventory = normalizeLoadingInventory(inventoryInput);
    const override = inventory.exerciseOverrides?.[exerciseId];
    const mode = override?.mode ?? modeFromSetup(exerciseId, equipment);
    let values = [];
    const incrementValues = (profile) => {
        if (Array.isArray(profile.availableLoads))
            return clean(profile.availableLoads);
        if (!Number.isFinite(profile.increment) || !(profile.increment > 0) || !Number.isFinite(profile.minimum))
            return [];
        const max = Math.min(profile.maximum ?? desiredLoad, desiredLoad);
        if (!Number.isFinite(max) || max < profile.minimum)
            return [];
        // The caller needs only the largest feasible load, not an array potentially
        // millions of entries long for a small increment in imported equipment.
        const steps = Math.floor((max - profile.minimum + 1e-6) / profile.increment);
        return [round(profile.minimum + steps * profile.increment)];
    };
    if (Array.isArray(override?.availableLoads))
        values = clean(override.availableLoads);
    else if (override?.increment !== undefined)
        values = incrementValues({ minimum: override.minimum ?? 0, increment: override.increment, maximum: override.maximum });
    else
        switch (mode) {
            case 'barbell_total':
                values = achievableBarbellLoads(inventory.barbell.barWeight, inventory.barbell.platePairs, desiredLoad) ?? [];
                break;
            case 'dumbbell_per_hand':
                values = clean(inventory.dumbbells.availablePerHand);
                break;
            case 'machine_stack':
                values = incrementValues(inventory.machine);
                break;
            case 'cable_stack':
                values = incrementValues(inventory.cable);
                break;
            case 'smith_total':
                values = incrementValues(inventory.smith);
                break;
            case 'external_load':
                values = override?.increment ? incrementValues({ minimum: override.minimum ?? 0, increment: override.increment, maximum: override.maximum }) : [];
                break;
            case 'bodyweight': return null;
        }
    const eligible = values.filter(v => v <= desiredLoad + 1e-6);
    return eligible.length ? eligible[eligible.length - 1] : null;
}
export function nextAvailableLoad(exerciseId, currentLoad, inventoryInput, equipment) {
    if (currentLoad === null || !Number.isFinite(currentLoad))
        return null;
    if (currentLoad < 0)
        return currentLoad;
    const inventory = normalizeLoadingInventory(inventoryInput);
    const override = inventory.exerciseOverrides?.[exerciseId];
    const overridden = override ? overrideNext(override, currentLoad) : undefined;
    if (overridden !== undefined)
        return overridden;
    const mode = override?.mode ?? modeFromSetup(exerciseId, equipment);
    switch (mode) {
        case 'barbell_total': {
            // Any first reachable total above current is at most one largest plate-pair step away:
            // take an ascending path to a heavier setup and examine its first crossing.
            const largestStep = inventory.barbell.platePairs.reduce((max, p) => Math.max(max, p.weight * 2), 0);
            const ceiling = Math.max(inventory.barbell.barWeight, currentLoad + largestStep);
            return nextFromExact(achievableBarbellLoads(inventory.barbell.barWeight, inventory.barbell.platePairs, ceiling), currentLoad);
        }
        case 'dumbbell_per_hand': return nextFromExact(inventory.dumbbells.availablePerHand, currentLoad);
        case 'machine_stack': return nextFromIncrement(inventory.machine, currentLoad);
        case 'cable_stack': return nextFromIncrement(inventory.cable, currentLoad);
        case 'smith_total': return nextFromIncrement(inventory.smith, currentLoad);
        case 'bodyweight': return null;
        case 'external_load': return override?.increment ? nextFromIncrement({ minimum: override.minimum ?? 0, increment: override.increment, maximum: override.maximum }, currentLoad) : null;
    }
}
export function loadingRecommendation(exerciseId, currentLoad, inventoryInput, equipment) {
    const inventory = normalizeLoadingInventory(inventoryInput);
    const mode = resolveLoadingMode(exerciseId, equipment, inventory);
    const suggestedLoad = nextAvailableLoad(exerciseId, currentLoad, inventory, equipment);
    const label = formatExerciseLoad(exerciseId, currentLoad, equipment, inventory);
    const suggestedLabel = suggestedLoad === null ? null : formatExerciseLoad(exerciseId, suggestedLoad, equipment, inventory);
    const exact = mode !== 'external_load';
    const rationale = suggestedLoad === null
        ? mode === 'bodyweight'
            ? 'This exercise is bodyweight-loaded; external load progression requires an explicit loading override.'
            : 'No heavier selectable load exists in the current equipment inventory.'
        : `Next selectable load is ${suggestedLabel}.`;
    return { mode, currentLoad, suggestedLoad, label, suggestedLabel, exact, rationale };
}
