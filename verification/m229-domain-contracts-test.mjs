import assert from 'node:assert/strict';
import fs from 'node:fs';
import { migrateDomainRecord, validatePrescription, validateEngineProgram, validateHistoryExposure, DOMAIN_SCHEMA_VERSIONS, DomainContractError } from '../modules/next-engine/domain-contracts.js';
import { engineModuleGraph, moduleCycles } from './module-graph.mjs';
import { shellConfigToNextRequest, generateNextProgramForShell } from '../modules/next-engine/app-shell-adapter.js';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
import { EXERCISES } from '../modules/training-domain.js';

for (const kind of Object.keys(DOMAIN_SCHEMA_VERSIONS)) {
    const input = { id: 'legacy', extension: { keep: true } }, before = structuredClone(input);
    const migrated = migrateDomainRecord(kind, input);
    assert.equal(migrated.schemaVersion, 1);
    assert.deepEqual(input, before);
    assert.deepEqual(migrated.extension, input.extension);
    assert.deepEqual(migrateDomainRecord(kind, JSON.parse(JSON.stringify(migrated))), migrated);
    for (const version of [0, 2, 99, '1', null, NaN])
        assert.throws(() => migrateDomainRecord(kind, { ...input, schemaVersion: version }), DomainContractError);
}
assert.throws(() => migrateDomainRecord('unknown', {}), /Unknown domain contract/);
assert.throws(() => validatePrescription({ reps: [12, 8], rir: [1, 2], restSeconds: 90 }), /Invalid reps/);
assert.throws(() => validatePrescription({ reps: [8, 12], rir: [1, 20], restSeconds: 90 }), /Invalid rir/);
assert.throws(() => validateHistoryExposure({ exerciseId: 'x', completedAt: 'invalid', sets: [] }), /timestamp/);
const exposure = validateHistoryExposure({ exerciseId: 'x', completedAt: '2026-10-01T12:00:00Z', sets: [{ load: 50, reps: 12, rir: null }], provenance: { observed: false } });
assert.equal(exposure.sets[0].rir, null, 'absence is not interpreted as zero RIR');
assert.deepEqual(exposure.provenance, { observed: false });
assert.equal(validateHistoryExposure({ ...exposure, sets: [{ load: -20, reps: 8, rir: null }] }).sets[0].load, -20, 'assisted load remains signed');
assert.throws(() => validateHistoryExposure({ ...exposure, sets: [{ load: 20, reps: 8 }] }), /Observed RIR/, 'missing effort must be represented explicitly as unknown');
const row = JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json', import.meta.url))).rows[0];
const request = shellConfigToNextRequest(row.config, [], EXERCISES, row.seed);
assert.equal(normalizeRequest(request).schemaVersion, 1);
assert.throws(() => normalizeRequest({ ...request, schemaVersion: 2 }), /Unsupported request schema/);
const p = generateNextProgramForShell({ config: row.config, legacyExercises: EXERCISES, seed: row.seed, makeId: () => 'contracts' }).program.nextEngine.program;
assert.equal(p.schemaVersion, 1);
assert.deepEqual(validateEngineProgram(p), p);
const corrupted = structuredClone(p); corrupted.sessions[0].exercises[0].sets = NaN;
assert.throws(() => validateEngineProgram(corrupted), /Working sets/);
const duplicate = structuredClone(p); duplicate.sessions[1].id = duplicate.sessions[0].id;
assert.throws(() => validateEngineProgram(duplicate), /unique/);
assert.throws(() => validateEngineProgram({ ...p, schemaVersion: 2 }), /Unsupported program schema/);
const graph = engineModuleGraph();
assert.deepEqual(moduleCycles(graph), [], 'canonical computation modules must have an acyclic dependency graph');
console.log(`PASS M229: explicit immutable legacy migrations, schema/type boundaries, future/malformed refusal, generated contracts and ${graph.size} acyclic computation modules.`);
