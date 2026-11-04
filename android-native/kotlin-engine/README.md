# Pursuit Iron — independent Kotlin training engine

## End state: full native replacement, not a permanent JavaScript bridge

The agreed destination is a **Kotlin-only training engine and fully native
Jetpack Compose Android successor** to Pursuit Iron's JavaScript/web product.
During development the JS implementation remains an isolated reference and
migration source. After gated cutover, the Android production workout path
must have **no JavaScript runtime, WebView, bundled JS training engine or
JavaScript fallback**. Kotlin owns every coaching decision; native-owned
storage preserves the user's actual sets, history and revisions. All 14
product capabilities, E01–E41 requirements and documented UX are mandatory,
and decisions must demonstrably improve over the web engine's known bugs.
Existing in-progress JS-issued sessions must never be silently rewritten.

**Authoritative release plan:** [Complete JavaScript replacement and
sunset gates](FULL_REPLACEMENT_CONTRACT.md). This is the target state;
the current headless Kotlin branch is NOT production-ready and is not yet
the native app's training authority. The live web app is not being retired
or modified as part of this branch.


## Kotlin-only journal state machine (proposed receipts, not yet persisted)

`KotlinWorkoutJournalDomain` models the next JS-retirement lifecycle seam:
begin from a native-verified committed snapshot identity, separately record
completed/skipped/zero-rep failed sets, make explicit prior-event corrections,
and finish only after every original set has a resolution. The reducer
preserves the immutable prescribed load/reps/RIR/rest and tracks actual
reported RIR independently; missing actual RIR stays unknown. Unweighted
bodyweight is distinct from a logged numerical zero. Honest manual actuals
are recorded without rounding them into an invented gym increment.

Every mutation carries an event ID and expected journal revision. Exact
replays of previously recorded events are idempotent (including after
subsequent edits or finish); conflicting event IDs, stale new writes,
unauthorized overwrites, wrong correction predecessors, invalid set values
and premature/duplicate finish are rejected. A correction adds a new
receipt; it never deletes the old factual history. The returned state and
collections are immutable **proposals**, not disk acknowledgments. A
native-owned store must verify real persisted issuance, enforce
transactional compare-and-swap and durably acknowledge receipts before UI
completion, then reconstruct state and historical correction semantics on
resume. The independent Kotlin rules neither import nor call JavaScript.

Pending: native storage bridge and crash/rotation/process-death replay,
warmup/amplifier/superset event variants and accurate rest session timeline,
history correction after session finish with deterministic PR/progression
recalculation, complete program- and load-policy authoring, full Coach
implementation and device proof.

## Kotlin-only session staging (replacement engine, not yet issued)

`KotlinWorkoutSnapshotDomain.stage` is the next S4 lifecycle boundary. It
recomputes **the entire** independently reviewed week and set prescription
from a Compose/native-owner-provided `ComposerRequest` and
`ExercisePrescriptionPolicy` list. It then produces an immutable,
one-day `KotlinStagedWorkout` with original exercise IDs, exact physically
attainable weights and their load basis, bodyweight semantics, original
warmup/working ordinals, rep bands, target RIR, active/rest seconds, day
purpose and estimated session length. It deliberately never copies an
untrusted “review-ready” draft from another layer.

The owner must authenticate an explicit workout-selection grant (approved
program, stable workout ID, athlete/gym/catalog/rules source revisions) against
its **current** durable facts. The pure domain checks revision equality and
the grant's fields and fails closed when other sessions of the week remain
infeasible, targets are not physically verified, or a policy is missing.
A staged snapshot is **not yet a committed or started workout**. It has
`persisted=false` and `canModifyJournal=false`; only future native
transactional storage can reserve a workout ID, commit a snapshot and issue
actual receipts. No JS runtime is used, and there is no fallback.

Still required for real engine replacement: source-authenticated native
grant issuance, durable and idempotent set/skip/failure/correction receipts,
restoration after crash or process death, real exercise variations, full
superset/myo/drop prescriptions and corresponding UI, exact coach-selected
weight progression (not merely reviewed eligibility), and native cutover
acceptance on a physical device.

## Kotlin-only workout recovery: verified receipt replay

`KotlinWorkoutJournalReplayDomain` reconstructs a workout's effective set
outcomes **only from the original immutable Kotlin-issued snapshot, authenticated
native storage identity, and the complete ordered committed receipt stream**.
It does not trust a cached `effectiveSets` map, finish flag, revision number,
or replayed duplicate event. It rejects missing, duplicated, reordered,
forged, invalid, or noncontiguous operations, plus an inconsistent finished
status. Corrections preserve original receipts; retries cannot create
additional working sets. A verified replay is a *pure recovered candidate*,
not proof that anything was durably written.

This is a concrete step toward fully retiring the JavaScript workout path,
but **the native storage owner, crash-safe transaction/commit protocol,
authentication, backup migration, cross-process cold restoration, and
Kotlin-owned workout issuance remain separate integration gates**. Do not
show a success confirmation before the native store commits the receipt.
Do not use the JavaScript journal as a fallback.

## Revision-bound verified machine weights (read-only Kotlin handoff)

