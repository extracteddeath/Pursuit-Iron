// Canonical prescriptions domain. Maintained production source; independent of React and browser APIs.
import { EXERCISE_MAP as NEXT_EXERCISE_MAP } from "../next-engine/exercise-db.js";
import { setShellEquipmentExpander, splitContractGaps, splitBuildability, refusalFixes, generateNextProgramForShell, recommendNextSplitForShell, getNextShellCell, canonicalShellSetCount, cloneNextDayPrescriptions, swapNextSlotPrescriptions, removeNextSlotPrescription, nextExerciseIdForShellExercise, resolveNextShellExerciseId, remapNextShellRoster, snapshotNextShellPrescription, markUserPrescriptionOverride, clearUserPrescriptionOverride, NextShellAdapterError } from "../next-engine/app-shell-adapter.js";
import { historyNumber, convertHistoryLoad, observedHistoryRIR, completedHistorySets, historyExposureContext, progressionExposureContext, normalizeHistoryEntries, validHistoryDate, resolveHistoryDayIndex, historyLoadReason } from '../next-engine/history-contract.js';
import { PCT_SCHEMES, percentageProtocolFor, adaptPercentageSetBudget, deriveTieredLinearState } from '../next-engine/percentage-protocols.js';
import { buildRuntimeSetTargets, customProgramProgressionStyle, refreshPendingSetTargets, reconcilePendingRepTargets, techniqueProtocolFromCell, freestyleCellForRepRange, buildUserAddedSlotPrescriptions } from "../next-engine/workout-runtime.js";
import { nextWorkoutSuggestionForShell, nextWorkoutSuggestionFromPerformedShell } from "../next-engine/workout-history-adapter.js";
import { evaluateWorkoutProgression } from "../next-engine/performance.js";
import { blockPhase, cellRepRange, clamp, computeCell, effortBounds, isBarLike, isMachineLike, loadStep, roundTo, secondaryOf, weeksOf } from './records.js';
import { EXERCISES, EX_BY_ID } from './catalog.js';
import { PART_ORDER, e1rmRIR } from './programs.js';

const PART_LABEL = {
    chest: "Chest", lats: "Lats", upper_back: "Upper Back", shoulders: "Shoulders", biceps: "Biceps",
    triceps: "Triceps", quads: "Quads", hamstrings: "Hamstrings", glutes: "Glutes", lower_back: "Lower Back",
    calves: "Calves", abs: "Abs / Core", traps: "Traps", forearms: "Forearms", neck: "Neck",
    adductors: "Adductors", abductors: "Abductors"
};

const INVENTORY_KEYS = [
    { key: "dumbbell", label: "Dumbbells", sub: "per hand", match: (eq) => eq.includes("dumbbell") },
    { key: "kettlebell", label: "Kettlebells", sub: "each bell", match: (eq) => eq.includes("kettlebell") },
    { key: "machine", label: "Machines & cables", sub: "stack notches", match: (eq) => isMachineLike(eq) && !eq.includes("dumbbell") },
];

let GYM_LIMITS = null;

function gymRackFor(ex, unit) {
    if (!GYM_LIMITS || !ex)
        return null;
    const eq = ex.equip || [];
    if (!eq.length)
        return null;
    const hit = INVENTORY_KEYS.find(k => k.match(eq));
    const list = hit ? GYM_LIMITS.inventory[hit.key] : null;
    if (!list || !list.length)
        return null;
    const from = GYM_LIMITS.unit;
    return list.map(v => toUnit(v, from, unit)).sort((a, b) => a - b);
}

const BASE_SET_COST = 0.5;

const __recoveryMemo = new WeakMap();

function muscleRecovery(history) {
    if (history && typeof history === "object") {
        const hit = __recoveryMemo.get(history);
        if (hit && Date.now() - hit.at < 60000)
            return hit.v;
    }
    const v = muscleRecoveryUncached(history);
    if (history && typeof history === "object")
        __recoveryMemo.set(history, { v, at: Date.now() });
    return v;
}

const __recovFitMemo = new WeakMap();

function personalRecoveryHours(history, part, clockHours) {
    if (!history || !history.length)
        return null;
    let byPart = __recovFitMemo.get(history);
    if (!byPart) {
        byPart = new Map();
        __recovFitMemo.set(history, byPart);
    }
    if (byPart.has(part))
        return byPart.get(part);
    const sess = []; // per-muscle session best e1RM, oldest→newest
    const sorted = [...(history || [])].filter(h => h && h.date).sort((a, b) => a.date - b.date);
    for (const h of sorted) {
        let best = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== part)
                continue;
            const ss = setsOf(p);
            for (const s of ss) {
                const w = +(s.w != null ? s.w : s.weight), r = +(s.r != null ? s.r : s.reps);
                if (w > 0 && r > 0 && !s.warm && !s.sub) {
                    const e = e1rmRIR(convertHistoryLoad(w, h.unit, "kg"), r, 0);
                    if (e > best)
                        best = e;
                }
            }
        }
        if (best > 0)
            sess.push({ date: h.date, e: best });
    }
    const obs = []; // (gapHours, performance ratio vs previous session)
    for (let i = 1; i < sess.length; i++) {
        const gap = (sess[i].date - sess[i - 1].date) / 3600000;
        if (gap <= 0 || gap > 240)
            continue;
        obs.push({ gap, ratio: sess[i].e / sess[i - 1].e });
    }
    let out = null;
    const gaps = obs.map(o => o.gap);
    const spread = obs.length ? Math.max(...gaps) - Math.min(...gaps) : 0;
    if (obs.length >= 8 && spread >= 24) {
        const n = obs.length, mx = gaps.reduce((s, g) => s + g, 0) / n, my = obs.reduce((s, o) => s + o.ratio, 0) / n;
        let num = 0, den = 0;
        for (const o of obs) {
            num += (o.gap - mx) * (o.ratio - my);
            den += (o.gap - mx) ** 2;
        }
        const b = den > 0 ? num / den : 0, a = my - b * mx;
        if (b > 1e-5) { // rest must measurably help, else recovery isn't identified
            const est = clamp((1.0 - a) / b, 18, 120); // gap at which performance returns to prior level
            const conf = clamp((n - 8) / 16, 0, 1) * clamp(spread / 48, 0, 1);
            out = clamp(clockHours + conf * (est - clockHours), 18, 120);
        }
    }
    byPart.set(part, out);
    return out;
}

