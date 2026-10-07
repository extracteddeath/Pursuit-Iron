import { attemptedHistorySets, convertHistoryLoad, progressionExposureContext, normalizeHistoryEntries } from './history-contract.js';

export const PCT_SCHEMES = {
    "531": {
        name: "Main-Lift Waves",
        basis: "Training Max (90% of 1RM)",
        weeks: [
            { label: "5s", sets: [[0.65, "5"], [0.75, "5"], [0.85, "5+"]] },
            { label: "3s", sets: [[0.70, "3"], [0.80, "3"], [0.90, "3+"]] },
            { label: "1s", sets: [[0.75, "5"], [0.85, "3"], [0.95, "1+"]] },
            { label: "Deload", sets: [[0.40, "5"], [0.50, "5"], [0.60, "5"]] },
        ],
        deloadWeek: 3
    },
    // 5/3/1 for Beginners (Wendler): TWO main lifts per session, run as 5's PRO — the main work is
    // straight 5×3 (no AMRAP on the mains) across the 5s/3s/1s waves — followed by First-Set-Last
    // (FSL) supplemental volume of 5×5 at the week's first-set percentage. T2 = the FSL lift, which
    // is the OTHER main lift of the pair, so each lift gets heavy 5×3 one slot and 5×5 volume the next.
    "531beg": {
        name: "Main-Lift Waves · Novice",
        basis: "Training Max (90% of 1RM)",
        fivesPro: true,
        weeks: [
            { label: "5s · 5's PRO", sets: [[0.65, "5"], [0.75, "5"], [0.85, "5"]] },
            { label: "3s · 5's PRO", sets: [[0.70, "5"], [0.80, "5"], [0.90, "5"]] },
            { label: "1s · 5's PRO", sets: [[0.75, "5"], [0.85, "5"], [0.95, "5"]] },
            { label: "Deload", sets: [[0.40, "5"], [0.50, "5"], [0.60, "5"]] },
        ],
        deloadWeek: 3,
        t2: { label: "FSL 5×5", sets: [[0.65, "5"], [0.65, "5"], [0.65, "5"], [0.65, "5"], [0.65, "5"]] }
    },
    madcow: {
        name: "Ramping 5×5",
        basis: "top 5×5 weight",
        // ramp to a top set of 5; week-over-week the top rises ~2.5% (handled via TM growth note)
        weeks: [
            { label: "Ramp 5×5", sets: [[0.50, "5"], [0.625, "5"], [0.75, "5"], [0.875, "5"], [1.0, "5"]] },
        ]
    },
    nsuns: {
        name: "High-Volume Wave LP",
        basis: "Training Max (90% of 1RM)",
        // T1 nine-set ramp (Scheme B): up to a 95% AMRAP single, then descending back-off sets.
        // The top AMRAP drives a weekly training-max bump — pure linear progression, no monthly waves.
        weeks: [
            { label: "T1 · 9 sets", sets: [
                    [0.75, "5"], [0.85, "3"], [0.95, "1+"], [0.90, "3"], [0.85, "3"],
                    [0.80, "5"], [0.75, "5"], [0.70, "5"], [0.65, "5+"],
                ] },
        ]
    },
    // Texas Method: the squat's load is set per DAY — heavy 5×5 volume (≈90% of the 5RM),
    // a light recovery day (≈80% × 2×5), and an intensity day building to a new top 5RM.
    // The 5RM (the basis) creeps up week to week — that weekly bump is the progression.
    texas: {
        name: "Texas Method",
        basis: "top 5RM",
        linearPerWeek: 0.015,
        days: {
            tx_volume: { label: "Volume 5×5", sets: [[0.90, "5"], [0.90, "5"], [0.90, "5"], [0.90, "5"], [0.90, "5"]] },
            tx_recovery: { label: "Recovery 2×5", sets: [[0.80, "5"], [0.80, "5"]] },
            tx_intensity: { label: "Intensity 1×5", sets: [[1.00, "5+"]] }
        },
        weeks: [{ label: "Texas", sets: [[0.90, "5"]] }], // fallback if day unknown
    },
    // GZCLP: T1 main lift = 5 sets of 3, last set AMRAP (5×3+), at one working weight; add load
    // every session you clear the AMRAP. (When you stall the stage cascades 5×3 → 6×2 → 10×1.)
    gzclp: {
        /* ⚠ THE PROGRAM PRESCRIBES ITS ACCESSORY WORK TOO, and until now only T1 and T2 were expressed
           here -- so every remaining slot on a GZCL day was filled by the generic coverage floors, and
           the time model (which assumed it owned every set count) got a session it had not budgeted for.
           GZCL's T3 is ONE TO THREE movements at 3x15+, last set AMRAP, and the source states the count
           three times with the same qualifier: "choose one to two movements, SOMETIMES THREE IF TIME AND
           ENERGY PERMITS". The range is conditioned on TIME, which is exactly what the session bracket
           already expresses -- so min/max here and the bracket picks within it, rather than a fixed count
           that is too many at sixty minutes and too few at a hundred and twenty. And the choice is
           not open: the Method article has a section headed "But where is the Back Work? (And Biceps
           Too!)" because T1 and T2 are the four barbell lifts and contain no pulling at all. Cody's
           answer is supersets in the Method; GZCLP puts it in T3, which is conventionally a lat pulldown
           and a row. So `categories` is pull-weighted by the program's own design, not by our guess.
           An earlier reading of this as "three movements, one push one pull one legs" was measured and
           cost the named bucket rearUnderMEV 348 -> 597 -- the pulling the program deliberately puts
           here was being spent on pressing and legs the T1/T2 pins already cover.
           `categories` deliberately does NOT name exercises. The program specifies the SHAPE -- how many
           accessories and of what kind -- and the app still chooses which row, which single-leg movement,
           from what the lifter's equipment allows and which muscles the T1/T2 pins left short. That is
           the division that was missing: the program owns how much accessory work it prescribes, the
           generator owns which movements fill it. */
        t3: { min: 1, max: 3, sets: 3, reps: "15+", pct: 0.65, categories: ["pull", "pull", "legs"] },
        name: "Tiered Linear Progression",
        basis: "T1 5×3 working weight",
        // No linearPerWeek here — GZCLP progresses per-SESSION via gzAdvance's stage cascade
        // (gzPlanFor / gzWorkWeight), not a weekly percentage creep. A linearPerWeek value would
        // double-progress the lift on top of the session-based AMRAP advancement.
        cascadeNote: "On a failed AMRAP, drop to 6×2, then 10×1, then reset +5 lb on a new 5×3.",
        weeks: [{ label: "T1 · 5×3+", sets: [[1.0, "3"], [1.0, "3"], [1.0, "3"], [1.0, "3"], [1.0, "3+"]] }]
    },
    // German Volume Training: 10 sets of 10 on the day's main lift at ~60% of 1RM. Brutal volume;
    // add ~2.5–5% once all 100 reps are clean.
    gvt: {
        name: "German Volume Training",
        basis: "60% of 1RM (enter your 1RM)",
        linearPerWeek: 0.02,
        weeks: [{ label: "10×10", sets: Array.from({ length: 10 }, () => [0.60, "10"]) }]
    },
    // The Rippler (GZCL): T1 rides a 3-week microcycle (volume → base → intensity) that repeats a
    // touch heavier each pass, building toward a peak. T2 is a tiered secondary lift.
    rippler: {
        /* ⚠ THE PROGRAM PRESCRIBES ITS ACCESSORY WORK TOO, and until now only T1 and T2 were expressed
           here -- so every remaining slot on a GZCL day was filled by the generic coverage floors, and
           the time model (which assumed it owned every set count) got a session it had not budgeted for.
           GZCL's T3 is ONE TO THREE movements at 3x15+, last set AMRAP, and the source states the count
           three times with the same qualifier: "choose one to two movements, SOMETIMES THREE IF TIME AND
           ENERGY PERMITS". The range is conditioned on TIME, which is exactly what the session bracket
           already expresses -- so min/max here and the bracket picks within it, rather than a fixed count
           that is too many at sixty minutes and too few at a hundred and twenty. And the choice is
           not open: the Method article has a section headed "But where is the Back Work? (And Biceps
           Too!)" because T1 and T2 are the four barbell lifts and contain no pulling at all. Cody's
           answer is supersets in the Method; GZCLP puts it in T3, which is conventionally a lat pulldown
           and a row. So `categories` is pull-weighted by the program's own design, not by our guess.
           An earlier reading of this as "three movements, one push one pull one legs" was measured and
           cost the named bucket rearUnderMEV 348 -> 597 -- the pulling the program deliberately puts
           here was being spent on pressing and legs the T1/T2 pins already cover.
           `categories` deliberately does NOT name exercises. The program specifies the SHAPE -- how many
           accessories and of what kind -- and the app still chooses which row, which single-leg movement,
           from what the lifter's equipment allows and which muscles the T1/T2 pins left short. That is
           the division that was missing: the program owns how much accessory work it prescribes, the
           generator owns which movements fill it. */
        t3: { min: 1, max: 3, sets: 3, reps: "15+", pct: 0.65, categories: ["pull", "pull", "legs"] },
        name: "Tiered Wave",
        basis: "Training Max (90% of 1RM)",
        wave: true,
        linearPerWeek: 0.005,
        weeks: [
            { label: "Volume", sets: [[0.70, "5"], [0.75, "5"], [0.80, "5"], [0.75, "5"], [0.70, "5+"]] },
            { label: "Base", sets: [[0.725, "4"], [0.80, "3"], [0.85, "2"], [0.80, "3"], [0.75, "4+"]] },
            { label: "Intensity", sets: [[0.75, "3"], [0.825, "2"], [0.90, "1"], [0.825, "2"], [0.775, "3+"]] },
        ],
        t2: { label: "T2 volume", sets: [[0.65, "8"], [0.70, "6"], [0.65, "8+"]] }
    },
    // Jacked & Tan 2.0 (GZCL): T1 ramps to a heavy max-rep set (the MRS, last single is AMRAP) then
    // back-off volume; T2 is higher-rep accessory volume. The MRS drives the weekly load creep.
    jt: {
        /* ⚠ THE PROGRAM PRESCRIBES ITS ACCESSORY WORK TOO, and until now only T1 and T2 were expressed
           here -- so every remaining slot on a GZCL day was filled by the generic coverage floors, and
           the time model (which assumed it owned every set count) got a session it had not budgeted for.
           GZCL's T3 is ONE TO THREE movements at 3x15+, last set AMRAP, and the source states the count
           three times with the same qualifier: "choose one to two movements, SOMETIMES THREE IF TIME AND
           ENERGY PERMITS". The range is conditioned on TIME, which is exactly what the session bracket
           already expresses -- so min/max here and the bracket picks within it, rather than a fixed count
           that is too many at sixty minutes and too few at a hundred and twenty. And the choice is
           not open: the Method article has a section headed "But where is the Back Work? (And Biceps
           Too!)" because T1 and T2 are the four barbell lifts and contain no pulling at all. Cody's
           answer is supersets in the Method; GZCLP puts it in T3, which is conventionally a lat pulldown
           and a row. So `categories` is pull-weighted by the program's own design, not by our guess.
           An earlier reading of this as "three movements, one push one pull one legs" was measured and
           cost the named bucket rearUnderMEV 348 -> 597 -- the pulling the program deliberately puts
           here was being spent on pressing and legs the T1/T2 pins already cover.
           `categories` deliberately does NOT name exercises. The program specifies the SHAPE -- how many
           accessories and of what kind -- and the app still chooses which row, which single-leg movement,
           from what the lifter's equipment allows and which muscles the T1/T2 pins left short. That is
           the division that was missing: the program owns how much accessory work it prescribes, the
           generator owns which movements fill it. */
        t3: { min: 1, max: 3, sets: 3, reps: "15+", pct: 0.65, categories: ["pull", "pull", "legs"] },
        name: "Tiered Powerbuilding",
        basis: "Training Max (90% of 1RM)",
        linearPerWeek: 0.015,
        weeks: [{ label: "T1 ramp + MRS", sets: [[0.70, "5"], [0.80, "3"], [0.875, "2"], [0.925, "1+"], [0.825, "3"], [0.75, "5+"]] }],
        t2: { label: "T2 volume", sets: [[0.65, "10"], [0.70, "8"], [0.70, "6+"]] }
    }
};
// returns [{ pct, reps, weight, amrap }] for the main lift this week, or null
export function percentageProtocolFor({ scheme, tm, weekIndex, weeksTotal, dayType, tier = "t1", snapLoad } = {}) {
    const S = PCT_SCHEMES[scheme];
    const snap = typeof snapLoad === 'function' ? snapLoad : value => value;
    if (!S || !Number.isFinite(tm) || !(tm > 0) || !Number.isInteger(weekIndex) || weekIndex < 1
        || !Number.isInteger(weeksTotal) || weeksTotal < 1)
        return null;
    let wk;
    if (scheme === "531" || scheme === "531beg") {
        // 5/3/1 block design: weeksTotal=4 means weeks 1-3 are the wave (5s/3s/5·3·1) and week 4
        // is baked-in as the deload (cyc index 3) — that's the (weekIndex-1)%4 indexing below.
        // weekIndex > weeksTotal happens only when the UI's separate "Deload" pill is tapped
        // AFTER the block (a second, explicit deload beyond the one inside the 4-week cycle) —
        // that always shows cyc[3] regardless of how far past weeksTotal it is, so it never
        // wraps back into a fresh wave on repeated taps.
        const cyc = S.weeks;
        if (tier === "t2" && S.t2 && weekIndex <= weeksTotal) {
            // FSL: 5×5 at THIS week's first working-set percentage (0.65 → 0.70 → 0.75)
            const first = cyc[(weekIndex - 1) % cyc.length].sets[0][0];
            wk = { label: S.t2.label, sets: S.t2.sets.map(([, r]) => [first, r]) };
        }
        else {
            wk = cyc[(weekIndex - 1) % cyc.length];
            if (weekIndex > weeksTotal)
                wk = cyc[3]; // explicit deload week beyond the block
        }
    }
    else if (tier === "t2" && S.t2) {
        wk = S.t2; // tiered T2 loading (GZCL Rippler / J&T)
    }
    else if (S.days && dayType && S.days[dayType]) {
        wk = S.days[dayType]; // per-day loading (Texas: volume / recovery / intensity)
    }
    else if (S.wave && S.weeks.length > 1) {
        wk = S.weeks[(weekIndex - 1) % S.weeks.length]; // repeating microcycle (The Rippler)
    }
    else {
        wk = S.weeks[0];
    }
    // A forced deload week (weekIndex beyond the block) must actually be LIGHT. 5/3/1 handles its own
    // deload above (cyc[3] is genuinely submaximal), but the linear-creep schemes (Madcow, Texas, GVT,
    // Rippler, J&T) would otherwise apply their HIGHEST weekly multiplier here, and single-week schemes
    // (nSuns) would repeat full load — turning the "deload" into the heaviest session of the block. So:
    // freeze the creep, thin the sets to ~half, drop the AMRAP, and scale the load to ~60%.
    const isDeloadWk = weekIndex > weeksTotal;
    const selfDeloads = scheme === "531" || scheme === "531beg"; // already returned its own deload table
    const genericDeload = isDeloadWk && !selfDeloads;
    let mult = 1, suffix = "";
    const per = scheme === "madcow" ? 0.025 : (S.linearPerWeek || 0);
    if (per && weekIndex > 1 && !isDeloadWk) { // no linear creep on a deload week
        mult = 1 + per * (weekIndex - 1);
        const bump = Math.round((mult - 1) * 100);
        if (bump >= 1)
            suffix = ` · wk ${weekIndex} (+${bump}%)`;
    }
    const DELOAD_SCALE = 0.6;
    const srcSets = genericDeload ? wk.sets.slice(0, Math.max(2, Math.ceil(wk.sets.length / 2))) : wk.sets;
    return {
        label: genericDeload ? "Deload · light" : wk.label + suffix,
        sets: srcSets.map(([pct, reps]) => ({
            pct,
            reps: genericDeload ? String(reps).replace("+", "") : reps, // no max-rep set on a deload
            amrap: genericDeload ? false : String(reps).includes("+"),
            weight: snap(tm * pct * mult * (genericDeload ? DELOAD_SCALE : 1))
        }))
    };
}

