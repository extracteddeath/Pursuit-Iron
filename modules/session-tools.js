import { sessionPlanChange } from './session-plan.js';
import { completedHistorySets, convertHistoryLoad, normalizeHistoryRevisions } from './next-engine/history-contract.js';
import { sessionSuggestion, prescribeSets, loadableAtOrBelow, resolveStyle } from './training-domain/prescriptions.js';
import { cellRepRange } from './training-domain/records.js';
import { e1rm, E1RM_REP_CAP } from './training-domain/programs.js';
import { sessionPrescriptionRows, isAssistedEx, exRecords } from './training-domain/analytics.js';
import { techniqueProtocolFromCell } from './next-engine/workout-runtime.js';
import { EX_BY_ID } from './training-domain/catalog.js';
import { programWorkingWeeks } from './program-duration.js';
import { buildUserAddedSlotPrescriptions } from './next-engine/workout-runtime.js';
import { markUserPrescriptionOverride, nextExerciseIdForShellExercise } from './next-engine/app-shell-adapter.js';

// Session choices are independent from program edits until the completed workout is saved.
export function withProgressionChoices(program, choices = {}) {
    const progStyle = { ...program.progStyle }, progStyleLifter = { ...program.progStyleLifter };
    for (const [id, style] of Object.entries(choices)) {
        if (!['auto','double','dynamic','ladder','linear','wave','e1rm'].includes(style)) continue;
        if (style === 'auto') { delete progStyle[id]; delete progStyleLifter[id]; }
        else { progStyle[id] = style; progStyleLifter[id] = true; }
    }
    return { ...program, progStyle, progStyleLifter };
}
export function repeatedNote(text, repeat) {
    return text && (repeat === 'once' || repeat === 'always')
        ? { text, repeat: repeat === 'once' ? 'none' : 'always' }
        : { text: '', repeat: 'none' };
}
export function refreshProgressionSets(current, prescribed, owns) {
    let workIndex = 0;
    const work = prescribed.filter(s => !s.warm && !s.sub);
    return current.map(set => {
        if (set.warm || set.sub) return set;
        const fresh = work[workIndex++];
        if (!fresh || set.done || owns(set) || set.added || set.typeOverride) return set;
        return { ...set, weight: fresh.weight, reps: fresh.reps, target: fresh.target,
            hint: fresh.hint, suggested: fresh.suggested, autoTuned: false };
    });
}
export function exerciseSessionTags(ex, row, day, cell) {
    const role = row.added ? 'Added lift' : row.slot === day.primaryIndex || cell?.role === 'primary'
        ? 'Key lift' : ex.type === 'compound' ? 'Supporting lift' : 'Accessory lift';
    return [role, ex.type === 'compound' ? 'Compound' : 'Isolation',
        ...(row.injuryAdjusted ? ['Injury adjusted'] : [])];
}


// Proposed program edits describe the intended working-set count, not how many sets were logged.
export function workoutProgramChanges(data, day, cellFor) {
    return (data || []).flatMap(row => {
        if (!row || row.injuryAdjusted || !EX_BY_ID[row.id]) return [];
        const sets = row.sets.filter(set => !set.warm && !set.sub).length;
        if (sets < 1 || sets > 20) return [];
        const added = !!row.added && !day.exercises.includes(row.id);
        if (!added && (!row.setCountEdited || day.sessionLengthPlan?.changed)) return [];
        const before = added ? 0 : Number(cellFor(row)?.sets);
        return added || Number.isFinite(before) && sets !== before
            ? [{ exerciseId: row.id, sets, added, previousSets: before }] : [];
    });
}

// Append by stable exercise ID and set explicit user-owned counts. Saved workout history is never an input.
export function withWorkoutProgramChanges(program, dayId, changes = []) {
    if (!changes.length) return program;
    const day = program?.days?.find(day => day.id === dayId);
    if (!day) throw new Error('This workout day is no longer in the program.');
    const exercises = [...day.exercises], overrides = { ...program.overrides };
    const nextWeekPrescriptions = { ...program.nextWeekPrescriptions };
    for (const change of changes) {
        const ex = EX_BY_ID[change.exerciseId], sets = Number(change.sets);
        if (!ex || !Number.isInteger(sets) || sets < 1 || sets > 20) throw new Error('Choose 1–20 working sets for each exercise.');
        let slot = exercises.indexOf(ex.id);
        if (slot < 0) {
            if (!change.added) throw new Error('An exercise changed in this program. Review the program before applying these sets.');
            slot = exercises.length; exercises.push(ex.id);
            const key = `${dayId}:${slot}`, nextId = nextExerciseIdForShellExercise(ex);
            overrides[key] = { added: true, nextEngine: true, legacyExerciseId: ex.id,
                ...(nextId ? { nextExerciseId: nextId } : {}) };
            nextWeekPrescriptions[key] = buildUserAddedSlotPrescriptions({ repRange: ex.rep,
                weeks: programWorkingWeeks(program), compound: ex.type === 'compound' });
        }
        const key = `${dayId}:${slot}`;
        overrides[key] = markUserPrescriptionOverride(overrides[key], 'sets', sets);
    }
    return { ...program, days: program.days.map(row => row.id === dayId ? { ...row, exercises, primaryIndex: row.primaryIndex ?? 0 } : row),
        overrides, nextWeekPrescriptions, edited: true };
}

