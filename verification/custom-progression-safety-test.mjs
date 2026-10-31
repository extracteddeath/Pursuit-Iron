import assert from 'node:assert/strict';
import { sessionSuggestion, computeCell, prescribeSets, EX_BY_ID, previousWorkoutSet } from '../modules/engine-api.js';
import { retainDoubleProgressionSetLoads } from '../modules/training-domain/prescriptions.js';

import { setExLoadInc } from '../modules/training-domain/records.js';
const program={id:'custom1',custom:true,config:{unit:'lb'},overrides:{'d1:0':{sets:3,reps:'5-8',rir:'2',progressionStyle:'double'}},days:[{id:'d1',exercises:['back-squat']}]};
const history=[{programId:'custom1',dayId:'d1',date:Date.now(),unit:'lb',perf:{'back-squat':{weight:215,reps:2,sets:[
 {w:200,r:3,rir:null,done:true},{w:205,r:3,rir:null,done:true},{w:210,r:2,rir:null,done:true},{w:215,r:2,rir:0,done:true}
]}}}];
const s=sessionSuggestion(program,program.days[0],0,null,'lb',1,history);
assert.ok(s);
assert.equal(s.action,'hold_load');
assert.equal(s.dir,'hold');
assert.equal(s.weight,200);
assert.deepEqual(s.setTargets.map(t=>t.weight),[200,205,210], 'Three prescribed sets retain the first three prior loads');
assert.equal(s.optionalLowerLoad,185);
assert.equal(s.target,5);
console.log('PASS custom double progression safety: positional load hold; 185 lb optional reduction.');

// Regression from the screenshot: custom-plan ranges persisted as arrays must not collapse [10,15] to 10.
// Ten reps across all prescribed sets is the BASELINE of a 10-15 range; load only increases once every
// prescribed set reaches 15 at the planned effort.
const arrayProgram={id:'custom-array',custom:true,config:{unit:'lb'},overrides:{'d2:0':{sets:3,reps:[10,15],rir:[2,2],progressionStyle:'double'}},days:[{id:'d2',primaryIndex:0,exercises:['back-squat']}]};
const baselineHistory=[{programId:'custom-array',dayId:'d2',date:Date.now(),unit:'lb',perf:{'back-squat':{weight:100,reps:10,sets:[
 {w:100,r:10,rir:2,done:true},{w:100,r:10,rir:2,done:true},{w:100,r:10,rir:2,done:true}
]}}}];
const arrayCell=computeCell(arrayProgram,arrayProgram.days[0],'back-squat',0,1);
assert.equal(arrayCell.reps,'10-15');
assert.equal(arrayCell.range,'10-15');
assert.equal(arrayCell.rir,'2');
const baseline=sessionSuggestion(arrayProgram,arrayProgram.days[0],0,null,'lb',1,baselineHistory);
assert.ok(baseline);
assert.equal(baseline.action,'add_reps');
assert.equal(baseline.dir,'hold');
assert.equal(baseline.weight,100);
assert.equal(baseline.target,11);
assert.match(baseline.reason,/toward 15 reps/);
assert.doesNotMatch(baseline.reason,/reached 10 reps/);

const topHistory=[{programId:'custom-array',dayId:'d2',date:Date.now()+1,unit:'lb',perf:{'back-squat':{weight:100,reps:15,sets:[
 {w:100,r:15,rir:2,done:true},{w:100,r:15,rir:2,done:true},{w:100,r:15,rir:2,done:true}
]}}}];
const top=sessionSuggestion(arrayProgram,arrayProgram.days[0],0,null,'lb',1,topHistory);
assert.ok(top);
assert.equal(top.action,'increase_load');
assert.equal(top.dir,'up');
assert.equal(top.weight,105);
assert.equal(top.target,10);
assert.match(top.reason,/top of the rep range/);
console.log('PASS custom 10-15 array range: 10s hold/build; only 15s across all sets earn a load increase.');


