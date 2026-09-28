import fs from 'node:fs';
const path = process.argv[2] || 'modules/App.js';
let s = fs.readFileSync(path,'utf8');
function replaceBetween(startNeedle,endNeedle,replacement,label){
  const a=s.indexOf(startNeedle); if(a<0) throw new Error(`Missing ${label} start`);
  const b=s.indexOf(endNeedle,a); if(b<0) throw new Error(`Missing ${label} end`);
  if(s.indexOf(startNeedle,a+1)>=0) throw new Error(`Non-unique ${label} start`);
  s=s.slice(0,a)+replacement+s.slice(b);
}

const review = `function LegacyRebuildReview({ review, onClose, onConfirm, onRemove }) {
    const old = review.original, next = review.program;
    const [removeConfirm, setRemoveConfirm] = useState(false);
    const removeActions = removeConfirm
        ? _jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap", padding: "10px 12px", borderRadius: 12, background: C.dangerDim }, children: [_jsx("div", { style: { flex: "1 1 150px", minWidth: 0, fontSize: 13, color: C.text, fontWeight: 650 }, children: "Remove this saved copy? Workout history will stay." }), _jsx("button", { type: "button", className: "pressable", onClick: () => setRemoveConfirm(false), style: { minHeight: 40, padding: "7px 11px", borderRadius: 10, border: \`1px solid \${C.border}\`, background: C.card, color: C.text, fontWeight: 700 }, children: "Cancel" }), _jsx("button", { type: "button", className: "pressable wpb-confirm-danger", onClick: () => onRemove?.(old.id), style: { minHeight: 40, padding: "7px 11px", borderRadius: 10, border: 0, background: C.danger, color: C.dangerText, fontWeight: 800 }, children: "Remove" })] })
        : _jsxs("div", { style: { display: "grid", gridTemplateColumns: "minmax(0,1fr) minmax(0,1.15fr)", gap: 8 }, children: [_jsx("button", { type: "button", onClick: onClose, className: "pressable", style: { minHeight: 46, borderRadius: 12, border: \`1px solid \${C.border}\`, background: C.card, color: C.text, fontWeight: 750 }, children: "Not now" }), _jsx("button", { type: "button", onClick: onConfirm, className: "pressable", style: { minHeight: 46, borderRadius: 12, border: 0, background: C.accent, color: C.accentText, fontWeight: 800 }, children: "Restore to library" }), _jsx("button", { type: "button", onClick: () => setRemoveConfirm(true), className: "pressable", style: { gridColumn: "1 / -1", minHeight: 42, borderRadius: 11, border: \`1px solid \${C.danger}55\`, background: "transparent", color: C.danger, fontWeight: 700 }, children: "Remove saved plan" })] });
    return _jsx("div", { role: "dialog", "aria-modal": "true", "aria-label": "Restore saved plan", className: "wpb-backdrop", onClick: onClose, style: { position: "absolute", inset: 0, zIndex: 120, background: "rgba(0,0,0,.68)", display: "flex", alignItems: "flex-end", justifyContent: "center", padding: "max(8px, env(safe-area-inset-top)) 8px max(8px, env(safe-area-inset-bottom))" }, children: _jsxs("div", { onClick: e => e.stopPropagation(), style: { width: "100%", maxWidth: 540, maxHeight: "100%", minHeight: 0, display: "flex", flexDirection: "column", background: C.bg, color: C.text, borderRadius: 20, overflow: "hidden", boxShadow: "0 -12px 40px rgba(0,0,0,.35)" }, children: [_jsxs("div", { style: { flexShrink: 0, padding: "14px 14px 10px 18px", display: "flex", alignItems: "center", gap: 12, borderBottom: \`1px solid \${C.borderSoft}\` }, children: [_jsx("div", { "data-dialog-title": true, style: { flex: 1, fontSize: 19, fontWeight: 750 }, children: "Restore saved plan" }), _jsx("button", { "aria-label": "Close restore preview", onClick: onClose, className: "pressable hit", style: { ...iconBtn(), minWidth: 44, minHeight: 44, flexShrink: 0 }, children: _jsx(X, { size: 20 }) })] }), _jsxs("div", { style: { flexShrink: 0, padding: "12px 18px 14px", borderBottom: \`1px solid \${C.borderSoft}\`, background: C.bg }, children: [_jsx("div", { style: { fontSize: 17, fontWeight: 700, overflowWrap: "anywhere" }, children: next.name }), _jsx("p", { style: { color: C.muted, fontSize: 13, lineHeight: 1.45, margin: "6px 0 10px" }, children: "Restore this saved plan using the current program engine. The name and workout history stay attached, and a full copy of the original plan is preserved." }), removeActions] }), _jsxs("div", { className: "wpb-scroll", style: { flex: "1 1 auto", minHeight: 0, overflowY: "auto", overscrollBehavior: "contain", padding: "14px 18px calc(18px + env(safe-area-inset-bottom))" }, children: [old.cycleId && _jsx("div", { role: "note", style: { padding: "10px 12px", marginBottom: 12, borderRadius: 12, background: C.accentDim, color: C.text, fontSize: 13, lineHeight: 1.45 }, children: "This plan came from an older training cycle. It will be restored as a standalone program so the archived cycle itself is not changed." }), _jsxs("div", { style: { padding: 12, borderRadius: 12, background: C.card, fontSize: 14 }, children: [old.days?.length || 0, " → ", next.days.length, " training days per week · ", next.config?.weeks || 4, " weeks"] }), next.days.map((day, i) => _jsxs("details", { open: i === 0, style: { borderBottom: \`1px solid \${C.border}\`, padding: "10px 0" }, children: [_jsxs("summary", { style: { padding: "8px 0", fontWeight: 700, cursor: "pointer" }, children: [day.label, " · ", day.exercises.length, " exercises"] }), day.exercises.map((id, slot) => { const cell = getNextShellCell(next, day, slot, 1); return _jsxs("div", { style: { display: "flex", gap: 12, padding: "7px 0", fontSize: 14 }, children: [_jsx("span", { style: { flex: 1, minWidth: 0 }, children: EX_BY_ID[id]?.name || id }), _jsxs("span", { style: { color: C.muted, flexShrink: 0 }, children: [cell?.sets ?? "—", " × ", cell?.reps?.join?.("–") || cell?.range || "—"] })] }, \`\${id}:\${slot}\`); })] }, day.id)), _jsxs("details", { style: { marginTop: 14 }, children: [_jsx("summary", { style: { cursor: "pointer", padding: "8px 0", fontWeight: 700 }, children: "Original saved plan" }), (old.days || []).map(day => _jsxs("div", { style: { fontSize: 13, color: C.muted, lineHeight: 1.6, marginTop: 10 }, children: [_jsx("strong", { children: day.label }), _jsx("br", {}), (day.exercises || []).map(id => EX_BY_ID[id]?.name || id).join(" · ")] }, day.id))] })] })] }) });
}
`;
replaceBetween('function LegacyRebuildReview({ review, onClose, onConfirm }) {','function ExerciseAnimation(',review,'LegacyRebuildReview');

