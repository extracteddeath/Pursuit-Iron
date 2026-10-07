// Canonical analytics domain. Maintained production source; independent of React and browser APIs.
import { EXERCISE_MAP as NEXT_EXERCISE_MAP } from "../next-engine/exercise-db.js";
import { setShellEquipmentExpander, splitContractGaps, splitBuildability, refusalFixes, generateNextProgramForShell, recommendNextSplitForShell, getNextShellCell, canonicalShellSetCount, cloneNextDayPrescriptions, swapNextSlotPrescriptions, removeNextSlotPrescription, nextExerciseIdForShellExercise, resolveNextShellExerciseId, remapNextShellRoster, snapshotNextShellPrescription, markUserPrescriptionOverride, clearUserPrescriptionOverride, NextShellAdapterError } from "../next-engine/app-shell-adapter.js";
import { avoidableExerciseOverlap } from "../next-engine/exercise-economy.js";
import { captureShellVolumeSnapshot, auditShellVolume, repairShellVolume, shellVolumeTargets, shellDayMuscleBreakdown } from "../next-engine/volume-repair.js";
import { historyNumber, convertHistoryLoad, observedHistoryRIR, completedHistorySets, historyExposureContext, progressionExposureContext, normalizeHistoryEntries, normalizeHistoryRevisions, validHistoryDate, resolveHistoryDayIndex, historyLoadReason } from '../next-engine/history-contract.js';
import { programWorkingWeeks, cycleBlockMetadata } from "../program-duration.js";
import { nextWorkoutSuggestionForShell, nextWorkoutSuggestionFromPerformedShell } from "../next-engine/workout-history-adapter.js";
import { ENGINE_VERSION, ENGINE_COMPATIBLE_VERSIONS } from "../next-engine/config.js";
import { ALL_EQUIP_IDS, ENGINE_V, EXERCISES, EX_BY_ID, SESSIONS, SPLITS, expandEquipment } from './catalog.js';
import { GYM_PRESETS, LOWER_PARTS, SECONDARY, SUB_LANDMARKS, blockPhase, cellRepRange, clamp, compositeMrv, computeCell, effortBounds, goalForDay, isBarLike, isMachineLike, landmarkFor, lastSetTech, loadStep, movePattern, roundTo, secondaryOf, weeksOf } from './records.js';
import { GYM_LIMITS, INVENTORY_KEYS, PART_LABEL, anchorPerfFor, effortCalibration, gymRackFor, muscleRecovery, parseRIRNum, personalRecoveryHours, prescribeSets, prescribedRIRof, progressionHistoryForProgram, sessionSuggestion, setsOf, toUnit } from './prescriptions.js';
import { E1RM_REP_CAP, EPLEY_SLOPE, PART_ORDER, decodeProgramCode, e1rm, e1rmRIR, engHas, engLacks, isAxialLoad, sameProgramContent } from './programs.js';

const FOCUS_CAVEAT = { neck: "Rarely fits — most sessions have no room for direct neck work" };

const VIEW_DEPTH = { home: 0, program: 1, library: 1, cycles: 1, compare: 1, progress: 1, settings: 1, wizard: 1, cycleWizard: 1, session: 2, cycleDetail: 2 };

