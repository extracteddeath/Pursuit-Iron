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
function isCompatibleReplacement(previous, candidate, day, request) {
    const exerciseMap = createExerciseMap(request.customExercises);
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
/**
 * Generate the target phase, then make conservative audit-gated attempts to
 * retain successful exercises from the previous phase. The target phase owns
 * sets/reps/RIR/rest; continuity owns exercise identity/progression history.
 */
export function transitionProgramPhase(previous, request, target, evidence) {
    const generated = generateProgram(request, { phase: target }).program;
    const successful = new Set(evidence.successfulExerciseIds);
    const protectedIds = new Set(evidence.protectedExerciseIds ?? []);
    const previousByDay = new Map(previous.sessions.map(session => [session.day, session]));
    let program = generated;
    // Exact retained IDs count automatically. For changed slots, try the most
    // successful/protected compatible previous exercise first.
    for (const session of [...program.sessions]) {
        const priorSession = previousByDay.get(session.day);
        if (!priorSession)
            continue;
        const alreadyUsed = new Set(session.exercises.map(ex => ex.exerciseId));
        for (const candidate of [...session.exercises]) {
            if (priorSession.exercises.some(ex => ex.exerciseId === candidate.exerciseId))
                continue;
            const alternatives = priorSession.exercises
                .filter(ex => successful.has(ex.exerciseId) || protectedIds.has(ex.exerciseId))
                .filter(ex => !alreadyUsed.has(ex.exerciseId))
                .filter(ex => isCompatibleReplacement(ex, candidate, session.day, request))
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
