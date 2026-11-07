# What has actually improved?

The new Kotlin engine is **not yet proven better overall**, and the Kotlin app
is **not yet ready for ordinary workouts**. Internal tests protect specific
behaviors; their count is not a measure of app completeness or coaching quality.

Both real engines were executed at frozen source revisions with the same
inputs. The current evidence covers eight comparison cases out of 49 required
proofs. Kotlin passes all eight; the frozen web engine passes seven. There are
no measured regressions within this small set. One progression rule shows a
measured improvement; program quality, complete feature parity and phone use
still need proof.

## A concrete difference in suggested reps

Three sets used 30 lb, with a 10–15 rep range and goal RIR 2. The agreed rule is
to add at most one rep to one set with demonstrated extra reserve, while keeping
the other sets and weights intact. These are per-set rep recommendations for
review, not automatic workout changes or a scientific claim about training gains.

| Completed reps | Reported effort | Web's next reps | Kotlin's next reps |
| --- | --- | --- | --- |
| 14 / 13 / 12 | 3 / 3 / 2 RIR | 15 / 14 / 13 | 14 / 14 / 12 |
| 14 / 13 / 12 | 2 / 2 / 2 RIR | 15 / 14 / 13 | 14 / 13 / 12 |
| 14 / 13 / 12 | Unreported; original goal 2 | 15 / 14 / 13 | 14 / 13 / 12 |
| 14 / 13 / 12 | 3 / 3 / 0 RIR | 14 / 13 / 12 | 14 / 13 / 12 |

The web policy increases every row in the first three scenarios. Kotlin follows
the agreed one-set/headroom guard. Neither engine reproduced a +3 jump within a
single row here. Missing actual RIR remains missing in history; the original goal
is only a calculation assumption.

## Where these tests show parity

Both engines passed the five original load-review scenarios, including the
mixed 200/205/210/215-lb failure, repeated 215-lb failures, an earned increment,
fatigue decay and missing actual RIR with an original goal. Their exact downward
weight reviews differ; these tests do not establish which reduction produces
better training outcomes.

Both engines also used the real equipment correctly:

| Equipment input | Attainable lower weight | Next physical weight | Result |
| --- | --- | --- | --- |
| Machine, request 159.5 lb, confirmed 5-lb stock | 155 lb | 160 lb | Both pass |
| Machine, request 38.5 lb, same confirmed stock | 35 lb | 40 lb | Both pass |
| 35-lb bar, one pair each of 10 and 2.5 lb, request 47.5 lb | 40 lb | 55 lb | Both pass |

The machine and plate tests do not reproduce the old reported phantom-weight
defect in the frozen web policy. Old reports are not evidence that today's web
source still has every reported problem.

## App usability

The debug Kotlin Lab launcher now opens the actual independent journey. Setup
links directly to the program editor; Train prioritizes saved approvals and
resume instead of manual IDs; History shows real completed native set facts.
The original themes and the existing app's training owner are preserved.

This is still a development route. A simple first-program generator from the
athlete's choices, saved-profile resume, seamless next-workout selection,
history editing and complete Android interaction acceptance are unfinished.
The next milestone is [one complete usable training loop](USABLE_APP_MILESTONE.md),
not more internal load-consent variants. No APK or production cutover is implied.

## Reproduction

Recorded policy execution: candidate `6ff4618ed9e38f6e18e010d6413f8b84b177e3cb`,
web `903b4a78564590d8983e651cbe19d81acd1efb9c`. The raw results are
`kotlin-engine/verification/paired-progression-measurement.json` and
`kotlin-engine/verification/paired-functional-measurement.json`. The evidence
ledger checks their content, source and runner fingerprints. CI reruns both
comparisons; this is a headless policy result, not an Android-device result.
