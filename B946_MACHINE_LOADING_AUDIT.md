# B946 — physical load-step consistency

## Reported regression
A configured 5 lb Machine Shoulder Press showed automatic target rows of 115.5, 110, and 99 lb after a previous exercise exposure at 120 lb. Those fractional values are not available on the configured machine.

## Root causes and repairs
- The canonical workout runtime previously forwarded per-set dynamic weights into *working* rows without applying the supplied equipment-snap callback. The callback was only used for warm-ups. All new automatically produced working rows now use the same canonical loadability boundary.
- A saved Next Engine machine inventory could disagree with the current program's per-exercise machine increment, so the progression evaluator could calculate an impossible target before the UI received it. Source snapshots and workout prescriptions now overlay the program's exercise-specific step on the normalized native-unit inventory. A specific finite exercise rack remains authoritative.
- Automatically generated drop weights in the legacy prescription adapter also now snap to loadable equipment settings instead of multiplying a load and displaying the raw fractional result.

## Regression verification
Release gate: `verification/b946-machine-loading-grid-test.mjs`
- Given automatic raw dynamic targets 115.5 / 110 / 99 lb with a 5 lb machine step, working rows are 115 / 110 / 95 lb; input and target agree.
- A comparable prior session completing 120 lb across all three sets at >=8 reps and adequate effort retains 120 lb; there is no fabricated load reduction. Real underperformance, explicit readiness, and position-specific fatigue may still result in load adjustments.
- Engine progression next steps honor the configured 5 lb increment.
- Existing untouched automatic prefills are refreshed on session restoration; user-edited, completed, and other user-owned values are preserved.
- A specifically configured finite rack, kg unit handling, and separate equipment inventory rules remain intact.

## Release verification
Published as Build 946 via the one-shot release workflow; `scripts/finalize-release.mjs --next-build`, the B946 regression, and `scripts/verify-release.mjs` passed in the publishing workflow. Web deployment is a separate GitHub Pages status check.

This repair does not edit historical workouts, reset in-progress sessions, or reinterpret user-entered weights.
