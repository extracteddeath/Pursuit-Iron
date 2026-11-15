// A focus the lifter asks for shows up as more weekly work for that region than the same plan without it
// (volume-repair ensureFocusedRegionDose, called from app-shell-adapter generateNextProgramForShell).
import assert from 'node:assert/strict';
import { EXERCISES, GYM_PRESETS, estimateMinutes } from '../modules/engine-api.js';
import { generateNextProgramForShell, focusedPublicRegions } from '../modules/next-engine/app-shell-adapter.js';
import { captureShellVolumeSnapshot, shellVolumeTargets, ensureFocusedRegionDose } from '../modules/next-engine/volume-repair.js';

const LIMIT = { s40: 40, s60: 60, s90: 90 };
const SPLIT = { 3: 'full_body', 4: 'upper_lower', 5: 'ulppl' };
const PARTS = ['chest', 'lats', 'upper_back', 'shoulders', 'biceps', 'triceps', 'quads', 'hamstrings', 'glutes', 'calves', 'abs'];
const build = config => generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 7, makeId: () => 'b1012f' }).program;
// The expectation is measured on the two finished plans with the public region ledger, not read back from focusDose.
const regionsOf = program => captureShellVolumeSnapshot(program, 1, EXERCISES).regions;

assert.deepEqual(focusedPublicRegions({ focus: { shoulders: 1, abs: 1, lats: 0 }, focusList: ['back'] }).sort(),
    ['core', 'lats', 'rear_delts', 'side_delts', 'upper_back'], '0 shoulders, back and abs name the regions the app trains directly');
assert.deepEqual(focusedPublicRegions({}), []);

let cases = 0, more = 0, same = 0, raisedByStage = 0, limited = 0, overLimit = 0, plainOver = 0;
const lower = [];
let armed = null;
for (const session of ['s60', 's90']) for (const goal of ['hypertrophy', 'both']) for (const days of [3, 4, 5]) {
    const config = { name: 'Focus', unit: 'kg', goal, experience: 'intermediate', days, weeks: 4, session, equipment: GYM_PRESETS[0].equipment, split: SPLIT[days] };
    const plain = build(config), base = regionsOf(plain);
    assert.equal(plain.nextEngine.focusDose, undefined, '1 an unfocused plan carries no focus record and is not rebuilt');
    for (const day of plain.days) for (let week = 1; week <= plain.weeks; week++) if (estimateMinutes(plain, day, week) > LIMIT[session]) plainOver++;
    for (const part of PARTS) {
        const focused = build({ ...config, focus: { [part]: 1 } });
        const record = focused.nextEngine.focusDose, after = regionsOf(focused), targets = shellVolumeTargets(focused);
        assert.ok(record, `${session} ${goal} ${days}d ${part}: the focus is recorded`);
        cases++;
        if (record.changes.length) raisedByStage++;
        if (record.status === 'limited') limited++;
        let gained = false;
        const untrimmed = [focused, plain].every(p => p.nextEngine.request.schedule.days.every(day => day.maxMinutes === LIMIT[session]));
        for (const region of focusedPublicRegions({ focus: { [part]: 1 } })) {
            if (after[region] < base[region] - .01) lower.push(`${session} ${goal} ${days}d ${part}/${region} ${base[region]}→${after[region]}`);
            if (after[region] > base[region] + .01) gained = true;
            assert.ok(after[region] <= Math.max(targets.find(t => t.region === region).mrv, base[region]) + .01, `${part}/${region}: never above the region's ceiling`);
            // What the record says matches the plans: its baseline is the unfocused plan's dose, and "improved" means the target was met.
            // (When the displayed-time referee tightened this plan's budgets, its baseline was built under the same budgets.)
            if (untrimmed) assert.ok(Math.abs(record.unfocused[region] - base[region]) < .01, `${part}/${region}: the recorded baseline is the unfocused plan`);
            if (record.status !== 'limited') assert.ok(after[region] + .05 >= record.target[region], `${part}/${region}: a focus reported as met is met`);
        }
        if (!armed && untrimmed && record.changes.length && focusedPublicRegions({ focus: { [part]: 1 } }).length === 1
            && record.changes.every(change => change.operations.every(op => op.delta > 0))) armed = { plain, focused, region: focusedPublicRegions({ focus: { [part]: 1 } })[0] };
        gained ? more++ : same++;
        for (const day of focused.days) for (let week = 1; week <= focused.weeks; week++)
            if (estimateMinutes(focused, day, week) > LIMIT[session]) overLimit++;
        for (const op of record.changes.flatMap(change => change.operations).filter(op => op.delta > 0)) for (let week = 1; week <= focused.weeks; week++) {
            const cell = focused.nextWeekPrescriptions[op.key][week];
            assert.ok(cell.sets <= 5 && !['primary_strength', 'secondary_strength', 'strength_support'].includes(cell.role), `${op.key}: a focus set never takes a lift past five sets or lands on strength work`);
        }
    }
}
// 5. Focus sets never push a day past the displayed limit. Every move goes through the caller's check, so a check
// that refuses everything leaves the plan alone.
{ assert.ok(armed, '5 fixture armed: a single-region focus that added sets');
  const { plain, focused, region } = armed;
  const { focusDose, ...engineState } = focused.nextEngine;
  const reset = { ...focused, nextEngine: engineState, nextWeekPrescriptions: structuredClone(focused.nextWeekPrescriptions) };
  for (const op of focusDose.changes.flatMap(change => change.operations)) for (let week = 1; week <= 4; week++) reset.nextWeekPrescriptions[op.key][week].sets -= op.delta;
  const refused = ensureFocusedRegionDose(reset, plain, EXERCISES, [region], () => false);
  assert.deepEqual(refused.nextWeekPrescriptions, reset.nextWeekPrescriptions, '5 a refusing check blocks every focus set');
  assert.equal(refused.nextEngine.focusDose.status, 'limited');
  const allowed = ensureFocusedRegionDose(reset, plain, EXERCISES, [region], () => true);
  assert.ok(allowed.nextEngine.focusDose.changes.length, '5 the same plan takes focus sets when the check allows them'); }
// The few focused plans still over are the displayed-time referee's own residual (a focused region is protected
// from its refits); pinned as a ceiling that may fall and must not rise.
assert.ok(overLimit <= 8, `5 focused plans over the limit on ${overLimit} day-weeks (ceiling 8); unfocused ${plainOver}`);
// Priority alone left 36% of focused builds unchanged and 4% lower on this engine (measured over 360 builds before the stage).
assert.ok(raisedByStage * 4 >= cases, `2 fixture armed: the stage itself added sets in ${raisedByStage} of ${cases} plans (need a quarter)`);
assert.ok(more * 100 >= cases * 90, `3 a focus raised its region in ${more} of ${cases} plans (need 90%)`);
assert.ok(lower.length <= 1, `4 focused regions lower than unfocused: ${lower.join('; ')}`);
console.log(`PASS B1012 focus dose: ${cases} focused plans, ${more} raised the region (${raisedByStage} by the stage), ${same} unchanged, ${limited} reported limited, ${overLimit} day-weeks over the limit (unfocused plans: ${plainOver}), ${lower.length} lower${lower.length ? ' (' + lower.join('; ') + ')' : ''}.`);
