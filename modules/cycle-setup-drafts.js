// Device-local setup only. Never merged with training records or workout drafts.
export const CYCLE_SETUP_DRAFT_KEY = 'wpb:cycle-setup:v1';
const phases = new Set(['hypertrophy_accumulation', 'strength_accumulation', 'mixed_accumulation', 'intensification', 'peak', 'foundation']);
export function normalizeCycleSetupDraft(value, signature) {
    if (!value || value.signature !== signature || value.v !== 1) return null;
    const config = {};
    for (const key of ['name','unit','experience','goal','split','session','progression','volumeApproach','progressionStyle'])
        if (typeof value.config?.[key] === 'string') config[key] = value.config[key].slice(0, 120);
    for (const key of ['days','weeks','barbellCap']) {
        const n = value.config?.[key];
        if (Number.isSafeInteger(n) && n >= (key === 'barbellCap' ? 0 : 1) && n <= (key === 'days' ? 6 : 104)) config[key] = n;
    }
    for (const [key, allowed] of Object.entries({ unit:['lb','kg'], experience:['none','beginner','intermediate','advanced'], goal:['strength','hypertrophy','both'], session:['s20','s40','s60','s90','s120','s120p'], volumeApproach:['standard','minimalist'] }))
        if (key in config && !allowed.includes(config[key])) delete config[key];
    for (const key of ['noSupersets','noBodyweight','deload','endless'])
        if (typeof value.config?.[key] === 'boolean') config[key] = value.config[key];
    for (const key of ['equipment','reduce'])
        if (Array.isArray(value.config?.[key])) config[key] = value.config[key].filter(x => typeof x === 'string').slice(0, 80);
    if (value.config?.focus && typeof value.config.focus === 'object' && !Array.isArray(value.config.focus))
        config.focus = Object.fromEntries(Object.entries(value.config.focus).filter(([k,v]) => /^[a-z_]+$/.test(k) && Number.isInteger(v) && v >= 0 && v <= 2));
    const blocks = Array.isArray(value.blocks) && value.blocks.length >= 1 && value.blocks.length <= 8
        && value.blocks.every(b => b && phases.has(b.phase) && ['string','number'].includes(typeof b.weeks) && String(b.weeks).length <= 16)
        ? value.blocks.map(b => ({ phase: b.phase, weeks: b.weeks })) : null;
    return { v: 1, signature, config, blocks,
        step: Number.isInteger(value.step) && value.step >= 0 && value.step < 16 ? value.step : 0,
        templateId: typeof value.templateId === 'string' ? value.templateId.slice(0, 50) : '',
        name: typeof value.name === 'string' ? value.name.slice(0, 60) : '',
        adapt: typeof value.adapt === 'boolean' ? value.adapt : false,
        cycleMode: value.cycleMode === true, choseMode: value.choseMode === true };
}
function readAll(storage) {
    try {
        storage = storage || globalThis.localStorage;
        const raw = storage.getItem(CYCLE_SETUP_DRAFT_KEY);
        if (!raw || raw.length > 250000) return {};
        const data = JSON.parse(raw);
        return data && typeof data === 'object' && !Array.isArray(data) ? data : {};
    } catch { return {}; }
}
export function readCycleSetupDraft(id, signature, storage) {
    return normalizeCycleSetupDraft(readAll(storage)[id], signature);
}
export function writeCycleSetupDraft(id, signature, draft, storage) {
    try {
        storage = storage || globalThis.localStorage;
        const data = readAll(storage);
        delete data[id];
        data[id] = normalizeCycleSetupDraft({ ...draft, signature, v: 1 }, signature);
        storage.setItem(CYCLE_SETUP_DRAFT_KEY, JSON.stringify(Object.fromEntries(Object.entries(data).slice(-20))));
        return true;
    } catch { return false; }
}
export function clearCycleSetupDraft(id, storage) {
    try { storage = storage || globalThis.localStorage; const data = readAll(storage); delete data[id]; storage.setItem(CYCLE_SETUP_DRAFT_KEY, JSON.stringify(data)); } catch {}
}
