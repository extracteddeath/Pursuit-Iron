import assert from 'node:assert/strict';
import { deriveAthleteResponse, requestWithAthleteResponse, personalizeMusclePrescription, validateAthleteResponse } from '../modules/next-engine/athlete-response.js';
import { createMusclePrescriptions, normalizeRequest } from '../modules/next-engine/prescription.js';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { EXERCISES } from '../modules/training-domain.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine } from '../modules/next-engine/workout-history-adapter.js';

const asOf = Date.parse('2026-10-07T12:00:00Z'), start = asOf - 42 * 86400000;
const id = [...EXERCISE_MAP.values()].find(x => x.muscles.chest?.credit === 1 && x.flags.compound).id;
const workout = (i, options = {}) => ({ programId: 'owned', historyId: `w${i}`, unit: 'lb', completedAt: new Date(start + i * 7 * 86400000).toISOString(),
    session: { id: 'day-A', exercises: [{ exerciseId: id, sets: 3, prescription: { reps: [8, 12], rir: [1, 3] } }] },
    performedSets: Array.from({ length: 3 }, () => ({ exerciseId: id, load: 100 + i * 2, reps: 10, rir: 2 })),
    progression: [{ exerciseId: id, outcome: 'success' }], ...options });
const history = Array.from({ length: 6 }, (_, i) => workout(i));
const input = { programId: 'owned', workouts: history, unit: 'lb', asOf };
const before = structuredClone(input), model = deriveAthleteResponse(input);
assert.deepEqual(input, before);
assert.deepEqual(deriveAthleteResponse({ ...input, workouts: [...history].reverse() }), model, 'order-independent replay');
assert.equal(model.muscles.chest.doseScale, 1, 'progressing work does not justify extra sets');
assert.ok(model.progressionVelocity[JSON.stringify(['day-A', id])].relativePerWeek > 0);
assert.equal(model.exerciseSuccess[id].successes, 6);
assert.equal(model.frequency.chest.observedPerWeek, 1);
assert.equal(model.recovery.status, 'tolerated');
assert.equal(model.evidence.provenance, 'program_and_session_owned_completed_observations');
const request = { athlete: { experience: 'intermediate' }, goal: { type: 'hypertrophy' }, schedule: { days: ['monday', 'thursday'].map(day => ({ day, maxMinutes: 60 })) },
    equipment: { available: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine'], loading: { unit: 'lb' } }, preferences: { avoidedExercises: [id] } };
const adapted = requestWithAthleteResponse(request, model);
assert.ok(!adapted.preferences.preferredExercises?.includes(id), 'avoidance wins learned success');
assert.equal(request.preferences.athleteResponse, undefined);
for (const modifier of [w => ({ ...w, programId: 'unrelated' }), w => ({ ...w, interrupted: true }), w => ({ ...w, badDay: true }),
    w => ({ ...w, performedSets: w.performedSets.slice(0, 1) }), w => ({ ...w, performedSets: w.performedSets.map(s => ({ ...s, prescriptionEdited: true })) }),
    w => ({ ...w, completedAt: '2025-01-01T00:00:00Z' }), w => ({ ...w, completedAt: '2027-01-01T00:00:00Z' })]) {
    const held = deriveAthleteResponse({ ...input, workouts: history.map(modifier) });
    assert.equal(held.evidence.comparableExposures, 0);
    assert.equal(held.fatigue.capacityScale, 1);
    assert.equal(held.recovery.status, 'unknown');
}
const missingEffort = deriveAthleteResponse({ ...input, workouts: history.map(w => ({ ...w, performedSets: w.performedSets.map(s => ({ ...s, rir: null })) })) });
assert.equal(missingEffort.muscles.chest.confidence, 0);
assert.equal(missingEffort.muscles.chest.doseScale, 1);
assert.equal(missingEffort.progressionVelocity[JSON.stringify(['day-A', id])].relativePerWeek, null);
const sparse = deriveAthleteResponse({ ...input, workouts: history.slice(0, 2) });
assert.equal(requestWithAthleteResponse(request, sparse), request);
const duplicate = deriveAthleteResponse({ ...input, workouts: [...history, history[0]] });
assert.equal(duplicate.evidence.comparableExposures, 6);
assert.equal(duplicate.evidence.excluded.duplicate_identity, 1);
const failed = history.map(w => ({ ...w, progression: [{ exerciseId: id, outcome: 'failure' }] }));
const reduced = deriveAthleteResponse({ ...input, workouts: failed });
assert.equal(reduced.muscles.chest.doseScale, .9);
const prior = { muscle: 'chest', priority: 'primary', minimum: 8, preferred: 12, upper: 16, directMinimum: 4, directPreferred: 6 };
const prescription = personalizeMusclePrescription(prior, reduced);
assert.equal(prescription.minimum, prior.minimum);
assert.equal(prescription.priority, 'primary');
assert.equal(prescription.preferred, 11);
const normalized = normalizeRequest(request), actualPrior = createMusclePrescriptions(normalized).find(p => p.muscle === 'chest');
const actualAdapted = createMusclePrescriptions(normalizeRequest(requestWithAthleteResponse(request, reduced))).find(p => p.muscle === 'chest');
assert.deepEqual(actualAdapted, personalizeMusclePrescription(actualPrior, reduced), 'response reaches canonical dosage');
const plateau = deriveAthleteResponse({ ...input, workouts: history.map(w => ({ ...w, performedSets: w.performedSets.map(s => ({ ...s, load: 100 })) })) });
assert.equal(plateau.muscles.chest.doseScale, 1.05);
const twoDays = deriveAthleteResponse({ ...input, workouts: [...history, ...history.map((w, i) => ({ ...w, historyId: `b${i}`, session: { ...w.session, id: 'day-B' }, performedSets: w.performedSets.map(s => ({ ...s, load: 20 })) }))] });
assert.equal(Object.keys(twoDays.progressionVelocity).length, 2);
assert.equal(twoDays.progressionVelocity[JSON.stringify(['day-A', id])].relativePerWeek, model.progressionVelocity[JSON.stringify(['day-A', id])].relativePerWeek);
const kg = deriveAthleteResponse({ ...input, workouts: history.map(w => ({ ...w, unit: 'kg', performedSets: w.performedSets.map(s => ({ ...s, load: s.load / 2.2046226218487757 })) })) });
assert.equal(kg.progressionVelocity[JSON.stringify(['day-A', id])].relativePerWeek, model.progressionVelocity[JSON.stringify(['day-A', id])].relativePerWeek);
assert.throws(() => validateAthleteResponse({ ...model, schemaVersion: 2 }), /Unsupported response schema/);
assert.throws(() => normalizeRequest({ ...request, preferences: { athleteResponse: { ...model, muscles: { chest: { ...model.muscles.chest, doseScale: 2 } } } } }), /unbounded/);
const config = { name: 'Response integration', unit: 'lb', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4,
    session: 's60', weeks: 6, progression: 'auto', deload: false, equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'smith', 'pullup', 'dip', 'legpress', 'legext', 'legcurl'],
    focus: {}, reduce: [], barbellCap: 3, noBodyweight: false, noSupersets: false };
const shell = generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 231, makeId: () => 'response-shell' }).program;
const day = shell.days[0];
const realHistory = history.map((w, i) => ({ id: `real-${i}`, programId: shell.id, dayId: day.id, date: start + i * 7 * 86400000, unit: 'lb', weekIndex: 1,
    perf: Object.fromEntries(day.exercises.map((legacyId, slot) => { const cell = shell.nextWeekPrescriptions[`${day.id}:${slot}`][1];
        const reps = Number(String(cell.reps).split('-').at(-1));
        return [legacyId, { sets: Array.from({ length: cell.sets }, () => ({ w: 100, r: reps, rir: 2, rirReported: true, done: true })) }]; })) }));
const analysis = analyzeShellHistoryForNextEngine(shell, realHistory, EXERCISES, { asOf });
assert.equal(analysis.athleteResponse.programId, shell.id);
assert.ok(analysis.athleteResponse.evidence.comparableExposures >= 6, 'actual shell logs feed the response model');
assert.deepEqual(analyzeShellHistoryForNextEngine(shell, [...realHistory, { ...realHistory[0], id: 'foreign', programId: 'foreign' }], EXERCISES, { asOf }).athleteResponse, analysis.athleteResponse);
console.log('PASS M231: owned/day-specific response, immutable deterministic historical replay, effort/recency/context restraint, frequency/fatigue/fit/velocity/recovery confidence and bounded canonical dosage.');