const card = `function LegacyProgramsCard({ programs = [], history = [], onRebuild, onRemove }) {
    const [msg, setMsg] = useState({});
    const [busy, setBusy] = useState(null);
    const [collapsed, setCollapsed] = useState(false);
    const [removeConfirm, setRemoveConfirm] = useState(null);
    if (!programs.length)
        return null;
    const logged = (id) => history.filter(h => h && h.programId === id).length;
    if (collapsed)
        return (_jsxs("div", { "data-testid": "legacy-programs-collapsed", style: { display: "flex", alignItems: "center", gap: 10, background: C.card, border: \`1px solid \${C.border}\`, borderRadius: 14, padding: "10px 12px", marginBottom: 14 }, children: [_jsx(History, { size: 16, color: C.accentInk, style: { flexShrink: 0 } }), _jsxs("div", { style: { flex: 1, minWidth: 0, fontSize: 13, color: C.muted }, children: [_jsx("strong", { style: { color: C.text }, children: "Previous-version plans" }), " · ", programs.length, " saved"] }), _jsx("button", { type: "button", className: "pressable", onClick: () => setCollapsed(false), style: { border: 0, background: "transparent", color: C.accentInk, fontSize: 13, fontWeight: 800, cursor: "pointer", padding: "7px 4px" }, children: "Show" })] }));
    return (_jsxs("div", { "data-testid": "legacy-programs", style: { background: C.card, border: \`1px solid \${C.border}\`, borderRadius: 16, padding: 16, marginBottom: 16 }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 10 }, children: [_jsx("div", { style: { flex: 1, fontSize: 15, fontWeight: 800, color: C.text }, children: "Saved plans from previous version" }), _jsx("button", { type: "button", className: "pressable hit", "aria-label": "Hide saved plans from previous version", title: "Hide for now", onClick: () => setCollapsed(true), style: { ...iconBtn(), width: 40, height: 40, minWidth: 40, minHeight: 40, flexShrink: 0 }, children: _jsx(X, { size: 18 }) })] }), _jsx("div", { style: { fontSize: 13, color: C.muted, lineHeight: 1.5, marginTop: 4, marginBottom: 12 }, children: "Restore a saved plan, hide this section for now, or remove a saved copy you no longer want listed. Removing a saved copy does not delete workout history." }), programs.map(p => (_jsxs("div", { style: { borderTop: \`1px solid \${C.border}\`, paddingTop: 12, marginTop: 12 }, children: [_jsx("div", { style: { fontSize: 14, fontWeight: 700, color: C.text, overflowWrap: "anywhere" }, children: p.name || "Untitled program" }), _jsxs("div", { style: { fontSize: 12, color: C.muted, marginTop: 2 }, children: [(p.days || []).length, " days a week · ", logged(p.id), " workout", logged(p.id) === 1 ? "" : "s", " logged", p.cycleId ? " · older cycle block" : ""] }), removeConfirm === p.id ? _jsxs("div", { style: { marginTop: 10, padding: "10px 11px", borderRadius: 12, background: C.dangerDim, display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }, children: [_jsx("div", { style: { flex: "1 1 150px", fontSize: 12, color: C.text, lineHeight: 1.4 }, children: "Remove this saved plan from the restore list? Workout history stays." }), _jsx("button", { type: "button", className: "pressable", onClick: () => setRemoveConfirm(null), style: { minHeight: 40, padding: "7px 10px", borderRadius: 10, border: \`1px solid \${C.border}\`, background: C.card, color: C.text, fontWeight: 700 }, children: "Cancel" }), _jsx("button", { type: "button", className: "pressable wpb-confirm-danger", onClick: () => { onRemove?.(p.id); setRemoveConfirm(null); }, style: { minHeight: 40, padding: "7px 10px", borderRadius: 10, border: 0, background: C.danger, color: C.dangerText, fontWeight: 800 }, children: "Remove" })] }) : _jsxs("div", { style: { display: "grid", gridTemplateColumns: "minmax(0,1fr) minmax(0,1.2fr)", gap: 8, marginTop: 10 }, children: [_jsxs("button", { type: "button", className: "pressable", onClick: () => setRemoveConfirm(p.id), style: { minHeight: 44, borderRadius: 11, border: \`1px solid \${C.danger}44\`, background: "transparent", color: C.danger, fontSize: 13, fontWeight: 700, display: "flex", alignItems: "center", justifyContent: "center", gap: 5 }, children: [_jsx(Trash2, { size: 14 }), " Remove"] }), _jsx("button", { type: "button", className: "pressable", "data-testid": "legacy-rebuild", disabled: busy !== null, "aria-busy": busy === p.id, onClick: async () => { if (busy !== null)
                                    return; setBusy(p.id); await new Promise(resolve => setTimeout(resolve, 40)); try {
                                    const r = onRebuild ? await onRebuild(p.id) : { ok: false, msg: "Restore preview is unavailable." };
                                    if (!r.ok)
                                        setMsg(m => ({ ...m, [p.id]: r.msg }));
                                }
                                catch {
                                    setMsg(m => ({ ...m, [p.id]: "Could not prepare the restore preview. Your saved plan is unchanged. Try again." }));
                                }
                                finally {
                                    setBusy(null);
                                } }, style: { minHeight: 44, borderRadius: 11, border: "none", background: C.accent, color: C.accentText, fontSize: 13, fontWeight: 800, cursor: busy !== null ? "default" : "pointer", opacity: busy !== null && busy !== p.id ? 0.65 : 1 }, children: busy === p.id ? "Preparing…" : "Restore" })] }), p.cycleId && _jsx("div", { style: { fontSize: 12, color: C.muted, lineHeight: 1.45, marginTop: 8 }, children: "Older cycle block · restore as a standalone program; the archived cycle stays untouched." }), msg[p.id] && _jsx("div", { role: "status", style: { fontSize: 12, color: C.muted, marginTop: 8 }, children: msg[p.id] })] }, p.id)))] }));
}
`;
replaceBetween('function LegacyProgramsCard({ programs = [], history = [], onRebuild }) {','function Home(',card,'LegacyProgramsCard');

