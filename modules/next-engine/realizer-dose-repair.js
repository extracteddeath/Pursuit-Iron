// M230 canonical dose-repair stage. Stage context is local to one realization.
import { productiveTimeBandDoseTarget } from './prescription.js';
import { assignAccessorySupersets } from './realizer-pairing.js';
import { makePlanned } from './realizer-prescriptions.js';
import { LARGE, SMALL, floorSpilloverAllowed, intentAcceptsMuscle, muscleCandidate, primaryMuscle } from './realizer-ranking.js';
import { estimateMinutes, fitCompactRestToSession } from './realizer-time-budget.js';

export function fitProposalBySafeDosage(context, ...args) {
const { capacityPrescriptions, currentFractional, exerciseMap, prescriptionMap, request, totalsWithSessionProposal } = context;
return (
/* M230:PRESERVE:helper.fitProposalBySafeDosage:BEGIN */
(session, initial, protectedIndex) => {
        const optimizeSupersets = (exercises) => assignAccessorySupersets({ ...session, exercises, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
        let proposal = optimizeSupersets(initial.map(ex => ({ ...ex })));
        let minutes = estimateMinutes(proposal);
        if (minutes <= session.maxMinutes)
            return proposal;
        const baseline = currentFractional();
        const pr = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
        for (let guard = 0; guard < 24 && minutes > session.maxMinutes; guard++) {
            const candidates = proposal.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter(x => x.index !== protectedIndex && x.def)
                .filter(x => x.exercise.sets > (x.exercise.role === 'primary_strength' || x.exercise.role === 'secondary_strength' ? 2 : 1))
                .map(x => {
                const next = optimizeSupersets(proposal.map((ex, i) => i === x.index ? { ...ex, sets: ex.sets - 1 } : ex));
                const totals = totalsWithSessionProposal(session.id, next);
                const safe = capacityPrescriptions.filter(p => p.muscle !== 'front_delts' && p.minimum > 0).every(p => {
                    const required = Math.min(baseline[p.muscle], p.minimum * .85);
                    return totals[p.muscle] + .001 >= required;
                });
                const primary = primaryMuscle(x.def);
                const priority = primary ? (request.goal.musclePriorities[primary] ?? 'normal') : 'normal';
                const strength = x.exercise.role === 'primary_strength' || x.exercise.role === 'secondary_strength';
                const roleRank = x.exercise.role === 'primary_strength' ? 2 : x.exercise.role === 'secondary_strength' ? 1 : 0;
                const surplus = primary ? Math.max(0, baseline[primary] - (prescriptionMap.get(primary)?.minimum ?? 0)) : 0;
                return { ...x, next, safe, priority, roleRank, strength, surplus };
            })
                .filter(x => x.safe)
                .sort((a, b) => {
                if (a.strength !== b.strength)
                    return a.strength ? 1 : -1;
                if (pr[a.priority] !== pr[b.priority])
                    return pr[a.priority] - pr[b.priority];
                if (a.roleRank !== b.roleRank)
                    return a.roleRank - b.roleRank;
                return b.surplus - a.surplus || b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
            });
            const chosen = candidates[0];
            if (!chosen)
                break;
            proposal = chosen.next;
            minutes = estimateMinutes(proposal);
        }
        return minutes <= session.maxMinutes ? proposal : null;
    }
/* M230:PRESERVE:helper.fitProposalBySafeDosage:END */
)(...args);
}

export function satisfyDirectFloors(context) {
const { capacityPrescriptions, currentDirect, currentFractional, exerciseCatalog, exerciseMap, policy, realized, request, smallIntentPreference, trackExerciseUse, weeklyMovementUse } = context;
/* M230:PRESERVE:stage.satisfyDirectFloors:BEGIN */
for (const p of capacityPrescriptions.filter(p => p.priority === 'high' || p.priority === 'specialization' || p.priority === 'primary')) {
        const totals = currentFractional(), direct = currentDirect();
        const need = Math.max(0, p.minimum - totals[p.muscle], p.directMinimum - direct[p.muscle]);
        if (need < .75)
            continue;
        const sessionCandidates = [...realized].filter(session => intentAcceptsMuscle(session.intent, p.muscle)).sort((a, b) => {
            const prefs = smallIntentPreference[p.muscle] ?? [];
            const ai = prefs.indexOf(a.intent), bi = prefs.indexOf(b.intent);
            return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi) || a.estimatedMinutes - b.estimatedMinutes;
        });
        for (const session of sessionCandidates) {
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const role = SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
            const allocation = { id: `priority-repair-${session.id}-${p.muscle}`, kind: 'muscle', muscle: p.muscle, role, dose: need, importance: p.priority === 'high' ? 'B' : 'A' };
            const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog);
            if (!def)
                continue;
            const sets = Math.max(1, Math.min(4, Math.ceil(need)));
            const added = makePlanned(def, role, sets, policy, request.athlete.experience);
            const proposal = [...session.exercises, added];
            if (estimateMinutes(proposal) > session.maxMinutes)
                continue;
            session.exercises = proposal;
            session.estimatedMinutes = estimateMinutes(proposal);
            trackExerciseUse(def);
            break;
        }
    }
/* M230:PRESERVE:stage.satisfyDirectFloors:END */

}

