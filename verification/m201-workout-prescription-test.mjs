import assert from 'node:assert/strict';
import { movementFamilyOptions, VARIANT_PRESENTATION, AttachmentGlyph, figurePose } from '../modules/App.js';
import { EXERCISES, EX_BY_ID, computeCell, prescribeSets, sessionSuggestion, subRegionOf, expandEquipment } from '../modules/engine-api.js';

import { reconcilePendingRepTargets } from '../modules/next-engine/workout-runtime.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { nextExerciseIdForShellExercise, getNextShellCell } from '../modules/next-engine/app-shell-adapter.js';

// The reported incline-curl history: 30x10/8/6 then 25x11; range 8-12. Session-level advice
// and newly built rows must use that actual range rather than estimates from an old set tuner.
const program = { id: 'm201-custom', custom: true, engineV: 4, config: { weeks: 10, unit: 'lb', goal: 'both',
    progression: 'auto', experience: 'intermediate', deload: true }, slotBias: { 'd:1': 1 }, autoBias: {},
    days: [{ id: 'd', label: 'Upper', primaryIndex: 0, exercises: ['bb-bench', 'inc-curl', 'lat-raise', 'seated-calf'] }], overrides: {} };
const day = program.days[0], curl = EX_BY_ID['inc-curl'];
const history = [{ id: 'm201-history', programId: program.id, dayId: day.id, date: 1, unit: 'lb', perf: {
    'inc-curl': { weight: 30, reps: 9, sets: [
        { w: 30, r: 10, rir: 2 }, { w: 30, r: 8, rir: 2 }, { w: 30, r: 6, rir: 2 }, { w: 25, r: 11, rir: 2 }
    ] }
} }];
const sug = sessionSuggestion(program, day, 1, null, 'lb', 4, history);
assert.equal(sug.target, 8);
assert.equal(sug.action, "hold_load");
assert.equal(sug.weight, 30, 'Double progression retains the first set load rather than silently cutting it');
assert.deepEqual(sug.setTargets.map(t=>t.weight),[30,30,30,25],
    'Each prior set keeps its own weight while the workout builds reps');
assert.equal(sug.optionalLowerLoad,25,'The fatigue-reduced load is a separate opt-in suggestion');
assert.match(sug.reason,/Keep your previous working-set loads and build reps/);
const fresh = prescribeSets(program, day, curl, 1, 4, 'lb', sug, null, {}, history, false);
assert.ok(fresh.length > 0);
assert.ok(fresh.every(s => Number(s.reps) >= 8 && Number(s.reps) <= 12));
assert.ok(fresh.every(s => s.target.reps === '8-12'));

// Reproduce the screenshot's surviving automatic 14/13 fields on resume. The app may clamp its
// pending entries, but it cannot rewrite logged performance, manual targets, or myo/drop minis.
const saved = [
    { weight: '30', reps: '14', done: true, auto: true, target: { reps: '8-12' } },
    { weight: '25', reps: '14', done: false, auto: true, target: { reps: '12-20' } },
    { weight: '25', reps: '13', done: false, auto: true, added: true, target: { reps: '8-12' } },
    { weight: '25', reps: '14', done: false, auto: false, target: { reps: '8-12' } },
    { weight: '15', reps: '20', done: false, auto: true, warm: true },
    { weight: '25', reps: '5', done: false, auto: true, sub: true, kind: 'myo' }
];
const original = structuredClone(saved), cell = computeCell(program, day, 'inc-curl', 1, 4);
const repaired = reconcilePendingRepTargets(saved, cell);
assert.deepEqual(repaired.map(s => s.reps), ['14', '12', '12', '14', '20', '5']);
assert.equal(repaired[1].target.reps, cell.range);
assert.equal(repaired[1].weight, '25');
assert.equal(repaired[0], saved[0]); assert.equal(repaired[3], saved[3]);
assert.deepEqual(saved, original, 'resume repair must be immutable');
assert.equal(reconcilePendingRepTargets(repaired, cell), repaired, 'resume repair must be idempotent');
assert.equal(reconcilePendingRepTargets([{ auto: true, reps: '', target: { reps: '8-12' } }], cell)[0].reps, '');
assert.equal(reconcilePendingRepTargets([{ auto: true, reps: '14', target: {} }], { reps: [10, 15] })[0].reps, '14', '14 is valid inside a real 10-15 range');
assert.equal(reconcilePendingRepTargets([{ auto: true, reps: '14', target: {} }], { reps: '10' })[0].reps, '10');

