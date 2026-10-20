import assert from 'node:assert/strict';
import fs from 'node:fs';
const css=fs.readFileSync('app.css','utf8');
const app=fs.readFileSync('modules/App.js','utf8');
const region=css.slice(css.indexOf('/* B892 / UI polish pass 02'),css.indexOf('/* B893 · Custom-cycle composer'));
assert.ok(region.length>3800,'Profile polish rule group is present');
assert.match(region,/grid-template-columns:minmax\(0,1fr\) 48px 48px minmax\(74px,auto\)/,'normal Profile grid has 48px targets');
assert.match(region,/@media\(max-width:380px\)/,'phone layout allows full-width section names');
assert.match(region,/grid-column:1\/-1/,'narrow phone labels get their own row');
assert.match(region,/@media\(prefers-reduced-motion:no-preference\)/,'micro-motion is opt-in');
assert.match(region,/@media\(forced-colors:active\)/,'system high contrast is preserved');
assert.match(region,/button:focus-visible/,'visible keyboard focus');
assert.match(region,/\.wpb-profile .wpb-profile-edit\{min-height:48px!important/,'inline profile edit sizing corrected');
assert.match(region,/Share Strength Score"\]\{width:48px!important/,'inline strength share size corrected');
assert.match(app,/function ProfileCustomization\(\{ prefs, onChange, onClose \}\)/,'same Profile state owner');
assert.match(app,/localStorage\.setItem\(PROFILE_PREFERENCE_KEY, JSON\.stringify\(safe\)\)/,'same persisted preferences');
assert.doesNotMatch(region,/@keyframes|animation:/,'no competing row transition or screen animation');

const b912=css.slice(css.lastIndexOf('/* B912 — Prototype-informed Profile hierarchy'));
assert.ok(b912.startsWith('/* B912 — Prototype-informed Profile hierarchy'),'scoped visual pass is present');
assert.match(b912,/\.wpb-profile-strength-grid\{[\s\S]*?grid-template-columns:minmax\(0,1fr\)!important/,'strength compares actual patterns in compact rows');
assert.match(b912,/\.wpb-profile-toolbar\{[\s\S]*?order:80/,'secondary actions move after profile content');
assert.ok(b912.includes('.wpb-profile-customize')&&b912.includes('order:81'),'customization remains accessible below actions');
assert.ok(b912.includes('@container pi-r77-profile')&&b912.includes('max-width:360px'),'narrow/large-text layout handled');
assert.ok(b912.includes('prefers-reduced-motion:reduce')&&b912.includes('forced-colors:active'),'motion and forced-colors');
assert.doesNotMatch(b912,/@keyframes|animation:/,'no second animation system');

console.log('PASS B907 Profile: tactile spacing, tonal selection, reduced-motion/high-contrast, persistent preferences.');
