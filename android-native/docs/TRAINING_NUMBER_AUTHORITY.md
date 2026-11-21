# Training-number authority: one Kotlin engine, one auditable chain

Status: architecture/acceptance contract, **not** a claim that all paths are
implemented or that native has web parity. The native replacement must never
package or call the web/JavaScript engine as a hidden second decision system.

## The four different kinds of numbers

1. **Authored prescription**: an athlete-approved cycle/program version
   determines exercise, physical load and basis, working-set count, rep range,
   RIR target, rest, amplifier and planned progression rule. The issued
   session holds an immutable snapshot with athlete, gym, machine, day, source
   revision and set identity. There is one source of truth.
2. **Editor starting value**: a read-only projection. For a regular set it
   may start from the issued target on the selected verified machine. For
   myo-reps, rest-pause, same-load cluster and lengthened partials it MUST
   begin from **that exact activation set's last saved performed load and
   station**, not a first working set, prior workout, stale UI draft or a
   program-level first-set weight. A drop uses its independently authored
   stage-specific load. A legacy explicitly authored override is not silently
   migrated. No usable activation => no guessed same-load default.
3. **Recorded performance**: the athlete's confirmed reps, weight, unit,
   RIR, station and outcome. Revisioned journal receipts are immutable
   historical evidence until an explicitly accepted correction. A logged
   set need not equal its target. Warmups, failed attempts and amplifier
   mini-sets do not silently become regular working sets.
4. **Coach/future-plan proposal**: a separately traced recommendation,
   initially uncommitted. Never overwrites an active workout, typed value,
   completed set, old session, or machine-specific history. Changes to a
   future prescription require the relevant review/automation consent.

The labels **Target**, **Last**, **Actual**, and **Next recommendation**
must never be interchanged. Load is not a scalar alone: include unit,
load basis, physical equipment/Machine A-B, authored scheme, exercise,
day and set role. Compare decimals numerically, but never compare
different physical machines or assistance counterweights as strength.

## Decision graph

```text
athlete goal & constraints / current history / scoped preferences
  -> one Kotlin program + phase/dose/exercise candidate
  -> scientific bounds + equipment + time + cycle audit
  -> accepted program version / immutable issued session targets
  -> edit seed (source-specific; myo uses saved activation receipt)
  -> athlete record -> native owner-fenced durable journal
  -> factual History (separate by machine/day/technique)
  -> longitudinal evidence -> reviewed future candidate
  -> approved new version (never rewrite existing targets)
```

Every decision that can produce a **number** needs a durable provenance
record in the longer-term unified policy API: source kind, version, evidence
receipts, exercise/day/machine, unit/basis, constraint range, chosen value,
rounding/increment, explicit reason, and whether athlete consent is required.
Missing source or violated guard => refusal or request for a user choice,
not silent fallback to another numeric rule. UI projections should call
the same policy, not reimplement load/rep/scheme arithmetic.

## Continuous goal-directed program: intended next architecture

A future upgrade to Endless Plan should accept **goal** (hypertrophy,
strength, powerbuilding), training experience, training days and schedule,
available session time, split preference versus adaptive split, verified gym
and machines, injuries/reduced movements, focused muscle groups, and
progression/fatigue-coaching opt-outs. It should also learn from **durable**
logged performance, adherence, measured fatigue/readiness and recovery.
When the athlete permits it, the cycle planner can periodically reconsider:

- phase duration and progression (including hypertrophy -> strength -> peak
  when appropriate); deloads and realistic main-lift exposure frequency
- per-muscle direct/indirect sets versus explicit MEV/MAV/MRV constraints,
  target muscles, exercise pattern balance and equipment feasibility
- exercise selection and variants, session distribution and duration,
  rep ranges and RIR targets matched to movement/goal/phase
- load/rep progression based on comparable day- and machine-specific
  completed sets, not a single exceptional last set or unknown RIR
- rep-range or exercise plateau changes, substitution, and lower/upper
  session doses; progressive choices should be bounded and explainable

**Autopilot is future-version consent, not silent authority over old work.**
Offer permission scopes independently for changing the split, exercises,
working-set count, rep range, future load and phases, with per-muscle caps,
gym-inventory checks, injury overrides, time budget, and undo/review of
meaningful changes. Active sessions, manually locked elements, custom
pinned slots, saved evidence and physical machine histories remain fixed.
A missed session/reduced readiness may revise a future plan; it must not
pretend a set was completed or lower a recorded load.

