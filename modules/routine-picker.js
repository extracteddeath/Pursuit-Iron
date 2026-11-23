// Freestyle start sheet, ported from the M3 prototype's routines: an empty workout, or one tap
// into a routine saved from an earlier freestyle session (saved programs flagged `quick`).
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { Play, Plus, Trash2 } from 'lucide-react';
import { UiAction, UiIconAction, UiSheetClose, UiSheetHeading } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';
import { EX_BY_ID } from './engine-api.js';

const DAY_MS = 86400000;

/** Saved routines, most recently trained first, each with its lift names and last session date. */
export function routineList(saved = [], history = []) {
    const last = new Map();
    for (const h of history || []) if (h?.programId && Number(h.date) > (last.get(h.programId) || 0)) last.set(h.programId, Number(h.date));
    return (saved || []).filter(p => p?.quick && p.days?.[0]?.exercises?.length).map(p => ({
        program: p, id: p.id, name: p.name || 'Routine',
        lifts: p.days[0].exercises.map(id => EX_BY_ID[id]?.name).filter(Boolean),
        lastAt: last.get(p.id) || 0
    })).sort((a, b) => (b.lastAt - a.lastAt) || ((Number(b.program.createdAt) || 0) - (Number(a.program.createdAt) || 0)));
}

export function routineWhen(lastAt, now = Date.now()) {
    if (!lastAt) return 'Not trained yet';
    const days = Math.floor((now - lastAt) / DAY_MS);
    return days <= 0 ? 'Trained today' : days === 1 ? 'Trained yesterday' : `Trained ${days} days ago`;
}

export function RoutinePickerSheet({ routines, onStartEmpty, onStart, onRemove, onClose }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose);
    return _jsx('div', { role: 'dialog', 'aria-modal': true, 'aria-labelledby': 'routine-picker-title', 'data-routine-picker': true,
        onClick: event => { if (event.target === event.currentTarget) onClose(); },
        className: 'wpb-backdrop wpb-ui-sheet-backdrop wpb-ui-sheet-focus wpb-routine-backdrop', children:
        _jsxs('section', { ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown, className: 'wpb-ui-sheet-panel wpb-routine-panel', onClick: event => event.stopPropagation(), children: [
            _jsxs(UiSheetHeading, { as: 'header', className: 'wpb-routine-heading', children: [
                _jsx('h2', { id: 'routine-picker-title', className: 'wpb-ui-sheet-title', children: 'Freestyle' }),
                _jsx(UiSheetClose, { onClick: onClose, 'aria-label': 'Close freestyle' })
            ] }),
            _jsxs('div', { className: 'wpb-scroll wpb-ui-sheet-scroll wpb-routine-body', children: [
                _jsxs(UiAction, { variant: 'tonal', className: 'wpb-routine-empty', onClick: onStartEmpty, children: [_jsx(Plus, { size: 18, 'aria-hidden': true }), 'Empty workout'] }),
                _jsx('h3', { className: 'wpb-routine-section', children: 'Your routines' }),
                _jsx('ul', { className: 'wpb-routine-list', children: routines.map(r => _jsxs('li', { className: 'wpb-routine-item', children: [
                    _jsxs(UiAction, { variant: 'row', className: 'wpb-routine-start', onClick: () => onStart(r.program), 'aria-label': `Start ${r.name}`, children: [
                        _jsxs('span', { className: 'wpb-routine-copy', children: [
                            _jsx('strong', { children: r.name }),
                            _jsx('span', { className: 'wpb-routine-lifts', children: r.lifts.join(' · ') }),
                            _jsx('span', { className: 'wpb-routine-when', children: `${r.lifts.length} ${r.lifts.length === 1 ? 'lift' : 'lifts'} · ${routineWhen(r.lastAt)}` })
                        ] }),
                        _jsx('span', { className: 'wpb-routine-play', 'aria-hidden': true, children: _jsx(Play, { size: 18 }) })
                    ] }),
                    onRemove && _jsx(UiIconAction, { className: 'wpb-routine-remove', onClick: () => onRemove(r.program), 'aria-label': `Remove ${r.name}`, children: _jsx(Trash2, { size: 16, 'aria-hidden': true }) })
                ] }, r.id)) }),
                _jsx('p', { className: 'wpb-routine-hint', children: 'Finish a freestyle workout with “Save as routine” to add one here.' })
            ] })
        ] }) });
}
