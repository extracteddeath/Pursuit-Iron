// Program Settings view only: staged draft, program transaction and persistence stay owned by ProgramView.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { Check } from 'lucide-react';
import { weeksOf, editableProgramWeeks, MAX_EDITABLE_PROGRAM_WEEKS, customProgramSettingsIssues } from './engine-api.js';
import { UiChoice, UiSheetClose } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';

const TARGET_OPTIONS = [
    ['auto', 'Auto', 'Periodized for you — sets & load ramp each week, with a deload'],
    ['manual', 'Manual', 'Set your own sets & reps on every exercise']
];
const APPROACH_OPTIONS = [
    ['standard', 'Standard volume', 'Typically 3–5 sets per exercise at 1–3 reps in reserve (up to 6 on main lifts in peak weeks)'],
    ['minimalist', 'Minimalist · high intensity', '2–3 hard sets near failure. Same exercises, less total volume']
];
const ASSISTANCE_OPTIONS = [
    ['triumvirate', 'Triumvirate', 'Main lift + 2 targeted accessories'],
    ['bbb', '5×10 Supplemental', 'Main lift + 5×10 supplemental @ 50%'],
    ['jack_shit', 'Jack Shit', 'Main lift only — nothing else'],
    [null, 'Full accessories', 'Main lift + a full slate of accessories']
];

// One presentation for detailed choices and constraints. Native button/selection semantics
// come from the same primitive used throughout the app. No settings or training state here.
function SettingsChoice({ label, detail, selected, onClick, ariaLabel }) {
    return _jsxs(UiChoice, {
        selected, onClick, 'aria-label': ariaLabel,
        className: 'wpb-program-settings-choice',
        children: [
            _jsx('span', { className: 'wpb-program-settings-indicator', 'aria-hidden': true,
                children: _jsx(Check, { size: 16 }) }),
            _jsxs('span', { className: 'wpb-program-settings-choice-copy', children: [
                _jsx('span', { className: 'wpb-program-settings-choice-label', children: label }),
                _jsx('span', { className: 'wpb-program-settings-choice-detail', children: detail })
            ] })
        ]
    });
}

