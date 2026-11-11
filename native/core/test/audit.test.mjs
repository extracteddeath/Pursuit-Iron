// Gates for the 2026-11-10 native audit fixes. Each check names the defect it pins; each was run against the
// pre-fix tree and failed there (see the PR). Fake clock, because day-one scheduling and block ends are about dates.
const Real = Date; let off = 0;
const setNow = (ms) => { off = ms - Real.now(); };
class FakeDate extends Real { constructor(...a) { if (a.length === 0) super(Real.now() + off); else super(...a); } static now() { return Real.now() + off; } }
globalThis.Date = FakeDate;
const C = await import('../src/core.js');
const E = await import('../../../modules/engine-api.js');

let fails = 0, passes = 0;
const ok = (cond, msg) => { if (cond) passes++; else { fails++; console.log('FAIL ' + msg); } };
const DAY = 864e5, MONDAY = new Real(2026, 9, 5, 17, 30).getTime();
const GYM = ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'];
const fresh = (store = { unit: 'kg' }) => C.init({ store: JSON.stringify(store) });
const savedOf = (id) => JSON.parse(C.exportStore()).saved.find((p) => p.id === id);
function logSession(load = '40') {
  C.startSession({});
  for (const e of C.session().exercises) for (const x of e.sets) {
    if (x.warm || x.sub) continue;
    if (!C.session().exercises[e.ei].sets[x.i].weight) C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: load });
    C.act({ type: 'toggle', ei: e.ei, si: x.i });
    C.act({ type: 'rir', ei: e.ei, si: x.i, rir: 2 });
  }
  return C.finishSession();
}

// A section that throws (a missing function on an older core) fails as a check instead of hiding the rest.
const section = async (name, fn) => { try { await fn(); } catch (e) { ok(false, name + ' threw: ' + String(e?.message || e).split('\n')[0]); } };

// 1. The wizard's "full gym" is the web's Commercial gym: every kind of equipment the app knows.
await section('1. The wizard\'s "full gym" is the web\'s Commercial gym: every kind of equipment the app knows.', async () => {
{ setNow(MONDAY); fresh();
  const so = C.setupOptions();
  const all = so.equipment.map((e) => e.id).filter((x) => x !== 'bodyweight' && x !== 'no-rack');
  const full = new Set(so.presets?.[0]?.equipment || []);
  ok(so.presets?.length >= 4, 'setupOptions carries the gym presets (' + (so.presets?.length || 0) + ')');
  ok(all.every((x) => full.has(x)), 'the first preset covers every equipment type (missing: ' + all.filter((x) => !full.has(x)).join(', ') + ')');
  ok(['legpress', 'legcurl', 'legext', 'pecdeck', 'inclinebench'].every((x) => full.has(x)), 'leg press, leg curl, leg extension, pec deck and incline bench are in a full gym'); }

});

// 2. Day one is a training day, whichever weekday the program is built on, and the week keeps its spacing.
await section('2. Day one is a training day, whichever weekday the program is built on, and the week keeps its spacing.', async () => {
{ const gaps = (sched) => { const wds = Object.keys(sched).map(Number).sort((a, b) => a - b); return wds.map((w, i) => ((wds[(i + 1) % wds.length] - w + 7) % 7) || 7).sort().join(','); };
  let base = null;
  for (let d = 0; d < 7; d++) {
    setNow(MONDAY + d * DAY); fresh();
    const m = C.createProgram({ config: { goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: GYM }, seed: 3 });
    const h = C.home(), p = savedOf(m.programId), wd = new Date().getDay();
    ok(!h.restDay && h.next && h.next.dayId === p.days[0].id, `built on ${new Date().toDateString().slice(0, 3)}: Today offers day 1, not a rest day`);
    if (base == null) base = gaps(p.schedule);
    ok(gaps(p.schedule) === base, `the rotated week keeps its spacing (${gaps(p.schedule)} vs ${base})`);
    ok(JSON.stringify(p.schedule) === JSON.stringify(p.scheduleBase), 'scheduleBase rotates with it, so the schedule does not read as hand-edited');
    const sc = C.schedule({ programId: m.programId });
    const sug = Object.fromEntries((sc.suggested || []).filter((x) => x.dayId).map((x) => [x.weekday, x.dayId]));
    ok(JSON.stringify(sug) === JSON.stringify(Object.fromEntries(Object.entries(p.schedule).map(([k, v]) => [Number(k), v]))),
      `the suggested week is the schedule, weekday for weekday (weekPlan read Monday-first) on ${['Sun','Mon','Tue','Wed','Thu','Fri','Sat'][wd]}`);
  } }

});

