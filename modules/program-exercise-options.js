// Exercise inspection and actions. ProgramView remains the only prescription writer.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useState } from 'react';
import { X, Pin, Layers, HelpCircle, Info, Ban, Trash2 } from 'lucide-react';
import { UiAction } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';
import { PART_LABEL, formatSetup } from './engine-api.js';

export function ProgramExerciseOptions({ exercise, primary, canRemove, canLink, linked,
    note, setup, onNote, onSuperset, onWhy, onInfo, onBan, onRemove, onClose, sheetDragRef }) {
    const [confirmRemove, setConfirmRemove] = useState(false);
    const hasNote = !!(note || setup);
    const close = () => { setConfirmRemove(false); onClose(); };
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(close, sheetDragRef);
    const act = handler => () => { close(); handler(); };
    const action = (id, Icon, label, description, onClick, danger = false) => _jsxs(UiAction, {
        variant: 'tonal', className: 'wpb-sheet-action wpb-program-exercise-action',
        'data-exercise-action': id, 'data-tone': danger ? 'danger' : undefined, onClick,
        children: [
            _jsx('span', { className: 'wpb-exercise-options-icon', 'aria-hidden': true, children: _jsx(Icon, { size: 18 }) }),
            _jsxs('span', { className: 'wpb-exercise-options-copy', children: [
                _jsx('span', { className: 'wpb-exercise-options-label', children: label }),
                _jsx('span', { className: 'wpb-exercise-options-description', children: description })
            ] })
        ]
    }, id);
    return _jsx('div', { className: 'wpb-backdrop wpb-exercise-options-backdrop', role: 'dialog',
        'aria-modal': true, 'aria-labelledby': 'program-exercise-options-title', onClick: event => { if (event.target === event.currentTarget) close(); },
        children: _jsxs('section', { ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown, className: 'wpb-exercise-options-sheet',
            onClick: event => event.stopPropagation(), children: [
                _jsxs('header', { className: 'wpb-exercise-options-heading', children: [
                    _jsxs('div', { className: 'wpb-exercise-options-identity', children: [
                        _jsx('h2', { id: 'program-exercise-options-title', className: 'wpb-exercise-options-title', children: exercise.name }),
                        _jsx('p', { className: 'wpb-exercise-options-meta', children: `${PART_LABEL[exercise.part]} · ${exercise.type}${primary ? ' · key lift' : ''}` })
                    ] }),
                    _jsx(UiAction, { variant: 'tonal', 'data-icon-only': true, 'aria-label': 'Close', onClick: close,
                        className: 'hit wpb-sheet-close', children: _jsx(X, { size: 18 }) })
                ] }),
                _jsxs('div', { className: 'wpb-scroll wpb-exercise-options-body', children: [
                    action('note', Pin, hasNote ? 'Edit pinned note' : 'Add pinned note',
                        setup ? [formatSetup(setup), note].filter(Boolean).join(' — ') : note || "A cue you'll see every time — seat height, tempo…", act(onNote)),
                    canLink && action('superset', Layers, linked ? 'Unlink superset' : 'Superset with next',
                        linked ? 'Currently paired with the next exercise' : 'Pair with the next exercise, alternating sets', act(onSuperset)),
                    action('why', HelpCircle, 'Why this lift?', 'See why the program selected this exercise', act(onWhy)),
                    action('info', Info, 'Exercise info', 'Muscles worked, equipment, form demo', act(onInfo)),
                    action('ban', Ban, 'Ban exercise', 'Never auto-pick this again, swap it out now', act(onBan), true),
                    canRemove && (confirmRemove ? _jsxs('div', { className: 'wpb-exercise-options-confirm',
                        role: 'group', 'aria-labelledby': 'program-exercise-remove-title', children: [
                            _jsx('p', { id: 'program-exercise-remove-title', children: `Remove ${exercise.name} from this day?` }),
                            _jsxs('div', { className: 'wpb-exercise-options-confirm-actions', children: [
                                _jsx(UiAction, { variant: 'tonal', className: 'wpb-exercise-options-remove', onClick: act(onRemove), children: 'Remove' }),
                                _jsx(UiAction, { variant: 'tonal', autoFocus: true, onClick: () => setConfirmRemove(false), children: 'Cancel' })
                            ] })
                        ] }) : action('remove', Trash2, 'Remove from day', 'Take this exercise out of the workout', () => setConfirmRemove(true), true))
                ] })
            ] }) });
}
