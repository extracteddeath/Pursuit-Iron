import assert from 'node:assert/strict';
import { historyNumber, normalizeHistoryEntries, normalizeHistoryRevisions, progressionExposureContext, resolveHistoryDayIndex } from '../modules/next-engine/history-contract.js';
import { deriveTieredLinearState } from '../modules/next-engine/percentage-protocols.js';
import { evaluateWorkoutProgression } from '../modules/next-engine/performance.js';
import { EXERCISES, EX_BY_ID, prescribeSets, sessionSuggestion, loggedWorkoutPerformance } from '../modules/App.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine } from '../modules/next-engine/workout-history-adapter.js';
import { progressionHistoryForProgram, customExerciseHistory, customExerciseReferenceHistory, muscleRecoveryUncached } from '../modules/training-domain/prescriptions.js';
import { buildLifterModel, deloadAdvice, exerciseTrends } from '../modules/training-domain/analytics.js';

for (const value of [true, false, [], [15], {}, { valueOf: () => 15 }, Symbol('load')])
    assert.equal(historyNumber(value), null);
for (const value of [0, 15, '15', ' 15 ']) assert.equal(historyNumber(value), Number(value));
const old = { id: 'same', programId: 'p', date: 1, updatedAt: 'invalid', perf: {} };
const edited = { ...old, date: 2, updatedAt: NaN, perf: { lift: { sets: [] } } };
for (const entries of [[old, edited], [edited, old]])
    assert.equal(normalizeHistoryEntries(entries, 'p').entries[0], edited);
assert.equal(normalizeHistoryEntries([{ ...old, updatedAt: 3 }, edited], 'p').entries[0].date, 1);

const duplicateDays = [
    { id: 'a', label: 'Repeat', exercises: ['lift'] },
    { id: 'b', label: 'Repeat', exercises: ['lift'] }
];
assert.equal(resolveHistoryDayIndex(duplicateDays, { dayId: 'b', dayLabel: 'Repeat', perf: { lift: {} } }), 1,
    'an exact persisted day id always wins');
assert.equal(resolveHistoryDayIndex(duplicateDays, { dayId: 'retired', dayLabel: 'Repeat', perf: { lift: {} } }), -1,
    'a stale id plus duplicate label must not guess between identical day occurrences');
const distinguishableDays = [
    { id: 'a', label: 'Repeat', exercises: ['lift', 'row'] },
    { id: 'b', label: 'Repeat', exercises: ['press'] }
];
assert.equal(resolveHistoryDayIndex(distinguishableDays, { dayId: 'retired', dayLabel: 'Repeat', perf: { row: {} } }), 0,
    'legacy label fallback may reattach when the logged roster uniquely identifies one duplicate-label day');
assert.equal(resolveHistoryDayIndex(distinguishableDays, { perf: { press: {} } }), 1,
    'pre-label legacy history may reattach only when its complete logged roster identifies exactly one day');
assert.equal(resolveHistoryDayIndex(duplicateDays, { perf: { lift: {} } }), -1,
    'pre-label history with a movement repeated across days remains unresolved');

const revisionDay = { id: 'revision-day', label: 'Revision', exercises: ['lift'] };
const revisionProgram = { id: 'revision-program', days: [revisionDay] };
const staleRevision = { id: 'revision-workout', programId: revisionProgram.id, dayId: revisionDay.id,
    date: 1000, updatedAt: 1100, unit: 'lb', perf: { lift: { weight: 100, reps: 10, sets: [{ w: 100, r: 10 }] } } };
const editedRevision = { ...staleRevision, updatedAt: 1200,
    perf: { lift: { weight: 80, reps: 8, sets: [{ w: 80, r: 8 }] } } };
for (const revisions of [[staleRevision, editedRevision], [editedRevision, staleRevision]]) {
    const scoped = progressionHistoryForProgram(revisionProgram, revisions);
    assert.equal(scoped.length, 1, 'adaptive progression must count one persisted workout identity once');
    assert.equal(scoped[0].perf.lift.weight, 80, 'adaptive progression must use the newest workout revision');
    assert.equal(customExerciseHistory({ ...revisionProgram, custom: true }, revisionDay, 'lift', revisions)?.perf?.lift?.weight, 80,
        'custom comparable history must use the newest same-program revision');
    assert.equal(customExerciseReferenceHistory({ ...revisionProgram, custom: true }, 'lift', revisions)?.perf?.lift?.weight, 80,
        'custom starting reference must prefer the newest canonical same-program revision');
}

