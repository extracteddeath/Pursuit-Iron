import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const workout=app.slice(app.indexOf('function WorkoutSession('));
assert.ok(workout.includes('"data-set-options-trigger": true') && workout.includes('onClick: () => { setSetOptions({ ei, si })'), 'Each set number opens its own menu');
assert.ok(workout.includes('"aria-haspopup": "dialog"') && workout.includes('"role": "dialog"') && workout.includes('"aria-modal": "true"'), 'Set options must be an accessible bottom sheet');
assert.ok(workout.includes('changeSessionSetType(ei, si, opt.id)') && workout.includes('"standard"') && workout.includes('"warmup"') && workout.includes('"failure"'), 'Working, warm-up and failure states available for current workout');
assert.ok(workout.includes('if (j !== si || s.done || s.sub'), 'Completed and extension sets never get retyped');
assert.ok(workout.includes('typeOriginal: original') && workout.includes('typeOverride: kind') && workout.includes('target: s.target'), 'Original set targets are preserved before type changes');
assert.ok(workout.includes('"data-set-remove": true') && workout.includes('removeSessionAddedSet(ei, si)') && workout.includes('setSetRemoveConfirm(true)'), 'Only the set menu can remove an added set, after two steps');
assert.ok(workout.includes('if (!row || row.done || !(row.added || (row.sub && !row.prescribed)))'), 'Prescribed and completed sets cannot be removed through menu');
assert.ok(!workout.includes('onClick: () => removeSubSet(ei, si)') && !workout.includes('Complete remaining sets') && !workout.includes('const completeRemaining'), 'No accidental inline delete or bulk completion');
assert.ok(/className: "wpb-exercise-name(?:\s[^"]*)?"/.test(workout) && css.includes('.wpb-exercise-heading .wpb-exercise-name'), 'Exercise name enlarged with adaptive headline');
assert.ok(css.includes('.wpb-set-controls>.wpb-set-index') && css.includes('.wpb-set-index:focus-visible'), 'Set-index button replaces span in phone grid without losing focus treatment');
assert.ok(workout.includes('onClick: () => toggleDone(ei, si)') && workout.includes('onStep: () => bumpReps(ei, si, -1)') && workout.includes('onStep: () => bumpWeight(ei, si, 1)'), 'Set completion and steppers preserved');
const controls=fs.readFileSync('modules/workout-session-controls.js','utf8');
for(const name of ['WorkingSetCountControl','SessionNoteEditor','selectNumericInput']) {
  assert.equal((controls.match(new RegExp('(?:function|export function) '+name+'\\(', 'g'))||[]).length,1,'One shared UI owner for '+name);
  assert.ok(!app.includes('function '+name+'('),'Removed the stale App definition for '+name);
}
assert.ok(app.includes("from './workout-session-controls.js'"),'WorkoutSession imports shared controls');
console.log('PASS B886 set number menu: safe per-set type, add-only removal, larger heading, no bulk completion');
