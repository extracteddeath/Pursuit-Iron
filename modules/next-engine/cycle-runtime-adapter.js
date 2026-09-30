import { auditProgram } from './arbiter.js';
import { createInitialCycleState, startPhase } from './cycles.js';
import { createEngineContext, createTransactionalEvaluator, auditVector, compareCandidateQuality } from './engine-context.js';
import { createTrainingSetEvents } from './events.js';
import { generateProgram } from './generate.js';
import { deriveMuscleLedger } from './ledgers.js';
import { phasePolicyFor, phaseLabel } from './phase-policy.js';
import { normalizeRequest } from './prescription.js';
import { estimateSessionMinutes, repsForPhase, restForExercise, rirForPhase } from './realizer.js';
import { progressionInstruction, selectProgressionStyle } from './progression-style.js';
import { blocksForCycleTemplate, cycleTemplates, goalForCycleTemplate } from './simulation.js';
import { transitionProgramPhase } from './phase-transition.js';
import { historyDecision, withProgramExplainability } from './explainability.js';
import { analyzeShellHistoryForNextEngine, carryForwardAvoidedExercises } from './workout-history-adapter.js';
import { shellConfigToNextRequest, nextProgramToShellProgram, NextShellAdapterError } from './app-shell-adapter.js';
import { firstPassingCapacityProgram } from './capacity-generation.js';
const phaseGoal = (phase) => phase === 'hypertrophy_accumulation' ? 'hypertrophy' :
    (phase === 'strength_accumulation' || phase === 'intensification' || phase === 'peak') ? 'strength' : 'both';
