# Pursuit Iron — old-engine defect elimination ledger

**Blocking acceptance policy:** The Kotlin replacement must not ship with any
*known* legacy defect, and must be demonstrably better in programming,
progression, reliability and native usability. Absolute zero bugs cannot be
promised, so every reproduced defect needs a minimal counterexample, a failing
regression first, a Kotlin fix, complete storage/Android integration tests
where applicable, and a real legacy/Kotlin paired measurement.

**Statuses below describe implementation evidence, not claims that the full
native product is fixed.** A JVM test is not device acceptance. "Partial" or
"Open" is a release blocker, not a postponed nice-to-have. The source of
older bugs is user-reported; do not assert the frozen JS baseline still fails
without executing it.

| ID | Legacy issue / adverse outcome | Kotlin safeguards or test anchor | Current gap |
|---|---|---|---|
| L01 | 200×3, 205×3, 210×2, 215×2 against 5–8 becomes 215 for **all** next sets | `ConservativeProgressionReview`, `OverloadRecoveryLoadReview` and real history-backed acceptance | Need paired JS run, separate all-set fatigue-aware prescription and device path |
| L02 | 215×2 near failure repeatedly offered as earned weight | Actual RIR, below-floor and observed uniform-load checks; `CoachExerciseDecisionDomainTest` | Need longitudinal individualized plateau/recovery policy and paired execution |
| L03 | Unavailable 38.5/159.5 lb weights on 5-lb equipment | `EquipmentLoadDomain`, native gym coherence and issuance tests | Need exhaustive device/equipment variant grid and real app data migration |
| L04 | A set below the rep floor still causes aggressive future progression | Evidence-gated reviews and history correction revalidation | Need full athlete-specific fallback progression |
| L05 | 14 reps within 10–15 or +3 reps on the very next set | `FatigueAwareRepTargetReviewDomainTest`, `KotlinAdaptiveSetPrescriptionDomainTest`, and disk-backed native acceptance/issuance tests: at most +1 on one earned set, exact weights, unchanged RIR/rest and other exercises, fail-closed after history corrections | Candidate now supports a versioned durable acceptance and guarded issuance; **remaining** multiweek rep calibration, custom/set-technique parity and device validation |
| L06 | Near-failure or unknown actual RIR treated as spare capacity | `FatigueAwareRepTargetReviewDomainTest`, `ConservativeProgressionReview` | Need complete client RIR capture and historical import rules |
| L07 | App displays the same target RIR all through hypertrophy without phase-dependent intent | Current session-purpose and prescription policy checks | **Open**: individualized changing RIR progression and UI display |
| L08 | Next set weight silently changes before the athlete logs it | `InWorkoutCoachDomain` explicitly preserves user edits and issued target | **Partial**: test actual Compose set editing, interrupted workouts, cold resume |
| L09 | Equipment-specific minima (e.g. DB curls start at 25) not honored | Current native gym stock and exact-load inspection | **Partial**: all exercise/variant minimum loads and gym UI |
| L10 | Custom block loses myo/drop/set techniques or rest details | Custom cycle authoring and audit review domains | **Partial**: complete custom technique parity, device acceptance |
| L11 | Strength phase drops ~30 weekly working sets to ~11 without a reason | `CustomCycleCoachAuditDomainTest` cross-phase continuity policy | **Partial**: actual generated multi-block strength and volume adaptation |
| L12 | Custom-to-adaptive cycle changes pinned split and exercise intent | Custom cycle provenance and transition audit | **Partial**: explicit adapt-split and adapt-exercises control, approval UI |
| L13 | Workout previews include equipment absent from selected gym | Native gym/catalog coherence and curated exercise picker | **Partial**: every preview/variant and device route |
| L14 | Advanced 75-minute session contains only four exercises without meeting dose | Weekly dose/time feasibility checks | **Open**: calibrated advanced session generation and workload quality |
| L15 | 40–60-minute bucket systematically hugs 40, regardless of athlete targets | Session window and dose feasibility reviews | **Open**: accurate elapsed time and preferred duration distribution |
| L16 | Incorrect warmup, amplifier, drop/myo rest semantics | Authored technique and prescription constraints | **Partial**: complete actual in-workout amplifier implementation |
| L17 | Previous/Last/history target mismatch, edits rejected or lost | Durable native completion, correction and full-history coaching tests | **Partial**: UI synchronization and restart/restore device test |
| L18 | Change or sign-out leaks another athlete's history or program | Owner-fenced native approval/history/journal and invalidation tests | **Partial**: full migration and account-switch Android validation |
| L19 | New program approved against obsolete physical gym inventory | `NativeKotlinApprovedGymCoherenceDomainTest` and real-file issue/accept tests | **Partial**: unify durable athlete/gym/catalog/approval revisions |
| L20 | Regimen has ineffective exercise variety, strength lift frequency, or arbitrary volume | Composer, coach dose audit, cross-block checks | **Open**: blind human-coach superiority and all 4 splits across cycles |
| L21 | Revisions produce 0 or inconsistent set/target data | Kotlin snapshot staging and immutable journal tests | **Partial**: all warmup/amplifier/focus devices, backup/restore |
| L22 | Android UI glitches, overlapping controls or unreadable dark themes | Isolated Compose/M3E candidate with shared components | **Open**: full screens, 20 themes, accessibility, phone/foldable UI/device testing |
| L23 | Backup/restart or stale cached progress overwrites real history | Native owner fence, manifest and redo/correction tests | **Partial**: scalable single-authority 4097+ log history, directory fsync, import |
| L24 | Original custom program cannot be edited as richly as a good coach would write it | Native custom source editor, split and cycle drafts | **Open**: complete premium UI, arbitrary custom structures and safe mutation |
| L25 | No objective proof the new engine beats the existing web coach | `verification/engine-superiority-gate.mjs` and pinned bilateral scenarios | **Open**: actual matched JS vs Kotlin runs, device and blinded coaching |

