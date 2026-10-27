// End-to-end checks of the native core against the canonical engine.
import * as C from '../src/core.js';
import * as E from '../../../modules/engine-api.js';
let fails = 0;
const ok = (cond, msg) => { console.log((cond ? 'PASS ' : 'FAIL ') + msg); if (!cond) fails++; };

// 0. Empty start, unknown fields survive a round trip
C.init({ store: JSON.stringify({ unit: 'kg', zzFuture: { keep: [1, 2, 3] }, theme: 'ember' }) });
let h = C.home();
ok(h.empty === true && h.next === null, 'empty store reads as empty with no next session');
const rt = JSON.parse(C.exportStore());
ok(JSON.stringify(rt.zzFuture) === JSON.stringify({ keep: [1, 2, 3] }), 'unknown store fields pass through untouched');

// 1. Create a program with the real generator
const cfg = { name: 'Native test', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60',
  equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] };
const made = C.createProgram({ config: cfg, seed: 11 });
ok(made.ok && made.days === 4, 'createProgram builds a 4-day program (' + (made.error || made.name) + ')');
h = C.home();
ok(h.next && h.next.exercises.length > 0 && h.next.weekIndex === 1, 'home shows the next session at week 1 with exercises');

// 2. Start a session and log every working set
let s = C.startSession({});
ok(s.active && s.exercises.length === h.next.exercises.length, 'session opens with the same exercises as the preview');
const firstEx = s.exercises[0];
const startW = 60;
s.exercises.forEach((e) => e.sets.forEach((st) => {
  if (st.warm || st.sub) return;
  s = C.act({ type: 'edit', ei: e.ei, si: st.i, field: 'weight', value: String(startW) });
  s = C.act({ type: 'edit', ei: e.ei, si: st.i, field: 'reps', value: '8' });
}));
const r0 = C.act({ type: 'toggle', ei: 0, si: firstEx.sets.find((x) => !x.warm && !x.sub).i });
ok(r0.exercises[0].sets.some((x) => x.done), 'toggle logs a set');
ok(r0.rest && r0.rest.seconds > 0, 'logging a working set starts a rest of ' + (r0.rest && r0.rest.seconds) + 's');
const undoT = C.act({ type: 'toggle', ei: 0, si: firstEx.sets.find((x) => !x.warm && !x.sub).i });
ok(!undoT.exercises[0].sets.some((x) => x.done) && !undoT.rest, 'toggling again unlogs it and clears rest');
s = C.session();
s.exercises.forEach((e) => e.sets.forEach((st) => { if (!st.warm && !st.sub && !st.done) { s = C.act({ type: 'toggle', ei: e.ei, si: st.i }); s = C.act({ type: 'rir', ei: e.ei, si: st.i, rir: 1 }); } }));
ok(s.doneSets === s.totalSets && s.totalSets > 0, 'all ' + s.totalSets + ' working sets logged');
const editDone = C.act({ type: 'edit', ei: 0, si: s.exercises[0].sets.find((x) => x.done).i, field: 'weight', value: '999' });
ok(!editDone.exercises[0].sets.some((x) => x.weight === '999'), 'a logged set cannot be edited (App.js editSet rule)');

// 3. Finish: history, perf, live cleared
const fin = C.finishSession();
ok(fin.ok && fin.setsDone === s.totalSets, 'finish records ' + fin.setsDone + ' sets');
const st1 = JSON.parse(C.exportStore());
ok(st1.history.length === 1 && Object.keys(st1.perf).length >= 1, 'history has the session and perf is updated');
ok(C.session().active === false && C.exportLive() === '', 'live session cleared after finish');
const parsedBack = E.parseStoredData(JSON.stringify(st1));
ok(parsedBack && parsedBack.history.length === 1, 'the PWA parser reads the native store');

// 4. Next session advances the rotation
h = C.home();
ok(h.next && h.next.dayId !== made.programId && h.next.dayLabel, 'next session is a different day: ' + h.next.dayLabel);

// 5. Same day again later: progression reads the logged session
C.init({ store: JSON.stringify(st1) });
const day0 = st1.saved[0].days[0].id;
const s2 = C.startSession({ programId: st1.saved[0].id, dayId: day0, weekIndex: 1 });
const w2 = s2.exercises[0].sets.filter((x) => !x.warm && !x.sub).map((x) => Number(x.weight));
ok(w2.every((w) => w > 0), 'repeating the day prescribes loads from history: ' + w2.join('/'));
C.discardSession();

// 6. Delete + undo
const del = C.deleteHistory({ id: st1.history[0].id });
ok(del.ok && JSON.parse(C.exportStore()).history.length === 0, 'delete removes the session');
ok(Object.keys(JSON.parse(C.exportStore()).perf).length === 0, 'delete recomputes perf (perfAfterDelete): no orphan anchor');
C.undo();
ok(JSON.parse(C.exportStore()).history.length === 1, 'undo restores it');

// 7. Pain flag through the PWA injury model
C.startSession({});
let sp = C.session();
const before = sp.exercises.reduce((n, e) => n + e.sets.filter((x) => !x.warm && !x.sub).length, 0);
const AREAS = C.setupOptions().injuryAreas; const parts = new Set(sp.exercises.map((e) => e.part));
const area = AREAS.find((a) => a.parts.some((p) => parts.has(p)));
const pv = C.injuryPreview({ region: area.id });
ok(pv.label === area.label && pv.lifts.length >= 1 && pv.lifts.every((n) => typeof n === 'string' && n), 'Something hurt previews the lifts the area touches (' + pv.lifts.join(', ') + ')');
sp = C.act({ type: 'injury', region: area.id, impact: 'reduce' });
const after = sp.exercises.reduce((n, e) => n + e.sets.filter((x) => !x.warm && !x.sub).length, 0);
ok(sp.injuries.length === 1 && after < before, area.label + ': reduce impact logs the injury and trims affected work (' + before + ' → ' + after + ')');
const fin2 = C.finishSession();
const st2 = JSON.parse(C.exportStore());
ok(fin2.ok && Array.isArray(st2.injuryLog) && st2.injuryLog.length === 1, 'injury log persists with the session');

// 8. Import merge never loses a workout
C.init({ store: JSON.stringify(st2) });
const other = JSON.parse(JSON.stringify(st1)); other.history = [{ ...st1.history[0], id: 'from-pwa', date: Date.now() - 86400000 * 3 }];
const imp = C.importStore({ text: JSON.stringify(other) });
ok(imp.ok && JSON.parse(C.exportStore()).history.some((x) => x.id === 'from-pwa') && JSON.parse(C.exportStore()).history.length >= 3, 'import merges a PWA store without dropping sessions');

// 9. Views do not throw
['progress', 'plan', 'library', 'body', 'settings', 'setupOptions', 'historyList'].forEach((m) => { let v; let err = null; try { v = C[m]({}); } catch (e) { err = e; } ok(!err && v != null, m + '() returns a view'); });
ok(C.library({ q: 'bench' }).length > 0, 'library search finds bench variations');

// 8. Split recommendation: a real split id the picker offers, for every day count (App.js nextRecommendedSplit + recommendedSplit)
{ const opts = C.setupOptions(); const ids = new Set(opts.splits.map((x) => x.id));
  const recs = [2, 3, 4, 5, 6].map((d) => C.recommend({ config: { goal: 'hypertrophy', experience: 'intermediate', days: d, session: 's60', equipment: ['barbell', 'dumbbell', 'bench', 'cable', 'machine', 'pullup'] } }));
  ok(recs.every((r, k) => r && ids.has(r) && opts.splits.find((x) => x.id === r).days.includes(k + 2)), 'recommend returns a valid split for 2-6 days (' + recs.join(', ') + ')'); }
// 9. adoptLive: refuses garbage and an already-logged session, accepts a fresh one
{ const before = C.session().active;
  ok(C.adoptLive({ live: 'not json' }).ok === false, 'adoptLive refuses invalid JSON');
  ok(before || C.adoptLive({ live: JSON.stringify({ sessionId: 'zz', programId: 'missing' }) }).ok === false, 'adoptLive refuses a session whose program is missing'); }

// 10. Bodyweight and measurements use the web app's shape ({ date: ms, w } / { date: ms, v }), and old native rows are repaired
{ C.logBodyweight({ value: 81.4 }); C.logBodyweight({ value: 81.2 });
  const st = JSON.parse(C.exportStore()); const today = st.bwLog.filter((e) => new Date(e.date).toDateString() === new Date().toDateString());
  ok(today.length === 1 && today[0].w === 81.2 && typeof today[0].date === 'number', 'logBodyweight keeps one { date: ms, w } row per day');
  ok(C.body().log.some((e) => e.w === 81.2), 'body() reads the logged bodyweight back');
  C.logMeasurement({ key: 'waist', value: 82 });
  ok(C.body().measurements.waist.length === 1 && typeof JSON.parse(C.exportStore()).measurements.waist[0].date === 'number', 'measurements logged with ms dates');
  const legacy = { ...st, bwLog: [{ date: '2026-10-01', v: 80, unit: 'kg' }], measurements: { arm: [{ date: '2026-10-01', v: 38, unit: 'cm' }] } };
  C.init({ store: JSON.stringify(legacy) });
  const fixed = JSON.parse(C.exportStore());
  ok(fixed.bwLog[0].w === 80 && typeof fixed.bwLog[0].date === 'number' && typeof fixed.measurements.arm[0].date === 'number', 'old native rows are rewritten to the web shape on load');
  C.init({ store: JSON.stringify(st) }); }

// 11. Calendar, profile, tools
{ const cal = C.calendar({});
  ok(cal.workouts >= 1 && cal.days.every((d) => d.day >= 1 && d.day <= cal.daysInMonth), 'calendar groups this month\'s workouts by day (' + cal.workouts + ')');
  ok(cal.days[0].sessions[0].summary && cal.days[0].sessions[0].lifts.length > 0, 'calendar day shows a summary and lifts');
  const pr = C.profile();
  ok(pr.level && pr.level.level >= 1 && pr.achievements.length > 5, 'profile has a level and achievements (level ' + pr.level.level + ')');
  ok(pr.score && pr.score.overall >= 0 && pr.score.overall <= 100 && typeof pr.score.level === 'string', 'strength score with bodyweight set: ' + (pr.score && pr.score.overall + ' ' + pr.score.level));
  ok(C.setProfile({ birth: '1994-02-30' }).ok === false && C.setProfile({ birth: '1994-06-21', sex: 'female' }).ok, 'setProfile validates the birth date');
  ok(C.profile().sex === 'female' && C.profile().age >= 32, 'profile reads sex and age back');
  const pc = C.plateCalc({ total: 100, bar: 'barbell' });
  ok(pc.result && pc.result.barWeight === 20 && JSON.stringify(pc.result.plates) === JSON.stringify([25, 15]) && pc.result.exact, 'plate calc: 100 kg = 20 bar + 25,15 per side');
  const orm = C.oneRm({ weight: 100, reps: 5 });
  ok(orm.e1rm > 100 && orm.percents[0].load <= orm.e1rm && orm.reps[0].load <= orm.e1rm, '1RM estimate floors every load at or below the estimate (' + orm.e1rm + ')'); }

// 12. Gyms and plates
{ const g = C.gyms();
  ok(g.gyms.length >= 1 && g.equipment.length > 1 && g.presets.length >= 3, 'gyms view lists gyms, equipment by category and presets');
  const fresh = C.newGym({ preset: 'home' });
  const list = [...g.gyms.map(({ limitKeys, inventoryKeys, barDefaults, ...x }) => x), fresh];
  ok(C.saveGyms({ gyms: list, activeGymId: fresh.id }).ok && C.gyms().activeGymId === fresh.id, 'a new gym saves and becomes active');
  const gone = C.gyms().gyms.find((x) => x.id !== fresh.id);
  C.saveGyms({ gyms: C.gyms().gyms.filter((x) => x.id !== gone.id).map(({ limitKeys, inventoryKeys, barDefaults, ...x }) => x), activeGymId: fresh.id, removed: [gone.id] });
  ok(!C.gyms().gyms.some((x) => x.id === gone.id) && JSON.parse(C.exportStore()).tombs[gone.id] > 0, 'a removed gym is tombstoned');
  C.setPlates({ plates: [20, 10, 5, 2.5] });
  ok(JSON.stringify(C.plateCalc({ total: 100 }).result.plates) === JSON.stringify([20, 20]), 'plate calc follows the plates you own');
  C.setPlates({ plates: [25, 20, 15, 10, 5, 2.5, 1.25] }); C.setMinInc({ v: 1 });
  ok(C.gyms().minInc.v === 1 && C.gyms().minInc.unit === 'kg', 'smallest increment saved with its unit');
  ok(JSON.stringify(C.gyms().minIncChoices.map((c) => c[0])) === '[0,1,0.5]', 'kg microplate choices are 1 and 0.5 (web Smallest jump)');
  ok(C.plateSetup().jump === '1 kg with microplates', 'Plates & bars names the microplate jump'); C.setMinInc({ v: 0 });
  // The design's Plates & bars: pairs you own per gym; plate math and the heaviest load follow the counts.
  const ps = C.plateSetup();
  ok(ps.bar === 20 && ps.rows.find((r) => r.size === 25).n === 4 && ps.rows.find((r) => r.size === 1.25).n === 2 && ps.jump === '2.5 kg', 'Plates & bars starts from the plates you own (' + ps.max + ' kg max)');
  [25, 20, 15, 10, 5, 2.5, 1.25].forEach((p) => C.setPlatePair({ size: p, n: p === 25 ? 1 : 0 }));
  C.setPlatePair({ size: 20, n: 2 });
  ok(C.plateSetup().max === 20 + 2 * 65 && C.plateSetup().jump === '40 kg', 'heaviest load and smallest jump follow the pairs (' + C.plateSetup().max + ')');
  ok(JSON.stringify(C.plateCalc({ total: 100 }).result.plates) === JSON.stringify([20, 20]), 'plate math loads within the pairs you own (one 25, two 20s → 40 a side)');
  ok(C.plateCalc({ total: 300 }).result.achievable === 150, 'a load beyond the pairs stops at what you can build');
  ok(C.setBarbellWeight({ w: 15 }).ok && C.plateSetup().bar === 15 && C.plateCalc({ total: 55 }).result.barWeight === 15, 'the bar choice is the active gym\'s bar');
  const active = C.gyms().gyms.find((x) => x.id === C.gyms().activeGymId);
  ok(active.platePairs.kg[25] === 1 && active.plateSum.max === 15 + 130, 'pairs are kept on the gym');
  C.setBarbellWeight({ w: 20 }); [25, 20, 15, 10, 5, 2.5, 1.25].forEach((p) => C.setPlatePair({ size: p, n: p === 25 ? 4 : 2 })); }