`CoachNativeVerifiedLoadHandoff` is a **standalone headless review**, not an
Android/Compose import or live workout engine. Its typed DTOs mirror the native
`NativeCoachVerifiedLoadEvidence` projection on the separate Android
foundation branch: program ID, `programUpdatedAt`, `storeSavedAt`, v13 store
version and exact SHA-256; candidate count; exercise ID and display name;
unit; and either *exact logged selectable loads* or an expressly verified
minimum/increment/maximum range. A future owner must obtain and authenticate
those facts against the **CURRENT** saved-program snapshot and gym profile
before constructing these DTOs. This module cannot authenticate or parse
canonical JS snapshots by itself.

The handoff rejects stale or malformed program revisions, mismatched gym
units, duplicate or unknown exercise IDs, conflicting names/modes, malformed
stock, specialty-machine ranges and infeasible increments. Exact-only
`external_load` specialty gear is never given an inferred mechanical load.
Verified facts become immutable, per-exercise `VerifiedLoadStock` with the
`EXERCISE_OVERRIDE` basis; absent facts stay manual. Generic bar/plate,
dumbbell, Smith, cable or machine-family stock is **not** silently
converted to an exercise-specific fact. A separate, authenticated future
bridge will be required for those implement-level loading families.

`inspect` uses the existing independent `EquipmentLoadDomain` and
`progressionReview` uses `ConservativeProgressionReview`, which requires
two comparable all-set completed sessions, actual reported RIR and a
user-approved maximum step. Both fail closed for mismatched revisions.
Even a qualified increase is a **review candidate**, not a changed training
target. No persistence, issuance, program edits, workout writes, migration,
or engine authority change is permitted by this code.

Full end-to-end integration remains outstanding: Compose-owned reference
checks at the call boundary, real source inventory transport, parity tests
against canonical native inspection, independently audited Kotlin
exercise selection/progression, and physical-device acceptance.

## Current feature completeness and native UX obligations

- [Complete engine capability ledger](ENGINE_BASELINE_COVERAGE.md) tracks **all 14 native feature IDs, basic engine requirements E01–E41, review-only implementation gaps, and acceptance milestones**.
- [Native Compose UX impact plan](NATIVE_ENGINE_UX_IMPACT.md) specifies the **screen and control changes required for each engine feature** with interaction and device gates.

These are living requirements, **not claims that the new engine is live or every feature is complete**.

### Opt-in verified exercise selection (E09/E11/E12, partial)

`CoachAthleteExerciseSelectionDomain.review` now selects from a potentially
larger **offered** catalog of explicitly load-authored movements. It
checks each variant's physical implement stock, exact load, credit review,
attachments/facilities, split eligibility, exclusions, barbell preference
and basic working-set bounds. All pinned required visits and coach-named
priority lifts are retained, or the entire selection fails closed.
Remaining candidates are selected deterministically using optimistic
MEV/direct-day capacity and desired movement-pattern variety, with each
rejected choice and coverage gap available for native UI diagnostics.

The actual composer still decides whether working sets, recovery,
training frequency and duration are truly feasible; this shortlist does
not certify them. Crucially, `selectFromOfferedCatalog=false` remains
the default, preserving all explicitly authored/custom exercise choices.
Automatic pruning is **opt-in only** and never manufactures a load, new
exercise, dose threshold, workout or save operation.

### First automated split, session-purpose and lift-placement generator

`CoachAthleteProgramGenerator.generate` now **actually constructs** a
calendar-aware candidate from confirmed athlete setup and a versioned
goal/experience/phase policy. It distributes Full Body, Upper/Lower, PPL and
ULPPL sessions over the chosen weekdays, separates related sessions where
possible, assigns the coach's exact session-purpose counts, and places named
primary lifts only on compatible days with verified gym equipment and
circular recovery gaps (including the Sunday–Monday boundary).

The generator asks for explicit extra-focus choices when an odd UL/PPL
schedule or six-day ULPPL schedule is ambiguous. It never makes up
unreviewed weekly muscle doses or leftover day purposes, and exposes
infeasible calendars instead of disregarding a lift. It reviews multiple
calendar alternatives within a **bounded 32-candidate search**, returning
the first independently feasible draft or the best inspectable unresolved
draft (prioritizing required visits and actual dose over softer time
preferences), not an arbitrary first failing arrangement. Each resulting
week passes through the existing **catalog, equipment, dose and whole-program**
checks. This is an isolated, bounded first generation layer—not a trained
exercise selector, issued workout, persisted program or validated dose preset.

### Whole-week goal-policy realization and primary lift frequency (E12/E13, partial)

`CoachGoalProgramRealizationDomain.review` now audits the **actual
composed week** against authored intentions rather than relying on goal
labels. It checks achieved muscle dose and frequency, active training
days, time windows, pinned exercises, movement variety, and day purpose.
An authored policy can also set the minimum number of distinct days for
each stable, catalog-verified PRIMARY lift. Strength and peak drafts
must explicitly provide those targets and name the primary on each
strength-skill day. A bench-frequency goal that the week fails to
deliver remains unresolved, with the observed day count exposed.

All changes are non-authoritative headless reviews. No fabricated
exercise prescription, applied program or implied scientific preset.

### Authored goal and experience policy matching (E12/E13, partial)

