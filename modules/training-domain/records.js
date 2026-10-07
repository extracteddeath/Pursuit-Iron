// Canonical records domain. Maintained production source; independent of React and browser APIs.
import { programWorkingWeeks, cycleBlockMetadata } from "../program-duration.js";
import { setShellEquipmentExpander, splitContractGaps, splitBuildability, refusalFixes, generateNextProgramForShell, recommendNextSplitForShell, getNextShellCell, canonicalShellSetCount, cloneNextDayPrescriptions, swapNextSlotPrescriptions, removeNextSlotPrescription, nextExerciseIdForShellExercise, resolveNextShellExerciseId, remapNextShellRoster, snapshotNextShellPrescription, markUserPrescriptionOverride, clearUserPrescriptionOverride, NextShellAdapterError } from "../next-engine/app-shell-adapter.js";
import { buildRuntimeSetTargets, customProgramProgressionStyle, refreshPendingSetTargets, reconcilePendingRepTargets, techniqueProtocolFromCell, freestyleCellForRepRange, buildUserAddedSlotPrescriptions } from "../next-engine/workout-runtime.js";
import { captureShellVolumeSnapshot, auditShellVolume, repairShellVolume, shellVolumeTargets, shellDayMuscleBreakdown } from "../next-engine/volume-repair.js";
import { emptyRetiredTrialData, preserveRetiredTrialData, emptyRetiredRolloutData, preserveRetiredRolloutData } from "../legacy-research-data.js";
import { ALL_EQUIP_IDS, EQUIPMENT, EX_BY_ID } from './catalog.js';

const MACHINE_EQUIP = new Set(EQUIPMENT.filter(e => e.cat === "Machines").map(e => e.id));

const isMachineLike = (eq) => (eq || []).some(q => MACHINE_EQUIP.has(q));

const BAR_EQUIP = new Set(["barbell", "trapbar", "safetybar", "landmine"]);

const isBarLike = (eq) => (eq || []).some(q => BAR_EQUIP.has(q));

const GYM_PRESETS = [
    { key: "commercial", name: "Commercial gym", equipment: [...ALL_EQUIP_IDS] },
    { key: "garage", name: "Garage gym", equipment: ["barbell", "dumbbell", "bench", "ezbar", "pullup", "bands", "preacher"] },
    { key: "home", name: "Home gym", equipment: ["dumbbell", "bench", "bands", "pullup"] },
    { key: "minimal", name: "Minimal kit", equipment: ["bands", "pullup"] },
    { key: "bodyweight", name: "Bodyweight only", equipment: [] },
];

const TOTAL_WEEKS = 4;

const weeksOf = program => programWorkingWeeks(program, TOTAL_WEEKS);

function blockPhase(program, weekIndex, singleWeekVal = 0.5) {
    const weeks = weeksOf(program);
    let phase = weeks <= 1 ? singleWeekVal : clamp((weekIndex - 1) / Math.max(1, weeks - 1), 0, 1);
    const pw = program?.config?.phaseWindow;
    if (!Number.isFinite(phase))
        phase = 0;
    if (Array.isArray(pw) && pw.length === 2 && pw.every(Number.isFinite) && pw[0] >= 0 && pw[1] <= 1 && pw[0] <= pw[1])
        phase = pw[0] + phase * (pw[1] - pw[0]);
    return phase;
}

const goalForDay = (program, day) => (day && day.focus) || program.config.goal;

const clamp = (n, lo, hi) => Math.max(lo, Math.min(hi, n));

const MRV_GRAIN = 0.5;

function normalizeCycleLinks(saved, cycles) {
    if (!Array.isArray(saved) || !saved.length)
        return Array.isArray(saved) ? saved : [];
    const live = new Set((Array.isArray(cycles) ? cycles : []).map(c => c && c.id).filter(Boolean));
    let touched = false;
    const out = saved.map(p => {
        if (!p || !p.cycleId || live.has(p.cycleId))
            return p;
        touched = true;
        const { cycleId, cycleIndex, blockLabel, ...rest } = p;
        return rest;
    });
    return touched ? out : saved;
}

function normalizeHistoryDayIds(history, saved) {
    if (!Array.isArray(history) || !history.length || !Array.isArray(saved) || !saved.length)
        return history || [];
    const byProgram = new Map(saved.filter(p => p && p.id).map(p => [p.id, p]));
    let changed = false;
    const out = history.map(h => {
        if (!h || !h.dayId || !h.dayLabel)
            return h;
        const prog = byProgram.get(h.programId);
        if (!prog || !Array.isArray(prog.days))
            return h;
        if (prog.days.some(d => d.id === h.dayId))
            return h; // already resolves
        const matches = prog.days.filter(d => d.label === h.dayLabel);
        if (matches.length !== 1)
            return h; // unresolvable or ambiguous
        changed = true;
        return { ...h, dayId: matches[0].id };
    });
    return changed ? out : history;
}

const LEGACY_EQUIP_IMPLIES = {
    machine: ["legpress", "hacksquat", "legext", "legcurl", "pecdeck", "machinerow", "assisted",
        "calfmachine", "abduction", "reversehyper", "ghd",
        "machinepress", "machineshoulder", "machinelatraise", "machinecurl", "machineext", "beltsquat", "hipthrustmachine", "kickback", "machinecrunch", "machineshrug", "machinepullover", "adduction"],
    barbell: ["trapbar", "safetybar", "landmine"],
    /* A code minted before this split said only "bench", and every incline, decline and preacher
       movement was available to it. Granting the three keeps those layouts identical. This is about
       reproducing history, not about what owning a bench implies in real life — the gym editor lets
       anyone untick what they do not have. */
    bench: ["inclinebench", "declinebench", "preacher"]
};

function capWeeklyVolume(program) {
    if (program.config.percentScheme || program.config.progression === "manual")
        return;
    const peak = weeksOf(program); // last hard week carries the most volume
    const bias = { ...(program.slotBias || {}) };
    for (let pass = 0; pass < 40; pass++) {
        // current projected volume per muscle at peak week
        const vol = {};
        program.days.forEach(day => day.exercises.forEach((id, slot) => {
            const ex = EX_BY_ID[id];
            if (!ex)
                return;
            const sets = Number(computeCell({ ...program, slotBias: bias }, day, id, slot, peak).sets) || 0;
            vol[ex.part] = (vol[ex.part] || 0) + sets;
            secondaryOf(ex, program.engineV).forEach(([pp, f]) => { vol[pp] = (vol[pp] || 0) + sets * f; });
        }));
        // worst over-MRV muscle (composite parts like shoulders use the summed per-head ceiling,
        // so the front delt's large indirect pressing volume doesn't trigger phantom trimming)
        let worst = null, worstOver = 0;
        for (const part in vol) {
            const over = vol[part] - compositeMrv(part);
            if (over > worstOver) {
                worstOver = over;
                worst = part;
            }
        }
        if (!worst || worstOver < MRV_GRAIN)
            break; // MRV_GRAIN, not a literal — volumeZone reads the same constant
        // trim one set from the lowest-priority slot training `worst` directly (isolation/accessory,
        // highest slot index), keeping a floor of 2 working sets.
        let target = null, targetRank = -Infinity;
        program.days.forEach(day => day.exercises.forEach((id, slot) => {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== worst)
                return;
            const key = `${day.id}:${slot}`;
            const cur = Number(computeCell({ ...program, slotBias: bias }, day, id, slot, peak).sets) || 0;
            if (cur <= 2)
                return; // never below 2 working sets
            const rank = (ex.type === "isolation" ? 100 : 0) + slot; // prefer trimming accessories & later slots
            if (rank > targetRank) {
                targetRank = rank;
                target = key;
            }
        }));
        if (!target)
            break;
        bias[target] = (bias[target] || 0) - 1;
    }
    if (Object.keys(bias).length)
        program.slotBias = bias;
}

function cellRepRange(cell, program, ex, isPrimary) {
    void program;
    void ex;
    void isPrimary;
    const parse = (value) => {
        if (Array.isArray(value) && value.length >= 2) {
            const a = Number(value[0]), b = Number(value[1]);
            if (a > 0 && b >= a)
                return [a, b];
        }
        const raw = String(value ?? "").trim();
        if (!raw)
            return null;
        const nums = raw.split(/[-–]/).map(Number).filter(Number.isFinite);
        if (nums.length >= 2 && nums[0] > 0 && nums[1] >= nums[0])
            return [nums[0], nums[1]];
        if (nums.length === 1 && nums[0] > 0)
            return [nums[0], nums[0]];
        return null;
    };
    /* M46: the engine-owned cell is the only source of planned rep targets. A missing range does not
       fall back to the removed v661 rep-range policy. The neutral 8–12 default is used only for
       freestyle/unplanned logging surfaces that have no engine prescription. */
    return parse(cell?.range) || parse(cell?.reps) || [8, 12];
}

