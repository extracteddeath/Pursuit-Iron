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
const canonicalExporter = read('engine-lab/canonical-export.mjs');
const retiredExporterShim = new URL('../engine-lab/export-engine.mjs', import.meta.url);

const matrixValues = (text, key) => {
    const match = text.match(new RegExp(key + ': \\[([^\\]]+)\\]'));
    assert.ok(match, 'missing workflow matrix: ' + key);
    return match[1].split(',').map(value => value.trim()).filter(Boolean).sort();
};

assert.deepEqual(matrixValues(core, 'group'), Object.keys(contractGroups).sort(), 'core workflow and registry groups diverged');
assert.deepEqual(matrixValues(phone, 'shard'), Object.keys(browserShards).sort(), 'browser workflow and registry shards diverged');

for (const [name, workflow] of Object.entries({ core, phone, audit, release })) {
    assert.match(workflow, /push:\s*\n\s*branches: \[main\]/, name + ' workflow push trigger must be main-only');
    assert.match(workflow, /pull_request:\s*\n\s*branches: \[main\]/, name + ' workflow must verify pull requests into main');
    assert.match(workflow, /workflow_dispatch:/, name + ' workflow must support manual pre-PR verification');
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
assert.equal(fs.existsSync(retiredExporterShim), false, 'retired exporter shim must not return; callers must use canonical-export.mjs directly');
assert.equal(Object.keys(exporterPackage.devDependencies ?? {}).length, 0, 'canonical exporter must not retain unused dev dependencies');
assert.match(canonicalExporter, /engineRoot = 'modules\/engine-api\.js'/,
    'standalone export must be derived from the single canonical engine API root');
assert.doesNotMatch(canonicalExporter, /engineRoots|\[engineRoot,\s*['"]modules\/engine-shell\.js['"]\]/,
    'engine-shell may remain a compatibility surface but must not become a second export authority');
assert.doesNotMatch(canonicalExporter, /reference\/App\.production|reference\/verification|integration-suites/,
    'standalone export must not duplicate UI or verification reference source');
assert.doesNotMatch(canonicalExporter, /appSourceSha256|readFileSync\(path\.join\(root, ['"]modules\/App\.js['"]\)\)/,
    'standalone export must not retain dead UI-source hash coupling');
assert.match(canonicalExporter, /importsOutsideEngine = productionImports\.some\(moduleFile => !runtimeSet\.has\(moduleFile\)\)/,
    'standalone verification must exclude suites that depend on non-engine production modules');
assert.doesNotMatch(canonicalExporter, /\^M22\[4-9\]|\^M23\[0-5\]/,
    'standalone export must not bundle milestone reports as runtime evidence');
assert.doesNotMatch(audit, /npm ci --prefix engine-lab/, 'independent audit must not reinstall retired AST tooling');
assert.match(audit, /node engine-lab\/canonical-export\.mjs/, 'independent audit must call the canonical exporter directly');
assert.match(audit, /github\.ref == 'refs\/heads\/main' \|\| github\.event_name == 'workflow_dispatch'/, 'engine artifact publication must stay main/manual only');

assert.equal(registry.contractCount, Object.values(contractGroups).flat().length);
assert.equal(registry.browserCount, Object.values(browserShards).flat().length);
assert.equal(registry.releaseCount, releaseTests.length);
assert.equal(registry.browserCount, 15, 'logging layout, worker generation and expressive motion retain their browser integration coverage');
assert.ok(browserShards.integration.includes('b831-expressive-motion-browser-test.mjs'),
    'app-wide expressive interaction coverage must run in the integration shard');
assert.equal(registry.releaseCount, 1, 'release-only semantics should remain narrow');

console.log(
    `PASS M236 verification topology: ${registry.contractCount} uniquely owned source contracts, `
    + `${registry.browserCount} browser contracts in ${Object.keys(browserShards).length} shards, `
    + `${registry.releaseCount} release-specific contract; feature branches are PR-only, stale PR/main CI cancels, and exporter has zero dependencies or duplicated UI/reference payload.`
);
