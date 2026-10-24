# M348 — Observed volume response, not assumed dose causality

M347 correctly blocked automatically adding sets just because a lift plateaued. M348 adds a conservative, explainable **observation** that a *recorded and comparable* set-count change was followed by improving, flat or declining performance.

## Evidence gate

The model checks six chronologically ordered, program-owned, unique, complete workouts: three before and three after a stable set-count change, using the same exercise, session, role, rep/RIR targets and progression method. Workouts need effort evidence and good technique, a consistent approximately weekly schedule, and no recorded failure, discomfort or context limitation. Both periods must have consistent set counts; the change must be within a reviewable 15–40% and at least one set per workout; the baseline must have been stable. For multi-exercise muscle sessions or changed training schedules/rosters, it returns **insufficient** instead of a misleading conclusion. The final evaluation is bounded to the six most recent comparable muscle sessions.

The resulting `muscles[muscle].observedVolumeResponse` describes `improvement_associated`, `decline_associated`, or `no_clear_benefit`; its `confidence` is explicitly `limited_observational`, `interpretation` is `association_not_causation`, and `autoApply` is always `false`. Reports include the recorded session set counts and within-period effort-adjusted estimated strength trends, without claiming a causal effect or recommending a particular number of sets.

## Boundaries

- **No automatic volume increase or reduction from the new observational result.** Existing M231/M347 bounded fatigue-response reductions still work independently.
- Do not conflate exercise-level set count with overall muscle weekly dose when other exercises train that muscle; such cases return insufficient.
- A pre-existing upward strength trend, different effort, missing data, edited program, exercise swap, change in progression policy, irregular schedule or wide set-count jump cannot certify a clean comparison.
- Observed estimated strength is only a proxy for performance, not proof of hypertrophy, strength adaptations or dose response. User judgment and prospective controlled trials remain appropriate before modifying volume.

## Certification

`verification/m348-observed-volume-response-test.mjs` is the thirteenth mandatory end-to-end engine audit stage. It checks response classifications, confounding exclusions, history immutability, duplicate/foreign record exclusion and **unchanged training prescriptions** across strength, hypertrophy and mixed goals. M231 and M347 regression suites must also continue passing.

Run `node scripts/audit-training-lifecycle.mjs --report verification/engine-lifecycle-audit.json`. The canonical `scripts/finalize-release.mjs` was run with passing release verification to refresh the production service-worker cache and release hashes.
