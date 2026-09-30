import fs from 'node:fs';

const path = 'modules/next-engine/cycle-runtime-adapter.js';
let text = fs.readFileSync(path, 'utf8');

const importNeedle = "import { shellConfigToNextRequest, nextProgramToShellProgram, NextShellAdapterError } from './app-shell-adapter.js';";
const importReplacement = `${importNeedle}\nimport { firstPassingCapacityProgram } from './capacity-generation.js';`;
if (!text.includes("from './capacity-generation.js'")) {
  if (!text.includes(importNeedle)) throw new Error('M198 patch: cycle adapter import anchor not found');
  text = text.replace(importNeedle, importReplacement);
}

const setupNeedle = `    const baseRequest = shellConfigToNextRequest({ ...options.config, goal: goal === 'mixed' ? 'both' : goal }, options.banned ?? [], options.legacyExercises, seed);\n    baseRequest.goal = { ...baseRequest.goal, type: goal };\n    const normalized = normalizeRequest(baseRequest);\n    const blocks = [];`;
const setupReplacement = `    let baseRequest = shellConfigToNextRequest({ ...options.config, goal: goal === 'mixed' ? 'both' : goal }, options.banned ?? [], options.legacyExercises, seed);\n    baseRequest.goal = { ...baseRequest.goal, type: goal };\n\n    // Program creation and cycle creation must share the same feasibility contract. The wizard\n    // already proves buildability with capacity-aware generation; bypassing it here made valid\n    // 60–90 minute Full Body cycles fail even though the equivalent single program passed.\n    const entrySpec = specs[0];\n    const entryAttempt = firstPassingCapacityProgram(baseRequest, options.config, {\n        phase: entrySpec.phase,\n        blockWeeks: entrySpec.weeks,\n        progressionStyle: baseRequest.preferences?.progressionStyle\n    });\n    baseRequest = entryAttempt.request;\n    const firstProgram = entryAttempt.result.program;\n    const normalized = normalizeRequest(baseRequest);\n    const blocks = [];`;
if (!text.includes('const entryAttempt = firstPassingCapacityProgram')) {
  if (!text.includes(setupNeedle)) throw new Error('M198 patch: cycle request setup anchor not found');
  text = text.replace(setupNeedle, setupReplacement);
}

const firstNeedle = `        if (!previous) {\n            next = generateProgram(baseRequest, {\n                phase: spec.phase,\n                blockWeeks: spec.weeks,\n                progressionStyle: baseRequest.preferences?.progressionStyle\n            }).program;\n        }`;
const firstReplacement = `        if (!previous) {\n            next = firstProgram;\n        }`;
if (text.includes(firstNeedle)) {
  text = text.replace(firstNeedle, firstReplacement);
} else if (!text.includes('next = firstProgram;')) {
  throw new Error('M198 patch: first cycle block anchor not found');
}

const metadataNeedle = `        nextEngineCycle: { schemaVersion: 1, templateId, goal, adaptBetweenBlocks: !!options.adaptBetweenBlocks, baseRequest: clone(baseRequest), baseConfig: clone(options.config), plannedBlocks: clone(specs), recoveryInsertions: 0 }`;
const metadataReplacement = `        nextEngineCycle: {\n            schemaVersion: 1, templateId, goal, adaptBetweenBlocks: !!options.adaptBetweenBlocks,\n            baseRequest: clone(baseRequest), baseConfig: clone(options.config), plannedBlocks: clone(specs), recoveryInsertions: 0,\n            ...(entryAttempt.adjusted ? { capacityAdjustment: {\n                policy: 'soft-capacity-band',\n                session: options.config?.session ?? 's60',\n                requestedTargetExercises: entryAttempt.requestedTarget,\n                effectiveTargetExercises: entryAttempt.effectiveTarget,\n                requestedMinimumMinutes: entryAttempt.requestedMinimumMinutes,\n                effectiveMinimumMinutes: entryAttempt.effectiveMinimumMinutes,\n                maxMinutes: baseRequest.schedule?.days?.[0]?.maxMinutes\n            } } : {})\n        }`;
if (!text.includes("nextEngineCycle: {\n            schemaVersion: 1, templateId, goal")) {
  if (!text.includes(metadataNeedle)) throw new Error('M198 patch: cycle metadata anchor not found');
  text = text.replace(metadataNeedle, metadataReplacement);
}

fs.writeFileSync(path, text);
console.log('M198 cycle adapter capacity patch applied.');
