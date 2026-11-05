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

## Owner-fenced explicit load-change acceptance (candidate only)

`NativeKotlinMixedHistoryReadCoordinator.acceptCurrentApprovedLoad` is now a
bounded **isolated-native** bridge from the actual stored coaching review to a
new independently approved program revision. It requires a user-triggered
confirmation and **exact** displayed expectations: two compared workout IDs,
history/approval revisions, exercise, change kind, old/new load, all affected
day indices, and a fresh workout selection grant and receipt ID. None of
these caller values is trusted as evidence of training performance.

Under the same owner fence used for native approvals, history and issuance,
the method reopens the full mixed-history ledger, independently validates
every historical journal and post-Finish correction, recomputes the coaching
proposal, compares its complete displayed decision with the user's
expectations, verifies the new workout ID is neither in completed history nor
already issued as an unfinished journal, and preflights the entire newly
proposed program. Only then does it use the existing approval store's
compare-and-swap transaction to advance **both** storage approval revision
and Kotlin program revision and save a new, separate workout grant.

An unchanged original issued journal remains resumable and immutable; the
new grant has **not** issued any sets or changed completed history. A
repeat acceptance, altered correction, stale approval, unavailable history,
identity reuse, unbuildable stock or missing explicit confirmation refuses
the write. The real native issuance transaction must separately approve the
newly selected workout before it appears in the logger.

The isolated `NativeKotlinCandidateCoachScreen` now presents this
candidate flow in Compose: exercise and verified-increment inputs, explicit
readiness/restriction controls, the read-only history review, actual before/
after load, all affected days, the next training-day selection, and a separate
checkbox plus confirmation action. It creates fresh native grant/workout/
receipt IDs for that accepted revision. The next workout remains unissued
until the normal native Train issuance path runs; the old workout stays frozen.
The acceptance writer also refuses IDs already held by **unfinished custom
workouts**, not just issued Composer journals and completed history entries.

**Still not production cutover:** The candidate approval store owns its own
Kotlin-generated program record, but the app's full authoritative
athlete/gym/catalog source store, production acceptance UI, and interrupted
legacy workout migration have not been transactionally tied to this commit.
The candidate UI has not had physical-device acceptance testing, and a
caller-supplied confirmation flag is not a standalone proof of user consent.
The v2 history manifest remains capped at 4,096 completions, and directory
fsync/device power-loss certification, atomic source-owner revisions and
real backup/restore checks remain pending. Do not invoke this acceptance
as an automatic post-Finish progression.

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
