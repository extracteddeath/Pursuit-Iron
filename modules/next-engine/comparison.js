import { createExerciseMap } from './exercise-db.js';
import { createMusclePrescriptions, createStrengthClaims, selectStrengthClaimsForCapacity } from './prescription.js';
function priorityWeight(priority) {
    return priority === 'primary' || priority === 'specialization' ? 1.4 : priority === 'high' ? 1.2 : priority === 'maintenance' ? .65 : 1;
}
function dayIndex(day) {
    return ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'].indexOf(day);
}
function circularGapDays(a, b) {
    const ai = dayIndex(a), bi = dayIndex(b);
    if (ai < 0 || bi < 0)
        return 7;
    const forward = (bi - ai + 7) % 7;
    return forward || 7;
}
function intentFamily(intent) {
    const value = String(intent || '').toLowerCase().replaceAll('-', '_');
    // Legacy v661 uses compact intent IDs (fb_a/fb2_b) and phase suffixes (_power/_hyp).
    // Normalize those before comparing recovery structure so different naming conventions do not
    // manufacture an apparent advantage for either engine.
    if (/^fb\d*_[a-z0-9]+$/.test(value) || value === 'fb' || value.startsWith('full'))
        return 'full';
    if (value === 'back_shoulders' || value.startsWith('back_') || value.includes('pull'))
        return 'pull';
    if (value === 'chest_arms' || value.startsWith('chest_') || value.includes('push') || value.includes('press'))
        return 'push';
    if (value.startsWith('upper') || value.includes('_upper'))
        return 'upper';
    if (value.startsWith('lower') || value.includes('_lower'))
        return 'lower';
    if (value.includes('legs') || value.includes('quad') || value.includes('ham') || value.includes('glute'))
        return 'lower';
    return value.replace(/_(power|hyp|hypertrophy|strength|day|a|b|c|d|e|f)$/, '');
}
function highRecoveryCollision(a, b) {
    const pair = `${intentFamily(a)}:${intentFamily(b)}`;
    return new Set(['upper:push', 'push:upper', 'push:push', 'upper:upper', 'lower:lower', 'full:full']).has(pair);
}
function programExerciseMap(program, request) {
    return createExerciseMap([...(request.customExercises ?? []), ...(program.customExerciseSnapshots ?? [])]);
}
function requiredLiftSessions(program, lift, request) {
    const exerciseMap = programExerciseMap(program, request);
    return program.sessions.filter(session => session.exercises.some(ex => (exerciseMap.get(ex.exerciseId)?.liftSpecificity?.[lift] ?? 0) > .45)).length;
}
export function fingerprintProgram(program, request) {
    const prescriptions = createMusclePrescriptions(request, program.phase);
    let weightedMuscle = 0;
    let muscleWeights = 0;
    for (const prescription of prescriptions) {
        if (prescription.muscle === 'front_delts')
            continue;
        const priority = request.goal.musclePriorities[prescription.muscle] ?? 'normal';
        // This dimension is intentionally about EXPLICIT priorities. Treating every normal-priority
        // muscle as a priority made large-volume legacy programs look better simply for being larger,
        // even when both programs cleared the required dose. General coverage is already enforced by
        // the independent audit; this metric answers the narrower question its name promises.
        if (priority !== 'high' && priority !== 'specialization' && priority !== 'primary')
            continue;
        const weight = priorityWeight(priority);
        const actual = program.muscleLedger[prescription.muscle].fractionalSets;
        const target = Math.max(1, prescription.preferred);
        weightedMuscle += Math.min(1, actual / target) * weight;
        muscleWeights += weight;
    }
    const requiredLifts = Object.entries(request.goal.liftPriorities)
        .filter(([, priority]) => priority === 'primary' || priority === 'high');
    const expectedClaims = selectStrengthClaimsForCapacity(request, createStrengthClaims(request));
    const expectedByLift = new Map();
    for (const claim of expectedClaims)
        expectedByLift.set(claim.lift, (expectedByLift.get(claim.lift) ?? 0) + 1);
    const liftFit = requiredLifts.length
        ? requiredLifts.reduce((sum, [lift]) => {
            const expected = Math.max(1, expectedByLift.get(lift) ?? 1);
            return sum + Math.min(1, requiredLiftSessions(program, lift, request) / expected);
        }, 0) / requiredLifts.length
        : 1;
    const exerciseMap = programExerciseMap(program, request);
    let redundancyFlags = 0;
    let exerciseCount = 0;
    let progressionCount = 0;
    for (const session of program.sessions) {
        const counts = new Map();
        for (const ex of session.exercises) {
            exerciseCount++;
            if (ex.progression.trim())
                progressionCount++;
            const movement = exerciseMap.get(ex.exerciseId)?.movementFamily ?? 'unknown';
            counts.set(movement, (counts.get(movement) ?? 0) + 1);
        }
        for (const count of counts.values())
            if (count >= 3)
                redundancyFlags += count - 2;
    }
    const ordered = [...program.sessions].sort((a, b) => dayIndex(a.day) - dayIndex(b.day));
    let recoveryFlags = program.audit.findings.filter(f => f.code === 'LOWER_BACK_CLUSTER').length;
    for (let i = 0; i < ordered.length; i++) {
        const a = ordered[i], b = ordered[(i + 1) % ordered.length];
        if (!b)
            continue;
        const gap = circularGapDays(a.day, b.day);
        if (gap > 1)
            continue;
        if (highRecoveryCollision(a.intent, b.intent))
            recoveryFlags++;
    }
    return {
        criticalFindings: program.audit.findings.filter(f => f.severity === 'critical').length,
        majorFindings: program.audit.findings.filter(f => f.severity === 'major').length,
        warnings: program.audit.findings.filter(f => f.severity === 'warning').length,
        hardTimeOverflows: program.sessions.filter(s => s.estimatedMinutes > s.maxMinutes).length,
        totalWeeklyMinutes: program.sessions.reduce((sum, s) => sum + s.estimatedMinutes, 0),
        peakSessionMinutes: Math.max(0, ...program.sessions.map(s => s.estimatedMinutes)),
        totalWorkingSets: program.sessions.reduce((sum, s) => sum + s.exercises.reduce((n, e) => n + e.sets, 0), 0),
        exerciseSlots: program.sessions.reduce((sum, s) => sum + s.exercises.length, 0),
        advancedTechniques: program.sessions.reduce((sum, s) => sum + s.exercises.filter(e => e.advancedTechnique).length, 0),
        redundancyFlags,
        recoveryFlags,
        progressionCompleteness: exerciseCount ? progressionCount / exerciseCount : 1,
        priorityMuscleFulfillment: muscleWeights ? weightedMuscle / muscleWeights : 1,
        requiredLiftFulfillment: liftFit
    };
}
function lowerBetter(dimension, before, after, tolerance = 0) {
    const diff = after - before;
    const direction = Math.abs(diff) <= tolerance ? 'equivalent' : diff < 0 ? 'improvement' : 'regression';
    return { dimension, before, after, direction, rationale: direction === 'equivalent' ? 'No material change.' : direction === 'improvement' ? 'Lower is better on this dimension.' : 'This dimension increased and should be reviewed.' };
}
function higherBetter(dimension, before, after, tolerance = 0) {
    const diff = after - before;
    const direction = Math.abs(diff) <= tolerance ? 'equivalent' : diff > 0 ? 'improvement' : 'regression';
    return { dimension, before, after, direction, rationale: direction === 'equivalent' ? 'No material change.' : direction === 'improvement' ? 'Goal fulfillment improved.' : 'Goal fulfillment declined.' };
}
function prescriptionText(ex) {
    return `${ex.sets}x${ex.prescription.reps[0]}-${ex.prescription.reps[1]} @${ex.prescription.rir[0]}-${ex.prescription.rir[1]} RIR / ${ex.prescription.restSeconds}s`;
}
export function comparePrograms(beforeProgram, afterProgram, request) {
    const before = fingerprintProgram(beforeProgram, request);
    const after = fingerprintProgram(afterProgram, request);
    const dimensions = [
        lowerBetter('critical_findings', before.criticalFindings, after.criticalFindings),
        lowerBetter('major_findings', before.majorFindings, after.majorFindings),
        lowerBetter('warnings', before.warnings, after.warnings),
        lowerBetter('hard_time_overflows', before.hardTimeOverflows, after.hardTimeOverflows),
        higherBetter('priority_muscle_fulfillment', before.priorityMuscleFulfillment, after.priorityMuscleFulfillment, .01),
        higherBetter('required_lift_fulfillment', before.requiredLiftFulfillment, after.requiredLiftFulfillment, .01),
        lowerBetter('redundancy', before.redundancyFlags, after.redundancyFlags),
        lowerBetter('recovery_flags', before.recoveryFlags, after.recoveryFlags),
        higherBetter('progression_completeness', before.progressionCompleteness, after.progressionCompleteness, .001),
        lowerBetter('peak_session_minutes', before.peakSessionMinutes, after.peakSessionMinutes, 2),
        lowerBetter('exercise_slots', before.exerciseSlots, after.exerciseSlots, 1)
    ];
    const exerciseChanges = [];
    const beforeSessions = new Map(beforeProgram.sessions.map(s => [s.day, s]));
    const afterSessions = new Map(afterProgram.sessions.map(s => [s.day, s]));
    for (const day of new Set([...beforeSessions.keys(), ...afterSessions.keys()])) {
        const b = beforeSessions.get(day);
        const a = afterSessions.get(day);
        const sessionId = a?.id ?? b?.id ?? String(day);
        const sessionName = a?.name ?? b?.name ?? String(day);
        const bMap = new Map((b?.exercises ?? []).map(ex => [ex.exerciseId, ex]));
        const aMap = new Map((a?.exercises ?? []).map(ex => [ex.exerciseId, ex]));
        for (const id of new Set([...bMap.keys(), ...aMap.keys()])) {
            const be = bMap.get(id);
            const ae = aMap.get(id);
            if (!be && ae)
                exerciseChanges.push({ sessionId, sessionName, exerciseId: id, exerciseName: ae.name, type: 'added', after: prescriptionText(ae) });
            else if (be && !ae)
                exerciseChanges.push({ sessionId, sessionName, exerciseId: id, exerciseName: be.name, type: 'removed', before: prescriptionText(be) });
            else if (be && ae) {
                const bt = prescriptionText(be), at = prescriptionText(ae);
                if (bt !== at)
                    exerciseChanges.push({ sessionId, sessionName, exerciseId: id, exerciseName: ae.name, type: 'prescription_changed', before: bt, after: at });
            }
        }
    }
    const beforeFindings = new Set(beforeProgram.audit.findings.map(f => `${f.severity}:${f.code}`));
    const afterFindings = new Set(afterProgram.audit.findings.map(f => `${f.severity}:${f.code}`));
    const findingsAdded = [...afterFindings].filter(x => !beforeFindings.has(x));
    const findingsResolved = [...beforeFindings].filter(x => !afterFindings.has(x));
    // Resource use (raw minutes / exercise-slot count) is descriptive, not a quality verdict by itself.
    // A shorter program is only better if the independently audited training-quality dimensions are
    // preserved or improved. This prevents the A/B harness from rewarding under-programming.
    const evaluativeDimensions = new Set([
        'critical_findings', 'major_findings', 'warnings', 'hard_time_overflows',
        'priority_muscle_fulfillment', 'required_lift_fulfillment', 'redundancy',
        'recovery_flags', 'progression_completeness'
    ]);
    const verdictDimensions = dimensions.filter(d => evaluativeDimensions.has(d.dimension));
    const regressions = verdictDimensions.filter(d => d.direction === 'regression');
    const improvements = verdictDimensions.filter(d => d.direction === 'improvement');
    const hardRegression = after.criticalFindings > before.criticalFindings || after.majorFindings > before.majorFindings || after.hardTimeOverflows > before.hardTimeOverflows || after.requiredLiftFulfillment + 0.01 < before.requiredLiftFulfillment || after.progressionCompleteness + 0.001 < before.progressionCompleteness;
    const verdict = hardRegression ? 'regression' : regressions.length && improvements.length ? 'mixed_tradeoff' : regressions.length ? 'regression' : improvements.length ? 'improvement' : 'equivalent';
    return { verdict, before, after, dimensions, exerciseChanges, findingsAdded, findingsResolved };
}