const newGymId = () => `gym_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;

function canonicalHistoryNewest(history) {
    return normalizeHistoryRevisions(history).entries.slice().sort((a, b) => Number(b.date) - Number(a.date));
}

function normalizeGyms(list, activeId) {
    const arr = (Array.isArray(list) ? list : []).filter(g => g && typeof g === "object").map((g, i) => ({
        id: typeof g.id === "string" && g.id ? g.id : `gym_${i}`,
        name: String(g.name || `Gym ${i + 1}`).slice(0, 40),
        equipment: (Array.isArray(g.equipment) ? g.equipment : []).filter(e => ALL_EQUIP_IDS.includes(e)),
        // Heaviest implement available AT THIS GYM. null = no ceiling. Stored with the unit they were
        // typed in, so switching the app between kg and lb can't silently reinterpret "75" as 75 kg.
        limits: sanitizeLimits(g.limits),
        inventory: sanitizeInventory(g.inventory),
        limitUnit: g.limitUnit === "kg" || g.limitUnit === "lb" ? g.limitUnit : "kg"
    }));
    const gyms = arr.length ? arr : [
        { id: "gym_commercial", name: "Commercial gym", equipment: [...ALL_EQUIP_IDS] },
        { id: "gym_home", name: "Home gym", equipment: [...GYM_PRESETS[2].equipment] },
    ];
    const active = gyms.some(g => g.id === activeId) ? activeId : gyms[0].id;
    return { gyms, activeGymId: active };
}

const GYM_LIMIT_KEYS = [
    { key: "dumbbell", label: "Dumbbells", noun: "dumbbell", sub: "heaviest per hand", match: (eq) => eq.includes("dumbbell") },
    { key: "kettlebell", label: "Kettlebells", noun: "kettlebell", sub: "heaviest bell", match: (eq) => eq.includes("kettlebell") },
    { key: "barbell", label: "Barbell", noun: "barbell load", sub: "heaviest total load", match: (eq) => eq.includes("barbell") || eq.includes("ezbar") || eq.includes("smith") },
    { key: "machine", label: "Machines & cables", noun: "stack", sub: "heaviest stack", match: (eq) => isMachineLike(eq) },
];

const RACK_PRESETS = {
    dumbbell: [
        { name: "Commercial rack", unit: "lb", v: [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100] },
        { name: "Commercial (kg)", unit: "kg", v: [2.5, 5, 7.5, 10, 12.5, 15, 17.5, 20, 22.5, 25, 27.5, 30, 32.5, 35, 40, 45, 50] },
        { name: "Adjustable 5–52.5", unit: "lb", v: [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 52.5] },
        { name: "Adjustable 2–24 kg", unit: "kg", v: [2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24] },
    ],
    kettlebell: [
        { name: "Standard (kg)", unit: "kg", v: [8, 12, 16, 20, 24, 28, 32, 36, 40] },
        { name: "Standard (lb)", unit: "lb", v: [18, 26, 35, 44, 53, 62, 70, 79] },
    ],
    machine: [
        { name: "5 lb stack", unit: "lb", v: Array.from({ length: 40 }, (_, i) => (i + 1) * 5) },
        { name: "5 kg stack", unit: "kg", v: Array.from({ length: 30 }, (_, i) => (i + 1) * 5) },
    ]
};

function sanitizeInventory(inv) {
    const out = {};
    INVENTORY_KEYS.forEach(({ key }) => {
        const list = Array.isArray(inv && inv[key]) ? inv[key] : null;
        if (!list)
            return;
        const clean = [...new Set(list.map(Number).filter(v => Number.isFinite(v) && v > 0))].sort((a, b) => a - b);
        if (clean.length)
            out[key] = clean;
    });
    return out;
}

function sanitizeLimits(l) {
    const out = {};
    GYM_LIMIT_KEYS.forEach(({ key }) => {
        const v = l && Number(l[key]);
        if (Number.isFinite(v) && v > 0)
            out[key] = v;
    });
    return out;
}



function gymCapFor(ex, unit) {
    if (!GYM_LIMITS || !ex)
        return Infinity;
    const eq = ex.equip || [];
    if (!eq.length)
        return Infinity; // bodyweight has no implement ceiling
    // An explicit rack is authoritative: its heaviest entry IS the ceiling, so the two can never disagree.
    const rack = gymRackFor(ex, unit);
    if (rack && rack.length)
        return rack[rack.length - 1];
    const hit = GYM_LIMIT_KEYS.find(k => k.match(eq));
    const v = hit ? GYM_LIMITS.limits[hit.key] : null;
    return v > 0 ? toUnit(v, GYM_LIMITS.unit, unit) : Infinity;
}

const gymById = (gyms, id) => (gyms || []).find(g => g.id === id) || (gyms || [])[0] || null;

const EQUIP_CATS = ["Free Weights", "Bars", "Machines", "Benches & Racks", "Bands", "Bodyweight"];

const PATTERNS = [
    "horizontal-push", "incline-push", "vertical-push", "shoulder-horizontal-adduction",
    "horizontal-pull", "vertical-pull", "pullover",
    "hip-hinge", "knee-flexion", "knee-extension", "squat", "lunge", "hip-extension", "hip-abduction", "hip-adduction",
    "elbow-flexion", "elbow-flexion-neutral",
    "elbow-extension-overhead", "elbow-extension-neutral",
    "shoulder-abduction", "shoulder-horizontal-abduction", "shoulder-external-rotation", "shrug",
    "plantarflexion-straight-knee", "plantarflexion-bent-knee",
    "wrist-flexion", "wrist-extension", "wrist-deviation", "grip-static",
    "spinal-flexion", "lateral-flexion", "anti-extension", "anti-rotation", "neck", "shoulder-flexion",
    "dorsiflexion", "isometric-hold", "scapular-upward-rotation",
];

const EX_ABBREV = {
    rdl: "romanian deadlift", ohp: "overhead press", bss: "bulgarian split squat", sldl: "stiff leg deadlift",
    db: "dumbbell", bb: "barbell", ez: "ez bar", cgbp: "close grip bench", gm: "good morning",
    ghr: "glute ham raise", rfess: "rear foot elevated split squat", jm: "jm press", bor: "bent over row",
    pjr: "pull", hspu: "handstand push", ohs: "overhead squat", sldf: "stiff leg deadlift"
};

const MUSCLE_SYNONYM = {
    chest: "chest", pec: "chest", pecs: "chest", lats: "lats", lat: "lats", "upper back": "upper_back",
    back: "lats", mid_back: "upper_back", "lower back": "lower_back", spine: "lower_back", erector: "lower_back",
    shoulders: "shoulders", shoulder: "shoulders", delt: "shoulders", delts: "shoulders", deltoid: "shoulders",
    bicep: "biceps", biceps: "biceps", bis: "biceps", tricep: "triceps", triceps: "triceps", tris: "triceps",
    quad: "quads", quads: "quads", thigh: "quads", ham: "hamstrings", hams: "hamstrings", hamstring: "hamstrings",
    glute: "glutes", glutes: "glutes", butt: "glutes", calf: "calves", calves: "calves",
    abs: "abs", ab: "abs", core: "abs", trap: "traps", traps: "traps", forearm: "forearms", forearms: "forearms",
    abductor: "abductors", adductor: "adductors"
};

function exMatches(ex, query) {
    const ql = query.trim().toLowerCase();
    if (!ql)
        return true;
    const name = ex.name.toLowerCase();
    const part = ex.part;
    const tokens = ql.split(/\s+/).filter(Boolean);
    return tokens.every(tok => {
        if (MUSCLE_SYNONYM[tok] && MUSCLE_SYNONYM[tok] === part)
            return true;
        const expanded = EX_ABBREV[tok];
        if (expanded && expanded.split(/\s+/).every(w => name.includes(w)))
            return true;
        if (name.includes(tok))
            return true;
        if (part.replace("_", " ").includes(tok))
            return true;
        return false;
    });
}

const ASSIST_IDS = new Set(["assisted-pullup", "assisted-chinup", "assisted-dip"]);

const isAssistedEx = (ex) => !!ex && (ASSIST_IDS.has(ex.id) || /^assisted-/.test(ex.id));

const WEIGHTED_SWAP = {
    "assisted-pullup": "pullup", "bw-pullup": "pullup", "assisted-chinup": "chinup",
    "assisted-dip": "dips-chest", "dips-tri": "dips-chest",
    "pushup": "dips-chest", "diamond-pushup": "cgbp", "bench-dip": "dips-tri",
    "bw-squat": "goblet", "bw-lunge": "walking-lunge", "bw-bulgarian": "bulgarian",
    "inv-row": "bb-row", "pike-pushup": "db-shoulder"
};

const NOVICE_INELIGIBLE_SPLITS = new Set(["arnold", "bro", "ppl", "ppla", "phat", "glute_focus", "sbd_power", "five_three_one", "jt", "rippler"]);

const TEMPLATE_CATS = ["Strength", "Powerbuilding", "Hypertrophy", "Specialization", "Tactical"];

const INTENT_FROM_CAT = { Hypertrophy: "Hypertrophy", Powerbuilding: "Powerbuilding", Strength: "Strength", Tactical: "Tactical" };

function templateIntent(t) {
    if (t.cfg.focus && Object.keys(t.cfg.focus).length)
        return "Specialization";
    if (INTENT_FROM_CAT[t.cat])
        return INTENT_FROM_CAT[t.cat];
    const g = t.cfg.goal;
    return g === "strength" ? "Strength" : g === "hypertrophy" ? "Hypertrophy" : "Powerbuilding";
}

const templateConfig = (t, equipment) => ({
    name: t.name, experience: "intermediate", goal: "hypertrophy", days: 4, split: "full_body", session: "s60",
    focus: {}, focusList: [], reduce: [], progression: "auto", weeks: 4, barbellCap: null, deload: true, percentScheme: null, assistance: null,
    ...t.cfg, equipment: t.cfg.equipment || equipment, // home templates force their own minimal kit
});

const GENERATION_ROUTE = "pursuit-next-only";

const uid = () => Math.random().toString(36).slice(2, 9);

function availableFor(part, equipSet, banned, noBw = false) {
    return EXERCISES.filter(e => e.part === part && !banned.includes(e.id) && e.equip.every(q => equipSet.has(q)) && (e.equip.length > 0 || !noBw));
}

const TECHNICAL_LIFTS = new Set([
    "good-morning", "zercher-squat", "snatch-deadlift", "deficit-barbell-deadlift",
    "jefferson-curl", "jefferson-squat", "pistol-squat", "z-press", "bradford-press",
    "power-shrug", "pendlay-row", "meadows-row", "ghr", "copenhagen",
]);

function swapOverlapNames(candidate, peers = [], program = null, day = null) {
    const def = NEXT_EXERCISE_MAP.get(nextExerciseIdForShellExercise(candidate));
    if (!def)
        return [];
    const role = def.flags.compound ? "hypertrophy_compound" : "hypertrophy_isolation";
    return peers.filter(Boolean).filter(peer => {
        const pd = NEXT_EXERCISE_MAP.get(nextExerciseIdForShellExercise(peer));
        if (!pd)
            return false;
        const slot = day?.exercises?.indexOf(peer.id);
        const peerRole = slot >= 0 ? (getNextShellCell(program, day, slot, 1)?.role ?? program?.overrides?.[`${day.id}:${slot}`]?.role) : null;
        return avoidableExerciseOverlap(def, role, [{ def: pd, role: peerRole || (pd.flags.compound ? "hypertrophy_compound" : "hypertrophy_isolation") }], { priority: "normal" });
    }).map(peer => peer.name);
}

function rankSwapAlts(cur, pool, starved = null, peers = [], program = null, day = null) {
    const curPat = movePattern(cur);
    const overlap = new Map(pool.map(ex => [ex.id, swapOverlapNames(ex, peers, program, day).length]));
    const fills = (ex) => (starved && starved.size && starved.has(subRegionOf(ex)) && subRegionOf(ex) !== subRegionOf(cur)) ? 0 : 1;
    return [...pool].sort((a, b) => {
        const redundancy = overlap.get(a.id) - overlap.get(b.id);
        if (redundancy)
            return redundancy;
        const fa = fills(a) - fills(b);
        if (fa)
            return fa;
        const pa = (movePattern(a) === curPat ? 0 : 1) - (movePattern(b) === curPat ? 0 : 1);
        if (pa)
            return pa;
        const ta = (a.type === cur.type ? 0 : 1) - (b.type === cur.type ? 0 : 1);
        if (ta)
            return ta;
        return (a.pri || 0) - (b.pri || 0);
    });
}

function starvedRegions(program) {
    const out = new Set();
    {
        const sv = weeklySubVolume(program, weeksOf(program));
        for (const [region, L] of Object.entries(SUB_LANDMARKS))
            if (L?.mev && (sv[region] || 0) < L.mev)
                out.add(region);
    }
    return out;
}

function programChangeLabels(before, after) {
    const changed = key => JSON.stringify(before?.[key]) !== JSON.stringify(after?.[key]);
    const labels = [];
    if (changed("name"))
        labels.push("Program name");
    if (changed("days"))
        labels.push("Training days, exercises or order");
    if (["overrides", "slotBias", "nextWeekPrescriptions", "progStyle"].some(changed))
        labels.push("Set, rep or effort targets");
    if (changed("ss"))
        labels.push("Supersets");
    if (["schedule", "weekPlan", "scheduleBase"].some(changed))
        labels.push("Schedule");
    if (["config", "weeks", "nextEngine"].some(changed))
        labels.push("Program settings or generated plan");
    if (["folder", "description"].some(changed))
        labels.push("Folder or description");
    return labels.length ? labels : ["Program details"];
}

function canUndoProgramSave(transaction, saved, drafts, working) {
    return !!transaction?.changes?.length && transaction.changes.every(({ after }) => {
        const current = saved.find(p => p.id === after.id);
        return sameProgramContent(current, after)
            && (!drafts[after.id] || sameProgramContent(drafts[after.id], after))
            && (working?.id !== after.id || sameProgramContent(working, after));
    });
}

function backupRecordImpact(before, after) {
    return [["saved", "Programs"], ["history", "Workouts"], ["cycles", "Cycles"], ["custom", "Exercises"], ["gyms", "Gyms"]].map(([key, label]) => {
        const old = new Map((before[key] || []).map(r => [r.id, r]));
        const next = new Map((after[key] || []).map(r => [r.id, r]));
        let added = 0, updated = 0, kept = 0;
        next.forEach((r, id) => { if (!old.has(id))
            added++;
        else if (!sameProgramContent(old.get(id), r))
            updated++;
        else
            kept++; });
        const removed = [...old.keys()].filter(id => !next.has(id)).length;
        return { key, label, added, updated, kept, removed, total: next.size };
    });
}

const SET_ROW_BLEED = 16;

const REDUCE_FRACTION = 0.6;

const EX_FAMILY = {
    // triceps pushdowns (cable, vertical pressdown — swap the handle)
    "pushdown": "tri-pushdown", "rope-pushdown": "tri-pushdown", "ez-pushdown": "tri-pushdown",
    "v-bar-pushdown": "tri-pushdown", "straight-bar-pushdown": "tri-pushdown",
    "dual-rope-pushdown": "tri-pushdown", "single-pushdown": "tri-pushdown", "underhand-pushdown": "tri-pushdown",
    // triceps overhead extensions
    "oh-cable-ext": "tri-ohext", "db-oh-ext": "tri-ohext", "ez-oh-ext": "tri-ohext", "bands-tricep-ext": "tri-ohext",
    // triceps kickbacks
    "tricep-kickback": "tri-kickback", "cable-tri-kickback": "tri-kickback",
    // side-delt lateral raises
    "lat-raise": "side-raise", "cable-lat-raise": "side-raise", "machine-lat-raise": "side-raise",
    "leaning-lat-raise": "side-raise", "single-cable-raise": "side-raise",
    "seated-db-lat-raise": "side-raise", "single-db-lat-raise": "side-raise", "chest-supported-lat-raise": "side-raise",
    "seated-cable-lat-raise": "side-raise", "cuff-cable-lat-raise": "side-raise",
    "band-lateral-raise": "side-raise", "side-lying-raise": "side-raise", "cable-behind-back-lateral": "side-raise",
    // rear-delt flyes
    "rear-fly": "rear-delt-fly", "reverse-pec": "rear-delt-fly", "cable-rear-fly": "rear-delt-fly",
    "seated-rear-fly": "rear-delt-fly", "single-cable-rear-fly": "rear-delt-fly", "chest-supported-rear-fly": "rear-delt-fly", "prone-rear-delt-raise": "rear-delt-fly",
    // chest flyes (flat)
    "cable-fly": "chest-fly", "pec-deck": "chest-fly", "db-fly": "chest-fly",
    // glute kickbacks
    "cable-kickback": "glute-kickback", "glute-kickback-machine": "glute-kickback"
};

function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}

function historyForProgram(history, program) {
    const id = program?.id;
    return (Array.isArray(history) ? history : []).filter(h => h && (!h.programId || !id || h.programId === id));
}

function decodeGallery(data) {
    const raw = Array.isArray(data) ? data : (data && Array.isArray(data.programs) ? data.programs : []);
    return raw.map(e => {
        if (!e || typeof e.code !== "string")
            return null;
        const d = decodeProgramCode(e.code);
        if (!d.ok)
            return null;
        return {
            code: e.code,
            title: String(e.title || "Shared program").slice(0, 80),
            author: String(e.author || "").slice(0, 40),
            tags: Array.isArray(e.tags) ? e.tags.slice(0, 6).map(t => String(t).slice(0, 24)) : [],
            note: String(e.note || "").slice(0, 240),
            split: d.config.split, days: d.config.days, goal: d.config.goal,
            equipmentCount: (d.config.equipment || []).length,
            coached: (d.overrides || []).length
        };
    }).filter(Boolean);
}

function preferenceFloor(program, part) {
    /* ⚠ JUDGE A PROGRAM BY THE LANDMARKS IT WAS BUILT WITH. This took `program` already and then asked
       `landmarkOf(part)` without it, so a floor was always resolved from the legacy table — which meant
       a program generated under uniform landmarks got scored against the old per-muscle numbers. That
       is how gates/rotationquality reported "lost floors" for muscles sitting comfortably above their
       own floor: apples measured with an orange yardstick. `program.landmarks` is stamped at
       generation exactly so this question has one answer. */
    /* ⚠ ONLY DIVERGE WHEN THE PROGRAM SAYS SO. Reading `landmarkFor(part, experience, program)`
       unconditionally changed the floor for EVERY engine, because the original `landmarkOf` defaulted
       experience differently — frozen engines 27-30 all moved. A program stamped with uniform landmarks
       answers for itself; every other program keeps the exact call it always made. */
    const L = program?.landmarks?.uniform
        ? landmarkFor(part, program.config?.experience, program)
        : landmarkOf(part);
    const base = Number(L?.mev) || 0;
    if (!engHas(program.engineV, "preferenceAware"))
        return base;
    if (!PART_ORDER.includes(part) && !SUB_LANDMARKS[part])
        return 0; // unlandmarked regions have no invented floor
    const parent = ["front_delts", "side_delts", "rear_delts"].includes(part) ? "shoulders" : part;
    return (program.config?.reduce || []).includes(parent) ? Math.ceil(base * REDUCE_FRACTION) : base;
}

function exerciseAt(program, day, slot, week) {
    const alt = program.pairs?.[`${day.id}:${slot}`];
    if (!alt)
        return day.exercises[slot];
    return (Math.max(1, week) % 2 === 0) ? alt : day.exercises[slot];
}

function perWeekOf(program) {
    const n = Number(program.config?.days) || program.days.length;
    return Math.max(1, Math.min(n, program.days.length));
}

function rotationOf(program) {
    const declared = Number(program.rotation || SPLITS[program.config?.split]?.rotation || 1);
    if (Number.isFinite(declared) && declared > 1)
        return Math.round(declared);
    const alternates = Object.keys(program.pairs || {}).length > 0 || Object.keys(program.weekOff || {}).length > 0;
    return alternates ? 2 : 1;
}

function poolOf(program) {
    return (program.pool && program.pool.length) ? program.pool : program.days;
}

function daysInWeek(program, week) {
    if (rotationOf(program) <= 1)
        return program.days;
    const pool = poolOf(program), per = perWeekOf(program), n = pool.length;
    const start = ((Math.max(1, week) - 1) * per) % n;
    return Array.from({ length: per }, (_, i) => pool[(start + i) % n]);
}

const PATTERN_GROUPS = { pulling: ["lats", "upper_back", "biceps"], pushing: ["chest", "shoulders", "triceps"], legs: ["quads", "hamstrings", "glutes"] };

const PATTERN_MIN_SETS = 0.5;

function patternTrainable(parts, kit) {
    if (!kit)
        return true;
    return parts.some(m => { try {
        return availableFor(m, kit, [], false).length > 0;
    }
    catch {
        return true;
    } });
}

function distributeVolBias(program, volBias, field = "slotBias") {
    if (!volBias || !Object.keys(volBias).length) {
        if (field === "autoBias")
            program.autoBias = {};
        return;
    }
    const peak = weeksOf(program);
    const SET_CEIL = 5; // don't pile more than this onto any single exercise — that's junk volume
    // Per-session autoregulation writes a SEPARATE `autoBias` accumulator, rebuilt from scratch each
    // call, so it's applied to the DESIGNED `slotBias` baseline and can never compound (the bug where
    // merging into slotBias every session grew set counts week over week). `slotBias` itself is only
    // written by generation and the manual volume auto-fix — the program's designed distribution.
    const target = field === "autoBias" ? {} : { ...(program.slotBias || {}) };
    // Headroom math is measured against the designed baseline only — never the ephemeral autoBias.
    const baseProg = { ...program, autoBias: undefined };
    const baseVol = weeklyVolume(baseProg, peak); // designed per-muscle weekly volume, for the MRV headroom guard
    PART_ORDER.forEach(part => {
        let delta = volBias[part] || 0;
        if (!delta)
            return;
        const slots = [];
        program.days.forEach(d => d.exercises.forEach((id, si) => {
            if (EX_BY_ID[id]?.part === part) {
                const cur = Number(computeCell(baseProg, d, id, si, peak).sets) || 0;
                slots.push({ key: `${d.id}:${si}`, comp: EX_BY_ID[id]?.type === "compound", cur });
            }
        }));
        if (!slots.length)
            return;
        if (delta > 0) {
            // MRV headroom guard: feedback/performance can only ADD volume up to a muscle's max recoverable
            // volume — never past it. This is what keeps a good-recovery signal from pushing a muscle into
            // junk/unrecoverable volume ("add only when there's headroom below MRV").
            const headroom = Math.max(0, Math.floor(compositeMrv(part) - (baseVol[part] || 0)));
            delta = Math.min(delta, headroom);
            if (delta <= 0)
                return;
            // ADD: round-robin one set at a time onto the slots with the most headroom, isolation-first
            // (don't load a heavy compound past its productive set count), until the delta is spent or
            // every slot hits the per-exercise ceiling. This actually delivers all `need` sets instead of
            // one-per-slot, so a muscle with a single isolation can still climb several sets toward MEV.
            slots.sort((a, b) => (a.comp === b.comp ? 0 : a.comp ? 1 : -1)); // isolation first for added volume
            let guard = 0;
            while (delta > 0 && guard++ < 60) {
                // pick the slot with the lowest projected total (cur + already-added bias) under the ceiling
                let best = null;
                for (const s of slots) {
                    const projected = s.cur + (target[s.key] || 0);
                    if (projected >= SET_CEIL)
                        continue;
                    if (!best || projected < best.projected)
                        best = { ...s, projected };
                }
                if (!best)
                    break; // everything at ceiling
                target[best.key] = (target[best.key] || 0) + 1;
                delta--;
            }
        }
        else {
            // REMOVE: peel sets off the highest-volume slots first, never below 1 working set
            slots.sort((a, b) => (a.comp === b.comp ? 0 : a.comp ? -1 : 1)); // compounds last to shed
            let rem = -delta, guard = 0;
            while (rem > 0 && guard++ < 60) {
                let best = null;
                for (const s of slots) {
                    const projected = s.cur + (target[s.key] || 0);
                    if (projected <= 1)
                        continue; // keep at least one working set
                    if (!best || projected > best.projected)
                        best = { ...s, projected };
                }
                if (!best)
                    break;
                target[best.key] = (target[best.key] || 0) - 1;
                rem--;
            }
        }
    });
    if (field === "autoBias")
        program.autoBias = target;
    else if (Object.keys(target).length) {
        program.slotBias = target;
        program.config = { ...program.config, autoVolume: true };
    }
}

function repRange(goal, ex, isPrimary, phase) {
    const [lo, hi] = ex.rep;
    const strength = goal === "strength" || (goal === "both" && isPrimary);
    if (!strength)
        return [lo, hi];
    // Isolation (single-joint) lifts don't get loaded into low-rep strength territory even in a
    // strength block: heavy 3–6 rep curls, flyes, or lateral raises are awkward, joint-stressful,
    // and build no meaningful strength the way compounds do — their job is hypertrophy volume. Keep
    // them in their natural range (trimming only the very top rep so they're not pure burnout sets).
    // Strength periodization belongs on the compounds, where it actually drives force production.
    if (ex.type === "isolation") {
        const iHi = Math.max(lo + 2, hi - 2);
        return [lo, Math.max(lo + 1, iHi)];
    }
    const sLo = clamp(Math.round(lo * 0.56), 2, Math.max(2, lo - 1));
    const sHi = clamp(Math.round(hi * 0.6), sLo + 1, hi);
    if (phase == null)
        return [sLo, sHi];
    if (phase < 0.4) { // accumulation — volume base at moderate loads
        const aLo = Math.round((sLo + lo) / 2);
        const aHi = Math.max(aLo + 1, Math.round((sHi + hi) / 2));
        return [aLo, aHi];
    }
    if (phase < 0.8)
        return [sLo, sHi]; // intensification — the classic strength zone
    const rLo = Math.max(2, sLo - 1); // realization / peak — heavy doubles-to-triples territory
    const rHi = Math.max(rLo + 1, sHi - 2);
    return [rLo, rHi];
}

function techSetTag(cue) {
    if (!cue)
        return null;
    if (cue.includes("lengthened partial"))
        return "+ partials";
    if (cue.includes("static stretch"))
        return "+ stretch";
    if (cue.includes("myo-rep"))
        return "+ myo-reps";
    if (/drop/i.test(cue))
        return "+ drop set";
    return null;
}

function applyCustomProgramSettings(program, draft) {
    const weeks = Math.max(1, Math.min(52, Math.round(Number(draft.weeks) || weeksOf(program))));
    const { folder, description, ...config } = draft;
    return { ...program, weeks, folder: String(folder || '').trim() || undefined,
        description: description || undefined, config: { ...program.config, ...config, weeks } };
}

function lastSetEffort(cell, ex, isPrimary, weekIndex, weeks) {
    void ex;
    void isPrimary;
    void weekIndex;
    void weeks;
    const bounds = effortBounds(cell?.rir);
    const failure = !!bounds && bounds[0] === 0 && bounds[1] === 0;
    return { rir: cell?.rir ?? null, failure };
}

const REFERENCES = [
    ["Renaissance Periodization — Dr. Mike Israetel", "Weekly volume landmarks (MEV / MAV / MRV) and RIR-based set progression — the model behind this app's per-muscle volume bars and the accumulation ramp."],
    ["Stronger By Science — Greg Nuckols", "Volume- and frequency-response meta-analyses and program-design work; the ≥2×/week frequency default and flexible rep ranges come from here."],
    ["Schoenfeld et al. — meta-analyses", "Dose–response of weekly sets (2017) and training frequency (2016), and the finding that hypertrophy is similar across ~6–30 reps when sets are taken near failure."],
    ["Pelland et al. 2026 — volume & frequency dose–response (Sports Med)", "The largest analysis of its kind, and the reason this app counts an indirect set as half a set: that method predicted growth better than counting them fully or ignoring them. It also found size and strength still improving as weekly sets rise, with diminishing returns rather than a point where more becomes harmful — so the volume ceiling here is a recovery budget, not a line past which sets stop counting. Frequency mattered for strength; for size, what mattered was the total."],
    ["Refalo et al. — proximity to failure", "Sets taken to failure and sets stopped 1–2 reps short produce comparable growth, while going to failure feels meaningfully worse and costs more recovery. Strength does not require it. Hence the ≥1 RIR floor on heavy compounds, and why the harder sets here are placed on isolations where the cost of a missed rep is lowest."],
    ["Eric Helms — The Muscle & Strength Pyramids", "RIR/RPE autoregulation and the adherence-first hierarchy the defaults follow."],
    ["Block periodization — Issurin; Helms / SBS", "Accumulate → intensify → realize for strength, and the alternating hypertrophy↔strength model behind the block-plan overlay."],
    ["Method lineage", "Several templates implement training structures that were popularised by well-known coaches and communities — tiered T1/T2/T3 loading, training-max percentage waves, ramping and straight 5×5, and weekly volume/intensity undulation. The implementations here are written from published descriptions of those structures; the templates are named for what they do, and are not affiliated with or endorsed by anyone."],
    ["Fuel & recovery", "Every progression here assumes adequate calories, protein (~1.6–2.2 g/kg) and sleep. If progress stalls with no obvious training cause, audit recovery and intake first."],
];

function blockPlanFor(program) {
    const cfg = program?.config || {};
    if (cfg.percentScheme)
        return null; // 5/3/1, GZCL, Texas, etc. periodize themselves
    const weeks = weeksOf(program);
    if (weeks < 3)
        return null;
    const a = Math.max(1, Math.round(weeks / 3));
    const b = Math.max(a + 1, Math.round((2 * weeks) / 3));
    const rng = (lo, hi) => lo >= hi ? `Wk ${lo}` : `Wk ${lo}–${hi}`;
    const deloadPhase = cfg.deload ? { w: `Wk ${weeks + 1}`, label: "Deload", detail: "Roughly half the working sets, ~15–20% lighter, nothing under 4 RIR. The point is recovery, not progress — volume sensitivity resets so the next block can start a touch heavier." } : null;
    if (cfg.goal === "strength") {
        return {
            name: "Block periodization — accumulate → intensify → realize",
            src: "Issurin's block model; volume-to-intensity trade per Stronger By Science / Helms",
            phases: [
                { w: rng(1, a), label: "Accumulation", detail: "Compounds at moderate loads; start around 3 RIR and add load (or a set) each week. Build a base and groove technique — resist maxing out early." },
                { w: rng(a + 1, b), label: "Intensification", detail: "Loads climb, reps drop, RIR falls to 1–2. Bar speed and clean technique are the KPIs; trim isolation volume so the heavy work recovers." },
                { w: rng(b + 1, weeks), label: "Realization · peak", detail: "Heaviest loads, lowest reps, near-limit effort. Accessories sit at maintenance. This is where the strength you built gets expressed." },
                ...(deloadPhase ? [deloadPhase] : []),
            ]
        };
    }
    if (cfg.goal === "both") {
        return {
            name: "Powerbuilding block — build size, then express it",
            src: "Alternating hypertrophy ↔ strength emphasis (Helms / Stronger By Science)",
            phases: [
                { w: rng(1, a), label: "Hypertrophy base", detail: "Moderate reps near 3 RIR; accumulate volume and quality reps. Add a set to a lagging muscle when recovery allows." },
                { w: rng(a + 1, b), label: "Build", detail: "Keep the volume but push load on the main lifts; RIR drifts toward 1–2. Size from the first block starts turning into strength." },
                { w: rng(b + 1, weeks), label: "Intensity · peak", detail: "Heaviest compound work of the block at 0–1 RIR, accessories at maintenance. Express the strength, then reset." },
                ...(deloadPhase ? [deloadPhase] : []),
            ]
        };
    }
    // hypertrophy (default)
    return {
        name: "Hypertrophy mesocycle — MEV → MRV volume ramp",
        src: "Renaissance Periodization set-progression model (Israetel); Schoenfeld volume meta-analyses",
        phases: [
            { w: rng(1, a), label: "Base · MEV", detail: "Start near your minimum effective volume at ~3 RIR. Groove technique and log every load — this is the baseline the rest of the block builds on." },
            { w: rng(a + 1, b), label: "Ramp", detail: "Volume climbs (the generator adds sets across the block) and RIR drifts to 1–2, with small load bumps wherever reps allow. Emphasized muscles gain the most." },
            { w: rng(b + 1, weeks), label: "Overreach · MRV", detail: "Full volume at 0–1 RIR on isolation. Pumps and some DOMS are expected — you're deliberately near the top of what you can recover from." },
            ...(deloadPhase ? [deloadPhase] : []),
        ]
    };
}

function techExplain(tag) {
    const t = (tag || "").toLowerCase();
    if (t.includes("lengthened partial"))
        return "On your last set, once you can't get a full rep, keep doing partial reps in the bottom (stretched) position. Training a muscle while it's lengthened is one of the best-supported ways to drive extra growth in a lagging area.";
    if (t.includes("myo-rep"))
        return "On your last set, take it to true failure, then rest ~10–15 seconds and squeeze out a few more reps; repeat for 2–4 mini-sets. It's a time-efficient way to pack extra effective reps near failure onto a smaller muscle.";
    if (t.includes("drop") || t.includes("reduce load"))
        return "On the final working set, reach the prescribed stopping point, then reduce the load by about 20–30% and continue for another controlled set with the same technique. The lighter load extends the set without turning every set into a maximal-effort set.";
    if (t.includes("static stretch") || t.includes("loaded stretch"))
        return "After your last set, hold the working weight in the fully stretched position for about 30 seconds. Keep the position controlled and stop if it causes joint pain; the goal is extra tension in the target muscle, not forcing range.";
    if (t.includes("intro") || t.includes("ease into"))
        return "The first week of a new block is an intro: volume is pulled back so you can re-acclimate to the block's new exercises, loads and rep focus without digging a fatigue hole. Keep effort moderate and leave reps in reserve — the harder, higher-volume work ramps from next week.";
    if (t.includes("technique focus") || t.includes("ease in") || t.includes("mev"))
        return "Early in the block the loads are intentionally submaximal. Use these weeks to groove clean technique and build a base — the harder, heavier work comes later. Don't rush the weight up yet.";
    if (t.includes("add load") || t.includes("add a set"))
        return "Mid-block: you've established a base, so now progressively add load (and volume, for hypertrophy) week to week while keeping form crisp. This is where most of the adaptation happens.";
    if (t.includes("near limit") || t.includes("heavy"))
        return "Late-block intensity: loads are near your working limit and reps are tough. Push the working sets close to failure with good technique. Expect this to feel hard — that's the point before the deload.";
    if (t.includes("peak"))
        return "Peak week — the heaviest loads of the block on this lift. Treat it as a test of the strength you've built, then recover.";
    if (t.includes("overreach") || t.includes("top volume"))
        return "The highest-volume week of the block. It should feel like a lot — you're deliberately overreaching so the following deload lets you bounce back stronger.";
    if (t.includes("push volume"))
        return "Keep adding working sets and/or load this week. For hypertrophy, accumulating volume across the block is the main driver — chase a bit more than last week.";
    if (t.includes("circuit"))
        return "These are circuit-style rounds: move between exercises with little rest to keep the heart rate up and pack work into less time.";
    if (t.includes("deload"))
        return "Deload week — intentionally light and low-volume. The goal is recovery, not progress: keep the bar moving fast and crisp, and resist the urge to add weight.";
    return "Follow the last-set instruction shown for this exercise, keep the reps controlled, and stop the technique if your form breaks down.";
}

const SESSION_BOUNDS = { s20: [0, 20], s40: [20, 40], s60: [40, 60], s90: [60, 90], s120: [90, 120], s120p: [120, Infinity] };

let REST_SCALE = 1;

function setRestScaleGlobal(v) { REST_SCALE = (v > 0 ? v : 1); }

function effectiveRest(goal, ex, isPrimary, restCustom, scale = REST_SCALE) {
    if (restCustom > 0)
        return Math.max(10, Math.round(restCustom)); // explicit override → honoured exactly
    return Math.max(10, Math.round(restSec(goal, ex, isPrimary) * (scale || 1)));
}

const LEGACY_EX_TYPICAL_KEYS = new Set(["s40", "s60", "s90", "s120", "s120p"]);

const SESSION_EX_TYPICAL = {
    /* ⚠ s20 WAS MISSING, AND THE FALLBACK BELOW TURNED THAT INTO A LIE ON SCREEN. With no row here,
       `SESSION_EX_TYPICAL[session] || SESSION_EX_TYPICAL.s60` handed back the SIXTY-MINUTE row, so the
       wizard advertised "~5 exercises" against "Up to 20 minutes" — more than the "~3" it promised for
       twenty-to-forty, which is the inversion that made this findable at all. The generator was never
       confused: it reads SESSIONS[].count, targets 2, and delivers 2. Only the label was wrong.
       A missing key plus a silent default is worse than a missing key: it produced a confident,
       specific, wrong number instead of an obvious blank. */
    s20: { hypertrophy: 2, strength: 2, both: 2 },
    s40: { hypertrophy: 3, strength: 3, both: 3 }, // v628: engine 15 prices transitions, three fit in forty minutes (measured 2.7 across 66 days)
    s60: { hypertrophy: 5, strength: 5, both: 5 },
    s90: { hypertrophy: 7, strength: 6, both: 6 },
    s120: { hypertrophy: 9, strength: 8, both: 8 },
    s120p: { hypertrophy: 11, strength: 10, both: 11 }, // v637: measured 10.8 / 9.8 / 10.6 across 66 days each; strength fills less because its days are fewer, bigger lifts
};

function sessionExercisePlan(session, goal = "hypertrophy", _experience = "intermediate", eng = ENGINE_V) {
    const [lo, hi] = SESSION_BOUNDS[session] || [0, 60];
    const row = (engLacks(eng, "shortSessionRows") && !LEGACY_EX_TYPICAL_KEYS.has(session) ? null : SESSION_EX_TYPICAL[session]) || SESSION_EX_TYPICAL.s60;
    let count = row[goal] ?? row.hypertrophy;
    /* Engine 15 prices transitions into the budget and three lifts is what forty minutes of hypertrophy
       work holds (measured 2.7 across 66 days). The row above says 3; programs minted before engine 15
       keep the 4 they were built under, because this number is also the ceiling the pattern floor may
       fill a day to (`count + 1`), so it is a generation input and stays frozen with the engine. */
    if (session === "s40" && goal === "hypertrophy" && engLacks(eng, "honestBudget"))
        count = 4;
    /* The label is a promise about what the app will build, so it moves with the engine that builds it:
       before engine 18 the long buckets were capped at 9 exercises and the row said 9. Frozen for older
       programs for the same reason the s40 row is — this count is also the ceiling the pattern floor may
       fill a day to, which makes it a generation input, not decoration. */
    if ((session === "s120p" || session === "s120") && engLacks(eng, "longSessionSlots"))
        count = Math.min(count, 9);
    return { count, lo, hi };
}

function restSec(goal, ex, isPrimary) {
    const comp = ex.type === "compound";
    const strength = goal === "strength" || (goal === "both" && isPrimary);
    if (comp)
        return strength ? 180 : 135;
    return strength ? 105 : 75;
}

const WARMUP_SET_SEC = 60;

function exerciseSlotSec(ex, { sets, goal, isPrimary = false, linked = false, restCustom = 0, scale = REST_SCALE, withTransitions = false }) {
    if (!ex)
        return 0;
    /* The warm-up is charged even when sets is 0, because estimateMinutes has always charged it and
       this helper exists to be exactly what estimateMinutes was. The FITTER's own cost function
       deliberately returns 0 for a dropped slot instead; that is a real difference between the two,
       and it is one of the reasons the fitter still prices separately. Do not "unify" that away
       without re-running gates/fitfloor.mjs — it moves generated output. */
    const rest = linked ? 30 : effectiveRest(goal, ex, isPrimary, restCustom, scale);
    /* CHANGING EXERCISE COSTS TIME, and this model priced it at zero.
       Sets, rest and the warm-up ramp were all charged; walking to the next station, waiting for it,
       collecting dumbbells, and loading the first working weight were not — so the estimate measured
       a lifter alone in a perfect gym. Reported by a lifter whose 90-120 minute sessions the app was
       calling ~78: short by roughly a quarter, consistently, which is the shape of a fixed per-
       exercise cost rather than a rest-length error.

       A compound costs more than an isolation here for the same reason it costs more to warm up: a
       barbell needs loading and a rack needs claiming, where a cable stack needs a pin moved. A
       SUPERSETTED slot pays almost nothing — the whole point is that you are already standing at both
       stations, which is why `linked` collapses the rest above and the transition here too.

       This raises every estimate, which matters because the generator treats session length as a hard
       constraint: days that used to read as under their band now read inside it, which is the correct
       answer for the lifter who reported it, and it removes the pressure to pad a two-hour session
       with exercises it never had time for. */
    /* OFF BY DEFAULT, and that is the important part. Session length is a HARD CONSTRAINT in the
       generator: charge it for transitions and it fits fewer exercises, which measured as 14,175 sets
       lost and seven ceilings rising — days that already fill their band would have been trimmed
       below it. The lifter who reported this has programs that already run 90-120 minutes; the volume
       was right and only the NUMBER SHOWN was wrong. So the generator keeps budgeting with the model
       it was tuned against, and the figures a human reads are billed for the gym they train in. */
    const transition = withTransitions ? (linked ? 15 : (ex.type === "compound" ? 105 : 60)) : 0;
    return warmupCount(ex, isPrimary) * WARMUP_SET_SEC + (sets || 0) * (45 + rest) + transition;
}

const GAP_FRACTION = 0.5;

const COVERED_MUSCLES = new Set(["side_delts", "rear_delts", "chest", "lats", "upper_back", "shoulders",
    "quads", "hamstrings", "glutes", "biceps", "triceps", "calves", "abs"]);

function coverageRelief(program) {
    const gaps = coverageGaps(program);
    if (!gaps.length)
        return null;
    const cfg = program && program.config;
    if (!cfg)
        return null;
    const sessionIds = SESSIONS.map(x => x.id);
    const si = sessionIds.indexOf(cfg.session);
    const splitDays = (SPLITS[cfg.split] && SPLITS[cfg.split].days) || [];
    const nextDays = splitDays.filter(d => d > (cfg.days || 0)).sort((a, b) => a - b)[0];
    const CANDIDATES = [
        si >= 0 && si < sessionIds.length - 1
            ? { id: "session", label: `a ${(SESSIONS[si + 1].label || "").toLowerCase()} session`, patch: { session: sessionIds[si + 1] } }
            : null,
        nextDays ? { id: "days", label: `${nextDays} training days a week`, patch: { days: nextDays } } : null,
    ].filter(Boolean);
    const before = gaps.length;
    let best = null;
    for (const c of CANDIDATES) {
        let trial;
        try {
            trial = generateNextProgramForShell({ config: { ...cfg, ...c.patch }, banned: [], legacyExercises: EXERCISES, seed: program.seed }).program;
        }
        catch {
            continue;
        }
        const left = coverageGaps(trial).length;
        const closed = before - left;
        /* The PATCH travels with the answer. A caller that had to parse the human label back into a
           config change would be reading prose as an API — brittle, and it made the first version of
           gates/relief.mjs fail on its own regex rather than on behaviour. */
        if (closed > 0 && (!best || closed > best.closed))
            best = { ...c, closed, left, before };
    }
    return best; // null when no single change closes anything — see the header
}

function coverageGaps(program, strict = false) {
    const WATCHED = COVERED_MUSCLES; // see the definition — one owner, shared with programQuality
    const out = [];
    try {
        const sv = weeklySubVolume(program, weeksOf(program));
        const wv = weeklyVolume(program, weeksOf(program));
        for (const [region, L] of Object.entries(SUB_LANDMARKS)) {
            if (WATCHED.has(region) && L?.mev > 0 && (sv[region] || 0) < preferenceFloor(program, region) * GAP_FRACTION)
                out.push({ id: region, label: SUBMUSCLE_LABEL[region] || region });
        }
        for (const part of PART_ORDER) {
            const L = landmarkFor(part);
            if (WATCHED.has(part) && L?.mev > 0 && (wv[part] || 0) < preferenceFloor(program, part) * GAP_FRACTION && !out.some(o => o.id === part)) { // same threshold as the heads above
                out.push({ id: part, label: PART_LABEL[part] || part });
            }
        }
    }
    catch (e) {
        /* This catch previously hid a ReferenceError — a mistyped label map made the function return an
           empty array, which is indistinguishable from "this program covers everything". A silent catch
           around a whole computation turns a crash into a false clean bill of health, which is the worse
           failure. Re-thrown in test builds so a gate sees it; still swallowed in the app, where a
           missing notice must never take the screen down. */
        if (strict || (typeof process !== "undefined" && process.env && process.env.NODE_ENV === "test"))
            throw e;
        return [];
    }
    return out;
}

function completionRate(history = []) {
    const rates = [];
    let abandoned = 0, counted = 0;
    for (const h of history || []) {
        const done = Number(h.setsDone) || 0, total = Number(h.totalSets) || 0;
        if (total < 3)
            continue; // too small to read anything from
        counted++;
        const r = done / total;
        if (r < 0.25) {
            abandoned++;
            continue;
        } // an abandoned session, not a short one
        rates.push(Math.min(1, r));
    }
    if (rates.length < 6)
        return { rate: 1, n: rates.length, abandoned, counted, known: false, short: false };
    rates.sort((a, b) => a - b);
    const mid = rates.length % 2 ? rates[(rates.length - 1) / 2]
        : (rates[rates.length / 2 - 1] + rates[rates.length / 2]) / 2;
    /* MEDIAN, not mean: one session cut short by a fire alarm should not move the reading. Same reason
       paceFactor takes a median of its ratios. */
    return {
        rate: mid, n: rates.length, abandoned, counted, known: true,
        /* The actionable threshold. Leaving roughly one set in eight undone, session after session, is a
           plan that does not fit the lifter's day — not a lifter who lacks discipline, and the app should
           offer to shorten the session rather than quietly prescribe more volume to make up for it. */
        short: mid < 0.88
    };
}

function paceFactor(history = [], saved = []) {
    const byId = new Map(saved.map(p => [p.id, p]));
    const ratios = [];
    for (const h of history) {
        const p = byId.get(h.programId);
        const est = h.estMin || (p && p.days ? (() => {
            const d = p.days.find(x => x.id === h.dayId);
            try {
                return d ? estimateMinutes(p, d, h.weekIndex || 1) : 0;
            }
            catch {
                return 0;
            }
        })() : 0);
        const actual = Number(h.durationMin) || 0;
        /* Only sessions that were actually completed and plausibly timed: a session abandoned after two
           sets, or one whose clock ran overnight, says nothing about pace. */
        if (est >= 15 && actual >= 15 && actual <= est * 3 && (h.setsDone || 0) >= (h.totalSets || 1) * 0.6) {
            ratios.push(actual / est);
        }
    }
    if (ratios.length < 4)
        return { factor: 1, n: ratios.length, known: false };
    ratios.sort((a, b) => a - b);
    const mid = ratios.length % 2 ? ratios[(ratios.length - 1) / 2]
        : (ratios[ratios.length / 2 - 1] + ratios[ratios.length / 2]) / 2;
    return { factor: Math.min(1.6, Math.max(0.7, mid)), n: ratios.length, known: true };
}

function addedSeconds(program, day, weekIndex, withTransitions) {
    let sec = 0;
    (day.exercises || []).forEach((id, slot) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        if (!program.overrides?.[`${day.id}:${slot}`]?.added)
            return;
        sec += exerciseSlotSec(ex, {
            sets: Number(computeCell(program, day, id, slot, weekIndex).sets) || 0,
            goal: goalForDay(program, day), isPrimary: slot === day.primaryIndex,
            linked: !program.config?.noSupersets && !!program.ss?.[`${day.id}:${slot}`],
            restCustom: program.overrides?.[`${day.id}:${slot}`]?.rest,
            withTransitions
        });
    });
    return sec;
}

function addedMinutes(program, day, weekIndex) { return Math.round(addedSeconds(program, day, weekIndex, true) / 60); }

function daySeconds(program, day, weekIndex, withTransitions) {
    let sec = 0;
    (day.exercises || []).forEach((_id, slot) => {
        const id = exerciseAt(program, day, slot, weekIndex);
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        sec += exerciseSlotSec(ex, {
            sets: Number(computeCell(program, day, id, slot, weekIndex).sets) || 0,
            goal: goalForDay(program, day), isPrimary: slot === day.primaryIndex,
            linked: !program.config?.noSupersets && !!program.ss?.[`${day.id}:${slot}`],
            restCustom: program.overrides?.[`${day.id}:${slot}`]?.rest,
            withTransitions
        });
    });
    return sec;
}

const budgetIncludesTransitions = (program) => engHas(program.engineV || 1, "honestBudget");

function estimateMinutesFor(program, day, weekIndex, pace) {
    return Math.round((daySeconds(program, day, weekIndex, true) / 60) * ((pace && pace.factor) || 1));
}

function estimateMinutes(program, day, weekIndex) {
    return Math.round(daySeconds(program, day, weekIndex, budgetIncludesTransitions(program)) / 60);
}

const STRETCH_FOCUS_E2 = new Set([
    // chest — deep stretch under load
    "db-fly", "cable-fly", "inc-cable-fly", "high-cable-fly", "cable-crossover", "dips-chest",
    // lats — overhead / pullover stretch
    "db-pullover", "floor-db-pullover", "bent-db-pullover",
    // triceps — overhead long-head stretch
    "oh-cable-ext", "db-oh-ext", "ez-oh-ext", "skullcrusher",
    // biceps — incline / behind-body stretch
    "inc-curl", "bayesian-curl", "incline-hammer",
    // hamstrings — hip hinge at length + seated curl + eccentric
    "rdl", "db-rdl", "slrdl", "stiff-deadlift", "good-morning", "seated-curl", "nordic", "ghr",
    // quads — deep knee flexion
    "sissy-squat", "hack-squat", "pendulum-squat", "bulgarian", "split-squat",
    // side delts — cable behind the body
    "leaning-lat-raise",
    // abs — loaded stretch at the top
    "cable-crunch",
    // calves — straight-knee gastroc stretch
    "standing-calf", "donkey-calf", "leg-press-calf",
]);

const STRETCH_FOCUS_E3 = new Set([
    ...STRETCH_FOCUS_E2,
    // chest — dumbbell pressing reaches a deeper bottom than the barbell (no bar to stop the descent)
    "db-bench", "inc-db-press", "low-incline-db", "db-incline-fly", "bodyweight-deficit-pushup",
    // lats — straight-arm pulldown loads the fully-lengthened lat; cable/machine pullovers likewise
    "straight-pulldown", "cable-pullover", "machine-pullover",
    // side delts — behind-the-back cable + side-lying put the delt under load at length
    "cable-behind-back-lateral", "side-lying-raise",
    // triceps — lying extensions and the skullover/PJR family travel back overhead → long head at length
    "lying-db-ext", "barbell-skullover", "pjr-pullover",
    // glutes — deep hip flexion under load (NOT thrusts, which peak shortened)
    "sumo-squat", "step-through-lunge",
]);

const stretchTable = (eng) => (engHas(eng, "stretchOpener") ? STRETCH_FOCUS_E3 : STRETCH_FOCUS_E2);

const isStretchFocus = (id, eng = 2) => stretchTable(eng).has(id);

const MACHINE_SETUP = {
    // pressing and pulling: fit the seat, then the pad you brace against
    machinepress: ["seat", "back", "rom"], // chest press: seat height, back pad, handle start depth
    machineshoulder: ["seat", "back"],
    machinerow: ["seat", "back", "rom"], // chest-supported row: chest pad fore/aft + handle start
    machinepullover: ["seat", "back", "rom"],
    machineext: ["seat", "back"],
    machinecurl: ["seat", "arm"], // preacher-style elbow pad — not a backrest, not a thigh pad
    machinelatraise: ["seat", "rom"], // seat height sets shoulder-to-pivot; arm pads have a start
    pecdeck: ["seat", "rom"], // seat height + arm-pad start; the pad is a chest rest, not adjustable
    machineshrug: ["seat"],
    machinecrunch: ["seat", "pad", "rom"], // seat, leg restraint, range stop
    // legs
    legpress: ["seat", "back"], // backrest angle (45°) or seat carriage (horizontal)
    hacksquat: ["shoulder"], // you stand in it — shoulder pad height is the fit
    beltsquat: ["belt"], // belt height / lever position
    legext: ["seat", "back", "pad", "ankle", "rom"],
    hipthrustmachine: ["seat", "pad"], // seat/back carriage + the lap pad
    kickback: ["pad", "rom"], // hip pad height + start position
    abduction: ["seat", "back", "rom"], // the start-width lever is a real, distinct setting
    adduction: ["seat", "back", "rom"],
    // bodyweight assist
    assisted: ["knee"], // knee/foot platform — no seat, no backrest
    // legacy coarse id: users who never re-picked equipment still carry it
    machine: ["seat", "back"]
};

const SETUP_FIELD_ORDER = ["seat", "back", "shoulder", "hip", "knee", "belt", "arm", "pad", "ankle", "rom", "pulley", "bench", "hooks", "safety"];

function setupFieldsFor(ex) {
    const equip = new Set(ex.equip || []);
    const name = (ex.name || "").toLowerCase();
    const floorStart = ex.id.includes("deadlift");
    const keys = new Set();
    for (const id of equip)
        (MACHINE_SETUP[id] || []).forEach(k => keys.add(k));
    // --- leg curl: posture decides. Seated has a backrest to set against; lying and standing don't.
    if (equip.has("legcurl")) {
        const lying = /\blying\b|\bprone\b/.test(name), standing = /\bstanding\b/.test(name);
        if (!lying && !standing) {
            keys.add("seat");
            keys.add("back");
        }
        ["pad", "ankle", "rom"].forEach(k => keys.add(k));
    }
    // --- calf: standing sets shoulder-pad height; seated/donkey load the thigh or hips instead.
    if (equip.has("calfmachine")) {
        if (/\bstanding\b/.test(name))
            keys.add("shoulder");
        else if (/\bdonkey\b/.test(name))
            keys.add("hip"); // you lean under a hip pad, bent at the waist
        else {
            keys.add("seat");
            keys.add("pad");
        }
    }
    /* --- cable: the STATION decides, not the fact that it's a stack. A pulldown seats you under the
       bar with a thigh pad; a seated row seats you at a low pulley; everything else — pushdowns, curls,
       flies, face pulls, kickbacks, woodchoppers — you stand or kneel for, and the only thing to set is
       pulley height. That last case is most of them, which is exactly what the old blanket rule got
       wrong on every one. */
    if (equip.has("cable")) {
        keys.add("pulley");
        /* A straight-arm pulldown is a STANDING movement that happens to have "pulldown" in its name —
           matching the word alone gave it a seat and a thigh pad it has never had. */
        const straightArm = /straight/.test(ex.id) || /straight-arm|straight arm/.test(name);
        if (!straightArm && (/pulldown|pull-down/.test(ex.id) || /pulldown/.test(name))) {
            keys.add("seat");
            keys.add("pad");
        }
        else if (/\bseated\b/.test(name))
            keys.add("seat");
    }
    if (equip.has("bench"))
        keys.add("bench");
    // Smith and barbell are rack hardware: set the hooks and the safeties. Floor-start pulls have neither.
    if ((isBarLike(ex.equip) || equip.has("smith")) && !floorStart) {
        keys.add("hooks");
        keys.add("safety");
    }
    return SETUP_FIELD_ORDER.filter(k => keys.has(k)).map(k => ({ key: k, label: setupLabel(k) }));
}

const SETUP_LABELS = { seat: "Seat", back: "Back pad", pad: "Thigh pad", ankle: "Ankle pad", rom: "Start position", shoulder: "Shoulder pad", hip: "Hip pad", knee: "Knee platform", belt: "Belt height", arm: "Arm pad", pulley: "Pulley", bench: "Bench", hooks: "J-hooks", safety: "Safety bars" };

const setupLabel = (k) => SETUP_LABELS[k] || k;

function setupFieldsWithStored(ex, value) {
    const derived = ex ? setupFieldsFor(ex) : [];
    const seen = new Set(derived.map(f => f.key));
    const orphans = Object.keys(value || {})
        .filter(k => !seen.has(k) && value[k])
        .map(k => ({ key: k, label: setupLabel(k), orphan: true }));
    return [...derived, ...orphans];
}

function clearSetup(id, stored, onSetExSetup) {
    Object.keys(stored || {}).forEach(k => onSetExSetup?.(id, k, ""));
}

function pickTopSet(sets, weightOf, repsOf) {
    let best = null;
    for (const s of sets || []) {
        const w = weightOf(s), r = repsOf(s);
        if (!(w > 0) || !(r > 0))
            continue;
        if (!best) {
            best = s;
            continue;
        }
        const bw = weightOf(best), br = repsOf(best);
        if (w > bw || (w === bw && r > br))
            best = s;
    }
    return best;
}

function formatSetup(setup) {
    if (!setup)
        return "";
    return Object.entries(setup).filter(([, v]) => v).map(([k, v]) => `${setupLabel(k)} ${v}`).join(" · ");
}

const EX_SPECIFIC = {
    // chest
    "bb-bench": "Tuck elbows ~75°, touch just below the nipples, keep wrists stacked over elbows.",
    "inc-bb-bench": "Set the bench ~30° — steeper just turns it into a shoulder press.",
    "low-inc-bb-bench": "Shallow ~15–20° bench — emphasizes the upper chest with less front-delt takeover.",
    "high-inc-bb-bench": "Steep ~45° bench — upper-chest and front-delt focused; expect lighter loads than a flat press.",
    "ez-pushdown": "Angled EZ bar eases wrist strain; elbows pinned, full lockout then a controlled stretch.",
    "inc-db-press": "~30° incline; let the dumbbells stretch a touch below the shoulders.",
    "db-fly": "Hug-a-barrel path with soft, fixed elbows — stretch wide, squeeze hands together.",
    "cable-fly": "Soft fixed elbows; get a deep stretch, then squeeze the hands all the way together.",
    "pec-deck": "Drive with the elbows/upper arms, not the hands; pause on the squeeze.",
    "machine-dip": "Lean the torso forward over the hands to bias the chest.",
    // lats / back
    "lat-pulldown": "Pull the bar to your collarbone with a slight lean-back; lead with the elbows.",
    "db-pullover": "Slight elbow bend; feel the lats stretch overhead — keep the hips low.",
    "bb-row": "Hinge ~45° with a flat braced back; pull to the lower ribs, no heaving.",
    "pendlay-row": "From a dead stop each rep — explosive pull to the lower chest, reset on the floor.",
    "tbar-row": "Flat back, pull the handle into your stomach, squeeze the mid-back.",
    "chest-row": "Chest pinned to the pad; let the arms hang fully, then row to the hips.",
    "seal-row": "Full hang at the bottom, row to the lower ribs, pause and squeeze.",
    "seated-row": "Tall chest, pull to the navel, control back to a full stretch — don't round.",
    // hinge
    "deadlift": "Take the slack out first, push the floor away, drag the bar up your legs.",
    "rack-pull": "Set the pins just below the knee; brace hard and push the floor away.",
    "rdl": "Push the hips back, soft knees — stop when you lose the hamstring stretch, don't round to the floor.",
    "db-rdl": "Hips back, dumbbells close to the legs; chase the stretch, not the floor.",
    "good-morning": "Hips travel back, flat braced back; light load, feel the hamstrings.",
    // shoulders
    "ohp": "Squeeze glutes, brace; push your head 'through the window' at lockout.",
    "db-shoulder": "Press in a slight arc; stop just short of clanging the dumbbells together.",
    "lat-raise": "Lead with the elbows, slight pour-the-pitcher, stop at shoulder height.",
    "cable-lat-raise": "Constant cable tension — lead with the elbow, control all the way down.",
    "rear-fly": "Lead with the elbows, think 'pull apart', pause at the back.",
    "reverse-pec": "Drive the elbows back and apart; pause on the squeeze, slow return.",
    "face-pull": "Pull to your forehead, knuckles to the ceiling, externally rotate at the end.",
    "upright-row": "Lead with the elbows to about chest height; back off if the shoulders pinch.",
    // biceps
    "bb-curl": "Pin the elbows at your sides; no swing — control the lowering.",
    "db-curl": "Supinate as you curl; keep the elbows still, slow the negative.",
    "preacher": "Don't relax at the bottom — keep tension across the full stretch.",
    "hammer": "Neutral grip throughout; control the negative — hits brachialis and forearm.",
    "inc-curl": "Let the arms hang back behind the torso for a big biceps stretch.",
    "bayesian-curl": "Step out so the cable pulls the arm behind you — stretch-loaded the whole rep.",
    // triceps
    "pushdown": "Elbows pinned to your sides; full lockout, then a controlled stretch.",
    "rope-pushdown": "Spread the rope apart at the bottom and lock out hard.",
    "skullcrusher": "Elbows still and slightly back; lower behind the head for a deeper stretch.",
    "oh-cable-ext": "Overhead position maximizes the stretch — keep the elbows in and high.",
    "cgbp": "Hands ~shoulder-width, elbows tucked, drive to a full lockout.",
    // quads / legs
    "back-squat": "Big brace, sit between the hips, knees track over the toes, drive up evenly.",
    "front-squat": "Tall chest, elbows high; let the knees travel forward over the toes.",
    "hack-squat": "Control to depth; don't let the lower back round off the pad at the bottom.",
    "leg-press": "Full but controlled depth; never let the lower back peel off the seat.",
    "bulgarian": "Most of the load through the front heel; drop straight down, not forward.",
    "split-squat": "Front heel drives; keep the torso tall and the rear knee under the hip.",
    "leg-ext": "Pause and squeeze hard at full extension; control the lowering.",
    "walking-lunge": "Step long enough to keep the front shin near-vertical; push through the heel.",
    // hamstrings / glutes
    "lying-curl": "Curl through the full range; keep the hips pinned to the pad.",
    "seated-curl": "The seated position pre-stretches the hamstring — drive through full range.",
    "hip-thrust": "Tuck the chin and ribs, drive through the heels, squeeze hard at the top.",
    "sumo-dl": "Wide stance, knees out, chest up; push the floor apart on the way up.",
    // calves
    "standing-calf": "Pause 1–2s in the bottom stretch, then a full contraction at the top.",
    "seated-calf": "Bent knee biases the soleus — slow, deep stretch on every rep.",
    "leg-press-calf": "Big stretch at the bottom, full plantarflexion at the top, no bouncing.",
    // abs
    "cable-crunch": "Round the spine down toward the knees; keep the hips fixed.",
    "pallof": "Resist the twist — brace and press straight out, return slow.",
    "machine-crunch": "Crunch through the spine, not the hips; pause on the contraction.",
    // traps / forearms
    "bb-shrug": "Straight up and down; pause at the top — no rolling the shoulders.",
    "db-shrug": "Shrug straight up to the ears, brief hold, control down.",
    "wrist-curl": "Let the bar roll to the fingertips, then curl up — slow and full range.",
    "hammer-radial-dev": "Forearm flat, neutral grip, hammer stood upright — tilt the head back toward the thumb side only.",
    "hammer-ulnar-dev": "Same setup, opposite direction — lower toward the little-finger side and control it back.",
    "cable-radial-dev": "Arm fixed at the side; move only the wrist toward the thumb. Light — the range is short.",
    "band-ulnar-dev": "Anchor low, wrist neutral; deviate toward the little finger and resist the return.",
    "farmers": "Tall posture, braced trunk, smooth steps — grip is the limiter.",
    // powerlifting competition variations
    "high-bar-squat": "Bar on the traps, upright torso, knees travel forward — quad-biased and deep.",
    "low-bar-squat": "Bar across the rear delts, more hip hinge and forward lean; sit back into the hips.",
    "paused-squat": "Hold 2–3s at the bottom dead-still, then drive — no bounce out of the hole.",
    "pin-squat": "Settle onto the pins, kill the stretch reflex, then squat it up from a dead stop.",
    "tempo-squat": "Lower over a slow 3–4s count; controlled, no bounce, then stand normally.",
    "spoto-press": "Pause an inch off the chest, hold the bar still, then press — builds raw pressing strength.",
    "larsen-press": "Feet up on the bench (or flat on the floor) — no leg drive, all chest and triceps.",
    "board-press": "Touch the board, brief pause, press — overloads the mid-range and lockout.",
    "pin-bench": "Press from a dead stop off the pins at chest height — no stretch reflex to help.",
    "pause-bench": "Touch, hold 3s motionless on the chest, then press — the competition command.",
    "paused-deadlift": "Pause 1–2s just below the knee, stay tight, then finish the pull.",
    "pin-press-ohp": "Press from a dead stop off the pins at forehead height — builds the sticking point.",
    "barbell-skullover": "A skull-crusher that travels back overhead — keep tension on the long head of the triceps."
};

function assertExerciseData() {
    const ids = new Set(EXERCISES.map(e => e.id));
    const parts = new Set(Object.keys(PART_LABEL));
    const warn = (m) => { try {
        console.warn("[data] " + m);
    }
    catch { } };
    // id-keyed objects: every KEY must be a real exercise id
    [["WEIGHTED_SWAP", WEIGHTED_SWAP], ["SECONDARY", SECONDARY], ["EX_SPECIFIC", EX_SPECIFIC], ["EX_FAMILY", EX_FAMILY]]
        .forEach(([name, obj]) => Object.keys(obj).forEach(k => { if (!ids.has(k))
        warn(`${name}: key "${k}" is not a known exercise id`); }));
    // id Sets: every member must be a real exercise id
    [["ASSIST_IDS", ASSIST_IDS], ["TECHNICAL_LIFTS", TECHNICAL_LIFTS]]
        .forEach(([name, set]) => set.forEach(k => { if (!ids.has(k))
        warn(`${name}: "${k}" is not a known exercise id`); }));
    // WEIGHTED_SWAP values are the graduate-to lift — they must resolve too
    Object.entries(WEIGHTED_SWAP).forEach(([k, v]) => { if (!ids.has(v))
        warn(`WEIGHTED_SWAP["${k}"] -> "${v}" is not a known exercise id`); });
    // SECONDARY values reference muscle parts (not ids) — validate the part names + factor shape
    Object.entries(SECONDARY).forEach(([k, list]) => (list || []).forEach(([part, f]) => {
        if (!parts.has(part))
            warn(`SECONDARY["${k}"] references unknown part "${part}"`);
        if (typeof f !== "number" || !(f >= 0))
            warn(`SECONDARY["${k}"] has a non-numeric factor for "${part}"`);
    }));
    // STRETCH_FOCUS entries must resolve to real exercises (catch a typo silently doing nothing).
    // E3 is a superset of E2, so validating it covers both tables.
    STRETCH_FOCUS_E3.forEach(id => { if (!ids.has(id))
        warn(`STRETCH_FOCUS "${id}" is not a known exercise id`); });
}

function tempoFor(ex) {
    switch (movePattern(ex)) {
        case "press":
        case "overhead":
        case "pushup":
        case "dip": return "2s down · brief pause · drive up";
        case "squat": return "2–3s down · no bounce · drive up";
        case "hinge": return "2–3s down · feel the stretch · drive up";
        case "hipthrust": return "drive up · 1s squeeze · 2s down";
        case "row":
        case "pulldown": return "pull · 1s squeeze · 2s return";
        case "fly":
        case "lateral": return "1s up · 2–3s down · no swing";
        case "curl":
        case "triceps":
        case "supinetriceps": return "1s up · squeeze · 2–3s down";
        case "legext":
        case "legcurl": return "1s up · 1s squeeze · 2s down";
        case "calf": return "1s up · 2s stretch at the bottom";
        case "ab": return "controlled crunch · 2s return";
        case "shrug": return "1s up · 1s hold · 2s down";
        default: return ex.type === "compound" ? "2s down · controlled up" : "2–3s down · controlled";
    }
}

function cuesFor(ex) {
    const spec = EX_SPECIFIC[ex.id];
    const base = patternCues(ex);
    return spec ? [spec, ...base] : base;
}

function patternCues(ex) {
    const n = ex.id, has = (...k) => k.some(s => n.includes(s));
    if (ex.part === "chest" && has("fly", "pec"))
        return ["Soft, fixed elbow angle throughout", "Stretch wide, squeeze hands toward each other", "Slow eccentric — no bouncing at the bottom"];
    if (ex.part === "chest" || (ex.part === "triceps" && has("cgbp", "dip", "pushup")))
        return ["Shoulder blades back & down, slight arch", "Elbows ~45° from torso, not flared", "Touch lower chest, drive through mid-foot"];
    if (has("ohp", "shoulder", "arnold", "pike"))
        return ["Brace abs & squeeze glutes — no excess lean", "Bar/dumbbells over mid-foot at lockout", "Press in a slight arc, head 'through' at top"];
    if (has("lat-raise", "lateral", "rear", "reverse", "face"))
        return ["Lead with the elbows, not the hands", "Slight forward lean; stop ~shoulder height", "Control the lowering for 2–3 sec"];
    if (has("pulldown", "pullup", "chinup"))
        return ["Depress & lead with the elbows", "Full stretch at the top, no swinging", "Drive elbows to the hips, squeeze lats"];
    if (ex.part === "lats")
        return ["Depress the shoulder, lead with the elbow", "Full stretch overhead, no swinging", "Drive the elbows down & in, squeeze the lats"];
    if (ex.part === "upper_back")
        return ["Hinge slightly, flat back, braced", "Pull elbows toward the hips", "Pause & squeeze the shoulder blades together"];
    if (ex.part === "lower_back")
        return ["Brace hard, neutral spine throughout", "Drive from the hips, don't round under load", "Control the range — slow, no jerking"];
    if (has("deadlift", "rdl", "good-morning", "slrdl"))
        return ["Brace hard, neutral spine, lats tight", "Keep the bar dragging close to the body", "Hips back on the way down, drive through floor"];
    if (has("squat", "leg-press", "lunge", "bulgarian", "goblet"))
        return ["Big breath, brace the core before descending", "Knees track over the toes", "Control to depth, drive up evenly"];
    if (has("hip-thrust", "glute-bridge", "kickback"))
        return ["Tuck the chin, ribs down", "Drive through the heels", "Squeeze glutes hard at the top, pause"];
    if (ex.part === "biceps" || has("curl"))
        return ["Pin the elbows, no swinging", "Full stretch at the bottom", "Squeeze at the top, slow the negative"];
    if (ex.part === "triceps")
        return ["Keep elbows tucked & still", "Lock out fully each rep", "Control the stretch, don't bounce"];
    if (ex.part === "calves")
        return ["Pause & stretch at the bottom", "Full plantarflexion at the top", "Slow, controlled — no bouncing"];
    if (ex.part === "abs")
        return ["Move through the spine, not the hips", "Exhale and crunch hard", "Control the return, keep tension"];
    if (ex.part === "neck")
        return ["Move slowly through a comfortable range", "Light load, high reps — never jerk", "Build up volume gradually over weeks"];
    if (ex.part === "adductors")
        return ["Control the stretch — feel the inner thigh lengthen", "Squeeze legs together at the top", "Slow, full range — no bouncing out of the stretch"];
    if (ex.part === "abductors")
        return ["Drive the knee out, lead with the heel", "Slight forward lean loads the glute medius", "Pause at the top, control the return"];
    if (ex.type === "compound")
        return ["Brace your core before each rep", "Full range of motion under control", "Own the eccentric — 2 sec down"];
    return ["Slow eccentric, full range of motion", "Squeeze the target muscle at peak", "Keep tension — avoid using momentum"];
}

function weeklyRecap(history, unit) {
    const now = Date.now(), wk = 7 * 86400000;
    const inWin = (h, a, b) => h.date > now - a && h.date <= now - b;
    const thisW = (history || []).filter(h => h && h.date > now - wk);
    const prevW = (history || []).filter(h => inWin(h, 2 * wk, wk));
    const sets = arr => arr.reduce((s, h) => s + (h.setsDone || 0), 0);
    const vol = arr => arr.reduce((s, h) => s + historyVolumeIn(h, unit || h?.unit), 0);
    const muscle = {};
    // Working sets, not logged rows: the weekly recap names a "top muscle", and a session with myo
    // minis on one lift used to hand that title to whatever muscle happened to run extensions.
    thisW.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => { const ex = EX_BY_ID[id]; if (!ex || !p.sets)
        return; muscle[ex.part] = (muscle[ex.part] || 0) + setsOf(p).filter(isWorkSet).length; }));
    const topMuscle = Object.entries(muscle).sort((a, b) => b[1] - a[1])[0] || null;
    const byId = {};
    (history || []).forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
        if (!(p && p.weight > 0))
            return;
        const ss = setsOf(p);
        const best = ss.length ? Math.max(...ss.map(s => e1rm(convertHistoryLoad(s.w, h.unit, unit), s.r || 1))) : e1rm(convertHistoryLoad(p.weight, h.unit, unit), p.reps || 1);
        (byId[id] = byId[id] || []).push({ date: h.date, best });
    }));
    let prs = 0;
    Object.values(byId).forEach(arr => {
        const prior = arr.filter(x => x.date <= now - wk).map(x => x.best);
        const cur = arr.filter(x => x.date > now - wk).map(x => x.best);
        if (cur.length && prior.length && Math.max(...cur) > Math.max(...prior))
            prs++;
    });
    return { count: thisW.length, sets: sets(thisW), vol: Math.round(vol(thisW)), prevCount: prevW.length, prevSets: sets(prevW), prevVol: Math.round(vol(prevW)), topMuscle, prs };
}

const __weeklyVolMemo = new WeakMap();

function __volKey(program, weekIndex) {
    const days = (program.days || []).map(d => (d.exercises || []).join(",")).join("|");
    const bias = program.slotBias ? JSON.stringify(program.slotBias) : "";
    /* ⚠ ANYTHING THAT CHANGES A SET COUNT BELONGS IN THIS KEY. The memo is keyed on exercise ids and
       slotBias; `weekOff` changes what `computeCell` returns and was invisible to it, so a slot turned
       off for a week still reported its old volume — the exact "repair pass reads stale numbers" trap
       this key already carries a warning about, hit again by a new input. */
    const off = program.weekOff ? JSON.stringify(program.weekOff) : "";
    const pair = program.pairs ? JSON.stringify(program.pairs) : "";
    return `${weekIndex}::${days}::${bias}::${off}::${pair}`;
}

function weeklyVolume(program, weekIndex) {
    if (program?.engineSource === "pursuit-next") {
        const snapshot = captureShellVolumeSnapshot(program, weekIndex, EXERCISES);
        if (snapshot) return snapshot.volume;
    }
    const key = __volKey(program, weekIndex);
    let byWeek = __weeklyVolMemo.get(program);
    if (byWeek) {
        const hit = byWeek.get(key);
        if (hit)
            return hit;
    }
    else {
        byWeek = new Map();
        __weeklyVolMemo.set(program, byWeek);
    }
    /* ⚠ TWO OWNERS FOR ONE FACT. `weeklyVolumeOf` in the generator computes the same thing and both
       iterated `program.days` independently — so teaching one about alternating weeks left the other
       reporting every day every week, and a rotating program read 7.0 in both weeks while its rotation
       mean said 5.5. `daysInWeek` is the single answer to "which days happen in week N"; both callers
       ask it. Non-rotating programs get `program.days` unchanged. */
    const map = {};
    daysInWeek(program, weekIndex).forEach(day => {
        day.exercises.forEach((_id, slot) => {
            const id = exerciseAt(program, day, slot, weekIndex);
            const ex = EX_BY_ID[id];
            if (!ex)
                return; // guard against a stale/unknown exercise id (matches weeklySubVolume)
            const sets = Number(computeCell(program, day, id, slot, weekIndex).sets) || 0;
            map[ex.part] = (map[ex.part] || 0) + sets; // direct
            secondaryOf(ex, program.engineV).forEach(([p, f]) => { map[p] = (map[p] || 0) + sets * f; }); // fractional
        });
    });
    if (byWeek.size > 64)
        byWeek.clear(); // a mutating generation pass churns keys; keep the map bounded
    byWeek.set(key, map);
    return map;
}

const fmtSets = (v) => (Math.abs(v - Math.round(v)) < 0.05 ? String(Math.round(v)) : v.toFixed(1));

function weekIntent(program, weekIndex) {
    if (!program?.days?.length)
        return null;
    const weeks = weeksOf(program);
    const isDeload = !!program.config?.deload && weekIndex > weeks;
    const p = blockPhase(program, Math.min(weekIndex, weeks), 0.5);
    /* Sample the program's actual roster: the day's primary compound and, if it has one, an isolation.
       Both come back through the same calls the session screen makes. */
    let primary = null, isoEx = null, isoDay = null, primDay = null, primSlot = -1, isoSlot = -1;
    for (const d of program.days) {
        (d.exercises || []).forEach((id, i) => {
            const ex = EX_BY_ID[id];
            if (!ex)
                return;
            if (!primary && i === d.primaryIndex) {
                primary = ex;
                primDay = d;
                primSlot = i;
            }
            if (!isoEx && ex.type === "isolation") {
                isoEx = ex;
                isoDay = d;
                isoSlot = i;
            }
        });
        if (primary && isoEx)
            break;
    }
    if (!primary)
        return null;
    /* ⚠ READ THE CELL, NOT THE RULE BEHIND IT. The first version called `rirFor` and `repRange`
       directly and was WRONG for every percentage scheme: on a 5/3/1 block it announced "4–8 reps" on
       the overhead press while `computeCell` — the function the plan tab and the session screen both
       render from — prescribed 3-6, because PCT_SCHEMES overrides the generic bracket. The RIR
       happened to agree, so a check on effort alone passed while the sentence contradicted the screen
       beside it. `computeCell` is the owner for what a week actually prescribes; ask it. */
    const wk = Math.min(weekIndex, weeks + (isDeload ? 1 : 0));
    const primCell = computeCell(program, primDay, primary.id, primSlot, wk);
    const isoCell = isoEx ? computeCell(program, isoDay, isoEx.id, isoSlot, wk) : null;
    const primRir = String(primCell.rir);
    const isoRir = isoCell ? String(isoCell.rir) : null;
    const primRange = String(primCell.range || "").split("-");
    /* The intensity technique is asked for, not assumed — `lastSetTech` owns when it appears, and it
       declines for strength work, for blocks under three weeks, and in the intro and deload weeks. */
    const focusSet = new Set(program.config?.focusList || []);
    const tech = isoEx && !isDeload
        ? lastSetTech(isoEx, goalForDay(program, isoDay) === "strength", focusSet.has(isoEx.part), p, weeks)
        : null;
    const here = plannedWeek(program, weekIndex);
    const first = plannedWeek(program, 1);
    const setDelta = (Number(here.sets) || 0) - (Number(first.sets) || 0);
    let label, detail;
    if (isDeload) {
        label = "Deload";
        /* ⚠ A DELOAD DOES NOT ALWAYS CUT SETS, AND SAYING SO WHEN IT DOES NOT IS THE EXACT FAILURE THIS
           WHOLE FUNCTION EXISTS TO AVOID. An s20 program already prescribes the two-set minimum, so its
           deload has nothing left to remove and backs off on EFFORT alone — measured on
           full_body/2/s20: 8 sets in the deload against 8 in week 1. The first draft printed "cut the
           sets — 8 against 8", which is a summary contradicting itself in the same sentence.
           gates/weekintent.mjs check 2 caught it; read the volume rather than assuming it moved. */
        detail = here.sets < first.sets
            ? `hold your loads, cut the sets — ${here.sets} against ${first.sets} in week 1 — and stop well short at ${primRir} RIR`
            : `hold your loads and your ${here.sets} sets, but stop well short at ${primRir} RIR — this week backs off on effort, not volume`;
    }
    else if (weekIndex === 1) {
        label = weeks === 1 ? "Single week" : "Introduction";
        detail = `find your loads at ${primRir} RIR on ${primary.name.toLowerCase()}, ${primRange.join("\u2013")} reps` +
            (isoRir ? `, ${isoRir} RIR on the isolation work` : "");
    }
    else if (weekIndex >= weeks) {
        label = "Peak";
        detail = `the hardest week — ${primRir} RIR on the main lifts` +
            (isoRir ? `, ${isoRir} on isolations` : "") +
            (setDelta > 0 ? `, and ${setDelta} more sets than week 1` : "");
    }
    else {
        label = p < 0.5 ? "Accumulation" : "Intensification";
        detail = `${primRir} RIR on the main lifts` +
            (isoRir ? `, ${isoRir} on isolations` : "") +
            (setDelta > 0 ? ` · ${setDelta} sets added since week 1` : setDelta < 0 ? ` · ${-setDelta} sets fewer than week 1` : "");
    }
    if (tech)
        detail += ` · ${tech.replace(/^Last set: /, "").split(" \u2014 ")[0]} on the last set of suitable isolations`;
    return { label, detail, deload: isDeload };
}

function plannedWeek(program, weekIndex) {
    let sets = 0, mins = 0;
    ((program && program.days) || []).forEach(d => {
        (d.exercises || []).forEach((id, slot) => {
            sets += Number(computeCell(program, d, id, slot, weekIndex).sets) || 0;
        });
        mins += Number(estimateMinutes(program, d, weekIndex)) || 0;
    });
    return { sets: Math.round(sets), mins: Math.round(mins) };
}

function weekMuscleBreakdown(program, weekIndex) {
    const map = {};
    ((program && program.days) || []).forEach(day => {
        dayMuscleBreakdown(program, day, weekIndex).forEach(e => {
            const t = map[e.part] || (map[e.part] = { part: e.part, sets: 0, from: [] });
            t.sets += e.sets;
            e.from.forEach(f => t.from.push({ ...f, dayLabel: day.label }));
        });
    });
    return Object.values(map).map(e => ({ ...e, from: e.from.sort((a, b) => b.contrib - a.contrib) }));
}

function dayMuscleBreakdown(program, day, weekIndex) {
    if (program?.engineSource === "pursuit-next") {
        const rows = shellDayMuscleBreakdown(program, day, weekIndex, EXERCISES);
        if (rows) return rows;
    }
    const map = {};
    ((day && day.exercises) || []).forEach((id, slot) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        const sets = Number(computeCell(program, day, id, slot, weekIndex).sets) || 0;
        if (!(sets > 0))
            return;
        const add = (part, contrib, direct, factor) => {
            const e = map[part] || (map[part] = { part, sets: 0, from: [] });
            e.sets += contrib;
            e.from.push({ id, name: ex.name, slot, sets, contrib, direct, factor });
        };
        add(ex.part, sets, true, 1);
        secondaryOf(ex, program.engineV).forEach(([pt, f]) => add(pt, sets * f, false, f));
    });
    return Object.values(map)
        .filter(e => e.sets >= 0.5)
        .map(e => ({ ...e, from: [...e.from].sort((a, b) => b.contrib - a.contrib || a.slot - b.slot) }))
        .sort((a, b) => b.sets - a.sets);
}

function dayMuscleVolume(program, day, weekIndex) {
    return dayMuscleBreakdown(program, day, weekIndex).map(e => [e.part, e.sets]);
}

function exerciseProfile(ex) {
    const muscles = { [ex.part]: 1 };
    secondaryOf(ex).forEach(([p, f]) => { muscles[p] = Math.max(muscles[p] || 0, f); });
    return {
        part: ex.part,
        type: ex.type,
        equip: ex.equip || [],
        rep: ex.rep || [],
        pattern: movePattern(ex),
        tempo: tempoFor(ex),
        axial: isAxialLoad(ex),
        cue: (cuesFor(ex) || [])[0] || "",
        muscles
    };
}

function volumeLedger(program, weekIndex) {
    /* EVERY landmark-carrying id present, explicitly zero when untrained. Both source functions omit a
       muscle they found no work for, so `ledger.forearms` came back undefined on a program with no
       forearm work — the right quantity, expressed the one way that is indistinguishable from having
       asked a wrong key. Seeding zeros makes `undefined` mean exactly one thing: that id is not a
       muscle this app models. A caller can then trust `0` and be caught immediately on a typo. */
    const out = {};
    PART_ORDER.forEach(k => { out[k] = 0; });
    Object.keys(SUB_LANDMARKS).forEach(k => { out[k] = 0; });
    Object.assign(out, weeklyVolume(program, weekIndex) || {});
    const sub = weeklySubVolume(program, weekIndex) || {};
    /* Sub-regions LAST and never merged into their parent: a delt head is its own quantity with its
       own landmark, and summing heads into `shoulders` would produce a number no landmark describes
       (measured during v592: summed heads read 62 against a raw shoulder MRV of 26). */
    for (const k of Object.keys(sub))
        out[k] = sub[k];
    return out;
}

function landmarkOf(id) {
    if (SUB_LANDMARKS[id])
        return SUB_LANDMARKS[id];
    return landmarkFor(id) || null;
}

function weeklySubVolume(program, weekIndex) {
    if (program?.engineSource === "pursuit-next") {
        const snapshot = captureShellVolumeSnapshot(program, weekIndex, EXERCISES);
        if (snapshot) return snapshot.subVolume;
    }
    const sub = {};
    const add = (k, v) => { if (k)
        sub[k] = (sub[k] || 0) + v; };
    daysInWeek(program, weekIndex).forEach(day => {
        day.exercises.forEach((_id, slot) => {
            const id = exerciseAt(program, day, slot, weekIndex);
            const ex = EX_BY_ID[id];
            if (!ex)
                return;
            const sets = Number(computeCell(program, day, id, slot, weekIndex).sets) || 0;
            add(subRegionOf(ex), sets); // direct → finest region
            /* ⚠ PRESSING TRAINS THE LATERAL DELT, AND THE MODEL SAID IT DID NOT. An overhead press credits
               front delts directly and triceps as a secondary — nothing reached side delts, so every
               pressing set counted ZERO toward a head that sits under MEV in 41 of 86 programs. The
               reference fractional-attribution table lists the lateral deltoid as a SECONDARY SYNERGIST of
               the overhead press at w = 0.5, alongside triceps.
               Same argument `secondaryOf` already makes for traps ("otherwise traps sit under MEV on most
               splits despite all the rowing"): the volume was being trained and simply not counted.
               MEASURED: side delts under MEV 41 -> 28 of 86 programs, with no slot moved. Engine-gated,
               because it changes the volume arithmetic for every existing program. */
            /* ⚠ PRESSING TRAINS THE LATERAL DELT — AT 0.3, NOT 0.5. An overhead press credits front delts
               directly and triceps as a secondary, and NOTHING reached side delts, so every pressing set
               counted zero toward a head under MEV in 41 of 86 programs.
               THE COEFFICIENT IS THE WHOLE ARGUMENT. One source put the lateral delt at 0.5 as a secondary
               synergist; the EMG and imaging evidence does not support that — Campos et al. measure 27.9%
               MVIC lateral against 33.3% anterior (and 30.3% for an actual lateral raise), and longitudinal
               imaging shows the lateral head grows minimally from vertical pressing because it acts as a
               STABILIZER there, not the force transducer. Empirical range 0.25-0.33. At 0.5 this reported
               side delts under MEV 41 -> 27; at the defensible 0.3 it is 41 -> 36. The larger number was
               the more attractive one and the wrong one. */
            /* HELD — see the coefficient note in ENGINE_RULES. Commented, not guarded: engHas THROWS on
               an unregistered rule.
               WHEN RE-ADDING: gate on an overhead movePattern and add sets * the chosen coefficient to
               side_delts. The call is written out rather than left as live syntax because gates/harness 11
               scans for engHas("name") literals and a held rule name must not appear among them. */
            secondaryOf(ex, program.engineV).forEach(([p, f]) => {
                if (p === "shoulders") {
                    const pat = movePattern(ex);
                    add(pat === "row" || pat === "pulldown" ? "rear_delts" : "front_delts", sets * f);
                }
                else if (p === "traps")
                    add("upper_traps", sets * f);
            });
        });
    });
    return sub;
}

function logWindow(history, days, until, visit) {
    const since = until - days * 86400000;
    (history || []).forEach(h => {
        if (!h || h.date <= since || h.date > until)
            return;
        Object.entries(h.perf || {}).forEach(([id, p]) => {
            const ex = EX_BY_ID[id];
            if (!ex || !p)
                return;
            // isWorkSet, NOT workSetsOf: workSetsOf also demands w > 0, which is right for a 1-RM estimate
            // and wrong here — a set of unweighted pull-ups is logged at w = 0 and is still a set of back.
            const n = setsOf(p).filter(isWorkSet).length; // NOT p.sets.length — extensions are not sets
            if (n)
                visit(ex, n);
        });
    });
}

function loggedVolume(history, days = 7, until = Date.now()) {
    const map = {};
    logWindow(history, days, until, (ex, n) => {
        map[ex.part] = (map[ex.part] || 0) + n; // direct
        secondaryOf(ex).forEach(([part, f]) => { map[part] = (map[part] || 0) + n * f; }); // fractional
    });
    return map;
}

function loggedSubVolume(history, days = 7, until = Date.now()) {
    const sub = {};
    const add = (k, v) => { if (k)
        sub[k] = (sub[k] || 0) + v; };
    logWindow(history, days, until, (ex, n) => {
        add(subRegionOf(ex), n);
        secondaryOf(ex).forEach(([part, f]) => {
            if (part === "shoulders") {
                const pat = movePattern(ex);
                add(pat === "row" || pat === "pulldown" ? "rear_delts" : "front_delts", n * f);
            }
            else if (part === "traps")
                add("upper_traps", n * f);
        });
    });
    return sub;
}

function feedbackDelta(pump, sore) {
    if (sore === "sore")
        return -1;
    if (sore === "fresh")
        return pump === "huge" ? 0 : 1;
    if (sore === "ontime")
        return 0;
    if (pump === "flat")
        return 1;
    if (pump === "huge")
        return 0;
    return undefined; // "good" pump alone, or nothing selected
}

function effortValueLabel(rir) {
    const bounds = effortBounds(rir);
    if (!bounds)
        return "—";
    const half = (n) => { const r = Math.round(n * 2) / 2; return Number.isInteger(r) ? String(r) : r.toFixed(1); };
    return bounds[0] === bounds[1] ? half(bounds[0]) : `${half(bounds[0])}–${half(bounds[1])}`;
}

function effortLabel(rir, mode) {
    const bounds = effortBounds(rir);
    if (!bounds)
        return "—";
    const conv = n => 10 - n;
    const half = (n) => { const r = Math.round(n * 2) / 2; return Number.isInteger(r) ? String(r) : r.toFixed(1); };
    const [lo, hi] = bounds;
    if (mode !== "rpe")
        return lo === hi ? `${half(lo)} RIR` : `${half(lo)}–${half(hi)} RIR`;
    // Higher RIR means lower RPE, so reverse the converted bounds for an ascending RPE range.
    const rpeLo = conv(Math.round(hi)), rpeHi = conv(Math.round(lo));
    return rpeLo === rpeHi ? `RPE ${rpeLo}` : `RPE ${rpeLo}–${rpeHi}`;
}

const mavFor = (part, src = 0) => { const L = landmarkFor(part, "intermediate", src); return L.mav != null ? L.mav : (L.mev + L.mrv) / 2; };

const DELT_HEAD = (id) => {
    if (/rear|face-pull|reverse-fly|rev-fly|reverse-pec|cable-rear/.test(id))
        return "rear_delts";
    if (/lat-raise|lateral|side-lying|upright|y-raise|lu-raise|cable-raise/.test(id))
        return "side_delts";
    if (/front-raise|ohp|shoulder|arnold|press|pike|landmine|push-press|z-press/.test(id))
        return "front_delts";
    return "side_delts";
};

const SUBMUSCLE_LABEL = {
    front_delts: "Front Delts", side_delts: "Side Delts", rear_delts: "Rear Delts",
    upper_traps: "Upper Traps", serratus: "Serratus", obliques: "Obliques", tibialis: "Tibialis",
    /* The forearm is not one muscle any more than the deltoid is. Wrist FLEXORS (palm side) and wrist
       EXTENSORS (back of the hand) are separate groups with separate jobs, and GRIP work — carries,
       hangs, pinches, a gripper — is an isometric hold that trains neither through a range. Naming them
       is what lets the coverage rule below spread the work instead of piling it all in one place. */
    wrist_flexors: "Wrist Flexors", wrist_extensors: "Wrist Extensors", wrist_deviators: "Wrist Deviators", grip: "Grip"
};

function subRegionOf(ex) {
    if (!ex)
        return null;
    if (ex.part === "shoulders")
        return DELT_HEAD(ex.id);
    if (ex.region)
        return ex.region; // explicit tag (serratus/obliques/tibialis/upper_traps)
    if (ex.part === "traps")
        return "upper_traps";
    return null;
}

const REGION_REQUIRED = {
    // Flexion and extension are the two with real cross-sectional area and real carryover; deviation is
    // a ~50° assistance role and is the THIRD slot's direction (v577). A gap rule that demanded all
    // three would fire on almost every program, and a rule that fires on everything is a broken rule.
    forearms: ["wrist_flexors", "wrist_extensors"],
    // Side and rear delts get nothing for free — pressing feeds the front head only. Same class.
    shoulders: ["side_delts", "rear_delts"]
};

function regionGapsFor(program, part) {
    const req = REGION_REQUIRED[part];
    if (!req || !program || !Array.isArray(program.days))
        return [];
    const seen = new Set();
    program.days.forEach(d => (d.exercises || []).forEach(id => {
        const ex = EX_BY_ID[id];
        if (!ex || ex.part !== part)
            return;
        const r = subRegionOf(ex);
        if (r)
            seen.add(r);
    }));
    return req.filter(r => !seen.has(r));
}

const REGION_MOVEMENT = {
    wrist_flexors: { name: "wrist flexion", eg: "a wrist curl" },
    wrist_extensors: { name: "wrist extension", eg: "a reverse wrist curl" },
    wrist_deviators: { name: "wrist deviation", eg: "a hammer radial/ulnar deviation" },
    side_delts: { name: "side delt", eg: "a lateral raise" },
    rear_delts: { name: "rear delt", eg: "a reverse fly or face pull" }
};

function regionGapFix(part, gaps) {
    if (!gaps.length)
        return null;
    const m = gaps.map(g => REGION_MOVEMENT[g] || { name: (SUBMUSCLE_LABEL[g] || g).toLowerCase(), eg: "a direct movement" });
    const list = m.length === 1 ? m[0].name : m.map(x => x.name).join(" or ");
    const egs = m.map(x => x.eg).join(" and ");
    return part === "forearms"
        ? `No ${list} work — carries, hangs and pinches are isometric holds and never take the wrist through a range, so more sets on them cannot fix this. Add ${egs}.`
        : `No direct ${list} work — pressing feeds the front head only, so these get nothing for free. Add ${egs}.`;
}

function lowerBodyLift(ex) {
    if (!ex)
        return false;
    if (LOWER_PARTS.includes(ex.part))
        return true;
    return ex.part === "lower_back" && movePattern(ex) === "hinge" && isBarLike(ex.equip);
}



function groupPctSets(sets) {
    const g = [];
    (sets || []).forEach(s => {
        const last = g[g.length - 1];
        if (last && last.weight === s.weight && last.reps === s.reps)
            last.count++;
        else
            g.push({ weight: s.weight, reps: s.reps, count: 1 });
    });
    return g;
}

function nextTMEvidence(program, history, id) {
    const last = progressionHistoryForProgram(program, history).find(h => h.perf?.[id]);
    const sets = last?.perf?.[id]?.sets;
    const amrap = Array.isArray(sets) && sets.length
        ? (sets.find(s => s.amrap) || sets[sets.length - 1]) : null;
    const reps = amrap ? (parseInt(amrap.r) || 0) : (last ? parseInt(last.perf[id].reps) || 0 : null);
    const factor = reps == null ? 1 : reps === 0 ? 0 : reps >= 8 ? 2 : reps <= 2 ? 0.5 : 1;
    const note = reps == null ? "no AMRAP logged — standard jump" : reps === 0 ? "missed — holding"
        : reps >= 8 ? `${reps} reps — big jump` : reps <= 2 ? `${reps} reps — small jump` : `${reps} reps — standard jump`;
    return { reps, factor, note };
}

function projectNextTM(program, history, unit) {
    unit = program.trainingMaxUnit || program.config?.unit || unit;
    const out = {};
    const tm = program.trainingMax || {};
    Object.keys(tm).forEach(id => {
        const ex = EX_BY_ID[id], current = historyNumber(tm[id]);
        if (!ex || current === null || !(current > 0)) {
            out[id] = current ?? 0;
            return;
        }
        const lower = lowerBodyLift(ex);
        // Floor the cycle bump at this lift's smallest real step so a custom coarse increment (e.g. a
        // machine-based main lift) doesn't get rounded back to the current TM, stalling progression.
        const inc = Math.max(unit === "lb" ? (lower ? 10 : 5) : (lower ? 5 : 2.5), loadStep(ex, unit));
        const { factor } = nextTMEvidence(program, history, id);
        out[id] = factor > 0 ? roundTo(current + inc * factor, loadStep(ex, unit)) : current;
    });
    return out;
}

const MOBILITY = {
    quads: ["Bodyweight squats × 15", "Walking lunges × 10/side", "Ankle rocks × 10/side"],
    hamstrings: ["Leg swings front-to-back × 10/side", "Bodyweight hip hinges × 12"],
    glutes: ["Glute bridges × 15", "Lateral band walks × 10/side"],
    upper_back: ["Cat–cow × 8", "Band pull-aparts × 15", "Scapular pull-ups / dead hang × 20s"],
    lower_back: ["Cat–cow × 8", "Bird-dog × 8/side", "Bodyweight hip hinges × 12"],
    lats: ["Dead hang × 20–30s", "Band lat pulldowns × 15"],
    chest: ["Band chest opener × 10", "Scapular push-ups × 10", "Push-ups × 8"],
    shoulders: ["Shoulder dislocates (band/PVC) × 10", "Arm circles × 10 each way", "Band external rotations × 12"],
    biceps: ["Light band curls × 15"],
    triceps: ["Band press-downs × 15", "Elbow circles × 10"],
    core: ["Dead bug × 8/side", "Cat–cow × 8"],
    calves: ["Ankle rocks × 12", "Calf raises × 15"],
    traps: ["Shrug rolls × 10", "Band pull-aparts × 15"],
    forearms: ["Wrist circles × 10", "Wrist flexor/extensor stretch × 20s"]
};

function warmupRoutine(day) {
    if (!day || !day.exercises || !day.exercises.length)
        return null;
    const order = [day.primaryIndex, ...day.exercises.map((_, i) => i).filter(i => i !== day.primaryIndex)];
    const parts = [];
    order.forEach(i => { const ex = EX_BY_ID[day.exercises[i]]; if (ex && !parts.includes(ex.part))
        parts.push(ex.part); });
    const moves = ["3–5 min easy cardio to raise your core temperature"];
    parts.slice(0, 3).forEach(p => (MOBILITY[p] || []).forEach(m => { if (!moves.includes(m))
        moves.push(m); }));
    const out = moves.slice(0, 6);
    out.push("Then ramp with 1–2 light sets on your first lift before working weight");
    return out;
}

function warmupCount(ex, isPrimary, workingWeight = 0, unit = "kg") {
    const comp = ex.type === "compound";
    const heavy = ex.rep[0] <= 6; // low-rep, heavily loaded
    let n = (isPrimary && comp) ? (heavy ? 3 : 2) : comp ? (heavy ? 2 : 1) : 1;
    /* ---- Depth must follow the LOAD, not just the slot ------------------------------------------
     *
     * The rules above key off the exercise's default rep range and whether it happens to be the day's
     * primary. Neither is the thing that makes a warm-up necessary. A back squat sitting in slot two
     * scored 2 warm-ups no matter the weight, so at 495 lb the ramp ran 250 → 345 → 495: a 150 lb,
     * 43% jump straight into the top set, on the most axially loaded movement there is. The same lift
     * at 135 lb got the identical ramp, which is over-warming a light day and under-warming a heavy one
     * with one rule.
     *
     * So scale with how far above the empty bar the working set actually is. A bar-plus-a-plate squat
     * needs one or two feeler sets; a triple-bodyweight squat needs to be approached. Ratios are to the
     * bar because that is the floor you must pass through anyway, and they only ever ADD steps — this
     * can shorten nothing, so no light day gets a longer ramp than before. Capped at 4 by warmupPlan;
     * useless steps are pruned downstream by warmupSets. */
    if (comp && workingWeight > 0) {
        const bar = BARS[barFor(ex)]?.[unit] || 0;
        if (bar > 0) {
            const mult = workingWeight / bar;
            if (mult >= 3)
                n = Math.max(n, 3); // ~135+ on a 45lb bar: needs a real ramp
            if (mult >= 5)
                n = Math.max(n, 4); // ~225+: approach it in four
        }
        else {
            /* Machines and cables have no bar to measure against, so use the lift's own load step as the
               yardstick — 20+ increments above nothing is a heavy machine press however it is loaded. */
            const step = loadStep(ex, unit) || 1;
            if (workingWeight / step >= 20)
                n = Math.max(n, 3);
        }
    }
    return n;
}

const WARMUP_PYRAMIDS = {
    1: { pct: [0.60], reps: [8] },
    2: { pct: [0.50, 0.70], reps: [8, 5] },
    3: { pct: [0.45, 0.65, 0.85], reps: [8, 5, 3] },
    4: { pct: [0.45, 0.60, 0.75, 0.85], reps: [8, 5, 4, 2] }
};

function warmupPlan(ex, isPrimary, workingWeight = 0, unit = "kg") {
    const n = clamp(warmupCount(ex, isPrimary, workingWeight, unit), 0, 4);
    return n >= 1 ? WARMUP_PYRAMIDS[n] : null;
}

function warmupSets(ex, isPrimary, workingWeight, unit) {
    const plan = warmupPlan(ex, isPrimary, workingWeight, unit);
    if (!plan || !(workingWeight > 0))
        return [];
    const step = loadStep(ex, unit);
    const bar = BARS[barFor(ex)]?.[unit] || 0;
    // Nothing to ramp if the working set is at/under the bar, or only a step or two above it. For
    // isolations the bar floor is usually 0 (cables/machines/dumbbells), so require a few increments
    // of load before a feeler set is worthwhile — a 60% warm-up of a light isolation isn't useful.
    const minWork = ex.type === "isolation" ? bar + step * 3 : bar + step;
    if (workingWeight <= minWork)
        return [];
    const out = [];
    const seen = new Set();
    plan.pct.forEach((p, i) => {
        const w = roundTo(workingWeight * p, step);
        if (w <= bar)
            return; // can't load below the empty bar
        if (w >= workingWeight)
            return; // not a warm-up if it meets/exceeds the work weight
        if (seen.has(w))
            return; // coarse rounding can collapse two steps onto one load
        seen.add(w);
        out.push({ weight: String(w), reps: String(plan.reps[i]), warm: true, done: false, target: { w: String(w), reps: String(plan.reps[i]) } });
    });
    return out;
}

const BARS = { barbell: { kg: 20, lb: 45 }, women: { kg: 15, lb: 35 }, ezbar: { kg: 10, lb: 25 }, smith: { kg: 15, lb: 35 }, trap: { kg: 25, lb: 55 }, ssb: { kg: 30, lb: 65 } };

const BAR_LABEL = { barbell: "Barbell", women: "Women's", ezbar: "EZ bar", smith: "Smith", trap: "Trap bar", ssb: "SSB" };

const PLATES = { kg: [25, 20, 15, 10, 5, 2.5, 1.25], lb: [45, 35, 25, 10, 5, 2.5] };

const ALL_PLATES = { kg: [25, 20, 15, 10, 5, 2.5, 1.25, 0.5, 0.25], lb: [45, 35, 25, 10, 5, 2.5, 1.25, 1, 0.5] };

let AVAIL_PLATES = { kg: null, lb: null };

function setAvailPlates(p) { AVAIL_PLATES = p || { kg: null, lb: null }; }

function barFor(ex) {
    if (ex.equip.includes("barbell"))
        return "barbell";
    if (ex.equip.includes("trapbar"))
        return "trap";
    if (ex.equip.includes("safetybar"))
        return "ssb";
    if (ex.equip.includes("ezbar"))
        return "ezbar";
    if (ex.equip.includes("smith"))
        return "smith";
    if (ex.equip.includes("landmine"))
        return "barbell"; // a landmine IS a straight bar, one end pinned
    return null;
}

function platesPerSide(total, bar, unit) {
    const barW = BARS[bar]?.[unit] ?? 0;
    let perSide = (total - barW) / 2;
    if (!Number.isFinite(perSide) || perSide > 2000)
        return { barW, plates: [], leftover: 0 }; // NaN/Infinity/absurd input (corrupt log, cleared field) must not reach the greedy loop
    if (!(perSide > 0))
        return { barW, plates: [], leftover: 0 };
    const list = (((AVAIL_PLATES[unit] && AVAIL_PLATES[unit].length) ? [...AVAIL_PLATES[unit]].sort((a, b) => b - a) : PLATES[unit]) || []).filter(p => Number.isFinite(p) && p > 0); // a 0/negative/NaN plate (possible via a corrupt import) would spin the greedy loop forever; unknown unit → empty list, not a throw
    // Greedy largest-first — the conventional loading order lifters expect, and provably exact for the
    // canonical default sets (every denomination is a multiple of the smallest).
    const gPlates = [];
    let rem = perSide;
    for (const p of list) {
        while (rem >= p - 1e-6) {
            gPlates.push(p);
            rem -= p;
        }
    }
    const gLeft = Math.round(rem * 100) / 100;
    if (gLeft <= 1e-6)
        return { barW, plates: gPlates, leftover: 0 };
    // Greedy stranded a remainder. With a CUSTOM (non-canonical) plate set an exact combination may
    // still exist that greedy can't see (e.g. [25,20] can't make 45 greedily but 25+20 does). Solve it
    // exactly via bounded DP on a 0.25-unit grid (all plate denominations are whole multiples of it),
    // minimizing plate count. Only override greedy if the solver actually gets CLOSER to the target —
    // so canonical sets and genuinely-unreachable fractions keep the familiar greedy display.
    const grid = 0.25;
    const T = Math.floor(perSide / grid + 1e-9); // FLOOR, not round: the solver must never target above the true per-side weight, or it "solves" by overloading the bar (negative leftover)
    // Denominations and their grid-units MUST be filtered together: `pick` stores indices into this
    // array, and the reconstruction reads the plate from the same index. Filtering only the units (as an
    // earlier version did) shifts every DP solution onto the wrong — larger — denominations whenever any
    // plate exceeds the target (e.g. loading a 25 where a 1.25 was solved).
    const denoms = [...new Set(list)].map(p => ({ p, u: Math.round(p / grid) })).filter(d => d.u > 0 && d.u <= T);
    if (denoms.length && T <= 4000) { // cap guards against a corrupted/absurd load blowing up the DP array
        const cnt = new Array(T + 1).fill(Infinity);
        cnt[0] = 0;
        const pick = new Array(T + 1).fill(-1);
        for (let s = 1; s <= T; s++) {
            for (let i = 0; i < denoms.length; i++) {
                const u = denoms[i].u;
                if (u <= s && cnt[s - u] + 1 < cnt[s]) {
                    cnt[s] = cnt[s - u] + 1;
                    pick[s] = i;
                }
            }
        }
        let s = T;
        while (s > 0 && cnt[s] === Infinity)
            s--;
        const gSum = perSide - gLeft; // weight greedy actually loaded
        if (s * grid > gSum + 1e-6) { // exact solver got closer → use it
            const plates = [];
            let cur = s;
            while (cur > 0) {
                const i = pick[cur];
                plates.push(denoms[i].p);
                cur -= denoms[i].u;
            }
            plates.sort((a, b) => b - a);
            return { barW, plates, leftover: Math.round((perSide - s * grid) * 100) / 100 };
        }
    }
    return { barW, plates: gPlates, leftover: gLeft };
}

function linearInc(ex, unit) {
    const lower = lowerBodyLift(ex);
    return unit === "lb" ? (lower ? 10 : 5) : (lower ? 5 : 2.5);
}

const PROG_STYLES = {
    auto: "Auto",
    double: "Double progression",
    dynamic: "Dynamic double",
    ladder: "Rep ladder",
    linear: "Linear LP",
    wave: "Wave loading",
    e1rm: "e1RM autoregulation"
};

function explainPrescription(o) {
    const { program, day, ex, slot, perf, history, weekIndex } = o || {};
    if (!program || !day || !ex || program.engineSource !== "pursuit-next")
        return null;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    if (!cell || cell.missing)
        return null;
    const range = cellRepRange(cell, program, ex, slot === day.primaryIndex);
    const sug = nextWorkoutSuggestionForShell(program, history || [], EXERCISES, day, slot, weekIndex);
    const last = perf && perf[ex.id] ? perf[ex.id] : null;
    const lastSets = Array.isArray(last?.sets) ? last.sets : [];
    const rirRows = lastSets.map((st, i) => ({
        set: i + 1,
        weight: st.w != null ? st.w : st.weight,
        reps: st.r != null ? st.r : st.reps,
        rir: st.rir != null ? st.rir : st.tr != null ? st.tr : "—",
        source: st.rir != null ? "you rated it" : st.tr != null ? "planned target" : "not reported"
    })).filter(r => r.weight != null || r.reps != null);
    const lastWork = [...lastSets].reverse().find(st => Number(st?.w ?? st?.weight) > 0 && Number(st?.r ?? st?.reps) > 0);
    const lastW = Number(last?.weight ?? lastWork?.w ?? lastWork?.weight);
    const lastR = Number(last?.reps ?? lastWork?.r ?? lastWork?.reps);
    return {
        lift: { id: ex.id, name: ex.name, part: ex.part, type: ex.type, equip: ex.equip, isPrimary: slot === day.primaryIndex, slot },
        context: {
            day: { id: day.id, label: day.label, type: day.type }, weekIndex, weeksTotal: weeksOf(program),
            phase: program?.nextEngine?.phase || null, goal: program?.config?.goal || null, deload: false,
            experience: program?.config?.experience, percentScheme: null
        },
        read: {
            lastPerformance: Number.isFinite(lastW) && Number.isFinite(lastR) ? { weight: lastW, reps: lastR, sets: rirRows.length || null } : null,
            perSet: rirRows.length ? rirRows : null,
            e1rm: Number.isFinite(lastW) && Number.isFinite(lastR) ? e1rm(lastW, lastR) : null,
            historyEntriesForThisDay: (history || []).filter(h => h?.programId === program.id
                && resolveHistoryDayIndex(program.days, h) === program.days.indexOf(day)).length,
            stallSessions: 0, plateauSessions: 0, readiness: null
        },
        decided: {
            repRange: range, targetRIR: effortValueLabel(cell.rir), styleSource: "Pursuit Iron",
            style: cell.progressionStyle || "auto", styleWhy: sug?.reason || "The saved week plan owns this prescription.",
            styleAt: null, override: null, sets: Number(cell.sets) || 0
        },
        load: sug ? {
            weight: sug.weight, direction: sug.dir, delta: sug.weight != null && Number.isFinite(lastW) ? sug.weight - lastW : null,
            reason: sug.reason || null, increment: null, snappedToRack: false, rack: null, cappedByGym: false,
            gymCeiling: null, swapTo: null
        } : { weight: null, reason: "No comparable completed workout yet; log this exposure and the next comparable session will use it for progression." },
        warmups: []
    };
}

function perfAfterDelete(history, perf, deletedId) {
    const gone = (history || []).find(h => h && h.id === deletedId);
    if (!gone || !gone.perf)
        return perf || {};
    const remaining = (history || [])
        .filter(h => h && h.id !== deletedId)
        .slice()
        .sort((a, b) => (b.date || 0) - (a.date || 0));
    const next = { ...(perf || {}) };
    Object.keys(gone.perf).forEach(exId => {
        const prior = remaining.find(h => h.perf && h.perf[exId]);
        if (prior)
            next[exId] = prior.perf[exId];
        else
            delete next[exId];
    });
    return next;
}

const __slopeMemo = new WeakMap();

function personalRepSlope(history, ex) {
    if (!history || !ex || !ex.id)
        return 30;
    let byEx = __slopeMemo.get(history);
    if (!byEx) {
        byEx = new Map();
        __slopeMemo.set(history, byEx);
    }
    if (byEx.has(ex.id))
        return byEx.get(ex.id);
    const pairs = [];
    let seen = 0;
    for (const h of history) {
        if (seen >= 40)
            break; // recent sessions carry the current curve
        const p = h && h.perf && h.perf[ex.id];
        if (!p || !p.sets || p.sets.length < 2)
            continue;
        seen++;
        const work = setsOf(p).filter(t => isWorkSet(t) && t.w > 0 && t.r > 0);
        for (let i = 0; i < work.length; i++)
            for (let j = i + 1; j < work.length; j++) {
                const a = work[i], b = work[j];
                const qa = a.rir != null ? a.rir : 2, qb = b.rir != null ? b.rir : 2;
                if (Math.abs(a.w - b.w) < 0.05 * Math.max(a.w, b.w))
                    continue; // need a real load gap
                const sl = (b.w * (b.r + qb) - a.w * (a.r + qa)) / (a.w - b.w);
                if (isFinite(sl) && sl >= 12 && sl <= 60)
                    pairs.push(sl);
            }
    }
    let out = 30;
    if (pairs.length >= 6) {
        pairs.sort((x, y) => x - y);
        const m = Math.floor(pairs.length / 2);
        const med = pairs.length % 2 ? pairs[m] : (pairs[m - 1] + pairs[m]) / 2;
        const conf = Math.min(1, (pairs.length - 6) / 24);
        out = Math.max(22, Math.min(42, 30 + (med - 30) * conf));
    }
    byEx.set(ex.id, out);
    return out;
}

const lengthUnitFor = (weightUnit) => (weightUnit === "kg" ? "cm" : "in");

const asLengthUnit = (u, weightUnit) => (u === "in" || u === "cm" ? u : u === "kg" ? "cm" : u === "lb" ? "in" : lengthUnitFor(weightUnit));

const toLength = (v, from, to) => (from === to ? v : to === "cm" ? v * 2.54 : v / 2.54);

function normalizeMeasureLog(arr, to, weightUnit) {
    return (arr || [])
        .filter(e => e && e.date != null && Number(e.v) > 0)
        .map(e => { const from = asLengthUnit(e.unit, weightUnit); return { ...e, v: Math.round(toLength(Number(e.v), from, to) * 10) / 10, unit: to }; })
        .sort((a, b) => a.date - b.date);
}

function recommendedSplit(config, nextRecommendation) {
    const days = config.days;
    const fitsSession = (k) => {
        const min = SPLITS[k]?.minSession, max = SPLITS[k]?.maxSession;
        if ((!min && !max) || !config.session)
            return true;
        const order = SESSIONS.map(x => x.id), i = order.indexOf(config.session);
        return (!min || i >= order.indexOf(min)) && (!max || i <= order.indexOf(max));
    };
    if (nextRecommendation && SPLITS[nextRecommendation]?.days.includes(days) && fitsSession(nextRecommendation))
        return nextRecommendation;
    // Fallback is compatibility-only if generation cannot form a recommendation (for example, a
    // temporarily impossible equipment selection). It does not rank named programs or maintain a
    // second scoring model.
    const generic = days <= 3 ? "full_body" : days === 4 ? "upper_lower" : days === 5 ? "ulppl" : "ppl";
    if (SPLITS[generic]?.days.includes(days) && fitsSession(generic))
        return generic;
    return Object.entries(SPLITS).find(([, split]) => split.days.includes(days))?.[0] || null;
}

const PHASES = {
    accumulation: { label: "Accumulation", tagline: "Build the work", why: "Volume is high and effort moderate — bank the work that later intensity will sharpen.", color: "#8EBE6B" },
    intensification: { label: "Intensification", tagline: "Add the load", why: "Sets get heavier and closer to failure while volume holds — tension and strength climb.", color: "#D4B85D" },
    peak: { label: "Peak", tagline: "Push the ceiling", why: "The hardest sets of the block. Leave it all here, then back off to recover.", color: "#D45D6C" },
    deload: { label: "Deload", tagline: "Recover & adapt", why: "Volume and load drop so your body catches up to the training — you return fresher and stronger.", color: "#5DB4D4" }
};

function phaseFor(program, weekIndex) {
    const weeks = weeksOf(program);
    if (program.config?.deload && weekIndex > weeks)
        return PHASES.deload;
    const p = blockPhase(program, weekIndex, 1); // honors cycle-block phaseWindow
    if (p >= 0.8)
        return PHASES.peak;
    if (p >= 0.4)
        return PHASES.intensification;
    return PHASES.accumulation;
}

const PV_PAD_TOP = 9;

const PV_ROW_GAP = 8;

function phaseKind(label, index) {
    const l = String(label || "").toLowerCase();
    if (/deload|recover|rest|taper/.test(l))
        return 4;
    if (/accum|hypertroph|base|found|volume|build/.test(l))
        return 0;
    if (/strength|intens|develop|raise/.test(l))
        return 1;
    if (/peak|specific|sharp|power/.test(l))
        return 2;
    if (/realis|realiz|test|compet|max/.test(l))
        return 3;
    return Math.min(index, 3);
}

const PHASE_ROT = [-34, -12, 14, 40];

const repsLow = (s) => (s == null) ? "" : String(s).includes("-") ? String(s).split("-")[0] : String(s);

function sessionSnapshotStatus(snapshot, ctx) {
    const s = snapshot;
    if (!s || s.programId !== ctx.programId || s.dayId !== ctx.dayId || s.weekIndex !== ctx.weekIndex
        || !Array.isArray(s.data) || !s.data.length)
        return "none";
    // Storage is untrusted: a null set used to pass this check and crash resume.
    if (!s.data.every(e => e && typeof e.id === "string" && EX_BY_ID[e.id] && Array.isArray(e.sets)
        && e.sets.every(row => row && typeof row === "object" && !Array.isArray(row))))
        return "none";
    if (s.dayExSig != null && s.dayExSig !== ctx.dayExSig)
        return "partial";
    return "resume";
}

function mergeSessionData(snapData, fresh) {
    if (!Array.isArray(fresh))
        return fresh;
    if (!Array.isArray(snapData) || !snapData.length)
        return fresh;
    const byId = new Map();
    for (const e of snapData)
        if (e && e.id != null && !byId.has(e.id))
            byId.set(e.id, e);
    return fresh.map(e => {
        const prev = byId.get(e.id);
        if (!prev || !Array.isArray(prev.sets) || !prev.sets.length)
            return e;
        return { ...e, sets: prev.sets, note: prev.note || e.note };
    });
}

function mergedSetCount(data) {
    return (Array.isArray(data) ? data : []).reduce((n, e) => n + ((e.sets || []).filter(s => s && s.done && !s.warm).length), 0);
}

function userOwnsRuntimeSet(row) {
    if (!row)
        return false;
    if (row.valueOwner != null)
        return row.valueOwner === "user";
    return row.auto === false; // pre-M204 live snapshots
}

function sanitizeWeightInput(v) {
    let s = String(v).replace(/[^0-9.\-]/g, "");
    const neg = s.trim().startsWith("-"); // a single leading minus = assistance (assisted lifts)
    s = s.replace(/-/g, "");
    const i = s.indexOf(".");
    if (i !== -1)
        s = s.slice(0, i + 1) + s.slice(i + 1).replace(/\./g, "");
    if (parseFloat(s) > 2000)
        s = "2000";
    return (neg ? "-" : "") + s;
}

function sanitizeRepsInput(v) {
    let s = String(v).replace(/[^0-9]/g, "");
    if (parseInt(s) > 100)
        s = "100";
    return s;
}

function calibratedAnchorPerf(program, day, perf, history, weekIndex, effort) {
    return calibrateDayPerf(anchorPerfFor(program, day, perf, history, weekIndex), effort);
}

function calibrateDayPerf(dayPerf, effort) {
    if (!effort || !effort.bias || !dayPerf)
        return dayPerf;
    const out = {};
    for (const [id, p] of Object.entries(dayPerf)) {
        if (!p || !p.sets || !p.sets.length) {
            out[id] = p;
            continue;
        }
        // keep the record's own shape here — do NOT synthesise sets it never had
        out[id] = { ...p, sets: Array.isArray(p.sets) ? p.sets.map(s => (s && s.rir == null ? s : { ...s, rir: clamp(s.rir - effort.bias, 0, 6) })) : p.sets };
    }
    return out;
}

const PLANNED_ROOM_RIR = 2.0;

function readinessBand(readiness, ctx) {
    if (readiness == null)
        return { band: "green", trim: 0, why: "" };
    if (readiness >= 80)
        return { band: "green", trim: 0, why: "" };
    /* Name the CAUSE. "Still recovering" on a day the program itself scheduled reads as the app being
       arbitrary, and the lifter has no way to act on it. The usual driver is not the calendar but the
       effort: taking sets to failure when the prescription asked for reps in reserve raises the
       estimated recovery cost, so it is the RIR — something they control — that should be named. */
    const days = ctx && ctx.hoursSince != null ? Math.max(1, Math.round(ctx.hoursSince / 24)) : null;
    const gap = days ? `${days} day${days === 1 ? "" : "s"}` : "this gap";
    /* WHOSE DOING WAS IT. `overBy` is how far under the prescribed reserve the lifter finished, and on
       its own it cannot answer that. An overshoot of 0.75 against a plan asking RIR 4 is a lifter
       ignoring the plan; the same 0.75 against a plan asking RIR 0.76 — which is precisely what week 4
       of an intensification block prescribes — is a quarter of a rep, and the plan was already at the
       edge on purpose. The old threshold read them identically, so a hypertrophy block doing exactly
       what it was designed to do got told it had "taken most of the session to failure" and had volume
       trimmed for it. The block IS the intensity; being surprised by it is the app failing to read its
       own programming.
       So the accusatory wording now requires that the plan actually left room to overshoot
       (PLANNED_ROOM_RIR). Below that the work was near-failure BY DESIGN: still trim on a short gap —
       autoregulation is exactly the right response to intense work on one day's rest — but name the
       block rather than the lifter, because there is nothing for them to correct. */
    const overshot = ctx && ctx.overBy >= 0.75;
    const planNearFailure = ctx && ctx.plannedRIR != null && ctx.plannedRIR < PLANNED_ROOM_RIR;
    const hard = overshot && !planNearFailure;
    const byDesign = planNearFailure && !!days;
    if (readiness >= 50) {
        return { band: "yellow", trim: 1, why: hard
                ? `You took most of your last session here to failure — ${gap} is short for that, so one accessory set is trimmed.`
                : byDesign
                    ? `This block programs near-failure work and it has only been ${gap} — one accessory set is trimmed to pay for it. The main lift is untouched.`
                    : "Still recovering — one set trimmed from accessory work." };
    }
    return { band: "red", trim: 1, why: hard
            ? `Last session here was taken to failure and it has only been ${gap}. Consider a fresher day, or keep it light.`
            : byDesign
                ? `Near-failure work as programmed, ${gap} ago. That's the block working — but consider a fresher day, or keep this one light.`
                : "This muscle group is under-recovered. Consider a fresher day, or keep it light." };
}

