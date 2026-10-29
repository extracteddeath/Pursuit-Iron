// Shared accessible session dialog. Training state stays in feature owners.
// Program sheets and session dialogs use a single focus/keyboard behavior owner.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { X } from 'lucide-react';
import { useProgramSheetFocus } from './program-sheet-focus.js';

export function SessionChoiceDialog({ title, onClose, children, dismissible = true, className = "", footer = null, panelRef }) {
    // Protected sheets still consume Escape to prevent background workout shortcuts.
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose, panelRef, { dismissible });
    return _jsx("div", {
        className: "wpb-backdrop wpb-session-dialog-backdrop",
        onClick: ev => { if (dismissible && ev.target === ev.currentTarget) onClose(); },
        children: _jsxs("section", {
            ref: attachPanel, tabIndex: -1, role: "dialog", "aria-modal": "true", "aria-label": title,
            className: "wpb-session-dialog wpb-scroll " + className,
            onClick: ev => ev.stopPropagation(), onKeyDown: onPanelKeyDown,
            children: [
                _jsxs("div", { className: "wpb-session-dialog-heading", children: [
                    _jsx("h2", { children: title }),
                    dismissible && _jsx("button", { type: "button", className: "pressable hit",
                        "aria-label": "Close " + title, onClick: onClose, children: _jsx(X, { size: 20 }) })
                ] }),
                children, footer
            ]
        })
    });
}