function baseSetsFor(config, ex, isPrimary) {
    const comp = ex.type === "compound";
    let s;
    if (config.goal === "strength")
        s = isPrimary ? 5 : comp ? 4 : 2;
    else if (config.goal === "hypertrophy")
        s = isPrimary ? 4 : comp ? 3 : 3;
    else /* both */
        s = isPrimary ? 4 : comp ? 3 : 3;
    if (config.experience === "none")
        s = Math.max(2, s - 1);
    else if (config.experience === "beginner")
        s = isPrimary ? Math.max(3, s - 1) : s;
    else if (config.experience === "advanced")
        s += isPrimary ? 1 : 0;
    return clamp(s, 2, 5);
}

const STRETCH_PARTIAL_PARTS = new Set(["chest", "biceps", "triceps", "lats", "quads", "hamstrings", "glutes", "calves", "abs"]);

const MYO_PARTS = new Set(["shoulders", "traps", "forearms"]);

function lastSetTech(ex, strength, isFocus, p, weeks) {
    if (strength || ex.type !== "isolation" || weeks < 3)
        return null;
    if (!(p >= (isFocus ? 0.5 : 0.67)))
        return null;
    if (ex.part === "calves")
        return "Last set: static stretch — hold a loaded stretch ~30s after your final rep";
    if (STRETCH_PARTIAL_PARTS.has(ex.part))
        return "Last set: lengthened partials — extra reps in the stretched position past failure";
    if (MYO_PARTS.has(ex.part))
        return "Last set: myo-reps — to failure, then mini-sets of a few reps with brief rests";
    return null;
}

function customAuthoredSetCount(raw, fallback, weekIndex = 1) {
    const base = canonicalShellSetCount(fallback) ?? 3;
    if (!Array.isArray(raw))
        return canonicalShellSetCount(raw, base) ?? base;
    const values = raw.map(v => canonicalShellSetCount(v)).filter(v => v != null);
    if (!values.length)
        return base;
    const w = Number(weekIndex);
    const idx = Number.isFinite(w) ? Math.max(0, Math.floor(w) - 1) : 0;
    return values[Math.min(idx, values.length - 1)] ?? base;
}

function legacyCustomSetCount(program, day, ex, slotIndex, weekIndex, rawSets, neutralFallback = 3) {
    const slotMap = program?.slotBias || {};
    const autoMap = program?.autoBias || {};
    const hasLegacyDose = Object.keys(slotMap).length > 0 || Object.keys(autoMap).length > 0;
    if (!hasLegacyDose)
        return customAuthoredSetCount(rawSets, neutralFallback, weekIndex);
    const key = `${day?.id}:${slotIndex}`;
    const roleBase = baseSetsFor(program?.config || {}, ex, slotIndex === day?.primaryIndex);
    const authoredBase = rawSets == null ? roleBase : customAuthoredSetCount(rawSets, roleBase, weekIndex);
    const finiteDelta = v => Number.isFinite(Number(v)) ? Number(v) : 0;
    return clamp(Math.round(authoredBase + finiteDelta(slotMap[key]) + finiteDelta(autoMap[key])), 1, 5);
}

function customLastSetTechnique(program, day, ex, slotIndex, weekIndex) {
    const o = program?.overrides?.[`${day?.id}:${slotIndex}`] || {};
    if (Object.prototype.hasOwnProperty.call(o, 'techOverride'))
        return o.techOverride || null;
    if (Object.prototype.hasOwnProperty.call(o, 'tech'))
        return o.tech || null;
    /* M203 — the custom flag is the durable identity. Older migrations/imports can legitimately
       lose engineV/slotBias/autoBias while still preserving `custom: true`, which made every
       implicit last-set amplifier disappear even though the program itself remained runnable.
       This helper is only called from the custom-program branch of computeCell, so do not gate the
       authored program's technique schedule on optional legacy metadata. Explicit technique edits
       above still win, including an explicit null/empty value meaning "off". */
    const authoredCustom = program?.custom === true;
    const weeks = weeksOf(program);
    if (!authoredCustom || program?.config?.progression === 'manual' || slotIndex === day?.primaryIndex
        || (program?.config?.deload && weekIndex > weeks))
        return null;
    const focused = (program?.config?.focusList || []).includes(ex.part) || Number(program?.config?.focus?.[ex.part]) > 0;
    return lastSetTech(ex, goalForDay(program, day) === 'strength', focused, blockPhase(program, weekIndex, 0), weeks);
}

function computeCell(program, day, id, slotIndex, weekIndex) {
    const ex = EX_BY_ID[id];
    if (!ex)
        return { sets: 0, reps: "—", note: "Exercise unavailable", range: null, rir: null, tech: null, missing: true };
    // Freestyle is deliberately not a generated program. It gets neutral logging defaults from the
    // Next runtime layer, never from the removed production/v661 programming rules.
    if (program?.artifactType === "freestyle" || program?.quick)
        return freestyleCellForRepRange(ex.rep);
    /* A CUSTOM program (Build your own, custom: true) is the lifter's own: their per-slot settings (sets, reps, reps-in-reserve,
       rest, technique) win, and anything they did not set gets the same neutral defaults a Quick workout uses — never the removed
       v661 rules. Without this it fell through to "This older plan is archived — rebuild it" with 0 sets. */
    if (program?.custom === true && program?.engineSource !== "pursuit-next") {
        const base = freestyleCellForRepRange(ex.rep), o = program.overrides?.[`${day?.id}:${slotIndex}`] || {};
        /* Custom-plan overrides can come from backups / old editors as arrays ([10,15], [2,2]).
           Keep the shell shape canonical just like Pursuit Next cells do. Otherwise String([10,15])
           becomes "10,15"; the old progression parser then saw only the first number and treated
           the *bottom* of a 10-15 range as the top, which could award a load increase at 10 reps. */
        const repPair = cellRepRange({ range: o.reps ?? base.reps }, program, ex, slotIndex === day?.primaryIndex);
        const reps = repPair[0] === repPair[1] ? String(repPair[0]) : `${repPair[0]}-${repPair[1]}`;
        const effort = effortBounds(o.rir ?? base.rir);
        const rir = effort ? (effort[0] === effort[1] ? String(effort[0]) : `${effort[0]}-${effort[1]}`) : (o.rir ?? base.rir);
        return { sets: legacyCustomSetCount(program, day, ex, slotIndex, weekIndex, o.sets, base.sets), reps, range: reps, rir, rest: o.rest ?? base.rest, tech: customLastSetTechnique(program, day, ex, slotIndex, weekIndex),
            role: base.role, progressionStyle: program.progStyle?.[id] ?? o.progressionStyle ?? "auto", note: "Your program", custom: true };
    }
    const nextCell = getNextShellCell(program, day, slotIndex, weekIndex);
    if (nextCell)
        return nextCell;
    /* M46: prescription fallback to the v661 set/rep/RIR engine is removed. A Pursuit Next artifact
       either has an engine-owned week cell or is considered incomplete/corrupt and fails visibly.
       Pre-Next saved programs are preserved for migration and must be rebuilt before execution. */
    return {
        sets: 0, reps: "—", note: program?.engineSource === "pursuit-next"
            ? "Prescription data is missing — rebuild this program"
            : "This older plan is archived — rebuild it to continue",
        range: null, rir: null, tech: null, missing: true, engineMissing: true
    };
}

