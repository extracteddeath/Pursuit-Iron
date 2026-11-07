# What has actually improved?

The new Kotlin engine is **not yet proven better overall**, and the Kotlin app
is **not yet ready for ordinary workouts**. Tests protect particular behaviors;
their count does not establish app completeness or coaching quality.

## Correction: the one-set comparison was invalid

The athlete did not request that only one set progress at a time. The assistant
introduced that restriction and incorrectly called it an agreed rule. A grader
then rewarded Kotlin for obeying it. That is not evidence of better coaching.
The claimed rep-target improvement is **withdrawn** in the executable evidence
gate. Its original raw outputs remain available for transparency.

The last frozen comparison contained eight paired cases. Excluding that invalid
rep-quality proof leaves **seven historical comparisons showing parity**, with
no demonstrated improvement domain. Different downward load reviews do not
establish which prescription produces better training outcomes. Updated policy
source must be remeasured before any historical result counts as current proof.

## Actual engine correction

New rep reviews no longer impose a one-set or one-rep cap. At the same verified
load, each set gets an initial estimate at its intended RIR from its own logged
reps and reserve. For example, 14 reps at reported 3 RIR supports trying 15 at
2 RIR. Several sets may change; a set performed closer to failure than intended
may receive fewer reps instead of a forced increase. If the intended effort
cannot fit the rep floor, the review asks for a load review.

This is an explicit **starting estimate**, not a validated individualized fatigue
model. It does not yet quantify changed actual rest, prior exercise work,
extra fatigue from harder early sets, or chronic fatigue. The review exposes
those limitations and asks the lifter to stop at intended effort if fewer reps
are available. Missing actual RIR stays missing; its original goal is only an
identified calculation assumption. Already accepted v1 prescriptions retain
versioned replay, and issued workouts/history are not rewritten.

See [the scientific basis and evaluation contract](kotlin-engine/SCIENCE_BASED_COACHING_CONTRACT.md).
Passing a test of this calculation does not prove better strength or hypertrophy.

## Retained historical observations

At 30 lb, a 10–15 rep range and goal RIR 2, the old frozen outputs were:

| Completed reps | Reported effort | Web's next reps | Previous Kotlin's next reps |
| --- | --- | --- | --- |
| 14 / 13 / 12 | 3 / 3 / 2 RIR | 15 / 14 / 13 | 14 / 14 / 12 |
| 14 / 13 / 12 | 2 / 2 / 2 RIR | 15 / 14 / 13 | Hold |
| 14 / 13 / 12 | Unreported; original goal 2 | 15 / 14 / 13 | Hold |
| 14 / 13 / 12 | 3 / 3 / 0 RIR | Hold | Hold |

These differences are observations, not rankings of accuracy. The first row's
first-set 15 is legitimate to evaluate; fewer changed rows are not inherently
better. New policy executions are recorded separately by CI rather than
silently changing this archive or treating a newly chosen formula as proof.

Both frozen engines passed five load-review scenarios: the mixed
200/205/210/215-lb failure, repeated 215-lb failures, an earned increment,
fatigue decay and missing actual RIR with an original goal. Both also respected
confirmed machine stock and a finite bar/plate inventory. Those old reported
loading defects were not reproduced in the pinned web policy.

## App usability

The debug Kotlin Lab launcher opens the independent native journey. Setup
links to the program editor; Train prioritizes saved approvals/resume; History
shows real completed set facts. The original themes are preserved.

A simple first-program generator from saved athlete choices, saved-profile
resume, seamless next-workout selection, history editing and complete Android
interaction acceptance remain unfinished. The finish line remains
[one complete usable training loop](USABLE_APP_MILESTONE.md). No APK or
production cutover is implied by headless policy checks.

## Reproduction and provenance

Historical execution: candidate `6ff4618ed9e38f6e18e010d6413f8b84b177e3cb`,
web `903b4a78564590d8983e651cbe19d81acd1efb9c`. Raw artifacts are
`kotlin-engine/verification/paired-progression-measurement.json` and
`kotlin-engine/verification/paired-functional-measurement.json`. The ledger
verifies their bytes and records the rep-quality retraction. CI executes the
current policies separately; its v2 rep checks verify data/load/range integrity,
not prediction accuracy, fatigue calibration or superiority.
