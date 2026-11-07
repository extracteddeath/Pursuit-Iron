# Pursuit Iron — comparison acceptance

The goal is a better coach and a usable native app. Both engines must face the
same inputs and independently defensible criteria. Matching a Kotlin-specific
rule, compiling successfully or having more tests does not prove superiority.

## Corrected evidence status — 2026-11-07

The assistant introduced a one-set/one-rep restriction and misattributed it to
the athlete. The rep grader rewarded that restriction. The resulting improvement
claim is **withdrawn**, with its artifact digest and reason in
`verification/engine-superiority-evidence.json`. The gate excludes it from
completed quality proofs and improvement domains and retains it as an unmet
requirement. The original measurements remain unchanged and verifiable.

Seven other frozen pairs show parity: five load reviews and two equipment
cases. There is **no established improvement domain**. Overall verdict:
**NOT_PROVEN_BETTER**. Any changed policy tree or runner invalidates current
credit for archived evidence; it must be remeasured. An archival pass cannot
certify newly changed code.

The old rep outputs, including web 15/14/13 versus Kotlin 14/14/12 for actual
14/13/12 at RIR 3/3/2, are behavior observations. The first-set 15 should be
evaluated against the intended RIR, not rejected because another set advanced.
Neither vector is validated as the best individualized prediction.

## Current rep correction and its limits

New target-RIR reviews use each observed ordinal's own reps and reserve, rather
than a fixed limit on how many sets or reps may change. They can increase one
or several sets, or reduce reps when recorded effort exceeded the intended
effort. A required estimate below the rep floor calls for a load review.
Previously accepted v1 receipts replay their original policy; new approvals
have a distinct v2 decision identity and version-6 storage encoding.

These are transparent starting estimates, **not a learned acute/chronic fatigue
model**. Actual rest, earlier exercise work, changed early-set work and longer
term fatigue are unmodeled. Forecast limitations are returned and shown in the
native preview. Missing actual effort remains null; only the original goal
can serve as an explicit assumption. This does not promise an extra rep or a
particular success probability.

The governing requirements and primary research are in
[SCIENCE_BASED_COACHING_CONTRACT.md](SCIENCE_BASED_COACHING_CONTRACT.md).

## Benchmark files and responsibilities

| File | Responsibility |
| --- | --- |
| `verification/engine-superiority-cases.json` | 33 scenarios and 49 required paired proofs covering progression, equipment, programming, custom programs/cycles, coaching, history, migration and Android |
| `verification/engine-superiority-evidence.json` | Frozen results, source identities, input/artifact digests, explicit retraction; no invented missing results |
| `verification/paired-progression-measurement.json` | Original five actual next-exposure load-review executions on each engine |
| `verification/paired-functional-measurement.json` | Immutable historical rep/physical-stock execution, including the withdrawn v1 rep oracle |
| `verification/run-paired-functional.mjs` | Fresh v2 executions; rep checks cover load/range/data integrity only and cannot establish predictive superiority |
| `verification/engine-superiority-gate.mjs` | Rejects incomplete, mismatched, stale, withdrawn or otherwise insufficient quality evidence |

The historical v1 grader remains solely to authenticate the archived bundle.
It is not a scientific coaching requirement. Its one-set conformance tests
must not be cited as quality evidence. New v2 rep checks deliberately do not
rank vectors by number of changed sets. They also must not be mistaken for a
prediction-quality oracle. Recording over the historical functional archive
is disabled while its rep-quality claim remains withdrawn.

## What would establish better coaching

1. Freeze actual executable engine revisions and identical athlete, equipment,
   exercise/day, history, schedule and goal/phase inputs. Do not compare
   unlike contexts or use reported old defects as current executable results.
2. Grade feasible goal-RIR work, physical loadability, dose, rest, time and
   constraints. Allow several physiologically plausible prescriptions. Do
   not turn a preferred implementation's formula into the superiority oracle.
3. Evaluate future held-out sessions chronologically for each lifter. Measure
   rep/effort prediction error, under-prescription and overshoot, and calibration
   when probabilistic predictions exist. Separate normal variation, acute
   fatigue and accumulated recovery concerns. Respect missing information.
4. Evaluate complete programs and adaptive cycles across goals, experience,
   available days, split/exercise adaptation, equipment, muscle priorities,
   time, progression methods and coaching-off behavior. Preserve separate
   wrist-flexion/extension and biceps/brachialis accounting.
5. Use at least two independent blinded coaches to review matched generated
   plans/cycles and the rationale for dose, recovery and transitions. A longer
   or more complicated program is not inherently better.
6. Verify the usable native loop: setup, complete feasible generated program,
   review/save, correct workout, logging/editing, minimize/resume, Finish,
   factual History, next workout and cold restart. Phone accessibility,
   original themes, backup/restore and actual device evidence are required.
7. Require explicit athlete acceptance before release. No failed critical
   domain may be averaged away by easier passing cases. Existing release
   criteria require improvements across at least three domains, all required
   paired proofs, and no measured regressions.

## Reproduction

With a clean pinned legacy checkout, Kotlin/JDK or Gradle, and the Android SDK
for app checks:

```sh
node --test android-native/kotlin-engine/verification/paired-progression-test.mjs android-native/kotlin-engine/verification/paired-functional-test.mjs
node android-native/kotlin-engine/verification/run-paired-progression.mjs --legacy-root <pinned-web-checkout> --check-current
node android-native/kotlin-engine/verification/run-paired-functional.mjs --legacy-root <pinned-web-checkout> --check-current
node android-native/kotlin-engine/verification/engine-superiority-gate.mjs --self-test
node android-native/kotlin-engine/verification/engine-superiority-gate.mjs --audit
```

The frozen web revision is `903b4a78564590d8983e651cbe19d81acd1efb9c`.
CI executes both real engines and attaches fresh raw outputs. An audit reports
honest gaps and exits successfully without certifying the product. `--release`
refuses missing/stale evidence or missing acceptance. Source CI success is not
Android interaction acceptance, a new APK, production cutover, or superiority.
