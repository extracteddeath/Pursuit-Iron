import assert from 'node:assert/strict';
import fs from 'node:fs';
import { uiStyleBudget } from './ui-style-budgets.mjs';

const css=fs.readFileSync('app.css','utf8');
assert.equal(css.length,uiStyleBudget.cssCharacters,'A real source retirement reduces CSS bytes');
assert.equal((css.match(/!important/g)||[]).length,uiStyleBudget.cssPriorityDeclarations,
 'Retired priority declarations are removed, not replaced by new overrides');

const pressedEarly='#root[data-pi-motion="expressive"] .wpb-wizard.wpb-wizard.wpb-wizard .wpb-wizard-option[aria-pressed="true"]{';
const pressedLate='#root[data-pi-motion="expressive"] .wpb-wizard.wpb-wizard.wpb-wizard\n  .wpb-wizard-option[aria-pressed="true"]{';
const earlyWizard=css.split(pressedEarly)[1]?.split('}')[0]||'';
const laterWizard=css.split(pressedLate)[1]?.split('}')[0]||'';
assert.ok(earlyWizard.includes('background-image:none!important;') &&
 !earlyWizard.includes('border-radius:')&&!earlyWizard.includes('border-color:'),
 'R5 wizard option stops competing with selected R11 borders');
assert.ok(laterWizard.includes('border-radius:19px 25px 25px 25px!important;') &&
 laterWizard.includes('border-color:var(--pi-r11-accent-stroke)!important;'),
 'Selected wizard keeps its actual final shape and accent edge');

// B1053: the Library header has no tonal painter at all; it is a flat top app bar on the
// shared page-header canvas, and its actions are uncontained icon/text buttons.
assert.ok(!css.includes('.wpb-library.wpb-library.wpb-library .wpb-library-header'),
 'Library header has no tonal R11 surface painter');
assert.ok(/\.wpb-library-headline>:is\(\.wpb-library-back,\.wpb-library-find\)\{[^}]*border:0;[^}]*border-radius:50%;[^}]*background:transparent/.test(css),
 'Library header icon actions are standard uncontained icon buttons');

// B1052: the exercise detail header now has one owner, a flat full-width top app bar.
const detailOwner=css.slice(css.indexOf('/* B1052 — Exercise detail'));
assert.ok(/\.wpb-exercise-detail-header\{[^}]*border-radius:0;[^}]*background:var\(--pi-color-canvas\)/.test(detailOwner),
 'Exercise Detail header is a flat top app bar with one paint owner');
assert.ok(!css.includes('#root[data-pi-motion="expressive"] .wpb-exercise-detail.wpb-exercise-detail.wpb-exercise-detail .wpb-exercise-detail-header{'),
 'Superseded R11/R20 header owners are retired');
console.log('PASS B1019: three superseded CSS priority owners retired with final paint intact.');
