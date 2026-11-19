// The clock the app displays referees a generated plan's session length (app-shell-adapter
// generateNextProgramForShell, cycle-runtime-adapter generateNextCycleForShell).
import assert from 'node:assert/strict';
import { EXERCISES, GYM_PRESETS, estimateMinutes } from '../modules/engine-api.js';
import { generateNextProgramForShell, setShellSessionClock } from '../modules/next-engine/app-shell-adapter.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { shellSessionOvershoot } from '../modules/next-engine/shell-session-clock.js';
import { shellPlanQuality, shellPlanQualityKept } from '../modules/next-engine/volume-repair.js';

const LIMIT = { s20: 20, s40: 40, s60: 60, s90: 90 };
const KITS = { full: GYM_PRESETS[0].equipment, dumbbell: ['dumbbell', 'bench', 'pullup'], barbell: ['barbell', 'bench', 'ezbar', 'pullup'] };
const SPLIT = { 2: 'full_body', 3: 'full_body', 4: 'upper_lower' };
const configs = [];
for (const session of ['s40', 's60']) for (const [kit, equipment] of Object.entries(KITS)) for (const goal of ['hypertrophy', 'both'])
    for (const experience of ['beginner', 'intermediate']) for (const days of [2, 3, 4])
        configs.push({ name: `${session} ${kit} ${goal} ${experience} ${days}d`, unit: 'kg', goal, experience, days, weeks: 4, session, equipment, split: SPLIT[days] });
const build = config => { try { return generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 7, makeId: () => 'b1012' }).program; } catch { return null; } };
// The expectation is read from the shell's own estimateMinutes, the function the screens call, not from the referee.
const shown = program => program.days.flatMap(day => Array.from({ length: program.weeks }, (_, i) => estimateMinutes(program, day, i + 1)));
const overCount = (program, limit) => shown(program).filter(minutes => minutes > limit).length;

// The shell registered its clock when the engine API loaded. Keep it so the control can switch it off and on.
let registered = null;
{ const probe = build(configs[0]); assert.ok(probe, 'fixture builds');
  assert.ok(shellSessionOvershoot(probe, 40), '0 the shell clock is registered once the engine API is loaded'); }

// 1. Control: without the referee the same requests show days over their limit (the defect this gate exists for).
const clockFor = (program, day, week) => estimateMinutes(program, day, week);
setShellSessionClock(null);
const unrefereed = configs.map(build);
setShellSessionClock(clockFor);
let overBefore = 0, plansBefore = 0;
unrefereed.forEach((program, i) => { if (!program) return; const n = overCount(program, LIMIT[configs[i].session]); overBefore += n; if (n) plansBefore++; });
assert.ok(plansBefore >= 5, `1 fixture armed: ${plansBefore} unrefereed plans show a day over the limit (need 5+)`);
console.log(`PASS 1 control: without the referee ${plansBefore} of ${configs.length} plans show ${overBefore} day-weeks over their limit.`);

// 2. With the referee every plan that builds fits on the displayed clock, in every work week. The selected
//    maximum is the one hard promise of a session length: a request that only builds over it is refused with
//    TIME_LIMIT and the time-limit recovery, never returned over. A plan that already fit is never refused.
const refusal = config => { try { generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 7, makeId: () => 'b1012' }); return null; } catch (error) { return error; } };
const refereed = configs.map(build);
let overAfter = 0;
const refused = [];
refereed.forEach((program, i) => {
    if (program) { overAfter += overCount(program, LIMIT[configs[i].session]); return; }
    if (!unrefereed[i]) return; // refused either way: not the referee's business
    assert.ok(overCount(unrefereed[i], LIMIT[configs[i].session]) > 0, configs[i].name + ': a plan that fit without the referee is never refused by it');
    const error = refusal(configs[i]);
    assert.deepEqual(error?.recovery?.blockingCodes, ['TIME_LIMIT'], configs[i].name + ': refused for its time limit');
    assert.ok(error.recovery.suggestions.some(x => x.code === 'increase_time'), configs[i].name + ': the refusal says what to change');
    refused.push(configs[i].name);
});
assert.equal(overAfter, 0, `2 refereed plans show ${overAfter} day-weeks over their limit`);
// Pinned as a ceiling that may fall and must not rise: requests with nothing left to give under their limit.
const REFUSAL_CEILING = Number(process.env.B1012_CEILING ?? 6);
assert.ok(refused.length <= REFUSAL_CEILING, `2 ${refused.length} requests refused for their time limit (ceiling ${REFUSAL_CEILING}): ${refused.join('; ')}`);
assert.ok(refused.length * 2 <= plansBefore, '2 most over-limit plans are fitted, not refused');
console.log(`PASS 2 refereed: 0 of ${refereed.reduce((n, p) => n + (p ? shown(p).length : 0), 0)} day-weeks over the limit (was ${overBefore}); refused for time: ${refused.join('; ') || 'none'}.`);

// 3. A plan that already fit is returned untouched: same cells, same budgets.
let untouched = 0;
// (The session fill reads the same displayed clock, so a plan it changed differs between the control, built with
// no clock, and the refereed build for a reason that is not the referee: those are left to the fill's own gate.)
// The region floors (withRegionFloors) read it too and need it to be kept.
const filled = program => { const fill = program.nextEngine.sessionFill; return !!(fill?.sets?.length || fill?.added?.length || program.nextEngine.regionFloor); };
unrefereed.forEach((program, i) => { if (!program || overCount(program, LIMIT[configs[i].session]) || filled(program) || filled(refereed[i])) return;
    assert.deepEqual(refereed[i].nextWeekPrescriptions, program.nextWeekPrescriptions, configs[i].name + ': a fitting plan is not rebuilt');
    assert.deepEqual(refereed[i].days.map(d => d.exercises), program.days.map(d => d.exercises));
    untouched++; });
