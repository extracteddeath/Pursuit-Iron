/* Pursuit Iron native app core.
 *
 * The native Android shell renders view models produced here and sends user actions back. All
 * training logic stays in JavaScript, calling the same canonical engine (modules/engine-api.js)
 * the PWA calls, in the same order. The persisted store is the PWA's `wpb:v1` object, carried
 * through untouched: fields this core does not understand are preserved byte-for-byte, so a store
 * moved from the PWA comes back out unchanged.
 *
 * Every flow below mirrors an App.js passage; the passage is named beside it. When App.js changes
 * one of these flows, change the mirror in the same release (native/core/test pins the behaviour).
 */
import * as E from '../../../modules/engine-api.js';
import { withProgressionChoices, repeatedNote, refreshProgressionSets } from '../../../modules/session-tools.js';
import { INJURY_AREAS, INJURY_IMPACTS, injuryParts, injuryTitle, injuryMatchesExercise, normalizeInjuryLog, mergeInjuryReports } from '../../../modules/injury-tools.js';
import { adjustInjuredExercises } from '../../../modules/workout-context.js';

const { EX_BY_ID, EXERCISES } = E;
const now = () => Date.now();

/* ------------------------------------------------------------------ state */
const S = { store: null, live: null, undo: null, dirty: { store: false, live: false } };

function markStore() { S.dirty.store = true; }

/* App.js stampedSetter: every write to a syncable list stamps the records it changed with updatedAt
   and tombstones the ids it removed, so a later merge (backup import, the web app) keeps the newer
   copy and does not resurrect deletions. A record restored by undo is a new write and wins over its
   own tombstone, exactly as on the web app. */
function setList(key, next) {
    const prev = Array.isArray(S.store[key]) ? S.store[key] : [];
    const t = now(), before = new Map(prev.map((r) => [r && r.id, r])), gone = new Set(prev.map((r) => r && r.id));
    next.forEach((r) => { if (r) gone.delete(r.id); });
    if (gone.size) { const tombs = { ...(S.store.tombs || {}) }; gone.forEach((id) => { if (id != null) tombs[id] = t; }); S.store.tombs = tombs; }
    S.store[key] = next.map((r) => (r && r.id != null && before.get(r.id) !== r ? { ...r, updatedAt: t } : r));
    markStore();
}
function markLive() { S.dirty.live = true; }

/* App.js applies these on every render; the engine reads them as module-level configuration. */
function applyGlobals() {
    const st = S.store;
    const minInc = st.minInc || { v: 0, unit: null };
    E.setLoadInc(minInc.v, minInc.unit);
    E.setRestScaleGlobal(Number(st.restScale) || 1);
    E.setAvailPlates(st.plates || { kg: [...E.PLATES.kg], lb: [...E.PLATES.lb] });
    const g = E.normalizeGyms(st.gyms, st.activeGymId);
    const gym = E.gymById(g.gyms, g.activeGymId);
    E.setGymLimits(gym); E.setGymBarWeights(gym);
}

function unit() { return S.store.unit || 'kg'; }
function saved() { return Array.isArray(S.store.saved) ? S.store.saved : []; }
function history() { return Array.isArray(S.store.history) ? S.store.history : []; }
function perf() { return S.store.perf || {}; }
function cycles() { return Array.isArray(S.store.cycles) ? S.store.cycles : []; }
function injuries() { return normalizeInjuryLog(S.store.injuryLog); }
function programById(id) { return saved().find((p) => p.id === id) || null; }

/* App.js registerCustomEx / syncCustomRegistry: custom exercises join the live pool so every screen,
   swap and prescription can use them; ones no longer in the store leave it. */
function syncCustomRegistry() {
    const next = (Array.isArray(S.store.custom) ? S.store.custom : []).filter((ex) => ex && ex.id != null);
    const ids = new Set(next.map((ex) => ex.id));
    (S.customIds || []).forEach((id) => {
        if (!ids.has(id) && (EX_BY_ID[id]?.custom || String(id).startsWith('custom-'))) {
            delete EX_BY_ID[id]; const i = EXERCISES.findIndex((x) => x.id === id); if (i >= 0) EXERCISES.splice(i, 1);
        }
    });
    next.forEach((ex) => {
        const normalized = { ...ex, equip: Array.isArray(ex.equip) ? ex.equip : [] };
        const prior = EX_BY_ID[ex.id];
        if (!prior) { EX_BY_ID[ex.id] = normalized; EXERCISES.push(normalized); }
        else if (prior.custom || String(ex.id).startsWith('custom-')) { EX_BY_ID[ex.id] = normalized; const i = EXERCISES.findIndex((x) => x.id === ex.id); if (i >= 0) EXERCISES[i] = normalized; }
    });
    S.customIds = [...ids];
}

/* ------------------------------------------------------------------ init / persistence */
export function init({ store = null, live = null } = {}) {
    let parsed = null;
    try { parsed = store ? E.parseStoredData(store) : null; } catch { parsed = null; }
    S.store = parsed || E.parseStoredData('{}') || { v: E.STORE_VERSION, saved: [], history: [], perf: {} };
    repairNativeLogs();
    syncCustomRegistry();
    S.live = null;
    if (live) {
        try {
            const l = JSON.parse(live);
            // App.js: an already-logged session must never resume (liveMatchRef / sessionSnapshotStatus).
            if (l && l.sessionId && !history().some((h) => h.id === l.sessionId) && liveProgram(l)) S.live = normalizeLive(l);
        } catch { S.live = null; }
    }
    applyGlobals();
    return { ok: true, empty: saved().length === 0 && history().length === 0, engine: E.ENGINE_VERSION, storeVersion: S.store.v };
}

/* Builds before this one wrote bodyweight as { date: 'YYYY-MM-DD', v } and measurements with ISO
   dates. The web app reads { date: ms, w }, so those rows were invisible to it; rewrite them once. */
function repairNativeLogs() {
    const ms = (d) => (typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d) ? new Date(d + 'T12:00:00').getTime() : d);
    if (Array.isArray(S.store.bwLog) && S.store.bwLog.some((e) => e && e.w == null && e.v != null || typeof e?.date === 'string')) {
        S.store.bwLog = S.store.bwLog.map((e) => (e && e.w == null && e.v != null ? (({ v, ...rest }) => ({ ...rest, w: v }))(e) : e))
            .map((e) => (e ? { ...e, date: ms(e.date) } : e)).sort((a, b) => a.date - b.date);
        markStore();
    }
    const m = S.store.measurements;
    if (m && typeof m === 'object' && Object.values(m).some((arr) => Array.isArray(arr) && arr.some((e) => typeof e?.date === 'string'))) {
        S.store.measurements = Object.fromEntries(Object.entries(m).map(([k, arr]) => [k, Array.isArray(arr) ? arr.map((e) => (e ? { ...e, date: ms(e.date) } : e)).sort((a, b) => a.date - b.date) : arr]));
        markStore();
    }
}

/* The program a live session belongs to: the library's, or the quick workout's own copy. */
function liveProgram(l) { return programById(l?.programId) || (l?.quickProgram && l.quickProgram.id === l.programId ? l.quickProgram : null); }
/* The web app's live snapshot keeps the rest clock as restEndMs/restMax and the recovery ratings as
   fbPump/fbSore; builds before this one kept `rest` and `feedback`. Normalise to the web app's names. */
function normalizeLive(l) {
    if (!l || typeof l !== 'object') return l;
    if (l.feedback && !l.fbPump && !l.fbSore) {
        l.fbPump = {}; l.fbSore = {};
        Object.entries(l.feedback).forEach(([part, v]) => { if (v?.pump) l.fbPump[part] = v.pump; if (v?.sore) l.fbSore[part] = v.sore; });
    }
    delete l.feedback;
    if (!l.rest && Number(l.restEndMs) > now() && Number(l.restMax) > 0) l.rest = { seconds: Number(l.restMax), startedAt: Number(l.restEndMs) - Number(l.restMax) * 1000, kind: 'rest' };
    l.fbPump = l.fbPump || {}; l.fbSore = l.fbSore || {};
    return l;
}

/* App.js persistAppStore: savedAt stamps the copy for merges; tombstones older than 180 days drop. */
export function exportStore() {
    const cutoff = now() - 1000 * 60 * 60 * 24 * 180;
    const tombs = {};
    Object.entries(S.store.tombs || {}).forEach(([id, t]) => { if (t >= cutoff) tombs[id] = t; });
    S.dirty.store = false;
    return JSON.stringify({ ...S.store, v: E.STORE_VERSION, savedAt: now(), tombs });
}
export function exportLive() { S.dirty.live = false; return S.live ? JSON.stringify(S.live) : ''; }
export function dirty() { return { ...S.dirty }; }

/* Import a backup (or the PWA handoff) with the app's own never-lose-a-workout merge. */
export function importStore({ text, mode = 'merge', csvWeightUnit = null, preview = false }) {
    const raw = String(text || '');
    if (raw.length > 20 * 1024 * 1024) return { ok: false, error: 'Backup exceeds the 20 MB restore limit.' };
    let incoming = null, csv = null;
    // App.js importData: a Strong or Hevy CSV becomes a history-only store and takes the same merge.
    if (mode !== 'replace' && E.detectWorkoutCsv(raw)) {
        const catalog = [...new Map(EXERCISES.map((ex) => [ex.id, ex])).values()];
        const converted = E.workoutCsvToStore(raw, { catalog, unit: unit(), weightUnit: csvWeightUnit });
        if (!converted.ok) return { ok: false, error: converted.msg };
        incoming = converted.store; csv = converted.summary;
        if (preview) return { ok: true, preview: true, csv };
    } else {
        try { incoming = E.parseStoredData(raw); } catch { incoming = null; }
    }
    if (!incoming) return { ok: false, error: 'That file is not a Pursuit Iron backup.' };
    const before = { sessions: history().length, programs: saved().length };
    if (!Array.isArray(incoming.saved) && !Array.isArray(incoming.history)) return { ok: false, error: 'No recognizable backup found.' };
    let stats = null;
    if (mode === 'replace' || (before.sessions === 0 && before.programs === 0 && !csv)) S.store = incoming;
    else {
        // App.js import: MERGE, never replace; the live copy is the local side and is newer.
        const merged = E.mergeStores({ ...S.store, savedAt: now() }, { ...incoming, savedAt: incoming.savedAt || incoming.exportedAt || 0 });
        S.store = merged.data; stats = merged.stats;
    }
    syncCustomRegistry(); applyGlobals(); markStore();
    return { ok: true, csv, sessions: history().length, programs: saved().length, stats, added: { sessions: history().length - before.sessions, programs: saved().length - before.programs } };
}

/* ------------------------------------------------------------------ helpers */
function exName(id) { return EX_BY_ID[id]?.name || id; }
function fmtW(v) { const n = Number(v); return Number.isFinite(n) && n > 0 ? String(Math.round(n * 100) / 100) : ''; }
function isWork(s) { return !s.warm && !s.sub; }

function upNext() {
    // App.js upNext: pinned → most-recently-trained → live cycle block → newest.
    if (!saved().length) return null;
    const active = E.pickActiveProgram(saved(), history(), cycles(), S.store.pinnedId);
    if (!active || !active.days?.length) return null;
    const c = E.nextSessionCursor(active, history());
    return { program: active, day: active.days[c.dayIndex], dayIndex: c.dayIndex, weekIndex: c.weekIndex || 1, scheduled: c.scheduled };
}

function readiness() {
    let rows = [];
    try { rows = E.muscleRecovery(history()) || []; } catch { rows = []; }
    return rows.map((r) => ({ part: r.part, label: E.PART_LABEL?.[r.part] || r.part, readiness: Math.round(r.readiness ?? 100) }));
}

/* The design's row subtitle: movement pattern · main equipment ("Horizontal push · Barbell"). */
const EQUIP_LABEL = { ...Object.fromEntries((E.EQUIPMENT || []).map((x) => [x.id, x.label])), barbell: 'Barbell', dumbbell: 'Dumbbell', cable: 'Cable', machine: 'Machine', kettlebell: 'Kettlebell', band: 'Band', pullup: 'Bodyweight', bodyweight: 'Bodyweight' };
function exSub(ex) {
    if (!ex) return '';
    const pat = ex.pattern ? ex.pattern.replace(/[-_]/g, ' ').replace(/^./, (c) => c.toUpperCase()) : (E.PART_LABEL?.[ex.part] || '');
    const eq = (ex.equip || []).find((x) => !['bench', 'rack'].includes(x)) || (ex.equip || [])[0];
    return [pat, eq ? (EQUIP_LABEL[eq] || eq) : 'Bodyweight'].filter(Boolean).join(' · ');
}
function dayPreview(program, day, weekIndex) {
    return day.exercises.map((_id, slot) => {
        const id = E.exerciseAt(program, day, slot, weekIndex);
        let cell = null; try { cell = E.computeCell(program, day, id, slot, weekIndex); } catch { cell = null; }
        return { id, name: exName(id), part: EX_BY_ID[id]?.part || '', primary: slot === day.primaryIndex, sets: cell?.sets ?? null, reps: cell?.reps ?? null, rir: cell?.rir ?? null, sub: exSub(EX_BY_ID[id]) };
    });
}

/* ------------------------------------------------------------------ Today */
export function home() {
    const nx = upNext();
    const H = history();
    const weekStart = (() => { const d = new Date(); const day = (d.getDay() + 6) % 7; d.setHours(0, 0, 0, 0); return d.getTime() - day * 86400000; })();
    const thisWeek = H.filter((h) => Number(h.date) >= weekStart);
    let est = null; if (nx) { try { est = E.estimateMinutes(nx.program, nx.day, nx.weekIndex); } catch { est = null; } }
    const rd = readiness();
    const partsToday = nx ? [...new Set(nx.day.exercises.map((id) => EX_BY_ID[id]?.part).filter(Boolean))] : [];
    const todayReady = partsToday.length ? Math.round(partsToday.reduce((s, p) => s + (rd.find((r) => r.part === p)?.readiness ?? 100), 0) / partsToday.length) : 100;
    return {
        unit: unit(), empty: !saved().length,
        next: nx ? {
            programId: nx.program.id, programName: nx.program.name, dayId: nx.day.id, dayLabel: nx.day.label,
            weekIndex: nx.weekIndex, weeksTotal: E.weeksOf(nx.program), endless: !!nx.program.config?.endless, deloadWeek: nx.weekIndex > E.weeksOf(nx.program), minutes: est, exercises: dayPreview(nx.program, nx.day, nx.weekIndex)
        } : null,
        week: { sessions: thisWeek.length, planned: nx ? nx.program.days.length : 0, volume: Math.round(thisWeek.reduce((s, h) => s + (Number(h.volume) || 0), 0)) },
        readiness: { today: todayReady, parts: rd },
        injuries: injuries().filter((i) => !i.resolvedAt).map((i) => ({ id: i.id, title: injuryTitle(i, E.PART_LABEL), impact: i.impact })),
        live: S.live ? liveSummary() : null,
        needsTrainingMax: nx && nx.program.config?.percentScheme ? mainLifts(nx.program).filter((id) => !(E.trainingMaxForUnit(nx.program, id, unit()) > 0)).length : 0,
        cycle: (() => { const ci = liveCycleInfo(); return ci ? { id: ci.cycle.id, name: ci.cycle.name || 'Training cycle', blockIdx: ci.blockIdx, blocks: (ci.cycle.blockMeta || []).map((b) => b.label || 'Block'),
            weekIndex: ci.weekIndex, weeksTotal: ci.weeksTotal, isDeload: ci.isDeload, dayIndex: ci.dayIndex, daysPerWeek: ci.daysPerWeek } : null; })(),
        recent: H.slice(0, 7).map(historyRow),
        reentry: nx && !S.live ? reentryOffer(H) : null,
        restDay: nx && !S.live ? restDayView(nx, H, rd) : null,
        ...todayDesign(nx, H, weekStart),
        ...todayInsights(nx, H)
    };
}
/* The design's welcome back (scenario "Back after a break"): after REENTRY_DAYS without training, offer
   one easier session before the plan resumes — two working sets per lift at 90% of today's loads.
   It is today's own prescription scaled down, the same path as the readiness check, so nothing about
   the program changes and the next session prescribes from what was logged, as usual. */
/* The design's Today: the week streak (App.js: consecutive 7-day windows with a workout, so rest
   days a program builds in never break it), the block's progress, a Monday-first week strip, this
   week's days with their status, and the coach's notes for the next session (App.js coachFacts +
   workoutPlanCoachFact, the facts the web workout shows). */
function weekStreak(H) {
    const now = Date.now(), DAY = 86400000;
    const has = (a, b) => H.some((h) => Number(h.date) > now - b * DAY && Number(h.date) <= now - a * DAY);
    let n = 0; for (let w = 0; w < 200 && has(w * 7, (w + 1) * 7); w++) n++;
    return n;
}
function todayDesign(nx, H, weekStart) {
    const DAY = 86400000;
    const streak = weekStreak(H);
    const sched = nx?.program.schedule || {};
    const today0 = new Date(); today0.setHours(0, 0, 0, 0);
    const strip = Array.from({ length: 7 }, (_, i) => {
        const t = weekStart + i * DAY, d = new Date(t), end = t + DAY;
        const done = H.some((h) => Number(h.date) >= t && Number(h.date) < end);
        return { dow: 'MTWTFSS'[i], date: d.getDate(), done, today: t === today0.getTime(), past: t < today0.getTime(), scheduled: !!sched[d.getDay()] };
    });
    let block = null, days = [], coach = [];
    if (nx) {
        const p = nx.program, weeks = E.weeksOf(p);
        block = { weekIndex: nx.weekIndex, weeksTotal: weeks, deload: !!p.config?.deload, endless: !!p.config?.endless, inDeload: nx.weekIndex > weeks,
            goal: GOAL_LABEL[p.config?.goal] || null, phase: (() => { try { return String(E.phaseFor(p, nx.weekIndex) || '').replace(/_/g, ' ') || null; } catch { return null; } })() };
        const doneIds = new Set(H.filter((h) => h.programId === p.id && Number(h.date) >= weekStart).map((h) => h.dayId));
        const wdOf = (id) => { const k = Object.keys(sched).find((x) => sched[x] === id); return k == null ? null : Number(k); };
        days = p.days.map((d) => {
            let sets = 0; try { sets = dayPreview(p, d, nx.weekIndex).reduce((a, e) => a + (Number(e.sets) || 0), 0); } catch { sets = 0; }
            const wd = wdOf(d.id);
            return { id: d.id, label: d.label, exercises: d.exercises.length, sets, weekday: wd == null ? null : ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'][wd],
                status: doneIds.has(d.id) ? 'done' : d.id === nx.day.id ? 'today' : 'planned' };
        });
        try {
            const lm = E.buildLifterModel(H, perf(), p);
            const facts = E.coachFacts(lm, nx.day, H) || [];
            const plan = E.workoutPlanCoachFact(p, nx.day, nx.weekIndex);
            coach = (plan ? [...facts, { ...plan, key: 'session-plan', priority: 20 }] : facts)
                .slice().sort((a, b) => (b.priority || 0) - (a.priority || 0))
                .map((f) => ({ key: f.key || f.title, icon: f.icon || 'plan', title: f.title || f.short || '', text: f.text || '', evidence: f.evidence || null }));
        } catch { coach = []; }
    }
    return { streakWeeks: streak, weekStrip: strip, block, weekDays: days, coach };
}
/* The design's rest day: a program with a weekly schedule has nothing on today's weekday, and nothing
   was logged today. Today then leads with recovery and when the next session is, instead of a start
   button for a day the lifter planned to rest. Training anyway stays one tap away. */
const WEEKDAY = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
function restDayView(nx, H, rd, at = new Date()) {
    const sched = nx.program.schedule || {};
    if (!Object.values(sched).some(Boolean)) return null;
    const wd = at.getDay(); if (sched[wd]) return null;
    const midnight = new Date(at); midnight.setHours(0, 0, 0, 0);
    if (H.some((h) => Number(h.date) >= midnight.getTime())) return null;
    let inDays = null; for (let k = 1; k <= 7; k++) if (sched[(wd + k) % 7]) { inDays = k; break; }
    const nextWd = inDays == null ? null : (wd + inDays) % 7;
    const trained = rd.filter((r) => MAJOR_PARTS.includes(r.part) || r.readiness < 100);
    const rows = [...trained].sort((a, b) => a.readiness - b.readiness).slice(0, 6);
    const sore = rows.filter((r) => r.readiness < 70).map((r) => r.label);
    return { nextLabel: nx.day.label, inDays, nextWeekday: nextWd == null ? null : WEEKDAY[nextWd],
        when: inDays === 1 ? 'tomorrow' : inDays == null ? null : 'on ' + WEEKDAY[nextWd], recovering: sore, rows };
}
export const REENTRY_DAYS = 10, REENTRY_FACTOR = 0.9, REENTRY_SETS = 2;
function reentryOffer(H) {
    const last = H.length ? Number(H[0].date) : 0;
    if (!(last > 0)) return null;
    const daysOff = Math.floor((Date.now() - last) / 86400000);
    return daysOff >= REENTRY_DAYS ? { daysOff, lastDate: last, factor: REENTRY_FACTOR, sets: REENTRY_SETS } : null;
}
export function startReentry() {
    const nx = upNext(); if (!nx) return { error: 'No program yet.' };
    const offer = reentryOffer(history()); if (!offer) return { error: 'No break to come back from.' };
    const v = startProgramDay(nx.program, nx.day, nx.weekIndex, { reentry: { daysOff: offer.daysOff, factor: REENTRY_FACTOR, sets: REENTRY_SETS } });
    if (v.error) return v;
    const l = S.live, ctx = sessionContext(l);
    // Keep the warm-ups and the first REENTRY_SETS working sets (with any drop or myo minis hanging off them).
    const trimmed = (l.data || []).map((e) => {
        let w = 0; const sets = [];
        for (const x of e.sets) { if (isWork(x)) w++; if (w <= REENTRY_SETS || x.warm) sets.push(x); }
        return { ...e, sets };
    });
    l.data = normalizeData(ctx, ACTIONS.readiness({ label: 'Re-entry', factor: REENTRY_FACTOR }, ctx, trimmed));
    markLive();
    return session();
}

/* App.js upNext freshness + alt, DeloadAdviceCard, MuscleRecoveryCard rest advice, homeCardPlan, WeeklyRecapCard. */
const MAJOR_PARTS = ['chest', 'lats', 'upper_back', 'shoulders', 'quads', 'hamstrings', 'glutes', 'biceps', 'triceps'];
function freshnessFor(program, cursorIdx, lastIdx, H) {
    let rec = []; try { rec = E.muscleRecovery(H) || []; } catch { rec = []; }
    const readyByPart = Object.fromEntries(rec.map((r) => [r.part, r.readiness]));
    const dayScore = (day) => {
        let sum = 0, wsum = 0;
        day.exercises.forEach((id) => {
            const ex = EX_BY_ID[id]; if (!ex) return;
            sum += (readyByPart[ex.part] ?? 100); wsum += 1;
            E.secondaryOf(ex).forEach(([pp, f]) => { sum += (readyByPart[pp] ?? 100) * f * 0.5; wsum += f * 0.5; });
        });
        return wsum ? sum / wsum : 100;
    };
    const schedScore = dayScore(program.days[cursorIdx]);
    let alt = null, best = schedScore + 12;
    program.days.forEach((d, i) => { if (i === cursorIdx || i === lastIdx) return; const sc = dayScore(d); if (sc > best) { best = sc; alt = { day: d, dayIndex: i, score: Math.round(sc) }; } });
    return { freshness: Math.round(schedScore), alt, rec };
}
function todayInsights(nx, H) {
    const u = unit();
    let cur = null; if (nx) { try { cur = E.nextSessionCursor(nx.program, H); } catch { cur = null; } }
    const fr = nx ? freshnessFor(nx.program, nx.dayIndex, cur?.lastIdx, H) : { freshness: null, alt: null, rec: (() => { try { return E.muscleRecovery(H) || []; } catch { return []; } })() };
    const majorTrained = fr.rec.filter((r) => MAJOR_PARTS.includes(r.part) && r.daysSince != null);
    const avgReady = majorTrained.length ? majorTrained.reduce((x, r) => x + r.readiness, 0) / majorTrained.length : 100;
    const fatigued = majorTrained.filter((r) => r.readiness < 50).length;
    const restAdvised = majorTrained.length >= 3 && (avgReady < 50 || fatigued >= Math.ceil(majorTrained.length * 0.6));
    let recap = null; try { recap = H.length ? E.weeklyRecap(H, u) : null; } catch { recap = null; }
    let deload = null; try { deload = H.length ? E.deloadAdvice(H) : null; } catch { deload = null; }
    const needsBw = !(parseFloat(S.store.bodyweight) > 0);
    const insights = [];
    if (H.length) {
        const ranked = [['recovery', restAdvised], ['strength', (recap?.prs || 0) > 0 || needsBw], ['recap', (recap?.count || 0) > 0], ['recovery', Date.now() - H[0].date < 2 * 86400000]];
        for (const [id, news] of ranked) { if (!news || insights.includes(id)) continue; if (insights.length >= 2) break; insights.push(id); }
    }
    const ready = fr.rec.filter((r) => r.readiness >= 85 && MAJOR_PARTS.includes(r.part)).map((r) => E.PART_LABEL[r.part]).slice(0, 4);
    return {
        freshness: fr.freshness,
        alt: fr.alt ? { dayId: fr.alt.day.id, dayLabel: fr.alt.day.label, score: fr.alt.score } : null,
        deload: deload ? { reasons: (deload.reasons || []).map(String) } : null,
        canDeload: !!nx && !S.live, canLight: !!nx && !S.live,
        recovery: { restAdvised, ready, parts: fr.rec.filter((r) => r.daysSince != null).sort((a, b) => a.readiness - b.readiness).slice(0, 8)
            .map((r) => ({ part: r.part, label: E.PART_LABEL[r.part] || r.part, readiness: Math.round(r.readiness), status: r.status || null, daysSince: r.daysSince })) },
        recap: recap && recap.count ? { count: recap.count, sets: recap.sets, vol: recap.vol, prevCount: recap.prevCount, prevSets: recap.prevSets, prevVol: recap.prevVol, prs: recap.prs,
            topMuscle: recap.topMuscle ? { part: recap.topMuscle[0], label: E.PART_LABEL[recap.topMuscle[0]] || recap.topMuscle[0], sets: recap.topMuscle[1] } : null } : null,
        insights, needsBodyweight: needsBw
    };
}
function startProgramDay(program, day, weekIndex, extra = {}) {
    if (S.live) return { error: 'Finish or discard the workout in progress first.' };
    const l = { schemaVersion: 2, sessionId: E.uid(), programId: program.id, dayId: day.id, weekIndex, startedAt: now(), elapsedMs: 0, exIdx: 0,
        readiness: null, injuryReports: [], sessionProgressions: {}, sessionNote: '', sessionNoteRepeat: 'none', fbPump: {}, fbSore: {}, rest: null, ...extra };
    S.live = l;
    const ctx = sessionContext(l);
    if (!ctx) { S.live = null; return { error: 'That workout day no longer exists.' }; }
    l.data = freshData(ctx).data; l.dayExSig = ctx.day.exercises.join('|');
    markLive();
    return session();
}
/* App.js startUpNextAlt: the fresher day, same week. */
export function startAlt() {
    const nx = upNext(); if (!nx) return { error: 'No program yet.' };
    let cur = null; try { cur = E.nextSessionCursor(nx.program, history()); } catch { cur = null; }
    const fr = freshnessFor(nx.program, nx.dayIndex, cur?.lastIdx, history());
    if (!fr.alt) return { error: 'No fresher day right now.' };
    return startProgramDay(nx.program, fr.alt.day, nx.weekIndex);
}
/* App.js startDeload: this week's day at deload intensity (the week after the block). */
export function startDeload() {
    const nx = upNext(); if (!nx) return { error: 'No program yet.' };
    return startProgramDay(nx.program, nx.day, E.weeksOf(nx.program) + 1, nx.program.config?.deload ? {} : { forceDeload: true });
}
/* App.js startLight: the freshest day at deload intensity. */
export function startLight() {
    const nx = upNext(); if (!nx) return { error: 'No program yet.' };
    let cur = null; try { cur = E.nextSessionCursor(nx.program, history()); } catch { cur = null; }
    const fr = freshnessFor(nx.program, nx.dayIndex, cur?.lastIdx, history());
    return startProgramDay(nx.program, fr.alt?.day || nx.day, E.weeksOf({ ...nx.program, config: { ...nx.program.config, deload: true } }) + 1, { forceDeload: true });
}

