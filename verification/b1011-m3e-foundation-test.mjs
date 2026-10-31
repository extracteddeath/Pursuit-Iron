import assert from 'node:assert/strict';
import fs from 'node:fs';

const css = fs.readFileSync('app.css','utf8');
const primitives = fs.readFileSync('modules/ui-primitives.js','utf8');
const report = fs.readFileSync('docs/design/M3_EXPRESSIVE_FOUNDATION_B1011.md','utf8');

for(const token of ['--pi-shape-feature:30px','--pi-shape-card:24px','--pi-shape-action:18px','--pi-shape-pill:999px','--pi-shape-identity:32px 16px 32px 32px','--pi-type-screen-title:clamp('])
  assert.ok(css.includes(token),'Missing shared M3E token: '+token);
for(const owner of ['.wpb .wpb-ui-card{','.wpb .wpb-settings-card.wpb-ui-card{','.wpb .wpb-settings .wpb-segmented{','.wpb-profile-card-metrics>div:nth-child(-n+2){','#root .wpb .wpb-home-session-preview{','#root .wpb .wpb-plan-change-banner{'])
  assert.ok(css.includes(owner),'Missing shared screen role: '+owner);
assert.equal((css.match(/\.wpb-profile-card-metrics\{display:grid/g)||[]).length,1,'Profile metric layout has more than one owner');
assert.ok(css.includes('@container pi-r77-profile (max-width:22em)'),'Profile must reflow at large text');
assert.ok(css.includes('@media(forced-colors:active)'),'High contrast must remain supported');
assert.ok(css.includes('prefers-reduced-motion:reduce'),'Reduced motion support lost');
for(const name of ['UiRootHeader','UiAction','UiChoice'])
  assert.ok(primitives.includes('export function '+name+'('),'Shared interaction owner missing: '+name);
assert.ok(report.includes('not a claim that all remaining pages'),'Foundation cannot be described as fully certified');
console.log('B1011 shared expressive source contract passed');
