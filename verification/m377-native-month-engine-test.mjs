// M377: engine fixes found by the native app's month test (a lifter who follows the plan for four weeks and beyond).
// Each block below failed on the engine before this build.
import assert from 'node:assert/strict';
import * as E from '../modules/engine-api.js';
import { represcribeForWeek } from '../modules/next-engine/workout-history-adapter.js';
import { withGroupRounds } from '../modules/workout-context.js';

// 1. A repeating block advances through its weeks on every pass. The cursor counted the first pass's sessions, so
//    the second pass never left week 1.
{
    const program = E.generateNextProgramForShell({ config: { name: 'M', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate',
        split: 'upper_lower', days: 4, weeks: 4, session: 's60', equipment: ['barbell', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] },
        banned: [], legacyExercises: E.EXERCISES, makeId: (() => { let n = 0; return () => 'id' + (++n); })() }).program;
    const weeks = [], history = [];
    let t = Date.UTC(2026, 9, 5, 17);
    for (let n = 0; n < 36; n++) {
        const c = E.nextSessionCursor(program, history);
        weeks.push(c.weekIndex);
        history.unshift({ id: 'h' + n, programId: program.id, dayId: program.days[c.dayIndex].id, weekIndex: c.weekIndex, date: t, unit: 'kg', perf: {} });
        t += 2 * 864e5;
    }
    const expected = Array.from({ length: 36 }, (_, i) => (Math.floor(i / 4) % 4) + 1);
    assert.deepEqual(weeks, expected, 'two passes through a 4-week block, then week 1 again');
    // 2. The Plan's done ticks are this pass only: after the first pass, week 1 starts with nothing done.
    const afterPass = history.slice(-16);
    const ids = E.earlierPassIds(program, [...history.slice(-17)]);
    assert.equal(ids.size, 16, 'the 17th session starts a new pass; the first 16 are an earlier pass');
    assert.equal(E.earlierPassIds(program, afterPass).size, 0, 'a block still on its first pass has no earlier sessions');
}

// 3. Dumbbell loads are per hand and step like the engine's default rack (2.5 kg / 5 lb), upper or lower body.
{
    const db = E.EXERCISES.find(x => x.id === 'db-rdl'), dbUp = E.EXERCISES.find(x => x.id === 'db-row');
    assert.equal(E.loadStep(db, 'kg'), 2.5); assert.equal(E.loadStep(db, 'lb'), 5);
    assert.equal(E.loadStep(dbUp, 'kg'), 2.5); assert.equal(E.loadStep(dbUp, 'lb'), 5);
}

// 4. A hold of a load the lifter logged stays that load; a re-set rounds to the nearer loadable weight.
{
    const db = E.EXERCISES.find(x => x.id === 'db-rdl');
    assert.equal(E.loadableForSuggestion(db, 14, 'kg', [14], false), 14, 'a logged 14 kg holds at 14');
    assert.equal(E.loadableForSuggestion(db, 13.6, 'kg', [14], false), 12.5, 'a decrease still rounds down');
    assert.equal(E.loadableForSuggestion(db, 12.4, 'kg', [], true), 12.5, 'a re-set rounds to the nearer weight');
    assert.equal(E.loadableForSuggestion(db, 11.2, 'kg', [], true), 10, 'and down when that is nearer');
    const last = { weight: 35, reps: 11, sets: [{ w: 35, r: 11, rir: 2, done: true }] };
    const r = represcribeForWeek('standing_cable_press', { reps: '10-15', rir: '1-2' }, { reps: '10-15', rir: '2-3' }, last,
        { equipment: { loading: E.loadingInventoryInUnit(null, 'kg'), available: ['cable'] } }, 'kg', 'kg');
    if (r) assert.ok(r.weight >= 32.5 && r.weight <= 35, `the re-set lands on the nearer cable notch (${r.weight})`);
}

// 5. Technique cues never contradict: the lift's own cue wins over a pattern cue on the same topic.
{
    const bench = E.EX_BY_ID['bb-bench'];
    const cues = E.cuesFor(bench).join(' | ');
    assert.ok(/75°/.test(cues) && !/45°/.test(cues), 'bench says one elbow angle: ' + cues);
}

// 6. A superset set to N rounds trains N working sets in the workout.
{
    const sets = [{ warm: true }, { weight: 20 }, { weight: 20, done: true }, { sub: true }, { weight: 20 }];
    assert.equal(withGroupRounds(sets, 2).filter(s => !s.warm && !s.sub).length, 2);
    assert.equal(withGroupRounds(sets, 5).filter(s => !s.warm && !s.sub).length, 5);
    assert.equal(withGroupRounds(sets, 5).at(-1).done, false, 'an added round is not logged');
    assert.equal(withGroupRounds(sets, 0), sets);
}

// 7. One shared station doesn't carry a session: a commercial-gym hypertrophy Upper was six cable lifts (436 of 1,934
//    swept days had 4+ lifts and 60%+ of the day at one station; 235 had 4+ cable lifts, now 10, all long or cable-gym days).
{
    const eq = ['barbell', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'];
    for (const goal of ['hypertrophy', 'both']) for (const [split, days] of [['upper_lower', 4], ['ppl', 3], ['full_body', 3]]) for (const session of ['s40', 's60', 's90']) {
        const p = E.generateNextProgramForShell({ config: { name: 'S', unit: 'kg', goal, experience: 'beginner', split, days, session, equipment: eq },
            banned: [], legacyExercises: E.EXERCISES, makeId: (() => { let n = 0; return () => 's' + (++n); })() }).program;
        for (const d of p.days) {
            const at = st => d.exercises.filter(id => (E.EX_BY_ID[id]?.equip || []).includes(st)).length;
            const label = `${goal} ${split} ${session} ${d.label}: ${d.exercises.join(', ')}`;
            for (const st of ['cable', 'smith']) {
                assert.ok(at(st) < d.exercises.length || d.exercises.length < 4, `${label} is all ${st}`); // a short 3-lift day may stay at one station
                assert.ok(at(st) <= (session === 's90' ? 4 : 3), `${label} has ${at(st)} ${st} lifts`);
            }
        }
    }
}
console.log('PASS m377 native month engine fixes: block passes, done ticks, dumbbell steps, load snapping, cues, rounds, station spread');
