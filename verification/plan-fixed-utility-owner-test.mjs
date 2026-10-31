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
assert.equal((plan.match(/style:\s*\{/g)||[]).length,14,'Plan styling retirement must not regress');
assert.ok(plan.includes('className: "mono pi-t-muted wpb-s956-fontsize-13 wpb-s956-margintop-2"'),
  'Week set/time metadata must use the shared muted text utilities');
assert.ok(plan.includes('className: "wpb-plan-glance", children:'),
  'The Plan metric grid must defer its geometry to the existing expressive CSS owner');
assert.ok(css.includes('.wpb-plan-view .wpb-plan-glance{'),
  'The already established responsive metric-grid owner must remain available');

assert.equal(report.appInlineStyleObjects,795,'App style debt must reflect Plan B1016 retirement');
assert.equal(uiStyleBudget.appInlineObjects,795,'Budget ratchet must track actual app');
assert.equal(css.length,uiStyleBudget.cssCharacters,'Plan migrated presentation must have measured CSS budget');
for(const cl of ['wpb-s956-display-block','wpb-s956-margintop-2','wpb-s956-overflow-hidden','wpb-s956-textoverflow-ellipsis','wpb-s956-whitespace-nowrap','wpb-s956-flexshrink-0','wpb-s956-textalign-right','wpb-type-body'])
 assert.ok(plan.includes(cl)&&css.includes('.'+cl),'Plan must reuse existing owner '+cl);
assert.ok(plan.includes('onClick: () => onOpenDay && onOpenDay(d.dayId, w.week)')&&
 plan.includes('onClick: () => setOpenWeek(isOpen ? null : w.week)')&&
 plan.includes('onClick: () => onOpenProgram(openWeek || plan.currentWeek)'),
 'Plan native navigation and week disclosure callbacks remain');
assert.ok(plan.includes('role: "group"') &&
 plan.includes('pane === "schedule" && featuredWeek'),
 'Plan selected week and disclosure structure remain');
for(const role of ['wpb-plan-eyebrow','wpb-plan-open-cue','wpb-plan-next-label',
 'wpb-plan-phase-header','wpb-plan-phase-name','wpb-plan-completion-summary',
 'wpb-plan-completion-percent','wpb-plan-completion-track','wpb-plan-completion-fill'])
 assert.ok(plan.includes(role)&&css.includes('.'+role),'B1016 Plan CSS owner missing '+role);
assert.ok(plan.includes('role: "progressbar"')&&plan.includes('"aria-label": "Program completion"')&&
 plan.includes('"aria-valuenow": Math.max(0, Math.min(100, plan.percentDone))'),
 'Program completion must expose its real clamped percent to assistive technology');
assert.ok(plan.includes('style: { width: `${Math.max(0, Math.min(100, plan.percentDone))}%` }'),
 'Only the calculated progress width remains inline');
assert.ok(!plan.includes('style: { height: 4, borderRadius: 999, background: C.bg2'),
 'Completion track should have one token-owned paint source');
assert.ok((css.match(/!important/g)||[]).length <= uiStyleBudget.cssPriorityDeclarations,'No priority overrides added');
console.log('PASS Plan shared utilities: eight more inline presentations retired, semantic completion intact.');