`CoachGoalProgramPolicyDomain.review` gates the full athlete plan status on
a **versioned, profile-specific and phase-specific** coaching proposal.
The supplied policy must match goal, experience, athlete revision, training
frequency and split. It must exactly support all 16 declared muscle
MEV/MAV/MRV and direct-day frequency bounds. For focused/reduced muscles,
the changed dose must differ in the intended direction from an explicitly
authored baseline; a toggle without changed programming fails review.
The authored day-purpose minimums must actually occur, and strength/peak
blocks require an explicitly named skill lift.

The catalog composer still exposes a technical draft with no policy, but
its athlete-facing status is **unresolved**. This does NOT invent or endorse
universal scientific volume thresholds; actual evidenced coaching presets
and longitudinal individualization remain open. No app ownership switch.

### Confirmed athlete setup to actual catalog-week candidate (E11, partial)

`CoachAthleteCatalogCandidateDomain.review` now derives the candidate's
weekdays, split, session-time preferences and physical gym solely from a
confirmed `ReviewedAthleteSetup`. It requires explicitly authored phase,
all sixteen dose targets, split-day focus, day purpose and real non-set
minutes; missing or malformed policies cannot produce a fabricated week.
It refuses excluded exercises, unapproved bodyweight and zero-barbell
profiles. The weekly search now **actively enforces each athlete's maximum
barbell exercise visits per day and disabled direct-triceps on lower-body
days (both UPPER/LOWER and PPL LEGS)**,
including additions, replacements, relocations, and seeded custom-program
rebalancing. These constraints apply before a candidate is accepted, not
only in post-composition reports. Required visits that cannot fit are
reported as unresolved; they are never silently dropped. Unimplemented
focused/reduced muscle and hard/long day policies remain visible unresolved
constraints, not falsely honored preferences.

The result stays **review-only**: no target issuance, save, persistence or
native authority switch. The composer still needs actual goal-specific policy
and optimization for per-day athlete restrictions.

### First basic-engine foundation: athlete and gym setup review (E01–E06, partial)

`CoachAthleteSetupDomain.review` adds a headless, deterministic profile-input
validator. Every goal, experience, selected weekday, time-bucket and explicit
hard-cap choice, gym load unit and physical inventory, bodyweight/superset
and day-preference toggle must be **confirmed**; null is an unresolved input,
not a guessed false or a synthetic default. Conflicting training/rest/locked
days, ULPPL with too few sessions, impossible units/stock, and focus/reduced
muscle overlap fail closed with field-specific reasons.

An approved output is a defensively copied, versioned
`ReviewedAthleteSetup` **for requesting a candidate review only**. It cannot
save a program or issue training; exercise selection, profile persistence,
actual strength policy, complete custom authoring and UI integration remain
open. A real gym with zero loaded implements is allowed only if the athlete
explicitly enables bodyweight. Working time buckets are preferences,
not hard time caps.




**Quality bar:** [PRODUCT_SUPERIORITY_GATE.md](../PRODUCT_SUPERIORITY_GATE.md) makes every feature and every new engine decision subject to independently verified improvements over the existing web app. Legacy parity is not an acceptance target. The latest independent Kotlin loading and evidence tests are passing, but the engine is not connected to active native training.

**Full coaching acceptance:** see
[COACHING_QUALITY_STANDARD.md](../COACHING_QUALITY_STANDARD.md).
Its independently specified whole-program and Android workout scenarios
are required before engine ownership is switched. Individual progression
review results are not equivalent to a complete coached program.

This module is a **headless engine-rebuild laboratory** on the isolated
`architecture/kotlin-engine-rebuild` branch. It is **not connected** to
Compose, the existing canonical JavaScript training engine, the web app,
saved user workouts, the Android preview APK or persistence.

The M5 native-core APK was successfully built and uploaded at
https://github.com/extracteddeath/Pursuit-Iron/actions/runs/41576280920.
Its source/Kotlin jobs passed, but real Android device acceptance remains
pending. This Kotlin work can proceed independently while testing occurs.

## First implemented domain: precise equipment feasibility

- Explicit `LB`/`KG`; no implicit conversion.
- Explicitly declared barbell **total** (bar + symmetric physical plate
  pairs), dumbbell **per-hand** values, Smith **total** loads, machine/cable
  stacks, and exercise-specific confirmed values.
- `BigDecimal` up to three fractional decimal places; no float drift,
  silent 159.5 lb rounding, inferred plate stock, invented machine sizes
  or reinterpretation of a logged zero as bodyweight.
- Bounded exploration of feasible barbell plate combinations and stack
  steps, validation of duplicated equivalent plate sizes and malformed
  inventories. An unconfirmed/mismatched source fails closed.
- Inspection reports exact feasibility and neighboring **possible**
  loads. It never changes a lifter's recorded load, selects a working
  weight, or proposes new exercise targets.

The module deliberately has no link to the app's engine routing or
journal. Kotlin rules and test expectations must be independently
specified, not copied from flawed current engine output.

## Next domains

1. Working-set evidence and conservative double-progression eligibility,
   with actual versus target RIR strictly distinguished.
2. Explicit fatigue/reps-across-sets model; equipment quantization
   integrated **after** earned-progression decisions, never before.
3. Per-muscle MEV/MAV/MRV, separate biceps/brachialis and wrist
   flexion/extension, session time bounds and programming feasibility.
4. Program generation, adaptive cycles, custom exercise/setting parity,
   readiness and injury adjustments, deterministic history replay.
5. Full independent acceptance followed by a separate engine ownership
   switch, preserving original targets and user history.

