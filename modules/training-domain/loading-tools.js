// Loading tools share the production plate solver, unit conversion and e1RM model.
import { BARS, platesPerSide, barWeightFor, platePairsFor } from './analytics.js';
import { e1rm, E1RM_REP_CAP } from './programs.js';
import { convertHistoryLoad } from '../next-engine/history-contract.js';

/** Resolve each staged edit against the latest gym, not the render that began typing. */
export const gymBarDraftUpdate = (kind, value) => gym => {
    const bars = gym.barWeights || {};
    return { barWeights: { ...bars, unit: bars.unit === 'kg' ? 'kg' : 'lb', [kind]: value } };
};
export const gymPlatePairDraftUpdate = (unit, plate, value, plates) => gym => {
    const counts = platePairsFor(gym, unit, plates?.[unit]);
    return { platePairs: { ...gym.platePairs, [unit]: {
        ...counts, ...gym.platePairs?.[unit], [plate]: value
    } } };
};

export function readLoadingNumber(input, { min = 0, max = Infinity, integer = false } = {}) {
    const raw = input == null ? '' : String(input).trim();
    if (!raw) return { status: 'empty', value: null };
    const valid = typeof input === 'number' || /^(?:\d+(?:\.\d+)?|\.\d+)$/.test(raw);
    const value = Number(raw);
    return valid && Number.isFinite(value) && value >= min && value <= max && (!integer || Number.isInteger(value))
        ? { status: 'valid', value } : { status: 'invalid', value: null };
}

export function plateLoadPreview(input, bar, unit) {
    const parsed = readLoadingNumber(input);
    if (parsed.status !== 'valid') return parsed;
    if (!BARS[bar] || !['lb', 'kg'].includes(unit)) return { status: 'invalid', value: null };
    const target = parsed.value, barW = barWeightFor(bar, unit);
    if (target < barW) return { status: 'below_bar', target, barW, plates: [], achievable: barW };
    // Match the production solver's bounded per-side range instead of displaying a fabricated result.
    if (target > barW + 4000) return { status: 'invalid', value: null };
    const result = platesPerSide(target, bar, unit);
    const achievable = Math.round((result.barW + result.plates.reduce((sum, plate) => sum + plate, 0) * 2) * 1000000) / 1000000;
    const exact = Math.abs(achievable - target) < 1e-6;
    return { ...result, target, achievable, status: exact ? 'exact' : result.plates.length ? 'closest' : 'no_plates' };
}

export function repMaxPreview(weightInput, repsInput, unit) {
    const weight = readLoadingNumber(weightInput, { min: Number.MIN_VALUE });
    const reps = readLoadingNumber(repsInput, { min: 1, max: E1RM_REP_CAP, integer: true });
    if (weight.status === 'invalid' || reps.status === 'invalid' || !['lb', 'kg'].includes(unit))
        return { status: 'invalid', maxReps: E1RM_REP_CAP, weightStatus: weight.status, repsStatus: reps.status };
    if (weight.status === 'empty' || reps.status === 'empty') return { status: 'empty', maxReps: E1RM_REP_CAP };
    const oneRM = e1rm(weight.value, reps.value), increment = unit === 'lb' ? 5 : 2.5;
    if (!Number.isFinite(oneRM)) return { status: 'invalid', maxReps: E1RM_REP_CAP };
    return { status: 'valid', oneRM, increment, maxReps: E1RM_REP_CAP,
        percentages: [100, 95, 90, 85, 80, 75, 70, 65, 60].map(percent =>
            ({ percent, load: Math.floor(oneRM * percent / 100 / increment) * increment })) };
}

