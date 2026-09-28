import { ALLOCATION_CONFIG, PRIORITY_UTILITY } from './config.js';
import { phasePolicyFor } from './phase-policy.js';
import { productiveTimeBandDoseTarget, selectStrengthClaimsForCapacity } from './prescription.js';
const projectedStrengthContribution = {
    bench_press: { chest: 7, triceps: 3.5, front_delts: 3.5 },
    back_squat: { quads: 4, glutes: 2 },
    deadlift: { hamstrings: 3, glutes: 2, back: 1, lower_back: 1.5, traps: .75, forearms: .5 },
    overhead_press: { front_delts: 5, triceps: 2.5, side_delts: 1, traps: .5 }
};
const SMALL = ['side_delts', 'rear_delts', 'front_delts', 'biceps', 'triceps', 'calves', 'core', 'traps', 'forearms', 'adductors', 'abductors', 'neck', 'lower_back'];
const PUSH = ['chest', 'front_delts', 'side_delts', 'triceps'];
const PULL = ['back', 'rear_delts', 'biceps', 'traps', 'forearms'];
const LOWER = ['quads', 'hamstrings', 'glutes', 'calves', 'adductors', 'abductors', 'lower_back'];
const MINUTES_PER_SET = {
    chest: 3.0,
    back: 2.8,
    side_delts: 1.45,
    rear_delts: 1.45,
    front_delts: 2.4,
    biceps: 1.55,
    triceps: 1.55,
    quads: 3.1,
    hamstrings: 2.65,
    glutes: 2.7,
    calves: 1.5,
    core: 1.4,
    traps: 1.55,
    forearms: 1.25,
    adductors: 1.55,
    abductors: 1.35,
    neck: 1.15,
    lower_back: 1.9
};
const FATIGUE_PRICE = {
    chest: .13,
    back: .14,
    side_delts: .045,
    rear_delts: .045,
    front_delts: .11,
    biceps: .05,
    triceps: .05,
    quads: .16,
    hamstrings: .15,
    glutes: .14,
    calves: .04,
    core: .035,
    traps: .055,
    forearms: .035,
    adductors: .05,
    abductors: .04,
    neck: .045,
    lower_back: .12
};
function directTarget(p) {
    if (p.muscle === 'front_delts' && p.priority === 'normal')
        return 0;
    if (!SMALL.includes(p.muscle))
        return 0;
    return Math.min(p.directPreferred, p.preferred);
}
function directMinimum(p) {
    if (p.muscle === 'front_delts' && p.priority === 'normal')
        return 0;
    if (!SMALL.includes(p.muscle))
        return 0;
    return Math.min(p.directMinimum, p.minimum);
}
function saturationValue(dose, p) {
    // Engineering utility curve. It is intentionally normalized around the modeled useful region,
    // not presented as a physiological law.
    const scale = Math.max(3, p.preferred * .72);
    return 1 - Math.exp(-dose / scale);
}
function marginalBenefit(current, p) {
    const raw = saturationValue(current + 1, p) - saturationValue(current, p);
    const belowMinimum = current < p.minimum;
    const belowPreferred = current < p.preferred;
    const regionBoost = belowMinimum ? ALLOCATION_CONFIG.floorUtilityBoost : belowPreferred ? 1.2 : .78;
    return raw * PRIORITY_UTILITY[p.priority] * regionBoost;
}
function directDeficitBonus(currentDirect, p) {
    const min = directMinimum(p);
    const preferred = directTarget(p);
    if (currentDirect < min)
        return .11;
    if (currentDirect < preferred)
        return .045;
    return 0;
}
function regionalPressure(muscle, state) {
    const denominator = Math.max(18, state.weeklyMinuteBudget / 15);
    const load = PUSH.includes(muscle) ? state.pushLoad : PULL.includes(muscle) ? state.pullLoad : LOWER.includes(muscle) ? state.lowerLoad : 0;
    const ratio = load / denominator;
    if (ratio < .55)
        return 0;
    if (ratio < .8)
        return (ratio - .55) * .05;
    return .0125 + Math.pow(ratio - .8, 2) * .35;
}
function minuteShadowPrice(state) {
    const ratio = state.estimatedMinutesUsed / Math.max(1, state.weeklyMinuteBudget);
    if (ratio < .55)
        return .006;
    if (ratio < ALLOCATION_CONFIG.minuteSoftPressureStart)
        return .008;
    if (ratio < .9)
        return .012 + (ratio - ALLOCATION_CONFIG.minuteSoftPressureStart) * .035;
    return .02 + Math.pow(ratio - .9, 2) * .5;
}
function bidFor(p, state, phase) {
    // Optional v661-era regions are available for explicit prioritization and accounting, but a maintenance
    // default with zero preferred dose must not become filler merely because session time exists.
    if (p.priority === 'maintenance' && p.minimum === 0 && p.preferred === 0)
        return null;
    const current = state.dose[p.muscle];
    if (current + .5 > p.upper)
        return null;
    // As strength specificity rises, normal-priority bodybuilding work should maintain useful tissue,
    // not keep bidding toward MRV simply because the clock has room. This creates real block contrast:
    // accumulation can chase productive surplus, while strength/peak phases cap ordinary accessories
    // around the modeled preferred region and reserve recovery for specific lifting.
    if ((phase === 'strength_accumulation' || phase === 'intensification' || phase === 'peak')
        && (p.priority === 'normal' || p.priority === 'maintenance')
        && current + .001 >= p.preferred)
        return null;
    // Fatigue-limited peaking should be selective: once a normal-priority muscle has met its reduced
    // minimum, do not spend recovery budget chasing optional bodybuilding volume simply because time
    // remains. High/specialization/primary priorities still bid normally.
    if (phase === 'peak' && (state.responseCapacityScale < .9) && (p.priority === 'normal' || p.priority === 'maintenance') && current + .001 >= p.minimum)
        return null;
    const benefit = marginalBenefit(current, p) + directDeficitBonus(state.direct[p.muscle], p);
    const price = MINUTES_PER_SET[p.muscle] * minuteShadowPrice(state)
        + FATIGUE_PRICE[p.muscle] * .11
        + regionalPressure(p.muscle, state);
    return {
        muscle: p.muscle,
        benefit,
        price,
        utility: benefit - price,
        reason: `${p.muscle} marginal value ${benefit.toFixed(3)} minus price ${price.toFixed(3)}`
    };
}
function addDose(state, p, direct) {
    state.dose[p.muscle] += 1;
    if (direct)
        state.direct[p.muscle] += 1;
    state.estimatedMinutesUsed += MINUTES_PER_SET[p.muscle];
    if (PUSH.includes(p.muscle))
        state.pushLoad += 1;
    if (PULL.includes(p.muscle))
        state.pullLoad += 1;
    if (LOWER.includes(p.muscle))
        state.lowerLoad += 1;
}
function shouldCountAllocatedSetAsDirect(p, state) {
    // Muscle claims for small muscles are realized as isolation work; large muscle claims are direct
    // compound/isolation work by construction. This flag primarily enforces a small-muscle direct floor.
    if (!SMALL.includes(p.muscle))
        return true;
    return state.direct[p.muscle] < directTarget(p) || state.dose[p.muscle] < p.minimum;
}
export function allocateTraining(request, muscles, strengthClaims, phase) {
    const allocations = [];
    const projectedFromStrength = {};
    const decisions = [];
    const decisionLog = [];
    let iteration = 0;
    const totalWeeklyMinutes = request.schedule.days.reduce((sum, d) => sum + d.maxMinutes, 0);
    const totalWeeklyMinimum = request.schedule.days.reduce((sum, d) => sum + (d.minMinutes ?? 0), 0);
    const phasePolicy = phase ? phasePolicyFor(phase) : undefined;
    // Reserve warm-ups/transitions and avoid treating every available minute as productive set capacity.
    // Later strength/peak blocks intentionally leave more available gym time unused because the marginal
    // value of extra bodybuilding work is lower than the recovery cost of carrying it into heavy work.
    const phaseBudgetMultiplier = phase === 'peak' ? .58 : phase === 'intensification' ? .72 : phase === 'strength_accumulation' ? .84 : 1;
    const responseCapacityScale = request.preferences.responseCapacityScale ?? 1;
    const baseWeeklyBudget = totalWeeklyMinutes * .74 * phaseBudgetMultiplier;
    // A duration card is a capacity band, not just a ceiling. In accumulation blocks, reserve enough
    // weekly productive budget to let the allocator meaningfully use the selected band before the
    // marginal-utility market decides whether additional work is worthwhile.
    const productiveBandFraction = phase === 'hypertrophy_accumulation' ? .55 : phase === 'mixed_accumulation' ? .45 : 0;
    const productiveBandBudget = productiveBandFraction > 0
        ? totalWeeklyMinimum + Math.max(0, totalWeeklyMinutes - totalWeeklyMinimum) * productiveBandFraction
        : 0;
    const weeklyMinuteBudget = Math.max(60, Math.max(baseWeeklyBudget, productiveBandBudget) * responseCapacityScale);
    let strengthMinutes = 0;
    // Required lift work is inviolable. Preferred/optional lift work is admitted only when the
    // weekly schedule has realistic room for another strength exposure. Previously every optional
    // claim was allocated unconditionally, which could force six S/B/D exposures into two 60-minute
    // full-body sessions and then starve basic muscle floors.
    const selectedStrength = selectStrengthClaimsForCapacity(request, strengthClaims);
    for (const claim of selectedStrength) {
        allocations.push({
            id: `alloc-${claim.id}`,
            kind: 'lift',
            lift: claim.lift,
            role: claim.role === 'heavy' ? 'primary_strength' : 'secondary_strength',
            dose: 1,
            importance: claim.importance
        });
        const projected = projectedStrengthContribution[claim.lift] ?? {};
        const setEquivalent = claim.role === 'heavy' ? 4 : 3;
        const scale = setEquivalent / 7;
        for (const [muscle, amount] of Object.entries(projected)) {
            projectedFromStrength[muscle] = (projectedFromStrength[muscle] ?? 0) + amount * scale;
        }
        strengthMinutes += claim.role === 'heavy' ? 20 : 15;
        decisions.push(`${claim.role} ${claim.lift} reserved before optional muscle work.`);
        decisionLog.push({
            iteration: iteration++,
            target: claim.lift,
            action: 'required',
            reason: `${claim.role} ${claim.lift} is a ${claim.required ? 'required' : 'capacity-approved'} ${claim.importance}-priority strength claim and reserves resources before hypertrophy bidding.`
        });
    }
    const byMuscle = new Map(muscles.map(p => [p.muscle, p]));
    const dose = Object.fromEntries(muscles.map(p => [p.muscle, projectedFromStrength[p.muscle] ?? 0]));
    const direct = Object.fromEntries(muscles.map(p => [p.muscle, 0]));
    const state = {
        dose,
        direct,
        projectedFromStrength,
        estimatedMinutesUsed: strengthMinutes,
        weeklyMinuteBudget,
        pushLoad: 0,
        pullLoad: 0,
        lowerLoad: 0,
        responseCapacityScale
    };
    // Regional pressure is based on approximate training events, not the sum of every muscle's
    // fractional credit from the same compound set. Using the maximum contribution avoids
    // double/triple-pricing one bench set as chest + triceps + front-delt regional work.
    state.pushLoad = Math.max(...PUSH.map(m => projectedFromStrength[m] ?? 0), 0);
    state.pullLoad = Math.max(...PULL.map(m => projectedFromStrength[m] ?? 0), 0);
    state.lowerLoad = Math.max(...LOWER.map(m => projectedFromStrength[m] ?? 0), 0);
    // Mandatory/quality floors are solved before the optional market. Most constrained/high-priority
    // muscles go first, but every floor is protected from list-order starvation.
    const floorOrder = [...muscles].sort((a, b) => {
        const aNeed = Math.max(0, a.minimum - state.dose[a.muscle]);
        const bNeed = Math.max(0, b.minimum - state.dose[b.muscle]);
        const aPriority = PRIORITY_UTILITY[a.priority];
        const bPriority = PRIORITY_UTILITY[b.priority];
        return (bNeed * bPriority) - (aNeed * aPriority);
    });
    for (const p of floorOrder) {
        while (state.dose[p.muscle] + .001 < p.minimum) {
            const directSet = shouldCountAllocatedSetAsDirect(p, state);
            addDose(state, p, directSet);
            decisionLog.push({
                iteration: iteration++,
                target: p.muscle,
                action: 'floor',
                reason: `${p.muscle} remains below its modeled minimum region, so one set-equivalent is reserved before optional bidding.`
            });
        }
        while (state.direct[p.muscle] + .001 < directMinimum(p)) {
            addDose(state, p, true);
            decisionLog.push({
                iteration: iteration++,
                target: p.muscle,
                action: 'floor',
                reason: `${p.muscle} direct-work floor is not yet met after indirect compound credit.`
            });
        }
    }
    // Capacity-aware productive reservation. M50 restored the minimum→preferred part of the old time-
    // bucket contract. M51 extends the same rule through the still-modeled preferred→upper region for
    // genuinely longer buckets. This keeps 60–90, 90–120 and 120+ from sharing one effective ceiling:
    // more explicitly available time can buy more recoverable work, but never beyond the muscle's upper
    // landmark and never just to fill the clock.
    if (phase === 'hypertrophy_accumulation' || phase === 'mixed_accumulation') {
        const productiveOrder = [...muscles].sort((a, b) => {
            const pa = PRIORITY_UTILITY[a.priority], pb = PRIORITY_UTILITY[b.priority];
            const ta = productiveTimeBandDoseTarget(request, a, phase);
            const tb = productiveTimeBandDoseTarget(request, b, phase);
            const da = Math.max(0, ta - state.dose[a.muscle]);
            const db = Math.max(0, tb - state.dose[b.muscle]);
            return (db * pb) - (da * pa) || a.muscle.localeCompare(b.muscle);
        });
        for (const p of productiveOrder) {
            const target = productiveTimeBandDoseTarget(request, p, phase);
            while (state.dose[p.muscle] + .001 < target) {
                const cost = MINUTES_PER_SET[p.muscle];
                if (state.estimatedMinutesUsed + cost > state.weeklyMinuteBudget)
                    break;
                const directSet = shouldCountAllocatedSetAsDirect(p, state);
                addDose(state, p, directSet);
                decisionLog.push({
                    iteration: iteration++, target: p.muscle, action: 'floor',
                    reason: `${p.muscle} receives productive time-band dose toward its band-specific adaptive target before optional bidding.`
                });
            }
        }
    }
    const capacityContract = request.schedule.days.every(day => day.targetExercises !== undefined);
    const baseCapacityUtility = capacityContract
        ? request.athlete.experience === 'advanced' ? .012 : request.athlete.experience === 'intermediate' ? .018 : .024
        : ALLOCATION_CONFIG.minimumUsefulUtility;
    const phaseUtilityFloor = phase === 'peak' ? .042 : phase === 'intensification' ? .035 : phase === 'strength_accumulation' ? .026 : baseCapacityUtility;
    const minimumUsefulUtility = Math.max(baseCapacityUtility, phaseUtilityFloor);
    void phasePolicy;
    let stopUtility = null;
    let marginalIterations = 0;
    while (marginalIterations < ALLOCATION_CONFIG.maxMarginalIterations) {
        const bids = muscles
            .map(p => ({ p, bid: bidFor(p, state, phase) }))
            .filter((x) => !!x.bid)
            .sort((a, b) => b.bid.utility - a.bid.utility || a.p.muscle.localeCompare(b.p.muscle));
        const best = bids[0];
        if (!best)
            break;
        if (best.bid.utility < minimumUsefulUtility) {
            stopUtility = best.bid.utility;
            decisionLog.push({
                iteration: iteration++,
                target: best.p.muscle,
                action: 'stop',
                utility: best.bid.utility,
                benefit: best.bid.benefit,
                price: best.bid.price,
                reason: `Stopped with unused capacity because the best remaining bid (${best.p.muscle}) fell below the minimum useful utility.`
            });
            break;
        }
        if (state.estimatedMinutesUsed + MINUTES_PER_SET[best.p.muscle] > state.weeklyMinuteBudget) {
            stopUtility = best.bid.utility;
            decisionLog.push({
                iteration: iteration++,
                target: best.p.muscle,
                action: 'stop',
                utility: best.bid.utility,
                benefit: best.bid.benefit,
                price: best.bid.price,
                reason: 'Stopped optional allocation because the productive weekly time budget is exhausted.'
            });
            break;
        }
        const directSet = shouldCountAllocatedSetAsDirect(best.p, state);
        addDose(state, best.p, directSet);
        decisionLog.push({
            iteration: iteration++,
            target: best.p.muscle,
            action: 'marginal',
            utility: best.bid.utility,
            benefit: best.bid.benefit,
            price: best.bid.price,
            reason: best.bid.reason
        });
        marginalIterations++;
    }
    const targetDose = {};
    const directTargetDose = {};
    for (const p of muscles) {
        // Do not force normal front-delt isolation when pressing has already covered it.
        if (p.muscle === 'front_delts' && p.priority === 'normal') {
            state.dose[p.muscle] = Math.max(projectedFromStrength[p.muscle] ?? 0, Math.min(state.dose[p.muscle], p.minimum));
            state.direct[p.muscle] = 0;
        }
        const target = Math.round(Math.min(p.upper, state.dose[p.muscle]) * 10) / 10;
        const directTarget = Math.round(Math.min(target, state.direct[p.muscle]) * 10) / 10;
        targetDose[p.muscle] = target;
        directTargetDose[p.muscle] = directTarget;
        const residual = Math.max(0, target - (projectedFromStrength[p.muscle] ?? 0));
        if (residual > .25) {
            allocations.push({
                id: `alloc-${p.muscle}`,
                kind: 'muscle',
                muscle: p.muscle,
                role: SMALL.includes(p.muscle) ? 'hypertrophy_isolation' : 'hypertrophy_compound',
                dose: residual,
                importance: p.priority === 'specialization' || p.priority === 'primary' ? 'A' : p.priority === 'high' ? 'B' : 'C'
            });
        }
    }
    const lastMarginal = [...decisionLog].reverse().find(d => d.action === 'marginal');
    if (lastMarginal)
        decisions.push(`Optional allocation stopped after ${marginalIterations} marginal awards; unused capacity is allowed when additional work has poor value.`);
    return {
        allocations,
        targetDose,
        directTargetDose,
        projectedFromStrength,
        decisions,
        decisionLog,
        metrics: {
            weeklyMinuteBudget: Math.round(weeklyMinuteBudget),
            estimatedMinutesUsed: Math.round(state.estimatedMinutesUsed),
            marginalIterations,
            stopUtility: stopUtility === null ? null : Math.round(stopUtility * 1000) / 1000
        }
    };
}
