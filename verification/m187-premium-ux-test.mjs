import assert from 'node:assert/strict';
import fs from 'node:fs';

const css=fs.readFileSync(new URL('../app.css',import.meta.url),'utf8');
const sw=fs.readFileSync(new URL('../sw.js',import.meta.url),'utf8');
const profile=JSON.parse(fs.readFileSync(new URL('../BUILD_PROFILE.json',import.meta.url),'utf8'));

assert.equal(profile.milestone,'M187');
assert.equal(profile.uiMilestone,'M187 Premium UX Audit + Cross-Screen Refactor');
assert.equal(profile.cache,'pursuit-iron-production-m187-premium-ux');
assert.match(sw,/const CACHE="pursuit-iron-production-m187-premium-ux"/,'service worker must rotate cache so the UX release actually reaches installed PWAs');
assert.match(sw,/"\.\/app\.css"/,'premium stylesheet must remain in the offline shell');

// Shared interaction contract: one place protects Home, Program, Workout, Progress, Settings and Onboarding.
for(const token of ['--pi-radius-sm','--pi-radius-md','--pi-radius-lg','--pi-motion-fast','--pi-motion-standard','--pi-ease'])
  assert.ok(css.includes(token),`missing premium UI token ${token}`);
assert.match(css,/#root h1,#root h2,#root h3,#root h4/,'heading hierarchy must be shared across screens');
assert.match(css,/#root button,#root \[role="button"\]/,'button interaction rules must cover native and role buttons');
assert.match(css,/button:active:not\(:disabled\)/,'controls need tactile pressed feedback');
assert.match(css,/input\[type="number"\]/,'workout numeric entry must have a dedicated numeric contract');
assert.match(css,/font-variant-numeric:tabular-nums lining-nums/,'weights, reps and progress metrics need stable numeric alignment');
assert.match(css,/\[role="dialog"\]/,'sheets/dialogs need contained scrolling behavior');
assert.match(css,/\[role="tab"\]\[aria-selected="true"\]/,'tab state must have an explicit hierarchy cue');
assert.match(css,/scrollbar-width:none/,'inner app scrollers must not show the giant desktop-style scrollbar');
assert.match(css,/prefers-reduced-motion:reduce/,'motion polish must honor reduced-motion preferences');
assert.match(css,/prefers-contrast:more/,'focus/disabled states must remain legible in increased-contrast mode');
assert.match(css,/:focus-visible/,'keyboard and assistive-tech focus cannot be removed');
assert.match(css,/text-wrap:balance/,'headings and form legends should avoid awkward premium-layout wrapping');

// Do not regress the compact workout steppers by imposing a blanket touch-target height on every button.
assert.doesNotMatch(css,/#root button[^}]*min-height\s*:\s*44px/i,'M187 must not blow compact workout steppers back up to full-size controls');
assert.doesNotMatch(css,/#root button[^}]*height\s*:\s*44px/i,'M187 must not force all workout buttons to 44px');

console.log('PASS M187 premium UX contract: shared hierarchy/interactions, compact workout controls preserved, numeric alignment, sheets, scrolling, focus, contrast, reduced motion, and PWA cache rotation.');
