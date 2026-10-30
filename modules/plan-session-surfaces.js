// One presentation owner for Home and Plan session review. No prescription generation or saved state.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { History, ChevronRight, ChevronDown, TrendingUp, Trophy, Zap } from 'lucide-react';
import { useId } from 'react';
import { workoutTechniqueRest } from './workout-context.js';
import { SessionChoiceDialog } from './session-choice-dialog.js';

export function sessionLoadText(set, unit) {
    const w = set?.weight === "" || set?.weight == null ? null : Number(set.weight), reps = Number(set?.reps);
    return `${w != null && Number.isFinite(w) ? Math.round(w * 100) / 100 + ' ' + unit : 'Choose load'} × ${reps > 0 ? reps : '—'}`;
}
export function sessionTargetsText(sets, unit) {
    const uniform = sets.every(set => set.weight === sets[0]?.weight && set.reps === sets[0]?.reps);
    return uniform ? sessionLoadText(sets[0], unit) : sets.map((set, i) => `Set ${i + 1}: ${sessionLoadText(set, unit)}`).join(' · ');
}
export function PlanChangeBanner({ review, onClick }) {
    if (!review?.changes.length) return null;
    const held = review.changes.filter(row => row.change.undone).length;
    const count = review.changes.length;
    return _jsxs("button", { type: "button", className: "wpb-plan-change-banner pressable", onClick, "aria-label": `Review ${count} plan adjustment${count === 1 ? '' : 's'}${held ? `, ${held} prior load${held === 1 ? '' : 's'} kept` : ''}`, children: [
        _jsx("span", { className: "wpb-session-plan-icon", 'aria-hidden': true, children: _jsx(History, { size: 19 }) }),
        _jsxs("span", { children: [_jsx("strong", { children: `${count} upcoming adjustment${count === 1 ? '' : 's'}` }),
            _jsx("span", { children: held ? `${held} prior load${held === 1 ? '' : 's'} kept · See details` : "Review load and planned sets" })] }),
        _jsx(ChevronRight, { size: 18, 'aria-hidden': true })] });
}
// The Home hero is a workout preview, not a second coaching feed.
export function HomeSessionOutline({ review, expanded, onToggle }) {
    // IDs must be unique if Home and Plan surfaces remain mounted during navigation.
    const listId = useId();
    if (!review?.exercises.length) return null;
    return _jsxs("section", { className: "wpb-home-session-outline", "aria-label": "Session outline", children: [
        _jsxs("button", { type: "button", className: "wpb-home-outline-toggle pressable", onClick: onToggle,
            "aria-expanded": expanded, "aria-controls": listId,
            "aria-label": `${expanded ? 'Hide' : 'Show'} all ${review.exercises.length} session exercises`, children: [
                _jsx("strong", { children: "Session outline" }),
                _jsx("span", { children: `${review.exercises.length} exercises` }),
                _jsx(ChevronDown, { size: 17 })] }),
        _jsx("div", { id: listId, hidden: !expanded, children: expanded &&
            _jsx("ul", { className: "wpb-home-outline-list", children: review.exercises.map(row =>
                _jsxs("li", { className: "wpb-m3-outline-row", children: [_jsx("span", { children: row.name }),
                    _jsx("strong", { children: `${row.cell.sets}×${row.cell.range || row.cell.reps}` })] }, row.id + ':' + row.slot)) }) })
    ] });
}
// Like the prototype, this is a small set of actionable coaching highlights,
// not every working set and the full engine explanation repeated on Home.
export function SessionGamePlan({ review, restScale = 1, home = false }) {
    if (!review?.exercises.length) return null;
    const ranked = [...review.exercises].sort((a, b) => {
        const score = row => (row.opportunity ? 8 : 0) + (row.change.undone ? 4 : 0)
            + (row.protocol.type !== 'straight' ? 2 : 0) + (row.change.changed ? 1 : 0);
        return score(b) - score(a);
    }).slice(0, 3);
    return _jsxs("section", { className: "wpb-session-game-plan" + (home ? " wpb-home-game-plan" : ""),
        "aria-label": "Today's game plan", children: [
        _jsxs("h2", { className: "wpb-game-plan-heading", children: [_jsx(TrendingUp, { size: 19 }),
            _jsx("strong", { children: "Today's game plan" }),
            _jsx("span", { className: "wpb-game-plan-count", children: ranked.length })] }),
        _jsx("div", { className: "wpb-game-plan-highlights", children: ranked.map(row => {
            const pr = row.opportunity;
            const technique = row.protocol.type === 'myo_reps' ? `Myo-reps · ${workoutTechniqueRest({ kind: 'myo' }, restScale)}-second rests.`
                : row.protocol.type === 'drop_set' ? 'Drop set on the last set.' : null;
            const dose = sessionTargetsText(row.effective, review.unit);
            const why = pr ? (pr.type === 'e1rm' ? `Could raise estimated 1RM to ${pr.estimated} ${review.unit}.`
                : `Could beat your ${pr.bestReps}-rep best at this load.`) : row.reason;
            const guidance = `${dose} · ${technique || why}`;
            return _jsxs("div", { className: "wpb-game-plan-exercise", "data-game-plan-exercise": row.id,
                ...(pr ? { "data-pr-exercise": row.id } : {}), children: [
                _jsx("span", { className: "wpb-game-plan-highlight-icon", "aria-hidden": true,
                    children: _jsx(pr ? Trophy : row.protocol.type !== 'straight' ? Zap : TrendingUp, { size: 16 }) }),
                _jsxs("details", { className: "wpb-game-plan-highlight-copy", children: [
                    _jsxs("summary", { children: [
                        _jsx("strong", { children: pr ? `PR chance · ${row.name}` : row.name }),
                        _jsx("span", { className: "wpb-game-plan-highlight-preview", children: guidance }),
                        _jsx(ChevronDown, { size: 16, "aria-hidden": true })] }),
                    _jsx("p", { children: guidance })] })] }, row.id + ':' + row.slot);
        }) })
    ] });
}
export function PlanChangesDialog({ review, dayLabel, busy, onChoice, onClose }) {
    // A planned-set revision is a saved program edit, while load holds are scoped
    // to this next workout. Do not mislabel both as a one-session change.
    const hasSavedSetChanges = review.changes.some(row => !!row.change.setDelta);
    return _jsx(SessionChoiceDialog, { title: "Plan changes", onClose, className: "wpb-plan-changes-dialog",
        children: _jsxs("div", { className: "wpb-plan-changes-body", children: [
            _jsx("p", { className: "wpb-plan-review-intro", children: `${dayLabel} · Week ${review.weekIndex}` }),
            _jsx("p", { className: "wpb-plan-review-basis", children: hasSavedSetChanges
                ? "Planned set changes affect your saved program. Load choices affect this workout. Completed history stays intact."
                : "Load choices affect this workout. Completed history stays intact." }),
            !review.changes.length && _jsx("p", { children: "No meaningful changes to review." }),
            ...review.changes.map(row => {
                const change = row.change;
                const why = change.undone ? `Original recommendation: ${row.proposedReason}` : row.proposedReason;
                const showTargets = change.loadChanged;
                // The first useful comparison should not require an extra tap.
                // Longer set-by-set diffs stay in the existing disclosure to avoid crowding.
                const singleSet = showTargets && row.proposed?.length === 1 && change.previous?.length === 1;
                const targetDetails = showTargets && _jsx("ol", { className: "wpb-plan-target-diff",
                    "aria-label": "Previous and upcoming working-set loads", children:
                    row.proposed.map((set, i) => _jsxs("li", { children: [
                        _jsx("span", { className: "wpb-change-set-name", children: `Set ${i + 1}` }),
                        _jsx("span", { className: "wpb-plan-before", children: change.previous[i]
                            ? sessionLoadText(change.previous[i], review.unit) : 'New set' }),
                        _jsx(ChevronRight, { size: 15, "aria-label": "to" }),
                        _jsx("strong", { children: sessionLoadText(change.undone ? change.undoTargets[i] : set, review.unit) })
                    ] }, i)) });
                return _jsxs("section", { className: "wpb-plan-change-card", "data-plan-change": row.id,
                    "data-kind": change.kind, "data-undone": change.undone, children: [
                    _jsxs("div", { className: "wpb-plan-change-title", children: [
                        _jsx("h3", { children: row.name }),
                        _jsx("span", { children: change.undone ? 'Prior load kept' : change.category })] }),
                    !singleSet && _jsx("p", { className: "wpb-plan-change-summary", children: change.summary }),
                    singleSet && _jsx("p", { className: "wpb-plan-review-basis", children: "Last → Upcoming" }),
                    singleSet && targetDetails,
                    _jsxs("details", { className: "wpb-plan-change-more", children: [
                        _jsx("summary", { children: singleSet ? "Why this change?" : "Why and set comparison" }),
                        _jsx("p", { className: "wpb-plan-change-reason", children: why }),
                        change.source && _jsx("p", { className: "wpb-plan-review-basis", children:
                            `Compared with ${new Date(change.source.date).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} · same workout day.` }),
                        change.setDelta && _jsx("p", { className: "wpb-plan-review-basis", children:
                            "Planned sets are compared with the most recent saved program revision, not completed sets." }),
                        !singleSet && targetDetails] }),
                    change.canUndo ? _jsx("button", { type: "button", className: "wpb-plan-undo pressable", disabled: busy,
                        "aria-label": `${change.undone ? 'Use recommended load for' : 'Keep previous load for'} ${row.name}`,
                        onClick: () => onChoice(review.programId, review.dayId, review.weekIndex, row.id,
                            !change.undone, review.sessionLengthPlan, row.slot),
                        children: change.undone ? "Use recommended load" : "Keep previous load" })
                        : _jsx("p", { className: "wpb-plan-review-basis", children: change.kind === 'sets'
                            ? "To revise planned sets, edit your saved program."
                            : "The previous load cannot be restored exactly with this equipment." })
                ] }, row.id + ':' + row.slot);
            })
        ] }) });
}
