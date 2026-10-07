import assert from 'node:assert/strict';
import fs from 'node:fs';
import { buildCandidatePool, selectBestCandidate } from '../modules/next-engine/candidate-optimization.js';

// M235 stress-checks the M234 boundary with a deliberately oversized custom catalog.
// Static filtering must keep original rich objects intact, while dynamic scoring remains bounded.
const catalog = Array.from({ length: 600 }, (_, index) => ({
    id: `custom-${String(index).padStart(3, '0')}`,
    eligible: index % 2 === 0,
    quality: index,
    prescription: { reps: [6, 10], rir: [1, 2], restSeconds: 120 },
    advancedTechnique: index % 10 === 0 ? { kind: 'drop_set', trigger: 'last_set' } : null
}));
const pool = buildCandidatePool(catalog, candidate => candidate.eligible);
assert.equal(pool.length, 300, 'static hard constraints should reduce the oversized catalog first');
assert.strictEqual(pool[0], catalog[0], 'static pools must preserve original rich candidate objects');

let scoreCalls = 0;
const selected = selectBestCandidate(pool, {
    maxEvaluations: 64,
    preCompare: (a, b) => b.quality - a.quality,
    score: candidate => {
        scoreCalls++;
        return candidate.quality;
    }
});
assert.equal(scoreCalls, 64, 'dynamic ranking work must stay within its explicit bound');
assert.equal(selected.evaluatedCount, 64, 'verification diagnostics should expose the bounded work count');
assert.equal(selected.truncated, true, 'oversized candidate sets should report truncation');
assert.equal(selected.candidate.id, 'custom-598', 'cheap pre-ranking must retain the best eligible candidate before bounded scoring');
assert.deepEqual(selected.candidate.prescription, catalog[598].prescription, 'bounded selection must retain full prescription metadata');
assert.deepEqual(selected.candidate.advancedTechnique, catalog[598].advancedTechnique, 'bounded selection must retain advanced-technique metadata');

const contracts = fs.readFileSync(new URL('../scripts/verify-engine-contracts.mjs', import.meta.url), 'utf8');
assert.match(contracts, /m234-candidate-optimization/, 'M234 must stay in the permanent adaptation gate');
assert.match(contracts, /m235-engine-verification/, 'M235 must verify itself through the permanent adaptation gate');

const realizer = fs.readFileSync(new URL('../modules/next-engine/realizer.js', import.meta.url), 'utf8');
assert.match(realizer, /buildCandidatePool, selectBestCandidate/, 'realizer must use the shared candidate optimization contract');
assert.match(realizer, /candidatePoolFor\(session\)/, 'late repair paths must be able to reuse session-scoped static eligibility');

console.log('PASS M235 engine verification');
