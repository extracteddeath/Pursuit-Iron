// UI/session operations only. Prescription and progression remain engine-owned.
export function activeSetContext(row) {
    const sets = row?.sets || [];
    const index = sets.findIndex(set => !set.done);
    const set = index >= 0 ? sets[index] : null;
    const work = sets.filter(set => !set.warm && !set.sub);
    const workIndex = set && !set.warm && !set.sub ? work.indexOf(set) : -1;
    const kind = !set ? 'complete' : set.warm ? 'warmup' : set.sub ? (set.kind === 'drop' ? 'drop' : 'myo') : 'work';
    return { set, index, workIndex, workCount: work.length, kind,
        finalWork: kind === 'work' && workIndex === work.length - 1 && !row.injuryAdjusted };
}

// A range is an allowed prescription, not a concrete progression suggestion.
// Prefer the engine's exact prefill; legacy/manual rows can retain their entered
// count. Never manufacture a new target from the bottom of a range.
export function dockRepSuggestion(set) {
    const exact = value => /^\d+$/.test(String(value ?? '').trim());
    for (const value of [set?.suggested?.reps, set?.target?.prefillReps, set?.target?.reps]) {
        if (exact(value)) return String(value).trim();
    }
    const protocol = String(set?.target?.reps ?? '');
    if (/amrap|failure/i.test(protocol)) return /amrap/i.test(protocol) ? 'AMRAP' : 'To failure';
    return exact(set?.reps) ? String(set.reps).trim() : '—';
}

export function removePendingWorkingSet(row) {
    const work = row.sets.filter(set => !set.warm && !set.sub);
    if (work.length <= 1) return row;
    const pending = work.filter(set => !set.done);
    const last = pending.at(-1);
    if (!last) return row;
    const index = row.sets.indexOf(last);
    // Extensions after the removed activation set cannot remain pending alone.
    // Logged work and extensions are immutable evidence.
    return { ...row, sets: row.sets.filter((set, i) => set !== last &&
        !(i > index && set.sub && !set.done)) };
}

export function adjustInjuredExercises(data, selectedIds, part, action) {
    const selected = new Set(selectedIds);
    return data.flatMap(row => {
        if (!selected.has(row.id)) return [row];
        const logged = row.sets.filter(set => set.done);
        if (action === 'skip')
            return logged.length ? [{ ...row, sets: logged, injuryAdjusted: part }] : [];
        // An injury reduction never preserves pending failure-technique extensions.
        // Keep completed work, including completed warm-ups/extensions, in its order.
        const pending = row.sets.filter(set => !set.done && !set.warm && !set.sub);
        const keep = Math.ceil(pending.length / 2);
        let count = 0;
        return [{ ...row, injuryAdjusted: part, sets: row.sets.filter(set =>
            set.done || (!set.warm && !set.sub && count++ < keep)) }];
    });
}


// Set-count edits are session-local. Logged work and warmups are never erased.
export function resizeWorkingSets(row, requested) {
    if (!row || !Array.isArray(row.sets)) return row;
    const work = row.sets.filter(set => !set.warm && !set.sub);
    const minimum = Math.max(1, work.filter(set => set.done).length);
    const count = Number(requested);
    if (!Number.isInteger(count) || count < minimum || count > 20 || count === work.length) return row;
    let next = row;
    while (next.sets.filter(set => !set.warm && !set.sub).length > count) {
        const reduced = removePendingWorkingSet(next);
        if (reduced === next) break;
        next = reduced;
    }
    const seed = work.at(-1) || {};
    const missing = count - next.sets.filter(set => !set.warm && !set.sub).length;
    if (missing > 0) next = { ...next, sets: [...next.sets, ...Array.from({ length: missing }, () => ({
        weight: seed.weight ?? '', reps: seed.reps ?? '', warm: false, done: false,
        auto: false, valueOwner: 'user', added: true,
        ...(seed.target ? { target: { ...seed.target } } : {})
    }))] };
    return next;
}
