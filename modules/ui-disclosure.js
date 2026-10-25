// Shared disclosure presentation and interruptible motion. No training state or calculations.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useId, useState, useRef, useLayoutEffect } from 'react';
import { ChevronDown } from 'lucide-react';
import { spring, stopMotion } from './ui-motion.js';

export function MotionReveal({ open, children }) {
    const [alive, setAlive] = useState(!!open);
    const holder = useRef(null), content = useRef(null), last = useRef(children);
    if (open) last.current = children;
    useLayoutEffect(() => {
        if (open && !alive) { setAlive(true); return; }
        const el = holder.current, inner = content.current;
        if (!el || !inner) return;
        el.inert = !open;
        let target = -1;
        const resize = () => {
            const next = open ? inner.getBoundingClientRect().height : 0;
            if (Math.abs(next - target) < .5) return;
            target = next;
            spring(el, 'reveal-height', next, { from: el.getBoundingClientRect().height, damping: 1,
                write: v => { el.style.height = Math.max(0, v) + 'px'; },
                complete: () => { if (open) el.style.height = 'auto'; else setAlive(false); } });
        };
        resize();
        const observer = new ResizeObserver(resize);
        if (open) observer.observe(inner);
        return () => { observer.disconnect(); stopMotion(el, 'reveal-height'); };
    }, [open, alive]);
    if (!open && !alive) return null;
    return _jsx('div', { ref: holder, className: 'wpb-motion-reveal', 'data-pi-reveal': true,
        'aria-hidden': open ? undefined : true,
        children: _jsx('div', { ref: content, children: open ? children : last.current }) });
}

export function InfoCard({ icon: Icon, title, subtitle, open, onToggle, children, collapsible = true }) {
    const isOpen = collapsible ? !!open : true;
    const contentId = useId();
    const heading = [
        _jsx('span', { className: 'wpb-info-card-icon', 'aria-hidden': true, children: _jsx(Icon, { size: 17 }) }, 'icon'),
        _jsxs('span', { className: 'wpb-info-card-heading-copy', children: [
            _jsx('span', { className: 'wpb-info-card-title', children: title }),
            subtitle && _jsx('span', { className: 'wpb-info-card-subtitle', children: subtitle })
        ] }, 'copy'),
        collapsible && _jsx(ChevronDown, { size: 20, 'aria-hidden': true, className: 'wpb-chev wpb-info-card-chevron' }, 'chevron')
    ];
    return _jsxs('section', { 'data-infocard': true, 'data-collapsible': collapsible ? '1' : '0',
        className: 'wpb-info-card', children: [
            collapsible
                ? _jsx('button', { type: 'button', className: 'pressable wpb-info-card-toggle',
                    onClick: onToggle, 'aria-expanded': isOpen, 'aria-controls': contentId, children: heading })
                : _jsx('div', { className: 'wpb-infocard-static-head wpb-info-card-static-head', children: heading }),
            _jsx('div', { id: contentId, children: _jsx(MotionReveal, { open: isOpen,
                children: _jsx('div', { className: 'wpb-info-card-content', children }) }) })
        ] });
}
