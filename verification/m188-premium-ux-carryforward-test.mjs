import assert from 'node:assert/strict';
import fs from 'node:fs';

const css=fs.readFileSync(new URL('../app.css',import.meta.url),'utf8');
const sw=fs.readFileSync(new URL('../sw.js',import.meta.url),'utf8');
const profile=JSON.parse(fs.readFileSync(new URL('../BUILD_PROFILE.json',import.meta.url),'utf8'));

assert.equal(profile.milestone,'M188');
assert.equal(profile.uiMilestone,'M188 Production Torture + Certification');
assert.equal(profile.cache,'pursuit-iron-production-m188-torture-certification');
assert.match(sw,/const CACHE="pursuit-iron-production-m188-torture-certification"/,'M188 service worker cache identity must be current');
assert.match(sw,/"\.\/app\.css"/,'premium stylesheet must remain in the offline shell');

for(const token of ['--pi-radius-sm','--pi-radius-md','--pi-radius-lg','--pi-motion-fast','--pi-motion-standard','--pi-ease'])
  assert.ok(css.includes(token),`missing carried premium UI token ${token}`);
assert.match(css,/#root h1,#root h2,#root h3,#root h4/,'shared heading hierarchy regressed');
assert.match(css,/#root button,#root \[role="button"\]/,'shared button interaction rules regressed');
assert.match(css,/button:active:not\(:disabled\)/,'tactile pressed feedback regressed');
assert.match(css,/input\[type="number"\]/,'workout numeric contract regressed');
assert.match(css,/font-variant-numeric:tabular-nums lining-nums/,'stable numeric alignment regressed');
assert.match(css,/\[role="dialog"\]/,'sheet/dialog containment regressed');
assert.match(css,/\[role="tab"\]\[aria-selected="true"\]/,'selected-tab hierarchy regressed');
assert.match(css,/scrollbar-width:none/,'inner app scrollbar suppression regressed');
assert.match(css,/prefers-reduced-motion:reduce/,'reduced-motion contract regressed');
assert.match(css,/prefers-contrast:more/,'contrast contract regressed');
assert.match(css,/:focus-visible/,'focus visibility regressed');
assert.doesNotMatch(css,/#root button[^}]*min-height\s*:\s*44px/i,'compact workout steppers were globally enlarged');
assert.doesNotMatch(css,/#root button[^}]*height\s*:\s*44px/i,'compact workout steppers were globally forced to 44px');

console.log('PASS M188 premium UX carry-forward contract.');
