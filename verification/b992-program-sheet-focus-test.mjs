import assert from 'node:assert/strict';
import fs from 'node:fs';
const focus=fs.readFileSync('modules/program-sheet-focus.js','utf8');
const settings=fs.readFileSync('modules/program-settings-sheet.js','utf8');
const share=fs.readFileSync('modules/program-share-sheet.js','utf8');
const css=fs.readFileSync('app.css','utf8');
for(const [name,source] of [['Settings',settings],['Share',share]]){
 assert.ok(source.includes("import { useProgramSheetFocus } from './program-sheet-focus.js';"),name+' uses shared focus hook');
 assert.ok(source.includes('useProgramSheetFocus(onClose, sheetDragRef)'),name+' preserves drag owner');
 assert.ok(source.includes('ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown'),name+' traps keyboard on focusable sheet');
 assert.ok(source.includes('event.target === event.currentTarget'),name+' ignores inside clicks on backdrop');
}
for(const term of ['useEffect','useCallback','getClientRects','details:not([open])','aria-disabled','previous?.isConnected','event.stopPropagation()', "event.key === 'Escape'"]){
 assert.ok(focus.includes(term),'Focus behavior missing: '+term);
}
assert.ok(css.includes(':is(.wpb-program-settings-backdrop,.wpb-program-share-backdrop)'), 'Program backdrop has one shared CSS geometry');
assert.ok(css.includes(':is(.wpb-program-settings-panel,.wpb-program-share-panel)'), 'Program panel has one shared CSS geometry');
assert.ok(css.includes('max-height:min(88dvh,calc(var(--visual-h'), 'Settings respects responsive viewport and safe area');
assert.ok(css.includes('max-height:min(92dvh,calc(var(--visual-h'), 'Share retains separate responsive height');
assert.ok(css.length<=628855,'CSS debt cannot exceed Build 985 baseline');
assert.ok((css.match(/!important/g)||[]).length<=2060,'No additional priority overrides');
console.log('PASS B992: Program Settings/Share share accessible keyboard and CSS shell ownership.');
