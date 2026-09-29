export const SUPPORTED_PROGRESSION_STYLES = ['auto', 'double', 'dynamic', 'ladder', 'linear', 'wave', 'e1rm'];

const SIMPLE_STYLES = new Set(['double', 'dynamic', 'ladder', 'linear']);
const ADVANCED_STYLES = new Set(['wave', 'e1rm']);

function finite(value, fallback = 0) {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
}

function explicitStyle(context = {}) {
    const requested = context.manualStyle ?? context.requestedStyle;
    return SUPPORTED_PROGRESSION_STYLES.includes(requested) && requested !== 'auto' ? requested : null;
}

function repRangeFor(ex, role, context = {}) {
    const prescribed = context?.prescription?.reps;
    if (Array.isArray(prescribed) && prescribed.length >= 2) {
        const a = finite(prescribed[0], 1), b = finite(prescribed[1], a);
        return [Math.min(a, b), Math.max(a, b)];
    }
    const strength = role === 'primary_strength' || role === 'secondary_strength';
    const source = strength ? ex?.preferredReps?.strength : ex?.preferredReps?.hypertrophy;
    if (Array.isArray(source) && source.length >= 2)
        return [Math.min(finite(source[0], 1), finite(source[1], 1)), Math.max(finite(source[0], 1), finite(source[1], 1))];
    return strength ? [3, 6] : ex?.flags?.compound ? [6, 12] : [10, 15];
}

function phaseRepRange(ex, role, context = {}) {
    const raw = repRangeFor(ex, role, context);
    if (context?.prescription?.reps)
        return raw;
    const phase = context.phase;
    const strength = role === 'primary_strength' || role === 'secondary_strength';
    if (!strength)
        return raw;
    if (phase === 'peak')
        return role === 'primary_strength' ? [1, Math.min(3, raw[1])] : [2, Math.min(4, raw[1])];
    if (phase === 'intensification' || phase === 'strength_accumulation')
        return role === 'primary_strength' ? [Math.max(2, raw[0]), Math.min(5, raw[1])] : [Math.max(3, raw[0]), Math.min(6, raw[1])];
    if (phase === 'hypertrophy_accumulation')
        return role === 'primary_strength' ? [Math.max(5, raw[0]), Math.max(6, Math.min(8, raw[1]))] : [Math.max(6, raw[0]), Math.max(8, Math.min(10, raw[1]))];
    return raw;
}

function equipmentTraits(ex = {}) {
    const equipment = new Set([...(ex.equipment ?? []), ...(ex.equipmentAlternatives ?? []).flat?.() ?? []]);
    const barbell = !!ex.flags?.barbell || equipment.has('barbell');
    const bodyweight = !!ex.flags?.bodyweight || equipment.has('bodyweight');
    const quickChange = !barbell && ['dumbbell', 'dumbbells', 'machine', 'cable', 'smith', 'selectorized'].some(x => equipment.has(x));
    return { barbell, bodyweight, quickChange };
}

function exerciseTraits(ex = {}, role, context = {}) {
    const { barbell, bodyweight, quickChange } = equipmentTraits(ex);
    const compound = !!ex.flags?.compound;
    const stability = finite(ex.stability, compound ? 6 : 8);
    const loadability = finite(ex.loadability, compound ? 6 : 4);
    const reps = phaseRepRange(ex, role, context);
    const repWidth = Math.max(0, reps[1] - reps[0]);
    const stable = stability >= 6;
    const highlyLoadable = loadability >= 7 || barbell;
    const strength = role === 'primary_strength' || role === 'secondary_strength';
    return { barbell, bodyweight, quickChange, compound, stability, loadability, reps, repWidth, stable, highlyLoadable, strength };
}

function accumulateStyle(traits) {
    if (traits.bodyweight && !traits.highlyLoadable)
        return 'ladder';
    if (traits.compound && !traits.barbell && traits.quickChange)
        return 'dynamic';
    return 'double';
}

function selection(style, confidence, reason, source = 'auto') {
    return { style, confidence, reason, source };
}

/**
 * M189 progression selector.
 *
 * The old Auto behavior was intentionally parity-oriented: phase + experience + compound/barbell flags.
 * That was useful for matching v661, but it made progression a static property of the generated exercise.
 * This selector treats progression as a policy choice. It uses the exercise's loading characteristics,
 * rep structure, phase, role and experience level, while keeping advanced methods away from movements
 * whose measurement quality cannot support them.
 */