function muscleRecoveryUncached(history) {
    const now = Date.now();
    /* Read effort through the SAME calibration the load engine uses. Fatigue is driven by how close to
       failure a set actually was, not by how close the lifter believed it was — and the app already
       measures the gap between those two and corrects load selection for it. Leaving recovery on the
       raw numbers meant the engine held two contradictory views of the same lifter: sizing their loads
       as though a logged "2 RIR" were really 0, while charging fatigue as though it were a comfortable
       2. For someone logging conservatively this UNDER-states fatigue, so it can raise trims, not lower
       them; that is the honest direction. A lifter with no measurable bias gets bias 0 and is unaffected. */
    const effortBias = (effortCalibration(history) || {}).bias || 0;
    const last = {}; // part -> {date, sets}
    const sorted = [...(history || [])].filter(h => h && h.date).sort((a, b) => b.date - a.date);
    sorted.forEach(h => {
        const sess = {}, overW = {}, overN = {}, prescW = {}, prescN = {};
        Object.entries(h.perf || {}).forEach(([id, p]) => {
            const ex = EX_BY_ID[id];
            if (!ex || !p.sets || !p.sets.length)
                return;
            // Fatigue per set scales with how close to failure it was: a set taken to failure (RIR 0)
            // costs full fatigue, a set left with reps in reserve (what most prescriptions call for)
            // costs less. So training at the prescribed RIR stops accruing failure-level fatigue.
            // Fatigue cost per set is RELATIVE to what was prescribed, not absolute. Training AT the
            // prescribed reserve is by design recoverable, so each such set costs a flat baseline. Cost
            // only climbs when you OVER-reach — logging fewer reps in reserve than prescribed (grinding
            // closer to failure than the plan asked). Leaving MORE in reserve than prescribed costs a
            // touch less. So logging the prescribed RIR never reads as if you trained to failure, and the
            // recovery estimate stops over-stating fatigue on a normal, on-plan session.
            /* DELIBERATELY RAW RIR — do not "fix" this to use the effort calibration.
               It looks like an inconsistency: effortCalibration can conclude a lifter logs ~2 RIR
               conservative, and this reads their logged 2 at face value. But the calibrated RIR already
               reaches the LOAD through dayPerf -> sessionSuggestion (measured: 155lb raw vs 145lb
               calibrated on the same session), so the prescription has already been sized down to put
               their true effort back at the intended reserve. Applying the bias a second time here would
               charge them again for a gap the load has already closed — a conservative logger would sit in
               permanent overreach and be trimmed every session forever.
               The invariant that makes this correct is gated: if the calibration ever stops reaching the
               prescription, gate_templates fails, and THEN this read needs to change. */
            /* Also carry the PRESCRIBED reserve, not just the overshoot. An overshoot of 0.75 means two
               completely different things depending on how much room the plan left: against a prescription
               of RIR 4 the lifter blew through the plan, but against a prescription of RIR 0.76 — which is
               what week 4 of an intensification block actually asks for — it is a quarter of a rep, and the
               plan was already at the edge on purpose. Without this the model could not tell them apart. */
            let overSum = 0, overN_ = 0, prescSum = 0;
            const n = setsOf(p).reduce((a, s) => {
                const presc = s.tr != null ? s.tr : 2; // prescription (default RIR 2 for legacy logs)
                // Calibrated, not raw: what the reserve ACTUALLY was. Unlogged sets still assume on-plan.
                const logged = s.rir != null ? clamp(s.rir - effortBias, 0, 6) : presc;
                const over = Math.max(0, presc - logged); // ground out BELOW plan → harder than asked
                const under = Math.max(0, logged - presc); // left ABOVE plan → easier than asked
                overSum += over;
                overN_++;
                prescSum += presc;
                return a + clamp(BASE_SET_COST + over * 0.2 - under * 0.06, 0.3, 1.3);
            }, 0);
            sess[ex.part] = (sess[ex.part] || 0) + n;
            /* Accumulate weighted by SETS, not Math.max. Taking one accessory to failure while doing the
               other three lifts exactly as prescribed is not the same session as grinding all of them out,
               and max() reported them identically — so the banner would tell someone they took "most of"
               a session to failure when three quarters of it was on plan. The sentence has to be true. */
            overW[ex.part] = (overW[ex.part] || 0) + overSum;
            overN[ex.part] = (overN[ex.part] || 0) + overN_;
            prescW[ex.part] = (prescW[ex.part] || 0) + prescSum;
            prescN[ex.part] = (prescN[ex.part] || 0) + overN_;
            /* ⚠ DELIBERATELY UNTHREADED. This is muscleRecoveryUncached — FATIGUE, not growth. The
               hamstrings genuinely work during a squat (25-50% MVIC co-contraction) and so accrue recovery
               debt, even though the same squat produces no measurable hypertrophy (Kubo, Plotkin). Growth
               credit and fatigue credit are different questions and the kneeFlexedHams correction applies
               only to the first. */
            secondaryOf(ex).forEach(([pp, f]) => { sess[pp] = (sess[pp] || 0) + n * f; });
        });
        Object.entries(sess).forEach(([part, sets]) => { if (!last[part])
            last[part] = { date: h.date, sets, overBy: overN[part] ? (overW[part] / overN[part]) : 0, plannedRIR: prescN[part] ? (prescW[part] / prescN[part]) : null }; });
    });
    return PART_ORDER.map(part => {
        const l = last[part];
        if (!l)
            return { part, readiness: 100, daysSince: null, status: "fresh", sets: 0 };
        const hours = (now - l.date) / 3600000;
        const clockHours = 30 + clamp(l.sets, 0, 28) * 2.3; // on-plan training recovers in ~2 days; overreach (higher cost) extends it
        /* `measured` records WHERE the recovery estimate came from. It matters downstream: a clock
           estimate is a population average and has no idea what frequency this program was designed
           around, while a measured one is this lifter's own demonstrated dip on short rest. Only the
           second is evidence worth overruling a plan with. */
        const personal = personalRecoveryHours(history, part, clockHours);
        const recoveryHours = personal ?? clockHours; // lifter's measured recovery when history earns it, else the clock model
        const readiness = Math.round(clamp(hours / recoveryHours, 0, 1) * 100);
        return { part, readiness, hoursSince: hours, measured: personal != null, overBy: l.overBy || 0, plannedRIR: l.plannedRIR != null ? l.plannedRIR : null, daysSince: Math.floor(hours / 24), status: readiness >= 85 ? "fresh" : readiness >= 55 ? "recovering" : "fatigued", sets: l.sets };
    });
}

function trainingMaxForUnit(program, exerciseId, unit) {
    return convertHistoryLoad(program.trainingMax?.[exerciseId], program.trainingMaxUnit || program.config?.unit || unit, unit);
}

function withTrainingMax(program, exerciseId, value, unit) {
    const storageUnit = program.trainingMaxUnit || program.config?.unit || unit;
    const load = convertHistoryLoad(value, unit, storageUnit);
    return { ...program, trainingMaxUnit: storageUnit,
        trainingMax: { ...(program.trainingMax || {}), [exerciseId]: load > 0 ? load : 0 }, edited: true };
}

function pctSetsFor(scheme, tm, weekIndex, weeksTotal, unit, ex, dayType, tier = "t1") {
    return percentageProtocolFor({ scheme, tm, weekIndex, weeksTotal, dayType, tier,
        snapLoad: value => loadableAtOrBelow(ex, value, unit) });
}

function percentagePlanFor(program, day, ex, slot, weekIndex, unit, history = []) {
    const tier = tierOf(day, slot);
    if (!tier || !program.config?.percentScheme) return null;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    if (cell.ownership?.reps === 'user' || cell.ownership?.rir === 'user') return null;
    const sourceUnit = program.trainingMaxUnit || program.config?.unit || unit;
    const tm = trainingMaxForUnit(program, ex.id, unit);
    let plan;
    if (program.config.percentScheme === 'gzclp') {
        const source = gzPlanFor(program, day, ex, slot, sourceUnit, history);
        plan = source ? { ...source, sets: source.sets.map(t => ({ ...t,
            weight: loadableAtOrBelow(ex, convertHistoryLoad(t.weight, sourceUnit, unit), unit) })) } : null;
    } else plan = pctSetsFor(program.config.percentScheme, tm, weekIndex, weeksOf(program), unit, ex, day.type, tier);
    if (!plan?.sets?.length) return null;
    if (program.config.percentScheme === 'gzclp' && weekIndex > weeksOf(program))
        plan = { ...plan, label: 'Deload · light', sets: plan.sets.map(t => ({ ...t,
            weight: loadableAtOrBelow(ex, t.weight * .6, unit), reps: String(t.reps).replace('+',''), amrap: false })) };
    return adaptPercentageSetBudget(plan, Number(cell.sets));
}

function tierOf(day, slot) {
    if (!day)
        return null;
    if (slot === day.primaryIndex)
        return "t1";
    if (day.t2Index != null && slot === day.t2Index)
        return "t2";
    return null;
}

const GZ_T1_STAGES = [
    { label: "T1 · 5×3+", n: 5, reps: 3 },
    { label: "T1 · 6×2+", n: 6, reps: 2 },
    { label: "T1 · 10×1+", n: 10, reps: 1 },
];

const GZ_T2_STAGES = [
    { label: "T2 · 3×10", n: 3, reps: 10 },
    { label: "T2 · 3×8", n: 3, reps: 8 },
    { label: "T2 · 3×6", n: 3, reps: 6 },
];

function gzTierOf(program, day, slot) {
    if (program?.config?.percentScheme !== "gzclp" || !day)
        return null;
    if (slot === day.primaryIndex)
        return "t1";
    if (day.t2Index != null && slot === day.t2Index)
        return "t2";
    return null;
}

function gzWorkWeight(program, exId, tier, unit, ex) {
    const step = loadStep(ex, unit);
    const t1 = historyNumber(program.trainingMax?.[exId]) ?? 0;
    if (tier === "t1")
        return t1;
    const t2 = historyNumber(program.gz?.[exId]?.t2w) ?? 0;
    if (t2 > 0)
        return t2;
    return t1 > 0 ? roundTo(t1 * 0.66, step) : 0; // a sensible T2 start (~10RM ≈ ⅔ of the 3RM weight)
}

