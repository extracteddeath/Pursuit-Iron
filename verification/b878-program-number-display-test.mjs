import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { programLastPerformance, programLastPerformances } from '../modules/program-last-performance.js';

const bench = { id: 'bench', name: 'Barbell Bench Press', equip: ['barbell'] };
const chest = { id: 'machine-chest', name: 'Machine Chest Press', equip: ['machine'] };
const lateral = { id: 'lat-raise', name: 'Dumbbell Lateral Raise', equip: ['dumbbell'] };
const exerciseById = { bench, 'machine-chest': chest, 'lat-raise': lateral };
const kg = {
    id: 'logged-kg', programId: 'p', date: 100, unit: 'kg',
    perf: {
        bench: { weight: 115.5, reps: 3, sets: [
            { w: 55, r: 12, warm: true, done: true },
            { w: 115.5, r: 3, done: true },
            { w: 105, r: 5, done: true }
        ] },
        'machine-chest': { weight: 70.5, reps: 12, sets: [{ w: 70.5, r: 12, done: true }] },
        'lat-raise': { weight: 12.5, reps: 12, sets: [{ w: 12.5, r: 12, done: true }] }
    }
};

const cards = programLastPerformances([kg], {
    bench: { weight: 254.5, reps: 3, sets: [{ w: 254.5, r: 3 }] }
}, 'lb', exerciseById);
assert.deepEqual(cards.bench, { weight: '255', reps: 3, approximate: true, unit: 'lb' },
    'program card uses source-unit workout history, not the half-unit rounded performance cache');
assert.deepEqual(cards['machine-chest'], { weight: '155', reps: 12, approximate: true, unit: 'lb' },
    'machine conversions do not imply a machine has fractional pound increments');
assert.deepEqual(cards['lat-raise'], { weight: '27.5', reps: 12, approximate: true, unit: 'lb' },
    'legitimate half-pound dumbbell references survive display conversion');
const native = { ...kg, id: 'native-lb', date: 200, unit: 'lb',
    perf: { bench: { weight: 254.5, reps: 3, sets: [{ w: 254.5, r: 3, done: true }] } }
};
assert.deepEqual(programLastPerformance([kg, native], null, 'bench', 'lb', bench),
    { weight: '254.5', reps: 3, approximate: false, unit: 'lb' },
    'an actual 254.5 lb log must stay exact, not be silently changed to 255');
assert.equal(programLastPerformance([], { weight: 160.5, reps: 6 }, 'bench', 'lb', bench).weight, '160.5',
    'older summary-only performance keeps the recorded precision');
assert.equal(programLastPerformance([], null, 'bench', 'lb', bench), null);
const edited = { ...native, id: 'native-lb', updatedAt: 300,
    perf: { bench: { weight: 255, reps: 5, sets: [{ w: 255, r: 5, done: true }] } }
};
assert.equal(programLastPerformance([native, edited], null, 'bench', 'lb', bench).reps, 5,
    'edited history revision wins over the stale original');
assert.equal(programLastPerformance([kg], null, 'bench', 'kg', bench).weight, '115.5',
    'originally recorded kilogram loads remain exact');
const app = readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
assert.match(app, /displayedLastSets = useMemo\(\(\) => programLastPerformances\(history, perf, unit, EX_BY_ID\)/);
assert.match(app, /const lt = displayedLastSets\[ex\.id\]/);
assert.match(app, /lt\.approximate \? "≈" : ""/);
console.log('PASS B878 program-view last-load provenance, sane conversions and precision checks');
