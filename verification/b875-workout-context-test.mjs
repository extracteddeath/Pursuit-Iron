import assert from 'node:assert/strict';
import { activeSetContext, adjustInjuredExercises, dockRepSuggestion, removePendingWorkingSet, resizeWorkingSets, lighterPendingWorkout } from '../modules/workout-context.js';

const row = { id: 'press', sets: [
    { warm: true, done: false, weight: '45' },
    { done: false, weight: '100', reps: '8' },
    { done: false, weight: '90', reps: '8' },
    { sub: true, kind: 'drop', done: false, weight: '72' },
    { sub: true, kind: 'drop', done: false, weight: '54' }
] };
assert.equal(activeSetContext(row).kind, 'warmup');
row.sets[0].done = true;
assert.equal(activeSetContext(row).workIndex, 0);
assert.equal(activeSetContext(row).finalWork, false);
row.sets[1].done = true;
assert.equal(activeSetContext(row).finalWork, true);
row.sets[2].done = true;
assert.equal(activeSetContext(row).kind, 'drop');
row.sets.forEach(set => set.done = true);
assert.equal(activeSetContext(row).kind, 'complete');
assert.equal(activeSetContext(row).set, null, 'complete exercise cannot recommend another last set');
const myo = { id: 'curl', sets: [{ done: true }, { sub: true, kind: 'myo', done: false }] };
assert.equal(activeSetContext(myo).kind, 'myo');

const affected = { id: 'press', sets: [
    { warm: true, done: true, weight: '45' },
    { done: true, weight: '100', reps: '8', actualRIR: 3 },
    { done: false, weight: '97.5', reps: '7', valueOwner: 'user' },
    { sub: true, kind: 'drop', done: false, weight: '80' },
    { sub: true, kind: 'drop', done: false, weight: '60' }
] };
const untouched = { id: 'squat', sets: [{ done: false, weight: '225' }] };
const data = [affected, untouched];
const before = structuredClone(data);
const reduced = adjustInjuredExercises(data, ['press'], 'chest', 'reduce');
assert.equal(reduced[1], untouched, 'unaffected exercise retains identity and all values');
assert.deepEqual(reduced[0].sets, affected.sets.slice(0, 3), 'logged results and manual load survive; both pending drops are removed');
assert.equal(activeSetContext(reduced[0]).finalWork, false, 'injury-adjusted last set never receives an amplifier');
assert.deepEqual(data, before, 'the original snapshot remains intact for Undo');
const skipped = adjustInjuredExercises(data, ['press'], 'chest', 'skip');
assert.deepEqual(skipped[0].sets, affected.sets.slice(0, 2), 'skip preserves all completed work and effort');
assert.deepEqual(adjustInjuredExercises([{id:'curl',sets:[{done:false}]}], ['curl'], 'biceps', 'skip'), []);
const volume = { id: 'press', sets: Array.from({ length: 5 }, (_, i) => ({ done: false, weight: String(100-i) })) };
assert.equal(adjustInjuredExercises([volume], ['press'], 'chest', 'reduce')[0].sets.length, 3, 'odd remaining ordinary set count rounds to a real usable number');
console.log('B875: actual warm-up/work/extension/completion context and injury reduction preservation pass.');

assert.equal(dockRepSuggestion({reps:'5',target:{reps:'5-8',prefillReps:'7'}}),'7');
assert.equal(dockRepSuggestion({reps:'5',suggested:{reps:'5-8'},target:{prefillReps:'6'}}),'6');
assert.equal(dockRepSuggestion({reps:'9',target:{reps:'8-12'}}),'9');
assert.equal(dockRepSuggestion({target:{reps:'8-12'}}),'—');
assert.equal(dockRepSuggestion({reps:'8',target:{reps:'AMRAP'}}),'AMRAP');
assert.equal(dockRepSuggestion({reps:'8',target:{reps:'to failure'}}),'To failure');
const countRow={sets:[{warm:true,done:true},{done:true,weight:'200',actualRIR:2},{done:false,weight:'195'},
    {sub:true,kind:'drop',done:false},{sub:true,kind:'myo',done:true}]};
