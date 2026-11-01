# Pursuit Iron Native — feature-first redesign contract

## Product decision

The current Pursuit Iron web application is a **behavioral reference and data authority**, not a pixel, component or navigation specification. Native Android is allowed to reorganize, combine, simplify or completely replace screen layouts where doing so materially improves training. No screen-by-screen cloning mandate. The live web app continues independently.

**A feature is preserved when an actual end-to-end user scenario and its invariants pass**: input, calculation, displayed prescription, user ownership, persistence, resume, correction, accessibility and history interpretation. Screenshots alone do not prove preservation.

## Native information architecture (proposed, not a copy)

- **Today** — training decision and next meaningful action. Readiness, next session, and situational adjustments belong near the decision, not spread across pages.
- **Train** — single active-session workspace with focused set controls, exercise context, rest, warm-ups, substitutions, tools and safe interruption/recovery. "Focus" is an alternate presentation of the same state, not a separate engine.
- **Plan** — program structure, upcoming sessions, goal/phase, split, equipment, schedule, volume, custom-program edits and adaptive-cycle preferences.
- **Insights** — history, PRs, progress charts, consistency, volume, bodyweight, strength standards and revision-aware comparisons.
- **Contextual sheets and settings** — exercise library, exercise info/attachments, injury constraints, session length, program management, backup/restore, gyms, preferences and profile. Not every feature deserves a persistent top-level tab.

The debug preview can demonstrate interactions with explicitly labeled ephemeral examples. **Never treat those examples as engine suggestions, set completions, storage writes or backup imports.**

## Non-negotiable engine and data invariants

| Capability | Canonical owner / starting reference | Native parity requirement |
| --- | --- | --- |
| Program construction and split/equipment constraints | `modules/engine-api.js`, `modules/next-engine/generate.js`, `modules/training-domain/programs.js` | Same valid options, exercise compatibility, day structure and stable IDs |
| Adaptive cycles and block decisions | `modules/next-engine/adaptive-cycle-upgrade.js`, `modules/next-engine/cycle-runtime-adapter.js` | Custom programs first-class; adapt split/exercises when authorized; stable cycle/phase identity |
| Double progression and observed-set feedback | `modules/next-engine/performance.js`, `modules/next-engine/workout-runtime.js` | Weight does not automatically jump after poor reps; respect set fatigue and user-entered weight |
| RIR and load increments | `modules/next-engine/loading.js`, `modules/training-domain/prescriptions.js` | Rep/RIR policies; plates and machine-specific increments; no impossible fractional loads |
| Workout runtime, history, edits and resumes | `modules/next-engine/workout-history-adapter.js`, `modules/next-engine/history-contract.js`, `modules/workout-session-controls.js` | Confirmed set completion, last/target, ordering, history revisions, idempotent resume |
| Readiness and injury handling | `modules/workout-readiness.js`, `modules/injury-tools.js` | Session-only and user-authored changes; never silently overwrite logged/user-owned values |
| Safe session trimming | `modules/next-engine/session-shortening.js` | Full/45/30/20; preserve warmup/rest/intensity integrity; no unintended program edit |
| History, PRs, analytics | `modules/training-domain/analytics.js`, `modules/workout-pr.js` | No duplicate PR or historical volume from superseded revisions |
| Backup/restore and conflict resolution | `modules/training-domain/records.js`, `modules/backup-restore-panel.js`, App callbacks | v13 canonical parser, edit clocks/tombstones, preview and Undo, never destructive overwrite |
| User preferences and equipment | `modules/program-settings-sheet.js`, `modules/gym-load-fields.js` | Same settings semantics, units, equipment and available increment rules |

Additional feature families to retain or improve: exercise discovery, attachments and substitutions; custom notes and pinned notes; warm-up support; rest timer; plate calculator; supersets and last-set amplifiers; quick workout adjustment; progress milestones and thousand-pound club; visual share cards; custom gym and bar weight; consistent first-class history edit; offline and Android interruption recovery; bodyweight and strength standards.

