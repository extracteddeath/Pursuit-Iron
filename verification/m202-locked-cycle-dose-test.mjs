import assert from 'node:assert/strict';
import { EXERCISES, EQUIPMENT } from '../modules/engine-api.js';

import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { auditShellVolume, lockedOverflowAtFloor } from '../modules/next-engine/volume-repair.js';

const base = { name: 'M202 locked-cycle dose', split: 'ppl', days: 5, session: 's90', goal: 'both',
  experience: 'intermediate', weeks: 6, deload: true, barbellCap: 3, equipment: EQUIPMENT.map(e => e.id) };
const seeds = [19908, 19811, 199, 20201];
let reproduced = 0, repaired = 0;
for (const seed of seeds) {
  let n = 0;
  const cycle = generateNextCycleForShell({ templateId: 'powerbuilding', config: base, legacyExercises: EXERCISES,
    seed, adaptBetweenBlocks: false, makeId: () => 'm202-' + seed + '-' + (++n) });
  const roster = cycle.blocks[0].days.map(day => [...day.exercises]);
  for (let i = 1; i < cycle.blocks.length; i++) {
    const block = cycle.blocks[i];
    assert.deepEqual(block.days.map(day => [...day.exercises]), roster, 'locked block ' + i + ' must preserve every exercise identity');
    assert.equal(block.nextEngine.program.audit.result, 'pass', 'locked block ' + i + ' engine audit');
    const audit = auditShellVolume(block, EXERCISES);
    const over = audit.issues.filter(issue => issue.status === 'over');
    assert.deepEqual(over, [], 'locked block ' + i + ' must not exceed public phase ceilings');
    assert.ok(audit.weeks.every(w => w.sessions.every(s => s.estimatedMinutes <= s.maxMinutes)), 'locked block ' + i + ' must still fit session time');
    const marker = block.nextEngine.lockedCycleVolumeRepair;
    assert.equal(marker?.rosterPreserved, true, 'locked block must record roster-preserving reconciliation');
    reproduced += marker.beforeOver.length;
    repaired += marker.changed ? 1 : 0;
  }
}
// The full-gym Powerbuilding matrix stopped overflowing before repair when the week-set allocator stopped handing a
// minimum-raised lift a second set; the repair transaction is pinned on a request that still overflows.
{
  let n = 0;
  const cfg = { name: 'M202 repair', unit: 'kg', goal: 'both', experience: 'beginner', days: 4, session: 's60', equipment: ['dumbbell', 'bench', 'pullup'] };
  const cycle = generateNextCycleForShell({ templateId: 'strength_peak', config: cfg, legacyExercises: EXERCISES, makeId: () => 'm202r-' + (++n) });
  for (const block of cycle.blocks.slice(1)) {
    const marker = block.nextEngine.lockedCycleVolumeRepair;
    if (!marker) continue;
    // Strength Peak changes the week between blocks; the repair's promise is the roster it was handed.
    assert.equal(marker.rosterPreserved, true, 'repair keeps the block roster');
    assert.equal(block.nextEngine.program.audit.result, 'pass', 'repaired block engine audit');
    assert.deepEqual(auditShellVolume(block, EXERCISES).issues.filter(issue => issue.status === 'over'), [], 'repaired block within its ceilings');
    reproduced += marker.beforeOver.length;
    repaired += marker.changed && marker.beforeOver.length ? 1 : 0;
  }
}
assert.ok(reproduced > 0, 'regression matrix must reproduce at least one pre-repair locked-cycle overflow');
assert.ok(repaired > 0, 'at least one locked block must require a real set-count repair');
// A locked roster whose every trimmable lift is already at one set cannot shed the last fraction of a set without
// dropping a movement. Strength Peak on dumbbells (beginner, 4 days, 60 min) was refused for chest 8.5 vs 8.4 although
// the engine audit passed every week; it now builds, records the floor, and anything above the floor still refuses.
// (The floor reproduces on the 5-day request since the week-set allocator fix; the 4-day one now repairs in full.)
let floorBlocks = 0;
{
  let n = 0;
  const cfg = { name: 'M202 floor', unit: 'kg', goal: 'both', experience: 'beginner', days: 5, session: 's60', equipment: ['dumbbell', 'bench', 'pullup'] };
  const cycle = generateNextCycleForShell({ templateId: 'strength_peak', config: cfg, legacyExercises: EXERCISES, makeId: () => 'm202f-' + (++n) });
  for (const block of cycle.blocks.slice(1)) {
    assert.equal(block.nextEngine.program.audit.result, 'pass', 'floor block engine audit');
    const marker = block.nextEngine.lockedCycleVolumeRepair;
    if (marker?.status !== 'locked_floor') continue;
    floorBlocks++;
    const audit = auditShellVolume(block, EXERCISES);
    assert.ok(marker.afterOver.length > 0 && marker.afterOver.every(o => o.actual - o.ceiling < 1), 'a floor overflow is less than one set: ' + JSON.stringify(marker.afterOver));
    assert.equal(lockedOverflowAtFloor(audit, EXERCISES, block), true, 'recorded floor is a real floor');
    // Mutant: one trimmable lift at two sets means the roster was not at its floor; that must still refuse.
    const issue = audit.issues.find(x => x.status === 'over');
    const week = structuredClone(audit.weeks[issue.week - 1]);
    const lift = week.sessions.flatMap(s => s.exercises).find(x => !/strength/.test(x.role || '') && x.sets === 1 && /fly|press|pullover/.test(x.exerciseId));
    assert.ok(lift, 'floor block has a one-set accessory to mutate');
    lift.sets = 2;
    const weeks = audit.weeks.map((w, i) => (i === issue.week - 1 ? week : w));
    assert.equal(lockedOverflowAtFloor({ ...audit, weeks }, EXERCISES, block), false, 'an accessory above one set is not a floor');
  }
}
assert.ok(floorBlocks > 0, 'Strength Peak on dumbbells must reproduce a locked-floor block');
console.log('PASS M202 locked-cycle dose: ' + seeds.length + ' powerbuilding cycles within ceilings; ' + reproduced + ' reproduced overflow issue(s) repaired with the roster preserved; ' + floorBlocks + ' locked-floor block(s) accepted, above-floor mutant refused.');
