// Program/Plan volume-wave presentation only. Prescriptions and week totals
// remain owned by the canonical engine; no separate cycle or history state.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo } from 'react';
import { BarChart3 } from 'lucide-react';
import { InfoCard } from './ui-disclosure.js';
import { weeksOf, plannedWeek } from './engine-api.js';

// plannedWeek uses 1-based weeks. Deload, when configured, is weeks + 1.
// These are actual planned sets, not fractional muscle-volume credits.
export function LoadWaveCard({ program, open, onToggle }) {
    const weeks = weeksOf(program);
    const deloadWeek = program.config?.deload ? weeks + 1 : null;
    const data = useMemo(() => {
        const out = Array.from({ length: weeks }, (_, i) => ({ w: i + 1, ...plannedWeek(program, i + 1) }));
        if (deloadWeek)
            out.push({ w: deloadWeek, deload: true, ...plannedWeek(program, deloadWeek) });
        return out;
    }, [program, weeks, deloadWeek]);
    const peak = Math.max(1, ...data.map(d => d.sets));
    const low = Math.min(...data.map(d => d.sets));
    return (_jsx(InfoCard, { icon: BarChart3, title: "Volume load wave", open: open, onToggle: onToggle, subtitle: `${low === peak ? peak : `${low}–${peak}`} sets/week · ${weeks} week${weeks === 1 ? "" : "s"}${deloadWeek ? " + deload" : ""}`, children: _jsx("ol", { "data-load-wave": true, className: "wpb-load-wave", tabIndex: 0, "aria-label": "Planned working sets by week", children: data.map(d => {
                // Show deviations from the block's low week, not zero; subtitle names the scale.
                const h = 18 + ((d.sets - low) / Math.max(1, peak - low)) * 78;
                const dl = !!d.deload; // the program's own fact, not a guess from the bar's height
                const first = d.w === 1;
                return (_jsxs("li", { "data-wave-week": d.w, "data-wave-sets": d.sets, "data-wave-deload": dl ? "1" : "0", "data-first": first,
                    "aria-label": `${dl ? "Deload" : `Week ${d.w}`}: ${d.sets} planned working sets`, className: "wpb-load-wave-week", children: [
                    _jsx("span", { className: "mono wpb-load-wave-sets", "aria-hidden": true, children: d.sets }),
                    _jsx("div", { className: "wpb-load-wave-bar", "aria-hidden": true, style: { height: h } }),
                    _jsx("span", { className: "mono wpb-load-wave-label", "aria-hidden": true, children: dl ? "Deload" : `W${d.w}` })] }, d.w));
            }) }) }));
}
