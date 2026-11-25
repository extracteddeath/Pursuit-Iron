import assert from 'node:assert/strict';
import fs from 'node:fs';
const css=fs.readFileSync('app.css','utf8');
const app=fs.readFileSync('modules/App.js','utf8');
// B1030: Profile is identity — the share card (with level), one link to the strength record in
// Progress, and achievements. The reorderable section stack, the lifetime strip, the strength
// grid and the 1,000 lb club were second copies of Progress and are gone.
const profileView = app.slice(app.indexOf('function ProfileView('), app.indexOf('\nfunction ',app.indexOf('function ProfileView(')+12));
// B1055: level sits with the person (name, details, level), above one Edit and one Share; the card is the share preview.
assert.ok(profileView.includes('sectionContent.level,') && profileView.includes('_jsx(ProfileShareCard, { rows: cardRows })'),'Level sits with the person; the card is what Share exports');
assert.equal((profileView.match(/Pencil/g)||[]).length,1,'Profile has one Edit action');
assert.ok(app.includes('function ProfileEditSheet(') && profileView.includes('_jsx(ProfileEditSheet,'),'One sheet edits body details and the share card');
assert.ok(profileView.includes('className: "wpb-profile-strength-link"')&&profileView.includes('onClick: onStrength'),'Strength lives in Progress, reached from one Profile row');
for(const gone of ['wpb-profile-stat-grid','wpb-profile-strength-grid','wpb-profile-toolbar','Arrange profile','ProfileStrengthClub'])
  assert.ok(!profileView.includes(gone),'Profile does not duplicate the Progress record: '+gone);
assert.ok(!app.includes('function ProfileCustomization('),'No reorderable Profile stack remains');
assert.match(app,/localStorage\.setItem\(PROFILE_PREFERENCE_KEY, JSON\.stringify\(safe\)\)/,'Share-card preferences still persist');
assert.ok(css.includes('.wpb-profile-person>.wpb-profile-card-actions>.wpb-ui-action.wpb-ui-action[data-action]{display:inline-flex;align-items:center;width:100%;min-height:48px;'),'Profile Edit/Share are full 48px targets');

const b961Start=css.indexOf('/* B961: 25 existing dialog overlays');
assert.ok(b961Start>css.indexOf('/* B912 — Prototype-informed Profile hierarchy'),'Overlay section follows Profile');
const b912=css.slice(css.lastIndexOf('/* B912 — Prototype-informed Profile hierarchy'),b961Start);
assert.ok(b912.startsWith('/* B912 — Prototype-informed Profile hierarchy'),'scoped visual pass is present');
assert.ok(b912.includes('@container pi-r77-profile')&&b912.includes('max-width:360px'),'narrow/large-text layout handled');
assert.ok(css.includes('@media (prefers-reduced-motion:reduce){#root button')&&b912.includes('forced-colors:active'),'shared reduced-motion and Profile forced-colors owners');
assert.doesNotMatch(b912,/@keyframes|animation:/,'no second animation system');
assert.ok(b912.includes('border-radius:var(--pi-shape-card)!important'),'Profile sections retain shared card geometry');

const shared=css.slice(css.indexOf('/* Cohesion 06 —'),css.indexOf('/* R61 — shared Home/Plan'));
assert.ok(shared.includes('[data-action="tonal"]')&&shared.includes('[aria-pressed="true"]'),'shared editor controls own tonal and selected paint');
for(const [name,token] of [['ProfileView','variant: "tonal"'],['ProfileCardCustomization','kind: "filter"'],['ProfileEditSheet','kind: "filter"']]){
 const start=app.indexOf('function '+name+'('),end=app.indexOf('\nfunction ',start+12);
 assert.ok(app.slice(start,end).includes(token),name+' uses the shared primitive: '+token);
}
console.log('PASS B907 Profile: identity card, one link to the strength record, shared controls, reduced-motion/high-contrast.');
