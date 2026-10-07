import assert from 'node:assert/strict';
import fs from 'node:fs';

const read = file => fs.readFileSync(new URL('../' + file, import.meta.url), 'utf8');
const workflows = Object.fromEntries([
    'core', 'audit', 'phone', 'release'
].map(name => [name, read({
    core: '.github/workflows/engine-contracts.yml',
    audit: '.github/workflows/engine-export.yml',
    phone: '.github/workflows/phone-integrity.yml',
    release: '.github/workflows/release-integrity.yml'
}[name])]));

const packageJson = JSON.parse(read('package.json'));
const packageLock = JSON.parse(read('package-lock.json'));
const actionPins = Object.freeze({
    'actions/checkout': '3d3c42e5aac5ba805825da76410c181273ba90b1',
    'actions/setup-node': '820762786026740c76f36085b0efc47a31fe5020',
    'actions/upload-artifact': '043fb46d1a93c77aae656e7c1c64a875d1fc6a0a'
});

for (const [name, workflow] of Object.entries(workflows)) {
    assert.match(workflow, /runs-on: ubuntu-24\.04/, name + ' workflow must pin the Ubuntu OS family');
    assert.doesNotMatch(workflow, /runs-on: ubuntu-latest/, name + ' workflow must not follow a moving OS family');

    const seenActions = [];
    for (const match of workflow.matchAll(/uses:\s*([^@\s]+)@([^\s#]+)/g)) {
        const [, action, ref] = match;
        seenActions.push(action);
        assert.match(ref, /^[a-f0-9]{40}$/, name + ': action must be pinned to a full commit SHA: ' + action);
        if (action in actionPins)
            assert.equal(ref, actionPins[action], name + ': unexpected pinned revision for ' + action);
        else
            assert.fail(name + ': unreviewed action dependency: ' + action);
    }
    assert.ok(seenActions.includes('actions/checkout'), name + ' workflow must use the reviewed checkout action');
    assert.ok(seenActions.includes('actions/setup-node'), name + ' workflow must use the reviewed setup-node action');
    assert.match(workflow, /persist-credentials:\s*false/, name + ' checkout must not persist credentials');
}

assert.equal(packageJson.private, true, 'verification package must stay private');
assert.equal(packageJson.devDependencies?.['puppeteer-core'], '25.12.0', 'Puppeteer must be an exact declared version');
assert.equal(packageLock.lockfileVersion, 3, 'browser harness lockfile must use npm lockfile v3');
assert.equal(packageLock.packages?.['']?.devDependencies?.['puppeteer-core'], '25.12.0', 'lock root must match package.json');
assert.equal(packageLock.packages?.['node_modules/puppeteer-core']?.version, '25.12.0', 'locked Puppeteer version changed');

for (const [name, record] of Object.entries(packageLock.packages ?? {})) {
    if (!name) continue;
    assert.equal(typeof record.version, 'string', 'locked package lacks version: ' + name);
    assert.match(record.resolved ?? '', /^https:\/\/registry\.npmjs\.org\//, 'locked package must resolve from npm registry: ' + name);
    assert.match(record.integrity ?? '', /^sha512-/, 'locked package must carry SHA-512 integrity: ' + name);
}

assert.match(workflows.phone, /npm ci --ignore-scripts --no-audit --no-fund/, 'browser harness must install from the committed lockfile');
assert.doesNotMatch(workflows.phone, /npm install/, 'browser workflow must not resolve dependencies dynamically');
assert.match(workflows.phone, /--ignore-scripts/, 'browser harness install must suppress install scripts');
assert.match(workflows.phone, /--no-audit/, 'browser harness install should not perform an unrelated network audit');
assert.match(workflows.phone, /--no-fund/, 'browser harness install should suppress funding network chatter');

console.log(
    'PASS M237 CI supply chain: official actions are immutable SHA pins, checkout credentials are ephemeral, '
    + 'Ubuntu is pinned to 24.04, and the Puppeteer 25.12.0 dependency graph is npm-lock/integrity pinned.'
);
