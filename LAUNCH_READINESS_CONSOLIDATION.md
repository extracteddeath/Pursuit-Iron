# Pursuit Iron — Consolidated launch-readiness program

Baseline: main 93a6e43d8444 (Build 985; engine 0.65.9). This is a working checklist, not a declaration that the app is launch-ready.

## Delivery policy
- Batch related source changes on focused branches; avoid bumping the public build for every styling edit.
- Keep a passing main branch and use PR checks before integration. Do not alter the engine for presentation-only work.
- At a release checkpoint, regenerate the app/build identifiers, manifest hashes, service-worker revision and changelog together using the canonical release finalizer.
- Validate the *merged* commit and the hosted site's actual revision, not just the branch. No launch-ready claim until the final gate is complete.

## Styling and layout workstream
- Measure CSS size, !important declarations, dead selectors and fixed inline-style objects at each checkpoint; budgets may decrease, never silently increase.
- Consolidate by component owner, starting with Workout (timer, controls, set editor, rest), Program (cycle/preview and controls), then Home, Library, History, and dialogs.
- Prefer shared M3 tokens and semantic state/data attributes to fixed React style objects; retain genuinely dynamic geometry and progress.
- Verify dark/light/forced colors, 320–foldable widths, 200% text, safe-area insets, keyboard resize, touch targets, focus order and reduced-motion behavior.
- Avoid new overrides to hide underlying ownership conflicts; audit CSS cascade and remove unreachable rules only after reference checks.

## Engine lifecycle workstream
- Creation -> schedule -> exercise prescription -> set logging -> complete -> history -> progression -> next session -> cycle adaptation -> undo.
- Check equipment compatibility, machine/plate increments, fatigue-aware double progression, PR events only on actual records, RIR, amplifier timing, and program-volume constraints.
- Maintain deterministic seeded fixtures and browser-backed persistence/refresh tests. No hidden or automatic weight reductions.

## Final release gate
- Canonical release integrity and independent engine audit.
- Core contracts plus all seven browser shards, including workout, program, lifecycle, data, accessibility, motion and integration.
- Hosted app and service-worker cache hashes match main; no stale PWA update or build-number mismatch.
- Physical Android acceptance: rotation, IME, TalkBack, offline/resume, installed PWA upgrade, interruption and back gestures. Record device-tested versus simulated coverage explicitly.
- Ship only after high-priority regressions are resolved; record any remaining nonblocking issues and a rollback point.

## Status
- Baseline collected. Implementation and certification are ongoing.
