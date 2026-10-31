import assert from 'node:assert/strict';
import fs from 'node:fs';

const css = fs.readFileSync('app.css', 'utf8');
const prefix = '#root .wpb .wpb-progress ';

// One canonical role per equivalent Progress state. Keep class/attribute
// specificity identical to the old single-target rules, without !important.
const owners = [
  [':is(.wpb-progress-body-change,.wpb-progress-trend-value)[data-direction="up"]', 'color:var(--wpb-theme-accent-ink)'],
  [':is(.wpb-progress-body-change,.wpb-progress-trend-value)[data-direction="down"]', 'color:var(--wpb-theme-warning)'],
  [':is(.wpb-progress-volume-change-value,.wpb-progress-lift-change)[data-direction="up"]', 'color:var(--wpb-theme-accent);background:var(--wpb-theme-accent-dim)'],
  [':is(.wpb-progress-record-level[data-high="true"],.wpb-progress-volume-change-value[data-direction="down"],.wpb-progress-lift-change[data-direction="down"])', 'color:var(--wpb-theme-warning);background:var(--wpb-theme-warning-dim)'],
  [':is(.wpb-progress-measure-toggle[aria-expanded="true"] .wpb-progress-measure-chevron,.wpb-history-session-toggle[aria-expanded="true"] .wpb-history-session-chevron)', 'transform:rotate(180deg)'],
  [':is(.wpb-progress-body-change,.wpb-progress-trend-value,.wpb-progress-volume-change-value,.wpb-progress-lift-change)', 'font-variant-numeric:tabular-nums']
];
for (const [selector, declarations] of owners) {
  const full = prefix + selector + '{' + declarations; // terminal semicolon is optional CSS syntax
  assert.equal(css.split(full).length - 1, 1, 'Exactly one semantic owner: ' + selector);
}
for (const role of ['body-change','trend-value','volume-change-value','lift-change']) {
  const old = new RegExp('\\.wpb-progress-' + role + '\\[data-direction="(?:up|down)"\\]\\{');
  assert.ok(!old.test(css), 'Do not fork directional paint: ' + role);
}
assert.ok(!css.includes(prefix + '.wpb-progress-record-level[data-high="true"]{color:var(--wpb-theme-warning);background:var(--wpb-theme-warning-dim)'),
  'Record warning reuses the down-state semantic owner');
assert.ok(!css.includes(prefix + '.wpb-history-session-toggle[aria-expanded="true"] .wpb-history-session-chevron{transform:rotate(180deg)'),
  'Expanded disclosures share one state presentation owner');
// Profile reuses the same section rhythm and caption color without duplicate owners.
assert.ok(css.includes('#root .wpb .wpb-profile .wpb-profile-achievement-board,#root .wpb .wpb-profile-score-trend{margin-top:12px;padding-top:12px;border-top:1px solid var(--wpb-theme-border-soft)}'));
assert.ok(css.includes('#root .wpb :is(.wpb-profile-trend-delta,.wpb-profile-pattern-level){font-size:var(--pi-type-caption);font-weight:700;color:var(--pi-profile-trend-ink)}'));
assert.ok(css.includes('#root .wpb .wpb-profile-pattern-delta{font-size:var(--pi-type-caption);font-weight:600;color:var(--pi-profile-trend-ink)}'));
assert.ok(!css.split('\n').some(line =>
  line.startsWith('#root .wpb .wpb-profile-score-trend{') ||
  line.startsWith('#root .wpb .wpb-profile-pattern-level{font-size:11px')),
  'Profile rhythm and metrics cannot regain duplicate standalone owners');
assert.equal((css.match(/!important/g) || []).length, 2024, 'No new priority declarations');
assert.ok(css.length <= 644408, 'Presentation CSS must remain within its measured post-foundation budget');
console.log('PASS design Progress/Profile: shared state, metric and section owners; no priority growth.');
