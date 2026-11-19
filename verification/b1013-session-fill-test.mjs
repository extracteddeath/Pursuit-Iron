// Spare session time and whole movement patterns in a new plan (volume-repair fillSessionsTowardBand,
// extendSessionsTowardBand, ensureMovementPatterns; called from finalizeGeneratedShellVolume).
// Every expectation is measured against the same request built with `sessionFill: false` (the plan as the
// realizer generated it), with limits computed here from that control and the engine's own targets.
import assert from 'node:assert/strict';
import { EXERCISES, EX_BY_ID, GYM_PRESETS, estimateMinutes, auditProgramWeek, weeklyVolume } from '../modules/engine-api.js';
import { generateNextProgramForShell, nextProgramToShellProgram } from '../modules/next-engine/app-shell-adapter.js';
import { auditShellVolume, ensureMovementPatterns } from '../modules/next-engine/volume-repair.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { createTrainingSetEvents } from '../modules/next-engine/events.js';
import { deriveMuscleLedger } from '../modules/next-engine/ledgers.js';
import { estimateSessionMinutes } from '../modules/next-engine/realizer.js';
import { auditProgram } from '../modules/next-engine/arbiter.js';
import { publicRegionContribution } from '../modules/next-engine/public-mev.js';

const BAND = { s40: [20, 40], s60: [40, 60], s90: [60, 90] };
const STRENGTH = new Set(['primary_strength', 'secondary_strength', 'strength_support']);
const KITS = { full: GYM_PRESETS[0].equipment, dumbbell: ['dumbbell', 'bench', 'pullup'] };
const SPLIT = { 2: 'full_body', 3: 'full_body', 4: 'upper_lower', 5: 'ulppl' };
const configs = [];
for (const session of ['s60', 's90']) for (const [kit, equipment] of Object.entries(KITS)) for (const goal of ['hypertrophy', 'both'])
    for (const experience of ['beginner', 'intermediate']) for (const days of [3, 4, 5])
        configs.push({ name: `${session} ${kit} ${goal} ${experience} ${days}d`, unit: 'kg', goal, experience, days, weeks: 4, session, equipment, split: SPLIT[days] });
const build = (config, sessionFill) => { try { return generateNextProgramForShell({ config, legacyExercises: EXERCISES, seed: 7, makeId: () => 'b1013', sessionFill }).program; } catch { return null; } };
const weeksOf = program => Array.from({ length: program.weeks }, (_, i) => i + 1);
const shown = (program, week) => program.days.map(day => estimateMinutes(program, day, week));
const underDays = program => shown(program, 1).filter(minutes => minutes < BAND[program.config.session][0]).length;
const cellsByLift = program => { const out = {}; program.days.forEach(day => day.exercises.forEach((id, slot) => { out[`${day.id}|${id}`] = program.nextWeekPrescriptions[`${day.id}:${slot}`]; })); return out; };

const controls = configs.map(config => build(config, false)), filled = configs.map(config => build(config));

// 0. Armed: the control really has short days, and the fill really ran.
const underBefore = controls.reduce((n, p) => n + (p ? underDays(p) : 0), 0);
const touched = filled.filter(p => p?.nextEngine.sessionFill?.sets.length || p?.nextEngine.sessionFill?.added.length).length;
assert.ok(underBefore >= 30, `0 fixture armed: ${underBefore} control days under their band (need 30+)`);
assert.ok(touched >= 12, `0 fixture armed: the fill changed ${touched} plans (need 12+)`);
assert.ok(filled.some(p => p?.nextEngine.sessionFill?.added.length) && filled.some(p => p?.nextEngine.sessionFill?.sets.length), '0 both steps ran: an added lift and added sets');

