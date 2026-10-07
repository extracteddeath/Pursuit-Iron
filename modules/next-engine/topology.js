import { createExerciseMap } from './exercise-db.js';
import { publicSplitName } from './method-policy.js';
export const INTENT_MUSCLES = {
    upper: ['chest', 'back', 'side_delts', 'rear_delts', 'biceps', 'triceps', 'front_delts', 'traps', 'forearms', 'neck'],
    lower: ['quads', 'hamstrings', 'glutes', 'calves', 'core', 'adductors', 'abductors', 'lower_back'],
    push: ['chest', 'side_delts', 'front_delts', 'triceps'],
    pull: ['back', 'rear_delts', 'biceps', 'traps', 'forearms'],
    legs: ['quads', 'hamstrings', 'glutes', 'calves', 'core', 'adductors', 'abductors', 'lower_back'],
    full: ['chest', 'back', 'side_delts', 'rear_delts', 'biceps', 'triceps', 'quads', 'hamstrings', 'glutes', 'calves', 'core', 'front_delts', 'traps', 'forearms', 'adductors', 'abductors', 'neck', 'lower_back'],
    chest: ['chest', 'triceps', 'front_delts', 'side_delts'],
    back_day: ['back', 'rear_delts', 'biceps', 'traps', 'forearms'],
    shoulders: ['side_delts', 'rear_delts', 'front_delts', 'traps', 'triceps'],
    arms: ['biceps', 'triceps', 'forearms', 'side_delts'],
    chest_back: ['chest', 'back', 'rear_delts', 'front_delts', 'biceps', 'triceps', 'traps'],
    shoulders_arms: ['side_delts', 'rear_delts', 'front_delts', 'biceps', 'triceps', 'forearms', 'traps'],
    torso: ['chest', 'back', 'side_delts', 'rear_delts', 'front_delts', 'traps'],
    limbs: ['quads', 'hamstrings', 'glutes', 'calves', 'biceps', 'triceps', 'forearms', 'core', 'adductors', 'abductors'],
    glute: ['glutes', 'hamstrings', 'quads', 'abductors', 'adductors', 'calves', 'core', 'lower_back'],
    core_day: ['core', 'adductors', 'abductors'],
    strength_full: ['chest', 'back', 'quads', 'hamstrings', 'glutes', 'side_delts', 'rear_delts', 'front_delts', 'biceps', 'triceps', 'calves', 'core', 'traps', 'forearms', 'lower_back'],
    squat_focus: ['quads', 'glutes', 'hamstrings', 'calves', 'core', 'adductors', 'abductors', 'lower_back'],
    bench_focus: ['chest', 'triceps', 'front_delts', 'side_delts', 'back', 'rear_delts'],
    deadlift_focus: ['hamstrings', 'glutes', 'back', 'calves', 'core', 'lower_back', 'traps', 'forearms', 'biceps'],
    press_focus: ['front_delts', 'triceps', 'side_delts', 'back']
};
export const SUPPORTED_SPLIT_FAMILIES = Object.freeze([
    'full_body', 'upper_lower', 'custom', 'ppl', 'ulppl', 'pplul', 'hybrid', 'phat', 'bro',
    'glute_focus', 'torso_limbs', 'ppla', 'ula', 'arnold', 'full_body_patterns', 'phul',
    'five_three_one', 'academy_prep', 'gzclp', 'rippler', 'jt', 'sbd_power', 'five31_beginner',
    'strength_fb', 'texas', 'upper_lower_alt'
]);
const FLEX_ACCESSORY_MUSCLES = new Set(['biceps', 'triceps', 'side_delts', 'rear_delts', 'forearms']);
const FLEX_ACCESSORY_SPLITS = new Set(['upper_lower', 'phul', 'ulppl', 'pplul', 'hybrid', 'upper_lower_alt']);
const FLEX_LOWER_INTENTS = new Set(['lower', 'legs', 'glute', 'limbs']);
const UPPER_ACCESSORY_INTENTS = new Set(['upper', 'push', 'pull', 'arms', 'shoulders', 'shoulders_arms', 'chest', 'back_day', 'chest_back', 'torso', 'bench_focus', 'press_focus']);
function priorityRank(priority) {
    return priority === 'primary' ? 4 : priority === 'specialization' ? 3 : priority === 'high' ? 2 : priority === 'normal' ? 1 : 0;
}
/**
 * Conventional splits keep the session label meaningful, but bodybuilding practice sometimes moves
 * small upper-body accessories onto a Lower/Leg day to reduce same-session pre-fatigue or equalize
 * session density. This pass is intentionally conservative: it only moves accessory claims, never
 * chest/back compounds or primary lower work, and never allows more than two spillover claims on a
 * lower-emphasis session. Splits with a dedicated Arms/Limbs destination are intentionally excluded: they already have a conventional home for direct arm work and do not need an extra spillover heuristic.
 */
