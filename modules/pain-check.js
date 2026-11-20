// Mid-workout pain check, ported from the M3 prototype: where → how bad → what changes today.
// The severity picks the action (twinge = note only, sore = half the remaining sets, sharp = skip
// the remaining sets); the workout owner applies it through adjustInjuredExercises with an undo.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo, useState } from 'react';
import { UiAction, UiChoice, UiSheetClose, UiSheetHeading } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';
import { INJURY_AREAS, INJURY_LEVELS, injuryAreaForPart, injuryMatchesExercise, injuryPlan } from './injury-tools.js';

export function painAction(level) {
    return INJURY_LEVELS.find(item => item.id === level)?.impact || 'monitor';
}

/** Session rows a region touches, with names and logged counts; `lookup(id)` → { ex, secondary }. */
export function painMatches(region, rows, lookup) {
    if (!region) return [];
    return (rows || []).flatMap(row => {
        const found = lookup(row.id);
        if (!found?.ex || !injuryMatchesExercise({ region }, found.ex, found.secondary || [])) return [];
        const work = (row.sets || []).filter(set => !set.warm && !set.sub);
        return [{ id: row.id, name: found.ex.name, logged: work.filter(set => set.done).length, pending: work.filter(set => !set.done).length }];
    });
}

const STEPS = ['Where does it hurt?', 'How bad is it?', 'Today’s plan'];

export function PainCheckSheet({ activePart, rows, lookup, onApply, onClose, onDetailed }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose);
    const [step, setStep] = useState(0);
    const [region, setRegion] = useState(() => injuryAreaForPart(activePart) || '');
    const [level, setLevel] = useState('');
    const matches = useMemo(() => painMatches(region, rows, lookup), [region, rows, lookup]);
    const area = INJURY_AREAS.find(item => item.id === region);
    const action = painAction(level);
    const plan = action === 'monitor'
        ? [matches.length ? `Nothing changes now. ${matches.map(m => m.name).join(', ')} ${matches.length === 1 ? 'is' : 'are'} flagged so you can stop if it returns.` : 'Nothing changes now. It is noted with this workout.']
        : injuryPlan(level, matches.filter(m => m.pending).map(m => m.name), area?.label).map(line => line.replace(/^If applied: /, ''));
    const ready = step === 0 ? !!region : step === 1 ? !!level : true;
    const next = () => {
        if (!ready) return;
        if (step < 2) return setStep(step + 1);
        onApply({ region, part: area?.parts[0] || activePart, level, action, ids: matches.map(m => m.id) });
    };
    const groups = [...new Set(INJURY_AREAS.map(item => item.group))];
    return _jsx('div', { role: 'dialog', 'aria-modal': true, 'aria-labelledby': 'pain-check-title', 'data-pain-check': true,
        onClick: event => { if (event.target === event.currentTarget) onClose(); },
        className: 'wpb-backdrop wpb-ui-sheet-backdrop wpb-ui-sheet-focus wpb-pain-backdrop', children:
        _jsxs('section', { ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown, className: 'wpb-ui-sheet-panel wpb-pain-panel', onClick: event => event.stopPropagation(), children: [
            _jsxs(UiSheetHeading, { as: 'header', className: 'wpb-pain-heading', children: [
                _jsxs('div', { className: 'wpb-pain-heading-copy', children: [
                    _jsx('p', { className: 'wpb-pain-step', children: `Pain check · ${step + 1} of 3` }),
                    _jsx('h2', { id: 'pain-check-title', className: 'wpb-ui-sheet-title', children: STEPS[step] })
                ] }),
                _jsx(UiSheetClose, { onClick: onClose, 'aria-label': 'Close pain check' })
            ] }),
            _jsxs('div', { className: 'wpb-scroll wpb-ui-sheet-scroll wpb-pain-body', children: [
                step === 0 && groups.map(group => _jsxs('div', { role: 'group', 'aria-label': group, className: 'wpb-pain-group', children: [
                    _jsx('p', { className: 'wpb-pain-group-label', children: group }),
                    _jsx('div', { className: 'wpb-pain-chips', children: INJURY_AREAS.filter(item => item.group === group).map(item =>
                        _jsx(UiChoice, { selected: region === item.id, onClick: () => setRegion(item.id), children: item.label }, item.id)) })
                ] }, group)),
                step === 1 && _jsx('div', { role: 'group', 'aria-label': 'Severity', className: 'wpb-pain-levels', children: INJURY_LEVELS.map(item =>
                    _jsxs(UiChoice, { selected: level === item.id, onClick: () => setLevel(item.id), 'data-level': item.id, className: 'wpb-pain-level', children: [
                        _jsx('span', { className: 'wpb-pain-level-label', children: item.label }),
                        _jsx('span', { className: 'wpb-pain-level-detail', children: item.detail })
                    ] }, item.id)) }),
                step === 2 && _jsxs('div', { className: 'wpb-pain-plan', 'data-action': action, children: [
                    _jsx('p', { className: 'wpb-pain-plan-head', children: `${area?.label || 'Area'} · ${INJURY_LEVELS.find(item => item.id === level)?.label}` }),
                    plan.map(line => _jsx('p', { children: line }, line)),
                    matches.length > 0 && _jsx('ul', { className: 'wpb-pain-plan-list', children: matches.map(m => _jsxs('li', { children: [
                        _jsx('span', { children: m.name }), _jsx('span', { className: 'mono', children: `${m.logged} logged · ${m.pending} left` })] }, m.id)) }),
                    _jsx('p', { className: 'wpb-pain-plan-note', children: 'Only today changes. Logged sets stay, and Undo restores the workout. Stop any movement that keeps hurting.' })
                ] }),
                step === 2 && onDetailed && _jsx(UiAction, { variant: 'text', className: 'wpb-pain-detailed', onClick: onDetailed, children: 'Choose the exact lifts instead' })
            ] }),
            _jsxs('footer', { className: 'wpb-ui-sheet-footer wpb-pain-footer', children: [
                _jsx(UiAction, { onClick: () => step === 0 ? onClose() : setStep(step - 1), children: step === 0 ? 'Cancel' : 'Back' }),
                _jsx(UiAction, { variant: 'primary', disabled: !ready, onClick: next, className: 'wpb-pain-next',
                    children: step < 2 ? 'Next' : action === 'monitor' ? 'Note it' : action === 'skip' ? 'Skip those sets' : 'Halve those sets' })
            ] })
        ] }) });
}
