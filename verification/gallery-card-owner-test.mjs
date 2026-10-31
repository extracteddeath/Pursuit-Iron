import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
const start=app.indexOf('function GalleryView('),end=app.indexOf('/* ⚠ ONE OWNER',start);
const gallery=app.slice(start,end);
assert.ok(start>0&&end>start,'Gallery source is present');
assert.equal(gallery.split('_jsxs(UiCard, { className: "wpb-gallery-program"').length-1,1,
 'Gallery program cards use the same standard card role as Settings rather than duplicate visual paint');
const rule='#root .wpb .wpb-gallery-program{';
assert.equal(css.split(rule).length-1,1,'Only one Gallery card layout owner');
const body=css.split(rule)[1]?.split('}')[0]||'';
assert.equal(body,'padding-block:var(--pi-space-4) var(--pi-space-3)',
 'Feature CSS may own only vertical rhythm; shared card owns fill, corners and border');
assert.ok(css.includes('.wpb .wpb-ui-card{'),'Shared card surface must exist');
assert.ok(gallery.includes('onClick: () => { const r = onImport(e.code);'),'Import action remains separate from read-only card');
assert.ok(gallery.includes('disabled: res && res.ok'),'Do not allow import twice');
assert.ok(css.length<=647477,'Consolidated card must remain within the current measured shared-foundation CSS budget');
assert.equal((css.match(/!important/g)||[]).length,2024,'No new priority overrides');
console.log('PASS Gallery card surface: one shared role and content-only feature spacing.');
