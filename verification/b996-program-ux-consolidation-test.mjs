// B996: preserve behavior while retiring duplicate focus, scroll and presentation ownership.
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read = path => fs.readFileSync(path, 'utf8');
const review=read('modules/program-review-surfaces.js');
const history=read('modules/history-editor.js');
const exercise=read('modules/program-exercise-options.js');
const focus=read('modules/program-sheet-focus.js');
const disclosure=read('modules/ui-disclosure.js');
const volume=read('modules/program-volume-surfaces.js');
const progression=read('modules/program-reference-surfaces.js');
const css=read('app.css');

assert.ok(review.includes("useProgramSheetFocus(onClose)") &&
    review.includes('ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown'),
    'save review reuses the focus owner');
assert.ok(!review.includes('handleProgramSaveReviewKeys') &&
    !review.includes('autoFocus: true'), 'retired the duplicated and conflicting modal focus helper');
assert.ok(review.includes('event.target === event.currentTarget'),
    'dialog only dismisses on actual backdrop clicks');
assert.ok(history.includes('useProgramSheetFocus(onClose, sheetDragRef)') &&
    history.includes('ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown'),
    'history editor shares keyboard, ref and focus behavior without changing logging');
assert.ok(history.includes("showError && invalid ? 'history-editor-error'"),
    'history correction save describes the visible error');
assert.ok(exercise.includes('if (confirmRemove) setConfirmRemove(false); else close();') &&
    exercise.includes('event.target === event.currentTarget) dismiss();'),
    'Escape and backdrop both back out of a destructive confirmation before dismissing the sheet');
assert.ok(exercise.includes("panelRef.current?.querySelector('[data-exercise-action=\"remove\"]')") &&
    !exercise.includes("document.querySelector('.wpb-exercise-options-sheet"),
    'cancel returns focus to the correct dialog, not a global selector');
assert.ok(focus.includes("!el.closest('[inert],[hidden],[aria-hidden=\"true\"]')") &&
    focus.includes('onPanelKeyDown, panelRef'), 'shared focus trap filters hidden actions and exposes its own panel');
assert.ok(disclosure.includes("typeof ResizeObserver === 'function'") &&
    disclosure.includes("window.addEventListener('resize', resize)") &&
    disclosure.includes("window.removeEventListener('resize', resize)"),
    'disclosure retains responsive motion without crashing on older embedded webviews');
assert.equal((volume.match(/role: "meter"/g)||[]).length,2,
    'sub-region and major-region progress bars expose accessible numeric values');
assert.ok(volume.includes('volumeDetailsId = useId()') &&
    volume.includes('aria-controls') && volume.includes('role: "region"'),
    'contributors have scoped labels and stable focus/expanded ownership');
assert.ok(progression.includes("type: 'search'") &&
    progression.includes('normalizedQuery = q.trim().toLocaleLowerCase()') &&
    progression.includes("'aria-live': 'polite'") &&
    progression.includes('wpb-progression-result-count'),
    'progression filters expose forgiving search and live results');
assert.ok(css.includes('.wpb-progression-result-count{') &&
    css.includes('max-height:min(85dvh,calc(var(--visual-h,') &&
    css.includes('.wpb-save-review-scroll{flex:1}') &&
    css.includes('.wpb-ui-sheet-scroll{min-width:0;min-height:0;overflow-y:auto;overscroll-behavior:contain}') &&
    review.includes('wpb-save-review-scroll wpb-ui-sheet-scroll'),
    'responsive CSS retains Review flex sizing and the shared sheet scroll owner');
assert.ok(css.includes('.wpb-program-schedule-sheet{box-sizing:border-box;width:100%;max-height:min(86dvh,calc(var(--visual-h,'),
    'Weekly Schedule retains independent keyboard-safe viewport geometry');
assert.ok(css.includes('max-height:min(var(--pi-ui-sheet-max-vh,86dvh),calc(var(--visual-h,var(--app-h,100dvh)) - env(safe-area-inset-top) - var(--pi-ui-sheet-top-gap,24px)))'),'Reference and Progression panels retain keyboard-safe viewport bounds');
for (const surface of ['wpb-program-reference-panel','wpb-progression-choice-panel']) {
    assert.ok(css.includes(`.${surface}{`) && progression.includes('wpb-ui-sheet-panel '+surface),
        'Reference/Progression keep distinct paint and shared responsive geometry: '+surface);
}
assert.ok(css.length<=643592,'consolidated CSS does not exceed last published size ceiling');
assert.ok((css.match(/!important/g)||[]).length<=2060,'no additional priority overrides');
console.log('PASS B996: shared modal focus, destructive cancellation, resilient disclosure, accessible volume/progression and lower CSS debt.');