/* ------------------------------------------------------------------ Progress: one lift (App.js lift detail + StrengthComparisonCard) */
export function liftDetail({ id }) {
    const ex = EX_BY_ID[id]; if (!ex) return null;
    const H = history(), u = unit();
    let t = null; try { t = (E.exerciseTrends(H) || []).find((x) => x.id === id) || null; } catch { t = null; }
    const bw = parseFloat(S.store.bodyweight), sex = S.store.sex || null, a = age();
    let lvl = null; try { lvl = t && bw > 0 && sex ? E.strengthLevel(id, t.prBest, t.unit, bw, u, sex, a) : null; } catch { lvl = null; }
    const conv = (v, from) => (from && from !== u ? (u === 'lb' ? v * 2.20462 : v / 2.20462) : v);
    return {
        id, name: ex.name, part: ex.part, partLabel: E.PART_LABEL[ex.part] || ex.part, unit: u, goal: Number(S.store.goals?.[id]) || null,
        sessions: t ? t.sessionsCount : 0,
        best: t ? { e1rm: Math.round(conv(t.prBest, t.unit) * 10) / 10, topWeight: Math.round(conv(t.prTopW, t.unit) * 10) / 10 } : null,
        delta: t ? Math.round(conv(t.delta, t.unit) * 10) / 10 : 0,
        points: t ? t.sessions.map((x) => ({ date: x.date, e1rm: Math.round(conv(x.best, x.unit) * 10) / 10, top: x.top ? `${fmtW(conv(x.top.w, x.unit))}×${x.top.r ?? ''}` : '' })) : [],
        recent: t ? t.sessions.slice(-8).reverse().map((x) => ({ date: x.date, sets: x.sets.map((st) => `${fmtW(conv(st.w, x.unit))}×${st.r ?? ''}`) })) : [],
        level: lvl ? { level: lvl.level, idx: lvl.idx, within: Math.round(lvl.within * 100) / 100, next: lvl.idx < E.STD_LEVELS.length - 1 ? E.STD_LEVELS[lvl.idx + 1] : null,
            add: lvl.idx < lvl.thresh.length ? Math.max(0, Math.round((lvl.thresh[lvl.idx] - t.prBest) * 10) / 10) : 0 } : null,
        levels: E.STD_LEVELS
    };
}
export function strengthStandards() {
    const H = history(), u = unit(), bw = parseFloat(S.store.bodyweight), sex = S.store.sex || null, a = age();
    const ready = bw > 0 && !!sex;
    let trends = []; try { trends = E.exerciseTrends(H) || []; } catch { trends = []; }
    const rows = !ready ? [] : trends.map((t) => { let l = null; try { l = E.strengthLevel(t.id, t.prBest, t.unit, bw, u, sex, a); } catch { l = null; } return l ? { t, l } : null; })
        .filter(Boolean).sort((x, y) => (y.l.idx - x.l.idx) || (y.l.within - x.l.within))
        .map(({ t, l }) => ({ id: t.id, name: t.name, level: l.level, idx: l.idx, within: Math.round(l.within * 100) / 100,
            next: l.idx < E.STD_LEVELS.length - 1 ? E.STD_LEVELS[l.idx + 1] : null, add: l.idx < l.thresh.length ? Math.max(0, Math.round((l.thresh[l.idx] - t.prBest) * 10) / 10) : 0, unit: t.unit }));
    return { ready, missing: !(bw > 0) ? 'bodyweight' : !sex ? 'sex' : null, cohort: ready ? `${sex === 'female' ? 'women' : 'men'}${a > 0 ? `, age ${a}` : ''}, ${Math.round(bw)}${u}` : '',
        rows, levels: E.STD_LEVELS };
}

/* ------------------------------------------------------------------ correct a logged workout (App.js HistoryEditSheet + commitHistoryCorrection) */
export function historyEditDraft({ id }) {
    const h = history().find((x) => x.id === id); if (!h) return null;
    return { id, dayLabel: h.dayLabel || 'Workout', date: h.date, durationMin: h.durationMin ?? null, unit: h.unit || unit(),
        lifts: Object.entries(h.perf || {}).map(([eid, p]) => ({ id: eid, name: exName(eid),
            sets: (Array.isArray(p?.sets) && p.sets.length ? p.sets : (p?.reps != null ? [{ w: p.weight ?? 0, r: p.reps }] : [])).map((st) => ({ w: st.w ?? '', r: st.r ?? '', rir: st.rir ?? '', sub: !!st.sub })) })) };
}
export function editHistory({ id, date, durationMin, lifts }) {
    const H = history(), original = H.find((x) => x.id === id); if (!original || !Array.isArray(lifts)) return { ok: false };
    const perfOut = {};
    lifts.forEach((lf) => {
        const p = original.perf?.[lf.id]; if (!p || !Array.isArray(lf.sets) || !lf.sets.length) return;
        const srcSets = Array.isArray(p.sets) && p.sets.length ? p.sets : [];
        perfOut[lf.id] = { ...p, sets: lf.sets.map((st, i) => ({ ...(srcSets[i] || {}), w: st.w === '' ? '' : String(st.w), r: st.r === '' ? '' : String(st.r), rir: st.rir === '' || st.rir == null ? '' : String(st.rir), ...(st.sub ? { sub: true } : {}) })) };
    });
    const draft = { ...original, perf: perfOut, ...(Number.isFinite(Number(date)) && Number(date) > 0 ? { date: Number(date) } : {}), ...(durationMin != null ? { durationMin: String(durationMin) } : {}) };
    const clean = E.normalizeEditedHistoryEntry(original, draft);
    S.undo = { history: H, perf: perf() };
    S.store.perf = E.perfAfterHistoryReplace(H, perf(), id, clean);
    setList('history', H.map((x) => (x.id === id ? clean : x)).sort((a, b) => (Number(b?.date) || 0) - (Number(a?.date) || 0)));
    return { ok: true, label: clean.dayLabel || 'workout' };
}

/* ------------------------------------------------------------------ share card data (App.js shareSession → drawRecapCard) */
export function shareCard({ id }) {
    const H = history(), entry = H.find((x) => x.id === id); if (!entry) return null;
    const u = entry.unit || 'kg';
    const e1 = (b) => Object.fromEntries(Object.entries(b || {}).map(([k, v]) => [k, v.e1rm]));
    const nowBest = e1(E.liftBests([entry], u));
    const t0 = new Date(entry.date).getTime();
    const prior = e1(E.liftBests(H, u, (h) => h !== entry && new Date(h.date).getTime() < t0));
    const prs = Object.entries(nowBest).filter(([k, v]) => prior[k] != null && v > prior[k] + 1e-9 && EX_BY_ID[k]).sort((a, b) => b[1] - a[1]).map(([k]) => EX_BY_ID[k].name);
    const mainId = Object.keys(nowBest).sort((a, b) => nowBest[b] - nowBest[a])[0];
    const tops = Object.entries(entry.perf || {}).map(([k, p]) => {
        const ex = EX_BY_ID[k]; if (!ex) return null;
        const done = E.setsOf(p).filter((x) => x.w != null && x.r > 0); if (!done.length) return null;
        const top = E.pickTopSet(done, (x) => x.w, (x) => x.r) || done[0];
        return { name: ex.name, set: `${top.w}${u}×${top.r}` };
    }).filter(Boolean);
    return { label: String(entry.dayLabel || 'Workout'), programName: entry.programName || '', date: entry.date, durMin: Number(entry.durationMin) || 0,
        doneSets: entry.setsDone ?? 0, volume: Number(entry.volume || 0), unit: u,
        mainE1rm: mainId && EX_BY_ID[mainId] ? { name: EX_BY_ID[mainId].name, est: Math.round(nowBest[mainId]) } : null, prs, tops };
}

/* ------------------------------------------------------------------ workout session */
/* App.js styleEdit sheet: each style with what it does, Auto naming the style it picks for this lift now
   (resolveStyle with the lift's own choice removed), Linear naming this lift's jump (linearInc). */
function progressionOptions(ctx, e, ex, u) {
    let auto = '';
    try {
        const id = E.resolveStyle({ ...ctx.program, progStyle: undefined }, ex, e.slot === ctx.day.primaryIndex, ctx.wk, ctx.P, ctx.H, ctx.day.id);
        auto = (E.PROG_STYLES?.[id] || 'double progression');
    } catch { auto = 'double progression'; }
    let inc = ''; try { inc = E.linearInc(ex, u) + u; } catch { inc = ''; }
    return { auto, options: [
        { id: 'auto', label: 'Auto (recommended)', detail: 'Picks the best style for this lift — currently ' + auto.toLowerCase() + '.' },
        { id: 'dynamic', label: 'Dynamic double', detail: 'True per-set DDP — each set advances independently. Top the rep ceiling on set 1 and its weight goes up next session even if set 2 didn\'t. Fastest per-set progression.' },
        { id: 'double', label: 'Double progression', detail: 'Build reps anywhere in the range, then add load when you top it. Simple and effective for most lifts.' },
        { id: 'ladder', label: 'Rep ladder', detail: 'One shared rep target that climbs each session (e.g. 10 → 12 → 15), then the load jumps and the target resets. A structured double-progression hybrid with a crisp weekly goal.' },
        { id: 'wave', label: 'Wave loading', detail: 'Rotates heavy / medium / light sessions (lo→mid→hi reps) within the range, then the load jumps. Classic strength periodization.' },
        { id: 'linear', label: 'Linear LP', detail: 'Add a fixed ' + (inc || 'jump') + ' every session you hit the bottom of the range. Best for beginners on big compounds.' },
        { id: 'e1rm', label: 'e1RM autoregulation', detail: 'Sets load each session from your estimated 1RM to land at the target RIR. Smoothest day-to-day adjustment.' },
    ].filter((o) => E.PROG_STYLES ? o.id in E.PROG_STYLES : true) };
}
function sessionContext(l) {
    // A quick (freestyle) workout's program is not in the library; it travels with the live session.
    const source = liveProgram(l);
    if (!source) return null;
    E.setExLoadInc(source.exLoadInc || {}); // App.js: per-machine increments come from the open program
    // App.js startDeload / startLight run the program with deload switched on (a synthesized copy).
    const base = l.forceDeload ? { ...source, config: { ...source.config, deload: true } } : source;
    const program = withProgressionChoices(base, l.sessionProgressions || {});
    const rawDay = program.days.find((d) => d.id === l.dayId);
    if (!rawDay) return null;
    const day = rawDay.exercises.every((id) => EX_BY_ID[id]) ? rawDay : { ...rawDay, exercises: rawDay.exercises.filter((id) => EX_BY_ID[id]) };
    const wk = l.weekIndex;
    const H = history(), P = perf();
    const lifterModel = E.buildLifterModel(H, P, program);
    const dayPerf = E.calibratedAnchorPerf(program, day, P, H, wk, lifterModel.effort);
    return { program, day, wk, H, P, dayPerf, lifterModel };
}

// App.js WorkoutSession `data` initializer (fresh path).
function freshData(ctx) {
    const { program, day, wk, H, P, dayPerf } = ctx, u = unit();
    const suggestions = day.exercises.map((_id, slot) => E.sessionSuggestion(program, day, slot, dayPerf, u, wk, H));
    const ids = day.exercises.map((_id, slot) => E.exerciseAt(program, day, slot, wk));
    const fresh = day.exercises.map((_id, slot) => {
        const id = ids[slot];
        const last = H.find((h) => h.programId === program.id && h.dayId === day.id && h.perf?.[id]);
        const n = repeatedNote(last?.perf?.[id]?.note, last?.perf?.[id]?.noteRepeat);
        return { id, slot, sets: E.prescribeSets(program, day, EX_BY_ID[id], slot, wk, u, suggestions[slot], dayPerf, P, H, true),
            note: n.text, noteRepeat: n.repeat, superset: !program.config?.noSupersets && !!program.ss?.[`${day.id}:${slot}`],
            restCustom: program.overrides?.[`${day.id}:${slot}`]?.rest || 0 };
    });
    return { data: fresh.map((e) => ({ ...e, sets: E.syncSubSets(E.reconcilePendingRepTargets(e.sets, E.computeCell(program, day, e.id, e.slot, wk)), EX_BY_ID[e.id], u) })), suggestions };
}

// App.js setData wrapper: myo growth and sub-set sync are invariants of every write.
function normalizeData(ctx, data) {
    const { program, day, wk } = ctx;
    return data.map((e) => {
        if (!e || !Array.isArray(e.sets)) return e;
        const cell = E.computeCell(program, day, e.id, e.slot, wk);
        return { ...e, sets: E.syncSubSets(E.growMyoSets(E.reconcilePendingRepTargets(e.sets, cell)), EX_BY_ID[e.id], unit()) };
    });
}

export function startSession({ programId, dayId, weekIndex } = {}) {
    if (S.live && (!programId || (S.live.programId === programId && S.live.dayId === dayId))) return session();
    const nx = upNext();
    const pid = programId || nx?.program.id, did = dayId || nx?.day.id, wk = weekIndex || (programId ? 1 : nx?.weekIndex) || 1;
    if (!pid || !did) return { error: 'No program yet. Build one first.' };
    const l = { schemaVersion: 2, sessionId: E.uid(), programId: pid, dayId: did, weekIndex: wk, startedAt: now(), elapsedMs: 0, exIdx: 0,
        readiness: null, injuryReports: [], sessionProgressions: {}, sessionNote: '', sessionNoteRepeat: 'none', fbPump: {}, fbSore: {}, rest: null };
    S.live = l;
    const ctx = sessionContext(l);
    if (!ctx) { S.live = null; return { error: 'That workout day no longer exists.' }; }
    l.data = freshData(ctx).data;
    l.dayExSig = ctx.day.exercises.join('|');
    markLive();
    return session();
}

function liveSummary() {
    const l = S.live, p = liveProgram(l), day = p?.days.find((d) => d.id === l.dayId);
    const work = (l.data || []).flatMap((e) => e.sets.filter(isWork));
    return { programName: p?.name || '', dayLabel: day?.label || '', done: work.filter((s) => s.done).length, total: work.length, startedAt: l.startedAt };
}

function setView(s, i, ex, prBase, prev = null) {
    const w = parseFloat(s.weight), r = parseInt(s.reps);
    let e1 = null; if (s.done && w > 0 && r > 0 && isWork(s)) e1 = E.e1rm(w, r);
    return {
        i, warm: !!s.warm, sub: !!s.sub, kind: s.kind || (s.warm ? 'warm' : s.sub ? 'sub' : 'work'), done: !!s.done,
        weight: s.weight ?? '', reps: s.reps ?? '', rir: s.actualRIR ?? null, added: !!s.added,
        target: s.target ? { w: s.target.w ?? null, reps: s.target.reps ?? null, rir: s.target.rir ?? null, effort: s.target.rir != null ? E.effortLabel(s.target.rir, S.store.loadMode === 'rpe' ? 'rpe' : 'rir') : null } : null,
        hint: typeof s.hint === 'string' ? s.hint : null,
        userOwned: !!E.userOwnsRuntimeSet?.(s),
        pr: !!(e1 && prBase > 0 && e1 > prBase),
        type: s.typeOverride || (s.target?.failure ? 'failure' : 'standard'),
        prev: prev ? { w: prev.w, r: prev.r ?? null } : null
    };
}

/* App.js supersetSuggestionMeta + supersetSuggestions: low-interference partners for the current lift. */
function supersetMeta(a, b) {
    if (!a || !b || a.part === b.part) return null;
    const push = new Set(['chest', 'shoulders', 'triceps']), pull = new Set(['lats', 'upper_back', 'biceps', 'traps']);
    const post = new Set(['hamstrings', 'glutes', 'lower_back']), small = new Set(['calves', 'abs', 'forearms', 'neck', 'abductors', 'adductors']);
    let score = 0, reason = 'Different muscle groups';
    if ((push.has(a.part) && pull.has(b.part)) || (pull.has(a.part) && push.has(b.part))) { score = 100; reason = 'Push + pull · low interference'; }
    else if ((a.part === 'quads' && post.has(b.part)) || (b.part === 'quads' && post.has(a.part))) { score = 96; reason = 'Quads + posterior chain'; }
    else if (small.has(a.part) || small.has(b.part)) { score = 78; reason = 'Accessory pairing · low interference'; }
    else score = 58;
    const aS = new Set(E.secondaryOf(a).map(([p]) => p)), bS = new Set(E.secondaryOf(b).map(([p]) => p));
    if (aS.has(b.part) || bS.has(a.part)) score -= 24;
    if (a.type === 'compound' && b.type === 'compound') score -= 12;
    return score >= 42 ? { score, reason } : null;
}
function supersetSuggestions(ctx, data, exIdx) {
    const curRow = data[exIdx], cur = EX_BY_ID[curRow?.id];
    if (!curRow || !cur) return [];
    const workOnly = (e) => e.sets.filter((x) => !x.warm && !x.sub);
    const protectedTo = Math.max(ctx.day.primaryIndex ?? -1, ctx.day.t2Index != null ? ctx.day.t2Index : -1);
    if (curRow.slot <= protectedTo) return [];
    const curSets = workOnly(curRow).length;
    return data.map((row, i) => {
        if (i === exIdx || row.slot <= protectedTo) return null;
        if (row.superset || (i > 0 && data[i - 1]?.superset) || workOnly(row).some((x) => x.done)) return null;
        const mate = EX_BY_ID[row.id], meta = supersetMeta(cur, mate); if (!meta) return null;
        const gap = Math.abs(curSets - workOnly(row).length);
        const score = meta.score + (gap === 0 ? 9 : gap === 1 ? 4 : 0) + (i > exIdx ? 3 : 0) - Math.min(8, Math.abs(i - exIdx));
        return { ei: i, name: mate.name, score, reason: meta.reason, sets: workOnly(row).length };
    }).filter(Boolean).sort((a, b) => b.score - a.score).slice(0, 4);
}

export function session() {
    const l = S.live;
    if (!l) return { active: false };
    const ctx = sessionContext(l);
    if (!ctx) return { active: false, error: 'That workout day no longer exists.' };
    const u = unit(), bests = (() => { try { return E.liftBests(ctx.H, u) || {}; } catch { return {}; } })();
    const data = l.data || [];
    let lastPerf = {}; try { lastPerf = E.lastDayPerf(ctx.day, ctx.P, ctx.H, ctx.program) || {}; } catch { lastPerf = {}; }
    const work = (e) => e.sets.filter(isWork);
    const totalSets = data.reduce((s, e) => s + work(e).length, 0);
    const doneSets = data.reduce((s, e) => s + work(e).filter((x) => x.done).length, 0);
    const volume = data.reduce((sum, e) => sum + e.sets.reduce((s, x) => s + (x.done && !x.warm ? (parseFloat(x.weight) || 0) * (parseInt(x.reps) || 0) : 0), 0), 0);
    let est = null; try { est = E.estimateMinutes(ctx.program, ctx.day, ctx.wk); } catch { est = null; }
    return {
        active: true, sessionId: l.sessionId, programId: l.programId, programName: ctx.program.name, dayId: ctx.day.id, dayLabel: ctx.day.label,
        weekIndex: ctx.wk, weeksTotal: E.weeksOf(ctx.program), startedAt: l.startedAt, estimateMin: est, unit: u,
        exIdx: Math.max(0, Math.min(data.length - 1, l.exIdx || 0)), doneSets, totalSets, volume: Math.round(volume),
        exercises: data.map((e, ei) => {
            const ex = EX_BY_ID[e.id] || {};
            const best = Number(bests?.[e.id]?.e1rm ?? bests?.[e.id]?.best ?? 0) || 0;
            // App.js "last" column: the previous workout's set at the same working-set position.
            let workIdx = -1;
            const sets = e.sets.map((s, i) => { const isW = !s.warm && !s.sub; if (isW) workIdx++; return setView(s, i, ex, best, isW ? E.previousWorkoutSet(lastPerf[e.id], workIdx) : null); });
            let restDefault = null; try { restDefault = E.effectiveRest(E.goalForDay(ctx.program, ctx.day), ex, e.slot === ctx.day.primaryIndex, 0, Number(S.store.restScale) || 1); } catch { restDefault = null; }
            const inc = ctx.program.exLoadInc?.[e.id];
            let setup = []; try { setup = E.setupFieldsWithStored(ex, S.store.exSetup?.[e.id]).map((f) => ({ key: f.key, label: f.label, value: S.store.exSetup?.[e.id]?.[f.key] || '' })); } catch { setup = []; }
            return { ei, id: e.id, name: ex.name || e.id, part: ex.part || '', partLabel: E.PART_LABEL?.[ex.part] || ex.part || '', equip: ex.equip || null, slot: e.slot, primary: e.slot === ctx.day.primaryIndex,
                superset: !!e.superset, note: e.note || '', lastNote: ctx.P[e.id]?.note || '', injuryAdjusted: e.injuryAdjusted || null, freestyle: !!e.freestyle,
                done: work(e).length > 0 && work(e).every((s) => s.done), sets,
                hasWarm: e.sets.some((s) => s.warm), hasDrop: e.sets.some((s) => s.sub && s.kind === 'drop'), hasMyo: e.sets.some((s) => s.sub && s.kind === 'myo'),
                restCustom: e.restCustom || 0, restDefault, goal: Number(S.store.goals?.[e.id]) || null,
                step: inc && inc.unit === u ? inc.v : null, stepChoices: u === 'lb' ? [5, 10, 15, 20, 25, 50] : [2.5, 5, 10, 15, 20],
                cues: (() => { try { return E.cuesFor(ex) || []; } catch { return []; } })(), setup,
                stretchFocus: (() => { try { return !!E.isStretchFocus(e.id, 3); } catch { return false; } })(),
                // App.js: assisted lifts carry help as a NEGATIVE load; progress is less help.
                assisted: (() => { try { return !!E.isAssistedEx(ex); } catch { return false; } })(),
                // The design's muscles worked: the engine's own part and secondary shares, biggest first.
                secondary: (() => { try { return (E.secondaryOf(ex) || []).slice().sort((a, b) => b[1] - a[1]).map(([p, f]) => ({ part: p, label: E.PART_LABEL?.[p] || p, share: f })); } catch { return []; } })(),
                progression: { style: E.styleFor(ctx.program, e.id), changed: !!l.sessionProgressions?.[e.id], locked: !!ctx.program.config?.percentScheme,
                    ...progressionOptions(ctx, e, ex, u) } };
        }),
        loadMode: S.store.loadMode === 'rpe' ? 'rpe' : 'rir',
        // App.js warmupRoutine: shown on the first lift until a set is logged.
        warmup: doneSets === 0 ? (() => { try { const m = E.warmupRoutine(ctx.day); if (!m) return null;
            const parts = [...new Set([ctx.day.exercises[ctx.day.primaryIndex], ...ctx.day.exercises].map((id) => EX_BY_ID[id]?.part).filter(Boolean))].slice(0, 3);
            return { moves: m, parts: parts.map((p) => E.PART_LABEL?.[p] || p) }; } catch { return null; } })() : null,
        quick: !!ctx.program.quick, readinessDone: !!l.readiness, readinessLabel: l.readiness?.label || null, reentry: l.reentry || null,
        sessionNote: l.sessionNote || '', sessionNoteRepeat: l.sessionNoteRepeat || 'none',
        progressionStyles: Object.entries(E.PROG_STYLES || {}).map(([id, label]) => ({ id, label })),
        supersetSuggestions: supersetSuggestions(ctx, data, Math.max(0, Math.min(data.length - 1, l.exIdx || 0))),
        rest: l.rest || null,
        injuries: injuries().filter((i) => !i.resolvedAt).map((i) => ({ id: i.id, title: injuryTitle(i, E.PART_LABEL), impact: i.impact,
            exercises: data.filter((e) => injuryMatchesExercise(i, EX_BY_ID[e.id], E.secondaryOf(EX_BY_ID[e.id] || {}))).map((e) => e.id) }))
    };
}

/* App.js computeNextPrefill. */
function nextPrefill(ctx, sets, si, ex, slot) {
    const { program, day, wk } = ctx;
    if (program.config?.progression === 'manual' || program.config?.percentScheme || !ex) return sets;
    const cell = E.computeCell(program, day, ex.id, slot, wk);
    const role = cell.role ?? (slot === day.primaryIndex ? 'primary_strength' : ex.type === 'isolation' ? 'hypertrophy_isolation' : 'hypertrophy_compound');
    return E.applyLiveAutoregulation({ sets, sourceIndex: si, cell: { ...cell, role }, context: { readiness: S.live.readiness, interrupted: false },
        snapLoad: (load) => E.loadableAtOrBelow(ex, load, unit()) }).sets;
}

