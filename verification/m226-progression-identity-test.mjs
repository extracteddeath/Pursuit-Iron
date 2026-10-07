import assert from 'node:assert/strict';
import { EXERCISES, EX_BY_ID, prescribeSets, sessionSuggestion, loggedWorkoutPerformance } from '../modules/App.js';
import { deriveProgressionSelectionEvidence } from '../modules/next-engine/workout-history-adapter.js';

const ex = EXERCISES.find(item => item && item.id && item.equip?.length && !item.bodyweight);
assert.ok(ex, 'fixture needs a loadable exercise');
const config = { name: 'Occurrence identity', unit: 'lb', goal: 'hypertrophy', experience: 'intermediate',
    split: 'upper_lower', days: 2, session: 's60', weeks: 6, progression: 'double', deload: false,
    equipment: [...new Set(ex.equip)], focus: {}, reduce: [], barbellCap: 2, noBodyweight: false, noSupersets: false };
const dayA = { id: 'day-a', label: 'A', primaryIndex: -1, exercises: [ex.id] };
const dayB = { id: 'day-b', label: 'B', primaryIndex: -1, exercises: [ex.id] };
const program = { id: 'custom-occurrence', custom: true, weeks: 6, config, days: [dayA, dayB], overrides: {
    'day-a:0': { sets: 2, reps: '6-8', rir: '2', progressionStyle: 'double' },
    'day-b:0': { sets: 4, reps: '12-15', rir: '2', progressionStyle: 'double' }
} };

function completedPerf(day, reps, weight = 50) {
    const rows = prescribeSets(program, day, EX_BY_ID[ex.id], 0, 1, 'lb', { weight }, null, {}, [], false)
        .filter(row => !row.warm && !row.sub)
        .map(row => ({ ...row, done: true, reps: String(reps), actualRIR: 2 }));
    return loggedWorkoutPerformance(program, day, [{ id: ex.id, slot: 0, note: '', sets: rows }], 1, 'lb', {}, []);
}

const aPerf = completedPerf(dayA, 8);
const onlyOtherDay = [{ id: 'a1', programId: program.id, dayId: dayA.id, dayLabel: dayA.label,
    weekIndex: 1, date: 1000, unit: 'lb', perf: aPerf }];
const initialB = sessionSuggestion(program, dayB, 0, null, 'lb', 1, onlyOtherDay);
assert.equal(initialB.action, 'initial');
assert.equal(initialB.dir, 'hold');
assert.match(String(initialB.reps), /12.*15/);
assert.equal(initialB.target, 12);
assert.match(initialB.reason, /starting reference/i);

// Once B has its own completed exposure, only B may earn B's progression.
const bPerf = completedPerf(dayB, 15);
const withSameDay = [...onlyOtherDay, { id: 'b1', programId: program.id, dayId: dayB.id, dayLabel: dayB.label,
    weekIndex: 1, date: 2000, unit: 'lb', perf: bPerf }];
const progressedB = sessionSuggestion(program, dayB, 0, null, 'lb', 1, withSameDay);
assert.notEqual(progressedB.action, 'initial');

// Adaptive-method evidence is independently scoped to each program-day occurrence.
const workouts = [
    { dayId: 'day-a', performedSets: [{ exerciseId: ex.id, rir: 2 }],
      progression: [{ exerciseId: ex.id, outcome: 'success', estimated1RM: 100 }] },
    { dayId: 'day-a', performedSets: [{ exerciseId: ex.id, rir: 2 }],
      progression: [{ exerciseId: ex.id, outcome: 'success', estimated1RM: 101 }] },
    { dayId: 'day-b', performedSets: [{ exerciseId: ex.id, rir: null }],
      progression: [{ exerciseId: ex.id, outcome: 'failure', estimated1RM: 90 }] }
];
const global = deriveProgressionSelectionEvidence(workouts);
const scoped = deriveProgressionSelectionEvidence(workouts, 'session_exercise');
assert.equal(global[ex.id].comparableExposures, 3);
assert.equal(scoped[`day-a::${ex.id}`].comparableExposures, 2);
assert.equal(scoped[`day-a::${ex.id}`].failureCount, 0);
assert.equal(scoped[`day-b::${ex.id}`].comparableExposures, 1);
assert.equal(scoped[`day-b::${ex.id}`].failureCount, 1);
assert.equal(scoped[`day-b::${ex.id}`].rirCoverage, 0);

console.log('PASS M226: repeated-exercise progression evidence is isolated by program day; cross-day history is reference-only.');
