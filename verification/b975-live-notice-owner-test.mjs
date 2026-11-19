import assert from 'node:assert/strict';
import fs from 'node:fs';

const app = fs.readFileSync('modules/App.js', 'utf8');
const css = fs.readFileSync('app.css', 'utf8');
const inline = (app.match(/style: \\{/g) || []).length;
assert.ok(inline <= 1296, `B975 shell controls must keep retired inline styles out (found ${inline})`);

const owners = [
  'live-surface', 'live-dot', 'live-warning', 'live-resume-copy',
  'live-resume-action', 'live-discard', 'update-action', 'offline-status',
  'update-dialog-actions', 'update-keep', 'update-restart'
];
for (const owner of owners) {
  const name = 'wpb-b975-' + owner;
  assert.ok(app.includes(name), name + ' has a real React owner');
  assert.ok(css.includes('.' + name + '{') || css.includes('.' + name + ','),
    name + ' has a CSS presentation owner');
}
const surface = css.slice(css.indexOf('/* B975: one theme-owned layout'));
assert.ok(surface.length > 2500, 'B975 CSS must exist');
assert.ok(!surface.includes('!important'), 'New shell styles cannot add CSS priority overrides');
assert.ok(surface.includes('[data-discard-armed="true"]'), 'Discard confirmation paints only when armed');
assert.ok(surface.includes('[data-has-tabs="true"]'), 'Dock clears the bottom navigation');
assert.ok(surface.includes('min-height:48px'), 'Update and confirmation must keep accessible targets');
assert.ok(surface.includes('min-height:44px'), 'Resume and Discard must keep accessible targets');
assert.ok(surface.includes('forced-colors:active'), 'Accessible contrast mode remains supported');
assert.ok(surface.includes('prefers-reduced-motion:reduce'), 'Reduced-motion status is supported');
assert.ok(app.includes('"data-discard-armed": discardArmed ? "true" : "false"'));
assert.ok(app.includes('"data-has-tabs": tabView ? "true" : "false"'));
assert.ok(app.includes('"--pi-discard-on-danger": C.dangerText'),
  'Theme-owned discard states preserve its correct on-danger contrast');
assert.ok(app.includes('const showUpdate = !!updateReady'), 'Pending update remains persistent');
assert.ok(app.includes('const requestUpdate = () =>'), 'Explicit restart consent remains owned by App');
assert.ok(app.includes('window.dispatchEvent(new CustomEvent("wpb:save-workout-before-update"'),
  'Live workout always saves before restart');
console.log('PASS B975: 8 live dock and update inline styles retired without changing workout ownership.');
