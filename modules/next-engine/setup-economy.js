const MACHINE_SPECIFIC = new Set([
    'leg_press', 'legpress', 'hacksquat', 'legext', 'legcurl', 'pecdeck', 'machinerow', 'assisted', 'calfmachine',
    'preacher', 'machinepress', 'machineshoulder', 'machinelatraise', 'machinecurl', 'machineext', 'beltsquat',
    'hipthrustmachine', 'machinecrunch', 'machineshrug', 'machinepullover', 'abduction', 'adduction', 'reversehyper', 'ghd', 'kickback'
]);
const normalized = (setup) => [...new Set(setup)].sort();
const descriptorCache = new WeakMap();
const transitionCache = new WeakMap();
function broadZone(setup, def) {
    const s = new Set(setup);
    if (s.has('cable'))
        return 'cable';
    if (s.has('smith'))
        return 'smith';
    if (s.has('dumbbell'))
        return 'dumbbell';
    if (s.has('barbell') || s.has('ezbar') || s.has('safetybar') || s.has('trapbar'))
        return 'freeweight_bar';
    if (s.has('landmine'))
        return 'landmine';
    if (s.has('kettlebell'))
        return 'kettlebell';
    if (s.has('bands'))
        return 'bands';
    for (const token of setup)
        if (MACHINE_SPECIFIC.has(token))
            return `machine:${token}`;
    // A generic machine token does not imply the same physical station across unrelated movements.
    // Keep it movement-specific so the optimizer never pretends a chest press and leg curl share a setup.
    if (s.has('machine'))
        return `machine:${def.movementFamily}`;
    if (s.has('pullup') || s.has('pullup_bar'))
        return 'pullup_station';
    if (s.has('dip'))
        return 'dip_station';
    if (s.has('bodyweight'))
        return 'bodyweight';
    if (s.has('bench') || s.has('inclinebench') || s.has('declinebench'))
        return 'bench';
    return setup[0] ?? 'other';
}
export function exerciseSetupDescriptors(def) {
    if (!def)
        return [];
    const cached = descriptorCache.get(def);
    if (cached)
        return cached;
    const setups = [def.equipment, ...(def.equipmentAlternatives ?? [])];
    const seen = new Set();
    const out = [];
    for (const raw of setups) {
        const equipment = normalized(raw);
        const zone = broadZone(equipment, def);
        const signature = equipment.join('+') || zone;
        const key = `${zone}|${signature}`;
        if (seen.has(key))
            continue;
        seen.add(key);
        out.push({ zone, signature, equipment });
    }
    descriptorCache.set(def, out);
    return out;
}
function sharedEquipment(a, b) {
    const broad = new Set(['machine', 'bodyweight']);
    const set = new Set(a.filter(x => !broad.has(x)));
    return b.some(x => !broad.has(x) && set.has(x));
}
/**
 * Practical station-change cost between two exercises. This is deliberately relative rather than
 * minutes: the generator's time budget remains unchanged, while ordering can prefer fewer attachment,
 * bench, rack, or station changes inside otherwise-equivalent sequencing choices.
 */
export function setupTransitionCost(a, b) {
    if (!a || !b)
        return 1;
    const cached = transitionCache.get(a)?.get(b);
    if (cached !== undefined)
        return cached;
    const aa = exerciseSetupDescriptors(a), bb = exerciseSetupDescriptors(b);
    let best = Infinity;
    for (const x of aa)
        for (const y of bb) {
            let cost;
            if (x.signature === y.signature && x.zone === y.zone)
                cost = 0;
            else if (x.zone === y.zone)
                cost = .18;
            else if (sharedEquipment(x.equipment, y.equipment))
                cost = .42;
            else
                cost = 1 + Math.min(5, b.setupCost) * .08;
            if (cost < best)
                best = cost;
        }
    const result = Number.isFinite(best) ? best : 1;
    let row = transitionCache.get(a);
    if (!row) {
        row = new WeakMap();
        transitionCache.set(a, row);
    }
    row.set(b, result);
    return result;
}
export function sessionSetupTransitionScore(exercises, exerciseMap) {
    let total = 0;
    for (let i = 1; i < exercises.length; i++)
        total += setupTransitionCost(exerciseMap.get(exercises[i - 1].exerciseId), exerciseMap.get(exercises[i].exerciseId));
    return Math.round(total * 1000) / 1000;
}
/**
 * Counts station revisits in a sequence (A -> B -> A). This is a diagnostic only; some revisits are
 * protected by strength/compound ordering, so milestone gates should apply it to reorderable bands.
 */
export function setupZoneRevisits(exercises, exerciseMap) {
    const zones = exercises.map(ex => exerciseSetupDescriptors(exerciseMap.get(ex.exerciseId))[0]?.zone ?? 'other');
    const seen = new Set();
    let prior, revisits = 0;
    for (const zone of zones) {
        if (zone === prior)
            continue;
        if (seen.has(zone))
            revisits++;
        seen.add(zone);
        prior = zone;
    }
    return revisits;
}
