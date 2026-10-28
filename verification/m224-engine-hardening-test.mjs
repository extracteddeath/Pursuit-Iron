import assert from 'node:assert/strict';
import { EXERCISES, EX_BY_ID, TEMPLATES, templateConfig, sessionSuggestion, prescribeSets, computeCell, percentagePlanFor, trainingMaxForUnit, withTrainingMax, nextSessionCursor, nextDueDayId, exerciseSeries, exerciseTrends, exRecords, strengthSnapshot, strengthScore, strengthScoreHistory, scoreAttribution, daySeconds, loggedWorkoutPerformance, explainPrescription } from '../modules/engine-api.js';

import { generateNextProgramForShell, snapshotNextShellPrescription, markUserPrescriptionOverride } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine, deriveProgressionSelectionEvidence,
    nextWorkoutSuggestionFromPerformedShell } from '../modules/next-engine/workout-history-adapter.js';
import { captureShellVolumeSnapshot, enforceShellWeekCapacity } from '../modules/next-engine/volume-repair.js';
import { reconcilePendingRepTargets, refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
import { normalizeHistoryEntries, observedHistoryRIR, historyNumber } from '../modules/next-engine/history-contract.js';
import { percentageProtocolFor, deriveTieredLinearState, adaptPercentageSetBudget } from '../modules/next-engine/percentage-protocols.js';
import { setExLoadInc, EX_LOAD_INC } from '../modules/training-domain/records.js';

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

// M361: the final equipment boundary must reconcile its explanation as well as its cells.
// Old mixed loads remain historical facts when the selected equipment increment changes.
{
    const previousIncrements = EX_LOAD_INC;
    const roundedProgram = structuredClone(p);
    for (const [field,value] of [['sets',4],['reps','8-12'],['rir','2'],['progressionStyle','double']])
        roundedProgram.overrides[key]=markUserPrescriptionOverride(roundedProgram.overrides[key]||{},field,value);
    const mixed = history([100,105,110,115].map((w,i) => ({w,r:[12,10,8,8][i],rir:2,rirReported:true,done:true})));
    mixed[0].perf[id].weight=115;mixed[0].perf[id].reps=8;
    const before = JSON.stringify({ roundedProgram, mixed });
    try {
        for (const [unit, step, expected] of [['lb',10,110], ['kg',5,50], ['lb',3,114]]) {
            setExLoadInc({[id]:{v:step,unit}});
            const advice = suggest(roundedProgram,mixed,1,unit);
            assert.equal(advice.weight,expected);
            assert.equal(advice.action,'hold_load','Double progression retains the last load by default');
            assert.ok(advice.optionalLowerLoad>0&&advice.optionalLowerLoad<expected,'Fatigue reduction remains optional');
            assert.match(advice.reason,new RegExp(`^Hold ${expected} ${unit} and build reps`),
                'The explanation must name the held equipment-loadable weight');
            const next = prescribeSets(roundedProgram,day,EX_BY_ID[id],slot,1,unit,advice,{}, {},mixed,false).filter(row=>!row.warm&&!row.sub);
            assert.deepEqual(next.map(row=>Number(row.weight)),[expected,expected,expected,expected]);
            const why=explainPrescription({program:roundedProgram,day:{...day},ex:EX_BY_ID[id],slot,weekIndex:1,unit,
                perf:{[id]:{weight:999,reps:15}},history:mixed});
            assert.equal(why.load.weight,advice.weight,'Why consumes the final equipment-aware suggestion');
            assert.equal(why.load.reason,advice.reason);
            assert.equal(why.read.lastPerformance.weight,unit==='lb'?115:52.16,
                'Why reads the exact suggestion exposure and converts its historical load once');
            assert.equal(why.read.historyEntriesForThisDay,1,'A copied day object preserves day identity');
        }
        setExLoadInc({[id]:{v:10,unit:'lb'}});
        const earned=suggest(roundedProgram,history(rows(140,12)));
        assert.equal(earned.weight,150);
        assert.match(earned.reason,/from 140 lb to 150 lb/,'Rounding an earned increase retains the original observed load');
        const missed=suggest(roundedProgram,history(rows(100,6).map(row=>({...row,rir:0}))));
        assert.equal(missed.action,'hold_load');
        assert.ok(missed.optionalLowerLoad<missed.weight);
        assert.match(missed.reason,/Hold .* and build reps/);
        const evidence=structuredClone(mixed);
        evidence[0].perf[id].sets[0].rirReported=false;evidence[0].perf[id].sets[0].tr=2;
        evidence[0].perf[id].sets.unshift({w:20,r:15,warm:true});
        evidence[0].perf[id].sets.push({w:999,r:15,done:false});
        const superseded={...evidence[0],updatedAt:evidence[0].date-1,perf:{[id]:{weight:500,reps:15,sets:rows(500,15)}}};
        const why=explainPrescription({program:roundedProgram,day,ex:EX_BY_ID[id],slot,weekIndex:1,unit:'lb',
            perf:{[id]:{weight:999,reps:15}},history:[superseded,...evidence]});
        assert.equal(why.read.perSet.length,4,'Warmups and pending sets are not completed evidence');
        assert.deepEqual(why.read.perSet[0],{set:1,weight:100,reps:12,rir:2,source:'planned target'});
        assert.equal(why.read.lastPerformance.weight,115,'A stronger/global or superseded anchor cannot become LAST');
        assert.equal(why.read.historyEntriesForThisDay,1,'Superseded history cannot inflate the evidence count');
        const otherDay={...mixed[0],dayId:roundedProgram.days.find(d=>d.id!==day.id).id};
        const reference=explainPrescription({program:roundedProgram,day,ex:EX_BY_ID[id],slot,weekIndex:1,unit:'lb',history:[otherDay]});
        assert.equal(reference.read.lastPerformance,null,'A starting reference is not this day’s LAST');
        assert.equal(JSON.stringify({roundedProgram,mixed}),before,'Explaining rounding cannot rewrite history or the saved plan');
    } finally { setExLoadInc(previousIncrements); }
}

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

// Repeated movements are owned by their program/day occurrence. Another day may seed an initial
// reference, but it cannot become comparable progression evidence even when labels collide.
const repeatedDayProgram = structuredClone(p);
const repeatedSourceDay = repeatedDayProgram.days[0], repeatedTargetDay = repeatedDayProgram.days[1];
repeatedTargetDay.label = repeatedSourceDay.label;
repeatedTargetDay.exercises = [...repeatedSourceDay.exercises];
repeatedTargetDay.primaryIndex = repeatedSourceDay.primaryIndex;
repeatedTargetDay.t2Index = repeatedSourceDay.t2Index;
repeatedDayProgram.nextEngine.program.sessions[1] = structuredClone(repeatedDayProgram.nextEngine.program.sessions[0]);
for (let i = 0; i < repeatedSourceDay.exercises.length; i++)
    repeatedDayProgram.nextWeekPrescriptions[`${repeatedTargetDay.id}:${i}`] = structuredClone(repeatedDayProgram.nextWeekPrescriptions[`${repeatedSourceDay.id}:${i}`]);
const otherDayOnly = history(rows(100, 15)).map(h => ({ ...h, dayId: repeatedSourceDay.id, dayLabel: repeatedSourceDay.label }));
const repeatedDaySuggestion = sessionSuggestion(repeatedDayProgram, repeatedTargetDay, slot, null, 'lb', 1, otherDayOnly);
assert.equal(repeatedDaySuggestion?.referenceOnly, true);
assert.equal(repeatedDaySuggestion?.action, 'initial');
assert.match(repeatedDaySuggestion?.reason || '', /starting reference/i);
const otherDayKg = sessionSuggestion(repeatedDayProgram, repeatedTargetDay, slot, null, 'kg', 1, otherDayOnly);
assert.ok(Math.abs(otherDayKg.weight - repeatedDaySuggestion.weight / 2.2046226218487757) < 6,
    'generated cross-day starting references convert units once');
const ambiguousReplay = { ...otherDayOnly[0], id: 'ambiguous-replay', dayId: 'retired-day-id',
    dayLabel: repeatedSourceDay.label };
const ambiguousAnalysis = analyzeShellHistoryForNextEngine(repeatedDayProgram, [ambiguousReplay], EXERCISES);
assert.equal(ambiguousAnalysis.workoutCount, 0, 'duplicate-label stale history must not be replayed against an arbitrary day');
assert.ok(ambiguousAnalysis.excludedHistoryEntries.some(row => row.id === 'ambiguous-replay' && row.reason === 'unresolved_session'));
for (const count of [23, 28, 100]) {
    const cursor = nextSessionCursor(p, Array.from({ length: count }, (_, i) => ({ ...good[0], id: `duplicate-day-${i}` })));
    assert.equal(cursor.weekIndex, 1);
    assert.notEqual(cursor.dayIndex, 0);
}
const cursorOlder = { ...good[0], id: 'cursor-older', dayId: p.days[0].id, date: 1000 };
const cursorNewer = { ...good[0], id: 'cursor-newer', dayId: p.days[1].id, date: 2000 };
const orderedCursor = nextSessionCursor(p, [cursorNewer, cursorOlder]);
const reversedCursor = nextSessionCursor(p, [cursorOlder, cursorNewer]);
assert.deepEqual(reversedCursor, orderedCursor, 'next-session cursor is independent of imported history array order');
assert.equal(nextDueDayId(p, [cursorOlder, cursorNewer]), nextDueDayId(p, [cursorNewer, cursorOlder]),
    'program-view next day is independent of imported history array order');
const invalidFutureCursor = { ...cursorOlder, id: 'cursor-invalid-future', dayId: p.days.at(-1).id, date: 1e100 };
assert.deepEqual(nextSessionCursor(p, [invalidFutureCursor, cursorNewer, cursorOlder]), orderedCursor,
    'invalid imported timestamps cannot hijack the next-session cursor');
assert.equal(nextDueDayId(p, [invalidFutureCursor, cursorNewer, cursorOlder]), nextDueDayId(p, [cursorNewer, cursorOlder]),
    'invalid imported timestamps cannot hijack the program-view next day');
const supersededCursor = { ...cursorNewer, dayId: p.days[0].id, updatedAt: 1500 };
const correctedCursor = { ...cursorNewer, dayId: p.days[1].id, updatedAt: 2500 };
assert.deepEqual(nextSessionCursor(p, [supersededCursor, correctedCursor, cursorOlder]), orderedCursor,
    'duplicate history identities use the newest revision once rather than advancing twice');
const unresolvedCursor = { ...cursorNewer, id: 'cursor-unresolved', dayId: 'retired-unresolved-day',
    dayLabel: 'No matching authored day', date: 3000, perf: {} };
assert.deepEqual(nextSessionCursor(p, [unresolvedCursor, cursorNewer, cursorOlder]), orderedCursor,
    'valid-timestamp history that cannot resolve to an authored day cannot advance finite program progress');
assert.equal(nextDueDayId(p, [unresolvedCursor, cursorNewer, cursorOlder]), nextDueDayId(p, [cursorNewer, cursorOlder]),
    'unresolved history cannot hijack Program View next-day selection');
const endlessCursorProgram = { ...p, config: { ...p.config, endless: true } };
assert.deepEqual(nextSessionCursor(endlessCursorProgram, [unresolvedCursor, cursorNewer, cursorOlder]),
    nextSessionCursor(endlessCursorProgram, [cursorNewer, cursorOlder]),
    'unresolved history cannot advance endless accumulation or deload cadence');

// A saved custom target survives subsequent dose edits. Imported target effort has identical semantics.
const custom = { id: 'custom', custom: true, weeks: 6, config, days: [{ id: 'custom-day', label: 'Push', primaryIndex: -1, exercises: [id] }],
    overrides: { 'custom-day:0': { sets: 4, reps: '10-15', rir: '2', progressionStyle: 'double' } } };
const customSuggest = hs => sessionSuggestion(custom, custom.days[0], 0, null, 'lb', 1, hs);
const customHistory = sets => history(sets).map(h => ({ ...h, programId: custom.id, dayId: 'custom-day' }));
// Custom programs emit display-unit weights; the shared suggestion adapter must not convert them twice.
const customKg = sessionSuggestion(custom, custom.days[0], 0, null, 'kg', 1, customHistory(rows(50, 15)));
const customLb = customSuggest(customHistory(rows(50, 15)));
assert.ok(customKg && customLb && Math.abs(customKg.weight - customLb.weight / 2.2046226218487757) < 6,
    'custom-program recommendations must convert from stored loads into display units exactly once');
assert.ok(customKg.setTargets?.length > 0 && customKg.setTargets.every(t => Number.isFinite(t.weight)),
    'per-set loads must stay numeric after a display-unit change');
const foreignReference = { ...customHistory(rows(100, 15))[0], id: 'foreign-reference', dayId: 'other-day',
    programId: 'different-program', updatedAt: 1700000001000 };
const staleReference = { ...foreignReference, updatedAt: 1700000000000,
    perf: { [id]: { weight: 500, reps: 15, sets: rows(500, 15) } } };
const crossProgramCustomLb = customSuggest([staleReference, foreignReference]);
const crossProgramCustomKg = sessionSuggestion(custom, custom.days[0], 0, null, 'kg', 1,
    [staleReference, foreignReference]);
assert.equal(crossProgramCustomLb.referenceOnly, true, 'cross-program history is a reference, not earned progression');
assert.ok(crossProgramCustomLb.weight < 200, 'a superseded foreign workout cannot provide the starting load');
assert.ok(Math.abs(crossProgramCustomKg.weight - crossProgramCustomLb.weight / 2.2046226218487757) < 6,
    'canonical cross-program references must use the chosen display unit once');
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

// Strength maturity, trend and attribution must share the same persisted-revision owner. A stale
// duplicate cannot add training maturity, preserve a corrected PR or reappear before a date cutoff.
const statsDay = 86400000, statsNow = Date.now();
const strengthLog = (logId, exId, weight, date, updatedAt = date, programId = 'strength-stats') => ({
    id: logId, programId, date, updatedAt, unit: 'lb',
    perf: { [exId]: { weight, reps: 5, sets: [{ w: weight, r: 5 }] } }
});
const strengthBase = [
    strengthLog('bench-1', 'bb-bench', 150, statsNow - 5 * statsDay),
    strengthLog('bench-2', 'bb-bench', 160, statsNow - 4 * statsDay),
    strengthLog('squat-1', 'back-squat', 90, statsNow - 3 * statsDay),
    strengthLog('squat-2', 'back-squat', 100, statsNow - 2 * statsDay)
];
const oldStrengthPR = strengthLog('corrected-pr', 'bb-bench', 300, statsNow - 6 * statsDay);
const correctedStrengthPR = strengthLog('corrected-pr', 'bb-bench', 100, oldStrengthPR.date, statsNow);
const movedStrengthOld = strengthLog('moved-date', 'bb-bench', 120, statsNow - 100 * statsDay);
const movedStrength = strengthLog('moved-date', 'bb-bench', 120, statsNow - 10 * statsDay, statsNow);
const otherStrengthProgram = strengthLog('corrected-pr', 'bb-bench', 130, statsNow - 8 * statsDay, statsNow, 'other-program');
const legacyStrengthLog = strengthLog(null, 'back-squat', 95, statsNow - 7 * statsDay);
const strengthWinners = [correctedStrengthPR, movedStrength, otherStrengthProgram,
    legacyStrengthLog, structuredClone(legacyStrengthLog), ...strengthBase];
const strengthRevisions = [...strengthWinners, oldStrengthPR, movedStrengthOld, strengthBase[2],
    null, { ...oldStrengthPR, id: 'invalid-strength-date', date: 'invalid' }];
const strengthOriginal = structuredClone(strengthRevisions);
const scoreOf = hs => strengthScore(hs, 180, 'lb', 'male', 30);
const trendOf = hs => strengthScoreHistory(hs, 180, 'lb', 'male', 30);
const attributionOf = hs => scoreAttribution(hs,
    [{ date: statsNow - 120 * statsDay, w: 180, unit: 'lb' }], 180, 'lb', 'male', 30);
assert.equal(scoreOf(strengthBase).overall, 39, 'distinct workouts retain the existing maturity model');
assert.ok(trendOf(strengthWinners)?.overall.length >= 2, 'fixture must exercise a real strength trend');
assert.equal(attributionOf(strengthWinners), null, 'a date-corrected workout provides no prior-window baseline');
for (const revisedHistory of [strengthRevisions, strengthRevisions.slice().reverse()]) {
    assert.deepEqual(scoreOf(revisedHistory), scoreOf(strengthWinners), 'strength maturity counts current revisions once');
    assert.deepEqual(trendOf(revisedHistory), trendOf(strengthWinners), 'corrected PRs cannot survive in the strength trend');
    assert.deepEqual(attributionOf(revisedHistory), attributionOf(strengthWinners), 'attribution resolves revisions before its date cutoff');
}
const baselineStrengthLog = strengthLog('prior-baseline', 'bb-bench', 90, statsNow - 110 * statsDay);
const strengthWithBaseline = [baselineStrengthLog, ...strengthWinners];
const attributionWithBaseline = attributionOf(strengthWithBaseline);
assert.ok(attributionWithBaseline, 'an independent genuine baseline remains eligible');
assert.deepEqual(attributionOf([baselineStrengthLog, ...strengthRevisions]), attributionWithBaseline);
assert.equal(scoreOf([null, { date: 'invalid', perf: {} }]), null);
assert.equal(trendOf([null, { date: 'invalid', perf: {} }]), null);
assert.equal(attributionOf([null, { date: 'invalid', perf: {} }]), null);
assert.deepEqual(strengthRevisions, strengthOriginal, 'strength analytics cannot mutate persisted revisions');

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


// M338 supersedes M296's unsafe consolidation assertion: a blocked exposure cannot
// increase lighter rows to the historical maximum, even after a method change.
const priorDynamic = structuredClone(p);
for (const c of Object.values(priorDynamic.nextWeekPrescriptions[key])) Object.assign(c,
    { sets: 3, reps: '8-12', rir: '2', progressionStyle: 'dynamic' });
const dynamicPrescription = snapshotNextShellPrescription(priorDynamic, day, slot, EX_BY_ID[id], 1);
for (const style of ['double', 'linear', 'ladder', 'wave', 'e1rm', 'dynamic']) {
    const currentProgram = structuredClone(priorDynamic);
    for (const c of Object.values(currentProgram.nextWeekPrescriptions[key])) c.progressionStyle = style;
    for (const flags of [{ prescriptionEdited: true }, { badDay: true }, { interrupted: true }]) {
        const oldHistory = history([100, 95, 95].map(w => ({ w, r: 10, rir: 2, rirReported: true })), 'lb', flags);
        oldHistory[0].perf[id].prescription = dynamicPrescription;
        const before = structuredClone(oldHistory);
        const s = suggest(currentProgram, oldHistory);
        const openingRows = prescribeSets(currentProgram, day, EX_BY_ID[id], slot, 1, 'lb', s, null, {}, oldHistory, false);
        // A complete exposure is not interrupted merely because the session flag is set.
        // Its deliberate method-change recalibration remains distinct from a blocked hold.
        const recalibrated = flags.interrupted && style !== 'dynamic';
        if(recalibrated) assert.equal(s.action,'represcribe');
        assert.deepEqual(openingRows.filter(r => !r.warm && !r.sub).map(r => r.weight),
            recalibrated ? ['100','100','100'] : ['100', '95', '95'],
            style + ': blocked holds retain loads; complete method changes explicitly recalibrate');
        if(flags.badDay || flags.prescriptionEdited)
            assert.deepEqual(openingRows.filter(r => !r.warm && !r.sub).map(r => r.reps), ['10', '10', '10'],
                'Observed reps stay attached to the unchanged per-set loads during a hold');
        assert.ok(!s.setTargets?.some(t=>t.earnedLoadIncrease), 'A blocked exposure earns no per-set load increase');
        assert.deepEqual(oldHistory, before, 'Opening a workout does not rewrite historical methods or logged performance');
    }
}
console.log('PASS M338 fresh opening: changed methods with blocked evidence retain observed loads and reps without an earned increase.');
