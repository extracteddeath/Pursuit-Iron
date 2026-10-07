/** Session realization: session. Maintained production source. */
import { addToLedger } from './rules.js';
import { armCoverageUseKeys } from '../arm-coverage.js';
import { createExerciseCatalog, createExerciseMap } from '../exercise-db.js';
import { functionalCoverageUseKeys } from '../functional-coverage.js';
import { makePlanned } from './prescriptions.js';
import { phasePolicyFor } from '../phase-policy.js';
import { populateHypertrophySessions } from './hypertrophy.js';
import { populateStrengthSessions } from './strength.js';
import { repairRealizedSessions } from './repair.js';

function realizeSessions(plans, request, targetDose = {}, directTargetDose = {}, phase, options = {}) {
    const policy = {
        ...phasePolicyFor(phase),
        blockWeeks: options.blockWeeks,
        requestedProgressionStyle: options.requestedProgressionStyle
    };
    const exerciseCatalog = createExerciseCatalog(request.customExercises);
    const exerciseMap = createExerciseMap(request.customExercises);
    const sessions = plans.map(plan => ({ plan, defs: [], exercises: [], importance: [] }));
    const ledger = { fractional: {}, direct: {} };
    const pinnedStrengthAnchors = options.strengthAnchors ?? {};
    const weeklyMovementUse = new Map();
    const trackExerciseUse = (def) => {
        weeklyMovementUse.set(def.movementFamily, (weeklyMovementUse.get(def.movementFamily) ?? 0) + 1);
        weeklyMovementUse.set(`@${def.id}`, (weeklyMovementUse.get(`@${def.id}`) ?? 0) + 1);
        for (const key of armCoverageUseKeys(def))
            weeklyMovementUse.set(key, (weeklyMovementUse.get(key) ?? 0) + 1);
        for (const key of functionalCoverageUseKeys(def))
            weeklyMovementUse.set(key, (weeklyMovementUse.get(key) ?? 0) + 1);
    };
    const add = (s, allocation, def, sets) => {
        if (sets <= 0)
            return;
        s.defs.push(def);
        s.exercises.push(makePlanned(def, allocation.role, sets, policy, request.athlete.experience));
        s.importance.push(allocation.importance);
        trackExerciseUse(def);
        addToLedger(ledger, def, sets);
    };
    const context = { plans, request, targetDose, directTargetDose, phase, policy, exerciseCatalog, exerciseMap, sessions, ledger, pinnedStrengthAnchors, weeklyMovementUse, trackExerciseUse, add };
    populateStrengthSessions(context);
    populateHypertrophySessions(context);
    return repairRealizedSessions(context);
}

export { realizeSessions };
