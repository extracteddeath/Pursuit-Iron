import assert from 'node:assert/strict';
import fs from 'node:fs';
import { weekStrip, recapShareText } from '../modules/home-week.js';
import { weeklyRecap } from '../modules/engine-api.js';

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
console.log('PASS prototype features: week strip, streak, 7-day recap and share text');
