import { createExerciseMap } from './exercise-db.js';
import { estimate1RM } from './history.js';
import { historyNumber, progressionExposureContext } from './history-contract.js';
import { recoverySignalForDecision } from './recovery.js';

const DAY = 86400000;
const limited = new Set(['unobserved', 'non_comparable', 'context_limited', 'interrupted', 'incomplete']);
const comparableOutcomes = new Set(['success', 'productive', 'failure', 'success_blocked']);
const round = value => Math.round(value * 10000) / 10000;
const clamp = (value, lo, hi) => Math.max(lo, Math.min(hi, value));
const timestamp = workout => Date.parse(workout?.completedAt ?? '');
const confidence = (count, effortCoverage, snapshots) => count >= 6 && effortCoverage >= .5 && snapshots >= 3
    ? 'high' : count >= 3 && snapshots >= 2 ? 'moderate' : 'low';

/** Reproducible descriptive memory. Only exact program/day owners can earn adaptive evidence.
 * Completed dose is counted even when progression evidence is limited; those are separate facts.
 * Modeled workload is an accounting estimate, not a measured physiological fatigue value. */
export function deriveAthleteResponse(workoutsInput = [], options = {}) {
    const programId = options.programId;
    if (typeof programId !== 'string' || !programId) throw new TypeError('Athlete response needs a program owner.');
    const allowedDays = new Set(options.dayIds ?? []);
    const excluded = {}, identities = new Map();
    const exclude = reason => { excluded[reason] = (excluded[reason] ?? 0) + 1; };
    for (const workout of Array.isArray(workoutsInput) ? workoutsInput : []) {
        if (workout?.programId !== programId) { exclude('different_program'); continue; }
        if (!workout.dayId || (allowedDays.size && !allowedDays.has(workout.dayId))) { exclude('unowned_day'); continue; }
        if (!Number.isFinite(timestamp(workout))) { exclude('invalid_date'); continue; }
        if (!Array.isArray(workout.session?.exercises) || !Array.isArray(workout.performedSets)) { exclude('invalid_workout'); continue; }
        const key = workout.historyId ?? `${workout.completedAt}:${workout.dayId}`;
        if (identities.has(key)) exclude('duplicate_identity');
        const prior = identities.get(key);
        if (!prior || timestamp(workout) > timestamp(prior)) identities.set(key, workout);
    }
    let workouts = [...identities.values()].sort((a,b) => timestamp(a)-timestamp(b)
        || String(a.historyId ?? '').localeCompare(String(b.historyId ?? '')));
    const lastDate = workouts.length ? timestamp(workouts.at(-1)) : null;
    workouts = workouts.filter(w => timestamp(w) >= lastDate - 28 * DAY).slice(-28);
    const exerciseMap = createExerciseMap(options.customExercises);
    const slots = new Map(), muscles = {}, sessionsByDay = {};
    let observedEffort = 0, performedCount = 0, comparableCount = 0, comparableSnapshots = 0, negative = 0, positive = 0;
    let modeledWorkload = 0;
    const negativeWorkouts = new Set(), negativeDays = new Set();
    for (const workout of workouts) {
        sessionsByDay[workout.dayId] = (sessionsByDay[workout.dayId] ?? 0) + 1;
        for (const exercise of workout.session.exercises) {
            if (!exercise?.exerciseId || !Number.isInteger(exercise.sets) || exercise.sets < 1) { exclude('invalid_prescription'); continue; }
            const completed = workout.performedSets.filter(s => s.exerciseId === exercise.exerciseId
                && s.done !== false && !s.warm && !s.sub && historyNumber(s.reps) > 0);
            if (!completed.length) continue;
            const def = exerciseMap.get(exercise.exerciseId);
            for (const [muscle, credit] of Object.entries(def?.muscles ?? {}))
                muscles[muscle] = (muscles[muscle] ?? 0) + credit.credit * completed.length;
            modeledWorkload += completed.length * ((def?.fatigue?.systemic ?? 0) + (def?.fatigue?.axial ?? 0) + (def?.fatigue?.lowerBack ?? 0));
            const reported = completed.filter(s => historyNumber(s.rir) !== null && s.rirReported !== false).length;
            performedCount += completed.length; observedEffort += reported;
            const context = progressionExposureContext(completed, workout);
            const decision = workout.progression?.find(d => d.exerciseId === exercise.exerciseId);
            if (!decision || limited.has(decision.outcome) || !comparableOutcomes.has(decision.outcome)
                || context.nonComparable || context.interrupted || context.badDay) {
                exclude('limited_exposure'); continue;
            }
            // A fallback to today's targets describes old work, but cannot justify personalized dose changes.
            const snapshot = exercise.prescriptionSource === 'logged_snapshot';
            comparableCount++; comparableSnapshots += Number(snapshot);
            const signal = recoverySignalForDecision(decision);
            if (signal < 0) { negative++; if (snapshot) { negativeWorkouts.add(workout.historyId); negativeDays.add(workout.dayId); } }
            if (signal > 0) positive++;
            const key = `${workout.dayId}:${exercise.exerciseId}`;
            const row = slots.get(key) ?? { dayId: workout.dayId, exerciseId: exercise.exerciseId,
                exposures: 0, successes: 0, failures: 0, completedSets: 0, prescribedSets: 0, rirReportedSets: 0, snapshots: 0, samples: [] };
            row.exposures++; row.successes += Number(signal > 0); row.failures += Number(signal < 0);
            row.completedSets += completed.length; row.prescribedSets += exercise.sets; row.rirReportedSets += reported; row.snapshots += Number(snapshot);
            // Rep-only changes and load changes remain distinct; estimated strength is only a descriptive trend.
            const estimates = completed.map(s => estimate1RM(historyNumber(s.load), historyNumber(s.reps), s.rirReported === false ? null : historyNumber(s.rir)))
                .filter(n => Number.isFinite(n) && n > 0);
            row.samples.push({ at: timestamp(workout), strength: estimates.length ? Math.max(...estimates) : null,
                reps: completed.reduce((n,s) => n + Number(s.reps),0) / completed.length });
            slots.set(key,row);
        }
    }
    const windowDays = workouts.length ? Math.max(7, (lastDate - timestamp(workouts[0])) / DAY + 1) : 7;
    const bySlot = Object.fromEntries([...slots].sort(([a],[b]) => a.localeCompare(b)).map(([key,row]) => {
        const first = row.samples[0], last = row.samples.at(-1), weeks = Math.max(1,(last.at-first.at)/DAY/7);
        const velocity = row.exposures >= 3 && first.strength > 0 && last.strength > 0
            ? clamp((last.strength-first.strength)/first.strength/weeks,-.2,.2) : null;
        const effortCoverage = row.rirReportedSets / Math.max(1,row.completedSets);
        return [key,{ dayId:row.dayId, exerciseId:row.exerciseId, comparableExposures:row.exposures,
            completedSetRatio:round(Math.min(1,row.completedSets/Math.max(1,row.prescribedSets))),
            successRate:round(row.successes/row.exposures), failureCount:row.failures,
            exposuresPerWeek:round(row.exposures/windowDays*7), rirCoverage:round(effortCoverage),
            progressionVelocityPerWeek:velocity === null ? null : round(velocity),
            meanReps:round(last.reps), confidence:confidence(row.exposures,effortCoverage,row.snapshots) }];
    }));
    const effortCoverage = observedEffort / Math.max(1,performedCount);
    const certainty = confidence(comparableCount,effortCoverage,comparableSnapshots);
    const previous = Number.isFinite(options.previousCapacityScale) ? clamp(options.previousCapacityScale,.6,1) : 1;
    let capacityScale = previous, action = 'maintain';
    const repeatedFatigue = certainty !== 'low' && negativeWorkouts.size >= 3 && negativeDays.size >= 2
        && negative >= Math.max(3,comparableCount * .4) && positive === 0;
    if (repeatedFatigue) { capacityScale = Math.max(.6, previous - .05); action = 'reduce_capacity'; }
    else if (certainty === 'high' && positive >= 3 && negative === 0 && options.recovery?.status === 'normal') {
        capacityScale = Math.min(1, previous + .05); action = capacityScale > previous ? 'restore_capacity' : 'maintain';
    }
    return { schemaVersion:1, programId, windowDays:round(windowDays), workouts:workouts.length,
        achievedDoseByMuscle:Object.fromEntries(Object.entries(muscles).sort(([a],[b])=>a.localeCompare(b)).map(([m,n])=>[m,round(n)])),
        sessionsByDay, comparableExposures:comparableCount, loggedPrescriptionExposures:comparableSnapshots,
        observedRirCoverage:round(effortCoverage), modeledWorkload:round(modeledWorkload), bySlot,
        recovery:options.recovery?.status ?? 'unknown', confidence:certainty,
        capacityScale:round(capacityScale), action, excluded };
}
