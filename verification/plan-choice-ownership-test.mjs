import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
const plan=app.slice(app.indexOf('function PlanView('),app.indexOf('function sessionLoadText('));
assert.ok(plan.includes('className: "wpb-plan-panes"')&&plan.includes('kind: "tab"'),'tested expressive pane stays native');
assert.ok(plan.includes('className: "wpb-plan-week-chip"')&&plan.includes('onClick: () => pickWeek(w.week)'),'week behavior');
assert.ok(plan.includes('"aria-label": `Week ${w.week}`'),'accessible week label');
const choices=plan.slice(plan.indexOf('className: "wpb-hscroll wpb-plan-week-picker"'),plan.indexOf('pane === "schedule" && (() =>',plan.indexOf('className: "wpb-hscroll wpb-plan-week-picker"')));
assert.ok(choices.length>300&&!choices.includes('style: {'),'week selector has no per-chip presentation objects');
const owner=css.slice(css.indexOf('/* Cohesion 12 —'));
assert.ok(owner.length>1000,'Cohesion 12 base style block must be present');
for(const s of ['.wpb-plan-week-picker{','.wpb-plan-week-chip{','.wpb-plan-week-label{','.wpb-plan-week-meta{'])
  assert.equal(owner.split('\n'+s).length-1,1,'one standalone Cohesion 12 base rule: '+s);
console.log('PASS Plan selector ownership: CSS-owned week selector, tested mode tabs and semantic navigation retained.');
