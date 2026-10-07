/** Pure shell identities, prescription ownership and weekly projection. No generation or repair imports. */
import { EXERCISE_MAP } from './exercise-db.js';
import { prescriptionForSimulationWeek } from './simulation.js';
export const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
export const EXPLICIT_EXERCISE_ALIASES = {
    barbell_bench: 'bb-bench', paused_bench: 'pause-bench', incline_smith: 'smith-incline', machine_press: 'machine-press', cable_fly: 'cable-fly',
    chest_supported_row: 'chest-row', barbell_row: 'bb-row', neutral_pulldown: 'neutral-pulldown', rear_delt_fly: 'reverse-pec', cable_lateral_raise: 'cable-lat-raise', band_lateral_raise: 'band-lateral-raise',
    machine_ohp: 'machine-shoulder', preacher_curl: 'preacher', cable_triceps: 'pushdown', overhead_cable_triceps: 'oh-cable-ext', back_squat: 'back-squat',
    hack_squat: 'hack-squat', leg_press: 'leg-press', leg_extension: 'leg-ext', seated_leg_curl: 'seated-curl', hip_thrust: 'hip-thrust', standing_calf: 'standing-calf',
    incline_db_press: 'inc-db-press', one_arm_db_row: 'db-row', db_lateral_raise: 'lat-raise', db_rear_delt_raise: 'rear-fly', db_curl: 'db-curl',
    db_overhead_triceps: 'db-oh-ext', db_split_squat: 'bulgarian', db_rdl: 'db-rdl', db_calf_raise: 'db-calf', dumbbell_crunch: 'db-weighted-crunch', bodyweight_crunch: 'crunch',
    conventional_deadlift: 'deadlift', barbell_ohp: 'ohp'
};
export function primaryPart(def) {
    const legacy = def?.legacyPart;
    if (legacy)
        return legacy;
    const entries = Object.entries(def?.muscles ?? {});
    const top = entries.sort((a, b) => (b[1]?.credit ?? 0) - (a[1]?.credit ?? 0))[0]?.[0];
    const map = { back: 'lats', side_delts: 'shoulders', rear_delts: 'shoulders', front_delts: 'shoulders', core: 'abs' };
    return map[top] ?? top;
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
export function resolveLegacyExerciseByIdentity(nextExercise, legacy, byId = new Map(legacy.map(ex => [ex.id, ex])), byName) {
    if (byId.has(nextExercise.exerciseId))
        return byId.get(nextExercise.exerciseId);
    const alias = EXPLICIT_EXERCISE_ALIASES[nextExercise.exerciseId];
    if (alias && byId.has(alias))
        return byId.get(alias);
    const nameKey = norm(nextExercise.name);
    let candidates = byName ? byName.get(nameKey) ?? [] : legacy.filter(ex => norm(ex.name) === nameKey);
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
// The visible roster owns movement identity. A slot's saved engine ID is valid only while its
// shell identity still matches; old cycle propagation could leave this metadata pointing at a
// different lift. Resolve the current catalog identity before trusting unbound legacy metadata.
export function resolveNextShellExerciseId(program, day, slotIndex, legacyExercise) {
    const legacyId = String(day?.exercises?.[slotIndex] ?? '');
    const meta = program?.overrides?.[`${day?.id}:${slotIndex}`];
    if (meta?.nextExerciseId && meta.legacyExerciseId === legacyId)
        return String(meta.nextExerciseId);
    const current = nextExerciseIdForShellExercise(legacyExercise);
    if (current) return current;
    return meta?.nextExerciseId && (!meta.legacyExerciseId || meta.legacyExerciseId === legacyId)
        ? String(meta.nextExerciseId) : null;
}

// Propagating a roster edit into another phase must keep THAT phase's prescriptions attached to
// its surviving movements. Match existing identities first, then reuse vacant slots for genuine
// substitutions. This copies no prescriptions from the edited phase and leaves the base immutable.
export function remapNextShellRoster(program, days, legacyExercises) {
    if (program?.engineSource !== 'pursuit-next') return { ...program, days };
    const legacyMap = new Map(legacyExercises.map(ex => [ex.id, ex]));
    let next = { ...program, days };
    for (const day of days) {
        const prior = program.days.find(d => d.id === day.id);
        if (!prior || day.exercises.length === prior.exercises.length
            && day.exercises.every((id, slot) => id === prior.exercises[slot])) continue;
        const used = new Set();
        const fromSlots = day.exercises.map(id => {
            const slot = prior.exercises.findIndex((old, i) => old === id && !used.has(i));
            if (slot >= 0) used.add(slot);
            return slot;
        });
        fromSlots.forEach((from, slot) => {
            if (from >= 0) return;
            const available = !used.has(slot) && slot < prior.exercises.length ? slot
                : prior.exercises.findIndex((_, i) => !used.has(i));
            fromSlots[slot] = available;
            if (available >= 0) used.add(available);
        });
        for (const field of ['overrides', 'nextWeekPrescriptions', 'progStyle', 'slotBias', 'autoBias', 'rounds', 'weekOff', 'pairs']) {
            const store = next[field];
            if (!store) continue;
            const prefix = `${day.id}:`, out = { ...store };
            const entries = Object.entries(store).filter(([key]) => key.startsWith(prefix)
                && /^\d+(?:$|:)/.test(key.slice(prefix.length)));
            for (const [key] of entries) delete out[key];
            fromSlots.forEach((from, slot) => {
                for (const [key, value] of entries) {
                    const match = key.slice(prefix.length).match(/^(\d+)(.*)$/);
                    if (Number(match[1]) === from) out[`${prefix}${slot}${match[2]}`] = value;
                }
            });
            next[field] = out;
        }
        const overrides = { ...next.overrides };
        day.exercises.forEach((id, slot) => {
            const key = `${day.id}:${slot}`, meta = { ...(overrides[key] ?? {}), nextEngine: true, legacyExerciseId: id };
            const engineId = nextExerciseIdForShellExercise(legacyMap.get(id));
            if (engineId) meta.nextExerciseId = engineId;
            else if (id !== prior.exercises[fromSlots[slot]]) delete meta.nextExerciseId;
            overrides[key] = meta;
        });
        next.overrides = overrides;
    }
    return next;
}
export function range(pair) { return pair[0] === pair[1] ? String(pair[0]) : `${pair[0]}-${pair[1]}`; }
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
export function legacyTypeForIntent(intent) { return intent || 'generated'; }

/* M204 — prescription ownership is explicit at the shell boundary. The executable week cell is the
 * owner for generated plans; overrides may replace individual fields only when that field is
 * explicitly user-owned. Older manual-mode programs and the long-standing rest/tech overrides are
 * recognized for backwards compatibility, but engine metadata living in `overrides` no longer
 * silently freezes sets/reps/RIR/role/progression across later weeks. */
const SHELL_PRESCRIPTION_FIELDS = ['sets', 'reps', 'rir', 'rest', 'tech', 'role', 'progressionStyle'];
const hasOwn = (obj, key) => !!obj && Object.prototype.hasOwnProperty.call(obj, key);
const overrideKeyForField = field => field === 'tech' ? 'techOverride' : field;
export function shellPrescriptionFieldOwner(program, override, field) {
    const explicit = override?.prescriptionOwners?.[field];
    if (explicit === 'user' || explicit === 'engine')
        return explicit;
    const key = overrideKeyForField(field);
    if (field === 'tech' && hasOwn(override, key))
        return 'user';
    // Rest has always been editable from the workout even while the rest of an Engine plan is Auto.
    // No automatic bridge path writes `rest` into an Auto override, so a persisted value is a user fact.
    if (field === 'rest' && hasOwn(override, key))
        return 'user';
    // Legacy manual-mode plans predate prescriptionOwners; their editable shell values remain theirs.
    if (program?.config?.progression === 'manual' && ['sets', 'reps', 'rir'].includes(field) && hasOwn(override, key))
        return 'user';
    return 'engine';
}
export function shellPrescriptionOwners(program, override) {
    return Object.fromEntries(SHELL_PRESCRIPTION_FIELDS.map(field => [field, shellPrescriptionFieldOwner(program, override, field)]));
}
export function markUserPrescriptionOverride(override, field, value) {
    const key = overrideKeyForField(field);
    const owners = { ...(override?.prescriptionOwners ?? {}), [field]: 'user' };
    return { ...(override ?? {}), [key]: value, prescriptionOwners: owners };
}
export function clearUserPrescriptionOverride(override, field) {
    if (!override)
        return override;
    const key = overrideKeyForField(field);
    const next = { ...override };
    delete next[key];
    if (next.prescriptionOwners) {
        const owners = { ...next.prescriptionOwners };
        delete owners[field];
        if (Object.keys(owners).length) next.prescriptionOwners = owners;
        else delete next.prescriptionOwners;
    }
    return next;
}
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
    const ownership = shellPrescriptionOwners(program, o);
    const ownedValue = (field, engineValue) => {
        const key = overrideKeyForField(field);
        return ownership[field] === 'user' && hasOwn(o, key) ? o[key] : engineValue;
    };
    /* One canonical scalar is returned to every consumer. If persisted shell state has an
       impossible/ambiguous set shape, replay the immutable engine snapshot rather than let
       Home/Program/Plan/Workout each interpret it differently. Engine-owned stale override mirrors
       are deliberately ignored; only an explicitly user-owned set count can replace the week cell. */
    const rawSets = ownedValue('sets', cell.sets);
    // Ordinary scalar cells are already executable. Rebuilding an entire simulated session for
    // each read is only necessary when restoring an ambiguous/corrupt persisted count.
    const scalarCount = !Array.isArray(rawSets) && (rawSets === null || typeof rawSets !== 'object')
        ? canonicalShellSetCount(rawSets) : null;
    const sets = scalarCount ?? canonicalShellSetCount(rawSets,
        immutableEngineSetCount(program, day, slotIndex, weekIndex) ?? cell.sets);
    const reps = shellRange(ownedValue('reps', cell.reps));
    const rir = shellRange(ownedValue('rir', cell.rir));
    const rest = ownedValue('rest', cell.rest);
    const techRaw = ownedValue('tech', cell.tech ?? null);
    const tech = techRaw || null;
    const role = ownedValue('role', cell.role);
    const explicitStyle = program?.progStyle?.[key];
    if (explicitStyle && explicitStyle !== 'auto') ownership.progressionStyle = 'user';
    const progressionStyle = explicitStyle && explicitStyle !== 'auto'
        ? explicitStyle : ownedValue('progressionStyle', cell.progressionStyle ?? 'auto');
    return {
        sets, reps, note: `Pursuit Engine ${program.engineSourceVersion || 'Next'}`,
        range: reps, rir, rest, tech, role, progressionStyle, ownership, nextEngine: true
    };
}
// A completed exposure owns its historical target. Later plan edits must not rewrite the question
// that the lifter was asked to complete; this snapshot is history, never a current-plan override.
export function snapshotNextShellPrescription(program, day, slotIndex, legacyExercise, weekIndex) {
    const cell = getNextShellCell(program, day, slotIndex, weekIndex);
    if (!cell || legacyExercise?.id !== day?.exercises?.[slotIndex]) return null;
    const exerciseId = resolveNextShellExerciseId(program, day, slotIndex, legacyExercise);
    if (!exerciseId) return null;
    return { schemaVersion: 1, exerciseId, engineVersion: program.engineSourceVersion,
        ...Object.fromEntries(SHELL_PRESCRIPTION_FIELDS.map(field => [field, cell[field]])) };
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
