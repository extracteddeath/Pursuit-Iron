import assert from 'node:assert/strict';
import fs from 'node:fs';
import { buildCandidatePool, selectBestCandidate } from '../modules/next-engine/candidate-optimization.js';

const rich = {
    id: 'rich',
    name: 'Rich candidate',
    prescription: { reps: [8, 12], rir: [1, 2], restSeconds: 90 },
    advancedTechnique: { kind: 'myo_reps', trigger: 'last_set' },
    metadataConfidence: 'high'
};
const rejected = { id: 'rejected', name: 'Rejected candidate' };
const pool = buildCandidatePool([rich, rejected], candidate => candidate.id !== 'rejected');
assert.equal(pool.length, 1, 'static pool should remove rejected candidates');
assert.strictEqual(pool[0], rich, 'candidate pools must preserve the full original object');
assert.deepEqual(pool[0].prescription, rich.prescription, 'candidate pool must not omit prescription fields');
assert.deepEqual(pool[0].advancedTechnique, rich.advancedTechnique, 'candidate pool must not omit technique fields');

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
assert.equal(constrained.candidate.id, 'c', 'best accepted candidate should win');
assert.equal(scored, 2, 'hard constraints must run before ranking');

const bounded = selectBestCandidate([
    { id: 'a', value: 1 },
    { id: 'b', value: 2 },
    { id: 'c', value: 99 }
], {
    score: candidate => candidate.value,
    maxEvaluations: 2
});
assert.equal(bounded.evaluatedCount, 2, 'candidate ranking must respect the evaluation bound');
assert.equal(bounded.truncated, true, 'bounded selection should expose truncation diagnostics');
assert.equal(bounded.candidate.id, 'b', 'bounded ranking must stay deterministic inside the evaluated window');

const stable = selectBestCandidate([
    { id: 'first', value: 10 },
    { id: 'second', value: 10 }
], { score: candidate => candidate.value });
assert.equal(stable.candidate.id, 'first', 'score ties must retain catalog order');


const explicitTie = selectBestCandidate([
    { id: 'z', value: 10 },
    { id: 'a', value: 10 }
], {
    score: candidate => candidate.value,
    tieBreak: (a, b) => a.id.localeCompare(b.id)
});
assert.equal(explicitTie.candidate.id, 'a', 'explicit tie-breaks must preserve legacy ordering contracts');

const compared = selectBestCandidate([
    { id: 'x', specificity: .7, loadability: 4 },
    { id: 'y', specificity: .8, loadability: 2 }
], {
    compare: (a, b) => b.specificity - a.specificity || b.loadability - a.loadability
});
assert.equal(compared.candidate.id, 'y', 'comparator ranking must preserve existing sort semantics');

const realizer = fs.readFileSync(new URL('../modules/next-engine/realizer.js', import.meta.url), 'utf8');
assert.match(realizer, /createSessionCandidatePoolCache/, 'realizer must reuse static candidate pools by session');
assert.match(realizer, /selectBestCandidate\(candidatePoolFor\(session\)/, 'public-floor repair must use the shared candidate selector');
assert.match(realizer, /strengthCandidate\([^\n]*candidatePoolFor\(/, 'strength selection must use cached static eligibility');
assert.match(realizer, /muscleCandidate\([^\n]*candidatePoolFor\(/, 'hypertrophy selection must use cached static eligibility');

console.log('PASS M234 candidate optimization');
