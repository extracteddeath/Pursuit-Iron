import { generateProgram } from './generate.js';
import { EXERCISE_MAP } from './exercise-db.js';
import { buildGenerationRecoveryPlan } from './generation-recovery.js';
import { prescriptionForSimulationWeek } from './simulation.js';
import { createInitialCycleState } from './cycles.js';
import { filterFeasibleLiftPriorities } from './prescription.js';
const DAY_ORDER = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
const DAY_SETS = {
    1: ['monday'], 2: ['monday', 'friday'], 3: ['monday', 'wednesday', 'friday'], 4: ['monday', 'wednesday', 'friday', 'sunday'],
    5: ['monday', 'tuesday', 'thursday', 'friday', 'sunday'], 6: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'sunday'], 7: DAY_ORDER
};
const DAY_TO_JS = { sunday: 0, monday: 1, tuesday: 2, wednesday: 3, thursday: 4, friday: 5, saturday: 6 };
const SESSION_BANDS = {
    s20: { minMinutes: 0, maxMinutes: 20 }, s40: { minMinutes: 20, maxMinutes: 40 }, s60: { minMinutes: 40, maxMinutes: 60 },
    s90: { minMinutes: 60, maxMinutes: 90 }, s120: { minMinutes: 90, maxMinutes: 120 }, s120p: { minMinutes: 120, maxMinutes: 150 }
};
// Preserve the proven session-complexity expectations from the production generator while letting
// Engine 0.56 own the exercise choices and dosage. The old app treated the lower edge as the promise
// and the upper edge as allowance; M48 accidentally collapsed that contract to an upper cap only.
const SESSION_EXERCISES = {
    s20: { hypertrophy: 2, strength: 2, mixed: 2 }, s40: { hypertrophy: 3, strength: 3, mixed: 3 },
    s60: { hypertrophy: 5, strength: 5, mixed: 5 }, s90: { hypertrophy: 7, strength: 6, mixed: 6 },
    s120: { hypertrophy: 9, strength: 8, mixed: 8 }, s120p: { hypertrophy: 11, strength: 10, mixed: 11 }
};
const SUPPORTED_SPLITS = new Set(['full_body', 'upper_lower', 'ppl', 'ulppl', 'pplul', 'custom', 'hybrid', 'phul', 'phat', 'bro', 'arnold', 'five_three_one', 'five31_beginner', 'strength_fb', 'academy_prep', 'texas', 'gzclp', 'rippler', 'jt', 'glute_focus', 'full_body_patterns', 'torso_limbs', 'ppla', 'ula', 'sbd_power', 'upper_lower_alt']);
const STRENGTH_STRUCTURES = new Set(['five_three_one', 'five31_beginner', 'strength_fb', 'academy_prep', 'texas', 'gzclp', 'rippler', 'jt', 'sbd_power']);
const OHP_STRUCTURES = new Set(['five_three_one', 'five31_beginner', 'gzclp', 'rippler', 'jt']);
const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
const EXPLICIT_EXERCISE_ALIASES = {
    barbell_bench: 'bb-bench', paused_bench: 'pause-bench', incline_smith: 'smith-incline', machine_press: 'machine-press', cable_fly: 'cable-fly',
    chest_supported_row: 'chest-row', barbell_row: 'bb-row', neutral_pulldown: 'neutral-pulldown', rear_delt_fly: 'reverse-pec', cable_lateral_raise: 'cable-lat-raise', band_lateral_raise: 'band-lateral-raise',
    machine_ohp: 'machine-shoulder', preacher_curl: 'preacher', cable_triceps: 'pushdown', overhead_cable_triceps: 'oh-cable-ext', back_squat: 'back-squat',
    hack_squat: 'hack-squat', leg_press: 'leg-press', leg_extension: 'leg-ext', seated_leg_curl: 'seated-curl', hip_thrust: 'hip-thrust', standing_calf: 'standing-calf',
    incline_db_press: 'inc-db-press', one_arm_db_row: 'db-row', db_lateral_raise: 'lat-raise', db_rear_delt_raise: 'rear-fly', db_curl: 'db-curl',
    db_overhead_triceps: 'db-oh-ext', db_split_squat: 'bulgarian', db_rdl: 'db-rdl', db_calf_raise: 'db-calf', dumbbell_crunch: 'db-weighted-crunch', bodyweight_crunch: 'crunch',
    conventional_deadlift: 'deadlift', barbell_ohp: 'ohp'
};
function experienceOf(value) {
    if (value === 'none' || value === 'beginner')
        return 'novice';
    return value === 'advanced' ? 'advanced' : 'intermediate';
}
function goalOf(value) { return value === 'strength' ? 'strength' : value === 'both' ? 'mixed' : 'hypertrophy'; }
function splitOf(value) { return SUPPORTED_SPLITS.has(value) ? value : 'full_body'; }
function muscleId(value) {
    const map = {
        chest: 'chest', back: 'back', lats: 'back', upper_back: 'back', lower_back: 'lower_back', shoulders: 'side_delts', side_delts: 'side_delts', rear_delts: 'rear_delts', front_delts: 'front_delts',
        biceps: 'biceps', triceps: 'triceps', quads: 'quads', hamstrings: 'hamstrings', glutes: 'glutes', calves: 'calves', abs: 'core', core: 'core', traps: 'traps', forearms: 'forearms',
        adductors: 'adductors', abductors: 'abductors', neck: 'neck'
    };
    return map[value];
}
function musclePriorities(config) {
    const out = {};
    const high = (m, p) => { const rank = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 }; if (!out[m] || rank[p] > rank[out[m]])
        out[m] = p; };
    for (const raw of config.reduce ?? []) {
        const m = muscleId(raw);
        if (m)
            out[m] = 'maintenance';
    }
    for (const raw of config.focusList ?? []) {
        const m = muscleId(raw);
        if (m)
            high(m, 'high');
    }
    for (const [raw, value] of Object.entries(config.focus ?? {})) {
        const points = Math.max(0, Math.round(Number(value) || 0));
        if (!points)
            continue;
        const m = muscleId(raw);
        if (m)
            high(m, points >= 2 ? 'specialization' : 'high');
    }
    // The legacy `shoulders` priority represented the whole delt complex. Keep that intent explicit.
    if (config.focus?.shoulders || config.focusList?.includes('shoulders')) {
        high('side_delts', 'specialization');
        high('rear_delts', 'high');
        high('front_delts', 'normal');
    }
    return out;
}
function liftPriorities(config) {
    // Preserve the shell's established strength/powerbuilding intent, then filter those automatic
    // priorities against the completed request below. Full-equipment behavior stays byte-for-behavior
    // compatible while restricted equipment no longer creates impossible automatic lift claims.
    const split = String(config.split || '');
    const goal = goalOf(config.goal);
    const out = {};
    if (STRENGTH_STRUCTURES.has(split) || goal === 'strength') {
        out.bench_press = 'high';
        out.back_squat = 'high';
        out.deadlift = 'high';
        out.overhead_press = OHP_STRUCTURES.has(split) ? 'high' : 'normal';
    }
    else if (goal === 'mixed') {
        out.bench_press = 'normal';
        out.back_squat = 'normal';
        out.deadlift = 'normal';
        out.overhead_press = 'normal';
    }
    return out;
}
function loadingInventoryOf(config) {
    const explicit = config.loadingInventory;
    if (explicit && explicit.barbell && explicit.dumbbells && explicit.machine && explicit.cable && explicit.smith)
        return explicit;
    const metric = String(config.unit || 'lb').toLowerCase() === 'kg';
    if (metric) {
        return {
            unit: 'kg',
            barbell: { barWeight: 20, platePairs: [1.25, 2.5, 5, 10, 15, 20, 25].map(weight => ({ weight, pairs: weight >= 20 ? 10 : 4 })) },
            dumbbells: { availablePerHand: Array.from({ length: 20 }, (_, i) => (i + 1) * 2.5) },
            machine: { minimum: 2.5, increment: 2.5, maximum: 250 },
            cable: { minimum: 2.5, increment: 2.5, maximum: 150 },
            smith: { minimum: 2.5, increment: 2.5, maximum: 250 },
            exerciseOverrides: {}
        };
    }
    return {
        unit: 'lb',
        barbell: { barWeight: 45, platePairs: [2.5, 5, 10, 25, 45].map(weight => ({ weight, pairs: weight >= 45 ? 10 : 4 })) },
        dumbbells: { availablePerHand: Array.from({ length: 20 }, (_, i) => (i + 1) * 5) },
        machine: { minimum: 5, increment: 5, maximum: 500 },
        cable: { minimum: 5, increment: 5, maximum: 300 },
        smith: { minimum: 5, increment: 5, maximum: 500 },
        exerciseOverrides: {}
    };
}
function equipmentOf(config) {
    const set = new Set((config.equipment ?? []).filter(Boolean));
    if (!config.noBodyweight)
        set.add('bodyweight');
    else
        set.delete('bodyweight');
    /* A barbell implies a rack unless the gym says "No squat rack" ("no-rack"). Before this, a rack was always assumed, so a
       home barbell without one was programmed squats and bench from the rack. */
    if (set.has('barbell') && !set.has('no-rack'))
        set.add('rack');
    set.delete('no-rack');
    if (set.has('pullup'))
        set.add('pullup_bar');
    if (set.has('legpress'))
        set.add('leg_press');
    return [...set];
}
function scheduleOf(config) {
    const count = Math.max(1, Math.min(7, Math.round(Number(config.days) || 4)));
    const days = DAY_SETS[count] ?? DAY_SETS[4];
    const session = String(config.session || 's60');
    const band = SESSION_BANDS[session] ?? SESSION_BANDS.s60;
    const goal = goalOf(config.goal);
    const targetExercises = SESSION_EXERCISES[session]?.[goal] ?? SESSION_EXERCISES.s60[goal];
    return { days: days.map(day => ({ day, minMinutes: band.minMinutes, maxMinutes: band.maxMinutes, targetExercises })) };
}
function mapBanned(banned, legacy) {
    const byId = new Map(legacy.map(ex => [ex.id, ex]));
    const out = new Set();
    for (const id of banned) {
        out.add(id);
        const ex = byId.get(id);
        if (!ex)
            continue;
        const nk = norm(ex.name);
        for (const [nextId, def] of EXERCISE_MAP)
            if (norm(def.name) === nk)
                out.add(nextId);
    }
    return [...out];
}
export function shellConfigToNextRequest(config, banned = [], legacyExercises = [], seed) {
    const split = splitOf(config.split);
    const request = {
        athlete: { experience: experienceOf(config.experience) },
        goal: { type: goalOf(config.goal), musclePriorities: musclePriorities(config), liftPriorities: liftPriorities(config) },
        schedule: scheduleOf(config),
        equipment: { available: equipmentOf(config), bodyweight: config.noBodyweight ? 'exclude' : 'allow', loading: loadingInventoryOf(config) },
        restrictions: {
            maxBarbellMovementsPerDay: Number.isFinite(config.barbellCap) ? Math.max(0, Math.round(config.barbellCap)) : 3,
            allowSupersets: !config.noSupersets
        },
        preferences: {
            preferredSplit: split, lockedSplit: split, avoidedExercises: mapBanned(banned, legacyExercises),
            volumeApproach: config.volumeApproach === 'minimalist' ? 'minimalist' : 'standard',
            // Persist the user's global method choice in the immutable request snapshot so later
            // blocks cannot silently fall back to Auto after honoring the choice at creation.
            progressionStyle: config.progressionStyle ?? 'auto'
        },
        seed: seed ?? Math.max(1, Math.floor(Date.now() % 2147483647))
    };
    // Named strength structures are literal lift contracts; impossible equipment must fail closed.
    // Generic strength/mixed shell defaults remain equipment-aware so they do not invent impossible lifts.
    if (!STRENGTH_STRUCTURES.has(split))
        request.goal.liftPriorities = filterFeasibleLiftPriorities(request, request.goal.liftPriorities ?? {});
    return request;
}
/** Which of a named strength structure's contract lifts can this gym NOT perform? Empty for every non-contract split.
 * The wizard used to let a lifter pick 5/3/1 with a dumbbell-only gym and then fail with "could not be built safely
 * (NEXT_ENGINE_REJECTED)". Measured across three restricted gyms: every one of the 27 dumbbell/kettlebell refusals, and
 * 9 of the 15 bodyweight ones, were exactly this — a contract split whose lifts need a barbell. Refusing is right (the
 * contract is the point of those programs); discovering it AFTER the choice is not. This asks the engine's OWN
 * feasibility filter — the one it deliberately skips for contract splits, which is why they fail later — so the
 * wizard can say so up front without running a generation and without keeping a second list. */