function gzPlanFor(program, day, ex, slot, unit, history = []) {
    const tier = gzTierOf(program, day, slot);
    if (!tier)
        return null;
    const w = gzWorkWeight(program, ex.id, tier, unit, ex);
    if (!(w > 0))
        return null;
    const defs = tier === "t1" ? GZ_T1_STAGES : GZ_T2_STAGES;
    const rawStage = Number(program.gzStage?.[ex.id + ':' + tier]);
    const initialStage = Number.isInteger(rawStage) ? Math.max(0, Math.min(rawStage, defs.length - 1)) : 0;
    const replay = deriveTieredLinearState({ programId: program.id, exerciseId: ex.id, tier,
        initialStage, initialLoad: w, entries: history, unit,
        nextLoad: weight => loadableAbove(ex, weight, unit),
        resetLoad: weight => loadableAtOrBelow(ex, weight * .85, unit) });
    const stage = replay.stage, st = defs[stage];
    const sets = [];
    for (let i = 0; i < st.n; i++) {
        const amrap = i === st.n - 1; // last set is AMRAP — it drives the progression
        sets.push({ weight: replay.weight, reps: amrap ? st.reps + "+" : String(st.reps), amrap, pct: 100 });
    }
    return { label: st.label, sets, tier, stage };
}

function summarizeSets(sets, ex, unit) {
    /* SUB-SETS ARE NOT WORKING SETS. Drop sets and myo mini-sets are extensions performed at or below
       the working load, deliberately short — a 4-rep mini or a stripped-load rep-out. Folding them in
       corrupted the summary in both branches: at a shared load the straight-set branch takes the MIN
       reps at the top weight, so one 4-rep mini reported the whole lift as 4 reps; at mixed loads the
       ramp branch averaged the stripped weight in and under-prescribed. The summary is what next
       session's opening load is built from, so it must describe the work, not the extensions. */
    const done = sets.filter(s => s.done && !s.warm && !s.sub && parseInt(s.reps) > 0);
    if (!done.length)
        return null;
    const wv = s => { const w = parseFloat(s.weight); return isNaN(w) ? 0 : w; };
    // If any working set carried real load it's a loaded lift — summarise from the loaded sets only.
    // Otherwise it's bodyweight (all 0/blank) or assisted (negative = machine/band help): keep those
    // sets, and pick the "best" as the one closest to bodyweight — heaviest load, or LEAST assistance.
    const hasPos = done.some(s => parseFloat(s.weight) > 0);
    const pool = hasPos ? done.filter(s => parseFloat(s.weight) > 0) : done;
    const weights = pool.map(wv);
    const maxW = Math.max(...weights);
    // Straight sets (every working set at the same load): summarise from the top tier exactly as before.
    if (weights.every(w => w === weights[0])) {
        const atTop = pool.filter(s => wv(s) === maxW);
        const reps = Math.min(...atTop.map(s => parseInt(s.reps)));
        return { weight: maxW, reps };
    }
    // Ramped / varying loads (e.g. 125→135→145→155): a single heaviest set isn't a load you actually
    // held across the session, so anchoring next session on it over-prescribes. Summarise from the
    // AVERAGE working load and the average reps instead — a weight you can realistically repeat across
    // all sets. (Per-set e1RM and PRs still come from the raw `sets` array, so a top-set PR isn't lost.)
    // Round the average onto the exercise's REAL increment (pin-stack notch / plate pair), not a flat
    // 0.5 — a raw mean like 36.5 on a 5 lb machine is a weight that doesn't exist, and it leaked into
    // "hold the same load" suggestions and the "Last" display as an impossible setting.
    const stepW = ex ? loadStep(ex, unit) : 0.5;
    const avgW = roundTo(weights.reduce((a, b) => a + b, 0) / weights.length, stepW);
    const reps = Math.round(pool.reduce((a, s) => a + parseInt(s.reps), 0) / pool.length);
    return { weight: avgW, reps };
}

function styleFor(program, id) {
    const s = program?.progStyle?.[id];
    if (!s)
        return "auto";
    /* ⚠ ENGINE-WRITTEN IS NOT LIFTER-CHOSEN. Next-engine programs used to have the engine's week-1 style written here, in the
       same map as a lifter's explicit choice, so resolveStyle treated it as one: the per-week block schedule never reached
       the workout and v661's adaptive rules never ran. New programs no longer write it (app-shell-adapter). For programs
       saved before that, an entry the lifter did NOT set (progStyleLifter) that equals the engine's own week-1 style for
       this exercise is engine-written, so it reads as auto. Anything the lifter set always wins. */
    if (program?.engineSource === "pursuit-next" && !program?.progStyleLifter?.[id] && engineWeekOneStyles(program, id).has(s))
        return "auto";
    return s;
}

function engineWeekOneStyles(program, id) {
    const out = new Set();
    for (const d of program?.days || [])
        (d.exercises || []).forEach((x, slot) => {
            if (x !== id)
                return;
            const st = program?.nextWeekPrescriptions?.[`${d.id}:${slot}`]?.[1]?.progressionStyle;
            if (st && st !== "auto")
                out.add(st);
        });
    return out;
}

function resolveStyle(program, ex, isPrimary, weekIndex, perf, history, dayId) {
    if (program?.custom === true && program?.engineSource !== "pursuit-next") {
        const day = program.days?.find(d => d.id === dayId), slot = day?.exercises?.indexOf(ex?.id);
        const cell = slot >= 0 ? computeCell(program, day, ex.id, slot, weekIndex) : null;
        const requested = program.progStyle?.[ex?.id] ?? cell?.progressionStyle ?? "auto";
        const nextEx = NEXT_EXERCISE_MAP.get(nextExerciseIdForShellExercise(ex)) || {
            flags: { compound: ex?.type === "compound", barbell: isBarLike(ex?.equip) }, equipment: ex?.equip || [] };
        return customProgramProgressionStyle(nextEx, cell, requested, program.config);
    }
    const explicit = styleFor(program, ex?.id);
    return explicit === "auto"
        ? autoStyleFor(program, ex, isPrimary, weekIndex, perf, history, dayId)
        : explicit;
}

function progressionHistoryForProgram(program, history) {
    const rows = (Array.isArray(history) ? history : []).filter(validHistoryDate);
    if (!rows.length)
        return [];
    const programId = program?.id;
    if (programId != null) {
        // Adaptive evidence obeys the same revision contract as the next-engine adapter:
        // one persisted workout identity contributes once, and only its newest revision survives.
        const own = normalizeHistoryEntries(rows, programId).entries;
        if (own.length)
            return own.slice().sort((a, b) => Number(b.date) - Number(a.date));
        // Once any tagged program history exists, unowned/other-program rows are ambiguous for adaptive
        // stall/fatigue decisions. A new program must establish its own comparable evidence.
        if (rows.some(h => h?.programId != null))
            return [];
    }
    // Pure legacy history predating program ownership remains usable and is made order-independent.
    return rows.slice().sort((a, b) => Number(b.date) - Number(a.date));
}

function plateauSessions(history, exId, dayId, limit = 6, program = null) {
    const scoped = program ? progressionHistoryForProgram(program, history) : (Array.isArray(history) ? history : []);
    const withEx = scoped.filter(h => historyNumber(h?.perf?.[exId]?.weight) > 0);
    if (!dayId)
        return withEx.slice(0, limit);
    if (program) {
        const targetIndex = (program.days || []).findIndex(day => String(day?.id ?? '') === String(dayId));
        if (targetIndex >= 0)
            return withEx.filter(h => resolveHistoryDayIndex(program.days, h) === targetIndex).slice(0, limit);
    }
    const sameDay = withEx.filter(h => h?.dayId != null && String(h.dayId) === String(dayId));
    // Legacy callers without a program cannot resolve migrated IDs safely. Preserve their old
    // unambiguous single-day fallback, while program-aware progression uses authored day ownership above.
    const days = new Set(withEx.map(h => h.dayId == null ? null : String(h.dayId)).filter(Boolean));
    return (days.size <= 1 ? withEx : sameDay).slice(0, limit);
}

