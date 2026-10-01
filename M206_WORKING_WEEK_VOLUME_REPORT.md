# M206 — Working-week volume integrity

Pursuit Iron 4.0.0 build 796 · Pursuit Engine 0.64.6.

Baseline: M205 build 795, commit `6789e3c8c250b3b9ffc4aa529122cbaeb2578ffa`. This completes the dose-guidance work interrupted after the legacy-engine cleanup.

## Result

The 13 configurations recorded by the M205 audit now have no displayed regional-volume findings and their executable work weeks fit their session limits. Four raw shell projections reproduce real rounding shortfalls; the new pass repairs them by changing accessory sets in the affected weeks. It preserves the roster, strength prescriptions, recovery week, rep/RIR/rest targets, progression methods, and technique cues. Already-valid standard plans retain their prescriptions.

Minimalist generation and public guidance now share the existing approach-aware dose model. The standard five-day accumulation floor no longer overrides an explicit minimalist selection. Standard-volume targets remain intact. A three-set cap travels with minimalist prescriptions through weekly rounding, simulation, locked phase retargeting, adaptive blocks, and conversion of older minimalist snapshots.

## Executable repair boundary

`finalizeGeneratedShellVolume` runs after projection at standalone generation, cycle creation, standalone-to-cycle conversion, history-driven next-block generation, and real cycle adaptation. It reuses `reconcileShellWeekDose`, which reads the same cells as Program, Home, and Workout.

The transaction considers a useful set increase or a surplus reduction first. When a single move cannot pass the complete safety audit, it tries a same-day transfer from one or two surplus accessory sets. Every accepted move improves the combined regional/time deficit, preserves prior safe regional and internal muscle envelopes, and introduces no new critical or major engine findings. It cannot modify user-owned set counts or strength slots. All proposals are immutable; a bounded search stops when no legal improvement remains.

Saved-plan Auto-fix uses this transaction before attempting a base-block change or an additional movement. Base repair, when needed, is followed by another exact-week finishing pass so regenerating touched cells cannot undo a correction. The existing canonical persistence boundary commits the returned program to both open and saved state.

The snapshot now reads executable supersets, rest, numeric rep/effort ranges, and the selected final-set technique. It shares catalog/request lookup context across weeks. Normal scalar cell reads avoid rebuilding a simulated session; ambiguous imported set shapes still use the engine fallback.

## Regression evidence

`verification/m206-working-week-dose-test.mjs` verifies:

- All 13 M205 configurations, including short Full Body, Strength, advanced hypertrophy, no bodyweight/supersets, minimalist, limited equipment, and explicit muscle priorities.
- Four reproduced rounded-week failures, successful direct repair, generation/Auto-fix parity, and idempotence.
- Immutable input and base engine prescriptions; stable exercise identity; strength, recovery week, and per-field ownership protection.
- Exact weekly time limits and consistent Program/Home volume and set totals after serialization/reload.
- Minimalist caps across six phases, locked/adaptive cycles, and conversion of an older snapshot without cap metadata.
- Manual-set constraints, impossible clocks, and incomplete data remain unable/partial rather than falsely successful.

The M199 capacity regression now fills every executable week to the tested base dose and reduces row dose enough to force a real transfer. Its former peak-week cap left legal slack in the lowest-volume weeks, which a correct week-specific repair could use without reallocation.

The new regression is part of current generation CI. Local release checks, the 71-route creation audit, 18-case regional audit, four locked-cycle regressions, 14 adaptation gates, and the quality/torture group passed. Browser tests passed Auto-fix click → canonical persistence → reload, 390/360-pixel workout inputs and custom final-set techniques, eight exercise previews, and the service-worker update lifecycle with training data preserved. These are Chromium phone-sized viewports, not physical-device certification.

## Limits retained honestly

This is a prescription/accounting repair, not new physiological evidence. Dose landmarks and clock estimates remain the engine's models. The 13 documented configurations passing does not establish that every possible request can satisfy every target. Tighter rosters, additional restrictions, or protected manual edits may leave a partial repair; unresolved regional/time findings remain stored and visible. Fallback consolidation also protects user-owned prescriptions from exercise removal. No warning is suppressed merely because base-engine generation passed.

The app's large main module and synchronous difficult generation remain separate maintainability/performance work. M205's cleanup and its historical timing results remain intact; M206 intentionally changes the affected weekly prescriptions.
