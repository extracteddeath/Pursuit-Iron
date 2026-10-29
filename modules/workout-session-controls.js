// Shared Workout presentation. The session owns timer, prescription, logging,
// saved notes, exercise order and edits; these controls dispatch callbacks only.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useRef, useLayoutEffect } from 'react';
import { Check } from 'lucide-react';
import { EX_BY_ID, isWorkSet } from './engine-api.js';
import { workoutNavLabel } from './workout-nav-labels.js';

export function selectNumericInput(event) {
    const input = event.currentTarget;
    if (!input.readOnly && !input.disabled)
        input.select();
}

export function WorkoutExerciseNav({ data, exIdx, onSelect }) {
    const rail = useRef(null);
    useLayoutEffect(() => {
        const list = rail.current, active = list?.querySelector('[aria-current="step"]');
        if (!list || !active) return;
        // Selection changes and responsive reflow both need to reveal the
        // current exercise, without scrolling the surrounding workout page.
        const reveal = () => {
            if (!list.isConnected || !active.isConnected) return;
            const a = active.getBoundingClientRect(), r = list.getBoundingClientRect();
            if (a.left < r.left + 4) list.scrollLeft -= r.left + 4 - a.left;
            else if (a.right > r.right - 4) list.scrollLeft += a.right - r.right + 4;
        };
        reveal();
        if (typeof ResizeObserver !== 'function') {
            window.addEventListener('resize', reveal);
            return () => window.removeEventListener('resize', reveal);
        }
        const observer = new ResizeObserver(reveal);
        observer.observe(list);
        observer.observe(active);
        return () => observer.disconnect();
    }, [exIdx, data.length]);
    return _jsx("nav", { ref: rail, className: "wpb-hscroll wpb-ex-nav", "aria-label": "Workout exercises", children: data.map((row, i) => {
        const work = row.sets.filter(isWorkSet), done = work.filter(s => s.done).length;
        const complete = work.length > 0 && done === work.length, active = i === exIdx;
        const name = EX_BY_ID[row.id]?.name || "Exercise";
        return _jsxs("button", { type: "button", onClick: () => onSelect(i),
            className: "pressable hit wpb-ex-nav-step", "data-active": active ? "1" : "0", "data-complete": complete ? "1" : "0",
            "aria-current": active ? "step" : undefined,
            "aria-label": `${name} · exercise ${i + 1} of ${data.length} · ${done} of ${work.length} working sets completed`,
            title: name, children: [complete && _jsx(Check, { size: 14, "aria-hidden": true }),
                _jsx("span", { className: "wpb-ex-nav-name", children: workoutNavLabel(name) }),
                _jsxs("span", { className: "mono wpb-ex-nav-count", children: [done, "/", work.length] })] }, `${row.id}:${i}`);
    }) });
}


export function WorkingSetCountControl({ value, onChange, label, minimum = 1, disabled = false }) {
    // Keep partial input editable, but never let a stepper emit NaN,
    // fractions, or a value beyond the visible 1–20 set range.
    const parsed = Number(value);
    const count = Number.isFinite(parsed) && String(value).trim() !== ''
        ? Math.min(20, Math.max(minimum, Math.trunc(parsed))) : minimum;
    return _jsxs("div", { className: "wpb-working-set-control", children: [
        _jsx("span", { children: label }),
        _jsxs("span", { className: "wpb-working-set-stepper", children: [
            _jsx("button", { type: "button", disabled: disabled || count <= minimum, "aria-label": "Decrease " + label, onClick: () => onChange(Math.max(minimum, count - 1)), children: "−" }),
            _jsx("input", { type: "number", inputMode: "numeric", min: minimum, max: 20, step: 1, value, disabled, "aria-label": label, onFocus: selectNumericInput, onChange: ev => onChange(ev.target.value) }),
            _jsx("button", { type: "button", disabled: disabled || count >= 20, "aria-label": "Increase " + label, onClick: () => onChange(Math.min(20, count + 1)), children: "+" })] })] });
}
export function SessionNoteEditor({ label, value, repeat, onChange, onRepeat }) {
    return _jsxs("div", { className: "wpb-session-note-editor", children: [
        _jsxs("label", { children: [_jsx("strong", { children: label }), _jsx("textarea", { value, rows: 3, maxLength: 3000,
            placeholder: "What should you remember? Setup, how it felt, or what to try next time…", onChange: ev => onChange(ev.target.value) })] }),
        _jsxs("label", { children: ["Show this note", _jsx("select", { value: repeat, "aria-label": label + " note repetition", onChange: ev => onRepeat(ev.target.value),
            children: [['none','This session only'],['once','Repeat once'],['always','Keep repeating']].map(([value, text]) => _jsx("option", { value, children: text }, value)) })] })] });
}