const STALL_WINDOW = 8;

function stallCountFor(perf, ex, history, dayId, program = null) {
    if (!perf || !ex || !history)
        return 0;
    const p = perf[ex.id];
    if (!p?.weight)
        return 0;
    // history is newest-first already — take the front directly. (An earlier .reverse() here grabbed
    // the OLDEST sessions, which inverted prIdx and made progressing lifters look stalled.)
    const sessions = plateauSessions(history, ex.id, dayId, STALL_WINDOW, program);
    if (sessions.length < 3)
        return 0;
    const e1rms = sessions.map(h => {
        const hp = h.perf[ex.id];
        const sets = hp.sets?.length ? hp.sets : [{ w: hp.weight, r: hp.reps }];
        return Math.max(...sets.filter(s => s.r > 0).map(s => e1rmRIR(s.w, s.r, ASSUMED_RIR)));
    }).filter(v => v > 0);
    if (e1rms.length < 3)
        return 0;
    /* Sessions since the last STRICT PR. The old form — indexOf(max) — has a tie-breaking hole that
       hid the most common plateau there is: on a DEAD-FLAT run every e1RM is equal, indexOf returns
       the first of the ties (the newest session), and that reads as "PR today, zero stall". The
       detector could only see a plateau if you had got WEAKER; merely going nowhere was invisible.
       Walk from the newest instead: a session is the PR only if it strictly beats everything older. */
    let prIdx = e1rms.length - 1;
    for (let i = 0; i < e1rms.length; i++) {
        const olderMax = e1rms.length > i + 1 ? Math.max(...e1rms.slice(i + 1)) : 0;
        if (e1rms[i] > olderMax + 1e-6) {
            prIdx = i;
            break;
        }
    }
    return prIdx;
}

const STALL_ENGAGE = 4;

const E1RM_HOLD = 3;

function styleOverride(program, ex, isPrimary, weekIndex, perf, history, dayId) {
    if (!ex || styleFor(program, ex.id) !== "auto")
        return null; // an explicit choice is not ours to override
    if (program?.config?.percentScheme)
        return null; // percent schemes own their periodization
    const exp = program?.config?.experience || "intermediate";
    if (exp === "none" || exp === "beginner")
        return null; // beginners are on linear by structure
    const scopedHistory = progressionHistoryForProgram(program, history);
    const stall = stallCountFor(perf, ex, history, dayId, program);
    if (stall >= STALL_ENGAGE) {
        if (stall < STALL_ENGAGE + E1RM_HOLD) {
            const left = STALL_ENGAGE + E1RM_HOLD - stall;
            return {
                style: "e1rm", kind: "plateau", stall,
                why: `Recalibrating — ${stall} sessions without a new best`,
                clears: `Back to your program's progression on a new best, or after ${left} more session${left === 1 ? "" : "s"}`
            };
        }
        return null; // held its window and did not break the stall — hand the lift back
    }
    const fatigued = scopedHistory.length ? (() => {
        const rec = muscleRecovery(scopedHistory);
        const pr = rec.find(r => r.part === ex.part);
        return !!(pr && pr.readiness < 55);
    })() : false;
    if (fatigued) {
        return {
            style: ex.type === "compound" ? "e1rm" : "double", kind: "fatigue", stall,
            /* The text used to say "load eased to match", but no suggestion path eases the load for this readiness score — the engine's
               suggestion (engine programs) and the lifter's history (custom programs) are not adjusted by it. Say what is true and what to
               do, consistent with the coach's "back off today" advice. */
            why: `${PART_LABEL[ex.part] || ex.part} is still recovering — keep the reps clean, and take a little weight off if the warm-up feels heavy`,
            clears: "Back to your program's progression once the muscle has recovered"
        };
    }
    return null;
}

function linearStalled(perf, ex, history, program = null, dayId = null) {
    if (!perf || !ex)
        return false;
    const p = perf[ex.id];
    if (!p?.weight || !p.reps)
        return false;
    const scopedHistory = program ? progressionHistoryForProgram(program, history) : (Array.isArray(history) ? history : []);
    const recent = dayId
        ? plateauSessions(history, ex.id, dayId, 4, program)
        : scopedHistory.filter(h => historyNumber(h?.perf?.[ex.id]?.weight) > 0).slice(0, 4);
    if (recent.length < 3)
        return false;
    const weights = recent.map(h => h.perf[ex.id].weight);
    return weights[0] === weights[1] && weights[1] === weights[2];
}

function nextEngineStyleFor(program, ex, dayId, weekIndex) {
    if (program?.engineSource !== "pursuit-next" || !ex)
        return null;
    const days = program.days || [];
    const order = dayId ? days.filter(d => d.id === dayId).concat(days.filter(d => d.id !== dayId)) : days;
    for (const d of order) {
        const slot = (d.exercises || []).indexOf(ex.id);
        if (slot < 0)
            continue;
        const byWeek = program.nextWeekPrescriptions?.[`${d.id}:${slot}`];
        const st = (byWeek?.[weekIndex] || byWeek?.[1])?.progressionStyle;
        if (st && st !== "auto")
            return st;
    }
    return null;
}