const SECONDARY = {
    "bb-bench": [["triceps", .5], ["shoulders", .4]], "inc-bb-bench": [["triceps", .5], ["shoulders", .5]],
    "low-inc-bb-bench": [["triceps", .5], ["shoulders", .45]], "high-inc-bb-bench": [["triceps", .45], ["shoulders", .6]],
    "ez-pushdown": [],
    "inc-db-press": [["triceps", .5], ["shoulders", .5]], "db-bench": [["triceps", .5], ["shoulders", .4]],
    "machine-press": [["triceps", .4], ["shoulders", .3]], "smith-bench": [["triceps", .5], ["shoulders", .4]],
    "dips-chest": [["triceps", .5], ["shoulders", .3]], "assisted-dip": [["triceps", .5], ["shoulders", .3]], "pushup": [["triceps", .4], ["shoulders", .3]],
    "ohp": [["triceps", .5]], "db-shoulder": [["triceps", .4]], "machine-shoulder": [["triceps", .4]],
    "arnold": [["triceps", .4]], "pike-pushup": [["triceps", .4]],
    "bb-row": [["biceps", .4], ["shoulders", .3]], "chest-row": [["biceps", .4], ["shoulders", .3]],
    "seated-row": [["biceps", .4], ["shoulders", .3]], "db-row": [["biceps", .4]], "tbar-row": [["biceps", .4], ["shoulders", .3]],
    "machine-row": [["biceps", .4], ["shoulders", .3]], "inv-row": [["biceps", .4]],
    "pullup": [["biceps", .5]], "lat-pulldown": [["biceps", .5]], "chinup": [["biceps", .6]], "assisted-chinup": [["biceps", .6]],
    "deadlift": [["hamstrings", .5], ["glutes", .5], ["traps", .3]], "sumo-dl": [["lower_back", .4], ["quads", .4], ["hamstrings", .3]],
    "back-squat": [["glutes", .5], ["hamstrings", .3]], "front-squat": [["glutes", .4], ["hamstrings", .2]],
    "hack-squat": [["glutes", .4]], "leg-press": [["glutes", .4], ["hamstrings", .2]], "smith-squat": [["glutes", .4], ["hamstrings", .2]],
    "bulgarian": [["glutes", .5], ["hamstrings", .3]], "goblet": [["glutes", .4]], "walking-lunge": [["glutes", .5], ["hamstrings", .2]],
    "bw-squat": [["glutes", .4]], "bw-lunge": [["glutes", .4]], "bw-bulgarian": [["glutes", .5]],
    "rdl": [["glutes", .5], ["lower_back", .3]], "db-rdl": [["glutes", .5], ["lower_back", .2]], "good-morning": [["glutes", .4], ["lower_back", .3]],
    "slrdl": [["glutes", .5]], "hip-thrust": [["hamstrings", .3]], "db-hip-thrust": [["hamstrings", .3]], "sl-hip-thrust": [["hamstrings", .3]], "glute-bridge": [["hamstrings", .2]],
    "cgbp": [["chest", .4], ["shoulders", .2]], "dips-tri": [["chest", .4], ["shoulders", .2]], "diamond-pushup": [["chest", .4]], "bench-dip": [["chest", .2]],
    "hammer": [["forearms", .4]], "farmers": [["traps", .4]],
    // extended library
    "decline-bench": [["triceps", .5], ["shoulders", .3]], "decline-db-press": [["triceps", .5], ["shoulders", .3]],
    "incline-machine-press": [["triceps", .4], ["shoulders", .4]], "floor-press": [["triceps", .5], ["shoulders", .3]],
    "machine-dip": [["triceps", .5], ["shoulders", .3]], "incline-pushup": [["triceps", .4], ["shoulders", .3]],
    "decline-pushup": [["triceps", .4], ["shoulders", .4]], "cable-press": [["triceps", .4], ["shoulders", .3]],
    "pendlay-row": [["biceps", .4], ["shoulders", .3]], "meadows-row": [["biceps", .4], ["shoulders", .3]],
    "seal-row": [["biceps", .4], ["shoulders", .3]], "inc-db-row": [["biceps", .4]],
    "wide-pulldown": [["biceps", .4]], "close-pulldown": [["biceps", .5]], "neutral-pulldown": [["biceps", .5]],
    "one-arm-pulldown": [["biceps", .4]], "assisted-pullup": [["biceps", .5]], "rack-pull": [["traps", .4], ["hamstrings", .3], ["glutes", .3]],
    "kroc-row": [["biceps", .4], ["traps", .3]], "db-pullover": [["chest", .3]], "floor-db-pullover": [["chest", .3]], "bent-db-pullover": [["chest", .2]],
    "seated-ohp": [["triceps", .5]], "seated-db-press": [["triceps", .4]], "smith-ohp": [["triceps", .4]],
    "landmine-press": [["triceps", .4], ["chest", .3]], "upright-row": [["traps", .4]], "db-upright-row": [["traps", .4]],
    "cross-hammer": [["forearms", .4]], "rope-hammer-curl": [["forearms", .4]], "zottman-curl": [["forearms", .5]],
    "jm-press": [["chest", .4]],
    "pendulum-squat": [["glutes", .4], ["hamstrings", .2]], "belt-squat": [["glutes", .4]], "box-squat": [["glutes", .5], ["hamstrings", .3]],
    "split-squat": [["glutes", .4], ["hamstrings", .2]], "step-up": [["glutes", .5], ["hamstrings", .2]],
    "reverse-lunge": [["glutes", .5], ["hamstrings", .2]], "single-leg-press": [["glutes", .4]],
    "stiff-deadlift": [["glutes", .4], ["lower_back", .3]], "ghr": [["glutes", .4], ["calves", .2]],
    "pull-through": [["glutes", .5], ["lower_back", .2]], "kb-swing": [["glutes", .4], ["lower_back", .2]],
    "machine-hip-thrust": [["hamstrings", .3]], "smith-hip-thrust": [["hamstrings", .3]], "reverse-hyper": [["hamstrings", .3], ["lower_back", .2]],
    "push-press": [["triceps", .4]], "underhand-row": [["biceps", .5]], "underhand-pulldown": [["biceps", .5]], "single-cable-row": [["biceps", .4]],
    "close-cable-row": [["biceps", .5], ["upper_back", .4]], "wide-cable-row": [["biceps", .3], ["shoulders", .3]],
    "underhand-cable-row": [["biceps", .5], ["upper_back", .4]], "rope-cable-row": [["biceps", .3], ["shoulders", .3]],
    "dual-cable-row": [["biceps", .4], ["lats", .4], ["shoulders", .3]],
    "wide-machine-row": [["biceps", .3], ["shoulders", .3]], "neutral-machine-row": [["biceps", .4], ["lats", .4]],
    "wide-tbar-row": [["biceps", .4], ["shoulders", .3]],
    "squeeze-press": [["triceps", .4], ["shoulders", .3]], "z-press": [["triceps", .5]],
    "bstance-hip-thrust": [["hamstrings", .3]], "curtsy-lunge": [["hamstrings", .2]],
    "cyclist-squat": [["glutes", .2]], "suitcase-carry": [["traps", .3], ["abs", .3]],
    "trap-bar-shrug": [], "power-shrug": [["upper_back", .2]], "jefferson-curl": [["glutes", .3], ["lower_back", .3]],
    "tate-press": [["chest", .2]], "cable-crossover": [],
    // expansion 2 + 3 compounds
    "incline-cable-press": [["triceps", .4], ["shoulders", .4]], "smith-incline": [["triceps", .5], ["shoulders", .4]],
    "low-incline-db": [["triceps", .5], ["shoulders", .5]], "band-pushup": [["triceps", .4], ["shoulders", .3]],
    "yates-row": [["biceps", .4], ["traps", .3]], "cs-db-row": [["biceps", .4]], "renegade-row": [["biceps", .4], ["abs", .3]],
    "band-pulldown": [["biceps", .4]], "bw-pullup": [["biceps", .5]],
    "trap-bar-deadlift": [["traps", .4], ["hamstrings", .4], ["glutes", .4], ["quads", .3]],
    "snatch-deadlift": [["traps", .4], ["hamstrings", .4], ["glutes", .4]],
    "cable-upright-row": [["traps", .4]], "bradford-press": [["triceps", .5]], "viking-press": [["triceps", .4]],
    "california-press": [["chest", .4]],
    "zercher-squat": [["glutes", .5], ["hamstrings", .3], ["abs", .3]], "lm-squat": [["glutes", .4], ["hamstrings", .2]],
    "heels-up-goblet": [["glutes", .3]], "pistol-squat": [["glutes", .4]], "v-squat": [["glutes", .4]],
    "cable-rdl": [["glutes", .5], ["lower_back", .2]], "band-good-morning": [["glutes", .4], ["lower_back", .3]],
    "back-ext-45": [["glutes", .5], ["hamstrings", .4]],
    "kas-glute-bridge": [["hamstrings", .3]], "band-hip-thrust": [["hamstrings", .3]],
    "sumo-squat": [["quads", .3], ["hamstrings", .2]], "step-through-lunge": [["quads", .4], ["hamstrings", .2]],
    // adductors / abductors
    "hip-abduction": [["glutes", .3]], "cable-abduction": [["glutes", .3]], "lateral-walk": [["glutes", .3]],
    "standing-cable-abduction": [["glutes", .3]], "side-lying-abduction": [["glutes", .2]], "band-abduction": [["glutes", .2]],
    "copenhagen": [["abs", .3]], "cossack-squat": [["glutes", .4], ["quads", .3]],
    "adductor-sumo": [["glutes", .4], ["quads", .3], ["hamstrings", .2]],
    "adduction-machine": [], "cable-adduction": [], "band-adduction": [],
    // expansion 4
    "db-floor-press": [["triceps", 0.4], ["shoulders", 0.2]],
    "db-neutral-press": [["triceps", 0.4], ["shoulders", 0.3]],
    "bodyweight-deficit-pushup": [["triceps", 0.4], ["shoulders", 0.3]],
    "bands-chest-press": [["triceps", 0.4], ["shoulders", 0.3]],
    "helms-row": [["biceps", 0.4]],
    "kb-gorilla-row": [["biceps", 0.4]],
    "t-bar-chest-supported": [["biceps", 0.4]],
    "bodyweight-doorway-row": [["biceps", 0.3]],
    "db-deficit-lunge": [["glutes", 0.4], ["hamstrings", 0.2]],
    "smith-reverse-lunge": [["glutes", 0.4], ["hamstrings", 0.2]],
    "db-deficit-step-up": [["glutes", 0.4], ["hamstrings", 0.2]],
    "jefferson-squat": [["glutes", 0.5], ["hamstrings", 0.3], ["lower_back", 0.2]],
    "bands-squat": [["glutes", 0.4]],
    "deficit-db-rdl": [["glutes", 0.5], ["lower_back", 0.3]],
    "db-good-morning": [["glutes", 0.4], ["lower_back", 0.3]],
    "deficit-barbell-deadlift": [["glutes", 0.5], ["lower_back", 0.4], ["traps", 0.3]],
    "cable-rear-delt-row": [["upper_back", 0.2]],
    "bands-face-pull": [["upper_back", 0.2]],
    "db-incline-shrug": [["upper_back", 0.2]]
};

