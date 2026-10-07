import assert from 'node:assert/strict';
import fs from 'node:fs';
import { engineModuleGraph, moduleCycles } from './module-graph.mjs';
import { ENGINE_VERSION, ENGINE_COMPATIBLE_VERSIONS } from '../modules/next-engine/config.js';
import { DEFAULT_CANDIDATE_EVALUATION_LIMIT } from '../modules/next-engine/candidate-optimization.js';

const read = path => fs.readFileSync(new URL('../' + path, import.meta.url), 'utf8');
const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
const profile = JSON.parse(read('BUILD_PROFILE.json'));
const contracts = read('scripts/verify-engine-contracts.mjs');
const sw = read('sw.js');

assert.equal(manifest.milestone, 'M234', 'M235 verifies the M234 release candidate rather than inventing a behaviorless release bump');
assert.equal(manifest.build, 823);
assert.equal(manifest.engineVersion, '0.65.6');
assert.equal(ENGINE_VERSION, '0.65.6');
assert.deepEqual(ENGINE_COMPATIBLE_VERSIONS, ['0.65.2', '0.65.3', '0.65.4', '0.65.5', '0.65.6']);
assert.equal(profile.nextRuntimeModules, 70);
assert.equal(Object.keys(manifest.runtimeFiles).filter(path => path.startsWith('modules/next-engine/')).length, 70);
assert.ok(manifest.runtimeFiles['modules/next-engine/candidate-optimization.js']);
assert.match(sw, /modules\/next-engine\/candidate-optimization\.js/);
assert.equal(DEFAULT_CANDIDATE_EVALUATION_LIMIT, 512);

for (const milestone of ['m228-canonical-boundary', 'm229-domain-contracts', 'm230-realizer-decomposition', 'm231-athlete-response', 'm232-live-autoregulation', 'm233-semantic-exercise-graph', 'm234-candidate-optimization'])
    assert.match(contracts, new RegExp(milestone), `permanent contract runner lost ${milestone}`);

for (const report of [
    'M228_CANONICAL_ENGINE_REPORT.md',
    'M229_DOMAIN_CONTRACTS_REPORT.md',
    'M230_REALIZER_DECOMPOSITION_REPORT.md',
    'M231_ATHLETE_RESPONSE_REPORT.md',
    'M232_LIVE_AUTOREGULATION_REPORT.md',
    'M233_SEMANTIC_EXERCISE_GRAPH_REPORT.md',
    'M234_CANDIDATE_OPTIMIZATION_REPORT.md'
])
    assert.ok(fs.existsSync(new URL('../' + report, import.meta.url)), `missing milestone report: ${report}`);

assert.deepEqual(moduleCycles(engineModuleGraph()), [], 'M228-M234 must finish with an acyclic engine module graph');
console.log('PASS M235: M228-M234 release candidate lineage, module graph, offline coverage and compatibility metadata agree.');