function autoStyleDetail(program, ex, isPrimary, weekIndex, perf = null, history = null, dayId = null) {
    const R = (style, why, at = null) => ({ style, why, at });
    const goal = program?.config?.goal || "hypertrophy";
    const exp = program?.config?.experience || "intermediate";
    const weeks = weeksOf(program);
    const phase = blockPhase(program, weekIndex, 0.5); // honors cycle-block phaseWindow
    const comp = ex?.type === "compound";
    const strength = goal === "strength" || (goal === "both" && isPrimary);
    /* ⚠ NEXT-ENGINE PROGRAMS: the engine owns the BASE style — per slot and per week, i.e. the block schedule — and v661's
       runtime rules apply on top exactly as they do for legacy programs: a percent-scheme lift stays e1RM (structural), a
       stalled beginner graduates from linear, then plateau override -> soft stall nudge -> fatigue override. */
    const nextBase = nextEngineStyleFor(program, ex, dayId, weekIndex);
    if (nextBase) {
        if (program?.config?.percentScheme && nextBase === "e1rm")
            return R("e1rm", "percent-scheme lift — load is matched to %TM, not to a rep range");
        if (nextBase === "linear")
            return linearStalled(perf, ex, history, program, dayId)
                ? R("double", "beginner compound whose linear progression stalled — same weight for 3 sessions, so it graduates to double progression")
                : R("linear", "beginner compound — linear progression is the simplest thing that still works");
        const stallN = stallCountFor(perf, ex, history, dayId, program);
        const ovN = styleOverride(program, ex, isPrimary, weekIndex, perf, history, dayId);
        if (ovN && ovN.kind === "plateau")
            return R(ovN.style, `plateau override — ${ovN.why || "a hard plateau was detected"}${ovN.clears ? `. ${ovN.clears}` : ""}`, { stallSessions: stallN, override: ovN });
        if (stallN >= 2 && !strength)
            return R("double", `stalled ${stallN} sessions and the goal is not strength — nudged to the simpler style until the stall clears`, { stallSessions: stallN });
        if (ovN)
            return R(ovN.style, `${ovN.kind} override — ${ovN.why || "an adaptive override is active"}${ovN.clears ? `. ${ovN.clears}` : ""}`, { stallSessions: stallN, override: ovN });
        return R(nextBase, `Planned progression — ${nextBase} for this lift in week ${weekIndex} of the block`);
    }
    // ── 1. Structural: percent-scheme programs own their own periodization ──────
    if (program?.config?.percentScheme)
        return R("e1rm", "percent-scheme program — load is matched to %TM, not to a rep range");
    // ── 2. Beginners: linear is maximally effective and simple ────────────────────
    if (exp === "none" || exp === "beginner") {
        if (comp) {
            // Check for LP stall: 3+ consecutive sessions without hitting the rep target → graduate
            const lpStall = linearStalled(perf, ex, history, program, dayId);
            if (lpStall)
                return R("double", "beginner compound whose linear progression stalled — same weight for 3 sessions, so it graduates to double progression");
            return R("linear", `beginner (${exp}) compound — linear progression is the simplest thing that still works`);
        }
        return R("double", `beginner (${exp}) isolation — double progression`);
    }
    // ── 3/4. Adaptive overrides — plateau, then fatigue ───────────────────────────
    /* Both used to be decided HERE, in their own inline branches, while suggestWeightUncapped
       re-derived the same two conditions separately in order to caption them. Two deciders for one
       decision. They now come from styleOverride, which owns the conditions, the reason strings AND
       the exit — so a style can never be adopted without an answer to "what ends this?".
       PRECEDENCE IS PRESERVED EXACTLY: hard plateau, then the soft-plateau nudge, then fatigue. The
       soft nudge stays inline because it is not an override with a hold window; it swaps one
       rep-range style for a simpler one and reverses itself the moment the stall clears. */
    const stallSessions = stallCountFor(perf, ex, history, dayId, program);
    const ov = styleOverride(program, ex, isPrimary, weekIndex, perf, history, dayId);
    if (ov && ov.kind === "plateau")
        return R(ov.style, `plateau override — ${ov.why || "a hard plateau was detected"}${ov.clears ? `. ${ov.clears}` : ""}`, { stallSessions, override: ov });
    if (stallSessions >= 2 && !strength)
        return R("double", `stalled ${stallSessions} sessions and the goal is not strength — nudged to the simpler style until the stall clears`, { stallSessions });
    if (ov)
        return R(ov.style, `${ov.kind} override — ${ov.why || "an adaptive override is active"}${ov.clears ? `. ${ov.clears}` : ""}`, { stallSessions, override: ov });
    // ── 5. Base style by goal × experience × phase ────────────────────────────────
    // Per-set DDP ("dynamic") lets each set settle on its own load, which is ideal on a machine,
    // cable, or dumbbell (a pin or a DB swap is instant) but a chore on a loaded barbell — you'd strip
    // plates between sets of the same lift. So for auto, a BARBELL compound's accumulation work uses
    // DOUBLE PROGRESSION instead: one weight, hold it and build toward the top of the rep range, then
    // an e1RM-accurate load jump once the range is sustained. Crucially it reads the LAST (most
    // fatigued) set to decide the load, so it fits heavy compounds where reps naturally fall across
    // sets (8→7→6→5) — unlike a single shared "rep ladder" target, which anchors on one set and can
    // read as "do fewer reps than your best." Non-barbell keeps true per-set DDP. (The rep ladder
    // remains available as a manual choice — it suits machines/isolation, where reps hold across sets.)
    const barbell = isBarLike(ex?.equip);
    const accumStyle = (comp && barbell) ? "double" : "dynamic";
    if (strength && comp && isPrimary) {
        // Short blocks (≤4 weeks) don't have enough sessions to complete a meaningful
        // wave cycle (hi→mid→lo→load-up needs ≥3 sessions). Skip wave and go straight
        // from accumulation to e1RM intensity.
        if (weeks <= 4) {
            return phase >= 0.5
                ? R("e1rm", `strength primary compound, short block (${weeks} weeks) past halfway (phase ${phase.toFixed(2)}) — skip wave, go straight to e1RM`, { phase, weeks })
                : R(accumStyle, `strength primary compound, short block (${weeks} weeks), still accumulating (phase ${phase.toFixed(2)})`, { phase, weeks });
        }
        // Peak phase (>75% through block): e1RM precision-targets the load for intensity
        if (phase >= 0.75)
            return R("e1rm", `strength primary compound in the peak phase (${phase.toFixed(2)} >= 0.75) — e1RM precision-targets the load`, { phase });
        // Mid-block intensification: wave loading (hi→mid→lo reps = volume/medium/heavy)
        if (phase >= 0.35)
            return R("wave", `strength primary compound mid-block (phase ${phase.toFixed(2)}) — wave loading`, { phase });
        // Early accumulation: rep ladder (barbell) or per-set DDP (machine/DB) — build volume + reps
        return R(accumStyle, `strength primary compound early in the block (phase ${phase.toFixed(2)}) — accumulation${barbell ? ", on a barbell so double rather than per-set" : ""}`, { phase });
    }
    if (strength && comp && !isPrimary) {
        // Accessory compounds in strength programs: accumulation, switching to e1rm late
        return phase >= 0.65
            ? R("e1rm", `strength ACCESSORY compound late in the block (phase ${phase.toFixed(2)} >= 0.65) — switch to e1RM`, { phase })
            : R(accumStyle, `strength ACCESSORY compound, still accumulating (phase ${phase.toFixed(2)})`, { phase });
    }
    // Hypertrophy: compounds → per-set DDP (machine/DB) or rep ladder (barbell); isolations →
    //              double progression (simpler, sufficient for single-joint)
    if (!strength) {
        if (comp)
            return R(accumStyle, `hypertrophy compound${barbell ? " on a barbell — double progression, since stripping plates between sets is a chore" : " on a machine or dumbbell — per-set dynamic, where changing the load is instant"}`);
        return R("double", "hypertrophy isolation — double progression is simpler and sufficient for single-joint work");
    }
    // "Both" goal, isolation or non-primary
    if (comp)
        return R(accumStyle, `"both" goal, non-primary compound${barbell ? " on a barbell" : ""} — accumulation`);
    return R("double", '"both" goal, isolation — double progression');
}

function autoStyleFor(program, ex, isPrimary, weekIndex, perf = null, history = null, dayId = null) {
    return autoStyleDetail(program, ex, isPrimary, weekIndex, perf, history, dayId).style;
}

function parseRIRNum(r) {
    const bounds = effortBounds(r);
    return bounds ? (bounds[0] + bounds[1]) / 2 : 2;
}

function prescribedRIRof(set) {
    if (!set || !set.target)
        return null;
    if (set.target.failure)
        return 0;
    const t = set.target.rir;
    const n = typeof t === "number" ? t : parseRIRNum(t);
    return Number.isFinite(n) ? n : null;
}

function dayPerfFor(day, perf, history, program = null) {
    const out = {};
    const scopedHistory = program ? progressionHistoryForProgram(program, history) : (history || []);
    const targetIndex = program ? (program.days || []).findIndex(candidate => candidate?.id === day?.id) : -1;
    // Estimated 1RM of a session's best logged set — used only to pick which recent session anchors
    // the next suggestion, so demonstrated capacity wins over a single off day.
    const anchorE1 = pp => {
        const ss = (pp.sets && pp.sets.length) ? pp.sets : [{ w: parseFloat(pp.weight), r: parseInt(pp.reps), rir: null }];
        let best = 0;
        ss.forEach(s => {
            const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
            if (w > 0 && r > 0)
                best = Math.max(best, e1rmRIR(w, r, s.rir != null ? s.rir : ASSUMED_RIR));
        });
        return best;
    };
    day.exercises.forEach(id => {
        // Anchor the suggestion on the BEST of the last few comparable sessions OF THIS DAY. When the
        // program is known, program ownership and day migration both resolve through the canonical
        // provenance rules; another program/day can never become progression evidence by id collision.
        const recent = [];
        for (const h of scopedHistory) {
            const sameDay = program ? resolveHistoryDayIndex(program.days, h) === targetIndex : h.dayId === day.id;
            if (sameDay && h.perf && h.perf[id] && h.perf[id].reps != null) {
                recent.push(h.perf[id]);
                if (recent.length >= 3)
                    break;
            }
        }
        if (recent.length)
            out[id] = recent.reduce((a, b) => anchorE1(b) > anchorE1(a) ? b : a);
        else if (perf && perf[id])
            out[id] = perf[id];
    });
    return out;
}

