// Program reference and progression presentation. App owns changes and persistence.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo, useState } from 'react';
import { X, Check, Settings2, TrendingUp, Star, ChevronLeft, ChevronRight } from 'lucide-react';
import { weeksOf, sessionPrescriptionRows, EX_BY_ID, PROG_STYLES, effortLabel, computeCell, exerciseAt } from './engine-api.js';
import { UiChoice } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';
import { InfoCard } from './ui-disclosure.js';

export function ProgressionChoiceSheet({ exerciseName, selected, automaticLabel, linearIncrement, unit, onChoose, onClose, sheetDragRef }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose, sheetDragRef);
    const options = [
        ['auto', 'Auto (recommended)', `Use this program’s per-week methods. Currently ${automaticLabel}.`],
        ['dynamic', 'Dynamic double', 'Each working set builds reps and earns its own load increase.'],
        ['double', 'Double progression', 'Build reps across the working sets before increasing their shared load.'],
        ['ladder', 'Rep ladder', 'Raise a shared rep target in steps, then increase the load.'],
        ['wave', 'Wave loading', 'Vary rep targets within the prescribed range across sessions.'],
        ['linear', 'Linear progression', `Add ${linearIncrement} ${unit} after successful work at the bottom of the range.`],
        ['e1rm', 'e1RM autoregulation', 'Use recent strength estimates and the target effort to choose a load.']
    ];
    return _jsx('div', { className: 'wpb-backdrop wpb-progression-choice-backdrop', role: 'dialog', 'aria-modal': true,
        'aria-labelledby': 'program-progression-title', 'aria-describedby': 'program-progression-scope', onClick: event => { if (event.target === event.currentTarget) onClose(); },
        children: _jsxs('section', { className: 'wpb-progression-choice-panel', ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown,
            onClick: event => event.stopPropagation(), children: [
                _jsxs('header', { className: 'wpb-progression-choice-heading', children: [
                    _jsxs('div', { children: [
                        _jsx('h2', { id: 'program-progression-title', children: 'Progression method' }),
                        _jsx('p', { className: 'wpb-progression-choice-exercise', children: exerciseName })
                    ] }),
                    _jsx('button', { type: 'button', className: 'pressable hit wpb-sheet-close wpb-progression-choice-close',
                        'aria-label': 'Close progression methods', onClick: onClose, children: _jsx(X, { size: 18 }) })
                ] }),
                _jsxs('div', { className: 'wpb-scroll wpb-progression-choice-body', children: [
                    _jsx('p', { id: 'program-progression-scope', className: 'wpb-progression-choice-scope',
                        children: 'Choose how this lift progresses throughout the program. Review and save your program to keep the change.' }),
                    _jsx('div', { className: 'wpb-progression-choice-options', role: 'group', 'aria-label': 'Progression methods',
                        children: options.map(([value, label, description]) => _jsxs(UiChoice, {
                            selected: selected === value, className: 'wpb-progression-choice', 'data-program-progression-choice': value,
                            onClick: () => onChoose(value), children: [
                                _jsx('span', { className: 'wpb-progression-choice-check', 'aria-hidden': true,
                                    children: selected === value && _jsx(Check, { size: 15 }) }),
                                _jsxs('span', { className: 'wpb-progression-choice-copy', children: [
                                    _jsx('strong', { children: label }), _jsx('span', { children: description })
                                ] })
                            ] }, value)) })
                ] })
            ] }) });
}

export function ProgramReferenceSheet({ onClose, sheetDragRef, children }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose, sheetDragRef);
    return _jsx('div', { className: 'wpb-backdrop wpb-program-reference-backdrop', role: 'dialog', 'aria-modal': true,
        'aria-labelledby': 'program-reference-title', onClick: event => { if (event.target === event.currentTarget) onClose(); },
        children: _jsxs('div', { className: 'wpb-program-reference-panel', ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown,
            onClick: event => event.stopPropagation(), children: [
                _jsxs('div', { className: 'wpb-program-reference-heading', children: [
                    _jsx('h2', { id: 'program-reference-title', children: 'Plan info' }),
                    _jsx('button', { type: 'button', className: 'pressable hit wpb-sheet-close wpb-program-reference-close',
                        'aria-label': 'Close program details', onClick: onClose, children: _jsx(X, { size: 18 }) })
                ] }),
                _jsxs('div', { className: 'wpb-scroll wpb-program-reference-body', children: [
                    _jsx('p', { className: 'wpb-program-reference-intro', children: 'How this program was put together, how progression was chosen, what it trains each week, and the arc it follows.' }),
                    children
                ] })
            ] }) });
}

