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
assert.equal((plan.match(/style:\s*\{/g)||[]).length,22,'Plan utility adoption and owner retirement must not regress');
assert.equal(report.appInlineStyleObjects,803,'App style debt ceiling must reflect five retired values');
assert.equal(uiStyleBudget.appInlineObjects,803,'Budget ratchet must track actual app');
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
assert.equal((css.match(/!important/g)||[]).length,2024,'No priority overrides added');
console.log('PASS Plan shared utilities: five fixed presentations retired, navigation intact.');
