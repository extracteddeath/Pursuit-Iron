import { createExerciseMap } from './exercise-db.js';
import { createMusclePrescriptions, createStrengthClaims, productiveTimeBandDoseTarget, selectStrengthClaimsForCapacity } from './prescription.js';
import { phasePolicyFor } from './phase-policy.js';
import { INTENT_MUSCLES } from './topology.js';
import { publicMevInternalSafetyCeiling } from './public-mev.js';
import { evaluateObjectiveCoachGuardrails } from './coach-regression.js';
import { assessWeeklyRecovery } from './weekly-recovery.js';
import { proposeRecoveryRedistributions } from './recovery-realization.js';
import { evaluateArmCoverage } from './arm-coverage.js';
import { evaluateFunctionalCoverage } from './functional-coverage.js';
function equipmentEligible(ex, equipment, bodyweight) {
    if ((ex.flags.bodyweight || ex.equipment.includes('bodyweight')) && bodyweight === 'exclude')
        return false;
    const setups = [ex.equipment, ...(ex.equipmentAlternatives ?? [])];
    return setups.some(setup => setup.every(req => req === 'bodyweight' ? bodyweight !== 'exclude' : equipment.includes(req)));
}
export function auditProgram(program, request, options) {
    const findings = [];
    const exerciseMap = createExerciseMap(request.customExercises);
    const prescriptions = createMusclePrescriptions(request, program.phase);
    if (request.preferences.lockedSplit && program.split.family !== request.preferences.lockedSplit) {
        findings.push({
            code: 'LOCKED_SPLIT_MISMATCH',
            severity: 'critical',
            message: `Requested ${request.preferences.lockedSplit} as a locked split, but generation produced ${program.split.family}.`
        });
    }
    for (const session of program.sessions) {
        if (session.estimatedMinutes > session.maxMinutes) {
            findings.push({ code: 'TIME_LIMIT', severity: 'critical', sessionId: session.id, message: `${session.name} is estimated at ${session.estimatedMinutes} min, above the ${session.maxMinutes}-minute limit.` });
        }
        const day = request.schedule.days.find(d => d.day === session.day);
        const equipment = (day.equipmentOverride ?? request.equipment.available);
        const maxBarbell = day.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay;
        const barbellCount = session.exercises.filter(e => exerciseMap.get(e.exerciseId)?.flags.barbell).length;
        if (barbellCount > maxBarbell)
            findings.push({ code: 'BARBELL_LIMIT', severity: 'critical', sessionId: session.id, message: `${session.name} uses ${barbellCount} barbell movements; limit is ${maxBarbell}.` });
        const sessionCapacity = session.targetExercises ?? day.targetExercises;
        if (sessionCapacity !== undefined) {
            // Audit the contract that generation actually handed to this phase/session. The raw schedule
            // target is availability; phase and fatigue adaptation may intentionally reduce it before
            // topology/realization. Comparing a 3-slot peak session against the original 7-slot tier creates
            // false warnings and obscures real capacity misses.
            const floor = Math.max(2, sessionCapacity - 2);
            if (session.exercises.length < floor) {
                const intended = (INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full)
                    .filter(m => m !== 'front_delts')
                    .map(m => prescriptions.find(p => p.muscle === m))
                    .filter((p) => !!p && p.priority !== 'maintenance');
                const productiveMiss = intended.some(p => {
                    const target = program.phase === 'hypertrophy_accumulation' || program.phase === 'mixed_accumulation'
                        ? productiveTimeBandDoseTarget(request, p, program.phase)
                        : p.preferred;
                    return target > 0 && program.muscleLedger[p.muscle].fractionalSets + .99 < target;
                });
                if (productiveMiss || session.exercises.length < 2)
                    findings.push({
                        code: 'SESSION_CAPACITY_MISS',
                        severity: session.exercises.length < 2 ? 'critical' : 'warning', sessionId: session.id,
                        message: `${session.name} has ${session.exercises.length} exercises against an approximate ${sessionCapacity}-exercise capacity while useful intended-muscle dose remains unrealized.`
                    });
            }
            // targetExercises is an approximate complexity budget, not a hard movement ceiling. Compact
            // sessions already use explicit density/superset modeling, so allow one additional setup beyond
            // the normal slack before calling the workout bloated; the hard minute cap remains authoritative.
            // Exercise-count targets are soft planning density hints; elapsed minutes are the hard session
            // cap. Keep three slots of setup slack at every duration so a recovery-preserving accessory
            // redistribution (for example moving rear delts off a consecutive heavy day) is not mislabeled
            // as session bloat when the complete workout still fits the athlete's selected time band.
            const capacityExcessSlack = 3;
            if (session.exercises.length > sessionCapacity + capacityExcessSlack)
                findings.push({
                    code: 'SESSION_CAPACITY_EXCESS', severity: 'warning', sessionId: session.id,
                    message: `${session.name} has ${session.exercises.length} exercises versus an approximate ${sessionCapacity}-exercise session target.`
                });
        }
        if (session.intent === 'full' || session.intent === 'strength_full') {
            const defs = session.exercises.map(e => exerciseMap.get(e.exerciseId)).filter((x) => !!x);
            const hasPush = defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily));
            const hasPull = defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily));
            const hasLower = defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily));
            if (!hasPush || !hasPull || !hasLower)
                findings.push({
                    code: 'FULL_BODY_INCOMPLETE', severity: 'critical', sessionId: session.id,
                    message: `${session.name} is labeled full body but is missing ${[!hasPush ? 'upper push' : '', !hasPull ? 'upper pull' : '', !hasLower ? 'lower body' : ''].filter(Boolean).join(', ')} work.`
                });
        }
        const movementCounts = new Map();
        for (const ex of session.exercises) {
            const def = exerciseMap.get(ex.exerciseId);
            if (!def) {
                findings.push({ code: 'UNKNOWN_EXERCISE', severity: 'critical', sessionId: session.id, message: `Unknown exercise ${ex.exerciseId}.` });
                continue;
            }
            if (!equipmentEligible(def, equipment, request.equipment.bodyweight))
                findings.push({ code: 'UNAVAILABLE_EQUIPMENT', severity: 'critical', sessionId: session.id, message: `${ex.name} cannot be performed with the equipment available on ${session.day}.` });
            if ((def.flags.bodyweight || def.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
                findings.push({ code: 'BODYWEIGHT_EXCLUDED', severity: 'critical', sessionId: session.id, message: `${ex.name} violates the bodyweight-exercise exclusion.` });
            if (!ex.progression)
                findings.push({ code: 'MISSING_PROGRESSION', severity: 'critical', sessionId: session.id, message: `${ex.name} has no progression rule.` });
            if (ex.advancedTechnique) {
                if (request.athlete.experience === 'novice')
                    findings.push({ code: 'ADVANCED_TECHNIQUE_NOVICE', severity: 'major', sessionId: session.id, message: `${ex.name} uses ${ex.advancedTechnique.type} for a novice athlete.` });
                if (ex.role === 'primary_strength' || ex.role === 'secondary_strength')
                    findings.push({ code: 'ADVANCED_TECHNIQUE_STRENGTH', severity: 'major', sessionId: session.id, message: `${ex.name} applies an advanced hypertrophy technique to a strength-specific slot.` });
            }
            movementCounts.set(def.movementFamily, (movementCounts.get(def.movementFamily) ?? 0) + 1);
        }
        const supersets = new Map();
        for (const ex of session.exercises)
            if (ex.supersetGroup) {
                const list = supersets.get(ex.supersetGroup) ?? [];
                list.push(ex);
                supersets.set(ex.supersetGroup, list);
            }
        for (const [group, pair] of supersets) {
            if (pair.length !== 2)
                findings.push({ code: 'SUPERSET_INVALID', severity: 'major', sessionId: session.id, message: `${session.name} superset ${group} does not contain exactly two exercises.` });
            if (pair.some(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength'))
                findings.push({ code: 'SUPERSET_STRENGTH', severity: 'major', sessionId: session.id, message: `${session.name} supersets strength-specific work; main strength rest must be protected.` });
            const defs = pair.map(ex => exerciseMap.get(ex.exerciseId)).filter((x) => !!x);
            if (defs.length === 2) {
                const primaries = defs.map(def => Object.entries(def.muscles).find(([, c]) => c.role === 'primary')?.[0]);
                if (primaries[0] && primaries[0] === primaries[1])
                    findings.push({ code: 'SUPERSET_LOCAL_INTERFERENCE', severity: 'warning', sessionId: session.id, message: `${session.name} pairs two exercises for ${primaries[0]} in ${group}.` });
            }
        }
        if ((movementCounts.get('horizontal_press') ?? 0) >= 3)
            findings.push({ code: 'PRESS_REDUNDANCY', severity: 'major', sessionId: session.id, message: `${session.name} contains three or more horizontal presses.` });
        const lowerBackLoad = session.exercises.reduce((sum, e) => sum + (exerciseMap.get(e.exerciseId)?.fatigue.lowerBack ?? 0), 0);
        if (lowerBackLoad >= 10) {
            const lowerStrength = session.exercises.filter(e => {
                if (e.role !== 'primary_strength' && e.role !== 'secondary_strength')
                    return false;
                const family = exerciseMap.get(e.exerciseId)?.movementFamily;
                return family === 'squat' || family === 'leg_press' || family === 'hip_hinge' || family === 'hip_extension';
            });
            const lowerSlots = program.sessions.filter(s => ['lower', 'legs', 'glute', 'deadlift_focus', 'strength_full', 'full'].includes(s.intent)).length;
            const splitForced = lowerStrength.length >= 2 && lowerSlots <= 1;
            findings.push(splitForced
                ? { code: 'SPLIT_STRENGTH_RECOVERY_TRADEOFF', severity: 'warning', sessionId: session.id, message: `${session.name} carries ${lowerStrength.length} required lower-body strength exposures in the week's only lower-capable session; the lower-back cluster is a split/strength tradeoff, not optional accessory stacking.${request.preferences.lockedSplit ? ' The split is locked, so the engine preserved that choice; unlocking split selection allows recovery-friendlier full-body structures to be evaluated.' : ''}` }
                : { code: 'LOWER_BACK_CLUSTER', severity: 'warning', sessionId: session.id, message: `${session.name} has a high lower-back fatigue cluster.` });
        }
    }
    // Every generated program runs the objective coach guardrail layer before it can be returned.
    // These are concrete, machine-checkable prescription invariants—not a claim that software can
    // replace a human coach's judgment. Human blind review remains a sampled validation layer.
    for (const finding of evaluateObjectiveCoachGuardrails(program, request)) {
        if (finding.code === 'SESSION_TIME_CAP_EXCEEDED')
            continue; // already enforced above as critical
        const severity = finding.code === 'STRENGTH_AFTER_HYPERTROPHY' || finding.code === 'LOWER_DAY_IDENTITY_DILUTED' || finding.code === 'REDUNDANT_ACCESSORY_FAMILY' || finding.code === 'REDUNDANT_COMPOUND_OVERLAP' || finding.code === 'REDUNDANT_SEMANTIC_OVERLAP' || finding.code === 'LOCAL_PREFATIGUE_BEFORE_COMPOUND' ? 'major' : 'warning';
        findings.push({ code: `COACH_${finding.code}`, severity, sessionId: program.sessions[finding.sessionIndex]?.id, message: finding.detail });
    }
    if (!options?.skipRecoveryRealization) {
        const recovery = assessWeeklyRecovery(program.sessions, request);
        if (recovery.hardCollisions > 0 && proposeRecoveryRedistributions(program.sessions, request).length) {
            findings.push({
                code: 'COACH_RECOVERY_REALIZATION_AVOIDABLE', severity: 'warning',
                message: `${recovery.hardCollisions} hard consecutive-day recovery collision${recovery.hardCollisions === 1 ? '' : 's'} remain even though dose-neutral assistance redistribution can reduce the weekly recovery objective.`
            });
        }
    }
    // Direct arm work has a function-level contract in addition to broad biceps/forearm volume. This
    // catches programs that technically hit the parent muscle while repeating the same curl orientation
    // or training wrist flexion without extension (or vice versa).
    for (const finding of evaluateArmCoverage(program.sessions, request, exerciseMap).findings) {
        findings.push({ code: finding.code, severity: finding.severity, message: finding.message });
    }
    // Broad weekly set totals can conceal repeated-function programming. Once a muscle has enough
    // meaningful direct work to support diversity, require complementary functions when the athlete's
    // equipment makes them feasible. This is an audit layer only; it never creates a second volume ledger.
    for (const finding of evaluateFunctionalCoverage(program.sessions, request, exerciseMap).findings) {
        findings.push({ code: finding.code, severity: finding.severity, message: finding.message });
    }
    const techniqueCount = program.sessions.reduce((n, s) => n + s.exercises.filter(e => e.advancedTechnique).length, 0);
    const techniqueBudget = phasePolicyFor(program.phase).advancedTechniqueBudget;
    if (techniqueCount > techniqueBudget)
        findings.push({ code: 'ADVANCED_TECHNIQUE_BUDGET', severity: 'major', message: `Program uses ${techniqueCount} advanced techniques but ${program.phase} permits ${techniqueBudget}.` });
    const selectedStrengthClaims = selectStrengthClaimsForCapacity(request, createStrengthClaims(request));
    const expectedLiftExposures = new Map();
    for (const claim of selectedStrengthClaims)
        expectedLiftExposures.set(claim.lift, (expectedLiftExposures.get(claim.lift) ?? 0) + 1);
    for (const [lift, priority] of Object.entries(request.goal.liftPriorities)) {
        if (priority !== 'primary' && priority !== 'high')
            continue;
        const exposureSessions = program.sessions.filter(session => session.exercises.some(ex => (exerciseMap.get(ex.exerciseId)?.liftSpecificity?.[lift] ?? 0) > .45));
        const expected = expectedLiftExposures.get(lift) ?? 1;
        if (exposureSessions.length < expected)
            findings.push({
                code: 'LIFT_EXPOSURE_MISSING',
                severity: priority === 'primary' ? 'critical' : 'major',
                message: `${lift.replace('_', ' ')} is ${priority} priority but only ${exposureSessions.length} sufficiently specific exposure${exposureSessions.length === 1 ? '' : 's'} were realized; ${expected} are expected after global and regional strength-capacity protection.`
            });
    }
    for (const p of prescriptions) {
        const actual = program.muscleLedger[p.muscle].fractionalSets;
        if (p.muscle === 'front_delts')
            continue;
        if (actual < p.minimum * .85)
            findings.push({ code: 'MUSCLE_UNDER_MIN', severity: 'major', message: `${p.muscle} receives ${actual.toFixed(1)} fractional sets versus a ${p.minimum}-set minimum region.` });
        const effectiveUpper = publicMevInternalSafetyCeiling(request, program.phase, p.muscle, p.upper);
        if (actual > effectiveUpper * 1.2) {
            const strengthContribution = program.sessions.reduce((total, session) => total + session.exercises.reduce((sessionTotal, exercise) => {
                if (exercise.role !== 'primary_strength' && exercise.role !== 'secondary_strength')
                    return sessionTotal;
                const def = exerciseMap.get(exercise.exerciseId);
                return sessionTotal + (def?.muscles[p.muscle]?.credit ?? 0) * exercise.sets;
            }, 0), 0);
            const upperBoundary = effectiveUpper * 1.2;
            const explainedByStrength = strengthContribution > 0 && actual - strengthContribution <= upperBoundary + .001;
            findings.push(explainedByStrength
                ? { code: 'STRENGTH_VOLUME_TRADEOFF', severity: 'warning', message: `${p.muscle} receives ${actual.toFixed(1)} fractional sets, above the muscle-only useful region because ${strengthContribution.toFixed(1)} sets of required strength-specific work also load it.` }
                : { code: 'MUSCLE_OVER_UPPER', severity: 'warning', message: `${p.muscle} receives ${actual.toFixed(1)} fractional sets, well above the modeled useful region.` });
        }
    }
    const critical = findings.some(f => f.severity === 'critical');
    const major = findings.some(f => f.severity === 'major');
    return { result: critical ? 'reject' : major ? 'repair' : 'pass', findings };
}
