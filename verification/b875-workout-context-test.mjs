import assert from 'node:assert/strict';
import { activeSetContext, adjustInjuredExercises, dockRepSuggestion, removePendingWorkingSet } from '../modules/workout-context.js';

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