export function splitContractGaps(config, legacyExercises = []) {
    const split = splitOf(config.split);
    if (!STRENGTH_STRUCTURES.has(split))
        return [];
    const request = shellConfigToNextRequest(config, [], legacyExercises, 1);
    const wanted = request.goal.liftPriorities ?? {};
    const feasible = filterFeasibleLiftPriorities(request, wanted);
    return Object.keys(wanted).filter(k => !(k in feasible));
}
/** Next-owned recommendation boundary for the program builder. The UI must never rank splits with
 * a stale parallel scorer: build the same request contract, unlock split selection, and let the
 * current allocator/topology engine choose among its automatic candidate families. */
export function recommendNextSplitForShell(config, banned = [], legacyExercises = [], seed = 63063) {
    try {
        const request = shellConfigToNextRequest(config, banned, legacyExercises, seed);
        request.preferences = { ...request.preferences };
        delete request.preferences.lockedSplit;
        delete request.preferences.preferredSplit;
        return generateProgram(request).program.split.family;
    }
    catch {
        return null;
    }
}
function techniqueCue(type, note) {
    if (!type)
        return null;
    if (type === 'myo_reps')
        return note || 'Last set: myo-reps — activation set, then short-rest mini-sets';
    if (type === 'drop_set')
        return note || 'Last set: drop set — reduce load and continue';
    return note || 'Last set: lengthened partials — extra reps in the stretched position';
}
function primaryPart(def) {
    const legacy = def?.legacyPart;
    if (legacy)
        return legacy;
    const entries = Object.entries(def?.muscles ?? {});
    const top = entries.sort((a, b) => (b[1]?.credit ?? 0) - (a[1]?.credit ?? 0))[0]?.[0];
    const map = { back: 'lats', side_delts: 'shoulders', rear_delts: 'shoulders', front_delts: 'shoulders', core: 'abs' };
    return map[top] ?? top;
}
let shellEquipmentExpander = null;
export function setShellEquipmentExpander(fn) { shellEquipmentExpander = fn; }
function performableFor(config) {
    if (!shellEquipmentExpander || !Array.isArray(config?.equipment))
        return () => true;
    const have = new Set(shellEquipmentExpander(config.equipment));
    const banned = new Set(Array.isArray(config?.banned) ? config.banned : []);
    return (ex) => {
        const equip = Array.isArray(ex?.equip) ? ex.equip : [];
        if (banned.has(ex?.id))
            return false;
        if (!equip.length)
            return !config?.noBodyweight;
        return equip.every(q => have.has(q));
    };
}
export function resolveLegacyExercise(nextExercise, legacy, canPerform = () => true) {
    const primary = resolveLegacyExerciseByIdentity(nextExercise, legacy);
    if (!primary || canPerform(primary))
        return primary;
    /* The identity match needs equipment this gym lacks. Take a v661 sibling that trains the same thing — same part,
       movement pattern and compound/isolation type, same region first — that the gym CAN do. None: undefined, and the
       caller fails the build cleanly rather than prescribe an exercise the lifter cannot perform. */
    const p = primary;
    const sibs = legacy.filter((ex) => ex.id !== p.id && ex.part === p.part && ex.pattern === p.pattern && ex.type === p.type && canPerform(ex));
    /* Ranking, measured against real substitutions: region-first alone swapped a dumbbell gym's Incline Dumbbell Press for
       Incline Push-Ups and its Chest-Supported Row for a Doorway Row. A lifter with dumbbells should get dumbbells. So:
       1) shares equipment with what the ENGINE picked, 2) loaded over bodyweight, 3) same region, 4) catalog priority. */
    const def = EXERCISE_MAP.get(nextExercise.exerciseId);
    const intended = new Set(Array.isArray(def?.equipment) ? def.equipment : []);
    const shares = (ex) => Number((ex.equip || []).some((q) => intended.has(q)));
    const loaded = (ex) => Number((ex.equip || []).length > 0);
    const rank = (a, b) => (shares(b) - shares(a)) || (loaded(b) - loaded(a)) || (Number(b.region === p.region) - Number(a.region === p.region)) || ((b.pri || 0) - (a.pri || 0));
    sibs.sort(rank);
    /* Widen one step when the same-PATTERN twins all drop the engine's equipment: a dumbbell gym's Incline Dumbbell Press
       otherwise became Incline Push-Ups, because the flat dumbbell press is a different pattern. Same muscle and type with
       the intended equipment beats the same pattern with none. */
    if (intended.size && !(sibs[0] && shares(sibs[0]))) {
        const wider = legacy.filter((ex) => ex.id !== p.id && ex.part === p.part && ex.type === p.type && canPerform(ex) && shares(ex)).sort(rank);
        if (wider[0])
            return wider[0];
    }
    return sibs[0];
}
function resolveLegacyExerciseByIdentity(nextExercise, legacy) {
    const byId = new Map(legacy.map(ex => [ex.id, ex]));
    if (byId.has(nextExercise.exerciseId))
        return byId.get(nextExercise.exerciseId);
    const alias = EXPLICIT_EXERCISE_ALIASES[nextExercise.exerciseId];
    if (alias && byId.has(alias))
        return byId.get(alias);
    const nameKey = norm(nextExercise.name);
    let candidates = legacy.filter(ex => norm(ex.name) === nameKey);
    if (candidates.length === 1)
        return candidates[0];
    const def = EXERCISE_MAP.get(nextExercise.exerciseId);
    if (def) {
        const wanted = primaryPart(def);
        if (candidates.length > 1 && wanted)
            candidates = candidates.filter(ex => ex.part === wanted);
        if (candidates.length === 1)
            return candidates[0];
    }
    return undefined;
}
export function nextExerciseIdForShellExercise(exercise) {
    if (!exercise)
        return null;
    if (EXERCISE_MAP.has(exercise.id))
        return exercise.id;
    for (const [nextId, shellId] of Object.entries(EXPLICIT_EXERCISE_ALIASES))
        if (shellId === exercise.id && EXERCISE_MAP.has(nextId))
            return nextId;
    const key = norm(exercise.name);
    const match = [...EXERCISE_MAP.values()].find(def => norm(def.name) === key);
    return match?.id ?? null;
}
function range(pair) { return pair[0] === pair[1] ? String(pair[0]) : `${pair[0]}-${pair[1]}`; }
function shellRange(value) {
    if (!Array.isArray(value))
        return value;
    if (!value.length)
        return null;
    const pair = value.length > 1 ? [value[0], value[1]] : [value[0], value[0]];
    const nums = pair.map(Number);
    return nums.every(Number.isFinite) ? range(nums) : String(value[0]);
}
/** Canonicalize every shell-facing working-set count to one scalar.
 * Persisted/imported programs can contain array-shaped values (for example [3,3]); React
 * renders those as repeated digits and numeric consumers turn them into NaN. That made the
 * same corrupted prescription look multiplied on Home, Program, Plan and Workout.
 *
 * Equal arrays collapse to their shared value. A disagreeing/malformed array uses the
 * engine-authored fallback when available; otherwise the first valid value is safer than
 * concatenating or summing it. Set counts are deliberately bounded to the shell's editable
 * 1–20 working-set range so corrupt state cannot create a dose explosion. */
