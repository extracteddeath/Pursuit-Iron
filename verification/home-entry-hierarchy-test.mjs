import assert from 'node:assert/strict';
import fs from 'node:fs';
const app = fs.readFileSync('modules/App.js', 'utf8');
const css = fs.readFileSync('app.css', 'utf8');
const start = app.indexOf('function Home(');
const end = app.indexOf('\nfunction ', start + 1);
const home = app.slice(start, end);
assert.ok(start > 0 && end > start, 'Home must have a single owner');
assert.ok(home.includes('className: "wpb-home-create"'), 'Home retains the one primary create route');
assert.ok(home.includes('!sessionActive && !upNext && (_jsxs("div", { className: "wpb-home-compose-bar"'),
    'Creation dock stays available on an empty Home, not over a running session');
assert.doesNotMatch(home, /Build my program|Get started|No programs yet|Build a program/,
    'Do not reintroduce extra first-run creation cards or empty-state duplicate buttons');
assert.ok(home.includes('!sessionActive && upNext && _jsx(SessionGamePlan'),
    'Game Plan stays below the session hero, never inside the session preview');
assert.ok(home.includes('onStartNext') && home.includes('onOpenActive') && home.includes('onOpenCycles'),
    'Home workout, preview and cycle routes stay available');
assert.ok(home.includes('className: "wpb-action-tile-icon"') &&
    home.includes('className: "wpb-action-tile-copy"') &&
    home.includes('className: "wpb-action-tile-label"') &&
    home.includes('className: "wpb-action-tile-subtitle"'),
    'Reusable discovery tile layout must be owned by CSS');
assert.ok(home.includes('}, a.id)))'), 'Action routes have stable keys, not filtered array indexes');
assert.ok(!home.includes('style: { textAlign: "left", padding: "13px 13px"'),
    'Discovery tiles may not restore duplicated static inline presentation');
assert.ok(css.includes('Cohesion 10 — Home discovery tiles share one presentation owner'));
assert.match(css, /#root \.wpb \.wpb-home-session-outline\{\s*margin:0;border:0;padding:0;/,
  'Session outline must not add a second large blank strip above its exercise list');
assert.ok(css.includes('#root .wpb .wpb-home-outline-list>li:first-child{padding-top:var(--pi-space-1)}'),
  'Expanded list starts close beneath its toggle');
const surfaces = fs.readFileSync('modules/plan-session-surfaces.js', 'utf8');
assert.match(app, /from ['"]\.\/plan-session-surfaces\.js['"]/, 'App composes the canonical Plan session surfaces');
assert.ok(!app.includes('function SessionGamePlan('), 'App must not retain the duplicate Game Plan implementation');
const gamePlan = surfaces.slice(surfaces.indexOf('function SessionGamePlan('), surfaces.indexOf('function PlanChangesDialog('));
assert.ok(gamePlan.includes('_jsxs("details"') && gamePlan.includes('_jsxs("summary"'),
  'Game Plan must expose complete coaching guidance via native keyboard-operable disclosures');
assert.ok(gamePlan.includes('children: preview') && gamePlan.includes('children: guidance') &&
    gamePlan.includes('const uniformTargets =') && !gamePlan.includes('style: {'),
  'Game Plan shows compact actual targets first, then an independently useful explanation');
assert.ok(css.includes('#root .wpb .wpb-game-plan-highlight-copy:not([open])>p{display:none}') &&
  css.includes('.wpb-game-plan-highlight-copy>summary:focus-visible'),
  'Game Plan guidance is hidden only while collapsed, with an observable keyboard focus target');

for(const selector of ['.wpb-action-tile-icon','.wpb-action-tile-copy','.wpb-action-tile-label',
    '.wpb-action-tile-subtitle'])assert.ok(css.includes(selector), selector + ' missing');
const week = home.indexOf('history.length > 0 && _jsx(WeeklyRecapCard');
const hero = home.indexOf('className: "wpb-hero wpb-home-hero"');
const coaching = home.indexOf('!sessionActive && upNext && _jsx(SessionGamePlan');
const programs = home.indexOf('className: "wpb-home-programs"');
assert.ok(hero > 0 && week > hero && coaching > week && programs > coaching,
  'Home hierarchy is session → This week → Game Plan → programs');
assert.equal((home.match(/_jsx\(WeeklyRecapCard/g) || []).length, 1,
  'There is one weekly progress card regardless of insight rank');
assert.ok(!home.includes('trainingPulse') && !home.includes('"Last 7 days"') && !home.includes('["Streak"'),
  'Remove standalone last-seven-days/streak duplication');
assert.ok(!app.slice(app.indexOf('function homeCardPlan('), app.indexOf('function DeloadAdviceCard(')).includes('["recap",'),
  'Weekly card does not consume a discretionary insight slot');
console.log('PASS Home ownership: one empty-state primary CTA, stable discovery route keys, shared tile CSS, Game Plan separate, runtime engine unchanged.');

assert.ok(home.includes('className: "wpb-primary-action", children:'), 'Home Start geometry is class-owned');
assert.ok(home.includes('className: "pressable wpb-home-switch-action"'), 'Program switching remains reachable');
assert.match(css, /#root \.wpb \.wpb-home-session-preview\{[^}]*margin:0;padding:0 var\(--pi-space-3\)/, 'Preview has one inset owner');
assert.match(css, /\.wpb-home-outline-list li\{[^}]*padding:var\(--pi-space-2\) 0;/, 'Workout rows use canonical spacing');
assert.match(css, /#root \.wpb \.wpb-plan-change-banner\{[^}]*min-height:56px;margin:0 0 var\(--pi-space-2\)/, 'Compact change history');
