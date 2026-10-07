import assert from 'node:assert/strict';
import fs from 'node:fs';
import { contractGroups, browserShards, releaseTests, verifyContractRegistry } from './contract-registry.mjs';

const read = file => fs.readFileSync(new URL('../' + file, import.meta.url), 'utf8');
const registry = verifyContractRegistry();
const core = read('.github/workflows/engine-contracts.yml');
const phone = read('.github/workflows/phone-integrity.yml');
const audit = read('.github/workflows/engine-export.yml');
const release = read('.github/workflows/release-integrity.yml');
const coreRunner = read('scripts/verify-engine-contracts.mjs');
const browserRunner = read('scripts/verify-browser-contracts.mjs');
const releaseRunner = read('scripts/verify-release.mjs');
const exporterPackage = JSON.parse(read('engine-lab/package.json'));

const matrixValues = (text, key) => {
    const match = text.match(new RegExp(key + ': \\[([^\\]]+)\\]'));
    assert.ok(match, 'missing workflow matrix: ' + key);
    return match[1].split(',').map(value => value.trim()).filter(Boolean).sort();
};

assert.deepEqual(matrixValues(core, 'group'), Object.keys(contractGroups).sort(), 'core workflow and registry groups diverged');
assert.deepEqual(matrixValues(phone, 'shard'), Object.keys(browserShards).sort(), 'browser workflow and registry shards diverged');

for (const [name, workflow] of Object.entries({ core, phone, audit, release })) {
    assert.match(workflow, /concurrency:/, name + ' workflow lost concurrency control');
    assert.match(workflow, /github\.event\.pull_request\.head\.ref \|\| github\.ref_name/, name + ' workflow must share one branch/head concurrency key');
    assert.match(workflow, /cancel-in-progress: true/, name + ' workflow must cancel stale branch runs');
    assert.match(workflow, /permissions:\s*\n\s*contents: read/, name + ' workflow must remain read-only');
}

assert.match(coreRunner, /contractGroups/, 'core runner must use canonical registry');
assert.match(coreRunner, /verifyContractRegistry/, 'core runner must validate gate ownership');
assert.match(browserRunner, /browserShards/, 'browser runner must use canonical registry');
assert.match(browserRunner, /verifyContractRegistry/, 'browser runner must validate gate ownership');
assert.match(releaseRunner, /releaseTests/, 'release runner must use release-specific registry');
assert.match(releaseRunner, /verifyContractRegistry/, 'release runner must validate gate ownership');

assert.equal(Object.keys(exporterPackage.dependencies ?? {}).length, 0, 'canonical exporter must not retain unused runtime dependencies');
assert.equal(Object.keys(exporterPackage.devDependencies ?? {}).length, 0, 'canonical exporter must not retain unused dev dependencies');
assert.doesNotMatch(audit, /npm ci --prefix engine-lab/, 'independent audit must not reinstall retired AST tooling');
assert.match(audit, /github\.ref == 'refs\/heads\/main' \|\| github\.event_name == 'workflow_dispatch'/, 'engine artifact publication must stay main/manual only');

assert.equal(registry.contractCount, Object.values(contractGroups).flat().length);
assert.equal(registry.browserCount, Object.values(browserShards).flat().length);
assert.equal(registry.releaseCount, releaseTests.length);
assert.equal(registry.browserCount, 12, 'browser coverage count changed unexpectedly');
assert.equal(registry.releaseCount, 1, 'release-only semantics should remain narrow');

console.log(
    `PASS M236 verification topology: ${registry.contractCount} uniquely owned source contracts, `
    + `${registry.browserCount} browser contracts in ${Object.keys(browserShards).length} shards, `
    + `${registry.releaseCount} release-specific contract; stale branch CI cancels and exporter has zero dependencies.`
);
