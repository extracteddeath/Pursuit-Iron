# Pursuit Iron — JavaScript vs Kotlin superiority certification

**Release blocker, not a migration parity checkbox.** The goal is a genuinely
better end-to-end coach and native product, not a line-by-line rewrite or a
merely safer collection of headless experiments. This comparison has two
distinct baselines: (1) the actual legacy JavaScript product at a pinned
commit, and (2) independently specified, coach-defensible expected decisions.
Neither engine gets credit for producing the other engine's output.

## Source-of-truth benchmark files

- `verification/engine-superiority-cases.json`: 33 fixed, independently
  stated scenarios (49 required paired proofs) spanning progression, equipment,
  programming, custom programs/cycles, coaching, history, migration and
  Android. The critical rep-jump case is a release blocker, not a claimed
  measured improvement.
- `verification/engine-superiority-evidence.json`: now contains eight executed
  headless pairs, with exact engine revisions, common input fingerprints and
  the content digest of the raw measurement artifact. Other pairs remain
  unmeasured. Do NOT fill it with reported defects, source comments, existence
  of Kotlin classes or impressions from screenshots.
- `verification/paired-progression-measurement.json`: complete raw native and
  JavaScript policy results, both engines' source-tree identities, exact
  previous/latest actuals and issued targets, independent oracle assessments
  and runner/fixture content fingerprints. Audit/release refuses altered,
  mismatched or stale evidence for this tranche.
- `verification/engine-superiority-gate.mjs --audit --write-report`: checks
  matrix/evidence structure, reports unmeasured pairs, publishes a
  non-certifying CI artifact; incomplete measurement is not CI failure.
  When Kotlin policy source or its comparison-runner files change, old measured
  headless results remain independently artifact-verified but are excluded
  from CURRENT pair counts,
  explicitly marked stale, and do not prevent separate engine/unit tests.
  `--release` still refuses stale source. CI uses a clean, pinned-legacy
  `--check-current` pair execution to grade present behavior without claiming
  that unreleased fresh results were recorded as accepted evidence.
- `verification/engine-superiority-gate.mjs --release`: fails closed until
  every required matched pair is measured and Kotlin passes every domain,
  improvement is demonstrated in three or more distinct domains, there are
  no measured regressions, and explicit user acceptance is documented.
- `--self-test`: verifies that missing results, mismatched inputs, duplicate
  evidence and equal passing engines cannot be mislabeled superiority.

The comparison gate is intentionally a final approval requirement, not part
of the current `app` runtime. Passing the ordinary Kotlin CI is **not**
passing this release gate.

## First executed progression tranche — 2026-11-07

The web engine is frozen at `903b4a78564590d8983e651cbe19d81acd1efb9c`;
the native policy/runner revision is pinned by the artifact's
`engines.independent-kotlin.commit` and source-tree identity. The shared ASCII Properties
fixture contains four working sets at a 5–8 practical rep band, prescribed
RIR 2, an explicit 45-lb bar and finite plate-pair inventory. Earlier capacity
is a controlled fixture, not an inferred or imported user workout.

| Frozen scenario | Web load review | Kotlin load review | Independent headless result |
| --- | --- | --- | --- |
| Mixed 200/205/210/215 at 3/3/2/2 | 180 lb | Historical 195 lb | Both pass |
| Repeated 215 at 2 reps near failure | 185 lb | Historical 200 lb | Both pass |
| Two full 200-lb ceiling sessions with actual RIR | 205 lb | 205 lb | Both pass |
| Declining 8/7/5/3 at 200 lb | 175 lb | Historical 195 lb | Both pass |
| 200-lb ceiling sets with actual RIR missing and original goal RIR 2 | 205 lb | 205 lb, explicitly assuming goal RIR | Both pass; logged actual RIR remains null |

