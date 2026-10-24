import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
    EXERCISES, EX_BY_ID, exerciseAt, computeCell, getNextShellCell,
    prescribeSets, sessionSuggestion, loggedWorkoutPerformance,
    normalizeEditedHistoryEntry, resolveNextShellExerciseId,
    summarizeSession, markUserPrescriptionOverride
} from '../modules/engine-api.js';
import { generateNextCycleForShell, advanceNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { analyzeShellHistoryForNextEngine, nextWorkoutSuggestionForShell } from '../modules/next-engine/workout-history-adapter.js';
import { captureShellVolumeSnapshot } from '../modules/next-engine/volume-repair.js';
import { config, completedHistory, ids } from './m265-generation-fixture.mjs';

// M343: genuinely logged workouts across four adaptive blocks, followed by
// a separate causal premature-fatigue -> inserted recovery -> reserved-preview
// branch. The previous M340-342 gates retain deeper per-set/mobile coverage.
const clone = x => JSON.parse(JSON.stringify(x));
const range = x => {
    const nums = String(x ?? '').match(/\d+/g)?.map(Number) || [];
    return [nums[0] ?? 2, nums.at(-1) ?? 6];
};
const workingCount = perf => Object.values(perf).reduce((n, lift) =>
    n + lift.sets.filter(s => !s.warm && !s.sub).length, 0);
const blockSpecs = [
    { phase: 'hypertrophy_accumulation', weeks: 2 },
    { phase: 'strength_accumulation', weeks: 2 },
    { phase: 'mixed_accumulation', weeks: 2 },
    { phase: 'strength_accumulation', weeks: 2 }
];
const profiles = [
    { seed: 34311, label: 'upper-lower', days: 4, split: 'upper_lower',
        equipment: config.equipment, methods: ['double','dynamic','ladder','e1rm'] },
    { seed: 34317, label: 'full-body-db', days: 3, split: 'full_body',
        equipment: ['dumbbell','bench','band'], methods: ['ladder','double','e1rm','dynamic'] }
];
const onlySeed = process.env.M343_SEED ? Number(process.env.M343_SEED) : null;
if (onlySeed !== null && !profiles.some(p => p.seed === onlySeed))
    throw new Error('M343_SEED must be ' + profiles.map(p => p.seed).join(' or '));
const selected = onlySeed === null ? profiles : profiles.filter(p => p.seed === onlySeed);
const metrics = { profiles: [], workouts: 0, workingSets: 0, phaseTransitions: 0,
    methods: [], historyCorrections: 0, recoveryInsertions: 0 };
const record = stage => console.log('M343_STAGE ' + JSON.stringify(stage));
let serial = 0;
function build(profile) {
    const result = generateNextCycleForShell({
        templateId: 'custom', config: { ...config, name: 'M343 ' + profile.label,
            days: profile.days, split: profile.split, equipment: profile.equipment, weeks: 2 },
        blocks: blockSpecs, legacyExercises: EXERCISES, seed: profile.seed,
        makeId: ids('m343-' + profile.seed), adaptBetweenBlocks: true, adaptTrainingSplit: true
    });
    assert.equal(result.blocks.length, 4);
    assert.ok(result.blocks.every(p => p.nextEngine?.program?.audit?.result === 'pass'));
    assert.deepEqual(result.cycle.blockIds, result.blocks.map(p => p.id));
    return result;
}
function persistDay(program, day, week, history, options = {}) {
    const start = JSON.stringify({ program, history });
    let expected = 0;
    const data = day.exercises.map((_, slot) => {
        const id = exerciseAt(program, day, slot, week);
        const ex = EX_BY_ID[id], cell = computeCell(program, day, id, slot, week);
        const canonical = getNextShellCell(program, day, slot, week);
        assert.ok(ex && !cell.missing && cell.sets > 0);
        assert.equal(Number(cell.sets), Number(canonical.sets));
        assert.equal(String(cell.reps), String(canonical.reps));
        const advice = slot === 0
            ? sessionSuggestion(program, day, slot, null, 'lb', week, history) : null;
        const rows = prescribeSets(program, day, ex, slot, week,
            'lb', advice, null, {}, history, true);
        assert.deepEqual(rows, prescribeSets(program, day, ex, slot, week,
            'lb', advice, null, {}, history, true), 'Identical reads changed the prescription');
        const [lo, hi] = range(cell.reps), [rir] = range(cell.rir);
        const sets = rows.map((row, index) => {
            const working = !row.warm && !row.sub;
            if (working) expected++;
            const load = Number(row.weight) > 0 ? row.weight
                : ex.equip?.includes('dumbbell') ? '25' : '95';
            return { ...row, done: true, weight: String(load),
                reps: working ? String(options.hard ? lo : hi)
                    : row.sub && !Number(row.reps) ? '4' : String(row.reps || lo),
                ...(working ? { actualRIR: options.hard ? 0 : Math.min(10, rir + 1) } : {}) };
        });
        return { id, slot, sets };
    });
    const perf = loggedWorkoutPerformance(program, day, data, week, 'lb', {}, history);
    assert.equal(workingCount(perf), expected, 'Saved completed set count changed');
    assert.equal(summarizeSession(program, day, week).sets, expected,
        'Program/Plan disagrees with executable and completed workout');
    for (const { id, slot } of data)
        assert.equal(perf[id].prescription?.exerciseId,
            resolveNextShellExerciseId(program, day, slot, EX_BY_ID[id]),
            'Original prescription lost canonical exercise identity');
    const stamp = Date.UTC(2026, 5, 1) + serial++ * 86400000;
    const entry = clone({ id: 'm343-log-' + serial, programId: program.id,
        dayId: day.id, dayLabel: day.label, weekIndex: week,
        date: stamp, updatedAt: stamp, unit: 'lb', perf });
    assert.equal(workingCount(entry.perf), expected, 'JSON roundtrip damaged performance');
    assert.equal(JSON.stringify({ program, history }), start,
        'Production logging modified saved program or existing history');
    metrics.workouts++;
    metrics.workingSets += expected;
    return entry;
}
function fail(error, context) {
    const filepath = process.env.M343_REPRO_DIR
        ? path.join(process.env.M343_REPRO_DIR,
            'm343-' + context.seed + '-block' + context.block + '.json') : null;
    if (filepath) {
        fs.mkdirSync(path.dirname(filepath), { recursive: true });
        fs.writeFileSync(filepath, JSON.stringify(context, null, 2) + '\n');
        console.error('M343_REPRO_FILE ' + filepath);
    }
    console.error('M343_FAILURE ' + JSON.stringify({
        seed: context.seed, block: context.block, phase: context.program?.nextEngine?.program?.phase,
        historyIds: context.history.map(x => x.id),
        error: String(error?.stack || error),
        command: 'M343_SEED=' + context.seed + ' node verification/m343-multicycle-recovery-test.mjs'
    }));
    throw error;
}
for (const profile of selected) {
    const built = build(profile);
    let cycle = clone(built.cycle), program = clone(built.blocks[0]), history = [];
    const reserved = built.blocks.map(p => p.id);
    const immutableSpecs = JSON.stringify(cycle.nextEngineCycle.plannedBlocks);
    const transitions = [];
    for (let block = 0; block < blockSpecs.length; block++) {
        try {
            const method = profile.methods[block];
            const position = program.days.flatMap(day => day.exercises.map((id, slot) =>
                ({ day, id, slot }))).find(({ id, day, slot }) =>
                EX_BY_ID[id] && ['hypertrophy_isolation','hypertrophy_compound','accessory']
                    .includes(computeCell(program, day, id, slot, 1).role));
            if (position) {
                const key = position.day.id + ':' + position.slot;
                program.overrides[key] = markUserPrescriptionOverride(program.overrides[key] ?? {},
                    'progressionStyle', method);
                assert.equal(computeCell(program, position.day, position.id, position.slot, 1)
                    .progressionStyle, method);
                metrics.methods.push(method);
            }
            const priorHistory = history.length;
            for (let week = 1; week <= program.config.weeks; week++) {
                for (const day of program.days) {
                    history = clone([...history, persistDay(program, day, week, history)]);
                    const analysis = analyzeShellHistoryForNextEngine(program, history, EXERCISES);
                    assert.equal(analysis.workoutCount, history.length - priorHistory,
                        'Previous cycles polluted active-block workout count');
                    assert.equal(analysis.performedSetCount, history.slice(priorHistory).reduce(
                        (n, e) => n + workingCount(e.perf), 0),
                        'Previous cycles polluted active-block logged set volume');
                }
            }
            // A correction to one workout must be consumed once without altering
            // any past block or inflating adaptation evidence.
            const edited = clone(history[priorHistory]);
            const id = Object.keys(edited.perf)[0];
            edited.perf[id].sets[0].r += 1;
            const revision = normalizeEditedHistoryEntry(history[priorHistory], edited);
            const reviewed = analyzeShellHistoryForNextEngine(program,
                [history[priorHistory], ...history, revision], EXERCISES);
            assert.equal(reviewed.workoutCount, history.length - priorHistory);
            assert.equal(reviewed.performedSetCount,
                history.slice(priorHistory).reduce((n, e) => n + workingCount(e.perf), 0));
            metrics.historyCorrections++;
            const snapshot = JSON.stringify({ cycle, program, history });
            const analysis = analyzeShellHistoryForNextEngine(program, history, EXERCISES);
            assert.equal(analysis.plannedDayCoverage.observedDays, program.days.length);
            assert.equal(analysis.readyForNextBlock, true,
                'Complete healthy training block cannot advance');
            const result = advanceNextCycleForShell({ cycle: clone(cycle),
                activeProgram: clone(program), history: clone(history),
                legacyExercises: EXERCISES, makeId: ids('m343-adapt-' + profile.seed + '-' + block) });
            assert.equal(JSON.stringify({ cycle, program, history }), snapshot);
            assert.equal(JSON.stringify(result.cycle.nextEngineCycle.plannedBlocks), immutableSpecs,
                'Adaptation changed the authored phase plan');
            assert.equal(result.insertedRecovery, false,
                'Successful logged training inserted a surprise recovery week');
            metrics.phaseTransitions++;
            transitions.push({ phase: program.nextEngine.program.phase, method,
                sessions: analysis.workoutCount, recovery: analysis.recovery.status });
            if (block === blockSpecs.length - 1) {
                assert.equal(result.done, true, 'Final block did not finish cycle');
                assert.equal(result.cycle.done, true);
            } else {
                assert.equal(result.nextProgram.id, reserved[block + 1],
                    'Adaptation replaced the reserved future preview identity');
                assert.equal(result.cycle.activeBlock, block + 1);
                assert.equal(result.nextProgram.nextEngine?.program?.audit?.result, 'pass');
                const view = captureShellVolumeSnapshot(result.nextProgram, 1, EXERCISES);
                assert.ok(!view.missing);
                assert.ok(view.sessions.every(session =>
                    session.estimatedMinutes <= session.maxMinutes + .001),
                    'Adaptation exceeded selected training time');
                cycle = clone(result.cycle);
                program = clone(result.nextProgram);
            }
        } catch (error) { fail(error, { seed: profile.seed, block, program, cycle, history }); }
    }
    metrics.profiles.push({ seed: profile.seed, label: profile.label,
        transitions, workouts: history.length, plannedBlocks: reserved.length });
    record({ scenario: 'healthy-four-block', seed: profile.seed,
        workouts: history.length, transitions });
}

// Force an EARLY recovery recommendation with actually recorded history,
// rather than a fabricated return value. Verify the inserted recovery does
// not overwrite the pending strength preview and retains the exact phase plan.
{
    const p = profiles[0], built = build({ ...p, seed: 34329 });
    const initial = built.blocks[0];
    const base = completedHistory(initial);
    const tired = base.slice(0, 5).map((_, i) => {
        const entry = clone(base[0]);
        entry.id = 'm343-fatigue-' + i;
        entry.date = Date.UTC(2026, 9, i + 1);
        entry.updatedAt = entry.date;
        Object.values(entry.perf).forEach(lift => {
            lift.weight = 140 - i * 10;
            lift.reps = 1;
            lift.sets.forEach(set => {
                set.w = lift.weight; set.r = 1; set.rir = 0; set.rirReported = true;
            });
        });
        return entry;
    });
    const before = JSON.stringify({ cycle: built.cycle, initial, tired });
    const early = analyzeShellHistoryForNextEngine(initial, tired, EXERCISES);
    assert.equal(early.plannedDayCoverage.observedDays, 1);
    assert.equal(early.recovery.status, 'deload_recommended');
    assert.equal(early.readyForNextBlock, true);
    const inserted = advanceNextCycleForShell({ cycle: clone(built.cycle),
        activeProgram: clone(initial), history: clone(tired),
        legacyExercises: EXERCISES, makeId: ids('m343-early-recovery') });
    assert.equal(JSON.stringify({ cycle: built.cycle, initial, tired }), before);
    assert.equal(inserted.insertedRecovery, true);
    assert.equal(inserted.cycle.nextEngineCycle.recoveryInsertions, 1);
    assert.equal(inserted.nextProgram.nextEngine?.program?.audit?.result, 'pass');
    assert.equal(inserted.nextProgram.cycleId, built.cycle.id);
    assert.equal(inserted.cycle.blockIds[inserted.cycle.activeBlock], inserted.nextProgram.id);
    assert.equal(inserted.cycle.blockMeta[inserted.cycle.activeBlock].resumePlannedIndex, 1);
    assert.equal(inserted.cycle.blockIds[inserted.cycle.activeBlock + 1],
        built.blocks[1].id, 'Recovery displaced reserved phase-2 preview');
    assert.equal(inserted.cycle.blockMeta[inserted.cycle.activeBlock + 1].preview, true);
    assert.deepEqual(inserted.cycle.nextEngineCycle.plannedBlocks,
        built.cycle.nextEngineCycle.plannedBlocks);
    const insufficient = [...tired, ...completedHistory(inserted.nextProgram).slice(0, 1)];
    const analysis = analyzeShellHistoryForNextEngine(inserted.nextProgram, insufficient, EXERCISES);
    assert.equal(analysis.workoutCount, 1, 'Old pre-recovery fatigue evidence polluted new recovery program');
    assert.throws(() => advanceNextCycleForShell({ cycle: clone(inserted.cycle),
        activeProgram: clone(inserted.nextProgram), history: clone(insufficient),
        legacyExercises: EXERCISES, makeId: ids('m343-premature-exit') }),
        error => ['NEXT_CYCLE_NOT_READY','NEXT_CYCLE_RECOVERY_NOT_READY'].includes(error.code),
        'An unproven recovery exposure resumed the strength block');
    metrics.recoveryInsertions++;
    record({ scenario: 'premature-fatigue-and-protected-recovery',
        classification: early.classification, recovery: early.recovery.status,
        activeBlock: inserted.cycle.activeBlock, preservedPreview: built.blocks[1].id });
}
if (onlySeed === null) {
    assert.equal(metrics.profiles.length, 2);
    assert.equal(metrics.historyCorrections, 8);
    assert.equal(metrics.phaseTransitions, 8);
    assert.equal(metrics.recoveryInsertions, 1);
    assert.equal(new Set(metrics.methods).size, 4);
}
console.log('ENGINE_MULTICYCLE_AUDIT ' + JSON.stringify({
    status: 'pass', profiles: metrics.profiles, workouts: metrics.workouts,
    workingSets: metrics.workingSets, phaseTransitions: metrics.phaseTransitions,
    historyCorrections: metrics.historyCorrections,
    recoveryInsertions: metrics.recoveryInsertions,
    methods: [...new Set(metrics.methods)]
}));
