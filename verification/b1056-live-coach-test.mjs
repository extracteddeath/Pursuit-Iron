import assert from 'node:assert/strict';
import fs from 'node:fs';
import { liveCoachForContext, workoutPlanCoachFact } from '../modules/training-domain/analytics.js';
import { EX_BY_ID } from '../modules/training-domain/catalog.js';

// B1056: the live Coach only speaks when it changes what the lifter does now.
const bench = EX_BY_ID['bb-bench'];
assert.ok(bench, 'fixture exercise exists');
const sets = (rows) => ({ id: 'bb-bench', sets: rows.map(([weight, reps, done = false]) => ({ weight: String(weight), reps: String(reps), done })) });

// A planned set that beats the best estimated 1RM is named, by set, from the sets as entered.
const pr = liveCoachForContext([], sets([[150, 5], [200, 5], [150, 5]]), bench, 0, { best: 228, unit: 'lb' });
assert.equal(pr.length, 1);
assert.equal(pr[0].context, 'pr');
assert.match(pr[0].short, /^Set 2: 200 × 5 is a new best$/);
assert.match(pr[0].evidence, /228 lb/);
// It stays visible mid-exercise while the record set is still ahead...
assert.equal(liveCoachForContext([], sets([[150, 5, true], [200, 5]]), bench, 1, { best: 228, unit: 'lb' })[0]?.context, 'pr');
// ...but not on a deload, nor when nothing planned beats the best.
assert.equal(liveCoachForContext([], sets([[150, 5], [200, 5]]), bench, 0, { best: 228, unit: 'lb', deload: true }).length, 0);
assert.equal(liveCoachForContext([], sets([[150, 5], [160, 5]]), bench, 0, { best: 228, unit: 'lb' }).length, 0);

// Fatigue or low recovery outranks a record attempt and silences it.
const tired = [{ key: 'readiness:chest', part: bench.part, priority: 92, short: 'Chest still recovering' }];
const cautious = liveCoachForContext(tired, sets([[150, 5], [200, 5]]), bench, 0, { best: 228, unit: 'lb' });
assert.equal(cautious[0]?.key, 'readiness:chest');
assert.equal(liveCoachForContext(tired, sets([[150, 5, true], [200, 5]]), bench, 1, { best: 228, unit: 'lb' }).length, 0);

// Facts marked live:false (true but not actionable) never reach the live Coach.
const quiet = [{ key: 'rep-curve:bb-bench', exId: 'bb-bench', live: false, priority: 70, short: 'use the listed reps' }];
assert.equal(liveCoachForContext(quiet, sets([[150, 5]]), bench, 0, {}).length, 0);

// Phase slogans stay off the live Coach; recovery days still reach it.
const cfg = { config: { goal: 'muscle', weeks: 4 }, days: [], nextEngine: { phase: 'accumulation' } };
assert.equal(workoutPlanCoachFact(cfg, {}, 1)?.live, false);
assert.notEqual(workoutPlanCoachFact({ ...cfg, nextEngine: { phase: 'deload' } }, {}, 1)?.live, false);

// The rep-curve fact is not live, and exercise-scoped copy no longer repeats the exercise name.
const src = fs.readFileSync(new URL('../modules/training-domain/analytics.js', import.meta.url), 'utf8');
assert.ok(/key: `rep-curve:\$\{primary\.id\}`,\s*exId: primary\.id,\s*\/\/[^\n]*\n\s*live: false,/.test(src), 'rep-curve stays out of the live Coach');
assert.ok(!src.includes("short: `${ex.name}: use today's recalculated load`"), 'plateau copy is an action, not a name');
console.log('PASS B1056 live Coach: PR in reach from entered sets, deload/no-PR quiet, non-actionable facts excluded.');
