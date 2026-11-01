import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const hash = buffer => crypto.createHash('sha256').update(buffer).digest('hex');
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const hex = /^[0-9a-f]{64}$/;

/**
 * Deterministic immutable identity over the canonical runtime dependency closure.
 * Validates the actual byte-for-byte snapshot; never labels output as certified.
 */
export function inspectEngineSnapshot(snapshotRoot, fixturesFile) {
  const coverage = readJson(path.join(snapshotRoot, 'coverage.json'));
  const snapshot = readJson(path.join(snapshotRoot, 'snapshot.json'));
  assert.equal(coverage.schemaVersion, 2, 'unexpected canonical export schema');
  assert.equal(coverage.entryRoot, 'modules/engine-api.js', 'incorrect canonical source root');
  assert.equal(snapshot.schemaVersion, 2, 'unexpected snapshot manifest schema');
  const files = coverage.canonicalRuntimeFiles;
  assert.ok(Array.isArray(files) && files.length > 0, 'empty canonical runtime');
  assert.ok(files.includes(coverage.entryRoot), 'missing canonical root');
  assert.equal(new Set(files).size, files.length, 'duplicate runtime file');
  const sorted = [...files].sort();
  const sourceEntries = sorted.map(file => {
    assert.ok(/^modules\/[a-zA-Z0-9_./-]+\.js$/.test(file) &&
      !file.includes('..') && !file.includes('//'), 'unsafe source path');
    const expected = snapshot.files[file];
    assert.ok(typeof expected === 'string' && hex.test(expected), 'missing source hash');
    const actual = hash(fs.readFileSync(path.join(snapshotRoot, file)));
    assert.equal(actual, expected, 'canonical runtime byte mismatch: ' + file);
    return file + '\0' + actual + '\n';
  });
  const fixtureBytes = fs.readFileSync(fixturesFile);
  const fixtures = JSON.parse(fixtureBytes);
  assert.equal(fixtures.schemaVersion, 1);
  assert.equal(fixtures.feature, 'progressionPreview');
  assert.ok(Array.isArray(fixtures.cases) && fixtures.cases.length >= 3);
  return Object.freeze({
    identityVersion: 1,
    protocolVersion: 1,
    source: coverage.entryRoot,
    sourceDigest: hash(Buffer.from(sourceEntries.join(''), 'utf8')),
    fixtureDigest: hash(fixtureBytes),
    canonicalRuntimeFiles: sorted.length,
    parityCertified: false
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const snapshotRoot = process.argv[2];
  if (!snapshotRoot || process.argv.length !== 3) {
    console.error('Usage: node android-native/engine-contract/snapshot-identity.mjs <verified-snapshot-dir>');
    process.exitCode = 2;
  } else {
    const fixtures = fileURLToPath(new URL('./progression-golden.json', import.meta.url));
    try {
      console.log(JSON.stringify(inspectEngineSnapshot(snapshotRoot, fixtures), null, 2));
    } catch (e) {
      console.error('Canonical engine snapshot identity unavailable: ' + e.message);
      process.exitCode = 1;
    }
  }
}
