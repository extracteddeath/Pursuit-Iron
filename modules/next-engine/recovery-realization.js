import { createExerciseMap } from './exercise-db.js';
import { publicRegionContribution } from './public-mev.js';
import { finalizePlannedSession } from './realizer.js';
import { equipmentEligible, maxBarbells, primaryMuscle } from './realizer-ranking.js';
import { INTENT_MUSCLES } from './topology.js';
import { assessWeeklyRecovery, optimizeWeeklyRecovery, weeklyRecoveryCollisions } from './weekly-recovery.js';
const STRENGTH_ROLES = new Set(['primary_strength', 'secondary_strength', 'strength_support']);
const UPPER_ACCESSORY_MUSCLES = new Set(['side_delts', 'rear_delts', 'biceps', 'triceps', 'forearms', 'traps', 'neck']);
const LOWER_ACCESSORY_MUSCLES = new Set(['calves', 'core', 'adductors', 'abductors']);
function sessionAccepts(def, session) {
    const primary = primaryMuscle(def);
    if (!primary)
        return false;
    const intent = INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full;
    if (intent.includes(primary))
        return true;
    if (def.flags.compound)
        return false;
    // Small isolation work is the engine's recovery pressure-release valve. Bodybuilding practice
    // commonly places delts/arms/calves/abs on an otherwise different day when that improves the week.
    // The target-shape guard still limits spillover so a Push/Pull/Lower label cannot become meaningless.
    if (UPPER_ACCESSORY_MUSCLES.has(primary))
        return true;
    if (LOWER_ACCESSORY_MUSCLES.has(primary))
        return true;
    return false;
}
function regionShape(session, defs) {
    const chosen = session.exercises.map(ex => defs.get(ex.exerciseId)).filter((x) => !!x);
    return {
        push: chosen.some(def => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily)),
        pull: chosen.some(def => ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily)),
        lower: chosen.some(def => ['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
    };
}
function sourceCanLose(source, index, movedSets, defs) {
    const exercise = source.exercises[index];
    if (!exercise || STRENGTH_ROLES.has(exercise.role))
        return false;
    if (movedSets <= 0 || movedSets > exercise.sets)
        return false;
    const whole = movedSets === exercise.sets;
    const remaining = whole ? source.exercises.filter((_, i) => i !== index) : source.exercises.map((ex, i) => i === index ? { ...ex, sets: ex.sets - movedSets } : ex);
    if (remaining.length < 2)
        return false;
    if (!whole && remaining[index]?.sets < 2)
        return false;
    if (source.intent === 'full' || source.intent === 'strength_full') {
        const shape = regionShape({ ...source, exercises: remaining }, defs);
        if (!shape.push || !shape.pull || !shape.lower)
            return false;
    }
    return true;
}
function mergeOrAdd(target, exercise, movedSets, def, defs) {
    const cloned = target.exercises.map(ex => ({ ...ex }));
    let exact = cloned.findIndex(ex => ex.exerciseId === exercise.exerciseId && ex.role === exercise.role);
    // Interchangeable isolation variants are equivalent dose carriers for recovery redistribution.
    // Prefer merging into an already-present movement family rather than creating two lateral raises,
    // curls, calf raises, etc. in the same workout. Compounds remain exercise-specific.
    if (exact < 0 && !def.flags.compound) {
        const primary = primaryMuscle(def);
        exact = cloned.findIndex(ex => {
            const existing = defs.get(ex.exerciseId);
            return !!existing && !existing.flags.compound && existing.movementFamily === def.movementFamily && primaryMuscle(existing) === primary && ex.role === exercise.role;
        });
    }
    if (exact >= 0) {
        const existingDef = defs.get(cloned[exact].exerciseId) ?? def;
        const cap = existingDef.flags.compound ? 5 : 6;
        if (cloned[exact].sets + movedSets > cap)
            return null;
        cloned[exact] = { ...cloned[exact], sets: cloned[exact].sets + movedSets };
        return cloned;
    }
    // Do not manufacture a one-set fragment in another workout. A whole existing one-set movement can
    // move, but splitting work creates a new exercise only when at least two sets travel together.
    if (movedSets < 2)
        return null;
    const next = { ...exercise, sets: movedSets };
    delete next.supersetGroup;
    if (movedSets !== exercise.sets)
        delete next.advancedTechnique;
    return [...cloned, next];
}
function validTargetShape(target, proposal, defs, request) {
    if (target.targetExercises !== undefined && proposal.length > target.targetExercises + 3)
        return false;
    const barbell = proposal.filter(ex => defs.get(ex.exerciseId)?.flags.barbell).length;
    if (barbell > maxBarbells(target, request))
        return false;
    // Keep Lower/Leg identity: at most two small upper accessories, and only after two lower movements.
    if (target.intent === 'lower' || target.intent === 'legs') {
        let lower = 0, upperAccessories = 0;
        for (const ex of proposal) {
            const def = defs.get(ex.exerciseId);
            if (!def)
                continue;
            if (['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion', 'calf', 'hip_abduction', 'hip_adduction', 'dorsiflexion'].includes(def.movementFamily))
                lower++;
            const primary = primaryMuscle(def);
            if (primary && UPPER_ACCESSORY_MUSCLES.has(primary) && !def.flags.compound)
                upperAccessories++;
        }
        if (upperAccessories > 2 || (upperAccessories > 0 && lower < 2))
            return false;
    }
    const intent = INTENT_MUSCLES[target.intent] ?? INTENT_MUSCLES.full;
    if (target.intent !== 'full' && target.intent !== 'strength_full') {
        const offIntent = proposal.filter(ex => {
            const def = defs.get(ex.exerciseId);
            const primary = def ? primaryMuscle(def) : undefined;
            return !!def && !def.flags.compound && !!primary && !intent.includes(primary);
        }).length;
        // Existing programs can already carry several deliberate spillover accessories. A recovery move
        // may keep that count or add at most one new off-intent slot, but never turn a session into filler.
        const existingOffIntent = target.exercises.filter(ex => {
            const def = defs.get(ex.exerciseId);
            const primary = def ? primaryMuscle(def) : undefined;
            return !!def && !def.flags.compound && !!primary && !intent.includes(primary);
        }).length;
        if (offIntent > Math.max(existingOffIntent + 1, 3))
            return false;
    }
    return true;
}
function collisionRelevance(def, sourceIndex, collisions) {
    let score = 0;
    for (const collision of collisions) {
        if (collision.aIndex !== sourceIndex && collision.bIndex !== sourceIndex)
            continue;
        for (const reason of collision.reasons) {
            if (reason.type === 'region')
                score += publicRegionContribution(def, reason.key) * 6;
            else if (reason.type === 'systemic')
                score += Math.max(0, def.fatigue.systemic - 1) * 1.5;
            else if (reason.type === 'joint')
                score += Math.max(0, def.fatigue[reason.key] ?? 0);
        }
    }
    return score;
}
/**
 * Produces conservative, dose-neutral whole-exercise / two-set transfers that can be evaluated by the
 * normal generator audit. Strength anchors are immovable. This function does not decide that a move is
 * better; it only enumerates plausible coach-like repairs for the weekly recovery optimizer to test.
 */
