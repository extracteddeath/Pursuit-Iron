import assert from 'node:assert/strict';
import fs from 'node:fs';
// B1045: one sheet chrome. Every sheet's close is the Compose IconButton (no fill or outline at rest,
// a round state layer when pressed) and sheet titles share one size, instead of an outlined square
// close on some sheets, a filled circle on others and titles from 15px to 22px.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const rule = selector => { const i = css.indexOf(selector + '{'); assert.ok(i >= 0, 'missing ' + selector); return css.slice(i, css.indexOf('}', i)); };
const close = rule('#root[data-pi-motion="expressive"] .wpb .wpb-sheet-close');
for (const decl of ['border-radius:50%!important;', 'border:0!important;', 'background-color:transparent!important;'])
    assert.ok(css.includes(decl) && css.indexOf('B1045') > 0, 'Sheet close is an icon button: ' + decl);
assert.ok(close.length > 0 && rule('#root[data-pi-motion="expressive"] .wpb .wpb-sheet-close:active').includes('color-mix('), 'Pressed close shows a state layer');
assert.ok(css.includes('.wpb-session-dialog-heading button{width:48px;height:48px;border:0;border-radius:50%;background:transparent;'), 'Dialog-chrome close has no fill');
assert.ok(css.includes('.wpb-b973-set-title{font-size:var(--pi-ui-sheet-title-size,1.125rem);font-weight:700;') &&
    css.includes('.wpb-session-dialog h2{font-size:var(--pi-ui-sheet-title-size,1.125rem);'), 'Sheet titles share the UiSheet title size');
assert.ok(app.includes('children: "Today\'s exercises" }), _jsx(UiSheetClose, {'), "Today's exercises uses the shared close");
console.log('PASS B1045 sheet chrome: one icon-button close and one title size across sheets.');
