// A first month on the core with a fake clock: onboarding's program, four weeks of a lifter who follows it,
// and after every day the screens' data checked against what that lifter would expect to see. Fast (no device),
// so it runs with every test; the emulator month (smoke/month.py) covers the same flow for layout and taps.
const Real = Date; let off = 0;
const setNow = (ms) => { off = ms - Real.now(); };
class FakeDate extends Real { constructor(...a) { if (a.length === 0) super(Real.now() + off); else super(...a); } static now() { return Real.now() + off; } }
globalThis.Date = FakeDate;
const C = await import('../src/core.js');

let fails = 0, passes = 0;
const ok = (cond, msg) => { if (cond) passes++; else { fails++; console.log('FAIL ' + msg); } };
const DAY = 864e5, start = new Real(2026, 9, 5, 17, 30).getTime(); // a Monday
// Text a screen would show: no NaN, undefined or Infinity anywhere in what the core hands the UI.
const clean = (label, v) => { const s = JSON.stringify(v ?? null); const m = s.match(/.{0,40}(NaN|undefined|Infinity).{0,40}/); ok(!m, label + ' has no NaN/undefined' + (m ? ': …' + m[0] + '…' : '')); };

setNow(start);
C.init({ store: JSON.stringify({ unit: 'kg' }) });
const made = C.createProgram({ config: { name: 'Month', unit: 'kg', goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, weeks: 4, session: 's60',
  equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 3 });
ok(made.ok, 'onboarding builds the program');
const pid = made.programId;
const sched = C.schedule({ programId: pid });
const trainDays = new Set(sched.week.filter((x) => x.dayId).map((x) => x.weekday));
ok(trainDays.size === 4, 'four scheduled training days (' + [...trainDays] + ')');
const perWeek = 4, blockWeeks = C.plan({ programId: pid }).weeksTotal;
ok(blockWeeks === 4, 'a four-week block, so the month runs through its end and back to week 1 (' + blockWeeks + ')');
// The program week after n sessions: a block with no deload repeats, so week 5 is week 1 again.
const weekAfter = (n) => (Math.floor(n / perWeek) % blockWeeks) + 1;

const W = (n) => (/dumbbell|curl|raise|fly/i.test(n) ? 14 : /barbell|squat|deadlift/i.test(n) ? 60 : 35);
const firstLoad = {}, lastLoad = {};
let sessions = 0;
for (let d = 0; d < 29; d++) {
  setNow(start + d * DAY);
  const tag = new Date().toDateString();
  const h = C.home();
  clean(tag + ' home', h);
  const wd = new Date().getDay();
  const expWeek = weekAfter(sessions);
  ok(h.next && h.next.weekIndex === expWeek, `${tag}: Today is on week ${expWeek} after ${sessions} sessions (got ${h.next?.weekIndex})`);
  ok(h.block && h.block.weekIndex === expWeek, `${tag}: the block header agrees (week ${h.block?.weekIndex})`);
  const doneInWeek = sessions % perWeek;
  const shownDone = h.weekDays.filter((x) => x.status === 'done').length;
  ok(shownDone === doneInWeek, `${tag}: Today's week shows ${doneInWeek} done (got ${shownDone})`);
  const pl = C.plan({ programId: pid });
  clean(tag + ' plan', pl);
  ok(pl.weekIndex === expWeek && pl.days.filter((x) => x.status === 'done').length === doneInWeek, `${tag}: Plan shows week ${expWeek} with ${doneInWeek} done (got week ${pl.weekIndex}, ${pl.days.filter((x) => x.status === 'done').length} done)`);
  ok(pl.days.filter((x) => x.status === 'today').length === 1, `${tag}: Plan marks exactly one day up next`);
  if (!trainDays.has(wd)) {
    ok(!!h.restDay, `${tag}: an unscheduled day reads as a rest day`);
    continue;
  }
  ok(!h.restDay, `${tag}: a scheduled day is not a rest day`);
  const pv = C.sessionPreview({});
  clean(tag + ' preview', pv);
  const s = C.startSession({});
  ok(!s.error, `${tag}: the session starts (${s.error || 'ok'})`);
  let n = 0, total = 0;
  for (const e of C.session().exercises) for (const x of e.sets) {
    if (x.warm || x.sub) continue;
    total++;
    const cur = C.session().exercises[e.ei].sets[x.i];
    if (!cur.weight) C.act({ type: 'edit', ei: e.ei, si: x.i, field: 'weight', value: String(W(e.name)) });
    const w = Number(C.session().exercises[e.ei].sets[x.i].weight);
    ok(w > 0, `${tag}: ${e.name} set ${x.i} has a load to lift (${w})`);
    if (!(e.id in firstLoad)) firstLoad[e.id] = w;
    lastLoad[e.id] = w;
    C.act({ type: 'toggle', ei: e.ei, si: x.i });
    // On target: the low end of the set's own reserve range (2 for "2–3 RIR").
    C.act({ type: 'rir', ei: e.ei, si: x.i, rir: parseFloat(String(C.session().exercises[e.ei].sets[x.i].target?.rir ?? '2')) || 2 }); n++;
  }
  clean(tag + ' live session', C.session());
  const fp = C.finishPreview ? C.finishPreview() : null;
  if (fp) clean(tag + ' finish page', fp);
  const fin = C.finishSession();
  ok(fin?.ok !== false, `${tag}: the session saves (${fin?.error || 'ok'})`);
  sessions++;
  ok(total >= 10 && n === total, `${tag}: every working set logged (${n}/${total})`);
  const after = C.home();
  ok(after.weekStrip.some((x) => x.today && x.done), `${tag}: today's strip day is ticked after training`);
  // Week rollover: the 4th session moves Today to the next week with nothing done in it.
  const ew = weekAfter(sessions);
  ok(after.next.weekIndex === ew && after.weekDays.filter((x) => x.status === 'done').length === sessions % perWeek,
    `${tag}: after session ${sessions}, Today is on week ${ew} with ${sessions % perWeek} done (got week ${after.next.weekIndex}, ${after.weekDays.filter((x) => x.status === 'done').length})`);
  if (after.recap) {
    const days = Math.round((after.recap.to - after.recap.from) / DAY);
    ok(days === 7 && after.recap.count <= perWeek + 1, `${tag}: recap is a 7-day window with at most a week's sessions (${days} days, ${after.recap.count})`);
  }
}
ok(sessions === 17, 'Mon/Wed/Fri/Sun over 29 days is 17 sessions (' + sessions + ')');

// After the month: progress, history and the profile read the month back.
setNow(start + 29 * DAY);
const pc = C.progressCharts();
clean('progress charts', pc);
const wk = pc.weeks.filter((x) => x.sessions > 0);
ok(wk.length >= 4 && wk.every((x) => x.volume > 0), 'progress has volume in each trained week (' + pc.weeks.map((x) => x.label + ':' + x.sessions).join(' ') + ')');
ok(pc.weeks.reduce((a, x) => a + x.sessions, 0) === sessions, 'the weekly chart counts every session');
const hist = C.historyList({});
clean('history', hist);
const pb = C.profileBoard();
clean('profile', pb);
// Engine behaviour (the web engine's, reported rather than failed here): a lifter on target shouldn't lose load.
const warn = (cond, msg) => { if (!cond) console.log('WARN ' + msg); };
const fell = Object.keys(lastLoad).filter((id) => lastLoad[id] < firstLoad[id] * 0.9);
warn(fell.length === 0, 'loads fell >10% over the month with every set on target: ' + fell.map((id) => id + ' ' + firstLoad[id] + '→' + lastLoad[id]).join(', '));
const rv = C.reviewBlock ? C.reviewBlock({ programId: pid }) : null;
if (rv) clean('block review', rv);

console.log(`month: ${passes} checks passed, ${fails} failed`);
if (fails) { console.log('MONTH FAIL'); process.exit(1); }
console.log('MONTH PASS');