const squatHamCredit = (ex, eng) => {
    if (!eng || true)
        return null; /* HELD — see the corrections note in ENGINE_RULES */
    const kpat = movePattern(ex);
    if (kpat === "squat" || kpat === "legpress")
        return 0;
    /* conventional and trap-bar pulls only — an RDL is hamstrings-primary and never reaches here */
    if (kpat === "hinge" && ex.part !== "hamstrings")
        return 0.1;
    return null;
    const pat = movePattern(ex);
    return (pat === "squat" || pat === "legpress") ? "hamstrings" : null;
};

const secondaryOf = (ex, eng) => {
    /* ⚠ RETURN THE ORIGINAL ARRAY WHEN NOTHING IS BEING CORRECTED. Rebuilding it unconditionally with
       flatMap produced the same VALUES but fresh arrays, and gates/frozen moved engines 27-30 —
       identical numbers, different object identity, and something downstream is sensitive to it. Only
       allocate when the correction actually applies. */
    const cap = squatHamCredit(ex, eng);
    const raw = SECONDARY[ex.id] || [];
    const base = cap == null ? raw : raw.flatMap(([p, f]) => p === "hamstrings" ? (cap > 0 ? [[p, Math.min(f, cap)]] : []) : [[p, f]]);
    // Compound back movements train both regions: rows hit the lats, vertical pulls hit the
    // mid-back/rhomboids. Credit the non-primary region so volume tracking reflects reality.
    let out = base;
    if (ex.type === "compound") {
        if (ex.part === "upper_back" && !base.some(([p]) => p === "lats"))
            out = [...out, ["lats", 0.4]];
        if (ex.part === "lats" && !base.some(([p]) => p === "upper_back"))
            out = [...out, ["upper_back", 0.4]];
    }
    // Heavy horizontal rows and hinges load the traps hard through scapular retraction / shrug at
    // lockout — crediting that keeps trap volume realistic (otherwise traps sit under MEV on most
    // splits despite all the rowing). Don't double-credit anything that already lists traps.
    const rowsHinges = ex.part === "upper_back" || (ex.type === "compound" && (ex.part === "lower_back" || /deadlift|row|rdl|good-morning|rack-pull|shrug|pull/.test(ex.id)));
    if (rowsHinges && !out.some(([p]) => p === "traps"))
        out = [...out, ["traps", 0.3]];
    return out;
};

function movePattern(ex) {
    const n = ex.id, has = (...k) => k.some(s => n.includes(s));
    if (ex.part === "neck")
        return "generic";
    if (ex.part === "adductors")
        return "adduction";
    if (has("side-lying-abduction"))
        return "sidelyingabduction";
    if (has("band-abduction"))
        return "seatedabduction";
    if (ex.part === "abductors")
        return "abduction";
    if (has("seated-calf"))
        return "seatedcalf";
    if (has("leg-press-calf", "hack-calf", "single-leg-press-calf"))
        return "legpresscalf";
    if (has("donkey-calf"))
        return "donkeycalf";
    if (ex.part === "calves")
        return "calf";
    // Isometric plank-family holds — resisting movement is the entire point, not a dynamic crunch.
    if (ex.part === "abs" && has("plank", "stir-pot"))
        return "plank";
    // Push-up family: a moving plank on the hands. Catches this before the abs/triceps/chest branches
    // below so diamond-pushup (part=triceps, previously a standing triceps-extension pose) and
    // serratus-pushup (part=abs, previously a dynamic crunch) get the correct pose too, not just the
    // plain chest-classified variants.
    if (has("pushup"))
        return "pushup";
    if (ex.part === "abs")
        return "ab";
    if (ex.part === "traps" || has("shrug"))
        return "shrug";
    if (has("dead-hang"))
        return "deadhang";
    if (ex.part === "forearms")
        return "curl";
    if (has("dips-chest", "dips-tri", "assisted-dip", "machine-dip"))
        return "dip";
    if (ex.id === "db-fly" || ex.id === "db-incline-fly")
        return "dbfly"; // supine on a bench, not standing
    if (ex.part === "chest")
        return has("fly", "pec", "crossover") ? "fly" : "press";
    if (has("ohp", "shoulder", "arnold", "push-press", "pike", "landmine", "z-press") || (has("upright") && !has("row")))
        return "overhead";
    if (ex.part === "shoulders" && has("press"))
        return "overhead"; // seated/standing shoulder presses
    if (has("lat-raise", "lateral", "rear", "reverse", "face", "front-raise", "y-raise", "raise", "fly") && ex.part === "shoulders")
        return "lateral";
    if (has("pulldown", "pullup", "chinup", "straight-pulldown"))
        return "pulldown";
    if (has("deadlift", "rdl", "good-morning", "slrdl", "stiff", "pull-through", "swing", "ghr", "hyper", "rack"))
        return "hinge";
    if (has("hip-thrust", "glute-bridge", "thrust", "bridge", "frog", "abduction"))
        return "hipthrust";
    if (has("upright-row"))
        return "uprightrow"; // standing vertical pull to the chin — not a hinge
    if (has("seated-row", "cable-row") && !has("upright"))
        return "seatedrow"; // seated horizontal pull from a low stack
    if (has("row") && !has("pulldown"))
        return "row"; // a row is a horizontal pull even when it emphasizes the lats (close/underhand grips)
    if (ex.part === "upper_back" && has("row", "pull", "pendlay", "meadows", "seal", "kroc"))
        return "row";
    if (ex.part === "lats")
        return "pulldown";
    if (ex.part === "upper_back")
        return "row";
    if (has("superman"))
        return "superman";
    if (ex.part === "lower_back")
        return "hinge";
    if (ex.part === "quads" && (has("leg-ext", "extension")))
        return "legext";
    if (has("bulgarian", "lunge", "split-squat", "pistol"))
        return "lunge";
    if (has("leg-press"))
        return "legpress";
    if (has("step-up"))
        return "stepup";
    if (has("squat", "leg-press", "lunge", "split", "bulgarian", "step-up", "goblet", "pendulum", "belt", "sissy", "hack"))
        return "squat";
    if (ex.part === "quads")
        return "squat";
    if (has("standing-curl"))
        return "standingcurl"; // upright, not prone
    if (ex.part === "hamstrings" && has("curl"))
        return "legcurl";
    if (ex.part === "hamstrings" || ex.part === "glutes")
        return "hinge";
    if (ex.part === "biceps" || has("curl"))
        return "curl";
    if (ex.part === "triceps") {
        // Close-grip/JM/California press are lying BENCH PRESSES with a triceps emphasis — the bar
        // travels to the chest exactly like a regular bench press, not a floor/standing extension.
        if (has("cgbp", "jm-press", "california-press"))
            return "press";
        // Skull crusher, lying DB extension, and the PJR pullover are lying EXTENSIONS — the bar/dumbbells
        // travel toward the forehead with the upper arm fixed, a different motion from either a bench
        // press or the standing overhead extension the plain "triceps" pose below is built for.
        if (has("skullcrusher", "lying-db-ext", "pjr-pullover"))
            return "supinetriceps";
        return "triceps";
    }
    return "generic";
}

function effortBounds(rir) {
    if (rir == null || rir === "")
        return null;
    let vals = null;
    if (Array.isArray(rir)) {
        vals = rir.slice(0, 2).map(Number).filter(Number.isFinite);
    }
    else if (typeof rir === "number") {
        if (!Number.isFinite(rir))
            return null;
        vals = [rir];
    }
    else {
        const raw = String(rir).trim();
        if (!raw)
            return null;
        // Accept the canonical 1-2 form plus legacy array stringification ("2,2") and en dashes.
        vals = raw.split(/\s*[-,–]\s*/).slice(0, 2).map(Number).filter(Number.isFinite);
    }
    if (!vals || !vals.length)
        return null;
    const a = vals[0], b = vals.length > 1 ? vals[1] : vals[0];
    return a <= b ? [a, b] : [b, a];
}

