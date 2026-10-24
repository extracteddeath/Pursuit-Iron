import assert from 'node:assert/strict';
import {
    EXERCISES, EX_BY_ID, computeCell, prescribeSets, sessionSuggestion,
    loggedWorkoutPerformance, markUserPrescriptionOverride,
    normalizeEditedHistoryEntry, resolveNextShellExerciseId, summarizeSession
} from '../modules/engine-api.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine, nextWorkoutSuggestionForShell } from '../modules/next-engine/workout-history-adapter.js';
import { applyLiveAutoregulation, reconcileRestoredLiveAdjustments } from '../modules/next-engine/live-autoregulation.js';
import { refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
import { config, ids } from './m265-generation-fixture.mjs';

// M341 adversarial journey: the SAME generated day and executable target are
// taken through live fatigue, manual changes, persistence, incomplete work,
// revision replay and subsequent recommendations. Never invent mock decisions.
const clone = value => JSON.parse(JSON.stringify(value));
const built = generateNextProgramForShell({
    config: { ...config, name: 'M341 adversarial lifecycle', days: 4, split: 'upper_lower',
        weeks: 4, session: 's90', progression: 'double' },
    legacyExercises: EXERCISES, seed: 34112, makeId: ids('m341')
});
const program = clone(built.program);
assert.equal(built.nextProgram.audit.result, 'pass');
const choice = program.days.flatMap(day => day.exercises.map((id, slot) => ({ day, id, slot })))
    .find(({ day, id, slot }) => EX_BY_ID[id]?.equip?.includes('cable')
        && computeCell(program, day, id, slot, 1).role === 'hypertrophy_isolation');
assert.ok(choice, 'Generated program must contain an executable cable isolation lift');
const { day, id, slot } = choice, key = day.id + ':' + slot;
for (const [field, value] of [['sets', 4], ['reps', '8-12'], ['rir', '2'],
    ['progressionStyle', 'double']])
    program.overrides[key] = markUserPrescriptionOverride(program.overrides[key] ?? {}, field, value);
const cell = computeCell(program, day, id, slot, 1);
assert.equal(cell.sets, 4);
assert.equal(cell.reps, '8-12');
assert.equal(cell.progressionStyle, 'double');
const ex = EX_BY_ID[id], engineId = resolveNextShellExerciseId(program, day, slot, ex);
const planned = summarizeSession(program, day, 1);
const beforeProgram = JSON.stringify(program);
const raw = () => prescribeSets(program, day, ex, slot, 1, 'lb',
    { weight: 100, target: 8, action: 'initial' }, null, {}, [], false)
    .filter(row => !row.warm && !row.sub);
const standard = raw();
assert.equal(standard.length, 4);
assert.deepEqual(raw(), standard, 'Opening the same week must not silently change set 2');
assert.ok(standard.every(row => row.auto && row.valueOwner === 'prescription'
    && row.weight === '100' && row.target?.prefillReps === '8'));
const scenarios = [];
let seq = 0;
function check(label, result) {
    scenarios.push({ label, ...result });
    console.log('M341_SCENARIO ' + JSON.stringify(scenarios.at(-1)));
}
function rowsFrom(reps, loads = [100, 100, 100, 100], efforts = [2, 2, 2, 2]) {
    return standard.map((row, i) => ({ ...clone(row), weight: String(loads[i]),
        reps: reps[i] === null ? row.reps : String(reps[i]), done: reps[i] !== null,
        ...(efforts[i] === null ? {} : { actualRIR: efforts[i] }) }));
}
function save(rows, context = {}) {
    const inRows = clone(rows);
    const perf = loggedWorkoutPerformance(program, day,
        [{ id, slot, sets: rows }], 1, 'lb', {}, []);
    assert.deepEqual(rows, inRows, 'Serialization must never mutate live rows');
    const lift = perf[id];
    assert.ok(lift, 'Production logger dropped the performed exercise');
    assert.equal(lift.prescription?.exerciseId, engineId,
        'Persisted snapshot lost its canonical movement identity');
    assert.equal(lift.prescription?.sets, 4,
        'Original executable prescription must not lose its four-set requirement');
    const date = Date.UTC(2026, 8, 20) + seq * 86400000;
    const entry = clone({ id: 'm341-log-' + (++seq), programId: program.id,
        dayId: day.id, dayLabel: day.label, weekIndex: 1,
        date, updatedAt: date, unit: 'lb', perf, ...context });
    const analysis = analyzeShellHistoryForNextEngine(program, [entry], EXERCISES);
    const decision = analysis.workouts[0]?.progression.find(row => row.exerciseId === engineId);
    assert.ok(decision, 'Saved workout cannot be evaluated against its logged exercise');
    const advice = nextWorkoutSuggestionForShell(program, [entry], EXERCISES, day, slot, 1, 'lb');
    assert.ok(advice?.action && advice?.reason,
        'Next-workout recommendation must explain the completed logged exposure');
    assert.deepEqual(advice.last, entry.perf[id],
        'Displayed LAST must use exact JSON-persisted, scoped performance');
    const counted = lift.sets.filter(s => !s.sub && !s.warm).length;
    assert.equal(analysis.performedSetCount, counted,
        'History analytics counted a different number of completed working sets');
    return { entry, analysis, decision, advice, counted };
}
// 1. Positive control: four logged ceiling sets can earn an increase.
{
    const done = save(rowsFrom([12, 12, 12, 12]));
    assert.equal(done.counted, 4);
    assert.equal(done.decision.action, 'increase_load');
    assert.ok(done.advice.weight > 100);
    check('complete-top-range', { action: done.advice.action, newLoad: done.advice.weight });
}
// 2. Incomplete work is real evidence but cannot masquerade as a complete win.
{
    const done = save(rowsFrom([12, 12, 12, null]));
    assert.equal(done.counted, 3);
    assert.notEqual(done.advice.action, 'increase_load',
        'Three completed sets cannot earn a four-set progression');
    assert.ok(done.analysis.workoutCount === 1);
    check('three-of-four', { action: done.advice.action, loggedSets: done.counted });
}
// 3. Classic double mixed loads: consolidate ONLY by an explicit conservative
// decision, with a bounded physical load; never clone the highest weight.
{
    const done = save(rowsFrom([12, 10, 8, 7], [95, 100, 105, 110]));
    assert.notEqual(done.advice.action, 'increase_load');
    assert.ok(done.advice.weight <= 102.5,
        'Fatigued last sets may not become four new 110-pound targets');
    const target = prescribeSets(program, day, ex, slot, 1, 'lb',
        done.advice, null, {}, [done.entry], false).filter(row => !row.sub && !row.warm);
    assert.equal(target.length, 4);
    assert.ok(target.every(row => Number(row.weight) <= 102.5),
        'Real next workout prefills must respect the fatigue-aware calibration');
    check('fatigue-aware-common-load', { action: done.advice.action, next: target.map(r => Number(r.weight)) });
}
// 4. Missing effort with all sets in range is not enough to force recalibration.
// Each set-position load is preserved, not silently promoted to 110 pounds.
{
    const done = save(rowsFrom([12, 11, 10, 8], [95, 100, 105, 110],
        [null, null, null, null]));
    assert.notEqual(done.decision.reasonCode, 'straight_set_calibration');
    assert.notEqual(done.advice.action, 'increase_load');
    const target = prescribeSets(program, day, ex, slot, 1, 'lb',
        done.advice, null, {}, [done.entry], false).filter(row => !row.sub && !row.warm);
    assert.deepEqual(target.map(row => Number(row.weight)), [95, 100, 105, 110],
        'An unearned hold must retain the actual per-set mixed loads');
    check('mixed-load-no-effort', { action: done.advice.action, next: target.map(r => Number(r.weight)) });
}
// 5. Two observed failures reduce ONE pending automatic accessory set.
// Interrupted completion and storage must retain fatigue provenance and bar progression.
{
    const observed = rowsFrom([6, 7, null, null], [100, 100, 100, 100], [0, 0, null, null]);
    const adjusted = applyLiveAutoregulation({ sets: observed, sourceIndex: 1, cell,
        snapLoad: weight => Math.floor(weight / 5) * 5 });
    assert.equal(adjusted.decision.action, 'reduce_pending_load');
    assert.equal(adjusted.sets[2].weight, '95');
    assert.equal(adjusted.sets[3].weight, '100');
    assert.deepEqual(applyLiveAutoregulation({ sets: clone(adjusted.sets), sourceIndex: 1, cell,
        snapLoad: weight => Math.floor(weight / 5) * 5 }).sets, clone(adjusted.sets),
        'Repeated fatigue feedback cannot repeatedly decrease the third set');
    const restored = reconcileRestoredLiveAdjustments(clone(adjusted.sets));
    assert.deepEqual(restored, clone(adjusted.sets),
        'Genuinely supported fatigue adjustments must survive device interruption');
    const completed = restored.map((row, i) => i === 2
        ? { ...row, done: true, reps: '8', actualRIR: 1 } : row);
    const saved = save(completed, { workoutInterrupted: true });
    assert.equal(saved.counted, 3);
    assert.equal(saved.entry.perf[id].sets[2].w, 95);
    assert.equal(saved.entry.perf[id].sets[2].recoveryLimited, true);
    assert.notEqual(saved.advice.action, 'increase_load');
    check('fatigue-and-interruption', { action: saved.advice.action, loggedSets: saved.counted });
    const orphan = clone(adjusted.sets);
    orphan[0].done = false;
    const orphanBefore = clone(orphan);
    const repaired = reconcileRestoredLiveAdjustments(orphan);
    assert.equal(repaired[2].weight, '100',
        'A saved reduction without its original logged evidence must be removed');
    assert.deepEqual(orphan, orphanBefore, 'Repair is read-only');
    check('orphan-adjustment-recovery', { loadRestored: Number(repaired[2].weight) });
}
// 6. A live user edit must survive a fresh prescription, restore, and logging.
// Mark the edit as noncomparable, so user-led adjustments never fabricate earnings.
{
    const manual = rowsFrom([12, 12, 12, 12]);
    manual[1].weight = '115';
    manual[1].valueOwner = 'user';
    manual[1].auto = false;
    manual[1].manualOverride = true;
    const original = clone(manual);
    const fresh = raw();
    const restored = refreshPendingSetTargets(clone(manual), fresh);
    assert.equal(restored[1].weight, '115');
    assert.equal(restored[1].valueOwner, 'user');
    assert.deepEqual(manual, original);
    const saved = save(restored);
    assert.equal(saved.entry.perf[id].sets[1].w, 115);
    assert.notEqual(saved.advice.action, 'increase_load',
        'Manually overridden loading must not be mistaken for earned progression');
    check('manual-edit-resume', { action: saved.advice.action, userLoad: saved.entry.perf[id].sets[1].w });
}
// 7. A revision replaces one workout once, including after a change in logged
// set effort; it must not manufacture another training day or working sets.
{
    const saved = save(rowsFrom([9, 9, 9, 9]));
    const amended = clone(saved.entry);
    amended.perf[id].sets[0].r = 10;
    const revised = normalizeEditedHistoryEntry(saved.entry, amended);
    const history = [saved.entry, revised];
    const analysis = analyzeShellHistoryForNextEngine(program, history, EXERCISES);
    assert.equal(analysis.workoutCount, 1);
    assert.equal(analysis.performedSetCount, 4);
    const advice = nextWorkoutSuggestionForShell(program, history, EXERCISES, day, slot, 1, 'lb');
    assert.deepEqual(advice.last, clone(revised.perf[id]));
    check('history-correction-once', { workoutCount: analysis.workoutCount, loggedSets: analysis.performedSetCount });
}
assert.equal(JSON.stringify(program), beforeProgram,
    'No scenario may mutate its source program or trigger a background regeneration');
assert.ok(planned.sets >= 4, 'The resulting workout still appears in real plan dosage');
assert.equal(scenarios.length, 8);
console.log('ENGINE_ADVERSARIAL_AUDIT ' + JSON.stringify({ status: 'pass', programId: program.id,
    exerciseId: id, canonicalId: engineId, prescribedSets: 4, scenarios }));