function classifyPlateau(trend, sets, muscleReadiness, ctx) {
    const base = plateauOf(trend);
    if (!base)
        return null;
    const recent = (sets || []).slice(-6); // oldest→newest per-session summaries { r, rir }
    let kind = "variance";
    if (recent.length >= 3) {
        const half = Math.floor(recent.length / 2);
        const avg = (a, f) => a.length ? a.reduce((s, x) => s + f(x), 0) / a.length : 0;
        const repsEarly = avg(recent.slice(0, half), x => x.r);
        const repsLate = avg(recent.slice(half), x => x.r);
        const rirEarly = avg(recent.slice(0, half).filter(x => x.rir != null), x => x.rir);
        const rirLate = avg(recent.slice(half).filter(x => x.rir != null), x => x.rir);
        const repsFalling = repsLate < repsEarly - 0.4;
        const grinding = rirLate < rirEarly - 0.3; // less in reserve for the same work
        const beatUp = muscleReadiness != null && muscleReadiness < 65;
        if (repsFalling && (grinding || beatUp))
            kind = "fatigue";
        else if (Math.abs(repsLate - repsEarly) <= 0.4)
            kind = "true";
    }
    /* ── TWO CAUSES A SINGLE LIFT'S OWN NUMBERS CANNOT SEE ───────────────────────────────────────
     * The three kinds above are all read from THIS lift's reps and effort, which is the right place to
     * look for fatigue and for noise. But a lift can also stop moving for reasons that live outside its
     * own series entirely, and calling those a "strength ceiling" sends the lifter to deload when the
     * answer is the opposite.
     *
     * VOLUME-LIMITED outranks a true plateau, and it is the one worth catching. A muscle sitting under
     * its weekly floor is not at a ceiling — it is under-stimulated, and "deload this lift, then
     * rebuild" is precisely the wrong instruction. Read through `volumeLedger`/`landmarkOf` so parts and
     * sub-regions answer the same way; the delt heads are exactly where this shows up, and reading the
     * part-level accessor for a head returns undefined, which used to look like zero.
     *
     * EXERCISE-SPECIFIC needs the lifter's other work for the same muscle. If the bench has stalled
     * while their incline and dip both keep climbing, the muscle is fine and the movement is stale —
     * rotate the exercise rather than change the program. Requires at least two SIBLINGS still moving,
     * because one other lift progressing is as easily noise as signal.
     *
     * Ordered deliberately: an under-fed muscle explains a stall better than a stale movement does, and
     * both explain it better than "you have hit your ceiling". Neither overrides `fatigue` — a lifter
     * whose reps are falling while effort climbs is beaten up regardless of how much volume the
     * spreadsheet says the muscle got. */
    if (ctx && kind !== "fatigue") {
        const floor = ctx.floor, vol = ctx.volume;
        if (floor > 0 && vol != null && vol < floor - 0.5)
            kind = "volume";
        else if (Array.isArray(ctx.siblingsProgressing) && ctx.siblingsProgressing.length >= 2)
            kind = "exercise";
    }
    const advice = kind === "volume"
        ? `No PR in ${base.since} sessions, and ${ctx.partLabel || "this muscle"} is getting ${Math.round(ctx.volume)} sets a week against a target of ${Math.round(ctx.floor)}. That's under-training, not a ceiling — add volume before you deload.`
        : kind === "exercise"
            ? `No PR in ${base.since} sessions, but your other ${ctx.partLabel || "work for this muscle"} is still climbing. The muscle is fine; this movement has gone stale. Swap it for a variation.`
            : kind === "fatigue"
                ? `Reps are slipping and effort is climbing over ${base.since} sessions — that's fatigue, not a strength ceiling. Deload this lift, then rebuild.`
                : kind === "true"
                    ? base.advice
                    : `No PR in ${base.since} sessions, but your numbers are just noisy rather than trending down. Hold the course — no change needed yet.`;
    return { ...base, kind, advice };
}

