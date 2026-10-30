// Program revision/restore/save presentation. Program state and persistence remain in App; training calculations remain in the canonical domain API.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo } from 'react';
import { UiSheetClose } from './ui-primitives.js';
import { fmtSets, programChangeLabels } from './engine-api.js';
import { reviewProgramChanges } from './program-review.js';
import { SessionChoiceDialog } from './session-choice-dialog.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';

function ProgramChangeDetails({ changes, emptyText }) {
    return _jsxs('div', { className: 'wpb-program-change-details', children: [
            changes.config.map((change, index) => _jsxs("div", { className: "wpb-program-review-setting", children: [
                _jsx("strong", { children: change.label }), _jsx("span", { children: `${change.before} → ${change.after}` })] }, index)),
            changes.days.map(day => _jsxs("section", { className: "wpb-program-review-day", children: [
                _jsx("h3", { children: day.label }),
                day.beforeMinutes !== day.afterMinutes && _jsx("p", { children: `Week 1 estimate: ${day.beforeMinutes} → ${day.afterMinutes} min` }),
                day.rows.map((row, index) => _jsxs("article", { className: "wpb-program-review-row", children: [
                    _jsx("strong", { children: row.name }), _jsx("small", { children: row.kind }),
                    _jsx("div", { className: "wpb-program-review-columns", children: [['Before', row.before], ['After', row.after]].map(([label, lines]) =>
                        _jsxs("div", { children: [_jsx("h4", { children: label }), ...lines.map((line, i) => _jsx("p", { children: line }, i))] }, label)) })] }, `${row.id}:${index}`))] }, day.id)),
            !changes.count && _jsx('p', { children: emptyText })] });
}

export function ProgramRevisionReview({ review, onApply, onClose }) {
    const changes = useMemo(() => reviewProgramChanges(review.before, review.after), [review.before, review.after]);
    return _jsx(SessionChoiceDialog, { title: review.title, onClose, className: "wpb-program-revision", footer:
        _jsxs("div", { className: "wpb-program-revision-footer", children: [
            _jsx("button", { type: "button", onClick: onClose, className: "wpb-session-choice pressable", children: "Keep current plan" }),
            _jsx("button", { type: "button", onClick: onApply, "data-apply-program-review": true, className: "wpb-session-choice pressable", children: "Apply changes" })] }),
        children: _jsxs("div", { "data-program-review": true, children: [
            _jsx("p", { className: "wpb-program-review-status", children: "Preview · nothing changes until you apply." }),
            _jsxs("section", { className: "wpb-program-review-scope", "aria-label": "Change impact", children: [
                _jsxs("p", { children: [_jsx("strong", { children: "What changes" }),
                    _jsx("span", { children: changes.count ? `${changes.count} setting or exercise difference${changes.count === 1 ? '' : 's'}` : "No visible setting or exercise differences" })] }),
                _jsxs("p", { children: [_jsx("strong", { children: "Why" }), _jsx("span", { children: review.message })] }),
                _jsxs("p", { children: [_jsx("strong", { children: "When" }), _jsx("span", { children: "After you choose Apply changes" })] }),
                _jsxs("p", { children: [_jsx("strong", { children: "What stays intact" }), _jsx("span", { children: "Completed workouts; a restorable copy of the previous layout is kept" })] })
            ] }),
            review.audit && _jsxs("section", { className: "wpb-program-review-audit", children: [
                _jsx("h3", { children: "Volume check" }),
                _jsx("p", { children: `${review.audit.before.length} findings before · ${review.audit.after.length} remaining after` }),
                review.audit.after.map((issue, index) => _jsx("p", { children: `${issue.label}: ${fmtSets(issue.v)} sets · ${issue.fix || (issue.status === 'under' ? 'still below the planned minimum' : 'still above the planned maximum')}` }, `${issue.region}:${index}`))] }),
            _jsx(ProgramChangeDetails, { changes, emptyText: "The exercise list, weekly targets and displayed settings match. Applying keeps the rebuilt program details." })] }) });
}
export function ProgramLayoutHistory({ program, onRestore }) {
    if (!program.layoutHistory?.length) return null;
    return _jsxs("section", { className: "wpb-program-layout-history", "aria-label": "Earlier layouts", children: [
        _jsx("h3", { children: "Earlier layouts" }),
        _jsx("p", { children: "Saved exercises, settings and targets. Review before restoring." }),
        program.layoutHistory.map((revision, index) => _jsxs("button", { type: "button", className: "wpb-session-choice pressable",
            "data-restore-program-layout": index, onClick: () => onRestore(revision), children: [
                _jsx("strong", { children: revision.label }),
                _jsx("span", { children: new Date(revision.at).toLocaleString() }), _jsx("span", { children: "Review restore" })] }, `${revision.at}:${index}`))] });
}