/* App.js rest selection inside toggleDone. */
function restFor(ctx, e, set, nextPending) {
    const restScale = Number(S.store.restScale) || 1;
    const subRest = (x) => Math.max(8, Math.round((x.kind === 'drop' ? 10 : 15) * restScale));
    if (nextPending && nextPending.sub) return subRest(nextPending);
    if (set.warm) return Math.max(10, Math.round(45 * restScale));
    if (set.sub) return subRest(set);
    return E.effectiveRest(E.goalForDay(ctx.program, ctx.day), EX_BY_ID[e.id], e.slot === ctx.day.primaryIndex, e.restCustom, restScale);
}

const ACTIONS = {
    // App.js toggleDone (superset interleaving and PR flash are UI concerns handled natively).
    toggle({ ei, si }, ctx, d) {
        const e = d[ei], set = e?.sets[si]; if (!set) return d;
        const wasDone = !!set.done, ex = EX_BY_ID[e.id];
        let sets = e.sets.map((s, j) => (j === si ? { ...s, done: !wasDone, hint: undefined, ...(wasDone ? { actualRIR: null } : {}) } : s));
        if (wasDone) {
            sets = E.rollbackLiveAutoregulation(sets, si);
            sets = sets.map((s) => {
                if (!s || s.retuneSrc !== si || s.done || E.userOwnsRuntimeSet(s) || !s.retuneFrom) return s;
                const { retuneFrom, retuneSrc, suggested, autoTuned, hint, over, belowRange, ceiling, ...rest } = s;
                return { ...rest, weight: retuneFrom.weight, reps: retuneFrom.reps, target: retuneFrom.target };
            });
        } else if (isWork(set)) {
            const W = parseFloat(set.weight), R = parseInt(set.reps);
            if (W > 0 && R > 0) sets = nextPrefill(ctx, sets, si, ex, e.slot);
        }
        const out = d.map((x, i) => (i === ei ? { ...x, sets } : x));
        // App.js toggleDone superset round: a working set inside a superset moves on to the partner
        // without a rest; the rest comes after the round. Groups are runs of `superset` flags.
        let gStart = ei, gEnd = ei;
        while (gStart > 0 && d[gStart - 1].superset) gStart--;
        while (gEnd < d.length - 1 && d[gEnd].superset) gEnd++;
        const inGroup = gEnd > gStart, isWorking = !set.warm && !set.sub;
        const memberUndone = (m) => d[m].sets.some((x, j) => !x.warm && !x.sub && !(x.done || (m === ei && j === si)));
        let advanceTo = -1, midRound = false;
        if (!wasDone && inGroup && isWorking) {
            for (let j = ei + 1; j <= gEnd; j++) if (memberUndone(j)) { advanceTo = j; midRound = true; break; }
            if (advanceTo < 0) for (let j = gStart; j <= gEnd; j++) if (memberUndone(j)) { advanceTo = j; break; }
        }
        // App.js: "is there anything left to rest for" includes the myo mini this tick is about to earn.
        const grows = !wasDone && E.growMyoSets(e.sets.map((x, j) => (j === si ? { ...x, done: true } : x))).length > e.sets.length;
        const pendingAnywhere = d.some((x, i) => x.sets.some((y, j) => !((i === ei && j === si) || y.done))) || grows;
        if (advanceTo >= 0 && advanceTo !== ei) S.live.exIdx = advanceTo;
        if (!wasDone && pendingAnywhere && S.store.restAutoStart !== false && !midRound) {
            const nextPending = e.sets.slice(si + 1).find((x) => !x.done);
            S.live.rest = { seconds: restFor(ctx, e, set, nextPending), startedAt: now(), kind: nextPending?.sub ? 'mini' : set.warm ? 'warm' : 'rest' };
        }
        if (wasDone && S.live.rest) S.live.rest = null;
        return out;
    },
    // App.js editSet / editWeight: a done set is history and cannot be edited.
    edit({ ei, si, field, value }, ctx, d) {
        return d.map((e, i) => (i !== ei ? e : { ...e, sets: e.sets.map((s, j) => (j !== si || s.done ? s : { ...s,
            [field]: field === 'reps' ? E.sanitizeRepsInput(value) : E.sanitizeWeightInput(value), auto: false, valueOwner: 'user', hint: undefined })) }));
    },
    bumpReps({ ei, si, delta }, ctx, d) {
        return d.map((e, i) => (i !== ei ? e : { ...e, sets: e.sets.map((s, j) => (j !== si || s.done ? s : { ...s,
            reps: String(E.clamp((parseInt(s.reps) || 0) + delta, 0, 100)), auto: false, valueOwner: 'user', hint: undefined })) }));
    },
    bumpWeight({ ei, si, dir }, ctx, d) {
        return d.map((e, i) => (i !== ei ? e : { ...e, sets: e.sets.map((s, j) => (j !== si || s.done ? s : { ...s,
            weight: String(E.nudgeWorkoutLoad(EX_BY_ID[e.id], unit(), s.weight, dir)), auto: false, valueOwner: 'user', hint: undefined })) }));
    },
    // App.js setActualRIR: re-tapping clears; the next set re-tunes either way.
    rir({ ei, si, rir }, ctx, d) {
        return d.map((e, i) => {
            if (i !== ei) return e;
            const cur = e.sets[si]?.actualRIR, next = cur === rir ? null : rir;
            let sets = e.sets.map((s, j) => (j === si ? { ...s, actualRIR: next } : s));
            sets = nextPrefill(ctx, sets, si, EX_BY_ID[e.id], e.slot);
            return { ...e, sets };
        });
    },
    // App.js addSetRow.
    addSet({ ei }, ctx, d, sug) {
        return d.map((e, i) => {
            if (i !== ei) return e;
            const work = e.sets.filter(isWork);
            const seed = work[work.length - 1] || (sug?.[ei] ? { weight: String(sug[ei].weight), reps: E.repsLow(sug[ei].reps) } : { weight: '', reps: '' });
            return { ...e, sets: [...e.sets, { weight: seed.weight ?? '', reps: seed.reps ?? '', warm: false, done: false, auto: true, valueOwner: 'prescription', added: true, hint: undefined }] };
        });
    },
    // App.js removeSessionAddedSet.
    removeSet({ ei, si }, ctx, d) {
        return d.map((e, i) => {
            if (i !== ei) return e;
            const row = e.sets[si];
            if (!row || row.done || !(row.added || (row.sub && !row.prescribed))) return e;
            return { ...e, sets: e.sets.filter((_, j) => j !== si) };
        });
    },
    select({ ei }, ctx, d) { S.live.exIdx = ei; return d; },
    // Exercise order: reorders rows; logged sets move with their exercise.
    move({ from, to }, ctx, d) {
        if (from === to || from < 0 || to < 0 || from >= d.length || to >= d.length) return d;
        const out = d.slice(); const [row] = out.splice(from, 1); out.splice(to, 0, row);
        if (S.live.exIdx === from) S.live.exIdx = to;
        return out;
    },
    // App.js doSwap, session scope.
    swap({ ei, newId }, ctx, d) {
        if (!EX_BY_ID[newId] || d.some((x, i) => i !== ei && x.id === newId)) return d;
        const slot = d[ei].slot, keepWarm = d[ei].sets.some((s) => s.warm);
        const sets = E.prescribeSets(ctx.program, ctx.day, EX_BY_ID[newId], slot, ctx.wk, unit(), null, ctx.dayPerf, ctx.P, ctx.H, keepWarm);
        return d.map((e, i) => (i !== ei ? e : { id: newId, slot, sets, note: '' }));
    },
    note({ ei, text, repeat }, ctx, d) {
        return d.map((e, i) => (i !== ei ? e : { ...e, note: String(text || ''), noteRepeat: repeat || e.noteRepeat || 'none' }));
    },
    // The pain flag: an injury log entry plus the session adjustment the PWA's injury review applies.
    injury({ region, impact = 'monitor', side = 'unspecified', note = '' }, ctx, d) {
        const area = INJURY_AREAS.find((a) => a.id === region); if (!area) return d;
        const id = 'inj-' + E.uid(), at = now();
        const entry = { id, region, part: area.parts[0], side, impact, note, createdAt: at, updatedAt: at };
        S.store.injuryLog = [...injuries(), entry]; markStore();
        const hit = d.filter((e) => injuryMatchesExercise(entry, EX_BY_ID[e.id], E.secondaryOf(EX_BY_ID[e.id] || {}))).map((e) => e.id);
        S.live.injuryReports = [...(S.live.injuryReports || []), { injuryId: id, region, impact, exercises: hit, at }];
        if (impact === 'monitor' || !hit.length) return d;
        return adjustInjuredExercises(d, hit, area.parts[0], impact === 'skip' ? 'skip' : 'reduce');
    },
    sessionNote({ text, repeat }, ctx, d) { S.live.sessionNote = String(text || ''); S.live.sessionNoteRepeat = repeat || 'none'; return d; },
    restDone(_, ctx, d) { S.live.rest = null; return d; },
    restAdjust({ delta }, ctx, d) { if (S.live.rest) S.live.rest = { ...S.live.rest, seconds: Math.max(0, S.live.rest.seconds + delta) }; return d; },
    tick({ elapsedMs }, ctx, d) { S.live.elapsedMs = Math.max(0, Number(elapsedMs) || 0); return d; },
    // App.js fbPump / fbSore: part -> "flat"|"good"|"huge" and "sore"|"ontime"|"fresh"; tapping the same value clears it.
    feedback({ part, pump, sore }, ctx, d) {
        if (pump !== undefined) { const m = { ...(S.live.fbPump || {}) }; if (!pump || m[part] === pump) delete m[part]; else m[part] = pump; S.live.fbPump = m; }
        if (sore !== undefined) { const m = { ...(S.live.fbSore || {}) }; if (!sore || m[part] === sore) delete m[part]; else m[part] = sore; S.live.fbSore = m; }
        return d;
    },
    // App.js applyReadiness: scale today's unlogged, app-owned loads; down-scaling always lands lower.
    readiness({ label = '—', factor = 1 }, ctx, d) {
        S.live.readiness = { label, factor };
        if (factor === 1) return d;
        const u = unit();
        return d.map((e) => {
            const step = E.loadStep(EX_BY_ID[e.id], u);
            return { ...e, sets: e.sets.map((x) => {
                if (x.done || E.userOwnsRuntimeSet(x) || !(parseFloat(x.weight) > 0)) return x;
                const cur = parseFloat(x.weight);
                let nw = factor < 1 ? Math.floor((cur * factor) / step) * step : E.roundTo(cur * factor, step);
                if (factor < 1 && nw >= cur) nw = cur - step;
                nw = Math.max(step, nw);
                const w = String(nw);
                return { ...x, weight: w, target: { ...x.target, w } };
            }) };
        });
    },
    // App.js toggleWarmups: add the warm-up ramp for this lift, or take it away.
    warmups({ ei }, ctx, d, sug) {
        return d.map((e, i) => {
            if (i !== ei) return e;
            if (e.sets.some((x) => x.warm)) return { ...e, sets: e.sets.filter((x) => !x.warm) };
            const work = parseFloat(e.sets.filter((x) => !x.warm && !x.sub)[0]?.weight) || (sug?.[ei] ? sug[ei].weight : null);
            const warm = work && work > 0 ? E.warmupSets(EX_BY_ID[e.id], e.slot === ctx.day.primaryIndex, work, unit()) : [];
            return warm.length ? { ...e, sets: [...warm, ...e.sets] } : e;
        });
    },
    // App.js removeExerciseFromSession: skip a lift for today (logged sets go with it, as on the web app).
    skipExercise({ ei }, ctx, d) {
        if (!d[ei]) return d;
        const out = d.filter((_, i) => i !== ei);
        S.live.exIdx = Math.max(0, (S.live.exIdx || 0) >= ei && (S.live.exIdx || 0) > 0 ? S.live.exIdx - 1 : (S.live.exIdx || 0));
        return out;
    },
    // App.js addExerciseToSession: three user-owned rows at the bottom of the lift's rep range.
    addExercise({ id }, ctx, d) {
        const ex = EX_BY_ID[id]; if (!ex || d.some((x) => x.id === id)) return d;
        const slot = d.length;
        const [lo, hi] = Array.isArray(ex.rep) ? ex.rep : [8, 12];
        const rows = Array.from({ length: 3 }, () => ({ weight: '', reps: String(lo || 8), warm: false, done: false, auto: false, valueOwner: 'user', added: true, target: { w: '—', reps: `${lo || 8}-${hi || 12}`, rir: null, freestyle: true } }));
        S.live.exIdx = slot;
        return [...d, { id, slot, sets: rows, note: '', superset: false, added: true, freestyle: true }];
    },
    // App.js changeSessionSetType: standard / warm-up / to failure, this session only, reversible.
    setType({ ei, si, kind }, ctx, d) {
        return d.map((e, i) => (i !== ei ? e : { ...e, sets: e.sets.map((x, j) => {
            if (j !== si || x.done || x.sub || !['standard', 'warmup', 'failure'].includes(kind)) return x;
            const original = x.typeOriginal || { target: x.target, suggested: x.suggested, autoTuned: x.autoTuned };
            const target = kind === 'warmup' ? { ...original.target, rir: null, failure: false } : kind === 'failure' ? { ...original.target, rir: 0, failure: true } : original.target;
            return { ...x, warm: kind === 'warmup', target, suggested: kind === 'warmup' ? undefined : original.suggested, autoTuned: kind === 'warmup' ? false : original.autoTuned, typeOriginal: original, typeOverride: kind };
        }) }));
    },
    // App.js addAdvanced: drop sets (80%, 60%) or myo-reps after the last working set; tapping again removes them.
    advanced({ ei, kind }, ctx, d, sug) {
        if (kind !== 'drop' && kind !== 'myo') return d;
        return d.map((e, i) => {
            if (i !== ei) return e;
            if (e.sets.some((x) => x.sub && x.kind === kind)) return { ...e, sets: e.sets.filter((x) => !(x.sub && x.kind === kind)) };
            const ex = EX_BY_ID[e.id];
            const work = e.sets.filter((x) => !x.warm && !x.sub);
            const baseW = parseFloat(work[work.length - 1]?.weight) || (sug?.[ei] ? sug[ei].weight : 0);
            const step = E.loadStep(ex, unit());
            const extra = kind === 'drop'
                ? [0.8, 0.6].map((f) => { const w = baseW ? E.roundTo(baseW * f, step) : null; return { weight: w != null ? String(w) : '', reps: '', warm: false, sub: true, kind: 'drop', dropF: f, done: false, target: { w: w != null ? String(w) : '—', reps: 'to failure' } }; })
                : Array.from({ length: E.MYO_MINI_SETS }, () => ({ ...E.makeMyoMini(baseW || '', false), warm: false }));
            const RANK = { drop: 0, myo: 1 };
            const merged = [...e.sets, ...extra];
            return { ...e, sets: [...merged.filter((x) => !x.sub), ...merged.filter((x) => x.sub).sort((a, b) => (RANK[a.kind] ?? 9) - (RANK[b.kind] ?? 9))] };
        });
    },
    // App.js levelSetsToAvg: flatten the remaining working sets to the average working load and reps.
    level({ ei }, ctx, d) {
        return d.map((e, i) => {
            if (i !== ei) return e;
            const work = e.sets.filter((x) => !x.warm && !x.sub && parseFloat(x.weight) > 0); if (!work.length) return e;
            const step = E.loadStep(EX_BY_ID[e.id], unit());
            const avg = E.roundTo(work.reduce((a, x) => a + parseFloat(x.weight), 0) / work.length, step);
            const avgReps = Math.round(work.reduce((a, x) => a + (parseInt(x.reps) || 0), 0) / work.length);
            const tw = String(avg), tr = avgReps > 0 ? String(avgReps) : null;
            return { ...e, sets: e.sets.map((x) => (x.warm || x.sub || x.done || !(parseFloat(x.weight) > 0) ? x
                : { ...x, weight: tw, reps: tr != null ? tr : x.reps, auto: false, valueOwner: 'user', hint: undefined, suggested: undefined, autoTuned: false, levelled: true, target: { ...x.target, w: tw } })) };
        });
    },
    // App.js setRestCustom (+ setProgramRest: a program lift keeps the choice for next time).
    restFor({ ei, sec }, ctx, d) {
        const v = Number(sec) > 0 ? Math.round(Number(sec)) : 0;
        const e = d[ei]; if (!e) return d;
        if (!ctx.program.quick && programById(ctx.program.id)) {
            const key = `${ctx.day.id}:${e.slot}`;
            setList('saved', saved().map((p) => {
                if (p.id !== ctx.program.id) return p;
                const overrides = { ...(p.overrides || {}) }; const cur = { ...(overrides[key] || {}) };
                const next = v > 0 ? E.markUserPrescriptionOverride(cur, 'rest', v) : E.clearUserPrescriptionOverride(cur, 'rest');
                if (next && Object.keys(next).length) overrides[key] = next; else delete overrides[key];
                return { ...p, overrides, edited: true };
            }));
        }
        return d.map((x, i) => (i === ei ? { ...x, restCustom: v } : x));
    },
    // Superset with the next lift (App.js toggleSuperset), or pull a suggested partner next to this one (applySuggestedSuperset).
    superset({ ei }, ctx, d) { return d.map((e, i) => (i === ei && ei < d.length - 1 ? { ...e, superset: !e.superset } : e)); },
    pairWith({ ei, partner }, ctx, d) {
        if (partner === ei || !d[partner] || !d[ei]) return d;
        const next = [...d]; const [mate] = next.splice(partner, 1);
        const cur = partner < ei ? ei - 1 : ei;
        next.splice(cur + 1, 0, mate); next[cur] = { ...next[cur], superset: true };
        S.live.exIdx = cur;
        return next;
    },
    // App.js changeProgression: this session's sets re-prescribed under another progression style.
    progression({ ei, style }, ctx, d) {
        const row = d[ei], ex = EX_BY_ID[row?.id];
        if (!ex || ctx.program.config?.percentScheme) return d;
        const source = programById(ctx.program.id) || S.live.quickProgram;
        const choices = { ...(S.live.sessionProgressions || {}), [row.id]: style };
        if (style === E.styleFor(source, row.id)) delete choices[row.id];
        const nextProgram = withProgressionChoices(source, choices);
        const anchors = E.calibratedAnchorPerf(nextProgram, ctx.day, ctx.P, ctx.H, ctx.wk, ctx.lifterModel.effort);
        const suggestion = E.sessionSuggestion(nextProgram, ctx.day, row.slot, anchors, unit(), ctx.wk, ctx.H);
        const prescribed = E.prescribeSets(nextProgram, ctx.day, ex, row.slot, ctx.wk, unit(), suggestion, anchors, ctx.P, ctx.H, true);
        S.live.sessionProgressions = choices;
        return d.map((item) => (item.id !== row.id ? item : { ...item, sets: refreshProgressionSets(item.sets, prescribed, E.userOwnsRuntimeSet) }));
    },
    resetTimer(_, ctx, d) { S.live.startedAt = now(); S.live.elapsedMs = 0; return d; }
};

export function act({ type, ...args } = {}) {
    const l = S.live; if (!l) return { active: false };
    const fn = ACTIONS[type]; if (!fn) return { ...session(), error: 'Unknown action ' + type };
    const ctx = sessionContext(l); if (!ctx) return { active: false };
    const sug = ['addSet', 'warmups', 'advanced'].includes(type) ? ctx.day.exercises.map((_id, slot) => E.sessionSuggestion(ctx.program, ctx.day, slot, ctx.dayPerf, unit(), ctx.wk, ctx.H)) : null;
    l.data = normalizeData(ctx, fn(args, ctx, l.data || [], sug));
    l.savedAt = now();
    markLive();
    return session();
}

export function swapOptions({ ei }) {
    const l = S.live; if (!l) return [];
    const ctx = sessionContext(l), e = l.data?.[ei]; if (!ctx || !e) return [];
    const cur = EX_BY_ID[e.id]; if (!cur) return [];
    const gym = E.gymById(E.normalizeGyms(S.store.gyms, S.store.activeGymId).gyms, S.store.activeGymId);
    const kit = E.expandEquipment(new Set(gym?.equipment || ctx.program.config?.equipment || []));
    const banned = new Set(S.store.banned || []);
    const pool = E.availableFor(cur.part, kit).filter((x) => x.id !== cur.id && !banned.has(x.id) && !l.data.some((r) => r.id === x.id));
    let ranked = pool; try { ranked = E.rankSwapAlts(cur, pool, null, l.data.map((r) => r.id), ctx.program, ctx.day) || pool; } catch { ranked = pool; }
    return ranked.slice(0, 12).map((x) => ({ id: x.id, name: x.name, equip: x.equip, part: x.part, type: x.type }));
}

export function explain({ ei }) {
    const l = S.live; if (!l) return null;
    const ctx = sessionContext(l), e = l.data?.[ei]; if (!ctx || !e) return null;
    try { return E.explainPrescription({ program: ctx.program, day: ctx.day, ex: EX_BY_ID[e.id], slot: e.slot, perf: ctx.P, history: ctx.H, weekIndex: ctx.wk }); } catch { return null; }
}

/* App.js WorkoutSession finish + App finishSession. */
export function finishSession() {
    const l = S.live; if (!l) return { ok: false, error: 'No workout in progress.' };
    const ctx = sessionContext(l); if (!ctx) return { ok: false, error: 'That workout day no longer exists.' };
    const { program, day, wk, H, P } = ctx, u = unit(), data = l.data || [];
    const work = (e) => e.sets.filter(isWork);
    const totalSets = data.reduce((s, e) => s + work(e).length, 0);
    const doneSets = data.reduce((s, e) => s + work(e).filter((x) => x.done).length, 0);
    const volume = data.reduce((sum, e) => sum + e.sets.reduce((s, x) => s + (x.done && !x.warm ? (parseFloat(x.weight) || 0) * (parseInt(x.reps) || 0) : 0), 0), 0);
    const perfOut = E.loggedWorkoutPerformance(program, day, data, wk, u, P, H);
    const elapsed = Math.max(Number(l.elapsedMs) || 0, now() - (l.startedAt || now()));
    let estMin; try { estMin = E.estimateMinutes(program, day, wk); } catch { estMin = undefined; }
    const fb = {};
    new Set([...Object.keys(l.fbPump || {}), ...Object.keys(l.fbSore || {})]).forEach((part) => { fb[part] = { pump: l.fbPump?.[part], sore: l.fbSore?.[part] }; });
    const fbDelta = {};
    Object.entries(fb).forEach(([part, v]) => { const dd = E.feedbackDelta(v.pump, v.sore); if (dd != null) fbDelta[part] = dd; });
    const progChanges = Object.keys(l.sessionProgressions || {}).length ? l.sessionProgressions : undefined;
    const log = {
        id: l.sessionId, programId: program.id, programName: program.name, dayLabel: day.label, dayId: day.id,
        engineV: program.engineV || 1, weekIndex: wk, setsDone: doneSets, totalSets, volume: Math.round(volume), unit: u,
        readiness: l.readiness, readinessStatus: l.readiness?.factor < 1 ? 'low' : 'normal', badDay: l.readiness?.factor < 1,
        substitutionOccurred: data.some((e) => e.id !== E.exerciseAt(program, day, e.slot, wk)),
        durationMin: Math.max(1, Math.round(elapsed / 60000)), estMin, perf: perfOut,
        feedback: fbDelta,
        injuryReports: l.injuryReports?.length ? l.injuryReports : undefined,
        sessionNote: (l.sessionNote || '').trim() || undefined, sessionNoteRepeat: l.sessionNoteRepeat,
        progressionChanges: progChanges, programUpdateStatus: progChanges && !program.quick ? 'pending' : undefined,
        feedbackRaw: Object.keys(fb).length ? fb : undefined
    };
    const t = now();
    const entry = { id: E.uid(), date: t, ...log, updatedAt: t };
    const nextHistory = E.capHistory([entry, ...H.filter((h) => h.id !== entry.id)]);
    const nextPerf = log.perf && Object.keys(log.perf).length ? { ...P, ...log.perf } : P;
    const tombs = { ...(S.store.tombs || {}) };
    const kept = new Set(nextHistory.map((h) => h.id));
    H.forEach((h) => { if (!kept.has(h.id)) tombs[h.id] = t; });
    const injuryLog = mergeInjuryReports(S.store.injuryLog, log.injuryReports, log.id);
    let nextSaved = saved();
    if (program.deloadNext && log.perf) {
        const trained = Object.keys(log.perf).filter((id) => program.deloadNext[id] != null);
        if (trained.length) {
            const nd = { ...program.deloadNext }; trained.forEach((id) => delete nd[id]);
            const cleared = { ...programById(program.id), deloadNext: Object.keys(nd).length ? nd : undefined, updatedAt: t };
            nextSaved = saved().map((p) => (p.id === cleared.id ? cleared : p));
        }
    }
    const msOpts = { unit: u, bodyweight: S.store.bodyweight };
    let fresh = [];
    try {
        const before = new Set(E.computeMilestones(H, msOpts).filter((m) => m.done).map((m) => m.id));
        fresh = E.computeMilestones([entry, ...H], msOpts).filter((m) => m.done && !before.has(m.id));
    } catch { fresh = []; }
    Object.assign(S.store, { perf: nextPerf, tombs, injuryLog });
    setList('history', nextHistory); setList('saved', nextSaved);
    S.live = null; markStore(); markLive();
    return { ok: true, entryId: entry.id, unit: u, setsDone: doneSets, totalSets, volume: Math.round(volume), durationMin: log.durationMin,
        achievements: fresh.map((m) => ({ id: m.id, label: m.label })), prs: summaryPRs(entry, H) };
}

function summaryPRs(entry, H) {
    const u = unit(); let before = {}; try { before = E.liftBests(H, u) || {}; } catch { before = {}; }
    const out = [];
    Object.entries(entry.perf || {}).forEach(([id, p]) => {
        const best = Number(before?.[id]?.e1rm ?? before?.[id]?.best ?? 0) || 0;
        const top = (p.sets || []).filter((s) => !s.sub && Number(s.r) > 0).reduce((m, s) => Math.max(m, E.e1rm(Number(s.w) || 0, Number(s.r) || 0)), 0);
        if (best > 0 && top > best + 0.25) out.push({ id, name: exName(id), e1rm: Math.round(top * 10) / 10, prev: Math.round(best * 10) / 10 });
    });
    return out;
}