export function satisfySmallMuscleFloors(context) {
const { capacityPrescriptions, currentFractional, exerciseCatalog, exerciseMap, policy, realized, request, smallIntentPreference, trackExerciseUse, weeklyMovementUse } = context;
/* M230:PRESERVE:stage.satisfySmallMuscleFloors:BEGIN */
for (const p of capacityPrescriptions.filter(p => p.minimum > 0 && p.muscle !== 'front_delts')) {
        const totals = currentFractional();
        const auditFloor = p.minimum * .85;
        if (totals[p.muscle] + .001 >= auditFloor)
            continue;
        const need = Math.max(1, Math.ceil(p.minimum - totals[p.muscle]));
        const candidates = [...realized].filter(session => intentAcceptsMuscle(session.intent, p.muscle)).sort((a, b) => {
            const prefs = smallIntentPreference[p.muscle] ?? [];
            const ai = prefs.indexOf(a.intent), bi = prefs.indexOf(b.intent);
            const pref = (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
            return pref || a.estimatedMinutes - b.estimatedMinutes;
        });
        for (const session of candidates) {
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const role = SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
            const allocation = { id: `floor-repair-${session.id}-${p.muscle}`, kind: 'muscle', muscle: p.muscle, role, dose: need, importance: 'B' };
            const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 1);
            if (!def)
                continue;
            // Floor repair should actually target the missing muscle, not count a minor .25 carryover as direct work.
            if ((def.muscles[p.muscle]?.credit ?? 0) < 1)
                continue;
            const sets = Math.min(3, need);
            const added = makePlanned(def, role, sets, policy, request.athlete.experience);
            const proposal = [...session.exercises, added];
            if (estimateMinutes(proposal) > session.maxMinutes)
                continue;
            session.exercises = proposal;
            session.estimatedMinutes = estimateMinutes(proposal);
            trackExerciseUse(def);
            break;
        }
    }
/* M230:PRESERVE:stage.satisfySmallMuscleFloors:END */

}