let _lmCache = { key: null, val: null };

function lifterModelKey(hs) {
    let h = 5381;
    const n = hs.length;
    for (let i = 0; i < n; i++) {
        const e = hs[i];
        if (!e)
            continue;
        /* Editing history must invalidate every model that consumes it. Volume/setsDone alone miss
           RIR-only corrections, set-order corrections and a changed load/reps pair whose tonnage happens
           to stay equal. Fingerprint the performed-set evidence itself, including target-vs-observed RIR
           provenance, so the coach/readiness/progression views cannot keep serving the pre-edit model. */
        let sig = `${e.id}|${e.date}|${e.volume}|${e.setsDone}`;
        const perf = e.perf || {};
        for (const exId of Object.keys(perf).sort()) {
            const p = perf[exId] || {};
            sig += `|${exId}:${p.weight ?? ""}:${p.reps ?? ""}`;
            for (const st of (Array.isArray(p.sets) ? p.sets : []))
                sig += `;${st?.w ?? ""},${st?.r ?? ""},${st?.rir ?? ""},${st?.tr ?? ""},${st?.sub ? 1 : 0}`;
        }
        for (let j = 0; j < sig.length; j++)
            h = ((h * 33) ^ sig.charCodeAt(j)) >>> 0;
    }
    return `${n}:${h}`;
}

function buildLifterModel(history, perf, program) {
    const hs = canonicalHistoryNewest(history);
    const key = lifterModelKey(hs) + "|" + (program ? `${program.id}:${program.updatedAt || ""}:${program.weeks || ""}` : "-");
    if (_lmCache.key === key && _lmCache.val)
        return _lmCache.val;
    const effort = effortCalibration(hs);
    const trends = exerciseTrends(hs);
    const recovery = muscleRecovery(hs);
    // per-session summaries per lift, oldest→newest, for slope + plateau classification.
    // perLiftDay carries the SAME summaries split by the day they were logged on — plateau is judged
    // per day (see the plateau assembly below), because a lift trained on two days is really two
    // progressions and merging them invents stalls that neither day has.
    const perLift = {};
    const perLiftDay = {};
    for (let i = hs.length - 1; i >= 0; i--) { // history is newest-first
        const h = hs[i];
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ss = Array.isArray(p.sets) && p.sets.length ? p.sets : [{ w: parseFloat(p.weight), r: parseInt(p.reps), rir: null }];
            let best = 0, bestR = 0, bestRir = null;
            for (const s of ss) {
                const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
                if (!(w > 0) || !(r > 0))
                    continue;
                const observedRir = observedHistoryRIR(s);
                const rirRaw = observedRir ?? 2;
                const rirAdj = clamp(rirRaw - effort.bias, 0, 6); // interpret through the calibration
                const e = e1rmRIR(convertHistoryLoad(w, h.unit, program?.config?.unit || hs[0]?.unit || "kg"), r, rirAdj);
                if (e > best) {
                    best = e;
                    bestR = r;
                    bestRir = observedRir;
                }
            }
            if (best > 0) {
                const rec = { e1rm: best, r: bestR, rir: bestRir, date: h.date };
                (perLift[id] = perLift[id] || []).push(rec);
                if (h.dayId)
                    ((perLiftDay[id] = perLiftDay[id] || {})[h.dayId] = perLiftDay[id][h.dayId] || []).push(rec);
            }
        }
    }
    const muscles = {};
    for (const m of recovery) {
        muscles[m.part] = { readiness: m.readiness, status: m.status, daysSince: m.daysSince, hoursSince: m.hoursSince, measured: m.measured, overBy: m.overBy, plannedRIR: m.plannedRIR, sets: m.sets, ...readinessBand(m.readiness, m) };
    }
    const lifts = {};
    const trendById = Object.fromEntries(trends.map(t => [t.id, t]));
    /* A lift trained on more than one day is more than one progression. See plateauSplitByDay() — the
       merged e1RM series interleaves them, and the day that structurally CANNOT set a PR (the lighter,
       higher-rep slot of a lift that also has a heavy slot) pads plateauOf()'s "sessions since PR"
       counter on every appearance. Judge each day on its own series, and keep the per-day summaries
       aligned with the per-day trends so classifyPlateau sees one day's reps/RIR, not two days' mixed. */
    /* ── CONTEXT FOR THE TWO OFF-LIFT PLATEAU CAUSES ────────────────────────────────────────────
     * Built once per model rather than per lift: `volumeLedger` walks the whole program, and calling it
     * inside a loop over every lift would turn an O(history) model build into an O(history x lifts) one.
     * Silently absent when there is no active program to read a floor from — classifyPlateau then
     * returns exactly the three kinds it always did. */
    const ledger = program ? (() => { try {
        return volumeLedger(program, Math.max(0, weeksOf(program) - 2));
    }
    catch {
        return null;
    } })() : null;
    /* Which lifts for a given muscle are still MOVING. `trend.dir` is the app's own read of a lift's
       direction, so this asks the same question the trend display answers rather than a second one. */
    /* ⚠ DERIVED FROM THE e1RM SERIES, NOT FROM A `dir` FIELD. My first version filtered on `t.dir ===
       "up"`, which does not exist on a trend — exerciseTrends returns {id, name, part, sessions[]} and
       nothing else. Every lift silently failed the filter, so `siblingsProgressing` was always empty
       and the exercise-specific kind could never fire. It compiled, ran, and did nothing.
       A lift counts as still moving when its most recent session beats the best of the window before
       it: the same e1RM series plateauOf reads, asked the opposite question. Six sessions to match the
       window plateauOf uses, so a lift and its siblings are judged over the same span. */
    const progressingByPart = {};
    trends.forEach(t => {
        const px = EX_BY_ID[t.id];
        const ss = (t && t.sessions) || [];
        if (!px || ss.length < 3)
            return;
        const win = ss.slice(-6);
        const latest = win[win.length - 1] && win[win.length - 1].best;
        const priorBest = Math.max(...win.slice(0, -1).map(x => x.best || 0));
        if (!(latest > priorBest))
            return; // no recent PR — not moving
        (progressingByPart[px.part] = progressingByPart[px.part] || []).push(t.id);
    });
    const plateauCtx = (id) => {
        const px = EX_BY_ID[id];
        if (!px || !ledger)
            return null;
        const L = landmarkOf(px.part) || {};
        return {
            volume: ledger[px.part],
            floor: L.mev,
            partLabel: (PART_LABEL[px.part] || px.part).toLowerCase(),
            siblingsProgressing: (progressingByPart[px.part] || []).filter(x => x !== id)
        };
    };
    const plateauFor = (id, mergedSets, readiness) => plateauSplitByDay(hs, id, (t, d) => classifyPlateau(t, (perLiftDay[id] || {})[d] || [], readiness, plateauCtx(id)), () => classifyPlateau(trendById[id], mergedSets, readiness, plateauCtx(id)));
    const now = Date.now();
    for (const [id, sess] of Object.entries(perLift)) {
        const ex = EX_BY_ID[id];
        const last = sess[sess.length - 1];
        // gain velocity: least-squares slope of e1RM over the trailing 6 sessions, per week
        const win = sess.slice(-6);
        let slope = 0, conf = 0;
        if (win.length >= 3) {
            const t0 = win[0].date;
            const xs = win.map(s => (s.date - t0) / (7 * 86400000)); // weeks
            const ys = win.map(s => s.e1rm);
            const mx = xs.reduce((a, b) => a + b, 0) / xs.length;
            const my = ys.reduce((a, b) => a + b, 0) / ys.length;
            let num = 0, den = 0;
            for (let i = 0; i < xs.length; i++) {
                num += (xs[i] - mx) * (ys[i] - my);
                den += (xs[i] - mx) ** 2;
            }
            slope = den > 0 ? num / den : 0;
            conf = clamp((win.length - 2) / 4, 0, 1);
        }
        lifts[id] = {
            e1rm: Math.round(last.e1rm * 10) / 10,
            e1rmSlope: Math.round(slope * 100) / 100,
            slopeConfidence: Math.round(conf * 100) / 100,
            sessions: sess.length,
            /* A history entry with no usable date yields no staleness — NOT NaN. Every entry this app
               writes carries `date` (finish() stamps Date.now()), but an IMPORTED backup is arbitrary JSON
               from another device, a hand-edit, or an older schema, and `migrateStore` deliberately does not
               drop entries for missing fields — losing a logged workout is worse than carrying an odd one.
               So a dateless entry does reach here, and `now - undefined` is NaN, which then propagates into
               anything that later reads this field. Found by fuzzing the model with corrupt-but-plausible
               history; nothing consumes `staleness` yet, so it was inert — but a NaN sitting in a model
               waiting for its first consumer is a bug with a delay on it, and "N days since you trained
               this" rendering as "NaN days" is exactly the kind of thing that ships. null means unknown,
               which is the truth. */
            staleness: Number.isFinite(last?.date) ? Math.round((now - last.date) / 86400000) : null,
            plateau: plateauFor(id, sess, ex ? muscles[ex.part]?.readiness : null)
        };
    }
    const val = { effort, lifts, muscles, builtAt: now };
    _lmCache = { key, val };
    return val;
}

const __doseMemo = new WeakMap();

function volumeResponse(history, part) {
    if (!history || !part)
        return null;
    let byPart = __doseMemo.get(history);
    if (!byPart) {
        byPart = new Map();
        __doseMemo.set(history, byPart);
    }
    if (byPart.has(part))
        return byPart.get(part);
    const WEEK = 7 * 86400000;
    const weeks = new Map(); // weekIndex -> { sets, e1: best flat-Epley e1RM }
    for (const h of history) {
        if (!h || !h.date)
            continue;
        const wk = Math.floor(h.date / WEEK);
        let rec = weeks.get(wk);
        if (!rec) {
            rec = { sets: 0, e1: 0 };
            weeks.set(wk, rec);
        }
        for (const [id, pf] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== part)
                continue;
            const ss = (pf.sets && pf.sets.length) ? pf.sets : [{ w: pf.weight, r: pf.reps }];
            for (const t of ss) {
                if (!(t.w > 0) || !(t.r > 0) || t.warm)
                    continue;
                rec.sets++;
                const e = e1rmRIR(t.w, t.r, 0);
                if (e > rec.e1)
                    rec.e1 = e;
            }
        }
    }
    const idx = [...weeks.keys()].sort((a, b) => a - b);
    // Pair over TWO-WEEK windows, not single weeks. A week of honest training moves e1RM by well under
    // 1%, while the measurement moves in quanta — a 2.5kg plate at 80% is ~2%, one rep at the same load
    // ~2.5% — so week-over-week deltas are mostly zeros punctuated by rounding ticks, and the terciles
    // all median to nothing. Two-week windows put the signal at or above the instrument's resolution
    // while volume attribution stays clean (the window's own average weekly sets). Halves the sample
    // count, which the identifiability floor below already respects.
    const pairs = []; // { sets: avg weekly sets of window A, gainPct: e1RM change into window B, per week }
    for (let i = 0; i + 3 < idx.length; i += 2) {
        if (idx[i + 1] !== idx[i] + 1 || idx[i + 2] !== idx[i] + 2 || idx[i + 3] !== idx[i] + 3)
            continue;
        const a1 = weeks.get(idx[i]), a2 = weeks.get(idx[i + 1]);
        const b1 = weeks.get(idx[i + 2]), b2 = weeks.get(idx[i + 3]);
        const aE = Math.max(a1.e1, a2.e1), bE = Math.max(b1.e1, b2.e1);
        const aSets = (a1.sets + a2.sets) / 2;
        if (!(aSets > 0) || !(aE > 0) || !(bE > 0))
            continue;
        pairs.push({ sets: aSets, gainPct: 100 * (bE - aE) / aE / 2 }); // per-week rate
    }
    const spread = pairs.length ? Math.max(...pairs.map(p => p.sets)) - Math.min(...pairs.map(p => p.sets)) : 0;
    let out = null;
    if (pairs.length >= 6 && spread >= 5) { // ≥6 window-pairs ≈ 6 months of training
        const bySets = pairs.slice().sort((x, y) => x.sets - y.sets);
        const cut = Math.floor(bySets.length / 3);
        const terc = [bySets.slice(0, cut), bySets.slice(cut, bySets.length - cut), bySets.slice(bySets.length - cut)];
        const med = (a) => { const v = a.map(x => x.gainPct).sort((x, y) => x - y); const m = Math.floor(v.length / 2); return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2; };
        const bins = terc.filter(t => t.length >= 2).map(t => ({
            lo: Math.min(...t.map(x => x.sets)), hi: Math.max(...t.map(x => x.sets)), gain: med(t), n: t.length
        }));
        if (bins.length >= 2) {
            const ranked = bins.slice().sort((x, y) => y.gain - x.gain);
            const clear = ranked[0].gain - ranked[1].gain >= 0.15;
            out = { part, weeks: pairs.length, spread, bins,
                best: clear ? { lo: ranked[0].lo, hi: ranked[0].hi, gain: ranked[0].gain } : null,
                confidence: Math.min(1, (pairs.length - 8) / 16) };
        }
    }
    byPart.set(part, out);
    return out;
}

function personalMav(history, part) {
    const groupMav = mavFor(part);
    if (!history || !history.length)
        return groupMav;
    const L = landmarkFor(part);
    const dr = volumeResponse(history, part);
    if (!dr || !dr.best)
        return groupMav;
    const signal = clamp(dr.best.hi, L.mev, L.mrv); // top of the lifter's best-responding band
    const c = clamp(dr.confidence || 0, 0, 1);
    return clamp(groupMav + c * (signal - groupMav), L.mev, L.mrv);
}

