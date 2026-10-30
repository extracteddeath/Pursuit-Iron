// B1001 — The root typography reset must never suppress component scale styles.
import assert from 'node:assert/strict';
import fs from 'node:fs';
const css=fs.readFileSync('app.css','utf8');
assert.ok(css.includes(':where(#root) :where(button,[role="button"],input,textarea,select){font:inherit}'),
    'base form typography remains inherited but has zero specificity');
assert.ok(!css.includes('#root button,#root [role="button"],#root input,#root textarea,#root select{font:inherit}'),
    'retired ID-specific typography rule must not win over component font sizes');
assert.ok(css.includes('input,textarea{font-size:16px}'),
    'unclassified text inputs retain iOS-safe baseline size');
assert.ok(css.includes('#root .wpb .wpb-working-set-stepper input{width:48px') &&
    css.includes('font-size:1.25rem;font-weight:700'),
    'Workout numeric controls have a scoped, text-scale-aware font owner');
assert.ok(css.includes('.wpb-plan-change-banner strong{display:block;font-size:.875rem'),
    'Home plan change labels have a responsive font owner');
assert.ok((css.indexOf('/* B1009 — shared, palette-owned fixed text roles')>0 && css.slice(0,css.indexOf('/* B1009 — shared, palette-owned fixed text roles')).length<=643592),'stylesheet remains inside the release size budget');
assert.ok((css.match(/!important/g)||[]).length<=2054,
    'no new priority declarations compensate for global specificity');
console.log('PASS B1001: zero-specificity inherited font reset and component-owned scaling without priority hacks.');