s=s.replace('function Home({ legacySaved = [], onRebuildLegacy, gyms = []','function Home({ legacySaved = [], onRebuildLegacy, onRemoveLegacy, gyms = []');
if(!s.includes('function Home({ legacySaved = [], onRebuildLegacy, onRemoveLegacy, gyms = []')) throw new Error('Home prop patch failed');

const oldCardCall='_jsx(LegacyProgramsCard, { programs: legacySaved, history: history, onRebuild: onRebuildLegacy })';
const newCardCall='_jsx(LegacyProgramsCard, { programs: legacySaved, history: history, onRebuild: onRebuildLegacy, onRemove: onRemoveLegacy })';
if(!s.includes(oldCardCall)) throw new Error('Legacy card call not found');
s=s.replace(oldCardCall,newCardCall);

const handlerNeedle='    const rebuildLegacyProgram = (id) => {';
const handlerInsert=`    const removeLegacyProgram = (id) => {\n        const old = (legacyStore.saved || []).find(x => x && x.id === id);\n        if (!old)\n            return;\n        setLegacyStore(st => ({ ...st, saved: (st.saved || []).filter(x => x && x.id !== id) }));\n        if (legacyReview?.original?.id === id)\n            setLegacyReview(null);\n        setAppToast({ msg: \`Removed \${old.name || "saved plan"} from previous-version plans. Workout history was kept.\` });\n    };\n`;
if(!s.includes(handlerNeedle)) throw new Error('rebuild handler location missing');
s=s.replace(handlerNeedle,handlerInsert+handlerNeedle);

