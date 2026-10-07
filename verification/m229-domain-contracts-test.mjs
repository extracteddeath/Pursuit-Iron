import assert from 'node:assert/strict';
import { DOMAIN_RECORD_KINDS, migrateDomainRecord, readDomainRecord } from '../modules/next-engine/domain-contracts.js';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { generateProgram } from '../modules/next-engine/generate.js';
const request = { athlete: { experience: 'intermediate' }, goal: { type: 'hypertrophy' },
    schedule: { days: ['monday', 'thursday'].map(day => ({ day, minMinutes: 0, maxMinutes: 60 })) },
    equipment: { available: ['dumbbell', 'bench', 'bodyweight'], bodyweight: 'allow' }, seed: 229 };
const values = {
    request,
    program: { sessions: [{ day: 'monday', exercises: [{ exerciseId: 'db-curl', sets: 3, prescription: { reps: [8,12], rir: [1,2] } }] }] },
    prescription: { exerciseId: 'db-curl', sets: 3, reps: '8-12', ownership: { reps: 'user' } },
    workout: { id: 'w', programId: 'p', perf: { lift: { sets: [{ w: 25, r: 12, rirReported: false }] } } },
    adaptation: { decisions: [{ exerciseId: 'db-curl', outcome: 'unobserved' }] },
    cycle: { phase: 'hypertrophy_accumulation', workoutsInPhase: 0 }
};
for (const kind of DOMAIN_RECORD_KINDS) {
    const value = values[kind];
    const before = structuredClone(value);
    const legacy = migrateDomainRecord(kind, value);
    const v0 = migrateDomainRecord(kind, { schemaVersion: 0, kind, payload: value });
    assert.deepEqual(legacy, v0);
    assert.deepEqual(readDomainRecord(kind, JSON.parse(JSON.stringify(legacy))), value);
    assert.deepEqual(migrateDomainRecord(kind, legacy), legacy, 'migration is idempotent');
    assert.deepEqual(value, before);
    assert.notEqual(legacy.value, value);
    for (const schemaVersion of [-1, 2, '1', NaN, null])
        assert.throws(() => readDomainRecord(kind, { schemaVersion, kind, value }), /schema version/);
    assert.throws(() => readDomainRecord(kind, { schemaVersion: 1, kind: 'wrong', value }), /kind/);
    assert.throws(() => readDomainRecord(kind, []));
    assert.throws(() => readDomainRecord(kind, { schemaVersion: 1, kind }));
}
for (const sets of [0, -1, '3', [3,3], true, NaN, 21])
    assert.throws(() => migrateDomainRecord('prescription', { exerciseId: 'x', sets }));
assert.throws(() => migrateDomainRecord('program', { sessions: [{ day: 'monday', exercises: [{ exerciseId: 'x', sets: 3, prescription: { reps: [12,8], rir: [1,2] } }] }] }));
assert.deepEqual(normalizeRequest(request), normalizeRequest(migrateDomainRecord('request', request)));
const now = Date.now; Date.now = () => 1791388800000;
try { assert.deepEqual(generateProgram(request), generateProgram(migrateDomainRecord('request', request))); }
finally { Date.now = now; }
console.log('PASS M229: six domain migrations, lossless/idempotent roundtrips, strict versions, ownership/effort preservation, legacy generation parity.');
