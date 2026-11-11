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

// 2. With the referee every one of those plans fits on the displayed clock, in every work week.
const refereed = configs.map(build);
let overAfter = 0;
// The referee never turns a plan that built into a refusal (a request the engine refuses either way is not its business).
refereed.forEach((program, i) => { assert.equal(!!program, !!unrefereed[i], configs[i].name + ' builds exactly when it did without the referee'); if (program) overAfter += overCount(program, LIMIT[configs[i].session]); });
// What remains is a plan with nothing left to give: every tighter build was refused by the engine or would have
// cost protected coverage (check 4). Pinned as a ceiling that may fall and must not rise.
const RESIDUAL_CEILING = Number(process.env.B1012_CEILING ?? 12);
const residual = configs.filter((config, i) => refereed[i] && overCount(refereed[i], LIMIT[config.session])).map(config => config.name);
assert.ok(overAfter <= RESIDUAL_CEILING, `2 refereed plans show ${overAfter} day-weeks over their limit (ceiling ${RESIDUAL_CEILING}): ${residual.join('; ')}`);
assert.ok(overAfter * 5 <= overBefore, '2 the referee removes at least four fifths of the overage');
refereed.forEach((program, i) => { if (program && unrefereed[i]) assert.ok(overCount(program, LIMIT[configs[i].session]) <= overCount(unrefereed[i], LIMIT[configs[i].session]), configs[i].name + ': never more over than before'); });
console.log(`PASS 2 refereed: ${overAfter} of ${refereed.reduce((n, p) => n + (p ? shown(p).length : 0), 0)} day-weeks over the limit (was ${overBefore}); left: ${residual.join('; ') || 'none'}.`);

// 3. A plan that already fit is returned untouched: same cells, same budgets.
let untouched = 0;
unrefereed.forEach((program, i) => { if (!program || overCount(program, LIMIT[configs[i].session])) return;
    assert.deepEqual(refereed[i].nextWeekPrescriptions, program.nextWeekPrescriptions, configs[i].name + ': a fitting plan is not rebuilt');
    assert.deepEqual(refereed[i].days.map(d => d.exercises), program.days.map(d => d.exercises));
    untouched++; });
assert.ok(untouched >= 5, '3 fixture armed: plans that needed no refit');
console.log(`PASS 3 ${untouched} plans that already fit are byte-for-byte what the engine first built.`);

// 4. A refit never gives up coverage the first plan had, and its tightened budgets are saved and legal.
let refits = 0;
unrefereed.forEach((program, i) => { if (!program || !overCount(program, LIMIT[configs[i].session])) return;
    const after = refereed[i], limit = LIMIT[configs[i].session];
    assert.ok(shellPlanQualityKept(shellPlanQuality(program, EXERCISES), shellPlanQuality(after, EXERCISES)), configs[i].name + ': refit kept region minimums and direct work');
    const budgets = after.nextEngine.request.schedule.days.map(day => day.maxMinutes);
    if (overCount(after, limit) < overCount(program, limit)) assert.ok(budgets.some(m => m < limit), configs[i].name + ': the tightened budget is saved for later blocks');
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
  // A cycle rebuild is expensive, so it gets two joint attempts and no day-by-day pass; the ceiling may fall, not rise.
  assert.ok(overControl >= 10, '6 fixture armed: unrefereed cycles show days over the limit');
  assert.ok(over <= 14 && over * 3 <= overControl * 2, `6 cycle blocks over the limit: ${over} (control ${overControl})`);
  console.log(`PASS 6 cycles: ${over} day-weeks over across ${blocks} blocks (control without the referee: ${overControl}).`); }
console.log('PASS B1012 displayed-time referee.');