const clone = (value) => JSON.parse(JSON.stringify(value));
function retargetStatic(previous, request, target, blockWeeks = 6) {
    const normalized = normalizeRequest(request);
    const sourcePolicy = phasePolicyFor(previous.phase);
    const targetPolicy = phasePolicyFor(target);
    const context = createEngineContext(normalized);
    const exerciseMap = context.exerciseMap;
    const sessions = previous.sessions.map(session => {
        const exercises = session.exercises.map(ex => {
            const def = exerciseMap.get(ex.exerciseId);
            if (!def)
                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_UNKNOWN_EXERCISE', `Cannot retarget unknown exercise ${ex.exerciseId}.`);
            const strength = ex.role === 'primary_strength' || ex.role === 'secondary_strength';
            const sourceScale = strength ? sourcePolicy.strengthVolumeMultiplier : sourcePolicy.volumeMultiplier;
            const targetScale = strength ? targetPolicy.strengthVolumeMultiplier : targetPolicy.volumeMultiplier;
            const ratio = sourceScale > 0 ? targetScale / sourceScale : 1;
            const prescription = {
                reps: repsForPhase(def, ex.role, targetPolicy),
                rir: rirForPhase(ex.role, targetPolicy),
                restSeconds: restForExercise(ex.role, def)
            };
            const progressionSelection = selectProgressionStyle(def, ex.role, {
                phase: target,
                experience: normalized.athlete.experience,
                blockWeeks: Math.max(1, Number(blockWeeks) || 6),
                requestedStyle: normalized.preferences?.progressionStyle,
                prescription
            });
            const previousStyle = ex.progressionStyle ?? null;
            return {
                ...ex,
                sets: Math.max(1, Math.round(ex.sets * ratio)),
                prescription,
                progressionStyle: progressionSelection.style,
                progression: progressionInstruction(progressionSelection.style),
                progressionSelection: {
                    source: progressionSelection.source,
                    confidence: progressionSelection.confidence,
                    reason: progressionSelection.reason,
                    previousStyle,
                    changed: previousStyle !== null ? previousStyle !== progressionSelection.style : false
                },
                advancedTechnique: targetPolicy.advancedTechniqueBudget > 0 ? ex.advancedTechnique : undefined
            };
        });
        return { ...session, exercises, estimatedMinutes: estimateSessionMinutes(exercises) };
    });
    const events = createTrainingSetEvents(sessions, normalized.customExercises);
    const muscleLedger = deriveMuscleLedger(events);
    const base = { ...previous, phase: target, sessions, events, muscleLedger, rationale: [...previous.rationale, `Next Cycle structure lock: preserved the exercise skeleton while applying the ${phaseLabel(target).toLowerCase()} prescription.`] };
    const transaction = createTransactionalEvaluator(candidateSessions => {
        const events = createTrainingSetEvents(candidateSessions, normalized.customExercises);
        const muscleLedger = deriveMuscleLedger(events);
        const prepared = { ...base, sessions: candidateSessions, events, muscleLedger };
        const { audit: _audit, ...auditable } = prepared;
        void _audit;
        return { ...prepared, audit: auditProgram(auditable, normalized) };
    });
    const reaudited = (candidate) => {
        const checked = transaction.evaluate(candidate.sessions);
        return { ...candidate, events: checked.events, muscleLedger: checked.muscleLedger, audit: checked.audit };
    };
    let repaired = reaudited(base);
    // Structure-locked cycles may need a small dose top-up when a low-volume Foundation skeleton
    // enters Hypertrophy. Repair dose only; never change exercise identity. Every proposed set is
    // independently re-audited, so time/recovery/volume ceilings can veto the top-up.
    for (let step = 0; step < 24 && repaired.audit.result !== 'pass'; step++) {
        const currentQuality = auditVector(repaired.audit);
        let best;
        let bestQuality = currentQuality;
        let bestDelta = Number.POSITIVE_INFINITY;
        for (let si = 0; si < repaired.sessions.length; si++)
            for (let ei = 0; ei < repaired.sessions[si].exercises.length; ei++) {
                const currentSets = repaired.sessions[si].exercises[ei].sets;
                // Static phase retargeting changes rest periods as well as reps/RIR. A skeleton that fit during
                // hypertrophy can therefore become 1-5 minutes too long in Strength even with identical exercise
                // identities. M69 only searched upward for volume-floor repairs, so a TIME_LIMIT rejection was
                // detectable but literally unrepairable. Search both directions transactionally: trim a few sets
                // when time/recovery is the problem, or add a few when a muscle floor is the problem. We keep at
                // least one set per exercise, re-audit every proposal, and only accept a strictly lower total
                // audit severity. Intermediate proposals may still be reject states when several sessions are
                // over the limit; they are never returned unless the final audit reaches pass.
                const deltas = [-1, -2, -3, 1, 2, 3].filter(delta => currentSets + delta >= 1);
                for (const delta of deltas) {
                    const sessions = repaired.sessions.map((session, sidx) => sidx !== si ? session : { ...session, exercises: session.exercises.map((ex, eidx) => eidx !== ei ? ex : { ...ex, sets: ex.sets + delta }) });
                    sessions[si] = { ...sessions[si], estimatedMinutes: estimateSessionMinutes(sessions[si].exercises) };
                    const proposal = reaudited({ ...repaired, sessions });
                    const magnitude = Math.abs(delta);
                    const proposalQuality = auditVector(proposal.audit, magnitude);
                    if (compareCandidateQuality(proposalQuality, currentQuality) < 0 && compareCandidateQuality(proposalQuality, bestQuality) < 0) {
                        best = proposal;
                        bestQuality = proposalQuality;
                        bestDelta = magnitude;
                    }
                }
            }
        // If a single-set move cannot improve the audit, try an atomic budget-neutral swap. This is
        // essential for short locked-structure phases: adding the missing 0.5-1.0 muscle set can breach
        // the session clock, while trimming room first does not change the under-min finding. Neither
        // half is independently "better", but the pair is. Prefer trimming accessories before secondary
        // strength work, and primary strength only as a last resort; the arbiter still vetoes any unsafe
        // trade.
        if (!best) {
            let bestPenalty = Number.POSITIVE_INFINITY;
            for (let dsi = 0; dsi < repaired.sessions.length; dsi++)
                for (let dei = 0; dei < repaired.sessions[dsi].exercises.length; dei++) {
                    const donor = repaired.sessions[dsi].exercises[dei];
                    if (donor.sets <= 1)
                        continue;
                    const donorPenalty = donor.role === 'primary_strength' ? 20 : donor.role === 'secondary_strength' ? 8 : 0;
                    for (let rsi = 0; rsi < repaired.sessions.length; rsi++)
                        for (let rei = 0; rei < repaired.sessions[rsi].exercises.length; rei++) {
                            if (dsi === rsi && dei === rei)
                                continue;
                            for (const amount of [1, 2]) {
                                if (donor.sets - amount < 1)
                                    continue;
                                const sessions = repaired.sessions.map((session, sidx) => ({ ...session, exercises: session.exercises.map(ex => ({ ...ex })) }));
                                sessions[dsi].exercises[dei].sets -= amount;
                                sessions[rsi].exercises[rei].sets += amount;
                                sessions[dsi] = { ...sessions[dsi], estimatedMinutes: estimateSessionMinutes(sessions[dsi].exercises) };
                                if (rsi !== dsi)
                                    sessions[rsi] = { ...sessions[rsi], estimatedMinutes: estimateSessionMinutes(sessions[rsi].exercises) };
                                const proposal = reaudited({ ...repaired, sessions });
                                const penalty = donorPenalty + amount;
                                const proposalQuality = auditVector(proposal.audit, penalty);
                                if (compareCandidateQuality(proposalQuality, currentQuality) < 0 && compareCandidateQuality(proposalQuality, bestQuality) < 0) {
                                    best = proposal;
                                    bestQuality = proposalQuality;
                                    bestPenalty = penalty;
                                }
                            }
                        }
                }
        }
        if (!best)
            break;
        repaired = best;
    }
    if (repaired.audit.result !== 'pass')
        throw new NextShellAdapterError('NEXT_CYCLE_STATIC_REJECTED', `The locked exercise skeleton could not safely support ${phaseLabel(target)}.`, repaired.audit);
    return withProgramExplainability(repaired, normalized, [historyDecision('Cycle block prescription', `${phaseLabel(previous.phase)} → ${phaseLabel(target)}`, `The exercise skeleton stayed locked while sets, reps, RIR, rest and progression were retargeted for ${phaseLabel(target).toLowerCase()}.`, { fromPhase: previous.phase, toPhase: target, exerciseIdentityLocked: true }, ['Every retargeted session was re-audited', 'Dose top-ups were accepted only when they reduced audit severity'])]);
}
function blockRequest(baseRequest, phase) {
    return { ...clone(baseRequest), goal: { ...clone(baseRequest.goal), type: baseRequest.goal.type } };
}
function legacyBlockConfig(base, phase, weeks, name) {
    return { ...base, name, goal: phaseGoal(phase), weeks, deload: false, percentScheme: null, cyclePeriodization: true, endless: false };
}
function stateForPhase(goal, days, phase) {
    return startPhase(createInitialCycleState(goal, days), phase, goal, days);
}
function attachBlockContext(legacy, next, request, baseRequest, ctx) {
    legacy.engineSource = 'pursuit-next';
    legacy.engineSourceVersion = next.engineVersion;
    legacy.nextEngine = {
        ...legacy.nextEngine,
        request: clone(request), baseRequest: clone(baseRequest), program: clone(next),
        cycleState: clone(stateForPhase(baseRequest.goal.type, baseRequest.schedule.days.length, next.phase)), historySchemaVersion: 1,
        cycleTemplate: clone(ctx)
    };
    legacy.blockLabel = ctx.label;
    legacy.blockNote = ctx.note || '';
    legacy.cycleIndex = ctx.blockIndex;
    return legacy;
}
export function nextCycleTemplatesForShell() {
    return cycleTemplates().map(t => ({
        id: t.id, name: t.name, goal: t.goal === 'mixed' ? 'both' : t.goal,
        tag: t.id === 'powerbuilding' ? 'Size → strength → peak' : t.id === 'strength_peak' ? 'Accumulate → intensify → realize' : t.id === 'foundation' ? 'Learn → grow → strengthen' : 'Two muscle-building waves',
        blurb: t.id === 'powerbuilding' ? 'Build muscle, convert it to strength, then express it with phase-specific sets, reps and effort targets.' :
            t.id === 'strength_peak' ? 'Build a strength base, intensify, then realize it with lower-volume high-specificity work.' :
                t.id === 'foundation' ? 'Build technique and tolerance first, then muscle, then a mixed strength block.' :
                    'Run two hypertrophy waves with the second block informed by actual performance in the first.',
        blocks: t.blocks.map(b => ({ label: b.label, weeks: b.weeks, phase: b.phase, note: `${phaseLabel(b.phase)} · ${b.weeks} weeks` }))
    }));
}
export function generateNextCycleForShell(options) {
    const templateId = options.templateId;
    const template = cycleTemplates().find(t => t.id === templateId);
    if (!template)
        throw new NextShellAdapterError('NEXT_CYCLE_TEMPLATE_INVALID', 'Choose a supported training cycle before building.');
    const specs = blocksForCycleTemplate(templateId);
    const goal = goalForCycleTemplate(templateId);
    const seed = (options.seed ?? Math.max(1, Math.floor(Date.now() % 2147483647))) >>> 0;
    const cid = (options.makeId ?? (() => `next-cycle-${Math.random().toString(36).slice(2, 10)}`))();
    let baseRequest = shellConfigToNextRequest({ ...options.config, goal: goal === 'mixed' ? 'both' : goal }, options.banned ?? [], options.legacyExercises, seed);
    baseRequest.goal = { ...baseRequest.goal, type: goal };

    // Program creation and cycle creation must share the same feasibility contract. The wizard
    // already proves buildability with capacity-aware generation; bypassing it here made valid
    // 60–90 minute Full Body cycles fail even though the equivalent single program passed.
    const entrySpec = specs[0];
    const entryAttempt = firstPassingCapacityProgram(baseRequest, options.config, {
        phase: entrySpec.phase,
        blockWeeks: entrySpec.weeks,
        progressionStyle: baseRequest.preferences?.progressionStyle
    });
    baseRequest = entryAttempt.request;
    const firstProgram = entryAttempt.result.program;
    const normalized = normalizeRequest(baseRequest);
    const blocks = [];
    let previous;
    for (let i = 0; i < specs.length; i++) {
        const spec = specs[i];
        let next;
        if (!previous) {
            next = firstProgram;
        }
        else if (options.adaptBetweenBlocks) {
            const ids = previous.sessions.flatMap(s => s.exercises.map(e => e.exerciseId));
            const protectedIds = previous.sessions.flatMap(s => s.exercises.filter(e => e.role === 'primary_strength' || e.role === 'secondary_strength').map(e => e.exerciseId));
            next = transitionProgramPhase(previous, normalized, spec.phase, {
                successfulExerciseIds: ids,
                protectedExerciseIds: protectedIds,
                nextBlockWeeks: spec.weeks
            }).program;
        }
        else {
            next = retargetStatic(previous, baseRequest, spec.phase, spec.weeks);
        }
        if (next.audit.result !== 'pass')
            throw new NextShellAdapterError('NEXT_CYCLE_BLOCK_REJECTED', `Pursuit Engine ${next.engineVersion} rejected ${spec.label}.`, next.audit);
        const cfg = legacyBlockConfig(options.config, spec.phase, spec.weeks, `${options.config.name || cycleTemplates().find(t => t.id === templateId)?.name || 'Training Cycle'} · ${spec.label}`);
        const legacy = nextProgramToShellProgram(next, cfg, options.legacyExercises, options.makeId);
        legacy.cycleId = cid;
        attachBlockContext(legacy, next, blockRequest(baseRequest, spec.phase), baseRequest, { templateId, plannedIndex: i, blockIndex: i, label: spec.label, weeks: spec.weeks, phase: spec.phase, preview: i > 0, adaptBetweenBlocks: !!options.adaptBetweenBlocks });
        blocks.push(legacy);
        previous = next;
    }
    const cycle = {
        id: cid, name: options.config.name || template.name, templateId, createdAt: Date.now(), seed, engineV: 33,
        engineSource: 'pursuit-next', engineSourceVersion: blocks[0]?.engineSourceVersion || '0.62.0', adaptExercises: !!options.adaptBetweenBlocks,
        blockIds: blocks.map(b => b.id), blockMeta: specs.map((s, i) => ({ label: s.label, note: `${phaseLabel(s.phase)} · ${s.weeks} weeks`, goal: phaseGoal(s.phase), weeks: s.weeks, phase: s.phase, id: blocks[i].id, plannedIndex: i, preview: i > 0 })),
        activeBlock: 0, advance: 'manual', onComplete: 'end', startedAt: null, done: false,
        nextEngineCycle: {
            schemaVersion: 1, templateId, goal, adaptBetweenBlocks: !!options.adaptBetweenBlocks,
            baseRequest: clone(baseRequest), baseConfig: clone(options.config), plannedBlocks: clone(specs), recoveryInsertions: 0,
            ...(entryAttempt.adjusted ? { capacityAdjustment: {
                policy: 'soft-capacity-band',
                session: options.config?.session ?? 's60',
                requestedTargetExercises: entryAttempt.requestedTarget,
                effectiveTargetExercises: entryAttempt.effectiveTarget,
                requestedMinimumMinutes: entryAttempt.requestedMinimumMinutes,
                effectiveMinimumMinutes: entryAttempt.effectiveMinimumMinutes,
                maxMinutes: baseRequest.schedule?.days?.[0]?.maxMinutes
            } } : {})
        }
    };
    return { cycle, blocks, baseRequest };
}

