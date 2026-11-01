import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { dispatchEngineRequest, dispatchEngineJson } from '../engine-bridge/protocol.mjs';

const manifestBytes = fs.readFileSync('android-native/engine-contract/progression-golden.json');
const manifest = JSON.parse(manifestBytes);
assert.equal(manifest.schemaVersion, 1);
assert.equal(manifest.feature, 'progressionPreview');
assert.ok(manifest.cases.length >= 3);
const ids = new Set();
for (const { id, params, expected } of manifest.cases) {
  assert.ok(!ids.has(id), 'Golden case IDs cannot repeat: ' + id);
  ids.add(id);
  const before = JSON.stringify(params);
  const wire = { protocolVersion: 1, id, method: 'progressionPreview', params };
  const response = JSON.parse(dispatchEngineJson(JSON.stringify(wire)));
  assert.equal(response.ok, true, id);
  assert.equal(response.id, id, id);
  assert.equal(response.protocolVersion, 1, id);
  assert.equal(response.result.previewOnly, true, id);
  assert.equal(response.result.source, 'modules/engine-api.js:evaluateWorkoutProgression', id);
  for (const [key, value] of Object.entries(expected))
    assert.deepEqual(response.result[key], value, id + ': ' + key);
  assert.equal(JSON.stringify(params), before, 'engine must not mutate source fixture');
}
// Ensure failing to validate shape never invokes or fabricates a training result.
for (const bad of [
  { exerciseId:'not valid!', repMin:1, repMax:2, targetRir:2, sets:[], equipmentAvailable:[] },
  { ...manifest.cases[0].params, sets:[{load:Infinity,reps:8,rir:2}] },
  { ...manifest.cases[0].params, equipmentAvailable:['barbell','barbell'] },
  { ...manifest.cases[0].params, sets:[] },
  { ...manifest.cases[0].params, repMin:9, repMax:8 }
]) {
  const answer = dispatchEngineRequest({protocolVersion:1,id:'reject',method:'progressionPreview',params:bad});
  assert.equal(answer.ok,false);
  assert.equal(answer.error.code,'INVALID_REQUEST');
}
const kotlin = fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/EngineProtocol.kt','utf8');
const backend = fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/ProgressionBackend.kt','utf8');
for (const name of ['exerciseId','repMin','repMax','targetRir','sets','equipmentAvailable','suggestedLoad','suggestedReps','setTargets'])
  assert.match(kotlin,new RegExp('\\b' + name + '\\b'), 'Kotlin protocol missing '+name);
for (const marker of ['NATIVE_KOTLIN','CANONICAL_JS','class ParityGatedProgressionRouter','No parity-certified progression backend'])
  assert.ok(backend.includes(marker), 'Missing fail-closed Kotlin gateway contract: '+marker);
assert.doesNotMatch(backend, /evaluateWorkoutProgression|suggestedLoad\\s*=/, 'No engine formulas in language router');
const fixtureDigest=crypto.createHash('sha256').update(manifestBytes).digest('hex');
console.log('PASS ' + manifest.cases.length + ' fixed canonical progression cases; Kotlin DTO, per-feature fail-closed routing, fixture sha256 ' + fixtureDigest);