The first four passes do **not** reproduce the old reported 215-for-every-set
defect in the frozen current web policy. Different downward reviews can pass:
the independent oracle requires a concrete buildable lower load after the
specified rep-floor failures and the smallest authorized increment after
earned work; it does not declare either reduction physiologically optimal.
**Corrected effort policy:** missing actual RIR may use the original goal RIR
as an explicit progression assumption. It does not become measured effort in
history. Reported values, especially 0/1 RIR, override the goal; missing both
sources cannot invent a default. The previous strict oracle incorrectly
counted Kotlin's refusal to progress as an advantage. That claimed improvement
is withdrawn under the user's clarified policy. The shared fixture versions
this choice as `assume_original_goal_when_unreported`.

These are headless **next-exposure load-review policies**, not issued-workout,
history persistence, full native onboarding or Android-device proofs. The web
entry point evaluates the latest completed exposure; native increase/recovery
policies inspect the supplied previous and latest exposures. The exact same
frozen history is available to both adapters. Both actual/target snapshots and
all set identities are checked after execution. A blanket refusal cannot pass
the earned-increase or concrete-recovery cases.

The original five load cases remain parity. The current gate also includes
three new paired cases described below, for **8/49 required paired proofs**,
one measured improvement domain (progression), and no measured regressions.
The verdict remains **NOT_PROVEN_BETTER**. The 41 missing pairs, improvement
in at least three domains, blinded coach assessment, device acceptance and
explicit user acceptance remain open.

**2026-11-07 evidence refresh:** both executable tranches were run directly
from frozen candidate `6ff4618ed9e38f6e18e010d6413f8b84b177e3cb` using the real
Kotlin production sources and a JDK/Kotlin compiler, alongside the clean pinned
web checkout. Raw measurements retain that actual executed commit and exact
policy-tree/runner digests. The ledger independently validates both artifacts;
there are zero stale pairs. CI reruns both tranches using Gradle. This does not
add app-issuance, program-quality or device proof.

## Rep-target and physical-stock tranche — 2026-11-07

`verification/paired-functional-measurement.json` records four rep-target
subscenarios and three actual-equipment queries as three matrix cases. Both
engines receive the same frozen shared Properties input. The independent
oracle enforces the already documented single-capable-set rep rule and
enumerates physical stock without using either engine's answer.

| Scenario | Web | Kotlin | Measured conclusion |
| --- | --- | --- | --- |
| 14/13/12 at 30 lb, 10–15 range, actual RIR 3/3/2 | 15/14/13 | 14/14/12 | Kotlin follows the one-set/one-rep guard; web advances every row |
| Same reps, actual RIR 2/2/2 | 15/14/13 | Hold 14/13/12 | Kotlin does not infer extra reserve |
| Same reps, actual RIR missing, original goal 2 | 15/14/13 | Hold 14/13/12 | Kotlin preserves missing actuals and uses only the original-goal assumption |
| Same reps, actual RIR 3/3/0 | Hold | Hold | Parity; near failure takes precedence |
| Machine requests 159.5 and 38.5 lb in known 5-lb stock | 155/35 lb, next 160/40 | Same | Parity; reported phantom-weight bug not reproduced |
| 35-lb bar, finite 10- and 2.5-lb plate pairs, request 47.5 lb | 40 lb, next 55 | Same | Parity with the actual bar and stock |

The rep result is a **narrow, measured policy improvement against the agreed
acceptance rule**. It is not proof of improved strength/hypertrophy outcomes,
better generated programs or a usable full product. A +3 jump inside one set
was not reproduced in this fixture. A blanket refusal cannot pass: the
headroom scenario requires one actual improvement on a demonstrably capable
set. The other seven matrix cases pass on both engines.

To reproduce with JDK 17, Android API 37 and Gradle 9.3.1 available:

```sh
git worktree add --detach ../pursuit-iron-web-comparison 903b4a78564590d8983e651cbe19d81acd1efb9c
node --test android-native/kotlin-engine/verification/paired-progression-test.mjs
node android-native/kotlin-engine/verification/run-paired-progression.mjs --legacy-root ../pursuit-iron-web-comparison --check-recorded
node android-native/kotlin-engine/verification/run-paired-functional.mjs --legacy-root ../pursuit-iron-web-comparison --check-current
node android-native/kotlin-engine/verification/engine-superiority-gate.mjs --audit
```

