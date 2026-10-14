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