export function discardSession() { S.live = null; markLive(); return { ok: true }; }

/* A workout in progress handed over from the web app: same rules as init (never resume a session
   that is already logged, never one whose program is missing), and never over a live workout here. */
export function adoptLive({ live }) {
    if (S.live) return { ok: false, reason: 'busy' };
    let l = null; try { l = JSON.parse(String(live || '')); } catch { l = null; }
    if (!l || !l.sessionId || history().some((h) => h.id === l.sessionId) || !liveProgram(l)) return { ok: false, reason: 'invalid' };
    S.live = normalizeLive(l); markLive();
    return { ok: true };
}

/* ------------------------------------------------------------------ history */
function historyRow(h) {
    return { id: h.id, date: h.date, programName: h.programName || '', dayLabel: h.dayLabel || 'Workout', setsDone: h.setsDone ?? null,
        totalSets: h.totalSets ?? null, volume: h.volume ?? null, unit: h.unit || unit(), durationMin: h.durationMin ?? null,
        exercises: Object.keys(h.perf || {}).map(exName) };
}
export function historyList({ limit = 200 } = {}) { return history().slice(0, limit).map(historyRow); }
export function historyDetail({ id }) {
    const h = history().find((x) => x.id === id); if (!h) return null;
    return { ...historyRow(h), note: h.sessionNote || '', lifts: Object.entries(h.perf || {}).map(([eid, p]) => ({ id: eid, name: exName(eid),
        sets: (p.sets || []).map((s) => ({ w: s.w ?? s.weight ?? null, r: s.r ?? s.reps ?? null, rir: s.rir ?? null, sub: !!s.sub, warm: !!s.warm })) })) };
}
/* App.js deleteHistoryEntry: the perf mirror is recomputed, not left pointing at the deleted session. */
export function deleteHistory({ id }) {
    const H = history(), gone = H.find((h) => h.id === id); if (!gone) return { ok: false };
    S.undo = { history: H, perf: perf(), tombs: { ...(S.store.tombs || {}) } };
    S.store.perf = E.perfAfterDelete(H, perf(), id);
    setList('history', H.filter((h) => h.id !== id));
    S.store.tombs = { ...(S.store.tombs || {}), [id]: now() };
    markStore();
    return { ok: true, label: gone.dayLabel || 'Workout' };
}
export function undo() {
    if (!S.undo) return { ok: false };
    const { history: h, saved: sv, cycles: cy, ...rest } = S.undo;
    Object.assign(S.store, rest);
    if (h) setList('history', h); if (sv) setList('saved', sv); if (cy) setList('cycles', cy);
    S.undo = null; markStore();
    return { ok: true };
}

/* ------------------------------------------------------------------ progress */
/* App.js chart favourites ("wpb:chart-favorites-v1", kept on the phone): pinned lifts show first on
   Progress, in pin order. The design caps them at six. Never pinned (null) shows the four most-trained
   lifts with a trend, the way the design seeds the main lifts. */
export const PIN_MAX = 6;
export function progress({ pinned = null } = {}) {
    const H = history(), u = unit();
    let trends = []; try { trends = E.exerciseTrends(H) || []; } catch { trends = []; }
    let bests = {}; try { bests = E.liftBests(H, u) || {}; } catch { bests = {}; }
    let recap = null; try { recap = E.weeklyRecap(H, u); } catch { recap = null; }
    const conv = (v, from) => (from && from !== u ? (u === 'lb' ? v * 2.20462 : v / 2.20462) : v);
    const r1 = (v) => Math.round(v * 10) / 10;
    const pts = (t) => (t.sessions || []).slice(-12).map((p) => ({ date: p.date ?? null, e1rm: r1(conv(p.best ?? 0, p.unit || t.unit)) }));
    const byId = Object.fromEntries(trends.map((t) => [t.id, t]));
    const auto = !Array.isArray(pinned);
    const ids = auto
        ? trends.filter((t) => (t.sessions || []).length >= 2).sort((a, b) => (b.sessionsCount || 0) - (a.sessionsCount || 0)).slice(0, 4).map((t) => t.id)
        : [...new Set(pinned.filter((id) => typeof id === 'string' && byId[id]))].slice(0, PIN_MAX);
    const pinView = (t) => {
        const ps = pts(t), first = ps[0]?.e1rm || 0, last = ps[ps.length - 1]?.e1rm || 0;
        // The card is remembered by its record session: the set behind the best e1RM, and when it was.
        const bestOf = (x) => conv(x.best ?? 0, x.unit || t.unit);
        const rec = (t.sessions || []).reduce((m, x) => (!m || bestOf(x) > bestOf(m) ? x : m), null);
        return { id: t.id, name: exName(t.id), points: ps, e1rm: rec ? r1(bestOf(rec)) : last,
            heavy: rec?.top?.w > 0 ? `${fmtW(conv(rec.top.w, rec.unit || t.unit))} ${u} × ${rec.top.r ?? ''}` : null,
            date: rec?.date ?? null, changePct: ps.length >= 2 && first > 0 ? Math.round((last / first - 1) * 1000) / 10 : null };
    };
    return {
        unit: u, sessions: H.length,
        lifts: trends.slice(0, 24).map((t) => ({ id: t.id, name: exName(t.id), points: pts(t), change: t.change ?? t.delta ?? null, pinned: ids.includes(t.id) })),
        pins: { auto, max: PIN_MAX, lifts: ids.map((id) => pinView(byId[id])),
            options: trends.filter((t) => !ids.includes(t.id)).map((t) => ({ id: t.id, name: exName(t.id) })).sort((a, b) => a.name.localeCompare(b.name)) },
        // liftBests carries the best e1RM and heaviest load but no reps; the set shown beside a record is
        // the one from the session that set it, as on the pinned cards.
        records: Object.entries(bests).map(([id, b]) => {
            const t = byId[id], bestOf = (x) => conv(x.best ?? 0, x.unit || t?.unit);
            const rec = t ? (t.sessions || []).reduce((m, x) => (!m || bestOf(x) > bestOf(m) ? x : m), null) : null;
            const top = rec?.top?.w > 0 ? rec.top : null;
            return { id, name: exName(id), e1rm: r1(b.e1rm ?? b.best ?? 0), weight: top ? fmtW(conv(top.w, rec.unit || t?.unit)) : (fmtW(b.weight ?? b.w) || null),
                reps: top ? (top.r ?? null) : (b.reps ?? b.r ?? null), date: rec?.date ?? b.date ?? null };
        }).sort((a, b) => b.e1rm - a.e1rm).slice(0, 30),
        recap,
        readiness: readiness()
    };
}

/* The design's Progress charts, from logged history only:
   - weekly volume and sessions for the last eight Monday-first weeks (historyVolumeIn, in the lifter's unit);
   - intensity distribution of working sets over the last four weeks, by reps (1–5 / 6–12 / 13+) and by the
     effort the lifter reported (0 / 1 / 2 / 3+ RIR; unreported sets are counted separately, never guessed);
   - recovery & feedback per muscle: the last three soreness answers (fbSore) beside its readiness. */
const SORE_LABEL = { sore: 'Too much', ontime: 'About right', fresh: 'Too easy' };
function isoWeek(t) { const d = new Date(t); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() + 3 - ((d.getDay() + 6) % 7)); const w1 = new Date(d.getFullYear(), 0, 4); return 1 + Math.round(((d - w1) / 86400000 - 3 + ((w1.getDay() + 6) % 7)) / 7); }
export function progressCharts() {
    const H = history(), u = unit(), DAY = 86400000;
    const ws = (() => { const d = new Date(); const day = (d.getDay() + 6) % 7; d.setHours(0, 0, 0, 0); return d.getTime() - day * DAY; })();
    const weeks = Array.from({ length: 8 }, (_, k) => {
        const start = ws - (7 - k) * 7 * DAY, end = start + 7 * DAY, hs = H.filter((h) => Number(h.date) >= start && Number(h.date) < end);
        let vol = 0; hs.forEach((h) => { try { vol += E.historyVolumeIn(h, u) || 0; } catch { /* unreadable entry */ } });
        return { label: 'W' + isoWeek(start), start, now: k === 7, volume: Math.round(vol), sessions: hs.length };
    });
    const since = Date.now() - 28 * DAY, reps = [0, 0, 0], eff = [0, 0, 0, 0]; let unrated = 0;
    H.filter((h) => Number(h.date) >= since).forEach((h) => Object.values(h.perf || {}).forEach((pf) => {
        let rows = []; try { rows = E.completedHistorySets(pf) || []; } catch { rows = []; }
        rows.filter((x) => !x.warm && !x.sub).forEach((x) => {
            const r = Number(x.r); if (!(r > 0)) return;
            reps[r <= 5 ? 0 : r <= 12 ? 1 : 2]++;
            const rir = Number(x.rir);
            if (x.rirReported === false || x.rir == null || !Number.isFinite(rir)) unrated++; else eff[rir <= 0 ? 0 : rir < 2 ? 1 : rir < 3 ? 2 : 3]++;
        });
    }));
    const rd = readiness(), fb = {};
    H.forEach((h) => Object.entries(h.feedbackRaw || {}).forEach(([part, v]) => { if (v?.sore && SORE_LABEL[v.sore]) (fb[part] = fb[part] || []).length < 3 && fb[part].push(SORE_LABEL[v.sore]); }));
    const feedback = Object.entries(fb).map(([part, answers]) => ({ part, label: E.PART_LABEL?.[part] || part, readiness: rd.find((r) => r.part === part)?.readiness ?? null, answers }))
        .sort((a, b) => (a.readiness ?? 100) - (b.readiness ?? 100));
    return { unit: u, weeks, intensity: { reps: [{ label: 'Strength', sub: '1–5 reps', sets: reps[0] }, { label: 'Hypertrophy', sub: '6–12 reps', sets: reps[1] }, { label: 'Endurance', sub: '13+ reps', sets: reps[2] }],
        effort: [{ label: 'To failure', sub: '0 RIR', sets: eff[0] }, { label: 'Hard', sub: '1 RIR', sets: eff[1] }, { label: 'Productive', sub: '2 RIR', sets: eff[2] }, { label: 'Easy', sub: '3+ RIR', sets: eff[3] }], unrated },
        feedback };
}

/* The design's You → Profile, from logged history: lifetime totals, the big three against bodyweight,
   a PR board (estimated 1RM or heaviest set, each with the session that set it), and the last 30 days'
   working sets per muscle with the push : pull balance. */
const PUSH_PARTS = ['chest', 'shoulders', 'triceps'], PULL_PARTS = ['lats', 'upper_back', 'biceps', 'traps'];
export function profileBoard() {
    const H = history(), u = unit(), DAY = 86400000, bw = parseFloat(S.store.bodyweight);
    const conv = (v, from) => (from && from !== u ? (u === 'lb' ? v * 2.20462 : v / 2.20462) : v);
    const r1 = (v) => Math.round(v * 10) / 10;
    let trends = []; try { trends = E.exerciseTrends(H) || []; } catch { trends = []; }
    let lifted = 0; H.forEach((h) => { try { lifted += E.historyVolumeIn(h, u) || 0; } catch { /* unreadable entry */ } });
    const recOf = (t, key) => (t.sessions || []).reduce((m, x) => {
        const v = key === 'e1rm' ? conv(x.best ?? 0, x.unit || t.unit) : conv(x.top?.w ?? 0, x.unit || t.unit);
        return !m || v > m.v ? { v, x } : m;
    }, null);
    const board = (key) => trends.map((t) => { const r = recOf(t, key); return r && r.v > 0 ? { id: t.id, name: exName(t.id), value: r1(r.v), date: r.x.date ?? null, reps: key === 'heaviest' ? (r.x.top?.r ?? null) : null } : null; })
        .filter(Boolean).sort((a, b) => b.value - a.value).slice(0, 10);
    const since30 = Date.now() - 30 * DAY;
    const prs30 = trends.filter((t) => { const r = recOf(t, 'e1rm'); return r && Number(r.x.date) >= since30 && (t.sessions || []).length > 1; }).length;
    const big = Object.entries(E.BIG_THREE || {}).map(([k, id]) => {
        const t = trends.find((x) => x.id === id), e = t ? r1(conv(t.prBest ?? 0, t.unit)) : 0;
        return { key: k, id, name: k.charAt(0).toUpperCase() + k.slice(1), e1rm: e, ratio: bw > 0 && e > 0 ? Math.round(e / bw * 100) / 100 : null };
    });
    const total = r1(big.reduce((a, b) => a + b.e1rm, 0));
    const sets = {};
    H.filter((h) => Number(h.date) >= since30).forEach((h) => Object.entries(h.perf || {}).forEach(([id, pf]) => {
        const part = EX_BY_ID[id]?.part; if (!part) return;
        let rows = []; try { rows = E.completedHistorySets(pf) || []; } catch { rows = []; }
        const n = rows.filter((x) => !x.warm && !x.sub && Number(x.r) > 0).length; if (n) sets[part] = (sets[part] || 0) + n;
    }));
    const push = PUSH_PARTS.reduce((a, p) => a + (sets[p] || 0), 0), pull = PULL_PARTS.reduce((a, p) => a + (sets[p] || 0), 0);
    return { unit: u, bodyweight: bw > 0 ? bw : null,
        lifetime: { sessions: H.length, lifted: Math.round(lifted), streakWeeks: weekStreak(H), prs30 },
        big3: { lifts: big, total, ratio: bw > 0 && total > 0 ? Math.round(total / bw * 10) / 10 : null, logged: big.filter((b) => b.e1rm > 0).length },
        prBoard: { e1rm: board('e1rm'), heaviest: board('heaviest') },
        balance: { parts: Object.entries(sets).map(([part, n]) => ({ part, label: E.PART_LABEL?.[part] || part, sets: n })).sort((a, b) => b.sets - a.sets),
            push, pull, ratio: push > 0 && pull > 0 ? Math.round(push / pull * 10) / 10 : null } };
}

/* ------------------------------------------------------------------ plan */
export function plan({ programId, weekIndex } = {}) {
    const nx = upNext();
    const p = programId ? programById(programId) : nx?.program;
    if (!p) return null;
    const wk = weekIndex || (nx && nx.program.id === p.id ? nx.weekIndex : 1);
    return { programId: p.id, name: p.name, weekIndex: wk, weeksTotal: E.weeksOf(p), current: nx?.program.id === p.id ? nx.weekIndex : null,
        nextDayId: nx?.program.id === p.id ? nx.day.id : null, split: p.config?.split || null, goal: p.config?.goal || null, endless: !!p.config?.endless,
        deload: !!p.config?.deload,
        days: (() => {
            // The design's day cards: weekday badge from the schedule, and this week's status when the week shown is the current one.
            const sched = p.schedule || {}, isCur = nx?.program.id === p.id && wk === nx.weekIndex;
            const ws = (() => { const d = new Date(); const day = (d.getDay() + 6) % 7; d.setHours(0, 0, 0, 0); return d.getTime() - day * 86400000; })();
            const done = new Set(isCur ? history().filter((h) => h.programId === p.id && Number(h.date) >= ws).map((h) => h.dayId) : []);
            return p.days.map((d) => {
                let m = null; try { m = E.estimateMinutes(p, d, wk); } catch { m = null; }
                const ex = dayPreview(p, d, wk), wd = Object.keys(sched).find((k) => sched[k] === d.id);
                return { id: d.id, label: d.label, minutes: m, exercises: ex, sets: ex.reduce((a, e) => a + (Number(e.sets) || 0), 0),
                    weekday: wd == null ? null : ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'][Number(wd)],
                    status: !isCur ? null : done.has(d.id) ? 'done' : d.id === nx.day.id ? 'today' : 'planned' };
            });
        })(),
        percent: !!p.config?.percentScheme,
        cycleId: (p.cycleId && cycles().some((c) => c.id === p.cycleId)) ? p.cycleId : null, canNextBlock: !p.cycleId || !cycles().some((c) => c.id === p.cycleId),
        // Cycle blocks are reached through their cycle, as on the web app's Home, so the list stays one row per plan.
        programs: saved().filter((x) => !(x.cycleId && cycles().some((c) => c.id === x.cycleId))).map((x) => ({ id: x.id, name: x.name, days: x.days.length, active: x.id === nx?.program.id })) };
}
/* App.js ProgressionCard ("Lift progression"): every lift for every week of the block, each cell from
   computeCell, the same call the workout prescribes from. App.js samples computeCell at weeks 0…n-1 and
   labels them Wk 1…n; week 0 has no prescription (it reads "Prescription data is missing", 0 sets) and
   every later column is one week early. This reads 1…n and the deload week, the indexing every other
   computeCell/plannedWeek caller uses. */
const UPPER_PARTS = new Set(['chest', 'lats', 'upper_back', 'shoulders', 'biceps', 'triceps', 'forearms', 'traps', 'neck']);
function blockWeeks(p) {
    const weeks = E.weeksOf(p), deloadWeek = p.config?.deload ? weeks + 1 : null;
    return { weeks, deloadWeek, list: Array.from({ length: weeks }, (_, i) => i + 1).concat(deloadWeek ? [deloadWeek] : []) };
}
export function weekByWeek({ programId } = {}) {
    const nx = upNext();
    const p = programId ? programById(programId) : nx?.program;
    if (!p) return null;
    const B = blockWeeks(p), rows = [];
    p.days.forEach((d) => (d.exercises || []).forEach((exId, slot) => {
        const ex = EX_BY_ID[exId]; if (!ex) return;
        rows.push({ exId, name: ex.name, part: ex.part, upper: UPPER_PARTS.has(ex.part), dayLabel: d.label, primary: slot === d.primaryIndex,
            cells: B.list.map((w) => {
                const c = E.computeCell(p, d, exId, slot, w);
                const rir = c.rir != null ? c.rir : (c.targetRIR != null ? c.targetRIR : null);
                return { w, sets: Number(c.sets) || 0, range: String(c.range ?? c.reps ?? ''), rir: rir == null ? null : String(rir), tech: c.tech ? String(c.tech) : null, deload: w === B.deloadWeek };
            }) });
    }));
    return { programId: p.id, name: p.name, weeks: B.weeks, deloadWeek: B.deloadWeek, current: nx?.program.id === p.id ? nx.weekIndex : null, percent: !!p.config?.percentScheme, rows };
}
/* App.js volumeZone / partZone: four bands (below MEV, productive to MAV, high to MRV + grain, over MRV);
   composites (the deltoid) only read "over" when a head past its own ceiling. Wording kept from App.js. */
function volumeZoneOf(part, sets, program) {
    const { mev, mrv } = E.landmarkFor(part, program?.config?.experience || 'intermediate', program);
    const mav = E.mavFor(part, program);
    if (sets < mev) return { label: 'below MEV', sub: 'under the volume that reliably drives growth', tone: 'muted' };
    if (sets <= mav) return { label: 'productive', sub: 'inside the range this app plans for', tone: 'good' };
    if (sets <= mrv + E.MRV_GRAIN) return { label: 'high', sub: 'near the top of the planned range', tone: 'good' };
    return { label: 'over MRV', sub: 'past what this app budgets to recover from', tone: 'warn' };
}
function partZoneOf(part, sets, sub, program) {
    if (program?.engineSource === 'pursuit-next') return volumeZoneOf(part, sets, program);
    const heads = E.COMPOSITE_HEADS?.[part];
    if (!heads || !sub) return volumeZoneOf(part, sets);
    const { mev } = E.landmarkFor(part);
    if (sets < mev) return { label: 'below MEV', sub: 'under the volume that reliably drives growth', tone: 'muted' };
    const over = heads.some((h) => { const lm = E.SUB_LANDMARKS?.[h]; return lm && lm.mev !== 0 && (sub[h] || 0) > (lm.mrv != null ? lm.mrv + E.MRV_GRAIN : Infinity); });
    if (over) return { label: 'a head over MRV', sub: 'one deltoid head is past its own ceiling', tone: 'warn' };
    return sets <= E.mavFor(part) ? { label: 'productive', sub: 'inside the range this app plans for', tone: 'good' } : { label: 'high', sub: 'near the top of the planned range', tone: 'good' };
}
/* App.js VolumeCard for a program week: sets per muscle against MEV/MAV/MRV, the volume check (every
   training week), and what your own history says about each muscle's productive range. */
export function weeklyVolumeView({ programId, weekIndex } = {}) {
    const nx = upNext();
    const p = programId ? programById(programId) : nx?.program;
    if (!p) return null;
    const wk = weekIndex || (nx && nx.program.id === p.id ? nx.weekIndex : 1);
    const volume = E.weeklyVolume(p, wk) || {}, sub = E.weeklySubVolume(p, wk) || {};
    const parts = (E.PART_ORDER || Object.keys(volume)).filter((x) => (volume[x] || 0) > 0.05);
    const r1 = (v) => Math.round((Number(v) || 0) * 10) / 10;
    let verdicts = []; try { verdicts = E.volumeVerdicts(history(), volume, parts) || []; } catch { verdicts = []; }
    let audit = { issues: [], volBias: {} }; try { audit = E.volumeAudit(p) || audit; } catch { audit = { issues: [], volBias: {} }; }
    const rows = parts.map((part) => {
        const lm = E.landmarkFor(part, p.config?.experience || 'intermediate', p), z = partZoneOf(part, volume[part], sub, p);
        return { part, label: E.PART_LABEL?.[part] || part, sets: r1(volume[part]), mev: lm.mev, mav: E.mavFor(part, p), mrv: lm.mrv, zone: z.label, zoneSub: z.sub, tone: z.tone };
    });
    return { programId: p.id, weekIndex: wk, total: r1(parts.reduce((a, x) => a + volume[x], 0)), rows,
        issues: (audit.issues || []).map((i) => ({ part: i.part, label: i.label || E.PART_LABEL?.[i.part] || i.part, status: i.status, sets: r1(i.v), mev: i.mev ?? null, mrv: i.mrv ?? null, fix: i.fix || '' })),
        canRepair: p.engineSource === 'pursuit-next' ? (audit.issues || []).length > 0 : Object.keys(audit.volBias || {}).length > 0,
        verdicts: verdicts.map((v) => ({ part: v.part, label: E.PART_LABEL?.[v.part] || v.part, status: v.status, planned: r1(v.planned), lo: v.lo, hi: v.hi, weeks: v.weeks })) };
}
/* App.js applyVolumeFix: engine programs are repaired by repairShellVolume; the result message is the engine's. */
export function repairVolume({ programId } = {}) {
    const p = programId ? programById(programId) : upNext()?.program;
    if (!p) return { ok: false, message: 'No program to repair.' };
    if (p.engineSource !== 'pursuit-next') {
        const volBias = (() => { try { return E.volumeAudit(p).volBias || {}; } catch { return {}; } })();
        if (!Object.keys(volBias).length) return { ok: true, changed: false, status: 'unchanged', message: 'Nothing the auto-fix can change.' };
        const next = { ...p, slotBias: { ...(p.slotBias || {}) }, config: { ...p.config } };
        E.distributeVolBias(next, volBias);
        S.undo = { saved: saved(), pinnedId: S.store.pinnedId };
        setList('saved', saved().map((x) => (x.id === p.id ? next : x)));
        return { ok: true, changed: true, status: 'repaired', message: 'Weekly volume adjusted.' };
    }
    let result;
    try { result = E.repairShellVolume(p, EXERCISES); }
    catch { return { ok: false, status: 'unable', message: 'Couldn’t safely repair this plan. Your sets have been kept; try a longer session or swap redundant work.' }; }
    if (result.changed) { S.undo = { saved: saved(), pinnedId: S.store.pinnedId }; setList('saved', saved().map((x) => (x.id === p.id ? result.program : x))); }
    return { ok: true, changed: !!result.changed, status: result.status, message: result.message };
}
/* App.js LoadWaveCard: planned working sets per week, deload included as the program's own fact. */
export function volumeWave({ programId } = {}) {
    const nx = upNext();
    const p = programId ? programById(programId) : nx?.program;
    if (!p) return null;
    const B = blockWeeks(p);
    const weeks = B.list.map((w) => ({ w, deload: w === B.deloadWeek, ...E.plannedWeek(p, w) }));
    return { programId: p.id, weeks, current: nx?.program.id === p.id ? nx.weekIndex : null };
}
export function pinProgram({ id }) { if (!programById(id)) return { ok: false }; S.store.pinnedId = id; markStore(); return { ok: true }; }

/* ------------------------------------------------------------------ library */
/* The design's Exercises: muscle and equipment filters, a count, and a TODAY tag on lifts in the next session. */
const LIB_EQUIP = { barbell: ['barbell', 'ezbar', 'trapbar'], dumbbell: ['dumbbell'], cable: ['cable'], machine: ['machine', 'smith', 'plate_loaded', 'legpress'], kettlebell: ['kettlebell'], band: ['band'] };
const BW_ONLY = new Set(['pullup', 'bench', 'dip', 'box', 'rings', 'floor', 'mat', 'rack']);
function libEquipOf(x) {
    const eq = x.equip || [];
    if (!eq.length || eq.every((e) => BW_ONLY.has(e))) return 'bodyweight';
    return Object.keys(LIB_EQUIP).find((k) => eq.some((e) => LIB_EQUIP[k].includes(e))) || 'other';
}
export function library({ q = '', part = null, equip = null } = {}) {
    const needle = String(q).trim().toLowerCase();
    const banned = new Set(S.store.banned || []);
    const nx = upNext();
    const today = new Set(nx ? nx.day.exercises.map((_id, slot) => E.exerciseAt(nx.program, nx.day, slot, nx.weekIndex)) : []);
    return EXERCISES.filter((x) => (!part || x.part === part) && (!equip || libEquipOf(x) === equip) && (!needle || x.name.toLowerCase().includes(needle)))
        .slice(0, 400).map((x) => ({ id: x.id, name: x.name, part: x.part, partLabel: E.PART_LABEL?.[x.part] || x.part, equip: x.equip, type: x.type,
            equipLabel: { barbell: 'Barbell', dumbbell: 'Dumbbell', cable: 'Cable', machine: 'Machine', kettlebell: 'Kettlebell', band: 'Band', bodyweight: 'Bodyweight' }[libEquipOf(x)] || EQUIP_LABEL[(x.equip || [])[0]] || 'Other',
            banned: banned.has(x.id), today: today.has(x.id), custom: !!x.custom }));
}
export function exercise({ id }) {
    const x = EX_BY_ID[id]; if (!x) return null;
    let cues = []; try { cues = E.cuesFor(x) || []; } catch { cues = []; }
    const H = history().filter((h) => h.perf?.[id]).slice(0, 12);
    return { id, name: x.name, part: x.part, partLabel: E.PART_LABEL?.[x.part] || x.part, equip: x.equip, type: x.type, cues,
        secondary: (E.secondaryOf(x) || []).map(([p, f]) => ({ part: p, label: E.PART_LABEL?.[p] || p, share: f })),
        note: S.store.exNotes?.[id] || '', banned: (S.store.banned || []).includes(id),
        // App.js exercise info sheet: isStretchFocus(ex.id, 3) shows the StretchFocusNote.
        stretchFocus: (() => { try { return !!E.isStretchFocus(id, 3); } catch { return false; } })(),
        history: H.map((h) => ({ date: h.date, sets: (h.perf[id].sets || []).filter((s) => !s.warm).map((s) => ({ w: s.w ?? null, r: s.r ?? null })) })) };
}
export function setBan({ id, banned }) {
    const cur = new Set(S.store.banned || []); banned ? cur.add(id) : cur.delete(id);
    S.store.banned = [...cur]; markStore(); return { ok: true, banned: !!banned };
}
export function setExNote({ id, text }) { S.store.exNotes = { ...(S.store.exNotes || {}), [id]: String(text || '') }; markStore(); return { ok: true }; }

