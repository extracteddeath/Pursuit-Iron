import { auditProgram } from '../modules/next-engine/arbiter.js';
import { evaluateObjectiveCoachGuardrails } from '../modules/next-engine/coach-regression.js';

/**
 * M180 Coach Quality Oracle 2.0
 *
 * This layer intentionally does NOT collapse program quality into one average score. A program with
 * excellent exercise selection cannot compensate for an equipment violation, an impossible session,
 * a major dose defect, or another release-blocking finding. Each quality domain keeps its own gate.
 */
export const COACH_QUALITY_DOMAINS = Object.freeze([
  'safety_and_restrictions',
  'dose_and_priority',
  'session_execution',
  'exercise_selection',
  'fatigue_and_recovery',
  'split_and_phase_identity',
  'prescription_quality'
]);

const HARD_GUARDRAIL_CODES = new Set([
  'SESSION_TIME_CAP_EXCEEDED',
  'SUPERSET_NONADJACENT',
  'REDUNDANT_COMPOUND_OVERLAP',
  'STRENGTH_AFTER_HYPERTROPHY',
  'LOWER_DAY_IDENTITY_DILUTED',
  'RECOVERY_TOPOLOGY_AVOIDABLE',
  'DIRECT_DOSE_CONCENTRATION'
]);

const REVIEW_GUARDRAIL_CODES = new Set([
  'ONE_SET_FRAGMENTATION',
  'LOCAL_PREFATIGUE_BEFORE_COMPOUND',
  'REDUNDANT_SEMANTIC_OVERLAP',
  'REDUNDANT_ACCESSORY_FAMILY',
  'FULL_BODY_COMPOUND_CLUSTER'
]);

const HARD_HUMAN_ISSUE_CATEGORIES = new Set([
  'equipment_or_restriction',
  'underdosed',
  'overdosed',
  'fatigue_risk',
  'prescription_issue'
]);

function domainForCode(code = '') {
  if (/EQUIPMENT|RESTRICTION|BODYWEIGHT|SUPERSET/.test(code)) return 'safety_and_restrictions';
  if (/DOSE|MEV|MRV|UNDER|OVER|PRIORITY|VOLUME/.test(code)) return 'dose_and_priority';
  if (/TIME|FRAGMENT|SEQUENCE|PREFATIGUE/.test(code)) return 'session_execution';
  if (/REDUNDANT|OVERLAP|EXERCISE/.test(code)) return 'exercise_selection';
  if (/RECOVERY|FATIGUE|COLLISION/.test(code)) return 'fatigue_and_recovery';
  if (/SPLIT|IDENTITY|PHASE|FULL_BODY|LOWER_DAY/.test(code)) return 'split_and_phase_identity';
  return 'prescription_quality';
}

function domainForIssueCategory(category = '') {
  if (category === 'equipment_or_restriction') return 'safety_and_restrictions';
  if (category === 'underdosed' || category === 'overdosed') return 'dose_and_priority';
  if (category === 'poor_sequence' || category === 'misplaced_exercise') return 'session_execution';
  if (category === 'redundant_work') return 'exercise_selection';
  if (category === 'fatigue_risk') return 'fatigue_and_recovery';
  if (category === 'split_identity') return 'split_and_phase_identity';
  return 'prescription_quality';
}

function normalizeAuditFinding(finding) {
  return {
    source: 'engine_audit',
    code: finding.code ?? 'AUDIT_FINDING',
    severity: finding.severity ?? 'warning',
    domain: domainForCode(finding.code),
    detail: finding.detail ?? finding.message ?? '',
    original: finding
  };
}

function normalizeGuardrailFinding(finding) {
  const severity = HARD_GUARDRAIL_CODES.has(finding.code)
    ? 'major'
    : REVIEW_GUARDRAIL_CODES.has(finding.code)
      ? 'warning'
      : 'warning';
  return {
    source: 'coach_guardrail',
    code: finding.code,
    severity,
    domain: domainForCode(finding.code),
    detail: finding.detail ?? '',
    original: finding
  };
}