// 3. A block that has run its last week says so on Today, for a standalone program and for a cycle block.
await section('3. A block that has run its last week says so on Today, for a standalone program and for a cycle block.', async () => {
{ setNow(MONDAY); fresh();
  const m = C.createProgram({ config: { goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, weeks: 4, session: 's60', equipment: GYM }, seed: 3 });
  let n = 0;
  for (let d = 0; d < 40 && n < 16; d++) { setNow(MONDAY + d * DAY); if (C.home().restDay) continue; ok(!C.home().blockEnd, `no block-end card mid-block (session ${n + 1})`); logSession(); n++; }
  setNow(MONDAY + 40 * DAY);
  const h = C.home(), b = h.blockEnd;
  ok(h.next.weekIndex === 1, 'the cursor still wraps to week 1 after the last week');
  ok(b && b.programId === m.programId && b.sessions === 16 && b.planned === 16 && b.weeks === 4, 'Today offers the block review after week 4 (' + JSON.stringify(b) + ')');
  ok(C.reviewBlock({ id: b.programId, index: b.blockIndex })?.ok !== false, 'the card opens a review that has data');
  C.dismissBlockEnd({ programId: m.programId });
  ok(!C.home().blockEnd, '"Run it again" hides the card');
  logSession();
  ok(!C.home().blockEnd, 'starting the block again keeps it hidden'); }
{ setNow(MONDAY); fresh();
  const tpl = C.cyclesView().templates[0].id;
  C.createCycle({ config: { name: 'Cy', goal: 'hypertrophy', experience: 'intermediate', days: 3, session: 's60', equipment: GYM }, templateId: tpl });
  const h0 = C.home(), weeks = h0.next.weeksTotal; let n = 0;
  for (let d = 0; d < 90 && n < 3 * weeks; d++) { setNow(MONDAY + d * DAY); if (C.home().restDay) continue; logSession(); n++; }
  setNow(MONDAY + 91 * DAY);
  const b = C.home().blockEnd;
  ok(b && b.cycleId && b.blockIndex === 0 && b.nextBlockInCycle, 'a finished cycle block offers its review and the next block (' + JSON.stringify(b) + ')'); }

});

// 4. The block review counts and judges volume the way the Plan tab does.
await section('4. The block review counts and judges volume the way the Plan tab does.', async () => {
{ setNow(MONDAY); fresh();
  const m = C.createProgram({ config: { goal: 'hypertrophy', experience: 'intermediate', split: 'torso_limbs', days: 4, weeks: 4, session: 's60', equipment: E.GYM_PRESETS[0].equipment }, seed: 3 });
  let n = 0; for (let d = 0; d < 40 && n < 16; d++) { setNow(MONDAY + d * DAY); if (C.home().restDay) continue; logSession(); n++; }
  const plan = C.weeklyVolumeView({ programId: m.programId, weekIndex: 2 });
  const rv = C.reviewBlock({ id: m.programId });
  const byPart = Object.fromEntries(plan.rows.map((r) => [r.part, r]));
  for (const mu of rv.muscles || []) {
    const pr = byPart[mu.part]; if (!pr) { ok(false, `review judges ${mu.part}, which the Plan does not show`); continue; }
    ok(mu.mev === pr.mev && mu.mrv === pr.mrv, `${mu.part}: review landmarks ${mu.mev}–${mu.mrv} match the Plan's ${pr.mev}–${pr.mrv}`);
    ok(Math.abs(mu.avgSets - pr.sets) <= Math.max(1.5, pr.sets * 0.3), `${mu.part}: review counts ${mu.avgSets} sets/wk, the Plan ${pr.sets}`);
  }
  const under = (rv.muscles || []).filter((x) => x.zone === 'under MEV').map((x) => x.part);
  ok(plan.issues.length || under.length === 0, 'a block the Plan calls all-in-range is not called under-dosed by its review (' + under.join(', ') + ')'); }

});