// Realize protocol intent within an accepted budget. Preserve the heaviest set and final rep-out;
// peaks can be inside a wave (nSuns), so endpoint sampling alone is insufficient.
export function adaptPercentageSetBudget(plan, count) {
    if (!plan?.sets?.length || !Number.isInteger(count) || count < 1 || count > 20) return null;
    const src = plan.sets;
    let sets;
    if (count === src.length) sets = src.map(t => ({ ...t }));
    else if (count < src.length) {
        const peak = src.reduce((best, t, i) => t.weight >= src[best].weight ? i : best, 0);
        const priorities = [peak, src.length - 1, 0,
            ...src.map((t, i) => t.amrap ? i : -1).filter(i => i >= 0),
            ...Array.from({ length: count }, (_, i) => Math.round(i * (src.length - 1) / Math.max(1, count - 1))),
            ...src.map((_, i) => i)];
        const indexes = [...new Set(priorities)].slice(0, count).sort((a, b) => a - b);
        sets = indexes.map(i => ({ ...src[i] }));
    }
    else sets = [...Array.from({ length: count - src.length }, () => ({ ...src[0], amrap: false,
        reps: String(src[0].reps).replace('+', '') })), ...src.map(t => ({ ...t }))];
    return { ...plan, sets, protocolSets: src.length, adapted: count !== src.length,
        label: plan.label + (count !== src.length ? ` · adapted to ${count} sets` : '') };
}

