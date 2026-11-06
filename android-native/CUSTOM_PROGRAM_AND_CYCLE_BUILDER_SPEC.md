# Pursuit Iron — native custom-program and cycle builder

## Product requirement

The Kotlin/Compose replacement must create genuinely custom programs and cycles
**better than the web version**. It must not make an athlete rebuild a generic
template after selecting “Custom,” and a cycle change must never discard chosen
exercises, sets, per-exercise RIR, attachments or amplifier settings.

An athlete can create from **Blank**, **Start from a split**, **Duplicate my
program**, or **Build a training cycle**. These are entry routes into ONE
first-class, versioned athlete-owned blueprint, not divergent legacy paths.

### Native authoring flow

1. **My program**: name, goal, custom schedule (1–7 chosen weekdays), optional
   named split or arbitrary user-defined split; choose ordinary program or
   multi-block cycle. A custom plan never requires choosing Full Body/UL/PPL.
2. **Week canvas**: swipe/reorder day cards with custom labels (Upper A / Pull /
   Day 1 / etc.), purpose and estimated duration. Drag exercises within or
   between sessions; preserve stable slot identity, not array index, and
   signal when a move affects recovery or muscle volume.
3. **Exercise picker**: searchable catalog filtered by **actual confirmed
   gym equipment**, permitted variants, facilities and attachments. Display
   verified load basis (per hand, machine stack, total barbell, Smith, etc.)
   and real attainable increments before exercise insertion. Unavailable
   exercises stay explainable rather than silently substituted.
4. **Set editor**: a compact card with editable individual warmup and working
   sets; per-set load, unit/basis, reps/range, RIR, rest and warmup. One action
   copies a set or repeats a scheme. A simple-first UI exposes advanced
   controls progressively: drop set, myo-reps, rest-pause, clusters, attachments,
   superset pairing, exercise notes, and **Pinned**. Amplifiers belong to the
   exact set, never float in a generic exercise drawer.
5. **Cycle timeline**: add named blocks (Foundation, Hypertrophy, Strength,
   Peak, Deload, Testing, or custom labels), set week lengths and day/focus
   structures independently. Duplicate previous block with the complete
   exercise/variant/set detail preserved, then edit only what changes.
   Any week can override one exercise slot without rewriting other weeks or
   deleting that block's template. Visual “this week only / all weeks in block /
   future blocks” scope is mandatory.
6. **Coach audit**: live but non-blocking warnings for gym feasibility,
   estimated duration, muscle-group MEV/MAV/MRV, heavy-pattern frequency,
   recovery, movement variety, rep targets and overload. No invented
   science-default thresholds. Author-supplied policy and verified athlete
   history govern interpretation; warnings distinguish *unavailable*,
   *conflicting*, *less optimal*, and *user-chosen*.
7. **Compare and approve**: present complete per-block/weekly diff before
   accepting any adaptive coach suggestion. Select which changes to accept;
   “Keep my exercise”, “Keep this weight/rep scheme”, and Undo must be
   granular. The author can intentionally override coach preferences, but
   the app cannot pretend unsafe/unavailable equipment is verified.

### Custom ≠ static: adaptive cycle control

The athlete owns **different permissions** for changes to goal, block schedule,
phase, split, exercise choice, pinned movement deletion, attachments, supersets,
working-set counts, loads, rep targets, RIR, rest, amplifiers, notes, and
week-level overrides. Every permission defaults OFF for coach suggestions.
“Adapt my exercises” must not imply permission to remove pinned movements;
“Adapt weights” must not rewrite sets or target RIR.

Coaching can propose improved progression and phase-specific exercise
frequencies (e.g. bench twice per week in strength) without silently
halving the entire hypertrophy volume or deleting custom amplifiers.
No proposal automatically changes an active issued workout, past history,
or completed evidence. Automatic athlete acceptance is a **separate,
future, authenticated policy**; this pass always returns review-only.

### Implemented Kotlin foundation on this branch

- `CustomTrainingCycleDomain`: fully authored, deterministic `CustomTrainingCycle`
  with blocks, freeform splits, ordered weekday sessions, stable exercise slots,
  exact per-set prescriptions, pinned exercises, gym attachments, superset
  membership, set-linked amplifier protocols, and per-block week overrides.
- `review()` checks id/bounds, unique slots and days, coherent weekly
  overrides, exact load/unit/basis attainability through the verified stock
  domain, availability of chosen exercise variants/attachments, amplifier
  compatibility and meaningful superset group sizes; returns an immutable
  review copy only when valid.
- `week()` projects the **exact** authored week and only its explicitly
  modified exercise slots. It never runs a searching composer to quietly
  replace exercise order or changed volume.