function volumeVerdicts(history, volume, parts) {
    if (!history || !history.length || !volume)
        return [];
    const out = [];
    for (const part of parts) {
        const planned = volume[part] || 0;
        if (planned <= 0.05)
            continue;
        const vr = volumeResponse(history, part);
        if (!vr || !vr.best)
            continue;
        const { lo, hi } = vr.best;
        const status = planned < lo - 0.5 ? "under" : planned > hi + 0.5 ? "over" : "in";
        out.push({ part, planned, lo, hi, status, weeks: vr.weeks, confidence: vr.confidence });
    }
    // the actionable ones first — a mismatch is worth a glance, a match is worth a nod
    const rank = { over: 0, under: 1, in: 2 };
    out.sort((a, b) => rank[a.status] - rank[b.status] || b.weeks - a.weeks);
    return out.slice(0, 3);
}

function coachFacts(lm, day, history) {
    const facts = [];
    if (!lm || !day)
        return facts;
    const exs = (day.exercises || []).map(id => EX_BY_ID[id]).filter(Boolean);
    // Coach copy is written for the lifter, not for the model debugger. Keep the underlying signals,
    // but translate each one into: WHAT TO DO -> WHY -> optional evidence. Facts also carry lightweight
    // applicability metadata (`exId`, `part`, `scope`, `priority`) so the live workout can show only the
    // one that matters for the exercise/set in front of you instead of dumping the whole session model.
    for (const ex of exs) {
        const lift = lm.lifts && lm.lifts[ex.id];
        const pl = lift && lift.plateau;
        if (!pl)
            continue;
        if (pl.kind === "fatigue") {
            facts.push({
                icon: "deload",
                key: `plateau-fatigue:${ex.id}`,
                exId: ex.id,
                priority: 100,
                short: `${ex.name}: back off today`,
                title: `Back off on ${ex.name} today`,
                text: "Recent sessions suggest fatigue is building. Keep the reps clean and take a small load reduction if the warm-up feels unusually heavy.",
                evidence: "Recent performance trend"
            });
        }
        else if (pl.kind === "true") {
            facts.push({
                icon: "recal",
                key: `plateau:${ex.id}`,
                exId: ex.id,
                priority: 85,
                short: `${ex.name}: use today's recalculated load`,
                title: `Use today's load on ${ex.name}`,
                text: "Progress has stalled, so today's target is based on your current strength instead of simply repeating the last session.",
                evidence: `${pl.since} sessions without a new best`
            });
        }
    }
    const tired = [];
    for (const part of new Set(exs.map(e => e.part))) {
        const m = lm.muscles && lm.muscles[part];
        if (m && m.readiness < 60)
            tired.push({ part, r: m.readiness });
    }
    tired.sort((a, b) => a.r - b.r);
    for (const t of tired) {
        const label = PART_LABEL[t.part] || t.part;
        facts.push({
            icon: "fatigue",
            key: `readiness:${t.part}`,
            part: t.part,
            priority: 92,
            short: `${label}: use the lighter target`,
            title: `Give ${label.toLowerCase()} a little more recovery`,
            text: "Today's target is already reduced. Follow it as written and don't add the weight back just because the first set feels easy.",
            evidence: `${label} recovery ${t.r}%`
        });
    }
    const eff = lm.effort;
    if (eff && eff.confidence > 0 && Math.abs(eff.bias) >= 0.4) {
        const harderThanLogged = eff.bias > 0;
        facts.push({
            icon: "cal",
            key: "effort-bias",
            scope: "session",
            priority: 60,
            short: "Trust today's load",
            title: "Trust today's prescribed load",
            text: harderThanLogged
                ? "Your sets usually end up harder than you rate them. Today's load already accounts for that, so avoid adding extra weight early."
                : "You usually have a little more left than you rate. Today's load already accounts for that, so use the listed target instead of deliberately holding back.",
            evidence: eff.n ? `Learned from ${eff.n} logged sets` : null
        });
    }
    const primary = exs.find(e => e.type === "compound") || exs[0];
    if (primary && history) {
        const sl = personalRepSlope(history, primary);
        if (Math.abs(sl - 30) >= 2.5) {
            const holdsWell = sl > 30;
            facts.push({
                icon: "curve",
                key: `rep-curve:${primary.id}`,
                exId: primary.id,
                priority: 70,
                short: `${primary.name}: use the listed reps`,
                title: holdsWell ? "You hold reps well as weight climbs" : "Your reps drop sooner as weight climbs",
                text: `The ${primary.name} target uses your own lifting history, so follow the listed reps and load instead of converting from a generic percentage chart.`,
                evidence: "Your logged sets"
            });
        }
    }
    // Volume response is valuable context in Progress, but it usually does not change the NEXT SET.
    // Keep the evidence available to diagnostics/other surfaces while explicitly keeping it out of the
    // live workout feed. The live Coach should earn screen space by changing an action now.
    if (history) {
        for (const part of new Set(exs.map(e => e.part))) {
            const vr = volumeResponse(history, part);
            if (vr && vr.best) {
                const label = PART_LABEL[part] || part;
                facts.push({
                    icon: "dose",
                    key: `volume:${part}`,
                    part,
                    live: false,
                    priority: 0,
                    short: `${label}: current volume is working`,
                    title: `${label} volume looks productive`,
                    text: `Your best recent progress has happened around ${vr.best.lo}–${vr.best.hi} hard sets per week. This plan is keeping ${label.toLowerCase()} near that range.`,
                    evidence: `${vr.weeks} weeks of your training`
                });
            }
        }
    }
    return facts;
}

function workoutPlanCoachFact(program, day, weekIndex) {
    if (!program || program.quick)
        return null;
    const cfg = program.config || {};
    const phaseRaw = String(program.nextEngine?.phase || program.nextEngine?.program?.phase || "").replace(/_/g, " ").toLowerCase();
    if (cfg.deload && weekIndex > weeksOf(program) || phaseRaw.includes("deload") || phaseRaw.includes("recovery")) {
        return { icon: "plan", short: "Today: recover", title: "Today is a recovery session", text: "Keep the work easy, leave plenty in reserve, and finish feeling better than you started." };
    }
    if (phaseRaw.includes("peak")) {
        return { icon: "plan", short: "Today: crisp heavy reps", title: "Today: prioritize crisp heavy reps", text: "Take the full rest, keep technique tight, and stop before reps turn into grinders." };
    }
    if (phaseRaw.includes("strength") || cfg.goal === "strength") {
        return { icon: "plan", short: "Today: quality strength work", title: "Today: build strength", text: "Use the listed load and rest. Keep every rep controlled and save your hardest effort for the sets that call for it." };
    }
    if (phaseRaw.includes("hypertrophy") || phaseRaw.includes("accumulation") || cfg.goal === "muscle") {
        return { icon: "plan", short: "Today: build quality volume", title: "Today: build quality volume", text: "Use the listed reps, rest, and effort. Keep the early sets controlled so you can finish the exercise strong." };
    }
    const ph = phaseFor(program, weekIndex);
    if (ph === PHASES.peak)
        return { icon: "plan", short: "Today: crisp heavy reps", title: "Today: prioritize crisp heavy reps", text: "Take the full rest, keep technique tight, and stop before reps turn into grinders." };
    if (ph === PHASES.intensification)
        return { icon: "plan", short: "Today: heavier, controlled work", title: "Today: push the working sets", text: "Loads are heavier now. Keep form clean and avoid spending extra effort on the early sets." };
    return { icon: "plan", short: "Today: build quality volume", title: "Today: build quality volume", text: "Keep the prescribed reps controlled and leave enough in reserve to maintain quality across the whole session." };
}

function liveCoachForContext(facts, row, ex, sessionDone) {
    if (!row || !ex)
        return [];
    const work = (row.sets || []).filter(s => !s.warm && !s.sub);
    const done = work.filter(s => s.done);
    const next = work.find(s => !s.done);
    if (done.length && next) {
        const last = [...work].reverse().find(s => s.done && s.actualRIR != null);
        if (last) {
            const actual = Number(last.actualRIR);
            const target = prescribedRIRof(last);
            if (Number.isFinite(actual) && Number.isFinite(target)) {
                if (actual <= target - 1) {
                    return [{
                            icon: "fatigue",
                            key: `set-hard:${ex.id}:${done.length}`,
                            context: "next-set",
                            short: "Next set: don't add weight",
                            title: "That set was harder than planned",
                            text: "Keep the next set at the listed load. If your form or rep speed drops, take a small reduction instead of forcing it.",
                            evidence: `You logged ${actual} RIR · target ${target}`
                        }];
                }
                if (actual >= target + 2) {
                    return [{
                            icon: "cal",
                            key: `set-easy:${ex.id}:${done.length}`,
                            context: "next-set",
                            short: "Next set: stay with the plan",
                            title: "That set was easier than planned",
                            text: "Use the next set as listed rather than jumping ahead. If it stays this easy, that is useful evidence for your next comparable workout.",
                            evidence: `You logged ${actual} RIR · target ${target}`
                        }];
                }
            }
        }
        return [];
    }
    if (done.length || !next)
        return [];
    const relevant = (facts || []).filter(f => {
        if (!f || f.live === false)
            return false;
        if (f.exId)
            return f.exId === ex.id;
        if (f.part)
            return f.part === ex.part;
        return f.scope === "session" && sessionDone === 0;
    }).sort((a, b) => (b.priority || 0) - (a.priority || 0));
    return relevant.length ? [{ ...relevant[0], context: "exercise-start" }] : [];
}

const PROG_POLICIES = Object.freeze({ next: Object.freeze({ id: "pursuit-next" }) });

const MYO_MINI_SETS = 3;

const MYO_MAX_SETS = 5;

const MYO_MINI_REPS = 5;

const MYO_MIN_REPS = 3;

const MYO_TARGET_REPS = `${MYO_MIN_REPS}-${MYO_MINI_REPS}`;

function makeMyoMini(w, prescribed = true) {
    const ws = w != null && w !== "" ? String(w) : "";
    return { weight: ws, reps: String(MYO_MINI_REPS), myo: true, sub: true, kind: "myo", done: false,
        ...(prescribed ? { prescribed: true } : {}),
        target: { w: ws || "\u2014", reps: MYO_TARGET_REPS, myo: true } };
}

function growMyoSets(sets) {
    if (!Array.isArray(sets) || !sets.length)
        return sets;
    let lastMyo = -1;
    let count = 0;
    sets.forEach((s, i) => { if (s && s.sub && s.kind === "myo") {
        lastMyo = i;
        count++;
    } });
    if (lastMyo < 0 || count >= MYO_MAX_SETS)
        return sets;
    const last = sets[lastMyo];
    if (!last.done)
        return sets;
    const reps = parseInt(last.reps);
    if (!(reps >= MYO_MIN_REPS))
        return sets; // couldn't hold the range — the extension is over
    /* NO LOAD GUARD. An earlier version refused to grow unless the last mini carried a positive
       weight, which silently disabled the whole rule for BODYWEIGHT and ASSISTED work — and for any
       loaded lift with no history yet, where the row opens blank because there is nothing to suggest.
       The DOM gate caught it: three minis went in, the last was logged at the target reps, and nothing
       appended. A blank load is a load the lifter has not typed yet, not a reason to end the set.
       syncSubSets fills the new row from the activation set on the same pass anyway, so carrying the
       last mini's weight through — blank or not — is both correct and self-correcting. */
    return [...sets.slice(0, lastMyo + 1), makeMyoMini(last.weight, !!last.prescribed), ...sets.slice(lastMyo + 1)];
}

function syncSubSets(sets, ex, unit) {
    if (!Array.isArray(sets) || !sets.length)
        return sets;
    const step = loadStep(ex, unit);
    let actW = null; // load of the working set these extensions hang off
    let changed = false;
    const out = sets.map(s => {
        if (!s)
            return s;
        if (!s.sub) {
            if (!s.warm) {
                const w = parseFloat(s.weight);
                if (w > 0)
                    actW = w;
            }
            return s;
        }
        if (s.done || userOwnsRuntimeSet(s) || !(actW > 0))
            return s;
        let want;
        if (s.kind === "drop") {
            if (!(s.dropF > 0))
                return s; // legacy row with no recorded fraction — don't invent one
            want = String(Math.max(step, roundTo(actW * s.dropF, step)));
        }
        else {
            want = String(actW); // myo mini: the activation load, unchanged
        }
        if (String(s.weight) === want && String(s.target?.w) === want)
            return s;
        changed = true;
        return { ...s, weight: want, target: { ...(s.target || {}), w: want } };
    });
    return changed ? out : sets;
}

function auditSets(o) {
    const { program, day, ex, slot, weekIndex, unit, perf, history, dayPerf } = o;
    const out = [];
    const at = (rule, severity, detail, setIndex = null) => out.push({ rule, severity, detail, setIndex, exId: ex.id, exName: ex.name, dayId: day.id, dayLabel: day.label, weekIndex });
    const isPrimary = slot === day.primaryIndex;
    let sets, sug, range;
    /* `__sets` is a TEST HOOK, following the `__poseJoints` precedent already in the renderer: it lets
       gates/setaudit.mjs re-run these rules over a deliberately DAMAGED copy of a real prescription, so
       each rule is proven to catch its own defect without mutating the engine. Never set in the app —
       when absent, the prescription is computed exactly as before. A clean audit that has never been
       shown to fail is worth nothing, and this is what makes the proof cheap enough to keep. */
    try {
        /* ⚠ THE PHASE RULE IS COMPUTED IN TWO PLACES AND THEY DISAGREED. `computeCell` opts percent-scheme
           and manual programs OUT of block periodization (`phase = null` -> static range); this line asked
           `blockPhase` unconditionally. So a lifter on Main-Lift Waves was PRESCRIBED from one range and
           SHOWN another: Triceps Dip target 4 reps against a printed range of 6-10, and the in-app
           self-test reported it as 56 findings on that template alone.
           The prescription is the one that must win — it is what the lifter performs — so the display
           follows the same opt-out. Present since the block-phase work, not a recent regression. */
        const cell = computeCell(program, day, ex.id, slot, weekIndex);
        range = cellRepRange(cell, program, ex, isPrimary);
        sug = sessionSuggestion(program, day, slot, dayPerf || perf, unit, weekIndex, history);
        sets = o.__sets || prescribeSets(program, day, ex, slot, weekIndex, unit, sug, dayPerf || perf, perf, history, true);
    }
    catch (e) {
        at("threw", "error", `prescribing this exercise threw: ${e.message}`);
        return out;
    }
    if (!Array.isArray(sets) || !sets.length) {
        at("no-sets", "error", "no sets were prescribed at all");
        return out;
    }
    const step = loadStep(ex, unit) || 0;
    const rack = gymRackFor(ex, unit);
    const ceiling = gymCapFor(ex, unit);
    const num = (v) => (v === "" || v == null ? null : Number(v));
    const work = sets.filter(isWorkSet);
    const workW = work.map(s => num(s.weight)).filter(w => w != null && w > 0);
    const topWork = workW.length ? Math.max(...workW) : null;
    sets.forEach((s, i) => {
        const w = num(s.weight), r = num(s.reps);
        /* A LOAD MUST BE A NUMBER. NaN and Infinity reach the screen as blank or "Infinity" and are the
           single most visible class of engine failure. */
        if (w != null && !Number.isFinite(w))
            at("load-not-finite", "error", `weight is ${s.weight}`, i);
        if (w != null && Number.isFinite(w) && w < 0)
            at("load-negative", "error", `weight is ${w}`, i);
        if (r != null && !Number.isFinite(r))
            at("reps-not-finite", "error", `reps is ${s.reps}`, i);
        if (r != null && Number.isFinite(r) && r <= 0 && !s.amrap)
            at("reps-nonpositive", "error", `reps is ${r}`, i);
        /* A LOAD MUST BE LOADABLE. If the smallest jump on this implement is 5, a prescription of 137.5
           is a number the lifter cannot put on the bar. Rack-based implements are exempt because their
           available loads are a fixed list, not a multiple — checked separately below. */
        if (w != null && Number.isFinite(w) && w > 0 && step > 0 && !(rack && rack.length)) {
            const rem = Math.abs(w / step - Math.round(w / step));
            if (rem > 1e-6)
                at("load-not-loadable", "error", `${w} ${unit} is not a multiple of the ${step} ${unit} increment`, i);
        }
        if (w != null && Number.isFinite(w) && w > 0 && rack && rack.length) {
            if (!rack.some(v => Math.abs(v - w) < 1e-6))
                at("load-not-on-rack", "warn", `${w} ${unit} is not one of the available weights`, i);
        }
        if (w != null && Number.isFinite(ceiling) && ceiling > 0 && w > ceiling + 1e-6) {
            at("load-over-gym-ceiling", "error", `${w} ${unit} exceeds the heaviest available ${ceiling} ${unit}`, i);
        }
        /* A REP TARGET MUST SIT IN THE RANGE PRINTED BESIDE IT. Warm-ups, myo minis and drop sets are
           short by design and an AMRAP has no ceiling — those are intended and are not findings. */
        /* ⚠ A NAMED PERCENT SCHEME OWNS ITS OWN REPS, AND THE GENERIC RANGE DOES NOT APPLY TO IT. 5's PRO
           prescribes FIVE reps on every main set, every week, at rising percentages of training max —
           that is the programme, not a mistake. The generic target narrows to 2-3 in the peak week, so
           this check reported the template doing exactly what it is supposed to do: "Deadlift wk4 — asks
           for 5 reps but the target range is 2-3". A scheme-governed main lift is exempt; its accessories
           are NOT, and neither is any lift on a program without a scheme. */
        const schemeOwned = !!program?.config?.percentScheme
            && (isPrimary || slot === day?.t2Index);
        if (isWorkSet(s) && !s.amrap && !schemeOwned && r != null && Number.isFinite(r) && range && range.length === 2) {
            const [lo, hi] = range;
            if (r < lo || r > hi)
                at("reps-outside-range", "error", `asks for ${r} reps but the target range is ${lo}-${hi}`, i);
        }
        /* RIR MUST BE SANE. A target of 12 reps in reserve is not a working set.
           PARSED WITH `parseRIRNum`, NOT `Number` — a target RIR is legitimately a RANGE string ("0-1"),
           which `Number` turns into NaN. My first version of this rule used Number and reported 55,782
           findings across 2,484 programs, every one of them my own bug rather than the app's. A rule that
           fires on almost everything is a broken rule, not a discovery: check the shape of the value
           before believing the count. */
        const rirRaw = s.target && s.target.rir != null ? s.target.rir : null;
        const rir = rirRaw != null ? parseRIRNum(rirRaw) : null;
        if (rirRaw != null && (rir == null || !Number.isFinite(rir) || rir < 0 || rir > 6))
            at("rir-implausible", "error", `target RIR is ${rirRaw}`, i);
        /* WHAT IS SHOWN MUST BE WHAT IS TARGETED. The set's own target block is what the UI prints under
           the load; if it disagrees with the load itself, two numbers on one screen describe one set. */
        if (s.target && s.target.w != null && w != null && Number.isFinite(w)) {
            const tw = num(s.target.w);
            if (tw != null && Number.isFinite(tw) && Math.abs(tw - w) > 1e-6)
                at("target-disagrees-with-load", "error", `set shows ${w} but its target says ${tw}`, i);
        }
    });
    /* A WARM-UP RAMP MUST CLIMB, AND MUST STAY UNDER THE WORK SET. A warm-up at or above the working
       load is the clearest possible sign the ramp was built from the wrong number. */
    const warms = sets.filter(s => s.warm).map(s => num(s.weight)).filter(w => w != null && w > 0);
    for (let i = 1; i < warms.length; i++) {
        if (warms[i] < warms[i - 1])
            at("warmup-not-ascending", "error", `warm-up drops from ${warms[i - 1]} to ${warms[i]}`, i);
    }
    if (topWork != null && warms.some(w => w > topWork + 1e-6)) {
        at("warmup-heavier-than-work", "error", `a warm-up (${Math.max(...warms)}) is heavier than the work set (${topWork})`);
    }
    /* AN EXTENSION HANGS OFF THE SET BEFORE IT. A myo mini or drop set carrying MORE load than the set
       that produced it describes something that cannot happen. */
    sets.forEach((s, i) => {
        if (!s.sub || i === 0)
            return;
        let j = i - 1;
        while (j >= 0 && sets[j].sub)
            j--;
        const parent = j >= 0 ? num(sets[j].weight) : null, mine = num(s.weight);
        if (parent != null && mine != null && Number.isFinite(parent) && Number.isFinite(mine) && mine > parent + 1e-6) {
            at("extension-heavier-than-parent", "error", `an extension set carries ${mine} but hangs off a set of ${parent}`, i);
        }
    });
    /* WORK SETS MUST EXIST. A prescription that is entirely warm-up is not a workout. */
    if (!work.length)
        at("no-work-sets", "error", "every prescribed set is a warm-up or an extension");
    return out;
}

function auditProgramWeek(program, weekIndex, opts) {
    const out = [];
    const o = opts || {};
    const add = (rule, detail, severity = "advisory") => out.push({ rule, severity, detail, weekIndex });
    let vol;
    try {
        vol = weeklyVolume(program, weekIndex);
    }
    catch (e) {
        add("volume-threw", e.message, "error");
        return out;
    }
    /* A WEEK WITH NO PULLING IN IT. Measured across 210 generated programs: 25 of them — 12% — contain
       ZERO volume for an entire movement pattern, and it is ALWAYS pull. Concentrated in short sessions
       (15 at s20, 8 at s40, 2 at s60) but not confined to them: a 2-day full body at s60 trains no back
       at all for the whole week. That is not a volume shortfall to be argued about, it is a program
       missing half of training, and nothing in the app noticed. This is the check that notices.

       GROUPS, THE THRESHOLD AND THE TRAINABILITY TEST NOW LIVE IN ONE PLACE (`PATTERN_GROUPS`,
       `PATTERN_MIN_SETS`, `patternTrainable`) because the GENERATOR reads them too as of engine 6.
       They were written here first; a private copy on each side is the v584 divergence class, and it
       matters more than usual here — a generator that fills to one definition while the audit reports
       against another produces a program that is green on screen and broken in the file, or the
       reverse. Behaviour is unchanged from the inlined version. */
    const kit = (() => { try {
        return expandEquipment((program.config && program.config.equipment) || []);
    }
    catch {
        return null;
    } })();
    Object.entries(PATTERN_GROUPS).forEach(([name, parts]) => {
        const total = parts.reduce((s, m) => s + (vol[m] || 0), 0);
        if (total >= PATTERN_MIN_SETS)
            return;
        if (!patternTrainable(parts, kit)) {
            add("pattern-unavailable", `no ${name} volume — this equipment offers no way to train it`, "info");
            return;
        }
        add("pattern-absent", `the whole week contains no ${name} volume`, "error");
    });
    /* A MUSCLE THE LIFTER ASKED FOR MUST ACTUALLY BE TRAINED. Muscles nobody prioritised falling under
       MEV is arithmetic, not a defect — a 40-minute session cannot give seventeen muscles eight sets
       each, and the app already tracks that trade-off as partsUnderMEV. A muscle the lifter explicitly
       FOCUSED is a different claim: the app said it would emphasise it. */
    const focused = Array.isArray(o.focusList) ? o.focusList : Object.keys((program.config && program.config.focus) || {});
    focused.forEach(part => {
        const mev = landmarkFor(part).mev, sets = vol[part] || 0;
        if (sets < mev)
            add("focus-under-mev", `${PART_LABEL[part] || part} is emphasised but gets ${sets.toFixed(1)} sets, under its ${mev}-set minimum`);
    });
    /* AND NOTHING MAY EXCEED THE CEILING THE ENGINE ITSELF ENFORCES. compositeMrv, not the raw
       landmark — see the note above. */
    Object.entries(vol).forEach(([part, sets]) => {
        if (!Number.isFinite(sets) || sets <= 0)
            return;
        if (!landmarkFor(part))
            return;
        const ceiling = compositeMrv(part);
        if (Number.isFinite(ceiling) && sets > ceiling + 0.5)
            add("over-engine-ceiling", `${PART_LABEL[part] || part} at ${sets.toFixed(1)} sets exceeds the planned ${ceiling}-set ceiling`);
    });
    return out;
}

function simulateAndAudit(o) {
    const { program, unit = "lb", weeks = null, behaviour = "hit" } = o || {};
    const findings = [];
    const history = [];
    let perf = {};
    const total = weeks || weeksOf(program);
    for (let w = 1; w <= total; w++) {
        program.days.forEach(day => {
            /* ⚠ ASK WHAT THE SLOT HOLDS **THIS WEEK**. A paired slot trains a different exercise on
               alternate weeks, so reading `day.exercises[slot]` audits and logs the wrong lift on half of
               them. `exerciseAt` is the one answer to that question; it returns the slot's own exercise
               when no pair is set, so this is inert until pairing ships. */
            day.exercises.forEach((_id, slot) => {
                const id = exerciseAt(program, day, slot, w);
                const ex = EX_BY_ID[id];
                if (!ex)
                    return;
                findings.push(...auditSets({ program, day, ex, slot, weekIndex: w, unit, perf, history, dayPerf: perf }));
            });
            // Log the day, so the NEXT week is prescribed from something the lifter did.
            const logged = {};
            day.exercises.forEach((_id, slot) => {
                const id = exerciseAt(program, day, slot, w);
                const ex = EX_BY_ID[id];
                if (!ex)
                    return;
                let sets = null, sug = null;
                try {
                    sug = sessionSuggestion(program, day, slot, perf, unit, w, history);
                    sets = prescribeSets(program, day, ex, slot, w, unit, sug, perf, perf, history, false);
                }
                catch {
                    return;
                }
                const work = (sets || []).filter(isWorkSet);
                if (!work.length)
                    return;
                /* SEED A STARTING LOAD ON THE FIRST SESSION. Without this the simulation is VACUOUS and looks
                   clean: with no history the engine has nothing to progress from and prescribes a blank load,
                   the simulated lifter logs 0, and every later week reads that 0 as the last performance — so
                   the lifter never lifts anything, no warm-up ramp is ever built, and the audit sweeps a
                   program in which no weight was ever prescribed. It reported ZERO findings across 2,484
                   programs and 1.5 million sets while three separate injected defects walked straight past it.
                   Caught by mutation testing, which is the only reason it was caught at all. */
                const rawSeed = ex.equip.includes("barbell") || ex.equip.includes("ezbar") || ex.equip.includes("smith") ? 95
                    : ex.equip.includes("dumbbell") ? 30
                        : ex.equip.includes("machine") || ex.equip.includes("cable") ? 60
                            : ex.equip.length === 0 ? 0 : 45;
                /* THE SEED MUST ITSELF BE LOADABLE. The first version used a flat 45, which is not a multiple
                   of the 10 lb step on a hack squat or a leg press — so the harness fabricated a load no
                   lifter could set, the engine faithfully carried it forward, and the audit reported 40
                   "load-not-loadable" findings that were entirely its own doing. An auditor must not
                   manufacture inputs that violate the invariant it is checking; that is how a sweep produces
                   confident nonsense. Snapped to the exercise's own increment. */
                const seedStep = loadStep(ex, unit) || 0;
                const seed = seedStep > 0 ? Math.max(seedStep, Math.round(rawSeed / seedStep) * seedStep) : rawSeed;
                const wt = Number(work[0].weight) || (perf[id] ? Number(perf[id].weight) : 0) || seed;
                const target = Number(work[0].reps) || 8;
                const reps = behaviour === "miss" ? Math.max(1, target - 3) : behaviour === "stall" ? target : target;
                const useW = behaviour === "stall" ? (perf[id] ? Number(perf[id].weight) || wt : wt) : wt;
                logged[id] = { weight: useW, reps, sets: work.map(() => ({ w: useW, r: reps, rir: 2 })), date: Date.now() };
            });
            if (Object.keys(logged).length) {
                history.unshift({ id: `sim-${w}-${day.id}`, programId: program.id, dayId: day.id, dayLabel: day.label, weekIndex: w, date: Date.now() - (total - w) * 86400000, perf: logged, volume: 1000, setsDone: 3 });
                perf = { ...perf, ...logged };
            }
        });
    }
    return findings;
}

function weekKeyOf(d) {
    const dt = new Date(d);
    const off = (dt.getDay() + 6) % 7; // Monday-based: Sunday is 6, not 0
    const m = new Date(dt);
    m.setDate(dt.getDate() - off);
    m.setHours(0, 0, 0, 0);
    return m.getTime();
}

function sessionE1RM(p) {
    if (!p)
        return 0;
    const sets = Array.isArray(p.sets) ? p.sets.filter(s => s && s.w > 0 && s.r > 0) : [];
    if (sets.length)
        return Math.max(...sets.map(s => e1rm(s.w, s.r)));
    return (p.weight > 0 && p.reps > 0) ? e1rm(p.weight, p.reps) : 0;
}

function lastTopSet(p) {
    const sets = setsOf(p).filter(s => s && s.w > 0 && !s.warm && !s.sub);
    if (!sets.length)
        return p && p.weight > 0 ? { w: p.weight, r: p.reps != null ? p.reps : null, rir: null, summary: true } : null;
    const score = s => (s.r > 0 ? e1rm(s.w, s.r) : s.w);
    const best = sets.reduce((m, s) => (score(s) > score(m) ? s : m), sets[0]);
    return { w: best.w, r: best.r != null ? best.r : null, rir: best.rir != null ? best.rir : null };
}

function betterTopSet(a, b) {
    if (!a)
        return b;
    if (!b)
        return a;
    if ((b.w || 0) > (a.w || 0))
        return b;
    if ((b.w || 0) === (a.w || 0) && (b.r || 0) > (a.r || 0))
        return b;
    return a;
}

function isWorkSet(s) { return !!s && !s.warm && !s.sub; }

function exRecords(history, id, unit = "kg") {
    let maxWeight = null, maxE1rm = null, maxVol = null;
    const perRep = {}; // reps → { w, date }
    canonicalHistoryNewest(history).forEach(h => {
        const p = h.perf?.[id];
        if (!(p && p.weight > 0))
            return;
        const u = unit;
        const sets = completedHistorySets(p).map(x => ({ ...x, w: convertHistoryLoad(x.w, h.unit, unit) }));
        sets.forEach(s => {
            if (!(s.w > 0) || s.sub)
                return; // exclude sub-sets (myo-reps, drop sets) from records
            if (betterTopSet(maxWeight, { w: s.w, r: s.r }) !== maxWeight)
                maxWeight = { w: s.w, r: s.r, date: h.date, unit: u, sourceUnit: h.unit || unit };
            if (s.r != null) {
                const e = e1rm(s.w, s.r);
                if (!maxE1rm || e > maxE1rm.v)
                    maxE1rm = { v: e, date: h.date, unit: u, sourceUnit: h.unit || unit };
                const vol = s.w * s.r;
                if (!maxVol || vol > maxVol.v)
                    maxVol = { v: vol, date: h.date, unit: u, sourceUnit: h.unit || unit };
                if (!perRep[s.r] || s.w > perRep[s.r].w)
                    perRep[s.r] = { w: s.w, date: h.date, unit: u, sourceUnit: h.unit || unit };
            }
        });
    });
    const originalUnits = record => {
        if (!record) return record;
        const { sourceUnit, ...out } = record;
        if (out.w != null) out.w = convertHistoryLoad(out.w, unit, sourceUnit);
        if (out.v != null) out.v = convertHistoryLoad(out.v, unit, sourceUnit);
        return { ...out, unit: sourceUnit };
    };
    return { maxWeight: originalUnits(maxWeight), maxE1rm: originalUnits(maxE1rm),
        maxVol: originalUnits(maxVol), perRep: Object.fromEntries(Object.entries(perRep).map(([r, v]) => [r, originalUnits(v)])) };
}

const EX_WINDOWS = [
    { id: "1W", label: "1W", days: 7 },
    { id: "1M", label: "1M", days: 30 },
    { id: "3M", label: "3M", days: 91 },
    { id: "6M", label: "6M", days: 182 },
    { id: "1Y", label: "1Y", days: 365 },
    { id: "all", label: "All", days: null },
];

const EX_METRICS = [
    { id: "e1rm", label: "1-RM", reps: 1, kind: "load", title: "Estimated 1-rep max", blurb: "Your best set each session, converted to a one-rep max — the cleanest single measure of strength." },
    { id: "e3rm", label: "3-RM", reps: 3, kind: "load", title: "Estimated 3-rep max", blurb: "What your best set implies you could hold for three reps. An estimate, not a set you performed." },
    { id: "e10rm", label: "10-RM", reps: 10, kind: "load", title: "Estimated 10-rep max", blurb: "What your best set implies you could hold for ten reps — the load your hypertrophy work sits near." },
    { id: "top", label: "Heaviest weight", kind: "load", title: "Heaviest weight", blurb: "The heaviest load you actually put in your hands each session. No estimate involved." },
    { id: "vol", label: "Total volume", kind: "vol", title: "Total volume", blurb: "Weight × reps across every working set of the session — how much work the lift did." },
    { id: "volset", label: "Volume per set", kind: "vol", title: "Volume per set", blurb: "Session volume divided by working sets — whether each set is doing more work, independent of how many you did." },
];

const EX_METRIC_BY_ID = Object.fromEntries(EX_METRICS.map(m => [m.id, m]));

const exWindow = id => EX_WINDOWS.find(w => w.id === id) || EX_WINDOWS[EX_WINDOWS.length - 1];

function rmAt(est, reps) { const r = Math.min(Math.max(reps || 1, 1), E1RM_REP_CAP); return r <= 1 ? est : est / (1 + r / EPLEY_SLOPE); }

function exerciseSeries(history, id, metricId = "e1rm", windowId = "all", now = Date.now()) {
    const metric = EX_METRIC_BY_ID[metricId] || EX_METRICS[0];
    const win = exWindow(windowId);
    const since = win.days == null ? -Infinity : now - win.days * 86400000;
    const rows = [];
    const canonicalHistory = canonicalHistoryNewest(history);
    let unit = canonicalHistory.find(h => validHistoryDate(h) && h.perf?.[id])?.unit || 'kg';
    for (const h of canonicalHistory) {
        if (!h || typeof h !== "object" || !h.perf)
            continue;
        if (!(h.date >= since))
            continue;
        const p = h.perf[id];
        if (!p)
            continue;
        const sets = completedHistorySets(p).filter(s => s.w > 0).map(s => ({ ...s, w: convertHistoryLoad(s.w, h.unit, unit) }));
        if (!sets.length)
            continue;

        const withReps = sets.filter(s => s.r > 0);
        const bestE = withReps.length ? Math.max(...withReps.map(s => e1rm(s.w, s.r))) : Math.max(...sets.map(s => s.w));
        const topW = Math.max(...sets.map(s => s.w));
        const vol = withReps.reduce((n, s) => n + s.w * s.r, 0);
        let v = null;
        if (metric.kind === "load")
            v = metric.reps ? rmAt(bestE, metric.reps) : topW;
        else if (metric.id === "vol")
            v = vol;
        else
            v = sets.length ? vol / sets.length : 0;
        if (!(v > 0))
            continue;
        rows.push({ date: h.date, v: Math.round(v * 10) / 10, sets: sets.length, reps: withReps.reduce((n, s) => n + s.r, 0), top: topW, e1rm: bestE, unit });
    }
    rows.sort((a, b) => a.date - b.date);
    if (!rows.length)
        return { ok: false, metric, window: win, points: [], sessions: 0, unit: unit || "kg" };
    const vals = rows.map(r => r.v);
    const bestRow = rows.reduce((m, r) => (r.v > m.v ? r : m), rows[0]);
    return {
        ok: true, metric, window: win, unit: unit || "kg", points: rows, sessions: rows.length,
        avg: Math.round((vals.reduce((a, b) => a + b, 0) / vals.length) * 10) / 10,
        best: bestRow.v, bestDate: bestRow.date,
        first: rows[0].v, last: rows[rows.length - 1].v,
        delta: Math.round((rows[rows.length - 1].v - rows[0].v) * 10) / 10,
        from: rows[0].date, to: rows[rows.length - 1].date
    };
}

