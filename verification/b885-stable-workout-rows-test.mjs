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
assert.ok(workout.includes('style: { flex: 1, minWidth: 0, position: "relative" }') &&
  workout.includes('className: "wpb-set-reps wpb-layout-flex-one wpb-s956-minwidth-0"') && css.includes('.wpb-layout-flex-one,') && css.includes('.wpb-s956-minwidth-0,'), 'Weight and reps have equal row flex shares');
assert.ok(css.includes('B885 — Stable, thumb-safe set rows') && css.includes('column-gap:8px!important;') &&
  css.includes('gap:5px!important;'), 'Set tracks and steppers have deliberate touch separation');
assert.ok(css.includes('@media(max-width:380px)') && css.includes('width:auto!important;'), 'Compact stacked row fallback preserved');
assert.ok(css.includes('grid-template-columns:minmax(0,1fr) minmax(0,1fr) 36px!important') && css.includes('content:"REPS"'), 'Small-screen fields are balanced and labelled');
assert.ok(css.includes('[data-testid^="set-"][data-done="1"] .wpb-set-controls') && css.includes('display:flex!important;align-items:flex-start!important;gap:6px!important;'), 'Logged rows remain compact on narrow phones');
console.log('PASS B885 stable set-row contract: one reference, even fields, separated controls.');
