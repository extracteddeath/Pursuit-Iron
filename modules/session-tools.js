import { sessionPlanChange } from './session-plan.js';
import { completedHistorySets, convertHistoryLoad, normalizeHistoryRevisions } from './next-engine/history-contract.js';
import { sessionSuggestion, prescribeSets, loadableAtOrBelow, resolveStyle } from './training-domain/prescriptions.js';
import { cellRepRange, exerciseAt, computeCell } from './training-domain/records.js';
import { e1rm, E1RM_REP_CAP } from './training-domain/programs.js';
import { sessionPrescriptionRows, isAssistedEx, exRecords } from './training-domain/analytics.js';
import { techniqueProtocolFromCell } from './next-engine/workout-runtime.js';
import { EX_BY_ID } from './training-domain/catalog.js';
import { programWorkingWeeks } from './program-duration.js';
import { buildUserAddedSlotPrescriptions } from './next-engine/workout-runtime.js';
import { markUserPrescriptionOverride, nextExerciseIdForShellExercise, shellPrescriptionFieldOwner } from './next-engine/app-shell-adapter.js';

const SESSION_PROGRESSION_STYLES = new Set(['auto','double','dynamic','ladder','linear','wave','e1rm']);

// Session choices are independent from program edits until the completed workout is saved.
export function withProgressionChoices(program, choices = {}) {
    const progStyle = { ...program.progStyle }, progStyleLifter = { ...program.progStyleLifter };
    for (const [id, style] of Object.entries(choices)) {
        if (!SESSION_PROGRESSION_STYLES.has(style)) continue;
        if (style === 'auto') { delete progStyle[id]; delete progStyleLifter[id]; }
        else { progStyle[id] = style; progStyleLifter[id] = true; }
    }
    return { ...program, progStyle, progStyleLifter };
}
// Retain the source of a session choice; timestamps and unrelated edits do not
// establish a conflict. Progression is exercise-wide, including paired slots.
export function workoutProgressionOrigin(program, exerciseId) {
    const slots = (program.days || []).flatMap(day => (day.exercises || []).flatMap((id, slot) => {
        const pair = program.pairs?.[`${day.id}:${slot}`] ?? null;
        return id === exerciseId || pair === exerciseId ? [[String(day.id), slot, id, pair]] : [];
    })).sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : a[1] - b[1]);
    return { schema: 1, programId: program.id, style: program.progStyle?.[exerciseId] ?? null,
        lifter: program.progStyleLifter?.[exerciseId] ?? null,
        defaultStyle: program.config?.progression ?? null, scheme: program.config?.percentScheme ?? null,
        engineSource: program.engineSource ?? null, slots };
}
const progressionOriginKey = value => JSON.stringify([value.schema, value.programId, value.style,
    value.lifter, value.defaultStyle, value.scheme, value.engineSource, value.slots]);

// One apply boundary is shared by saved programs, drafts and the active view.
// Validate everything before the caller persists any part of the review.
export function withWorkoutProgramReview(program, review) {
    if (!program || program.id !== review.programId)
        throw new Error('This program is no longer the one reviewed. Keep these changes for the session only.');
    for (const [id, style] of Object.entries(review.progressionChanges || {})) {
        if (!EX_BY_ID[id] || !SESSION_PROGRESSION_STYLES.has(style))
            throw new Error('This progression choice is unavailable. Keep it for the session only.');
        const current = workoutProgressionOrigin(program, id), origin = review.progressionOrigins?.[id];
        const added = review.programExerciseChanges?.some(change => change.exerciseId === id && change.added);
        if (!current.slots.length && !added)
            throw new Error('This lift is no longer in the program. Keep its progression change for the session only.');
        if (current.scheme)
            throw new Error('This program now uses percentage stages. Review its progression in the program editor.');
        const alreadyApplied = style === 'auto' ? current.style == null && current.lifter == null
            : current.style === style && current.lifter === true;
        if (!alreadyApplied && (!origin || progressionOriginKey(current) !== progressionOriginKey(origin)))
            throw new Error(origin
                ? 'This lift’s progression or workout slots changed after your choice. Keep this session only, then review the program.'
                : 'This older review has no original progression to compare. Keep it for the session only and change the program in its editor.');
    }
    const changes = review.programExerciseChanges || [];
    if (changes.some(change => !change.added && change.slot == null)
        && (!Number.isInteger(review.weekIndex) || review.weekIndex < 1))
        throw new Error('This older set-count review has no workout week to compare. Keep it for the session only and use the program editor.');
    return withProgressionChoices(withWorkoutProgramChanges(program, review.dayId,
        changes, review.weekIndex), review.progressionChanges || {});
}

