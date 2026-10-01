export const SUPPORTED_PROGRESSION_STYLES = ['auto', 'double', 'dynamic', 'ladder', 'linear', 'wave', 'e1rm'];

const ADVANCED_STYLES = new Set(['wave', 'e1rm']);

function finite(value, fallback = 0) {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
}

function explicitStyle(context = {}) {
    const requested = context.manualStyle ?? context.requestedStyle;
    return SUPPORTED_PROGRESSION_STYLES.includes(requested) && requested !== 'auto' ? requested : null;
}

// An explicit global choice wins; otherwise a retained lift keeps its own user-selected method.
// Auto methods remain eligible for phase/evidence-based re-selection.
export function continuationProgressionStyle(requestedStyle, previous) {
    return explicitStyle({ requestedStyle })
        ?? (previous?.progressionSelection?.source === 'manual'
            ? explicitStyle({ requestedStyle: previous.progressionStyle }) : null)
        ?? requestedStyle;
}

function repRangeFor(ex, role, context = {}) {
    const prescribed = context?.prescription?.reps;
    if (Array.isArray(prescribed) && prescribed.length >= 2) {
        const a = finite(prescribed[0], 1), b = finite(prescribed[1], a);
        return [Math.min(a, b), Math.max(a, b)];
    }
    const strength = role === 'primary_strength' || role === 'secondary_strength';
    const source = strength ? ex?.preferredReps?.strength : ex?.preferredReps?.hypertrophy;
    if (Array.isArray(source) && source.length >= 2) {
        const a = finite(source[0], 1), b = finite(source[1], a);
        return [Math.min(a, b), Math.max(a, b)];
    }
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
    const alternatives = (ex.equipmentAlternatives ?? []).flat();
    const equipment = new Set([...(ex.equipment ?? []), ...alternatives]);
    const barbell = !!ex.flags?.barbell || equipment.has('barbell');
    const bodyweight = !!ex.flags?.bodyweight || equipment.has('bodyweight');
    const quickChange = !barbell && ['dumbbell', 'dumbbells', 'machine', 'cable', 'smith', 'selectorized', 'leg_press'].some(x => equipment.has(x));
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
    // Technical stability and measurement reliability are not the same thing. A competition squat or
    // bench is technically demanding (low stability metadata) but is still highly standardized,
    // incrementable and measurable enough for e1RM/wave decisions when used as strength work.
    const measurementReliable = highlyLoadable && (stable || (barbell && strength));
    const noviceLinearEligible = compound && !bodyweight && loadability >= 5 && (stable || barbell);
    return {
        barbell, bodyweight, quickChange, compound, stability, loadability, reps, repWidth,
        stable, highlyLoadable, strength, measurementReliable, noviceLinearEligible
    };
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
 * Select Auto at program/cycle creation from the actual exercise, phase and athlete rather than one
 * program-wide default. Advanced methods are reserved for prescriptions with a trustworthy signal.
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

    if (phase === 'recovery' || phase === 'maintenance')
        return selection(accumulate, 'high', `${phase === 'recovery' ? 'Recovery' : 'Maintenance'} uses a simple rep/load progression so the method does not add fatigue or noise.`);

    if (experience === 'novice') {
        if (t.noviceLinearEligible)
            return selection('linear', 'high', 'A novice on a loadable compound can use simple load steps while adaptation is rapid; technical barbell lifts remain eligible because their loading is standardized.');
        if (t.bodyweight && t.repWidth >= 3)
            return selection('ladder', 'high', 'This novice bodyweight movement is better progressed through the rep range before external loading.');
        if (t.compound && t.quickChange)
            return selection('dynamic', 'moderate', 'Per-set loading is practical here, so a novice can progress uneven sets without forcing a full-load jump.');
        return selection('double', 'high', 'Double progression gives a novice a simple rep-first rule for this exercise.');
    }

    if (t.strength && t.compound) {
        const e1rmEligible = t.measurementReliable && t.reps[1] <= 5;
        const waveEligible = role === 'primary_strength' && t.measurementReliable && t.reps[1] <= 6 && blockWeeks >= 5;

        if (phase === 'peak') {
            if (e1rmEligible)
                return selection('e1rm', 'high', 'Peak strength work is low-rep and measurable enough for effort-adjusted e1RM autoregulation.');
            return selection(accumulate, 'moderate', 'This peak exercise does not provide a reliable enough low-rep loading signal for e1RM, so Auto keeps a simpler progression.');
        }
        if (phase === 'intensification') {
            if (waveEligible)
                return selection('wave', 'high', 'An experienced primary strength lift in a long enough intensification block can use a short loading wave.');
            if (e1rmEligible)
                return selection('e1rm', 'moderate', 'The lift supports e1RM autoregulation, but the block is too short or the role is not appropriate for a wave.');
            return selection(accumulate, 'moderate', 'Auto avoids advanced strength progression because the movement or rep target does not provide a reliable enough signal.');
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

    return selection('double', 'high', 'Isolation work uses rep-first double progression because it is simple and does not overreact to small performance noise.');
}

function validCurrent(style) {
    return SUPPORTED_PROGRESSION_STYLES.includes(style) && style !== 'auto';
}

/**
 * Re-select Auto once training evidence exists. Phase changes can legitimately change the method;
 * within a phase, repeated comparable evidence is required so Auto cannot thrash after one workout.
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

    if (phaseChanged && baseline.style !== current) {
        if ((evidence.fatigueLimited || evidence.techniqueLimited) && ADVANCED_STYLES.has(baseline.style)) {
            const simple = accumulateStyle(t);
            return selection(simple, 'high', 'The phase changed, but recent fatigue/technique limits make a simpler progression safer than an advanced loading method.', 'adaptive');
        }
        return selection(baseline.style, baseline.confidence, `The phase changed, so Auto re-selected the progression to match the new rep/effort structure. ${baseline.reason}`, 'adaptive');
    }

    if (comparable < 3 || styleExposures < 2)
        return selection(current, 'moderate', 'Auto is keeping the current progression until at least three comparable exposures establish a reliable trend.', 'adaptive_hold');

    if (evidence.fatigueLimited || evidence.techniqueLimited) {
        const simple = accumulateStyle(t);
        if (ADVANCED_STYLES.has(current) && simple !== current)
            return selection(simple, 'high', 'Repeated fatigue/technique-limited evidence makes the advanced method unnecessarily noisy; Auto is simplifying progression.', 'adaptive');
        return selection(current, 'high', 'The issue is fatigue/technique rather than the progression model itself, so Auto is not changing a simple method.', 'adaptive_hold');
    }

    if (current === 'linear' && (stalls >= 2 || failures >= 2)) {
        const next = accumulateStyle(t);
        return selection(next, 'high', 'Repeated comparable stalls show that simple linear load jumps are no longer the best fit; Auto is graduating this exercise to rep-based progression.', 'adaptive');
    }

    if (ADVANCED_STYLES.has(current)) {
        const rirCoverage = Math.max(0, Math.min(1, finite(evidence.rirCoverage, 1)));
        const e1rmSamples = Math.max(0, finite(evidence.e1rmSamples, comparable));
        if (rirCoverage < .5 || e1rmSamples < 2) {
            const next = accumulateStyle(t);
            return selection(next, 'high', 'There is not enough comparable effort/e1RM evidence to justify the advanced method, so Auto is reverting to a robust rep/load progression.', 'adaptive');
        }
    }

    if (loadingBlocked >= 2 && t.bodyweight && current !== 'ladder')
        return selection('ladder', 'high', 'Repeated loading-inventory limits make rep-ladder progression a better fit than asking for unavailable load jumps.', 'adaptive');

    if (evidence.successful)
        return selection(current, 'high', 'Recent comparable exposures are progressing, so Auto is preserving the current method.', 'adaptive_hold');

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
