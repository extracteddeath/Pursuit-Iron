// Single presentation owner for weekly muscle-volume bars and the block ramp.
// Numbers and thresholds come from the canonical engine and App callbacks;
// no saved state, program mutation, or duplicated prescription calculation.
import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from 'react/jsx-runtime';
import { useState, useMemo, useId } from 'react';
import { Layers, ChevronDown } from 'lucide-react';
import {
    PART_LABEL, PART_ORDER, SUBMUSCLE_LABEL, SUB_LANDMARKS, fmtSets,
    landmarkFor, compositeMrv, weekMuscleBreakdown, weeklyVolume, weeksOf
} from './engine-api.js';

export function VolumeBars({ volume, subVolume, target, program = null, week = 0, palette: C, partZone, catInk }) {
    const [openPart, setOpenPart] = useState(null);
    const volumeDetailsId = useId();
    const weekBreak = useMemo(() => (program ? weekMuscleBreakdown(program, week) : null), [program, week]);
    const [granular, setGranular] = useState(false);
    const parts = PART_ORDER.filter(p => (volume[p] || 0) > 0.05 || (target && (target[p] || 0) > 0.05));
    if (!parts.length)
        return _jsx("div", { className: "wpb-volume-empty", children: "No volume logged yet." });
    // sub-regions to surface beneath their parent part when granular view is on
    const subUnder = { shoulders: ["front_delts", "side_delts", "rear_delts"], traps: ["upper_traps"], abs: ["obliques", "serratus"], calves: ["tibialis"] };
    const subBar = (k) => {
        const v = (subVolume && subVolume[k]) || 0;
        const lm = program?.engineSource === "pursuit-next" && k !== "front_delts" ? landmarkFor(k, program.config?.experience, program) : SUB_LANDMARKS[k] || { mev: 4, mrv: 20 };
        /* Use each sub-region's own MEV/MAV/MRV landmarks. */
        const subMav = lm.mav != null ? lm.mav : (lm.mev + lm.mrv) / 2;
        const zone = v < lm.mev ? { color: C.muted, label: "below MEV", sub: "under the volume that reliably drives growth" }
            : v <= subMav ? { color: C.accentInk, label: "productive", sub: "inside the range this app plans for" }
                : v <= lm.mrv ? { color: C.accentInk, label: "high", sub: "near the top of the planned range" }
                    : { color: C.warn, label: "over MRV", sub: "past what this app budgets to recover from" };
        const scale = Math.max(lm.mrv * 1.1, v, 1);
        return (_jsxs("div", { className: "wpb-volume-sub", children: [_jsxs("div", { className: "wpb-volume-sub-heading", children: [_jsx("span", { className: "wpb-volume-sub-label", children: SUBMUSCLE_LABEL[k] }), _jsxs("span", { children: [_jsx("span", { className: "mono wpb-volume-value", style: { color: catInk(zone.color) }, children: fmtSets(v) }), " ", _jsx("span", { className: "wpb-volume-note", children: zone.label })] })] }), _jsxs("div", { className: "wpb-volume-track wpb-volume-track--sub",
            role: "meter", "aria-label": `${SUBMUSCLE_LABEL[k]} weekly training volume`,
            "aria-valuemin": 0, "aria-valuemax": scale, "aria-valuenow": v,
            "aria-valuetext": `${fmtSets(v)} estimated sets, ${zone.label}`, children: [_jsx("div", { className: "wpb-volume-band", style: { left: `${(lm.mev / scale) * 100}%`, width: `${((lm.mrv - lm.mev) / scale) * 100}%` } }), _jsx("div", { className: "wpb-bar", style: { background: zone.color, transform: `translateX(-${100 - Math.min(100, (v / scale) * 100)}%)` } })] })] }, k));
    };
    return (_jsxs(_Fragment, { children: [subVolume && (_jsxs("button", { onClick: () => setGranular(g => !g), "aria-expanded": granular, type: "button", className: "pressable wpb-volume-detail-toggle", children: [_jsx(Layers, { size: 13 }), " ", granular ? "Hide" : "Show", " delt heads & detail"] })), parts.map(p => {
                const v = volume[p] || 0;
                const tgt = target ? (target[p] || 0) : 0;
                const { mev } = landmarkFor(p, program?.config?.experience, program);
                const mrv = compositeMrv(p, program); // composite parts (shoulders) scale to the sum of their heads
                const zone = partZone(p, v, subVolume, program);
                const scale = Math.max(mrv * 1.1, v, tgt); // bar axis tops out past MRV, the bar, and the target
                return (_jsxs("div", { className: "wpb-volume-part", children: [_jsxs(program ? "button" : "div", { "data-vol-part": p, type: program ? "button" : undefined, "aria-expanded": program ? openPart === p : undefined, "aria-label": program ? `${PART_LABEL[p]}: ${fmtSets(v)} weekly sets, ${zone.label}. ${openPart === p ? 'Hide' : 'Show'} contributors` : undefined,
                "aria-controls": program && openPart === p ? `${volumeDetailsId}-${p}` : undefined, onClick: program ? () => setOpenPart(o => o === p ? null : p) : undefined, className: "wpb-volume-part-trigger", children: [_jsxs("span", { className: "wpb-volume-label", children: [PART_LABEL[p], program && _jsx(ChevronDown, { size: 11, className: "wpb-volume-chevron" })] }), _jsxs("span", { children: [_jsx("span", { className: "mono wpb-volume-value", style: { color: catInk(zone.color) }, children: fmtSets(v) }), tgt > 0 && _jsxs("span", { className: "mono wpb-volume-note", children: [" / ", fmtSets(tgt)] }), " ", _jsx("span", { className: "wpb-volume-note", title: tgt > 0 ? undefined : zone.sub, children: tgt > 0 ? "planned" : zone.label })] })] }), _jsxs("div", { className: "wpb-volume-track",
                    role: "meter", "aria-label": `${PART_LABEL[p]} weekly training volume`,
                    "aria-valuemin": 0, "aria-valuemax": scale, "aria-valuenow": v,
                    "aria-valuetext": `${fmtSets(v)} estimated sets, ${zone.label}`, children: [_jsxs("div", { className: "wpb-volume-clip", children: [_jsx("div", { className: "wpb-volume-band", style: { left: `${(mev / scale) * 100}%`, width: `${((mrv - mev) / scale) * 100}%` } }), _jsx("div", { className: "wpb-bar", style: { background: zone.color, transform: `translateX(-${100 - Math.min(100, (v / scale) * 100)}%)` } })] }), _jsx("div", { className: "wpb-volume-marker", style: { left: `${(mev / scale) * 100}%` } }), _jsx("div", { className: "wpb-volume-marker wpb-volume-marker--mrv", style: { left: `${(mrv / scale) * 100}%` } }), tgt > 0 && _jsxs(_Fragment, { children: [_jsx("div", { className: "wpb-volume-target-caret", style: { left: `calc(${(tgt / scale) * 100}% - 3px)` } }), _jsx("div", { className: "wpb-volume-marker wpb-volume-marker--target", style: { left: `${(tgt / scale) * 100}%` } })] })] }), openPart === p && program && (() => {
                            const entry = (weekBreak || []).find(e => e.part === p);
                            const from = (entry && entry.from) || [];
                            if (!from.length)
                                return _jsx("div", { className: "wpb-volume-no-contributors", children: "Nothing in this program trains it this week." });
                            return (_jsxs("div", { id: `${volumeDetailsId}-${p}`, role: "region",
                                "aria-label": `${PART_LABEL[p]} volume contributors`,
                                className: "wpb-volume-contributors", children: [from.map((f, i) => (_jsxs("div", { "data-volume-contributor": true, className: "wpb-volume-contributor", children: [_jsx("span", { className: "wpb-volume-day", children: f.dayLabel }), _jsxs("span", { className: "wpb-volume-exercise", children: [f.name, !f.direct && _jsxs("span", { className: "wpb-volume-assist", children: [" (", Math.round(f.factor * 100), "% assist)"] })] }), _jsxs("span", { className: "mono wpb-volume-credit", "data-direct": !!f.direct, children: ["+", fmtSets(f.contrib)] })] }, i)))] }));
                        })(), granular && subVolume && subUnder[p] && subUnder[p].filter(k => (subVolume[k] || 0) > 0.05 || SUB_LANDMARKS[k]?.mev > 0).map(subBar)] }, p));
            }), _jsxs("div", { className: "wpb-volume-help", children: ["Bars include fractional sets from compounds. The shaded band runs from ", _jsx("span", { className: "wpb-volume-muted", children: "MEV" }), " (minimum effective) to ", _jsx("span", { className: "wpb-volume-warning", children: "MRV" }), " (max recoverable) \u2014 staying inside it is the productive zone.", target ? " The light marker is your active program's planned volume for this week — the gap is what's left to hit." : "", subVolume ? " Expand a region for delt heads, obliques, and other detail — front delts read high because pressing feeds them, so side/rear usually need the direct work." : "", " Landmarks are population estimates and vary by individual."] })] }));
}
export function VolumeRampTable({ program, volumeZone, catInk }) {
    const weeks = weeksOf(program);
    const cols = [];
    for (let w = 1; w <= weeks; w++)
        cols.push({ w, label: `W${w}` });
    if (program.config.deload)
        cols.push({ w: weeks + 1, label: "DL" });
    const vols = cols.map(c => weeklyVolume(program, c.w));
    const parts = PART_ORDER.filter(p => vols.some(v => (v[p] || 0) > 0.05));
    if (!parts.length)
        return null;
    return (_jsxs("div", { className: "wpb-volume-ramp", children: [_jsx("div", { className: "wpb-volume-ramp-title", children: "Volume across the block" }), _jsx("div", { className: "wpb-volume-ramp-copy", children: "Sets per muscle each week \u2014 color shows the MEV \u2192 MRV zone" }), _jsx("div", { className: "wpb-hscroll wpb-volume-ramp-scroll", tabIndex: 0, role: "region", "aria-label": "Muscle volume across the block", children: _jsxs("div", { className: "wpb-volume-ramp-grid", children: [_jsxs("div", { className: "wpb-volume-ramp-heading", children: [_jsx("div", { className: "wpb-volume-ramp-label" }), cols.map(c => (_jsx("div", { className: "wpb-volume-ramp-week", "data-deload": c.label === "DL", children: c.label }, c.w)))] }), parts.map(p => (_jsxs("div", { className: "wpb-volume-ramp-row", children: [_jsx("div", { className: "wpb-volume-ramp-label", children: PART_LABEL[p] }), cols.map((c, ci) => {
                                    const s = vols[ci][p] || 0;
                                    const z = volumeZone(p, s, program);
                                    return (_jsx("div", { className: "wpb-volume-ramp-cell", children: _jsx("div", { className: "mono wpb-volume-ramp-dose", title: `${PART_LABEL[p]} · ${z.label}`,
                                        "aria-label": `${PART_LABEL[p]}, ${c.label}: ${fmtSets(s)} sets, ${z.label}`, style: { background: `${z.color}22`, color: catInk(z.color) }, children: fmtSets(s) }) }, c.w));
                                })] }, p)))] }) }), _jsx("div", { className: "wpb-volume-ramp-legend", children: [["below MEV", "low"], ["productive", "productive"], ["over MRV", "high"]].map(([l, zone]) => (_jsxs("span", { className: "wpb-volume-ramp-key", "data-zone": zone, children: [_jsx("span", { className: "wpb-volume-ramp-swatch" }), l] }, l))) })] }));
}