export function convertProgramToNextCycleForShell(options) {
    const current = options?.program;
    if (!current || current.engineSource !== 'pursuit-next')
        throw new NextShellAdapterError('NEXT_CYCLE_CONVERSION_UNSUPPORTED', 'Only a current Pursuit program can be turned into a training cycle.');
    if (current.cycleId)
        throw new NextShellAdapterError('NEXT_CYCLE_CONVERSION_ALREADY_LINKED', 'This program already belongs to a training cycle.');
    if (current.config?.endless)
        throw new NextShellAdapterError('NEXT_CYCLE_CONVERSION_ENDLESS', 'Endless programs do not have a block boundary. Switch to a fixed-length block before creating a cycle.');
    const source = current.nextEngine?.program;
    const sourceRequest = current.nextEngine?.baseRequest ?? current.nextEngine?.request;
    if (!source || !sourceRequest)
        throw new NextShellAdapterError('NEXT_CYCLE_CONVERSION_SOURCE_MISSING', 'This program is missing the engine snapshot needed to build future cycle blocks safely.');
    const templateId = options.templateId;
    const template = cycleTemplates().find(t => t.id === templateId);
    if (!template)
        throw new NextShellAdapterError('NEXT_CYCLE_TEMPLATE_INVALID', 'Choose a supported training cycle before converting this program.');
    const specs = blocksForCycleTemplate(templateId);
    const goal = goalForCycleTemplate(templateId);
    const sourceIndex = specs.findIndex(spec => spec.phase === source.phase);
    const currentWeeks = Math.max(1, Math.round(Number(current.config?.weeks || current.weeks) || 4));
    const currentSpec = sourceIndex >= 0
        ? { ...specs[sourceIndex], weeks: currentWeeks }
        : { label: current.blockLabel || phaseLabel(source.phase) || 'Current block', weeks: currentWeeks, phase: source.phase };
    const futureSpecs = sourceIndex >= 0 ? specs.slice(sourceIndex + 1) : specs;
    if (!futureSpecs.length)
        throw new NextShellAdapterError('NEXT_CYCLE_CONVERSION_NO_FUTURE_BLOCKS', 'That cycle path has no later phase after the current program. Choose a different cycle path.');
    const planned = [currentSpec, ...futureSpecs];
    const seed = (options.seed ?? current.seed ?? Math.max(1, Math.floor(Date.now() % 2147483647))) >>> 0;
    const makeId = options.makeId ?? (() => `next-cycle-${Math.random().toString(36).slice(2, 10)}`);
    const cid = makeId();
    const cycleName = String(options.name || current.name || template.name || 'Training Cycle').trim() || 'Training Cycle';
    const baseRequest = clone(sourceRequest);
    baseRequest.goal = { ...(baseRequest.goal ?? {}), type: goal };
    const normalized = normalizeRequest(baseRequest);
    const baseConfig = {
        ...(clone(current.config || {})),
        name: cycleName,
        goal: goal === 'mixed' ? 'both' : goal,
        endless: false,
        cyclePeriodization: true,
        cycleTemplate: templateId,
        cycleAdapt: !!options.adaptBetweenBlocks
    };

    const currentLegacy = clone(current);
    currentLegacy.cycleId = cid;
    currentLegacy.cycleIndex = 0;
    currentLegacy.blockLabel = currentSpec.label || phaseLabel(source.phase) || 'Current block';
    currentLegacy.blockNote = `${phaseLabel(source.phase)} · ${currentWeeks} weeks · converted from standalone program`;
    currentLegacy.config = { ...(currentLegacy.config || {}), endless: false, cyclePeriodization: true };
    attachBlockContext(
        currentLegacy,
        source,
        current.nextEngine?.request ?? blockRequest(baseRequest, currentSpec.phase),
        baseRequest,
        {
            templateId,
            plannedIndex: 0,
            blockIndex: 0,
            label: currentLegacy.blockLabel,
            weeks: currentWeeks,
            phase: source.phase,
            preview: false,
            adaptBetweenBlocks: !!options.adaptBetweenBlocks,
            convertedFromStandalone: true
        }
    );

    const blocks = [currentLegacy];
    let previous = source;
    for (let i = 1; i < planned.length; i++) {
        const spec = planned[i];
        let next;
        if (options.adaptBetweenBlocks) {
            const ids = previous.sessions.flatMap(session => session.exercises.map(ex => ex.exerciseId));
            const protectedIds = previous.sessions.flatMap(session => session.exercises
                .filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength')
                .map(ex => ex.exerciseId));
            // M193 converted-cycle preview uses the duration of THIS upcoming block.
            next = transitionProgramPhase(previous, normalized, spec.phase, {
                successfulExerciseIds: ids,
                protectedExerciseIds: protectedIds,
                nextBlockWeeks: spec.weeks
            }).program;
        }
        else {
            // M193 converted-cycle locked preview also reselects against this block's duration.
            next = retargetStatic(previous, baseRequest, spec.phase, spec.weeks);
        }
        if (next.audit.result !== 'pass')
            throw new NextShellAdapterError('NEXT_CYCLE_BLOCK_REJECTED', `Pursuit Engine ${next.engineVersion} rejected ${spec.label}.`, next.audit);
        const cfg = legacyBlockConfig(baseConfig, spec.phase, spec.weeks, `${cycleName} · ${spec.label}`);
        const legacy = nextProgramToShellProgram(next, cfg, options.legacyExercises, makeId);
        legacy.cycleId = cid;
        attachBlockContext(legacy, next, blockRequest(baseRequest, spec.phase), baseRequest, {
            templateId,
            plannedIndex: i,
            blockIndex: i,
            label: spec.label,
            weeks: spec.weeks,
            phase: spec.phase,
            preview: true,
            adaptBetweenBlocks: !!options.adaptBetweenBlocks,
            convertedFromStandalone: true
        });
        blocks.push(legacy);
        previous = next;
    }

    const cycle = {
        id: cid,
        name: cycleName,
        templateId,
        createdAt: Date.now(),
        seed,
        engineV: 33,
        engineSource: 'pursuit-next',
        engineSourceVersion: currentLegacy.engineSourceVersion || source.engineVersion || '0.62.0',
        adaptExercises: !!options.adaptBetweenBlocks,
        blockIds: blocks.map(block => block.id),
        blockMeta: planned.map((spec, i) => ({
            label: spec.label,
            note: i === 0 ? `${phaseLabel(source.phase)} · ${currentWeeks} weeks · existing program` : `${phaseLabel(spec.phase)} · ${spec.weeks} weeks`,
            goal: phaseGoal(spec.phase),
            weeks: i === 0 ? currentWeeks : spec.weeks,
            phase: i === 0 ? source.phase : spec.phase,
            id: blocks[i].id,
            plannedIndex: i,
            preview: i > 0
        })),
        activeBlock: 0,
        advance: 'manual',
        onComplete: 'end',
        startedAt: Date.now(),
        done: false,
        nextEngineCycle: {
            schemaVersion: 1,
            templateId,
            goal,
            adaptBetweenBlocks: !!options.adaptBetweenBlocks,
            baseRequest: clone(baseRequest),
            baseConfig: clone(baseConfig),
            plannedBlocks: clone(planned),
            recoveryInsertions: 0,
            convertedFromProgramId: current.id
        }
    };
    return { cycle, currentProgram: currentLegacy, blocks: blocks.slice(1), allBlocks: blocks, baseRequest };
}

