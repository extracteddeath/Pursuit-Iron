export const SUPPORTED_PROGRESSION_STYLES = ['auto', 'double', 'dynamic', 'ladder', 'linear', 'wave', 'e1rm'];
/**
 * Seven strategy families, selected by v661's DOCUMENTED table (the comment above used to claim this; the rules had drifted).
 *
 * Reported: "the engine picks double progression instead of dynamic double progression like it normally would". Measured
 * against v661's autoStyleDetail, the resolver had diverged in five places:
 *   - hypertrophy compounds on machines/dumbbells got DOUBLE; v661 gives per-set DYNAMIC double progression — each set
 *     settles on its own load, and a pin or dumbbell swap between sets is instant;
 *   - barbell compounds got LADDER; v661 deliberately dropped the ladder for auto on a barbell ("stripping plates between
 *     sets is a chore", and a shared ladder target "can read as do fewer reps than your best") in favour of DOUBLE, which
 *     reads the last, most fatigued set;
 *   - strength primaries got WAVE in accumulation; v661 accumulates first (double on a barbell, dynamic otherwise) and
 *     waves only mid-block;
 *   - strength primaries got e1RM in intensification; v661's mid-block is the WAVE, e1RM is the peak;
 *   - beginner compounds got linear only as strength primaries; v661 gives every beginner compound LINEAR.
 * Block position comes from the phase: accumulation phases are v661's early block (phase < 0.35), intensification its
 * mid-block (0.35-0.75), peak its late block (>= 0.75). Two v661 rules need data the realizer does not have and are NOT
 * reproduced here: short blocks (<= 4 weeks) skipping the wave, and a stalled beginner graduating from linear.
 * gates: integration/m116-progression-style-check.mjs pins this table and checks generated programs against it.
 */
export function resolveProgressionStyle(ex, role, context) {
    const { phase, experience } = context;
    const compound = !!ex.flags.compound;
    const barbell = !!ex.flags.barbell;
    /* v661 accumStyle: one weight on a barbell (double), per-set loads elsewhere (dynamic). */
    const accumulate = compound && barbell ? 'double' : 'dynamic';
    if (experience === 'novice')
        return compound ? 'linear' : 'double';
    const strength = role === 'primary_strength' || role === 'secondary_strength';
    if (strength && compound) {
        if (phase === 'peak')
            return 'e1rm';
        if (phase === 'intensification')
            return role === 'primary_strength' ? 'wave' : 'e1rm';
        return accumulate;
    }
    if (compound)
        return accumulate;
    return 'double'; /* isolation: v661 — "double progression is simpler and sufficient for single-joint work" */
}
export function progressionInstruction(style) {
    switch (style) {
        case 'linear': return 'Linear progression: after a complete in-range exposure, add the smallest available load increment; hold or regress only when effort/performance misses the prescription.';
        case 'ladder': return 'Rep ladder: build reps through the prescribed range across exposures, then add the smallest load increment and return toward the lower rep target.';
        case 'dynamic': return 'Dynamic double progression: advance reps set-by-set inside the range; increase load once the prescription is owned at the intended effort.';
        case 'wave': return 'Wave loading: progress the performance trend across a short heavy/moderate exposure wave; use load increases only when the wave improves at comparable effort.';
        case 'e1rm': return 'e1RM autoregulation: use comparable effort-adjusted strength estimates to select the next practical load while preserving the phase rep/RIR target.';
        case 'double': return 'Double progression: add reps within the range, then add the smallest practical load increase once all prescribed work is achieved at target effort.';
    }
}