- `CustomCycleBuilderDomain`: revision-checked blank-cycle creation, day and slot insertion, set-level edits, block cloning and exact weekly overrides; undo requires the precise prior receipt. The week-canvas domain now also moves complete exercise/superset groups between existing days while preserving stable slot IDs, pinned status, notes, attachments, amplifiers, and migrating matching per-week overrides to the destination weekday. It supports inserting and reordering blocks and renaming a day without changing its schedule. These are **headless proposed draft changes**, not a Compose drag-and-drop editor, persisted autosave or workout approval.
- `CustomCycleTransitionDomain`: granular field-level change review with
  separate athlete-origin and coach-origin semantics and explicit
  `CustomCycleCoachPermissions`. Every proposal remains review-only.

### Candidate native draft persistence (source-only, not a live editor)

- `NativeCustomCycleDraftCodec` serializes full athlete-owned blocks, custom splits, stable exercise slots, ordered sets, physical load basis, warmups, RIR, rest, attachments, pinned status, superset groups, set-linked amplifiers, coaching notes and individual week overrides without JavaScript or Java object serialization. It also permits explicitly **unfinished** drafts such as blank cycles.
- `NativeCustomCycleDraftFileStore` stores a private, versioned, checksum-checked draft by authenticated owner + cycle ID, sharing the approval/issuance transaction fence. Save requires the exact prior revision, uses temporary-file atomic replacement and rejects corrupted existing bytes. Delete retains a revision tombstone so queued old autosaves cannot silently resurrect the program.
- The isolated `NativeKotlinCandidateHost` now exposes read/save/delete entry points, but the production Compose program editor does not call them. These APIs do **not** authenticate training policy, approve coaching, issue workouts, mutate JS workouts or migrate existing web programs.
- JVM tests cover complete custom-cycle roundtrip, blank draft roundtrip, cold restoration, CAS conflicts, deletion, owner/athlete isolation, metadata refusal and corruption. Passing JVM CI is not yet Android device power-loss, Compose auto-save, user acceptance, or coaching approval proof.

### First Compose custom-cycle editor (candidate-only)

- `NativeKotlinCustomCycleController` performs all native draft reads/writes on an IO coroutine. It reopens only acknowledged records, rejects stale revisions, and presents refused saves without treating the proposed UI state as durable. Undo is a new revision-checked saved operation, not a rollback of historical training facts.
- `NativeKotlinCustomCycleCandidateScreen` uses shared Material 3 components and the original theme palette to create a fully blank custom cycle, open an existing cycle by ID, edit its name/notes, add named training days, change a day's name and purpose **atomically**, insert/duplicate/reorder blocks and edit phase duration/split/purpose. Destructive deletion requires confirmation and writes a tombstone. The editor makes no working-set prescription, program approval, or JS runtime call.
- This is a meaningful functional UI slice, but **not a completed custom-program builder or production navigation**. It is not selected by MainActivity, and no release APK is built. Existing authenticated local athlete enrolment is required. Draft enumeration/reopen selection, trusted native gym/catalog source wiring, authenticated inventory revision consistency, full per-week set-override UI, background autosave/debounce, cross-process activity restoration, migration, full coach audits, and athlete-approved handoff to Kotlin-issued workouts are **unfinished**. The existing workout path stays unchanged.
- The CI workflow now includes both candidate editor files in the isolated Kotlin branch's Android compilation gate. A passing compile alone does not establish device-level interaction, screen-reader/foldable acceptance, or production readiness.

### Candidate custom exercise picker and set editor (not yet released)

- `CustomExerciseAuthoringDomain` now filters movements using a **reviewed athlete setup, gym inventory and catalog**: excludes disabled bodyweight exercises and excluded movements, unavailable facilities/attachments, unreviewed muscle-credit variants, mismatched loading basis and missing or invalid confirmed equipment stock. No UI text field or catalog name can fabricate a plate, cable-stack increment or attachment.
- Adding a slot and replacing its explicitly authored sets revalidate the current movement, physical load, per-set reps, RIR, rest, warmup status and advanced technique. A drop set must have a separately attainable, strictly lighter follow-up load with the same unit and basis. Every authoring operation preserves pinned identity and unrelated block/week overrides, and remains a **draft** rather than an approved workout.
- The candidate `NativeKotlinCustomExerciseSheet` has exercise search, per-set editing, explicit load, working/warmup selection, rep ranges, optional RIR, work/rest timing, drop/myo/rest-pause/cluster follow-ups, and accessible duplicate/reorder/remove controls. `NativeKotlinCustomCycleCandidateScreen` can add exercises and edit/reorder existing slots through a revision-CAS controller.
- `NativeKotlinCustomCycleController` accepts an optional `NativeKotlinCustomAuthoringSource`. **No source is installed by default.** Without an authenticated, revisioned native gym/catalog provider, options are empty and set authoring is blocked. This prevents an unsafe temporary demo catalog from being mistaken for usable gym stock. Supplying trusted persistent gym/catalog revisions and preserving the same owner fence across their updates is still required before production use.
- Validation remains independent of final full-cycle exercise frequency, total session duration, weekly per-muscle dose, advanced technique recovery, coaching approval and workout issuance. The source-only JVM/Android CI gate does not prove on-device accessibility, screen sizes, process death or live athlete acceptance.