// An old custom program stored its last-set schedule implicitly. Restore eligible late-block
// work, respect explicit edits/off, and keep introduction, compound mains, and deload clear.
assert.equal(computeCell(program, day, 'inc-curl', 1, 1).tech, null);
assert.match(computeCell(program, day, 'inc-curl', 1, 8).tech, /lengthened partials/);
assert.match(computeCell(program, day, 'lat-raise', 2, 8).tech, /myo-reps/);
assert.match(computeCell(program, day, 'seated-calf', 3, 8).tech, /static stretch/);
assert.equal(computeCell(program, day, 'bb-bench', 0, 8).tech, null);
assert.equal(computeCell(program, day, 'inc-curl', 1, 11).tech, null);
assert.equal(computeCell({ ...program, config: { ...program.config, goal: 'strength' } }, day, 'inc-curl', 1, 8).tech, null);
// Imported/migrated custom programs can lose optional legacy engine metadata. `custom: true` is the
// durable identity, so losing engineV/slotBias/autoBias must not erase the last-set schedule.
const markerlessCustom = structuredClone(program);
delete markerlessCustom.engineV; delete markerlessCustom.slotBias; delete markerlessCustom.autoBias;
assert.match(computeCell(markerlessCustom, markerlessCustom.days[0], 'inc-curl', 1, 8).tech, /lengthened partials/);
assert.match(computeCell(markerlessCustom, markerlessCustom.days[0], 'lat-raise', 2, 8).tech, /myo-reps/);
const off = { ...program, overrides: { 'd:2': { techOverride: null } } };
assert.equal(computeCell(off, day, 'lat-raise', 2, 8).tech, null);
const drop = { ...program, overrides: { 'd:1': { techOverride: 'Last set: drop set — reduce load and continue' } } };
const drops = prescribeSets(drop, day, curl, 1, 8, 'lb', sug, null, {}, history, false);
assert.equal(drops.filter(s => s.sub && s.kind === 'drop' && s.prescribed).length, 2);
const myo = prescribeSets(program, day, EX_BY_ID['lat-raise'], 2, 8, 'lb', { weight: 15, target: 12 }, null, {}, [], false);
assert.equal(myo.filter(s => s.sub && s.kind === 'myo' && s.prescribed).length, 3);
assert.equal(myo.filter(s => !s.sub).length, computeCell(program, day, 'lat-raise', 2, 8).sets);
const nextOff = { engineSource: 'pursuit-next', nextWeekPrescriptions: { 'd:0': { 1: { sets: 3, reps: [8,12], rir: [2,2], tech: 'drop set' } } }, overrides: { 'd:0': { techOverride: null } } };
assert.equal(getNextShellCell(nextOff, { id: 'd' }, 0, 1).tech, null);

// Catalog coverage includes equipment, stimulus, engine identity, setup art, and family browsing.
const ids = ['seated-db-lat-raise', 'single-db-lat-raise', 'chest-supported-lat-raise', 'seated-cable-lat-raise',
    'cuff-cable-lat-raise', 'seated-rear-fly', 'single-cable-rear-fly', 'chest-supported-rear-fly'];
assert.equal(new Set(EXERCISES.map(ex => ex.id)).size, EXERCISES.length);
for (const id of ids) {
    const ex = EX_BY_ID[id], def = EXERCISE_MAP.get(id);
    assert.ok(ex && def, id + ' must exist in both catalogs');
    assert.equal(nextExerciseIdForShellExercise(ex), id);
    const region = id.includes('rear') ? 'rear_delts' : 'side_delts';
    assert.equal(subRegionOf(ex), region);
    assert.equal(def.muscles[region].credit, 1);
    assert.deepEqual(def.equipment, ex.equip);
    assert.notEqual(figurePose(ex), 'generic');
    assert.ok(VARIANT_PRESENTATION[id]?.visual && VARIANT_PRESENTATION[id]?.detail);
    const glyph = AttachmentGlyph({ ex });
    assert.equal(glyph.props['data-variant-visual'], VARIANT_PRESENTATION[id].visual);
    assert.equal(glyph.props.children.type, 'g');
}
const dbOnly = expandEquipment(['dumbbell']);
assert.ok(movementFamilyOptions(EX_BY_ID['lat-raise'], dbOnly).some(x => x.id === 'single-db-lat-raise'));
assert.ok(!movementFamilyOptions(EX_BY_ID['lat-raise'], dbOnly).some(x => x.id === 'seated-db-lat-raise'));
const gym = expandEquipment(['dumbbell', 'bench', 'inclinebench', 'cable', 'bands', 'machinelatraise', 'pecdeck']);
for (const id of ids) {
    const root = id.includes('rear') ? 'rear-fly' : 'lat-raise';
    assert.ok(movementFamilyOptions(EX_BY_ID[root], gym).some(x => x.id === id));
    assert.ok(!movementFamilyOptions(EX_BY_ID[root], gym, [id]).some(x => x.id === id));
}
for (const id of ['band-lateral-raise', 'side-lying-raise', 'cable-behind-back-lateral'])
    assert.ok(movementFamilyOptions(EX_BY_ID['lat-raise'], gym).some(x => x.id === id), id + ' existing variation must be discoverable');