// 13. Unit switch converts loads (App.js switchUnit) and keeps history as logged
{ const before = JSON.parse(C.exportStore());
  const id = Object.keys(before.perf)[0]; const w0 = parseFloat(before.perf[id].weight);
  const r = C.switchUnit({ to: 'lb' }); const after = JSON.parse(C.exportStore());
  ok(r.ok && after.unit === 'lb' && after.saved.every((p) => p.config.unit === 'lb'), 'switchUnit to lb relabels programs');
  ok(!(w0 > 0) || Math.abs(parseFloat(after.perf[id].weight) - Math.round(w0 * 2.2046226 * 2) / 2) < 1e-9, 'perf loads converted (' + w0 + ' kg → ' + after.perf[id].weight + ' lb)');
  ok(JSON.stringify(after.history) === JSON.stringify(before.history), 'history untouched');
  ok(Math.abs(parseFloat(after.bodyweight) - Math.round(parseFloat(before.bodyweight) * 2.2046226 * 2) / 2) < 1e-9, 'bodyweight converted');
  C.startSession({}); ok(C.switchUnit({ to: 'kg' }).ok === false, 'switching is refused with a workout open'); C.discardSession();
  ok(C.switchUnit({ to: 'kg' }).ok && JSON.parse(C.exportStore()).unit === 'kg', 'switch back to kg'); }

// 14. Training cycles (App.js buildCycle / doCompleteCycleBlock / doDeleteCycle / BlockReviewView)
{ C.init({});
  const v = C.cyclesView();
  ok(v.templates.length >= 3 && v.phases.length >= 4, 'cycle templates and custom phases listed');
  const ccfg = { name: 'Cycle test', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] };
  const made = C.createCycle({ config: ccfg, templateId: v.templates[0].id });
  ok(made.ok && made.blocks === v.templates[0].blocks.length, 'createCycle builds every block (' + (made.error || made.blocks) + ')');
  const st = JSON.parse(C.exportStore());
  ok(st.cycles.length === 1 && st.cycles[0].blockIds.every((id) => st.saved.some((p) => p.id === id)) && st.pinnedId === st.cycles[0].blockIds[0], 'cycle blocks saved and the first one is up next');
  const d = C.cycleDetail({ id: made.cycleId });
  ok(d.blocks[0].state === 'active' && d.live && d.live.blockIdx === 0 && C.home().cycle && C.home().cycle.blockIdx === 0, 'detail and Today place the lifter in block 1');
  ok(C.nextBlock({ programId: st.cycles[0].blockIds[0] }).ok === false, 'a cycle block cannot be forked with Build next block');
  for (let k = 0; k < 3; k++) { C.startSession({}); for (const e of C.session().exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: String(50 + 2.5 * k) }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); } C.finishSession(); }
  const rv = C.reviewBlock({ id: made.cycleId, index: 0 });
  ok(rv && rv.ok && typeof rv.headline === 'string' && Array.isArray(rv.lifts), 'block review summarises the logged block');
  C.startSession({}); ok(C.advanceCycle({ id: made.cycleId }).ok === false, 'advancing is refused with a workout open'); C.discardSession();
  const adv = C.advanceCycle({ id: made.cycleId });
  const d2 = C.cycleDetail({ id: made.cycleId });
  ok(adv.ok && d2.activeBlock === 1 && d2.blocks[0].state === 'done' && C.home().cycle.blockIdx === 1, 'advance moves to block 2: ' + (adv.message || adv.error));
  { const d3 = C.cycleDetail({ id: made.cycleId }); const n0 = JSON.parse(C.exportStore()).cycles[0].blockIds.length;
    ok(d3.reshapable && d3.phases.length >= 4 && d3.templates.length >= 3, 'cycle detail offers phases and templates to the editor');
    const ren = C.editCycle({ id: made.cycleId, name: 'Renamed', adapt: false });
    const sR = JSON.parse(C.exportStore()); const cR = sR.cycles[0];
    ok(ren.ok && cR.name === 'Renamed' && cR.nextEngineCycle.adaptBetweenBlocks === false && cR.blockIds.length === n0, 'rename and adapt toggle keep the blocks');
    const re = C.editCycle({ id: made.cycleId, blocks: [{ phase: 'strength_accumulation', weeks: 3 }, { phase: 'peak', weeks: 2 }, { phase: 'foundation', weeks: 2 }] });
    const sE = JSON.parse(C.exportStore()); const cE = sE.cycles[0];
    ok(re.ok && cE.blockIds.length === 2 + 3 && cE.blockIds.every((id) => sE.saved.some((p) => p.id === id)) && cE.blockMeta.slice(2).map((m) => m.phase).join() === 'strength_accumulation,peak,foundation', 'reshaping rebuilds the blocks after the current one: ' + (re.error || re.blocks));
    ok(sE.saved.filter((p) => p.cycleId === cE.id).length === cE.blockIds.length, 'no orphaned preview blocks are left behind');
    const shrink = C.editCycle({ id: made.cycleId, blocks: [{ phase: 'peak', weeks: 2 }] });
    const sS = JSON.parse(C.exportStore());
    ok(shrink.ok && sS.cycles[0].blockIds.length === 3 && sS.saved.filter((p) => p.cycleId === sS.cycles[0].id).length === 3, 'removing blocks deletes their previews');
    ok(!C.editCycle({ id: made.cycleId, blocks: [{ phase: 'peak', weeks: 99 }] }).ok && JSON.parse(C.exportStore()).cycles[0].blockIds.length === 3, 'an invalid reshape changes nothing');
    ok(C.undo().ok && JSON.parse(C.exportStore()).cycles[0].blockIds.length === 5, 'undo restores the previous shape'); }
  const del = C.deleteCycle({ id: made.cycleId });
  const st2 = JSON.parse(C.exportStore());
  ok(del.ok && st2.cycles.length === 0 && st2.saved.length > 0 && st2.saved.every((p) => !p.cycleId), 'deleting a cycle keeps its blocks as standalone programs');
  ok(C.undo().ok && JSON.parse(C.exportStore()).cycles.length === 1, 'undo restores the cycle');
  { const m2 = C.createCycle({ config: ccfg, templateId: 'powerbuilding' });
    const e2 = C.editCycle({ id: m2.cycleId, blocks: [{ phase: 'intensification', weeks: 3 }, { phase: 'peak', weeks: 2 }] });
    for (let k = 0; k < 3; k++) { C.startSession({}); for (const e of C.session().exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: String(50 + 2.5 * k) }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); } C.finishSession(); }
    const a3 = C.advanceCycle({ id: m2.cycleId }); const c3 = JSON.parse(C.exportStore()).cycles.find((c) => c.id === m2.cycleId);
    ok(e2.ok && a3.ok && ['intensification', 'recovery'].includes(c3.blockMeta[c3.activeBlock].phase), 'a reshaped cycle advances into its edited next phase: ' + (a3.message || a3.error));
    const nBefore = JSON.parse(C.exportStore()).saved.length; const lv = C.leaveCycle({ id: m2.cycleId }); const sL = JSON.parse(C.exportStore());
    ok(lv.ok && !sL.cycles.some((c) => c.id === m2.cycleId) && sL.saved.length < nBefore && sL.saved.every((p) => p.cycleId !== m2.cycleId), 'leaving a cycle keeps trained blocks and drops the untrained ones (' + lv.removed + ' removed)'); }
  const custom = C.createCycle({ config: ccfg, templateId: 'custom', blocks: [{ phase: 'hypertrophy_accumulation', weeks: 3 }, { phase: 'strength_accumulation', weeks: 2 }] });
  ok(custom.ok && custom.blocks === 2, 'a custom two-block cycle builds (' + (custom.error || custom.blocks) + ')');
  const bad = C.createCycle({ config: ccfg, templateId: 'custom', blocks: [{ phase: 'nonsense', weeks: 3 }, { phase: 'peak', weeks: 2 }] });
  ok(!bad.ok && JSON.parse(C.exportStore()).cycles.length === 2, 'an invalid custom cycle saves nothing'); }

// 15. Standalone program: next block from its history (App.js startNextBlock)
{ C.init({}); const mk = C.createProgram({ config: { name: 'Solo', goal: 'both', experience: 'intermediate', split: 'full_body', days: 3, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] } });
  const before = JSON.parse(C.exportStore()).saved.length;
  const nb = C.nextBlock({ programId: mk.programId });
  ok(!nb.ok && nb.error && JSON.parse(C.exportStore()).saved.length === before, 'next block with no logged workouts is refused and saves nothing: ' + nb.error.slice(0, 60));
  ok(JSON.parse(C.exportStore()).saved[0].generationRoute === E.GENERATION_ROUTE, 'programs carry the generation route'); }

// 15b. Turn a program into a training cycle (App.js confirmCycleConversion)
{ C.init({}); const mk = C.createProgram({ config: { name: 'Solo', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] } });
  const cv = C.convertToCycle({ programId: mk.programId, templateId: 'custom', name: 'Solo cycle', blocks: [{ phase: 'strength_accumulation', weeks: 4 }, { phase: 'peak', weeks: 2 }] });
  const st = JSON.parse(C.exportStore());
  ok(cv.ok && st.cycles.length === 1 && st.cycles[0].blockIds[0] === mk.programId && st.cycles[0].blockIds.length === 3, 'the program becomes block 1 of a new cycle: ' + (cv.error || cv.blocks));
  ok(!C.convertToCycle({ programId: mk.programId, templateId: 'custom', blocks: [{ phase: 'peak', weeks: 2 }] }).ok, 'a program already in a cycle is refused'); }

// 16a. Editor rows carry the design's details; day focus overrides the goal and Auto restores the engine's own tag
{ C.init({}); const mk = C.createProgram({ config: { name: 'Focus', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 9 });
  const pid = mk.programId; let ed = C.editor({ programId: pid }); const d0 = ed.days[0], raw0 = JSON.parse(C.exportStore()).saved[0].days[0].focus;
  ok(d0.hits.length > 0 && d0.sets > 0 && d0.exercises.every((x) => x.type && x.styleLabel) && ed.styles.length > 2, 'editor days list sets by muscle and rows carry type and progression');
  C.editAct({ programId: pid, type: 'dayFocus', dayId: d0.id, focus: 'strength' }); ed = C.editor({ programId: pid });
  ok(ed.days[0].focus === 'strength' && ed.days[0].edited && ed.dirty, 'a day focus lands in the draft');
  C.editAct({ programId: pid, type: 'dayFocus', dayId: d0.id, focus: null }); ed = C.editor({ programId: pid });
  const dr = JSON.parse(C.exportStore()).drafts?.[pid]?.days?.[0];
  ok(ed.days[0].focus === null && dr && dr.focus === raw0 && !('baseFocus' in dr), 'Auto puts the engine\'s own day tag back (' + raw0 + ')'); }

// 16b. Plateau options (App.js workoutPlateau / lighterPendingWorkout), queued from Today or the editor
{ C.init({}); const mk = C.createProgram({ config: { name: 'Stall', goal: 'both', experience: 'intermediate', split: 'full_body', days: 3, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 4 });
  const pid = mk.programId, d0 = C.editor({ programId: pid }).days[0].id;
  for (let k = 0; k < 5; k++) { C.startSession({ programId: pid, dayId: d0, weekIndex: 1 }); for (const e of C.session().exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '60' }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); } C.finishSession(); }
  const ed = C.editor({ programId: pid }), row = ed.days[0].exercises.find((x) => x.plateau);
  ok(row && row.plateau.since >= 3 && /PR/.test(row.plateau.advice), 'a flat lift reads as a plateau on its day (' + (row && row.plateau.since) + ' sessions)');
  ok(C.setPlateauChoice({ programId: pid, dayId: d0, exId: row.id, choice: 'lighter' }).choice === 'lighter' && C.editor({ programId: pid }).days[0].exercises.find((x) => x.id === row.id).plateauChoice === 'lighter', 'a plateau choice is queued for the next session');
  const s = C.startSession({ programId: pid, dayId: d0, weekIndex: 1 }), ex = s.exercises.find((x) => x.id === row.id);
  const w = ex.sets.filter((x) => !x.warm && !x.sub).map((x) => parseFloat(x.weight)).filter((v) => v > 0);
  ok(ex.plateau && w.length && w.every((v) => v < 60), 'the queued lighter targets apply when that day starts: ' + w.join(','));
  ok(!JSON.parse(C.exportStore()).plateauQueue?.[`${pid}:${d0}`], 'the queued choice is used once');
  C.discardSession(); }

