// M230 canonical strength stage. Stage context is local to one realization.
import { createExerciseCatalog } from './exercise-db.js';
import { phasePolicyFor } from './phase-policy.js';
import { addToLedger } from './realizer-math.js';
import { makePlanned } from './realizer-prescriptions.js';
import { equipmentEligible, strengthCandidate } from './realizer-ranking.js';
import { estimateSessionMinutes } from './realizer-time-budget.js';

export /* M230:PRESERVE:top.strengthSetsForAllocation:BEGIN */
function strengthSetsForAllocation(allocation, session, request, policy) {
    const shortSession = session.maxMinutes <= 35;
    const denseThreeDayStrength = request.goal.type === 'strength' && request.schedule.days.length <= 3;
    const baseSets = allocation.role === 'primary_strength'
        ? (request.athlete.experience === 'novice' ? 3 : (shortSession ? 3 : 4))
        : (shortSession || denseThreeDayStrength || request.athlete.experience === 'novice' ? 2 : 3);
    return Math.max(2, Math.round(baseSets * policy.strengthVolumeMultiplier));
}
/* M230:PRESERVE:top.strengthSetsForAllocation:END */

export /* M230:PRESERVE:top.realizeStrengthAnchors:BEGIN */
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
/* M230:PRESERVE:top.realizeStrengthAnchors:END */

export function realizePinnedStrength(context) {
const { add, exerciseCatalog, exerciseMap, pinnedStrengthAnchors, policy, request, sessions } = context;
/* M230:PRESERVE:stage.realizePinnedStrength:BEGIN */
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
/* M230:PRESERVE:stage.realizePinnedStrength:END */

}
