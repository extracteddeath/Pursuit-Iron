import assert from 'node:assert/strict';
import { EXERCISES, EX_BY_ID, TEMPLATES, templateConfig, sessionSuggestion, prescribeSets,
    computeCell, percentagePlanFor, trainingMaxForUnit, withTrainingMax, nextSessionCursor,
    exerciseSeries, exerciseTrends, exRecords, strengthSnapshot, strengthScoreHistory, daySeconds, loggedWorkoutPerformance } from '../modules/App.js';
import { generateNextProgramForShell, snapshotNextShellPrescription, markUserPrescriptionOverride } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine, deriveProgressionSelectionEvidence,
    nextWorkoutSuggestionFromPerformedShell } from '../modules/next-engine/workout-history-adapter.js';
import { captureShellVolumeSnapshot, enforceShellWeekCapacity } from '../modules/next-engine/volume-repair.js';
import { reconcilePendingRepTargets, refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
import { normalizeHistoryEntries, observedHistoryRIR, historyNumber } from '../modules/next-engine/history-contract.js';
import { percentageProtocolFor, deriveTieredLinearState, adaptPercentageSetBudget } from '../modules/next-engine/percentage-protocols.js';

const gym = ['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
const config = { name: 'Hardening fixture', unit: 'lb', goal: 'both', experience: 'intermediate',
    split: 'upper_lower', days: 4, session: 's60', weeks: 6, progression: 'auto', deload: false,
    equipment: gym, focus: {}, reduce: [], barbellCap: 3, noBodyweight: false, noSupersets: false };
const built = generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 71007, makeId: () => 'm224' });
const p = structuredClone(built.program), day = p.days[0];
const slot = day.exercises.findIndex(id => EX_BY_ID[id].equip.includes('cable'));
const id = day.exercises[slot], key = `${day.id}:${slot}`;
for (const cell of Object.values(p.nextWeekPrescriptions[key])) Object.assign(cell,
    { sets: 4, reps: '10-15', rir: '2', progressionStyle: 'double' });
const rows = (w, r) => Array.from({ length: 4 }, () => ({ w, r, rir: 2, rirReported: true }));
const history = (sets, unit = 'lb', flags = {}) => [{ id: 'log', programId: p.id, dayId: day.id,
    weekIndex: 1, date: 1700000000000, unit, ...flags,
    perf: { [id]: { weight: sets[0]?.w, reps: sets[0]?.r, sets } } }];
const suggest = (program, hs, week = 1, unit = 'lb') => sessionSuggestion(program, day, slot, null, unit, week, hs);

// Missing fields and copied targets cannot invent athlete evidence.
for (const value of [null, undefined, '', ' ', NaN, Infinity]) assert.equal(historyNumber(value), null);
assert.equal(observedHistoryRIR({ rir: 2, tr: 2, rirReported: false }), null);
assert.equal(observedHistoryRIR({ rir: 2, tr: 2, rirReported: true }), 2);
assert.equal(observedHistoryRIR({ rir: 2, tr: 2 }), null);
assert.equal(observedHistoryRIR({ rir: 1, tr: 2 }), 1);
assert.equal(deriveProgressionSelectionEvidence([{ progression: [{ exerciseId: id, outcome: 'productive', estimated1RM: null }],
    performedSets: [{ exerciseId: id, rir: null }] }])[id].e1rmSamples, 0);
assert.ok(built.nextProgram.sessions.every(s => s.exercises.every(e => typeof e.progression === 'string')));

// Physical load comparisons agree across logging and display units, including live advice.
const changed = structuredClone(p); changed.nextWeekPrescriptions[key][2].reps = '5-8';
const lb = suggest(changed, history(rows(100, 10)), 2);
const kg = suggest(changed, history(rows(100 / 2.2046226218487757, 10), 'kg'), 2);
assert.equal(lb.weight, kg.weight);
const displayed = suggest(p, history(rows(100, 15)), 1, 'kg');
assert.ok(Math.abs(displayed.weight - suggest(p, history(rows(100, 15))).weight / 2.2046226218487757) < 5);
assert.doesNotMatch(displayed.reason, /\blb\b/);
const live = nextWorkoutSuggestionFromPerformedShell(p, EXERCISES, day, slot, 1,
    history(rows(100 / 2.2046226218487757, 15), 'kg')[0].perf, 'kg');