export function selectProgressionStyle(ex, role, context = {}) {
    const manual = explicitStyle(context);
    if (manual)
        return selection(manual, 'high', 'A manual progression choice is locked for this exercise.', 'manual');

    const phase = context.phase ?? 'mixed_accumulation';
    const experience = context.experience ?? 'intermediate';
    const blockWeeks = Math.max(1, finite(context.blockWeeks, 6));
    const t = exerciseTraits(ex, role, context);
    const accumulate = accumulateStyle(t);

    // Recovery and maintenance should reduce decision complexity as well as training stress.
    if (phase === 'recovery' || phase === 'maintenance') {
        return selection(accumulate, 'high', `${phase === 'recovery' ? 'Recovery' : 'Maintenance'} uses a simple rep/load progression so the method does not add fatigue or noise.`);
    }

    // Beginners benefit from frequent, obvious load steps only when the movement is stable and loadable.
    // Bodyweight/coarse-loading movements instead earn reps first; isolations stay on double progression.
    if (experience === 'novice') {
        if (t.compound && !t.bodyweight && t.stable && t.loadability >= 5)
            return selection('linear', 'high', 'A novice on a stable, loadable compound can use simple load steps while the adaptation rate is high.');
        if (t.bodyweight && t.repWidth >= 3)
            return selection('ladder', 'high', 'This novice bodyweight movement is better progressed through the rep range before external loading.');
        if (t.compound && t.quickChange)
            return selection('dynamic', 'moderate', 'Per-set loading is practical here, so a novice can progress uneven sets without forcing a full-load jump.');
        return selection('double', 'high', 'Double progression gives a novice a simple rep-first rule for this exercise.');
    }

    if (t.strength && t.compound) {
        // e1RM is reserved for low-rep, stable, loadable strength work. Using it on unstable or high-rep
        // work creates false precision from noisy estimates.
        const e1rmEligible = t.stable && t.highlyLoadable && t.reps[1] <= 5;
        const waveEligible = role === 'primary_strength' && experience !== 'novice' && t.stable && t.highlyLoadable
            && t.reps[1] <= 6 && blockWeeks >= 5;

        if (phase === 'peak') {
            if (e1rmEligible)
                return selection('e1rm', 'high', 'Peak-phase primary loading is low-rep, stable and loadable enough for effort-adjusted e1RM autoregulation.');
            return selection(accumulate, 'moderate', 'This peak exercise is not stable/loadable enough for reliable e1RM decisions, so Auto keeps a simpler progression.');
        }
        if (phase === 'intensification') {
            if (waveEligible)
                return selection('wave', 'high', 'An experienced primary strength lift in a long enough intensification block can use a short loading wave without changing plates every set.');
            if (e1rmEligible)
                return selection('e1rm', 'moderate', 'The lift is suitable for e1RM autoregulation, but the block/movement is not a good wave candidate.');
            return selection(accumulate, 'moderate', 'Auto avoids advanced strength progression because the movement or rep target does not support a reliable signal.');
        }
        if (phase === 'strength_accumulation' || phase === 'mixed_accumulation' || phase === 'hypertrophy_accumulation' || phase === 'foundation')
            return selection(accumulate, 'high', t.barbell
                ? 'Accumulation keeps one barbell load across work sets and builds reps before adding plates.'
                : 'Accumulation uses per-set progression because load changes are quick and individual sets can advance safely.');
    }

    if (t.compound) {
        if (t.bodyweight && t.repWidth >= 4)
            return selection('ladder', 'high', 'A broad bodyweight rep range makes rep-ladder progression more useful than forcing external load jumps.');
        if (t.barbell)
            return selection('double', 'high', 'A barbell compound keeps one working load across sets and earns the next plate jump by owning the rep range.');
        if (t.quickChange)
            return selection('dynamic', 'high', 'This compound has quick load changes, so each set can progress independently inside the rep range.');
        return selection('double', 'moderate', 'A single shared load with rep-first progression is the most robust default for this compound.');
    }

    // Isolation work deliberately stays simple. The smaller signal and smaller absolute loads make
    // e1RM/waves unnecessary; double progression also avoids chasing noisy set-to-set fluctuations.
    return selection('double', 'high', 'Isolation work uses rep-first double progression because it is simple, stable, and does not overreact to small performance noise.');
}

export function resolveProgressionStyle(ex, role, context = {}) {
    return selectProgressionStyle(ex, role, context).style;
}

function validCurrent(style) {
    return SUPPORTED_PROGRESSION_STYLES.includes(style) && style !== 'auto';
}

/**
 * Re-select Auto after training evidence exists. This is intentionally conservative: Auto may change
 * method when the phase changes or when repeated evidence shows the current method is a poor fit, but
 * it does not bounce between methods after one good/bad day.
 *
 * Supported evidence fields are optional so block review, simulation and the live history bridge can
 * all use the same policy as they gain richer data:
 *   comparableExposures, styleExposures, successful, stalled, stallCount, failureCount,
 *   fatigueLimited, techniqueLimited, loadingBlockedCount, rirCoverage, e1rmSamples.
 */