// 16c. B905 rebuild review, earlier layouts, start again from week 1
{ C.init({}); const mk = C.createProgram({ config: { name: 'Review', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 11 });
  const pid = mk.programId, before = JSON.stringify(JSON.parse(C.exportStore()).saved[0].days);
  const pv = C.applyProgramSettings({ programId: pid, settings: { goal: 'strength' }, preview: true });
  ok(pv.ok && pv.preview && pv.review.config.some((c) => c.label === 'Goal') && JSON.stringify(JSON.parse(C.exportStore()).saved[0].days) === before, 'a settings rebuild is previewed without saving: ' + pv.review.count + ' changes');
  const cm = C.commitRebuild({ programId: pid }); const st = JSON.parse(C.exportStore()).saved[0];
  ok(cm.ok && st.config.goal === 'strength' && st.layoutHistory?.length === 1 && C.programSettings({ programId: pid }).layouts[0].label === 'Before settings change', 'applying keeps the earlier layout');
  const rv = C.restoreLayout({ programId: pid, index: 0 }); ok(rv.ok && rv.preview && rv.review.count > 0, 'restoring an earlier layout is reviewed first');
  ok(C.commitRebuild({ programId: pid }).ok && JSON.parse(C.exportStore()).saved[0].config.goal === 'both' && JSON.stringify(JSON.parse(C.exportStore()).saved[0].days) === before, 'restore brings back the exact earlier program');
  ok(!C.commitRebuild({ programId: pid }).ok, 'a preview applies once');
  const rs = C.resetProgram({ programId: pid, mode: 'same' }); const s2 = JSON.parse(C.exportStore());
  ok(rs.ok && s2.saved.length === 2 && s2.pinnedId === rs.programId && JSON.stringify(s2.saved[1].days) === before, 'start again from week 1 keeps the exercises in a fresh program'); }

// 16d. B903 working-set count in a workout, reviewed for the program after saving
{ C.init({}); const mk = C.createProgram({ config: { name: 'Count', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 21 });
  let s = C.startSession({}); const n0 = s.exercises[1].sets.filter((x) => !x.warm && !x.sub).length;
  s = C.act({ type: 'setCount', ei: 1, count: n0 + 1 });
  ok(s.exercises[1].sets.filter((x) => !x.warm && !x.sub).length === n0 + 1, 'the working-set count changes in the workout');
  s.exercises.forEach((e, ei) => e.sets.forEach((x) => { if (!x.done && !x.warm && !x.sub) C.act({ type: 'edit', ei, si: x.i, field: 'reps', value: '8' }) && C.act({ type: 'toggle', ei, si: x.i }); }));
  const fp = C.finishPreview(); ok(fp.programChanges.exercises.length === 1, 'the finish page lists the changed count');
  C.finishSession();
  const pu = C.pendingProgramUpdate();
  ok(pu && pu.exerciseChanges.length === 1 && pu.exerciseChanges[0].sets === n0 + 1, 'the changed count is offered to the program');
  const ap = C.completeProgramUpdate({ apply: true }); const ed = C.editor({ programId: mk.programId, weekIndex: 1 });
  ok(ap.ok && ed.days[0].exercises[1].sets === n0 + 1, 'applying sets that count in the program: ' + (ap.error || ed.days[0].exercises[1].sets));
  let s2 = C.startSession({ programId: mk.programId, dayId: ed.days[0].id, weekIndex: 1 }); const n1 = s2.exercises[2].sets.filter((x) => !x.warm && !x.sub).length;
  C.act({ type: 'setCount', ei: 2, count: n1 + 1 }); const fin = C.finishSession({ keep: { progression: [], exercises: [] } });
  ok(fin.kept && fin.kept.applied === 0 && !C.pendingProgramUpdate(), 'an unanswered change stays this workout only'); }

// 16. Program editing (App.js ProgramView ops + save review) and record stamping
{ C.init({}); const mk = C.createProgram({ config: { name: 'Edit me', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 5 });
  const pid = mk.programId; let ed = C.editor({ programId: pid });
  const d0 = ed.days[0], prim = d0.exercises.find((e) => e.primary);
  ok(ed && !ed.dirty && d0.exercises.length > 2, 'editor opens the saved program, clean');
  const alts = C.editSwapOptions({ programId: pid, dayId: d0.id, slot: 1 });
  ok(alts.length > 0 && alts.every((a) => a.id !== d0.exercises[1].id), 'swap options exclude the current lift');
  C.editAct({ programId: pid, type: 'replace', dayId: d0.id, slot: 1, newId: alts[0].id });
  ed = C.editor({ programId: pid });
  ok(ed.dirty && ed.days[0].exercises[1].id === alts[0].id && JSON.parse(C.exportStore()).saved[0].days[0].exercises[1] === d0.exercises[1].id, 'a swap lands in the draft, not the saved program');
  ok(ed.days[0].exercises.find((e) => e.primary).id === prim.id, 'swapping another slot leaves the primary lift alone');
  C.editAct({ programId: pid, type: 'move', dayId: d0.id, slot: prim.slot, dir: 1 });
  ed = C.editor({ programId: pid });
  ok(ed.days[0].exercises[prim.slot + 1].id === prim.id && ed.days[0].exercises[prim.slot + 1].primary, 'moving the primary lift keeps it primary');
  const n0 = ed.days[0].exercises.length;
  C.editAct({ programId: pid, type: 'remove', dayId: d0.id, slot: n0 - 1 });
  const add = C.editAddOptions({ programId: pid, dayId: d0.id, part: ed.days[0].exercises[0].part });
  ok(add.parts.length > 0 && add.items.length > 0, 'add options list muscles and lifts available at the gym');
  C.editAct({ programId: pid, type: 'add', dayId: d0.id, exId: add.items[0].id });
  ed = C.editor({ programId: pid });
  ok(ed.days[0].exercises.length === n0 && ed.days[0].exercises[n0 - 1].added && ed.days[0].exercises[n0 - 1].sets > 0, 'an added lift is owned and gets a prescription');
  C.editAct({ programId: pid, type: 'renameDay', dayId: d0.id, label: 'Push heavy' });
  C.editAct({ programId: pid, type: 'copyDay', dayId: d0.id });
  ed = C.editor({ programId: pid });
  ok(ed.days.length === 5 && ed.days[1].label === 'Push heavy (copy)', 'rename and duplicate a day');
  const beforeSave = JSON.parse(C.exportStore()).saved[0].updatedAt || 0;
  const sv = C.saveEdits({ programId: pid });
  const st = JSON.parse(C.exportStore());
  ok(sv.ok && sv.changes === 1 && st.saved[0].days.length === 5 && !(st.drafts || {})[pid] && st.saved[0].updatedAt >= beforeSave, 'save applies the draft, clears it and stamps updatedAt');
  ok(C.editor({ programId: pid }).canUndoSave && C.undoSave({ programId: pid }).ok && JSON.parse(C.exportStore()).saved[0].days.length === 4, 'undo the save restores the previous version');
  C.editAct({ programId: pid, type: 'deleteDay', dayId: d0.id }); ok(C.discardEdits({ programId: pid }).ok && !C.editor({ programId: pid }).dirty, 'discard drops the draft');
  const dup = C.duplicateProgram({ id: pid }); ok(dup.ok && dup.name === 'Edit me (copy)', 'duplicate a program');
  const del = C.deleteProgram({ id: dup.id }); const st2 = JSON.parse(C.exportStore());
  ok(del.ok && !st2.saved.some((p) => p.id === dup.id) && st2.tombs[dup.id] > 0, 'delete removes and tombstones the program');
  ok(C.undo().ok && JSON.parse(C.exportStore()).saved.some((p) => p.id === dup.id), 'undo brings it back');
  const cy = C.createCycle({ config: { name: 'Blocks', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, templateId: 'powerbuilding' });
  ok(C.deleteProgram({ id: JSON.parse(C.exportStore()).cycles[0].blockIds[0] }).ok === false, 'a cycle block cannot be deleted on its own');
  const ids = JSON.parse(C.exportStore()).cycles[0].blockIds; C.deleteCycle({ id: cy.cycleId, alsoBlocks: true });
  const st3 = JSON.parse(C.exportStore());
  ok(st3.tombs[cy.cycleId] > 0 && ids.every((id) => st3.tombs[id] > 0), 'deleting a cycle with its blocks tombstones all of them'); }

// 17. Custom exercises, CSV in and out, reminders
{ C.init({}); const mk = C.createProgram({ config: { name: 'Customs', goal: 'both', experience: 'intermediate', split: 'full_body', days: 3, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 4 });
  const ed = C.editor({ programId: mk.programId }); const day = ed.days[0];
  ok(C.createCustomExercise({ name: '', part: 'chest' }).ok === false, 'a custom exercise needs a name');
  const cx = C.createCustomExercise({ name: 'Landmine Press (mine)', part: 'shoulders', type: 'compound', equip: ['barbell', 'bogus'], lo: 6, hi: 10, programId: mk.programId, dayId: day.id });
  const ed2 = C.editor({ programId: mk.programId });
  ok(cx.ok && ed2.days[0].exercises.some((e) => e.id === cx.id && e.name === 'Landmine Press (mine)' && e.sets > 0), 'a custom lift is created and added to the day with a prescription');
  C.saveEdits({ programId: mk.programId });
  const store = C.exportStore();
  C.init({ store });
  ok(C.library({ q: 'Landmine Press (mine)' }).length === 1 && C.editor({ programId: mk.programId }).days[0].exercises.some((e) => e.name === 'Landmine Press (mine)'), 'custom lifts are registered again after a restart');
  ok(C.deleteCustomExercise({ id: cx.id }).ok === false, 'a custom lift a program uses cannot be deleted');
  const strong = ['Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE',
    '2026-03-02 07:10:00,Upper A,1h 5m,Bench Press (Barbell),W,60,10,0,0,,,', '2026-03-02 07:10:00,Upper A,1h 5m,Bench Press (Barbell),1,100,5,0,0,,,8',
    '2026-03-02 07:10:00,Upper A,1h 5m,Lat Pulldown (Cable),1,70,10,0,0,,,', '2026-03-04 07:00:00,Lower A,58m,Deadlift (Barbell),1,180,3,0,0,,,',
    '2026-03-04 07:00:00,Lower A,58m,Mystery Machine Thing,1,40,12,0,0,,,'].join('\n');
  const pv = C.importStore({ text: strong, preview: true });
  ok(pv.ok && pv.preview && pv.csv.workouts === 2 && pv.csv.label, 'a Strong CSV previews as ' + (pv.csv && pv.csv.workouts) + ' workouts from ' + (pv.csv && pv.csv.label));
  const before = (JSON.parse(C.exportStore()).history || []).length;
  const imp = C.importStore({ text: strong, csvWeightUnit: 'kg' });
  ok(imp.ok && imp.csv && JSON.parse(C.exportStore()).history.length === before + 2, 'importing the CSV merges two workouts into history');
  const csv = C.exportCsv({}); const lines = csv.split('\n');
  ok(lines[0].startsWith('Date,Program,Day,Exercise') && lines.some((l) => l.includes('Bench Press')), 'workout CSV export has the header and the imported lifts');
  C.logBodyweight({ value: 80 }); ok(C.exportCsv({ kind: 'body' }).split('\n')[1].includes('Bodyweight'), 'body CSV export');
  ok(C.reminders().enabled === false && C.reminders().days.join() === '1,2,3,4,5', 'reminders default off, weekdays');
  C.setReminders({ enabled: true, time: '07:30', days: [6, 0, 0, 9] });
  ok(C.settings().reminders.enabled && C.reminders().time === '07:30' && C.reminders().days.join() === '0,6', 'reminders saved and cleaned'); }

// 18. Program settings rebuild, shuffle and restore, compare
{ C.init({}); const eq = ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'];
  const mk = C.createProgram({ config: { name: 'Settings', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: eq }, seed: 21 });
  const ps = C.programSettings({ programId: mk.programId });
  ok(ps && ps.weeks > 0 && ps.sessions.length > 3, 'program settings read back');
  const first = JSON.parse(C.exportStore()).saved[0];
  const rb = C.applyProgramSettings({ programId: mk.programId, settings: { weeks: ps.weeks === 6 ? 5 : 6, goal: 'strength' } });
  const after = JSON.parse(C.exportStore()).saved[0];
  ok(rb.ok && rb.rebuilt && after.id === first.id && after.name === first.name && after.config.goal === 'strength' && C.programSettings({ programId: mk.programId }).weeks === (ps.weeks === 6 ? 5 : 6), 'a goal/length change rebuilds the same program in place');
  ok(after.rolls && after.rolls[0].seed === first.seed, 'the previous roll is remembered');
  const meta = C.applyProgramSettings({ programId: mk.programId, settings: { description: 'Off-season' } });
  ok(meta.ok && !meta.rebuilt && JSON.parse(C.exportStore()).saved[0].description === 'Off-season', 'description is a plain edit');
  const sh = C.applyProgramSettings({ programId: mk.programId, shuffle: true });
  const shuffled = JSON.parse(C.exportStore()).saved[0];
  ok(sh.ok && shuffled.seed !== after.seed, 'shuffle rolls a new layout');
  const back = C.applyProgramSettings({ programId: mk.programId, seed: after.seed });
  ok(back.ok && JSON.parse(C.exportStore()).saved[0].seed === after.seed, 'a previous roll can be restored');
  const two = C.createProgram({ config: { name: 'Other', goal: 'hypertrophy', experience: 'intermediate', split: 'full_body', days: 3, session: 's60', equipment: eq }, seed: 8 });
  const cmp = C.comparePrograms({ a: mk.programId, b: two.programId });
  ok(cmp && cmp.a.days === 4 && cmp.b.days === 3 && cmp.muscles.length > 4 && cmp.a.totalSets > 0, 'compare two programs: days, sets, muscles');
  const lib = C.library({ q: 'bench' });
  const cx = C.compareExercises({ a: lib[0].id, b: lib[1].id });
  ok(cx && cx.a.rep.length === 2 && cx.muscles.length > 0, 'compare two exercises');
  const cy = C.createCycle({ config: { name: 'C', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: eq }, templateId: 'powerbuilding' });
  ok(C.applyProgramSettings({ programId: JSON.parse(C.exportStore()).cycles[0].blockIds[0], settings: { weeks: 3 } }).ok === false, 'a cycle block cannot be rebuilt from its settings'); }

// 19. Templates and training maxes
{ C.init({}); const tl = C.templates();
  ok(tl.items.length > 30 && tl.categories.length > 4, 'templates listed (' + tl.items.length + ')');
  const pct = tl.items.find((t) => t.percent);
  const made = C.createFromTemplate({ id: pct.id });
  ok(made.ok && made.percent, 'a percentage template builds: ' + (made.error || pct.name));
  const h0 = C.home();
  const tms = C.trainingMaxes({ programId: made.programId });
  ok(tms.percent && tms.lifts.length > 0 && h0.needsTrainingMax === tms.lifts.length, 'its main lifts need training maxes (' + tms.lifts.length + ')');
  C.setTrainingMax({ programId: made.programId, exId: tms.lifts[0].id, value: 100, fromOneRm: true });
  const t1 = C.trainingMaxes({ programId: made.programId }).lifts[0];
  ok(t1.tm > 0 && t1.tm <= 90 && t1.week1 && t1.week1.sets.length > 0, '90% of a 100 kg 1RM sets the TM (' + t1.tm + ') and week 1 shows ' + (t1.week1 && t1.week1.sets.join(' ')));
  tms.lifts.forEach((l) => C.setTrainingMax({ programId: made.programId, exId: l.id, value: 80 }));
  ok(C.home().needsTrainingMax === 0, 'all set: the prompt clears');
  { const all = C.trainingMaxes({ programId: made.programId });
    ok(all.method && all.method.name && all.weeks.length >= 1 && all.weeks.every((w) => w.w >= 1), 'percentage panel names the method and lists weeks (' + all.method.name + ', ' + all.weeks.map((w) => w.label || w.w).join('/') + ')');
    const last = all.weeks[all.weeks.length - 1].w, dl = C.trainingMaxes({ programId: made.programId, weekIndex: last });
    ok(dl.weekIndex === last && dl.lifts.every((l) => l.week && l.week.sets.length > 0), 'every lift shows the chosen week\'s sets');
    ok(!all.deloadWeek || all.weeks.some((w) => w.deload), 'the deload week has a chip when the block has one');
    // The panel's week equals what the workout prescribes from
    const day = all.lifts[0]; ok(day.week.sets.every((x) => /×/.test(x.text)), 'sets read weight × reps'); }
  { const t = C.programText({ programId: made.programId }).text;
    ok(/Week \d+ of \d+ — saved plan snapshot/.test(t) && /Planned loads: [\d.]+ kg × \d/.test(t) && /Made with Pursuit Iron/.test(t), 'share text (B930): the selected week with canonical planned loads');
    const pt = C.programText({ programId: made.programId }); ok(pt && pt.text.split('\n').length > 6, 'share text for a plain program'); }
  const plain = C.createFromTemplate({ id: tl.items.find((t) => !t.percent).id });
  ok(plain.ok && C.trainingMaxes({ programId: plain.programId }).percent === false, 'a regular template builds without training maxes'); }

// 20. Injury journal outside a workout
{ C.init({}); C.createProgram({ config: { name: 'Inj', goal: 'both', experience: 'intermediate', split: 'full_body', days: 3, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  ok(C.saveInjury({ region: '' }).ok === false, 'an injury needs an area');
  ok(C.saveInjury({ region: 'knee', onset: '2999-01-01' }).ok === false, 'onset cannot be in the future');
  const s1 = C.saveInjury({ region: 'shoulder', side: 'left', impact: 'reduce', note: 'pinch overhead' });
  const log = C.injuryLog();
  ok(s1.ok && log.active.length === 1 && log.active[0].side === 'left' && C.home().injuries.length === 1, 'logged from Today: active and shown');
  ok(Array.isArray(log.active[0].nextWorkout), 'lists the next workout lifts it touches (' + log.active[0].nextWorkout.join(', ') + ')');
  C.saveInjury({ id: s1.id, region: 'shoulder', side: 'both', impact: 'skip' });
  ok(C.injuryLog().active[0].impact === 'skip' && C.injuryLog().active.length === 1, 'editing keeps one entry');
  const r = C.toggleInjuryResolved({ id: s1.id });
  ok(r.resolved && C.injuryLog().resolved.length === 1 && C.home().injuries.length === 0, 'mark resolved clears it from Today and keeps the record');
  ok(C.toggleInjuryResolved({ id: s1.id }).resolved === false && C.home().injuries.length === 1, 'mark active again'); }

// 21. Workout screen parity: readiness, feedback, warm-ups, skip, set types, rest, progression, finish preview, quick + routine
{ C.init({}); const eq = ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'];
  const mk = C.createProgram({ config: { name: 'WS', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: eq }, seed: 5 });
  const pg = JSON.parse(C.exportStore()).saved[0];
  let s = C.startSession({ programId: pg.id, dayId: pg.days[0].id, weekIndex: 1 });
  s.exercises.forEach((e, ei) => e.sets.forEach((x) => { if (!x.warm && !x.sub) { C.act({ type: 'edit', ei, si: x.i, field: 'weight', value: '40' }); C.act({ type: 'edit', ei, si: x.i, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei, si: x.i }); } }));
  C.finishSession();
  s = C.startSession({ programId: pg.id, dayId: pg.days[0].id, weekIndex: 1 });
  const w0 = Number(s.exercises[0].sets.find((x) => !x.warm && !x.sub).weight);
  s = C.act({ type: 'readiness', label: 'Run down', factor: 0.88 });
  const w1 = Number(s.exercises[0].sets.find((x) => !x.warm && !x.sub).weight);
  ok(s.readinessDone && s.readinessLabel === 'Run down' && (w0 === 0 || w1 < w0), 'readiness scales loads down (' + w0 + ' -> ' + w1 + ')');
  const hadWarm = s.exercises[0].hasWarm;
  s = C.act({ type: 'warmups', ei: 0 });
  ok(s.exercises[0].hasWarm === !hadWarm, 'warm-ups toggle on the first lift');
  const nSets = s.exercises[1].sets.length;
  s = C.act({ type: 'setType', ei: 1, si: s.exercises[1].sets.find((x) => !x.warm).i, kind: 'failure' });
  ok(s.exercises[1].sets.some((x) => x.type === 'failure'), 'a set can be marked to failure');
  s = C.act({ type: 'advanced', ei: 1, kind: 'drop' });
  ok(s.exercises[1].hasDrop && s.exercises[1].sets.length > nSets, 'drop sets added');
  s = C.act({ type: 'advanced', ei: 1, kind: 'drop' });
  ok(!s.exercises[1].hasDrop, 'drop sets removed again');
  { // The design's live-set additions: current-set plates, direct + Drop / + Myo, skip a set, BW / Band tag.
    const bi = s.exercises.findIndex((e) => e.bar === 'barbell');
    if (bi >= 0) {
      const bx = s.exercises[bi], si0 = bx.cur;
      C.act({ type: 'edit', ei: bi, si: si0, field: 'weight', value: s.unit === 'lb' ? '225' : '100' });
      const pb = C.session().exercises[bi].plates;
      ok(pb && pb.exact && pb.text === (s.unit === 'lb' ? '2×45/side' : '25 · 15/side') && pb.barLabel === 'barbell', 'the current set shows its plates per side (' + (pb && pb.text) + ')');
      ok(C.session().exercises[bi].addRules.drop.show === false, 'no drop sets on a barbell lift');
    }
    const di = s.exercises.findIndex((e, i) => i !== bi && e.addRules.drop.show && !e.assisted && e.sets.some((x) => !x.warm && !x.sub));
    if (di >= 0) {
      let e = C.session().exercises[di];
      ok(e.addRules.drop.why === 'Log a working set first', 'drop waits for a logged working set');
      const w = e.sets.filter((x) => !x.warm && !x.sub);
      w.forEach((x) => { C.act({ type: 'edit', ei: di, si: x.i, field: 'weight', value: '40' }); C.act({ type: 'edit', ei: di, si: x.i, field: 'reps', value: '10' }); });
      const last = w[w.length - 1];
      C.act({ type: 'toggle', ei: di, si: last.i });
      let s2 = C.act({ type: 'addOne', ei: di, kind: 'drop' });
      const d1 = s2.exercises[di].sets.filter((x) => x.sub && x.kind === 'drop');
      ok(d1.length === 1 && Number(d1[0].weight) < 40 && Number(d1[0].weight) >= 25, 'one drop added at about −25% (' + (d1[0] && d1[0].weight) + ')');
      ok(s2.exercises[di].addRules.drop.why === 'Finish the extension you added first', 'one pending extension at a time');
      C.act({ type: 'toggle', ei: di, si: d1[0].i });
      s2 = C.act({ type: 'addOne', ei: di, kind: 'drop' });
      ok(s2.exercises[di].sets.filter((x) => x.sub && x.kind === 'drop').length === 2, 'a second drop after the first is logged');
      s2 = C.act({ type: 'toggle', ei: di, si: s2.exercises[di].sets.filter((x) => x.sub && x.kind === 'drop')[1].i });
      ok(s2.exercises[di].addRules.drop.why === 'Two drops is the limit', 'two drops is the limit');
      const t0 = s2.totalSets, first = s2.exercises[di].sets.find((x) => !x.warm && !x.sub && !x.done);
      if (first) {
        s2 = C.act({ type: 'skipSet', ei: di, si: first.i });
        ok(s2.totalSets === t0 - 1 && s2.exercises[di].sets[first.i].skipped, 'a skipped set stops counting');
        ok(s2.exercises[di].cur !== first.i, 'the current set moves past a skipped one');
        s2 = C.act({ type: 'skipSet', ei: di, si: first.i, skip: false });
        ok(s2.totalSets === t0 && !s2.exercises[di].sets[first.i].skipped, 'a skipped set can be restored');
      }
      const rmv = s2.exercises[di].sets.filter((x) => x.sub && x.kind === 'drop').map((x) => x.i);
      rmv.forEach((i) => C.act({ type: 'toggle', ei: di, si: i }));
      rmv.slice().reverse().forEach((i) => C.act({ type: 'removeSet', ei: di, si: i }));
      ok(!C.session().exercises[di].hasDrop, 'unlogged drops can be removed');
      C.act({ type: 'toggle', ei: di, si: last.i });
    }
  }
  s = C.act({ type: 'restFor', ei: 1, sec: 150 });
  ok(s.exercises[1].restCustom === 150 && JSON.parse(C.exportStore()).saved[0].overrides, 'per-lift rest saved to the program');
  C.setGoal({ id: s.exercises[0].id, weight: 1 }); C.setExSetup({ id: s.exercises[0].id, key: 'seat', value: '4' });
  s = C.session();
  ok(s.exercises[0].goal === 1, 'goal weight shows on the lift');
  const style = s.progressionStyles.map((x) => x.id).find((k) => k !== 'auto' && k !== s.exercises[0].progression?.style);
  if (!s.exercises[0].progression?.locked) { s = C.act({ type: 'progression', ei: 0, style }); ok(s.exercises[0].progression.changed, 'progression changed for this session'); }
  const part = s.exercises[0].part;
  C.act({ type: 'feedback', part, pump: 'good' }); C.act({ type: 'feedback', part, sore: 'fresh' });
  s.exercises.forEach((e, ei) => e.sets.forEach((x) => { if (!x.done && !x.warm) C.act({ type: 'toggle', ei, si: x.i }); }));
  s = C.act({ type: 'skipExercise', ei: s.exercises.length - 1 });
  const pv = C.finishPreview();
  ok(pv && pv.doneSets > 0 && pv.parts.some((p) => p.part === part && p.pump === 'good' && p.sore === 'fresh'), 'finish preview carries sets and feedback');
  ok(pv.nextPlan.length > 0 && pv.mainE1rm && pv.mainE1rm.prev > 0, 'next-session plan and main-lift e1RM vs the last workout (' + pv.nextPlan.length + ')');
  const fin = C.finishSession();
  const h = JSON.parse(C.exportStore()).history[0];
  ok(h.feedbackRaw && h.feedbackRaw[part].pump === 'good' && typeof h.feedback[part] === 'number', 'feedback stored on the workout');
  if (h.progressionChanges) {
    const pu = C.pendingProgramUpdate();
    ok(pu && pu.changes.length > 0, 'a progression change asks to update the program');
    ok(C.completeProgramUpdate({ apply: true }).applied && JSON.parse(C.exportStore()).history[0].programUpdateStatus === 'applied' && C.pendingProgramUpdate() === null, 'applying it settles the prompt');
  }
  // Quick workout -> routine
  let q = C.startQuick();
  ok(q && q.quick && q.exercises.length === 0, 'quick workout starts empty');
  const lib = C.library({ q: 'curl' });
  q = C.act({ type: 'addExercise', id: lib[0].id }); q = C.act({ type: 'addExercise', id: C.library({ q: 'press' })[0].id });
  ok(q.exercises.length === 2 && q.exercises[0].freestyle, 'lifts added to the quick workout');
  q = C.act({ type: 'edit', ei: 0, si: q.exercises[0].sets[0].i, field: 'weight', value: '12' });
  q = C.act({ type: 'toggle', ei: 0, si: q.exercises[0].sets[0].i });
  const qp = C.finishPreview();
  ok(qp.quick && qp.routineIds.length === 2, 'quick finish offers to save as a routine');
  C.finishSession();
  const before = JSON.parse(C.exportStore()).saved.length;
  ok(C.saveRoutine({ ids: qp.routineIds, name: 'Arms' }).ok && JSON.parse(C.exportStore()).saved.length === before + 1, 'routine saved to the library');
  const qh = JSON.parse(C.exportStore()).history[0];
  const rp = C.repeatWorkout({ historyId: qh.id });
  ok(rp && rp.exercises && rp.exercises.length >= 1, 'a past workout can be repeated');
  C.discardSession && C.discardSession(); }

// 22. Today and Progress parity: fresher day, deload/light sessions, recap, lift detail, standards, correct a workout, share card
{ C.init({}); const eq = ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'];
  C.createProgram({ config: { name: 'TP', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: eq }, seed: 9 });
  const logAll = (w) => { const s = C.session(); s.exercises.forEach((e, ei) => e.sets.forEach((x) => { if (!x.warm && !x.sub) { C.act({ type: 'edit', ei, si: x.i, field: 'weight', value: String(w) }); C.act({ type: 'edit', ei, si: x.i, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei, si: x.i }); } })); return C.finishSession(); };
  C.startSession({}); logAll(40); C.startSession({}); logAll(45);
  const h = C.home();
  ok(typeof h.freshness === 'number' && 'alt' in h && h.recap && h.recap.count === 2 && Array.isArray(h.insights), 'Today: freshness, recap (' + h.recap.count + ' this week), insights ' + JSON.stringify(h.insights));
  ok(h.recovery && Array.isArray(h.recovery.parts) && h.recovery.parts.length > 0, 'muscle recovery rows');
  const dl = C.startDeload();
  const st = JSON.parse(C.exportLive());
  ok(dl.active && st.forceDeload && dl.weekIndex === dl.weeksTotal + 1, 'deload now: the deload week of this day (wk ' + dl.weekIndex + ')');
  C.discardSession();
  const lt = C.startLight();
  ok(lt.active && JSON.parse(C.exportLive()).forceDeload, 'lighter session starts at deload intensity');
  C.discardSession();
  if (h.alt) { const a = C.startAlt(); ok(a.active && a.dayId === h.alt.dayId, 'fresher day starts'); C.discardSession(); }
  else ok(C.startAlt().error, 'no fresher day: refused');
  const pr = C.progress();
  ok(pr.lifts.length > 0 && pr.lifts[0].points.length === 1 || pr.lifts.some((l) => l.points.length >= 1), 'progress sparklines have points');
  const lid = pr.lifts[0].id;
  ok(pr.pins.auto && pr.pins.lifts.every((l) => l.points.length >= 2), 'never pinned: auto pins only lifts with a trend');
  const ids = pr.lifts.map((l) => l.id), want = [ids[ids.length - 1], 'no-such-lift', ids[0]];
  const pp = C.progress({ pinned: want });
  ok(!pp.pins.auto && pp.pins.lifts.map((l) => l.id).join() === [ids[ids.length - 1], ids[0]].filter((v, i, a) => a.indexOf(v) === i).join(), 'pins keep their order and drop unknown lifts');
  ok(pp.pins.lifts.every((l) => !pp.pins.options.some((o) => o.id === l.id)) && ids.every((id) => pp.pins.lifts.some((l) => l.id === id) || pp.pins.options.some((o) => o.id === id)), 'options are every other trended lift');
  ok(pp.pins.lifts.every((l) => l.e1rm > 0 && (l.heavy === null || / × /.test(l.heavy))), 'pinned cards carry the record e1RM and set');
  ok(C.progress({ pinned: ids.concat(ids) }).pins.lifts.length === Math.min(6, new Set(ids).size), 'six pins at most');
  ok(C.progress({ pinned: [] }).pins.lifts.length === 0, 'unpinning everything stays empty');
  ok(pr.records.length > 0 && pr.records.every((r) => r.weight && r.reps > 0), 'records show the set that made them: ' + pr.records.slice(0, 2).map((r) => r.weight + '×' + r.reps).join(', '));
  const ld = C.liftDetail({ id: lid });
  ok(ld && ld.points.length >= 1 && ld.best && ld.best.e1rm > 0 && ld.recent.length >= 1, 'lift detail: series, best and recent sets');
  ok(C.strengthStandards().ready === false && C.strengthStandards().missing === 'bodyweight', 'standards ask for bodyweight first');
  C.setProfile({ sex: 'male', birth: '1990-01-01' }); C.logBodyweight({ value: 80 });
  const ss = C.strengthStandards();
  ok(ss.ready && ss.cohort.includes('men'), 'standards cohort: ' + ss.cohort + ' (' + ss.rows.length + ' lifts)');
  const H0 = JSON.parse(C.exportStore()).history[0];
  const dr = C.historyEditDraft({ id: H0.id });
  ok(dr && dr.lifts.length > 0 && dr.lifts[0].sets.length > 0, 'edit draft lists lifts and sets');
  dr.lifts[0].sets[0].w = '50'; dr.lifts = dr.lifts.slice(0, -1);
  const ed = C.editHistory({ id: H0.id, date: H0.date, durationMin: 42, lifts: dr.lifts });
  const H1 = JSON.parse(C.exportStore()).history.find((x) => x.id === H0.id);
  ok(ed.ok && Object.keys(H1.perf).length === Object.keys(H0.perf).length - 1 && String(H1.perf[dr.lifts[0].id].sets[0].w) === '50' && Number(H1.durationMin) === 42, 'corrected workout saved: set, removed lift, minutes');
  ok(C.undo().ok && Object.keys(JSON.parse(C.exportStore()).history.find((x) => x.id === H0.id).perf).length === Object.keys(H0.perf).length, 'correction can be undone');
  const sc = C.shareCard({ id: H0.id });
  ok(sc && sc.tops.length > 0 && sc.label && sc.mainE1rm, 'share card data: ' + sc.tops.length + ' lifts, PRs ' + sc.prs.length); }

// 23. Settings parity: effort scale, experience, banned list, schedule, clear/reset, storage, references
{ C.init({}); const eq = ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'];
  const mk = C.createProgram({ config: { name: 'S', unit: 'kg', goal: 'both', experience: 'advanced', split: 'upper_lower', days: 4, session: 's60', equipment: eq }, seed: 3 });
  ok(C.settings().experience === 'advanced', 'building a program remembers training experience');
  C.updateSettings({ loadMode: 'rpe', experience: 'novice' });
  ok(C.settings().loadMode === 'rpe' && C.settings().experience === 'novice', 'effort scale and experience saved');
  ok(C.updateSettings({ loadMode: 'bogus' }).ok && C.settings().loadMode === 'rpe', 'unknown effort scale ignored');
  const s = C.startSession({});
  const tgt = s.exercises[0].sets.find((x) => !x.warm && x.target && x.target.rir != null);
  ok(s.loadMode === 'rpe' && (!tgt || /^RPE/.test(tgt.target.effort)), 'targets read as RPE: ' + (tgt && tgt.target.effort));
  C.discardSession(); C.updateSettings({ loadMode: 'rir' });
  const lib = C.library({ q: 'curl' }); C.setBan({ id: lib[0].id, banned: true }); C.setBan({ id: lib[1].id, banned: true });
  ok(C.settings().banned.length === 2 && C.settings().banned[0].name, 'banned list with names');
  ok(C.clearBanned().count === 2 && C.settings().banned.length === 0, 'unban all');
  ok(C.undo().ok && C.settings().banned.length === 2, 'unban all can be undone');
  const sc = C.schedule({ programId: mk.programId });
  ok(sc.week.length === 7 && sc.days.length === 4, 'weekly schedule lists 7 weekdays');
  C.clearSchedule({ programId: mk.programId });
  let s2 = C.cycleSchedule({ programId: mk.programId, weekday: 1 }); s2 = C.cycleSchedule({ programId: mk.programId, weekday: 1 });
  ok(s2.week[0].dayId === sc.days[1].id && s2.trainingDays === 1, 'tapping Monday cycles rest → day 1 → day 2');
  ok(C.clearSchedule({ programId: mk.programId }).trainingDays === 0, 'schedule cleared');
  C.startSession({}); const ss = C.session(); ss.exercises[0].sets.filter((x) => !x.warm).forEach((x) => { C.act({ type: 'edit', ei: 0, si: x.i, field: 'weight', value: '20' }); C.act({ type: 'toggle', ei: 0, si: x.i }); }); C.finishSession();
  ok(C.clearHistory().count === 1 && C.settings().sessions === 0, 'clear history');
  ok(C.undo().ok && C.settings().sessions === 1, 'clear history can be undone');
  const st = C.storageStatus(); ok(st.bytes > 1000 && st.workouts === 1, 'storage status (' + st.bytes + ' bytes)');
  ok(C.references().length > 3 && C.references()[0].name, 'references listed');
  C.updateSettings({ theme: 'x' });
  ok(C.resetAll().ok && C.settings().programs === 0 && C.settings().sessions === 0 && C.settings().banned.length === 0 && C.settings().theme === 'x' && !C.session().active, 'reset everything keeps device preferences only'); }
// Week-by-week table and volume wave read the engine's weeks 1…n (+ deload), never week 0
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  const mk = C.createProgram({ config: { name: 'Wk', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', split: 'ppl', days: 3, session: 's60', deload: true,
    equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 7 });
  const wb = C.weekByWeek({});
  ok(wb && wb.rows.length > 5 && wb.weeks >= 4, 'week-by-week lists ' + (wb && wb.rows.length) + ' lifts over ' + (wb && wb.weeks) + ' weeks');
  ok(wb.deloadWeek === wb.weeks + 1 && wb.rows.every((r) => r.cells.length === wb.weeks + 1 && r.cells[wb.weeks].deload), 'deload week is the last column');
  ok(wb.rows.every((r) => r.cells.every((c) => c.sets > 0)), 'every cell prescribes sets (no week-0 "missing" cell)');
  const p0 = JSON.parse(C.exportStore()).saved.find((x) => x.id === wb.programId), d0 = p0.days[0];
  const cc = E.computeCell(p0, d0, d0.exercises[0], 0, 1), first = wb.rows.find((r) => r.dayLabel === d0.label && r.exId === d0.exercises[0]);
  ok(first.cells[0].sets === Number(cc.sets) && first.cells[0].range === String(cc.range), 'week 1 cell equals computeCell(week 1): ' + cc.sets + ' × ' + cc.range);
  ok(wb.rows.some((r) => r.primary) && wb.rows.some((r) => r.upper) && wb.rows.some((r) => !r.upper), 'rows carry main/upper/lower for the filters');
  const vw = C.volumeWave({});
  ok(vw.weeks.length === wb.weeks + 1 && vw.weeks.every((w) => w.sets > 0) && vw.weeks[wb.weeks].deload, 'volume wave has every week plus the deload');
  const wSum = wb.rows.reduce((a, r) => a + r.cells[1].sets, 0);
  ok(vw.weeks[1].sets === wSum, 'wave week 2 total (' + vw.weeks[1].sets + ') equals the table column sum (' + wSum + ')');
  const st = C.exercise({ id: 'cable-fly' }), nb = C.exercise({ id: wb.rows[0].exId });
  ok(st && st.stretchFocus === true, 'cable fly is a stretch-focus lift');
  ok(nb && typeof nb.stretchFocus === 'boolean', 'exercise detail always says whether it is stretch focus'); }
// Progression sheet: every engine style with what it does; Auto names its current pick, Linear its jump
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'P', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60',
    equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 11 });
  const ss = C.startSession({}), o = ss.exercises[0].progression.options;
  ok(o.length === Object.keys(E.PROG_STYLES).length && o.every((x) => x.label && x.detail), 'progression options cover every engine style with a description');
  ok(/currently [a-z]/.test(o[0].detail) && ss.exercises[0].progression.auto.length > 3, 'Auto names the style it picks now: ' + o[0].detail);
  ok(/fixed \d+(\.\d+)?kg/.test(o.find((x) => x.id === 'linear').detail), 'Linear names this lift\'s jump');
  C.discardSession(); }
// Weekly volume per muscle against the landmarks, and the engine's repair
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'V', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', split: 'ppl', days: 3, session: 's40',
    equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 7 });
  const v = C.weeklyVolumeView({});
  ok(v.rows.length > 5 && v.rows.every((r) => r.mev >= 0 && r.mrv > r.mev && ['below MEV', 'productive', 'high', 'over MRV', 'a head over MRV'].includes(r.zone)), 'volume rows carry landmarks and a zone');
  const sum = Math.round(v.rows.reduce((a, r) => a + r.sets, 0));
  ok(Math.abs(sum - v.total) <= 1, 'rows add up to the total (' + sum + ' vs ' + v.total + ')');
  ok(v.rows.every((r) => (r.sets < r.mev) === (r.zone === 'below MEV')), 'below MEV exactly when sets are under MEV');
  const rp = C.repairVolume({});
  ok(rp.ok && typeof rp.message === 'string' && rp.message.length > 5, 'repair answers with the engine message: ' + rp.message); }
// Welcome back: after ten days off, one easier session (2 working sets at 90%) before the plan resumes
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'R', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'full_body', days: 3, session: 's60',
    equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 5 });
  for (let k = 0; k < 2; k++) { C.startSession({}); for (const e of C.session().exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '60' }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); } C.finishSession(); }
  ok(C.home().reentry === null, 'no welcome back right after training');
  const st = JSON.parse(C.exportStore()); st.history.forEach((h) => { h.date -= 14 * 86400000; });
  C.init({ store: JSON.stringify(st) });
  const hr = C.home().reentry;
  ok(hr && hr.daysOff >= 14 && hr.sets === 2 && hr.factor === 0.9, 'fourteen days off offers re-entry');
  const plain = C.startSession({}); const full = plain.exercises.map((e) => ({ n: e.sets.filter((x) => !x.warm && !x.sub).length, w: e.sets.filter((x) => !x.warm && !x.sub).map((x) => parseFloat(x.weight) || 0) }));
  C.discardSession();
  const re = C.startReentry();
  ok(re.active && re.reentry && re.reentry.daysOff >= 14 && re.readinessDone, 're-entry starts, flagged, with readiness answered');
  ok(re.exercises.every((e, i) => e.sets.filter((x) => !x.warm && !x.sub).length === Math.min(2, full[i].n)), 'two working sets per lift');
  ok(re.exercises.every((e, i) => e.sets.filter((x) => !x.warm && !x.sub).every((x, j) => !(full[i].w[j] > 0) || parseFloat(x.weight) < full[i].w[j])), 'every loaded set is lighter than the plan');
  ok(C.home().reentry === null && C.startReentry().error, 'no second offer with a workout open');
  C.discardSession(); }
