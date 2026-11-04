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
- An explicit first-time native setup path now uses `NativeKotlinCandidateHost.activateReviewedStarterAuthoringSource` to re-review the full athlete setup, activate only the matching device-local owner, and publish `StarterExerciseTranche.movements()` (20 documented engineering-provisional movement variants) with expected source revision zero. The gym candidate screen shows a clearly labeled opt-in only when a *separately collected, user-confirmed* `AthleteSetupDraft` is supplied by native onboarding. No barbell, dumbbell, specialty machine, fixture, attachment or target load is invented; variants remain ineligible until the matching physical gym settings are verified. This is the first native curated catalog publisher, but not yet connected to production onboarding/navigation.\n The reviewed native athlete and curated exercise catalog must still be explicitly published by the onboarding/catalog owner before the UI can edit saved stock. **The existing web/legacy inventory editor is not the trusted Kotlin source**; copying it without checking per-implement basis, unit, and program ownership would reintroduce the old weight bugs.
- Tests cover a single equipment revision, unknown or duplicate loads, unit mismatch, attachment removals hiding affected variants, full athlete-profile preservation, invalid catalog provenance, stale source revisions and unchanged custom-program prescriptions. Android compilation is required; an APK and device interaction proof are not yet complete.

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
