import assert from 'node:assert/strict';
import fs from 'node:fs';
import {
  capacityTargetCandidates,
  capacityTargetFloor,
  requestedExerciseTarget,
  requestWithExerciseTarget
} from '../modules/next-engine/capacity-policy.js';

const patchedAdapter = await import('../modules/next-engine/app-shell-adapter.js');
assert.equal(typeof patchedAdapter.generateNextProgramForShell, 'function');
assert.equal(typeof patchedAdapter.splitBuildability, 'function');
assert.equal(typeof patchedAdapter.nextProgramToShellProgram, 'function');
assert.equal(typeof patchedAdapter.shellConfigToNextRequest, 'function');

const s90Request = {
  schedule: {
    days: [
      { day: 'monday', minMinutes: 60, maxMinutes: 90, targetExercises: 7 },
      { day: 'wednesday', minMinutes: 60, maxMinutes: 90, targetExercises: 7 },
      { day: 'friday', minMinutes: 60, maxMinutes: 90, targetExercises: 7 }
    ]
  }
};

assert.equal(capacityTargetFloor('s90'), 5, '60–90 may fall back to the proven 40–60 exercise floor');
assert.equal(requestedExerciseTarget(s90Request), 7);
assert.deepEqual(capacityTargetCandidates(s90Request, 's90'), [6, 5]);
assert.deepEqual(capacityTargetCandidates({ schedule: { days: [{ targetExercises: 5 }] } }, 's60'), []);

const relaxed = requestWithExerciseTarget(s90Request, 5);
assert.equal(relaxed.schedule.days[0].minMinutes, 60, 'fallback must keep the selected time band');
assert.equal(relaxed.schedule.days[0].maxMinutes, 90, 'fallback must keep the selected time band');
assert.equal(relaxed.schedule.days[0].targetExercises, 5, 'only the optional exercise target may relax');
assert.equal(s90Request.schedule.days[0].targetExercises, 7, 'request cloning must not mutate the original');

const index = fs.readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const main = fs.readFileSync(new URL('../modules/main.js', import.meta.url), 'utf8');
const sw = fs.readFileSync(new URL('../sw.js', import.meta.url), 'utf8');
assert.doesNotMatch(index, /app-shell-adapter/, 'browser must use the same canonical adapter as Node without an import-map substitution');
assert.match(main, /wizard-stability\.js/, 'wizard stability guard must load before App');
for (const asset of ['capacity-policy.js', 'app-shell-adapter.js', 'wizard-stability.js'])
  assert.ok(sw.includes(asset), `service worker must precache ${asset}`);
assert.ok(sw.includes('"./modules/next-engine/app-shell-adapter.js"'), 'offline cache must include the base shell adapter');
assert.ok(/c\.match\(r,\{ignoreSearch:true\}\)/.test(sw), 'offline runtime must resolve query-mapped adapter imports from the cached base module');

console.log('M196 capacity-band regression: pass');
