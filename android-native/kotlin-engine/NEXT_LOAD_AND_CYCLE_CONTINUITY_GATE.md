# Native Kotlin coaching: next-session loads and custom-cycle continuity

**Isolated branch only. No live app or JavaScript engine replacement.**

## Changed block recipes: conservative calibration proposals

`CustomBlockTransitionLoadDomain` adds an independent review of first-week
phase or rep/RIR changes. It uses the shared complete-history exposure verifier
instead of weakening ordinary double progression. Each working set retains at
most its own historical and currently authored weight, with exact stock and
unit/basis checks. The recommended starting reps are the new rep-range minimum;
starting reps plus reserve must not exceed the old performed reps plus reported
or explicitly assumed original goal RIR. The new goal never substitutes for the
historical goal. No estimated maximum or earned load increase is claimed.

The native owner computes the review from current consent, complete corrected
history and current gym inside its existing fence. Compose shows the historical
facts, authored/proposed weights, starting reps and goal effort. Changed warmups,
working order, added sets, shorter inter-set rests, unsupported effort, near
failure, recovery restrictions and changed source/split contexts remain refusals.
The rest after the final selected working set cannot support earlier loads.

The pure projection has no acceptance or issuance authority. The ordinary
signed-cycle native path now supplies a separate, explicit one-workout protocol:

1. `NativeKotlinBlockCalibrationConsentStore` reopens the complete corrected
   history, current signed authored cycle, assistance settings and LOADS plus
   WEEK_OVERRIDES permissions under the shared owner fence. It refuses a pinned
   week exception, an already prepared/issued target day, or a workout-ID
   collision. The projected slot is applied only to a virtual cycle copy and
   every cycle week is audited again; the snapshot must exactly match the
   displayed projection.
2. Athlete confirmation stores a checksummed receipt bound to all revisions,
   original/projected fingerprints and exact per-set diff. An identical retry
   acknowledges the stored fact; it does not grant fresh issuance authority.
3. Preparation and actual issuance independently recompute the same evidence
   and full-cycle audit. A missing, stale or corrupt receipt refuses the changed
   targets rather than substituting authored weights. The immutable issued
   snapshot enters the existing native journal, actual logger and Finish path.
4. Before issuance the athlete may separately review and confirm restoration of
   the original authored snapshot, including with coaching disabled. This clears
   the preparation's calibration proof and durably prevents automatic revival
   when coaching is re-enabled. Issued targets cannot be reset.

Acceptance/preparation discovery supports cold restart, without treating a
stored receipt as proof it is still current. The cycle and its signoff remain
unchanged. Native tests cover the 25 -> 20 lb accepted target through Finish,
null actual RIR, cold reads, corrections, missing/corrupt approval records,
permission revocation, protected week exceptions, duplicate ownership and
explicit coaching-off reset. Prepared schemas 7 (ordinary calibration) and 8
(ordinary authored reset) retain reads of schemas 1–6.

**Same-block continuity gate:** an accepted first-week adjustment still
applies only to that exact workout. A later same-slot session now has a separate
lower-only repeat proposal based on the immediately preceding completed
same-weekday issued snapshot and corrected history. No higher load is earned
by this repeat. Matching source, split, block, phase, recipe, exercise variant,
physical inventory, effort provenance, readiness and restrictions are checked.
Ambiguous duplicate prior-day completions refuse review. Only fresh explicit
LOADS + WEEK_OVERRIDES acceptance after a full-cycle audit can prepare and issue
a revised later-week workout. Native persistence tests exercise weeks 4–6,
process restart and a late RIR-0 correction revoking a staged receipt.

**Explicit authored return (isolated native candidate):**
`previewAuthoredReturn` checks every slot against the immediately prior
same-day issued workout, shows each higher working set, and audits the full
current cycle and gym again. The athlete may choose these exact authored
targets even when the previous work was near failure; the UI identifies this
as an athlete choice, never an earned increase or a coach recommendation.
No adaptive-load permission is borrowed: this selects the already signed
authored plan and does not edit the cycle or its week overrides.