function exerciseTrends(history, dayId = null) {
    const map = {}, units = {};
    const ordered = normalizeHistoryRevisions(history).entries;
    for (const h of ordered) if (!dayId || h.dayId === dayId)
        for (const id of Object.keys(h.perf || {})) units[id] = h.unit || units[id] || 'kg';
    ordered.forEach(h => {
        if (!h || typeof h !== "object" || !h.perf)
            return; // a corrupt entry skips, never throws
        if (dayId && h.dayId !== dayId)
            return;
        Object.entries(h.perf).forEach(([id, p]) => {
            if (!(p && p.weight > 0))
                return;
            const unit = units[id];
            const raw = completedHistorySets(p).map(s => ({ ...s, w: convertHistoryLoad(s.w, h.unit, unit) }));
            const sets = raw.filter(s => s.w > 0).map(s => ({ w: s.w, r: s.r ?? null, e1rm: s.r != null ? e1rm(s.w, s.r) : s.w }));
            if (!sets.length)
                return;
            const best = sets.reduce((m, s) => (s.e1rm > m.e1rm ? s : m), sets[0]);
            /* `top` is the set this session is REMEMBERED BY — what shows as the record. It must come from
               the sets that were actually logged, NOT from the perf summary (p.weight/p.reps).
               The summary's reps are deliberately the MINIMUM across the sets at the top weight: it exists to
               anchor the next session's load on something you held for every set, which is the right, cautious
               number for PROGRESSION. It is the wrong number for a RECORD. Bench 225×6 then 225×5 and the
               summary says 225×5 — accurate as "what you held throughout", but shown as your record it reports
               a set you beat. `best` (highest e1RM) was already computed here from the real sets and is the
               set that deserves the billing; reuse it so the record and the number driving the PR line can
               never disagree again. */
            (map[id] = map[id] || []).push({ date: h.date, unit, sets, best: best.e1rm, top: { w: best.w, r: best.r } });
        });
    });
    return Object.entries(map).map(([id, sessions]) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return null;
        const last = sessions[sessions.length - 1];
        const prBest = Math.max(...sessions.map(s => s.best));
        /* The heaviest load actually put on the bar, as distinct from prBest, which is an ESTIMATE off a
           submaximal set. A 5-rep 245 estimates ~275; only one of those is a number you have lifted.
           Both are legitimate and they answer different questions, so both are carried. */
        const prTopW = Math.max(...sessions.map(s => (s.top && s.top.w > 0 ? s.top.w : 0)));
        return {
            id, name: ex.name, part: ex.part, sessions, first: sessions[0], last,
            sessionsCount: sessions.length, pts: sessions.map(s => s.best),
            prBest, prTopW, unit: last.unit, delta: last.best - sessions[0].best
        };
    }).filter(Boolean).sort((a, b) => b.last.date - a.last.date);
}

function blockRetro(history, opts = {}) {
    const WEEK = 7 * 86400000, now = Date.now();
    const weeks = opts.weeks || 6;
    const since = opts.sinceDate || (now - weeks * WEEK);
    const until = opts.untilDate || now;
    const win = (history || []).filter(h => h && h.date && h.date >= since && h.date <= until);
    const spanWeeks = Math.max(1, (until - since) / WEEK);
    if (win.length < 3)
        return { ok: false, reason: "Not enough logged sessions in this window to say anything honest.", sessions: win.length, weeks: Math.round(spanWeeks) };
    const r1 = v => Math.round(v * 10) / 10, r2 = v => Math.round(v * 100) / 100;
    const median = a => { if (!a.length)
        return null; const v = a.slice().sort((x, y) => x - y); const m = Math.floor(v.length / 2); return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2; };
    const slopePctWk = ser => {
        if (ser.length < 3)
            return 0;
        const t0 = ser[0].date, xs = ser.map(s => (s.date - t0) / WEEK), ys = ser.map(s => s.e);
        const mx = xs.reduce((a, b) => a + b, 0) / xs.length, my = ys.reduce((a, b) => a + b, 0) / ys.length;
        let num = 0, den = 0;
        for (let i = 0; i < xs.length; i++) {
            num += (xs[i] - mx) * (ys[i] - my);
            den += (xs[i] - mx) ** 2;
        }
        return my > 0 && den > 0 ? 100 * (num / den) / my : 0; // %/wk relative to mean e1RM
    };
    // Per-lift e1RM series within the window
    const byLift = {};
    for (const h of [...win].sort((a, b) => a.date - b.date)) {
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex)
                continue;
            const ss = setsOf(p);
            let best = 0;
            for (const s of ss) {
                const w = +(s.w != null ? s.w : s.weight), r = +(s.r != null ? s.r : s.reps);
                if (w > 0 && r > 0 && isWorkSet(s)) {
                    const e = e1rm(convertHistoryLoad(w, h.unit, "kg"), r);
                    if (e > best)
                        best = e;
                }
            }
            if (best > 0)
                (byLift[id] = byLift[id] || []).push({ date: h.date, e: best });
        }
    }
    const lifts = Object.entries(byLift).map(([id, ser]) => {
        const ex = EX_BY_ID[id];
        const deltaPct = 100 * (ser[ser.length - 1].e - ser[0].e) / ser[0].e;
        const state = ser.length < 3 ? "thin" : (deltaPct >= 1.5 ? "progressed" : deltaPct <= -1.5 ? "regressed" : "flat");
        return { id, name: ex.name, part: ex.part, sessions: ser.length, deltaPct: r1(deltaPct), slopePctWk: r2(slopePctWk(ser)), state };
    });
    // Per-muscle: direct working sets/wk in the window, paired with the muscle's aggregate e1RM trend
    const setsByPart = {};
    for (const h of win)
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex || !p.sets)
                continue;
            const n = p.sets.filter(s => isWorkSet(s)).length;
            if (n)
                setsByPart[ex.part] = (setsByPart[ex.part] || 0) + n;
        }
    const muscles = Object.keys(setsByPart).map(part => {
        const avgWk = setsByPart[part] / spanWeeks;
        const L = landmarkFor(part), mav = personalMav(history, part), gmav = mavFor(part);
        const partLifts = lifts.filter(l => l.part === part && l.sessions >= 3);
        const prog = partLifts.length ? median(partLifts.map(l => l.deltaPct)) : null;
        const trend = prog == null ? "unknown" : (prog >= 1.5 ? "progressed" : prog <= -1.5 ? "regressed" : "flat");
        const zone = avgWk < L.mev ? "under MEV" : avgWk >= L.mrv ? "at/over MRV" : avgWk >= mav ? "near MAV" : "productive";
        let rec = null, drove = null;
        if (trend === "progressed") {
            drove = `up ${prog > 0 ? "+" : ""}${r1(prog)}% at ${Math.round(avgWk)} sets/wk (${zone})`;
            rec = avgWk < mav ? `Working — and you're below your MAV (~${Math.round(mav)}). Hold, or add 1–2 sets to press the advantage.` : `Working near your MAV. Hold volume; let load keep climbing.`;
        }
        else if (trend === "flat" || trend === "regressed") {
            drove = `${trend}, ${Math.round(avgWk)} sets/wk (${zone})`;
            rec = zone === "under MEV" ? `Under-dosed at ${Math.round(avgWk)} sets/wk — add volume toward MEV ${L.mev}${avgWk < mav ? `–MAV ~${Math.round(mav)}` : ""} next block.`
                : (zone === "at/over MRV" || zone === "near MAV") ? `High volume (${Math.round(avgWk)} sets/wk) with no progress — volume isn't the lever. Deload, then reduce or vary the exercise.`
                    : `Stalled at productive volume — deload or swap the exercise; chase reps before adding load.`;
        }
        return { part, avgSets: r1(avgWk), mev: L.mev, mav: r1(mav), mavPersonal: Math.abs(mav - gmav) > 0.05, mrv: L.mrv, zone, trend, drove, rec };
    }).sort((a, b) => ({ regressed: 0, flat: 1, unknown: 2, progressed: 3 }[a.trend] - { regressed: 0, flat: 1, unknown: 2, progressed: 3 }[b.trend]) || a.avgSets - b.avgSets);
    const gained = lifts.filter(l => l.state === "progressed").length;
    const stalled = lifts.filter(l => l.state === "flat" || l.state === "regressed").length;
    return {
        ok: true, weeks: Math.round(spanWeeks), sessions: win.length,
        headline: `${win.length} sessions over ~${Math.round(spanWeeks)} wk · ${gained} lift${gained !== 1 ? "s" : ""} up, ${stalled} flat`,
        lifts: lifts.sort((a, b) => b.deltaPct - a.deltaPct),
        muscles,
        recs: muscles.filter(m => m.rec).map(m => ({ part: m.part, trend: m.trend, rec: m.rec }))
    };
}

const MIN_DAY_SESSIONS = 3;

let _dstCache = { key: null, val: null };

function dayScopedTrends(history) {
    const hs = canonicalHistoryNewest(history);
    // Content fingerprint, not array identity: identity alone would serve a stale index to any caller
    // that mutates history in place. EXERCISES.length is folded in because exerciseTrends() drops ids
    // missing from EX_BY_ID — a custom exercise registered after the first call would otherwise stay
    // invisible to every later one.
    const key = `${lifterModelKey(hs)}:${EXERCISES.length}`;
    if (_dstCache.key === key && _dstCache.val)
        return _dstCache.val;
    const dayIds = [...new Set(hs.filter(h => h && h.dayId).map(h => h.dayId))];
    const val = {};
    // One pass per DAY, not per lift — days are few, history is not.
    dayIds.forEach(d => { val[d] = Object.fromEntries(exerciseTrends(hs, d).map(t => [t.id, t])); });
    _dstCache = { key, val };
    return val;
}

function plateauSplitByDay(history, id, judge, fallback) {
    const byDay = dayScopedTrends(history);
    const days = Object.keys(byDay).filter(d => byDay[d][id] && byDay[d][id].sessionsCount >= MIN_DAY_SESSIONS);
    // Under two judgeable days there is nothing to split — including a history with no dayId at all,
    // which is exactly the pre-existing behaviour and the best available for that data.
    if (days.length < 2)
        return fallback();
    const perDay = days.map(d => ({ dayId: d, pl: judge(byDay[d][id], d) }));
    if (perDay.some(x => !x.pl))
        return null; // progressing on some day → not stalled
    const soonest = perDay.reduce((m, x) => (x.pl.since < m.pl.since ? x : m), perDay[0]);
    return { ...soonest.pl, high: soonest.pl.since >= 3, perDay: Object.fromEntries(perDay.map(x => [x.dayId, x.pl])) };
}

function plateauOfLift(history, trend) {
    if (!trend)
        return null;
    return plateauSplitByDay(history, trend.id, (t) => plateauOf(t), () => plateauOf(trend));
}

function plateauOf(trend) {
    if (!trend || trend.sessionsCount < 3)
        return null;
    const pts = trend.pts;
    /* THE LAST SESSION THAT STRICTLY BEAT EVERYTHING BEFORE IT — not `lastIndexOf(Math.max(...pts))`.
     *
     * This is the same tie-breaking hole that `stallCountFor` was fixed for, still living in its
     * sibling. `pts` runs oldest→newest, so on a DEAD-FLAT run every value ties the max, lastIndexOf
     * returns the NEWEST index, `since` computes to 0, and the lift reads as "PR today, no stall".
     * Measured on [100,100,100,100,100,100]: since = 0 — byte-identical to a genuinely RISING run.
     * The detector could only see a plateau once you had got WEAKER; merely going nowhere, which is
     * what a plateau overwhelmingly looks like, was invisible.
     *
     * It mattered because two detectors were answering one question and disagreeing: on six flat
     * sessions `stallCountFor` correctly reported 5 and the session badge announced "e1RM
     * autoregulation · temporary — recalibrating", while the coach card, reading this function, had
     * nothing to say at all. The lifter is told their programming changed and given no reason.
     *
     * Walking forward, a session counts as the PR only if it strictly beats every session older than
     * it, so a flat run scores its full length and a rising one still scores 0. The 2% work band below
     * is unchanged and still does its own job — it is what stops double progression (which adds reps
     * and sets at a fixed load, invisible to an e1RM series capped at 12 reps) from reading as a
     * stall. */
    let prIdx = 0, best = -Infinity;
    for (let i = 0; i < pts.length; i++)
        if (pts[i] > best + 1e-9) {
            best = pts[i];
            prIdx = i;
        }
    const since = (pts.length - 1) - prIdx;
    if (since < 2)
        return null;
    /* Double progression deliberately HOLDS the load and adds reps, then sets, until every set sits at
       the top of the range — and the e1RM series cannot see any of it. e1rm() caps reps at 12, so on a
       12–20 rep accessory the estimate literally cannot move; and `best` reads only the single best set,
       so adding a third and fourth top-range set changes nothing. A lifter doing strictly more work each
       week was being told they had plateaued. Rising work is progress; only flag a stall when the work
       has stopped growing too. */
    const workAt = (i) => ((trend.sessions[i] && trend.sessions[i].sets) || [])
        .reduce((sum, x) => sum + (x.w > 0 && x.r > 0 ? x.w * x.r : 0), 0);
    const prWork = workAt(prIdx), lastWork = workAt(pts.length - 1);
    if (prWork > 0 && lastWork > prWork * 1.02)
        return null; // 2% band so logging noise isn't "progress"
    const high = since >= 3;
    return {
        since, high,
        advice: high
            ? `No estimated-1RM PR in ${since} sessions. Swap in a fresh variation or run a deload to shed fatigue, then rebuild.`
            : `No PR in ${since} sessions. Add a set or chase 1–2 more reps at this load before adding weight.`
    };
}

const STD_LEVELS = ["Untrained", "Beginner", "Novice", "Intermediate", "Advanced", "Elite"];

const SEX_FACTOR = { male: 1, female: 0.72 };

const STANDARDS = {
    "bb-bench": [0.5, 0.75, 1.0, 1.5, 2.0], "inc-bb-bench": [0.4, 0.6, 0.85, 1.25, 1.6], "low-inc-bb-bench": [0.4, 0.6, 0.85, 1.25, 1.6], "high-inc-bb-bench": [0.35, 0.56, 0.75, 1.1, 1.45], "smith-bench": [0.5, 0.75, 1.0, 1.45, 1.9],
    "back-squat": [0.75, 1.25, 1.5, 2.25, 2.75], "front-squat": [0.6, 1.0, 1.3, 1.85, 2.25],
    "deadlift": [1.0, 1.5, 2.0, 2.5, 3.0], "sumo-dl": [1.0, 1.5, 2.0, 2.5, 3.0],
    "ohp": [0.35, 0.56, 0.8, 1.1, 1.4], "bb-row": [0.5, 0.75, 1.0, 1.35, 1.7], "cgbp": [0.45, 0.7, 0.95, 1.4, 1.85],
    "bb-curl": [0.2, 0.35, 0.5, 0.65, 0.85], "ez-curl": [0.2, 0.35, 0.5, 0.65, 0.85], "hip-thrust": [1.0, 1.5, 2.0, 2.75, 3.5],
    "decline-bench": [0.5, 0.75, 1.0, 1.5, 2.0], "seated-ohp": [0.35, 0.56, 0.8, 1.1, 1.4], "pendlay-row": [0.5, 0.75, 1.0, 1.35, 1.7],
    "box-squat": [0.75, 1.25, 1.5, 2.25, 2.75], "stiff-deadlift": [0.9, 1.35, 1.8, 2.3, 2.8], "rack-pull": [1.1, 1.6, 2.1, 2.7, 3.2]
};

const todayISO = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; };

const BIRTH_RE = /^\d{4}-\d{2}-\d{2}$/;

function birthParts(b) {
    if (typeof b !== "string" || !BIRTH_RE.test(b))
        return null;
    const [y, m, d] = b.split("-").map(Number);
    if (!(y >= 1900) || !(m >= 1 && m <= 12) || !(d >= 1 && d <= 31))
        return null;
    const dt = new Date(y, m - 1, d);
    // rejects impossible calendar dates that the regex lets through (2023-02-30 rolls over to Mar 2)
    if (dt.getFullYear() !== y || dt.getMonth() !== m - 1 || dt.getDate() !== d)
        return null;
    return { y, m, d };
}

function ageFrom(birth, now = Date.now()) {
    const p = birthParts(birth);
    if (!p)
        return "";
    const t = new Date(now);
    let a = t.getFullYear() - p.y;
    const mo = t.getMonth() + 1, day = t.getDate();
    if (mo < p.m || (mo === p.m && day < p.d))
        a -= 1; // birthday hasn't come round yet this year
    return a >= 0 && a <= 120 ? a : ""; // future dates and implausible ones score as unknown
}

function ageFactor(age) {
    const a = Number(age);
    if (!(a > 0))
        return 1;
    if (a < 14)
        return 0.80;
    if (a < 17)
        return 0.90;
    if (a < 19)
        return 0.96;
    if (a <= 33)
        return 1.00;
    if (a <= 39)
        return 0.97;
    if (a <= 44)
        return 0.93;
    if (a <= 49)
        return 0.89;
    if (a <= 54)
        return 0.84;
    if (a <= 59)
        return 0.79;
    if (a <= 64)
        return 0.74;
    if (a <= 69)
        return 0.68;
    if (a <= 74)
        return 0.62;
    if (a <= 79)
        return 0.56;
    return 0.50;
}

function strengthLevel(exId, e1rmVal, e1rmUnit, bw, bwUnit, sex, age) {
    const base = STANDARDS[exId];
    if (!base || !(bw > 0) || !(e1rmVal > 0))
        return null;
    const bwL = toUnit(bw, bwUnit, e1rmUnit); // bodyweight in the lift's unit
    const af = ageFactor(age);
    const thresh = base.map(m => m * (SEX_FACTOR[sex] ?? 1) * af * bwL);
    let idx = 0;
    thresh.forEach((t, i) => { if (e1rmVal >= t)
        idx = i + 1; });
    const prevT = idx > 0 ? thresh[idx - 1] : 0;
    const nextT = idx < thresh.length ? thresh[idx] : thresh[thresh.length - 1];
    const within = idx >= thresh.length ? 1 : clamp((e1rmVal - prevT) / ((nextT - prevT) || 1), 0, 1);
    return { level: STD_LEVELS[idx], idx, ratio: e1rmVal / bwL, within, thresh, unit: e1rmUnit };
}

function strengthSnapshot(history, bw, bwUnit, sex, age) {
    if (!(bw > 0))
        return null;
    const trends = exerciseTrends(history);
    const lifts = trends.map(t => {
        const direct = STANDARDS[t.id] ? { id: t.id, val: t.prBest, est: false } : null;
        const use = direct || scoreEquivalent(t.id, t.prBest, t.unit, bw, bwUnit);
        if (!use)
            return null;
        const lvl = strengthLevel(use.id, use.val, t.unit, bw, bwUnit, sex, age);
        return lvl ? { id: t.id, name: t.name, prBest: t.prBest, unit: t.unit, lvl,
            est: !!use.est, via: use.id, pat: SCORE_PATTERN[use.id] || null } : null;
    }).filter(Boolean);
    if (!lifts.length)
        return null;
    const score = lifts.reduce((s, l) => s + l.lvl.idx + (l.lvl.idx < 5 ? l.lvl.within : 0), 0) / lifts.length;
    const overallIdx = Math.max(0, Math.min(5, Math.round(score)));
    lifts.sort((a, b) => (b.lvl.idx - a.lvl.idx) || (b.lvl.within - a.lvl.within));
    return { lifts, overallIdx, overallLabel: STD_LEVELS[overallIdx], score };
}

const SCORE_PATTERN = {
    "bb-bench": "push", "inc-bb-bench": "push", "low-inc-bb-bench": "push", "high-inc-bb-bench": "push", "smith-bench": "push", "decline-bench": "push",
    "ohp": "push", "seated-ohp": "push", "cgbp": "push",
    "bb-row": "pull", "pendlay-row": "pull", "bb-curl": "pull", "ez-curl": "pull",
    "back-squat": "squat", "front-squat": "squat", "box-squat": "squat",
    "deadlift": "hinge", "sumo-dl": "hinge", "stiff-deadlift": "hinge", "rack-pull": "hinge", "hip-thrust": "hinge"
};

const SCORE_PATTERN_LABEL = { push: "Push", pull: "Pull", squat: "Squat", hinge: "Hinge" };

const SCORE_EQUIV = {
    "db-bench": { to: "bb-bench", k: 2.3 }, "inc-db-press": { to: "inc-bb-bench", k: 2.3 },
    "decline-db-press": { to: "decline-bench", k: 2.3 },
    "db-shoulder": { to: "ohp", k: 2.2 }, "seated-db-press": { to: "seated-ohp", k: 2.2 },
    "arnold": { to: "ohp", k: 2.1 },
    "db-row": { to: "bb-row", k: 1.8 }, "inc-db-row": { to: "bb-row", k: 1.9 }, "kroc-row": { to: "bb-row", k: 1.6 },
    "db-rdl": { to: "stiff-deadlift", k: 2.0 },
    "pullup": { to: "bb-row", k: 1.0, bw: true }, "chinup": { to: "bb-row", k: 1.0, bw: true },
    "dips-chest": { to: "bb-bench", k: 0.95, bw: true }
};

function scoreTargetFor(id) { return STANDARDS[id] ? id : (SCORE_EQUIV[id] ? SCORE_EQUIV[id].to : null); }

function scorePatternFor(id) { const t = scoreTargetFor(id); return t ? SCORE_PATTERN[t] : null; }

const SCORE_BANDS = STD_LEVELS.length - 1;

const scoreContin = (lvl) => lvl.idx + (lvl.idx < SCORE_BANDS ? lvl.within : 0);

const scoreTo100 = (c) => Math.round(clamp(c / SCORE_BANDS, 0, 1) * 100);

const scoreMatur = (n) => clamp(((n || 0) - 1) / 4, 0, 1);

function scoreLevelIdx(overall) { return clamp(Math.floor((Number(overall) || 0) / (100 / SCORE_BANDS)), 0, SCORE_BANDS); }

function scoreLevelLabel(overall) { return STD_LEVELS[scoreLevelIdx(overall)]; }

function scoreCompose(entries, liftSessions, patSessions) {
    const byPat = {};
    entries.forEach(l => {
        const pat = l.pat;
        if (!pat)
            return;
        const c = scoreContin(l.lvl);
        /* A MEASURED lift always wins its pattern over an estimated one — not merely on a tie. The
           conversions carry real error, and a dumbbell press converting to a bigger number than the
           bench the lifter actually performed would let that error overwrite data the app is certain
           about. Estimates exist to cover a pattern with NO barbell lift in it, not to outbid one that
           has. */
        const cur = byPat[pat];
        if (!cur) {
            byPat[pat] = { c, lift: l };
            return;
        }
        if (cur.lift.est !== l.est) {
            if (cur.lift.est && !l.est)
                byPat[pat] = { c, lift: l };
            return;
        }
        if (c > cur.c)
            byPat[pat] = { c, lift: l };
    });
    const subs = Object.keys(SCORE_PATTERN_LABEL)
        .filter(p => byPat[p])
        .map(p => ({ key: p, label: SCORE_PATTERN_LABEL[p], score: scoreTo100(byPat[p].c), lift: byPat[p].lift, idx: byPat[p].lift.lvl.idx }));
    // The unweighted mean is the fallback for both means, used only when nothing has reached any
    // maturity at all (every movement on its first session).
    const flat = entries.length ? scoreTo100(entries.reduce((s, l) => s + scoreContin(l.lvl), 0) / entries.length) : 0;
    let aw = 0, awt = 0;
    entries.forEach(l => { const w = scoreMatur(liftSessions[l.id]); aw += scoreContin(l.lvl) * w; awt += w; });
    const allMean = awt > 0 ? scoreTo100(aw / awt) : flat;
    let pw = 0, pwt = 0;
    subs.forEach(x => { const w = scoreMatur(patSessions[x.key]); pw += x.score * w; pwt += w; });
    const patMean = pwt > 0 ? pw / pwt : (subs.length ? subs.reduce((s, x) => s + x.score, 0) / subs.length : allMean);
    return { overall: Math.round(patMean * 0.7 + allMean * 0.3), subs, byPat };
}

function scoreEquivalentInverse(id, val, unit, bw, bwUnit) {
    const eq = SCORE_EQUIV[id];
    if (!eq)
        return val; // a real standard lift: already its own unit
    return eq.bw ? (val / eq.k) - toUnit(bw, bwUnit, unit) : val / eq.k;
}

function scoreEquivalent(id, e1rmVal, unit, bw, bwUnit) {
    const eq = SCORE_EQUIV[id];
    if (!eq || !(e1rmVal > 0))
        return null;
    const load = eq.bw ? (toUnit(bw, bwUnit, unit) + e1rmVal) * eq.k : e1rmVal * eq.k;
    return load > 0 ? { id: eq.to, val: load, est: true } : null;
}

function strengthScore(history, bw, bwUnit, sex, age) {
    const snap = strengthSnapshot(history, bw, bwUnit, sex, age);
    if (!snap)
        return null;
    // Training maturity per lift / pattern: a movement you've only just started shouldn't yank the
    // overall down. Its weight ramps from a fraction up to full over the first ~4 sessions, so trying
    // a new lift eases into the score instead of tanking it; as you keep training it, it counts fully.
    const liftSessions = {}, patSessions = {};
    (history || []).forEach(h => {
        if (!h || typeof h !== "object" || !h.perf)
            return;
        const lifts = new Set(), pats = new Set();
        Object.keys(h.perf).forEach(id => { if (!scoreTargetFor(id))
            return; lifts.add(id); const pt = scorePatternFor(id); if (pt)
            pats.add(pt); });
        lifts.forEach(id => liftSessions[id] = (liftSessions[id] || 0) + 1);
        pats.forEach(p => patSessions[p] = (patSessions[p] || 0) + 1);
    });
    /* overall = pattern mean blended with the all-lift average, each weighted by training maturity.
       Composed by the ONE OWNER above, which the trend line also runs — see its header for why. */
    const entries = snap.lifts.map(l => ({ ...l, pat: l.pat || SCORE_PATTERN[l.id] }));
    const { overall, subs } = scoreCompose(entries, liftSessions, patSessions);
    const weakest = subs.length ? subs.reduce((a, b) => b.score < a.score ? b : a) : null;
    const strongest = subs.length ? subs.reduce((a, b) => b.score > a.score ? b : a) : null;
    /* A score tells you where you stand and nothing about what to do next. The thresholds that produced
       it already know: strengthLevel returns the full ladder for the lift, so the load that reaches the
       next band is read straight off it rather than re-derived — one table, so the milestone can never
       disagree with the level shown beside it. Taken from the WEAKEST pattern because that is where a
       given pound moves the overall number most, and returns null at Elite, where there is no next
       band and inventing one would be a lie. */
    let nextMilestone = null;
    if (weakest && weakest.lift && weakest.lift.lvl) {
        const l = weakest.lift, idx = l.lvl.idx;
        if (idx < l.lvl.thresh.length) {
            /* The ladder is in BARBELL-EQUIVALENT load, so for an estimated lift the threshold must be
               converted back into what the lifter actually loads before it is shown beside their own
               numbers. Without this the card read "Dumbbell RDL 270 lb · now 114" — a target in barbell
               terms sitting next to a per-hand best, which is not a harder goal but a different unit, and
               would send someone after more than double the weight they need. */
            const target = Math.ceil(scoreEquivalentInverse(l.id, l.lvl.thresh[idx], l.unit, bw, bwUnit));
            if (target > l.prBest)
                nextMilestone = { id: l.id, name: l.name, pattern: weakest.label,
                    current: Math.round(l.prBest), target, unit: l.unit, est: !!l.est,
                    level: STD_LEVELS[idx + 1] || STD_LEVELS[STD_LEVELS.length - 1] };
        }
    }
    /* `estimated` is true only when NOTHING in the score came off a barbell — the dumbbell-only case,
       where the whole number rests on conversions and the UI must say so. A lifter with one real
       standard lift plus some estimated ones is not shown a blanket caveat over a score that is
       partly measured; `subs[].lift.est` carries the per-pattern detail for that. */
    const estimated = snap.lifts.length > 0 && snap.lifts.every(l => l.est);
    /* ⚠ THE LEVEL IS THE LEVEL OF `overall`, NOT OF A SECOND QUANTITY. It used to be
       `snap.overallLabel` — round(mean of raw level indices), unweighted, on 0–5 — printed beside a
       maturity-weighted 0–100 number it had no relationship to. `snapLevelIdx` is kept so a caller
       that genuinely wants the per-lift-average standing can still ask for it by name. */
    return { overall, level: scoreLevelLabel(overall), levelIdx: scoreLevelIdx(overall),
        snapLevelIdx: snap.overallIdx, snapLevel: snap.overallLabel,
        subs, weakest, strongest, liftCount: snap.lifts.length, nextMilestone, estimated };
}

function scoreAttribution(history, bwLog, bwNow, bwUnit, sex, age, days = 90) {
    if (!(bwNow > 0) || !history || !history.length)
        return null;
    const cutoff = Date.now() - days * 86400000;
    const log = normalizeBwLog(bwLog, bwUnit);
    const older = log.filter(e => e.date <= cutoff);
    const bwThen = older.length ? older[older.length - 1].w : (log.length ? log[0].w : null);
    if (!(bwThen > 0))
        return null;
    const past = history.filter(h => h && h.date && h.date <= cutoff);
    if (!past.length)
        return null;
    const now = strengthScore(history, bwNow, bwUnit, sex, age);
    const then = strengthScore(past, bwThen, bwUnit, sex, age);
    if (!now || !then)
        return null;
    // same lifts as `then`, weighed today: isolates the scale from the training
    const counter = strengthScore(past, bwNow, bwUnit, sex, age);
    if (!counter)
        return null;
    const fromBw = counter.overall - then.overall;
    const fromLifts = now.overall - counter.overall;
    return { total: now.overall - then.overall, fromLifts, fromBw,
        bwThen: Math.round(bwThen * 10) / 10, bwNow: Math.round(bwNow * 10) / 10, unit: bwUnit, days,
        scoreThen: then.overall, scoreNow: now.overall };
}

function strengthScoreHistory(history, bw, bwUnit, sex, age) {
    if (!(bw > 0) || !history || !history.length)
        return null;
    // chronological sessions, each with the lifts performed and their best e1RM that day
    const sessions = [...(history || [])].filter(h => h && h.date && h.perf).sort((a, b) => a.date - b.date);
    if (!sessions.length)
        return null;
    const bestById = {}; // running max e1RM per lift id (in that lift's unit)
    const overall = [], byPat = { push: [], pull: [], squat: [], hinge: [] };
    let changed = false;
    /* THE WEIGHTS ARE FIXED FOR THE WHOLE SERIES. THIS IS THE POINT OF THE FUNCTION.
     *
     * strengthScore() weights each lift and pattern by how many sessions you have ON IT SO FAR, so a
     * movement you just started eases into today's number instead of tanking it. Correct for a single
     * snapshot. Applied to a TIME SERIES it is a trap: the weights move between points, so the line
     * moves even when nothing about your strength does.
     *
     * Patterns are not trained at equal frequency. Train push every session and squat every fourth,
     * and push reaches full maturity while squat is still near zero — the early points are effectively
     * a push-only score, and squat and hinge drag the line down as they mature. Measured on a fixture
     * of exactly that shape: the curve fell 78 -> 56 while every individual lift ROSE, and the card
     * announced a 22-point loss. Nothing had got weaker; the weighting had rebalanced.
     *
     * So the counts are taken over the WHOLE history once, up front, and every point is scored with
     * those same weights. The only thing left that can move the line is strength, which is what a
     * trend is for. DO NOT switch these back to running counts. */
    const liftSessions = {}, patSessions = {};
    sessions.forEach(h => {
        const lifts = new Set(), pats = new Set();
        Object.entries(h.perf || {}).forEach(([id, pf]) => {
            /* ⚠ COUNT THE SAME LIFTS THE HEADLINE COUNTS. This filtered on `STANDARDS[id]` — barbell
               lifts only — while strengthScore counts through `scoreTargetFor`, which also admits the
               movements that convert to a standard. So a dumbbell-press lifter's maturity weights, and
               therefore the whole line, were computed over a different set of lifts than the number
               printed above it. */
            if (!scoreTargetFor(id) || !EX_BY_ID[id] || !(pf && pf.weight > 0))
                return;
            lifts.add(id);
            const pt = scorePatternFor(id);
            if (pt)
                pats.add(pt);
        });
        lifts.forEach(id => liftSessions[id] = (liftSessions[id] || 0) + 1);
        pats.forEach(pt => patSessions[pt] = (patSessions[pt] || 0) + 1);
    });
    /* A point is only comparable once every pattern that CARRIES WEIGHT has data behind it; before
     * that the mean is over a smaller set of patterns and is a different quantity. */
    const weighted = Object.keys(patSessions).filter(pt => scoreMatur(patSessions[pt]) > 0);
    const seenPats = new Set();
    sessions.forEach(h => {
        const unit = bwUnit || "kg";
        let touched = false;
        const liftsThis = new Set(), patsThis = new Set();
        Object.entries(h.perf).forEach(([id, p]) => {
            if (!scoreTargetFor(id) || !EX_BY_ID[id] || !(p && p.weight > 0))
                return;
            liftsThis.add(id);
            const pt = scorePatternFor(id);
            if (pt)
                patsThis.add(pt);
            const raw = completedHistorySets(p).map(s => ({ ...s, w: convertHistoryLoad(s.w, h.unit, unit) }));
            const e = Math.max(...raw.filter(s => s.w > 0).map(s => s.r != null ? e1rm(s.w, s.r) : s.w));
            if (!(e > 0))
                return;
            const cur = bestById[id];
            /* Keyed by the lift AS LOGGED, never by what it converts to: maturity is counted per movement
               you actually perform, and two different dumbbell presses both converting to `bb-bench` are
               two movements, not one. The conversion happens at scoring time, below. */
            if (!cur || e > cur.e || unit !== cur.unit) {
                bestById[id] = { e, unit };
                touched = true;
            }
        });
        patsThis.forEach(pt => seenPats.add(pt));
        if (weighted.some(pt => !seenPats.has(pt)))
            return; // not yet comparable — see the note above
        if (!touched && changed)
            return; // no new PR on a scorable lift → score unchanged, skip a point
        changed = true;
        /* ⚠ THE LINE AND THE NUMBER ABOVE IT ARE THE SAME FUNCTION. This block used to re-implement the
           blend — and it re-implemented a DIFFERENT one: barbell lifts only, no estimated conversions,
           so a lifter whose score included converted work saw a final point that disagreed with the
           headline on the same card ("56/100" above "· 47/100 now"). `scoreCompose` is now the only
           place that blend exists, so the last point IS the headline by construction. */
        const entries = [];
        Object.entries(bestById).forEach(([id, { e, unit }]) => {
            const use = STANDARDS[id] ? { id, val: e, est: false } : scoreEquivalent(id, e, unit, bw, bwUnit);
            if (!use)
                return;
            const lvl = strengthLevel(use.id, use.val, unit, bw, bwUnit, sex, age);
            if (!lvl)
                return;
            entries.push({ id, pat: SCORE_PATTERN[use.id] || null, est: !!use.est, lvl });
        });
        if (!entries.length)
            return;
        const composed = scoreCompose(entries, liftSessions, patSessions);
        overall.push({ date: h.date, v: composed.overall });
        Object.keys(byPat).forEach(p => { if (composed.byPat[p])
            byPat[p].push({ date: h.date, v: scoreTo100(composed.byPat[p].c) }); });
    });
    if (overall.length < 2)
        return null; // need at least two points to draw a trend
    /* NO BASELINE OFFSET IS NEEDED ANY MORE, and startIdx stays for the caller's sake.
     *
     * There used to be a "coverage-fair baseline" here that hunted for the first point at which every
     * pattern was present, because early points were computed over fewer patterns than later ones and
     * comparing across them read as a loss. That was a correction applied downstream of the real
     * problem. The weights are now fixed across the series and no point is emitted until every pattern
     * that carries weight has data, so every point in `overall` is already measured the same way and
     * index 0 is a fair comparison. */
    const startIdx = 0;
    return { overall, byPat, startIdx };
}

