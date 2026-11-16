// Local text drafts only. The canonical domain validates and applies authored targets.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { canonicalRepRange, parseManualTargets } from './engine-api.js';
import { UiAction } from './ui-primitives.js';

export function ProgramManualTargets({ editorKey, name, cell, rawReps, effort, variedSets, registerPending, onApply }) {
    const pair = canonicalRepRange(cell.reps);
    const initialSets = String(cell.sets);
    const initialReps = pair ? (pair[0] === pair[1] ? String(pair[0]) : `${pair[0]}-${pair[1]}`) : String(rawReps ?? '');
    const [draft, setDraft] = useState(() => ({ sets: initialSets, reps: initialReps }));
    const [formError, setFormError] = useState('');
    const root = useRef(null);
    useEffect(() => { setDraft({ sets: initialSets, reps: initialReps }); setFormError(''); }, [initialSets, initialReps]);
    const dirty = draft.sets !== initialSets || draft.reps !== initialReps;
    const parsed = parseManualTargets(draft);
    useLayoutEffect(() => {
        if (!dirty) return;
        return registerPending(editorKey, () => {
            const input = root.current?.querySelector('[aria-invalid="true"]') || root.current?.querySelector('input');
            input?.focus(); input?.scrollIntoView({ block: 'center', behavior: 'instant' });
        });
    }, [dirty, editorKey, registerPending]);
    const errorId = `manual-target-${editorKey.replace(/[^\w-]/g, '-')}`;
    const error = dirty || cell.prescriptionInvalid ? parsed.errors : {};
    const change = (field, value) => { setDraft(previous => ({ ...previous, [field]: value })); setFormError(''); };
    const apply = event => {
        event.preventDefault();
        if (!dirty || !parsed.valid) return;
        const result = onApply(draft);
        if (result?.errors?.form) { setFormError(result.errors.form); return; }
        setDraft({ sets: String(parsed.values.sets), reps: parsed.values.reps });
    };
    return _jsxs('form', { ref: root, className: 'wpb-manual-targets', 'data-dirty': dirty, 'data-testid': 'manual-targets',
        'aria-label': `Targets for ${name}`, onSubmit: apply, children: [
            _jsxs('div', { className: 'wpb-manual-target-fields', children: [
                _jsxs('label', { className: 'wpb-manual-target-field', children: [
                    _jsx('span', { children: 'Sets' }), _jsx('input', { className: 'mono wpb-ui-control', type: 'text', inputMode: 'numeric',
                        'aria-label': `Sets for ${name}`, 'aria-invalid': !!error.sets, 'aria-describedby': error.sets ? `${errorId}-sets` : undefined,
                        value: draft.sets, onChange: event => change('sets', event.target.value) })] }),
                _jsxs('label', { className: 'wpb-manual-target-field', children: [
                    _jsx('span', { children: 'Reps' }), _jsx('input', { className: 'mono wpb-ui-control', type: 'text', inputMode: 'text',
                        'aria-label': `Reps for ${name}`, 'aria-invalid': !!error.reps, 'aria-describedby': error.reps ? `${errorId}-reps` : undefined,
                        value: draft.reps, onChange: event => change('reps', event.target.value) })] }),
                _jsx('span', { className: 'wpb-manual-target-effort', children: `~${effort}` })] }),
            error.sets && _jsx('p', { id: `${errorId}-sets`, className: 'wpb-manual-target-error', children: error.sets }),
            error.reps && _jsx('p', { id: `${errorId}-reps`, className: 'wpb-manual-target-error', children: error.reps }),
            formError && _jsx('p', { role: 'alert', className: 'wpb-manual-target-error', children: formError }),
            dirty && _jsxs('div', { className: 'wpb-manual-target-draft', children: [
                _jsx('p', { className: 'wpb-manual-target-help', children: variedSets
                    ? 'Sets vary by week. Changed fields apply to every week; untouched fields keep their schedule.'
                    : 'Changed fields apply to every week. Review and save to update your saved program.' }),
                _jsxs('div', { className: 'wpb-manual-target-actions', children: [
                    _jsx(UiAction, { type: 'submit', variant: 'primary', disabled: !parsed.valid, 'aria-label': `Apply targets for ${name}`, children: 'Apply' }),
                    _jsx(UiAction, { variant: 'tonal', 'aria-label': `Cancel targets for ${name}`, onClick: () => {
                        setDraft({ sets: initialSets, reps: initialReps }); setFormError('');
                    }, children: 'Cancel' })] })] })
        ] });
}
