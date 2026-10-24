import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { EXERCISES, EX_BY_ID, exerciseAt, computeCell, prescribeSets,
    loggedWorkoutPerformance, resolveNextShellExerciseId, markUserPrescriptionOverride } from '../modules/engine-api.js';
import { generateNextCycleForShell, advanceNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { analyzeShellHistoryForNextEngine, nextWorkoutSuggestionForShell } from '../modules/next-engine/workout-history-adapter.js';
import { captureShellVolumeSnapshot } from '../modules/next-engine/volume-repair.js';
import { config, completedHistory, ids } from './m265-generation-fixture.mjs';

// M344: the M343 coverage reached entry into recovery; this gate proves
// recovery EXIT with real persisted sets, the reserved preview being resumed,
// and an ongoing plateau not becoming an unearned progression after the return.
const clone = x => JSON.parse(JSON.stringify(x));
const bounds = x => {
    const n = String(x ?? '').match(/\d+/g)?.map(Number) ?? [];
    return [n[0] ?? 5, n.at(-1) ?? 8];
};
const count = perf => Object.values(perf).reduce((a, x) =>
    a + x.sets.filter(s => !s.warm && !s.sub).length, 0);
const profiles = [
    { seed: 34411, split: 'upper_lower', days: 4, equipment: config.equipment },
    { seed: 34417, split: 'full_body', days: 3, equipment: ['dumbbell','bench','band'] }
];
const solo = process.env.M344_SEED ? Number(process.env.M344_SEED) : null;
if (solo !== null && !profiles.some(p => p.seed === solo)) throw Error('Invalid M344_SEED');
const selected = solo === null ? profiles : profiles.filter(p => p.seed === solo);
const trace = [];
function stage(name, data) {
    const line = { name, ...data };
    trace.push(line);
    console.log('M344_STAGE ' + JSON.stringify(line));
}
function reproduce(err, ctx) {
    const dir = process.env.M344_REPRO_DIR;
    if (dir) {
        const file = path.join(dir, 'm344-' + ctx.seed + '.json');
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, JSON.stringify(ctx, null, 2) + '\n');
        console.error('M344_REPRO_FILE ' + file);
    }
    console.error('M344_FAILURE ' + JSON.stringify({
        seed: ctx.seed, phase: ctx.phase, historyIds: ctx.history.map(x => x.id),
        error: String(err?.stack ?? err),
        command: 'M344_SEED=' + ctx.seed + ' node verification/m344-recovery-exit-plateau-test.mjs'
    }));
    throw err;
}
let sequence = 0;
function logDay(program, day, week, prior, options = {}) {
    const before = JSON.stringify({ program, prior });
    const data = [];
    let expected = 0;
    for (let slot = 0; slot < day.exercises.length; slot++) {
        const id = exerciseAt(program, day, slot, week);
        const ex = EX_BY_ID[id], cell = computeCell(program, day, id, slot, week);
        assert.ok(ex && cell && cell.sets > 0);
        const [floor, top] = bounds(cell.reps);
        const [rir] = bounds(cell.rir);
        const prescription = prescribeSets(program, day, ex, slot, week,
            'lb', null, null, {}, prior, false);
        const performed = prescription.map(s => {
            const working = !s.warm && !s.sub;
            if (working) expected++;
            const weight = Number(s.weight) > 0 ? Number(s.weight)
                : ex.equip?.includes('dumbbell') ? 25 : 95;
            return { ...s, weight: String(weight), done: true,
                reps: working ? String(options.plateau ? floor : top)
                    : s.sub && !Number(s.reps) ? '4' : String(s.reps || floor),
                ...(working ? { actualRIR: options.plateau ? rir : Math.min(10, rir + 1) } : {}) };
        });
        data.push({ id, slot, sets: performed });
    }
    const perf = loggedWorkoutPerformance(program, day, data, week, 'lb', {}, prior);
    assert.equal(count(perf), expected);
    for (const e of data)
        assert.equal(perf[e.id].prescription?.exerciseId,
            resolveNextShellExerciseId(program, day, e.slot, EX_BY_ID[e.id]));
    const stamp = Date.UTC(2026, 6, 1) + sequence++ * 86400000;
    const entry = clone({ id: 'm344-log-' + sequence, programId: program.id,
        dayId: day.id, dayLabel: day.label, weekIndex: week,
        unit: 'lb', date: stamp, updatedAt: stamp, perf });
    assert.equal(JSON.stringify({ program, prior }), before,
        'Logging altered the source program or old history');
    return entry;
}
function logPlateau(program, day, slot, prior, reps, style) {
    const id = exerciseAt(program, day, slot, 1), ex = EX_BY_ID[id];
    const cell = computeCell(program, day, id, slot, 1);
    assert.equal(cell.progressionStyle, style);
    const suggestion = { weight: 100, target: 8, action: 'initial' };
    const rows = prescribeSets(program, day, ex, slot, 1, 'lb',
        suggestion, null, {}, prior, false);
    assert.equal(rows.filter(s => !s.warm && !s.sub).length, 3);
    const data = [{ id, slot, sets: rows.map(row => ({
        ...row, done: true, weight: '100', reps: String(reps), actualRIR: 2
    })) }];
    const perf = loggedWorkoutPerformance(program, day, data, 1, 'lb', {}, prior);
    assert.equal(perf[id].sets.length, 3);
    const stamp = Date.UTC(2026, 8, 1) + sequence++ * 86400000;
    const entry = clone({ id: 'm344-plateau-' + sequence, programId: program.id,
        dayId: day.id, dayLabel: day.label, weekIndex: 1,
        unit: 'lb', date: stamp, updatedAt: stamp, perf });
    return { entry, id };
}
for (const profile of selected) {
    let context = { seed: profile.seed, phase: 'creation', history: [] };
    try {
        const built = generateNextCycleForShell({
            templateId: 'custom', config: { ...config, name: 'M344 recovery exit ' + profile.seed,
                days: profile.days, split: profile.split, equipment: profile.equipment,
                weeks: 2, session: 's90' },
            blocks: [{ phase: 'hypertrophy_accumulation', weeks: 2 },
                { phase: 'strength_accumulation', weeks: 2 },
                { phase: 'mixed_accumulation', weeks: 2 }],
            legacyExercises: EXERCISES, seed: profile.seed, makeId: ids('m344-' + profile.seed),
            adaptBetweenBlocks: true, adaptTrainingSplit: true
        });
        const source = clone(built.blocks[0]);
        const preview = built.blocks[1].id;
        const authored = JSON.stringify(built.cycle.nextEngineCycle.plannedBlocks);
        const history = completedHistory(source).slice(0, 5).map((_, i) => {
            const entry = clone(completedHistory(source)[0]);
            entry.id = 'm344-tired-' + profile.seed + '-' + i;
            entry.date = Date.UTC(2026, 4, i + 1);
            entry.updatedAt = entry.date;
            Object.values(entry.perf).forEach(lift => {
                lift.weight = 140 - 10 * i;
                lift.reps = 1;
                lift.sets.forEach(s => { s.w = lift.weight; s.r = 1; s.rir = 0; s.rirReported = true; });
            });
            return entry;
        });
        context.history = history;
        const entryAnalysis = analyzeShellHistoryForNextEngine(source, history, EXERCISES);
        assert.equal(entryAnalysis.recovery.status, 'deload_recommended');
        const inserted = advanceNextCycleForShell({ cycle: clone(built.cycle),
            activeProgram: clone(source), history: clone(history), legacyExercises: EXERCISES,
            makeId: ids('m344-recovery-' + profile.seed) });
        assert.equal(inserted.insertedRecovery, true);
        const cycle = clone(inserted.cycle), recovery = clone(inserted.nextProgram);
        assert.equal(cycle.blockMeta[cycle.activeBlock].phase, 'recovery');
        assert.equal(cycle.blockMeta[cycle.activeBlock].resumePlannedIndex, 1);
        assert.equal(cycle.blockIds[cycle.activeBlock + 1], preview);
        stage('recovery-entry', { seed: profile.seed,
            recoveryProgram: recovery.id, reservedStrengthPreview: preview });

        context.phase = 'recovery'; context.history = [...history];
        let checkpoint = null;
        for (let i = 0; i < recovery.days.length; i++) {
            const entry = logDay(recovery, recovery.days[i], 1, context.history);
            context.history = clone([...context.history, entry]);
            const analysis = analyzeShellHistoryForNextEngine(recovery, context.history, EXERCISES);
            assert.equal(analysis.workoutCount, i + 1, 'Past fatigue was counted as recovery exposure');
            assert.equal(analysis.performedSetCount, context.history.filter(e => e.programId === recovery.id)
                .reduce((sum, e) => sum + count(e.perf), 0));
            if (analysis.readyForNextBlock) { checkpoint = analysis; break; }
        }
        assert.ok(checkpoint, 'Successfully logged recovery sessions did not provide exit evidence');
        assert.equal(checkpoint.recovery.status, 'normal');
        assert.equal(checkpoint.cycleState.status, 'review_eligible');
        assert.ok(checkpoint.cycleState.recoveryExitEvidence >= 2);
        stage('recovery-eligible', { seed: profile.seed, workouts: checkpoint.workoutCount,
            evidence: checkpoint.cycleState.recoveryExitEvidence });

        const beforeAdvance = JSON.stringify({ cycle, recovery, history: context.history });
        const resumed = advanceNextCycleForShell({ cycle: clone(cycle),
            activeProgram: clone(recovery), history: clone(context.history),
            legacyExercises: EXERCISES, makeId: ids('m344-resume-' + profile.seed) });
        assert.equal(JSON.stringify({ cycle, recovery, history: context.history }), beforeAdvance,
            'Recovery exit mutated saved state');
        assert.equal(resumed.insertedRecovery, false);
        assert.equal(resumed.done, false);
        assert.equal(resumed.nextProgram.id, preview, 'Recovery exit lost reserved next phase');
        assert.equal(resumed.cycle.blockMeta[resumed.cycle.activeBlock].preview, false);
        assert.equal(resumed.cycle.blockMeta[resumed.cycle.activeBlock].phase,
            'strength_accumulation');
        assert.equal(resumed.nextProgram.nextEngine.program.phase, 'strength_accumulation');
        assert.equal(resumed.nextProgram.nextEngine.program.audit.result, 'pass');
        assert.equal(JSON.stringify(resumed.cycle.nextEngineCycle.plannedBlocks), authored);
        assert.equal(resumed.cycle.nextEngineCycle.recoveryInsertions, 1);
        assert.ok(resumed.nextProgram.nextEngine.request.preferences?.responseCapacityScale !== .72,
            'Recovery-only fatigue capacity was carried into the new training phase');
        const resumedView = captureShellVolumeSnapshot(resumed.nextProgram, 1, EXERCISES);
        assert.ok(!resumedView.missing);
        assert.ok(resumedView.sessions.every(s => s.estimatedMinutes <= s.maxMinutes + .001));
        stage('recovery-exit-and-return', { seed: profile.seed, strengthId: preview,
            recoveryInsertions: resumed.cycle.nextEngineCycle.recoveryInsertions });

        // Progression plateaus: three comparable but below-ceiling workouts must
        // never earn load. Change methods after the third exposure and verify
        // the old history and target remain immutable before the next workout.
        context.phase = 'plateau';
        const active = clone(resumed.nextProgram), day = active.days[0];
        const slot = day.exercises.findIndex((id, i) => ['hypertrophy_isolation',
            'hypertrophy_compound','accessory'].includes(computeCell(active, day, id, i, 1).role));
        assert.ok(slot >= 0);
        const override = key => {
            for (const [field, value] of [['sets', 3], ['reps', '8-12'], ['rir', '2'],
                ['progressionStyle', 'double']])
                active.overrides[key] = markUserPrescriptionOverride(active.overrides[key] || {}, field, value);
        };
        const key = day.id + ':' + slot; override(key);
        assert.equal(computeCell(active, day, day.exercises[slot], slot, 1).sets, 3);
        for (let i = 0; i < 3; i++) {
            const { entry } = logPlateau(active, day, slot, context.history, 9, 'double');
            context.history = clone([...context.history, entry]);
            const advise = nextWorkoutSuggestionForShell(active, context.history, EXERCISES,
                day, slot, 1, 'lb');
            assert.ok(advise);
            assert.notEqual(advise.action, 'increase_load',
                'Repeated below-ceiling sets produced an unearned increase');
            assert.ok(advise.weight <= 100, 'Plateau raised the load despite repeated misses');
        }
        const beforeMethodChange = JSON.stringify(context.history);
        active.overrides[key] = markUserPrescriptionOverride(active.overrides[key],
            'progressionStyle', 'dynamic');
        assert.equal(computeCell(active, day, day.exercises[slot], slot, 1)
            .progressionStyle, 'dynamic');
        assert.equal(JSON.stringify(context.history), beforeMethodChange,
            'Changing progression mode rewrote historical logged performance');
        const next = nextWorkoutSuggestionForShell(active, context.history, EXERCISES,
            day, slot, 1, 'lb');
        assert.ok(next && next.action !== 'increase_load');
        stage('plateau-method-change', { seed: profile.seed, plateauWorkouts: 3,
            from: 'double', to: 'dynamic', advice: next.action, load: next.weight });
    } catch (err) { reproduce(err, context); }
}
console.log('ENGINE_RECOVERY_EXIT_AUDIT ' + JSON.stringify({ status: 'pass',
    profiles: selected.map(x => x.seed), stages: trace }));
