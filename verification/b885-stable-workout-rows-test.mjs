import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const workout=app.slice(app.indexOf('function WorkoutSession('));
assert.ok(workout.includes('"data-reference-action": "last"'), 'LAST reference still directly applies a prior set');
assert.ok(workout.includes('"data-reference-action": "target"'), 'TARGET reference applies a target or suggestion');
assert.ok(!workout.includes('children: "Use last"'), 'Redundant previous-set pill removed');
assert.ok(!workout.includes('isActive && !s.done && (hasSuggested || pvLast)'), 'No extra active-row action strip');
assert.ok(workout.includes('onClick: () => editWeight(ei, si, String(pv.w))') && !workout.includes('editSet(ei, si, "reps", String(pv.r))'), 'Previous load reuse must preserve today\'s reps');
assert.ok(workout.includes('const actionableTarget = !s.done'), 'Completed sets cannot be overwritten from references');
assert.ok(workout.includes('if (hasSuggested) editSet(ei, si, "reps"'), 'Exact suggested reps only, never rep ranges');
assert.ok(workout.includes('className: "wpb-b965-layout-19"') &&
  css.includes('.wpb-b965-layout-19{flex:1;min-width:0;position:relative}') &&
  workout.includes('className: "wpb-set-reps wpb-layout-flex-one wpb-s956-minwidth-0"') &&
  css.includes('.wpb-layout-flex-one,') && css.includes('.wpb-s956-minwidth-0,'),
  'Weight and reps share a balanced track through the single CSS owner');
assert.ok(css.includes('B885 — Stable, thumb-safe set rows') && css.includes('column-gap:8px!important;'), 'Set tracks and steppers have deliberate touch separation');
// B1043 superseded the narrow-phone two-tier row: every set is one grid line under a single header.
const grid='grid-template-columns:32px minmax(56px,.95fr) minmax(0,1.15fr) minmax(0,1fr) 40px!important;';
assert.ok(css.includes('B1043 — One line per set') && css.includes(grid), 'Set rows and their header share one grid');
assert.ok(!css.includes('content:"REPS"') && !css.includes('content:"LOAD"') && !app.includes('content:"Load"'), 'Column labels are not repeated on every row');
assert.equal((workout.match(/!s\.done && isActive && _jsx\(StepButton/g)||[]).length, 4, 'Only the working set carries steppers');
console.log('PASS B885 stable set-row contract: one reference, even fields, separated controls.');
