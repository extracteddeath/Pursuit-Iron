import { auditProgram } from './arbiter.js';
import { SUPPORTED_SPLIT_FAMILIES, splitSupportsTrainingDays } from './topology.js';
import { createExerciseMap } from './exercise-db.js';
import { createInitialCycleState, startPhase } from './cycles.js';
import { createEngineContext, createTransactionalEvaluator, auditVector, compareCandidateQuality } from './engine-context.js';
import { createTrainingSetEvents } from './events.js';
import { deriveMuscleLedger } from './ledgers.js';
import { phasePolicyFor, phaseLabel } from './phase-policy.js';
import { normalizeRequest, createMusclePrescriptions } from './prescription.js';
import { estimateSessionMinutes, repsForPhase, restForExercise, rirForPhase } from './realizer.js';
import { progressionInstruction, selectProgressionStyle, continuationProgressionStyle } from './progression-style.js';
import { blocksForCycleTemplate, cycleTemplates, goalForCycleTemplate } from './simulation.js';
import { transitionProgramPhase } from './phase-transition.js';
import { historyDecision, withProgramExplainability } from './explainability.js';
import { analyzeShellHistoryForNextEngine, carryForwardAvoidedExercises } from './workout-history-adapter.js';
import { requestWithAthleteResponse } from './athlete-response.js';
import { shellConfigToNextRequest, nextProgramToShellProgram, NextShellAdapterError } from './app-shell-adapter.js';
import { repairShellVolume, reconcileLockedCycleWeekOverflows, finalizeGeneratedShellVolume, captureShellBaseProgram } from './volume-repair.js';
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
                requestedStyle: continuationProgressionStyle(normalized.preferences?.progressionStyle, ex),
                prescription
            });
            const previousStyle = ex.progressionStyle ?? null;
            const workingSetCap = normalized.preferences.volumeApproach === 'minimalist' ? 3 : ex.workingSetCap;
            return {
                ...ex,
                ...(workingSetCap !== undefined ? { workingSetCap } : {}),
                sets: Math.min(workingSetCap ?? Infinity, Math.max(1, Math.round(ex.sets * ratio))),
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
                                const sessions = repaired.sessions.map(session => ({ ...session, exercises: session.exercises.map(ex => ({ ...ex })) }));
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
function blockRequest(baseRequest) {
    return { ...clone(baseRequest), goal: { ...clone(baseRequest.goal), type: baseRequest.goal.type } };
}
const BLOCK_LIFTS = new Set(['bench_press', 'back_squat', 'deadlift', 'overhead_press']);
function changesWeek(spec) {
    return Boolean(spec?.split || spec?.days || spec?.priorityLift);
}
function blockConfigForSpec(config, spec) {
    return { ...config, ...(spec?.split ? { split: spec.split } : {}),
        ...(spec?.days ? { days: spec.days } : {}) };
}
function requestForBlock(baseRequest, baseConfig, spec, legacyExercises = []) {
    const request = blockRequest(baseRequest);
    if (!changesWeek(spec))
        return request;
    const cfg = blockConfigForSpec(baseConfig, spec);
    if (!splitSupportsTrainingDays(cfg.split, Number(cfg.days)))
        throw new NextShellAdapterError('NEXT_CYCLE_SPLIT_DAYS_INCOMPATIBLE',
            `The ${String(cfg.split).replaceAll('_', ' ')} split cannot be scheduled for ${cfg.days} training days. Change this block's split or training-day count.`);
    if (spec.days)
        request.schedule = shellConfigToNextRequest(cfg, [], legacyExercises, request.seed).schedule;
    request.preferences = { ...request.preferences,
        ...(spec.split ? { lockedSplit: spec.split, preferredSplit: spec.split } : {}) };
    request.goal = { ...request.goal, type: phaseGoal(spec.phase) === 'both' ? 'mixed' : phaseGoal(spec.phase) };
    if (spec.priorityLift) {
        request.goal.liftPriorities = { ...(request.goal.liftPriorities ?? {}), [spec.priorityLift]: 'primary' };
        request.goal.liftFrequency = { [spec.priorityLift]: spec.frequency };
    } else {
        // A previous block's frequency must not leak into an unrelated phase.
        delete request.goal.liftFrequency;
    }
    return request;
}
function checkRequestedPractice(program, spec, request) {
    if (!spec?.priorityLift) return;
    const catalog = createExerciseMap(request.customExercises);
    const actual = program.sessions.filter(session => session.exercises.some(ex =>
        ['primary_strength','secondary_strength'].includes(ex.role)
        && (catalog.get(ex.exerciseId)?.liftSpecificity?.[spec.priorityLift] ?? 0) > .45)).length;
    if (actual < spec.frequency)
        throw new NextShellAdapterError('NEXT_CYCLE_LIFT_FREQUENCY_UNMET',
            `Cannot fit ${spec.frequency} weekly ${spec.priorityLift.replaceAll('_', ' ')} exposures into this block's days, time, split and recovery constraints (planned ${actual}). Choose fewer exposures, more training days or another split.`);
}
const totalSets = program => program.sessions.reduce((n, session) =>
    n + session.exercises.reduce((sets, ex) => sets + (Number(ex.sets) || 0), 0), 0);
function protectStrengthEntry(previous, next, spec, request) {
    if (previous?.phase !== 'hypertrophy_accumulation' || spec.phase !== 'strength_accumulation')
        return next;
    const before = totalSets(previous), floor = Math.ceil(before * .5);
    if (before < 16 || totalSets(next) >= floor) return next;
    // A converted custom roster can be more faithful than a newly generated strength skeleton.
    // Try the audited same-roster prescription before refusing an extreme volume cliff.
    if (!changesWeek(spec)) {
        try {
            const locked = retargetStatic(previous, request, spec.phase, spec.weeks);
            if (locked.audit.result === 'pass' && totalSets(locked) >= floor)
                return locked;
        } catch { /* The normal refusal below gives a useful adjustment route. */ }
    }
    throw new NextShellAdapterError('NEXT_CYCLE_STRENGTH_VOLUME_CLIFF',
        `Strength would fall from ${before} to ${totalSets(next)} weekly working sets. This is too large a reduction for a normal accumulation block. Change split or session capacity; a taper-sized drop belongs in Peak, not Strength.`);
}
// Automatic cycle topology is separate from exercise adaptation. Preserve explicit
// block overrides, training days, session limits, equipment and athlete restrictions.
// A bounded, deterministic candidate search only compares fully audited programs.
const PHASE_SPLIT_CANDIDATES = Object.freeze({
    hypertrophy_accumulation: ['ulppl','pplul','upper_lower','full_body','ppl','full_body_patterns','torso_limbs'],
    strength_accumulation: ['upper_lower','sbd_power','full_body','ulppl','pplul','strength_fb','full_body_patterns','phul'],
    intensification: ['upper_lower','sbd_power','full_body','ulppl','strength_fb','phul','full_body_patterns'],
    peak: ['sbd_power','upper_lower','full_body','strength_fb','full_body_patterns','ulppl'],
    mixed_accumulation: ['ulppl','pplul','phul','upper_lower','full_body','full_body_patterns','sbd_power'],
    foundation: ['full_body','upper_lower','ulppl','pplul','full_body_patterns']
});
export function cycleSplitCandidates(spec, config, enabled) {
    const days = Number(spec.days || config.days);
    if (!enabled || spec.split)
        return [{ ...spec }];
    const preferred = [config.split, ...(PHASE_SPLIT_CANDIDATES[spec.phase] || ['full_body','upper_lower'])];
    const seen = new Set();
    return preferred.filter(split => split && !seen.has(split) && (seen.add(split), splitSupportsTrainingDays(split, days)))
        .slice(0, 6).map(split => ({ ...spec, split }));
}
function cycleProgramScore(program, request, phase, previous, startingSplit) {
    const normalized = normalizeRequest(request);
    const prescriptions = createMusclePrescriptions(normalized, phase);
    let received = 0, possible = 0;
    for (const p of prescriptions) {
        const priority = normalized.goal.musclePriorities[p.muscle];
        // Upper-body and lower-body muscle priorities count; maintenance muscles must not
        // displace the important work merely because they are easy to fill.
        const weight = priority === 'primary' ? 3 : priority === 'specialization' ? 2.6
            : priority === 'high' ? 1.9 : priority === 'normal' ? 1 : .12;
        const target = Math.max(.25, Number(p.preferred) || 0);
        received += weight * Math.min(1, (program.muscleLedger?.[p.muscle]?.fractionalSets || 0) / target);
        possible += weight;
    }
    const catalog = createExerciseMap(normalized.customExercises);
    const liftFrequencies = {};
    for (const lift of BLOCK_LIFTS) {
        liftFrequencies[lift] = program.sessions.filter(session => session.exercises.some(ex =>
            ['primary_strength','secondary_strength'].includes(ex.role)
            && (catalog.get(ex.exerciseId)?.liftSpecificity?.[lift] || 0) > .45)).length;
    }
    const focusStrength = ['strength_accumulation','intensification','peak','mixed_accumulation'].includes(phase);
    const targetLifts = Object.entries(normalized.goal.liftPriorities || {})
        .filter(([,priority]) => priority !== 'maintenance').map(([lift]) => lift);
    const desiredPractice = phase === 'peak' ? 1 : phase === 'mixed_accumulation' ? 1 : 2;
    const liftQuality = targetLifts.length
        ? targetLifts.reduce((n,lift) => n + Math.min(1, (liftFrequencies[lift] || 0) / (normalized.goal.liftFrequency?.[lift] || desiredPractice)), 0) / targetLifts.length
        : 0;
    const sets = totalSets(program);
    const totalMinutes = program.sessions.reduce((n,session)=>n+session.estimatedMinutes,0);
    const capacity = program.sessions.reduce((n,session)=>n+session.maxMinutes,0) || 1;
    const usefulDensity = Math.min(1, totalMinutes / (capacity * .72));
    const continuity = startingSplit && program.split?.family === startingSplit ? 1 : 0;
    // Muscle and goal coverage dominate. Strength gets specific practice reward;
    // hypertrophy rewards adequate productive volume, not filling time indiscriminately.
    const goalScore = 100 * (possible ? received / possible : 0)
        + (focusStrength ? 22 * liftQuality : 0)
        + (phase === 'hypertrophy_accumulation' ? 8 * usefulDensity : 3 * usefulDensity)
        + 2 * continuity;
    return { score: Math.round(goalScore * 100) / 100, weeklySets: sets,
        muscleTargetCoverage: Math.round(100 * (possible ? received / possible : 0)),
        liftFrequencies };
}
function chooseOptimizedCycleBlock(spec, config, enabled, buildCandidate) {
    const candidates = cycleSplitCandidates(spec, config, enabled);
    let winner = null, firstError = null, passing = 0;
    for (const candidateSpec of candidates) {
        try {
            const output = buildCandidate(candidateSpec);
            if (output.next?.audit?.result !== 'pass') continue;
            passing++;
            const score = cycleProgramScore(output.next, output.request, spec.phase, output.previous, config.split);
            const value = { ...output, spec: candidateSpec, quality: score };
            if (!winner || value.quality.score > winner.quality.score + .0001)
                winner = value;
        } catch (error) {
            firstError ||= error;
        }
    }
    if (!winner)
        throw firstError || new NextShellAdapterError('NEXT_CYCLE_SPLIT_SEARCH_REJECTED',
            'No safe weekly structure was found for this block. Try more time, fewer lift exposures or another split.');
    const automatic = enabled && !spec.split;
    winner.decision = {
        mode: automatic ? 'goal_optimized' : 'fixed',
        selectedSplit: winner.next.split.family,
        candidateCount: candidates.length,
        passingCandidates: passing,
        ...winner.quality,
        reason: automatic
            ? `Compared ${candidates.length} compatible weekly structures for ${phaseLabel(spec.phase).toLowerCase()}, retained ${passing} audited plans, and selected the highest scoring plan for muscle coverage, relevant lift practice and training capacity.`
            : 'Kept the selected weekly structure and verified it against the training constraints.'
    };
    return winner;
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
/* B893: Custom cycles are real, independently realized phase blocks, not a renamed preset.
   Keep the permitted phase keys narrow, ordered and validated before ANY generation or state write. */
export const CUSTOM_CYCLE_PHASES = Object.freeze([
    { phase: 'hypertrophy_accumulation', label: 'Hypertrophy' },
    { phase: 'strength_accumulation', label: 'Strength' },
    { phase: 'mixed_accumulation', label: 'Powerbuilding' },
    { phase: 'intensification', label: 'Intensification' },
    { phase: 'peak', label: 'Peak' },
    { phase: 'foundation', label: 'Foundation' }
]);
export function customCycleSpecs(blocks, { minBlocks = 2, maxBlocks = 8, maxWeeks = 104 } = {}) {
    if (!Array.isArray(blocks) || blocks.length < minBlocks || blocks.length > maxBlocks)
        throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_BLOCK_COUNT', `Choose between ${minBlocks} and ${maxBlocks} cycle blocks.`);
    const valid = new Set(CUSTOM_CYCLE_PHASES.map(x => x.phase));
    const planned = blocks.map((block, i) => {
        const phase = typeof block?.phase === 'string' ? block.phase : '';
        const weeks = Number(block?.weeks);
        if (!valid.has(phase))
            throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_PHASE_INVALID', `Block ${i + 1} has an unsupported training phase.`);
        if (!Number.isSafeInteger(weeks) || weeks < 1 || weeks > 24)
            throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_WEEKS_INVALID', `Block ${i + 1} must run 1–24 whole weeks.`);
        const phaseName = CUSTOM_CYCLE_PHASES.find(x => x.phase === phase).label;
        const label = typeof block?.label === 'string' && block.label.trim() ? block.label.trim().slice(0, 40) : phaseName;
        const split = block?.split == null || block.split === '' ? undefined : block.split;
        if (split !== undefined && !SUPPORTED_SPLIT_FAMILIES.includes(split))
            throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_SPLIT_INVALID', `Block ${i + 1} has an unsupported weekly split.`);
        const days = block?.days == null || block.days === '' ? undefined : Number(block.days);
        if (days !== undefined && (!Number.isInteger(days) || days < 2 || days > 6))
            throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_DAYS_INVALID', `Block ${i + 1} must use 2–6 training days.`);
        const priorityLift = block?.priorityLift || undefined;
        if (priorityLift !== undefined && !BLOCK_LIFTS.has(priorityLift))
            throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_LIFT_INVALID', `Block ${i + 1} has an unsupported priority lift.`);
        const frequency = priorityLift ? Number(block.frequency) : undefined;
        if (priorityLift && (!Number.isInteger(frequency) || frequency < 1 || frequency > 3 || (days && frequency > days)))
            throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_FREQUENCY_INVALID', `Block ${i + 1} needs 1–3 lift exposures, no more than its training days.`);
        return { phase, weeks, label, ...(split ? { split } : {}), ...(days ? { days } : {}),
            ...(priorityLift ? { priorityLift, frequency } : {}) };
    });
    if (planned.reduce((sum, block) => sum + block.weeks, 0) > maxWeeks)
        throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_LENGTH_INVALID', `These blocks must total at most ${maxWeeks} weeks; the whole cycle can run at most 104 weeks.`);
    return planned;
}
export function customFutureCycleSpecs(blocks, currentWeeks) {
    if (!Number.isSafeInteger(currentWeeks) || currentWeeks < 1 || currentWeeks >= 104)
        throw new NextShellAdapterError('NEXT_CYCLE_CUSTOM_LENGTH_INVALID', 'The existing block needs a valid fixed duration within the 104-week cycle limit.');
    return customCycleSpecs(blocks, { minBlocks: 1, maxBlocks: 7, maxWeeks: 104 - currentWeeks });
}
export function generateNextCycleForShell(options) {
    const templateId = options.templateId;
    const isCustom = templateId === 'custom';
    const template = isCustom ? { id: 'custom', name: 'Custom cycle' } : cycleTemplates().find(t => t.id === templateId);
    if (!template)
        throw new NextShellAdapterError('NEXT_CYCLE_TEMPLATE_INVALID', 'Choose a supported training cycle before building.');
    // Legacy conversions generate only their future blocks; the shell then prepends the
    // untouched existing program. No disposable generated block stands in for that program.
    const specs = isCustom
        ? (options.existingBlockWeeks != null ? customFutureCycleSpecs(options.blocks, options.existingBlockWeeks) : customCycleSpecs(options.blocks))
        : blocksForCycleTemplate(templateId);
    const goal = isCustom
        ? (options.config?.goal === 'strength' ? 'strength' : options.config?.goal === 'hypertrophy' ? 'hypertrophy' : 'mixed')
        : goalForCycleTemplate(templateId);
    const seed = (options.seed ?? Math.max(1, Math.floor(Date.now() % 2147483647))) >>> 0;
    const cid = (options.makeId ?? (() => `next-cycle-${Math.random().toString(36).slice(2, 10)}`))();
    let baseRequest = shellConfigToNextRequest({ ...options.config, goal: goal === 'mixed' ? 'both' : goal }, options.banned ?? [], options.legacyExercises, seed);
    baseRequest.goal = { ...baseRequest.goal, type: goal };

    // Program creation and cycle creation must share the same feasibility contract. The wizard
    // already proves buildability with capacity-aware generation; bypassing it here made valid
    // 60–90 minute Full Body cycles fail even though the equivalent single program passed.
    const entrySpec = specs[0];
    const entryRequest = requestForBlock(baseRequest, options.config, entrySpec, options.legacyExercises);
    const entryAttempt = firstPassingCapacityProgram(entryRequest, blockConfigForSpec(options.config, entrySpec), {
        phase: entrySpec.phase,
        blockWeeks: entrySpec.weeks,
        progressionStyle: baseRequest.preferences?.progressionStyle
    });
    const firstEffectiveRequest = entryAttempt.request;
    const firstProgram = entryAttempt.result.program;
    const blocks = [];
    let previous;
    for (let i = 0; i < specs.length; i++) {
        const spec = specs[i];
        const targetRequest = i === 0 ? firstEffectiveRequest : requestForBlock(baseRequest, options.config, spec, options.legacyExercises);
        const normalized = normalizeRequest(targetRequest);
        let next;
        if (!previous) {
            next = firstProgram;
        }
        else if (options.adaptBetweenBlocks || changesWeek(spec)) {
            const ids = previous.sessions.flatMap(s => s.exercises.map(e => e.exerciseId));
            const protectedIds = previous.sessions.flatMap(s => s.exercises.filter(e => e.role === 'primary_strength' || e.role === 'secondary_strength').map(e => e.exerciseId));
            const transitioned = transitionProgramPhase(previous, normalized, spec.phase, {
                successfulExerciseIds: ids,
                protectedExerciseIds: protectedIds,
                nextBlockWeeks: spec.weeks,
                capacityConfig: blockConfigForSpec(options.config, spec)
            });
            next = transitioned.program;
        }
        else {
            next = retargetStatic(previous, targetRequest, spec.phase, spec.weeks);
        }
        if (previous) next = protectStrengthEntry(previous, next, spec, targetRequest);
        checkRequestedPractice(next, spec, targetRequest);
        if (next.audit.result !== 'pass')
            throw new NextShellAdapterError('NEXT_CYCLE_BLOCK_REJECTED', `Pursuit Engine ${next.engineVersion} rejected ${spec.label}.`, next.audit);
        const cfg = legacyBlockConfig(blockConfigForSpec(options.config, spec), spec.phase, spec.weeks, `${options.config.name || cycleTemplates().find(t => t.id === templateId)?.name || 'Training Cycle'} · ${spec.label}`);
        let legacy = nextProgramToShellProgram(next, cfg, options.legacyExercises, options.makeId);
        legacy.cycleId = cid;
        attachBlockContext(legacy, next, targetRequest, baseRequest, { templateId, plannedIndex: i, blockIndex: i, label: spec.label, weeks: spec.weeks, phase: spec.phase, preview: i > 0, adaptBetweenBlocks: !!(options.adaptBetweenBlocks || changesWeek(spec)) });
        // Static cycles lock exercise identity across blocks, but phase retargeting is complete
        // before the shell expands base sets into exact work-week cells. Reconcile those exact public
        // regional doses after materialization without adding, removing, or swapping any movement.
        if (!options.adaptBetweenBlocks && !changesWeek(spec) && i > 0) {
            const rosterBefore = legacy.days.map(day => [...day.exercises]);
            const baseRepair = repairShellVolume(legacy, options.legacyExercises, { preserveRoster: true });
            const weeklyRepair = reconcileLockedCycleWeekOverflows(baseRepair.program, options.legacyExercises);
            legacy = weeklyRepair.program;
            const rosterAfter = legacy.days.map(day => [...day.exercises]);
            if (JSON.stringify(rosterAfter) !== JSON.stringify(rosterBefore))
                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_ROSTER_CHANGED', 'Locked cycle volume repair changed the exercise roster.');
            const beforeOver = baseRepair.before.issues.filter(issue => issue.status === 'over');
            const afterOver = weeklyRepair.after.issues.filter(issue => issue.status === 'over');
            if (afterOver.length)
                throw new NextShellAdapterError('NEXT_CYCLE_STATIC_VOLUME_REJECTED', `The locked exercise skeleton could not safely fit the ${phaseLabel(spec.phase).toLowerCase()} accessory-volume ceiling.`, afterOver);
            next = legacy.nextEngine.program;
            legacy.nextEngine.lockedCycleVolumeRepair = {
                status: baseRepair.changed || weeklyRepair.changed ? 'success' : 'unchanged',
                changed: baseRepair.changed || weeklyRepair.changed,
                rosterPreserved: true,
                beforeOver: beforeOver.map(issue => ({ region: issue.region, actual: issue.v, ceiling: issue.mrv })),
                afterOver: []
            };
        }
        legacy = finalizeGeneratedShellVolume(legacy, options.legacyExercises);
        blocks.push(legacy);
        previous = legacy.nextEngine.program;
    }
    const cycle = {
        id: cid, name: options.config.name || template.name, templateId, createdAt: Date.now(), seed, engineV: 33,
        engineSource: 'pursuit-next', engineSourceVersion: blocks[0]?.engineSourceVersion || '0.62.0', adaptExercises: !!options.adaptBetweenBlocks,
        blockIds: blocks.map(b => b.id), blockMeta: specs.map((s, i) => ({ label: s.label, note: `${phaseLabel(s.phase)} · ${s.weeks} weeks`, goal: phaseGoal(s.phase), weeks: s.weeks, phase: s.phase, ...(s.split ? { split: s.split } : {}), ...(s.days ? { days: s.days } : {}), ...(s.priorityLift ? { priorityLift: s.priorityLift, frequency: s.frequency } : {}), id: blocks[i].id, plannedIndex: i, preview: i > 0 })),
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
                maxMinutes: firstEffectiveRequest.schedule?.days?.[0]?.maxMinutes
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
    const source = captureShellBaseProgram(current, options.legacyExercises);
    const sourceRequest = current.nextEngine?.baseRequest ?? current.nextEngine?.request;
    if (!source || !sourceRequest)
        throw new NextShellAdapterError('NEXT_CYCLE_CONVERSION_SOURCE_MISSING', 'This program is missing the engine snapshot needed to build future cycle blocks safely.');
    const templateId = options.templateId;
    const isCustom = templateId === 'custom';
    const template = isCustom ? { id: 'custom', name: 'Custom cycle' } : cycleTemplates().find(t => t.id === templateId);
    if (!template)
        throw new NextShellAdapterError('NEXT_CYCLE_TEMPLATE_INVALID', 'Choose a supported training cycle before converting this program.');
    const currentWeeks = Math.max(1, Math.round(Number(current.config?.weeks || current.weeks) || 4));
    const specs = isCustom ? customFutureCycleSpecs(options.blocks, currentWeeks) : blocksForCycleTemplate(templateId);
    const goal = isCustom ? sourceRequest.goal.type : goalForCycleTemplate(templateId);
    const sourceIndex = specs.findIndex(spec => spec.phase === source.phase);
    const currentSpec = !isCustom && sourceIndex >= 0
        ? { ...specs[sourceIndex], weeks: currentWeeks }
        : { label: current.blockLabel || phaseLabel(source.phase) || 'Current block', weeks: currentWeeks, phase: source.phase };
    const futureSpecs = !isCustom && sourceIndex >= 0 ? specs.slice(sourceIndex + 1) : specs;
    if (!futureSpecs.length)
        throw new NextShellAdapterError('NEXT_CYCLE_CONVERSION_NO_FUTURE_BLOCKS', 'That cycle path has no later phase after the current program. Choose a different cycle path.');
    const planned = [currentSpec, ...futureSpecs];
    const seed = (options.seed ?? current.seed ?? Math.max(1, Math.floor(Date.now() % 2147483647))) >>> 0;
    const makeId = options.makeId ?? (() => `next-cycle-${Math.random().toString(36).slice(2, 10)}`);
    const cid = makeId();
    const cycleName = String(options.name || current.name || template.name || 'Training Cycle').trim() || 'Training Cycle';
    const baseRequest = clone(sourceRequest);
    baseRequest.goal = { ...(baseRequest.goal ?? {}), type: goal };
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
        const targetRequest = requestForBlock(baseRequest, baseConfig, spec, options.legacyExercises);
        const normalized = normalizeRequest(targetRequest);
        let next;
        if (options.adaptBetweenBlocks || changesWeek(spec)) {
            const ids = previous.sessions.flatMap(session => session.exercises.map(ex => ex.exerciseId));
            const protectedIds = previous.sessions.flatMap(session => session.exercises
                .filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength')
                .map(ex => ex.exerciseId));
            // M193 converted-cycle preview uses the duration of THIS upcoming block.
            next = transitionProgramPhase(previous, normalized, spec.phase, {
                successfulExerciseIds: ids,
                protectedExerciseIds: protectedIds,
                nextBlockWeeks: spec.weeks,
                capacityConfig: blockConfigForSpec(baseConfig, spec)
            }).program;
        }
        else {
            // M193 converted-cycle locked preview also reselects against this block's duration.
            next = retargetStatic(previous, targetRequest, spec.phase, spec.weeks);
        }
        next = protectStrengthEntry(previous, next, spec, targetRequest);
        checkRequestedPractice(next, spec, targetRequest);
        if (next.audit.result !== 'pass')
            throw new NextShellAdapterError('NEXT_CYCLE_BLOCK_REJECTED', `Pursuit Engine ${next.engineVersion} rejected ${spec.label}.`, next.audit);
        const cfg = legacyBlockConfig(blockConfigForSpec(baseConfig, spec), spec.phase, spec.weeks, `${cycleName} · ${spec.label}`);
        let legacy = nextProgramToShellProgram(next, cfg, options.legacyExercises, makeId);
        legacy.cycleId = cid;
        attachBlockContext(legacy, next, targetRequest, baseRequest, {
            templateId,
            plannedIndex: i,
            blockIndex: i,
            label: spec.label,
            weeks: spec.weeks,
            phase: spec.phase,
            preview: true,
            adaptBetweenBlocks: !!(options.adaptBetweenBlocks || changesWeek(spec)),
            convertedFromStandalone: true
        });
        legacy = finalizeGeneratedShellVolume(legacy, options.legacyExercises);
        blocks.push(legacy);
        previous = legacy.nextEngine.program;
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
            ...(spec.split ? { split: spec.split } : {}), ...(spec.days ? { days: spec.days } : {}),
            ...(spec.priorityLift ? { priorityLift: spec.priorityLift, frequency: spec.frequency } : {}),
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

function requestForAdvance(cycle, current, target, weeks, analysis, spec) {
    const base = (cycle?.nextEngineCycle?.baseRequest ?? current?.nextEngine?.baseRequest);
    if (!base)
        throw new NextShellAdapterError('NEXT_CYCLE_REQUEST_MISSING', 'Next cycle is missing its immutable request snapshot.');
    const phaseRequest = target === 'recovery' ? clone(base)
        : requestForBlock(base, cycle?.nextEngineCycle?.baseConfig ?? current.config, spec ?? { phase: target }, []);
    let request = requestWithAthleteResponse(carryForwardAvoidedExercises(phaseRequest, current?.nextEngine?.request, analysis), analysis.athleteResponse);
    if (target === 'recovery' || analysis.classification === 'fatigue_limited' || analysis.recovery.status === 'deload_recommended') {
        request.schedule = { days: request.schedule.days.map(day => ({ ...day, targetExercises: day.targetExercises === undefined ? undefined : Math.max(2, day.targetExercises - Math.max(1, Math.ceil(day.targetExercises * .2))) })) };
        request.preferences = { ...(request.preferences ?? {}), responseCapacityScale: target === 'recovery' ? .72 : .82 };
    }
    void weeks;
    return request;
}
function buildAdaptedBlock(current, cycle, target, weeks, label, analysis, legacyExercises, makeId, existingId, spec) {
    const source = captureShellBaseProgram(current, legacyExercises);
    if (!source)
        throw new NextShellAdapterError('NEXT_CYCLE_SOURCE_MISSING', 'Current cycle block is missing its engine source snapshot.');
    const request = requestForAdvance(cycle, current, target, weeks, analysis, spec);
    const normalized = normalizeRequest(request);
    let next;
    if (cycle?.nextEngineCycle?.adaptBetweenBlocks || changesWeek(spec)) {
        next = transitionProgramPhase(source, normalized, target, {
            successfulExerciseIds: analysis.successfulExerciseIds,
            protectedExerciseIds: analysis.protectedExerciseIds,
            replaceExerciseIds: analysis.replaceExerciseIds,
            techniqueLimitedExerciseIds: analysis.techniqueLimitedExerciseIds,
            fatigueLimitedExerciseIds: analysis.fatigueLimitedExerciseIds,
            progressionEvidenceByExercise: analysis.progressionEvidenceByExercise,
            nextBlockWeeks: weeks,
            capacityConfig: blockConfigForSpec(cycle?.nextEngineCycle?.baseConfig ?? current.config, spec)
        }).program;
    }
    else {
        next = retargetStatic(source, request, target, weeks);
    }
    if (target !== 'recovery' && spec) next = protectStrengthEntry(source, next, spec, request);
    checkRequestedPractice(next, spec, request);
    if (next.audit.result !== 'pass')
        throw new NextShellAdapterError('NEXT_CYCLE_ADAPT_REJECTED', `Pursuit Engine ${next.engineVersion} could not safely prepare ${label}.`, next.audit);
    const baseCfg = cycle?.nextEngineCycle?.baseConfig ?? current.config;
    const cfg = legacyBlockConfig(blockConfigForSpec(baseCfg, spec), target, weeks, `${cycle.name} · ${label}`);
    const idFactory = existingId ? () => existingId : makeId;
    const legacy = nextProgramToShellProgram(next, cfg, legacyExercises, idFactory);
    legacy.cycleId = cycle.id;
    const plannedIndex = target === 'recovery' ? null : (cycle.blockMeta?.find((m) => m.id === existingId)?.plannedIndex ?? null);
    attachBlockContext(legacy, next, request, cycle.nextEngineCycle.baseRequest, { templateId: cycle.templateId, plannedIndex, blockIndex: cycle.activeBlock + 1, label, weeks, phase: target, preview: false, adaptBetweenBlocks: !!cycle.adaptExercises, adaptedFrom: current.id });
    legacy.nextEngine.priorBlock = { programId: current.id, phase: source.phase, workouts: analysis.workoutCount, classification: analysis.classification, recovery: analysis.recovery.status };
    legacy.nextEngine.historySummary = { positive: analysis.positiveDecisionCount, negative: analysis.negativeDecisionCount, successfulExercises: analysis.successfulExerciseIds.length, ignoredLegacyExerciseIds: analysis.ignoredLegacyExerciseIds };
    return finalizeGeneratedShellVolume(legacy, legacyExercises);
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
    const adapted = buildAdaptedBlock(current, cycle, targetSpec.phase, targetSpec.weeks, targetSpec.label, analysis, options.legacyExercises, options.makeId, existingId, targetSpec);
    adapted.cycleIndex = targetMetaIndex;
    const blockMeta = cycle.blockMeta.map((m, i) => i === targetMetaIndex ? { ...m, preview: false, adaptedFrom: current.id } : m);
    return { cycle: { ...cycle, activeBlock: targetMetaIndex, blockMeta }, nextProgram: adapted, replaceProgramId: existingId, analysis, insertedRecovery: false, done: false };
}