// M166 regression: array-shaped custom sets can intentionally encode one set count per week.
const setScheduleProgram={id:'custom-set-schedule',custom:true,config:{unit:'lb',weeks:4},overrides:{'d3:0':{sets:[5,3,3,4],reps:'8-12',rir:'2'}},days:[{id:'d3',primaryIndex:0,exercises:['back-squat']}]};
assert.deepEqual([1,2,3,4].map(week=>computeCell(setScheduleProgram,setScheduleProgram.days[0],'back-squat',0,week).sets),[5,3,3,4]);
console.log('PASS custom set schedule: computeCell preserves 5/3/3/4 across weeks 1–4.');

// Fractional history and unit-converted references must pass the SAME loadability
// boundary as generated plans. Logged observations themselves remain untouched.
const gridProgram = { id: 'custom-grid', custom: true, config: { unit: 'lb' },
    overrides: { 'grid:0': { sets: 3, reps: '8-12', rir: '2', progressionStyle: 'double' } },
    days: [{ id: 'grid', primaryIndex: 0, exercises: ['inc-curl'] }] };
const gridDay = gridProgram.days[0], gridEx = EX_BY_ID['inc-curl'];
const gridHistory = (load, unit = 'lb', owner = gridProgram.id, reps = 9) => [{
    id: 'grid-log', programId: owner, dayId: gridDay.id, date: 1, unit,
    perf: { 'inc-curl': { weight: load, reps, sets: Array.from({ length: 3 }, () => ({ w: load, r: reps, rir: 2, done: true })) } }
}];
setExLoadInc({ 'inc-curl': { v: 5, unit: 'lb' } });
try {
    for (const [load, unit, owner] of [[38.5, 'lb', gridProgram.id], [17.5, 'kg', gridProgram.id],
        [38.5, 'lb', 'previous-program'], [17.5, 'kg', 'previous-program']]) {
        const hs = gridHistory(load, unit, owner), before = structuredClone(hs);
        const suggestion = sessionSuggestion(gridProgram, gridDay, 0, null, 'lb', 1, hs);
        assert.equal(suggestion.weight, 35, '5 lb equipment cannot suggest 38.5 or a raw converted reference');
        assert.ok((suggestion.setTargets ?? []).every(t => t.weight % 5 === 0));
        const rows = prescribeSets(gridProgram, gridDay, gridEx, 0, 1, 'lb', suggestion, null, {}, hs, false);
        assert.ok(rows.filter(s => !s.warm && !s.sub).every(s => Number(s.weight) % 5 === 0));
        assert.deepEqual(hs, before, 'rounding prescriptions never rewrites logged weights');
    }
    const earned = sessionSuggestion(gridProgram, gridDay, 0, null, 'lb', 1, gridHistory(35, 'lb', gridProgram.id, 12));
    assert.equal(earned.weight, 40, 'earned increases still reach the next selectable load');
    setExLoadInc({ 'inc-curl': { v: 0.5, unit: 'lb' } });
    assert.equal(sessionSuggestion(gridProgram, gridDay, 0, null, 'lb', 1, gridHistory(38.5)).weight, 38.5,
        'explicit fractional equipment increments remain valid');
} finally { setExLoadInc({}); }
console.log('PASS custom load grid: 38.5 lb and converted references snap to 35 on 5 lb equipment; earned increases and real fractional stock preserved.');

