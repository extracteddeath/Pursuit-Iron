import assert from 'node:assert/strict';
import fs from 'node:fs';
import { THEMES } from '../modules/App.js';
import { EXERCISES, EX_BY_ID, TEMPLATES, templateConfig, ALL_EQUIP_IDS, sessionSuggestion, prescribeSets, liftBests, weeklyRecap, displayDayName } from '../modules/engine-api.js';

import { generateNextProgramForShell, getNextShellCell } from '../modules/next-engine/app-shell-adapter.js';
import { nextWorkoutSuggestionForShell, nextWorkoutSuggestionFromPerformedShell } from '../modules/next-engine/workout-history-adapter.js';
import { normalizeLoadingInventory, loadingInventoryInUnit, defaultLoadingInventoryFor, nextAvailableLoad } from '../modules/next-engine/loading.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { exerciseEconomyCluster } from '../modules/next-engine/exercise-economy.js';
import { primaryMuscle } from '../modules/next-engine/realizer-ranking.js';
import { consolidateRedundantAccessoryFamilies } from '../modules/next-engine/realizer-fragment-repair.js';

const kg = normalizeLoadingInventory({ unit: ' KG ' });
assert.equal(kg.unit, 'kg'); assert.equal(kg.barbell.barWeight, 20);
assert.equal(kg.dumbbells.availablePerHand[0], 2.5); assert.equal(kg.machine.increment, 2.5);
assert.equal(nextAvailableLoad('back_squat', 60, { unit: 'kg' }, ['barbell', 'rack']), 62.5);
assert.deepEqual(normalizeLoadingInventory({ dumbbells: { availablePerHand: [] } }).dumbbells.availablePerHand, []);
assert.deepEqual(normalizeLoadingInventory({ barbell: { platePairs: 'invalid' } }).barbell.platePairs, []);
const inventory = defaultLoadingInventoryFor('lb'); inventory.barbell.barWeight = 35;
inventory.dumbbells.availablePerHand = []; inventory.smith.availableLoads = [15, 25, 35];
inventory.exerciseOverrides = { barbell_bench: { mode: 'barbell_total', availableLoads: [35, 55] } };
const beforeInventory = JSON.stringify(inventory), converted = loadingInventoryInUnit(inventory, 'kg', { nativeDefaults: true });
assert.equal(converted.barbell.barWeight, 15.88); assert.deepEqual(converted.dumbbells.availablePerHand, []);
assert.deepEqual(converted.smith.availableLoads, [6.8, 11.34, 15.88]);
assert.deepEqual(converted.exerciseOverrides.barbell_bench.availableLoads, [15.88, 24.95]);
assert.equal(JSON.stringify(inventory), beforeInventory);
assert.equal(loadingInventoryInUnit(defaultLoadingInventoryFor('lb'), 'kg', { nativeDefaults: true }).barbell.barWeight, 20);

// Reproduce a saved unit-less template, not only a freshly corrected kg program.
const template = TEMPLATES.find(t => t.id === 'p531');
const build = config => generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 218, makeId: () => 'b860-template' }).program;
const p = build(templateConfig(template, ALL_EQUIP_IDS)), day = p.days[0], slot = day.primaryIndex;
const id = day.exercises[slot], cell = getNextShellCell(p, day, slot, 1);
const reps = Number(String(cell.reps).split('-').pop()), rir = Number(String(cell.rir).split('-')[0]);
const perf = { [id]: { weight: 27.5, reps, sets: Array.from({ length: Number(cell.sets) }, () => ({ w: 27.5, r: reps, rir, tr: rir, rirReported: true, done: true })) } };
const history = [{ id: 'kg-log', date: 1, programId: p.id, dayId: day.id, weekIndex: 1, unit: 'kg', perf }];
const beforeProgram = JSON.stringify(p);
for (const unit of ['kg', undefined]) {
    const suggestion = nextWorkoutSuggestionForShell(p, history, EXERCISES, day, slot, 1, unit);
    assert.equal(suggestion.weight, 30); assert.equal(suggestion.unit, 'kg'); assert.match(suggestion.reason, /27\.5 kg to 30 kg/);
}
assert.equal(sessionSuggestion(p, day, slot, null, 'kg', 1, history).weight, 30);
assert.equal(nextWorkoutSuggestionFromPerformedShell(p, EXERCISES, day, slot, 1, perf, 'kg').weight, 30);
const warmups = prescribeSets(p, day, EX_BY_ID[id], slot, 1, 'kg', { weight: 60 }, null, {}, history, true).filter(s => s.warm).map(s => Number(s.weight));
assert.deepEqual(warmups, [30, 40, 50]);
assert.equal(JSON.stringify(p), beforeProgram, 'saved templates are repaired at read time');
const fresh = build({ ...templateConfig(template, ALL_EQUIP_IDS), unit: 'kg' });
assert.equal(fresh.nextEngine.request.equipment.loading.barbell.barWeight, 20);

const now = Date.now(), DAY = 86400000;
const log = (id, ago, unit, w, r = 5, extra = {}) => ({ id, date: now - ago * DAY, unit, programId: 'pr', setsDone: 1,
    perf: { 'bb-bench': { weight: w, reps: r, sets: [{ w, r, done: true }] } }, ...extra });
