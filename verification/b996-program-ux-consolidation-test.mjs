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
assert.ok(exercise.includes('if (confirmRemove) setConfirmRemove(false); else close();'),
    'Escape backs out of destructive confirmation before dismissing the sheet');
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
    css.includes('.wpb-save-review-scroll{flex:1;min-height:0'),
    'responsive CSS belongs to the original components, not new override layers');
for (const surface of ['wpb-program-schedule-sheet','wpb-program-reference-panel','wpb-progression-choice-panel']) {
    assert.ok(css.includes(`.${surface}{`) && css.includes(`.${surface}{box-sizing:border-box`),
        'Program surface retains a component-owned stylesheet rule: '+surface);
    const start = css.indexOf(`.${surface}{`);
    assert.ok(css.slice(start, css.indexOf('}', start)).includes('max-height:min(86dvh,calc(var(--visual-h,'),
        'Program surface respects the visible keyboard viewport: '+surface);
}
assert.ok(css.length<=643592,'consolidated CSS does not exceed last published size ceiling');
assert.ok((css.match(/!important/g)||[]).length<=2060,'no additional priority overrides');
console.log('PASS B996: shared modal focus, destructive cancellation, resilient disclosure, accessible volume/progression and lower CSS debt.');
