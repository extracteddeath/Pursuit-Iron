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
  assert.ok(['met', 'limited'].includes(record.status), '1 strength retention has an explicit outcome');
  assert.deepEqual(record.actual, Object.fromEntries(Object.keys(record.requested).map(region =>
      [region, Math.round((w1[region] ?? 0) * 10) / 10])),
      '1 retained dose reports the actual audited first work week, not a proposed value');
  for (const [region, asked] of Object.entries(record.requested)) {
      assert.ok(asked + .1 >= record.target[region],
          '1 the feasible muscle-capped target cannot exceed the original retention request');
      assert.equal(record.capacityLimited.includes(region), record.target[region] < asked - .05,
          '1 shared-muscle capacity loss must be listed by region: ' + region);
      if (asked - (w1[region] ?? 0) >= .51)
          assert.ok(record.unresolved.includes(region),
              '1 a shortfall to ORIGINAL 60% retention remains explicitly unresolved: ' + region);
  }
  assert.equal(record.status, record.unresolved.length ? 'limited' : 'met');
  assert.deepEqual(record.limits.map(x => x.region), record.unresolved,
      '1 each unresolved region has an explanation and its original requested target');
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
    assert.ok(rec, '2 every comparable non-minimalist Strength transition reports dose even when no safe addition was possible');
    assert.equal(rec.status, rec.unresolved.length ? 'limited' : 'met',
        '2 an unmet original retention request cannot be marked complete');
    const first = au.weeks[0].regions;
    for (const [region, requested] of Object.entries(rec.requested)) {
        const got = first[region] ?? 0;
        assert.equal(rec.actual[region], Math.round(got * 10) / 10,
            '2 actual retained region sets are audited, not inferred');
        if (requested - got >= .51) assert.ok(rec.unresolved.includes(region),
            '2 original 60% target was missed but not reported: ' + region);
        assert.equal(rec.capacityLimited.includes(region),
            rec.target[region] < requested - .05,
            '2 shared-muscle constraint is visible even when an achievable target is lowered');
    }
    restored += rec.restored.length; raised += rec.sets.reduce((n, s) => n + s.delta, 0);
    for (const set of rec.sets) assert.ok(!/strength/.test(str.nextWeekPrescriptions[set.key]?.[1]?.role ?? ''), '2 never a strength lift');
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

// 4. A new plan meets each region's minimum where it can (withRegionFloors): fewer regions short than the plan as
//    filled, never a day past its limit, the engine audit passing, and some plans actually repaired.
{ const { generateNextProgramForShell } = await import('../modules/next-engine/app-shell-adapter.js');
  let repaired = 0, plans = 0, shortBefore = 0, shortAfter = 0;
  for (const kit of [FULL, ['dumbbell', 'bench', 'pullup']]) for (const session of ['s40', 's90', 's120']) for (const days of [3, 5, 6]) for (const goal of ['hypertrophy', 'both']) for (const experience of ['beginner', 'intermediate']) {
    let p; try { p = generateNextProgramForShell({ config: { name: 'floor', unit: 'kg', goal, experience, days, session, equipment: kit }, legacyExercises: EXERCISES, seed: 7, makeId: () => 'b1019f' }).program; } catch { continue; }
    plans++;
    const record = p.nextEngine.regionFloor; if (!record) continue;
    repaired++; shortBefore += record.before.length; shortAfter += record.remaining.length;
    assert.ok(record.remaining.length <= record.before.length, '4 fewer regions short: ' + JSON.stringify(record));
    assert.equal(p.nextEngine.program.audit.result, 'pass', '4 engine audit');
    const hi = SESSION_BOUNDS[session][1];
    for (let w = 1; w <= weeksOf(p); w++) p.days.forEach(d => assert.ok(estimateMinutes(p, d, w) <= hi, `4 ${session} week ${w} inside ${hi} minutes`));
  }
  assert.ok(plans >= 50 && repaired >= 2, `4 ${repaired} of ${plans} plans repaired`);
  console.log(`PASS 4 ${repaired} of ${plans} new plans had a region under its minimum: ${shortBefore} -> ${shortAfter} short regions, every day inside its limit.`); }

console.log('PASS B1019 strength entry retention.');