/* ------------------------------------------------------------------ program creation */
export function setupOptions() {
    return { splits: Object.entries(E.SPLITS || {}).map(([id, s]) => ({ id, name: s.name || id, days: s.days || null })),
        sessions: (E.SESSIONS || []).map((s) => (typeof s === 'string' ? { id: s, label: s } : { id: s.id, label: s.label || s.id })),
        equipment: (E.EQUIPMENT || []).map((e) => ({ id: e.id, label: e.label || e.name || e.id })),
        goals: ['strength', 'hypertrophy', 'both'], experience: Object.keys(E.EXP || { novice: 1, intermediate: 1, advanced: 1 }),
        injuryAreas: INJURY_AREAS, injuryImpacts: INJURY_IMPACTS,
        parts: (E.PART_ORDER || []).filter((p) => E.PART_LABEL?.[p]).map((p) => ({ id: p, label: E.PART_LABEL[p] })) };
}
/* The same defaults createProgram uses, so the recommendation describes the program that gets built. */
function programConfig(config) {
    return { unit: unit(), weeks: 6, progression: 'auto', deload: false, focus: {}, reduce: [], barbellCap: 3, noBodyweight: false, noSupersets: false, ...config };
}
/* App.js nextRecommendedSplit: the engine picks among its automatic split families. */
export function recommend({ config }) {
    const cfg = programConfig(config);
    let next = null; try { next = E.recommendNextSplitForShell(cfg, [], EXERCISES, 63063); } catch { next = null; }
    try { const rec = E.recommendedSplit(cfg, next); return rec && E.SPLITS[rec]?.days.includes(cfg.days) ? rec : null; } catch { return null; }
}
/* App.js handleGenerate: banned lifts honoured, the route stamped, the equipment remembered, and a
   unit picked here goes through switchUnit once a unit has been chosen (so loads convert). */
function adoptConfigUnit(cfgUnit) {
    if (cfgUnit && cfgUnit !== unit()) {
        if (S.store.unitChosen && !S.live) switchUnit({ to: cfgUnit }); else S.store.unit = cfgUnit;
    }
    S.store.unitChosen = true;
}
export function createProgram({ config, seed }) {
    const cfg = programConfig(config);
    let r;
    try { r = E.generateNextProgramForShell({ config: cfg, banned: S.store.banned || [], legacyExercises: EXERCISES, ...(Number.isFinite(seed) ? { seed } : {}), makeId: () => 'p-' + E.uid() }); }
    catch (err) { return { ok: false, error: String(err?.message || err) }; }
    const p = r?.program; if (!p) return { ok: false, error: 'The engine could not build that program.' };
    p.generationRoute = E.GENERATION_ROUTE;
    S.store.equipDefault = cfg.equipment;
    adoptConfigUnit(cfg.unit);
    if (cfg.experience) S.store.experience = cfg.experience; // App.js rememberExperience
    setList('saved', [...saved(), p]); S.store.pinnedId = p.id;
    markStore();
    return { ok: true, programId: p.id, name: p.name, days: p.days.length };
}

/* ------------------------------------------------------------------ training cycles (App.js "Cycles" section) */
class CycleError extends Error { constructor(message, code = 'NEXT_CYCLE_INCOMPLETE') { super(message); this.code = code; } }
/* App.js requireCompleteCycleShell: nothing reaches the store until the cross-record invariants agree. */
function requireCompleteCycleShell(cycle, programs, { allowDone = false, expectedCycleId = null } = {}) {
    const blocks = Array.isArray(programs) ? programs : [];
    const ids = Array.isArray(cycle?.blockIds) ? cycle.blockIds : [];
    const meta = Array.isArray(cycle?.blockMeta) ? cycle.blockMeta : [];
    if (!cycle?.id) throw new CycleError('The cycle is missing its id.');
    if (expectedCycleId && cycle.id !== expectedCycleId) throw new CycleError('The cycle id changed unexpectedly during the transition.', 'NEXT_CYCLE_ID_MISMATCH');
    if (!ids.length || meta.length !== ids.length) throw new CycleError('The cycle block index is incomplete.');
    const badActive = cycle.activeBlock == null || cycle.activeBlock < 0 || cycle.activeBlock >= ids.length;
    if ((!allowDone && badActive) || (allowDone && !cycle.done && badActive)) throw new CycleError('The active cycle block is invalid.', 'NEXT_CYCLE_ACTIVE_BLOCK_INVALID');
    const byId = new Map(blocks.filter(Boolean).map((b) => [b.id, b]));
    for (let i = 0; i < ids.length; i++) {
        if (!ids[i] || meta[i]?.id !== ids[i]) throw new CycleError('Cycle block metadata no longer matches the block order.', 'NEXT_CYCLE_BLOCK_INDEX_MISMATCH');
        const block = byId.get(ids[i]);
        if (block && (!block.config || (block.cycleId && block.cycleId !== cycle.id))) throw new CycleError('A cycle block has inconsistent ownership metadata.', 'NEXT_CYCLE_BLOCK_OWNER_MISMATCH');
    }
    return { cycle, ids, meta, byId };
}
const cycleFail = (err, fallback) => ({ ok: false, code: err?.code || 'NEXT_ENGINE_ERROR', error: err instanceof E.NextShellAdapterError || err instanceof CycleError ? String(err.message || err.code) : fallback });

/* App.js liveCycleInfo: where the cycle is right now. */
function liveCycleInfo() {
    const nx = upNext(); const pid = nx?.program?.id; if (!pid) return null;
    const c = cycles().find((x) => !x.done && Array.isArray(x.blockIds) && x.blockIds.includes(pid)); if (!c) return null;
    const i = c.blockIds.indexOf(pid), wk = nx.weekIndex, wkTotal = E.weeksOf(nx.program);
    return { cycle: c, blockIdx: i, weekIndex: wk, weeksTotal: wkTotal, isDeload: wk > wkTotal, dayIndex: nx.dayIndex, dayLabel: nx.day?.label, daysPerWeek: nx.program.days.length };
}
function cycleCard(c, info) {
    let P = null; try { P = E.cycleProgress(c, saved(), history(), info && info.cycle.id === c.id ? info : null); } catch { P = null; }
    return { id: c.id, name: c.name || 'Training cycle', done: !!c.done, activeBlock: c.activeBlock || 0, templateId: c.templateId || null, onComplete: c.onComplete || null,
        pct: P?.pct ?? 0, curWeek: P?.curWeek ?? null, totalWeeks: P?.totalWeeks ?? null, weeksLeft: P?.weeksLeft ?? null, sessions: P?.sessions ?? 0, startMs: P?.startMs ?? null, endMs: P?.endMs ?? null,
        blocks: (P?.blocks || []).map((b) => ({ index: b.index, id: b.id, label: b.label || `Block ${b.index + 1}`, note: b.note || '', phase: b.phase || null, weeks: b.weeks || 0, deload: !!b.deload, total: b.total, weeksIn: b.weeksIn,
            state: b.state, sessions: b.sessions, exists: b.exists, days: b.days, split: b.split, range: b.range, dates: b.dates })),
        live: info && info.cycle.id === c.id ? { blockIdx: info.blockIdx, weekIndex: info.weekIndex, weeksTotal: info.weeksTotal, isDeload: info.isDeload, dayIndex: info.dayIndex, dayLabel: info.dayLabel, daysPerWeek: info.daysPerWeek } : null };
}
export function cyclesView() {
    const info = liveCycleInfo();
    return { templates: E.nextCycleTemplatesForShell(), phases: E.CUSTOM_CYCLE_PHASES, cycles: cycles().map((c) => cycleCard(c, info)).reverse(), liveId: info?.cycle.id || null };
}
export function cycleDetail({ id }) {
    const c = cycles().find((x) => x.id === id); if (!c) return null;
    const info = liveCycleInfo(), card = cycleCard(c, info), active = c.activeBlock || 0;
    const current = programById(c.blockIds[active]), next = programById(c.blockIds[active + 1]);
    let changes = null; try { changes = current && next ? E.blockChangeSummary(current, next) : null; } catch { changes = null; }
    return { ...card, adapts: !!c.nextEngineCycle?.adaptBetweenBlocks, engine: c.engineSource || null, changes,
        nextLabel: next ? (next.blockLabel || next.name) : null, final: !c.done && active >= c.blockIds.length - 1,
        currentProgramId: current?.id || null };
}
/* App.js buildCycle. */
export function createCycle({ config, templateId, blocks, adapt = false } = {}) {
    const cfg = programConfig(config);
    const tmplId = templateId || E.nextCycleTemplatesForShell()[0]?.id || 'powerbuilding';
    try {
        const built = E.generateNextCycleForShell({ templateId: tmplId, blocks: tmplId === 'custom' ? blocks : undefined, config: cfg, banned: S.store.banned || [], legacyExercises: EXERCISES, adaptBetweenBlocks: !!adapt, makeId: E.uid });
        const cycle = built?.cycle; const progs = Array.isArray(built?.blocks) ? built.blocks : [];
        const { meta, ids } = requireCompleteCycleShell(cycle, progs);
        if (progs.length !== ids.length || progs.some((b, i) => !b?.id || ids[i] !== b.id)) throw new CycleError('The cycle builder returned an incomplete cycle. Nothing was saved.');
        cycle.generationRoute = E.GENERATION_ROUTE; progs.forEach((b) => { b.generationRoute = E.GENERATION_ROUTE; });
        S.store.equipDefault = cfg.equipment;
        adoptConfigUnit(cfg.unit);
        cycle.startedAt = now();
        setList('saved', [...saved(), ...progs]); setList('cycles', [...cycles(), cycle]); S.store.pinnedId = progs[0].id;
        markStore();
        return { ok: true, cycleId: cycle.id, name: cycle.name, blocks: meta.length, first: meta[0]?.label || 'first block' };
    } catch (err) { return cycleFail(err, 'The cycle builder hit an unexpected error. Nothing was saved.'); }
}
/* App.js doCompleteCycleBlock: legacy first-block bridge, history-driven advance, and looping. */
export function advanceCycle({ id }) {
    const cycle = cycles().find((c) => c.id === id); if (!cycle) return { ok: false, error: 'That cycle no longer exists.' };
    if (S.live) return { ok: false, error: 'Finish or discard the workout in progress first.' };
    if (cycle.engineSource === 'pursuit-next' && cycle.nextEngineCycle?.legacyFirstBlockId && Number(cycle.activeBlock || 0) === 0) {
        try {
            const bridged = E.advanceLegacyFirstCycleBlock(cycle, saved());
            if (!bridged) throw new CycleError('The legacy first-block bridge could not resolve the next block.');
            requireCompleteCycleShell(bridged.cycle, saved(), { expectedCycleId: cycle.id });
            setList('cycles', cycles().map((c) => (c.id === cycle.id ? bridged.cycle : c))); S.store.pinnedId = bridged.nextProgram.id; markStore();
            return { ok: true, message: `Advanced to ${bridged.cycle.blockMeta[1]?.label || 'next block'}. Your original plan stayed intact as Block 1.` };
        } catch { return { ok: false, error: 'The next cycle block was incomplete, so nothing changed.' }; }
    }
    if (cycle.engineSource !== 'pursuit-next') return { ok: false, error: 'This older cycle needs to be rebuilt before it can continue.' };
    const activeProgram = programById(cycle.blockIds[cycle.activeBlock]);
    if (!activeProgram) return { ok: false, error: 'The active cycle block could not be found. Nothing changed.' };
    try {
        const advanced = E.advanceNextCycleForShell({ cycle, activeProgram, history: history(), legacyExercises: EXERCISES, makeId: E.uid });
        const nextCycle = advanced.cycle;
        const nextSaved = advanced.nextProgram ? [...saved().filter((p) => p.id !== advanced.nextProgram.id), advanced.nextProgram] : saved();
        requireCompleteCycleShell(nextCycle, nextSaved, { allowDone: true, expectedCycleId: cycle.id });
        if (!advanced.done && !advanced.nextProgram) throw new E.NextShellAdapterError('NEXT_CYCLE_INCOMPLETE', 'The next block is missing.');
        if (advanced.done && cycle.onComplete === 'loop') {
            const rolled = E.generateNextCycleForShell({ templateId: cycle.templateId, config: { ...(cycle.nextEngineCycle?.baseConfig || activeProgram.config), name: cycle.name },
                banned: S.store.banned || [], legacyExercises: EXERCISES, adaptBetweenBlocks: !!cycle.nextEngineCycle?.adaptBetweenBlocks, makeId: E.uid });
            const { ids } = requireCompleteCycleShell(rolled.cycle, rolled.blocks);
            if (rolled.blocks.length !== ids.length || rolled.cycle.id === cycle.id || rolled.blocks.some((b) => saved().some((p) => p.id === b.id)))
                throw new E.NextShellAdapterError('NEXT_CYCLE_INCOMPLETE', 'The new cycle could not be assembled safely.');
            rolled.cycle.onComplete = 'loop'; rolled.cycle.continuedFrom = cycle.id; rolled.cycle.generationRoute = E.GENERATION_ROUTE;
            rolled.blocks.forEach((b) => { b.generationRoute = E.GENERATION_ROUTE; });
            setList('saved', [...saved(), ...rolled.blocks]);
            setList('cycles', [...cycles().map((c) => (c.id === cycle.id ? nextCycle : c)), rolled.cycle]);
            S.store.pinnedId = rolled.blocks[0].id; markStore();
            return { ok: true, message: 'New training cycle ready. Your completed cycle and workout history are saved.' };
        }
        if (advanced.nextProgram) { advanced.nextProgram.generationRoute = E.GENERATION_ROUTE; setList('saved', nextSaved); S.store.pinnedId = advanced.nextProgram.id; }
        setList('cycles', cycles().map((c) => (c.id === cycle.id ? nextCycle : c))); markStore();
        return { ok: true, done: !!advanced.done, message: advanced.done ? 'Training cycle complete. Your programs and workout history are saved.'
            : advanced.insertedRecovery ? 'Recovery block added from your completed workouts.' : `Advanced to ${advanced.nextProgram.blockLabel || 'your next block'}` };
    } catch (err) { return cycleFail(err, 'The next block could not be prepared safely. Nothing changed.'); }
}
/* App.js doDeleteCycle: kept blocks become standalone programs (normalizeCycleLinks), undo restores. */
export function deleteCycle({ id, alsoBlocks = false }) {
    const c = cycles().find((x) => x.id === id); if (!c) return { ok: false };
    S.undo = { cycles: cycles(), saved: saved(), pinnedId: S.store.pinnedId };
    const rest = cycles().filter((x) => x.id !== id);
    setList('cycles', rest);
    if (alsoBlocks) setList('saved', saved().filter((p) => !c.blockIds.includes(p.id)));
    else setList('saved', E.normalizeCycleLinks(saved(), rest));
    markStore();
    return { ok: true, name: c.name };
}
/* App.js BlockReviewView. */
export function reviewBlock({ id, index }) {
    const c = cycles().find((x) => x.id === id); if (!c) return null;
    let r = null; try { r = E.blockReview(history(), c, index); } catch { r = null; }
    if (!r) return null;
    return { ...r, cycleName: c.name, label: r.block?.label || `Block ${index + 1}`, note: r.block?.note || '' };
}
/* App.js startNextBlock for a standalone program (cycle blocks advance through their cycle). */
export function nextBlock({ programId }) {
    const p = programById(programId); if (!p) return { ok: false, error: 'That program no longer exists.' };
    const owner = p.cycleId ? cycles().find((c) => c.id === p.cycleId) : null;
    if (owner) return { ok: false, error: owner.engineSource === 'pursuit-next' ? 'This program is part of a cycle. Complete the block from the cycle so the next phase is rebuilt from this block\'s workouts.' : 'This archived cycle can\'t continue here. Start a new cycle instead.' };
    try {
        let np;
        if (p.engineSource === 'pursuit-next') np = E.generateNextBlockFromShellHistory({ program: p, history: history(), legacyExercises: EXERCISES, makeId: E.uid }).program;
        else np = E.generateNextProgramForShell({ config: p.config, banned: S.store.banned || [], legacyExercises: EXERCISES, makeId: E.uid }).program;
        const m = (p.name || '').match(/^(.*?)\s*·\s*Block\s*(\d+)\s*$/);
        np.name = m ? `${m[1]} · Block ${Number(m[2]) + 1}` : `${p.name} · Block 2`;
        np.folder = p.folder; np.description = p.description; np.generationRoute = E.GENERATION_ROUTE;
        setList('saved', [...saved(), np]); S.store.pinnedId = np.id; markStore();
        return { ok: true, programId: np.id, name: np.name };
    } catch (err) { return cycleFail(err, 'The next block could not be created safely. Your current program is unchanged.'); }
}

/* ------------------------------------------------------------------ body + settings */
export function body() {
    const u = unit();
    let log = []; try { log = E.normalizeBwLog(S.store.bwLog || [], u) || []; } catch { log = []; }
    const lu = E.lengthUnitFor(u), raw = S.store.measurements || {}, measurements = {};
    Object.keys(raw).forEach((k) => { try { measurements[k] = E.normalizeMeasureLog(raw[k], lu, u); } catch { measurements[k] = []; } });
    return { unit: u, lengthUnit: lu, bodyweight: S.store.bodyweight || '', log: log.slice(-120).map((e) => ({ date: e.date, w: e.w })), measurements };
}
/* App.js logBodyweight: { date: ms, w, unit }, one entry per local day, sorted, capped. */
export function logBodyweight({ value }) {
    const v = Number(value); if (!(v > 0)) return { ok: false };
    const today = new Date().toDateString();
    const rows = (S.store.bwLog || []).filter((e) => new Date(e.date).toDateString() !== today);
    S.store.bwLog = [...rows, { date: now(), w: v, unit: unit() }].sort((a, b) => a.date - b.date).slice(-(E.BW_LOG_CAP || 2000));
    S.store.bodyweight = String(v); markStore();
    return { ok: true };
}
/* App.js logMeasurement: { date: ms, v, unit: length unit }. */
export function logMeasurement({ key, value }) {
    const v = Number(value); if (!key || !(v > 0)) return { ok: false };
    const today = new Date().toDateString();
    const m = { ...(S.store.measurements || {}) };
    m[key] = [...(m[key] || []).filter((e) => new Date(e.date).toDateString() !== today), { date: now(), v, unit: E.lengthUnitFor(unit()) }]
        .sort((a, b) => a.date - b.date).slice(-(E.BW_LOG_CAP || 2000));
    S.store.measurements = m; markStore(); return { ok: true };
}
export function settings() {
    const st = S.store;
    return { unit: unit(), restScale: Number(st.restScale) || 1, restAutoStart: st.restAutoStart !== false, haptics: st.haptics !== false,
        theme: st.theme || null, warmupCard: st.warmupCard !== false, lastBackup: st.lastBackup || null, sessions: history().length, programs: saved().length,
        reminders: reminders(), customCount: Array.isArray(st.custom) ? st.custom.length : 0,
        loadMode: st.loadMode === 'rpe' ? 'rpe' : 'rir', experience: st.experience || null,
        banned: (st.banned || []).map((id) => ({ id, name: exName(id) })) };
}
export function updateSettings(patch = {}) {
    const allowed = ['restScale', 'restAutoStart', 'haptics', 'theme', 'warmupCard', 'loadMode', 'experience'];
    if ('loadMode' in patch && !['rir', 'rpe'].includes(patch.loadMode)) delete patch.loadMode;
    if ('experience' in patch && !['novice', 'beginner', 'intermediate', 'advanced', ...Object.keys(E.EXP || {})].includes(patch.experience)) delete patch.experience;
    Object.entries(patch).forEach(([k, v]) => { if (allowed.includes(k)) S.store[k] = v; });
    if (patch.unit && patch.unit !== unit()) { const r = switchUnit({ to: patch.unit }); if (!r.ok) return r; }
    applyGlobals(); markStore();
    return { ok: true, settings: settings() };
}
export function markBackup() { S.store.lastBackup = now(); markStore(); return { ok: true }; }

/* ------------------------------------------------------------------ calendar (App.js TrainingHeatmap) */
function calendarDate(value) {
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
        const [y, m, d] = value.split('-').map(Number); const local = new Date(y, m - 1, d);
        return local.getFullYear() === y && local.getMonth() === m - 1 && local.getDate() === d ? local : null;
    }
    if (value == null || value === '') return null;
    const date = new Date(value); return Number.isFinite(date.getTime()) ? date : null;
}
/* App.js calendarSessionSummary: display-only, never invents a zero for missing legacy fields. */
function calendarSummary(h) {
    const valid = (v) => v !== undefined && v !== null && v !== '' && Number.isFinite(Number(v)) && Number(v) >= 0;
    const observed = Object.values(h?.perf || {}).reduce((n, p) => n + (Array.isArray(p?.sets) ? p.sets.filter((s) => s?.done === true).length : 0), 0);
    const sets = valid(h?.setsDone) ? Number(h.setsDone) : observed || null;
    const volume = valid(h?.volume) ? Number(h.volume) : valid(h?.tonnage) ? Number(h.tonnage) : null;
    const minutes = valid(h?.durationMin) && Number(h.durationMin) > 0 ? Math.round(Number(h.durationMin))
        : valid(h?.duration) && Number(h.duration) > 0 ? Math.max(1, Math.round(Number(h.duration) / 60)) : null;
    const facts = [sets !== null ? `${sets} ${sets === 1 ? 'set' : 'sets'}` : null, volume !== null && h?.unit ? `${volume.toLocaleString()} ${h.unit}` : null, minutes !== null ? `${minutes}m` : null].filter(Boolean);
    return facts.length ? facts.join(' · ') : 'Summary not recorded';
}
export function calendar({ year, month } = {}) {
    const t = new Date(); const y = Number.isInteger(year) ? year : t.getFullYear(), m = Number.isInteger(month) ? month : t.getMonth();
    const days = {};
    history().forEach((h) => {
        const d = calendarDate(h?.date); if (!d || d.getFullYear() !== y || d.getMonth() !== m) return;
        (days[d.getDate()] = days[d.getDate()] || []).push({
            id: h.id, label: h.dayLabel || h.programName || 'Workout', summary: calendarSummary(h),
            lifts: Object.entries(h.perf || {}).filter(([id]) => EX_BY_ID[id]).map(([id, p]) => {
                const sets = p.sets && p.sets.length ? p.sets : (p.weight != null ? [{ w: p.weight, r: p.reps }] : []);
                return { name: exName(id), sets: sets.filter((s) => !s.warm).map((s) => (Number(s.w) > 0 ? fmtW(s.w) : 'BW') + (s.r != null ? '×' + s.r : '')).join(', ') };
            }).filter((l) => l.sets)
        });
    });
    const list = Object.entries(days).map(([d, sessions]) => ({ day: Number(d), sessions }));
    return { year: y, month: m, firstDow: new Date(y, m, 1).getDay(), daysInMonth: new Date(y, m + 1, 0).getDate(),
        days: list, trainingDays: list.length, workouts: list.reduce((n, d) => n + d.sessions.length, 0),
        earliest: (() => { const ds = history().map((h) => calendarDate(h?.date)).filter(Boolean); return ds.length ? Math.min(...ds.map((d) => d.getTime())) : null; })() };
}

