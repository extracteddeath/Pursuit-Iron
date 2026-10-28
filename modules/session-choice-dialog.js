// Shared accessible session dialog. Training state stays in feature owners.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useRef, useEffect, useCallback } from 'react';
import { X } from 'lucide-react';

const focusable = 'button:not(:disabled),select:not(:disabled),textarea:not(:disabled),input:not(:disabled),summary,a[href]';

export function SessionChoiceDialog({ title, onClose, children, dismissible = true, className = "", footer = null, panelRef }) {
    const dialogRef = useRef(null);
    const attachPanel = useCallback(node => { dialogRef.current = node; panelRef?.(node); }, [panelRef]);
    useEffect(() => {
        const previous = document.activeElement;
        dialogRef.current?.focus({ preventScroll: true });
        return () => { if (previous?.isConnected) previous.focus?.({ preventScroll: true }); };
    }, []);
    const onKeyDown = ev => {
        if (ev.key === 'Escape' && dismissible) {
            ev.preventDefault(); ev.stopPropagation(); onClose(); return;
        }
        if (ev.key !== 'Tab') return;
        const panel = dialogRef.current;
        if (!panel) return;
        // Match the established journal/wizard tab order: generic tabindex elements
        // are not action controls, and including them after a <summary> lets Tab escape.
        const items = [...panel.querySelectorAll(focusable)].filter(el =>
            el.getClientRects().length &&
            el.getAttribute('tabindex') !== '-1' &&
            !el.closest('[inert],[aria-hidden="true"]') &&
            getComputedStyle(el).visibility !== 'hidden' &&
            (el.tagName === 'SUMMARY' || !el.closest('details:not([open])')));
        const first = items[0], last = items.at(-1), active = document.activeElement;
        if (!first) { ev.preventDefault(); panel.focus({ preventScroll: true }); }
        else if (ev.shiftKey && (active === first || active === panel || !panel.contains(active))) {
            ev.preventDefault(); last.focus({ preventScroll: true });
        } else if (!ev.shiftKey && (active === last || active === panel || !panel.contains(active))) {
            ev.preventDefault(); first.focus({ preventScroll: true });
        }
    };
    return _jsx("div", {
        className: "wpb-backdrop wpb-session-dialog-backdrop",
        onClick: ev => { if (dismissible && ev.target === ev.currentTarget) onClose(); },
        children: _jsxs("section", {
            ref: attachPanel, tabIndex: -1, role: "dialog", "aria-modal": "true", "aria-label": title,
            className: "wpb-session-dialog wpb-scroll " + className,
            onClick: ev => ev.stopPropagation(), onKeyDown,
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
