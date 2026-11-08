# Pursuit Iron Kotlin release status

As of 2026-11-08. Active branch: `architecture/kotlin-engine-rebuild`.

**Production verdict: NOT READY.** There is a source-tested native training
core, but substantial engine, feature, data and device acceptance work remains.
The recent workout/restart checkpoints described individual improvements, not
the complete remaining release scope. No percentage-complete or release date
is supported by the current evidence.

## What works in the isolated Kotlin core

- Saved athlete/gym setup, explicit movement/load calibration, provisional
  first-program generation, whole-cycle review and custom program authoring.
- Durable program approval, exact preparation/issuance, native warmup/working
  and authored amplifier receipts, corrections, interruption/resume, Finish,
  verified history and next-session selection.
- Saved custom coaching rules recover under their exact program/source
  approval. Older approvals without those inputs retain receipt replay and
  need one explicit renewal to save the missing rules.
- Per-slot actual history, optional-RIR provenance, versioned rep/load reviews,
  immediate next-set advice and saved Standard/Progression Only/Manual controls.

These are bounded source/native-file demonstrations. Phone acceptance and full
coaching accuracy are not implied by successful compilation or test counts.

## This pass: an actual program-generation improvement

The shipped-catalog smoke audit originally produced reviewable programs for
**3 of 12** selected split/phase combinations. The three-day Full Body
hypertrophy case proposed **64 / 73 / 73 minutes** for a 40–60-minute preference.
Its local search chased the provisional preferred dose and did not find a
smaller feasible distribution.

New first-program policy **v4** keeps the original first attempt and, only when
that draft satisfies hard constraints but exceeds the preferred time window,
checks at most four smaller secondary/accessory set envelopes through the same
complete composer. Primary options, muscle-dose/frequency bounds, exact stock,
warmups, reps, effort, rest, recovery and user time settings remain unchanged.
Only a fully audited result can reach the existing approval workflow.

The same Full Body case now produces **56 / 56 / 57 minutes**. Its whole week
also passes native issuance, all set logging, corrections, Finish and cold
selection of week two. Saved v1–v3 policies replay their original algorithms;
the native suite separately cold-replays and issues an old timed v3 program.

This corrects an earlier Kotlin defect. It is not a comparison proving better
training outcomes than the web engine, nor a measured elapsed-time prediction.

## Current real-starter smoke matrix

Fixture: advanced experience, 40–60 minutes, 15 minutes declared non-set time,
confirmed bar/plates and dumbbells, one barbell exercise per day, eight explicitly
calibrated loaded movements plus a chosen bodyweight crunch. The shipped catalog
contains 20 provisional entries. This is one declared fixture, not all athletes,
gyms, goals, schedules or time preferences.

| Split | Days | Hypertrophy estimate (minutes) | Strength | Peak |
| --- | ---: | --- | --- | --- |
| Full Body | 3 | 56 / 56 / 57; reviewable | Unresolved | Unresolved |
| Upper/Lower | 4 | 60 / 60 / 60 / 60; reviewable | Unresolved | Unresolved |
| PPL | 6 | 51 / 40 / 42 / 51 / 40 / 42; reviewable | Unresolved | Unresolved |
| ULPPL | 5 | 60 / 42 / 45 / 51 / 56; reviewable | Unresolved | Unresolved |

**4 of 12 are reviewable; 8 remain unresolved.** These counts are test coverage,
not a completion percentage. All four reviewable selections still declare seven
direct-muscle coverage gaps; unsupported muscles are not secretly counted as
fully trained. The full exercise library and program-quality review remain open.

The automatic strength/peak starter policy currently marks all days as skill
days while registering only one named primary lift. That cannot place suitable
main lifts across the chosen Upper/Lower/PPL focuses and constraints. Full Body
also remains unresolved in those phases. Manual/custom phase prescriptions and
bounded adaptive proposals exist; that does not complete automatic first-plan
or whole-cycle creation for those goals.

