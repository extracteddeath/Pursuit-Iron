import assert from 'node:assert/strict';
import fs from 'node:fs';
import { EXERCISES } from '../modules/training-domain/catalog.js';
import { loggedWorkoutPerformance } from '../modules/training-domain/prescriptions.js';
import { progressionExposureContext } from '../modules/next-engine/history-contract.js';

// One completed exercise swap must not turn the unswapped day's other lifts
// into "non-comparable" progression evidence.
const catalog = EXERCISES.filter(ex => ex?.id && Array.isArray(ex.rep) && ex.rep.length >= 2);
assert.ok(catalog.length >= 3, 'need three catalog exercise identities');
const [planned, unaffected, replacement] = catalog.slice(0, 3);
const day = { id: 'swap-day', label: 'Upper', primaryIndex: 0,
    exercises: [planned.id, unaffected.id] };
const program = { id: 'swap-program', name: 'Swap regression', custom: true,
    config: { unit: 'lb' }, overrides: {}, days: [day] };
const work = (id, slot) => ({ id, slot, sets: [
    { done: true, weight: '75', reps: '8', warm: false, sub: false,
        target: { w: '75', reps: '8', rir: 2 } }
] });
const performed = [work(replacement.id, 0), work(unaffected.id, 1)];
const saved = loggedWorkoutPerformance(program, day, performed, 1, 'lb', {}, []);
assert.equal(saved[replacement.id]?.exerciseSubstituted, true,
    'swapped exercise must explicitly own the non-comparable flag');
assert.equal(saved[unaffected.id]?.exerciseSubstituted, undefined,
    'unmodified slot must not inherit another lift\'s substitution');
assert.equal(progressionExposureContext([saved[replacement.id]], {}).nonComparable, true);
assert.equal(progressionExposureContext([saved[unaffected.id]], {}).nonComparable, false);
assert.equal(saved[replacement.id]?.prescription?.exerciseId, replacement.id,
    'swapped set must never be recorded as the original planned exercise');
assert.equal(saved[unaffected.id]?.prescription?.exerciseId, unaffected.id);

// Old session-global flags would falsely hold the whole workout. The saved
// substitution list is informational; only the touched exercise is gated.
const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
assert.doesNotMatch(app, /substitutionOccurred:\\s*data\\.some\\(/,
    'a single swap must not mark the whole session non-comparable');
assert.match(app, /substitutions:\\s*data\\.flatMap\\(/);
assert.match(app, /current\\.sets\\.some\\(set => set\\.done\\)/,
    'swap must reject a lift with any completed work');
assert.match(app, /onBanCurrent: \\(\\) => \\{ if \\(data\\[exIdx\\]\\?\\.sets\\.some\\(set => set\\.done\\)\\)/,
    'ban-and-replace must also protect logged work before changing the program ban list');
console.log('PASS M327: session swap identity is local, untouched lifts remain comparable, completed sets are protected.');
