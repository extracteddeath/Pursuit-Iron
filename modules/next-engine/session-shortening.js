import { computeCell, exerciseAt, EX_BY_ID, effectiveRest, goalForDay, warmupCount, WARMUP_SET_SEC, percentagePlanFor } from '../training-domain.js';
import { techniqueProtocolFromCell } from './workout-runtime.js';
import { EXERCISE_MAP } from './exercise-db.js';
import { resolveNextShellExerciseId } from './shell-projection.js';

export const SESSION_LENGTH_OPTIONS = Object.freeze([null, 45, 30, 20]);

// One exposure, never a program edit. Original slot indices stay stable for swaps, notes and loads.
// Do not trade rest, warmups, intensity or a percentage protocol for clock time.
export function planShorterSession(program, day, weekIndex, minutes, options = {}) {
    if (!SESSION_LENGTH_OPTIONS.includes(minutes)) throw new RangeError('Choose Full, 45, 30 or 20 minutes.');
    const sourceDay = { ...day }; delete sourceDay.sessionLengthPlan;
    const rows = (day.exercises || []).map((_, slot) => {
        const id = exerciseAt(program, sourceDay, slot, weekIndex), ex = EX_BY_ID[id];
        if (!ex) return null;
        const cell = computeCell(program, sourceDay, id, slot, weekIndex);
        if (cell.missing || !(cell.sets > 0)) return null;
        const protocol = percentagePlanFor(program, sourceDay, ex, slot, weekIndex, program.config?.unit || 'lb', options.history || []);
        const sets = protocol?.sets.length || Math.round(cell.sets);
        const key = slot === day.primaryIndex || cell.role === 'primary_strength';
        const locked = !!protocol || !!cell.setTargets?.length || ['primary_strength', 'secondary_strength'].includes(cell.role);
        const focus = program.config?.focus?.[ex.part];
        const sides = EXERCISE_MAP.get(resolveNextShellExerciseId(program, sourceDay, slot, ex))?.flags?.unilateral ? 2 : 1;
        return { slot, id, name: ex.name, part: ex.part, compound: ex.type === 'compound', key, locked,
            sets, originalSets: sets, sides, tech: cell.tech || null, originalTech: cell.tech || null,
            // Higher priority survives longer. Later duplicates are cut before distinct muscle work.
            priority: (key ? 1000 : locked ? 900 : ex.type === 'compound' ? 300 : 100) + (focus && focus !== 'normal' ? 50 : 0) - slot,
            reps: Math.max(1, ...(String(cell.reps).match(/\d+/g) || ['12']).map(Number)),
            rest: Math.max(Number(cell.rest) || 0, effectiveRest(goalForDay(program, day), ex, key,
                program.overrides?.[`${day.id}:${slot}`]?.rest, options.restScale || 1)),
            warmSeconds: warmupCount(ex, key) * WARMUP_SET_SEC };
    }).filter(Boolean);
    // Keep one complementary compound alongside the key lift where possible (e.g. full-body).
    const anchor = rows.find(r => r.key) || rows[0];
    if (anchor) {
        anchor.key = true; anchor.priority = Math.max(1000, anchor.priority);
        anchor.warmSeconds = Math.max(anchor.warmSeconds, warmupCount(EX_BY_ID[anchor.id], true) * WARMUP_SET_SEC);
    }
    const complement = rows.find(r => r.compound && r !== anchor && r.part !== anchor?.part);
    const floor = r => r.locked ? r.originalSets : Math.min(2, r.originalSets);
    const protectedRow = r => r.key || r.locked || r === complement;
    const pace = Math.max(1, Math.min(1.6, Number(options.paceFactor) || 1));
    const extraSeconds = r => {
        const protocol = techniqueProtocolFromCell({ tech: r.tech });
        if (protocol.type === 'myo_reps') return r.sides * protocol.miniSets.maximum * (protocol.miniSets.targetReps * 4 + protocol.miniSets.restSeconds);
        if (protocol.type === 'drop_set') return r.sides * protocol.drops.reduce((n, d) => n + Math.max(12, r.reps) * 4 + d.restSeconds, 0);
        return protocol.type ? 45 * r.sides : 0;
    };
    const seconds = () => rows.some(r => r.sets > 0) ? Math.ceil((5 * 60 + rows.filter(r => r.sets > 0).reduce((n, r) =>
        n + r.warmSeconds + (r.compound ? 105 : 60) + r.sets * (Math.max(45, r.reps * 4) * r.sides + r.rest) + extraSeconds(r), 0)) * pace) : 0;
    const originalSeconds = seconds(), budget = minutes == null ? Infinity : minutes * 60;
    // Amplifiers are optional fatigue first. Keep intact protocols; never truncate mini-set rests.
    const ordered = [...rows].sort((a, b) => a.priority - b.priority);
    for (const row of ordered) if (seconds() > budget && !row.locked) row.tech = null;
    // Remove extra accessory sets before supporting compounds, then extra key hypertrophy sets.
    for (const row of ordered) while (seconds() > budget && row.sets > floor(row)) row.sets--;
    // Drop entire optional movements, keeping useful two-set exposures instead of one-set fragments.
    const omitOrder = [...ordered].sort((a, b) => {
        const duplicate = r => rows.some(other => other !== r && other.part === r.part && other.priority > r.priority);
        return (a.compound ? 100 : 0) - (b.compound ? 100 : 0) + (duplicate(a) ? -50 : 0) - (duplicate(b) ? -50 : 0) || a.priority - b.priority;
    });
    for (const row of omitOrder) if (seconds() > budget && !protectedRow(row)) row.sets = 0;
    const estimatedSeconds = seconds();
    return { schemaVersion: 1, programId: program.id, dayId: day.id, weekIndex, minutes,
        originalMinutes: Math.ceil(originalSeconds / 60), estimatedMinutes: Math.ceil(estimatedSeconds / 60),
        fits: estimatedSeconds <= budget, changed: rows.some(r => r.sets !== r.originalSets || r.tech !== r.originalTech),
        slots: Object.fromEntries(rows.map(r => [r.slot, { exerciseId: r.id, sets: r.sets, tech: r.tech }])),
        cuts: rows.filter(r => r.sets !== r.originalSets || r.tech !== r.originalTech).map(r => ({
            slot: r.slot, name: r.name, fromSets: r.originalSets, toSets: r.sets,
            amplifierRemoved: !!r.originalTech && !r.tech })) };
}
