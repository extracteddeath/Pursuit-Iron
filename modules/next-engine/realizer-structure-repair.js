// M230 canonical structure-repair stage. Stage context is local to one realization.
import { avoidableExerciseOverlap, exerciseEconomyCluster } from './exercise-economy.js';
import { assignAccessorySupersets } from './realizer-pairing.js';
import { makePlanned } from './realizer-prescriptions.js';
import { equipmentEligible, maxBarbells, muscleCandidate, primaryMuscle } from './realizer-ranking.js';
import { estimateMinutes } from './realizer-time-budget.js';

export function repairFullBodyStructure(context, ...args) {
const { capacityPrescriptions, currentDirect, currentFractional, directTotalsWithSessionProposal, exerciseCatalog, exerciseMap, fitProposalBySafeDosage, policy, prescriptionMap, realized, request, totalsWithSessionProposal, trackExerciseUse, weeklyMovementUse } = context;
return (
/* M230:PRESERVE:helper.repairFullBodyStructure:BEGIN */
() => {
        if (realized.some(s => s.intent === 'full' || s.intent === 'strength_full')) {
            const regionStatus = (session) => {
                const defs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                return {
                    push: defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily)),
                    pull: defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily)),
                    lower: defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
                };
            };
            for (const session of realized.filter(s => s.intent === 'full' || s.intent === 'strength_full')) {
                for (let guard = 0; guard < 3; guard++) {
                    const status = regionStatus(session);
                    const missing = !status.push ? 'push' : !status.pull ? 'pull' : !status.lower ? 'lower' : null;
                    if (!missing)
                        break;
                    const totals = currentFractional();
                    const targetMuscles = missing === 'push' ? ['chest'] : missing === 'pull' ? ['back'] : ['quads', 'hamstrings', 'glutes'];
                    let repaired = false;
                    for (const muscle of targetMuscles.sort((a, b) => {
                        const pa = prescriptionMap.get(a), pb = prescriptionMap.get(b);
                        return (pb.preferred - totals[b]) - (pa.preferred - totals[a]);
                    })) {
                        const allocation = { id: `full-body-region-${session.id}-${muscle}`, kind: 'muscle', muscle, role: 'hypertrophy_compound', dose: 2, importance: 'B' };
                        const chosenDefs = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                        const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog);
                        if (!def)
                            continue;
                        // Full-body identity is structural, not a two-set volume mandate. Try the normal
                        // two-set repair first, but if it cannot fit without violating protected weekly
                        // minimums, retain a one-set push/pull/lower exposure rather than returning a
                        // label-only Full Body session. The normal weekly dose ledger remains authoritative.
                        const structuralSetOptions = session.maxMinutes <= 35 ? [1] : [2, 1];
                        let proposal = null;
                        for (const structuralSets of structuralSetOptions) {
                            const added = makePlanned(def, 'hypertrophy_compound', structuralSets, policy, request.athlete.experience);
                            const candidate = [...session.exercises, added];
                            const fitted = fitProposalBySafeDosage(session, candidate, candidate.length - 1);
                            if (fitted) {
                                proposal = fitted;
                                break;
                            }
                        }
                        if (!proposal)
                            continue;
                        const minutes = estimateMinutes(proposal);
                        session.exercises = proposal;
                        session.estimatedMinutes = minutes;
                        trackExerciseUse(def);
                        repaired = true;
                        break;
                    }
                    // Appending a missing region is preferred, but a full-body day can already be at its
                    // useful movement/time capacity. In that case the structural promise outranks an optional
                    // normal-priority slot: replace the cheapest dispensable movement with the missing region
                    // instead of returning a label-only Full Body session. The swap is dose/floor audited and
                    // may never remove another required push/pull/lower region.
                    if (!repaired) {
                        const beforeDirect = currentDirect();
                        const donors = session.exercises
                            .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                            .filter(x => x.def && !['primary_strength', 'secondary_strength', 'strength_support', 'specialization'].includes(x.exercise.role))
                            .map(x => {
                                const primary = primaryMuscle(x.def);
                                const priority = primary ? (request.goal.musclePriorities[primary] ?? 'normal') : 'normal';
                                const fatigue = x.def.fatigue.systemic + x.def.fatigue.axial + x.def.fatigue.lowerBack;
                                return { ...x, primary, priority, fatigue };
                            })
                            .filter(x => x.priority !== 'primary' && x.priority !== 'specialization' && x.priority !== 'high')
                            .sort((a, b) => a.exercise.sets - b.exercise.sets || b.fatigue - a.fatigue || b.def.setupCost - a.def.setupCost || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                        donorLoop: for (const donor of donors) {
                            const withoutDonor = session.exercises.filter((_, i) => i !== donor.index);
                            for (const muscle of targetMuscles) {
                                const allocation = { id: 'full-body-region-swap-' + session.id + '-' + muscle, kind: 'muscle', muscle, role: 'hypertrophy_compound', dose: 2, importance: 'B' };
                                const chosenDefs = withoutDonor.map(ex => exerciseMap.get(ex.exerciseId)).filter(Boolean);
                                const def = muscleCandidate(allocation, session, request, chosenDefs, weeklyMovementUse, exerciseCatalog);
                                if (!def || withoutDonor.some(ex => ex.exerciseId === def.id))
                                    continue;
                                const sets = Math.max(1, Math.min(2, donor.exercise.sets || 1));
                                const replacementExercise = makePlanned(def, 'hypertrophy_compound', sets, policy, request.athlete.experience);
                                const proposal = session.exercises.map((ex, i) => i === donor.index ? replacementExercise : ex);
                                const after = regionStatus({ ...session, exercises: proposal });
                                if (!after.push || !after.pull || !after.lower)
                                    continue;
                                const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                                const proposedDirect = directTotalsWithSessionProposal(session.id, proposal);
                                const safe = capacityPrescriptions.filter(p => p.muscle !== 'front_delts').every(p => {
                                    const fractionalRequired = Math.min(totals[p.muscle], p.minimum * .85);
                                    const directRequired = Math.min(beforeDirect[p.muscle] ?? 0, p.directMinimum ?? 0);
                                    return proposedTotals[p.muscle] + .001 >= fractionalRequired && proposedDirect[p.muscle] + .001 >= directRequired;
                                });
                                if (!safe)
                                    continue;
                                const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                                if (optimized.estimatedMinutes > session.maxMinutes)
                                    continue;
                                session.exercises = optimized.exercises;
                                session.estimatedMinutes = optimized.estimatedMinutes;
                                trackExerciseUse(def);
                                repaired = true;
                                break donorLoop;
                            }
                        }
                    }
                    if (!repaired)
                        break;
                }
            }
        }
    }
