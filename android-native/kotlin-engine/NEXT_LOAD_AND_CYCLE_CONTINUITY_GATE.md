# Native Kotlin coaching: next-session loads and custom-cycle continuity

**Isolated branch only. No live app or JavaScript engine replacement.**

## Evidence-driven next-program load review

`CoachNextProgramLoadReviewDomain` now connects the existing conservative
double-progression and overload-recovery evidence with the independently audited
native weekly prescription. It accepts a known versioned program, explicit
athlete/current-source revisions, current history revision, the exact two most
recent comparable exercise outcomes, the physical inventory and a maximum
approved increment.

The pure reviewer never writes or issues anything. A load proposal must have
one of two evidence paths:

1. **Earned load:** two completed comparable performances at the same verified
   working weight, all reps at the target ceiling, actual reported RIR at least
   two on every set, with an attainable next physical weight within the
   athlete's maximum increment.
2. **Overload recovery:** the last attempt contains a rep-floor miss after
   trying higher weights, a prior comparable workout demonstrated a safe common
   weight, the last attempted maximum matches the *currently authored* load,
   and that prior load is physically attainable on the same implement basis.

The proposal first verifies the original complete program. It refuses low
readiness, explicit movement restrictions, stale source/history, absent or
incompatible exercise visits, different rep bands or set counts, custom
top/backoff schemes, warmup overrides, and unattainable weights. A proposed
load is applied to a **copy of the program**, including every affected visit,
and the whole week is recomposed and audited again. The original authored
program, issued workouts, historical observations and stored grants remain
unchanged.

**Important product gap:** current generated Composer options hold one load
per exercise across the whole program. The reviewer therefore surfaces every
affected training day; it does not pretend to recommend a one-day adjustment.
Production requires a new approved program revision, complete owner-verified
history and native grant before these proposals may influence targets. A
future per-purpose/per-visit loading model and a review/approval UI are needed
for true coach-quality strength-vs-hypertrophy variation. A submitted DTO is
not authenticated history by itself.

## Explicit cross-block volume continuity

`CustomCycleCoachAuditDomain` now checks actual resolved weeks at every
TRAINING-to-TRAINING block boundary, including any author-defined weekly
overrides. It requires two explicitly authored maximum-reduction fractions:
total working sets and direct muscle-credit sets. It rejects negative,
excessive or over-precise policy values, missing continuity policies and
training blocks that exceed the reviewed reduction. This checks direct muscles
separately, including biceps/brachialis and wrist flexion/extension.

A deliberately named RECOVERY or PERFORMANCE_TEST block has a separate
coaching purpose and is not equated to a normal TRAINING boundary. The
existing MEV/MAV/MRV, muscle frequency, gym, rest, full-cycle and actual
technique/attachment checks continue to apply.

For illustration, a change from five to three sets is a 40% drop. An
athlete-authored 25% maximum blocks it even when both weeks satisfy their
separate individual MEV bounds; an expressly reviewed 50% maximum can
permit that transition. These are tests of explicit individual policy, **not
recommended universal drop percentages**. The original reported 30-to-11
volume collapse is a mandatory head-to-head regression, not a claimed
measured web or Kotlin outcome.

## Candidate Android workout path

`NativeKotlinCandidateJourney` can now navigate to the existing
`NativeKotlinWorkoutCandidateScreen` via a Train destination. A manually
entered ID is strictly a **testing entry point** for a previously native-owned
approval grant. Typing an ID never creates a grant, enrolls history, begins a
workout or touches the current web-backed app. The session controller verifies
the approved native grant and reopens the durable journal before recording,
skipping, correction or Finish.

This is intentionally not the final user-facing picker. A production native
journey needs an automatic authorized Today selection, first-class
generated/custom program approval, onboarding-to-grant connection, continuity
and safe closeout of any in-progress JS-issued workout before cutover.

## Evidence and remaining gates

Headless Kotlin tests cover earned increase, overload rollback, stale history,
incompatible or missing evidence, authored techniques, exact physical stock,
unapproved proposals, cross-block dose changes and missing continuity policy.
These tests verify candidate behavior, not human-coach superiority. CI must
also pass the native store recovery suite and Compose compilation.

Do not transfer source ownership, merge this development branch, publish an APK
or claim superiority until real paired JavaScript/Kotlin comparisons, the
full 14-feature and E01–E41 ledgers, authoritative history, actual Android
onboarding-to-Finish acceptance, device power-loss/restore and blind coaching
reviews pass. The 24-scenario comparison's evidence file remains empty until
both engines are actually run with matched inputs.
