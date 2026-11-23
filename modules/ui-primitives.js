// Stateless shared UI. Screen state, navigation and training stay with their feature owners.
import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from 'react/jsx-runtime';
import { Check, X } from 'lucide-react';
import { navigateChoiceRow } from './ui-choice-navigation.js';

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
// Canonical surface owner: preserve each caller's existing DOM tag, aria state, and feature classes.
export function UiCard({ as: Element = "div", className = "", tone, ...props }) {
    // Tone is a design-system surface role, not an arbitrary screen-specific style.
    // Keep the same native tag, props and caller class for existing card consumers.
    return _jsx(Element, { ...props, ...(tone ? { "data-surface": tone } : {}),
        className: `wpb-ui-card ${className}`.trim() });
}
// Metric anatomy is shared across destinations; each caller retains its existing
// class names, visible value/label order, formatting, and optional context.
// This component introduces no CSS owner or data dependency, and never changes
// number formatting or layout geometry. The screen owns the metric data.
export function UiMetric({
    as: Element = "div", className = "", label, value, context = null,
    labelFirst = false, labelClassName = "", valueClassName = "",
    contextClassName = "", labelProps = {}, valueProps = {}, contextProps = {},
    ...props
}) {
    const valueNode = _jsx("div", { ...valueProps, className: valueClassName, children: value });
    const labelNode = _jsx("div", { ...labelProps, className: labelClassName, children: label });
    const children = labelFirst ? [labelNode, valueNode] : [valueNode, labelNode];
    if (context != null)
        children.push(_jsx("div", { ...contextProps, className: contextClassName, children: context }));
    return _jsxs(Element, { ...props, className: `wpb-ui-metric ${className}`.trim(), children });
}


// Shared native-form owner: gym and workout controls keep their labels and state,
// while one component supplies the canonical editable field anatomy.
export function UiSummary({ ...props }) {
    return _jsx('summary', props);
}

export function UiField({ as: Element = "input", className = "", ...props }) {
    return _jsx(Element, { ...props, className: `wpb-ui-control ${className}`.trim() });
}