// 5. Volume / Recovery / Intensity loads each main lift by its day in the week.
await section('5. Volume / Recovery / Intensity loads each main lift by its day in the week.', async () => {
{ setNow(MONDAY); fresh();
  const r = C.createFromTemplate({ id: 'texas' }); const p = savedOf(r.programId);
  const t1 = p.days.map((d) => ({ d, s: d.primaryIndex, id: d.exercises[d.primaryIndex] }));
  const twice = t1.filter((x) => t1.filter((y) => y.id === x.id).length >= 2);
  ok(twice.length >= 2, 'the template trains a main lift on two days (' + twice.map((x) => x.id).join(',') + ')');
  [...new Set(t1.map((x) => x.id))].forEach((id) => C.setTrainingMax({ programId: r.programId, exId: id, value: 100 }));
  const tm = savedOf(r.programId);
  // Soft-bound so an older engine fails on what it prescribes, not on a missing export.
  const dayType = E.percentDayType || ((pp, d) => d.type);
  const plans = twice.map((x) => ({ role: dayType(tm, x.d, x.s), plan: E.percentagePlanFor(tm, x.d, E.EX_BY_ID[x.id], x.s, 1, 'kg', []) }));
  ok(plans[0].role === 'tx_volume' && plans[plans.length - 1].role === 'tx_intensity', 'first day is volume, last is intensity (' + plans.map((x) => x.role).join(' → ') + ')');
  const top = (pl) => Math.max(...(pl?.sets || []).map((s) => s.weight));
  ok(plans.every((x) => x.plan), 'each day has a percentage plan once a max is set');
  ok(top(plans[plans.length - 1].plan) > top(plans[0].plan), `intensity day goes heavier than volume day (${top(plans[plans.length - 1].plan)} vs ${top(plans[0].plan)} kg)`);
  const syn = { ...tm, days: [0, 1, 2].map((i) => ({ ...tm.days[0], id: 'd' + i })) };
  ok(['d0', 'd1', 'd2'].map((id, i) => dayType(syn, syn.days[i], syn.days[i].primaryIndex)).join() === 'tx_volume,tx_recovery,tx_intensity', 'a lift on three days gets volume, recovery, intensity');
  const padded = E.adaptPercentageSetBudget({ label: 'x', sets: [{ weight: 100, reps: '5+', amrap: true }, { weight: 90, reps: '5' }] }, 3);
  ok(padded.sets.filter((s) => s.weight === 100).length === 1, 'a padded intensity day has one top set, not two'); }

});

// 6. New lifters don't get lifts that need strength they don't have yet.
await section("6. New lifters don't get lifts that need strength they don't have yet.", async () => {
{ const gated = ['Nordic Curl', 'Sissy Squat', 'Bodyweight Reverse Nordic Curl', 'Pistol Squat', 'Ab Wheel Rollout', 'Hanging Leg Raise'];
  let built = 0, hits = [];
  for (const equipment of [GYM, ['dumbbell', 'bench', 'pullup'], ['barbell', 'bench', 'ezbar', 'pullup'], []]) for (const experience of ['none', 'beginner']) for (const days of [3, 4]) {
    setNow(MONDAY); fresh();
    const m = C.createProgram({ config: { goal: 'hypertrophy', experience, days, session: 's60', equipment } }); if (!m.ok) continue; built++;
    const p = savedOf(m.programId);
    p.days.forEach((d) => d.exercises.forEach((id) => { const nm = E.EX_BY_ID[id]?.name; if (gated.includes(nm)) hits.push(`${experience}/${equipment.length}/${days}: ${nm}`); }));
  }
  ok(built >= 12, 'new-lifter programs still build on every kit (' + built + ' of 16)');
  ok(hits.length === 0, 'no strength-prerequisite lift in a new lifter\'s program (' + hits.slice(0, 4).join('; ') + ')');
  setNow(MONDAY); fresh();
  const adv = C.createProgram({ config: { goal: 'hypertrophy', experience: 'advanced', days: 3, session: 's60', equipment: [] } });
  ok(adv.ok, 'an advanced bodyweight program still builds'); }

});