const LANDMARKS = {
    chest: { mev: 8, mrv: 22 }, lats: { mev: 8, mrv: 25 }, upper_back: { mev: 8, mrv: 22 }, shoulders: { mev: 8, mrv: 26 },
    lower_back: { mev: 2, mrv: 12 },
    biceps: { mev: 8, mrv: 24 }, triceps: { mev: 6, mrv: 22 }, quads: { mev: 8, mrv: 20 },
    hamstrings: { mev: 6, mrv: 20 }, glutes: { mev: 4, mrv: 26 }, calves: { mev: 8, mrv: 22 },
    abs: { mev: 6, mrv: 25 }, traps: { mev: 6, mrv: 26 }, forearms: { mev: 4, mrv: 20 }, neck: { mev: 4, mrv: 16 },
    adductors: { mev: 4, mrv: 16 }, abductors: { mev: 4, mrv: 16 }
};

const EXP_BAND = {
    novice: { mev: 0.6, mrv: 0.75 },
    beginner: { mev: 0.75, mrv: 0.85 },
    intermediate: { mev: 1, mrv: 1 },
    advanced: { mev: 1.25, mrv: 1.1 },
};

const UNIFORM_LANDMARK = { mev: 4, mav: 18, mrv: 31 };

const uniformLandmarkFor = (part) => {
    const sub = (typeof SUB_LANDMARKS !== "undefined") ? SUB_LANDMARKS[part] : null;
    return (sub && Number(sub.mev) === 0) ? { ...UNIFORM_LANDMARK, mev: 0 } : { ...UNIFORM_LANDMARK };
};

const landmarkFor = (part, exp = "intermediate", src = 0) => {
    if (src?.engineSource === "pursuit-next" && src.nextEngine?.request) {
        const targets = shellVolumeTargets(src);
        const target = targets.find(t => t.part === part || t.region === part);
        if (target) return target;
        if (part === "shoulders") {
            const heads = targets.filter(t => ["side_delts", "rear_delts"].includes(t.region));
            return { mev: heads.reduce((n, t) => n + t.mev, 0), mav: heads.reduce((n, t) => n + t.mav, 0), mrv: heads.reduce((n, t) => n + t.mrv, 0) };
        }
    }
    const stamped = src && typeof src === "object" ? src.landmarks : null;
    if (stamped)
        return stamped.uniform ? uniformLandmarkFor(part) : landmarkForLegacy(part, exp);
    /* held: the uniform table returns here when the rule is registered again */
    /* ⚠ FRONT DELTS NEED AN EXCEPTION WHEN THIS SHIPS, AND THE ATTEMPT BELOW DID NOT WORK. Under
       uniform landmarks the anterior deltoid gets a floor of 4, but its entire requirement is met by
       compound pressing (reconciled DIRECT requirement: zero) — this app has carried MEV 0 for it for
       years. 13 of 20 floor breaches under uniform landmarks were front delts alone.
       The tried fix — read `landmarkForLegacy` and keep a zero — FAILED because front_delts lives in
       SUB_LANDMARKS and landmarkForLegacy does not resolve sub-heads. Check SUB_LANDMARKS explicitly. */
    return landmarkForLegacy(part, exp);
};

const landmarkForLegacy = (part, exp = "intermediate") => {
    const base = (typeof SUB_LANDMARKS !== "undefined" && SUB_LANDMARKS[part]) || LANDMARKS[part] || { mev: 8, mrv: 22 };
    const k = EXP_BAND[exp] || EXP_BAND.intermediate;
    if (k.mev === 1 && k.mrv === 1)
        return base;
    // front delts have MEV 0 by design (pressing supplies them) — scaling zero must stay zero.
    return { ...base, mev: base.mev ? Math.max(1, Math.round(base.mev * k.mev)) : 0, mrv: Math.round(base.mrv * k.mrv) };
};

const SUB_LANDMARKS = {
    front_delts: { mev: 0, mrv: 12 }, side_delts: { mev: 8, mrv: 26 }, rear_delts: { mev: 8, mrv: 24 },
    upper_traps: { mev: 4, mrv: 26 }, serratus: { mev: 4, mrv: 16 }, obliques: { mev: 4, mrv: 16 }, tibialis: { mev: 4, mrv: 16 }
};

const COMPOSITE_HEADS = { shoulders: ["front_delts", "side_delts", "rear_delts"] };

function compositeMrv(part, program = 0) {
    const heads = COMPOSITE_HEADS[part];
    if (!heads)
        return landmarkFor(part, "intermediate", program).mrv;
    if (program && typeof program === "object" && program.landmarks?.uniform) {
        return heads.reduce((s) => s + landmarkFor("side_delts", "intermediate", program).mrv, 0);
    }
    return heads.reduce((s, h) => s + (SUB_LANDMARKS[h]?.mrv || 0), 0);
}

const roundTo = (x, step) => {
    const v = Number(x), st = Number(step);
    if (!Number.isFinite(v))
        return 0;
    if (!Number.isFinite(st) || st <= 0)
        return v;
    return Math.round(v / st) * st;
};

const LOWER_PARTS = ["quads", "hamstrings", "glutes"];

let LOAD_INC = { v: 0, unit: null };

let EX_LOAD_INC = {};

function loadStep(ex, unit) {
    // A per-exercise increment (e.g. a specific machine's pin-stack notch) wins over everything —
    // it's the user telling us exactly how this machine actually moves.
    if (ex && EX_LOAD_INC[ex.id] && EX_LOAD_INC[ex.id].v > 0 && EX_LOAD_INC[ex.id].unit === unit)
        return EX_LOAD_INC[ex.id].v;
    // User-set global plate increment wins next when it applies (their actual smallest jump).
    if (LOAD_INC.v > 0 && LOAD_INC.unit === unit)
        return LOAD_INC.v;
    const lower = LOWER_PARTS.includes(ex.part);
    // Fixed/selectorized loads jump coarser than a micro-loadable barbell: a dumbbell pair or a
    // machine stack typically moves in ~5 lb / 2.5 kg notches per hand or plate, and you can't
    // split it. Reflect that so progression math doesn't assume bar-fine increments it can't make.
    const eq = ex.equip || [];
    const fixedJump = (eq.includes("dumbbell") || eq.includes("kettlebell")) && !isBarLike(eq);
    const machineJump = isMachineLike(eq) && !isBarLike(eq) && !eq.includes("dumbbell");
    // Isolations on a machine/cable (triceps pushdown, lateral raise, curl, leg extension) should not
    // jump a full pin-stack notch — 10 lb on a small muscle is a 15-30% leap that stalls progress.
    // Treat compound machine work (leg press, pulldown, row, hack squat) as the coarse 10 lb stack,
    // but step isolations at 5 lb (and cables, which usually have finer increments, at 5 lb too).
    // Only a true selectorized/plate machine stack (leg press, hack squat, plate-loaded row) jumps
    // coarse and can't be split. Cables usually have add-on weights or finer pins, and any barbell
    // takes a pair of 2.5 lb plates — so all barbell & freeweight work, all cables, and machine
    // ISOLATIONS step the fine 5 lb / 2.5 kg. That keeps progression sustainable (a 10 lb jump on a
    // squat or a pulldown is a big leap most weeks); the coarse 10 lb / 5 kg is reserved for compound
    // machine stacks where it's genuinely the smallest notch (and any machine can be set exactly via
    // its per-exercise increment).
    const eqCable = eq.includes("cable") && !isBarLike(eq) && !eq.includes("dumbbell");
    const coarseMachine = machineJump && !eqCable && ex.type !== "isolation"; // compound plate/pin stack
    if (unit === "lb") {
        if (fixedJump)
            return lower ? 10 : 5; // DB/KB pairs move in ~5 lb-per-hand notches
        if (coarseMachine)
            return 10; // leg press / hack squat / plate-loaded stack
        return 5; // barbell, freeweight, cables, machine isolations
    }
    if (fixedJump)
        return lower ? 4 : 2;
    if (coarseMachine)
        return 5;
    return 2.5;
}

const STORE_VERSION = 13;

const BW_LOG_CAP = 2000;

