import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, realpathSync } from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

export const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
export const fixturePath = 'android-native/kotlin-engine/verification/paired-progression-input.properties';
export const artifactPath = 'android-native/kotlin-engine/verification/paired-progression-measurement.json';
export const evidencePath = 'android-native/kotlin-engine/verification/engine-superiority-evidence.json';
export const sourcePath = 'android-native/kotlin-engine/src/main';
export const caseIds = ['dp-mixed-overload', 'dp-uniform-failure', 'dp-earned-step',
  'dp-fatigue-decay', 'progression-no-rir'];
export const runnerPaths = [fixturePath,
  'android-native/kotlin-engine/verification/paired-progression.mjs',
  'android-native/kotlin-engine/verification/run-paired-progression.mjs',
  'android-native/kotlin-engine/verification/run-jvm-probe.mjs',
  'android-native/kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/PairedProgressionProbe.kt',
  'android-native/kotlin-engine/build.gradle.kts'];
export const sha256 = value => createHash('sha256').update(value).digest('hex');
export const scenarioDigest = (fixtureDigest, id) => sha256(`${fixtureDigest}\n${id}\n`);
export const git = (cwd, ...args) => execFileSync('git', args, { cwd, encoding: 'utf8' }).trim();

/** Exact shared ASCII Properties subset, also checked by the JVM probe. */
export function parseFixture(bytes) {
  const lines = bytes.toString('utf8').split(/\r?\n/).filter(line => line && !line.startsWith('#'));
  const p = {};
  for (const line of lines) {
    assert.match(line, /^[A-Za-z0-9_.-]+=[A-Za-z0-9_:.,-]+$/);
    const [key, value] = [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)];
    assert.ok(!Object.hasOwn(p, key), `duplicate fixture key ${key}`);
    p[key] = value;
  }
  const used = new Set();
  function field(key) { used.add(key); assert.ok(Object.hasOwn(p, key), `missing ${key}`); return p[key]; }
  function number(value) { assert.match(value, /^\d+(?:\.\d+)?$/); return Number(value); }
  const integer = value => { const n = number(value); assert.ok(Number.isSafeInteger(n)); return n; };
  assert.equal(field('schemaVersion'), '1');
  const effortPolicy=field('effortPolicy');
  assert.equal(effortPolicy,'assume_original_goal_when_unreported');
  assert.match(field('legacyCommit'), /^[a-f0-9]{40}$/);
  assert.deepEqual(field('caseIds').split(','), caseIds, 'only implemented, fixed scenarios can be recorded');
  assert.equal(field('exerciseId'), 'barbell_bench');
  assert.equal(field('unit'), 'lb');
  const count = integer(field('workingSets'));
  assert.equal(count, 4);
  const floor = integer(field('repFloor')), ceiling = integer(field('repCeiling'));
  assert.deepEqual([floor, ceiling], [5, 8]);
  const issuedRir = integer(field('issuedRir'));
  assert.equal(issuedRir, 2);
  const barWeight = number(field('barWeight'));
  const platePairs = field('platePairs').split(',').map(pair => {
    const [weight, pairs] = pair.split(':').map(number);
    assert.ok(weight > 0 && Number.isSafeInteger(pairs) && pairs > 0 && pairs <= 10);
    return { weight, pairs };
  });
  assert.ok(barWeight > 0 && platePairs.length <= 8);
  assert.equal(new Set(platePairs.map(p => p.weight)).size, platePairs.length);
  const maximumIncrease = number(field('maximumIncrease'));
  assert.ok(maximumIncrease > 0);
  const cases = caseIds.map(id => {
    function visit(name) {
      function values(key, parse) {
        const v = field(`${id}.${name}.${key}`).split(',').map(parse);
        assert.equal(v.length, count);
        return v;
      }
      const loads = values('loads', number), reps = values('reps', integer);
      const rir = values('rir', v => v === 'null' ? null : integer(v));
      const issued = values('issuedLoads', number);
      return loads.map((load, ordinal) => {
        assert.ok(load > 0 && reps[ordinal] > 0 && reps[ordinal] <= 100);
        assert.ok(rir[ordinal] === null || rir[ordinal] <= 10);
        return { ordinal, load, reps: reps[ordinal], rir: rir[ordinal],
          issuedLoad: issued[ordinal], issuedRir };
      });
    }
    return { id, previous: visit('previous'), latest: visit('latest') };
  });
  assert.equal(used.size, Object.keys(p).length, 'unused/unknown input would make the comparison ambiguous');
  return { legacyCommit: p.legacyCommit, exerciseId: p.exerciseId, unit: p.unit,
    count, floor, ceiling, issuedRir, maximumIncrease, barWeight, platePairs,
    effortPolicy, fixtureDigest: sha256(bytes), cases };
}