export function SetupCard({ chips = [], open, onToggle }) {
    if (!chips.length) return null;
    const headline = chips.filter(chip => chip.key === 'focus' || chip.key === 'reduce');
    return _jsx(InfoCard, { icon: Settings2, title: 'How this was built', open, onToggle,
        subtitle: headline.length ? headline.map(chip => `${chip.label}: ${chip.value}`).join(' · ') : `${chips.length} settings`,
        children: _jsx('div', { className: 'wpb-program-setup-chips', children: chips.map(chip =>
            _jsxs('div', { className: 'wpb-program-setup-chip', 'data-tone': chip.tone, children: [
                _jsx('span', { className: 'wpb-program-setup-label', children: chip.label }),
                _jsx('span', { className: 'wpb-program-setup-value', children: chip.value })
            ] }, chip.key)) }) });
}

export function ProgressionCard({ program, open, onToggle }) {
    const [q, setQ] = useState('');
    const [filter, setFilter] = useState('all');
    const [from, setFrom] = useState(0);
    const weeks = weeksOf(program);
    const totalWeeks = weeks + (program.config?.deload ? 1 : 0);
    // Read the saved selection record. The UI never selects a progression method.
    const selectedProgressions = Array.isArray(program?.nextEngine?.progressionPlan) ? program.nextEngine.progressionPlan : [];
    const progressionLabel = style => PROG_STYLES[style] || String(style || 'Auto').replace(/_/g, ' ');
    const progressionGroups = [];
    const progressionGroupMap = new Map();
    for (const item of selectedProgressions) {
        const key = item?.style || 'auto';
        let group = progressionGroupMap.get(key);
        if (!group) { group = { key, label: progressionLabel(key), items: [] }; progressionGroupMap.set(key, group); progressionGroups.push(group); }
        group.items.push(item);
    }
    const adaptiveSelections = selectedProgressions.filter(item => item?.source === 'adaptive' || item?.source === 'adaptive_hold').length;
    const changedSelections = selectedProgressions.filter(item => item?.changed === true).length;
    const manualSelections = selectedProgressions.filter(item => item?.source === 'manual').length;
    const progressionOverview = selectedProgressions.length ? _jsxs('details', {
        className: 'wpb-progression-explanation', 'data-progression-selection': true, children: [
            _jsx('summary', { children: 'Why these methods?' }),
            _jsx('p', { className: 'wpb-progression-origin-note', children: 'This records how the block was built. Current weekly methods appear above; your later choices take precedence.' }),
            _jsxs('div', { className: 'wpb-progression-explanation-heading', children: [
                _jsx('strong', { children: manualSelections === selectedProgressions.length ? 'Your progression choices'
                    : adaptiveSelections ? 'Progression re-checked from your training' : 'Auto progression for this block' }),
                _jsx('span', { children: adaptiveSelections ? `${changedSelections} changed · ${Math.max(0, adaptiveSelections - changedSelections)} kept`
                    : `${progressionGroups.length} method${progressionGroups.length === 1 ? '' : 's'}` })
            ] }),
            _jsx('p', { children: manualSelections === selectedProgressions.length
                ? 'These methods are locked by your choices. Auto will not replace them at block review.'
                : adaptiveSelections ? 'Auto used comparable workout history before keeping or changing each method. One unusual workout is not enough to switch it.'
                : 'Pursuit chose per exercise from the lift, rep target, loading setup, your experience, phase, and block length. It can re-check the method between blocks once enough comparable training exists.' }),
            _jsx('div', { className: 'wpb-progression-methods', children: progressionGroups.map(group =>
                _jsxs('span', { children: [group.label, _jsx('b', { className: 'mono', children: group.items.length })] }, group.key)) }),
            selectedProgressions.map((item, index) => _jsxs('div', { 'data-progression-reason': true,
                className: 'wpb-progression-reason', children: [
                    _jsxs('div', { className: 'wpb-progression-reason-heading', children: [
                        _jsx('strong', { children: item.exerciseName || 'Exercise' }), _jsx('span', { children: progressionLabel(item.style) })] }),
                    item.previousStyle && _jsx('p', { 'data-progression-delta': item.changed ? 'changed' : 'kept',
                        children: item.changed ? `Changed: ${progressionLabel(item.previousStyle)} → ${progressionLabel(item.style)}` : `Kept: ${progressionLabel(item.style)}` }),
                    _jsx('p', { children: item.reason || 'Auto selected the progression that best fits this exercise and block.' })
                ] }, `${item.exerciseId || item.exerciseName || 'exercise'}:${index}`))
        ] }) : null;
    const rows = useMemo(() => (program.days || []).flatMap(day => (day.exercises || []).map((id, slot) => ({
        ex: EX_BY_ID[id], day, slot, primary: slot === day.primaryIndex,
        alternate: EX_BY_ID[program.pairs?.[`${day.id}:${slot}`]]
    }))), [program]);
    // Paging uses a zero-based position; the domain API always receives a one-based week.
    // Clamp the view when the same program is shortened without changing component identity.
    const start = Math.max(0, Math.min(from, totalWeeks - Math.min(2, totalWeeks)));
    const cols = [start + 1, start + 2].filter(week => week <= totalWeeks);
    const columns = useMemo(() => cols.map(week => ({ week, rows: new Map((program.days || []).flatMap(day =>
        sessionPrescriptionRows(program, day, week).map(row => [`${day.id}:${row.slot}`, row]))) })), [program, start, totalWeeks]);
    const upper = new Set(['chest', 'lats', 'upper_back', 'shoulders', 'biceps', 'triceps', 'forearms', 'traps', 'neck']);
    const shown = rows.filter(row => {
        const candidates = [row.ex, row.alternate].filter(Boolean);
        if (q && !candidates.some(ex => ex.name.toLowerCase().includes(q.toLowerCase()))) return false;
        if (filter === 'primary') return row.primary;
        if (filter === 'upper') return candidates.some(ex => upper.has(ex.part));
        if (filter === 'lower') return candidates.some(ex => !upper.has(ex.part));
        return true;
    });
    return _jsx(InfoCard, { icon: TrendingUp, title: 'Lift progression', open, onToggle,
        subtitle: `${rows.length} lifts · ${weeks} training week${weeks === 1 ? '' : 's'}${program.config?.deload ? ' + deload' : ''}`,
        children: _jsxs('div', { className: 'wpb-progression', 'data-columns': cols.length, children: [
            _jsx('input', { value: q, onChange: event => setQ(event.target.value), placeholder: 'Search a lift…',
                'aria-label': 'Search a lift', className: 'wpb-progression-search' }),
            _jsx('div', { className: 'wpb-progression-filters', role: 'group', 'aria-label': 'Filter lift progression',
                children: [['all', 'All lifts'], ['primary', 'Primary only'], ['upper', 'Upper'], ['lower', 'Lower']].map(([key, label]) =>
                    _jsx(UiChoice, { selected: filter === key, onClick: () => setFilter(key), className: 'wpb-progression-filter', children: label }, key)) }),
            _jsxs('div', { className: 'wpb-progression-pager', children: [
                _jsx('button', { type: 'button', className: 'pressable wpb-progression-page', 'aria-label': 'Earlier weeks',
                    disabled: start === 0, onClick: () => setFrom(Math.max(0, start - 1)), children: _jsx(ChevronLeft, { size: 18 }) }),
                _jsx('span', { className: 'wpb-progression-page-label', 'aria-live': 'polite', children: cols.length > 1 ? `Weeks ${cols[0]}–${cols.at(-1)} of ${totalWeeks}` : `Week ${cols[0]} of ${totalWeeks}` }),
                _jsx('button', { type: 'button', className: 'pressable wpb-progression-page', 'aria-label': 'Later weeks',
                    disabled: start + cols.length >= totalWeeks, onClick: () => setFrom(Math.min(totalWeeks - 2, start + 1)), children: _jsx(ChevronRight, { size: 18 }) })
            ] }),
            _jsxs('div', { className: 'wpb-progression-columns', 'aria-hidden': true, children: [
                _jsx('span', { children: 'Exercise' }), ...cols.map(week => _jsx('span', { children: week > weeks ? 'Deload' : `Wk ${week}` }, week))] }),
            !shown.length && _jsx('p', { className: 'wpb-progression-empty', children: 'No lift matches that.' }),
            ...shown.map(row => _jsxs('div', { className: 'wpb-progression-row', 'data-progression-row': `${row.day.id}:${row.slot}`, children: [
                _jsxs('div', { className: 'wpb-progression-lift', children: [
                    _jsxs('strong', { children: [row.ex?.name || row.alternate?.name || 'Exercise unavailable',
                        row.primary && _jsx(Star, { size: 12, 'aria-label': 'Key lift' })] }),
                    _jsx('span', { children: row.day.label })] }),
                ...columns.map(column => {
                    const projected = column.rows.get(`${row.day.id}:${row.slot}`);
                    const label = column.week > weeks ? 'Deload' : `Week ${column.week}`;
                    const unavailable = projected ? null : computeCell(program, row.day, exerciseAt(program, row.day, row.slot, column.week), row.slot, column.week);
                    return _jsxs('div', { className: 'wpb-progression-cell', 'data-progression-week': column.week,
                        'data-progression-exercise': projected?.id, 'data-deload': column.week > weeks,
                        children: [
                            _jsx('span', { className: 'wpb-progression-cell-week', children: label }),
                            projected ? [
                                _jsx('span', { className: 'wpb-progression-prescription mono', children: `${projected.cell.sets}×${projected.cell.reps}` }, 'prescription'),
                                _jsx('span', { className: 'wpb-progression-effort', children: effortLabel(projected.cell.rir) }, 'effort'),
                                _jsx('span', { className: 'wpb-progression-method', 'data-progression-method': projected.cell.progressionStyle,
                                    children: progressionLabel(projected.cell.progressionStyle) }, 'method'),
                                projected.id !== row.ex?.id && _jsx('span', { className: 'wpb-progression-variant', children: projected.ex.name }, 'variant')
                            ] : _jsx('span', { className: 'wpb-progression-unavailable', children: unavailable?.missing ? (unavailable.note || 'Prescription unavailable') : 'Not scheduled' })
                        ] }, column.week);
                })
            ] }, `${row.day.id}:${row.slot}`)),
            progressionOverview
        ] }) });
}