/* ------------------------------------------------------------------ profile (App.js ProfileView, StrengthSnapshotCard, ProfileStrengthClub) */
function age() { const a = E.ageFrom(S.store.birth); return a !== '' ? a : (Number(S.store.age) > 0 ? Number(S.store.age) : ''); }
export function profile() {
    const H = history(), u = unit(), bw = parseFloat(S.store.bodyweight), sex = S.store.sex || 'male', a = age();
    let ms = []; try { ms = E.computeMilestones(H, { unit: u, bodyweight: S.store.bodyweight }) || []; } catch { ms = []; }
    let lvl = null; try { lvl = E.computeLevel(H, ms); } catch { lvl = null; }
    let score = null; try { score = bw > 0 ? E.strengthScore(H, bw, u, sex, a) : null; } catch { score = null; }
    let trend = null; try { trend = bw > 0 ? E.strengthScoreHistory(H, bw, u, sex, a) : null; } catch { trend = null; }
    let trends = []; try { trends = E.exerciseTrends(H) || []; } catch { trends = []; }
    const scorable = !(bw > 0) && trends.some((t) => E.scoreTargetFor(t.id) && t.prBest > 0);
    const club = (mode) => {
        let c = null; try { c = E.strengthClubSnapshot(trends, u, mode); } catch { c = null; }
        if (!c || !c.hasAny) return null;
        const r1 = (v) => (u === 'lb' ? Math.round(v) : Math.round(v * 2) / 2);
        return { mode, complete: c.complete, allLogged: c.allLogged, total: r1(c.total), inThousand: c.inThousand, maxedOut: c.maxedOut,
            current: c.current ? c.current.name : null, next: c.next ? c.next.name : null, remaining: r1(c.remaining), pct: Math.round(c.pct),
            thousandRemaining: r1(c.thousandRemaining), thousandPct: Math.round(c.thousandPct),
            rows: c.rows.map((r) => ({ label: r.label, value: r1(r.val), has: r.has, actual: r.actual })) };
    };
    const tPoints = (() => { const pts = trend?.overall || trend?.points || trend?.series || []; return Array.isArray(pts) ? pts.slice(-40).map((p) => (typeof p === 'number' ? p : Number(p?.score ?? p?.v ?? p?.y ?? p?.overall ?? 0))) : []; })();
    const val = (id) => ms.find((m) => m.id === id)?.value || 0;
    const next = ms.filter((m) => !m.done).sort((x, y) => (y.progress - x.progress) || (x.target - y.target))[0] || null;
    return {
        unit: u, bodyweight: S.store.bodyweight || '', sex, birth: S.store.birth || '', age: a,
        level: lvl ? { level: lvl.level, title: lvl.title || null, xp: Math.round(lvl.xp || 0), into: Math.round(lvl.xpIntoLevel || 0), span: Math.round(lvl.xpForLevel || 0) } : null,
        summary: { workouts: val('first'), bestStreak: val('streak3'), volume: Math.round(val('vol1')) },
        score: score ? { overall: Math.round(score.overall), level: score.level, levelIdx: score.levelIdx, estimated: !!score.estimated, liftCount: score.liftCount,
            subs: (score.subs || []).map((x) => ({ label: x.label, score: Math.round(x.score), lift: x.lift ? { name: x.lift.name, level: x.lift.lvl?.level || null, est: !!x.lift.est } : null })),
            next: score.nextMilestone ? { name: score.nextMilestone.name, pattern: score.nextMilestone.pattern, current: score.nextMilestone.current, target: score.nextMilestone.target, unit: score.nextMilestone.unit, level: score.nextMilestone.level } : null,
            trend: tPoints } : null,
        scoreLocked: scorable,
        clubLifted: club('lifted'), clubE1rm: club('e1rm'),
        achievements: ms.map((m) => ({ id: m.id, label: m.label, desc: m.desc || m.description || '', cat: m.cat || '', done: !!m.done, progress: Math.max(0, Math.min(1, Number(m.progress) || 0)), value: m.value ?? null, target: m.target ?? null })),
        nextAchievement: next ? { label: next.label, progress: Math.max(0, Math.min(1, Number(next.progress) || 0)) } : null,
        levels: E.STD_LEVELS
    };
}
/* App.js Settings → Profile: sex adjusts the standards; birth (YYYY-MM-DD) gives the age factor. */
export function setProfile({ sex, birth } = {}) {
    if (sex === 'male' || sex === 'female') S.store.sex = sex;
    if (birth !== undefined) {
        if (birth === '' || birth === null) { S.store.birth = ''; S.store.birthEst = false; }
        else if (E.birthParts(birth)) { S.store.birth = birth; S.store.birthEst = false; S.store.age = E.ageFrom(birth); }
        else return { ok: false, error: 'Use a real date, like 1994-06-21.' };
    }
    markStore(); return { ok: true };
}

/* ------------------------------------------------------------------ tools (App.js PlateCalcSheet, OneRepMaxCalc) */
export function plateCalc({ total, bar = 'barbell' } = {}) {
    const u = unit(), t = Number(total);
    const bars = Object.keys(E.BARS).map((k) => ({ id: k, label: E.BAR_LABEL?.[k] || k, weight: E.barWeightFor(k, u) }));
    if (!(t > 0)) return { unit: u, bars, bar, result: null };
    const r = E.platesPerSide(t, E.BARS[bar] ? bar : 'barbell', u);
    const achievable = r.barW + r.plates.reduce((s, p) => s + p, 0) * 2;
    return { unit: u, bars, bar, result: { barWeight: r.barW, plates: r.plates, achievable: Math.round(achievable * 100) / 100, exact: Math.abs(achievable - t) < 1e-6 } };
}
export function oneRm({ weight, reps } = {}) {
    const W = Number(weight), R = parseInt(reps), u = unit();
    if (!(W > 0 && R > 0 && R <= 20)) return { unit: u, e1rm: null };
    const orm = E.e1rm(W, R), inc = u === 'lb' ? 5 : 2.5, down = (v) => Math.floor(v / inc) * inc;
    return { unit: u, e1rm: Math.round(orm * 10) / 10,
        percents: [100, 95, 90, 85, 80, 75, 70, 65, 60].map((p) => ({ pct: p, load: down(orm * p / 100) })),
        reps: [1, 2, 3, 5, 8, 10, 12, 15].map((n) => ({ reps: n, load: down(E.rmAt(orm, n)) })) };
}

/* ------------------------------------------------------------------ gyms + loading (App.js GymsSheet, Settings plates / increments) */
export function gyms() {
    const g = E.normalizeGyms(S.store.gyms, S.store.activeGymId), u = unit();
    const cats = [...new Set(E.EQUIPMENT.map((e) => e.cat || 'Free Weights'))];
    return {
        unit: u, activeGymId: g.activeGymId,
        gyms: g.gyms.map((x) => ({ ...x, limitKeys: E.GYM_LIMIT_KEYS.filter((k) => k.match(x.equipment)).map((k) => ({ key: k.key, label: k.label, sub: k.sub })),
            inventoryKeys: E.INVENTORY_KEYS.filter((k) => k.match(x.equipment)).map((k) => ({ key: k.key, label: k.label, sub: k.sub })),
            barDefaults: { barbell: E.BARS.barbell[x.barWeights?.unit === 'kg' ? 'kg' : 'lb'], smith: E.BARS.smith[x.barWeights?.unit === 'kg' ? 'kg' : 'lb'] } })),
        presets: E.GYM_PRESETS.map((p) => ({ key: p.key, name: p.name, equipment: p.equipment })),
        equipment: cats.map((c) => ({ cat: c, items: E.EQUIPMENT.filter((e) => (e.cat || 'Free Weights') === c).map((e) => ({ id: e.id, label: e.label })).sort((a, b) => a.label.localeCompare(b.label)) })),
        plates: { all: E.ALL_PLATES[u], on: (S.store.plates?.[u] || E.PLATES[u]) },
        minInc: S.store.minInc || { v: 0, unit: null },
        minIncChoices: u === 'kg' ? [[0, 'Std'], [2.5, '2.5'], [1, '1']] : [[0, 'Std'], [1, '1'], [0.5, '0.5']]
    };
}
/* App.js GymsSheet commit + onSave: normalized, timestamped, deletions tombstoned, equipDefault follows. */
export function saveGyms({ gyms: list, activeGymId, removed = [] } = {}) {
    const t = now();
    const prev = new Map((Array.isArray(S.store.gyms) ? S.store.gyms : []).map((g) => [g.id, g]));
    const stamped = (Array.isArray(list) ? list : []).map((g) => {
        const before = prev.get(g.id); const same = before && JSON.stringify({ ...before, updatedAt: 0 }) === JSON.stringify({ ...g, updatedAt: 0 });
        return { ...g, updatedAt: same ? before.updatedAt : t };
    });
    const n = E.normalizeGyms(stamped, activeGymId);
    const keep = new Map(stamped.map((g) => [g.id, g.updatedAt]));
    S.store.gyms = n.gyms.map((g) => ({ ...g, updatedAt: keep.get(g.id) || t }));
    S.store.activeGymId = n.activeGymId;
    const a = E.gymById(n.gyms, n.activeGymId); if (a) S.store.equipDefault = a.equipment;
    if (removed.length) S.store.tombs = { ...(S.store.tombs || {}), ...Object.fromEntries(removed.map((id) => [id, t])) };
    applyGlobals(); markStore();
    return { ok: true };
}
export function newGym({ preset } = {}) {
    const p = E.GYM_PRESETS.find((x) => x.key === preset) || E.GYM_PRESETS[0];
    const existing = E.normalizeGyms(S.store.gyms, S.store.activeGymId).gyms;
    let name = p.name, k = 2; while (existing.some((g) => g.name === name)) name = `${p.name} ${k++}`;
    return { id: E.newGymId(), name, equipment: [...p.equipment] };
}
export function setPlates({ plates }) {
    const u = unit(), all = E.ALL_PLATES[u];
    const v = [...new Set((plates || []).map(Number).filter((p) => all.includes(p)))].sort((a, b) => b - a);
    S.store.plates = { ...(S.store.plates || { kg: [...E.PLATES.kg], lb: [...E.PLATES.lb] }), [u]: v };
    applyGlobals(); markStore(); return { ok: true };
}
export function setMinInc({ v }) {
    const n = Number(v) || 0; S.store.minInc = { v: n, unit: n ? unit() : null };
    applyGlobals(); markStore(); return { ok: true };
}

/* ------------------------------------------------------------------ unit switching (App.js switchUnit) */
/* Converts every stored load (programs, training maxes, increments, perf, goals, bodyweight, logs);
   history keeps the unit each session was logged in. */
export function switchUnit({ to }) {
    const from = unit();
    if (to !== 'kg' && to !== 'lb') return { ok: false };
    if (to === from) { S.store.unitChosen = true; markStore(); return { ok: true, converted: false }; }
    // An open workout holds typed loads in the old unit; the web app only offers this from Settings,
    // outside a workout, so the same order applies here.
    if (S.live) return { ok: false, error: 'Finish or discard the workout in progress first.' };
    const k = to === 'lb' ? 2.2046226 : 1 / 2.2046226;
    const conv = (v) => { const n = parseFloat(v); return n > 0 ? Math.round(n * k * 2) / 2 : v; };
    const convMap = (m) => { if (!m) return m; const o = {}; for (const key in m) o[key] = conv(m[key]); return o; };
    const convPerf = (pf) => {
        if (!pf) return pf; const o = {};
        for (const id in pf) { const p = pf[id]; if (!p) continue; o[id] = { ...p, weight: conv(p.weight), sets: Array.isArray(p.sets) ? p.sets.map((s) => ({ ...s, w: conv(s.w) })) : p.sets }; }
        return o;
    };
    const convProgram = (p) => {
        if (!p) return p;
        const np = { ...p, config: { ...p.config, unit: to, ...(p.config?.loadingInventory ? { loadingInventory: E.loadingInventoryInUnit(p.config.loadingInventory, to) } : {}) },
            ...(p.trainingMax ? { trainingMaxUnit: to } : {}) };
        if (np.trainingMax) np.trainingMax = convMap(np.trainingMax);
        if (np.gz) { const g = {}; for (const id in np.gz) g[id] = { ...np.gz[id], ...(np.gz[id]?.t2w != null ? { t2w: conv(np.gz[id].t2w) } : {}) }; np.gz = g; }
        if (np.exLoadInc) { const e = {}; for (const id in np.exLoadInc) { const v = np.exLoadInc[id]; e[id] = (v && v.v != null) ? { ...v, v: conv(v.v), unit: to } : v; } np.exLoadInc = e; }
        return np;
    };
    const st = S.store;
    st.perf = convPerf(st.perf);
    setList('saved', saved().map(convProgram));
    if (st.goals) { const o = {}; for (const id in st.goals) { const n = parseFloat(st.goals[id]); o[id] = n > 0 ? Math.round(n * k * 2) / 2 : st.goals[id]; } st.goals = o; }
    { const n = parseFloat(st.bodyweight); if (n > 0) st.bodyweight = String(Math.round(n * k * 2) / 2); }
    if (Array.isArray(st.bwLog)) st.bwLog = st.bwLog.map((e) => ({ ...e, w: conv(e.w), unit: to }));
    if (st.measurements && typeof st.measurements === 'object') { const lu = E.lengthUnitFor(to), o = {}; for (const key in st.measurements) o[key] = E.normalizeMeasureLog(st.measurements[key], lu, from); st.measurements = o; }
    st.unit = to; st.unitChosen = true;
    applyGlobals(); markStore();
    return { ok: true, converted: true };
}

/* ------------------------------------------------------------------ program editing (App.js ProgramView + save review) */
/* Edits go to a draft (the store's `drafts`, as on the web app) and reach the program only on save,
   through the same programSavePlan, so a cycle block can carry its edit to its sibling blocks. */
function draftOf(id) { return S.store.drafts?.[id] || programById(id); }
function putDraft(p) { S.store.drafts = { ...(S.store.drafts || {}), [p.id]: p }; markStore(); }
function dropDraft(id) { if (S.store.drafts && id in S.store.drafts) { const { [id]: _drop, ...rest } = S.store.drafts; S.store.drafts = rest; markStore(); } }
function gymKit(p) {
    const g = E.normalizeGyms(S.store.gyms, S.store.activeGymId), gym = E.gymById(g.gyms, g.activeGymId);
    return E.expandEquipment(gym?.equipment?.length ? gym.equipment : p.config?.equipment || []);
}
function reindexSlotMap(map, dayId, fn) {
    if (!map) return map; const out = {};
    Object.entries(map).forEach(([k, v]) => { const [d, sStr] = k.split(':'); if (d !== dayId) { out[k] = v; return; } const s = fn(+sStr); if (s != null) out[`${d}:${s}`] = v; });
    return out;
}
function clearDayKeys(map, dayId) { if (!map) return map; const out = {}; Object.keys(map).forEach((k) => { if (!k.startsWith(dayId + ':')) out[k] = map[k]; }); return out; }
function addSlot(p, dayId, ex) {
    const day = p.days.find((d) => d.id === dayId); if (!day || !ex || day.exercises.includes(ex.id)) return p;
    const newSlot = day.exercises.length, key = `${dayId}:${newSlot}`;
    const days = p.days.map((d) => (d.id !== dayId ? d : { ...d, exercises: [...d.exercises, ex.id], primaryIndex: d.primaryIndex ?? 0 }));
    const nextExerciseId = E.nextExerciseIdForShellExercise(ex);
    const overrides = { ...(p.overrides || {}), [key]: { ...(p.overrides?.[key] || {}), added: true, nextEngine: true, legacyExerciseId: ex.id, ...(nextExerciseId ? { nextExerciseId } : {}) } };
    const nextWeekPrescriptions = { ...(p.nextWeekPrescriptions || {}), [key]: E.buildUserAddedSlotPrescriptions({ repRange: ex.rep, weeks: E.weeksOf(p), compound: ex.type === 'compound' }) };
    return { ...p, days, overrides, nextWeekPrescriptions, edited: true };
}
const EDITS = {
    // App.js replaceSlot: the primary lift only moves when the slot that was primary is replaced.
    replace(p, { dayId, slot, newId }) {
        const ex = EX_BY_ID[newId]; if (!ex) return p;
        const days = p.days.map((d) => {
            if (d.id !== dayId) return d;
            const exercises = [...d.exercises]; exercises[slot] = newId;
            let pi = d.primaryIndex;
            if (slot === d.primaryIndex || pi == null || pi < 0 || pi >= exercises.length || EX_BY_ID[exercises[pi]]?.type !== 'compound') { pi = exercises.findIndex((id) => EX_BY_ID[id]?.type === 'compound'); if (pi < 0) pi = 0; }
            return { ...d, exercises, primaryIndex: pi };
        });
        const key = `${dayId}:${slot}`, nextExerciseId = E.nextExerciseIdForShellExercise(ex);
        const overrides = { ...(p.overrides || {}), [key]: { ...(p.overrides?.[key] || {}), nextEngine: true, legacyExerciseId: newId, ...(nextExerciseId ? { nextExerciseId } : {}) } };
        return { ...p, days, overrides, edited: true };
    },
    // App.js removeSlot: keeps one exercise; later slots, overrides and set bias shift down with their lifts.
    remove(p, { dayId, slot }) {
        const d0 = p.days.find((d) => d.id === dayId); if (!d0 || d0.exercises.length <= 1) return p;
        const days = p.days.map((d) => {
            if (d.id !== dayId) return d;
            const exercises = d.exercises.filter((_, i) => i !== slot);
            let pi = d.primaryIndex;
            if (slot === d.primaryIndex) { pi = exercises.findIndex((id) => EX_BY_ID[id]?.type === 'compound'); if (pi < 0) pi = 0; } else if (slot < d.primaryIndex) pi = d.primaryIndex - 1;
            return { ...d, exercises, primaryIndex: pi };
        });
        const shift = (s) => (s === slot ? null : s > slot ? s - 1 : s);
        return { ...p, days, overrides: reindexSlotMap(p.overrides || {}, dayId, shift), slotBias: reindexSlotMap(p.slotBias, dayId, shift),
            nextWeekPrescriptions: E.removeNextSlotPrescription(p.nextWeekPrescriptions, dayId, slot), ss: clearDayKeys(p.ss, dayId), rounds: clearDayKeys(p.rounds, dayId), edited: true };
    },
    // App.js moveSlot: the primary lift travels with its exercise.
    move(p, { dayId, slot, dir }) {
        const d0 = p.days.find((d) => d.id === dayId), target = slot + dir;
        if (!d0 || target < 0 || target >= d0.exercises.length) return p;
        const days = p.days.map((d) => {
            if (d.id !== dayId) return d;
            const exercises = [...d.exercises]; [exercises[slot], exercises[target]] = [exercises[target], exercises[slot]];
            let pi = d.primaryIndex; if (pi === slot) pi = target; else if (pi === target) pi = slot;
            if (pi == null || pi < 0 || pi >= exercises.length) { pi = exercises.findIndex((id) => EX_BY_ID[id]?.type === 'compound'); if (pi < 0) pi = 0; }
            return { ...d, exercises, primaryIndex: pi };
        });
        const swap = (s) => (s === slot ? target : s === target ? slot : s);
        return { ...p, days, overrides: reindexSlotMap(p.overrides || {}, dayId, swap), slotBias: reindexSlotMap(p.slotBias, dayId, swap),
            nextWeekPrescriptions: E.swapNextSlotPrescriptions(p.nextWeekPrescriptions, dayId, slot, target), ss: clearDayKeys(p.ss, dayId), rounds: clearDayKeys(p.rounds, dayId), edited: true };
    },
    // App.js addSpecific: an added slot is owned by the lifter in every progression mode.
    add(p, { dayId, exId }) { return addSlot(p, dayId, EX_BY_ID[exId]); },
    // App.js toggleSS: pairs this slot with the next; clears the day's rounds.
    superset(p, { dayId, slot }) { const ss = { ...(p.ss || {}) }, key = `${dayId}:${slot}`; if (ss[key]) delete ss[key]; else ss[key] = true; return { ...p, ss, rounds: clearDayKeys(p.rounds, dayId) }; },
    // App.js editOverride, manual-progression sets/reps only (auto mode prescribes them).
    override(p, { dayId, slot, field, value }) {
        if (!['sets', 'reps'].includes(field)) return p;
        const k = `${dayId}:${slot}`, cur = p.overrides?.[k] || {};
        const val = field === 'sets' ? E.clamp(parseInt(value) || 1, 1, 20) : String(value || '').slice(0, 9);
        return { ...p, overrides: { ...(p.overrides || {}), [k]: E.markUserPrescriptionOverride(cur, field, val) } };
    },
    renameDay(p, { dayId, label }) { return { ...p, days: p.days.map((d) => (d.id === dayId ? { ...d, label: String(label || '').trim() || d.label } : d)) }; },
    dayNote(p, { dayId, note }) { return { ...p, days: p.days.map((d) => (d.id === dayId ? { ...d, note: String(note || '').trim() || undefined } : d)) }; },
    addDay(p) { return { ...p, days: [...p.days, { id: E.uid(), label: `Day ${p.days.length + 1}`, type: 'custom', exercises: [], primaryIndex: 0 }] }; },
    deleteDay(p, { dayId }) { return p.days.length <= 1 ? p : { ...p, days: p.days.filter((d) => d.id !== dayId) }; },
    moveDay(p, { dayId, dir }) { const i = p.days.findIndex((d) => d.id === dayId), j = i + dir; if (i < 0 || j < 0 || j >= p.days.length) return p; const days = [...p.days]; [days[i], days[j]] = [days[j], days[i]]; return { ...p, days }; },
    // App.js copyDay: overrides, supersets, rounds and the engine's week prescriptions copy with it.
    copyDay(p, { dayId }) {
        const src = p.days.find((d) => d.id === dayId); if (!src) return p;
        const nid = E.uid(), newDay = { ...src, id: nid, label: `${src.label} (copy)`, exercises: [...src.exercises] };
        const dup = (m) => { const o = { ...(m || {}) }; Object.entries(m || {}).forEach(([k, v]) => { const [d, s] = k.split(':'); if (d === dayId) o[`${nid}:${s}`] = typeof v === 'object' && v ? { ...v } : v; }); return o; };
        const idx = p.days.findIndex((d) => d.id === dayId);
        const days = [...p.days.slice(0, idx + 1), newDay, ...p.days.slice(idx + 1)];
        return { ...p, days, overrides: dup(p.overrides), ss: dup(p.ss), rounds: dup(p.rounds), nextWeekPrescriptions: E.cloneNextDayPrescriptions(p.nextWeekPrescriptions, dayId, nid), config: { ...p.config, days: days.length }, edited: true };
    },
    rename(p, { name }) { const n = String(name || '').trim().slice(0, 60); return n ? { ...p, name: n } : p; }
};
export function editor({ programId, weekIndex = 1 } = {}) {
    const p = draftOf(programId); if (!p) return null;
    const stored = programById(programId), wk = Math.max(1, Math.min(E.weeksOf(p), Number(weekIndex) || 1));
    const manual = p.config?.progression === 'manual';
    return { programId: p.id, name: p.name, weekIndex: wk, weeksTotal: E.weeksOf(p), manual, cycleId: p.cycleId || null,
        dirty: !!(stored && S.store.drafts?.[programId] && !E.sameProgramContent(stored, p)),
        canUndoSave: !!(S.saveUndo && S.saveUndo.id === programId),
        days: p.days.map((d, di) => {
            let mins = null; try { mins = E.estimateMinutes(p, d, wk); } catch { mins = null; }
            return { id: d.id, index: di, label: d.label, note: d.note || '', minutes: mins,
                exercises: d.exercises.map((_id, slot) => {
                    const id = d.exercises[slot]; const ex = EX_BY_ID[id];
                    let cell = null; try { cell = E.computeCell(p, d, id, slot, wk); } catch { cell = null; }
                    const ov = p.overrides?.[`${d.id}:${slot}`] || {};
                    return { slot, id, name: ex?.name || id, part: ex?.part || '', partLabel: E.PART_LABEL?.[ex?.part] || ex?.part || '', primary: slot === d.primaryIndex,
                        superset: !!p.ss?.[`${d.id}:${slot}`], added: !!ov.added, sets: ov.sets ?? cell?.sets ?? null, reps: ov.reps ?? cell?.reps ?? null, rir: cell?.rir ?? null };
                }) };
        }) };
}
export function editAct({ programId, type, ...args } = {}) {
    const p = draftOf(programId), fn = EDITS[type];
    if (!p || !fn) return { ok: false };
    const next = fn(p, args);
    if (next !== p) putDraft(next);
    return { ok: true, changed: next !== p };
}
export function editSwapOptions({ programId, dayId, slot }) {
    const p = draftOf(programId), day = p?.days.find((d) => d.id === dayId); if (!day) return [];
    const curId = day.exercises[slot], cur = EX_BY_ID[curId]; if (!cur) return [];
    const used = new Set(day.exercises.filter((_, i) => i !== slot));
    const pool = E.availableFor(cur.part, gymKit(p), S.store.banned || [], !!p.config?.noBodyweight).filter((e) => e.id !== curId && !used.has(e.id));
    let ranked = pool; try { ranked = E.rankSwapAlts(cur, pool, E.starvedRegions(p), [...used].map((id) => EX_BY_ID[id]), p, day) || pool; } catch { ranked = pool; }
    return ranked.slice(0, 16).map((x) => ({ id: x.id, name: x.name, equip: x.equip, part: x.part }));
}
export function editAddOptions({ programId, dayId, q = '', part = null }) {
    const p = draftOf(programId), day = p?.days.find((d) => d.id === dayId); if (!day) return { parts: [], items: [] };
    const kit = gymKit(p), used = new Set(day.exercises), banned = new Set(S.store.banned || []), needle = String(q).trim().toLowerCase();
    const parts = (E.PART_ORDER || [...new Set(EXERCISES.map((x) => x.part))]).filter((pt) => E.availableFor(pt, kit, [...banned], !!p.config?.noBodyweight).some((e) => !used.has(e.id)))
        .map((pt) => ({ id: pt, label: E.PART_LABEL?.[pt] || pt }));
    const pool = part ? E.availableFor(part, kit, [...banned], !!p.config?.noBodyweight) : EXERCISES.filter((x) => !banned.has(x.id) && E.availableFor(x.part, kit, [...banned], !!p.config?.noBodyweight).some((e) => e.id === x.id));
    const items = pool.filter((x) => !used.has(x.id) && (!needle || x.name.toLowerCase().includes(needle)))
        .sort((a, b) => (a.type === 'compound' ? 0 : 1) - (b.type === 'compound' ? 0 : 1)).slice(0, 60).map((x) => ({ id: x.id, name: x.name, part: x.part, equip: x.equip }));
    return { parts, items };
}
/* App.js handleSave + confirmProgramSave: scope "cycle" carries the edit to the sibling blocks. */
export function saveEdits({ programId, scope = 'program' }) {
    const edited = S.store.drafts?.[programId]; if (!edited) return { ok: true, changes: 0 };
    const changes = E.programSavePlan(edited, saved(), cycles(), scope);
    if (!changes.length) { dropDraft(programId); return { ok: true, changes: 0 }; }
    const conflicts = changes.filter(({ after }) => after.id !== programId && S.store.drafts?.[after.id] && !E.sameProgramContent(S.store.drafts[after.id], programById(after.id)));
    if (conflicts.length) return { ok: false, error: `${conflicts.length === 1 ? 'Another block has' : `${conflicts.length} other blocks have`} unsaved edits. Save or discard ${conflicts.length === 1 ? 'it' : 'them'} first.` };
    const byId = new Map(changes.map((c) => [c.after.id, c.after]));
    setList('saved', saved().map((p) => byId.get(p.id) || p));
    dropDraft(programId);
    S.saveUndo = { id: programId, changes };
    return { ok: true, changes: changes.length };
}
export function discardEdits({ programId }) { dropDraft(programId); return { ok: true }; }
/* App.js undoProgramSave: only while nothing has been edited since. */
export function undoSave({ programId }) {
    const u = S.saveUndo; if (!u || u.id !== programId) return { ok: false };
    if (!E.canUndoProgramSave(u, saved(), S.store.drafts || {}, programById(programId))) { S.saveUndo = null; return { ok: false, error: 'Something changed since that save, so it can\'t be undone.' }; }
    const before = new Map(u.changes.map((c) => [c.before.id, c.before]));
    setList('saved', saved().map((p) => before.get(p.id) || p)); S.saveUndo = null;
    return { ok: true };
}
/* App.js handleDelete / doDeleteProgram (cycle blocks stay with their cycle). */
export function deleteProgram({ id }) {
    const removed = programById(id); if (!removed) return { ok: false };
    const owner = cycles().find((c) => c.blockIds.includes(id));
    if (owner?.engineSource === 'pursuit-next') return { ok: false, error: 'Cycle blocks stay with their cycle. Delete the cycle instead, or duplicate this block as a standalone program.' };
    if (S.live?.programId === id) return { ok: false, error: 'Finish or discard the workout in progress first.' };
    S.undo = { saved: saved(), cycles: cycles(), pinnedId: S.store.pinnedId };
    setList('saved', saved().filter((p) => p.id !== id));
    if (S.store.pinnedId === id) S.store.pinnedId = null;
    setList('cycles', cycles().map((c) => {
        if (!c.blockIds.includes(id)) return c;
        const ri = c.blockIds.indexOf(id), blockIds = c.blockIds.filter((b) => b !== id), blockMeta = (c.blockMeta || []).filter((m) => m.id !== id), was = c.activeBlock || 0;
        return { ...c, blockIds, blockMeta, activeBlock: Math.max(0, Math.min(blockIds.length - 1, ri < was ? was - 1 : was)) };
    }).filter((c) => c.blockIds.length));
    dropDraft(id);
    return { ok: true, name: removed.name };
}
/* App.js handleDuplicate: a copied cycle block becomes standalone. */
export function duplicateProgram({ id }) {
    const p = programById(id); if (!p) return { ok: false };
    const copy = { ...p, id: E.uid(), name: `${p.name} (copy)`, createdAt: now() };
    if (copy.cycleId) {
        const detached = copy.nextEngine?.cycleTemplate ? { ...copy.nextEngine.cycleTemplate } : null;
        delete copy.cycleId; delete copy.cycleIndex; delete copy.blockLabel;
        if (copy.nextEngine?.cycleTemplate) { const { cycleTemplate, ...rest } = copy.nextEngine; copy.nextEngine = { ...rest, detachedFromCycle: detached }; }
    }
    setList('saved', [...saved(), copy]);
    return { ok: true, id: copy.id, name: copy.name, detached: !!p.cycleId };
}