const countBefore=structuredClone(countRow);
const fewer=removePendingWorkingSet(countRow);
assert.deepEqual(fewer.sets,[countRow.sets[0],countRow.sets[1],countRow.sets[4]],'remove pending work and its unlogged extensions, preserve all logged evidence');
assert.equal(removePendingWorkingSet(fewer),fewer,'cannot remove last or logged working set');
assert.deepEqual(countRow,countBefore,'set-count edit preserves the original row');
console.log('Dock exact rep suggestions and safe session-only set-count reduction pass.');

// Session-local edits preserve completed sets and user-entered values; adding a set
// must never turn a logged set into an engine-controlled recommendation.
const editable = { sets: [
    { warm: true, done: true, weight: '45' },
    { done: true, weight: '120', reps: '8', actualRIR: 1 },
    { done: false, weight: '120', reps: '8', target: { w: '120', reps: [8, 12] } },
    { sub: true, done: false, kind: 'drop', weight: '90' }
] };
const more = resizeWorkingSets(editable, 3);
assert.equal(more.sets.length, 5, 'session-only expansion adds one working set');
assert.deepEqual(more.sets.slice(0, 3), editable.sets.slice(0, 3), 'new sets preserve original warmups and logged working evidence');
assert.equal(more.sets[3].added, true, 'manual work precedes the pending drop extension');
assert.equal(more.sets[4], editable.sets[3], 'drop extension still follows the final activation set');
assert.equal(more.sets[3].valueOwner, 'user', 'manually added work is not automatically re-prescribed');
assert.equal(more.sets[3].auto, false);
assert.equal(resizeWorkingSets(editable, 0), editable, 'cannot remove all ordinary working sets');
const onlyLogged = resizeWorkingSets(editable, 1);
assert.deepEqual(onlyLogged.sets, editable.sets.slice(0, 2),
    'reducing to logged work removes only pending work and its orphaned drop extension');
assert.equal(resizeWorkingSets(editable, 21), editable, 'reject unbounded session volume');
assert.deepEqual(editable.sets[1], { done: true, weight: '120', reps: '8', actualRIR: 1 },
    'live set-count edits must not mutate logged training evidence');

const lighter = { sets: [
    { done: true, weight: '120', reps: '8' },
    { done: false, weight: '120', target: { w: '120' } },
    { done: false, weight: '115', valueOwner: 'user', target: { w: '115' } },
    { sub: true, done: false, kind: 'drop', weight: '85' }
] };
const beforeLight = structuredClone(lighter);
const reducedDay = lighterPendingWorkout(lighter, load => Math.floor(load / 5) * 5, set => set.valueOwner === 'user');
assert.equal(reducedDay.sets[0], lighter.sets[0], 'completed work cannot be quietly reduced');
assert.equal(reducedDay.sets[1].weight, '105', 'only an explicit easier-day action changes pending prescribed load');
assert.equal(reducedDay.sets[1].valueOwner, 'user');
assert.equal(reducedDay.sets[2], lighter.sets[2], 'user-entered load is protected');
assert.equal(reducedDay.sets.some(set => set.sub && !set.done), false, 'pending failure-technique sets are removed on a lighter day');
assert.deepEqual(lighter, beforeLight, 'the original snapshot remains valid for Undo');
const loggedExtension = { sets: [
    { done: true, weight: '100' }, { sub: true, done: true, kind: 'myo', weight: '80' }
] };
const retainedExtension = resizeWorkingSets(loggedExtension, 2);
assert.deepEqual(retainedExtension.sets.slice(0, 2), loggedExtension.sets,
    'logged extensions remain in their original order when a new work set is added');
assert.equal(retainedExtension.sets[2].added, true);
console.log('PASS session edits: set-count changes and explicit easier-day loads preserve completed and manually owned evidence.');
