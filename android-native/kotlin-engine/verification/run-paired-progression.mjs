import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';
import { root, fixturePath, artifactPath, evidencePath, sourcePath, runnerPaths, caseIds,
  sha256, scenarioDigest, git, parseFixture, grade, verifyBundle,
  verifyRecordedProgressionEvidence } from './paired-progression.mjs';

const args = process.argv.slice(2);
function value(flag, fallback) { const i = args.indexOf(flag); return i < 0 ? fallback : args[i + 1]; }
const legacyRoot = resolve(value('--legacy-root', 'comparison-legacy'));
const output = resolve(root, value('--output', 'android-native/kotlin-engine/build/reports/paired-progression.json'));
const fixtureFile = resolve(root, fixturePath);
const fixture = parseFixture(readFileSync(fixtureFile));
assert.equal(git(legacyRoot, 'rev-parse', 'HEAD'), fixture.legacyCommit, 'legacy checkout is not the pinned web revision');
assert.equal(git(legacyRoot, 'status', '--porcelain'), '', 'legacy checkout must be clean');
assert.equal(git(root, 'status', '--porcelain', '--untracked-files=all', '--', sourcePath), '',
  'native production policies must be a frozen committed revision');
const candidateCommit = git(root, 'rev-parse', 'HEAD');
const nativeOutput = resolve(dirname(output), 'paired-progression-native.json');
mkdirSync(dirname(output), { recursive: true });
const gradle = value('--gradle', 'gradle');
// Host-specific proxy/trust options can be supplied locally but are never
// persisted into public artifacts or substituted for a real engine result.
const extra = JSON.parse(process.env.PURSUIT_COMPARISON_GRADLE_ARGS || '[]');
assert.ok(Array.isArray(extra) && extra.every(v => typeof v === 'string'));
execFileSync(gradle, ['-p', resolve(root, 'android-native'), ':kotlin-engine:runPairedProgressionProbe',
  `-PcomparisonInput=${fixtureFile}`, `-PcomparisonOutput=${nativeOutput}`,
  '--no-daemon', '--console=plain', '--max-workers=2', ...extra], { cwd: root, stdio: 'inherit' });
const native = JSON.parse(readFileSync(nativeOutput, 'utf8'));
assert.equal(native.fixtureDigest, fixture.fixtureDigest);
assert.deepEqual(native.results.map(r => r.caseId), caseIds);

const { evaluateWorkoutProgression } = await import(pathToFileURL(resolve(legacyRoot, 'modules/next-engine/performance.js')));
const inventory = { unit: fixture.unit,
  barbell: { barWeight: fixture.barWeight, platePairs: fixture.platePairs },
  dumbbells: { availablePerHand: [] }, machine: { availableLoads: [] },
  cable: { availableLoads: [] }, smith: { availableLoads: [] }, exerciseOverrides: {} };
const legacyResults = fixture.cases.map(item => {
  const session = { id: `${item.id}-latest`, exercises: [{ exerciseId: fixture.exerciseId,
    name: 'Barbell Bench Press', role: 'primary', sets: fixture.count, progressionStyle: 'double',
    prescription: { reps: [fixture.floor, fixture.ceiling], rir: [fixture.issuedRir, fixture.issuedRir] } }] };
  const mapRows = rows => rows.map(s => ({ exerciseId: fixture.exerciseId,
    setIndex: s.ordinal, load: s.load, reps: s.reps, rir: s.rir }));
  const performed = mapRows(item.latest);
  const context = { loadingInventory: structuredClone(inventory), equipmentAvailable: ['barbell', 'bench', 'rack'],
    history: [{ id: `${item.id}-previous`, performedSets: mapRows(item.previous) }] };
  const before = JSON.stringify({ session, performed, context });
  const raw = evaluateWorkoutProgression(session, performed, context);
  assert.equal(raw.length, 1);
  const result = raw[0];
  return { caseId: item.id, inputDigest: scenarioDigest(fixture.fixtureDigest, item.id),
    previous: item.previous, latest: item.latest,
    candidateLoad: result.suggestedLoad, setTargets: result.setTargets,
    mutatedInput: before !== JSON.stringify({ session, performed, context }), raw };
});
const engines = {
  'legacy-javascript': { commit: fixture.legacyCommit,
    sourceTree: git(legacyRoot, 'rev-parse', 'HEAD:modules'),
    entryPoint: 'modules/next-engine/performance.js:evaluateWorkoutProgression', results: legacyResults },
  'independent-kotlin': { commit: candidateCommit,
    sourceTree: git(root, 'rev-parse', `HEAD:${sourcePath}`),
    entryPoint: 'ConservativeProgressionReview.assess + OverloadRecoveryLoadReviewDomain.assess',
    results: native.results }
};
const measurements = [];
for (const [engine, run] of Object.entries(engines)) for (const item of fixture.cases) {
  const r = run.results.find(r => r.caseId === item.id);
  r.assessment = grade(fixture, item, r);
  measurements.push({ caseId: item.id, engine, proof: 'headless', outcome: r.assessment.outcome,
    commit: run.commit, inputDigest: r.inputDigest, reproducible: true });
}
const bundle = verifyBundle({ schemaVersion: 1, kind: 'paired-headless-progression-review',
  scope: 'Next-exposure load-review policies only; no issued workout, persistence, device or full-product certification.',
  fixture: fixturePath, fixtureDigest: fixture.fixtureDigest,
  reproduction: 'Check out the recorded candidate and pinned legacy commits; run node android-native/kotlin-engine/verification/run-paired-progression.mjs --legacy-root <clean-legacy-checkout>. Requires JDK 17, Android API 37 and Gradle 9.3.1.',
  runnerFiles: Object.fromEntries(runnerPaths.map(file => [file, sha256(readFileSync(resolve(root, file)))])),
  engines, measurements }, fixture);
writeFileSync(output, JSON.stringify(bundle, null, 2) + '\n');

if (args.includes('--record')) {
  const bytes = JSON.stringify(bundle, null, 2) + '\n';
  writeFileSync(resolve(root, artifactPath), bytes);
  const evidence = JSON.parse(readFileSync(resolve(root, evidencePath), 'utf8'));
  evidence.measurements = evidence.measurements.filter(r => r.proof !== 'headless' || !caseIds.includes(r.caseId));
  evidence.measurements.push(...measurements.map(r => ({ ...r, artifact: artifactPath, artifactDigest: sha256(bytes) })));
  writeFileSync(resolve(root, evidencePath), JSON.stringify(evidence, null, 2) + '\n');
  verifyRecordedProgressionEvidence(evidence);
} else if (args.includes('--check-recorded')) {
  const evidence = JSON.parse(readFileSync(resolve(root, evidencePath), 'utf8'));
  verifyRecordedProgressionEvidence(evidence);
  const recorded = JSON.parse(readFileSync(resolve(root, artifactPath), 'utf8'));
  for (const engine of Object.keys(engines)) {
    assert.equal(engines[engine].sourceTree, recorded.engines[engine].sourceTree);
    assert.deepEqual(engines[engine].results, recorded.engines[engine].results,
      'fresh executed outputs differ from the recorded comparison');
  }
}
for (const item of fixture.cases) console.log(JSON.stringify({ caseId: item.id,
  legacy: engines['legacy-javascript'].results.find(r => r.caseId === item.id).assessment,
  kotlin: engines['independent-kotlin'].results.find(r => r.caseId === item.id).assessment }));
if (measurements.some(m => m.engine === 'independent-kotlin' && m.outcome !== 'pass')) process.exitCode = 1;