**Not invariants:** current React component hierarchy, existing tab order, CSS class names, card/banners proliferation, duplicated explanation copy, current modal hierarchy, pixel positions or exact animations. Those may be removed.

## Feature gate definitions

- **Native preview:** UI behavior can be explored using explicitly labeled transient samples; no persisted training data.
- **Canonical engine:** Kotlin may display real engine outputs only after an isolated, versioned, pinned JS runtime passes independent fixtures for the feature.
- **Verified storage:** anything involving user history, program mutations or backup import also requires schema/merge/round-trip tests and Android interruption testing.
- **Device integration:** timers, foreground/background, keyboard, vibration, accessibility and screen-awake controls must be device-verified.

Never substitute native hardcoded training formulas as a temporary workaround. Never imply a feature is complete solely because its UI is listed in this roadmap.

## Workflow-level acceptance before production

1. **Onboard → customize → generate:** valid equipment, 3–6 days, goal, time range, schedule, muscle focus/reduction and split; inspect full generated workouts.
2. **Open session → adjust → log → resume → finish:** readiness and injury, Full/45/30/20 trim, working sets, increments, RIR, notes, warm-ups, rest, focus, interruption and recovery; compare all engine outputs with JS.
3. **Finish → review → next session:** progressive overload decisions, unchanged user-owned loads, real-only PRs, history revision identity and Undo.
4. **Customize → cycle transition:** preserve custom programming and user-approved split/exercise adaptation, equipment constraints, MEV/MAV/MRV semantics.
5. **Backup → validate → preview → import → edit → export:** canonical v13 store, preserves timestamps/IDs, revision/tombstone policy, rollback on failure, no data loss.
6. **Accessibility:** narrow/wide, text 200%, TalkBack, reduced motion, edge-to-edge keyboard, contrast, portrait/landscape and interruption on real devices.

## Execution order

A. Compose information architecture and shared components, native responsive layouts and meaningful preview interactions — **no engine writes**.
B. Isolated JS runtime bundle with immutable canonical export manifest, handshake and deterministic fixture parity.
C. Read-only data bridge with canonical historical projections and safe preview import.
D. Real Workout vertical slice with transactional set logging, failure recovery and device tests.
E. Program authoring, insights, library and full-feature acceptance; then release readiness.

## Future Kotlin engine without an all-at-once rewrite

The `domain-contract` package now defines typed `ProgressionPreviewInput`, `ProgressionPreviewResult` and `ParityGatedProgressionRouter` contracts. Read-only fixed JS golden fixtures in `engine-contract/` are regression-checked in source CI. Certified Kotlin backends may eventually replace JS per feature when they exactly match protocol version, pinned source digest and independent fixture digest. No Kotlin training formulas are in production; feature routing currently has no certified runtime backend.

## Next data milestone: canonical v13 saved-program reference (not yet enabled)

The native read-only `ReadOnlyProgramReference` gate is now a **contract**, not a
program loader. The canonical v13 store keeps authored programs under
`saved[]`; individual records have stable `id` and `updatedAt` clocks,
`savedAt` is a store-level legacy/equal-revision tie breaker, and a
`tombs[programId]` deletion timestamp at or after `updatedAt` hides the
record. These facts are owned by `modules/training-domain/records.js` and must
not be rewritten into a second, diverging merge algorithm on Android.

Before any native screen displays a **real** program:

1. Validate a read-only copy with `parseStoredData` inside the trusted,
   pinned canonical JavaScript runtime; reject unsupported future versions,
   malformed lists and any untrusted program structure.
2. Select a record only by its exact canonical program ID, not list position
   or a guessed recent date. Preserve custom authored programs and all advanced
   slot settings without collapsing them into generic exercises.
