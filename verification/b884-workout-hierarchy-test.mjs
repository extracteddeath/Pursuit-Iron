import assert from 'node:assert/strict';
import fs from 'node:fs';

// Keep the B884 screen pass honest without coupling to pixel-specific CSS.
// Runtime behavior is separately covered by phone/workout browser gates.
const app = fs.readFileSync('modules/App.js','utf8');
const css = fs.readFileSync('app.css','utf8');
const workout = app.slice(app.indexOf('function WorkoutSession('), app.indexOf('function WorkoutSession(')+275000);
assert.ok(workout.startsWith('function WorkoutSession('), 'WorkoutSession source must exist');
const exercise = workout.indexOf('className: "wpb-exercise-heading ');
const toolbar = workout.indexOf('className: "wpb-hscroll wpb-workout-tools'); // Additional shared CSS owner classes must not hide or reorder the toolbar.
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
// Accessible disclosure/primer controls are not allowed to steal the
// numeric logger's interaction model or regress to caption-size hit areas.
for (const [selector,property] of [
 ['.wpb-load-explanation summary','min-height:48px'],
 ['#root .wpb-workout .wpb-warmup-hide','min-height:48px'],
 ['#root .wpb-workout .wpb-warmup-finish','min-height:52px']
]) {
 const at=css.indexOf(selector+'{');
 assert.ok(at>=0&&css.slice(at,css.indexOf('}',at)).includes(property),
   'Workout supporting action retains its native touch target: '+selector);
}
assert.ok(css.includes('R93 / B884 — Workout is a set logger'), 'Style pass is present');
assert.ok(css.includes('data-focus-mode="false"'), 'Focus and normal screen styling isolated');
// The exercise header must not drift back into a tall name/tags/set/actions stack.
for(const selector of [
  '.wpb-workout[data-focus-mode="false"] .wpb-exercise-heading>div:first-child',
  '.wpb-workout[data-focus-mode="false"] .wpb-exercise-title-row,',
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
assert.ok(css.includes('#root .wpb-workout[data-focus-mode="false"] .wpb-exercise-meta{') && css.includes('  display:contents;'),
  'The meta wrapper must be layout-transparent');
assert.ok(css.includes('.wpb-exercise-meta>.wpb-exercise-set-context{') && css.includes('grid-column:1;grid-row:2;'),
  'Set context must share row 2 with tags');
assert.ok(css.includes('.wpb-exercise-meta>.wpb-progression-label{') && css.includes('grid-column:1/-1;grid-row:3;'),
  'Progression sits on the third row');
assert.ok(workout.includes('className: "wpb-exercise-head-actions ') && workout.includes('className: "wpb-exercise-set-context"'),
  'Skip/history/info and current working-set context retain their actions');

assert.ok(css.includes('data-focus-mode="false"][data-set-dock-visible="true"]') && css.includes('padding-bottom:calc(16px + var(--pi-set-dock-height,0px))!important;'), 'Dock scroll reserve must survive the B884 normal-mode padding shorthand');
assert.ok(css.includes('prefers-reduced-motion:reduce') && css.includes('forced-colors:active'),
  'Motion and accessibility overrides remain supported');
console.log('PASS B884 workout hierarchy: active-set-first layout, preserved input ownership and honest previous-load cues.');

// B968: exercise action hit-target and destructive confirmation layout owner.
assert.ok(css.includes('width:48px!important;min-width:48px!important;') &&
  css.includes('height:48px!important;min-height:48px!important;'),
  'Workout action hit targets are real 48px boxes, independent of icon size');
assert.ok(workout.includes('aria-label": "Confirm skip exercise for today"') &&
  css.includes('.wpb-exercise-head-actions>.wpb-confirm-skip{'),
  'Skip confirmation has its own accessible label and intrinsic text width');

/* B969: toolbar buttons are icon-only; feature callbacks remain in WorkoutSession. */
assert.ok(workout.includes('className: "pressable wpb-workout-tool", "aria-label": c.label') &&
  workout.includes('"aria-pressed": c.active === undefined ? undefined : !!c.active') &&
  workout.includes('children: _jsx(c.icon, { size: 19, "aria-hidden": true })'),
  'Accessible icon actions retain their labels and toggle semantics');
assert.ok(!workout.includes('padding: "8px 13px", borderRadius: 999, border: '),
  'Toolbar presentation must no longer be owned by JSX styles');
assert.ok(css.includes('/* B969: compact workout action rail.') &&
  css.includes('.wpb-workout-tools .wpb-workout-tool[data-selected="true"]'),
  'One CSS owner paints the compact icon rail and selected state');

/* Design 41–43: One Workout note editor owns exercise notes.
   Previous-session context moves into it instead of taking logger height. */
assert.ok(!workout.includes('placeholder: "Add a note\\u2026"') && !workout.includes('children: "Last note:"'),
  'A duplicate full-width note editor must not return below working sets');
assert.ok(workout.includes('className: "wpb-session-notes-previous"') &&
  workout.includes('perf[data[exIdx].id]?.note') &&
  workout.includes('onChange: text => editNote(exIdx, text)'),
  'Last exercise note remains readable from the existing session note editor');
assert.ok(workout.includes('setSessionNotesOpen(true)') &&
  workout.includes('label: "Whole session"') &&
  workout.includes('noteRepeat: value'),
  'Session and exercise note editors and repeat behavior stay available');
assert.ok(css.includes('.wpb-session-notes-previous{') &&
  css.includes('.wpb-session-notes-previous>strong{'),
  'Previous note style is a shared theme-aware CSS role');

/* Design 44–46: one accessible average-load action with no duplicate inline paint. */
assert.ok(workout.includes('className: "pressable wpb-workout-average-action"') &&
    workout.includes('onClick: () => levelSetsToAvg(ei)') &&
    !workout.includes('title: "You ramped up'),
    'Average-load remains an intentional button, without a lengthy tooltip');
const averageAction=css.split('#root .wpb-workout .wpb-workout-average-action{')[1]?.split('}')[0]||'';
assert.ok(averageAction.includes('min-height:48px') &&
    averageAction.includes('background:var(--wpb-theme-bg-tint)') &&
    css.includes('.wpb-workout-average-action:focus-visible,'),
    'Average load uses a 48px theme-aware action and the existing focus group');

/* B970: keep notes discoverable in one toolbar action and CSS-owned hint surfaces. */
assert.ok(!workout.includes('children: "Session note · " + e.note'),
  'Exercise header must not duplicate the session note toolbar action');
assert.ok(workout.includes('{ k: "session-note", hasNote: !!e.note') &&
  workout.includes('"data-has-note": c.hasNote ? "true" : undefined'),
  'Workout note action retains its accessible label and displays saved-note presence');
for(const cls of ['wpb-workout-pinned-note','wpb-workout-pinned-note-text','wpb-workout-starting-hint']){
 assert.ok(css.includes('#root .wpb-workout .'+cls+'{'),'CSS owns workout note/hint presentation: '+cls);
}

/* B971: UI-only header CSS ownership; timer and progress state remain in React. */
for(const cls of ['wpb-workout-header','wpb-workout-close','wpb-workout-summary',
  'wpb-workout-exercise-link','wpb-workout-day-note','wpb-workout-day-note-copy']){
 assert.ok(css.includes('#root .wpb-workout .'+cls+'{'), 'Missing fixed header CSS owner: '+cls);
}
assert.ok(workout.includes('"aria-label": "Workout completion"') &&
  workout.includes('"aria-valuenow": doneSets') &&
  workout.includes('"aria-label": "Show today\'s exercises"') &&
  workout.includes('"aria-label": "Leave workout"'),
  'Progress, exercise navigation and Leave actions retain their state owners');
assert.ok(!workout.includes('className: "wpb-workout-header", style: { padding:') &&
  !workout.includes('className: "wpb-workout-summary", style:') &&
  !workout.includes('className: "pressable hit wpb-workout-close", style:'),
  'Static workout header styles must not be duplicated inline');
