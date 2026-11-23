import assert from 'node:assert/strict';
import fs from 'node:fs';
import { weekStrip, recapShareText } from '../modules/home-week.js';
import { weeklyRecap, EX_BY_ID, secondaryOf, sessionPrescriptionRows } from '../modules/engine-api.js';
import { encodeShareCode, decodeShareCode, isShareCode } from '../modules/program-code.js';
import { painAction, painMatches } from '../modules/pain-check.js';
import { adjustInjuredExercises } from '../modules/workout-context.js';
import { routineList, routineWhen } from '../modules/routine-picker.js';

// Features carried over from the M3 prototype: the Home week strip and the rolling 7-day recap.
const D = 86400000;
// Friday 20 Nov 2026, noon local: Monday is the 16th.
const now = new Date(2026, 10, 20, 12).getTime();
const at = (y, m, d, h = 18) => new Date(y, m, d, h).getTime();
const history = [at(2026, 10, 18), at(2026, 10, 20, 9), at(2026, 10, 11), at(2026, 10, 3), at(2026, 9, 20)].map(date => ({ date }));
const strip = weekStrip(history, { schedule: { 1: 'a', 3: 'b', 5: 'a' } }, now);
assert.deepEqual(strip.days.map(d => d.date), [16, 17, 18, 19, 20, 21, 22], 'Strip runs Monday to Sunday of this week');
assert.deepEqual(strip.days.map(d => d.trained), [false, false, true, false, true, false, false]);
assert.deepEqual(strip.days.map(d => d.scheduled), [true, false, true, false, true, false, false], 'Schedule keys are JS weekdays (0 = Sunday)');
assert.equal(strip.days.findIndex(d => d.today), 4);
assert.deepEqual(strip.days.map(d => d.future), [false, false, false, false, false, true, true]);
assert.equal(strip.streak, 3, 'Weeks of 16, 9 and 2 Nov are consecutive; the October week is not');
assert.equal(strip.atRisk, false);
// A week still in progress with nothing logged keeps the streak but flags it.
const quiet = weekStrip(history.slice(2), null, now);
assert.equal(quiet.streak, 2); assert.equal(quiet.atRisk, true);
assert.equal(weekStrip([], null, now).streak, 0);
assert.equal(weekStrip([{ date: now + 5 * D }], null, now).streak, 0, 'Future-dated entries never count');

const r = { count: 2, sets: 12, vol: 8910, prs: 1, minutes: 104, unit: 'lb',
  best: { name: 'Barbell Bench Press', w: 190, r: 6 }, topMuscle: ['chest', 6] };
assert.equal(recapShareText(r, 3),
  'My last 7 days on Pursuit Iron: 2 sessions of 3 · 12 sets · 8,910 lb volume · 1 PR · 1h 44m trained.\nBest set: Barbell Bench Press 190 lb × 6.\nMost trained: Chest.');

// weeklyRecap owns minutes and the best set so the card and the share text agree.
const T = Date.now();
const rec = weeklyRecap([
  { date: T - D, unit: 'lb', durationMin: 50, setsDone: 2, perf: { 'bb-bench': { sets: [{ w: 185, r: 5, done: true }, { w: 205, r: 3, done: true, rir: 1 }] } } },
  { date: T - 2 * D, unit: 'kg', durationMin: 40, setsDone: 1, perf: { 'bb-bench': { sets: [{ w: 100, r: 3, done: true }] } } }
], 'lb');
assert.equal(rec.minutes, 90);
assert.equal(rec.best.id, 'bb-bench');
assert.ok(Math.abs(rec.best.w - 220.46) < 0.1, 'Best set is compared in the display unit: ' + rec.best.w);
assert.equal(rec.unit, 'lb');

const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
assert.ok(app.includes("import { HomeWeekStrip, HomeRecapCard as WeeklyRecapCard } from './home-week.js';"));
assert.ok(!app.includes('function WeeklyRecapCard('), 'One weekly card owner');

// Pain check: severity picks the action and the region picks the lifts.
assert.equal(painAction('twinge'), 'monitor'); assert.equal(painAction('sore'), 'reduce'); assert.equal(painAction('sharp'), 'skip');
const lookup = id => EX_BY_ID[id] ? { ex: EX_BY_ID[id], secondary: secondaryOf(EX_BY_ID[id]) } : null;
const rows = ['back-squat', 'bb-bench', 'ohp', 'lat-raise'].map(id => ({ id, sets: [{ warm: true, done: true }, { done: true }, { done: false }, { done: false }, { done: false }] }));
const knee = painMatches('knee', rows, lookup).map(m => m.id);
assert.ok(knee.includes('back-squat') && !knee.includes('ohp') && !knee.includes('lat-raise'), 'Knee pain matches squats only: ' + knee);
const shoulder = painMatches('shoulder', rows, lookup);
assert.ok(shoulder.some(m => m.id === 'ohp') && shoulder.some(m => m.id === 'lat-raise') && !shoulder.some(m => m.id === 'back-squat'));
assert.deepEqual(shoulder.find(m => m.id === 'ohp'), { id: 'ohp', name: EX_BY_ID.ohp.name, logged: 1, pending: 3 }, 'Warm-ups are not counted');
const halved = adjustInjuredExercises(rows, ['ohp'], 'Shoulder', 'reduce').find(r => r.id === 'ohp');
assert.equal(halved.sets.filter(x => !x.done).length, 2, 'Sore keeps half the pending sets, rounded up');
assert.equal(halved.injuryAdjusted, 'Shoulder', 'The banner names the area the lifter chose');
const painApp = app.slice(app.indexOf('const applyPain = '), app.indexOf('const removeExerciseFromSession'));
assert.ok(painApp.includes('action === "monitor" || !ids.length ? data') && painApp.includes('setInjuryReports(prev => [...prev, { part, region, level, action'),
  'A twinge is recorded without changing the workout; every check joins the session injury reports');