### Durable native gym and curated catalog source — first integration slice

- `NativeKotlinCustomAuthoringSourceCodec` explicitly serializes a **complete reviewed athlete setup**, individual physical implement stock (barbell pairs, explicit dumbbell loads and machine increments), available facilities/attachments and curated catalog movement/variant metadata. Decode reconstructs a real `AthleteSetupDraft` and runs `CoachAthleteSetupDomain.review` again; unknown binary versions, malformed inventory and altered checksums fail closed. No Java object serialization or JS runtime.
- `NativeKotlinCustomAuthoringSourceFileStore` persists one owner/athlete-scoped source revision in the app-private directory, using checksum verification, atomic replacement and the shared issuance/revision file fence. Catalog/gym updates are compare-and-swap, and revocation leaves a tombstone rather than causing stale catalog writes to resurrect stock.
- `NativeKotlinCandidateHost` has candidate native read/publish/revoke APIs. It re-reviews the supplied athlete input and enforces the currently activated local identity. The default `NativeKotlinCustomCycleController` source now reads **only this persisted native source**; Compose receives cached reviewed picker choices rather than performing file I/O while drawing.
- `NativeKotlinCustomAuthoringDraftCoordinator` atomically verifies the exact persisted gym/catalog revision and authored cycle revision **under the same owner lock** before saving an exercise/set edit. A gym change, source revocation, identity switch, corruption or stale draft causes a refusal without overwriting the athlete's saved custom program.
- JVM regression tests cover cold restoration of load/attachment/variant data, version/garbage rejection, cross-account isolation, checksum failures, revision races, source revocation, stale edit rejection and recovery of the original authored draft.
- **Still not a production workout authority:** enrollment remains explicitly activated and the native app has not connected its real persistent gym editor, complete reviewed exercise library or source revision propagation into active workout approvals. No default gym or fictitious exercise is created. Until the native onboarding and curated catalog publisher feeds this repository, options are empty. Screen/device tests, cross-process recovery, full authoring approval and Android navigation remain outstanding.

### Athlete-edited Kotlin gym inventory (candidate implementation)

- `NativeKotlinGymCatalogEditingDomain` is the native source-authoring engine for **specific** verified implements (independent dumbbell totals, cable/machine stacks with explicitly entered increments, barbell weights and plate-pair counts), fixture IDs, attachment IDs and author-reviewed exercise variants. It reconstructs the full athlete setup without losing session preferences, focused/reduced muscles, calendar locks or gym unit, then reruns `CoachAthleteSetupDomain.review` before producing a new source revision.
- `NativeKotlinCandidateHost` now exposes owner-locked source updates for one implement, equipment removal, fixture/attachment changes and curated catalog replacement. Each update reads the currently saved native snapshot and performs revision-CAS publication; no older writer may replace newer physical stock.
- `NativeKotlinCustomAuthoringSourceCodec` now rejects unreviewed or malformed catalog metadata during disk persistence itself, rather than trusting the caller to classify its own movement as reviewed. A catalog needs stable exercise/variant IDs, explicit muscles and credit fractions, a provisional human review reference, equipment basis, allowed focus, and any required facilities/attachments. Source persistence and saved custom-cycle edits remain separate from workout issuance authority.
- `NativeKotlinGymSourceController` and `NativeKotlinGymSourceCandidateScreen` supply native Material 3 editing of gym stock, exact cable/machine increments, verified barbell pairs and available fixtures/attachments. The screen displays its catalog provenance read-only; it does **not** fabricate movement muscle-credit review or auto-convert legacy JS catalog metadata into approved native movements.
- An explicit first-time native setup path now uses `NativeKotlinCandidateHost.activateReviewedStarterAuthoringSource` to re-review the full athlete setup, activate only the matching device-local owner, and publish `StarterExerciseTranche.movements()` (20 documented engineering-provisional movement variants) with expected source revision zero. The gym candidate screen shows a clearly labeled opt-in only when a *separately collected, user-confirmed* `AthleteSetupDraft` is supplied by native onboarding. No barbell, dumbbell, specialty machine, fixture, attachment or target load is invented; variants remain ineligible until the matching physical gym settings are verified. This is the first native curated catalog publisher, but not yet connected to production onboarding/navigation.
- The screen is not selected by production `MainActivity`. The reviewed native athlete and curated exercise catalog must still be explicitly published by the onboarding/catalog owner before the UI can edit saved stock. **The existing web/legacy inventory editor is not the trusted Kotlin source**; copying it without checking per-implement basis, unit, and program ownership would reintroduce the old weight bugs.
- Tests cover a single equipment revision, unknown or duplicate loads, unit mismatch, attachment removals hiding affected variants, full athlete-profile preservation, invalid catalog provenance, stale source revisions and unchanged custom-program prescriptions. Android compilation is required; an APK and device interaction proof are not yet complete.

