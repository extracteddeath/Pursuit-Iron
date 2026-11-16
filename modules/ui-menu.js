// One menu renderer. Motion owns measured placement/keyboard behavior; screens own every action.
import { jsx as _jsx, jsxs as _jsxs, Fragment } from 'react/jsx-runtime';
import { useLayoutEffect, useRef } from 'react';
import { createPortal } from '../vendor/react-dom.js';
import { Check, Play } from 'lucide-react';
import { registerContextMenu } from './ui-motion.js';

export function ContextMenu({ id, anchor, label, title, items, onClose, ...props }) {
    const menu = useRef(null);
    const host = anchor?.closest('.wpb');
    useLayoutEffect(() => registerContextMenu(menu.current, anchor), [anchor, host]);
    const close = () => { if (anchor?.isConnected) anchor.focus({ preventScroll: true }); onClose(); };
    if (!host) return null;
    return createPortal(_jsxs(Fragment, { children: [
        _jsx('div', { className: 'wpb-dismiss-layer wpb-menu-dismiss', onClick: close, 'aria-hidden': true }),
        _jsxs('div', { ...props, ref: menu, id, role: 'menu', 'aria-label': label, tabIndex: -1,
            className: 'wpb-context-menu', children: [
                title && _jsx('div', { className: 'wpb-context-menu-heading', role: 'presentation', children: title }),
                items.map(item => _jsxs('button', { type: 'button', role: 'menuitem',
                    className: 'pressable wpb-context-menu-item', 'aria-label': item.ariaLabel,
                    'aria-current': item.selected ? 'true' : undefined, 'data-selected': item.selected || undefined,
                    'data-tone': item.danger ? 'danger' : undefined, 'data-icon-tone': item.iconTone,
                    onClick: event => { event.stopPropagation(); close(); item.on(); }, disabled: item.disabled,
                    children: [
                        item.icon && _jsx(item.icon, { size: 16, 'aria-hidden': true }),
                        item.marker != null && _jsx('span', { className: 'wpb-context-menu-marker', 'data-status': item.status, 'aria-hidden': true,
                            children: item.status === 'done' ? _jsx(Check, { size: 12, strokeWidth: 3 })
                                : item.status === 'next' ? _jsx(Play, { size: 9, fill: 'currentColor' }) : item.marker }),
                        _jsx('span', { className: 'wpb-context-menu-label', children: item.label }),
                        item.badge && _jsx('span', { className: 'wpb-context-menu-badge', children: item.badge })
                    ]
                }, item.k))
            ] })
    ] }), host);
}
