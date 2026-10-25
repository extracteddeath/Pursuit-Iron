// M365: Program weekly-schedule presentation. The parent is the sole schedule writer.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { X, Dumbbell } from 'lucide-react';

const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const JS_WEEKDAYS = [1, 2, 3, 4, 5, 6, 0];

export function WeeklyScheduleSheet({ program, onCycle, onClear, onClose, sheetDragRef }) {
    const schedule = program.schedule || {};
    const trainingDays = JS_WEEKDAYS.filter(weekday => schedule[weekday]).length;
    const todayWd = new Date().getDay();
    return _jsx('div', {
        'data-testid': 'weekly-schedule-sheet', role: 'dialog', 'aria-modal': true,
        'aria-labelledby': 'program-weekly-schedule-title',
        className: 'wpb-backdrop wpb-program-schedule-backdrop', onClick: onClose,
        children: _jsxs('div', {
            ref: sheetDragRef, onClick: event => event.stopPropagation(),
            className: 'wpb-scroll wpb-program-schedule-sheet',
            children: [
                _jsxs('div', { className: 'wpb-program-schedule-heading', children: [
                    _jsx('h2', { id: 'program-weekly-schedule-title',
                        className: 'wpb-program-schedule-title', children: 'Weekly schedule' }),
                    _jsx('button', { type: 'button', 'aria-label': 'Close', onClick: onClose,
                        className: 'pressable hit wpb-sheet-close wpb-program-schedule-close',
                        children: _jsx(X, { size: 18 }) })
                ] }),
                _jsxs('p', { className: 'wpb-program-schedule-caption', children: [
                    'Tap a day to assign a workout — ', trainingDays, ' training ',
                    trainingDays === 1 ? 'day' : 'days', ', ', 7 - trainingDays,
                    ' rest. Cycles through your days, then rest.'
                ] }),
                ...DAY_LABELS.map((label, i) => {
                    const weekday = JS_WEEKDAYS[i];
                    const id = schedule[weekday];
                    const day = id ? program.days.find(item => item.id === id) : null;
                    const isToday = weekday === todayWd;
                    return _jsxs('button', {
                        type: 'button', 'aria-label': 'Schedule ' + label,
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
                    type: 'button', 'aria-label': 'Clear schedule', onClick: onClear,
                    className: 'pressable wpb-program-schedule-clear', children: 'Clear schedule'
                })
            ]
        })
    });
}
