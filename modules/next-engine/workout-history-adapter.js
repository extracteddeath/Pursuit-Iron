import { historyNumber as numberOf, convertHistoryLoad as convertLoad, observedHistoryRIR, completedHistorySets, historyExposureContext, progressionExposureContext, normalizeHistoryEntries, validHistoryDate, historyLoadReason } from './history-contract.js';
import { advanceCycleState, createInitialCycleState, startPhase } from './cycles.js';
import { evaluateWorkoutProgression } from './performance.js';
import { assessRecovery, recoverySignalForDecision } from './recovery.js';
import { diagnoseExerciseResponse } from './response.js';
import { deriveAthleteResponse, requestWithAthleteResponse } from './athlete-response.js';
import { EXERCISE_MAP } from './exercise-db.js';
import { estimate1RM } from './history.js';
import { availableLoadAtOrBelow } from './loading.js';
import { normalizeRequest } from './prescription.js';
import { transitionProgramPhase } from './phase-transition.js';
import { nextProgramToShellProgram, getNextShellCell, resolveNextShellExerciseId, NextShellAdapterError } from './app-shell-adapter.js';
import { advancedTechniqueFromCell } from './workout-runtime.js';
import { finalizeGeneratedShellVolume, captureShellBaseProgram } from './volume-repair.js';
const clamp = (n, min, max) => Math.max(min, Math.min(max, n));
const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
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
function resolveNextExerciseId(program, day, slot, sourceSession, legacyMap) {
    const legacy = legacyMap.get(String(day.exercises[slot]));
    const current = resolveNextShellExerciseId(program, day, slot, legacy);
    if (current) return current;
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
function historicalShellCell(program, day, slot, week, perf, exerciseId) {
    const current = getNextShellCell(program, day, slot, week);
    const saved = perf?.prescription;
    if (!current || saved?.schemaVersion !== 1 || saved.exerciseId !== exerciseId) return current;
    const reps = rangeOf(saved.reps, null), rir = rangeOf(saved.rir, null);
    if (!Number.isInteger(saved.sets) || saved.sets < 1 || saved.sets > 20
        || !reps || reps[0] <= 0 || !rir || rir[0] < 0 || rir[1] > 10
        || saved.rest == null || saved.rest === '' || !Number.isFinite(Number(saved.rest)) || Number(saved.rest) < 0)
        return current;
    const textRange = pair => pair[0] === pair[1] ? String(pair[0]) : pair.join('-');
    return { ...current, sets: saved.sets, reps: textRange(reps), rir: textRange(rir), rest: Number(saved.rest),
        role: saved.role ?? current.role, progressionStyle: saved.progressionStyle ?? current.progressionStyle,
        tech: saved.tech ?? null,
        setTargets: Array.isArray(saved.setTargets) && saved.setTargets.length === saved.sets
            && saved.setTargets.every(t => numberOf(t.reps) > 0 && numberOf(t.weight) >= 0)
            ? saved.setTargets : undefined };
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
        const nextId = resolveNextExerciseId(program, day, slot, sourceSession, legacyMap);
        if (!nextId)
            continue;
        const fallbackWeek = Math.max(...Object.keys(program?.nextWeekPrescriptions?.[`${day.id}:${slot}`] ?? {}).map(Number).filter(Number.isFinite), 1);
        const cell = historicalShellCell(program, day, slot, week, entry.perf?.[legacyId], nextId)
            ?? historicalShellCell(program, day, slot, fallbackWeek, entry.perf?.[legacyId], nextId);
        if (!cell)
            continue;
        const sourceEx = sourceSession.exercises.find(ex => ex.exerciseId === nextId) ?? sourceSession.exercises[slot];
        const legacy = legacyMap.get(legacyId);
        const role = (cell.role ?? sourceEx?.role ?? 'hypertrophy_isolation');
        exercises.push({
            exerciseId: nextId,
            name: sourceEx?.exerciseId === nextId ? sourceEx.name : legacy?.name ?? legacyId,
            role,
            sets: Math.max(1, Math.round(Number(cell.sets) || sourceEx?.sets || 1)),
            prescription: {
                reps: rangeOf(cell.reps, sourceEx?.prescription.reps ?? [8, 12]),
                rir: rangeOf(cell.rir, sourceEx?.prescription.rir ?? [1, 3]),
                ...(cell.setTargets ? { setTargets: cell.setTargets.map(t => ({ ...t, weight: convertLoad(t.weight, t.unit || entry.unit, program.config?.unit) })) } : {}),
                restSeconds: cell.rest != null && cell.rest !== '' && Number.isFinite(Number(cell.rest)) && Number(cell.rest) >= 0
                    ? Math.round(Number(cell.rest)) : sourceEx?.prescription.restSeconds ?? 90
            },
            progression: String(sourceEx?.progression ?? cell.progressionStyle ?? 'auto'),
            progressionStyle: (cell.progressionStyle ?? sourceEx?.progressionStyle ?? 'auto'),
            advancedTechnique: advancedTechniqueFromCell(cell, sourceEx?.advancedTechnique)
        });
        legacyIds.push(legacyId);
    }
    if (!exercises.length)
        return null;
    return { session: { ...sourceSession, id: String(day.id), name: String(day.label || sourceSession.name), intent: String(day.focus || day.type || sourceSession.intent), exercises }, legacyIds };
}
function workoutFromEntry(program, entry, legacyExercises) {
    if (!validHistoryDate(entry)) return null;
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
        for (const raw of completedHistorySets(perf)) {
            const reps = intOf(raw.r), load0 = numberOf(raw.w);
            const observedRir = observedHistoryRIR(raw);
            const exposure = progressionExposureContext([perf, raw]);
            performed.push({
                ...historyExposureContext(raw), exerciseId: ex.exerciseId, setIndex: setIndex++, load: convertLoad(load0, entry.unit, targetUnit), reps,
                badDay: exposure.badDay, interrupted: exposure.interrupted, prescriptionEdited: exposure.nonComparable,
                rir: observedRir, painFlag: raw.painFlag === true, techniqueQuality: raw.techniqueQuality,
                advancedTechnique: ex.advancedTechnique?.type
            });
        }
    });
    for (const legacyId of Object.keys(entry.perf ?? {}))
        if (!planned.legacyIds.includes(legacyId))
            ignored.push(legacyId);
    const source = sourceSnapshot(program);
    const progression = evaluateWorkoutProgression(planned.session, performed, { ...historyExposureContext(entry), loadingInventory: source?.request.equipment.loading, equipmentAvailable: source?.request.equipment.available });
    return {
        programId: String(program.id), unit: targetUnit,
        ...historyExposureContext(entry),
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
function latestShellEntry(history, programId, legacyId, dayId) {
    const entries = [...(history ?? [])].filter(h => h?.programId === programId && validHistoryDate(h) && completedHistorySets(h?.perf?.[legacyId]).length).sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
    return entries.find(h => h.dayId === dayId) ?? entries[0] ?? null;
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
    const nextId = resolveNextExerciseId(program, day, slot, sourceSession, legacyExerciseById(legacyExercises));
    if (!nextId)
        return null;
    const cell = getNextShellCell(program, day, slot, weekIndex);
    const reps = String(cell?.reps ?? cell?.range ?? '');
    const lastEntry = latestShellEntry(history, String(program.id), legacyId, String(day.id));
    const rawLast = lastEntry?.perf?.[legacyId] ?? null;
    const last = rawLast;
    const lastLoad = convertLoad(representativeShellLoad(last), lastEntry?.unit, program.config?.unit);
    let analysis;
    try {
        analysis = analyzeShellHistoryForNextEngine(program, history, legacyExercises);
    }
    catch {
        return last ? { weight: lastLoad, dir: 'hold', reason: 'Hold the last logged load until the new engine has comparable completed-set evidence.', reps, last, lastUnit: lastEntry?.unit || program.config?.unit, action: 'initial' } : null;
    }
    let decision;
    for (let i = analysis.workouts.length - 1; i >= 0 && !decision; i--) {
        const workout = analysis.workouts[i];
        if (workout.dayId !== String(day.id))
            continue;
        decision = workout.progression.find(d => d.exerciseId === nextId);
    }
    if (!decision)
        return last ? { weight: lastLoad, dir: 'hold', reason: 'Hold the last logged load until the new engine has comparable completed-set evidence.', reps, last, lastUnit: lastEntry?.unit || program.config?.unit, action: 'initial' } : null;
    const current = decision.currentLoad ?? lastLoad;
    /* ⚠ SUGGEST FOR THIS WEEK, NOT FOR THE WEEK THE LAST WORKOUT WAS LOGGED IN. `decision` was made when the last workout was
       analysed, against THAT workout's prescription; this function then only relabelled the rep range. Measured on a 6-week
       strength block: week 5 prescribes 1–3 reps, but the suggestion kept the week-1 load and "target 5". When this week's
       prescription (reps, reps in reserve, or progression style) differs from the one the last workout was trained under,
       re-set the load from the lifter's estimated max so the new range lands mid-range, rounded DOWN to a load they can make.
       Same prescription -> the engine's decision stands (that is where add-reps / add-load double progression lives). */

    const lastCell = historicalShellCell(program, day, slot, Number(lastEntry?.weekIndex) || 1,
        lastEntry?.perf?.[legacyId], nextId);
    const changed = !!cell && !!lastCell && (String(cell.reps) !== String(lastCell.reps) || String(cell.rir) !== String(lastCell.rir) || cell.progressionStyle !== lastCell.progressionStyle);
    if (changed && !['unobserved', 'non_comparable', 'context_limited', 'interrupted', 'incomplete'].includes(decision.outcome)) {
        const shifted = represcribeForWeek(nextId, cell, lastCell, rawLast, snap?.request, lastEntry?.unit, program.config?.unit);
        if (shifted)
            return { weight: shifted.weight, dir: current != null && shifted.weight > current ? 'up' : current != null && shifted.weight < current ? 'down' : 'hold',
                reason: shifted.reason, reps, target: shifted.target, last, lastUnit: lastEntry?.unit || program.config?.unit, action: 'represcribe', confidence: decision.confidence };
    }
    const weight = decision.suggestedLoad ?? current ?? null;
    return {
        weight,
        dir: decision.action === 'increase_load' ? 'up' : decision.action === 'decrease_load' ? 'down' : (decision.suggestedLoad != null && current != null && decision.suggestedLoad < current ? 'down' : 'hold'),
        reason: decision.reason,
        reps,
        target: decision.suggestedReps,
        setTargets: decision.setTargets,
        last, lastUnit: lastEntry?.unit || program.config?.unit,
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
function represcribeForWeek(exerciseId, cell, lastCell, last, request, fromUnit, toUnit) {
    const reps = pairOf(cell?.reps ?? cell?.range), rir = pairOf(cell?.rir), lastRir = pairOf(lastCell?.rir);
    if (!reps || !last)
        return null;
    const assumedRir = lastRir ? lastRir[0] : null;
    let e1 = null;
    for (const set of completedHistorySets(last)) {
        const w = convertLoad(set?.w, fromUnit, toUnit), r = numberOf(set?.r), reported = observedHistoryRIR(set);
        const est = estimate1RM(w, r ?? 0, reported ?? assumedRir);
        if (est !== null && (e1 === null || est > e1))
            e1 = est;
    }
    if (e1 === null)
        e1 = estimate1RM(convertLoad(last.weight, fromUnit, toUnit), numberOf(last.reps) ?? 0, assumedRir);
    if (e1 === null)
        return null;
    const repsMid = Math.round((reps[0] + reps[1]) / 2), rirMid = rir ? (rir[0] + rir[1]) / 2 : 2;
    const weight = availableLoadAtOrBelow(exerciseId, e1 / (1 + (repsMid + rirMid) / 30), request?.equipment?.loading, request?.equipment?.available);
    if (weight === null)
        return null;
    const range = reps[0] === reps[1] ? String(reps[0]) : `${reps[0]}–${reps[1]}`, reserve = rir ? (rir[0] === rir[1] ? String(rir[0]) : `${rir[0]}–${rir[1]}`) : null;
    return { weight, target: repsMid, reason: `This week's prescription changes to ${range} reps${reserve ? ` with ${reserve} in reserve` : ''}, so the load is re-set from your estimated max (about ${Math.round(e1)} ${toUnit || request?.equipment?.loading?.unit || 'lb'}) to land mid-range.` };
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
    const nextId = resolveNextExerciseId(program, day, slot, sourceSession, legacyExerciseById(legacyExercises));
    if (!nextId)
        return null;
    const decision = workout.progression.find(d => d.exerciseId === nextId);
    if (!decision)
        return null;
    const last = perf?.[legacyId] ?? null;
    const cell = getNextShellCell(program, day, slot, weekIndex);
    const convert = value => convertLoad(value, program.config?.unit, unit);
    return {
        weight: convert(decision.suggestedLoad ?? decision.currentLoad) ?? representativeShellLoad(last),
        dir: decision.action === 'increase_load' ? 'up' : decision.action === 'decrease_load' ? 'down' : (decision.suggestedLoad != null && decision.currentLoad != null && decision.suggestedLoad < decision.currentLoad ? 'down' : 'hold'),
        reason: historyLoadReason(decision.reason, unit),
        reps: String(cell?.reps ?? cell?.range ?? ''),
        target: decision.suggestedReps,
        setTargets: decision.setTargets?.map(t => ({ ...t, weight: convert(t.weight) })),
        last,
        action: decision.action,
        confidence: decision.confidence
    };
}
export function deriveProgressionSelectionEvidence(workouts = []) {
    const rows = new Map();
    const excluded = new Set(['unobserved', 'non_comparable', 'context_limited', 'interrupted', 'incomplete']);
    const ensure = (id) => {
        const existing = rows.get(id);
        if (existing) return existing;
        const row = { comparableExposures: 0, styleExposures: 0, failureCount: 0, stallCount: 0, loadingBlockedCount: 0, rirReportedSets: 0, rirEligibleSets: 0, e1rmSamples: 0 };
        rows.set(id, row);
        return row;
    };
    for (const workout of workouts ?? []) {
        for (const decision of workout?.progression ?? []) {
            const id = decision?.exerciseId;
            if (!id) continue;
            const row = ensure(id);
            const sets = (workout?.performedSets ?? []).filter(set => set.exerciseId === id);
            const comparable = !excluded.has(decision.outcome);
            if (!comparable) continue;
            row.comparableExposures += 1;
            // Saved shell history does not record a separate style-version stamp. Within a block the style
            // is stable, so comparable exposures are also the conservative evidence count for that style.
            row.styleExposures += 1;
            if (decision.outcome === 'failure') {
                row.failureCount += 1;
                row.stallCount += 1;
            }
            if (decision.outcome === 'success_blocked') row.loadingBlockedCount += 1;
            if (numberOf(decision.estimated1RM) > 0) row.e1rmSamples += 1;
            for (const set of sets) {
                row.rirEligibleSets += 1;
                if (numberOf(set.rir) !== null) row.rirReportedSets += 1;
            }
        }
    }
    return Object.fromEntries([...rows.entries()].map(([id, row]) => [id, {
        comparableExposures: row.comparableExposures,
        styleExposures: row.styleExposures,
        failureCount: row.failureCount,
        stallCount: row.stallCount,
        loadingBlockedCount: row.loadingBlockedCount,
        rirCoverage: row.rirEligibleSets ? row.rirReportedSets / row.rirEligibleSets : 0,
        e1rmSamples: row.e1rmSamples
    }]));
}

export function analyzeShellHistoryForNextEngine(program, history, legacyExercises, options = {}) {
    const snap = sourceSnapshot(program);
    if (!snap)
        throw new NextShellAdapterError('NEXT_HISTORY_SNAPSHOT_MISSING', 'This saved program lacks the engine snapshot required for history adaptation. Rebuild it before adapting the next block.');
    const normalized = normalizeHistoryEntries(history, program.id);
    const { entries } = normalized;
    const workouts = entries.map(entry => workoutFromEntry(program, entry, legacyExercises)).filter(x => {
        if (x && x.performedSets.length) return true;
        normalized.excluded.push({ id: x?.historyId ?? null, reason: x ? 'no_completed_working_sets' : 'unresolved_session' });
        return false;
    });
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
    // Response/protection evidence follows the live roster too. A user swap or added movement is
    // absent from the immutable generation snapshot, but its logged exposures must still be diagnosed.
    const sourceExercises = (program.days ?? []).flatMap(day => plannedSessionForEntry(program,
        { dayId: day.id, weekIndex: 1 }, legacyExercises)?.session.exercises ?? []);
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
    // M189 keeps progression-method evidence separate from volume/response diagnosis. Method changes
    // need repeated comparable exposures, actual failure counts, effort coverage and loading constraints.
    const progressionEvidenceByExercise = deriveProgressionSelectionEvidence(workouts);
    const successful = evidence.successfulExerciseIds;
    const protectedIds = evidence.protectedExerciseIds;
    const subjective = subjectiveUnderRecovery(entries);
    if (subjective >= 3 && recovery.status === 'normal')
        recovery = { ...recovery, status: 'watch', confidence: 'moderate', rationale: `${recovery.rationale} Subjective soreness also remained unresolved in ${subjective} of the last five logged sessions.` };
    const classification = classify(recovery, positive, negative, diagnoses, workouts.length);
    const ignoredIds = [...new Set(workouts.flatMap(w => w.ignoredLegacyExerciseIds))];
    const athleteResponse = deriveAthleteResponse({ programId: String(program.id), workouts,
        customExercises: snap.request.customExercises, unit: snap.request.equipment?.loading?.unit ?? program.config?.unit ?? 'lb',
        asOf: options.asOf ?? Date.now() });
    return {
        athleteResponse,
        excludedHistoryEntries: normalized.excluded, workouts, workoutCount: workouts.length, performedSetCount: workouts.reduce((n, w) => n + w.performedSets.length, 0),
        ignoredSetCount: entries.reduce((n, e) => n + Object.entries(e.perf ?? {}).filter(([id]) => ignoredIds.includes(id)).reduce((m, [, p]) => m + (p.sets?.filter(s => !s.sub).length ?? 0), 0), 0),
        ignoredLegacyExerciseIds: ignoredIds, recovery, cycleState, classification,
        successfulExerciseIds: successful, protectedExerciseIds: [...new Set(protectedIds)], replaceExerciseIds: evidence.replaceExerciseIds,
        techniqueLimitedExerciseIds: evidence.techniqueLimitedExerciseIds, fatigueLimitedExerciseIds: evidence.fatigueLimitedExerciseIds,
        progressionEvidenceByExercise, diagnoses,
        positiveDecisionCount: positive, negativeDecisionCount: negative, subjectiveUnderRecoverySignals: subjective,
        readyForNextBlock: !!cycleState.recommendedNextPhase, recommendedNextPhase: cycleState.recommendedNextPhase
    };
}
export function carryForwardAvoidedExercises(baseRequest, currentRequest, analysis) {
    const avoided = [...new Set([
        ...(baseRequest?.preferences?.avoidedExercises ?? []),
        ...(currentRequest?.preferences?.avoidedExercises ?? []),
        ...(analysis?.replaceExerciseIds ?? [])
    ])];
    if (!avoided.length)
        return baseRequest;
    return {
        ...baseRequest,
        preferences: { ...(baseRequest.preferences ?? {}), avoidedExercises: avoided }
    };
}
function requestAdaptedFromHistory(request, currentRequest, analysis) {
    const adapted = requestWithAthleteResponse(carryForwardAvoidedExercises(request, currentRequest, analysis), analysis.athleteResponse);
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
        throw new NextShellAdapterError('NOT_NEXT_ENGINE_PROGRAM', 'Next-block history adaptation requires an engine-generated program.');
    const snap = sourceSnapshot(current);
    if (!snap)
        throw new NextShellAdapterError('NEXT_HISTORY_SNAPSHOT_MISSING', 'This saved program lacks the engine snapshot required for history adaptation. Rebuild it before adapting the next block.');
    const analysis = analyzeShellHistoryForNextEngine(current, options.history, options.legacyExercises);
    const phase = analysis.cycleState.recommendedNextPhase;
    if (!phase)
        throw new NextShellAdapterError('NEXT_BLOCK_NOT_READY', `The new engine needs more comparable completed workouts before changing phase. ${analysis.cycleState.rationale}`, {
            workouts: analysis.workoutCount, minimumWorkouts: analysis.cycleState.minimumWorkouts, reviewAfterWorkouts: analysis.cycleState.reviewAfterWorkouts, recovery: analysis.recovery
        });
    const baseRequest = current?.nextEngine?.baseRequest ?? snap.request;
    const adaptedRequest = requestAdaptedFromHistory(baseRequest, snap.request, analysis);
    const normalized = normalizeRequest(adaptedRequest);
    const nextBlockWeeks = Math.max(1, Math.round(Number(
        options.nextBlockWeeks
        ?? current?.nextEngine?.nextBlock?.weeks
        ?? current?.config?.weeks
        ?? current?.nextEngine?.cycleTemplate?.weeks
        ?? 4
    ) || 4));
    const source = captureShellBaseProgram(current, options.legacyExercises);
    if (!source)
        throw new NextShellAdapterError('NEXT_HISTORY_SNAPSHOT_MISSING', 'This plan has incomplete current prescriptions. Rebuild it before adapting the next block.');
    const transitioned = transitionProgramPhase(source, normalized, phase, {
        successfulExerciseIds: analysis.successfulExerciseIds,
        protectedExerciseIds: analysis.protectedExerciseIds,
        replaceExerciseIds: analysis.replaceExerciseIds,
        techniqueLimitedExerciseIds: analysis.techniqueLimitedExerciseIds,
        fatigueLimitedExerciseIds: analysis.fatigueLimitedExerciseIds,
        progressionEvidenceByExercise: analysis.progressionEvidenceByExercise,
        nextBlockWeeks
    });
    if (transitioned.program.audit.result !== 'pass')
        throw new NextShellAdapterError('NEXT_BLOCK_REJECTED', `Pursuit Engine ${transitioned.program.engineVersion} could not produce a safe adapted ${phase} block.`);
    const nextConfig = { ...current.config, weeks: nextBlockWeeks };
    const legacy = nextProgramToShellProgram(transitioned.program, nextConfig, options.legacyExercises, options.makeId);
    const nextCycle = startPhase(analysis.cycleState, phase, snap.request.goal.type, snap.request.schedule.days.length);
    legacy.nextEngine = {
        ...legacy.nextEngine, request: JSON.parse(JSON.stringify(adaptedRequest)), baseRequest: JSON.parse(JSON.stringify(baseRequest)), program: JSON.parse(JSON.stringify(transitioned.program)), cycleState: JSON.parse(JSON.stringify(nextCycle)), historySchemaVersion: 1,
        priorBlock: { programId: current.id, phase: snap.program.phase, workouts: analysis.workoutCount, classification: analysis.classification, recovery: analysis.recovery.status },
        continuity: transitioned.continuity,
        historySummary: { positive: analysis.positiveDecisionCount, negative: analysis.negativeDecisionCount, successfulExercises: analysis.successfulExerciseIds.length, ignoredLegacyExerciseIds: analysis.ignoredLegacyExerciseIds }
    };
    const finalized = finalizeGeneratedShellVolume(legacy, options.legacyExercises);
    return { program: finalized, nextProgram: finalized.nextEngine.program, request: adaptedRequest, analysis, continuity: transitioned.continuity };
}
