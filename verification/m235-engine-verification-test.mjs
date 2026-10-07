import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { performance } from 'node:perf_hooks';
import * as api from '../modules/engine-api.js';
import { engineModuleGraph, moduleCycles } from './module-graph.mjs';
import { ENGINE_VERSION, ENGINE_COMPATIBLE_VERSIONS } from '../modules/next-engine/config.js';
import { DEFAULT_CANDIDATE_EVALUATION_LIMIT } from '../modules/next-engine/candidate-optimization.js';
import { validateEngineProgram } from '../modules/next-engine/domain-contracts.js';
import { createExerciseMap, EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { equipmentEligible } from '../modules/next-engine/realizer-ranking.js';
import { SUPPORTED_PHASES } from '../modules/next-engine/phase-policy.js';
import { deriveAthleteResponse } from '../modules/next-engine/athlete-response.js';

const read = path => fs.readFileSync(new URL('../' + path, import.meta.url), 'utf8');
const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
const profile = JSON.parse(read('BUILD_PROFILE.json'));
const contracts = read('scripts/verify-engine-contracts.mjs');
const sw = read('sw.js');
const parityRows = JSON.parse(read('verification/m205-generation-parity-results.json')).rows;
const golden = JSON.parse(read('verification/fixtures/engine-816-golden.json'));

assert.equal(manifest.milestone, 'M234', 'M235 verifies M234 rather than inventing a behaviorless release bump');
assert.equal(manifest.build, 823);
assert.equal(manifest.engineVersion, '0.65.6');
assert.equal(ENGINE_VERSION, '0.65.6', 'a new engine line must create an explicit M235 successor baseline');
assert.deepEqual(ENGINE_COMPATIBLE_VERSIONS, ['0.65.2', '0.65.3', '0.65.4', '0.65.5', '0.65.6']);
assert.equal(profile.nextRuntimeModules, 70);
assert.equal(Object.keys(manifest.runtimeFiles).filter(path => path.startsWith('modules/next-engine/')).length, 70);
assert.ok(manifest.runtimeFiles['modules/next-engine/candidate-optimization.js']);
assert.match(sw, /modules\/next-engine\/candidate-optimization\.js/);
assert.equal(DEFAULT_CANDIDATE_EVALUATION_LIMIT, 512);
assert.equal(parityRows.length, 13);
assert.equal(golden.length, 13);

for (const milestone of ['m228-canonical-boundary', 'm229-domain-contracts', 'm230-realizer-decomposition', 'm231-athlete-response', 'm232-live-autoregulation', 'm233-semantic-exercise-graph', 'm234-candidate-optimization'])
    assert.match(contracts, new RegExp(milestone), 'permanent contract runner lost ' + milestone);

for (const report of [
    'M228_CANONICAL_ENGINE_REPORT.md',
    'M229_DOMAIN_CONTRACTS_REPORT.md',
    'M230_REALIZER_DECOMPOSITION_REPORT.md',
    'M231_ATHLETE_RESPONSE_REPORT.md',
    'M232_LIVE_AUTOREGULATION_REPORT.md',
    'M233_SEMANTIC_EXERCISE_GRAPH_REPORT.md',
    'M234_CANDIDATE_OPTIMIZATION_REPORT.md',
    'M235_ENGINE_VERIFICATION_2_REPORT.md'
])
    assert.ok(fs.existsSync(new URL('../' + report, import.meta.url)), 'missing milestone report: ' + report);

assert.deepEqual(moduleCycles(engineModuleGraph()), [], 'M228-M234 must finish with an acyclic engine module graph');

const projectionHash = value => crypto.createHash('sha256').update(JSON.stringify(value, (key, item) =>
    key === 'note' && typeof item === 'string' && /^Pursuit Engine \d+\.\d+\.\d+$/.test(item)
        ? 'Pursuit Engine [version]' : item)).digest('hex');

function projectionFor(shellProgram) {
    return {
        sessions: shellProgram.nextEngine.program.sessions,
        weeks: shellProgram.nextWeekPrescriptions,
        cells: shellProgram.days.flatMap(day => day.exercises.flatMap((id, slot) =>
            [1, 3, 6].map(week => api.shell.computeCell(shellProgram, day, id, slot, week)))),
        volume: api.shell.weeklyVolume(shellProgram),
        weekPlan: api.shell.buildWeekPlan(shellProgram)
    };
}

function assertFiniteTree(value, path = 'root', seen = new Set()) {
    if (typeof value === 'number') {
        assert.ok(Number.isFinite(value), path + ' must be finite');
        return;
    }
    if (!value || typeof value !== 'object' || seen.has(value))
        return;
    seen.add(value);
    if (Array.isArray(value)) {
        value.forEach((item, index) => assertFiniteTree(item, path + '[' + index + ']', seen));
        return;
    }
    for (const [key, item] of Object.entries(value))
        assertFiniteTree(item, path + '.' + key, seen);
}

function assertProgramProperties(result, label) {
    const program = result.nextProgram;
    validateEngineProgram(program);
    assert.equal(program.audit?.result, 'pass', label + ': final audit must pass');
    assert.ok(SUPPORTED_PHASES.includes(program.phase), label + ': unsupported phase ' + String(program.phase));
    assertFiniteTree({ program, weeks: result.program.nextWeekPrescriptions }, label);

    const exerciseMap = createExerciseMap(result.request.customExercises ?? []);
    const sessionIds = program.sessions.map(session => session.id);
    assert.equal(new Set(sessionIds).size, sessionIds.length, label + ': duplicate session identity');

    for (const session of program.sessions) {
        assert.ok(session.estimatedMinutes <= session.maxMinutes,
            label + ': ' + session.id + ' exceeds max time (' + session.estimatedMinutes + ' > ' + session.maxMinutes + ')');
        const exerciseIds = session.exercises.map(exercise => exercise.exerciseId);
        assert.equal(new Set(exerciseIds).size, exerciseIds.length, label + ': duplicate exercise slot identity in ' + session.id);
        const scheduleDay = result.request.schedule.days.find(day => day.day === session.day);
        const barbellLimit = scheduleDay?.maxBarbellMovements ?? result.request.restrictions.maxBarbellMovementsPerDay;
        const barbellCount = session.exercises.filter(exercise => exerciseMap.get(exercise.exerciseId)?.flags?.barbell).length;
        if (Number.isFinite(barbellLimit))
            assert.ok(barbellCount <= barbellLimit, label + ': barbell cap exceeded in ' + session.id);

        for (const exercise of session.exercises) {
            const def = exerciseMap.get(exercise.exerciseId);
            assert.ok(def, label + ': missing exercise definition ' + exercise.exerciseId);
            assert.ok(equipmentEligible(def, session, result.request),
                label + ': unavailable equipment selected for ' + exercise.exerciseId + ' on ' + session.day);
            assert.ok(Number.isInteger(exercise.sets) && exercise.sets >= 1 && exercise.sets <= 20,
                label + ': invalid working-set count for ' + exercise.exerciseId);
            const prescription = exercise.prescription;
            assert.ok(Array.isArray(prescription.reps) && prescription.reps.length === 2
                && prescription.reps.every(Number.isFinite) && prescription.reps[0] >= 1
                && prescription.reps[1] >= prescription.reps[0], label + ': invalid reps for ' + exercise.exerciseId);
            assert.ok(Array.isArray(prescription.rir) && prescription.rir.length === 2
                && prescription.rir.every(Number.isFinite) && prescription.rir[0] >= 0
                && prescription.rir[1] >= prescription.rir[0] && prescription.rir[1] <= 10,
                label + ': invalid RIR for ' + exercise.exerciseId);
        }
    }

    for (const [slotKey, weeks] of Object.entries(result.program.nextWeekPrescriptions))
        for (const [week, cell] of Object.entries(weeks))
            assert.ok(Number.isInteger(cell.sets) && cell.sets >= 1 && cell.sets <= 20,
                label + ': shell cell ' + slotKey + ' week ' + week + ' has invalid sets');
}

const propertyCases = parityRows.flatMap((row, index) => [
    { label: 'baseline-' + index + '-' + row.label, config: row.config, seed: row.seed, goldenIndex: index },
    { label: 'alternate-' + index + '-' + row.label, config: row.config, seed: row.seed + 50000, goldenIndex: null }
]);
const fixedNow = 1791388800000;
const originalNow = Date.now;
const baselineTimes = [];
let propertyChecks = 0;
Date.now = () => fixedNow;
try {
    for (const testCase of propertyCases) {
        const config = structuredClone(testCase.config);
        const configBefore = structuredClone(config);
        const start = performance.now();
        const first = api.generateNextProgramForShell({
            config,
            legacyExercises: api.shell.EXERCISES,
            seed: testCase.seed,
            makeId: () => 'm235-' + testCase.label
        });
        const elapsed = performance.now() - start;
        assert.deepEqual(config, configBefore, testCase.label + ': generation mutated caller-owned config');
        assertProgramProperties(first, testCase.label);

        const second = api.generateNextProgramForShell({
            config: structuredClone(testCase.config),
            legacyExercises: api.shell.EXERCISES,
            seed: testCase.seed,
            makeId: () => 'm235-' + testCase.label
        });
        assert.deepEqual(second.request, first.request, testCase.label + ': same seed changed canonical request');
        assert.deepEqual(second.nextProgram, first.nextProgram, testCase.label + ': same seed changed engine program');
        assert.equal(projectionHash(projectionFor(second.program)), projectionHash(projectionFor(first.program)),
            testCase.label + ': same seed changed executable shell projection');

        if (testCase.goldenIndex !== null) {
            baselineTimes.push({ label: testCase.label, ms: elapsed });
            const actual = {
                name: golden[testCase.goldenIndex].name,
                hash: projectionHash(projectionFor(first.program))
            };
            assert.deepEqual(actual, golden[testCase.goldenIndex],
                'Build 816 / Engine 0.64.16 differential changed in scenario ' + testCase.goldenIndex);
        }
        propertyChecks++;
    }
} finally {
    Date.now = originalNow;
}

assert.equal(propertyChecks, 26, 'M235 property matrix must keep both seeds for every certified scenario');
const benchmarkTotal = baselineTimes.reduce((sum, row) => sum + row.ms, 0);
const benchmarkSlowest = Math.max(...baselineTimes.map(row => row.ms));
assert.ok(benchmarkTotal <= 30000,
    'M235 generation benchmark exceeded 30,000 ms aggregate budget: ' + benchmarkTotal.toFixed(1) + ' ms');
assert.ok(benchmarkSlowest <= 8000,
    'M235 generation benchmark exceeded 8,000 ms scenario budget: ' + benchmarkSlowest.toFixed(1) + ' ms');

const asOf = Date.parse('2026-10-07T12:00:00Z');
const historyStart = asOf - 42 * 86400000;
const replayExercise = [...EXERCISE_MAP.values()].find(def => def.muscles.chest?.credit === 1 && def.flags.compound);
assert.ok(replayExercise, 'M235 replay requires a reviewed compound chest exercise');
const replayWorkout = (index, overrides = {}) => ({
    programId: 'm235-owned',
    historyId: 'm235-history-' + index,
    unit: 'lb',
    completedAt: new Date(historyStart + index * 7 * 86400000).toISOString(),
    session: {
        id: 'day-A',
        exercises: [{
            exerciseId: replayExercise.id,
            sets: 3,
            prescription: { reps: [8, 12], rir: [1, 3] }
        }]
    },
    performedSets: Array.from({ length: 3 }, () => ({
        exerciseId: replayExercise.id,
        load: 100 + index * 2,
        reps: 10,
        rir: 2
    })),
    progression: [{ exerciseId: replayExercise.id, outcome: 'success' }],
    ...overrides
});
const replayHistory = Array.from({ length: 6 }, (_, index) => replayWorkout(index));
const responseInput = { programId: 'm235-owned', workouts: replayHistory, unit: 'lb', asOf };
const replayKey = JSON.stringify(['day-A', replayExercise.id]);
const replaySummary = model => ({
    comparableExposures: model.evidence.comparableExposures,
    comparableWorkouts: model.evidence.comparableWorkouts,
    successes: model.exerciseSuccess[replayExercise.id]?.successes ?? 0,
    chestDoseScale: model.muscles.chest?.doseScale ?? null,
    chestFrequency: model.frequency.chest?.observedPerWeek ?? null,
    recovery: model.recovery.status,
    capacityScale: model.fatigue.capacityScale,
    progressionVelocity: model.progressionVelocity[replayKey]?.relativePerWeek ?? null
});
const replayModel = deriveAthleteResponse(responseInput);
const expectedReplay = replaySummary(replayModel);
assert.equal(expectedReplay.comparableExposures, 6);
assert.equal(expectedReplay.comparableWorkouts, 6);
assert.equal(expectedReplay.successes, 6);
assert.equal(expectedReplay.chestDoseScale, 1);
assert.equal(expectedReplay.chestFrequency, 1);
assert.equal(expectedReplay.recovery, 'tolerated');
assert.equal(expectedReplay.capacityScale, 1);
assert.ok(expectedReplay.progressionVelocity > 0);

assert.deepEqual(replaySummary(deriveAthleteResponse({ ...responseInput, workouts: [...replayHistory].reverse() })),
    expectedReplay, 'history replay changed with input order');
assert.deepEqual(replaySummary(deriveAthleteResponse(JSON.parse(JSON.stringify(responseInput)))),
    expectedReplay, 'history replay changed after JSON round-trip');
const kgHistory = replayHistory.map(workout => ({
    ...workout,
    unit: 'kg',
    performedSets: workout.performedSets.map(set => ({ ...set, load: set.load / 2.2046226218487757 }))
}));
assert.deepEqual(replaySummary(deriveAthleteResponse({ ...responseInput, workouts: kgHistory })),
    expectedReplay, 'history replay changed after physically equivalent unit conversion');
const foreignReplay = deriveAthleteResponse({
    ...responseInput,
    workouts: replayHistory.map(workout => ({ ...workout, programId: 'foreign-program' }))
});
assert.equal(foreignReplay.evidence.comparableExposures, 0, 'foreign history leaked into owned athlete response');

async function requireMutationKill(label, sourcePath, mutate, checker) {
    const sourceUrl = new URL('../' + sourcePath, import.meta.url);
    const source = fs.readFileSync(sourceUrl, 'utf8');
    const mutated = mutate(source);
    assert.notEqual(mutated, source, label + ': mutation target was not found');
    const fileName = '.__m235-mutant-' + label.replace(/[^a-z0-9]+/gi, '-').toLowerCase() + '.mjs';
    const mutantUrl = new URL(fileName, sourceUrl);
    fs.writeFileSync(mutantUrl, mutated, 'utf8');
    try {
        let module;
        try {
            module = await import(mutantUrl.href + '?m235=' + encodeURIComponent(label));
        } catch (error) {
            assert.fail(label + ': mutant must compile before the contract is allowed to kill it: ' + error);
        }
        let killed = false;
        try {
            await checker(module);
        } catch (error) {
            if (error?.code === 'ERR_ASSERTION')
                killed = true;
            else
                throw error;
        }
        assert.equal(killed, true, label + ': verification suite failed to kill semantic mutation');
    } finally {
        if (fs.existsSync(mutantUrl))
            fs.unlinkSync(mutantUrl);
    }
}

await requireMutationKill(
    'candidate-hard-constraint',
    'modules/next-engine/candidate-optimization.js',
    source => source.replace('if (accept(candidate, index))', 'if (true)'),
    module => {
        const selected = module.selectBestCandidate([
            { id: 'blocked', eligible: false, score: 100 },
            { id: 'allowed-a', eligible: true, score: 4 },
            { id: 'allowed-b', eligible: true, score: 8 }
        ], {
            accept: candidate => candidate.eligible,
            score: candidate => candidate.score
        });
        assert.equal(selected.candidate.id, 'allowed-b', 'hard constraints must run before ranking');
    }
);

await requireMutationKill(
    'history-program-scope',
    'modules/next-engine/athlete-response.js',
    source => source.replace(
        "if (w?.programId !== programId) { exclude('unrelated_program'); continue; }",
        "if (false) { exclude('unrelated_program'); continue; }"
    ),
    module => {
        const foreign = module.deriveAthleteResponse({
            ...responseInput,
            workouts: replayHistory.map(workout => ({ ...workout, programId: 'foreign-program' }))
        });
        assert.equal(foreign.evidence.comparableExposures, 0, 'unrelated program history must be excluded');
    }
);

console.log(
    'PASS M235 Verification 2.0: ' + propertyChecks + ' deterministic property cases, 13/13 Build 816 differentials, '
    + 'stable historical replay, 2/2 mutation kills, acyclic lineage and performance budgets ('
    + benchmarkTotal.toFixed(1) + ' ms total, ' + benchmarkSlowest.toFixed(1) + ' ms slowest).'
);
