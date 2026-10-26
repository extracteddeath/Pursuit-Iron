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
    /* plannedWeek, not the weeklyVolume sum: this chart is labelled "planned sets", and the volume
       sum includes fractional secondary credit, which is a different quantity from the sets you will
       actually perform. Showing that one under this label was the exact confusion criticised in the
       build these views came from. */
    /* ⚠ TWO DEFECTS ON ONE LINE, AND THE SECOND WAS HIDING BEHIND THE FIRST.
       (1) OFF BY ONE. This sampled plannedWeek(0 … weeks-1) and labelled them W1 … Wn, while every
       other caller of plannedWeek — the week chips, the header strip — samples 1 … weeks and calls the
       deload weeks+1. computeCell clamps week 0 to week 1, so the first bar looked right and nothing
       threw; the wave was simply one week behind from W3 onward. Measured on a 6-week block: the wave
       showed 85/85/85/89/96/100 where the chips show 85/85/89/96/100/100. The comment above promises
       "one definition, read by the week chips, the header strip and the load wave, so those three can
       never disagree about the size of a week" — they were sharing the function and disagreeing about
       the ARGUMENT, which is the same divergence wearing a disguise.
       (2) THE DELOAD WAS NEVER IN THE CHART AT ALL, so a card whose whole purpose is to make "an
       accumulation ramp, a step, or a deload trough visible at a glance" could not show the trough.
       The old highlight test — lowest quartile AND `w === weeks - 1` — was looking for the deload in
       the last HARD week, which carries the most volume, so it could never fire on any program ever
       generated. A deload is not inferred from the shape of a bar; the program says whether it has
       one, so ask it. */
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
                /* Bars are scaled from the LOW week, not from zero: every week of a block is a large
                   number, so a zero-based axis renders twelve near-identical bars and shows nothing. The
                   floor is stated in the subtitle so the exaggeration cannot be read as the whole story. */
                const h = 18 + ((d.sets - low) / Math.max(1, peak - low)) * 78;
                const dl = !!d.deload; // the program's own fact, not a guess from the bar's height
                const first = d.w === 1;
                return (_jsxs("div", { "data-wave-week": d.w, "data-wave-sets": d.sets, "data-wave-deload": dl ? "1" : "0", style: { flex: 1, minWidth: 0, textAlign: "center" }, children: [_jsx("div", { className: "mono", style: { fontSize: 11, fontWeight: 700, color: dl ? palette.warn : first ? palette.accentInk : palette.faint, marginBottom: 2 }, children: d.sets }), _jsx("div", { style: { height: h, borderRadius: 8, background: dl ? palette.warn : first ? palette.accent : palette.accentDim } }), _jsx("div", { className: "mono", style: { fontSize: 11, color: dl ? palette.warn : first ? palette.accentInk : palette.faint, marginTop: 4 }, children: dl ? "DL" : `W${d.w}` })] }, d.w));
            }) }) }));
}
