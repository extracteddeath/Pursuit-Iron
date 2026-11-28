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
// The modulepreload list names the canonical adapter by path; only the import map could substitute it.
assert.doesNotMatch(index.match(/<script type="importmap">[\s\S]*?<\/script>/)?.[0] ?? '', /app-shell-adapter/, 'browser must use the same canonical adapter as Node without an import-map substitution');
assert.match(main, /wizard-stability\.js/, 'wizard stability guard must load before App');
for (const asset of ['capacity-policy.js', 'app-shell-adapter.js', 'wizard-stability.js'])
  assert.ok(sw.includes(asset), `service worker must precache ${asset}`);
assert.ok(sw.includes('"./modules/next-engine/app-shell-adapter.js"'), 'offline cache must include the base shell adapter');
assert.ok(/c\.match\(r,\{ignoreSearch:true\}\)/.test(sw), 'offline runtime must resolve query-mapped adapter imports from the cached base module');

// The reported five-day Week 1 has 121 working sets, including three direct
// lateral/rear-delt exposures and 20 direct pulling sets. Available minutes
// must not by themselves raise every ordinary muscle above its preferred dose.
// This is a policy regression, not a reconstruction of the unknown generation seed.
const { productiveTimeBandDoseTarget } = await import('../modules/next-engine/prescription.js');
const ordinary = { muscle: 'side_delts', priority: 'normal', minimum: 6, preferred: 10, upper: 20 };
const prioritized = { ...ordinary, priority: 'high' };
for (const minMinutes of [20, 40, 60, 90, 120]) {
  const request = { schedule: { days: Array.from({ length: 5 }, () => ({ minMinutes, maxMinutes: minMinutes + 30 })) } };
  for (const phase of ['hypertrophy_accumulation', 'mixed_accumulation', 'strength_accumulation', 'peak']) {
    assert.ok(productiveTimeBandDoseTarget(request, ordinary, phase) <= ordinary.preferred,
      'ordinary muscle dose must not be increased merely to occupy a longer session');
    assert.ok(productiveTimeBandDoseTarget(request, prioritized, phase) <= prioritized.upper);
  }
  if (minMinutes >= 60) assert.ok(productiveTimeBandDoseTarget(request, prioritized, 'hypertrophy_accumulation') > prioritized.preferred,
    'explicit muscle priorities retain extra productive capacity');
}
console.log('M196 capacity-band regression: pass');
