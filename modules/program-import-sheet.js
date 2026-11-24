// Shared programs in, from Plan's program list: paste a PI2 code (or a plan shared as text), or
// browse the community gallery. Bringing a program in is program management, so it lives with
// the other ways to start one (Templates, Custom split, Cycles, New program) — not in Settings.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useState } from 'react';
import { Layers } from 'lucide-react';
import { UiAction } from './ui-primitives.js';
import { UiSheet } from './ui-sheet.js';

export function ProgramImportSheet({ onImport, onBrowse, onClose }) {
    const [text, setText] = useState('');
    const [result, setResult] = useState(null);
    const [busy, setBusy] = useState(false);
    const submit = () => {
        if (!text.trim() || busy) return;
        setBusy(true);
        Promise.resolve(onImport(text)).then(r => {
            setBusy(false);
            if (r?.ok) onClose(); else setResult(r || { ok: false, msg: 'That could not be imported.' });
        });
    };
    return _jsx(UiSheet, { id: 'program-import', title: 'Shared program', onClose, closeLabel: 'Close shared program',
        className: 'wpb-program-import', footer: _jsx(UiAction, { variant: 'filled', onClick: submit, disabled: !text.trim() || busy,
            className: 'wpb-settings-import-submit', children: busy ? 'Importing…' : 'Import program' }),
        children: [
            _jsxs('p', { className: 'wpb-type-section-intro', children: ['Paste a ', _jsx('b', { children: 'program code' }),
                ' (PI2-…) to add someone’s program as an editable copy: every day, exercise, set, rep range, effort and rest.'] }),
            _jsx('textarea', { 'aria-label': 'Program code or text', value: text, autoFocus: true,
                onChange: e => { setText(e.target.value); setResult(null); }, placeholder: 'Paste a code (PI2-…)…',
                className: 'wpb-scroll mono wpb-ui-control wpb-settings-import-input' }),
            result && _jsx('div', { className: 'wpb-settings-import-result', role: 'status', 'data-outcome': result.ok ? 'ok' : 'error', children: result.msg }),
            onBrowse && _jsxs(UiAction, { variant: 'text', className: 'wpb-program-import-browse', onClick: () => { onClose(); onBrowse(); }, children: [
                _jsx(Layers, { size: 16, 'aria-hidden': true }), ' Browse community programs'] })
        ] });
}
