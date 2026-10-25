import { exerciseAt } from './training-domain/records.js';
import { completedHistorySets, convertHistoryLoad, normalizeHistoryEntries, resolveHistoryDayIndex } from './next-engine/history-contract.js';

// A declined recommendation belongs to one exposure and one exact recommendation. It is
// invalid after changed performance, new evidence, equipment/range changes or a different week.
// Sync revision timestamps alone do not change the recommendation or consume the choice.
export function sessionPlanChoiceKey(day, slot, id) { return `${day.id}:${slot}:${id}`; }
export function sessionPlanChange({ program, day, ex, slot, weekIndex, unit, cell, range, suggestion, history, snapLoad }) {
    const index = program.days.findIndex(row => row.id === day.id);
    const source = normalizeHistoryEntries(history, program.id).entries.slice().reverse().find(entry =>
        index >= 0 && resolveHistoryDayIndex(program.days, entry) === index && completedHistorySets(entry.perf?.[ex.id]).length);
    const previous = completedHistorySets(source?.perf?.[ex.id]).map(set => ({
        weight: convertHistoryLoad(set.w, source.unit, unit), reps: Number(set.r) }));
    const targets = Array.from({ length: Number(cell.sets) || 0 }, (_, i) => ({
        weight: suggestion?.setTargets?.[i]?.weight ?? suggestion?.weight ?? null,
        reps: suggestion?.setTargets?.[i]?.reps ?? suggestion?.target ?? range[0]
    }));
    const undoTargets = targets.map((target, i) => {
        const prior = previous[i];
        if (!(prior?.weight > 0) || !(prior.reps >= range[0] && prior.reps <= range[1])) return target;
        const weight = snapLoad(prior.weight);
        // Undo means the actual previous load/reps, never a fabricated loading-grid approximation.
        return Number.isFinite(weight) && Math.abs(weight - prior.weight) < .001 ? { ...prior } : target;
    });
    const different = (a, b) => a && b && (Math.abs(Number(a.weight) - Number(b.weight)) > .001 || Number(a.reps) !== Number(b.reps));
    const changed = targets.some((target, i) => previous[i]?.weight > 0 && target.weight > 0 && different(target, previous[i]));
    const eligible = !!source && exerciseAt(program, day, slot, weekIndex) === ex.id && !program.quick && !suggestion?.referenceOnly && suggestion?.action !== 'initial'
        && !(program.config?.percentScheme && (slot === day.primaryIndex || slot === day.t2Index)) && !/deload/i.test(String(cell.note || ''))
        && !(weekIndex > Number(program.weeks ?? program.config?.weeks ?? Infinity));
    const canUndo = changed && eligible && undoTargets.some((target, i) => different(target, targets[i]));
    const key = JSON.stringify([program.id, day.id, slot, ex.id, weekIndex, unit,
        source?.id, source?.date, source?.perf?.[ex.id], program.progStyle?.[ex.id], cell, targets, undoTargets, suggestion?.action], (_, value) => value && typeof value === 'object' && !Array.isArray(value)
            ? Object.fromEntries(Object.keys(value).sort().map(key => [key, value[key]])) : value);
    const choiceSlot = sessionPlanChoiceKey(day, slot, ex.id);
    const undone = canUndo && program.sessionPlanChoices?.[choiceSlot]?.key === key;
    return { changed, canUndo, undone, choiceSlot, key, source, previous, targets, undoTargets };
}

export function withSessionPlanChoice(program, change, undo) {
    const choices = { ...program.sessionPlanChoices };
    if (undo) {
        if (!change.canUndo) throw new Error('This recommendation has changed. Review it again.');
        choices[change.choiceSlot] = { key: change.key, at: Date.now() };
    } else delete choices[change.choiceSlot];
    return { ...program, sessionPlanChoices: choices, updatedAt: Date.now() };
}
