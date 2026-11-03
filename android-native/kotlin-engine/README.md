# Pursuit Iron — independent Kotlin training engine

**Quality bar:** [PRODUCT_SUPERIORITY_GATE.md](../PRODUCT_SUPERIORITY_GATE.md) makes every feature and every new engine decision subject to independently verified improvements over the existing web app. Legacy parity is not an acceptance target. The latest independent Kotlin loading and evidence tests are passing, but the engine is not connected to active native training.

This module is a **headless engine-rebuild laboratory** on the isolated
`architecture/kotlin-engine-rebuild` branch. It is **not connected** to
Compose, the existing canonical JavaScript training engine, the web app,
saved user workouts, the Android preview APK or persistence.

The M5 native-core APK was successfully built and uploaded at
https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41576280920.
Its source/Kotlin jobs passed, but real Android device acceptance remains
pending. This Kotlin work can proceed independently while testing occurs.

## First implemented domain: precise equipment feasibility

- Explicit `LB`/`KG`; no implicit conversion.
- Explicitly declared barbell **total** (bar + symmetric physical plate
  pairs), dumbbell **per-hand** values, Smith **total** loads, machine/cable
  stacks, and exercise-specific confirmed values.
- `BigDecimal` up to three fractional decimal places; no float drift,
  silent 159.5 lb rounding, inferred plate stock, invented machine sizes
  or reinterpretation of a logged zero as bodyweight.
- Bounded exploration of feasible barbell plate combinations and stack
  steps, validation of duplicated equivalent plate sizes and malformed
  inventories. An unconfirmed/mismatched source fails closed.
- Inspection reports exact feasibility and neighboring **possible**
  loads. It never changes a lifter's recorded load, selects a working
  weight, or proposes new exercise targets.

The module deliberately has no link to the app's engine routing or
journal. Kotlin rules and test expectations must be independently
specified, not copied from flawed current engine output.

## Next domains

1. Working-set evidence and conservative double-progression eligibility,
   with actual versus target RIR strictly distinguished.
2. Explicit fatigue/reps-across-sets model; equipment quantization
   integrated **after** earned-progression decisions, never before.
3. Per-muscle MEV/MAV/MRV, separate biceps/brachialis and wrist
   flexion/extension, session time bounds and programming feasibility.
4. Program generation, adaptive cycles, custom exercise/setting parity,
   readiness and injury adjustments, deterministic history replay.
5. Full independent acceptance followed by a separate engine ownership
   switch, preserving original targets and user history.

**No automatic backend fallback, no migration and no release approval.**

## Second implemented domain: immutable progression evidence (no load proposals)

`DoubleProgressionEvidenceDomain.assess` examines the exact original
working-set ordinals and the actual, separately recorded load/reps/RIR. It
does not treat the prescribed RIR as a reported one; logged skips, unresolved
sets and explicit zero-rep failed attempts have different meanings.

The reported regression, **200×3 / 205×3 / 210×2 / 215×2 with 5–8 targets**,
is independently classified as *below the rep floor*, not a reason to
promote all subsequent sets to 215. A repeated 215×2 near failure likewise
is not recognized as an earned increase. Meeting the ceiling at mixed loads
does not prove that all sets can perform the heaviest load. Even uniform
ceiling evidence means **review eligibility only**, never automatic load
selection. Training rules, RIR thresholds and fatigue model still require
additional independent specification before the Kotlin engine can prescribe.

This phase cannot mutate existing training records or displace the canonical
JavaScript engine, including for sessions already underway.

### Numeric-normalization regression

The first independent CI identified two failed assertions caused by
`BigDecimal.equals` scale distinctions (`115` versus `115.0`) despite
equal physical weights. The loading-result boundary now normalizes to
plain decimal, never scientific notation, and tests include a 1,000 lb
value, trailing-zero inputs and monotonic feasibility. No stored workout
weight is rewritten. This is an independently tested correctness fix,
not a fallback to JS parity.
