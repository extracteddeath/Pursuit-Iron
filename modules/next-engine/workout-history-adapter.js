import { advanceCycleState, createInitialCycleState, startPhase } from './cycles.js';
import { evaluateWorkoutProgression } from './performance.js';
import { assessRecovery, recoverySignalForDecision } from './recovery.js';
import { diagnoseExerciseResponse } from './response.js';
import { EXERCISE_MAP } from './exercise-db.js';
import { estimate1RM } from './history.js';
import { availableLoadAtOrBelow } from './loading.js';
import { normalizeRequest } from './prescription.js';
import { transitionProgramPhase } from './phase-transition.js';
import { nextProgramToShellProgram, NextShellAdapterError } from './app-shell-adapter.js';
const clamp = (n, min, max) => Math.max(min, Math.min(max, n));
const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
function numberOf(value) {
    // Missing numeric fields must stay missing. Number(null) and Number('') are 0, which previously
    // turned an unreported RIR into a reported 0 RIR (failure) and distorted load calibration.
    if (value === null || value === undefined || (typeof value === 'string' && value.trim() === ''))
        return null;
    const n = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(n) ? n : null;
}
function intOf(value) {
    const n = numberOf(value);
    return n === null ? null : Math.round(n);
}
function rangeOf(value, fallback) {
    if (Array.isArray(value) && value.length >= 2) {
        const a = numberOf(value[0]), b = numberOf(value[1]);
        if (a !== null && b !== null)
            return [Math.min(a, b), Math.max(a, b)];
    }
    if (typeof value === 'number' && Number.isFinite(value))
        return [value, value];
    const raw = String(value ?? '').trim();
    if (!raw)
        return fallback;
    const parts = raw.split(/[-–]/).map(x => Number(x.trim())).filter(Number.isFinite);
    if (parts.length >= 2)
        return [Math.min(parts[0], parts[1]), Math.max(parts[0], parts[1])];
    if (parts.length === 1)
        return [parts[0], parts[0]];
    return fallback;
}
function convertLoad(value, fromUnit, toUnit) {
    if (value === null)
        return null;
    const from = String(fromUnit || toUnit || 'lb').toLowerCase();
    const to = String(toUnit || fromUnit || 'lb').toLowerCase();
    if (from === to)
        return value;
    if (from === 'kg' && to === 'lb')
        return Math.round(value * 2.2046226218 * 100) / 100;
    if (from === 'lb' && to === 'kg')
        return Math.round(value / 2.2046226218 * 100) / 100;
    return value;
}
function legacyExerciseById(legacyExercises) {
    return new Map(legacyExercises.map(ex => [ex.id, ex]));
}
function sourceSnapshot(program) {
    const request = program?.nextEngine?.request;
    const next = program?.nextEngine?.program;
    if (!request || !next || !Array.isArray(next.sessions))
        return null;
    const cycle = program?.nextEngine?.cycleState
        ?? createInitialCycleState(request.goal.type, request.schedule.days.length);
    /* Loads are counted for the setup the lifter is SHOWN (app-shell-adapter displayedLoadingMode); a lifter's own override wins. */
    const shown = program?.nextEngine?.displayLoadingModes;
    if (shown && Object.keys(shown).length) {
        const loading = request.equipment?.loading ?? {};
        const ov = { ...(loading.exerciseOverrides ?? {}) };
        for (const [id, mode] of Object.entries(shown))
            if (!ov[id]?.mode)
                ov[id] = { ...(ov[id] ?? {}), mode };
        return { request: { ...request, equipment: { ...request.equipment, loading: { ...loading, exerciseOverrides: ov } } }, program: next, cycleState: cycle };
    }
    return { request, program: next, cycleState: cycle };
}
function resolveDay(program, entry) {
    const days = Array.isArray(program?.days) ? program.days : [];
    let index = entry.dayId ? days.findIndex((d) => d?.id === entry.dayId) : -1;
    if (index < 0 && entry.dayLabel)
        index = days.findIndex((d) => d?.label === entry.dayLabel);
    if (index < 0)
        return null;
    return { day: days[index], dayIndex: index };
}
function resolveNextExerciseId(program, day, slot, legacyId, sourceSession, legacyExercises) {
    const meta = program?.overrides?.[`${day.id}:${slot}`];
    if (meta?.nextExerciseId && (!meta.legacyExerciseId || meta.legacyExerciseId === legacyId))
        return String(meta.nextExerciseId);
    const sourceAtSlot = sourceSession.exercises?.[slot];
    if (sourceAtSlot && meta?.legacyExerciseId === legacyId)
        return sourceAtSlot.exerciseId;
    const legacy = legacyExerciseById(legacyExercises).get(legacyId);
    const nameKey = norm(legacy?.name || '');
    const exact = sourceSession.exercises.find(ex => norm(ex.name) === nameKey);
    if (exact)
        return exact.exerciseId;
    // User swaps can point at a movement that did not exist in the original session. Search the full
    // immutable source program by name before dropping the exposure from adaptation evidence.
    const all = (program?.nextEngine?.program?.sessions ?? []).flatMap((s) => s.exercises ?? []);
    const global = all.find(ex => norm(ex.name) === nameKey);
    if (global)
        return global.exerciseId;
    const catalog = [...EXERCISE_MAP.values()].find(def => norm(def.name) === nameKey);
    return catalog?.id ?? null;
}
function plannedSessionForEntry(program, entry, legacyExercises) {
    const snap = sourceSnapshot(program);
    if (!snap)
        return null;
    const found = resolveDay(program, entry);
    if (!found)
        return null;
    const { day, dayIndex } = found;
    const sourceSession = snap.program.sessions[dayIndex];
    if (!sourceSession)
        return null;
    const legacyMap = legacyExerciseById(legacyExercises);
    const exercises = [];
    const legacyIds = [];
    const week = Math.max(1, Math.round(Number(entry.weekIndex) || 1));
    for (let slot = 0; slot < (day.exercises ?? []).length; slot++) {
        const legacyId = String(day.exercises[slot] ?? '');
        if (!legacyId)
            continue;
        const cell = program?.nextWeekPrescriptions?.[`${day.id}:${slot}`]?.[week]
            ?? program?.nextWeekPrescriptions?.[`${day.id}:${slot}`]?.[Math.max(...Object.keys(program?.nextWeekPrescriptions?.[`${day.id}:${slot}`] ?? {}).map(Number).filter(Number.isFinite), 1)];
        if (!cell)
            continue;
        const nextId = resolveNextExerciseId(program, day, slot, legacyId, sourceSession, legacyExercises);
        if (!nextId)
            continue;
        const sourceEx = sourceSession.exercises.find(ex => ex.exerciseId === nextId) ?? sourceSession.exercises[slot];
        const legacy = legacyMap.get(legacyId);
        const role = (cell.role ?? sourceEx?.role ?? 'hypertrophy_isolation');
        exercises.push({
            exerciseId: nextId,
            name: sourceEx?.name ?? legacy?.name ?? legacyId,
            role,
            sets: Math.max(1, Math.round(Number(cell.sets) || sourceEx?.sets || 1)),
            prescription: {
                reps: rangeOf(cell.reps, sourceEx?.prescription.reps ?? [8, 12]),
                rir: rangeOf(cell.rir, sourceEx?.prescription.rir ?? [1, 3]),
                restSeconds: Math.max(15, Math.round(Number(cell.rest) || sourceEx?.prescription.restSeconds || 90))
            },
            progression: String(sourceEx?.progression ?? cell.progressionStyle ?? 'auto'),
            progressionStyle: (cell.progressionStyle ?? sourceEx?.progressionStyle ?? 'auto'),
            advancedTechnique: sourceEx?.advancedTechnique,
            supersetGroup: sourceEx?.supersetGroup
        });
        legacyIds.push(legacyId);
    }
    if (!exercises.length)
        return null;
    return { session: { ...sourceSession, id: String(day.id), name: String(day.label || sourceSession.name), intent: String(day.focus || day.type || sourceSession.intent), exercises }, legacyIds };
}
function workoutFromEntry(program, entry, legacyExercises) {
    const planned = plannedSessionForEntry(program, entry, legacyExercises);
    if (!planned)
        return null;
    const found = resolveDay(program, entry);
    if (!found)
        return null;
    const targetUnit = String(program?.config?.unit || entry.unit || 'lb');
    const performed = [];
    const ignored = [];
    planned.legacyIds.forEach((legacyId, slot) => {
        const ex = planned.session.exercises[slot];
        const perf = entry.perf?.[legacyId];
        if (!ex || !perf?.sets?.length)
            return;
        let setIndex = 0;
        for (const raw of perf.sets) {
            if (raw?.sub)
                continue; // drop/myo extensions are not independent prescribed work sets
            const reps = intOf(raw?.r);
            if (reps === null || reps <= 0)
                continue;
            const load0 = numberOf(raw?.w);
            const rir0 = numberOf(raw?.rir);
            const targetRir = numberOf(raw?.tr);
            // v661 historically stores target RIR into `rir` when the user does not explicitly report
            // effort. Equal rir/tr values are therefore ambiguous. Treat only a value that differs from
            // the stored target (or has no target provenance) as observed effort; reps/completion remain
            // usable evidence either way. This prevents target effort from masquerading as athlete data.
            const observedRir = rir0 !== null && (targetRir === null || Math.abs(rir0 - targetRir) > .001) ? clamp(rir0, 0, 10) : null;
            performed.push({
                exerciseId: ex.exerciseId, setIndex: setIndex++, load: convertLoad(load0, entry.unit, targetUnit), reps,
                rir: observedRir, advancedTechnique: ex.advancedTechnique?.type
            });
        }
    });
    for (const legacyId of Object.keys(entry.perf ?? {}))
        if (!planned.legacyIds.includes(legacyId))
            ignored.push(legacyId);
    const source = sourceSnapshot(program);
    const progression = evaluateWorkoutProgression(planned.session, performed, { loadingInventory: source?.request.equipment.loading, equipmentAvailable: source?.request.equipment.available });
    return {
        historyId: String(entry.id ?? `${entry.date ?? 0}-${entry.dayId ?? entry.dayLabel ?? 'session'}`),
        completedAt: new Date(Number(entry.date) || 0).toISOString(),
        dayId: String(found.day.id), dayLabel: String(found.day.label || entry.dayLabel || planned.session.name),
        weekIndex: Math.max(1, Math.round(Number(entry.weekIndex) || 1)), session: planned.session, performedSets: performed, progression,
        ignoredLegacyExerciseIds: ignored
    };
}
function subjectiveUnderRecovery(entries) {
    return entries.slice(-5).filter(entry => Object.values(entry.feedbackRaw ?? {}).some(v => v?.sore === 'sore')).length;
}
function classify(recovery, positive, negative, diagnoses, workouts) {
    if (workouts < 2)
        return 'insufficient';
    const fatigue = diagnoses.filter(d => d.state === 'fatigue_limited' && d.confidence !== 'low').length;
    if (recovery.status === 'deload_recommended' || fatigue >= 2)
        return 'fatigue_limited';
    if (positive > negative * 1.5 && positive >= 2)
        return 'productive';
    return 'mixed';
}
function latestShellPerf(history, programId, legacyId) {
    const entries = [...(history ?? [])].filter(h => h?.programId === programId && h?.perf?.[legacyId]).sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
    return entries[0]?.perf?.[legacyId] ?? null;
}
function representativeShellLoad(perf) {
    if (!perf)
        return null;
    const direct = numberOf(perf.weight);
    if (direct !== null && direct > 0)
        return direct;
    const loads = (perf.sets ?? []).filter(s => !s?.sub).map(s => numberOf(s?.w)).filter((n) => n !== null && n > 0);
    return loads.length ? Math.max(...loads) : null;
}
export function deriveLongitudinalExerciseEvidence(diagnoses, latestDecisions = [], sourceExercises = []) {
    const successful = new Set();
    const protectedIds = new Set(sourceExercises.filter(ex => ex.role === 'primary_strength').map(ex => ex.exerciseId));
    const replaceExerciseIds = new Set();
    const techniqueLimitedExerciseIds = new Set();
    const fatigueLimitedExerciseIds = new Set();
    const diagnosisById = new Map((diagnoses ?? []).map(d => [d.exerciseId, d]));
    for (const diagnosis of diagnoses ?? []) {
        if (diagnosis.state === 'poor_fit') replaceExerciseIds.add(diagnosis.exerciseId);
        else if (diagnosis.state === 'technique_limited') { techniqueLimitedExerciseIds.add(diagnosis.exerciseId); protectedIds.add(diagnosis.exerciseId); }
        else if (diagnosis.state === 'fatigue_limited') { fatigueLimitedExerciseIds.add(diagnosis.exerciseId); protectedIds.add(diagnosis.exerciseId); }
        else if (['progressing', 'underloaded', 'possibly_understimulated'].includes(diagnosis.state) && diagnosis.confidence !== 'low') successful.add(diagnosis.exerciseId);
    }
    for (const decision of latestDecisions ?? []) {
        const diagnosis = diagnosisById.get(decision.exerciseId);
        if (recoverySignalForDecision(decision) > 0 && (!diagnosis || diagnosis.state === 'uncertain') && !replaceExerciseIds.has(decision.exerciseId))
            successful.add(decision.exerciseId);
    }
    for (const id of replaceExerciseIds) { successful.delete(id); protectedIds.delete(id); }
    return {
        successfulExerciseIds: [...successful], protectedExerciseIds: [...protectedIds], replaceExerciseIds: [...replaceExerciseIds],
        techniqueLimitedExerciseIds: [...techniqueLimitedExerciseIds], fatigueLimitedExerciseIds: [...fatigueLimitedExerciseIds]
    };
}

