// Program/Plan volume-wave presentation only. Prescriptions and week totals
// remain owned by the canonical engine; no separate cycle or history state.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo } from 'react';
import { BarChart3 } from 'lucide-react';
import { InfoCard } from './ui-disclosure.js';
import { weeksOf, plannedWeek } from './engine-api.js';

// plannedWeek uses 1-based weeks. Deload, when configured, is weeks + 1.
// These are actual planned sets, not fractional muscle-volume credits.
export function LoadWaveCard({ program, open, onToggle, palette }) {
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
    return (_jsx(InfoCard, { icon: BarChart3, title: "Volume load wave", open: open, onToggle: onToggle, subtitle: `${low}–${peak} planned sets a week across ${weeks} week${weeks === 1 ? "" : "s"}${deloadWeek ? " plus a deload" : ""}`, children: _jsx("div", { "data-load-wave": true, style: { display: "flex", alignItems: "flex-end", gap: 6, paddingTop: 8 }, children: data.map(d => {
                // Show deviations from the block's low week, not zero; subtitle names the scale.
                const h = 18 + ((d.sets - low) / Math.max(1, peak - low)) * 78;
                const dl = !!d.deload; // the program's own fact, not a guess from the bar's height
                const first = d.w === 1;
                return (_jsxs("div", { "data-wave-week": d.w, "data-wave-sets": d.sets, "data-wave-deload": dl ? "1" : "0", style: { flex: 1, minWidth: 0, textAlign: "center" }, children: [_jsx("div", { className: "mono", style: { fontSize: 11, fontWeight: 700, color: dl ? palette.warn : first ? palette.accentInk : palette.faint, marginBottom: 2 }, children: d.sets }), _jsx("div", { style: { height: h, borderRadius: 8, background: dl ? palette.warn : first ? palette.accent : palette.accentDim } }), _jsx("div", { className: "mono", style: { fontSize: 11, color: dl ? palette.warn : first ? palette.accentInk : palette.faint, marginTop: 4 }, children: dl ? "DL" : `W${d.w}` })] }, d.w));
            }) }) }));
}
