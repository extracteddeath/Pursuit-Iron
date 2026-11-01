import assert from 'node:assert/strict';
import fs from 'node:fs';
import { auditUiElements } from '../scripts/audit-ui-elements.mjs';
import { uiStyleBudget } from './ui-style-budgets.mjs';
import { uiElementBudget } from './ui-element-budgets.mjs';

const plan=fs.readFileSync('modules/plan-session-surfaces.js','utf8');
const review=fs.readFileSync('modules/program-review-surfaces.js','utf8');
const css=fs.readFileSync('app.css','utf8');
assert.ok(plan.includes('import { UiAction, UiCard }') &&
 plan.includes('return _jsxs(UiCard, { as: "section", className: "wpb-session-game-plan"') &&
 plan.includes('_jsxs(UiCard, { as: "section", className: "wpb-plan-change-card"'),
 'Home/Plan review panels share UiCard without altering their semantic section tags');
assert.ok(plan.includes('"aria-label": "Today\\'s game plan"') ||
 plan.includes('"aria-label": "Today\'s game plan"'),
 'Game-plan panel retains its accessible name');
assert.ok(review.includes('_jsx(UiAction, { variant: "primary", disabled: !changes.length || !!conflicts.length') &&
 review.includes('_jsx(UiAction, { variant: "tonal", onClick: onClose') &&
 review.includes('children: "Save reviewed changes"') &&
 review.includes('children: "Keep editing"'),
 'Save and Keep editing use shared action variants with existing callbacks and disabled semantics');
assert.ok(!css.includes('.wpb-save-review-confirm:disabled{opacity:.45}') &&
 !css.includes('.wpb-plan-change-card{margin:14px 0;padding:15px;border:1px') &&
 !css.includes('#root .wpb-session-game-plan{padding:14px;margin-top:14px;border:1px'),
 'Superseded surface and action paint removed, not papered over');
const result=auditUiElements();
assert.ok(result.totals.rawInteractive<=uiElementBudget.rawInteractive &&
 result.totals.rawSurfaceCandidates<=uiElementBudget.rawSurfaceCandidates &&
 result.totals.shared>=uiElementBudget.minSharedComponents);
assert.ok(css.length<=uiStyleBudget.cssCharacters &&
 (css.match(/!important/g)||[]).length<=uiStyleBudget.cssPriorityDeclarations);
console.log('PASS Home/Plan card and Program review footer adoption.');
