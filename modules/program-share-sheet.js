// Program sharing reads the same selected-week cells as Preview and Workout.
// Transient clipboard feedback belongs here; App retains program and navigation state.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo, useRef, useState } from 'react';
import { Check, Copy, Layers } from 'lucide-react';
import { UiAction, UiSheetClose, UiSheetHeading } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';
import { SPLITS, weeksOf, daysInWeek, sessionPrescriptionRows, effortLabel,
    percentagePlanFor, gallerySubmission, galleryIssueBody } from './engine-api.js';

export function programToText(program, loadMode = 'rir', unit = 'kg', weekIndex = 1, history = []) {
    const cfg = program.config || {}, weeks = weeksOf(program);
    const days = daysInWeek(program, weekIndex);
    const lines = [program.name || 'My Program',
        `${SPLITS[cfg.split]?.name || cfg.split || 'Custom'} · ${days.length} ${days.length === 1 ? 'day' : 'days'}/week${cfg.goal ? ' · ' + cfg.goal : ''} · ${weeks}-week block`,
        `${weekIndex > weeks ? `Deload after ${weeks} training weeks` : `Week ${weekIndex} of ${weeks}`} — saved plan snapshot`];
    if (program.description?.trim()) lines.push(program.description.trim());
    lines.push('');
    for (const day of days) {
        lines.push(day.label || 'Day');
        if (day.note) lines.push(`  — ${day.note}`);
        const rows = sessionPrescriptionRows(program, day, weekIndex);
        if (!rows.length) lines.push('  No executable exercises in this week.');
        for (const { ex, slot, cell } of rows) {
            const grouped = !cfg.noSupersets && (program.ss?.[`${day.id}:${slot}`]
                || (slot > 0 && program.ss?.[`${day.id}:${slot - 1}`]));
            const effort = cell.rir != null ? ` @ ${effortLabel(cell.rir, loadMode)}` : '';
            lines.push(`${grouped ? '   ↳' : `  ${slot + 1}.`} ${ex.name} — ${cell.sets} × ${cell.range}${effort}`);
            const percentage = percentagePlanFor(program, day, ex, slot, weekIndex, unit, history);
            if (percentage?.sets?.length) lines.push('       Planned loads: ' + percentage.sets.map(set =>
                `${set.weight} ${unit} × ${set.reps}`).join(' · '));
        }
        lines.push('');
    }
    lines.push('Made with Pursuit Iron — Strength Coach');
    return lines.join('\n');
}