3. Derive and pin its `updatedAt`, overall store `savedAt`, and SHA-256 of
   the exact source snapshot. Reject if the snapshot changes, its version is
   unsupported, the program clock differs or a deletion tombstone supersedes it.
4. For an actual workout use canonical schedule/cycle/phase and session
   prescription code to select the correct week. The existing **synthetic**
   `workoutOutline` route must NOT be reused as if it were personalized.
5. Compare a source-backed, read-only outline with a canonical web projection
   across custom plans, cycle transitions, user overrides, equipment changes and
   undo/edit revisions. Until this passes, keep Compose in the explicit
   unconnected state and all workout logging disabled.

`ReadOnlyProgramReferenceGate` alone cannot authenticate the snapshot digest,
parse a backup, recover stale data or authorize writes. Only a future trusted
source adapter and device/interruption test can do that.


## Current-program day browsing: first read-only Plan to Train vertical slice

A program inspected through the existing canonical current-Plan gateway can
now select a specific training day and navigate to Train. The transient
ReadOnlyTrainingDaySelection holds only the exact revision reference and
stable day ID, not the program, workout or history. Train re-resolves the
selection against the still-approved current Plan presentation, rejects
changed source SHA/revision, missing or deleted days and detached navigation,
and shows only the canonical day name and exercise IDs. It does NOT invent
exercise names, weights, sets, RIR, warm-up or progression suggestions.

This is intentionally an end-to-end read-only navigation path, NOT a working
logger; the old explicitly labeled demo remains separate for users with
no verified current program. No active store is connected in the installable
preview, so synthetic demo UI is unaffected and no program is saved.


## Canonical synthetic Train exploration (read-only device path)

The native Train tab now has an optional, explicitly synthetic session explorer
backed by the app's preexisting locally SHA-checked canonical JavaScript sandbox.
Only a finite set of predetermined split/day pairs may enter the existing
SandboxWorkoutOutline operation. The strict Kotlin WorkoutOutlineCodec and
OutlinePresentationPolicy inspect outputs before the existing Material 3
WorkoutOutlinePanel renders exercise names, working-set counts, rep/RIR ranges,
rest, and order. There is no duplicate Kotlin generator or new sandbox service.
Requests run off-main; navigation and choice changes discard old results.

The ordinary native source checkout deliberately packages no canonical bundle,
so this action remains disabled unless a separately verified local debug
bundle is supplied. Even when available it is a synthetic test, not an
individual's session or a certified prescription. It never touches current
store, backup import, saved workout logs, equipment overrides or PR history.
The interactive sample set editor is a separate, clearly labeled demo.

Next meaningful product gate: current athlete program and history must be
owned by a verified Android store, actual session selections must be
canonical and feature parity-certified, and set completion needs transactional
storage with resume/recovery before the Train tab can log anything real.


## Synthetic canonical workout: set-by-set review

The native Train synthetic explorer now lets a tester review every engine-
generated exercise and set in actual canonical order, then browse all exercises
and jump directly to one. A pure Kotlin review cursor walks variable set counts,
including transitions between different movements; JVM tests cover forward,
reverse, boundaries, and malformed cursors. Exercise-level rep/RIR targets,
rest and names are displayed *unchanged*, with an explicit explanation that
per-set working loads require authenticated athlete context.

The underlying generated outline is still synthetic, read-only, and optional
for debug engine-bundled previews. The policy now clones and freezes exercise
lists before presentation to prevent a mutable source alias changing the UI
after approval. All workout completion remains disabled; no data writes or
program activation occur. This improves the native journey without inventing
Kotlin progression rules or more engine adapters.


### Shared native workout set header

The editable Train demo and the canonical read-only set reviewer now reuse
a single TrainingSetHeader. M3 typography, position, ordinals and progress
are consistent across both screens, with no training calculations or storage
access in the shared component. This prevents a third divergent set header
when certified personal training is connected.


