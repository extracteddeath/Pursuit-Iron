// Single presentation owner for weekly muscle-volume bars and the block ramp.
// Numbers and thresholds come from the canonical engine and App callbacks;
// no saved state, program mutation, or duplicated prescription calculation.
import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from 'react/jsx-runtime';
import { useState, useMemo } from 'react';
import { Layers, ChevronDown } from 'lucide-react';
import {
    PART_LABEL, PART_ORDER, SUBMUSCLE_LABEL, SUB_LANDMARKS, fmtSets,
    landmarkFor, compositeMrv, weekMuscleBreakdown, weeklyVolume, weeksOf
} from './engine-api.js';

export function VolumeBars({ volume, subVolume, target, program = null, week = 0, palette: C, partZone, catInk }) {
    const [openPart, setOpenPart] = useState(null);
    const weekBreak = useMemo(() => (program ? weekMuscleBreakdown(program, week) : null), [program, week]);
    const [granular, setGranular] = useState(false);
    const parts = PART_ORDER.filter(p => (volume[p] || 0) > 0.05 || (target && (target[p] || 0) > 0.05));
    if (!parts.length)
        return _jsx("div", { style: { fontSize: 13, color: C.muted, padding: "6px 2px" }, children: "No volume logged yet." });
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
        return (_jsxs("div", { style: { marginTop: 6, paddingLeft: 12, borderLeft: `2px solid ${C.border}` }, children: [_jsxs("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "baseline", fontSize: 13, marginBottom: 2 }, children: [_jsx("span", { style: { color: C.muted, fontWeight: 500 }, children: SUBMUSCLE_LABEL[k] }), _jsxs("span", { children: [_jsx("span", { className: "mono", style: { color: catInk(zone.color), fontWeight: 600 }, children: fmtSets(v) }), " ", _jsx("span", { style: { fontSize: 11, color: C.faint }, children: zone.label })] })] }), _jsxs("div", { style: { position: "relative", height: 6, borderRadius: 999, background: C.bg2, overflow: "hidden" }, children: [_jsx("div", { style: { position: "absolute", left: `${(lm.mev / scale) * 100}%`, width: `${((lm.mrv - lm.mev) / scale) * 100}%`, top: 0, bottom: 0, background: C.accent, opacity: 0.13 } }), _jsx("div", { className: "wpb-bar", style: { background: zone.color, transform: `translateX(-${100 - Math.min(100, (v / scale) * 100)}%)` } })] })] }, k));
    };
    return (_jsxs(_Fragment, { children: [subVolume && (_jsxs("button", { onClick: () => setGranular(g => !g), "aria-expanded": granular, className: "pressable", style: { display: "flex", alignItems: "center", gap: 6, marginTop: 8, padding: "6px 10px", borderRadius: 8, border: `1px solid ${C.border}`, background: granular ? C.accentDim : C.card, color: granular ? C.accentInk : C.muted, fontSize: 13, fontWeight: 600, cursor: "pointer" }, children: [_jsx(Layers, { size: 13 }), " ", granular ? "Hide" : "Show", " delt heads & detail"] })), parts.map(p => {
                const v = volume[p] || 0;
                const tgt = target ? (target[p] || 0) : 0;
                const { mev } = landmarkFor(p, program?.config?.experience, program);
                const mrv = compositeMrv(p, program); // composite parts (shoulders) scale to the sum of their heads
                const zone = partZone(p, v, subVolume, program);
                const scale = Math.max(mrv * 1.1, v, tgt); // bar axis tops out past MRV, the bar, and the target
                return (_jsxs("div", { style: { marginTop: 12 }, children: [_jsxs(program ? "button" : "div", { "data-vol-part": p, type: program ? "button" : undefined, "aria-expanded": program ? openPart === p : undefined, "aria-label": program ? `Volume contributors for ${PART_LABEL[p]}` : undefined, onClick: program ? () => setOpenPart(o => o === p ? null : p) : undefined, className: "wpb-volume-part-trigger", children: [_jsxs("span", { style: { color: C.text, fontWeight: 500, display: "inline-flex", alignItems: "center", gap: 4 }, children: [PART_LABEL[p], program && _jsx(ChevronDown, { size: 11, color: C.faint, style: { transform: openPart === p ? "rotate(180deg)" : "none", transition: "transform .15s" } })] }), _jsxs("span", { children: [_jsx("span", { className: "mono", style: { color: catInk(zone.color), fontWeight: 600 }, children: fmtSets(v) }), tgt > 0 && _jsxs("span", { className: "mono", style: { fontSize: 11, color: C.faint }, children: [" / ", fmtSets(tgt)] }), " ", _jsx("span", { style: { fontSize: 11, color: C.faint }, title: tgt > 0 ? undefined : zone.sub, children: tgt > 0 ? "planned" : zone.label })] })] }), _jsxs("div", { style: { position: "relative", height: 9, borderRadius: 999, background: C.bg2, overflow: "visible" }, children: [_jsxs("div", { style: { position: "absolute", inset: 0, borderRadius: 999, overflow: "hidden" }, children: [_jsx("div", { style: { position: "absolute", left: `${(mev / scale) * 100}%`, width: `${((mrv - mev) / scale) * 100}%`, top: 0, bottom: 0, background: C.accent, opacity: 0.13 } }), _jsx("div", { className: "wpb-bar", style: { background: zone.color, transform: `translateX(-${100 - Math.min(100, (v / scale) * 100)}%)` } })] }), _jsx("div", { style: { position: "absolute", left: `${(mev / scale) * 100}%`, top: -1, bottom: -1, width: 2, background: C.faint, opacity: 0.6 } }), _jsx("div", { style: { position: "absolute", left: `${(mrv / scale) * 100}%`, top: -1, bottom: -1, width: 2, background: C.warn, opacity: 0.7 } }), tgt > 0 && _jsxs(_Fragment, { children: [_jsx("div", { style: { position: "absolute", left: `calc(${(tgt / scale) * 100}% - 3px)`, top: -7, width: 0, height: 0, borderLeft: "3px solid transparent", borderRight: "3px solid transparent", borderTop: `5px solid ${C.text}` } }), _jsx("div", { style: { position: "absolute", left: `${(tgt / scale) * 100}%`, top: -2, bottom: -2, width: 2, background: C.text, opacity: 0.85 } })] })] }), openPart === p && program && (() => {
                            const entry = (weekBreak || []).find(e => e.part === p);
                            const from = (entry && entry.from) || [];
                            if (!from.length)
                                return _jsx("div", { style: { fontSize: 13, color: C.faint, padding: "8px 0 2px" }, children: "Nothing in this program trains it this week." });
                            return (_jsxs("div", { style: { marginTop: 8, background: C.bg2, borderRadius: 12, padding: "8px 10px" }, children: [from.map((f, i) => (_jsxs("div", { "data-volume-contributor": true, style: { display: "flex", alignItems: "flex-start", gap: 8, padding: "4px 0", fontSize: 13 }, children: [_jsx("span", { style: { color: C.faint, flex: "0 0 72px", overflowWrap: "anywhere" }, children: f.dayLabel }), _jsxs("span", { style: { flex: 1, minWidth: 0, color: C.text, fontWeight: 500, overflowWrap: "anywhere" }, children: [f.name, !f.direct && _jsxs("span", { style: { color: C.faint, fontWeight: 400 }, children: [" (", Math.round(f.factor * 100), "% assist)"] })] }), _jsxs("span", { className: "mono", style: { flexShrink: 0, color: f.direct ? C.accentInk : C.muted, fontWeight: 600 }, children: ["+", fmtSets(f.contrib)] })] }, i)))] }));
                        })(), granular && subVolume && subUnder[p] && subUnder[p].filter(k => (subVolume[k] || 0) > 0.05 || SUB_LANDMARKS[k]?.mev > 0).map(subBar)] }, p));
            }), _jsxs("div", { style: { fontSize: 11, color: C.faint, marginTop: 12, lineHeight: 1.5 }, children: ["Bars include fractional sets from compounds. The shaded band runs from ", _jsx("span", { style: { color: C.muted }, children: "MEV" }), " (minimum effective) to ", _jsx("span", { style: { color: C.warn }, children: "MRV" }), " (max recoverable) \u2014 staying inside it is the productive zone.", target ? " The light marker is your active program's planned volume for this week — the gap is what's left to hit." : "", subVolume ? " Expand a region for delt heads, obliques, and other detail — front delts read high because pressing feeds them, so side/rear usually need the direct work." : "", " Landmarks are population estimates and vary by individual."] })] }));
}
export function VolumeRampTable({ program, palette: C, volumeZone, catInk, eyebrow }) {
    const weeks = weeksOf(program);
    const cols = [];
    for (let w = 1; w <= weeks; w++)
        cols.push({ w, label: `W${w}` });
    if (program.config.deload)
        cols.push({ w: weeks + 1, label: "DL" });
    const vols = cols.map(c => weeklyVolume(program, c.w));
    const parts = PART_ORDER.filter(p => vols.some(v => (v[p] || 0) > 0.05));
    const CW = 30;
    if (!parts.length)
        return null;
    return (_jsxs("div", { style: { marginTop: 16, paddingTop: 12, borderTop: `1px solid ${C.borderSoft}` }, children: [_jsx("div", { style: { ...eyebrow(), marginBottom: 2 }, children: "Volume across the block" }), _jsx("div", { style: { fontSize: 13, color: C.muted, marginBottom: 8 }, children: "Sets per muscle each week \u2014 color shows the MEV \u2192 MRV zone" }), _jsx("div", { className: "wpb-hscroll", style: { overflowX: "auto", overflowY: "hidden", touchAction: "pan-x pan-y" }, children: _jsxs("div", { style: { display: "inline-block", minWidth: "100%" }, children: [_jsxs("div", { style: { display: "flex", marginBottom: 6 }, children: [_jsx("div", { style: { width: 72, flexShrink: 0 } }), cols.map(c => (_jsx("div", { style: { width: CW, textAlign: "center", fontSize: 11, fontWeight: 700, color: c.label === "DL" ? C.muted : C.faint }, children: c.label }, c.w)))] }), parts.map(p => (_jsxs("div", { style: { display: "flex", alignItems: "center", marginBottom: 4 }, children: [_jsx("div", { style: { width: 72, flexShrink: 0, fontSize: 13, fontWeight: 500, color: C.text, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", paddingRight: 4 }, children: PART_LABEL[p] }), cols.map((c, ci) => {
                                    const s = vols[ci][p] || 0;
                                    const z = volumeZone(p, s, program);
                                    return (_jsx("div", { style: { width: CW, display: "flex", justifyContent: "center" }, children: _jsx("div", { className: "mono", title: `${PART_LABEL[p]} · ${z.label}`, style: { width: 25, height: 22, borderRadius: 8, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 11, fontWeight: 600, background: `${z.color}22`, color: catInk(z.color) }, children: fmtSets(s) }) }, c.w));
                                })] }, p)))] }) }), _jsx("div", { style: { display: "flex", gap: 12, marginTop: 8, fontSize: 11, color: C.muted }, children: [["below MEV", C.muted], ["productive", C.accent], ["over MRV", C.warn]].map(([l, col]) => (_jsxs("span", { style: { display: "flex", alignItems: "center", gap: 4 }, children: [_jsx("span", { style: { width: 9, height: 9, borderRadius: 8, background: `${col}33`, border: `1px solid ${col}` } }), l] }, l))) })] }));
}