// Slot recommendations belong only to the exercise prescribed for this week.
// Ad-hoc swaps/additions start without a borrowed load, including after a
// program-scope swap has already changed the saved roster.
export function workoutRowMatchesPrescription(program, day, row, weekIndex) {
    return !!row && !row.added && !row.exerciseSubstituted
        && Number.isInteger(row.slot) && row.slot >= 0 && row.slot < day.exercises.length
        && row.id === exerciseAt(program, day, row.slot, weekIndex);
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
export function workoutProgramChanges(data, day, cellFor, program = null, weekIndex = 1) {
    return (data || []).flatMap(row => {
        if (!row || row.injuryAdjusted || !EX_BY_ID[row.id]) return [];
        const sets = row.sets.filter(set => !set.warm && !set.sub).length;
        if (sets < 1 || sets > 20) return [];
        const added = !!row.added && !day.exercises.includes(row.id);
        if (!added && (!row.setCountEdited || day.sessionLengthPlan?.changed)) return [];
        // A session-only substitution cannot change the authored slot's dose.
        if (!added && program && (!Number.isInteger(row.slot) || row.slot < 0
            || row.slot >= day.exercises.length || row.id !== exerciseAt(program, day, row.slot, weekIndex))) return [];
        const before = added ? 0 : Number(cellFor(row)?.sets);
        return added || Number.isFinite(before) && sets !== before
            ? [{ exerciseId: row.id, sets, added, previousSets: before,
                ...(!added && program ? { slot: row.slot, weekIndex,
                    slotExerciseIds: [day.exercises[row.slot], program.pairs?.[`${day.id}:${row.slot}`]].filter(Boolean) } : {}) }] : [];
    });
}

// Append by stable exercise ID and set explicit user-owned counts. Saved workout history is never an input.
export function withWorkoutProgramChanges(program, dayId, changes = [], reviewWeekIndex = null) {
    if (!changes.length) return program;
    const day = program?.days?.find(day => day.id === dayId);
    if (!day) throw new Error('This workout day is no longer in the program.');
    const exercises = [...day.exercises], overrides = { ...program.overrides };
    const nextWeekPrescriptions = { ...program.nextWeekPrescriptions };
    for (const change of changes) {
        const ex = EX_BY_ID[change.exerciseId], sets = Number(change.sets);
        if (!ex || !Number.isInteger(sets) || sets < 1 || sets > 20) throw new Error('Choose 1–20 working sets for each exercise.');
        let slot;
        if (change.slot != null) {
            slot = change.slot;
            const ids = [exercises[slot], program.pairs?.[`${dayId}:${slot}`]].filter(Boolean);
            if (!Number.isInteger(slot) || slot < 0 || slot >= exercises.length
                || !Number.isInteger(change.weekIndex) || change.weekIndex < 1
                || exerciseAt(program, day, slot, change.weekIndex) !== ex.id
                || JSON.stringify(ids) !== JSON.stringify(change.slotExerciseIds))
                throw new Error('This workout slot changed after the session. Keep the session changes or review the program.');

        } else {
            // Older pending reviews did not save slot identity. Resolve only an
            // unambiguous base/alternate occurrence; never guess between duplicates.
            const matches = exercises.flatMap((id, index) =>
                id === ex.id || program.pairs?.[`${dayId}:${index}`] === ex.id ? [index] : []);
            if (matches.length > 1) throw new Error('This exercise has more than one workout slot. Review the program before applying these sets.');
            slot = matches[0] ?? -1;
        }
        if (change.added && slot >= 0) {
            const existing = overrides[`${dayId}:${slot}`];
            if (Number(existing?.sets) !== sets || shellPrescriptionFieldOwner(program, existing, 'sets') !== 'user')
                throw new Error('This lift was already added or edited in the program. Keep this session only, then review the program.');
        }
        const sourceWeek = change.weekIndex ?? reviewWeekIndex;
        if (!change.added && slot >= 0 && Number.isInteger(sourceWeek) && sourceWeek > 0) {
            if (exerciseAt(program, day, slot, sourceWeek) !== ex.id)
                throw new Error('This workout slot changed after the session. Keep the session changes or review the program.');
            const currentSets = Number(computeCell(program, day, ex.id, slot, sourceWeek)?.sets);
            if (currentSets !== Number(change.previousSets) && currentSets !== sets)
                throw new Error('This workout slot’s set count changed after the session. Keep this session only, then review the program.');
        }
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

// Program-scope workout swaps are one reversible transaction spanning the open
// program and its saved cycle siblings. Nothing in React owns a second roster.
function swapWorkoutDay(p, dayId, fromId, toId) {
    const day = p?.days?.find(d => d.id === dayId);
    const slot = day?.exercises?.indexOf(fromId) ?? -1;
    if (slot < 0 || fromId === toId || day.exercises.includes(toId)) return null;
    return { slot, before: p, after: {
        ...p, days: p.days.map(d => d.id === dayId ? { ...d,
            exercises: d.exercises.map((id, i) => i === slot ? toId : id) } : d),
        edited: true
    } };
}
export function beginWorkoutProgramSwap(program, saved = [], dayId, fromId, toId) {
    const active = swapWorkoutDay(program, dayId, fromId, toId);
    if (!active) return null;
    const changed = [];
    const nextSaved = saved.map(p => {
        if (p.id === program.id) {
            changed.push({ id: p.id, before: p, after: active.after, slot: active.slot });
            return active.after;
        }
        if (!program.cycleId || p.cycleId !== program.cycleId) return p;
        const next = swapWorkoutDay(p, dayId, fromId, toId);
        if (!next) return p;
        changed.push({ id: p.id, ...next });
        return next.after;
    });
    return { program: active.after, saved: nextSaved,
        receipt: { dayId, fromId, toId, active: { ...active, id: program.id }, saved: changed } };
}
export function undoWorkoutProgramSwap(program, saved = [], receipt) {
    if (!receipt || program?.id !== receipt.active?.id) return null;
    const { dayId, fromId, toId } = receipt;
    const rosterValid = (current, slot) => {
        const day = current?.days?.find(d => d.id === dayId);
        return day?.exercises?.[slot] === toId && !day.exercises.includes(fromId);
    };
    // Refuse the entire undo if any affected block changed underneath it.
    if (!rosterValid(program, receipt.active.slot)
        || receipt.saved.some(item => !rosterValid(saved.find(p => p.id === item.id), item.slot)))
        return null;
    const restore = (current, entry) => {
        // A later unrelated edit must survive Undo; restore only the swapped slot.
        // Saved records receive an updatedAt stamp even when this was their
        // sole mutation. Treat that stamp as equivalent to the applied version,
        // so Undo can also restore the previous edited state.
        const withoutStamp = value => ({ ...value, updatedAt: null });
        if (current === entry.after || JSON.stringify(withoutStamp(current)) === JSON.stringify(withoutStamp(entry.after)))
            return entry.before;
        return { ...current, days: current.days.map(d => d.id === dayId ? {
            ...d, exercises: d.exercises.map((id, i) => i === entry.slot ? fromId : id)
        } : d) };
    };
    return { program: restore(program, receipt.active),
        saved: saved.map(p => {
            const change = receipt.saved.find(item => item.id === p.id);
            return change ? restore(p, change) : p;
        }) };
}
