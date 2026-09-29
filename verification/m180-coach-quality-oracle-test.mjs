import assert from 'node:assert/strict';
import { evaluateCoachQualityEvidence } from '../modules/next-engine/coach-quality-oracle.js';

function cleanHumanAggregate(overrides = {}) {
  return {
    reviewerCount: 3,
    byOrigin: {
      candidate: {
        dimensionMeans: {
          session_coherence: 4.5,
          exercise_selection: 4.5,
          priority_fulfillment: 4.5,
          fatigue_management: 4.5,
          prescription_quality: 4.5,
          exercise_economy: 4.5,
          split_identity: 4.5
        },
        gradeCounts: { pass: 3, minor_changes: 0, reject: 0 }
      }
    },
    agreement: { consensusIssues: [] },
    ...overrides
  };
}

{
  const oracle = evaluateCoachQualityEvidence({
    audit: { findings: [] },
    guardrails: [{ code: 'SESSION_TIME_CAP_EXCEEDED', detail: '95m exceeds 90m cap.' }],
    humanAggregate: cleanHumanAggregate()
  });
  assert.equal(oracle.result, 'reject');
  assert.equal(oracle.releaseEligible, false);
  assert.equal(oracle.qualityScore, null);
  assert.ok(oracle.hardFailures.some(f => f.code === 'SESSION_TIME_CAP_EXCEEDED'));
  console.log('PASS hard execution failure cannot be averaged away by strong human scores');
}

{
  const oracle = evaluateCoachQualityEvidence({
    audit: { findings: [] },
    guardrails: [],
    humanAggregate: cleanHumanAggregate()
  });
  assert.equal(oracle.result, 'pass');
  assert.equal(oracle.releaseEligible, true);
  for (const domain of Object.values(oracle.domains)) assert.equal(domain.result, 'pass');
  console.log('PASS clean domain evidence is release-eligible');
}

{
  const oracle = evaluateCoachQualityEvidence({
    audit: { findings: [] },
    guardrails: [{ code: 'ONE_SET_FRAGMENTATION', detail: '3/5 movements are one-set fragments.' }],
    humanAggregate: cleanHumanAggregate()
  });
  assert.equal(oracle.result, 'review');
  assert.equal(oracle.releaseEligible, false);
  assert.equal(oracle.hardFailures.length, 0);
  assert.ok(oracle.warnings.some(f => f.code === 'ONE_SET_FRAGMENTATION'));
  console.log('PASS soft coach-quality defect requires review without pretending it is a hard failure');
}

{
  const aggregate = cleanHumanAggregate();
  aggregate.byOrigin.candidate.dimensionMeans = {
    session_coherence: 5,
    exercise_selection: 5,
    priority_fulfillment: 5,
    fatigue_management: 2.5,
    prescription_quality: 5,
    exercise_economy: 5,
    split_identity: 5
  };
  const oracle = evaluateCoachQualityEvidence({ audit: { findings: [] }, guardrails: [], humanAggregate: aggregate });
  assert.equal(oracle.result, 'review');
  assert.ok(oracle.warnings.some(f => f.code === 'HUMAN_DIMENSION_FLOOR_FATIGUE_MANAGEMENT'));
  assert.equal(oracle.domains.fatigue_and_recovery.result, 'review');
  console.log('PASS one weak human-review domain cannot be hidden by six excellent dimensions');
}

{
  const aggregate = cleanHumanAggregate();
  aggregate.agreement.consensusIssues = [{
    origin: 'candidate', category: 'underdosed', severity: 'major', target: 'Quads', reviewerCount: 2
  }];
  const oracle = evaluateCoachQualityEvidence({ audit: { findings: [] }, guardrails: [], humanAggregate: aggregate });
  assert.equal(oracle.result, 'reject');
  assert.ok(oracle.hardFailures.some(f => f.code === 'HUMAN_CONSENSUS_UNDERDOSED'));
  assert.equal(oracle.domains.dose_and_priority.result, 'reject');
  console.log('PASS repeated major underdosing consensus is independently release-blocking');
}

{
  const aggregate = cleanHumanAggregate();
  aggregate.reviewerCount = 1;
  const oracle = evaluateCoachQualityEvidence({ audit: { findings: [] }, guardrails: [], humanAggregate: aggregate });
  assert.equal(oracle.result, 'review');
  assert.ok(oracle.warnings.some(f => f.code === 'HUMAN_REVIEW_SAMPLE_TOO_SMALL'));
  console.log('PASS insufficient human evidence remains review-only rather than false certification');
}

console.log('M180 coach quality oracle foundation OK.');
