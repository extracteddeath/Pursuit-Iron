import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
const begin=app.indexOf('function WorkoutSession('),end=app.indexOf('function shareSession(',begin);
const w=app.slice(begin,end),a=w.indexOf('return (_jsx("div", { onClick: () => { if (finishingRef.current)'),b=w.indexOf('})() }), setDockPreview',a);
assert.ok(a>=0&&b>a,'Workout finish owner present');
const sheet=w.slice(a,b);
for(const role of ["wpb-finish-sheet","wpb-finish-trophy","wpb-finish-subtitle","wpb-finish-block-label","wpb-finish-metric","wpb-finish-metric-value","wpb-finish-metric-label","wpb-finish-highlight","wpb-finish-award-heading","wpb-finish-highlight-title","wpb-finish-entry-name","wpb-finish-entry-result","wpb-finish-estimate","wpb-finish-eyebrow","wpb-finish-estimate-gain","wpb-finish-estimate-value","wpb-finish-estimate-unit","wpb-finish-estimate-context","wpb-finish-next-card","wpb-finish-next-direction","wpb-finish-next-caption","wpb-finish-feedback-section","wpb-finish-feedback-toggle","wpb-finish-feedback-optional","wpb-finish-feedback-detail","wpb-finish-feedback-chevron","wpb-finish-feedback-help","wpb-finish-rating-choice","wpb-finish-feedback-card","wpb-finish-feedback-explanation","wpb-finish-feedback-axis","wpb-finish-save","wpb-finish-share-action","wpb-finish-routine-trigger","wpb-finish-routine-editor","wpb-finish-routine-label","wpb-finish-routine-input","wpb-finish-routine-save","wpb-finish-routine-cancel","wpb-finish-keep-training"]){
 assert.ok(sheet.includes(role),'Missing finished-workout JSX role '+role);
 assert.ok(css.includes('.'+role+'{'),'Missing finished-workout CSS owner '+role);
}
assert.ok(sheet.includes('"aria-pressed": on'),'Pump/recovery input is accessible');
assert.ok(sheet.includes('style: { color: col }'),'Next-session status preserves dynamic direction color');
assert.ok((sheet.match(/style: \\{/g)||[]).length<=2,'Workout completion must not regress to inline styling');
assert.ok((app.match(/style: \\{/g)||[]).length<=1101,'App inline debt budget');
assert.ok((css.match(/!important/g)||[]).length<=2060,'No new important overrides');
assert.ok(css.length<=643592,'CSS size ceiling tracks the migration');
for(const token of ['.wpb-finish-rating-choice[aria-pressed="true"]','.wpb-finish-feedback-toggle[aria-expanded="true"]','.wpb-finish-routine-save:disabled','@media(prefers-reduced-motion:reduce)'])
 assert.ok(css.includes(token),'Missing state/accessibility owner '+token);
console.log('PASS B994: 46 Workout finish inline presentation objects retired; controls and geometry CSS-owned.');