/* ------------------------------------------------------------------ custom exercises (App.js AddSheet create + createCustom) */
export function createCustomExercise({ name, part, type = 'compound', equip = [], lo = 8, hi = 12, programId = null, dayId = null } = {}) {
    const n = String(name || '').trim().slice(0, 60), L = Math.round(Number(lo)), H = Math.round(Number(hi));
    if (!n || !(L > 0) || !(H >= L) || !E.PART_LABEL?.[part]) return { ok: false, error: 'Give it a name, a muscle and a rep range.' };
    if (!['compound', 'isolation'].includes(type)) type = 'compound';
    const ex = { id: `custom-${E.uid()}`, pri: 9000, custom: true, name: n, part, type, equip: (Array.isArray(equip) ? equip : []).filter((e) => E.ALL_EQUIP_IDS.includes(e)), rep: [L, H] };
    setList('custom', [...(Array.isArray(S.store.custom) ? S.store.custom : []), ex]);
    syncCustomRegistry();
    if (programId && dayId) editAct({ programId, type: 'add', dayId, exId: ex.id });
    return { ok: true, id: ex.id, name: ex.name };
}
export function customExercises() {
    const used = new Set(saved().flatMap((p) => p.days.flatMap((d) => d.exercises)));
    return (Array.isArray(S.store.custom) ? S.store.custom : []).map((x) => ({ id: x.id, name: x.name, part: x.part, partLabel: E.PART_LABEL?.[x.part] || x.part, type: x.type, rep: x.rep, equip: x.equip || [], inUse: used.has(x.id) }));
}
/* A custom lift a program still uses is kept: deleting it would orphan those slots. */
export function deleteCustomExercise({ id }) {
    const list = Array.isArray(S.store.custom) ? S.store.custom : [];
    if (!list.some((x) => x.id === id)) return { ok: false };
    if (saved().some((p) => p.days.some((d) => d.exercises.includes(id)))) return { ok: false, error: 'A program still uses it. Swap it out first.' };
    setList('custom', list.filter((x) => x.id !== id)); syncCustomRegistry();
    return { ok: true };
}

