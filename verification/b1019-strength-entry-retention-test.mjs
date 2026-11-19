// B1019: a strength block after hypertrophy keeps a share of every region's work (volume-repair retainStrengthEntryDose).
// Measured before: a five-day 90-minute full_body_patterns cycle went 102 -> 63 weekly sets with upper back 10 -> 3.6
// and side delts 9 -> 4 while its strength days used 35-51 of 90 minutes; across 432 Powerbuilding cycles the worst
// strength blocks kept 0% of core, 29% of upper back and 35% of lats.
import assert from 'node:assert/strict';
import { EXERCISES, GYM_PRESETS, SESSION_BOUNDS, estimateMinutes, weeksOf } from '../modules/engine-api.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { auditShellVolume, captureShellVolumeSnapshot, retainStrengthEntryDose, STRENGTH_ENTRY_RETENTION } from '../modules/next-engine/volume-repair.js';

const FULL = GYM_PRESETS[0].equipment;
const cycle = (config, adapt = true) => { let n = 0; return generateNextCycleForShell({ templateId: 'powerbuilding', config, legacyExercises: EXERCISES, adaptBetweenBlocks: adapt, makeId: () => 'b1019-' + (++n) }); };
const entry = (c) => { const i = c.blocks.findIndex((b, k) => k > 0 && b.nextEngine.program.phase === 'strength_accumulation' && c.blocks[k - 1].nextEngine.program.phase === 'hypertrophy_accumulation'); return i > 0 ? [c.blocks[i - 1], c.blocks[i]] : null; };

// 1. The reproduced plan: every region at its retained share or reported, a dropped lateral raise brought back.
{ const c = cycle({ name: 'fbp', unit: 'kg', goal: 'both', experience: 'intermediate', days: 5, session: 's90', equipment: FULL, split: 'full_body_patterns', weeks: 6 });
  const [hyp, str] = entry(c);
  const record = str.nextEngine.strengthEntryDose;
  assert.ok(record, '1 the strength block records its retained dose');
  assert.equal(record.retention, STRENGTH_ENTRY_RETENTION);
  const au = auditShellVolume(str, EXERCISES), w1 = au.weeks[0].regions;
  for (const [region, target] of Object.entries(record.target))
      assert.ok(w1[region] >= target - .5 || record.unresolved.includes(region), `1 ${region} ${w1[region]} reaches ${target} or is reported`);
  assert.ok(record.restored.length >= 1, '1 a dropped hypertrophy accessory comes back');
  const hypNames = new Set(hyp.days.flatMap(d => d.exercises).map(id => EXERCISES.find(x => x.id === id)?.name));
  for (const r of record.restored) assert.ok(hypNames.has(r.exercise), `1 ${r.exercise} comes from the hypertrophy roster`);
  assert.ok(w1.side_delts >= 6 - 1e-6, `1 side delts kept: ${w1.side_delts}`);
  console.log(`PASS 1 five-day 90-minute plan: restored ${record.restored.map(r => r.exercise).join(', ')}; side delts ${w1.side_delts}, rear delts ${w1.rear_delts}, upper back ${w1.upper_back}.`); }

// 2. Limits across a matrix: no region over its ceiling, no day over its limit on the shown clock, engine audit passes,
//    strength lifts keep their sets, nothing under the strength phase's minimum.
{ let blocks = 0, restored = 0, raised = 0;
  for (const session of ['s40', 's60', 's90']) for (const days of [3, 5]) for (const goal of ['hypertrophy', 'both']) for (const adapt of [false, true]) {
    const c = cycle({ name: 'm', unit: 'kg', goal, experience: 'intermediate', days, session, equipment: FULL }, adapt);
    const pair = entry(c); if (!pair) continue;
    const [, str] = pair; blocks++;
    assert.equal(str.nextEngine.program.audit.result, 'pass', `2 ${session} ${days}d engine audit`);
    const au = auditShellVolume(str, EXERCISES);
    assert.deepEqual(au.issues.filter(x => x.status === 'over'), [], `2 ${session} ${days}d no region over its ceiling`);
    assert.deepEqual(au.issues.filter(x => x.status === 'under'), [], `2 ${session} ${days}d no region under its minimum`);
    const hi = SESSION_BOUNDS[session][1];
    for (let w = 1; w <= weeksOf(str); w++) str.days.forEach(d => assert.ok(estimateMinutes(str, d, w) <= hi, `2 ${session} ${days}d week ${w} inside ${hi} minutes`));
    const rec = str.nextEngine.strengthEntryDose;
    if (rec) { restored += rec.restored.length; raised += rec.sets.reduce((n, s) => n + s.delta, 0);
      for (const s of rec.sets) assert.ok(!/strength/.test(str.nextWeekPrescriptions[s.key]?.[1]?.role ?? ''), '2 never a strength lift'); }
  }
  assert.ok(blocks >= 20 && (raised > 0 || restored > 0), `2 ${blocks} strength entries checked, ${raised} sets and ${restored} lifts kept`);
  console.log(`PASS 2 ${blocks} strength entries inside every ceiling and limit; ${raised} sets and ${restored} lifts kept.`); }

// 3. Idempotent, and leaves everything else alone: a second pass adds nothing; a hypertrophy block, a minimalist plan
//    and a plan without a hypertrophy predecessor are returned as they are.
{ const c = cycle({ name: 'i', unit: 'kg', goal: 'both', experience: 'intermediate', days: 4, session: 's60', equipment: FULL });
  const [hyp, str] = entry(c);
  const prior = captureShellVolumeSnapshot(hyp, weeksOf(hyp), EXERCISES);
  const again = retainStrengthEntryDose(hyp, str, EXERCISES, prior);
  assert.deepEqual(again.nextWeekPrescriptions, str.nextWeekPrescriptions, '3 a second pass changes no cell');
  assert.equal(retainStrengthEntryDose(null, hyp, EXERCISES), hyp, '3 a hypertrophy block is untouched');
  assert.equal(retainStrengthEntryDose(null, str, EXERCISES), str, '3 without a hypertrophy predecessor nothing changes');
  const minimal = cycle({ name: 'min', unit: 'kg', goal: 'both', experience: 'intermediate', days: 4, session: 's60', equipment: FULL, volumeApproach: 'minimalist' });
  const mp = entry(minimal);
  if (mp) assert.equal(mp[1].nextEngine.strengthEntryDose, undefined, '3 a minimalist plan keeps the strength dose');
  console.log('PASS 3 idempotent; hypertrophy, unpaired and minimalist blocks untouched.'); }

console.log('PASS B1019 strength entry retention.');
