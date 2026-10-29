import assert from 'node:assert/strict';
import fs from 'node:fs';

// B990: these legacy CSS names have no owner in the shipped ESM/UI markup.
// A future feature can restore a name only with a corresponding real component.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const absentClasses = [
  'wpb-profile-identity', 'wpb-profile-card-brand',
  'wpb-game-plan-technique', 'wpb-game-plan-exercise-title',
  'wpb-game-plan-method', 'wpb-game-plan-target',
  'wpb-session-pr-chance', 'wpb-plan-held-target',
  'wpb-cycle-week-outlook-deload'
];
const absentVars = [
  '--pi-choice-min', '--pi-choice-overflow', '--pi-layout-radius-large',
  '--pi-layout-wash', '--pi-layout-wash-strong', '--pi-m3-overlay-tonal',
  '--pi-m3e-dark-support', '--pi-m3e-list-corner', '--pi-m3e-list-edge',
  '--pi-m3e-list-gap', '--pi-meta-size',
  '--pi-r2-featured', '--pi-r2-group', '--pi-r2-hero', '--pi-r2-small',
  '--pi-r2-tone', '--pi-r2-tone-strong', '--pi-r38-nav-surface',
  '--pi-r38-surface-2', '--pi-r38-surface-hero', '--pi-r4-tint',
  '--pi-r41-separator', '--pi-r48-page', '--pi-r5-accent',
  '--pi-r5-shadow', '--pi-r5-soft', '--pi-reading-line',
  '--pi-section-gap', '--pi-section-size', '--pi-section-spacing',
  '--pi-space-0', '--pi-title-size', '--pi-workspace-max'
];
assert.equal(absentClasses.length, 9);
assert.equal(absentVars.length, 33);
for (const name of absentClasses) {
  assert.ok(!css.includes('.' + name), 'Unused legacy CSS class returned: ' + name);
  assert.ok(!app.includes(name), 'Live UI resurrected an unstyled legacy class: ' + name);
}
for (const name of absentVars) {
  assert.ok(!new RegExp(name + '(?![\\w-])').test(css),
    'Unused CSS custom property returned: ' + name);
}
for (const kept of ['--pi-m3e-secondary-container','--pi-shape-feature','--pi-radius-md','--pi-radius-lg']) {
  assert.ok(css.includes(kept), 'Public semantic design role must remain: ' + kept);
}
assert.ok(css.includes('#root .wpb-workout .wpb-rest-actions>.wpb-rest-action{'),
  'Canonical workout rest button styles must remain');
assert.ok(css.includes('min-width:48px;min-height:48px'),
  'Workout rest controls must remain accessible');
assert.ok(css.includes('--pi-r11-quiet:var(--pi-color-card)'),
  'Canonical Material 3 Expressive roles must remain');
// B993 allows CSS-owned Workout-info presentation while the inline budget decreases.
assert.ok(css.length <=643592, 'B990 CSS source budget increased');
assert.ok((css.match(/!important/g) || []).length <= 2060,
  'B990 must add no priority overrides');
assert.ok((app.match(/style: \{/g) || []).length <= 1262,
  'B990 must not resurrect retired inline Workout geometry');
console.log('PASS B990: dead selector/role owners pruned and CSS budget lowered.');
