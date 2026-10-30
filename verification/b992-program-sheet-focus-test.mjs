import assert from 'node:assert/strict';
import fs from 'node:fs';
const focus=fs.readFileSync('modules/program-sheet-focus.js','utf8');
const settings=fs.readFileSync('modules/program-settings-sheet.js','utf8');
const share=fs.readFileSync('modules/program-share-sheet.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const schedule=fs.readFileSync('modules/program-schedule-sheet.js','utf8');
const exercise=fs.readFileSync('modules/program-exercise-options.js','utf8');
const reference=fs.readFileSync('modules/program-reference-surfaces.js','utf8');
const maxes=fs.readFileSync('modules/program-training-maxes.js','utf8');
for(const [name,source] of [['Settings',settings],['Share',share],['Schedule',schedule],['Exercise options',exercise],['Training maxes',maxes],['Progression and reference',reference]]){
 assert.ok(source.includes("import { useProgramSheetFocus } from './program-sheet-focus.js';"),name+' uses shared focus hook');
 assert.ok(source.includes('useProgramSheetFocus(onClose, sheetDragRef)') || source.includes('useProgramSheetFocus(close, sheetDragRef)') || source.includes('useProgramSheetFocus(dismiss, sheetDragRef)') || source.includes('useProgramSheetFocus(conversion ? closeConversion : onClose, sheetDragRef)'),name+' preserves drag owner');
 assert.ok(source.includes('ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown') || source.includes('tabIndex: -1, ref: attachPanel, onKeyDown: onPanelKeyDown'),name+' traps keyboard on focusable sheet');
 assert.ok(source.includes('event.target === event.currentTarget'),name+' ignores inside clicks on backdrop');
}
for(const term of ['useEffect','useCallback','getClientRects','details:not([open])','aria-disabled','previous?.isConnected','event.stopPropagation()', "event.key === 'Escape'"]){
 assert.ok(focus.includes(term),'Focus behavior missing: '+term);
}
assert.ok(css.includes('.wpb-ui-sheet-backdrop{position:fixed;inset:0;display:flex;') &&
    settings.includes('wpb-ui-sheet-backdrop wpb-ui-sheet-focus wpb-program-settings-backdrop') &&
    share.includes('wpb-ui-sheet-backdrop wpb-ui-sheet-focus wpb-program-share-backdrop'),
    'Settings and Share reuse one overlay/focus owner');
assert.ok(css.includes('.wpb-ui-sheet-panel{box-sizing:border-box;display:flex;flex-direction:column;') &&
    settings.includes('wpb-ui-sheet-panel wpb-program-settings-panel') &&
    share.includes('wpb-ui-sheet-panel wpb-program-share-panel'),
    'Settings and Share reuse shared panel sizing');
assert.ok(css.includes('max-height:min(var(--pi-ui-sheet-max-vh,86dvh),calc(var(--visual-h,var(--app-h,100dvh)) - env(safe-area-inset-top) - var(--pi-ui-sheet-top-gap,24px)))') &&
    css.includes('.wpb-program-settings-panel{--pi-ui-sheet-max-vh:88dvh;--pi-ui-sheet-top-gap:32px}') &&
    css.includes('.wpb-program-share-panel{--pi-ui-sheet-max-vh:92dvh;--pi-ui-sheet-top-gap:32px}'),
    'Settings and Share retain their distinct keyboard-safe height bounds');
// B993 adds one themed Workout Info owner and permanently retires inline styling.
assert.ok(css.length<=643592,'CSS presentation budget includes the reviewed B994 finish migration');
assert.ok((css.match(/!important/g)||[]).length<=2060,'No additional priority overrides');
console.log('PASS B992: Program Settings/Share share accessible keyboard and CSS shell ownership.');
