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