export function ShareProgramSheet({ program, shareCode, weekIndex = 1, loadMode, unit, history = [], onClose, sheetDragRef }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose, sheetDragRef);
    const text = useMemo(() => programToText(program, loadMode, unit, weekIndex, history),
        [program, loadMode, unit, weekIndex, history]);
    const [feedback, setFeedback] = useState(null);
    const busy = useRef(false);
    const copy = async (value, kind) => {
        if (busy.current) return;
        busy.current = true;
        setFeedback({ kind, state: 'pending' });
        try {
            // A denied asynchronous permission must never produce a success flag.
            await navigator.clipboard.writeText(value);
            setFeedback({ kind, state: 'copied' });
        } catch {
            setFeedback({ kind, state: 'error' });
        } finally {
            busy.current = false;
        }
    };
    const submit = () => {
        const sub = gallerySubmission(program);
        if (!sub.ok) return;
        let slug = 'extracteddeath/pursuit-iron';
        const owner = location.hostname.endsWith('.github.io') ? location.hostname.split('.')[0] : null;
        const segment = location.pathname.split('/').filter(Boolean)[0];
        if (owner && segment) slug = `${owner}/${segment}`;
        const url = `https://github.com/${slug}/issues/new?labels=gallery-submission&title=${encodeURIComponent('Gallery: ' + (program.name || 'program'))}&body=${encodeURIComponent(galleryIssueBody(sub))}`;
        window.open(url, '_blank', 'noopener');
    };
    const pending = feedback?.state === 'pending';
    return _jsx('div', { 'data-testid': 'share-program-sheet', role: 'dialog', 'aria-modal': true,
        'aria-labelledby': 'program-share-title', 'aria-describedby': 'program-share-scope', onClick: event => { if (event.target === event.currentTarget) onClose(); },
        className: 'wpb-backdrop wpb-ui-sheet-backdrop wpb-ui-sheet-focus wpb-program-share-backdrop', children:
        _jsxs('section', { ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown, className: 'wpb-ui-sheet-panel wpb-program-share-panel', onClick: event => event.stopPropagation(), children: [
            _jsxs(UiSheetHeading, { as: 'header', className: 'wpb-ui-sheet-heading wpb-program-share-heading', children: [
                _jsx('h2', { id: 'program-share-title', className: 'wpb-ui-sheet-title', children: 'Share program' }),
                _jsx(UiSheetClose, { className: 'wpb-program-share-close', onClick: onClose })
            ] }),
            _jsxs('div', { className: 'wpb-scroll wpb-program-share-body wpb-ui-sheet-scroll', children: [
                _jsx('p', { id: 'program-share-scope', children: `${weekIndex > weeksOf(program) ? 'Deload' : 'Week ' + weekIndex + ' of ' + weeksOf(program)} · Saved sets, reps and target effort.` }),
                shareCode && _jsxs('div', { className: 'wpb-program-share-code', children: [
                    _jsx('label', { htmlFor: 'program-share-code', children: 'Program code' }),
                    _jsx('textarea', { id: 'program-share-code', readOnly: true, value: shareCode, rows: 2,
                        className: 'mono wpb-program-share-text', onFocus: event => event.target.select() }),
                    _jsxs(UiAction, { disabled: pending, onClick: () => copy(shareCode, 'code'), 'aria-label': 'Copy program code',
                        children: [_jsx(feedback?.kind === 'code' && feedback.state === 'copied' ? Check : Copy, { size: 16 }),
                            feedback?.kind === 'code' && feedback.state === 'copied' ? 'Code copied' : 'Copy program code'] }),
                    _jsx('p', { children: 'Paste the code into Settings → Add a shared program to recreate the supported program.' }),
                    _jsxs(UiAction, { onClick: submit, children: [_jsx(Layers, { size: 16 }), 'Submit to the gallery'] }),
                    _jsx('p', { children: 'Opens a submission draft on GitHub for maintainer review.' })
                ] }),
                _jsx('label', { htmlFor: 'program-share-text', className: 'wpb-program-share-label', children: 'Plain-text plan' }),
                _jsx('textarea', { id: 'program-share-text', readOnly: true, value: text,
                    onFocus: event => event.target.select(), className: 'wpb-scroll mono wpb-program-share-text' })
            ] }),
            _jsxs('footer', { className: 'wpb-ui-sheet-footer wpb-ui-sheet-footer-scroll wpb-program-share-footer', children: [
                _jsx('p', { role: 'status', className: 'wpb-program-share-status', children: feedback?.state === 'pending'
                    ? 'Copying…' : feedback?.state === 'error'
                    ? 'Could not copy. Select the text above and copy it manually.'
                    : feedback?.state === 'copied' ? (feedback.kind === 'code' ? 'Program code copied.' : 'Plain-text plan copied.') : '' }),
                _jsxs(UiAction, { variant: 'primary', disabled: pending, 'aria-label': 'Copy plain text', onClick: () => copy(text, 'text'),
                    className: 'wpb-program-share-copy', children: [
                        _jsx(feedback?.kind === 'text' && feedback.state === 'copied' ? Check : Copy, { size: 16 }),
                        feedback?.kind === 'text' && feedback.state === 'copied' ? 'Copied ✓' : pending ? 'Copying…' : 'Copy plain text'
                    ] })
            ] })
        ] }) });
}
