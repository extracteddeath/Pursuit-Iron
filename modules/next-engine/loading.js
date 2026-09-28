import { EXERCISE_MAP } from './exercise-db.js';
const round = (value) => Number(value.toFixed(2));
const clean = (values) => [...new Set(values.filter(v => Number.isFinite(v) && v >= 0).map(round))].sort((a, b) => a - b);
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
export function summarizeLoadingInventory(input) {
    const inventory = normalizeLoadingInventory(input);
    const smallest = Math.min(...inventory.barbell.platePairs.map(p => p.weight));
    const dumbbells = inventory.dumbbells.availablePerHand;
    const diffs = dumbbells.slice(1).map((value, index) => round(value - dumbbells[index])).filter(v => v > 0);
    const dumbbellIncrement = (diffs[0] ?? 5);
    const smallestPlatePerSide = (smallest <= 1.25 ? 1.25 : smallest <= 2.5 ? 2.5 : 5);
    const clampIncrement = (value) => value <= 2.5 ? 2.5 : value <= 5 ? 5 : value <= 10 ? 10 : 15;
    return {
        barWeight: inventory.barbell.barWeight,
        smallestPlatePerSide,
        dumbbellIncrement,
        dumbbellMaximum: dumbbells.at(-1) ?? 100,
        machineIncrement: clampIncrement(inventory.machine.increment),
        cableIncrement: clampIncrement(inventory.cable.increment)
    };
}
export function createLoadingInventory(settings = {}) {
    const smallest = settings.smallestPlatePerSide ?? 2.5;
    const standard = [1.25, 2.5, 5, 10, 25, 45].filter(weight => weight >= smallest);
    const dumbbellIncrement = settings.dumbbellIncrement ?? 5;
    const dumbbellMaximum = Math.max(dumbbellIncrement, settings.dumbbellMaximum ?? 100);
    const dumbbells = [];
    for (let value = dumbbellIncrement; value <= dumbbellMaximum + 1e-6; value += dumbbellIncrement)
        dumbbells.push(round(value));
    const machineIncrement = settings.machineIncrement ?? 5;
    const cableIncrement = settings.cableIncrement ?? machineIncrement;
    return normalizeLoadingInventory({
        unit: 'lb',
        barbell: { barWeight: settings.barWeight ?? 45, platePairs: standard.map(weight => ({ weight, pairs: weight >= 45 ? 10 : 4 })) },
        dumbbells: { availablePerHand: dumbbells },
        machine: { minimum: machineIncrement, increment: machineIncrement, maximum: 500 },
        cable: { minimum: cableIncrement, increment: cableIncrement, maximum: 300 },
        smith: { minimum: machineIncrement, increment: machineIncrement, maximum: 500 },
        exerciseOverrides: {}
    });
}
export function normalizeLoadingInventory(input) {
    if (!input)
        return structuredClone(DEFAULT_LOADING_INVENTORY);
    const platePairs = (input.barbell?.platePairs ?? DEFAULT_LOADING_INVENTORY.barbell.platePairs)
        .filter(p => Number.isFinite(p.weight) && p.weight > 0 && Number.isFinite(p.pairs) && p.pairs > 0)
        .map(p => ({ weight: round(p.weight), pairs: Math.max(1, Math.floor(p.pairs)) }))
        .sort((a, b) => a.weight - b.weight);
    const normalizeIncrement = (candidate, fallback) => ({
        minimum: Number.isFinite(candidate?.minimum) ? Math.max(0, candidate.minimum) : fallback.minimum,
        increment: Number.isFinite(candidate?.increment) && candidate.increment > 0 ? candidate.increment : fallback.increment,
        maximum: Number.isFinite(candidate?.maximum) ? Math.max(0, candidate.maximum) : fallback.maximum,
        availableLoads: candidate?.availableLoads?.length ? clean(candidate.availableLoads) : undefined
    });
    return {
        unit: input.unit === 'kg' ? 'kg' : 'lb',
        barbell: { barWeight: Number.isFinite(input.barbell?.barWeight) ? Math.max(0, input.barbell.barWeight) : 45, platePairs },
        dumbbells: { availablePerHand: clean(input.dumbbells?.availablePerHand?.length ? input.dumbbells.availablePerHand : DEFAULT_LOADING_INVENTORY.dumbbells.availablePerHand) },
        machine: normalizeIncrement(input.machine, DEFAULT_LOADING_INVENTORY.machine),
        cable: normalizeIncrement(input.cable, DEFAULT_LOADING_INVENTORY.cable),
        smith: normalizeIncrement(input.smith, DEFAULT_LOADING_INVENTORY.smith),
        exerciseOverrides: { ...(input.exerciseOverrides ?? {}) }
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
function achievableBarbellLoads(barWeight, plates) {
    let totals = new Set([round(barWeight)]);
    for (const plate of plates) {
        const before = [...totals];
        for (const current of before) {
            for (let count = 1; count <= plate.pairs; count++)
                totals.add(round(current + plate.weight * 2 * count));
        }
    }
    // Combining denominations requires repeated expansion after each denomination; the loop above does that.
    return [...totals].sort((a, b) => a - b);
}
function nextFromExact(values, current) {
    return clean(values).find(v => v > current + 1e-6) ?? null;
}
function nextFromIncrement(profile, current) {
    if (profile.availableLoads?.length)
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
    return next;
}
function overrideNext(override, current) {
    if (override.availableLoads?.length)
        return nextFromExact(override.availableLoads, current);
    if (override.increment && override.increment > 0)
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
        if (profile.availableLoads?.length)
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
    if (override?.availableLoads?.length)
        values = clean(override.availableLoads);
    else if (override?.increment && override.increment > 0)
        values = incrementValues({ minimum: override.minimum ?? 0, increment: override.increment, maximum: override.maximum });
    else
        switch (mode) {
            case 'barbell_total':
                values = achievableBarbellLoads(inventory.barbell.barWeight, inventory.barbell.platePairs);
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
        case 'barbell_total': return nextFromExact(achievableBarbellLoads(inventory.barbell.barWeight, inventory.barbell.platePairs), currentLoad);
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