### Native onboarding → gym → custom-cycle candidate journey

- `NativeKotlinOnboardingInputDomain` assembles **explicit** athlete choices and delegates the entire review to the Kotlin setup domain. Unknown remains null, distinct from an athlete-confirmed empty equipment list or explicit false. "All unselected days are rest days" is a user-confirmed action, never an invisible default.
- `NativeKotlinOnboardingCandidateScreen` collects goal, experience, split, 3–6 workout weekdays, preferred duration versus explicit hard cap, training/rest/locked/hard/long calendar rules, focused/reduced muscles, exercise exclusions, bodyweight/superset/triceps permissions, max barbell movements, time zone, gym identity/unit, and explicitly confirmed equipment weights, facilities and attachments. No guessed plates, machine intervals, training prescription or default weekly plan is injected. The independent Kotlin review provides actionable field errors and a separate confirmation step before native enrollment.
- `NativeKotlinCandidateJourney` links that onboarding screen with `NativeKotlinGymSourceCandidateScreen` and `NativeKotlinCustomCycleCandidateScreen` using a shared candidate host and Material 3 theme components. It preserves unsaved onboarding form state across its tabs and refreshes the saved cycle and exercise picker when returning from an updated gym. **This is still an isolated candidate route, not production `MainActivity` navigation.**
- Re-enrolling the same explicitly confirmed local athlete can reopen their prior source revision without replacing verified inventory, custom catalog curation or workout history. The candidate onboarding UI can display the device's existing **ID only**, behind an explicit "Use saved local athlete ID" action; this does not activate the owner or read protected gym/workout records. A fully reviewed athlete setup and second confirmation still gate access. Other athlete identities remain disallowed, and revoked/corrupt source records are not restored by creating a new catalog.
- Kotlin unit tests cover unconfirmed and intentionally empty values, independent bodyweight-only gyms, calendar/rest consent, session preferences and separate focus/reduced muscles. Production app integration, comprehensive source-version rebase, on-device interaction/accessibility tests and fully authored workout issue remain release gates.

### Whole-cycle coaching audit (new review-only pass)

- `CustomCycleCoachAuditDomain` resolves and inspects **every scheduled week** of the athlete-authored custom cycle, including the exact individual slot's week-specific set overrides. It preserves chosen exercises, rep ranges, RIR, warmups, physical load basis, attached advanced-set follow-ups and rest—never replaces the cycle with a generic ComposerRequest.
- The audit validates the currently reviewed native athlete/gym/catalog, permitted exercise roles, excluded exercises, stated training weekdays, barbell-per-day limit, bodyweight permission, superset permission and full structural/physical load feasibility. It provides stable issue paths for on-screen corrections.
- Every resolved week gets total working sets, direct/indirect fractional muscle-credit totals for all **16 distinct tracked muscles**, direct training frequency, and observed work/rest duration per training day. No omitted muscle is collapsed into a generic arm or forearm bucket.
- Goal-/experience-/phase-specific MEV/MAV/MRV, direct-frequency thresholds and primary-lift frequency are checked **only if an explicit, versioned `AuthoredGoalProgramPolicy` is supplied**. Missing policies are reported as blockers; the old web engine is not consulted and no generic threshold is invented. Recovery/test blocks remain distinct from ordinary training weeks.
- Non-set overhead, transitions and cooldown duration must also be explicitly supplied to assess hard session limits. The current advanced-set schema lacks follow-up active-work seconds; the audit explicitly flags this information as unresolved rather than giving an incorrect all-in session-time guarantee.
- `NativeKotlinCustomCycleController.auditFullCycle` re-reads the durable athlete-owned cycle and gym/catalog source before review, and checks both again afterward; a changed cycle or source is rejected as stale. The candidate Compose program screen shows actionable issue paths, reviewed weeks and per-week actual working-set counts, while clearly marking approval/issuance as separate.
- The audit status `READY_FOR_ATHLETE_REVIEW` is not a persisted sign-off or an issued workout. It **cannot publish an approved program, change workout history or authorize a live session**. Future work must bind policy/source revisions to a protected athlete consent receipt and implement a truly custom per-week Kotlin workout snapshot retaining all authored set detail and amplifier semantics.