## Receipt-first transaction lifecycle (not yet wired to storage)

WorkoutJournalPolicy is a pure, immutable persistence-acknowledgement reducer.
It accepts only an exact persisted receipt matching the user-owned observed
load, reps and RIR, canonical program/session identity, set position and
compare-and-swap revision. Retries are idempotent, overlapping set writes and
stale receipts are rejected. A completed session requires every planned set
to be durably completed or skipped, plus a separate store-confirmed finish.

This is not the canonical training engine, a data store, a program generator,
or an active workout logger. The next implementation must supply canonical
session slots and atomic store receipts; the native preview remains disabled
for real set completion. The APK build freeze remains active until onboarding,
program generation, logging, resume and workout completion all work together.


## Durable workout interruption staging (not active workout history)

The native app now has an unwired private Android AtomicFile bytes owner.
The pure Kotlin WorkoutJournalPersistence validates and atomically saves a
bounded, SHA-checked private pending-session journal. It reconstructs the
exact saved user-owned set load/reps/RIR and skip/finish acknowledgements on
restart by replaying them through WorkoutJournalPolicy. Each write is
compare-and-swap revision-checked; exact operation-ID retries after a crash
return the committed state without duplicating a set. Failure or corruption
does not silently reset a session or manufacture a completed workout.

This is a crash recovery staging journal, NEVER the canonical v13 history or
a separate training engine. It is deliberately NOT connected to MainActivity
or the Train tab, and its single-process writer and Android AtomicFile crash
semantics still require device acceptance. Later work must authenticate a
real canonical athlete session and safely publish finished sets to canonical
v13 history before live logging can be enabled. APK production stays frozen
until onboarding through completed-workout history passes end-to-end.

## Finished-journal canonical history handoff

Private journal format 2 now durably records the selected day, week, unit,
start time, finish time and active duration. An exact operation retry must
retain those facts. Unsupported format-1 bytes are preserved and rejected;
there are no live athlete journals to migrate because logging is still unwired.

`WorkoutJournalPersistence.finishedHandoff` exports only a disk-verified finished
journal, without clearing recovery or claiming a history commit. The shared
`finished-journal-golden.json` fixture is constructed/replayed by Kotlin JVM
tests and consumed by the canonical JavaScript history tests.

`engine-bridge/workout-history-completion.mjs` prepares a v13 candidate through
the actual `prescribeSets`, `loggedWorkoutPerformance` and `mergeStores` exports.
It authenticates the exact originating snapshot, validates the complete slot
roster and receipt sequence, preserves skipped working-set positions, and keeps
target RIR separate from reported RIR. Retrospectively calculated suggestions
are never recorded as if they were shown during training. Canonical round-trip,
history correction/deletion conflicts, duplicate finish retries, history limits,
and real generated Full Body/UL/ULPPL progression lifecycles are covered.

The pure preparation returns `persisted: false` and
`approvedForWorkoutExecution: false`; its Node entry point re-exports the same
Android-compatible implementation. The native owner retains the exact origin
until history is acknowledged, authenticates the pinned response, and compares
the expected current snapshot before saving. It acknowledges history before
clearing the journal. Custom catalog
installation, session adjustments and assistance loads
still need their canonical owners; unsupported cases cannot silently
drop observations. Onboarding, native program creation, the authenticated live
session and the full persistence/resume/completion journey remain unfinished.
The preview and all APK generation stay disabled.

## Atomic canonical store and recovery retirement

`CanonicalWorkoutStorePersistence` now implements the current-snapshot owner
with one bounded, app-private atomic file. It stores the canonical snapshot,
the pending workout's exact originating snapshot/session/slot roster, and a
durable canonical history receipt together. Canonical JavaScript remains the
only schema, history, merge and training calculation owner.

