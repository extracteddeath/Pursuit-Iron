import fs from 'node:fs';

function patch(path, before, after, label) {
  let source = fs.readFileSync(path, 'utf8');
  if (source.includes(after)) return false;
  if (!source.includes(before)) throw new Error(`M192 marker missing (${label}) in ${path}`);
  source = source.replace(before, after);
  fs.writeFileSync(path, source);
  return true;
}

const changed = [];
const mark = (path, did) => { if (did) changed.push(path); };

mark('modules/next-engine/phase-transition.js', patch(
  'modules/next-engine/phase-transition.js',
  `                progressionSelection: {\n                    source: selection.source,\n                    confidence: selection.confidence,\n                    reason: selection.reason,\n                    previousStyle: currentStyle\n                }`,
  `                progressionSelection: {\n                    source: selection.source,\n                    confidence: selection.confidence,\n                    reason: selection.reason,\n                    previousStyle: currentStyle,\n                    // M192: preserve the actual before/after answer as engine-owned metadata. The UI\n                    // should never infer a method change from labels or regenerate progression policy.\n                    changed: selection.style !== currentStyle\n                }`,
  'engine transition delta'
));

mark('modules/next-engine/app-shell-adapter.js', patch(
  'modules/next-engine/app-shell-adapter.js',
  `function schemeStyle(config, role, style) {\n    if (config?.percentScheme && (role === 'primary_strength' || role === 'secondary_strength'))\n        return 'e1rm';\n    return style;\n}`,
  `function schemeStyle(config, role, style) {\n    if (config?.percentScheme && (role === 'primary_strength' || role === 'secondary_strength'))\n        return 'e1rm';\n    return style;\n}\nfunction progressionPlanItem(config, exercise) {\n    const style = schemeStyle(config, exercise.role, exercise.progressionStyle ?? 'auto');\n    const rawPrevious = exercise.progressionSelection?.previousStyle ?? null;\n    const previousStyle = rawPrevious ? schemeStyle(config, exercise.role, rawPrevious) : null;\n    return {\n        exerciseId: exercise.exerciseId, exerciseName: exercise.name, role: exercise.role, style,\n        source: exercise.progressionSelection?.source ?? 'auto',\n        confidence: exercise.progressionSelection?.confidence ?? 'moderate',\n        reason: exercise.progressionSelection?.reason ?? 'Auto selected a progression that matches this exercise and block.',\n        // Carry the comparison result through the shell snapshot. Recompute only the display-level\n        // percent-scheme normalization; the actual transition decision remains engine-owned.\n        previousStyle,\n        changed: previousStyle !== null ? previousStyle !== style : false\n    };\n}`,
  'shell progression plan helper'
));

mark('modules/next-engine/app-shell-adapter.js', patch(
  'modules/next-engine/app-shell-adapter.js',
  `            progressionPlan: nextProgram.sessions.flatMap(session => session.exercises.map(exercise => ({\n                exerciseId: exercise.exerciseId, exerciseName: exercise.name, role: exercise.role,\n                style: schemeStyle(config, exercise.role, exercise.progressionStyle ?? 'auto'),\n                source: exercise.progressionSelection?.source ?? 'auto',\n                confidence: exercise.progressionSelection?.confidence ?? 'moderate',\n                reason: exercise.progressionSelection?.reason ?? 'Auto selected a progression that matches this exercise and block.'\n            })))`,
  `            progressionPlan: nextProgram.sessions.flatMap(session => session.exercises.map(exercise => progressionPlanItem(config, exercise)))`,
  'shell progression plan delta metadata'
));

mark('modules/App.js', patch(
  'modules/App.js',
  `    const adaptiveSelections = selectedProgressions.filter(item => item?.source === "adaptive" || item?.source === "adaptive_hold").length;\n    const manualSelections = selectedProgressions.filter(item => item?.source === "manual").length;`,
  `    const adaptiveSelections = selectedProgressions.filter(item => item?.source === "adaptive" || item?.source === "adaptive_hold").length;\n    const changedSelections = selectedProgressions.filter(item => item?.changed === true).length;\n    const manualSelections = selectedProgressions.filter(item => item?.source === "manual").length;`,
  'UI transition delta count'
));

mark('modules/App.js', patch(
  'modules/App.js',
  `children: adaptiveSelections ? adaptiveSelections + " re-checked" : progressionGroups.length + " method" + (progressionGroups.length === 1 ? "" : "s")`,
  `children: adaptiveSelections ? changedSelections + " changed · " + Math.max(0, adaptiveSelections - changedSelections) + " kept" : progressionGroups.length + " method" + (progressionGroups.length === 1 ? "" : "s")`,
  'UI transition delta summary'
));

mark('modules/App.js', patch(
  'modules/App.js',
  `_jsx("div", { style: { marginTop: 2, color: C.muted, fontSize: 12, lineHeight: 1.45 }, children: item.reason || "Auto selected the progression that best fits this exercise and block." })] }, String(item.exerciseId || item.exerciseName || "exercise") + ":" + index)`,
  `item.previousStyle ? _jsx("div", { "data-progression-delta": item.changed ? "changed" : "kept", style: { marginTop: 3, color: item.changed ? C.accentInk : C.faint, fontSize: 11, fontWeight: 700 }, children: item.changed ? "Changed: " + progressionLabel(item.previousStyle) + " → " + progressionLabel(item.style) : "Kept: " + progressionLabel(item.style) }) : null, _jsx("div", { style: { marginTop: 2, color: C.muted, fontSize: 12, lineHeight: 1.45 }, children: item.reason || "Auto selected the progression that best fits this exercise and block." })] }, String(item.exerciseId || item.exerciseName || "exercise") + ":" + index)`,
  'UI per-exercise transition delta'
));

console.log(changed.length ? `M192 progression deltas applied: ${[...new Set(changed)].join(', ')}` : 'M192 progression deltas already applied.');
