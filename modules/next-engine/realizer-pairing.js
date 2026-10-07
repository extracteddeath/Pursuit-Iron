// M230 canonical pairing stage. Stage context is local to one realization.
import { primaryMuscle } from './realizer-ranking.js';
import { estimateMinutes } from './realizer-time-budget.js';

export /* M230:PRESERVE:top.assignAccessorySupersets:BEGIN */
function assignAccessorySupersets(session, exerciseMap, allowSupersets = true) {
    const exercises = session.exercises.map(ex => { const clone = { ...ex }; delete clone.supersetGroup; return clone; });
    const rawMinutes = estimateMinutes(exercises);
    if (!allowSupersets)
        return { ...session, exercises, estimatedMinutes: rawMinutes };
    // Normal sessions only use isolation supersets once there are enough movements to justify the
    // transition complexity. In genuinely short sessions, also permit one stable chest/back antagonist
    // compound pairing (e.g. machine press + chest-supported row). This is a deliberate density tool,
    // never a strength-anchor shortcut and never a lower-body compound circuit.
    const constrained = session.maxMinutes <= 35 || rawMinutes > session.maxMinutes;
    if (exercises.length < 5 && !constrained)
        return { ...session, exercises, estimatedMinutes: rawMinutes };
    const eligible = exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }))
        .filter((x) => !!x.def)
        .filter(x => x.exercise.role !== 'primary_strength' && x.exercise.role !== 'secondary_strength')
        .filter(x => {
        if (!x.def.flags.compound)
            return x.def.fatigue.systemic <= 2 && x.def.fatigue.lowerBack <= 1;
        const primary = primaryMuscle(x.def);
        return constrained && (primary === 'chest' || primary === 'back') && x.def.fatigue.systemic <= 3 && x.def.fatigue.axial <= 2 && x.def.fatigue.lowerBack <= 1 && x.def.stability >= 4;
    });
    let group = 1;
    const used = new Set();
    for (const a of eligible) {
        if (used.has(a.index) || group > 2)
            continue;
        const am = primaryMuscle(a.def);
        const b = eligible.find(candidate => {
            if (candidate.index <= a.index || used.has(candidate.index))
                return false;
            const bm = primaryMuscle(candidate.def);
            if (!am || !bm || am === bm)
                return false;
            // Compound/compound supersets are restricted to the stable chest/back antagonist case above.
            if (a.def.flags.compound && candidate.def.flags.compound && !((am === 'chest' && bm === 'back') || (am === 'back' && bm === 'chest')))
                return false;
            // Do not pair two movements that compete for the same joint-local target family.
            if (a.def.movementFamily === candidate.def.movementFamily)
                return false;
            return Math.abs(a.exercise.sets - candidate.exercise.sets) <= 2;
        });
        if (!b)
            continue;
        const id = `SS${group++}`;
        exercises[a.index].supersetGroup = id;
        exercises[b.index].supersetGroup = id;
        used.add(a.index);
        used.add(b.index);
    }
    return { ...session, exercises, estimatedMinutes: estimateMinutes(exercises) };
}
/* M230:PRESERVE:top.assignAccessorySupersets:END */