const crossProgramRevision = { ...editedRevision, programId: 'other-program' };
assert.equal(normalizeHistoryRevisions([staleRevision, editedRevision, crossProgramRevision]).entries.length, 2,
    'global revision normalization must keep identical row ids from different programs independent');

const recoveryExercise = EXERCISES.find(ex => ex?.id && ['chest','lats','upper_back','shoulders','biceps','triceps','quads','hamstrings','glutes','lower_back','adductors','abductors','calves','abs','traps','forearms','neck'].includes(ex.part));
assert.ok(recoveryExercise, 'recovery revision regression needs one catalog exercise with a canonical muscle part');
const fixedNow = 10_000_000;
const recoveryDate = fixedNow - 3_600_000;
const recoveryBase = { id: 'recovery-revision', programId: 'recovery-program', dayId: 'recovery-day',
    date: recoveryDate, unit: 'lb' };
const recoveryStale = { ...recoveryBase, updatedAt: recoveryDate + 1,
    perf: { [recoveryExercise.id]: { sets: [{ w: 50, r: 10, rir: 4, tr: 2 }] } } };
const recoveryEdited = { ...recoveryBase, updatedAt: recoveryDate + 2,
    perf: { [recoveryExercise.id]: { sets: Array.from({ length: 8 }, () => ({ w: 50, r: 10, rir: 0, tr: 2 })) } } };
const originalNow = Date.now;
try {
    Date.now = () => fixedNow;
    const forward = muscleRecoveryUncached([recoveryStale, recoveryEdited]);
    const reverse = muscleRecoveryUncached([recoveryEdited, recoveryStale]);
    assert.deepEqual(forward, reverse,
        'recovery/readiness must be independent of stale-vs-edited revision array order');
    const row = forward.find(item => item.part === recoveryExercise.part);
    assert.ok(row && row.sets > 6,
        'recovery/readiness must reflect the newest hard edited exposure, not the stale easy revision');
} finally {
    Date.now = originalNow;
}

const decisionExercises = EXERCISES.filter(ex => ex?.id && EX_BY_ID[ex.id]).slice(0, 2);
assert.equal(decisionExercises.length, 2, 'decision revision regression needs two catalog exercises');
const decisionHistory = [];
for (let i = 2; i >= 0; i--) {
    const date = 2_000_000 + i * 86_400_000;
    const currentReps = 8 + (2 - i) * 2;
    const perf = Object.fromEntries(decisionExercises.map(ex => [ex.id, {
        weight: 50, reps: currentReps, sets: [{ w: 50, r: currentReps, rir: 1, tr: 2 }]
    }]));
    const current = { id: `decision-${i}`, programId: 'decision-program', dayId: 'decision-day',
        date, updatedAt: date + 2, unit: 'lb', perf };
    const stalePerf = Object.fromEntries(decisionExercises.map(ex => [ex.id, {
        weight: 50, reps: currentReps + 1, sets: [{ w: 50, r: currentReps + 1, rir: 1, tr: 2 }]
    }]));
    const stale = { ...current, updatedAt: date + 1, perf: stalePerf };
    decisionHistory.push(current, stale);
}
const decisionTrends = exerciseTrends(decisionHistory);
for (const ex of decisionExercises) {
    const trend = decisionTrends.find(row => row.id === ex.id);
    assert.equal(trend?.sessionsCount, 3,
        'decision trends must count three persisted workouts, not six stale/current revisions');
}
const decisionModel = buildLifterModel(decisionHistory, null, null);
for (const ex of decisionExercises)
    assert.equal(decisionModel.lifts?.[ex.id]?.sessions, 3,
        'lifter model plateau evidence must count each persisted workout identity once');
assert.equal(deloadAdvice(decisionHistory), null,
    'three actual sessions with edited revisions must not satisfy the four-session deload evidence floor');

const exercise = { exerciseId: 'lift', name: 'Lift', sets: 2, progressionStyle: 'double',
    prescription: { reps: [10, 15], rir: [2, 2] } };
const performed = () => Array.from({ length: 2 }, (_, setIndex) => ({ exerciseId: 'lift', setIndex, load: 50, reps: 15, rir: 2 }));
const gzEntry = () => ({ id: 'gz', programId: 'p', date: 1, unit: 'lb', perf: { lift: {
    sets: Array.from({ length: 2 }, () => ({ w: 50, r: 3 })), prescription: { sets: 2,
        protocol: { scheme: 'gzclp', tier: 't1', stage: 0 }, setTargets: [{ weight: 50, reps: 3 }, { weight: 50, reps: 3 }] }
} } });
const replay = entries => deriveTieredLinearState({ programId: 'p', exerciseId: 'lift', tier: 't1', initialLoad: 50,
    unit: 'lb', entries, nextLoad: w => w + 5, resetLoad: w => w * .85 });