// 1. Fewer short days, never more, and a refusal is never created or cured by the fill.
let underAfter = 0;
filled.forEach((program, i) => {
    assert.equal(!!program, !!controls[i], configs[i].name + ' builds exactly when the control does');
    if (!program) return;
    const before = underDays(controls[i]), after = underDays(program); underAfter += after;
    assert.ok(after <= before, `${configs[i].name}: ${after} short days, control ${before}`);
    shown(program, 1).forEach((minutes, di) => assert.ok(minutes >= shown(controls[i], 1)[di] - 1, configs[i].name + ': no day made shorter'));
});
assert.ok(underAfter * 3 <= underBefore * 2, `1 short days ${underBefore} -> ${underAfter}: at least a third closed`);
console.log(`PASS 1 days under their band in week one: ${underBefore} -> ${underAfter} across ${configs.length} requests.`);

// 2. The limits, from the control and the engine's targets, on every plan the fill changed.
let checkedRegions = 0, checkedLifts = 0;
filled.forEach((program, i) => {
    const fill = program?.nextEngine.sessionFill; if (!fill || !(fill.sets.length || fill.added.length)) return;
    const name = configs[i].name, control = controls[i], [, max] = BAND[program.config.session];
    const after = auditShellVolume(program, EXERCISES), before = auditShellVolume(control, EXERCISES);
    assert.deepEqual(after.issues.length <= before.issues.length, true, name + ': no new dose issue');
    for (const t of after.targets) after.weeks.forEach((week, wi) => {
        const now = week.regions[t.region], was = before.weeks[wi].regions[t.region];
        assert.ok(now <= Math.max(t.mrv, was) + 1e-6, `${name}: ${t.region} week ${wi + 1} ${now} over its ceiling ${t.mrv}`);
        if (wi === 0 && !fill.added.some(a => a.reason === 'movement_pattern')) {
            assert.ok(now <= Math.max(t.mrv / 1.2, was) + 1e-6, `${name}: ${t.region} starts over its modeled upper dose`);
            assert.ok(now <= Math.max(was * 4 / 3, was + 2) + 1e-6, `${name}: ${t.region} grew more than a third (${was} -> ${now})`);
        }
        checkedRegions++;
    });
    const mine = cellsByLift(program), theirs = cellsByLift(control);
    for (const [lift, weeks] of Object.entries(mine)) for (const week of weeksOf(program)) {
        const cell = weeks[week], was = theirs[lift]?.[week];
        assert.ok(cell.sets <= Math.max(5, was?.sets ?? 0), `${name}: ${lift} has ${cell.sets} sets`);
        // (With a lift added, the dose reconcile reruns on the new week and may trim a set elsewhere to hold a ceiling.)
        if (was && !fill.added.length) assert.ok(cell.sets >= was.sets, `${name}: ${lift} lost a set to the fill`);
        if (STRENGTH.has(cell.role)) assert.deepEqual(cell, was, `${name}: strength work ${lift} is untouched`);
        if (week === 1 && was && cell.sets > was.sets) assert.ok(cell.sets <= 4, `${name}: ${lift} filled past four first-week sets`);
        if (was) assert.deepEqual({ ...cell, sets: 0 }, { ...was, sets: 0 }, `${name}: only set counts change on ${lift}`);
        checkedLifts++;
    }
    for (const week of weeksOf(program)) shown(program, week).forEach((minutes, di) =>
        assert.ok(minutes <= max, `${name}: generated filled day ${di + 1} week ${week} shows ${minutes} minutes, exceeding the user's selected ${max}-minute maximum (control: ${shown(control, week)[di]})`));
    for (const added of fill.added) assert.ok(program.days.find(d => d.id === added.dayId).exercises.some(id => EX_BY_ID[id].name === added.exercise), name + ': the recorded lift is in the plan');
    const recorded = new Set(fill.added.map(a => a.exercise));
    for (const lift of Object.keys(mine)) if (!theirs[lift]) assert.ok(recorded.has(EX_BY_ID[lift.split('|')[1]].name), `${name}: ${lift} is new and not recorded`);
    const replaced = new Set(fill.added.map(a => a.replaced).filter(Boolean));
    for (const lift of Object.keys(theirs)) assert.ok(mine[lift] || replaced.has(EX_BY_ID[lift.split('|')[1]].name), `${name}: ${lift} was dropped`);
});
console.log(`PASS 2 limits hold: ${checkedRegions} region-weeks under their ceilings and growth bound, ${checkedLifts} lift-weeks never past five by the fill, strength work identical.`);

