import fs from 'node:fs';

const phasePath = 'modules/next-engine/phase-transition.js';
const cyclePath = 'modules/next-engine/cycle-runtime-adapter.js';
let phase = fs.readFileSync(phasePath, 'utf8');
let cycle = fs.readFileSync(cyclePath, 'utf8');

function replaceOnce(text, before, after, label) {
  const count = text.split(before).length - 1;
  if (count !== 1) throw new Error(`${label}: expected exactly one match, found ${count}`);
  return text.replace(before, after);
}

if (!phase.includes("from './capacity-generation.js'")) {
  phase = replaceOnce(
    phase,
    "import { generateProgram } from './generate.js';\n",
    "import { generateProgram } from './generate.js';\nimport { firstPassingCapacityProgram } from './capacity-generation.js';\n",
    'capacity import'
  );
}

phase = replaceOnce(
  phase,
  `    const generated = generateProgram(request, {\n        phase: target,\n        // New exercises introduced by the target phase must use the actual next-block duration too.\n        // Otherwise a four-week block can accidentally start a five-plus-week wave simply because\n        // the exercise has no prior history for the adaptive pass to correct.\n        blockWeeks: nextBlockWeeks,\n        progressionStyle: request.preferences?.progressionStyle\n    }).program;`,
  `    // Adaptive transitions must use the same soft-capacity contract as initial program/cycle creation.\n    // A valid 60–90 minute Full Body cycle can otherwise enter Strength with a raw generation that\n    // violates Full Body coverage even though a nearby capacity candidate passes the normal arbiter.\n    const generation = firstPassingCapacityProgram(request, evidence?.capacityConfig, {\n        phase: target,\n        // New exercises introduced by the target phase must use the actual next-block duration too.\n        // Otherwise a four-week block can accidentally start a five-plus-week wave simply because\n        // the exercise has no prior history for the adaptive pass to correct.\n        blockWeeks: nextBlockWeeks,\n        progressionStyle: request.preferences?.progressionStyle\n    });\n    const effectiveRequest = generation.request;\n    const generated = generation.result.program;`,
  'adaptive generation'
);

for (const [before, after, label] of [
  ['const context = createEngineContext(request);', 'const context = createEngineContext(effectiveRequest);', 'transition context'],
  ['matchPriorSessionsForTransition(previous.sessions, program.sessions, request, context)', 'matchPriorSessionsForTransition(previous.sessions, program.sessions, effectiveRequest, context)', 'session matching'],
  ['createTrainingSetEvents(sessions, request.customExercises)', 'createTrainingSetEvents(sessions, effectiveRequest.customExercises)', 'transition events'],
  ['auditProgram(base, request)', 'auditProgram(base, effectiveRequest)', 'transition audit'],
  ['isCompatibleReplacement(ex, candidate, session.day, request, context)', 'isCompatibleReplacement(ex, candidate, session.day, effectiveRequest, context)', 'replacement eligibility'],
  ['applyAdaptiveProgressionStyles(program, previous, request, target, evidence, context)', 'applyAdaptiveProgressionStyles(program, previous, effectiveRequest, target, evidence, context)', 'adaptive progression'],
  ['withProgramExplainability(program, request, [', 'withProgramExplainability(program, effectiveRequest, [', 'program explainability'],
  ['withBlockReviewExplainability(previous, program, request, {', 'withBlockReviewExplainability(previous, program, effectiveRequest, {', 'block review explainability'],
  ['return { program, continuity };', `return {\n        program,\n        continuity,\n        request: effectiveRequest,\n        capacityAdjustment: generation.adjusted ? {\n            requestedTargetExercises: generation.requestedTarget,\n            effectiveTargetExercises: generation.effectiveTarget,\n            requestedMinimumMinutes: generation.requestedMinimumMinutes,\n            effectiveMinimumMinutes: generation.effectiveMinimumMinutes\n        } : null\n    };`, 'transition return']
]) {
  if (phase.includes(before)) phase = replaceOnce(phase, before, after, label);
}

cycle = replaceOnce(
  cycle,
  `        else if (options.adaptBetweenBlocks) {\n            const ids = previous.sessions.flatMap(s => s.exercises.map(e => e.exerciseId));\n            const protectedIds = previous.sessions.flatMap(s => s.exercises.filter(e => e.role === 'primary_strength' || e.role === 'secondary_strength').map(e => e.exerciseId));\n            next = transitionProgramPhase(previous, normalized, spec.phase, {\n                successfulExerciseIds: ids,\n                protectedExerciseIds: protectedIds,\n                nextBlockWeeks: spec.weeks\n            }).program;\n        }`,
  `        else if (options.adaptBetweenBlocks) {\n            const ids = previous.sessions.flatMap(s => s.exercises.map(e => e.exerciseId));\n            const protectedIds = previous.sessions.flatMap(s => s.exercises.filter(e => e.role === 'primary_strength' || e.role === 'secondary_strength').map(e => e.exerciseId));\n            const transitioned = transitionProgramPhase(previous, normalized, spec.phase, {\n                successfulExerciseIds: ids,\n                protectedExerciseIds: protectedIds,\n                nextBlockWeeks: spec.weeks,\n                capacityConfig: options.config\n            });\n            next = transitioned.program;\n        }`,
  'cycle adaptive transition'
);

fs.writeFileSync(phasePath, phase);
fs.writeFileSync(cyclePath, cycle);
console.log('M198 adaptive transition capacity patch applied.');
