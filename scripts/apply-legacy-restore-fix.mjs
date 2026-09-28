import fs from 'node:fs';

const path = 'modules/App.js';
let s = fs.readFileSync(path, 'utf8');

function replaceExact(oldText, newText, label) {
  if (!s.includes(oldText)) throw new Error(`Patch target not found: ${label}`);
  if (s.indexOf(oldText) !== s.lastIndexOf(oldText)) throw new Error(`Patch target not unique: ${label}`);
  s = s.replace(oldText, newText);
}

const reviewStart = s.indexOf('function LegacyRebuildReview');
const reviewEnd = s.indexOf('function ExerciseAnimation', reviewStart);
if (reviewStart < 0 || reviewEnd < 0) throw new Error('LegacyRebuildReview boundaries not found');
const reviewOld = s.slice(reviewStart, reviewEnd);
let reviewNew = reviewOld
  .replaceAll('Review updated plan', 'Restore saved plan')
  .replace('Close plan review', 'Close restore preview')
  .replace('Exercises and targets may change. Review the updated days below. Saving keeps your workout history and a copy of the original plan.', 'This brings the saved plan back into your normal program library using the current program engine. Exercises or targets may change, but the program name, workout history, and a full copy of the original plan are preserved.')
  .replace('children: "Original plan"', 'children: "Original saved plan"')
  .replace('children: "Keep original"', 'children: "Not now"')
  .replace('children: "Save updated plan"', 'children: "Restore to library"');
const reviewNeedle = '_jsx("p", { style: { color: C.muted, fontSize: 14, lineHeight: 1.5 }, children: "This brings the saved plan back into your normal program library using the current program engine. Exercises or targets may change, but the program name, workout history, and a full copy of the original plan are preserved." }), ';
if (!reviewNew.includes(reviewNeedle)) throw new Error('Restore review paragraph not found after relabel');
reviewNew = reviewNew.replace(reviewNeedle, reviewNeedle + 'old.cycleId && _jsx("div", { role: "note", style: { padding: "10px 12px", marginBottom: 12, borderRadius: 12, background: C.accentDim, color: C.text, fontSize: 13, lineHeight: 1.45 }, children: "This plan came from an older training cycle. It will be restored as a standalone program so the archived cycle itself is not changed." }), ');
s = s.slice(0, reviewStart) + reviewNew + s.slice(reviewEnd);