// Replay saved targets, rather than mutating a second progression state at workout finish.
// Legacy logs without a protocol snapshot cannot safely reconstruct a tier/stage.
export function deriveTieredLinearState({ programId, exerciseId, tier, initialStage = 0,
    initialLoad, entries = [], unit, nextLoad, resetLoad } = {}) {
    const stage0 = Number.isInteger(initialStage) && initialStage >= 0 && initialStage <= 2 ? initialStage : 0;
    const load0 = Number(initialLoad);
    let state = { stage: stage0, weight: Number.isFinite(load0) && load0 > 0 ? load0 : null };
    const increase = typeof nextLoad === 'function' ? nextLoad : value => value;
    const reset = typeof resetLoad === 'function' ? resetLoad : value => value;
    for (const entry of normalizeHistoryEntries(entries, programId).entries) {
        const perf = entry.perf?.[exerciseId], saved = perf?.prescription, protocol = saved?.protocol;
        if (protocol?.scheme !== 'gzclp' || protocol.tier !== tier
            || !Number.isInteger(protocol.stage) || protocol.stage < 0 || protocol.stage > 2) continue;
        const sets = attemptedHistorySets(perf);
        const context = progressionExposureContext([perf, ...sets], entry);
        if (context.nonComparable || context.interrupted || context.badDay) continue;
        if (!Array.isArray(saved.setTargets) || saved.setTargets.length !== saved.sets
            || sets.length !== saved.sets || !sets.length) continue;
        const loads = sets.map(s => convertHistoryLoad(s.w, entry.unit, unit));
        if (loads.some(w => !(w > 0)) || loads.some(w => Math.abs(w - loads[0]) > .01)) continue;
        if (saved.setTargets.some((t, i) => !(Number(t.reps) > 0)
            || Math.abs(convertHistoryLoad(t.weight, t.unit || entry.unit, unit) - loads[i]) > .01)) continue;
        const passed = sets.every((s, i) => Number(s.r) >= Number(saved.setTargets[i].reps));
        const stage = protocol.stage, weight = loads[0];
        if (passed) state = { stage, weight: increase(weight) ?? weight };
        else if (stage < 2) state = { stage: stage + 1, weight };
        else state = { stage: 0, weight: reset(weight) ?? weight };
    }
    return state;
}
