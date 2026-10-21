import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
const plan=app.slice(app.indexOf('function PlanView('),app.indexOf('function sessionLoadText('));
assert.ok(plan.includes('className: "wpb-plan-pane-tab"')&&plan.includes('kind: "tab"'),'semantic Plan tab choice');
assert.ok(plan.includes('className: "wpb-plan-week-chip"')&&plan.includes('onClick: () => pickWeek(w.week)'),'week behavior');
assert.ok(plan.includes('"aria-label": `Week ${w.week}`'),'accessible week label');
const choices=plan.slice(plan.indexOf('className: "wpb-plan-panes"'),plan.indexOf('pane === "schedule" && (() =>',plan.indexOf('className: "wpb-plan-panes"')));
assert.ok(choices.length>1000&&!choices.includes('style: {'),'no per-choice static styling');
for(const selector of [
  '#root[data-pi-motion="expressive"] .wpb .wpb-plan-panes>.wpb-plan-pane-tab{',
  '.wpb-plan-pane-label{', '.wpb-plan-week-picker{',
  '#root .wpb .wpb-ui-choice[data-choice-kind="summary"]{',
  '.wpb-ui-choice-label{', '.wpb-ui-choice-caption{'
]) assert.equal(css.split('\n'+selector).length-1,1,'one presentation rule: '+selector);
assert.ok(plan.includes('kind: "summary"'), 'Plan weeks reuse the shared labelled choice');
const blockNav=app.slice(app.indexOf('function CycleBlockNav('), app.indexOf('function CycleBlockPreview('));
assert.ok(blockNav.includes('kind: "summary"')&&!blockNav.includes('style: {'), 'cycle navigation reuses the same choice');
assert.ok(!app.includes('function WeekPill('), 'no private competing week renderer');
console.log('PASS selector ownership: shared labelled choice, existing pane and week navigation semantics.');
