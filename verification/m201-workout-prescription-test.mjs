import assert from 'node:assert/strict';
import { EXERCISES, EX_BY_ID, computeCell, prescribeSets, sessionSuggestion, movementFamilyOptions,
    VARIANT_PRESENTATION, AttachmentGlyph, figurePose, subRegionOf, expandEquipment } from '../modules/App.js';
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
assert.equal(sug.target, 12);
assert.match(sug.reason, /toward 12 reps/);
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