// 3. Plans the fill must leave alone are the control, cell for cell.
const alone = [
    { name: 'minimalist', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', days: 4, weeks: 4, session: 's90', equipment: KITS.full, split: 'upper_lower', volumeApproach: 'minimalist' },
    { name: '5/3/1', unit: 'kg', goal: 'strength', experience: 'intermediate', days: 4, weeks: 4, session: 's90', equipment: KITS.full, split: 'five_three_one', percentScheme: '531' },
    { name: '20 minutes', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', days: 4, weeks: 4, session: 's20', equipment: KITS.full, split: 'upper_lower' }
];
let left = 0;
for (const config of alone) {
    const a = build(config), b = build(config, false);
    assert.equal(!!a, !!b, config.name);
    if (!a) continue;
    assert.deepEqual(a.nextWeekPrescriptions, b.nextWeekPrescriptions, config.name + ': left as generated');
    assert.deepEqual(a.days.map(d => d.exercises), b.days.map(d => d.exercises), config.name + ': roster left as generated');
    left++;
}
assert.ok(left >= 2, '3 fixture armed');
console.log(`PASS 3 ${left} minimalist, named-strength and no-floor plans are the plan as generated.`);

// 4. A week with no pulling: the control reproduces it, the plan has it, and the app's own week audit agrees.
const noPull = { name: 'two-day strength', unit: 'kg', goal: 'strength', experience: 'beginner', days: 2, weeks: 4, session: 's40', equipment: KITS.full, split: 'upper_lower' };
const pulling = program => { const r = auditShellVolume(program, EXERCISES).weeks[0].regions; return r.lats + r.upper_back + r.biceps; };
const absent = program => auditProgramWeek(program, 1, {}).filter(f => f.rule === 'pattern-absent').length;
const bare = build(noPull, false), fixed = build(noPull);
assert.ok(bare && fixed, '4 fixture builds');
assert.ok(pulling(bare) < .5 && absent(bare) >= 1, `4 control reproduces the defect: ${pulling(bare)} pulling sets, ${absent(bare)} pattern-absent finding`);
assert.ok(pulling(fixed) >= 2 && absent(fixed) === 0, `4 the plan pulls: ${pulling(fixed)} sets, ${absent(fixed)} pattern-absent`);
assert.equal(fixed.nextEngine.sessionFill.added[0].reason, 'movement_pattern');
shown(fixed, 1).forEach(minutes => assert.ok(minutes <= 40, '4 the added lift fits the session'));
// The same request on a barbell kit has no time for another lift beside four sets of bench: the pull takes the
// place of the one accessory, and that is recorded.
const barbell = build({ ...noPull, name: 'two-day strength, barbell', experience: 'advanced', equipment: ['barbell', 'bench', 'ezbar', 'pullup'] });
assert.ok(barbell && pulling(barbell) >= 2 && absent(barbell) === 0, '4 the barbell plan pulls too');
// With back dosed above chest the generator now programs that pull itself, so the case where a full day must give
// up an accessory for it is built directly: the same plan without its pulls, its upper day filled to the limit.
{ const defs = createExerciseMap([]), engine = JSON.parse(JSON.stringify(barbell.nextEngine.program)), request = JSON.parse(JSON.stringify(barbell.nextEngine.request));
  const pulls = id => ['lats', 'upper_back'].some(region => publicRegionContribution(defs.get(id), region) > 0);
  // Each pull becomes a skull crusher (the accessory the old plan had there), so the week has no pulling at all.
  for (const session of engine.sessions) session.exercises = session.exercises.map(e => !pulls(e.exerciseId) ? e
      : { ...e, exerciseId: 'skullcrusher', name: 'Skull Crusher', role: 'hypertrophy_isolation' });
  const upper = engine.sessions.find(session => session.exercises.some(e => !/strength/.test(e.role)));
  assert.ok(upper, '4 fixture has an accessory to give up');
  for (const e of upper.exercises) if (!/strength/.test(e.role)) while (estimateSessionMinutes(upper.exercises) < upper.maxMinutes - 2 && e.sets < 6) e.sets++;
  engine.events = createTrainingSetEvents(engine.sessions, request.customExercises);
  engine.muscleLedger = deriveMuscleLedger(engine.events);
  engine.sessions.forEach(session => { session.estimatedMinutes = estimateSessionMinutes(session.exercises); });
  engine.audit = auditProgram(engine, request);
  const shell = nextProgramToShellProgram(engine, barbell.config, EXERCISES, () => barbell.id);
  shell.nextEngine = { ...barbell.nextEngine, ...shell.nextEngine, program: engine, request };
  assert.ok(pulling(shell) < .5, '4 the direct fixture has no pulling: ' + pulling(shell));
  const result = ensureMovementPatterns(shell, EXERCISES);
  assert.ok(pulling(result.program) >= 2, '4 the full day pulls after the pass: ' + pulling(result.program));
  assert.ok(result.changes.some(c => c.reason === 'movement_pattern' && c.replacedExercise), '4 the lift that made room is recorded: ' + JSON.stringify(result.changes)); }
console.log(`PASS 4 two-day strength plan: pulling ${pulling(bare)} -> ${pulling(fixed)} sets (${fixed.nextEngine.sessionFill.added[0].exercise}).`);

// 5. The engine source of an extended plan still projects to the cells the lifter runs: an added lift must not
//    turn week-one effort into the block's base (which would shift RIR again on every later projection).
let projected = 0;
for (const program of [...filled, fixed]) {
    if (!program?.nextEngine.sessionFill?.added.length) continue;
    const raw = nextProgramToShellProgram(program.nextEngine.program, program.config, EXERCISES, () => program.id);
    for (const [key, weeks] of Object.entries(raw.nextWeekPrescriptions)) for (const [week, cell] of Object.entries(weeks)) {
        const live = program.nextWeekPrescriptions[key][week];
        for (const field of ['reps', 'rir', 'rest', 'role']) assert.equal(live[field], cell[field], `5 ${key} week ${week} ${field}`);
    }
    projected++;
}
assert.ok(projected >= 3, '5 fixture armed: plans with an added lift');
console.log(`PASS 5 ${projected} extended plans re-project to the same reps, effort and rest.`);
// 6. The capacity ladder's last rung. Four heavy sets at four minutes' rest filled a 40-minute full-body day and
//    the request was refused for a missing push; it now builds on the short-session strength dose, and says so.
{ const compact = build({ name: 'two-day mixed', unit: 'kg', goal: 'both', experience: 'intermediate', days: 2, weeks: 4, session: 's40', equipment: KITS.full, split: 'full_body' });
  assert.ok(compact, '6 the request builds');
  assert.equal(compact.nextEngine.request.preferences.compactStrength, true, '6 the compact dose is written on the request');
  const heavy = Object.values(compact.nextWeekPrescriptions).map(weeks => weeks[1]).filter(cell => cell.role === 'primary_strength');
  assert.ok(heavy.length >= 2 && heavy.every(cell => cell.sets === 3), '6 primary lifts take three sets: ' + heavy.map(c => c.sets).join(','));
  for (const week of weeksOf(compact)) shown(compact, week).forEach(minutes => assert.ok(minutes <= 40, '6 inside the 40-minute limit'));
  const ordinary = filled.find(p => p && p.config.goal === 'both');
  assert.ok(ordinary && ordinary.nextEngine.request.preferences.compactStrength !== true, '6 a request that builds as asked never takes the compact dose');
  console.log('PASS 6 a refused two-day mixed plan builds on three heavy sets, recorded on its request.'); }

// 7. A focus never costs the muscle it names, and a focused muscle with a day to spare gets a lift.
{ const focusCase = (config, part) => { const plain = build(config), focusedPlan = build({ ...config, focus: { [part]: 1 } });
      assert.ok(plain && focusedPlan, `7 ${config.name} builds with and without the focus`);
      return { plain, focusedPlan, was: weeklyVolume(plain, 1)[part] || 0, now: weeklyVolume(focusedPlan, 1)[part] || 0 }; };
  const mixed3 = { name: 'mixed 3d', unit: 'kg', goal: 'both', experience: 'intermediate', days: 3, weeks: 4, session: 's40', equipment: KITS.full, split: 'full_body' };
  let raised = 0;
  for (const part of ['shoulders', 'chest', 'lats', 'quads', 'hamstrings', 'glutes', 'biceps', 'triceps']) {
      const r = focusCase(mixed3, part);
      assert.ok(r.now >= r.was - 1e-6, `7 ${part}: focus ${r.now} is not below the plain plan's ${r.was}`);
      if (r.now > r.was + .01) raised++;
      for (const week of weeksOf(r.focusedPlan)) shown(r.focusedPlan, week).forEach(minutes => assert.ok(minutes <= 40, `7 ${part}: focus stays inside the limit`));
  }
  assert.ok(raised >= 5, `7 ${raised} of 8 focused muscles got more`);
  // The fallback: a rebuild around the focus that lands below the plain plan starts from the plain roster instead.
  // (Its first reproducer, a 3-day shoulders focus, stopped needing it when the week-set allocator stopped moving a
  // set off a 2-set lift onto a 1-set one; this request still does.)
  const fallback = focusCase({ ...mixed3, name: 'mixed 4d', days: 4 }, 'quads');
  assert.equal(fallback.focusedPlan.nextEngine.focusDose.start, 'unfocused_roster', '7 the 4-day quads build starts from the plain roster (the focused roster gave quads less)');
  assert.ok(fallback.now >= fallback.was - 1e-6, `7 fallback quads ${fallback.now} is not below the plain plan's ${fallback.was}`);
  const lats = focusCase({ name: 'ppl 3d', unit: 'kg', goal: 'both', experience: 'beginner', days: 3, weeks: 4, session: 's60', equipment: KITS.full, split: 'ppl' }, 'lats');
  assert.ok(lats.now >= lats.was + 1.5, `7 lats ${lats.was} -> ${lats.now}`);
  assert.ok(lats.focusedPlan.nextEngine.sessionFill.added.some(a => a.reason === 'focus'), '7 the lats focus added a lift (one pulldown at its cap could take no more sets)');
  console.log(`PASS 7 focus: ${raised} of 8 muscles raised on a tight 40-minute week, none lowered; lats ${lats.was} -> ${lats.now} with ${lats.focusedPlan.nextEngine.sessionFill.added.find(a => a.reason === 'focus').exercise}.`); }

// 8. A ramp week that would show past the limit gives its added sets back, and no lift loses its last set.
{ const held = build({ name: 'five short days', unit: 'kg', goal: 'hypertrophy', experience: 'beginner', days: 5, weeks: 4, session: 's20', equipment: KITS.full, split: 'ulppl' });
  assert.ok(held?.nextEngine.displayHeld?.length, '8 fixture armed: the plan builds and records held weeks');
  for (const week of weeksOf(held)) shown(held, week).forEach(minutes => assert.ok(minutes <= 20, `8 week ${week} shows ${minutes} on a 20-minute limit`));
  for (const { key, week } of held.nextEngine.displayHeld) { const cell = held.nextWeekPrescriptions[key][week];
      assert.ok(cell.sets >= 1 && !STRENGTH.has(cell.role), '8 a held lift keeps a set and is not strength work');
  }
  console.log(`PASS 8 ${held.nextEngine.displayHeld.length} ramp sets given back; every week inside 20 minutes.`); }
console.log('PASS B1013 session fill.');