function humanEvidence(aggregate, minimumReviewers = 2, dimensionFloor = 3) {
  if (!aggregate) return [];
  const findings = [];
  if ((aggregate.reviewerCount ?? 0) < minimumReviewers) {
    findings.push({
      source: 'blind_coach_review',
      code: 'HUMAN_REVIEW_SAMPLE_TOO_SMALL',
      severity: 'warning',
      domain: 'prescription_quality',
      detail: `${aggregate.reviewerCount ?? 0} reviewers; ${minimumReviewers} required for the human quality gate.`
    });
    return findings;
  }

  const candidate = aggregate.byOrigin?.candidate;
  if (!candidate) {
    findings.push({
      source: 'blind_coach_review',
      code: 'HUMAN_CANDIDATE_ASSESSMENT_MISSING',
      severity: 'warning',
      domain: 'prescription_quality',
      detail: 'Blind-review aggregate does not contain a candidate assessment.'
    });
    return findings;
  }

  const rejectVotes = candidate.gradeCounts?.reject ?? 0;
  if (rejectVotes >= Math.ceil(aggregate.reviewerCount / 2)) {
    findings.push({
      source: 'blind_coach_review',
      code: 'HUMAN_REJECT_MAJORITY',
      severity: 'major',
      domain: 'prescription_quality',
      detail: `${rejectVotes}/${aggregate.reviewerCount} reviewers rejected the candidate as written.`
    });
  }

  for (const [dimension, value] of Object.entries(candidate.dimensionMeans ?? {})) {
    if (value >= dimensionFloor) continue;
    findings.push({
      source: 'blind_coach_review',
      code: `HUMAN_DIMENSION_FLOOR_${dimension.toUpperCase()}`,
      severity: 'warning',
      domain: dimension === 'fatigue_management'
        ? 'fatigue_and_recovery'
        : dimension === 'exercise_selection' || dimension === 'exercise_economy'
          ? 'exercise_selection'
          : dimension === 'split_identity'
            ? 'split_and_phase_identity'
            : dimension === 'priority_fulfillment'
              ? 'dose_and_priority'
              : 'prescription_quality',
      detail: `${dimension} averaged ${value.toFixed(2)}, below the ${dimensionFloor.toFixed(2)} quality floor.`
    });
  }

  for (const issue of aggregate.agreement?.consensusIssues ?? []) {
    if (issue.origin !== 'candidate') continue;
    const major = issue.severity === 'major' && HARD_HUMAN_ISSUE_CATEGORIES.has(issue.category);
    findings.push({
      source: 'blind_coach_review',
      code: `HUMAN_CONSENSUS_${String(issue.category).toUpperCase()}`,
      severity: major ? 'major' : 'warning',
      domain: domainForIssueCategory(issue.category),
      detail: `${issue.reviewerCount} reviewers flagged ${issue.category} at ${issue.target}.`
    });
  }
  return findings;
}

function dedupe(findings) {
  const seen = new Set();
  const out = [];
  for (const finding of findings) {
    const key = `${finding.source}|${finding.code}|${finding.domain}|${finding.detail}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(finding);
  }
  return out;
}

export function evaluateCoachQualityEvidence({ audit, guardrails = [], humanAggregate, minimumReviewers = 2, dimensionFloor = 3 }) {
  const findings = dedupe([
    ...(audit?.findings ?? []).map(normalizeAuditFinding),
    ...guardrails.map(normalizeGuardrailFinding),
    ...humanEvidence(humanAggregate, minimumReviewers, dimensionFloor)
  ]);

  const domains = Object.fromEntries(COACH_QUALITY_DOMAINS.map(domain => [domain, {
    result: 'pass',
    major: 0,
    warnings: 0,
    findings: []
  }]));

  for (const finding of findings) {
    const bucket = domains[finding.domain] ?? domains.prescription_quality;
    bucket.findings.push(finding);
    if (finding.severity === 'critical' || finding.severity === 'major') {
      bucket.major++;
      bucket.result = 'reject';
    } else {
      bucket.warnings++;
      if (bucket.result === 'pass') bucket.result = 'review';
    }
  }

  const hardFailures = findings.filter(f => f.severity === 'critical' || f.severity === 'major');
  const warnings = findings.filter(f => f.severity !== 'critical' && f.severity !== 'major');
  const result = hardFailures.length ? 'reject' : warnings.length ? 'review' : 'pass';

  return {
    result,
    releaseEligible: result === 'pass',
    hardFailures,
    warnings,
    domains,
    // Deliberately no aggregate quality score. Domain gates are authoritative.
    qualityScore: null
  };
}

export function evaluateProgramCoachQuality(program, request, options = {}) {
  const audit = options.audit ?? auditProgram(program, request);
  const guardrails = options.guardrails ?? evaluateObjectiveCoachGuardrails(program, request);
  return evaluateCoachQualityEvidence({
    audit,
    guardrails,
    humanAggregate: options.humanAggregate,
    minimumReviewers: options.minimumReviewers,
    dimensionFloor: options.dimensionFloor
  });
}