Initialization requires `PinnedCanonicalWorkoutStoreEngine` validation. Finish
preparation runs outside the storage lock; publication rechecks the current
snapshot after the engine returns. The preparation is bound to the exact
origin, current snapshot and finished-journal bytes, with the same canonical
golden output checked by Node and Kotlin. Cancelled calls, stale inputs,
conflicting identities and malformed output leave recovery intact.

Only a confirmed canonical history receipt permits private journal retirement.
The exact origin remains pinned until recovery cleanup succeeds. Fault injection
covers disk-full before history publication, a crash after commit, failures
before/after journal deletion, failed origin retirement, simultaneous finish
requests, corruption and restart. An identical retry acknowledges the saved
workout without inserting another history row. After cleanup, the next session
can pin its own origin and start its own journal.

The Android byte owners explicitly sync writes and verify the committed bytes
before acknowledgement. Snapshot reads are detached and transferred buffers are
wiped; change listeners track committed canonical snapshots. The initial native
snapshot window remains 1 MiB, with a separately bounded 16 MiB private envelope.
Oversized and corrupt stores are preserved and rejected, never treated as empty.

The app instantiates the private owner for first-program creation. The training
writer remains disconnected. Runtime session authentication, Android interruption acceptance and the complete
user journey remain pending. No APK generation or real workout logging is enabled.

## Packaged runtime to native storage integration

`SandboxCanonicalWorkoutStoreEngine` implements the native persistence engine
port over the existing checksum-pinned serial sandbox worker. A dedicated
`dispatchWorkoutStoreJson` entry point accepts only `validateOwnedStore` and
`prepareWorkoutHistory`; the ordinary engine and backup channels reject them.
The channel and strict Kotlin codec independently bound UTF-8 input/output and
bind every preparation to exact source, current-store and journal bytes.
Cancellation discards late responses; the worker still has no persistence access.

Node and Android now share completion policy and canonical revision validation.
The portable content-identity helper is checked against fixed SHA-256 vectors,
native crypto, block boundaries and Unicode/surrogate cases. The original golden
history and generated-program lifecycle cases remain unchanged. There are no
Node, DOM or network dependencies in the bundled runtime.

The runtime CI builds the exact canonical IIFE from a verified source snapshot
and runs it through the Kotlin transport, journal and atomic store in JVM tests.
The test includes a post-commit crash, exact retry, skipped-set positions,
unreported RIR and cancellation. This replaces canned engine responses in the
integration proof. A shared bundle source manifest prevents packaging drift.
Android compilation and instrumentation-source compilation remain APK-free;
actual Android execution and live training acceptance are pending.

## Native onboarding and explicit first-program creation

`NativeOnboardingScreen` now implements setup -> equipment -> generated review
-> explicit save. Its draft fields are saveable and a retained proposal restores
their initial values after Activity recreation. Compatibility choices are
returned by canonical `splitSupportsTrainingDays`; the form has no topology or
training formulas. Profile choices, a one-time creation/program identity, seed
and clock enter `prepareNativeProgram` on the dedicated runtime channel.

Canonical generation must pass its audit. The result contains an exact bounded
v13 store, source-bound program structure and first-party onboarding identity.
The native codec checks request/candidate identity and hands the proposal to a
review-only screen. Only Save calls the atomic owner. Exact retries never
duplicate initialization, and another existing program cannot be overwritten.
Cold start inspects the stored identity and current program through canonical JS.

`PursuitIronApplication` owns one `NativeRuntimeHost`, worker and writer across
Activity recreation. Asset work starts off-main after the first composition;
late results and cancelled form actions are discarded. Plan refresh now runs
on IO and remains revision-checked. Backup selection cannot supply active data.

Node and actual-bundle JVM tests cover four audited splits, preserved settings,
generation determinism, read-only review, save/retry, cold restore, tombstones
and refusal to replace another program. Live session authentication, workout
logging controls and Android device acceptance remain next. All APK jobs stay
disabled; this pass does not claim the entire onboarding-to-completion milestone.