function underRecoveredWeekly(history, part) {
    const sess = [];
    for (const h of (history || []).filter(h => h && h.date).sort((a, b) => a.date - b.date)) {
        let n = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (ex && ex.part === part && p.sets)
                n += p.sets.filter(s => isWorkSet(s)).length;
        }
        if (n > 0)
            sess.push({ date: h.date, sets: n });
    }
    if (sess.length < 3)
        return false;
    const recent = sess.slice(-5);
    const avgSets = recent.reduce((a, s) => a + s.sets, 0) / recent.length;
    const clock = 30 + clamp(avgSets, 0, 28) * 2.3;
    const rec = personalRecoveryHours(history, part, clock) ?? clock;
    const gaps = [];
    for (let i = 1; i < recent.length; i++) {
        const g = (recent[i].date - recent[i - 1].date) / 3600000;
        if (g > 0 && g <= 240)
            gaps.push(g);
    }
    if (!gaps.length)
        return false;
    return gaps.filter(g => g < rec * 0.85).length >= Math.ceil(gaps.length / 2);
}

function volumeAudit(program) {
    if (program?.engineSource === "pursuit-next")
        return auditShellVolume(program, EXERCISES);
    if (!program || !program.days?.length)
        return { issues: [], volBias: {} };
    const peak = weeksOf(program); // peak training week (before any deload) carries the most volume
    const vol = weeklyVolume(program, peak);
    const perDayBudget = (SESSIONS.find(s => s.id === program.config?.session)?.count) || 5;
    // whether any day has room to add a movement
    const dayRoom = program.days.some(d => d.exercises.length < perDayBudget);
    const SET_CEIL_PER_EX = 5; // a single exercise much past this is junk volume; prefer a new movement
    const issues = [];
    const volBias = {};
    PART_ORDER.forEach(part => {
        const v = vol[part] || 0;
        const mev = landmarkFor(part).mev;
        const mrv = compositeMrv(part);
        // how many direct slots already train this muscle, and their current set load
        const slots = [];
        program.days.forEach(d => d.exercises.forEach((id, si) => {
            if (EX_BY_ID[id]?.part === part) {
                const sets = Number(computeCell(program, d, id, si, peak).sets) || 0;
                slots.push({ sets });
            }
        }));
        /* ⚠ v634 — ADVISE ONLY ON WHAT THE PROGRAM COMMITS TO. `COVERED_MUSCLES` is the set every
           coverage pass holds at MEV; the six landmarked parts outside it (traps, lower_back, adductors,
           abductors, forearms, neck) are accessory work a lifter opts into, and the generator has never
           targeted them. Measured across 234 generated programs, 92 of 409 issues (22%) were for those
           parts — "add 3 sets to your traps" against a plan whose design never included direct trap work,
           which the one-click fix then thickens some other slot to satisfy. Same class as the v586 forearm
           finding: advice the app cannot honestly act on is worse than silence. Over-MRV is UNCHANGED and
           still reported for every part — too much work is always the generator's doing, whatever the
           muscle. A lifter who adds their own trap work still sees it in the volume chart. */
        /* ⚠ THE OPTIONAL-MUSCLE RULE IS STATED HERE, NOT LEFT TO A COINCIDENCE. Two rules meet in this
           branch and until engine 22 both were enforced by accident:
             v586 — a REQUIRED REGION the program never trains is a missing MOVEMENT, and the audit must
                    say so ("your only forearm work is grip — add a wrist curl"), never "add a set".
             v634 — a SET SHORTFALL on an optional part (traps, forearms, neck, ...) is advice the app
                    cannot honestly act on, because the plan never included that work; never offer it.
           The v634 guard only tested `v > 0`, which held because the optional parts sat at exactly zero.
           Engine 22 put a hammer curl in the week, its 0.4-set forearm credit lifted forearms off zero,
           and 126 programs the lifter never asked forearm work of started advising it. The first repair
           scoped the WHOLE branch to COVERED_MUSCLES — which deleted the v586 advice with the padding
           (gates/regionfix 0 and 3 caught it). So the rule, by membership and by opt-in:
             • a covered part      → gap advice, then set-shortfall advice, as before.
             • an optional part    → gap advice ONLY when the program holds a DIRECT slot for it (the
                                     lifter or a focus opted in and the coverage is incomplete); never
                                     set-shortfall advice; incidental credit alone never produces a
                                     finding. Over-MRV stays unscoped, as before.
           `slots` is the list of direct slots for `part`, built above from EX_BY_ID[id].part. */
        if (v > 0 && v < mev - 0.5) {
            const need = Math.ceil(mev - v); // sets to reach MEV
            /* A DIRECTION THE PROGRAM NEVER TRAINS CANNOT BE FIXED BY ADDING SETS TO ONE IT DOES. See
               regionGapsFor: measured 21 of 21 forearm findings offered "add a set" against a program whose
               only forearm work was grip. The set went onto a carry — volume delivered, coverage unchanged,
               and the button claimed a fix it had not made. When a required region is missing, the honest
               answer is an EXERCISE, and volBias stays unset so the one-click fix does not fire at all
               (distributeVolBias only ever thickens existing slots; it cannot add a movement). */
            const gaps = regionGapsFor(program, part);
            const optedIn = COVERED_MUSCLES.has(part) || slots.length > 0;
            if (gaps.length && optedIn) {
                issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "under", need,
                    fix: regionGapFix(part, gaps), fits: false, regionGaps: gaps });
                return;
            }
            /* The gap check runs for every opted-in part ABOVE this line; only the set-shortfall advice
               below is scoped to the parts the generator commits to. gates/coveragemodel check 3 pins the
               order from source; gates/regionfix pins the behaviour. */
            if (!COVERED_MUSCLES.has(part))
                return;
            // headroom = how many sets we can still add across existing slots before any one exercise hits
            // the junk-volume ceiling. We add as many as fit (a PARTIAL fix is still worth doing), and only
            // tell the user to add an exercise when there's no set-level headroom left at all.
            const headroom = slots.reduce((s, sl) => s + Math.max(0, SET_CEIL_PER_EX - sl.sets), 0);
            const addable = Math.min(need, headroom);
            const fix = addable > 0
                ? (addable >= need
                    ? `Add ${need} set${need === 1 ? "" : "s"} to your ${PART_LABEL[part].toLowerCase()} work`
                    : `Add ${addable} set${addable === 1 ? "" : "s"} to existing ${PART_LABEL[part].toLowerCase()} work (then add an exercise for the rest)`)
                : dayRoom
                    ? `Add a ${PART_LABEL[part].toLowerCase()} exercise (a day has room within your time budget)`
                    : slots.length === 0
                        ? `No direct ${PART_LABEL[part].toLowerCase()} work — add an exercise (sessions are at your time cap, consider a longer session length)`
                        : `${PART_LABEL[part]} exercises are at their set ceiling — add an exercise or raise session length`;
            if (addable > 0)
                volBias[part] = addable;
            issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "under", need, fix, fits: addable >= need });
        }
        else if (v > mrv + 0.5) {
            const cut = Math.ceil(v - mrv);
            // How much of this muscle's volume is DIRECT (removable) vs secondary credit from compounds?
            // A muscle slightly over MRV is often there from spillover — e.g. triceps from all your
            // pressing — which can't be honestly fixed by cutting its isolation work (and you shouldn't
            // cut pressing to "fix" triceps). Only offer an auto-cut when there's removable direct volume
            // above a single working set; otherwise explain it's compound spillover.
            const directSets = slots.reduce((s, sl) => s + sl.sets, 0);
            const removable = slots.reduce((s, sl) => s + Math.max(0, sl.sets - 1), 0); // can't drop below 1/exercise
            const secondaryShare = v > 0 ? 1 - directSets / v : 0;
            // The spillover floor: volume this muscle gets from compounds even if we cut ALL removable
            // direct sets. If that floor alone is already over MRV, cutting isolation can't fix it — the
            // excess is structural pressing/pulling spillover. Offering a cut here creates the loop the
            // user hit: drop a triceps set, still over MRV next audit, button never goes away.
            const flooredVol = v - removable; // volume remaining if every removable direct set is cut
            const cutCanClear = flooredVol <= mrv + 0.5;
            if (removable >= cut && secondaryShare < 0.6 && cutCanClear) {
                volBias[part] = -cut;
                issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "over", need: cut, fix: `Drop ${cut} set${cut === 1 ? "" : "s"} from ${PART_LABEL[part].toLowerCase()} accessory work to stay recoverable`, fits: true });
            }
            else if (secondaryShare >= 0.6 || !cutCanClear) {
                // mostly spillover, or cutting all direct work still wouldn't clear MRV — no auto-fix; the
                // excess comes from compound lifts feeding this muscle, not its isolation work.
                issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "over", need: cut, fix: `Mostly spillover from your compound lifts — only ${fmtSets(directSets)} direct set${directSets === 1 ? "" : "s"}. A little over MRV here is usually fine; if it's a recovery problem, trim pressing/pulling frequency rather than ${PART_LABEL[part].toLowerCase()} isolation.`, fits: false });
            }
            else {
                // some removable, partial cut helps but won't fully clear — offer what it can
                if (removable > 0)
                    volBias[part] = -removable;
                issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "over", need: cut, fix: removable > 0 ? `Drop ${removable} set${removable === 1 ? "" : "s"} from ${PART_LABEL[part].toLowerCase()} isolation (the rest is compound spillover — trim pressing/pulling if recovery suffers)` : `Driven by compound spillover — trim pressing/pulling frequency if recovery suffers`, fits: false });
            }
        }
    });
    // under-volume first (more actionable), then over
    issues.sort((a, b) => (a.status === b.status ? 0 : a.status === "under" ? -1 : 1));
    return { issues, volBias };
}

function prescribedRIRofAny(s) {
    if (!s)
        return null;
    if (s.target)
        return prescribedRIRof(s);
    return Number.isFinite(s.tr) ? s.tr : null;
}

function rirTrend(history) {
    history = canonicalHistoryNewest(history);
    const MIN_SETS = 4; // a week needs this many RIR-logged sets to be a point at all
    const BASELINE_WEEKS = 4; // "prior" compares against recent weeks, not a lifetime mean
    const weekKey = weekKeyOf;
    const byWeek = {}, byWeekDev = {}, byWeekE = {};
    (history || []).forEach(h => Object.values(h.perf || {}).forEach(p => (p.sets || []).forEach(s => {
        const observedRir = observedHistoryRIR(s);
        if (observedRir == null || s.warm || s.sub || s.done === false || !(s.r > 0))
            return;
        const k = weekKey(h.date);
        (byWeek[k] = byWeek[k] || []).push(observedRir);
        const rx = prescribedRIRofAny(s);
        if (rx != null)
            (byWeekDev[k] = byWeekDev[k] || []).push(observedRir - rx);
        if (s.w > 0) {
            const e = e1rmRIR(convertHistoryLoad(s.w, h.unit, "kg"), s.r, 0);
            byWeekE[k] = Math.max(byWeekE[k] || 0, e);
        }
    })));
    const avg = a => a.reduce((x, y) => x + y, 0) / a.length;
    const weeks = Object.keys(byWeek).map(Number).sort((a, b) => a - b)
        .filter(k => byWeek[k].length >= MIN_SETS);
    const rawPts = weeks.map(k => avg(byWeek[k]));
    // Deviation is only usable where the plan is actually known for most of the week's sets. Sessions
    // logged before `tr` was persisted have no prescription on record; those weeks fall back to raw.
    const devCover = weeks.map(k => (byWeekDev[k] ? byWeekDev[k].length : 0) / byWeek[k].length);
    const haveDev = weeks.length > 0 && devCover.every(c => c >= 0.5);
    const devPts = haveDev ? weeks.map(k => avg(byWeekDev[k])) : [];
    const ePts = weeks.map(k => byWeekE[k] || 0);
    const n = weeks.reduce((a, k) => a + byWeek[k].length, 0);
    const mode = haveDev ? "vs-plan" : "raw";
    // The series the verdict reasons over: deviation when the plan is known, raw otherwise.
    const sig = haveDev ? devPts : rawPts;
    let declining2 = false, onPlanTaper = false;
    if (sig.length >= 3 && n >= 9) {
        const a = sig[sig.length - 3], b = sig[sig.length - 2], c = sig[sig.length - 1];
        const dropping = (b < a - 0.2 && c < b - 0.2) || (c <= a - 1.0);
        // Reserve falling while top-end loads climb is planned intensification, not fatigue outrunning
        // recovery — the same guard as before, and still worth keeping once deviation removes the
        // programming component.
        const eA = ePts[sig.length - 3], eC = ePts[sig.length - 1];
        const strengthStalled = !(eA > 0 && eC > 0) || eC < eA * 1.005;
        if (haveDev) {
            // With the plan removed, fatigue means genuinely grinding BELOW what was asked — a deviation
            // that is both falling and now meaningfully negative. Following a taper exactly sits at ~0 and
            // can never trip this, which is the whole point.
            declining2 = dropping && strengthStalled && c <= -0.5;
            // Raw reserve falling while deviation holds steady: the block asked for it. Worth saying out
            // loud, because the old card called this fatigue.
            const rawDrop = rawPts.length >= 3 && rawPts[rawPts.length - 1] <= rawPts[rawPts.length - 3] - 0.5;
            onPlanTaper = rawDrop && !declining2;
        }
        else {
            declining2 = dropping && strengthStalled;
        }
    }
    const last = arr => (arr.length ? arr[arr.length - 1] : null);
    const baseline = arr => {
        if (arr.length < 2)
            return null;
        const prev = arr.slice(Math.max(0, arr.length - 1 - BASELINE_WEEKS), arr.length - 1);
        return prev.length ? avg(prev) : null;
    };
    return {
        pts: rawPts, n, declining2, onPlanTaper, mode,
        latest: last(rawPts), prior: baseline(rawPts),
        devPts, devLatest: last(devPts), devPrior: baseline(devPts)
    };
}

function constantLoadDecay(history, exId) {
    // Collect (date, weight, reps, rir) for the TOP working set of each session of this lift.
    const hs = canonicalHistoryNewest(history);
    const sess = [];
    for (let i = hs.length - 1; i >= 0; i--) { // oldest → newest
        const p = hs[i]?.perf?.[exId];
        if (!p)
            continue;
        const sets = (p.sets && p.sets.length) ? p.sets : [{ w: p.weight, r: p.reps, rir: null }];
        let best = null;
        for (const s of sets) {
            const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
            if (!(w > 0) || !(r > 0))
                continue;
            if (!best || w > best.w || (w === best.w && r > best.r))
                best = { w, r, rir: s.rir != null ? s.rir : null };
        }
        if (best)
            sess.push({ ...best, date: hs[i].date });
    }
    if (sess.length < 3)
        return null;
    // Compare only sessions at the SAME load — that's what makes this identifiable. A rep drop at a
    // heavier weight tells you nothing; a rep drop at the same weight tells you everything.
    const recent = sess.slice(-6);
    const byWeight = new Map();
    recent.forEach(s => { const k = String(s.w); if (!byWeight.has(k))
        byWeight.set(k, []); byWeight.get(k).push(s); });
    // the load with the most repeat exposures is the one we can actually say something about
    let bestGroup = null;
    for (const g of byWeight.values())
        if (g.length >= 2 && (!bestGroup || g.length > bestGroup.length))
            bestGroup = g;
    if (!bestGroup)
        return null;
    const first = bestGroup[0], last = bestGroup[bestGroup.length - 1];
    const repDelta = last.r - first.r; // negative = decaying
    const rirDelta = (last.rir != null && first.rir != null) ? last.rir - first.rir : null; // negative = grinding harder
    return {
        weight: last.w, exposures: bestGroup.length, repDelta, rirDelta,
        // Overreaching on this lift = doing LESS work for the SAME or MORE effort at an unchanged load.
        decaying: repDelta <= -1 && (rirDelta == null || rirDelta <= 0)
    };
}

function overreachSignal(history, _program) {
    const hs = canonicalHistoryNewest(history);
    if (hs.length < 4)
        return { level: "none", lifts: [], why: "" };
    // only judge lifts trained often enough to compare
    const ids = new Set();
    hs.slice(0, 12).forEach(h => Object.keys(h.perf || {}).forEach(id => ids.add(id)));
    const decaying = [];
    let checked = 0;
    for (const id of ids) {
        const d = constantLoadDecay(hs, id);
        if (!d)
            continue;
        checked++;
        if (d.decaying)
            decaying.push({ id, name: EX_BY_ID[id]?.name || id, ...d });
    }
    if (checked < 2)
        return { level: "none", lifts: [], why: "" }; // not enough comparable lifts yet
    const frac = decaying.length / checked;
    // Two or more lifts going backwards at unchanged loads is the signal. One is noise — a bad night's
    // sleep, a missed meal, a heavy work week — and intervening on one lift's bad day is how you talk
    // someone out of training that was working.
    const deep = decaying.filter(d => d.repDelta <= -2).length;
    const level = (decaying.length >= 2 && frac >= 0.4) ? (deep >= 2 ? "high" : "moderate") : "none";
    const names = decaying.slice(0, 3).map(d => d.name).join(", ");
    return {
        level, lifts: decaying, checked,
        why: level === "none" ? ""
            : `${decaying.length} of ${checked} tracked lifts are losing reps at the same load (${names}${decaying.length > 3 ? "…" : ""}). Losing reps at an unchanged weight is fatigue, not a strength ceiling — the load hasn't got heavier, you've got more tired.`
    };
}

function deloadAdvice(history, program = null) {
    const hs = canonicalHistoryNewest(history);
    if (hs.length < 6)
        return null;
    // PHASE 4′: direct, identifiable overreach evidence — lifts losing reps at an UNCHANGED load.
    // This is the strongest signal available and it outranks the proxies below (PR droughts, RIR
    // drift), which can both fire for reasons that have nothing to do with fatigue.
    const over = overreachSignal(hs, program);
    const trends = exerciseTrends(hs);
    // A "stall" worth weighing toward a deload is a GENUINE multi-session plateau — not a lift that
    // merely failed to set an estimated-1RM PR in the last session or two (you don't PR every
    // session, so at any moment some lift is "2 sessions since a PR"). Require 4+ sessions without a
    // PR to count, 6+ to count as a hard stall.
    let stalled = 0, hardStall = 0;
    trends.forEach(t => { const pl = plateauOfLift(hs, t); if (pl && pl.since >= 4) {
        stalled++;
        if (pl.since >= 6)
            hardStall++;
    } });
    const rt = rirTrend(hs);
    let smashed = 0;
    hs.slice(0, 8).forEach(h => { if (h.feedback)
        smashed += Object.values(h.feedback).filter(v => v < 0).length; });
    // Deloads are earned by PERFORMANCE evidence (real plateaus) and sustained TRENDS (reps-in-reserve
    // falling over weeks, repeated "smashed" self-reports) — NEVER by instantaneous time-based
    // readiness, which is naturally low right after training and sits ~50-60% for anyone training
    // frequently. A momentary low recovery reading can no longer trigger a deload, nor justify one on
    // its own; the trend/self-report signals only escalate an already-evident stall.
    // PHASE 4′: constant-load rep decay is DIRECT evidence of accumulated fatigue, so it both
    // (a) triggers on its own when severe, and (b) lowers the bar for the proxy signals — a PR
    // drought means something quite different when your reps are also falling at the same weight
    // than it does when they aren't. The proxies alone keep their original, stricter thresholds:
    // a PR drought by itself is normal (you don't PR every session) and must not order a deload.
    const advised = over.level === "high"
        || (over.level === "moderate" && stalled >= 1)
        || hardStall >= 2
        || stalled >= 3
        || (stalled >= 2 && rt.declining2)
        || (stalled >= 2 && smashed >= 6);
    if (!advised)
        return null;
    const rec = muscleRecovery(hs);
    const major = ["chest", "lats", "upper_back", "shoulders", "quads", "hamstrings", "glutes", "biceps", "triceps"];
    const mt = rec.filter(r => major.includes(r.part) && r.daysSince != null);
    const avgReady = mt.length ? mt.reduce((s, r) => s + r.readiness, 0) / mt.length : 100;
    const reasons = [];
    // Lead with the direct observation. It's the only reason here the lifter can check against their
    // own logbook — "you lost reps at the same weight" is falsifiable; "fatigue is outrunning
    // recovery" is a story we tell about it.
    if (over.why)
        reasons.push(over.why);
    /* Wording tracks what actually trips this now: reserve BELOW the prescription, not merely falling.
       A block that tapers reserve on purpose no longer lands here at all. */
    if (rt.declining2)
        reasons.push(rt.mode === "vs-plan" && rt.devLatest != null
            ? `you've been finishing sets about ${Math.abs(rt.devLatest).toFixed(1)} reps closer to failure than prescribed for 2+ weeks — a sign fatigue is outrunning recovery`
            : `your reps-in-reserve has been falling for 2+ weeks${rt.latest != null && rt.prior != null ? ` (now ~${rt.latest.toFixed(1)} vs ${rt.prior.toFixed(1)} earlier)` : ""} — a sign fatigue is outrunning recovery`);
    if (stalled >= 1)
        reasons.push(`${stalled} ${stalled === 1 ? "lift hasn't" : "lifts haven't"} set a PR in 4+ sessions`);
    if (smashed >= 4)
        reasons.push(`you've flagged muscles as "smashed" repeatedly`);
    return { reasons, stalled, avgReady: Math.round(avgReady), fatigue: rt.declining2, overreach: over.level, overreachLifts: over.lifts };
}

const STRENGTH_CLUB_LIFTS = [
    { id: "back-squat", label: "Squat" },
    { id: "bb-bench", label: "Bench" },
    { id: "deadlift", label: "Deadlift" },
];

const STRENGTH_CLUB_TIERS_LB = [
    { lb: 600, name: "600 lb Club" },
    { lb: 800, name: "800 lb Club" },
    { lb: 1000, name: "1,000 lb Club" },
    { lb: 1200, name: "1,200 lb Club" },
    { lb: 1500, name: "1,500 lb Club" },
];

function strengthClubSnapshot(trends, unit, mode = "lifted") {
    const rows = STRENGTH_CLUB_LIFTS.map(l => {
        const t = (trends || []).find(x => x.id === l.id);
        const actual = !!(t && t.prTopW > 0);
        const raw = !t ? 0 : (mode === "lifted" ? (actual ? t.prTopW : t.prBest) : t.prBest);
        const val = t ? toUnit(raw, t.unit, unit) : 0;
        return { ...l, val, has: !!t, actual, sourceUnit: t?.unit || unit };
    });
    const hasAny = rows.some(r => r.has);
    const allLogged = rows.every(r => r.has && r.val > 0);
    const allActual = rows.every(r => r.actual && r.val > 0);
    const complete = allLogged && (mode !== "lifted" || allActual);
    const total = rows.reduce((sum, r) => sum + (r.val || 0), 0);
    const totalLb = toUnit(total, unit, "lb");
    const achieved = complete ? STRENGTH_CLUB_TIERS_LB.filter(t => totalLb >= t.lb) : [];
    const current = achieved.length ? achieved[achieved.length - 1] : null;
    const next = complete
        ? (STRENGTH_CLUB_TIERS_LB.find(t => totalLb < t.lb) || STRENGTH_CLUB_TIERS_LB[STRENGTH_CLUB_TIERS_LB.length - 1])
        : STRENGTH_CLUB_TIERS_LB.find(t => t.lb === 1000);
    const maxedOut = complete && achieved.length === STRENGTH_CLUB_TIERS_LB.length;
    const prevLb = current ? current.lb : 0;
    const span = next.lb - prevLb;
    const pct = maxedOut || span <= 0 ? 100 : Math.max(0, Math.min(100, ((totalLb - prevLb) / span) * 100));
    const nextInUnit = toUnit(next.lb, "lb", unit);
    const remaining = maxedOut ? 0 : Math.max(0, nextInUnit - total);
    const thousandTarget = toUnit(1000, "lb", unit);
    const thousandRemaining = Math.max(0, thousandTarget - total);
    const thousandPct = Math.max(0, Math.min(100, thousandTarget > 0 ? (total / thousandTarget) * 100 : 0));
    const inThousand = complete && totalLb >= 1000;
    return { mode, unit, rows, hasAny, allLogged, allActual, complete, total, totalLb, achieved, current, next, maxedOut, pct, remaining, thousandTarget, thousandRemaining, thousandPct, inThousand };
}

const BIG_THREE = { bench: "bb-bench", squat: "back-squat", deadlift: "deadlift" };

function computeMilestones(history, opts = {}) {
    const canonicalHistory = canonicalHistoryNewest(history);
    const unit = opts.unit || "kg";
    const bw = parseFloat(opts.bodyweight) > 0 ? parseFloat(opts.bodyweight) : 0; // already in `unit`
    const n = canonicalHistory.length;
    // Sessions are stored in the unit they were logged in. Summing them raw double-counts anyone who
    // has ever switched kg <-> lb, so normalise into the unit the lifter is reading right now.
    const totalVol = canonicalHistory.reduce((a, h) => a + toUnit(h.volume || 0, h.unit || unit, unit), 0);
    const weekKey = weekKeyOf;
    const byWeek = {}, partsByWeek = {}, idsByWeek = {};
    canonicalHistory.forEach(h => {
        const k = weekKey(h.date);
        byWeek[k] = (byWeek[k] || 0) + 1;
        // Distinct muscle groups touched in the week, for the coverage badge. Unknown ids (a custom
        // exercise, a lift retired from the library) simply don't contribute rather than counting as
        // a mystery group.
        const set = partsByWeek[k] || (partsByWeek[k] = new Set());
        const ids = idsByWeek[k] || (idsByWeek[k] = new Set());
        Object.keys(h.perf || {}).forEach(id => { ids.add(id); const ex = EX_BY_ID[id]; if (ex && ex.part)
            set.add(ex.part); });
    });
    // Squat, bench and deadlift all inside one training week — the frequency the big lifts actually
    // want. A week, not a session: pressing and pulling heavy on the same day is a different thing.
    const bigThreeWeek = Object.values(idsByWeek).some(x => x.has(BIG_THREE.bench) && x.has(BIG_THREE.squat) && x.has(BIG_THREE.deadlift)) ? 1 : 0;
    const weeks = Object.keys(byWeek).map(Number).sort((a, b) => a - b);
    const maxInWeek = weeks.length ? Math.max(...Object.values(byWeek)) : 0;
    const bestPartsWeek = weeks.length ? Math.max(...Object.values(partsByWeek).map(x => x.size)) : 0;
    // Longest run of CONSECUTIVE weeks that each held 3+ sessions. Stricter than the plain week streak
    // above, which a single session a week satisfies.
    let fullRun = 0, bestFullRun = 0;
    for (let i = 0; i < weeks.length; i++) {
        const consec = i > 0 && Math.round((weeks[i] - weeks[i - 1]) / 86400000) === 7;
        fullRun = byWeek[weeks[i]] >= 3 ? (consec ? fullRun + 1 : 1) : 0;
        bestFullRun = Math.max(bestFullRun, fullRun);
    }
    let streak = 0, best = 0;
    // Rounded week-count, not exact millisecond equality — the same DST-transition fragility as the
    // daily streak above (a week spanning a DST change is ±1 hour off 7*86400000ms exactly).
    for (let i = 0; i < weeks.length; i++) {
        streak = (i > 0 && Math.round((weeks[i] - weeks[i - 1]) / 86400000) === 7) ? streak + 1 : 1;
        best = Math.max(best, streak);
    }
    // Walk the log oldest-first: personal records, exercise breadth, training hours, and the best e1RM
    // reached on each of the big three.
    const chrono = canonicalHistory.slice().sort((a, b) => a.date - b.date);
    const bestE1 = {}; // exercise -> best e1RM so far, in `unit`
    let prs = 0, bestPrDay = 0, earlyBird = 0, nightOwl = 0, comeback = 0, prevDate = null;
    // Hours under the bar, weekend sessions, and the single biggest session — the comment above has
    // always claimed this walk collects training hours; now it actually does.
    let totalMin = 0, weekendCount = 0, maxSessionVol = 0, totalSets = 0, totalReps = 0;
    const seenExercises = new Set();
    const sessionsPerLift = {}; // exercise -> how many separate sessions it has appeared in
    for (const h of chrono) {
        const hUnit = h.unit || unit;
        const hour = new Date(h.date).getHours();
        if (hour < 7)
            earlyBird++;
        if (hour >= 21)
            nightOwl++;
        // A gap of two weeks or more, followed by this session: they came back. That deserves a badge,
        // not a broken streak and silence.
        if (prevDate && h.date - prevDate >= 14 * 86400000)
            comeback = 1;
        prevDate = h.date;
        // A missing or junk duration counts as zero rather than NaN-ing the running total.
        totalMin += Math.max(0, parseFloat(h.durationMin) || 0);
        const dow = new Date(h.date).getDay();
        if (dow === 0 || dow === 6)
            weekendCount++;
        maxSessionVol = Math.max(maxSessionVol, toUnit(h.volume || 0, hUnit, unit));
        let prsToday = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            seenExercises.add(id);
            sessionsPerLift[id] = (sessionsPerLift[id] || 0) + 1;
            /* Counted on reps, not weight. A set of chin-ups or planks logs no load, and gating on w > 0
               would quietly decide that bodyweight work isn't work — the one population most likely to be
               training entirely without a barbell would earn nothing here. */
            setsOf(p).forEach(x => {
                const r = parseInt(x.r != null ? x.r : x.reps);
                if (r > 0) {
                    totalSets++;
                    totalReps += r;
                }
            });
            const e1 = toUnit(sessionE1RM(p), hUnit, unit);
            if (!(e1 > 0))
                continue;
            // Only counts as a PR if we've seen the lift before — otherwise every new exercise would hand
            // out a free record on its first outing.
            if (bestE1[id] != null && e1 > bestE1[id]) {
                prs++;
                prsToday++;
            }
            if (bestE1[id] == null || e1 > bestE1[id])
                bestE1[id] = e1;
        }
        bestPrDay = Math.max(bestPrDay, prsToday);
    }
    // Bodyweight-relative strength. Locked (value 0) until a bodyweight is set in Settings, rather than
    // silently comparing against nothing.
    const ratio = (id) => (bw > 0 && bestE1[id] > 0) ? bestE1[id] / bw : 0;
    const benchR = ratio(BIG_THREE.bench), squatR = ratio(BIG_THREE.squat), deadR = ratio(BIG_THREE.deadlift);
    /* Combined big-three total, as a multiple of bodyweight. Requires ALL THREE to have been logged —
       summing whatever exists would let a strong bench and squat alone clear a total that is supposed
       to represent three lifts, which is exactly the "lie about progress" this board is meant to
       avoid. Two lifts and a blank is not a total. */
    const bigThreeR = (benchR > 0 && squatR > 0 && deadR > 0) ? benchR + squatR + deadR : 0;
    const totalHours = totalMin / 60;
    const maxLiftSessions = Object.keys(sessionsPerLift).length ? Math.max(...Object.values(sessionsPerLift)) : 0;
    // Elapsed days between first and last logged session. Deliberately NOT a streak: time served
    // counts even across breaks, so a layoff never erases it.
    const spanDays = chrono.length > 1 ? Math.round((chrono[chrono.length - 1].date - chrono[0].date) / 86400000) : 0;
    const tiers = [
        // showing up
        { id: "first", cat: "Showing up", icon: "🌱", label: "First Step", desc: "Log your first workout", target: 1, value: n },
        { id: "five", cat: "Showing up", icon: "🔥", label: "Getting Going", desc: "5 workouts logged", target: 5, value: n },
        { id: "fifteen", cat: "Showing up", icon: "💪", label: "Committed", desc: "15 workouts logged", target: 15, value: n },
        { id: "fifty", cat: "Showing up", icon: "🏋️", label: "Iron Habit", desc: "50 workouts logged", target: 50, value: n },
        { id: "century", cat: "Showing up", icon: "🏆", label: "Century", desc: "100 workouts logged", target: 100, value: n },
        { id: "veteran", cat: "Showing up", icon: "🗿", label: "Veteran", desc: "250 workouts logged", target: 250, value: n },
        { id: "halfk", cat: "Showing up", icon: "🛡️", label: "Ironclad", desc: "500 workouts logged", target: 500, value: n },
        // consistency
        { id: "week3", cat: "Consistency", icon: "📅", label: "Week Warrior", desc: "3 workouts in one week", target: 3, value: maxInWeek },
        { id: "week5", cat: "Consistency", icon: "🗓️", label: "Five in Seven", desc: "5 workouts in one week", target: 5, value: maxInWeek },
        { id: "streak3", cat: "Consistency", icon: "⚡", label: "On a Roll", desc: "Train 3 weeks straight", target: 3, value: best },
        { id: "streak8", cat: "Consistency", icon: "🔗", label: "Unbroken", desc: "Train 8 weeks straight", target: 8, value: best },
        { id: "streak26", cat: "Consistency", icon: "🧱", label: "Half a Year", desc: "Train 26 weeks straight", target: 26, value: best },
        { id: "streak52", cat: "Consistency", icon: "👑", label: "Year of Iron", desc: "Train 52 weeks straight", target: 52, value: best },
        { id: "fullmonth", cat: "Consistency", icon: "📆", label: "Solid Month", desc: "4 straight weeks with 3+ workouts each", target: 4, value: bestFullRun },
        { id: "comeback", cat: "Consistency", icon: "🔄", label: "Back at It", desc: "Return to training after a break of 2 weeks or more", target: 1, value: comeback },
        // Time served, not time unbroken — these survive a layoff on purpose.
        { id: "year1", cat: "Consistency", icon: "🎂", label: "One Year In", desc: "A year between your first and latest workout", target: 365, value: spanDays },
        { id: "year2", cat: "Consistency", icon: "🌳", label: "Two Years In", desc: "Two years between your first and latest workout", target: 730, value: spanDays },
        // getting stronger
        { id: "pr1", cat: "Getting stronger", icon: "🎯", label: "New Best", desc: "Beat a previous best on any lift", target: 1, value: prs },
        { id: "pr10", cat: "Getting stronger", icon: "💥", label: "Record Breaker", desc: "Set 10 personal records", target: 10, value: prs },
        { id: "pr50", cat: "Getting stronger", icon: "🚀", label: "PR Machine", desc: "Set 50 personal records", target: 50, value: prs },
        { id: "pr100", cat: "Getting stronger", icon: "🌟", label: "Unstoppable", desc: "Set 100 personal records", target: 100, value: prs },
        { id: "prday3", cat: "Getting stronger", icon: "🎆", label: "Big Day", desc: "Set 3 personal records in one session", target: 3, value: bestPrDay },
        { id: "prday5", cat: "Getting stronger", icon: "🎇", label: "Perfect Storm", desc: "Set 5 personal records in one session", target: 5, value: bestPrDay },
        { id: "bwBench", cat: "Getting stronger", icon: "🏅", label: "Bodyweight Bench", desc: "Bench press your own bodyweight", target: 1, value: benchR },
        { id: "bwSquat", cat: "Getting stronger", icon: "🦿", label: "Squat 1.5×", desc: "Squat 1.5× your bodyweight", target: 1.5, value: squatR },
        { id: "bwDead", cat: "Getting stronger", icon: "🐘", label: "Deadlift 2×", desc: "Deadlift 2× your bodyweight", target: 2, value: deadR },
        { id: "bwBench15", cat: "Getting stronger", icon: "🥇", label: "Bench 1.5×", desc: "Bench press 1.5× your bodyweight", target: 1.5, value: benchR },
        { id: "bwSquat2", cat: "Getting stronger", icon: "🦵", label: "Squat 2×", desc: "Squat 2× your bodyweight", target: 2, value: squatR },
        { id: "bwDead25", cat: "Getting stronger", icon: "🐋", label: "Deadlift 2.5×", desc: "Deadlift 2.5× your bodyweight", target: 2.5, value: deadR },
        { id: "bigThree4", cat: "Getting stronger", icon: "🏛️", label: "Total Package", desc: "Bench + squat + deadlift equal to 4× your bodyweight", target: 4, value: bigThreeR },
        { id: "bigThree5", cat: "Getting stronger", icon: "⚜️", label: "Elite Total", desc: "Bench + squat + deadlift equal to 5× your bodyweight", target: 5, value: bigThreeR },
        // breadth
        { id: "variety25", cat: "Breadth", icon: "🧭", label: "Explorer", desc: "Train 25 different exercises", target: 25, value: seenExercises.size },
        { id: "variety50", cat: "Breadth", icon: "🗺️", label: "Well Rounded", desc: "Train 50 different exercises", target: 50, value: seenExercises.size },
        { id: "variety100", cat: "Breadth", icon: "🌐", label: "Cartographer", desc: "Train 100 different exercises", target: 100, value: seenExercises.size },
        { id: "parts8", cat: "Breadth", icon: "🧩", label: "Full Coverage", desc: "Train 8 different muscle groups in one week", target: 8, value: bestPartsWeek },
        // when you train
        { id: "earlybird", cat: "When you train", icon: "🌅", label: "Early Bird", desc: "10 workouts started before 7am", target: 10, value: earlyBird },
        { id: "nightowl", cat: "When you train", icon: "🌙", label: "Night Owl", desc: "10 workouts started after 9pm", target: 10, value: nightOwl },
        { id: "weekend25", cat: "When you train", icon: "🎽", label: "Weekend Warrior", desc: "25 workouts on a Saturday or Sunday", target: 25, value: weekendCount },
        // tonnage
        { id: "vol1", cat: "Tonnage", icon: "🪨", label: "Tonnage", desc: "50k total volume lifted", target: 50000, value: totalVol },
        { id: "vol2", cat: "Tonnage", icon: "⛰️", label: "Heavy Mover", desc: "250k total volume lifted", target: 250000, value: totalVol },
        { id: "vol3", cat: "Tonnage", icon: "🌋", label: "Million Club", desc: "1M total volume lifted", target: 1000000, value: totalVol },
        { id: "vol4", cat: "Tonnage", icon: "🌌", label: "Mountain Mover", desc: "5M total volume lifted", target: 5000000, value: totalVol },
        { id: "vol5", cat: "Tonnage", icon: "🪐", label: "Continental", desc: "10M total volume lifted", target: 10000000, value: totalVol },
        { id: "bigSession", cat: "Tonnage", icon: "💣", label: "Monster Session", desc: "15k volume in a single workout", target: 15000, value: maxSessionVol },
        // Hours under the bar. Rewards the work itself rather than how it was scheduled — an hour is
        // an hour whether it came in a streak or after six months off.
        { id: "hours10", cat: "Time under the bar", icon: "🕐", label: "Ten Hours", desc: "10 hours of logged training", target: 10, value: totalHours },
        { id: "hours50", cat: "Time under the bar", icon: "⏳", label: "Fifty Hours", desc: "50 hours of logged training", target: 50, value: totalHours },
        { id: "hours100", cat: "Time under the bar", icon: "🕰️", label: "Hundred Hours", desc: "100 hours of logged training", target: 100, value: totalHours },
        { id: "hours500", cat: "Time under the bar", icon: "🗼", label: "Five Hundred Hours", desc: "500 hours of logged training", target: 500, value: totalHours },
        // Sets and reps — the unit of work that isn't load. Counts bodyweight training as fully as
        // barbell training, which the tonnage badges above structurally cannot.
        { id: "sets1k", cat: "Sets & reps", icon: "🧮", label: "Set Collector", desc: "1,000 sets logged", target: 1000, value: totalSets },
        { id: "sets10k", cat: "Sets & reps", icon: "🏗️", label: "Ten Thousand Sets", desc: "10,000 sets logged", target: 10000, value: totalSets },
        { id: "reps10k", cat: "Sets & reps", icon: "🔁", label: "Ten Thousand Reps", desc: "10,000 reps logged", target: 10000, value: totalReps },
        { id: "reps100k", cat: "Sets & reps", icon: "♾️", label: "Hundred Thousand Reps", desc: "100,000 reps logged", target: 100000, value: totalReps },
        // Devotion — sticking with a lift long enough to actually get good at it.
        { id: "lift50", cat: "Devotion", icon: "🤝", label: "Old Faithful", desc: "Train the same exercise in 50 sessions", target: 50, value: maxLiftSessions },
        { id: "lift100", cat: "Devotion", icon: "🪢", label: "Lifelong Lift", desc: "Train the same exercise in 100 sessions", target: 100, value: maxLiftSessions },
        { id: "bigThreeWeek", cat: "Devotion", icon: "🎳", label: "The Big Three", desc: "Squat, bench and deadlift all in one week", target: 1, value: bigThreeWeek },
    ];
    return tiers.map(t => ({ ...t, done: t.value >= t.target, progress: Math.min(1, t.value / t.target) }));
}

