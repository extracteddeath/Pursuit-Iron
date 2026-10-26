// One pinned-note editor and physical-setup presentation. App owns persistence.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useState } from 'react';
import { Pin } from 'lucide-react';
import { setupFieldsWithStored } from './engine-api.js';
import { UiAction } from './ui-primitives.js';

function SetupFieldsGrid({ ex, source, value, onChange }) {
    // Retain saved orphan fields while editing, including after clearing their value.
    const fields = setupFieldsWithStored(ex, source);
    if (!fields.length) return null;
    return _jsx('div', { className: 'wpb-setup-fields', children: fields.map(field =>
        _jsxs('label', { className: 'wpb-setup-field', 'data-orphan': field.orphan || undefined, children: [
            _jsx('span', { className: 'wpb-setup-field-label', title: field.orphan
                ? "Saved earlier — this exercise's equipment no longer lists this setting. Clear it to remove." : undefined,
                children: field.label }),
            _jsx('input', { 'aria-label': field.label, className: 'wpb-setup-field-input',
                value: value[field.key] ?? '', onChange: event => onChange(field.key, event.target.value),
                placeholder: '—', inputMode: 'text' })
        ] }, field.key)) });
}

export function SetupFieldsDisplay({ ex, value }) {
    const fields = setupFieldsWithStored(ex, value).filter(field => value?.[field.key]);
    if (!fields.length) return null;
    return _jsx('div', { className: 'wpb-setup-chips', children: fields.map(field =>
        _jsxs('span', { className: 'wpb-setup-chip', children: [field.label, ' ', value[field.key]] }, field.key)) });
}

export function PinnedNoteEditor({ ex, note = '', setup = {}, onNote, onSetup, onClose, context = 'detail', saveLabel = 'Save note' }) {
    const [text, setText] = useState(() => String(note ?? ''));
    const [draft, setDraft] = useState(() => ({ ...setup }));
    const saved = !!(note || Object.values(setup || {}).some(Boolean));
    const commit = (nextText, nextSetup) => {
        // The existing App callbacks normalize and persist in the same React event.
        // Typing and Cancel never call them; unchanged fields do not cause writes.
        if (String(nextText || '').trim() !== String(note || '').trim()) onNote?.(ex.id, nextText);
        for (const key of new Set([...Object.keys(setup || {}), ...Object.keys(nextSetup)])) {
            const next = String(nextSetup[key] ?? '').trim();
            if (next !== String(setup?.[key] ?? '').trim()) onSetup?.(ex.id, key, next);
        }
        onClose();
    };
    return _jsxs('section', { className: 'wpb-note-editor', 'data-pinned-note-editor': ex.id, 'data-context': context,
        role: 'group', 'aria-label': `Pinned note and setup for ${ex.name}`, children: [
            _jsxs('h3', { className: 'wpb-note-editor-heading', children: [
                _jsx(Pin, { size: 12, 'aria-hidden': true }), 'Pinned note', context !== 'program' && ` · ${ex.name}`
            ] }),
            _jsx(SetupFieldsGrid, { ex, source: setup, value: draft,
                onChange: (key, value) => setDraft(previous => ({ ...previous, [key]: value })) }),
            _jsx('textarea', { autoFocus: true, value: text, onChange: event => setText(event.target.value),
                'aria-label': `Pinned note for ${ex.name}`, placeholder: 'e.g. pause on chest · pinky on ring',
                rows: context === 'program' ? 2 : 3, className: 'wpb-scroll wpb-note-editor-text' }),
            _jsx('p', { className: 'wpb-note-editor-help', children: 'Saved to this exercise wherever it appears. Save applies both the note and setup; Cancel discards your edits.' }),
            _jsxs('div', { className: 'wpb-note-editor-actions', children: [
                _jsx(UiAction, { variant: 'primary', onClick: () => commit(text, draft), children: saveLabel }),
                saved && _jsx(UiAction, { variant: 'tonal', className: 'wpb-note-editor-remove', onClick: () => commit('', {}), children: 'Remove' }),
                _jsx(UiAction, { variant: 'tonal', onClick: onClose, children: 'Cancel' })
            ] })
        ] });
}