export function ProgramSaveReview({ program, changes, scope, onScope, conflicts, onConfirm, onClose }) {
    const detailedChanges = useMemo(() => changes.map(change => ({ ...change,
        details: reviewProgramChanges(change.before, change.after) })), [changes]);
    // One focus owner for Program dialogs: closed <details> and disabled actions
    // are excluded from the keyboard cycle; Escape cannot reach the background.
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose);
    return _jsx("div", { className: "wpb-backdrop wpb-save-review-backdrop", role: "dialog", "aria-modal": "true",
        "aria-labelledby": "program-save-title", onClick: event => { if (event.target === event.currentTarget) onClose(); },
        children: _jsxs("div", { ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown,
            onClick: e => e.stopPropagation(), className: "wpb-save-review-panel", children: [_jsxs("div", { className: "wpb-ui-sheet-heading wpb-save-review-heading", children: [_jsx("h2", { id: "program-save-title", className: "wpb-ui-sheet-title wpb-save-review-title", children: "Review changes" }), _jsx(UiSheetClose, { chrome: "dialog", "aria-label": "Close change review",
                        onClick: onClose, className: "wpb-save-review-close" })] }), _jsxs("div", { className: "wpb-scroll wpb-save-review-scroll wpb-ui-sheet-scroll", children: [_jsx("p", { className: "wpb-save-review-intro", children: "Review the saved programs affected. Completed workout history stays as logged." }), program.cycleId && _jsxs("fieldset", { className: "wpb-save-review-scope", children: [_jsx("legend", { className: "wpb-save-review-legend", children: "Apply exercise changes to" }), [["program", "This block only"], ["cycle", "Linked cycle blocks"]].map(([value, label]) => _jsxs("label", { className: "wpb-save-review-option", children: [_jsx("input", { type: "radio", name: "program-save-scope", value: value, checked: scope === value, onChange: () => onScope(value) }), label] }, value)), _jsx("div", { className: "wpb-save-review-help", children: "Linked blocks use the cycle's exercise-sharing rules. Only blocks listed below will change; each keeps its own training targets." })] }), _jsxs("div", { className: "wpb-save-review-count", children: [changes.length, " saved ", changes.length === 1 ? "program" : "programs", " will change"] }), detailedChanges.map(({ before, after, details }) => _jsxs("div", { className: "wpb-save-review-change", children: [_jsx("strong", { className: "wpb-save-review-name", children: after.name }), _jsx("div", { className: "wpb-save-review-summary", children: programChangeLabels(before, after).join(" · ") }),
            _jsxs('details', { className: 'wpb-save-review-details', children: [
                _jsx('summary', { children: 'View before and after' }),
                _jsx(ProgramChangeDetails, { changes: details, emptyText: 'The displayed exercise targets match; the saved details listed above will change.' })
            ] })] }, after.id)), !!conflicts.length && _jsxs("div", { role: "alert", className: "wpb-save-review-alert", children: ["Another affected block has draft changes: ", conflicts.map(x => x.after.name).join(", "), ". Save or discard that draft first, or choose This block only."] }), _jsx("div", { className: "wpb-save-review-undo", children: "You can undo this save from the program screen until the next save or app reload. Later edits can prevent undo." })] }), _jsxs("div", { className: "wpb-save-review-footer", children: [_jsx("button", { disabled: !changes.length || !!conflicts.length, onClick: onConfirm, className: "pressable wpb-save-review-confirm", children: "Save reviewed changes" }), _jsx("button", { onClick: onClose, className: "pressable wpb-save-review-cancel", children: "Keep editing" })] })] }) });
}