const STORE_MIGRATIONS = {
    // 12 -> 13: completed workout history became editable in M164, so a same-id session can now have
    // two legitimate versions. Give history the same per-record conflict clock used by other mutable
    // syncable lists. Legacy records deliberately start at zero so any real M177+ edit wins.
    13: (d) => {
        if (!Array.isArray(d.history))
            return d;
        const history = d.history.map(x => (x && typeof x === "object" && x.updatedAt == null) ? { ...x, updatedAt: 0 } : x);
        return { ...d, history };
    },
    // 11 -> 12: selective-promotion safety state is release-scoped and starts fail-safe. M76 ships
    // with a zero-percent manifest, so upgrading cannot change a prescription.
    12: (d) => d.selectivePromotionRuntime ? d : { ...d, selectivePromotionRuntime: emptyRetiredRolloutData() },
    // 10 -> 11: controlled canary research is opt-in and local. Existing users must remain
    // unenrolled after upgrade; a missing field therefore migrates to an explicit disabled state.
    11: (d) => d.canaryResearch ? d : { ...d, canaryResearch: emptyRetiredTrialData() },
    // 9 -> 10: the typed `age` integer becomes a `birth` date. An age is only true for a year and the
    // app had no way to know it had gone stale, so a lifter's strength standards quietly drifted onto
    // the wrong age band and stayed there. The exact date can't be recovered from a number, so estimate
    // mid-year of the implied birth year (±6 months, inside ageFactor's 5–6 year bands) and mark it
    // `birthEst` so the UI can ask for the real one. `age` is left in place and kept in sync on save,
    // so an older build reading this store after a downgrade still finds the field it expects.
    10: (d) => {
        if (d.birth)
            return d;
        const b = birthFromAge(d.age, d.savedAt);
        return b ? { ...d, birth: b, birthEst: true } : d;
    },
    // 7 → 8: the single `equipDefault` list becomes a list of GYMS. Equipment belongs to a place, not
    // to a person, and a lifter with a garage rack and a gym membership had no way to say so.
    // The existing list is preserved as the ACTIVE gym so nobody's programs change on upgrade, and it
    // is named from its own contents rather than assumed — calling a full commercial kit "Home gym"
    // would be a silent lie. A second preset is added so the pair the app promises always exists.
    // `equipDefault` is deliberately left in place and kept in sync: an older build reading this store
    // after a downgrade still finds the field it expects.
    // 8 -> 9: "Selectorized Machines" split into specific apparatus. Grant every gym the fine ids its
    // coarse category already implied, so no user's available exercises change on upgrade. Granting is
    // conditional on the parent: a bands-only gym must not acquire a leg press.
    9: (d) => {
        const grow = (g) => {
            const eq = new Set(Array.isArray(g.equipment) ? g.equipment : []);
            Object.entries(LEGACY_EQUIP_IMPLIES).forEach(([parent, fine]) => { if (eq.has(parent))
                fine.forEach(f => eq.add(f)); });
            return { ...g, equipment: [...eq] };
        };
        /* Defensive: a store can arrive at v8 without gyms — a partial import, a merge from a device that
           never wrote them, a hand-edited backup. Rebuilding them here rather than assuming means the
           migration can never hand the app a gym-less store. normalizeGyms would paper over it on load,
           but a migration that silently drops the user's equipment is not something to leave to a later
           safety net. */
        const base = (Array.isArray(d.gyms) && d.gyms.length) ? d : STORE_MIGRATIONS[8]({ ...d, gyms: null });
        return { ...base, gyms: (Array.isArray(base.gyms) ? base.gyms : []).map(grow),
            equipDefault: Array.isArray(base.equipDefault) ? grow({ equipment: base.equipDefault }).equipment : base.equipDefault };
    },
    8: (d) => {
        if (Array.isArray(d.gyms) && d.gyms.length)
            return d;
        const eq = Array.isArray(d.equipDefault) && d.equipDefault.length ? d.equipDefault : [...ALL_EQUIP_IDS];
        const isFull = ALL_EQUIP_IDS.every(x => eq.includes(x));
        const mine = { id: "gym_1", name: isFull ? "Commercial gym" : "My gym", equipment: eq };
        const other = isFull
            ? { id: "gym_2", name: "Home gym", equipment: [...GYM_PRESETS[2].equipment] }
            : { id: "gym_2", name: "Commercial gym", equipment: [...ALL_EQUIP_IDS] };
        return { ...d, gyms: [mine, other], activeGymId: mine.id };
    },
    // 1 → 2: the legacy `lastWeights` map (weight only) becomes the per-exercise `perf` store
    // (weight + reps). Previously handled inline at hydration; centralized here so load AND import
    // both get it. No-op when `perf` already exists (everyone past the original change).
    2: (d) => {
        if (!d.perf && d.lastWeights) {
            const perf = {};
            Object.entries(d.lastWeights).forEach(([k, v]) => { perf[k] = { weight: v, reps: null }; });
            return { ...d, perf };
        }
        return d;
    },
    // 2 → 3: repair runaway auto-volume. Per-session autoregulation used to be merged into `slotBias`
    // and persisted, so set counts compounded every session (programs grew longer week over week).
    // Autoregulation now lives in a separate `autoBias` field rebuilt fresh each session. Reset each
    // auto-progression program's designed volume to its clean structural baseline (base sets, then the
    // MRV cap) — exactly what a freshly generated block uses — and drop any stored autoBias. Manual
    // per-set edits (overrides / circuit rounds) and all progression state are left untouched.
    3: (d) => {
        if (!Array.isArray(d.saved))
            return d;
        const saved = d.saved.map(p => {
            if (!p || !p.config)
                return p;
            if (p.config.progression === "manual" || p.config.percentScheme)
                return p; // never auto-volumed
            if (!p.slotBias && !p.autoBias)
                return p; // nothing accumulated
            const np = { ...p, slotBias: {}, autoBias: undefined };
            if (np.config.autoVolume)
                np.config = { ...np.config, autoVolume: false };
            try {
                capWeeklyVolume(np);
            }
            catch { } // re-enforce the MRV ceiling on the clean baseline
            return np;
        });
        return { ...d, saved };
    },
    // 3 → 4: barbell compounds no longer default to the rep ladder (auto now picks double progression,
    // which fits lifts whose reps fall across sets). Drop any progStyle override that PINS a barbell
    // compound to "ladder" so it falls back to auto → double. Machine/isolation ladders — where reps
    // hold across sets and the ladder is a fine choice — are left exactly as the lifter set them, as
    // are all other explicit styles. A no-op for the common case (styles were never saved, just
    // auto-resolved), which the auto change already corrects.
    4: (d) => {
        if (!Array.isArray(d.saved))
            return d;
        // Frozen deliberately: this is a v4-era migration, a historical artifact. It should keep
        // classifying the way it did when it was written, not drift with today's taxonomy.
        const isBarbellCompound = (id) => { const ex = EX_BY_ID[id]; return !!ex && ex.type === "compound" && (ex.equip || []).includes("barbell"); };
        const saved = d.saved.map(p => {
            if (!p || !p.progStyle)
                return p;
            const ps = { ...p.progStyle };
            let changed = false;
            Object.keys(ps).forEach(id => { if (ps[id] === "ladder" && isBarbellCompound(id)) {
                delete ps[id];
                changed = true;
            } });
            if (!changed)
                return p;
            return { ...p, progStyle: Object.keys(ps).length ? ps : undefined };
        });
        return { ...d, saved };
    },
    // 4 → 5: additive only. Everything that existed before this migration was produced by engine 1, so
    // stamp it as such; that's what lets legacy programs keep engine-1 behaviour while new ones are
    // generated on engine 2. Logged sets also start carrying pw/pt (the weight and rep target that were
    // prescribed) from here on — older sets simply lack the snapshot.
    // 5 -> 6: everything moves to engine 2. Engine 1 was kept alive so that programs generated by it
    // would not have their prescriptions change mid-block underneath the lifter. With no engine-1
    // programs left in the wild, that promise has nothing to protect, and carrying two engines forever
    // to honour it would be paying rent on an empty room. Restamped here rather than defaulted at read
    // time so the store is self-describing: a program says which engine made it, and now they all say 2.
    6: (d) => {
        const stamp = (x) => (x && typeof x === "object") ? { ...x, engineV: 2 } : x;
        const out = { ...d };
        if (Array.isArray(d.saved))
            out.saved = d.saved.map(stamp);
        if (Array.isArray(d.cycles))
            out.cycles = d.cycles.map(stamp);
        // History is a RECORD of what happened. A session prescribed by engine 1 was prescribed by engine
        // 1, and rewriting that would be falsifying the log — the replay/eval tooling reads this field to
        // know which policy issued each set. Left exactly as it was.
        return out;
    },
    5: (d) => {
        const stamp = (x) => (x && typeof x === "object" && x.engineV == null) ? { ...x, engineV: 1 } : x;
        const out = { ...d };
        if (Array.isArray(d.saved))
            out.saved = d.saved.map(stamp);
        if (Array.isArray(d.cycles))
            out.cycles = d.cycles.map(stamp);
        if (Array.isArray(d.history))
            out.history = d.history.map(stamp);
        return out;
    },
    // 6 → 7: groundwork for merging two devices' data. Two things were missing and neither can be
    // reconstructed after the fact, which is why they go in now rather than when a sync backend lands:
    //
    //   `tombs`  — deletions. A merge that only unions records can never delete anything: remove a
    //              program on your phone, merge with the tablet that still has it, and it comes back
    //              from the dead. A deletion has to be a FACT that travels, not an absence.
    //   updatedAt — which of two versions of the same program is the newer one. Without it, a merge
    //              has to guess, and guessing about someone's training data is not acceptable.
    //
    // Existing records get updatedAt = 0: any edit on any device beats a record that predates the
    // concept, which is the only safe default (it can never cause a newer edit to lose).
    7: (d) => {
        const stamp = (x) => (x && typeof x === "object" && x.updatedAt == null) ? { ...x, updatedAt: 0 } : x;
        const out = { ...d, tombs: d.tombs && typeof d.tombs === "object" ? d.tombs : {} };
        if (Array.isArray(d.saved))
            out.saved = d.saved.map(stamp);
        if (Array.isArray(d.cycles))
            out.cycles = d.cycles.map(stamp);
        if (Array.isArray(d.custom))
            out.custom = d.custom.map(stamp);
        return out;
    }
};