/* ------------------------------------------------------------------ CSV export (App.js exportCSV / exportBodyCSV) */
const isoLocal = (t) => { const d = new Date(t); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const csvEsc = (c) => { const v = String(c ?? ''); return /[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v; };
export function exportCsv({ kind = 'workouts' } = {}) {
    const u = unit();
    if (kind === 'body') {
        const rows = [['Date', 'Metric', 'Value', 'Unit']], lu = E.lengthUnitFor(u);
        E.normalizeBwLog(S.store.bwLog || [], u).forEach((e) => rows.push([isoLocal(e.date), 'Bodyweight', e.w, e.unit]));
        Object.entries(S.store.measurements || {}).forEach(([k, arr]) => E.normalizeMeasureLog(arr, lu, u).forEach((e) => rows.push([isoLocal(e.date), k.charAt(0).toUpperCase() + k.slice(1), e.v, e.unit])));
        return rows.map((r) => r.map(csvEsc).join(',')).join('\n');
    }
    const rows = [['Date', 'Program', 'Day', 'Exercise', 'Muscle', 'Top weight', 'Top reps', 'Sets', 'Volume', 'Unit', 'Duration (min)']];
    [...history()].sort((a, b) => a.date - b.date).forEach((h) => {
        const d = isoLocal(h.date), entries = Object.entries(h.perf || {});
        if (!entries.length) rows.push([d, h.programName, h.dayLabel, '', '', '', '', 0, 0, h.unit || u, h.durationMin || '']);
        entries.forEach(([id, p]) => {
            const ex = EX_BY_ID[id];
            const vol = Math.round((p.sets || []).reduce((a, x) => a + (x.w || 0) * (x.r || 0), 0));
            rows.push([d, h.programName, h.dayLabel, ex ? ex.name : id, ex ? E.PART_LABEL[ex.part] : '', p.weight ?? '', p.reps ?? '', (p.sets || []).length, vol, h.unit || u, h.durationMin || '']);
        });
    });
    return rows.map((r) => r.map(csvEsc).join(',')).join('\n');
}

/* ------------------------------------------------------------------ training reminders (App.js reminders: days 0 = Sunday) */
export function reminders() {
    const r = S.store.reminders || {};
    return { enabled: !!r.enabled, time: /^\d{1,2}:\d{2}$/.test(String(r.time || '')) ? r.time : '18:00', days: Array.isArray(r.days) ? r.days.filter((d) => Number.isInteger(d) && d >= 0 && d <= 6) : [1, 2, 3, 4, 5] };
}
export function setReminders({ enabled, time, days } = {}) {
    const cur = reminders();
    const next = { enabled: enabled ?? cur.enabled, time: /^\d{1,2}:\d{2}$/.test(String(time || '')) ? time : cur.time,
        days: Array.isArray(days) ? [...new Set(days.filter((d) => Number.isInteger(d) && d >= 0 && d <= 6))].sort() : cur.days };
    S.store.reminders = next; markStore();
    return { ok: true, reminders: next };
}

/* ------------------------------------------------------------------ program settings + rebuild (App.js applyCfgDraft / handleRegenerate) */
export function programSettings({ programId }) {
    const p = draftOf(programId); if (!p) return null;
    const c = p.config || {};
    const owner = p.cycleId ? cycles().find((x) => x.id === p.cycleId) : null;
    return { programId: p.id, engine: p.engineSource || null, cycleOwned: !!owner, custom: p.custom === true,
        goal: c.goal || 'both', weeks: E.weeksOf(p), endless: !!c.endless, deload: !!c.deload, session: c.session || 's60', progression: c.progression === 'manual' ? 'manual' : 'auto',
        barbellCap: c.barbellCap ?? null, noBodyweight: !!c.noBodyweight, noSupersets: !!c.noSupersets, volumeApproach: c.volumeApproach || 'standard',
        folder: p.folder || '', description: p.description || '', seed: p.seed ?? null,
        rolls: (p.rolls || []).map((r) => ({ seed: r.seed, at: r.at, lifts: r.lifts || [] })),
        sessions: (E.SESSIONS || []).map((s) => (typeof s === 'string' ? { id: s, label: s } : { id: s.id, label: s.label || s.id })) };
}
/* App.js handleRegenerate: rebuilt from the same seed (or a new one to shuffle) by the current engine,
   keeping id, name, cycle links and the lifts the user added; the roll walked away from is remembered. */
function regenerateProgram(program, { seed, configPatch = null, metaPatch = null }) {
    const cfg = configPatch ? { ...program.config, ...configPatch } : program.config;
    const p = E.generateNextProgramForShell({ config: cfg, banned: S.store.banned || [], legacyExercises: EXERCISES, ...(seed != null ? { seed } : {}), makeId: E.uid }).program;
    p.generationRoute = E.GENERATION_ROUTE;
    Object.assign(p, { id: program.id, name: program.name, createdAt: program.createdAt, cycleId: program.cycleId, cycleIndex: program.cycleIndex, blockLabel: program.blockLabel });
    p.folder = metaPatch && Object.prototype.hasOwnProperty.call(metaPatch, 'folder') ? (metaPatch.folder || undefined) : program.folder;
    p.description = metaPatch && Object.prototype.hasOwnProperty.call(metaPatch, 'description') ? (metaPatch.description || undefined) : program.description;
    const prevRoll = { seed: program.seed, at: now(), lifts: (program.days || []).map((d) => EX_BY_ID[d.exercises[d.primaryIndex != null ? d.primaryIndex : 0]]?.name).filter(Boolean).slice(0, 3) };
    p.rolls = [prevRoll, ...(program.rolls || []).filter((r) => r.seed !== program.seed && r.seed !== p.seed)].slice(0, 5);
    const carried = [];
    (program.days || []).forEach((od) => (od.exercises || []).forEach((id, slot) => { if (program.overrides?.[`${od.id}:${slot}`]?.added) carried.push({ type: od.type, id }); }));
    if (carried.length) {
        const ov = { ...(p.overrides || {}) };
        carried.forEach(({ type, id }) => {
            const nd = p.days.find((d) => d.type === type) || p.days[0];
            if (!nd || nd.exercises.includes(id)) return;
            nd.exercises = [...nd.exercises, id];
            ov[`${nd.id}:${nd.exercises.length - 1}`] = { ...(ov[`${nd.id}:${nd.exercises.length - 1}`] || {}), added: true };
        });
        p.overrides = ov;
    }
    return p;
}
/* App.js applyCfgDraft for an engine-built program: settings that change the generation contract
   rebuild it once; folder and description are plain edits. Cycle blocks are owned by their cycle. */
export function applyProgramSettings({ programId, settings = {}, shuffle = false, seed = undefined }) {
    const program = programById(programId); if (!program) return { ok: false, error: 'That program no longer exists.' };
    const owner = program.cycleId ? cycles().find((c) => c.id === program.cycleId) : null;
    const c = program.config || {}, d = settings;
    const patch = {};
    if (d.goal != null && d.goal !== c.goal) patch.goal = d.goal;
    if (d.weeks != null && d.weeks !== E.weeksOf(program)) patch.weeks = d.weeks;
    if (d.deload != null && !!d.deload !== !!c.deload) patch.deload = !!d.deload;
    // App.js builder "Endless — continuous": a 5-week accumulation wave with its own deload, Auto progression.
    if (d.endless != null && !!d.endless !== !!c.endless) Object.assign(patch, d.endless ? { endless: true, weeks: 5, deload: true, progression: 'auto' } : { endless: false });
    if (d.session != null && d.session !== (c.session || 's60')) patch.session = d.session;
    if (d.progression != null && d.progression !== (c.progression === 'manual' ? 'manual' : 'auto')) patch.progression = d.progression;
    if (d.barbellCap !== undefined && (d.barbellCap ?? null) !== (c.barbellCap ?? null)) patch.barbellCap = d.barbellCap ?? null;
    if (d.noBodyweight != null && !!d.noBodyweight !== !!c.noBodyweight) patch.noBodyweight = !!d.noBodyweight;
    if (d.noSupersets != null && !!d.noSupersets !== !!c.noSupersets) patch.noSupersets = !!d.noSupersets;
    if (d.volumeApproach != null && d.volumeApproach !== (c.volumeApproach || 'standard')) patch.volumeApproach = d.volumeApproach;
    const meta = {};
    if (d.folder != null && d.folder !== (program.folder || '')) meta.folder = String(d.folder).trim() || undefined;
    if (d.description != null && d.description !== (program.description || '')) meta.description = String(d.description) || undefined;
    const rebuild = shuffle || seed !== undefined || Object.keys(patch).length > 0;
    if (rebuild && owner) return { ok: false, error: 'This block belongs to a cycle, which decides how it is built. Duplicate it for a standalone copy you can rebuild.' };
    if (S.live?.programId === programId && rebuild) return { ok: false, error: 'Finish or discard the workout in progress first.' };
    if (!rebuild) {
        if (!Object.keys(meta).length) return { ok: true, rebuilt: false, changed: false };
        setList('saved', saved().map((p) => (p.id === programId ? { ...p, ...meta } : p)));
        return { ok: true, rebuilt: false, changed: true };
    }
    let np;
    try { np = regenerateProgram(program, { seed: shuffle ? undefined : (seed !== undefined ? seed : program.seed), configPatch: Object.keys(patch).length ? patch : null, metaPatch: Object.keys(meta).length ? meta : null }); }
    catch (err) { return cycleFail(err, 'This program could not be rebuilt safely. Your current program was left unchanged.'); }
    setList('saved', saved().map((p) => (p.id === programId ? np : p)));
    dropDraft(programId);
    return { ok: true, rebuilt: true, changed: true };
}

/* ------------------------------------------------------------------ compare (App.js CompareView + summarizeProgram) */
const GOAL_LABEL = { hypertrophy: 'Hypertrophy', strength: 'Strength', both: 'Strength & Size' };
function summarizeProgram(p) {
    const wk = E.weeksOf(p), vol = E.weeklyVolume(p, wk);
    let totalSets = 0, comp = 0, iso = 0, exCount = 0, mins = 0; const exIds = [], seen = new Set();
    (p.days || []).forEach((day) => {
        try { mins += E.estimateMinutes(p, day, wk); } catch { /* ignore */ }
        (day.exercises || []).forEach((id, slot) => {
            const ex = EX_BY_ID[id]; if (!ex) return;
            exCount++; if (!seen.has(id)) { seen.add(id); exIds.push(id); }
            if (ex.type === 'compound') comp++; else iso++;
            totalSets += Number(E.computeCell(p, day, id, slot, wk).sets) || 0;
        });
    });
    return { goal: GOAL_LABEL[p.config?.goal] || p.config?.goal || '—', split: E.SPLITS[p.config?.split]?.name || 'Custom', days: (p.days || []).length, weeks: wk,
        totalSets: Math.round(totalSets), weeklyMin: Math.round(mins), exercises: exCount, comp, iso, vol, exIds };
}
export function comparePrograms({ a, b }) {
    const A = programById(a), B = programById(b); if (!A || !B) return null;
    const sa = summarizeProgram(A), sb = summarizeProgram(B);
    const parts = (E.PART_ORDER || []).filter((p) => (sa.vol[p] || 0) > 0.05 || (sb.vol[p] || 0) > 0.05);
    const setB = new Set(sb.exIds), setA = new Set(sa.exIds);
    const strip = ({ vol, exIds, ...rest }) => rest;
    return { a: { id: A.id, name: A.name, ...strip(sa) }, b: { id: B.id, name: B.name, ...strip(sb) },
        muscles: parts.map((p) => ({ part: p, label: E.PART_LABEL?.[p] || p, a: Math.round((sa.vol[p] || 0) * 10) / 10, b: Math.round((sb.vol[p] || 0) * 10) / 10 })),
        shared: sa.exIds.filter((id) => setB.has(id)).map(exName), onlyA: sa.exIds.filter((id) => !setB.has(id)).map(exName), onlyB: sb.exIds.filter((id) => !setA.has(id)).map(exName),
        programs: saved().map((p) => ({ id: p.id, name: p.name })) };
}
export function compareExercises({ a, b }) {
    const A = EX_BY_ID[a], B = EX_BY_ID[b]; if (!A || !B) return null;
    const pa = E.exerciseProfile(A), pb = E.exerciseProfile(B);
    const parts = (E.PART_ORDER || []).filter((p) => (pa.muscles[p] || 0) > 0.02 || (pb.muscles[p] || 0) > 0.02);
    const view = (x, pr) => ({ id: x.id, name: x.name, part: E.PART_LABEL?.[pr.part] || pr.part, type: pr.type, equip: pr.equip, rep: pr.rep, pattern: pr.pattern, tempo: pr.tempo, axial: !!pr.axial, cue: pr.cue || '' });
    return { a: view(A, pa), b: view(B, pb), muscles: parts.map((p) => ({ part: p, label: E.PART_LABEL?.[p] || p, a: pa.muscles[p] || 0, b: pb.muscles[p] || 0 })) };
}

/* ------------------------------------------------------------------ templates (App.js Home "Start from a template" + startTemplate) */
export function templates() {
    return { categories: [...new Set(E.TEMPLATES.map((t) => t.cat))],
        items: E.TEMPLATES.map((t) => ({ id: t.id, name: t.name, cat: t.cat, tag: t.tag || '', desc: t.desc || '', featured: !!t.featured, rank: t.featuredRank ?? 50, percent: !!t.cfg?.percentScheme, days: t.cfg?.days || null })) };
}
/* The design's Percentage program → Method. A method is tied to its own split (Texas has volume,
   recovery and intensity days; tiered methods have a T2 slot), so changing it rebuilds the program
   from that method's template with this program's equipment, keeping its id, name, notes, history
   link and pin. Training maxes carry over only between methods that share a basis (a 5/3/1 TM is not
   a Texas 5RM); otherwise Today asks for them again. */
export function percentMethods({ programId }) {
    const p = programById(programId); if (!p?.config?.percentScheme) return null;
    const cur = p.config.percentScheme, curBbb = p.config.assistance === 'bbb';
    return { programId: p.id, current: cur, methods: E.TEMPLATES.filter((t) => t.cfg?.percentScheme).map((t) => {
        const S2 = E.PCT_SCHEMES[t.cfg.percentScheme] || {};
        return { templateId: t.id, scheme: t.cfg.percentScheme, name: t.name, method: S2.name || t.cfg.percentScheme, basis: S2.basis || '', desc: t.desc || '', tag: t.tag || '',
            days: t.cfg.days || null, weeks: t.cfg.weeks || null, current: t.cfg.percentScheme === cur && (t.cfg.assistance === 'bbb') === curBbb };
    }) };
}
export function changePercentMethod({ programId, templateId }) {
    const p = programById(programId), t = E.TEMPLATES.find((x) => x.id === templateId);
    if (!p || !t?.cfg?.percentScheme) return { ok: false, error: 'That method is not available.' };
    if (p.cycleId && cycles().some((c) => c.id === p.cycleId)) return { ok: false, error: 'This block belongs to a cycle, which decides how it is built.' };
    if (S.live?.programId === programId) return { ok: false, error: 'Finish or discard the workout in progress first.' };
    const cfg = programConfig({ ...E.templateConfig(t, p.config?.equipment?.length ? p.config.equipment : E.ALL_EQUIP_IDS), name: p.name, unit: p.config?.unit || unit() });
    let np;
    try { np = E.generateNextProgramForShell({ config: cfg, banned: S.store.banned || [], legacyExercises: EXERCISES, makeId: E.uid }).program; }
    catch (err) { return { ok: false, error: 'This method could not be built for your equipment. Your program was left unchanged.' }; }
    if (!np) return { ok: false, error: 'The engine could not build that method.' };
    np.generationRoute = E.GENERATION_ROUTE;
    Object.assign(np, { id: p.id, name: p.name, createdAt: p.createdAt, folder: p.folder, description: p.description, edited: true });
    const oldBasis = E.PCT_SCHEMES[p.config.percentScheme]?.basis, newBasis = E.PCT_SCHEMES[t.cfg.percentScheme]?.basis;
    let carried = 0;
    if (oldBasis && oldBasis === newBasis && p.trainingMax) {
        const u = unit();
        mainLifts(np).forEach((id) => { const tm = E.trainingMaxForUnit(p, id, u); if (tm > 0) { Object.assign(np, E.withTrainingMax(np, id, tm, u)); carried++; } });
    }
    setList('saved', saved().map((x) => (x.id === programId ? np : x)));
    dropDraft(programId);
    const need = mainLifts(np).filter((id) => !(E.trainingMaxForUnit(np, id, unit()) > 0)).length;
    return { ok: true, method: E.PCT_SCHEMES[t.cfg.percentScheme]?.name, carried, needsTrainingMax: need,
        message: `Now running ${E.PCT_SCHEMES[t.cfg.percentScheme]?.name}` + (need ? ` · set ${need} training max${need === 1 ? '' : 'es'}` : carried ? ' · training maxes kept' : '') };
}
/* The active gym decides what a template is built from; a template that pins its own kit still wins. */
export function createFromTemplate({ id }) {
    const t = E.TEMPLATES.find((x) => x.id === id); if (!t) return { ok: false, error: 'That template no longer exists.' };
    const g = E.normalizeGyms(S.store.gyms, S.store.activeGymId), gym = E.gymById(g.gyms, g.activeGymId);
    const equipment = gym?.equipment?.length ? gym.equipment : (S.store.activeGymId ? [] : E.ALL_EQUIP_IDS);
    const r = createProgram({ config: E.templateConfig(t, equipment) });
    return r.ok ? { ...r, percent: !!t.cfg?.percentScheme } : r;
}

/* ------------------------------------------------------------------ training maxes for percentage programs (App.js tmSheet + "Set from 1RM") */
function mainLifts(p) { const ids = []; (p.days || []).forEach((d) => { const id = d.exercises[d.primaryIndex]; if (id && !ids.includes(id) && EX_BY_ID[id]) ids.push(id); }); return ids; }
/* App.js tmSheet + the design's percentage program panel: the method, a chip per week of the block
   (deload included), and for each main lift its training max and that week's sets from the engine's
   own percentagePlanFor — the same call the workout prescribes from. */
export function trainingMaxes({ programId, weekIndex = null }) {
    const p = programById(programId); if (!p) return null;
    const u = unit(), percent = !!p.config?.percentScheme, scheme = p.config?.percentScheme || null;
    const S = scheme ? E.PCT_SCHEMES?.[scheme] : null;
    const nx = upNext(), current = nx?.program.id === p.id ? nx.weekIndex : null;
    const bw = blockWeeks(p);
    const wk = Math.max(1, Math.min(bw.list[bw.list.length - 1] || 1, Number(weekIndex) || current || 1));
    const firstMain = mainLifts(p)[0];
    const planOf = (id, w) => {
        const ex = EX_BY_ID[id], day = p.days.find((d) => d.exercises[d.primaryIndex] === id) || p.days.find((d) => d.exercises.includes(id));
        if (!ex || !day) return null;
        try { return E.percentagePlanFor(p, day, ex, day.exercises.indexOf(id), w, u, history()); } catch { return null; }
    };
    const setTxt = (x) => `${fmtW(x.weight) || '—'}×${x.reps}`;
    return { programId: p.id, name: p.name, percent, unit: u,
        method: S ? { id: scheme, name: S.name, basis: S.basis } : null,
        weekIndex: wk, current, deloadWeek: bw.deloadWeek,
        weeks: percent ? bw.list.map((w) => {
            const pl = firstMain && (E.trainingMaxForUnit(p, firstMain, u) || 0) > 0 ? planOf(firstMain, w) : null;
            return { w, deload: w === bw.deloadWeek || /deload/i.test(pl?.label || ''), label: pl?.label || null, now: w === current };
        }) : [],
        lifts: percent ? mainLifts(p).map((id) => {
            const ex = EX_BY_ID[id], tm = E.trainingMaxForUnit(p, id, u) || 0;
            const w1 = tm > 0 ? planOf(id, 1) : null, wn = tm > 0 ? planOf(id, wk) : null;
            return { id, name: ex.name, tm, step: E.loadStep(ex, u),
                week1: w1 ? { label: w1.label, sets: w1.sets.map(setTxt) } : null,
                week: wn ? { label: wn.label, sets: wn.sets.map((x) => ({ text: setTxt(x), pct: x.pct != null ? Math.round(x.pct * 1000) / 10 : null, amrap: !!x.amrap })) } : null };
        }) : [] };
}
/* App.js programToText ("Share program"): the plan as plain text. Two departures, both fixes: the goal
   line reads GOALS[...].label (App.js reads .name, which GOALS doesn't have, so the goal never
   printed), and percentage main lifts print from percentagePlanFor in the lifter's unit, the same
   numbers the workout uses, instead of re-deriving them from the stored max. */
export function programText({ programId }) {
    const p = programById(programId); if (!p) return null;
    const cfg = p.config || {}, u = unit(), mode = S.store.loadMode === 'rpe' ? 'rpe' : 'rir';
    const scheme = cfg.percentScheme, PS = scheme ? E.PCT_SCHEMES?.[scheme] : null, wk = E.weeksOf(p);
    const L = [p.name || 'My Program'];
    L.push(`${E.SPLITS?.[cfg.split]?.name || cfg.split || 'Custom'} · ${p.days.length} days/week${GOAL_LABEL[cfg.goal] ? " · " + GOAL_LABEL[cfg.goal] : ''} · ${wk}-week block`);
    if ((p.description || '').trim()) L.push(p.description.trim());
    if (PS) L.push(`Main lifts use ${PS.name} percentages of ${PS.basis}.`);
    L.push('');
    p.days.forEach((day) => {
        L.push(day.label || 'Day');
        if (day.note) L.push(`  — ${day.note}`);
        day.exercises.forEach((id, slot) => {
            const ex = EX_BY_ID[id]; if (!ex) return;
            const grouped = p.ss?.[`${day.id}:${slot}`] || (slot > 0 && p.ss?.[`${day.id}:${slot - 1}`]);
            const bullet = grouped ? '   ↳' : `  ${slot + 1}.`;
            if (PS && slot === day.primaryIndex) {
                const tm = E.trainingMaxForUnit(p, id, u) || 0;
                L.push(`${bullet} ${ex.name} — ${PS.name} main lift${tm > 0 ? ` · TM ${fmtW(tm)} ${u}` : ''}`);
                for (let w = 1; w <= Math.min(wk, 4); w++) {
                    let pl = null;
                    try { pl = tm > 0 ? E.percentagePlanFor(p, day, ex, slot, w, u, history()) : E.percentageProtocolFor({ scheme, tm: 100, weekIndex: w, weeksTotal: wk, dayType: day.type, tier: 't1' }); } catch { pl = null; }
                    if (!pl?.sets?.length) continue;
                    const parts = pl.sets.map((x) => (tm > 0 ? `${fmtW(x.weight)}×${x.reps}` : `${Math.round(x.pct * 100)}%×${x.reps}`));
                    L.push(`       W${w}${pl.label ? ` (${pl.label})` : ''}: ${parts.join(' · ')}`);
                }
                return;
            }
            let cell = null; try { cell = E.computeCell(p, day, id, slot, 1); } catch { cell = null; }
            if (!cell) { L.push(`${bullet} ${ex.name}`); return; }
            const eff = cell.rir != null ? ` @ ${E.effortLabel(cell.rir, mode)}` : (cell.range ? ' to failure' : '');
            L.push(`${bullet} ${ex.name} — ${cell.sets} × ${cell.range}${eff}`);
        });
        L.push('');
    });
    L.push('Made with Pursuit Iron — Strength Coach');
    return { text: L.join('\n'), name: p.name || 'My Program' };
}
/* A training max typed directly, or 90% of a one-rep max rounded to the lift's step. */
export function setTrainingMax({ programId, exId, value, fromOneRm = false }) {
    const p = programById(programId), ex = EX_BY_ID[exId]; if (!p || !ex) return { ok: false };
    const n = Number(value), u = unit();
    const tm = !(n > 0) ? 0 : fromOneRm ? E.roundTo(n * 0.9, E.loadStep(ex, u)) : n;
    setList('saved', saved().map((x) => (x.id === programId ? E.withTrainingMax(x, exId, tm, u) : x)));
    return { ok: true, tm };
}

/* ------------------------------------------------------------------ injury log (App.js HomeInjuryJournal) */
export function injuryLog() {
    const nx = upNext();
    const preview = (entry) => {
        if (!nx?.day) return [];
        return nx.day.exercises.map((_id, slot) => EX_BY_ID[E.exerciseAt(nx.program, nx.day, slot, nx.weekIndex)])
            .filter((ex) => ex && injuryMatchesExercise(entry, ex, E.secondaryOf(ex))).map((ex) => ex.name);
    };
    const rows = injuries().map((e) => ({ id: e.id, title: injuryTitle(e, E.PART_LABEL), region: e.region || '', part: e.part, side: e.side, impact: e.impact,
        onset: e.onset || null, note: e.note || '', at: e.at || e.createdAt || null, resolvedAt: e.resolvedAt || null, nextWorkout: e.resolvedAt ? [] : preview(e) }));
    return { active: rows.filter((r) => !r.resolvedAt), resolved: rows.filter((r) => r.resolvedAt), areas: INJURY_AREAS, impacts: INJURY_IMPACTS,
        parts: (E.PART_ORDER || []).filter((p) => E.PART_LABEL?.[p]).map((p) => ({ id: p, label: E.PART_LABEL[p] })), nextDay: nx?.day?.label || null };
}
/* Log or edit one entry: an area (or a single muscle), side, how to handle related lifts, onset, note. */
export function saveInjury({ id = null, region = '', part = '', side = 'unspecified', impact = 'monitor', onset = null, note = '' } = {}) {
    const area = INJURY_AREAS.find((a) => a.id === region);
    const p = part || area?.parts?.[0] || '';
    if (!p) return { ok: false, error: 'Choose an affected area or muscle.' };
    const today = isoLocal(now());
    const on = onset || today;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(on) || on > today) return { ok: false, error: 'Choose today or an earlier onset date.' };
    const clean = injuries(), previous = clean.find((x) => x.id === id), t = now();
    const entry = { ...previous, region: area ? region : '', part: p, side: ['left', 'right', 'both'].includes(side) ? side : 'unspecified',
        impact: ['monitor', 'reduce', 'skip'].includes(impact) ? impact : 'monitor', onset: on, note: String(note || '').trim(),
        id: previous?.id || E.uid(), at: previous?.at || t, updatedAt: t };
    S.store.injuryLog = previous ? clean.map((x) => (x.id === entry.id ? entry : x)) : [entry, ...clean];
    markStore();
    return { ok: true, id: entry.id, updated: !!previous };
}
export function toggleInjuryResolved({ id }) {
    const clean = injuries(), e = clean.find((x) => x.id === id); if (!e) return { ok: false };
    S.store.injuryLog = clean.map((x) => (x.id === id ? { ...x, resolvedAt: x.resolvedAt ? undefined : now(), updatedAt: now() } : x));
    markStore();
    return { ok: true, resolved: !e.resolvedAt };
}

/* ------------------------------------------------------------------ the finish sheet (App.js "finishing" review before onFinish) */
export function finishPreview() {
    const l = S.live; if (!l) return null;
    const ctx = sessionContext(l); if (!ctx) return null;
    const { program, day, wk } = ctx, u = unit(), data = l.data || [];
    const isW = (x) => x.done && E.isWorkSet(x);
    let priorBests = {}; try { priorBests = E.liftBests(history(), u, (h) => h.id !== l.sessionId) || {}; } catch { priorBests = {}; }
    const prs = data.map((e) => {
        const w = e.sets.filter((x) => isW(x) && parseFloat(x.weight) > 0 && parseInt(x.reps) > 0); if (!w.length) return null;
        const best = Math.max(...w.map((x) => E.e1rm(parseFloat(x.weight), parseInt(x.reps))));
        const prior = priorBests[e.id]?.e1rm ?? null;
        return prior != null && best > prior ? { name: EX_BY_ID[e.id]?.name || e.id, gain: Math.round((best - prior) * 10) / 10 } : null;
    }).filter(Boolean);
    const trained = [...new Set(data.filter((e) => e.sets.some((x) => x.done && !x.warm)).map((e) => EX_BY_ID[e.id]?.part).filter(Boolean))];
    const goals = S.store.goals || {};
    const goalsHit = data.map((e) => {
        const g = Number(goals[e.id]); if (!(g > 0)) return null;
        const w = e.sets.filter((x) => isW(x) && parseFloat(x.weight) > 0); if (!w.length) return null;
        const bestNow = Math.max(...w.map((x) => parseFloat(x.weight)));
        return bestNow >= g && (priorBests[e.id]?.w || 0) < g ? { name: EX_BY_ID[e.id]?.name, goal: g } : null;
    }).filter(Boolean);
    const mainE1rm = (() => {
        const mainId = day.exercises[day.primaryIndex], e = data.find((x) => x.id === mainId); if (!e) return null;
        const w = e.sets.filter((x) => isW(x) && !x.bbb && parseFloat(x.weight) > 0 && parseInt(x.reps) > 0); if (!w.length) return null;
        const top = w.reduce((a, b) => (E.e1rm(parseFloat(b.weight), parseInt(b.reps)) > E.e1rm(parseFloat(a.weight), parseInt(a.reps)) ? b : a));
        return { name: EX_BY_ID[mainId]?.name, weight: parseFloat(top.weight), reps: parseInt(top.reps), est: Math.round(E.e1rm(parseFloat(top.weight), parseInt(top.reps))), prev: Math.round(priorBests[mainId]?.e1rm || 0) };
    })();
    const nextPlan = data.map((e) => {
        const ex = EX_BY_ID[e.id]; if (!ex) return null;
        let sm = null; try { sm = E.summarizeSets(e.sets, ex, u); } catch { sm = null; }
        if (!sm || !(sm.weight > 0)) return null;
        const logged = e.sets.filter((x) => x.done && !x.warm && !x.sub && parseFloat(x.weight) > 0 && parseInt(x.reps) > 0)
            .map((x) => ({ w: parseFloat(x.weight), r: parseInt(x.reps), ...(x.actualRIR != null ? { rir: x.actualRIR } : {}), ...(E.prescribedRIRof(x) != null ? { tr: E.prescribedRIRof(x) } : {}) }));
        if (!logged.length) return null;
        let sug = null; try { sug = E.nextWorkoutSuggestionFromPerformedShell(program, EXERCISES, day, e.slot, wk, { [e.id]: { weight: sm.weight, reps: sm.reps, sets: logged } }, u); } catch { sug = null; }
        if (!sug) return null;
        return { name: ex.name, dir: sug.dir, delta: sug.weight != null ? Math.round((sug.weight - sm.weight) * 100) / 100 : 0, weight: sug.weight, from: sm.weight, reason: sug.reason || "" };
    }).filter(Boolean);
    const work = (e) => e.sets.filter(isWork);
    return { unit: u, quick: !!program.quick, doneSets: data.reduce((n, e) => n + work(e).filter((x) => x.done).length, 0), totalSets: data.reduce((n, e) => n + work(e).length, 0),
        prs, goalsHit, mainE1rm: program.quick ? null : mainE1rm, nextPlan: program.quick ? [] : nextPlan,
        parts: trained.map((p) => ({ part: p, label: E.PART_LABEL?.[p] || p, pump: l.fbPump?.[p] || null, sore: l.fbSore?.[p] || null })),
        pumpChoices: [['flat', 'Flat'], ['good', 'Good'], ['huge', 'Huge']], soreChoices: [['sore', 'Sore'], ['ontime', 'On time'], ['fresh', 'Fresh']],
        sessionNote: l.sessionNote || '', routineIds: program.quick ? data.map((x) => x.id) : [] };
}

/* ------------------------------------------------------------------ quick workout, repeat, routines (App.js startQuickWorkout / repeatWorkout / saveRoutine) */
const QUICK_EQUIP = ['barbell', 'dumbbell', 'bench', 'cable', 'machine', 'smith', 'ezbar', 'pullup', 'dip', 'kettlebell', 'bands'];
function quickConfig(name) {
    return { name, goal: 'both', progression: 'manual', split: null, days: 1, session: 's60', experience: 'intermediate', weeks: 1, equipment: QUICK_EQUIP, focus: {}, reduce: [], barbellCap: null, noBodyweight: false, deload: false, percentScheme: null };
}
function startWith(program, day) {
    const l = { schemaVersion: 2, sessionId: E.uid(), programId: program.id, dayId: day.id, weekIndex: 1, startedAt: now(), elapsedMs: 0, exIdx: 0,
        readiness: null, injuryReports: [], sessionProgressions: {}, sessionNote: '', sessionNoteRepeat: 'none', fbPump: {}, fbSore: {}, rest: null,
        ...(programById(program.id) ? {} : { quickProgram: program }) };
    S.live = l;
    const ctx = sessionContext(l); if (!ctx) { S.live = null; return { error: 'That workout could not be started.' }; }
    l.data = freshData(ctx).data; l.dayExSig = ctx.day.exercises.join('|');
    markLive();
    return session();
}
export function startQuick() {
    if (S.live) return session();
    const day = { id: E.uid(), label: 'Quick workout', exercises: [], primaryIndex: 0 };
    return startWith({ id: E.uid(), name: 'Quick workout', quick: true, artifactType: 'freestyle', engineV: E.ENGINE_V, createdAt: now(), config: quickConfig('Quick workout'), days: [day], overrides: {} }, day);
}
export function repeatWorkout({ historyId }) {
    if (S.live) return { error: 'Finish or discard the workout in progress first.' };
    const h = history().find((x) => x.id === historyId); if (!h) return { error: 'That workout is no longer in your history.' };
    const prog = programById(h.programId), day = prog?.days.find((d) => d.id === h.dayId);
    if (prog && day) return startWith(prog, day);
    const ids = Object.keys(h.perf || {}).filter((id) => EX_BY_ID[id]);
    if (!ids.length) return { error: 'Nothing in that workout can be repeated.' };
    const pi = ids.findIndex((id) => EX_BY_ID[id]?.type === 'compound');
    const d = { id: E.uid(), label: h.dayLabel || 'Workout', exercises: ids, primaryIndex: pi < 0 ? 0 : pi };
    return startWith({ id: E.uid(), name: h.programName || 'Workout', quick: true, artifactType: 'freestyle', engineV: E.ENGINE_V, createdAt: now(), config: quickConfig(h.dayLabel || 'Workout'), days: [d], overrides: {} }, d);
}
export function saveRoutine({ ids, name }) {
    const valid = (ids || []).filter((id) => EX_BY_ID[id]); if (!valid.length) return { ok: false };
    const pi = valid.findIndex((id) => EX_BY_ID[id]?.type === 'compound');
    const nm = (name && String(name).trim()) || 'My routine';
    const d = { id: E.uid(), label: nm, exercises: valid, primaryIndex: pi < 0 ? 0 : pi };
    setList('saved', [...saved(), { id: E.uid(), name: nm, quick: true, artifactType: 'freestyle', engineV: E.ENGINE_V, createdAt: now(), config: quickConfig(nm), days: [d], overrides: {} }]);
    return { ok: true, name: nm };
}

/* ------------------------------------------------------------------ progression choices after a workout (App.js pendingProgramUpdate) */
export function pendingProgramUpdate() {
    const h = history().find((x) => x.programUpdateStatus === 'pending' && x.progressionChanges); if (!h) return null;
    return { historyId: h.id, programName: h.programName || '', changes: Object.entries(h.progressionChanges).map(([id, style]) => ({ name: exName(id), style: E.PROG_STYLES?.[style] || style })) };
}
export function completeProgramUpdate({ apply }) {
    const h = history().find((x) => x.programUpdateStatus === 'pending' && x.progressionChanges); if (!h) return { ok: false };
    const target = programById(h.programId);
    if (apply && !target) return { ok: false, error: 'This program is no longer saved. Keep this change for the completed session only.' };
    if (apply) {
        const updated = { ...withProgressionChoices(target, h.progressionChanges), edited: true };
        setList('saved', saved().map((p) => (p.id === updated.id ? updated : p)));
        if (S.store.drafts?.[updated.id]) S.store.drafts = { ...S.store.drafts, [updated.id]: withProgressionChoices(S.store.drafts[updated.id], h.progressionChanges) };
    }
    setList('history', history().map((x) => (x.id === h.id ? { ...x, programUpdateStatus: apply ? 'applied' : 'session-only' } : x)));
    return { ok: true, applied: !!apply };
}

/* The design's Plan → day row ⓘ ("Why this lift"), from what the engine recorded when it built the
   program: the slot's role and prescription, its progression choice and reason, and the plan-level
   decisions about that muscle (program.nextEngine). The design's per-term selection score is not
   stored by the engine, so it is not shown; nothing here is re-derived or invented. Lifts the user
   added or swapped in say so instead. */
const ROLE_TEXT = {
    primary_strength: ['Main strength lift', 'Placed first in the day and trained heavy with long rests. The engine reserves these before any optional muscle work.'],
    hypertrophy_compound: ['Compound for size', 'A multi-joint lift chosen for the muscles this day still needed, scored on stimulus against fatigue and what the week already covers.'],
    hypertrophy_isolation: ['Isolation for size', "Single-joint work to finish a muscle's weekly dose without adding much fatigue, chosen to cover a function the week was missing where it could."],
};
export function whyLift({ programId, dayId, slot, weekIndex }) {
    const p = programById(programId); if (!p) return null;
    const di = p.days.findIndex((d) => d.id === dayId), day = p.days[di]; if (!day) return null;
    const k = Number(slot), id = E.exerciseAt(p, day, k, weekIndex || 1), ex = EX_BY_ID[id]; if (!ex) return null;
    const ne = p.nextEngine || {}, sess = ne.program?.sessions?.[di], eng = sess?.exercises?.[k];
    const added = !!p.overrides?.[`${day.id}:${k}`]?.added;
    const same = !!eng && !added && (eng.name || '').toLowerCase() === (ex.name || '').toLowerCase();
    let cell = null; try { cell = E.computeCell(p, day, id, k, weekIndex || 1); } catch { cell = null; }
    let style = null; try { style = E.styleFor(p, id); } catch { style = null; }
    const part = E.PART_LABEL?.[ex.part] || ex.part;
    const names = [part].concat((E.secondaryOf(ex) || []).map(([pp]) => E.PART_LABEL?.[pp] || pp)).map((x) => String(x).toLowerCase());
    const dose = (ne.explainability?.decisions || []).filter((x) => (x.category === 'dose' || x.category === 'coverage') && names.includes(String(x.subject || '').toLowerCase()))
        .map((x) => ({ subject: x.subject, outcome: x.outcome, why: x.why }));
    const role = same ? (ROLE_TEXT[eng.role] || [String(eng.role || '').replace(/_/g, ' '), '']) : null;
    return { programId, dayLabel: day.label, slot: k + 1, name: ex.name, part, primary: k === day.primaryIndex,
        source: added ? 'added' : same ? 'engine' : 'edited',
        role: role ? { label: role[0], text: role[1] } : null,
        week: weekIndex || 1, rx: cell ? { sets: cell.sets ?? null, range: cell.range || null, rir: cell.rir ?? null } : null,
        progression: { style: style || 'auto', label: STYLE_LABEL[style] || style || 'Auto', reason: (same && eng.progressionSelection?.reason) || null, locked: !!p.progStyleLifter?.[id] },
        muscles: [part].concat((E.secondaryOf(ex) || []).slice().sort((a, b) => b[1] - a[1]).map(([pp]) => E.PART_LABEL?.[pp] || pp)),
        dose, summary: Array.isArray(ne.explainability?.summary) ? ne.explainability.summary : [] };
}

/* App.js ProgressionCard selection overview + the design's Program settings → Lift progression: every
   lift of the program with the style it runs, why the engine picked it (program.nextEngine
   .progressionPlan — read, never re-derived), and a lock when the lifter chose it. Choosing a style
   writes progStyle + progStyleLifter, the same record "keep in program" writes after a workout;
   choosing Auto hands the lift back to the engine. */
const STYLE_LABEL = { double: 'Double progression', dynamic: 'Dynamic double progression', ladder: 'Rep ladder', linear: 'Linear progression', wave: 'Wave loading', e1rm: 'e1RM autoregulation', auto: 'Auto' };
export function liftProgression({ programId }) {
    const p = programById(programId); if (!p) return null;
    const plan = Array.isArray(p.nextEngine?.progressionPlan) ? p.nextEngine.progressionPlan : [];
    const byEx = Object.fromEntries(plan.filter((x) => x?.exerciseId).map((x) => [x.exerciseId, x]));
    const seen = new Set(), lifts = [];
    (p.days || []).forEach((d) => (d.exercises || []).forEach((id, slot) => {
        if (seen.has(id) || !EX_BY_ID[id]) return; seen.add(id);
        let style = null; try { style = E.styleFor(p, id); } catch { style = null; }
        const sel = byEx[id], locked = !!p.progStyleLifter?.[id];
        lifts.push({ id, name: exName(id), dayLabel: d.label, primary: slot === d.primaryIndex, style: style || 'auto', label: STYLE_LABEL[style] || style || 'Auto', locked,
            reason: locked ? 'You chose this. Auto will not replace it at block review.' : (sel?.reason || 'Auto picked the progression that best fits this lift and block.'),
            changed: !!sel?.changed, previous: sel?.previousStyle ? (STYLE_LABEL[sel.previousStyle] || sel.previousStyle) : null });
    }));
    const groups = []; lifts.forEach((l) => { const g = groups.find((x) => x.style === l.style); if (g) g.count++; else groups.push({ style: l.style, label: l.label, count: 1 }); });
    const adaptive = plan.filter((x) => x?.source === 'adaptive' || x?.source === 'adaptive_hold').length, changed = plan.filter((x) => x?.changed).length;
    const allMine = lifts.length > 0 && lifts.every((l) => l.locked);
    return { programId: p.id, percent: !!p.config?.percentScheme,
        heading: allMine ? 'Your progression choices' : adaptive ? 'Progression re-checked from your training' : 'Auto progression for this block',
        note: allMine ? 'These methods are locked by your choices. Auto will not replace them at block review.'
            : adaptive ? `Auto used comparable workout history before keeping or changing each method: ${changed} changed, ${Math.max(0, adaptive - changed)} kept. One unusual workout is not enough to switch it.`
            : 'Pursuit chose per exercise from the lift, rep target, loading setup, your experience, phase and block length. It re-checks between blocks once there is enough comparable training.',
        groups, lifts, styles: [{ id: 'auto', label: 'Auto (recommended)' }].concat(Object.keys(E.PROG_STYLES || {}).filter((k) => k !== 'auto').map((k) => ({ id: k, label: STYLE_LABEL[k] || E.PROG_STYLES[k] }))) };
}
export function setLiftProgression({ programId, exId, style }) {
    const p = programById(programId); if (!p || !EX_BY_ID[exId]) return { ok: false, error: 'That lift is not in this program.' };
    if (p.config?.percentScheme) return { ok: false, error: 'Percentage programs set their main lifts by the method.' };
    const next = { ...withProgressionChoices(p, { [exId]: style }), edited: true };
    setList('saved', saved().map((x) => (x.id === programId ? next : x)));
    if (S.store.drafts?.[programId]) S.store.drafts = { ...S.store.drafts, [programId]: withProgressionChoices(S.store.drafts[programId], { [exId]: style }) };
    return { ok: true, ...liftProgression({ programId }) };
}

/* ------------------------------------------------------------------ per-exercise settings (App.js goals / exSetup / setProgramExLoadInc) */
export function setGoal({ id, weight }) {
    const g = { ...(S.store.goals || {}) }, w = Number(weight);
    if (w > 0) g[id] = w; else delete g[id];
    S.store.goals = g; markStore(); return { ok: true };
}
export function setExSetup({ id, key, value }) {
    const all = { ...(S.store.exSetup || {}) }, cur = { ...(all[id] || {}) }, v = String(value || '').trim();
    if (v) cur[key] = v; else delete cur[key];
    if (Object.keys(cur).length) all[id] = cur; else delete all[id];
    S.store.exSetup = all; markStore(); return { ok: true };
}
/* A machine's weight step belongs to the equipment, so it applies to every block of the program's cycle. */
export function setExLoadStep({ programId, id, value }) {
    const p = liveProgram(S.live) && S.live?.programId === programId ? liveProgram(S.live) : programById(programId); if (!p) return { ok: false };
    const u = unit(), v = Number(value);
    const apply = (x) => { const map = { ...(x.exLoadInc || {}) }; if (v > 0) map[id] = { v, unit: u }; else delete map[id]; return { ...x, exLoadInc: Object.keys(map).length ? map : undefined }; };
    if (programById(programId)) setList('saved', saved().map((x) => (x.id === programId || (p.cycleId && x.cycleId === p.cycleId) ? apply(x) : x)));
    else if (S.live?.quickProgram?.id === programId) { S.live.quickProgram = apply(S.live.quickProgram); markLive(); }
    return { ok: true };
}
/* App.js doSwap with scope "program": the swap also replaces the lift in the saved program day. */
export function swapInProgram({ ei, newId }) {
    const l = S.live; if (!l) return { ok: false };
    const oldId = l.data?.[ei]?.id; if (!oldId) return { ok: false };
    act({ type: 'swap', ei, newId });
    const p = programById(l.programId); if (!p) return { ok: true, program: false };
    setList('saved', saved().map((x) => {
        if (x.id !== p.id) return x;
        let changed = false;
        const days = x.days.map((d) => { if (d.id !== l.dayId) return d; const i = d.exercises.indexOf(oldId); if (i < 0 || d.exercises.includes(newId)) return d; changed = true; const ex = [...d.exercises]; ex[i] = newId; return { ...d, exercises: ex }; });
        return changed ? { ...x, days, edited: true } : x;
    }));
    return { ok: true, program: true };
}


/* ------------------------------------------------------------------ Settings → Data (App.js onClearBanned / onClearHistory / resetAll) */
export function clearBanned() {
    const prev = S.store.banned || []; if (!prev.length) return { ok: false };
    S.undo = { banned: prev }; S.store.banned = []; markStore();
    return { ok: true, count: prev.length };
}
export function clearHistory() {
    const prev = history(); if (!prev.length) return { ok: false };
    S.undo = { history: prev }; setList('history', []);
    return { ok: true, count: prev.length };
}
/* "Erase all data" means it; device preferences (theme, unit, plates, haptics) stay, as on the web app. */
export function resetAll() {
    S.live = null; markLive();
    setList('saved', []); setList('cycles', []); setList('history', []);
    Object.assign(S.store, { drafts: {}, banned: [], perf: {}, custom: [], bwLog: [], measurements: {}, goals: {}, exNotes: {}, injuryLog: [], exSetup: {},
        pinnedId: null, bodyweight: '', birth: '', birthEst: false, sex: 'male', minInc: { v: 0, unit: null }, reminders: { enabled: false, time: '18:00', days: [1, 2, 3, 4, 5] } });
    S.undo = null; syncCustomRegistry(); markStore();
    return { ok: true };
}
export function storageStatus() {
    let bytes = 0; try { bytes = JSON.stringify(S.store).length; } catch { bytes = 0; }
    return { bytes, workouts: history().length, programs: saved().length, lastBackup: S.store.lastBackup || null };
}
export function references() { return (E.REFERENCES || []).map(([name, detail]) => ({ name, detail })); }

/* ------------------------------------------------------------------ weekly schedule (App.js WeeklyScheduleSheet + cycleSchedule) */
export function schedule({ programId }) {
    const p = programById(programId); if (!p) return null;
    const sched = p.schedule || {};
    return { programId, days: p.days.map((d) => ({ id: d.id, label: d.label })),
        week: [1, 2, 3, 4, 5, 6, 0].map((wd) => ({ weekday: wd, dayId: sched[wd] || null, label: sched[wd] ? (p.days.find((d) => d.id === sched[wd])?.label || null) : null })),
        trainingDays: [1, 2, 3, 4, 5, 6, 0].filter((wd) => sched[wd]).length };
}
export function cycleSchedule({ programId, weekday }) {
    const p = programById(programId); if (!p) return null;
    const sched = { ...(p.schedule || {}) }, ids = p.days.map((d) => d.id);
    const cur = sched[weekday] ?? null, nextIdx = (cur === null ? -1 : ids.indexOf(cur)) + 1;
    if (nextIdx >= ids.length) delete sched[weekday]; else sched[weekday] = ids[nextIdx];
    setList('saved', saved().map((x) => (x.id === programId ? { ...x, schedule: sched, scheduleEdited: true } : x)));
    return schedule({ programId });
}
export function clearSchedule({ programId }) {
    if (!programById(programId)) return null;
    setList('saved', saved().map((x) => (x.id === programId ? { ...x, schedule: {}, scheduleEdited: true } : x)));
    return schedule({ programId });
}
