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

const actionPins = Object.freeze({
    'actions/checkout': '3d3c42e5aac5ba805825da76410c181273ba90b1',
    'actions/setup-node': '820762786026740c76f36085b0efc47a31fe5020',
    'actions/upload-artifact': '043fb46d1a93c77aae656e7c1c64a875d1fc6a0a'
});

for (const [name, workflow] of Object.entries(workflows)) {
    assert.match(workflow, /runs-on: ubuntu-24\.04/, name + ' workflow must pin the Ubuntu OS family');
    assert.doesNotMatch(workflow, /runs-on: ubuntu-latest/, name + ' workflow must not follow a moving OS family');

    for (const match of workflow.matchAll(/uses:\s*([^@\s]+)@([^\s#]+)/g)) {
        const [, action, ref] = match;
        assert.match(ref, /^[a-f0-9]{40}$/, name + ': action must be pinned to a full commit SHA: ' + action);
        if (action in actionPins)
            assert.equal(ref, actionPins[action], name + ': unexpected pinned revision for ' + action);
        else
            assert.fail(name + ': unreviewed action dependency: ' + action);
    }

    if (workflow.includes('actions/checkout@'))
        assert.match(workflow, /persist-credentials:\s*false/, name + ' checkout must not persist credentials');
}

assert.match(
    workflows.phone,
    /puppeteer-core@25\.12\.0/,
    'browser harness must pin the reviewed Puppeteer version'
);
assert.doesNotMatch(
    workflows.phone,
    /puppeteer-core@(?:\^|~|latest|\d+\s|\d+$)/m,
    'browser harness must not use a floating Puppeteer selector'
);
assert.match(workflows.phone, /--ignore-scripts/, 'browser harness install must suppress install scripts');
assert.match(workflows.phone, /--no-audit/, 'browser harness install should not perform an unrelated network audit');
assert.match(workflows.phone, /--no-fund/, 'browser harness install should suppress funding network chatter');

console.log(
    'PASS M237 CI supply chain: official actions are immutable SHA pins, checkout credentials are ephemeral, '
    + 'Ubuntu is pinned to 24.04, and browser harness is pinned to puppeteer-core 25.12.0.'
);
