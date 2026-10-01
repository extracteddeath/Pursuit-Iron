import fs from 'node:fs';
import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';
import { shellConfigToNextRequest, splitBuildability } from '../modules/next-engine/app-shell-adapter.js';
import { firstPassingCapacityProgram } from '../modules/next-engine/capacity-generation.js';

const adapterSource = fs.readFileSync(new URL('../modules/next-engine/app-shell-adapter.js', import.meta.url), 'utf8');
const capacitySource = fs.readFileSync(new URL('../modules/next-engine/capacity-generation.js', import.meta.url), 'utf8');
const splitStart = adapterSource.indexOf('export function splitBuildability');
assert.ok(splitStart >= 0, 'capacity adapter must export splitBuildability');
const splitBody = adapterSource.slice(splitStart);
assert.equal(/generateNextProgramForShell\s*\(/.test(splitBody), false, 'wizard buildability must not run full program generation');
assert.equal(/generateProgram\s*\(/.test(splitBody), false, 'wizard buildability must not invoke the generator directly');
assert.match(capacitySource, /alternateSeedCandidates/, 'capacity generation must include deterministic rejected-seed retries');
assert.match(capacitySource, /effectiveSeed/, 'successful retry seed must be carried forward');

const fullGym = ['barbell', 'dumbbell', 'bench', 'cable', 'machine', 'smith', 'pullup', 'legpress'];
const base = {
  split: 'full_body',
  days: 5,
  session: 's60',
  goal: 'both',
  experience: 'intermediate',
  weeks: 6,
  equipment: fullGym,
  noBodyweight: false,
  noSupersets: false,
  barbellCap: 3
};

const sessions = ['s40', 's60', 's90', 's120', 's120p'];
const start = performance.now();
for (let round = 0; round < 100; round++) {
  for (const session of sessions) {
    const verdict = splitBuildability({ ...base, session }, []);
    assert.equal(verdict.ok, true, `generic Full Body ${session} should stay selectable without a speculative roll`);
  }
}
const elapsed = performance.now() - start;
assert.ok(elapsed < 500, `500 wizard feasibility checks should stay UI-cheap; took ${elapsed.toFixed(1)}ms`);

// Exercise the real generation boundary too. The wizard being responsive is only half the fix: each
// normal Full Body time band must actually reach a passing program without surfacing a random
// FULL_BODY_INCOMPLETE / "missing upper pull work" failure. Use seed 1 because that was the old
// wizard's speculative seed and therefore directly guards the user-visible regression.
for (const session of sessions) {
  for (const days of [3, 5]) {
    const cfg = { ...base, session, days };
    const request = shellConfigToNextRequest(cfg, [], [], 1);
    const attempt = firstPassingCapacityProgram(request, cfg, { blockWeeks: cfg.weeks });
    assert.equal(attempt.result.program.audit.result, 'pass', `Full Body ${days}d ${session} must produce an audited passing plan`);
    const structural = (attempt.result.program.audit.findings ?? []).filter(f => f.code === 'FULL_BODY_INCOMPLETE');
    assert.equal(structural.length, 0, `Full Body ${days}d ${session} must not finish with missing upper/lower movement work`);
    assert.equal(attempt.request.seed, attempt.result.program.seed, `Full Body ${days}d ${session} must persist the seed that actually passed`);
  }
}

const contractFailure = splitBuildability({
  ...base,
  split: 'five_three_one',
  days: 4,
  goal: 'strength',
  equipment: ['dumbbell'],
  noBodyweight: false
}, []);
assert.equal(contractFailure.ok, false, 'named strength contracts must still reject impossible equipment up front');
assert.equal(contractFailure.kind, 'lifts');

const noEquipment = splitBuildability({
  ...base,
  split: 'full_body',
  equipment: [],
  noBodyweight: true
}, []);
assert.equal(noEquipment.ok, false, 'zero usable equipment with bodyweight disabled should still refuse cheaply');
assert.equal(noEquipment.kind, 'coverage');

console.log(`M199 wizard feasibility OK: 500 checks in ${elapsed.toFixed(1)}ms; all Full Body time bands passed real generation without structural gaps.`);
