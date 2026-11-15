# Pursuit Iron Kotlin release status

As of 2026-11-15. Active branch: `architecture/kotlin-engine-rebuild`.

**Production verdict: NOT READY.** There is a source-tested native training
core, but substantial engine, feature, data and device acceptance work remains.
The recent workout/restart checkpoints described individual improvements, not
the complete remaining release scope. No percentage-complete or release date
is supported by the current evidence.

**Product acceptance requirement (2026-11-14):** every carried feature must
meet the [product cohesion and improvement contract](PRODUCT_UI_DESIGN_CONTRACT.md#product-cohesion-and-improvement--binding-beta-requirement-2026-11-14).
Review purpose, deliberate placement, shared behavior, complete task flows and
evidence of improvement. Feature counts, catalog coverage and green source
tests do not certify that the app feels coherent or that its UI is finished.

**Saved-program checkpoint (2026-11-14):** Plan has a visible Programs chooser
with the same searchable list on cold entry, real goal/week/day/block details,
current-program indication and complete pagination. Browsing/Cancel preserve
the editor and Undo. Explicit selections require a fresh owner-checked native
read, reject stale athlete bindings, preserve the current Plan on read failure,
and suppress cancelled/superseded results. These are source/JVM guarantees;
physical device, accessibility, visual and motion acceptance remain pending.
The APK/AAB freeze remains in force.

**Runtime configuration verified (2026-11-15):** `MainActivity` already opens
`NativeKotlinCandidateJourney`. Both app variants compile the staged Kotlin
source allowlist, and app dependencies exclude `:engine-runtime` and JS bridge
modules. The standalone legacy project is retained for comparison/migration;
it is not an app runtime fallback. `verifyEarlyBetaRuntime` checks the resolved
release graph and device-acceptance evidence before packaging. Earlier notes
describing a legacy release launcher/dependency were stale. Final artifact,
full parity, complete UI, migration and physical-device acceptance remain open.

**Smith physical loading checkpoint (2026-11-15):** onboarding and Gym accept
an explicitly confirmed Smith empty-bar resistance (including a confirmed zero
counterbalance) and actual symmetric plate-pair inventory under the exact
reviewed station ID. Free bars, other Smith stations and old lists of totals
cannot supply that physical proof. The shared plate viewer supports both issued
workout routes with Smith-specific labels and exact entered-load arithmetic;
viewing it does not log a set or change approved targets. New inventory uses
source v2 / approval v9 only when needed; old inventory retains its previous
formats. Independently captured old Smith source/approval payloads remain
byte-identical. Native tests cover source/consent/issuance, explicit actual
loads, retry, cold reopen, Finish and History. Full device/visual/accessibility,
broader equipment semantics and overall parity acceptance remain open.

**Kettlebell equipment checkpoint (2026-11-15):** the two active-web
kettlebell identities now use a distinct per-bell loading basis. One-bell
swings and matched-pair gorilla rows require separately confirmed inventory;
a single bell or dumbbell rack cannot prove a matched pair. Onboarding
reveals the optional fields on request, and Gym uses the same exact-mass input
and native source review. Plan calibration, issued set entry/Last, exercise
Info, coaching reviews, history and machine-record presentation share the
physical units. Viewing or entering a weight never silently changes targets.

Real Kotlin program generation and four-week conversion preserve the new
movements' exact calibration and source rep bands. Native-file tests cover
source/consent/preparation/issuance, deliberately different actual loads,
retry, cold reopen after each set, Finish, verified History and CSV. All
pre-existing frozen native/approval compatibility tests remain mandatory.
Source inventory is now **320/376**; **56 IDs remain absent**. This bounded
checkpoint does not certify full functional parity, coaching superiority or
physical-device/UI acceptance. No APK/AAB is built or released.

**Workout display checkpoint (2026-11-15):** both native workout routes and
Settings share a device-local display source for Target/Last, Keep screen on,
and starting layout. The existing Last preference remains compatible. Automatic
starts compact windows in Focus and wider windows in All sets; saved current
workout views and drafts remain independent. Awake requests default on, honor
the toggle only while recording, and restore the previous View flag. Initial
screen composition no longer writes an old local reference mode back to storage.
Policy/source tests and Android compilation are required; physical-device,
large-font/TalkBack and app-wide UI acceptance remain open. No APK/AAB.

## What works in the isolated Kotlin core

**Recorded History dates (2026-11-14):** both native Finish routes observe the
device clock once in the durable History intent. Completion time survives
recovery, duplicate acknowledgments, archived/segmented reads and corrections.
Old ledgers remain readable and undated; original journal receipts/digests are
unchanged. History groups recorded dates by local month, and CSV adds UTC
values only when available. The source calendar browser adds recorded-month/day
filters intersected with exercise search, explicit undated access, locale
week starts, and a full-width day list for small panes/large fonts. Source/JVM
verification does not clear full UI, visual, accessibility or actual-device
acceptance. The APK/AAB freeze remains.

The trap/hex-bar checkpoint adds 17 reviewed source identities and independent
spinal-erector/trapezius accounting. Actual bar mass and paired plates flow
through onboarding, saved gym stock, program generation, cycle conversion,
workout logging/corrections, completion and cold recovery. Missing equipment,
unsupported old muscle scopes and copied straight-bar weights fail closed.
At that checkpoint source coverage was 318/376; the remaining identities, full functional
parity, comparative coaching evidence and device/UI acceptance are still open.
On 2026-11-15 the two web catalog files used by this audit were checked against
`main` and their Git blob IDs matched. This is current source-inventory evidence,
not certification of catalog behavior or overall engine parity.
Signed v8/v9 training is recovered from independently captured native fixtures
without filling new muscle rows, rewriting approvals or changing file bytes.
No APK/AAB is built or released by this source checkpoint.

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
- Receipt-driven rest countdowns in both Kotlin workout paths, with exact
  warmup/working and amplifier timings, manual restart/clear, per-workout
  minimize/resume UI state and screen-awake behavior.

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

## This continuation: usable workout rest and minimize/resume

Generated/custom journals and the original composer journal now connect their
durably recovered receipts to the existing native rest countdown. The pure
Kotlin projection uses the just-completed set's issued rest even when Focus
advances to an exercise with a different or zero manual rest target. Warmups
retain their own durations. Amplified mains use their explicit intra-set pause;
follow-ups use the parent's ordinary between-set rest. An immediate follow-up
clears the preceding countdown rather than inventing a pause. Corrections,
skips and failed attempts do not start a fresh automatic rest, and completing
the last required visit removes the timer before Finish.

Both loggers reserve space below the scrolling controls for the countdown.
Each workout has its own saveable screen state, preserving the running deadline
and unsubmitted fields through minimize/resume and normal Activity restoration.
A fresh timer mount baselines recovered receipts so an old receipt cannot
restart rest; elapsed/wall-clock checks still reject invalid restored clocks.
Custom workouts now keep the screen awake while active and restore the previous
view setting when closed. The timer does not write measured rest into history
or feed assumed rest into fatigue calculations. Authored 2–3-movement
supersets now share one alternating execution order with Focus and round rest:
warm up each member, alternate working sets, finish triggered amplifiers
before switching, and rest after the round's closing completed visit using
that visit's authored duration. Unequal counts finish solo; skipped/failed
visits and corrections do not become completed work or restart rest.
The original snapshot, loads, reps, set keys and history receipts stay intact.
Cold native issuance/logging/retry/Finish tests cover a full authored pair
with an amplifier. Plan now creates, edits and unlinks full superset groups
through an owner/source/revision-checked, undoable draft save. Original sets
and week overrides remain exact; previously issued workouts stay unchanged.
Plan's editor now offers optional partner ideas after selecting one movement,
using only that day's reviewed accessories, recorded muscle targets, working
set counts and current positions. Main/supporting lifts, cross-target overlap,
unavailable entries and other groups are excluded from ideas. Shared supporting
targets are disclosed. Choosing an idea changes only the pending selection;
the separate source/revision-checked Save and renewed approval remain required.
Coaching and superset off switches suppress ideas while manual controls remain.
In-session suggestions and automatic generated pairing remain separate work.
Notifications and physical
background/restart, keyboard and accessibility acceptance remain open.

## Remaining production release gates

| Gate | Current gap / required exit evidence |
| --- | --- |
| Complete program generation | The declared twelve-case placement matrix passes. Broaden goal/experience/split/day/gym/time coverage; finish catalog/attachments and all separately tracked muscle channels; independently review phase-specific dose, practice frequency, recovery, peak/taper behavior and actual program quality. |
| Progressions and personalized fatigue | All advertised progression methods must work through actual next workouts. Validate individual target-RIR predictions, intra-workout effects, accumulated fatigue, plateaus and deload response against later performance. The current arithmetic estimates and V3 reported-effort hold do not establish that accuracy. |
| Adaptive cycles | Complete goal-appropriate hypertrophy/strength/peak and powerbuilding options, permitted split/day/exercise changes, stable history mappings, preserved custom techniques, full prospective audits and understandable end-to-end acceptance. Existing bounded proposal/consent paths are partial evidence. |
| Full app feature parity | Ordinary and authored superset-round rest are source-connected; notification/device acceptance and remaining Kotlin-path tools still need closure: Last/Target, complete Focus, notes, plate/bar controls, substitutions, supersets, restrictions and shorter sessions; finish real-data PR/charts/standards, bodyweight, milestones and profile/preferences. Existing legacy/native components are not automatically functional Kotlin Lab features. |
| Data recovery and migration | Verified rebuilt-app export/restore, conflicts/Undo, interrupted import, existing web history/settings reconciliation and active legacy-session closeout; preserve exact targets, actuals, IDs and PR eligibility. Native journal durability does not substitute for complete user backup/restore. |
| Android acceptance | Test the installable build on phone and foldable: complete offline journey, keyboard, large fonts/TalkBack, original themes, timing/awake behavior, background/process death, corrections, recovery and signed updates retaining data. Source checks are not physical-device acceptance. |
| Demonstrated improvement | Fresh matched comparisons of complete programs, cycles, progressions and user experience; independent expected outcomes and individual forecast calibration. Historical parity and the withdrawn one-set benchmark do not prove overall superiority. |
| Kotlin-only packaging and release acceptance | The app already stages Kotlin-only sources and dependencies and launches the native journey. Complete every required capability and migration, verify the final resolved runtime/artifact with the existing packaging gate, and pass full UI/device acceptance before release. A configured Kotlin-only entry is not a completed beta. |

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
current integration does not expose secret administration. This pass has not
configured the repository's signing settings or requested any private key in chat.

The owner setup is now a single private command in
`tools/kotlin_lab_signing.py configure`; it uploads the four secrets without
printing them and pins the public certificate in `KOTLIN_LAB_CERT_SHA256`.
It refuses to replace an existing pinned key. The device workflow checks real
private-key access before assembly and verifies the final APK's signature,
stable package, version and Kotlin launcher before upload. Its public receipt
binds the APK hash to the source commit and marks device acceptance NOT_TESTED.
Temporary signing-key tests do not configure the repository or produce an APK;
owner setup and signed-update testing remain open.

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

Current local validation passes **727 engine tests + 475 native persistence
tests**. Native cases include complete generated hypertrophy, strength, peak
and mixed-purpose PPL weeks, nine-session custom-cycle continuity, and saved
v2/v3/v4 compatibility. New rest checks cover exact issued durations, all four
amplifier kinds, immediate follow-ups, corrections, duplicate movement slots,
superset/manual behavior and cold native receipts without target/RIR mutation.
Source CI repeats these checks, audits all twelve starter
cases, re-executes web/Kotlin comparison probes and compiles Android.

The prior [31-step source pipeline](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/42459293542)
verified the preceding v4 checkpoint. Its separate device milestone passed the
source guards and then refused assembly because private signing was incomplete.
This v5 continuation makes no APK or production deployment.