export function canonicalShellSetCount(value, fallback = null) {
    const scalar = (raw) => {
        const n = Number(raw);
        if (!Number.isFinite(n) || n <= 0)
            return null;
        return Math.max(1, Math.min(20, Math.round(n)));
    };
    const fallbackScalar = (() => {
        if (Array.isArray(fallback)) {
            const vals = fallback.map(scalar).filter(v => v != null);
            return vals.length ? vals[0] : null;
        }
        if (fallback && typeof fallback === 'object' && 'sets' in fallback)
            return scalar(fallback.sets);
        return scalar(fallback);
    })();
    if (Array.isArray(value)) {
        const vals = value.map(scalar).filter(v => v != null);
        if (!vals.length)
            return fallbackScalar;
        if (vals.every(v => v === vals[0]))
            return vals[0];
        return fallbackScalar ?? vals[0];
    }
    if (value && typeof value === 'object' && 'sets' in value)
        return canonicalShellSetCount(value.sets, fallbackScalar);
    return scalar(value) ?? fallbackScalar;
}
function immutableEngineSetCount(program, day, slotIndex, weekIndex) {
    try {
        const engineProgram = program?.nextEngine?.program;
        if (!engineProgram || !Array.isArray(engineProgram.sessions))
            return null;
        const dayIndex = (program?.days ?? []).findIndex(d => d?.id === day?.id);
        const session = engineProgram.sessions[dayIndex];
        if (!session)
            return null;
        const workWeeks = Math.max(1, Math.round(Number(program?.weeks ?? program?.config?.weeks) || 4));
        const deload = !!program?.config?.deload && Number(weekIndex) === workWeeks + 1;
        const phase = deload ? 'recovery' : (engineProgram.phase ?? program?.nextEngine?.phase ?? 'hypertrophy');
        const week = deload ? 1 : Math.max(1, Math.min(workWeeks, Math.round(Number(weekIndex) || 1)));
        const totalWeeks = deload ? 1 : workWeeks;
        const exercise = prescriptionForSimulationWeek(session, phase, week, totalWeeks)?.exercises?.[slotIndex];
        return canonicalShellSetCount(exercise?.sets);
    }
    catch {
        return null;
    }
}
function legacyTypeForIntent(intent) { return intent || 'generated'; }
export function getNextShellCell(program, day, slotIndex, weekIndex) {
    if (program?.engineSource !== 'pursuit-next')
        return null;
    const key = `${day?.id ?? ''}:${slotIndex}`;
    const cell = program?.nextWeekPrescriptions?.[key]?.[weekIndex];
    if (!cell)
        return null;
    // Engine-owned week snapshots remain canonical, but explicit user edits in the legacy shell must
    // still win. Bridge-created overrides intentionally contain metadata only in auto mode, so this
    // overlay cannot accidentally freeze week-1 sets/reps across the entire block.
    const o = program?.overrides?.[key] ?? {};
    /* One canonical scalar is returned to every consumer. If persisted shell state has an
       impossible/ambiguous set shape, replay the immutable engine snapshot rather than let
       Home/Program/Plan/Workout each interpret it differently. */
    const engineSets = immutableEngineSetCount(program, day, slotIndex, weekIndex);
    const sets = canonicalShellSetCount(o.sets ?? cell.sets, engineSets ?? cell.sets);
    const reps = shellRange(o.reps ?? cell.reps);
    const rir = shellRange(o.rir ?? cell.rir);
    const rest = o.rest ?? cell.rest;
    const tech = o.techOverride ?? cell.tech ?? null;
    const role = o.role ?? cell.role;
    const progressionStyle = o.progressionStyle ?? cell.progressionStyle ?? 'auto';
    return {
        sets, reps, note: `Pursuit Engine ${program.engineSourceVersion || 'Next'}`,
        range: reps, rir, rest, tech, role, progressionStyle, nextEngine: true
    };
}
export function cloneNextDayPrescriptions(store, fromDayId, toDayId) {
    if (!store)
        return store;
    const out = { ...store };
    for (const [key, value] of Object.entries(store)) {
        const [dayId, slot] = key.split(':');
        if (dayId === fromDayId)
            out[`${toDayId}:${slot}`] = JSON.parse(JSON.stringify(value));
    }
    return out;
}
export function swapNextSlotPrescriptions(store, dayId, aSlot, bSlot) {
    if (!store)
        return store;
    const out = { ...store };
    const a = `${dayId}:${aSlot}`, b = `${dayId}:${bSlot}`;
    const av = out[a], bv = out[b];
    if (av !== undefined)
        out[b] = av;
    else
        delete out[b];
    if (bv !== undefined)
        out[a] = bv;
    else
        delete out[a];
    return out;
}
export function removeNextSlotPrescription(store, dayId, slot) {
    if (!store)
        return store;
    const out = {};
    for (const [key, value] of Object.entries(store)) {
        const [d, sRaw] = key.split(':');
        const s = Number(sRaw);
        if (d !== dayId) {
            out[key] = value;
            continue;
        }
        if (s === slot)
            continue;
        out[`${d}:${s > slot ? s - 1 : s}`] = value;
    }
    return out;
}
export class NextShellAdapterError extends Error {
    constructor(code, message, recovery) { super(message); this.name = 'NextShellAdapterError'; this.code = code; this.recovery = recovery; }
}
/* PERCENT-SCHEME LIFTS PROGRESS BY e1RM, EVERY WEEK (v661: "percent-scheme program — load is matched to %TM, not to a rep
   range"). Main-Lift Waves otherwise inherited the generic block schedule (double, then e1RM). Narrowed deliberately to the
   lifts the scheme governs — the strength-role lifts; v661 applied it to every exercise, but accessories are not loaded
   from the training max, so they keep the table (dynamic / double). */
