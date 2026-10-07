// M230 canonical math stage. Stage context is local to one realization.


export /* M230:PRESERVE:top.addToLedger:BEGIN */
function addToLedger(ledger, ex, sets) {
    for (const [m, c] of Object.entries(ex.muscles)) {
        ledger.fractional[m] = (ledger.fractional[m] ?? 0) + c.credit * sets;
        if (c.credit >= 1)
            ledger.direct[m] = (ledger.direct[m] ?? 0) + sets;
    }
}
/* M230:PRESERVE:top.addToLedger:END */

export /* M230:PRESERVE:top.integerSetPlan:BEGIN */
function integerSetPlan(entries, desiredTotal) {
    const result = new Map();
    if (!entries.length || desiredTotal <= 0)
        return result;
    const weights = entries.map(entry => Math.max(0, entry.allocation.dose));
    const weightSum = weights.reduce((a, b) => a + b, 0) || entries.length;
    const raw = entries.map((entry, index) => ({ entry, value: desiredTotal * (weights[index] || 1) / weightSum }));
    let assigned = 0;
    for (const item of raw) {
        const base = Math.min(4, Math.floor(item.value));
        result.set(item.entry, base);
        assigned += base;
    }
    const ranked = [...raw].sort((a, b) => (b.value - Math.floor(b.value)) - (a.value - Math.floor(a.value)) || a.entry.allocation.id.localeCompare(b.entry.allocation.id));
    let cursor = 0;
    while (assigned < desiredTotal && ranked.length) {
        const item = ranked[cursor % ranked.length];
        const current = result.get(item.entry) ?? 0;
        if (current < 4) {
            result.set(item.entry, current + 1);
            assigned++;
        }
        cursor++;
        if (cursor > ranked.length * 6)
            break;
    }
    return result;
}
/* M230:PRESERVE:top.integerSetPlan:END */

export /* M230:PRESERVE:top.desiredDirect:BEGIN */
function desiredDirect(muscle, target, directTargetDose) {
    const modeled = directTargetDose[muscle];
    if (modeled !== undefined)
        return modeled;
    if (muscle === 'side_delts' || muscle === 'rear_delts' || muscle === 'calves')
        return Math.min(6, Math.max(3, Math.round(target * .7)));
    if (muscle === 'biceps' || muscle === 'triceps')
        return Math.min(5, Math.max(2, Math.round(target * .5)));
    if (muscle === 'core')
        return Math.min(4, Math.max(2, Math.round(target * .5)));
    return 0;
}
/* M230:PRESERVE:top.desiredDirect:END */
