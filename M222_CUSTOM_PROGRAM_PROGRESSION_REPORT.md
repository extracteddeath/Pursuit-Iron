# M222 — custom programs and workout progression

Pursuit Iron 4.0.0 build 812 · Pursuit Engine 0.64.12.

Custom programs now send their authored prescription and scoped history into the same performance evaluator as generated programs. This removes the separate custom double-progression formula that ignored selected methods, filled held loads at the rep ceiling, and lacked the engine's complete-exposure/effort safeguards. Program structure remains user-authored.

## Corrections

- Working rep targets follow each set position's own prior performance. Fresh Cable Fly test evidence 25×19 / 25×10 / 20×11 / 25×10 produces double-progression targets 25×20 / 25×11 / 25×10 / 25×11 within 10–20. Dynamic progression retains positional loads and produces 25×20 / 25×11 / 20×12 / 25×11.
- Reduced loads rebuild at the range floor. No theoretical high-rep estimate jumps a lighter suggestion straight to the ceiling. Shell equipment rounding updates the banner and each working target together.
- Load increases require a full controlled top-range exposure at the qualifying load; lighter backoff sets cannot qualify the heaviest load. Incomplete and effort-limited exposures retain the canonical safeguards. Dynamic retains the existing full-exposure load-increase safety policy while progressing positional reps/loads.
- Custom slot methods and explicit exercise methods reach both the style label and runtime. Auto uses the engine selector. Generated history bridges carry engine-owned positional targets into Workout.
- Advice uses the evaluator's decision and cites the prior working-set count, avoiding an averaged weight/reps pair labeled as a real prior set. Mixed dynamic prescriptions show their load range.
- Recovery refreshes untouched app-owned automatic weights and reps together. Logged, manual, locally edited, added, warmup and extension rows remain protected. Explicit average-load actions are marked user-owned. Reconciliation is immutable/idempotent. The unused custom-only load-refresh helper was removed.
- Compact weight fields scale their digits for long/decimal entries so 125.5 remains visible at 320 px.
- Custom settings apply atomically without resizing or rebalancing the authored roster. Disallow supersets persists and suppresses workout pairing. Existing author-selected last-set techniques remain intact across settings, later weeks and backup reads.

## Validation

New engine/runtime and production browser regressions cover the supplied Cable Fly pattern, mixed dynamic targets, lowered-load rep floors, unsafe effort/incomplete/mixed-load promotions, canonical-evaluator equivalence, protected recovery/manual typing, no-superset settings, and late-week Myo/Drop realizations.

Existing release integrity checks, 28 pre-existing engine contract gates (8 generation, 17 adaptation, 3 quality), and the standalone legacy-dose/cycle bridge were run. The new M222 gate is now included in the adaptation and release suites. Existing browser gates cover PWA update/storage preservation, volume repair, prescription editing/logging, cycle duration, interruption/save recovery, phone layouts/release notes, theme/audio lifecycle and day-scoped progression; M222 adds the custom progression browser gate.

Two historical assertions were updated for intended behavior: custom suggestions no longer default to the rep ceiling, and release-note tests count the actual current notes rather than assuming every release contains five items. The new regression and all existing browser gates must pass in CI.

Browser testing uses headless Chromium at phone viewports; it does not certify physical Android hardware behavior. Custom authored schedules remain explicit, and this release does not claim that the app automatically generates new week-by-week periodization for an authored roster.
