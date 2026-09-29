import fs from 'node:fs';

const path = 'modules/next-engine/simulation.js';
let source = fs.readFileSync(path, 'utf8');
const changed = [];

function replaceOnce(label, before, after) {
  if (source.includes(after)) return;
  if (!source.includes(before)) throw new Error(`M194 marker missing: ${label}`);
  source = source.replace(before, after);
  changed.push(label);
}

replaceOnce(
  'progression selector import',
  `import { progressionInstruction } from './progression-style.js';`,
  `import { progressionInstruction, selectProgressionStyle } from './progression-style.js';`
);
replaceOnce(
  'realizer import cleanup',
  `import { estimateSessionMinutes, repsForPhase, rirForPhase, restForExercise, progressionForExercise } from './realizer.js';`,
  `import { estimateSessionMinutes, repsForPhase, rirForPhase, restForExercise } from './realizer.js';`
);

replaceOnce(
  'state progression evidence counters',
  `            constrainedReviews: 0,\n            firstE1rm: null,`,
  `            constrainedReviews: 0,\n            loadingBlockedExposures: 0,\n            e1rmSamples: 0,\n            firstE1rm: null,`
);

replaceOnce(
  'session progression evidence counters',
  `        if (decision.estimated1RM !== undefined && decision.estimated1RM !== null) {\n            if (state.firstE1rm === null)\n                state.firstE1rm = decision.estimated1RM;\n            state.lastE1rm = decision.estimated1RM;\n        }`,
  `        if (decision.outcome === 'success_blocked')\n            state.loadingBlockedExposures++;\n        if (decision.estimated1RM !== undefined && decision.estimated1RM !== null) {\n            state.e1rmSamples++;\n            if (state.firstE1rm === null)\n                state.firstE1rm = decision.estimated1RM;\n            state.lastE1rm = decision.estimated1RM;\n        }`
);

replaceOnce(
  'counter snapshot evidence',
  `function counterSnapshot(states) {\n    return new Map([...states.entries()].map(([id, state]) => [id, { exposures: state.exposures, positive: state.positive, holds: state.holds, reviews: state.reviews, successfulExposures: state.successfulExposures, failedExposures: state.failedExposures, constrainedReviews: state.constrainedReviews }]));\n}`,
  `function counterSnapshot(states) {\n    return new Map([...states.entries()].map(([id, state]) => [id, {\n        exposures: state.exposures, positive: state.positive, holds: state.holds, reviews: state.reviews,\n        successfulExposures: state.successfulExposures, failedExposures: state.failedExposures, constrainedReviews: state.constrainedReviews,\n        loadingBlockedExposures: state.loadingBlockedExposures ?? 0, e1rmSamples: state.e1rmSamples ?? 0\n    }]));\n}`
);

replaceOnce(
  'block response evidence setup',
  `    const successful = [];\n    const stalled = [];\n    let totalPerformanceFailures = 0, totalPerformanceExposures = 0;`,
  `    const successful = [];\n    const stalled = [];\n    const progressionEvidenceByExercise = {};\n    let totalPerformanceFailures = 0, totalPerformanceExposures = 0;`
);

replaceOnce(
  'block response evidence derivation',
  `        const start = before.get(id) ?? { exposures: 0, positive: 0, holds: 0, reviews: 0, successfulExposures: 0, failedExposures: 0, constrainedReviews: 0 };\n        const exposures = state.exposures - start.exposures;`,
  `        const start = before.get(id) ?? { exposures: 0, positive: 0, holds: 0, reviews: 0, successfulExposures: 0, failedExposures: 0, constrainedReviews: 0, loadingBlockedExposures: 0, e1rmSamples: 0 };\n        const exposures = state.exposures - start.exposures;`
);

replaceOnce(
  'block response evidence row',
  `        totalPerformanceFailures += failedExposures;\n        totalPerformanceExposures += exposures;\n        if (successRate >= .65 && failureRate < .25)\n            successful.push(id);\n        if (isStallCandidate({ role: state.role }) && failureRate >= .35)\n            stalled.push(id);`,
  `        totalPerformanceFailures += failedExposures;\n        totalPerformanceExposures += exposures;\n        const successfulTrend = successRate >= .65 && failureRate < .25;\n        const stalledTrend = failureRate >= .35;\n        progressionEvidenceByExercise[id] = {\n            comparableExposures: exposures,\n            styleExposures: exposures,\n            failureCount: failedExposures,\n            stallCount: stalledTrend ? failedExposures : 0,\n            loadingBlockedCount: Math.max(0, (state.loadingBlockedExposures ?? 0) - (start.loadingBlockedExposures ?? 0)),\n            // Synthetic performed sets always contain an observed RIR, so the simulator can make the\n            // same evidence-quality decision as production instead of relying on selector defaults.\n            rirCoverage: 1,\n            e1rmSamples: Math.max(0, (state.e1rmSamples ?? 0) - (start.e1rmSamples ?? 0)),\n            successful: successfulTrend\n        };\n        if (successfulTrend)\n            successful.push(id);\n        if (isStallCandidate({ role: state.role }) && stalledTrend)\n            stalled.push(id);`
);

