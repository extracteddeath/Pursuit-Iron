# Native Kotlin workout journal store — isolated integration candidate

**Status: JVM-tested integration candidate, NOT the production Android workout
owner, and NOT the final end-to-end Kotlin cutover.** The Android app does not
depend on this module. The legacy JavaScript runtime remains unchanged.

## Kotlin-issued workout Compose candidate (not live navigation)

A new `NativeKotlinWorkoutCandidateScreen` uses the **existing
Material 3 Expressive components and preserved original theme system**,
but gets all actual training data from the independent Kotlin
`NativeKotlinSessionController` and pure
`KotlinWorkoutPresentationDomain`. It supports an ordered exercise
overview, single-set focus and full set list, explicit target-versus-actual
weights/reps/RIR, corrected receipts, skipped/failed sets, revision conflict
refresh, and confirmation before Finish. All actions dispatch through
`NativeKotlinCandidateHost.recordWorkoutOperation` on IO threads; the
controller never displays optimistic completion as a committed event.

`KotlinWorkoutEntryDomain` owns typed actual input validation, so no
UI formula assumes the athlete performed a coach-recommended load or
mistakes target RIR for observed RIR. A failed attempt logs the **actual
attempted** weight entered by the athlete; it does not copy the planned
weight. Projection tests cover unknown actual RIR, correction ordering,
skips/failures and finished read-only state.

The screen and process-scoped controller are **compiled but not routed**
from `MainActivity`. They cannot enroll an athlete, create an approved
program or silently migrate old JS-issued workouts. No user-visible
full cutover, backups, or APK until current native program/coach/athlete
ownership and history migration are complete.

## Isolated Android application host (compile-only integration)

The **Android app module now compiles** the pure Kotlin engine and native store
directly on the dedicated `architecture/kotlin-engine-rebuild` branch.
`PursuitIronApplication.kotlinCandidate` lazily provides
`NativeKotlinCandidateHost`, which composes:

- `AppPrivateKotlinLocalAthlete`: a versioned, checksummed
  Android `AtomicFile` identity record under `noBackupFilesDir`, created
  only after a **complete Kotlin-reviewed onboarding profile** is explicitly
  activated. It refuses corrupt records and different-athlete re-enrollment,
  does not auto-activate on process restart, and retains history on deactivate.
- `NativeKotlinApprovalFileAuthority`, `NativeKotlinApprovalRevisionFence`,
  `KotlinNativeJournalFileStore` and `NativeKotlinWorkoutCoordinator`,
  all rooted in a separate private `kotlin-coach-v1` directory.
- `reviewSetup`, `activateReviewedSetup`, `publishReviewedProgram`,
  `issueWorkout`, `recoverWorkout`, `recordWorkoutOperation`, and
  `revokeCurrentApproval` as Kotlin-native lifecycle entry points.

**Security scope:** This is a *single-device, explicitly activated local
athlete*, not remote login or identity-proofing. The existing application has
no Kotlin account provider, so claiming this record represents a signed-in
server account would be incorrect. The program-publish API is **R&D only**
until the upstream native program/gym/catalog source-revision owners have
been integrated. JavaScript outputs do not authorize native issuance.

**Active runtime unchanged:** `MainActivity` and `NativeRuntimeHost` still
use their original JS-backed store and workout routes; the new host is
never activated or selected by a screen. The new CI task
`:app:compileDebugKotlin` checks app module integration **without
assembling or publishing an APK**. Actual native onboarding and Compose
navigation remain to be implemented and validated on real devices.

## Native Kotlin issuance coordinator — approval-checked integration

`NativeKotlinWorkoutCoordinator` joins the **actual independent Kotlin
`KotlinWorkoutSnapshotDomain.stage`** with the already tested atomic
`KotlinNativeJournalFileStore.issueAuthorized` path, rather than importing
any JavaScript training engine. An injected `NativeKotlinWorkoutAuthority`
must read an authenticated user's **current** accepted program, exercise
policies, workout-day grant, revisions and trusted storage identity from the
app's own data/authentication layer. No UI text or caller-provided boolean is
an approval. The coordinator stages a fully audited week and workout first,
then **re-reads and re-stages the trusted approval inside the file lock**
before committing; revoked grants, gym revisions, changed policy/targets
or invalid physical loads refuse without writing a partial workout.

A pre-existing workout can still be opened and finished after a plan edit:
`canAccessIssuedWorkout` checks current account ownership separately from
new-issuance approval. Original issued targets and receipts remain frozen.
Logout or another athlete's session must deny access. The authorization
provider is a required production host dependency **not implemented here**;
test doubles prove the contract, not actual Android sign-in security.
### Approval-to-commit transaction fence (independent native implementation)

`NativeKotlinApprovalRevisionFence.withOwnerTransaction` now holds a
**per-owner cross-process OS file lock** (plus JVM process mutex) throughout
the approved-week review, revalidation and durable journal issuance.
`NativeKotlinWorkoutCoordinator` REQUIRES an injected fence: there is no
constructor that silently runs without it. Cold-open and append also use
the fence so app-owned sign-out and ownership changes can be serialized.
Lock ordering is always **owner approval fence -> workout journal lock**.

A separate concurrent-writer test proves that a compliant approved-program
writer cannot change the source revision between the last approval review and
the workout commit. **This is a working fence, not full integration:** every
real athlete/profile/program/gym/grant mutation and sign-out MUST also use
this SAME app-private fence directory and owner key. Existing live Compose
program writers do not do that yet, and using an unrelated DB transaction,
different directory or uncoordinated mutation would bypass the protection.
It does not authenticate the caller or make separate files/SQLite transactions
atomically recoverable. Actual Android owner/session persistence and device
instrumentation are still mandatory before production integration.