### Exact authored-cycle consent (separate from workout approval)

- `NativeCustomCycleSignoffFingerprint` SHA-256 binds an athlete's **entire** immutable custom-cycle binary to their **entire** reviewed native gym/catalog source and a canonical encoding of all complete coach-authored phase policies, muscle dose bounds, required skill frequencies, rationales, and declared non-set session duration. Reusing a policy revision label with altered contents does NOT preserve consent validity.
- `NativeKotlinCustomCycleSignoffFileStore` stores an owner/athlete/cycle-scoped revisioned consent receipt in the app-private directory, with binary format validation, checksum corruption detection, atomic replacement, durable revocation tombstones and compare-and-swap. It obtains the shared OS per-owner approval lock and rereads the real persisted program/source and recomputes **all 104 or fewer scheduled weeks** of coaching audit at the moment of the sign-off transaction; stale draft revisions, source revisions, changed identity, incomplete coaching policy or absent explicit athlete confirmation refuse without creating a receipt.
- Cold reopens can show a historical receipt, but `isCurrent` is **false** until a real policy-complete audit and exact content fingerprint are checked again. Editing the program, equipment or policy makes an existing receipt non-current without erasing training history.
- The isolated native host exposes confirm/read/revoke; the Compose cycle screen provides a separate confirmation dialog and revocation action. The primary sign-off button is disabled until a policy-complete audit has returned `READY_FOR_ATHLETE_REVIEW`. No silently assumed MEV/MAV/MRV, machine increment, week override, session overhead or exercise substitutions are permitted.
- **Consent is not a training-program execution grant:** this new file store cannot write the existing ComposerRequest approval, grant a workout, modify history, or issue sessions. The custom-workout snapshot and provenance-aware issuer must preserve exact individual authored set/variant/attachment/superset/amplifier/override details before production activation. The native coaching policy publisher and its UI remain unresolved, so most real candidate cycles currently cannot reach sign-off.

### Exact custom workout snapshot and consent-bound preflight

- `CustomWorkoutSnapshotDomain.preview` validates the **entire** authored cycle against the currently reviewed native athlete, physical gym and exercise catalog, resolves a selected 1-based absolute cycle week, then projects the chosen weekday without using `ComposerRequest` or the old JavaScript workout engine. The snapshot includes immutable block/week and gym/source revision provenance; stable per-slot set identities (duplicates of the same exercise remain separate), actual movement family/variant/attachment, pinned flag, supersets, athlete notes, all independently authored warmup/working sets, rep bands, load/unit/basis, RIR, rest/work duration, and complete follow-up amplifier fields including lower drop loads. A week override changes only the matching week and slot.
- `reviewForConsent` runs the **complete coaching audit again** and requires explicit non-set session seconds before returning a signoff-compatible session. It never supplies missing muscle policies, active follow-up work time or a provisional machine weight.
- `NativeKotlinCustomWorkoutPreflight.inspect` reads actual device-private source, program, and saved athlete consent while holding the shared per-owner OS revision lock. It checks account identity and verifies the existing signoff's full content fingerprint against the **current** coach-authored policies and declared session times. A revoked, stale, missing or tampered signoff, changed gym, altered program or unmatched athlete cannot produce a consent-bound preview.
- The candidate native host and Compose custom-program screen provide separate **draft preview** and **consent-verified preflight** controls. The view presents exact sets and advanced techniques instead of collapsing an authored custom day into a composer-generated exercise target. Both preview types carry `isIssued=false`, and no direct journal or history write exists on this route.
- The next issuance step still requires an owner-authenticated *one-time workout ID*, complete native-history migration, independently tested custom session codec/journal schema for every amplifier/superset/set receipt, and atomic persistence/recovery. The existing ComposerRequest workout coordinator must not be used as a backdoor because it cannot preserve all custom fields.

### Native custom session binary + durable preparation (next migration pass)

