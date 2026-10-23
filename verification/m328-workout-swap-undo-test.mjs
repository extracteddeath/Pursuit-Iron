import assert from 'node:assert/strict';
import fs from 'node:fs';
import { beginWorkoutProgramSwap, undoWorkoutProgramSwap } from '../modules/session-tools.js';

const make = (id, cycleId, lift = 'bench') => ({
    id, cycleId, name: id, edited: false, config: { goal: 'strength' }, days: [
        { id: 'day', label: 'Upper', exercises: [lift, 'row'], primaryIndex: 0 },
        { id: 'other', label: 'Lower', exercises: ['squat'] }
    ]
});
const current = make('active', 'cycle');
const sibling = make('future', 'cycle');
const unrelated = make('unrelated', 'other');
const saved = [current, sibling, unrelated];
const tx = beginWorkoutProgramSwap(current, saved, 'day', 'bench', 'incline');
assert.ok(tx);
assert.equal(tx.program.days[0].exercises[0], 'incline');
assert.equal(tx.saved[0], tx.program, 'saved active plan must share exact authoritative instance');
assert.equal(tx.saved[1].days[0].exercises[0], 'incline');
assert.equal(tx.saved[2], unrelated, 'unrelated programs stay untouched');
assert.deepEqual(tx.program.days[1], current.days[1], 'other training days remain unchanged');
const undo = undoWorkoutProgramSwap(tx.program, tx.saved, tx.receipt);
assert.ok(undo);
assert.equal(undo.program, current, 'untouched active program restored exactly');
assert.equal(undo.saved[0], current, 'saved current program restored too');
assert.equal(undo.saved[1], sibling, 'cycle sibling program restored too');
assert.equal(undo.saved[2], unrelated);
const stamped = tx.saved.map(p => p.id === current.id || p.id === sibling.id
    ? { ...p, updatedAt: 99999 } : p);
const stampedUndo = undoWorkoutProgramSwap(tx.program, stamped, tx.receipt);
assert.equal(stampedUndo.saved[0].edited, current.edited,
    'Undo restores the edited flag after store timestamps the applied swap');
assert.equal(stampedUndo.saved[1].edited, sibling.edited);

// No silent partial rollback if a later change replaced the same exercise.
const collision = { ...tx.saved[1], days: tx.saved[1].days.map(d =>
    d.id === 'day' ? { ...d, exercises: ['press', 'row'] } : d) };
assert.equal(undoWorkoutProgramSwap(tx.program, [tx.saved[0], collision, unrelated], tx.receipt), null);

// An unrelated edit after swap is not overwritten by Undo.
const changed = { ...tx.program, name: 'User renamed this program' };
const safe = undoWorkoutProgramSwap(changed, [tx.saved[0], tx.saved[1], unrelated], tx.receipt);
assert.equal(safe.program.name, changed.name);
assert.equal(safe.program.days[0].exercises[0], 'bench');
assert.equal(safe.saved[1].days[0].exercises[0], 'bench');
assert.equal(beginWorkoutProgramSwap(current, saved, 'day', 'missing', 'incline'), null);
assert.equal(beginWorkoutProgramSwap(current, saved, 'day', 'bench', 'row'), null);
const solo = beginWorkoutProgramSwap({ ...current, cycleId: null }, [unrelated], 'day', 'bench', 'incline');
assert.equal(solo.saved[0], unrelated);
assert.equal(undoWorkoutProgramSwap(solo.program, solo.saved, solo.receipt).program.days[0].exercises[0], 'bench');

// UI must use the transaction for BOTH sides, and protect subsequent edits.
const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
for (const phrase of [
    'const transaction = beginWorkoutProgramSwap(program, saved, dayId, oldId, newId)',
    'const restored = undoWorkoutProgramSwap(latest.program, latest.saved, transaction.receipt)',
    'const programUndo = scope === "program"',
    'JSON.stringify(latest.sets) !== plannedSets',
    'if (programUndo && !programUndo())',
    'setData(rows => rows.map((row, i) => i === ei ? snap[ei] : row))'
]) assert.ok(app.includes(phrase), 'workout swap Undo contract missing: '+phrase);
console.log('PASS M328: active/saved/cycle swap transaction and safe Undo are consistent.');
