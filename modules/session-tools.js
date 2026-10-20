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