replaceOnce(
  'block response evidence result',
  `    return {\n        successfulExerciseIds: [...new Set(successful)].sort(),\n        stalledExerciseIds: [...new Set(stalled)].sort(),`,
  `    const fatigueLimitedExerciseIds = classification === 'fatigue_limited'\n        ? Object.entries(progressionEvidenceByExercise).filter(([, row]) => Number(row.failureCount) > 0).map(([id]) => id).sort()\n        : [];\n    for (const id of fatigueLimitedExerciseIds)\n        progressionEvidenceByExercise[id] = { ...progressionEvidenceByExercise[id], fatigueLimited: true };\n    return {\n        successfulExerciseIds: [...new Set(successful)].sort(),\n        stalledExerciseIds: [...new Set(stalled)].sort(),\n        fatigueLimitedExerciseIds,\n        techniqueLimitedExerciseIds: [],\n        progressionEvidenceByExercise,`
);

replaceOnce(
  'static retarget signature',
  `function retargetProgramWithoutStructuralAdaptation(previous, request, target) {`,
  `function retargetProgramWithoutStructuralAdaptation(previous, request, target, blockWeeks = 6) {`
);

replaceOnce(
  'static retarget progression policy',
  `            return {\n                ...ex,\n                sets,\n                prescription: {\n                    reps: repsForPhase(def, ex.role, targetPolicy),\n                    rir: rirForPhase(ex.role, targetPolicy),\n                    restSeconds: restForExercise(ex.role, def)\n                },\n                progression: progressionForExercise(def, ex.role, targetPolicy, request.athlete.experience),\n                advancedTechnique: targetPolicy.advancedTechniqueBudget > 0 ? ex.advancedTechnique : undefined\n            };`,
  `            const prescription = {\n                reps: repsForPhase(def, ex.role, targetPolicy),\n                rir: rirForPhase(ex.role, targetPolicy),\n                restSeconds: restForExercise(ex.role, def)\n            };\n            const progressionSelection = selectProgressionStyle(def, ex.role, {\n                phase: target,\n                experience: request.athlete.experience,\n                blockWeeks: Math.max(1, Number(blockWeeks) || 6),\n                requestedStyle: request.preferences?.progressionStyle,\n                prescription\n            });\n            const previousStyle = ex.progressionStyle ?? null;\n            return {\n                ...ex,\n                sets,\n                prescription,\n                progressionStyle: progressionSelection.style,\n                progression: progressionInstruction(progressionSelection.style),\n                progressionSelection: {\n                    source: progressionSelection.source,\n                    confidence: progressionSelection.confidence,\n                    reason: progressionSelection.reason,\n                    previousStyle,\n                    changed: previousStyle !== null ? previousStyle !== progressionSelection.style : false\n                },\n                advancedTechnique: targetPolicy.advancedTechniqueBudget > 0 ? ex.advancedTechnique : undefined\n            };`
);

replaceOnce(
  'manual weekly progression preservation',
  `export function weeklyProgressionStyle(ex, phase, week, totalWeeks) {\n    const base = ex.progressionStyle;\n    if (!base || base === 'linear' || !ACCUMULATION_PHASES.has(phase))`,
  `export function weeklyProgressionStyle(ex, phase, week, totalWeeks) {\n    const base = ex.progressionStyle;\n    // Manual means manual for the actual weekly shell prescription too. The legacy within-block\n    // schedule may evolve Auto strength work, but it must never silently replace an explicit method.\n    if (ex.progressionSelection?.source === 'manual')\n        return base;\n    if (!base || base === 'linear' || !ACCUMULATION_PHASES.has(phase))`
);

replaceOnce(
  'simulation initial block context',
  `            program = generateProgram(baseRequest, { phase: spec.phase }).program;`,
  `            program = generateProgram(baseRequest, {\n                phase: spec.phase,\n                blockWeeks: spec.weeks,\n                progressionStyle: baseRequest.preferences?.progressionStyle\n            }).program;`
);

replaceOnce(
  'simulation adaptive transition context',
  `            const result = transitionProgramPhase(priorProgram, normalized, spec.phase, {\n                successfulExerciseIds: priorResponse?.successfulExerciseIds ?? [],\n                protectedExerciseIds: priorProgram.sessions.flatMap(s => s.exercises).filter(ex => ex.role === 'primary_strength').map(ex => ex.exerciseId)\n            });`,
  `            const result = transitionProgramPhase(priorProgram, normalized, spec.phase, {\n                successfulExerciseIds: priorResponse?.successfulExerciseIds ?? [],\n                protectedExerciseIds: priorProgram.sessions.flatMap(s => s.exercises).filter(ex => ex.role === 'primary_strength').map(ex => ex.exerciseId),\n                fatigueLimitedExerciseIds: priorResponse?.fatigueLimitedExerciseIds ?? [],\n                techniqueLimitedExerciseIds: priorResponse?.techniqueLimitedExerciseIds ?? [],\n                progressionEvidenceByExercise: priorResponse?.progressionEvidenceByExercise ?? {},\n                nextBlockWeeks: spec.weeks\n            });`
);

replaceOnce(
  'simulation static transition context',
  `            program = retargetProgramWithoutStructuralAdaptation(priorProgram, normalized, spec.phase);`,
  `            program = retargetProgramWithoutStructuralAdaptation(priorProgram, normalized, spec.phase, spec.weeks);`
);

fs.writeFileSync(path, source);
console.log(changed.length ? `M194 simulation progression parity applied: ${changed.join(', ')}` : 'M194 simulation progression parity already applied.');
