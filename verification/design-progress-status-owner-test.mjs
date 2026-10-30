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
  const full = prefix + selector + '{' + declarations + '}';
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
const source = css.slice(css.indexOf('/* B891'), css.indexOf('/* B891') + 1);
assert.equal((css.match(/!important/g) || []).length, 2024, 'No new priority declarations');
assert.ok(css.length <= 643992, 'Source consolidation must reduce—not expand—CSS debt');
console.log('PASS design Progress status ownership: six common paint/number/disclosure roles; no priority growth.');