const cardStart = s.indexOf('function LegacyProgramsCard');
const cardEnd = s.indexOf('function Home', cardStart);
if (cardStart < 0 || cardEnd < 0) throw new Error('LegacyProgramsCard boundaries not found');
let card = s.slice(cardStart, cardEnd);
card = card.replace(
  '    const [busy, setBusy] = useState(null);\n    if (!programs.length)\n        return null;\n    const logged = (id) => history.filter(h => h && h.programId === id).length;\n',
  '    const [busy, setBusy] = useState(null);\n    const [collapsed, setCollapsed] = useState(false);\n    if (!programs.length)\n        return null;\n    const logged = (id) => history.filter(h => h && h.programId === id).length;\n    if (collapsed)\n        return (_jsxs("div", { "data-testid": "legacy-programs-collapsed", style: { display: "flex", alignItems: "center", gap: 10, background: C.card, border: `1px solid ${C.border}`, borderRadius: 14, padding: "10px 12px", marginBottom: 14 }, children: [_jsx(History, { size: 16, color: C.accentInk, style: { flexShrink: 0 } }), _jsxs("div", { style: { flex: 1, minWidth: 0, fontSize: 13, color: C.muted }, children: [_jsx("strong", { style: { color: C.text }, children: "Previous-version plans" }), " · ", programs.length, " saved"] }), _jsx("button", { type: "button", className: "pressable", onClick: () => setCollapsed(false), style: { border: 0, background: "transparent", color: C.accentInk, fontSize: 13, fontWeight: 800, cursor: "pointer", padding: "7px 4px" }, children: "Show" })] }));\n'
);
card = card.replace(
  '_jsx("div", { style: { fontSize: 15, fontWeight: 800, color: C.text }, children: "Saved plans to update" })',
  '_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 10 }, children: [_jsx("div", { style: { flex: 1, fontSize: 15, fontWeight: 800, color: C.text }, children: "Saved plans from previous version" }), _jsx("button", { type: "button", className: "pressable", "aria-label": "Collapse saved plans from previous version", title: "Hide for now", onClick: () => setCollapsed(true), style: { ...iconBtn(), width: 36, height: 36, minWidth: 36, minHeight: 36, flexShrink: 0 }, children: _jsx(X, { size: 17 }) })] })'
);
card = card.replace('These plans are from an earlier app version. Preview an updated plan before saving it. Your original plan and logged workouts are kept.', 'These plans were saved by an earlier app version. Restore any one to your normal program library after reviewing its current-engine version. Workout history stays attached, and a complete copy of the original plan is retained.');
card = card.replace('!p.cycleId && (_jsx("button",', '_jsx("button",');
card = card.replace('children: busy === p.id ? "Preparing…" : "Preview update" }))', 'children: busy === p.id ? "Preparing…" : "Restore" })');
card = card.replace('const r = onRebuild ? await onRebuild(p.id) : { ok: false, msg: "Preview is unavailable." };', 'const r = onRebuild ? await onRebuild(p.id) : { ok: false, msg: "Restore preview is unavailable." };');
card = card.replace('Could not prepare a preview. Your original plan is unchanged. Try again.', 'Could not prepare the restore preview. Your original plan is unchanged. Try again.');
card = card.replace('This belongs to an earlier training cycle. Create a new cycle to continue; this plan remains saved with its workout history.', 'This was part of an older cycle. Restoring brings this block back as a standalone program; the archived cycle itself stays untouched.');
card = card.replace('p.cycleId ? " · part of a cycle" : ""', 'p.cycleId ? " · older cycle block" : ""');
if (!card.includes('Saved plans from previous version') || !card.includes('children: busy === p.id ? "Preparing…" : "Restore"')) throw new Error('LegacyProgramsCard patch assertions failed');
s = s.slice(0, cardStart) + card + s.slice(cardEnd);

{
  const re = /        if \(old\.cycleId\)\n            return \{ ok: false, msg: "This program is part of an older training cycle\.[^\n]+\n/;
  const matches = s.match(new RegExp(re.source, 'g')) || [];
  if (matches.length !== 1) throw new Error(`Expected one legacy cycle restore block, found ${matches.length}`);
  s = s.replace(re, '');
}
replaceExact(
  '        setAppToast({ msg: `Updated ${p.name}. Your original plan is included in backups.` });',
  '        setAppToast({ msg: `Restored ${p.name} to your programs. Your original plan is still included in backups.` });',
  'restore toast'
);
replaceExact(
  '       previous-engine CYCLE are not rebuilt one block at a time: that would orphan the block from its cycle. */',
  '       previous-engine cycle block is restored as a standalone current program. The original block remains embedded in\n       `rebuiltFrom.program`, so the archived cycle is never executed or mutated by the current engine. */',
  'legacy rebuild contract comment'
);

const required = [
  'Saved plans from previous version',
  'Collapse saved plans from previous version',
  'Previous-version plans',
  'Restore to library',
  'This was part of an older cycle. Restoring brings this block back as a standalone program',
  'Restored ${p.name} to your programs'
];
for (const needle of required) if (!s.includes(needle)) throw new Error(`Post-patch assertion missing: ${needle}`);
const forbidden = [
  'Saved plans to update',
  'This program is part of an older training cycle. It\'s kept safely on this device; create a new cycle to keep training.',
  'children: "Save updated plan"'
];
for (const needle of forbidden) if (s.includes(needle)) throw new Error(`Old behavior still present: ${needle}`);

fs.writeFileSync(path, s);
console.log('Legacy plan restore UX patch applied.');