// M418: a skipped or undone earlier working set cannot shift a later completed
// load into the wrong position. The canonical last-set/history source uses
// workingSetIndex, not a compressed completed-row array.
{
    const proposed = { action: 'add_reps', weight: 195, setTargets: [
        { weight: 195, reps: 7 }, { weight: 190, reps: 6 }, { weight: 200, reps: 5 }
    ] };
    const remembered = retainDoubleProgressionSetLoads(proposed, [195, null, 205]);
    assert.deepEqual(remembered.setTargets.map(t => t.weight), [195, 190, 205],
        'A missing second position cannot inherit the third completed load');
    assert.equal(remembered.weight, 195);
    assert.deepEqual(proposed.setTargets.map(t => t.weight), [195, 190, 200],
        'The evaluator decision is never mutated by a hold safeguard');

    const gapLog = [{
        id: 'positioned-partial', programId: program.id, dayId: program.days[0].id,
        date: Date.now() + 2, unit: 'lb', perf: { 'back-squat': {
            weight: 215, reps: 2, sets: [
                { workingSetIndex: 0, w: 200, r: 3, done: true, rir: 0 },
                { workingSetIndex: 1, w: 205, r: 0, done: false },
                { workingSetIndex: 2, w: 210, r: 2, done: true, rir: 0 },
                { workingSetIndex: 3, w: 215, r: 2, done: true, rir: 0 }
            ]
        } }
    }];
    const saved = structuredClone(gapLog);
    const next = sessionSuggestion(program, program.days[0], 0, null, 'lb', 1, gapLog);
    assert.ok(next && Array.isArray(next.setTargets) && next.setTargets.length === 3,
        'An incomplete positioned exposure still creates the three prescribed sets');
    assert.equal(next.setTargets[0].weight, 200,
        'First set retains its actual 200 lb load');
    assert.equal(next.setTargets[2].weight, 210,
        'Third set retains 210 lb, never the later 215 lb fourth set');
    const prefill = prescribeSets(program, program.days[0], EX_BY_ID['back-squat'],
        0, 1, 'lb', next, null, {}, gapLog, false).filter(row => !row.warm && !row.sub);
    assert.equal(Number(prefill[2].weight), 210,
        'Workout prefill must preserve the same third-set history identity as the recommendation');
    assert.equal(prefill[2].target.decisionTrace.source.originalLoad, 210,
        'Source trace and displayed third-set load must agree on historical position');
    assert.deepEqual(gapLog, saved, 'Position-aware targeting must not rewrite history');
}
console.log('PASS M418 positional double holds: skipped working sets cannot shift later loads.');

// M418 follow-up: pre-index legacy history can retain a skipped row but no
// workingSetIndex metadata. Completion must not turn positions 0,2,3 into
// an apparently complete three-set exposure (and earn a false load increase).
{
    const legacyRows = [
        { w: 100, r: 15, rir: 2, done: true },
        { w: 100, r: 0, done: false },
        { w: 100, r: 15, rir: 2, done: true },
        { w: 100, r: 15, rir: 2, done: true }
    ];
    const legacyGapHistory = [{
        id: 'legacy-unindexed-gap', programId: arrayProgram.id, dayId: arrayProgram.days[0].id,
        date: Date.now() + 3, unit: 'lb',
        perf: { 'back-squat': { weight: 100, reps: 15, sets: legacyRows } }
    }];
    const original = structuredClone(legacyGapHistory);
    const observed = legacyGapHistory[0].perf['back-squat'];
    assert.equal(previousWorkoutSet(observed, 0).r, 15);
    assert.equal(previousWorkoutSet(observed, 1), null,
        'LAST set 2 must remain missing, not borrow the third completed working set');
    assert.equal(previousWorkoutSet(observed, 2).r, 15);
    assert.equal(previousWorkoutSet(observed, 3).r, 15);
    const next = sessionSuggestion(arrayProgram, arrayProgram.days[0], 0, null, 'lb', 1, legacyGapHistory);
    assert.ok(next);
    assert.notEqual(next.action, 'increase_load',
        'Three completed rows at positions 0, 2, 3 are not three prescribed sets 0, 1, 2');
    assert.equal(next.weight, 100, 'An incomplete unindexed exposure cannot earn 105 lb');
    const rows = prescribeSets(arrayProgram, arrayProgram.days[0], EX_BY_ID['back-squat'],
        0, 1, 'lb', next, null, {}, legacyGapHistory, false).filter(row => !row.warm && !row.sub);
    assert.equal(rows[1].target.decisionTrace.source, null,
        'Executable Workout trace must not falsely credit a skipped middle set');
    assert.equal(rows[2].target.decisionTrace.source.originalLoad, 100);
    assert.deepEqual(legacyGapHistory, original, 'Legacy history is immutable during target calculation');

    const withNull = { sets: [legacyRows[0], null, legacyRows[2]] };
    assert.equal(previousWorkoutSet(withNull, 1), null,
        'An explicit null history slot is also a missing position');
    assert.equal(previousWorkoutSet(withNull, 2)?.r, 15);
}
console.log('PASS M418 unindexed legacy skips: positional LAST and evaluator cannot earn a false increase.');
