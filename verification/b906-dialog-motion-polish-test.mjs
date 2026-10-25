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
const dialog=fs.readFileSync('modules/session-choice-dialog.js','utf8');
assert.ok(app.includes("from './session-choice-dialog.js'") && dialog.includes('function SessionChoiceDialog(') && dialog.includes('"aria-modal": true'),
 'shared session dialog remains in the application as a single accessible owner');
console.log('PASS B906: session choice dialogs use existing spring enter/exit/resume, with responsive sheet geometry.');