Explicit confirmation atomically saves the original authored snapshot and
a distinct revision-bound consent fingerprint in prepared schema 9. It
requires no second sidecar transaction. An old untouched preparation can add
this consent without changing its targets. A stale unissued authored-return
preparation can replace its consent only after a fresh displayed review and
confirmation; ordinary prepare/issue retries cannot refresh it automatically.
Both acceptance and first issuance reopen corrected history, current settings,
signed cycle, gym and outstanding workout identities under the same owner
fence. A missing verifier, correction, settings/source change, competing
preparation, corrupt file or wrong owner refuses rather than falling back.
Existing schemas 1–8 retain read compatibility.

Cold discovery includes these preparations. Compose shows all higher sets,
asks for separate authored-target confirmation, then offers the normal exact
issuance and logger/Finish path. Coaching-off can use the separately reviewed
authored-reset path; it clears the old return proof permanently, and turning
coaching on again cannot revive it. Already issued workouts remain frozen
and accessible independently of later settings or history edits.

**Earned step from a reduced baseline (isolated native candidate):**
`CustomReducedBaselineProgressionDomain` first verifies the immediately prior
issued lower loads and the preceding adjacent same-day workout, including
the complete exercise variant, prescription, warmups and corrected receipts.
It then reviews a virtual next snapshot at that verified baseline through
the existing `CustomNextWorkoutCoachingDomain` and
`CustomNextLoadAmendmentDomain`. No new double-progression mathematics or
performance estimates are introduced. Two complete comparable ceiling
workouts, adequate reported/original-goal effort, exact stock and the approved
maximum increment remain mandatory. An intervening missing/failed/changed
day cannot be skipped, and another weekday cannot supply the second workout.
Mixed working loads cannot become a uniform all-sets increase.

The bounded proposal may remain below or exactly reach the authored loads;
it never exceeds an individual authored working weight. A new explicit
`EARNED_BASELINE_INCREASE` receipt kind carries the two workout IDs, actual
issued baseline loads and count of original-goal RIR assumptions. Its
checksummed schema 2 shares the existing owner-fenced full-cycle audit,
LOADS + WEEK_OVERRIDES permissions, acceptance, preparation and issuance
transactions. Conservative reductions keep their original schema-1 bytes
and never silently become earned increases. Actual unreported RIR stays null.

Compose separately offers the earned option, displays baseline → proposed
weights and effort assumptions, then requires acceptance and exact issuance.
Cold discovery and revalidation understand both receipt versions. A late
correction, revoked permission, changed setting or damaged receipt refuses
first issuance; previously issued snapshots remain immutable. Explicit
coaching-off authored reset also works when the earned step exactly equals
the authored snapshot, and permanently clears its old advice proof.

**Still open:** production activation of the candidate journey;
earned increases above current authored weights in ordinary signed cycles;
activated source/split mapping, complete backup/restore, directory fsync/
power-loss recovery and device acceptance. The checksum detects accidental
corruption, not cryptographic owner authentication; native file tests do not
constitute paired or device certification.

## Previous-block work and conservative starting weights

The native custom-day review now includes the evidence-only
`CustomBlockBaselineContinuityDomain` plus `CustomBlockStartingLoadDomain`.
The Future Week and custom-cycle preview screens show the last verified same-day sets and
their original reported effort, then separately explains whether repeating
the existing per-set authored loads is supported. It never calls that repeat
an earned load increase and does not create a new approval or edit a cycle.
The full request is recomputed under the existing native owner fence.

Only an identical recipe, source, split, phase and purpose may receive the
repeat recommendation. Missing RIR uses the original goal only for calculation;
near failure, missing both effort sources, low readiness, restrictions, failed
or missing latest work and unavailable physical stock refuse it. Changed phase
or purpose can expose factual reference without converting it into a load.
The ordinary signed-cycle path uses `NativeKotlinCustomWorkoutPreflight` under
the same owner transaction, so unchanged blocks can actually receive the
same-source repeat from real persisted history. Nine-workout native tests prove
missing-effort provenance, cold reads and withdrawal after a saved correction
changes the latest work to failure; stale/revoked consent cannot read advice.
The separate future-schedule activation path changes source and training
context, so it still requires a fresh baseline. Different starting weights,
new-context transition policies remain separate unfinished work. Changed
same-source first-workout loads have the bounded acceptance/issuance path above.

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
   working weight, all reps at the target ceiling, reported or assumed original
   goal RIR at least two on every set, with an attainable next physical weight within the
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