- `NativeKotlinCustomWorkoutSnapshotCodec` is a separate versioned binary format for the **entire exact authored day**, not the lossy `KotlinWorkoutTarget`/ComposerRequest format. It preserves full athlete/gym/program provenance, absolute week, custom split, ordered stable slot and set IDs, actual movement variants, attachments, pinning, authored notes, warmups/working sets, literal attainable load/basis/unit, rep ranges, RIR, work/rest timings, supersets, and complete myo/drop/rest-pause/cluster follow-up fields including independently verified drop weight. Codec refuses unknown schema, trailing bytes, duplicate/missing set identities, contradictory duration, overlong/invalid values, broken supersets, and nonsensical advanced sets.
- `NativeKotlinCustomWorkoutPreparedStore` acquires the same native per-owner OS revision lock as the gym/program/consent stores. Before writing a workout-ID-specific immutable prepared file, it rereads the **current** trusted consent with full policy-content fingerprint and reruns the whole-cycle coaching review plus exact selected-day projection. It uses SHA-256 corruption detection, atomic rename, cold reload and replay-safe idempotency, and never overwrites a different workout under an already used ID. This is preparation, **not issuance**.
- `NativeKotlinCandidateHost` exposes authenticated prepare/reopen APIs, and the candidate Compose custom screen permits storing an exact non-issuing preparation after verified consent. The cold-reopened result is shown with its real workout ID; no set log, Finish, PR, last-run or history record is written.
- Explicit regressions cover same-exercise multi-slot identities, week overrides, confirmed dumbbell drop loads (20 → 15 lb) versus physically missing 17.5 lb loads, advanced-set binary roundtrip, invalid duration/IDs, consent revocation, stale gym versions, conflicting workout IDs, corrupt files, owner switch, replay and recovery. A codec or prepared file by itself is **not** a secure issued-workout grant.
- **Still required for live Kotlin workouts:** complete athlete-native history enrollment/migration; one-time workout issuance authority and unique ID arbitration with the legacy/native journal; durable per-set actual/extension receipts, rest/Focusing/amp handling, idempotent crash-recovery and Finish/history transaction; and device-tested Compose execution. Neither the production app nor other developer branches are switched.

### Factual Kotlin logging and receipt replay (new headless foundation)

- `CustomWorkoutJournalDomain` supplies a **pure non-persisting reducer** over the fully authored `CustomWorkoutDaySnapshot`. Every main set and its optional MYO_REPS / DROP_SET / REST_PAUSE / CLUSTER follow-up is a separately identified visit. An authored amplifier does **not** imply actual follow-up work was completed; finishing requires explicit recorded or skipped follow-up for every completed main set. A skipped or failed main set makes its follow-up non-applicable, never a fabricated completed exposure.
- Every journal operation has a unique event ID, expected journal revision and, for corrections, the **currently effective** event ID it corrects. Duplicate exact event replay is idempotent even after a lost acknowledgment; collisions, stale revisions, orphaned follow-up events and attempts to complete an unresolved workout fail. An athlete's actual RIR remains nullable and independent of the target RIR; actual performance is not copied from prescriptions.
- Changing a completed main set into a skipped/failed one cannot strand a previously completed follow-up in the log. The athlete must first explicitly correct that follow-up to skipped; then they may correct the main set. The complete immutable target prescription and exercise slot identity are unchanged.
- `NativeCustomWorkoutJournalCodec` encodes a **self-contained versioned** target snapshot, all factual main/follow-up events, corrections, revision and finish marker. Every decode rebuilds effective state **only by replaying the reducer** and refuses invalid receipt order, forged completion or inconsistent revisions/cached state. It is transport, not a signed authorization, device-file write, journal issuance, or history proof.
- Tests cover independent follow-up and observed-RIR facts, explicit skipped/failure semantics, truthful corrections, idempotent replay, cold restore, valid weight/unit bounds, corrupted byte formats and missing amplifier receipt finish gates.
- **Still required:** an owner-authenticated, same-fence atomic native custom workout journal file store; source/consent/history migration validation at issuance; idempotent set-write acknowledgment; crash-recoverable Finish transaction merged into native history/Last/PR/progression; a real usable Compose workout screen and on-device acceptance. Neither prepared snapshot nor a pure reducer currently permits user-facing logged workouts or an APK.

### Durable native custom issuance and factual set acknowledgments (isolated candidate)