assert.deepEqual(replay([gzEntry()]), { stage: 0, weight: 55 });
const flags = [
    [{ readinessDisrupted: true }, 'context_limited'], [{ recoveryLimited: true }, 'context_limited'],
    [{ readiness: 'recover' }, 'context_limited'], [{ readinessStatus: ' LOW ' }, 'context_limited'],
    [{ readiness: { status: 'Very Low' } }, 'context_limited'], [{ workoutInterrupted: true }, 'interrupted'],
    [{ manualPrescriptionOverride: true }, 'non_comparable'], [{ exerciseSubstituted: true }, 'non_comparable'],
    [{ wasEdited: true }, 'non_comparable']
];
let checks = 0;
for (const [flag, outcome] of flags) for (const scope of ['session', 'exercise', 'set']) {
    const entry = gzEntry();
    Object.assign(scope === 'session' ? entry : scope === 'exercise' ? entry.perf.lift : entry.perf.lift.sets[0], flag);
    const before = JSON.stringify(entry);
    assert.deepEqual(replay([entry]), { stage: 0, weight: 50 });
    assert.equal(JSON.stringify(entry), before);
    const actual = performed(), context = scope === 'session' ? flag : {};
    if (outcome === 'interrupted') actual.pop();
    if (scope !== 'session') Object.assign(actual[0], flag);
    assert.equal(evaluateWorkoutProgression({ exercises: [exercise] }, actual, context)[0].outcome, outcome);
    checks++;
}
assert.equal(progressionExposureContext([{ recoveryLimited: false }], { recoveryLimited: true }).badDay, true);

// Prove provenance survives the same serializer and history adapters used by Workout.
const equipment = ['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
const config = { name: 'Provenance', unit: 'lb', goal: 'both', experience: 'intermediate', split: 'upper_lower',
    days: 4, session: 's60', weeks: 6, progression: 'double', deload: false, equipment, focus: {}, reduce: [],
    barbellCap: 3, noBodyweight: false, noSupersets: false };
const generated = generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 225, makeId: () => 'generated' }).program;
const day = generated.days[0], slot = 0, id = day.exercises[slot], ex = EX_BY_ID[id];
for (const cell of Object.values(generated.nextWeekPrescriptions[`${day.id}:${slot}`]))
    Object.assign(cell, { sets: 2, reps: '10-15', rir: '2', progressionStyle: 'double' });
const custom = { id: 'custom', custom: true, weeks: 6, config, days: [{ id: 'custom-day', label: 'Push', primaryIndex: -1, exercises: [id] }],
    overrides: { 'custom-day:0': { sets: 2, reps: '10-15', rir: '2', progressionStyle: 'double' } } };
for (const program of [generated, custom]) for (const [flag, outcome] of flags) for (const scope of ['exercise', 'set']) {
    const d = program.days[0];
    const sets = prescribeSets(program, d, ex, 0, 1, 'lb', { weight: 50 }, null, {}, [], false)
        .filter(r => !r.warm && !r.sub).map(r => ({ ...r, done: true, reps: '15', actualRIR: 2 }));
    const data = { id, slot: 0, note: '', sets };
    if (outcome === 'interrupted') sets[1].done = false;
    Object.assign(scope === 'exercise' ? data : sets[0], flag);
    const perf = loggedWorkoutPerformance(program, d, [data], 1, 'lb', {}, []);
    const saved = scope === 'exercise' ? perf[id] : perf[id].sets[0];
    for (const [key, value] of Object.entries(flag)) assert.deepEqual(saved[key], value);
    const history = [{ id: 'actual-log', programId: program.id, dayId: d.id, weekIndex: 1, date: 1, unit: 'lb', perf }];
    const advice = sessionSuggestion(program, d, 0, null, 'lb', 1, history);
    assert.notEqual(advice.action, 'increase_load');
    if (program.nextEngine) assert.equal(analyzeShellHistoryForNextEngine(program, history, EXERCISES).workouts[0].progression[0].outcome, outcome);
    checks++;
}
console.log(`PASS M225: malformed numeric/revision evidence, canonical adaptive/recovery/decision revision ownership, ${checks} context/provenance routes, additive flags, actual generated/custom log roundtrips and immutable tiered replay.`);

