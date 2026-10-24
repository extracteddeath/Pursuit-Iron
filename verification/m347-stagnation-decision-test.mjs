import assert from 'node:assert/strict';
import { CURATED_EXERCISES, EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { reselectProgressionStyle } from '../modules/next-engine/progression-style.js';
import { deriveProgressionSelectionEvidence } from '../modules/next-engine/workout-history-adapter.js';
import { deriveAthleteResponse, requestWithAthleteResponse,
    personalizeMusclePrescription } from '../modules/next-engine/athlete-response.js';
import { createMusclePrescriptions, normalizeRequest } from '../modules/next-engine/prescription.js';

const byId = Object.fromEntries(CURATED_EXERCISES.map(e => [e.id, e]));
const lift = (style = 'double', reps = 9, rir = 2, outcome = 'productive') => ({
    session: { exercises: [{ exerciseId: 'machine_press', role: 'hypertrophy_compound',
        sets: 3, progressionStyle: style, prescription: { reps: [8, 12], rir: [1, 3] } }] },
    progression: [{ exerciseId: 'machine_press', outcome,
        action: outcome === 'failure' ? 'hold' : 'add_reps',
        reasonCode: outcome === 'failure' ? 'failed_prescription' : 'normal_progression' }],
    performedSets: Array.from({ length: 3 }, (_, setIndex) => ({
        exerciseId: 'machine_press', setIndex, load: 100, reps, rir }))
});
const ev = xs => deriveProgressionSelectionEvidence(xs).machine_press;
const flat = Array.from({ length: 4 }, () => lift());
assert.equal(ev(flat.slice(0, 2)).stallCount, 0,
    'Two comparable same-load workouts are not enough evidence for a plateau');
assert.equal(ev(flat).stallCount, 2);
const decision = args => reselectProgressionStyle(byId.machine_press, 'hypertrophy_compound', {
    phase: 'hypertrophy_accumulation', experience: 'intermediate', currentStyle: 'double',
    evidence: args
});
assert.equal(decision({ ...ev(flat), successful: true }).style, 'dynamic',
    'Per-workout productive labels must not mask repeated, unresolved flat exposure evidence');
assert.equal(decision({ ...ev(flat.slice(0, 2)), successful: true }).style, 'double',
    'Auto must not switch on two flat sessions');
assert.equal(reselectProgressionStyle(byId.machine_press, 'hypertrophy_compound', {
    phase: 'hypertrophy_accumulation', experience: 'intermediate', currentStyle: 'double',
    requestedStyle: 'double', evidence: { ...ev(flat), successful: true }
}).style, 'double', 'Manual choice must remain locked');
const improved = ev([...flat, lift('double', 10)]);
assert.equal(improved.stallCount, 0,
    'Actual rep improvement clears the outdated plateau rather than retaining a historical cumulative score');
assert.equal(decision({ ...improved, successful: true }).style, 'double',
    'Successful rep improvements must prevent stale method changes');
const failuresThenImprovement = ev([lift('double', 7, 0, 'failure'), lift('double', 7, 0, 'failure'), lift('double', 9)]);
assert.equal(failuresThenImprovement.failureCount, 2, 'Failure evidence remains available for diagnosis');
assert.equal(failuresThenImprovement.stallCount, 0, 'A more successful workout resolves the current failure streak');
assert.equal(decision({ ...failuresThenImprovement, successful: true }).style, 'double',
    'Old failures must not negate a newly improving exercise');
const switched = ev([...flat, lift('dynamic')]);
assert.equal(switched.comparableExposures, 1);
assert.equal(switched.stallCount, 0);
assert.equal(switched.styleExposures, 1);
assert.equal(reselectProgressionStyle(byId.machine_press, 'hypertrophy_compound', {
    phase: 'hypertrophy_accumulation', experience: 'intermediate',
    currentStyle: 'dynamic', evidence: { ...switched, successful: true }
}).style, 'dynamic', 'New progression needs its own evidence before reconsideration');

// A six-week unchanged load trend was previously treated as permission to add
// 5% preferred volume. A plateau is NOT a dose-response experiment. The
// response can explain it and request review, but should not increase sets.
const ex = [...EXERCISE_MAP.values()].find(e => e.muscles?.chest?.credit === 1 && e.flags.compound);
assert.ok(ex);
const since = Date.UTC(2026, 7, 1);
const history = Array.from({ length: 6 }, (_, i) => ({
    programId: 'm347', historyId: 'm347-' + i, unit: 'lb',
    completedAt: new Date(since + i * 7 * 86400000).toISOString(),
    session: { id: 'day-1', exercises: [{
        exerciseId: ex.id, sets: 3, prescription: { reps: [8, 12], rir: [1, 3] }
    }] },
    performedSets: Array.from({ length: 3 }, (_, setIndex) => ({
        exerciseId: ex.id, setIndex, load: 100, reps: 10, rir: 2 })),
    progression: [{ exerciseId: ex.id, outcome: 'success' }]
}));
const source = { programId: 'm347', workouts: history, unit: 'lb',
    asOf: since + 40 * 86400000 };
const saved = JSON.stringify(source);
const response = deriveAthleteResponse(source);
assert.equal(JSON.stringify(source), saved, 'Analytics changed saved workout records');
assert.equal(response.muscles.chest.volumeReviewRequired, true);
assert.equal(response.muscles.chest.reason, 'plateau_requires_dose_review');
assert.equal(response.muscles.chest.doseScale, 1,
    'Six healthy but stagnant training weeks must not trigger automatic volume additions');
assert.equal(response.fatigue.capacityScale, 1,
    'A stable plateau is not evidence for deloading');
for (const goal of ['hypertrophy', 'strength', 'mixed']) {
    const request = { athlete: { experience: 'intermediate' },
        goal: { type: goal }, schedule: { days: [
            { day: 'monday', maxMinutes: 90 }, { day: 'thursday', maxMinutes: 90 }
        ] },
        equipment: { available: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine'],
            loading: { unit: 'lb' } }, preferences: {} };
    const previous = createMusclePrescriptions(normalizeRequest(request));
    const personalized = createMusclePrescriptions(normalizeRequest(requestWithAthleteResponse(request, response)));
    assert.deepEqual(personalized, previous,
        goal + ': plateau-only response changed periodized muscle volumes');
}
const baseline = { muscle: 'chest', priority: 'normal',
    minimum: 8, preferred: 12, upper: 16, directMinimum: 4, directPreferred: 6 };
assert.deepEqual(personalizeMusclePrescription(baseline, response), baseline);
// True broad failures remain eligible for conservative volume decreases. This
// is a different causal branch than successful stable plateau.
const failed = deriveAthleteResponse({ ...source, workouts: history.map(w => ({
    ...w, progression: [{ exerciseId: ex.id, outcome: 'failure' }]
})) });
assert.equal(failed.muscles.chest.doseScale, .9);
assert.equal(failed.muscles.chest.volumeReviewRequired, false);
assert.equal(failed.fatigue.capacityScale, 1,
    'A single-lift failure trend alone does not establish broad systemic fatigue');
console.log('ENGINE_STAGNATION_DECISION_AUDIT ' + JSON.stringify({
    status: 'pass', flatStalls: ev(flat).stallCount,
    methodAfterSustainedPlateau: decision({ ...ev(flat), successful: true }).style,
    methodAfterImprovement: decision({ ...improved, successful: true }).style,
    stagnantChestDoseScale: response.muscles.chest.doseScale,
    plateauVolumeReview: response.muscles.chest.volumeReviewRequired,
    failedChestDoseScale: failed.muscles.chest.doseScale
}));