export function ProgramSettingsSheet({ program, draft: cfgDraft, setDraft: setCfgDraft, dirty: cfgDirty, onApply: applyCfgDraft, onClose, GOALS, sheetDragRef, selectNumericInput }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose, sheetDragRef);
    const cycleLengthOwned = program.engineSource === "pursuit-next" && !!program.cycleId;
    const draftWeeks = cfgDraft?.weeks ?? weeksOf(program);
    const editableWeeks = editableProgramWeeks(draftWeeks);
    const retainedDuration = /^\d+$/.test(String(draftWeeks)) && Number(draftWeeks) === weeksOf(program) && Number.isSafeInteger(Number(draftWeeks)) && Number(draftWeeks) > 0;
    const validWeeks = editableWeeks != null || retainedDuration;
    const settingsIssues = customProgramSettingsIssues(program, cfgDraft || {}).filter(issue => issue.code !== 'weeks');
    const canApply = cfgDirty && (cycleLengthOwned || validWeeks) && settingsIssues.length === 0;
    // Keep incomplete text in the staged draft; typing a replacement must never save a blank as zero.
    const editWeeks = value => {
        const n = Number(value);
        const weeks = editableProgramWeeks(value) != null ? n : value;
        setCfgDraft(d => ({ ...d, weeks }));
    };
    const stepWeeks = delta => {
        if (editableWeeks != null)
            setCfgDraft(d => ({ ...d, weeks: Math.max(1, Math.min(MAX_EDITABLE_PROGRAM_WEEKS, Number(d?.weeks ?? weeksOf(program)) + delta)) }));
    };
    const choices = (field, options, value, ariaPrefix) => options.map(([key, label, detail]) =>
        _jsx(SettingsChoice, { label, detail, selected: value === key,
            ariaLabel: ariaPrefix ? `${ariaPrefix}: ${label}` : undefined,
            onClick: () => setCfgDraft(d => ({ ...d, [field]: key })) }, label));
    const group = (title, children, grid = false) => _jsxs('fieldset', {
        className: 'wpb-program-settings-group', children: [
            _jsx('legend', { className: 'wpb-program-settings-field-label', children: title }),
            _jsx('div', { className: grid ? 'wpb-program-settings-choice-grid' : 'wpb-program-settings-choice-list', children })
        ]
    }, title);
    const durationControl = cycleLengthOwned
        ? _jsxs("div", { "data-testid": "cycle-phase-duration", className: "wpb-program-settings-cycle-length", children: [
            _jsxs("div", { className: "wpb-program-settings-cycle-length-value", children: [weeksOf(program), " weeks"] }),
            _jsx("div", { className: "wpb-program-settings-cycle-length-help", children: "Set by this phase of the training cycle." })] })
        : _jsxs("div", { className: "wpb-program-settings-length", children: [
            _jsxs("div", { "data-testid": "program-duration-control", "data-invalid": !validWeeks, className: "wpb-program-settings-length-control", children: [
                _jsx("button", { type: "button", "aria-label": "Decrease block length", disabled: editableWeeks == null || Number(draftWeeks) <= 1, onClick: () => stepWeeks(-1), className: "pressable wpb-program-settings-length-step", children: "−" }),
                _jsxs("label", { className: "wpb-program-settings-length-label", children: [
                    _jsx("input", { type: "text", inputMode: "numeric", onFocus: selectNumericInput, onClick: selectNumericInput, enterKeyHint: "done", "aria-label": "Block length in weeks", "aria-invalid": !validWeeks, "aria-describedby": !validWeeks ? "program-weeks-error" : undefined, value: draftWeeks, onChange: e => editWeeks(e.target.value), onKeyDown: e => {
                        if (validWeeks && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
                            e.preventDefault(); stepWeeks(e.key === "ArrowUp" ? 1 : -1);
                        }
                        if (e.key === "Enter") e.currentTarget.blur();
                    }, className: "mono wpb-num-input wpb-program-settings-length-input" }), "weeks"] }),
                _jsx("button", { type: "button", "aria-label": "Increase block length", disabled: editableWeeks == null || Number(draftWeeks) >= MAX_EDITABLE_PROGRAM_WEEKS, onClick: () => stepWeeks(1), className: "pressable wpb-program-settings-length-step", children: "+" })] }),
            !validWeeks && _jsx("div", { id: "program-weeks-error", role: "alert", className: "wpb-program-settings-length-error", children: "Enter a whole number of weeks from 1 to 52." })] });

    return _jsx("div", {
        "data-testid": "program-settings-sheet", role: "dialog", "aria-modal": true,
        "aria-labelledby": "program-settings-title", onClick: event => { if (event.target === event.currentTarget) onClose(); },
        className: "wpb-backdrop wpb-ui-sheet-backdrop wpb-ui-sheet-focus wpb-program-settings-backdrop",
        children: _jsxs("div", {
            ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown, onClick: e => e.stopPropagation(), className: "wpb-ui-sheet-panel wpb-program-settings-panel",
            children: [
                _jsxs("div", { className: "wpb-ui-sheet-heading wpb-program-settings-heading", children: [
                    _jsx("h2", { id: "program-settings-title", className: "wpb-ui-sheet-title wpb-program-settings-title", children: "Program settings" }),
                    _jsx(UiSheetClose, { className: "wpb-program-settings-close", onClick: onClose })] }),
                _jsxs('div', { className: 'wpb-scroll wpb-program-settings-body wpb-ui-sheet-scroll', children: [
                    _jsx("p", { className: "wpb-ui-sheet-copy wpb-program-settings-intro", children: program.engineSource === "pursuit-next"
                        ? "Adjust your program, then save to rebuild it with the latest program logic. Your current program stays unchanged if the rebuild cannot be completed safely."
                        : program.custom ? "Update your program settings. Swap or remove any exercises that conflict with a new constraint before saving." : "Adjust your program, then save to apply. Changes re-balance your current exercises." }),
                    settingsIssues.length > 0 && _jsxs('section', {role:'alert',id:'program-settings-issues',className:'wpb-program-settings-issues',children:[
                        _jsx('h3',{children:'Settings need attention'}),
                        ...settingsIssues.map((issue,index)=>_jsx('p',{children:issue.message},`${issue.code}:${issue.dayId || ''}:${index}`))
                    ]}),
                    !program.config?.percentScheme && [
                        group('Training goal', ['strength', 'both', 'hypertrophy'].map(k => _jsx(UiChoice, {
                            selected: cfgDraft?.goal === k, className: 'wpb-program-settings-chip',
                            onClick: () => setCfgDraft(d => ({ ...d, goal: k })),
                            children: k === 'both' ? 'Both' : GOALS[k].label
                        }, k)), true),
                        group('Block length', durationControl),
                        group('Set & rep targets', choices('progression', TARGET_OPTIONS, cfgDraft?.progression)),
                        group('Training approach', choices('volumeApproach', APPROACH_OPTIONS, cfgDraft?.volumeApproach || 'standard'))
                    ],
                    _jsxs("label", { htmlFor: "program-settings-folder", className: "wpb-program-settings-field-label", children: [
                        "Folder ", _jsx("span", { className: 'wpb-program-settings-optional', children: "· optional" })] }),
                    _jsx("input", { id: "program-settings-folder", className: "wpb-program-settings-field", value: cfgDraft?.folder || "", onChange: e => setCfgDraft(d => ({ ...d, folder: e.target.value })), placeholder: "Group routines, e.g. Strength block" }),
                    _jsxs("label", { htmlFor: "program-settings-description", className: "wpb-program-settings-field-label", children: [
                        "Description ", _jsx("span", { className: 'wpb-program-settings-optional', children: "· optional" })] }),
                    _jsx("textarea", { id: "program-settings-description", className: "wpb-program-settings-field wpb-program-settings-description", value: cfgDraft?.description || "", onChange: e => setCfgDraft(d => ({ ...d, description: e.target.value })), rows: 3, placeholder: "What this routine is for, how to progress, deload notes…" }),
                    program.config?.percentScheme && (program.engineSource === "pursuit-next"
                        ? _jsxs("section", { className: 'wpb-program-settings-guidance', children: [
                            _jsx('h3', { className: 'wpb-program-settings-field-label', children: 'Assistance work' }),
                            "Assistance is managed automatically for this program, so changing an older assistance preset cannot silently replace the plan's structure."] })
                        : group('Assistance work', choices('assistance', ASSISTANCE_OPTIONS, cfgDraft?.assistance ?? null, 'Assistance'))),
                    group('Barbell movements per session', [[null, 'No limit'], [1, '1'], [2, '2'], [3, '3']].map(([v, label]) => _jsx(UiChoice, {
                        selected: (cfgDraft?.barbellCap ?? null) === v, className: 'wpb-program-settings-chip',
                        onClick: () => setCfgDraft(d => ({ ...d, barbellCap: v })), children: label
                    }, label)), true),
                    _jsxs('fieldset', { className: 'wpb-program-settings-group', children: [
                        _jsx('legend', { className: 'wpb-program-settings-field-label', children: 'Exercise constraints' }),
                        _jsxs('div', { className: 'wpb-program-settings-choice-list', children: [
                            _jsx(SettingsChoice, { label: 'Skip bodyweight exercises', detail: 'Replace any bodyweight movements with loaded ones',
                                selected: !!cfgDraft?.noBodyweight, onClick: () => setCfgDraft(d => ({ ...d, noBodyweight: !d.noBodyweight })) }),
                            _jsx(SettingsChoice, { label: 'No supersets', detail: 'Budget the workout as straight sets only',
                                selected: !!cfgDraft?.noSupersets, onClick: () => setCfgDraft(d => ({ ...d, noSupersets: !d.noSupersets })) })] }),
                        _jsx('p', { className: 'wpb-program-settings-help', children: 'These choices are treated as firm program constraints. If a requested combination cannot be built safely, the rebuild is rejected instead of silently ignoring the setting.' })] })
                ] }),
                _jsxs("div", { className: "wpb-ui-sheet-footer wpb-ui-sheet-footer-scroll wpb-program-settings-footer", children: [
                    _jsx("button", { type: "button", onClick: onClose, className: "pressable wpb-program-settings-cancel", children: "Cancel" }),
                    _jsx("button", { type: "button", "aria-label": "Save program settings",
                        "aria-describedby": settingsIssues.length ? "program-settings-issues"
                            : (!validWeeks && !cycleLengthOwned ? "program-weeks-error" : undefined),
                        onClick: applyCfgDraft, disabled: !canApply, className: "pressable wpb-program-settings-save", children: cfgDirty
                        ? _jsxs('span', { children: [_jsx(Check, { size: 17, 'aria-hidden': true }), ' Save changes'] })
                        : "No changes" })] })
            ]
        })
    });
}
