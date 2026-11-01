import assert from 'node:assert/strict';
import fs from 'node:fs';
import { uiStyleBudget } from './ui-style-budgets.mjs';
const css=fs.readFileSync('app.css','utf8');
const primitives=fs.readFileSync('modules/ui-primitives.js','utf8');
const plan=fs.readFileSync('modules/plan-session-surfaces.js','utf8');
const motion=fs.readFileSync('modules/ui-motion.js','utf8');

// Published guidance, not a claim that the web UI is Android Compose.
const guide=fs.readFileSync('docs/design/M3E_CURRENT_STANDARD_2026.md','utf8');
for(const source of ['developer.android.com/jetpack/androidx/releases/compose-material3',
 'developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme'])
 assert.ok(guide.includes(source),'The reference standard must have a verifiable official source');
assert.ok(motion.includes('export const MOTION_SCHEMES = Object.freeze({') &&
 motion.includes('standard: Object.freeze({ stiffness: 700, damping: .9 })') &&
 motion.includes('expressive: Object.freeze({ stiffness: 560, damping: .84 })') &&
 motion.includes('options.stiffness ?? motionScheme.stiffness'),
 'Interruptible springs share named M3E standard/expressive roles without changing legacy defaults');
for(const name of ['--pi-m3e-effects-fast:120ms','--pi-m3e-effects-default:160ms',
 '--pi-m3e-spatial-default:380ms','--pi-m3e-ease-effects:'])
 assert.ok(css.includes(name),'Shared motion scheme is missing '+name);
assert.ok(primitives.includes('export function UiIconAction(') &&
 primitives.includes('"aria-label": label') &&
 primitives.includes('"data-icon-only": true') &&
 primitives.includes('return _jsx(UiIconAction, { variant, ...props,'),
 'The icon-action primitive must preserve accessible labeling and sheet close behavior');
assert.ok(primitives.includes('export function UiCard(') &&
 primitives.includes('"data-surface": tone') &&
 plan.includes('tone: "featured"') &&
 css.includes('.wpb .wpb-ui-card[data-surface="featured"]{'),
 'Feature cards have a single expressive tone/shape owner, not inline paint');
assert.ok(css.includes('pointer-events:none') && css.includes('::after{opacity:.12}') &&
 css.includes('::after{opacity:.07}'),
 'Interaction feedback uses an inert state layer with distinct hover and press opacity');
assert.ok(css.includes('@media(prefers-reduced-motion:reduce){\n  #root[data-pi-motion="expressive"] .wpb :is(.wpb-ui-action,.wpb-ui-choice)::after{transition:none}') &&
 css.includes('@media(forced-colors:active){\n  #root[data-pi-motion="expressive"] .wpb :is(.wpb-ui-action,.wpb-ui-choice)::after{display:none}'),
 'No animated state layer for reduced motion and no invisible overlays in forced colors');
assert.equal((css.match(/!important/g)||[]).length,uiStyleBudget.cssPriorityDeclarations,
 'M3 Expressive upgrade cannot add high-specificity priority debt');
assert.equal(css.length,uiStyleBudget.cssCharacters);
console.log('PASS latest Material 3 Expressive motion, surface, icon and a11y contracts.');