console.log('PASS M201: screenshot rep mismatch, immutable/manual resume, custom intensifier schedule, explicit off/drop/myo, and eight complete exercise variants.');


// B845: an incline dumbbell curl at 25 lb must be able to descend into
// realistic lighter increments, even with a stale coarse per-exercise notch.
// The actual gym rack, if supplied, is authoritative.
{
    const { nudgeWorkoutLoad, setGymLimits } = await import('../modules/training-domain/prescriptions.js');
    const { setLoadInc, setExLoadInc } = await import('../modules/training-domain/records.js');
    setLoadInc(0, null);
    setExLoadInc({});
    setGymLimits(null);
    assert.equal(nudgeWorkoutLoad(curl, 'lb', '25', -1), 20);
    assert.equal(nudgeWorkoutLoad(curl, 'lb', '20', -1), 15);
    assert.equal(nudgeWorkoutLoad(curl, 'lb', '20', 1), 25);
    setExLoadInc({ 'inc-curl': { v: 25, unit: 'lb' } });
    assert.equal(nudgeWorkoutLoad(curl, 'lb', '25', -1), 20,
        'a coarse historical machine notch must not force a 25 lb dumbbell to zero');
    setGymLimits({ limitUnit: 'lb', inventory: { dumbbell: [5, 10, 15, 20, 25, 30] } });
    assert.equal(nudgeWorkoutLoad(curl, 'lb', '25', -1), 20,
        'dumbbell stepper follows actual gym rack before a chosen increment');
    assert.equal(nudgeWorkoutLoad(curl, 'lb', '25', 1), 30);
    setGymLimits(null);
    setExLoadInc({});
}

// B845: Dynamic Double must raise ONLY the particular set that achieved the
// rep ceiling, not require all sets, not copy the increase to every set.
{
    const { evaluateWorkoutProgression } = await import('../modules/next-engine/performance.js');
    const exercise = { exerciseId: 'db_curl', name: 'Dumbbell Curl', role: 'hypertrophy_isolation',
        sets: 2, progressionStyle: 'dynamic',
        prescription: { reps: [8, 12], rir: [2, 2] } };
    const session = { exercises: [exercise] };
    const result = sets => evaluateWorkoutProgression(session, sets.map((setIndex) => setIndex), {})[0];
    const samples = (reps1, reps2, rir1 = 2, rir2 = 2) => [
        { exerciseId: 'db_curl', setIndex: 0, load: 25, reps: reps1, rir: rir1 },
        { exerciseId: 'db_curl', setIndex: 1, load: 25, reps: reps2, rir: rir2 }
    ];
    const earned = result(samples(12, 9));
    assert.equal(earned.action, 'increase_load');
    assert.deepEqual(earned.setTargets.map(x => [x.weight, x.reps]), [[30, 8], [25, 10]]);
    assert.deepEqual(earned.setTargets.map(x => x.earnedLoadIncrease), [true, false]);
    const incomplete = result(samples(12, 9).slice(0, 1));
    assert.notEqual(incomplete.action, 'increase_load', 'missing sets cannot earn extra load');
    const exhausted = result(samples(12, 9, 0, 2));
    assert.notEqual(exhausted.action, 'increase_load', 'effort below target does not earn a weight bump');
    const limited = evaluateWorkoutProgression(session, samples(12, 9), { badDay: true })[0];
    assert.notEqual(limited.action, 'increase_load', 'recovery-limited sessions hold loads');
    const standard = evaluateWorkoutProgression({ exercises: [{ ...exercise, progressionStyle: 'double' }] }, samples(12, 9))[0];
    assert.notEqual(standard.action, 'increase_load', 'classic double still waits for all working sets');
}
console.log('PASS B845: incline dumbbell weight step below 25, real rack priority, set-specific dynamic double with safety gates.');