- `NativeKotlinCustomWorkoutJournalFileStore` is a separate, owner-scoped custom workout issuer. Its first issuance must find an existing immutable prepared session, the authenticated athlete, current source/program/consent rechecked against the full coach-policy audit, **explicitly enrolled non-pending native history**, and no collision with the Composer journal or an already completed workout ID. It rejects an unprepared workout, unenrolled history, stale consent, altered program/gym, mismatched workout ID or another owner. All reads/commit operations use the SAME per-owner OS revision fence as gym/program/signoff writes.
- Accepted issuance atomically stores the complete custom session snapshot, original consent revision and fingerprint, plus a receipt-replayable journal in app-private binary files. A private SHA-256 envelope detects corruption (it is not a digital signature). Existing identical issued sessions can be re-opened and logged after the athlete subsequently changes their program or gym; **new** issue attempts still require fresh consent.
- A factual `Record` operation uses `CustomWorkoutJournalDomain.reduce`, revision CAS and unique event IDs. It acknowledges **only after** atomic file replacement. Exact repeated requests are idempotent, stale revisions and mismatched event collisions are rejected, the main/follow-up identities remain independent, and cold reopen verifies the immutable prepared workout and replays every factual set event. Cross-owner or corrupt file access fails closed.
- `NativeKotlinCandidateHost.issueReviewedCustomWorkout`, `openIssuedCustomWorkout` and `recordIssuedCustomSet` provide candidate-only Kotlin integration endpoints. They are intentionally not wired to the production JavaScript runtime or an Android workout-logging UI.
- **Finish is now available only through the new isolated, redo-backed custom-history coordinator** (not via a direct journal/UI append); global Last/PR/progression remains gated until the mixed-history reader supports native custom set and amplifier facts. Real Compose workout screen and on-device end-to-end acceptance remain required before cutover or an APK.

### Native custom Finish with shared redo-backed history (isolated candidate)

- `NativeHistoryWriteIntent.CustomFinish` extends the SAME versioned, owner-scoped native history manifest used for Composer workout chronology; the new intent stores the custom workout ID, immutable Finish event ID and expected journal revision. The old manifest binary remains readable, with custom intent encoded as distinct tag 3. A pending custom Finish makes the shared ledger unavailable to ordinary history readers and to unrelated custom set writes.
- `NativeKotlinCustomHistoryTransaction.complete` performs the three-stage durable protocol while holding the shared owner lock: **prepare CustomFinish intent in history → append the exact custom Finish event to the already-issued lossless journal → finalize the shared chronological manifest entry**. It requires authentic athlete identity, enrolled history, a complete set/advanced-set receipt history and matching revisions; neither a mere prepared snapshot nor an in-memory reducer can finish a workout.
- `recover` is an idempotent owner-fenced redo operation. A process death **after manifest prepare but before the journal write**, or **after journal Finish but before manifest finalize**, can replay the exact original event and finish the same chronological entry without double counting. A different athlete, corrupt journal, foreign Composer pending intent, event collision or unresolved set cannot be called completed.
- `CustomWorkoutHistoryEvidenceDomain.project` separately replay-validates the finished custom journal and emits independently observed MAIN and FOLLOW_UP facts, including historical set/slot identity, actual reps/load/RIR and original prescribed targets. It does **not** silently convert custom advanced techniques to ordinary sets or publish invented PRs/progression.
- The manifest stores custom entries with a distinct deterministic `custom-` receipt identifier. Until a unified custom-aware global chronology/Last/PR/progression reader is implemented, the **composer-only history reader refuses mixed manifests** rather than treating custom completions as missing or pretending they have Composer-based evidence. The isolated native host now offers explicit custom Finish and crash recovery endpoints; no production workout UI or APK is enabled.
- **Remaining launch gates:** unified historical projection for both workout types, enrollment that inventories custom journal files and reconciles existing completed custom sessions, correction of *saved* finished custom workouts, device-tested Compose session logging/Finish/resume, full onboarding-to-completion acceptance, and verified adaptive coaching targets.

### Release gates not yet implemented

These source-domain constructs are NOT a completed native program workflow.
Before production authority, build and test:

- Native Compose **My Program / Week / Exercise / Set / Cycle / Compare** screens
  as reusable M3 Expressive components, persistent onboarding-to-program
  navigation, responsive phone/foldable layout, accessible drag alternatives,
  animation, draft auto-save, undo and crash recovery.
- Connect the candidate native revision-checked draft repository to real Compose authoring with diff receipts, persistent autosave/undo, crash-device proof, catalog/gym/source freshness, migration/export, and a **separate explicit author approval -> Kotlin workout issuance** handoff. No JS object or saved draft is an approval.
- Full catalog-backed variant/attachment filtering and per-gym physical stock,
  correct training-dose/recovery/periodization coaching, efficient cloning of
  all authored fields between blocks, athlete-approved adaptation,
  chronological prior-session evidence and real RIR.
- An audited importer for existing web custom programs AND cycle plans;
  never drop a previous program's custom exercise or volume amplifiers.
- Real-world end-to-end Playwright/Compose UI and Android device tests,
  power-loss recovery, backup, long-lived history, accessibility and
  coach decision audits. No release APK solely for this domain pass.

