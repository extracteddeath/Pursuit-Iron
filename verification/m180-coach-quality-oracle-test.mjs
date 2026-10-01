import assert from 'node:assert/strict';
import { evaluateCoachQualityEvidence } from './coach-quality-oracle.mjs';
import { createStrengthClaims, normalizeRequest, selectStrengthClaimsForCapacity } from '../modules/next-engine/prescription.js';

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

{
  const available = ['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
  const request = normalizeRequest({
    athlete: { experience: 'intermediate' },
    goal: { type: 'mixed', musclePriorities: {}, liftPriorities: { back_squat: 'high', bench_press: 'high', deadlift: 'high' } },
    schedule: { days: ['monday','tuesday','wednesday','thursday'].map(day => ({ day, maxMinutes: 75 })) },
    equipment: {
      available,
      bodyweight: 'allow',
      loading: {
        unit: 'lb',
        barbell: { barWeight: 45, platePairs: [{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}] },
        dumbbells: { availablePerHand: [5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100] },
        machine: { minimum: 5, increment: 5, maximum: 500 },
        cable: { minimum: 5, increment: 5, maximum: 300 },
        smith: { minimum: 5, increment: 5, maximum: 500 },
        exerciseOverrides: {}
      }
    },
    restrictions: { maxBarbellMovementsPerDay: 3, allowSupersets: true },
    preferences: { preferredSplit: 'upper_lower', lockedSplit: 'upper_lower', avoidedExercises: [] },
    customExercises: [],
    seed: 18002
  });
  const selected = selectStrengthClaimsForCapacity(request, createStrengthClaims(request));
  const lower = selected.filter(claim => claim.lift === 'back_squat' || claim.lift === 'deadlift');
  assert.deepEqual(new Set(lower.map(claim => claim.id)), new Set(['back_squat-heavy', 'deadlift-heavy']));
  assert.equal(lower.every(claim => claim.required), true);
  assert.ok(selected.some(claim => claim.id === 'bench_press-volume'), 'upper-region optional volume should remain available when it fits cleanly');
  console.log('PASS mixed Upper/Lower protects required squat/deadlift anchors without optional lower-strength stacking');
}

console.log('M180 coach quality oracle foundation OK.');
