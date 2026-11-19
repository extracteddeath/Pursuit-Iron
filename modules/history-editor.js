// History correction presentation; the canonical records owner normalizes all saved evidence.
import { createElement as h, useState, useEffect } from 'react';
import { Pencil, X } from 'lucide-react';
import { EX_BY_ID, normalizeEditedHistoryEntry } from './engine-api.js';
import { UiAction } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';

function localHistoryDateValue(ms) {
    const d = new Date(Number(ms) || Date.now());
    const shifted = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
    return shifted.toISOString().slice(0, 16);
}

export function HistoryEditSheet({ entry, unit, onClose, onSave, sheetDragRef, selectNumericInput }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose, sheetDragRef);
    const makeDraft = (src) => ({ ...src, perf: Object.fromEntries(Object.entries(src?.perf || {}).map(([id, p]) => [id, {
        ...p,
        sets: (Array.isArray(p?.sets) && p.sets.length ? p.sets : (p?.reps != null ? [{ w: p.weight ?? 0, r: p.reps }] : [])).map(st => ({ ...st }))
    }])) });
    const [draft, setDraft] = useState(() => makeDraft(entry));
    useEffect(() => setDraft(makeDraft(entry)), [entry?.id]);
    const setSet = (exId, index, key, value) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) };
        const sets = (p.sets || []).map((st, i) => i === index ? { ...st, [key]: value } : st);
        perf[exId] = { ...p, sets };
        return { ...prev, perf };
    });
    const removeSet = (exId, index) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) };
        const sets = (p.sets || []).filter((_, i) => i !== index);
        if (sets.length)
            perf[exId] = { ...p, sets };
        else
            delete perf[exId];
        return { ...prev, perf };
    });
    const addSet = (exId) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) }, sets = [...(p.sets || [])];
        const last = sets[sets.length - 1];
        sets.push({ w: last?.w ?? 0, r: last?.r ?? "", rir: "" });
        perf[exId] = { ...p, sets };
        return { ...prev, perf };
    });
    const removeExercise = (exId) => setDraft(prev => { const perf = { ...(prev.perf || {}) }; delete perf[exId]; return { ...prev, perf }; });
    const exEntries = Object.entries(draft.perf || {});
    const [showError, setShowError] = useState(false);
    const corrected = normalizeEditedHistoryEntry(entry, draft);
    // A correction must retain every staged set and numeric value. Canonical
    // normalization owns validity, rounding and effort bounds; never silently
    // commit its filtered/clamped result while the user is still typing.
    const invalid = !Number.isFinite(corrected.volume)
        || Object.values(corrected.perf).some(performance => !Number.isFinite(performance.weight) || !Number.isFinite(performance.reps))
        || Number(draft.durationMin ?? 0) !== Number(corrected.durationMin ?? 0) || exEntries.some(([id, performance]) => {
        const raw = performance.sets || [], clean = corrected.perf[id]?.sets || [];
        if (raw.length !== clean.length) return true;
        return raw.some((set, index) => Number(set.w ?? 0) !== clean[index].w || Number(set.r) !== clean[index].r
            || (set.rir !== '' && set.rir != null && Number(set.rir) !== clean[index].rir));
    });
    const save = () => {
        if (!exEntries.length) return;
        if (invalid) { setShowError(true); return; }
        onSave(corrected);
    };
    const field = (id, label, props, className = '') => h('label', {
        className: 'wpb-history-editor-label ' + className, htmlFor: id
    }, label, h('input', { ...props, id, className: 'mono wpb-history-editor-input',
        'aria-describedby': showError && invalid ? 'history-editor-error' : undefined }));
    return h('div', { className: 'wpb-backdrop wpb-history-editor-backdrop', role: 'dialog',
        'aria-modal': true, 'aria-labelledby': 'history-editor-title', 'aria-describedby': 'history-editor-help',
        onClick: event => { if (event.target === event.currentTarget) onClose(); } },
        h('section', { ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown,
            className: 'wpb-history-editor-sheet', onClick: event => event.stopPropagation() },
            h('header', { className: 'wpb-history-editor-heading' },
                h(Pencil, { size: 18, 'aria-hidden': true }),
                h('div', { className: 'wpb-history-editor-copy' },
                    h('h2', { id: 'history-editor-title' }, 'Correct workout')),
                h(UiAction, { variant: 'tonal', 'data-icon-only': true, 'data-wpb-system-back': true,
                    'aria-label': 'Close history editor', onClick: onClose }, h(X, { size: 18 }))),
            h('div', { className: 'wpb-scroll wpb-history-editor-body' },
                h('p', { className: 'wpb-history-editor-context' }, entry.dayLabel || 'Workout'),
                h('p', { id: 'history-editor-help', className: 'wpb-history-editor-help' },
                    'Edit this workout’s logged sets and timing.'),
                h('div', { className: 'wpb-history-editor-metadata' },
                    field('history-editor-date', 'Date / time', { type: 'datetime-local', value: localHistoryDateValue(draft.date),
                        onChange: event => { const date = new Date(event.target.value).getTime();
                            if (Number.isFinite(date)) setDraft(previous => ({ ...previous, date })); } }, 'wpb-history-editor-date'),
                    field('history-editor-minutes', 'Minutes', { inputMode: 'numeric', onFocus: selectNumericInput, onClick: selectNumericInput,
                        value: draft.durationMin ?? '', onChange: event => setDraft(previous => ({ ...previous, durationMin: event.target.value })) })),
                ...exEntries.map(([exId, performance]) => h('section', { key: exId, className: 'wpb-history-editor-exercise',
                    'aria-labelledby': `history-editor-exercise-${exId}` },
                    h('div', { className: 'wpb-history-editor-exercise-heading' },
                        h('h3', { id: `history-editor-exercise-${exId}` }, EX_BY_ID[exId]?.name || exId),
                        h(UiAction, { variant: 'text', className: 'wpb-history-editor-remove-lift', onClick: () => removeExercise(exId),
                            'aria-label': `Remove ${EX_BY_ID[exId]?.name || exId} from correction` }, 'Remove lift')),
                    ...(performance.sets || []).map((set, index) => h('div', { key: index, role: 'group',
                        'aria-label': `${set.sub ? 'Sub-set' : 'Set'} ${index + 1}`, 'data-sub': !!set.sub,
                        className: 'wpb-history-editor-set' },
                        h('span', { className: 'mono wpb-history-editor-set-index', 'aria-hidden': true }, set.sub ? '↳' : String(index + 1)),
                        field(`history-editor-${exId}-${index}-load`, `Load ${unit || entry.unit || ''}`, { inputMode: 'decimal',
                            onFocus: selectNumericInput, onClick: selectNumericInput, value: set.w ?? '',
                            onChange: event => setSet(exId, index, 'w', event.target.value) }, 'wpb-history-editor-load'),
                        field(`history-editor-${exId}-${index}-reps`, 'Reps', { inputMode: 'numeric',
                            onFocus: selectNumericInput, onClick: selectNumericInput, value: set.r ?? '',
                            onChange: event => setSet(exId, index, 'r', event.target.value) }, 'wpb-history-editor-reps'),
                        field(`history-editor-${exId}-${index}-rir`, 'RIR', { inputMode: 'decimal',
                            onFocus: selectNumericInput, onClick: selectNumericInput, placeholder: '—', value: set.rir ?? '',
                            onChange: event => setSet(exId, index, 'rir', event.target.value) }, 'wpb-history-editor-rir'),
                        h(UiAction, { variant: 'tonal', 'data-icon-only': true, 'aria-label': `Remove set ${index + 1}`,
                            className: 'wpb-history-editor-remove-set', onClick: () => removeSet(exId, index) }, h(X, { size: 16 })))),
                    h(UiAction, { variant: 'tonal', className: 'wpb-history-editor-add-set', onClick: () => addSet(exId) }, '+ Add set'))),
                !exEntries.length && h('p', { className: 'wpb-history-editor-empty' },
                    'No sets remain. Delete the whole workout instead if this session should not exist.')),
            h('footer', { className: 'wpb-history-editor-footer' },
                showError && invalid && h('p', { id: 'history-editor-error', role: 'status' },
                    'Review incomplete or invalid values before saving.'),
                h('div', { className: 'wpb-history-editor-actions' },
                    h(UiAction, { variant: 'tonal', onClick: onClose }, 'Cancel'),
                    h(UiAction, { variant: 'primary', disabled: !exEntries.length, onClick: save,
                        'aria-describedby': showError && invalid ? 'history-editor-error' : undefined }, 'Save correction')))));
}
