# Build 1004 — Preserve working-set positions in double-progression holds

## User-visible defect and cause

The Workout's `sessionSuggestion` boundary previously constructed its prior load list with `completedHistorySets(perf).map(...)`. This compressed away skipped/incomplete set rows. When explicit logged positions were `0, 2, 3`, the code treated them as `0, 1, 2`. A three-set double-progression hold could therefore prefill set 3 from historical set 4's weight, despite the source trace correctly identifying historical set 3.

## Runtime change

Use the existing canonical `previousWorkoutSet(perf, index)` position-aware history lookup in both double-progression branches: (1) converting an automatic decrease into a user-optional reduction with held loads, and (2) retaining per-position prior weights during `add_reps`, `hold` and `hold_load` decisions. Missing positions retain a gap; no later set supplies their supposed prior load. In the opt-in correction path, an unobserved position uses the lowest actually observed prior working load as a conservative prefill rather than pretending another set was its history.

The `retainDoubleProgressionSetLoads` helper now accepts sparse positional observations without allowing them to rewrite other positions. Completed full histories, manually owned sets, percent protocols and earned increases retain their existing policies.

## Reproduction and acceptance

`verification/custom-progression-safety-test.mjs` includes a partial-history fixture for three prescribed sets with an incomplete second set and a completed *fourth* historical position. It verifies set 3 continues at its true prior weight rather than inheriting set 4's weight, and cross-checks the Workout prefill and the persisted decision trace. A separate pure helper fixture proves a null middle position remains unclaimed. Inputs are never mutated.

## Release evidence and remaining gates

PR #434 completed seven draft Chromium browser shards and source contracts on the exact source head. The monotonic release stage finalized Build 1004, `RELEASE_MANIFEST.json`, `BUILD_PROFILE.json`, `modules/App.js`, `index.html`, and `sw.js`. Engine version remains 0.65.9 (runtime patch, no engine-version declaration change).

Full ready-for-review independent source/engine/browser/host/release checks and all post-merge GitHub Pages/offline checks remain mandatory for publication. **This pass fixes one positional source-of-truth bug, not the complete launch blocker #418.** Physical Android testing, broad gym/plate parity and all progression/PR outcome acceptance criteria remain open.