// Rest day: a scheduled program with nothing on today's weekday
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  const mk = C.createProgram({ config: { name: 'S', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60',
    equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 3 });
  const pid = mk.programId || C.home().next.programId, today = new Date().getDay();
  C.clearSchedule({ programId: pid });
  ok(C.home().restDay === null, 'no schedule, no rest day');
  C.cycleSchedule({ programId: pid, weekday: (today + 2) % 7 });
  const rdv = C.home().restDay;
  ok(rdv && rdv.inDays === 2 && /^on [A-Z]/.test(rdv.when) && rdv.nextLabel, 'unscheduled today: rest day, next session ' + (rdv && rdv.when));
  ok(Array.isArray(rdv.rows) && rdv.rows.length <= 6, 'rest day lists recovery rows');
  C.cycleSchedule({ programId: pid, weekday: today });
  ok(C.home().restDay === null, 'scheduled today: a training day'); }
// Block length: endless (continuous) and the deload week, from the builder and from program settings
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  const eq = ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'];
  const mk = C.createProgram({ config: { name: 'E', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: eq, endless: true, weeks: 5, deload: true, progression: 'auto' }, seed: 9 });
  ok(mk.ok && C.home().next.endless === true && C.plan({}).endless === true, 'an endless program says so on Today and Plan');
  const mk2 = C.createProgram({ config: { name: 'F', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: eq, weeks: 8, deload: true }, seed: 9 });
  ok(C.weekByWeek({ programId: mk2.programId }).deloadWeek === 9, 'an 8-week block with a deload has week 9 as its deload');
  const ps = C.programSettings({ programId: mk2.programId });
  ok(ps && ps.weeks === 8 && ps.deload === true && ps.endless === false, 'settings read length, deload and continuous');
  const r = C.applyProgramSettings({ programId: mk2.programId, settings: { endless: true } });
  ok(r.ok && r.rebuilt && C.programSettings({ programId: mk2.programId }).endless === true, 'switching to continuous rebuilds the program');
  const r2 = C.applyProgramSettings({ programId: mk2.programId, settings: { endless: false, deload: false } });
  const ps2 = C.programSettings({ programId: mk2.programId });
  ok(r2.ok && ps2.endless === false && ps2.deload === false, 'and back to a fixed block without a deload'); }