## Acceptance evidence before calling this complete

A deterministic replay and matrix of generated and fully custom workouts,
3-6 days, each goal/phase/split, warmups, straight and authored back-offs,
shorter sessions, A/B machine swaps, Last/Target, Leveling and DP, repeats,
partial/failed sets, drops, 1-5 myo stages, corrections, offline/cold
restarts, gym stock changes, deload and next week. For every field compare
**issued target -> suggested seed -> athlete edit -> durable actual ->
history -> future proposal**, with the provenance and every refusal.
The web case (myo entries seeded from last prescribed row instead of the
saved activation) must be kept as a reproducible negative fixture.

The full UI, real Android device accessibility/restore tests and independent
web-to-Kotlin comparisons remain beta release gates. No APK until full UI.

## Current implementation checkpoints: numerical acceptance (partial)

The first hard consolidation is `KotlinSetNumberAuthorityDomain`, called
from the generated journal, custom journal, and shared numeric entry parser.
It defines one exact acceptance matrix for completed/skipped/failed work,
weight unit/range/precision, machine-ID syntax and nullable reported RIR;
the same inspected fact distinguishes an issued-load match from a different
athlete-performed load without changing the issued target. JUnit replays
both real journal formats through correction and factual History projection;
source contracts fail if a caller forks the policy.

This is **not yet the full numerical lineage audit**. Remaining work must
trace all source-backed rep/set/weight decisions through program generation,
custom authoring, block transitions and shortening, Leveling/Last shortcuts,
all five amplifier protocols, per-physical-machine and per-training-day
longitudinal coaching, proposed next-session numbers, and any future
automatic cycle optimizer. The later milestone should make a numerical
decision trace reproducible from committed receipts and approved policy
versions, and exercise that trace in paired random and adversarial simulations.


## Current implementation checkpoint: shared future-facing working-set facts

The canonical `KotlinWorkingSetEvidenceDomain` now calls the original
`KotlinSetNumberAuthorityDomain` before producing a `WorkingSetFact`
usable by any coach. It preserves original issued load and effort target,
exact athlete-entered actuals, null/unreported RIR, the physical machine ID,
and a read-only classification (same issued load, different performed load,
failed or skipped).

Both generated/custom **live next-set coaching** and generated **finished
History** use this same conversion. The custom-cycle next-session rep
assessment and future-load review use it too. Corrupt or incomparable
observations cannot turn into numeric progression evidence. All future
suggestions remain non-destructive proposals under separate review and owner
consent; this new bridge cannot save a program or change completed sets.

Still not complete: source-backed program/phase/set-count and rep-range
creation, exact physical stock/increment choices, the separate custom History
projection and amplifier-stage-specific outcome traces, fully authenticated
day/machine historical queries, and multi-week continuous-plan optimizer.
These are required by release-gate issue #786.

## Current implementation checkpoint: future working-load candidates (partial)

`KotlinFutureLoadNumberAuthorityDomain` is now the shared, pure numerical
acceptance gate used by both `CoachNextProgramLoadReviewDomain` (generated)
and `CustomNextLoadAmendmentDomain` (athlete-authored). They retain separate
evidence-qualification and source-revision gates, but no longer each decide
physical load direction, next verified equipment setting, increment budget
and numeric candidate validity. The returned `KotlinFutureLoadFact` records
the original and proposed loads, exact unit/basis, source kind and permitted
step, with **no permission to issue, save or edit a workout**.

- Earned external load increases must be **the next verified setting**, never
  a rounded guess or a leap to the last set's heaviest attempted weight.
- Earned assisted-machine progression goes **down** by one verified
  counterweight setting and obeys the separately authorized step.
- Overload recovery may propose a larger decrease only to a **physically
  verified earlier demonstrated load**, once the separate two-session
  evidence policy qualifies it and the athlete reviews the new version.
- Mixed physical machine IDs or source/day histories are rejected by the
  caller's evidence and revision fences; this numerical domain does NOT
  authenticate saved history or infer that one machine's stock fits another.

This is not yet a full shared program/cycle issuance engine: upstream
rep-range, volume, phase, Leveling, rep-forecast and longitudinal evidence
policies still require cross-route provenance and parity testing.