// Home, Preview and the change review are projections of the actual Workout prescription.
export function buildSessionReview(program, day, weekIndex, unit, history = [], perf = {}) {
    const records = normalizeHistoryRevisions(history).entries;
    const exercises = sessionPrescriptionRows(program, day, weekIndex).flatMap(({ id, ex, slot, cell }) => {
        const range = cellRepRange(cell, program, ex, slot === day.primaryIndex);
        const proposedSuggestion = sessionSuggestion(program, day, slot, perf, unit, weekIndex, history, false);
        const suggestion = sessionSuggestion(program, day, slot, perf, unit, weekIndex, history);
        const proposed = prescribeSets(program, day, ex, slot, weekIndex, unit, proposedSuggestion, perf, perf, history, false).filter(set => !set.warm && !set.sub);
        const effective = prescribeSets(program, day, ex, slot, weekIndex, unit, suggestion, perf, perf, history, false).filter(set => !set.warm && !set.sub);
        const change = sessionPlanChange({ program, day, ex, slot, weekIndex, unit, cell, range,
            suggestion: proposedSuggestion, history, snapLoad: value => loadableAtOrBelow(ex, value, unit) });
        // Records use the same actual-load/actual-reps e1RM as Progress. Target RIR is not extra reps.
        const observed = records.flatMap(entry => completedHistorySets(entry.perf?.[id]).map(set => ({
            weight: convertHistoryLoad(set.w, entry.unit, unit), reps: Number(set.r) })))
            .filter(set => set.weight > 0 && set.reps > 0);
        const record = exRecords(records, id, unit).maxE1rm;
        const best = record ? Math.round(convertHistoryLoad(record.v, record.unit, unit) * 100) / 100 : null;
        let opportunity = null;
        if (!isAssistedEx(ex) && best > 0 && observed.length && !program.quick && !/deload/i.test(String(cell.note || ''))) {
            for (const set of effective) {
                const weight = Number(set.weight), reps = Number(set.reps);
                if (!(weight > 0 && reps > 0)) continue;
                const estimated = reps <= E1RM_REP_CAP ? e1rm(weight, reps) : null;
                if (estimated > best + .01 && (!opportunity || opportunity.type !== 'e1rm' || estimated > opportunity.estimated))
                    opportunity = { type: 'e1rm', weight, reps, estimated, best };
                const sameLoad = observed.filter(row => Math.abs(row.weight - weight) < .001);
                const bestReps = sameLoad.length ? Math.max(...sameLoad.map(row => row.reps)) : null;
                if (!opportunity && bestReps != null && reps > bestReps)
                    opportunity = { type: 'reps', weight, reps, bestReps };
            }
        }
        const proposedReason = proposed.some(set => set.target?.nextAction === 'percentage')
            ? 'This lift follows the saved percentage program and training max for this week. Its programmed weights and reps stay together.'
            : proposedSuggestion?.reason || `Choose a starting load for ${range[0]}–${range[1]} reps at the planned effort. This exposure will establish a reference for future progression.`;
        const reason = change.undone ? suggestion.reason : proposedReason;
        const action = proposedSuggestion?.action;
        const category = action === 'increase_load' || action === 'add_reps' ? 'Progression'
            : action === 'decrease_load' ? 'Load adjustment' : program.config?.percentScheme ? 'Programmed targets' : 'Rep target';
        return [{ id, name: ex.name, slot, cell, range, suggestion, proposed, effective, reason, proposedReason,
            style: resolveStyle(program, ex, slot === day.primaryIndex, weekIndex, perf, history, day.id),
            protocol: techniqueProtocolFromCell(cell), change, category, opportunity }];
    });
    return { programId: program.id, dayId: day.id, weekIndex, unit, sessionLengthPlan: day.sessionLengthPlan || null, exercises,
        changes: exercises.filter(row => row.change.changed),
        sourceDate: Math.max(0, ...exercises.map(row => Number(row.change.source?.date) || 0)) || null };
}