assert.ok(Math.abs(live.weight - 105 / 2.2046226218487757) < .1);

// Bad days, interruptions and edits remain explicit, including per-set imported flags.
for (const [flags, outcome] of [[{ badDay: true }, 'context_limited'],
    [{ readinessStatus: 'low' }, 'context_limited'], [{ prescriptionEdited: true }, 'non_comparable'],
    [{ substitutionOccurred: true }, 'non_comparable']]) {
    const a = analyzeShellHistoryForNextEngine(p, history(rows(100, 15), 'lb', flags), EXERCISES);
    assert.equal(a.workouts[0].progression[slot].outcome, outcome);
    assert.notEqual(suggest(changed, history(rows(100, 15), 'lb', flags), 2).action, 'represcribe');
}
const interrupted = analyzeShellHistoryForNextEngine(p, history(rows(100, 15).slice(0, 1), 'lb', { workoutInterrupted: true }), EXERCISES);
assert.equal(interrupted.workouts[0].progression[slot].outcome, 'interrupted');
const flagged = rows(100, 15); flagged[0].substituted = true;
assert.equal(analyzeShellHistoryForNextEngine(p, history(flagged), EXERCISES).workouts[0].progression[slot].outcome, 'non_comparable');
assert.equal(suggest(changed, history(rows(50, 10).slice(0, 1)), 2).weight, 50);
const work = rows(50, 10);
assert.equal(suggest(changed, history([{ w: 100, r: 12, warm: true }, ...work]), 2).weight,
    suggest(changed, history(work), 2).weight);

// Each identity contributes once; invalid/empty rows never advance a cycle or mask good advice.
const good = history(rows(100, 15));
const repeated = analyzeShellHistoryForNextEngine(p, Array.from({ length: 30 }, () => good[0]), EXERCISES);
assert.equal(repeated.workoutCount, 1); assert.equal(repeated.readyForNextBlock, false);
assert.equal(repeated.excludedHistoryEntries.length, 29);
const empty = analyzeShellHistoryForNextEngine(p, Array.from({ length: 30 }, (_, i) => ({ ...good[0], id: `empty-${i}`, perf: {} })), EXERCISES);
assert.equal(empty.workoutCount, 0); assert.equal(empty.readyForNextBlock, false);
const corrupt = { ...good[0], id: 'bad-date', date: 1e100 };
assert.equal(suggest(p, [corrupt, ...good]).weight, suggest(p, good).weight);
assert.equal(normalizeHistoryEntries([corrupt, ...good], p.id).excluded[0].reason, 'invalid_date');
const revised = { ...good[0], updatedAt: good[0].date + 1, perf: {} };
assert.equal(normalizeHistoryEntries([good[0], revised], p.id).entries[0], revised);
for (const count of [23, 28, 100]) {
    const cursor = nextSessionCursor(p, Array.from({ length: count }, (_, i) => ({ ...good[0], id: `duplicate-day-${i}` })));
    assert.equal(cursor.weekIndex, 1);
    assert.notEqual(cursor.dayIndex, 0);
}

// A saved custom target survives subsequent dose edits. Imported target effort has identical semantics.
const custom = { id: 'custom', custom: true, weeks: 6, config, days: [{ id: 'custom-day', label: 'Push', primaryIndex: -1, exercises: [id] }],
    overrides: { 'custom-day:0': { sets: 4, reps: '10-15', rir: '2', progressionStyle: 'double' } } };
