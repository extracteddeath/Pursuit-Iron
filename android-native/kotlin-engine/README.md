# Pursuit Iron — independent Kotlin training engine

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