const mixed = [log('new', 1, 'lb', 250), log('old', 20, 'kg', 120)];
assert.equal(weeklyRecap(mixed).prs, 0); assert.equal(weeklyRecap(mixed, 'lb').prs, 0);
assert.equal(weeklyRecap([log('new', 1, 'lb', 275), mixed[1]]).prs, 1);
const best = liftBests([log('old', 2, 'lb', 100, 8, { perf: { 'bb-bench': { weight: 100, reps: 7, sets: [8, 8, 7].map(r => ({ w: 100, r })) } } })], 'lb')['bb-bench'];
assert.equal(best.e1rm, 127, 'finish compares actual best, not the 100x7 summary');
assert.equal(liftBests([log('first', 1, 'lb', 100)], 'lb', h => h.id !== 'first')['bb-bench'], undefined);
assert.equal(liftBests([log('a', 1, 'lb', 200, 5, { perf: { 'bb-bench': { sets: [{ w: 300, r: 8, sub: true }, { w: 500, r: 8, warm: true }, { w: 500, r: 8, done: false }] } } })], 'lb')['bb-bench'], undefined);
assert.equal(liftBests([log('same', 1, 'lb', 300), log('same', 1, 'lb', 100, 5, { updatedAt: now })], 'lb')['bb-bench'].w, 100, 'latest correction wins');

// Two distinct redundant families formerly reused stale positions after the first filter.
const families = new Map();
for (const def of EXERCISE_MAP.values()) {
    if (def.flags.compound) continue;
    const cluster = exerciseEconomyCluster(def), muscle = primaryMuscle(def);
    if (!cluster || !muscle) continue;
    const key = cluster + '|' + muscle; const rows = families.get(key) || [];
    rows.push(def); families.set(key, rows);
}
const pairs = [...families.values()].filter(g => g.length >= 2).slice(0, 2);
assert.equal(pairs.length, 2);
const entry = def => ({ exerciseId: def.id, name: def.name, role: 'hypertrophy_isolation', sets: 2,
    prescription: { reps: [8, 12], rir: [1, 3], restSeconds: 90 } });
const realized = [{ id: 'double-family', day: 'monday', intent: 'hypertrophy_full', exercises: pairs.flatMap(g => g.slice(0, 2).map(entry)), maxMinutes: 90 }];
consolidateRedundantAccessoryFamilies({ exerciseMap: EXERCISE_MAP, realized, request: { goal: { musclePriorities: {} }, restrictions: { allowSupersets: false } } });
assert.equal(realized[0].exercises.length, 2);
assert.equal(realized[0].exercises.reduce((n, e) => n + e.sets, 0), 8);
assert.deepEqual(realized[0].exercises.map(e => primaryMuscle(EXERCISE_MAP.get(e.exerciseId))).sort(), pairs.map(g => primaryMuscle(g[0])).sort());
assert.ok(realized[0].estimatedMinutes > 0);

assert.equal(displayDayName('Full Body — Bench focus', { days: [{ label: 'Full Body — Bench focus' }, { label: 'Full Body — Squat focus' }] }), 'Bench focus');
assert.equal(displayDayName('Push — Chest', { days: [{ label: 'Push — Chest' }, { label: 'Pull — Back' }] }), 'Push — Chest');
assert.equal(displayDayName('Bench — Bench Focus', {}), 'Bench Focus');
assert.equal(displayDayName('Upper A', {}), 'Upper A');
const lum = hex => hex.slice(1).match(/../g).map(v => parseInt(v,16)/255).map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4).reduce((n,v,i)=>n+v*[.2126,.7152,.0722][i],0);
for (const theme of Object.values(THEMES).filter(t => t.base === 'dark')) for (const ink of ['text', 'muted', 'faint']) {
    const a = lum(theme.palette[ink]), b = lum(theme.palette.feature);
    assert.ok((Math.max(a,b)+.05)/(Math.min(a,b)+.05) >= 4.5, theme.name+' '+ink+' feature contrast');
}
const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
const themeCss = fs.readFileSync(new URL('../app.css', import.meta.url), 'utf8');
assert.match(themeCss, /--pi-color-feature:light-dark\(var\(--pi-m3e-primary-container\),color-mix\(in srgb,var\(--wpb-theme-card\) 84%,var\(--wpb-theme-accent\) 16%\)\)/,
    'R78 derives strong containers from the selected card and accent instead of the legacy saturated feature');
assert.match(app, /className: "wpb-program-amplifier", style: \{ margin: "2px 0 10px"/,
    'R78 amplifier attaches to exercise slot without the old 34px horizontal offset');
assert.doesNotMatch(app, /margin: "-3px 8px 8px 34px"/,
    'the legacy amplifier stagger must not return');
assert.match(app, /className: "wpb-training-calendar"/,
    'R78 calendar remains an explicit always-visible history surface');
assert.match(app, /className: "wpb-progress-disclosure wpb-milestones-disclosure"/,
    'R78 milestones own their independent native collapsed disclosure');
assert.doesNotMatch(app, /children: "Training calendar and milestones"/,
    'R78 must not fold the exact-date calendar into achievements');
assert.doesNotMatch(app, /title: "Workouts this week"/,
    'R78 removes the unexplained extra calendar column');
assert.match(themeCss, /\.wpb-program-amplifier>button:focus-visible/,
    'amplifier disclosure keeps a visible focus ring');
assert.ok(app.includes('completedSetPR(set, prBaseline[data[ei].id], e1rm)'), 'PR gate belongs to the user completion gesture');
assert.ok(!app.includes('prBaseline[e.id] > 0 && est > prBaseline[e.id]'), 'Never fire PR from React state updater');
assert.match(app, /const prior = priorBests\[e.id\]\?\.e1rm \?\? null/);
assert.match(app, /const nextHistory = history\.map[\s\S]{0,160}\.sort/);
console.log('PASS Build 860: saved kg template and warm-ups; custom/empty inventory safety; normalized PR revisions/units/working sets; two-family dose conservation; useful day titles and 20-theme feature ink.');