/** Independent, finite inventory enumeration; neither engine supplies the oracle. */
export function physicalLoads(fixture) {
  let totals = new Set([fixture.barWeight]);
  for (const plate of fixture.platePairs) {
    const next = new Set();
    for (const base of totals) for (let pairs = 0; pairs <= plate.pairs; pairs++)
      next.add(base + 2 * pairs * plate.weight);
    totals = next;
    assert.ok(totals.size <= 100000, 'fixture inventory exceeds bounded oracle');
  }
  return [...totals].sort((a, b) => a - b);
}

/** Grade load reviews only. Refusing every request cannot pass earned/recovery cases. */
export function grade(fixture, item, result) {
  const failures = [];
  const stock = physicalLoads(fixture);
  const load = result.candidateLoad;
  const max = Math.max(...item.latest.map(s => s.load));
  if (result.mutatedInput !== false) failures.push('input observations were changed or preservation was not measured');
  if (load !== null && (!Number.isFinite(load) || !stock.includes(load))) failures.push('candidate is not physically buildable');
  if (Array.isArray(result.setTargets) && result.setTargets.some(s => !stock.includes(s.weight)))
    failures.push('a proposed set has an unbuildable load');
  switch (item.id) {
    case 'dp-mixed-overload':
    case 'dp-uniform-failure':
    case 'dp-fatigue-decay':
      if (!(load > 0 && load < max)) failures.push('no concrete lower-load review after demonstrated rep-floor failure');
      break;
    case 'dp-earned-step':
    case 'progression-no-rir': {
      const current = item.latest[0].load;
      const earned = [item.previous, item.latest].every(rows => rows.every(s =>
        s.load === current && s.issuedLoad === current && s.reps >= fixture.ceiling &&
        (s.rir ?? s.issuedRir) !== null && (s.rir ?? s.issuedRir) >= fixture.issuedRir));
      const next = stock.find(v => v > current);
      if (!earned || load !== next || load - current > fixture.maximumIncrease)
        failures.push('did not offer the earned smallest authorized physical increment');
      break;
    }
    default: throw Error('Unimplemented comparison oracle');
  }
  return { outcome: failures.length ? 'fail' : 'pass', failures };
}