// Program-wide lift progression: styles, reasons, locks
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  const mk = C.createProgram({ config: { name: 'L', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 4 });
  const lp = C.liftProgression({ programId: mk.programId });
  ok(lp.lifts.length > 5 && lp.lifts.every((l) => l.style && l.label && l.reason && !l.locked), 'every lift has a style and a reason, none locked');
  ok(lp.groups.reduce((a, g) => a + g.count, 0) === lp.lifts.length, 'groups count every lift');
  const l0 = lp.lifts[0], other = lp.styles.find((x) => x.id !== 'auto' && x.id !== l0.style).id;
  const r = C.setLiftProgression({ programId: mk.programId, exId: l0.id, style: other });
  const l1 = r.lifts.find((l) => l.id === l0.id);
  ok(r.ok && l1.style === other && l1.locked, 'choosing a style locks it: ' + other);
  const ss = C.startSession({}); const e = ss.exercises.find((x) => x.id === l0.id);
  ok(!e || e.progression.style === other, 'the workout runs the chosen style'); C.discardSession();
  const r2 = C.setLiftProgression({ programId: mk.programId, exId: l0.id, style: 'auto' });
  ok(r2.ok && !r2.lifts.find((l) => l.id === l0.id).locked, 'Auto hands the lift back to the engine');
  const pl = C.plan({ programId: mk.programId }), d0 = pl.days[0];
  const w = C.whyLift({ programId: mk.programId, dayId: d0.id, slot: 0, weekIndex: 1 });
  ok(w && w.source === 'engine' && w.role && w.role.label === 'Main strength lift' && w.rx && w.rx.sets > 0, "why this lift: the engine's role and prescription for the main lift");
  const w2 = C.whyLift({ programId: mk.programId, dayId: d0.id, slot: d0.exercises.length - 1, weekIndex: 1 });
  ok(w2 && w2.role && w2.progression.label && w2.muscles.length >= 1, 'why this lift for an accessory: ' + (w2 && w2.role && w2.role.label) + ' · dose lines ' + (w2 && w2.dose.length)); }
