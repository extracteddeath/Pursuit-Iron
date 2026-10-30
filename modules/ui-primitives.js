// Stateless shared UI. Screen state, navigation and training stay with their feature owners.
import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from 'react/jsx-runtime';
import { X } from 'lucide-react';

// R61 foundations pilot. These primitives own presentation and native button semantics only;
// navigation, saved choices, logging and scroll memory remain with their existing screen owners.
export function UiHeader({ className = "", ...props }) {
    return _jsx("div", { ...props, className: `wpb-ui-header ${className}` });
}
// Root destinations share one app-bar anatomy. Deep screens keep their own Back action.
export function UiRootHeader({ title, titleClassName = "", actions, actionsClassName = "", children, className = "" }) {
    return _jsxs("header", { className: `wpb-root-header ${className}`, children: [
        _jsxs("div", { className: "wpb-root-title-row", children: [
            _jsx("h1", { className: `wpb-screen-title ${titleClassName}`, children: title }),
            actions && _jsx("div", { className: `wpb-root-actions ${actionsClassName}`, children: actions })
        ] }), children
    ] });
}
export function UiAction({ variant = "secondary", className = "", ...props }) {
    // Components own static visual properties; only runtime-measured styles are forwarded.
    return _jsx("button", { type: "button", ...props, className: `pressable wpb-ui-action ${className}`, "data-action": variant });
}
export function UiChoice({ selected, kind = "chip", className = "", label, caption, children, ...props }) {
    return _jsx("button", { type: "button", ...props, className: `pressable wpb-ui-choice ${className}`, "data-choice-kind": kind, "aria-pressed": !!selected,
        children: kind === "summary" ? _jsxs(_Fragment, { children: [
            _jsx("span", { className: "wpb-ui-choice-label", children: label }),
            caption != null && _jsx("span", { className: "mono wpb-ui-choice-caption", children: caption })
        ] }) : children });
}
export function UiStatus({ className = "", children, ...props }) {
    // Static guidance is not a live announcement. Dynamic save/failure announcements keep their
    // existing status owner rather than repeating every time a view mounts.
    return _jsx("div", { ...props, className: `wpb-ui-status ${className}`, children });
}
export function UiSection({ className = "", ...props }) {
    return _jsx("section", { ...props, className: `wpb-ui-section ${className}` });
}


// Stateless close control for Program sheets and dialogs; each feature owns
// its dismissal/confirmation state. Dialog chrome retains the existing paint.
export function UiSheetClose({ className = "", "aria-label": label = "Close",
    variant, chrome = "sheet", iconSize = 18, ...props }) {
    const children = _jsx(X, { size: iconSize, "aria-hidden": true });
    if (variant) return _jsx(UiAction, { variant, "data-icon-only": true, ...props,
        "aria-label": label, className: `hit wpb-sheet-close ${className}`, children });
    return _jsx("button", { type: "button", ...props, "aria-label": label,
        className: `pressable hit ${chrome === "dialog" ? "" : "wpb-sheet-close "}wpb-ui-sheet-close ${className}`,
        children });
}


// One labeled family of mutually exclusive choices. This uses native buttons and
// aria-pressed (a group of toggle choices, not an ARIA tablist), so screen owners
// only supply options and changes. The existing .wpb-segmented track owns geometry.
export function UiSegmented({ label, options, value, onChange, className = "" }) {
    return _jsx("div", { role: "group", "aria-label": label,
        className: `wpb-segmented ${className}`.trim(),
        children: options.map(([key, text]) => _jsx(UiChoice, {
            kind: "tab", selected: Object.is(value, key),
            onClick: () => onChange(key), children: text
        }, String(key))) });
}