const MILESTONE_XP = {
    first: 10, five: 20, fifteen: 40, fifty: 100, century: 250, veteran: 600, halfk: 1500,
    week3: 15, week5: 30, streak3: 40, streak8: 120, streak26: 400, streak52: 900, comeback: 20,
    fullmonth: 60, year1: 300, year2: 700,
    pr1: 15, pr10: 60, pr50: 250, pr100: 500, prday3: 80, prday5: 150,
    bwBench: 120, bwSquat: 150, bwDead: 180, bwBench15: 220, bwSquat2: 260, bwDead25: 300,
    bigThree4: 350, bigThree5: 800,
    variety25: 60, variety50: 150, variety100: 300, parts8: 80,
    earlybird: 50, nightowl: 50, weekend25: 70,
    vol1: 40, vol2: 150, vol3: 500, vol4: 1500, vol5: 3000, bigSession: 90,
    hours10: 30, hours50: 100, hours100: 200, hours500: 800,
    sets1k: 80, sets10k: 400, reps10k: 80, reps100k: 500,
    lift50: 100, lift100: 250, bigThreeWeek: 60
};

const LEVEL_TITLES = [
    [1, "Newcomer"], [5, "Regular"], [10, "Dedicated"], [16, "Iron Disciple"], [22, "Forged"],
    [30, "Relentless"], [40, "Iron Veteran"], [50, "Legend"], [65, "Titan"], [80, "Mythic"],
];

function computeLevel(history, milestones) {
    const n = canonicalHistoryNewest(history).length;
    const K = 8; // tuned so level 2 lands on your first workout and a maxed multi-year veteran lands ~level 35-40
    const milestoneXP = (milestones || []).filter(m => m.done).reduce((s, m) => s + (MILESTONE_XP[m.id] ?? 15), 0);
    const xp = n * 8 + milestoneXP;
    const level = Math.max(1, Math.floor(1 + Math.sqrt(xp / K)));
    const xpAtLevel = K * (level - 1) * (level - 1);
    const xpForNext = K * level * level;
    const progress = xpForNext > xpAtLevel ? clamp((xp - xpAtLevel) / (xpForNext - xpAtLevel), 0, 1) : 1;
    const title = LEVEL_TITLES.filter(([lvl]) => lvl <= level).slice(-1)[0]?.[1] || "Newcomer";
    return { level, xp, xpIntoLevel: xp - xpAtLevel, xpForLevel: xpForNext - xpAtLevel, progress, title };
}

function normalizeBwLog(bwLog, to) {
    return (bwLog || [])
        .filter(e => e && e.date != null && Number(e.w) > 0)
        .map(e => ({ ...e, w: e.unit && to && e.unit !== to ? Math.round(toUnit(Number(e.w), e.unit, to) * 10) / 10 : Number(e.w), unit: to || e.unit }))
        .sort((a, b) => a.date - b.date);
}

let _planOverviewMemo = null;

function planOverviewMemo(program, weekIndex, cycle, history, saved) {
    const m = _planOverviewMemo;
    if (m && m.program === program && m.weekIndex === weekIndex && m.cycle === cycle && m.history === history
        && m.saved === saved && m.restScale === REST_SCALE && m.exercises.length === EXERCISES.length
        && m.exercises.every((ex, i) => ex === EXERCISES[i]))
        return m.value;
    const value = planOverview(program, weekIndex, cycle, history, saved);
    _planOverviewMemo = { program, weekIndex, cycle, history, saved, restScale: REST_SCALE,
        exercises: EXERCISES.slice(), value };
    return value;
}

function planOverview(program, weekIndex = 1, cycle = null, history = [], saved = []) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return null;
    const days = program.days;
    const accum = weeksOf(program);
    const hasDeload = !!(program.config && program.config.deload);
    const total = accum + (hasDeload ? 1 : 0);
    /* WEEKS × DAYS. Each cell is that day at that week — total working sets and the estimated length,
       both already week-aware, so a deload week visibly shrinks instead of being a label on identical
       numbers. Storing dayId per cell lets the UI open the real day at the real week on tap. */
    /* SESSIONS ALREADY LOGGED, per week. History entries carry `weekIndex`, so the plan can say how
       much of each week you actually did rather than only what it prescribes — which is the difference
       between a plan you read and a plan you are inside of. */
    const doneByWeek = {}, doneDaysByWeek = {};
    for (const h of history || []) {
        if (!h || h.programId !== program.id)
            continue;
        const w = Number(h.weekIndex);
        if (!Number.isFinite(w) || w < 1)
            continue;
        doneByWeek[w] = (doneByWeek[w] || 0) + 1;
        if (h.dayId)
            (doneDaysByWeek[w] = doneDaysByWeek[w] || new Set()).add(h.dayId);
    }
    const weeks = [];
    for (let w = 1; w <= total; w++) {
        const isDeload = hasDeload && w === total;
        const done = Math.min(doneByWeek[w] || 0, days.length);
        /* status drives the badge. Exactly one week is NOW and at most one is NEXT, so the list has a
           single obvious entry point rather than several competing highlights. */
        const status = w === weekIndex ? "now" : w === weekIndex + 1 ? "next" : w < weekIndex ? "past" : "todo";
        const row = { week: w, isDeload, current: w === weekIndex, status, done, total: days.length, days: [], sets: 0, minutes: 0 };
        for (const d of days) {
            let sets = 0;
            (d.exercises || []).forEach((id, si) => {
                const c = computeCell(program, d, id, si, w);
                sets += Number(c && c.sets) || 0;
            });
            const mins = estimateMinutes(program, d, w) || 0;
            /* WHAT THE DAY ACTUALLY IS, not just how long it takes. A row reading "Push · Chest · 27 sets
               · 62m" describes a container; the lifter wants to know what is IN it. The lead lift and the
               muscles it works are the two things that distinguish one day from another at a glance, and
               both are already derivable — no new stored state. */
            const exIds = d.exercises || [];
            const leadId = exIds[d.primaryIndex] || exIds[0];
            const leadEx = leadId ? EX_BY_ID[leadId] : null;
            const focus = dayMuscleVolume(program, d, w).slice(0, 3).map(([part]) => part);
            row.days.push({ dayId: d.id, label: d.label || "Day", sets, minutes: mins,
                exCount: exIds.length, lead: leadEx ? leadEx.name : null, focus,
                lifts: exIds.map(id => (EX_BY_ID[id] ? EX_BY_ID[id].name : null)).filter(Boolean),
                done: !!(doneDaysByWeek[w] && doneDaysByWeek[w].has(d.id)) });
            row.sets += sets;
            row.minutes += mins;
        }
        weeks.push(row);
    }
    /* PER-LIFT PROGRESSION. One row per exercise that appears anywhere in the block, carrying its
       prescription for every week. A lift can sit in different slots on different days, so it is keyed
       by exercise id and takes its FIRST appearance — the progression is a property of the lift, and
       showing one row per (day, slot) would turn a readable table into noise. */
    const liftMap = new Map();
    days.forEach(d => (d.exercises || []).forEach((id, si) => {
        if (liftMap.has(id))
            return;
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        liftMap.set(id, {
            id, name: ex.name, dayLabel: d.label || "Day", primary: si === d.primaryIndex,
            cells: Array.from({ length: total }, (_, i) => {
                const c = computeCell(program, d, id, si, i + 1) || {};
                return { week: i + 1, sets: c.sets, reps: c.reps, rir: c.rir, note: c.note,
                    isDeload: hasDeload && i + 1 === total };
            })
        });
    }));
    /* primaries first, then the order they appear — the lift you build the block around reads first */
    const lifts = [...liftMap.values()].sort((a, b) => (a.primary === b.primary ? 0 : a.primary ? -1 : 1));
    /* PHASES. A cycle's blocks are the real phases and are labelled; a standalone program still HAS
       phases — accumulation, then the deload — and saying so is more honest than showing nothing. */
    let phases;
    if (cycle && Array.isArray(cycle.blockMeta) && cycle.blockMeta.length) {
        const here = cycle.blockIds ? cycle.blockIds.indexOf(program.id) : -1;
        phases = cycleBlockMetadata(cycle, [...saved.filter(p => p.id !== program.id), program]).map((m, i) => ({
            key: m.id || `b${i}`, label: m.label || `Block ${i + 1}`, goal: m.goal || null,
            weeks: m.weeks || null, deload: m.deload, current: i === here, done: here >= 0 && i < here, isBlock: true
        }));
    }
    else {
        phases = [{ key: "accum", label: "Accumulation", goal: program.config?.goal || null, weeks: accum, current: weekIndex <= accum, done: weekIndex > accum, isBlock: false }];
        if (hasDeload)
            phases.push({ key: "deload", label: "Deload", goal: null, weeks: 1, current: weekIndex > accum, done: false, isBlock: false });
    }
    /* THE NEXT THING TO DO. A plan that cannot answer "what now" is a document, not a plan. It is the
       first unlogged day of the CURRENT week; if the week is complete it rolls to the next week's
       first day, and at the end of the block there is nothing left to point at. */
    let upNext = null;
    for (const w of weeks) {
        if (w.week < weekIndex)
            continue;
        const d = w.days.find(x => !x.done);
        if (d) {
            upNext = { ...d, week: w.week };
            break;
        }
    }
    const sessionsTotal = total * days.length;
    const sessionsDone = weeks.reduce((n, w) => n + w.done, 0);
    /* WEEKS BELONG TO PHASES. Without this the list is a flat run of weeks and the phase strip is
       decoration; with it the plan reads as structure — Foundation weeks 1-5, then Development. */
    /* A CYCLE'S PHASES ARE OTHER PROGRAMS, NOT OTHER WEEKS OF THIS ONE.
       The first version walked a cursor across THIS program's weeks and clamped each phase to its
       length, so on a 6-week block inside a 3-block cycle the strip printed "Strength W6-6" and
       "Peak W10-6" — ranges that are empty, and wrong. Block spans are cumulative over the CYCLE, so
       they must be summed from blockMeta and never clamped to the current block's length.
       Every week of this program belongs to the block it IS. Weeks are only distributed across phases
       in the standalone case, where the phases really are stretches of one program. */
    {
        let cursor = 0;
        for (const ph of phases) {
            const span = (ph.weeks || (ph.key === "deload" ? 1 : ph.isBlock ? 0 : total)) + (ph.isBlock && ph.deload ? 1 : 0);
            ph.weekFrom = cursor + 1;
            ph.weekTo = cursor + Math.max(span, 1);
            cursor += Math.max(span, 1);
        }
        const currentPhaseKey = (phases.find(p => p.current) || phases[0] || {}).key || null;
        for (const w of weeks) {
            if (phases.some(p => p.isBlock)) {
                w.phaseKey = currentPhaseKey;
                continue;
            }
            const ph = phases.find(p => w.week >= p.weekFrom && w.week <= p.weekTo) || phases[phases.length - 1];
            w.phaseKey = ph ? ph.key : null;
        }
    }
    return { weeks, lifts, phases, totalWeeks: total, accumWeeks: accum, hasDeload, currentWeek: weekIndex,
        dayLabels: days.map(d => d.label || "Day"), sessionsTotal, sessionsDone, upNext,
        percentDone: sessionsTotal ? Math.round((sessionsDone / sessionsTotal) * 100) : 0,
        /* the phase you are in right now, for the header tile */
        currentPhase: (phases.find(p => p.current) || phases[0] || null) };
}

function historyVolumeIn(h, unit) {
    let volume = 0, hasLedger = false;
    for (const p of Object.values(h?.perf || {})) {
        const work = setsOf(p).filter(isWorkSet);
        if (!work.length)
            continue;
        hasLedger = true;
        for (const st of work) {
            const w = Number(st?.w ?? st?.weight), r = Number(st?.r ?? st?.reps);
            if (Number.isFinite(w) && w > 0 && Number.isFinite(r) && r > 0)
                volume += w * r;
        }
    }
    /* Old imports may predate per-set ledgers. Preserve their cached total only when there is no
       working-set evidence to recompute; a stale cached aggregate can never overrule real sets. */
    if (!hasLedger) {
        volume = Number(h?.volume);
        if (!Number.isFinite(volume) || volume <= 0)
            return 0;
    }
    const from = h?.unit || "kg";
    return !unit || from === unit ? volume : from === "lb" && unit === "kg" ? volume / 2.2046226218 : from === "kg" && unit === "lb" ? volume * 2.2046226218 : volume;
}

function filterWorkoutHistory(history, saved, query, programId, period, now = Date.now()) {
    const names = new Map((saved || []).map(p => [p.id, p.name]));
    const words = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
    const cutoff = period === "all" ? -Infinity : now - Number(period) * 86400000;
    return (history || []).filter(h => {
        if (!h || h.date == null || !Number.isFinite(new Date(h.date).getTime()))
            return false;
        if (programId !== "all" && String(h.programId || "none") !== programId)
            return false;
        if (new Date(h.date).getTime() < cutoff)
            return false;
        const text = [h.dayLabel, h.programName, names.get(h.programId), h.note, ...Object.entries(h.perf || {}).flatMap(([id, p]) => [EX_BY_ID[id]?.name || p?.name || id, p?.note])].filter(Boolean).join(" ").toLocaleLowerCase();
        return words.every(word => text.includes(word));
    }).slice().sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
}

function groupSessionsByCycle(history, cycles, saved, unit) {
    const progById = new Map((saved || []).map(p => [p.id, p]));
    const cycleById = new Map((cycles || []).map(c => [c.id, c]));
    const groups = new Map();
    for (const h of history || []) {
        if (!h || h.date == null || isNaN(new Date(h.date)))
            continue;
        const prog = progById.get(h.programId);
        const cycle = prog && prog.cycleId ? cycleById.get(prog.cycleId) : null;
        /* Key on the CYCLE, and carry the block within it. A deleted program leaves history behind, so
           every lookup above can miss — that is normal, not an error, and it lands in "other". */
        const key = cycle ? `c:${cycle.id}` : `p:${h.programId || "none"}`;
        let g = groups.get(key);
        if (!g) {
            g = {
                key, cycleId: cycle ? cycle.id : null,
                label: cycle ? (cycle.name || "Training cycle") : (prog ? prog.name : h.programName || "Earlier sessions"),
                isCycle: !!cycle, blocks: new Map(), items: [], count: 0, volume: 0, newest: 0
            };
            groups.set(key, g);
        }
        g.items.push(h);
        g.count++;
        if (h.date > g.newest)
            g.newest = h.date;
        const v = historyVolumeIn(h, unit);
        if (Number.isFinite(v) && v > 0)
            g.volume += v;
        /* Blocks only mean something inside a cycle. Prefer the cycle's own blockMeta label over the
           copy stamped on the program, so renaming a block in one place doesn't split the group. */
        if (cycle) {
            const meta = (cycle.blockMeta || []).find(m => m.id === h.programId);
            const bLabel = (meta && meta.label) || (prog && prog.blockLabel) || (prog && prog.name) || "Block";
            const bKey = h.programId || bLabel;
            let b = g.blocks.get(bKey);
            if (!b) {
                b = { key: bKey, label: bLabel, index: (cycle.blockIds || []).indexOf(h.programId), items: [], count: 0, volume: 0, newest: 0 };
                g.blocks.set(bKey, b);
            }
            b.items.push(h);
            b.count++;
            if (h.date > b.newest)
                b.newest = h.date;
            if (Number.isFinite(v) && v > 0)
                b.volume += v;
        }
    }
    return [...groups.values()]
        .map(g => ({
        ...g,
        /* blocks in PROGRAMME order, which is how the cycle was planned and read; a block with no
           position (its program was deleted) sorts last rather than jumping to the front on -1 */
        blocks: [...g.blocks.values()].sort((a, b) => (a.index < 0 ? 1 : b.index < 0 ? -1 : a.index - b.index))
    }))
        .sort((a, b) => b.newest - a.newest);
}

function groupSessionsByMonth(history, unit) {
    const groups = new Map();
    const thisYear = new Date().getFullYear();
    for (const h of history || []) {
        if (!h || h.date == null)
            continue;
        const d = new Date(h.date);
        if (isNaN(d))
            continue;
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
        let g = groups.get(key);
        if (!g) {
            g = { key, label: d.toLocaleDateString(undefined, d.getFullYear() === thisYear ? { month: "long" } : { month: "long", year: "numeric" }), items: [], count: 0, volume: 0 };
            groups.set(key, g);
        }
        g.items.push(h);
        g.count++;
        const v = historyVolumeIn(h, unit);
        if (Number.isFinite(v) && v > 0)
            g.volume += v;
    }
    // newest month first, matching the order the sessions themselves are already in
    return [...groups.values()].sort((a, b) => (a.key < b.key ? 1 : -1));
}

function weeklyBodyweightTrend(bwLog, unit) {
    const entries = normalizeBwLog(bwLog, unit);
    if (entries.length < 2)
        return null;
    const last = entries[entries.length - 1].date;
    const DAY = 86400000, WEEK = 7 * DAY;
    // bucket[0] = most recent 7 days, bucket[1] = the 7 days before that, etc.
    const buckets = [];
    entries.forEach(e => {
        const idx = Math.floor((last - e.date) / WEEK);
        (buckets[idx] || (buckets[idx] = [])).push(e.w);
    });
    const avg = (arr) => arr.reduce((s, v) => s + v, 0) / arr.length;
    const weeks = buckets.map((b, i) => b && b.length ? { weekIndex: i, avg: avg(b), n: b.length } : null).filter(Boolean);
    if (weeks.length < 2)
        return null; // everything logged fell inside one 7-day window
    const current = weeks[0];
    const prior = weeks[weeks.length - 1]; // oldest populated bucket — the longest-baseline comparison available
    const spanWeeks = prior.weekIndex - current.weekIndex;
    const changeAbs = current.avg - prior.avg;
    const changePerWeek = spanWeeks > 0 ? changeAbs / spanWeeks : 0;
    const pctPerWeek = prior.avg > 0 ? (changePerWeek / prior.avg) * 100 : 0;
    return {
        currentWeekAvg: Math.round(current.avg * 10) / 10,
        priorWeekAvg: Math.round(prior.avg * 10) / 10,
        changeAbs: Math.round(changeAbs * 100) / 100,
        changePerWeek: Math.round(changePerWeek * 100) / 100,
        pctPerWeek: Math.round(pctPerWeek * 100) / 100,
        spanWeeks,
        weeksOfData: weeks.length
    };
}

function homeProgramGroups(saved, cycles, activeId) {
    const byCycle = new Map(cycles.map(c => [c.id, c]));
    const groups = new Map();
    for (const p of saved) {
        const key = p.cycleId ? `cycle:${p.cycleId}` : `program:${p.id}`;
        if (!groups.has(key)) groups.set(key, { key, cycle: byCycle.get(p.cycleId), cycleId: p.cycleId, programs: [] });
        groups.get(key).programs.push(p);
    }
    return [...groups.values()].map(g => {
        g.programs.sort((a, b) => (a.cycleIndex ?? 0) - (b.cycleIndex ?? 0));
        g.current = g.programs.some(p => p.id === activeId);
        g.done = !!g.cycle?.done;
        g.name = g.cycle?.name || (g.cycleId ? 'Training cycle' : g.programs[0].name);
        g.createdAt = Math.max(...g.programs.map(p => Number(p.createdAt) || 0));
        return g;
    }).sort((a, b) => Number(b.current) - Number(a.current) || Number(a.done) - Number(b.done) || b.createdAt - a.createdAt);
}

const SELF_TEST_SUPPORTED_ENGINES = new Set(ENGINE_COMPATIBLE_VERSIONS);

const SELF_TEST_HISTORY_LIMIT = 160;

const SELF_TEST_FRAME_BUDGET_MS = 10;

function cycleProgress(cycle, saved = [], history = [], cycleInfo = null) {
    const meta = cycleBlockMetadata(cycle, saved);
    const done = !!(cycle && cycle.done);
    const activeIdx = done ? meta.length : (cycle?.activeBlock || 0);
    const isActiveCycle = !!(cycleInfo && cycleInfo.cycle && cycle && cycleInfo.cycle.id === cycle.id);
    const progOf = b => (b ? saved.find(s => s.id === b.id) : null);
    const totalOf = b => (b?.weeks || 0) + (b?.deload ? 1 : 0);
    const totalWeeks = meta.reduce((n, b) => n + totalOf(b), 0);
    const startMs = cycle?.startedAt || cycle?.createdAt || null;
    let doneWeeks = 0, acc = 0;
    const blocks = meta.map((b, i) => {
        const total = totalOf(b);
        const range = [acc + 1, acc + total];
        acc += total;
        const state = done || i < activeIdx ? "done" : i === activeIdx ? "active" : "upcoming";
        let weeksIn = 0;
        if (state === "done")
            weeksIn = total;
        else if (state === "active" && isActiveCycle)
            weeksIn = Math.max(0, Math.min(total, cycleInfo.isDeload ? b.weeks : (cycleInfo.weekIndex || 1) - 1));
        doneWeeks += weeksIn;
        const prog = progOf(b);
        const sessions = (history || []).filter(h => h && h.programId === b.id).length;
        return {
            ...b, index: i, state, total, weeksIn, range, sessions,
            exists: !!prog,
            days: prog?.days?.length || prog?.config?.days || null,
            deload: !!prog?.config?.deload,
            split: prog && Array.isArray(prog.days) ? prog.days.map(d => d.label).filter(Boolean) : [],
            dates: startMs ? [startMs + (range[0] - 1) * 7 * 86400000, startMs + range[1] * 7 * 86400000 - 86400000] : null
        };
    });
    const ids = cycle?.blockIds || meta.map(b => b.id);
    const sessions = (history || []).filter(h => h && ids.includes(h.programId)).length;
    /* PLANNED-TO-DATE counts only ELAPSED weeks, so "12 of 12 logged" means you have kept up, not that
       you have finished the cycle. Comparing against the whole cycle would show every lifter permanently
       behind, which is not information. */
    const plannedToDate = blocks.reduce((n, b) => n + (b.days || 0) * b.weeksIn, 0);
    return {
        blocks, done, activeIdx, totalWeeks, doneWeeks,
        pct: totalWeeks ? Math.round(100 * doneWeeks / totalWeeks) : 0,
        curWeek: done ? totalWeeks : Math.min(totalWeeks, doneWeeks + 1),
        weeksLeft: Math.max(0, totalWeeks - doneWeeks),
        sessions, plannedToDate,
        startMs, endMs: startMs && totalWeeks ? startMs + totalWeeks * 7 * 86400000 : null,
        active: blocks[activeIdx] || null
    };
}

function blockReview(history, cycle, i) {
    const meta = (cycle?.blockMeta || [])[i] || null;
    const id = (cycle?.blockIds || [])[i] || meta?.id || null;
    const hs = (history || []).filter(h => h && h.programId === id && h.date);
    if (hs.length < 3)
        return { ok: false, block: meta, sessions: hs.length, reason: hs.length ? `Only ${hs.length} session${hs.length === 1 ? "" : "s"} logged in this block — not enough to say anything honest about it.` : "No sessions were logged against this block." };
    const dates = hs.map(h => h.date);
    const from = Math.min(...dates), to = Math.max(...dates);
    const r = blockRetro(hs, { sinceDate: from - 1, untilDate: to + 1 });
    return { ...r, block: meta, from, to };
}

function blockChangeSummary(before, after) {
    if (!before || !after)
        return null;
    const ids = p => new Set((p.days || []).flatMap(d => d.exercises || []));
    const oldIds = ids(before), newIds = ids(after);
    const retained = [...newIds].filter(id => oldIds.has(id)).length;
    const added = [...newIds].filter(id => !oldIds.has(id)).map(id => EX_BY_ID[id]?.name || id);
    const removed = [...oldIds].filter(id => !newIds.has(id)).map(id => EX_BY_ID[id]?.name || id);
    const opening = p => {
        const rows = Object.values(p.nextWeekPrescriptions || {}).map(weeks => weeks?.[1] ?? weeks?.["1"]).filter(Boolean);
        if (!rows.length)
            return null;
        const reps = rows.flatMap(row => row.reps || []).filter(Number.isFinite);
        return { sets: rows.reduce((n, row) => n + (Number(row.sets) || 0), 0), repRange: reps.length ? `${Math.min(...reps)}–${Math.max(...reps)}` : "—" };
    };
    return { retained, added, removed, before: opening(before), after: opening(after) };
}

const RELEASE_DIAG_KEY = "wpb:release-diag";

const RELEASE_DIAG_CAP = 40;

const RELEASE_DIAG_FIELDS = new Set(["code", "source", "action", "phase", "engine", "reason", "status", "build"]);

function isRuntimeProgram(p) {
    return !!p && (p.engineSource === "pursuit-next" || p.artifactType === "freestyle" || p.custom === true);
}
export { ALL_PLATES, ASSIST_IDS, AVAIL_PLATES, BARS, BAR_LABEL, BIG_THREE, BIRTH_RE, COVERED_MUSCLES, DELT_HEAD, EQUIP_CATS, EX_ABBREV, EX_FAMILY, EX_METRICS, EX_METRIC_BY_ID, EX_SPECIFIC, EX_WINDOWS, FOCUS_CAVEAT, GAP_FRACTION, GENERATION_ROUTE, GYM_LIMIT_KEYS, INTENT_FROM_CAT, LEGACY_EX_TYPICAL_KEYS, LEVEL_TITLES, MACHINE_SETUP, MILESTONE_XP, MIN_DAY_SESSIONS, MOBILITY, MUSCLE_SYNONYM, MYO_MAX_SETS, MYO_MINI_REPS, MYO_MINI_SETS, MYO_MIN_REPS, MYO_TARGET_REPS, NOVICE_INELIGIBLE_SPLITS, PATTERNS, PATTERN_GROUPS, PATTERN_MIN_SETS, PHASES, PHASE_ROT, PLANNED_ROOM_RIR, PLATES, PROG_POLICIES, PROG_STYLES, PV_PAD_TOP, PV_ROW_GAP, RACK_PRESETS, REDUCE_FRACTION, REFERENCES, REGION_MOVEMENT, REGION_REQUIRED, RELEASE_DIAG_CAP, RELEASE_DIAG_FIELDS, RELEASE_DIAG_KEY, REST_SCALE, SCORE_BANDS, SCORE_EQUIV, SCORE_PATTERN, SCORE_PATTERN_LABEL, SELF_TEST_FRAME_BUDGET_MS, SELF_TEST_HISTORY_LIMIT, SELF_TEST_SUPPORTED_ENGINES, SESSION_BOUNDS, SESSION_EX_TYPICAL, SETUP_FIELD_ORDER, SETUP_LABELS, SET_ROW_BLEED, SEX_FACTOR, STANDARDS, STD_LEVELS, STRENGTH_CLUB_LIFTS, STRENGTH_CLUB_TIERS_LB, STRETCH_FOCUS_E2, STRETCH_FOCUS_E3, SUBMUSCLE_LABEL, TECHNICAL_LIFTS, TEMPLATE_CATS, VIEW_DEPTH, WARMUP_PYRAMIDS, WARMUP_SET_SEC, WEIGHTED_SWAP, __doseMemo, __slopeMemo, __volKey, __weeklyVolMemo, _dstCache, _lmCache, _planOverviewMemo, addedMinutes, addedSeconds, ageFactor, ageFrom, applyCustomProgramSettings, asLengthUnit, assertExerciseData, auditProgramWeek, auditSets, availableFor, backupRecordImpact, barFor, betterTopSet, birthParts, blockChangeSummary, blockPlanFor, blockRetro, blockReview, budgetIncludesTransitions, buildLifterModel, calibrateDayPerf, calibratedAnchorPerf, canUndoProgramSave, classifyPlateau, clearSetup, coachFacts, completionRate, computeLevel, computeMilestones, constantLoadDecay, coverageGaps, coverageRelief, cuesFor, cycleProgress, dayMuscleBreakdown, dayMuscleVolume, dayScopedTrends, daySeconds, daysInWeek, decodeGallery, deloadAdvice, distributeVolBias, effectiveRest, effortLabel, effortValueLabel, estimateMinutes, estimateMinutesFor, exMatches, exRecords, exWindow, exerciseAt, exerciseProfile, exerciseSeries, exerciseSlotSec, exerciseTrends, explainPrescription, feedbackDelta, filterWorkoutHistory, fmtSets, formatSetup, groupPctSets, groupSessionsByCycle, groupSessionsByMonth, growMyoSets, gymById, gymCapFor, historyForProgram, historyVolumeIn, homeProgramGroups, isAssistedEx, isRuntimeProgram, isStretchFocus, isWorkSet, landmarkOf, lastSetEffort, lastTopSet, lengthUnitFor, lifterModelKey, linearInc, liveCoachForContext, logWindow, loggedSubVolume, loggedVolume, lowerBodyLift, makeMyoMini, mavFor, mergeSessionData, mergedSetCount, newGymId, nextTMEvidence, normalizeBwLog, normalizeGyms, normalizeMeasureLog, overreachSignal, paceFactor, patternCues, patternTrainable, perWeekOf, perfAfterDelete, personalMav, personalRepSlope, phaseFor, phaseKind, pickActiveProgram, pickTopSet, planOverview, planOverviewMemo, plannedWeek, plateauOf, plateauOfLift, plateauSplitByDay, platesPerSide, poolOf, preferenceFloor, prescribedRIRofAny, programChangeLabels, projectNextTM, rankSwapAlts, readinessBand, recommendedSplit, regionGapFix, regionGapsFor, repRange, repsLow, restSec, rirTrend, rmAt, rotationOf, sanitizeInventory, sanitizeLimits, sanitizeRepsInput, sanitizeWeightInput, scoreAttribution, scoreCompose, scoreContin, scoreEquivalent, scoreEquivalentInverse, scoreLevelIdx, scoreLevelLabel, scoreMatur, scorePatternFor, scoreTargetFor, scoreTo100, sessionE1RM, sessionExercisePlan, sessionSnapshotStatus, setAvailPlates, setRestScaleGlobal, setupFieldsFor, setupFieldsWithStored, setupLabel, simulateAndAudit, starvedRegions, strengthClubSnapshot, strengthLevel, strengthScore, strengthScoreHistory, strengthSnapshot, stretchTable, subRegionOf, swapOverlapNames, syncSubSets, techExplain, techSetTag, templateConfig, templateIntent, tempoFor, toLength, todayISO, uid, underRecoveredWeekly, userOwnsRuntimeSet, volumeAudit, volumeLedger, volumeResponse, volumeVerdicts, warmupCount, warmupPlan, warmupRoutine, warmupSets, weekIntent, weekKeyOf, weekMuscleBreakdown, weeklyBodyweightTrend, weeklyRecap, weeklySubVolume, weeklyVolume, workoutPlanCoachFact };