**Current loading model:** a Composer exercise has a program-wide physical
default, but the verified `ExercisePrescriptionPolicy` can now override its
working load per session purpose **and per exact 0-based day slot**. Upper
versus Push need not share a bench target, even when both use a hypertrophy
session purpose. The native reviewer selects two comparable, authenticated
visits from that *same day*, proposes a one-slot load change, and independently
reaudits the complete revised week. Old issued workouts never change. This is
an actual candidate capability, not proof of production release or human-coach
superiority. Further exercise-specific load schemes, custom cycle handoff,
source ownership and device validation are still required.

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
new grant has **not** issued any sets or changed completed history. If a prior
UNISSUED grant has athlete-accepted exact rep targets, the current review
surfaces that pending prescription and requires separate explicit consent
before the load acceptance can supersede it. The new program approval clears
the old rep-only proof; it never transfers old exact rep goals to a changed
working load. The replacement workout stages from the new verified load and
approved practical rep band, while completed workouts retain their exact
logged history. A repeat acceptance, altered correction, stale approval, unavailable history,
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

## Current native gym/catalog coherence before a new target

`NativeKotlinApprovedGymCoherenceDomain` compares an accepted generated
program against the **currently persisted, reviewed** native athlete/gym and
curated exercise source. It refuses missing/stale gym stock, an unavailable
attachment or facility, removed/excluded movements, incompatible exercise
roles/patterns/focus/muscle credits, invalid schedule/rest days, incompatible
exercise equipment, and a planned load that is not *exactly* buildable.

The isolated Android host now requires this check when publishing a first
generated program, reviewing an earned load, explicitly accepting the new
revision and issuing the next session. Native issuance uses a separately
wrapped authority that reads the accepted program and gym source together
under one owner revision fence, preventing edits between validation and the
new journal commit. The wrapper delegates **historical** journal access to
the original owner authority: editing or removing equipment must not make an
already issued workout or its corrections disappear.

The pure validation is not source authentication on its own; the native
store must have decoded and verified the full reviewed gym snapshot under
the current owner and athlete. Candidate tests cover physical equipment
removal, confirmation changes, attachment/variant and direct-muscle credit
mismatches, schedule/exclusion changes, buildable weights, and read/accept/
issuance after on-disk gym edits. This is not yet a production source
cutover: the source snapshot revision is not cryptographically pinned in the
generated approval's source revision fields. Before full replacement, the
athlete/profile/gym/catalog and approved program need one unified durable
revision protocol and migration/rollback tests.

## Training-cycle volume continuity across ALL consecutive weeks

`CustomCycleCoachAuditDomain` now checks actual resolved consecutive
TRAINING weeks **both inside each block and across block boundaries**.
It includes every authored per-week override in the measured sets. A 5-to-3
weekly drop cannot escape review just because both weeks independently meet
their MEV. A 3-to-5 weekly ramp within MRV likewise cannot escape review.

`CustomCoachVolumeContinuityPolicy` preserves separately reviewed ceilings
for total working-set drop and **direct credited-muscle** drop, and requires
explicit total and direct-muscle **ramp-up** ceilings whenever training work
increases. It rejects missing policy, unbounded ramps, excessive weekly
declines, invalid percentages, and stale assumptions about muscle dose.
Direct biceps/brachialis and wrist flexion/extension are evaluated separately.
The audit does not manufacture missing sets, auto-adjust custom authoring, or
permit a coach suggestion to overwrite an athlete-pinned program.

A declared RECOVERY or PERFORMANCE_TEST block has a separate coaching purpose
and is *not* treated as ordinary training-volume loss. Existing phase-specific
MEV/MAV/MRV, direct training frequency, exact equipment, time and session
purpose checks still apply to every week.

Example **test fixtures only**, not universal training recommendations:
5-to-3 sets represents a 40% drop; a reviewed 25% drop limit rejects it,
whereas an explicitly approved 50% limit can permit it. A 3-to-5 set rise
is about 67%; it exceeds an athlete-reviewed 50% rise limit but can fit
a separately approved 75% ceiling. Both cases pass their *individual*
weekly MEV/MRV and therefore prove why continuity needs its own review.
The old reported 30-to-11 collapse remains a required real paired regression;
no legacy/Kotlin head-to-head result is claimed here.

