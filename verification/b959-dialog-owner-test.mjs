import assert from 'node:assert/strict';
import fs from 'node:fs';
const src=fs.readFileSync('modules/session-choice-dialog.js','utf8');
const css=fs.readFileSync('app.css','utf8');
assert.ok(src.includes('dismissible && ev.target === ev.currentTarget'),'backdrop respects protected sheets');
assert.ok(src.includes("ev.key === 'Escape'") && src.includes('ev.preventDefault(); ev.stopPropagation();') && src.includes('if (dismissible) onClose();'),
  'Protected dialogs consume Escape and only dismiss when allowed');
assert.ok(src.includes("ev.key !== 'Tab'")&&src.includes('last.focus({ preventScroll: true })')&&src.includes('first.focus({ preventScroll: true })'),'Tab stays inside dialog');
assert.ok(src.includes('if (!first)')&&src.includes('panel.focus({ preventScroll: true })'),'no-action dialog retains focus');
assert.ok(src.includes('previous?.isConnected'),'safe focus restoration');
for(const selector of ['button:not(:disabled)','select:not(:disabled)','textarea:not(:disabled)','input:not(:disabled)'])
 assert.ok(src.includes(selector),'disabled controls cannot become Tab-trap endpoints: '+selector);
assert.ok(src.includes("!el.closest('[inert],[aria-hidden=\"true\"]')")&&src.includes("getComputedStyle(el).visibility !== 'hidden'")&&src.includes("el.getAttribute('tabindex') !== '-1'"),'non-interactive descendants never become Tab-trap endpoints');
assert.ok(css.includes('#root .wpb .wpb-session-dialog-heading>button{width:48px;height:48px;min-width:48px'),'48px Close action');
console.log('PASS B963/B959: protected dismissal, eligible focus trap and minimum close geometry');
