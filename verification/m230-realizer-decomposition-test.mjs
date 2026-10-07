import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { engineModuleGraph, moduleCycles } from './module-graph.mjs';
import * as realizer from '../modules/next-engine/realizer.js';
import * as strength from '../modules/next-engine/realizer-strength.js';
import * as time from '../modules/next-engine/realizer-time-budget.js';
import * as sequence from '../modules/next-engine/realizer-sequence.js';
import * as builder from '../modules/next-engine/realizer-session-builder.js';

const root = new URL('../', import.meta.url);
const fixture = JSON.parse(fs.readFileSync(new URL('./fixtures/m230-preserved-realizer.json', import.meta.url)));
for (const record of fixture.records) {
    const source = fs.readFileSync(new URL(record.file, root), 'utf8');
    const begin = `/* M230:PRESERVE:${record.key}:BEGIN */\n`, end = `\n/* M230:PRESERVE:${record.key}:END */`;
    assert.equal(source.split(begin).length, 2, `One preserved authority for ${record.key}`);
    const text = source.split(begin)[1].split(end)[0];
    assert.ok(source.includes(end), `Missing fragment boundary: ${record.key}`);
    assert.equal(crypto.createHash('sha256').update(text).digest('hex'), record.sha256, `M229 expression/order changed: ${record.key}`);
}
assert.equal(realizer.realizeStrengthAnchors, strength.realizeStrengthAnchors);
assert.equal(realizer.estimateSessionMinutes, time.estimateSessionMinutes);
assert.equal(realizer.optimizeSetupAwareSessionSequence, sequence.optimizeSetupAwareSessionSequence);
assert.equal(realizer.finalizePlannedSession, builder.finalizePlannedSession);
const coordinator = fs.readFileSync(new URL('modules/next-engine/realizer.js', root), 'utf8');
const recoveryRepair = fs.readFileSync(new URL('modules/next-engine/recovery-realization.js', root), 'utf8');
const functionalRepair = fs.readFileSync(new URL('modules/next-engine/functional-coverage-repair.js', root), 'utf8');
for (const [name, source] of [['recovery', recoveryRepair], ['functional', functionalRepair]]) {
    assert.match(source, /from '.\/realizer-ranking\.js'/, `${name} repair must consume canonical ranking eligibility helpers`);
    assert.doesNotMatch(source, /function equipmentEligible\(/, `${name} repair must not own a second equipment-eligibility rule`);
    assert.doesNotMatch(source, /function maxBarbells\(/, `${name} repair must not own a second barbell-cap rule`);
}
assert.doesNotMatch(recoveryRepair, /function primaryMuscle\(/, 'recovery repair must not own a second primary-muscle rule');
assert.ok(coordinator.split('\n').length < 400, 'Coordinator must remain smaller than the former 1,975-line realization function');
assert.deepEqual(moduleCycles(engineModuleGraph()), []);
console.log(`PASS M230: ${fixture.records.length} byte-preserved M229 expressions/stages, canonical facade identity, shared repair eligibility ownership, compact coordinator and acyclic stage graph.`);
