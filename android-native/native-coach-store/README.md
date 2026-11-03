# Native Kotlin workout journal store — isolated integration candidate

**Status: JVM-tested integration candidate, NOT the production Android workout
owner, and NOT the final end-to-end Kotlin cutover.** The Android app does not
depend on this module. The legacy JavaScript runtime remains unchanged.

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