export function repairMinimumFloors(context, ...args) {
const { capacityPrescriptions, currentFractional, exerciseCatalog, exerciseMap, fitProposalBySafeDosage, policy, realized, request, smallIntentPreference, trackExerciseUse, weeklyMovementUse } = context;
return (
/* M230:PRESERVE:helper.repairMinimumFloors:BEGIN */
() => {
        const maxSetsPerExercise = request.preferences.volumeApproach === 'minimalist' ? 3 : 4;
        for (const p of capacityPrescriptions.filter(p => p.minimum > 0 && p.muscle !== 'front_delts')) {
            const totals = currentFractional();
            const auditFloor = p.minimum * .85;
            if (totals[p.muscle] + .001 >= auditFloor)
                continue;
            const need = Math.max(1, Math.ceil(p.minimum - totals[p.muscle]));
            const candidates = [...realized]
                .filter(session => intentAcceptsMuscle(session.intent, p.muscle) || floorSpilloverAllowed(session.intent, p.muscle, request))
                .sort((a, b) => {
                const an = intentAcceptsMuscle(a.intent, p.muscle), bn = intentAcceptsMuscle(b.intent, p.muscle);
                if (an !== bn)
                    return an ? -1 : 1;
                const prefs = smallIntentPreference[p.muscle] ?? [];
                const ai = prefs.indexOf(a.intent), bi = prefs.indexOf(b.intent);
                const pref = (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
                return pref || a.exercises.length - b.exercises.length || (b.maxMinutes - b.estimatedMinutes) - (a.maxMinutes - a.estimatedMinutes) || a.id.localeCompare(b.id);
            });
            for (const session of candidates) {
                const spillover = !intentAcceptsMuscle(session.intent, p.muscle);
                // Prefer extending an already-selected direct movement. It avoids another setup transition and is
                // usually the most coach-like way to repair a one-set floor miss late in realization.
                const existing = session.exercises
                    .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def)
                    .filter(x => (x.def.muscles[p.muscle]?.credit ?? 0) >= (p.directMinimum > 0 ? 1 : .25) && x.exercise.sets < maxSetsPerExercise)
                    .filter(x => !spillover || !x.def.flags.compound)
                    .sort((a, b) => a.def.setupCost - b.def.setupCost || a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId))[0];
                if (existing) {
                    const credit = Math.max(.25, existing.def.muscles[p.muscle]?.credit ?? 0);
                    const creditNeed = Math.max(1, Math.ceil((auditFloor - currentFractional()[p.muscle]) / credit));
                    const maxAdd = Math.min(creditNeed, maxSetsPerExercise - existing.exercise.sets);
                    for (let addSets = maxAdd; addSets >= 1; addSets--) {
                        const proposal = session.exercises.map((ex, i) => i === existing.index ? { ...ex, sets: ex.sets + addSets } : ex);
                        const compact = fitCompactRestToSession(session, proposal);
                        const fitted = compact ?? fitProposalBySafeDosage(session, proposal, existing.index);
                        if (!fitted)
                            continue;
                        session.exercises = fitted;
                        session.estimatedMinutes = estimateMinutes(fitted);
                        break;
                    }
                    if (currentFractional()[p.muscle] + .001 >= auditFloor)
                        break;
                }
                const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                const role = spillover || SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
                const allocation = { id: `post-structure-floor-${session.id}-${p.muscle}`, kind: 'muscle', muscle: p.muscle, role, dose: need, importance: 'B' };
                const minimumCredit = p.directMinimum > 0 ? 1 : .25;
                const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, minimumCredit, minimumCredit < 1 ? 2 : undefined);
                if (!def || (spillover && def.flags.compound))
                    continue;
                // A new cross-split movement should be a real accessory dose rather than a one-set fragment.
                // Existing direct movements may still absorb a single missing set above.
                const credit = Math.max(.25, def.muscles[p.muscle]?.credit ?? 0);
                const creditNeed = Math.max(1, Math.ceil((auditFloor - currentFractional()[p.muscle]) / credit));
                const upperRoom = Math.max(1, Math.floor((p.upper - currentFractional()[p.muscle]) / credit));
                const usefulNewSets = Math.min(maxSetsPerExercise, upperRoom, Math.max(spillover ? 2 : 1, Math.min(maxSetsPerExercise, creditNeed)));
                for (const sets of [usefulNewSets, 1]) {
                    if (spillover && sets < 2)
                        continue;
                    if (sets <= 0)
                        continue;
                    const added = makePlanned(def, role, sets, policy, request.athlete.experience);
                    let proposal = [...session.exercises, added];
                    const compact = fitCompactRestToSession(session, proposal);
                    const fitted = compact ?? fitProposalBySafeDosage(session, proposal, proposal.length - 1);
                    if (!fitted)
                        continue;
                    proposal = fitted;
                    const minutes = estimateMinutes(proposal);
                    session.exercises = proposal;
                    session.estimatedMinutes = minutes;
                    trackExerciseUse(def);
                    break;
                }
                if (currentFractional()[p.muscle] + .001 >= auditFloor)
                    break;
            }
        }
    }
/* M230:PRESERVE:helper.repairMinimumFloors:END */
)(...args);
}

