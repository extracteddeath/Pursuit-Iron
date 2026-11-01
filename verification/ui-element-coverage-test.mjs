import assert from 'node:assert/strict';
import fs from 'node:fs';
import { auditUiElements } from '../scripts/audit-ui-elements.mjs';
import { uiStyleBudget } from './ui-style-budgets.mjs';
const result=auditUiElements();
assert.ok(result.totals.jsxFiles>=15,'Inventory must include the extracted UI modules, not just App.js');
assert.ok(result.totals.elements>500&&result.totals.native>400,'Every native JSX call is in scope');
for(const owner of ['Home','Plan','Program','Next workout','Cycles','Wizard','Library',
    'Progress','Profile','Settings','Gallery','Workout'])
 assert.ok(result.perOwner.some(x=>x.owner===owner&&x.elements>0),'Missing screen: '+owner);
assert.ok(result.totals.rawInteractive>0 && result.totals.rawSurfaceCandidates>0,
 'Track raw interactive controls and surfaces even if they have no inline style');
assert.ok(result.crossOwnerClassCount>0 && result.totals.staticClassTokens>100,
 'Inspect stylesheet class reuse, not only inline styles');
assert.ok(result.css.rules>3000&&result.css.repeatedSelectors>50,
 'Include all stylesheet rules and scoped cascade overlap');
assert.equal(result.totals.elements,result.totals.native+result.totals.shared);
const css=fs.readFileSync('app.css','utf8');
assert.equal(result.css.characters,css.length);
assert.ok(result.css.characters<=uiStyleBudget.cssCharacters&&
 result.css.priorityDeclarations<=uiStyleBudget.cssPriorityDeclarations,
 'Do not add CSS/priority debt to enable the audit');
assert.ok(result.scope.includes('not a rendered DOM'));
console.log('PASS full JSX element audit:',JSON.stringify(result.totals));
console.log('REVIEW_QUEUE',JSON.stringify(result.reviewQueue.slice(0,16)));