/* M230:PRESERVE:helper.repairFullBodyStructure:END */
)(...args);
}

export function rebalanceRepeatedIntents(context, ...args) {
const { exerciseMap, realized, request } = context;
return (
/* M230:PRESERVE:helper.rebalanceRepeatedIntents:BEGIN */
() => {
        const structureValid = (intent, exercises) => {
            const defs = exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const push = defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily));
            const pull = defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily));
            const lower = defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily));
            if (intent === 'full' || intent === 'strength_full')
                return push && pull && lower;
            if (intent === 'upper')
                return push && pull;
            if (intent === 'lower' || intent === 'legs' || intent === 'glute')
                return lower;
            if (intent === 'push' || intent === 'chest' || intent === 'bench_focus' || intent === 'press_focus')
                return push;
            if (intent === 'pull' || intent === 'back_day' || intent === 'deadlift_focus')
                return pull || lower;
            return true;
        };
        const groups = new Map();
        for (const session of realized) {
            const list = groups.get(session.intent) ?? [];
            list.push(session);
            groups.set(session.intent, list);
        }
        for (const group of groups.values()) {
            if (group.length < 2)
                continue;
            for (let pass = 0; pass < 40; pass++) {
                const ordered = [...group].sort((a, b) => a.exercises.length - b.exercises.length || a.id.localeCompare(b.id));
                const receiver = ordered[0], donor = ordered[ordered.length - 1];
                if (donor.exercises.length - receiver.exercises.length <= 1)
                    break;
                const receiverIds = new Set(receiver.exercises.map(ex => ex.exerciseId));
                const candidates = donor.exercises
                    .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def)
                    .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && x.exercise.role !== 'strength_support')
                    .filter(x => equipmentEligible(x.def, receiver, request))
                    .filter(x => !x.def.flags.barbell || receiver.exercises.filter(ex => exerciseMap.get(ex.exerciseId)?.flags.barbell).length < maxBarbells(receiver, request))
                    .filter(x => !receiverIds.has(x.exercise.exerciseId))
                    .filter(x => {
                    const muscle = primaryMuscle(x.def);
                    const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal';
                    const chosen = receiver.exercises.map(ex => ({ def: exerciseMap.get(ex.exerciseId), role: ex.role })).filter((x) => !!x.def);
                    return !avoidableExerciseOverlap(x.def, x.exercise.role, chosen, { priority });
                })
                    .filter(x => {
                    if (x.def.flags.compound)
                        return true;
                    const muscle = primaryMuscle(x.def);
                    const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal';
                    if (priority === 'high' || priority === 'specialization' || priority === 'primary')
                        return true;
                    const signature = (def) => Object.entries(def.muscles).sort(([a], [b]) => a.localeCompare(b)).map(([m, c]) => `${m}:${c?.credit ?? 0}`).join('|');
                    const sameFamily = receiver.exercises.map((ex, index) => ({ ex, index, def: exerciseMap.get(ex.exerciseId) })).find(y => !!y.def && !y.def.flags.compound && exerciseEconomyCluster(y.def) === exerciseEconomyCluster(x.def) && primaryMuscle(y.def) === muscle);
                    if (!sameFamily)
                        return true;
                    const donorKeepsFamily = donor.exercises.some((ex, i) => i !== x.index && exerciseEconomyCluster(exerciseMap.get(ex.exerciseId)) === exerciseEconomyCluster(x.def));
                    return donorKeepsFamily && !!sameFamily.def && signature(sameFamily.def) === signature(x.def) && sameFamily.ex.sets + x.exercise.sets <= 5;
                })
                    .sort((a, b) => {
                    const ap = primaryMuscle(a.def), bp = primaryMuscle(b.def);
                    const pr = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
                    const aPriority = ap ? (request.goal.musclePriorities[ap] ?? 'normal') : 'normal';
                    const bPriority = bp ? (request.goal.musclePriorities[bp] ?? 'normal') : 'normal';
                    if (pr[aPriority] !== pr[bPriority])
                        return pr[aPriority] - pr[bPriority];
                    const af = a.def.fatigue.systemic + a.def.fatigue.axial + a.def.fatigue.lowerBack;
                    const bf = b.def.fatigue.systemic + b.def.fatigue.axial + b.def.fatigue.lowerBack;
                    return af - bf || a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId);
                });
                let moved = false;
                for (const candidate of candidates) {
                    const donorProposal = donor.exercises.filter((_, i) => i !== candidate.index);
                    if (donorProposal.length < 2 || !structureValid(donor.intent, donorProposal))
                        continue;
                    const signature = (def) => Object.entries(def.muscles).sort(([a], [b]) => a.localeCompare(b)).map(([m, c]) => `${m}:${c?.credit ?? 0}`).join('|');
                    let receiverProposal;
                    const mergeIndex = !candidate.def.flags.compound ? receiver.exercises.findIndex(ex => {
                        const def = exerciseMap.get(ex.exerciseId);
                        return !!def && !def.flags.compound && exerciseEconomyCluster(def) === exerciseEconomyCluster(candidate.def) && signature(def) === signature(candidate.def) && ex.sets + candidate.exercise.sets <= 5;
                    }) : -1;
                    const donorKeepsFamily = donor.exercises.some((ex, i) => i !== candidate.index && exerciseEconomyCluster(exerciseMap.get(ex.exerciseId)) === exerciseEconomyCluster(candidate.def));
                    if (mergeIndex >= 0 && donorKeepsFamily)
                        receiverProposal = receiver.exercises.map((ex, i) => i === mergeIndex ? { ...ex, sets: ex.sets + candidate.exercise.sets } : ex);
                    else
                        receiverProposal = [...receiver.exercises, candidate.exercise];
                    const receiverOptimized = assignAccessorySupersets({ ...receiver, exercises: receiverProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (receiverOptimized.estimatedMinutes > receiver.maxMinutes)
                        continue;
                    donor.exercises = assignAccessorySupersets({ ...donor, exercises: donorProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                    donor.estimatedMinutes = estimateMinutes(donor.exercises);
                    receiver.exercises = receiverOptimized.exercises;
                    receiver.estimatedMinutes = receiverOptimized.estimatedMinutes;
                    moved = true;
                    break;
                }
                if (!moved)
                    break;
            }
        }
    }
/* M230:PRESERVE:helper.rebalanceRepeatedIntents:END */
)(...args);
}