function schemeStyle(config, role, style) {
    if (config?.percentScheme && (role === 'primary_strength' || role === 'secondary_strength'))
        return 'e1rm';
    return style;
}
function progressionPlanItem(config, exercise) {
    const style = schemeStyle(config, exercise.role, exercise.progressionStyle ?? 'auto');
    const rawPrevious = exercise.progressionSelection?.previousStyle ?? null;
    const previousStyle = rawPrevious ? schemeStyle(config, exercise.role, rawPrevious) : null;
    return {
        exerciseId: exercise.exerciseId, exerciseName: exercise.name, role: exercise.role, style,
        source: exercise.progressionSelection?.source ?? 'auto',
        confidence: exercise.progressionSelection?.confidence ?? 'moderate',
        reason: exercise.progressionSelection?.reason ?? 'Auto selected a progression that matches this exercise and block.',
        // Carry the comparison result through the shell snapshot. Recompute only the display-level
        // percent-scheme normalization; the actual transition decision remains engine-owned.
        previousStyle,
        changed: previousStyle !== null ? previousStyle !== style : false
    };
}
/* ⚠ THE EXERCISE THE LIFTER IS SHOWN DECIDES HOW ITS LOAD IS COUNTED. Several engine exercises have more than one setup —
   Chest-Supported Row is dumbbells + bench OR a machine; Preacher Curl a machine OR dumbbells + bench — and the engine loads the
   FIRST setup the gym can do. The app shows the v661 exercise the engine exercise maps to, which can be the other setup: a full gym
   saw the MACHINE Chest-Supported Row while every suggestion was computed per hand for dumbbells, capped at the heaviest dumbbell
   ("no heavier selectable load exists" at 130 lb; a re-prescription to 100). Record the displayed setup's mode when it is one of
   the engine exercise's own setups; sourceSnapshot applies it (a lifter's own override still wins). */
