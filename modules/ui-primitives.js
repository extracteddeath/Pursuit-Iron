// Stateless shared UI. Screen state, navigation and training stay with their feature owners.
import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from 'react/jsx-runtime';

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
