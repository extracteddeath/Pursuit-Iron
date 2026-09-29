import fs from 'node:fs';

function replaceOnce(path, before, after, label) {
  let source = fs.readFileSync(path, 'utf8');
  if (source.includes(after)) return false;
  if (!source.includes(before)) throw new Error(`M193 marker missing (${label}) in ${path}`);
  source = source.replace(before, after);
  fs.writeFileSync(path, source);
  return true;
}

const path='modules/next-engine/cycle-runtime-adapter.js';
const changed=[];
const mark=(label,did)=>{if(did) changed.push(label);};

mark('progression imports', replaceOnce(path,
  `import { estimateSessionMinutes, progressionForExercise, repsForPhase, restForExercise, rirForPhase } from './realizer.js';`,
  `import { estimateSessionMinutes, repsForPhase, restForExercise, rirForPhase } from './realizer.js';\nimport { progressionInstruction, selectProgressionStyle } from './progression-style.js';`,
  'progression imports'
));

mark('static retarget signature', replaceOnce(path,
  `function retargetStatic(previous, request, target) {`,
  `function retargetStatic(previous, request, target, blockWeeks = 6) {`,
  'static retarget signature'
));

mark('static progression selection', replaceOnce(path,
  `            return {\n                ...ex,\n                sets: Math.max(1, Math.round(ex.sets * ratio)),\n                prescription: {\n                    reps: repsForPhase(def, ex.role, targetPolicy),\n                    rir: rirForPhase(ex.role, targetPolicy),\n                    restSeconds: restForExercise(ex.role, def)\n                },\n                progression: progressionForExercise(def, ex.role, targetPolicy, normalized.athlete.experience),\n                advancedTechnique: targetPolicy.advancedTechniqueBudget > 0 ? ex.advancedTechnique : undefined\n            };`,
  `            const prescription = {\n                reps: repsForPhase(def, ex.role, targetPolicy),\n                rir: rirForPhase(ex.role, targetPolicy),\n                restSeconds: restForExercise(ex.role, def)\n            };\n            const progressionSelection = selectProgressionStyle(def, ex.role, {\n                phase: target,\n                experience: normalized.athlete.experience,\n                blockWeeks: Math.max(1, Number(blockWeeks) || 6),\n                requestedStyle: normalized.preferences?.progressionStyle,\n                prescription\n            });\n            const previousStyle = ex.progressionStyle ?? null;\n            return {\n                ...ex,\n                sets: Math.max(1, Math.round(ex.sets * ratio)),\n                prescription,\n                progressionStyle: progressionSelection.style,\n                progression: progressionInstruction(progressionSelection.style),\n                progressionSelection: {\n                    source: progressionSelection.source,\n                    confidence: progressionSelection.confidence,\n                    reason: progressionSelection.reason,\n                    previousStyle,\n                    changed: previousStyle !== null ? previousStyle !== progressionSelection.style : false\n                },\n                advancedTechnique: targetPolicy.advancedTechniqueBudget > 0 ? ex.advancedTechnique : undefined\n            };`,
  'static progression selection'
));

mark('first block context', replaceOnce(path,
  `            next = generateProgram(baseRequest, { phase: spec.phase }).program;`,
  `            next = generateProgram(baseRequest, {\n                phase: spec.phase,\n                blockWeeks: spec.weeks,\n                progressionStyle: baseRequest.preferences?.progressionStyle\n            }).program;`,
  'first block context'
));

mark('cycle preview transition context', replaceOnce(path,
  `            next = transitionProgramPhase(previous, normalized, spec.phase, { successfulExerciseIds: ids, protectedExerciseIds: protectedIds }).program;`,
  `            next = transitionProgramPhase(previous, normalized, spec.phase, {\n                successfulExerciseIds: ids,\n                protectedExerciseIds: protectedIds,\n                nextBlockWeeks: spec.weeks\n            }).program;`,
  'cycle preview transition context'
));

mark('cycle preview static context', replaceOnce(path,
  `            next = retargetStatic(previous, baseRequest, spec.phase);`,
  `            next = retargetStatic(previous, baseRequest, spec.phase, spec.weeks);`,
  'cycle preview static context'
));

mark('converted preview transition context', replaceOnce(path,
  `            next = transitionProgramPhase(previous, normalized, spec.phase, {\n                successfulExerciseIds: ids,\n                protectedExerciseIds: protectedIds\n            }).program;`,
  `            next = transitionProgramPhase(previous, normalized, spec.phase, {\n                successfulExerciseIds: ids,\n                protectedExerciseIds: protectedIds,\n                nextBlockWeeks: spec.weeks\n            }).program;`,
  'converted preview transition context'
));

mark('converted preview static context', replaceOnce(path,
  `            next = retargetStatic(previous, baseRequest, spec.phase);`,
  `            next = retargetStatic(previous, baseRequest, spec.phase, spec.weeks);`,
  'converted preview static context'
));

mark('history-driven cycle transition context', replaceOnce(path,
  `        next = transitionProgramPhase(source, normalized, target, { successfulExerciseIds: analysis.successfulExerciseIds, protectedExerciseIds: analysis.protectedExerciseIds, replaceExerciseIds: analysis.replaceExerciseIds }).program;`,
  `        next = transitionProgramPhase(source, normalized, target, {\n            successfulExerciseIds: analysis.successfulExerciseIds,\n            protectedExerciseIds: analysis.protectedExerciseIds,\n            replaceExerciseIds: analysis.replaceExerciseIds,\n            techniqueLimitedExerciseIds: analysis.techniqueLimitedExerciseIds,\n            fatigueLimitedExerciseIds: analysis.fatigueLimitedExerciseIds,\n            progressionEvidenceByExercise: analysis.progressionEvidenceByExercise,\n            nextBlockWeeks: weeks\n        }).program;`,
  'history-driven cycle transition context'
));

mark('history-driven static context', replaceOnce(path,
  `        next = retargetStatic(source, request, target);`,
  `        next = retargetStatic(source, request, target, weeks);`,
  'history-driven static context'
));

console.log(changed.length ? `M193 cycle progression context applied: ${changed.join(', ')}` : 'M193 cycle progression context already applied.');
