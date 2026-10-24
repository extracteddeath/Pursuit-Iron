import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { EXERCISES, EX_BY_ID, computeCell, getNextShellCell, prescribeSets,
    sessionSuggestion, loggedWorkoutPerformance, nextSessionCursor,
    markUserPrescriptionOverride, summarizeSession, resolveNextShellExerciseId } from '../modules/engine-api.js';
import { generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { analyzeShellHistoryForNextEngine, nextWorkoutSuggestionForShell } from '../modules/next-engine/workout-history-adapter.js';
import { applyLiveAutoregulation, reconcileRestoredLiveAdjustments } from '../modules/next-engine/live-autoregulation.js';
import { refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
import { config, ids } from './m265-generation-fixture.mjs';

// M342. Deterministic property/replay testing on canonical production APIs.
// M340/M341 own deep single-cycle/single-exercise checks. This gate varies
// schedule, equipment, progression, fatigue, incomplete work and saved history
// ACROSS real generated multi-week programs, with seed-scoped replay.
const clone = value => JSON.parse(JSON.stringify(value));
const profiles = [
    { name: 'limited-full-body', days: 3, split: 'full_body', goal: 'both',
        equipment: ['dumbbell', 'bench', 'band'], style: 'double' },
    { name: 'upper-lower', days: 4, split: 'upper_lower', goal: 'hypertrophy',
        equipment: config.equipment, style: 'dynamic' },
    { name: 'five-day-ulppl', days: 5, split: 'ulppl', goal: 'both',
        equipment: config.equipment, style: 'ladder' },
    { name: 'barbell-full-body', days: 3, split: 'full_body', goal: 'strength',
        equipment: ['barbell', 'rack', 'bench', 'pullup', 'band', 'bodyweight'],
        style: 'e1rm' }
];
const seeds = [34201, 34207, 34213, 34219, 34223, 34231, 34237, 34243];
const onlySeed = process.env.M342_SEED ? Number(process.env.M342_SEED) : null;
if (onlySeed !== null && (!Number.isSafeInteger(onlySeed) || !seeds.includes(onlySeed)))
    throw Error('M342_SEED must be one of ' + seeds.join(', '));
const cases = onlySeed === null ? seeds : [onlySeed];
const metrics = { scenarios: 0, sessions: 0, skipped: 0, workingSets: 0, incomplete: 0,
    manual: 0, interrupted: 0, badDay: 0, fatigue: 0, liveAdjustments: 0,
    recommendations: 0, styles: new Set(), profiles: new Set() };
function failWithContext(error, context) {
    const dir = process.env.M342_REPRO_DIR;
    if (dir) {
        const file = path.join(dir, 'm342-' + context.seed + '-' + context.profile +
            '-w' + context.week + '-d' + context.dayIndex + '.json');
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, JSON.stringify(context, null, 2) + '\n');
        console.error('M342_REPRO_FILE ' + file);
    }
    console.error('M342_FAILURE ' + JSON.stringify({
        seed: context.seed, profile: context.profile, week: context.week,
        dayIndex: context.dayIndex, event: context.event,
        historyIds: context.history?.map(row => row.id),
        error: String(error?.stack ?? error),
        command: 'M342_SEED=' + context.seed +
            ' node verification/m342-seeded-longitudinal-test.mjs'
    }));
    throw error;
}
const numRange = text => {
    const digits = String(text ?? '').match(/\d+/g)?.map(Number) ?? [];
    return [digits[0] ?? 6, digits.at(-1) ?? 10];
};
const working = rows => rows.filter(row => !row.warm && !row.sub);
const recorded = perf => Object.values(perf).reduce((total, lift) =>
    total + lift.sets.filter(set => !set.warm && !set.sub).length, 0);
function doSession(p, day, week, history, event, context) {
    const before = JSON.stringify({ p, history });
    const data = [];
    let active = 0, intended = 0, chosen = null;
    const choose = index => (index + context.seed + week * 7) % 3 === 0;
    for (let slot = 0; slot < day.exercises.length; slot++) {
        const id = day.exercises[slot], ex = EX_BY_ID[id];
        const cell = computeCell(p, day, id, slot, week);
        if (!ex || !cell || cell.missing || !(cell.sets > 0))
            throw Error('Generated executable slot missing: ' + day.id + ':' + slot + ':' + id);
        const canonical = getNextShellCell(p, day, slot, week);
        assert.equal(String(cell.reps), String(canonical.reps));
        assert.equal(Number(cell.sets), Number(canonical.sets));
        const prior = sessionSuggestion(p, day, slot, null, 'lb', week, history);
        // An uncalibrated first exposure needs an explicit gym entry load.
        const suggestion = prior ?? { weight: 100, target: numRange(cell.reps)[0],
            action: 'initial', reason: 'No prior comparable work' };
        const rows = prescribeSets(p, day, ex, slot, week, 'lb', suggestion,
            null, {}, history, false);
        const work = working(rows);
        assert.equal(work.length, Number(cell.sets),
            'Working-set count drifted between week preview and live logger');
        assert.ok(work.every(row => row.target?.reps),
            'An auto-prescribed set has no historical target');
        assert.deepEqual(rows, prescribeSets(p, day, ex, slot, week,
            'lb', suggestion, null, {}, history, false),
        'Repeated read regenerated a different set-2 target');
        intended += work.length;
        const [lo, hi] = numRange(cell.reps);
        const rowMap = rows.map((row, i) => ({ ...clone(row), _idx: i }));
        const eligible = rowMap.filter(row => !row.warm && !row.sub);
        // Keep a real, bounded load when no prior history exists. The logger
        // retains the exact original prescribed target separately from the entry.
        const starting = Number(eligible[0]?.weight) > 0 ? Number(eligible[0].weight) : 100;
        const sawWork = eligible.length >= 2;
        const partial = event === 'incomplete' && choose(slot) && sawWork;
        const altered = event === 'manual' && choose(slot) && sawWork;
        const hard = event === 'fatigue' && eligible.length >= 3 &&
            ['hypertrophy_compound','hypertrophy_isolation','accessory'].includes(cell.role) &&
            !chosen && String(cell.progressionStyle) !== 'wave';
        const updated = rowMap.map((row, i) => {
            const activeRow = !row.warm && !row.sub, position = eligible.findIndex(v => v._idx === i);
            if (!activeRow) return { ...row, done: false };
            const completed = event === 'fatigue' ? position < 2
                : (!partial || position < eligible.length - 1);
            const effort = event === 'bad_day' || event === 'fatigue' ? 0
                : event === 'good' ? Math.min(10, numRange(cell.rir)[0] + 1)
                : numRange(cell.rir)[0];
            const reps = event === 'good' ? hi
                : event === 'bad_day' ? Math.max(1, lo - 2)
                : event === 'fatigue' && position < 2 ? Math.max(1, lo - 2)
                : Math.max(lo, hi - (position % 2) - 1);
            const base = Number(row.weight) > 0 ? Number(row.weight) : starting;
            return { ...row, done: completed,
                weight: String(base), reps: completed ? String(reps) : row.reps,
                ...(completed ? { actualRIR: effort } : {}) };
        }).map(({ _idx, ...row }) => row);
        if (altered) {
            const index = updated.findIndex((row, i) => !row.warm && !row.sub &&
                eligible.findIndex(v => v._idx === i) === 1);
            const changed = clone(updated);
            changed[index].weight = String(Number(changed[index].weight) + 5);
            changed[index].valueOwner = 'user';
            changed[index].auto = false;
            changed[index].manualOverride = true;
            const fresh = refreshPendingSetTargets(clone(changed), clone(rows));
            assert.equal(fresh[index].weight, changed[index].weight,
                'User load was overwritten by the next prefill');
            assert.deepEqual(changed, clone(changed));
            updated.splice(0, updated.length, ...fresh);
            active++;
        }
        if (hard) {
            const snap = w => Math.floor(w / 5) * 5;
            const attempt = applyLiveAutoregulation({ sets: updated, sourceIndex: eligible[1]._idx,
                cell, snapLoad: snap });
            const replayed = reconcileRestoredLiveAdjustments(clone(attempt.sets));
            assert.deepEqual(replayed, clone(attempt.sets),
                'Evidence-backed adjustment changed merely by resuming');
            if (attempt.decision.action === 'reduce_pending_load') {
                const newRow = replayed.find(row => row.liveAdjustment?.action === 'reduce_pending_load');
                assert.ok(newRow?.recoveryLimited && !newRow.done);
                assert.ok(Number(newRow.weight) < starting,
                    'Fatigue adjustment increased a pending load');
                metrics.liveAdjustments++;
            }
            updated.splice(0, updated.length, ...replayed.map(row => {
                if (row.warm || row.sub || row.done) return row;
                return { ...row, done: true, reps: String(Math.max(1, lo)), actualRIR: 1 };
            }));
            chosen = true;
        }
        // Real interruption: reload completed/live rows, not a new invented
        // prescription. Changes must survive the resume reconciliation.
        if (event === 'interrupted' && slot === 0) {
            const restored = refreshPendingSetTargets(clone(updated), clone(rows));
            assert.equal(JSON.stringify(working(restored).filter(row => row.done)),
                JSON.stringify(working(updated).filter(row => row.done)));
            updated.splice(0, updated.length, ...reconcileRestoredLiveAdjustments(clone(restored)));
        }
        data.push({ id, slot, sets: updated });
    }
    assert.ok(data.length);
    const perf = loggedWorkoutPerformance(p, day, data, week, 'lb', {}, history);
    const expected = data.reduce((n, e) => n + e.sets.filter(s =>
        s.done && !s.warm && !s.sub && Number(s.reps) > 0).length, 0);
    const total = recorded(perf);
    assert.equal(total, expected, 'Logged working sets were lost or doubled');
    assert.ok(total > 0);
    assert.ok(total <= intended, 'Session recorded more work than the plan prescribed');
    if (!['incomplete'].includes(event))
        assert.equal(total, intended, 'Full session dropped working sets');
    assert.equal(summarizeSession(p, day, week).sets, intended,
        'Plan summary and actual prescribed working sets diverged');
    for (const lift of data) {
        if (!perf[lift.id]) continue;
        const engineId = resolveNextShellExerciseId(p, day, lift.slot, EX_BY_ID[lift.id]);
        assert.equal(perf[lift.id].prescription?.exerciseId, engineId);
    }
    const date = Date.UTC(2026, 7, 1) + (week * 10 + context.dayIndex) * 86400000;
    const entry = clone({ id: 'm342-' + context.seed + '-' + week + '-' + context.dayIndex,
        programId: p.id, dayId: day.id, dayLabel: day.label,
        weekIndex: week, unit: 'lb', date, updatedAt: date, perf,
        ...(event === 'interrupted' ? { workoutInterrupted: true } : {}),
        ...(event === 'bad_day' ? { badDay: true } : {}) });
    assert.equal(recorded(entry.perf), total);
    const next = clone([...history, entry]);
    const analysis = analyzeShellHistoryForNextEngine(p, next, EXERCISES, { asOf: Date.UTC(2026, 9, 1) });
    assert.equal(analysis.workoutCount, next.length,
        'Previously stored workout disappeared or newly duplicated during replay');
    assert.equal(analysis.performedSetCount,
        next.reduce((n, h) => n + recorded(h.perf), 0),
        'Progress analysis lost working sets or double-counted a completed session');
    // The shell's next target must be grounded in the exact saved exposure.
    for (const lift of data.slice(0, 2)) {
        const advice = nextWorkoutSuggestionForShell(p, next, EXERCISES,
            day, lift.slot, week, 'lb');
        if (!advice) continue;
        assert.ok(advice.reason && advice.action);
        assert.deepEqual(advice.last, entry.perf[lift.id], 'LAST mismatches persisted work');
        metrics.recommendations++;
        if (event === 'interrupted' || event === 'bad_day' ||
            (event === 'incomplete' && total < intended) ||
            (event === 'manual' && active > 0)) {
            // An authored phase may shift week targets, but this exposure
            // cannot independently EARN a heavier load.
            const decision = analysis.workouts.at(-1)?.progression
                .find(d => d.exerciseId === resolveNextShellExerciseId(p, day,
                    lift.slot, EX_BY_ID[lift.id]));
            if (decision && (event !== 'manual' ||
                entry.perf[lift.id].sets.some(set => set.manualOverride)))
                assert.notEqual(decision.action, 'increase_load',
                    'Incomplete, interrupted or manual evidence earned load progression');
        }
    }
    assert.equal(JSON.stringify({ p, history }), before,
        'Reads or logging changed the source program or pre-workout history');
    metrics.workingSets += total;
    return next;
}
for (const seed of cases) {
    const profile = profiles[seeds.indexOf(seed) % profiles.length];
    const built = generateNextProgramForShell({
        config: { ...config, name: 'M342 ' + profile.name + ' ' + seed,
            days: profile.days, split: profile.split, goal: profile.goal,
            equipment: profile.equipment, weeks: 3, session: 's90' },
        legacyExercises: EXERCISES, seed, makeId: ids('m342-' + seed)
    });
    const p = clone(built.program);
    assert.equal(built.nextProgram.audit.result, 'pass');
    assert.equal(p.days.length, profile.days);
    const candidate = p.days.flatMap(day => day.exercises.map((id, slot) => ({ day, id, slot })))
        .find(({ day, id, slot }) => EX_BY_ID[id] &&
            ['hypertrophy_isolation', 'hypertrophy_compound', 'accessory'].includes(
                computeCell(p, day, id, slot, 1).role));
    if (candidate) {
        const key = candidate.day.id + ':' + candidate.slot;
        p.overrides[key] = markUserPrescriptionOverride(p.overrides[key] ?? {},
            'progressionStyle', profile.style);
        assert.equal(computeCell(p, candidate.day, candidate.id, candidate.slot, 1).progressionStyle,
            profile.style);
        metrics.styles.add(profile.style);
    }
    let history = [], skippedDays = 0;
    const events = ['good', 'normal', 'fatigue', 'incomplete', 'manual', 'bad_day', 'interrupted'];
    for (let week = 1; week <= 3; week++) {
        for (let dayIndex = 0; dayIndex < p.days.length; dayIndex++) {
            const event = events[(week * 5 + dayIndex + seeds.indexOf(seed) * 3) % events.length];
            const context = { seed, profile: profile.name, week, dayIndex,
                event, program: p, history };
            if ((week * 7 + dayIndex + seed) % 13 === 0) {
                metrics.skipped++;
                skippedDays++;
                continue;
            }
            try {
                history = doSession(p, p.days[dayIndex], week, history, event, context);
            } catch (error) { failWithContext(error, context); }
            metrics.sessions++;
            if (event === 'incomplete') metrics.incomplete++;
            if (event === 'manual') metrics.manual++;
            if (event === 'interrupted') metrics.interrupted++;
            if (event === 'bad_day') metrics.badDay++;
            if (event === 'fatigue') metrics.fatigue++;
        }
    }
    assert.ok(history.length > 0);
    metrics.scenarios++;
    metrics.profiles.add(profile.name);
    const normalized = analyzeShellHistoryForNextEngine(p, history, EXERCISES,
        { asOf: Date.UTC(2026, 9, 1) });
    assert.equal(normalized.workoutCount, history.length);
    assert.equal(normalized.performedSetCount, history.reduce((n, e) => n + recorded(e.perf), 0));
    const reversed = analyzeShellHistoryForNextEngine(p, clone(history).reverse(), EXERCISES,
        { asOf: Date.UTC(2026, 9, 1) });
    assert.equal(reversed.workoutCount, normalized.workoutCount,
        'History ordering cannot invent another workout');
    assert.equal(reversed.performedSetCount, normalized.performedSetCount,
        'Reordered persisted history cannot change completed volume');
    const cursor = nextSessionCursor(p, history);
    assert.ok(Number.isInteger(cursor.dayIndex) && Number.isInteger(cursor.weekIndex));
    console.log('M342_PROFILE ' + JSON.stringify({ seed, profile: profile.name, style: profile.style,
        workouts: history.length, skipped: skippedDays, workingSets: normalized.performedSetCount,
        recovery: normalized.recovery.status, readyForNextBlock: normalized.readyForNextBlock }));
}
const result = { status: 'pass', seeds: cases, scenarios: metrics.scenarios,
    profiles: [...metrics.profiles], progressionStyles: [...metrics.styles],
    workouts: metrics.sessions, skipped: metrics.skipped, workingSets: metrics.workingSets,
    incomplete: metrics.incomplete, manual: metrics.manual,
    interrupted: metrics.interrupted, badDays: metrics.badDay,
    fatigue: metrics.fatigue, liveAdjustments: metrics.liveAdjustments,
    recommendations: metrics.recommendations };
assert.equal(metrics.scenarios, cases.length);
assert.ok(metrics.workingSets > 0 && metrics.recommendations > 0);
if (onlySeed === null) {
    assert.equal(metrics.profiles.size, 4);
    assert.equal(metrics.styles.size, 4);
    assert.ok(metrics.liveAdjustments > 0 && metrics.incomplete > 0 && metrics.manual > 0 &&
        metrics.interrupted > 0 && metrics.badDay > 0 && metrics.fatigue > 0 &&
        metrics.skipped > 0, 'Seeded matrix lost an important edge-case dimension');
}
console.log('ENGINE_SEEDED_AUDIT ' + JSON.stringify(result));
