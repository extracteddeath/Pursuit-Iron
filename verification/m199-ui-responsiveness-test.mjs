import fs from 'node:fs';
import assert from 'node:assert/strict';

const app = fs.readFileSync('modules/App.js', 'utf8');
const html = fs.readFileSync('index.html', 'utf8');

function section(start, end) {
  const a = app.indexOf(start);
  assert.ok(a >= 0, `Missing start marker: ${start}`);
  const b = app.indexOf(end, a + start.length);
  assert.ok(b > a, `Missing end marker: ${end}`);
  return app.slice(a, b);
}

const stepBody = section('function StepBody(', 'function Wizard(');
const wizard = section('function Wizard(', 'function Heading(');

assert.match(stepBody, /const buildKey = key === "session" \? "session\|" \+ JSON\.stringify\(fpBase\) : "";/, 'Only the selected session should trigger full feasibility generation.');
assert.doesNotMatch(stepBody, /compatibleSplits\.map\(\(\[k\]\) => \["split", k\]\)/, 'Split screen must not eagerly generate every split.');
assert.doesNotMatch(stepBody, /sessionChoicesFor\(config\.split\)\.map\(x => \["session", x\.id\]\)/, 'Session screen must not eagerly generate every session length.');
assert.match(stepBody, /timer = setTimeout\(run, 90\)/, 'Selected-session feasibility should be debounced.');
assert.match(stepBody, /await new Promise\(resolve => setTimeout\(resolve, 0\)\)/, 'Selected-session loading state should receive a paint opportunity.');
assert.match(stepBody, /Checking this session length…/, 'Selected-session validation must expose a loading message.');
assert.match(stepBody, /Fits your setup/, 'Successful selected-session validation must expose completion feedback.');
assert.match(stepBody, /Equipment requirements are checked here\. Session-time fit is confirmed on the next step\./, 'Split step must explain when the slower validation happens.');
assert.match(stepBody, /splitContractGaps\(\{ \.\.\.config, goal: effectiveGoal, split: config\.split \}, EXERCISES\)/, 'Split Next gating should retain cheap hard-contract validation.');
assert.match(stepBody, /buildable\.__key === buildKey && !!buildable\[config\.session\] && !blockedHere\(config\.session\)/, 'Session Next must still fail closed until selected feasibility is known.');

assert.match(wizard, /setBuilding\(true\)/, 'Final build must enter a visible building state.');
assert.match(wizard, /await afterNextPaint\(\)/, 'Final build must paint before heavy synchronous generation.');
assert.match(wizard, /Building your \$\{mode === "cycle" \? "cycle" : "program"\}…/, 'Final build must describe the active work.');
assert.match(wizard, /Balancing exercises, volume, recovery, and session time\./, 'Final build must explain what is being processed.');

assert.match(html, /"\.\/modules\/next-engine\/app-shell-adapter\.js":"\.\/modules\/next-engine\/app-shell-adapter-capacity\.js"/, 'Browser import map must keep the capacity-aware adapter authoritative.');

console.log('M199 UI responsiveness/loading contract: pass');