export function satisfyDedicatedIntentFloors(context) {
const { currentFractional, dedicatedIntentMuscles, exerciseCatalog, exerciseMap, fitProposalBySafeDosage, policy, prescriptionMap, realized, request, trackExerciseUse, weeklyMovementUse } = context;
/* M230:PRESERVE:stage.satisfyDedicatedIntentFloors:BEGIN */
for (const session of realized) {
        const targets = dedicatedIntentMuscles[session.intent];
        if (!targets || session.exercises.length >= 2)
            continue;
        const totals = currentFractional();
        const ordered = [...targets].sort((a, b) => {
            const pa = prescriptionMap.get(a), pb = prescriptionMap.get(b);
            const ar = (pa.preferred - totals[a]) + Math.max(0, pa.upper - totals[a]) * .25;
            const br = (pb.preferred - totals[b]) + Math.max(0, pb.upper - totals[b]) * .25;
            return br - ar || a.localeCompare(b);
        });
        for (const muscle of ordered) {
            const p = prescriptionMap.get(muscle);
            if (totals[muscle] >= p.upper)
                continue;
            const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const role = SMALL.includes(muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
            const allocation = { id: `intent-repair-${session.id}-${muscle}`, kind: 'muscle', muscle, role, dose: 2, importance: 'B' };
            const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 1);
            if (!def)
                continue;
            const room = Math.max(1, Math.floor(p.upper - totals[muscle]));
            const sets = Math.max(1, Math.min(2, room));
            const added = makePlanned(def, role, sets, policy, request.athlete.experience);
            const fitted = fitProposalBySafeDosage(session, [...session.exercises, added], session.exercises.length);
            if (!fitted)
                continue;
            session.exercises = fitted;
            session.estimatedMinutes = estimateMinutes(fitted);
            trackExerciseUse(def);
            break;
        }
    }
/* M230:PRESERVE:stage.satisfyDedicatedIntentFloors:END */

}

export function fitPreferredDosage(context) {
const { capacityPrescriptions, currentFractional, exerciseMap, finalStructureValid, realized, request, totalsWithSessionProposal } = context;
/* M230:PRESERVE:stage.fitPreferredDosage:BEGIN */
for (const session of realized) {
        if (session.targetExercises === undefined)
            continue;
        // An exercise-count selection is a complexity budget at every time tier, not only on short days.
        // Long sessions can use more sets/rest; they should not fill spare clock time with setup-heavy
        // accessory sprawl. Permit one exercise of practical slack for floor/identity repair.
        const maxExercises = Math.max(3, session.targetExercises + 1);
        for (let guard = 0; guard < 12 && session.exercises.length > maxExercises; guard++) {
            const baseline = currentFractional();
            const candidates = session.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter((x) => !!x.def)
                .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength')
                .map(x => {
                const primary = primaryMuscle(x.def);
                const priority = primary ? (request.goal.musclePriorities[primary] ?? 'normal') : 'normal';
                const proposal = session.exercises.filter((_, i) => i !== x.index);
                const totals = totalsWithSessionProposal(session.id, proposal);
                const safe = finalStructureValid(session, proposal) && capacityPrescriptions.filter(p => p.muscle !== 'front_delts' && p.minimum > 0).every(p => {
                    const required = Math.min(baseline[p.muscle], p.minimum * .85);
                    return totals[p.muscle] + .001 >= required;
                });
                const fatigue = x.def.fatigue.systemic + x.def.fatigue.axial + x.def.fatigue.lowerBack;
                return { ...x, primary, priority, proposal, safe, fatigue };
            })
                .filter(x => x.safe && x.priority !== 'primary' && x.priority !== 'specialization' && x.priority !== 'high')
                .sort((a, b) => {
                const roleA = a.exercise.role === 'hypertrophy_isolation' ? 0 : 1, roleB = b.exercise.role === 'hypertrophy_isolation' ? 0 : 1;
                return roleA - roleB || a.exercise.sets - b.exercise.sets || b.fatigue - a.fatigue || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
            });
            const chosen = candidates[0];
            if (!chosen)
                break;
            session.exercises = chosen.proposal;
            session.estimatedMinutes = estimateMinutes(session.exercises);
        }
    }
/* M230:PRESERVE:stage.fitPreferredDosage:END */

}

