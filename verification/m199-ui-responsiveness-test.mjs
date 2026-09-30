import fs from 'node:fs';
import assert from 'node:assert/strict';

const app = fs.readFileSync('modules/App.js', 'utf8');
const html = fs.readFileSync('index.html', 'utf8');

// These strings are intentionally specific to the wizard scheduler/UX. App.js is generated, so function order is not stable.
assert.match(app, /const buildKey = key === "session" \? "session\|" \+ JSON\.stringify\(fpBase\) : "";/, 'Only the selected session should trigger full feasibility generation.');
assert.doesNotMatch(app, /compatibleSplits\.map\(\(\[k\]\) => \["split", k\]\)/, 'Split screen must not eagerly generate every split.');
assert.doesNotMatch(app, /sessionChoicesFor\(config\.split\)\.map\(x => \["session", x\.id\]\)/, 'Session screen must not eagerly generate every session length.');
assert.match(app, /timer = setTimeout\(run, 90\)/, 'Selected-session feasibility should be debounced.');
assert.match(app, /Paint the selected card's loading state before the synchronous engine validation begins\./, 'Selected-session validation should explicitly yield for paint.');
assert.match(app, /Checking this session length…/, 'Selected-session validation must expose a loading message.');
assert.match(app, /Fits your setup/, 'Successful selected-session validation must expose completion feedback.');
assert.match(app, /Equipment requirements are checked here\. Session-time fit is confirmed on the next step\./, 'Split step must explain when the slower validation happens.');
assert.match(app, /splitContractGaps\(\{ \.\.\.config, goal: effectiveGoal, split: config\.split \}, EXERCISES\)/, 'Split Next gating should retain cheap hard-contract validation.');
assert.match(app, /buildable\.__key === buildKey && !!buildable\[config\.session\] && !blockedHere\(config\.session\)/, 'Session Next must still fail closed until selected feasibility is known.');

assert.match(app, /setBuilding\(true\)/, 'Final build must enter a visible building state.');
assert.match(app, /await afterNextPaint\(\)/, 'Final build must paint before heavy synchronous generation.');
assert.match(app, /Building your /, 'Final build must describe the active work.');
assert.match(app, /Balancing exercises, volume, recovery, and session time\./, 'Final build must explain what is being processed.');

// Observational research must not add another generation pass to ordinary user-facing builds.
assert.match(app, /const promotionActive = Array\.isArray\(M76_SELECTIVE_PROMOTION_MANIFEST\?\.entries\)/, 'Shadow work should be gated by an active reviewed rollout or explicit research mode.');
assert.match(app, /options\.canaryResearch\?\.enabled \|\| promotionActive/, 'Standalone shadow evaluation should stay off in ordinary production creation.');
assert.match(app, /function attachShadowToCycleBuild\(built, banned = \[\], researchEnabled = false\)/, 'Cycle shadow evaluation should default off.');
assert.match(app, /if \(!built\?\.blocks \|\| !researchEnabled\)/, 'Cycle shadow evaluation should return before per-block work when research is off.');

// The workout clock must not force the entire Session tree to re-render once per second.
assert.doesNotMatch(app, /setInterval\(\(\) => forceTick\(n => n \+ 1\), 1000\)/, 'Whole-session one-second force-render loop must stay removed.');
assert.match(app, /const timerTextRef = useRef\(null\)/, 'Elapsed timer should have an isolated text ref.');
assert.match(app, /const syncElapsedLabel = \(\) =>/, 'Elapsed timer should update only its own text node.');
assert.match(app, /ref: timerTextRef, className: "mono"/, 'Visible workout clock should use the isolated timer ref.');

assert.match(html, /"\.\/modules\/next-engine\/app-shell-adapter\.js":"\.\/modules\/next-engine\/app-shell-adapter-capacity\.js"/, 'Browser import map must keep the capacity-aware adapter authoritative.');

console.log('M199 UI responsiveness/loading contract: pass');
