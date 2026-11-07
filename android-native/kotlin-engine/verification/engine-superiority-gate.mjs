import assert from 'node:assert/strict';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { verifyRecordedProgressionEvidence, caseIds } from './paired-progression.mjs';

/**
 * Independent comparative release gate. It intentionally does not derive a
 * score from code size, test count, an unverified bug report, or mere Kotlin
 * compilation. The same frozen scenario and input digest must be measured
 * on BOTH engines, with actual reproducible artifacts.
 *
 * --audit: validate inputs and print current evidence gaps; exits 0 for an
 * honestly incomplete project, but never calls it superior.
 * --release: refuse unless every paired test is complete, all Kotlin tests
 * pass, material improvement spans >=3 domains, no regression exists, and
 * blind coach / Android / explicit user acceptance are documented.
 * --self-test: assert that missing/spoofed/inferior evidence cannot pass.
 */
const here = dirname(fileURLToPath(import.meta.url));
const cases = JSON.parse(readFileSync(join(here, 'engine-superiority-cases.json'), 'utf8'));
const evidence = JSON.parse(readFileSync(join(here, 'engine-superiority-evidence.json'), 'utf8'));
const sha40 = /^[a-f0-9]{40}$/;
const sha64 = /^[a-f0-9]{64}$/;
const proofKinds = new Set(['headless', 'device', 'human']);
const domains = new Set(['progression', 'equipment', 'programming', 'custom',
  'coaching', 'history', 'migration', 'android']);

export function reviewComparison(matrix, observations) {
  assert.equal(matrix.schemaVersion, 1);
  assert.equal(observations.schemaVersion, 1);
  assert.ok(Array.isArray(matrix.cases));
  assert.ok(Array.isArray(observations.measurements));
  const identifiers = new Set();
  for (const item of matrix.cases) {
    assert.match(item.id, /^[a-z0-9-]+$/);
    assert.ok(!identifiers.has(item.id), 'duplicate comparison scenario ' + item.id);
    identifiers.add(item.id);
    assert.ok(domains.has(item.domain), 'unknown comparison domain ' + item.domain);
    assert.ok(['critical', 'major'].includes(item.severity));
    assert.ok(item.scenario?.trim() && item.expected?.trim());
    assert.ok(item.requiredProof?.length > 0);
    assert.equal(new Set(item.requiredProof).size, item.requiredProof.length);
    assert.ok(item.requiredProof.every(kind => proofKinds.has(kind)));
  }
  const seen = new Set();
  const byKey = new Map();
  for (const record of observations.measurements) {
    assert.ok(identifiers.has(record.caseId), 'unknown scenario result');
    assert.ok(['legacy-javascript', 'independent-kotlin'].includes(record.engine));
    assert.ok(proofKinds.has(record.proof));
    assert.ok(matrix.cases.find(c => c.id === record.caseId)
      .requiredProof.includes(record.proof), 'unexpected proof channel');
    assert.ok(['pass', 'fail'].includes(record.outcome));
    assert.ok(sha40.test(record.commit), 'unversioned engine result');
    assert.ok(sha64.test(record.inputDigest), 'missing common input fingerprint');
    assert.ok(record.artifact?.trim(), 'missing reproducible evidence artifact');
    assert.equal(record.reproducible, true, 'unreproducible evidence is not proof');
    if (record.proof === 'device')
      assert.ok(record.androidDevice?.trim(), 'device proof requires actual device');
    if (record.proof === 'human') {
      assert.ok(record.blinded === true && record.reviewers >= 2,
        'human gate requires independent blinded assessment');
    }
    const key = [record.caseId, record.proof, record.engine].join(':');
    assert.ok(!seen.has(key), 'duplicate evidence for ' + key);
    seen.add(key);
    byKey.set(key, record);
  }

  let completedPairs = 0;
  let missingPairs = 0;
  const blockers = [];
  const improvements = [];
  const regressions = [];
  for (const item of matrix.cases) {
    for (const proof of item.requiredProof) {
      const legacy = byKey.get([item.id, proof, 'legacy-javascript'].join(':'));
      const kotlin = byKey.get([item.id, proof, 'independent-kotlin'].join(':'));
      if (!legacy || !kotlin) {
        missingPairs++;
        blockers.push(item.id + '/' + proof + ': bilateral measured evidence missing');
        continue;
      }
      if (legacy.inputDigest !== kotlin.inputDigest) {
        blockers.push(item.id + '/' + proof + ': engine inputs differ');
        continue;
      }
      completedPairs++;
      if (kotlin.outcome !== 'pass') {
        blockers.push(item.id + '/' + proof + ': Kotlin did not pass');
        if (legacy.outcome === 'pass') regressions.push(item.id + '/' + proof);
      } else if (legacy.outcome === 'fail') {
        improvements.push({ id: item.id, domain: item.domain, proof });
      }
    }
  }
  const improvedDomains = [...new Set(improvements.map(i => i.domain))].sort();
  if (improvedDomains.length < 3)
    blockers.push('insufficient measured superiority: require improvements in >=3 domains');
  if (!observations.userAcceptance?.approved ||
      !observations.userAcceptance?.artifact?.trim())
    blockers.push('explicit user acceptance evidence absent');
  return {
    verdict: blockers.length ? 'NOT_PROVEN_BETTER' : 'RELEASE_COMPARISON_PASSED',
    caseCount: matrix.cases.length,
    requiredPairs: matrix.cases.reduce((n, c) => n + c.requiredProof.length, 0),
    completedPairs,
    missingPairs,
    measuredImprovements: improvements,
    improvedDomains,
    regressions,
    blockers
  };
}

