import assert from 'node:assert/strict';
import fs from 'node:fs';

// Keep the B884 screen pass honest without coupling to pixel-specific CSS.
// Runtime behavior is separately covered by phone/workout browser gates.
const app = fs.readFileSync('modules/App.js','utf8');
const css = fs.readFileSync('app.css','utf8');
const workout = app.slice(app.indexOf('function WorkoutSession('), app.indexOf('function WorkoutSession(')+275000);
assert.ok(workout.startsWith('function WorkoutSession('), 'WorkoutSession source must exist');
const exercise = workout.indexOf('className: "wpb-exercise-heading"');
const toolbar = workout.indexOf('className: "wpb-hscroll wpb-workout-tools"');
const list = workout.indexOf('data-testid": `set-${ei}-${si}`');
const add = workout.indexOf('className: "pressable wpb-add-set"');
const coaching = workout.indexOf('"aria-label": "Expand Coach guidance"');
assert.ok(exercise > -1 && toolbar > exercise && list > toolbar && add > list && coaching > add,
  'Exercise identity, tools, editable set list and add-set must precede optional coaching');
assert.ok(!workout.includes('First time logging this'),
  'No first-time assertion when the previous set may be available');
assert.ok(workout.includes('previousWorkoutSet(lastPerf[e.id], 0)'),
  'Suggestion absence must not override actual previous-workout evidence');
assert.ok(workout.includes('children: tgtW ?? "Set load"'),
  'Missing target loads should get an understandable label, not a fake number');
assert.ok(workout.includes('data-reference-action\": \"last') && workout.includes('data-reference-action\": \"target'),
  'Target and Last references must be the only set-local tap-to-fill actions');
assert.ok(!workout.includes('children: "Use last"'), 'Do not duplicate the LAST-column action beneath the active row');
assert.ok(workout.includes('"data-focus-mode": focus ? "true" : "false"'),
  'Normal logging layout styling must be able to isolate Focus mode');
for (const stable of ['"aria-label": "weight"','"aria-label": "reps"','"data-haptic-manual": "1"',
  'onClick: () => addSetRow(ei)',
  'onClick: () => toggleDone(ei, si)', 'className: "wpb-workout-side"']){
  assert.ok(workout.includes(stable), `Set-entry functionality is preserved: ${stable}`);
}
assert.ok(!workout.includes('Complete remaining sets'), 'Each set must be completed intentionally; no bulk-complete action');
assert.ok(css.includes('R93 / B884 — Workout is a set logger'), 'Style pass is present');
assert.ok(css.includes('data-focus-mode="false"'), 'Focus and normal screen styling isolated');
// The exercise header must not drift back into a tall name/tags/set/actions stack.
for(const selector of [
  '.wpb-workout[data-focus-mode="false"] .wpb-exercise-heading>div:first-child',
  '.wpb-workout[data-focus-mode="false"] .wpb-exercise-title-row{display:contents;}',
  '.wpb-workout[data-focus-mode="false"] .wpb-exercise-heading .wpb-exercise-tags',
  '.wpb-workout[data-focus-mode="false"] .wpb-exercise-meta'
]) assert.ok(css.includes(selector), 'Missing compact workout header owner: '+selector);
assert.match(css, /\.wpb-workout\[data-focus-mode="false"\] \.wpb-exercise-heading \.wpb-exercise-tags\{\s*grid-column:2;grid-row:2;/,
  'Key lift/type tags belong directly beneath the right-hand controls');
assert.match(css, /\.wpb-workout\[data-focus-mode="false"\] \.wpb-exercise-title-row>div:last-child\{\s*grid-column:2;grid-row:1;/,
  'Right-hand actions stay aligned to the top of the exercise name');
assert.match(css, /#root \.wpb-workout \.wpb-workout-tools\{\s*display:flex;flex-wrap:nowrap;overflow-x:auto;overflow-y:hidden;/,
  'Workout tools must stay on one horizontally scrolling row');
assert.ok(!css.includes('flex-wrap:wrap;overflow:visible!important;touch-action:pan-y!important;'),
  'Do not regress workout tools to wrapping rows');
assert.match(css, /\.wpb-workout\[data-focus-mode="false"\] \.wpb-exercise-meta\{\s*grid-column:1;grid-row:2;/,
  'Set context then progression belong under lift name');
assert.ok(workout.includes('className: "wpb-exercise-head-actions"') && workout.includes('className: "wpb-exercise-set-context"'),
  'Skip/history/info and current working-set context retain their actions');

assert.ok(css.includes('data-focus-mode="false"][data-set-dock-visible="true"]') && css.includes('padding-bottom:calc(16px + var(--pi-set-dock-height,0px))!important;'), 'Dock scroll reserve must survive the B884 normal-mode padding shorthand');
assert.ok(css.includes('prefers-reduced-motion:reduce') && css.includes('forced-colors:active'),
  'Motion and accessibility overrides remain supported');
console.log('PASS B884 workout hierarchy: active-set-first layout, preserved input ownership and honest previous-load cues.');