export function verifyBundle(bundle, fixture) {
  assert.equal(bundle.schemaVersion, 1);
  assert.equal(bundle.kind, 'paired-headless-progression-review');
  assert.equal(bundle.fixtureDigest, fixture.fixtureDigest);
  assert.equal(bundle.engines['legacy-javascript'].commit, fixture.legacyCommit);
  assert.equal(bundle.measurements.length, caseIds.length * 2);
  for (const engine of ['legacy-javascript', 'independent-kotlin']) {
    const run = bundle.engines[engine];
    assert.match(run.commit, /^[a-f0-9]{40}$/);
    assert.match(run.sourceTree, /^[a-f0-9]{40}$/);
    assert.deepEqual(run.results.map(r => r.caseId), caseIds);
    for (const item of fixture.cases) {
      const r = run.results.find(r => r.caseId === item.id);
      assert.equal(r.inputDigest, scenarioDigest(fixture.fixtureDigest, item.id));
      assert.deepEqual(r.previous, item.previous, 'previous actuals/issued targets differ');
      assert.deepEqual(r.latest, item.latest, 'latest actuals/issued targets differ');
      if (engine === 'independent-kotlin') {
        assert.equal(r.actualRirCount, item.latest.filter(s => s.rir !== null).length);
        assert.equal(r.assumedGoalRirSetCount,[...item.previous,...item.latest]
          .filter(s=>s.rir===null && s.issuedRir!==null).length);
        assert.equal(r.completedSets, fixture.count);
        assert.equal(r.mayAutoApply, false);
      } else {
        assert.equal(r.candidateLoad, r.raw.suggestedLoad);
        assert.deepEqual(r.setTargets, r.raw.setTargets);
      }
      const expected = grade(fixture, item, r);
      const records = bundle.measurements.filter(m => m.caseId === item.id && m.engine === engine);
      assert.equal(records.length, 1);
      const record = records[0];
      assert.equal(record.proof, 'headless');
      assert.equal(record.reproducible, true);
      assert.equal(record.commit, run.commit);
      assert.equal(record.inputDigest, r.inputDigest);
      assert.equal(record.outcome, expected.outcome, 'recorded outcome differs from independent oracle');
      assert.deepEqual(r.assessment, expected);
    }
  }
  return bundle;
}

/** Content-bound evidence, not a self-declared pass or a missing artifact path. */
export function verifyRecordedProgressionEvidence(observations, { allowStaleProvenance = false } = {}) {
  const records = observations.measurements.filter(r => r.proof === 'headless' && caseIds.includes(r.caseId));
  if (!records.length) return { currentNativeTree: false, recordedPairs: 0 };
  assert.equal(records.length, caseIds.length * 2, 'incomplete recorded progression tranche');
  const path = realpathSync(resolve(root, artifactPath));
  assert.ok(!relative(realpathSync(root), path).startsWith(`..${sep}`));
  const bytes = readFileSync(path);
  const fixture = parseFixture(readFileSync(resolve(root, fixturePath)));
  const bundle = verifyBundle(JSON.parse(bytes), fixture);
  for (const record of records) {
    assert.equal(record.artifact, artifactPath);
    assert.equal(record.artifactDigest, sha256(bytes), 'measurement artifact content changed');
    const { artifact, artifactDigest, ...measurement } = record;
    const matching=bundle.measurements.filter(m => m.caseId===record.caseId && m.engine===record.engine);
    assert.equal(matching.length,1);
    assert.deepEqual(measurement,matching[0], 'ledger result is not bound to the measured execution');
  }
  const changedRunnerFiles=runnerPaths.filter(file =>
    bundle.runnerFiles[file]!==sha256(readFileSync(resolve(root,file))));
  if(!allowStaleProvenance)
    assert.deepEqual(changedRunnerFiles,[],
      'comparison runner/fixture changed: remeasure before certification');
  const nativeTree=git(root, 'rev-parse', `HEAD:${sourcePath}`);
  const clean=git(root,'status','--porcelain','--untracked-files=all','--',sourcePath)==='';
  // The audit may report HISTORICAL evidence after a source change, but may
  // never count it as CURRENT measured superiority. Always validate the
  // artifact bytes, runner hashes and independent oracle before demoting it.
  const currentNativeTree=bundle.engines['independent-kotlin'].sourceTree===nativeTree &&
    clean && changedRunnerFiles.length===0;
  if(!allowStaleProvenance) {
    assert.equal(bundle.engines['independent-kotlin'].sourceTree,nativeTree,
      'native engine source changed: historical comparison is not current certification');
    assert.ok(clean,'uncommitted native policies cannot borrow recorded certification');
  }
  return { currentNativeTree, recordedPairs: records.length / 2,
    changedRunnerFiles };
}