const homeCall='legacySaved: legacyStore.saved, onRebuildLegacy: rebuildLegacyProgram,';
if(!s.includes(homeCall)) throw new Error('Home invocation target missing');
s=s.replace(homeCall,'legacySaved: legacyStore.saved, onRebuildLegacy: rebuildLegacyProgram, onRemoveLegacy: removeLegacyProgram,');

const reviewCall='legacyReview && _jsx(LegacyRebuildReview, { review: legacyReview, onClose: () => setLegacyReview(null), onConfirm: confirmLegacyRebuild })';
if(!s.includes(reviewCall)) throw new Error('review invocation target missing');
s=s.replace(reviewCall,'legacyReview && _jsx(LegacyRebuildReview, { review: legacyReview, onClose: () => setLegacyReview(null), onConfirm: confirmLegacyRebuild, onRemove: removeLegacyProgram })');

const checks=[
 'Remove saved plan',
 'Removed ${old.name || "saved plan"} from previous-version plans. Workout history was kept.',
 'onRemoveLegacy: removeLegacyProgram',
 'onRemove: removeLegacyProgram',
 'position: "absolute", inset: 0, zIndex: 120',
 'Restore to library'
];
for(const x of checks) if(!s.includes(x)) throw new Error('Missing post-patch assertion: '+x);
fs.writeFileSync(path,s);
console.log('legacy restore action patch OK');