## Durable Kotlin-owned approval record (integration candidate)

`NativeKotlinApprovalFileAuthority` is a real independent native
implementation of `NativeKotlinWorkoutAuthority` with a **versioned binary
record for the complete accepted plan**, explicit confirmed gym stock, muscle
bounds, schedule, warmups, rep/RIR/rest policies, athlete/source revisions,
workout grant and monotonically increasing approval revision. It uses
`NativeKotlinApprovalCodec` for bounded, lossless, versioned serialization,
checksum-checked app-private atomic file replacement, and the SAME
`NativeKotlinApprovalRevisionFence` used by workout issuance. There is no
`ObjectOutputStream`, JavaScript engine, cross-language bridge or invented
equipment load.

An authenticated native host must supply `NativeKotlinCurrentIdentity`
from its real sign-in/account session. `publish(ownerKey,
expectedRevision,approval)` does a fenced compare-and-swap (zero for first
approval), rejects malformed or incomplete programs and refuses to overwrite
corrupt or foreign account data. `revokeCurrentGrant` commits a revisioned
tombstone, not file deletion. A revoked or modified *current* grant cannot
issue new workouts; already-issued work remains accessible to the same
authenticated athlete with unchanged frozen prescriptions.

Nested owner-fence acquisition is safely reentrant: the workout coordinator
already holds the fence when it queries this authority inside the storage
transaction. Revocation and all compliant program mutations acquire the
same file lock and cannot race that commit.

**Integration limit:** the real Compose app is not yet wired to this
approval store or a trusted sign-in identity provider. It still uses
legacy JS-backed program storage. Native session sign-out/account switching,
other profile/gym/grant writers, production backup and migration must adopt
the same revision fence. Application root directory access must be private,
and Android device power-loss testing is outstanding. Local JVM tests are
not a substitute for these gates.

## What exists

`KotlinNativeJournalFileStore` provides a **private-directory, per-athlete,
per-workout atomic compare-and-swap** boundary for the already-developed
immutable `KotlinStagedWorkout`, `KotlinStoredWorkoutIdentity`,
`KotlinJournalOperation` and `KotlinWorkoutJournalReplayDomain`.

- A new session uses `issueAuthorized`: the native session owner must
  approve the current athlete, source revision and grant while the storage
  lock is held. The **entire immutable Kotlin-issued snapshot and empty
  receipt journal are committed together in one atomic v2 envelope**.
  Duplicate identical issuance is idempotent; conflicting snapshots refuse
  to overwrite. The old ungated `issue/open/append` paths are internal
  compatibility and unit-test methods, not production integration APIs.
- `append` obtains a cross-process file lock, reloads and checksum-verifies
  current bytes, replays **all original receipts**, checks the expected
  journal revision and deduplicates event IDs, then writes an atomically
  replaced file after flushing file contents. It never alters a target.
- A retry after a lost acknowledgment finds the previous receipt and returns
  `replayed=true`, rather than producing a duplicate set or finish.
- `openStored` reconstructs the complete original Kotlin workout directly
  from private disk, without caller-supplied targets or JS. It verifies the
  current owner and replays stored receipts, refusing missing/corrupt
  workouts. `appendStored` repeats that owner check under the lock before
  an atomic write. Neither API silently creates a missing workout.
- The binary protocol uses a fixed magic/version, bounded receipt count and
  length, SHA-256 checksum, and snapshot fingerprint covering **all immutable
  issued targets**. It is a corruption check, **not a cryptographic signature**.
- All application-facing I/O errors are explicit refusals. There is no
  fallback to JavaScript or a non-atomic write mode.

## Important incomplete security and durability gates

1. **Authenticated native storage owner**: only the real authenticated
   athlete's current private `filesDir` may supply `ownerKey`. Neither this
   stand-alone module nor the SHA checksum authenticates that identity. The
   owner must protect files from untrusted/symlink paths and scope requests.
2. **Approved workout grant**: the v2 file now atomically persists the
   complete Kotlin-issued snapshot **together with** receipts, but the
   injected native owner callback must independently authenticate the
   current approved program, workout grant and gym/catalog/rules revisions.
   A caller-constructed snapshot alone must never authorize issuance.
   That real app-side ownership authority is not connected yet.
3. **Power-loss durability**: the journal temp file is flushed and atomically
   renamed, but parent directory metadata flush behavior differs on Android
   filesystems. Validate it on real supported devices and choose an
   appropriately proven native transaction layer before production.
4. **No encryption built in**: rely on correctly scoped app-private Android
   storage until a separately specified at-rest encryption design is reviewed.
   Backups, privacy, logout and per-user cleanup are not yet integrated.
5. **No release integration**: no Compose screen uses this module, and the
   production JS workout engine still owns live preview workflows. Offline
   full session issuance, restore, history migration, user-approved cutover,
   and physical-device tests are outstanding.

## Verification

The `android-kotlin-rebuild.yml` workflow runs the entire Kotlin coaching
suite and `:native-coach-store:testDebugUnitTest` (local JVM tests of the
Android-compatible java.nio implementation, not instrumentation tests).
Regression cases include cold reopen after process restart **without a
caller-supplied snapshot**, exact load/RIR/bodyweight and full target
roundtrips, denied authorization, complete-workout receipt replay,
idempotent issuance/finish, revision conflicts, corrections, concurrent
writers, in-flight program edits blocked through the shared owner fence,
different athlete scopes, altered targets and corrupt bytes. These results are necessary but
not proof of Android device crash/power-loss acceptance.

See [complete JS replacement contract](../kotlin-engine/FULL_REPLACEMENT_CONTRACT.md).
