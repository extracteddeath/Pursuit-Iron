// M230 canonical session-builder stage. Stage context is local to one realization.
import { armCoverageUseKeys } from './arm-coverage.js';
import { createExerciseMap } from './exercise-db.js';
import { functionalCoverageUseKeys } from './functional-coverage.js';
import { addToLedger } from './realizer-math.js';
import { assignAccessorySupersets } from './realizer-pairing.js';
import { makePlanned } from './realizer-prescriptions.js';
import { primaryMuscle } from './realizer-ranking.js';
import { sequenceSessionExercises } from './realizer-sequence.js';
import { estimateMinutes } from './realizer-time-budget.js';

export /* M230:PRESERVE:top.finalizePlannedSession:BEGIN */
function finalizePlannedSession(session, request) {
    const exerciseMap = createExerciseMap(request.customExercises);
    return sequenceSessionExercises(assignAccessorySupersets({ ...session, exercises: session.exercises.map(ex => ({ ...ex })), estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets), exerciseMap);
}
/* M230:PRESERVE:top.finalizePlannedSession:END */

export function trackExerciseUse(context, ...args) {
const { weeklyMovementUse } = context;
return (
/* M230:PRESERVE:helper.trackExerciseUse:BEGIN */
(def) => {
        weeklyMovementUse.set(def.movementFamily, (weeklyMovementUse.get(def.movementFamily) ?? 0) + 1);
        weeklyMovementUse.set(`@${def.id}`, (weeklyMovementUse.get(`@${def.id}`) ?? 0) + 1);
        for (const key of armCoverageUseKeys(def))
            weeklyMovementUse.set(key, (weeklyMovementUse.get(key) ?? 0) + 1);
        for (const key of functionalCoverageUseKeys(def))
            weeklyMovementUse.set(key, (weeklyMovementUse.get(key) ?? 0) + 1);
    }
/* M230:PRESERVE:helper.trackExerciseUse:END */
)(...args);
}

export function add(context, ...args) {
const { ledger, policy, request, trackExerciseUse } = context;
return (
/* M230:PRESERVE:helper.add:BEGIN */
(s, allocation, def, sets) => {
        if (sets <= 0)
            return;
        s.defs.push(def);
        s.exercises.push(makePlanned(def, allocation.role, sets, policy, request.athlete.experience));
        s.importance.push(allocation.importance);
        trackExerciseUse(def);
        addToLedger(ledger, def, sets);
    }
/* M230:PRESERVE:helper.add:END */
)(...args);
}

export function buildRealizedSessions(context) {
const { request, sessions } = context;
return (
/* M230:PRESERVE:builder.buildRealizedSessions:BEGIN */
sessions.map(s => {
        // Time repair must be importance-aware. Tail trimming recreates legacy list-order starvation: a B/A
        // priority claim added later can disappear before earlier C filler. Protect A, then B, then C exactly
        // as the allocator intended; strength anchors are last-resort removals only.
        const rank = { C: 0, B: 1, A: 2 };
        while (s.exercises.length > 3 && estimateMinutes(s.exercises) > s.plan.maxMinutes) {
            const removable = s.exercises.map((exercise, index) => ({ exercise, index, importance: s.importance[index] ?? 'C', def: s.defs[index] }))
                .sort((a, b) => {
                const strengthA = a.exercise.role === 'primary_strength' || a.exercise.role === 'secondary_strength' ? 1 : 0;
                const strengthB = b.exercise.role === 'primary_strength' || b.exercise.role === 'secondary_strength' ? 1 : 0;
                if (strengthA !== strengthB)
                    return strengthA - strengthB;
                if (rank[a.importance] !== rank[b.importance])
                    return rank[a.importance] - rank[b.importance];
                const aPriority = primaryMuscle(a.def) ? request.goal.musclePriorities[primaryMuscle(a.def)] : 'normal';
                const bPriority = primaryMuscle(b.def) ? request.goal.musclePriorities[primaryMuscle(b.def)] : 'normal';
                const pr = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
                if (pr[aPriority] !== pr[bPriority])
                    return pr[aPriority] - pr[bPriority];
                return a.exercise.sets - b.exercise.sets || b.index - a.index;
            })[0];
            if (!removable)
                break;
            s.exercises.splice(removable.index, 1);
            s.defs.splice(removable.index, 1);
            s.importance.splice(removable.index, 1);
        }
        return { ...s.plan, estimatedMinutes: estimateMinutes(s.exercises), exercises: s.exercises };
    })
/* M230:PRESERVE:builder.buildRealizedSessions:END */
);
}
