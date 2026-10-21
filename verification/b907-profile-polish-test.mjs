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
assert.match(b912,/\.wpb-profile-toolbar\{[\s\S]*?display:flex!important/,'secondary actions retain compact shared row');
const featuredCell = b912.split('.wpb-profile-strength-grid>.wpb-profile-strength-cell:first-child{')[1]?.split('}')[0];
assert.ok(featuredCell?.includes('background:var(--wpb-theme-accent-dim)!important'), 'one featured score remains tonal');
assert.ok(b912.includes('.wpb-profile-customize')&&!b912.includes('order:80')&&!b912.includes('order:81'),'no visual and keyboard order mismatch');
assert.ok(b912.includes('@container pi-r77-profile')&&b912.includes('max-width:360px'),'narrow/large-text layout handled');
assert.ok(b912.includes('prefers-reduced-motion:reduce')&&b912.includes('forced-colors:active'),'motion and forced-colors');
assert.doesNotMatch(b912,/@keyframes|animation:/,'no second animation system');
assert.ok(b912.includes('border-radius:var(--pi-shape-feature)!important')&&b912.includes('border-radius:var(--pi-shape-card)!important'),'Profile uses shared feature/card geometry roles');


const profileView = app.slice(app.indexOf('function ProfileView('), app.indexOf('\nfunction ',app.indexOf('function ProfileView(')+12));
assert.ok(profileView.includes('className: "wpb-profile-strength-label"'),'strength pattern label has an explicit style owner');
for (const [name, marker] of Object.entries({
  stats:'className: "wpb-profile-stat-grid", "aria-label": "Lifetime training summary", style:',
  strength:'className: "wpb-profile-strength-grid", style:',
  cell:'className: "wpb-profile-strength-cell", style: { minWidth:',
  next:'className: "wpb-profile-next-strength", style: { display:'
})) assert.ok(!profileView.includes(marker),name+' no longer embeds fixed presentation geometry');
const styleOwner = css.slice(css.indexOf('/* B913 / Cohesion 03'),css.indexOf('/* B892 / UI polish pass 02'));
for(const selector of ['.wpb-profile-stat-grid','.wpb-profile-stat','.wpb-profile-strength-grid','.wpb-profile-strength-cell','.wpb-profile-strength-label','.wpb-profile-next-strength']){
  assert.ok(styleOwner.includes(selector+'{'),selector+' has one CSS base declaration');
}
assert.ok(b912.includes('.wpb-profile-strength-cell>.wpb-profile-strength-label'),'Expressive overrides explicitly target the semantic label class');

console.log('PASS B907 Profile: tactile spacing, tonal selection, reduced-motion/high-contrast, persistent preferences.');