const customSuggest = hs => sessionSuggestion(custom, custom.days[0], 0, null, 'lb', 1, hs);
const customHistory = sets => history(sets).map(h => ({ ...h, programId: custom.id, dayId: 'custom-day' }));
const unreported = rows(25, 8).map(s => ({ ...s, tr: 2, rirReported: false }));
assert.deepEqual({ action: customSuggest(customHistory(unreported)).action, weight: customSuggest(customHistory(unreported)).weight },
    { action: suggest(p, history(unreported)).action, weight: suggest(p, history(unreported)).weight });
const customRows = prescribeSets(custom, custom.days[0], EX_BY_ID[id], 0, 1, 'lb', { weight: 50 }, null, {}, [], false)
    .map(r => ({ ...r, done: true, reps: '15' }));
const serializedCustom = loggedWorkoutPerformance(custom, custom.days[0], [{ id, slot: 0, note: '', sets: customRows }], 1, 'lb', {}, []);
assert.equal(serializedCustom[id].prescription.sets, 4);
assert.equal(serializedCustom[id].prescription.exerciseId, id);
assert.equal(serializedCustom[id].sets[0].rirReported, false);
const incomplete = customHistory(rows(50, 15).slice(0, 2));
incomplete[0].perf[id].prescription = { schemaVersion: 1, exerciseId: id, sets: 4, reps: [10,15], rir: [2,2], rest: 90 };
custom.overrides['custom-day:0'].sets = 2;
assert.notEqual(customSuggest(incomplete).action, 'increase_load');

// PRs, series and classification compare physical loads before rounding strength estimates.
const bench = 'bb-bench';
const benchLog = (w, unit, date) => ({ id: `bench-${date}`, programId: 'stats', date, unit,
    perf: { [bench]: { weight: w, reps: 10, sets: rows(w, 10) } } });
const mixed = [benchLog(100 / 2.2046226218487757, 'kg', 2000), benchLog(100, 'lb', 1000)];
assert.ok(Math.abs(exerciseSeries(mixed, bench, 'top').delta) < .2);
assert.ok(Math.abs(exerciseTrends(mixed)[0].delta) < 1);
const stronger = [benchLog(50, 'kg', 3000), ...mixed];
assert.equal(exRecords(stronger, bench, 'lb').maxWeight.unit, 'kg');
const canonical = mixed.map(h => ({ ...h, unit: 'lb', perf: { [bench]: { weight: 100, reps: 10, sets: rows(100, 10) } } }));
assert.ok(Math.abs(strengthSnapshot(mixed, 180, 'lb', 'male', 30).score - strengthSnapshot(canonical, 180, 'lb', 'male', 30).score) < .1);

assert.deepEqual(strengthScoreHistory(mixed, 180, 'lb', 'male', 30), strengthScoreHistory(canonical, 180, 'lb', 'male', 30));

// The final executable clock is hard; protected manual counts cannot be silently trimmed.
const capacity = generateNextProgramForShell({ config: { ...config, goal: 'hypertrophy', split: 'strength_fb', days: 3,
    noSupersets: true, noBodyweight: true, volumeApproach: 'standard', focusList: [] },
    legacyExercises: EXERCISES, seed: 710136, makeId: () => 'capacity' }).program;
for (let week = 1; week <= capacity.config.weeks; week++) assert.ok(captureShellVolumeSnapshot(capacity, week, EXERCISES)
    .sessions.every(s => s.estimatedMinutes <= s.maxMinutes));
const impossible = structuredClone(capacity);
for (const d of impossible.days) for (let i = 0; i < d.exercises.length; i++)
    impossible.overrides[`${d.id}:${i}`] = markUserPrescriptionOverride({}, 'sets', 20);
const immutable = JSON.stringify(impossible);
assert.throws(() => enforceShellWeekCapacity(impossible, EXERCISES), e => e.code === 'NEXT_WEEK_CAPACITY_EXCEEDED');
assert.equal(JSON.stringify(impossible), immutable);
const straight = { ...custom, config: { ...config, noSupersets: true }, days: [{ ...custom.days[0], exercises: ['cable-fly','cable-curl'] }],
    overrides: { 'custom-day:0': { sets: 4, rest: 120 }, 'custom-day:1': { sets: 4, rest: 120 } }, ss: { 'custom-day:0': true } };