/** Runtime bridge only: asks Pursuit Engine's own performance evaluator what the next exposure should do. */
export function nextWorkoutSuggestionForShell(program, history, legacyExercises, day, slot, weekIndex) {
    if (program?.engineSource !== 'pursuit-next')
        return null;
    const snap = sourceSnapshot(program);
    if (!snap)
        return null;
    const dayIndex = (program?.days ?? []).findIndex((d) => d?.id === day?.id);
    if (dayIndex < 0)
        return null;
    const sourceSession = snap.program.sessions[dayIndex];
    if (!sourceSession)
        return null;
    const legacyId = String(day?.exercises?.[slot] ?? '');
    if (!legacyId)
        return null;
    const nextId = resolveNextExerciseId(program, day, slot, legacyId, sourceSession, legacyExercises);
    if (!nextId)
        return null;
    const cell = program?.nextWeekPrescriptions?.[`${day.id}:${slot}`]?.[weekIndex];
    const reps = String(cell?.reps ?? cell?.range ?? '');
    const last = latestShellPerf(history, String(program.id), legacyId);
    let analysis;
    try {
        analysis = analyzeShellHistoryForNextEngine(program, history, legacyExercises);
    }
    catch {
        return last ? { weight: representativeShellLoad(last), dir: 'hold', reason: 'Hold the last logged load until the new engine has comparable completed-set evidence.', reps, last, action: 'initial' } : null;
    }
    let decision;
    for (let i = analysis.workouts.length - 1; i >= 0 && !decision; i--) {
        const workout = analysis.workouts[i];
        if (workout.dayId !== String(day.id))
            continue;
        decision = workout.progression.find(d => d.exerciseId === nextId);
    }
    if (!decision)
        return last ? { weight: representativeShellLoad(last), dir: 'hold', reason: 'Hold the last logged load until the new engine has comparable completed-set evidence.', reps, last, action: 'initial' } : null;
    const current = decision.currentLoad ?? representativeShellLoad(last);
    /* ⚠ SUGGEST FOR THIS WEEK, NOT FOR THE WEEK THE LAST WORKOUT WAS LOGGED IN. `decision` was made when the last workout was
       analysed, against THAT workout's prescription; this function then only relabelled the rep range. Measured on a 6-week
       strength block: week 5 prescribes 1–3 reps, but the suggestion kept the week-1 load and "target 5". When this week's
       prescription (reps, reps in reserve, or progression style) differs from the one the last workout was trained under,
       re-set the load from the lifter's estimated max so the new range lands mid-range, rounded DOWN to a load they can make.
       Same prescription -> the engine's decision stands (that is where add-reps / add-load double progression lives). */
    const lastEntry = [...(history ?? [])].filter(h => h?.programId === String(program.id) && h?.perf?.[legacyId]).sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0))[0];
    const lastCell = program?.nextWeekPrescriptions?.[`${day.id}:${slot}`]?.[Number(lastEntry?.weekIndex) || 1];
    const changed = !!cell && !!lastCell && (String(cell.reps) !== String(lastCell.reps) || String(cell.rir) !== String(lastCell.rir) || cell.progressionStyle !== lastCell.progressionStyle);
    if (changed) {
        const shifted = represcribeForWeek(nextId, cell, lastCell, last, snap?.request);
        if (shifted)
            return { weight: shifted.weight, dir: current != null && shifted.weight > current ? 'up' : current != null && shifted.weight < current ? 'down' : 'hold',
                reason: shifted.reason, reps, target: shifted.target, last, action: 'represcribe', confidence: decision.confidence };
    }
    const weight = decision.suggestedLoad ?? current ?? null;
    return {
        weight,
        dir: decision.action === 'increase_load' ? 'up' : decision.action === 'decrease_load' ? 'down' : (decision.suggestedLoad != null && current != null && decision.suggestedLoad < current ? 'down' : 'hold'),
        reason: decision.reason,
        reps,
        target: decision.suggestedReps,
        last,
        action: decision.action,
        confidence: decision.confidence
    };
}
function pairOf(v) {
    const m = String(v ?? '').trim().match(/^(\d+(?:\.\d+)?)(?:\s*-\s*(\d+(?:\.\d+)?))?$/);
    if (!m)
        return null;
    const a = Number(m[1]), b = m[2] != null ? Number(m[2]) : a;
    return Number.isFinite(a) && Number.isFinite(b) && b >= a ? [a, b] : null;
}
/* Load for a changed prescription, from the lifter's estimated max. The estimate uses each logged set's reps-in-reserve; where
   none was logged it assumes the LOWER bound of the RIR prescribed for the week that set was trained in — the conservative end
   of what the lifter was told to do. (Assuming failure instead would underestimate the max and cancel out the heavier week.) */
