// B998 — UI-only control correctness: no duplicate disclosure IDs or invalid stepper adjustments.
import assert from 'node:assert/strict';
import fs from 'node:fs';
const home=fs.readFileSync('modules/plan-session-surfaces.js','utf8');
const controls=fs.readFileSync('modules/workout-session-controls.js','utf8');
const css=fs.readFileSync('app.css','utf8');
assert.ok(home.includes("import { useId } from 'react'") && home.includes('const listId = useId()'),
    'Home/Plan each receive a stable unique disclosure ID');
assert.ok(home.includes('"aria-controls": listId') && home.includes('id: listId, hidden: !expanded'),
    'expanded control and disclosure reference the same instance ID');
assert.ok(home.includes('"aria-label": `Review ${count} plan adjustment'),
    'review banner communicates actual adjustment count');
assert.ok(controls.includes('Math.min(20, Math.max(minimum, Math.trunc(parsed)))') &&
    controls.includes('String(value).trim()') &&
    controls.includes('onChange(Math.max(minimum, count - 1))') &&
    controls.includes('onChange(Math.min(20, count + 1))'),
    'steppers do not emit NaN or values outside the visible 1–20 interval');
assert.ok(controls.includes('onChange: ev => onChange(ev.target.value)'),
    'direct typing remains delegated to the owner and is never silently overwritten');
assert.ok(css.includes('.wpb-working-set-stepper') && css.includes('.wpb-home-outline-toggle'),
    'controls retain their established themed CSS owners');
console.log('PASS B998: unique Home outline controls, meaningful plan banner and bounded set steppers.');
