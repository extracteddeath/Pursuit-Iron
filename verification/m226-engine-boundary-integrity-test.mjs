import assert from 'node:assert/strict';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { phasePolicyFor } from '../modules/next-engine/phase-policy.js';
import { generateProgram } from '../modules/next-engine/generate.js';
import { shellConfigToNextRequest } from '../modules/next-engine/app-shell-adapter.js';
import { EXERCISES, EX_BY_ID, sessionSuggestion, lastDayPerf, stallCountFor, linearStalled,
    projectNextTM, nextTMEvidence, gzPlanFor } from '../modules/App.js';

const gym = ['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
const config = { name: 'M226 boundary fixture', unit: 'lb', goal: 'both', experience: 'intermediate',
    split: 'upper_lower', days: 4, session: 's60', weeks: 6, progression: 'auto', deload: false,
    equipment: gym, focus: {}, reduce: [], barbellCap: 3, noBodyweight: false, noSupersets: false };
const request = shellConfigToNextRequest(config, [], EXERCISES, 226);

const cosmetic = structuredClone(request);
cosmetic.goal.type = ' Strength ';
cosmetic.athlete.experience = ' Advanced ';
cosmetic.schedule.days[0].day = ` ${cosmetic.schedule.days[0].day.toUpperCase()} `;
cosmetic.goal.musclePriorities = { chest: ' HIGH ' };
cosmetic.preferences.progressionStyle = ' Double ';
const normalized = normalizeRequest(cosmetic);
assert.equal(normalized.goal.type, 'strength');
assert.equal(normalized.athlete.experience, 'advanced');
assert.equal(normalized.schedule.days[0].day, 'monday');
assert.equal(normalized.goal.musclePriorities.chest, 'high');
assert.equal(normalized.preferences.progressionStyle, 'double');
const reject = (mutate, re) => { const bad = structuredClone(request); mutate(bad); assert.throws(() => normalizeRequest(bad), re); };
reject(x => x.goal.type = 'oops', /Unsupported training goal/);
reject(x => x.athlete.experience = 'expert', /Unsupported training experience/);
reject(x => x.schedule.days[0].day = 'funday', /Unsupported training day/);
reject(x => x.schedule.days[0].maxMinutes = NaN, /valid time limit/);
reject(x => x.goal.musclePriorities = { chest: 'maximum' }, /Unsupported priority/);
reject(x => x.goal.liftPriorities = { snatch: 'high' }, /Unsupported lift priority target/);
reject(x => x.equipment.bodyweight = 'sometimes', /bodyweight mode/i);
reject(x => x.preferences.avoidedExercises = ['bb-bench', 7], /nonblank strings/);
reject(x => x.preferences.responseCapacityScale = '0.8', /Response capacity scale/);
reject(x => x.customExercises = {}, /Custom exercises must be a list/);
assert.equal(phasePolicyFor(' Peak ').phase, 'peak');
assert.throws(() => phasePolicyFor('oops'), /Unsupported training phase/);
assert.throws(() => generateProgram(request, { phase: 'oops' }), /Unsupported training phase/);
assert.throws(() => generateProgram(request, { blockWeeks: Infinity }), /Block weeks/);
assert.throws(() => generateProgram(request, { progressionStyle: {} }), /Progression style/);

const bench = EX_BY_ID['bb-bench'];
const custom = { id: 'custom-226', custom: true, weeks: 6, config: { ...config, progression: 'double' },
    days: [
        { id: 'push-a', label: 'Push A', primaryIndex: 0, exercises: [bench.id] },
        { id: 'push-b', label: 'Push B', primaryIndex: 0, exercises: [bench.id] }
    ],
    overrides: {
        'push-a:0': { sets: 2, reps: '10-15', rir: '2', progressionStyle: 'double' },
        'push-b:0': { sets: 4, reps: '6-10', rir: '2', progressionStyle: 'double' }
    }
};
const completed = (w, r, n = 2) => ({ weight: w, reps: r, sets: Array.from({ length: n }, () => ({ w, r, rir: 2, rirReported: true, done: true })) });
const log = (id, programId, dayId, date, perf, dayLabel) => ({ id, programId, dayId, dayLabel, date, unit: 'lb', perf: { [bench.id]: perf } });
const otherDay = log('other-day', custom.id, 'push-b', 3000, completed(210, 10, 4), 'Push B');
const otherProgram = log('other-program', 'different-program', 'push-a', 4000, completed(400, 15, 2), 'Push A');
for (const history of [[otherDay], [otherProgram], [otherProgram, otherDay]]) {
    const advice = sessionSuggestion(custom, custom.days[0], 0, null, 'lb', 1, history);
    assert.equal(advice.action, 'initial');
    assert.equal(advice.referenceOnly, true);
    assert.match(advice.reason, /not progressed from another day or program/);
    assert.equal(lastDayPerf(custom.days[0], {}, history, custom)[bench.id], undefined);
}
const sameDay = log('same-day', custom.id, 'push-a', 5000, completed(205, 15, 2), 'Push A');
const earned = sessionSuggestion(custom, custom.days[0], 0, null, 'lb', 1, [sameDay, otherProgram]);
assert.equal(earned.action, 'increase_load');
assert.equal(earned.referenceOnly, undefined);
assert.ok(earned.weight > 205);

const adaptiveProgram = { id: 'adaptive-current', config: { experience: 'intermediate' }, days: [{ id: 'd1', exercises: [bench.id] }] };
const perf = { [bench.id]: completed(200, 8, 3) };
const flat = (programId, date) => log(`flat-${programId}-${date}`, programId, 'd1', date, completed(200, 8, 3), 'Day 1');
const foreign = [flat('adaptive-other', 3000), flat('adaptive-other', 2000), flat('adaptive-other', 1000)];
assert.equal(stallCountFor(perf, bench, foreign, 'd1', adaptiveProgram), 0);
assert.equal(linearStalled(perf, bench, foreign, adaptiveProgram), false);
const own = [flat(adaptiveProgram.id, 1000), flat(adaptiveProgram.id, 3000), flat(adaptiveProgram.id, 2000)];
assert.equal(stallCountFor(perf, bench, own, 'd1', adaptiveProgram), 2);
assert.equal(linearStalled(perf, bench, own, adaptiveProgram), true);

const tmProgram = { id: 'tm-current', config: { unit: 'lb' }, trainingMaxUnit: 'lb', trainingMax: { [bench.id]: '100' } };
const amrap = (programId, date, reps) => ({ id: `amrap-${programId}-${date}`, programId, dayId: 'd1', date, unit: 'lb',
    perf: { [bench.id]: { weight: 100, reps, sets: [{ w: 100, r: reps, amrap: true, done: true }] } } });
const tmHistory = [amrap('tm-other', 5000, 10), amrap(tmProgram.id, 3000, 3)];
assert.equal(nextTMEvidence(tmProgram, tmHistory, bench.id).reps, 3);
assert.equal(projectNextTM(tmProgram, tmHistory, 'lb')[bench.id], 105);
assert.equal(nextTMEvidence(tmProgram, [amrap(tmProgram.id, 1000, 3), amrap(tmProgram.id, 6000, 10)], bench.id).reps, 10);
assert.equal(projectNextTM(tmProgram, [amrap(tmProgram.id, 1000, 3), amrap(tmProgram.id, 6000, 10)], 'lb')[bench.id], 110);

const squat = EX_BY_ID['back-squat'];
const gz = { id: 'gz-current', config: { unit: 'lb', percentScheme: 'gzclp' }, trainingMax: { [squat.id]: '225' }, gzStage: { [`${squat.id}:t1`]: 'not-a-stage' } };
const gzDay = { id: 'gz-day', primaryIndex: 0, exercises: [squat.id] };
const gzPlan = gzPlanFor(gz, gzDay, squat, 0, 'lb', []);
assert.equal(gzPlan.stage, 0);
assert.equal(gzPlan.sets.length, 5);
assert.equal(gzPlan.sets[0].weight, 225);

console.log('PASS M226: request/phase boundaries, custom-day ownership, adaptive program scoping, numeric TM projection and GZCLP imported-state safety.');
