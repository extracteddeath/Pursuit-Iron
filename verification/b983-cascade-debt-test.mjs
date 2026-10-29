import assert from 'node:assert/strict';
import fs from 'node:fs';

// B983: only proven-shadowed CSS declarations were retired. Keep the canonical
// material roles and interaction geometry in their final owners. This gate is
// intentionally independent of a chosen theme and of the DOM fixture.
const css=fs.readFileSync('app.css','utf8');
const app=fs.readFileSync('modules/App.js','utf8');
const exact=(regexp,label)=>assert.equal((css.match(regexp)||[]).length,1,label);
for(const name of ['quiet','raised','hero','selected','edge','accent-stroke'])
  exact(new RegExp('--pi-r11-'+name+'\\s*:', 'g'),'One canonical tonal role: '+name);
exact(/--pi-r38-nav-indicator-fill\s*:/g,'Navigation indicator has one final role');
exact(/--pi-r36-accent-label\s*:/g,'Accessible accent label has one final role');
assert.ok(css.includes('--pi-r11-quiet:var(--pi-color-card)'),
  'The canonical dark tonal role is sourced from the saved palette');
assert.ok(css.includes('--pi-r11-raised:var(--pi-color-control)'),
  'Raised surfaces still use the saved palette');
assert.ok(css.includes('--pi-r11-edge:var(--pi-color-divider)'),
  'Surface divider retains its canonical palette owner');
assert.ok(!css.includes('box-shadow:inset 3px 0 0 color-mix(in srgb,var(--pi-m3e-primary) 44%,transparent)!important'),
  'Outdated Plan next-session priority shadow must be removed');
assert.ok(css.includes('color-mix(in srgb,var(--pi-m3e-primary) 36%,transparent)!important'),
  'Keep the effective Plan next-session accent edge');
assert.ok(!css.includes('.wpb-home-health{margin:10px 0 18px}'),
  'Dead Home card margin must not reappear');
assert.ok(css.includes('.wpb-home-health{display:flex;align-items:center;gap:11px;'),
  'The current Home health card geometry remains present');
assert.ok(css.includes('.wpb-injury-journal .wpb-session-dialog-heading{position:static'),
  'Injury modal header uses its final nonsticky owner');
assert.ok(css.includes('.wpb-program-revision-footer{bottom:0;'),
  'Program review footer retains its safe-area geometry');
assert.ok(css.includes('.wpb-exercise-tags{display:flex;gap:5px;flex-wrap:wrap;'),
  'Actual exercise tags retain current compact geometry');
assert.ok(css.includes('#root .wpb-workout .wpb-rest-actions>.wpb-rest-action{') &&
  css.includes('min-width:48px;min-height:48px'),
  'Workout rest actions remain accessible after the cleanup');
assert.ok((css.match(/!important/g)||[]).length<=2060,
  'CSS priority budget must not grow after retiring dead overrides');
// B993 replaces fixed Workout-info JSX with themed CSS; this is the new CSS-only ratchet.
assert.ok(css.length<=635281,
  'CSS migration budget must not grow after the B987 Strength and 1RM owners');
assert.ok((app.match(/style: \{/g)||[]).length<=1262,
  'Do not restore inline Workout geometry to replace deleted CSS');
console.log('PASS B983: one canonical role per material, superseded CSS retired and priority/size budgets lowered.');