// Percentage method change on an existing program
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  const made = C.createFromTemplate({ id: 'p531' }); const tms = C.trainingMaxes({ programId: made.programId });
  tms.lifts.forEach((l) => C.setTrainingMax({ programId: made.programId, exId: l.id, value: 100 }));
  const pm = C.percentMethods({ programId: made.programId });
  ok(pm && pm.methods.length >= 9 && pm.methods.filter((m) => m.current).length === 1, 'every percentage method listed, the current one marked');
  const nsuns = C.changePercentMethod({ programId: made.programId, templateId: 'nsuns' });
  const after = C.trainingMaxes({ programId: made.programId });
  ok(nsuns.ok && after.method.id === 'nsuns' && C.percentMethods({ programId: made.programId }).methods.find((m) => m.current).scheme === 'nsuns', 'switching rebuilds on the new method: ' + nsuns.message);
  ok(nsuns.carried > 0 && after.lifts.some((l) => l.tm === 100), 'same basis: training maxes carry over');
  const tx = C.changePercentMethod({ programId: made.programId, templateId: 'texas' });
  ok(tx.ok && tx.carried === 0 && tx.needsTrainingMax > 0, 'a different basis asks for maxes again');
  { const sv = JSON.parse(C.exportStore()).saved; ok(sv.length === 1 && sv[0].id === made.programId, 'still one program, same id'); }
  C.startSession({}); ok(C.changePercentMethod({ programId: made.programId, templateId: 'p531' }).ok === false, 'refused with a workout open'); C.discardSession(); }
// Assisted lifts: help is a negative load, and taking help off is the "more" direction
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.startQuick(); let q = C.act({ type: 'addExercise', id: 'assisted-pullup' });
  const e = q.exercises.find((x) => x.id === 'assisted-pullup');
  ok(e && e.assisted === true && q.exercises.filter((x) => x.assisted).length === 1, 'assisted lifts are flagged for the workout');
  const si = e.sets.findIndex((x) => !x.warm && !x.sub);
  q = C.act({ type: 'edit', ei: e.ei, si, field: 'weight', value: '-40' });
  ok(q.exercises[e.ei].sets[si].weight === '-40', 'help is stored as a negative load');
  q = C.act({ type: 'bumpWeight', ei: e.ei, si, dir: 1 });
  ok(parseFloat(q.exercises[e.ei].sets[si].weight) > -40, 'plus takes help off (' + q.exercises[e.ei].sets[si].weight + ')');
  C.discardSession(); }
// The design's Today: streak, week strip, block, this week's days, coach notes
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'T', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  let h = C.home();
  ok(h.streakWeeks === 0 && h.weekStrip.length === 7 && h.weekStrip.filter((d) => d.today).length === 1, 'no streak yet, a 7-day strip with today marked');
  ok(h.block && h.block.weekIndex === 1 && h.weekDays.length === 4 && h.weekDays.filter((d) => d.status === 'today').length === 1, 'block and this week\'s days, one up next');
  ok(h.coach.length >= 1 && h.coach.every((f) => f.title && f.text), 'coach notes for the next session: ' + h.coach.map((f) => f.title).join(' | '));
  C.startSession({}); for (const e of C.session().exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '50' }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); }
  C.finishSession(); h = C.home();
  ok(h.streakWeeks === 1 && h.weekStrip.some((d) => d.today && d.done) && h.weekDays.filter((d) => d.status === 'done').length === 1, 'a workout today: streak 1, today checked, its day done'); }
// Progress charts: weekly volume, intensity distribution, recovery & feedback — from logged history
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'G', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  C.startSession({}); let n = 0;
  for (const e of C.session().exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '50' }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); if (n++ % 2 === 0) C.act({ type: 'rir', ei: e.ei, si: x.i, rir: 2 }); }
  C.act({ type: 'feedback', part: 'chest', sore: 'sore' }); C.finishSession();
  const g = C.progressCharts();
  ok(g.weeks.length === 8 && g.weeks[7].now && g.weeks[7].sessions === 1 && g.weeks[7].volume > 0, 'eight weeks of volume, this week logged: ' + g.weeks[7].volume + ' kg');
  const total = g.intensity.reps.reduce((a, z) => a + z.sets, 0), rated = g.intensity.effort.reduce((a, z) => a + z.sets, 0);
  ok(total === n && g.intensity.reps[1].sets === n, 'every working set counted once, all 8 reps in 6–12');
  ok(rated + g.intensity.unrated === n && g.intensity.effort[2].sets === Math.ceil(n / 2), 'effort only from reported RIR; the rest counted as unrated');
  ok(g.feedback.some((f) => f.part === 'chest' && f.answers[0] === 'Too much'), 'soreness answers per muscle'); }
