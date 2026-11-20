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
