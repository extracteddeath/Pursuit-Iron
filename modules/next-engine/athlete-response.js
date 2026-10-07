import { createExerciseMap } from './exercise-db.js';
import { estimate1RM } from './history.js';
import { convertHistoryLoad, progressionExposureContext } from './history-contract.js';
import { migrateDomainRecord, DomainContractError } from './domain-contracts.js';

export const ATHLETE_RESPONSE_POLICY = Object.freeze({ windowDays: 90, maxExposuresPerSlot: 12,
    minimumExposures: 6, minimumSpanDays: 14, minimumEffortCoverage: .75,
    minimumDoseScale: .9, maximumDoseScale: 1.05 });
const limited = new Set(['unobserved', 'non_comparable', 'context_limited', 'interrupted', 'incomplete']);
const limitedReasons = new Set(['no_completed_sets', 'non_comparable_exposure', 'readiness_limited_exposure', 'interrupted_exposure', 'incomplete_session', 'incomplete_prescription']);
const clamp = (x, lo, hi) => Math.max(lo, Math.min(hi, x));
const mean = xs => xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : null;
const median = xs => { const a = [...xs].sort((x, y) => x - y); return a.length ? (a[Math.floor((a.length - 1) / 2)] + a[Math.ceil((a.length - 1) / 2)]) / 2 : null; };
const dayMs = 86400000;
const confidence = (n, coverage, span) => n < 3 || span < 7 ? 0 : Math.round(Math.min(.9, n / (n + 6)) * coverage * 1000) / 1000;
const slotKey = (sessionId, exerciseId) => JSON.stringify([sessionId, exerciseId]);

