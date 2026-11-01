import assert from 'node:assert/strict';
import fs from 'node:fs';
import { report } from '../scripts/audit-ui-style-ownership.mjs';
import { uiStyleBudget } from './ui-style-budgets.mjs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const start=app.indexOf('function PlanView(');
const end=app.indexOf('\nfunction ',start+1);
const plan=app.slice(start,end);
assert.ok(start>0 && end>start,'Plan has one screen owner');
assert.equal((plan.match(/style:\s*\{/g)||[]).length,20,'Plan utility adoption and owner retirement must not regress');
assert.ok(plan.includes('className: "mono pi-t-muted wpb-s956-fontsize-13 wpb-s956-margintop-2"'),
  'Week set/time metadata must use the shared muted text utilities');
assert.ok(plan.includes('className: "wpb-plan-glance", children:'),
  'The Plan metric grid must defer its geometry to the existing expressive CSS owner');
assert.ok(css.includes('.wpb-plan-view .wpb-plan-glance{'),
  'The already established responsive metric-grid owner must remain available');

assert.equal(report.appInlineStyleObjects,801,'App style debt ceiling must reflect five retired values');
assert.equal(uiStyleBudget.appInlineObjects,801,'Budget ratchet must track actual app');
assert.equal(css.length,uiStyleBudget.cssCharacters,'Plan migration cannot add CSS');
for(const cl of ['wpb-s956-display-block','wpb-s956-margintop-2','wpb-s956-overflow-hidden','wpb-s956-textoverflow-ellipsis','wpb-s956-whitespace-nowrap','wpb-s956-flexshrink-0','wpb-s956-textalign-right','wpb-type-body'])
 assert.ok(plan.includes(cl)&&css.includes('.'+cl),'Plan must reuse existing owner '+cl);
assert.ok(plan.includes('onClick: () => onOpenDay && onOpenDay(d.dayId, w.week)')&&
 plan.includes('onClick: () => setOpenWeek(isOpen ? null : w.week)')&&
 plan.includes('onClick: () => onOpenProgram(openWeek || plan.currentWeek)'),
 'Plan native navigation and week disclosure callbacks remain');
assert.ok(plan.includes('role: "group"') &&
 plan.includes('pane === "schedule" && featuredWeek'),
 'Plan selected week and disclosure structure remain');
assert.ok(plan.includes('className: "wpb-type-value", children: ph.label')&&
 plan.includes('className: "wpb-s956-display-flex wpb-s956-justifycontent-space-between wpb-s956-marginbottom-4 wpb-type-support-muted"'),
 'Plan uses the existing typography and layout owners, not new CSS');
assert.ok(plan.includes('role: "progressbar"')&&
 plan.includes('"aria-label": "Program completion"')&&
 plan.includes('"aria-valuenow": Math.max(0, Math.min(100, plan.percentDone))')&&
 plan.includes('width: `${Math.max(0, Math.min(100, plan.percentDone))}%`'),
 'Progressbar remains data-driven and accessible');
assert.ok((css.match(/!important/g)||[]).length <= uiStyleBudget.cssPriorityDeclarations,'No priority overrides added');
console.log('PASS Plan utilities: two more static objects removed, live progress announced accessibly.');
