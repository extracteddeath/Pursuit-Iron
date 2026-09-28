import { createExerciseMap } from './exercise-db.js';
import { PUBLIC_MEV_REGIONS, directlyTargetsPublicRegion, publicRegionContribution } from './public-mev.js';
const DAY_ORDER = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
const STRENGTH_ROLES = new Set(['primary_strength', 'secondary_strength', 'strength_support']);
function dayIndex(day) { return DAY_ORDER.indexOf(day); }
function circularGap(a, b) {
    const x = dayIndex(a), y = dayIndex(b);
    const forward = (y - x + 7) % 7, backward = (x - y + 7) % 7;
    return Math.min(forward || 7, backward || 7);
}
function profileSession(session, defs) {
    const region = {}, direct = {};
    const joints = { shoulder: 0, elbow: 0, knee: 0, lowerBack: 0, axial: 0 };
    let systemic = 0;
    for (const exercise of session.exercises) {
        const def = defs.get(exercise.exerciseId);
        if (!def)
            continue;
        for (const publicRegion of PUBLIC_MEV_REGIONS) {
            const credit = publicRegionContribution(def, publicRegion);
            if (credit > 0)
                region[publicRegion] = (region[publicRegion] ?? 0) + exercise.sets * credit;
            if (directlyTargetsPublicRegion(def, publicRegion))
                direct[publicRegion] = (direct[publicRegion] ?? 0) + exercise.sets;
        }
        // Recovery placement uses movement-level fatigue rather than pretending every set adds a full
        // copy of the exercise fatigue vector. Strength anchors matter most; compounds matter more than
        // isolation/accessory slots. The score is comparative within one already-valid weekly program.
        const factor = STRENGTH_ROLES.has(exercise.role) ? 1.2 : def.flags.compound ? 1 : .6;
        systemic += def.fatigue.systemic * factor;
        joints.shoulder += def.fatigue.shoulder * factor;
        joints.elbow += def.fatigue.elbow * factor;
        joints.knee += def.fatigue.knee * factor;
        joints.lowerBack += def.fatigue.lowerBack * factor;
        joints.axial += def.fatigue.axial * factor;
    }
    return { region, direct, joints, systemic };
}
function pairAssessment(a, b, experience) {
    let score = 0, hard = 0;
    const directFloor = experience === 'novice' ? 3 : experience === 'advanced' ? 4.5 : 4;
    const regionFloor = experience === 'novice' ? 3.5 : experience === 'advanced' ? 4.5 : 4;
    for (const region of PUBLIC_MEV_REGIONS) {
        const x = a.region[region] ?? 0, y = b.region[region] ?? 0, dx = a.direct[region] ?? 0, dy = b.direct[region] ?? 0;
        const overlap = Math.min(x, y), directOverlap = Math.min(dx, dy);
        // Consecutive exposure itself is legal. Cost begins only when both sessions are substantial;
        // this lets high-frequency/full-body plans use small repeated doses without being mislabeled.
        if (overlap > 2.5)
            score += (overlap - 2.5) * 1.25;
        if (directOverlap > 2)
            score += (directOverlap - 2) * 1.75;
        const hardRegion = (x >= regionFloor && y >= regionFloor && (dx >= 2 || dy >= 2)) || (dx >= directFloor && dy >= 3) || (dx >= 3 && dy >= directFloor);
        if (hardRegion) {
            hard++;
            score += 10 + overlap;
        }
    }
    const systemicFloor = experience === 'novice' ? 12 : experience === 'advanced' ? 16 : 14;
    if (a.systemic >= systemicFloor && b.systemic >= systemicFloor) {
        hard++;
        score += 6 + (Math.min(a.systemic, b.systemic) - systemicFloor) * .2;
    }
    const jointFloors = experience === 'novice'
        ? { shoulder: 6, elbow: 5, knee: 6, lowerBack: 6, axial: 7 }
        : experience === 'advanced' ? { shoulder: 8, elbow: 7, knee: 8, lowerBack: 8, axial: 9 }
            : { shoulder: 7, elbow: 6, knee: 7, lowerBack: 7, axial: 8 };
    for (const joint of Object.keys(jointFloors)) {
        const floor = jointFloors[joint];
        if (a.joints[joint] >= floor && b.joints[joint] >= floor) {
            hard++;
            score += 5 + (Math.min(a.joints[joint], b.joints[joint]) - floor) * .25;
        }
    }
    return { score, hard };
}
function pairCollisionReasons(a, b, experience) {
    const reasons = [];
    const directFloor = experience === 'novice' ? 3 : experience === 'advanced' ? 4.5 : 4;
    const regionFloor = experience === 'novice' ? 3.5 : experience === 'advanced' ? 4.5 : 4;
    for (const region of PUBLIC_MEV_REGIONS) {
        const x = a.region[region] ?? 0, y = b.region[region] ?? 0, dx = a.direct[region] ?? 0, dy = b.direct[region] ?? 0;
        const hardRegion = (x >= regionFloor && y >= regionFloor && (dx >= 2 || dy >= 2)) || (dx >= directFloor && dy >= 3) || (dx >= 3 && dy >= directFloor);
        if (hardRegion)
            reasons.push({ type: 'region', key: region });
    }
    const systemicFloor = experience === 'novice' ? 12 : experience === 'advanced' ? 16 : 14;
    if (a.systemic >= systemicFloor && b.systemic >= systemicFloor)
        reasons.push({ type: 'systemic', key: 'systemic' });
    const jointFloors = experience === 'novice'
        ? { shoulder: 6, elbow: 5, knee: 6, lowerBack: 6, axial: 7 }
        : experience === 'advanced' ? { shoulder: 8, elbow: 7, knee: 8, lowerBack: 8, axial: 9 }
            : { shoulder: 7, elbow: 6, knee: 7, lowerBack: 7, axial: 8 };
    for (const joint of Object.keys(jointFloors)) {
        const floor = jointFloors[joint];
        if (a.joints[joint] >= floor && b.joints[joint] >= floor)
            reasons.push({ type: 'joint', key: joint });
    }
    return reasons;
}
/** Hard consecutive-day collisions in the program's current weekday assignment. */
export function weeklyRecoveryCollisions(sessions, request) {
    if (sessions.length < 2)
        return [];
    const defs = createExerciseMap(request.customExercises), profiles = sessions.map(session => profileSession(session, defs));
    const out = [];
    for (let i = 0; i < sessions.length; i++)
        for (let j = i + 1; j < sessions.length; j++) {
            if (circularGap(sessions[i].day, sessions[j].day) !== 1)
                continue;
            const reasons = pairCollisionReasons(profiles[i], profiles[j], request.athlete.experience);
            if (reasons.length)
                out.push({ aIndex: i, bIndex: j, reasons });
        }
    return out;
}
function equipmentEligibleOnDay(def, day, request) {
    if ((def.flags.bodyweight || def.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
        return false;
    const available = day.equipmentOverride ?? request.equipment.available;
    const setups = [def.equipment, ...(def.equipmentAlternatives ?? [])];
    return setups.some(setup => setup.every(item => item === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : available.includes(item)));
}
function sessionFitsDay(session, day, request, defs) {
    if (session.estimatedMinutes > day.maxMinutes)
        return false;
    const maxBarbell = day.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay;
    let barbell = 0;
    for (const exercise of session.exercises) {
        const def = defs.get(exercise.exerciseId);
        if (!def)
            return false;
        if (!equipmentEligibleOnDay(def, day, request))
            return false;
        if (def.flags.barbell)
            barbell++;
    }
    return barbell <= maxBarbell;
}
function assignmentFeasible(assignment, sessions, days, request, defs) {
    for (let dayIndex = 0; dayIndex < assignment.length; dayIndex++) {
        const contract = request.schedule.days.find(day => day.day === days[dayIndex]);
        if (!contract || !sessionFitsDay(sessions[assignment[dayIndex]], contract, request, defs))
            return false;
    }
    return true;
}
function assessmentForAssignment(assignment, days, profiles, request) {
    let score = 0, hardCollisions = 0, adjacentPairs = 0;
    for (let i = 0; i < assignment.length; i++)
        for (let j = i + 1; j < assignment.length; j++) {
            if (circularGap(days[i], days[j]) !== 1)
                continue;
            adjacentPairs++;
            const pair = pairAssessment(profiles[assignment[i]], profiles[assignment[j]], request.athlete.experience);
            score += pair.score;
            hardCollisions += pair.hard;
        }
    return { score: +score.toFixed(4), hardCollisions, adjacentPairs };
}
function eachPermutation(values, visit, prefix = []) {
    if (values.length === 0) {
        visit(prefix);
        return;
    }
    for (let i = 0; i < values.length; i++)
        eachPermutation([...values.slice(0, i), ...values.slice(i + 1)], visit, [...prefix, values[i]]);
}
function movementCost(assignment) { return assignment.reduce((sum, sessionIndex, dayIndex) => sum + Math.abs(sessionIndex - dayIndex), 0); }
/** Scores the current realized session-to-day assignment. Lower is better. */
export function assessWeeklyRecovery(sessions, request) {
    if (sessions.length < 2)
        return { score: 0, hardCollisions: 0, adjacentPairs: 0 };
    const defs = createExerciseMap(request.customExercises), profiles = sessions.map(session => profileSession(session, defs));
    const days = sessions.map(session => session.day);
    return assessmentForAssignment(sessions.map((_, i) => i), days, profiles, request);
}
/**
 * Assigns already-realized workouts to the athlete's selected weekdays using the actual exercise
 * stress of those workouts. Weekly dose, exercise choice, prescriptions, session identity and the
 * available weekdays are unchanged; only which valid weekday receives which workout may change.
 */
export function optimizeWeeklyRecovery(sessions, request) {
    if (sessions.length < 2)
        return { score: 0, hardCollisions: 0, adjacentPairs: 0, beforeScore: 0, beforeHardCollisions: 0, changed: false, sessions: sessions.map(s => ({ ...s, exercises: s.exercises.map(e => ({ ...e })) })) };
    const defs = createExerciseMap(request.customExercises), profiles = sessions.map(session => profileSession(session, defs));
    const days = sessions.map(session => session.day), identity = sessions.map((_, i) => i);
    const before = assessmentForAssignment(identity, days, profiles, request);
    let bestAssignment = identity, best = before, bestMovement = 0;
    eachPermutation(identity, candidate => {
        if (!assignmentFeasible(candidate, sessions, days, request, defs))
            return;
        const current = assessmentForAssignment(candidate, days, profiles, request), move = movementCost(candidate);
        if (current.score < best.score - .0001 ||
            (Math.abs(current.score - best.score) <= .0001 && current.hardCollisions < best.hardCollisions) ||
            (Math.abs(current.score - best.score) <= .0001 && current.hardCollisions === best.hardCollisions && move < bestMovement)) {
            best = current;
            bestAssignment = [...candidate];
            bestMovement = move;
        }
    });
    if (bestAssignment.every((sessionIndex, dayIndex) => sessionIndex === dayIndex))
        return { ...best, beforeScore: before.score, beforeHardCollisions: before.hardCollisions, changed: false, sessions: sessions.map(s => ({ ...s, exercises: s.exercises.map(e => ({ ...e })) })) };
    const assignedDayBySession = new Map();
    bestAssignment.forEach((sessionIndex, dayIndex) => assignedDayBySession.set(sessionIndex, days[dayIndex]));
    const optimized = sessions.map((session, index) => {
        const day = assignedDayBySession.get(index) ?? session.day;
        const contract = request.schedule.days.find(item => item.day === day);
        return { ...session, day, minMinutes: contract?.minMinutes, maxMinutes: contract?.maxMinutes ?? session.maxMinutes, targetExercises: contract?.targetExercises, exercises: session.exercises.map(e => ({ ...e })) };
    });
    return { ...best, beforeScore: before.score, beforeHardCollisions: before.hardCollisions, changed: true, sessions: optimized };
}
export function minimumWeeklyRecoveryScore(sessions, request) {
    const defs = createExerciseMap(request.customExercises), profiles = sessions.map(session => profileSession(session, defs)), days = sessions.map(session => session.day), identity = sessions.map((_, i) => i);
    let best = assessmentForAssignment(identity, days, profiles, request);
    eachPermutation(identity, candidate => { if (!assignmentFeasible(candidate, sessions, days, request, defs))
        return; const current = assessmentForAssignment(candidate, days, profiles, request); if (current.score < best.score - .0001 || (Math.abs(current.score - best.score) <= .0001 && current.hardCollisions < best.hardCollisions))
        best = current; });
    return best;
}