## Upstream generated-program numerical reconciliation (partial)

`GeneratedUpstreamNumberTraceDomain` now runs at the final reviewed
`GeneratedAdaptiveBlockDomain.propose` handoff. A source/inventory-valid
candidate is refused if **any** generated exercise, weekday, slot, working or
warmup set, rep band, target RIR, load basis, active time or rest time cannot
be accounted for by precisely one of:

- the purpose/day-specific authored exercise prescription;
- an exactly preserved protected athlete-authored source slot;
- a source-reviewed and independently opted-in Strength menu restoration;
- a separately permitted Strength working-dose repair repeating exactly the
  already authored Strength set, within the existing full-cycle guards.

The restored-Strength branch additionally proves the source slot's exact
working-set values (rep band, load, RIR, rest and active time), role,
machine/attachment constraints and exercise identity against the last resolved
Hypertrophy TRAINING week. Merely carrying the source slot ID is not sufficient.
A modified prior block or an unexpected source slot is refused, including
when its resulting numerical value is otherwise valid. Strength recovery
never uses the deload as a prescription baseline or an athlete performance
record.

The immutable ledger records the actual proposed number and its source class,
set ordinal, day/slot/exercise/phase, prior slot lineage and author day scope.
It also records the final resolved preceding *training* week versus the
candidate week's working-set and exercise-visit counts, skipping deload/test
bridges. A SHA-256 digest covers the exact input request, source revision,
exercise policies, resulting cycle and observed set facts for byte-sensitive
replay. This is **not** a new progression algorithm or permission to issue.
The complete proposed week now also undergoes an independent
`WeeklyDoseFeasibilityDomain` pass **after** generated prescriptions,
preserved athlete slots, source-menu restoration and separately consented
Strength dose repair. This rebuilds actual planned working-set exposures from
the final source-reviewed movement credits, including direct/indirect muscle
credit, declared phase-specific MEV/MAV/MRV, direct training days, session
rest durations and athlete time windows. Its typed `plannedWeekDose` result
is included in the candidate's exact replay digest. It is a **planning**
measurement, not a claim that the athlete completed those sets. Advanced
amplifier extension contributions remain outside the regular working-set
model and require their own reviewed dose policy.

Adversarial JUnit cases reject plausible but unauthored rep edits, silent
extra sets and stale source revisions, and assert separate Heavy vs General
Peak rep/RIR/rest and Strength-to-Peak numbers.

**Important evidence boundary:** this generator currently receives an
approved *program* and verified equipment, not authenticated actual workout
receipts. The ledger explicitly says `NOT_CONSUMED` for training-performance
history: a preceding programmed 27-set Strength week cannot be represented as
27 *completed* sets. Before whole-program automatic optimization or a claim
that every numerical decision can be reproduced from athlete settings **and
verified training history**, the next engine pass must bind versioned,
machine/day-comparable completed-session evidence, actual adherence and
performance to program construction and replay. Custom-program authoring,
independent history corrections and every amplifier protocol also remain
release-gate #786 scope. This trace is an additional *review gate*, not an
automatic plan change, an APK or device certification.


## Finished-workout replay inputs at the upstream generation boundary (partial)

`KotlinProgramHistoryReadoutDomain` now accepts only exact-source finished
Kotlin journals, plus the effective saved-correction state for a matching
journal. Every workout is replay-verified through the existing historical
projection and shared numeric acceptance policy before a factual observation
is admitted. It rejects unfinished/corrupt journals, mismatched source
revisions, duplicate workout identities, unanchored corrections and
out-of-scope program day indices. Observations preserve the original issued
load, rep band and RIR target separately from the performed weight, reps,
nullable actual RIR, physical machine ID, effective set event and corrected
predecessor. It never merges distinct physical machines into one comparable
session; unknown machine identity cannot earn machine-specific progression.
No recorded date/chronology is guessed from workout ID or input ordering.