function sessionSuggestion(program, day, slot, dayPerf, unit, weekIndex, history) {
    void dayPerf;
    if (program?.custom === true && program?.engineSource !== "pursuit-next")
        return customProgramSuggestion(program, day, slot, unit, weekIndex, history);
    const original = nextWorkoutSuggestionForShell(program, history || [], EXERCISES, day, slot, weekIndex);
    const convert = value => convertHistoryLoad(value, program.config?.unit, unit);
    const s = original ? { ...original, weight: convert(original.weight),
        last: original.last,
        setTargets: original.setTargets?.map(t => ({ ...t, weight: convert(t.weight) })),
        reason: historyLoadReason(original.reason, unit) } : null;
    /* ⚠ WHAT IS LOADABLE HAS ONE OWNER: the app's loadStep / gymRackFor — the same rule the workout's "load-not-loadable" check
       uses. The engine rounds against its own loading inventory, which can allow a weight the app's implement cannot make (45 lb on
       a 10 lb machine stack). Measured: the week-aware re-prescription produced 1,160 such loads in the self-test. Every suggestion
       is snapped DOWN to a loadable weight here, whichever engine path produced it. */
    if (!s || !(Number(s.weight) > 0))
        return s;
    const ex = EX_BY_ID[day?.exercises?.[slot]], eng = Number(s.weight);
    /* ⚠ DIRECTION MATTERS. An EARNED increase rounds UP to the next weight the equipment can make; everything else rounds DOWN
       (conservative). Rounding an increase down erased it: the engine said "Increase from 140 lb to 145 lb", the 10 lb machine stack
       snapped it back to 140, and the lift could never progress (found by m158-adaptive-loop-check's simulated lifter). */
    const current = convertHistoryLoad(s.last?.weight, s.lastUnit || program.config?.unit, unit) || 0;
    let w = loadableAtOrBelow(ex, eng, unit);
    if (s.action === "increase_load" && w <= current)
        w = loadableAbove(ex, current, unit) ?? w;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    const floor = cellRepRange(cell, program, ex, slot === day.primaryIndex)[0];
    const setTargets = s.setTargets?.map(target => {
        const weight = s.action === "increase_load" ? w : loadableAtOrBelow(ex, target.weight, unit);
        return { ...target, weight, reps: weight < target.weight ? floor : target.reps };
    });
    const reason = typeof s.reason === "string" ? s.reason.replace(new RegExp(`\\bto ${eng}( ?${unit})`), `to ${w}$1`) : s.reason;
    return { ...s, weight: w, setTargets, reason, target: w < eng ? floor : s.target,
        dir: current && w > current ? "up" : current && w < current ? "down" : s.dir };

}

function loadableAbove(ex, w, unit) {
    if (!ex || !(w > 0))
        return null;
    const rack = gymRackFor(ex, unit);
    if (rack && rack.length) {
        const up = rack.filter(x => x > w + 1e-9);
        return up.length ? Math.min(...up) : null;
    }
    const step = loadStep(ex, unit) || 0;
    return step > 0 ? (Math.floor(w / step + 1e-9) + 1) * step : null;
}

function customExerciseHistory(program, day, id, history) {
    const targetIndex = (program?.days || []).findIndex(candidate => candidate?.id === day?.id);
    if (targetIndex < 0) return null;
    const entries = normalizeHistoryEntries(history, program.id).entries.filter(h => h?.perf?.[id])
        .slice().sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
    return entries.find(entry => resolveHistoryDayIndex(program.days, entry) === targetIndex) ?? null;
}

function customExerciseReferenceHistory(program, id, history) {
    // Prefer a canonical same-program revision before using cross-program history as the explicitly
    // low-confidence, reference-only starting load.
    const own = normalizeHistoryEntries(history, program.id).entries.filter(h => h?.perf?.[id])
        .slice().sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
    if (own.length)
        return own[0];
    const entries = (history || []).filter(h => validHistoryDate(h) && h?.perf?.[id]).slice().sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
    return entries[0] || null;
}

function representativeCustomLoad(perf) {
    const direct = historyNumber(perf?.weight);
    if (direct !== null && direct > 0)
        return direct;
    const loads = completedHistorySets(perf).map(set => historyNumber(set?.w)).filter(value => value !== null && value > 0);
    return loads.length ? Math.max(...loads) : null;
}

function customProgramSuggestion(program, day, slot, unit, weekIndex, history) {
    const ex = EX_BY_ID[day?.exercises?.[slot]];
    if (!ex)
        return null;
    const last = customExerciseHistory(program, day, ex.id, history);
    if (!last) {
        const reference = customExerciseReferenceHistory(program, ex.id, history);
        const rawLoad = representativeCustomLoad(reference?.perf?.[ex.id]);
        if (!reference || rawLoad === null)
            return null;
        const currentCell = computeCell(program, day, ex.id, slot, weekIndex);
        const [lo] = cellRepRange(currentCell, program, ex, slot === day?.primaryIndex);
        const weight = convertHistoryLoad(rawLoad, reference.unit, unit);
        return { weight, dir: 'hold', action: 'initial', reps: currentCell?.range ?? currentCell?.reps,
            target: lo, last: reference.perf[ex.id], confidence: 'low', referenceOnly: true,
            reason: 'Use the last known load only as a starting reference. This program day has no comparable completed history yet, so reps/load are not progressed from another day or program.' };
    }
    const conv = w => convertHistoryLoad(w, last.unit, unit);
    const currentCell = computeCell(program, day, ex.id, slot, weekIndex);
    const saved = last.perf[ex.id].prescription;
    const validSaved = saved?.schemaVersion === 1 && saved.exerciseId === ex.id
        && Number.isInteger(saved.sets) && saved.sets > 0 && saved.sets <= 20
        && effortBounds(saved.reps)?.[0] > 0 && effortBounds(saved.rir)?.[0] >= 0;
    const cell = validSaved ? { ...currentCell, ...saved, range: Array.isArray(saved.reps) ? saved.reps.join('-') : saved.reps,
        reps: Array.isArray(saved.reps) ? saved.reps.join('-') : saved.reps,
        rir: Array.isArray(saved.rir) ? saved.rir.join('-') : saved.rir } : currentCell;
    const [lo, hi] = cellRepRange(cell, program, ex, slot === day?.primaryIndex);
    const rir = effortBounds(cell.rir) || [2, 2];
    const work = (Array.isArray(last.perf[ex.id].sets) ? last.perf[ex.id].sets : [])
        .filter(x => x && !x.warm && !x.sub && x.done !== false && historyNumber(x.w) !== null && historyNumber(x.w) >= 0
            && historyNumber(x.r) > 0);
    if (!work.length) return null;
    const style = resolveStyle(program, ex, slot === day.primaryIndex, weekIndex, null, history, day.id);
    const rack = gymRackFor(ex, unit), step = loadStep(ex, unit);
    const loadingInventory = { unit, exerciseOverrides: { [ex.id]: rack?.length
        ? { availableLoads: rack } : { increment: step, minimum: 0 } } };
    const performed = work.map((x, i) => ({ exerciseId: ex.id, setIndex: i, load: conv(x.w), reps: Number(x.r),
        ...historyExposureContext(x), rir: observedHistoryRIR(x) }));
    const exercise = { exerciseId: ex.id, name: ex.name, role: cell.role, sets: Number(cell.sets),
        progressionStyle: style, prescription: { reps: [lo, hi], rir } };
    const exposure = progressionExposureContext([last.perf[ex.id], ...performed], last);
    const result = evaluateWorkoutProgression({ exercises: [exercise] }, performed, {
        loadingInventory, equipmentAvailable: program.config?.equipment || [],
        ...historyExposureContext(last), badDay: exposure.badDay, interrupted: exposure.interrupted,
        prescriptionEdited: exposure.nonComparable
    })[0];
    const weight = result.suggestedLoad ?? result.currentLoad;
    return { weight, dir: weight > result.currentLoad ? "up" : weight < result.currentLoad ? "down" : "hold",
        action: result.action, reps: cell.range, target: result.suggestedReps ?? lo,
        setTargets: result.setTargets, last: last.perf[ex.id], confidence: result.confidence, reason: result.reason };

}

function loadableAtOrBelow(ex, w, unit) {
    if (!ex || !(w > 0))
        return w;
    const rack = gymRackFor(ex, unit);
    if (rack && rack.length) {
        const fit = rack.filter(x => x <= w + 1e-9);
        return fit.length ? Math.max(...fit) : Math.min(...rack);
    }
    const step = loadStep(ex, unit) || 0;
    return step > 0 ? Math.max(step, Math.floor(w / step + 1e-9) * step) : w;
}

const EFFORT_MIN_PAIRS = 6;

const EFFORT_MAX_BIAS = 2.5;

const EFFORT_REP_CAP = 15;