// You → Profile board: lifetime, big three, PR board, muscle balance
{ C.init({ store: JSON.stringify({ unit: 'kg', bodyweight: '80' }) });
  C.createProgram({ config: { name: 'B', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  C.startSession({}); for (const e of C.session().exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '100' }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '5' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); }
  C.finishSession();
  const b = C.profileBoard();
  ok(b.lifetime.sessions === 1 && b.lifetime.lifted > 0 && b.lifetime.streakWeeks === 1, 'lifetime totals');
  ok(b.prBoard.e1rm.length > 0 && b.prBoard.heaviest[0].value === 100 && b.prBoard.heaviest[0].reps === 5, 'PR board: heaviest set with its reps');
  ok(b.big3.lifts.length === 3 && b.big3.lifts.some((l) => l.e1rm > 0 && l.ratio > 1), 'big three against bodyweight: ' + b.big3.lifts.map((l) => l.name + ' ' + l.e1rm + (l.ratio ? ' (' + l.ratio + '×)' : '')).join(', '));
  ok(b.balance.parts.length > 0 && b.balance.parts.reduce((a, p) => a + p.sets, 0) > 0, 'muscle balance counts working sets per muscle');
  ok(b.heat.length === 84 && b.heat.filter((h) => h.today).length === 1 && b.heat.find((h) => h.today).level > 0 && b.heatCount === 1, 'training calendar: 12 weeks, today shaded');
  ok(b.badges.length === 8 && b.badges.find((x) => x.id === 'century').status === '1% · 1 of 100 sessions', 'eight badges with progress');
  const rp = b.repPRs[0]; ok(rp && rp.reps.length === 6 && rp.reps.find((r) => r.reps === 5).done && rp.reps.find((r) => r.reps === 5).w === 100 && !rp.reps.find((r) => r.reps === 12).done, 'rep PRs: done at 5, predicted at 12');
  ok(/^B · /.test(b.program), 'header: program and goal: ' + b.program);
  C.setProfile({ name: 'Sam', age: 31 }); const pr = C.profile();
  ok(pr.name === 'Sam' && pr.age === 31, 'profile name and age from the stepper');
  // B892 share card: chosen rows in a fixed order, at least one, favorites limited to three logged or known lifts
  const card = C.profileCard({ share: ['streak', 'prs', 'bogus'], favorites: [] });
  ok(card.rows.map((r) => r.id).join() === 'prs,streak' && card.rows[1].lines[0] === '1 week' && card.rows[0].lines.length > 0 && /·/.test(card.rows[0].lines[0]), 'share card rows: ' + JSON.stringify(card.rows.map((r) => r.lines)));
  ok(C.profileCard({ share: [] }).rows.map((r) => r.id).join() === 'level', 'an empty selection keeps one row');
  const favId = card.lifts[0].id; const fc = C.profileCard({ share: ['favorites'], favorites: [favId, favId, 'nope', card.lifts[1]?.id, card.lifts[2]?.id, card.lifts[3]?.id].filter(Boolean) });
  ok(fc.favorites.length <= 3 && fc.favorites[0] === favId && fc.rows[0].lines[0].startsWith(card.lifts[0].name), 'favorites: deduped, known, up to three'); }
// live-autoregulation.js: two observed misses ease the next pending accessory row, shown as `adjusted`
{ C.init({ store: JSON.stringify({ unit: 'kg', bodyweight: '80' }) });
  C.createProgram({ config: { name: 'B', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  for (let n = 0; n < 4; n++) { C.startSession({}); for (const e of C.session().exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '40' }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '10' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); } C.finishSession(); }
  C.startSession({});
  const e = C.session().exercises.find((x) => x.sets.filter((y) => !y.warm && !y.sub).length >= 3 && !x.primary);
  const w = e.sets.filter((x) => !x.warm && !x.sub);
  ok(w[2].adjusted === null, 'nothing eased before any set');
  for (const k of [0, 1]) { C.act({ type: 'edit', ei: e.ei, si: w[k].i, field: 'reps', value: '3' }); C.act({ type: 'toggle', ei: e.ei, si: w[k].i }); C.act({ type: 'rir', ei: e.ei, si: w[k].i, rir: 0 }); }
  const a = C.session().exercises[e.ei].sets.filter((x) => !x.warm && !x.sub);
  ok(a[2].adjusted === 'load' && parseFloat(a[2].weight) < parseFloat(w[2].weight), 'two misses ease the next set: ' + w[2].weight + ' → ' + a[2].weight);
  C.act({ type: 'toggle', ei: e.ei, si: w[1].i });
  ok(C.session().exercises[e.ei].sets.filter((x) => !x.warm && !x.sub)[2].adjusted === null, 'un-logging the set puts the load back'); }
// B918: a program the lifter built keeps its days through Program settings; a constraint its lifts break is refused
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'Mine', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 3 });
  const st = JSON.parse(C.exportStore()); const prog = st.saved[0]; prog.custom = true; delete prog.engineSource;
  const days = JSON.stringify(prog.days.map((d) => d.exercises));
  C.init({ store: JSON.stringify(st) });
  const r = C.applyProgramSettings({ programId: prog.id, settings: { weeks: 8, goal: 'strength' } });
  const after = C.exportStore() && JSON.parse(C.exportStore()).saved.find((x) => x.id === prog.id);
  ok(r.ok && JSON.stringify(after.days.map((d) => d.exercises)) === days && after.config.weeks === 8 && after.config.goal === 'strength', 'custom program: settings apply in place, days unchanged');
  ok(C.applyProgramSettings({ programId: prog.id, shuffle: true }).ok === false, 'custom program: nothing to shuffle');
  const bad = C.applyProgramSettings({ programId: prog.id, settings: { weeks: 60 } });
  ok(!bad.ok && /1 to 52/.test(bad.error), 'block length outside 1–52 is refused: ' + bad.error);
  }
// A comma-decimal keyboard: "82,5" logs 82.5, never 825
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'B', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  C.startSession({}); const e = C.session().exercises[0], x = e.sets.find((y) => !y.warm && !y.sub);
  C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '82,5' });
  ok(C.session().exercises[0].sets[x.i].weight === '82.5', 'comma decimal weight: ' + C.session().exercises[0].sets[x.i].weight); }
// B932 pinned note: note and setup saved together, Remove clears both (older setup fields too)
{ const id = 'bb-bench'; C.init({ store: JSON.stringify({ unit: 'kg', exSetup: { [id]: { old_field: 'x' } } }) });
  const pn = id && C.pinnedNote({ id });
  ok(pn && pn.has && pn.fields.some((f) => f.key === 'old_field' && f.value === 'x'), 'an older setup field is kept and shown');
  if (pn) {
    C.savePinnedNote({ id, note: ' pause on chest ', setup: { old_field: 'x', bench: ' 4 ' } });
    const a1 = C.pinnedNote({ id }); ok(a1.note === 'pause on chest' && a1.fields.find((f) => f.key === 'bench')?.value === '4' && a1.fields.find((f) => f.key === 'old_field')?.value === 'x', 'save writes the note and setup together');
    C.removePinnedNote({ id }); const a2 = C.pinnedNote({ id }); ok(!a2.has && a2.note === '' && !a2.fields.some((f) => f.value), 'remove clears note and every setup field'); } }
// Top set + back-offs (manual style): logging the top set re-aims back-offs to ~90%; an owned top set earns the next load
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'B', unit: 'kg', goal: 'strength', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  const p = C.plan(), d = p.days[0], ex = d.exercises[0];
  ok(C.setLiftProgression({ programId: p.programId, exId: ex.id, style: 'top' }).ok, 'top set + back-offs can be chosen for a lift');
  const run = (rir) => { C.startSession({ programId: p.programId, dayId: d.id }); const e = C.session().exercises.find((x) => x.id === ex.id), w = e.sets.filter((x) => !x.warm && !x.sub);
    C.act({ type: 'edit', ei: e.ei, si: w[0].i, field: 'weight', value: '100' }); C.act({ type: 'edit', ei: e.ei, si: w[0].i, field: 'reps', value: '6' });
    C.act({ type: 'toggle', ei: e.ei, si: w[0].i }); C.act({ type: 'rir', ei: e.ei, si: w[0].i, rir });
    const a = C.session().exercises[e.ei].sets.filter((x) => !x.warm && !x.sub);
    for (const x of a.slice(1)) C.act({ type: 'toggle', ei: e.ei, si: x.i });
    C.finishSession(); return a; };
  const a = run(3);
  ok(a.slice(1).every((x) => x.weight === '90' && x.adjusted === 'backoff'), 'back-offs re-aim to 90% when the top set is logged: ' + a.map((x) => x.weight).join(' '));
  C.startSession({ programId: p.programId, dayId: d.id }); const n = C.session().exercises.find((x) => x.id === ex.id).sets.filter((x) => !x.warm && !x.sub);
  ok(Number(n[0].weight) > 100 && n.slice(1).every((x) => Number(x.weight) <= Number(n[0].weight) * 0.9 + 1e-6 && Number(x.weight) >= Number(n[0].weight) * 0.85), 'an owned top set earns the next load, back-offs follow at ~90%: ' + n.map((x) => x.weight).join(' ')); }
// Main-Lift Waves · High-Volume Supplemental: the same lift follows its wave as 5×10 at 50% TM (light 5×5 on the deload week)
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  ok(C.createFromTemplate({ id: 'p531bbb' }).ok, 'the supplemental template builds');
  const p = C.plan(); for (const l of C.trainingMaxes({ programId: p.programId }).lifts) C.setTrainingMax({ programId: p.programId, exId: l.id, value: 100 });
  const d = C.plan().days[0], ex = d.exercises;
  ok(ex[1].id === ex[0].id && ex[1].supplemental && ex[1].sets === 5 && String(ex[1].reps) === '10', 'each day: the main lift, then its 5×10 supplemental: ' + ex.slice(0, 2).map((x) => x.name + ' ' + x.sets + '×' + x.reps).join(', '));
  C.startSession({ programId: p.programId, dayId: d.id });
  const s1 = C.session(), sup = s1.exercises[1].sets.filter((x) => !x.warm && !x.sub);
  ok(s1.exercises[1].supplemental && sup.length === 5 && sup.every((x) => x.weight === '50' && String(x.reps) === '10'), 'the workout prescribes 5 × 10 at 50 kg (50% of 100): ' + sup.map((x) => x.weight + '×' + x.reps).join(' '));
  for (const e of s1.exercises.slice(0, 2)) for (const x of e.sets) if (!x.warm && !x.sub) C.act({ type: 'toggle', ei: e.ei, si: x.i });
  ok(C.finishSession().ok, 'a workout with the same lift twice saves');
  const t = C.programText({ programId: p.programId, weekIndex: 4 }).text;
  ok(/5 × 5 @/.test(t) && /40 kg × 5/.test(t), 'the deload week runs a light 5×5 supplemental'); }
// Main-Lift Waves · Novice: each day's main lift is followed by its partner's First-Set-Last 5×5 (squat↔bench, deadlift↔OHP)
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  ok(C.createFromTemplate({ id: 'p531beg' }).ok, 'the novice template builds');
  const p = C.plan(), tms = C.trainingMaxes({ programId: p.programId });
  ok(tms.lifts.length === 4 && C.home().needsTrainingMax === 4, 'all four main lifts ask for a training max: ' + tms.lifts.map((l) => l.name).join(', '));
  for (const l of tms.lifts) C.setTrainingMax({ programId: p.programId, exId: l.id, value: 100 });
  const pairs = C.plan().days.map((d) => [d.exercises[0].id, d.exercises[1].id, d.exercises[1].supplemental]);
  ok(pairs.every(([a, b, sup]) => sup && ({ 'back-squat': 'bb-bench', 'bb-bench': 'back-squat', deadlift: 'ohp' })[a] === b), 'every day pairs its main lift with the partner FSL: ' + pairs.map((x) => x.join('+')).join(', '));
  C.startSession({ programId: p.programId, dayId: C.plan().days[0].id });
  const f = C.session().exercises[1].sets.filter((x) => !x.warm && !x.sub);
  ok(f.length === 5 && f.every((x) => x.weight === '65' && String(x.reps) === '5'), 'week 1 FSL: 5 × 5 at 65% of the partner\'s max: ' + f.map((x) => x.weight + '×' + x.reps).join(' ')); C.discardSession(); }
// Warm-up & mobility on the first lift until a set is logged (App.js warmupRoutine)
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'W', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  let ss = C.startSession({});
  ok(ss.warmup && ss.warmup.moves.length >= 3 && ss.warmup.parts.length >= 1, 'warm-up routine before the first set: ' + ss.warmup.parts.join(', '));
  const e = ss.exercises[0], si = e.sets.findIndex((x) => !x.warm && !x.sub);
  C.act({ type: 'edit', ei: 0, si, field: 'weight', value: '50' }); C.act({ type: 'edit', ei: 0, si, field: 'reps', value: '5' }); ss = C.act({ type: 'toggle', ei: 0, si });
  ok(!ss.warmup, 'gone once a set is logged'); C.discardSession(); }
