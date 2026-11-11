import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/engine-api.js';
import { shellConfigToNextRequest } from '../modules/next-engine/app-shell-adapter.js';
import { requestForBlock } from '../modules/next-engine/cycle-runtime-adapter.js';
import { createStrengthClaims, normalizeRequest } from '../modules/next-engine/prescription.js';
import { generateProgram } from '../modules/next-engine/generate.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';

// Full commercial gym, real five-day ULPPL source fixture. Unlike unit-only
// scoring tests, this audits the actual built weekly exercise/phase outputs.
const fixtures = JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json', import.meta.url)));
const config = { ...fixtures.rows[3].config, goal: 'both', split: 'ulppl', days: 5 };
const original = shellConfigToNextRequest(config, [], EXERCISES, 34410);
const base = normalizeRequest(original);
const before = JSON.stringify({ config, base });
const phases = ['hypertrophy_accumulation', 'strength_accumulation', 'intensification', 'peak'];
const result = [];

for (const phase of phases) {
    const request = requestForBlock(base, config, { phase, weeks: 5, split: 'ulppl' });
    assert.equal(request.goal.type, phase === 'hypertrophy_accumulation' ? 'hypertrophy' : 'strength');
    if (phase === 'hypertrophy_accumulation') {
        assert.equal(request.preferences.powerbuildingInclineVolume, undefined);
        assert.ok(Object.keys(request.goal.liftFrequency ?? {}).length === 0);
        continue;
    }
    assert.deepEqual(request.goal.liftFrequency, { bench_press: 2, back_squat: 2, deadlift: 1 },
        'Both must create distinct bench and squat practice, with a single fatigue-managed deadlift visit');
    assert.equal(request.preferences.powerbuildingInclineVolume, true);
    const claims = createStrengthClaims(request, phase);
    for (const [lift, expected] of Object.entries(request.goal.liftFrequency))
        assert.equal(claims.filter(c => c.lift === lift).length, expected,
            phase + ': the phase must have real strength-claim slots, not only a scoring target');
    const { program } = generateProgram(request, { phase, blockWeeks: 5 });
    assert.equal(program.audit.result, 'pass', phase + ': ' + JSON.stringify(program.audit).slice(0, 1000));
    const catalog = createExerciseMap(request.customExercises);
    const counts = Object.fromEntries(['bench_press','back_squat','deadlift'].map(lift => [lift,
        program.sessions.filter(s => s.exercises.some(e =>
            ['primary_strength', 'secondary_strength'].includes(e.role) &&
            (catalog.get(e.exerciseId)?.liftSpecificity?.[lift] ?? 0) > .45)).length]));
    assert.ok(counts.bench_press >= 2, phase + ' lost the second bench day');
    assert.ok(counts.back_squat >= 2, phase + ' lost the second squat-capable day');
    assert.ok(counts.deadlift >= 1, phase + ' lost the hinge practice');
    assert.ok(program.sessions.every(s => s.estimatedMinutes <= s.maxMinutes + .01),
        phase + ' exceeded an actual session time limit');
    const push = program.sessions.find(s => s.intent === 'push');
    assert.ok(push, phase + ' must retain its Push day');
    console.log('M344 AUDIT '+JSON.stringify({phase,flag:request.preferences.powerbuildingInclineVolume,
        sessions:program.sessions.map(s => ({intent:s.intent,day:s.day,presses:s.exercises
            .filter(e=>e.role==='primary_strength'||e.role==='secondary_strength')
            .map(e=>({id:e.exerciseId,role:e.role}))}))}));
    const incline = push.exercises.find(e => ['incline_db_press','incline_smith'].includes(e.exerciseId));
    assert.ok(incline && incline.role === 'secondary_strength',
        phase + ': an eligible incline press must be the secondary bench anchor on Push');
    result.push({ phase, counts, pushIncline: incline.exerciseId,
        minutes: program.sessions.map(s => Math.round(s.estimatedMinutes)),
        sets: program.sessions.reduce((sum, s) => sum + s.exercises.reduce((n, e) => n + e.sets, 0), 0) });
}
// Athlete intent is stronger than inferred defaults, including a chosen
// one-exposure frequency and a specific OHP focus. Never silently overwrite.
const custom = structuredClone(base);
custom.goal.liftFrequency = { bench_press: 1 };
const explicit = requestForBlock(custom, config, { phase: 'strength_accumulation', weeks: 5, split: 'ulppl' });
assert.equal(explicit.goal.liftFrequency.bench_press, 1);
assert.equal(explicit.goal.liftFrequency.back_squat, 2);
const preferred = structuredClone(base);
preferred.preferences.preferredExercises = ['paused_bench'];
const preferredPhase = requestForBlock(preferred, config, { phase: 'strength_accumulation', weeks: 5, split: 'ulppl' });
assert.deepEqual(preferredPhase.preferences.preferredExercises, ['paused_bench']);

// Preserve other training structures and all muscle-building phases. A
// 3-day Full Body is not allowed to invent five required lift exposures.
const threeDays = structuredClone(base);
threeDays.schedule.days = threeDays.schedule.days.slice(0, 3);
threeDays.preferences = { ...threeDays.preferences, lockedSplit: 'full_body', preferredSplit: 'full_body' };
const noChange = requestForBlock(threeDays, { ...config, split: 'full_body', days: 3 },
    { phase: 'strength_accumulation', weeks: 5, split: 'full_body' });
assert.ok(Object.keys(noChange.goal.liftFrequency ?? {}).length === 0);

assert.equal(JSON.stringify({ config, base }), before, 'Phase generation must not mutate athlete settings or past blocks');
console.log('M344 actual Both / ULPPL phase training audit: ' + JSON.stringify(result));
console.log('PASS M344: ULPPL Both strength/peak requires repeated bench and squat with a real Push incline variant; explicit intent remains intact.');
