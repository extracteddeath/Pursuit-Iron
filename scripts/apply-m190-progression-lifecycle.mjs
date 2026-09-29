import fs from 'node:fs';

function replaceOnce(path, before, after) {
  const source = fs.readFileSync(path, 'utf8');
  if (source.includes(after)) return false;
  if (!source.includes(before)) throw new Error(`M190 marker missing in ${path}`);
  fs.writeFileSync(path, source.replace(before, after));
  return true;
}

const changed = [];
const mark = (path, didChange) => { if (didChange) changed.push(path); };

mark('modules/next-engine/realizer.js', replaceOnce(
  'modules/next-engine/realizer.js',
  `import { progressionInstruction, resolveProgressionStyle } from './progression-style.js';`,
  `import { progressionInstruction, selectProgressionStyle } from './progression-style.js';`
));

mark('modules/next-engine/realizer.js', replaceOnce(
  'modules/next-engine/realizer.js',
  `export function progressionStyleForExercise(ex, role, policy, experience = 'intermediate') {\n    return resolveProgressionStyle(ex, role, {\n        phase: policy.phase,\n        experience,\n        blockWeeks: policy.blockWeeks,\n        requestedStyle: policy.requestedProgressionStyle\n    });\n}`,
  `export function progressionStyleForExercise(ex, role, policy, experience = 'intermediate') {\n    return selectProgressionStyle(ex, role, {\n        phase: policy.phase,\n        experience,\n        blockWeeks: policy.blockWeeks,\n        requestedStyle: policy.requestedProgressionStyle\n    }).style;\n}`
));

mark('modules/next-engine/realizer.js', replaceOnce(
  'modules/next-engine/realizer.js',
  `    const progressionStyle = progressionStyleForExercise(ex, realizedRole, policy, experience);\n    return { exerciseId: ex.id, name: ex.name, role: realizedRole, sets, prescription: { reps: repsForPhase(ex, realizedRole, policy), rir: rirForPhase(realizedRole, policy), restSeconds: restForExercise(realizedRole, ex) }, progression: progressionInstruction(progressionStyle), progressionStyle };`,
  `    const prescription = { reps: repsForPhase(ex, realizedRole, policy), rir: rirForPhase(realizedRole, policy), restSeconds: restForExercise(realizedRole, ex) };\n    const progressionSelection = selectProgressionStyle(ex, realizedRole, {\n        phase: policy.phase,\n        experience,\n        blockWeeks: policy.blockWeeks,\n        requestedStyle: policy.requestedProgressionStyle,\n        prescription\n    });\n    const progressionStyle = progressionSelection.style;\n    return {\n        exerciseId: ex.id, name: ex.name, role: realizedRole, sets, prescription,\n        progression: progressionInstruction(progressionStyle), progressionStyle,\n        progressionSelection: {\n            source: progressionSelection.source,\n            confidence: progressionSelection.confidence,\n            reason: progressionSelection.reason\n        }\n    };`
));

mark('modules/next-engine/app-shell-adapter.js', replaceOnce(
  'modules/next-engine/app-shell-adapter.js',
  `            preferredSplit: split, lockedSplit: split, avoidedExercises: mapBanned(banned, legacyExercises),\n            volumeApproach: config.volumeApproach === 'minimalist' ? 'minimalist' : 'standard'`,
  `            preferredSplit: split, lockedSplit: split, avoidedExercises: mapBanned(banned, legacyExercises),\n            volumeApproach: config.volumeApproach === 'minimalist' ? 'minimalist' : 'standard',\n            // Persist the user's global method choice in the immutable request snapshot so later\n            // blocks cannot silently fall back to Auto after honoring the choice at creation.\n            progressionStyle: config.progressionStyle ?? 'auto'`
));

mark('modules/next-engine/app-shell-adapter.js', replaceOnce(
  'modules/next-engine/app-shell-adapter.js',
  `                nextEngine: true, nextExerciseId: exercise.exerciseId, legacyExerciseId: legacy.id, role: exercise.role, progressionStyle: schemeStyle(config, exercise.role, exercise.progressionStyle ?? 'auto')\n            };`,
  `                nextEngine: true, nextExerciseId: exercise.exerciseId, legacyExerciseId: legacy.id, role: exercise.role,\n                progressionStyle: schemeStyle(config, exercise.role, exercise.progressionStyle ?? 'auto'),\n                progressionSelection: exercise.progressionSelection ? { ...exercise.progressionSelection } : undefined\n            };`
));

mark('modules/next-engine/app-shell-adapter.js', replaceOnce(
  'modules/next-engine/app-shell-adapter.js',
  `        nextEngine: { displayLoadingModes, version: nextProgram.engineVersion, phase: nextProgram.phase, split: nextProgram.split, audit: nextProgram.audit, rationale: nextProgram.rationale, explainability: nextProgram.explainability, sourceProgramId: nextProgram.id }`,
  `        nextEngine: {\n            displayLoadingModes, version: nextProgram.engineVersion, phase: nextProgram.phase, split: nextProgram.split, audit: nextProgram.audit, rationale: nextProgram.rationale, explainability: nextProgram.explainability, sourceProgramId: nextProgram.id,\n            progressionPlan: nextProgram.sessions.flatMap(session => session.exercises.map(exercise => ({\n                exerciseId: exercise.exerciseId, exerciseName: exercise.name, role: exercise.role,\n                style: schemeStyle(config, exercise.role, exercise.progressionStyle ?? 'auto'),\n                source: exercise.progressionSelection?.source ?? 'auto',\n                confidence: exercise.progressionSelection?.confidence ?? 'moderate',\n                reason: exercise.progressionSelection?.reason ?? 'Auto selected a progression that matches this exercise and block.'\n            })))\n        }`
));

mark('modules/next-engine/phase-transition.js', replaceOnce(
  'modules/next-engine/phase-transition.js',
  `                blockWeeks,\n                evidence: progressionEvidenceFor(exercise.exerciseId, evidence, successful, fatigueLimited, techniqueLimited)`,
  `                blockWeeks,\n                // A global manual method remains manual across block review. Auto still re-selects\n                // exercise by exercise because explicitStyle ignores the literal 'auto' value.\n                requestedStyle: request.preferences?.progressionStyle,\n                evidence: progressionEvidenceFor(exercise.exerciseId, evidence, successful, fatigueLimited, techniqueLimited)`
));

mark('modules/next-engine/phase-transition.js', replaceOnce(
  'modules/next-engine/phase-transition.js',
  `export function transitionProgramPhase(previous, request, target, evidence) {\n    const generated = generateProgram(request, { phase: target }).program;`,
  `export function transitionProgramPhase(previous, request, target, evidence) {\n    const nextBlockWeeks = Math.max(1, Number(evidence?.nextBlockWeeks ?? evidence?.blockWeeks ?? 6) || 6);\n    const generated = generateProgram(request, {\n        phase: target,\n        // New exercises introduced by the target phase must use the actual next-block duration too.\n        // Otherwise a four-week block can accidentally start a five-plus-week wave simply because\n        // the exercise has no prior history for the adaptive pass to correct.\n        blockWeeks: nextBlockWeeks,\n        progressionStyle: request.preferences?.progressionStyle\n    }).program;`
));

console.log(changed.length ? `M190 progression lifecycle applied: ${[...new Set(changed)].join(', ')}` : 'M190 progression lifecycle already applied.');
