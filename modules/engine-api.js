/** Canonical computation API used by the app and standalone consumers. */
export * from './training-domain.js';
export * from './program-duration.js';
export * as shell from './engine-shell.js';
export * from './next-engine/config.js';
export * from './next-engine/domain-contracts.js';
export * from './next-engine/generate.js';
export * from './next-engine/app-shell-adapter.js';
export * from './next-engine/cycle-runtime-adapter.js';
export * from './next-engine/workout-history-adapter.js';
export * from './next-engine/workout-runtime.js';
export * from './next-engine/history-contract.js';
export * from './next-engine/percentage-protocols.js';
export { evaluateWorkoutProgression } from './next-engine/performance.js';
export { deriveArmCoverage } from './next-engine/arm-coverage.js';
export { deriveFunctionalCoverage } from './next-engine/functional-coverage.js';
export { EXERCISE_MAP } from './next-engine/exercise-db.js';
export { avoidableExerciseOverlap } from './next-engine/exercise-economy.js';
export { captureShellVolumeSnapshot, auditShellVolume, repairShellVolume, shellVolumeTargets, shellDayMuscleBreakdown } from './next-engine/volume-repair.js';