assert.equal(daySeconds(straight, straight.days[0], 1, true), daySeconds({ ...straight, ss: {} }, straight.days[0], 1, true));

// Literal percentage arithmetic is independently checked before adapting the accepted set budget.
assert.deepEqual(percentageProtocolFor({ scheme: '531', tm: 200, weekIndex: 1, weeksTotal: 4 }).sets
    .map(s => [s.weight, s.reps, s.amrap]), [[130,'5',false],[150,'5',false],[170,'5+',true]]);
let percentageChecks = 0;
for (const templateId of ['p531','p531beg','madcow','nsuns','texas','gvt','rippler','jt2','gzclp']) {
    const template = TEMPLATES.find(t => t.id === templateId);
    const cfg = { ...templateConfig(template, gym), unit: 'lb', barbellCap: 3, noSupersets: true, noBodyweight: false };
    const pp = generateNextProgramForShell({ config: cfg, legacyExercises: EXERCISES, seed: 712020 + percentageChecks,
        makeId: () => `percent-${templateId}` }).program;
    for (const d of pp.days) for (const s of [d.primaryIndex, d.t2Index].filter(i => i != null && i >= 0)) {
        const ex = EX_BY_ID[d.exercises[s]];
        pp.trainingMax = { ...pp.trainingMax, [ex.id]: 200 };
        for (const week of [1, 2, cfg.weeks, cfg.weeks + 1]) for (const unit of ['lb','kg']) {
            const preview = percentagePlanFor(pp, d, ex, s, week, unit);
            if (!preview) continue;
            const runtime = prescribeSets(pp, d, ex, s, week, unit, null, null, {}, [], true);
            const working = runtime.filter(r => !r.warm && !r.sub);
            assert.equal(working.length, computeCell(pp, d, ex.id, s, week).sets);
            assert.deepEqual(working.map(r => [r.weight, r.reps, r.target.amrap]),
                preview.sets.map(r => [String(r.weight), String(r.reps).replace('+',''), !!r.amrap]));
            assert.ok(runtime.filter(r => r.warm).every(r => +r.weight < +working[0].weight));
            assert.deepEqual(reconcilePendingRepTargets(working, computeCell(pp,d,ex.id,s,week)), working);
            assert.deepEqual(refreshPendingSetTargets(working, working), working);
            if (unit === 'lb' && week === 1) {
                const serialized = loggedWorkoutPerformance(pp, d, [{ id: ex.id, slot: s, note: '',
                    sets: working.map(r => ({ ...r, done: true })) }], week, unit, {}, []);
                assert.equal(serialized[ex.id].prescription.setTargets.length, working.length);
                const exposure = analyzeShellHistoryForNextEngine(pp, [{ id: 'percent-log', date: 1700000000000,
                    programId: pp.id, dayId: d.id, weekIndex: week, unit, perf: serialized }], EXERCISES);
                assert.equal(exposure.workouts[0].progression[s].outcome, 'success');
                if (templateId === 'gzclp') {
                    const next = percentagePlanFor(pp, d, ex, s, week, unit, [{ id: 'gz-earned', date: 1700000000000,
                        programId: pp.id, dayId: d.id, unit, perf: serialized }]);
                    assert.ok(next.sets[0].weight > preview.sets[0].weight);
                }
            }
            if (week > cfg.weeks) assert.ok(preview.sets.every(r => !r.amrap));
            percentageChecks++;
        }
        if (templateId === 'gzclp') {
            const tier = s === d.primaryIndex ? 't1' : 't2';
            pp.gzStage = { ...pp.gzStage, [ex.id + ':' + tier]: 2 };
            const failedRows = prescribeSets(pp, d, ex, s, 1, 'lb', null, null, {}, [], false)
                .filter(r => !r.sub).map(r => ({ ...r, done: true, reps: '0' }));
            const zeroLog = loggedWorkoutPerformance(pp, d, [{ id: ex.id, slot: s, note: '', sets: failedRows }], 1, 'lb', {}, []);
            assert.ok(zeroLog[ex.id].sets.every(r => r.r === 0 && r.failedAttempt));
            const next = percentagePlanFor(pp, d, ex, s, 1, 'lb', [{ id: 'explicit-failed-attempt', date: 1700000000000,
                programId: pp.id, dayId: d.id, unit: 'lb', perf: zeroLog }]);
            assert.equal(next.stage, 0);
            assert.ok(next.sets[0].weight < Number(failedRows[0].weight));
        }
        const edited = withTrainingMax(pp, ex.id, 100, 'kg');
        assert.ok(Math.abs(trainingMaxForUnit(edited, ex.id, 'kg') - 100) < 1e-9);
        assert.ok(Math.abs(trainingMaxForUnit(edited, ex.id, 'lb') - 220.46226218487757) < 1e-9);
    }
}
console.log(`PASS M224 hardening: 14 finding families; shared evidence, units, saved targets, capacity and percentage parity (${percentageChecks} cases).`);