export function reconcileAllocationTargets(context) {
const { capacityPrescriptions, currentFractional, exerciseCatalog, exerciseMap, finalStructureValid, phase, policy, prescriptionMap, realized, request, targetDose, totalsWithSessionProposal, trackExerciseUse, weeklyMovementUse } = context;
/* M230:PRESERVE:stage.reconcileAllocationTargets:BEGIN */
if (phase === 'hypertrophy_accumulation' || phase === 'mixed_accumulation') {
        const targetPriority = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
        const targets = capacityPrescriptions
            .filter(p => p.muscle !== 'front_delts' && p.minimum > 0 && (targetDose[p.muscle] ?? 0) > 0)
            .map(p => ({ p, target: Math.min(p.upper, targetDose[p.muscle] ?? p.minimum) }))
            .sort((a, b) => targetPriority[b.p.priority] - targetPriority[a.p.priority] ||
            (LARGE.includes(b.p.muscle) ? 1 : 0) - (LARGE.includes(a.p.muscle) ? 1 : 0) ||
            b.target - a.target || a.p.muscle.localeCompare(b.p.muscle));
        for (const { p, target } of targets) {
            for (let guard = 0; guard < 12; guard++) {
                const totals = currentFractional();
                if (totals[p.muscle] + .49 >= target)
                    break;
                const sessionsByRoom = [...realized]
                    .filter(session => intentAcceptsMuscle(session.intent, p.muscle))
                    .sort((a, b) => (b.maxMinutes - b.estimatedMinutes) - (a.maxMinutes - a.estimatedMinutes) || a.id.localeCompare(b.id));
                let repaired = false;
                // Prefer deepening an existing direct movement: no new setup cost and the weekly exercise
                // selection stays stable. One set at a time keeps the repair inside the modeled upper region.
                for (const session of sessionsByRoom) {
                    const existing = session.exercises
                        .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def)
                        .filter(x => {
                        const depthCap = x.exercise.role === 'hypertrophy_isolation' && (session.minMinutes ?? 0) >= 90 ? 5 : 4;
                        return (x.def.muscles[p.muscle]?.credit ?? 0) >= 1 && x.exercise.sets < depthCap && x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength';
                    })
                        .sort((a, b) => a.exercise.sets - b.exercise.sets || a.def.setupCost - b.def.setupCost || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                    for (const x of existing) {
                        const proposal = session.exercises.map((ex, i) => i === x.index ? { ...ex, sets: ex.sets + 1 } : ex);
                        const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                        if (proposedTotals[p.muscle] > p.upper + .001)
                            continue;
                        const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimized.estimatedMinutes > session.maxMinutes)
                            continue;
                        session.exercises = optimized.exercises;
                        session.estimatedMinutes = optimized.estimatedMinutes;
                        repaired = true;
                        break;
                    }
                    if (repaired)
                        break;
                }
                if (repaired)
                    continue;
                // Before adding another setup, rebalance a locally redundant accessory. Late time repair can
                // leave one intended muscle below its productive target while another muscle in the same
                // session still carries marginal work above the band-specific target. Swap that surplus first:
                // this preserves coach-like session density and restores the weekly dose mix without clock fill.
                const needsSubfloorRebalance = sessionsByRoom.some(session => {
                    const lower = session.minMinutes ?? 0;
                    return lower > 0 && session.estimatedMinutes + 0.001 < lower * .95;
                });
                for (const session of needsSubfloorRebalance ? sessionsByRoom : []) {
                    const desiredSets = Math.max(1, Math.min(2, Math.ceil(target - totals[p.muscle])));
                    const donors = session.exercises
                        .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def)
                        .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength')
                        .map(x => {
                        const muscle = primaryMuscle(x.def);
                        const donorPrescription = muscle ? prescriptionMap.get(muscle) : undefined;
                        const donorTarget = donorPrescription ? productiveTimeBandDoseTarget(request, donorPrescription, phase) : 0;
                        const surplus = muscle ? totals[muscle] - donorTarget : 0;
                        return { ...x, muscle, donorTarget, surplus };
                    })
                        .filter(x => x.muscle && x.muscle !== p.muscle && x.surplus >= .75)
                        .sort((a, b) => b.surplus - a.surplus || a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                    for (const donor of donors) {
                        const chosenDefs = session.exercises
                            .filter((_, i) => i !== donor.index)
                            .map(ex => exerciseMap.get(ex.exerciseId))
                            .filter((d) => !!d);
                        const role = SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
                        const allocation = { id: `target-swap-${session.id}-${p.muscle}-${guard}`, kind: 'muscle', muscle: p.muscle, role, dose: desiredSets, importance: p.priority === 'primary' || p.priority === 'specialization' ? 'A' : p.priority === 'high' ? 'B' : 'C' };
                        const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 1);
                        if (!def)
                            continue;
                        const replacement = makePlanned(def, role, desiredSets, policy, request.athlete.experience);
                        const proposal = session.exercises.map((ex, i) => i === donor.index ? replacement : ex);
                        if (!finalStructureValid(session, proposal))
                            continue;
                        const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                        if (proposedTotals[p.muscle] > p.upper + .001 || proposedTotals[p.muscle] <= totals[p.muscle] + .001)
                            continue;
                        const stealsUsefulDose = capacityPrescriptions
                            .filter(q => q.muscle !== p.muscle && q.muscle !== 'front_delts' && q.minimum > 0)
                            .some(q => {
                            const qTarget = Math.max(q.preferred, productiveTimeBandDoseTarget(request, q, phase));
                            return totals[q.muscle] + .49 >= qTarget && proposedTotals[q.muscle] + .49 < qTarget;
                        });
                        if (stealsUsefulDose)
                            continue;
                        const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimized.estimatedMinutes > session.maxMinutes)
                            continue;
                        session.exercises = optimized.exercises;
                        session.estimatedMinutes = optimized.estimatedMinutes;
                        trackExerciseUse(def);
                        repaired = true;
                        break;
                    }
                    if (repaired)
                        break;
                }
                if (repaired)
                    continue;
                // If no existing movement can absorb another productive set, use an intended slot only when
                // the session still has movement capacity. Do not create a new setup merely to chase a number.
                for (const session of sessionsByRoom) {
                    if (session.targetExercises !== undefined && session.exercises.length >= session.targetExercises + (needsSubfloorRebalance ? 2 : 1))
                        continue;
                    const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                    const role = SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
                    const allocation = { id: `target-reconcile-${session.id}-${p.muscle}-${guard}`, kind: 'muscle', muscle: p.muscle, role, dose: 1, importance: p.priority === 'primary' || p.priority === 'specialization' ? 'A' : p.priority === 'high' ? 'B' : 'C' };
                    const familyCap = p.priority === 'high' || p.priority === 'specialization' || p.priority === 'primary' ? 2 : 1;
                    const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog, 1, familyCap);
                    if (!def)
                        continue;
                    const added = makePlanned(def, role, 1, policy, request.athlete.experience);
                    const proposal = [...session.exercises, added];
                    const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                    if (proposedTotals[p.muscle] > p.upper + .001)
                        continue;
                    const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (optimized.estimatedMinutes > session.maxMinutes)
                        continue;
                    session.exercises = optimized.exercises;
                    session.estimatedMinutes = optimized.estimatedMinutes;
                    trackExerciseUse(def);
                    repaired = true;
                    break;
                }
                if (!repaired)
                    break;
            }
        }
    }
/* M230:PRESERVE:stage.reconcileAllocationTargets:END */

}

export function enforceFinalTimeLimits(context) {
const { fitProposalBySafeDosage, realized } = context;
/* M230:PRESERVE:stage.enforceFinalTimeLimits:BEGIN */
for (const session of realized) {
        session.estimatedMinutes = estimateMinutes(session.exercises);
        if (session.estimatedMinutes <= session.maxMinutes)
            continue;
        const compact = fitCompactRestToSession(session, session.exercises);
        if (compact) {
            session.exercises = compact;
            session.estimatedMinutes = estimateMinutes(compact);
            continue;
        }
        const fitted = fitProposalBySafeDosage(session, session.exercises, -1);
        if (!fitted)
            continue;
        session.exercises = fitted;
        session.estimatedMinutes = estimateMinutes(fitted);
    }
/* M230:PRESERVE:stage.enforceFinalTimeLimits:END */

}
