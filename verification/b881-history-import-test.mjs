// Build 881 — import workout history from Strong and Hevy CSV exports.
// The converter emits a backup-shaped store that travels the existing restore path (mergeStores,
// preview, Undo). These checks pin the conversion rules and the merge properties that make importing
// safe: deterministic ids, idempotent re-import, corrections that survive, and no history lost.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import * as domain from '../modules/training-domain.js';
import { EXERCISES } from '../modules/App.js';

const { mergeStores, STORE_VERSION } = domain;
const workoutCsvToStore = domain.workoutCsvToStore ?? (() => ({ ok: false, msg: 'missing' }));
const detectWorkoutCsv = domain.detectWorkoutCsv ?? (() => null);
const catalog = EXERCISES;

const HEVY = [
    'title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Bench Press (Barbell),,"Elbows in, slow",0,warmup,135,8,,,',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Bench Press (Barbell),,"Elbows in, slow",1,normal,225,5,,,8',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Bench Press (Barbell),,"Elbows in, slow",2,normal,225,5,,,9',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Bench Press (Barbell),,"Elbows in, slow",3,failure,225,4,,,',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Lateral Raise (Dumbbell),0,,0,normal,25,12,,,',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Lateral Raise (Dumbbell),0,,1,dropset,15,10,,,',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Triceps Pushdown (Cable - Straight Bar),0,,0,normal,60,12,,,',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Bench Press (Barbell),,,0,normal,185,10,,,',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Clean and Jerk (Barbell),,,0,normal,135,3,,,',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Meadows Landmine Row Variation,,,0,normal,70,10,,,',
    '"Push, Heavy","15 Jul 2026, 09:52","15 Jul 2026, 10:47","Felt good",Treadmill,,,0,normal,,,1.5,900,',
    'Legs,"17 Jul 2026, 18:05","17 Jul 2026, 19:10",,Squat (Barbell),,,0,normal,315,5,,,7',
    'Legs,"17 Jul 2026, 18:05","17 Jul 2026, 19:10",,Squat (Barbell),,,1,normal,315,5,,,'
].join('\n');
const STRONG_COMMA = [
    'Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE',
    '2026-03-02 07:10:00,Upper A,1h 5m,Bench Press (Barbell),W,60,10,0,0,,,',
    '2026-03-02 07:10:00,Upper A,1h 5m,Bench Press (Barbell),1,100,5,0,0,,,8',
    '2026-03-02 07:10:00,Upper A,1h 5m,Bench Press (Barbell),2,100,5,0,0,,,',
    '2026-03-02 07:10:00,Upper A,1h 5m,Lat Pulldown (Cable),1,70,10,0,0,,,',
    '2026-03-02 07:10:00,Upper A,1h 5m,Lat Pulldown (Cable),D,50,8,0,0,,,',
    '2026-03-04 07:00:00,Lower A,58m,Deadlift (Barbell),1,180,3,0,0,,,',
    '2026-03-04 07:00:00,Lower A,58m,Deadlift (Barbell),F,180,2,0,0,,,'
].join('\n');
const STRONG_SEMI = [
    'Date;Workout Name;Duration;Exercise Name;Set Order;Weight;Weight Unit;Reps;RPE;Distance;Distance Unit;Seconds;Notes;Workout Notes',
    '2026-05-10 17:30:00;Push;45m;Overhead Press (Barbell);1;52,5;kg;6;;;;;;',
    '2026-05-10 17:30:00;Push;45m;Overhead Press (Barbell);2;52,5;kg;5;;;;;;'
].join('\r\n');

const ids = rows => rows.map(r => r.id).sort();
const only = (store, exId) => store.history.flatMap(h => h.perf[exId] ? [h.perf[exId]] : []);

// 1. Format detection — and a JSON backup is never mistaken for a CSV.
assert.equal(detectWorkoutCsv(HEVY), 'hevy');
assert.equal(detectWorkoutCsv(STRONG_COMMA), 'strong');
assert.equal(detectWorkoutCsv(STRONG_SEMI), 'strong');
assert.equal(detectWorkoutCsv(JSON.stringify({ v: STORE_VERSION, history: [] })), null);
assert.equal(detectWorkoutCsv('a,b,c\n1,2,3'), null);
console.log('PASS 1 Strong (comma/semicolon) and Hevy exports are detected; JSON backups are not.');

// 2. Hevy conversion rules.
const hevy = workoutCsvToStore(HEVY, { catalog, unit: 'lb', now: 1 });
assert.ok(hevy.ok, hevy.msg);
assert.equal(hevy.summary.workouts, 2);
const push = hevy.store.history.find(h => h.dayLabel === 'Push, Heavy');
assert.ok(push, 'quoted title with a comma survives');
const bench = push.perf['bb-bench'];
assert.ok(bench, 'Bench Press (Barbell) resolved to the catalog barbell bench');
assert.deepEqual(bench.sets.map(s => [s.w, s.r]), [[225, 5], [225, 5], [225, 4], [185, 10]], 'warm-up excluded; the repeated exercise block is merged in order');
assert.equal(bench.sets[0].rir, 2); assert.equal(bench.sets[0].rirReported, true);
assert.equal(bench.sets[1].rir, 1);
assert.equal(bench.sets[2].rir, 0, 'a failure set is 0 RIR');
assert.equal(bench.sets[3].rir, undefined, 'no RPE logged means effort stays unreported, never invented');
assert.equal(bench.note, 'Elbows in, slow');
const lat = push.perf['lat-raise'];
assert.deepEqual(lat.sets[1], { w: 15, r: 10, sub: true, kind: 'drop' }, 'a Hevy drop set is stored as an extension, not a working set');
assert.ok(push.perf['straight-bar-pushdown'], 'straight-bar pushdown resolved');
assert.equal(push.durationMin, 55, 'duration from start/end');
assert.equal(hevy.summary.warmupsSkipped, 1); assert.equal(hevy.summary.cardioSkipped, 1);
assert.deepEqual(hevy.summary.unmatched, [{ name: 'Clean and Jerk (Barbell)', sets: 1 }], 'a lift with no inferable muscle is skipped and named');
const created = hevy.store.custom.find(c => c.name === 'Meadows Landmine Row Variation');
assert.ok(created && created.part === 'upper_back' && created.custom, 'an unknown row becomes a custom upper-back exercise');
assert.ok(push.perf[created.id], 'its sets are kept under the custom exercise');
assert.equal(new Date(push.date).getHours(), 9, 'local wall-clock start time');
console.log('PASS 2 Hevy: warm-ups, drop sets, failure, RPE→RIR, notes, quoting, repeats, custom and skipped lifts.');

