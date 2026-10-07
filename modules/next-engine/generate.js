import { ENGINE_VERSION } from './config.js';
import { initialPhaseForGoal, phasePolicyFor } from './phase-policy.js';
import { createTrainingSetEvents } from './events.js';
import { deriveMuscleLedger } from './ledgers.js';
import { allocateTraining } from './allocator.js';
import { createMusclePrescriptions, createStrengthClaims, normalizeRequest } from './prescription.js';
import { finalizePlannedSession, optimizeSetupAwareSessionSequence, progressionForExercise, repsForPhase, restForExercise, rirForPhase, realizeSessions, realizeStrengthAnchors } from './realizer.js';
import { proposeExplicitFocusRepair } from './focus-intent.js';
import { evaluateFunctionalCoverage } from './functional-coverage.js';
import { proposeFunctionalCoverageRepairs } from './functional-coverage-repair.js';
import { createEngineContext, createTransactionalEvaluator, auditVector, compareCandidateQuality } from './engine-context.js';
import { armCoverageBias, evaluateArmCoverage } from './arm-coverage.js';
import { INTENT_MUSCLES, solveTopology } from './topology.js';
import { auditProgram } from './arbiter.js';
import { assessWeeklyRecovery, optimizeWeeklyRecovery } from './weekly-recovery.js';
import { proposeRecoveryRedistributions } from './recovery-realization.js';
import { buildProgramExplainability } from './explainability.js';
import { reconcileRecoverableDose } from './dose-reconciliation.js';
export function generateProgram(requestInput, options) {
    const normalized = normalizeRequest(requestInput);
    const requestedPhase = options?.phase;
    const phase = requestedPhase == null ? initialPhaseForGoal(normalized.goal.type)
        : (typeof requestedPhase === 'string' ? requestedPhase.trim().toLowerCase() : '');
    const basePolicy = phasePolicyFor(phase);
    if (!basePolicy)
        throw new Error(`Unsupported training phase: ${requestedPhase}.`);
    const rawBlockWeeks = options?.blockWeeks;
    if (rawBlockWeeks !== undefined && (!Number.isFinite(rawBlockWeeks) || rawBlockWeeks <= 0))
        throw new Error('Block weeks must be a finite positive number.');
    const rawStyle = options?.progressionStyle;
    if (rawStyle !== undefined && typeof rawStyle !== 'string')
        throw new Error('Progression style must be a string.');
    const optionStyle = rawStyle === undefined ? undefined : rawStyle.trim().toLowerCase();
    if (optionStyle !== undefined && !['auto', 'double', 'dynamic', 'ladder', 'linear', 'wave', 'e1rm'].includes(optionStyle))
        throw new Error(`Unsupported progression style: ${rawStyle}.`);
    const policy = {
        ...basePolicy,
        // M189: block duration is part of progression-method selection. A four-week
        // intensification block should not start a wave that needs five+ weeks to justify itself.
        blockWeeks: rawBlockWeeks === undefined ? undefined : Math.max(1, Math.round(rawBlockWeeks)),
        requestedProgressionStyle: optionStyle ?? normalized.preferences?.progressionStyle
    };
    // Session-time cards describe available capacity, not a quota that must be filled. Experience and
    // phase determine how much of that capacity is likely productive. Advanced accumulation can use the
    // full target; novice/intermediate and lower-fatigue phases deliberately cap exercise count lower.
    const experienceCapacityMultiplier = normalized.athlete.experience === 'novice' ? .75 : normalized.athlete.experience === 'intermediate' ? .9 : 1;
    const experienceExerciseCap = normalized.athlete.experience === 'novice' ? 6 : normalized.athlete.experience === 'intermediate' ? 8 : 12;
    const responseCapacityMultiplier = normalized.preferences?.responseCapacityScale ?? 1;
    const capacityMultiplier = policy.sessionCapacityMultiplier * experienceCapacityMultiplier * responseCapacityMultiplier;
    const split = normalized.preferences.lockedSplit ?? normalized.preferences.preferredSplit;
    // Full-body structures have a three-region identity (push + pull + lower). Phase tapering may cut
    // sets and accessories aggressively, but an exercise-count target below three is internally
    // contradictory and forces later structural repairs to look like capacity violations.
    const structuralExerciseFloor = ['full_body', 'full_body_patterns', 'strength_fb', 'texas', 'academy_prep'].includes(split ?? '') ? 3 : 2;
    const request = capacityMultiplier === 1 && normalized.athlete.experience === 'advanced' ? normalized : {
        ...normalized,
        schedule: { days: normalized.schedule.days.map(day => ({
                ...day,
                targetExercises: day.targetExercises === undefined ? undefined : Math.max(structuralExerciseFloor, Math.min(experienceExerciseCap, Math.round(day.targetExercises * capacityMultiplier)))
            })) }
    };
    const context = createEngineContext(request);
    const muscles = createMusclePrescriptions(request, phase);
    const strength = createStrengthClaims(request, phase);
    const provisionalAllocation = allocateTraining(request, muscles, strength, phase);
    const provisionalTopology = solveTopology(request, provisionalAllocation.allocations);
    const hasStrengthAnchors = provisionalAllocation.allocations.some(a => a.kind === 'lift');
    const strengthBaseline = hasStrengthAnchors ? realizeStrengthAnchors(provisionalTopology.sessions, request, phase, {
        blockWeeks: policy.blockWeeks, requestedProgressionStyle: policy.requestedProgressionStyle
    }) : undefined;
    const allocation = strengthBaseline
        ? allocateTraining(request, muscles, strength, phase, { strengthBaseline })
        : provisionalAllocation;
    // Muscle dose can change after actual anchor accounting, but split identity/day contracts cannot:
    // otherwise a different topology could select different strength variants and reintroduce the same
    // circular projection error. Rebuild the chosen seed with residual muscle allocations only.
    const topology = strengthBaseline
        ? solveTopology(request, allocation.allocations, { seed: provisionalTopology.seed })
        : provisionalTopology;
    const realized = realizeSessions(topology.sessions, request, allocation.targetDose, allocation.directTargetDose, phase, {
        ...(strengthBaseline ? { strengthAnchors: strengthBaseline.anchors } : {}),
        blockWeeks: policy.blockWeeks,
        requestedProgressionStyle: policy.requestedProgressionStyle
    });
    const assembleRaw = (sessions) => {
        const recovery = optimizeWeeklyRecovery(sessions, request);
        const scheduled = recovery.sessions;
        const events = createTrainingSetEvents(scheduled, request.customExercises);
        const muscleLedger = deriveMuscleLedger(events);
        const base = {
            id: `program-${request.seed}`,
            engineVersion: ENGINE_VERSION,
            seed: request.seed,
            phase,
            split: { family: topology.family, displayName: topology.displayName },
            sessions: scheduled,
            events,
            muscleLedger,
            rationale: [...allocation.decisions, ...topology.rationale],
            customExerciseSnapshots: request.customExercises.length ? request.customExercises.map(ex => structuredClone(ex)) : undefined
        };
        return { base, audit: auditProgram(base, request, { skipRecoveryRealization: true }) };
    };
    const transaction = createTransactionalEvaluator(assembleRaw);
    const assemble = sessions => transaction.evaluate(sessions);
    // Explicit focus repair is transactional per session. A named Squat/Bench/Deadlift/Press day should
    // keep the corresponding lift when the swap/addition is compatible, but one difficult repair is never
    // allowed to veto valid repairs on the rest of the week or turn a valid program into repair/reject.
    let accepted = [...realized];
    let assembled = assemble(accepted);
    accepted = [...assembled.base.sessions];
    for (let index = 0; index < accepted.length; index++) {
        const proposal = proposeExplicitFocusRepair(accepted[index], request, phase);
        if (proposal === accepted[index])
            continue;
        const next = [...accepted];
        next[index] = proposal;
        const checked = assemble(next);
        if (checked.audit.result === 'pass') {
            accepted = [...checked.base.sessions];
            assembled = checked;
        }
    }
    // M65 functional coverage repair is transactional just like explicit-focus repair. Selection scoring
    // normally provides complementary functions up front; this layer closes the small set of cases where
    // a valid high-dose week crosses the diversity threshold only after protected strength/focus work is
    // realized. Never weaken the audit: propose conservative changes, then accept only a full-program
    // audit improvement with no new critical findings.
    const exerciseMap = context.exerciseMap;
    const auditCounts = (audit) => {
        const vector = auditVector(audit);
        return { critical: vector.critical, major: vector.major, warnings: vector.warning };
    };
    for (let guard = 0; guard < 8; guard++) {
        const coverage = evaluateFunctionalCoverage(assembled.base.sessions, request, exerciseMap);
        const target = coverage.findings.find(f => f.severity === 'major');
        if (!target)
            break;
        const baselineCounts = auditCounts(assembled.audit);
        let best = null;
        for (const proposal of proposeFunctionalCoverageRepairs(assembled.base.sessions, target, request, phase)) {
            const checked = assemble(proposal);
            const counts = auditCounts(checked.audit);
            if (counts.critical > baselineCounts.critical || counts.major > baselineCounts.major)
                continue;
            const candidateCoverage = evaluateFunctionalCoverage(checked.base.sessions, request, exerciseMap);
            const remaining = candidateCoverage.findings.length;
            const targetGone = !candidateCoverage.findings.some(f => f.code === target.code);
            if (!targetGone)
                continue;
            const improves = counts.major < baselineCounts.major || (counts.major === baselineCounts.major && remaining < coverage.findings.length);
            if (!improves)
                continue;
            const quality = auditVector(checked.audit, remaining);
            if (!best || compareCandidateQuality(quality, best.quality) < 0) {
                best = { checked, quality, critical: counts.critical, major: counts.major, warnings: counts.warnings, coverageFindings: remaining };
            }
            if (counts.critical === 0 && counts.major === 0 && remaining === 0)
                break;
        }
        if (!best)
            break;
        assembled = best.checked;
        accepted = [...assembled.base.sessions];
    }
    // Weekday reassignment (M56) can only solve recovery conflicts that are removable by permutation.
    // When the best valid weekday map still leaves a hard collision, test conservative dose-neutral
    // assistance transfers. Every accepted move must improve the recovery objective AND pass the same
    // full runtime audit as an ordinary generated program, so split identity, time, MEV/MRV, sequencing,
    // equipment and strength-anchor contracts remain authoritative.
    const nonRecoveryFindingCounts = (audit) => {
        const counts = new Map();
        for (const finding of audit.findings) {
            if (finding.code === 'COACH_RECOVERY_REALIZATION_AVOIDABLE')
                continue;
            const key = `${finding.severity}:${finding.code}`;
            counts.set(key, (counts.get(key) ?? 0) + 1);
        }
        return counts;
    };
    const warningProfileNoWorse = (candidate, baseline) => {
        const allowed = nonRecoveryFindingCounts(baseline), actual = nonRecoveryFindingCounts(candidate);
        for (const [key, count] of actual)
            if (count > (allowed.get(key) ?? 0))
                return false;
        return true;
    };
    for (let guard = 0; guard < 8; guard++) {
        const current = assessWeeklyRecovery(assembled.base.sessions, request);
        // M64 also polishes non-hard adjacent recovery pressure. The weekly day assignment is already
        // globally optimal for the current session contents; if a dose-neutral accessory transfer can
        // reduce the remaining recovery score without worsening any audit dimension, take it.
        if (current.hardCollisions === 0 && current.score <= .0001)
            break;
        let best = null;
        const proposals = proposeRecoveryRedistributions(assembled.base.sessions, request);
        for (const proposal of proposals) {
            const checked = assemble(proposal.sessions);
            if (checked.audit.result !== 'pass' || !warningProfileNoWorse(checked.audit, assembled.audit))
                continue;
            const recovery = assessWeeklyRecovery(checked.base.sessions, request);
            const improves = recovery.hardCollisions < current.hardCollisions || (recovery.hardCollisions === current.hardCollisions && recovery.score < current.score - .0001);
            if (!improves)
                continue;
            if (!best || recovery.hardCollisions < best.hard || (recovery.hardCollisions === best.hard && recovery.score < best.score - .0001))
                best = { checked, hard: recovery.hardCollisions, score: recovery.score };
            if (recovery.hardCollisions === 0 && recovery.score === 0)
                break;
        }
        if (!best)
            break;
        assembled = best.checked;
        accepted = [...assembled.base.sessions];
    }
    // Arm-function reconciliation must run after every late program-level mutation, not only inside
    // realization. M69 repaired initial exercise selection, but recovery/coverage transactions could
    // still leave an adaptive Strength/Peak block with wrist flexion and no wrist extension (or one curl
    // bias only). The arbiter correctly rejected those blocks. Close the loop transactionally here:
    // prefer a one-set dose-neutral swap within the same muscle, then a one-set add only when it fits the
    // session clock. Every proposal is rebuilt and re-audited; no finding is suppressed or downgraded.
    const exerciseCatalog = context.exerciseCatalog;
    const armBiasForCode = (code) => code === 'ARM_BICEPS_BIAS_MISSING' ? 'biceps_bias' :
        code === 'ARM_BRACHIALIS_BIAS_MISSING' || code === 'ARM_BRACHIORADIALIS_UNDERSERVED' ? 'brachialis_bias' :
            code === 'WRIST_FLEXION_MISSING' ? 'wrist_flexion' :
                code === 'WRIST_EXTENSION_MISSING' ? 'wrist_extension' : null;
    const armMuscle = (bias) => bias === 'biceps_bias' || bias === 'brachialis_bias' ? 'biceps' : 'forearms';
    const equipmentEligibleForSession = (def, session) => {
        if (!context.equipmentEligible(def, session.day))
            return false;
        const day = context.scheduleDay(session.day);
        if (def.flags.barbell) {
            const max = day.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay;
            const used = session.exercises.filter(ex => exerciseMap.get(ex.exerciseId)?.flags.barbell).length;
            if (used >= max)
                return false;
        }
        return true;
    };
    const plannedArmExercise = (def) => ({
        exerciseId: def.id, name: def.name, role: 'hypertrophy_isolation', sets: 1,
        prescription: { reps: repsForPhase(def, 'hypertrophy_isolation', policy), rir: rirForPhase('hypertrophy_isolation', policy), restSeconds: restForExercise('hypertrophy_isolation', def) },
        progression: progressionForExercise(def, 'hypertrophy_isolation', policy, request.athlete.experience)
    });
    const armRepairProposals = (sessions, targetBias) => {
        const muscle = armMuscle(targetBias);
        const defs = exerciseCatalog.filter(def => armCoverageBias(def) === targetBias && !request.preferences.avoidedExercises?.includes(def.id))
            .sort((a, b) => (a.flags.barbell ? 1 : 0) - (b.flags.barbell ? 1 : 0) || b.suitability.hypertrophy - a.suitability.hypertrophy || a.setupCost - b.setupCost || a.id.localeCompare(b.id));
        const intentRank = (intent) => {
            const order = muscle === 'forearms' ? ['arms', 'shoulders_arms', 'limbs', 'pull', 'upper', 'back_day', 'full', 'strength_full'] : ['arms', 'shoulders_arms', 'pull', 'back_day', 'upper', 'limbs', 'full', 'strength_full'];
            const i = order.indexOf(intent);
            return i < 0 ? 99 : i;
        };
        const destinationIndexes = sessions.map((session, index) => ({ session, index }))
            .filter(x => (INTENT_MUSCLES[x.session.intent] ?? INTENT_MUSCLES.full).includes(muscle))
            .sort((a, b) => intentRank(a.session.intent) - intentRank(b.session.intent) || (b.session.maxMinutes - b.session.estimatedMinutes) - (a.session.maxMinutes - a.session.estimatedMinutes) || a.index - b.index);
        const proposals = [];
        for (const { session, index } of destinationIndexes) {
            for (const def of defs) {
                if (!equipmentEligibleForSession(def, session) || session.exercises.some(ex => ex.exerciseId === def.id))
                    continue;
                const added = plannedArmExercise(def);
                // First preserve weekly dose by moving one set from another direct exercise for the same arm
                // muscle. For wrists this naturally converts flexion-only work into a flexion/extension pair.
                const donors = session.exercises.map((exercise, exerciseIndex) => ({ exercise, exerciseIndex, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def && x.exercise.sets > 1)
                    .filter(x => {
                    const primary = Object.entries(x.def.muscles).find(([, c]) => c.role === 'primary')?.[0];
                    return primary === muscle && armCoverageBias(x.def) !== targetBias;
                })
                    .sort((a, b) => (a.exercise.role === 'primary_strength' ? 10 : a.exercise.role === 'secondary_strength' ? 5 : 0) - (b.exercise.role === 'primary_strength' ? 10 : b.exercise.role === 'secondary_strength' ? 5 : 0) || b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                for (const donor of donors) {
                    const exercises = session.exercises.map((ex, i) => i === donor.exerciseIndex ? { ...ex, sets: ex.sets - 1 } : { ...ex });
                    exercises.push(added);
                    const finalized = finalizePlannedSession({ ...session, exercises, estimatedMinutes: 0 }, request);
                    if (finalized.estimatedMinutes > session.maxMinutes)
                        continue;
                    const next = sessions.map((s, i) => i === index ? finalized : s);
                    proposals.push(next);
                }
                // If no dose-neutral donor exists, allow the minimum one-set exposure only when genuine clock
                // capacity remains. The full arbiter still enforces MRV/MEV and every other global contract.
                const finalized = finalizePlannedSession({ ...session, exercises: [...session.exercises, added], estimatedMinutes: 0 }, request);
                if (finalized.estimatedMinutes <= session.maxMinutes)
                    proposals.push(sessions.map((s, i) => i === index ? finalized : s));
            }
        }
        return proposals;
    };
    for (let guard = 0; guard < 8; guard++) {
        const arm = evaluateArmCoverage(assembled.base.sessions, request, exerciseMap);
        const target = arm.findings.find(f => f.severity === 'major');
        if (!target)
            break;
        const bias = armBiasForCode(target.code);
        if (!bias)
            break;
        const baselineCounts = auditCounts(assembled.audit);
        let best = null;
        let bestRemaining = arm.findings.length;
        for (const proposal of armRepairProposals(assembled.base.sessions, bias)) {
            const checked = assemble(proposal);
            const counts = auditCounts(checked.audit);
            if (counts.critical > baselineCounts.critical || counts.major > baselineCounts.major)
                continue;
            const nextArm = evaluateArmCoverage(checked.base.sessions, request, exerciseMap);
            if (nextArm.findings.some(f => f.code === target.code && f.severity === 'major'))
                continue;
            const improves = counts.major < baselineCounts.major || (counts.major === baselineCounts.major && nextArm.findings.length < arm.findings.length);
            if (!improves)
                continue;
            if (!best || counts.major < auditCounts(best.audit).major || nextArm.findings.length < bestRemaining) {
                best = checked;
                bestRemaining = nextArm.findings.length;
            }
            if (counts.critical === 0 && counts.major === 0 && bestRemaining === 0)
                break;
        }
        if (!best)
            break;
        assembled = best;
        accepted = [...assembled.base.sessions];
    }
    // M83 applies setup-aware sequencing only after every transactional repair is finished. Keeping
    // it out of intermediate generation prevents exercise order from feeding back into pairing, dose,
    // recovery, or repair decisions; the final transform is therefore prescription-neutral.
    const orderedSessions = assembled.base.sessions.map(session => optimizeSetupAwareSessionSequence(session, exerciseMap, request));
    // M181 reconciles secondary/collateral muscle credits only after every additive repair has finished.
    // This keeps the allocator's upper regions authoritative without teaching earlier repair passes to
    // game a second ledger. Reduced sessions are re-finalized and then audited normally below.
    const doseReconciliation = reconcileRecoverableDose(orderedSessions, request, phase);
    const reconciledSessions = doseReconciliation.sessions.map(session => {
        const finalized = finalizePlannedSession({ ...session, estimatedMinutes: 0 }, request);
        // M182: M181 reconciliation can remove movements after the first setup-aware ordering pass.
        // Re-optimize the reduced final session so generic re-finalization cannot reintroduce avoidable
        // station changes. This transform is prescription-neutral and remains subject to the final audit.
        return optimizeSetupAwareSessionSequence(finalized, exerciseMap, request);
    });
    const orderedEvents = createTrainingSetEvents(reconciledSessions, request.customExercises);
    const finalBase = { ...assembled.base, sessions: reconciledSessions, events: orderedEvents, muscleLedger: deriveMuscleLedger(orderedEvents) };
    const finalAudit = auditProgram(finalBase, request);
    const auditableProgram = { ...finalBase, audit: finalAudit };
    const program = { ...auditableProgram, explainability: buildProgramExplainability(auditableProgram, request) };
    return { program, diagnostics: { allocationDecisions: allocation.decisions, allocationDecisionLog: allocation.decisionLog, allocationMetrics: allocation.metrics, topologyRationale: topology.rationale, topologyCandidates: topology.candidates, doseReconciliationAdjustments: doseReconciliation.adjustments, remainingDoseOverflow: doseReconciliation.remainingOverflow } };
}
