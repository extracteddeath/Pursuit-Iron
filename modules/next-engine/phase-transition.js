import { auditProgram } from './arbiter.js';
import { createTrainingSetEvents } from './events.js';
import { createEngineContext, createTransactionalEvaluator } from './engine-context.js';
import { generateProgram } from './generate.js';
import { firstPassingCapacityProgram } from './capacity-generation.js';
import { deriveMuscleLedger } from './ledgers.js';
import { historyDecision, withBlockReviewExplainability, withProgramExplainability } from './explainability.js';
import { progressionInstruction, reselectProgressionStyle } from './progression-style.js';

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

function isCompatibleReplacement(previous, candidate, day, request, context) {
    const priorDef = context.exerciseById(previous.exerciseId);
    const candidateDef = context.exerciseById(candidate.exerciseId);
    if (!priorDef || !candidateDef)
        return false;
    if (!context.equipmentEligible(priorDef, day))
        return false;
    if (context.isAvoided(previous.exerciseId))
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

function sessionsWithExercise(program, sessionId, candidateId, previous) {
    return program.sessions.map(session => session.id !== sessionId ? session : {
        ...session,
        exercises: session.exercises.map(ex => ex.exerciseId !== candidateId ? ex : {
            ...ex,
            exerciseId: previous.exerciseId,
            name: previous.name
        })
    });
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

function transitionSessionScore(previous, target, request, exerciseMap, context) {
    let score = previous.intent === target.intent ? 5 : 0;
    score += vectorSimilarity(sessionMuscleVector(previous, exerciseMap), sessionMuscleVector(target, exerciseMap)) * 4;
    score += jaccard(sessionMovementSet(previous, exerciseMap), sessionMovementSet(target, exerciseMap)) * 3;
    const priorStrength = (previous.exercises ?? []).filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength').length;
    const targetStrength = (target.exercises ?? []).filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength').length;
    score += Math.max(0, 2 - Math.abs(priorStrength - targetStrength) * .75);
    const eligible = (previous.exercises ?? []).filter(ex => {
        const def = exerciseMap.get(ex.exerciseId);
        return def && context.equipmentEligible(def, target.day);
    }).length;
    score += (previous.exercises?.length ? eligible / previous.exercises.length : 0) * 2;
    if (previous.day === target.day) score += .2;
    return score;
}

/** Deterministic maximum-score bipartite session matching for phase continuity. */
export function matchPriorSessionsForTransition(previousSessions, targetSessions, request, suppliedContext) {
    const context = suppliedContext ?? createEngineContext(request);
    const exerciseMap = context.exerciseMap;
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
            const local = transitionSessionScore(previous[j], target[i], request, exerciseMap, context);
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

function progressionEvidenceFor(id, evidence, successful, fatigueLimited, techniqueLimited) {
    const detailed = evidence?.progressionEvidenceByExercise?.[id] ?? {};
    const hasSignal = successful.has(id) || fatigueLimited.has(id) || techniqueLimited.has(id)
        || Number(detailed.comparableExposures) > 0;
    return {
        ...detailed,
        successful: detailed.successful ?? successful.has(id),
        fatigueLimited: detailed.fatigueLimited ?? fatigueLimited.has(id),
        techniqueLimited: detailed.techniqueLimited ?? techniqueLimited.has(id),
        comparableExposures: Number.isFinite(Number(detailed.comparableExposures))
            ? Number(detailed.comparableExposures)
            : hasSignal ? 3 : 0,
        styleExposures: Number.isFinite(Number(detailed.styleExposures))
            ? Number(detailed.styleExposures)
            : hasSignal ? 3 : 0
    };
}

/**
 * Re-select progression after a block using the target phase plus longitudinal evidence. The target
 * phase still owns sets/reps/RIR/rest; this pass only changes progression policy. It applies to exact
 * retained IDs and audit-gated retained replacements, while brand-new exercises keep the selector used
 * during generation.
 */
function applyAdaptiveProgressionStyles(program, previous, request, target, evidence, context) {
    const previousById = new Map(previous.sessions.flatMap(session => session.exercises).map(ex => [ex.exerciseId, ex]));
    const successful = new Set(evidence?.successfulExerciseIds ?? []);
    const fatigueLimited = new Set(evidence?.fatigueLimitedExerciseIds ?? []);
    const techniqueLimited = new Set(evidence?.techniqueLimitedExerciseIds ?? []);
    const blockWeeks = Math.max(1, Number(evidence?.nextBlockWeeks ?? evidence?.blockWeeks ?? 6) || 6);
    const changes = [];
    const sessions = program.sessions.map(session => ({
        ...session,
        exercises: session.exercises.map(exercise => {
            const prior = previousById.get(exercise.exerciseId);
            const def = context.exerciseById(exercise.exerciseId);
            if (!prior || !def)
                return exercise;
            const currentStyle = prior.progressionStyle ?? exercise.progressionStyle ?? 'double';
            const selection = reselectProgressionStyle(def, exercise.role, {
                phase: target,
                previousPhase: previous.phase,
                experience: request.athlete?.experience ?? 'intermediate',
                currentStyle,
                prescription: exercise.prescription,
                blockWeeks,
                // A global manual method remains manual across block review. Auto still re-selects
                // exercise by exercise because explicitStyle ignores the literal 'auto' value.
                requestedStyle: request.preferences?.progressionStyle,
                evidence: progressionEvidenceFor(exercise.exerciseId, evidence, successful, fatigueLimited, techniqueLimited)
            });
            if (selection.style !== exercise.progressionStyle || selection.style !== currentStyle) {
                changes.push({
                    exerciseId: exercise.exerciseId,
                    exerciseName: exercise.name,
                    previousStyle: currentStyle,
                    generatedStyle: exercise.progressionStyle,
                    selectedStyle: selection.style,
                    confidence: selection.confidence,
                    reason: selection.reason
                });
            }
            return {
                ...exercise,
                progressionStyle: selection.style,
                progression: progressionInstruction(selection.style),
                progressionSelection: {
                    source: selection.source,
                    confidence: selection.confidence,
                    reason: selection.reason,
                    previousStyle: currentStyle,
                    // M192: preserve the actual before/after answer as engine-owned metadata. The UI
                    // should never infer a method change from labels or regenerate progression policy.
                    changed: selection.style !== currentStyle
                }
            };
        })
    }));
    return { program: { ...program, sessions }, changes };
}

/**
 * Generate the target phase, then make conservative audit-gated attempts to
 * retain successful exercises from the previous phase. The target phase owns
 * sets/reps/RIR/rest; continuity owns exercise identity/progression history.
 */
export function transitionProgramPhase(previous, request, target, evidence) {
    const nextBlockWeeks = Math.max(1, Number(evidence?.nextBlockWeeks ?? evidence?.blockWeeks ?? 6) || 6);
    // Adaptive transitions must use the same soft-capacity contract as initial program/cycle creation.
    // A valid 60–90 minute Full Body cycle can otherwise enter Strength with a raw generation that
    // violates Full Body coverage even though a nearby capacity candidate passes the normal arbiter.
    const generation = firstPassingCapacityProgram(request, evidence?.capacityConfig, {
        phase: target,
        // New exercises introduced by the target phase must use the actual next-block duration too.
        // Otherwise a four-week block can accidentally start a five-plus-week wave simply because
        // the exercise has no prior history for the adaptive pass to correct.
        blockWeeks: nextBlockWeeks,
        progressionStyle: request.preferences?.progressionStyle
    });
    const effectiveRequest = generation.request;
    const generated = generation.result.program;
    const successful = new Set(evidence.successfulExerciseIds);
    const protectedIds = new Set(evidence.protectedExerciseIds ?? []);
    const replaceIds = new Set(evidence.replaceExerciseIds ?? []);
    const context = createEngineContext(effectiveRequest);
    let program = generated;
    const previousByTargetSession = matchPriorSessionsForTransition(previous.sessions, program.sessions, effectiveRequest, context);
    const transaction = createTransactionalEvaluator(sessions => {
        const events = createTrainingSetEvents(sessions, effectiveRequest.customExercises);
        const muscleLedger = deriveMuscleLedger(events);
        const { audit: _audit, ...withoutAudit } = generated;
        void _audit;
        const base = { ...withoutAudit, sessions, events, muscleLedger };
        return { base, audit: auditProgram(base, effectiveRequest) };
    });
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
                .filter(ex => isCompatibleReplacement(ex, candidate, session.day, effectiveRequest, context))
                .sort((a, b) => Number(protectedIds.has(b.exerciseId)) - Number(protectedIds.has(a.exerciseId)) || a.exerciseId.localeCompare(b.exerciseId));
            for (const previousExercise of alternatives) {
                const proposedSessions = sessionsWithExercise(program, session.id, candidate.exerciseId, previousExercise);
                const checked = transaction.evaluate(proposedSessions);
                if (checked.audit.result === 'pass') {
                    program = { ...checked.base, audit: checked.audit, rationale: [...program.rationale, `${previousExercise.name} was retained across the phase transition because recent performance supported continuity and the target-phase program still passed audit.`] };
                    alreadyUsed.delete(candidate.exerciseId);
                    alreadyUsed.add(previousExercise.exerciseId);
                    break;
                }
            }
        }
    }

    // M189: exercise identity continuity no longer means progression-method continuity. Re-select the
    // method after the target prescription is known, using history only where the exercise actually has it.
    const adaptive = applyAdaptiveProgressionStyles(program, previous, effectiveRequest, target, evidence, context);
    program = adaptive.program;

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
        progressionMethodChanges: adaptive.changes,
        rationale: eligible.length
            ? `${preserved.length}/${eligible.length} recently successful exercises were retained (${preserved.length}/${continuityCapacity} of structurally available continuity slots) while applying the target phase prescription.`
            : 'No recent successful-exercise evidence was available, so the target phase was generated without continuity preferences.'
    };
    const progressionSummary = adaptive.changes.length
        ? `${adaptive.changes.length} retained exercise progression method${adaptive.changes.length === 1 ? ' was' : 's were'} re-selected for the new phase/history evidence.`
        : 'Retained exercise progression methods remained appropriate after the block review.';
    program = withProgramExplainability(program, effectiveRequest, [
        historyDecision('Phase transition continuity', eligible.length ? `${preserved.length}/${continuityCapacity} structurally retainable successful exercises preserved` : 'Target phase generated without prior success evidence', continuity.rationale, { eligibleSuccessfulExercises: eligible.length, retainedSuccessfulExercises: preserved.length, continuityCapacity, capacityAdjustedRetentionRate: Math.round(capacityAdjustedRetentionRate * 1000) / 1000, structuralNewStrengthSlots }, replaced.length ? [`${replaced.length} successful exercise identities changed where the target phase or compatibility constraints required it.`] : ['No successful exercise identity was changed unnecessarily.']),
        historyDecision('Progression method review', adaptive.changes.length ? `${adaptive.changes.length} methods re-selected` : 'No method change required', progressionSummary, { progressionMethodChanges: adaptive.changes.length }, adaptive.changes.slice(0, 4).map(change => `${change.exerciseName}: ${change.previousStyle} → ${change.selectedStyle}. ${change.reason}`))
    ]);
    program = withBlockReviewExplainability(previous, program, effectiveRequest, {
        continuity,
        transitionReason: `The training cycle advanced from ${String(previous.phase || 'the prior focus').replace(/_/g, ' ')} to ${String(target || program.phase || 'the next focus').replace(/_/g, ' ')}.`,
        reasonCodes: ['block:transition:reviewed', 'progression:auto:reviewed']
    });
    return {
        program,
        continuity,
        request: effectiveRequest,
        capacityAdjustment: generation.adjusted ? {
            requestedTargetExercises: generation.requestedTarget,
            effectiveTargetExercises: generation.effectiveTarget,
            requestedMinimumMinutes: generation.requestedMinimumMinutes,
            effectiveMinimumMinutes: generation.effectiveMinimumMinutes
        } : null
    };
}
