// Focused regression gate for approved web-engine fixes taken from PR #455.
// Deliberately excludes native-app features, new progression methods and starting-load estimates.
import assert from 'node:assert/strict';
import * as E from '../modules/engine-api.js';
import { represcribeForWeek } from '../modules/next-engine/workout-history-adapter.js';

// Repeating finite blocks must progress through every week on EACH pass, not only the first.
{
    const { program } = E.generateNextProgramForShell({
        config: { name: 'Repeat test', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate',
            split: 'upper_lower', days: 4, weeks: 4, session: 's60',
            equipment: ['barbell', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] },
        banned: [], legacyExercises: E.EXERCISES,
        makeId: (() => { let n = 0; return () => 'repeat-' + (++n); })()
    });
    const history = [];
    const weeks = [];
    for (let n = 0; n < 36; n++) {
        const cursor = E.nextSessionCursor(program, history);
        weeks.push(cursor.weekIndex);
        history.unshift({ id: 'session-' + n, programId: program.id,
            dayId: program.days[cursor.dayIndex].id, weekIndex: cursor.weekIndex,
            date: Date.UTC(2026, 0, 1) + n * 2 * 864e5, unit: 'kg', perf: {} });
    }
    assert.deepEqual(weeks, Array.from({ length: 36 }, (_, i) => Math.floor(i / 4) % 4 + 1));
    // At the first session of pass two, previous-pass sessions cannot count as done.
    assert.equal(E.earlierPassIds(program, history.slice(-17)).size, 16);
    assert.equal(E.earlierPassIds(program, history.slice(-16)).size, 0);
}

// Equipment increments must be consistent across generation and the workout.
{
    const lower = E.EX_BY_ID['db-rdl'], upper = E.EX_BY_ID['db-row'];
    assert.ok(lower && upper, 'known dumbbell exercises');
    for (const db of [lower, upper]) {
        assert.equal(E.loadStep(db, 'kg'), 2.5);
        assert.equal(E.loadStep(db, 'lb'), 5);
    }
    assert.equal(E.loadableForSuggestion(lower, 14, 'kg', [14], false), 14,
        'a successfully logged off-grid load is not silently reduced');
    assert.equal(E.loadableForSuggestion(lower, 13.6, 'kg', [14], false), 12.5,
        'decreases remain conservative');
    assert.equal(E.loadableForSuggestion(lower, 12.4, 'kg', [], true), 12.5,
        'week estimates use the nearest available weight');
    assert.equal(E.loadableForSuggestion(lower, 11.2, 'kg', [], true), 10);
    try {
        E.setExLoadInc({ [lower.id]: { v: 4, unit: 'kg' } });
        assert.equal(E.loadableForSuggestion(lower, 14, 'kg', [14], false), 12,
            'user-configured equipment increments override old logged loads');
    } finally {
        E.setExLoadInc({});
    }
}

// Week changes keep a loadable prescription even when target reps or RIR change.
{
    const last = { weight: 35, reps: 11, sets: [{ w: 35, r: 11, rir: 2, done: true }] };
    const next = represcribeForWeek('standing_cable_press',
        { reps: '10-15', rir: '1-2' }, { reps: '10-15', rir: '2-3' }, last,
        { equipment: { loading: E.loadingInventoryInUnit(null, 'kg'), available: ['cable'] } }, 'kg', 'kg');
    if (next) assert.ok(next.weight >= 32.5 && next.weight <= 35,
        'estimated load should not repeatedly floor to a substantially lower cable notch');
}

// A full commercial gym should not prescribe an entire 4+ lift day on one station.
{
    const equipment = ['barbell', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'];
    for (const goal of ['hypertrophy', 'both'])
        for (const [split, days] of [['upper_lower', 4], ['ppl', 3], ['full_body', 3]])
            for (const session of ['s40', 's60', 's90']) {
                const { program } = E.generateNextProgramForShell({
                    config: { name: 'Station test', unit: 'kg', goal, experience: 'beginner',
                        split, days, session, equipment },
                    banned: [], legacyExercises: E.EXERCISES,
                    makeId: (() => { let n = 0; return () => 'station-' + (++n); })()
                });
                for (const day of program.days) {
                    const exercises = day.exercises.map(id => E.EX_BY_ID[id]);
                    const stationCount = kind => exercises.filter(ex => (ex?.equip || []).includes(kind)).length;
                    for (const station of ['cable', 'smith']) {
                        const count = stationCount(station);
                        assert.ok(count < exercises.length || exercises.length < 4,
                            `${split} ${session}: all ${exercises.length} on ${station}`);
                        assert.ok(count <= (session === 's90' ? 4 : 3),
                            `${split} ${session}: ${count} ${station} exercises`);
                    }
                }
            }
}
console.log('PASS selective web engine: repeating blocks, loadability, week reset, station variety');