The generated-block request can optionally supply this read-only actuals
ledger. The existing upstream numerical trace validates matching athlete
and cycle identities, stores its exact deterministic replay digest and
attaches it to the output without changing the source cycle, generated sets,
phase or approved workout. Without it, the earlier no-history behavior is
unchanged. With it, the status is explicitly
`REPLAY_VERIFIED_NOT_STORE_ATTESTED`: a replayed reducer/projection is NOT
cryptographic or owner-fenced proof of a durable native storage write. The
history is **not yet consumed to choose** exercise patterns, frequency,
rep ranges, loads, working sets or phase; no such adaptation is authorized
from this bridge. The next production boundary must authenticate the storage
transaction and source revision before a versioned, permission-scoped future
program optimizer may consume these observations. This does not close #786
or #783, does not prove end-to-end parity or replace real-device acceptance.


## Native storage-owner history bridge for program review (read only)

The production Android host exposes `reviewNativeProgramHistory` through
`NativeKotlinGlobalHistoryReadCoordinator.readProgramHistory`. It uses
the **same** owner-locked, full-manifest history reconstruction that powers
native History, and verifies every retained completion and saved correction
*before* selecting the exact issued-program source epoch (athlete, program
revision, gym, catalog and coaching rules). A corrupt or absent workout in
an unselected epoch still blocks the entire review; it cannot be hidden by a
caller-supplied list or a filtered UI screen.

The output includes factual per-set replay (prescription vs logged actual,
nullable actual RIR, effective corrected receipt, physical machine and
training day), the immutable global History content fingerprint and
manifest revision, plus real native completion ordinals. Workouts from
different program/gym/source epochs are never silently averaged together.
This preserves a verifiable ordering reference even though the underlying
replay ledger intentionally does not guess training chronology from lexical
workout IDs.

This is a **read-only** authenticated evidence boundary, not a signed
permission to edit the athlete's program. It grants neither adaptation
nor changes to issued workouts. The next numerical authority step is to
make the program authoring pathway itself consume the verified source
evidence with explicit athlete-selected adaptation scope, fatigue/lock
preferences and independently tested rep/set/phase policy. No change to
the current program or an APK release is authorized by this bridge alone.


### Native saved-correction machine provenance (codec v2)

The authenticated-history regression exposed an actual persisted-correction
gap: the earlier saved-history correction codec stored loads, reps and RIR
but **discarded physical machine IDs**. The corrected private codec writes
version 2 with each effective saved set's physical-machine identity and
also includes original journal receipt machine IDs in the source
fingerprint. A correction on press Machine A must not turn into an
unknown or shared-machine observation after an offline restart.

Existing version-1 files are still read with their exact old fingerprint
and without inventing machine IDs they never stored. Only a separately
committed new edit upgrades the file to version 2; a read or exact
lost-acknowledgment retry does not rewrite the original. V1 encoding with
non-null machine IDs now fails closed rather than silently dropping them.
Tests cover cold restart, set load/target-RIR distinction, and V1-to-V2
upgrade with unknown historical machine identity retained as unknown.


## Owner-replayed performance threads for future program decisions

`KotlinProgramPerformanceEvidenceDomain` is a read-only view over the
**existing authoritative** effective workout/correction ledger. It is
constructed by `NativeKotlinGlobalHistoryReadCoordinator` inside the
same complete-manifest owner fence, using the native store's actual
completion ordinals rather than ordering records by workout IDs. Each
performance thread is scoped to an exact historical program-source
revision, weekday, session purpose, exercise, physical machine, load
unit and load basis. The latest three relevant exposures are checked,
including *failed, skipped or time-shortened* workouts.

The only favorable signal is
`REP_CEILING_FOR_REVIEW_ONLY`: three completed and comparable exposures
with unchanged prescription/working load all met their issued rep
ceiling while reporting **actual** effort at or above the corresponding
target RIR. If effort is unreported, if one set fails or is skipped,
if the prescription changes, or if a newer ambiguous machine visit
could belong to that thread, the favorable signal is suppressed.
Distinct explicitly identified A/B stations and weekdays have
independent histories, without borrowing one another's capacity.
A free-weight load basis does not require a physical machine tag;
machine/Smith/cable/assistance loads do.

This is an *observational programming input*, not a second system that
adjusts reps, sets, loads, volume or phase. It does not infer training
dates, recovery state, athlete effort or permissions. All returned
objects explicitly prohibit changing an accepted plan or issued
workout. Later goal-driven adaptation must use the same authentic
source, reviewed constraints and permission gates, then independently
re-audit the entire future candidate and its numeric lineage before
the athlete can accept it. This by itself does not close #786 or #783.