assert.ok(untouched >= 5, '3 fixture armed: plans that needed no refit');
console.log(`PASS 3 ${untouched} plans that already fit are byte-for-byte what the engine first built.`);

// 4. A refit never gives up coverage the first plan had, and its tightened budgets are saved and legal.
let refits = 0;
unrefereed.forEach((program, i) => { if (!program || !refereed[i] || !overCount(program, LIMIT[configs[i].session])) return;
    const after = refereed[i], limit = LIMIT[configs[i].session];
    assert.ok(shellPlanQualityKept(shellPlanQuality(program, EXERCISES), shellPlanQuality(after, EXERCISES)), configs[i].name + ': refit kept region minimums and direct work');
    const budgets = after.nextEngine.request.schedule.days.map(day => day.maxMinutes);
    // A plan comes under its limit by a tightened budget (saved for later blocks), a moved lift, or a held ramp week: each is recorded.
    if (overCount(after, limit) < overCount(program, limit)) assert.ok(budgets.some(m => m < limit) || after.nextEngine.displayMoves?.length || after.nextEngine.displayHeld?.length, configs[i].name + ': how the plan was fitted is recorded');
    assert.ok(budgets.every(m => m >= 12 && m <= limit), configs[i].name + ': budgets stay between 12 and the limit');
    assert.deepEqual(after.nextEngine.baseRequest.schedule, after.nextEngine.request.schedule);
    assert.equal(after.config.session, configs[i].session, 'the lifter\'s own choice is not rewritten');
    assert.equal(after.nextEngine.program.audit.result, 'pass');
    refits++; });
console.log(`PASS 4 ${refits} refits kept coverage, saved a legal budget and left the lifter's time choice alone.`);

// 5. The quality comparison itself fires: a plan that loses a muscle's second day, or drops under a minimum, is refused.
{ const q = unrefereed.filter(Boolean).map(program => shellPlanQuality(program, EXERCISES)).find(x => x.directDays.quads >= 2 && ['calves', 'core'].some(r => x.directDays[r] >= 1));
  assert.ok(q, '5 fixture armed: a plan with two quad days and small-region direct work');
  const region = q.protectedRegions.find(r => q.directDays[r] >= 1 && !q.under.includes(r)), optional = ['calves', 'core'].find(r => !q.protectedRegions.includes(r) && q.directDays[r] >= 1);
  assert.ok(region && q.directDays.quads >= 2, '5 fixture armed: direct work, and quads on two days');
  assert.equal(shellPlanQualityKept(q, { ...q, directDays: { ...q.directDays, [region]: 0 } }), false, 'losing a large region\'s only direct work is refused');
  const novice = { ...q, novice: true }, veteran = { ...q, novice: false }, oneQuadDay = { directDays: { ...q.directDays, quads: 1 } };
  assert.equal(shellPlanQualityKept(novice, { ...novice, ...oneQuadDay }), false, 'a new lifter keeps the second quad day');
  assert.equal(shellPlanQualityKept(veteran, { ...veteran, ...oneQuadDay }), true, 'past novice, the time limit outranks a second day');
  assert.equal(shellPlanQualityKept(q, { ...q, under: [...q.under, region] }), false, 'a large region newly under its minimum is refused');
  assert.ok(optional, '5 fixture armed: an unfocused small region with direct work');
  assert.equal(shellPlanQualityKept(q, { ...q, directDays: { ...q.directDays, [optional]: 0 }, under: [...q.under, optional] }), true, 'an unfocused small region may give way to the time limit');
  assert.equal(shellPlanQualityKept(q, q), true);
  console.log('PASS 5 the coverage comparison refuses lost direct work, a new under-minimum region and a new lifter\'s lost second leg day.'); }

// 6. Cycles use the same referee: every block of a 40-minute cycle fits on the displayed clock.
{ let blocks = 0, over = 0, overControl = 0;
  const cycleConfigs = [];
  for (const [kit, equipment] of Object.entries(KITS)) for (const goal of ['hypertrophy', 'both']) for (const days of [3, 4])
      cycleConfigs.push({ name: 'Cycle', unit: 'kg', goal, experience: 'intermediate', days, session: 's40', equipment });
  const run = () => cycleConfigs.flatMap(config => { for (const templateId of ['powerbuilding', 'foundation']) { try {
      return generateNextCycleForShell({ config, templateId, legacyExercises: EXERCISES, seed: 7, makeId: (() => { let n = 0; return () => 'b1012-c' + n++; })() }).blocks; } catch { /* next template */ } } return []; });
  setShellSessionClock(null); for (const block of run()) overControl += overCount(block, 40);
  setShellSessionClock(clockFor);
  for (const block of run()) { blocks++; over += overCount(block, 40); }
  assert.ok(blocks >= 10, '6 fixture armed: cycle blocks were built');
  // A generated cycle that cannot fit must refuse the requested capacity; over-limit blocks are not a valid result.
  assert.ok(overControl >= 10, '6 fixture armed: unrefereed cycles show days over the limit');
  assert.equal(over, 0, `6 generated cycle blocks must not exceed the selected 40-minute maximum: ${over} over-limit day-weeks (control ${overControl})`);
  console.log(`PASS 6 cycles: ${over} day-weeks over across ${blocks} blocks (control without the referee: ${overControl}).`); }
console.log('PASS B1012 displayed-time referee.');
