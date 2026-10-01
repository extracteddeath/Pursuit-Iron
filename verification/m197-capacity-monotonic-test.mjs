import assert from 'node:assert/strict';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import {
  generateNextProgramForShell,
  splitBuildability
} from '../modules/next-engine/app-shell-adapter.js';
import {
  capacityMinimumCandidates,
  requestWithMinimumMinutes,
  requestedMinimumMinutes
} from '../modules/next-engine/capacity-policy.js';

const fullEquipment = [
  'barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'
];

// The shell mapper only needs an identity-compatible v661 record for each engine exercise here.
// Keeping IDs aligned makes the regression exercise the REAL adapter/buildability path rather than
// stopping at generateProgram and missing the exact class of wizard bug M196 failed to catch.
const legacyExercises = [...EXERCISE_MAP.values()].map(def => ({
  id: def.id,
  name: def.name,
  equip: Array.isArray(def.equipment) ? [...def.equipment] : []
}));

const baseConfig = {
  name: 'Capacity regression',
  split: 'full_body',
  days: 3,
  session: 's60',
  goal: 'hypertrophy',
  experience: 'intermediate',
  equipment: fullEquipment,
  noBodyweight: false,
  noSupersets: false,
  barbellCap: 3,
  weeks: 6,
  volumeApproach: 'standard',
  progressionStyle: 'auto',
  focus: {},
  focusList: [],
  reduce: [],
  unit: 'lb'
};

// Policy-level contract: 60–90 first asks for 60 minutes, then may inherit the proven 40-minute
// floor while KEEPING maxMinutes=90. Larger bands progressively inherit shorter floors.
const syntheticS90 = {
  schedule: { days: [
    { day: 'monday', minMinutes: 60, maxMinutes: 90, targetExercises: 7 },
    { day: 'wednesday', minMinutes: 60, maxMinutes: 90, targetExercises: 7 },
    { day: 'friday', minMinutes: 60, maxMinutes: 90, targetExercises: 7 }
  ] }
};
assert.equal(requestedMinimumMinutes(syntheticS90), 60);
assert.deepEqual(capacityMinimumCandidates(syntheticS90, 's90'), [40]);
const softened = requestWithMinimumMinutes(syntheticS90, 40);
assert.equal(softened.schedule.days[0].minMinutes, 40);
assert.equal(softened.schedule.days[0].maxMinutes, 90, 'fallback must never shrink selected capacity');
assert.equal(syntheticS90.schedule.days[0].minMinutes, 60, 'fallback cloning must not mutate original request');

const baseline = splitBuildability(baseConfig, legacyExercises);
assert.equal(baseline.ok, true, `40–60 Full Body baseline must build: ${JSON.stringify(baseline)}`);

for (const [session, expectedMax] of [['s90', 90], ['s120', 120], ['s120p', 150]]) {
  const config = { ...baseConfig, session };
  const verdict = splitBuildability(config, legacyExercises);
  assert.equal(
    verdict.ok,
    true,
    `${session} must stay buildable once the same Full Body setup works at s60: ${JSON.stringify(verdict)}`
  );

  const built = generateNextProgramForShell({
    config,
    banned: [],
    legacyExercises,
    seed: 197
  });
  assert.equal(built.nextProgram.audit.result, 'pass');
  for (const day of built.request.schedule.days) {
    assert.equal(day.maxMinutes, expectedMax, `${session} fallback may not reduce maxMinutes`);
    assert.ok(day.minMinutes <= expectedMax);
  }
}

console.log('M197 capacity monotonic regression: pass');
