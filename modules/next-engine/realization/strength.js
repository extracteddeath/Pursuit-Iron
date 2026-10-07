/** Session realization: strength. Maintained production source. */
import { addToLedger, equipmentEligible } from './rules.js';
import { createExerciseCatalog } from '../exercise-db.js';
import { estimateSessionMinutes } from './time.js';
import { makePlanned } from './prescriptions.js';
import { phasePolicyFor } from '../phase-policy.js';
import { strengthCandidate } from './ranking.js';

function strengthSetsForAllocation(allocation, session, request, policy) {
    const shortSession = session.maxMinutes <= 35;
    const denseThreeDayStrength = request.goal.type === 'strength' && request.schedule.days.length <= 3;
    const baseSets = allocation.role === 'primary_strength'
        ? (request.athlete.experience === 'novice' ? 3 : (shortSession ? 3 : 4))
        : (shortSession || denseThreeDayStrength || request.athlete.experience === 'novice' ? 2 : 3);
    return Math.max(2, Math.round(baseSets * policy.strengthVolumeMultiplier));
}

function realizeStrengthAnchors(plans, request, phase, options = {}) {
    const policy = {
        ...phasePolicyFor(phase),
        blockWeeks: options.blockWeeks,
        requestedProgressionStyle: options.requestedProgressionStyle
    };
    const exerciseCatalog = createExerciseCatalog(request.customExercises);
    const ledger = { fractional: {}, direct: {} };
    const anchors = {};
    const sessionMinutes = {};
    const missingAllocationIds = [];
    let realizedCount = 0;
    for (const plan of plans) {
        const chosen = [];
        const planned = [];
        for (const allocation of plan.allocations.filter(x => x.kind === 'lift')) {
            const def = strengthCandidate(allocation, plan, request, chosen, exerciseCatalog);
            if (!def) {
                missingAllocationIds.push(allocation.id);
                continue;
            }
            const sets = strengthSetsForAllocation(allocation, plan, request, policy);
            const exercise = makePlanned(def, allocation.role, sets, policy, request.athlete.experience);
            chosen.push(def);
            planned.push(exercise);
            addToLedger(ledger, def, sets);
            anchors[allocation.id] = {
                allocationId: allocation.id, exerciseId: def.id, exerciseName: def.name,
                sets, sessionId: plan.id, day: plan.day, role: allocation.role, lift: allocation.lift
            };
            realizedCount++;
        }
        if (planned.length)
            sessionMinutes[plan.id] = estimateSessionMinutes(planned);
    }
    return {
        anchors,
        fractional: ledger.fractional,
        estimatedMinutes: Object.values(sessionMinutes).reduce((sum, value) => sum + value, 0),
        sessionMinutes,
        realizedCount,
        expectedCount: plans.reduce((sum, plan) => sum + plan.allocations.filter(x => x.kind === 'lift').length, 0),
        missingAllocationIds
    };
}

function populateStrengthSessions(context) {
    const { plans, request, targetDose, directTargetDose, phase, policy, exerciseCatalog, exerciseMap, sessions, ledger, pinnedStrengthAnchors, weeklyMovementUse, trackExerciseUse, add } = context;
    // 1) Required/specific lift work first.
    for (const s of sessions)
        for (const a of s.plan.allocations.filter(x => x.kind === 'lift')) {
            const pinned = pinnedStrengthAnchors[a.id];
            const def = pinned ? exerciseMap.get(pinned.exerciseId) : strengthCandidate(a, s.plan, request, s.defs, exerciseCatalog);
            if (!def)
                continue;
            if (pinned) {
                const eligible = equipmentEligible(def, s.plan, request)
                    && !request.preferences.avoidedExercises?.includes(def.id)
                    && (!pinned.sessionId || pinned.sessionId === s.plan.id)
                    && (def.liftSpecificity?.[a.lift] ?? 0) > .45;
                if (!eligible)
                    throw new Error('Pinned strength anchor ' + pinned.exerciseId + ' is no longer eligible for ' + a.id + ' on ' + s.plan.day + '.');
            }
            const phaseSets = pinned?.sets ?? strengthSetsForAllocation(a, s.plan, request, policy);
            add(s, a, def, phaseSets);
        }

}

export { strengthSetsForAllocation, realizeStrengthAnchors, populateStrengthSessions };
