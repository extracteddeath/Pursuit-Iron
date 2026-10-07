// M230 canonical capacity-repair stage. Stage context is local to one realization.
import { productiveTimeBandDoseTarget } from './prescription.js';
import { assignAccessorySupersets } from './realizer-pairing.js';
import { makePlanned } from './realizer-prescriptions.js';
import { LARGE, SMALL, muscleCandidate, primaryMuscle } from './realizer-ranking.js';
import { estimateMinutes } from './realizer-time-budget.js';
import { chooseAdvancedTechnique } from './techniques.js';
import { INTENT_MUSCLES } from './topology.js';

export function redistributeFullBodyAccessories(context) {
const { exerciseMap, realized, request } = context;
/* M230:PRESERVE:stage.redistributeFullBodyAccessories:BEGIN */
if (realized.every(s => s.intent === 'full') && (realized.some(s => s.targetExercises !== undefined) || request.preferences.lockedSplit === 'full_body')) {
        for (let pass = 0; pass < 50; pass++) {
            const under = [...realized]
                .filter(s => s.targetExercises !== undefined && s.exercises.length < Math.max(3, (s.targetExercises ?? 3) - 1))
                .sort((a, b) => (a.exercises.length - (a.targetExercises ?? 3)) - (b.exercises.length - (b.targetExercises ?? 3)) || a.id.localeCompare(b.id))[0];
            if (!under)
                break;
            const donor = [...realized]
                .filter(s => s.id !== under.id && s.exercises.length > Math.max(3, (s.targetExercises ?? 3) - 1))
                .sort((a, b) => (b.exercises.length - (b.targetExercises ?? 3)) - (a.exercises.length - (a.targetExercises ?? 3)) || b.id.localeCompare(a.id))[0];
            if (!donor)
                break;
            const chosenIds = new Set(under.exercises.map(e => e.exerciseId));
            const candidate = [...donor.exercises]
                .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter(x => x.def && x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && !chosenIds.has(x.exercise.exerciseId))
                .filter(x => {
                const remaining = donor.exercises.filter((_, i) => i !== x.index).map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                const hasPush = remaining.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily));
                const hasPull = remaining.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily));
                const hasLower = remaining.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily));
                return hasPush && hasPull && hasLower;
            })
                .sort((a, b) => {
                const fatigueA = (a.def.fatigue.systemic + a.def.fatigue.axial + a.def.fatigue.lowerBack);
                const fatigueB = (b.def.fatigue.systemic + b.def.fatigue.axial + b.def.fatigue.lowerBack);
                return fatigueA - fatigueB || a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
            })[0];
            if (!candidate)
                break;
            const proposed = [...under.exercises, candidate.exercise];
            if (estimateMinutes(proposed) > under.maxMinutes)
                break;
            donor.exercises.splice(candidate.index, 1);
            donor.estimatedMinutes = estimateMinutes(donor.exercises);
            under.exercises = proposed;
            under.estimatedMinutes = estimateMinutes(under.exercises);
        }
    }
/* M230:PRESERVE:stage.redistributeFullBodyAccessories:END */

}

export function diversifySparseSessions(context) {
const { exerciseCatalog, exerciseMap, policy, realized, request, trackExerciseUse, weeklyMovementUse } = context;
/* M230:PRESERVE:stage.diversifySparseSessions:BEGIN */
for (const session of realized) {
        const target = session.targetExercises;
        if (target === undefined)
            continue;
        const floor = Math.max(2, target - 1);
        for (let pass = 0; pass < 8 && session.exercises.length < floor; pass++) {
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((x) => !!x);
            const sourceCandidates = session.exercises
                .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter(x => x.def && x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && x.exercise.sets >= 4)
                .map(x => {
                const primary = Object.entries(x.def.muscles).find(([, c]) => c.role === 'primary')?.[0];
                return { ...x, primary };
            })
                .filter(x => x.primary && (LARGE.includes(x.primary) || (target >= 7 && SMALL.includes(x.primary))))
                .sort((a, b) => b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
            let changed = false;
            for (const source of sourceCandidates) {
                const allocation = {
                    id: `capacity-${session.id}-${source.primary}`,
                    kind: 'muscle', muscle: source.primary, role: source.exercise.role, dose: 1, importance: 'C'
                };
                const alternate = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog);
                if (!alternate)
                    continue;
                if (alternate.movementFamily === source.def.movementFamily && LARGE.includes(source.primary))
                    continue;
                const movedSets = 2;
                if (source.exercise.sets - movedSets < 2)
                    continue;
                const reduced = { ...source.exercise, sets: source.exercise.sets - movedSets };
                const added = makePlanned(alternate, source.exercise.role, movedSets, policy, request.athlete.experience);
                const proposal = session.exercises.map((ex, i) => i === source.index ? reduced : ex).concat(added);
                const minutes = estimateMinutes(proposal);
                if (minutes > session.maxMinutes)
                    continue;
                session.exercises = proposal;
                session.estimatedMinutes = minutes;
                trackExerciseUse(alternate);
                changed = true;
                break;
            }
            if (!changed)
                break;
        }
    }
/* M230:PRESERVE:stage.diversifySparseSessions:END */

}

