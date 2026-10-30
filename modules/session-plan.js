import { exerciseAt, computeCell } from './training-domain/records.js';
import { completedHistorySets, convertHistoryLoad, normalizeHistoryEntries, resolveHistoryDayIndex } from './next-engine/history-contract.js';

// Keep the review about an actual adjustment, not ordinary double-progression rep gains.
export function classifySessionAdjustment({ previous = [], targets = [], suggestion = null, setDelta = null, unit = 'lb' }) {
    const diff = targets.flatMap((target, index) => {
        const old = previous[index], before = Number(old?.weight), after = Number(target?.weight);
        return old?.weight != null && target?.weight != null && Number.isFinite(before) && Number.isFinite(after)
            && before > 0 && after > 0 && Math.abs(before - after) > .001
            ? [{ before, after, index }] : [];
    });
    const plateau = /plateau|stall|stagnat|reset|back.?off/i.test(
        [suggestion?.action, suggestion?.reason, suggestion?.override?.kind].filter(Boolean).join(' '));
    const kind = setDelta ? 'sets' : diff.length ? (plateau ? 'plateau' : 'load') : null;
    const up = diff.length > 0 && diff.every(d => d.after > d.before);
    const down = diff.length > 0 && diff.every(d => d.after < d.before);
    const category = setDelta ? 'Sets updated' : kind === 'plateau' ? 'Plateau response'
        : up ? 'Load increased' : down ? 'Load reduced' : 'Load adjusted';
    const format = n => String(Math.round(n * 100) / 100);
    const loadSummary = diff.length ? `${format(diff[0].before)} → ${format(diff[0].after)} ${unit}`
        + (diff.length > 1 ? ` · ${diff.length} sets` : '') : '';
    const summary = setDelta ? `${setDelta.before} → ${setDelta.after} planned sets`
        + (loadSummary ? ` · ${loadSummary}` : '') : loadSummary;
    return { changed: !!kind, loadChanged: diff.length > 0, kind, category, summary };
}
// Only a saved, recent, explicit revision establishes a planned-set change.
// Completed set counts are not a valid baseline: missed sets are not a program edit.
function plannedSetRevision(program, day, ex, slot, weekIndex, cell) {
    const revision = program.layoutHistory?.[0], at = Number(revision?.at);
    if (!Number.isFinite(at) || at <= 0 || Date.now() - at < 0 || Date.now() - at > 21 * 86400000) return null;
    const before = revision.program, priorDay = before?.days?.find(item => item.id === day.id);
    if (before?.id !== program.id || !priorDay || slot >= (priorDay.exercises?.length || 0)) return null;
    try {
        if (exerciseAt(before, priorDay, slot, weekIndex) !== ex.id) return null;
        const priorSets = Number(computeCell(before, priorDay, ex.id, slot, weekIndex)?.sets);
        const currentSets = Number(cell?.sets);
        return Number.isInteger(priorSets) && Number.isInteger(currentSets) && priorSets > 0 && currentSets > 0
            && priorSets !== currentSets ? { before: priorSets, after: currentSets } : null;
    } catch { return null; }
}
// An override is specific to one exposure and its exact recommendation. Merely
// syncing the plan does not consume a choice; changed evidence invalidates it.
export function sessionPlanChoiceKey(day, slot, id) { return `${day.id}:${slot}:${id}`; }
export function sessionPlanChange({ program, day, ex, slot, weekIndex, unit, cell, range, suggestion, history, snapLoad }) {
    const index = program.days.findIndex(row => row.id === day.id);
    const source = normalizeHistoryEntries(history, program.id).entries.slice().reverse().find(entry =>
        index >= 0 && resolveHistoryDayIndex(program.days, entry) === index && completedHistorySets(entry.perf?.[ex.id]).length);
    // The review comparison is positional, just like LAST and the evaluator.
    // A skipped middle set must not present set three as the previous set two.
    const observed = completedHistorySets(source?.perf?.[ex.id]);
    const hasPositions = observed.some(set => Number.isInteger(set.workingSetIndex) && set.workingSetIndex >= 0);
    const previous = Array.from({ length: Number(cell.sets) || 0 }, (_, index) => {
        const set = hasPositions ? observed.find(set => set.workingSetIndex === index) : observed[index];
        return set ? { weight: convertHistoryLoad(set.w, source.unit, unit), reps: Number(set.r) } : null;
    });
    const targets = Array.from({ length: Number(cell.sets) || 0 }, (_, i) => ({
        weight: suggestion?.setTargets?.[i]?.weight ?? suggestion?.weight ?? null,
        reps: suggestion?.setTargets?.[i]?.reps ?? suggestion?.target ?? range[0]
    }));
    // Undo may retain a previously used load, but never rolls back newly earned reps.
    // Extra planned sets remain prescribed, and equipment inventory remains authoritative.
    const undoTargets = targets.map((target, i) => {
        const prior = previous[i];
        if (!(prior?.weight > 0) || !(prior.reps >= range[0] && prior.reps <= range[1])) return target;
        const weight = snapLoad(prior.weight);
        return Number.isFinite(weight) && Math.abs(weight - prior.weight) < .001
            ? { ...target, weight: prior.weight } : target;
    });
    const eligible = !!source && exerciseAt(program, day, slot, weekIndex) === ex.id && !program.quick && !suggestion?.referenceOnly && suggestion?.action !== 'initial'
        && !(program.config?.percentScheme && (slot === day.primaryIndex || slot === day.t2Index)) && !/deload/i.test(String(cell.note || ''))
        && !(weekIndex > Number(program.weeks ?? program.config?.weeks ?? Infinity));
    const setDelta = plannedSetRevision(program, day, ex, slot, weekIndex, cell);
    const classified = classifySessionAdjustment({ previous, targets, suggestion, setDelta, unit });
    const changed = !!setDelta || eligible && classified.loadChanged;
    const canUndo = eligible && classified.loadChanged && undoTargets.some((target, i) =>
        target.weight != null && targets[i]?.weight != null &&
        Math.abs(Number(target.weight) - Number(targets[i].weight)) > .001);
    const key = JSON.stringify([program.id, day.id, slot, ex.id, weekIndex, unit,
        source?.id, source?.date, source?.perf?.[ex.id], program.progStyle?.[ex.id], cell, targets, undoTargets, suggestion?.action], (_, value) => value && typeof value === 'object' && !Array.isArray(value)
            ? Object.fromEntries(Object.keys(value).sort().map(key => [key, value[key]])) : value);
    const choiceSlot = sessionPlanChoiceKey(day, slot, ex.id);
    const undone = canUndo && program.sessionPlanChoices?.[choiceSlot]?.key === key;
    return { ...classified, changed, canUndo, undone, choiceSlot, key, source, previous, targets, undoTargets, setDelta };
}
export function withSessionPlanChoice(program, change, undo) {
    const choices = { ...program.sessionPlanChoices };
    if (undo) {
        if (!change.canUndo) throw new Error('This recommendation has changed. Review it again.');
        choices[change.choiceSlot] = { key: change.key, at: Date.now() };
    } else delete choices[change.choiceSlot];
    return { ...program, sessionPlanChoices: choices, updatedAt: Date.now() };
}