**No automatic backend fallback, no migration and no release approval.**

## Second implemented domain: immutable progression evidence (no load proposals)

`DoubleProgressionEvidenceDomain.assess` examines the exact original
working-set ordinals and the actual, separately recorded load/reps/RIR. It
does not treat the prescribed RIR as a reported one; logged skips, unresolved
sets and explicit zero-rep failed attempts have different meanings.

The reported regression, **200×3 / 205×3 / 210×2 / 215×2 with 5–8 targets**,
is independently classified as *below the rep floor*, not a reason to
promote all subsequent sets to 215. A repeated 215×2 near failure likewise
is not recognized as an earned increase. Meeting the ceiling at mixed loads
does not prove that all sets can perform the heaviest load. Even uniform
ceiling evidence means **review eligibility only**, never automatic load
selection. Training rules, RIR thresholds and fatigue model still require
additional independent specification before the Kotlin engine can prescribe.

This phase cannot mutate existing training records or displace the canonical
JavaScript engine, including for sessions already underway.

### Numeric-normalization regression

The first independent CI identified two failed assertions caused by
`BigDecimal.equals` scale distinctions (`115` versus `115.0`) despite
equal physical weights. The loading-result boundary now normalizes to
plain decimal, never scientific notation, and tests include a 1,000 lb
value, trailing-zero inputs and monotonic feasibility. No stored workout
weight is rewritten. This is an independently tested correctness fix,
not a fallback to JS parity.


## Third domain: physically valid, conservative load-increase reviews