function rebalanceAccessorySpillover(seed, sessions, request) {
    if (!FLEX_ACCESSORY_SPLITS.has(seed.family))
        return;
    const spillCount = (session) => session.allocations.filter(a => a.kind === 'muscle' && !!a.muscle && FLEX_ACCESSORY_MUSCLES.has(a.muscle) && FLEX_LOWER_INTENTS.has(session.intent)).length;
    const lowerStructured = (session) => {
        const hasLowerLift = session.allocations.some(a => a.kind === 'lift' && (a.lift === 'back_squat' || a.lift === 'deadlift'));
        const lowerMuscleDose = session.allocations
            .filter(a => a.kind === 'muscle' && !!a.muscle && ['quads', 'hamstrings', 'glutes'].includes(a.muscle))
            .reduce((sum, a) => sum + a.dose, 0);
        // Accessory spillover is only useful after the Lower/Leg day has real lower-body depth. A lone
        // squat/deadlift anchor plus a fractional 0.5-set placeholder can realize as a one-movement day;
        // adding arms there would dilute the label instead of balancing workload.
        return lowerMuscleDose >= 1.5 || (hasLowerLift && lowerMuscleDose >= 1);
    };
    const projectedRatio = (session) => sessionProjectedMinutes(session) / Math.max(1, session.maxMinutes);
    const muscles = [['side_delts', 0.09], ['biceps', 0.10], ['rear_delts', 0.12], ['forearms', 0.12], ['triceps', 0.15]];
    for (const [muscle, baseThreshold] of muscles) {
        const priority = request.goal.musclePriorities[muscle];
        const sources = sessions
            .filter(s => UPPER_ACCESSORY_INTENTS.has(s.intent))
            .filter(s => s.allocations.some(a => a.kind === 'muscle' && a.muscle === muscle))
            .sort((a, b) => projectedRatio(b) - projectedRatio(a) || a.id.localeCompare(b.id));
        for (const source of sources) {
            const allocation = source.allocations
                .filter(a => a.kind === 'muscle' && a.muscle === muscle)
                .sort((a, b) => a.dose - b.dose || a.id.localeCompare(b.id))[0];
            if (!allocation)
                continue;
            const target = sessions
                .filter(s => FLEX_LOWER_INTENTS.has(s.intent) && lowerStructured(s) && spillCount(s) < 2)
                .filter(s => s.targetExercises === undefined || s.allocations.length < (s.targetExercises + 1))
                .map(s => ({ s, before: projectedRatio(s) }))
                .filter(x => x.before < .88)
                .sort((a, b) => a.before - b.before || a.s.id.localeCompare(b.s.id))[0];
            if (!target)
                continue;
            const sourcePressure = projectedRatio(source);
            const priorityRelief = priorityRank(priority) >= 2 ? .04 : 0;
            const threshold = Math.max(.06, baseThreshold - priorityRelief);
            if (sourcePressure < .72 || sourcePressure - target.before < threshold)
                continue;
            const originalSource = source.allocations.map(a => ({ ...a }));
            const transferDose = allocation.dose > 2.5 ? Math.min(2, Math.round((allocation.dose / 2) * 10) / 10) : allocation.dose;
            if (allocation.dose - transferDose >= .75)
                allocation.dose = Math.round((allocation.dose - transferDose) * 10) / 10;
            else
                source.allocations = source.allocations.filter(a => a !== allocation);
            const spillId = `${allocation.id}-spill-${target.s.id}`;
            target.s.allocations.push({ ...allocation, id: spillId, dose: transferDose });
            // If moving the accessory would make the Lower day the more crowded session, undo it. This
            // keeps spillover an actual balancing tool rather than a novelty placement rule.
            if (sessionProjectedMinutes(target.s) > target.s.maxMinutes * .94 || projectedRatio(target.s) > projectedRatio(source) + .04) {
                target.s.allocations = target.s.allocations.filter(a => a.id !== spillId);
                source.allocations = originalSource;
                continue;
            }
            break;
        }
    }
}
const INTENT_LABELS = {
    upper: 'Upper', lower: 'Lower', push: 'Push', pull: 'Pull', legs: 'Legs', full: 'Full Body',
    chest: 'Chest', back_day: 'Back', shoulders: 'Shoulders', arms: 'Arms', chest_back: 'Chest + Back',
    shoulders_arms: 'Shoulders + Arms', torso: 'Torso', limbs: 'Limbs', glute: 'Glutes + Lower', core_day: 'Core',
    strength_full: 'Strength Full Body', squat_focus: 'Squat', bench_focus: 'Bench', deadlift_focus: 'Deadlift', press_focus: 'Press'
};
function candidateStructures(days, locked, preferred) {
    const all = [];
    const add = (family, intents, display) => {
        if (intents.length === days)
            all.push({ family, intents, display });
    };
    // Preserve the established automatic candidate pool, but make Full Body a real hard-selectable
    // structure at every supported frequency when the athlete explicitly asks for it.
    if (days === 2) {
        add('full_body', ['full', 'full'], '2-Day Full Body');
        add('upper_lower', ['upper', 'lower'], 'Upper / Lower');
    }
    else if (days === 3) {
        add('full_body', ['full', 'full', 'full'], '3-Day Full Body');
        add('custom', ['upper', 'lower', 'full'], 'Upper / Lower / Full');
        add('ppl', ['push', 'pull', 'legs'], 'Push / Pull / Legs');
    }
    else if (days === 4) {
        add('upper_lower', ['upper', 'lower', 'upper', 'lower'], 'Upper / Lower');
        add('custom', ['upper', 'lower', 'push', 'pull'], 'Upper / Lower / Push / Pull');
        add('custom', ['push', 'pull', 'lower', 'full'], 'Push / Pull / Lower / Full');
    }
    else if (days === 5) {
        add('ulppl', ['upper', 'lower', 'push', 'pull', 'legs'], 'ULPPL');
        add('custom', ['upper', 'lower', 'pull', 'push', 'legs'], 'Upper / Lower / Pull / Push / Legs');
        add('pplul', ['push', 'pull', 'legs', 'upper', 'lower'], 'PPLUL');
        add('custom', ['upper', 'lower', 'upper', 'lower', 'full'], 'Upper / Lower Hybrid');
    }
    else if (days === 6) {
        add('ppl', ['push', 'pull', 'legs', 'push', 'pull', 'legs'], 'Push / Pull / Legs');
        add('upper_lower', ['upper', 'lower', 'upper', 'lower', 'upper', 'lower'], 'Upper / Lower ×3');
        add('custom', ['upper', 'lower', 'push', 'pull', 'legs', 'full'], 'Hybrid 6-Day');
    }
    else if (days >= 7) {
        add('ppl', ['push', 'pull', 'legs', 'push', 'pull', 'legs', 'full'].slice(0, days), 'PPL + Flexible');
    }
    if (days >= 4 && days <= 7 && (locked === 'full_body' || preferred === 'full_body')) {
        add('full_body', Array.from({ length: days }, () => 'full'), `${days}-Day Full Body`);
    }
    // v661 structural parity. These are topology contracts, not branded progression schemes: the
    // prescription/progression engine still owns reps, RIR, loading, and phase behavior. Keeping the
    // structures here means a saved/locked old split can be reproduced without falling back to an
    // unrelated family.
    if (days === 5) {
        add('ppl', ['push', 'pull', 'legs', 'push', 'pull'], 'Push / Pull / Legs · 5 Day');
        add('hybrid', ['upper', 'lower', 'push', 'pull', 'legs'], 'Hybrid Strength + Size');
        add('phat', ['upper', 'lower', 'back_day', 'legs', 'chest'], 'Power–Hypertrophy 5-Day');
        add('bro', ['chest', 'back_day', 'shoulders', 'legs', 'arms'], 'Bro Split');
        add('glute_focus', ['glute', 'upper', 'glute', 'upper', 'core_day'], 'Glutes & Lower Body');
        add('torso_limbs', ['torso', 'limbs', 'torso', 'limbs', 'torso'], 'Torso / Limbs');
        add('ppla', ['push', 'pull', 'legs', 'upper', 'shoulders_arms'], 'Push / Pull / Legs / Arms');
        add('ula', ['upper', 'lower', 'upper', 'lower', 'arms'], 'Upper / Lower / Arms');
    }
    if (days === 6) {
        add('bro', ['chest', 'back_day', 'shoulders', 'legs', 'arms', 'legs'], 'Bro Split · 6 Day');
        add('arnold', ['chest_back', 'shoulders_arms', 'legs', 'chest_back', 'shoulders_arms', 'legs'], 'Chest + Back / Shoulders + Arms / Legs');
        add('full_body_patterns', Array.from({ length: 6 }, () => 'full'), 'Full Body · Pattern Rotation');
    }
    if (days === 4) {
        add('phul', ['upper', 'lower', 'upper', 'lower'], 'Power / Hypertrophy Upper-Lower');
        add('five_three_one', ['press_focus', 'deadlift_focus', 'bench_focus', 'squat_focus'], 'Main-Lift Waves');
        add('academy_prep', ['strength_full', 'strength_full', 'strength_full', 'full'], 'Academy Prep');
        add('gzclp', ['squat_focus', 'bench_focus', 'deadlift_focus', 'press_focus'], 'Tiered Linear Progression');
        add('rippler', ['squat_focus', 'bench_focus', 'deadlift_focus', 'press_focus'], 'Tiered Wave');
        add('jt', ['squat_focus', 'bench_focus', 'deadlift_focus', 'press_focus'], 'Tiered Powerbuilding');
        add('glute_focus', ['glute', 'upper', 'glute', 'upper'], 'Glutes & Lower Body');
        add('torso_limbs', ['torso', 'limbs', 'torso', 'limbs'], 'Torso / Limbs');
        add('ppla', ['push', 'pull', 'legs', 'shoulders_arms'], 'Push / Pull / Legs / Arms');
        add('sbd_power', ['squat_focus', 'bench_focus', 'deadlift_focus', 'upper'], 'Powerlifting · SBD Wave');
        add('full_body_patterns', Array.from({ length: 4 }, () => 'full'), 'Full Body · Pattern Rotation');
    }
    if (days === 3) {
        add('five31_beginner', ['squat_focus', 'bench_focus', 'deadlift_focus'], 'Main-Lift Waves · Novice');
        add('strength_fb', ['strength_full', 'strength_full', 'strength_full'], 'Strength Full Body');
        add('academy_prep', ['strength_full', 'strength_full', 'strength_full'], 'Academy Prep');
        add('texas', ['strength_full', 'strength_full', 'strength_full'], 'Volume / Recovery / Intensity');
        add('glute_focus', ['glute', 'upper', 'glute'], 'Glutes & Lower Body');
        add('sbd_power', ['squat_focus', 'bench_focus', 'deadlift_focus'], 'Powerlifting · SBD Wave');
        add('upper_lower_alt', ['upper', 'lower', 'upper'], 'Upper / Lower · Alternating');
        add('full_body_patterns', Array.from({ length: 3 }, () => 'full'), 'Full Body · Pattern Rotation');
    }
    if (days === 2)
        add('full_body_patterns', ['full', 'full'], 'Full Body · Pattern Rotation');
    if (days === 5)
        add('full_body_patterns', Array.from({ length: 5 }, () => 'full'), 'Full Body · Pattern Rotation');
    // Branded/specialized v661 structures are compatibility contracts, not extra automatic lottery
    // tickets. They enter search when explicitly preferred/locked; otherwise the modern core topology
    // families compete. This preserves breadth without letting a random Bro/Glute/PHAT seed hijack a
    // generic hypertrophy request merely because it happened to score a little better.
    const automaticFamilies = new Set(['full_body', 'upper_lower', 'ppl', 'ulppl', 'pplul', 'custom']);
    const source = locked
        ? all.filter(x => x.family === locked)
        : all.filter(x => (automaticFamilies.has(x.family) && !(days === 5 && x.family === 'ppl')) || x.family === preferred);
    return [...source].sort((a, b) => {
        const ap = a.family === preferred ? 1 : 0;
        const bp = b.family === preferred ? 1 : 0;
        return bp - ap || a.display.localeCompare(b.display);
    });
}
function liftPreferredIntent(lift) {
    if (lift === 'bench_press')
        return ['bench_focus', 'upper', 'push', 'chest', 'chest_back', 'torso', 'shoulders', 'strength_full', 'full'];
    if (lift === 'overhead_press')
        return ['press_focus', 'shoulders', 'upper', 'push', 'shoulders_arms', 'torso', 'strength_full', 'full'];
    if (lift === 'back_squat')
        return ['squat_focus', 'lower', 'legs', 'glute', 'limbs', 'strength_full', 'full'];
    if (lift === 'deadlift')
        return ['deadlift_focus', 'lower', 'legs', 'glute', 'limbs', 'pull', 'strength_full', 'full'];
    return ['full'];
}
export function intentPreference(muscle) {
    switch (muscle) {
        case 'chest': return ['chest', 'bench_focus', 'push', 'chest_back', 'torso', 'upper', 'full'];
        case 'back': return ['back_day', 'deadlift_focus', 'pull', 'chest_back', 'torso', 'upper', 'full'];
        case 'side_delts': return ['shoulders', 'shoulders_arms', 'arms', 'push', 'upper', 'full', 'pull'];
        case 'rear_delts': return ['back_day', 'shoulders', 'shoulders_arms', 'pull', 'upper', 'full'];
        case 'front_delts': return ['press_focus', 'shoulders', 'push', 'chest', 'upper', 'full'];
        case 'biceps': return ['arms', 'shoulders_arms', 'back_day', 'pull', 'limbs', 'upper', 'full'];
        case 'triceps': return ['arms', 'shoulders_arms', 'push', 'chest', 'limbs', 'upper', 'full'];
        case 'quads': return ['squat_focus', 'legs', 'lower', 'glute', 'limbs', 'full'];
        case 'hamstrings': return ['deadlift_focus', 'lower', 'legs', 'glute', 'limbs', 'full'];
        case 'glutes': return ['glute', 'squat_focus', 'deadlift_focus', 'legs', 'lower', 'limbs', 'full'];
        case 'calves': return ['legs', 'lower', 'glute', 'limbs', 'full'];
        case 'core': return ['core_day', 'lower', 'legs', 'full'];
        case 'traps': return ['back_day', 'deadlift_focus', 'shoulders', 'pull', 'upper', 'full'];
        case 'forearms': return ['arms', 'back_day', 'pull', 'limbs', 'upper', 'full'];
        case 'adductors': return ['glute', 'legs', 'lower', 'limbs', 'full'];
        case 'abductors': return ['glute', 'legs', 'lower', 'limbs', 'full'];
        case 'neck': return ['shoulders', 'upper', 'full'];
        case 'lower_back': return ['deadlift_focus', 'squat_focus', 'lower', 'legs', 'glute', 'full'];
    }
}
function dayIndex(day) {
    return ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'].indexOf(day);
}
function circularGapDays(a, b) {
    const x = dayIndex(a), y = dayIndex(b);
    const forward = (y - x + 7) % 7;
    const backward = (x - y + 7) % 7;
    return Math.min(forward || 7, backward || 7);
}
function projectedAllocationMinutes(a) {
    if (a.kind === 'lift')
        return a.role === 'primary_strength' ? 20 : 15;
    const small = ['side_delts', 'rear_delts', 'front_delts', 'biceps', 'triceps', 'calves', 'core', 'traps', 'forearms', 'adductors', 'abductors', 'neck', 'lower_back'].includes(a.muscle);
    return a.dose * (small ? 1.65 : 2.75);
}
function sessionProjectedMinutes(s) {
    return 7 + s.allocations.reduce((sum, a) => sum + projectedAllocationMinutes(a), 0);
}
function recoveryPairPenalty(a, b, gap) {
    if (gap >= 2)
        return 0;
    const pair = `${a}:${b}`;
    const high = new Set(['upper:push', 'push:upper', 'push:push', 'lower:legs', 'legs:lower', 'lower:lower', 'legs:legs']);
    const medium = new Set(['pull:legs', 'legs:pull', 'full:full', 'upper:upper', 'pull:pull']);
    if (high.has(pair))
        return 6;
    if (medium.has(pair))
        return 2.5;
    if (a === 'full' || b === 'full')
        return 1.8;
    return .45;
}
function scoreStructure(seed, sessions, request) {
    const projected = sessions.map(sessionProjectedMinutes);
    const peak = Math.max(...projected);
    let overflowPenalty = 0;
    let capacityPenalty = 0;
    sessions.forEach((s, i) => {
        if (projected[i] > s.maxMinutes)
            overflowPenalty += (projected[i] - s.maxMinutes) * 8;
        else {
            const pressure = projected[i] / Math.max(1, s.maxMinutes);
            if (pressure > .88)
                capacityPenalty += (pressure - .88) * 8;
        }
    });
    let recoveryPenalty = 0;
    for (let i = 0; i < sessions.length; i++) {
        const next = (i + 1) % sessions.length;
        const gap = circularGapDays(sessions[i].day, sessions[next].day);
        recoveryPenalty += recoveryPairPenalty(sessions[i].intent, sessions[next].intent, gap);
    }
    let liftSpacingScore = 0;
    const liftDays = new Map();
    for (const s of sessions)
        for (const a of s.allocations)
            if (a.kind === 'lift') {
                const list = liftDays.get(a.lift) ?? [];
                list.push(s.day);
                liftDays.set(a.lift, list);
            }
    for (const days of liftDays.values())
        if (days.length > 1)
            liftSpacingScore += Math.min(...days.slice(1).map(d => circularGapDays(days[0], d))) * 1.5;
    const preferenceBonus = request.preferences.preferredSplit === seed.family ? 4 : 0;
    // A preferred split is guidance unless it is locked. For a generic strength goal, do not let the
    // preference bonus overpower an obvious structural recovery conflict such as forcing both squat and
    // deadlift strength anchors into the week's only lower-capable slot. Full-body/strength-full options
    // can separate those anchors across days without changing the athlete's selected weekdays. Locked
    // splits remain literal and are never auto-swapped by this score.
    const lowerCapableIntents = new Set(['lower', 'legs', 'glute', 'limbs', 'strength_full', 'full', 'squat_focus', 'deadlift_focus']);
    const lowerStrengthLifts = new Set(sessions.flatMap(session => session.allocations
        .filter(a => a.kind === 'lift' && (a.lift === 'back_squat' || a.lift === 'deadlift'))
        .map(a => a.lift)));
    const lowerSlots = seed.intents.filter(intent => lowerCapableIntents.has(intent)).length;
    const unlockedStrengthTradeoffPenalty = !request.preferences.lockedSplit && request.goal.type === 'strength' && lowerStrengthLifts.size >= 2 && lowerSlots <= 1 ? 10 : 0;
    const balance = projected.length ? Math.max(...projected) - Math.min(...projected) : 0;
    const balancePenalty = Math.max(0, balance - 25) * .08;
    const score = 100 + liftSpacingScore + preferenceBonus - overflowPenalty - capacityPenalty - recoveryPenalty - balancePenalty - unlockedStrengthTradeoffPenalty;
    return {
        family: seed.family,
        displayName: publicSplitName(seed.family, seed.display),
        intents: [...seed.intents],
        score: Math.round(score * 100) / 100,
        projectedPeakMinutes: Math.round(peak),
        recoveryPenalty: Math.round(recoveryPenalty * 100) / 100
    };
}
function liftRegion(lift) {
    if (lift === 'back_squat' || lift === 'deadlift')
        return 'lower';
    if (lift === 'bench_press' || lift === 'overhead_press')
        return 'upper';
    return 'other';
}
function crossLiftInterference(alloc, session) {
    const region = liftRegion(alloc.lift);
    let penalty = 0;
    for (const existing of session.allocations.filter(a => a.kind === 'lift')) {
        const existingRegion = liftRegion(existing.lift);
        if (region === 'lower' && existingRegion === 'lower')
            penalty += 6;
        else if (region === 'upper' && existingRegion === 'upper')
            penalty += 2.5;
        else
            penalty += .75;
    }
    return penalty;
}
function chooseLiftSession(alloc, sessions, used, request) {
    const preferred = liftPreferredIntent(alloc.lift);
    const supported = (session) => { const day = request.schedule.days.find(item => item.day === session.day); return !!day && daySupportsLift(day, alloc.lift, request); };
    const preferredUnused = sessions.filter(s => preferred.includes(s.intent) && !used.has(s.id));
    const supportedPreferred = preferredUnused.filter(supported);
    const unused = sessions.filter(s => !used.has(s.id));
    const supportedUnused = unused.filter(supported);
    const pool = supportedPreferred.length ? supportedPreferred : preferredUnused.length ? preferredUnused : supportedUnused.length ? supportedUnused : unused;
    const fallback = pool.length ? pool : sessions;
    return [...fallback].sort((a, b) => {
        const ai = preferred.indexOf(a.intent), bi = preferred.indexOf(b.intent);
        const intentA = ai < 0 ? 10 : ai, intentB = bi < 0 ? 10 : bi;
        const loadA = sessionProjectedMinutes(a) / a.maxMinutes, loadB = sessionProjectedMinutes(b) / b.maxMinutes;
        const interferenceA = crossLiftInterference(alloc, a), interferenceB = crossLiftInterference(alloc, b);
        const spacingA = [...used].map(id => sessions.find(s => s.id === id)).filter(Boolean).reduce((best, s) => Math.min(best, circularGapDays(a.day, s.day)), 7);
        const spacingB = [...used].map(id => sessions.find(s => s.id === id)).filter(Boolean).reduce((best, s) => Math.min(best, circularGapDays(b.day, s.day)), 7);
        // Cross-lift fatigue is a stronger coaching constraint than a small intent preference. In
        // particular, do not stack squat and deadlift anchors on the same lower day when another
        // semantically valid day is available. Intent remains the tie-break after interference.
        return interferenceA - interferenceB || intentA - intentB || spacingB - spacingA || loadA - loadB || a.id.localeCompare(b.id);
    })[0];
}
function topologyEquipmentEligible(def, day, request) {
    const available = day.equipmentOverride ?? request.equipment.available;
    if ((def.flags.bodyweight || def.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
        return false;
    const setups = [def.equipment, ...(def.equipmentAlternatives ?? [])];
    return setups.some((setup) => setup.every(item => item === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : available.includes(item)));
}
function daySupportsLift(day, lift, request) {
    const defs = createExerciseMap(request.customExercises);
    const maxBarbell = day.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay;
    return [...defs.values()].some(def => (def.liftSpecificity?.[lift] ?? 0) > .45 && (!def.flags.barbell || maxBarbell >= 1) && topologyEquipmentEligible(def, day, request));
}
function eachIndexPermutation(values, visit, prefix = []) {
    if (!values.length) {
        visit(prefix);
        return;
    }
    for (let i = 0; i < values.length; i++)
        eachIndexPermutation([...values.slice(0, i), ...values.slice(i + 1)], visit, [...prefix, values[i]]);
}
/** Assign split intents to the athlete's fixed weekdays before realization so day-specific equipment
 * and barbell caps are respected by strength-anchor placement instead of being discovered afterward. */
function optimizeSeedDayContracts(seed, request, allocations) {
    const days = request.schedule.days;
    if (days.length < 2 || days.length !== seed.intents.length)
        return [...days];
    // If every selected day has the same execution contract, there is nothing for this pre-realization
    // solver to improve. Preserve the proven split/day mapping exactly and avoid factorial search; M56's
    // post-realization recovery scheduler can still permute finished workouts using their actual stress.
    const signature = (day) => JSON.stringify({
        equipment: [...(day.equipmentOverride ?? request.equipment.available)].sort(),
        maxBarbell: day.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay,
        min: day.minMinutes ?? 0, max: day.maxMinutes, target: day.targetExercises ?? null
    });
    const firstSignature = signature(days[0]);
    if (days.every(day => signature(day) === firstSignature))
        return [...days];
    const identity = days.map((_, i) => i), liftClaims = new Map();
    for (const alloc of allocations)
        if (alloc.kind === 'lift' && alloc.lift) {
            const list = liftClaims.get(alloc.lift) ?? [];
            list.push(alloc);
            liftClaims.set(alloc.lift, list);
        }
    let best = identity, bestPenalty = Number.POSITIVE_INFINITY, bestMove = Number.POSITIVE_INFINITY;
    eachIndexPermutation(identity, candidate => {
        let penalty = 0;
        for (const [lift, claims] of liftClaims) {
            let supported = 0;
            for (let intentIndex = 0; intentIndex < seed.intents.length; intentIndex++) {
                const day = days[candidate[intentIndex]];
                if (liftPreferredIntent(lift).includes(seed.intents[intentIndex]) && daySupportsLift(day, lift, request))
                    supported++;
            }
            const missing = Math.max(0, claims.length - supported);
            if (missing) {
                const ordered = [...claims].sort((a, b) => (a.importance === 'A' ? 0 : 1) - (b.importance === 'A' ? 0 : 1) || a.id.localeCompare(b.id));
                for (const claim of ordered.slice(Math.max(0, ordered.length - missing)))
                    penalty += claim.importance === 'A' ? 2000 : 500;
            }
        }
        // Prefer putting strength-oriented intents on roomier days after equipment feasibility is tied.
        for (let i = 0; i < seed.intents.length; i++)
            if (['squat_focus', 'bench_focus', 'deadlift_focus', 'press_focus', 'strength_full'].includes(seed.intents[i]))
                penalty += Math.max(0, 60 - days[candidate[i]].maxMinutes) * .15;
        const move = candidate.reduce((sum, dayIndex, intentIndex) => sum + Math.abs(dayIndex - intentIndex), 0);
        if (penalty < bestPenalty - .0001 || (Math.abs(penalty - bestPenalty) <= .0001 && move < bestMove)) {
            best = [...candidate];
            bestPenalty = penalty;
            bestMove = move;
        }
    });
    return best.map(index => days[index]);
}
function buildCandidate(seed, request, allocations) {
    const assignedDays = optimizeSeedDayContracts(seed, request, allocations);
    const sessions = assignedDays.map((d, i) => ({
        id: `session-${i + 1}`,
        day: d.day,
        name: '',
        intent: seed.intents[i] ?? 'full',
        minMinutes: d.minMinutes,
        maxMinutes: d.maxMinutes,
        targetExercises: d.targetExercises,
        allocations: []
    }));
    const liftAllocations = allocations.filter(a => a.kind === 'lift').sort((a, b) => a.importance.localeCompare(b.importance) || a.id.localeCompare(b.id));
    const usedByLift = new Map();
    for (const alloc of liftAllocations) {
        const used = usedByLift.get(alloc.lift) ?? new Set();
        const chosen = chooseLiftSession(alloc, sessions, used, request);
        chosen.allocations.push({ ...alloc });
        used.add(chosen.id);
        usedByLift.set(alloc.lift, used);
    }
    const muscleAllocations = allocations.filter(a => a.kind === 'muscle').sort((a, b) => {
        const importance = { A: 0, B: 1, C: 2 };
        return importance[a.importance] - importance[b.importance] || b.dose - a.dose || a.id.localeCompare(b.id);
    });
    // A locked full-body split is a structural promise, not a label. When the cycle builder supplied
    // an exercise-count contract, plan enough weekly exercise slots to make each day genuinely full body.
    const fullBodyExposurePlan = new Map();
    if (seed.family === 'full_body' && sessions.some(s => s.targetExercises !== undefined)) {
        const dayCount = sessions.length;
        const desiredSlots = sessions.reduce((sum, s) => sum + (s.targetExercises ?? 3), 0);
        const mandatory = new Set(['chest', 'back']);
        for (const alloc of muscleAllocations) {
            if (alloc.muscle && mandatory.has(alloc.muscle))
                fullBodyExposurePlan.set(alloc.id, dayCount);
            else
                fullBodyExposurePlan.set(alloc.id, 1);
        }
        let current = [...fullBodyExposurePlan.values()].reduce((a, b) => a + b, 0);
        const importanceWeight = { A: 3, B: 2, C: 1 };
        while (current < desiredSlots) {
            const candidate = muscleAllocations
                .filter(a => !mandatory.has(a.muscle ?? ''))
                .filter(a => (fullBodyExposurePlan.get(a.id) ?? 1) < Math.min(dayCount, Math.max(1, Math.floor(a.dose))))
                .map(a => ({ a, exposures: fullBodyExposurePlan.get(a.id) ?? 1 }))
                .sort((x, y) => {
                const xv = (x.a.dose / x.exposures) * importanceWeight[x.a.importance];
                const yv = (y.a.dose / y.exposures) * importanceWeight[y.a.importance];
                return yv - xv || x.a.id.localeCompare(y.a.id);
            })[0];
            if (!candidate)
                break;
            fullBodyExposurePlan.set(candidate.a.id, candidate.exposures + 1);
            current++;
        }
    }
    for (const alloc of muscleAllocations) {
        const compatible = sessions.filter(s => INTENT_MUSCLES[s.intent]?.includes(alloc.muscle));
        if (!compatible.length)
            continue;
        const preferred = intentPreference(alloc.muscle);
        // Structural coverage is a destination contract, not merely an exposure count. If a split contains
        // an Upper session, chest/back work must actually reach that Upper session rather than both claims
        // being consumed by more-preferred focus days elsewhere in the week.
        const coreByIntent = {
            upper: ['chest', 'back'],
            lower: ['quads', 'hamstrings'],
            push: ['chest'],
            pull: ['back'],
            legs: ['quads', 'hamstrings']
        };
        const structuralFamilies = new Set(['upper_lower', 'phul', 'ulppl', 'pplul', 'ppl', 'sbd_power']);
        const structuralRequired = structuralFamilies.has(seed.family)
            ? compatible.filter(session => (coreByIntent[session.intent] ?? []).includes(alloc.muscle))
            : [];
        const requestedExposures = seed.family === 'full_body'
            ? Math.min(compatible.length, fullBodyExposurePlan.get(alloc.id) ?? Math.max(2, Math.ceil(alloc.dose / 2)))
            : Math.min(compatible.length, Math.max(structuralRequired.length || 1, alloc.dose >= 4.5 ? 2 : 1));
        const requiredIds = new Set(structuralRequired.map(session => session.id));
        const optionalRanked = compatible.filter(session => !requiredIds.has(session.id)).sort((a, b) => {
            const ai = preferred.indexOf(a.intent), bi = preferred.indexOf(b.intent);
            const prefA = ai < 0 ? 10 : ai, prefB = bi < 0 ? 10 : bi;
            const loadA = sessionProjectedMinutes(a) / a.maxMinutes, loadB = sessionProjectedMinutes(b) / b.maxMinutes;
            return prefA - prefB || loadA - loadB || a.id.localeCompare(b.id);
        });
        const ranked = [...structuralRequired, ...optionalRanked].slice(0, requestedExposures);
        const per = alloc.dose / ranked.length;
        ranked.forEach((session, index) => {
            const target = session.targetExercises;
            const largeMuscle = !!alloc.muscle && ['chest', 'back', 'quads', 'hamstrings', 'glutes'].includes(alloc.muscle);
            const splitThreshold = target && target >= 9 ? 2.75 : target && target >= 7 ? 3.5 : target && target >= 5 ? 4.5 : Number.POSITIVE_INFINITY;
            const slots = largeMuscle && per >= splitThreshold ? 2 : 1;
            if (slots === 1) {
                session.allocations.push({ ...alloc, id: `${alloc.id}-${index + 1}`, dose: Math.round(per * 10) / 10 });
            }
            else {
                const first = Math.round((per / 2) * 10) / 10;
                const second = Math.round((per - first) * 10) / 10;
                session.allocations.push({ ...alloc, id: `${alloc.id}-${index + 1}-a`, dose: first });
                session.allocations.push({ ...alloc, id: `${alloc.id}-${index + 1}-b`, dose: second });
            }
        });
    }
    rebalanceAccessorySpillover(seed, sessions, request);
    // Full-body capacity contracts also require balanced slot counts. Strength work can otherwise make the
    // topology optimizer avoid those days and dump accessories onto the remaining sessions. Move flexible
    // muscle allocations before realization so every full-body day stays recognizably complete.
    if (seed.family === 'full_body' && sessions.some(s => s.targetExercises !== undefined)) {
        for (let pass = 0; pass < 80; pass++) {
            const under = [...sessions]
                .filter(s => s.targetExercises !== undefined && s.allocations.length < Math.max(3, (s.targetExercises ?? 3) - 1))
                .sort((a, b) => (a.allocations.length - (a.targetExercises ?? 3)) - (b.allocations.length - (b.targetExercises ?? 3)) || a.id.localeCompare(b.id))[0];
            if (!under)
                break;
            const donor = [...sessions]
                .filter(s => s.id !== under.id && s.allocations.length > (s.targetExercises ?? 3))
                .sort((a, b) => (b.allocations.length - (b.targetExercises ?? 3)) - (a.allocations.length - (a.targetExercises ?? 3)) || b.id.localeCompare(a.id))[0];
            if (!donor)
                break;
            const underMuscles = new Set(under.allocations.filter(a => a.kind === 'muscle').map(a => a.muscle));
            const movable = donor.allocations
                .filter(a => a.kind === 'muscle')
                .sort((a, b) => {
                const duplicateA = underMuscles.has(a.muscle) ? 1 : 0, duplicateB = underMuscles.has(b.muscle) ? 1 : 0;
                const importance = { A: 2, B: 1, C: 0 };
                return duplicateA - duplicateB || importance[a.importance] - importance[b.importance] || a.dose - b.dose || a.id.localeCompare(b.id);
            })[0];
            if (!movable)
                break;
            donor.allocations = donor.allocations.filter(a => a !== movable);
            under.allocations.push({ ...movable, id: `${movable.id}-balance-${under.id}` });
        }
    }
    // If the user selected a day, do not leave it as a fake 7-minute empty workout.
    // Rebalance flexible muscle work from the most loaded compatible donor into any empty day.
    for (const empty of sessions.filter(s => s.allocations.length === 0)) {
        const compatibleMuscles = INTENT_MUSCLES[empty.intent] ?? [];
        const donors = [...sessions]
            .filter(s => s.id !== empty.id)
            .sort((a, b) => sessionProjectedMinutes(b) - sessionProjectedMinutes(a));
        let moved = false;
        for (const donor of donors) {
            const candidates = donor.allocations
                .filter(a => a.kind === 'muscle' && !!a.muscle && compatibleMuscles.includes(a.muscle) && a.dose >= 1.5)
                .sort((a, b) => b.dose - a.dose || a.id.localeCompare(b.id));
            const source = candidates[0];
            if (!source)
                continue;
            const transfer = Math.max(.75, Math.round((source.dose / 2) * 10) / 10);
            source.dose = Math.max(.5, Math.round((source.dose - transfer) * 10) / 10);
            empty.allocations.push({ ...source, id: `${source.id}-rebalance-${empty.id}`, dose: transfer });
            moved = true;
            break;
        }
        if (!moved) {
            const donor = donors.find(s => s.allocations.some(a => a.kind === 'muscle' && a.dose >= 1));
            const source = donor?.allocations.find(a => a.kind === 'muscle' && a.dose >= 1);
            if (source) {
                const transfer = Math.min(1, source.dose);
                source.dose = Math.max(0, source.dose - transfer);
                empty.allocations.push({ ...source, id: `${source.id}-rebalance-${empty.id}`, dose: transfer });
                if (source.dose <= 0)
                    donor.allocations = donor.allocations.filter(a => a !== source);
            }
        }
    }
    sessions.forEach((s, i) => {
        const primaryBench = s.allocations.some(a => a.lift === 'bench_press' && a.role === 'primary_strength');
        const volumeBench = s.allocations.some(a => a.lift === 'bench_press' && a.role === 'secondary_strength');
        const primaryOther = s.allocations.find(a => a.kind === 'lift' && a.role === 'primary_strength');
        const baseLabel = seed.family === 'full_body' ? `Full Body ${String.fromCharCode(65 + i)}` : (INTENT_LABELS[s.intent] ?? `Session ${i + 1}`);
        s.name = primaryBench ? `${baseLabel} — Bench Focus`
            : volumeBench ? `${baseLabel} — Bench Volume`
                : primaryOther ? `${baseLabel} — ${primaryOther.lift.replace('_', ' ')}`
                    : baseLabel;
    });
    return { seed, sessions, summary: scoreStructure(seed, sessions, request) };
}
export function solveTopology(request, allocations, options = {}) {
    const days = request.schedule.days.length;
    const seeds = options.seed ? [options.seed] : candidateStructures(days, request.preferences.lockedSplit, request.preferences.preferredSplit);
    if (!seeds.length)
        throw new Error(`No topology seed supports ${days} training days.`);
    const candidates = seeds.map(seed => buildCandidate(seed, request, allocations)).sort((a, b) => b.summary.score - a.summary.score || a.seed.display.localeCompare(b.seed.display));
    const chosen = candidates[0];
    const rationale = [
        `Evaluated ${candidates.length} topology candidate${candidates.length === 1 ? '' : 's'} and selected ${publicSplitName(chosen.seed.family, chosen.seed.display)}.`,
        'A-priority lift exposures were placed before muscle-volume allocations and same-lift exposures were spread when the calendar allowed it.',
        'Muscle work was distributed using session intent, projected time pressure, and weekly recovery rather than a fixed exercise template.'
    ];
    if (candidates.length > 1) {
        const runner = candidates[1];
        rationale.push(`${publicSplitName(runner.seed.family, runner.seed.display)} was the next structural candidate (${runner.summary.score.toFixed(1)} vs ${chosen.summary.score.toFixed(1)}).`);
    }
    return {
        seed: chosen.seed,
        family: chosen.seed.family,
        displayName: publicSplitName(chosen.seed.family, chosen.seed.display),
        sessions: chosen.sessions,
        rationale,
        candidates: candidates.map(c => c.summary)
    };
}