export function reselectProgressionStyle(ex, role, context = {}) {
    const manual = explicitStyle(context);
    if (manual)
        return selection(manual, 'high', 'The athlete manually selected this progression, so Auto will not replace it.', 'manual');

    const baseline = selectProgressionStyle(ex, role, context);
    const current = validCurrent(context.currentStyle) ? context.currentStyle : baseline.style;
    const evidence = context.evidence ?? {};
    const comparable = Math.max(0, finite(evidence.comparableExposures, 0));
    const styleExposures = Math.max(0, finite(evidence.styleExposures, comparable));
    const failures = Math.max(0, finite(evidence.failureCount, 0));
    const stalls = Math.max(0, finite(evidence.stallCount, evidence.stalled ? 1 : 0));
    const loadingBlocked = Math.max(0, finite(evidence.loadingBlockedCount, 0));
    const phaseChanged = context.previousPhase && context.phase && context.previousPhase !== context.phase;
    const t = exerciseTraits(ex, role, context);

    // A new phase is a legitimate reason to change method immediately because the prescription itself
    // changed. This is not method thrashing; it is periodization.
    if (phaseChanged && baseline.style !== current) {
        if ((evidence.fatigueLimited || evidence.techniqueLimited) && ADVANCED_STYLES.has(baseline.style)) {
            const simple = accumulateStyle(t);
            return selection(simple, 'high', 'The phase changed, but recent fatigue/technique limits make a simpler progression safer than an advanced loading method.', 'adaptive');
        }
        return selection(baseline.style, baseline.confidence, `The phase changed, so Auto re-selected the progression to match the new rep/effort structure. ${baseline.reason}`, 'adaptive');
    }

    // Before changing a method inside a phase, require repeated comparable evidence.
    if (comparable < 3 || styleExposures < 2)
        return selection(current, 'moderate', 'Auto is keeping the current progression until at least three comparable exposures establish a reliable trend.', 'adaptive_hold');

    if (evidence.fatigueLimited || evidence.techniqueLimited) {
        const simple = accumulateStyle(t);
        if (ADVANCED_STYLES.has(current) && simple !== current)
            return selection(simple, 'high', 'Repeated fatigue/technique-limited evidence makes the advanced method unnecessarily noisy; Auto is simplifying progression.', 'adaptive');
        return selection(current, 'high', 'The issue is fatigue/technique rather than the progression model itself, so Auto is not changing a simple method.', 'adaptive_hold');
    }

    // Linear progression is a starting strategy, not a permanent identity. Graduate a novice only after
    // repeated stalls/failures, then use the same exercise-specific accumulation rule as an experienced lifter.
    if (current === 'linear' && (stalls >= 2 || failures >= 2)) {
        const next = accumulateStyle(t);
        return selection(next, 'high', 'Repeated comparable stalls show that simple linear load jumps are no longer the best fit; Auto is graduating this exercise to rep-based progression.', 'adaptive');
    }

    // e1RM and waves need observed effort/strength data. If the athlete is not supplying enough usable
    // effort data, fall back instead of pretending the estimates are precise.
    if (ADVANCED_STYLES.has(current)) {
        const rirCoverage = Math.max(0, Math.min(1, finite(evidence.rirCoverage, 1)));
        const e1rmSamples = Math.max(0, finite(evidence.e1rmSamples, comparable));
        if (rirCoverage < .5 || e1rmSamples < 2) {
            const next = accumulateStyle(t);
            return selection(next, 'high', 'There is not enough comparable effort/e1RM evidence to justify the advanced method, so Auto is reverting to a robust rep/load progression.', 'adaptive');
        }
    }

    // Coarse/bodyweight loading that repeatedly hits an inventory wall should shift toward reps before
    // asking the athlete to review the same impossible load jump over and over.
    if (loadingBlocked >= 2 && t.bodyweight && current !== 'ladder')
        return selection('ladder', 'high', 'Repeated loading-inventory limits make rep-ladder progression a better fit than asking for unavailable load jumps.', 'adaptive');

    // If the current method is working, keep it. Successful progression is evidence against unnecessary
    // novelty even when another method would also be defensible.
    if (evidence.successful)
        return selection(current, 'high', 'Recent comparable exposures are progressing, so Auto is preserving the current method.', 'adaptive_hold');

    // When repeated stalls exist on a non-linear simple method, re-run the exercise/phase selector. Only
    // switch if the recommended method is meaningfully different; otherwise the problem is probably load,
    // fatigue or exercise fit rather than the progression family.
    if ((stalls >= 2 || failures >= 2) && baseline.style !== current)
        return selection(baseline.style, 'moderate', `Repeated stalls justify reconsidering the method. ${baseline.reason}`, 'adaptive');

    return selection(current, 'moderate', 'Auto found no strong evidence that changing progression method would improve the prescription.', 'adaptive_hold');
}

export function progressionInstruction(style) {
    switch (style) {
        case 'linear': return 'Linear progression: after a complete in-range exposure, add the smallest available load increment; hold or regress only when effort/performance misses the prescription.';
        case 'ladder': return 'Rep ladder: build reps through the prescribed range across exposures, then add the smallest load increment and return toward the lower rep target.';
        case 'dynamic': return 'Dynamic double progression: advance reps set-by-set inside the range; increase load once the prescription is owned at the intended effort.';
        case 'wave': return 'Wave loading: progress the performance trend across a short heavy/moderate exposure wave; use load increases only when the wave improves at comparable effort.';
        case 'e1rm': return 'e1RM autoregulation: use comparable effort-adjusted strength estimates to select the next practical load while preserving the phase rep/RIR target.';
        case 'double': return 'Double progression: add reps within the range, then add the smallest practical load increase once all prescribed work is achieved at target effort.';
        default: return 'Auto progression: build the prescribed work at the planned effort before increasing load.';
    }
}
