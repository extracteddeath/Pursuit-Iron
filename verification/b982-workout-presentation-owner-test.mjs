import assert from 'node:assert/strict';
import fs from 'node:fs';

// B982: static Workout typography/geometry is CSS-owned; no new priority overrides.
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const old=app.slice(app.indexOf('function WorkoutRestActions('),app.indexOf('function SetAwareDockPrototype('));
assert.match(old,/wpb-rest-action/);
assert.ok(!old.includes('style: {')&&!old.includes('tinyBtn()'),'Rest actions must not own inline geometry');
const zone=app.slice(app.indexOf('function WorkoutSession('),app.indexOf('function shareSession('));
for(const owner of ['wpb-load-reference','wpb-set-head','wpb-target-reps','wpb-set-tech-tag','wpb-rest-card','wpb-rest-ring']){
  const occurrence=zone.match(new RegExp('className: "'+owner+'"[^}\n]{0,240}', 'g'))||[];
  assert.ok(occurrence.length,'Missing JSX owner: '+owner);
  assert.ok(occurrence.every(s=>!s.includes('style:')),'Duplicate static style: '+owner);
  assert.ok(css.includes('.'+owner+'{'),'Missing CSS owner '+owner);
}
assert.match(zone, /"data-paused": paused \? "true" : "false"/);
assert.match(zone, /"data-ending": !paused && rest <= 10/);
const scope=css.slice(css.indexOf('/* B982: consolidate'));
assert.ok(scope.length>2000,'B982 CSS must be present');
assert.ok(!scope.includes('!important'),'No new CSS priority overrides');
assert.match(scope,/min-width:48px;min-height:48px/,'Rest buttons must be 48px targets');
assert.match(scope,/prefers-reduced-motion|forced-colors:active/,'Honor accessibility contrast and motion');
assert.ok(app.includes('onBump: bumpRest')&&app.includes('onPause: togglePause')&&app.includes('onSkip: skipRest'),
  'Logger continues to own one timer and callbacks');
assert.ok((app.match(/style: \{/g)||[]).length<=1262,'Seven static workout inline objects retired');
assert.ok((css.match(/!important/g)||[]).length<=2060,'Existing CSS override debt cannot increase');
console.log('PASS B982: workout presentation is CSS-owned without extra inline/priority styles.');