export function fillSessionCapacity(context) {
const { currentFractional, exerciseCatalog, exerciseMap, phase, policy, prescriptionMap, priorityWeight, realized, request, trackExerciseUse, weeklyMovementUse } = context;
/* M230:PRESERVE:stage.fillSessionCapacity:BEGIN */
for (const session of realized) {
        const target = session.targetExercises;
        if (target === undefined)
            continue;
        const floor = Math.max(2, target - 1);
        for (let pass = 0; pass < 10 && session.exercises.length < floor; pass++) {
            const totals = currentFractional();
            const muscles = (INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full)
                .filter(m => m !== 'front_delts')
                .map(m => {
                const p = prescriptionMap.get(m);
                const target = phase === 'hypertrophy_accumulation' || phase === 'mixed_accumulation'
                    ? productiveTimeBandDoseTarget(request, p, phase)
                    : p.preferred;
                return { m, p, target, deficit: p.preferred - totals[m], room: p.upper - totals[m] };
            })
                // targetExercises is a complexity/capacity reference, not an instruction to manufacture
                // optional maintenance-region filler. For modeled muscles, spare slots may still buy marginal
                // adaptive work above preferred when useful upper-region room remains, matching M50 behavior.
                .filter(x => x.target > 0 && x.p.priority !== 'maintenance' && x.room >= 2)
                .sort((a, b) => {
                const av = (Math.max(0, a.deficit) + .35 * Math.max(0, a.room)) * priorityWeight[a.p.priority];
                const bv = (Math.max(0, b.deficit) + .35 * Math.max(0, b.room)) * priorityWeight[b.p.priority];
                return bv - av || a.m.localeCompare(b.m);
            });
            let added = false;
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((x) => !!x);
            for (const option of muscles) {
                const role = SMALL.includes(option.m) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
                const allocation = { id: `capacity-add-${session.id}-${option.m}`, kind: 'muscle', muscle: option.m, role, dose: 2, importance: option.p.priority === 'specialization' || option.p.priority === 'primary' ? 'A' : option.p.priority === 'high' ? 'B' : 'C' };
                const familyCap = option.p.priority === 'high' || option.p.priority === 'specialization' || option.p.priority === 'primary' ? 2 : 1;
                const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 0, familyCap);
                if (!def)
                    continue;
                const setOptions = [2];
                for (const sets of setOptions) {
                    const exercise = makePlanned(def, role, sets, policy, request.athlete.experience);
                    const proposal = [...session.exercises, exercise];
                    const minutes = estimateMinutes(proposal);
                    if (minutes > session.maxMinutes)
                        continue;
                    session.exercises = proposal;
                    session.estimatedMinutes = minutes;
                    trackExerciseUse(def);
                    added = true;
                    break;
                }
                if (added)
                    break;
            }
            if (!added)
                break;
        }
    }
/* M230:PRESERVE:stage.fillSessionCapacity:END */

}

export function scheduleAdvancedTechniques(context) {
const { candidates, policy, realized, request } = context;
let { techniquesUsed } = context;
try {
/* M230:PRESERVE:stage.scheduleAdvancedTechniques:BEGIN */
for (const candidate of candidates) {
        if (techniquesUsed >= policy.advancedTechniqueBudget)
            break;
        const session = realized[candidate.sessionIndex];
        const exercise = session.exercises[candidate.exerciseIndex];
        const technique = chooseAdvancedTechnique(candidate.def, exercise, request.athlete.experience, candidate.priority, techniquesUsed, policy.advancedTechniqueBudget);
        if (!technique)
            continue;
        const proposed = { ...exercise, advancedTechnique: technique };
        const exercises = session.exercises.map((x, i) => i === candidate.exerciseIndex ? proposed : x);
        const minutes = estimateMinutes(exercises);
        if (minutes > session.maxMinutes)
            continue;
        session.exercises = exercises;
        session.estimatedMinutes = minutes;
        techniquesUsed++;
    }
/* M230:PRESERVE:stage.scheduleAdvancedTechniques:END */

} finally { context.techniquesUsed = techniquesUsed; }
}

export function usefulTarget(context, ...args) {
const { accumulationFloorExpansion, latePhaseExpansion, phase, request } = context;
return (
/* M230:PRESERVE:helper.usefulTarget:BEGIN */
(p) => {
        if (phase === 'hypertrophy_accumulation' || phase === 'mixed_accumulation') {
            if (p.priority === 'maintenance' && p.minimum === 0 && p.preferred === 0)
                return 0;
            return Math.min(p.upper, Math.max(productiveTimeBandDoseTarget(request, p, phase), p.preferred + Math.round(Math.max(0, p.upper - p.preferred) * accumulationFloorExpansion)));
        }
        return Math.min(p.upper, p.preferred + Math.round(Math.max(0, p.upper - p.preferred) * latePhaseExpansion));
    }
/* M230:PRESERVE:helper.usefulTarget:END */
)(...args);
}