function represcribeForWeek(exerciseId, cell, lastCell, last, request) {
    const reps = pairOf(cell?.reps ?? cell?.range), rir = pairOf(cell?.rir), lastRir = pairOf(lastCell?.rir);
    if (!reps || !last)
        return null;
    const assumedRir = lastRir ? lastRir[0] : null;
    let e1 = null;
    for (const set of (last.sets ?? [])) {
        if (set?.sub || set?.done === false)
            continue;
        const w = numberOf(set?.w), r = numberOf(set?.r), reported = numberOf(set?.rir);
        const est = estimate1RM(w, r ?? 0, reported ?? assumedRir);
        if (est !== null && (e1 === null || est > e1))
            e1 = est;
    }
    if (e1 === null)
        e1 = estimate1RM(numberOf(last.weight), numberOf(last.reps) ?? 0, assumedRir);
    if (e1 === null)
        return null;
    const repsMid = Math.round((reps[0] + reps[1]) / 2), rirMid = rir ? (rir[0] + rir[1]) / 2 : 2;
    const weight = availableLoadAtOrBelow(exerciseId, e1 / (1 + (repsMid + rirMid) / 30), request?.equipment?.loading, request?.equipment?.available);
    if (weight === null)
        return null;
    const range = reps[0] === reps[1] ? String(reps[0]) : `${reps[0]}–${reps[1]}`, reserve = rir ? (rir[0] === rir[1] ? String(rir[0]) : `${rir[0]}–${rir[1]}`) : null;
    return { weight, target: repsMid, reason: `This week's prescription changes to ${range} reps${reserve ? ` with ${reserve} in reserve` : ''}, so the load is re-set from your estimated max (about ${Math.round(e1)}) to land mid-range.` };
}
export function nextWorkoutSuggestionFromPerformedShell(program, legacyExercises, day, slot, weekIndex, perf, unit) {
    if (program?.engineSource !== 'pursuit-next')
        return null;
    const legacyId = String(day?.exercises?.[slot] ?? '');
    if (!legacyId)
        return null;
    const workout = workoutFromEntry(program, { programId: String(program.id), dayId: String(day.id), weekIndex, date: Date.now(), unit, perf }, legacyExercises);
    if (!workout)
        return null;
    const snap = sourceSnapshot(program);
    if (!snap)
        return null;
    const dayIndex = (program?.days ?? []).findIndex((d) => d?.id === day?.id);
    if (dayIndex < 0)
        return null;
    const sourceSession = snap.program.sessions[dayIndex];
    if (!sourceSession)
        return null;
    const nextId = resolveNextExerciseId(program, day, slot, legacyId, sourceSession, legacyExercises);
    if (!nextId)
        return null;
    const decision = workout.progression.find(d => d.exerciseId === nextId);
    if (!decision)
        return null;
    const last = perf?.[legacyId] ?? null;
    const cell = program?.nextWeekPrescriptions?.[`${day.id}:${slot}`]?.[weekIndex];
    return {
        weight: decision.suggestedLoad ?? decision.currentLoad ?? representativeShellLoad(last),
        dir: decision.action === 'increase_load' ? 'up' : decision.action === 'decrease_load' ? 'down' : (decision.suggestedLoad != null && decision.currentLoad != null && decision.suggestedLoad < decision.currentLoad ? 'down' : 'hold'),
        reason: decision.reason,
        reps: String(cell?.reps ?? cell?.range ?? ''),
        target: decision.suggestedReps,
        last,
        action: decision.action,
        confidence: decision.confidence
    };
}
export function analyzeShellHistoryForNextEngine(program, history, legacyExercises) {
    const snap = sourceSnapshot(program);
    if (!snap)
        throw new NextShellAdapterError('NEXT_HISTORY_SNAPSHOT_MISSING', 'This beta program predates the M41 engine snapshot required for history adaptation. Regenerate it once before adapting the next block.');
    const entries = [...(history ?? [])].filter(h => h?.programId === program.id).sort((a, b) => (Number(a.date) || 0) - (Number(b.date) || 0));
    const workouts = entries.map(entry => workoutFromEntry(program, entry, legacyExercises)).filter((x) => !!x);
    let cycleState = { ...snap.cycleState };
    // Fixed-length cycle blocks can be shorter than the open-ended four-week
    // review window. Apply this while reading history so existing saved cycles
    // benefit too; retain minimum exposure and the separate recovery exit rules.
    const plannedWeeks = Number(program?.nextEngine?.cycleTemplate?.weeks);
    if (program?.cycleId && cycleState.phase !== 'recovery' && Number.isInteger(plannedWeeks) && plannedWeeks > 0) {
        const plannedWorkouts = plannedWeeks * snap.program.sessions.length;
        cycleState.reviewAfterWorkouts = Math.max(cycleState.minimumWorkouts, Math.min(cycleState.reviewAfterWorkouts, plannedWorkouts));
    }
    const recent = [];
    const exposures = new Map();
    let recovery = { status: 'normal', confidence: 'low', evidenceCount: 0, rationale: 'Not enough completed workout evidence to assess recovery.' };
    for (const workout of workouts) {
        recent.push(workout.progression);
        if (recent.length > 5)
            recent.shift();
        recovery = assessRecovery(recent);
        cycleState = advanceCycleState(cycleState, workout.progression, false, recovery, snap.request.goal.type);
        for (const ex of workout.session.exercises) {
            const sets = workout.performedSets.filter(s => s.exerciseId === ex.exerciseId);
            const progression = workout.progression.find(d => d.exerciseId === ex.exerciseId);
            if (!sets.length)
                continue;
            const list = exposures.get(ex.exerciseId) ?? [];
            list.push({ completedAt: workout.completedAt, sets, progression });
            exposures.set(ex.exerciseId, list);
        }
    }
    const sourceExercises = snap.program.sessions.flatMap(s => s.exercises);
    const unique = new Map(sourceExercises.map(ex => [ex.exerciseId, ex]));
    const diagnoses = [...unique.values()].map(ex => diagnoseExerciseResponse(ex, exposures.get(ex.exerciseId) ?? [], recovery));
    let positive = 0, negative = 0;
    const latest = new Map();
    for (const workout of workouts)
        for (const decision of workout.progression) {
            latest.set(decision.exerciseId, decision);
            const signal = recoverySignalForDecision(decision);
            if (signal > 0) positive++;
            else if (signal < 0) negative++;
        }
    const evidence = deriveLongitudinalExerciseEvidence(diagnoses, [...latest.values()], sourceExercises);
    const successful = evidence.successfulExerciseIds;
    const protectedIds = evidence.protectedExerciseIds;
    const subjective = subjectiveUnderRecovery(entries);
    if (subjective >= 3 && recovery.status === 'normal')
        recovery = { ...recovery, status: 'watch', confidence: 'moderate', rationale: `${recovery.rationale} Subjective soreness also remained unresolved in ${subjective} of the last five logged sessions.` };
    const classification = classify(recovery, positive, negative, diagnoses, workouts.length);
    const ignoredIds = [...new Set(workouts.flatMap(w => w.ignoredLegacyExerciseIds))];
    return {
        workouts, workoutCount: workouts.length, performedSetCount: workouts.reduce((n, w) => n + w.performedSets.length, 0),
        ignoredSetCount: entries.reduce((n, e) => n + Object.entries(e.perf ?? {}).filter(([id]) => ignoredIds.includes(id)).reduce((m, [, p]) => m + (p.sets?.filter(s => !s.sub).length ?? 0), 0), 0),
        ignoredLegacyExerciseIds: ignoredIds, recovery, cycleState, classification,
        successfulExerciseIds: successful, protectedExerciseIds: [...new Set(protectedIds)], replaceExerciseIds: evidence.replaceExerciseIds,
        techniqueLimitedExerciseIds: evidence.techniqueLimitedExerciseIds, fatigueLimitedExerciseIds: evidence.fatigueLimitedExerciseIds, diagnoses,
        positiveDecisionCount: positive, negativeDecisionCount: negative, subjectiveUnderRecoverySignals: subjective,
        readyForNextBlock: !!cycleState.recommendedNextPhase, recommendedNextPhase: cycleState.recommendedNextPhase
    };
}
function requestAdaptedFromHistory(request, analysis) {
    const replace = new Set(analysis.replaceExerciseIds ?? []);
    let adapted = replace.size ? {
        ...request,
        preferences: { ...request.preferences, avoidedExercises: [...new Set([...(request.preferences?.avoidedExercises ?? []), ...replace])] }
    } : request;
    if (analysis.classification !== 'fatigue_limited' && analysis.recovery.status !== 'deload_recommended')
        return adapted;
    return {
        ...adapted,
        schedule: { days: adapted.schedule.days.map(day => ({
                ...day,
                targetExercises: day.targetExercises === undefined ? undefined : Math.max(2, day.targetExercises - Math.max(1, Math.ceil(day.targetExercises * .2)))
            })) }
    };
}
export function generateNextBlockFromShellHistory(options) {
    const current = options.program;
    if (current?.engineSource !== 'pursuit-next')
        throw new NextShellAdapterError('NOT_NEXT_ENGINE_PROGRAM', 'Next-block history adaptation is only available for Pursuit Next beta programs.');
    const snap = sourceSnapshot(current);
    if (!snap)
        throw new NextShellAdapterError('NEXT_HISTORY_SNAPSHOT_MISSING', 'This beta program does not contain the M41 source snapshot needed for safe adaptation.');
    const analysis = analyzeShellHistoryForNextEngine(current, options.history, options.legacyExercises);
    const phase = analysis.cycleState.recommendedNextPhase;
    if (!phase)
        throw new NextShellAdapterError('NEXT_BLOCK_NOT_READY', `The new engine needs more comparable completed workouts before changing phase. ${analysis.cycleState.rationale}`, {
            workouts: analysis.workoutCount, minimumWorkouts: analysis.cycleState.minimumWorkouts, reviewAfterWorkouts: analysis.cycleState.reviewAfterWorkouts, recovery: analysis.recovery
        });
    const baseRequest = current?.nextEngine?.baseRequest ?? snap.request;
    const adaptedRequest = requestAdaptedFromHistory(baseRequest, analysis);
    const normalized = normalizeRequest(adaptedRequest);
    const transitioned = transitionProgramPhase(snap.program, normalized, phase, {
        successfulExerciseIds: analysis.successfulExerciseIds,
        protectedExerciseIds: analysis.protectedExerciseIds,
        replaceExerciseIds: analysis.replaceExerciseIds
    });
    if (transitioned.program.audit.result !== 'pass')
        throw new NextShellAdapterError('NEXT_BLOCK_REJECTED', `Pursuit Engine ${transitioned.program.engineVersion} could not produce a safe adapted ${phase} block.`);
    const legacy = nextProgramToShellProgram(transitioned.program, current.config, options.legacyExercises, options.makeId);
    const nextCycle = startPhase(analysis.cycleState, phase, snap.request.goal.type, snap.request.schedule.days.length);
    legacy.nextEngine = {
        ...legacy.nextEngine, request: JSON.parse(JSON.stringify(adaptedRequest)), baseRequest: JSON.parse(JSON.stringify(baseRequest)), program: JSON.parse(JSON.stringify(transitioned.program)), cycleState: JSON.parse(JSON.stringify(nextCycle)), historySchemaVersion: 1,
        priorBlock: { programId: current.id, phase: snap.program.phase, workouts: analysis.workoutCount, classification: analysis.classification, recovery: analysis.recovery.status },
        continuity: transitioned.continuity,
        historySummary: { positive: analysis.positiveDecisionCount, negative: analysis.negativeDecisionCount, successfulExercises: analysis.successfulExerciseIds.length, ignoredLegacyExerciseIds: analysis.ignoredLegacyExerciseIds }
    };
    return { program: legacy, nextProgram: transitioned.program, request: adaptedRequest, analysis, continuity: transitioned.continuity };
}