function displayedLoadingMode(engineId, legacy) {
    const def = EXERCISE_MAP.get(engineId);
    if (!def)
        return null;
    const eq = new Set((legacy.equip || []));
    const shown = eq.has('barbell') || eq.has('ezbar') || eq.has('trapbar') ? 'barbell' : eq.has('dumbbell') ? 'dumbbell' : eq.has('smith') ? 'smith' : eq.has('cable') ? 'cable'
        : [...eq].some(x => /^machine|legpress|hacksquat|pecdeck|legcurl|legext|calfmachine|stack/.test(x)) ? 'machine' : null;
    if (!shown)
        return null;
    const setups = [def.equipment, ...(def.equipmentAlternatives ?? [])];
    const has = (k) => setups.some(st => st.some(x => k === 'machine' ? (x === 'machine' || x === 'leg_press') : x === k));
    if (!has(shown))
        return null; // the displayed implement is not one of this exercise's setups: leave the engine's mode alone
    return shown === 'barbell' ? 'barbell_total' : shown === 'dumbbell' ? 'dumbbell_per_hand' : shown === 'smith' ? 'smith_total' : shown === 'cable' ? 'cable_stack' : 'machine_stack';
}
export function nextProgramToShellProgram(nextProgram, config, legacyExercises, makeId = () => `next-${Math.random().toString(36).slice(2, 10)}`) {
    const canPerform = performableFor(config);
    if (nextProgram?.audit?.result !== 'pass')
        throw new NextShellAdapterError('NEXT_ENGINE_NOT_PASS', `Pursuit Engine ${nextProgram?.engineVersion ?? 'unknown'} returned ${nextProgram?.audit?.result ?? 'no audit'}.`);
    const overrides = {};
    const displayLoadingModes = {};
    const progStyle = {};
    const ss = {};
    const nextWeekPrescriptions = {};
    const totalWeeks = Math.max(1, Math.round(Number(config.weeks) || 4));
    const days = nextProgram.sessions.map((session, dayIndex) => {
        const id = `next-${dayIndex + 1}-${session.day}`;
        const mapped = session.exercises.map((exercise, slot) => {
            const legacy = resolveLegacyExercise(exercise, legacyExercises, canPerform);
            if (!legacy)
                throw new NextShellAdapterError('UNMAPPED_EXERCISE', `Could not map ${exercise.name} (${exercise.exerciseId}) into the v661 exercise catalog.`);
            const shownMode = displayedLoadingMode(exercise.exerciseId, legacy);
            if (shownMode && !(exercise.exerciseId in displayLoadingModes))
                displayLoadingModes[exercise.exerciseId] = shownMode;
            const key = `${id}:${slot}`;
            overrides[key] = {
                nextEngine: true, nextExerciseId: exercise.exerciseId, legacyExerciseId: legacy.id, role: exercise.role,
                progressionStyle: schemeStyle(config, exercise.role, exercise.progressionStyle ?? 'auto'),
                progressionSelection: exercise.progressionSelection ? { ...exercise.progressionSelection } : undefined
            };
            // Manual mode is an explicit request to own the prescription in the shell. Seed the editable
            // values from week 1; auto mode stores metadata only so later weeks continue to come from 0.41.
            if (config.progression === 'manual')
                Object.assign(overrides[key], {
                    sets: canonicalShellSetCount(exercise.sets), reps: range(exercise.prescription.reps), rir: range(exercise.prescription.rir), rest: exercise.prescription.restSeconds
                });
            nextWeekPrescriptions[key] = {};
            for (let week = 1; week <= totalWeeks; week++) {
                const weekly = prescriptionForSimulationWeek(session, nextProgram.phase, week, totalWeeks).exercises[slot];
                nextWeekPrescriptions[key][week] = { sets: canonicalShellSetCount(weekly.sets), reps: range(weekly.prescription.reps), rir: range(weekly.prescription.rir), rest: weekly.prescription.restSeconds,
                    role: weekly.role, progressionStyle: schemeStyle(config, weekly.role, weekly.progressionStyle ?? 'auto'), tech: techniqueCue(weekly.advancedTechnique?.type, weekly.advancedTechnique?.note) };
            }
            // The shell treats a requested deload as a real executable week after the configured work weeks.
            // M60 omitted this cell entirely, causing every deload slot to become an engine-missing/bad-sets
            // failure. Materialize an engine-owned recovery prescription and suppress intensity techniques.
            if (config.deload) {
                const deload = prescriptionForSimulationWeek(session, 'recovery', 1, 1).exercises[slot];
                nextWeekPrescriptions[key][totalWeeks + 1] = { sets: canonicalShellSetCount(deload.sets), reps: range(deload.prescription.reps), rir: range(deload.prescription.rir), rest: deload.prescription.restSeconds,
                    role: deload.role, progressionStyle: schemeStyle(config, deload.role, deload.progressionStyle ?? 'auto'), tech: null };
            }
            /* Engine styles are NOT written into progStyle: that map is the lifter's explicit choices, and an entry there makes the
               shell skip auto — which hid the per-week block schedule and every v661 adaptive rule. The shell reads the engine's
               style per slot and week from nextWeekPrescriptions instead (07-progression nextEngineStyleFor). */
            return legacy.id;
        });
        // v661 links adjacent exercise slots: true at slot N means N and N+1 are a group. New-engine
        // supersets are also adjacent after realization, so preserve only verified adjacent group mates.
        for (let slot = 0; slot < session.exercises.length - 1; slot++) {
            const a = session.exercises[slot]?.supersetGroup, b = session.exercises[slot + 1]?.supersetGroup;
            if (a && a === b)
                ss[`${id}:${slot}`] = true;
        }
        const primaryIndex = Math.max(0, session.exercises.findIndex((x) => x.role === 'primary_strength'));
        return { id, label: session.name, type: legacyTypeForIntent(session.intent), focus: session.intent, exercises: mapped, primaryIndex };
    });
    const weekPlan = Array(7).fill(null);
    const schedule = {};
    nextProgram.sessions.forEach((session, index) => { const js = DAY_TO_JS[session.day]; if (Number.isInteger(js)) {
        weekPlan[js] = index;
        schedule[js] = days[index].id;
    } });
    return {
        id: makeId(), name: config.name || nextProgram.split?.displayName || 'Pursuit Program', createdAt: Date.now(), seed: nextProgram.seed,
        // Keep v661 engineV for legacy shell feature gates. Provenance has its own explicit version fields.
        engineV: 33, engineSource: 'pursuit-next', engineSourceVersion: nextProgram.engineVersion,
        config: { ...config }, weeks: totalWeeks, days, overrides, progStyle, ss, nextWeekPrescriptions, weekPlan, schedule, scheduleBase: { ...schedule },
        nextEngine: {
            displayLoadingModes, version: nextProgram.engineVersion, phase: nextProgram.phase, split: nextProgram.split, audit: nextProgram.audit, rationale: nextProgram.rationale, explainability: nextProgram.explainability, sourceProgramId: nextProgram.id,
            progressionPlan: nextProgram.sessions.flatMap(session => session.exercises.map(exercise => progressionPlanItem(config, exercise)))
        }
    };
}
export function generateNextProgramForShell(options) {
    const request = shellConfigToNextRequest(options.config, options.banned ?? [], options.legacyExercises, options.seed);
    const result = generateProgram(request, {
        // The shell's configured work weeks are the block length the athlete will actually run.
        // Feed that into Auto instead of letting progression selection assume a generic six-week block.
        blockWeeks: Math.max(1, Math.round(Number(options.config?.weeks) || 4)),
        progressionStyle: options.config?.progressionStyle
    });
    if (result.program.audit.result !== 'pass') {
        let recovery;
        try {
            recovery = buildGenerationRecoveryPlan(result.program.audit, request);
        }
        catch {
            recovery = undefined;
        }
        throw new NextShellAdapterError('NEXT_ENGINE_REJECTED', `Pursuit Engine ${result.program.engineVersion} could not safely satisfy this request.`, recovery);
    }
    const legacyProgram = nextProgramToShellProgram(result.program, options.config, options.legacyExercises, options.makeId);
    // M41: preserve the immutable engine-side request/program snapshot inside the legacy program.
    // v661 history remains the canonical performed-work store; these snapshots only provide the
    // interpretation contract needed to replay that history through the Next engine later.
    legacyProgram.nextEngine = {
        ...legacyProgram.nextEngine,
        request: JSON.parse(JSON.stringify(request)),
        baseRequest: JSON.parse(JSON.stringify(request)),
        program: JSON.parse(JSON.stringify(result.program)),
        cycleState: JSON.parse(JSON.stringify(createInitialCycleState(request.goal.type, request.schedule.days.length))),
        historySchemaVersion: 1
    };
    return { program: legacyProgram, nextProgram: result.program, request, diagnostics: result.diagnostics };
}
/* Plain-language reasons from engine findings. Unknown codes are skipped rather than shown raw ("fractional sets versus a
   minimum region" is engine language, not lifter language). */