export function expandProductiveCapacity(context) {
const { currentFractional, exerciseCatalog, exerciseMap, policy, prescriptionMap, priorityRank, realized, request, totalsWithSessionProposal, trackExerciseUse, usefulTarget, weeklyMovementUse } = context;
/* M230:PRESERVE:stage.expandProductiveCapacity:BEGIN */
for (const session of realized) {
        const lower = session.minMinutes ?? 0;
        const productiveFloor = lower > 0 ? lower * .95 : 0;
        if (productiveFloor <= 0)
            continue;
        session.estimatedMinutes = estimateMinutes(session.exercises);
        for (let guard = 0; guard < 24 && session.estimatedMinutes + 0.001 < productiveFloor; guard++) {
            const totals = currentFractional();
            const options = session.exercises.map((exercise, index) => {
                const depthCap = exercise.role === 'hypertrophy_isolation' && (session.minMinutes ?? 0) >= 90 ? 5 : 4;
                if (exercise.role === 'primary_strength' || exercise.role === 'secondary_strength' || exercise.sets >= depthCap)
                    return null;
                const def = exerciseMap.get(exercise.exerciseId);
                const muscle = def ? primaryMuscle(def) : undefined;
                if (!def || !muscle)
                    return null;
                if (muscle === 'front_delts' && request.goal.musclePriorities.front_delts === 'normal')
                    return null;
                const prescription = prescriptionMap.get(muscle);
                if (!prescription)
                    return null;
                const target = usefulTarget(prescription);
                if (target <= 0 || totals[muscle] + .001 >= target)
                    return null;
                const proposal = session.exercises.map((ex, i) => i === index ? { ...ex, sets: ex.sets + 1 } : ex);
                const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                if (proposedTotals[muscle] > prescription.upper + .001)
                    return null;
                const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                if (optimized.estimatedMinutes > session.maxMinutes)
                    return null;
                const deficit = (target - totals[muscle]) / Math.max(1, target);
                const priority = priorityRank[request.goal.musclePriorities[muscle] ?? 'normal'];
                return { index, muscle, deficit, priority, optimized, currentSets: exercise.sets };
            }).filter(Boolean);
            options.sort((a, b) => b.priority - a.priority || b.deficit - a.deficit || a.currentSets - b.currentSets || a.index - b.index);
            const best = options[0];
            if (best) {
                session.exercises = best.optimized.exercises;
                session.estimatedMinutes = best.optimized.estimatedMinutes;
                continue;
            }
            // If useful set depth is exhausted but the session is still below the bucket floor, spend one
            // of the session's intended movement slots on the most under-served compatible muscle. This is
            // how a 40–60 card can actually produce ~5 useful movements instead of accepting four at 31m.
            const targetExercises = session.targetExercises;
            if (targetExercises === undefined || session.exercises.length >= targetExercises)
                break;
            const intentPool = (INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full)
                .map(muscle => {
                if (muscle === 'front_delts' && request.goal.musclePriorities.front_delts === 'normal')
                    return null;
                const prescription = prescriptionMap.get(muscle);
                if (!prescription)
                    return null;
                const target = usefulTarget(prescription);
                if (target <= 0 || totals[muscle] + .001 >= target)
                    return null;
                const deficit = (target - totals[muscle]) / Math.max(1, target);
                const priority = priorityRank[request.goal.musclePriorities[muscle] ?? 'normal'];
                return { muscle, prescription, target, deficit, priority };
            }).filter(Boolean);
            intentPool.sort((a, b) => b.priority - a.priority || b.deficit - a.deficit || a.muscle.localeCompare(b.muscle));
            let appended = false;
            for (const need of intentPool) {
                const allocation = {
                    id: `time-band-${session.id}-${need.muscle}-${guard}`, kind: 'muscle', muscle: need.muscle,
                    role: SMALL.includes(need.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound', dose: 2,
                    importance: (request.goal.musclePriorities[need.muscle] === 'primary' || request.goal.musclePriorities[need.muscle] === 'specialization') ? 'A' : request.goal.musclePriorities[need.muscle] === 'high' ? 'B' : 'C'
                };
                const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((x) => !!x);
                const familyCap = need.prescription.priority === 'high' || need.prescription.priority === 'specialization' || need.prescription.priority === 'primary' ? 2 : 1;
                const alternate = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 0, familyCap);
                if (!alternate)
                    continue;
                const remaining = Math.max(1, Math.floor(need.prescription.upper - totals[need.muscle]));
                const sets = Math.min(2, remaining);
                const added = makePlanned(alternate, allocation.role, sets, policy, request.athlete.experience);
                const proposal = [...session.exercises, added];
                const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                if (optimized.estimatedMinutes > session.maxMinutes)
                    continue;
                session.exercises = optimized.exercises;
                session.estimatedMinutes = optimized.estimatedMinutes;
                trackExerciseUse(alternate);
                appended = true;
                break;
            }
            if (!appended)
                break;
        }
    }
/* M230:PRESERVE:stage.expandProductiveCapacity:END */

}
