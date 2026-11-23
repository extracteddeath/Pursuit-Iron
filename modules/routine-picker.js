// Freestyle start sheet, ported from the M3 prototype's routines: an empty workout, or one tap
// into a routine saved from an earlier freestyle session (saved programs flagged `quick`).
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { Play, Plus, Trash2 } from 'lucide-react';
import { UiAction, UiIconAction, UiListItem } from './ui-primitives.js';
import { UiSheet } from './ui-sheet.js';
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
    return _jsx(UiSheet, { id: 'routine-picker', 'data-routine-picker': true, title: 'Freestyle', onClose, closeLabel: 'Close freestyle',
        className: 'wpb-routine-backdrop', children: [
            _jsxs(UiAction, { variant: 'tonal', className: 'wpb-routine-empty', onClick: onStartEmpty, children: [_jsx(Plus, { size: 18, 'aria-hidden': true }), 'Empty workout'] }, 'empty'),
            _jsx('h3', { className: 'wpb-ui-list-subheader', children: 'Your routines' }, 'head'),
            _jsx('div', { className: 'wpb-ui-list', children: routines.map(r => _jsxs('div', { className: 'wpb-ui-list-row', children: [
                _jsx(UiListItem, { onClick: () => onStart(r.program), 'aria-label': `Start ${r.name}`,
                    leading: _jsx(Play, { size: 18, 'aria-hidden': true }), headline: r.name, supporting: r.lifts.join(' · '),
                    overline: `${r.lifts.length} ${r.lifts.length === 1 ? 'lift' : 'lifts'} · ${routineWhen(r.lastAt)}` }),
                onRemove && _jsx(UiIconAction, { className: 'wpb-routine-remove', onClick: () => onRemove(r.program), 'aria-label': `Remove ${r.name}`, children: _jsx(Trash2, { size: 16, 'aria-hidden': true }) })
            ] }, r.id)) }, 'list'),
            _jsx('p', { className: 'wpb-routine-hint', children: 'Finish a freestyle workout with “Save as routine” to add one here.' }, 'hint')
        ] });
}
