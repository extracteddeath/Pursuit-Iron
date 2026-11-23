// One modal bottom sheet for feature sheets (Compose ModalBottomSheet anatomy): scrim, drag
// handle, heading with optional kicker and close, scrolling body and an optional pinned footer.
// Portaled to the app root so a screen's own stacking context (Home isolates) never puts the
// tab bar above it. Focus trap, Escape and restore come from the shared sheet-focus owner.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { createPortal } from '../vendor/react-dom.js';
import { UiSheetClose, UiSheetHeading } from './ui-primitives.js';
import { useProgramSheetFocus } from './program-sheet-focus.js';

const appRoot = () => (typeof document !== 'undefined' && (document.querySelector('#root .wpb') || document.body)) || null;

export function UiSheet({ id, title, kicker, onClose, footer, children, className = '', panelClassName = '',
    bodyClassName = '', headingClassName = '', footerClassName = '', closeClassName = '', titleId: givenTitleId, closeLabel = 'Close', describedBy, sheetDragRef, portal = true, ...props }) {
    const { attachPanel, onPanelKeyDown } = useProgramSheetFocus(onClose, sheetDragRef);
    const titleId = givenTitleId || `${id}-title`;
    const sheet = _jsx('div', { ...props, role: 'dialog', 'aria-modal': true, 'aria-labelledby': titleId, 'aria-describedby': describedBy,
        onClick: event => { if (event.target === event.currentTarget) onClose(); },
        className: `wpb-backdrop wpb-ui-sheet-backdrop wpb-ui-sheet-focus wpb-ui-modal-sheet ${className}`.trim(), children:
        _jsxs('section', { ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown, onClick: event => event.stopPropagation(),
            className: `wpb-ui-sheet-panel wpb-ui-modal-sheet-panel ${panelClassName}`.trim(), children: [
                _jsx('div', { className: 'wpb-ui-sheet-handle', 'aria-hidden': true }),
                _jsxs(UiSheetHeading, { as: 'header', className: `wpb-ui-modal-sheet-heading ${headingClassName}`.trim(), children: [
                    _jsxs('div', { className: 'wpb-ui-modal-sheet-copy', children: [
                        kicker && _jsx('p', { className: 'wpb-ui-modal-sheet-kicker', children: kicker }),
                        _jsx('h2', { id: titleId, className: 'wpb-ui-sheet-title', children: title })
                    ] }),
                    _jsx(UiSheetClose, { onClick: onClose, 'aria-label': closeLabel, className: closeClassName })
                ] }),
                _jsx('div', { className: `wpb-scroll wpb-ui-sheet-scroll wpb-ui-modal-sheet-body ${bodyClassName}`.trim(), children }),
                footer && _jsx('footer', { className: `wpb-ui-sheet-footer wpb-ui-modal-sheet-footer ${footerClassName}`.trim(), children: footer })
            ] }) });
    const target = portal ? appRoot() : null;
    return target ? createPortal(sheet, target) : sheet;
}