// 3. Deterministic: converting twice produces identical ids and records.
const again = workoutCsvToStore(HEVY, { catalog, unit: 'lb', now: 999 });
assert.deepEqual(ids(again.store.history), ids(hevy.store.history));
assert.deepEqual(again.store.custom.map(c => c.id), hevy.store.custom.map(c => c.id));
console.log('PASS 3 ids are derived from the source rows, not from time or randomness.');

// 4. Strong without a unit: the caller's unit is assumed and reported; an override converts.
const strongLb = workoutCsvToStore(STRONG_COMMA, { catalog, unit: 'lb' });
assert.ok(strongLb.ok); assert.equal(strongLb.summary.unitAssumed, true); assert.equal(strongLb.summary.weightUnit, 'lb');
const strongKgFile = workoutCsvToStore(STRONG_COMMA, { catalog, unit: 'lb', weightUnit: 'kg' });
const upper = strongKgFile.store.history.find(h => h.dayLabel === 'Upper A');
assert.deepEqual(upper.perf['bb-bench'].sets.map(s => s.w), [220.5, 220.5], '100 kg recorded in an lb app is 220.5 lb');
assert.equal(upper.perf['lat-pulldown'].sets[1].sub, true, 'Strong "D" is a drop set');
const lower = strongKgFile.store.history.find(h => h.dayLabel === 'Lower A');
assert.equal(lower.perf.deadlift.sets[1].rir, 0, 'Strong "F" is a failure set');
assert.equal(upper.durationMin, 65); assert.equal(lower.durationMin, 58);
console.log('PASS 4 Strong: missing unit is asked about, kg→lb conversion, W/D/F set orders, durations.');

// 5. Semicolon export with decimal commas and a Weight Unit column.
const semi = workoutCsvToStore(STRONG_SEMI, { catalog, unit: 'kg' });
assert.ok(semi.ok); assert.equal(semi.summary.unitAssumed, false);
assert.deepEqual(semi.store.history[0].perf.ohp.sets.map(s => [s.w, s.r]), [[52.5, 6], [52.5, 5]]);
console.log('PASS 5 Strong 6.x semicolon export with decimal commas and per-row units.');

// 6. Merging into a real store: existing history kept, re-import idempotent, corrections survive.
const existing = { id: 'mine-1', date: Date.UTC(2026, 6, 20), updatedAt: Date.UTC(2026, 6, 20), programId: 'p', dayId: 'd', unit: 'lb',
    perf: { 'bb-bench': { weight: 230, reps: 5, date: Date.UTC(2026, 6, 20), sets: [{ w: 230, r: 5 }] } } };
const live = { v: STORE_VERSION, savedAt: Date.now(), tombs: {}, history: [existing], saved: [], cycles: [], custom: [], unit: 'lb', perf: { 'bb-bench': existing.perf['bb-bench'] } };
const first = mergeStores(live, hevy.store);
assert.equal(first.stats.history, 2, 'both imported workouts were added');
assert.ok(first.data.history.some(h => h.id === 'mine-1'), 'existing history kept');
assert.equal(first.data.unit, 'lb', 'device settings are untouched by an import');
assert.equal(first.data.perf['bb-bench'].weight, 230, 'a newer session on this device still owns the performance mirror');
assert.equal(first.data.perf['back-squat'].weight, 315, 'a lift only the import has gets its latest imported performance');
const second = mergeStores(first.data, again.store);
assert.equal(second.stats.history, 0); assert.equal(second.data.history.length, first.data.history.length, 're-importing the same file adds nothing');
const editedId = push.id;
const corrected = { ...first.data, history: first.data.history.map(h => h.id === editedId ? { ...h, updatedAt: Date.now(), dayLabel: 'Push (fixed)' } : h) };
const third = mergeStores(corrected, again.store);
assert.equal(third.data.history.find(h => h.id === editedId).dayLabel, 'Push (fixed)', 'a later correction survives a re-import');
console.log('PASS 6 merge: nothing lost, settings untouched, idempotent re-import, corrections survive.');

// 7. The app routes CSV through the existing restore path, with the unit question and Undo.
const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
assert.match(app, /const csvSource = !replace && detectWorkoutCsv\(json\);/, 'importData detects CSV before backup parsing');
assert.match(app, /workoutCsvToStore\(json, \{ catalog, unit, weightUnit: csvWeightUnit \}\)/);
assert.ok(app.includes('accept: "application/json,.json,.txt,.csv,text/csv,text/comma-separated-values"'), 'the restore picker accepts CSV');
assert.ok(app.includes('onPreviewImport(importText, { csvWeightUnit: csvUnit })') && app.includes('onImport(importText, { csvWeightUnit: csvUnit })'), 'preview and import use the same unit choice');
console.log('PASS 7 the restore panel previews, imports and undoes CSV history through one path.');
