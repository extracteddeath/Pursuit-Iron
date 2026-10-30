// M365: Program weekly-schedule presentation. The parent is the sole schedule writer.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { Dumbbell } from 'lucide-react';
import { UiSheetClose } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';

const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const JS_WEEKDAYS = [1, 2, 3, 4, 5, 6, 0];

// Resolve scheduled IDs against the current program before counting training days.
// Saved schedules may contain obsolete IDs after cycle edits or import; never count
// an assignment that the sheet can only render as a rest day.
export function resolvedWeeklySchedule(program) {
    const dayMap = new Map((program?.days || []).filter(day => day?.id != null).map(day => [String(day.id), day]));
    const schedule = program?.schedule || {};
    return JS_WEEKDAYS.map(weekday => ({ weekday, day: dayMap.get(String(schedule[weekday])) || null }));
}

export function WeeklyScheduleSheet({ program, onCycle, onClear, onClose, sheetDragRef }) {
    const { attachPanel, onPanelKeyDown, panelRef } = useProgramSheetFocus(onClose, sheetDragRef);
    const schedule = program.schedule || {};
    const rows = resolvedWeeklySchedule(program);
    const trainingDays = rows.filter(row => row.day).length;
    const todayWd = new Date().getDay();
    return _jsx('div', {
        'data-testid': 'weekly-schedule-sheet', role: 'dialog', 'aria-modal': true,
        'aria-labelledby': 'program-weekly-schedule-title',
        className: 'wpb-backdrop wpb-ui-sheet-backdrop wpb-program-schedule-backdrop', onClick: event => { if (event.target === event.currentTarget) onClose(); },
        children: _jsxs('div', {
            ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown, onClick: event => event.stopPropagation(),
            className: 'wpb-scroll wpb-program-schedule-sheet',
            children: [
                _jsxs('div', { className: 'wpb-ui-sheet-heading wpb-program-schedule-heading', children: [
                    _jsx('h2', { id: 'program-weekly-schedule-title',
                        className: 'wpb-ui-sheet-title wpb-program-schedule-title', children: 'Weekly schedule' }),
                    _jsx(UiSheetClose, { onClick: onClose, className: 'wpb-program-schedule-close' })
                ] }),
                _jsxs('p', { className: 'wpb-program-schedule-caption', children: [
                    'Tap a day to assign a workout — ',
                    _jsxs('span', { role: 'status', 'aria-live': 'polite', 'aria-atomic': true, children: [
                        trainingDays, ' training ', trainingDays === 1 ? 'day' : 'days',
                        ', ', 7 - trainingDays, ' rest'
                    ] }),
                    '. Cycles through your days, then rest.'
                ] }),
                ...DAY_LABELS.map((label, i) => {
                    const { weekday, day } = rows[i];
                    const isToday = weekday === todayWd;
                    return _jsxs('button', {
                        type: 'button',
                        'aria-label': label + (isToday ? ', today' : '') + ': ' + (day?.label || 'Rest day') + '. Change assignment',
                        onClick: () => onCycle(weekday),
                        className: 'pressable wpb-program-schedule-row',
                        'data-assigned': day ? 'true' : 'false',
                        'data-today': isToday ? 'true' : 'false',
                        children: [
                            _jsxs('span', { className: 'wpb-program-schedule-weekday', children: [
                                _jsx('span', { className: 'wpb-program-schedule-weekday-name', children: label }),
                                isToday && _jsx('small', { className: 'wpb-program-schedule-today',
                                    children: 'Today' })
                            ] }),
                            _jsx('span', { className: 'wpb-program-schedule-assignment',
                                children: day ? day.label : 'Rest day' }),
                            day ? _jsx(Dumbbell, { size: 15, className: 'wpb-program-schedule-icon', 'aria-hidden': true })
                                : _jsx('span', { className: 'wpb-program-schedule-hint',
                                    children: 'tap to set' })
                        ]
                    }, weekday);
                }),
                Object.keys(schedule).length > 0 && _jsx('button', {
                    type: 'button', 'aria-label': 'Clear all scheduled workouts',
                    onClick: () => {
                        // The Clear button unmounts when the schedule becomes empty.
                        // Move focus to a stable control before changing parent state,
                        // otherwise Escape would go to the background document.
                        panelRef.current?.querySelector('.wpb-program-schedule-close')?.focus({ preventScroll: true });
                        onClear();
                    },
                    className: 'pressable wpb-program-schedule-clear', children: 'Clear schedule'
                })
            ]
        })
    });
}
