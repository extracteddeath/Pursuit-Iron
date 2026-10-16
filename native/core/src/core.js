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
    let log = []; try { log = E.normalizeBwLog(S.store.bwLog || [], u) || []; } catch { log = S.store.bwLog || []; }
    return { unit: u, bodyweight: S.store.bodyweight || '', log: log.slice(-120), measurements: S.store.measurements || {} };
}
export function logBodyweight({ value }) {
    const v = Number(value); if (!(v > 0)) return { ok: false };
    const today = E.todayISO();
    const rows = (S.store.bwLog || []).filter((r) => r.date !== today);
    S.store.bwLog = [...rows, { date: today, v, unit: unit() }].slice(-(E.BW_LOG_CAP || 2000));
    S.store.bodyweight = String(v); markStore();
    return { ok: true };
}
export function logMeasurement({ key, value }) {
    const v = Number(value); if (!key || !(v > 0)) return { ok: false };
    const lu = E.lengthUnitFor ? E.lengthUnitFor(unit()) : (unit() === 'lb' ? 'in' : 'cm');
    const m = { ...(S.store.measurements || {}) };
    m[key] = [...(m[key] || []).filter((r) => r.date !== E.todayISO()), { date: E.todayISO(), v, unit: lu }];
    S.store.measurements = m; markStore(); return { ok: true };
}
export function settings() {
    const st = S.store;
    return { unit: unit(), restScale: Number(st.restScale) || 1, restAutoStart: st.restAutoStart !== false, haptics: st.haptics !== false,
        theme: st.theme || null, warmupCard: st.warmupCard !== false, lastBackup: st.lastBackup || null, sessions: history().length, programs: saved().length };
}
/* Unit switching converts stored numbers in the PWA (switchUnit); until that is ported it is only
   allowed on a store with no logged history, where there is nothing to convert. */
export function updateSettings(patch = {}) {
    const allowed = ['restScale', 'restAutoStart', 'haptics', 'theme', 'warmupCard'];
    Object.entries(patch).forEach(([k, v]) => { if (allowed.includes(k)) S.store[k] = v; });
    if (patch.unit && patch.unit !== unit()) {
        if (history().length) return { ok: false, error: 'Switching units with logged history is not available in this build yet.' };
        S.store.unit = patch.unit; S.store.unitChosen = true;
    }
    applyGlobals(); markStore();
    return { ok: true, settings: settings() };
}
export function markBackup() { S.store.lastBackup = now(); markStore(); return { ok: true }; }
