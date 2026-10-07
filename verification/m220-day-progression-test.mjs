import assert from 'node:assert/strict';
import { sessionSuggestion, lastDayPerf, dayPerfFor, prescribeSets, EX_BY_ID } from '../modules/App.js';
import { refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
const id = 'seated-calf';
export const program = { id: 'm220-custom', custom: true, weeks: 10, config: { unit: 'lb', weeks: 10, progression: 'manual', goal: 'both', experience: 'intermediate', deload: false },
    days: [{ id: 'lower', label: 'Lower', primaryIndex: -1, exercises: [id] }, { id: 'legs', label: 'Legs', primaryIndex: -1, exercises: [id] }],
    overrides: { 'lower:0': { sets: 5, reps: '12-20', rir: '2', progressionStyle: 'double' }, 'legs:0': { sets: 3, reps: '12-20', rir: '2', progressionStyle: 'double' } } };
const entry = (dayId, date, weight, reps, programId = program.id) => ({ id: `${dayId}-${date}`, programId, dayId, date, unit: 'lb', weekIndex: 1,
    perf: { [id]: { weight, reps: reps.at(-1), sets: reps.map(r => ({ w: weight, r, rir: 2, tr: 2, done: true })) } } });
export const history = [entry('legs', Date.UTC(2026,8,28), 210, [20,17,15]), entry('lower', Date.UTC(2026,8,23), 205, [20,16,16,12,12])];
const suggest = (day, hs = history) => sessionSuggestion(program, day, 0, null, 'lb', 1, hs);
const lower = suggest(program.days[0]);
assert.equal(lower.weight, 205, 'Lower must use its five-set session, not newer three-set Legs');
assert.equal(lower.action, 'add_reps');
assert.equal(lower.last, history[1].perf[id]);
assert.equal(lastDayPerf(program.days[0], {}, history, program)[id], lower.last, 'LAST and advice share the same raw session');
const rows = prescribeSets(program, program.days[0], EX_BY_ID[id], 0, 1, 'lb', lower, {}, {}, history, false);
assert.equal(rows.length, 5); assert.ok(rows.every(r => r.weight === '205' && r.target.reps === '12-20'));
assert.equal(suggest(program.days[1]).weight, 210, 'Legs retains its own working load');
const cappedOther = [entry('legs', Date.UTC(2026,8,29), 210, [20,20,20]), ...history];
assert.equal(suggest(program.days[0], cappedOther).weight, 205, 'a capped three-set session cannot advance the five-set day');
const cappedLower = [entry('lower', Date.UTC(2026,9,1), 205, [20,20,20,20,20]), ...history];
assert.equal(suggest(program.days[0], cappedLower).weight, 210, 'all five sets at the ceiling earn the next load');
assert.equal(suggest(program.days[0], [entry('lower', Date.UTC(2026,9,1), 205, [20,20,20]), ...history]).weight, 205, 'three sets do not satisfy a five-set prescription');
const fallback = suggest(program.days[0], [history[0]]);
assert.equal(fallback.weight, 210, 'a never-trained day may initialize from the same program');
assert.equal(lastDayPerf(program.days[0], {}, [history[0]], program)[id], undefined, 'M226: another day is a load reference, not LAST for this day');
assert.equal(fallback.last, history[0].perf[id]);
assert.equal(fallback.referenceOnly, true);
assert.equal(fallback.action, 'initial', 'a cross-day reference cannot earn progression');
const unrelated = entry('lower', Date.UTC(2026,9,2), 400, [20,20,20,20,20], 'other-program');
assert.equal(suggest(program.days[0], [unrelated, ...history]).weight, 205, 'same day IDs in other programs cannot replace this program evidence');
assert.equal(lastDayPerf(program.days[0], {}, [unrelated, ...history], program)[id], lower.last);
assert.equal(suggest(program.days[0], [...history].reverse()).weight, 205, 'date ordering is independent of input order');
assert.equal(suggest(program.days[0], [entry('lower', Date.UTC(2026,9,1), 90, [20,20,20,20,20], 'other')]).weight, 90, 'M226: another program may initialize load but cannot earn an increase');
assert.deepEqual(history[1].perf[id].sets.map(s => s.w), [205,205,205,205,205], 'logged evidence is immutable');
console.log('PASS M220: screenshot Lower/Legs regression, separate day loads, set-count qualification, earned progression, scoped fallbacks, shared LAST/advice and immutable history.');

const automatic = { weight: '210', reps: '20', auto: true, done: false, valueOwner: 'prescription', target: { w: '210', reps: '12-20' } };
const pending = [automatic, { ...automatic, done: true }, { ...automatic, valueOwner: 'user', auto: false }, { ...automatic, weight: '212.5' }, { ...automatic, added: true }, { ...automatic, sub: true }];
const before = structuredClone(pending);
const refreshed = refreshPendingSetTargets(pending, [{...automatic,weight:'205',target:{...automatic.target,w:'205'}}]);
assert.equal(refreshed[0].weight, '205'); assert.equal(refreshed[0].target.w, '205');
for (let i = 1; i < pending.length; i++) assert.equal(refreshed[i], pending[i], 'completed, manual, edited, added and sub rows are protected');
assert.deepEqual(pending, before);
assert.equal(refreshPendingSetTargets(refreshed, [{...automatic,weight:'205',target:{...automatic.target,w:'205'}}]), refreshed, 'unchanged targets preserve identity');
console.log('PASS M220 recovery: stale automatic custom loads refresh; completed/manual/edited/added/sub values and original snapshot remain intact.');

// Generated programs already evaluate day-specific workouts, but their returned LAST/changed-week
// reference also has to come from that same day rather than a newer occurrence elsewhere.
const { default: fs } = await import('node:fs');
const { EXERCISES } = await import('../modules/App.js');
const { generateNextProgramForShell, markUserPrescriptionOverride } = await import('../modules/next-engine/app-shell-adapter.js');
const { nextWorkoutSuggestionForShell } = await import('../modules/next-engine/workout-history-adapter.js');
const fixture = JSON.parse(fs.readFileSync('verification/m205-generation-parity-results.json')).rows[3];
const generated = generateNextProgramForShell({ config: fixture.config, legacyExercises: EXERCISES, seed: fixture.seed, makeId: () => 'm220-next' }).program;
const [first, second] = generated.days, slot = 1, lift = first.exercises[slot];
second.exercises[slot] = lift;
for (const [day, sets] of [[first,5],[second,3]]) {
    const key = `${day.id}:${slot}`;
    for (const [field, value] of [['sets',sets], ['reps','12-20'], ['rir','2'], ['progressionStyle','double']])
        generated.overrides[key] = markUserPrescriptionOverride(generated.overrides[key] || {}, field, value);
}
const nextEntry = (day, date, weight, reps) => ({ id: `next-${date}`, programId: generated.id, dayId: day.id, date, weekIndex: 1, unit: 'lb',
    perf: { [lift]: { weight, reps: reps.at(-1), sets: reps.map(r => ({w:weight,r,rir:2,tr:2,done:true})) } } });
const oldDay = nextEntry(first, 1000, 205, [20,16,16,12,12]);
const newOtherDay = nextEntry(second, 2000, 210, [20,17,15]);
const generatedSuggestion = nextWorkoutSuggestionForShell(generated, [newOtherDay,oldDay], EXERCISES, first, slot, 1);
assert.equal(generatedSuggestion.last, oldDay.perf[lift], 'generated suggestion metadata shares its evaluator day');
assert.equal(generatedSuggestion.weight, 205);
assert.equal(generatedSuggestion.action, 'add_reps');
const otherProgramSameDay = { ...oldDay, id: 'other-program-same-day', programId: 'other-program',
    date: 3000, perf: { [lift]: { weight: 400, reps: 20, sets: [{ w: 400, r: 20, rir: 2, tr: 2, done: true }] } } };
assert.equal(lastDayPerf(first, {}, [otherProgramSameDay, oldDay], generated)[lift], oldDay.perf[lift],
    'LAST cannot be stolen by the same day id from another program');
assert.equal(dayPerfFor(first, {}, [otherProgramSameDay, oldDay], generated)[lift], oldDay.perf[lift],
    'best-of-recent anchor cannot use another program with a colliding day id');
console.log('PASS M220 generated bridge: decision, LAST and anchor metadata stay program/day scoped even when another occurrence or program is newer.');
