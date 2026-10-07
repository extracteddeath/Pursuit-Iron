// M230 canonical fragment-repair stage. Stage context is local to one realization.
import { exerciseEconomyCluster } from './exercise-economy.js';
import { publicMevContractApplies } from './public-mev.js';
import { assignAccessorySupersets } from './realizer-pairing.js';
import { equipmentEligible, intentAcceptsMuscle, primaryMuscle } from './realizer-ranking.js';
import { estimateMinutes } from './realizer-time-budget.js';

export function consolidateOneSetDuplicates(context, ...args) {
const { exerciseMap, finalStructureValid, realized, request } = context;
return (
/* M230:PRESERVE:helper.consolidateOneSetDuplicates:BEGIN */
() => {
        for (let pass = 0; pass < 80; pass++) {
            const sources = [...realized].sort((a, b) => {
                const af = a.exercises.filter(ex => ex.sets === 1).length, bf = b.exercises.filter(ex => ex.sets === 1).length;
                return bf - af || b.exercises.length - a.exercises.length || a.id.localeCompare(b.id);
            });
            let moved = false;
            for (const source of sources) {
                const oneSetCount = source.exercises.filter(ex => ex.sets === 1).length;
                const target = source.targetExercises;
                const crowded = target !== undefined && source.exercises.length > Math.max(3, target + 1);
                const extremeLongFragmentation = source.maxMinutes > 45 && oneSetCount >= 4 && source.exercises.length >= 9;
                const hasConsolidatableCompound = source.exercises.some(fragment => {
                    if (fragment.sets !== 1 || fragment.role === 'primary_strength' || fragment.role === 'secondary_strength')
                        return false;
                    const def = exerciseMap.get(fragment.exerciseId);
                    if (!def?.flags.compound)
                        return false;
                    const muscle = primaryMuscle(def);
                    return realized.some(session => session.id !== source.id && (!muscle || intentAcceptsMuscle(session.intent, muscle)) && session.exercises.some(ex => ex.exerciseId === fragment.exerciseId && ex.role === fragment.role && ex.sets < 4));
                });
                // Normal long sessions keep their planned distribution unless the engine produced an exact
                // one-set compound duplicate, which is usually better consolidated into a useful 2+ set dose.
                if (source.maxMinutes > 45 && !crowded && !extremeLongFragmentation && !hasConsolidatableCompound)
                    continue;
                if (oneSetCount <= 2 && !crowded && !hasConsolidatableCompound)
                    continue;
                for (let index = source.exercises.length - 1; index >= 0; index--) {
                    const fragment = source.exercises[index];
                    if (fragment.sets !== 1 || fragment.role === 'primary_strength' || fragment.role === 'secondary_strength')
                        continue;
                    const fragmentDef = exerciseMap.get(fragment.exerciseId);
                    const fragmentMuscle = fragmentDef ? primaryMuscle(fragmentDef) : undefined;
                    if (source.maxMinutes > 45 && !crowded && !extremeLongFragmentation && !fragmentDef?.flags.compound)
                        continue;
                    const sourceProposal = source.exercises.filter((_, i) => i !== index);
                    if (sourceProposal.length < 2 || !finalStructureValid(source, sourceProposal))
                        continue;
                    const destinations = realized
                        .filter(session => session.id !== source.id && (!fragmentMuscle || intentAcceptsMuscle(session.intent, fragmentMuscle)))
                        .map(session => {
                        const matchIndex = session.exercises.findIndex(ex => ex.exerciseId === fragment.exerciseId && ex.role === fragment.role && ex.sets < 4);
                        if (matchIndex < 0)
                            return null;
                        const proposal = session.exercises.map((ex, i) => i === matchIndex ? { ...ex, sets: ex.sets + 1 } : ex);
                        const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        return optimized.estimatedMinutes <= session.maxMinutes ? { session, matchIndex, optimized } : null;
                    })
                        .filter(Boolean);
                    destinations.sort((a, b) => a.optimized.estimatedMinutes - b.optimized.estimatedMinutes || a.session.id.localeCompare(b.session.id));
                    const chosen = destinations[0];
                    if (!chosen)
                        continue;
                    source.exercises = assignAccessorySupersets({ ...source, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                    source.estimatedMinutes = estimateMinutes(source.exercises);
                    chosen.session.exercises = chosen.optimized.exercises;
                    chosen.session.estimatedMinutes = chosen.optimized.estimatedMinutes;
                    moved = true;
                    break;
                }
                if (moved)
                    break;
            }
            if (!moved)
                break;
        }
    }
/* M230:PRESERVE:helper.consolidateOneSetDuplicates:END */
)(...args);
}

export function rebalanceOneSetFragmentsBySwap(context, ...args) {
const { exerciseMap, finalStructureValid, realized, request } = context;
return (
/* M230:PRESERVE:helper.rebalanceOneSetFragmentsBySwap:BEGIN */
() => {
        const movable = (ex) => !['primary_strength', 'secondary_strength', 'strength_support', 'specialization'].includes(ex.role);
        for (let pass = 0; pass < 24; pass++) {
            const source = [...realized]
                .filter(session => session.maxMinutes <= 45 && session.exercises.filter(ex => ex.sets === 1).length > 2)
                .sort((a, b) => b.exercises.filter(ex => ex.sets === 1).length - a.exercises.filter(ex => ex.sets === 1).length || a.id.localeCompare(b.id))[0];
            if (!source)
                break;
            let swapped = false;
            const fragments = source.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                .filter((x) => !!x.def && x.exercise.sets === 1 && movable(x.exercise))
                .filter(x => { const muscle = primaryMuscle(x.def); const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal'; return priority === 'normal' || priority === 'maintenance'; });
            for (const fragment of fragments) {
                const muscle = primaryMuscle(fragment.def);
                if (!muscle)
                    continue;
                const destinations = [...realized]
                    .filter(session => session.id !== source.id && session.exercises.filter(ex => ex.sets === 1).length <= 1)
                    .sort((a, b) => a.estimatedMinutes - b.estimatedMinutes || a.id.localeCompare(b.id));
                for (const destination of destinations) {
                    if (!equipmentEligible(fragment.def, destination, request))
                        continue;
                    const receivers = destination.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def && x.exercise.sets >= 2 && movable(x.exercise))
                        .filter(x => primaryMuscle(x.def) === muscle && equipmentEligible(x.def, source, request))
                        .sort((a, b) => b.exercise.sets - a.exercise.sets || a.def.setupCost - b.def.setupCost || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                    for (const receiver of receivers) {
                        if (source.exercises.some((ex, i) => i !== fragment.index && ex.exerciseId === receiver.exercise.exerciseId))
                            continue;
                        if (destination.exercises.some((ex, i) => i !== receiver.index && ex.exerciseId === fragment.exercise.exerciseId))
                            continue;
                        const sourceProposal = source.exercises.map((ex, i) => i === fragment.index ? { ...receiver.exercise } : ex);
                        const destinationProposal = destination.exercises.map((ex, i) => i === receiver.index ? { ...fragment.exercise } : ex);
                        if (!finalStructureValid(source, sourceProposal) || !finalStructureValid(destination, destinationProposal))
                            continue;
                        const sourceOptimized = assignAccessorySupersets({ ...source, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        const destinationOptimized = assignAccessorySupersets({ ...destination, exercises: destinationProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (sourceOptimized.estimatedMinutes > source.maxMinutes || destinationOptimized.estimatedMinutes > destination.maxMinutes)
                            continue;
                        if (sourceOptimized.exercises.filter(ex => ex.sets === 1).length >= source.exercises.filter(ex => ex.sets === 1).length)
                            continue;
                        if (destinationOptimized.exercises.filter(ex => ex.sets === 1).length > 2)
                            continue;
                        source.exercises = sourceOptimized.exercises;
                        source.estimatedMinutes = sourceOptimized.estimatedMinutes;
                        destination.exercises = destinationOptimized.exercises;
                        destination.estimatedMinutes = destinationOptimized.estimatedMinutes;
                        swapped = true;
                        break;
                    }
                    if (swapped)
                        break;
                }
                if (swapped)
                    break;
            }
            if (!swapped)
                break;
        }
    }
/* M230:PRESERVE:helper.rebalanceOneSetFragmentsBySwap:END */
)(...args);
}

export function pruneCompactOneSetFragments(context, ...args) {
const { capacityPrescriptions, currentDirect, currentFractional, directTotalsWithSessionProposal, exerciseMap, finalStructureValid, prescriptionMap, realized, request, totalsWithSessionProposal } = context;
return (
/* M230:PRESERVE:helper.pruneCompactOneSetFragments:BEGIN */
() => {
        const priorityRank = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
        const contributionSignature = (def) => Object.entries(def.muscles)
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([muscle, c]) => `${muscle}:${c?.credit ?? 0}`)
            .join('|');
        for (const source of realized) {
            for (let guard = 0; guard < 8; guard++) {
                const oneSetCount = source.exercises.filter(ex => ex.sets === 1).length;
                const fragmented = oneSetCount > 2 && (source.maxMinutes <= 45 || oneSetCount * 2 >= source.exercises.length);
                if (!fragmented)
                    break;
                // Exact-ledger consolidation comes before deletion. If a one-set assistance movement has the
                // same muscle-credit vector as an existing non-strength movement, fold that set into the
                // established prescription. This removes setup fragmentation while preserving weekly dose
                // exactly (e.g. 1 set Leg Press -> an existing Hack Squat set when both credit quads/glutes
                // identically). Cap the receiver at five sets so cleanup never creates a local mega-set block.
                let exactMerge = false;
                for (let fragmentIndex = source.exercises.length - 1; fragmentIndex >= 0 && !exactMerge; fragmentIndex--) {
                    const fragment = source.exercises[fragmentIndex];
                    if (fragment.sets !== 1 || fragment.role === 'primary_strength' || fragment.role === 'secondary_strength' || fragment.role === 'strength_support' || fragment.role === 'specialization')
                        continue;
                    const fragmentDef = exerciseMap.get(fragment.exerciseId);
                    if (!fragmentDef)
                        continue;
                    const fragmentMuscle = primaryMuscle(fragmentDef);
                    const fragmentPriority = fragmentMuscle ? (request.goal.musclePriorities[fragmentMuscle] ?? 'normal') : 'normal';
                    if (fragmentPriority === 'high' || fragmentPriority === 'specialization' || fragmentPriority === 'primary')
                        continue;
                    const signature = contributionSignature(fragmentDef);
                    for (let receiverIndex = 0; receiverIndex < source.exercises.length; receiverIndex++) {
                        if (receiverIndex === fragmentIndex)
                            continue;
                        const receiver = source.exercises[receiverIndex];
                        if (receiver.role === 'primary_strength' || receiver.role === 'secondary_strength' || receiver.role === 'strength_support' || receiver.role === 'specialization' || receiver.sets >= 5)
                            continue;
                        const receiverDef = exerciseMap.get(receiver.exerciseId);
                        if (!receiverDef || receiverDef.flags.compound !== fragmentDef.flags.compound || contributionSignature(receiverDef) !== signature)
                            continue;
                        const proposal = source.exercises
                            .filter((_, i) => i !== fragmentIndex)
                            .map(ex => ({ ...ex }));
                        const adjustedReceiverIndex = receiverIndex - (fragmentIndex < receiverIndex ? 1 : 0);
                        proposal[adjustedReceiverIndex] = { ...proposal[adjustedReceiverIndex], sets: proposal[adjustedReceiverIndex].sets + 1 };
                        const optimized = assignAccessorySupersets({ ...source, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimized.estimatedMinutes > source.maxMinutes)
                            continue;
                        source.exercises = optimized.exercises;
                        source.estimatedMinutes = optimized.estimatedMinutes;
                        exactMerge = true;
                        break;
                    }
                }
                if (exactMerge)
                    continue;
                const baseline = currentFractional(), baselineDirect = currentDirect();
                const candidates = source.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def && x.exercise.sets === 1)
                    .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && x.exercise.role !== 'strength_support')
                    .map(x => {
                    const muscle = primaryMuscle(x.def);
                    const priority = muscle ? (request.goal.musclePriorities[muscle] ?? 'normal') : 'normal';
                    if (priority === 'high' || priority === 'specialization' || priority === 'primary')
                        return null;
                    const proposal = source.exercises.filter((_, i) => i !== x.index);
                    if (proposal.length < 2)
                        return null;
                    // Some constrained split weeks intentionally distribute an Upper/Lower identity across
                    // sister sessions (for example one Upper is bench-dominant and the other pull-dominant).
                    // Fragment cleanup may not worsen that existing pattern coverage, but it should not be
                    // blocked merely because the source session was already intentionally asymmetric.
                    if (!finalStructureValid(source, proposal)) {
                        const coverage = (items) => {
                            const defs = items.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
                            return {
                                push: defs.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily)),
                                pull: defs.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily)),
                                lower: defs.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
                            };
                        };
                        const before = coverage(source.exercises), after = coverage(proposal);
                        if ((before.push && !after.push) || (before.pull && !after.pull) || (before.lower && !after.lower))
                            return null;
                    }
                    const totals = totalsWithSessionProposal(source.id, proposal), direct = directTotalsWithSessionProposal(source.id, proposal);
                    const safe = capacityPrescriptions.filter(p => p.muscle !== 'front_delts').every(p => {
                        const requiredFractional = Math.min(baseline[p.muscle], p.minimum * .85);
                        const requiredDirect = Math.min(baselineDirect[p.muscle], p.directMinimum);
                        return totals[p.muscle] + .001 >= requiredFractional && direct[p.muscle] + .001 >= requiredDirect;
                    });
                    if (!safe)
                        return null;
                    const prescription = muscle ? prescriptionMap.get(muscle) : undefined;
                    const surplus = muscle ? Math.max(0, baseline[muscle] - (prescription?.minimum ?? 0)) : 0;
                    return { ...x, muscle, priority, proposal, surplus };
                })
                    .filter(Boolean);
                candidates.sort((a, b) => priorityRank[a.priority] - priorityRank[b.priority]
                    || (a.def.flags.compound ? 1 : 0) - (b.def.flags.compound ? 1 : 0)
                    || b.surplus - a.surplus
                    || b.def.setupCost - a.def.setupCost
                    || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                const chosen = candidates[0];
                if (!chosen)
                    break;
                const optimized = assignAccessorySupersets({ ...source, exercises: chosen.proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                source.exercises = optimized.exercises;
                source.estimatedMinutes = optimized.estimatedMinutes;
            }
        }
    }
/* M230:PRESERVE:helper.pruneCompactOneSetFragments:END */
)(...args);
}

export function consolidateRedundantAccessoryFamilies(context, ...args) {
const { exerciseMap, realized, request } = context;
return (
/* M230:PRESERVE:helper.consolidateRedundantAccessoryFamilies:BEGIN */
() => {
        for (const session of realized) {
            const groups = new Map();
            for (const [index, exercise] of session.exercises.entries()) {
                if (exercise.role === 'primary_strength' || exercise.role === 'secondary_strength' || exercise.role === 'strength_support')
                    continue;
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def || def.flags.compound)
                    continue;
                const muscle = primaryMuscle(def);
                const cluster = exerciseEconomyCluster(def);
                if (!cluster)
                    continue;
                const key = `${cluster}|${muscle ?? 'generic'}`;
                const list = groups.get(key) ?? [];
                list.push({ exercise, index, def, muscle });
                groups.set(key, list);
            }
            for (const entries of groups.values()) {
                if (entries.length < 2)
                    continue;
                const muscles = new Set(entries.map(x => x.muscle).filter(Boolean));
                if (muscles.size !== 1)
                    continue;
                const muscle = [...muscles][0];
                const priority = request.goal.musclePriorities[muscle] ?? 'normal';
                if (priority === 'high' || priority === 'specialization' || priority === 'primary')
                    continue;
                const combined = entries.reduce((sum, x) => sum + x.exercise.sets, 0);
                if (combined < 2)
                    continue;
                const ranked = [...entries].sort((a, b) => {
                    const score = (x) => x.def.suitability.hypertrophy * .5 + x.def.stability * .15 - x.def.setupCost * .12 - x.def.fatigue.systemic * .08;
                    return score(b) - score(a) || a.index - b.index;
                });
                const keeper = ranked[0];
                if (combined <= 5) {
                    const remove = new Set(entries.filter(x => x !== keeper).map(x => x.index));
                    session.exercises = session.exercises.filter((_, index) => !remove.has(index));
                    // Filtering changes indexes before the keeper when a duplicate happened to precede it, so
                    // resolve by stable exercise id after removal.
                    const kept = session.exercises.findIndex(ex => ex.exerciseId === keeper.exercise.exerciseId);
                    if (kept >= 0)
                        session.exercises[kept] = { ...session.exercises[kept], sets: combined };
                    session.estimatedMinutes = estimateMinutes(session.exercises);
                    continue;
                }
                // If one useful movement cannot hold the full normal-priority dose, move one whole duplicate
                // to another compatible weekly exposure instead of leaving two interchangeable variants in
                // the same session. This preserves weekly sets and avoids creating a one-set fragment.
                const mover = ranked.slice(1).sort((a, b) => b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId))[0];
                if (!mover)
                    continue;
                const destinations = realized
                    .filter(target => target.id !== session.id && intentAcceptsMuscle(target.intent, muscle))
                    .filter(target => !target.exercises.some(ex => exerciseEconomyCluster(exerciseMap.get(ex.exerciseId)) === exerciseEconomyCluster(mover.def)))
                    .map(target => {
                    if (target.targetExercises !== undefined && target.exercises.length >= target.targetExercises + 2)
                        return null;
                    const optimized = assignAccessorySupersets({ ...target, exercises: [...target.exercises, { ...mover.exercise }], estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    return optimized.estimatedMinutes <= target.maxMinutes ? { target, optimized } : null;
                })
                    .filter(Boolean);
                destinations.sort((a, b) => a.optimized.estimatedMinutes - b.optimized.estimatedMinutes || a.target.id.localeCompare(b.target.id));
                const destination = destinations[0];
                if (!destination)
                    continue;
                session.exercises = session.exercises.filter((_, index) => index !== mover.index);
                session.estimatedMinutes = estimateMinutes(session.exercises);
                destination.target.exercises = destination.optimized.exercises;
                destination.target.estimatedMinutes = destination.optimized.estimatedMinutes;
            }
        }
    }
/* M230:PRESERVE:helper.consolidateRedundantAccessoryFamilies:END */
)(...args);
}

export function consolidateCompoundFragments(context) {
const { capacityPrescriptions, currentFractional, exerciseMap, finalStructureValid, prescriptionMap, realized, request, totalsWithSessionProposal } = context;
/* M230:PRESERVE:stage.consolidateCompoundFragments:BEGIN */
for (const session of realized) {
        for (let index = session.exercises.length - 1; index >= 0; index--) {
            const exercise = session.exercises[index];
            if (exercise.sets !== 1 || exercise.role !== 'hypertrophy_compound')
                continue;
            const def = exerciseMap.get(exercise.exerciseId);
            const muscle = def ? primaryMuscle(def) : undefined;
            if (!def || !muscle)
                continue;
            const prescription = prescriptionMap.get(muscle);
            const totals = currentFractional();
            if (prescription && totals[muscle] + .001 < prescription.preferred) {
                const proposal = session.exercises.map((ex, i) => i === index ? { ...ex, sets: 2 } : ex);
                const proposedTotals = totalsWithSessionProposal(session.id, proposal);
                if (proposedTotals[muscle] <= prescription.upper + 0.001) {
                    const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (optimized.estimatedMinutes <= session.maxMinutes) {
                        session.exercises = optimized.exercises;
                        session.estimatedMinutes = optimized.estimatedMinutes;
                        continue;
                    }
                }
            }
            const proposal = session.exercises.filter((_, i) => i !== index);
            if (proposal.length < 2 || !finalStructureValid(session, proposal))
                continue;
            const proposedTotals = totalsWithSessionProposal(session.id, proposal);
            const safe = capacityPrescriptions.filter(p => p.muscle !== 'front_delts' && p.minimum > 0).every(p => proposedTotals[p.muscle] + .001 >= p.minimum * .85);
            if (!safe)
                continue;
            const optimized = assignAccessorySupersets({ ...session, exercises: proposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
            session.exercises = optimized.exercises;
            session.estimatedMinutes = optimized.estimatedMinutes;
        }
    }
/* M230:PRESERVE:stage.consolidateCompoundFragments:END */

}

export function pruneFinalCompactFragments(context) {
const { phase, pruneCompactOneSetFragments, request } = context;
/* M230:PRESERVE:stage.pruneFinalCompactFragments:BEGIN */
if (!publicMevContractApplies(request, phase))
        pruneCompactOneSetFragments();
/* M230:PRESERVE:stage.pruneFinalCompactFragments:END */

}
