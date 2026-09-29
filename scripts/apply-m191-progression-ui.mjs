import fs from 'node:fs';

const path = 'modules/App.js';
let source = fs.readFileSync(path, 'utf8');
const changed = [];

function replaceOnce(label, before, after) {
  if (source.includes(after)) return;
  if (!source.includes(before)) throw new Error(`M191 marker missing: ${label}`);
  source = source.replace(before, after);
  changed.push(label);
}

replaceOnce(
  'wizard progression heading',
  `sub: "Auto applies double-progression / load ramps across the block. Manual lets you set everything yourself.", children: "Sets & reps"`,
  `sub: "Auto chooses the progression that fits each exercise and this block, then re-checks it from your training history. Manual keeps the weekly prescription in your hands.", children: "How should progression work?"`
);
replaceOnce(
  'wizard auto progression option',
  `label: "Auto progressive overload", sub: "We adjust sets & reps each week"`,
  `label: "Auto · choose per exercise", sub: "Pursuit picks the method for each lift and only changes it when your training gives enough evidence"`
);
replaceOnce(
  'wizard manual progression option',
  `label: "Manual", sub: "Fill in your own sets & reps"`,
  `label: "Manual weekly targets", sub: "You set the weekly sets and reps yourself; Auto will not rewrite those targets"`
);
replaceOnce(
  'short block progression copy',
  `[4, "4 weeks", "Classic short block — fast progression"]`,
  `[4, "4 weeks", "Short block — Auto avoids methods that need a longer runway"]`
);
replaceOnce(
  'balanced block progression copy',
  `[6, "6 weeks", "Balanced accumulation"]`,
  `[6, "6 weeks", "Balanced block — more room for advanced progression when it fits"]`
);

