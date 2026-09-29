import { auditProgram } from './arbiter.js';
import { createTrainingSetEvents } from './events.js';
import { createExerciseMap } from './exercise-db.js';
import { generateProgram } from './generate.js';
import { deriveMuscleLedger } from './ledgers.js';
import { historyDecision, withProgramExplainability } from './explainability.js';
function equipmentEligible(def, day, request) {
    const scheduleDay = request.schedule.days.find(d => d.day === day);
    if (!scheduleDay)
        return false;
    const available = scheduleDay.equipmentOverride ?? request.equipment.available;
    if ((def.flags.bodyweight || def.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
        return false;
    return [def.equipment, ...(def.equipmentAlternatives ?? [])]
        .some(setup => setup.every(item => item === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : available.includes(item)));
}
function muscleSimilarity(a, b) {
    const muscles = new Set([
        ...Object.keys(a.muscles),
        ...Object.keys(b.muscles)
    ]);
    let overlap = 0;
    let union = 0;
    for (const muscle of muscles) {
        const av = a.muscles[muscle]?.credit ?? 0;
        const bv = b.muscles[muscle]?.credit ?? 0;
        overlap += Math.min(av, bv);
        union += Math.max(av, bv);
    }
    return union > 0 ? overlap / union : 0;
}
function strengthCompatible(previous, candidate) {
    const lifts = ['bench_press', 'back_squat', 'deadlift', 'overhead_press'];
    return lifts.some(lift => {
        const prior = previous.liftSpecificity?.[lift] ?? 0;
        const next = candidate.liftSpecificity?.[lift] ?? 0;
        return next > .45 && prior > .45 && prior >= next - .15;
    });
}
function roleCompatible(previous, candidate) {
    if (previous.role === candidate.role)
        return true;
    const hypertrophy = new Set(['hypertrophy_compound', 'hypertrophy_isolation']);
    return hypertrophy.has(previous.role) && hypertrophy.has(candidate.role);
}
function isCompatibleReplacement(previous, candidate, day, request, exerciseMap) {
    const priorDef = exerciseMap.get(previous.exerciseId);
    const candidateDef = exerciseMap.get(candidate.exerciseId);
    if (!priorDef || !candidateDef)
        return false;
    if (!equipmentEligible(priorDef, day, request))
        return false;
    if (request.preferences.avoidedExercises?.includes(previous.exerciseId))
        return false;
    if (!roleCompatible(previous, candidate))
        return false;
    if (candidate.role === 'primary_strength' || candidate.role === 'secondary_strength') {
        return strengthCompatible(priorDef, candidateDef);
    }
    const similarity = muscleSimilarity(priorDef, candidateDef);
    const sameMovement = priorDef.movementFamily === candidateDef.movementFamily;
    const sameCompoundClass = priorDef.flags.compound === candidateDef.flags.compound;
    if (candidate.advancedTechnique && !sameCompoundClass)
        return false;
    return similarity >= .5 && (sameMovement || similarity >= .72);
}
function withExercise(program, sessionId, candidateId, previous, request) {
    const sessions = program.sessions.map(session => session.id !== sessionId ? session : {
        ...session,
        exercises: session.exercises.map(ex => ex.exerciseId !== candidateId ? ex : {
            ...ex,
            exerciseId: previous.exerciseId,
            name: previous.name
        })
    });
    const events = createTrainingSetEvents(sessions, request.customExercises);
    const muscleLedger = deriveMuscleLedger(events);
    return { ...program, sessions, events, muscleLedger };
}
function vectorSimilarity(a, b) {
    const keys = new Set([...a.keys(), ...b.keys()]);
    let overlap = 0, union = 0;
    for (const key of keys) {
        const av = a.get(key) ?? 0, bv = b.get(key) ?? 0;
        overlap += Math.min(av, bv); union += Math.max(av, bv);
    }
    return union > 0 ? overlap / union : 0;
}
function sessionMuscleVector(session, exerciseMap) {
    const out = new Map();
    for (const exercise of session.exercises ?? []) {
        const def = exerciseMap.get(exercise.exerciseId);
        if (!def) continue;
        for (const [muscle, credit] of Object.entries(def.muscles ?? {}))
            out.set(muscle, (out.get(muscle) ?? 0) + (credit.credit ?? 0) * Math.max(1, Number(exercise.sets) || 1));
    }
    return out;
}
function sessionMovementSet(session, exerciseMap) {
    return new Set((session.exercises ?? []).map(ex => exerciseMap.get(ex.exerciseId)?.movementFamily).filter(Boolean));
}
function jaccard(a, b) {
    const union = new Set([...a, ...b]);
    if (!union.size) return 0;
    let overlap = 0;
    for (const value of a) if (b.has(value)) overlap++;
    return overlap / union.size;
}
function transitionSessionScore(previous, target, request, exerciseMap) {
    let score = previous.intent === target.intent ? 5 : 0;
    score += vectorSimilarity(sessionMuscleVector(previous, exerciseMap), sessionMuscleVector(target, exerciseMap)) * 4;
    score += jaccard(sessionMovementSet(previous, exerciseMap), sessionMovementSet(target, exerciseMap)) * 3;
    const priorStrength = (previous.exercises ?? []).filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength').length;
    const targetStrength = (target.exercises ?? []).filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength').length;
    score += Math.max(0, 2 - Math.abs(priorStrength - targetStrength) * .75);
    const eligible = (previous.exercises ?? []).filter(ex => {
        const def = exerciseMap.get(ex.exerciseId);
        return def && equipmentEligible(def, target.day, request) && !request.preferences.avoidedExercises?.includes(ex.exerciseId);
    }).length;
    score += (previous.exercises?.length ? eligible / previous.exercises.length : 0) * 2;
    if (previous.day === target.day) score += .2;
    return score;
}
/** Deterministic maximum-score bipartite session matching for phase continuity. */
export function matchPriorSessionsForTransition(previousSessions, targetSessions, request) {
    const exerciseMap = createExerciseMap(request.customExercises);
    const previous = [...(previousSessions ?? [])].sort((a, b) => String(a.id).localeCompare(String(b.id)));
    const target = [...(targetSessions ?? [])].sort((a, b) => String(a.id).localeCompare(String(b.id)));
    const memo = new Map();
    const solve = (i, mask) => {
        const key = `${i}:${mask}`;
        if (memo.has(key)) return memo.get(key);
        if (i >= target.length) return { score: 0, pairs: [] };
        let best = solve(i + 1, mask);
        for (let j = 0; j < previous.length; j++) {
            if (mask & (1 << j)) continue;
            const local = transitionSessionScore(previous[j], target[i], request, exerciseMap);
            if (local < 1.5) continue;
            const tail = solve(i + 1, mask | (1 << j));
            const candidate = { score: local + tail.score, pairs: [[target[i].id, previous[j]], ...tail.pairs] };
            const sig = x => x.pairs.map(([id, p]) => `${id}:${p.id}`).join('|');
            if (candidate.score > best.score + 1e-9 || (Math.abs(candidate.score - best.score) <= 1e-9 && sig(candidate) < sig(best))) best = candidate;
        }
        memo.set(key, best); return best;
    };
    return new Map(solve(0, 0).pairs);
}

/**
 * Generate the target phase, then make conservative audit-gated attempts to
 * retain successful exercises from the previous phase. The target phase owns
 * sets/reps/RIR/rest; continuity owns exercise identity/progression history.
 */
export function transitionProgramPhase(previous, request, target, evidence) {
    const generated = generateProgram(request, { phase: target }).program;
    const successful = new Set(evidence.successfulExerciseIds);
    const protectedIds = new Set(evidence.protectedExerciseIds ?? []);
    const replaceIds = new Set(evidence.replaceExerciseIds ?? []);
    const exerciseMap = createExerciseMap(request.customExercises);
    let program = generated;
    const previousByTargetSession = matchPriorSessionsForTransition(previous.sessions, program.sessions, request);
    // Exact retained IDs count automatically. For changed slots, try the most
    // successful/protected compatible previous exercise first.
    for (const session of [...program.sessions]) {
        const priorSession = previousByTargetSession.get(session.id);
        if (!priorSession)
            continue;
        const alreadyUsed = new Set(session.exercises.map(ex => ex.exerciseId));
        for (const candidate of [...session.exercises]) {
            if (priorSession.exercises.some(ex => ex.exerciseId === candidate.exerciseId))
                continue;
            const alternatives = priorSession.exercises
                .filter(ex => successful.has(ex.exerciseId) || protectedIds.has(ex.exerciseId))
                .filter(ex => !replaceIds.has(ex.exerciseId))
                .filter(ex => !alreadyUsed.has(ex.exerciseId))
                .filter(ex => isCompatibleReplacement(ex, candidate, session.day, request, exerciseMap))
                .sort((a, b) => Number(protectedIds.has(b.exerciseId)) - Number(protectedIds.has(a.exerciseId)) || a.exerciseId.localeCompare(b.exerciseId));
            for (const previousExercise of alternatives) {
                const proposed = withExercise(program, session.id, candidate.exerciseId, previousExercise, request);
                const { audit: _audit, ...auditable } = proposed;
                void _audit;
                const audit = auditProgram(auditable, request);
                if (audit.result === 'pass') {
                    program = { ...proposed, audit, rationale: [...proposed.rationale, `${previousExercise.name} was retained across the phase transition because recent performance supported continuity and the target-phase program still passed audit.`] };
                    alreadyUsed.delete(candidate.exerciseId);
                    alreadyUsed.add(previousExercise.exerciseId);
                    break;
                }
            }
        }
    }
    const priorExerciseIds = new Set(previous.sessions.flatMap(s => s.exercises.map(e => e.exerciseId)));
    const finalExerciseIds = new Set(program.sessions.flatMap(s => s.exercises.map(e => e.exerciseId)));
    const eligible = [...successful].filter(id => priorExerciseIds.has(id));
    const preserved = eligible.filter(id => finalExerciseIds.has(id));
    const replaced = eligible.filter(id => !finalExerciseIds.has(id));
    const retentionRate = eligible.length ? preserved.length / eligible.length : 1;
    // A phase transition can intentionally shrink the exercise menu while introducing required strength
    // anchors. Raw retained/eligible therefore understates continuity whenever there are fewer legitimate
    // target-phase slots than successful prior exercises (e.g. 31-slot hypertrophy -> 18-slot strength PPL).
    // Reserve only NEW strength-role slots here; retained prior strength work remains a continuity success.
    const finalExercises = program.sessions.flatMap(session => session.exercises);
    const structuralNewStrengthSlots = finalExercises.filter(ex => (ex.role === 'primary_strength' || ex.role === 'secondary_strength') && !priorExerciseIds.has(ex.exerciseId)).length;
    const continuityCapacity = Math.max(0, Math.min(eligible.length, finalExercises.length - structuralNewStrengthSlots));
    const capacityAdjustedRetentionRate = continuityCapacity > 0 ? Math.min(1, preserved.length / continuityCapacity) : 1;
    const continuity = {
        previousPhase: previous.phase,
        nextPhase: target,
        eligibleSuccessfulExercises: eligible.length,
        retainedSuccessfulExercises: preserved.length,
        retentionRate,
        continuityCapacity,
        capacityAdjustedRetentionRate,
        structuralNewStrengthSlots,
        preservedExerciseIds: preserved,
        replacedDespiteSuccess: replaced,
        rationale: eligible.length
            ? `${preserved.length}/${eligible.length} recently successful exercises were retained (${preserved.length}/${continuityCapacity} of structurally available continuity slots) while applying the target phase prescription.`
            : 'No recent successful-exercise evidence was available, so the target phase was generated without continuity preferences.'
    };
    program = withProgramExplainability(program, request, [historyDecision('Phase transition continuity', eligible.length ? `${preserved.length}/${continuityCapacity} structurally retainable successful exercises preserved` : 'Target phase generated without prior success evidence', continuity.rationale, { eligibleSuccessfulExercises: eligible.length, retainedSuccessfulExercises: preserved.length, continuityCapacity, capacityAdjustedRetentionRate: Math.round(capacityAdjustedRetentionRate * 1000) / 1000, structuralNewStrengthSlots }, replaced.length ? [`${replaced.length} successful exercise identities changed where the target phase or compatibility constraints required it.`] : ['No successful exercise identity was changed unnecessarily.'])]);
    return { program, continuity };
}