const gzEntry = (date, stage, reps, count = 3, flags = {}) => ({ id: `gz-${date}`, programId: 'gz',
    date, unit: 'lb', ...flags, perf: { lift: { sets: Array.from({ length: count }, () => ({ w: 50, r: reps })),
        prescription: { sets: 3, protocol: { scheme: 'gzclp', tier: 't1', stage },
            setTargets: Array.from({ length: 3 }, () => ({ reps: [3,2,1][stage], weight: 50 })) } } } });
const replayGz = entries => deriveTieredLinearState({ programId: 'gz', exerciseId: 'lift', tier: 't1',
    initialLoad: 50, entries, unit: 'lb', nextLoad: w => w + 5, resetLoad: w => w * .85 });
assert.deepEqual(replayGz([gzEntry(1, 0, 3)]), { stage: 0, weight: 55 });
assert.deepEqual(replayGz([gzEntry(1, 0, 2)]), { stage: 1, weight: 50 });
assert.deepEqual(replayGz([gzEntry(1, 1, 1)]), { stage: 2, weight: 50 });
assert.deepEqual(replayGz([gzEntry(1, 2, 0)]), { stage: 0, weight: 50 }); // zero reps never complete a set
const hardMiss = gzEntry(1, 2, 1); hardMiss.perf.lift.prescription.setTargets.forEach(t => t.reps = 2);
assert.deepEqual(replayGz([hardMiss]), { stage: 0, weight: 42.5 });
assert.deepEqual(replayGz([gzEntry(1, 0, 3), gzEntry(2, 0, 3, 1)]), { stage: 0, weight: 55 });
assert.deepEqual(replayGz([gzEntry(1, 0, 3, 3, { badDay: true })]), { stage: 0, weight: 50 });
console.log('PASS M224 tiered linear history: earned increases, stage failures/reset, incomplete/context holds and no secondary state mutation.');

const failedSingle = gzEntry(1, 2, 0); failedSingle.perf.lift.sets.forEach(s => s.failedAttempt = true);
assert.deepEqual(replayGz([failedSingle]), { stage: 0, weight: 42.5 });

const nsunsProtocol = percentageProtocolFor({ scheme: 'nsuns', tm: 200, weekIndex: 1, weeksTotal: 4 });
for (const count of [1,2,3,4,5,6,7,8]) {
    const adapted = adaptPercentageSetBudget(nsunsProtocol, count);
    assert.ok(adapted.sets.some(s => s.weight === 190 && s.amrap));
    if (count > 1) assert.deepEqual(adapted.sets.at(-1), nsunsProtocol.sets.at(-1));
}

for (const tm of [null, NaN, Infinity, -1, 0])
    assert.equal(percentageProtocolFor({ scheme: '531', tm, weekIndex: 1, weeksTotal: 4 }), null);
for (const weekIndex of [NaN, Infinity, -1, 0, 1.5])
    assert.equal(percentageProtocolFor({ scheme: '531', tm: 200, weekIndex, weeksTotal: 4 }), null);
