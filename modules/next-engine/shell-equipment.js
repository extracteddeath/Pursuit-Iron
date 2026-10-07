// Keep equipment expansion behind one shared registration seam so every shell
// caller uses the same inventory rules without importing UI-owned catalog code.
let expander = null;

export function setShellEquipmentExpander(fn) { expander = fn; }

export function shellExercisePerformableFor(config) {
    if (!expander || !Array.isArray(config?.equipment))
        return () => true;
    const have = new Set(expander(config.equipment));
    const banned = new Set(Array.isArray(config?.banned) ? config.banned : []);
    return ex => {
        const equip = Array.isArray(ex?.equip) ? ex.equip : [];
        if (banned.has(ex?.id)) return false;
        if (!equip.length) return !config?.noBodyweight;
        return equip.every(q => have.has(q));
    };
}
