// Training-max presentation and transient text only. ProgramView owns applying the draft.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useRef, useState } from 'react';
import { Dumbbell, ChevronRight, X } from 'lucide-react';
import { EX_BY_ID, PCT_SCHEMES, trainingMaxForUnit, withTrainingMax, percentagePlanFor, roundTo, loadStep } from './engine-api.js';
import { UiAction } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';

const numberValue = text => {
    const value = String(text).trim();
    if (!value) return 0;
    if (!/^(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)) return null;
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? number : null;
};

export function TrainingMaxSummary({ scheme, count, total, onOpen }) {
    const metadata = PCT_SCHEMES[scheme];
    return _jsxs('button', { type: 'button', className: 'pressable wpb-training-max-summary',
        'data-incomplete': count < total, 'aria-label': 'Edit training maxes', onClick: onOpen, children: [
            _jsx(Dumbbell, { size: 18, 'aria-hidden': true }),
            _jsxs('span', { className: 'wpb-training-max-summary-copy', children: [
                _jsxs('span', { className: 'wpb-training-max-summary-title', children: ['Training maxes · ', metadata?.name] }),
                _jsx('span', { className: 'wpb-training-max-summary-detail', children: count < total
                    ? `Set your ${metadata?.basis} for each main lift — ${count}/${total} done`
                    : `${count} main lift${count === 1 ? '' : 's'} set · tap to edit` })
            ] }), _jsx(ChevronRight, { size: 17, 'aria-hidden': true })
        ] });
}

