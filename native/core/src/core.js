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

/* ------------------------------------------------------------------ init / persistence */
export function init({ store = null, live = null } = {}) {
    let parsed = null;
    try { parsed = store ? E.parseStoredData(store) : null; } catch { parsed = null; }
    S.store = parsed || E.parseStoredData('{}') || { v: E.STORE_VERSION, saved: [], history: [], perf: {} };
    repairNativeLogs();
    S.live = null;
    if (live) {
        try {
            const l = JSON.parse(live);
            // App.js: an already-logged session must never resume (liveMatchRef / sessionSnapshotStatus).
            if (l && l.sessionId && !history().some((h) => h.id === l.sessionId) && programById(l.programId)) S.live = l;
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
export function importStore({ text, mode = 'merge' }) {
    const incoming = E.parseStoredData(String(text || ''));
    if (!incoming) return { ok: false, error: 'That file is not a Pursuit Iron backup.' };
    const before = { sessions: history().length, programs: saved().length };
    if (!Array.isArray(incoming.saved) && !Array.isArray(incoming.history)) return { ok: false, error: 'No recognizable backup found.' };
    let stats = null;
    if (mode === 'replace' || (before.sessions === 0 && before.programs === 0)) S.store = incoming;
    else {
        // App.js import: MERGE, never replace; the live copy is the local side and is newer.
        const merged = E.mergeStores({ ...S.store, savedAt: now() }, { ...incoming, savedAt: incoming.savedAt || incoming.exportedAt || 0 });
        S.store = merged.data; stats = merged.stats;
    }
    applyGlobals(); markStore();
    return { ok: true, sessions: history().length, programs: saved().length, stats, added: { sessions: history().length - before.sessions, programs: saved().length - before.programs } };
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

function dayPreview(program, day, weekIndex) {
    return day.exercises.map((_id, slot) => {
        const id = E.exerciseAt(program, day, slot, weekIndex);
        let cell = null; try { cell = E.computeCell(program, day, id, slot, weekIndex); } catch { cell = null; }
        return { id, name: exName(id), part: EX_BY_ID[id]?.part || '', primary: slot === day.primaryIndex, sets: cell?.sets ?? null, reps: cell?.reps ?? null, rir: cell?.rir ?? null };
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
            weekIndex: nx.weekIndex, weeksTotal: E.weeksOf(nx.program), minutes: est, exercises: dayPreview(nx.program, nx.day, nx.weekIndex)
        } : null,
        week: { sessions: thisWeek.length, planned: nx ? nx.program.days.length : 0, volume: Math.round(thisWeek.reduce((s, h) => s + (Number(h.volume) || 0), 0)) },
        readiness: { today: todayReady, parts: rd },
        injuries: injuries().filter((i) => !i.resolvedAt).map((i) => ({ id: i.id, title: injuryTitle(i, E.PART_LABEL), impact: i.impact })),
        live: S.live ? liveSummary() : null,
        recent: H.slice(0, 7).map(historyRow)
    };
}

/* ------------------------------------------------------------------ workout session */
function sessionContext(l) {
    const source = programById(l.programId);
    if (!source) return null;
    const program = withProgressionChoices(source, l.sessionProgressions || {});
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
        readiness: null, injuryReports: [], sessionProgressions: {}, sessionNote: '', sessionNoteRepeat: 'none', feedback: {}, rest: null };
    S.live = l;
    const ctx = sessionContext(l);
    if (!ctx) { S.live = null; return { error: 'That workout day no longer exists.' }; }
    l.data = freshData(ctx).data;
    l.dayExSig = ctx.day.exercises.join('|');
    markLive();
    return session();
}

function liveSummary() {
    const l = S.live, p = programById(l.programId), day = p?.days.find((d) => d.id === l.dayId);
    const work = (l.data || []).flatMap((e) => e.sets.filter(isWork));
    return { programName: p?.name || '', dayLabel: day?.label || '', done: work.filter((s) => s.done).length, total: work.length, startedAt: l.startedAt };
}

function setView(s, i, ex, prBase) {
    const w = parseFloat(s.weight), r = parseInt(s.reps);
    let e1 = null; if (s.done && w > 0 && r > 0 && isWork(s)) e1 = E.e1rm(w, r);
    return {
        i, warm: !!s.warm, sub: !!s.sub, kind: s.kind || (s.warm ? 'warm' : s.sub ? 'sub' : 'work'), done: !!s.done,
        weight: s.weight ?? '', reps: s.reps ?? '', rir: s.actualRIR ?? null, added: !!s.added,
        target: s.target ? { w: s.target.w ?? null, reps: s.target.reps ?? null, rir: s.target.rir ?? null } : null,
        hint: typeof s.hint === 'string' ? s.hint : null,
        userOwned: !!E.userOwnsRuntimeSet?.(s),
        pr: !!(e1 && prBase > 0 && e1 > prBase)
    };
}

export function session() {
    const l = S.live;
    if (!l) return { active: false };
    const ctx = sessionContext(l);
    if (!ctx) return { active: false, error: 'That workout day no longer exists.' };
    const u = unit(), bests = (() => { try { return E.liftBests(ctx.H, u) || {}; } catch { return {}; } })();
    const data = l.data || [];
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
            return { ei, id: e.id, name: ex.name || e.id, part: ex.part || '', equip: ex.equip || null, slot: e.slot, primary: e.slot === ctx.day.primaryIndex,
                superset: !!e.superset, note: e.note || '', injuryAdjusted: e.injuryAdjusted || null,
                done: work(e).length > 0 && work(e).every((s) => s.done), sets: e.sets.map((s, i) => setView(s, i, ex, best)) };
        }),
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
        const pendingAnywhere = out.some((x) => x.sets.some((s) => !s.done));
        if (!wasDone && pendingAnywhere && S.store.restAutoStart !== false) {
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
    readiness({ value }, ctx, d) { S.live.readiness = value || null; return d; },
    sessionNote({ text, repeat }, ctx, d) { S.live.sessionNote = String(text || ''); S.live.sessionNoteRepeat = repeat || 'none'; return d; },
    restDone(_, ctx, d) { S.live.rest = null; return d; },
    restAdjust({ delta }, ctx, d) { if (S.live.rest) S.live.rest = { ...S.live.rest, seconds: Math.max(0, S.live.rest.seconds + delta) }; return d; },
    tick({ elapsedMs }, ctx, d) { S.live.elapsedMs = Math.max(0, Number(elapsedMs) || 0); return d; },
    feedback({ part, pump, sore }, ctx, d) { S.live.feedback = { ...(S.live.feedback || {}), [part]: { pump, sore } }; return d; }
};

export function act({ type, ...args } = {}) {
    const l = S.live; if (!l) return { active: false };
    const fn = ACTIONS[type]; if (!fn) return { ...session(), error: 'Unknown action ' + type };
    const ctx = sessionContext(l); if (!ctx) return { active: false };
    const sug = type === 'addSet' ? ctx.day.exercises.map((_id, slot) => E.sessionSuggestion(ctx.program, ctx.day, slot, ctx.dayPerf, unit(), ctx.wk, ctx.H)) : null;
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
    const fb = l.feedback || {};
    const log = {
        id: l.sessionId, programId: program.id, programName: program.name, dayLabel: day.label, dayId: day.id,
        engineV: program.engineV || 1, weekIndex: wk, setsDone: doneSets, totalSets, volume: Math.round(volume), unit: u,
        readiness: l.readiness, readinessStatus: l.readiness?.factor < 1 ? 'low' : 'normal', badDay: l.readiness?.factor < 1,
        substitutionOccurred: data.some((e) => e.id !== E.exerciseAt(program, day, e.slot, wk)),
        durationMin: Math.max(1, Math.round(elapsed / 60000)), estMin, perf: perfOut,
        feedback: Object.fromEntries(Object.entries(fb).map(([k, v]) => [k, E.feedbackDelta ? E.feedbackDelta(v.pump, v.sore) : 0])),
        injuryReports: l.injuryReports?.length ? l.injuryReports : undefined,
        sessionNote: (l.sessionNote || '').trim() || undefined, sessionNoteRepeat: l.sessionNoteRepeat,
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
    Object.assign(S.store, { history: nextHistory, perf: nextPerf, tombs, injuryLog, saved: nextSaved });
    S.live = null; markStore(); markLive();
    return { ok: true, entryId: entry.id, setsDone: doneSets, totalSets, volume: Math.round(volume), durationMin: log.durationMin,
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
    if (!l || !l.sessionId || history().some((h) => h.id === l.sessionId) || !programById(l.programId)) return { ok: false, reason: 'invalid' };
    S.live = l; markLive();
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
    S.store.history = H.filter((h) => h.id !== id);
    S.store.tombs = { ...(S.store.tombs || {}), [id]: now() };
    markStore();
    return { ok: true, label: gone.dayLabel || 'Workout' };
}
export function undo() {
    if (!S.undo) return { ok: false };
    Object.assign(S.store, S.undo); S.undo = null; markStore();
    return { ok: true };
}

/* ------------------------------------------------------------------ progress */
export function progress() {
    const H = history(), u = unit();
    let trends = []; try { trends = E.exerciseTrends(H) || []; } catch { trends = []; }
    let bests = {}; try { bests = E.liftBests(H, u) || {}; } catch { bests = {}; }
    let recap = null; try { recap = E.weeklyRecap(H, u); } catch { recap = null; }
    return {
        unit: u, sessions: H.length,
        lifts: trends.slice(0, 24).map((t) => ({ id: t.id, name: exName(t.id), points: (t.points || t.series || []).slice(-12).map((p) => ({ date: p.date ?? p.t ?? null, e1rm: Math.round((p.e1rm ?? p.v ?? p.y ?? 0) * 10) / 10 })), change: t.change ?? t.delta ?? null })),
        records: Object.entries(bests).map(([id, b]) => ({ id, name: exName(id), e1rm: Math.round((b.e1rm ?? b.best ?? 0) * 10) / 10, weight: b.weight ?? b.w ?? null, reps: b.reps ?? b.r ?? null, date: b.date ?? null })).sort((a, b) => b.e1rm - a.e1rm).slice(0, 30),
        recap,
        readiness: readiness()
    };
}

/* ------------------------------------------------------------------ plan */
export function plan({ programId, weekIndex } = {}) {
    const nx = upNext();
    const p = programId ? programById(programId) : nx?.program;
    if (!p) return null;
    const wk = weekIndex || (nx && nx.program.id === p.id ? nx.weekIndex : 1);
    return { programId: p.id, name: p.name, weekIndex: wk, weeksTotal: E.weeksOf(p), current: nx?.program.id === p.id ? nx.weekIndex : null,
        nextDayId: nx?.program.id === p.id ? nx.day.id : null, split: p.config?.split || null, goal: p.config?.goal || null,
        days: p.days.map((d) => { let m = null; try { m = E.estimateMinutes(p, d, wk); } catch { m = null; } return { id: d.id, label: d.label, minutes: m, exercises: dayPreview(p, d, wk) }; }),
        programs: saved().map((x) => ({ id: x.id, name: x.name, days: x.days.length, active: x.id === nx?.program.id })) };
}
export function pinProgram({ id }) { if (!programById(id)) return { ok: false }; S.store.pinnedId = id; markStore(); return { ok: true }; }

/* ------------------------------------------------------------------ library */
export function library({ q = '', part = null } = {}) {
    const needle = String(q).trim().toLowerCase();
    const banned = new Set(S.store.banned || []);
    return EXERCISES.filter((x) => (!part || x.part === part) && (!needle || x.name.toLowerCase().includes(needle)))
        .slice(0, 400).map((x) => ({ id: x.id, name: x.name, part: x.part, partLabel: E.PART_LABEL?.[x.part] || x.part, equip: x.equip, type: x.type, banned: banned.has(x.id) }));
}
export function exercise({ id }) {
    const x = EX_BY_ID[id]; if (!x) return null;
    let cues = []; try { cues = E.cuesFor(x) || []; } catch { cues = []; }
    const H = history().filter((h) => h.perf?.[id]).slice(0, 12);
    return { id, name: x.name, part: x.part, partLabel: E.PART_LABEL?.[x.part] || x.part, equip: x.equip, type: x.type, cues,
        secondary: (E.secondaryOf(x) || []).map(([p, f]) => ({ part: p, label: E.PART_LABEL?.[p] || p, share: f })),
        note: S.store.exNotes?.[id] || '', banned: (S.store.banned || []).includes(id),
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
        injuryAreas: INJURY_AREAS, injuryImpacts: INJURY_IMPACTS };
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
export function createProgram({ config, seed }) {
    const cfg = programConfig(config);
    let r;
    try { r = E.generateNextProgramForShell({ config: cfg, legacyExercises: E.shell.EXERCISES, seed: Number.isFinite(seed) ? seed : Math.floor(Math.random() * 1e6), makeId: () => 'p-' + E.uid() }); }
    catch (err) { return { ok: false, error: String(err?.message || err) }; }
    const p = r?.program; if (!p) return { ok: false, error: 'The engine could not build that program.' };
    S.store.saved = [...saved(), p]; S.store.pinnedId = p.id;
    if (!S.store.unitChosen && cfg.unit) { S.store.unit = cfg.unit; S.store.unitChosen = true; }
    markStore();
    return { ok: true, programId: p.id, name: p.name, days: p.days.length };
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
        theme: st.theme || null, warmupCard: st.warmupCard !== false, lastBackup: st.lastBackup || null, sessions: history().length, programs: saved().length };
}
export function updateSettings(patch = {}) {
    const allowed = ['restScale', 'restAutoStart', 'haptics', 'theme', 'warmupCard'];
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
    st.saved = saved().map(convProgram);
    if (st.goals) { const o = {}; for (const id in st.goals) { const n = parseFloat(st.goals[id]); o[id] = n > 0 ? Math.round(n * k * 2) / 2 : st.goals[id]; } st.goals = o; }
    { const n = parseFloat(st.bodyweight); if (n > 0) st.bodyweight = String(Math.round(n * k * 2) / 2); }
    if (Array.isArray(st.bwLog)) st.bwLog = st.bwLog.map((e) => ({ ...e, w: conv(e.w), unit: to }));
    if (st.measurements && typeof st.measurements === 'object') { const lu = E.lengthUnitFor(to), o = {}; for (const key in st.measurements) o[key] = E.normalizeMeasureLog(st.measurements[key], lu, from); st.measurements = o; }
    st.unit = to; st.unitChosen = true;
    applyGlobals(); markStore();
    return { ok: true, converted: true };
}