### Effective saved corrections invalidate prior performance signals

Cold-open native owner-history regression now checks **three** distinct
persisted correction scenarios after three previously qualifying
same-day/same-machine workouts. If a finished workout's saved record is
corrected to fewer reps at near failure, its old favorable repeated-rep
ceiling signal must disappear on the next authenticated read. If a
correction changes one working set to Machine B, the source Machine A
session becomes noncomparable and must not falsely qualify. A corrected
actual RIR of `null` remains UNKNOWN even when the originally issued
target RIR was 2; the favorable signal is withheld.

The original immutable issued prescription, saved correction predecessor
receipt, effective event ID, physical machine, and History content
fingerprint remain independently auditable. An older favorable readout
is a stale **review snapshot**, not a durable eligibility token; future
program adoption must re-read the owner manifest, saved corrections,
consent and source revisions atomically before accepting any proposal.
These tests do not enable automatic programming.

## Historical calendar weekday and role — single Kotlin performance authority

The native program-history replay now carries both the **actual issued
calendar weekday** and the **program day index**, as well as the original
issued exercise role and session purpose for every recorded working set.
The replay's schema/digest is version 2 so a stale version 1 observation
cannot masquerade as a complete weekday/role record. The native owner
still authenticates its complete stored manifest and correction receipts
before returning the factual ledger.

This information flows through the **existing**
`KotlinProgramPerformanceEvidenceDomain` — *not* a second rep/set/fatigue
decision engine. Its three-visit same-machine/same-purpose evidence threads
now also separate actual weekdays and issued exercise roles, with
`CHANGED_WEEKDAY_OR_ROLE_AFTER_LAST_MATCHED_VISIT` preventing stale
performance from an older schedule/role after a newer exposure.
Corrupt records whose set weekday or purpose contradicts the issued
workout fail closed. A missing station cannot conceal an otherwise
skipped, shortened, failed or incompatible training session; untagged
free-weight exercises remain distinguished from machine-stack lifts.

This is a necessary upstream numerical provenance boundary. It **does
not** derive new rep bands, set counts, exercise selections or block
transitions, and grants no program edit, issued workout change or phase
promotion. The next future-only policy must consume the same owner-verified
performance report with reviewed goal, full scheduled-week adherence,
machine/day continuity, athlete adaptation scope and locks. Custom
journals retain their separate authenticated history path until a
lossless parity adapter is reviewed. Keep #786 and #783 open.

## Owner-verified History to future generated training cells (read-only)

The generated upstream-number trace v2 records BOTH calendar weekday and
the 0-based program-day index for every proposed set, including protected
athlete-authored work and restored source exercises. Those are distinct
identities: e.g., an Upper-day bench cannot borrow Push-day performance just
because the exercise ID happens to match.

`NativeKotlinGlobalHistoryReadCoordinator.readGeneratedFutureHistory` is the
native read-only bridge: first reopen the **complete** durable owner History
manifest with every saved correction under its owner fence, then generate the
regular future block from the reviewed athlete source **without trusting an
externally supplied history replay**. The single
`KotlinProgramPerformanceEvidenceDomain` remains the history decision
authority. The `KotlinFutureProgramPerformanceLinkDomain` only associates its
already-classified threads with exact future cell identity: program day,
weekday, purpose, role, exercise, physical Machine A/B instance, load unit
and basis. The future authored working-set count, per-set rep bands, target
RIR and actual source-equivalent issued loads must agree with the latest
matched prescription. Advanced follow-up protocols remain separate stages
and cannot gain ordinary straight-set rep-ceiling credit.

A mismatched station, stale source revision, different load, changed
prescription, unreported effort, newer correction, or incompatible weekday
holds the association. No synthetic training observation, target, RIR, load,
stage or compliance date is created. The owner manifest revision, content
fingerprint and exact replay/trace digests are included for explanation and
future revalidation; this read result is **not a durable authorization token**.
It never changes issued workouts, grants automatic cycle edits, measures full
scheduled-week adherence, or promotes Strength/Peak. Full progression and
continuous goal-driven adaptation remain gated by #786 and #783.

### Canonical historical report replay at the future-number boundary

