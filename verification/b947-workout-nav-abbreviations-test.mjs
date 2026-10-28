import assert from 'node:assert/strict';
import fs from 'node:fs';
import { workoutNavLabel } from '../modules/workout-nav-labels.js';

for (const [name, compact] of [
    ['Barbell Bench Press', 'BB Bench Press'],
    ['Machine Shoulder Press', 'Mach. Shoulder Press'],
    ['Dumbbell Lateral Raise', 'DB Lateral Raise'],
    ['Triceps Extension', 'Triceps Ext.'],
    ['Incline Dumbbell Press', 'Incl. DB Press'],
    ['Overhead Cable Triceps Extension', 'OH Cable Triceps Ext.'],
    ['Alternating Dumbbell Curl', 'Alt. DB Curl'],
    ['Decline Barbell Bench Press', 'Decl. BB Bench Press'],
    ['Smith Machine Shoulder Press', 'Smith Mach. Shoulder Press'],
    ['Reverse Grip Extension', 'Rev. Grip Ext.'],
    ['Chest Supported Dumbbell Row', 'Chest Supp. DB Row'],
    ['Back Extensions', 'Back Ext.'],
    ['Dumbbell Lateral Raise', 'DB Lateral Raise'],
    ['Hyperextension', 'Hyperextension'],
    ['BB Bench Press', 'BB Bench Press'],
    ['DB Lateral Raise', 'DB Lateral Raise']
]) {
    assert.equal(workoutNavLabel(name), compact, name);
    assert.equal(workoutNavLabel(compact), compact, 'applying the label twice is idempotent');
}
assert.equal(workoutNavLabel(''), 'Exercise');
assert.equal(workoutNavLabel(null), 'Exercise');
assert.equal(workoutNavLabel('  Machine   Shoulder Press '), 'Mach. Shoulder Press');
const app = fs.readFileSync('modules/App.js', 'utf8');
const nav = app.slice(app.indexOf('function WorkoutExerciseNav('), app.indexOf('function WorkoutRestActions('));
assert.match(nav, /children: workoutNavLabel\(name\)/, 'only the visible pill uses abbreviated labels');
assert.match(nav, /const name = EX_BY_ID\[row.id\]\?\.name/, 'resolve original canonical exercise title');
assert.match(nav, /"aria-label": `\$\{name\}/, 'screen readers get the full name and set progress');
assert.match(nav, /title: name/, 'full hover label is retained');
assert.match(nav, /children: \[done, "\/", work.length\]/, 'working set count is preserved');
const workout = app.slice(app.indexOf('function WorkoutSession('));
assert.ok(workout.includes('ex.name'), 'workout title still displays the original exercise name');
assert.equal((app.match(/workoutNavLabel\(name\)/g) || []).length, 1,
    'do not rewrite history, exercise library, notifications or program title names');
const css = fs.readFileSync('app.css','utf8');
assert.match(css, /\.wpb-ex-nav-name\{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0;/,
    'names remain one line and do not push counts out of the rail');
assert.match(css, /\.wpb-ex-nav-count\{flex-shrink:0;/,
    'set count cannot be truncated in a dense rail');
console.log('PASS B947 workout nav labels: common gym abbreviations, idempotence, accessible full names, stable set progress');
