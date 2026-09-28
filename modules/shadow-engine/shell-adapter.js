import { normalizeRequest } from '../next-engine/prescription.js';
import { shellConfigToNextRequest } from '../next-engine/app-shell-adapter.js';
import { SHADOW_ENGINE_VERSION, SHADOW_SCHEMA_VERSION } from './config.js';
import { optimizeShadowProgram } from './optimizer.js';
import { shadowPriorityMetrics } from './scoring.js';
import { buildShadowTransitionReport } from './transition.js';
function weeklyMinutes(program) { return program.sessions.reduce((n, s) => n + s.estimatedMinutes, 0); }
function comparison(liveProgram, request, continuityIds = [], previousProgram, protectedExerciseIds = []) {
    const result = optimizeShadowProgram(request, liveProgram, { continuityIds, previousProgram });
    const base = result.baseline, cand = result.candidate;
    const b = shadowPriorityMetrics(base.program, request), c = shadowPriorityMetrics(cand.program, request);
    const delta = cand.score.total - base.score.total;
    const transition = previousProgram ? buildShadowTransitionReport(previousProgram, cand.program, normalizeRequest(request), continuityIds, protectedExerciseIds) : undefined;
    return { schemaVersion: SHADOW_SCHEMA_VERSION, shadowEngineVersion: SHADOW_ENGINE_VERSION, liveEngineVersion: liveProgram.engineVersion, evaluatedAt: Date.now(), baselineScore: base.score, shadowScore: cand.score, scoreDelta: Math.round(delta * 1000) / 1000, accepted: delta > .15, candidateCount: result.candidateCount, changes: cand.changes, transition, metrics: { baselineWeeklyMinutes: weeklyMinutes(base.program), shadowWeeklyMinutes: weeklyMinutes(cand.program), baselinePriorityFrequency: b.frequency, shadowPriorityFrequency: c.frequency, baselinePriorityEarlySlots: b.early, shadowPriorityEarlySlots: c.early }, research: { evidenceClass: 'structural_plus_longitudinal_simulation', productionPromotionAllowed: false, note: 'M75 keeps simulation qualification observational; controlled canary trials feed uncertainty-aware human review and cannot auto-promote behavior.' }, experimentalProgram: delta > .15 ? cand.program : undefined };
}
export function runShadowProgramForShell(options) {
    const request = shellConfigToNextRequest(options.config, options.banned ?? [], options.legacyExercises, options.seed ?? options.liveProgram.seed);
    return comparison(options.liveProgram, request);
}
export function runShadowTransitionForShell(options) {
    return comparison(options.liveNextProgram, options.request, options.successfulExerciseIds, options.previousProgram, options.protectedExerciseIds ?? []);
}