// B1059: cable lateral raise history with a different prior evaluator count.
// A missing fourth per-set target cannot default to the representative 15 lb.
const { retainComparableWorkingTargets } = await import('../modules/training-domain/prescriptions.js');
const { buildRuntimeSetTargets, refreshPendingSetTargets } = await import('../modules/next-engine/workout-runtime.js');
const priorLateral = [{weight:15,reps:15},{weight:15,reps:12},{weight:10,reps:14},{weight:10,reps:13}];
const incompleteLateral = {action:'hold',weight:15,setTargets:[{weight:15,reps:15},{weight:15,reps:12},{weight:10,reps:14}]};
const correctedLateral = retainComparableWorkingTargets(incompleteLateral,priorLateral,4,[12,15],true);
assert.deepEqual(correctedLateral.setTargets.map(t=>[t.weight,t.reps]),[[15,15],[15,12],[10,14],[10,13]]);
assert.equal(retainComparableWorkingTargets(correctedLateral,priorLateral,4,[12,15],true),correctedLateral);
const earnedLateral = retainComparableWorkingTargets({action:'increase_load',weight:20,setTargets:[
    {weight:20,reps:12,previousLoad:15,earnedLoadIncrease:true},
    {weight:15,reps:12}, {weight:10,reps:14}, {weight:15,reps:12,previousLoad:10,earnedLoadIncrease:true}
]},priorLateral,4,[12,15],true);
assert.deepEqual(earnedLateral.setTargets.map(t=>t.weight),[20,15,10,10]);
assert.equal(retainComparableWorkingTargets({...incompleteLateral,referenceOnly:true},priorLateral,4,[12,15],true).setTargets.length,3);
const three = retainComparableWorkingTargets(incompleteLateral,priorLateral,3,[12,15],true);
assert.equal(three.setTargets.length,3,'fresh issue uses current Plan count');
const runtimeLateral = buildRuntimeSetTargets({exerciseId:'cable_lateral_raise',cell:{sets:4,reps:[12,15],rir:[2,2],role:'hypertrophy_isolation'},workingLoad:15,setTargets:correctedLateral.setTargets,snapLoad:w=>w,includeWarmups:false});
assert.deepEqual(runtimeLateral.map(t=>t.weight),[15,15,10,10]);
const issuedRows=priorLateral.map((prior,index)=>{
    const weight=index===3?15:prior.weight, reps=index===3?12:prior.reps;
    return {weight:String(weight),reps:String(reps),auto:true,valueOwner:'prescription',done:false,
        target:{w:String(weight),reps:'12-15',prefillReps:String(reps),decisionTrace:{
            schemaVersion:1,programId:'p',dayId:'d',exerciseId:'cable-lat-raise',slot:1,weekIndex:3,
            workingSetIndex:index,issuedWorkingSetCount:4,ownership:'prescription',
            source:{load:prior.weight,reps:prior.reps,unit:'lb'},
            evaluation:{action:'hold',reasonCode:'normal_progression'},
            target:{load:weight,reps,unit:'lb'}
        }}};
});
const restoredLateral=refreshPendingSetTargets(issuedRows,issuedRows.slice(0,3));
assert.equal(restoredLateral.length,4,'do not erase an issued fourth working set on resume');
assert.deepEqual(restoredLateral.map(t=>t.weight),['15','15','10','10']);
assert.equal(restoredLateral[3].target.decisionTrace.evaluation.correctedFromLoad,15);
assert.equal(refreshPendingSetTargets(restoredLateral,issuedRows.slice(0,3)),restoredLateral);
const loggedLateral=[...issuedRows.slice(0,3),{...issuedRows[3],done:true}];
assert.equal(refreshPendingSetTargets(loggedLateral,issuedRows.slice(0,3))[3],loggedLateral[3]);
const editedLateral=[...issuedRows.slice(0,3),{...issuedRows[3],weightOwner:'user'}];
assert.equal(refreshPendingSetTargets(editedLateral,issuedRows.slice(0,3))[3],editedLateral[3]);
console.log('PASS B1059: positional mixed-load hold, verified earned increment, count mismatch and safe restored issued row repair.');