CI checks out the same frozen web revision, runs both actual policies afresh
and compares their raw results with the recorded artifact. Its generated run
outputs are attached separately. Local re-recording uses `--record`. An exact
CI artifact can also be promoted after independently checking its ZIP digest,
raw bundle, executed commit, current policy tree and every runner hash through
the same validators. CI does not silently rewrite the evidence ledger or
confer user acceptance.

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
| Single-set rep-target guard | Real policy increases all three rows in 3 of 4 shared subscenarios | Actual Kotlin policy advances one capable set or holds without sufficient reserve | **Yes for this specific policy rule:** no training-outcome or complete-engine superiority claim |
| Overloaded progression (200/205/210/215 for 3/3/2/2) | Frozen headless policy reviews 180 lb; the old reported 215-for-every-set defect was not reproduced | Native typed policies review the fixture's demonstrated 195 lb | **Parity for this headless safeguard:** no load-quality or end-to-end superiority claim |
| Missing actual RIR despite a prescribed target | Frozen headless policy offers 200→205 | Native review also offers 200→205 using an explicit original-goal assumption; actual RIR stays null | **Headless parity:** the earlier claimed Kotlin advantage is withdrawn |
| Verified exercise equipment and weight increments | Live web engine has broad equipment support; user-reported phantom 38.5/159.5 recommendations | `EquipmentLoadDomain` uses BigDecimal, confirmed plate pairs, machine stack and exact implement basis | **Not yet:** narrow headless correctness is encouraging, full supported-catalog breadth and app issuance are not proven |
| Recovery-week per-set intensity | No matched web execution yet; cannot infer behavior from old bug reports | Kotlin candidate now flags higher rep floors/ceilings at unchanged load and refuses unmatched set comparisons despite fewer sets | **Not yet:** requires matched executed headless and Android proofs |
| Custom program and cycle authoring | Existing live working product has many features, with reported amplifier and volume-loss defects | Separate first-class custom authoring, candidate transitions and review contracts; not fully production-integrated | **No** until complete parity plus better authoring and block outcomes on device |
| Revision-fenced longitudinal review | Historical source revisions can change across sessions; legacy outcome is unmeasured | Kotlin review now rejects changes to athlete/program revision, not just IDs or gym source | **Not yet:** requires paired persisted-history and Android proofs |
| Conflicting redo after acknowledgment loss | Requires a fresh identical event-ID/different-payload experiment in the legacy product | Candidate Kotlin segmented root now binds the full typed correction and has a disk-level stale-WAL regression | **Not yet:** paired engine and physical-device experiments remain required |
| Paired root/index recovery | No pinned matched behavior yet for the live web source | Isolated checksum-framed candidate checkpoint binds the complete authenticated segmented root to both workout/receipt index trees; crash and lost-ack tests passed in native CI | **Not yet:** full single-writer cutover, backup/restore and real Android device proofs are missing |
| Native history integrity | Existing web and JS-native paths support actual historical workouts | v2 native history and candidate segmented root, replay, redo, correction and lookup tests; segmented candidate is inactive and v2 is capped at 4,096 entries | **No:** missing single-authority cutover and 4,097+ actual persistence |
| End-to-end usability | Web product available for real workouts | Kotlin replacement is not the sole native coaching issuer; existing Compose integration can still depend on old engine | **No** until complete offline device journey and no JS fallback |

The separate `KNOWN_LEGACY_BUG_REGRESSIONS.md` ledger tracks 25 reported
regression areas. New Kotlin-only single-set rep targeting is tested for
no +3 jumps, no blanket all-set increases, missing effort, near failure,
unbuildable weights and altered set loading. The rep-target safeguard now has
a real matched policy execution. It remains **not** production parity or a
complete engine/device benchmark.

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
