import assert from 'node:assert/strict';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { deriveAthleteResponse, requestWithAthleteResponse,
    personalizeMusclePrescription } from '../modules/next-engine/athlete-response.js';
import { createMusclePrescriptions, normalizeRequest } from '../modules/next-engine/prescription.js';

const DAY = 86400000, start = Date.UTC(2026, 6, 1), asOf = start + DAY * 54;
const id = [...EXERCISE_MAP.values()]
    .find(ex => ex.muscles?.chest?.credit === 1 && ex.flags?.compound)?.id;
assert.ok(id, 'Need a consistent directly trained chest exercise');
const workouts = ({ beforeSets = 3, afterSets = 4, followupReps = [8, 9, 10],
    baselineReps = [8, 8, 8], days = [0, 7, 14, 21, 28, 35],
    effort = 2, followupStyle = 'double', outcome = 'success',
    alteredRange = false } = {}) => Array.from({ length: 6 }, (_, i) => {
    const sets = i < 3 ? beforeSets : afterSets;
    const reps = i < 3 ? baselineReps[i] : followupReps[i - 3];
    return {
        programId: 'm348', historyId: 'volume-' + i, unit: 'lb',
        completedAt: new Date(start + days[i] * DAY).toISOString(),
        session: { id: 'upper-A', exercises: [{ exerciseId: id, sets,
            role: 'hypertrophy_compound', progressionStyle: i < 3 ? 'double' : followupStyle,
            prescription: { reps: alteredRange && i >= 3 ? [6, 10] : [8, 12],
                rir: [1, 3] } }] },
        performedSets: Array.from({ length: sets }, (_, setIndex) => ({
            exerciseId: id, setIndex, load: 100, reps,
            rir: i >= 3 ? effort : 2 })),
        progression: [{ exerciseId: id, outcome }]
    };
});
const response = workoutsArg => deriveAthleteResponse({
    programId: 'm348', workouts: workoutsArg, unit: 'lb', asOf
});
const key = row => row.muscles.chest.observedVolumeResponse;
const input = workouts();
const stable = response(input);
assert.equal(stable.muscles.chest.doseScale, 1,
    'A possible association is not authorization to increase training volume');
assert.equal(key(stable).status, 'observed');
assert.equal(key(stable).signal, 'improvement_associated');
assert.equal(key(stable).direction, 'higher_volume');
assert.equal(key(stable).before.weeklySets, 3);
assert.equal(key(stable).after.weeklySets, 4);
assert.ok(key(stable).after.performanceChange > .015);
assert.equal(key(stable).confidence, 'limited_observational');
assert.equal(key(stable).interpretation, 'association_not_causation');
assert.equal(key(stable).autoApply, false);
assert.deepEqual(stable, response([...input].reverse()),
    'Historical-order changes cannot rewrite volume response estimates');
assert.deepEqual(stable, response(JSON.parse(JSON.stringify(input))),
    'Persisted JSON must preserve observed volume response semantics');

const snapshot = JSON.stringify(input);
for (const goal of ['strength', 'hypertrophy', 'mixed']) {
    const request = {
        athlete: { experience: 'intermediate' }, goal: { type: goal },
        schedule: { days: [{ day: 'monday', maxMinutes: 90 }, { day: 'thursday', maxMinutes: 90 }] },
        equipment: { available: ['barbell', 'rack', 'bench', 'dumbbell', 'machine', 'cable'],
            loading: { unit: 'lb' } }, preferences: {}
    };
    const original = createMusclePrescriptions(normalizeRequest(request));
    const personalized = createMusclePrescriptions(normalizeRequest(requestWithAthleteResponse(request, stable)));
    assert.deepEqual(personalized, original,
        goal + ': detected dose association changed volume without causal evidence or review');
}
assert.equal(JSON.stringify(input), snapshot, 'Model mutated stored workout history');
const prior = { muscle: 'chest', priority: 'normal',
    minimum: 8, preferred: 12, upper: 16, directMinimum: 4, directPreferred: 6 };
assert.deepEqual(personalizeMusclePrescription(prior, stable), prior);

// Flat followup and worse followup are not evidence that adding sets worked.
const noGain = response(workouts({ followupReps: [8, 8, 8] }));
assert.equal(key(noGain).signal, 'no_clear_benefit');
const worsening = response(workouts({ followupReps: [10, 9, 8] }));
assert.equal(key(worsening).signal, 'decline_associated');
assert.equal(worsening.muscles.chest.doseScale, 1,
    'Observed decline must not induce speculative automatic dosage edits');
