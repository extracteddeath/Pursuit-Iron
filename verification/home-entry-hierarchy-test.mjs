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
assert.ok(css.includes('#root .wpb .wpb-home-outline-list>li:first-child{padding-top:3px}'),
  'Expanded list starts close beneath its toggle');

for(const selector of ['.wpb-action-tile-icon','.wpb-action-tile-copy','.wpb-action-tile-label',
    '.wpb-action-tile-subtitle'])assert.ok(css.includes(selector), selector + ' missing');
assert.ok(home.includes('className: "wpb-primary-action", children:'),
    'Home Start uses CSS-owned geometry rather than a static inline style');
assert.ok(home.includes('className: "pressable wpb-home-switch-action"'),
    'Program switch remains reachable through a token-owned action');
assert.ok(!home.includes('className: "pressable wpb-home-switch-action", "aria-label": "Switch program", style:'),
    'Program switch must not return to inline static presentation');
assert.ok(css.includes('#root .wpb .wpb-home-hero>.wpb-home-switch-action{'),
    'Home switch geometry is class-owned');
assert.ok(css.includes('padding-top:var(--pi-space-3)!important;'),
    'Home cards retain their inset below the header');
assert.ok(css.includes('padding:6px 0;color:var(--wpb-theme-text);font-size:var(--pi-type-support);'),
    'Expanded passive workout rows preserve the denser token-owned rhythm');
console.log('PASS Home ownership: one task CTA, token-owned rhythm, full outline, independent Game Plan.');
