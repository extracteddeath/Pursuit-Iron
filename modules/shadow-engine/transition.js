import { createExerciseMap } from '../next-engine/exercise-db.js';
function isStrengthPhase(phase) { return phase === 'strength_accumulation' || phase === 'intensification' || phase === 'peak'; }
function primaryMuscles(ex, map) { const def = map.get(ex.exerciseId); return def ? Object.entries(def.muscles).filter(([, c]) => c.credit >= 1).map(([m]) => m) : []; }
const LIFT_PRIORITY_WEIGHT = { maintenance: 0, normal: .2, high: .75, specialization: .9, primary: 1 };
function relevantLiftSpecificity(ex, map, request) { const def = map.get(ex.exerciseId); if (!def)
    return 0; let best = 0; for (const [lift, priority] of Object.entries(request.goal.liftPriorities))
    best = Math.max(best, (def.liftSpecificity?.[lift] ?? 0) * LIFT_PRIORITY_WEIGHT[priority]); return best; }
export function buildShadowTransitionReport(previous, next, request, successfulIds, protectedIds = []) {
    const map = createExerciseMap(request.customExercises), success = new Set(successfulIds), protectedSet = new Set(protectedIds), nextIds = new Set(next.sessions.flatMap(s => s.exercises.map(e => e.exerciseId)));
    const decisions = [];
    for (const ex of previous.sessions.flatMap(s => s.exercises)) {
        const def = map.get(ex.exerciseId);
        if (!def)
            continue;
        const retained = nextIds.has(ex.exerciseId);
        const specific = relevantLiftSpecificity(ex, map, request);
        const priority = Math.max(0, ...primaryMuscles(ex, map).map(m => ({ maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 }[request.goal.musclePriorities[m] ?? 'normal'])));
        if (retained && (protectedSet.has(ex.exerciseId) || success.has(ex.exerciseId)))
            decisions.push({ exerciseId: ex.exerciseId, exerciseName: ex.name, decision: 'preserve', reason: 'Recent positive evidence plus compatible target-phase structure supports continuity.' });
        else if (isStrengthPhase(next.phase) && specific >= .45 && ex.role.startsWith('hypertrophy'))
            decisions.push({ exerciseId: ex.exerciseId, exerciseName: ex.name, decision: retained ? 'promote' : 'rotate', reason: retained ? 'The movement carries useful lift specificity into the strength-focused phase.' : 'A more phase-specific movement won the counterfactual while preserving the same training target.' });
        else if (next.phase === 'peak' && priority <= 1 && ex.role.includes('hypertrophy'))
            decisions.push({ exerciseId: ex.exerciseId, exerciseName: ex.name, decision: retained ? 'demote' : 'rotate', reason: retained ? 'Kept only as low-dose tissue maintenance during peaking.' : 'Low-priority accessory cost was removed as specificity and recovery became more valuable.' });
        else if (!retained)
            decisions.push({ exerciseId: ex.exerciseId, exerciseName: ex.name, decision: 'rotate', reason: 'The target phase or weekly counterfactual scored better without preserving this exercise identity.' });
    }
    const eligible = successfulIds.filter(id => previous.sessions.some(s => s.exercises.some(e => e.exerciseId === id))).length;
    const retained = successfulIds.filter(id => nextIds.has(id)).length;
    return { fromPhase: previous.phase, toPhase: next.phase, decisions, successfulRetained: retained, successfulEligible: eligible };
}