export function proposeRecoveryRedistributions(sessions, request) {
    const defs = createExerciseMap(request.customExercises);
    const collisions = weeklyRecoveryCollisions(sessions, request);
    const baseline = assessWeeklyRecovery(sessions, request);
    if (!collisions.length && baseline.score <= .0001)
        return [];
    const softPolish = collisions.length === 0;
    const proposals = [];
    const seen = new Set();
    for (let sourceIndex = 0; sourceIndex < sessions.length; sourceIndex++) {
        const source = sessions[sourceIndex];
        for (let exerciseIndex = 0; exerciseIndex < source.exercises.length; exerciseIndex++) {
            const exercise = source.exercises[exerciseIndex], def = defs.get(exercise.exerciseId);
            if (!def || STRENGTH_ROLES.has(exercise.role))
                continue;
            const relevance = softPolish
                ? Math.max(.1, def.fatigue.systemic * 1.5 + def.fatigue.shoulder + def.fatigue.elbow + def.fatigue.knee + def.fatigue.lowerBack + def.fatigue.axial * .5)
                : collisionRelevance(def, sourceIndex, collisions);
            if (relevance <= 0 || (softPolish && def.flags.compound))
                continue;
            const primary = primaryMuscle(def);
            if (!primary)
                continue;
            const splitAllowed = exercise.sets >= 4 && !exercise.advancedTechnique;
            const mergeSetAllowed = exercise.sets >= 3 && !exercise.advancedTechnique;
            const moveOptions = (softPolish ? [exercise.sets] : [exercise.sets, ...(splitAllowed ? [2] : []), ...(mergeSetAllowed ? [1] : [])]).filter((v, i, a) => a.indexOf(v) === i);
            for (const movedSets of moveOptions) {
                if (!sourceCanLose(source, exerciseIndex, movedSets, defs))
                    continue;
                for (let targetIndex = 0; targetIndex < sessions.length; targetIndex++) {
                    if (targetIndex === sourceIndex)
                        continue;
                    const target = sessions[targetIndex];
                    if (!sessionAccepts(def, target) || !request.schedule.days.some(day => day.day === target.day) || !equipmentEligible(def, target, request))
                        continue;
                    const targetExercises = mergeOrAdd(target, exercise, movedSets, def, defs);
                    if (!targetExercises)
                        continue;
                    if (!validTargetShape(target, targetExercises, defs, request))
                        continue;
                    const sourceExercises = movedSets === exercise.sets
                        ? source.exercises.filter((_, i) => i !== exerciseIndex).map(ex => ({ ...ex }))
                        : source.exercises.map((ex, i) => i === exerciseIndex ? { ...ex, sets: ex.sets - movedSets } : ({ ...ex }));
                    const next = sessions.map(s => ({ ...s, exercises: s.exercises.map(ex => ({ ...ex })) }));
                    next[sourceIndex] = finalizePlannedSession({ ...source, exercises: sourceExercises, estimatedMinutes: 0 }, request);
                    next[targetIndex] = finalizePlannedSession({ ...target, exercises: targetExercises, estimatedMinutes: 0 }, request);
                    if (next[sourceIndex].estimatedMinutes > next[sourceIndex].maxMinutes || next[targetIndex].estimatedMinutes > next[targetIndex].maxMinutes)
                        continue;
                    const key = `${source.id}|${target.id}|${exercise.exerciseId}|${movedSets}`;
                    if (seen.has(key))
                        continue;
                    seen.add(key);
                    if (softPolish) {
                        const quick = assessWeeklyRecovery(next, request);
                        const improves = quick.hardCollisions < baseline.hardCollisions || (quick.hardCollisions === baseline.hardCollisions && quick.score < baseline.score - .0001);
                        if (!improves)
                            continue;
                        proposals.push({ sessions: next, sourceSessionId: source.id, targetSessionId: target.id, exerciseId: exercise.exerciseId, movedSets, wholeExercise: true, relevance, quickHard: quick.hardCollisions, quickScore: quick.score });
                    }
                    else
                        proposals.push({ sessions: next, sourceSessionId: source.id, targetSessionId: target.id, exerciseId: exercise.exerciseId, movedSets, wholeExercise: movedSets === exercise.sets, relevance });
                }
            }
        }
    }
    // Recovery scoring is cheap compared with a full program audit. Rank every structurally plausible
    // move by its best weekday assignment first, then return only a small Pareto-relevant shortlist to
    // the generator. This keeps runtime bounded while still allowing a lower-ranked compound move when
    // accessories cannot cross the hard-collision threshold.
    const shortlist = softPolish
        ? [...proposals].sort((a, b) => (a.quickHard ?? 99) - (b.quickHard ?? 99) || ((a.quickScore ?? Infinity) - (b.quickScore ?? Infinity)) || b.relevance - a.relevance).slice(0, 24)
        : proposals;
    const ranked = shortlist.map(proposal => {
        const recovery = optimizeWeeklyRecovery(proposal.sessions, request);
        return { ...proposal, fastHard: recovery.hardCollisions, fastScore: recovery.score };
    }).filter(p => p.fastHard < baseline.hardCollisions || (p.fastHard === baseline.hardCollisions && p.fastScore < baseline.score - .0001));
    const rankPriority = (p) => p === 'maintenance' ? 0 : p === 'normal' ? 1 : p === 'high' ? 2 : p === 'specialization' ? 3 : 4;
    ranked.sort((a, b) => {
        if (a.fastHard !== b.fastHard)
            return a.fastHard - b.fastHard;
        if (Math.abs(a.fastScore - b.fastScore) > .0001)
            return a.fastScore - b.fastScore;
        const ax = sessions.find(s => s.id === a.sourceSessionId)?.exercises.find(e => e.exerciseId === a.exerciseId);
        const bx = sessions.find(s => s.id === b.sourceSessionId)?.exercises.find(e => e.exerciseId === b.exerciseId);
        const ad = ax ? defs.get(ax.exerciseId) : undefined, bd = bx ? defs.get(bx.exerciseId) : undefined;
        const ap = ad ? request.goal.musclePriorities[primaryMuscle(ad)] ?? 'normal' : 'normal';
        const bp = bd ? request.goal.musclePriorities[primaryMuscle(bd)] ?? 'normal' : 'normal';
        return b.relevance - a.relevance || rankPriority(ap) - rankPriority(bp) || (ad?.flags.compound ? 1 : 0) - (bd?.flags.compound ? 1 : 0) || (a.wholeExercise ? 0 : 1) - (b.wholeExercise ? 0 : 1) || a.exerciseId.localeCompare(b.exerciseId) || a.targetSessionId.localeCompare(b.targetSessionId);
    });
    return ranked.slice(0, 12).map(({ fastHard: _, fastScore: __, relevance: ___, quickHard: ____, quickScore: _____, ...proposal }) => proposal);
}
