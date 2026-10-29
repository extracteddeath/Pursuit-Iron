// Keyboard and focus ownership for Program Settings and Share sheets.
// Their owners continue to control open/close, drag gestures, saving and navigation.
import { useCallback, useEffect, useRef } from 'react';
const ACTIONS = 'button:not(:disabled),select:not(:disabled),textarea:not(:disabled),input:not(:disabled),summary,a[href]';
export function useProgramSheetFocus(onClose, forwardedRef) {
    const panelRef = useRef(null);
    const attachPanel = useCallback(node => {
        panelRef.current = node;
        if (typeof forwardedRef === 'function') forwardedRef(node);
        else if (forwardedRef && typeof forwardedRef === 'object') forwardedRef.current = node;
    }, [forwardedRef]);
    useEffect(() => {
        const previous = document.activeElement;
        panelRef.current?.focus({ preventScroll: true });
        return () => { if (previous?.isConnected) previous.focus?.({ preventScroll: true }); };
    }, []);
    const onPanelKeyDown = event => {
        if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            onClose();
            return;
        }
        if (event.key !== 'Tab') return;
        const panel = panelRef.current;
        if (!panel) return;
        const eligible = [...panel.querySelectorAll(ACTIONS)].filter(el =>
            el.getClientRects().length && el.getAttribute('tabindex') !== '-1' &&
            el.getAttribute('aria-disabled') !== 'true' &&
            !el.closest('[inert],[aria-hidden="true"]') &&
            getComputedStyle(el).visibility !== 'hidden' &&
            (el.tagName === 'SUMMARY' || !el.closest('details:not([open])')));
        const first = eligible[0], last = eligible.at(-1);
        const focused = document.activeElement;
        if (!first) {
            event.preventDefault();
            panel.focus({ preventScroll: true });
        } else if (event.shiftKey && (focused === first || focused === panel || !eligible.includes(focused))) {
            event.preventDefault();
            last.focus({ preventScroll: true });
        } else if (!event.shiftKey && (focused === last || focused === panel || !eligible.includes(focused))) {
            event.preventDefault();
            first.focus({ preventScroll: true });
        }
    };
    return { attachPanel, onPanelKeyDown };
}
