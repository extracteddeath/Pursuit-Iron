/** Session realization: sequencing. Maintained production source. */
import { createExerciseMap } from '../exercise-db.js';
import { estimateMinutes } from './time.js';
import { primaryMuscle } from './rules.js';
import { setupTransitionCost } from '../setup-economy.js';

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

function sequenceSessionExercises(session, exerciseMap) {
    const indexed = session.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }));
    const used = new Set();
    const blocks = [];
    const familyClass = (def) => {
        if (!def)
            return 'other';
        if (['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily))
            return 'push';
        if (['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily))
            return 'pull';
        if (['squat', 'leg_press', 'knee_extension'].includes(def.movementFamily))
            return 'knee';
        if (['hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
            return 'hinge';
        return def.movementFamily;
    };
    const makeBlock = (items) => {
        // A mixed compound/accessory density pair always presents the compound first. Pure accessory
        // pairs preserve their original order. Strength work is never supersetted by construction.
        items = [...items].sort((a, b) => {
            const ac = !!a.def?.flags.compound, bc = !!b.def?.flags.compound;
            if (ac !== bc)
                return ac ? -1 : 1;
            return a.index - b.index;
        });
        const lead = items.find(x => x.def?.flags.compound) ?? items[0];
        const defs = items.map(x => x.def).filter((x) => !!x);
        return {
            items, first: Math.min(...items.map(x => x.index)),
            compound: defs.some(def => def.flags.compound),
            strength: items.some(x => x.exercise.role === 'primary_strength' || x.exercise.role === 'secondary_strength' || x.exercise.role === 'strength_support'),
            familyClass: familyClass(lead?.def),
            joint: [
                Math.max(0, ...defs.map(d => d.fatigue.shoulder)), Math.max(0, ...defs.map(d => d.fatigue.elbow)),
                Math.max(0, ...defs.map(d => d.fatigue.knee)), Math.max(0, ...defs.map(d => d.fatigue.grip)),
                Math.max(0, ...defs.map(d => d.fatigue.lowerBack))
            ]
        };
    };
    for (const item of indexed) {
        if (used.has(item.index))
            continue;
        if (item.exercise.supersetGroup) {
            const pair = indexed.filter(x => x.exercise.supersetGroup === item.exercise.supersetGroup);
            pair.forEach(x => used.add(x.index));
            blocks.push(makeBlock(pair));
        }
        else {
            used.add(item.index);
            blocks.push(makeBlock([item]));
        }
    }
    const strength = blocks.filter(b => b.strength).sort((a, b) => a.first - b.first);
    const compoundPool = blocks.filter(b => !b.strength && b.compound).sort((a, b) => a.first - b.first);
    const accessories = blocks.filter(b => !b.strength && !b.compound).sort((a, b) => a.first - b.first);
    // Mixed density pairs (compound + non-interfering accessory) can be useful under a real time cap,
    // but the accessory must not pre-fatigue another compound that is still waiting. Build explicit
    // dependencies between compound blocks: if block A contains an accessory for a muscle materially
    // used by compound block B, B has to happen first. This preserves the superset while protecting
    // later performance.
    const preFatigues = (a, b) => {
        const accessoryPrimaries = a.items.filter(x => x.def && !x.def.flags.compound).map(x => Object.entries(x.def.muscles).find(([, c]) => c.role === 'primary')?.[0]).filter(Boolean);
        if (!accessoryPrimaries.length)
            return false;
        return b.items.some(x => x.def?.flags.compound && accessoryPrimaries.some(m => (x.def.muscles[m]?.credit ?? 0) >= .5));
    };
    let compounds = [];
    const remaining = [...compoundPool];
    while (remaining.length) {
        const available = remaining.filter(candidate => !remaining.some(other => other !== candidate && preFatigues(candidate, other)));
        const pool = available.length ? available : remaining; // deterministic fail-safe for an unlikely dependency cycle
        let chosen = pool[0];
        if ((session.intent === 'full' || session.intent === 'strength_full') && compounds.length && pool.length > 1) {
            const prev = compounds[compounds.length - 1];
            chosen = [...pool].sort((a, b) => {
                const score = (cand) => {
                    const classPenalty = cand.familyClass === prev.familyClass ? 5 : 0;
                    const jointOverlap = cand.joint.reduce((sum, v, j) => sum + Math.min(v, prev.joint[j]), 0);
                    return classPenalty + jointOverlap * .35 + cand.first * .01;
                };
                return score(a) - score(b) || a.first - b.first;
            })[0];
        }
        else
            chosen = [...pool].sort((a, b) => a.first - b.first)[0];
        compounds.push(chosen);
        remaining.splice(remaining.indexOf(chosen), 1);
    }
    const exercises = [...strength, ...compounds, ...accessories].flatMap(block => block.items.map(item => item.exercise));
    return { ...session, exercises, estimatedMinutes: estimateMinutes(exercises) };
}

function optimizeSetupAwareSessionSequence(session, exerciseMap, request) {
    const indexed = session.exercises.map((exercise, index) => ({ exercise, index, def: exerciseMap.get(exercise.exerciseId) }));
    const used = new Set();
    const blocks = [];
    const familyClass = (def) => {
        if (!def)
            return 'other';
        if (['horizontal_press', 'vertical_press', 'chest_adduction'].includes(def.movementFamily))
            return 'push';
        if (['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(def.movementFamily))
            return 'pull';
        if (['squat', 'leg_press', 'knee_extension'].includes(def.movementFamily))
            return 'knee';
        if (['hip_hinge', 'hip_extension', 'knee_flexion'].includes(def.movementFamily))
            return 'hinge';
        return def.movementFamily;
    };
    const makeBlock = (items) => {
        // A mixed compound/accessory density pair always presents the compound first. Pure accessory
        // pairs preserve their original order. Strength work is never supersetted by construction.
        items = [...items].sort((a, b) => {
            const ac = !!a.def?.flags.compound, bc = !!b.def?.flags.compound;
            if (ac !== bc)
                return ac ? -1 : 1;
            return a.index - b.index;
        });
        const lead = items.find(x => x.def?.flags.compound) ?? items[0];
        const defs = items.map(x => x.def).filter((x) => !!x);
        const primaryMuscles = new Set();
        for (const def of defs)
            for (const [muscle, contribution] of Object.entries(def.muscles)) {
                if (contribution?.role === 'primary' || contribution?.credit === 1)
                    primaryMuscles.add(muscle);
            }
        return {
            items, first: Math.min(...items.map(x => x.index)),
            compound: defs.some(def => def.flags.compound),
            strength: items.some(x => x.exercise.role === 'primary_strength' || x.exercise.role === 'secondary_strength' || x.exercise.role === 'strength_support'),
            familyClass: familyClass(lead?.def), primaryMuscles,
            joint: [
                Math.max(0, ...defs.map(d => d.fatigue.shoulder)), Math.max(0, ...defs.map(d => d.fatigue.elbow)),
                Math.max(0, ...defs.map(d => d.fatigue.knee)), Math.max(0, ...defs.map(d => d.fatigue.grip)),
                Math.max(0, ...defs.map(d => d.fatigue.lowerBack))
            ]
        };
    };
    const firstDef = (block) => block.items.find(x => x.def)?.def;
    const lastDef = (block) => [...block.items].reverse().find(x => x.def)?.def;
    for (const item of indexed) {
        if (used.has(item.index))
            continue;
        if (item.exercise.supersetGroup) {
            const pair = indexed.filter(x => x.exercise.supersetGroup === item.exercise.supersetGroup);
            pair.forEach(x => used.add(x.index));
            blocks.push(makeBlock(pair));
        }
        else {
            used.add(item.index);
            blocks.push(makeBlock([item]));
        }
    }
    const strength = blocks.filter(b => b.strength).sort((a, b) => a.first - b.first);
    const compoundPool = blocks.filter(b => !b.strength && b.compound).sort((a, b) => a.first - b.first);
    const accessories = blocks.filter(b => !b.strength && !b.compound).sort((a, b) => a.first - b.first);
    // Mixed density pairs (compound + non-interfering accessory) can be useful under a real time cap,
    // but the accessory must not pre-fatigue another compound that is still waiting. Build explicit
    // dependencies between compound blocks: if block A contains an accessory for a muscle materially
    // used by compound block B, B has to happen first. This preserves the superset while protecting
    // later performance.
    const preFatigues = (a, b) => {
        const accessoryPrimaries = a.items.filter(x => x.def && !x.def.flags.compound).map(x => Object.entries(x.def.muscles).find(([, c]) => c.role === 'primary')?.[0]).filter(Boolean);
        if (!accessoryPrimaries.length)
            return false;
        return b.items.some(x => x.def?.flags.compound && accessoryPrimaries.some(m => (x.def.muscles[m]?.credit ?? 0) >= .5));
    };
    let compounds = [];
    const remaining = [...compoundPool];
    while (remaining.length) {
        const available = remaining.filter(candidate => !remaining.some(other => other !== candidate && preFatigues(candidate, other)));
        const pool = available.length ? available : remaining; // deterministic fail-safe for an unlikely dependency cycle
        let chosen = pool[0];
        if ((session.intent === 'full' || session.intent === 'strength_full') && compounds.length && pool.length > 1) {
            const prev = compounds[compounds.length - 1];
            chosen = [...pool].sort((a, b) => {
                const score = (cand) => {
                    const classPenalty = cand.familyClass === prev.familyClass ? 5 : 0;
                    const jointOverlap = cand.joint.reduce((sum, v, j) => sum + Math.min(v, prev.joint[j]), 0);
                    // Setup affinity is only a low-weight tie-break here. Full-body interference rotation remains
                    // far more important than staying at the same station.
                    const setup = setupTransitionCost(lastDef(prev), firstDef(cand));
                    return classPenalty + jointOverlap * .35 + setup * .08 + cand.first * .01;
                };
                return score(a) - score(b) || a.first - b.first;
            })[0];
        }
        else
            chosen = [...pool].sort((a, b) => a.first - b.first)[0];
        compounds.push(chosen);
        remaining.splice(remaining.indexOf(chosen), 1);
    }
    const priorityValue = { maintenance: 0, normal: 1, high: 2, specialization: 3, primary: 4 };
    const accessoryTier = (block) => {
        if (block.items.some(x => x.exercise.role === 'specialization'))
            return 0;
        let best = 0;
        for (const muscle of block.primaryMuscles)
            best = Math.max(best, priorityValue[request.goal.musclePriorities[muscle] ?? 'normal']);
        return best >= 2 ? 1 : 2;
    };
    const adjacencyPenalty = (a, b) => {
        if (!a)
            return b.first * .0001;
        const setup = setupTransitionCost(lastDef(a), firstDef(b));
        const shared = [...a.primaryMuscles].some(m => b.primaryMuscles.has(m));
        const sameFamily = a.familyClass === b.familyClass;
        const jointOverlap = b.joint.reduce((sum, v, j) => sum + Math.min(v, a.joint[j]), 0);
        // Setup is the dominant objective only after higher-value sequencing has already been protected.
        // Small local-fatigue penalties prevent pathological grouping when two options save the same setup.
        return setup + (shared ? .22 : 0) + (sameFamily ? .08 : 0) + jointOverlap * .012;
    };
    const optimizeBand = (band, preceding) => {
        if (band.length <= 1)
            return band;
        // Accessory tails are normally short. Solve the station path exactly for <=9 blocks; use a
        // deterministic nearest-neighbor fallback for unusual custom programs with larger tails.
        if (band.length > 9) {
            const left = [...band], out = [];
            let prev = preceding;
            while (left.length) {
                const next = [...left].sort((a, b) => adjacencyPenalty(prev, a) - adjacencyPenalty(prev, b) || a.first - b.first)[0];
                out.push(next);
                left.splice(left.indexOf(next), 1);
                prev = next;
            }
            return out;
        }
        const n = band.length, dp = new Map();
        const better = (candidate, current) => {
            if (!current)
                return true;
            if (Math.abs(candidate.cost - current.cost) > .000001)
                return candidate.cost < current.cost;
            const ca = candidate.path.map(i => band[i].first), cb = current.path.map(i => band[i].first);
            for (let i = 0; i < Math.min(ca.length, cb.length); i++)
                if (ca[i] !== cb[i])
                    return ca[i] < cb[i];
            return ca.length < cb.length;
        };
        for (let i = 0; i < n; i++)
            dp.set(`${1 << i}:${i}`, { cost: adjacencyPenalty(preceding, band[i]), path: [i] });
        for (let mask = 1; mask < (1 << n); mask++)
            for (let last = 0; last < n; last++) {
                const state = dp.get(`${mask}:${last}`);
                if (!state)
                    continue;
                for (let next = 0; next < n; next++) {
                    if (mask & (1 << next))
                        continue;
                    const key = `${mask | (1 << next)}:${next}`;
                    const candidate = { cost: state.cost + adjacencyPenalty(band[last], band[next]), path: [...state.path, next] };
                    if (better(candidate, dp.get(key)))
                        dp.set(key, candidate);
                }
            }
        let best;
        const full = (1 << n) - 1;
        for (let last = 0; last < n; last++) {
            const state = dp.get(`${full}:${last}`);
            if (state && better(state, best))
                best = state;
        }
        return best ? best.path.map(i => band[i]) : band;
    };
    // Priority boundaries are hard. Specialization and user-prioritized accessories keep their place
    // ahead of ordinary cleanup work; equipment economy only optimizes within each equally safe band.
    const orderedAccessories = [];
    let preceding = compounds[compounds.length - 1] ?? strength[strength.length - 1];
    for (const tier of [0, 1, 2]) {
        const band = accessories.filter(block => accessoryTier(block) === tier);
        const optimized = optimizeBand(band, preceding);
        orderedAccessories.push(...optimized);
        if (optimized.length)
            preceding = optimized[optimized.length - 1];
    }
    const exercises = [...strength, ...compounds, ...orderedAccessories].flatMap(block => block.items.map(item => item.exercise));
    return { ...session, exercises, estimatedMinutes: estimateMinutes(exercises) };
}

function finalizePlannedSession(session, request) {
    const exerciseMap = createExerciseMap(request.customExercises);
    return sequenceSessionExercises(assignAccessorySupersets({ ...session, exercises: session.exercises.map(ex => ({ ...ex })), estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets), exerciseMap);
}

export { assignAccessorySupersets, sequenceSessionExercises, optimizeSetupAwareSessionSequence, finalizePlannedSession };