// Exercises: equipment filter and TODAY tags
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'L', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  const all = C.library({}), bb = C.library({ equip: 'barbell' }), bw = C.library({ equip: 'bodyweight' });
  ok(bb.length > 5 && bb.every((x) => x.equipLabel === 'Barbell') && bw.length > 3 && bw.every((x) => x.equipLabel === 'Bodyweight'), 'equipment filter: ' + bb.length + ' barbell, ' + bw.length + ' bodyweight');
  ok(all.filter((x) => x.today).length === C.home().next.exercises.length, 'TODAY tags the next session\'s lifts');
  ok(C.library({ part: 'chest', equip: 'dumbbell' }).every((x) => x.part === 'chest' && x.equipLabel === 'Dumbbell'), 'filters combine'); }
// Backup nudge (App.js needsBackup): a program and no backup in two weeks
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  ok(C.home().backup === null, 'nothing to back up yet');
  C.createProgram({ config: { name: 'K', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  ok(C.home().backup === null, 'a program built today: no nudge yet');
  const st = JSON.parse(C.exportStore()); st.saved.forEach((p) => { p.createdAt = Date.now() - 20 * 86400000; }); C.init({ store: JSON.stringify(st) });
  ok(C.home().backup && C.home().backup.lastBackup === null, 'three weeks in and never backed up: nudge');
  C.markBackup(); ok(C.home().backup === null, 'a fresh backup clears it'); }
// Build 898 shorter sessions: preview, start, log, and the saved program untouched
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'S', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's90', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 6 });
  const before = JSON.stringify(JSON.parse(C.exportStore()).saved);
  const pv = C.sessionPreview({});
  ok(pv && pv.options.length === 4 && pv.options[0].label === 'Full' && pv.options[0].fits, 'preview offers Full / 45 / 30 / 20');
  const opt = pv.options.slice(1).find((o) => o.fits && o.changed);
  ok(!!opt, 'a shorter option fits and changes the session: ' + pv.options.map((o) => o.label + (o.fits ? '' : '✗') + ' ' + o.estimatedMinutes).join(', '));
  const sp = C.sessionPreview({ minutes: opt.minutes });
  ok(sp.workingSets < pv.workingSets && sp.estimatedMinutes <= opt.minutes, 'the plan has fewer working sets (' + pv.workingSets + ' → ' + sp.workingSets + ') and fits ' + opt.minutes + ' min');
  const ss = C.startSession({ minutes: opt.minutes });
  const rows = ss.exercises.reduce((a, e) => a + e.sets.filter((x) => !x.warm && !x.sub).length, 0);
  ok(ss.active && ss.timeLimit && ss.timeLimit.minutes === opt.minutes && rows === sp.workingSets, 'the workout runs the plan: ' + rows + ' working sets');
  for (const e of ss.exercises) for (const x of e.sets) { if (x.warm || x.sub) continue; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '40' }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '10' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); }
  C.finishSession();
  const st = JSON.parse(C.exportStore()), h = st.history[0];
  ok(h.timeLimited === true && h.prescriptionEdited === true && h.sessionLengthPlan && h.sessionLengthPlan.minutes === opt.minutes, 'history records the time limit and marks the exposure neutral');
  ok(JSON.stringify(st.saved.map((x) => x.days)) === JSON.stringify(JSON.parse(before).map((x) => x.days)), 'the saved program is unchanged');
  const tooShort = C.sessionPreview({ minutes: 20 });
  if (!tooShort.fits) ok(!!C.startSession({ minutes: 20 }).error, '20 min refused when the protected work does not fit');
  else { const s2 = C.startSession({ minutes: 20 }); ok(s2.active, '20 min fits and starts'); C.discardSession(); } }
{ // BW / Band tags come from the lift's equipment.
  const tags = C.session().active ? C.session().exercises.map((e) => [e.id, e.loadTag]) : [];
  ok(tags.every(([id, t]) => t !== 'Band' || /band/.test(id)), 'only band lifts carry the Band tag');
}
{ // The design's measurements in cm or inches, independent of kg / lb.
  const u0 = C.body().unit;
  C.setLengthUnit({ unit: 'cm' }); C.logMeasurement({ key: 'waist', value: 84 });
  C.setLengthUnit({ unit: 'in' });
  const b = C.body(), w = b.measurements.waist;
  ok(b.lengthUnit === 'in' && b.unit === u0 && Math.abs(w[w.length - 1].v - 33.1) < 0.05, 'measurements read in inches without changing kg / lb (' + (w.length && w[w.length - 1].v) + ' in)');
  C.setLengthUnit({ unit: 'cm' });
  ok(Math.abs(C.body().measurements.waist.slice(-1)[0].v - 84) < 0.05, 'and back in cm unchanged');
}
{ // The design's library status filters.
  const L = C.library({});
  ok(L.some((x) => x.recent) && L.filter((x) => x.recent).every((x) => typeof x.goal === 'boolean'), 'library rows say which lifts were trained (' + L.filter((x) => x.recent).length + ')');
}
{ // The design's lift page: sessions, records, rep maxes.
  const id = JSON.parse(C.exportStore()).history[0] && Object.keys(JSON.parse(C.exportStore()).history[0].perf || {})[0];
  const d = id ? C.liftDetail({ id }) : null;
  ok(d && d.log.length > 0 && d.log[0].sets.length > 0 && d.records && d.records.heaviest.w > 0 && d.repMax.length > 0, 'lift page has sessions, records and rep maxes (' + (d && d.log.length) + ' sessions)');
  ok(d && d.repMax.every((x, i, a) => i === 0 || x.reps > a[i - 1].reps), 'rep maxes run from fewest reps up');
}
{ // The design's history list: groups, filters, programs.
  const g = C.historyGroups({}), m = C.historyGroups({ by: 'month' }), none = C.historyGroups({ q: 'zzzz-nothing' });
  ok(g.groups.length > 0 && g.groups.reduce((a, x) => a + x.items.length, 0) === g.total && g.programs.length > 0, 'history groups by cycle cover every session (' + g.total + ')');
  ok(m.groups.length > 0 && m.groups.reduce((a, x) => a + x.count, 0) === m.total, 'and by month');
  ok(none.filtered && none.shown === 0, 'a search with no match is filtered and empty');
}
{ // The design's shared programs.
  const G = C.galleryList(), pin0 = JSON.parse(C.exportStore()).pinnedId, n0 = JSON.parse(C.exportStore()).saved.length;
  ok(G.length >= 10 && G.every((g) => g.ok && g.days > 0 && g.title), 'gallery lists ' + G.length + ' decodable shared programs');
  const pick = G.find((g) => !g.equipment) || G[0];
  const r = C.importGallery({ code: pick.code }), st = JSON.parse(C.exportStore());
  ok(r.ok && st.saved.length === n0 + 1 && st.pinnedId === pin0, 'importing adds the program without switching to it (' + (r.error || r.name) + ')');
  ok(C.galleryList().find((g) => g.code === pick.code).added, 'the shared program shows as added');
}
{ // The design's "Short on time?" mid-workout.
  if (C.session().active) C.discardSession();
  let s1 = C.startSession({}); const o = C.timeOptions();
  ok(o && o.leftMin > 0 && o.options.length === 4, 'time options list what is left (' + (o && o.leftMin) + ' min)');
  const tight = o.options.find((x) => x.cut > 0);
  if (tight) {
    const t0 = s1.totalSets; s1 = C.act({ type: 'fitTime', minutes: tight.minutes });
    ok(s1.totalSets === t0 - tight.cut, 'fitting ' + tight.minutes + ' min trims ' + tight.cut + ' sets');
    ok(s1.exercises.every((e) => e.sets.some((x) => !x.warm && !x.sub && !x.skipped)), 'every lift keeps a working set');
    s1 = C.act({ type: 'restoreTrimmed' });
    ok(s1.totalSets === t0, 'trimmed sets come back');
  }
  C.discardSession();
}
{ // Web Build 904–910: the next session's review — game plan and plan changes with undo.
  C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'R', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'full_body', days: 3, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 3 });
  let s3 = C.startSession({}); const day = s3.dayId, pid = s3.programId;
  s3.exercises.forEach((e) => e.sets.forEach((x) => { if (x.warm || x.sub) return; C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: '50' }); C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'reps', value: '15' }); C.act({ type: 'toggle', ei: e.ei, si: x.i }); C.act({ type: 'rir', ei: e.ei, si: x.i, rir: 3 }); }));
  C.finishSession();
  const r = C.sessionReview({ programId: pid, dayId: day, weekIndex: 2 });
  ok(r && r.gamePlan.length > 0 && r.gamePlan.length <= 3 && r.changes.length > 0, 'next session review: ' + r.gamePlan.length + ' game-plan notes, ' + r.changes.length + ' plan changes');
  ok(r.gamePlan.some((g) => g.kind === 'pr'), 'a PR chance leads the game plan');
  const ch = r.changes.find((c) => c.canUndo);
  ok(!!ch, 'one change can be undone');
  if (ch) {
    const u = C.planChoice({ programId: pid, dayId: day, weekIndex: 2, exerciseId: ch.id, slot: ch.slot, undo: true });
    ok(u.ok && u.review.changes.find((c) => c.id === ch.id).undone, 'a plan change can be undone');
    const b = C.planChoice({ programId: pid, dayId: day, weekIndex: 2, exerciseId: ch.id, slot: ch.slot, undo: false });
    ok(b.ok && !b.review.changes.find((c) => c.id === ch.id).undone, 'and the recommendation restored');
  }
}
{ // The design's Programs and Program details.
  const L = C.programsList(), d = C.programDetails({});
  ok(L.count > 0 && L.programs[0].active && L.programs[0].meta.includes('day'), 'programs list leads with the active program (' + L.programs[0].meta + ')');
  ok(d && d.chips.some((c) => c.k === 'Split') && d.plan.length > 0 && d.plan.some((w) => w.current), 'program details: ' + d.chips.length + ' chips, ' + d.plan.length + ' weeks');
}
// The design's carried load: a logged set's load fills the later empty working sets; unlogging takes it back
{ C.init({}); C.createProgram({ config: { name: 'Carry', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] } });
  C.startSession({}); let cs = C.session();
  const ei = cs.exercises.findIndex((e) => e.sets.filter((x) => !x.warm).every((x) => !(parseFloat(x.weight) > 0)));
  if (ei >= 0) {
    const si = cs.exercises[ei].sets.findIndex((x) => !x.warm && !x.sub);
    C.act({ type: 'edit', ei, si, field: 'weight', value: '60' }); cs = C.act({ type: 'toggle', ei, si });
    const later = cs.exercises[ei].sets.filter((x, j) => j > si && !x.warm && !x.sub);
    ok(later.length > 0 && later.every((x) => x.weight === '60'), 'a logged load fills the later empty working sets');
    C.act({ type: 'edit', ei, si: si + 1, field: 'weight', value: '62.5' });
    cs = C.act({ type: 'toggle', ei, si });
    const after = cs.exercises[ei].sets.filter((x, j) => j > si && !x.warm && !x.sub);
    ok(after[0].weight === '62.5' && after.slice(1).every((x) => x.weight === ''), 'unlogging takes the carried load back, keeping a load you changed');
  } else ok(false, 'no first-time lift to test the carried load'); }

console.log(fails ? fails + ' FAILED' : 'ALL PASS'); process.exit(fails ? 1 : 0);

// The design's finish page: too easy / about right / too much per muscle, the next day, time and volume
{ C.init({ store: JSON.stringify({ unit: 'kg' }) });
  C.createProgram({ config: { name: 'F', unit: 'kg', goal: 'both', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60', equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 2 });
  const s0 = C.startSession({}); const e = s0.exercises[0], si = e.sets.findIndex((x) => !x.warm && !x.sub);
  C.act({ type: 'edit', ei: 0, si, field: 'weight', value: '60' }); C.act({ type: 'edit', ei: 0, si, field: 'reps', value: '8' }); C.act({ type: 'toggle', ei: 0, si });
  let f = C.finishPreview(); const part = f.parts[0].part;
  C.act({ type: 'feedback', part, diff: -1 }); f = C.finishPreview();
  ok(f.parts[0].diff === -1 && f.parts[0].sets === 1 && typeof f.parts[0].room === 'boolean', 'difficulty answer kept with the sets done');
  ok(f.title && f.nextDay && f.nextDay.rows.length > 0 && f.volume === 480 && f.elapsedMs >= 0, 'finish page: next day ' + (f.nextDay && f.nextDay.label) + ', volume and time');
  C.act({ type: 'feedback', part, diff: -1 }); ok(C.finishPreview().parts[0].diff === null, 'tapping the same answer clears it');
  C.act({ type: 'feedback', part, diff: -1 });
  const r = C.finishSession(); const h = C.history ? null : null;
  ok(r && r.ok !== false, 'finish with a difficulty answer'); }
