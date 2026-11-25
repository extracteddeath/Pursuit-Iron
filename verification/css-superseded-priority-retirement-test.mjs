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

const librarySurface='#root[data-pi-motion="expressive"] .wpb-library.wpb-library.wpb-library .wpb-library-header{';
// B1046: the early surface-container painter was always overridden by the later R11 list owner
// with the identical selector, so it was removed; the R11 owner is the one that paints.
const libraryOwner='#root[data-pi-motion="expressive"] .wpb-library.wpb-library.wpb-library .wpb-library-header,\n';
const libraryPaint=css.split(libraryOwner)[1]?.split('}')[0]||'';
assert.ok(!css.includes(librarySurface) && libraryPaint.includes('background-image:none!important;') &&
 libraryPaint.includes('background-color:var(--pi-r11-quiet)!important;'),
 'Library header has one painter: the R11 owner');
assert.ok(!css.includes(librarySurface.replace(/\{$/,'')+',\n#root[data-pi-motion="expressive"] .wpb-exercise-detail'),
 'Exercise Detail is no longer redundantly included in the early Library owner');

// B1052: the exercise detail header now has one owner, a flat full-width top app bar.
const detailOwner=css.slice(css.indexOf('/* B1052 — Exercise detail'));
assert.ok(/\.wpb-exercise-detail-header\{[^}]*border-radius:0;[^}]*background:var\(--pi-color-canvas\)/.test(detailOwner),
 'Exercise Detail header is a flat top app bar with one paint owner');
assert.ok(!css.includes('#root[data-pi-motion="expressive"] .wpb-exercise-detail.wpb-exercise-detail.wpb-exercise-detail .wpb-exercise-detail-header{'),
 'Superseded R11/R20 header owners are retired');
console.log('PASS B1019: three superseded CSS priority owners retired with final paint intact.');