// 7. When no split fits, the wizard is told the nearest setup that builds.
await section('7. When no split fits, the wizard is told the nearest setup that builds.', async () => {
{ setNow(MONDAY); fresh();
  const cfg = { goal: 'strength', experience: 'intermediate', days: 3, session: 's20', equipment: E.GYM_PRESETS[0].equipment };
  const ids = C.rankSplitsPlan({ config: cfg }).ids;
  const res = ids.map((id) => C.rankSplit({ config: cfg, id }));
  ok(res.every((x) => x.failed) && res.every((x) => Array.isArray(x.codes) && x.codes.length), 'every Strength / 20-min split fails with the engine\'s reason attached');
  const alt = C.rankAlternatives({ config: cfg }).alternatives;
  ok(alt.length >= 1 && alt[0].kind === 'session', 'a longer session is offered (' + JSON.stringify(alt) + ')');
  const alts = { ...cfg, session: alt[0].session };
  ok(C.rankSplitsPlan({ config: alts }).ids.some((id) => !C.rankSplit({ config: alts, id }).failed), 'and that session length really builds'); }

});

// 8. Intensity buckets are named by load, not by goal.
await section('8. Intensity buckets are named by load, not by goal.', async () => {
{ setNow(MONDAY); fresh(); C.createProgram({ config: { goal: 'hypertrophy', experience: 'intermediate', days: 3, session: 's60', equipment: GYM } }); logSession();
  const lab = C.progressCharts().intensity.reps.map((x) => x.label).join(',');
  ok(lab === 'Heavy,Moderate,Light', 'intensity buckets read Heavy, Moderate, Light (' + lab + ')'); }

});

// 9. A screen's engine cost doesn't multiply with history: Today serialises the history at most twice.
await section("9. A screen's engine cost doesn't multiply with history: Today serialises the history at most twice.", async () => {
{ setNow(MONDAY); fresh();
  C.createProgram({ config: { goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: GYM }, seed: 3 });
  for (let d = 0; d < 28; d++) { setNow(MONDAY + d * DAY); if (!C.home().restDay) logSession(); }
  const st = JSON.parse(C.exportStore()), H = st.history; st.history = [];
  for (let i = 0; i < 300; i++) { const h = JSON.parse(JSON.stringify(H[i % H.length])); h.id += '_' + i; h.date = MONDAY - (300 - i) * 2 * DAY; st.history.push(h); }
  st.history.sort((a, b) => b.date - a.date); setNow(MONDAY + DAY); C.init({ store: JSON.stringify(st) }); C.home();
  await new Promise((r) => setTimeout(r, 300));
  const JS = JSON.stringify; let big = 0;
  JSON.stringify = function (v) { const s = JS.apply(this, arguments); if (s && s.length > 100000) big++; return s; };
  const t = performance.now(); C.home(); const ms = performance.now() - t;
  JSON.stringify = JS;
  ok(big <= 2, `home() serialises the whole history ${big} time(s) with 300 sessions (was 23)`);
  ok(ms < 400, `home() with 300 sessions takes ${ms.toFixed(0)} ms in node`); }

});

// 10. The self-test checks every set and is armed: a gym ceiling below the logged loads is caught.
await section('10. The self-test checks every set and is armed: a gym ceiling below the logged loads is caught.', async () => {
{ setNow(MONDAY); fresh();
  C.createProgram({ config: { goal: 'hypertrophy', experience: 'intermediate', days: 4, session: 's60', equipment: E.GYM_PRESETS[0].equipment } }); logSession();
  const clean = C.selfTest();
  ok(clean.ok && clean.setsChecked > 50 && clean.simulations === 3, `a clean store passes (${clean.setsChecked} sets, ${clean.simulations} simulations, ${clean.failureCount} problems)`);
  const st = JSON.parse(C.exportStore()), g = C.gyms();
  st.gyms = (st.gyms || g.gyms).map((x) => ({ ...x, limits: { dumbbell: 5, cable: 5, machine: 5, smith: 5, barbell: 5 } }));
  C.init({ store: JSON.stringify(st) });
  const bad = C.selfTest();
  ok(!bad.ok && bad.failures.some((f) => f.rule === 'load-over-gym-ceiling'), 'a load over the gym\'s heaviest weight is reported (' + bad.failureCount + ')'); }

});
console.log(`audit: ${passes} checks passed, ${fails} failed`);
if (fails) { console.log('AUDIT FAIL'); process.exit(1); }
console.log('AUDIT PASS');
