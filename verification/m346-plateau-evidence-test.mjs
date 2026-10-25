import assert from 'node:assert/strict';
import { CURATED_EXERCISES } from '../modules/next-engine/exercise-db.js';
import { reselectProgressionStyle } from '../modules/next-engine/progression-style.js';
import { analyzeShellHistoryForNextEngine, deriveProgressionSelectionEvidence } from '../modules/next-engine/workout-history-adapter.js';
import { EXERCISES, EX_BY_ID, computeCell, prescribeSets,
    loggedWorkoutPerformance, markUserPrescriptionOverride, resolveNextShellExerciseId } from '../modules/engine-api.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { config, ids } from './m265-generation-fixture.mjs';

const clone = x => JSON.parse(JSON.stringify(x));
const movement = CURATED_EXERCISES.find(x => x.id === 'back_squat');
assert.ok(movement);
const specimen = (style = 'linear', reps = 8, rir = 2, count = 3) => ({
    session: { exercises: [{ exerciseId: 'back_squat', role: 'primary_strength',
        sets: 3, progressionStyle: style, prescription: { reps: [6, 10], rir: [1, 3] } }] },
    progression: [{ exerciseId: 'back_squat', role: 'primary_strength',
        outcome: 'productive', action: 'add_reps', reasonCode: 'normal_progression' }],
    performedSets: Array.from({ length: count }, (_, i) => ({
        exerciseId: 'back_squat', setIndex: i, load: 100, reps, rir }))
});
const evidence = workouts => deriveProgressionSelectionEvidence(workouts).back_squat;
assert.equal(evidence([specimen(), specimen()]).stallCount, 0,
    'Two flat workouts cannot establish a repeated plateau');
const third = evidence(Array.from({ length: 3 }, () => specimen()));
assert.equal(third.stallCount, 1);
assert.equal(third.comparableExposures, 3);
const fourth = evidence(Array.from({ length: 4 }, () => specimen()));
assert.equal(fourth.stallCount, 2);
const switching = reselectProgressionStyle(movement, 'primary_strength', {
    phase: 'strength_accumulation', experience: 'novice', currentStyle: 'linear',
    evidence: fourth
});
assert.equal(switching.style, 'double',
    'Four comparable unchanged exposures should retire stalled novice linear progression');
assert.equal(evidence([specimen(), specimen(), specimen('linear', 9), specimen('linear', 9)]).stallCount, 0,
    'Rep improvement resets the plateau streak');
assert.equal(evidence([specimen(), specimen(), specimen('linear', 8, 1)]).stallCount, 0,
    'Worsened effort is not a stable-effort plateau');
assert.equal(evidence([specimen('linear', 8, null), specimen('linear', 8, null),
    specimen('linear', 8, null), specimen('linear', 8, null)]).stallCount, 0,
'Unknown RIR cannot become high-confidence repeated plateau evidence');
assert.equal(evidence([specimen(), specimen(), specimen('linear', 8, 2, 2),
    specimen()]).stallCount, 0,
'Partial prescriptions must break plateau streaks, even if wrongly marked productive');
const afterChange = evidence([...Array.from({ length: 4 }, () => specimen()),
    specimen('dynamic')]);
assert.equal(afterChange.styleExposures, 1,
    'The new method needs its own comparable evidence window');
assert.equal(afterChange.stallCount, 0,
    'A new progression method must not inherit the old method stagnation score');
assert.equal(reselectProgressionStyle(movement, 'primary_strength', {
    phase: 'strength_accumulation', experience: 'novice', currentStyle: 'dynamic',
    evidence: afterChange
}).style, 'dynamic', 'Do not immediately abandon a newly introduced method');

// Exercise real shell persistence and normalization rather than proving the
// estimator exclusively with hand-authored evaluation objects.
const built = generateNextProgramForShell({
    config: { ...config, name: 'M346 real plateau replay', days: 4, split: 'upper_lower',
        weeks: 4, session: 's90' },
    legacyExercises: EXERCISES, seed: 34617, makeId: ids('m346')
});
assert.equal(built.nextProgram.audit.result, 'pass');
const p = clone(built.program);
const choice = p.days.flatMap(day => day.exercises.map((id, slot) => ({ day, id, slot })))
    .find(({ day, id, slot }) => EX_BY_ID[id] && ['hypertrophy_isolation', 'hypertrophy_compound']
        .includes(computeCell(p, day, id, slot, 1).role));
assert.ok(choice);
const { day, id, slot } = choice, key = day.id + ':' + slot;
for (const [field, value] of [['sets', 3], ['reps', '8-12'], ['rir', '2'],
    ['progressionStyle', 'double']])
    p.overrides[key] = markUserPrescriptionOverride(p.overrides[key] ?? {}, field, value);
const engineId = resolveNextShellExerciseId(p, day, slot, EX_BY_ID[id]);
let history = [];
for (let i = 0; i < 4; i++) {
    const rows = prescribeSets(p, day, EX_BY_ID[id], slot, 1, 'lb',
        { weight: 100, target: 8, action: 'initial' }, null, {}, history, false);
    const logged = rows.map(s => ({ ...clone(s), done: true, weight: '100',
        reps: '9', actualRIR: 2 }));
    const perf = loggedWorkoutPerformance(p, day, [{ id, slot, sets: logged }],
        1, 'lb', {}, history);
    const date = Date.UTC(2026, 5, 1 + i);
    history.push(clone({ id: 'm346-log-' + i, programId: p.id,
        dayId: day.id, dayLabel: day.label, weekIndex: 1,
        unit: 'lb', date, updatedAt: date, perf }));
}
const analyzed = analyzeShellHistoryForNextEngine(p, history, EXERCISES);
const savedEvidence = analyzed.progressionEvidenceByExercise[engineId];
assert.ok(savedEvidence, 'Logged comparable history was lost on canonical movement mapping');
assert.equal(savedEvidence.comparableExposures, 4);
assert.equal(savedEvidence.stallCount, 2,
    'Four flat logged workouts must count as a genuine plateau');
console.log('ENGINE_PLATEAU_EVIDENCE_AUDIT ' + JSON.stringify({
    status: 'pass', movement: engineId, realSavedWorkouts: analyzed.workoutCount,
    realStalls: savedEvidence.stallCount, methodChange: switching.style,
    postSwitchComparableExposures: afterChange.styleExposures }));
