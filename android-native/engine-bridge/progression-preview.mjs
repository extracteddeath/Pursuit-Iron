// Read-only proof that the bridge can invoke the REAL canonical progression policy.
// This is NOT authorized for a native workout UI until bundled JS parity/device gates pass.
import { evaluateWorkoutProgression } from '../../modules/engine-api.js';

const plain = value => value !== null && typeof value === 'object' &&
  !Array.isArray(value) &&
  (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null);
const exactly = (obj, names) => plain(obj) && Object.keys(obj).every(key => names.includes(key));
const whole = (n, min, max) => Number.isInteger(n) && n >= min && n <= max;
const weight = n => typeof n === 'number' && Number.isFinite(n) && n >= 0 && n <= 2500;
const idPattern = /^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$/;
const equipmentPattern = /^[a-z0-9_-]{1,40}$/;

export function validProgressionPreviewParams(p) {
  if (!exactly(p, ['exerciseId','repMin','repMax','targetRir','sets','equipmentAvailable'])) return false;
  if (typeof p.exerciseId !== 'string' || !idPattern.test(p.exerciseId)) return false;
  if (!whole(p.repMin, 1, 100) || !whole(p.repMax, p.repMin, 100)) return false;
  if (!whole(p.targetRir, 0, 10)) return false;
  if (!Array.isArray(p.sets) || p.sets.length < 1 || p.sets.length > 8) return false;
  if (!p.sets.every(set => exactly(set, ['load','reps','rir']) &&
      weight(set.load) && whole(set.reps, 0, 100) &&
      (set.rir === null || whole(set.rir, 0, 10)))) return false;
  if (!Array.isArray(p.equipmentAvailable) || p.equipmentAvailable.length > 32 ||
      !p.equipmentAvailable.every(x => typeof x === 'string' && equipmentPattern.test(x)) ||
      new Set(p.equipmentAvailable).size !== p.equipmentAvailable.length) return false;
  return true;
}

export function canonicalProgressionPreview(p) {
  if (!validProgressionPreviewParams(p)) throw new TypeError('Invalid bounded progression input');
  // Explicitly reconstruct the only accepted schema; never pass caller objects to the engine.
  const exerciseId = p.exerciseId;
  const session = { exercises: [{
    exerciseId, name: exerciseId, sets: p.sets.length,
    prescription: { reps: [p.repMin, p.repMax], rir: [p.targetRir, p.targetRir] },
    progressionStyle: 'double'
  }] };
  const observed = p.sets.map((set, setIndex) => ({
    exerciseId, setIndex, load: set.load, reps: set.reps, rir: set.rir
  }));
  const decisions = evaluateWorkoutProgression(session, observed, {
    equipmentAvailable: [...p.equipmentAvailable]
  });
  const decision = decisions?.[0];
  if (!decision || typeof decision.action !== 'string')
    throw new Error('Canonical progression did not yield a decision');
  // No saved program edit, data write or automatic load override; a bounded diagnostic only.
  return {
    source: 'modules/engine-api.js:evaluateWorkoutProgression',
    previewOnly: true,
    action: decision.action,
    suggestedLoad: decision.suggestedLoad ?? null,
    suggestedReps: decision.suggestedReps ?? null,
    reasonCode: decision.reasonCode ?? null,
    confidence: decision.confidence ?? null,
    setTargets: Array.isArray(decision.setTargets)
      ? decision.setTargets.slice(0, 8).map(s => ({
          weight: s.weight ?? null, reps: s.reps ?? null
        }))
      : []
  };
}