function effortCalibration(history) {
    // CRITICAL: reference and observations must come from the SAME TIME WINDOW.
    // The first version built the reference from RECENT maximal sets but drew observations from the
    // WHOLE history. A set performed 14 weeks ago — when the lifter was genuinely weaker — was then
    // judged against today's stronger reference, which made it look as though they'd had far more in
    // reserve than they claimed. That manufactured a NEGATIVE bias in exact proportion to how much the
    // lifter had gained (measured: slow gainer 0.0, average −0.87, fast gainer −1.0, all of whom
    // report honestly), and the engine "corrected" for it by pushing their loads UP until they started
    // missing reps. Strength drift is not a reporting bias. Windowing both sides to the same recent
    // sessions holds strength approximately constant, which is the only condition under which the
    // comparison means anything.
    const WINDOW = 8; // recent sessions per lift on both sides of the comparison
    const hs = history || [];
    const bySession = {}; // exId -> [{sets, date}] newest-first
    for (const h of hs) {
        for (const [id, p] of Object.entries(h.perf || {})) {
            // setsOf, not p.sets: a corrupt backup can put nulls (or a string) in here, and this function is
            // now reached from muscleRecovery — so a malformed entry would take out the whole session view.
            const ss = completedHistorySets(p).map(x => ({ ...x, w: convertHistoryLoad(x.w, h.unit, "kg"), rir: observedHistoryRIR(x) }));
            if (!ss.length)
                continue;
            (bySession[id] = bySession[id] || []).push(ss);
        }
    }
    const obs = [];
    for (const [, sessions] of Object.entries(bySession)) {
        const win = sessions.slice(0, WINDOW); // newest-first → the most recent WINDOW sessions
        // pass 1: reference, from sets the lifter was ASKED to take to maximum, within the window
        const maximal = [];
        for (const sets of win) {
            for (const s of sets) {
                const w = parseFloat(s.w), r = parseInt(s.r);
                if (!(w > 0) || !(r > 0))
                    continue;
                // Only a set PRESCRIBED as maximal (AMRAP, or RIR 0 asked and reported) is a trustworthy 1RM
                // reference. A bare "0" tapped on a set nobody asked to be maximal is ambiguous, and feeding
                // it in as ground truth poisons the reference.
                if (!(s.amrap || (s.rir === 0 && s.tr != null && s.tr <= 0)))
                    continue;
                maximal.push(e1rmRIR(w, r, 0, EFFORT_REP_CAP));
            }
        }
        if (!maximal.length)
            continue;
        // A robust CENTRE, never a maximum: the max of a noisy sample is biased upward by construction —
        // it selects for the luckiest day — and that bias flows straight into every implied reserve.
        const sorted = maximal.slice().sort((a, b) => a - b);
        const m = Math.floor(sorted.length / 2);
        const ref = sorted.length % 2 ? sorted[m] : (sorted[m - 1] + sorted[m]) / 2;
        if (!(ref > 0))
            continue;
        // pass 2: ordinary stated-reserve sets from the SAME window, against that reference
        for (const sets of win) {
            for (const s of sets) {
                const w = parseFloat(s.w), r = parseInt(s.r);
                if (!(w > 0) || !(r > 0) || s.rir == null || s.amrap)
                    continue;
                if (s.rir === 0 && (s.tr == null || s.tr <= 0))
                    continue; // reference set / unknown intent
                if (w >= ref)
                    continue; // no reserve to infer at/above ref
                const implied = 30 * (ref / w - 1) - Math.min(r, EFFORT_REP_CAP);
                if (!isFinite(implied) || implied < -3 || implied > 8)
                    continue;
                obs.push(s.rir - implied);
            }
        }
    }
    if (obs.length < EFFORT_MIN_PAIRS)
        return { bias: 0, confidence: 0, n: obs.length };
    // median is the right centre here — one mis-tapped RIR shouldn't move the calibration
    const sorted = obs.slice().sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    const med = sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    const confidence = clamp((obs.length - EFFORT_MIN_PAIRS) / 18, 0, 1);
    // ramp the correction in with confidence, so it never lurches on the 6th observation
    const bias = clamp(med, -EFFORT_MAX_BIAS, EFFORT_MAX_BIAS) * confidence;
    return { bias: Math.round(bias * 100) / 100, confidence: Math.round(confidence * 100) / 100, n: obs.length };
}

function lastDayPerf(day, perf, history, program = null) {
    const out = {};
    const scopedHistory = program ? progressionHistoryForProgram(program, history) : (history || []);
    const targetIndex = program ? (program.days || []).findIndex(candidate => candidate?.id === day?.id) : -1;
    for (const id of day.exercises) {
        if (program?.custom === true && program?.engineSource !== "pursuit-next") {
            const comparable = customExerciseHistory(program, day, id, history);
            if (comparable?.perf?.[id])
                out[id] = comparable.perf[id];
            continue;
        }
        for (const h of scopedHistory) { // history is newest-first
            const sameDay = program ? resolveHistoryDayIndex(program.days, h) === targetIndex : h.dayId === day.id;
            if (sameDay && h.perf?.[id] && h.perf[id].reps != null) {
                out[id] = h.perf[id];
                break;
            }
        }
        if (!out[id] && perf?.[id])
            out[id] = perf[id]; // no comparable history for this day → caller-provided reference only
    }
    return out;
}

function anchorPerfFor(program, day, perf, history, weekIndex) {
    const best = dayPerfFor(day, perf, history, program);
    if (!day || !Array.isArray(day.exercises))
        return best;
    let last = null; // computed lazily — most days have no DDP lift
    const out = { ...best };
    day.exercises.forEach((id, slot) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        if (resolveStyle(program, ex, slot === day.primaryIndex, weekIndex, perf, history, day?.id) !== "dynamic")
            return;
        if (!last)
            last = lastDayPerf(day, perf, history, program);
        if (last[id])
            out[id] = last[id];
    });
    return out;
}

function prescribeSets(program, day, ex, slot, weekIndex, unit, sug, dayPerf, perf, history, withWarm) {
    void dayPerf;
    void perf;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    if (!cell || cell.missing || !(Number(cell.sets) > 0))
        return [];
    const request = program?.nextEngine?.request || program?.nextEngine?.baseRequest;
    const percentage = percentagePlanFor(program, day, ex, slot, weekIndex, unit, history);
    const targets = buildRuntimeSetTargets({
        exerciseId: resolveNextShellExerciseId(program, day, slot, ex) || ex.id,
        cell,
        workingLoad: percentage?.sets[0]?.weight ?? sug?.weight ?? null,
        suggestedReps: sug?.target ?? null,
        setTargets: sug?.setTargets,
        includeWarmups: !!withWarm,
        loadingInventory: request?.equipment?.loading,
        equipmentAvailable: request?.equipment?.available,
        snapLoad: value => loadableAtOrBelow(ex, value, unit)
    });
    const range = cellRepRange(cell, program, ex, slot === day.primaryIndex);
    const rangeText = range[0] === range[1] ? String(range[0]) : `${range[0]}-${range[1]}`;
    const rirText = cell.rir ?? null;
    let rows = targets.map(t => ({
        weight: t.weight == null ? "" : String(t.weight),
        reps: String(t.reps),
        warm: t.kind === "warmup",
        done: false,
        auto: true,
        valueOwner: "prescription",
        target: t.kind === "warmup"
            ? { w: t.weight == null ? "—" : String(t.weight), reps: String(t.reps), rir: null }
            : { w: t.weight == null ? "—" : String(t.weight), reps: rangeText, rir: rirText, nextAction: sug?.action || "initial", confidence: sug?.confidence || null, prefillReps: String(t.reps) }
    }));
    if (percentage) {
        rows = [...rows.filter(r => r.warm), ...percentage.sets.map(t => ({
            weight: String(t.weight), reps: String(t.reps).replace('+', ''), warm: false, done: false,
            auto: true, valueOwner: 'prescription',
            target: { w: String(t.weight), reps: String(t.reps).replace('+', ''),
                rir: t.amrap ? '0' : rirText, amrap: !!t.amrap,
                nextAction: 'percentage', confidence: null, prefillReps: String(t.reps).replace('+', '') }
        }))];
    }
    // Advanced-technique selection is engine-owned; this shell helper only realizes the engine's cue
    // as loggable rows. The protocol itself is now defined in next-engine/workout-runtime.ts.
    const protocol = techniqueProtocolFromCell(cell);
    if (protocol.type === 'myo_reps') {
        let lastWork = -1;
        for (let i = rows.length - 1; i >= 0; i--)
            if (!rows[i].warm) {
                lastWork = i;
                break;
            }
        if (lastWork >= 0) {
            const base = rows[lastWork];
            const mini = protocol.miniSets;
            for (let i = 0; i < (mini?.minimum || 0); i++)
                rows.push({
                    weight: base.weight, reps: String(mini?.targetReps || 5), warm: false, sub: true, myo: true,
                    kind: 'myo', prescribed: true, done: false, auto: true, valueOwner: "prescription",
                    target: { w: base.weight || '—', reps: String(mini?.targetReps || 5), rir: null, rest: mini?.restSeconds || 15 }
                });
        }
    }
    else if (protocol.type === 'drop_set') {
        let lastWork = -1;
        for (let i = rows.length - 1; i >= 0; i--)
            if (!rows[i].warm) {
                lastWork = i;
                break;
            }
        if (lastWork >= 0) {
            const base = Number(rows[lastWork].weight) || 0;
            for (const drop of protocol.drops || [])
                rows.push({
                    weight: base > 0 ? String(Math.round(base * drop.fraction * 100) / 100) : '', reps: '', warm: false,
                    sub: true, kind: 'drop', dropF: drop.fraction, prescribed: true, done: false, auto: true, valueOwner: "prescription",
                    target: { w: base > 0 ? String(Math.round(base * drop.fraction * 100) / 100) : '—', reps: 'to failure', rir: null, rest: drop.restSeconds }
                });
        }
    }
    return rows;
}

