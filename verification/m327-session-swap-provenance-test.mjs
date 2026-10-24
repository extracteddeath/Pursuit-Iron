import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/training-domain/catalog.js';
import { loggedWorkoutPerformance } from '../modules/training-domain/prescriptions.js';
import { progressionExposureContext } from '../modules/next-engine/history-contract.js';

// One completed exercise swap must not turn the unswapped day's other lifts
// into "non-comparable" progression evidence.
const catalog = EXERCISES.filter(ex => ex?.id && Array.isArray(ex.rep) && ex.rep.length >= 2);
assert.ok(catalog.length >= 3, 'need three catalog exercise identities');
const [planned, unaffected, replacement] = catalog.slice(0, 3);
const day = { id: 'swap-day', label: 'Upper', primaryIndex: 0,
    exercises: [planned.id, unaffected.id] };
const program = { id: 'swap-program', name: 'Swap regression', custom: true,
    config: { unit: 'lb' }, overrides: {}, days: [day] };
const work = (id, slot) => ({ id, slot, sets: [
    { done: true, weight: '75', reps: '8', warm: false, sub: false,
        target: { w: '75', reps: '8', rir: 2 } }
] });
const performed = [work(replacement.id, 0), work(unaffected.id, 1)];
const saved = loggedWorkoutPerformance(program, day, performed, 1, 'lb', {}, []);
assert.equal(saved[replacement.id]?.exerciseSubstituted, true,
    'swapped exercise must explicitly own the non-comparable flag');
assert.equal(saved[unaffected.id]?.exerciseSubstituted, undefined,
    'unmodified slot must not inherit another lift\'s substitution');
assert.equal(progressionExposureContext([saved[replacement.id]], {}).nonComparable, true);
assert.equal(progressionExposureContext([saved[unaffected.id]], {}).nonComparable, false);
assert.equal(saved[replacement.id]?.prescription?.exerciseId, replacement.id,
    'swapped set must never be recorded as the original planned exercise');
assert.equal(saved[unaffected.id]?.prescription?.exerciseId, unaffected.id);

// A permanent program swap can update the day roster before the session finishes.
// The explicit session flag still preserves the initial replacement identity.
const editedDay = { ...day, exercises: [replacement.id, unaffected.id] };
const editedProgram = { ...program, days: [editedDay] };
const editedSession = [{ ...work(replacement.id, 0), exerciseSubstituted: true,
    previousExerciseId: planned.id }, work(unaffected.id, 1)];
const editedSaved = loggedWorkoutPerformance(editedProgram, editedDay, editedSession, 1, 'lb', {}, []);
assert.equal(editedSaved[replacement.id]?.exerciseSubstituted, true,
    'persisted workout substitution identity must survive a program-roster update');
assert.equal(editedSaved[unaffected.id]?.exerciseSubstituted, undefined);

// The session-global flag used to hold all lifts. Substitution history is
// informational; the exercise-level exposure flag owns progression comparability.
const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
assert.ok(!app.includes('substitutionOccurred: data.some('),
    'a single swap must not mark the entire session non-comparable');
assert.ok(app.includes('substitutions: data.flatMap(e => {'));
assert.ok(app.includes('current.sets.some(set => set.done)'),
    'swap must reject a lift with completed sets');
assert.ok(app.includes('onBanCurrent: () => { if (data[exIdx]?.sets.some(set => set.done))'),
    'ban-and-replace must protect logged work before changing the ban list');
assert.ok(app.includes('exerciseSubstituted: true,') && app.includes('previousExerciseId: current.previousExerciseId || oldId'),
    'session and persistent swaps must retain their own identity even if the program updates');
console.log('PASS M327: session swap identity is local, untouched lifts remain comparable, completed sets are protected.');

