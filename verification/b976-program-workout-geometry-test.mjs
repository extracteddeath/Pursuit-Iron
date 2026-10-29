import assert from 'node:assert/strict';
import fs from 'node:fs';

const app = fs.readFileSync('modules/App.js','utf8');
const css = fs.readFileSync('app.css','utf8');
const names = [
  'schedule-trigger','schedule-day','reorder-backdrop','swap-search-icon',
  'swap-results','swap-chevron','restore-backdrop','trace-trigger',
  'previous-set-action','coach-chevron','injury-backdrop','pr-value',
  'session-exit','milestone-item','changelog-backdrop','template-scroller'
];
const scope = css.slice(css.indexOf('/* B976 —'));
assert.ok(scope.length > 2500,'B976 CSS ownership block is required');
for(const name of names) {
  const cls='wpb-b976-'+name;
  assert.ok(app.includes(cls),'Missing React class: '+cls);
  assert.ok(scope.includes('.'+cls+'{'),'Missing CSS owner: '+cls);
  assert.equal(scope.split('.'+cls+'{').length-1,1,'Duplicate CSS owner: '+cls);
}
assert.ok((app.match(/style: \\{/g)||[]).length <= 1280,
  'B976 must permanently retire 16 static inline declarations');
assert.equal((css.match(/!important/g)||[]).length,2061,
  'New CSS must not increase global priority override debt');
assert.ok(!scope.includes('!important'),'B976 must not introduce priority overrides');
assert.ok(app.includes('setSchedSheet(true)'),'Program schedule still opens its editor');
assert.ok(app.includes('setTraceOpen(open ? null : ei)'),'Workout trace still expands on demand');
assert.ok(app.includes('setConfirmExit(true)'),'Workout exit still requests confirmation');
assert.ok(app.includes('setDetail(m)'),'Progress milestones still open details');
assert.ok(app.includes('onClick: onClose'),'Backdrops keep their native dismissal owner');
assert.match(scope,/wpb-b976-session-exit\\{[^}]*min-height:48px/s,
  'Session exit must have a real 48px touch area');
assert.match(scope,/wpb-b976-schedule-trigger\\{[^}]*min-height:44px/s,
  'Schedule editor must have a usable action area');
assert.match(scope,/wpb-b976-swap-results\\{[^}]*min-height:0;overflow-y:auto/s,
  'Exercise search scroll must not be trapped beneath the keyboard');
assert.ok(scope.includes('@media(prefers-reduced-motion:reduce)'));
assert.ok(scope.includes('@media(forced-colors:active)'));
console.log('PASS B976: 16 program/workout CSS geometry owners preserve actions, scrolling and responsive targets.');
