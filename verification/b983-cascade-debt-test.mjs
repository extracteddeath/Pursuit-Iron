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
assert.ok(css.includes('#root .wpb-workout :is(.wpb-workout-tools .wpb-workout-tool,.wpb-rest-actions>.wpb-rest-action){') &&
  css.includes('flex:0 0 48px;width:48px;min-width:48px;height:48px;min-height:48px;padding:0') &&
  css.includes('#root .wpb-workout .wpb-rest-actions>.wpb-rest-action{'),
  'Workout and Rest share a real 48px icon-action sizing owner');
assert.ok((css.match(/!important/g)||[]).length<=2024,
  'Workout Coach and set options shared focus debt capped at 2027');
// Preserve Build 996's visual-viewport fix without relaxing design CSS debt.
const viewportFix='bottom:auto;height:min(100dvh,var(--visual-h,var(--app-h,100dvh)));';
assert.ok(css.includes('.wpb-backdrop.wpb-b961-overlay-09{position:fixed;inset:0;'+viewportFix),
  'Workout Info must stay within the visual viewport');
const searchRecoveryRule='#root .wpb .wpb-progression-search-row{display:flex;align-items:center;gap:var(--pi-space-2);margin-bottom:8px}\n';
const searchClearRule='#root .wpb .wpb-progression-search-clear:disabled{visibility:hidden}';
assert.ok(css.includes(searchRecoveryRule)&&css.includes(searchClearRule),
  'New search UI must use only its two named compact presentation owners');
const reviewScopeRule="#root .wpb .wpb-program-review-scope{display:grid;gap:var(--pi-space-2);margin:var(--pi-space-3) 0}\n#root .wpb .wpb-program-review-scope p{display:grid;gap:2px;margin:0;font-size:var(--pi-type-support);line-height:1.45;overflow-wrap:anywhere}\n#root .wpb .wpb-program-review-scope strong{color:var(--wpb-theme-muted);font-size:var(--pi-type-label)}\n";
assert.ok(css.includes(reviewScopeRule), 'Compact program-impact grid must use a single named semantic style owner');
// Explicitly segregate this fixed CSS role migration from legacy stylesheet debt.
const b1003Start=css.indexOf('/* B1003 — shared fixed type roles');
assert.ok(b1003Start>=0,'B1003 CSS role block is present');
const b1003SharedTypeRules=css.slice(b1003Start);
assert.ok(b1003SharedTypeRules.length<=731,'B1003 type roles must stay compact');
const b1003RoleNames=["body-muted","value","subline","strong-label","footnote","body","helper"];
for(const role of b1003RoleNames)
  assert.ok(css.includes('#root .wpb .wpb-type-'+role+'{'),'Missing B1003 type owner '+role);
for(const old of ["style: { fontSize: 13, color: C.muted, lineHeight: 1.5 }","style: { fontSize: 15, fontWeight: 600, color: C.text }","style: { fontSize: 12, color: C.muted, marginTop: 2 }","style: { fontSize: 13, fontWeight: 700, color: C.text }","style: { fontSize: 11, color: C.faint, marginTop: 8, lineHeight: 1.4 }","style: { fontSize: 13, color: C.text, lineHeight: 1.45 }","style: { fontSize: 13, color: C.muted, marginTop: 2, lineHeight: 1.4 }"])
  assert.ok(!app.includes(old),'Old inline type object restored: '+old);
assert.ok(css.replace(viewportFix,'').replace(searchRecoveryRule,'').replace(searchClearRule,'').replace(reviewScopeRule,'').replace(b1003SharedTypeRules,'').length<=637454,
  'Shared Program sheet chrome must not grow stylesheet debt');
assert.ok((app.match(/style: \{/g)||[]).length<=1003,
  'Do not restore inline Workout geometry to replace deleted CSS');
assert.ok(app.includes('copyProgressionDiagnostics = async () =>') &&
  app.includes('canonicalExerciseIncrement(program.exLoadInc?.[ex.id], unit)'),
  'Build 996 diagnostics and physical loading-step repairs must survive UI integration');
assert.ok(app.length+css.replace(viewportFix,'').replace(searchRecoveryRule,'').replace(searchClearRule,'').replace(reviewScopeRule,'').replace(b1003SharedTypeRules,'').length<=2668250,
  'Combined App and stylesheet must not grow after sheet-chrome consolidation');

// B1002: five shared type roles replace 58 repeated fixed-paint objects.
const sharedRoles=['faint','support-muted','caption-faint','section-intro','confirm-copy'];
for(const role of sharedRoles)
  assert.ok(css.includes('#root .wpb .wpb-type-'+role+'{'),'Shared type owner missing: '+role);
const replacedStyles=[
 'style: { color: C.faint }',
 'style: { fontSize: 13, color: C.muted }',
 'style: { fontSize: 11, color: C.faint }',
 'style: { fontSize: 13, color: C.muted, marginBottom: 12 }',
 'style: { fontSize: 15, color: C.muted, marginBottom: 16, lineHeight: 1.5 }'
];
for(const literal of replacedStyles)
  assert.ok(!app.includes(literal),'Duplicate inline type style restored: '+literal);
assert.ok(!css.includes('@media(max-width:350px){\n  #root[data-pi-motion="expressive"] .wpb{\n'),
  'Inert theme selector must stay removed');
console.log('PASS B983: one canonical role per material, superseded CSS retired and priority/size budgets lowered.');
