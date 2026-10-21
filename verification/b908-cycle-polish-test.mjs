import assert from 'node:assert/strict';
import fs from 'node:fs';
const css=fs.readFileSync('app.css','utf8');
const app=fs.readFileSync('modules/App.js','utf8');
const i=css.indexOf('/* B908 / UI polish pass 03');
assert.ok(i>0,'isolated Cycle polish styles exist');
const end=css.indexOf('/* B909 — Prototype-aligned Home coaching',i);
const patch=css.slice(i,end>i?end:undefined);
assert.ok(patch.includes('.wpb-cycle-block-row[data-state="active"]'),'active block hierarchy');
assert.ok(patch.includes('box-shadow:inset 4px 0 var(--wpb-theme-accent-ink)'),'active rail is theme-bound');
assert.ok(patch.includes('opacity:1;'),'planned blocks remain legible');
assert.ok(patch.includes('@media(hover:hover) and (pointer:fine)'),'hover effects do not affect touch');
assert.ok(patch.includes('@media(prefers-reduced-motion:reduce)')&&patch.includes('transition:none;'),'motion respects reduced-motion');
assert.ok(patch.includes('@media(forced-colors:active)')&&patch.includes('Highlight'),'forced-colors remains visible');
assert.ok(!/(?:^|[;{])\s*(?:animation|transform)\s*:/.test(patch)&&!patch.includes('height 180ms'),'no page/row geometry animation');
assert.ok(app.includes('function CycleBlockRows(')&&app.includes('disabled: !p')&&app.includes('data-state'), 'Cycle state and all previews remain owned by existing component');
console.log('PASS B908 Cycle UI: hierarchy, semantic states, local paint-only motion and a11y.');

// Cohesion 14 removes the legacy Cycle cascade instead of adding a later patch.
assert.ok(!patch.includes('!important'),'Cycle owner needs no priority escalation');
assert.equal((patch.match(/^#root \.wpb-cycle-block-row\{/gm)||[]).length,1,'One base row owner; contextual overrides stay inside its family');
const rows=app.slice(app.indexOf('function CycleBlockRows('),app.indexOf('function CycleBlockNav('));
const preview=app.slice(app.indexOf('function CycleBlockPreview('),app.indexOf('function CycleDetail('));
assert.ok(!rows.includes('style: {')&&!preview.includes('style: {'),'Rows and read-only previews have no private presentation objects');
assert.ok(preview.includes('computeCell(program, day, id, slot, week)'),'Preview retains canonical prescriptions');
assert.ok(app.includes('cycles.length > 0 && _jsx(UiAction, { onClick: onNew'),'Empty cycle screen has only its main creation action');
assert.ok(!app.includes('.wpb-cycle-list-block{'),'No hidden legacy row stylesheet');