Reproduce with `gradle :kotlin-engine:runStarterProgramReleaseProbe` from
`android-native`. CI records all twelve outcomes, including unresolved cases,
in `starter-program-release-coverage-incomplete`. The audit never grants release
approval. The checked-in result is
[starter-program-release-audit.json](kotlin-engine/reports/starter-program-release-audit.json).

## Remaining production release gates

| Gate | Current gap / required exit evidence |
| --- | --- |
| Complete program generation | Fix strength/peak main-lift and day-purpose placement; broaden goal/experience/split/day/gym/time coverage; finish catalog/attachments and all separately tracked muscle channels; independently review dose, practice frequency, recovery and actual program quality. |
| Progressions and personalized fatigue | All advertised progression methods must work through actual next workouts. Validate individual target-RIR predictions, intra-workout effects, accumulated fatigue, plateaus and deload response against later performance. The current arithmetic estimates and V3 reported-effort hold do not establish that accuracy. |
| Adaptive cycles | Complete goal-appropriate hypertrophy/strength/peak and powerbuilding options, permitted split/day/exercise changes, stable history mappings, preserved custom techniques, full prospective audits and understandable end-to-end acceptance. Existing bounded proposal/consent paths are partial evidence. |
| Full app feature parity | Complete Kotlin-path workout tools and adjustments: rest, Last/Target, Focus, notes, plate/bar controls, substitutions, supersets, restrictions and shorter sessions; finish real-data PR/charts/standards, bodyweight, milestones and profile/preferences. Existing legacy/native components are not automatically functional Kotlin Lab features. |
| Data recovery and migration | Verified rebuilt-app export/restore, conflicts/Undo, interrupted import, existing web history/settings reconciliation and active legacy-session closeout; preserve exact targets, actuals, IDs and PR eligibility. Native journal durability does not substitute for complete user backup/restore. |
| Android acceptance | Test the installable build on phone and foldable: complete offline journey, keyboard, large fonts/TalkBack, original themes, timing/awake behavior, background/process death, corrections, recovery and signed updates retaining data. Source checks are not physical-device acceptance. |
| Demonstrated improvement | Fresh matched comparisons of complete programs, cycles, progressions and user experience; independent expected outcomes and individual forecast calibration. Historical parity and the withdrawn one-set benchmark do not prove overall superiority. |
| Kotlin-only production cutover | The app still depends on `:engine-runtime`; the release manifest still launches the legacy `MainActivity`. Deliberately integrate every required Kotlin feature, migrate/reconcile data, remove runnable JS training authority/fallback, test the release artifact and record explicit cutover approval. |

The [full replacement contract](kotlin-engine/FULL_REPLACEMENT_CONTRACT.md),
14-feature/E01–E41 scope and native UX requirements remain mandatory. None is
waived by publishing a development APK.

## Next test APK versus full release

An isolated **development test APK is a nearer milestone** than production.
The ordinary core is available in source; its next device candidate requires
the stable private signing configuration to be confirmed and the milestone
workflow to build the exact source-tested commit. The current tools do not
expose repository-secret configuration, so signing readiness is **unverified**,
not asserted missing or complete.

Future Lab builds use the distinct `.kotlinlabstable` identity and the same
private development certificate on every update. Existing v2 test data stays in
the old app; it is not silently migrated. See
[the signing setup](KOTLIN_LAB_SIGNING_SETUP.md).

After a stable test build, verify the complete supported core on the phone and
fix observed defects while closing the engine and feature gates above. Then
prove backup/migration and independent quality, remove production JS authority,
and approve the release. Physical acceptance cannot be required before the test
APK that makes it possible; it is required before a production release.

## Validation at this checkpoint

Local execution passes **714 engine tests + 470 native persistence tests**.
The latter includes the complete three-day generated Full Body journey, the
four-day generated Upper/Lower journey, nine-session custom-cycle continuity,
and explicit saved v2/v3 compatibility. The isolated CI pipeline repeats those
checks, audits all twelve starter cases, re-executes web/Kotlin comparison
probes and compiles Android. No APK assembly or production deployment is part
of this pass.
