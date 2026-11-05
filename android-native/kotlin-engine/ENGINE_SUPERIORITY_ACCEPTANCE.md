# Pursuit Iron — JavaScript vs Kotlin superiority certification

**Release blocker, not a migration parity checkbox.** The goal is a genuinely
better end-to-end coach and native product, not a line-by-line rewrite or a
merely safer collection of headless experiments. This comparison has two
distinct baselines: (1) the actual legacy JavaScript product at a pinned
commit, and (2) independently specified, coach-defensible expected decisions.
Neither engine gets credit for producing the other engine's output.

## Source-of-truth benchmark files

- `verification/engine-superiority-cases.json`: 25 fixed, independently
  stated scenarios (34 required paired proofs) spanning progression, equipment,
  programming, custom programs/cycles, coaching, history, migration and
  Android. The critical rep-jump case is a release blocker, not a claimed
  measured improvement.
- `verification/engine-superiority-evidence.json`: deliberately empty until
  both engines are actually run. Do NOT fill it with reported defects, source
  comments, existence of Kotlin classes or impressions from screenshots.
- `verification/engine-superiority-gate.mjs --audit --write-report`: checks
  matrix/evidence structure, reports unmeasured pairs, publishes a
  non-certifying CI artifact; incomplete measurement is not CI failure.
- `verification/engine-superiority-gate.mjs --release`: fails closed until
  every required matched pair is measured and Kotlin passes every domain,
  improvement is demonstrated in three or more distinct domains, there are
  no measured regressions, and explicit user acceptance is documented.
- `--self-test`: verifies that missing results, mismatched inputs, duplicate
  evidence and equal passing engines cannot be mislabeled superiority.

The comparison gate is intentionally a final approval requirement, not part
of the current `app` runtime. Passing the ordinary Kotlin CI is **not**
passing this release gate.

## Measurement method

1. Freeze the **actual current web engine** and the proposed **Android Kotlin
   engine** by exact 40-character commit SHA, together with versioned gym,
   program, athlete, schedule, exercise, journal and correction inputs.
   Preserve stable workout and exercise mapping. Never compare two different
   exercises, athlete circumstances, or target definitions as if identical.
2. Run each fixed scenario through the **real executable path** for each
   engine. Compare outputs against independently stated expectations, not
   numeric equality to JavaScript. Collect decision rationale, exact loads,
   reps, RIR, time estimates, training-dose results and native journal
   results. Save stdout/JSON output and a reproducible command/test artifact.
3. Give each result an identical SHA-256 scenario-input digest, separate engine
   revision, actual outcome (pass/fail), runner type (headless/device/blinded
   human), and artifact. The release gate refuses incomplete evidence. A source
   file containing a unit test is NOT a recorded head-to-head execution.
4. For native interactions, collect cold-start/offline real-Android evidence:
   first onboarding to workout to Finish and next session; custom cycle
   authoring and transitions; logged correction/Last/PR refresh; restart,
   interrupted session and backup/restore; physical loadability and data
   ownership; accessibility, phone/foldable and all original 20 themes.
   Device evidence cannot be replaced with Compose compilation.
5. Require at least two blinded independent coach reviews of the same
   generated program/cycle scenario. Review split suitability, dose, fatigue,
   variety, recovery, user constraints, workload and next-session quality.
   Aesthetics, impressive training vocabulary, or more sets alone do not
   prove coaching improvement.
6. Independent user acceptance is explicitly required. A failed P0 domain
   cannot be averaged away by passing easier cases. Kotlin must have no
   regressions where web passes and show demonstrable superiority in at least
   three distinct areas. Add further cases as new issues appear; do not remove
   inconvenient cases without documented consent.

## Immediate comparisons: what is and is not proven

| Area | Legacy JavaScript | Isolated Kotlin now | Can claim better? |
| --- | --- | --- | --- |
| Overloaded progression (200/205/210/215 for 3/3/2/2) | **User-reported** unearned 215-for-every-set recommendation; NOT yet remeasured on frozen web revision | Candidate `ConservativeProgressionReview` refuses increase; `OverloadRecoveryLoadReviewDomain` can offer a prior verified all-set load for review | **Not yet:** new logic is headless and no paired current web run exists |
| Verified exercise equipment and weight increments | Live web engine has broad equipment support; user-reported phantom 38.5/159.5 recommendations | `EquipmentLoadDomain` uses BigDecimal, confirmed plate pairs, machine stack and exact implement basis | **Not yet:** narrow headless correctness is encouraging, full supported-catalog breadth and app issuance are not proven |
| Custom program and cycle authoring | Existing live working product has many features, with reported amplifier and volume-loss defects | Separate first-class custom authoring, candidate transitions and review contracts; not fully production-integrated | **No** until complete parity plus better authoring and block outcomes on device |
| Native history integrity | Existing web and JS-native paths support actual historical workouts | v2 native history and candidate segmented root, replay, redo, correction and lookup tests; segmented candidate is inactive and v2 is capped at 4,096 entries | **No:** missing single-authority cutover and 4,097+ actual persistence |
| End-to-end usability | Web product available for real workouts | Kotlin replacement is not the sole native coaching issuer; existing Compose integration can still depend on old engine | **No** until complete offline device journey and no JS fallback |

The separate `KNOWN_LEGACY_BUG_REGRESSIONS.md` ledger tracks 25 reported
regression areas. New Kotlin-only single-set rep targeting is tested for
no +3 jumps, no blanket all-set increases, missing effort, near failure,
unbuildable weights and altered set loading. That is one headless safeguard,
**not** production parity or a completed matched JS/Kotlin benchmark.

The user-reported bugs above are essential regression scenarios, **not**
a claim that the current JavaScript commit still reproduces each issue.
Retest the web engine directly instead of relying only on old reports.

## Current next engineering gates

1. Finish a source-backed **real Kotlin issued-workout path** (without hidden
   JS fallback), and preserve every logged actual, revision and correction.
2. Replace the bounded migration-only identity table and 4,096-entry v2
   manifest with a scalable indexed, exclusive single-writer protocol.
   Atomically integrate root/index/redo under one authorized owner and test
   power-loss, directory sync, backup/restore and old-record editing.
3. Complete all 14 FeatureCatalog capabilities and the E01–E41 requirement
   ledger, including gym-aware loads, expert programming, custom authoring
   and cycle transitions, actual RIR/techniques, live adaptation and Insights.
4. Run complete paired legacy vs Kotlin cases, then blind coach review,
   production-data import rehearsal, physical-device accessibility tests
   and user acceptance before any engine or product cutover.

**Prohibited shortcuts:** marking a scenario pass because code compiles,
assuming the old app is wrong because of a stale bug report, hiding
unavailable equipment or RIR as zero, silently excluding an unfinished
feature, creating a second history writer, altering the live web branch,
or calling a synthetic self-test a real engine comparison.
