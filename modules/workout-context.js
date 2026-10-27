import { plateauSessions, exerciseTrends, plateauOf } from './engine-api.js';

// UI/session operations only. Prescription and progression remain engine-owned.
export function workoutPlateau(program, day, exerciseId, history) {
    const entries = plateauSessions(history, exerciseId, day.id, 8, program);
    const trend = exerciseTrends(entries).find(t => t.id === exerciseId);
    return plateauOf(trend);
}

// An explicit, session-only load reduction, never a second progression policy. Completed work,
// warmups already performed, manually entered values and prescribed percentage stages are protected.
export function lighterPendingWorkout(row, loadAtOrBelow, protectedSet) {
    if (!row || row.injuryAdjusted) return row;
    let changed = false;
    const sets = row.sets.map(set => {
        if (set.done || set.sub || protectedSet(set) || !(Number(set.weight) > 0)) return set;
        const next = loadAtOrBelow(Number(set.weight) * .9);
        if (!(next > 0 && next < Number(set.weight))) return set;
        if (!set.warm) changed = true;
        const weight = String(next);
        return { ...set, weight, auto: false, valueOwner: 'user', suggested: undefined,
            hint: undefined, autoTuned: false, target: { ...set.target, w: weight } };
    });
    if (!changed) return row;
    // A lighter day does not leave pending failure-technique extensions attached.
    return { ...row, sets: sets.filter(set => !set.sub || set.done) };
}
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

// The short timer between technique extensions, shared by the game plan and logger.
export function workoutTechniqueRest(set, restScale = 1) {
    return Math.max(8, Math.round((set.kind === 'drop' ? 10 : 15) * (restScale || 1)));
}

// A logged working set's load carries to the later working sets of the same lift that have no load yet
// (a first-time lift, a quick workout), so the lifter isn't retyping it. Unlogging takes the carried
// load back off, unless the lifter has changed it since.
export function carryLoggedLoad(sets, si) {
    const w = String(sets?.[si]?.weight ?? '').trim();
    if (!(parseFloat(w) > 0)) return sets;
    return sets.map((s, j) => (j <= si || !s || s.done || s.warm || s.sub || String(s.weight ?? '').trim() !== '' ? s : { ...s, weight: w, carriedFrom: si }));
}
export function uncarryLoggedLoad(sets, si) {
    const w = String(sets?.[si]?.weight ?? '').trim();
    return sets.map((s) => {
        if (!s || s.done || s.carriedFrom !== si) return s;
        const { carriedFrom, ...rest } = s;
        return String(s.weight ?? '').trim() === w ? { ...rest, weight: '' } : rest;
    });
}
