import assert from 'node:assert/strict';
import fs from 'node:fs';
const src=fs.readFileSync('modules/session-choice-dialog.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const focus=fs.readFileSync('modules/program-sheet-focus.js','utf8');
assert.ok(src.includes('dismissible && ev.target === ev.currentTarget'),'backdrop respects protected sheets');
assert.ok(src.includes('useProgramSheetFocus(onClose, panelRef, { dismissible })'),
  'session and Program sheets share exactly one modal focus owner');
assert.ok(!src.includes('const focusable =') && !src.includes('useRef') && !src.includes('querySelectorAll'),
  'duplicate session-specific focus trap is removed');
assert.ok(focus.includes("event.key === 'Escape'") &&
    focus.includes('event.preventDefault();') && focus.includes('event.stopPropagation();') &&
    focus.includes('if (dismissible) onClose();'),
  'protected dialogs consume Escape and only dismiss when allowed');
assert.ok(focus.includes("event.key !== 'Tab'") &&
    focus.includes('last.focus({ preventScroll: true })') &&
    focus.includes('first.focus({ preventScroll: true })'),'Tab stays inside dialog');
assert.ok(focus.includes('if (!first)') && focus.includes('panel.focus({ preventScroll: true })'),
  'no-action dialog retains focus');
assert.ok(focus.includes('previous?.isConnected'),'safe focus restoration');
for(const selector of ['button:not(:disabled)','select:not(:disabled)','textarea:not(:disabled)','input:not(:disabled)'])
 assert.ok(focus.includes(selector),'disabled controls cannot become Tab-trap endpoints: '+selector);
assert.ok(focus.includes("!el.closest('[inert],[hidden],[aria-hidden=\"true\"]')") &&
    focus.includes("getComputedStyle(el).visibility !== 'hidden'") &&
    focus.includes("el.getAttribute('tabindex') !== '-1'"),
    'non-interactive descendants never become Tab-trap endpoints');
assert.ok(css.includes('#root .wpb .wpb-session-dialog-heading>button{width:48px;height:48px;min-width:48px'),'48px Close action');
console.log('PASS B963/B959: protected dismissal, eligible focus trap and minimum close geometry');
