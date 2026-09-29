import { ALL_MUSCLES, MUSCLE_DOSE_PRIOR, OPTIONAL_MUSCLES, PRIORITY_MULTIPLIER } from './config.js';
import { initialPhaseForGoal, phasePolicyFor } from './phase-policy.js';
import { normalizeLoadingInventory } from './loading.js';
import { EXERCISE_MAP } from './exercise-db.js';
const DEFAULT_PRIORITY = 'normal';
function exerciseEligibleForRequest(ex, request) {
    // Feasibility uses the same candidate universe as realization. An exercise the athlete
    // explicitly avoided cannot rescue a lift/muscle feasibility check and then disappear later.
    if (request.preferences?.avoidedExercises?.includes(ex.id))
        return false;
    if ((ex.flags.bodyweight || ex.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
        return false;
    const setups = [ex.equipment, ...(ex.equipmentAlternatives ?? [])];
    return request.schedule.days.some(day => {
        const available = day.equipmentOverride ?? request.equipment.available;
        return setups.some(setup => setup.every(item => item === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : available.includes(item)));
    });
}
function liftFeasibleSomewhere(request, lift) {
    const catalog = [...EXERCISE_MAP.values(), ...(request.customExercises ?? [])];
    return catalog.some(ex => (ex.liftSpecificity?.[lift] ?? 0) > .45 && exerciseEligibleForRequest(ex, request));
}
function muscleExerciseFeasibility(request, muscle) {
    const catalog = [...EXERCISE_MAP.values(), ...(request.customExercises ?? [])];
    let any = false, direct = false;
    for (const ex of catalog) {
        const credit = ex.muscles?.[muscle]?.credit ?? 0;
        if (credit <= 0 || !exerciseEligibleForRequest(ex, request))
            continue;
        any = true;
        if (credit >= 1)
            direct = true;
        if (any && direct)
            break;
    }
    return { any, direct };
}
export function filterFeasibleLiftPriorities(request, priorities) {
    return Object.fromEntries(Object.entries(priorities).filter(([lift]) => liftFeasibleSomewhere(request, lift)));
}
function inferredLiftPriorities(request) {
    const explicit = request.goal.liftPriorities ?? {};
    // Explicit user lift priorities remain literal intent. If the requested lift is impossible with the
    // supplied equipment, the normal audit/recovery path must surface that conflict instead of silently
    // changing the athlete's request. Only engine-inferred defaults are equipment-aware and droppable.
    if (Object.keys(explicit).length)
        return explicit;
    const split = request.preferences?.lockedSplit ?? request.preferences?.preferredSplit;
    let inferred = {};
    if (request.goal.type === 'strength') {
        if (['five_three_one', 'five31_beginner', 'gzclp', 'rippler', 'jt', 'sbd_power'].includes(split ?? '')) {
            // Selecting a named strength system is itself explicit lift intent. Do not silently rewrite the
            // structure when equipment cannot support it; downstream audit must fail closed.
            return { bench_press: 'high', back_squat: 'high', deadlift: 'high', overhead_press: 'high' };
        }
        else if (split === 'academy_prep') {
            return { bench_press: 'high', back_squat: 'high', deadlift: 'normal', overhead_press: 'normal' };
        }
        else {
            // A general strength goal still needs specific strength practice, but inferred competition-lift
            // anchors only exist when at least one selected training day can actually perform them. This
            // keeps dumbbell/machine/home constraints from generating impossible high-priority claims.
            inferred = { bench_press: 'high', back_squat: 'high', deadlift: 'high', overhead_press: 'normal' };
        }
    }
    else if (request.goal.type === 'mixed') {
        // Powerbuilding-oriented named splits should contain actual strength anchors when the selected
        // equipment can support them. Normal priority creates one capacity-approved exposure rather than
        // forcing duplicate volume work.
        if (split === 'phul')
            inferred = { bench_press: 'normal', back_squat: 'normal', deadlift: 'normal', overhead_press: 'normal' };
        else if (['phat', 'ula', 'torso_limbs'].includes(split ?? ''))
            inferred = { bench_press: 'normal', back_squat: 'normal', deadlift: 'normal' };
        else if (['ulppl', 'pplul', 'hybrid', 'full_body_patterns', 'ppla', 'jt'].includes(split ?? ''))
            inferred = { bench_press: 'normal', back_squat: 'normal', deadlift: 'normal' };
    }
    return filterFeasibleLiftPriorities(request, inferred);
}
const MUSCLE_SCALE = {
    chest: { minimum: 1, preferred: 1, upper: 1, direct: 0 },
    back: { minimum: 1, preferred: 1, upper: 1, direct: 0 },
    quads: { minimum: 1, preferred: 1, upper: 1, direct: 0 },
    hamstrings: { minimum: .82, preferred: .88, upper: .92, direct: 0 },
    glutes: { minimum: .62, preferred: .72, upper: .8, direct: 0 },
    side_delts: { minimum: .52, preferred: .72, upper: .82, direct: .62 },
    rear_delts: { minimum: .42, preferred: .62, upper: .74, direct: .58 },
    front_delts: { minimum: .15, preferred: .38, upper: .58, direct: .35 },
    biceps: { minimum: .48, preferred: .65, upper: .76, direct: .55 },
    triceps: { minimum: .48, preferred: .65, upper: .76, direct: .55 },
    calves: { minimum: .45, preferred: .62, upper: .72, direct: .7 },
    core: { minimum: .28, preferred: .42, upper: .55, direct: .75 },
    traps: { minimum: .24, preferred: .42, upper: .58, direct: .65 },
    forearms: { minimum: .18, preferred: .34, upper: .48, direct: .75 },
    adductors: { minimum: .12, preferred: .25, upper: .40, direct: .7 },
    abductors: { minimum: .12, preferred: .25, upper: .40, direct: .7 },
    neck: { minimum: .10, preferred: .22, upper: .34, direct: .9 },
    lower_back: { minimum: .10, preferred: .24, upper: .38, direct: .45 }
};
export function normalizeRequest(request) {
    const dayOrder = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
    if (request.schedule.days.length < 2 || request.schedule.days.length > 7)
        throw new Error('Programs require between 2 and 7 training days.');
    if (new Set(request.schedule.days.map(d => d.day)).size !== request.schedule.days.length)
        throw new Error('Training days must be unique.');
    if (request.schedule.days.some(d => !Number.isFinite(d.maxMinutes) || d.maxMinutes < 20))
        throw new Error('Each training day needs a valid time limit of at least 20 minutes.');
    if (request.schedule.days.some(d => d.minMinutes !== undefined && (!Number.isFinite(d.minMinutes) || d.minMinutes < 0 || d.minMinutes > d.maxMinutes)))
        throw new Error('Session minimum minutes must be a valid lower bound no greater than the session maximum.');
    if (request.schedule.days.some(d => d.targetExercises !== undefined && (!Number.isInteger(d.targetExercises) || d.targetExercises < 2 || d.targetExercises > 12)))
        throw new Error('Target exercises per session must be a whole number between 2 and 12.');
    if (!request.equipment.available.length)
        throw new Error('At least one equipment option is required.');
    const customIds = new Set();
    for (const exercise of request.customExercises ?? []) {
        if (!exercise.id?.trim() || !exercise.name?.trim())
            throw new Error('Custom exercises require stable IDs and names.');
        if (EXERCISE_MAP.has(exercise.id))
            throw new Error(`Custom exercise ${exercise.id} collides with a built-in exercise ID.`);
        if (customIds.has(exercise.id))
            throw new Error(`Duplicate custom exercise ID: ${exercise.id}.`);
        customIds.add(exercise.id);
        if (!exercise.equipment?.length && !exercise.flags?.bodyweight)
            throw new Error(`Custom exercise ${exercise.name} needs an equipment requirement.`);
        if (!Object.keys(exercise.muscles ?? {}).length)
            throw new Error(`Custom exercise ${exercise.name} needs at least one muscle target.`);
    }
    const scheduleDays = [...request.schedule.days].sort((a, b) => dayOrder.indexOf(a.day) - dayOrder.indexOf(b.day));
    const goalWeights = request.goal.type === 'hypertrophy'
        ? { hypertrophyWeight: 1, strengthWeight: .15 }
        : request.goal.type === 'strength'
            ? { hypertrophyWeight: .3, strengthWeight: 1 }
            : { hypertrophyWeight: .75, strengthWeight: .75 };
    const optional = new Set(OPTIONAL_MUSCLES);
    const musclePriorities = Object.fromEntries(ALL_MUSCLES.map(m => [m, request.goal.musclePriorities?.[m] ?? (optional.has(m) ? 'maintenance' : DEFAULT_PRIORITY)]));
    return {
        ...request,
        equipment: { ...request.equipment, loading: normalizeLoadingInventory(request.equipment.loading) },
        schedule: { days: scheduleDays },
        athlete: { experience: request.athlete.experience, trainingAgeMonths: request.athlete.trainingAgeMonths ?? 12 },
        goal: { ...request.goal, ...goalWeights, musclePriorities, liftPriorities: inferredLiftPriorities(request) },
        restrictions: {
            maxBarbellMovementsPerDay: request.restrictions?.maxBarbellMovementsPerDay ?? 2,
            allowSupersets: request.restrictions?.allowSupersets !== false
        },
        preferences: {
            ...(request.preferences ?? {}),
            responseCapacityScale: request.preferences?.responseCapacityScale === undefined ? undefined : Math.max(.6, Math.min(1, request.preferences.responseCapacityScale))
        },
        customExercises: request.customExercises?.map(ex => structuredClone(ex)) ?? [],
        seed: request.seed ?? 42
    };
}
export function createMusclePrescriptions(request, phase = initialPhaseForGoal(request.goal.type)) {
    const prior = MUSCLE_DOSE_PRIOR[request.athlete.experience];
    const policy = phasePolicyFor(phase);
    const minimalist = request.preferences.volumeApproach === 'minimalist';
    // Minimalist is an explicit user tradeoff: preserve meaningful weekly floors, but target materially
    // less total work because each work set is prescribed closer to failure. Audit uses the same
    // approach-aware landmarks, so the engine cannot claim standard-volume MEV while programming less.
    const approach = { minimum: minimalist ? .82 : 1, preferred: minimalist ? .70 : 1, upper: minimalist ? .72 : 1 };
    const base = {
        minimum: prior.minimum * policy.volumeMultiplier * approach.minimum,
        preferred: prior.preferred * policy.volumeMultiplier * approach.preferred,
        upper: prior.upper * Math.max(policy.volumeMultiplier, .62) * approach.upper
    };
    // Dose landmarks are priors, not immutable quotas. When hard strength priorities consume a large
    // share of a very short training week, compress normal-priority muscle floors instead of generating
    // bloated sessions or pretending every population-level minimum can coexist. Priority work is
    // progressively protected from this capacity compression.
    const totalWeeklyMinutes = request.schedule.days.reduce((sum, day) => sum + day.maxMinutes, 0);
    const averageSessionMinutes = totalWeeklyMinutes / Math.max(1, request.schedule.days.length);
    const compactWeek = averageSessionMinutes <= 45;
    const veryCompactWeek = averageSessionMinutes <= 35;
    const hardStrengthClaims = Object.values(request.goal.liftPriorities).reduce((count, priority) => count + (priority === 'primary' ? 2 : priority === 'high' ? 1 : 0), 0);
    const residualForMuscleWork = Math.max(0, totalWeeklyMinutes - hardStrengthClaims * 20);
    const densityScale = veryCompactWeek ? .82 : compactWeek ? .92 : 1;
    const historyCapacityScale = request.preferences.responseCapacityScale ?? 1;
    const capacityScale = Math.max(.35, Math.min(1, residualForMuscleWork / 120) * densityScale);
    const priorityCapacityScale = (priority) => {
        const historyScale = priority === 'primary' || priority === 'specialization' ? Math.max(.9, historyCapacityScale) : priority === 'high' ? Math.max(.82, historyCapacityScale) : historyCapacityScale;
        const baseScale = priority === 'primary' || priority === 'specialization' ? 1 : priority === 'high' ? Math.max(veryCompactWeek ? .72 : .85, capacityScale) : capacityScale;
        return baseScale * historyScale;
    };
    const feasibility = Object.fromEntries(ALL_MUSCLES.map(muscle => [muscle, muscleExerciseFeasibility(request, muscle)]));
    return ALL_MUSCLES.map((muscle) => {
        const priority = request.goal.musclePriorities[muscle];
        const mult = PRIORITY_MULTIPLIER[priority];
        const compactSmallNormal = compactWeek && priority === 'normal' && ['side_delts', 'rear_delts', 'biceps', 'triceps', 'calves', 'core'].includes(muscle);
        const resourceScale = priorityCapacityScale(priority) * (compactSmallNormal ? (veryCompactWeek ? .55 : .75) : 1);
        const scale = MUSCLE_SCALE[muscle];
        const normalFrontDelt = muscle === 'front_delts' && priority === 'normal';
        const optionalMaintenance = OPTIONAL_MUSCLES.includes(muscle) && priority === 'maintenance';
        const equipmentFeasibility = feasibility[muscle];
        // Population-level landmarks cannot be a hard contract for an ordinary muscle target when the
        // selected equipment has literally no exercise that trains it. Relax only normal/maintenance
        // defaults; explicit high/specialization/primary intent remains fail-closed so the UI can ask the
        // athlete to change equipment or priority rather than silently ignoring a requested focus.
        const equipmentImpossibleNormal = (priority === 'normal' || priority === 'maintenance') && !equipmentFeasibility.any;
        const normalWithoutDirect = priority === 'normal' && !equipmentFeasibility.direct;
        const isPeak = phase === 'peak';
        const peakPriority = priority === 'high' || priority === 'specialization' || priority === 'primary';
        const compactNormal = compactWeek && (priority === 'normal' || priority === 'maintenance');
        const fatigueTaperedPeakNormal = isPeak && historyCapacityScale < .9 && !peakPriority;
        const minimumFloor = optionalMaintenance ? 0 : isPeak ? (peakPriority ? 2 : (fatigueTaperedPeakNormal ? 0 : 1)) : compactSmallNormal ? 0 : (muscle === 'core' ? 1 : compactNormal ? 1 : 2);
        const compactStrengthSmallOptional = request.goal.type === 'strength' && compactSmallNormal;
        // A short strength week should protect the strength spine and large-muscle minimums first. Normal
        // small-muscle isolation is useful when capacity remains, but it is not a mandatory floor that
        // should manufacture four separate one-set accessories in a 20–40 minute session.
        const minimum = equipmentImpossibleNormal ? 0 : normalFrontDelt ? 0 : optionalMaintenance ? 0 : compactStrengthSmallOptional ? 0 : Math.max(minimumFloor, Math.round(base.minimum * Math.min(1, mult) * scale.minimum * resourceScale));
        const preferred = equipmentImpossibleNormal ? 0 : normalFrontDelt ? 0 : optionalMaintenance ? 0 : Math.max(minimum, Math.round(base.preferred * mult * scale.preferred * resourceScale));
        const upper = equipmentImpossibleNormal ? 0 : normalFrontDelt ? Math.max(isPeak ? 2 : 4, Math.round(base.upper * .45)) : optionalMaintenance ? Math.max(2, Math.round(base.upper * .25)) : Math.max(preferred, Math.round(base.upper * mult * scale.upper));
        const directPreferred = normalWithoutDirect ? 0 : optionalMaintenance || equipmentImpossibleNormal ? 0 : Math.round(preferred * scale.direct);
        const directMinimum = normalWithoutDirect ? 0 : optionalMaintenance || equipmentImpossibleNormal ? 0 : priority === 'high' || priority === 'specialization' || priority === 'primary' ? Math.max(2, Math.round(minimum * scale.direct)) : 0;
        return {
            muscle,
            priority,
            minimum,
            preferred,
            upper,
            directMinimum,
            directPreferred
        };
    });
}
/**
 * Translate the selected session-time band into a productive weekly dose target.
 *
 * The lower edge is capacity the athlete explicitly made available, but it is not a clock-filling
 * quota. Short/standard bands first fund the minimum -> preferred region. Longer bands progressively
 * unlock the still-modeled preferred -> upper region so 60-90, 90-120 and 120+ do not collapse to
 * the same prescription. Phase scaling preserves real powerbuilding/strength tapering.
 */
export function productiveTimeBandDoseTarget(request, prescription, phase) {
    // Optional regions stay opt-in. Extra time may deepen useful work for modeled targets, but it must
    // never turn a zero-dose maintenance default into neck/forearm/lower-back filler.
    if (prescription.priority === 'maintenance' && prescription.minimum === 0 && prescription.preferred === 0)
        return 0;
    const averageMinimumMinutes = request.schedule.days.reduce((sum, day) => sum + (day.minMinutes ?? 0), 0) / Math.max(1, request.schedule.days.length);
    let accumulationTarget = prescription.minimum;
    if (averageMinimumMinutes >= 120) {
        accumulationTarget = prescription.preferred + (prescription.upper - prescription.preferred) * .60;
    }
    else if (averageMinimumMinutes >= 90) {
        accumulationTarget = prescription.preferred + (prescription.upper - prescription.preferred) * .42;
    }
    else if (averageMinimumMinutes >= 60) {
        accumulationTarget = prescription.preferred + (prescription.upper - prescription.preferred) * .25;
    }
    else if (averageMinimumMinutes >= 40) {
        accumulationTarget = prescription.minimum + (prescription.preferred - prescription.minimum) * .70;
    }
    else if (averageMinimumMinutes >= 20) {
        accumulationTarget = prescription.minimum + (prescription.preferred - prescription.minimum) * .35;
    }
    const phaseScale = phase === 'hypertrophy_accumulation' ? 1 : phase === 'mixed_accumulation' ? .74 : 0;
    if (phaseScale <= 0)
        return prescription.minimum;
    return Math.min(prescription.upper, prescription.minimum + (accumulationTarget - prescription.minimum) * phaseScale);
}
export function createStrengthClaims(request, phase = initialPhaseForGoal(request.goal.type)) {
    const out = [];
    const priorities = request.goal.liftPriorities;
    // A phase is a training contract, not just a rep-range skin. Custom/transitioned cycles can move a
    // hypertrophy-origin request into strength accumulation, intensification, or peak even when the
    // original request did not name competition-lift priorities. In those phases, synthesize only the
    // minimum useful strength spine: one required S/B/D exposure, plus optional OHP when frequency allows.
    // Do not infer second/volume exposures here; those belong to explicit athlete strength priorities.
    if (!Object.keys(priorities).length && ['strength_accumulation', 'intensification', 'peak'].includes(phase)) {
        out.push({ id: 'bench_press-phase-heavy', lift: 'bench_press', role: 'heavy', importance: 'A', required: true });
        out.push({ id: 'back_squat-phase-heavy', lift: 'back_squat', role: 'heavy', importance: 'A', required: true });
        out.push({ id: 'deadlift-phase-heavy', lift: 'deadlift', role: 'heavy', importance: 'A', required: true });
        if (request.schedule.days.length >= 4)
            out.push({ id: 'overhead_press-phase-heavy', lift: 'overhead_press', role: 'heavy', importance: 'B', required: false });
        return out;
    }
    for (const [lift, priority] of Object.entries(priorities)) {
        if (priority === 'primary') {
            out.push({ id: `${lift}-heavy`, lift, role: 'heavy', importance: 'A', required: true });
            out.push({ id: `${lift}-volume`, lift, role: 'volume', importance: 'A', required: true });
        }
        else if (priority === 'high') {
            out.push({ id: `${lift}-heavy`, lift, role: 'heavy', importance: 'A', required: true });
            // A second exposure is strongly preferred, but it must not crowd out whole-body minimums
            // when frequency/time make every high-priority lift impossible to train twice.
            out.push({ id: `${lift}-volume`, lift, role: 'volume', importance: 'A', required: false });
        }
        else if (priority === 'normal') {
            out.push({ id: `${lift}-heavy`, lift, role: 'heavy', importance: 'B', required: false });
        }
    }
    return out;
}
export function strengthLiftRegion(lift) {
    return lift === 'back_squat' || lift === 'deadlift' ? 'lower' : 'upper';
}
function canonicalSplitIntents(request) {
    const split = request.preferences.lockedSplit ?? request.preferences.preferredSplit;
    const days = request.schedule.days.length;
    if (split === 'ppl') {
        if (days === 3)
            return ['push', 'pull', 'legs'];
        if (days === 5)
            return ['push', 'pull', 'legs', 'push', 'pull'];
        if (days === 6)
            return ['push', 'pull', 'legs', 'push', 'pull', 'legs'];
    }
    if (split === 'ppla') {
        if (days === 4)
            return ['push', 'pull', 'legs', 'shoulders_arms'];
        if (days === 5)
            return ['push', 'pull', 'legs', 'upper', 'shoulders_arms'];
    }
    if (split === 'ulppl' && days === 5)
        return ['upper', 'lower', 'push', 'pull', 'legs'];
    if (split === 'pplul' && days === 5)
        return ['push', 'pull', 'legs', 'upper', 'lower'];
    if ((split === 'upper_lower' || split === 'phul')) {
        if (days === 2)
            return ['upper', 'lower'];
        if (days === 4)
            return ['upper', 'lower', 'upper', 'lower'];
        if (days === 6)
            return ['upper', 'lower', 'upper', 'lower', 'upper', 'lower'];
    }
    if (split === 'ula' && days === 5)
        return ['upper', 'lower', 'upper', 'lower', 'arms'];
    if (split === 'torso_limbs' && days === 4)
        return ['torso', 'limbs', 'torso', 'limbs'];
    if (split === 'upper_lower_alt' && days === 3)
        return ['upper', 'lower', 'upper'];
    if (split === 'five_three_one' && days === 4)
        return ['press_focus', 'deadlift_focus', 'bench_focus', 'squat_focus'];
    if (['gzclp', 'rippler', 'jt'].includes(split ?? '') && days === 4)
        return ['squat_focus', 'bench_focus', 'deadlift_focus', 'press_focus'];
    if (split === 'five31_beginner' && days === 3)
        return ['squat_focus', 'bench_focus', 'deadlift_focus'];
    if (split === 'texas' && days === 3)
        return ['strength_full', 'strength_full', 'strength_full'];
    if (split === 'strength_fb' && days === 3)
        return ['strength_full', 'strength_full', 'strength_full'];
    if (split === 'academy_prep') {
        if (days === 3)
            return ['strength_full', 'strength_full', 'strength_full'];
        if (days === 4)
            return ['strength_full', 'strength_full', 'strength_full', 'full'];
    }
    if (split === 'sbd_power') {
        if (days === 3)
            return ['squat_focus', 'bench_focus', 'deadlift_focus'];
        if (days === 4)
            return ['squat_focus', 'bench_focus', 'deadlift_focus', 'upper'];
    }
    return null;
}
function liftCompatibleIntent(lift, intent) {
    if (lift === 'bench_press')
        return ['bench_focus', 'upper', 'push', 'chest', 'chest_back', 'shoulders', 'strength_full', 'full'].includes(intent);
    if (lift === 'overhead_press')
        return ['press_focus', 'shoulders', 'upper', 'push', 'shoulders_arms', 'strength_full', 'full'].includes(intent);
    if (lift === 'back_squat')
        return ['squat_focus', 'lower', 'legs', 'glute', 'limbs', 'strength_full', 'full'].includes(intent);
    return ['deadlift_focus', 'lower', 'legs', 'glute', 'pull', 'strength_full', 'full'].includes(intent);
}
function canonicalStrengthMinuteCapacity(maxMinutes) {
    if (maxMinutes <= 35)
        return Math.max(15, maxMinutes - 8);
    if (maxMinutes <= 45)
        return maxMinutes - 8;
    if (maxMinutes <= 60)
        return maxMinutes - 15;
    if (maxMinutes <= 75)
        return maxMinutes - 20;
    return maxMinutes - 25;
}
function claimsFitCanonicalSessions(request, claims, avoidLowerStacking = false) {
    const intents = canonicalSplitIntents(request);
    if (!intents || intents.length !== request.schedule.days.length)
        return true;
    const sessions = intents.map((intent, index) => ({ intent, remaining: canonicalStrengthMinuteCapacity(request.schedule.days[index].maxMinutes), lifts: new Set(), regions: new Set() }));
    const ordered = [...claims].sort((a, b) => {
        const aOptions = intents.filter(intent => liftCompatibleIntent(a.lift, intent)).length;
        const bOptions = intents.filter(intent => liftCompatibleIntent(b.lift, intent)).length;
        return aOptions - bOptions || (a.role === 'heavy' ? 0 : 1) - (b.role === 'heavy' ? 0 : 1) || a.id.localeCompare(b.id);
    });
    const place = (index) => {
        if (index >= ordered.length)
            return true;
        const claim = ordered[index];
        const cost = claim.role === 'heavy' ? 20 : 15;
        const candidates = sessions.map((session, i) => ({ session, i }))
            .filter(({ session }) => liftCompatibleIntent(claim.lift, session.intent) && !session.lifts.has(claim.lift) && session.remaining >= cost && !(avoidLowerStacking && strengthLiftRegion(claim.lift) === 'lower' && session.regions.has('lower')))
            .sort((a, b) => b.session.remaining - a.session.remaining || a.i - b.i);
        for (const { session } of candidates) {
            session.remaining -= cost;
            session.lifts.add(claim.lift);
            const region = strengthLiftRegion(claim.lift);
            session.regions.add(region);
            if (place(index + 1))
                return true;
            session.lifts.delete(claim.lift);
            session.remaining += cost;
            if (![...session.lifts].some(lift => strengthLiftRegion(lift) === region))
                session.regions.delete(region);
        }
        return false;
    };
    return place(0);
}
export function selectStrengthClaimsForCapacity(request, claims) {
    const totalCapacity = Math.max(1, Math.floor(request.schedule.days.reduce((sum, day) => {
        if (request.goal.type === 'mixed')
            return sum + (day.maxMinutes >= 90 ? 2.5 : day.maxMinutes >= 60 ? 1.5 : 1);
        return sum + (day.maxMinutes >= 90 ? 3 : day.maxMinutes >= 45 ? 2 : 1);
    }, 0)));
    const regionCapacity = Math.max(2, Math.ceil(request.schedule.days.length * (request.goal.type === 'mixed' ? .65 : .75)));
    const required = claims.filter(claim => claim.required);
    const optional = claims.filter(claim => !claim.required).sort((a, b) => {
        const importance = { A: 0, B: 1 };
        return importance[a.importance] - importance[b.importance]
            || (a.role === 'volume' ? 0 : 1) - (b.role === 'volume' ? 0 : 1)
            || a.id.localeCompare(b.id);
    });
    const selected = [...required];
    const regionCounts = { upper: 0, lower: 0 };
    const liftCounts = {};
    for (const claim of selected) {
        regionCounts[strengthLiftRegion(claim.lift)]++;
        liftCounts[claim.lift] = (liftCounts[claim.lift] ?? 0) + 1;
    }
    for (const claim of optional) {
        if (selected.length >= Math.max(required.length, totalCapacity))
            break;
        const region = strengthLiftRegion(claim.lift);
        // Spare minutes in the week are not automatically spare recovery capacity in the same body region.
        // Optional exposures stop once that region already has enough strength work to require repeated
        // same-session stacking in a normal weekly topology. Required A-priority exposures remain inviolable.
        if (regionCounts[region] >= regionCapacity)
            continue;
        // Split identity is also a capacity constraint. Optional claims must fit a conventional session
        // assignment for the locked/preferred split, including short-session strength-time pressure and a
        // one-exposure-per-lift-per-session rule. This prevents cases such as Back Squat appearing on a PPL
        // Push day merely because the week had spare minutes somewhere. Required primary exposures remain
        // inviolable; only optional work is declined when no clean destination exists.
        const split = request.preferences.lockedSplit ?? request.preferences.preferredSplit;
        const namedStrengthSystem = ['five_three_one', 'five31_beginner', 'gzclp', 'rippler', 'jt', 'sbd_power', 'texas'].includes(split ?? '');
        // Generic strength and mixed/powerbuilding templates should not spend optional volume by stacking
        // a second squat/deadlift strength exposure onto a session that already has another lower-body
        // strength anchor when the canonical split has enough lower slots to keep the required anchors
        // separate. High-priority volume is strongly preferred, not required; preserve the lower-back
        // fatigue budget before adding a fourth lower-region strength exposure. Named strength systems
        // retain their own structure-specific exposure rules.
        const avoidOptionalLowerStacking = (request.goal.type === 'strength' || request.goal.type === 'mixed') && !namedStrengthSystem && region === 'lower' && claim.role === 'volume';
        if (!claimsFitCanonicalSessions(request, [...selected, claim], avoidOptionalLowerStacking))
            continue;
        selected.push(claim);
        regionCounts[region]++;
        liftCounts[claim.lift] = (liftCounts[claim.lift] ?? 0) + 1;
    }
    return selected;
}