function refusalItems(p) {
    const dayName = new Map((p.sessions ?? []).map((s) => [s.id, String(s.label ?? s.name ?? s.id)]));
    const items = [];
    for (const f of (p.audit?.findings ?? [])) {
        if (f.severity === 'warning')
            continue;
        const msg = String(f.message ?? '');
        if (f.code === 'MUSCLE_UNDER_MIN')
            items.push(msg.split(' ')[0].replace(/_/g, ' '));
        else if (f.code === 'SESSION_CAPACITY_MISS')
            items.push(`the ${dayName.get(f.sessionId) ?? 'a'} day`);
        else if (f.code === 'FULL_BODY_INCOMPLETE') {
            const m = msg.match(/missing (.+?) work/);
            items.push(m ? `${m[1]} work on full-body days` : 'full-body coverage');
        }
        else if (f.code === 'LIFT_EXPOSURE_MISSING') {
            const m = msg.match(/^(.+?) is high priority/);
            if (m)
                items.push(m[1]);
        }
    }
    return [...new Set(items)];
}
function shellRefusal(config, legacyExercises) {
    try {
        generateNextProgramForShell({ config, banned: [], legacyExercises, seed: 1 });
        return null;
    }
    catch (err) {
        return err instanceof NextShellAdapterError ? err : null;
    }
}
/** The engine's own recovery suggestions carried by a refusal (buildGenerationRecoveryPlan), best first. The engine knows
 *  what would fix a refusal — more time, another day, a lower lift priority — and the app used to show only the code. */
