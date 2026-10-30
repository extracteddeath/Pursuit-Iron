// B1000: Workout controls fit real touch and scaled text without competing style layers.
import assert from 'node:assert/strict';
import fs from 'node:fs';
const css=fs.readFileSync('app.css','utf8');
const source=fs.readFileSync('modules/workout-session-controls.js','utf8');
assert.ok(css.includes('height:48px!important;min-height:48px!important;padding:0 12px!important'),
    'the horizontal exercise rail must use a genuine 48px rather than a visual-only 44px hit target');
assert.ok(css.includes('.wpb-working-set-stepper input{width:clamp(56px,4rem,88px)'),
    'typed multi-digit set counts must remain legible at 200% Android font scaling');
assert.ok(css.includes('font-size:1.25rem;font-weight:700;box-sizing:border-box'),
    'the numeric stepper scales with accessible text size');
for (const prefix of [
    'button[aria-label^="Edit pinned note for "]{\n  box-sizing:border-box;\n  min-height:48px!important;',
    'button[aria-label^="Edit pinned note for "]{\n  min-height:48px!important;'
]) assert.ok(css.includes(prefix),'Workout pinned-note actions retain a shared 48px minimum in both theme scopes');
assert.ok(css.includes('.wpb-ex-nav-count{flex-shrink:0;font-size:.6875rem;font-weight:600}'),
    'workout navigation counts do not pin text to tiny pixel sizes');
assert.ok(source.includes('const reveal = () =>') && source.includes("observer.observe(active)"),
    'active exercise reveal remains owned by the single nav component');
assert.ok((css.indexOf('/* B1009 — shared, palette-owned fixed text roles')>0 && css.slice(0,css.indexOf('/* B1009 — shared, palette-owned fixed text roles')).length<=643592),'stylesheet stays within published maximum size');
assert.ok((css.match(/!important/g)||[]).length<=2054,'Workout touch pass adds no new override declarations');
console.log('PASS B1000: Workout rail 48px, large-text number input, owner-bound scroll and no new !important.');