replaceOnce(
  'progression selector metadata model',
  `    const weeks = weeksOf(program);\n    const rows = useMemo(() => {`,
  `    const weeks = weeksOf(program);\n    // M191 is a VIEW of the engine-owned M190 selection record. Do not re-run or approximate\n    // progression policy in the UI: the program snapshot already says what was selected and why.\n    const selectedProgressions = Array.isArray(program?.nextEngine?.progressionPlan) ? program.nextEngine.progressionPlan : [];\n    const progressionLabel = (style) => ({\n        double: "Double progression", dynamic: "Dynamic double progression", ladder: "Rep ladder",\n        linear: "Linear progression", wave: "Wave loading", e1rm: "e1RM autoregulation", auto: "Auto"\n    })[style] || String(style || "Auto").replace(/_/g, " ");\n    const progressionGroups = [];\n    const progressionGroupMap = new Map();\n    for (const item of selectedProgressions) {\n        const key = item?.style || "auto";\n        let group = progressionGroupMap.get(key);\n        if (!group) {\n            group = { key, label: progressionLabel(key), items: [] };\n            progressionGroupMap.set(key, group);\n            progressionGroups.push(group);\n        }\n        group.items.push(item);\n    }\n    const adaptiveSelections = selectedProgressions.filter(item => item?.source === "adaptive" || item?.source === "adaptive_hold").length;\n    const manualSelections = selectedProgressions.filter(item => item?.source === "manual").length;\n    const progressionOverview = selectedProgressions.length ? (_jsxs("div", { "data-progression-selection": true, style: { background: C.bg2, borderRadius: 14, padding: "12px 13px", marginBottom: 12 }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }, children: [_jsx(Wand2, { size: 14, color: C.accentInk }), _jsx("span", { style: { fontSize: 13, fontWeight: 750, color: C.text }, children: manualSelections === selectedProgressions.length ? "Your progression choices" : adaptiveSelections ? "Progression re-checked from your training" : "Auto progression for this block" }), _jsx("span", { style: { marginLeft: "auto", fontSize: 11, color: C.faint }, children: adaptiveSelections ? adaptiveSelections + " re-checked" : progressionGroups.length + " method" + (progressionGroups.length === 1 ? "" : "s") })] }), _jsx("div", { style: { fontSize: 13, color: C.muted, lineHeight: 1.5 }, children: manualSelections === selectedProgressions.length ? "These methods are locked by your choices. Auto will not replace them at block review." : adaptiveSelections ? "Auto used comparable workout history before keeping or changing each method. One unusual workout is not enough to switch it." : "Pursuit chose per exercise from the lift, rep target, loading setup, your experience, phase, and block length. It can re-check the method between blocks once enough comparable training exists." }), _jsx("div", { style: { display: "flex", gap: 6, flexWrap: "wrap", marginTop: 9 }, children: progressionGroups.map(group => (_jsxs("span", { style: { display: "inline-flex", alignItems: "center", gap: 5, padding: "6px 9px", borderRadius: 10, background: C.card, color: C.text, fontSize: 12, fontWeight: 650 }, children: [_jsx("span", { children: group.label }), _jsx("span", { className: "mono", style: { color: C.accentInk, fontWeight: 750 }, children: group.items.length })] }, group.key))) }), _jsxs("details", { style: { marginTop: 10 }, children: [_jsx("summary", { style: { cursor: "pointer", color: C.accentInk, fontSize: 12, fontWeight: 700, listStylePosition: "inside" }, children: "Why these methods?" }), _jsx("div", { style: { marginTop: 7 }, children: selectedProgressions.map((item, index) => (_jsxs("div", { "data-progression-reason": true, style: { padding: "8px 0", borderTop: "1px solid " + C.borderSoft }, children: [_jsxs("div", { style: { display: "flex", alignItems: "baseline", gap: 8 }, children: [_jsx("span", { style: { flex: 1, minWidth: 0, color: C.text, fontSize: 13, fontWeight: 650, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }, children: item.exerciseName || "Exercise" }), _jsx("span", { style: { flexShrink: 0, color: C.accentInk, fontSize: 11, fontWeight: 700 }, children: progressionLabel(item.style) })] }), _jsx("div", { style: { marginTop: 2, color: C.muted, fontSize: 12, lineHeight: 1.45 }, children: item.reason || "Auto selected the progression that best fits this exercise and block." })] }, String(item.exerciseId || item.exerciseName || "exercise") + ":" + index))) })] })] })) : null;\n    const rows = useMemo(() => {`
);

replaceOnce(
  'progression overview render',
  `children: _jsxs("div", { style: { paddingTop: 4 }, children: [_jsx("input", { value: q, onChange: e => setQ(e.target.value),`,
  `children: _jsxs("div", { style: { paddingTop: 4 }, children: [progressionOverview, _jsx("input", { value: q, onChange: e => setQ(e.target.value),`
);

replaceOnce(
  'plan info copy',
  `children: "How this program was put together, what it trains each week, and the arc it follows."`,
  `children: "How this program was put together, how progression was chosen, what it trains each week, and the arc it follows."`
);
replaceOnce(
  'plan info progression card',
  `_jsx(SetupCard, { program: program, open: showSetup, onToggle: () => setShowSetup(v => !v) }), _jsx(VolumeCard, { history: history, volume: wkVol, subVolume: wkSubVol, program: program, onApplyFix: applyVolumeFix, open: showVol, onToggle: () => setShowVol(v => !v), week: activeWeek })`,
  `_jsx(SetupCard, { program: program, open: showSetup, onToggle: () => setShowSetup(v => !v) }), _jsx(ProgressionCard, { program: program, open: showProg, onToggle: () => setShowProg(v => !v) }), _jsx(VolumeCard, { history: history, volume: wkVol, subVolume: wkSubVol, program: program, onApplyFix: applyVolumeFix, open: showVol, onToggle: () => setShowVol(v => !v), week: activeWeek })`
);

fs.writeFileSync(path, source);
console.log(changed.length ? `M191 progression UI applied: ${changed.join(', ')}` : 'M191 progression UI already applied.');