## Definition of done for each defect

1. Verify the *actual* legacy baseline or record the defect explicitly as a
   historical report with reproducer and uncertain current status.
2. Write a minimal deterministic failing regression or real-device scripted
   scenario; include negative cases for missing RIR, stale source, invalid
   stock, skipped sets, recovery and user-edited prescriptions.
3. Fix it **at the production ownership path**. A review-only Kotlin helper
   does not count as full resolution if issued targets, program generation,
   history edits, or Compose output still behave incorrectly.
4. Add crash/replay/restart and account isolation tests wherever data changes.
   No mutating target, grant or logging evidence without a versioned owner.
5. Execute both engines on the **same inputs**, pin revisions, retain artifacts,
   and measure quality with independent expected results. UI needs Android
   device evidence; programming quality needs blinded coach review.
6. Require zero known unresolved P0 regressions **and** full feature parity
   before cutover, while adding real improvements to the coach algorithm.
   Never lower the benchmark or delete failing cases to make Kotlin pass.

## Native set-specific next-session prescription (candidate)

`KotlinAdaptiveSetPrescriptionDomain` first recomputes the **entire** currently
approved workout from `KotlinWorkoutSnapshotDomain.stage`. When full,
comparable, exactly loaded historical working sets have actual RIR and at
least one set has enough demonstrated headroom, it produces a concrete
**immutable full-workout snapshot** with exact set-specific rep targets.
It does not change a single load, unit, equipment basis, RIR, rest, warmup,
other exercise, training day, journal or historical observation. Reps remain
inside the approved practical rep envelope, and only one actual working set
can earn an additional rep. Missing effort, near failure, skipped sets,
edited/mixed loads, an unaudited set scheme, or stale source/history fails
closed. This prevents copying a strong first set onto a fatigued last set.

`NativeKotlinMixedHistoryReadCoordinator.reviewCurrentNextSetTargets`
reopens the full native completion/correction ledger, identifies the
**most recent** exercise appearance without skipping intervening custom
work, and checks the currently approved program and actual available gym
under the owner lock. It refuses review if the next workout's immutable
journal has already been issued. The native host can request this read-only
proposal off-thread.

**New isolated native acceptance path:** The candidate Coach screen
requires explicit set-by-set authorization. The private approval file now
stores the accepted proposal and original historical revision in a **version-2**
binary format, while retaining version-1 decoding. The acceptance writer
reopens the full corrected ledger and current gym, rejects already issued
workouts, and compares every UI-presented rep against a new deterministic
proposal. A fresh grant and approval storage revision are persisted; the old
journal is never modified. The issuer must independently re-derive the exact
approved set targets twice under its owner/journal locks. If history changes
after acceptance, physical stock disappears, or the history verifier is not
connected, issuance refuses rather than silently falling back to generic
ranges. Regression tests cover successful cold-open issuance and history
correction after acceptance.

**Still not production-complete:** This is the isolated candidate, not the
production app migration or physical-device acceptance. The v2 history ledger
is bounded, directory fsync/backup-restore are not certified, account switching
and restart behavior need device runs, and subsequent fixed-target sessions
need their own longitudinal recalibration policy. A retained original program
source revision is supplemented by approval storage CAS here; the future
unified athlete/gym/catalog/program owner must reconcile all revision
semantics. The actual frozen JS/Kotlin benchmark is not yet completed.

The engineering goal is **strictly greater real-world quality**, not a
one-sided claim that any headless Kotlin test makes it superior.