function mergeStores(local, incoming) {
    if (!incoming || typeof incoming !== "object")
        return { data: local, stats: null };
    if (!local || typeof local !== "object")
        return { data: incoming, stats: null };
    const A = migrateStore(local), B = migrateStore(incoming);
    const aNewer = (A.savedAt || 0) >= (B.savedAt || 0);
    const older = aNewer ? B : A, newer = aNewer ? A : B;
    const tombs = { ...(A.tombs || {}) };
    Object.entries(B.tombs || {}).forEach(([id, t]) => { if (!(tombs[id] >= t))
        tombs[id] = t; });
    const stats = { history: 0, programs: 0, cycles: 0, custom: 0, bw: 0, conflicts: 0 };
    const byId = (a = [], b = [], count) => {
        const m = new Map(), sourceClock = new Map();
        const add = (rows, storeClock) => (Array.isArray(rows) ? rows : []).forEach(r => {
            if (!r || r.id == null)
                return;
            const prev = m.get(r.id);
            if (!prev) {
                m.set(r.id, r);
                sourceClock.set(r.id, storeClock);
                return;
            }
            if (prev === r)
                return;
            const prevSig = JSON.stringify(prev), nextSig = JSON.stringify(r);
            if (prevSig === nextSig)
                return;
            const pu = prev.updatedAt || 0, ru = r.updatedAt || 0;
            const pc = sourceClock.get(r.id) || 0;
            // Per-record clocks are authoritative. Store recency only breaks legacy/equal-clock ties;
            // the serialised signature is the final stable tie-breaker when both clocks are identical.
            if (ru > pu || (ru === pu && (storeClock > pc || (storeClock === pc && nextSig > prevSig)))) {
                m.set(r.id, r);
                sourceClock.set(r.id, storeClock);
            }
            stats.conflicts++;
        });
        add(a, A.savedAt || 0);
        add(b, B.savedAt || 0);
        // a tombstone removes a record only if the deletion happened AFTER the record's last edit
        const out = [...m.values()].filter(r => !(tombs[r.id] != null && tombs[r.id] >= (r.updatedAt || 0)));
        if (count)
            stats[count] = Math.max(0, out.length - (Array.isArray(a) ? a.length : 0));
        return out;
    };
    /* HISTORY_CAP is applied AFTER the union, so a merge of two well-used devices can discard real
       sessions — 400 + 400 keeps 500 and drops 300. Two things were wrong with that being invisible:
       byId() recorded stats.history from the pre-trim union, so the restore summary announced "400 new
       workouts" when only 100 survived; and the discarded sessions were never mentioned at all. The
       count is now recomputed against what was actually kept, and anything the cap removed is reported
       separately so the summary can say so out loud. Trimming the oldest is still the policy — silently
       overstating what was imported is what this fixes. */
    const histUnion = byId(A.history, B.history, "history");
    const hist = capHistory(histUnion); // count ceiling + byte budget, oldest-first
    const aLen = Array.isArray(A.history) ? A.history.length : 0;
    stats.history = Math.max(0, hist.length - aLen); // net sessions actually gained
    stats.historyDropped = Math.max(0, histUnion.length - hist.length); // lost to the cap
    const mapMerge = (key) => {
        const oa = older[key] || {}, nb = newer[key] || {};
        return { ...oa, ...nb }; // keys only the older side has survive; shared keys take the newer blob
    };
    // `perf` is a derived latest-performance mirror. A per-session correction may beat a globally newer
    // store snapshot, so rebuild exercises represented in retained history from their newest session.
    const mergedPerf = mapMerge("perf"), perfSeen = new Set();
    hist.slice().sort((x, y) => (Number(y?.date) || 0) - (Number(x?.date) || 0)).forEach(h => {
        Object.entries(h?.perf || {}).forEach(([exId, p]) => {
            if (!perfSeen.has(exId)) {
                mergedPerf[exId] = p;
                perfSeen.add(exId);
            }
        });
    });
    /* A weigh-in serialises to ~45 chars, so 200 of them was never a storage decision — it was a
     placeholder that quietly deleted the oldest data of anyone who steps on a scale daily, after
     about six months. History now retains ~6.8 years; a body-weight trend that expires first makes
     the long-range comparison it exists for impossible. 2000 daily weigh-ins is ~5.5 years for ~90KB. */
    const bwKey = (e) => String(e && e.date);
    const bw = [...(Array.isArray(A.bwLog) ? A.bwLog : []), ...(Array.isArray(B.bwLog) ? B.bwLog : [])]
        .filter(e => e && e.date != null && e.w != null)
        .reduce((m, e) => (m.has(bwKey(e)) ? m : m.set(bwKey(e), e)), new Map());
    const bwLog = [...bw.values()].sort((x, y) => x.date - y.date).slice(-BW_LOG_CAP);
    stats.bw = Math.max(0, bwLog.length - (Array.isArray(A.bwLog) ? A.bwLog.length : 0));
    const meas = { ...(older.measurements || {}) };
    Object.entries(newer.measurements || {}).forEach(([d, v]) => {
        meas[d] = (v && typeof v === "object" && meas[d] && typeof meas[d] === "object") ? { ...meas[d], ...v } : v;
    });
    const data = {
        ...older, ...newer, // scalar settings: newer blob wins
        v: STORE_VERSION,
        savedAt: Math.max(A.savedAt || 0, B.savedAt || 0),
        tombs,
        history: hist,
        saved: byId(A.saved, B.saved, "programs"),
        cycles: byId(A.cycles, B.cycles, "cycles"),
        /* Gyms merge by id with the same tombstone rules as programs and history. Previously they were
           not merged at all: the whole list came from whichever store had the later savedAt, so a gym
           added on one device was destroyed by the next sync from another. */
        gyms: (() => {
            const merged = byId(A.gyms, B.gyms, "gyms");
            return merged.length ? merged : (newer.gyms || older.gyms || []); // never sync to zero gyms
        })(),
        // The newer device's choice wins, unless the other device deleted that gym — then fall back
        // rather than leaving the app pointed at something that is gone.
        activeGymId: (() => {
            const surviving = byId(A.gyms, B.gyms);
            const want = newer.activeGymId || older.activeGymId;
            return surviving.some(g => g.id === want) ? want : (surviving[0] && surviving[0].id) || want;
        })(),
        custom: byId(A.custom, B.custom, "custom"),
        banned: [...new Set([...(A.banned || []), ...(B.banned || [])])],
        bwLog,
        measurements: meas,
        perf: mergedPerf,
        exNotes: mapMerge("exNotes"),
        exSetup: mapMerge("exSetup"),
        goals: mapMerge("goals"),
        // Research evidence is append-only. Merge trials by trial id so restoring a backup can never
        // erase a completed control/treatment observation from the other device. The newer blob owns
        // the consent toggle, but both sides' evidence survives.
        canaryResearch: (() => {
            const ca = preserveRetiredTrialData(A.canaryResearch), cb = preserveRetiredTrialData(B.canaryResearch);
            const pref = aNewer ? ca : cb;
            const trials = new Map();
            [...ca.trials, ...cb.trials].forEach(t => { const prev = trials.get(t.trialId); if (!prev || (t.completedAt || t.startedAt || 0) >= (prev.completedAt || prev.startedAt || 0))
                trials.set(t.trialId, t); });
            return { ...pref, trials: [...trials.values()].slice(-200) };
        })()
    };
    return { data, stats };
}

function migrateStore(raw) {
    if (!raw || typeof raw !== "object")
        return raw;
    let d = raw;
    let v = typeof d.v === "number" ? d.v : 1;
    while (v < STORE_VERSION) {
        v += 1;
        const fn = STORE_MIGRATIONS[v];
        if (fn)
            d = fn(d);
    }
    return d.v === STORE_VERSION ? d : { ...d, v: STORE_VERSION };
}

