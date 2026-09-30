import assert from 'node:assert/strict';
import { EXERCISES, EQUIPMENT } from '../modules/App.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { auditShellVolume } from '../modules/next-engine/volume-repair.js';

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
assert.ok(reproduced > 0, 'regression matrix must reproduce at least one pre-repair locked-cycle overflow');
assert.ok(repaired > 0, 'at least one locked block must require a real set-count repair');
console.log('PASS M202 locked-cycle dose: ' + seeds.length + ' powerbuilding cycles; ' + reproduced + ' reproduced overflow issue(s); roster preserved; all repaired blocks within displayed ceilings.');