const rackKeys = ['dumbbell', 'kettlebell', 'machine'];
const limitKeys = ['dumbbell', 'kettlebell', 'barbell', 'machine'];
export function prepareGymLoadDraft(gym) {
    if (!gym || typeof gym !== 'object' || Array.isArray(gym))
        return { ok: false, gym: {}, issues: [{ gymId: '', fieldId: '', message: 'Choose a valid gym.' }] };
    const next = { ...gym, inventory: {}, limits: {} }, issues = [];
    const issue = (field, message) => issues.push({ gymId: gym.id, fieldId: `gym-${gym.id}-${field}`, message });
    for (const key of rackKeys) {
        const raw = gym.inventoryText?.[key];
        if (raw === undefined && gym.inventory?.[key] === undefined) continue;
        if (raw === undefined && !Array.isArray(gym.inventory[key])) { issue(`rack-${key}`, 'Enter a valid list of available loads.'); continue; }
        const tokens = raw === undefined ? gym.inventory[key] : String(raw).trim().split(/[,\s]+/).filter(Boolean);
        const parsed = tokens.map(raw => readLoadingNumber(raw, { min: Number.MIN_VALUE }));
        if (parsed.some(value => value.status !== 'valid')) issue(`rack-${key}`, 'Enter positive loads separated by commas, or clear the rack.');
        else if (parsed.length) next.inventory[key] = [...new Set(parsed.map(value => value.value))].sort((a, b) => a - b);
        else delete next.inventory[key];
    }
    for (const key of limitKeys) {
        const value = readLoadingNumber(gym.limits?.[key], { min: Number.MIN_VALUE });
        if (value.status === 'invalid') issue(`cap-${key}`, 'Enter a positive maximum load, or leave it blank.');
        else if (value.status === 'valid') next.limits[key] = value.value;
        else delete next.limits[key];
    }
    if (gym.barWeights && typeof gym.barWeights === 'object') {
        next.barWeights = { ...gym.barWeights };
        for (const key of ['barbell', 'smith']) {
            const value = readLoadingNumber(gym.barWeights[key], { max: 500 });
            if (value.status === 'invalid') issue(`${key}-bar-weight`, 'Enter an empty bar weight from 0 to 500, or leave it blank.');
            else if (value.status === 'valid') next.barWeights[key] = value.value;
            else delete next.barWeights[key];
        }
    }
    if (gym.platePairs) {
        next.platePairs = {};
        for (const unit of ['lb', 'kg']) {
            if (!gym.platePairs[unit]) continue;
            next.platePairs[unit] = {};
            for (const [plate, raw] of Object.entries(gym.platePairs[unit])) {
                const value = readLoadingNumber(raw, { integer: true, max: 50 });
                if (value.status === 'invalid') issue(`plates-${unit}-${plate}`, 'Enter a whole number of pairs from 0 to 50.');
                else next.platePairs[unit][plate] = value.value ?? 0;
            }
        }
    }
    delete next.inventoryText;
    return { ok: issues.length === 0, gym: next, issues };
}

export function withGymLoadingUnit(gym, unit) {
    const draft = prepareGymLoadDraft(gym);
    if (!draft.ok || !['lb', 'kg'].includes(unit)) return { ...draft, ok: false };
    const from = gym.limitUnit === 'lb' ? 'lb' : 'kg';
    return { ...draft, gym: { ...draft.gym, limitUnit: unit,
        limits: Object.fromEntries(Object.entries(draft.gym.limits).map(([key, value]) => [key, convertHistoryLoad(value, from, unit)])),
        inventory: Object.fromEntries(Object.entries(draft.gym.inventory).map(([key, values]) => [key, values.map(value => convertHistoryLoad(value, from, unit))])) } };
}

export function withGymBarUnit(gym, unit) {
    const draft = prepareGymLoadDraft(gym);
    if (!draft.ok || !['lb', 'kg'].includes(unit)) return { ...draft, ok: false };
    const bars = draft.gym.barWeights || {}, from = bars.unit === 'kg' ? 'kg' : 'lb';
    return { ...draft, gym: { ...draft.gym, barWeights: { ...bars, unit,
        ...Object.fromEntries(['barbell', 'smith'].filter(key => Number.isFinite(bars[key])).map(key => [key, convertHistoryLoad(bars[key], from, unit)])) } } };
}
