import fs from 'node:fs';
import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';
import { splitBuildability } from '../modules/next-engine/app-shell-adapter-capacity.js';

const adapterSource = fs.readFileSync(new URL('../modules/next-engine/app-shell-adapter-capacity.js', import.meta.url), 'utf8');
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

console.log(`M199 wizard feasibility OK: 500 checks in ${elapsed.toFixed(1)}ms; deterministic seed retry markers present.`);