function requestForAdvance(cycle, current, target, weeks, analysis) {
    const base = (cycle?.nextEngineCycle?.baseRequest ?? current?.nextEngine?.baseRequest);
    if (!base)
        throw new NextShellAdapterError('NEXT_CYCLE_REQUEST_MISSING', 'Next cycle is missing its immutable request snapshot.');
    let request = carryForwardAvoidedExercises(clone(base), current?.nextEngine?.request, analysis);
    if (target === 'recovery' || analysis.classification === 'fatigue_limited' || analysis.recovery.status === 'deload_recommended') {
        request.schedule = { days: request.schedule.days.map(day => ({ ...day, targetExercises: day.targetExercises === undefined ? undefined : Math.max(2, day.targetExercises - Math.max(1, Math.ceil(day.targetExercises * .2))) })) };
        request.preferences = { ...(request.preferences ?? {}), responseCapacityScale: target === 'recovery' ? .72 : .82 };
    }
    void weeks;
    return request;
}
function buildAdaptedBlock(current, cycle, target, weeks, label, analysis, legacyExercises, makeId, existingId) {
    const source = current?.nextEngine?.program;
    if (!source)
        throw new NextShellAdapterError('NEXT_CYCLE_SOURCE_MISSING', 'Current cycle block is missing its engine source snapshot.');
    const request = requestForAdvance(cycle, current, target, weeks, analysis);
    const normalized = normalizeRequest(request);
    let next;
    if (cycle?.nextEngineCycle?.adaptBetweenBlocks) {
        next = transitionProgramPhase(source, normalized, target, {
            successfulExerciseIds: analysis.successfulExerciseIds,
            protectedExerciseIds: analysis.protectedExerciseIds,
            replaceExerciseIds: analysis.replaceExerciseIds,
            techniqueLimitedExerciseIds: analysis.techniqueLimitedExerciseIds,
            fatigueLimitedExerciseIds: analysis.fatigueLimitedExerciseIds,
            progressionEvidenceByExercise: analysis.progressionEvidenceByExercise,
            nextBlockWeeks: weeks
        }).program;
    }
    else {
        next = retargetStatic(source, request, target, weeks);
    }
    if (next.audit.result !== 'pass')
        throw new NextShellAdapterError('NEXT_CYCLE_ADAPT_REJECTED', `Pursuit Engine ${next.engineVersion} could not safely prepare ${label}.`, next.audit);
    const baseCfg = cycle?.nextEngineCycle?.baseConfig ?? current.config;
    const cfg = legacyBlockConfig(baseCfg, target, weeks, `${cycle.name} · ${label}`);
    const idFactory = existingId ? () => existingId : makeId;
    const legacy = nextProgramToShellProgram(next, cfg, legacyExercises, idFactory);
    legacy.cycleId = cycle.id;
    const plannedIndex = target === 'recovery' ? null : (cycle.blockMeta?.find((m) => m.id === existingId)?.plannedIndex ?? null);
    attachBlockContext(legacy, next, request, cycle.nextEngineCycle.baseRequest, { templateId: cycle.templateId, plannedIndex, blockIndex: cycle.activeBlock + 1, label, weeks, phase: target, preview: false, adaptBetweenBlocks: !!cycle.adaptExercises, adaptedFrom: current.id });
    legacy.nextEngine.priorBlock = { programId: current.id, phase: source.phase, workouts: analysis.workoutCount, classification: analysis.classification, recovery: analysis.recovery.status };
    legacy.nextEngine.historySummary = { positive: analysis.positiveDecisionCount, negative: analysis.negativeDecisionCount, successfulExercises: analysis.successfulExerciseIds.length, ignoredLegacyExerciseIds: analysis.ignoredLegacyExerciseIds };
    return legacy;
}
export function advanceNextCycleForShell(options) {
    const cycle = options.cycle, current = options.activeProgram;
    if (cycle?.engineSource !== 'pursuit-next' || current?.engineSource !== 'pursuit-next')
        throw new NextShellAdapterError('NOT_NEXT_ENGINE_CYCLE', 'This cycle is not owned by Pursuit Next.');
    const active = Number(cycle.activeBlock) || 0;
    if (cycle.blockIds?.[active] !== current.id)
        throw new NextShellAdapterError('NEXT_CYCLE_ACTIVE_MISMATCH', 'The active cycle block does not match the program being completed.');
    const analysis = analyzeShellHistoryForNextEngine(current, options.history, options.legacyExercises);
    if (!analysis.readyForNextBlock)
        throw new NextShellAdapterError('NEXT_CYCLE_NOT_READY', `Pursuit Engine ${current.engineSourceVersion || 'Next'} needs more completed work before this block can advance.`, { workouts: analysis.workoutCount, recovery: analysis.recovery, cycleState: analysis.cycleState });
    const planned = cycle?.nextEngineCycle?.plannedBlocks ?? [];
    const currentMeta = cycle.blockMeta?.[active] ?? {};
    const isRecovery = currentMeta.phase === 'recovery';
    const nextPlannedIndex = isRecovery ? currentMeta.resumePlannedIndex : Number(currentMeta.plannedIndex) + 1;
    if (!Number.isInteger(nextPlannedIndex) || nextPlannedIndex >= planned.length) {
        return { cycle: { ...cycle, done: true, completedAt: Date.now() }, analysis, insertedRecovery: false, done: true };
    }
    const targetSpec = planned[nextPlannedIndex];
    const fatigue = analysis.classification === 'fatigue_limited' || analysis.recovery.status === 'deload_recommended';
    if (fatigue && !isRecovery) {
        const recovery = buildAdaptedBlock(current, cycle, 'recovery', 1, 'Recovery', analysis, options.legacyExercises, options.makeId);
        const insertAt = active + 1;
        const blockIds = [...cycle.blockIds];
        blockIds.splice(insertAt, 0, recovery.id);
        const blockMeta = [...cycle.blockMeta];
        blockMeta.splice(insertAt, 0, { id: recovery.id, label: 'Recovery', note: 'Inserted from real workout-history fatigue evidence before the next planned phase.', goal: 'both', weeks: 1, phase: 'recovery', plannedIndex: null, resumePlannedIndex: nextPlannedIndex, preview: false, inserted: true });
        recovery.cycleIndex = insertAt;
        const updated = { ...cycle, blockIds, blockMeta, activeBlock: insertAt, nextEngineCycle: { ...cycle.nextEngineCycle, recoveryInsertions: Number(cycle.nextEngineCycle?.recoveryInsertions || 0) + 1 } };
        return { cycle: updated, nextProgram: recovery, analysis, insertedRecovery: true, done: false };
    }
    if (isRecovery && (analysis.recovery.status !== 'normal' || analysis.cycleState.status !== 'review_eligible')) {
        throw new NextShellAdapterError('NEXT_CYCLE_RECOVERY_NOT_READY', 'Recovery has not produced enough favorable evidence to resume the planned cycle.', { recovery: analysis.recovery, cycleState: analysis.cycleState });
    }
    // Locate the prebuilt preview for the target planned index. A recovery insertion shifts its array index.
    const targetMetaIndex = cycle.blockMeta.findIndex((m, i) => i > active && m.plannedIndex === nextPlannedIndex);
    if (targetMetaIndex < 0)
        throw new NextShellAdapterError('NEXT_CYCLE_PREVIEW_MISSING', 'The next planned cycle block is missing its preview slot.');
    const existingId = cycle.blockIds[targetMetaIndex];
    if (options.history.some(entry => entry?.programId === existingId))
        throw new NextShellAdapterError('NEXT_CYCLE_PREVIEW_HAS_HISTORY', 'The upcoming Next cycle preview already has workout history, so it cannot be replaced safely. Remove or preserve that premature history before advancing.');
    const adapted = buildAdaptedBlock(current, cycle, targetSpec.phase, targetSpec.weeks, targetSpec.label, analysis, options.legacyExercises, options.makeId, existingId);
    adapted.cycleIndex = targetMetaIndex;
    const blockMeta = cycle.blockMeta.map((m, i) => i === targetMetaIndex ? { ...m, preview: false, adaptedFrom: current.id } : m);
    return { cycle: { ...cycle, activeBlock: targetMetaIndex, blockMeta }, nextProgram: adapted, replaceProgramId: existingId, analysis, insertedRecovery: false, done: false };
}
