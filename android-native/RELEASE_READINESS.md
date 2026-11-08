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

## Previous checkpoint: time-feasible Full Body starters

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

## This pass: strength/peak day roles and main-lift placement

New first-program policy **v5** closes the eight placement failures in the
declared smoke fixture. It registers multiple calibrated primary lifts and
checks a bounded set of skill/supporting-day distributions through the same
complete composer. A pull-only day no longer needs a fictitious bench or squat
to satisfy an all-days skill label.

The search starts with the eligible selected main lifts and more practice days,
then checks supporting-day and bounded alternative-main-lift options if needed.
All calibrated exercise offers remain available; those offers are not immutable
custom pins. At most 32 phase policies are checked, each with the existing
bounded set-allocation search. Failed searches report their limitations rather
than claiming mathematical infeasibility. Stock, dose/frequency constraints,
rest, warmups, effort, user weekdays and time settings retain their authority.

In this fixture, Full Body delivers two bench practices and one squat practice;
Upper/Lower, PPL and ULPPL deliver two of each. PPL has two supporting pull days
and ULPPL has one. These are checked outcomes under the declared one-barbell/day
constraint, not a universal frequency prescription. The JSON audit now records
day roles, named lifts, actual frequencies and phase-search limits.

The existing strength/peak rep bands, RIR and rest assumptions are unchanged.
This is a placement and usable-program improvement, not individualized peaking,
a validated taper or proof of superior training outcomes. Saved v1–v4 plans
replay their original algorithms and targets.

## Current real-starter smoke matrix

Fixture: advanced experience, 40–60 minutes, 15 minutes declared non-set time,
confirmed bar/plates and dumbbells, one barbell exercise per day, eight explicitly
calibrated loaded movements plus a chosen bodyweight crunch. The shipped catalog
contains 20 provisional entries. This is one declared fixture, not all athletes,
gyms, goals, schedules or time preferences.

| Split | Days | Hypertrophy estimate (minutes) | Strength | Peak |
| --- | ---: | --- | --- | --- |
| Full Body | 3 | 56 / 56 / 57; reviewable | Reviewable | Reviewable |
| Upper/Lower | 4 | 60 / 60 / 60 / 60; reviewable | Reviewable | Reviewable |
| PPL | 6 | 51 / 40 / 42 / 51 / 40 / 42; reviewable | Reviewable | Reviewable |
| ULPPL | 5 | 60 / 42 / 45 / 51 / 56; reviewable | Reviewable | Reviewable |

**12 of 12 are reviewable; none is unresolved in this fixture.** V4 remains
reproducible with four reviewable cases and eight unresolved cases. These counts are test coverage,
not a completion percentage. All twelve selections still declare seven
direct-muscle coverage gaps; unsupported muscles are not secretly counted as
fully trained. The full exercise library and program-quality review remain open.

All twelve v5 cases pass whole-session timing, declared muscle dose and direct
frequency, verified stock, actual primary-lift placement and recovery checks.
Complete native-file journeys additionally cover generated strength and peak
Upper/Lower weeks and a mixed-purpose six-day PPL strength week, including
approval, logging, corrections, Finish, phase-rule recovery and next week.
This does not complete the broader goal/experience/gym/time matrix or adaptive
whole-cycle coaching.

Reproduce with `gradle :kotlin-engine:runStarterProgramReleaseProbe` from
`android-native`. CI records all twelve outcomes, including unresolved cases,
in `starter-program-release-coverage-incomplete`. The audit never grants release
approval. The checked-in result is
[starter-program-release-audit.json](kotlin-engine/reports/starter-program-release-audit.json).

## Remaining production release gates

| Gate | Current gap / required exit evidence |
| --- | --- |
| Complete program generation | The declared twelve-case placement matrix passes. Broaden goal/experience/split/day/gym/time coverage; finish catalog/attachments and all separately tracked muscle channels; independently review phase-specific dose, practice frequency, recovery, peak/taper behavior and actual program quality. |
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
The ordinary core is available in source. The candidate-3 build was actually
attempted after source validation and **stopped at the required private-signing
guard**, before APK assembly: at least one required GitHub Actions signing
secret is absent. This is a concrete infrastructure blocker, not an Android
compilation failure. No APK was produced. See
[the guarded build attempt](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/42459673171).

The repository owner must complete the one-time private signing configuration
in GitHub Actions secrets before a stable test build can be assembled. The
current integration does not expose secret administration; no key material
was created, displayed, committed or requested in chat during this pass.

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

Current local validation passes **717 engine tests + 474 native persistence
tests**. Native cases include complete generated hypertrophy, strength, peak
and mixed-purpose PPL weeks, nine-session custom-cycle continuity, and saved
v2/v3/v4 compatibility. Source CI repeats these checks, audits all twelve starter
cases, re-executes web/Kotlin comparison probes and compiles Android.

The prior [31-step source pipeline](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/42459293542)
verified the preceding v4 checkpoint. Its separate device milestone passed the
source guards and then refused assembly because private signing was incomplete.
This v5 continuation makes no APK or production deployment.
