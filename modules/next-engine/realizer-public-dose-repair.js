// M230 canonical public-dose-repair stage. Stage context is local to one realization.
import { avoidableExerciseOverlap } from './exercise-economy.js';
import { PUBLIC_MEV_REGIONS, PUBLIC_REGION_MUSCLE, directlyTargetsPublicRegion, publicMevBaseTarget, publicMevContractApplies, publicMevLedger, publicMevRequired, publicRegionContribution } from './public-mev.js';
import { assignAccessorySupersets } from './realizer-pairing.js';
import { makePlanned } from './realizer-prescriptions.js';
import { equipmentEligible, intentAcceptsMuscle, maxBarbells, muscleScore } from './realizer-ranking.js';
import { estimateMinutes } from './realizer-time-budget.js';

export function reconcilePublicDose(context) {
const { capacityPrescriptions, consolidateRedundantAccessoryFamilies, exerciseCatalog, exerciseMap, phase, policy, realized, request, trackExerciseUse, weeklyMovementUse } = context;
/* M230:PRESERVE:stage.reconcilePublicDose:BEGIN */
if (publicMevContractApplies(request, phase)) {
        const flexRegions = new Set(['side_delts', 'rear_delts', 'biceps', 'triceps', 'calves', 'core']);
        const roleForRegion = (region) => ['side_delts', 'rear_delts', 'biceps', 'triceps', 'calves', 'core'].includes(region) ? 'hypertrophy_isolation' : 'hypertrophy_compound';
        const sessionAcceptsRegion = (session, region) => {
            const muscle = PUBLIC_REGION_MUSCLE[region];
            if (intentAcceptsMuscle(session.intent, muscle))
                return true;
            // Low-interference accessories may move across the nominal split boundary when that is the only
            // way to distribute a real weekly floor cleanly. This is preferable to 8–10 local sets on one
            // day and matches the engine's existing accessory-spillover philosophy.
            if (flexRegions.has(region))
                return !['chest', 'back_day', 'bench_focus', 'press_focus'].includes(session.intent) || ['side_delts', 'rear_delts', 'biceps', 'triceps'].includes(region);
            return false;
        };
        const sessionRegionPenalty = (session, region) => {
            // Cross-split accessory placement is a fallback, not a first choice. In particular, do not
            // spend the generous 60–90 minute capacity by turning Lower/Legs into an arms/delts day while
            // compatible upper sessions still have room.
            if (['side_delts', 'rear_delts', 'biceps', 'triceps'].includes(region) && ['lower', 'legs', 'limbs'].includes(session.intent))
                return 3;
            if (region === 'calves' && ['upper', 'push', 'pull', 'torso'].includes(session.intent))
                return 2;
            return 0;
        };
        const publicCandidate = (region, session) => {
            const muscle = PUBLIC_REGION_MUSCLE[region];
            const chosen = session.exercises.map(ex => exerciseMap.get(ex.exerciseId)).filter((d) => !!d);
            const chosenWithRoles = session.exercises.map(ex => ({ def: exerciseMap.get(ex.exerciseId), role: ex.role })).filter((x) => !!x.def);
            const families = new Map();
            for (const def of chosen)
                families.set(def.movementFamily, (families.get(def.movementFamily) ?? 0) + 1);
            const exactUses = (id) => realized.reduce((n, s) => n + s.exercises.filter(ex => ex.exerciseId === id).length, 0);
            const barbells = chosen.filter(def => def.flags.barbell).length;
            const role = roleForRegion(region);
            return exerciseCatalog
                .filter(def => equipmentEligible(def, session, request))
                .filter(def => !request.preferences.avoidedExercises?.includes(def.id))
                .filter(def => directlyTargetsPublicRegion(def, region))
                .filter(def => !chosen.some(x => x.id === def.id))
                .filter(def => !avoidableExerciseOverlap(def, role, chosenWithRoles, { priority: request.goal.musclePriorities[muscle] ?? 'normal' }))
                .filter(def => (families.get(def.movementFamily) ?? 0) < ((region === 'lats' || region === 'upper_back') ? 2 : 1))
                .filter(def => !def.flags.barbell || barbells < maxBarbells(session, request))
                .map(def => {
                const exact = exactUses(def.id), familyUses = realized.reduce((n, s) => n + s.exercises.filter(ex => exerciseMap.get(ex.exerciseId)?.movementFamily === def.movementFamily).length, 0);
                const fatigue = def.fatigue.systemic + def.fatigue.axial * .7 + def.fatigue.lowerBack * .8;
                const base = muscleScore(def, muscle, role, session, chosen, weeklyMovementUse, request);
                const direct = publicRegionContribution(def, region);
                return { def, score: base + direct * 4 - exact * 3.5 - familyUses * .12 - fatigue * .04 };
            })
                .sort((a, b) => b.score - a.score || a.def.id.localeCompare(b.def.id))[0]?.def;
        };
        const prescriptionByMuscle = new Map(capacityPrescriptions.map(p => [p.muscle, p]));
        const priorityRankPublic = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
        const orderedRegions = [...PUBLIC_MEV_REGIONS]
            .filter(region => publicMevRequired(request, region))
            .sort((a, b) => priorityRankPublic[request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[b]] ?? 'normal'] - priorityRankPublic[request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[a]] ?? 'normal']
            || publicMevBaseTarget(request, b) - publicMevBaseTarget(request, a) || a.localeCompare(b));
        for (const region of orderedRegions) {
            const muscle = PUBLIC_REGION_MUSCLE[region];
            const internal = prescriptionByMuscle.get(muscle);
            if (!internal)
                continue;
            const target = publicMevBaseTarget(request, region);
            for (let guard = 0; guard < 24; guard++) {
                const publicLedger = publicMevLedger(realized, exerciseMap);
                if (publicLedger[region] + .001 >= target)
                    break;
                const sessionsByRoom = [...realized]
                    .filter(session => sessionAcceptsRegion(session, region))
                    .sort((a, b) => sessionRegionPenalty(a, region) - sessionRegionPenalty(b, region) || (b.maxMinutes - b.estimatedMinutes) - (a.maxMinutes - a.estimatedMinutes) || a.id.localeCompare(b.id));
                let repaired = false;
                // First deepen a direct movement already earning this public region's floor. This is usually
                // the cleanest fix because it adds no setup or duplicate movement family.
                for (const session of sessionsByRoom) {
                    const existing = session.exercises
                        .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def)
                        .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && directlyTargetsPublicRegion(x.def, region))
                        .filter(x => x.exercise.sets < 5)
                        .sort((a, b) => a.exercise.sets - b.exercise.sets || a.def.setupCost - b.def.setupCost || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                    for (const item of existing) {
                        const proposal = session.exercises.map((ex, i) => i === item.index ? { ...ex, sets: ex.sets + 1 } : ex);
                        // Public MEV is a stricter visible floor than the coarse internal upper model for some
                        // composite/secondary-heavy muscles. The public target itself bounds this repair, so do
                        // not let the private upper estimate veto a still-unmet user-facing floor.
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
                // Add a complementary direct movement only when deepening is exhausted. Exact weekly repeats
                // and same-session family duplicates are strongly avoided; small accessory floors may use a
                // cross-split destination to keep per-session dosage coach-like.
                for (const session of sessionsByRoom) {
                    const def = publicCandidate(region, session);
                    if (!def)
                        continue;
                    const remaining = target - publicLedger[region];
                    const sets = Math.max(1, Math.min(def.flags.compound ? 3 : 4, Math.ceil(remaining / Math.max(.001, publicRegionContribution(def, region)))));
                    const added = makePlanned(def, roleForRegion(region), sets, policy, request.athlete.experience);
                    const proposal = [...session.exercises, added];
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
        // Coach-like weekly distribution: when a muscle already has multiple direct exposures, avoid
        // concentrating nearly all movable hypertrophy work in one of them. Strength anchors are never
        // moved. This is deliberately conservative: a heavy/light 70/30-ish split is fine; the repair
        // only acts on extreme direct-set concentration that can be improved without changing weekly dose.
        const directRegionDose = (session, region) => session.exercises.reduce((sum, exercise) => {
            const def = exerciseMap.get(exercise.exerciseId);
            return sum + (def && directlyTargetsPublicRegion(def, region) ? exercise.sets * publicRegionContribution(def, region) : 0);
        }, 0);
        const concentrationLimit = request.athlete.experience === 'novice' ? .7 : request.athlete.experience === 'advanced' ? .8 : .75;
        for (const region of orderedRegions) {
            if (!publicMevRequired(request, region))
                continue;
            for (let guard = 0; guard < 16; guard++) {
                const doses = realized.map((session, index) => ({ session, index, dose: directRegionDose(session, region) }));
                const exposed = doses.filter(x => x.dose > .001);
                const total = exposed.reduce((sum, x) => sum + x.dose, 0);
                if (total < 6 || (exposed.length < 2 && !flexRegions.has(region)))
                    break;
                const source = [...exposed].sort((a, b) => b.dose - a.dose || a.index - b.index)[0];
                const publicPriority = request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[region]] ?? 'normal';
                const specialized = publicPriority === 'high' || publicPriority === 'specialization' || publicPriority === 'primary';
                const sourceFamilyCounts = new Map();
                for (const exercise of source.session.exercises) {
                    const def = exerciseMap.get(exercise.exerciseId);
                    if (!def || def.flags.compound || exercise.role === 'primary_strength' || exercise.role === 'secondary_strength' || !directlyTargetsPublicRegion(def, region))
                        continue;
                    sourceFamilyCounts.set(def.movementFamily, (sourceFamilyCounts.get(def.movementFamily) ?? 0) + 1);
                }
                const duplicateAccessoryFamily = [...sourceFamilyCounts.values()].some(count => count > 1);
                let allowed = Math.max(4, Math.floor(total * concentrationLimit));
                // Normal-priority work should fit in one useful accessory family per exposure whenever the
                // week already offers another compatible exposure. If a sixth+ direct set would force two
                // same-family isolations, move that dose across the week instead. Specialization can earn it.
                if (!specialized && (flexRegions.has(region) || duplicateAccessoryFamily))
                    allowed = Math.min(allowed, 5);
                if (source.dose <= allowed + .001)
                    break;
                const sourceMovable = source.session.exercises
                    .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                    .filter((x) => !!x.def)
                    .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && directlyTargetsPublicRegion(x.def, region))
                    .filter(x => x.exercise.sets > 1)
                    .sort((a, b) => b.exercise.sets - a.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId));
                if (!sourceMovable.length)
                    break;
                const targetCandidates = doses
                    .filter(x => x.index !== source.index && sessionAcceptsRegion(x.session, region))
                    .sort((a, b) => sessionRegionPenalty(a.session, region) - sessionRegionPenalty(b.session, region) || a.dose - b.dose || (b.session.maxMinutes - b.session.estimatedMinutes) - (a.session.maxMinutes - a.session.estimatedMinutes) || a.index - b.index);
                let moved = false;
                for (const target of targetCandidates) {
                    const existing = target.session.exercises
                        .map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
                        .filter((x) => !!x.def)
                        .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength' && directlyTargetsPublicRegion(x.def, region) && x.exercise.sets < 5)
                        .sort((a, b) => a.exercise.sets - b.exercise.sets || a.exercise.exerciseId.localeCompare(b.exercise.exerciseId))[0];
                    const sourceItem = sourceMovable[0];
                    if (existing) {
                        const sourceProposal = source.session.exercises.map((ex, i) => i === sourceItem.index ? { ...ex, sets: ex.sets - 1 } : ex);
                        const targetProposal = target.session.exercises.map((ex, i) => i === existing.index ? { ...ex, sets: ex.sets + 1 } : ex);
                        const optimizedTarget = assignAccessorySupersets({ ...target.session, exercises: targetProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                        if (optimizedTarget.estimatedMinutes > target.session.maxMinutes)
                            continue;
                        source.session.exercises = assignAccessorySupersets({ ...source.session, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                        source.session.estimatedMinutes = estimateMinutes(source.session.exercises);
                        target.session.exercises = optimizedTarget.exercises;
                        target.session.estimatedMinutes = optimizedTarget.estimatedMinutes;
                        moved = true;
                        break;
                    }
                    if (target.session.targetExercises !== undefined && target.session.exercises.length >= target.session.targetExercises + 2)
                        continue;
                    const def = publicCandidate(region, target.session);
                    if (!def)
                        continue;
                    const added = makePlanned(def, roleForRegion(region), 1, policy, request.athlete.experience);
                    const optimizedTarget = assignAccessorySupersets({ ...target.session, exercises: [...target.session.exercises, added], estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets);
                    if (optimizedTarget.estimatedMinutes > target.session.maxMinutes)
                        continue;
                    const sourceProposal = source.session.exercises.map((ex, i) => i === sourceItem.index ? { ...ex, sets: ex.sets - 1 } : ex);
                    source.session.exercises = assignAccessorySupersets({ ...source.session, exercises: sourceProposal, estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets).exercises;
                    source.session.estimatedMinutes = estimateMinutes(source.session.exercises);
                    target.session.exercises = optimizedTarget.exercises;
                    target.session.estimatedMinutes = optimizedTarget.estimatedMinutes;
                    trackExerciseUse(def);
                    moved = true;
                    break;
                }
                if (!moved)
                    break;
            }
        }
        // Public-floor and distribution repairs can add a new accessory after the earlier M52 cleanup.
        // Re-run the same consolidation rule so a repair cannot regress into mergeable 1–2 set variants.
        consolidateRedundantAccessoryFamilies();
        for (let i = 0; i < realized.length; i++)
            realized[i] = assignAccessorySupersets(realized[i], exerciseMap, request.restrictions.allowSupersets);
    }
/* M230:PRESERVE:stage.reconcilePublicDose:END */

}