export function finalStructureValid(context, ...args) {
const { exerciseMap } = context;
return (
/* M230:PRESERVE:helper.finalStructureValid:BEGIN */
(session, exercises) => {
        const defs = exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
        const push = defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily));
        const pull = defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily));
        const lower = defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily));
        if (session.intent === 'full' || session.intent === 'strength_full')
            return push && pull && lower;
        if (session.intent === 'upper')
            return push && pull;
        if (session.intent === 'lower' || session.intent === 'legs' || session.intent === 'glute')
            return lower;
        if (session.intent === 'push' || session.intent === 'chest' || session.intent === 'bench_focus' || session.intent === 'press_focus')
            return push;
        if (session.intent === 'pull' || session.intent === 'back_day' || session.intent === 'deadlift_focus')
            return pull || lower;
        return true;
    }
/* M230:PRESERVE:helper.finalStructureValid:END */
)(...args);
}

export function redistributeUpperAccessories(context) {
const { exerciseMap, lowerMovementFamilies, realized, request, upperAccessoryFamilies, upperAccessoryIntents } = context;
/* M230:PRESERVE:stage.redistributeUpperAccessories:BEGIN */
for (const source of realized.filter(session => session.intent === 'lower' || session.intent === 'legs')) {
        for (let guard = 0; guard < 4; guard++) {
            const lowerCount = source.exercises.filter(ex => lowerMovementFamilies.has(exerciseMap.get(ex.exerciseId)?.movementFamily ?? '')).length;
            const accessoryEntries = source.exercises
                .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter((x) => !!x.def)
                .filter(x => upperAccessoryFamilies.has(x.def.movementFamily) && !x.def.flags.compound);
            if (lowerCount >= 2 || !accessoryEntries.length)
                break;
            let moved = false;
            for (const item of accessoryEntries.sort((a, b) => a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId))) {
                const sourceProposal = source.exercises.filter((_, i) => i !== item.index);
                if (!sourceProposal.some(ex => lowerMovementFamilies.has(exerciseMap.get(ex.exerciseId)?.movementFamily ?? '')))
                    continue;
                const destinations = realized
                    .filter(session => session.id !== source.id && upperAccessoryIntents.has(session.intent))
                    .map(session => {
                    const existing = session.exercises.findIndex(ex => ex.exerciseId === item.exercise.exerciseId && ex.role === item.exercise.role);
                    let proposal;
                    if (existing >= 0) {
                        if (session.exercises[existing].sets + item.exercise.sets > 5)
                            return null;
                        proposal = session.exercises.map((ex, i) => i === existing ? { ...ex, sets: ex.sets + item.exercise.sets } : ex);
                    }
                    else {
                        const muscle = primaryMuscle(item.def);
                        const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal';
                        const chosen = session.exercises.map(ex => ({ def: exerciseMap.get(ex.exerciseId), role: ex.role }))
                            .filter((x) => !!x.def);
                        if (avoidableExerciseOverlap(item.def, item.exercise.role, chosen, { priority })) {
                            const cluster = exerciseEconomyCluster(item.def);
                            const mergeIndex = session.exercises.findIndex(ex => {
                                const def = exerciseMap.get(ex.exerciseId);
                                return !!def && !def.flags.compound && exerciseEconomyCluster(def) === cluster && primaryMuscle(def) === muscle && ex.sets + item.exercise.sets <= 5;
                            });
                            if (mergeIndex < 0)
                                return null;
                            proposal = session.exercises.map((ex, i) => i === mergeIndex ? { ...ex, sets: ex.sets + item.exercise.sets } : ex);
                        }
                        else
                            proposal = [...session.exercises, { ...item.exercise }];
                    }
                    const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    return optimized.estimatedMinutes <= session.maxMinutes ? { session, optimized } : null;
                })
                    .filter(Boolean);
                destinations.sort((a, b) => a.optimized.estimatedMinutes - b.optimized.estimatedMinutes || a.session.id.localeCompare(b.session.id));
                const target = destinations[0];
                if (!target)
                    continue;
                source.exercises = assignAccessorySupersets({ ...source, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                source.estimatedMinutes = estimateMinutes(source.exercises);
                target.session.exercises = target.optimized.exercises;
                target.session.estimatedMinutes = target.optimized.estimatedMinutes;
                moved = true;
                break;
            }
            if (!moved)
                break;
        }
    }
/* M230:PRESERVE:stage.redistributeUpperAccessories:END */

}

export function restoreFinalFloors(context) {
const { exerciseMap, realized, repairMinimumFloors, request } = context;
/* M230:PRESERVE:stage.restoreFinalFloors:BEGIN */
if (request.preferences.volumeApproach === 'minimalist') {
        // Same movement plan, lower dose, harder work. Keep heavy strength anchors just shy of true
        // failure while hypertrophy work lives at the 0–1 RIR target promised by the UI.
        // IMPORTANT: this cap used to run after all floor repairs and could therefore undo them (for
        // example 4 squat sets repaired to the quad floor were silently cut back to 3). Keep the
        // per-exercise <=3 contract, then re-run the floor allocator with that same cap so any remaining
        // deficit is distributed to another useful movement rather than invalidating the finished block.
        for (let i = 0; i < realized.length; i++) {
            const exercises = realized[i].exercises.map(ex => {
                const strength = ex.role === 'primary_strength' || ex.role === 'secondary_strength';
                return { ...ex, sets: Math.min(3, ex.sets), prescription: { ...ex.prescription, rir: strength ? [1, 2] : [0, 1] } };
            });
            realized[i] = { ...realized[i], exercises, estimatedMinutes: estimateMinutes(exercises) };
        }
        for (let i = 0; i < realized.length; i++)
            realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
        repairMinimumFloors();
        for (let i = 0; i < realized.length; i++)
            realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    }
/* M230:PRESERVE:stage.restoreFinalFloors:END */

}