/** Observational response estimates, not causal diagnoses. Targets never count as observed effort. */
export function deriveAthleteResponse({ programId, workouts = [], customExercises = [], unit = 'lb', asOf = Date.now() }) {
    if (typeof programId !== 'string' || !programId || !['lb', 'kg'].includes(unit) || !Number.isFinite(asOf) || Math.abs(asOf) > 8640000000000000)
        throw new DomainContractError('INVALID_RESPONSE_SCOPE', 'response', 'Response requires a program identity, valid unit and finite observation time.');
    const exerciseMap = createExerciseMap(customExercises), identities = new Map(), excluded = {};
    const exclude = reason => { excluded[reason] = (excluded[reason] ?? 0) + 1; };
    for (const w of Array.isArray(workouts) ? workouts : []) {
        if (w?.programId !== programId) { exclude('unrelated_program'); continue; }
        const date = Date.parse(w.completedAt);
        if (!Number.isFinite(date) || date > asOf || asOf - date > ATHLETE_RESPONSE_POLICY.windowDays * dayMs) { exclude('outside_time_window'); continue; }
        if (!w.historyId || !w.session?.id) { exclude('missing_identity'); continue; }
        if (w.unit !== undefined && !['lb', 'kg'].includes(w.unit)) { exclude('invalid_unit'); continue; }
        const id = String(w.historyId), previous = identities.get(id);
        if (previous) exclude('duplicate_identity');
        const revision = x => Number.isFinite(Date.parse(x.updatedAt)) ? Date.parse(x.updatedAt) : Date.parse(x.completedAt);
        if (!previous || revision(w) >= revision(previous)) identities.set(id, w);
    }
    const slots = new Map(), muscleRows = new Map(), workoutRows = new Map();
    const ordered = [...identities.values()].sort((a, b) => a.completedAt.localeCompare(b.completedAt) || String(a.historyId).localeCompare(String(b.historyId)));
    for (const w of ordered) for (const planned of w.session.exercises ?? []) {
        if (w.session.exercises.filter(ex => ex.exerciseId === planned.exerciseId).length !== 1) { exclude('ambiguous_exercise_slot'); continue; }
        const decision = (w.progression ?? []).find(d => d.exerciseId === planned.exerciseId);
        const sets = (w.performedSets ?? []).filter(s => s.exerciseId === planned.exerciseId);
        const flags = progressionExposureContext(sets, w);
        if (!decision || limited.has(decision.outcome) || limitedReasons.has(decision.reasonCode) || flags.nonComparable || flags.interrupted || flags.badDay) { exclude('limited_exposure'); continue; }
        if (!Number.isInteger(planned.sets) || planned.sets < 1 || sets.length < planned.sets) { exclude('incomplete_exposure'); continue; }
        if (sets.some(s => !Number.isFinite(s.load) || !Number.isInteger(s.reps) || s.reps < 1 || (s.rir !== null && (!Number.isFinite(s.rir) || s.rir < 0 || s.rir > 10)))) { exclude('malformed_observation'); continue; }
        const def = exerciseMap.get(planned.exerciseId);
        if (!def || (def.source === 'custom' && def.metadataConfidence !== 'high')) { exclude('unknown_exercise_semantics'); continue; }
        const observed = sets.filter(s => s.rir !== null);
        const scores = observed.map(s => estimate1RM(convertHistoryLoad(s.load, w.unit ?? unit, unit), s.reps, s.rir)).filter(x => x !== null);
        const row = { at: Date.parse(w.completedAt), historyId: String(w.historyId), sessionId: String(w.session.id), exerciseId: planned.exerciseId,
            sets: sets.length, effortCoverage: observed.length / sets.length, score: scores.length ? Math.max(...scores) : null,
            failure: decision.outcome === 'failure', success: ['success', 'success_blocked'].includes(decision.outcome),
            pain: sets.some(s => s.painFlag === true), poorTechnique: sets.some(s => s.techniqueQuality === 'poor'),
            effortOvershoot: observed.length ? observed.filter(s => s.rir < (planned.prescription?.rir?.[0] ?? 0)).length / observed.length : null };
        const key = slotKey(row.sessionId, row.exerciseId), list = slots.get(key) ?? [];
        list.push(row); slots.set(key, list.slice(-ATHLETE_RESPONSE_POLICY.maxExposuresPerSlot));
        for (const [muscle, contribution] of Object.entries(def.muscles)) if (contribution.credit >= .5) {
            const rows = muscleRows.get(muscle) ?? []; rows.push({ ...row, dose: sets.length * contribution.credit }); muscleRows.set(muscle, rows);
        }
        const rows = workoutRows.get(row.historyId) ?? []; rows.push(row); workoutRows.set(row.historyId, rows);
    }
    const progressionVelocity = {}, exerciseSuccess = {}, muscles = {}, frequency = {};
    for (const [key, rows] of [...slots].sort(([a], [b]) => a.localeCompare(b))) {
        const span = (rows.at(-1).at - rows[0].at) / dayMs, coverage = mean(rows.map(r => r.effortCoverage));
        const samples = rows.filter(r => r.score !== null), first = samples[0], last = samples.at(-1);
        const velocity = samples.length >= 3 && last.at > first.at ? clamp((last.score / first.score - 1) / ((last.at - first.at) / (7 * dayMs)), -.25, .25) : null;
        progressionVelocity[key] = { sessionId: rows[0].sessionId, exerciseId: rows[0].exerciseId, sampleCount: samples.length,
            relativePerWeek: velocity, confidence: confidence(samples.length, coverage, span), basis: 'reported_effort_trend' };
        const id = rows[0].exerciseId, combined = exerciseSuccess[id] ?? { sampleCount: 0, successes: 0, failures: 0, discomfortExposures: 0, poorTechniqueExposures: 0, confidence: 0 };
        combined.sampleCount += rows.length; combined.successes += rows.filter(r => r.success).length; combined.failures += rows.filter(r => r.failure).length;
        combined.discomfortExposures += rows.filter(r => r.pain).length; combined.poorTechniqueExposures += rows.filter(r => r.poorTechnique).length;
        combined.confidence = Math.max(combined.confidence, confidence(rows.length, coverage, span)); exerciseSuccess[id] = combined;
    }
    for (const [muscle, all] of [...muscleRows].sort(([a], [b]) => a.localeCompare(b))) {
        // Multiple exercises on the same day supply one frequency/recovery observation.
        const days = new Map(); for (const row of all) { const key = row.historyId, a = days.get(key) ?? []; a.push(row); days.set(key, a); }
        const rows = [...days.values()].map(a => ({ at: a[0].at, dose: a.reduce((n, r) => n + r.dose, 0), failure: a.some(r => r.failure),
            effortCoverage: mean(a.map(r => r.effortCoverage)), discomfort: a.some(r => r.pain || r.poorTechnique) })).slice(-12);
        const span = (rows.at(-1).at - rows[0].at) / dayMs, coverage = mean(rows.map(r => r.effortCoverage));
        const failures = rows.filter(r => r.failure).length, pressure = failures / rows.length;
        const conf = confidence(rows.length, coverage, span), enough = rows.length >= 6 && span >= 14 && coverage >= .75 && !rows.some(r => r.discomfort);
        const velocities = Object.values(progressionVelocity).filter(v => (exerciseMap.get(v.exerciseId)?.muscles?.[muscle]?.credit ?? 0) >= .5 && v.relativePerWeek !== null);
        const plateau = velocities.length > 0 && velocities.every(v => v.sampleCount >= 6 && Math.abs(v.relativePerWeek) < .002);
        const doseScale = enough && pressure >= .5 ? .9 : enough && pressure === 0 && plateau ? 1.05 : 1;
        muscles[muscle] = { sampleCount: rows.length, spanDays: Math.round(span * 100) / 100, effortCoverage: coverage,
            confidence: conf, doseScale, observedMeanSets: mean(rows.map(r => r.dose)), fatiguePressure: pressure,
            reason: doseScale < 1 ? 'repeated_comparable_failures' : doseScale > 1 ? 'stable_complete_observed_plateau' : 'retain_prior' };
        const gaps = rows.slice(1).map((r, i) => (r.at - rows[i].at) / dayMs).filter(x => x > 0);
        frequency[muscle] = { observedPerWeek: gaps.length ? 7 / median(gaps) : null, medianGapDays: median(gaps), confidence: conf,
            recommendedExposureDelta: enough && pressure >= .5 ? -1 : 0, application: 'review_only_preserve_authored_schedule' };
    }
    const recentWorkouts = [...workoutRows.values()].slice(-12), broadFailures = recentWorkouts.filter(rows => rows.filter(r => r.failure).length >= 2).length;
    const coverage = mean(recentWorkouts.flatMap(rows => rows.map(r => r.effortCoverage))) ?? 0;
    const span = recentWorkouts.length ? (recentWorkouts.at(-1)[0].at - recentWorkouts[0][0].at) / dayMs : 0;
    const conf = confidence(recentWorkouts.length, coverage, span), pressure = recentWorkouts.length ? broadFailures / recentWorkouts.length : 0;
    const watch = recentWorkouts.length >= 6 && span >= 14 && coverage >= .75 && pressure >= .5;
    const model = { schemaVersion: 1, programId, unit, asOf: new Date(asOf).toISOString(), muscles, frequency, exerciseSuccess, progressionVelocity,
        fatigue: { pressure, confidence: conf, capacityScale: watch ? .95 : 1 },
        recovery: { status: watch ? 'watch' : recentWorkouts.length >= 6 && conf >= .4 ? 'tolerated' : 'unknown', confidence: conf,
            observedMedianGapHours: median(recentWorkouts.slice(1).map((rows, i) => (rows[0].at - recentWorkouts[i][0].at) / 3600000).filter(x => x > 0)) },
        evidence: { comparableExposures: [...slots.values()].reduce((n, rows) => n + rows.length, 0), comparableWorkouts: recentWorkouts.length, excluded,
            provenance: 'program_and_session_owned_completed_observations', interpretation: 'regularized_observational_estimates' } };
    return validateAthleteResponse(model);
}