const lowerVolume = response(workouts({ beforeSets: 4, afterSets: 3 }));
assert.equal(key(lowerVolume).status, 'observed');
assert.equal(key(lowerVolume).direction, 'lower_volume');
assert.equal(key(lowerVolume).autoApply, false);
const noChange = response(workouts({ afterSets: 3 }));
assert.equal(key(noChange).reason, 'no_observed_volume_change');
const tooLarge = response(workouts({ afterSets: 6 }));
assert.equal(key(tooLarge).reason, 'volume_change_outside_comparable_trial_range');
const alreadyImproving = response(workouts({ baselineReps: [8, 9, 10] }));
assert.equal(key(alreadyImproving).reason, 'baseline_already_changing');
const changedTargets = response(workouts({ alteredRange: true }));
assert.equal(key(changedTargets).reason, 'changed_exercise_or_prescription');
const changedMethod = response(workouts({ followupStyle: 'dynamic' }));
assert.equal(key(changedMethod).reason, 'changed_exercise_or_prescription',
    'Switching progression method is a separate adaptation, not proof higher sets worked');
const irregularFrequency = response(workouts({ days: [0, 7, 14, 21, 32, 39] }));
assert.equal(key(irregularFrequency).reason, 'changed_or_irregular_training_frequency');
const missingEffort = response(workouts({ effort: null }));
assert.equal(key(missingEffort).reason, 'incomplete_or_unreliable_effort');
const repeatedFailures = response(workouts({ outcome: 'failure' }));
assert.equal(key(repeatedFailures).reason, 'incomplete_or_unreliable_effort');
const interrupted = response(workouts().map((w, i) => i === 3 ? { ...w, interrupted: true } : w));
assert.equal(key(interrupted).status, 'insufficient');
assert.ok(key(interrupted).observations < 6);
const unrelated = response([...workouts(), {
    ...workouts()[0], programId: 'foreign', historyId: 'foreign'
}]);
assert.deepEqual(key(unrelated), key(stable));
const duplicates = response([...workouts(), { ...workouts()[0] }]);
assert.deepEqual(key(duplicates), key(stable),
    'Duplicate logged workouts must not create extra exposure evidence');
// A correction remains authoritative even when it moves a workout out of the
// observation window or removes evidence needed by the response model.
const editedAt = new Date(asOf + DAY).toISOString();
for (const [name, patch] of [
    ['older date', { completedAt: new Date(start - 120 * DAY).toISOString() }],
    ['future date', { completedAt: new Date(asOf + DAY).toISOString() }],
    ['invalid date', { completedAt: 'invalid' }],
    ['invalid unit', { unit: 'invalid' }],
    ['missing session', { session: null }],
    ['missing effort', { performedSets: workouts()[3].performedSets.map(s => ({ ...s, rir: null })) }]
]) {
    const correction = { ...workouts()[3], ...patch, updatedAt: editedAt };
    for (const records of [[...workouts(), correction], [correction, ...workouts()]]) {
        const revised = response(records);
        assert.equal(key(revised).status, 'insufficient',
            name + ': the superseded workout resurrected observational evidence');
        assert.equal(revised.muscles.chest.doseScale, 1);
        assert.equal(key(revised).autoApply, false);
    }
}
// The same revision rule protects the older fatigue/prescription path, not
// merely the new annotation. Corrected-away failures cannot reduce dosage.
const failedHistory = workouts({ outcome: 'failure' });
assert.equal(response(failedHistory).muscles.chest.doseScale, .9);
const correctedHistory = failedHistory.map(w => ({ ...w, updatedAt: editedAt,
    completedAt: new Date(start - 120 * DAY).toISOString() }));
for (const records of [[...failedHistory, ...correctedHistory],
    [...correctedHistory, ...failedHistory]]) {
    const revised = response(records);
    assert.deepEqual(revised.muscles, {});
    assert.equal(revised.evidence.comparableExposures, 0);
    assert.equal(revised.fatigue.capacityScale, 1);
    assert.equal(revised.recovery.status, 'unknown');
    assert.equal(revised.evidence.excluded.duplicate_identity, 6);
    assert.equal(revised.evidence.excluded.outside_time_window, 6);
    assert.deepEqual(personalizeMusclePrescription(prior, revised), prior);
}
const validCorrection = { ...workouts()[3], updatedAt: editedAt,
    performedSets: workouts()[3].performedSets.map(s => ({ ...s, load: 110 })) };
assert.deepEqual(key(response([...workouts(), validCorrection])),
    key(response(workouts().map((w, i) => i === 3 ? validCorrection : w))),
    'A usable correction contributes exactly once and replaces the old observation');
const annotated = stable.muscles.chest;
assert.ok(annotated.observedVolumeResponse);
assert.equal(annotated.volumeReviewRequired, false,
    'Improving after a reported dose change is not the same as an ongoing plateau');
console.log('ENGINE_DOSE_RESPONSE_AUDIT ' + JSON.stringify({
    status: 'pass', muscle: 'chest',
    direction: key(stable).direction, signal: key(stable).signal,
    observations: key(stable).observations,
    heldDoseScale: annotated.doseScale,
    rejectedConfounders: 9, noAutomaticChanges: true
}));
