import assert from 'node:assert/strict';
import fs from 'node:fs';
import { auditUiElements } from '../scripts/audit-ui-elements.mjs';
import { uiStyleBudget } from './ui-style-budgets.mjs';
import { uiElementBudget } from './ui-element-budgets.mjs';

// Five actual native button call sites become one action system across
// Home/Plan, Program volume and reusable disclosure. One native info-card
// section also adopts UiCard. Existing ARIA, callbacks and DOM tags remain.
const source={
 plan:fs.readFileSync('modules/plan-session-surfaces.js','utf8'),
 volume:fs.readFileSync('modules/program-volume-surfaces.js','utf8'),
 disclosure:fs.readFileSync('modules/ui-disclosure.js','utf8'),
 css:fs.readFileSync('app.css','utf8')
};
for(const [code,roles] of [
 [source.plan,['wpb-plan-change-banner','wpb-home-outline-toggle','wpb-plan-undo']],
 [source.volume,['wpb-volume-detail-toggle']],
 [source.disclosure,['wpb-info-card-toggle']]]){
 for(const role of roles){
  assert.ok(code.includes(role),'Feature CSS class and callback owner retained: '+role);
  assert.ok(!code.includes('className: "'+role+' pressable"') &&
    !code.includes("className: 'pressable "+role+"'"),
    'Control no longer owns a raw native pressable class: '+role);
 }
}
assert.ok(source.plan.includes('_jsxs(UiAction, { variant: "tonal", "data-emphasis": "accent"') &&
 source.plan.includes('_jsxs(UiAction, { variant: "text", className: "wpb-home-outline-toggle"') &&
 source.plan.includes('_jsx(UiAction, { variant: "tonal", className: "wpb-plan-undo"'),
 'Plan/Home use one shared action anatomy with the intended variants');
assert.ok(source.volume.includes('_jsxs(UiAction, { variant: "tonal"') &&
 source.volume.includes('"data-emphasis": granular ? "accent" : undefined') &&
 source.volume.includes('"aria-expanded": granular') &&
 source.volume.includes('setGranular(g => !g)'),
 'Volume disclosure keeps its state writer, expanded semantics and active presentation');
assert.ok(source.disclosure.includes("_jsxs(UiCard, { as: 'section'") &&
 source.disclosure.includes("_jsx(UiAction, { variant: 'text'") &&
 source.disclosure.includes("'aria-controls': contentId") &&
 source.disclosure.includes("'aria-expanded': isOpen"),
 'Info disclosure uses UiCard/UiAction while retaining section, heading and interaction semantics');
assert.ok(source.plan.includes('onClick: () => onChoice(review.programId') &&
 source.plan.includes('disabled: busy') &&
 source.plan.includes('"aria-controls": listId') &&
 source.plan.includes('"aria-expanded": expanded'),
 'Plan review, undo and Home toggle preserve their original actions');
assert.ok(!source.css.includes('.wpb-plan-undo:disabled{opacity:.5}') &&
 !source.css.includes('.wpb-volume-detail-toggle[aria-expanded="true"]{'),
 'Feature-specific duplicate action states retired in favor of shared state owners');
const inventory=auditUiElements();
assert.ok(inventory.totals.rawInteractive<=uiElementBudget.rawInteractive &&
 inventory.totals.rawSurfaceCandidates<=uiElementBudget.rawSurfaceCandidates &&
 inventory.totals.shared>=uiElementBudget.minSharedComponents,
 'Shared action and card migration must lower actual element ownership debt');
assert.ok(source.css.length<=uiStyleBudget.cssCharacters &&
 (source.css.match(/!important/g)||[]).length<=uiStyleBudget.cssPriorityDeclarations);
console.log('PASS cross-screen actions: five native controls and one card use shared owners.');
