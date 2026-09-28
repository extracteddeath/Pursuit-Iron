import { createExerciseMap } from '../next-engine/exercise-db.js';
import { createMusclePrescriptions, normalizeRequest } from '../next-engine/prescription.js';
const PRIORITY_WEIGHT = { maintenance: .15, normal: .55, high: 1.15, specialization: 1.75, primary: 2.1 };
const HIGH_PRIORITY = new Set(['high', 'specialization', 'primary']);
const LIFT_PRIORITY_WEIGHT = { maintenance: 0, normal: .2, high: .75, specialization: .9, primary: 1 };
function requestOf(input) {
    return input.goal?.hypertrophyWeight !== undefined ? input : normalizeRequest(input);
}
function relevantLiftSpecificity(def, request) {
    let best = 0;
    for (const [lift, priority] of Object.entries(request.goal.liftPriorities)) {
        best = Math.max(best, (def.liftSpecificity?.[lift] ?? 0) * LIFT_PRIORITY_WEIGHT[priority]);
    }
    return best;
}
export function shadowPriorityMetrics(program, input) {
    const request = requestOf(input);
    const map = createExerciseMap(request.customExercises);
    const priorityMuscles = Object.entries(request.goal.musclePriorities).filter(([, p]) => HIGH_PRIORITY.has(p)).map(([m]) => m);
    let frequency = 0, early = 0;
    for (const muscle of priorityMuscles) {
        for (const session of program.sessions) {
            const hits = session.exercises.map((ex, index) => ({ ex, index, def: map.get(ex.exerciseId) })).filter(x => (x.def?.muscles[muscle]?.credit ?? 0) >= 1);
            if (hits.length) {
                frequency++;
                if (Math.min(...hits.map(x => x.index)) <= 2)
                    early++;
            }
        }
    }
    return { frequency, early, priorityMuscles };
}
export function scoreShadowProgram(program, input, continuityIds = []) {
    const request = requestOf(input);
    const map = createExerciseMap(request.customExercises);
    const prescriptions = createMusclePrescriptions(request, program.phase);
    const pMap = new Map(prescriptions.map(p => [p.muscle, p]));
    const audit = program.audit.result === 'pass' ? 0 : program.audit.result === 'repair' ? -500 : -5000;
    let timeUtility = 0;
    const preferredDeficit = Object.keys(program.muscleLedger).reduce((sum, m) => {
        const p = pMap.get(m);
        if (!p)
            return sum;
        return sum + Math.max(0, p.preferred - (program.muscleLedger[m]?.fractionalSets ?? 0)) * PRIORITY_WEIGHT[p.priority];
    }, 0);
    for (const session of program.sessions) {
        const min = session.minMinutes ?? 0, max = Math.max(min + 1, session.maxMinutes), used = session.estimatedMinutes;
        const span = Math.max(1, max - min);
        if (used > max)
            timeUtility -= (used - max) * 12;
        else {
            const productiveTarget = min + span * .58;
            if (used < min && preferredDeficit > .25)
                timeUtility -= (min - used) * .85;
            else if (used <= productiveTarget)
                timeUtility += (used - min) * .18;
            else
                timeUtility += Math.max(0, productiveTarget - min) * .18 - (used - productiveTarget) * .05;
            timeUtility -= Math.max(0, used - max * .96) * .16;
        }
    }
    let specialization = 0, doseQuality = 0, exerciseOrder = 0;
    for (const [muscle, p] of pMap) {
        const dose = program.muscleLedger[muscle]?.fractionalSets ?? 0;
        const direct = program.muscleLedger[muscle]?.directSets ?? 0;
        const weight = PRIORITY_WEIGHT[p.priority];
        if (dose < p.minimum)
            doseQuality -= (p.minimum - dose) * 8 * weight;
        else
            doseQuality += Math.min(dose, p.preferred) * .22 * weight;
        if (dose > p.upper)
            doseQuality -= (dose - p.upper) * 6;
        if (HIGH_PRIORITY.has(p.priority)) {
            const directTarget = Math.max(p.directPreferred, p.directMinimum);
            specialization += Math.min(direct, directTarget) * .8 * weight;
            let sessionsHit = 0;
            for (const session of program.sessions) {
                const indexes = session.exercises.map((ex, i) => ({ i, def: map.get(ex.exerciseId) })).filter(x => (x.def?.muscles[muscle]?.credit ?? 0) >= 1).map(x => x.i);
                if (indexes.length) {
                    sessionsHit++;
                    exerciseOrder += Math.max(0, 3 - Math.min(...indexes)) * .45 * weight;
                }
            }
            const desiredFreq = request.schedule.days.length >= 5 ? 3 : request.schedule.days.length >= 3 ? 2 : 1;
            specialization += Math.min(sessionsHit, desiredFreq) * 2.6 * weight;
            if (sessionsHit < desiredFreq)
                specialization -= (desiredFreq - sessionsHit) * 2.2 * weight;
        }
    }
    const continuitySet = new Set(continuityIds);
    let continuity = 0;
    if (continuitySet.size) {
        const ids = new Set(program.sessions.flatMap(s => s.exercises.map(e => e.exerciseId)));
        for (const id of continuitySet)
            continuity += ids.has(id) ? 1.1 : -.9;
    }
    // Phase specificity is deliberately modest in accumulation and strongest in intensification/peak.
    // It lets transition counterfactuals prefer promoting useful strength anchors while preserving
    // hypertrophy dose where it still earns its recovery cost.
    let phaseSpecificity = 0;
    const strengthPhase = program.phase === 'strength_accumulation' || program.phase === 'intensification' || program.phase === 'peak';
    const phaseWeight = program.phase === 'peak' ? 1.35 : program.phase === 'intensification' ? 1.0 : program.phase === 'strength_accumulation' ? .65 : 0;
    if (strengthPhase) {
        for (const session of program.sessions) {
            for (const ex of session.exercises) {
                const def = map.get(ex.exerciseId);
                if (!def)
                    continue;
                const liftSpecificity = relevantLiftSpecificity(def, request);
                const strengthSuitability = def.suitability.strength ?? 0;
                if (ex.role === 'primary_strength')
                    phaseSpecificity += ex.sets * (.55 + liftSpecificity * .8) * phaseWeight;
                else if (ex.role === 'secondary_strength' || ex.role === 'strength_support')
                    phaseSpecificity += ex.sets * (.25 + liftSpecificity * .45) * phaseWeight;
                else if (program.phase === 'peak' && ex.role === 'hypertrophy_isolation') {
                    const muscles = Object.entries(def.muscles).filter(([, c]) => c.credit >= 1).map(([m]) => m);
                    const priority = Math.max(0, ...muscles.map(m => PRIORITY_WEIGHT[request.goal.musclePriorities[m] ?? 'normal']));
                    if (priority <= PRIORITY_WEIGHT.normal)
                        phaseSpecificity -= ex.sets * .22;
                }
                else if (ex.role === 'hypertrophy_compound' && strengthSuitability >= .7)
                    phaseSpecificity += ex.sets * .08 * phaseWeight;
            }
        }
    }
    // Penalize needless exercise proliferation/setup transitions. The optimizer may spend available time,
    // but it should prefer one valuable extra set over another low-value station change.
    let complexityPenalty = 0;
    for (const session of program.sessions) {
        complexityPenalty -= Math.max(0, session.exercises.length - 8) * .8;
        complexityPenalty -= session.exercises.reduce((sum, ex) => sum + (map.get(ex.exerciseId)?.setupCost ?? 0) * .025, 0);
    }
    const total = audit + timeUtility + specialization + doseQuality + exerciseOrder + continuity + phaseSpecificity + complexityPenalty;
    return { total, audit, timeUtility, specialization, doseQuality, exerciseOrder, continuity, phaseSpecificity, complexityPenalty };
}