\`ConservativeProgressionReview.assess\` combines independent observations
from **two comparable sessions** with the independently verified loading
catalog. It produces a candidate *for review*, never an issued target.

Eligibility requires all working sets completed at the rep ceiling in both
sessions, an unchanged observed baseline load, actual (not target) RIR at
least 2 on every set, confirmed exact loading stock, an attainable next
increment and a bounded user-authorized maximum step. Missing RIR, near
failure, incomplete/skipped/failed work, mixed loads, zero-load/bodyweight
ambiguity and fabricated equipment all block auto-promotion. It never
silently changes the athlete's loads, reduces an unachieved load, issues
a new program, or implies a user-approved change.

This is a deliberately narrow, provisional safety gate for the reported
double-progression regressions. It is **not yet the full training progression
engine**: a tested longitudinal fatigue model, actual exercise prescription
and adaptation policy must exist before any workout issuance can use it.


## Fourth domain: set-level fatigue observations (no next-set prediction)

\`SessionFatigueEvidenceDomain.inspect\` compares working-set reps only
when every set was actually completed at an equal recorded load. It
distinguishes declining, stable, increasing and nonmonotonic performance,
retains observed RIR differences only where genuinely reported, and
identifies rep-floor misses. Mixed load sessions, skips, missing ordinals
and failed attempts are **not** translated into false fatigue curves.

This is an observation module, not a validated fatigue prediction or
exercise prescription. Its output cannot automatically lower a load,
raise repetitions, invent effort or change current/next workouts.


## Fifth domain: explicit muscle-volume accounting

\`MuscleVolumeAccountingDomain.summarize\` stores independently
identified **biceps** and **brachialis** exposures and separates **wrist
flexion** from **wrist extension**. Direct and indirect fractional credits
are tracked separately, as are ordinary working sets and amplifier/extension
sets. Completed warm-ups, skips, failed attempts and missing records never
become fabricated productive working sets. Duplicate stable set identities,
missing exercise allocations, invalid fractions and duplicate muscle
channels fail closed.

The fractions are *declared inputs* from a future vetted exercise catalog,
not universally validated physiological dose coefficients. The module does
not yet enforce MEV/MAV/MRV, predict gains, issue prescriptions, or silently
convert a myo/drop mini-set into a full working set. A future dose engine
must add phase-specific thresholds and constraint feasibility evidence.


## Sixth domain: explicit weekly dose and session feasibility

`WeeklyDoseFeasibilityDomain.audit` evaluates the **whole submitted week**
of 3–6 planned training days. Every one of the 16 tracked muscle groups has an
explicit caller-authored MEV/MAV/MRV band and minimum direct-training frequency;
the engine provides **no invented universal threshold preset**. Dose below
MEV, above MRV, below direct frequency, or beyond an explicitly entered hard
time limit causes a **non-feasible report for this proposed plan**. MAV is
reported as a target gap, not treated as a binary guarantee of gains.

- Preserve the distinction between direct/indirect fractional work and
  extension/amplifier sets. A myo/drop extension cannot silently satisfy a
  regular working-set minimum, and warmups never count as normal working sets.
- Return exact deficits, excess and missing direct-training days rather than
  quietly reducing custom-program volume when changing from hypertrophy to
  strength/peak, or treating biceps and brachialis as the same muscle.
- Account for caller-supplied active exercise time, rest and transitions.
  A preferred 40–60 or 60–90 minute *range* is not an implicit hard cap.
  120+ minutes has no invented maximum; a real user hard stop is separate.
- Validate all declared bounds, complete muscle coverage, unique set IDs,
  bounded precision and every physical planned session position. No plan or
  training record is changed by an audit.

**Open work:** trustworthy movement-to-muscle credit definitions, sourced
phase-specific volume ranges, time-estimation validation, selecting/scheduling
exercises and sets to resolve actual constraint conflicts, and proving
the chosen prescription over longitudinal histories. This is a **feasibility
checker for submitted plans, not yet a generated workout program**.

### Preliminary quality review is separate from safety feasibility

The dose audit now explicitly reports days outside preferred session-time
ranges, and whether the plan contains extensions whose fatigue/working-set
conversion has not yet been certified. `satisfiesDeclaredHardConstraints`
does **not** imply the program is good or ready for release.
`preliminaryReviewNeeded` stays true when those concerns are unresolved
even if the hard bounds are satisfied.

An additional regression recreates the reported ~30-set hypertrophy to
11-set strength collapse. The new checker raises muscle-specific dose gaps
**only against the caller's actual phase requirements**, rather than
declaring every strength phase should match hypertrophy volume.

### No hidden empty days or zero-duration workouts

A plan containing a nominal training day without any regular working sets
now reports `daysWithoutWorkingSets` and requires review, even if a time
estimate happens to fall within a preferred window. Every declared set
must have strictly positive active work time; a nonexistent zero-duration
set cannot be used to satisfy the dose or scheduling audit. Empty plans
remain auditable but cannot appear preliminarily ready.


## Coach-quality review: individualized, explainable, not merely restrictive

The provisional \`CoachExerciseDecisionDomain\` interprets actual sessions in
chronological order and produces a concise **exercise-level** explanation:
build reps, stabilize inconsistent set loads, repeat success, review a small
equipment-valid increase, review failed/below-floor work, confirm unknown
loading semantics, or adjust for user-reported low readiness/restrictions.

This avoids the earlier prototype's rigid all-set-RIR gate: if performance
is repeatable but RIR is missing, it **does not invent effort** or stop
progress entirely. It offers a possible increase **for review** with limited
confidence and explicitly asks to confirm effort. Reported near-failure,
skips/failed attempts, unverified equipment and oversize increments still
block a load jump. No user-entered value or workout is changed.

These are not certified prescriptions or evidence that all personalization,
fatigue, recovery and phase decisions are solved. Next milestones include
exercise context/variants, objective-specific rep and set schemes, validated
fatigue response and multi-week cycle decisions, and human-comparable
programming quality scenarios.

## Seventh domain: first full-week candidate composer

`CoachWeekComposer.compose` is the first *actual candidate generator* rather
than another passive validator. It constructs deterministic, provisional
3–6-day Full Body, Upper/Lower, PPL and ULPPL weeks from **explicitly
declared** exercise IDs, allowed day focuses, physical gym availability,
per-set work/rest estimates, working-set ranges, exercise appearance
requirements, calendar-day recovery gaps and the complete 16-muscle volume
bounds. It calls the independent weekly dose/time checker after each
candidate move, never accepting known MRV or user hard-time violations.

The search first repairs unmet minimum dose, frequency, empty days and
required authored exercise appearances. Only then does it improve preferred
time, MAV and movement-pattern variety. It does **not** inflate a completed
training week beyond declared MAV just to make a time bucket look full.
Recovery checks are circular across Sunday/Monday; optional caller-defined
shared heavy-movement recovery groups prevent changing to another variant
solely to bypass recovery. Compatible variations can still be clustered
within a single training session.

The result contains exact, stable working-set keys, proposed movement visits,
an independent feasibility report, unverified-equipment exclusions, unresolved
required visits and short explanations. The draft explicitly cannot issue
workout targets and cannot mutate authored programs or logged history.

**Remaining before coaching certification:** independently validated exercise
catalog and per-muscle coefficients, actual athlete and block context,
selection/repair beyond bounded greedy search, per-set loading/reps/RIR,
warmup and amplifier semantics, relative strength priorities and adjacent-
week recovery, advanced session duration estimates, personalized volume
thresholds, progression/deload policy and real-device workflow acceptance.
One feasible week does not yet constitute a coach-quality multiweek program.


### Intent-led session design (first pass)

The composer now accepts **explicit day-specific session intents**.
A caller may mark a strength-skill or hypertrophy day and require a
specific catalog-declared primary movement. The search gives that
day's missing main lift a hard scheduling priority, reports it as
unresolved if the lift cannot be placed or verified in the gym, and
never silently substitutes an accessory or another press. Each day
also emits an ordered review sequence: main movements, secondary
movements, then accessories (with declared priority and stable IDs).
Days without an explicit purpose remain **UNSPECIFIED** rather than
being invented from the training phase.

This ordering is only a provisional draft; actual warmups, technique
work, rep targets, effort, supersets and exercise-to-exercise fatigue
must still be prescribed, validated and approved before issue.


## Ninth domain: safe within-workout next-set review

\`InWorkoutCoachDomain.review\` distinguishes a missed rep floor,
effort near failure, a completed rep ceiling, an authored top/backoff
change, uncertain RIR, a user-edited upcoming target, incomplete
earlier work and an already logged future set. It can propose one
*smaller, physically verified* load for **human review on the next
unlogged set only**—never a blanket weight change to every set.
No available increment, incompatible unit or an overlarge step means
the recommendation is unavailable, not silently rounded.

The user-owned next target and all completed sets are returned/retained
unchanged; \`canApplyAutomatically\` is always false. Unknown RIR stays
unknown; low readiness and movement restrictions trigger session review
rather than a false performance failure.

This is an isolated coaching-review component. Actual rest prescriptions,
recovery validation, personalized effort and exercise-specific target
ownership still need integration and independent certification.


## Tenth domain: purpose-driven exercise prescription drafts

\`CoachSessionPrescriptionDomain.draft\` now takes an independently recomputed
\`CoachWeekComposer\` candidate and **explicitly authored** exercise policies
keyed to both the exercise ID and session purpose. It preserves strength-skill
versus hypertrophy differences for rep envelopes, target RIR and rest without
using universal guesswork or presuming a phase implies an arbitrary intensity.

For each exercise visit, the draft produces stable original working-set IDs,
a separate warm-up series with explicitly verified physical loads, and optional
one-last-set backoffs using the **next exact available implement load below
the authored top weight**. Backoff reps and effort are independently declared,
rather than copied from the top set. RIR is a proposed target, never a
fabricated log of actual exertion. Explicit bodyweight is not represented as
a spurious 0 lb; no physical load or warm-up is invented for it.

The final audit is recomputed from the actual proposed set-work durations,
warm-ups and rest. Unverified or impossible loads, missing purpose-specific
policies, invalid backoff steps and excess hard-time requirements keep the
result **UNRESOLVED**. Partial results have no complete audit. Neither a
valid draft nor an unresolved draft may issue workouts or change saved
programs. Reviews and explanations are exercise-level rather than
per-set banners.

**Still unresolved before the engine owns real training:** independently
vetted phase/exercise templates, athlete-specific programming targets,
fatigue-aware prescription over the full set sequence, transitions between
weeks, supervised deload/cycle decisions, authenticated data ownership,
native UI integration, device verification and comparative coaching-quality
acceptance against the old engine. A valid draft is not a certified coach.


## Longitudinal coaching: effort-sensitive plateau review

The new `LongitudinalCoachDomain.review` evaluates multiple chronological
training weeks, requiring actual completed key-lift sets and separately
reported recovery/readiness before discussing persistent fatigue, plateau,
deload or requested phase transitions. It does not guess a deload percent,
rewrite an existing block or issue sessions.

Plateau checks now distinguish **equal reps at equal load but explicitly
lower observed effort** (higher actual RIR) from stagnation. More reps
also count as improvement. Missing actual RIR remains unknown rather than
being replaced with issued target effort. Changing a rep band, mixed
loads, gaps in history or partial work cannot invent comparable evidence.

A deload or next phase remains a review recommendation and must next be
expressed as an explicitly constrained, auditable **candidate week** before
a user can approve any change. No automatic engine switch is implied.


## Twelfth domain: coach-reviewed next-block candidates

`CoachBlockCandidateDomain.review` accepts the real chronological
`LongitudinalCoachDomain` evidence and builds both baseline and proposed
week drafts with `CoachWeekComposer`. A deload requires sustained observed
difficulty **and separately reported recovery concerns**. It also requires
an *explicitly authored* maximum number of working sets for the specific
recovery week; it cannot invent a universal 50% volume reduction, leave
the week unchanged or quietly increase another muscle's dose. A strength
phase transition requires documented performance, a requested next phase,
and an explicit main strength-skill lift in the proposed week.

User controls remain independent: adapting split, calendar schedule,
exercise catalog, loading equipment and dropping/reducing a pinned
custom exercise each require their own authorization. Missing permissions,
infeasible candidate dose/time/recovery, unspecified session purpose,
missing phase evidence or an unverified source keep the change unresolved.

Both draft requests are recomputed from caller-provided inputs and all
result sets are read-only. **A reviewable proposal does not authorize a
workout issue, modify history or change the active program.** The module
does not attest the source request belongs to the real logged-in athlete.
Recovery week policies, movement-to-muscle catalog, cycle persistence,
approval provenance and Android end-to-end tests are still required
before it can become a training authority.


## Thirteenth domain: fully prescribed deload and phase candidates

CoachPrescribedBlockDomain.review joins longitudinal performance evidence,
custom-program permissions and both fully prescribed candidate weeks.
Unlike merely comparing weekly set counts, this recomputes warmups, working
sets, physical load basis and units, goal-specific rep ranges, RIR targets,
rest durations, session-time feasibility and every muscle's prescribed dose.

A phase transition cannot reach the human-review gate with a missing
strength-day exercise policy or invalid equipment weight. A purported deload
also cannot claim recovery by increasing an exercise's load, lowering the
planned RIR target, erasing a previous effort target, shortening rest,
increasing a muscle's dose while reducing total sets, or changing to an
incomparable bodyweight setup without separate review. Missing baseline
data is unresolved rather than permission for unsupported adaptation.

READY_FOR_HUMAN_REVIEW means feasibility checks completed, not that the
program has been certified by qualified coaches or independently validated
for training outcomes. Neither this status nor any returned candidate can
issue a workout, alter a custom program or modify training history.
Exercise-science validation, engine ownership, explicit approval provenance,
migration/rollback and Android device acceptance remain outstanding.


## Fourteenth domain: stable, provenance-aware exercise catalog

CoachExerciseCatalogDomain is a new fail-closed bridge from an explicit
exercise catalog and the athlete's exact gym inventory to the independent
weekly composer. Stable exercise, movement-family, variant and attachment
identities are preserved. The caller must supply documented, provisionally
reviewed muscle credits; biceps/brachialis and wrist flexor/extensor effects
stay independently identified. Missing review provenance, unavailable rope
or straight-bar attachments, wrong load-basis equipment, phantom increments,
and numeric bodyweight interpretations block composition.

An author-reviewed coefficient is NOT proof of scientific or independent
physiological validation. A complete authoritative catalog, evidence review
and exercise-specific coefficients are still substantial unfinished work.
The rules cannot synthesize an approved exercise definition from a name.

CoachCatalogBlockDomain connects catalog verification for both baseline and
proposed plans through CoachPrescribedBlockDomain. A phase/deload proposal
cannot smuggle raw ComposerExerciseOption values past catalog checks.
Reusing a stable exercise ID for a different movement family, variant,
attachment, role or implement meaning is rejected even when generic exercise
adaptation is enabled. Genuine substitutions need distinct exercise IDs,
the user's adaptation permissions and a complete, physically valid block
review. Neither the catalog gate nor the block gate can issue workouts,
modify saved training history, or replace the active engine.


## Fifteenth domain: real legacy catalog ingestion and reconciliation inventory

The native Kotlin rebuild has a **reproducible source staging pass**:
`node android-native/kotlin-engine/verification/legacy-catalog-ingest.mjs --write-report`.

This imports the *actual* curated and extended web exercise definitions
from the repository without copying their conclusions into the Kotlin
prescription engine. The generated `reports/legacy-exercise-inventory.json`
retains all historical IDs (including entries suppressed by canonical
name/ID deduplication), the effective-winner identity, source location,
equipment combinations and alternatives, original muscle coefficients,
rep ranges and canonical ordering. It includes source-file SHA-256 digests.

Every record is **UNREVIEWED** and excluded from training authority.
The report exposes ambiguous broad muscle buckets such as `back`,
unspecified attachment variants, mixed equipment alternatives and missing
independent physiological credit review. Curated-source membership and old
`metadataConfidence` fields are historical evidence only, not permission
to approve coefficients, infer machine loading, or auto-swap exercises.

CI checks that every staged record is unreviewed and that all live
built-in IDs have a traceable winner. The full report is attached as a
short-lived **unreviewed** GitHub Actions artifact so migration work
can audit individual source records without modifying the production
catalog or hiding lost aliases.

Next, an actual human-reviewed manifest must resolve each exercise's
muscles (including separate biceps/brachialis/wrist flexors/extensors),
attachment, exercise-specific load convention and supported equipment
combinations before any selected tranche can become catalog-authoritative
in Kotlin. This import is intentionally NOT proof of coaching quality.

## Sixteenth domain: first real starter exercises (provisional engineering review)

`StarterExerciseTranche` provides 20 **actual stable IDs from the curated
web exercise database**, with source-linked movement families, explicit
per-hand vs barbell-total equipment loading, supporting fixtures, muscle
credits, and practical rep bounds. Selection still requires the athlete's
actual gym stock and explicit working loads. No weight is synthesized.
The legacy source is independently checked by
`verification/starter-catalog-parity.mjs` in CI, including exact IDs,
movement families, equipment requirements and practical rep ranges.

Each real catalog option now transports its recorded historical practical
rep envelope into the headless prescription draft. A proposed working or
backoff rep range outside that *source* envelope remains unresolved rather
than being treated as a valid exercise prescription. This is a provenance
and sanity gate, **not** independent exercise-science approval. A changed
source envelope is also part of stable exercise identity during an adaptive
block review. The week composer prioritizes the explicitly requested main
lift ahead of other primary lifts, even when a different lift has a higher
general catalog priority. Both the weekly draft and exact set-level
prescription audit now exclude *post-finish rest* from session time.
Intra-session rests, declared warmups and setup/transition overhead remain
counted. Target rest instructions and all source facts remain unchanged.
This avoids false preferred-time coverage caused by treating rest after
the final set as training time.

The week composer now adds a bounded, deterministic repair phase after
its initial greedy construction. It may exchange an occupied exercise slot
or relocate an existing visit when that strictly improves the entire week's
declared dose, required appearances, training-day coverage, time preference
or movement variety. Every trial is reaudited for MRV, real hard-time limits,
physical equipment eligibility, day focus, visit counts and circular recovery.
It never fabricates new exercise identities or creates live targets.
The repair phase never worsens any pinned exercise appearance, erases a
previously scheduled named main lift, or breaks a muscle's already-met MEV
and direct-frequency constraints just to improve a competing score. A
read-only, validated `rebalanceForReview` diagnostic path also supports
regression tests of custom-program repair, including pin preservation and
unverified equipment exclusion. This remains a bounded local search,
not a global optimizer or individual coaching certification.

The starter catalog now declares provisional shared recovery identities for
barbell/paused bench and barbell RDL/conventional deadlift, with a conservative
two-calendar-day separation *between distinct training days*. The weekly
composer already audits the circular Sunday-to-Monday boundary, so renaming
the lift no longer bypasses the grouping. A custom group cannot silently
replace a declared catalog group; a more conservative gap can be requested.
Group identity is pinned during adaptive block review. These are provisional
scheduling protections, not medical recovery guarantees or a universal
48-hour rule. Same-day scheduling, advanced mixed-intensity prescriptions
and individual recovery remain subjects for independent coaching review.

The retained definitions include barbell/paused bench, squat, overhead
press; incline dumbbell press, one-arm row, lateral raise, biceps curl,
overhead triceps extension, split squat, Romanian deadlift, calf raise
and bodyweight crunch. Five additional exact IDs cover barbell row,
barbell Romanian deadlift, conventional deadlift, dumbbell rear-delt
raise and unweighted pull-up, plus dedicated seated leg curl and
leg-extension machines. Each machine requires independently confirmed
exercise-specific selectable logged loads (EXERCISE_OVERRIDE), never a shared
generic machine increment. The pull-up requires a separately verified
pull-up bar, without invented machine assistance or a zero-pound load.
Barbell variants remain separate from per-hand dumbbell variants;
no previous load is silently carried across them. Broad source labels require an **explicit and
provisional engineering mapping**: the one-arm dumbbell row's old
`back` bucket is provisionally allocated to upper back directly and
lats indirectly, with reported biceps/rear delts secondary credits.
The RDL's old generic `back` contribution is quarantined, not
reassigned to lats. `core` on a crunch is provisionally constrained to
abs; an ordinary supinated dumbbell curl does NOT automatically credit
brachialis or wrist flexors.

Importantly, this is an **engineering-authored provisional catalog**,
not exercise-science certification by an independent qualified coach.
The credit values remain assumptions subject to scientific review,
especially the legacy broad-back remapping. Likewise, this limited
20-exercise tranche is not a complete equipment-balanced 3–6 day
catalog; the rest of the 402 historical records remain unreviewed.

A regression discovered that the prior catalog did not validate a
required bench/rack when the bar and plates were present. The catalog
now verifies `requiredFacilityIds` against the athlete's available
fixtures; the identity gate also treats fixture requirements as part
of stable exercise meaning. A missing incline bench cannot be replaced
silently by a flat bench, and removing a rack requirement from a
same-ID exercise is refused across block transitions. Changing a
movement's authorized day focuses under the same exercise ID is also
rejected, even when exercise-adaptation permission is otherwise enabled.

This tranche can feed isolated, actual composed candidate weeks in
Kotlin unit tests, **not active user workouts**. Independent muscle
mapping review, a broader true gym catalog, goal-specific full-week
quality, history-linked state ownership and real APK acceptance still
gate release.


## Kotlin journal to factual history evidence (candidate only)

The new `KotlinWorkoutHistoryEvidenceDomain` accepts a **finished** workout
journal only after `KotlinWorkoutJournalReplayDomain.verifyStoredState`
reconstructs the ordered receipt chain. It projects each original issued set
into one factual row, preserving warmups, original targets, actual values,
skips, failed attempts and the current correction receipt ID. Older correction
events remain in the immutable audit stream and never become duplicate sets.
Issued RIR never populates unreported actual RIR.

For weighted working sets, it may produce a **read-only**
`DoubleProgressionEvidenceDomain` review, keyed by original working-set
ordinal. A mixed rep-band/backoff prescription, mismatched load basis, invalid
ordinals, unweighted bodyweight, or more than 32 working sets cannot masquerade
as uniform comparable double-progression evidence. Those workouts retain
their facts with an explicit unreviewable reason; no observations are discarded
to make the review appear stronger. Warmups do not count as working-set
performance. Skips and failures remain separate from missing observations.

The projector deliberately cannot publish a history record, authenticate
storage ownership, correct a saved workout, authorize progression, determine
future set loads or switch the Android app off the JS runtime. It is the
**factual, Kotlin-only read model** needed before separately implementing
atomic native history ownership and audited longitudinal progression. Tests
cover corrections, unknown actual RIR, failed attempts, skipped work,
backoff rep bands, bodyweight and forged denormalized journal state. CI
verification must still pass for the new branch head; no APK or app integration
is asserted by this addition.


## Chronological Kotlin progression timeline — revision-bound review, not issuance

`KotlinProgressionTimelineDomain` closes a headless source gap between an
individual finished workout and a multiworkout coach review. An authenticated
native history owner must read **all** retained completions in order and
provide a current store history revision, an athlete identity and independently
verified inventory. The Kotlin domain checks revision agreement, chronological
completion ordinals, duplicate IDs, athlete ownership, and full journal replay
before inspecting the most recent **two appearances of the exercise**. A recent
failed, skipped or below-floor session cannot be skipped to cherry-pick two
older successful workouts.

Comparison fails closed when either workout has incompatible working-set
structure, a different rep band, unit/load basis, training purpose, gym
revision, catalog identity, program identity or coaching-rules version.
The existing conservative two-workout DP policy still requires real all-set
performance, reported actual RIR and physically attainable inventory. The
timeline also returns an independent within-workout fatigue description, not
an instruction to automatically cut weight.

History-read revisions must be advanced **transactionally by the future
native storage owner** whenever saved workout facts change. The projector
does not authenticate disk bytes by itself, prove that the supplied list is a
complete history read, or implement post-Finish history corrections. Current
tests model corrections made *inside* an active workout before Finish. A
separate authenticated saved-history correction ledger is required before
claims about correction-driven PR/Last/next-session recalculation are valid.

`VERIFIED_REVIEW` means the supplied input passed the headless historical
review rules, **not** that a workout, future load or program is approved or
that the data has been proven committed to disk. `canIssueTargets` and
`canChangeProgram` always remain false. No Compose, active JS runtime,
production website or APK is changed by this engine-only pass.
