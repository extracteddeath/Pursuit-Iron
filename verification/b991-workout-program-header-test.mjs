import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const workout=app.slice(app.indexOf('function WorkoutSession('),app.indexOf('function shareSession('));
const program=app.slice(app.indexOf('className: "wpb-page-header wpb-program-header"'),app.indexOf('className: "wpb-program-meta-line"'));
for(const feature of ['"data-paused": runPaused ? "true" : "false"','"data-focus": focus ? "1" : "0"','"data-compact": narrowSet ? "true" : "false"','wpb-workout-timer-value','wpb-workout-save-error','wpb-workout-empty-icon','wpb-workout-empty-copy','wpb-workout-empty-add'])
  assert.ok(workout.includes(feature),'Workout role/state missing: '+feature);
for(const feature of ['wpb-program-header-back','wpb-program-icon-change','wpb-program-rename-input','wpb-program-rename-save'])
  assert.ok(program.includes(feature),'Program role missing: '+feature);
assert.ok(!workout.includes('className: "pressable wpb-workout-timer", style:'),'Timer palette must not recompute inline');
assert.ok(!workout.includes('wpb-workout-header-action", style: narrowSet'),'Finish state must stay in CSS');
assert.ok(!program.includes('style: { ...iconBtn(), width: 36, height: 36'),'Program name controls must stay CSS-owned');
for(const selector of ['.wpb-workout-timer[data-paused="true"]','.wpb-workout-header-action[data-focus="1"]','.wpb-workout-finish[data-compact="true"]','.wpb-program-rename-input','.wpb-program-icon-change'])
  assert.ok(css.includes(selector),'Missing CSS role '+selector);
assert.ok(!css.slice(css.indexOf('/* B991:')).includes('!important'),'No priority overrides in new CSS');
assert.ok((css.match(/!important/g)||[]).length<=2060,'No priority-debt increase');
assert.ok((app.match(/style: \\{/g)||[]).length<=1101,'No new inline presentation');
console.log('PASS B991: Workout and Program header controls retain callback ownership and CSS styling.');
