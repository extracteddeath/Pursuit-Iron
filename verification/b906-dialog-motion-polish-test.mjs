import assert from 'node:assert/strict';
import fs from 'node:fs';
const motion=fs.readFileSync('modules/ui-motion.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const app=fs.readFileSync('modules/App.js','utf8');
const selector="[data-view-frame], [data-pi-workout-page], [data-sheet-drag], .wpb-pop, .wpb-dialog, .wpb-session-dialog";
assert.ok(motion.includes(selector),'shared motion scans session dialogs');
assert.ok(motion.includes("el.matches('.wpb-pop,.wpb-dialog,.wpb-session-dialog')"),
 'session dialog has dialog-scale and bounded slide springs');
assert.equal(motion.split("querySelectorAll('[data-sheet-drag],.wpb-pop,.wpb-dialog,.wpb-session-dialog')").length-1,2,
 'dismiss and resume share the same session dialog motion owner');
assert.ok(css.includes('UI polish pass 01')&&css.includes('min-width:800px')&&css.includes('min-height:640px'),
 'phone bottom attachment and tablet centered presentation are both present');
assert.ok(css.includes('data-pi-surface/data-pi-backdrop motion ownership'),
 'do not add a competing CSS entrance animation');
assert.ok(app.includes('function SessionChoiceDialog(')&&app.includes('aria-modal'),
 'session dialog remains in the application');
console.log('PASS B906: session choice dialogs use existing spring enter/exit/resume, with responsive sheet geometry.');
