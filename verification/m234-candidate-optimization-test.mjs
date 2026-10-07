import assert from 'node:assert/strict';
import fs from 'node:fs';
import { buildCandidatePool, cachedCandidatePool, selectBestCandidate } from '../modules/next-engine/candidate-optimization.js';

const rich = {
    id: 'rich',
    name: 'Rich candidate',
    prescription: { reps: [8, 12], rir: [1, 2], restSeconds: 90 },
    advancedTechnique: { kind: 'myo_reps', trigger: 'last_set' },
    semanticMetadata: { version: 1, loadConvention: 'machine_stack' },
    metadataConfidence: 'high'
};
const rejected = { id: 'rejected', name: 'Rejected candidate' };
const pool = buildCandidatePool([rich, rejected], candidate => candidate.id !== 'rejected');
assert.equal(pool.length, 1);
assert.strictEqual(pool[0], rich, 'candidate pools must preserve canonical exercise identity');
assert.deepEqual(pool[0].prescription, rich.prescription, 'prescription metadata must survive candidate caching');
assert.deepEqual(pool[0].advancedTechnique, rich.advancedTechnique, 'technique metadata must survive candidate caching');
assert.deepEqual(pool[0].semanticMetadata, rich.semanticMetadata, 'semantic metadata must survive candidate caching');

let staticChecks = 0;
const catalog = [rich, rejected];
const first = cachedCandidatePool(catalog, 'monday|machine', candidate => {
    staticChecks++;
    return candidate.id !== 'rejected';
});
const second = cachedCandidatePool(catalog, 'monday|machine', () => {
    throw new Error('cached static eligibility must not rerun');
});
assert.strictEqual(first, second, 'same catalog/key should reuse the invocation-derived static pool');
assert.equal(staticChecks, 2, 'static eligibility should run once per source candidate');
assert.strictEqual(first[0], rich, 'cache must retain full source objects');

let scored = 0;
const constrained = selectBestCandidate([
    { id: 'a', eligible: false, value: 100 },
    { id: 'b', eligible: true, value: 4 },
    { id: 'c', eligible: true, value: 8 }
], {
    accept: candidate => candidate.eligible,
    score: candidate => {
        scored++;
        return candidate.value;
    }
});
assert.equal(constrained.candidate.id, 'c');
assert.equal(scored, 2, 'hard constraints must run before ranking');

let boundedScores = 0;
const oversized = Array.from({ length: 800 }, (_, index) => ({
    id: `custom-${String(index).padStart(3, '0')}`,
    quality: index,
    prescription: rich.prescription,
    advancedTechnique: index % 10 === 0 ? rich.advancedTechnique : null
}));
const bounded = selectBestCandidate(oversized, {
    maxEvaluations: 64,
    preCompare: (a, b) => b.quality - a.quality,
    score: candidate => {
        boundedScores++;
        return candidate.quality;
    }
});
assert.equal(boundedScores, 64, 'expensive ranking work must obey the configured ceiling');
assert.equal(bounded.evaluatedCount, 64);
assert.equal(bounded.truncated, true);
assert.equal(bounded.candidate.id, 'custom-799', 'cheap pre-ranking must retain the strongest oversized candidate');
assert.strictEqual(bounded.candidate.prescription, rich.prescription, 'bounded ranking must retain full prescription objects');

const tie = selectBestCandidate([{ id: 'first', value: 10 }, { id: 'second', value: 10 }], {
    score: candidate => candidate.value
});
assert.equal(tie.candidate.id, 'first', 'score ties must preserve catalog order');

const ranking = fs.readFileSync(new URL('../modules/next-engine/realizer-ranking.js', import.meta.url), 'utf8');
assert.match(ranking, /cachedCandidatePool/);
assert.match(ranking, /selectBestCandidate\(staticCandidatePool\(catalog, session, request\)/);
assert.match(ranking, /preCompare: cheapRank/, 'oversized hypertrophy scoring must have a cheap bounded pre-rank');
assert.match(ranking, /preCompare: compare/, 'oversized strength selection must preserve the strength comparator');

console.log('PASS M234: full-object candidate cache, constraint-first ranking and bounded evaluation.');
