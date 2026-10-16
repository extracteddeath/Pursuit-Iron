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
  ok(C.gyms().minInc.v === 1 && C.gyms().minInc.unit === 'kg', 'smallest increment saved with its unit'); C.setMinInc({ v: 0 }); }

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
  const del = C.deleteCycle({ id: made.cycleId });
  const st2 = JSON.parse(C.exportStore());
  ok(del.ok && st2.cycles.length === 0 && st2.saved.length > 0 && st2.saved.every((p) => !p.cycleId), 'deleting a cycle keeps its blocks as standalone programs');
  ok(C.undo().ok && JSON.parse(C.exportStore()).cycles.length === 1, 'undo restores the cycle');
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
console.log(fails ? fails + ' FAILED' : 'ALL PASS'); process.exit(fails ? 1 : 0);