In particular, do not conflate `READY_FOR_COACH_REVIEW` with a saved
user-approved program or with successful issuance. Both result objects
explicitly report that they cannot issue workouts or publish changes.


### Post-Finish custom set corrections — headless candidate

- CustomSavedHistoryCorrectionDomain is a separate Kotlin-only correction
  reducer for **already finished** custom sessions. It holds exact original
  journal and Finish immutable, uses revisioned event/corrects-event receipts
  and replay validation, and reuses the original custom actual load/basis/RIR
  rules instead of allowing a second, divergent weight validator.
- MAIN and FOLLOW_UP corrections retain distinct visit identity and explicit
  causal rules. To change a main set to skipped/failed, its recorded follow-up
  must first be separately corrected to skipped; a skipped main with no original
  follow-up cannot be retrospectively counted as a completed amplified set.
- CustomSavedHistoryEvidenceDomain projects corrected *facts*, omitting
  now-nonapplicable follow-up work from effective exposure while retaining all
  immutable audit receipts. It never issues progressions, PRs or live history.
- JVM source tests cover revised actuals with unknown RIR, physical load
  mismatch, causal amplifier correction order, absent follow-up, revision and
  predecessor conflicts, exact idempotent retries and forged replay caches.
- **Unfinished:** durable owner-locked saved-custom-correction codec/store,
  redo-backed ledger update, verified mixed-history correction read,
  chronological lifetime history, UI editing and legacy import. Do not treat
  this headless reducer as a completed workout editor.


### Durable post-Finish custom history correction — isolated native integration

- Custom saved corrections now have their own binary codec and owner-scoped
  private file, separate from a custom workout's original Finish and immutable
  prescribed snapshot. Every disk read independently replays saved event
  order, current event IDs, load basis, RIR and amplifier-causality rules.
- The existing shared native history ledger carries a distinct CustomCorrect
  redo intent, not the Composer correction format. The owner-fenced
  transaction uses prepare → durable correction file → manifest finalize.
  Recovery resumes after either write boundary without duplicating events.
- The verified mixed Composer/custom history reader accepts corrected custom
  facts only when the original custom-v2 receipt, owner identity, journal,
  saved correction revision and shared chronological manifest agree. A
  mismatch fails closed for all historical completions.
- The isolated candidate host exposes correct/recover methods. Production
  MainActivity, Last/PR and the Compose history editing UI remain unchanged.
- Kotlin regressions exercise cold restart, idempotent retries, foreign
  pending redo, corrupt correction files and mixed-history recomputation.
  JVM/Android compile and device acceptance remain unverified release gates.

### Candidate adaptive option preview and athlete-authored draft acceptance

- `NativeKotlinAdaptiveCycleDraftAcceptance` now reopens the currently saved
  athlete-owned custom cycle and reviewed gym/catalog source under the same
  per-owner OS fence, re-runs full-cycle coaching and coach-permission reviews,
  and returns an exact fingerprint of the offered option and review context.
- A separate, explicit athlete action rechecks that fingerprint, current
  revisions, source, permissions and full-cycle audit under the owner lock
  before saving ONE revision-CAS **draft**. A stale program, changed gym,
  modified proposal, denied permission or wrong signed-in account fails
  closed. No issued workout, past evidence, training history or source
  inventory is rewritten. The previous cycle signoff becomes non-current.
- `NativeKotlinCandidateHost` exposes off-main-thread preview and acceptance
  entry points using its own local authenticated owner rather than letting
  the screen supply identity. These remain unused by production navigation.
- **The supported slice is intentionally limited:** an option with changed
  split or scheduled weekdays is rejected until prospective athlete setup,
  phase policies and actual gym/source revisions have coordinated approval.
  Caller-supplied policy and permission values are still candidate review
  inputs, not production-authenticated coaching grants. Even after draft
  acceptance, the athlete must separately sign off and the independent
  native workout preflight/issuer must approve exact targets before training.

### Owner-persisted adaptation rights (candidate)

- `NativeKotlinAdaptiveCyclePermissionFileStore` persists the athlete's
  explicit per-cycle change rights as an owner-fenced, SHA-256 verified,
  revision-CAS record, including revocation tombstones. No issued workout,
  training history or completed log is changed by the permission store.
- A caller's `CustomCycleCoachPermissions` object now has no standalone
  authority. Candidate previews and commits must reopen the durable rights,
  require the expected revision and an exact match of allowed change kinds.
  Changed/revoked/corrupt/foreign grants fail closed.
- `NativeKotlinCandidateHost` exposes read/confirm/revoke rights and uses its
  own authenticated identity. The acceptance proposal still needs a
  separately authenticated *coaching policy* source before full production
  activation or prospective split/frequency approvals.
