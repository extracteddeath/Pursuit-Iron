// Shared accessible session dialog: focus restoration and keyboard containment belong in one place.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useRef, useEffect, useCallback } from 'react';
import { X } from 'lucide-react';

export function SessionChoiceDialog({ title, onClose, children, dismissible = true, className = "", footer = null, panelRef }) {
    const dialogRef = useRef(null);
    const attachPanel = useCallback(node => { dialogRef.current = node; panelRef?.(node); }, [panelRef]);
    useEffect(() => { const previous = document.activeElement; dialogRef.current?.focus();
        return () => previous?.isConnected && previous.focus?.(); }, []);
    return _jsx("div", { className: "wpb-backdrop wpb-session-dialog-backdrop", onClick: onClose,
        children: _jsxs("section", { ref: attachPanel, tabIndex: -1, role: "dialog", "aria-modal": "true", "aria-label": title,
            className: "wpb-session-dialog wpb-scroll " + className, onClick: ev => ev.stopPropagation(),
            onKeyDown: ev => { if (ev.key === 'Escape' && dismissible) onClose();
                if (ev.key === 'Tab') { const items = [...dialogRef.current.querySelectorAll('button:not(:disabled),select,textarea,input,summary,a[href]')].filter(el => el.getClientRects().length && (el.tagName === 'SUMMARY' || !el.closest('details:not([open])')));
                    const first = items[0], last = items.at(-1);
                    if (ev.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) { ev.preventDefault(); last?.focus(); }
                    else if (!ev.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) { ev.preventDefault(); first?.focus(); } } },
            children: [_jsxs("div", { className: "wpb-session-dialog-heading", children: [
                _jsx("h2", { children: title }), dismissible && _jsx("button", { type: "button", className: "pressable hit", "aria-label": "Close " + title,
                    onClick: onClose, children: _jsx(X, { size: 20 }) })] }), children, footer] }) });
}