assert.ok(painApp.includes('undo: () => { setData(originals)'), 'Every pain change offers undo');
assert.ok(app.includes('{ k: "pain", label: "Pain check", icon: Activity, on: () => setPainOpen(true) }'), 'Pain check sits in the exercise tool row');
assert.ok(app.includes('e.injuryAdjusted && _jsxs("div", { className: "wpb-pain-banner"'), 'Adjusted lifts show a protection banner with Swap');

// Routines: saved freestyle workouts, most recently trained first, one tap to start.
const quick = (id, ex, createdAt) => ({ id, name: id, quick: true, createdAt, days: [{ id: 'd', exercises: ex }] });
const list = routineList([quick('a', ['bb-bench', 'not-an-exercise'], 1), { id: 'p', days: [{ exercises: ['ohp'] }] }, quick('b', ['ohp'], 2), quick('empty', [], 3)],
  [{ programId: 'a', date: 50 }, { programId: 'a', date: 70 }]);
assert.deepEqual(list.map(r => r.id), ['a', 'b'], 'Only quick programs with lifts, last trained first');
assert.deepEqual(list[0].lifts, [EX_BY_ID['bb-bench'].name], 'Unknown ids are dropped from the lift list');
assert.equal(list[0].lastAt, 70);
assert.equal(routineWhen(0), 'Not trained yet'); assert.equal(routineWhen(now - 2.5 * D, now), 'Trained 2 days ago'); assert.equal(routineWhen(now - D, now), 'Trained yesterday');
assert.ok(app.includes('onStartRoutine: p => p.days?.[0] && startSession(p.days[0], 1, p)'), 'A routine starts as its own saved program');
assert.ok(app.includes('onClick: routines.length && onStartRoutine ? () => setRoutinePick(true) : onQuick'), 'Freestyle opens the routine sheet only when routines exist');
assert.ok(app.includes('routinePick && createPortal(_jsx(RoutinePickerSheet'), 'The sheet escapes Home\'s stacking context');

// Share codes carry the program itself and round-trip exactly, compressed or not.
const lifts = ['bb-bench', 'ohp', 'lat-raise', 'inc-curl', 'back-squat'];
const shared = { id: 'x', name: 'Big Five', custom: true, weeks: 6,
  config: { goal: 'strength', experience: 'advanced', progression: 'double', weeks: 6, session: 's90', deload: true },
  days: [1, 2, 3].map(i => ({ id: 'd' + i, label: 'Day ' + i, primaryIndex: 1, exercises: lifts })),
  overrides: { 'd1:0': { sets: 5, reps: '3-5', rir: '1', rest: 240 } }, ss: { 'd1:2': true } };
const cells = p => p.days.map(d => sessionPrescriptionRows(p, d, 1).map(x => [x.id, x.cell.sets, x.cell.range, x.cell.rir, x.cell.rest]));
for (const compress of [true, false]) {
  const { code, skipped } = await encodeShareCode(shared, 1, { compress });
  assert.ok(isShareCode(code) && skipped === 0);
  const back = await decodeShareCode('  ' + code.slice(0, 30) + '\n' + code.slice(30) + ' ');
  assert.ok(back.ok, back.reason);
  assert.deepEqual(cells(back.program), cells(shared), 'Every set, rep range, effort and rest survives (compress=' + compress + ')');
  assert.equal(back.program.custom, true); assert.equal(back.program.weeks, 6);
  assert.equal(back.program.config.goal, 'strength'); assert.equal(back.program.config.session, 's90'); assert.equal(back.program.config.deload, true);
  assert.equal(back.program.days[0].primaryIndex, 1);
  assert.deepEqual(Object.keys(back.program.ss), [back.program.days[0].id + ':2'], 'Supersets keep their pairing');
  assert.notEqual(back.program.id, shared.id, 'An import is a new program');
  assert.match((await decodeShareCode(code.slice(0, -1) + (code.at(-1) === 'a' ? 'b' : 'a'))).reason, /typo/);
  assert.match((await decodeShareCode(code.slice(0, code.length - 12) + code.slice(-5))).reason, /typo/, 'A truncated paste is refused');
}
const short = (await encodeShareCode(shared, 1)).code;
assert.ok(short.length < 400, 'Compressed codes stay short enough to text: ' + short.length);
// The sender's own custom exercises can't travel; the count is reported, never silently dropped.
EX_BY_ID['custom-zz'] = { id: 'custom-zz', name: 'Mine', part: 'chest', custom: true };
const withCustom = { ...shared, days: [{ id: 'd1', label: 'A', primaryIndex: 0, exercises: ['custom-zz', 'bb-bench'] }], overrides: {}, ss: {} };
const partial = await encodeShareCode(withCustom, 1);
delete EX_BY_ID['custom-zz'];
assert.equal(partial.skipped, 1);
assert.deepEqual((await decodeShareCode(partial.code)).program.days[0].exercises, ['bb-bench']);
assert.match((await decodeShareCode('PI-6RP4-C60F')).reason, /PI2-/);
assert.ok(app.includes('encodeShareCode(program, weekIndex).then('), 'The share sheet mints a PI2 code for the week on screen');
assert.ok(app.includes('if (isShareCode(text)) {') && app.includes('setSaved(prev => [...prev, r.program]);'), 'Pasting a code adds the program');
console.log('PASS prototype features: week strip, streak, 7-day recap, share text, pain check, routines and share codes');