function selfTest() {
  const blank = reviewComparison(cases, evidence);
  assert.equal(blank.verdict, 'NOT_PROVEN_BETTER');
  assert.ok(blank.missingPairs > 0);
  const fake = JSON.parse(JSON.stringify(evidence));
  const all = [];
  for (const item of cases.cases) for (const proof of item.requiredProof) {
    for (const engine of ['legacy-javascript', 'independent-kotlin']) {
      all.push({
        caseId: item.id, proof, engine, outcome: 'pass',
        commit: 'a'.repeat(40), inputDigest: 'b'.repeat(64),
        artifact: 'test-only/must-not-be-used',
        reproducible: true, ...(proof === 'device' ? { androidDevice: 'synthetic' } : {}),
        ...(proof === 'human' ? { blinded: true, reviewers: 2 } : {})
      });
    }
  }
  fake.measurements = all;
  fake.userAcceptance = { approved: true, artifact: 'synthetic/approval' };
  assert.equal(reviewComparison(cases, fake).verdict, 'NOT_PROVEN_BETTER',
    'passing all tests equally is not demonstrated superiority');
  const first = cases.cases[0];
  fake.measurements.find(x => x.caseId === first.id && x.engine === 'independent-kotlin')
    .inputDigest = 'c'.repeat(64);
  assert.ok(reviewComparison(cases, fake).blockers.some(x => x.includes('inputs differ')));
  const duplicate = structuredClone(fake);
  duplicate.measurements.push({ ...duplicate.measurements[0] });
  assert.throws(() => reviewComparison(cases, duplicate), /duplicate evidence/);
  console.log('Engine comparison validation self-test passed; no superiority inferred.');
}

const args = new Set(process.argv.slice(2));
if (args.has('--self-test')) {
  selfTest();
} else {
  if (!args.has('--audit') && !args.has('--release'))
    throw Error('Choose --audit, --release, or --self-test');
  const auditOnly=args.has('--audit') && !args.has('--release');
  const provenance=verifyRecordedProgressionEvidence(evidence,
    { allowStaleProvenance: auditOnly });
  // Stale source results remain tamper-verified as archival evidence, but
  // cannot count as a CURRENT measured pair in an audit or release score.
  const currentEvidence=provenance.currentNativeTree ? evidence : {
    ...evidence,
    measurements:evidence.measurements.filter(m =>
      !(m.proof==='headless' && caseIds.includes(m.caseId)))
  };
  const verdict=reviewComparison(cases, currentEvidence);
  verdict.staleRecordedHeadlessPairs=provenance.currentNativeTree ? 0 : provenance.recordedPairs;
  if(verdict.staleRecordedHeadlessPairs)
    verdict.blockers.unshift('Recorded Kotlin headless measurements have outdated engine or comparison-runner provenance; remeasure before certification.');
  console.log(JSON.stringify(verdict, null, 2));
  if (args.has('--write-report')) {
    const out = join(here, '..', 'reports', 'engine-superiority-audit.json');
    mkdirSync(dirname(out), { recursive: true });
    writeFileSync(out, JSON.stringify(verdict, null, 2) + '\n');
  }
  if (args.has('--release') && verdict.verdict !== 'RELEASE_COMPARISON_PASSED')
    process.exitCode = 1;
}