// M329: a live row may no longer be the exercise owning its authored slot.
const { workoutRowMatchesPrescription, withProgressionChoices, refreshProgressionSets } = await import('../modules/session-tools.js');
const { sessionSuggestion, prescribeSets, userOwnsRuntimeSet } = await import('../modules/engine-api.js');
const { EX_BY_ID } = await import('../modules/training-domain/catalog.js');
const benchDay = { id: 'upper', primaryIndex: 0, exercises: ['bb-bench', 'bb-row'] };
const benchProgram = { id: 'swap-loads', custom: true, weeks: 4, days: [benchDay],
    config: { unit: 'lb', goal: 'both', experience: 'intermediate', progression: 'double', deload: false },
    overrides: { 'upper:0': { sets: 3, reps: '6-8', rir: '2', rest: 90 } } };
const benchHistory = [{ id: 'source', date: 1700000000000, programId: benchProgram.id,
    dayId: benchDay.id, weekIndex: 1, unit: 'lb', perf: { 'bb-bench': { weight: 200, reps: 8,
        sets: [1,2,3].map(() => ({ w: 200, r: 8, rir: 2, rirReported: true, tr: 2 })) } } }];
const curl = { id: 'inc-curl', slot: 0, exerciseSubstituted: true };
const choice = withProgressionChoices(benchProgram, { 'inc-curl': 'dynamic' });
const borrowed = sessionSuggestion(choice, benchDay, 0, {}, 'lb', 2, benchHistory);
assert.ok(borrowed.weight >= 200, 'reproduce a real bench load that must never reach a curl');
assert.equal(workoutRowMatchesPrescription(choice, benchDay, curl, 2), false);
assert.equal(workoutRowMatchesPrescription(choice, benchDay, { id: 'inc-curl', slot: 0 }, 2), false,
    'older snapshots without the explicit substitution flag are protected by identity');
assert.equal(workoutRowMatchesPrescription(choice, benchDay, { id: 'bb-bench', slot: 0 }, 2), true);
assert.equal(workoutRowMatchesPrescription(choice, benchDay, { id: 'bb-row', slot: 1 }, 2), true,
    'an unchanged row retains its own suggestion after list reordering/removal');
const pairProgram = { ...choice, pairs: { 'upper:0': 'db-bench' } };
assert.equal(workoutRowMatchesPrescription(pairProgram, benchDay, { id: 'db-bench', slot: 0 }, 2), true);
assert.equal(workoutRowMatchesPrescription(pairProgram, benchDay, { id: 'bb-bench', slot: 0 }, 2), false);
assert.equal(workoutRowMatchesPrescription(choice, benchDay, { id: 'bb-bench', slot: 0, added: true }, 2), false);
assert.equal(workoutRowMatchesPrescription(choice, benchDay, { id: 'inc-curl', slot: 2, added: true }, 2), false);
const persistedDay = { ...benchDay, exercises: ['inc-curl', 'bb-row'] };
assert.equal(workoutRowMatchesPrescription({ ...choice, days: [persistedDay] }, persistedDay, curl, 2), false,
    'persisting a program swap does not manufacture comparable session evidence');
const replacementSets = prescribeSets(choice, benchDay, EX_BY_ID[curl.id], 0, 2, 'lb', null, {}, {}, benchHistory, false);
assert.ok(replacementSets.every(set => !Number(set.weight)), 'replacement load stays unseeded');
const entered = [{ ...replacementSets[0], done: true, weight: '25', reps: '8' },
    { ...replacementSets[1], valueOwner: 'user', auto: false, weight: '30', reps: '7' }, replacementSets[2]];
const refreshed = refreshProgressionSets(entered, replacementSets, userOwnsRuntimeSet);
assert.equal(refreshed[0], entered[0]);
assert.equal(refreshed[1], entered[1]);
assert.equal(refreshed[2].weight, '');
assert.ok(app.includes('const runtimeSuggestions = data.map(row => workoutRowMatchesPrescription('));
assert.ok(app.includes('const suggestion = workoutRowMatchesPrescription(nextProgram, day, row, weekIndex)'));
assert.ok(!app.includes('suggestions[ei]') && !app.includes('const sug = suggestions[e.slot]'));
console.log('PASS M329: swapped/added rows cannot borrow slot loads; native alternate weeks and manual work retain ownership.');