export function ProgramTrainingMaxes({ program, mainLifts, unit, onApply, onClose, sheetDragRef, selectNumericInput }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose, sheetDragRef);
    const [draft, setDraft] = useState(() => Object.fromEntries(mainLifts.map(id => [id,
        String(trainingMaxForUnit(program, id, unit) || '')])));
    const [conversion, setConversion] = useState(null);
    const conversionOpener = useRef(null);
    const changes = {};
    let valid = true;
    for (const id of mainLifts) {
        const value = numberValue(draft[id]);
        if (value == null) valid = false;
        else if (value !== (trainingMaxForUnit(program, id, unit) || 0)) changes[id] = value;
    }
    const preview = Object.entries(changes).reduce((current, [id, value]) => withTrainingMax(current, id, value, unit), program);
    const canApply = !conversion && valid && Object.keys(changes).length > 0;
    const scheme = PCT_SCHEMES[program.config?.percentScheme];
    const closeConversion = () => { setConversion(null); conversionOpener.current?.focus({ preventScroll: true }); };
    const apply = () => { if (canApply) onApply(changes); };
    return _jsx('div', { className: 'wpb-backdrop wpb-training-max-backdrop', role: 'dialog', 'aria-modal': true,
        'aria-labelledby': 'program-training-max-title', onClick: event => { if (event.target === event.currentTarget) onClose(); },
        children: _jsxs('section', { ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown, className: 'wpb-training-max-sheet',
            onClick: event => event.stopPropagation(), children: [
                _jsxs('header', { className: 'wpb-training-max-heading', children: [
                    _jsx('h2', { id: 'program-training-max-title', children: 'Training maxes' }),
                    _jsx(UiAction, { variant: 'tonal', 'data-icon-only': true, 'data-wpb-system-back': !conversion || undefined,
                        'aria-label': 'Close', className: 'hit wpb-sheet-close', onClick: onClose, children: _jsx(X, { size: 18 }) })
                ] }),
                _jsxs('div', { className: 'wpb-scroll wpb-training-max-body', children: [
                    _jsx('p', { className: 'wpb-training-max-help', children: `${scheme?.name || 'Percentage training'} loads each main lift from its ${scheme?.basis || 'training max'}. Apply your maxes here, then review and save the program to keep the changes.` }),
                    _jsx('p', { className: 'wpb-training-max-help', children: 'Use 1RM calculates 90% of a recent one-rep max, rounded to the lift’s loading step. Leave a max blank to clear it.' }),
                    mainLifts.map(id => {
                        const ex = EX_BY_ID[id];
                        if (!ex) return null;
                        const value = numberValue(draft[id]);
                        const inputId = `training-max-${id}`;
                        const ownerDay = program.days.find(day => day.exercises.includes(id));
                        const week = value > 0 && ownerDay ? percentagePlanFor(preview, ownerDay, ex, ownerDay.exercises.indexOf(id), 1, unit) : null;
                        const source = conversion?.id === id ? numberValue(conversion.value) : 0;
                        const calculated = source > 0 ? roundTo(source * .9, loadStep(ex, unit)) : 0;
                        const canConvert = Number.isFinite(calculated) && calculated > 0;
                        const useConversion = () => { if (!canConvert) return; setDraft(previous => ({ ...previous, [id]: String(calculated) })); closeConversion(); };
                        return _jsxs('div', { className: 'wpb-training-max-row', 'data-training-max': id, children: [
                            _jsx('label', { className: 'wpb-training-max-label', htmlFor: inputId, children: ex.name }),
                            _jsxs('div', { className: 'wpb-training-max-controls', children: [
                                _jsx('input', { id: inputId, className: 'mono wpb-training-max-input', inputMode: 'decimal',
                                    'aria-label': `Training max for ${ex.name} (${unit})`, 'aria-invalid': value == null || undefined,
                                    'aria-describedby': value == null ? `${inputId}-error` : undefined, value: draft[id],
                                    placeholder: `Training max (${unit})`, onFocus: selectNumericInput, onClick: selectNumericInput,
                                    onChange: event => setDraft(previous => ({ ...previous, [id]: event.target.value })) }),
                                _jsx(UiAction, { variant: 'tonal', 'data-1rm-for': id, 'aria-label': `Use 1RM for ${ex.name}`,
                                    onClick: event => { conversionOpener.current = event.currentTarget; setConversion({ id, value: '' }); }, children: 'Use 1RM' })
                            ] }),
                            value == null && _jsx('p', { id: `${inputId}-error`, className: 'wpb-training-max-error', role: 'status', children: 'Use a nonnegative number with one decimal point, or leave blank to clear.' }),
                            week && _jsxs('p', { className: 'mono wpb-training-max-preview', children: [
                                'Preview · Week 1 (', week.label, '): ', week.sets.map(set => `${set.weight} ${unit} × ${set.reps}`).join(' · ')
                            ] }),
                            conversion?.id === id && _jsxs('fieldset', { className: 'wpb-training-max-conversion', children: [
                                _jsx('legend', { children: `Set ${ex.name} from 1RM` }),
                                _jsx('label', { htmlFor: `${inputId}-1rm`, className: 'wpb-training-max-label', children: `Recent one-rep max (${unit})` }),
                                _jsx('input', { id: `${inputId}-1rm`, key: id, autoFocus: true, className: 'mono wpb-training-max-input',
                                    inputMode: 'decimal', 'aria-label': `One-rep max for ${ex.name} (${unit})`,
                                    value: conversion.value, onFocus: selectNumericInput, onClick: selectNumericInput,
                                    onChange: event => setConversion({ id, value: event.target.value }),
                                    onKeyDown: event => { if (event.key === 'Enter') { event.preventDefault(); useConversion(); } } }),
                                _jsx('p', { className: 'mono wpb-training-max-preview', children: canConvert
                                    ? `90% → training max ${calculated} ${unit}` : 'Enter a positive one-rep max that rounds to a positive training max.' }),
                                _jsxs('div', { className: 'wpb-training-max-conversion-actions', children: [
                                    _jsx(UiAction, { variant: 'tonal', disabled: !canConvert, onClick: useConversion, children: 'Use this max' }),
                                    _jsx(UiAction, { variant: 'tonal', 'data-wpb-system-back': true, onClick: closeConversion, children: 'Cancel conversion' })
                                ] })
                            ] })
                        ] }, id);
                    })
                ] }),
                _jsxs('footer', { className: 'wpb-training-max-footer', children: [
                    _jsx(UiAction, { variant: 'tonal', onClick: onClose, children: 'Cancel' }),
                    _jsx(UiAction, { variant: 'primary', disabled: !canApply, onClick: apply, children: 'Apply maxes' })
                ] })
            ] }) });
}