A report carrying a matching `sourceReplayDigest` is **not** authenticated
proof of its own conclusions. The future-number link must independently run
`KotlinProgramPerformanceEvidenceDomain.review` on the effective journal
ledger and the exact owner-manifest `completionOrderByWorkout`, then compare
the complete canonical result (every key, visit, gate and order) against the
supplied report. Forged favorable rep-ceiling gates, invented workout IDs,
duplicate or shuffled completion orders fail closed. The native coordinator
provides this order only after private journal/correction and manifest review.
The public pure Kotlin function is a **consistency review**, not independent
proof of private storage ownership.

The durable global-history manifest supports an **optional** native
`completedAtEpochMillis` Finish observation, but the current future-program
history projection does not retain that timestamp or a verified schedule-week
identifier. In particular, older undated receipts and three successively
completed visits are NOT proof that a full scheduled week was completed.
A future date-aware projection must preserve owner source, local timezone,
and every missing or conflicting date before it can even report observational
calendar coverage. It cannot independently authorize weekly adherence,
muscle-volume, set-count, rep-band or phase automation.

### Observed local training weeks from native Finish receipts (read-only)

The owner history coordinator now preserves the original **optional** Finish
timestamp for every selected source-epoch workout, alongside its complete
manifest completion ordinal. `KotlinObservedScheduleWeekDomain` projects
dated observations in the athlete's reviewed IANA timezone, with
Monday=0 through Sunday=6, preserving the exact issued training day.

- A calendar week can be labeled `FULL_SCHEDULE_OBSERVED_FOR_REVIEW_ONLY`
  only when there is exactly one fully recorded unshortened issued workout
  on **each** selected weekday and its dated local completion weekday agrees
  with the issued weekday.
- Skipped/failed/shortened work, duplicate visits on one weekday, a
  mismatched local completion day or missing weekday produce only partial
  observations. Earlier undated receipts or contradictory completion-clock
  ordering downgrade the dated week to `CHRONOLOGY_UNVERIFIED`; they are
  never assigned a fabricated date or silently dropped.
- A completed session may span midnight, and device clocks or manifest
  recording may disagree with its scheduled day. Such records are retained
  but not counted toward exact-day observational coverage.
- Absence of a recorded workout does not, by itself, prove a scheduled week
  existed, was activated, or was skipped. The owner still needs verified
  program activation/week boundaries, clock attestation and policy review
  before full schedule adherence or automatic muscle-volume/frequency/
  rep-range/phase evolution can be authorized.

The observed-week result and all future-number links are **read-only**.
They cannot update issued workouts, approve a cycle, claim missed weeks or
automatically apply higher/lower loads or sets. The user-selected automatic
scope and full program-cycle audit remain separate release gates.

### Cross-week evidence for future goal-driven coaching reviews

`KotlinContinuousPlanEvidenceDomain` now pairs the **existing** canonical
per-exercise `REP_CEILING_REVIEW_ONLY` thread with the **existing** local
Finish-date observations. Three reported rep ceilings on the same day cannot
be misread as three full training weeks.

An exploratory rep-range coaching review signal can appear only when the
three exact matched workout IDs fall on **three different consecutive
Monday-based weeks**, every week has exactly one complete unshortened
workout on every selected training weekday, the records match each issued
weekday and day index, chronology is intact, and there is no newer partial
observed week superseding the sequence. A missing date, incomplete week,
duplicate day, stale sequence, intervening gap, contradictory clock, changed
machine, exercise or prescribed load keeps the signal guarded. No new
exercise, rep/weight/set amount, RIR or scientific fatigue conclusion is
inferred from these observations.

