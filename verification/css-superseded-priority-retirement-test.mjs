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
const libraryPaint=css.split(librarySurface)[1]?.split('}')[0]||'';
assert.ok(libraryPaint.includes('background-image:none!important;') &&
 libraryPaint.includes('background-color:var(--pi-m3e-surface-container)!important;'),
 'Library header keeps the original first shared surface painter');
assert.ok(!css.includes(librarySurface.replace(/\{$/,'')+',\n#root[data-pi-motion="expressive"] .wpb-exercise-detail'),
 'Exercise Detail is no longer redundantly included in the early Library owner');

const r20='#root[data-pi-motion="expressive"] .wpb-exercise-detail.wpb-exercise-detail.wpb-exercise-detail\n  .wpb-exercise-detail-header{';
const r11='#root[data-pi-motion="expressive"] .wpb-exercise-detail.wpb-exercise-detail.wpb-exercise-detail .wpb-exercise-detail-header{';
const r20Body=css.split(r20)[1]?.split('}')[0]||'';
const r11Body=css.split(r11)[1]?.split('}')[0]||'';
assert.ok(r20Body.includes('align-items:center!important;') &&
 r20Body.includes('padding-bottom:10px!important;') &&
 !r20Body.includes('background-color:'),
 'R20 Exercise Detail layout survives without an obsolete paint declaration');
assert.ok(r11Body.includes('background-color:var(--pi-r11-quiet)!important;') &&
 r11Body.includes('background-image:none!important;') &&
 r11Body.includes('border-bottom:1px solid var(--pi-r11-edge)!important;'),
 'R11 remains the later, actual Exercise Detail paint owner');
console.log('PASS B1019: three superseded CSS priority owners retired with final paint intact.');
