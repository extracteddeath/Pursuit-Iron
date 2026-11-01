// Explicit per-exercise deload choices. Prescriptions and completion share this
// owner; merely opening, skipping or syncing a workout never consumes a choice.
export function oneShotDeloadFactor(program, exerciseId) {
    const choices = program?.deloadNext;
    if (!choices || !Object.hasOwn(choices, exerciseId) || /^assisted-/.test(exerciseId)) return null;
    const factor = choices[exerciseId];
    return typeof factor === 'number' && Number.isFinite(factor) && factor > 0 && factor < 1 ? factor : null;
}

export function oneShotDeloadLoad(load, factor, snapLoad) {
    if (!(Number(load) > 0) || typeof factor !== 'number' || !(factor > 0 && factor < 1)) return load;
    // Respect the existing equipment minimum rather than inventing a fractional
    // plate or an unavailable lighter implement. Never raise an existing load.
    return Math.min(Number(load), snapLoad(Number(load) * factor));
}

export function consumeOneShotDeload(program, performance, completedAt) {
    const trained = Object.keys(performance || {}).filter(id => oneShotDeloadFactor(program, id) !== null);
    if (!trained.length) return program;
    const updatedAt = Math.max(completedAt, (program.updatedAt || 0) + 1);
    if (!Number.isSafeInteger(updatedAt) || updatedAt < 1)
        throw new TypeError('Invalid deload completion revision');
    const remaining = { ...program.deloadNext };
    trained.forEach(id => delete remaining[id]);
    return { ...program, deloadNext: Object.keys(remaining).length ? remaining : undefined, updatedAt };
}
