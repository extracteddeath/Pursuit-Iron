import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
const plan=app.slice(app.indexOf('function PlanView('),app.indexOf('function sessionLoadText('));
assert.ok(plan.includes('className: "wpb-plan-pane-tab"')&&plan.includes('kind: "tab"'),'semantic Plan tab choice');
assert.ok(plan.includes('className: "wpb-plan-week-chip"')&&plan.includes('onClick: () => pickWeek(w.week)'),'week behavior');
assert.ok(plan.includes('"aria-label": `Week ${w.week}`'),'accessible week label');
const choices=plan.slice(plan.indexOf('className: "wpb-plan-panes"'),plan.indexOf('pane === "schedule" && (() =>',plan.indexOf('className: "wpb-plan-panes"')));
assert.ok(choices.length>1000&&!choices.includes('style: {'),'no per-choice static styling');
const owner=css.slice(css.indexOf('/* Cohesion 12 —'));
assert.ok(owner.length>1000,'Cohesion 12 base style block must be present');
for(const s of ['.wpb-plan-pane-tab{','.wpb-plan-pane-label{','.wpb-plan-week-picker{','.wpb-plan-week-chip{','.wpb-plan-week-label{','.wpb-plan-week-meta{'])
  assert.equal(owner.split('\n'+s).length-1,1,'one standalone Cohesion 12 base rule: '+s);
console.log('PASS Plan selector ownership: one CSS presentation, existing semantics and navigation retained.');