function parseStoredData(raw) {
    try {
        const d = JSON.parse(raw);
        const object = v => !!v && typeof v === "object" && !Array.isArray(v);
        if (!object(d))
            throw new Error("The saved data isn't a training backup.");
        if (typeof d.v === "number" && d.v > STORE_VERSION)
            throw new Error("This data needs a newer version of Pursuit Iron. Update the app, then try again.");
        for (const k of ["saved", "history", "cycles", "custom", "gyms", "bwLog", "banned", "equipDefault"]) {
            if (d[k] != null && !Array.isArray(d[k]))
                throw new Error(`The saved ${k} list couldn't be read.`);
        }
        for (const k of ["perf", "drafts", "tombs", "measurements", "goals", "exNotes", "exSetup", "minInc", "plates", "reminders", "canaryResearch", "selectivePromotionRuntime"]) {
            if (d[k] != null && !object(d[k]))
                throw new Error(`The saved ${k} settings couldn't be read.`);
        }
        for (const k of ["saved", "history", "cycles", "custom", "gyms", "bwLog"]) {
            if ((d[k] || []).some(v => !object(v)))
                throw new Error(`A saved ${k} entry couldn't be read.`);
        }
        const programs = [...(d.saved || []), ...Object.values(d.drafts || {})];
        if (programs.some(p => !object(p) || !object(p.config) || !Array.isArray(p.days) || p.days.some(day => !object(day) || !Array.isArray(day.exercises))))
            throw new Error("A saved program couldn't be read.");
        if ((d.cycles || []).some(c => !Array.isArray(c.blockIds)))
            throw new Error("A saved cycle couldn't be read.");
        if (d.reminders?.days != null && !Array.isArray(d.reminders.days))
            throw new Error("The saved reminder days couldn't be read.");
        return migrateStore(d);
    }
    catch (cause) {
        const error = new Error(cause instanceof SyntaxError ? "The saved file is incomplete or unreadable." : cause.message);
        error.raw = raw; // recovery exports the exact bytes, including malformed JSON
        throw error;
    }
}

function birthFromAge(age, asOf) {
    const a = Math.round(Number(age));
    if (!(a > 0 && a <= 120))
        return null;
    const y = new Date(asOf || Date.now()).getFullYear() - a;
    return `${y}-07-01`;
}

function summarizeHistoryLoggedSets(sets, ex, unit) {
    const work = (sets || []).filter(st => st && !st.sub && Number(st.r) > 0);
    if (!work.length)
        return null;
    const numW = st => Number.isFinite(Number(st.w)) ? Number(st.w) : 0;
    const hasPos = work.some(st => numW(st) > 0);
    const pool = hasPos ? work.filter(st => numW(st) > 0) : work;
    if (!pool.length)
        return null;
    const weights = pool.map(numW);
    const maxW = Math.max(...weights);
    if (weights.every(w => w === weights[0])) {
        const atTop = pool.filter(st => numW(st) === maxW);
        return { weight: maxW, reps: Math.min(...atTop.map(st => Math.max(1, Math.round(Number(st.r) || 1)))) };
    }
    const step = ex ? loadStep(ex, unit) : .5;
    const avgW = roundTo(weights.reduce((a, b) => a + b, 0) / weights.length, step || .5);
    const reps = Math.round(pool.reduce((a, st) => a + Math.max(1, Math.round(Number(st.r) || 1)), 0) / pool.length);
    return { weight: avgW, reps };
}

function normalizeEditedHistoryEntry(original, draft) {
    if (!original || !draft)
        return original;
    const out = { ...original };
    const date = Number(draft.date);
    if (Number.isFinite(date) && date > 0)
        out.date = date;
    const duration = Number(draft.durationMin);
    if (Number.isFinite(duration) && duration >= 0)
        out.durationMin = Math.round(duration);
    const perf = {};
    for (const [exId, rawPerf] of Object.entries(draft.perf || {})) {
        const prior = original.perf?.[exId] || {};
        const sourceSets = Array.isArray(rawPerf?.sets) && rawPerf.sets.length
            ? rawPerf.sets
            : (rawPerf?.reps != null ? [{ w: rawPerf.weight ?? 0, r: rawPerf.reps }] : []);
        const sets = [];
        for (const [setIndex, raw] of sourceSets.entries()) {
            if (!raw)
                continue;
            const reps = Math.round(Number(raw.r));
            if (!Number.isFinite(reps) || reps <= 0)
                continue;
            const w0 = raw.w === "" || raw.w == null ? 0 : Number(raw.w);
            if (!Number.isFinite(w0))
                continue;
            const st = { ...raw, w: w0, r: reps };
            if (raw.rir === "" || raw.rir == null || !Number.isFinite(Number(raw.rir))) {
                delete st.rir;
                delete st.rirReported;
            }
            else {
                st.rir = Math.max(0, Math.min(10, Number(raw.rir)));
                if (Number(raw.rir) !== Number(prior.sets?.[setIndex]?.rir)
                    || prior.sets?.[setIndex]?.rir == null) st.rirReported = true;
            }
            sets.push(st);
        }
        const summary = summarizeHistoryLoggedSets(sets, EX_BY_ID[exId], original.unit || "lb");
        if (!summary)
            continue;
        perf[exId] = { ...prior, ...rawPerf, weight: summary.weight, reps: summary.reps, sets, date: out.date };
    }
    out.perf = perf;
    let volume = 0, setsDone = 0;
    for (const p of Object.values(perf)) {
        for (const st of (p.sets || [])) {
            if (!st.sub) {
                volume += (Number(st.w) || 0) * (Number(st.r) || 0);
                setsDone++;
            }
        }
    }
    out.volume = Math.round(volume);
    out.setsDone = setsDone;
    return out;
}

function perfAfterHistoryReplace(history, perf, histId, replacement) {
    const old = (history || []).find(h => h && h.id === histId);
    if (!old || !replacement)
        return perf || {};
    const nextHistory = (history || []).map(h => h && h.id === histId ? replacement : h)
        .slice().sort((a, b) => (Number(b?.date) || 0) - (Number(a?.date) || 0));
    const affected = new Set([...Object.keys(old.perf || {}), ...Object.keys(replacement.perf || {})]);
    const next = { ...(perf || {}) };
    for (const exId of affected) {
        const latest = nextHistory.find(h => h?.perf?.[exId]);
        if (latest)
            next[exId] = latest.perf[exId];
        else
            delete next[exId];
    }
    return next;
}

const HISTORY_CAP = 2000;

const HISTORY_BYTES = 1600000;

const HISTORY_BYTE_CHECK_FROM = 700;

function capHistory(list) {
    const arr = (Array.isArray(list) ? list : []).filter(Boolean);
    let out = arr.slice().sort((x, y) => (y.date || 0) - (x.date || 0)).slice(0, HISTORY_CAP);
    // The byte pass costs a JSON.stringify, so it's skipped entirely below the threshold where the
    // budget is unreachable — which is where almost every user lives. Above it, one proportional cut
    // lands close, then a couple of bounded refinements settle it: ~3 serialisations worst case, never
    // the O(n²) of dropping one session at a time.
    if (out.length >= HISTORY_BYTE_CHECK_FROM) {
        let bytes = JSON.stringify(out).length;
        if (bytes > HISTORY_BYTES) {
            out = out.slice(0, Math.max(1, Math.floor(out.length * (HISTORY_BYTES / bytes))));
            let guard = 0;
            while (out.length > 1 && guard++ < 4 && JSON.stringify(out).length > HISTORY_BYTES) {
                out = out.slice(0, Math.max(1, Math.floor(out.length * 0.95)));
            }
        }
    }
    return out;
}
function setLoadInc(v, unit) { LOAD_INC = { v: v || 0, unit: unit || null }; }

function setExLoadInc(map) { EX_LOAD_INC = map || {}; }

export { setExLoadInc, setLoadInc, BAR_EQUIP, BW_LOG_CAP, COMPOSITE_HEADS, EXP_BAND, EX_LOAD_INC, GYM_PRESETS, HISTORY_BYTES, HISTORY_BYTE_CHECK_FROM, HISTORY_CAP, LANDMARKS, LEGACY_EQUIP_IMPLIES, LOAD_INC, LOWER_PARTS, MACHINE_EQUIP, MRV_GRAIN, MYO_PARTS, SECONDARY, STORE_MIGRATIONS, STORE_VERSION, STRETCH_PARTIAL_PARTS, SUB_LANDMARKS, TOTAL_WEEKS, UNIFORM_LANDMARK, baseSetsFor, birthFromAge, blockPhase, capHistory, capWeeklyVolume, cellRepRange, clamp, compositeMrv, computeCell, customAuthoredSetCount, customLastSetTechnique, effortBounds, goalForDay, isBarLike, isMachineLike, landmarkFor, landmarkForLegacy, lastSetTech, legacyCustomSetCount, loadStep, mergeStores, migrateStore, movePattern, normalizeCycleLinks, normalizeEditedHistoryEntry, normalizeHistoryDayIds, parseStoredData, perfAfterHistoryReplace, roundTo, secondaryOf, squatHamCredit, summarizeHistoryLoggedSets, uniformLandmarkFor, weeksOf };
