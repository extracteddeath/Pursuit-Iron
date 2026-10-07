/** Stable realizer API; implementation is partitioned by responsibility. */
export { repsForPhase } from './realization/prescriptions.js';
export { rirForPhase } from './realization/prescriptions.js';
export { restForExercise } from './realization/prescriptions.js';
export { progressionStyleForExercise } from './realization/prescriptions.js';
export { progressionForExercise } from './realization/prescriptions.js';
export { estimateSessionMinutes } from './realization/time.js';
export { optimizeSetupAwareSessionSequence } from './realization/sequencing.js';
export { finalizePlannedSession } from './realization/sequencing.js';
export { realizeStrengthAnchors } from './realization/strength.js';
export { realizeSessions } from './realization/session.js';
