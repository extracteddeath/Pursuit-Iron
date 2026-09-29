import { createExerciseCatalog, createExerciseMap } from './exercise-db.js';

function setupEligible(def, available, bodyweightMode) {
    if ((def.flags.bodyweight || def.equipment.includes('bodyweight')) && bodyweightMode === 'exclude')
        return false;
    return [def.equipment, ...(def.equipmentAlternatives ?? [])]
        .some(setup => setup.every(item => item === 'bodyweight' ? bodyweightMode !== 'exclude' : available.includes(item)));
}

/**
 * Immutable-by-contract per-generation lookup context. It centralizes the catalog, day schedule,
 * avoided IDs, equipment eligibility, and candidate indexes so repair paths do not rebuild parallel
 * views of the same request. Callers receive frozen candidate arrays and never mutate the context.
 */
export function createEngineContext(request) {
    const exerciseMap = createExerciseMap(request.customExercises);
    const exerciseCatalog = Object.freeze([...createExerciseCatalog(request.customExercises)]);
    const scheduleByDay = new Map(request.schedule.days.map(day => [day.day, day]));
    const avoidedExercises = new Set(request.preferences?.avoidedExercises ?? []);
    const eligibilityCache = new Map();
    const equipmentEligible = (defOrId, dayName) => {
        const def = typeof defOrId === 'string' ? exerciseMap.get(defOrId) : defOrId;
        if (!def || avoidedExercises.has(def.id))
            return false;
        const key = `${dayName}:${def.id}`;
        if (eligibilityCache.has(key))
            return eligibilityCache.get(key);
        const day = scheduleByDay.get(dayName);
        const eligible = !!day && setupEligible(def, day.equipmentOverride ?? request.equipment.available, request.equipment.bodyweight);
        eligibilityCache.set(key, eligible);
        return eligible;
    };
    const feasibleSomewhere = def => request.schedule.days.some(day => equipmentEligible(def, day.day));
    const candidatesByMuscle = new Map();
    const candidatesByLift = new Map();
    for (const def of exerciseCatalog) {
        if (!feasibleSomewhere(def))
            continue;
        for (const [muscle, credit] of Object.entries(def.muscles ?? {})) {
            if ((credit.credit ?? 0) <= 0)
                continue;
            const list = candidatesByMuscle.get(muscle) ?? [];
            list.push(def);
            candidatesByMuscle.set(muscle, list);
        }
        for (const [lift, specificity] of Object.entries(def.liftSpecificity ?? {})) {
            if ((specificity ?? 0) <= .45)
                continue;
            const list = candidatesByLift.get(lift) ?? [];
            list.push(def);
            candidatesByLift.set(lift, list);
        }
    }
    for (const [key, list] of candidatesByMuscle)
        candidatesByMuscle.set(key, Object.freeze([...list].sort((a, b) => a.id.localeCompare(b.id))));
    for (const [key, list] of candidatesByLift)
        candidatesByLift.set(key, Object.freeze([...list].sort((a, b) => a.id.localeCompare(b.id))));
    return Object.freeze({
        request,
        exerciseMap,
        exerciseCatalog,
        scheduleByDay,
        avoidedExercises,
        equipmentEligible,
        exerciseById: id => exerciseMap.get(id),
        scheduleDay: day => scheduleByDay.get(day),
        isAvoided: id => avoidedExercises.has(id),
        candidatesForMuscle: muscle => candidatesByMuscle.get(muscle) ?? Object.freeze([]),
        candidatesForLift: lift => candidatesByLift.get(lift) ?? Object.freeze([])
    });
}

/** Structural fingerprint for candidate-program memoization. */
export function programFingerprint(sessions) {
    return JSON.stringify((sessions ?? []).map(session => [
        session.id, session.day, session.intent, session.maxMinutes,
        (session.exercises ?? []).map(ex => [
            ex.exerciseId, ex.role, ex.sets,
            ex.prescription?.reps, ex.prescription?.rir, ex.prescription?.restSeconds,
            ex.progression, ex.progressionStyle,
            ex.advancedTechnique?.type ?? null,
            ex.supersetGroup ?? null
        ])
    ]));
}

export function auditVector(audit, objective = 0) {
    const findings = audit?.findings ?? [];
    return {
        critical: findings.filter(f => f.severity === 'critical').length,
        major: findings.filter(f => f.severity === 'major').length,
        warning: findings.filter(f => f.severity === 'warning').length,
        objective: Number.isFinite(objective) ? objective : Number.POSITIVE_INFINITY
    };
}

/** Negative means A is preferred. Order is always critical → major → warning → objective. */
export function compareCandidateQuality(a, b) {
    for (const key of ['critical', 'major', 'warning', 'objective']) {
        const delta = (a?.[key] ?? Number.POSITIVE_INFINITY) - (b?.[key] ?? Number.POSITIVE_INFINITY);
        if (Math.abs(delta) > 1e-9)
            return delta < 0 ? -1 : 1;
    }
    return 0;
}

/**
 * Reusable deterministic transaction cache. Evaluation remains caller-owned; this layer guarantees
 * identical candidate structures are rebuilt/audited at most once inside a generation transaction.
 */
export function createTransactionalEvaluator(evaluate) {
    const cache = new Map();
    let hits = 0;
    let misses = 0;
    return Object.freeze({
        evaluate(sessions) {
            const fingerprint = programFingerprint(sessions);
            if (cache.has(fingerprint)) {
                hits++;
                return cache.get(fingerprint);
            }
            misses++;
            const result = evaluate(sessions, fingerprint);
            cache.set(fingerprint, result);
            return result;
        },
        stats() { return Object.freeze({ hits, misses, size: cache.size }); }
    });
}