export function refusalFixes(err) {
    const plan = err?.recovery;
    const list = Array.isArray(plan?.suggestions) ? plan.suggestions : [];
    return list.filter(x => x && x.code !== 'report_issue' && typeof x.title === 'string').map(x => String(x.title)).slice(0, 2);
}
export function splitBuildability(config, legacyExercises = []) {
    const gaps = splitContractGaps(config, legacyExercises);
    if (gaps.length)
        return { ok: false, kind: 'lifts', items: gaps.map(g => g.replace(/_/g, ' ')) };
    const refusal = shellRefusal(config, legacyExercises);
    if (!refusal)
        return { ok: true };
    const p = generateProgram(shellConfigToNextRequest(config, [], legacyExercises, 1)).program;
    /* ⚠ DON'T BLAME THE GYM FOR THE ENGINE. With a FULL gym, three offered combinations are still refused (goal "both": Bro
       5 days leaves calves at 1 set, Glute Focus 3 days leaves chest at 5 of 6, Full Body Patterns 2 days misses upper push).
       Telling that lifter "not enough exercises with your equipment" would send them shopping for nothing. So re-ask with
       every piece of equipment the catalog knows: still refused means the setting, not the gym, is the problem. */
    const everything = [...new Set(legacyExercises.flatMap((e) => Array.isArray(e?.equip) ? e.equip : []))];
    const equipmentIsTheCause = !shellRefusal({ ...config, equipment: everything, noBodyweight: false }, legacyExercises);
    return { ok: false, kind: equipmentIsTheCause ? 'coverage' : 'design', items: refusalItems(p), fixes: refusalFixes(refusal) };
}