function loggedExercisePerformance(program, day, e, weekIndex, unit, perf, history) {
    const failedRows = program.config?.percentScheme === 'gzclp' ? e.sets.filter(x => x.done
        && !x.warm && !x.sub && parseInt(x.reps) === 0 && parseFloat(x.weight) > 0) : [];
    const s = summarizeSets(e.sets, EX_BY_ID[e.id], unit) || (failedRows.length
        ? { weight: Math.max(...failedRows.map(x => parseFloat(x.weight))), reps: 0 } : null);
    const note = e.note.trim() || (perf[e.id] && perf[e.id].note) || undefined;
    if (s) {
        const doneWork = e.sets.filter(x => x.done && !x.warm && (parseInt(x.reps) > 0 || (failedRows.includes(x))));
        const hasPos = doneWork.some(x => parseFloat(x.weight) > 0);
        const wv = x => { const w = parseFloat(x.weight); return isNaN(w) ? 0 : w; };
        // Persist the amrap flag from the prescription onto each logged set. Percent-scheme
        // progression (5/3/1 TM projection, GZCLP stage advancement) needs to identify the
        // AMRAP set directly rather than inferring it from being the unique top-weight set —
        // that heuristic silently breaks if a manually-edited weight ties with another set.
        // RIR: prefer the explicitly-logged actual RIR; otherwise fall back to the set's target
        // RIR so the LAST TIME column still shows the intended effort (most users just tick the
        // set done without tapping an RIR, which previously left the history with no effort at all).
        const setRIR = (x) => {
            if (x.actualRIR != null)
                return x.actualRIR;
            const t = x.target?.rir;
            const n = typeof t === "number" ? t : parseRIRNum(t);
            return Number.isFinite(n) ? n : null;
        };
        const logged = (hasPos ? doneWork.filter(x => parseFloat(x.weight) > 0) : doneWork)
            .map(x => {
            const r = setRIR(x);
            // Snapshot what was ASKED for alongside what was done: pw = prescribed weight, pt = the
            // prescribed rep target. Self-contained per set, so a logged session can be replayed and
            // scored against a different progression without needing the program that produced it.
            // Omitted for manual/freestyle sets, where nothing was prescribed.
            const pw = parseFloat(x.target?.w);
            const pt = x.target?.reps != null ? String(x.target.reps) : null;
            // PROVENANCE TRAVELS WITH THE SET. `sub` marks a drop set or myo mini — an extension of
            // the set above, not a working set. Every reader downstream already filters on it
            // (`!s.warm && !s.sub`), but the flag was never PERSISTED, so on replayed history those
            // filters matched nothing and extensions counted as full sets all over again — the exact
            // bug v493 fixed in the live session, surviving in the log.
            return { ...historyExposureContext(x), w: wv(x), r: parseInt(x.reps), ...(failedRows.includes(x) ? { failedAttempt: true } : {}), ...(r != null ? { rir: r, rirReported: x.actualRIR != null } : {}), ...(x.sub ? { sub: true, ...(x.kind ? { kind: x.kind } : {}) } : {}), ...(prescribedRIRof(x) != null ? { tr: prescribedRIRof(x) } : {}), ...(x.target?.amrap ? { amrap: true } : {}), ...(x.auto && pw > 0 ? { pw } : {}), ...(!x.target?.freestyle && pt ? { pt } : {}) };
        });
        const prescription = snapshotNextShellPrescription(program, day, e.slot, EX_BY_ID[e.id], weekIndex)
            || { schemaVersion: 1, exerciseId: e.id, ...computeCell(program, day, e.id, e.slot, weekIndex) };
        const percentage = percentagePlanFor(program, day, EX_BY_ID[e.id], e.slot, weekIndex, unit, history);
        if (percentage) prescription.protocol = { scheme: program.config.percentScheme,
            ...(percentage.tier ? { tier: percentage.tier, stage: percentage.stage } : {}) };
        if (percentage) prescription.setTargets = percentage.sets.map(t => ({
            reps: Number.parseInt(t.reps), weight: t.weight, unit, amrap: !!t.amrap,
            rir: t.amrap ? 0 : parseRIRNum(prescription.rir)
        }));
        return { ...historyExposureContext(e), weight: s.weight, reps: s.reps, date: Date.now(), sets: logged, note,
            ...(prescription ? { prescription } : {}) };
    }
    return null;
}

function loggedWorkoutPerformance(program, day, data, weekIndex, unit, perf, history) {
    return Object.fromEntries(data.map(e => [e.id, loggedExercisePerformance(program, day, e, weekIndex, unit, perf, history)])
        .filter(([, value]) => value !== null));
}

function setsOf(p) {
    if (!p)
        return [];
    if (Array.isArray(p.sets) && p.sets.length)
        return p.sets.filter(x => x && typeof x === "object");
    return p.weight != null ? [{ w: p.weight, r: p.reps }] : [];
}

const ASSUMED_RIR = 2;

const toUnit = (v, from, to) => from === to ? v : (to === "lb" ? v * 2.2046226 : v / 2.2046226);
function setGymLimits(gym) {
    GYM_LIMITS = gym ? { limits: gym.limits || {}, inventory: gym.inventory || {}, unit: gym.limitUnit || gym.unit || "kg" } : null;
}

export { setGymLimits, ASSUMED_RIR, BASE_SET_COST, E1RM_HOLD, EFFORT_MAX_BIAS, EFFORT_MIN_PAIRS, EFFORT_REP_CAP, GYM_LIMITS, GZ_T1_STAGES, GZ_T2_STAGES, INVENTORY_KEYS, PART_LABEL, STALL_ENGAGE, STALL_WINDOW, __recovFitMemo, __recoveryMemo, anchorPerfFor, autoStyleDetail, autoStyleFor, customExerciseHistory, customExerciseReferenceHistory, customProgramSuggestion, dayPerfFor, effortCalibration, engineWeekOneStyles, gymRackFor, gzPlanFor, gzTierOf, gzWorkWeight, lastDayPerf, linearStalled, loadableAbove, loadableAtOrBelow, loggedExercisePerformance, loggedWorkoutPerformance, muscleRecovery, muscleRecoveryUncached, nextEngineStyleFor, parseRIRNum, pctSetsFor, percentagePlanFor, personalRecoveryHours, plateauSessions, prescribeSets, prescribedRIRof, progressionHistoryForProgram, representativeCustomLoad, resolveStyle, sessionSuggestion, setsOf, stallCountFor, styleFor, styleOverride, summarizeSets, tierOf, toUnit, trainingMaxForUnit, withTrainingMax };