## Durable, athlete-accepted per-set rep prescription

`KotlinAdaptiveSetPrescriptionDomain` builds one **whole-session** candidate
from the approved program and measured completed working sets. It changes
only exact per-set rep targets, never arbitrary set weights, RIR, rest,
exercise order, or logged evidence. Eligibility requires matching actual
unit and equipment stock, stable ordinals, authentic historical source,
reported or explicitly assumed original-goal RIR, sufficient headroom and no restricted movement.

The isolated native Coach screen now displays the before/after working-set
targets and asks the athlete to explicitly accept them. The native owner
reopens complete saved history including corrections and current equipment
under one revision fence. It rejects unissued/custom workout identity
collisions, stale approval/history, unavailable effort, or any altered proposed
rep. Acceptance persists a new approval storage revision and **new grant**
with a constrained evidence proof (original workout/revision and exact
working-set targets). Version-2 `NativeKotlinApprovalCodec` retains version-1
read compatibility; the codec refuses fabricated or multi-rep jumps.

Issuance is genuinely connected through
`NativeKotlinAcceptedRepSnapshotVerifier`. The issuer restages the entire
original Kotlin workout, re-derives accepted sets from authoritative
corrected history and current gym **before and again under the immutable
journal lock**, and refuses if anything differs. The inner verifier
intentionally never re-locks its own pending journal; it checks the full
history and gym while the outer issuer holds that journal's lock.
Neither a missing verifier nor a post-acceptance correction can cause
a silent fallback to broad generic ranges. The old issued journal survives
unchanged.

This is still a candidate-only grant, not a release-ready Android feature.
Device account switches, interruption recovery, full backup/restore,
cross-program phase behavior, custom amplifiers, and future rep recalibration
after a narrowly issued target still need complete testing. The full native
history is bounded by its existing ledger cap, and the web coach still has
unmatched features. No JS/Kotlin paired superiority result is claimed.

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
unapproved proposals, within-block and cross-block dose changes, weekly ramps and missing continuity policy.
These tests verify candidate behavior, not human-coach superiority. CI must
also pass the native store recovery suite and Compose compilation.

Do not transfer source ownership, merge this development branch, publish an APK
or claim superiority until real paired JavaScript/Kotlin comparisons, the
full 14-feature and E01–E41 ledgers, authoritative history, actual Android
onboarding-to-Finish acceptance, device power-loss/restore and blind coaching
reviews pass. The current 33-scenario comparison requires 49 paired proofs.
Five current headless pairs have actual matched executions; 44 proofs and
measured improvement in at least three domains remain missing. The five
existing pairs do not measure this new block-entry workflow.

## Native authored-load reversion guard

Ordinary candidate-host preparation now rechecks EVERY same-day exercise slot against
the full corrected native ledger under the owner revision fence. If an immediately
previous same-block, same-source immutable issued working load was LOWER than the
authored next-week target, preparation of the higher unamended workout is refused
with `AUTHORED_LOAD_REVERSION_REVIEW_REQUIRED`. This independent detection
still fires on failed, near-failure, or missing-RIR sessions, where a repeat
recommendation may itself be unsafe. No blanket return to the authored weight
is an earned progression. The previously shipped lower-load consent path remains
available on qualifying evidence. Coaching OFF deliberately uses the untouched
authored plan; authenticated manual reset of a PREPARED amendment remains a
separate explicit athlete action. No issued session or source cycle is mutated.

The guard is enforced for the app's candidate host, which always provides
the native calibration/history reviewer to ordinary preparation. Standalone
legacy store consumers without that injected reviewer are not certified as
guarded issuance entry points. Full production cutover must make the guard
mandatory in every trusted issuer path and implement a direct reviewed
authored-load-return acknowledgement without toggling coaching OFF.

The ordinary candidate-host issuer now repeats this owner-fenced reversion
check on FIRST ISSUE, including old prepared authored workouts made before
the guard was introduced. It refuses first issuance without changing that
prepared file or existing issued history. Cold replay of legitimately issued
workouts remains untouched. Regression tests also exercise the old-preparer
bypass after a saved near-failure correction.
