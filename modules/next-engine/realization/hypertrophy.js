/** Session realization: hypertrophy. Maintained production source. */
import { LARGE, SMALL, desiredDirect, integerSetPlan, smallIntentPreference } from './rules.js';
import { muscleCandidate } from './ranking.js';

function populateHypertrophySessions(context) {
    const { plans, request, targetDose, directTargetDose, phase, policy, exerciseCatalog, exerciseMap, sessions, ledger, pinnedStrengthAnchors, weeklyMovementUse, trackExerciseUse, add } = context;
    // 2) Major-muscle work. Convert fractional topology allocations into an integer weekly set plan
    // before selecting exercises so distributing 4 target sets across 3 sessions cannot silently become 3.
    for (const muscle of LARGE) {
        const allEntries = sessions.flatMap(s => s.plan.allocations
            .filter(a => a.kind === 'muscle' && a.muscle === muscle)
            .map(allocation => ({ session: s, allocation })));
        if (!allEntries.length)
            continue;
        const fullBodyCapacityContract = plans.every(p => p.intent === 'full') && (plans.some(p => p.targetExercises !== undefined) || request.preferences.lockedSplit === 'full_body');
        const mandatoryFullBodyMuscle = fullBodyCapacityContract && ['chest', 'back'].includes(muscle);
        const entries = mandatoryFullBodyMuscle
            ? allEntries.filter(entry => !entry.session.defs.some(def => (def.muscles[muscle]?.credit ?? 0) >= 1))
            : allEntries;
        const activeEntries = entries.length ? entries : allEntries;
        const modeledTarget = targetDose[muscle] ?? allEntries.reduce((sum, e) => sum + e.allocation.dose, 0);
        const perUncoveredSession = request.athlete.experience === 'novice' ? 1 : 2;
        const current = ledger.fractional[muscle] ?? 0;
        const target = mandatoryFullBodyMuscle
            ? Math.max(modeledTarget, current + perUncoveredSession * activeEntries.length)
            : modeledTarget;
        const residual = Math.max(0, Math.round(target - current));
        if (residual <= 0)
            continue;
        const setPlan = integerSetPlan(activeEntries, residual);
        for (const entry of activeEntries) {
            const sets = setPlan.get(entry) ?? 0;
            if (sets <= 0)
                continue;
            const def = muscleCandidate(entry.allocation, entry.session.plan, request, entry.session.defs, weeklyMovementUse, exerciseCatalog);
            if (!def)
                continue;
            add(entry.session, entry.allocation, def, sets);
        }
    }
    // 3) Direct isolation after compound carryover is known.

    for (const muscle of SMALL) {
        if (muscle === 'front_delts' && request.goal.musclePriorities.front_delts === 'normal')
            continue;
        const rankedSessions = [...sessions].sort((a, b) => {
            // Topology may deliberately spill a low-interference accessory onto a Lower/Leg day to balance
            // session density. Honor that explicit destination before the generic intent preference; otherwise
            // earlier Upper allocations can satisfy the global direct-volume target and silently erase the move.
            const aSpill = a.plan.allocations.some(x => x.kind === 'muscle' && x.muscle === muscle && x.id.includes('-spill-')) ? 1 : 0;
            const bSpill = b.plan.allocations.some(x => x.kind === 'muscle' && x.muscle === muscle && x.id.includes('-spill-')) ? 1 : 0;
            if (aSpill !== bSpill)
                return bSpill - aSpill;
            const pref = smallIntentPreference[muscle] ?? [];
            const ai = pref.indexOf(a.plan.intent);
            const bi = pref.indexOf(b.plan.intent);
            return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi);
        });
        for (const s of rankedSessions)
            for (const a of s.plan.allocations.filter(x => x.kind === 'muscle' && x.muscle === muscle)) {
                const target = targetDose[muscle] ?? a.dose;
                const totalResidual = Math.max(0, target - (ledger.fractional[muscle] ?? 0));
                const directResidual = Math.max(0, desiredDirect(muscle, target, directTargetDose) - (ledger.direct[muscle] ?? 0));
                const need = Math.max(totalResidual, directResidual);
                if (need < .75)
                    continue;
                const def = muscleCandidate(a, s.plan, request, s.defs, weeklyMovementUse, exerciseCatalog);
                if (!def)
                    continue;
                const sets = Math.max(1, Math.min(4, Math.round(Math.min(a.dose, need))));
                add(s, a, def, sets);
            }
    }

}

export { populateHypertrophySessions };