The summary is linked only inside the native owner-history coordinator,
which first reopens the complete private journal/correction manifest and
recomputes historical performance under the existing Kotlin authority.
The pure summary is **not an owner attestation** on its own and
`EXPLORATORY_REVIEW_ONLY_ACTIVATION_REQUIRED` is deliberately not an
approval to change the athlete's saved future program. The source program
activation epoch, authorized calendar/week provenance, complete multi-block
coaching audit, controls for protected/manual work, and explicit athlete
scope are still separate gates for the continuous plan (#783). Engine
numerical release/parity evidence (#786) is likewise not complete.

### Historical week versus future training-day calendar changes

A new adaptive split may legitimately replace a previously issued calendar
day (e.g., Mon/Wed/Fri to Mon/Thu/Fri). The owner-sourced original Wednesday
receipt and its dated actual remain true; it cannot become Thursday by
remapping its 0-based program-day index. The observed-week reviewer now
preserves original dated sessions but records the exact incompatible workout
IDs and marks `SOURCE_SCHEDULE_INCOMPATIBLE` instead of rejecting that
otherwise valid history or declaring the new week complete. All week-day
completion credits are withheld for that source epoch, even if other days
appear complete; three old rep-ceiling visits cannot generate a new-calendar
corroboration signal. An actually invalid issued weekday/index remains an
input refusal, not a legitimate schedule migration.

This is strictly an observational boundary, not a substitute for a verified
approved source schedule or a written, versioned program activation date.
Until the native owner can attest the correct current/previous block calendar,
no split change may silently borrow old training volume, frequency, or
compliance toward automatic future program evolution.

### Native custom phase-boundary historical completion proof

A durable custom workout `Finish` event means the athlete resolved its
required visit records; it does **not** imply all originally issued working
sets were actually completed. Skipped sets, failed attempts and valid early
stops are factual completed journal **sessions**, not complete prescribed
working-set exposures.

Future-block activation preflight now uses the existing independently replayed
`CustomWorkoutHistoricalEvidence` to determine whether **every issued MAIN
working set** in each prior-block session has a completed actual. A selected
shorter-session mode or omitted original sets is not full-prescription
completion. Optional myo-rep, drop-set and rest-pause follow-up visits remain
distinct recorded stages and cannot substitute for MAIN work; a safely stopped
optional stage does not invent another required normal set.

The source-authenticated absolute program week, block and weekday must still
match the exact prior-block schedule, without missing or duplicate sessions.
If a journal is finished but prescribed MAIN work is incomplete, the future
activation boundary returns `INCOMPLETE_HISTORY` instead of a favorable
phase-transition review. History is preserved and an athlete can still view
the genuine skipped or failed workout. This does not infer activation **dates**,
give permission to edit issued workout numbers or authorize automatic phase
promotion. The independent clock/activation-epoch and goal-driven optimizer
work remain open under #783 and #786.

### Native future-block calendar corroboration (owner Finish receipts)

Future custom-block activation now cross-checks the complete set of
independently reopened, owner-manifest **Finish timestamps** against the
original issued `absoluteWeek` and Monday=0 `weekday` on each
authenticated custom workout. Only after every expected prior-block
session and its issued MAIN working sets are accounted for does this
read-only boundary check:

- Require a valid IANA timezone from the accepted prospective athlete
  context and a present, valid Finish timestamp for **every** prior-block
  completion. Undated legacy receipts return `CALENDAR_UNVERIFIED`.
- Convert each timestamp to the athlete's local day and refuse a receipt
  whose local **Finish** weekday differs from its original scheduled
  weekday (`CALENDAR_CONFLICT`). No UTC-day guessing or retrospective
  remapping across split changes is allowed.
- Independently calculate the candidate first-week Monday implied by
  each original absolute-week number, and require all completed prior
  weeks to agree. A logged week 1 cannot masquerade as week 2 simply
  because the athlete finished it later.
- Preserve the existing complete custom receipt replay, exact
  block/week/day roster, skipped/failed/shortened guards and separate
  outstanding-workout inventory checks. No additional training number
  policy or device-clock fallback is introduced.

**Important:** This verifies *observed calendar consistency*, NOT an
independently persisted athlete-approved program activation timestamp.
A long-delayed Finish after midnight may be valid athletic work yet
fail the conservative boundary; its historical record remains intact.
A future owner-transactionally persisted, versioned activation/start
calendar, reliable scheduled-session identity, and exact consent
revalidation are still required to authorize automated goal-driven
week or phase changes. This checker grants no workout issuance or
automatic load, set, rep, exercise or phase edits.

### Immutable approved start Monday for an already activated future block

`NativeKotlinActivatedBlockCalendarFileStore` now supports a separate
private, owner-fenced, one-time **athlete-confirmed block calendar**. This is
not an inferred activation epoch, and it is not yet a UI/issuance gate.

- A calendar preview only exists after the existing write-ahead
  future-activation transaction has successfully applied the source CAS.
  It reopens the exact *currently active* source and saved authored cycle,
  checks the target block's position/phase and scheduled weekdays, and
  binds source/draft SHA-256 content digests, both revisions, athlete/owner,
  IANA timezone, absolute first-week number and selected local Monday.
- The athlete must confirm the exact preview fingerprint. The transaction
  captures its own valid system/approved clock observation. An invalid,
  missing or unknown clock refuses acceptance. Start Monday must be a true
  local Monday in the current or next twelve training weeks, not an
  arbitrary backdated week, and old phase sessions are **never** given
  newly inferred dates.
- The accepted record is written once using a checksum-checked, versioned
  binary and atomic rename under the same native owner transaction fence
  as the source and custom journals. Confirming the identical record can
  replay idempotently; attempting a different Monday refuses replacement.
  Cold reads re-verify the currently applied activation, saved source and
  draft digests, revision, timezone and exact scheduled days. A changed
  program or owner refuses current-calendar status; corruption refuses.
- The preview, accepted record and cold read cannot issue a workout, remove
  the future handoff barrier, change training loads/rep ranges/set counts,
  claim adherence or infer an original cycle's historical activation.
  An approved **block** start Monday is not proof when earlier cycle blocks
  originally began. The independent full-cycle coaching signoff, workout
  issuance, schedule/missed-session policy and every future numerical
  change remain separate, explicit gates.

The native UI has not yet exposed this confirmation flow or linked it into
workout issuance and the full backup inventory. It is a hardened durable
authority primitive, **not completion of goal-driven autopilot**. Backup and
restoration integration, migrations, first-block activation, source-consistent
week replay, time-zone travel handling and real-device testing remain
required before using the anchor for any automatic program decision.

### Complete owner calendar-file inventory during encrypted restore

The encrypted native training snapshot already carries private calendar
record bytes. The **staged core restore audit** now separately enumerates
**every physical** `activated-block-calendar-*` entry, including archived
records for superseded cycles. It verifies the strict owner/cycle/block hash
filename, file type (not a symlink), versioned length/checksum envelope,
internal content fingerprint, real owner/athlete, original approved Monday,
cycle/source/draft revisions, IANA timezone, day schedule and uniqueness.
A malformed, interrupted `.pending`, renamed or foreign calendar is a
`FUTURE_CALENDARS_UNVERIFIED` recovery refusal, **not** a silently omitted
file. An archived but well-formed record may be retained and reported as
`staleFutureBlockCalendars`; it is *not* currently approved, and normal
`read` still refuses it when the active source/draft no longer matches.

The independent staged audit records separate counts of currently matching
and archived calendar records. The encrypted clean-store test covers
owner-approved future source transition → calendar acceptance → complete
snapshot capture → isolated staged byte verification → atomic restore →
cold read of the **exact same** calendar and preserved issuance barrier.
It also proves that corrupted date bytes stop the staging audit before
publication. The backup does **not** make checksum authentication into
cryptographic source attestation or authorize any training edit. Production
UI selection, first-block activation, full historic plan adherence and
automated muscle-frequency/phase changes remain distinct open work.

### Independent native activation commit-intent restore census

The second-consent namespace (`future-activation-<hash>.bin`)
and the post-consent source-switch WAL namespace
(`future-activation-commit-<hash>.bin`) share a textual prefix but
**not** a binary schema. Staged recovery now scans them independently:
the archived approval inventory examines only exact second-consent
names, while `NativeKotlinFutureActivationCommitTransaction.inventoryOwner`
accounts for every physical commit-intent file under its own prefix.
Commit inventory requires the original owner-hashed path, regular
unsymlinked file, valid write-ahead envelope, exact athlete/handoff,
and a verified `Prepared` or `Applied` source-CAS state. Missing intents
are legitimate when no source transition began. Corrupt, foreign,
renamed, conflicting, or interrupted `.pending` intents are a separate
`FUTURE_ACTIVATION_INTENT_UNVERIFIED` refusal, never discarded as
irrelevant or confused with consent. A correctly `Prepared` intent
remains a source-change barrier during recovery; an `Applied` one
preserves its original approved source and cannot be replayed as new
athlete consent.

These checks run on the staged data before a restored store is
atomically published. They preserve existing backup bytes, do not
perform activation, cannot grant workout issuance, and never silently
remove the athlete's future-handoff barrier.