export function UiAction({ variant = "secondary", className = "", ...props }) {
    // Components own static visual properties; only runtime-measured styles are forwarded.
    return _jsx("button", { type: "button", ...props, className: `pressable wpb-ui-action ${className}`, "data-action": variant });
}
// One accessible 48px icon target for sheets, toolbars and standalone actions.
export function UiIconAction({ "aria-label": label, variant = "icon", ...props }) {
    return _jsx(UiAction, { variant, "aria-label": label, "data-icon-only": true, ...props });
}
// kind "filter" is the Compose FilterChip: 32dp visual (48 hit), 8dp corners, a leading check
// once selected; `icon` is its unselected leading icon (an AssistChip when nothing toggles).
export function UiChoice({ selected, kind = "chip", className = "", label, caption, icon: Icon, children, ...props }) {
    const lead = kind === "filter" && (selected ? _jsx(Check, { size: 16, "aria-hidden": true, className: "wpb-ui-chip-icon" })
        : Icon ? _jsx(Icon, { size: 16, "aria-hidden": true, className: "wpb-ui-chip-icon" }) : null);
    return _jsx("button", { type: "button", ...props, className: `pressable wpb-ui-choice ${className}`, "data-choice-kind": kind, "aria-pressed": !!selected,
        children: kind === "summary" ? _jsxs(_Fragment, { children: [
            _jsx("span", { className: "wpb-ui-choice-label", children: label }),
            caption != null && _jsx("span", { className: "mono wpb-ui-choice-caption", children: caption })
        ] }) : lead ? _jsxs(_Fragment, { children: [lead, children] }) : children });
}
// Compose ListItem anatomy: leading slot, overline/headline/supporting text, trailing slot. Renders
// a native button when it has an action, so rows never need their own one-off button styles.
export function UiListItem({ leading, overline, headline, supporting, trailing, onClick, className = "", ...props }) {
    const Element = onClick ? "button" : "div";
    return _jsxs(Element, { ...(onClick ? { type: "button", onClick } : {}), ...props,
        className: `${onClick ? "pressable " : ""}wpb-ui-list-item ${className}`.trim(), children: [
            leading != null && _jsx("span", { className: "wpb-ui-list-leading", children: leading }),
            _jsxs("span", { className: "wpb-ui-list-content", children: [
                overline && _jsx("span", { className: "wpb-ui-list-overline", children: overline }),
                _jsx("span", { className: "wpb-ui-list-headline", children: headline }),
                supporting && _jsx("span", { className: "wpb-ui-list-supporting", children: supporting })
            ] }),
            trailing != null && _jsx("span", { className: "wpb-ui-list-trailing", children: trailing })
        ] });
}
// Compose LinearWavyProgressIndicator: the completed part is a wave, the rest a flat track
// separated by a gap and ended by a stop dot. Pure SVG; the wave flattens under reduced motion.
export function UiWavyProgress({ value = 0, max = 1, label, className = "", ...props }) {
    const pct = Math.max(0, Math.min(1, max ? value / max : 0));
    const W = 240, H = 12, mid = H / 2, amp = 3, wave = 20, end = Math.max(0, pct * W - (pct < 1 ? 4 : 0));
    let d = `M0 ${mid}`;
    for (let x = 0; x < end; x += wave / 2) {
        const nx = Math.min(end, x + wave / 2), up = Math.round(x / (wave / 2)) % 2 === 0;
        d += ` Q${(x + nx) / 2} ${up ? mid - amp * 2 : mid + amp * 2} ${nx} ${mid}`;
    }
    return _jsx("div", { ...props, role: "progressbar", "aria-label": label, "aria-valuemin": 0, "aria-valuemax": 100,
        "aria-valuenow": Math.round(pct * 100), className: `wpb-ui-wavy ${className}`.trim(), children:
        _jsxs("svg", { viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: "none", "aria-hidden": true, children: [
            pct < 1 && _jsx("line", { className: "wpb-ui-wavy-track", x1: Math.min(W, end + 8), x2: W - 4, y1: mid, y2: mid }),
            pct < 1 && _jsx("circle", { className: "wpb-ui-wavy-stop", cx: W - 2, cy: mid, r: 2 }),
            end > 0 && _jsx("path", { className: "wpb-ui-wavy-active", d })
        ] }) });
}
// Compose-style inline banner: leading icon, title and supporting text, one action. Home's injury
// card and the workout's protection notice share it instead of each owning a flex row.
export function UiBanner({ as: Element = "div", icon: Icon, title, children, actionLabel, onAction, actionVariant = "tonal", className = "", ...props }) {
    return _jsxs(Element, { ...props, className: `wpb-ui-banner ${className}`.trim(), children: [
        Icon && _jsx("span", { className: "wpb-ui-banner-icon", "aria-hidden": true, children: _jsx(Icon, { size: 18 }) }),
        _jsxs("span", { className: "wpb-ui-banner-copy", children: [
            title && _jsx("strong", { className: "wpb-ui-banner-title", children: title }),
            children && _jsx("span", { className: "wpb-ui-banner-text", children })
        ] }),
        actionLabel && _jsx(UiAction, { variant: actionVariant, className: "wpb-ui-banner-action", onClick: onAction, children: actionLabel })
    ] });
}
// One stat tile: value (with optional change badge) over a label, as a description-list pair.
export function UiStatTile({ value, label, delta, deltaLabel, className = "", ...props }) {
    return _jsxs("div", { ...props, className: `wpb-ui-stat ${className}`.trim(), children: [
        _jsx("dt", { className: "wpb-ui-stat-label", children: label }),
        _jsxs("dd", { className: "mono wpb-ui-stat-value", children: [value, delta != null && delta !== 0 && _jsxs("span", {
            className: "wpb-ui-stat-delta", "data-up": delta > 0 ? "true" : "false", "aria-label": deltaLabel,
            children: [delta > 0 ? "\u25B2" : "\u25BC", Math.abs(delta), "%"] })] })
    ] });
}
export function UiStatus({ className = "", children, ...props }) {
    // Static guidance is not a live announcement. Dynamic save/failure announcements keep their
    // existing status owner rather than repeating every time a view mounts.
    return _jsx("div", { ...props, className: `wpb-ui-status ${className}`, children });
}
export function UiSection({ className = "", ...props }) {
    return _jsx("section", { ...props, className: `wpb-ui-section ${className}` });
}


// One header anatomy for Program sheets. A caller may retain its original native
// header/div tag, heading, close action, accessibility attributes and feature classes.
// Styling stays with the existing wpb-ui-sheet-heading CSS role.
export function UiSheetHeading({ as: Element = "div", className = "", ...props }) {
    const names = className.trim().split(/\s+/).filter(Boolean);
    const classes = names.includes("wpb-ui-sheet-heading")
        ? className : `wpb-ui-sheet-heading ${className}`.trim();
    return _jsx(Element, { ...props, className: classes });
}

// Stateless close control for Program sheets and dialogs; each feature owns
// its dismissal/confirmation state. Dialog chrome retains the existing paint.
export function UiSheetClose({ className = "", "aria-label": label = "Close",
    variant, chrome = "sheet", iconSize = 18, ...props }) {
    const children = _jsx(X, { size: iconSize, "aria-hidden": true });
    if (variant) return _jsx(UiIconAction, { variant, ...props,
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
        onKeyDown: event => navigateChoiceRow(event, index => onChange(options[index][0])),
        children: options.map(([key, text]) => _jsx(UiChoice, {
            kind: "tab", selected: Object.is(value, key),
            onClick: () => onChange(key), children: text
        }, String(key))) });
}
