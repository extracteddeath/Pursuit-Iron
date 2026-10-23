import assert from 'node:assert/strict';
import { summarizeSession, sessionPrescriptionRows, computeCell, exerciseAt, EXERCISES, EX_BY_ID,
    planOverview, plannedWeek, estimateMinutes, estimateMinutesFor, dayMuscleBreakdown, paceFactor,
    generateNextProgramForShell, planShorterSession, phaseFor, weekIntent } from '../modules/engine-api.js';
import { phaseLabel } from '../modules/next-engine/phase-policy.js';
import { captureShellVolumeSnapshot } from '../modules/next-engine/volume-repair.js';
import { config, ids } from './m265-generation-fixture.mjs';

import { summaryProgram } from './m324-session-summary-fixture.mjs';

const p = summaryProgram, day = p.days[0], original = JSON.stringify(p);
const expectedNames = ['Dumbbell Bench Press', 'Back Squat'];
const overview = planOverview(p, 2, null, [], [p]);
assert.deepEqual(overview.weeks[1].days[0].lifts, expectedNames);
assert.equal(overview.weeks[1].days[0].lead, expectedNames[0]);
assert.equal(overview.weeks[0].days[0].lead, 'Barbell Bench Press');
const contributions = dayMuscleBreakdown(p, day, 2).flatMap(row => row.from);
assert.ok(contributions.some(row => row.name === 'Back Squat'));
assert.ok(contributions.every(row => !['Barbell Bench Press', 'Lateral Raise'].includes(row.name)));

const paceHistory = Array.from({ length: 4 }, (_, i) => ({ id: `pace-${i}`, programId: p.id,
    date: Date.UTC(2026, 8, 1 + i), estMin: 30, durationMin: 42, setsDone: 3, totalSets: 3 }));
const pace = paceFactor(paceHistory, [p]);
assert.equal(pace.factor, 1.4);
const paced = planOverview(p, 2, null, paceHistory, [p]);
const summary = summarizeSession(p, day, 2, pace);
assert.equal(paced.weeks[1].days[0].minutes, summary.minutes);
assert.equal(summary.minutes, estimateMinutesFor(p, day, 2, pace));
assert.equal(summary.budgetMinutes, estimateMinutes(p, day, 2));
assert.ok(summary.minutes > summary.budgetMinutes, 'Display calibration must not rewrite engine capacity');
assert.equal(paced.weeks[1].sets, summary.sets);
assert.equal(plannedWeek(p, 2).sets, summary.sets);
assert.equal(paced.upNext.lead, expectedNames[0]);
assert.equal(JSON.stringify(p), original);

const copies = Array.from({ length: 5 }, (_, i) => ({ ...paceHistory[0], updatedAt: paceHistory[0].date + i }));
assert.equal(paceFactor(copies, [p]).known, false, 'Repeated revisions cannot manufacture four pacing observations');
const stale = paceHistory.map(row => ({ ...row, durationMin: 60, updatedAt: row.date - 1 }));
assert.deepEqual(paceFactor([...stale, ...paceHistory], [p]), pace,
    'An old edited duration cannot bias the shared estimate');

// Unknown base IDs and omitted shorter-session slots cannot inflate counts,
// hide a valid even-week replacement, or remain the lead lift.
const imported = { ...p, days: [{ ...day, exercises: ['missing-id', ...day.exercises] }],
    pairs: { [`${day.id}:0`]: 'db-bench' } };
assert.equal(summarizeSession(imported, imported.days[0], 1).exCount, 2);
assert.equal(summarizeSession(imported, imported.days[0], 2).exCount, 3);
assert.equal(planOverview(imported, 2).weeks[1].sets, summarizeSession(imported, imported.days[0], 2).sets);

const long = { ...p, pairs: {}, days: [{ ...day, exercises: ['bb-bench', 'back-squat', 'inc-curl', 'cable-fly', 'lat-raise', 'pushdown'] }],
    overrides: Object.fromEntries(Array.from({ length: 6 }, (_, slot) => [`${day.id}:${slot}`,
        { sets: 5, reps: slot < 2 ? '5-8' : '10-15', rir: '2', rest: slot < 2 ? 180 : 90 }])) };
const length = planShorterSession(long, long.days[0], 2, 30);
const shortenedDay = { ...long.days[0], sessionLengthPlan: length };
const shortened = summarizeSession(long, shortenedDay, 2, { factor: 1.6 });
assert.equal(shortened.minutes, length.estimatedMinutes, 'A valid short-session estimate is calibrated only once');
assert.equal(shortened.sets, Object.values(length.slots).reduce((n, slot) => n + slot.sets, 0));
assert.equal(shortened.exCount, Object.values(length.slots).filter(slot => slot.sets > 0).length);
for (const corrupt of [{ dayId: 'other' }, { programId: 'other' }, { weekIndex: 1 }, { schemaVersion: 9 }]) {
    const staleDay = { ...long.days[0], sessionLengthPlan: { ...length, ...corrupt } };
    assert.equal(summarizeSession(long, staleDay, 2, pace).minutes,
        summarizeSession(long, long.days[0], 2, pace).minutes, 'Foreign/stale shortening cannot hijack full-session duration');
}

let sessions = 0;
for (const [days, split] of [[3, 'full_body'], [4, 'upper_lower'], [5, 'ulppl']]) {
    const generated = generateNextProgramForShell({ config: { ...config, days, split, weeks: 4, deload: true },
        legacyExercises: EXERCISES, seed: 32400 + days, makeId: ids(`m324-${days}`) }).program;
    const before = JSON.stringify(generated);
    const plan = planOverview(generated, 1, null, paceHistory, [generated]);
    const label = phaseLabel(generated.nextEngine.program.phase);
    assert.equal(plan.currentPhase.label, label);
    assert.equal(phaseFor(generated, 4).label, label, 'Final work week cannot invent a Peak phase');
    assert.equal(weekIntent(generated, 4).label, label);
    assert.doesNotMatch(weekIntent(generated, 4).detail, /hardest week|leave it all/i);
    for (const week of [1, 4, 5]) {
        const view = captureShellVolumeSnapshot(generated, week, EXERCISES);
        for (const [index, d] of generated.days.entries()) {
            const s = summarizeSession(generated, d, week, pace);
            const expected = d.exercises.flatMap((_, slot) => {
                const id = exerciseAt(generated, d, slot, week), cell = computeCell(generated, d, id, slot, week);
                return EX_BY_ID[id] && !cell.missing && cell.sets > 0 ? [{ id, sets: cell.sets }] : [];
            });
            assert.deepEqual(s.exercises.map(row => ({ id: row.id, sets: row.cell.sets })), expected);
            assert.equal(s.sets, view.sessions[index].exercises.reduce((n, ex) => n + ex.sets, 0));
            assert.equal(plan.weeks[week - 1].days[index].sets, s.sets);
            assert.equal(plan.weeks[week - 1].days[index].minutes, s.minutes);
            assert.equal(s.exCount, sessionPrescriptionRows(generated, d, week).length);
            sessions++;
        }
    }
    assert.equal(JSON.stringify(generated), before, 'Summaries are pure projections, never plan regeneration');
}
console.log(`PASS M324: ${sessions} generated day/week comparisons, alternating names/muscles/counts, shared display/budget clocks, revision-aware pacing and scoped shorter-session estimates.`);
