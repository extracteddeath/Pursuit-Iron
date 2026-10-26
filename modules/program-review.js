import { computeCell, weeksOf, EX_BY_ID, PROG_STYLES, estimateMinutesFor, trainingMaxForUnit } from './engine-api.js';

// Reviews read the same week cells as Plan and Workout. The generated/repair result is retained
// and applied verbatim; pressing Apply never rolls the generator a second time.
const clone = value => JSON.parse(JSON.stringify(value));
export const programReviewKey = program => JSON.stringify(program);
const text = value => Array.isArray(value) ? value.join('–') : String(value ?? '');
const cellText = (program, day, slot, week) => {
    const cell = computeCell(program, day, day.exercises[slot], slot, week);
    if (!cell || cell.missing) return 'Prescription unavailable';
    const style = cell.progressionStyle || program.progStyle?.[day.exercises[slot]];
    return `${cell.sets} × ${text(cell.reps)} · ${text(cell.rir)} RIR`
        + (cell.rest != null ? ` · ${cell.rest}s rest` : '')
        + (cell.tech ? ` · ${cell.tech}` : '')
        + (style ? ` · ${PROG_STYLES[style] || style}` : '')
        + (program.ss?.[`${day.id}:${slot}`] && !program.config?.noSupersets ? ' · superset' : '')
        + (program.rounds?.[`${day.id}:${slot}`] ? ` · ${program.rounds[`${day.id}:${slot}`]} rounds` : '');
};
const runs = values => {
    const groups = [];
    values.forEach((value, index) => {
        const last = groups[groups.length - 1];
        if (last?.value === value) last.end = index + 1;
        else groups.push({ start: index + 1, end: index + 1, value });
    });
    return groups.map(g => `W${g.start}${g.end !== g.start ? '–' + g.end : ''}: ${g.value}`);
};
const settings = [
    ['goal', 'Goal', v => ({ strength: 'Strength', hypertrophy: 'Muscle', both: 'Both' })[v] || v],
    ['session', 'Session length', v => ({ s20: 'Up to 20 min', s40: '20–40 min', s60: '40–60 min', s90: '60–90 min', s120: '90–120 min', s150: 'Over 120 min' })[v] || v],
    ['progression', 'Progression', v => v === 'manual' ? 'Authored targets' : 'Automatic'],
    ['barbellCap', 'Barbell limit', v => v == null ? 'No limit' : String(v)],
    ['noBodyweight', 'Bodyweight exercises', v => v ? 'Excluded' : 'Allowed'],
    ['noSupersets', 'Supersets', v => v ? 'Disabled' : 'Allowed'],
    ['volumeApproach', 'Volume approach', v => v === 'minimalist' ? 'Lower volume' : 'Standard'],
    ['assistance', 'Assistance', v => v || 'Default']
];
export function reviewProgramChanges(before, after) {
    const config = settings.flatMap(([key, label, format]) => {
        const a = format(before.config?.[key]), b = format(after.config?.[key]);
        return a !== b ? [{ label, before: a, after: b }] : [];
    });
    if (weeksOf(before) !== weeksOf(after)) config.push({ label: 'Block length', before: `${weeksOf(before)} weeks`, after: `${weeksOf(after)} weeks` });
    const beforeUnit = before.trainingMaxUnit || before.config?.unit || 'lb';
    const afterUnit = after.trainingMaxUnit || after.config?.unit || 'lb';
    for (const id of new Set([...Object.keys(before.trainingMax || {}), ...Object.keys(after.trainingMax || {})])) {
        const a = trainingMaxForUnit(before, id, beforeUnit) || 0;
        const b = trainingMaxForUnit(after, id, afterUnit) || 0;
        if (a === b && (a === 0 || beforeUnit === afterUnit)) continue;
        config.push({ label: `${EX_BY_ID[id]?.name || id} training max`,
            before: a > 0 ? `${a} ${beforeUnit}` : 'Not set', after: b > 0 ? `${b} ${afterUnit}` : 'Not set' });
    }
    for (const [key, label] of [['folder', 'Folder'], ['description', 'Description']]) {
        if ((before[key] || '') !== (after[key] || '')) config.push({ label, before: before[key] || 'None', after: after[key] || 'None' });
    }
    if (JSON.stringify(before.schedule || {}) !== JSON.stringify(after.schedule || {})) {
        const schedule = p => Object.entries(p.schedule || {}).map(([weekday, id]) =>
            `${['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][Number(weekday)] || weekday}: ${p.days.find(d => d.id === id)?.label || id}`).join(' · ') || 'Rotation';
        if (schedule(before) !== schedule(after)) config.push({ label: 'Schedule', before: schedule(before), after: schedule(after) });
    }
    const usedDays = new Set(), retainedIds = new Set((after.days || []).map(d => d.id));
    const days = (after.days || []).map((day, index) => {
        const oldIndex = before.days.findIndex(d => d.id === day.id);
        const fallback = before.days[index];
        const prev = oldIndex >= 0 ? before.days[oldIndex]
            : (!usedDays.has(index) && fallback && !retainedIds.has(fallback.id) ? fallback : null);
        if (prev) usedDays.add(before.days.indexOf(prev));
        const used = new Set(), rows = [];
        day.exercises.forEach((id, slot) => {
            const oldSlot = prev?.exercises.findIndex((other, i) => other === id && !used.has(i)) ?? -1;
            if (oldSlot >= 0) used.add(oldSlot);
            const a = oldSlot >= 0 ? Array.from({ length: weeksOf(before) }, (_, i) => cellText(before, prev, oldSlot, i + 1)) : [];
            const b = Array.from({ length: weeksOf(after) }, (_, i) => cellText(after, day, slot, i + 1));
            if (oldSlot !== slot || JSON.stringify(a) !== JSON.stringify(b)) rows.push({ id, name: EX_BY_ID[id]?.name || id,
                kind: oldSlot < 0 ? 'Added' : oldSlot !== slot ? 'Moved / updated' : 'Updated', before: a.length ? runs(a) : ['Not in this day'], after: runs(b) });
        });
        (prev?.exercises || []).forEach((id, slot) => {
            if (!used.has(slot)) rows.push({ id, name: EX_BY_ID[id]?.name || id, kind: 'Removed',
                before: runs(Array.from({ length: weeksOf(before) }, (_, i) => cellText(before, prev, slot, i + 1))), after: ['Removed from this day'] });
        });
        if (prev?.note !== day.note) rows.push({ id: 'note', name: 'Day note', kind: 'Updated', before: [prev?.note || 'None'], after: [day.note || 'None'] });
        return { id: day.id, label: day.label, rows, beforeMinutes: prev ? estimateMinutesFor(before, prev, 1) : 0, afterMinutes: estimateMinutesFor(after, day, 1) };
    });
    before.days.forEach((day, index) => {
        if (!usedDays.has(index)) days.push({ id: day.id, label: day.label, rows: [{ id: 'day', name: day.label, kind: 'Day removed',
            before: day.exercises.map(id => EX_BY_ID[id]?.name || id), after: ['Removed from the schedule'] }], beforeMinutes: estimateMinutesFor(before, day, 1), afterMinutes: 0 });
    });
    return { config, days: days.filter(d => d.rows.length || d.beforeMinutes !== d.afterMinutes), count: days.reduce((n, d) => n + d.rows.length, 0) + config.length };
}

// Exact, bounded snapshots include overrides, settings, schedule and generated prescriptions.
// A seed alone cannot restore an edited plan, a changed goal, or an older engine's result.
export function withProgramRevision(before, after, label, now = Date.now()) {
    if (!before || before.id !== after?.id) throw new Error('A program revision must keep its identity.');
    const snapshot = clone(before);
    delete snapshot.layoutHistory;
    const entries = [{ at: now, label, program: snapshot }, ...(before.layoutHistory || [])];
    const history = [];
    let bytes = 0;
    for (const entry of entries.slice(0, 3)) {
        const size = JSON.stringify(entry).length;
        if (bytes + size > 1500000) break;
        history.push(clone(entry)); bytes += size;
    }
    if (!history.length) throw new Error('This plan is too large to keep an undo copy. Export it before rebuilding.');
    return { ...clone(after), layoutHistory: history };
}