export function validateAthleteResponse(input) {
    const model = migrateDomainRecord('response', input);
    const valid = typeof model.programId === 'string' && !!model.programId && ['lb', 'kg'].includes(model.unit) && Number.isFinite(Date.parse(model.asOf))
        && model.muscles && typeof model.muscles === 'object' && !Array.isArray(model.muscles) && model.fatigue;
    if (!valid) throw new DomainContractError('INVALID_RESPONSE', 'response', 'Malformed athlete response scope.');
    for (const [muscle, row] of Object.entries(model.muscles)) if (!row || !Number.isInteger(row.sampleCount) || row.sampleCount < 1 || row.sampleCount > 12
        || !Number.isFinite(row.confidence) || row.confidence < 0 || row.confidence > 1 || !Number.isFinite(row.doseScale) || row.doseScale < .9 || row.doseScale > 1.05
        || !Number.isFinite(row.spanDays) || row.spanDays < 0 || !Number.isFinite(row.effortCoverage) || row.effortCoverage < 0 || row.effortCoverage > 1)
        throw new DomainContractError('INVALID_RESPONSE', `response.muscles.${muscle}`, 'Malformed or unbounded muscle response estimate.');
    if (!Number.isFinite(model.fatigue.capacityScale) || model.fatigue.capacityScale < .95 || model.fatigue.capacityScale > 1)
        throw new DomainContractError('INVALID_RESPONSE', 'response.fatigue.capacityScale', 'Response capacity must remain bounded.');
    return model;
}

export function personalizeMusclePrescription(prescription, response) {
    if (!response) return prescription;
    const row = response.muscles?.[prescription.muscle];
    if (!row || row.sampleCount < 6 || row.spanDays < 14 || row.effortCoverage < .75 || row.confidence < .4 || row.doseScale === 1) return prescription;
    // Hard/public floors and explicit priority remain intact. Only the preferred/upper region moves.
    const preferred = Math.max(prescription.minimum, Math.round(prescription.preferred * row.doseScale));
    const upper = Math.max(preferred, prescription.minimum, Math.round(prescription.upper * row.doseScale));
    return { ...prescription, preferred, upper, directPreferred: Math.max(prescription.directMinimum, Math.round(prescription.directPreferred * row.doseScale)) };
}

export function requestWithAthleteResponse(request, response) {
    const model = validateAthleteResponse(response);
    if (model.evidence?.comparableExposures < 6) return request;
    const learned = Object.entries(model.exerciseSuccess ?? {}).filter(([, row]) => row.confidence >= .4 && row.sampleCount >= 6
        && row.successes / row.sampleCount >= .6 && !row.discomfortExposures && !row.poorTechniqueExposures)
        .map(([id]) => id).filter(id => !request.preferences?.avoidedExercises?.includes(id)).sort().slice(0, 8);
    return { ...request, preferences: { ...(request.preferences ?? {}), athleteResponse: model,
        ...(learned.length ? { preferredExercises: [...new Set([...(request.preferences?.preferredExercises ?? []), ...learned])] } : {}),
        responseCapacityScale: Math.min(request.preferences?.responseCapacityScale ?? 1, model.fatigue.capacityScale) } };
}
